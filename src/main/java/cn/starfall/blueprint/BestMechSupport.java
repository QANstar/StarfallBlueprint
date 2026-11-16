package cn.starfall.blueprint;

import java.util.Comparator;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative support. Each pulse purchases one treatment from real cargo and energy. */
public final class BestMechSupport {
    public static final String REPAIR = "best_mech_repair_module";
    public static final String FRIENDLY_REPAIR = "best_mech_friendly_repair_module";
    private static final String LAST_PILOT = "BestSupportPilot";
    public static final String MAINTENANCE_DRONE = "best_mech_maintenance_drone";
    public static final String HEALING_DRONE = "best_mech_healing_drone";
    public static final String MEDICAL_SUPPLY = "best_mech_medical_supply";
    private BestMechSupport() {}

    public static boolean isModule(String key) {
        return REPAIR.equals(key) || FRIENDLY_REPAIR.equals(key) || MAINTENANCE_DRONE.equals(key) || HEALING_DRONE.equals(key);
    }
    /** No neutral mobs, unoccupied mechs or ownerless pets qualify as allies. */
    public static boolean isFriendly(BestMechEntity source, LivingEntity target) {
        if (!(source.getControllingPassenger() instanceof Player pilot) || !source.qualifiedFor(pilot)) return false;
        if (target == source || target == pilot) return true;
        if (target instanceof BestMechEntity other) {
            if (other.getControllingPassenger() instanceof Player driver) return allied(pilot, driver);
            return other.getPassengers().isEmpty() && other.onGround()
                    && other.getPersistentData().hasUUID(LAST_PILOT)
                    && pilot.getUUID().equals(other.getPersistentData().getUUID(LAST_PILOT));
        }
        if (target instanceof Player player) return allied(pilot, player);
        if (target instanceof TamableAnimal pet)
            return pet.isTame() && pet.getOwnerUUID() != null && (pilot.getUUID().equals(pet.getOwnerUUID())
                    || pet.getOwner() instanceof Player owner && allied(pilot, owner));
        if (target instanceof AbstractHorse horse)
            return horse.isTamed() && pilot.getUUID().equals(horse.getOwnerUUID());
        return false;
    }
    private static boolean allied(Player a, Player b) {
        return b.isAlive() && !b.isSpectator() && (a == b || a.getTeam() != null && a.getTeam() == b.getTeam());
    }
    /** Call once from the server entity tick. Universal chassis has a modest built-in repair/healing pulse. */
    public static void tick(BestMechEntity mech) {
        if (!(mech.level() instanceof ServerLevel level)) return;
        if (mech.getControllingPassenger() instanceof Player driver && mech.qualifiedFor(driver))
            mech.getPersistentData().putUUID(LAST_PILOT, driver.getUUID());
        if (!mech.engineeringCapable() || !mech.ready(2000)
                || !(mech.getControllingPassenger() instanceof Player pilot) || !mech.qualifiedFor(pilot)) return;
        String module = mech.moduleKey();
        boolean builtin = BestMechProgression.UNIVERSAL.equals(mech.platformType());
        boolean medical = HEALING_DRONE.equals(module);
        boolean drone = medical || MAINTENANCE_DRONE.equals(module);
        if (drone && mech.tickCount % 5 == 0) {
            double phase = mech.tickCount * .12;
            level.sendParticles(ParticleTypes.END_ROD, mech.getX() + Math.cos(phase) * 1.8,
                    mech.getY() + 2.4, mech.getZ() + Math.sin(phase) * 1.8, 1, 0, 0, 0, 0);
        }
        if (mech.tickCount % 40 != 0) return;
        if (!builtin && !isModule(module)) return;
        boolean basic = !isModule(module);
        double radius = drone ? 10 : builtin ? 5 : 4;
        // Lowest health fraction first; distance and UUID give stable tie-breaking.
        var targets = level.getEntitiesOfClass(LivingEntity.class, mech.getBoundingBox().inflate(radius),
                target -> target.isAlive() && target.getHealth() < target.getMaxHealth()
                        && mech.distanceToSqr(target) <= radius * radius && isFriendly(mech, target)
                        && (medical ? !(target instanceof BestMechEntity)
                            : REPAIR.equals(module) || basic ? target == mech
                            : target instanceof BestMechEntity && target != mech)
                        && mech.hasLineOfSight(target));
        if ((builtin || REPAIR.equals(module) || basic) && mech.getHealth() < mech.getMaxHealth() && !targets.contains(mech)) targets.add(mech);
        targets.sort(Comparator.comparingDouble((LivingEntity t) -> t.getHealth() / t.getMaxHealth())
                .thenComparingDouble(mech::distanceToSqr).thenComparing(t -> t.getUUID().toString()));
        for (LivingEntity target : targets) {
            String supply = target instanceof BestMechEntity ? "steel" : MEDICAL_SUPPLY;
            int slot = findSupply(mech, supply);
            if (slot < 0) continue;
            int cost = target instanceof BestMechEntity ? 4000 : 3000;
            if (!mech.consumeSupportEnergy(cost)) return;
            ItemStack stack = mech.cargo().getItem(slot);
            stack.shrink(1); mech.cargo().setChanged();
            target.heal(drone ? 4 : 2);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 1,
                    target.getZ(), 5, .4, .4, .4, 0);
            return;
        }
    }
    private static int findSupply(BestMechEntity mech, String key) {
        for (int slot = 0; slot < mech.cargoSlots(); slot++)
            if (mech.cargo().getItem(slot).is(CatalogueContent.item(key))) return slot;
        return -1;
    }
}
