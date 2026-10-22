package cn.starfall.blueprint;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.ArrayList;
import java.util.List;

/** All settlement terminals currently represent one public craftsman's supply account. */
public final class SupplyLedger extends SavedData {
    public static final String DATA_NAME = "starfall_public_supply";
    public static final int DAILY_TRADES = 4;
    private long supplyDay = -1;
    private int trades;
    private int lunarTrades;
    public enum Result { SUCCESS, INVENTORY_FULL, INVALID_SAMPLE, SOLD_OUT, UNAVAILABLE }

    public static SupplyLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SupplyLedger::new, SupplyLedger::load), DATA_NAME);
    }

    public static SupplyLedger load(CompoundTag tag, HolderLookup.Provider provider) {
        SupplyLedger ledger = new SupplyLedger();
        ledger.supplyDay = Math.max(-1, tag.getLong("SupplyDay"));
        ledger.trades = Math.clamp(tag.getInt("Trades"), 0, DAILY_TRADES);
        ledger.lunarTrades=Math.clamp(tag.getInt("LunarTrades"),0,8);
        return ledger;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("SchemaVersion", 1);
        tag.putLong("SupplyDay", supplyDay);
        tag.putInt("Trades", trades);
        tag.putInt("LunarTrades",lunarTrades);
        return tag;
    }

    private void advanceDay(ServerLevel level) {
        long day = Math.floorDiv(Math.max(0L, level.getServer().overworld().getDayTime()), 24_000L);
        // Time rollback must not refill stock. Only a new, previously unseen world date can do so.
        if (day > supplyDay) { supplyDay = day; trades = 0; setDirty(); }
    }

    public int remaining(ServerLevel level) { advanceDay(level); return DAILY_TRADES - trades; }
    // Retain the API and saved counter for compatibility, but no future-dimension supplies are open.
    public int lunarRemaining(ServerLevel level){return 0;}
    public Result importLunar(ServerPlayer player,InteractionHand hand){return Result.UNAVAILABLE;}

    public Result exchange(ServerPlayer player, InteractionHand hand) {
        ItemStack sample = player.getItemInHand(hand);
        MaterialBatch batch = sample.get(StarfallBlueprint.BATCH.get());
        if (!sample.is(StarfallBlueprint.CRYSTAL.get()) || batch == null || !batch.isQ0()) return Result.INVALID_SAMPLE;
        if (remaining(player.serverLevel()) == 0) return Result.SOLD_OUT;
        Inventory inventory = player.getInventory();
        List<ItemStack> plan = snapshot(inventory);
        int source = hand == InteractionHand.MAIN_HAND ? inventory.selected : 40;
        plan.get(source).shrink(1);
        if (!insert(plan, new ItemStack(Items.IRON_INGOT))) return Result.INVENTORY_FULL;
        // Server-thread transaction: nothing is consumed, awarded, or counted before all checks pass.
        commit(player, plan);
        trades++; setDirty();
        return Result.SUCCESS;
    }

    private static List<ItemStack> snapshot(Inventory inventory) {
        List<ItemStack> plan = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) plan.add(inventory.getItem(slot).copy());
        return plan;
    }

    private static boolean insert(List<ItemStack> plan, ItemStack offered) {
        ItemStack remaining = offered.copy();
        // Rewards go only into normal storage, never into armor or the offhand.
        for (int slot = 0; slot < 36 && !remaining.isEmpty(); slot++) {
            ItemStack target = plan.get(slot);
            if (!target.isEmpty() && ItemStack.isSameItemSameComponents(target, remaining)) {
                int count = Math.min(remaining.getCount(), target.getMaxStackSize() - target.getCount());
                target.grow(count); remaining.shrink(count);
            }
        }
        for (int slot = 0; slot < 36 && !remaining.isEmpty(); slot++) {
            if (plan.get(slot).isEmpty()) {
                int count = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                plan.set(slot, remaining.copyWithCount(count)); remaining.shrink(count);
            }
        }
        return remaining.isEmpty();
    }

    private static void commit(ServerPlayer player, List<ItemStack> plan) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < plan.size(); slot++) inventory.setItem(slot, plan.get(slot));
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }
}
