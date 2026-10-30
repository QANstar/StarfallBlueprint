package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;

/** Real entity ticks, vanilla break transactions and inventory/state round trips. */
@GameTestHolder("starfall_blueprint_mech")
@PrefixGameTestTemplate(false)
public final class BestMechGameTests {
    private static ServerPlayer player(GameTestHelper h, boolean qualified) {
        // NeoForge FakePlayer refuses startRiding unconditionally; use the actual server player implementation.
        var profile = new GameProfile(UUID.randomUUID(), "BestMechTest");
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, CommonListenerCookie.createInitial(profile, false));
        p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent(); p.getInventory().selected = 0;
        p.getPersistentData().putInt("BestEngineeringResearch", 3);
        p.getPersistentData().putBoolean("BestDiagnostics", true);
        p.getPersistentData().putBoolean("BestLightMobility", true);
        p.getPersistentData().putBoolean("BestMechOperation", qualified);
        p.getAbilities().flying = false; p.setShiftKeyDown(false); p.setOnGround(true);
        Vec3 away = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 1, 1)));
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
    private static BestMechEntity mech(GameTestHelper h, int energy) {
        var m = BestMechs.TYPE.get().create(h.getLevel());
        var tag = new CompoundTag(); tag.putInt("Energy", energy); tag.putFloat("Health", 100); m.loadState(tag);
        BlockPos pos = h.absolutePos(new BlockPos(5, 1, 3)); m.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        m.setOnGround(true); h.getLevel().addFreshEntity(m); return m;
    }
    private static int count(ServerPlayer p, String key) {
        return p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item(key))).mapToInt(ItemStack::getCount).sum();
    }
    private static void inputs(ServerPlayer p, Map<String, Integer> values) {
        int slot = 1; for(var e : values.entrySet()) p.getInventory().setItem(slot++, CatalogueContent.stack(e.getKey(), e.getValue()));
    }
    private static List<ItemStack> inventory(ServerPlayer p) { return p.getInventory().items.stream().map(ItemStack::copy).toList(); }
    private static boolean unchanged(ServerPlayer p, List<ItemStack> before) {
        for(int i = 0; i < 36; i++) if(!ItemStack.matches(before.get(i), p.getInventory().getItem(i))) return false;
        return true;
    }
    private static void tickRider(BestMechEntity m, ServerPlayer p, float x, float z) {
        p.setPlayerInput(x, z, false, false); m.tick(); m.positionRider(p);
    }
    private static InteractionResult deploy(GameTestHelper h, ServerPlayer p, BlockPos floor) {
        var world = h.absolutePos(floor);
        return p.getMainHandItem().getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(world).add(0, .5, 0), Direction.UP, world, false)));
    }
    private static BestMechEntity deployed(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(BestMechEntity.class,
                new AABB(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)), Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(12, 10, 12)))), e -> !e.isRemoved()).stream().findFirst().orElseThrow();
    }
    private static void setState(BestMechEntity m, String field, int value) {
        var t = m.stateTag(); t.putInt(field, value); m.loadState(t);
    }

    @GameTest(template = "empty")
    public static void previewIsReadOnlyAndCountsOnlyUsableMaterials(GameTestHelper h) {
        var p = player(h, true);
        try {
            inputs(p, Map.of("steel",32,"load",16,"conductor",8,"board",4,"steel_fix",16,"head",2,"sensor",2,"sheath",8));
            var load = p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item("load"))).findFirst().orElseThrow();
            load.setCount(8);
            var polluted = CatalogueContent.stack("load",8);
            polluted.set(CatalogueContent.QUALITY.get(),new MaterialQuality(90,100,0,1,true,"load"));
            p.getInventory().setItem(20,polluted);
            var before = inventory(p);
            var preview = BestEngineering.preview(p,18);
            var requirement = preview.materials().stream().filter(m -> m.item().is(CatalogueContent.item("load"))).findFirst().orElseThrow();
            h.assertTrue(preview.status()==BestEngineering.Status.MATERIALS && requirement.available()==8 && requirement.required()==16,
                    "Visible counts exclude contaminated input exactly as the actual transaction does");
            h.assertTrue(unchanged(p,before) && !BestEngineering.perform(p,18) && unchanged(p,before),
                    "Neither preview nor a failed assembly mutates clean or contaminated inputs");
            polluted.remove(CatalogueContent.QUALITY.get());
            h.assertTrue(BestEngineering.preview(p,18).executable(),"Cleaning the same input immediately refreshes availability");
            h.assertTrue(BestEngineering.preview(p,17).status()==BestEngineering.Status.ALREADY_LEARNED,"Learned actions report their actual disabled reason");
            p.getPersistentData().putBoolean("BestMechOperation",false);
            before=inventory(p);
            for(int action=-1;action<=19;action++) BestEngineering.preview(p,action);
            h.assertTrue(unchanged(p,before) && !BestEngineering.hasMechOperation(p),"Repeated previews never consume inputs, manufacture output or teach knowledge");
            h.assertTrue(BestEngineering.preview(p,17).executable(),"Learning can be previewed independently from assembly");
        } finally { clear(p); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void serverRechecksChangesAfterAnExecutablePreview(GameTestHelper h) {
        var p = player(h,true);
        try {
            for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
            inputs(p,Map.of("steel",64,"load",32,"conductor",16,"board",8,"steel_fix",32,"head",4,"sensor",4,"sheath",16));
            p.getInventory().setItem(35,ItemStack.EMPTY);
            h.assertTrue(BestEngineering.preview(p,18).executable(),"Initial preview has knowledge, materials and output space");
            p.getPersistentData().putBoolean("BestMechOperation",false);
            h.assertTrue(BestEngineering.preview(p,18,new BestEngineering.Knowledge(3,true,true,true)).executable(),
                    "A client knowledge snapshot can differ from current server knowledge");
            var before=inventory(p);
            h.assertTrue(!BestEngineering.perform(p,18)&&unchanged(p,before),"Commit derives knowledge from the server player, not an earlier preview");
            p.getPersistentData().putBoolean("BestMechOperation",true);
            h.assertTrue(BestEngineering.preview(p,18).executable(),"Restored personal knowledge refreshes the preview");
            var steel=p.getInventory().items.stream().filter(s->s.is(CatalogueContent.item("steel"))).findFirst().orElseThrow();
            steel.setCount(31);before=inventory(p);
            h.assertTrue(!BestEngineering.perform(p,18)&&unchanged(p,before),"Materials removed after preview cannot be spent or partially committed");
            steel.setCount(64);
            h.assertTrue(BestEngineering.preview(p,18).executable(),"Restored materials refresh the preview");
            p.getInventory().setItem(35,new ItemStack(Items.COBBLESTONE,64));before=inventory(p);
            h.assertTrue(BestEngineering.preview(p,18).status()==BestEngineering.Status.SPACE
                    &&!BestEngineering.perform(p,18)&&unchanged(p,before),"Output space is simulated after costs and rechecked before commit");
            p.getInventory().setItem(35,ItemStack.EMPTY);
            h.assertTrue(BestEngineering.preview(p,18).executable()&&BestEngineering.perform(p,18)&&count(p,"best_mech")==1,
                    "The same current state accepted by preview commits exactly one chassis");
        } finally { clear(p); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void maintenancePreviewCannotMutateEquipment(GameTestHelper h) {
        var p=player(h,true);
        try {
            var frame=CatalogueContent.stack("best_frame",1);
            var data=CatalogueEquipment.getData(frame);
            data.putInt("BestEnergy",12345);data.putInt("BestHeat",321);data.putInt("BestWear",456);
            data.putInt("BestWorkTicks",37);data.putBoolean("BestStrideInstalled",true);data.putBoolean("BestStrideEnabled",true);
            CatalogueEquipment.setData(frame,data);
            p.setItemInHand(InteractionHand.MAIN_HAND,frame);
            p.getInventory().setItem(1,CatalogueContent.stack("steel",1));
            p.getInventory().setItem(2,new ItemStack(Items.WATER_BUCKET));
            var before=inventory(p);
            for(int action:new int[]{4,7,11,12,15})h.assertTrue(BestEngineering.preview(p,action).executable(),"Available equipment operation can be previewed: "+action);
            h.assertTrue(unchanged(p,before),"Repair, cooling, toggle, removal and isolation previews preserve all actual equipment fields");
            h.assertTrue(BestEngineering.perform(p,4)&&BestEquipment.wear(p.getMainHandItem())==256
                    &&BestEquipment.heat(p.getMainHandItem())==321&&BestEquipment.charge(p.getMainHandItem())==12345
                    &&BestEquipment.strideEnabled(p.getMainHandItem())&&CatalogueEquipment.getData(p.getMainHandItem()).getInt("BestWorkTicks")==37,
                    "Only the selected real repair commits, preserving charge, heat, module and work count");
        } finally { clear(p); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mechKnowledgeRequiresAllPersonalBranchesAndClones(GameTestHelper h) {
        var p = player(h, false); var other = player(h, false);
        try {
            inputs(p, Map.of("load", 8, "conductor", 8, "sensor", 4, "board", 4));
            for(String missing : List.of("BestDiagnostics", "BestLightMobility")) {
                p.getPersistentData().putBoolean(missing, false); var before = inventory(p);
                h.assertTrue(!BestEngineering.perform(p, 17) && unchanged(p, before), "Missing personal branch blocks learning without consuming any input: " + missing);
                p.getPersistentData().putBoolean(missing, true);
            }
            p.getPersistentData().putInt("BestEngineeringResearch", 2); var before = inventory(p);
            h.assertTrue(!BestEngineering.perform(p, 17) && unchanged(p, before), "Research three is independently required");
            p.getPersistentData().putInt("BestEngineeringResearch", 3);
            h.assertTrue(BestEngineering.perform(p, 17) && BestEngineering.hasMechOperation(p) && !BestEngineering.hasMechOperation(other)
                    && count(p, "load") == 4 && count(p, "conductor") == 4 && count(p, "sensor") == 2 && count(p, "board") == 2,
                    "Mech operation is personal and consumes exactly four load, four conductor, two sensor and two board");
            before = inventory(p); h.assertTrue(!BestEngineering.perform(p, 17) && unchanged(p, before), "Repeated learning cannot consume materials");
            BestEngineering.clonePlayer(new PlayerEvent.Clone(other, p, true));
            h.assertTrue(BestMechEntity.qualified(other) && BestEngineering.researchLevel(other) == 3, "Death clone retains all four independent personal prerequisites");
        } finally { clear(p); clear(other); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void assemblyCommitsExactInputsAndProducesAnEmptySingleChassis(GameTestHelper h) {
        var p = player(h, true);
        try {
            for(int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            var costs = Map.of("steel", 32, "load", 16, "conductor", 8, "board", 4, "steel_fix", 16, "head", 2, "sensor", 2, "sheath", 8);
            var doubled = new LinkedHashMap<String, Integer>(); costs.forEach((k,v) -> doubled.put(k, v * 2)); inputs(p, doubled);
            var before = inventory(p);
            h.assertTrue(!BestEngineering.perform(p, 18) && unchanged(p, before), "Full inventory rejects the entire assembly transaction with no material loss");
            p.getInventory().setItem(35, ItemStack.EMPTY);
            p.getPersistentData().putBoolean("BestMechOperation", false); before = inventory(p);
            h.assertTrue(!BestEngineering.perform(p, 18) && unchanged(p, before), "Assembly independently checks personal mech training");
            p.getPersistentData().putBoolean("BestMechOperation", true);
            h.assertTrue(BestEngineering.perform(p, 18) && count(p, "best_mech") == 1, "One manual assembly commits one chassis");
            for(var e : costs.entrySet()) h.assertTrue(count(p, e.getKey()) == e.getValue(), "Exact assembly cost: " + e.getKey());
            var packed = p.getInventory().getItem(35); var port = packed.getCapability(Capabilities.EnergyStorage.ITEM);
            h.assertTrue(packed.getItem() instanceof BestMechItem && packed.getMaxStackSize() == 1 && port != null
                    && port.getEnergyStored() == 0 && port.getMaxEnergyStored() == 20_000_000 && !port.canExtract(),
                    "The actual catalogue factory yields a single-stack 20MXH empty battery chassis");
        } finally { clear(p); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualRidingTicksMoveTurnChargeAndStopAtEveryBoundary(GameTestHelper h) {
        floor(h); var p = player(h, true); var other = player(h, true); var m = mech(h, 100_000);
        try {
            h.assertTrue(m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && p.getVehicle() == m
                    && !m.interact(other, InteractionHand.MAIN_HAND).consumesAction(), "Empty hand mounts exactly one qualified controlling passenger");
            var anchor = m.getType().getDimensions().attachments().get(EntityAttachment.PASSENGER, 0, 0);
            h.assertTrue(Math.abs(anchor.y - 2.2) < 1E-5 && m.getMaxHealth() == 100
                    && Math.abs(m.getBbWidth() - 1.8) < 1E-5 && Math.abs(m.getBbHeight() - 3.2) < 1E-5
                    && m.getAttributeValue(Attributes.STEP_HEIGHT) <= 1, "Industrial size, health, step ceiling and explicit seat anchor match the registered entity");
            m.positionRider(p); double initialSeat = p.getY() - m.getY();
            var start = m.position(); tickRider(m, p, 0, 1);
            h.assertTrue(m.getZ() > start.z && m.energy() == 99_600 && m.heat() == 4 && Math.abs(p.getY() - m.getY() - initialSeat) < 1E-5,
                    "Actual vanilla riding tick reads server player input, moves through collision, spends 400 and keeps the rider on its seat");
            tickRider(m, p, 0, 0); int energy = m.energy(); var stopped = m.position();
            h.assertTrue(m.getDeltaMovement().horizontalDistanceSqr() == 0 && m.energy() == energy && m.heat() == 2, "Idle has no powered horizontal momentum or cruise drain and passively cools");
            tickRider(m, p, 0, 0); h.assertTrue(m.position().distanceToSqr(stopped) < .01 && m.energy() == energy, "Idle stays stationary on a real floor");
            p.setYRot(-90); tickRider(m, p, 0, 1);
            h.assertTrue(m.getX() > stopped.x && Math.abs(m.getYRot() + 90) < .001, "Turning uses controlling player yaw and vanilla movement direction");
            m.setIsolated(true); m.setDeltaMovement(1, 0, 1); stopped = m.position(); energy = m.energy(); tickRider(m, p, 0, 1);
            h.assertTrue(m.position().distanceToSqr(stopped) < .01 && m.energy() == energy && m.getDeltaMovement().horizontalDistanceSqr() == 0, "Isolation immediately stops powered horizontal motion");
            m.setIsolated(false);
            for(String missing : List.of("BestMechOperation", "BestDiagnostics", "BestLightMobility")) {
                p.getPersistentData().putBoolean(missing, false); stopped = m.position(); tickRider(m, p, 0, 1);
                h.assertTrue(m.position().distanceToSqr(stopped) < .01 && m.energy() == energy, "Driving rechecks each personal branch every tick: " + missing);
                p.getPersistentData().putBoolean(missing, true);
            }
            p.getPersistentData().putInt("BestEngineeringResearch", 2); stopped = m.position(); tickRider(m, p, 0, 1);
            h.assertTrue(m.position().distanceToSqr(stopped) < .01 && m.energy() == energy, "Research cannot be removed while retaining powered driving");
            p.getPersistentData().putInt("BestEngineeringResearch", 3);
            setState(m, "Energy", 399); stopped = m.position(); tickRider(m, p, 1, 1);
            h.assertTrue(m.position().distanceToSqr(stopped) < .01 && m.energy() == 399, "Insufficient cruise energy neither moves nor spends");
            setState(m, "Energy", 400); tickRider(m, p, 0, 1);
            h.assertTrue(m.energy() == 0 && m.getDeltaMovement().horizontalDistanceSqr() == 0, "The final powered step immediately clears all horizontal momentum when it exhausts the battery");
            m.setIsolated(true); var safe = m.getDismountLocationForPassenger(p); p.stopRiding();
            h.assertTrue(!p.isPassenger() && h.getLevel().noCollision(p, p.getDimensions(Pose.STANDING).makeBoundingBox(safe)), "Blackout/isolation never trap the rider and exit search returns a collision-free location");
        } finally { clear(p); clear(other); m.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void floorCollisionStopsWallDriftAndUnpoweredChassisStillFalls(GameTestHelper h) {
        floor(h); var p = player(h, true); var m = mech(h, 100_000);
        try {
            h.assertTrue(p.startRiding(m), "A real server player mounts before the collision test");
            for(int x = 3; x <= 7; x++) for(int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
            for(int i = 0; i < 20; i++) tickRider(m, p, 0, 1);
            h.assertTrue(m.energy() < 100_000 && m.getBoundingBox().maxZ <= h.absolutePos(new BlockPos(0, 0, 5)).getZ() + 1E-5, "Real vanilla collision prevents a genuinely driven mech from crossing a tall wall");
            int energy = m.energy(); tickRider(m, p, 0, 1);
            h.assertTrue(m.energy() == energy, "A blocked movement command cannot consume another cruise action");
            setState(m, "Energy", 0); m.setIsolated(true);
            var pos = h.absolutePos(new BlockPos(8, 6, 8)); m.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
            m.setOnGround(false); m.setDeltaMovement(1, 0, 1); double y = m.getY();
            for(int i = 0; i < 3; i++) tickRider(m, p, 1, 1);
            h.assertTrue(m.getY() < y && m.getDeltaMovement().horizontalDistanceSqr() == 0 && m.energy() == 0,
                    "Blackout clears horizontal motion but preserves vanilla unpowered gravity");
        } finally { clear(p); m.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void packDeployAndServerSavePreserveExactUsedStateWithoutDuplication(GameTestHelper h) {
        floor(h); var p = player(h, true); var m = mech(h, 54321);
        try {
            var t = m.stateTag(); t.putInt("Heat", 678); t.putInt("Wear", 321); t.putInt("WorkTicks", 87); t.putInt("DrillCooldown", 13);
            t.putFloat("Health", 42.25f); t.putBoolean("ThermalLock", true); t.putBoolean("Isolated", true);
            t.putLong("ChargeTick", h.getLevel().getGameTime()); t.putInt("ChargeUsed", 456);
            m.loadState(t); m.setCustomName(Component.literal("Used chassis")); m.tick(); m.tick(); var before = m.stateTag();
            var saved = new CompoundTag(); m.saveWithoutId(saved); var loaded = BestMechs.TYPE.get().create(h.getLevel()); loaded.load(saved);
            h.assertTrue(loaded.stateTag().equals(before) && loaded.isNoAi() && loaded.isPersistenceRequired(), "Server save/load retains exact used state, name, persistence and no AI"); loaded.discard();
            for(int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            h.assertTrue(!m.pack(p) && !m.isRemoved() && m.stateTag().equals(before), "Full inventory rejects packing without discard or state changes");
            p.getInventory().setItem(0, ItemStack.EMPTY);
            h.assertTrue(m.pack(p) && m.isRemoved() && count(p, "best_mech") == 1 && !m.pack(p), "Packing creates exactly one inventory item and discards only once");
            var packed = p.getMainHandItem(); h.assertTrue(BestMechItem.state(packed).equals(before), "The physical packed item retains energy, thermal lock, wear, work, cooldown, health, name and charge budget");
            h.setBlock(new BlockPos(6, 2, 6), Blocks.STONE); var itemBefore = packed.copy();
            h.assertTrue(!deploy(h, p, new BlockPos(6, 0, 6)).consumesAction() && ItemStack.matches(itemBefore, packed), "Deployment collision failure leaves the entire item/state intact");
            h.setBlock(new BlockPos(6, 2, 6), Blocks.AIR);
            h.assertTrue(deploy(h, p, new BlockPos(6, 0, 6)).consumesAction() && packed.isEmpty(), "Successful deployment alone consumes the packed item");
            m = deployed(h); h.assertTrue(m.stateTag().equals(before) && m.getHealth() == 42.25f, "Re-deployment cannot heal damage or reset operational counters");
            m.setOnGround(true); m.setDeltaMovement(Vec3.ZERO); p.getInventory().clearContent();
            h.assertTrue(m.pack(p) && count(p, "best_mech") == 1, "A second pack-deploy cycle still owns one chassis");
        } finally { clear(p); if(!m.isRemoved()) m.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realCapabilitiesShareTickBudgetsAndCellChargingNeverLosesSource(GameTestHelper h) {
        floor(h); var p = player(h, true); var m = mech(h, 0);
        try {
            var packed = CatalogueContent.stack("best_mech", 1); var itemPort = packed.getCapability(Capabilities.EnergyStorage.ITEM);
            var original = packed.copy();
            h.assertTrue(itemPort != null && itemPort.receiveEnergy(9000, true) == 5000 && ItemStack.matches(original, packed), "Packed capability simulation is read-only and capped at 5000");
            h.assertTrue(itemPort.receiveEnergy(9000, false) == 5000 && BestMechItem.energy(packed).receiveEnergy(9000, false) == 0,
                    "New capability wrappers cannot reset the shared 5000-per-tick budget");
            m.loadState(BestMechItem.state(packed)); var entityPort = m.getCapability(Capabilities.EnergyStorage.ENTITY, null);
            h.assertTrue(entityPort != null && entityPort.receiveEnergy(512, false) == 0, "Item-to-entity conversion preserves the already spent tick budget");
            var t = m.stateTag(); t.putLong("ChargeTick", h.getLevel().getGameTime() - 1); t.putInt("ChargeUsed", 0); m.loadState(t);
            h.assertTrue(entityPort.receiveEnergy(1000, true) == 512 && m.energy() == 5000 && entityPort.receiveEnergy(1000, false) == 512
                    && m.energyPort().receiveEnergy(1, false) == 0 && !entityPort.canExtract(), "Entity capability simulations and all wrappers enforce one 512-per-tick allowance");
            m.setIsolated(true); var before = m.stateTag();
            h.assertTrue(!entityPort.canReceive() && entityPort.receiveEnergy(512, true) == 0 && entityPort.receiveEnergy(512, false) == 0 && m.stateTag().equals(before), "Previously acquired ports immediately honor isolation");
            var cell = CatalogueContent.stack("s01", 1); var c = CatalogueEquipment.getData(cell); c.putInt("Energy", 8000); CatalogueEquipment.setData(cell, c);
            p.setItemInHand(InteractionHand.MAIN_HAND, cell); p.setShiftKeyDown(true); var sourceBefore = cell.copy();
            m.interact(p, InteractionHand.MAIN_HAND); h.assertTrue(ItemStack.matches(sourceBefore, cell), "An isolated cell interaction never drains its source");
            m.setIsolated(false); t = m.stateTag(); t.putLong("ChargeTick", h.getLevel().getGameTime() - 1); t.putInt("ChargeUsed", 0); m.loadState(t);
            int energy = m.energy(); m.interact(p, InteractionHand.MAIN_HAND); m.interact(p, InteractionHand.MAIN_HAND);
            h.assertTrue(m.energy() == energy + 512 && CatalogueEquipment.getData(cell).getInt("Energy") == 7488,
                    "Repeated cell interactions in one tick transfer exactly 512 with equal source loss and receiver gain");
            m.setIsolated(true); m.setDeltaMovement(Vec3.ZERO); p.getInventory().clearContent(); h.assertTrue(m.pack(p), "Budget test packs the same physical entity");
            packed = p.getMainHandItem(); t = BestMechItem.state(packed); t.putBoolean("Isolated", false); BestMechItem.setState(packed, t);
            h.assertTrue(BestMechItem.energy(packed).receiveEnergy(9000, false) == 4488, "Entity-to-item conversion retains the spent 512 and leaves exactly 4488 of the packed allowance");
            baseChargingRespectsProtection(h, p);
        } finally { clear(p); if(!m.isRemoved()) m.discard(); }
        h.succeed();
    }

    private static void baseChargingRespectsProtection(GameTestHelper h, ServerPlayer p) {
        var pos = h.absolutePos(new BlockPos(9, 1, 2)); p.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        BlockPos sourcePos = BlockPos.containing(p.getEyePosition().add(0, 0, 2));
        h.getLevel().setBlockAndUpdate(sourcePos, CatalogueContent.BLOCKS.get("s02").get().defaultBlockState());
        var source = (IndustryBlockEntity) h.getLevel().getBlockEntity(sourcePos); source.setPower(10000);
        var packed = CatalogueContent.stack("best_mech", 1); p.setItemInHand(InteractionHand.MAIN_HAND, packed); p.setShiftKeyDown(true);
        Consumer<PlayerInteractEvent.RightClickBlock> protection = event -> { if(event.getEntity() == p && event.getPos().equals(sourcePos)) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(protection);
        try {
            h.assertTrue(packed.getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND).getResult().consumesAction(), "Air use begins charging before a source is targeted");
            var before = packed.copy(); packed.getItem().onUseTick(h.getLevel(), p, packed, 71999);
            h.assertTrue(source.getPower() == 10000 && ItemStack.matches(before, packed) && !p.isUsingItem(),
                    "Air-start charging still fires real RightClickBlock protection before touching either energy balance");
        } finally { NeoForge.EVENT_BUS.unregister(protection); }
        packed.getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        packed.getItem().onUseTick(h.getLevel(), p, packed, 71999); int received = BestMechItem.energy(packed).getEnergyStored();
        h.assertTrue(received > 0 && received <= 5000 && source.getPower() == 10000 - received,
                "An allowed real source transfers equal extracted and received energy through the held packed-item charging path");
        var tag = BestMechItem.state(packed); tag.putBoolean("Isolated", true); BestMechItem.setState(packed, tag);
        long remaining = source.getPower(); packed.getItem().onUseTick(h.getLevel(), p, packed, 71998);
        h.assertTrue(source.getPower() == remaining && !p.isUsingItem(), "Isolation immediately stops an active charging session without source loss");
        p.stopUsingItem();
    }

    @GameTest(template = "empty")
    public static void drillUsesVanillaDropsRestoresHeldItemAndHonorsEveryRejection(GameTestHelper h) {
        floor(h); var p = player(h, true); var m = mech(h, 1_000_000);
        Consumer<BlockEvent.BreakEvent> protection = event -> { if(event.getPlayer() == p) event.setCanceled(true); };
        boolean listener = false;
        boolean blockDrops = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, h.getLevel().getServer());
        try {
            p.startRiding(m); var held = new ItemStack(Items.DIAMOND, 7); p.setItemInHand(InteractionHand.MAIN_HAND, held);
            // The eye ray is derived solely from the real server player. No packet target or arbitrary position is provided.
            var eyeBase = h.absolutePos(new BlockPos(5, 2, 3)); p.moveTo(eyeBase.getX() + .5, eyeBase.getY(), eyeBase.getZ() + .5, 0, 0);
            BlockPos target = BlockPos.containing(p.getEyePosition().add(0, 0, 2));
            h.getLevel().setBlockAndUpdate(target, Blocks.STONE.defaultBlockState()); var beforeHeld = held.copy();
            h.assertTrue(m.drill(p) && h.getLevel().getBlockState(target).isAir() && m.energy() == 968000 && m.heat() == 120 && m.wear() == 1
                    && p.getMainHandItem() == held && ItemStack.matches(beforeHeld, held), "A real iron-tier vanilla break spends 32000/120/1 and restores the exact held object");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(1)).stream().anyMatch(e -> e.getItem().is(Items.COBBLESTONE)),
                    "Vanilla survival destruction produces ordinary iron-pickaxe cobblestone drops");
            h.getLevel().setBlockAndUpdate(target, Blocks.STONE.defaultBlockState()); int energy = m.energy();
            h.assertTrue(!m.drill(p) && m.energy() == energy && h.getLevel().getBlockState(target).is(Blocks.STONE), "Packet spam during the 20-tick cooldown cannot duplicate another action");
            for(var block : List.of(Blocks.CHEST, Blocks.BEDROCK, Blocks.OBSIDIAN)) {
                setState(m, "DrillCooldown", 0); h.getLevel().setBlockAndUpdate(target, block.defaultBlockState());
                h.assertTrue(!m.drill(p) && m.energy() == energy && h.getLevel().getBlockState(target).is(block) && p.getMainHandItem() == held,
                        "Block entities, negative hardness and unsupported diamond tier are rejected before spending: " + block);
            }
            h.getLevel().setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            BlockPos far = BlockPos.containing(p.getEyePosition().add(0, 0, 7)); h.getLevel().setBlockAndUpdate(far, Blocks.STONE.defaultBlockState());
            h.assertTrue(!m.drill(p) && m.energy() == energy && h.getLevel().getBlockState(far).is(Blocks.STONE), "A server eye ray cannot reach an arbitrary distant target");
            h.getLevel().setBlockAndUpdate(far, Blocks.AIR.defaultBlockState()); h.getLevel().setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
            p.getAbilities().mayBuild = false; h.assertTrue(!m.drill(p) && m.energy() == energy, "Adventure/build permission refusal consumes no power"); p.getAbilities().mayBuild = true;
            NeoForge.EVENT_BUS.addListener(protection); listener = true;
            h.assertTrue(!m.drill(p) && h.getLevel().getBlockState(target).is(Blocks.STONE) && m.energy() == energy && p.getMainHandItem() == held,
                    "A real NeoForge protection BreakEvent cancels destruction and still restores the exact held item");
            NeoForge.EVENT_BUS.unregister(protection); listener = false;
            p.setGameMode(GameType.CREATIVE); h.assertTrue(!m.drill(p) && m.energy() == energy, "Creative never bypasses survival drop/protection semantics"); p.setGameMode(GameType.SURVIVAL);
            p.stopRiding(); h.assertTrue(!m.drill(p), "Only the actual controlling rider can activate the intrinsic drill");
        } finally {
            if(listener) NeoForge.EVENT_BUS.unregister(protection);
            h.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(blockDrops, h.getLevel().getServer()); clear(p); m.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void thermalHysteresisWornRedeploymentRepairAndDeathCannotHealOrDuplicate(GameTestHelper h) {
        floor(h); var p = player(h, true); var m = mech(h, 50000);
        boolean drops = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(true, h.getLevel().getServer());
        try {
            var t = m.stateTag(); t.putInt("Heat", 402); t.putBoolean("ThermalLock", true); m.loadState(t);
            h.assertTrue(!m.ready(400), "Thermal hysteresis remains locked above 400 even below maximum heat");
            m.tick(); h.assertTrue(m.heat() == 400 && m.ready(400), "Passive cooling recovers the thermal latch exactly at 400");
            m.tick();
            t = m.stateTag(); t.putInt("Wear", BestMechEntity.MAX_WEAR); t.putFloat("Health", 35.5f); t.putBoolean("Isolated", true); m.loadState(t);
            h.assertTrue(!m.ready(0) && m.pack(p), "A live worn-out chassis can be packed without becoming ready");
            h.assertTrue(deploy(h, p, new BlockPos(6, 0, 6)).consumesAction(), "A live worn-out chassis remains redeployable for field repair");
            m = deployed(h); h.assertTrue(m.wear() == 2400 && m.getHealth() == 35.5f && !m.ready(0), "Redeployment cannot reset wear or grant free health");
            p.setShiftKeyDown(true); p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack("steel", 2));
            m.interact(p, InteractionHand.MAIN_HAND);
            h.assertTrue(m.wear() == 2200 && m.getHealth() == 40.5f && p.getMainHandItem().getCount() == 1 && m.isolated(), "One actual catalogue steel repairs exactly 200 wear and five health while isolated");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET)); m.interact(p, InteractionHand.MAIN_HAND);
            h.assertTrue(m.heat() == 0 && p.getMainHandItem().is(Items.BUCKET) && m.isolated(), "Cooling uses one water bucket and atomically returns its container");
            var deathBox = m.getBoundingBox().inflate(3); m.setHealth(0); m.die(h.getLevel().damageSources().generic()); m.die(h.getLevel().damageSources().generic());
            var damaged = h.getLevel().getEntitiesOfClass(ItemEntity.class, deathBox).stream().filter(e -> e.getItem().is(CatalogueContent.item("best_mech"))).toList();
            h.assertTrue(damaged.size() == 1 && BestMechItem.state(damaged.getFirst().getItem()).getFloat("Health") == 0, "Destruction drops at most one packed chassis retaining zero health");
            var broken = damaged.getFirst().getItem().copy(); p.setShiftKeyDown(false); p.setItemInHand(InteractionHand.MAIN_HAND, broken); var brokenBefore = broken.copy();
            h.assertTrue(!deploy(h, p, new BlockPos(2, 0, 8)).consumesAction() && ItemStack.matches(brokenBefore, broken)
                    && BestMechItem.energy(broken).receiveEnergy(5000, false) == 0, "A destroyed packed chassis cannot be deployed, charged or healed through conversion");
            damaged.forEach(Entity::discard);
        } finally {
            h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(drops, h.getLevel().getServer()); clear(p); if(!m.isRemoved()) m.discard();
        }
        h.succeed();
    }
}
