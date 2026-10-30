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
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;

/** Three server tests for physical arm transactions, state conversion and actual protected work. */
@GameTestHolder("starfall_blueprint_mech_arms")
@PrefixGameTestTemplate(false)
public final class BestMechArmGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        // FakePlayer refuses riding; exercise the actual server player and vanilla break pipeline.
        var profile = new GameProfile(UUID.randomUUID(), "MechArmTest");
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, CommonListenerCookie.createInitial(profile, false));
        p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent(); p.getInventory().selected = 0;
        p.getPersistentData().putInt("BestEngineeringResearch", 3);
        for(String key : List.of("BestDiagnostics", "BestLightMobility", "BestMechOperation")) p.getPersistentData().putBoolean(key, true);
        var away = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 1, 1))); p.moveTo(away.x, away.y, away.z, 0, 0);
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
        m.setOnGround(true); h.getLevel().addFreshEntity(m); m.tick(); m.tick(); return m;
    }
    private static List<ItemStack> inventory(ServerPlayer p) { return p.getInventory().items.stream().map(ItemStack::copy).toList(); }
    private static boolean unchanged(ServerPlayer p, List<ItemStack> before) {
        for(int i = 0; i < 36; i++) if(!ItemStack.matches(before.get(i), p.getInventory().getItem(i))) return false;
        return true;
    }
    private static CompoundTag operationalState(BestMechEntity m) { var t = m.stateTag(); t.remove(BestMechArms.STATE); return t; }
    private static void set(BestMechEntity m, String key, int value) { var t = m.stateTag(); t.putInt(key, value); m.loadState(t); }
    private static void aim(GameTestHelper h, ServerPlayer p) {
        var pos = h.absolutePos(new BlockPos(5, 2, 3)); p.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
    }
    private static InteractionResult deploy(GameTestHelper h, ServerPlayer p) {
        var pos = h.absolutePos(new BlockPos(6, 0, 6));
        return p.getMainHandItem().getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false)));
    }

    @GameTest(template = "empty")
    public static void assemblyAndServiceAreAtomicAndPreserveEveryOtherState(GameTestHelper h) {
        floor(h); var p = player(h); var m = mech(h);
        try {
            for(int action : new int[]{21, 22}) {
                var costs = action == 21 ? Map.of("steel", 8, "conductor", 4, "head", 2, "board", 1, "steel_fix", 4)
                        : Map.of("steel", 8, "load", 4, "conductor", 2, "steel_fix", 4, "sheath", 2);
                p.getInventory().clearContent(); int slot = 1;
                for(var cost : costs.entrySet()) p.getInventory().setItem(slot++, CatalogueContent.stack(cost.getKey(), cost.getValue() * 2));
                var before = inventory(p);
                h.assertTrue(BestEngineering.preview(p, action).executable() && unchanged(p, before), "Arm assembly preview is read-only");
                for(String prerequisite : List.of("BestMechOperation", "BestDiagnostics", "BestLightMobility")) {
                    p.getPersistentData().putBoolean(prerequisite, false);
                    h.assertTrue(!BestEngineering.perform(p, action) && unchanged(p, before), "Assembly rechecks each existing personal branch: " + prerequisite);
                    p.getPersistentData().putBoolean(prerequisite, true);
                }
                p.getPersistentData().putInt("BestEngineeringResearch", 2);
                h.assertTrue(!BestEngineering.perform(p, action) && unchanged(p, before), "Research three remains required");
                p.getPersistentData().putInt("BestEngineeringResearch", 3);
                for(int i = 0; i < 36; i++) if(p.getInventory().getItem(i).isEmpty()) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                before = inventory(p);
                h.assertTrue(!BestEngineering.perform(p, action) && unchanged(p, before), "Full inventory refuses without spending inputs");
                p.getInventory().setItem(0, ItemStack.EMPTY);
                h.assertTrue(BestEngineering.perform(p, action), "Arm assembly commits");
                var arm = p.getMainHandItem(); String key = action == 21 ? BestMechArms.DRILL : BestMechArms.EXCAVATOR;
                h.assertTrue(arm.is(CatalogueContent.item(key)) && arm.getCount() == 1 && arm.getMaxStackSize() == 1
                        && !arm.isDamageableItem() && arm.getCapability(Capabilities.EnergyStorage.ITEM) == null, "One ordinary arm has no durability or stored energy");
                for(var cost : costs.entrySet()) h.assertTrue(p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item(cost.getKey())))
                        .mapToInt(ItemStack::getCount).sum() == cost.getValue(), "Exact assembly cost: " + cost.getKey());
            }
            clear(p); var t = m.stateTag(); t.putString(BestMechModules.STATE, BestMechModules.DRIVE);
            t.putInt("Heat", 678); t.putInt("Wear", 321); t.putInt("WorkTicks", 87); t.putInt("DrillCooldown", 13);
            t.putFloat("Health", 42.25f); t.putLong("ChargeTick", h.getLevel().getGameTime()); t.putInt("ChargeUsed", 123);
            m.loadState(t); m.setCustomName(Component.literal("Used arm chassis")); m.setIsolated(true); m.setDeltaMovement(Vec3.ZERO); m.setOnGround(true);
            p.setShiftKeyDown(true); p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechArms.EXCAVATOR, 1));
            var before = operationalState(m); var inv = inventory(p);
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && unchanged(p, inv) && m.armKey().equals(BestMechArms.DRILL), "Existing built-in drill must be removed before installation");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack("best_diagnostic_tool", 1));
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_PICKAXE)); var tool = p.getOffhandItem().copy();
            for(int i = 1; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64)); inv = inventory(p);
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && unchanged(p, inv) && m.isolated()
                    && m.armKey().equals(BestMechArms.DRILL) && operationalState(m).equals(before), "Full-inventory explicit removal never toggles isolation or mutates state");
            p.getInventory().setItem(35, ItemStack.EMPTY);
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && m.armKey().isEmpty()
                    && p.getInventory().getItem(35).is(CatalogueContent.item(BestMechArms.DRILL)) && ItemStack.matches(tool, p.getOffhandItem())
                    && operationalState(m).equals(before), "Removal returns the drill without touching tool, utility or operational state");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechArms.EXCAVATOR, 1));
            p.getPersistentData().putBoolean("BestMechOperation", false); inv = inventory(p);
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && unchanged(p, inv) && m.armKey().isEmpty(), "Unqualified arm interaction is consumed without fallback");
            p.getPersistentData().putBoolean("BestMechOperation", true);
            p.setShiftKeyDown(false); h.assertTrue(!m.installArm(p, InteractionHand.MAIN_HAND), "Installation requires crouching"); p.setShiftKeyDown(true);
            m.setOnGround(false); h.assertTrue(!m.installArm(p, InteractionHand.MAIN_HAND), "Airborne service fails"); m.setOnGround(true);
            m.setDeltaMovement(.2, 0, 0); h.assertTrue(!m.installArm(p, InteractionHand.MAIN_HAND), "Moving service fails"); m.setDeltaMovement(Vec3.ZERO);
            m.setIsolated(false); h.assertTrue(!m.installArm(p, InteractionHand.MAIN_HAND), "Connected service fails"); m.setIsolated(true);
            h.assertTrue(!m.installArm(p, InteractionHand.OFF_HAND) && m.installArm(p, InteractionHand.MAIN_HAND)
                    && p.getMainHandItem().isEmpty() && operationalState(m).equals(before), "One main-hand arm installs without changing any other state");
            for(String key : List.of(BestMechArms.EXCAVATOR, BestMechArms.DRILL)) {
                p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(key, 1)); inv = inventory(p);
                h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && unchanged(p, inv)
                        && m.armKey().equals(BestMechArms.EXCAVATOR) && operationalState(m).equals(before), "Duplicate and swap attempts are atomic");
            }
        } finally { clear(p); m.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyExplicitEmptyAndBothArmsSurviveSavePackDeployAndDeath(GameTestHelper h) {
        floor(h); var p = player(h); var m = mech(h);
        boolean drops = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(true, h.getLevel().getServer());
        try {
            h.assertTrue(BestMechArms.read(BestMechItem.state(CatalogueContent.stack("best_mech", 1))).equals(BestMechArms.DRILL), "Factory chassis includes the original drill without a new cost");
            var legacy = m.stateTag(); legacy.remove(BestMechArms.STATE); m.loadState(legacy);
            h.assertTrue(m.armKey().equals(BestMechArms.DRILL), "Old entity state receives its legacy drill");
            legacy.putString(BestMechArms.STATE, "unknown_arm"); m.loadState(legacy);
            h.assertTrue(m.armKey().isEmpty() && m.stateTag().getString(BestMechArms.STATE).isEmpty(), "Unknown state sanitizes to explicit empty rather than generating a drill");
            for(String key : List.of("", BestMechArms.DRILL, BestMechArms.EXCAVATOR)) {
                var t = m.stateTag(); t.putString(BestMechArms.STATE, key); t.putString(BestMechModules.STATE, BestMechModules.RADIATOR);
                t.putBoolean("Isolated", true); t.putInt("Heat", 678); t.putInt("Wear", 321); t.putInt("WorkTicks", 87);
                t.putInt("DrillCooldown", 13); t.putFloat("Health", 42.25f); t.putLong("ChargeTick", h.getLevel().getGameTime()); t.putInt("ChargeUsed", 123);
                m.loadState(t); m.setCustomName(Component.literal("Persistent arms")); m.setOnGround(true); m.setDeltaMovement(Vec3.ZERO); var before = m.stateTag();
                var saved = new CompoundTag(); m.saveWithoutId(saved); var loaded = BestMechs.TYPE.get().create(h.getLevel()); loaded.load(saved);
                h.assertTrue(loaded.stateTag().equals(before) && loaded.armKey().equals(key), "Entity NBT preserves explicit arm and every other field"); loaded.discard();
                p.getInventory().clearContent(); h.assertTrue(m.pack(p), "Isolated chassis packs");
                h.assertTrue(BestMechItem.state(p.getMainHandItem()).equals(before), "Packed item keeps exact arm state, including explicit empty");
                h.assertTrue(deploy(h, p).consumesAction() && p.getMainHandItem().isEmpty(), "Physical packed item deploys once");
                var box = new AABB(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)), Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(12, 10, 12))));
                m = h.getLevel().getEntitiesOfClass(BestMechEntity.class, box, e -> !e.isRemoved()).stream().findFirst().orElseThrow();
                h.assertTrue(m.stateTag().equals(before) && m.armKey().equals(key), "Deployment cannot reset arm, name, cooldown, utility or charge allowance");
            }
            var box = m.getBoundingBox().inflate(3); m.setHealth(0); m.die(h.getLevel().damageSources().generic()); m.die(h.getLevel().damageSources().generic());
            var packed = h.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream().filter(e -> e.getItem().is(CatalogueContent.item("best_mech"))).toList();
            h.assertTrue(packed.size() == 1 && BestMechArms.read(BestMechItem.state(packed.getFirst().getItem())).equals(BestMechArms.EXCAVATOR)
                    && BestMechItem.state(packed.getFirst().getItem()).getFloat("Health") == 0, "Death yields exactly one destroyed chassis with its arm");
            packed.forEach(Entity::discard);
        } finally {
            h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(drops, h.getLevel().getServer()); clear(p); if(!m.isRemoved()) m.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualRiderUsesArmToolDropsDynamicCostAndSharedCooldown(GameTestHelper h) {
        floor(h); var p = player(h); var m = mech(h);
        Consumer<BlockEvent.BreakEvent> protection = event -> { if(event.getPlayer() == p) event.setCanceled(true); };
        boolean listener = false, drops = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, h.getLevel().getServer());
        try {
            var held = new ItemStack(Items.DIAMOND, 7); p.setItemInHand(InteractionHand.MAIN_HAND, held); var beforeHeld = held.copy();
            h.assertTrue(!m.work(p) && p.startRiding(m), "Work requires the actual rider"); aim(h, p);
            BlockPos target = BlockPos.containing(p.getEyePosition().add(0, 0, 2));
            h.getLevel().setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
            h.assertTrue(m.drill(p) && m.energy() == 68000 && m.heat() == 120 && m.wear() == 1
                    && h.getLevel().getBlockState(target).isAir(), "Legacy drill delegates work at unchanged 32000/120/1");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(1)).stream().anyMatch(e -> e.getItem().is(Items.COBBLESTONE)), "Drill produces ordinary iron-pick drops");
            p.stopRiding(); p.setShiftKeyDown(true); m.setIsolated(true); m.setOnGround(true); m.setDeltaMovement(Vec3.ZERO);
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack("best_diagnostic_tool", 1)); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_PICKAXE));
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && m.armKey().isEmpty(), "Explicit gesture removes the drill");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechArms.EXCAVATOR, 1));
            h.assertTrue(m.installArm(p, InteractionHand.MAIN_HAND) && m.stateTag().getInt("DrillCooldown") == 20, "Swapping arms preserves the shared cooldown");
            p.setShiftKeyDown(false); p.setItemInHand(InteractionHand.MAIN_HAND, held); m.setIsolated(false); h.assertTrue(p.startRiding(m), "Rider remounts"); aim(h, p);
            h.getLevel().setBlockAndUpdate(target, Blocks.DIRT.defaultBlockState());
            h.assertTrue(!m.work(p) && m.energy() == 68000, "Swapping cannot bypass cooldown"); set(m, "DrillCooldown", 0);
            for(var block : List.of(Blocks.STONE, Blocks.OAK_LOG, Blocks.CHEST, Blocks.BEDROCK, Blocks.OBSIDIAN, Blocks.WATER)) {
                h.getLevel().setBlockAndUpdate(target, block.defaultBlockState()); var before = m.stateTag();
                h.assertTrue(!m.work(p) && m.stateTag().equals(before) && h.getLevel().getBlockState(target).is(block), "Excavator refuses non-shovel blocks and fluids: " + block);
            }
            h.getLevel().setBlockAndUpdate(target, Blocks.DIRT.defaultBlockState()); set(m, "Energy", 11999);
            h.assertTrue(!m.work(p) && m.energy() == 11999, "Excavator uses its dynamic 12000 boundary"); set(m, "Energy", 12000); set(m, "Heat", 0);
            NeoForge.EVENT_BUS.addListener(protection); listener = true; var before = m.stateTag();
            h.assertTrue(!m.work(p) && m.stateTag().equals(before) && h.getLevel().getBlockState(target).is(Blocks.DIRT)
                    && p.getMainHandItem() == held, "Real protection cancellation spends nothing and restores the hand");
            NeoForge.EVENT_BUS.unregister(protection); listener = false;
            var neighbor = target.east(); h.getLevel().setBlockAndUpdate(neighbor, Blocks.DIRT.defaultBlockState());
            h.assertTrue(m.work(p) && m.energy() == 0 && m.heat() == 45 && m.wear() == 2 && m.stateTag().getInt("DrillCooldown") == 20
                    && h.getLevel().getBlockState(target).isAir() && h.getLevel().getBlockState(neighbor).is(Blocks.DIRT)
                    && p.getMainHandItem() == held && ItemStack.matches(held, beforeHeld), "Excavator breaks one looked-at block for 12000/45/1 and restores the exact held item");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(1)).stream().anyMatch(e -> e.getItem().is(Items.DIRT)), "Excavator produces ordinary shovel drops");
            set(m, "Energy", 32000); set(m, "DrillCooldown", 0); var empty = m.stateTag(); empty.putString(BestMechArms.STATE, ""); m.loadState(empty);
            h.getLevel().setBlockAndUpdate(target, Blocks.DIRT.defaultBlockState()); before = m.stateTag();
            h.assertTrue(!m.work(p) && !m.drill(p) && m.stateTag().equals(before), "An explicit empty slot has no freehand or drill fallback");
        } finally {
            if(listener) NeoForge.EVENT_BUS.unregister(protection);
            h.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(drops, h.getLevel().getServer()); clear(p); m.discard();
        }
        h.succeed();
    }
}
