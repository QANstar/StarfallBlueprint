package cn.starfall.blueprint;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A network-tracked, non-saving chassis attachment. It owns no items, energy or effects. */
public final class BestMechDrone extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(BestMechDrone.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(BestMechDrone.class, EntityDataSerializers.INT);
    private UUID ownerUuid;

    public BestMechDrone(EntityType<? extends BestMechDrone> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1);
        builder.define(KIND, 0);
    }

    void configure(BestMechEntity owner, BestMechDrones.Kind kind) {
        ownerUuid = owner.getUUID();
        entityData.set(OWNER, owner.getId());
        entityData.set(KIND, kind.ordinal());
    }

    public int ownerId() { return entityData.get(OWNER); }
    public BestMechDrones.Kind kind() {
        int value = entityData.get(KIND);
        var kinds = BestMechDrones.Kind.values();
        return value >= 0 && value < kinds.length ? kinds[value] : BestMechDrones.Kind.ATTACK;
    }

    public Vec3 anchor(BestMechEntity owner) {
        // Attack follows the existing combat orbit; the other roles occupy distinct phases.
        double phase = owner.tickCount * .12 + kind().ordinal() * Math.PI / 2;
        double radius = kind().ordinal() < 2 ? 1.7 : 1.8;
        double height = kind().ordinal() < 2 ? 2.7 : 2.4;
        return owner.position().add(Math.cos(phase) * radius, height, Math.sin(phase) * radius);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        Entity candidate = ownerUuid == null ? null : level.getEntity(ownerUuid);
        if (!(candidate instanceof BestMechEntity owner) || owner.isRemoved() || !owner.isAlive()
                || !BestMechDrones.installed(owner, kind())) {
            discard();
            return;
        }
        setPos(anchor(owner));
        setYRot(owner.getYRot());
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    // The registered type is noSave: the sole durable source is the mech's component state.
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
