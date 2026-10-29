package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.*;

/** Targeted commissioning, read-only diagnostics and isolation at actual energy interfaces. */
@GameTestHolder("starfall_blueprint_diagnostics")
@PrefixGameTestTemplate(false)
public final class BestDiagnosticsGameTests {
    private static final ResourceLocation STRIDE = StarfallBlueprint.id("best_stride_assist");
    private static final ResourceLocation SPRINT = StarfallBlueprint.id("best_walking_assist");

    private static FakePlayer player(GameTestHelper h, int research, boolean diagnostics) {
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "BestDiagnostics"));
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent(); p.getInventory().selected = 0;
        p.getPersistentData().putInt("BestEngineeringResearch", research);
        p.getPersistentData().putBoolean("BestDiagnostics", diagnostics);
        p.getPersistentData().putBoolean("BestLightMobility", false);
        p.setOnGround(true); p.setSprinting(false); p.setShiftKeyDown(false);
        return p;
    }
    private static void input(FakePlayer p, int slot, String key, int count) {
        p.getInventory().setItem(slot, CatalogueContent.stack(key, count));
    }
    private static int count(FakePlayer p, String key) {
        return p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item(key))).mapToInt(ItemStack::getCount).sum();
    }
    private static ItemStack equipment(String key, boolean isolated, int heat, int wear) {
        var s = CatalogueContent.stack(key, 1);
        var d = CatalogueEquipment.getData(s);
        d.putInt("BestEnergy", 12345); d.putInt("BestHeat", heat); d.putInt("BestWear", wear);
        d.putInt("BestWorkTicks", 43); d.putBoolean("BestOverheated", heat >= 1000);
        d.putBoolean("BestBatteryIsolated", isolated);
        d.putBoolean("BestStrideInstalled", key.equals("best_frame"));
        d.putBoolean("BestStrideEnabled", key.equals("best_frame"));
        d.putString("DiagnosticSentinel", "preserve"); CatalogueEquipment.setData(s, d);
        return s;
    }
    private static void clear(FakePlayer p) {
        p.getInventory().clearContent(); p.setSprinting(false); p.setShiftKeyDown(false);
        BestEngineering.tickPlayer(p);
    }
    private static void unchangedExceptIsolation(GameTestHelper h, ItemStack before, ItemStack after) {
        var a = CatalogueEquipment.getData(before); var b = CatalogueEquipment.getData(after);
        a.remove("BestBatteryIsolated"); b.remove("BestBatteryIsolated");
        h.assertTrue(a.equals(b), "Isolation changes only the battery isolation flag, retaining every other custom field");
    }

    @GameTest(template = "empty")
    public static void diagnosticsLearningAndToolAssemblyArePersonalAndAtomic(GameTestHelper h) {
        var p = player(h, 2, false); var other = player(h, 3, false);
        try {
            input(p, 1, "load", 2); input(p, 2, "conductor", 4); input(p, 3, "board", 2);
            h.assertTrue(!BestEngineering.perform(p, 13) && count(p, "load") == 2, "Diagnostics cannot skip field servicing or consume its learning materials");
            p.getPersistentData().putInt("BestEngineeringResearch", 3);
            h.assertTrue(BestEngineering.perform(p, 13) && BestEngineering.hasDiagnostics(p)
                    && !BestEngineering.hasLightMobility(p) && BestEngineering.researchLevel(p) == 3,
                    "Diagnostics branches independently after level three without requiring light mobility or a fourth level");
            h.assertTrue(count(p, "load") == 1 && count(p, "conductor") == 2 && count(p, "board") == 1,
                    "Learning consumes exactly one load material, two conductor and one board");
            h.assertTrue(!BestEngineering.perform(p, 13) && count(p, "conductor") == 2,
                    "Repeated learning never charges again");
            h.assertTrue(!BestEngineering.hasDiagnostics(other), "Knowledge is personal even with shared tools");
            BestEngineering.clonePlayer(new PlayerEvent.Clone(other, p, true));
            h.assertTrue(BestEngineering.hasDiagnostics(other) && BestEngineering.researchLevel(other) == 3
                    && !BestEngineering.hasLightMobility(other), "Death clone retains independent diagnostics knowledge");
            for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            input(p, 1, "steel", 3); input(p, 2, "conductor", 2); input(p, 3, "sensor", 2);
            input(p, 4, "board", 2); input(p, 5, "sheath", 2);
            h.assertTrue(!BestEngineering.perform(p, 14) && count(p, "steel") == 3 && count(p, "sensor") == 2,
                    "A full inventory rejects tool assembly without consuming inputs");
            p.getInventory().setItem(6, ItemStack.EMPTY);
            h.assertTrue(BestEngineering.perform(p, 14) && count(p, "best_diagnostic_tool") == 1
                    && count(p, "steel") == 1 && count(p, "sensor") == 1 && count(p, "conductor") == 1
                    && count(p, "board") == 1 && count(p, "sheath") == 1, "Manual assembly commits the exact budget and one real tool");
            var tool = p.getInventory().getItem(6);
            h.assertTrue(tool.getMaxStackSize() == 1 && !tool.isDamageableItem()
                    && tool.getCapability(Capabilities.EnergyStorage.ITEM) == null,
                    "The reusable diagnostic tool is neither a battery nor a damageable powered item");
        } finally { clear(p); clear(other); }
        h.succeed();
    }

    private static Object[] reportArguments(net.minecraft.network.chat.Component component, String key) {
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && text.getKey().equals("message.starfall_blueprint." + key)) return text.getArgs();
        for (var child : component.getSiblings()) {
            var args = reportArguments(child, key); if (args != null) return args;
        }
        return null;
    }

    @GameTest(template = "empty")
    public static void handheldDiagnosticsAreReadOnlyAndRespectPersonalKnowledge(GameTestHelper h) {
        var p = player(h, 3, false);
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack("best_diagnostic_tool", 1));
            p.setItemInHand(InteractionHand.OFF_HAND, equipment("best_frame", true, 1000, 1200));
            var tool = p.getMainHandItem().copy(); var target = p.getOffhandItem().copy();
            var result = p.getMainHandItem().getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
            h.assertTrue(!result.getResult().consumesAction() && ItemStack.matches(tool, p.getMainHandItem())
                    && ItemStack.matches(target, p.getOffhandItem()), "Borrowing a diagnostic tool cannot bypass personal knowledge or alter equipment");
            p.getPersistentData().putBoolean("BestDiagnostics", true);
            var report = BestDiagnosticTool.report(p, p.getOffhandItem());
            var values = reportArguments(report, "best_diagnostic_report");
            var frameValues = reportArguments(report, "best_diagnostic_frame");
            var yes = net.minecraft.network.chat.Component.translatable("message.starfall_blueprint.best_state_yes");
            var no = net.minecraft.network.chat.Component.translatable("message.starfall_blueprint.best_state_no");
            h.assertTrue(values != null && values.length == 11 && values[1].equals(12345) && values[3].equals(1000)
                    && values[6].equals(yes) && values[7].equals(0) && values[9].equals(yes) && values[10].equals(yes)
                    && frameValues != null && frameValues.length == 3 && frameValues[0].equals(yes)
                    && frameValues[1].equals(yes) && frameValues[2].equals(no),
                    "A single report exposes simultaneous heat lock, exhausted wear and isolation alongside independent frame and operator state");
            result = p.getMainHandItem().getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
            h.assertTrue(result.getResult().consumesAction() && ItemStack.matches(tool, p.getMainHandItem())
                    && ItemStack.matches(target, p.getOffhandItem()), "Diagnosing simultaneous faults never consumes the tool, repairs, charges or changes any custom data");
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_PICKAXE)); target = p.getOffhandItem().copy();
            result = p.getMainHandItem().getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
            h.assertTrue(!result.getResult().consumesAction() && ItemStack.matches(tool, p.getMainHandItem())
                    && ItemStack.matches(target, p.getOffhandItem()), "Invalid diagnostic targets are rejected without mutation");
            p.setItemInHand(InteractionHand.OFF_HAND, CatalogueContent.stack("best_cutter", 1));
            target = p.getOffhandItem().copy();
            result = p.getMainHandItem().getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
            h.assertTrue(result.getResult().consumesAction() && ItemStack.matches(target, p.getOffhandItem()),
                    "Diagnosing a fresh device never creates custom data or initializes energy");
        } finally { clear(p); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void isolationAndReconnectionPreserveDeviceAndRequireKnowledge(GameTestHelper h) {
        var p = player(h, 3, false);
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND, equipment("best_frame", false, 765, 321));
            var original = p.getMainHandItem().copy();
            h.assertTrue(!BestEngineering.perform(p, 15) && ItemStack.matches(original, p.getMainHandItem()),
                    "A shared device does not allow an unqualified operator to isolate it");
            p.getPersistentData().putBoolean("BestDiagnostics", true);
            h.assertTrue(BestEngineering.perform(p, 15) && BestEquipment.isIsolated(p.getMainHandItem()),
                    "A trained operator can isolate the main-hand frame");
            unchangedExceptIsolation(h, original, p.getMainHandItem());
            var isolated = p.getMainHandItem().copy();
            h.assertTrue(!BestEngineering.perform(p, 15) && ItemStack.matches(isolated, p.getMainHandItem()),
                    "Repeated isolation is rejected without mutating the device");
            h.assertTrue(BestEngineering.perform(p, 16) && !BestEquipment.isIsolated(p.getMainHandItem()),
                    "Reconnection clears isolation without replacing energy or repairing equipment");
            unchangedExceptIsolation(h, original, p.getMainHandItem());
            var connected = p.getMainHandItem().copy();
            h.assertTrue(!BestEngineering.perform(p, 16) && ItemStack.matches(connected, p.getMainHandItem()),
                    "Repeated reconnection is rejected without changes");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            h.assertTrue(!BestEngineering.perform(p, 15), "Ordinary equipment cannot acquire a Best isolation flag");
            p.setItemInHand(InteractionHand.MAIN_HAND, equipment("best_cutter", false, 0, 0));
            h.assertTrue(BestEngineering.perform(p, 15) && BestEquipment.isIsolated(p.getMainHandItem())
                    && BestEngineering.perform(p, 16), "The same isolation transaction supports the cutter");
        } finally { clear(p); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void isolationBlocksRealCapabilitiesAndBatteryTransfers(GameTestHelper h) {
        var p = player(h, 3, true);
        try {
            var target = equipment("best_cutter", false, 0, 0);
            var receiver = target.getCapability(Capabilities.EnergyStorage.ITEM);
            h.assertTrue(receiver != null && receiver.canReceive(), "Connected devices expose their existing charging capability");
            var d = CatalogueEquipment.getData(target); d.putBoolean("BestBatteryIsolated", true); CatalogueEquipment.setData(target, d);
            var before = target.copy();
            h.assertTrue(!receiver.canReceive() && receiver.receiveEnergy(512, true) == 0 && receiver.receiveEnergy(512, false) == 0
                    && ItemStack.matches(before, target), "Even a capability acquired before isolation rejects simulation and real writes without data changes");
            var battery = CatalogueContent.stack("s01", 1); var b = CatalogueEquipment.getData(battery);
            b.putInt("Energy", 8000); CatalogueEquipment.setData(battery, b); p.getInventory().setItem(1, battery);
            var batteryBefore = battery.copy();
            h.assertTrue(BestEquipment.recharge(p, target) == 0 && ItemStack.matches(batteryBefore, battery)
                    && ItemStack.matches(before, target), "Handheld charging never drains the source or mutates an isolated destination");
            d = CatalogueEquipment.getData(target); d.putBoolean("BestBatteryIsolated", false); CatalogueEquipment.setData(target, d);
            h.assertTrue(receiver.canReceive() && BestEquipment.recharge(p, target) == 512 && BestEquipment.charge(target) == 12857
                    && CatalogueEquipment.getData(battery).getInt("Energy") == 7488,
                    "Reconnection restores the existing charging path and its exact 512 XH transfer budget");
        } finally { clear(p); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void isolatedDevicesStopWorkButRetainServicing(GameTestHelper h) {
        var p = player(h, 3, true);
        try {
            p.getPersistentData().putBoolean("BestLightMobility", true);
            var frame = equipment("best_frame", false, 0, 0); p.setItemSlot(EquipmentSlot.LEGS, frame);
            BestEngineering.tickPlayer(p);
            h.assertTrue(p.getAttribute(Attributes.STEP_HEIGHT).hasModifier(STRIDE), "A connected trained frame can enter stride mode");
            var d = CatalogueEquipment.getData(frame); d.putBoolean("BestBatteryIsolated", true); CatalogueEquipment.setData(frame, d);
            var before = frame.copy(); BestEngineering.tickPlayer(p);
            h.assertTrue(!p.getAttribute(Attributes.STEP_HEIGHT).hasModifier(STRIDE) && ItemStack.matches(before, frame),
                    "Isolation immediately removes stride assist without charging or accumulating work ticks");
            d = CatalogueEquipment.getData(frame); d.putBoolean("BestStrideEnabled", false); CatalogueEquipment.setData(frame, d);
            p.setSprinting(true); BestEngineering.tickPlayer(p);
            h.assertTrue(!p.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SPRINT), "Isolation also blocks original sprint assist");
            p.setSprinting(false); p.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            var cutter = equipment("best_cutter", true, 800, 500); p.setItemInHand(InteractionHand.MAIN_HAND, cutter);
            var pos = h.absolutePos(new BlockPos(1, 1, 1));
            h.assertTrue(!BestEquipment.ready(cutter, 0)
                    && cutter.getItem().getDestroySpeed(cutter, Blocks.STONE.defaultBlockState()) == 0
                    && !cutter.getItem().canAttackBlock(Blocks.STONE.defaultBlockState(), h.getLevel(), pos, p)
                    && !cutter.getItem().mineBlock(cutter, h.getLevel(), Blocks.AIR.defaultBlockState(), pos, p),
                    "An isolated cutter cannot operate even against zero-hardness blocks or with a zero energy request");
            input(p, 1, "steel", 2); p.getInventory().setItem(2, new ItemStack(Items.WATER_BUCKET));
            h.assertTrue(BestEngineering.perform(p, 4) && BestEngineering.perform(p, 7), "Isolated equipment remains repairable and coolable at the workbench");
            cutter = p.getMainHandItem();
            h.assertTrue(BestEquipment.isIsolated(cutter) && BestEquipment.charge(cutter) == 12345
                    && BestEquipment.heat(cutter) == 400 && BestEquipment.wear(cutter) == 300,
                    "Workbench servicing retains isolation and the original energy while applying only repair and cooling");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack("best_service_kit", 1));
            p.setItemInHand(InteractionHand.OFF_HAND, cutter); p.getMainHandItem().getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
            cutter = p.getOffhandItem();
            h.assertTrue(BestEquipment.isIsolated(cutter) && BestEquipment.wear(cutter) == 100 && BestEquipment.heat(cutter) == 400
                    && BestEquipment.charge(cutter) == 12345 && CatalogueEquipment.getData(cutter).getInt("BestWorkTicks") == 43
                    && CatalogueEquipment.getData(cutter).getString("DiagnosticSentinel").equals("preserve"),
                    "Portable servicing retains isolation, energy, heat and unrelated custom data");
            cutter.getItem().inventoryTick(cutter, h.getLevel(), p, 40, false);
            h.assertTrue(BestEquipment.heat(cutter) == 398 && BestEquipment.isIsolated(cutter),
                    "Isolation never stops ordinary passive cooling");
        } finally { clear(p); }
        h.succeed();
    }
}
