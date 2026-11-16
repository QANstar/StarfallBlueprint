package cn.starfall.blueprint;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Persisted orbital impacts, guided missiles and gravity bombs; never loads a target chunk. */
public final class OrbitalStrike extends Entity {
    private static final EntityDataAccessor<Integer> STYLE=SynchedEntityData.defineId(OrbitalStrike.class,EntityDataSerializers.INT),TIME=SynchedEntityData.defineId(OrbitalStrike.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS=SynchedEntityData.defineId(OrbitalStrike.class,EntityDataSerializers.FLOAT),DAMAGE=SynchedEntityData.defineId(OrbitalStrike.class,EntityDataSerializers.FLOAT),BLAST=SynchedEntityData.defineId(OrbitalStrike.class,EntityDataSerializers.FLOAT);
    private UUID owner,source,target;private int impactTicks=60;private boolean impacted;
    private boolean blockDestruction,terrainPending;private int terrainUpgrade,terrainCursor;
    private boolean layeredTerrain=true;
    public void terrain(boolean enabled,int upgrade){blockDestruction=enabled;terrainUpgrade=Mth.clamp(upgrade,0,OrbitalFleet.Fleet.MAX_UPGRADE);}
    public int terrainRadius(){return style()==0?2+terrainUpgrade/2:Math.min(28,(int)Math.ceil(radius()));}
    public int terrainDepth(){return style()==0?12+terrainUpgrade*7:8+terrainUpgrade*2;}
    public OrbitalStrike(EntityType<? extends OrbitalStrike> t,Level l){super(t,l);noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(STYLE,0);b.define(TIME,0);b.define(RADIUS,4f);b.define(DAMAGE,80f);b.define(BLAST,2.5f);}
    public int style(){return entityData.get(STYLE);}public int age(){return entityData.get(TIME);}public int delay(){return impactTicks;}public float radius(){return entityData.get(RADIUS);}public float damage(){return entityData.get(DAMAGE);}public float blast(){return entityData.get(BLAST);}
    public void configure(int style,UUID owner,int delay){entityData.set(STYLE,style);this.owner=owner;impactTicks=delay;entityData.set(TIME,-delay);if(style==1)strength(220,12,8);}
    public void strength(float damage,float radius,float blast){entityData.set(DAMAGE,Mth.clamp(damage,0,2000));entityData.set(RADIUS,Mth.clamp(radius,.5f,40));entityData.set(BLAST,Mth.clamp(blast,0,16));}
    public void projectileSource(Entity vehicle,Entity locked){source=vehicle==null?null:vehicle.getUUID();target=locked==null?null:locked.getUUID();}
    public UUID guidanceTarget(){return target;}
    @Override public void tick(){super.tick();if(level().isClientSide)return;int age=age()+1;entityData.set(TIME,age);
        if(style()==3){if(age%2==0)((ServerLevel)level()).sendParticles(ParticleTypes.CLOUD,getX(),getY()+Math.max(0,age+impactTicks)*.35,getZ(),8,.4,.3,.4,.08);if(age>20)discard();return;}
        if(style()==2||style()==4){projectile(age);return;}
        if(age==0&&!impacted){impacted=true;impact();}if(terrainPending)excavate();if(age>24&&!terrainPending)discard();
    }
    private void projectile(int age){
        var l=(ServerLevel)level();var pilot=owner==null?null:l.getPlayerByUUID(owner);Vec3 motion=getDeltaMovement();
        if(style()==4)motion=motion.scale(.99).add(0,-.055,0);
        else if(target!=null){var entity=l.getEntity(target);if(entity!=null&&entity.isAlive()){double speed=Mth.clamp(motion.length(),2.4,4.2);Vec3 lead=entity.getBoundingBox().getCenter().add(entity.getDeltaMovement().scale(Math.min(10,distanceTo(entity)/speed)));motion=motion.scale(.78).add(lead.subtract(position()).normalize().scale(speed*.22)).normalize().scale(speed);}else target=null;}
        Vec3 next=position().add(motion);if(!l.hasChunkAt(net.minecraft.core.BlockPos.containing(next))||!l.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(next))){discard();return;}
        var block=l.clip(new ClipContext(position(),next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));Vec3 collision=block.getType()==HitResult.Type.MISS?null:block.getLocation();double nearest=collision==null?Double.MAX_VALUE:collision.distanceToSqr(position());
        for(var e:l.getEntities(this,getBoundingBox().expandTowards(motion).inflate(.65),e->e!=pilot&&!e.getUUID().equals(source)&&!(e.getVehicle()!=null&&e.getVehicle().getUUID().equals(source))&&e.isAlive()&&e.isPickable())){
            var entry=e.getBoundingBox().inflate(.35).clip(position(),next);if(entry.isPresent()&&entry.get().distanceToSqr(position())<nearest){collision=entry.get();nearest=collision.distanceToSqr(position());}}
        setDeltaMovement(motion);if(collision!=null){setPos(collision);if(pilot!=null&&l.mayInteract(pilot,blockPosition()))l.explode(pilot,getX(),getY(),getZ(),style()==4?5:3,style()==4?Level.ExplosionInteraction.BLOCK:Level.ExplosionInteraction.NONE);discard();return;}
        if(style()==2){
            int steps=Math.max(1,(int)Math.ceil(motion.length()/.4));
            for(int i=0;i<steps;i++){Vec3 trail=position().add(motion.scale((double)i/steps));l.sendParticles(ParticleTypes.FLAME,trail.x,trail.y,trail.z,1,.06,.06,.06,.005);l.sendParticles(ParticleTypes.SMOKE,trail.x,trail.y,trail.z,1,.12,.12,.12,.008);}
        }
        setPos(next);if(age>=(style()==4?200:100)||getY()<l.getMinBuildHeight()-16){discard();return;}
        l.sendParticles(style()==4?ParticleTypes.CRIT:ParticleTypes.SMOKE,getX(),getY(),getZ(),1,0,0,0,0);
    }
    private void impact(){
        if(!(level() instanceof ServerLevel l)||owner==null)return;var p=l.getPlayerByUUID(owner);var pos=blockPosition();if(p==null||!p.isAlive()||!l.hasChunkAt(pos)||!l.mayInteract(p,pos))return;
        float radius=radius(),damage=damage();for(var entity:l.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(radius)))if(entity!=p&&entity.distanceToSqr(this)<=radius*radius){var hit=l.clip(new ClipContext(position().add(0,1,0),entity.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));if(hit.getType()==HitResult.Type.MISS)entity.hurt(l.damageSources().playerAttack(p),damage*(float)Math.max(.2,1-entity.distanceTo(this)/radius));}
        // Terrain is handled separately so the off switch also prevents vanilla blast damage to blocks.
        l.explode(p,getX(),getY(),getZ(),blast(),Level.ExplosionInteraction.NONE);terrainPending=blockDestruction;
        l.sendParticles(style()==0?ParticleTypes.END_ROD:ParticleTypes.EXPLOSION_EMITTER,getX(),getY()+1,getZ(),style()==0?30:2,1,1,1,.1);
        l.playSound(null,pos,style()==0?net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE:net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),net.minecraft.sounds.SoundSource.PLAYERS,3,style()==0?.5f:.6f);
    }
    private void excavate(){
        var l=(ServerLevel)level();var p=owner==null?null:l.getPlayerByUUID(owner);
        if(p==null||!p.isAlive()||p.isSpectator()){terrainPending=false;return;}
        var budget=OrbitalTerrainBudget.get(l.getServer());long started=System.nanoTime();int changed=0;
        int r=terrainRadius(),depth=terrainDepth(),width=r*2+1,top=style()==0?4:8,height=depth+top+1;
        var pattern=layeredTerrain?OrbitalTerrainPattern.get(style()==0,r,depth):null;
        int total=pattern==null?width*width*height:pattern.size();var origin=blockPosition();
        // Bound real mutations as well as scans. All strikes share the server's tick/time allowance.
        try{for(int work=0;work<2048&&changed<OrbitalTerrainBudget.BLOCKS_PER_STRIKE&&terrainCursor<total&&budget.available(started);work++){
            budget.scans--;
            net.minecraft.core.BlockPos pos;
            if(pattern!=null)pos=pattern.position(terrainCursor++,origin);
            else{
                // Pending strikes saved by older versions keep their original cursor interpretation.
                int column=terrainCursor/height,dx=column%width-r,dz=column/width-r,dy=top-terrainCursor%height;
                double distance=(dx*dx+dz*dz)/(double)(r*r);
                if(distance>1||dy<-(int)Math.ceil(depth*(style()==0?Math.sqrt(1-distance):1-distance))){terrainCursor+=height-terrainCursor%height;continue;}
                terrainCursor++;pos=origin.offset(dx,dy,dz);
            }
            if(l.isOutsideBuildHeight(pos)||!l.hasChunkAt(pos)||!l.getWorldBorder().isWithinBounds(pos))continue;
            var state=l.getBlockState(pos);if(state.isAir()||!state.getFluidState().isEmpty()||state.getDestroySpeed(l,pos)<0)continue;
            if(!l.mayInteract(p,pos))continue;
            budget.blocks--;
            var event=new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(l,pos,state,p);
            // Keep normal block callbacks, physics, lighting and client updates; omit destroyBlock's
            // per-voxel level event (2001), which floods clients with debris particles and sounds.
            if(!net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled()&&l.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL))changed++;
        }}finally{budget.spent+=System.nanoTime()-started;}
        if(terrainCursor>=total)terrainPending=false;
    }
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<512d*512;}
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(radius()).expandTowards(0,256,0);}
    @Override protected void addAdditionalSaveData(CompoundTag t){t.putBoolean("BlockDestruction",blockDestruction);t.putBoolean("TerrainPending",terrainPending);t.putInt("TerrainUpgrade",terrainUpgrade);t.putInt("TerrainCursor",terrainCursor);t.putInt("TerrainOrder",layeredTerrain?1:0);t.putInt("Style",style());t.putInt("Age",age());t.putInt("Delay",impactTicks);t.putBoolean("Impacted",impacted);t.putFloat("Radius",radius());t.putFloat("Damage",damage());t.putFloat("Blast",blast());if(owner!=null)t.putUUID("Owner",owner);if(source!=null)t.putUUID("Source",source);if(target!=null)t.putUUID("Target",target);}
    @Override protected void readAdditionalSaveData(CompoundTag t){terrain(t.getBoolean("BlockDestruction"),t.getInt("TerrainUpgrade"));terrainPending=blockDestruction&&t.getBoolean("TerrainPending");terrainCursor=Math.clamp(t.getInt("TerrainCursor"),0,200000);layeredTerrain=t.getInt("TerrainOrder")>=1||terrainCursor==0;int style=Mth.clamp(t.getInt("Style"),0,4);entityData.set(STYLE,style);entityData.set(TIME,t.getInt("Age"));impactTicks=Mth.clamp(t.getInt("Delay"),1,100);impacted=t.getBoolean("Impacted");owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;source=t.hasUUID("Source")?t.getUUID("Source"):null;target=t.hasUUID("Target")?t.getUUID("Target"):null;strength(t.contains("Damage")?t.getFloat("Damage"):style==1?220:80,t.contains("Radius")?t.getFloat("Radius"):style==1?12:4,t.contains("Blast")?t.getFloat("Blast"):style==1?8:2.5f);}
}
