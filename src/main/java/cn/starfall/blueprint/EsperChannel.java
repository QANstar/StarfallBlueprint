package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;

/** Server-owned channels. Heartbeats stop stuck keys, disconnects and stale loadouts. */
final class EsperChannel {
    private static final Map<ServerPlayer, Channel> CHANNELS = new WeakHashMap<>();
    static final int GROW_TICKS = 80;
    private static final class Channel {
        final ServerLevel world;
        final int slot, level;
        final Esper.Skill skill;
        final long start;
        final Vec3 focus;
        final LivingEntity selected;
        final Object bindingToken = new Object();
        long heartbeat;
        int elapsed;
        long lastTick = Long.MIN_VALUE;
        Channel(ServerPlayer p, int slot, Esper.Skill skill, int level, long now) {
            world = p.serverLevel(); this.slot = slot; this.skill = skill; this.level = level;
            start = heartbeat = now;
            Vec3 aimed = skill == Esper.Skill.COLLAPSE ? EsperEffects.trace(p, 12 + level * 2) : p.getEyePosition();
            selected = skill == Esper.Skill.COLLAPSE && EsperEffects.target(p, aimed,
                    e -> EsperEffects.canAffect(p, e) && !e.isInvisibleTo(p)) instanceof LivingEntity living ? living : null;
            focus = selected == null ? aimed.subtract(p.getLookAngle().scale(.5)) : selected.getBoundingBox().getCenter();
        }
    }
    static boolean active(ServerPlayer p) { return CHANNELS.containsKey(p); }
    static boolean begin(ServerPlayer p, int slot, Esper.Skill skill, int level, long now) {
        if (active(p) || EsperVolley.active(p)) return false;
        Channel c = new Channel(p, slot, skill, level, now);
        if (skill == Esper.Skill.COLLAPSE && c.selected == null && c.focus.distanceToSqr(p.getEyePosition()) < 4) return false;
        CHANNELS.put(p, c);
        if (skill == Esper.Skill.SLASH) EsperEffects.slash(p, level);
        else {
            if (c.selected != null) EsperEffects.bind(p, c.selected, 8, c.bindingToken);
            showSphere(p, c, radius(level, 0));
        }
        return true;
    }
    static void hold(ServerPlayer p, int slot, long now) {
        Channel c = CHANNELS.get(p);
        if (c != null && c.slot == slot) c.heartbeat = now;
    }
    static void cancel(ServerPlayer p) {
        Channel c = CHANNELS.remove(p);
        if (c != null && c.selected != null) EsperEffects.unbind(c.selected, c.bindingToken);
    }
    static boolean release(ServerPlayer p, int slot, long now) {
        Channel c = CHANNELS.get(p);
        if (c == null || c.slot != slot) return false;
        if (valid(p, Esper.data(p), c, now) && c.skill == Esper.Skill.COLLAPSE) explode(p, c);
        cancel(p);
        return true;
    }
    private static boolean valid(ServerPlayer p, CompoundTag d, Channel c, long now) {
        return Esper.canCast(p) && d.getBoolean("Mode") && p.serverLevel() == c.world
                && Esper.school(d) == Esper.School.SPACE && Esper.slot(d, c.slot) == c.skill.ordinal()
                && Esper.level(d, c.skill) == c.level && now - c.heartbeat <= 12;
    }
    static double radius(int level, int elapsed) {
        return 1 + (3 + level * .65) * Math.clamp(elapsed / (double) GROW_TICKS, 0, 1);
    }
    static void tick(ServerPlayer p, CompoundTag d, long now) {
        Channel c = CHANNELS.get(p);
        if (c == null) return;
        if (!valid(p, d, c, now)) { cancel(p); return; }
        if (c.lastTick == now) return;
        c.lastTick = now;
        c.elapsed = (int) (now - c.start);
        int interval = c.skill == Esper.Skill.SLASH ? 10 : 5;
        if (c.elapsed <= 0 || c.elapsed % interval != 0) return;
        int discount = Esper.level(d, Esper.Skill.RESONANCE) / 2;
        int cost = c.skill == Esper.Skill.SLASH ? Math.max(1, 3 - discount) : 5 - discount;
        if (d.getInt("Power") < cost) { release(p, c.slot, now); return; }
        d.putInt("Power", d.getInt("Power") - cost);
        if (c.skill == Esper.Skill.SLASH) { EsperEffects.slash(p, c.level); return; }
        double radius = radius(c.level, c.elapsed);
        showSphere(p, c, radius);
        if (c.selected != null && c.selected.level() == c.world && c.selected.isAlive())
            EsperEffects.bind(p, c.selected, 8, c.bindingToken);
        for (LivingEntity target : victims(p, c, radius)) {
            if (target != c.selected) EsperEffects.bind(p, target, 8);
            if (c.elapsed % 10 == 0) target.hurt(p.damageSources().playerAttack(p), 2 + c.level);
        }
        if (c.elapsed >= GROW_TICKS) { explode(p, c); cancel(p); }
    }
    private static void showSphere(ServerPlayer p, Channel c, double radius) {
        EsperNetworking.fracture(c.world, EsperNetworking.Pattern.SINGULARITY, c.focus,
                p.getLookAngle(), (float) radius, 7);
    }
    private static List<LivingEntity> victims(ServerPlayer p, Channel c, double r) {
        return c.world.getEntitiesOfClass(LivingEntity.class, new AABB(c.focus, c.focus).inflate(r),
                e -> EsperEffects.canAffect(p, e) && e.getBoundingBox().getCenter().distanceToSqr(c.focus) <= r * r
                    && c.world.clip(new net.minecraft.world.level.ClipContext(c.focus, e.getBoundingBox().getCenter(),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p))
                        .getType() == HitResult.Type.MISS);
    }
    private static void explode(ServerPlayer p, Channel c) {
        double r = radius(c.level, c.elapsed);
        float strength = Math.clamp(c.elapsed / (float) GROW_TICKS, .12F, 1);
        for (LivingEntity target : victims(p, c, r + .7)) {
            target.invulnerableTime = 0;
            target.hurt(p.damageSources().playerAttack(p), (16 + c.level * 7) * strength);
        }
        EsperNetworking.fracture(c.world, EsperNetworking.Pattern.DETONATION, c.focus, p.getLookAngle(), (float) r, 26);
        EsperCollapse.enqueue(p, c.focus, r, c.level);
        EsperEffects.sound(c.world, c.focus, net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), .55F);
    }
    private EsperChannel() {}
}
