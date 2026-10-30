package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

/** Three focused real-server tests for the exclusive utility slot. */
@GameTestHolder("starfall_blueprint_mech_modules")
@PrefixGameTestTemplate(false)
public final class BestMechModuleGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        // FakePlayer.startRiding always returns false; retain the actual embedded-connection fixture.
        var profile = new GameProfile(UUID.randomUUID(), "MechModuleTest");
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, CommonListenerCookie.createInitial(profile, false));
        p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent(); p.getInventory().selected = 0;
        p.getPersistentData().putInt("BestEngineeringResearch", 3);
        for(String key : List.of("BestDiagnostics", "BestLightMobility", "BestMechOperation")) p.getPersistentData().putBoolean(key, true);
        p.setOnGround(true); var away = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 1, 1)));
        p.moveTo(away.x, away.y, away.z, 0, 0);
        return p;
    }
    private static void clear(ServerPlayer p) {
        p.stopRiding(); p.stopUsingItem(); p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); p.setShiftKeyDown(false); p.xxa = p.zza = 0;
    }
    private static void floor(GameTestHelper h) {
        for(int x = 0; x < 12; x++) for(int z = 0; z < 12; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }
    private static BestMechEntity mech(GameTestHelper h) {
        var m = BestMechs.TYPE.get().create(h.getLevel()); var t = new CompoundTag();
        t.putInt("Energy", 100_000); t.putFloat("Health", 100); m.loadState(t);
        var pos = h.absolutePos(new BlockPos(5, 1, 3)); m.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        m.setOnGround(true); h.getLevel().addFreshEntity(m); return m;
    }
    private static void tickRider(BestMechEntity m, ServerPlayer p, float forward) {
        p.setPlayerInput(0, forward, false, false); m.tick(); m.positionRider(p);
    }
    private static CompoundTag operationalState(BestMechEntity m) {
        var t = m.stateTag(); t.remove(BestMechModules.STATE); return t;
    }
    private static List<ItemStack> inventory(ServerPlayer p) { return p.getInventory().items.stream().map(ItemStack::copy).toList(); }
    private static boolean unchanged(ServerPlayer p, List<ItemStack> before) {
        for(int i = 0; i < 36; i++) if(!ItemStack.matches(before.get(i), p.getInventory().getItem(i))) return false;
        return true;
    }
    private static InteractionResult deploy(GameTestHelper h, ServerPlayer p) {
        var pos = h.absolutePos(new BlockPos(6, 0, 6));
        return p.getMainHandItem().getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false)));
    }

    @GameTest(template = "empty")
    public static void exclusiveSlotTransactionsPreserveUsedStateAndNeverFallThrough(GameTestHelper h) {
        floor(h); var p = player(h); var m = mech(h);
        try {
            for(int action : new int[]{19, 20}) {
                var costs = action == 19 ? Map.of("steel", 6, "conductor", 4, "sheath", 4, "steel_fix", 4)
                        : Map.of("steel", 8, "load", 4, "conductor", 4, "board", 2, "steel_fix", 4);
                p.getInventory().clearContent(); int slot = 1;
                for(var cost : costs.entrySet()) p.getInventory().setItem(slot++, CatalogueContent.stack(cost.getKey(), cost.getValue() * 2));
                var materials = inventory(p);
                h.assertTrue(BestEngineering.preview(p, action).executable() && unchanged(p, materials), "Module assembly preview is read-only");
                p.getPersistentData().putBoolean("BestMechOperation", false);
                h.assertTrue(!BestEngineering.perform(p, action) && unchanged(p, materials), "Assembly rechecks the existing personal driving prerequisite atomically");
                p.getPersistentData().putBoolean("BestMechOperation", true);
                p.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                for(int i = slot; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                materials = inventory(p);
                h.assertTrue(!BestEngineering.perform(p, action) && unchanged(p, materials), "No output space leaves all manufacturing inputs intact");
                p.getInventory().setItem(0, ItemStack.EMPTY);
                h.assertTrue(BestEngineering.perform(p, action) && p.getMainHandItem().is(CatalogueContent.item(action == 19 ? BestMechModules.RADIATOR : BestMechModules.DRIVE))
                        && p.getMainHandItem().getCount() == 1, "Manufacturing returns exactly one ordinary physical module");
                for(var cost : costs.entrySet()) h.assertTrue(p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item(cost.getKey())))
                        .mapToInt(ItemStack::getCount).sum() == cost.getValue(), "Manufacturing consumes exactly the agreed materials: " + cost.getKey());
            }
            p.getInventory().clearContent();
            h.assertTrue(p.startRiding(m), "Real server player mounts to create actual movement work");
            tickRider(m, p, 1); p.stopRiding(); p.setPlayerInput(0, 0, false, false);
            m.energyPort().receiveEnergy(123, false);
            var used = m.stateTag(); used.putInt("Wear", 321); used.putFloat("Health", 42.25f);
            used.putInt("Heat", 678); used.putInt("DrillCooldown", 13); m.loadState(used);
            m.setIsolated(true); m.setOnGround(true); m.setDeltaMovement(Vec3.ZERO); m.tick(); m.tick();
            h.assertTrue(m.stateTag().getInt("WorkTicks") > 0 && m.stateTag().getInt("ChargeUsed") == 123
                    && m.stateTag().getInt("DrillCooldown") == 11, "Fixture carries actual movement work, spent charging allowance and ticking used cooldown");
            p.setShiftKeyDown(true); p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechModules.RADIATOR, 1));
            var module = p.getMainHandItem();
            h.assertTrue(module.getMaxStackSize() == 1 && !module.isDamageableItem()
                    && module.getCapability(Capabilities.EnergyStorage.ITEM) == null, "Utility modules are ordinary nonstack items without durability or an energy port");
            var before = operationalState(m);
            p.getPersistentData().putBoolean("BestMechOperation", false); var inv = inventory(p);
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && unchanged(p, inv)
                    && operationalState(m).equals(before) && m.moduleKey().isEmpty() && !p.isPassenger(), "Unqualified module use is consumed without riding or mutation");
            p.getPersistentData().putBoolean("BestMechOperation", true);
            m.setOnGround(false); h.assertTrue(!m.installModule(p, InteractionHand.MAIN_HAND), "Airborne installation fails"); m.setOnGround(true);
            m.setDeltaMovement(.2, 0, 0); h.assertTrue(!m.installModule(p, InteractionHand.MAIN_HAND), "Moving installation fails"); m.setDeltaMovement(Vec3.ZERO);
            m.setIsolated(false); h.assertTrue(!m.installModule(p, InteractionHand.MAIN_HAND), "Connected installation fails"); m.setIsolated(true);
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && module.isEmpty()
                    && m.moduleKey().equals(BestMechModules.RADIATOR) && m.coolingRate() == 4
                    && m.movementCost() == 400 && m.movementHeat() == 4 && operationalState(m).equals(before), "One physical radiator changes only the shared slot");
            for(String key : List.of(BestMechModules.RADIATOR, BestMechModules.DRIVE)) {
                p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(key, 1)); inv = inventory(p);
                h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && unchanged(p, inv)
                        && m.moduleKey().equals(BestMechModules.RADIATOR) && operationalState(m).equals(before), "Duplicate and swap attempts never consume an item or stack effects");
            }
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack("best_diagnostic_tool", 1));
            p.setItemInHand(InteractionHand.OFF_HAND, CatalogueContent.stack("steel_fix", 1));
            for(int i = 1; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            inv = inventory(p); var tool = p.getOffhandItem().copy();
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && unchanged(p, inv)
                    && m.moduleKey().equals(BestMechModules.RADIATOR) && operationalState(m).equals(before)
                    && ItemStack.matches(tool, p.getOffhandItem()), "Full inventory removal is atomic and cannot fall through to isolation toggle");
            p.getInventory().setItem(35, ItemStack.EMPTY);
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && m.moduleKey().isEmpty()
                    && p.getInventory().getItem(35).is(CatalogueContent.item(BestMechModules.RADIATOR))
                    && operationalState(m).equals(before) && ItemStack.matches(tool, p.getOffhandItem()), "Removal returns exactly one ordinary module and consumes no removal tool or used state");
            inv = inventory(p); h.assertTrue(!m.removeModule(p) && unchanged(p, inv), "Repeated removal cannot duplicate a module");
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); p.setShiftKeyDown(false);
            m.interact(p, InteractionHand.MAIN_HAND); h.assertTrue(m.isolated(), "Normal diagnostic remains read-only");
            p.setShiftKeyDown(true); m.interact(p, InteractionHand.MAIN_HAND);
            h.assertTrue(!m.isolated(), "Plain crouch diagnostic retains the existing isolation toggle");
        } finally { clear(p); m.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bothModulesSurviveSavePackDeployAndDestroyedPackedItem(GameTestHelper h) {
        floor(h); var p = player(h); var m = mech(h);
        boolean drops = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(true, h.getLevel().getServer());
        try {
            var legacy = m.stateTag(); legacy.remove(BestMechModules.STATE); m.loadState(legacy);
            h.assertTrue(m.moduleKey().isEmpty() && m.movementCost() == 400 && m.coolingRate() == 2, "Old state defaults to an empty slot and base stats");
            legacy.putString(BestMechModules.STATE, "unknown_module"); m.loadState(legacy);
            h.assertTrue(m.moduleKey().isEmpty() && m.stateTag().getString(BestMechModules.STATE).isEmpty(), "Unknown persisted module is sanitized to empty");
            for(String key : List.of(BestMechModules.RADIATOR, BestMechModules.DRIVE)) {
                var t = m.stateTag(); t.putString(BestMechModules.STATE, key); t.putBoolean("Isolated", true);
                t.putInt("Heat", 678); t.putInt("Wear", 321); t.putInt("WorkTicks", 87); t.putInt("DrillCooldown", 13);
                t.putFloat("Health", 42.25f); t.putLong("ChargeTick", h.getLevel().getGameTime()); t.putInt("ChargeUsed", 123);
                m.loadState(t); m.setOnGround(true); m.setDeltaMovement(Vec3.ZERO); var before = m.stateTag();
                var saved = new CompoundTag(); m.saveWithoutId(saved); var loaded = BestMechs.TYPE.get().create(h.getLevel()); loaded.load(saved);
                h.assertTrue(loaded.stateTag().equals(before) && loaded.moduleKey().equals(key), "Entity NBT retains the module and every operational counter"); loaded.discard();
                p.getInventory().clearContent(); h.assertTrue(m.pack(p) && m.isRemoved(), "A used module-equipped chassis packs exactly once");
                h.assertTrue(BestMechItem.state(p.getMainHandItem()).equals(before), "Packed physical item retains exact module state");
                h.assertTrue(deploy(h, p).consumesAction() && p.getMainHandItem().isEmpty(), "Actual deployment consumes the physical packed item");
                var box = new AABB(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)), Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(12, 10, 12))));
                m = h.getLevel().getEntitiesOfClass(BestMechEntity.class, box, e -> !e.isRemoved()).stream().findFirst().orElseThrow();
                h.assertTrue(m.stateTag().equals(before) && m.moduleKey().equals(key)
                        && m.movementCost() == BestMechModules.movementCost(key) && m.coolingRate() == BestMechModules.coolingRate(key), "Deployed module effects and exact used state survive conversion");
            }
            var deathBox = m.getBoundingBox().inflate(3); m.setHealth(0);
            m.die(h.getLevel().damageSources().generic()); m.die(h.getLevel().damageSources().generic());
            var packed = h.getLevel().getEntitiesOfClass(ItemEntity.class, deathBox).stream().filter(e -> e.getItem().is(CatalogueContent.item("best_mech"))).toList();
            h.assertTrue(packed.size() == 1 && BestMechItem.state(packed.getFirst().getItem()).getString(BestMechModules.STATE).equals(BestMechModules.DRIVE)
                    && BestMechItem.state(packed.getFirst().getItem()).getFloat("Health") == 0, "Death drops one destroyed packed chassis retaining its utility module");
            packed.forEach(Entity::discard);
        } finally {
            h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(drops, h.getLevel().getServer());
            clear(p); if(!m.isRemoved()) m.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualDriveUsesSixHundredOnlyWhenMovingAndRadiatorRecoversAtFourHundred(GameTestHelper h) {
        floor(h); var p = player(h); var m = mech(h);
        try {
            h.assertTrue(p.startRiding(m), "Real embedded-connection server player controls vanilla riding");
            var start = m.position(); tickRider(m, p, 1); double baseDistance = m.getZ() - start.z;
            h.assertTrue(baseDistance > 0 && m.energy() == 99_600 && m.heat() == 4, "Base movement remains 400 XH and four heat");
            var t = m.stateTag(); t.putString(BestMechModules.STATE, BestMechModules.DRIVE); t.putInt("Energy", 100_000); t.putInt("Heat", 0); m.loadState(t);
            m.moveTo(start.x, start.y, start.z, 0, 0); m.setOnGround(true); m.setDeltaMovement(Vec3.ZERO); tickRider(m, p, 1);
            double driveDistance = m.getZ() - start.z;
            h.assertTrue(Math.abs(driveDistance / baseDistance - .28 / .22) < .001 && m.energy() == 99_400 && m.heat() == 6,
                    "Actual drive riding uses speed .28 and spends exactly 600 XH/six heat for displacement");
            tickRider(m, p, 0); h.assertTrue(m.energy() == 99_400 && m.heat() == 4, "Idle drive has no cruise cost and retains base two-per-tick cooling");
            t = m.stateTag(); t.putInt("Energy", 599); m.loadState(t); var stopped = m.position(); tickRider(m, p, 1);
            h.assertTrue(m.energy() == 599 && m.position().distanceToSqr(stopped) < .01, "Drive refuses a command below its actual 600 threshold");
            t = m.stateTag(); t.putInt("Energy", 600); m.loadState(t); tickRider(m, p, 1);
            h.assertTrue(m.energy() == 0 && m.getDeltaMovement().horizontalDistanceSqr() == 0, "Last drive step spends 600 and clears powered momentum");
            t = m.stateTag(); t.putInt("Energy", 100_000); m.loadState(t);
            for(int x = 3; x <= 7; x++) for(int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
            for(int i = 0; i < 20; i++) tickRider(m, p, 1);
            int energy = m.energy(), heat = m.heat(); tickRider(m, p, 1);
            h.assertTrue(energy < 100_000 && m.energy() == energy && m.heat() == Math.max(0, heat - 2)
                    && m.getBoundingBox().maxZ <= h.absolutePos(new BlockPos(0, 0, 5)).getZ() + 1E-5, "Wall-blocked drive has no movement cost or movement heat");
            p.stopRiding(); t = m.stateTag(); t.putString(BestMechModules.STATE, BestMechModules.RADIATOR);
            t.putInt("Heat", 405); t.putBoolean("ThermalLock", true); m.loadState(t);
            m.tick(); h.assertTrue(m.heat() == 401 && m.thermalLocked(), "Radiator cools four per tick while recovery remains locked above 400");
            m.tick(); h.assertTrue(m.heat() == 397 && !m.thermalLocked() && m.movementCost() == 400 && m.movementHeat() == 4,
                    "Radiator unlocks at the existing 400 boundary and retains base movement costs");
        } finally { clear(p); m.discard(); }
        h.succeed();
    }
}
