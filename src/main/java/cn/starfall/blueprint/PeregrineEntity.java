package cn.starfall.blueprint;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Server flight, weapon selection and targeting. Clients send buttons, never hits or velocity. */
public final class PeregrineEntity extends Entity {
    public static final int FIRE=256,LOCK=512,BOOST=2048,LOCK_TICKS=10,MISSILE_INTERVAL=8;
    public static final double TAKEOFF_SPEED=.55,TAKEOFF_ROLL=8;
    private static final EntityDataAccessor<Integer> ENERGY=dataInt(),AMMO=dataInt(),BOMBS=dataInt(),WEAPON=dataInt(),LOCK_TARGET=dataInt(),LOCK_PROGRESS=dataInt();
    private static final EntityDataAccessor<Float> ROLL=dataFloat(),THROTTLE=dataFloat(),HEALTH=dataFloat();
    private static final EntityDataAccessor<Boolean> BOOSTING=SynchedEntityData.defineId(PeregrineEntity.class,EntityDataSerializers.BOOLEAN);
    private static EntityDataAccessor<Integer> dataInt(){return SynchedEntityData.defineId(PeregrineEntity.class,EntityDataSerializers.INT);}
    private static EntityDataAccessor<Float> dataFloat(){return SynchedEntityData.defineId(PeregrineEntity.class,EntityDataSerializers.FLOAT);}
    private int input,lastInput,laserCooldown,missileCooldown,bombCooldown,lerpSteps;
    private boolean creativeAircraft;
    private double targetX,targetY,targetZ,groundRoll;
    private float targetYaw,targetPitch,clientRoll,previousClientRoll;
    private UUID trackingTarget;
    public PeregrineEntity(EntityType<? extends PeregrineEntity> t,Level l){super(t,l);blocksBuilding=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(ENERGY,0);b.define(AMMO,0);b.define(BOMBS,0);b.define(WEAPON,0);b.define(LOCK_TARGET,-1);b.define(LOCK_PROGRESS,0);b.define(ROLL,0f);b.define(THROTTLE,0f);b.define(HEALTH,160f);b.define(BOOSTING,false);}
    public int energy(){return entityData.get(ENERGY);}public int ammo(){return entityData.get(AMMO);}public int bombs(){return entityData.get(BOMBS);}public int weapon(){return entityData.get(WEAPON);}public int lockProgress(){return entityData.get(LOCK_PROGRESS);}public int lockTarget(){return entityData.get(LOCK_TARGET);}
    public float roll(){return entityData.get(ROLL);}public float throttle(){return entityData.get(THROTTLE);}public float health(){return entityData.get(HEALTH);}public boolean boosting(){return entityData.get(BOOSTING);}public double groundRoll(){return groundRoll;}
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){targetX=x;targetY=y;targetZ=z;targetYaw=yaw;targetPitch=pitch;lerpSteps=Math.max(3,steps);}
    public float visualRoll(float partial){return level().isClientSide?Mth.lerp(partial,previousClientRoll,clientRoll):roll();}
    private void interpolateClient(){previousClientRoll=clientRoll;clientRoll=Mth.rotLerp(.45f,clientRoll,roll());if(lerpSteps>0){double t=1d/lerpSteps;setPos(Mth.lerp(t,getX(),targetX),Mth.lerp(t,getY(),targetY),Mth.lerp(t,getZ(),targetZ));setYRot(Mth.rotLerp((float)t,getYRot(),targetYaw));setXRot(Mth.lerp((float)t,getXRot(),targetPitch));lerpSteps--;}}
    @Override public boolean isPickable(){return !isRemoved();}@Override public boolean canBeCollidedWith(){return true;}
    @Override protected boolean canAddPassenger(Entity e){return getPassengers().isEmpty()&&e instanceof Player p&&(!creativeAircraft||p.isCreative());}
    @Override public boolean isControlledByLocalInstance(){return !level().isClientSide;}
    @Override public LivingEntity getControllingPassenger(){return getFirstPassenger() instanceof LivingEntity p?p:null;}
    private boolean operator(Player p){return getFirstPassenger()==p&&p.isAlive()&&!p.isSpectator()&&(!creativeAircraft||p.isCreative())&&Haijia.knows(p,"flight");}
    public void controls(ServerPlayer p,int bits){if(operator(p)){input=bits&(255|FIRE|BOOST);lastInput=tickCount;}}
    public boolean selectWeapon(Player p,int selection){if(!operator(p)||selection<0||selection>2)return false;entityData.set(WEAPON,selection);clearLock();return true;}
    @Override public InteractionResult interact(Player p,InteractionHand hand){
        if(creativeAircraft&&!p.isCreative()||!Haijia.knows(p,"flight"))return InteractionResult.FAIL;if(level().isClientSide)return InteractionResult.SUCCESS;var s=p.getItemInHand(hand);
        if(s.is(Haijia.item("hj_missile"))&&ammo()<16){if(!p.isCreative())s.shrink(1);entityData.set(AMMO,ammo()+1);return InteractionResult.CONSUME;}
        if(s.is(Haijia.item("hj_bomb"))&&bombs()<8){if(!p.isCreative())s.shrink(1);entityData.set(BOMBS,bombs()+1);return InteractionResult.CONSUME;}
        if(p.isShiftKeyDown()&&getPassengers().isEmpty()&&onGround()&&getDeltaMovement().lengthSqr()<.01){var out=Haijia.stack(creativeAircraft?"hj_creative_fighter":"hj_fighter",1);CatalogueEquipment.setData(out,pack());if(OrbitalMenu.transact(p,java.util.Map.of(),out))discard();return InteractionResult.CONSUME;}
        if(getPassengers().isEmpty())p.startRiding(this);return InteractionResult.CONSUME;
    }
    public void toggleSeat(ServerPlayer p){if(getFirstPassenger()==p){p.stopRiding();p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING,200));clearLock();}else if(getPassengers().isEmpty()&&p.distanceToSqr(this)<36)p.startRiding(this);}
    @Override public void tick(){
        super.tick();if(level().isClientSide){interpolateClient();return;}
        if(laserCooldown>0)laserCooldown--;if(missileCooldown>0)missileCooldown--;if(bombCooldown>0)bombCooldown--;
        var pilot=getFirstPassenger() instanceof ServerPlayer p&&operator(p)?p:null;
        if(creativeAircraft&&pilot!=null){entityData.set(ENERGY,2000000);entityData.set(AMMO,16);entityData.set(BOMBS,8);}
        int keys=pilot!=null&&tickCount-lastInput<=10?input:0;
        float throttle=throttle();if((keys&1)!=0)throttle+=.012f;if((keys&2)!=0)throttle-=.025f;if(pilot==null)throttle-=.02f;
        boolean boost=pilot!=null&&(keys&BOOST)!=0&&energy()>=1120;if(boost)throttle=Math.max(throttle,.85f);if(energy()<=0)throttle=0;throttle=Mth.clamp(throttle,0,1);entityData.set(THROTTLE,throttle);
        int cost=(int)(40+throttle*180+(boost?900:0));boolean powered=pilot!=null&&energy()>=cost;boolean wasBoosting=boosting();entityData.set(BOOSTING,boost&&powered);if(boosting()&&!wasBoosting)sound(HaijiaSounds.BOOST.get(),.65f,.85f);
        if(powered){
            if(!creativeAircraft)entityData.set(ENERGY,energy()-cost);
            float pitch=getXRot()+(((keys&8)!=0?1:0)-((keys&4)!=0?1:0))*2.2f;
            setXRot(Mth.clamp(pitch,onGround()?-12:-65,onGround()?8:65));
            float bank=nextBank(roll(),keys,onGround());entityData.set(ROLL,bank);
            setYRot(getYRot()+(((keys&128)!=0?1:0)-((keys&64)!=0?1:0))*(onGround()?2:3.4f)+roll()*.035f);
        }
        if(!powered)entityData.set(ROLL,nextBank(roll(),0,onGround()));
        double horizontal=getDeltaMovement().horizontalDistance();boolean ground=onGround();
        if(ground){if(horizontal<.04)groundRoll=0;else groundRoll+=horizontal;}else groundRoll=0;
        Vec3 motion=flightMotion(getDeltaMovement(),getLookAngle(),throttle,ground,powered,boosting(),groundRoll,getXRot());
        var next=net.minecraft.core.BlockPos.containing(position().add(motion));if(!level().hasChunkAt(next)||!level().getWorldBorder().isWithinBounds(next))motion=new Vec3(0,Math.min(0,motion.y),0);
        setDeltaMovement(motion);move(MoverType.SELF,motion);hasImpulse=true;
        if((horizontalCollision||verticalCollision&&motion.y<-.35)&&horizontal>.65)hurt(level().damageSources().flyIntoWall(),(float)(horizontal*12));
        updateLock(pilot,(keys&FIRE)!=0);
        if(pilot!=null&&(keys&FIRE)!=0){if(weapon()==0)laser(pilot);else if(weapon()==1)missile(pilot);else bomb(pilot);}
    }
    /** Inertia, runway distance, rotation speed and stall sink are independent of presentation. */
    public static Vec3 flightMotion(Vec3 velocity,Vec3 forward,float throttle,boolean ground,boolean powered,boolean boost,double rollDistance,float pitch){
        double speed=velocity.horizontalDistance(),desired=powered?throttle*(boost?3.3:1.5):0;
        double acceleration=powered?(boost?.085:.008+throttle*.027):ground?.045:.006;
        double next=speed<desired?Math.min(desired,speed+acceleration):Math.max(desired,speed-(ground?.035:.012+Math.max(0,speed-desired)*.03));
        Vec3 heading=new Vec3(forward.x,0,forward.z).normalize(),previous=speed>.001?new Vec3(velocity.x/speed,0,velocity.z/speed):heading;
        Vec3 horizontal=previous.lerp(heading,ground?.18:.12).normalize().scale(next);double vertical;
        if(ground){boolean rotate=powered&&next>=TAKEOFF_SPEED&&rollDistance>=TAKEOFF_ROLL&&pitch<-2;vertical=rotate?Math.max(.045,Math.min(.18,forward.y*next*.4)):-.06;}
        else {double stall=1-Mth.clamp(next/TAKEOFF_SPEED,0,1);vertical=velocity.y*.92+forward.y*next*.08-stall*.045-(powered?0:.012);}
        return new Vec3(horizontal.x,vertical,horizontal.z);
    }
    private boolean validTarget(Entity e,Player pilot){return e!=null&&e!=this&&e!=pilot&&e.isAlive()&&e.isPickable()&&!(e.getVehicle() instanceof PeregrineEntity)&&(e instanceof LivingEntity||e instanceof PeregrineEntity)&&e.distanceToSqr(this)<=256*256&&pilot.hasLineOfSight(e);}
    private void clearLock(){trackingTarget=null;entityData.set(LOCK_TARGET,-1);entityData.set(LOCK_PROGRESS,0);}
    /** Re-evaluate the reticle every firing tick; no held lock or acquisition delay. */
    private void updateLock(ServerPlayer p,boolean requested){
        clearLock();if(p==null||!requested||weapon()==2)return;
        var l=(ServerLevel)level();Entity target=null;double best=-Double.MAX_VALUE;Vec3 look=p.getLookAngle();
        for(var e:l.getEntities(this,getBoundingBox().inflate(192),candidate->validTarget(candidate,p))){
            Vec3 delta=e.getBoundingBox().getCenter().subtract(p.getEyePosition());double alignment=look.dot(delta.normalize()),score=alignment-delta.length()*.00015;
            if(alignment>=.9781476&&score>best){best=score;target=e;}
        }
        if(target!=null){trackingTarget=target.getUUID();entityData.set(LOCK_TARGET,target.getId());entityData.set(LOCK_PROGRESS,LOCK_TICKS);}
    }
    /** Symmetric roll input and active wings-level stabilization after release. */
    public static float nextBank(float current,int keys,boolean ground){
        if(ground)return 0;int direction=((keys&32)!=0?1:0)-((keys&16)!=0?1:0);
        if(direction==0)return Math.abs(current)<.3f?0:current*.78f;
        return Mth.clamp(current+direction*4.2f,-65,65);
    }
    public Entity lockedTarget(){return !level().isClientSide&&lockProgress()>=LOCK_TICKS&&trackingTarget!=null?((ServerLevel)level()).getEntity(trackingTarget):null;}
    private Vec3 aim(ServerPlayer p){var target=lockedTarget();return target==null?p.getLookAngle():target.getBoundingBox().getCenter().subtract(position().add(0,.7,0)).normalize();}
    private void laser(ServerPlayer p){
        if(laserCooldown>0||energy()<12000)return;laserCooldown=6;if(!creativeAircraft)entityData.set(ENERGY,energy()-12000);
        Vec3 start=position().add(0,.7,0),end=start.add(aim(p).scale(160));var block=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));end=block.getLocation();
        Entity nearest=null;double distance=start.distanceToSqr(end);
        for(Entity e:level().getEntities(this,new AABB(start,end).inflate(1.25),e->e!=p&&e.isAlive()&&e.isPickable())){
            var entry=e.getBoundingBox().inflate(1.25).clip(start,end);if(entry.isEmpty()||start.distanceToSqr(entry.get())>=distance)continue;
            var visibility=level().clip(new ClipContext(start,e.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));if(visibility.getType()!=HitResult.Type.MISS)continue;
            distance=start.distanceToSqr(entry.get());nearest=e;end=entry.get();
        }
        if(nearest!=null)nearest.hurt(level().damageSources().playerAttack(p),24);
        sound(HaijiaSounds.LASER.get(),.5f,1.65f);
        var beam=BlackDogWeapons.BEAM.get().create(level());if(beam!=null){beam.setPos(start);beam.configure(end.subtract(start),5);level().addFreshEntity(beam);}
    }
    private void missile(ServerPlayer p){
        if(missileCooldown>0||ammo()==0||energy()<4000)return;var m=Haijia.STRIKE.get().create(level());if(m==null)return;
        Vec3 direction=aim(p);m.setPos(position().add(direction.scale(3)).add(0,.4,0));m.configure(2,p.getUUID(),1);m.projectileSource(this,lockedTarget());m.setDeltaMovement(direction.scale(3.2).add(getDeltaMovement().scale(.3)));
        if(!level().addFreshEntity(m))return;sound(HaijiaSounds.MISSILE.get(),.85f,.75f);missileCooldown=MISSILE_INTERVAL;if(!creativeAircraft){entityData.set(AMMO,ammo()-1);entityData.set(ENERGY,energy()-4000);}
    }
    private void bomb(ServerPlayer p){
        if(bombCooldown>0||bombs()==0||energy()<2000||onGround())return;var bomb=Haijia.STRIKE.get().create(level());if(bomb==null)return;
        bomb.setPos(position().add(0,-.45,0));bomb.configure(4,p.getUUID(),1);bomb.projectileSource(this,null);bomb.setDeltaMovement(getDeltaMovement().add(0,-.15,0));
        if(!level().addFreshEntity(bomb))return;sound(HaijiaSounds.BOMB.get(),.65f,.8f);bombCooldown=12;if(!creativeAircraft){entityData.set(BOMBS,bombs()-1);entityData.set(ENERGY,energy()-2000);}
    }
    private void sound(net.minecraft.sounds.SoundEvent event,float volume,float pitch){level().playSound(null,getX(),getY(),getZ(),event,net.minecraft.sounds.SoundSource.PLAYERS,volume,pitch);}
    @Override public boolean hurt(DamageSource source,float amount){if(level().isClientSide||isRemoved()||!Float.isFinite(amount)||amount<=0||source.getEntity()==getFirstPassenger())return false;entityData.set(HEALTH,health()-amount);if(health()<=0){for(Entity p:java.util.List.copyOf(getPassengers())){p.stopRiding();if(p instanceof LivingEntity living)living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING,200));}discard();level().explode(this,getX(),getY(),getZ(),2,Level.ExplosionInteraction.NONE);}return true;}
    public IEnergyStorage energyPort(){return new IEnergyStorage(){public int receiveEnergy(int max,boolean simulate){int n=Math.max(0,Math.min(Math.min(8192,max),2000000-energy()));if(!simulate)entityData.set(ENERGY,energy()+n);return n;}public int extractEnergy(int n,boolean sim){return 0;}public int getEnergyStored(){return energy();}public int getMaxEnergyStored(){return 2000000;}public boolean canReceive(){return true;}public boolean canExtract(){return false;}};}
    public CompoundTag pack(){var t=new CompoundTag();t.putBoolean("CreativeAircraft",creativeAircraft);t.putInt("FlightEnergy",energy());t.putInt("Missiles",ammo());t.putInt("Bombs",bombs());t.putInt("Weapon",weapon());t.putFloat("Hull",health());t.putInt("LaserCooldown",laserCooldown);t.putInt("MissileCooldown",missileCooldown);t.putInt("BombCooldown",bombCooldown);return t;}
    public void unpack(CompoundTag t){creativeAircraft=t.getBoolean("CreativeAircraft");entityData.set(ENERGY,Mth.clamp(t.getInt("FlightEnergy"),0,2000000));entityData.set(AMMO,Mth.clamp(t.getInt("Missiles"),0,16));entityData.set(BOMBS,Mth.clamp(t.getInt("Bombs"),0,8));entityData.set(WEAPON,Mth.clamp(t.getInt("Weapon"),0,2));entityData.set(HEALTH,t.contains("Hull")?Mth.clamp(t.getFloat("Hull"),1,160):160);laserCooldown=Mth.clamp(t.getInt("LaserCooldown"),0,6);missileCooldown=Mth.clamp(t.getInt("MissileCooldown"),0,MISSILE_INTERVAL);bombCooldown=Mth.clamp(t.getInt("BombCooldown"),0,12);}
    @Override protected void addAdditionalSaveData(CompoundTag t){t.merge(pack());t.putFloat("Roll",roll());}
    @Override protected void readAdditionalSaveData(CompoundTag t){unpack(t);entityData.set(ROLL,Mth.clamp(t.getFloat("Roll"),-75,75));}
}
