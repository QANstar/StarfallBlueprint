package cn.starfall.blueprint.test;

import cn.starfall.blueprint.BestEngineering;
import cn.starfall.blueprint.BestEquipment;
import cn.starfall.blueprint.BestWorkbenchMenu;
import cn.starfall.blueprint.CatalogueContent;
import cn.starfall.blueprint.CatalogueEquipment;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Player transactions and actual survival mining; every player has an isolated profile. */
@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class BestEngineeringGameTests {
    private static final BlockPos WORKBENCH = new BlockPos(1, 1, 1);
    private static final BlockPos ORE = new BlockPos(2, 1, 2);

    private static FakePlayer player(GameTestHelper h) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "BestEngineering"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.setShiftKeyDown(false);
        player.setSprinting(false);
        player.getAbilities().flying = false;
        nearWorkbench(h, player);
        return player;
    }

    private static void nearWorkbench(GameTestHelper h, FakePlayer player) {
        BlockPos absolute = h.absolutePos(WORKBENCH);
        player.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 1.5);
    }

    private static void inputs(FakePlayer player, String[] keys, int[] counts) {
        player.getInventory().clearContent();
        for (int i = 0; i < keys.length; i++) player.getInventory().setItem(i, CatalogueContent.stack(keys[i], counts[i]));
    }

    private static void learn(GameTestHelper h, FakePlayer player, int level) {
        if (level >= 1) {
            inputs(player, new String[]{"steel", "board"}, new int[]{2, 1});
            h.assertTrue(BestEngineering.perform(player, 0) && BestEngineering.researchLevel(player) == 1,
                    "The real basic research transaction unlocks level one");
        }
        if (level >= 2) {
            inputs(player, new String[]{"load", "conductor"}, new int[]{1, 1});
            h.assertTrue(BestEngineering.perform(player, 1) && BestEngineering.researchLevel(player) == 2,
                    "The real drill research transaction unlocks level two");
        }
        player.getInventory().clearContent();
    }

    private static List<ItemStack> snapshot(FakePlayer player) {
        List<ItemStack> copy = new ArrayList<>();
        for (int i = 0; i < 36; i++) copy.add(player.getInventory().getItem(i).copy());
        return copy;
    }

    private static void unchanged(GameTestHelper h, FakePlayer player, List<ItemStack> before, String reason) {
        for (int i = 0; i < 36; i++) {
            h.assertTrue(ItemStack.matches(before.get(i), player.getInventory().getItem(i)), reason + "; slot=" + i);
        }
    }

    private static int count(FakePlayer player, String key) {
        return player.getInventory().items.stream().filter(stack -> CatalogueContent.matches(key, stack))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static ItemStack equipment(String key, int energy, int heat, int wear) {
        ItemStack stack = CatalogueContent.stack(key, 1);
        var data = CatalogueEquipment.getData(stack);
        data.putInt("BestEnergy", energy);
        data.putInt("BestHeat", heat);
        data.putInt("BestWear", wear);
        CatalogueEquipment.setData(stack, data);
        return stack;
    }

    private static ItemStack battery(int energy) {
        ItemStack stack = CatalogueContent.stack("s01", 1);
        var data = CatalogueEquipment.getData(stack);
        data.putInt("Energy", energy);
        CatalogueEquipment.setData(stack, data);
        return stack;
    }

    @GameTest(template = "empty")
    public static void researchRequiresOrderIsPersonalAndDuplicateNeverChargesAgain(GameTestHelper h) {
        FakePlayer first = player(h), other = player(h);
        try {
            inputs(first, new String[]{"load", "conductor"}, new int[]{2, 2});
            List<ItemStack> before = snapshot(first);
            h.assertTrue(!BestEngineering.perform(first, 1) && BestEngineering.researchLevel(first) == 0,
                    "Drill research cannot precede basic engineering");
            unchanged(h, first, before, "Wrong-order research consumes no materials");
            inputs(first, new String[]{"steel", "board"}, new int[]{6, 3});
            h.assertTrue(BestEngineering.perform(first, 0) && BestEngineering.researchLevel(first) == 1,
                    "Basic engineering can be learned with the exact transaction");
            h.assertTrue(count(first, "steel") == 4 && count(first, "board") == 2,
                    "Basic research consumes exactly two steel and one board");
            before = snapshot(first);
            h.assertTrue(!BestEngineering.perform(first, 0), "Already learned basic research is rejected");
            unchanged(h, first, before, "Repeated basic research is free of side effects");
            inputs(first, new String[]{"load", "conductor"}, new int[]{3, 3});
            h.assertTrue(BestEngineering.perform(first, 1) && BestEngineering.researchLevel(first) == 2,
                    "Drill research follows the personal basic unlock");
            h.assertTrue(count(first, "load") == 2 && count(first, "conductor") == 2,
                    "Drill research consumes exactly one load crystal and one conductor");
            before = snapshot(first);
            h.assertTrue(!BestEngineering.perform(first, 1), "Already learned drill research is rejected");
            unchanged(h, first, before, "Repeated drill research never consumes another crystal");
            h.assertTrue(BestEngineering.researchLevel(other) == 0,
                    "A different random player profile does not inherit another player's research");
            inputs(other, new String[]{"steel", "load", "board", "steel_fix"}, new int[]{4, 2, 1, 2});
            before = snapshot(other);
            h.assertTrue(!BestEngineering.perform(other, 2), "Another player cannot assemble from somebody else's unlock");
            unchanged(h, other, before, "Missing personal permission consumes no assembly materials");
        } finally {
            first.getInventory().clearContent();
            other.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullInventoryAssemblyFailsAtomicallyThenFreedSlotSucceeds(GameTestHelper h) {
        FakePlayer player = player(h);
        try {
            learn(h, player, 1);
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            String[] keys = {"steel", "load", "board", "steel_fix"};
            for (int i = 0; i < keys.length; i++) player.getInventory().setItem(i, CatalogueContent.stack(keys[i], 64));
            List<ItemStack> before = snapshot(player);
            h.assertTrue(!BestEngineering.perform(player, 2), "Assembly refuses a genuinely full inventory after input subtraction");
            unchanged(h, player, before, "A blocked output cannot swallow any assembly input");
            h.assertTrue(count(player, "best_frame") == 0, "Failure cannot create or drop a frame into the inventory");
            player.getInventory().setItem(35, ItemStack.EMPTY);
            h.assertTrue(BestEngineering.perform(player, 2), "One free output slot makes the same frame transaction succeed");
            h.assertTrue(count(player, "steel") == 60 && count(player, "load") == 62
                    && count(player, "board") == 63 && count(player, "steel_fix") == 62
                    && count(player, "best_frame") == 1, "Successful frame assembly consumes the exact bill once");
            ItemStack frame = player.getInventory().getItem(35);
            h.assertTrue(frame.is(CatalogueContent.item("best_frame")) && BestEquipment.charge(frame) == 0
                    && BestEquipment.heat(frame) == 0 && BestEquipment.wear(frame) == 0,
                    "Fresh assembly produces one uncharged, cool, unworn frame in the reserved output slot");
        } finally { player.getInventory().clearContent(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cutterAssemblyRequiresDrillResearchAndConsumesItsExactBill(GameTestHelper h) {
        FakePlayer player = player(h);
        try {
            learn(h, player, 1);
            inputs(player, new String[]{"steel", "conductor", "head", "board"}, new int[]{3, 2, 1, 1});
            List<ItemStack> before = snapshot(player);
            h.assertTrue(!BestEngineering.perform(player, 3), "Basic knowledge alone cannot assemble the drill cutter");
            unchanged(h, player, before, "Missing drill knowledge leaves every input intact");
            inputs(player, new String[]{"load", "conductor"}, new int[]{1, 1});
            h.assertTrue(BestEngineering.perform(player, 1), "The same player learns the missing drill knowledge");
            inputs(player, new String[]{"steel", "conductor", "head", "board"}, new int[]{3, 2, 1, 1});
            h.assertTrue(BestEngineering.perform(player, 3), "Qualified cutter assembly succeeds");
            h.assertTrue(count(player, "best_cutter") == 1 && count(player, "steel") == 0
                    && count(player, "conductor") == 0 && count(player, "head") == 0 && count(player, "board") == 0,
                    "Assembly consumes all four exact inputs and produces one cutter");
        } finally { player.getInventory().clearContent(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void repairPreservesChargeHeatLockNameAndOtherData(GameTestHelper h) {
        FakePlayer player = player(h);
        try {
            learn(h, player, 2);
            for (String key : List.of("best_frame", "best_cutter")) {
                player.getInventory().clearContent();
                ItemStack held = equipment(key, 12345, 713, 350);
                Component name = Component.literal("专用维修测试装备");
                held.set(DataComponents.CUSTOM_NAME, name);
                var data = CatalogueEquipment.getData(held);
                data.putBoolean("BestOverheated", true);
                data.putString("RepairSentinel", "preserve-me");
                CatalogueEquipment.setData(held, data);
                player.setItemInHand(InteractionHand.MAIN_HAND, held);
                player.getInventory().setItem(1, CatalogueContent.stack("steel", 3));
                h.assertTrue(BestEngineering.perform(player, 4), "A held damaged equipment piece accepts workbench repair: " + key);
                ItemStack repaired = player.getMainHandItem();
                h.assertTrue(BestEquipment.wear(repaired) == 150 && count(player, "steel") == 2,
                        "One steel repairs exactly 200 engineering wear: " + key);
                h.assertTrue(BestEquipment.charge(repaired) == 12345 && BestEquipment.heat(repaired) == 713
                        && CatalogueEquipment.getData(repaired).getBoolean("BestOverheated")
                        && "preserve-me".equals(CatalogueEquipment.getData(repaired).getString("RepairSentinel"))
                        && name.equals(repaired.get(DataComponents.CUSTOM_NAME)),
                        "Repair preserves stored energy, temperature, thermal lock, custom data and custom name: " + key);
                h.assertTrue(BestEngineering.perform(player, 4) && BestEquipment.wear(player.getMainHandItem()) == 0
                        && count(player, "steel") == 1, "Repair clamps residual wear at zero: " + key);
                List<ItemStack> before = snapshot(player);
                h.assertTrue(!BestEngineering.perform(player, 4), "Undamaged equipment cannot be repaired again: " + key);
                unchanged(h, player, before, "An unnecessary repair never charges another steel");
            }
        } finally { player.getInventory().clearContent(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void workbenchMenuRejectsInvalidIdsDistanceAndRemovedBlock(GameTestHelper h) {
        h.setBlock(WORKBENCH, BestEngineering.WORKBENCH.get());
        FakePlayer player = player(h);
        BestWorkbenchMenu menu = new BestWorkbenchMenu(1, player.getInventory(),
                ContainerLevelAccess.create(h.getLevel(), h.absolutePos(WORKBENCH)));
        try {
            inputs(player, new String[]{"steel", "board"}, new int[]{2, 1});
            List<ItemStack> before = snapshot(player);
            h.assertTrue(menu.slots.size() == 36 && menu.stillValid(player),
                    "The real block-bound menu contains only the 36 nearby player's inventory slots");
            h.assertTrue(!menu.clickMenuButton(player, -1) && !menu.clickMenuButton(player, 5)
                    && !menu.clickMenuButton(player, Integer.MAX_VALUE), "Only the five documented action IDs are accepted");
            unchanged(h, player, before, "Invalid button IDs cannot mutate the inventory");
            player.setPos(player.getX() + 20, player.getY(), player.getZ());
            h.assertTrue(!menu.stillValid(player) && !menu.clickMenuButton(player, 0),
                    "A menu packet received after walking away cannot execute research");
            unchanged(h, player, before, "Too-distant research consumes nothing");
            nearWorkbench(h, player);
            h.setBlock(WORKBENCH, Blocks.AIR);
            h.assertTrue(!menu.stillValid(player) && !menu.clickMenuButton(player, 0),
                    "A menu packet received after the workbench is removed cannot execute research");
            unchanged(h, player, before, "Removed-workbench research consumes nothing");
            h.assertTrue(BestEngineering.researchLevel(player) == 0, "All rejected requests leave personal research unchanged");
            h.setBlock(WORKBENCH, BestEngineering.WORKBENCH.get());
            h.assertTrue(menu.stillValid(player) && menu.clickMenuButton(player, 0)
                    && BestEngineering.researchLevel(player) == 1, "The nearby restored workstation executes the authorized action");
            // researchLevel() is the client DataSlot mirror; server assertions read the player's authoritative data.
            List<ItemStack> after = snapshot(player);
            menu.removed(player);
            unchanged(h, player, after, "Closing an inventory-only workbench never removes or drops owned items");
        } finally { player.getInventory().clearContent(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalDestroyBlockMinesOneIronOreAndChargesExactlyOnce(GameTestHelper h) {
        FakePlayer player = player(h);
        try {
            learn(h, player, 2);
            ItemStack cutter = equipment("best_cutter", 4096, 10, 7);
            player.setItemInHand(InteractionHand.MAIN_HAND, cutter);
            h.setBlock(ORE, Blocks.IRON_ORE);
            for (BlockPos adjacent : List.of(ORE.north(), ORE.south(), ORE.east(), ORE.west(), ORE.above(), ORE.below())) {
                h.setBlock(adjacent, Blocks.IRON_ORE);
            }
            h.assertTrue(player.gameMode.destroyBlock(h.absolutePos(ORE)), "A qualified survival player can break real iron ore with the cutter");
            h.assertTrue(h.getBlockState(ORE).isAir(), "The actual server destroyBlock removes the selected ore");
            for (BlockPos adjacent : List.of(ORE.north(), ORE.south(), ORE.east(), ORE.west(), ORE.above(), ORE.below())) {
                h.assertTrue(h.getBlockState(adjacent).is(Blocks.IRON_ORE), "Single-point drill cutting leaves every adjacent block intact");
            }
            h.assertTrue(BestEquipment.charge(cutter) == 3840 && BestEquipment.heat(cutter) == 130
                    && BestEquipment.wear(cutter) == 8 && cutter.getCount() == 1 && cutter.getDamageValue() == 0,
                    "One real ore break consumes exactly 256 energy, adds 120 heat and one engineering wear without vanilla damage");
        } finally { player.getInventory().clearContent(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalDestroyBlockRejectsUnlearnedAndUnreadyCuttersWithoutConsumption(GameTestHelper h) {
        FakePlayer player = player(h);
        try {
            ItemStack cutter = equipment("best_cutter", 4096, 10, 7);
            player.setItemInHand(InteractionHand.MAIN_HAND, cutter);
            h.setBlock(ORE, Blocks.IRON_ORE);
            ItemStack before = cutter.copy();
            h.assertTrue(!player.gameMode.destroyBlock(h.absolutePos(ORE)) && h.getBlockState(ORE).is(Blocks.IRON_ORE),
                    "The real server mining path rejects a charged cutter held by an unlearned player");
            h.assertTrue(ItemStack.matches(before, cutter), "Denied unlearned mining neither spends energy nor adds wear or heat");
            learn(h, player, 2);
            for (ItemStack blocked : List.of(equipment("best_cutter", 255, 0, 0),
                    equipment("best_cutter", 4096, BestEquipment.MAX_HEAT, 0),
                    equipment("best_cutter", 4096, 0, BestEquipment.MAX_WEAR))) {
                player.setItemInHand(InteractionHand.MAIN_HAND, blocked);
                before = blocked.copy();
                h.assertTrue(!player.gameMode.destroyBlock(h.absolutePos(ORE)) && h.getBlockState(ORE).is(Blocks.IRON_ORE),
                        "Actual mining rejects insufficient energy, maximum heat or worn-out equipment");
                h.assertTrue(ItemStack.matches(before, blocked), "Rejected unready mining cannot consume the held cutter state");
            }
        } finally { player.getInventory().clearContent(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void thermalLockPersistsUntilCoolingReachesExactlyFourHundred(GameTestHelper h) {
        ItemStack cutter = equipment("best_cutter", 4096, 900, 0);
        BestEquipment.spend(cutter, 256, 120);
        h.assertTrue(BestEquipment.heat(cutter) == 1000 && !BestEquipment.ready(cutter, 256)
                && CatalogueEquipment.getData(cutter).getBoolean("BestOverheated"), "Hitting the thermal limit latches an overheat lock");
        for (int i = 0; i < 299; i++) BestEquipment.cool(cutter);
        h.assertTrue(BestEquipment.heat(cutter) == 402 && !BestEquipment.ready(cutter, 256),
                "Cooling below the maximum alone cannot clear the lock before 400");
        BestEquipment.cool(cutter);
        h.assertTrue(BestEquipment.heat(cutter) == 400 && BestEquipment.ready(cutter, 256)
                && !CatalogueEquipment.getData(cutter).getBoolean("BestOverheated"), "Cooling to exactly 400 restores readiness");
        h.assertTrue(BestEquipment.charge(cutter) == 3840 && BestEquipment.wear(cutter) == 0,
                "Cooling changes neither stored charge nor engineering wear");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void batterySimulationAndRepeatedRechargeShareOneFiveHundredTwelveBudget(GameTestHelper h) {
        FakePlayer player = player(h);
        ItemStack target = equipment("best_frame", 200, 0, 0), first = battery(350), second = battery(900);
        player.setItemInHand(InteractionHand.MAIN_HAND, target);
        player.getInventory().setItem(1, first);
        player.getInventory().setItem(2, second);
        var receiver = target.getCapability(Capabilities.EnergyStorage.ITEM);
        var source = first.getCapability(Capabilities.EnergyStorage.ITEM);
        h.assertTrue(receiver != null && source != null, "The actual registered equipment and battery item energy capabilities are exposed");
        List<ItemStack> before = snapshot(player);
        h.assertTrue(receiver.receiveEnergy(5000, true) == 512 && receiver.receiveEnergy(5000, true) == 512
                && source.extractEnergy(5000, true) == 350 && source.extractEnergy(5000, true) == 350,
                "Repeated simulation quotes the same transfer without consuming the per-tick allowance");
        unchanged(h, player, before, "Simulation changes neither source nor receiver components");
        h.assertTrue(BestEquipment.recharge(player, target) == 512 && BestEquipment.charge(target) == 712
                && CatalogueEquipment.energy(first).getEnergyStored() == 0 && CatalogueEquipment.energy(second).getEnergyStored() == 738,
                "Two battery stacks jointly supply exactly one receiver budget with no conversion loss");
        h.assertTrue(BestEquipment.charge(target) + CatalogueEquipment.energy(first).getEnergyStored()
                + CatalogueEquipment.energy(second).getEnergyStored() == 1450, "The first recharge conserves total stored energy");
        before = snapshot(player);
        h.assertTrue(BestEquipment.recharge(player, target) == 0 && receiver.receiveEnergy(1, true) == 0
                && BestEquipment.energy(target).receiveEnergy(1, false) == 0,
                "Repeated recharge and fresh capability objects share the exhausted same-tick budget");
        unchanged(h, player, before, "An exhausted receiver budget never drains another source");
        long firstTick = h.getLevel().getServer().overworld().getGameTime();
        h.runAfterDelay(2, () -> {
            try {
                h.assertTrue(h.getLevel().getServer().overworld().getGameTime() > firstTick,
                        "The renewed budget is tested on a real later server tick");
                h.assertTrue(BestEquipment.recharge(player, target) == 512 && BestEquipment.charge(target) == 1224
                        && CatalogueEquipment.energy(second).getEnergyStored() == 226,
                        "A later server tick renews exactly one 512 energy allowance");
                h.assertTrue(BestEquipment.charge(target) + CatalogueEquipment.energy(first).getEnergyStored()
                        + CatalogueEquipment.energy(second).getEnergyStored() == 1450,
                        "Renewing the allowance still conserves source plus receiver energy");
                h.assertTrue(BestEquipment.recharge(player, target) == 0,
                        "The renewed tick also refuses a second transfer after its allowance is exhausted");
            } finally { player.getInventory().clearContent(); }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void nearCapacityRechargeExtractsOnlyTheAcceptedEnergy(GameTestHelper h) {
        FakePlayer player = player(h);
        try {
            ItemStack target = equipment("best_cutter", BestEquipment.CAPACITY - 37, 0, 0), source = battery(900);
            player.setItemInHand(InteractionHand.MAIN_HAND, target);
            player.getInventory().setItem(1, source);
            h.assertTrue(BestEquipment.recharge(player, target) == 37 && BestEquipment.charge(target) == BestEquipment.CAPACITY
                    && CatalogueEquipment.energy(source).getEnergyStored() == 863,
                    "A nearly full cutter extracts only the 37 energy it can actually accept");
            List<ItemStack> before = snapshot(player);
            h.assertTrue(BestEquipment.recharge(player, target) == 0, "A full cutter accepts no additional charge");
            unchanged(h, player, before, "A full receiver cannot waste any battery energy");
        } finally { player.getInventory().clearContent(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void frameTickSpendsExactlyAndRemovesItsTemporarySpeedWhenConditionsEnd(GameTestHelper h) {
        FakePlayer player = player(h);
        var assist = StarfallBlueprint.id("best_walking_assist");
        try {
            learn(h, player, 1);
            ItemStack frame = equipment("best_frame", 1024, 0, 0);
            player.setItemSlot(EquipmentSlot.LEGS, frame);
            player.setOnGround(true);
            player.setSprinting(true);
            var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            h.assertTrue(speed != null, "The survival player has a movement speed attribute");
            double sprintSpeed = speed.getValue();
            BestEngineering.tickPlayer(player);
            h.assertTrue(speed.hasModifier(assist) && Math.abs(speed.getValue() - sprintSpeed * 1.15) < 0.000001
                    && BestEquipment.charge(frame) == 1008 && BestEquipment.heat(frame) == 6,
                    "A grounded sprint gets exactly 15 percent assistance at 16 energy and six heat per tick");
            double assistedSpeed = speed.getValue();
            BestEngineering.tickPlayer(player);
            h.assertTrue(Math.abs(speed.getValue() - assistedSpeed) < 0.000001 && BestEquipment.charge(frame) == 992,
                    "Repeated ticks charge once each and never stack duplicate movement bonuses");
            player.setSprinting(false);
            BestEngineering.tickPlayer(player);
            h.assertTrue(!speed.hasModifier(assist) && BestEquipment.charge(frame) == 992,
                    "Stopping the sprint removes the temporary modifier without another energy charge");
            player.setSprinting(true);
            BestEngineering.tickPlayer(player);
            h.assertTrue(speed.hasModifier(assist), "Resuming an eligible sprint re-adds assistance");
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            BestEngineering.tickPlayer(player);
            h.assertTrue(!speed.hasModifier(assist), "Removing the frame clears its temporary modifier next tick");
            frame = equipment("best_frame", 16, 0, 0);
            player.setItemSlot(EquipmentSlot.LEGS, frame);
            BestEngineering.tickPlayer(player);
            h.assertTrue(speed.hasModifier(assist) && BestEquipment.charge(frame) == 0,
                    "The final complete energy quantum can assist for one tick");
            BestEngineering.tickPlayer(player);
            h.assertTrue(!speed.hasModifier(assist), "An empty frame cannot leave a stale speed modifier active");
        } finally {
            var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.removeModifier(assist);
            player.setSprinting(false);
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void deathCloneCopiesPersonalResearchWithoutGrantingItToOthers(GameTestHelper h) {
        FakePlayer original = player(h), replacement = player(h), unrelated = player(h);
        try {
            learn(h, original, 2);
            h.assertTrue(BestEngineering.researchLevel(replacement) == 0, "A fresh replacement profile starts without research");
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, original, true));
            h.assertTrue(BestEngineering.researchLevel(replacement) == 2 && BestEngineering.researchLevel(original) == 2,
                    "The registered death clone listener preserves the deceased player's full personal research");
            h.assertTrue(BestEngineering.researchLevel(unrelated) == 0,
                    "The death clone transfer does not unlock an unrelated player profile");
            h.assertTrue(replacement.getInventory().isEmpty(), "Research cloning creates no equipment or inventory reward");
        } finally {
            original.getInventory().clearContent();
            replacement.getInventory().clearContent();
            unrelated.getInventory().clearContent();
        }
        h.succeed();
    }
}
