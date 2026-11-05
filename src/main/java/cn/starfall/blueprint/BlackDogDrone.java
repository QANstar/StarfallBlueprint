package cn.starfall.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

/** Two independently tracked hovering gun bodies; removed when their owning suit is unavailable. */
public final class BlackDogDrone extends Entity {
    private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(BlackDogDrone.class,EntityDataSerializers.INT),SIDE=SynchedEntityData.defineId(BlackDogDrone.class,EntityDataSerializers.INT),TARGET=SynchedEntityData.defineId(BlackDogDrone.class,EntityDataSerializers.INT);
    private long fired;
    private boolean animationReady;
    private Vec3 offset=Vec3.ZERO,previousOffset=Vec3.ZERO,offsetVelocity=Vec3.ZERO,pathStart=Vec3.ZERO,pathControl=Vec3.ZERO,pathEnd=Vec3.ZERO;
    private int pathAge=6;
    private float aimYaw,previousAimYaw,aimPitch,previousAimPitch;
    public BlackDogDrone(EntityType<? extends BlackDogDrone> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(OWNER,-1);b.define(SIDE,0);b.define(TARGET,-1);}
    public void configure(Player owner,int side){entityData.set(OWNER,owner.getId());entityData.set(SIDE,side);}
    public int ownerId(){return entityData.get(OWNER);}
    public Entity owner(){return level().getEntity(ownerId());}
    public Entity target(){return level().getEntity(entityData.get(TARGET));}
    public void fireAt(Entity target){entityData.set(TARGET,target.getId());fired=level().getGameTime();}
    public Vec3 anchor(Entity owner,float partial){
        double angle=Math.toRadians(owner.getYRot())+(entityData.get(SIDE)==0?-Math.PI/2:Math.PI/2);
        return owner.getPosition(partial).add(-Math.sin(angle)*1.1,1.8+Math.sin((owner.tickCount+partial)*.1+entityData.get(SIDE)*2)*.1,Math.cos(angle)*1.1);
    }
    /** Position packets never compete with local formation animation after the owner is available. */
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){
        if(!level().isClientSide||!animationReady)super.lerpTo(x,y,z,yaw,pitch,steps);
    }
    public Vec3 renderPosition(float partial){var owner=owner();return animationReady&&owner!=null?owner.getPosition(partial).add(previousOffset.lerp(offset,partial)):getPosition(partial);}
    public float aimYaw(float partial){return Mth.rotLerp(partial,previousAimYaw,aimYaw);}
    public float aimPitch(float partial){return Mth.lerp(partial,previousAimPitch,aimPitch);}
    private void animate(Player player){
        Vec3 targetOffset=anchor(player,1).subtract(player.position());
        if(!animationReady){
            offset=previousOffset=pathStart=pathControl=pathEnd=targetOffset;aimYaw=previousAimYaw=player.getYRot();animationReady=true;
        }
        previousOffset=offset;previousAimYaw=aimYaw;previousAimPitch=aimPitch;
        if(pathAge>=4){pathStart=offset;pathControl=offset.add(offsetVelocity.scale(2));pathEnd=targetOffset;pathAge=0;}
        double t=++pathAge/6d,u=1-t;
        // Cubic Bezier: preserve incoming tangent, ease toward the moving formation target.
        offset=pathStart.scale(u*u*u).add(pathControl.scale(3*u*u*t)).add(pathEnd.scale(3*u*t*t+t*t*t));offsetVelocity=offset.subtract(previousOffset);
        setPos(player.position().add(offset));
        var target=target();float heading=player.getViewYRot(1),pitch=0;
        if(target!=null){var direction=target.getEyePosition().subtract(position());heading=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));pitch=(float)-Math.toDegrees(Math.atan2(direction.y,direction.horizontalDistance()));}
        aimYaw=Mth.rotLerp(.18f,aimYaw,heading);aimPitch=Mth.lerp(.18f,aimPitch,pitch);
    }
    @Override public void tick(){
        super.tick();var owner=owner();
        if(owner instanceof Player player&&player.isAlive()){
            if(!level().isClientSide){var watch=BlackDogArmor.active(player);if(watch.isEmpty()||!BlackDogArmor.complete(player,watch)||!BlackDogArmor.enabled(watch,"turret")||BlackDogArmor.charge(watch)<=0){discard();return;}if(level().getGameTime()-fired>6)entityData.set(TARGET,-1);}
            if(level().isClientSide)animate(player);else setPos(anchor(player,1));
        }else if(!level().isClientSide)discard();
    }
    @Override protected void readAdditionalSaveData(CompoundTag d){}
    @Override protected void addAdditionalSaveData(CompoundTag d){}
}
