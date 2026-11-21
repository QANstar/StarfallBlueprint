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
    int returnSlot = 45;
    private boolean returning;
    private java.util.UUID payloadOwner;
    public EsperProjectile(EntityType<? extends EsperProjectile> type, Level level) { super(type, level); }
    void prepare(LivingEntity owner, ItemStack stack, int strength, Vec3 from, boolean conjured) {
        setOwner(owner); payloadOwner = owner.getUUID(); setItem(stack); this.strength = strength;
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
            // The carried object is recalled; the spatial impact supplies the damage.
            for (var target : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2 + strength * .35),
                    e -> getOwner() != null && EsperEffects.canAffect(getOwner(), e)))
                target.hurt(damageSources().thrown(this, getOwner()), 8 + strength * 3);
        } else if (hit instanceof EntityHitResult result) {
            double[] damage = {3 + strength * .9};
            var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
            if (modifiers != null) modifiers.forEach(EquipmentSlot.MAINHAND, (attribute, mod) -> {
                if (attribute.equals(Attributes.ATTACK_DAMAGE)) damage[0] += mod.amount();
            });
            result.getEntity().hurt(damageSources().thrown(this, getOwner()), (float)Math.clamp(damage[0],1,60));
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel server)
            EsperNetworking.fracture(server, EsperNetworking.Pattern.SHATTER, hit.getLocation(),
                    getDeltaMovement().normalize(), .62F, 15);
        recall();
    }
    void recall() {
        if (conjured) { discard(); return; } // Old saves never turn conjured blades into real items.
        returning = true;
        setDeltaMovement(Vec3.ZERO); setNoGravity(true); noPhysics = true;
        if (!(level() instanceof net.minecraft.server.level.ServerLevel world)) return;
        var id = payloadOwner != null ? payloadOwner : getOwner() == null ? null : getOwner().getUUID();
        var owner = getOwner() instanceof net.minecraft.server.level.ServerPlayer live && !live.isRemoved()
                ? live : id == null ? null : world.getServer().getPlayerList().getPlayer(id);
        if (owner == null) return; // Persist the payload until its owner returns.
        var storage = new EsperStorage(owner, Esper.level(Esper.data(owner), Esper.Skill.STORAGE));
        var remaining = storage.returnAmmo(returnSlot, getItem());
        if (!remaining.isEmpty()) { setItem(remaining); return; }
        discard();
    }
    @Override public void tick() {
        if (returning) { if (!level().isClientSide) recall(); return; }
        if (!level().isClientSide && !level().hasChunkAt(net.minecraft.core.BlockPos.containing(position().add(getDeltaMovement())))) {
            recall(); return;
        }
        super.tick();
        if (!level().isClientSide && !isRemoved() && tickCount >= 100) recall();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Strength", strength);
        tag.putBoolean("Conjured", conjured);
        if (payloadOwner != null) tag.putUUID("PayloadOwner", payloadOwner);
        tag.putBoolean("Returning", returning); tag.putInt("ReturnSlot", returnSlot);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); strength = Math.clamp(tag.getInt("Strength"), 1, Esper.MAX_LEVEL);
        conjured = tag.getBoolean("Conjured");
        payloadOwner = tag.hasUUID("PayloadOwner") ? tag.getUUID("PayloadOwner") : tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        returning = tag.getBoolean("Returning"); returnSlot = Math.clamp(tag.getInt("ReturnSlot"),45,53);
    }
}
