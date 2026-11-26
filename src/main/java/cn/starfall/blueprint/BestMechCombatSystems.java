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

/** Server-owned combat, with physical components and cargo ammunition. */
public final class BestMechCombatSystems {
    public static final String STATE = "CombatSystems";
    public static final String MELEE="best_mech_melee_weapon", MACHINE_GUN="best_mech_machine_gun", SNIPER="best_mech_sniper", LASER="best_mech_laser", EXPLOSIVE="best_mech_explosive_launcher";
    public static final String ATTACK_DRONE="best_mech_attack_drone", DEFENSE_DRONE="best_mech_defense_drone";
    public static final String BULLET="best_mech_bullet", SNIPER_ROUND="best_mech_sniper_round", EXPLOSIVE_ROUND="best_mech_explosive_round";
    private static final List<String> WEAPONS=List.of(MELEE,MACHINE_GUN,SNIPER,LASER,EXPLOSIVE);
    private final BestMechEntity mech;
    private String weapon="", drone="";
    private int cooldown, droneCooldown;
    private boolean friendlyFire, terrainDamage;
    public boolean friendlyFire() { return friendlyFire; }
    public boolean terrainDamage() { return terrainDamage; }
    public void setFriendlyFire(boolean enabled) { friendlyFire=enabled; }
    public void setTerrainDamage(boolean enabled) { terrainDamage=enabled; }
    public BestMechCombatSystems(BestMechEntity mech) { this.mech=mech; }
    public String weapon() { return weapon.isEmpty()?LASER:weapon; }
    public String drone() { return drone; }
    public String installedWeaponRaw() { return weapon; }
    public String installedDroneRaw() { return drone; }
    public static String weaponKey(ItemStack stack) {
        if (stack.isEmpty()) return "";
        for (String key : WEAPONS) if (stack.is(CatalogueContent.item(key))) return key;
        return "";
    }
    public static String droneKey(ItemStack stack) {
        if (stack.isEmpty()) return "";
        if (stack.is(CatalogueContent.item(ATTACK_DRONE))) return ATTACK_DRONE;
        if (stack.is(CatalogueContent.item(DEFENSE_DRONE))) return DEFENSE_DRONE;
        return "";
    }
    void setWeaponDirect(String key) {
        weapon = WEAPONS.contains(key) ? key : "";
        mech.syncCombatAppearance(weapon, drone);
    }
    void setDroneDirect(String key) {
        drone = ATTACK_DRONE.equals(key) || DEFENSE_DRONE.equals(key) ? key : "";
        mech.syncCombatAppearance(weapon, drone);
    }
    public int cooldown() { return cooldown; }
    private boolean universal() { return BestMechProgression.UNIVERSAL.equals(mech.platformType()); }
    public void save(CompoundTag root) { var t=new CompoundTag(); t.putString("Weapon",weapon); t.putString("Drone",drone); t.putInt("Cooldown",cooldown); t.putInt("DroneCooldown",droneCooldown); if(friendlyFire)t.putBoolean("FriendlyFire",true); if(terrainDamage)t.putBoolean("TerrainDamage",true); root.put(STATE,t); }
    public void load(CompoundTag root) {
        var state = root.getCompound(STATE);
        String savedWeapon = state.getString("Weapon");
        String savedDrone = state.getString("Drone");
        weapon = WEAPONS.contains(savedWeapon) ? savedWeapon : "";
        drone = ATTACK_DRONE.equals(savedDrone) || DEFENSE_DRONE.equals(savedDrone) ? savedDrone : "";
        cooldown = Math.clamp(root.contains("WeaponCooldown") ? root.getInt("WeaponCooldown") : state.getInt("Cooldown"), 0, 100);
        droneCooldown = Math.clamp(state.getInt("DroneCooldown"), 0, 100);
        friendlyFire = state.getBoolean("FriendlyFire");
        terrainDamage = state.getBoolean("TerrainDamage");
        mech.syncCombatAppearance(weapon, drone);
    }
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
            mech.syncCombatAppearance(weapon, drone);
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
        mech.syncCombatAppearance(weapon, drone);
        p.displayClientMessage(Component.literal("战斗组件已卸载"),true); return true;
    }
    /** Conservative faction rule: no player, pet, passenger, team ally or neutral creature is ever a target. */
    public boolean hostile(Player p, LivingEntity e) {
        if(e==mech || e==p || !e.isAlive() || e.isSpectator() || e instanceof Player || e instanceof TamableAnimal || e.getVehicle()==mech || mech.isAlliedTo(e) || p.isAlliedTo(e)) return false;
        return e instanceof Monster || e instanceof Mob mob && (mob.getTarget()==p || mob.getTarget()==mech);
    }
    private boolean visible(Vec3 from, Vec3 to) { return mech.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mech)).getType()==HitResult.Type.MISS; }
    /** A drone or blast cannot shoot through another living body. */
    private boolean clearShot(Vec3 from, LivingEntity target, Player pilot, LivingEntity sourceBody) {
        Vec3 to=target.getEyePosition();
        if(!visible(from,to)) return false;
        for(var body:mech.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(.3),e->e!=mech && e!=pilot && e!=target && e!=sourceBody && e.isAlive()))
            if(body.getBoundingBox().inflate(.1).clip(from,to).isPresent()) return false;
        return true;
    }
    private boolean permitted(Player p, LivingEntity e) { return hostile(p,e) && mech.level().getWorldBorder().isWithinBounds(e.blockPosition()) && mech.level().mayInteract(p,e.blockPosition()); }
    private boolean damageAllowed(Player p, LivingEntity e, boolean finalWeapon) {
        return permitted(p,e) || finalWeapon && friendlyFire && e!=mech && e!=p && e.isAlive()
                && BestMechSupport.isFriendly(mech,e) && mech.level().getWorldBorder().isWithinBounds(e.blockPosition())
                && mech.level().mayInteract(p,e.blockPosition());
    }
    private boolean active(Player p,int cost) { return !mech.level().isClientSide && mech.combatCapable() && mech.getControllingPassenger()==p && mech.qualifiedFor(p) && mech.ready(cost) && mech.level().mayInteract(p,mech.blockPosition()); }
    public static String ammunition(String w) { return MACHINE_GUN.equals(w)?BULLET:SNIPER.equals(w)?SNIPER_ROUND:EXPLOSIVE.equals(w)?EXPLOSIVE_ROUND:""; }
    public int currentAmmoCount() {
        if(!mech.combatCapable() || mech.hasUpgrade(BestMechProgression.TIANPU)) return -1;
        String ammo = ammunition(weapon());
        if(ammo.isEmpty()) return -1;
        int total = 0;
        for(int i = 0; i < mech.cargoSlots(); i++) {
            var stack = mech.cargo().getItem(i);
            if(stack.is(CatalogueContent.item(ammo))) total += stack.getCount();
        }
        return total;
    }
    private boolean hasAmmo(String ammo) { if(ammo.isEmpty()) return true; for(int i=0;i<mech.cargoSlots();i++) if(mech.cargo().getItem(i).is(CatalogueContent.item(ammo))) return true; return false; }
    private boolean consume(String ammo) { if(ammo.isEmpty()) return true; for(int i=0;i<mech.cargoSlots();i++) { var stack=mech.cargo().getItem(i); if(stack.is(CatalogueContent.item(ammo))) { stack.shrink(1); mech.cargo().setChanged(); return true; } } return false; }
    public boolean fire(Player p) {
        String w=weapon(); boolean laser=LASER.equals(w), tianpu=mech.hasUpgrade(BestMechProgression.TIANPU), sustained=mech.hasUpgrade(BestMechProgression.SUSTAINED_FIRE);
        boolean creative = p.isCreative();
        int cost=creative?0:tianpu?500:laser?12000:MELEE.equals(w)?1500:2000;
        if(cooldown>0 || !active(p,cost)) return false;
        Vec3 start=p.getEyePosition(); double range=MELEE.equals(w)?(tianpu?5:4):SNIPER.equals(w)?80:mech.hasUpgrade(BestMechProgression.FIRE_CONTROL)||tianpu?64:40;
        if(EXPLOSIVE.equals(w)) {
            if(!creative && !tianpu && !hasAmmo(ammunition(w))) { mech.notifyPilot(p, Component.literal("货舱缺少对应弹药")); return false; }
            if(!(p instanceof net.minecraft.server.level.ServerPlayer server)
                    || !BestMechMissiles.launch(server,mech,start,p.getLookAngle(),range,tianpu,friendlyFire,terrainDamage,mech.hasUpgrade(BestMechProgression.FIRE_CONTROL))) return false;
            if(!creative && !tianpu) consume(ammunition(w));
            if(!creative) mech.spendSystems(cost,tianpu?0:35,tianpu?0:1);
            cooldown=tianpu?24:30;
            return true;
        }
        Vec3 end=start.add(p.getLookAngle().scale(range)); var block=mech.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(block.getType()!=HitResult.Type.MISS) end=block.getLocation();
        LivingEntity target=null; double nearest=start.distanceToSqr(end); Vec3 impact=end;
        // Every living body stops the ray, including friends: shots never tunnel through allies.
        for(var e:mech.level().getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(.3),e->e!=mech && e!=p && e.isAlive())) {
            var hit=e.getBoundingBox().inflate(.1).clip(start,end);
            if(hit.isPresent() && start.distanceToSqr(hit.get())<nearest) { nearest=start.distanceToSqr(hit.get()); target=e; impact=hit.get(); }
        }
        if(!tianpu && target!=null && !permitted(p,target)) { mech.notifyPilot(p, Component.literal("火控已拦截非敌对目标")); return false; }
        if(!creative && !tianpu && !hasAmmo(ammunition(w))) { mech.notifyPilot(p, Component.literal("货舱缺少对应弹药")); return false; }
        if(!creative && !tianpu) consume(ammunition(w));
        int heat=tianpu?0:MELEE.equals(w)?10:laser&&!sustained?65:35;
        if(!creative) mech.spendSystems(cost,heat,tianpu?0:1);
        cooldown=laser?(tianpu?8:sustained?12:20):MACHINE_GUN.equals(w)?(tianpu?4:5):SNIPER.equals(w)?(tianpu?30:40):EXPLOSIVE.equals(w)?(tianpu?24:30):(tianpu?15:20);
        if(target!=null && damageAllowed(p,target,tianpu)) target.hurt(mech.damageSources().playerAttack(p),MELEE.equals(w)?(tianpu?16:12):SNIPER.equals(w)?(tianpu?30:24):MACHINE_GUN.equals(w)?(tianpu?7:5):EXPLOSIVE.equals(w)?(tianpu?18:14):tianpu?18:10);
        if(target!=null && permitted(p,target) && (mech.hasUpgrade(BestMechProgression.FIRE_CONTROL)||tianpu)) for(var marked:mech.level().getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(6),e->permitted(p,e) && visible(mech.getEyePosition(),e.getEyePosition()))) marked.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,100,0));
        shotEffect(w,start,impact); return true;
    }
    private void shotEffect(String weapon, Vec3 from, Vec3 to) {
        if (!(mech.level() instanceof ServerLevel level)) return;
        BestMechSounds.weapon(mech, weapon);
        Vec3 dir = to.subtract(from);
        Vec3 norm = dir.lengthSqr() > 1e-6 ? dir.normalize() : mech.getLookAngle();
        Vec3 right = norm.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() > 1e-6) right = right.normalize();
        Vec3 muzzle = from.add(norm.scale(0.8)).add(right.scale(0.35)).add(0, -0.25, 0);
        if (LASER.equals(weapon)) {
            BlackDogWeapons.beam(level, muzzle, to, 5);
        } else if (SNIPER.equals(weapon)) {
            BlackDogWeapons.beam(level, muzzle, to, 1);
        } else if (MACHINE_GUN.equals(weapon)) {
            BlackDogWeapons.beam(level, muzzle, to, 2);
        } else {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, to.x, to.y, to.z, 2, .25, .15, .25, 0);
        }
        level.sendParticles(MELEE.equals(weapon) ? ParticleTypes.SWEEP_ATTACK : ParticleTypes.FLAME,
                muzzle.x, muzzle.y, muzzle.z, 2, .08, .08, .08, .01);
        level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 6, .14, .14, .14, .06);
    }
    private void beam(Vec3 from,Vec3 to) {
        if(!(mech.level() instanceof ServerLevel level)) return;
        BlackDogWeapons.beam(level, from, to, 2);
    }
    public void tick() {
        if(mech.level().isClientSide) return;
        if(cooldown>0) cooldown--; if(droneCooldown>0) droneCooldown--;
        if(!(mech.getControllingPassenger() instanceof Player p) || !active(p,0)) return;
        boolean attack=universal() || ATTACK_DRONE.equals(drone), defense=universal() || DEFENSE_DRONE.equals(drone);
        if(!attack && !defense) return;
        Vec3 orbit=mech.position().add(Math.cos(mech.tickCount*.12)*1.7,2.7,Math.sin(mech.tickCount*.12)*1.7);
        if(mech.tickCount%3==0) { var level=(ServerLevel)mech.level(); level.sendParticles(ParticleTypes.END_ROD,orbit.x,orbit.y,orbit.z,4,.12,.08,.12,0); if(defense) level.sendParticles(ParticleTypes.WAX_ON,mech.getX(),mech.getY()+1.3,mech.getZ(),5,1,.6,1,0); }
        if(!attack || droneCooldown>0 || !mech.ready(3000) || !visible(mech.getEyePosition(),orbit)) return;
        var targets=mech.level().getEntitiesOfClass(LivingEntity.class,mech.getBoundingBox().inflate(12),e->permitted(p,e) && clearShot(orbit,e,p,null));
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
