package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.*;

/** Independent knowledge, recoverable hardware, paid work windows and actual vanilla collisions. */
@GameTestHolder("starfall_blueprint_mobility")
@PrefixGameTestTemplate(false)
public final class BestMobilityGameTests {
    private static final ResourceLocation STRIDE = StarfallBlueprint.id("best_stride_assist");
    private static final ResourceLocation WALK = StarfallBlueprint.id("best_walking_assist");

    private static FakePlayer player(GameTestHelper h, int level, boolean mobility) {
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "BestMobility"));
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        p.getPersistentData().putInt("BestEngineeringResearch", level);
        p.getPersistentData().putBoolean("BestLightMobility", mobility);
        p.setOnGround(true);
        p.setShiftKeyDown(false);
        p.setSprinting(false);
        p.getAbilities().flying = false;
        return p;
    }
    private static void input(FakePlayer p, int slot, String key, int count) {
        p.getInventory().setItem(slot, CatalogueContent.stack(key, count));
    }
    private static ItemStack frame(int energy, int heat, int wear, boolean installed, boolean enabled) {
        var s = CatalogueContent.stack("best_frame", 1);
        var d = CatalogueEquipment.getData(s);
        d.putInt("BestEnergy", energy); d.putInt("BestHeat", heat); d.putInt("BestWear", wear);
        d.putInt("BestWorkTicks", 37); d.putString("MobilitySentinel", "retain");
        d.putBoolean("BestStrideInstalled", installed); d.putBoolean("BestStrideEnabled", enabled);
        CatalogueEquipment.setData(s, d);
        return s;
    }
    private static boolean stride(FakePlayer p) { return p.getAttribute(Attributes.STEP_HEIGHT).hasModifier(STRIDE); }
    private static void clear(FakePlayer p) {
        p.getInventory().clearContent();
        p.setSprinting(false); p.setShiftKeyDown(false); p.getAbilities().flying = false;
        BestEngineering.tickPlayer(p);
    }
    private static void preserved(GameTestHelper h, ItemStack s) {
        var d = CatalogueEquipment.getData(s);
        h.assertTrue(BestEquipment.charge(s) == 12345 && BestEquipment.heat(s) == 321 && BestEquipment.wear(s) == 456
                && d.getInt("BestWorkTicks") == 37 && d.getString("MobilitySentinel").equals("retain"),
                "Module transactions preserve charge, heat, wear, work counter and unrelated data");
    }

    @GameTest(template = "empty")
    public static void mobilityLearningIsIndependentPersonalAndCloned(GameTestHelper h) {
        var p = player(h, 2, false); var other = player(h, 3, false);
        try {
            input(p, 1, "load", 4); input(p, 2, "conductor", 2); input(p, 3, "board", 2);
            h.assertTrue(!BestEngineering.perform(p, 8) && p.getInventory().getItem(1).getCount() == 4,
                    "Light mobility cannot skip field servicing or consume learning materials on failure");
            p.getPersistentData().putInt("BestEngineeringResearch", 3);
            h.assertTrue(BestEngineering.perform(p, 8) && BestEngineering.hasLightMobility(p)
                    && BestEngineering.researchLevel(p) == 3 && p.getInventory().getItem(1).getCount() == 2,
                    "Learning consumes exactly two load material and retains the original three-level research model");
            h.assertTrue(!BestEngineering.perform(p, 8) && p.getInventory().getItem(1).getCount() == 2,
                    "Duplicate learning never charges again");
            h.assertTrue(!BestEngineering.hasLightMobility(other), "Sharing a world does not share personal knowledge");
            BestEngineering.clonePlayer(new PlayerEvent.Clone(other, p, true));
            h.assertTrue(BestEngineering.hasLightMobility(other) && BestEngineering.researchLevel(other) == 3,
                    "Death clone preserves both independent mobility knowledge and existing research");
        } finally { clear(p); clear(other); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void moduleAssemblyInstallationAndRemovalAreAtomic(GameTestHelper h) {
        var p = player(h, 3, true);
        try {
            input(p, 1, "steel", 4); input(p, 2, "load", 2); input(p, 3, "steel_fix", 2); input(p, 4, "board", 1);
            h.assertTrue(BestEngineering.perform(p, 9), "A qualified operator can manually manufacture the module");
            h.assertTrue(p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item("best_stride_module"))).mapToInt(ItemStack::getCount).sum() == 1,
                    "Manual assembly creates exactly one module");
            // Move the actual result out of the selected slot before installing it on a real, used frame.
            var module = p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item("best_stride_module"))).findFirst().orElseThrow().copy();
            p.getInventory().clearContent(); input(p, 1, "best_stride_module", module.getCount());
            p.setItemInHand(InteractionHand.MAIN_HAND, frame(12345, 321, 456, false, false));
            h.assertTrue(BestEngineering.perform(p, 10), "Installation consumes a real inventory module");
            preserved(h, p.getMainHandItem());
            h.assertTrue(CatalogueEquipment.getData(p.getMainHandItem()).getBoolean("BestStrideInstalled")
                    && !CatalogueEquipment.getData(p.getMainHandItem()).getBoolean("BestStrideEnabled")
                    && p.getInventory().getItem(1).isEmpty(), "New installations start in the original sprint mode");
            input(p, 1, "best_stride_module", 1);
            h.assertTrue(!BestEngineering.perform(p, 10) && p.getInventory().getItem(1).getCount() == 1,
                    "Repeated installation does not swallow another module");
            h.assertTrue(BestEngineering.perform(p, 11) && CatalogueEquipment.getData(p.getMainHandItem()).getBoolean("BestStrideEnabled"),
                    "An installed frame can switch modes through the workbench transaction");
            preserved(h, p.getMainHandItem());
            for (int i = 1; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            var before = p.getMainHandItem().copy();
            h.assertTrue(!BestEngineering.perform(p, 12) && ItemStack.matches(before, p.getMainHandItem()),
                    "A full inventory cannot lose a module or mutate the frame during removal");
            p.getInventory().setItem(35, ItemStack.EMPTY);
            h.assertTrue(BestEngineering.perform(p, 12) && p.getInventory().getItem(35).is(CatalogueContent.item("best_stride_module")),
                    "Removing into a freed slot returns one physical module");
            preserved(h, p.getMainHandItem());
            h.assertTrue(!CatalogueEquipment.getData(p.getMainHandItem()).getBoolean("BestStrideInstalled")
                    && !CatalogueEquipment.getData(p.getMainHandItem()).getBoolean("BestStrideEnabled"), "Removal clears both mode fields");
            h.assertTrue(!BestEngineering.perform(p, 11) && !BestEngineering.perform(p, 12), "An unmodified frame cannot toggle or yield a second module");
        } finally { clear(p); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void strideChargesStandbyAndStopsAtEveryResourceBoundary(GameTestHelper h) {
        var p = player(h, 3, true);
        var unrelated = StarfallBlueprint.id("mobility_test_external_step");
        try {
            var s = frame(5000, 0, 0, true, true);
            p.setItemSlot(EquipmentSlot.LEGS, s);
            var step = p.getAttribute(Attributes.STEP_HEIGHT);
            double base = step.getValue();
            BestEngineering.tickPlayer(p);
            h.assertTrue(stride(p) && Math.abs(step.getValue() - base - 0.4) < 0.00001
                    && BestEquipment.charge(s) == 4976 && BestEquipment.heat(s) == 4,
                    "Ground standby is a paid work tick granting exactly 0.4 step height");
            for (int i = 1; i < 63; i++) BestEngineering.tickPlayer(p);
            h.assertTrue(BestEquipment.wear(s) == 1 && CatalogueEquipment.getData(s).getInt("BestWorkTicks") == 0,
                    "The preserved 37 work ticks reach one wear after 63 paid ticks");
            p.setSprinting(true); int energy = BestEquipment.charge(s);
            BestEngineering.tickPlayer(p);
            h.assertTrue(!stride(p) && !p.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(WALK) && BestEquipment.charge(s) == energy,
                    "Sprint never stacks either stride or the original powered sprint bonus while stride mode is selected");
            p.setSprinting(false); p.setShiftKeyDown(true); BestEngineering.tickPlayer(p);
            h.assertTrue(!stride(p) && BestEquipment.charge(s) == energy, "Sneaking suspends stride without charging");
            p.setShiftKeyDown(false); p.getAbilities().flying = true; BestEngineering.tickPlayer(p);
            h.assertTrue(!stride(p) && BestEquipment.charge(s) == energy, "Flight suspends stride");
            p.getAbilities().flying = false; p.setOnGround(false); BestEngineering.tickPlayer(p);
            h.assertTrue(!stride(p) && BestEquipment.charge(s) == energy, "Airborne players receive no powered step");
            p.setOnGround(true); p.getPersistentData().putBoolean("BestLightMobility", false); BestEngineering.tickPlayer(p);
            h.assertTrue(!stride(p) && BestEquipment.charge(s) == energy, "Borrowing a modified frame cannot bypass personal knowledge");
            p.getPersistentData().putBoolean("BestLightMobility", true);
            for (var boundary : new ItemStack[]{frame(23, 0, 0, true, true), frame(1000, 1000, 0, true, true), frame(1000, 0, 1200, true, true)}) {
                p.setItemSlot(EquipmentSlot.LEGS, boundary); int before = BestEquipment.charge(boundary);
                BestEngineering.tickPlayer(p);
                h.assertTrue(!stride(p) && BestEquipment.charge(boundary) == before, "Low power, maximum heat and worn-out frames cannot operate");
            }
            s = frame(24, 0, 0, true, true); p.setItemSlot(EquipmentSlot.LEGS, s); BestEngineering.tickPlayer(p);
            h.assertTrue(BestEquipment.charge(s) == 0 && !stride(p), "The final paid tick cannot leave an unpowered step modifier");
            s = frame(1000, 998, 0, true, true); p.setItemSlot(EquipmentSlot.LEGS, s); BestEngineering.tickPlayer(p);
            h.assertTrue(BestEquipment.heat(s) == 1000 && CatalogueEquipment.getData(s).getBoolean("BestOverheated") && !stride(p),
                    "Reaching the overheat threshold stops the modifier immediately");
            p.setItemSlot(EquipmentSlot.LEGS, frame(1000, 0, 0, true, true)); BestEngineering.tickPlayer(p);
            step.addTransientModifier(new AttributeModifier(unrelated, 0.1, AttributeModifier.Operation.ADD_VALUE));
            p.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY); BestEngineering.tickPlayer(p);
            h.assertTrue(!stride(p) && step.hasModifier(unrelated), "Unequipping clears only this mod's stride modifier");
            p.setItemSlot(EquipmentSlot.LEGS, frame(1000, 0, 0, false, false)); p.setSprinting(true); BestEngineering.tickPlayer(p);
            h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(WALK) && !stride(p), "An original frame retains the existing sprint behavior");
        } finally { p.getAttribute(Attributes.STEP_HEIGHT).removeModifier(unrelated); clear(p); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void existingServicingPreservesInstalledHardwareAndMode(GameTestHelper h) {
        var p = player(h, 3, true);
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND, frame(12345, 321, 456, true, true));
            input(p, 1, "steel", 2);
            p.getInventory().setItem(2, new ItemStack(Items.WATER_BUCKET));
            h.assertTrue(BestEngineering.perform(p, 4) && BestEngineering.perform(p, 7), "Existing workbench repair and cooling accept a modified frame");
            var serviced = p.getMainHandItem();
            h.assertTrue(BestEquipment.wear(serviced) == 256 && BestEquipment.heat(serviced) == 0
                    && BestEquipment.strideEnabled(serviced) && CatalogueEquipment.getData(serviced).getInt("BestWorkTicks") == 37,
                    "Workbench maintenance preserves the installed, enabled module and accumulated work ticks");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack("best_service_kit", 1));
            p.setItemInHand(InteractionHand.OFF_HAND, serviced);
            p.getMainHandItem().getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
            serviced = p.getOffhandItem();
            h.assertTrue(BestEquipment.wear(serviced) == 56 && BestEquipment.charge(serviced) == 12345
                    && BestEquipment.heat(serviced) == 0 && BestEquipment.strideEnabled(serviced)
                    && CatalogueEquipment.getData(serviced).getInt("BestWorkTicks") == 37
                    && CatalogueEquipment.getData(serviced).getString("MobilitySentinel").equals("retain"),
                    "Portable repair also retains hardware, mode, energy and unrelated custom data");
        } finally { clear(p); }
        h.succeed();
    }

    private static void startAtStep(GameTestHelper h, FakePlayer p) {
        var pos = h.absolutePos(new net.minecraft.core.BlockPos(1, 1, 2));
        p.setPos(pos.getX() + 0.4, pos.getY(), pos.getZ() + 0.5);
        p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO);
    }
    private static void moveIntoStep(FakePlayer p) { p.move(MoverType.SELF, new Vec3(0.9, -0.08, 0)); }

    @GameTest(template = "empty")
    public static void actualCollisionClimbsOneBlockButRespectsFenceAndLowCeiling(GameTestHelper h) {
        var p = player(h, 3, true);
        try {
            for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) h.setBlock(x, 0, z, Blocks.STONE);
            h.setBlock(2, 1, 2, Blocks.STONE);
            startAtStep(h, p); double floor = p.getY();
            p.setItemSlot(EquipmentSlot.LEGS, frame(10000, 0, 0, false, false)); BestEngineering.tickPlayer(p);
            moveIntoStep(p);
            h.assertTrue(p.getY() < floor + 0.1 && p.horizontalCollision, "The ordinary 0.6-height player collides with a solid one-block step");
            startAtStep(h, p); p.setItemSlot(EquipmentSlot.LEGS, frame(10000, 0, 0, true, true)); BestEngineering.tickPlayer(p);
            moveIntoStep(p);
            h.assertTrue(p.getY() > floor + 0.99, "Actual vanilla movement climbs the solid one-block step with paid stride assist");
            h.setBlock(2, 1, 2, Blocks.OAK_FENCE);
            startAtStep(h, p); BestEngineering.tickPlayer(p); moveIntoStep(p);
            h.assertTrue(p.getY() < floor + 0.1 && p.horizontalCollision, "A 1.5-high fence still blocks the stride frame");
            h.setBlock(2, 1, 2, Blocks.STONE); h.setBlock(2, 3, 2, Blocks.STONE);
            startAtStep(h, p); BestEngineering.tickPlayer(p); moveIntoStep(p);
            h.assertTrue(p.getY() < floor + 0.1 && p.horizontalCollision, "A low ceiling prevents stepping into an obstructed body volume");
        } finally { clear(p); }
        h.succeed();
    }
}
