package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;

/** Three focused server checks: physical transactions, real break pipeline, and action boundaries. */
@GameTestHolder("starfall_blueprint_heavy_drill")
@PrefixGameTestTemplate(false)
public final class BestHeavyDrillGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var profile = new GameProfile(UUID.randomUUID(), "HeavyDrillTest");
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
    private static void clear(ServerPlayer p) {
        p.stopRiding(); p.getInventory().clearContent(); p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); p.setShiftKeyDown(false);
    }
    private static BestMechEntity mech(GameTestHelper h, boolean heavy) {
        for(int x = 0; x < 12; x++) for(int z = 0; z < 12; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        var m = (heavy ? BestMechs.HEAVY_TYPE : BestMechs.TYPE).get().create(h.getLevel());
        var pos = h.absolutePos(new BlockPos(3, 1, 3)); m.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        m.setOnGround(true); h.getLevel().addFreshEntity(m); reset(m, 1_000_000, 0, 0); return m;
    }
    private static void reset(BestMechEntity m, int energy, int heat, int wear) {
        var t = m.stateTag(); t.putString(BestMechArms.STATE, BestMechArms.HEAVY_DRILL); t.putInt("Energy", energy);
        t.putInt("Heat", heat); t.putInt("Wear", wear); t.putInt("DrillCooldown", 0); t.putBoolean("Isolated", false); t.putBoolean("ThermalLock", false);
        m.loadState(t); m.setOnGround(true); m.setDeltaMovement(Vec3.ZERO);
    }
    private static void service(ServerPlayer p, BestMechEntity m) {
        p.stopRiding(); p.setShiftKeyDown(true); m.setIsolated(true); m.setOnGround(true); m.setDeltaMovement(Vec3.ZERO);
    }
    private static List<ItemStack> inventory(ServerPlayer p) { return p.getInventory().items.stream().map(ItemStack::copy).toList(); }
    private static boolean unchanged(ServerPlayer p, List<ItemStack> before) {
        for(int i = 0; i < 36; i++) if(!ItemStack.matches(before.get(i), p.getInventory().getItem(i))) return false;
        return true;
    }
    private static CompoundTag independent(BestMechEntity m) { var t = m.stateTag(); t.remove(BestMechArms.STATE); return t; }
    private static List<BlockPos> plane(BlockPos center, Direction.Axis axis) {
        var result = new ArrayList<BlockPos>(); result.add(center);
        for(int row = -1; row <= 1; row++) for(int col = -1; col <= 1; col++) if(row != 0 || col != 0)
            result.add(switch(axis) { case X -> center.offset(0, col, row); case Y -> center.offset(col, 0, row); case Z -> center.offset(col, row, 0); });
        return result;
    }
    private static void aim(ServerPlayer p, BestMechEntity m, BlockPos target, Direction.Axis axis) {
        p.setShiftKeyDown(false); if(p.getVehicle() != m) p.startRiding(m);
        Vec3 eye = Vec3.atCenterOf(target).add(axis == Direction.Axis.X ? -2 : 0, axis == Direction.Axis.Y ? 2 : 0, axis == Direction.Axis.Z ? -2 : 0);
        p.moveTo(eye.x, eye.y - p.getEyeHeight(), eye.z, axis == Direction.Axis.X ? -90 : 0, axis == Direction.Axis.Y ? 90 : 0);
    }
    private static void fill(GameTestHelper h, List<BlockPos> positions) {
        positions.forEach(pos -> h.getLevel().setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState()));
    }

    @GameTest(template = "empty")
    public static void physicalManufactureServiceAndSavedFortyTickCooldown(GameTestHelper h) {
        var p = player(h); var m = mech(h, true); var light = BestMechs.TYPE.get().create(h.getLevel());
        boolean drops = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(true, h.getLevel().getServer());
        try {
            var costs = Map.of("steel", 24, "load", 12, "conductor", 8, "head", 8, "board", 4, "steel_fix", 12, "sheath", 8);
            int slot = 1;
            for(var cost : costs.entrySet()) {
                int remaining = cost.getValue() * 2, max = CatalogueContent.stack(cost.getKey(), 1).getMaxStackSize();
                while(remaining > 0) { int count = Math.min(max, remaining); p.getInventory().setItem(slot++, CatalogueContent.stack(cost.getKey(), count)); remaining -= count; }
            }
            var inv = inventory(p); var knowledge = p.getPersistentData().copy();
            h.assertTrue(BestEngineering.preview(p, 27).executable() && unchanged(p, inv) && knowledge.equals(p.getPersistentData()), "Action27 preview is read-only");
            for(String key : List.of("BestDiagnostics", "BestLightMobility", "BestMechOperation")) {
                p.getPersistentData().putBoolean(key, false); h.assertTrue(!BestEngineering.perform(p, 27) && unchanged(p, inv), "Requires existing knowledge " + key);
                p.getPersistentData().putBoolean(key, true);
            }
            p.getPersistentData().putInt("BestEngineeringResearch", 2); h.assertTrue(!BestEngineering.perform(p, 27) && unchanged(p, inv), "Requires research3");
            p.getPersistentData().putInt("BestEngineeringResearch", 3);
            var steel = p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item("steel"))).findFirst().orElseThrow();
            steel.setCount(23); inv = inventory(p); h.assertTrue(!BestEngineering.perform(p, 27) && unchanged(p, inv), "Missing material spends nothing"); steel.setCount(48);
            for(int i = 0; i < 36; i++) if(p.getInventory().getItem(i).isEmpty()) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            inv = inventory(p); h.assertTrue(BestEngineering.preview(p, 27).executable() && unchanged(p, inv), "Full inventory preview accounts for consumed head slots without mutation");
            h.assertTrue(BestEngineering.perform(p, 27), "Full inventory manufacture atomically reuses consumed nonstacking head slot");
            var arm = p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item(BestMechArms.HEAVY_DRILL))).findFirst().orElseThrow(); h.assertTrue(arm.is(CatalogueContent.item(BestMechArms.HEAVY_DRILL)) && arm.getCount() == 1 && arm.getMaxStackSize() == 1
                    && !arm.isDamageableItem() && arm.getCapability(Capabilities.EnergyStorage.ITEM) == null, "Ordinary single physical arm");
            for(var cost : costs.entrySet()) h.assertTrue(p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item(cost.getKey()))).mapToInt(ItemStack::getCount).sum() == cost.getValue(), "Exact cost " + cost.getKey());
            h.assertTrue(knowledge.equals(p.getPersistentData()), "Manufacture adds no knowledge");
            clear(p); service(p, m); var t = m.stateTag(); t.putString(BestMechArms.STATE, ""); t.putInt("DrillCooldown", 40);
            t.putInt("WorkTicks", 87); t.putInt("CoreWorkTicks", 731); t.putInt("ChargeUsed", 123); t.putLong("ChargeTick", h.getLevel().getGameTime());
            t.putString(BestMechModules.STATE, BestMechModules.RADIATOR); t.putString(BestMechLegs.STATE, BestMechLegs.CRUISE);
            t.putInt("Heat", 678); t.putInt("Wear", 321); t.putFloat("Health", 242.25f); m.loadState(t);
            var other = independent(m); p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechArms.HEAVY_DRILL, 1));
            p.setShiftKeyDown(false); h.assertTrue(!m.installArm(p, InteractionHand.MAIN_HAND), "Requires crouch"); p.setShiftKeyDown(true);
            h.assertTrue(!m.installArm(p, InteractionHand.OFF_HAND) && m.installArm(p, InteractionHand.MAIN_HAND) && independent(m).equals(other), "Main-hand install preserves all counters including40");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechArms.DRILL, 1)); inv = inventory(p);
            h.assertTrue(!m.installArm(p, InteractionHand.MAIN_HAND) && unchanged(p, inv) && independent(m).equals(other), "No hot swap");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack("best_diagnostic_tool", 1));
            var marker = new ItemStack(Items.IRON_PICKAXE); p.setItemInHand(InteractionHand.OFF_HAND, marker);
            for(int i = 1; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64)); var before = m.stateTag(); inv = inventory(p);
            m.interact(p, InteractionHand.MAIN_HAND); h.assertTrue(before.equals(m.stateTag()) && unchanged(p, inv) && m.isolated(), "Full inventory explicit removal is atomic");
            p.getInventory().setItem(35, ItemStack.EMPTY); m.interact(p, InteractionHand.MAIN_HAND);
            h.assertTrue(m.armKey().isEmpty() && p.getInventory().getItem(35).is(CatalogueContent.item(BestMechArms.HEAVY_DRILL)) && p.getOffhandItem() == marker && independent(m).equals(other), "Removal returns physical arm and keeps marker");
            clear(p); service(p, light); t = light.stateTag(); t.putString(BestMechArms.STATE, ""); light.loadState(t);
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechArms.HEAVY_DRILL, 1)); before = light.stateTag(); inv = inventory(p);
            h.assertTrue(!light.installArm(p, InteractionHand.MAIN_HAND) && before.equals(light.stateTag()) && unchanged(p, inv), "Light cannot install heavy arm");
            t.putString(BestMechArms.STATE, BestMechArms.HEAVY_DRILL); t.putInt("DrillCooldown", 40); light.loadState(t); before = light.stateTag();
            h.assertTrue(!light.armCompatible() && !light.work(p) && before.equals(light.stateTag()), "Loaded incompatible key stays physical and inert");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack("best_diagnostic_tool", 1)); p.setItemInHand(InteractionHand.OFF_HAND, marker); light.interact(p, InteractionHand.MAIN_HAND);
            h.assertTrue(light.armKey().isEmpty() && p.getInventory().items.stream().anyMatch(s -> s.is(CatalogueContent.item(BestMechArms.HEAVY_DRILL))) && light.stateTag().getInt("DrillCooldown") == 40, "Incompatible arm returns without conversion or cooldown reset");
            clear(p); service(p, m); t = m.stateTag(); t.putString(BestMechArms.STATE, BestMechArms.HEAVY_DRILL); m.loadState(t); before = m.stateTag();
            var saved = new CompoundTag(); m.save(saved); var loaded = EntityType.loadEntityRecursive(saved, h.getLevel(), e -> e);
            h.assertTrue(loaded instanceof BestMechEntity restored && restored.stateTag().equals(before), "Entity save/load retains exact arm and40 cooldown"); if(loaded != null) loaded.discard();
            h.assertTrue(m.pack(p) && BestMechItem.state(p.getMainHandItem()).equals(before), "Packing retains exact state"); p.setShiftKeyDown(false);
            var floor = h.absolutePos(new BlockPos(7, 0, 7));
            h.assertTrue(p.getMainHandItem().getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false))).consumesAction(), "Packed heavy redeploys");
            m = h.getLevel().getEntitiesOfClass(BestMechEntity.class, new AABB(floor).inflate(5), e -> e.heavy() && !e.isRemoved()).stream().findFirst().orElseThrow();
            h.assertTrue(m.stateTag().equals(before), "Deploy preserves every counter"); var box = m.getBoundingBox().inflate(3);
            m.setHealth(0); m.die(h.getLevel().damageSources().generic()); m.die(h.getLevel().damageSources().generic());
            var loot = h.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(CatalogueContent.item("best_heavy_mech")));
            before.putFloat("Health", 0); h.assertTrue(loot.size() == 1 && BestMechItem.state(loot.getFirst().getItem()).equals(before), "Death packs one arm-bearing chassis with40 cooldown"); loot.forEach(Entity::discard);
        } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(drops, h.getLevel().getServer()); clear(p); m.discard(); light.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realRiderDiamondDropsThreeAxesAndPerBlockProtection(GameTestHelper h) {
        var p = player(h); var m = mech(h, true); var center = h.absolutePos(new BlockPos(6, 5, 6));
        var events = new ArrayList<BlockPos>(); var denied = new HashSet<BlockPos>();
        Consumer<BlockEvent.BreakEvent> protection = e -> { if(e.getPlayer() == p) { events.add(e.getPos().immutable()); if(denied.contains(e.getPos())) e.setCanceled(true); } };
        boolean drops = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, h.getLevel().getServer()); NeoForge.EVENT_BUS.addListener(protection);
        try {
            var held = new ItemStack(Items.DIAMOND, 7); p.setItemInHand(InteractionHand.MAIN_HAND, held); var heldCopy = held.copy();
            for(var axis : Direction.Axis.values()) {
                reset(m, 1_000_000, 0, 0); var positions = plane(center, axis); fill(h, positions); aim(p, m, center, axis); events.clear();
                var outside = axis == Direction.Axis.X ? center.offset(0, 2, 0) : center.offset(2, 0, 0);
                var behind = axis == Direction.Axis.X ? center.east() : axis == Direction.Axis.Y ? center.below() : center.south();
                h.getLevel().setBlockAndUpdate(outside, Blocks.OBSIDIAN.defaultBlockState()); h.getLevel().setBlockAndUpdate(behind, Blocks.OBSIDIAN.defaultBlockState());
                h.assertTrue(m.work(p) && events.equals(positions) && positions.stream().allMatch(pos -> h.getLevel().getBlockState(pos).isAir()), "Center first then deterministic3x3 plane " + axis);
                h.assertTrue(m.energy() == 568000 && m.heat() == 315 && m.wear() == 9 && m.stateTag().getInt("DrillCooldown") == 40 && p.getMainHandItem() == held && ItemStack.matches(held, heldCopy), "Only nine successful blocks charge48000/35/1 and exact hand restored");
                h.assertTrue(h.getLevel().getBlockState(outside).is(Blocks.OBSIDIAN) && h.getLevel().getBlockState(behind).is(Blocks.OBSIDIAN), "No outside plane or vein work");
                h.assertTrue(m.cargo().countItem(Items.OBSIDIAN) == 9, "Diamond-tier ordinary drops enter cargo");
                m.cargo().clearContent();
                h.getLevel().setBlockAndUpdate(outside, Blocks.AIR.defaultBlockState()); h.getLevel().setBlockAndUpdate(behind, Blocks.AIR.defaultBlockState());
            }
            var positions = plane(center, Direction.Axis.Z); fill(h, positions); reset(m, 1_000_000, 0, 0); aim(p, m, center, Direction.Axis.Z);
            denied.add(positions.get(1)); h.getLevel().setBlockAndUpdate(positions.get(2), Blocks.CHEST.defaultBlockState()); events.clear();
            h.assertTrue(m.work(p) && events.getFirst().equals(center) && events.contains(positions.get(1)) && !events.contains(positions.get(2))
                    && m.energy() == 664000 && m.wear() == 7 && h.getLevel().getBlockState(positions.get(1)).is(Blocks.OBSIDIAN) && h.getLevel().getBlockState(positions.get(2)).is(Blocks.CHEST), "Protected candidate skipped with no cost; BE never broken; later blocks continue");
            fill(h, positions); reset(m, 1_000_000, 0, 0); denied.clear(); denied.add(center); events.clear(); var before = m.stateTag();
            h.assertTrue(!m.work(p) && events.equals(List.of(center)) && before.equals(m.stateTag()) && positions.stream().allMatch(pos -> h.getLevel().getBlockState(pos).is(Blocks.OBSIDIAN)) && p.getMainHandItem() == held, "Denied center means no neighbors, costs or cooldown");
            denied.clear();
            for(var block : List.of(Blocks.CHEST, Blocks.BEDROCK, Blocks.WATER)) {
                h.getLevel().setBlockAndUpdate(center, block.defaultBlockState()); events.clear();
                h.assertTrue(!m.work(p) && events.isEmpty() && before.equals(m.stateTag()) && positions.subList(1, 9).stream().allMatch(pos -> h.getLevel().getBlockState(pos).is(Blocks.OBSIDIAN)), "Ineligible center never enters break pipeline: " + block);
            }
        } finally { NeoForge.EVENT_BUS.unregister(protection); h.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(drops, h.getLevel().getServer()); clear(p); m.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void partialBudgetsThermalWearReachCooldownAndInvalidOperators(GameTestHelper h) {
        var p = player(h); var stranger = player(h); var m = mech(h, true); var light = mech(h, false);
        var center = h.absolutePos(new BlockPos(6, 5, 6)); var positions = plane(center, Direction.Axis.Z); var events = new ArrayList<BlockPos>();
        Consumer<BlockEvent.BreakEvent> protection = e -> { if(e.getPlayer() == p) { events.add(e.getPos().immutable()); if(e.getPos().equals(positions.get(1))) e.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(protection);
        try {
            var held = new ItemStack(Items.EMERALD, 3); p.setItemInHand(InteractionHand.MAIN_HAND, held);
            for(int budget : new int[]{48000, 96000}) {
                fill(h, positions); reset(m, budget, 0, 0); aim(p, m, center, Direction.Axis.Z); events.clear();
                h.assertTrue(m.work(p) && m.energy() == 0 && m.heat() == budget / 48000 * 35 && m.wear() == budget / 48000
                        && h.getLevel().getBlockState(positions.get(1)).is(Blocks.OBSIDIAN), "Partial budget pays only successes and skips protected candidate");
                h.assertTrue(budget == 48000 ? events.equals(List.of(center)) : events.equals(List.of(center, positions.get(1), positions.get(2))), "Deterministic traversal stops when next block unaffordable");
                h.assertTrue(m.stateTag().getInt("DrillCooldown") == 40 && p.getMainHandItem() == held, "First success creates one40 tick cooldown");
            }
            for(boolean thermal : new boolean[]{true, false}) {
                fill(h, positions); reset(m, 1_000_000, thermal ? 980 : 0, thermal ? 0 : BestMechEntity.MAX_WEAR - 1); aim(p, m, center, Direction.Axis.Z); events.clear();
                h.assertTrue(m.work(p) && events.equals(List.of(center)) && m.energy() == 952000
                        && (thermal ? m.thermalLocked() && m.heat() == 1000 : m.wear() == BestMechEntity.MAX_WEAR), "Stop after successful thermal/wear boundary");
            }
            fill(h, positions); reset(m, 96000, 0, 0); aim(p, m, center, Direction.Axis.Z); p.moveTo(p.getX(), p.getY(), center.getZ() - p.blockInteractionRange() + .01, 0, 0); events.clear();
            h.assertTrue(m.work(p) && events.equals(List.of(center)) && m.energy() == 48000 && positions.subList(1, 9).stream().allMatch(pos -> h.getLevel().getBlockState(pos).is(Blocks.OBSIDIAN)), "Each corner independently obeys eye-to-unit-AABB actual reach");
            service(p, m); p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack("best_diagnostic_tool", 1)); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_PICKAXE)); m.interact(p, InteractionHand.MAIN_HAND);
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechArms.DRILL, 1)); h.assertTrue(m.installArm(p, InteractionHand.MAIN_HAND), "Install normal drill after isolated removal");
            h.assertTrue(m.stateTag().getInt("DrillCooldown") == 40 && m.workCooldown() == 20 && m.workCost() == 32000 && m.workHeat() == 120, "Swap retains40 shared cooldown while normal drill keeps20 economics");
            m.setIsolated(false); aim(p, m, center, Direction.Axis.Z); var before = m.stateTag(); h.assertTrue(!m.work(p) && before.equals(m.stateTag()), "Arm swap cannot bypass cooldown");
            for(int i = 0; i < 39; i++) { m.setOnGround(true); m.tick(); }
            h.assertTrue(m.stateTag().getInt("DrillCooldown") == 1, "Shared cooldown remains until40th tick"); m.setOnGround(true); m.tick(); h.assertTrue(m.stateTag().getInt("DrillCooldown") == 0, "40th tick clears cooldown");
            reset(m, 1_000_000, 0, 0); aim(p, m, center, Direction.Axis.Z); before = m.stateTag();
            h.assertTrue(!m.work(stranger) && before.equals(m.stateTag()), "Nonrider rejected without mutation");
            p.getPersistentData().putBoolean("BestMechOperation", false); h.assertTrue(!m.work(p) && before.equals(m.stateTag()), "Unqualified rider rejected"); p.getPersistentData().putBoolean("BestMechOperation", true);
            p.setGameMode(GameType.CREATIVE); h.assertTrue(!m.work(p) && before.equals(m.stateTag()), "Creative rider rejected"); p.setGameMode(GameType.ADVENTURE); h.assertTrue(!m.work(p) && before.equals(m.stateTag()), "Cannot-build rider rejected"); p.setGameMode(GameType.SURVIVAL);
            m.setOnGround(false); h.assertTrue(!m.work(p) && before.equals(m.stateTag()), "Airborne chassis rejected"); m.setOnGround(true);
            p.stopRiding(); before = m.stateTag(); h.assertTrue(!m.work(p) && before.equals(m.stateTag()), "Dismounted operator rejected");
            aim(p, light, center, Direction.Axis.Z); before = light.stateTag(); h.assertTrue(!light.work(p) && before.equals(light.stateTag()) && BestMechArms.HEAVY_DRILL.equals(light.armKey()), "Actual light rider cannot use loaded heavy key");
            h.assertTrue(BestMechArms.workCost(BestMechArms.EXCAVATOR) == 12000 && BestMechArms.workHeat(BestMechArms.EXCAVATOR) == 45 && BestMechArms.cooldown(BestMechArms.EXCAVATOR) == 20, "Excavator economics unchanged");
        } finally { NeoForge.EVENT_BUS.unregister(protection); clear(p); clear(stranger); m.discard(); light.discard(); }
        h.succeed();
    }
}
