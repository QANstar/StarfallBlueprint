package cn.starfall.blueprint;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** Observe the actual break's event; collect only after every listener and spawning has completed. */
@EventBusSubscriber(modid = "starfall_blueprint")
public final class BestMechLoot {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private BestMechLoot() {}

    @SubscribeEvent(receiveCanceled = true)
    public static void drops(BlockDropsEvent event) {
        Scope scope = CURRENT.get();
        if(scope != null && event.getLevel() == scope.player.serverLevel()
                && event.getBreaker() == scope.player && event.getPos().equals(scope.pos)
                && ItemStack.matches(event.getTool(), scope.tool)) scope.events.add(event);
    }

    static Scope begin(ServerPlayer player, BlockPos pos, ItemStack tool) {
        Scope scope = new Scope(player, pos.immutable(), tool.copy(), CURRENT.get());
        CURRENT.set(scope); return scope;
    }

    static final class Scope implements AutoCloseable {
        final ServerPlayer player;
        final BlockPos pos;
        final ItemStack tool;
        final Scope previous;
        final List<BlockDropsEvent> events = new ArrayList<>();
        Scope(ServerPlayer player, BlockPos pos, ItemStack tool, Scope previous) {
            this.player = player; this.pos = pos; this.tool = tool; this.previous = previous;
        }
        @Override public void close() {
            if(previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
        void collect(BestMechEntity mech) {
            for(var event : events) {
                if(event.isCanceled()) continue;
                for(ItemEntity entity : event.getDrops()) {
                    if(entity.isRemoved() || !entity.isAddedToLevel()) continue;
                    ItemStack remaining = entity.getItem();
                    if(remaining.isEmpty() || remaining.getItem() instanceof BestMechItem) continue;
                    // Merge exact components before occupying empty cells; never address hidden backing slots.
                    for(int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
                        for(int slot = 0; slot < mech.cargoSlots() && !remaining.isEmpty(); slot++) {
                            ItemStack stored = mech.cargo().getItem(slot);
                            if(pass == 0 && !stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, remaining)) {
                                int moved = Math.min(remaining.getCount(), Math.max(0, Math.min(stored.getMaxStackSize(), mech.cargo().getMaxStackSize()) - stored.getCount()));
                                stored.grow(moved); remaining.shrink(moved);
                            } else if(pass == 1 && stored.isEmpty()) {
                                int moved = Math.min(remaining.getCount(), Math.min(remaining.getMaxStackSize(), mech.cargo().getMaxStackSize()));
                                mech.cargo().setItem(slot, remaining.split(moved));
                            }
                        }
                    }
                    mech.cargo().setChanged();
                    if(remaining.isEmpty()) entity.discard(); else entity.setItem(remaining);
                }
            }
        }
    }
}
