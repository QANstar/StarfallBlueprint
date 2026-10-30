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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

/** Three real-server tests exercise transactions, actual entity ticks and state conversion. */
@GameTestHolder("starfall_blueprint_mech_core")
@PrefixGameTestTemplate(false)
public final class BestMechCoreGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var profile = new GameProfile(UUID.randomUUID(), "MechCoreTest");
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
        var m = BestMechs.TYPE.get().create(h.getLevel());
        var pos = h.absolutePos(new BlockPos(5, 1, 3)); m.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        m.setOnGround(true); h.getLevel().addFreshEntity(m); return m;
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
    private static void set(BestMechEntity m, String key, int value) { var t = m.stateTag(); t.putInt(key, value); m.loadState(t); }
    private static CompoundTag withoutCore(BestMechEntity m) { var t = m.stateTag(); t.remove(BestMechCores.STATE); return t; }
    private static void removalRefused(GameTestHelper h, ServerPlayer p, BestMechEntity m, String why) {
        var before = m.stateTag(); var inv = inventory(p);
        h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && before.equals(m.stateTag()) && unchanged(p, inv), why);
    }
    private static void running(BestMechEntity m) {
        var t = m.stateTag(); t.putString(BestMechCores.STATE, BestMechCores.GENERATOR); t.putBoolean("Isolated", false);
        t.putInt("Energy", 0); t.putInt("Heat", 0); t.putInt("Wear", 0); t.putInt("CoreFuelTicks", 0);
        t.putInt("CoreWorkTicks", 0); t.putBoolean("ThermalLock", false); t.putFloat("Health", 100); m.loadState(t);
    }

    @GameTest(template = "empty")
    public static void assemblyAndCoreServiceCommitAtomically(GameTestHelper h) {
        var p = player(h); var m = mech(h);
        try {
            var costs = Map.of("steel", 16, "load", 8, "conductor", 8, "board", 4, "sheath", 8, "steel_fix", 8);
            int slot = 1; for(var cost : costs.entrySet()) p.getInventory().setItem(slot++, CatalogueContent.stack(cost.getKey(), cost.getValue() * 2));
            var inv = inventory(p); h.assertTrue(BestEngineering.preview(p, 24).executable() && unchanged(p, inv), "Core preview is read-only");
            for(String prerequisite : List.of("BestMechOperation", "BestDiagnostics", "BestLightMobility")) {
                p.getPersistentData().putBoolean(prerequisite, false);
                h.assertTrue(!BestEngineering.perform(p, 24) && unchanged(p, inv), "Assembly requires personal knowledge: " + prerequisite);
                p.getPersistentData().putBoolean(prerequisite, true);
            }
            p.getPersistentData().putInt("BestEngineeringResearch", 2);
            h.assertTrue(!BestEngineering.perform(p, 24) && unchanged(p, inv), "Research three required"); p.getPersistentData().putInt("BestEngineeringResearch", 3);
            var missing = p.getInventory().getItem(1).copy(); p.getInventory().setItem(1, ItemStack.EMPTY); inv = inventory(p);
            h.assertTrue(!BestEngineering.perform(p, 24) && unchanged(p, inv), "Missing inputs are atomic"); p.getInventory().setItem(1, missing);
            for(int i = 0; i < 36; i++) if(p.getInventory().getItem(i).isEmpty()) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            inv = inventory(p); h.assertTrue(!BestEngineering.perform(p, 24) && unchanged(p, inv), "Full output inventory spends nothing");
            p.getInventory().setItem(0, ItemStack.EMPTY); h.assertTrue(BestEngineering.perform(p, 24), "Core assembly commits");
            var kit = p.getMainHandItem(); h.assertTrue(kit.is(CatalogueContent.item(BestMechCores.GENERATOR)) && kit.getCount() == 1
                    && kit.getMaxStackSize() == 1 && !kit.isDamageableItem() && kit.getCapability(Capabilities.EnergyStorage.ITEM) == null, "Core is one ordinary physical kit");
            for(var cost : costs.entrySet()) h.assertTrue(p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item(cost.getKey())))
                    .mapToInt(ItemStack::getCount).sum() == cost.getValue(), "Exact cost: " + cost.getKey());
            clear(p); service(p, m); var t = m.stateTag(); t.putString(BestMechBodies.STATE, BestMechBodies.REINFORCED);
            t.putString(BestMechArms.STATE, BestMechArms.EXCAVATOR); t.putString(BestMechModules.STATE, BestMechModules.DRIVE);
            t.putInt("Energy", 123456); t.putInt("Heat", 678); t.putInt("Wear", 321); t.putInt("WorkTicks", 87);
            t.putInt("CoreWorkTicks", 999); t.putInt("DrillCooldown", 13); t.putFloat("Health", 42.25f);
            t.putLong("ChargeTick", h.getLevel().getGameTime()); t.putInt("ChargeUsed", 123); m.loadState(t);
            m.cargo().setItem(17, new ItemStack(Items.DIAMOND, 7)); m.setCustomName(Component.literal("Core service"));
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechCores.GENERATOR, 1)); var before = m.stateTag(); inv = inventory(p);
            p.setShiftKeyDown(false); h.assertTrue(!m.installCore(p, InteractionHand.MAIN_HAND), "Crouch required"); p.setShiftKeyDown(true);
            h.assertTrue(!m.installCore(p, InteractionHand.OFF_HAND), "Main hand required");
            m.setOnGround(false); h.assertTrue(!m.installCore(p, InteractionHand.MAIN_HAND), "Ground required"); m.setOnGround(true);
            m.setDeltaMovement(.2, 0, 0); h.assertTrue(!m.installCore(p, InteractionHand.MAIN_HAND), "Stationary required"); m.setDeltaMovement(Vec3.ZERO);
            m.setIsolated(false); h.assertTrue(!m.installCore(p, InteractionHand.MAIN_HAND), "Isolation required"); m.setIsolated(true);
            p.getPersistentData().putBoolean("BestMechOperation", false); h.assertTrue(!m.installCore(p, InteractionHand.MAIN_HAND), "Knowledge required"); p.getPersistentData().putBoolean("BestMechOperation", true);
            h.assertTrue(before.equals(m.stateTag()) && unchanged(p, inv), "Rejected installs retain every state"); before = withoutCore(m);
            h.assertTrue(m.installCore(p, InteractionHand.MAIN_HAND) && p.getMainHandItem().isEmpty() && withoutCore(m).equals(before), "Install consumes one kit and preserves other state");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechCores.GENERATOR, 1)); inv = inventory(p);
            h.assertTrue(!m.installCore(p, InteractionHand.MAIN_HAND) && unchanged(p, inv) && withoutCore(m).equals(before), "Occupied core refuses replacement");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack("best_diagnostic_tool", 1)); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.FURNACE)); var marker = p.getOffhandItem().copy();
            set(m, "CoreFuelTicks", 1); removalRefused(h, p, m, "Paid residual fuel refuses explicit removal without toggle"); set(m, "CoreFuelTicks", 0);
            m.setIsolated(false); removalRefused(h, p, m, "Connected explicit removal refuses without toggling"); m.setIsolated(true);
            for(int i = 1; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            removalRefused(h, p, m, "Full inventory refuses without state changes"); p.getInventory().setItem(35, ItemStack.EMPTY); before = withoutCore(m);
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && m.coreKey().isEmpty() && withoutCore(m).equals(before)
                    && ItemStack.matches(marker, p.getOffhandItem()) && p.getInventory().getItem(35).is(CatalogueContent.item(BestMechCores.GENERATOR)), "Removal returns one kit and preserves counters, cargo and marker");
            removalRefused(h, p, m, "Empty slot cannot generate another core");
        } finally { clear(p); m.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realTicksConsumeOnlyActiveSolidFuelAndPreserveChargingBudget(GameTestHelper h) {
        var m = mech(h);
        try {
            running(m); m.cargo().setItem(0, new ItemStack(Items.LAVA_BUCKET)); m.cargo().setItem(1, new ItemStack(Items.STONE));
            m.cargo().setItem(9, new ItemStack(Items.COAL, 2)); m.tick();
            h.assertTrue(m.energy() == 0 && m.coreFuelTicks() == 0 && m.cargo().getItem(0).is(Items.LAVA_BUCKET) && m.cargo().getItem(9).getCount() == 2, "Reject remainder and nonfuel; inactive standard cargo never burns");
            var t = m.stateTag(); t.putString(BestMechBodies.STATE, BestMechBodies.REINFORCED); m.loadState(t); m.tick();
            int coalTicks = new ItemStack(Items.COAL).getBurnTime(RecipeType.SMELTING);
            h.assertTrue(m.energy() == 1000 && m.heat() == 2 && m.coreFuelTicks() == coalTicks - 1 && m.cargo().getItem(9).getCount() == 1
                    && m.stateTag().getInt("CoreWorkTicks") == 1 && m.getPassengers().isEmpty(), "Driverless first tick consumes exactly one active coal and generates 1000/2");
            m.tick(); h.assertTrue(m.energy() == 2000 && m.heat() == 2 && m.coreFuelTicks() == coalTicks - 2 && m.cargo().getItem(9).getCount() == 1, "Paid fuel decrements one tick after cooling, no second fuel consumed");
            set(m, "Energy", m.capacity() - 999); int fuel = m.coreFuelTicks(), work = m.stateTag().getInt("CoreWorkTicks"); m.tick();
            h.assertTrue(m.energy() == m.capacity() - 999 && m.coreFuelTicks() == fuel && m.stateTag().getInt("CoreWorkTicks") == work, "Partial capacity pauses paid fuel without waste");
            set(m, "CoreFuelTicks", 0); m.tick(); h.assertTrue(m.cargo().getItem(9).getCount() == 1 && m.coreFuelTicks() == 0, "Partial capacity cannot buy new fuel");
            set(m, "Energy", m.capacity() - 1000); m.tick(); h.assertTrue(m.energy() == m.capacity() && m.coreFuelTicks() == coalTicks - 1 && m.cargo().getItem(9).isEmpty(), "Exactly 1000 free accepts one complete operating tick");
            set(m, "Energy", 0); set(m, "ChargeUsed", 0); var port = m.energyPort();
            h.assertTrue(port.receiveEnergy(512, false) == 512, "External budget starts at 512"); fuel = m.coreFuelTicks(); m.tick();
            h.assertTrue(m.energy() == 1512 && m.coreFuelTicks() == fuel - 1 && port.receiveEnergy(512, false) == 0, "Internal generation neither uses nor resets same-tick external budget");
            m.setIsolated(true); fuel = m.coreFuelTicks(); m.tick(); h.assertTrue(m.energy() == 1512 && m.coreFuelTicks() == fuel, "Isolation pauses paid fuel"); m.setIsolated(false);
            set(m, "Wear", BestMechEntity.MAX_WEAR); m.tick(); h.assertTrue(m.energy() == 1512 && m.coreFuelTicks() == fuel, "Worn core pauses paid fuel");
            var electric = m.stateTag(); electric.putString(BestMechCores.STATE, "unknown"); electric.putInt("Wear", 0); m.loadState(electric); m.tick();
            h.assertTrue(m.coreKey().isEmpty() && m.energy() == 1512 && m.coreFuelTicks() == fuel, "Unknown core remains electric and cannot spend residual fuel");
            running(m); m.cargo().clearContent(); m.cargo().setItem(8, new ItemStack(Items.STICK, 2));
            int stickTicks = new ItemStack(Items.STICK).getBurnTime(RecipeType.SMELTING); for(int i = 0; i < stickTicks; i++) m.tick();
            h.assertTrue(m.energy() == stickTicks * 1000 && m.coreFuelTicks() == 0 && m.cargo().getItem(8).getCount() == 1, "A complete actual vanilla fuel duration produces exact total output");
        } finally { m.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void coreStateSurvivesSavePackDeployDeathAndThermalWearBoundaries(GameTestHelper h) {
        var p = player(h); var m = mech(h); boolean drops = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(true, h.getLevel().getServer());
        try {
            h.assertTrue(m.coreKey().isEmpty() && m.coreFuelTicks() == 0, "Legacy core is electric"); running(m);
            // Core runs before vanilla riding: its output is available to movement in this tick.
            var t = m.stateTag(); t.putInt("Heat", 999); t.putInt("Energy", 10000); t.putInt("CoreFuelTicks", 4); m.loadState(t);
            p.moveTo(m.getX() + 3, m.getY(), m.getZ(), 0, 0); h.assertTrue(p.startRiding(m), "Actual operator mounts for thermal boundary");
            p.setPlayerInput(0, 1, false, false); m.setOnGround(true); m.tick();
            h.assertTrue(m.energy() == 10600 && m.heat() == 1000 && m.thermalLocked() && m.coreFuelTicks() == 3
                    && m.getDeltaMovement().horizontalDistanceSqr() == 0, "Generation precedes actual movement and movement heat immediately latches and stops");
            m.tick(); h.assertTrue(m.energy() == 10600 && m.coreFuelTicks() == 3, "Latched following tick cannot generate"); p.stopRiding();
            t = m.stateTag(); t.putInt("Heat", 402); t.putInt("Energy", 0); t.putInt("CoreFuelTicks", 4);
            t.putInt("CoreWorkTicks", 999); t.putInt("WorkTicks", 87); t.putInt("Wear", 0); m.loadState(t); m.tick();
            h.assertTrue(!m.thermalLocked() && m.heat() == 402 && m.energy() == 1000 && m.coreFuelTicks() == 3 && m.wear() == 1
                    && m.stateTag().getInt("CoreWorkTicks") == 0 && m.stateTag().getInt("WorkTicks") == 87, "Cooling to 400 recovers, then generation adds two heat and separate 999 counter wears once");
            t = m.stateTag(); t.putInt("Heat", 403); t.putBoolean("ThermalLock", true); m.loadState(t); m.tick();
            h.assertTrue(m.thermalLocked() && m.heat() == 401 && m.energy() == 1000 && m.coreFuelTicks() == 3, "Latch above 400 pauses output and fuel");
            t = m.stateTag(); t.putInt("Heat", 999); t.putBoolean("ThermalLock", false); t.putInt("CoreWorkTicks", 999); t.putInt("Wear", 2399);
            t.putString(BestMechModules.STATE, ""); m.loadState(t); m.tick();
            h.assertTrue(m.energy() == 2000 && m.wear() == 2400 && m.coreFuelTicks() == 2 && m.getDeltaMovement().horizontalDistanceSqr() == 0, "Last wear tick operates once and stops horizontal motion");
            int energy = m.energy(); m.tick(); h.assertTrue(m.energy() == energy && m.coreFuelTicks() == 2, "Worn boundary blocks next tick");
            service(p, m); t = m.stateTag(); t.putString(BestMechBodies.STATE, BestMechBodies.REINFORCED); t.putString(BestMechArms.STATE, BestMechArms.EXCAVATOR);
            t.putString(BestMechModules.STATE, BestMechModules.RADIATOR); t.putInt("CoreWorkTicks", 731); t.putInt("DrillCooldown", 13);
            t.putFloat("Health", 42.25f); t.putLong("ChargeTick", h.getLevel().getGameTime()); t.putInt("ChargeUsed", 123); m.loadState(t);
            m.setCustomName(Component.literal("Persistent generator")); m.cargo().setItem(17, new ItemStack(Items.COAL, 7)); var before = m.stateTag();
            var saved = new CompoundTag(); m.saveWithoutId(saved); var loaded = BestMechs.TYPE.get().create(h.getLevel()); loaded.load(saved);
            h.assertTrue(loaded.stateTag().equals(before), "Registry-aware entity save preserves every exact counter and resource"); loaded.discard();
            h.assertTrue(m.pack(p) && BestMechItem.state(p.getMainHandItem()).equals(before), "Packed generator retains paid fuel and complete state");
            p.setShiftKeyDown(false); var pos = h.absolutePos(new BlockPos(6, 0, 6));
            h.assertTrue(p.getMainHandItem().getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false))).consumesAction() && p.getMainHandItem().isEmpty(), "Actual packed item deploys once");
            var area = new AABB(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)), Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(12, 10, 12))));
            m = h.getLevel().getEntitiesOfClass(BestMechEntity.class, area, e -> !e.isRemoved()).stream().findFirst().orElseThrow();
            h.assertTrue(m.stateTag().equals(before), "Deployment preserves exact state including unused fuel");
            var deathBox = m.getBoundingBox().inflate(3); m.setHealth(0); m.die(h.getLevel().damageSources().generic()); m.die(h.getLevel().damageSources().generic());
            var loot = h.getLevel().getEntitiesOfClass(ItemEntity.class, deathBox); var packed = loot.stream().filter(e -> e.getItem().is(CatalogueContent.item("best_mech"))).toList();
            h.assertTrue(packed.size() == 1 && loot.stream().filter(e -> e.getItem().is(Items.COAL)).mapToInt(e -> e.getItem().getCount()).sum() == 7, "Death drops cargo fuel independently and only one packed chassis");
            var dead = BestMechItem.state(packed.getFirst().getItem()); h.assertTrue(BestMechCores.read(dead).equals(BestMechCores.GENERATOR)
                    && dead.getInt("CoreFuelTicks") == 2 && dead.getInt("CoreWorkTicks") == 731 && dead.getFloat("Health") == 0 && dead.getList("Cargo", 10).isEmpty(), "Dead packed generator retains unusable paid fuel without duplicate cargo");
            var corpse = BestMechs.TYPE.get().create(h.getLevel()); corpse.loadState(dead); corpse.setIsolated(false); int stored = corpse.energy(); corpse.tick();
            h.assertTrue(corpse.energy() == stored && corpse.coreFuelTicks() == 2 && !BestMechItem.energy(packed.getFirst().getItem()).canReceive(), "Dead entity and packed item cannot use paid fuel or receive energy"); corpse.discard();
            var malformed = new CompoundTag(); malformed.putString(BestMechCores.STATE, "unknown"); malformed.putInt("CoreFuelTicks", Integer.MAX_VALUE); malformed.putInt("CoreWorkTicks", Integer.MAX_VALUE);
            loaded = BestMechs.TYPE.get().create(h.getLevel()); loaded.loadState(malformed);
            h.assertTrue(loaded.coreKey().isEmpty() && loaded.coreFuelTicks() == BestMechCores.MAX_FUEL_TICKS && loaded.stateTag().getInt("CoreWorkTicks") == 999, "Malformed counters clamp to safe persistent bounds"); loaded.discard(); loot.forEach(Entity::discard);
        } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(drops, h.getLevel().getServer()); clear(p); if(!m.isRemoved()) m.discard(); }
        h.succeed();
    }
}
