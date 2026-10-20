package cn.starfall.blueprint.test;

import cn.starfall.blueprint.MaterialBatch;
import cn.starfall.blueprint.StarfallBlueprint;
import cn.starfall.blueprint.SupplyLedger;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class SupplyGameTests {
    private static final String BATCH = "public_supply";
    private static final BlockPos FIRST = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND = new BlockPos(3, 1, 1);

    private static FakePlayer player(GameTestHelper h) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(),
                new GameProfile(UUID.randomUUID(), "SupplyTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(false);
        player.moveTo(Vec3.atCenterOf(h.absolutePos(FIRST)).add(0, 0, 1));
        return player;
    }

    private static ItemStack crystal(int amount) {
        ItemStack stack = new ItemStack(StarfallBlueprint.CRYSTAL.get(), amount);
        stack.set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(58));
        return stack;
    }

    private static int count(Inventory inventory, Item item) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static List<ItemStack> snapshot(Inventory inventory) {
        List<ItemStack> saved = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) saved.add(inventory.getItem(i).copy());
        return saved;
    }

    private static void restore(Inventory inventory, List<ItemStack> saved) {
        for (int i = 0; i < saved.size(); i++) inventory.setItem(i, saved.get(i).copy());
        inventory.setChanged();
    }

    private static void assertUnchanged(GameTestHelper h, Inventory inventory, List<ItemStack> saved) {
        for (int i = 0; i < saved.size(); i++) {
            ItemStack actual = inventory.getItem(i), expected = saved.get(i);
            h.assertTrue(actual.getCount() == expected.getCount()
                            && ItemStack.isSameItemSameComponents(actual, expected),
                    "Rejected exchange preserves every inventory slot, including components: " + i);
        }
    }

    private static InteractionResult use(GameTestHelper h, BlockPos local, FakePlayer player, InteractionHand hand) {
        BlockPos absolute = h.absolutePos(local);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        return player.gameMode.useItemOn(player, h.getLevel(), player.getItemInHand(hand), hand, hit);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void emptyHandAndManualOnlyDescribeStock(GameTestHelper h) {
        FakePlayer player = player(h);
        SupplyLedger ledger = SupplyLedger.get(h.getLevel());
        int stock = ledger.remaining(h.getLevel());
        try {
            player.getInventory().clearContent();
            h.setBlock(FIRST, StarfallBlueprint.SUPPLY_TERMINAL.get());
            for (int i = 0; i < 3; i++) use(h, FIRST, player, InteractionHand.MAIN_HAND);
            h.assertTrue(player.getInventory().isEmpty(), "Empty-hand requests only describe stock; they never grant free materials");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StarfallBlueprint.GUIDE.get()));
            List<ItemStack> before = snapshot(player.getInventory());
            for (int i = 0; i < 3; i++) use(h, FIRST, player, InteractionHand.MAIN_HAND);
            assertUnchanged(h, player.getInventory(), before);
            h.assertTrue(ledger.remaining(h.getLevel()) == stock, "Reading with a manual cannot enroll, reward, or consume a trade");
        } finally { player.getInventory().clearContent(); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void isolatedLedgerRejectsBadSamplesAndCommitsOnlyWholeRewards(GameTestHelper h) {
        FakePlayer player = player(h);
        Inventory inventory = player.getInventory();
        List<ItemStack> original = snapshot(inventory);
        int selected = inventory.selected;
        SupplyLedger ledger = new SupplyLedger();
        try {
            inventory.clearContent();
            inventory.selected = 0;
            List<ItemStack> bad = new ArrayList<>();
            ItemStack missing = crystal(2);
            missing.remove(StarfallBlueprint.BATCH.get());
            bad.add(missing);
            for (MaterialBatch batch : new MaterialBatch[]{
                    new MaterialBatch("meteor_phase", 10_000, 9_000, 58, 1, "granules", "common"),
                    MaterialBatch.crystal(19),
                    new MaterialBatch("meteor_phase", 10_000, 9_000, 58, 0, "granules", "anima")}) {
                ItemStack invalid = crystal(2);
                invalid.set(StarfallBlueprint.BATCH.get(), batch);
                bad.add(invalid);
            }
            int stock = ledger.remaining(h.getLevel());
            for (ItemStack invalid : bad) {
                player.setItemInHand(InteractionHand.MAIN_HAND, invalid);
                List<ItemStack> before = snapshot(inventory);
                h.assertTrue(ledger.exchange(player, InteractionHand.MAIN_HAND) == SupplyLedger.Result.INVALID_SAMPLE,
                        "Missing metadata, pollution, insufficient integrity and wrong route are rejected");
                assertUnchanged(h, inventory, before);
                h.assertTrue(ledger.remaining(h.getLevel()) == stock, "Invalid samples never consume stock");
            }
            inventory.clearContent();
            for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            player.setItemInHand(InteractionHand.MAIN_HAND, crystal(2));
            List<ItemStack> full = snapshot(inventory);
            h.assertTrue(ledger.exchange(player, InteractionHand.MAIN_HAND) == SupplyLedger.Result.INVENTORY_FULL,
                    "Consuming one of two samples leaves no normal slot for iron");
            assertUnchanged(h, inventory, full);
            h.assertTrue(ledger.remaining(h.getLevel()) == stock, "Full inventory preserves daily stock");
            player.setItemInHand(InteractionHand.MAIN_HAND, crystal(1));
            h.assertTrue(ledger.exchange(player, InteractionHand.MAIN_HAND) == SupplyLedger.Result.SUCCESS,
                    "Consuming the final main-hand sample frees the reward slot even in a full inventory");
            h.assertTrue(player.getMainHandItem().is(Items.IRON_INGOT)
                            && count(inventory, Items.IRON_INGOT) == 1
                            && count(inventory, StarfallBlueprint.CRYSTAL.get()) == 0
                            && ledger.remaining(h.getLevel()) == stock - 1,
                    "Exactly one sample becomes one iron and consumes one trade");
            player.setItemInHand(InteractionHand.OFF_HAND, crystal(1));
            // Slot zero now contains one iron, so it can accept the offhand reward by merging.
            h.assertTrue(ledger.exchange(player, InteractionHand.OFF_HAND) == SupplyLedger.Result.SUCCESS
                            && player.getOffhandItem().isEmpty() && player.getMainHandItem().getCount() == 2,
                    "Offhand input can merge its iron reward into an existing normal stack");
        } finally {
            restore(inventory, original);
            inventory.selected = selected;
        }
        h.succeed();
    }

    /** This is the only test that installs or trades against the real world account and changes time.
     * All operations are synchronous: same-batch parallel GameTests cannot interleave scheduled ticks here. */
    @GameTest(template = "empty", batch = BATCH)
    public static void realTerminalsShareStockAcrossPlayersAndWorldDates(GameTestHelper h) {
        var overworld = h.getLevel().getServer().overworld();
        long originalTime = overworld.getDayTime();
        SupplyLedger originalLedger = SupplyLedger.get(h.getLevel());
        FakePlayer first = player(h), second = player(h);
        List<ItemStack> firstInventory = snapshot(first.getInventory());
        List<ItemStack> secondInventory = snapshot(second.getInventory());
        int firstSelected = first.getInventory().selected, secondSelected = second.getInventory().selected;
        try {
            overworld.getDataStorage().set(SupplyLedger.DATA_NAME, new SupplyLedger());
            SupplyLedger ledger = SupplyLedger.get(h.getLevel());
            overworld.setDayTime(23_999L);
            first.getInventory().clearContent();
            second.getInventory().clearContent();
            first.getInventory().selected = 0;
            second.getInventory().selected = 0;
            h.setBlock(FIRST, StarfallBlueprint.SUPPLY_TERMINAL.get());
            h.setBlock(SECOND, StarfallBlueprint.SUPPLY_TERMINAL.get());
            first.setItemInHand(InteractionHand.MAIN_HAND, crystal(3));
            first.setItemInHand(InteractionHand.OFF_HAND, crystal(2));
            h.assertTrue(use(h, FIRST, first, InteractionHand.MAIN_HAND).consumesAction(),
                    "Real main-hand terminal interaction consumes the action");
            h.assertTrue(first.getMainHandItem().getCount() == 2 && first.getOffhandItem().getCount() == 2
                            && count(first.getInventory(), Items.IRON_INGOT) == 1 && ledger.remaining(h.getLevel()) == 3,
                    "One real click settles exactly one main-hand trade and does not touch the offhand");
            h.assertTrue(use(h, SECOND, first, InteractionHand.OFF_HAND).consumesAction(),
                    "Real offhand terminal interaction consumes the action");
            h.assertTrue(first.getMainHandItem().getCount() == 2 && first.getOffhandItem().getCount() == 1
                            && count(first.getInventory(), Items.IRON_INGOT) == 2 && ledger.remaining(h.getLevel()) == 2,
                    "A second terminal uses the same stock and preserves the other hand");
            second.setItemInHand(InteractionHand.MAIN_HAND, crystal(3));
            use(h, FIRST, second, InteractionHand.MAIN_HAND);
            use(h, SECOND, first, InteractionHand.MAIN_HAND);
            h.assertTrue(ledger.remaining(h.getLevel()) == 0, "Two players and two terminals share exactly four trades");
            List<ItemStack> rejected = snapshot(second.getInventory());
            use(h, SECOND, second, InteractionHand.MAIN_HAND);
            assertUnchanged(h, second.getInventory(), rejected);
            h.assertTrue(count(first.getInventory(), Items.IRON_INGOT)
                            + count(second.getInventory(), Items.IRON_INGOT) == 4,
                    "The fifth real terminal click awards no iron and consumes no sample");

            // Use independent instances for serialization; never replace the live account with a roundtrip clone.
            CompoundTag saved = ledger.save(new CompoundTag(), h.getLevel().registryAccess());
            SupplyLedger loaded = SupplyLedger.load(saved, h.getLevel().registryAccess());
            h.assertTrue(loaded.remaining(h.getLevel()) == 0, "Save/load preserves an exhausted day");
            overworld.setDayTime(24_000L);
            h.assertTrue(loaded.remaining(h.getLevel()) == 4, "The exact next-day boundary refills four trades");
            h.assertTrue(loaded.exchange(second, InteractionHand.MAIN_HAND) == SupplyLedger.Result.SUCCESS
                            && loaded.remaining(h.getLevel()) == 3,
                    "A reloaded independent account can transact once on its new day");
            overworld.setDayTime(23_999L);
            h.assertTrue(loaded.remaining(h.getLevel()) == 3, "Rolling back time does not refill stock");
            overworld.setDayTime(24_000L);
            h.assertTrue(loaded.remaining(h.getLevel()) == 3, "Returning to the already-seen day does not refill stock");
            loaded = SupplyLedger.load(loaded.save(new CompoundTag(), h.getLevel().registryAccess()),
                    h.getLevel().registryAccess());
            h.assertTrue(loaded.remaining(h.getLevel()) == 3, "Roundtrip preserves partial stock and its latest supply day");
            overworld.setDayTime(7L * 24_000L);
            h.assertTrue(loaded.remaining(h.getLevel()) == 4, "Skipping days gives four current trades, not accumulated stock");
            h.assertTrue(ledger.remaining(h.getLevel()) == 4,
                    "Independent ledger trades never consume the live world's new-day stock");
        } finally {
            overworld.setDayTime(originalTime);
            overworld.getDataStorage().set(SupplyLedger.DATA_NAME, originalLedger);
            restore(first.getInventory(), firstInventory);
            restore(second.getInventory(), secondInventory);
            first.getInventory().selected = firstSelected;
            second.getInventory().selected = secondSelected;
        }
        h.succeed();
    }
}
