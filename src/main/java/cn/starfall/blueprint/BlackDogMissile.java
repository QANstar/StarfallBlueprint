package cn.starfall.blueprint;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Fast physical missile with its own model, exhaust and impact. */
public final class BlackDogMissile extends Projectile {
    private LivingEntity target;
    public void track(LivingEntity target){this.target=target;}
    public BlackDogMissile(EntityType<? extends BlackDogMissile> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b){}
    @Override public void tick(){
        super.tick();
        if(!level().isClientSide&&target!=null){
            if(!target.isAlive()||target.level()!=level()){discard();return;}
            Vec3 aim=target.getBoundingBox().getCenter().add(target.getDeltaMovement().scale(2)).subtract(position()).normalize();
            Vec3 steered=getDeltaMovement().normalize().lerp(aim,tickCount<6?.12:.28).normalize();
            setDeltaMovement(steered.scale(Math.min(1.65,.8+tickCount*.08)));
        }
        Vec3 motion=getDeltaMovement();
        var hit=ProjectileUtil.getHitResultOnMoveVector(this,this::canHitEntity);
        if(!level().isClientSide&&hit.getType()!=HitResult.Type.MISS){
            setPos(hit.getLocation());
            if(hit instanceof EntityHitResult entityHit)entityHit.getEntity().hurt(damageSources().thrown(this,getOwner()),18);
            level().explode(this,getX(),getY(),getZ(),2f,Level.ExplosionInteraction.MOB);
            BlackDogWeapons.beam((net.minecraft.server.level.ServerLevel)level(),position(),position(),4);discard();return;
        }
        setPos(position().add(motion));
        setYRot((float)Math.toDegrees(Math.atan2(-motion.x,motion.z)));setXRot((float)-Math.toDegrees(Math.atan2(motion.y,motion.horizontalDistance())));
        if(level().isClientSide){
            for(int i=0;i<4;i++){Vec3 trail=position().subtract(motion.scale(i*.18));level().addParticle(ParticleTypes.FLAME,trail.x,trail.y,trail.z,-motion.x*.03,-motion.y*.03,-motion.z*.03);}
            level().addParticle(ParticleTypes.SMOKE,getX(),getY(),getZ(),0,.01,0);
        }else if(tickCount>100||!level().hasChunkAt(blockPosition()))discard();
    }
}
