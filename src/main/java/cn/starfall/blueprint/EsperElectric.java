package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

final class EsperElectric {
    private static final Map<ServerLevel, List<Field>> FIELDS = new WeakHashMap<>();
    private static final int FE_PER_CHARGE = 90;

    private static final class Field {
        final UUID owner;
        final Esper.Skill skill;
        final Vec3 anchor;
        final int level;
        final Set<UUID> hit = new HashSet<>();
        final UUID target;
        long next;
        int wave;

        Field(ServerPlayer player, Esper.Skill skill, Vec3 point, int level, UUID target) {
            owner = player.getUUID(); this.skill = skill; anchor = point; this.level = level; this.target = target;
            next = player.serverLevel().getGameTime();
        }
    }

    static boolean cast(ServerPlayer p, CompoundTag data, Esper.Skill skill, int level, long time) {
        ServerLevel world = p.serverLevel();
        Vec3 at = p.position();
        switch (skill) {
            case INDUCTION -> {
                int charge = data.getInt("Charge"), capacity = capacity(data);
                if (charge >= capacity) return false;
                boolean circuit = BlockPos.withinManhattanStream(p.blockPosition(), 3, 2, 3)
                        .filter(world::hasChunkAt).anyMatch(world::hasNeighborSignal);
                var redstone = p.getInventory().items.stream().filter(s -> s.is(Items.REDSTONE)).findFirst();
                int gain;
                if (circuit) gain = 40 + 6 * level;
                else if (redstone.isPresent() && capacity - charge >= 26) {
                    redstone.get().consume(1, p);
                    gain = 32 + 5 * level;
                } else gain = 18 + 3 * level;
                data.putInt("Charge", Math.min(capacity, charge + gain));
                ring(world, at.add(0, .8, 0), 1.15);
                EsperEffects.sound(world, at, SoundEvents.BEACON_POWER_SELECT, 1.6F);
            }
            case SPARK, PARALYZE, IONIZE -> {
                LivingEntity victim = aimed(p, 9 + level * 2);
                if (victim == null) return false;
                bolt(world, p.getEyePosition(), victim.getBoundingBox().getCenter());
                damage(p, victim, skill == Esper.Skill.SPARK ? 5 + 2.2F * level : 3 + level);
                if (skill == Esper.Skill.PARALYZE) {
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30 + level * 13, 3), p);
                    victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 35 + level * 15, 1), p);
                }
                if (skill == Esper.Skill.IONIZE) {
                    victim.getPersistentData().putUUID("EsperIonOwner", p.getUUID());
                    victim.getPersistentData().putLong("EsperIonUntil", world.getGameTime() + 80 + level * 30);
                    Field field = new Field(p, skill, victim.position(), level, victim.getUUID());
                    field.next += 21;
                    add(p, field);
                }
            }
            case ARC -> { return EsperEffects.arc(p, level); }
            case BOOST -> {
                Vec3 look = p.getLookAngle();
                Vec3 forward = new Vec3(look.x, 0, look.z).normalize().scale(.8 + .18 * level);
                EsperEffects.push(p, forward.add(0, .16, 0));
                long conductors = world.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4),
                        e -> EsperEffects.canAffect(p,e) && e.getPersistentData().getLong("EsperIonUntil") > world.getGameTime()).size();
                if (conductors > 0) gain(data, (int)Math.min(12, conductors * 4));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 26 + 12 * level, 0), p);
                flash(world, at);
                EsperEffects.sound(world, at, SoundEvents.BREEZE_JUMP, 1.6F);
            }
            case FEED, SURGE -> {
                BlockHitResult block = aimedBlock(p, 6 + level);
                if (block == null) return false;
                int available = skill.charge * FE_PER_CHARGE;
                int delivered = receive(world, block.getBlockPos(), block.getDirection(), available);
                if (skill == Esper.Skill.SURGE) {
                    available -= delivered;
                    for (Direction dir : Direction.values()) {
                        if (available <= 0) break;
                        int transfer = receive(world, block.getBlockPos().relative(dir), dir.getOpposite(), available);
                        delivered += transfer; available -= transfer;
                    }
                }
                if (delivered == 0) return false;
                bolt(world, p.getEyePosition(), block.getLocation());
                p.sendSystemMessage(Esper.label("feed_result", delivered));
            }
            case SCAN -> {
                int radius = 4 + level, count = 0;
                BlockPos center = p.blockPosition();
                for (BlockPos pos : BlockPos.withinManhattan(center, radius, 3, radius)) {
                    if (!world.hasChunkAt(pos) || !visible(p, p.getEyePosition(), Vec3.atCenterOf(pos))) continue;
                    if (!world.hasNeighborSignal(pos) && port(world, pos, null) == null) continue;
                    flash(world, Vec3.atCenterOf(pos));
                    if (++count == 24) break;
                }
                if (count == 0) return false;
                p.sendSystemMessage(Esper.label("scan_result", count));
                ring(world, at.add(0, .2, 0), radius);
            }
            case INSULATE -> {
                data.putLong("Insulation", time + 70 + level * 26);
                data.putInt("InsulationHits", 2 + level);
                ring(world, at.add(0, 1, 0), 1.2);
            }
            case OVERDRIVE -> {
                data.putLong("Overdrive", time + 110 + 30 * level);
                data.putInt("OverdriveBonus", 3 + level * 2);
                ring(world, at.add(0, 1, 0), 1);
                EsperEffects.sound(world, at, SoundEvents.BEACON_ACTIVATE, 1.65F);
            }
            case PULSE -> {
                add(p, new Field(p, skill, at, level, null));
                EsperEffects.sound(world, at, SoundEvents.WARDEN_SONIC_BOOM, 1.6F);
            }
            case STORM -> {
                BlockHitResult block = aimedBlock(p, 17 + level * 3);
                LivingEntity victim = aimed(p, 17 + level * 3);
                if (block == null && victim == null) return false;
                Vec3 focus = victim == null ? block.getLocation() : victim.position();
                add(p, new Field(p, skill, focus, level, null));
                ring(world, focus.add(0, .16, 0), 4 + .6 * level);
                EsperEffects.sound(world, focus, SoundEvents.AMETHYST_BLOCK_CHIME, .57F);
            }
            case LANCE -> {
                Vec3 from = p.getEyePosition(), to = EsperEffects.trace(p, 12 + level * 4);
                AABB ray = new AABB(from, to).inflate(.7);
                var targets = world.getEntitiesOfClass(LivingEntity.class, ray, victim ->
                        EsperEffects.canAffect(p, victim) && victim.getBoundingBox().inflate(.34).clip(from, to).isPresent());
                if (targets.isEmpty()) return false;
                targets.sort(Comparator.comparingDouble(p::distanceToSqr));
                for (int i = 0; i < Math.min(targets.size(), 2 + level); i++)
                    damage(p, targets.get(i), Math.max(3, 11 + 2.8F * level - 2.5F * i));
                bolt(world, from, to);
                EsperEffects.sound(world, from, SoundEvents.LIGHTNING_BOLT_IMPACT, .78F);
            }
            default -> { return false; }
        }
        return true;
    }

    static void tick(ServerPlayer p, CompoundTag data, long now) {
        if (Esper.school(data) != Esper.School.ELECTRIC) return;
        int passive = Esper.level(data, Esper.Skill.CAPACITOR);
        if (p.tickCount % 20 == 0 && passive > 0) {
            int cap = 12 + passive * 8;
            if (data.getInt("Charge") < cap) data.putInt("Charge", Math.min(cap, data.getInt("Charge") + 1 + passive));
        }
        if (now % 12 == 0 && (now < data.getLong("Overdrive")
                || now < data.getLong("Insulation") && data.getInt("InsulationHits") > 0))
            flash(p.serverLevel(), p.position().add(0, .8, 0));
        if (now < data.getLong("Overdrive") && p.tickCount % 20 == 0) {
            gain(data, data.getInt("OverdriveBonus"));
        }
    }

    static boolean protect(ServerPlayer p, CompoundTag data, long now, LivingIncomingDamageEvent event) {
        if (Esper.school(data) != Esper.School.ELECTRIC || now >= data.getLong("Insulation")
                || data.getInt("InsulationHits") <= 0 || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(event.getSource().getDirectEntity() != null || event.getSource().is(DamageTypeTags.IS_LIGHTNING))) return false;
        data.putInt("InsulationHits", data.getInt("InsulationHits") - 1);
        event.setAmount(event.getAmount() * .4F);
        gain(data, 3 + Esper.level(data, Esper.Skill.RECYCLING));
        flash(p.serverLevel(), p.position().add(0, 1, 0));
        return true;
    }

    private static void add(ServerPlayer p, Field field) {
        var fields = FIELDS.computeIfAbsent(p.serverLevel(), world -> new ArrayList<>());
        if (fields.size() < 256) fields.add(field);
    }

    static void tickFields(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel world)) return;
        var fields = FIELDS.get(world);
        if (fields == null) return;
        long now = world.getGameTime();
        fields.removeIf(field -> {
            if (!(world.getPlayerByUUID(field.owner) instanceof ServerPlayer owner)
                    || !owner.isAlive() || owner.isSpectator()
                    || Esper.school(owner.getPersistentData().getCompound(Esper.DATA)) != Esper.School.ELECTRIC) return true;
            if (now < field.next) return false;
            return switch (field.skill) {
                case PULSE -> pulse(owner, field, now);
                case STORM -> storm(owner, field, now);
                case IONIZE -> ionize(owner, field, now);
                default -> true;
            };
        });
        if (fields.isEmpty()) FIELDS.remove(world);
    }

    private static boolean ionize(ServerPlayer p, Field f, long now) {
        if (!(p.serverLevel().getEntity(f.target) instanceof LivingEntity victim) || !EsperEffects.canAffect(p, victim))
            return true;
        flash(p.serverLevel(), victim.getBoundingBox().getCenter());
        damage(p, victim, 2 + f.level);
        f.next = now + 21;
        return ++f.wave >= 2 + f.level;
    }

    private static boolean pulse(ServerPlayer p, Field f, long now) {
        ServerLevel world = p.serverLevel();
        int waves = 4 + f.level;
        double radius = ++f.wave * 1.2;
        ring(world, f.anchor.add(0, .27, 0), radius);
        ring(world, f.anchor.add(0, .62, 0), radius);
        for (LivingEntity target : world.getEntitiesOfClass(LivingEntity.class, new AABB(f.anchor, f.anchor).inflate(radius, 2, radius),
                victim -> EsperEffects.canAffect(p, victim) && !f.hit.contains(victim.getUUID()))) {
            Vec3 offset = target.position().subtract(f.anchor);
            if (offset.horizontalDistanceSqr() > radius * radius
                    || !visible(p, f.anchor.add(0, .75, 0), target.getBoundingBox().getCenter())) continue;
            f.hit.add(target.getUUID());
            damage(p, target, 8 + f.level * 2.3F);
            EsperEffects.push(target, new Vec3(offset.x, 0, offset.z).normalize().scale(.9).add(0, .3, 0));
            flash(world, target.position());
        }
        f.next = now + 3;
        return f.wave >= waves;
    }

    private static boolean storm(ServerPlayer p, Field f, long now) {
        ServerLevel world = p.serverLevel();
        if (!world.hasChunkAt(BlockPos.containing(f.anchor))) return true;
        double radius = 4 + f.level * .6;
        var targets = world.getEntitiesOfClass(LivingEntity.class, new AABB(f.anchor, f.anchor).inflate(radius, 4, radius),
                victim -> EsperEffects.canAffect(p, victim) && victim.position().distanceToSqr(f.anchor) <= radius * radius
                        && visible(p, f.anchor.add(0, .8, 0), victim.getBoundingBox().getCenter()));
        targets.sort(Comparator.comparingDouble(victim -> victim.distanceToSqr(f.anchor)));
        int strikes = 4 + f.level;
        for (int i = 0; i < strikes; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(world.random.nextDouble()) * radius;
            Vec3 spot = i < targets.size() ? targets.get(i).position()
                    : f.anchor.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            if (!world.hasChunkAt(BlockPos.containing(spot))) continue;
            var terrain = world.clip(new ClipContext(spot.add(0, 4, 0), spot.subtract(0, 3, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Vec3 impact = i < targets.size() ? spot.add(0, .2, 0) : terrain.getType() == HitResult.Type.BLOCK ? terrain.getLocation() : spot;
            Vec3 ceiling = upward(p, impact.add(0, .25, 0), 14);
            bolt(world, ceiling, impact);
            flash(world, impact);
            if (i < targets.size()) damage(p, targets.get(i), 4 + 1.2F * f.level);
        }
        EsperEffects.sound(world, f.anchor, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.25F);
        f.next = now + 11;
        return ++f.wave >= 4 + f.level;
    }

    private static Vec3 upward(ServerPlayer p, Vec3 at, int height) {
        return p.serverLevel().clip(new ClipContext(at, at.add(0, height, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p)).getLocation();
    }
    static int capacity(CompoundTag d) { return 80 + 10 * Esper.level(d, Esper.Skill.INDUCTION) + 8 * Esper.level(d, Esper.Skill.CAPACITOR); }
    static void gain(CompoundTag d, int amount) { d.putInt("Charge", Math.min(capacity(d), d.getInt("Charge") + amount)); }
    static void ring(ServerLevel world, Vec3 at, double radius) {
        EsperNetworking.fracture(world, EsperNetworking.Pattern.ELECTRIC_RING, at, new Vec3(0,1,0), (float) radius, 14);
    }
    static void flash(ServerLevel world, Vec3 at) {
        EsperNetworking.fracture(world, EsperNetworking.Pattern.ELECTRIC_HIT, at, new Vec3(0,1,0), .65F, 10);
    }
    static void bolt(ServerLevel world, Vec3 start, Vec3 end) {
        EsperNetworking.fracture(world, EsperNetworking.Pattern.LIGHTNING, start, end.subtract(start).normalize(),
                (float) start.distanceTo(end), 9);
    }
    static void onHit(ServerPlayer p, LivingEntity target) {
        CompoundTag d = Esper.data(p), mark = target.getPersistentData();
        long now = p.serverLevel().getGameTime();
        if (mark.hasUUID("EsperIonOwner") && mark.getUUID("EsperIonOwner").equals(p.getUUID())
                && now < mark.getLong("EsperIonUntil") && now >= mark.getLong("EsperIonRefund")) {
            gain(d, 2 + Esper.level(d, Esper.Skill.RECYCLING));
            mark.putLong("EsperIonRefund", now + 10);
        }
    }
    private static boolean visible(ServerPlayer p, Vec3 start, Vec3 end) {
        return p.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }
    private static LivingEntity aimed(ServerPlayer p, double range) {
        return EsperEffects.target(p, EsperEffects.trace(p, range), victim -> EsperEffects.canAffect(p, victim))
                instanceof LivingEntity victim ? victim : null;
    }
    private static BlockHitResult aimedBlock(ServerPlayer p, double range) {
        Vec3 end = EsperEffects.trace(p, range).add(p.getLookAngle().scale(.02));
        var hit = p.serverLevel().clip(new ClipContext(p.getEyePosition(), end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }
    private static IEnergyStorage port(ServerLevel l, BlockPos p, Direction face) {
        return l.hasChunkAt(p) ? l.getCapability(Capabilities.EnergyStorage.BLOCK, p, face) : null;
    }
    private static int receive(ServerLevel l, BlockPos p, Direction face, int amount) {
        IEnergyStorage port = port(l, p, face);
        return port == null || !port.canReceive() ? 0 : port.receiveEnergy(amount, false);
    }
    static void damage(ServerPlayer source, LivingEntity target, float damage) {
        target.hurt(source.damageSources().playerAttack(source), damage);
        onHit(source, target);
        var d = Esper.data(source);
        if (!target.isAlive() && Esper.level(d, Esper.Skill.RECYCLING) > 0
                && !target.getPersistentData().getBoolean("EsperChargeReclaimed")) {
            target.getPersistentData().putBoolean("EsperChargeReclaimed", true);
            gain(d, 4 + Esper.level(d, Esper.Skill.RECYCLING) * 2);
        }
    }
    private EsperElectric() {}
}
