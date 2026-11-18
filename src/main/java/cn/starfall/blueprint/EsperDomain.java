package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

final class EsperDomain {
    static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, StarfallBlueprint.id("esper_domain"));
    private static final int STRIDE = 1024, ROW = 1024, RADIUS = 128, LOCAL = 32;
    private static final Set<ServerPlayer> TRANSIT = Collections.newSetFromMap(new IdentityHashMap<>());

    private record Cell(int id) {
        BlockPos center() { return new BlockPos(id % ROW * STRIDE, 80, id / ROW * STRIDE); }
    }
    private record Chunk(ResourceLocation dimension, long coordinates) {}

    static final class Ledger extends SavedData {
        private final Map<UUID, Cell> cells = new HashMap<>();
        private final Map<Chunk, Set<BlockPos>> units = new HashMap<>();
        private final Map<Cell, Integer> domainUnits = new HashMap<>();
        private int next;

        static Ledger get(ServerLevel level) {
            return level.getServer().overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(Ledger::new, Ledger::load), "starfall_esper_domains");
        }
        private static Ledger load(CompoundTag tag, HolderLookup.Provider lookup) {
            Ledger out = new Ledger();
            out.next = tag.getInt("Next");
            for (Tag t : tag.getList("Cells", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) t;
                out.cells.put(row.getUUID("Owner"), new Cell(row.getInt("Id")));
            }
            for (Tag t : tag.getList("Units", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) t;
                ResourceLocation dimension = ResourceLocation.tryParse(row.getString("Dimension"));
                if (dimension != null) for (long pos : row.getLongArray("Positions"))
                    out.index(dimension, BlockPos.of(pos), true);
            }
            return out;
        }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
            tag.putInt("Next", next);
            ListTag owners = new ListTag(), positions = new ListTag();
            cells.forEach((owner, cell) -> {
                CompoundTag row = new CompoundTag();
                row.putUUID("Owner", owner); row.putInt("Id", cell.id);
                owners.add(row);
            });
            units.forEach((chunk, blocks) -> {
                CompoundTag row = new CompoundTag();
                row.putString("Dimension", chunk.dimension.toString());
                row.putLongArray("Positions", blocks.stream().mapToLong(BlockPos::asLong).toArray());
                positions.add(row);
            });
            tag.put("Cells", owners); tag.put("Units", positions);
            return tag;
        }
        private Cell allocate(UUID player) {
            Cell existing = cells.get(player);
            if (existing != null) return existing;
            if (next >= ROW * ROW) return null;
            Cell cell = new Cell(next++);
            cells.put(player, cell);
            setDirty();
            return cell;
        }
        private static Cell cellAt(BlockPos pos) {
            int x = Math.floorDiv(pos.getX() + STRIDE / 2, STRIDE);
            int z = Math.floorDiv(pos.getZ() + STRIDE / 2, STRIDE);
            if (x < 0 || x >= ROW || z < 0 || z >= ROW) return null;
            Cell cell = new Cell(z * ROW + x);
            return inside(cell, pos.getX(), pos.getZ()) ? cell : null;
        }
        private void index(ResourceLocation dimension, BlockPos pos, boolean add) {
            Chunk chunk = new Chunk(dimension, new ChunkPos(pos).toLong());
            Set<BlockPos> blocks = add ? units.computeIfAbsent(chunk, k -> new HashSet<>()) : units.get(chunk);
            if (blocks == null || !(add ? blocks.add(pos.immutable()) : blocks.remove(pos))) return;
            if (blocks.isEmpty()) units.remove(chunk);
            if (dimension.equals(KEY.location())) {
                Cell cell = cellAt(pos);
                if (cell != null) {
                    if (add) domainUnits.merge(cell, 1, Integer::sum);
                    else domainUnits.computeIfPresent(cell, (key, count) -> count <= 1 ? null : count - 1);
                }
            }
            setDirty();
        }
        void unit(ServerLevel level, BlockPos pos, boolean add) { index(level.dimension().location(), pos, add); }
        int remote(ServerPlayer p) { return Math.min(512, domainUnits.getOrDefault(cells.get(p.getUUID()), 0)); }
        int local(ServerPlayer p) {
            ServerLevel l = p.serverLevel();
            BlockPos at = p.blockPosition();
            Cell own = l.dimension().equals(KEY) ? cells.get(p.getUUID()) : null;
            int count = 0;
            for (int x = (at.getX() - LOCAL) >> 4; x <= (at.getX() + LOCAL) >> 4; x++)
                for (int z = (at.getZ() - LOCAL) >> 4; z <= (at.getZ() + LOCAL) >> 4; z++) {
                    Set<BlockPos> blocks = units.get(new Chunk(l.dimension().location(), ChunkPos.asLong(x, z)));
                    if (blocks == null || !l.hasChunk(x, z)) continue;
                    for (BlockPos block : blocks)
                        if ((own == null || !own.equals(cellAt(block))) && at.distSqr(block) <= LOCAL * LOCAL
                                && l.getBlockState(block).is(Esper.COMPUTE.get()) && ++count >= 512) return 512;
                }
            return count;
        }
    }

    static boolean move(ServerPlayer caster, ServerPlayer target) {
        ServerLevel from = caster.serverLevel();
        Ledger ledger = Ledger.get(from);
        if (from.dimension().equals(KEY)) {
            if (caster != target && !caster.getUUID().equals(host(target))) return false;
            return leave(target);
        }
        ServerLevel domain = from.getServer().getLevel(KEY);
        if (domain == null || target.serverLevel() != from || !target.isAlive() || target.isPassenger()
                || target.isSleeping() || target.isSpectator()) return false;
        Cell cell = ledger.allocate(caster.getUUID());
        if (cell == null) return false;
        BlockPos center = cell.center();
        for (int x = -4; x <= 4; x += 8)
            for (int z = -4; z <= 4; z += 8) domain.getChunkAt(center.offset(x, 0, z));
        CompoundTag root = Esper.data(caster);
        if (!root.getBoolean("DomainBuilt")) {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, -1, 4)))
                if (domain.isEmptyBlock(pos)) domain.setBlockAndUpdate(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            root.putBoolean("DomainBuilt", true);
        }
        Vec3 landing = safeNear(domain, target, center);
        if (landing == null) return false;
        CompoundTag back = new CompoundTag();
        back.putString("Dimension", from.dimension().location().toString());
        back.putLong("Position", target.blockPosition().asLong());
        back.putUUID("Host", caster.getUUID());
        Esper.data(target).put("DomainReturn", back);
        travel(target, domain, landing);
        target.displayClientMessage(Esper.label("domain_enter"), false);
        return true;
    }

    private static boolean leave(ServerPlayer p) {
        CompoundTag data = Esper.data(p);
        CompoundTag back = data.getCompound("DomainReturn");
        var server = p.serverLevel().getServer();
        ResourceLocation id = ResourceLocation.tryParse(back.getString("Dimension"));
        ServerLevel to = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        BlockPos at = BlockPos.of(back.getLong("Position"));
        Vec3 destination = null;
        if (to != null && !to.dimension().equals(KEY)) {
            to.getChunkAt(at);
            destination = safeNear(to, p, at);
        }
        if (destination == null) {
            to = server.overworld();
            at = to.getSharedSpawnPos();
            to.getChunkAt(at);
            destination = safeNear(to, p, at);
            if (destination == null) destination = Vec3.atBottomCenterOf(to.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, at));
        }
        travel(p, to, destination);
        data.remove("DomainReturn");
        return true;
    }

    private static Vec3 safeNear(ServerLevel level, ServerPlayer p, BlockPos origin) {
        for (BlockPos pos : BlockPos.withinManhattan(origin, 4, 3, 4)) {
            Vec3 at = Vec3.atBottomCenterOf(pos);
            if (EsperEffects.safe(level, p, at, true)) return at;
        }
        return null;
    }
    private static void travel(ServerPlayer p, ServerLevel l, Vec3 at) {
        TRANSIT.add(p);
        try {
            p.teleportTo(l, at.x, at.y, at.z, p.getYRot(), p.getXRot());
            p.setDeltaMovement(Vec3.ZERO);
            p.resetFallDistance();
        } finally { TRANSIT.remove(p); }
    }
    static void guardTravel(EntityTravelToDimensionEvent e) {
        if ((e.getDimension().equals(KEY) || e.getEntity().level().dimension().equals(KEY))
                && !TRANSIT.contains(e.getEntity())) e.setCanceled(true);
    }
    static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("baiwan")
                .then(net.minecraft.commands.Commands.literal("return").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return player.level().dimension().equals(KEY) && leave(player) ? 1 : 0;
                })));
    }
    private static UUID host(ServerPlayer p) {
        CompoundTag mark = p.getPersistentData().getCompound(Esper.DATA).getCompound("DomainReturn");
        return mark.hasUUID("Host") ? mark.getUUID("Host") : null;
    }
    private static boolean inside(Cell cell, double x, double z) {
        BlockPos center = cell.center();
        return Math.abs(x - center.getX()) < RADIUS && Math.abs(z - center.getZ()) < RADIUS;
    }
    static boolean allows(ServerLevel l, ServerPlayer p, Vec3 at) {
        if (!l.dimension().equals(KEY)) return true;
        Cell cell = Ledger.get(l).cells.get(host(p));
        return cell != null && inside(cell, at.x, at.z);
    }
    static void tick(ServerPlayer p) {
        if (!p.serverLevel().dimension().equals(KEY)) return;
        if (!allows(p.serverLevel(), p, p.position())) leave(p);
    }
}
