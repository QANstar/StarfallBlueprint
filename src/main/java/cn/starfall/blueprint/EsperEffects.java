package cn.starfall.blueprint;

import static cn.starfall.blueprint.EsperNetworking.Pattern.*;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

final class EsperEffects {
    private record Portal(UUID owner, Vec3 a, Vec3 b, long end, Set<UUID> inside) {}
    private static final class Harvest {
        final ServerPlayer caster;
        final int cargoLevel;
        EsperStorage storage;
        Harvest(ServerPlayer caster, int cargoLevel) { this.caster = caster; this.cargoLevel = cargoLevel; }
    }
    private record Binding(UUID owner, ServerLevel dimension, Vec3 anchor, long until, Object source) {}
    private static final Map<ServerLevel, List<Portal>> PORTALS = new WeakHashMap<>();
    private static final Map<LivingEntity, Binding> BINDINGS = new WeakHashMap<>();
    private static final ThreadLocal<Harvest> HARVEST = new ThreadLocal<>();

    static boolean cast(ServerPlayer p, CompoundTag d, Esper.Skill skill, int level) {
        ServerLevel l = p.serverLevel();
        Vec3 at = p.position();
        long time = l.getServer().overworld().getGameTime();
        if (skill.school == Esper.School.ELECTRIC) return EsperElectric.cast(p, d, skill, level, time);
        switch (skill) {
            case BLINK -> {
                double range = 7 + level * 3;
                LivingEntity selected = visibleCreature(p);
                Vec3 to = selected != null ? behind(p, selected) : landing(p, range, false);
                if (to == null) return false;
                Vec3 look = p.getLookAngle();
                teleport(p, to);
                if (selected != null) p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, selected.getEyePosition());
                fracture(l, RIFT, at.add(0, .9, 0), look, 1.2F, 23);
                fracture(l, RIFT, to.add(0, .9, 0), p.getLookAngle(), 1.2F, 23);
                sound(l, at, SoundEvents.CHORUS_FRUIT_TELEPORT, 1.3F);
            }
            case PORTAL -> {
                Vec3 a = at.add(horizontal(p.getLookAngle()).scale(2.1)), b = landing(p, 12 + level * 5, true);
                if (b == null || !safe(l, p, a, true) || !EsperDomain.allows(l, p, a) || a.distanceToSqr(b) < 16) return false;
                List<Portal> list = PORTALS.computeIfAbsent(l, key -> new ArrayList<>());
                list.removeIf(portal -> portal.owner.equals(p.getUUID()));
                list.add(new Portal(p.getUUID(), a, b, l.getGameTime() + 120 + level * 60, new HashSet<>()));
                fracture(l, PORTAL, a.add(0, 1, 0), b.subtract(a), 1.12F, 38);
                fracture(l, PORTAL, b.add(0, 1, 0), a.subtract(b), 1.12F, 38);
                sound(l, at, SoundEvents.PORTAL_TRIGGER, 1.7F);
            }
            case SLASH -> slash(p, level);
            case STORAGE -> new EsperStorage(p, level).open();
            case COLLAPSE -> { return false; }
            case SLING -> { return EsperWarp.cast(p, level); }
            case SHIFT -> {
                if (p.isFallFlying()) p.stopFallFlying();
                d.putLong("Shift", Long.MAX_VALUE);
                d.putLong("ShiftTakeoff", time + 6);
                d.putBoolean("ShiftGranted", !p.getAbilities().mayfly);
                d.putBoolean("ShiftWasFlying", p.getAbilities().flying);
                p.getAbilities().mayfly = true;
                p.getAbilities().flying = true;
                if (p.onGround()) {
                    p.setOnGround(false);
                    push(p, p.getDeltaMovement().with(net.minecraft.core.Direction.Axis.Y, .28));
                }
                p.resetFallDistance(); p.onUpdateAbilities();
                fracture(l, WAKE, at, new Vec3(0, 1, 0), 1.55F, 20);
            }
            case PULL -> {
                Entity selected = target(p, trace(p, 18 + level * 4), e -> canAffect(p, e));
                if (!(selected instanceof LivingEntity target)) return false;
                Vec3 destination = at.add(horizontal(p.getLookAngle()).scale(1.7));
                if (!safe(l, target, destination, false) || !EsperDomain.allows(l, p, destination)
                        || (target instanceof ServerPlayer other && !EsperDomain.allows(l, other, destination))) return false;
                fracture(l, RIFT, target.getBoundingBox().getCenter(), p.getLookAngle(), 1.05F, 24);
                fracture(l, RIFT, destination.add(0, .8, 0), p.getLookAngle(), 1.05F, 24);
                teleport(target, destination); sound(l, at, SoundEvents.ENDERMAN_TELEPORT, 1.5F);
                if (level >= 3) bind(p, target, 8 + level * 3);
            }
            case LOCK -> {
                if (!(target(p, trace(p, 22 + level * 4), e -> canAffect(p, e)) instanceof LivingEntity target)) return false;
                bind(p, target, 28 + level * 14);
                fracture(l, BIND, target.getBoundingBox().getCenter(), p.getLookAngle(), .95F, 33);
                sound(l, target.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, .6F);
            }
            case DOMAIN -> {
                Entity selected = p.isShiftKeyDown() ? target(p, trace(p, 9 + level), e ->
                        e instanceof ServerPlayer guest && guest.isShiftKeyDown() && !guest.isSpectator()) : null;
                if (p.isShiftKeyDown() && !(selected instanceof ServerPlayer)) return false;
                ServerPlayer traveller = selected instanceof ServerPlayer guest ? guest : p;
                Vec3 old = traveller.position();
                if (!EsperDomain.move(p, traveller)) return false;
                fracture(l, PORTAL, old.add(0, 1, 0), p.getLookAngle(), 1.35F, 34);
                fracture(traveller.serverLevel(), PORTAL, traveller.position().add(0, 1, 0), traveller.getLookAngle(), 1.35F, 34);
                sound(l, old, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.3F);
            }
            case BARRIER -> {
                if (d.getInt("Power") <= 0) return false;
                d.putLong("Barrier", Long.MAX_VALUE);
                EsperNetworking.aura(p, EsperNetworking.Aura.GUARD, 1, 40);
                sound(l, at, SoundEvents.AMETHYST_BLOCK_RESONATE, .85F);
            }
            default -> { return false; }
        }
        return true;
    }

    static boolean arc(ServerPlayer p, int level) {
        ServerLevel l = p.serverLevel();
        Entity first = target(p, trace(p, 8 + level * 2), e -> canAffect(p, e));
        if (!(first instanceof LivingEntity current)) return false;
        Vec3 start = p.getEyePosition();
        Set<Entity> seen = new HashSet<>();
        for (int jump = 0; jump < 1 + level && current != null; jump++) {
            LivingEntity hit = current;
            Vec3 end = hit.getBoundingBox().getCenter();
            EsperElectric.bolt(l, start, end);
            EsperElectric.damage(p, hit, Math.max(2, 5 + level * 2F - jump * 1.5F));
            seen.add(hit);
            start = end;
            current = l.getEntitiesOfClass(LivingEntity.class, hit.getBoundingBox().inflate(3 + level * .5),
                    e -> !seen.contains(e) && canAffect(p, e) && l.clip(new ClipContext(end, e.getBoundingBox().getCenter(),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS)
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(hit))).orElse(null);
        }
        sound(l, p.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.55F);
        return true;
    }

    static double slashRadius(int level) { return 18 + level * 4; }

    static void slash(ServerPlayer player, int level) {
        var world = player.serverLevel();
        Vec3 from = player.getEyePosition(), direction = player.getLookAngle();
        double radius = slashRadius(level);
        int hits = 0;
        for (var target : world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> canAffect(player, e) && !e.isInvisibleTo(player))) {
            Vec3 center = target.getBoundingBox().getCenter(), relative = center.subtract(from);
            double distance = relative.length();
            if (distance > radius || relative.dot(direction) < distance * Math.cos(Esper.SLASH_HALF_ANGLE)) continue;
            if (world.clip(new ClipContext(from, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                    .getType() != HitResult.Type.MISS) continue;
            target.hurt(player.damageSources().playerAttack(player), 4.5F + level * 1.75F);
            fracture(world, TARGET_SLASH, center, direction, Math.max(.9F, target.getBbHeight() * .65F), 12);
            if (++hits >= 32) break;
        }
        if (hits > 0) sound(world, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F);
    }

    static void withHarvest(ServerPlayer player, int level, Runnable work) {
        int storageLevel = Esper.level(Esper.data(player), Esper.Skill.STORAGE);
        if (level < 3 || storageLevel == 0) { work.run(); return; }
        Harvest previous = HARVEST.get();
        HARVEST.set(new Harvest(player, storageLevel));
        try { work.run(); }
        finally {
            if (previous == null) HARVEST.remove(); else HARVEST.set(previous);
        }
    }
    static void blockDrops(BlockDropsEvent event) {
        Harvest harvest = HARVEST.get();
        if (harvest == null || event.getBreaker() != harvest.caster || event.isCanceled()) return;
        if (harvest.storage == null) harvest.storage = new EsperStorage(harvest.caster, harvest.cargoLevel);
        event.getDrops().removeIf(drop -> {
            var rest = harvest.storage.store(drop.getItem());
            if (rest.isEmpty()) return true;
            drop.setItem(rest); return false;
        });
    }
    static boolean canAffect(Entity owner, Entity target) {
        return owner != target && target instanceof LivingEntity && target.isAlive() && !target.isSpectator()
                && !owner.isAlliedTo(target) && (!(owner instanceof Player a && target instanceof Player b) || a.canHarmPlayer(b));
    }
    static Entity target(ServerPlayer p, Vec3 to, java.util.function.Predicate<Entity> filter) {
        Vec3 from = p.getEyePosition();
        var hit = ProjectileUtil.getEntityHitResult(p, from, to,
                p.getBoundingBox().expandTowards(to.subtract(from)).inflate(1), filter, from.distanceToSqr(to));
        return hit == null ? null : hit.getEntity();
    }
    static Vec3 trace(ServerPlayer p, double range) {
        Vec3 from = p.getEyePosition(), look = p.getLookAngle();
        Vec3 to = from;
        for (double dist = .5; dist <= range; dist += .5) {
            Vec3 next = from.add(look.scale(dist));
            if (!p.level().hasChunkAt(BlockPos.containing(next))) break;
            to = next;
        }
        return p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getLocation();
    }
    private static Vec3 horizontal(Vec3 v) {
        Vec3 flat = new Vec3(v.x, 0, v.z);
        return flat.lengthSqr() < .0001 ? new Vec3(0, 0, 1) : flat.normalize();
    }
    private static Vec3 loadedSight(ServerPlayer p, Vec3 from, Vec3 ray, int range) {
        Vec3 last = from;
        for (int distance = 1; distance <= range; distance++) {
            Vec3 point = from.add(ray.scale(distance));
            if (!p.level().hasChunkAt(BlockPos.containing(point))) break;
            last = point;
        }
        return last;
    }
    private static LivingEntity visibleCreature(ServerPlayer p) {
        Vec3 from = p.getEyePosition();
        Vec3 ray = p.getLookAngle();
        int serverView = Math.max(2, p.serverLevel().getServer().getPlayerList().getViewDistance());
        int view = Math.clamp(p.requestedViewDistance(), 2, serverView);
        Vec3 to = loadedSight(p, from, ray, (view + 1) * 16);
        Vec3 stop = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getLocation();
        double length = from.distanceTo(stop);
        for (int distance = 0; distance < length; distance += 8) {
            Vec3 start = from.add(ray.scale(distance));
            Vec3 end = from.add(ray.scale(Math.min(distance + 8, length)));
            var nearby = p.level().getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(.45),
                    entity -> entity != p && entity.isAlive() && !entity.isSpectator() && !entity.isInvisibleTo(p));
            LivingEntity closest = null;
            double best = Double.POSITIVE_INFINITY;
            for (LivingEntity entity : nearby) {
                var box = entity.getBoundingBox().inflate(entity.getPickRadius());
                Vec3 hit = box.contains(start) ? start : box.clip(start, end).orElse(null);
                if (hit != null && from.distanceToSqr(hit) < best) {
                    best = from.distanceToSqr(hit);
                    closest = entity;
                }
            }
            if (closest != null) return closest;
        }
        return null;
    }
    private static Vec3 behind(ServerPlayer p, LivingEntity target) {
        Vec3 forward = Vec3.directionFromRotation(0, target.getYRot());
        double gap = (p.getBbWidth() + target.getBbWidth()) / 2 + .55;
        for (double depth = 0; depth <= 1.5; depth += .5) {
            Vec3 base = target.position().subtract(forward.scale(gap + depth));
            for (int dy : new int[]{0, 1, -1}) {
                Vec3 at = base.add(0, dy, 0);
                if (EsperDomain.allows(p.serverLevel(), p, at)
                        && safe(p.serverLevel(), p, at, target.onGround())) return at;
            }
        }
        return null;
    }
    private static Vec3 landing(ServerPlayer p, double range, boolean grounded) {
        Vec3 end = trace(p, range).subtract(p.getLookAngle().scale(.7));
        for (double depth = 0; depth <= 3; depth += .5) {
            Vec3 offset = end.subtract(p.getLookAngle().scale(depth));
            for (int y = 0; y >= -4; y--) {
                Vec3 at = new Vec3(offset.x, Math.floor(offset.y) + y, offset.z);
                if (at.distanceToSqr(p.position()) >= 4 && EsperDomain.allows(p.serverLevel(), p, at)
                        && safe(p.serverLevel(), p, at, grounded)) return at;
            }
        }
        return null;
    }
    static boolean safe(ServerLevel l, LivingEntity entity, Vec3 at, boolean grounded) {
        BlockPos pos = BlockPos.containing(at);
        AABB box = entity.getBoundingBox().move(at.subtract(entity.position()));
        return l.isInWorldBounds(pos) && box.maxY < l.getMaxBuildHeight()
                && l.getWorldBorder().isWithinBounds(box)
                && l.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                && l.noCollision(entity, box) && !l.containsAnyLiquid(box)
                && (!grounded || l.getBlockState(pos.below()).isFaceSturdy(l, pos.below(), Direction.UP));
    }
    private static void teleport(LivingEntity e, Vec3 to) {
        e.teleportTo(to.x, to.y, to.z);
        push(e, Vec3.ZERO); e.resetFallDistance();
    }
    static void push(Entity e, Vec3 velocity) { e.setDeltaMovement(velocity); e.hurtMarked = true; }

    static void bind(ServerPlayer owner, LivingEntity target, int duration) {
        bind(owner, target, duration, null);
    }
    static void bind(ServerPlayer owner, LivingEntity target, int duration, Object source) {
        if (target.isPassenger()) target.stopRiding();
        long now = owner.serverLevel().getGameTime();
        Binding previous = BINDINGS.get(target);
        if (source == null && previous != null && previous.source != null && previous.until > now) return;
        Vec3 anchor = previous != null && previous.source == source
                && previous.owner.equals(owner.getUUID()) && previous.dimension == owner.serverLevel()
                && previous.until > now ? previous.anchor : target.position();
        BINDINGS.put(target, new Binding(owner.getUUID(), owner.serverLevel(), anchor, now + duration, source));
        push(target, Vec3.ZERO);
    }
    static void unbind(LivingEntity target, Object source) {
        Binding current = BINDINGS.get(target);
        if (current != null && current.source == source) BINDINGS.remove(target);
    }
    static void tickBinding(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(target.level() instanceof ServerLevel level)) return;
        Binding binding = BINDINGS.get(target);
        if (binding == null) return;
        if (!target.isAlive() || binding.dimension != level || level.getGameTime() >= binding.until
                || !safe(level, target, binding.anchor, false)) {
            BINDINGS.remove(target);
            return;
        }
        if (target.isPassenger()) target.stopRiding();
        if (target.position().distanceToSqr(binding.anchor) > .025) teleport(target, binding.anchor);
        else if (target.getDeltaMovement().lengthSqr() > .002) push(target, Vec3.ZERO);
        if (level.getGameTime() % 20 == 0)
            fracture(level, BIND, binding.anchor.add(0, .75, 0), target.getLookAngle(), .9F, 23);
    }
    static void endSchoolEffects(ServerPlayer player, CompoundTag data) {
        stopShift(player, data);
        stopBarrier(player, data);
        endModeEffects(player, data);
    }

    static void endModeEffects(ServerPlayer player, CompoundTag data) {
        EsperVolley.cancel(player);
        EsperChannel.cancel(player);
        for (String key : new String[]{"Sling", "Insulation", "InsulationHits", "Overdrive"})
            data.remove(key);
        List<Portal> portals = PORTALS.get(player.serverLevel());
        if (portals != null) portals.removeIf(portal -> portal.owner.equals(player.getUUID()));
        BINDINGS.values().removeIf(binding -> binding.owner.equals(player.getUUID()));
    }

    static void tickPlayer(ServerPlayer p, CompoundTag data, long now) {
        EsperElectric.tick(p, data, now);
        if (data.contains("Shift")) {
            if (p.isSpectator() || Esper.school(data) != Esper.School.SPACE
                    || p.onGround() && now >= data.getLong("ShiftTakeoff")
                    || data.getInt("Power") <= 0) stopShift(p, data);
            else {
                int interval = 2 + Esper.level(data, Esper.Skill.ANCHOR) / 2;
                if (!p.getAbilities().flying || !p.getAbilities().mayfly) {
                    p.getAbilities().flying = true;
                    p.getAbilities().mayfly = true;
                    p.onUpdateAbilities();
                }
                if (now % interval == 0) data.putInt("Power", data.getInt("Power") - 1);
                p.resetFallDistance();
                if (now % 10 == 0) fracture(p.serverLevel(), WAKE, p.position().add(0, -.25, 0),
                        new Vec3(0, 1, 0), .65F, 12);
            }
        }
        if (p.isSpectator() || Esper.school(data) != Esper.School.SPACE || data.getInt("Power") <= 0)
            stopBarrier(p, data);
        if (data.contains("Barrier")) {
            if (now % 20 == 0) EsperNetworking.aura(p, EsperNetworking.Aura.GUARD, 1, 40);
            for (var projectile : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                    p.getBoundingBox().inflate(3), shot -> shot.getOwner() != p && !(shot.getOwner() != null && p.isAlliedTo(shot.getOwner())))) {
                Vec3 center = p.position().add(0, .96, 0);
                var sphere = new AABB(center, center).inflate(Esper.BARRIER_RADIUS);
                if (projectile.getDeltaMovement().lengthSqr() > .0001
                        && (sphere.contains(projectile.position()) || sphere.clip(projectile.position(),
                        projectile.position().add(projectile.getDeltaMovement().scale(2))).isPresent())) {
                    if (barrierHit(p, data, 5, projectile.position(), false)) {
                        if (projectile instanceof EsperProjectile shot) shot.recall();
                        else projectile.discard();
                    }
                }
            }
        }
    }
    static void stopBarrier(ServerPlayer p, CompoundTag data) {
        if (data.contains("Barrier") && p.connection != null)
            EsperNetworking.aura(p, EsperNetworking.Aura.GUARD, 0, 0);
        data.remove("Barrier");
        data.remove("BarrierHits");
    }
    static void stopShift(Player p, CompoundTag data) {
        if (!data.contains("Shift") && !data.contains("ShiftGranted")) return;
        boolean granted = data.getBoolean("ShiftGranted");
        boolean wasFlying = data.getBoolean("ShiftWasFlying");
        data.remove("Shift");
        data.remove("ShiftTakeoff");
        data.remove("ShiftGranted");
        data.remove("ShiftWasFlying");
        if (p.isSpectator()) return;
        if (granted && !p.isCreative()) p.getAbilities().mayfly = false;
        p.getAbilities().flying = p.getAbilities().mayfly && wasFlying;
        p.resetFallDistance();
        data.putLong("Sling", p.level().getGameTime() + 100);
        p.onUpdateAbilities();
    }
    static void protect(ServerPlayer p, LivingIncomingDamageEvent event) {
        CompoundTag data = p.getPersistentData().getCompound(Esper.DATA);
        long now = p.serverLevel().getServer().overworld().getGameTime();
        if (EsperElectric.protect(p, data, now, event)) return;
        if (event.getSource().is(DamageTypeTags.IS_FALL) && (now < data.getLong("Sling") || now < data.getLong("Shift"))) {
            event.setCanceled(true); data.remove("Sling"); return;
        }
        if (!data.contains("Barrier") || Esper.school(data) != Esper.School.SPACE
                || event.getAmount() <= 0
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || (event.getSource().getDirectEntity() == null && !event.getSource().is(DamageTypeTags.IS_EXPLOSION))) return;
        Vec3 source = event.getSource().getSourcePosition();
        boolean melee = event.getSource().getDirectEntity() instanceof LivingEntity
                && !event.getSource().is(DamageTypeTags.IS_EXPLOSION);
        if (barrierHit(p, data, Math.max(2, (int) Math.ceil(event.getAmount() * 2)), source, melee))
            event.setCanceled(true);
    }
    static boolean barrierHit(ServerPlayer p, CompoundTag data, int cost, Vec3 source, boolean melee) {
        if (!data.contains("Barrier") || data.getInt("Power") < cost) return false;
        data.putInt("Power", data.getInt("Power") - cost);
        Vec3 center = p.position().add(0, .96, 0);
        Vec3 direction = source == null ? p.getLookAngle() : source.subtract(center).normalize();
        if (direction.lengthSqr() < .01) direction = p.getLookAngle();
        fracture(p.serverLevel(), melee ? GUARD_MELEE : GUARD,
                center.add(direction.scale(Esper.BARRIER_RADIUS)), direction, melee ? .85F : .68F, melee ? 20 : 16);
        sound(p.serverLevel(), center, SoundEvents.SHIELD_BLOCK, .9F);
        p.serverLevel().broadcastEntityEvent(p, (byte) 29);
        if (data.getInt("Power") == 0) stopBarrier(p, data);
        return true;
    }
    static void tickPortals(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel l)) return;
        var portals = PORTALS.get(l);
        if (portals == null) return;
        long now = l.getGameTime();
        portals.removeIf(portal -> now >= portal.end);
        if (portals.isEmpty()) { PORTALS.remove(l); return; }
        if (now % 4 != 0) return;
        for (Portal portal : portals) {
            if (!l.hasChunkAt(BlockPos.containing(portal.a)) || !l.hasChunkAt(BlockPos.containing(portal.b))) continue;
            if (now % 28 == 0) {
                fracture(l, PORTAL, portal.a.add(0, 1, 0), portal.b.subtract(portal.a), 1.1F, 32);
                fracture(l, PORTAL, portal.b.add(0, 1, 0), portal.a.subtract(portal.b), 1.1F, 32);
            }
            for (ServerPlayer p : l.players()) {
                Vec3 to = p.position().distanceToSqr(portal.a) < 1 ? portal.b
                        : p.position().distanceToSqr(portal.b) < 1 ? portal.a : null;
                if (to == null) { portal.inside.remove(p.getUUID()); continue; }
                if (!p.isAlive() || p.isPassenger() || p.isSleeping() || p.isSpectator() || portal.inside.contains(p.getUUID())
                        || (!p.getUUID().equals(portal.owner) && !p.isShiftKeyDown())
                        || !safe(l, p, to, true) || !EsperDomain.allows(l, p, to)) continue;
                portal.inside.add(p.getUUID());
                teleport(p, to);
                sound(l, to, SoundEvents.CHORUS_FRUIT_TELEPORT, 1.55F);
            }
        }
    }
    static void sound(ServerLevel l, Vec3 at, SoundEvent sound, float pitch) {
        l.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, .75F, pitch);
    }
    private static void fracture(ServerLevel l, EsperNetworking.Pattern pattern, Vec3 at, Vec3 normal, float radius, int life) {
        EsperNetworking.fracture(l, pattern, at, normal.normalize(), radius, life);
    }
}
