package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

/** Dedicated networked weapon visuals; all block work is performed through the real player game mode. */
public final class BlackDogWeapons {
    private static final Map<Player,BlackDogDrone[]> DRONES=new WeakHashMap<>();
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(Registries.ENTITY_TYPE,StarfallBlueprint.ID);
    public static final DeferredHolder<EntityType<?>,EntityType<BlackDogDrone>> DRONE=TYPES.register("blackdog_drone",()->EntityType.Builder.<BlackDogDrone>of(BlackDogDrone::new,MobCategory.MISC).sized(.65f,.3f).noSave().clientTrackingRange(96).updateInterval(2).build("blackdog_drone"));
    public static final DeferredHolder<EntityType<?>,EntityType<BlackDogBeam>> BEAM=TYPES.register("blackdog_beam",()->EntityType.Builder.<BlackDogBeam>of(BlackDogBeam::new,MobCategory.MISC).sized(.1f,.1f).noSave().clientTrackingRange(96).updateInterval(20).build("blackdog_beam"));
    public static final DeferredHolder<EntityType<?>,EntityType<BlackDogMissile>> MISSILE=TYPES.register("blackdog_missile",()->EntityType.Builder.<BlackDogMissile>of(BlackDogMissile::new,MobCategory.MISC).sized(.25f,.25f).noSave().clientTrackingRange(96).updateInterval(1).build("blackdog_missile"));
    public static void register(IEventBus bus){TYPES.register(bus);}
    public static void beam(ServerLevel level,Vec3 from,Vec3 to,int style){
        var beam=BEAM.get().create(level);if(beam==null)return;beam.setPos(from);beam.configure(to.subtract(from),style);level.addFreshEntity(beam);
    }
    public static void sound(Player player,SoundEvent sound,float volume,float pitch){player.level().playSound(null,player.blockPosition(),sound,SoundSource.PLAYERS,volume,pitch);}
    public static void shieldImpact(Player player){beam((ServerLevel)player.level(),player.position().add(0,1,0),player.position().add(0,1,0),3);sound(player,SoundEvents.SHIELD_BLOCK,.8f,1.4f);}
    public static void tickDrones(Player player,ItemStack watch){
        if(!(player.level() instanceof ServerLevel level))return;
        boolean active=BlackDogArmor.enabled(watch,"turret")&&BlackDogArmor.charge(watch)>0;
        if(!active){clearDrones(player);return;}
        var pair=DRONES.computeIfAbsent(player,p->new BlackDogDrone[2]);
        for(int side=0;side<2;side++)if(pair[side]==null||pair[side].isRemoved()||pair[side].level()!=level){
            var drone=DRONE.get().create(level);if(drone==null)continue;drone.configure(player,side);drone.setPos(drone.anchor(player,1));
            if(level.addFreshEntity(drone))pair[side]=drone;
        }
    }
    public static void clearDrones(Player player){var pair=DRONES.remove(player);if(pair!=null)for(var drone:pair)if(drone!=null)drone.discard();}
    public static void droneShot(Player player,LivingEntity target){
        var level=(ServerLevel)player.level();int side=(player.tickCount/20)&1;
        var pair=DRONES.get(player);var drone=pair==null?null:pair[side];
        Vec3 from=player.getEyePosition().add(0,.5,0);
        if(drone!=null&&!drone.isRemoved()){from=drone.position();drone.fireAt(target);}
        beam(level,from,target.getEyePosition(),2);sound(player,SoundEvents.GUARDIAN_ATTACK,.45f,1.7f);
    }
    public static void fire(Player player,ItemStack watch,String weapon){
        var level=(ServerLevel)player.level();Vec3 from=player.getEyePosition(),direction=player.getLookAngle();
        if(weapon.equals("missile")){
            var missile=MISSILE.get().create(level);if(missile==null)return;
            missile.setOwner(player);missile.setPos(from.add(direction.scale(.8)));missile.setDeltaMovement(direction.scale(1.8));missile.setYRot(player.getYRot());missile.setXRot(player.getXRot());level.addFreshEntity(missile);
            sound(player,SoundEvents.FIREWORK_ROCKET_LAUNCH,1.2f,.65f);return;
        }
        boolean cannon=weapon.equals("cannon");Vec3 end;
        if(cannon&&player instanceof ServerPlayer server)end=mine(server,watch,from,direction);
        else end=level.clip(new ClipContext(from,from.add(direction.scale(64)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getLocation();
        Vec3 right=direction.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<.01)right=new Vec3(1,0,0);
        beam(level,from.add(right.scale(.22)).add(0,-.15,0),end,cannon?1:0);
        sound(player,cannon?SoundEvents.RESPAWN_ANCHOR_DEPLETE.value():SoundEvents.WARDEN_SONIC_BOOM,cannon?1.5f:.6f,cannon?.65f:1.8f);
        for(var target:level.getEntitiesOfClass(LivingEntity.class,new AABB(from,end).inflate(cannon?2.2:.4),e->e!=player&&e.isAlive()&&!player.isAlliedTo(e))){
            if(target instanceof Player other&&!player.canHarmPlayer(other))continue;
            if(target.getBoundingBox().inflate(cannon?1.8:.2).clip(from,end).isEmpty())continue;
            var obstruction=level.clip(new ClipContext(from,target.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
            if(obstruction.getType()!=HitResult.Type.MISS&&from.distanceToSqr(obstruction.getLocation())+.3<from.distanceToSqr(target.getEyePosition()))continue;
            target.hurt(player.damageSources().playerAttack(player),cannon?24:12);
        }
    }
    /** 48 m, five by five bore, at most 1536 successful breaks; each ray stops at protected blocks. */
    public static Vec3 mine(ServerPlayer player,ItemStack watch,Vec3 from,Vec3 direction){
        var level=player.serverLevel();var checked=new HashSet<BlockPos>();
        Vec3 right=direction.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<.01)right=new Vec3(1,0,0);
        Vec3 up=right.cross(direction).normalize();int broken=0;double range=48;
        var offsets=new ArrayList<int[]>();offsets.add(new int[]{0,0});
        for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)if(x!=0||y!=0)offsets.add(new int[]{x,y});
        for(var offset:offsets){
            Vec3 origin=from.add(right.scale(offset[0])).add(up.scale(offset[1]));boolean center=offset[0]==0&&offset[1]==0;
            for(double distance=1;distance<=range;distance+=.5){
                BlockPos pos=BlockPos.containing(origin.add(direction.scale(distance)));
                if(checked.contains(pos))continue;
                if(!level.hasChunkAt(pos)){if(center)range=Math.max(0,distance-.75);break;}
                var state=level.getBlockState(pos);if(state.isAir()){checked.add(pos);continue;}
                boolean blocked=broken>=1536||BlackDogArmor.charge(watch)<100||!level.mayInteract(player,pos)||state.getDestroySpeed(level,pos)<0||state.getDestroySpeed(level,pos)>100||level.getBlockEntity(pos)!=null;
                if(blocked||!player.gameMode.destroyBlock(pos)){if(center)range=Math.max(0,distance-.75);break;}
                BlackDogArmor.spend(watch,100);broken++;checked.add(pos);
            }
            if(broken>=1536||BlackDogArmor.charge(watch)<100)break;
        }
        return from.add(direction.scale(range));
    }
    private BlackDogWeapons(){}
}
