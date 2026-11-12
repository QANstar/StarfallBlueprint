package cn.starfall.blueprint;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;

/** Server-owned targeting and timed salvos; no client-supplied target IDs. */
public final class BlackDogMissileLock {
    public static final int LOCK_TICKS=16, SALVO_SIZE=16, INTERVAL=3, COST=16000;
    private static final Map<Player,Lock> LOCKS=new WeakHashMap<>();
    private static final Map<Player,Salvo> SALVOS=new WeakHashMap<>();
    private static final class Lock { ItemStack watch; LivingEntity target; int ticks; Lock(ItemStack w){watch=w;} }
    private static final class Salvo { ItemStack watch; LivingEntity target; int remaining=SALVO_SIZE,delay; Salvo(ItemStack w,LivingEntity t){watch=w;target=t;} }
    private static boolean ready(Player p,ItemStack w){
        return p.isAlive()&&!p.isSpectator()&&BlackDogArmor.active(p)==w&&BlackDogArmor.complete(p,w)&&BlackDogArmor.has(w,"swarm");
    }
    private static boolean visible(Player p,LivingEntity target){
        return target!=p&&target.isAlive()&&!target.isSpectator()&&target.level()==p.level()&&p.distanceToSqr(target)<=64*64&&p.hasLineOfSight(target);
    }
    private static boolean aimed(Player p,LivingEntity t,boolean retaining){
        if(!visible(p,t))return false;
        Vec3 eye=p.getEyePosition(),end=eye.add(p.getLookAngle().scale(64));
        double margin=retaining?.6+eye.distanceTo(t.getBoundingBox().getCenter())*.08:.25;
        return t.getBoundingBox().inflate(margin).contains(eye)||t.getBoundingBox().inflate(margin).clip(eye,end).isPresent();
    }
    private static LivingEntity acquire(Player p){
        Vec3 eye=p.getEyePosition(),end=eye.add(p.getLookAngle().scale(64));
        LivingEntity best=null;double nearest=Double.MAX_VALUE;
        for(var t:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(eye,end).inflate(2),e->aimed(p,e,false))){
            Vec3 hit=t.getBoundingBox().inflate(.25).clip(eye,end).orElse(eye);double distance=eye.distanceToSqr(hit);
            if(distance<nearest){best=t;nearest=distance;}
        }
        return best;
    }
    public static BlackDogNetworking.TargetLock targetLock(Player p){
        var lock=LOCKS.get(p);
        return lock==null||lock.target==null?BlackDogNetworking.TargetLock.empty():new BlackDogNetworking.TargetLock(lock.target.getId(),lock.target.getUUID(),lock.ticks);
    }
    private static void syncMarker(Player p){
        if(p instanceof net.minecraft.server.level.ServerPlayer server&&server.connection.getConnection().isConnected())
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(server,targetLock(p));
    }
    public static void begin(Player p,ItemStack watch){LOCKS.put(p,new Lock(watch));syncMarker(p);}
    public static void hold(Player p){
        if(p.level().isClientSide)return;
        var lock=LOCKS.get(p);
        if(lock==null||!ready(p,lock.watch)||!BlackDogArmor.selectedSystem(lock.watch).equals("swarm")){cancel(p);return;}
        if(lock.target!=null&&!aimed(p,lock.target,true)){lock.target=null;lock.ticks=0;}
        if(lock.target==null){lock.target=acquire(p);lock.ticks=0;}
        if(lock.target==null){syncMarker(p);status(p,"lc.swarm_searching",0);return;}
        if(lock.ticks<LOCK_TICKS){
            lock.ticks++;
            if(lock.ticks==LOCK_TICKS)BlackDogWeapons.sound(p,SoundEvents.NOTE_BLOCK_PLING.value(),.8f,1.8f);
            else if(lock.ticks%4==0)BlackDogWeapons.sound(p,SoundEvents.NOTE_BLOCK_HAT.value(),.4f,.8f+lock.ticks*.04f);
        }
        syncMarker(p);status(p,lock.ticks==LOCK_TICKS?"lc.swarm_locked":"lc.swarm_locking",lock.ticks*100/LOCK_TICKS);
    }
    private static void status(Player p,String key,int progress){p.displayClientMessage(Component.translatable(key,progress),true);}
    public static void release(Player p){
        if(p.level().isClientSide)return;
        var lock=LOCKS.remove(p);
        if(lock!=null)syncMarker(p);
        if(lock==null||lock.ticks<LOCK_TICKS||!ready(p,lock.watch)||!BlackDogArmor.selectedSystem(lock.watch).equals("swarm")||!aimed(p,lock.target,true))return;
        if(SALVOS.containsKey(p)||!BlackDogArmor.spend(lock.watch,COST)){Lingchuan.fail(p,"lc.power_missing");return;}
        SALVOS.put(p,new Salvo(lock.watch,lock.target));BlackDogArmor.markCombat(p,lock.watch);
        p.getCooldowns().addCooldown(CatalogueContent.item("blackdog_fire_control"),60);
    }
    public static void tick(Player p){
        var salvo=SALVOS.get(p);if(salvo==null)return;
        if(!ready(p,salvo.watch)||!salvo.target.isAlive()||salvo.target.level()!=p.level()||BlackDogArmor.charge(salvo.watch)<100){cancel(p);return;}
        if(salvo.delay-->0)return;
        var level=(ServerLevel)p.level();var missile=BlackDogWeapons.MISSILE.get().create(level);
        if(missile!=null){
            int index=SALVO_SIZE-salvo.remaining;Vec3 forward=p.getLookAngle(),right=forward.cross(new Vec3(0,1,0)).normalize();
            if(right.lengthSqr()<.01)right=new Vec3(1,0,0);
            Vec3 direction=forward.scale(.45).add(right.scale(index%2==0?-.65:.65)).add(0,.6+(index%3)*.1,0).normalize();
            missile.setOwner(p);missile.track(salvo.target);missile.setPos(p.getEyePosition().add(right.scale(index%2==0?-.7:.7)).add(0,.35,0));missile.setDeltaMovement(direction.scale(.8));level.addFreshEntity(missile);
            BlackDogWeapons.sound(p,SoundEvents.FIREWORK_ROCKET_LAUNCH,.65f,.8f+(index%3)*.1f);
        }
        salvo.delay=INTERVAL-1;if(--salvo.remaining==0)SALVOS.remove(p);
    }
    public static void cancel(Player p){if(LOCKS.remove(p)!=null)syncMarker(p);SALVOS.remove(p);}
    private BlackDogMissileLock(){}
}
