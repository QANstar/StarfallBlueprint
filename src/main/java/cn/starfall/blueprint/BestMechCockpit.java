package cn.starfall.blueprint;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** A mounted pilot occupies the hull. Protection follows the live passenger relationship. */
@EventBusSubscriber(modid = StarfallBlueprint.ID)
public final class BestMechCockpit {
    private static BestMechEntity occupiedHull(Entity target) {
        return target instanceof Player && target.getVehicle() instanceof BestMechEntity hull
                && hull.isAlive() && hull.getControllingPassenger() == target ? hull : null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void intercept(LivingIncomingDamageEvent event) {
        var hull = occupiedHull(event.getEntity());
        var source = event.getSource();
        if (hull == null || hull.level().isClientSide || source.is(DamageTypes.GENERIC_KILL)
                || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.STARVE)) return;
        event.setCanceled(true);
        hull.hurtOccupant(source, event.getAmount());
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        // Exclude the passenger before both damage and knockback, including blasts that destroy the hull.
        var targets = event.getAffectedEntities();
        targets.removeIf(entity -> {
            var hull = occupiedHull(entity);
            return hull != null && targets.contains(hull);
        });
    }

    private BestMechCockpit() {}
}
