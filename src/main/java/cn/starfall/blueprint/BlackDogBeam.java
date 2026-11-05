package cn.starfall.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

public final class BlackDogBeam extends Entity {
    private static final EntityDataAccessor<Vector3f> END=SynchedEntityData.defineId(BlackDogBeam.class,EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> STYLE=SynchedEntityData.defineId(BlackDogBeam.class,EntityDataSerializers.INT);
    public BlackDogBeam(EntityType<? extends BlackDogBeam> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(END,new Vector3f());b.define(STYLE,0);}
    public void configure(Vec3 end,int style){entityData.set(END,end.toVector3f());entityData.set(STYLE,style);}
    public Vec3 end(){return new Vec3(entityData.get(END));}
    public int style(){return entityData.get(STYLE);}
    public int lifetime(){return style()==1?16:style()==3?14:8;}
    @Override public void tick(){super.tick();if(!level().isClientSide&&tickCount>=lifetime())discard();}
    @Override public AABB getBoundingBoxForCulling(){return new AABB(position(),position().add(end())).inflate(3);}
    @Override protected void readAdditionalSaveData(CompoundTag d){}
    @Override protected void addAdditionalSaveData(CompoundTag d){}
}
