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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

/** Three real-server tests for body transactions, cargo lifetime and powered riding. */
@GameTestHolder("starfall_blueprint_mech_body")
@PrefixGameTestTemplate(false)
public final class BestMechBodyGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var profile = new GameProfile(UUID.randomUUID(), "MechBodyTest");
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, CommonListenerCookie.createInitial(profile, false)) {
            // This server-only fixture has no handshaken client. Keep real menu creation and
            // interactions while discarding outbound presentation packets at the listener.
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent(); p.getInventory().selected = 0;
        p.getPersistentData().putInt("BestEngineeringResearch", 3);
        for(String key : List.of("BestDiagnostics", "BestLightMobility", "BestMechOperation")) p.getPersistentData().putBoolean(key, true);
        return p;
    }
    private static void clear(ServerPlayer p) {
        p.closeContainer(); p.stopRiding(); p.stopUsingItem(); p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); p.setShiftKeyDown(false); p.xxa = p.zza = 0;
    }
    private static BestMechEntity mech(GameTestHelper h) {
        for(int x = 0; x < 12; x++) for(int z = 0; z < 12; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        var m = BestMechs.TYPE.get().create(h.getLevel()); var t = new CompoundTag(); t.putInt("Energy", 100_000); m.loadState(t);
        var pos = h.absolutePos(new BlockPos(5, 1, 3)); m.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        m.setOnGround(true); h.getLevel().addFreshEntity(m); return m;
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
    private static CompoundTag withoutBody(BestMechEntity m) { var t = m.stateTag(); t.remove(BestMechBodies.STATE); return t; }
    private static void set(BestMechEntity m, String key, int value) { var t = m.stateTag(); t.putInt(key, value); m.loadState(t); }
    private static void rejectRemoval(GameTestHelper h, ServerPlayer p, BestMechEntity m, String why) {
        var before = m.stateTag(); var inv = inventory(p);
        h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && before.equals(m.stateTag()) && unchanged(p, inv), why);
    }

    @GameTest(template = "empty")
    public static void manufacturingAndEveryBodyServiceRejectionAreAtomic(GameTestHelper h) {
        var p = player(h); var m = mech(h);
        try {
            var costs = Map.of("steel", 24, "load", 12, "conductor", 8, "steel_fix", 16, "sheath", 8);
            int slot = 1; for(var cost : costs.entrySet()) p.getInventory().setItem(slot++, CatalogueContent.stack(cost.getKey(), cost.getValue() * 2));
            var inv = inventory(p);
            h.assertTrue(BestEngineering.preview(p, 23).executable() && unchanged(p, inv), "Body assembly preview is read-only");
            for(String prerequisite : List.of("BestMechOperation", "BestDiagnostics", "BestLightMobility")) {
                p.getPersistentData().putBoolean(prerequisite, false);
                h.assertTrue(!BestEngineering.perform(p, 23) && unchanged(p, inv), "Existing branch is required: " + prerequisite);
                p.getPersistentData().putBoolean(prerequisite, true);
            }
            p.getPersistentData().putInt("BestEngineeringResearch", 2);
            h.assertTrue(!BestEngineering.perform(p, 23) && unchanged(p, inv), "Research three remains required");
            p.getPersistentData().putInt("BestEngineeringResearch", 3);
            var missing = p.getInventory().getItem(1).copy(); p.getInventory().setItem(1, ItemStack.EMPTY); inv = inventory(p);
            h.assertTrue(!BestEngineering.perform(p, 23) && unchanged(p, inv), "Missing materials do not consume anything"); p.getInventory().setItem(1, missing);
            for(int i = 0; i < 36; i++) if(p.getInventory().getItem(i).isEmpty()) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            inv = inventory(p); h.assertTrue(!BestEngineering.perform(p, 23) && unchanged(p, inv), "Full output inventory preserves every input");
            p.getInventory().setItem(0, ItemStack.EMPTY); h.assertTrue(BestEngineering.perform(p, 23), "Body assembly commits");
            var kit = p.getMainHandItem(); h.assertTrue(kit.is(CatalogueContent.item(BestMechBodies.REINFORCED)) && kit.getCount() == 1
                    && kit.getMaxStackSize() == 1 && !kit.isDamageableItem() && kit.getCapability(Capabilities.EnergyStorage.ITEM) == null, "Body kit is one ordinary nonstacking item");
            for(var cost : costs.entrySet()) h.assertTrue(p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item(cost.getKey())))
                    .mapToInt(ItemStack::getCount).sum() == cost.getValue(), "Exact body cost: " + cost.getKey());
            clear(p); service(p, m); var t = m.stateTag(); t.putString(BestMechModules.STATE, BestMechModules.DRIVE);
            t.putString(BestMechArms.STATE, BestMechArms.EXCAVATOR); t.putInt("Heat", 678); t.putInt("Wear", 321);
            t.putInt("WorkTicks", 87); t.putInt("DrillCooldown", 13); t.putFloat("Health", 42.25f);
            t.putLong("ChargeTick", h.getLevel().getGameTime()); t.putInt("ChargeUsed", 123); m.loadState(t);
            m.setCustomName(Component.literal("Used body")); m.cargo().setItem(0, new ItemStack(Items.DIAMOND, 7));
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechBodies.REINFORCED, 1));
            var before = m.stateTag(); inv = inventory(p);
            p.setShiftKeyDown(false); h.assertTrue(!m.installBody(p, InteractionHand.MAIN_HAND), "Install requires crouch"); p.setShiftKeyDown(true);
            h.assertTrue(!m.installBody(p, InteractionHand.OFF_HAND), "Install requires main hand");
            m.setOnGround(false); h.assertTrue(!m.installBody(p, InteractionHand.MAIN_HAND), "Airborne install refused"); m.setOnGround(true);
            m.setDeltaMovement(.2, 0, 0); h.assertTrue(!m.installBody(p, InteractionHand.MAIN_HAND), "Moving install refused"); m.setDeltaMovement(Vec3.ZERO);
            m.setIsolated(false); h.assertTrue(!m.installBody(p, InteractionHand.MAIN_HAND), "Connected install refused"); m.setIsolated(true);
            p.getPersistentData().putBoolean("BestMechOperation", false); h.assertTrue(!m.installBody(p, InteractionHand.MAIN_HAND), "Unqualified install refused"); p.getPersistentData().putBoolean("BestMechOperation", true);
            h.assertTrue(before.equals(m.stateTag()) && unchanged(p, inv), "Every rejected install preserves exact state and inventory");
            var operational = withoutBody(m); var container = m.cargo();
            h.assertTrue(m.installBody(p, InteractionHand.MAIN_HAND) && m.getMaxHealth() == 160 && m.getHealth() == 42.25f
                    && withoutBody(m).equals(operational) && m.cargo() == container && p.getMainHandItem().isEmpty(), "Install increases limits without healing or replacing cargo");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechBodies.REINFORCED, 1)); before = m.stateTag(); inv = inventory(p);
            h.assertTrue(!m.installBody(p, InteractionHand.MAIN_HAND) && before.equals(m.stateTag()) && unchanged(p, inv), "Duplicate upgrade is atomic");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack("best_diagnostic_tool", 1)); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_BLOCK));
            var tool = p.getOffhandItem().copy();
            m.setOnGround(false); rejectRemoval(h, p, m, "Airborne downgrade refuses without changes"); m.setOnGround(true);
            m.setDeltaMovement(.2, 0, 0); rejectRemoval(h, p, m, "Moving downgrade refuses without changes"); m.setDeltaMovement(Vec3.ZERO);
            m.setIsolated(false); rejectRemoval(h, p, m, "Connected downgrade refuses without toggling isolation"); m.setIsolated(true);
            p.getPersistentData().putBoolean("BestMechOperation", false); rejectRemoval(h, p, m, "Unqualified downgrade refuses without changes"); p.getPersistentData().putBoolean("BestMechOperation", true);
            set(m, "Energy", 20_000_001); rejectRemoval(h, p, m, "Energy overflow refuses downgrade without clamp or toggle"); set(m, "Energy", 100_000);
            m.setHealth(100.25f); rejectRemoval(h, p, m, "Health overflow refuses downgrade without healing or clamp"); m.setHealth(42.25f);
            for(int i = 9; i < 18; i++) { m.cargo().setItem(i, new ItemStack(Items.IRON_INGOT)); rejectRemoval(h, p, m, "Every upper cargo slot blocks downgrade: " + i); m.cargo().setItem(i, ItemStack.EMPTY); }
            for(int i = 1; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64)); rejectRemoval(h, p, m, "Full inventory refuses explicit removal without isolation toggle");
            p.getInventory().setItem(35, ItemStack.EMPTY); operational = withoutBody(m);
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && m.bodyKey().isEmpty() && m.getMaxHealth() == 100
                    && m.getHealth() == 42.25f && withoutBody(m).equals(operational) && m.cargo() == container
                    && p.getInventory().getItem(35).is(CatalogueContent.item(BestMechBodies.REINFORCED)) && ItemStack.matches(tool, p.getOffhandItem()), "Downgrade returns one kit and preserves all other state");
            rejectRemoval(h, p, m, "Standard body cannot generate another kit");
        } finally { clear(p); m.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realCargoMenuAndSavePackDeployDeathRetainItemsExactlyOnce(GameTestHelper h) {
        var p = player(h); var m = mech(h);
        boolean drops = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(true, h.getLevel().getServer());
        try {
            h.assertTrue(m.bodyKey().isEmpty() && m.capacity() == 20_000_000 && m.cargoSlots() == 9 && m.cargo().getContainerSize() == 18, "Missing legacy body is standard with fixed eighteen storage cells");
            var t = m.stateTag(); t.putString(BestMechBodies.STATE, "unknown_body"); m.loadState(t);
            h.assertTrue(m.bodyKey().isEmpty() && BestMechBodies.read(new CompoundTag()).isEmpty(), "Unknown and absent body keys are standard empty");
            service(p, m); p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST)); var chest = p.getMainHandItem().copy();
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && p.containerMenu instanceof BestMechCargoMenu
                    && ItemStack.matches(chest, p.getMainHandItem()), "Actual chest gesture opens cargo without consuming chest");
            var menu = p.containerMenu; h.assertTrue(menu.slots.size() == 45 && menu.stillValid(p), "Standard menu exposes nine cargo and thirty-six player slots");
            p.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 7)); menu.quickMoveStack(p, 9);
            h.assertTrue(m.cargo().getItem(0).is(Items.DIAMOND) && m.cargo().getItem(0).getCount() == 7 && p.getInventory().getItem(9).isEmpty(), "Real shift transfer moves player material into active cargo");
            p.getInventory().setItem(9, CatalogueContent.stack("best_mech", 1)); var inv = inventory(p); menu.quickMoveStack(p, 9);
            h.assertTrue(unchanged(p, inv) && !m.cargo().canPlaceItem(0, p.getInventory().getItem(9)), "Packed mechs cannot nest through shift transfer or container predicate");
            menu.quickMoveStack(p, 0); h.assertTrue(m.cargo().isEmpty() && p.getInventory().items.stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() == 7, "Cargo shift transfer returns each item once");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechBodies.REINFORCED, 1)); h.assertTrue(m.installBody(p, InteractionHand.MAIN_HAND), "Upgrade while old menu exists");
            h.assertTrue(!menu.stillValid(p), "Body slot-count change invalidates existing menu"); var before = m.stateTag();
            menu.clicked(0, 0, ClickType.PICKUP, p); h.assertTrue(before.equals(m.stateTag()), "Stale menu click cannot mutate cargo");
            p.closeContainer(); p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST)); m.interact(p, InteractionHand.MAIN_HAND);
            menu = p.containerMenu; h.assertTrue(menu.slots.size() == 54 && menu.stillValid(p), "Reinforced menu exposes eighteen cargo slots");
            for(int i = 0; i < 18; i++) m.cargo().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            p.getInventory().setItem(9, new ItemStack(Items.DIAMOND)); inv = inventory(p); menu.quickMoveStack(p, 18);
            h.assertTrue(unchanged(p, inv), "Shift transfer refuses full cargo without item loss");
            m.cargo().setItem(17, ItemStack.EMPTY); menu.quickMoveStack(p, 18);
            h.assertTrue(m.cargo().getItem(17).is(Items.DIAMOND) && p.getInventory().getItem(9).isEmpty(), "Real shift transfer reaches reinforced slot eighteen");
            m.cargo().clearContent(); m.cargo().setItem(0, new ItemStack(Items.DIAMOND, 7)); m.cargo().setItem(17, new ItemStack(Items.GOLD_INGOT, 11));
            p.moveTo(m.getX() + 8.01, m.getY(), m.getZ(), 0, 0); h.assertTrue(!menu.stillValid(p) && !m.canAccessCargo(p), "Cargo access stops beyond eight blocks"); service(p, m);
            before = m.stateTag(); var saved = new CompoundTag(); m.saveWithoutId(saved); var loaded = BestMechs.TYPE.get().create(h.getLevel()); loaded.load(saved);
            h.assertTrue(loaded.stateTag().equals(before) && loaded.cargo().getItem(17).getCount() == 11, "Registry-aware entity NBT retains exact body and cargo"); loaded.discard();
            p.getInventory().clearContent(); var oldContainer = m.cargo(); h.assertTrue(m.pack(p) && oldContainer.isEmpty() && !menu.stillValid(p), "Packing clears stale container after making the packed item");
            h.assertTrue(BestMechItem.state(p.getMainHandItem()).equals(before), "Packed item contains one exact cargo snapshot");
            p.setShiftKeyDown(false); var pos = h.absolutePos(new BlockPos(6, 0, 6));
            h.assertTrue(p.getMainHandItem().getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false))).consumesAction() && p.getMainHandItem().isEmpty(), "Actual packed item deploys once");
            var area = new AABB(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)), Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(12, 10, 12))));
            m = h.getLevel().getEntitiesOfClass(BestMechEntity.class, area, e -> !e.isRemoved()).stream().findFirst().orElseThrow();
            h.assertTrue(m.stateTag().equals(before), "Deployment preserves cargo and body exactly");
            var deathBox = m.getBoundingBox().inflate(3); m.setHealth(0); m.die(h.getLevel().damageSources().generic()); m.die(h.getLevel().damageSources().generic());
            var loot = h.getLevel().getEntitiesOfClass(ItemEntity.class, deathBox);
            h.assertTrue(loot.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum() == 7
                    && loot.stream().filter(e -> e.getItem().is(Items.GOLD_INGOT)).mapToInt(e -> e.getItem().getCount()).sum() == 11 && m.cargo().isEmpty(), "Death drops each cargo stack separately exactly once");
            var packed = loot.stream().filter(e -> e.getItem().is(CatalogueContent.item("best_mech"))).toList();
            h.assertTrue(packed.size() == 1 && BestMechItem.state(packed.getFirst().getItem()).getList("Cargo", 10).isEmpty()
                    && BestMechItem.state(packed.getFirst().getItem()).getFloat("Health") == 0, "Destroyed packed chassis contains no trapped or duplicate cargo");
            loot.forEach(Entity::discard);
        } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(drops, h.getLevel().getServer()); clear(p); if(!m.isRemoved()) m.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualRidingUsesBodyMovementBoundariesAndPackedCapacityWithoutHealing(GameTestHelper h) {
        var p = player(h); var m = mech(h);
        try {
            double standardDistance = 0; var start = m.position();
            for(String body : List.of("", BestMechBodies.REINFORCED)) for(String module : List.of("", BestMechModules.DRIVE)) {
                p.stopRiding(); var t = m.stateTag(); t.putString(BestMechBodies.STATE, body); t.putString(BestMechModules.STATE, module);
                t.putInt("Energy", 100_000); t.putInt("Heat", 0); t.putBoolean("Isolated", false); t.putFloat("Health", 42.25f); m.loadState(t);
                m.moveTo(start.x, start.y, start.z, 0, 0); m.setDeltaMovement(Vec3.ZERO); m.setOnGround(true); p.setShiftKeyDown(false);
                h.assertTrue(p.startRiding(m), "Actual server player rides each configuration"); p.setPlayerInput(0, 1, false, false); m.tick(); m.positionRider(p);
                int cost = body.isEmpty() ? module.isEmpty() ? 400 : 600 : module.isEmpty() ? 600 : 900;
                int heat = body.isEmpty() ? module.isEmpty() ? 4 : 6 : module.isEmpty() ? 6 : 8;
                double speed = body.isEmpty() ? module.isEmpty() ? .22 : .28 : module.isEmpty() ? .18 : .23;
                double distance = m.getZ() - start.z; if(standardDistance == 0) standardDistance = distance;
                h.assertTrue(distance > 0 && Math.abs(distance / standardDistance - speed / .22) < .001
                        && m.energy() == 100_000 - cost && m.heat() == heat && m.getHealth() == 42.25f, "Actual displacement uses body speed, cost, heat and exact damaged health");
                set(m, "Energy", cost - 1); var stopped = m.position(); m.setOnGround(true); p.setPlayerInput(0, 1, false, false); m.tick();
                h.assertTrue(m.energy() == cost - 1 && m.position().distanceToSqr(stopped) < .01, "Below-cost command spends nothing");
                set(m, "Energy", cost); m.setOnGround(true); m.tick();
                h.assertTrue(m.energy() == 0 && m.getDeltaMovement().horizontalDistanceSqr() == 0, "Last affordable movement clears powered momentum");
            }
            p.stopRiding(); var t = m.stateTag(); t.putInt("Energy", 30_000_001); t.putFloat("Health", 159.25f); m.loadState(t);
            h.assertTrue(m.energy() == 30_000_000 && m.getHealth() == 159.25f && m.getMaxHealth() == 160 && m.capacity() == 30_000_000, "Reinforced entity loads its increased limits before health");
            set(m, "Energy", 29_999_999);
            h.assertTrue(m.energyPort().receiveEnergy(512, false) == 1 && m.energy() == 30_000_000 && m.energyPort().receiveEnergy(1, false) == 0, "Entity receiving honors reinforced capacity");
            var stack = CatalogueContent.stack("best_mech", 1); t.putBoolean("Isolated", false); t.putInt("Energy", 29_999_999); t.putLong("ChargeTick", Long.MIN_VALUE); BestMechItem.setState(stack, t);
            var port = BestMechItem.energy(stack); h.assertTrue(port.getMaxEnergyStored() == 30_000_000 && port.receiveEnergy(5000, false) == 1
                    && port.getEnergyStored() == 30_000_000 && port.receiveEnergy(1, false) == 0 && stack.getItem().getBarWidth(stack) == 13, "Packed charging and bar honor the thirty-million cap");
            t.remove(BestMechBodies.STATE); BestMechItem.setState(stack, t); h.assertTrue(BestMechItem.energy(stack).getMaxEnergyStored() == 20_000_000
                    && BestMechItem.energy(stack).getEnergyStored() == 20_000_000, "Legacy packed body retains the standard cap");
        } finally { clear(p); m.discard(); }
        h.succeed();
    }
}
