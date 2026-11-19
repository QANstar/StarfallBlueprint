package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

final class EsperCollapse {
    static final int EXPANSION_TICKS = 16;
    private record Cell(BlockPos offset, int squaredDistance) {}
    private static final List<List<Cell>> SPHERES = java.util.stream.IntStream.rangeClosed(1, Esper.MAX_LEVEL)
            .mapToObj(level -> sphere(radius(level))).toList();
    private static final Map<ServerLevel, Deque<Wave>> WAVES = new WeakHashMap<>();

    private static final class Wave {
        final UUID caster;
        final BlockPos origin;
        final int level;
        final long start;
        int cursor;
        Wave(ServerPlayer player, BlockPos origin, int level) {
            caster = player.getUUID();
            this.origin = origin;
            this.level = level;
            start = player.level().getGameTime();
        }
    }

    private static int radius(int level) { return 5 + level; }
    private static List<Cell> sphere(int radius) {
        var cells = new ArrayList<Cell>();
        for (int x = -radius; x <= radius; x++) for (int y = -radius; y <= radius; y++)
            for (int z = -radius; z <= radius; z++) {
                int distance = x * x + y * y + z * z;
                if (distance <= radius * radius) cells.add(new Cell(new BlockPos(x, y, z), distance));
            }
        cells.sort(Comparator.comparingInt(Cell::squaredDistance).thenComparingLong(cell -> cell.offset.asLong()));
        return List.copyOf(cells);
    }

    static boolean start(ServerPlayer player, int level) {
        var world = player.serverLevel();
        var waves = WAVES.computeIfAbsent(world, ignored -> new ArrayDeque<>());
        if (waves.size() >= 16 || waves.stream().anyMatch(wave -> wave.caster.equals(player.getUUID()))) return false;
        BlockPos origin = BlockPos.containing(player.position().add(0, 1, 0));
        waves.addLast(new Wave(player, origin, level));
        EsperNetworking.fracture(world, EsperNetworking.Pattern.COLLAPSE, Vec3.atCenterOf(origin),
                new Vec3(0, 1, 0), radius(level), 44);
        EsperEffects.sound(world, player.position(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), .61F);
        return true;
    }

    static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var waves = WAVES.get(level);
        if (waves == null) return;
        var budget = OrbitalTerrainBudget.get(level.getServer());
        int count = waves.size();
        for (int i = 0; i < count; i++) {
            var wave = waves.removeFirst();
            if (level.getPlayerByUUID(wave.caster) instanceof ServerPlayer player
                    && player.isAlive() && !player.isSpectator() && !advance(level, wave, player, budget))
                waves.addLast(wave);
        }
        if (waves.isEmpty()) WAVES.remove(level);
    }

    private static boolean advance(ServerLevel world, Wave wave, ServerPlayer player, OrbitalTerrainBudget budget) {
        long elapsed = world.getGameTime() - wave.start;
        int radius = radius(wave.level);
        if (elapsed == EXPANSION_TICKS) {
            var center = Vec3.atCenterOf(wave.origin);
            EsperEffects.sound(world, center, SoundEvents.GLASS_BREAK, .54F);
            EsperEffects.sound(world, center, SoundEvents.AMETHYST_BLOCK_BREAK, .7F);
        }
        double reached = radius * Math.clamp(elapsed / (double) EXPANSION_TICKS, 0, 1);
        var cells = SPHERES.get(wave.level - 1);
        long started = System.nanoTime();
        if (!budget.available(started) || cells.get(wave.cursor).squaredDistance > reached * reached) return false;
        try {
            EsperEffects.withHarvest(player, wave.level, () -> {
                int changed = 0;
                for (int work = 0; work < 512 && changed < 96 && wave.cursor < cells.size() && budget.available(started); work++) {
                    var cell = cells.get(wave.cursor);
                    if (cell.squaredDistance > reached * reached) break;
                    wave.cursor++;
                    budget.scans--;
                    var pos = wave.origin.offset(cell.offset);
                    if (world.isOutsideBuildHeight(pos) || !world.hasChunkAt(pos) || !world.mayInteract(player, pos)
                            || !world.getWorldBorder().isWithinBounds(pos) || !EsperDomain.allows(world, player, Vec3.atCenterOf(pos))) continue;
                    var state = world.getBlockState(pos);
                    if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(world, pos) < 0
                            || !state.getFluidState().isEmpty()) continue;
                    if (player.gameMode.destroyBlock(pos)) {
                        changed++;
                        budget.blocks--;
                    }
                }
            });
        } finally {
            budget.spent += System.nanoTime() - started;
        }
        return wave.cursor >= cells.size();
    }
    private EsperCollapse() {}
}
