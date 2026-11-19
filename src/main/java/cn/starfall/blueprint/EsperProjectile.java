package cn.starfall.blueprint;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

public final class EsperProjectile extends ThrowableItemProjectile {
    private int strength = 1;
    private boolean conjured;
    public EsperProjectile(EntityType<? extends EsperProjectile> type, Level level) { super(type, level); }
    void prepare(LivingEntity owner, ItemStack stack, int strength, Vec3 from, boolean conjured) {
        setOwner(owner); setItem(stack); this.strength = strength;
        setPos(from);
        this.conjured = conjured;
    }
    @Override protected double getDefaultGravity() { return .012; }
    @Override protected Item getDefaultItem() { return Items.IRON_NUGGET; }
    @Override protected boolean canHitEntity(Entity entity) {
        return entity != getOwner() && (getOwner() == null || EsperEffects.canAffect(getOwner(), entity))
                && super.canHitEntity(entity);
    }
    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level().isClientSide || isRemoved()) return;
        ItemStack stack = getItem();
        if (stack.is(Items.TNT)) {
            Vec3 at = hit.getLocation();
            level().explode(this, damageSources().explosion(this, getOwner()), null,
                    at.x, at.y, at.z, 2F + strength * .35F, false, Level.ExplosionInteraction.NONE);
        } else {
            if (hit instanceof EntityHitResult result && canHitEntity(result.getEntity())) {
                double[] damage = {conjured ? 2 + strength * .6 : 3 + strength * .9};
                var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
                if (!conjured && modifiers != null) modifiers.forEach(EquipmentSlot.MAINHAND, (attribute, mod) -> {
                    if (attribute.equals(Attributes.ATTACK_DAMAGE)) damage[0] += mod.amount();
                });
                result.getEntity().hurt(damageSources().thrown(this, getOwner()), (float) Math.clamp(damage[0], 1, 60));
            }
            if (!conjured) spawnAtLocation(stack.copy());
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel server)
            EsperNetworking.fracture(server, EsperNetworking.Pattern.SHATTER, hit.getLocation(),
                    getDeltaMovement().normalize(), .62F, 15);
        discard();
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && !isRemoved()) {
            if (tickCount >= 100) {
                if (!conjured && !getItem().is(Items.TNT)) spawnAtLocation(getItem().copy());
                discard();
            }
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Strength", strength);
        tag.putBoolean("Conjured", conjured);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); strength = Math.clamp(tag.getInt("Strength"), 1, Esper.MAX_LEVEL);
        conjured = tag.getBoolean("Conjured");
    }
}
