package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

/** Three real-server tests cover the physical kit, persistence and vanilla riding/collision. */
@GameTestHolder("starfall_blueprint_mech_legs")
@PrefixGameTestTemplate(false)
public final class BestMechLegGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var profile = new GameProfile(UUID.randomUUID(), "MechLegTest");
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent(); p.getInventory().selected = 0;
        p.getPersistentData().putInt("BestEngineeringResearch", 3);
        for(String key : List.of("BestDiagnostics", "BestLightMobility", "BestMechOperation")) p.getPersistentData().putBoolean(key, true);
        return p;
    }
    private static BestMechEntity mech(GameTestHelper h) {
        for(int x = 0; x < 12; x++) for(int z = 0; z < 12; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        var m = BestMechs.TYPE.get().create(h.getLevel()); var pos = h.absolutePos(new BlockPos(5, 1, 3));
        m.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0); m.setOnGround(true); h.getLevel().addFreshEntity(m); return m;
    }
    private static void clear(ServerPlayer p) {
        p.stopRiding(); p.stopUsingItem(); p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); p.setShiftKeyDown(false);
    }
    private static void service(ServerPlayer p, BestMechEntity m) {
        p.stopRiding(); p.moveTo(m.getX() + 3, m.getY(), m.getZ(), 0, 0); p.setShiftKeyDown(true);
        m.setIsolated(true); m.setOnGround(true); m.setDeltaMovement(Vec3.ZERO);
    }
    private static List<ItemStack> inventory(ServerPlayer p) { return p.getInventory().items.stream().map(ItemStack::copy).toList(); }
    private static boolean unchanged(ServerPlayer p, List<ItemStack> before) {
        for(int i = 0; i < 36; i++) if(!ItemStack.matches(before.get(i), p.getInventory().getItem(i))) return false;
        return true;
    }
    private static CompoundTag withoutLegs(BestMechEntity m) { var t = m.stateTag(); t.remove(BestMechLegs.STATE); return t; }
    private static void removalRefused(GameTestHelper h, ServerPlayer p, BestMechEntity m, String why) {
        var before = m.stateTag(); var inv = inventory(p); var marker = p.getOffhandItem().copy();
        h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && before.equals(m.stateTag())
                && unchanged(p, inv) && ItemStack.matches(marker, p.getOffhandItem()), why);
    }
    private static void set(BestMechEntity m, String key, int value) { var t = m.stateTag(); t.putInt(key, value); m.loadState(t); }

    @GameTest(template = "empty")
    public static void assemblyAndLegServiceAreAtomicAndPreserveEveryOtherState(GameTestHelper h) {
        var p = player(h); var m = mech(h);
        try {
            var costs = Map.of("steel", 12, "load", 6, "conductor", 4, "steel_fix", 8, "sheath", 4);
            int slot = 1; for(var cost : costs.entrySet()) p.getInventory().setItem(slot++, CatalogueContent.stack(cost.getKey(), cost.getValue() * 2));
            var inv = inventory(p); h.assertTrue(BestEngineering.preview(p, 25).executable() && unchanged(p, inv), "Wheel preview is read-only");
            for(String prerequisite : List.of("BestMechOperation", "BestDiagnostics", "BestLightMobility")) {
                p.getPersistentData().putBoolean(prerequisite, false);
                h.assertTrue(!BestEngineering.perform(p, 25) && unchanged(p, inv), "Personal knowledge required: " + prerequisite);
                p.getPersistentData().putBoolean(prerequisite, true);
            }
            p.getPersistentData().putInt("BestEngineeringResearch", 2);
            h.assertTrue(!BestEngineering.perform(p, 25) && unchanged(p, inv), "Research three required"); p.getPersistentData().putInt("BestEngineeringResearch", 3);
            var missing = p.getInventory().getItem(1).copy(); p.getInventory().setItem(1, ItemStack.EMPTY); inv = inventory(p);
            h.assertTrue(!BestEngineering.perform(p, 25) && unchanged(p, inv), "Missing inputs spend nothing"); p.getInventory().setItem(1, missing);
            for(int i = 0; i < 36; i++) if(p.getInventory().getItem(i).isEmpty()) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            inv = inventory(p); h.assertTrue(!BestEngineering.perform(p, 25) && unchanged(p, inv), "Full output inventory spends nothing");
            p.getInventory().setItem(0, ItemStack.EMPTY); h.assertTrue(BestEngineering.perform(p, 25), "Wheel assembly commits");
            var kit = p.getMainHandItem(); h.assertTrue(kit.is(CatalogueContent.item(BestMechLegs.CRUISE)) && kit.getCount() == 1
                    && kit.getMaxStackSize() == 1 && !kit.isDamageableItem() && kit.getCapability(Capabilities.EnergyStorage.ITEM) == null, "One ordinary physical kit");
            for(var cost : costs.entrySet()) h.assertTrue(p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item(cost.getKey())))
                    .mapToInt(ItemStack::getCount).sum() == cost.getValue(), "Exact assembly cost: " + cost.getKey());
            clear(p); service(p, m); var t = m.stateTag(); t.putString(BestMechBodies.STATE, BestMechBodies.REINFORCED);
            t.putString(BestMechArms.STATE, BestMechArms.EXCAVATOR); t.putString(BestMechModules.STATE, BestMechModules.DRIVE);
            t.putString(BestMechCores.STATE, BestMechCores.GENERATOR); t.putInt("CoreFuelTicks", 131); t.putInt("CoreWorkTicks", 731);
            t.putInt("Energy", 123456); t.putInt("Heat", 678); t.putInt("Wear", 321); t.putInt("WorkTicks", 87);
            t.putInt("DrillCooldown", 13); t.putFloat("Health", 42.25f); t.putBoolean("ThermalLock", true);
            t.putLong("ChargeTick", h.getLevel().getGameTime()); t.putInt("ChargeUsed", 123); m.loadState(t);
            m.cargo().setItem(17, new ItemStack(Items.DIAMOND, 7)); m.setCustomName(Component.literal("Wheel service"));
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechLegs.CRUISE, 1)); var before = m.stateTag(); inv = inventory(p);
            p.setShiftKeyDown(false); h.assertTrue(!m.installLegs(p, InteractionHand.MAIN_HAND), "Crouch required"); p.setShiftKeyDown(true);
            h.assertTrue(!m.installLegs(p, InteractionHand.OFF_HAND), "Main hand required");
            m.setHealth(0); h.assertTrue(!m.installLegs(p, InteractionHand.MAIN_HAND), "Live chassis required"); m.setHealth(42.25f);
            m.setOnGround(false); h.assertTrue(!m.installLegs(p, InteractionHand.MAIN_HAND), "Ground required"); m.setOnGround(true);
            m.setDeltaMovement(.2, 0, 0); h.assertTrue(!m.installLegs(p, InteractionHand.MAIN_HAND), "Stationary required"); m.setDeltaMovement(Vec3.ZERO);
            m.setIsolated(false); h.assertTrue(!m.installLegs(p, InteractionHand.MAIN_HAND), "Isolation required"); m.setIsolated(true);
            p.getPersistentData().putBoolean("BestMechOperation", false); h.assertTrue(!m.installLegs(p, InteractionHand.MAIN_HAND), "Knowledge required"); p.getPersistentData().putBoolean("BestMechOperation", true);
            h.assertTrue(p.startRiding(m, true) && !m.installLegs(p, InteractionHand.MAIN_HAND), "Occupied chassis refuses install"); p.stopRiding(); m.setDeltaMovement(Vec3.ZERO); m.setOnGround(true);
            h.assertTrue(before.equals(m.stateTag()) && unchanged(p, inv), "Rejected installs retain every state"); before = withoutLegs(m);
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && m.legsKey().equals(BestMechLegs.CRUISE)
                    && p.getMainHandItem().isEmpty() && withoutLegs(m).equals(before), "Install consumes one kit without healing or altering independent state");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechLegs.CRUISE, 1)); inv = inventory(p);
            h.assertTrue(!m.installLegs(p, InteractionHand.MAIN_HAND) && unchanged(p, inv) && withoutLegs(m).equals(before), "Occupied legs refuse replacement");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack("best_diagnostic_tool", 1)); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.MINECART)); var marker = p.getOffhandItem().copy();
            m.setIsolated(false); removalRefused(h, p, m, "Connected removal consumes without isolation toggle"); m.setIsolated(true);
            p.getPersistentData().putBoolean("BestDiagnostics", false); removalRefused(h, p, m, "Unqualified removal consumes without toggle"); p.getPersistentData().putBoolean("BestDiagnostics", true);
            m.setOnGround(false); removalRefused(h, p, m, "Airborne removal refuses"); m.setOnGround(true);
            m.setDeltaMovement(.2, 0, 0); removalRefused(h, p, m, "Moving removal refuses"); m.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(p.startRiding(m, true), "Operator occupies chassis for removal guard"); removalRefused(h, p, m, "Occupied removal refuses"); p.stopRiding(); m.setDeltaMovement(Vec3.ZERO); m.setOnGround(true);
            m.setHealth(0); h.assertTrue(!m.removeLegs(p), "Dead chassis refuses removal"); m.setHealth(42.25f);
            for(int i = 1; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            removalRefused(h, p, m, "All 36 slots occupied refuses kit return"); p.getInventory().setItem(35, ItemStack.EMPTY); before = withoutLegs(m);
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && m.legsKey().isEmpty() && withoutLegs(m).equals(before)
                    && ItemStack.matches(marker, p.getOffhandItem()) && p.getInventory().getItem(35).is(CatalogueContent.item(BestMechLegs.CRUISE)), "Removal returns one kit preserving marker and every other state");
            removalRefused(h, p, m, "Original walking legs produce no free items");
        } finally { clear(p); m.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legsSurviveSavePackDeployDeathAndPreserveUnrelatedStepModifiers(GameTestHelper h) {
        var p = player(h); var m = mech(h); boolean drops = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(true, h.getLevel().getServer());
        try {
            var step = m.getAttribute(Attributes.STEP_HEIGHT); var other = StarfallBlueprint.id("legs_test_other_step");
            step.addTransientModifier(new AttributeModifier(other, .15, AttributeModifier.Operation.ADD_VALUE));
            var t = m.stateTag(); t.putString(BestMechLegs.STATE, BestMechLegs.CRUISE); m.loadState(t); m.loadState(t);
            h.assertTrue(Math.abs(step.getValue() - .75) < 1e-6 && step.hasModifier(other), "Repeated load applies only one wheel modifier and preserves unrelated modifier");
            t.putString(BestMechLegs.STATE, "unknown"); m.loadState(t);
            h.assertTrue(m.legsKey().isEmpty() && Math.abs(step.getValue() - 1.15) < 1e-6 && step.hasModifier(other), "Unknown legs restore walking without deleting other modifiers");
            t.remove(BestMechLegs.STATE); m.loadState(t); h.assertTrue(m.legsKey().isEmpty(), "Old state keeps original walking legs");
            service(p, m); p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechLegs.CRUISE, 1));
            h.assertTrue(m.installLegs(p, InteractionHand.MAIN_HAND) && Math.abs(step.getValue() - .75) < 1e-6, "Install applies dedicated step modifier");
            h.assertTrue(m.removeLegs(p) && Math.abs(step.getValue() - 1.15) < 1e-6 && step.hasModifier(other), "Removal preserves unrelated modifier");
            step.removeModifier(other); clear(p); service(p, m); t = m.stateTag(); t.putString(BestMechLegs.STATE, BestMechLegs.CRUISE);
            t.putString(BestMechBodies.STATE, BestMechBodies.REINFORCED); t.putString(BestMechCores.STATE, BestMechCores.GENERATOR);
            t.putString(BestMechArms.STATE, BestMechArms.EXCAVATOR); t.putString(BestMechModules.STATE, BestMechModules.RADIATOR);
            t.putInt("Energy", 123456); t.putInt("Heat", 678); t.putInt("Wear", 321); t.putInt("WorkTicks", 87);
            t.putInt("CoreFuelTicks", 131); t.putInt("CoreWorkTicks", 731); t.putInt("DrillCooldown", 13); t.putFloat("Health", 42.25f);
            t.putLong("ChargeTick", h.getLevel().getGameTime()); t.putInt("ChargeUsed", 123); m.loadState(t);
            m.setCustomName(Component.literal("Persistent wheels")); m.cargo().setItem(17, new ItemStack(Items.DIAMOND, 7)); var before = m.stateTag();
            var saved = new CompoundTag(); m.saveWithoutId(saved); var loaded = BestMechs.TYPE.get().create(h.getLevel()); loaded.load(saved);
            h.assertTrue(loaded.stateTag().equals(before) && Math.abs(loaded.getAttributeValue(Attributes.STEP_HEIGHT) - .6) < 1e-6, "Real entity save restores full wheel state and transient modifier"); loaded.discard();
            h.assertTrue(m.pack(p) && BestMechItem.state(p.getMainHandItem()).equals(before), "Actual packing preserves exact state");
            p.setShiftKeyDown(false); var pos = h.absolutePos(new BlockPos(6, 0, 6));
            h.assertTrue(p.getMainHandItem().getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false))).consumesAction() && p.getMainHandItem().isEmpty(), "Actual packed item deploys once");
            var area = new AABB(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)), Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(12, 10, 12))));
            m = h.getLevel().getEntitiesOfClass(BestMechEntity.class, area, e -> !e.isRemoved()).stream().findFirst().orElseThrow();
            h.assertTrue(m.stateTag().equals(before) && Math.abs(m.getAttributeValue(Attributes.STEP_HEIGHT) - .6) < 1e-6, "Deploy restores wheels and every independent state");
            var deathBox = m.getBoundingBox().inflate(3); m.setHealth(0); m.die(h.getLevel().damageSources().generic()); m.die(h.getLevel().damageSources().generic());
            var loot = h.getLevel().getEntitiesOfClass(ItemEntity.class, deathBox); var packed = loot.stream().filter(e -> e.getItem().is(CatalogueContent.item("best_mech"))).toList();
            h.assertTrue(packed.size() == 1 && loot.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum() == 7
                    && loot.stream().noneMatch(e -> e.getItem().is(CatalogueContent.item(BestMechLegs.CRUISE))), "Death drops cargo and one packed chassis without duplicate wheel kit");
            var dead = BestMechItem.state(packed.getFirst().getItem()); before.putFloat("Health", 0); before.put("Cargo", new net.minecraft.nbt.ListTag());
            h.assertTrue(dead.equals(before) && BestMechLegs.read(dead).equals(BestMechLegs.CRUISE), "Dead packed chassis preserves wheels and all other counters"); loot.forEach(Entity::discard);
        } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(drops, h.getLevel().getServer()); clear(p); if(!m.isRemoved()) m.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realRidingUsesWheelEconomyBoundariesAndVanillaStepCollision(GameTestHelper h) {
        var p = player(h); var m = mech(h);
        try {
            var start = m.position(); double baseline = 0;
            for(String body : List.of("", BestMechBodies.REINFORCED)) for(String module : List.of("", BestMechModules.DRIVE)) for(String legs : List.of("", BestMechLegs.CRUISE)) {
                p.stopRiding(); var t = m.stateTag(); t.putString(BestMechBodies.STATE, body); t.putString(BestMechModules.STATE, module); t.putString(BestMechLegs.STATE, legs);
                t.putInt("Energy", 100000); t.putInt("Heat", 0); t.putInt("Wear", 0); t.putInt("WorkTicks", 99); t.putBoolean("Isolated", false); t.putFloat("Health", 42.25f); m.loadState(t);
                m.moveTo(start.x, start.y, start.z, 0, 0); m.setDeltaMovement(Vec3.ZERO); m.setOnGround(true); p.setShiftKeyDown(false);
                h.assertTrue(p.startRiding(m), "Actual server operator mounts each configuration"); p.setPlayerInput(0, 1, false, false); m.tick(); m.positionRider(p);
                int cost = body.isEmpty() ? module.isEmpty() ? 400 : 600 : module.isEmpty() ? 600 : 900;
                int heat = body.isEmpty() ? module.isEmpty() ? 4 : 6 : module.isEmpty() ? 6 : 8;
                double speed = body.isEmpty() ? module.isEmpty() ? .22 : .28 : module.isEmpty() ? .18 : .23;
                if(!legs.isEmpty()) { cost = cost * 3 / 4; heat--; speed *= 1.25; }
                double distance = m.getZ() - start.z; if(baseline == 0) baseline = distance;
                h.assertTrue(distance > 0 && Math.abs(distance / baseline - speed / .22) < .001 && Math.abs(m.movementSpeed() - speed) < 1e-6
                        && m.energy() == 100000 - cost && m.heat() == heat && m.wear() == 1 && m.stateTag().getInt("WorkTicks") == 0
                        && m.getHealth() == 42.25f && m.coolingRate() == BestMechModules.coolingRate(module), "Real flat riding uses exact speed/cost/heat with unchanged wear, cooling and damaged health");
                set(m, "Energy", cost - 1); var stopped = m.position(); m.setOnGround(true); m.tick();
                h.assertTrue(m.energy() == cost - 1 && Math.abs(m.getZ() - stopped.z) < 1e-6, "Below dynamic cost cannot move or spend");
                set(m, "Energy", cost); m.setOnGround(true); m.tick();
                h.assertTrue(m.energy() == 0 && m.getZ() > stopped.z && m.getDeltaMovement().horizontalDistanceSqr() == 0, "Exact last affordable movement spends once then stops");
            }
            // A 599-XH reinforced drive wheel configuration cannot afford its actual 675-XH tick.
            set(m, "Energy", 599); m.setOnGround(true); var stopped = m.position(); m.tick();
            h.assertTrue(m.energy() == 599 && Math.abs(m.getZ() - stopped.z) < 1e-6, "599 cannot bypass 675 wheel cost");
            p.stopRiding();
            for(int x = 2; x <= 8; x++) for(int y = 1; y <= 5; y++) h.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
            var t = m.stateTag(); t.putInt("Energy", 100000); t.putInt("Heat", 0); m.loadState(t);
            var wall = h.absolutePos(new BlockPos(5, 1, 6)); m.moveTo(wall.getX() + .5, wall.getY(), wall.getZ() - m.getBbWidth() / 2.0, 0, 0);
            m.setDeltaMovement(Vec3.ZERO); m.setOnGround(true); h.assertTrue(p.startRiding(m), "Operator mounts at real wall"); p.setPlayerInput(0, 1, false, false); stopped = m.position(); m.tick();
            h.assertTrue(m.energy() == 100000 && m.heat() == 0 && Math.abs(m.getZ() - stopped.z) < 1e-6, "Vanilla wall collision charges no movement energy or heat"); p.stopRiding();
            for(int x = 2; x <= 8; x++) for(int y = 2; y <= 5; y++) h.setBlock(new BlockPos(x, y, 6), Blocks.AIR);
            for(String legs : List.of("", BestMechLegs.CRUISE)) {
                t = m.stateTag(); t.putString(BestMechLegs.STATE, legs); m.loadState(t);
                m.moveTo(start.x, start.y, start.z, 0, 0); m.setDeltaMovement(Vec3.ZERO); m.setOnGround(true);
                m.move(MoverType.SELF, new Vec3(0, -.01, 4));
                h.assertTrue(legs.isEmpty() ? m.getY() > start.y + .99 && m.getZ() > wall.getZ()
                        : Math.abs(m.getY() - start.y) < 1e-6 && m.getZ() < wall.getZ(), "Standing vanilla collision: walking passes one block, wheels stay blocked");
            }
            for(int x = 2; x <= 8; x++) h.setBlock(new BlockPos(x, 1, 6), Blocks.STONE_SLAB);
            m.moveTo(start.x, start.y, start.z, 0, 0); m.setDeltaMovement(Vec3.ZERO); m.setOnGround(true); m.move(MoverType.SELF, new Vec3(0, -.01, 4));
            h.assertTrue(Math.abs(m.getY() - start.y - .5) < 1e-6 && m.getZ() > wall.getZ(), "Actual wheel collision passes a half slab with 0.6 step height");
        } finally { clear(p); m.discard(); }
        h.succeed();
    }
}
