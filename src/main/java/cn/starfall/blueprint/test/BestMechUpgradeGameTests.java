package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.*;

/** Exactly three server checks for paid initial-to-advanced conversion and its operating gains. */
@GameTestHolder("starfall_blueprint_mech_upgrade")
@PrefixGameTestTemplate(false)
public final class BestMechUpgradeGameTests {
    private static final Map<String, Integer> COST = Map.of("steel",64,"load",32,"conductor",16,"board",8,"steel_fix",32,"head",2,"sensor",4,"sheath",16);
    private static ServerPlayer player(GameTestHelper h) {
        var profile = new GameProfile(UUID.randomUUID(), "MechUpgradeTest");
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, CommonListenerCookie.createInitial(profile, false)) {
            // This real server fixture has no handshaken client; retain actual server menus and transactions.
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent(); p.getInventory().selected = 0;
        p.getPersistentData().putInt("BestEngineeringResearch", 3);
        for(String key : List.of("BestDiagnostics", "BestLightMobility", "BestMechOperation", "BestAdvancedEngineering")) p.getPersistentData().putBoolean(key, true);
        return p;
    }
    private static void clear(ServerPlayer p) {
        p.closeContainer(); p.stopRiding(); p.stopUsingItem(); p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); p.setShiftKeyDown(false); p.setPlayerInput(0,0,false,false);
    }
    private static ItemStack source(boolean reinforced) {
        var source = CatalogueContent.stack("best_mech", 1); var t = new CompoundTag();
        t.putBoolean("Isolated", true); if(reinforced) t.putString(BestMechBodies.STATE, BestMechBodies.REINFORCED);
        BestMechItem.setState(source, t); return source;
    }
    private static void inputs(ServerPlayer p, ItemStack source, boolean surplus, boolean full) {
        clear(p); p.getInventory().selected = 0; p.setItemInHand(InteractionHand.MAIN_HAND, source); int slot = 1;
        for(var cost : COST.entrySet()) {
            int count = cost.getValue() + (surplus ? 1 : 0);
            while(count > 0) { int n = Math.min(CatalogueContent.stack(cost.getKey(),1).getMaxStackSize(), count); p.getInventory().setItem(slot++, CatalogueContent.stack(cost.getKey(), n)); count -= n; }
        }
        if(full) for(int i = 1; i < 36; i++) if(p.getInventory().getItem(i).isEmpty()) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE,64));
    }
    private static List<ItemStack> inventory(ServerPlayer p) { return p.getInventory().items.stream().map(ItemStack::copy).toList(); }
    private static boolean unchanged(ServerPlayer p, List<ItemStack> before) {
        for(int i=0;i<36;i++) if(!ItemStack.matches(before.get(i),p.getInventory().getItem(i))) return false;
        return true;
    }
    private static int count(ServerPlayer p, String key) { return p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item(key))).mapToInt(ItemStack::getCount).sum(); }
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z), Blocks.STONE); }
    private static BestMechEntity mech(GameTestHelper h, boolean heavy) {
        floor(h); var m = (heavy ? BestMechs.HEAVY_TYPE.get() : BestMechs.TYPE.get()).create(h.getLevel());
        var pos = h.absolutePos(new BlockPos(5,1,3)); m.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0); h.getLevel().addFreshEntity(m);
        for(int i=0;i<3;i++) m.tick(); return m;
    }
    private static void set(BestMechEntity m, String key, int n) { var t=m.stateTag(); t.putInt(key,n); m.loadState(t); }
    private static InteractionResult deploy(GameTestHelper h, ServerPlayer p) {
        var pos=h.absolutePos(new BlockPos(6,0,6));
        return p.getMainHandItem().getItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false)));
    }
    private static void cargo(GameTestHelper h, CompoundTag t, int slots) {
        var list = new ListTag();
        for(int i=0;i<slots;i++) { var entry=new CompoundTag(); entry.putInt("Slot",i); entry.put("Item",new ItemStack(Items.DIAMOND,i+1).save(h.getLevel().registryAccess())); list.add(entry); }
        t.put("Cargo",list);
    }

    @GameTest(template="empty")
    public static void action26PreviewPaymentIsolationBodyReturnAndPersonalLifecycleAreAtomic(GameTestHelper h) {
        var p=player(h); var replacement=player(h);
        try {
            var initial=source(false); inputs(p,initial,true,false); var inv=inventory(p); var knowledge=p.getPersistentData().copy();
            for(int i=0;i<2;i++) h.assertTrue(BestEngineering.preview(p,26).executable() && unchanged(p,inv) && knowledge.equals(p.getPersistentData()),"Repeated upgrade preview changes no stack or knowledge");
            for(String prerequisite : List.of("BestDiagnostics","BestLightMobility","BestMechOperation","BestAdvancedEngineering")) {
                p.getPersistentData().putBoolean(prerequisite,false); var before=p.getPersistentData().copy();
                h.assertTrue(!BestEngineering.preview(p,26).executable() && !BestEngineering.perform(p,26) && unchanged(p,inv) && before.equals(p.getPersistentData()),"Missing personal knowledge is atomic: "+prerequisite);
                p.getPersistentData().putBoolean(prerequisite,true);
            }
            p.getPersistentData().putInt("BestEngineeringResearch",2);
            h.assertTrue(!BestEngineering.perform(p,26) && unchanged(p,inv),"Research three remains required"); p.getPersistentData().putInt("BestEngineeringResearch",3);
            var connected=BestMechItem.state(initial); connected.putBoolean("Isolated",false); BestMechItem.setState(initial,connected); inv=inventory(p);
            h.assertTrue(!BestEngineering.preview(p,26).executable() && !BestEngineering.perform(p,26) && unchanged(p,inv),"Connected chassis cannot upgrade or spend");
            connected.putBoolean("Isolated",true); BestMechItem.setState(initial,connected);
            int steelSlot=-1; for(int i=1;i<36;i++) if(p.getInventory().getItem(i).is(CatalogueContent.item("steel"))) { steelSlot=i; break; }
            var steel=p.getInventory().getItem(steelSlot); p.getInventory().setItem(steelSlot,ItemStack.EMPTY); inv=inventory(p);
            h.assertTrue(!BestEngineering.perform(p,26) && unchanged(p,inv),"Missing part of split steel cost spends nothing"); p.getInventory().setItem(steelSlot,steel);
            var original=initial.copy();
            h.assertTrue(BestEngineering.perform(p,26) && p.getMainHandItem().is(CatalogueContent.item("best_heavy_mech")) && ItemStack.matches(initial,original),"Upgrade replaces the selected slot and leaves original source object untouched");
            h.assertTrue(BestMechItem.state(p.getMainHandItem()).getFloat("Health")==100 && knowledge.equals(p.getPersistentData()),"Upgrade preserves old health and grants no knowledge");
            for(String key:COST.keySet()) h.assertTrue(count(p,key)==1,"Exact incremental cost: "+key);
            inv=inventory(p); h.assertTrue(!BestEngineering.perform(p,26) && unchanged(p,inv),"Advanced chassis cannot be converted a second time");
            // Conversion replaces the selected source even when every inventory slot begins occupied.
            inputs(p,source(false),true,true);
            h.assertTrue(BestEngineering.preview(p,26).executable() && BestEngineering.perform(p,26) && p.getMainHandItem().is(CatalogueContent.item("best_heavy_mech")),"Full inventory succeeds by replacing the source in place");
            inputs(p,source(true),true,true); inv=inventory(p);
            h.assertTrue(BestEngineering.preview(p,26).executable() && BestEngineering.perform(p,26) && count(p,BestMechBodies.REINFORCED)==1,"Full inventory returns the reinforced body into a material cell freed by legal steel64 payment");
            // A material cell emptied by payment is available for the returned reinforced kit.
            inputs(p,source(true),false,true);
            h.assertTrue(BestEngineering.preview(p,26).executable() && BestEngineering.perform(p,26) && count(p,BestMechBodies.REINFORCED)==1
                    && BestMechItem.state(p.getMainHandItem()).getString(BestMechBodies.STATE).isEmpty()
                    && BestMechItem.state(p.getMainHandItem()).getFloat("Health")==160,"Exact payment frees a return slot; body is returned once without healing");
            replacement.getPersistentData().remove("BestAdvancedEngineering"); var old=p.getPersistentData().copy();
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement,p,true));
            h.assertTrue(replacement.getPersistentData().getBoolean("BestAdvancedEngineering") && BestEngineering.hasMechOperation(replacement)
                    && old.equals(p.getPersistentData()) && replacement.getInventory().isEmpty(),"Registered clone lifecycle preserves advanced personal knowledge without rewards");
            var fresh=player(h); fresh.getPersistentData().remove("BestAdvancedEngineering"); inputs(fresh,source(false),false,false); inv=inventory(fresh);
            h.assertTrue(!BestEngineering.perform(fresh,26) && unchanged(fresh,inv),"An old operator save does not acquire advanced engineering for free"); clear(fresh);
        } finally { clear(p); clear(replacement); }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void packedHelperPreservesLegacyComponentsCargoPaidFuelAndRealSavePackDeployDeath(GameTestHelper h) {
        var p=player(h); BestMechEntity m=null; boolean drops=h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(true,h.getLevel().getServer());
        try {
            for(boolean reinforced:List.of(false,true)) {
                var legacy=source(reinforced); var before=legacy.copy(); var result=BestMechUpgrades.upgraded(legacy);
                h.assertTrue(ItemStack.matches(legacy,before) && BestMechItem.state(result).getFloat("Health")== (reinforced?160:100),"Missing legacy Health resolves old maximum before changing identity");
            }
            for(float health:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
                var invalid=source(false); var t=BestMechItem.state(invalid); t.putFloat("Health",health); BestMechItem.setState(invalid,t); var before=invalid.copy();
                h.assertTrue(BestMechUpgrades.upgraded(invalid).isEmpty() && ItemStack.matches(invalid,before),"Dead or nonfinite Health is rejected without mutation: "+health);
            }
            var doubleSource=source(false); doubleSource.setCount(2);
            h.assertTrue(BestMechUpgrades.upgraded(doubleSource).isEmpty() && BestMechUpgrades.upgraded(ItemStack.EMPTY).isEmpty()
                    && BestMechUpgrades.upgraded(new ItemStack(Items.DIAMOND)).isEmpty() && BestMechUpgrades.upgraded(CatalogueContent.stack("best_heavy_mech",1)).isEmpty(),"Only one actual initial packed item can convert");
            for(String arm:List.of("",BestMechArms.DRILL,BestMechArms.EXCAVATOR)) for(String module:List.of("",BestMechModules.DRIVE,BestMechModules.RADIATOR)) {
                var initial=source(false); var t=BestMechItem.state(initial); t.putString(BestMechArms.STATE,arm); t.putString(BestMechModules.STATE,module);
                t.putString(BestMechCores.STATE,BestMechCores.GENERATOR); t.putString(BestMechLegs.STATE,BestMechLegs.CRUISE); BestMechItem.setState(initial,t);
                h.assertTrue(BestMechItem.state(BestMechUpgrades.upgraded(initial)).getString(BestMechArms.STATE).equals(arm)
                        && BestMechItem.state(BestMechUpgrades.upgraded(initial)).getString(BestMechModules.STATE).equals(module),"All legal arm and utility combinations survive conversion");
            }
            for(int slots:List.of(9,18,27)) {
                var initial=source(slots>9); var t=BestMechItem.state(initial); cargo(h,t,slots); BestMechItem.setState(initial,t);
                h.assertTrue(BestMechItem.state(BestMechUpgrades.upgraded(initial)).getList("Cargo",Tag.TAG_COMPOUND).equals(t.getList("Cargo",Tag.TAG_COMPOUND)),"Entire cargo backing survives including legacy overflow: "+slots);
            }
            var initial=source(true); var t=BestMechItem.state(initial); cargo(h,t,27);
            t.putFloat("Health",42.25f); t.putInt("Energy",29_123_456); t.putInt("Heat",678); t.putInt("Wear",321); t.putBoolean("ThermalLock",true);
            t.putInt("WorkTicks",87); t.putInt("DrillCooldown",13); t.putInt("CoreFuelTicks",17); t.putInt("CoreWorkTicks",731);
            t.putLong("ChargeTick",h.getLevel().getGameTime()); t.putInt("ChargeUsed",4999);
            t.putString(BestMechCores.STATE,BestMechCores.GENERATOR); t.putString(BestMechModules.STATE,BestMechModules.RADIATOR); t.putString(BestMechLegs.STATE,BestMechLegs.CRUISE);
            t.putString("UnknownFutureState","keep nested"); t.putString("Platform","light payload cannot override item identity");
            // Missing arm is deliberately kept missing so legacy default reading remains unchanged.
            BestMechItem.setState(initial,t); initial.set(DataComponents.CUSTOM_NAME,Component.literal("涓流 → 沧澜")); initial.set(DataComponents.UNBREAKABLE,new net.minecraft.world.item.component.Unbreakable(false)); initial.remove(DataComponents.ITEM_NAME);
            var data=CatalogueEquipment.getData(initial); data.putString("UnrelatedSystem","keep outer"); CatalogueEquipment.setData(initial,data);
            var before=initial.copy(); var advanced=BestMechUpgrades.upgraded(initial); var expected=t.copy(); expected.putString(BestMechBodies.STATE,"");
            h.assertTrue(ItemStack.matches(initial,before) && BestMechItem.state(advanced).equals(expected) && !BestMechItem.state(advanced).contains(BestMechArms.STATE)
                    && CatalogueEquipment.getData(advanced).getString("UnrelatedSystem").equals("keep outer")
                    && advanced.get(DataComponents.CUSTOM_NAME).equals(initial.get(DataComponents.CUSTOM_NAME)) && advanced.get(DataComponents.UNBREAKABLE).equals(initial.get(DataComponents.UNBREAKABLE))
                    && !advanced.has(DataComponents.ITEM_NAME),"Copy preserves arbitrary outer/nested state, name, explicit components and removals, and missing-arm semantics");
            m=BestMechs.HEAVY_TYPE.get().create(h.getLevel()); m.loadState(BestMechItem.state(advanced));
            h.assertTrue(m.heavy() && m.getHealth()==42.25f && m.getMaxHealth()==300 && m.capacity()==60_000_000 && m.cargoSlots()==27
                    && m.armKey().equals(BestMechArms.DRILL) && m.cargo().getItem(26).getCount()==27 && m.coreFuelTicks()==17
                    && m.stateTag().getInt("CoreWorkTicks")==731 && m.stateTag().getInt("ChargeUsed")==4999,"Runtime loads preserved HP, paid fuel, counters and all 27 cargo cells"); m.discard(); m=null;
            floor(h); p.setItemInHand(InteractionHand.MAIN_HAND,advanced);
            h.assertTrue(deploy(h,p).consumesAction() && p.getMainHandItem().isEmpty(),"Actual advanced deployment consumes converted item once");
            var box=new AABB(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)),Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(12,10,12))));
            m=h.getLevel().getEntitiesOfClass(BestMechEntity.class,box,e->!e.isRemoved()).getFirst(); var canonical=m.stateTag();
            h.assertTrue(m.heavy() && m.getHealth()==42.25f && m.cargo().getItem(26).getCount()==27,"Deployment keeps heavy identity, damaged HP and last cargo cell");
            var saved=new CompoundTag(); m.save(saved); var restored=EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);
            h.assertTrue(restored instanceof BestMechEntity loaded && loaded.heavy() && loaded.stateTag().equals(canonical),"Vanilla save/load preserves every runtime field"); if(restored!=null) restored.discard();
            m.setOnGround(true); m.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(m.pack(p) && BestMechItem.state(p.getMainHandItem()).equals(canonical),"Packing retains HP, fuel and every operating counter");
            p.setShiftKeyDown(false); h.assertTrue(deploy(h,p).consumesAction(),"Packed advanced chassis redeploys");
            m=h.getLevel().getEntitiesOfClass(BestMechEntity.class,box,e->!e.isRemoved()).getFirst(); var deathBox=m.getBoundingBox().inflate(3);
            m.setHealth(0); m.die(h.getLevel().damageSources().generic()); m.die(h.getLevel().damageSources().generic());
            var loot=h.getLevel().getEntitiesOfClass(ItemEntity.class,deathBox); var chassis=loot.stream().filter(e->e.getItem().is(CatalogueContent.item("best_heavy_mech"))).toList();
            h.assertTrue(chassis.size()==1 && loot.stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum()==378
                    && BestMechItem.state(chassis.getFirst().getItem()).getFloat("Health")==0 && BestMechItem.state(chassis.getFirst().getItem()).getInt("CoreFuelTicks")==17
                    && BestMechItem.state(chassis.getFirst().getItem()).getList("Cargo",Tag.TAG_COMPOUND).isEmpty(),"Death emits one destroyed advanced chassis, all cargo once and unchanged paid fuel");
            h.assertTrue(BestMechUpgrades.upgraded(chassis.getFirst().getItem()).isEmpty() && !BestMechItem.energy(chassis.getFirst().getItem()).canReceive(),"Dead advanced item cannot convert or charge"); loot.forEach(Entity::discard);
        } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(drops,h.getLevel().getServer()); clear(p); if(m!=null) m.discard(); }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void actualAdvancedMovementCoreCoolingThermalAndCapacityBoundariesImproveInitial(GameTestHelper h) {
        var p=player(h); var initial=mech(h,false); var advanced=BestMechs.HEAVY_TYPE.get().create(h.getLevel());
        try {
            var anchor=initial.position(); double initialDistance=0;
            for(boolean heavy:List.of(false,true)) {
                var m=heavy?advanced:initial;
                if(heavy) { initial.discard(); m.moveTo(anchor.x,anchor.y,anchor.z,0,0); h.getLevel().addFreshEntity(m); }
                for(String module:List.of("",BestMechModules.DRIVE)) for(String legs:List.of("",BestMechLegs.CRUISE)) {
                    p.stopRiding(); var t=new CompoundTag(); t.putInt("Energy",100_000); t.putString(BestMechModules.STATE,module); t.putString(BestMechLegs.STATE,legs); m.loadState(t);
                    m.moveTo(anchor.x,anchor.y,anchor.z,0,0); m.setDeltaMovement(Vec3.ZERO); m.setOnGround(true); p.setShiftKeyDown(false);
                    h.assertTrue(p.startRiding(m),"Qualified server operator mounts platform"); p.setPlayerInput(0,1,false,false); m.tick();
                    double distance=m.getZ()-anchor.z; double speed=heavy?(module.isEmpty()?.26:.34):(module.isEmpty()?.22:.28); if(!legs.isEmpty()) speed*=1.25;
                    if(initialDistance==0) initialDistance=distance;
                    h.assertTrue(distance>0 && Math.abs(distance/initialDistance-speed/.22)<.001 && Math.abs(m.movementSpeed()-speed)<1e-6,"Actual vanilla travel delivers platform speed with drive and wheel multipliers");
                    h.assertTrue(m.energy()==100_000-m.movementCost() && m.heat()==m.movementHeat(),"Improved speed retains exact configured movement cost and heat");
                    set(m,"Energy",m.movementCost()-1); var stopped=m.position(); m.setOnGround(true); m.tick();
                    h.assertTrue(m.position().distanceToSqr(stopped)<1e-8 && m.energy()==m.movementCost()-1,"Insufficient movement energy stops");
                    set(m,"Energy",m.movementCost()); m.setOnGround(true); m.tick();
                    h.assertTrue(m.energy()==0 && m.getZ()>stopped.z && m.getDeltaMovement().horizontalDistanceSqr()==0,"Last affordable movement tick executes once"); p.stopRiding();
                }
            }
            for(String module:List.of("",BestMechModules.RADIATOR)) {
                var t=new CompoundTag(); t.putString(BestMechModules.STATE,module); t.putString(BestMechCores.STATE,BestMechCores.GENERATOR); t.putInt("Heat",500); t.putInt("CoreFuelTicks",2);
                advanced.loadState(t); advanced.tick();
                h.assertTrue(advanced.coolingRate()==(module.isEmpty()?4:6) && advanced.energy()==2000 && advanced.heat()==500-advanced.coolingRate()+2 && advanced.coreFuelTicks()==1,"Advanced core produces2000 at two heat with improved passive cooling");
                var light=BestMechs.TYPE.get().create(h.getLevel()); light.loadState(t); light.tick();
                h.assertTrue(light.coreOutput()==1000 && light.energy()==1000 && light.coolingRate()==(module.isEmpty()?2:4),"Initial core and cooling remain unchanged"); light.discard();
            }
            var t=new CompoundTag(); t.putString(BestMechCores.STATE,BestMechCores.GENERATOR); t.putInt("Energy",59_998_001); t.putInt("CoreFuelTicks",1); t.putInt("CoreWorkTicks",999); advanced.loadState(t);
            var fuel=new ItemStack(Items.COAL,2); advanced.cargo().setItem(26,fuel); advanced.tick();
            h.assertTrue(advanced.energy()==59_998_001 && advanced.coreFuelTicks()==1 && advanced.stateTag().getInt("CoreWorkTicks")==999 && advanced.cargo().getItem(26).getCount()==2,"1999 headroom pauses paid fuel, cargo and core wear");
            set(advanced,"Energy",59_998_000); advanced.tick();
            h.assertTrue(advanced.energy()==60_000_000 && advanced.coreFuelTicks()==0 && advanced.wear()==1 && advanced.stateTag().getInt("CoreWorkTicks")==0 && advanced.cargo().getItem(26).getCount()==2,"Exactly2000 headroom spends last paid tick and rolls independent core wear once");
            set(advanced,"Energy",59_998_001); advanced.tick(); h.assertTrue(advanced.cargo().getItem(26).getCount()==2 && advanced.coreFuelTicks()==0,"Insufficient headroom never buys a new fuel item");
            set(advanced,"Energy",59_998_000); advanced.tick(); int burn=new ItemStack(Items.COAL).getBurnTime(RecipeType.SMELTING);
            h.assertTrue(advanced.cargo().getItem(26).getCount()==1 && advanced.energy()==60_000_000 && advanced.coreFuelTicks()==burn-1,"Last heavy cargo cell buys the unchanged fuel duration and immediately produces2000");
            for(String gate:List.of("Isolated","ThermalLock","Wear")) {
                t=new CompoundTag(); t.putString(BestMechCores.STATE,BestMechCores.GENERATOR); t.putInt("CoreFuelTicks",2); t.putInt("Heat",500);
                if(gate.equals("Wear")) t.putInt(gate,BestMechEntity.MAX_WEAR); else t.putBoolean(gate,true); advanced.loadState(t); advanced.tick();
                h.assertTrue(advanced.energy()==0 && advanced.coreFuelTicks()==2,"Core preserves paid fuel while blocked by "+gate);
            }
            t=new CompoundTag(); t.putInt("Heat",405); t.putBoolean("ThermalLock",true); advanced.loadState(t); advanced.tick();
            h.assertTrue(advanced.thermalLocked() && advanced.heat()==401,"Advanced latch remains above recovery threshold"); advanced.tick();
            h.assertTrue(!advanced.thermalLocked() && advanced.heat()==397,"Advanced cooling recovers through400");
            t=new CompoundTag(); t.putInt("Heat",999); t.putInt("Energy",10000); advanced.loadState(t); advanced.moveTo(anchor.x,anchor.y,anchor.z,0,0); advanced.setOnGround(true);
            h.assertTrue(p.startRiding(advanced),"Operator mounts for heat limit"); p.setPlayerInput(0,1,false,false); advanced.tick();
            h.assertTrue(advanced.thermalLocked() && advanced.heat()==1000 && advanced.energy()==8400 && advanced.getDeltaMovement().horizontalDistanceSqr()==0,"Movement clamps heat and latches at1000 with unchanged1600 cost"); p.stopRiding();
            t=new CompoundTag(); t.putInt("Energy",59_999_999); advanced.loadState(t); var port=advanced.energyPort(); var before=advanced.stateTag();
            h.assertTrue(port.receiveEnergy(512,true)==1 && before.equals(advanced.stateTag()) && port.receiveEnergy(512,false)==1 && advanced.energy()==60_000_000,"Advanced entity external charge retains capacity and simulation boundary");
            var packed=CatalogueContent.stack("best_heavy_mech",1); t.putInt("Energy",59_990_000); t.putLong("ChargeTick",Long.MIN_VALUE); BestMechItem.setState(packed,t);
            h.assertTrue(BestMechItem.energy(packed).receiveEnergy(3000,false)==3000 && BestMechItem.energy(packed).receiveEnergy(5000,false)==2000
                    && BestMechItem.energy(packed).receiveEnergy(1,false)==0,"Advanced packed item retains shared5000 external charge budget");
        } finally { clear(p); initial.discard(); advanced.discard(); }
        h.succeed();
    }
}
