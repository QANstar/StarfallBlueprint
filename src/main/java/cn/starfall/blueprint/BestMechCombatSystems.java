package cn.starfall.blueprint;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Server-owned combat, with physical components and cargo ammunition. Never modifies blocks. */
public final class BestMechCombatSystems {
    public static final String STATE = "CombatSystems";
    public static final String MELEE="best_mech_melee_weapon", MACHINE_GUN="best_mech_machine_gun", SNIPER="best_mech_sniper", LASER="best_mech_laser", EXPLOSIVE="best_mech_explosive_launcher";
    public static final String ATTACK_DRONE="best_mech_attack_drone", DEFENSE_DRONE="best_mech_defense_drone";
    public static final String BULLET="best_mech_bullet", SNIPER_ROUND="best_mech_sniper_round", EXPLOSIVE_ROUND="best_mech_explosive_round";
    private static final List<String> WEAPONS=List.of(MELEE,MACHINE_GUN,SNIPER,LASER,EXPLOSIVE);
    private final BestMechEntity mech;
    private String weapon="", drone="";
    private int cooldown, droneCooldown;
    public BestMechCombatSystems(BestMechEntity mech) { this.mech=mech; }
    public String weapon() { return weapon.isEmpty()?LASER:weapon; }
    public String drone() { return drone; }
    public int cooldown() { return cooldown; }
    private boolean universal() { return BestMechProgression.UNIVERSAL.equals(mech.platformType()); }
    public void save(CompoundTag root) { var t=new CompoundTag(); t.putString("Weapon",weapon); t.putString("Drone",drone); t.putInt("Cooldown",cooldown); t.putInt("DroneCooldown",droneCooldown); root.put(STATE,t); }
    public void load(CompoundTag root) { var t=root.getCompound(STATE); weapon=WEAPONS.contains(t.getString("Weapon"))?t.getString("Weapon"):""; String d=t.getString("Drone"); drone=ATTACK_DRONE.equals(d)||DEFENSE_DRONE.equals(d)?d:""; cooldown=Math.max(0,Math.min(100,root.contains("WeaponCooldown")?root.getInt("WeaponCooldown"):t.getInt("Cooldown"))); droneCooldown=Math.max(0,Math.min(100,t.getInt("DroneCooldown"))); }
    private boolean service(Player p) { return !mech.level().isClientSide && mech.combatCapable() && mech.isolated() && mech.isAlive() && mech.qualifiedFor(p) && p.level()==mech.level() && mech.distanceToSqr(p)<=64 && mech.getPassengers().isEmpty() && mech.onGround() && mech.getDeltaMovement().lengthSqr()<.01 && !mech.isInWater() && !mech.isInLava() && mech.level().mayInteract(p,mech.blockPosition()); }
    public boolean install(Player p, InteractionHand hand) {
        ItemStack held=p.getItemInHand(hand);
        // Sneak with a wrench (shears): main hand removes weapon, off hand removes drone.
        if(p.isShiftKeyDown() && held.is(Items.SHEARS)) return remove(p,hand==InteractionHand.OFF_HAND);
        for(String key:List.of(MELEE,MACHINE_GUN,SNIPER,LASER,EXPLOSIVE,ATTACK_DRONE,DEFENSE_DRONE)) {
            if(!held.is(CatalogueContent.item(key))) continue;
            if(!service(p)) { p.displayClientMessage(Component.literal("需要有权限的驾驶员，在近处隔离并停稳空载战斗机甲后安装"),true); return true; }
            boolean isDrone=key.equals(ATTACK_DRONE)||key.equals(DEFENSE_DRONE);
            if(!(isDrone?drone:weapon).isEmpty()) { p.displayClientMessage(Component.literal("对应战斗槽已占用"),true); return true; }
            if(isDrone) drone=key; else weapon=key;
            held.shrink(1); p.getInventory().setChanged(); p.displayClientMessage(Component.literal("战斗组件已安装"),true); return true;
        }
        return false;
    }
    public boolean remove(Player p, boolean removeDrone) {
        if(!service(p)) return false; String key=removeDrone?drone:weapon;
        if(key.isEmpty()) return false;
        int slot=p.getInventory().getFreeSlot(); if(slot<0) { p.displayClientMessage(Component.literal("背包需要一个空位"),true); return true; }
        p.getInventory().setItem(slot,CatalogueContent.stack(key,1)); p.getInventory().setChanged();
        if(removeDrone) drone=""; else weapon="";
        p.displayClientMessage(Component.literal("战斗组件已卸载"),true); return true;
    }
    /** Conservative faction rule: no player, pet, passenger, team ally or neutral creature is ever a target. */
    public boolean hostile(Player p, LivingEntity e) {
        if(e==mech || e==p || !e.isAlive() || e.isSpectator() || e instanceof Player || e instanceof TamableAnimal || e.getVehicle()==mech || mech.isAlliedTo(e) || p.isAlliedTo(e)) return false;
        return e instanceof Monster || e instanceof Mob mob && (mob.getTarget()==p || mob.getTarget()==mech);
    }
    private boolean visible(Vec3 from, Vec3 to) { return mech.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mech)).getType()==HitResult.Type.MISS; }
    private boolean permitted(Player p, LivingEntity e) { return hostile(p,e) && mech.level().getWorldBorder().isWithinBounds(e.blockPosition()) && mech.level().mayInteract(p,e.blockPosition()); }
    private boolean active(Player p,int cost) { return !mech.level().isClientSide && mech.combatCapable() && mech.getControllingPassenger()==p && mech.qualifiedFor(p) && mech.ready(cost) && mech.level().mayInteract(p,mech.blockPosition()); }
    public static String ammunition(String w) { return MACHINE_GUN.equals(w)?BULLET:SNIPER.equals(w)?SNIPER_ROUND:EXPLOSIVE.equals(w)?EXPLOSIVE_ROUND:""; }
    private boolean consume(String ammo) { if(ammo.isEmpty()) return true; for(int i=0;i<mech.cargoSlots();i++) { var stack=mech.cargo().getItem(i); if(stack.is(CatalogueContent.item(ammo))) { stack.shrink(1); mech.cargo().setChanged(); return true; } } return false; }
    public boolean fire(Player p) {
        String w=weapon(); boolean laser=LASER.equals(w), tianpu=mech.hasUpgrade(BestMechProgression.TIANPU), sustained=mech.hasUpgrade(BestMechProgression.SUSTAINED_FIRE); int cost=laser?(tianpu?24000:12000):MELEE.equals(w)?1500:2000;
        if(cooldown>0 || !active(p,cost)) return false;
        Vec3 start=p.getEyePosition(); double range=MELEE.equals(w)?4:SNIPER.equals(w)?80:mech.hasUpgrade(BestMechProgression.FIRE_CONTROL)?64:40;
        Vec3 end=start.add(p.getLookAngle().scale(range)); var block=mech.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(block.getType()!=HitResult.Type.MISS) end=block.getLocation();
        LivingEntity target=null; double nearest=start.distanceToSqr(end); Vec3 impact=end;
        // Every living body stops the ray, including friends: shots never tunnel through allies.
        for(var e:mech.level().getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(.3),e->e!=mech && e!=p && e.isAlive())) {
            var hit=e.getBoundingBox().inflate(.1).clip(start,end);
            if(hit.isPresent() && start.distanceToSqr(hit.get())<nearest) { nearest=start.distanceToSqr(hit.get()); target=e; impact=hit.get(); }
        }
        if(!consume(ammunition(w))) { p.displayClientMessage(Component.literal("货舱缺少对应弹药"),true); return false; }
        mech.spendSystems(cost,MELEE.equals(w)?10:laser&&!sustained?65:35,1); cooldown=laser?(tianpu?8:sustained?12:20):MACHINE_GUN.equals(w)?5:SNIPER.equals(w)?40:EXPLOSIVE.equals(w)?30:20;
        if(target!=null && permitted(p,target)) target.hurt(mech.damageSources().playerAttack(p),MELEE.equals(w)?12:SNIPER.equals(w)?24:MACHINE_GUN.equals(w)?5:EXPLOSIVE.equals(w)?14:tianpu?18:10);
        if(target!=null && permitted(p,target) && (mech.hasUpgrade(BestMechProgression.FIRE_CONTROL)||tianpu)) for(var marked:mech.level().getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(6),e->permitted(p,e) && visible(mech.getEyePosition(),e.getEyePosition()))) marked.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,100,0));
        if(EXPLOSIVE.equals(w)) { for(var e:mech.level().getEntitiesOfClass(LivingEntity.class,new AABB(impact,impact).inflate(3))) if(e!=target && permitted(p,e) && visible(impact.add(0,.05,0),e.getEyePosition())) e.hurt(mech.damageSources().playerAttack(p),8); }
        beam(start,impact); return true;
    }
    private void beam(Vec3 from,Vec3 to) { if(!(mech.level() instanceof ServerLevel level)) return; int n=Math.min(100,Math.max(1,(int)(from.distanceTo(to)*3))); for(int i=0;i<=n;i++) { Vec3 v=from.lerp(to,(double)i/n); level.sendParticles(ParticleTypes.ELECTRIC_SPARK,v.x,v.y,v.z,1,0,0,0,0); } }
    public void tick() {
        if(mech.level().isClientSide) return;
        if(cooldown>0) cooldown--; if(droneCooldown>0) droneCooldown--;
        if(!(mech.getControllingPassenger() instanceof Player p) || !active(p,0)) return;
        boolean attack=universal() || ATTACK_DRONE.equals(drone), defense=universal() || DEFENSE_DRONE.equals(drone);
        if(!attack && !defense) return;
        Vec3 orbit=mech.position().add(Math.cos(mech.tickCount*.12)*1.7,2.7,Math.sin(mech.tickCount*.12)*1.7);
        if(mech.tickCount%3==0) { var level=(ServerLevel)mech.level(); level.sendParticles(ParticleTypes.END_ROD,orbit.x,orbit.y,orbit.z,4,.12,.08,.12,0); if(defense) level.sendParticles(ParticleTypes.WAX_ON,mech.getX(),mech.getY()+1.3,mech.getZ(),5,1,.6,1,0); }
        if(!attack || droneCooldown>0 || !mech.ready(3000) || !visible(mech.getEyePosition(),orbit)) return;
        var targets=mech.level().getEntitiesOfClass(LivingEntity.class,mech.getBoundingBox().inflate(12),e->permitted(p,e) && visible(orbit,e.getEyePosition()));
        targets.sort(java.util.Comparator.comparingDouble(mech::distanceToSqr));
        if(!targets.isEmpty()) { var target=targets.getFirst(); mech.spendSystems(3000,8,0); droneCooldown=30; target.hurt(mech.damageSources().playerAttack(p),4); beam(orbit,target.getEyePosition()); }
    }
    public float reduceDamage(DamageSource source,float amount) {
        if(amount<=0 || mech.level().isClientSide || !(source.getEntity() instanceof LivingEntity enemy)) return amount;
        for(var provider:mech.level().getEntitiesOfClass(BestMechEntity.class,mech.getBoundingBox().inflate(8))) {
            var system=provider.combatSystems();
            if(!(provider.getControllingPassenger() instanceof Player p) || !(system.universal() || DEFENSE_DRONE.equals(system.drone)) || !system.active(p,2500) || !BestMechSupport.isFriendly(provider,mech) || !system.hostile(p,enemy) || !system.visible(provider.getEyePosition(),mech.getEyePosition())) continue;
            provider.spendSystems(2500,5,0); system.beam(provider.getEyePosition(),mech.getEyePosition()); return amount*.75f;
        }
        return amount;
    }
}
