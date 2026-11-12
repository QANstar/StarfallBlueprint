package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("starfall_blueprint_mech_three")
@PrefixGameTestTemplate(false)
public final class BestMechThreePlatformGameTests {
    private static net.minecraft.server.level.ServerPlayer pilot(GameTestHelper h) {
        var profile=new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ThreeMechPilot");
        var p=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);return p;
    }
    private static void knowledge(net.minecraft.server.level.ServerPlayer p,String key) {
        var tag=p.getPersistentData().getCompound("BestModuleKnowledge");tag.putBoolean(key,true);p.getPersistentData().put("BestModuleKnowledge",tag);
    }
    private static BestMechEntity vehicle(GameTestHelper h,net.minecraft.world.entity.EntityType<BestMechEntity> type,net.minecraft.server.level.ServerPlayer p) {
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)h.setBlock(new net.minecraft.core.BlockPos(x,0,z),net.minecraft.world.level.block.Blocks.STONE);
        var origin=h.absolutePos(new net.minecraft.core.BlockPos(4,1,2));var m=type.create(h.getLevel());m.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()+.5,0,0);
        var tag=new CompoundTag();tag.putInt("Energy",1_000_000);m.loadState(tag);h.getLevel().addFreshEntity(m);m.setOnGround(true);
        p.moveTo(m.getX(),m.getY(),m.getZ(),0,0);h.assertTrue(p.startRiding(m),"real server player must ride vehicle");
        p.moveTo(m.getX(),m.getY(),m.getZ(),0,0);return m;
    }
    private static <T extends net.minecraft.world.entity.Mob> T target(GameTestHelper h,net.minecraft.world.entity.EntityType<T> type) {
        var mob=type.create(h.getLevel());var pos=h.absolutePos(new net.minecraft.core.BlockPos(4,1,9));mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);mob.setNoAi(true);h.getLevel().addFreshEntity(mob);return mob;
    }
    private static void resetWeapon(BestMechEntity m) {var tag=m.stateTag();tag.putInt("WeaponCooldown",0);m.loadState(tag);}
    @GameTest(template="combat_arena")
    public static void combatRequiresOwnKnowledgeAndPaysForShots(GameTestHelper h) {
        var p=pilot(h);var m=vehicle(h,BestMechs.COMBAT_TYPE.get(),p);var enemy=target(h,net.minecraft.world.entity.EntityType.ZOMBIE);float before=enemy.getHealth();
        h.assertTrue(!m.fireWeapon(p)&&m.energy()==1_000_000,"untrained rider cannot shoot or spend energy");
        knowledge(p,"combat_operation");h.assertTrue(m.qualifiedFor(p)&&!BestMechEntity.qualified(p),"combat knowledge must not require engineering knowledge");
        boolean fired=m.fireWeapon(p);h.assertTrue(fired&&enemy.getHealth()<before,"actual hostile target must take damage; fired="+fired+" eye="+p.getEyePosition()+" look="+p.getLookAngle()+" target="+enemy.getBoundingBox()+" hit="+h.getLevel().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(40)),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p)).getLocation());
        h.assertTrue(m.energy()==988_000&&m.heat()==65&&m.wear()==1,"shot pays exact energy heat and wear");
        float hit=enemy.getHealth();h.assertTrue(!m.fireWeapon(p)&&m.energy()==988_000&&enemy.getHealth()==hit,"cooldown rejects second shot without payment");
        p.stopRiding();resetWeapon(m);h.assertTrue(!m.fireWeapon(p),"unmounted operator cannot fire");
        m.setIsolated(true);m.setOnGround(true);m.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        h.assertTrue(m.pack(p)&&BestMechProgression.COMBAT.equals(BestMechProgression.type(p.getMainHandItem())),"combat-only knowledge permits normal packing without engineering diagnostic prerequisite");
        enemy.discard();h.succeed();
    }
    @GameTest(template="combat_arena")
    public static void combatWallOccludesAndFriendlyTargetsRemainPhysical(GameTestHelper h) {
        var p=pilot(h);knowledge(p,"combat_operation");var m=vehicle(h,BestMechs.COMBAT_TYPE.get(),p);
        var cow=target(h,net.minecraft.world.entity.EntityType.COW);p.setXRot(8);float before=cow.getHealth();
        var wall=new net.minecraft.core.BlockPos(4,2,5);h.setBlock(wall,net.minecraft.world.level.block.Blocks.STONE);
        h.assertTrue(m.fireWeapon(p)&&cow.getHealth()==before,"opaque block must prevent damage behind wall");
        h.assertTrue(m.energy()==988_000,"blocked shot still consumes energy");
        h.setBlock(wall,net.minecraft.world.level.block.Blocks.AIR);resetWeapon(m);
        h.assertTrue(m.fireWeapon(p)&&cow.getHealth()<before,"nonhostile animal is a physical target with real damage");
        p.stopRiding();m.discard();cow.discard();h.succeed();
    }
    @GameTest(template="combat_arena")
    public static void universalCanMineAndFireIndependently(GameTestHelper h) {
        var p=pilot(h);knowledge(p,"confluence");var m=vehicle(h,BestMechs.UNIVERSAL_TYPE.get(),p);
        var t=m.stateTag();t.putString(BestMechArms.STATE,"");m.loadState(t);
        h.assertTrue(m.workArmKey().equals(BestMechArms.DRILL),"empty universal arm retains built in drill");
        var block=new net.minecraft.core.BlockPos(4,2,5);h.setBlock(block,net.minecraft.world.level.block.Blocks.STONE);
        h.assertTrue(m.work(p)&&h.getBlockState(block).isAir(),"universal engineering action must really mine");
        var cow=target(h,net.minecraft.world.entity.EntityType.COW);p.setXRot(8);float before=cow.getHealth();
        h.assertTrue(m.fireWeapon(p)&&cow.getHealth()<before,"separate weapon action remains usable after engineering action");
        h.assertTrue(m.stateTag().getInt("DrillCooldown")>0&&m.stateTag().getInt("WeaponCooldown")>0,"independent work and weapon cooldowns coexist");
        p.stopRiding();m.discard();cow.discard();h.succeed();
    }
    @GameTest(template="combat_arena")
    public static void engineeringExpansionProvidesConsumedDrill(GameTestHelper h) {
        var p=pilot(h);p.getPersistentData().putInt("BestEngineeringResearch",3);
        for(String key:java.util.List.of("BestMechOperation","BestDiagnostics","BestLightMobility"))p.getPersistentData().putBoolean(key,true);
        var m=vehicle(h,BestMechs.TYPE.get(),p);var t=m.stateTag();t.putInt("UpgradeMask",1);t.putString(BestMechArms.STATE,"");m.loadState(t);
        h.assertTrue(m.armKey().isEmpty()&&m.workArmKey().equals(BestMechArms.HEAVY_DRILL),"component provides internal consumed drill without physical arm to duplicate");
        var block=new net.minecraft.core.BlockPos(4,2,5);h.setBlock(block,net.minecraft.world.level.block.Blocks.STONE);
        h.assertTrue(m.work(p)&&h.getBlockState(block).isAir(),"engineering component must perform work without separate arm");
        h.assertTrue(m.armKey().isEmpty(),"internal arm must not turn into removable inventory arm");p.stopRiding();m.discard();h.succeed();
    }
    @GameTest(template="combat_arena")
    public static void reinforcedBodyAndEngineeringComponentSurviveDeployment(GameTestHelper h) {
        var p=pilot(h);p.getPersistentData().putInt("BestEngineeringResearch",3);
        for(String key:java.util.List.of("BestMechOperation","BestDiagnostics","BestLightMobility"))p.getPersistentData().putBoolean(key,true);
        var m=vehicle(h,BestMechs.TYPE.get(),p);var t=m.stateTag();t.putInt("UpgradeMask",1);t.putString(BestMechBodies.STATE,BestMechBodies.REINFORCED);t.putInt("Energy",29_000_000);t.putFloat("Health",150);t.putBoolean("Isolated",true);m.loadState(t);
        h.assertTrue(m.bodyKey().equals(BestMechBodies.REINFORCED)&&m.energy()==29_000_000&&m.getHealth()==150,"engineering component must preserve existing reinforced body balances");
        p.stopRiding();m.setOnGround(true);m.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        h.assertTrue(m.pack(p),"reinforced upgraded engineering mech packs");p.setShiftKeyDown(false);
        var floor=h.absolutePos(new net.minecraft.core.BlockPos(4,0,2));var result=p.getMainHandItem().getItem().useOn(new net.minecraft.world.item.context.UseOnContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(floor).add(0,.5,0),net.minecraft.core.Direction.UP,floor,false)));
        h.assertTrue(result.consumesAction(),"reinforced upgraded engineering mech deploys");
        var live=h.getLevel().getEntitiesOfClass(BestMechEntity.class,new net.minecraft.world.phys.AABB(floor).inflate(5),e->!e.isRemoved());
        h.assertTrue(live.size()==1&&live.getFirst().bodyKey().equals(BestMechBodies.REINFORCED)&&live.getFirst().energy()==29_000_000&&live.getFirst().getHealth()==150&&live.getFirst().upgradeMask()==1,"deployment keeps body, 29M energy, 150 health and component");
        live.forEach(net.minecraft.world.entity.Entity::discard);h.succeed();
    }

    @GameTest(template="combat_arena")
    public static void platformsAndIndependentFinals(GameTestHelper h) {
        var combat=BestMechs.COMBAT_TYPE.get().create(h.getLevel());combat.loadState(new CompoundTag());
        h.assertTrue(combat.combatCapable()&&!combat.engineeringCapable(),"combat capabilities");
        var universal=BestMechs.UNIVERSAL_TYPE.get().create(h.getLevel());universal.loadState(new CompoundTag());
        h.assertTrue(universal.combatCapable()&&universal.engineeringCapable(),"base universal retains both functions");
        var tag=universal.stateTag();float health=universal.getHealth();tag.putInt("UpgradeMask",448);tag.putInt("Energy",10000);tag.putInt("Wear",100);
        universal.loadState(tag);
        h.assertTrue(universal.upgradeMask()==448 && universal.getHealth()==health && universal.energy()==10000 && universal.wear()==100,"all finals coexist without healing");
        h.assertTrue(universal.capacity()==160_000_000 && universal.coolingRate()>combat.coolingRate(),"final endurance is effective");
        var id=java.util.UUID.randomUUID();var source=java.util.UUID.randomUUID();tag=universal.stateTag();tag.putUUID("MechId",id);tag.putUUID("EngineeringSource",source);tag.putUUID("CombatSource",id);tag.putString(BestMechCores.STATE,"");tag.putInt("CoreFuelTicks",178);tag.putInt("CoreWorkTicks",53);universal.loadState(tag);
        var roundtrip=universal.stateTag();
        h.assertTrue(roundtrip.getUUID("MechId").equals(id)&&roundtrip.getUUID("EngineeringSource").equals(source)&&roundtrip.getUUID("CombatSource").equals(id),"fusion identity provenance survives runtime");
        h.assertTrue(roundtrip.getInt("CoreFuelTicks")==178&&roundtrip.getInt("CoreWorkTicks")==53&&universal.coreKey().isEmpty(),"paid fuel survives without installed generator");
        tag.putInt("UpgradeMask",511);combat.loadState(tag);
        h.assertTrue(combat.upgradeMask()==56,"platform rejects foreign component bits");h.succeed();
    }
}
