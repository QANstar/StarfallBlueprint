package cn.starfall.blueprint;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A server-authoritative, straight flying missile. Unloaded chunks are never entered. */
public final class BestMechMissile extends Entity {
    private UUID pilotId, mechId;
    private boolean tianpu, friendlyFire, terrainDamage, fireControl;
    private double remaining;
    public BestMechMissile(EntityType<? extends BestMechMissile> type, Level level) { super(type, level); noPhysics=true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    public void configure(ServerPlayer pilot, BestMechEntity mech, double range, boolean finalWeapon,
                          boolean friendly, boolean terrain, boolean marking) {
        pilotId=pilot.getUUID(); mechId=mech.getUUID(); remaining=range;
        tianpu=finalWeapon; friendlyFire=friendly; terrainDamage=terrain; fireControl=marking;
    }
    @Override public void tick() {
        super.tick();
        Vec3 velocity=getDeltaMovement();
        if (level().isClientSide) {
            for (int i=0;i<3;i++) { Vec3 p=position().subtract(velocity.scale(i*.2)); level().addParticle(ParticleTypes.FLAME,p.x,p.y,p.z,0,0,0); }
            level().addParticle(ParticleTypes.SMOKE,getX(),getY(),getZ(),0,.01,0);
            return;
        }
        var world=(ServerLevel)level();
        var pilotEntity=pilotId==null?null:world.getPlayerByUUID(pilotId);
        var entity=mechId==null?null:world.getEntity(mechId);
        if (!(entity instanceof BestMechEntity mech) || !(pilotEntity instanceof ServerPlayer pilot) || !pilot.isAlive() || pilot.isSpectator()
                || !mech.isAlive() || !mech.qualifiedFor(pilot) || tickCount>100 || remaining<=0
                || !world.hasChunkAt(blockPosition()) || !world.getWorldBorder().isWithinBounds(blockPosition())
                || !world.mayInteract(pilot,blockPosition())) { discard(); return; }
        double length=Math.min(remaining, velocity.length());
        if (!Double.isFinite(length) || length<1e-6) { discard(); return; }
        Vec3 from=position(), step=velocity.normalize().scale(length), to=from.add(step);
        if (!chunksLoaded(world,from,to)) { discard(); return; }
        // Check the complete segment before clip() can inspect any not-yet-loaded chunk.
        int samples=Math.max(1,(int)Math.ceil(length/.25));
        for (int i=1;i<=samples;i++) {
            BlockPos pos=BlockPos.containing(from.add(step.scale((double)i/samples)));
            if (!world.hasChunkAt(pos) || !world.getWorldBorder().isWithinBounds(pos) || !world.mayInteract(pilot,pos)) { discard(); return; }
        }
        var block=world.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        Vec3 collision=block.getType()==HitResult.Type.MISS?null:block.getLocation();
        double nearest=collision==null?Double.MAX_VALUE:from.distanceToSqr(collision);
        Entity hit=null;
        for (Entity candidate:world.getEntities(this,getBoundingBox().expandTowards(step).inflate(.3),
                e->e!=mech && e!=pilot && !e.isPassengerOfSameVehicle(mech) && e.isAlive() && e.isPickable())) {
            var bounds=candidate.getBoundingBox().inflate(.1);
            var crossing=bounds.contains(from)?java.util.Optional.of(from):bounds.clip(from,to);
            if (crossing.isPresent() && from.distanceToSqr(crossing.get())<nearest) { collision=crossing.get(); nearest=from.distanceToSqr(collision); hit=candidate; }
        }
        if (collision!=null) {
            setPos(collision);
            impact(world,pilot,mech,hit,collision.subtract(step.normalize().scale(.04)));
            discard(); return;
        }
        setPos(to); remaining-=length;
        setYRot((float)Math.toDegrees(Math.atan2(-velocity.x,velocity.z)));
        setXRot((float)-Math.toDegrees(Math.atan2(velocity.y,velocity.horizontalDistance())));
        if (remaining<=0) discard();
    }
    private boolean allowed(ServerLevel world, ServerPlayer pilot, BestMechEntity mech, LivingEntity target) {
        if (target==pilot || target==mech || !target.isAlive() || target.isSpectator()
                || !world.hasChunkAt(target.blockPosition()) || !world.getWorldBorder().isWithinBounds(target.blockPosition())
                || !world.mayInteract(pilot,target.blockPosition())) return false;
        return mech.combatSystems().hostile(pilot,target) || tianpu && friendlyFire && BestMechSupport.isFriendly(mech,target);
    }
    private boolean chunksLoaded(ServerLevel world, Vec3 from, Vec3 to) {
        BlockPos a=BlockPos.containing(from), b=BlockPos.containing(to);
        for (int x=Math.min(a.getX()>>4,b.getX()>>4);x<=Math.max(a.getX()>>4,b.getX()>>4);x++)
            for (int z=Math.min(a.getZ()>>4,b.getZ()>>4);z<=Math.max(a.getZ()>>4,b.getZ()>>4);z++)
                if (!world.hasChunkAt(new BlockPos(x<<4,a.getY(),z<<4))) return false;
        return true;
    }
    private boolean clear(ServerLevel world, Vec3 from, Vec3 to, ServerPlayer pilot, BestMechEntity mech, LivingEntity target, Entity direct) {
        if (!chunksLoaded(world,from,to)) return false;
        if (world.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS) return false;
        for (LivingEntity body:world.getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(.3),
                e->e!=pilot && e!=mech && e!=target && e!=direct && e.isAlive()))
            if (body.getBoundingBox().inflate(.1).clip(from,to).isPresent()) return false;
        return true;
    }
    private void impact(ServerLevel world, ServerPlayer pilot, BestMechEntity mech, Entity hit, Vec3 origin) {
        BlockPos impactPos=BlockPos.containing(origin);
        if (!world.hasChunkAt(impactPos) || !world.getWorldBorder().isWithinBounds(impactPos) || !world.mayInteract(pilot,impactPos)) return;
        LivingEntity direct=hit instanceof LivingEntity living?living:null;
        if (direct!=null && allowed(world,pilot,mech,direct)) {
            direct.hurt(world.damageSources().playerAttack(pilot),tianpu?18:14);
            if (fireControl) for (LivingEntity marked:world.getEntitiesOfClass(LivingEntity.class,direct.getBoundingBox().inflate(6),e->allowed(world,pilot,mech,e) && mech.combatSystems().hostile(pilot,e)))
                if (clear(world,mech.getEyePosition(),marked.getEyePosition(),pilot,mech,marked,null)) marked.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,100,0));
        }
        for (LivingEntity target:world.getEntitiesOfClass(LivingEntity.class,new AABB(origin,origin).inflate(3))) {
            if (target==direct || target.distanceToSqr(origin)>9 || !allowed(world,pilot,mech,target)) continue;
            if (clear(world,origin,target.getEyePosition(),pilot,mech,target,direct)) target.hurt(world.damageSources().playerAttack(pilot),tianpu?10:8);
        }
        if (tianpu && terrainDamage) breakTerrain(world,pilot,origin);
        world.sendParticles(ParticleTypes.EXPLOSION,origin.x,origin.y,origin.z,1,0,0,0,0);
        world.sendParticles(ParticleTypes.SMOKE,origin.x,origin.y,origin.z,12,.35,.35,.35,.04);
        world.playSound(null,impactPos,SoundEvents.GENERIC_EXPLODE.value(),SoundSource.PLAYERS,1,.9f);
    }
    private void breakTerrain(ServerLevel world, ServerPlayer pilot, Vec3 origin) {
        BlockPos center=BlockPos.containing(position());
        var breakable=new java.util.ArrayList<BlockPos>();
        for (int x=-2;x<=2;x++) for (int y=-2;y<=2;y++) for (int z=-2;z<=2;z++) {
            if (x*x+y*y+z*z>4) continue;
            BlockPos pos=center.offset(x,y,z);
            if (!world.hasChunkAt(pos) || !world.getWorldBorder().isWithinBounds(pos) || !world.mayInteract(pilot,pos)) continue;
            var state=world.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(world,pos)<0) continue;
            Vec3 destination=Vec3.atCenterOf(pos);
            if (!chunksLoaded(world,origin,destination)) continue;
            var obstruction=world.clip(new ClipContext(origin,destination,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if (obstruction.getType()!=HitResult.Type.MISS && !obstruction.getBlockPos().equals(pos)) continue;
            breakable.add(pos);
        }
        for (BlockPos pos:breakable) pilot.gameMode.destroyBlock(pos);
    }
}
