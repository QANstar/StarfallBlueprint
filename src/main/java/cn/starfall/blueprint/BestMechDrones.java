package cn.starfall.blueprint;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Server-side visual attachments. The mech component state remains authoritative. */
@EventBusSubscriber(modid = StarfallBlueprint.ID)
public final class BestMechDrones {
    public enum Kind { ATTACK, DEFENSE, MAINTENANCE, HEALING }

    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, StarfallBlueprint.ID);
    public static final DeferredHolder<EntityType<?>, EntityType<BestMechDrone>> TYPE = TYPES.register("best_mech_drone", () -> EntityType.Builder
            .<BestMechDrone>of(BestMechDrone::new, MobCategory.MISC)
            .sized(.65f, .3f).noSave().clientTrackingRange(12).updateInterval(1)
            .build(StarfallBlueprint.id("best_mech_drone").toString()));

    public static void register(IEventBus bus) { TYPES.register(bus); }

    // Entries are explicitly cleared when the owner leaves its level.
    private static final Map<BestMechEntity, BestMechDrone[]> DRONES = new WeakHashMap<>();

    public static boolean installed(BestMechEntity mech, Kind kind) {
        boolean universal = BestMechProgression.UNIVERSAL.equals(mech.platformType());
        return switch (kind) {
            case ATTACK -> mech.combatCapable() && (universal || BestMechCombatSystems.ATTACK_DRONE.equals(mech.combatSystems().drone()));
            case DEFENSE -> mech.combatCapable() && (universal || BestMechCombatSystems.DEFENSE_DRONE.equals(mech.combatSystems().drone()));
            case MAINTENANCE -> mech.engineeringCapable() && BestMechSupport.MAINTENANCE_DRONE.equals(mech.moduleKey());
            case HEALING -> mech.engineeringCapable() && BestMechSupport.HEALING_DRONE.equals(mech.moduleKey());
        };
    }

    @SubscribeEvent public static void onTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof BestMechEntity mech && !mech.level().isClientSide) reconcile(mech);
    }

    /** Called after the server mech tick, including after component changes or world loading. */
    public static void reconcile(BestMechEntity mech) {
        if (mech.level().isClientSide) return;
        if (mech.isRemoved() || !mech.isAlive()) { clear(mech); return; }
        BestMechDrone[] drones = DRONES.computeIfAbsent(mech, ignored -> new BestMechDrone[Kind.values().length]);
        for (Kind kind : Kind.values()) {
            int index = kind.ordinal();
            BestMechDrone drone = drones[index];
            if (!installed(mech, kind)) {
                if (drone != null) drone.discard();
                drones[index] = null;
                continue;
            }
            if (drone != null && !drone.isRemoved() && drone.level() == mech.level()) continue;
            drone = TYPE.get().create(mech.level());
            if (drone == null) { drones[index] = null; continue; }
            drone.configure(mech, kind);
            drone.setPos(drone.anchor(mech));
            drones[index] = mech.level().addFreshEntity(drone) ? drone : null;
        }
    }

    @SubscribeEvent public static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof BestMechEntity mech) clear(mech);
    }

    private static void clear(BestMechEntity mech) {
        BestMechDrone[] drones = DRONES.remove(mech);
        if (drones != null) for (BestMechDrone drone : drones) if (drone != null) drone.discard();
    }

    private BestMechDrones() {}
}
