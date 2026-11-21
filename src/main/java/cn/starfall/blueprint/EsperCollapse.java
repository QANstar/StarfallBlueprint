package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

final class EsperCollapse {
    private static final Map<ServerLevel, Deque<Excavation>> PENDING = new WeakHashMap<>();

    private static final class Excavation {
        final UUID owner;
        final int skillLevel;
        final List<BlockPos> cells = new ArrayList<>();
        int cursor;

        Excavation(ServerPlayer player, Vec3 center, double radius, int skillLevel) {
            owner = player.getUUID();
            this.skillLevel = skillLevel;
            Vec3 extent = new Vec3(radius, radius, radius);
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(center.subtract(extent)),
                    BlockPos.containing(center.add(extent)))) {
                if (Vec3.atCenterOf(pos).distanceToSqr(center) <= radius * radius) cells.add(pos.immutable());
            }
            cells.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(center)));
        }
    }

    static void enqueue(ServerPlayer player, Vec3 center, double radius, int skillLevel) {
        var queue = PENDING.computeIfAbsent(player.serverLevel(), world -> new ArrayDeque<>());
        if (queue.size() < 64) queue.addLast(new Excavation(player, center, radius, skillLevel));
    }

    static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel world)) return;
        var queue = PENDING.get(world);
        if (queue == null) return;
        var budget = OrbitalTerrainBudget.get(world.getServer());
        for (int i = queue.size(); i > 0 && budget.available(System.nanoTime()); i--) {
            var work = queue.removeFirst();
            if (!(world.getPlayerByUUID(work.owner) instanceof ServerPlayer player)
                    || !player.isAlive() || player.isSpectator()) continue;
            long start = System.nanoTime();
            try {
                EsperEffects.withHarvest(player, work.skillLevel, () -> excavate(player, work, budget, start));
            } finally {
                budget.spent += System.nanoTime() - start;
            }
            if (work.cursor < work.cells.size()) queue.addLast(work);
        }
        if (queue.isEmpty()) PENDING.remove(world);
    }

    private static void excavate(ServerPlayer player, Excavation work, OrbitalTerrainBudget budget, long start) {
        ServerLevel world = player.serverLevel();
        for (int scans = 0, broken = 0; scans < 256 && broken < 64 && work.cursor < work.cells.size()
                && budget.available(start); scans++) {
            BlockPos pos = work.cells.get(work.cursor++);
            budget.scans--;
            if (!world.isInWorldBounds(pos) || !world.hasChunkAt(pos) || !world.getWorldBorder().isWithinBounds(pos)
                    || !world.mayInteract(player, pos) || !EsperDomain.allows(world, player, Vec3.atCenterOf(pos))) continue;
            var state = world.getBlockState(pos);
            if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                    || state.getDestroySpeed(world, pos) < 0) continue;
            if (player.gameMode.destroyBlock(pos)) {
                broken++;
                budget.blocks--;
            }
        }
    }

    private EsperCollapse() {}
}
