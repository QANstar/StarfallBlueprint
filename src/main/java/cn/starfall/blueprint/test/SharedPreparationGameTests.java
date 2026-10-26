package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Two survival players interleave real menu clicks on one authoritative machine. */
@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class SharedPreparationGameTests {
    private static final BlockPos POS = new BlockPos(2, 1, 2);

    private static final class Session implements AutoCloseable {
        final PreparationBlockEntity bench;
        final FakePlayer first, second;
        final IndustryMenu a, b;

        Session(GameTestHelper h) {
            h.setBlock(POS, StarfallBlueprint.PREPARATION.get());
            bench = (PreparationBlockEntity) h.getBlockEntity(POS);
            first = player(h);
            second = player(h);
            a = (IndustryMenu) bench.createMenu(1, first.getInventory(), first);
            b = (IndustryMenu) bench.createMenu(2, second.getInventory(), second);
            h.assertTrue(a.stillValid(first) && b.stillValid(second), "Both nearby survival players can use the same bench");
        }

        @Override public void close() {
            a.removed(first);
            b.removed(second);
            first.getInventory().clearContent();
            second.getInventory().clearContent();
        }
    }

    private static FakePlayer player(GameTestHelper h) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "SharedBench"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        BlockPos pos = h.absolutePos(POS);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
        return player;
    }

    private static ItemStack ore(int count) {
        ItemStack stack = new ItemStack(StarfallBlueprint.RAW.get(), count);
        stack.set(StarfallBlueprint.BATCH.get(), MaterialBatch.ore(12, 40));
        return stack;
    }

    private static void supply(PreparationBlockEntity bench, int count) {
        bench.setItem(0, ore(count));
        bench.setItem(1, new ItemStack(Items.COAL));
        bench.water = 1000;
    }

    private static void ticks(PreparationBlockEntity bench, int count) {
        for (int i = 0; i < count; i++) {
            PreparationBlockEntity.tick(bench.getLevel(), bench.getBlockPos(), bench.getBlockState(), bench);
        }
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void assertQuality(GameTestHelper h, Container inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(StarfallBlueprint.CRYSTAL.get())) {
                h.assertTrue(MaterialBatch.crystal(38).equals(stack.get(StarfallBlueprint.BATCH.get())),
                        "Menu transfer preserves crystal quality and mass");
            } else if (stack.is(StarfallBlueprint.TAILINGS.get())) {
                h.assertTrue(MaterialBatch.tailings(12).equals(stack.get(StarfallBlueprint.BATCH.get())),
                        "Menu transfer preserves tailings mass");
            }
        }
    }

    @GameTest(template = "empty")
    public static void twoMenusQuickMoveSettlesEachOutputOnce(GameTestHelper h) {
        try (Session s = new Session(h)) {
            supply(s.bench, 1);
            ticks(s.bench, 200);
            // Both viewers see the output before either request is processed.
            h.assertTrue(s.a.getSlot(9).getItem().getCount() == 10 && s.b.getSlot(9).getItem().getCount() == 10,
                    "Both menus initially view the same ten crystals");
            s.a.clicked(9, 0, ClickType.QUICK_MOVE, s.first);
            s.b.clicked(9, 0, ClickType.QUICK_MOVE, s.second);
            s.b.clicked(10, 0, ClickType.QUICK_MOVE, s.second);
            s.a.clicked(10, 0, ClickType.QUICK_MOVE, s.first);
            h.assertTrue(count(s.first.getInventory(), StarfallBlueprint.CRYSTAL.get()) == 10
                            && count(s.second.getInventory(), StarfallBlueprint.CRYSTAL.get()) == 0
                            && count(s.first.getInventory(), StarfallBlueprint.TAILINGS.get()) == 0
                            && count(s.second.getInventory(), StarfallBlueprint.TAILINGS.get()) == 1,
                    "Later clicks on either already-emptied output cannot duplicate it");
            assertQuality(h, s.first.getInventory());
            assertQuality(h, s.second.getInventory());
            ticks(s.bench, 40);
            h.assertTrue(s.bench.isEmpty() && s.bench.water == 750 && s.bench.energy == 280000,
                    "Shared extraction settles exactly one batch and idle ticks cannot recreate output");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void twoMenusSplitPickupAndCloseConservesOutput(GameTestHelper h) {
        try (Session s = new Session(h)) {
            supply(s.bench, 1);
            ticks(s.bench, 200);
            s.a.clicked(9, 1, ClickType.PICKUP, s.first);
            s.b.clicked(9, 0, ClickType.PICKUP, s.second);
            h.assertTrue(s.a.getCarried().getCount() == 5 && s.b.getCarried().getCount() == 5
                            && s.bench.getItem(2).isEmpty(),
                    "Right pickup and another viewer's left pickup split one stack into two halves");
            h.assertTrue(MaterialBatch.crystal(38).equals(s.a.getCarried().get(StarfallBlueprint.BATCH.get()))
                            && MaterialBatch.crystal(38).equals(s.b.getCarried().get(StarfallBlueprint.BATCH.get())),
                    "Both cursors keep the product component");
            s.a.removed(s.first);
            s.b.removed(s.second);
            h.assertTrue(s.a.getCarried().isEmpty() && s.b.getCarried().isEmpty()
                            && count(s.first.getInventory(), StarfallBlueprint.CRYSTAL.get()) == 5
                            && count(s.second.getInventory(), StarfallBlueprint.CRYSTAL.get()) == 5,
                    "Closing both menus returns cursor items to each player's inventory exactly once");
            s.a.removed(s.first);
            s.b.removed(s.second);
            h.assertTrue(count(s.first.getInventory(), StarfallBlueprint.CRYSTAL.get())
                            + count(s.second.getInventory(), StarfallBlueprint.CRYSTAL.get()) == 10,
                    "Repeated close does not return the same cursor stack twice");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void twoMenusConnectorChangesKeepReservedBatchBudget(GameTestHelper h) {
        try (Session s = new Session(h)) {
            supply(s.bench, 2);
            s.first.getInventory().setItem(0, new ItemStack(StarfallBlueprint.CONNECTOR.get(), 2));
            s.second.getInventory().setItem(0, new ItemStack(StarfallBlueprint.CONNECTOR.get(), 2));
            // Hotbar slot zero remains menu index 54; upgrades use the growing sidebar.
            int bay=IndustryMenu.UPGRADE_MENU_START;
            s.a.clicked(54, 0, ClickType.QUICK_MOVE, s.first);
            s.b.clicked(54, 0, ClickType.QUICK_MOVE, s.second);
            h.assertTrue(count(s.bench.upgrades, StarfallBlueprint.CONNECTOR.get()) == 4
                            && count(s.first.getInventory(), StarfallBlueprint.CONNECTOR.get()) == 0
                            && count(s.second.getInventory(), StarfallBlueprint.CONNECTOR.get()) == 0,
                    "Both viewers install their actual stacks without losing or duplicating connectors");
            ticks(s.bench, 73);
            s.b.clicked(bay, 0, ClickType.QUICK_MOVE, s.second);
            s.a.clicked(bay, 0, ClickType.QUICK_MOVE, s.first);
            h.assertTrue(count(s.bench.upgrades, StarfallBlueprint.CONNECTOR.get())==4,"Both viewers are locked out of in-flight upgrades");
            ticks(s.bench, 127);
            h.assertTrue(s.bench.energy == 292307 && s.bench.getItem(2).getCount() == 10,
                    "Four modules reserve and settle ceil(40000*9/13) XH once");
            s.b.clicked(bay,0,ClickType.QUICK_MOVE,s.second);
            ticks(s.bench, 200);
            h.assertTrue(s.bench.energy == 252307 && s.bench.water == 500
                            && s.bench.getItem(2).getCount() == 20 && s.bench.getItem(3).getCount() == 2
                            && s.bench.upgrades.isEmpty(),
                    "Only the next batch uses the ordinary 40kXH budget");
            h.assertTrue(count(s.first.getInventory(), StarfallBlueprint.CONNECTOR.get())
                            + count(s.second.getInventory(), StarfallBlueprint.CONNECTOR.get()) == 4,
                    "Both initial connector inventories are conserved after installation and shared removal");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void partialViewerCapacityLeavesExactRemainder(GameTestHelper h) {
        try (Session s = new Session(h)) {
            supply(s.bench, 1);
            ticks(s.bench, 200);
            for (int i = 0; i < 36; i++) s.first.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            ItemStack existing = new ItemStack(StarfallBlueprint.CRYSTAL.get(), 62);
            existing.set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(38));
            s.first.getInventory().setItem(0, existing);
            s.a.clicked(9, 0, ClickType.QUICK_MOVE, s.first);
            h.assertTrue(count(s.first.getInventory(), StarfallBlueprint.CRYSTAL.get()) == 64
                            && s.bench.getItem(2).getCount() == 8,
                    "A partially full matching stack accepts exactly two crystals and leaves eight in the machine");
            s.b.clicked(9, 0, ClickType.QUICK_MOVE, s.second);
            s.a.clicked(9, 0, ClickType.QUICK_MOVE, s.first);
            h.assertTrue(count(s.second.getInventory(), StarfallBlueprint.CRYSTAL.get()) == 8
                            && count(s.first.getInventory(), StarfallBlueprint.CRYSTAL.get()) == 64
                            && s.bench.getItem(2).isEmpty(),
                    "The other viewer receives only the remainder; seeded inventory plus one batch totals 72 crystals");
            assertQuality(h, s.first.getInventory());
            assertQuality(h, s.second.getInventory());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullViewerInventoryLeavesProductsForOtherViewer(GameTestHelper h) {
        try (Session s = new Session(h)) {
            supply(s.bench, 1);
            ticks(s.bench, 200);
            for (int i = 0; i < 36; i++) s.first.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            s.a.clicked(9, 0, ClickType.QUICK_MOVE, s.first);
            s.a.clicked(10, 0, ClickType.QUICK_MOVE, s.first);
            h.assertTrue(s.bench.getItem(2).getCount() == 10 && s.bench.getItem(3).getCount() == 1,
                    "A viewer without capacity cannot consume either output by shift clicking");
            s.b.clicked(9, 0, ClickType.QUICK_MOVE, s.second);
            s.b.clicked(10, 0, ClickType.QUICK_MOVE, s.second);
            h.assertTrue(count(s.second.getInventory(), StarfallBlueprint.CRYSTAL.get()) == 10
                            && count(s.second.getInventory(), StarfallBlueprint.TAILINGS.get()) == 1
                            && count(s.first.getInventory(), Items.STONE) == 36 * 64
                            && s.bench.isEmpty(),
                    "The second viewer can take the unchanged products, while the full inventory stays intact");
            assertQuality(h, s.second.getInventory());
        }
        h.succeed();
    }
}
