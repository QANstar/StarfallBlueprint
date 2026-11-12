package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("starfall_blueprint_lingchuan")
@PrefixGameTestTemplate(false)
public final class BlackDogCombatGameTests {
    private static net.minecraft.world.entity.LivingEntity swarmTarget(GameTestHelper h,FakePlayer p,net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity> type){
        var target=type.create(h.getLevel());target.setPos(p.position().add(0,0,8));h.getLevel().addFreshEntity(target);
        var direction=target.getBoundingBox().getCenter().subtract(p.getEyePosition());p.setYRot(0);p.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,direction.horizontalDistance())));return target;
    }
    private static ItemStack swarmTerminal(FakePlayer p){
        var watch=equip(p,"swarm");p.setItemInHand(InteractionHand.MAIN_HAND,CatalogueContent.stack("blackdog_fire_control",1));
        BlackDogArmor.selectSystem(p,BlackDogArmor.SYSTEMS.indexOf("swarm"));return watch;
    }
    private static void holdSwarm(FakePlayer p,int ticks){
        use(p);for(int i=0;i<ticks;i++)p.getMainHandItem().getItem().onUseTick(p.level(),p,p.getMainHandItem(),72000-i);
    }
    private static void releaseSwarm(FakePlayer p){p.getMainHandItem().getItem().releaseUsing(p.getMainHandItem(),p.level(),p,71000);p.stopUsingItem();}
    @GameTest(template="combat",timeoutTicks=100)
    public static void swarmMarkerBelongsToOperatorAndClearsWithLock(GameTestHelper h){
        var p=player(h);var watch=swarmTerminal(p);var observer=player(h);var target=swarmTarget(h,p,net.minecraft.world.entity.EntityType.COW);
        holdSwarm(p,7);var marker=BlackDogMissileLock.targetLock(p);
        h.assertTrue(marker.entityId()==target.getId()&&marker.target().equals(target.getUUID())&&marker.ticks()==7,"Marker identifies the server-selected target and partial lock progress");
        h.assertTrue(BlackDogMissileLock.targetLock(observer).entityId()==-1,"Another player has no target marker for the operator's lock");
        p.setYRot(90);BlackDogMissileLock.hold(p);h.assertTrue(BlackDogMissileLock.targetLock(p).entityId()==-1,"Lost aim removes the old marker immediately");
        p.setYRot(0);for(int i=0;i<16;i++)BlackDogMissileLock.hold(p);
        h.assertTrue(BlackDogMissileLock.targetLock(p).ticks()==16,"Completed lock marks the same target as ready");
        releaseSwarm(p);h.assertTrue(BlackDogMissileLock.targetLock(p).entityId()==-1,"Releasing the terminal removes the marker");
        holdSwarm(p,5);BlackDogArmor.recall(p,watch);h.assertTrue(BlackDogMissileLock.targetLock(p).entityId()==-1,"Recalling the suit clears private targeting feedback");
        target.discard();h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=100)
    public static void swarmLocksFriendlyNeutralAndHostileTargetsAndSpacesEveryMissile(GameTestHelper h){
        var p=player(h);var watch=swarmTerminal(p);
        for(var type:List.of(net.minecraft.world.entity.EntityType.WOLF,net.minecraft.world.entity.EntityType.COW,net.minecraft.world.entity.EntityType.ZOMBIE)){
            var target=swarmTarget(h,p,type);if(target instanceof net.minecraft.world.entity.animal.Wolf wolf)wolf.tame(p);
            int before=BlackDogArmor.charge(watch);holdSwarm(p,BlackDogMissileLock.LOCK_TICKS);releaseSwarm(p);
            h.assertTrue(BlackDogArmor.charge(watch)==before-BlackDogMissileLock.COST,"Every living faction, including the owner's tamed wolf, can be locked");
            var missiles=new ArrayList<BlackDogMissile>();
            for(int tick=0;tick<(BlackDogMissileLock.SALVO_SIZE-1)*BlackDogMissileLock.INTERVAL+1;tick++){
                BlackDogMissileLock.tick(p);
                var current=h.getLevel().getEntitiesOfClass(BlackDogMissile.class,p.getBoundingBox().inflate(4),m->m.getOwner()==p);
                h.assertTrue(current.size()==tick/BlackDogMissileLock.INTERVAL+1,"Exactly one missile launches per interval, never the entire salvo at once");missiles=new ArrayList<>(current);
            }
            h.assertTrue(missiles.size()==16,"A completed lock launches the full sixteen-missile salvo");
            var missile=missiles.getFirst();target.setPos(target.position().add(5,0,0));
            double beforeAim=missile.getDeltaMovement().normalize().dot(target.getBoundingBox().getCenter().subtract(missile.position()).normalize());
            missile.tick();double afterAim=missile.getDeltaMovement().normalize().dot(target.getBoundingBox().getCenter().subtract(missile.position()).normalize());
            h.assertTrue(afterAim>beforeAim,"Guided missile turns toward a moving target");
            missiles.forEach(BlackDogMissile::discard);target.discard();
        }
        BlackDogArmor.recall(p,watch);h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=100)
    public static void swarmRejectsEarlyReleaseLostAimBlockedSightAndDeadTargets(GameTestHelper h){
        var p=player(h);var watch=swarmTerminal(p);var target=swarmTarget(h,p,net.minecraft.world.entity.EntityType.COW);int energy=BlackDogArmor.charge(watch);
        holdSwarm(p,15);releaseSwarm(p);h.assertTrue(BlackDogArmor.charge(watch)==energy,"Releasing before sixteen ticks spends no power");
        holdSwarm(p,16);p.setYRot(90);BlackDogMissileLock.hold(p);p.setYRot(0);BlackDogMissileLock.hold(p);releaseSwarm(p);
        h.assertTrue(BlackDogArmor.charge(watch)==energy,"Looking away cancels the old lock and reacquiring restarts progress");
        holdSwarm(p,16);p.setYRot(90);releaseSwarm(p);p.setYRot(0);h.assertTrue(BlackDogArmor.charge(watch)==energy,"Release revalidates aim even before the next hold tick");
        holdSwarm(p,16);var wall=BlockPos.containing(p.getEyePosition().add(p.getLookAngle().scale(4)));h.getLevel().setBlock(wall,Blocks.STONE.defaultBlockState(),3);h.getLevel().setBlock(wall.above(),Blocks.STONE.defaultBlockState(),3);releaseSwarm(p);
        h.assertTrue(BlackDogArmor.charge(watch)==energy,"A wall blocks lock release");h.getLevel().setBlock(wall,Blocks.AIR.defaultBlockState(),3);h.getLevel().setBlock(wall.above(),Blocks.AIR.defaultBlockState(),3);
        holdSwarm(p,16);target.discard();releaseSwarm(p);h.assertTrue(BlackDogArmor.charge(watch)==energy,"Removed targets cannot consume energy or start a salvo");
        target=swarmTarget(h,p,net.minecraft.world.entity.EntityType.COW);holdSwarm(p,16);releaseSwarm(p);BlackDogArmor.recall(p,watch);BlackDogMissileLock.tick(p);
        h.assertTrue(h.getLevel().getEntitiesOfClass(BlackDogMissile.class,p.getBoundingBox().inflate(4),m->m.getOwner()==p).isEmpty(),"Recalling the suit cancels queued launches");target.discard();h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=100)
    public static void landingStopsFlightAndRejectsStaleHeartbeat(GameTestHelper h){
        var p=player(h);var watch=equip(p,"flight");p.setOnGround(true);BlackDogArmor.flightRequest(p,true,false,true);BlackDogArmor.tickPlayer(p);
        h.assertTrue(p.getAbilities().flying,"A deliberate takeoff has a short ground grace period");
        p.setOnGround(false);BlackDogArmor.tickPlayer(p);p.setOnGround(true);BlackDogArmor.tickPlayer(p);
        h.assertTrue(!p.getAbilities().flying&&!p.isFallFlying()&&!CatalogueEquipment.getData(watch).getBoolean("Hovering"),"Physical landing stops hover and its flight posture");
        int energy=BlackDogArmor.charge(watch);BlackDogArmor.flightRequest(p,true,false,false);BlackDogArmor.tickPlayer(p);
        h.assertTrue(!p.getAbilities().flying&&BlackDogArmor.charge(watch)==energy,"A delayed flying heartbeat cannot relaunch or spend flight power");
        BlackDogArmor.flightRequest(p,true,false,true);h.assertTrue(p.getAbilities().flying,"A new deliberate double-tap can take off again");BlackDogArmor.recall(p,watch);h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=100)
    public static void lowPowerRecallsCreativeFlightAndEveryVisibleSystem(GameTestHelper h){
        var p=player(h);p.setGameMode(GameType.CREATIVE);var watch=equip(p,"flight","turret","shield","cloak");
        var d=CatalogueEquipment.getData(watch);d.putBoolean("Enabled_turret",true);d.putBoolean("Enabled_cloak",true);CatalogueEquipment.setData(watch,d);
        BlackDogArmor.flightRequest(p,true,true,true);p.setOnGround(false);BlackDogArmor.tickPlayer(p);
        var drones=h.getLevel().getEntitiesOfClass(BlackDogDrone.class,p.getBoundingBox().inflate(4),e->e.ownerId()==p.getId());
        BlackDogArmor.setCharge(watch,50);BlackDogArmor.tickPlayer(p);
        h.assertTrue(!BlackDogArmor.deployed(watch)&&!p.getAbilities().flying&&!p.isFallFlying()&&p.getPose()==Pose.STANDING,"Power failure recalls even creative-mode suit flight and the horizontal model");
        for(var slot:List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET))h.assertTrue(p.getItemBySlot(slot).isEmpty(),"All worn projection models are removed");
        h.assertTrue(drones.size()==2&&drones.stream().allMatch(e->e.isRemoved())&&!BlackDogArmor.enabled(watch,"turret")&&!BlackDogArmor.enabled(watch,"shield")&&!BlackDogArmor.enabled(watch,"cloak"),"Drones and every active toggle turn off together");
        BlackDogArmor.regenerate(p,watch);h.assertTrue(BlackDogArmor.charge(watch)>50&&!BlackDogArmor.deployed(watch),"Passive charging never reopens the armour or flight");h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=100)
    public static void drainedWatchCannotDeployUntilItHasOperatingReserve(GameTestHelper h){
        var p=player(h);var watch=equip(p,"flight");BlackDogArmor.recall(p,watch);BlackDogArmor.setCharge(watch,50);
        h.assertTrue(!BlackDogArmor.toggle(p,watch)&&!BlackDogArmor.deployed(watch),"A low-power watch refuses deployment without creating projections");
        BlackDogArmor.setCharge(watch,1000);h.assertTrue(BlackDogArmor.toggle(p,watch),"Recharged watch can be deliberately deployed again");BlackDogArmor.recall(p,watch);h.succeed();
    }
    private static FakePlayer player(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"BlackDogCombat"));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getInventory().selected=0;p.setPos(h.absolutePos(new BlockPos(8,5,8)).getCenter());p.setYRot(0);p.setXRot(0);return p;
    }
    private static ItemStack equip(FakePlayer p,String... abilities){
        var bases=new ArrayList<ItemStack>();for(int i=0;i<8;i++)bases.add(CatalogueContent.stack(Lingchuan.baseKey(i,3),1));
        var watch=BlackDogArmor.build(bases,Arrays.stream(abilities).map(a->CatalogueContent.stack("blackdog_component_"+a,1)).toList());BlackDogArmor.setCharge(watch,1_000_000);p.getInventory().setItem(1,watch);BlackDogArmor.toggle(p,watch);return watch;
    }
    private static void use(FakePlayer p){p.getCooldowns().removeCooldown(p.getMainHandItem().getItem());p.getMainHandItem().getItem().use(p.level(),p,InteractionHand.MAIN_HAND);}
    @GameTest(template="combat",timeoutTicks=100)
    public static void nightVisionNeverEntersVanillaFadeWindow(GameTestHelper h){
        var p=player(h);var watch=equip(p,"night");
        for(int tick=0;tick<1200;tick++){
            p.tickCount=tick;BlackDogArmor.tickPlayer(p);var effect=p.getEffect(MobEffects.NIGHT_VISION);
            h.assertTrue(effect!=null&&effect.getDuration()>200,"Powered night vision remains above the 200-tick flashing threshold for a full simulated minute");
            effect.tick(p,()->{});
        }
        BlackDogArmor.setCharge(watch,0);p.tickCount=1200;BlackDogArmor.tickPlayer(p);h.assertTrue(!p.hasEffect(MobEffects.NIGHT_VISION),"Depletion removes only the suit effect");h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=100)
    public static void recallPreservesStrongerExternalNightVision(GameTestHelper h){
        var p=player(h);var watch=equip(p,"night");p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,2400,1));p.tickCount=20;BlackDogArmor.tickPlayer(p);BlackDogArmor.recall(p,watch);
        h.assertTrue(p.getEffect(MobEffects.NIGHT_VISION)!=null&&p.getEffect(MobEffects.NIGHT_VISION).getAmplifier()==1,"A stronger external effect survives armour removal");h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=100)
    public static void terminalControlsPhysicalDronesAndDoesNotSpawnDuplicates(GameTestHelper h){
        var p=player(h);var watch=equip(p,"laser","turret");
        h.assertTrue(!BlackDogArmor.selectSystem(p,3),"Selection packets without a terminal are rejected");
        p.setItemInHand(InteractionHand.MAIN_HAND,CatalogueContent.stack("blackdog_fire_control",1));h.assertTrue(BlackDogArmor.selectSystem(p,3),"Installed drone mode can be selected");use(p);BlackDogArmor.tickPlayer(p);BlackDogArmor.tickPlayer(p);
        var drones=h.getLevel().getEntitiesOfClass(BlackDogDrone.class,p.getBoundingBox().inflate(4),d->d.ownerId()==p.getId());
        h.assertTrue(BlackDogArmor.enabled(watch,"turret")&&drones.size()==2,"Enabling turret creates exactly two owned physical gun bodies");
        h.assertTrue(!BlackDogArmor.canRegenerate(p,watch),"An armed automated weapon suspends charging");
        use(p);BlackDogArmor.tickPlayer(p);h.assertTrue(!BlackDogArmor.enabled(watch,"turret")&&drones.stream().allMatch(d->d.isRemoved()),"Disabling turret removes both bodies immediately");
        h.assertTrue(BlackDogArmor.canRegenerate(p,watch),"Disarming an idle suit permits charging again");
        h.assertTrue(!BlackDogArmor.selectSystem(p,2),"Uninstalled mining cannon cannot be selected");h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=100)
    public static void particleShieldToggleChangesActualDamageAndEnergyCost(GameTestHelper h){
        var p=player(h);var watch=equip(p,"shield");p.setItemInHand(InteractionHand.MAIN_HAND,CatalogueContent.stack("blackdog_fire_control",1));BlackDogArmor.selectSystem(p,4);use(p);
        var off=new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(p,new net.neoforged.neoforge.common.damagesource.DamageContainer(p.damageSources().generic(),10));BlackDogArmor.incomingDamage(off);
        h.assertTrue(off.getAmount()==8&&BlackDogArmor.charge(watch)==999000,"Disabled particle shield leaves only the basic 20 percent buffer");
        use(p);var on=new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(p,new net.neoforged.neoforge.common.damagesource.DamageContainer(p.damageSources().generic(),10));BlackDogArmor.incomingDamage(on);
        h.assertTrue(on.getAmount()==2&&BlackDogArmor.charge(watch)==995000,"Enabled shield absorbs 80 percent and pays the real 4000 XH");h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=160)
    public static void idleReactorResumesAfterRealCombatDelay(GameTestHelper h){
        var p=player(h);var watch=equip(p,"night","breathing","jump","shield");BlackDogArmor.setCharge(watch,50000);
        BlackDogArmor.regenerate(p,watch);h.assertTrue(BlackDogArmor.charge(watch)==50800,"Idle reactor restores power even while shield is on standby");
        BlackDogArmor.markCombat(p,watch);BlackDogArmor.regenerate(p,watch);h.assertTrue(BlackDogArmor.charge(watch)==50800,"Combat immediately blocks charging");
        h.runAfterDelay(101,()->{BlackDogArmor.regenerate(p,watch);h.assertTrue(BlackDogArmor.charge(watch)==51600,"Actual game time re-enables charging after five seconds");h.succeed();});
    }
    @GameTest(template="combat",timeoutTicks=100)
    public static void cruiseRequestsPayMoreAndCleanUpOnTimeoutAndRecall(GameTestHelper h){
        var p=player(h);var watch=equip(p,"flight");BlackDogArmor.tickPlayer(p);BlackDogArmor.flightRequest(p,true,true);p.tickCount=1;BlackDogArmor.tickPlayer(p);
        h.assertTrue(!p.getAbilities().flying&&BlackDogArmor.cruise(p)&&p.getPose()==Pose.FALL_FLYING&&BlackDogArmor.charge(watch)==999650,"Cruise uses actual gliding posture rather than the incompatible vanilla hover flag and spends its higher tick budget");
        p.getPersistentData().putLong("BlackDogFlightInput",p.level().getGameTime()-20);BlackDogArmor.tickPlayer(p);
        h.assertTrue(!BlackDogArmor.cruise(p)&&BlackDogArmor.charge(watch)==999550,"Stale input returns to hover instead of unlimited cruise");
        BlackDogArmor.recall(p,watch);h.assertTrue(!p.getAbilities().mayfly&&!p.getAbilities().flying&&!p.isFallFlying(),"Recall revokes flight, cruise and horizontal pose");h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=100)
    public static void miningCannonBoresWideDeepObsidianAndStopsAtContainer(GameTestHelper h){
        var p=player(h);var watch=equip(p,"cannon");var origin=BlockPos.containing(p.getEyePosition());
        for(int z=20;z<23;z++)for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)h.getLevel().setBlock(origin.offset(x,y,z),Blocks.OBSIDIAN.defaultBlockState(),3);
        var chest=origin.offset(0,0,24);var protectedBehind=origin.offset(0,0,28);h.getLevel().setBlock(chest,Blocks.CHEST.defaultBlockState(),3);h.getLevel().setBlock(protectedBehind,Blocks.OBSIDIAN.defaultBlockState(),3);
        p.setItemInHand(InteractionHand.MAIN_HAND,CatalogueContent.stack("blackdog_fire_control",1));use(p);
        for(int z=20;z<23;z++)for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)h.assertTrue(h.getLevel().getBlockState(origin.offset(x,y,z)).isAir(),"The cannon destroys an entire five by five obsidian wall beyond the old twelve metre range");
        h.assertTrue(h.getLevel().getBlockState(chest).is(Blocks.CHEST)&&h.getLevel().getBlockState(protectedBehind).is(Blocks.OBSIDIAN),"Containers stop the central ray and remain intact with material behind them");
        h.assertTrue(BlackDogArmor.charge(watch)==984500,"A real 75-block bore pays only its 8000 shot and 100 per successful block");h.succeed();
    }
}
