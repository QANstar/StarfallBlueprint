package cn.starfall.blueprint;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registration and server-side launch boundary for the mech explosive launcher. */
public final class BestMechMissiles {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, StarfallBlueprint.ID);
    public static final DeferredHolder<EntityType<?>, EntityType<BestMechMissile>> TYPE = TYPES.register("best_mech_missile",
            () -> EntityType.Builder.<BestMechMissile>of(BestMechMissile::new, MobCategory.MISC)
                    .sized(.25f, .25f).noSave().clientTrackingRange(12).updateInterval(1).build("best_mech_missile"));
    public static void register(IEventBus bus) { TYPES.register(bus); }
    /** Returns false without changing ammunition, power or cooldown when spawning fails. */
    public static boolean launch(ServerPlayer pilot, BestMechEntity mech, Vec3 start, Vec3 direction,
                                 double range, boolean tianpu, boolean friendlyFire, boolean terrainDamage, boolean fireControl) {
        if (pilot.level().isClientSide || mech.level() != pilot.level() || !pilot.isAlive() || pilot.isSpectator()
                || !mech.isAlive() || !mech.qualifiedFor(pilot) || mech.getControllingPassenger() != pilot
                || !Double.isFinite(range) || range <= 0 || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1.0e-8
                || !Double.isFinite(start.x) || !Double.isFinite(start.y) || !Double.isFinite(start.z)
                || !pilot.level().hasChunkAt(net.minecraft.core.BlockPos.containing(start))
                || !pilot.level().getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(start))
                || !pilot.level().mayInteract(pilot, net.minecraft.core.BlockPos.containing(start))) return false;
        var missile = TYPE.get().create(pilot.level());
        if (missile == null) return false;
        missile.configure(pilot, mech, Math.min(range, 80), tianpu, friendlyFire, terrainDamage, fireControl);
        missile.setPos(start);
        Vec3 motion=direction.normalize().scale(1.5);
        missile.setDeltaMovement(motion);
        missile.setYRot((float)Math.toDegrees(Math.atan2(-motion.x,motion.z)));
        missile.setXRot((float)-Math.toDegrees(Math.atan2(motion.y,motion.horizontalDistance())));
        if (!pilot.level().addFreshEntity(missile)) return false;
        pilot.level().playSound(null,pilot.blockPosition(),net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH,net.minecraft.sounds.SoundSource.PLAYERS,.8f,.7f);
        return true;
    }
    private BestMechMissiles() {}
}
