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
    public EsperProjectile(EntityType<? extends EsperProjectile> type, Level level) { super(type, level); }
    void prepare(LivingEntity owner, ItemStack stack, int strength) {
        setOwner(owner); setItem(stack); this.strength = strength;
        setPos(owner.getEyePosition().subtract(0, .12, 0));
    }
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
                double[] damage = {2 + strength * .75};
                var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
                if (modifiers != null) modifiers.forEach(EquipmentSlot.MAINHAND, (attribute, mod) -> {
                    if (attribute.equals(Attributes.ATTACK_DAMAGE)) damage[0] += mod.amount();
                });
                result.getEntity().hurt(damageSources().thrown(this, getOwner()), (float) Math.clamp(damage[0], 1, 60));
            }
            spawnAtLocation(stack.copy());
        }
        discard();
    }
    @Override public void tick() {
        super.tick();
        if (level() instanceof net.minecraft.server.level.ServerLevel server && !isRemoved()) {
            if (tickCount % 2 == 0) server.sendParticles(EsperEffects.VIOLET, getX(), getY(), getZ(), 2, .06, .06, .06, 0);
            if (tickCount >= 100) {
                if (!getItem().is(Items.TNT)) spawnAtLocation(getItem().copy());
                discard();
            }
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("Strength", strength); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); strength = Math.clamp(tag.getInt("Strength"), 1, Esper.MAX_LEVEL);
    }
}
