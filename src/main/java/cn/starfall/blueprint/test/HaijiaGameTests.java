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
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("starfall_blueprint_haijia")
@PrefixGameTestTemplate(false)
public final class HaijiaGameTests {
    private static ServerPlayer player(GameTestHelper h){var profile=new GameProfile(UUID.randomUUID(),"OrbitTest");var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());var c=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(c);p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),c,p,CommonListenerCookie.createInitial(profile,false)){@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}};p.setGameMode(GameType.SURVIVAL);var tag=new CompoundTag();for(String k:Haijia.KNOWLEDGE)tag.putBoolean(k,true);p.getPersistentData().put("HaijiaKnowledge",tag);return p;}
    private static OrbitalBlockEntity block(GameTestHelper h,BlockPos p,String key){var state=Haijia.BLOCKS.get(key).get().defaultBlockState();var def=OrbitalStructure.of(key);for(int i=0;i<def.parts();i++)h.getLevel().setBlock(p.offset(def.offset(Direction.NORTH,i)),state.setValue(OrbitalBlock.PART,i),3);return (OrbitalBlockEntity)h.getLevel().getBlockEntity(p);}
    private static void fill(OrbitalBlockEntity b,int value){for(int n=value;n>0;){int got=b.energy.receiveEnergy(n,false);if(got==0)break;n-=got;}}
    @GameTest(template="empty",timeoutTicks=100)
    public static void assembly_is_atomic_and_consumes_exact_energy(GameTestHelper h){var p=player(h);var pos=h.absolutePos(new BlockPos(1,2,1));var b=block(h,pos,"assembler");p.setPos(pos.getX(),pos.getY(),pos.getZ());fill(b,400000);var m=new OrbitalMenu(1,p.getInventory(),ContainerLevelAccess.create(h.getLevel(),pos),1);var r=OrbitalMenu.RECIPES.getFirst();for(var e:r.inputs().entrySet())p.getInventory().add(CatalogueContent.stack(e.getKey(),e.getValue()));h.assertTrue(m.clickMenuButton(p,0),"Assembly succeeds");h.assertTrue(b.energy.getEnergyStored()==240000,"Exact 160k energy cost");h.assertTrue(p.getInventory().items.stream().filter(s->s.is(Haijia.item("hj_sat_charge"))).mapToInt(ItemStack::getCount).sum()==1,"One satellite output");h.assertTrue(!m.clickMenuButton(p,0)&&b.energy.getEnergyStored()==240000,"Repeat cannot duplicate or drain");h.succeed();}
    @GameTest(template="empty",timeoutTicks=100)
    public static void full_inventory_failure_changes_nothing(GameTestHelper h){var p=player(h);for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));p.getInventory().setItem(0,Haijia.stack("hj_coil",64));var before=p.getInventory().items.stream().map(ItemStack::copy).toList();h.assertTrue(!OrbitalMenu.transact(p,Map.of("hj_coil",1),Haijia.stack("hj_bus",1)),"No output space refuses");for(int i=0;i<36;i++)h.assertTrue(ItemStack.matches(before.get(i),p.getInventory().getItem(i)),"No partial material deduction");h.succeed();}
    @GameTest(template="empty",timeoutTicks=100)
    public static void relay_links_conserve_energy_and_survive_save(GameTestHelper h){var pos=h.absolutePos(new BlockPos(1,2,1));var a=block(h,pos,"relay");var b=block(h,pos.offset(3,0,0),"relay");fill(a,100000);h.assertTrue(a.link(b)&&a.links.contains(b.getBlockPos())&&b.links.contains(pos),"Reciprocal link");h.assertTrue(!a.link(a),"No self links");OrbitalBlockEntity.tick(h.getLevel(),pos,a.getBlockState(),a);h.assertTrue(a.energy.getEnergyStored()+b.energy.getEnergyStored()==100000&&b.energy.getEnergyStored()==4096,"Transfer conserves exact balance");var saved=a.saveWithoutMetadata(h.getLevel().registryAccess());var restored=new OrbitalBlockEntity(pos,a.getBlockState());restored.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(restored.links.equals(a.links)&&restored.energy.getEnergyStored()==a.energy.getEnergyStored(),"Energy and edges persist");h.succeed();}
    @GameTest(template="empty",timeoutTicks=100)
    public static void fleet_upgrade_disable_and_persistence(GameTestHelper h){var p=player(h);var data=new OrbitalFleet();var f=data.fleet(p);f.count[0]=2;f.count[2]=1;f.rods=8;f.energy=100;f.lastTick=10;f.advance(30);h.assertTrue(f.energy==17700,"Two solar satellites and one payload produce exact energy");f.active[0]=false;f.advance(50);h.assertTrue(f.energy==19300,"Disabled solar stops generation");var saved=data.save(new CompoundTag(),h.getLevel().registryAccess());var loaded=OrbitalFleet.load(saved,h.getLevel().registryAccess()).save(new CompoundTag(),h.getLevel().registryAccess());h.assertTrue(saved.equals(loaded),"Ownership levels energy ammo active flags and clocks round-trip");h.succeed();}
    @GameTest(template="empty",timeoutTicks=100)
    public static void launch_consumes_vehicle_and_payload_once(GameTestHelper h){var p=player(h);var pos=h.absolutePos(new BlockPos(2,3,2));var b=block(h,pos,"launchpad");for(int y=pos.getY()+4;y<h.getLevel().getMaxBuildHeight();y++)h.getLevel().setBlock(new BlockPos(pos.getX()+2,y,pos.getZ()+2),Blocks.AIR.defaultBlockState(),3);p.setPos(pos.getX(),pos.getY(),pos.getZ());fill(b,400000);p.getInventory().add(Haijia.stack("hj_sat_kinetic",1));p.getInventory().add(Haijia.stack("hj_launch_vehicle",1));var m=new OrbitalMenu(1,p.getInventory(),ContainerLevelAccess.create(h.getLevel(),pos),2);h.assertTrue(m.clickMenuButton(p,102),"Launch succeeds");var f=OrbitalFleet.get(h.getLevel()).fleet(p);h.assertTrue(f.count[2]==1&&f.rods==8&&b.energy.getEnergyStored()==200000,"One level, eight physical rods, exact power");h.assertTrue(!m.clickMenuButton(p,102)&&f.count[2]==1,"Replay cannot duplicate satellites");h.succeed();}
    @GameTest(template="empty",timeoutTicks=100)
    public static void fighter_save_and_inputs_are_server_validated(GameTestHelper h){var f=Haijia.FIGHTER.get().create(h.getLevel());var p=player(h);var tag=new CompoundTag();tag.putInt("FlightEnergy",100000);tag.putInt("Missiles",7);tag.putFloat("Hull",90);tag.putInt("MissileCooldown",12);f.unpack(tag);h.assertTrue(f.pack().getInt("Missiles")==7&&f.pack().getFloat("Hull")==90&&f.pack().getInt("MissileCooldown")==8,"Pack preserves damage ammo and cooldown");f.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,8,2)));h.getLevel().addFreshEntity(f);f.controls(p,1);f.tick();h.assertTrue(f.throttle()==0,"Non-rider input rejected");p.startRiding(f);f.controls(p,5);f.tick();h.assertTrue(f.throttle()>0&&f.getXRot()<0&&f.energy()<100000,"Rider accelerates, pitches up and pays energy");h.succeed();}
    @GameTest(template="empty",timeoutTicks=100)
    public static void public_recipes_have_real_items_and_match_runtime(GameTestHelper h){for(String id:List.of("hj_relay","hj_processor","hj_solar_wing","hj_tungsten_rod","hj_launch_vehicle","hj_terminal")){var recipe=h.getLevel().getRecipeManager().byKey(StarfallBlueprint.id(id));h.assertTrue(recipe.isPresent(),"Production recipe exists: "+id);for(var ingredient:recipe.orElseThrow().value().getIngredients())h.assertTrue(!ingredient.isEmpty()&&ingredient.getItems().length>0,"Ingredient resolves: "+id);}for(String key:Haijia.KNOWLEDGE)h.assertTrue(Progression.NODES.stream().anyMatch(n->n.id().equals("haijia:"+key)),"Research registered: "+key);h.succeed();}

    @GameTest(template="empty",timeoutTicks=100)
    public static void terminal_charges_multiple_devices_and_spends_shared_budget(GameTestHelper h){
        var p=player(h);p.tickCount=20;p.getInventory().setItem(0,Haijia.stack("hj_terminal",1));p.getInventory().setItem(1,Haijia.stack("hj_fighter",1));p.getInventory().setItem(2,CatalogueContent.stack("best_frame",1));
        var fleet=OrbitalFleet.get(h.getLevel()).fleet(p);fleet.count[0]=2;fleet.energy=10000;
        var frame=p.getInventory().getItem(2).getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);var plane=p.getInventory().getItem(1).getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        for(int tick=0;tick<20;tick++){p.tickCount=tick;OrbitalFleet.playerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));}
        h.assertTrue(frame.getEnergyStored()>0&&plane.getEnergyStored()>0,"Both carried devices receive energy");h.assertTrue(fleet.energy<10000&&fleet.energy>=2000,"Shared charging budget is deducted once");
        long before=fleet.energy;fleet.active[0]=false;OrbitalFleet.playerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));h.assertTrue(fleet.energy==before,"Disabled satellite supplies no charge");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void weather_uses_energy_and_shared_cooldown(GameTestHelper h){
        var p=player(h);var f=OrbitalFleet.get(h.getLevel()).fleet(p);f.count[3]=1;f.energy=800000;
        h.assertTrue(OrbitalTerminalItem.weather(p,0),"Weather control succeeds");h.assertTrue(f.energy==400000&&f.weatherReady==h.getLevel().getGameTime()+2400,"Exact cost and cooldown");
        h.assertTrue(!OrbitalTerminalItem.weather(p,2)&&f.energy==400000,"Cooldown blocks thunder and repeated costs");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void creative_terminal_provides_services_without_changing_real_fleet(GameTestHelper h){
        var p=player(h);p.setGameMode(GameType.CREATIVE);p.getInventory().setItem(0,Haijia.stack("hj_creative_terminal",1));
        var data=OrbitalFleet.get(h.getLevel());var normal=data.fleet(p);normal.count[0]=2;normal.energy=1234;
        var before=data.save(new CompoundTag(),h.getLevel().registryAccess());var virtual=data.serviceFleet(p);
        h.assertTrue(virtual!=normal&&java.util.Arrays.stream(virtual.count).allMatch(n->n>0),"All four creative satellite services available without launch");
        h.assertTrue(virtual.energy==virtual.capacity()&&virtual.rods==256,"Creative service starts fully supplied");
        h.assertTrue(OrbitalTerminalItem.weather(p,0),"Creative terminal changes weather immediately");
        h.assertTrue(data.save(new CompoundTag(),h.getLevel().registryAccess()).equals(before),"Creative service never modifies saved survival fleet");
        p.setGameMode(GameType.SURVIVAL);h.assertTrue(!OrbitalFleet.hasTerminal(p)&&!OrbitalTerminalItem.creativeAccess(p),"Creative terminal disabled after switching to survival");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void creative_aircraft_keeps_resources_and_requires_creative_pilot(GameTestHelper h){
        var p=player(h);p.setGameMode(GameType.CREATIVE);var packed=Haijia.stack("hj_creative_fighter",1);PeregrineItem.prepareCreative(packed);
        var f=Haijia.FIGHTER.get().create(h.getLevel());f.unpack(CatalogueEquipment.getData(packed));f.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,8,2)));h.getLevel().addFreshEntity(f);
        h.assertTrue(f.energy()==2000000&&f.ammo()==16&&f.pack().getBoolean("CreativeAircraft"),"Creative aircraft deploys full with persistent identity");
        h.assertTrue(p.startRiding(f),"Creative pilot can board");f.controls(p,1|256|512);f.tick();
        h.assertTrue(f.energy()==2000000&&f.ammo()==16&&f.throttle()>0,"Flight, laser and missile do not consume creative resources");
        p.stopRiding();p.setGameMode(GameType.SURVIVAL);h.assertTrue(!p.startRiding(f),"Survival pilot cannot board creative aircraft");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void enlarged_station_ports_and_teardown_use_one_controller(GameTestHelper h){
        var root=h.absolutePos(new BlockPos(1,2,1));var controller=block(h,root,"assembler");var def=OrbitalStructure.of("assembler");var last=root.offset(def.offset(Direction.NORTH,def.parts()-1));
        h.assertTrue(def.parts()==27&&OrbitalBlock.complete(controller),"Assembler occupies a complete 3x3x3 structure");
        var port=h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,last,null);h.assertTrue(port!=null&&port.receiveEnergy(5000,false)==5000&&controller.energy.getEnergyStored()==5000,"Any shell port resolves to the same energy buffer");
        h.assertTrue(OrbitalBlock.controller(h.getLevel(),last)==controller,"Shell interaction resolves to the controller");
        h.getLevel().destroyBlock(last,true);for(int i=0;i<def.parts();i++)h.assertTrue(h.getLevel().getBlockState(root.offset(def.offset(Direction.NORTH,i))).isAir(),"Breaking one part removes all shells");
        var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(root).inflate(5));h.assertTrue(drops.stream().filter(e->e.getItem().is(Haijia.item("hj_assembler"))).mapToInt(e->e.getItem().getCount()).sum()==1,"Whole structure drops exactly one item");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void orbital_mark_preserves_indoor_target_height(GameTestHelper h){
        var p=player(h);var root=h.absolutePos(new BlockPos(1,4,1));p.setPos(root.getX()+.5,root.getY(),root.getZ()+.5);p.setYRot(0);p.setXRot(0);
        var target=root.offset(0,1,5);h.getLevel().setBlock(target,Blocks.STONE.defaultBlockState(),3);h.getLevel().setBlock(target.above(3),Blocks.STONE.defaultBlockState(),3);
        var fleet=OrbitalFleet.get(h.getLevel()).fleet(p);fleet.count[1]=1;fleet.energy=500000;
        h.runAfterDelay(5,()->{h.assertTrue(!h.getLevel().canSeeSky(target),"Target is indoors");h.assertTrue(OrbitalTerminalItem.strike(p,0),"Indoor target may be marked");
        var strikes=h.getLevel().getEntitiesOfClass(OrbitalStrike.class,new net.minecraft.world.phys.AABB(root).inflate(12));h.assertTrue(strikes.stream().anyMatch(e->e.style()==0&&Math.abs(e.getY()-p.getEyeY())<.05),"Target Y stays at the ray hit, never moved onto the roof");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void kinetic_resupply_requires_launch_even_at_satellite_limit(GameTestHelper h){
        var p=player(h);var root=h.absolutePos(new BlockPos(1,3,1));var b=block(h,root,"launchpad");p.setPos(root.getX(),root.getY(),root.getZ());fill(b,400000);
        for(int y=root.getY()+4;y<h.getLevel().getMaxBuildHeight();y++)h.getLevel().setBlock(new BlockPos(root.getX()+2,y,root.getZ()+2),Blocks.AIR.defaultBlockState(),3);
        var fleet=OrbitalFleet.get(h.getLevel()).fleet(p);fleet.count[2]=16;fleet.rods=0;
        p.getInventory().setItem(0,Haijia.stack("hj_terminal",1));p.getInventory().add(Haijia.stack("hj_tungsten_rod",1));p.getInventory().add(Haijia.stack("hj_launch_vehicle",1));
        var remote=new OrbitalMenu(1,p.getInventory());h.assertTrue(!remote.clickMenuButton(p,130)&&fleet.rods==0,"Legacy single-rod remote resupply is disabled");
        p.getInventory().add(Haijia.stack("hj_sat_kinetic",1));var launch=new OrbitalMenu(2,p.getInventory(),ContainerLevelAccess.create(h.getLevel(),root),2);
        h.assertTrue(launch.clickMenuButton(p,102)&&fleet.rods==8&&fleet.count[2]==16,"Launching a complete kinetic satellite supplies eight rods at the satellite cap");
        h.assertTrue(b.energy.getEnergyStored()==200000,"Resupply launch pays normal energy");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void plane_left_and_right_turns_are_symmetric_and_responsive(GameTestHelper h){
        var p=player(h);p.setGameMode(GameType.CREATIVE);var packed=Haijia.stack("hj_creative_fighter",1);PeregrineItem.prepareCreative(packed);
        var f=Haijia.FIGHTER.get().create(h.getLevel());f.unpack(CatalogueEquipment.getData(packed));f.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,12,2)));h.getLevel().addFreshEntity(f);p.startRiding(f);
        f.setYRot(0);f.controls(p,64);f.tick();float left=f.getYRot();f.setYRot(0);f.controls(p,128);f.tick();float right=f.getYRot();
        h.assertTrue(left<=-3&&right>=3&&Math.abs(left+right)<.001,"Left and right rudder respond immediately with equal sensitivity");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void charging_control_pauses_equipment_but_keeps_solar_generation(GameTestHelper h){
        var p=player(h);p.getInventory().setItem(0,Haijia.stack("hj_terminal",1));var data=OrbitalFleet.get(h.getLevel());var f=data.fleet(p);f.count[0]=1;f.energy=100;var menu=new OrbitalMenu(1,p.getInventory());
        h.assertTrue(menu.clickMenuButton(p,110)&&!f.chargingEnabled&&f.active[0],"Pause controls equipment charging, never shuts down solar generation");long before=f.energy;f.advance(f.lastTick+20);h.assertTrue(f.energy==before+8000,"Solar generation continues while charging is paused");
        var saved=data.save(new CompoundTag(),h.getLevel().registryAccess());var loaded=OrbitalFleet.load(saved,h.getLevel().registryAccess()).save(new CompoundTag(),h.getLevel().registryAccess());h.assertTrue(saved.equals(loaded),"Charging preference survives saves");
        h.assertTrue(menu.clickMenuButton(p,110)&&f.chargingEnabled,"Charging may be explicitly resumed");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void structure_placement_refuses_obstructions_without_overwriting(GameTestHelper h){
        var p=player(h);var root=h.absolutePos(new BlockPos(1,2,1));h.getLevel().setBlock(root.below(),Blocks.STONE.defaultBlockState(),3);var obstruction=root.offset(2,0,2);h.getLevel().setBlock(obstruction,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);p.setPos(root.getX()-3,root.getY(),root.getZ());p.setYRot(180);p.getInventory().setItem(0,Haijia.stack("hj_assembler",1));
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(root.below()).add(0,.5,0),Direction.UP,root.below(),false);
        var context=new net.minecraft.world.item.context.BlockPlaceContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,p.getMainHandItem(),hit);
        h.assertTrue(Haijia.BLOCKS.get("assembler").get().getStateForPlacement(context)==null,"Occupied footprint refuses placement");h.assertTrue(h.getLevel().getBlockState(obstruction).is(Blocks.DIAMOND_BLOCK)&&h.getLevel().getBlockState(root).isAir()&&p.getMainHandItem().getCount()==1,"Existing blocks and item are untouched");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void indoor_orbital_strike_reaches_impact_without_sky_gate(GameTestHelper h){
        var p=player(h);var root=h.absolutePos(new BlockPos(2,5,2));p.setPos(root.getX()+6,root.getY(),root.getZ());h.getLevel().addNewPlayer(p);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)h.getLevel().setBlock(root.offset(x,3,z),Blocks.STONE.defaultBlockState(),3);
        var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setPos(root.getX()+.7,root.getY(),root.getZ()+.5);h.getLevel().addFreshEntity(cow);
        h.runAfterDelay(5,()->{try{h.assertTrue(!h.getLevel().canSeeSky(root),"Impact location is covered by a roof");var strike=Haijia.STRIKE.get().create(h.getLevel());strike.setPos(root.getX()+.5,root.getY()+.8,root.getZ()+.5);strike.configure(0,p.getUUID(),1);strike.tick();h.assertTrue(!cow.isAlive()||cow.getHealth()<20,"Indoor strike performs real impact damage");h.succeed();}finally{p.discard();}});
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void station_blueprints_require_complete_shells_and_one_material(GameTestHelper h){
        var definition=OrbitalStructure.of("assembler");var state=Haijia.BLOCKS.get("assembler").get().defaultBlockState();var cells=new ArrayList<ConstructionPlan.Cell>();
        for(int i=0;i<definition.parts();i++)cells.add(new ConstructionPlan.Cell(definition.offset(Direction.NORTH,i),state.setValue(OrbitalBlock.PART,i),"",false));
        var plan=new ConstructionPlan("Station",cells);h.assertTrue(plan.materials().get(Haijia.item("hj_assembler"))==1,"Complete structure costs one machine, not twenty-seven");
        var rotated=new ArrayList<ConstructionPlan.Cell>();for(var cell:cells)rotated.add(new ConstructionPlan.Cell(cell.pos().rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90),cell.state().rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90),"",false));new ConstructionPlan("Rotated",rotated);
        boolean refused=false;try{new ConstructionPlan("Partial",cells.subList(0,3));}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused,"Partial structures are rejected");h.succeed();
    }

    private static PeregrineEntity armedPlane(GameTestHelper h,ServerPlayer p,net.minecraft.world.phys.Vec3 at){var f=Haijia.FIGHTER.get().create(h.getLevel());var t=new CompoundTag();t.putInt("FlightEnergy",1000000);t.putInt("Missiles",8);t.putInt("Bombs",4);f.unpack(t);f.setPos(at);f.setYRot(0);h.getLevel().addFreshEntity(f);p.setPos(at.add(0,.3,0));p.setYRot(0);p.setXRot(0);p.startRiding(f);return f;}
    @GameTest(template="empty",timeoutTicks=100)
    public static void wide_laser_hits_off_axis_target(GameTestHelper h){var p=player(h);var at=h.absoluteVec(new net.minecraft.world.phys.Vec3(2,14,2));var f=armedPlane(h,p,at);var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setPos(at.add(1.35,0,9));h.getLevel().addFreshEntity(cow);f.controls(p,PeregrineEntity.FIRE);f.tick();h.assertTrue(!cow.isAlive()||cow.getHealth()<20,"Wide beam hits target outside former narrow ray");h.assertTrue(f.energy()<1000000,"Laser pays real energy");f.discard();h.succeed();}
    @GameTest(template="empty",timeoutTicks=100)
    public static void firing_auto_targets_and_guides_missile_with_short_cooldown(GameTestHelper h){
        var p=player(h);var at=h.absoluteVec(new net.minecraft.world.phys.Vec3(2,16,2));var f=armedPlane(h,p,at);f.selectWeapon(p,1);var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setPos(at.add(0,0,20));h.getLevel().addFreshEntity(cow);
        f.controls(p,PeregrineEntity.LOCK);f.tick();h.assertTrue(f.lockedTarget()==null,"Legacy right mouse packet cannot acquire a target");
        f.controls(p,PeregrineEntity.FIRE);f.tick();h.assertTrue(f.lockedTarget()==cow&&f.ammo()==7,"First firing tick immediately guides missile without right mouse");
        var missile=h.getLevel().getEntitiesOfClass(OrbitalStrike.class,f.getBoundingBox().inflate(8),e->e.style()==2&&cow.getUUID().equals(e.guidanceTarget())).stream().findFirst().orElseThrow();
        cow.setPos(cow.position().add(5,0,0));missile.tick();h.assertTrue(missile.getDeltaMovement().x>.05,"Missile follows a moving locked target");
        for(int i=0;i<7;i++){f.controls(p,PeregrineEntity.FIRE);f.tick();}h.assertTrue(f.ammo()==7,"Shared missile cooldown blocks early repeat");f.controls(p,PeregrineEntity.FIRE);f.tick();h.assertTrue(f.ammo()==6,"Missile can fire again after 8 ticks");
        f.controls(p,0);f.tick();h.assertTrue(f.lockProgress()==0,"Releasing fire clears selection");f.discard();missile.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void bank_is_symmetric_and_returns_to_level(GameTestHelper h){
        float left=0,right=0;for(int i=0;i<20;i++){left=PeregrineEntity.nextBank(left,16,false);right=PeregrineEntity.nextBank(right,32,false);}
        h.assertTrue(left==-right&&right>0,"Left and right banking are symmetric");
        for(int i=0;i<30;i++){left=PeregrineEntity.nextBank(left,0,false);right=PeregrineEntity.nextBank(right,0,false);}
        h.assertTrue(left==0&&right==0,"Releasing either bank restores level wings");
        h.assertTrue(PeregrineEntity.nextBank(40,48,false)<40,"Opposing roll inputs cancel");
        var beam=BlackDogWeapons.BEAM.get().create(h.getLevel());beam.configure(new net.minecraft.world.phys.Vec3(0,0,160),5);
        h.assertTrue(beam.shouldRenderAtSqrDistance(160*160),"Aircraft laser remains visible beyond chase-camera distance");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void bombs_fall_and_destroy_terrain(GameTestHelper h){
        var p=player(h);p.setGameMode(GameType.CREATIVE);h.getLevel().addNewPlayer(p);var base=h.absolutePos(new BlockPos(3,5,3));for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){h.getLevel().setBlock(base.offset(x,0,z),Blocks.DIRT.defaultBlockState(),3);for(int y=1;y<=10;y++)h.getLevel().setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        var f=armedPlane(h,p,net.minecraft.world.phys.Vec3.atCenterOf(base).add(0,8,0));f.selectWeapon(p,2);f.controls(p,PeregrineEntity.FIRE);f.tick();
        var bomb=h.getLevel().getEntitiesOfClass(OrbitalStrike.class,f.getBoundingBox().inflate(3),e->e.style()==4).stream().findFirst().orElseThrow();double height=bomb.getY();h.assertTrue(f.bombs()==3&&f.pack().getInt("BombCooldown")==12,"Dropping a physical bomb pays ammo and starts cooldown");
        int[] blastBlocks={-1};java.util.function.Consumer<net.neoforged.neoforge.event.level.ExplosionEvent.Detonate> watch=e->{if(e.getLevel()==h.getLevel())blastBlocks[0]=e.getAffectedBlocks().size();};net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(watch);try{for(int i=0;i<60&&!bomb.isRemoved();i++)bomb.tick();}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(watch);}int left=0;for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(h.getLevel().getBlockState(base.offset(x,0,z)).is(Blocks.DIRT))left++;
        h.assertTrue(bomb.getY()<height&&bomb.isRemoved()&&left<9,"Gravity bomb terrain: y="+bomb.getY()+", start="+height+", removed="+bomb.isRemoved()+", dirt="+left+", owner="+(h.getLevel().getPlayerByUUID(p.getUUID())!=null)+", base="+base+", hit="+h.getLevel().getBlockState(bomb.blockPosition())+", below="+h.getLevel().getBlockState(bomb.blockPosition().below())+", affected="+blastBlocks[0]);p.stopRiding();p.discard();f.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void runway_rotation_requires_speed_and_ground_distance(GameTestHelper h){
        var p=player(h);p.setGameMode(GameType.CREATIVE);var base=h.absolutePos(new BlockPos(2,28,2));for(int x=-2;x<=2;x++)for(int z=-2;z<40;z++)h.getLevel().setBlock(base.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
        var f=armedPlane(h,p,net.minecraft.world.phys.Vec3.atBottomCenterOf(base));var t=f.pack();t.putBoolean("CreativeAircraft",true);f.unpack(t);f.setOnGround(true);
        for(int i=0;i<10;i++){f.controls(p,1|4|PeregrineEntity.BOOST);f.tick();}h.assertTrue(f.getY()<=base.getY()+.1&&f.onGround(),"Holding pitch and boost cannot take off without a ground roll");
        boolean airborne=false;for(int i=0;i<35;i++){f.controls(p,1|4|PeregrineEntity.BOOST);f.tick();if(f.getY()>base.getY()+.3){airborne=true;break;}}h.assertTrue(airborne,"Sufficient runway speed allows rotation and takeoff");f.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void afterburner_adds_speed_and_costs_extra_energy(GameTestHelper h){
        var normal=net.minecraft.world.phys.Vec3.ZERO;var boost=normal;var forward=new net.minecraft.world.phys.Vec3(0,0,1);for(int i=0;i<80;i++){normal=PeregrineEntity.flightMotion(normal,forward,1,false,true,false,0,0);boost=PeregrineEntity.flightMotion(boost,forward,1,false,true,true,0,0);}h.assertTrue(boost.horizontalDistance()>normal.horizontalDistance()*2,"Held afterburner more than doubles cruise speed");
        var p=player(h);var f=armedPlane(h,p,h.absoluteVec(new net.minecraft.world.phys.Vec3(2,20,2)));f.controls(p,PeregrineEntity.BOOST);f.tick();h.assertTrue(f.boosting()&&f.energy()<=999000,"Survival plane pays extra boost energy");f.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void weapon_selection_and_bomb_state_are_validated_and_saved(GameTestHelper h){var p=player(h);var outsider=player(h);var f=armedPlane(h,p,h.absoluteVec(new net.minecraft.world.phys.Vec3(2,16,2)));h.assertTrue(!f.selectWeapon(outsider,1)&&!f.selectWeapon(p,3),"Only rider may choose a valid weapon");h.assertTrue(f.selectWeapon(p,2),"Rider selects bombs");var restored=Haijia.FIGHTER.get().create(h.getLevel());restored.unpack(f.pack());h.assertTrue(restored.weapon()==2&&restored.bombs()==4,"Weapon and bombs survive packing");f.discard();h.succeed();}
    @GameTest(template="empty",timeoutTicks=100)
    public static void upgrade_satellites_increase_effects_and_consume_launch_resources(GameTestHelper h){
        var p=player(h);var root=h.absolutePos(new BlockPos(1,3,1));var b=block(h,root,"launchpad");p.setPos(root.getX(),root.getY(),root.getZ());fill(b,600000);for(int y=root.getY()+4;y<h.getLevel().getMaxBuildHeight();y++)h.getLevel().setBlock(root.offset(2,y-root.getY(),2),Blocks.AIR.defaultBlockState(),3);
        var f=OrbitalFleet.get(h.getLevel()).fleet(p);f.count[0]=f.count[1]=f.count[2]=1;float damage=f.strikeDamage(1),radius=f.strikeRadius(2);int charge=f.chargePerTick();p.getInventory().add(Haijia.stack("hj_launch_vehicle",3));for(String type:List.of("charge","laser","kinetic"))p.getInventory().add(Haijia.stack("hj_sat_"+type+"_upgrade",1));
        var menu=new OrbitalMenu(1,p.getInventory(),ContainerLevelAccess.create(h.getLevel(),root),2);for(int action=104;action<=106;action++)h.assertTrue(menu.clickMenuButton(p,action),"Upgrade launch succeeds");
        h.assertTrue(f.upgrades[0]==1&&f.upgrades[1]==1&&f.upgrades[2]==1&&f.strikeDamage(1)>damage&&f.strikeRadius(2)>radius&&f.chargePerTick()>charge&&f.rods==8,"All three upgrades improve real effects and kinetic payload brings rods");h.assertTrue(b.energy.getEnergyStored()==0,"Three upgrades pay 600k energy");
        var data=OrbitalFleet.get(h.getLevel());var saved=data.save(new CompoundTag(),h.getLevel().registryAccess());h.assertTrue(saved.equals(OrbitalFleet.load(saved,h.getLevel().registryAccess()).save(new CompoundTag(),h.getLevel().registryAccess())),"Upgrade levels persist");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void creative_terminal_uses_max_strength_and_max_charging(GameTestHelper h){var p=player(h);p.setGameMode(GameType.CREATIVE);p.getInventory().setItem(0,Haijia.stack("hj_creative_terminal",1));var f=OrbitalFleet.get(h.getLevel()).serviceFleet(p);h.assertTrue(f.count[0]==16&&f.upgrades[0]==8&&f.upgrades[1]==8&&f.upgrades[2]==8&&f.chargePerTick()==28800,"Creative terminal has all eight upgrade levels and maximum charge budget");h.assertTrue(f.strikeRadius(1)>11&&f.strikeRadius(2)>=31.5&&f.strikeDamage(1)>=420,"Creative orbital effects use maximum damage and radius");h.succeed();}
    @GameTest(template="empty",timeoutTicks=100)
    public static void orbital_effect_strength_survives_delayed_entity_save(GameTestHelper h){var e=Haijia.STRIKE.get().create(h.getLevel());e.configure(1,UUID.randomUUID(),100);e.strength(1000,30,13);var tag=new CompoundTag();e.saveWithoutId(tag);var restored=Haijia.STRIKE.get().create(h.getLevel());restored.load(tag);h.assertTrue(restored.damage()==1000&&restored.radius()==30&&restored.blast()==13&&restored.age()==-100,"Marked attack keeps its snapshotted upgraded strength across saves");h.succeed();}

    @GameTest(template="empty",timeoutTicks=100)
    public static void upgraded_charging_reaches_equipment_with_real_receiver_limits(GameTestHelper h){
        var p=player(h);p.getInventory().setItem(0,Haijia.stack("hj_terminal",1));var plane=Haijia.stack("hj_fighter",1);p.getInventory().setItem(1,plane);var f=OrbitalFleet.get(h.getLevel()).fleet(p);f.count[0]=1;f.energy=1000000;
        p.tickCount=0;OrbitalFleet.playerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));int basic=PeregrineItem.energy(plane).getEnergyStored();f.upgrades[0]=8;p.tickCount=1;OrbitalFleet.playerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));int extra=PeregrineItem.energy(plane).getEnergyStored()-basic;
        h.assertTrue(basic==200&&extra>=basic*8,"Enhancement speeds up actual equipment charging");p.setGameMode(GameType.CREATIVE);p.getInventory().setItem(0,Haijia.stack("hj_creative_terminal",1));int before=PeregrineItem.energy(plane).getEnergyStored();p.tickCount=2;OrbitalFleet.playerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));h.assertTrue(PeregrineItem.energy(plane).getEnergyStored()-before==8192,"Creative charging reaches the aircraft receiver's full legal per-tick rate");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void capped_upgrade_launch_preserves_payload_and_energy(GameTestHelper h){
        var p=player(h);var root=h.absolutePos(new BlockPos(1,3,1));var b=block(h,root,"launchpad");p.setPos(root.getX(),root.getY(),root.getZ());fill(b,200000);var f=OrbitalFleet.get(h.getLevel()).fleet(p);f.count[1]=1;f.upgrades[1]=8;
        p.getInventory().setItem(0,Haijia.stack("hj_sat_laser_upgrade",1));p.getInventory().setItem(1,Haijia.stack("hj_launch_vehicle",1));var menu=new OrbitalMenu(1,p.getInventory(),ContainerLevelAccess.create(h.getLevel(),root),2);
        h.assertTrue(!menu.clickMenuButton(p,105)&&b.energy.getEnergyStored()==200000&&p.getInventory().getItem(0).getCount()==1&&p.getInventory().getItem(1).getCount()==1,"Full upgrade rejects the entire launch without losing resources");h.succeed();
    }
}
