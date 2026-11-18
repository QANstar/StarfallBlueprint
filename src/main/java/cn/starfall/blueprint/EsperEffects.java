package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

final class EsperEffects {
    static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(.66F, .28F, 1F), 1.3F);
    private static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(.13F, .94F, 1F), 1.15F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(.86F, .98F, 1F), .85F);
    private record Portal(UUID owner, Vec3 a, Vec3 b, long end, Set<UUID> inside) {}
    private record Harvest(ServerPlayer caster, EsperStorage storage) {}
    private record Binding(UUID owner, ServerLevel dimension, Vec3 anchor, long until) {}
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
                Entity selected = target(p, trace(p, range), entity -> entity instanceof LivingEntity living
                        && living.isAlive() && !living.isSpectator());
                Vec3 to = selected instanceof LivingEntity living ? behind(p, living, range) : landing(p, range, false);
                if (to == null) return false;
                teleport(p, to); ring(l, at.add(0, .8, 0), 1.1, VIOLET, false);
                ring(l, to.add(0, .8, 0), 1.1, CYAN, false);
                sound(l, at, SoundEvents.CHORUS_FRUIT_TELEPORT, 1.3F);
            }
            case PORTAL -> {
                Vec3 a = at.add(horizontal(p.getLookAngle()).scale(2.1)), b = landing(p, 12 + level * 5, true);
                if (b == null || !safe(l, p, a, true) || !EsperDomain.allows(l, p, a) || a.distanceToSqr(b) < 16) return false;
                List<Portal> list = PORTALS.computeIfAbsent(l, key -> new ArrayList<>());
                list.removeIf(portal -> portal.owner.equals(p.getUUID()));
                list.add(new Portal(p.getUUID(), a, b, l.getGameTime() + 120 + level * 60, new HashSet<>()));
                ring(l, a.add(0, 1, 0), 1.05, VIOLET, true);
                ring(l, b.add(0, 1, 0), 1.05, CYAN, true);
                sound(l, at, SoundEvents.PORTAL_TRIGGER, 1.7F);
            }
            case SLASH -> {
                Vec3 start = p.getEyePosition(), to = trace(p, 6 + level * 2);
                Entity target = target(p, to, e -> canAffect(p, e));
                if (target != null) target.hurt(p.damageSources().playerAttack(p), 5 + 2.5F * level);
                Vec3 side = horizontal(p.getLookAngle()).cross(new Vec3(0, 1, 0));
                for (int i = 0; i < 25; i++) {
                    double t = i / 24.0;
                    Vec3 q = start.lerp(to, t).add(side.scale(Math.sin(t * Math.PI) * 1.35)).add(0, t - .5, 0);
                    dot(l, q, VIOLET); if (i % 2 == 0) dot(l, q.add(0, .12, 0), WHITE);
                }
                sound(l, at, SoundEvents.PLAYER_ATTACK_SWEEP, .8F); return target != null || start.distanceToSqr(to) > 1;
            }
            case STORAGE -> new EsperStorage(p, level).open();
            case COLLAPSE -> {
                if (!collapse(p, level)) return false;
            }
            case VOLLEY -> {
                int storageLevel = Esper.level(d, Esper.Skill.STORAGE);
                if (storageLevel == 0) return false;
                EsperStorage storage = new EsperStorage(p, storageLevel);
                int fired = 0;
                for (int i = 0; i < 1 + (level - 1) / 2; i++) {
                    int slot = storage.firstAmmo();
                    if (slot < 0) break;
                    EsperProjectile shot = Esper.SHOT.get().create(l);
                    if (shot == null) break;
                    shot.prepare(p, storage.getItem(slot).copyWithCount(1), level);
                    shot.shootFromRotation(p, p.getXRot(), p.getYRot() + (i == 0 ? 0 : i == 1 ? -7 : 7),
                            0, 2.1F + level * .23F, 0);
                    if (l.addFreshEntity(shot)) { storage.removeItem(slot, 1); fired++; }
                }
                if (fired == 0) return false;
                ring(l, p.getEyePosition().add(p.getLookAngle()), .65, VIOLET, true);
                sound(l, at, SoundEvents.CROSSBOW_SHOOT, .7F);
            }
            case SLING -> {
                Vec3 v = p.getLookAngle().scale(.95 + .24 * level);
                push(p, new Vec3(v.x, Math.max(.28, v.y + .23), v.z));
                d.putLong("Sling", time + 100);
                ring(l, at, 1.35, CYAN, false);
                burst(l, at, ParticleTypes.CLOUD, 18, .38);
                sound(l, at, SoundEvents.BREEZE_JUMP, 1.25F);
            }
            case SHIFT -> {
                if (p.isFallFlying() || p.getAbilities().mayfly) return false;
                d.putLong("Shift", time + 55 + level * 24);
                d.putBoolean("ShiftGranted", true);
                p.getAbilities().mayfly = true;
                p.getAbilities().flying = true;
                p.resetFallDistance();
                p.onUpdateAbilities();
                push(p, new Vec3(0, .22, 0));
                ring(l, at, 1.4, CYAN, false);
                sound(l, at, SoundEvents.BEACON_ACTIVATE, 1.5F);
            }
            case PULL -> {
                Entity selected = target(p, trace(p, 7 + level * 3), e -> canAffect(p, e));
                if (!(selected instanceof LivingEntity target)) return false;
                Vec3 destination = at.add(horizontal(p.getLookAngle()).scale(1.7));
                if (!safe(l, target, destination, false) || !EsperDomain.allows(l, p, destination)
                        || (target instanceof ServerPlayer other && !EsperDomain.allows(l, other, destination))) return false;
                beam(l, target.position().add(0, .8, 0), destination.add(0, .8, 0), VIOLET);
                teleport(target, destination); sound(l, at, SoundEvents.ENDERMAN_TELEPORT, 1.5F);
            }
            case LOCK -> {
                if (!(target(p, trace(p, 8 + level * 2), e -> canAffect(p, e)) instanceof LivingEntity target)) return false;
                bind(p, target, 28 + level * 14);
                ring(l, target.position().add(0, .8, 0), .9, VIOLET, true);
                sound(l, target.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, .6F);
            }
            case DOMAIN -> {
                Entity selected = p.isShiftKeyDown() ? target(p, trace(p, 9 + level), e ->
                        e instanceof ServerPlayer guest && guest.isShiftKeyDown() && !guest.isSpectator()) : null;
                if (p.isShiftKeyDown() && !(selected instanceof ServerPlayer)) return false;
                ServerPlayer traveller = selected instanceof ServerPlayer guest ? guest : p;
                Vec3 old = traveller.position();
                if (!EsperDomain.move(p, traveller)) return false;
                ring(l, old.add(0, 1, 0), 1.3, VIOLET, true);
                ring(traveller.serverLevel(), traveller.position().add(0, 1, 0), 1.3, CYAN, true);
                sound(l, old, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.3F);
            }
            case BARRIER -> {
                d.putLong("Barrier", time + 70 + level * 24);
                d.putInt("BarrierHits", level + 2);
                ring(l, at.add(0, 1, 0), 1.45, CYAN, false);
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
            Vec3 point = start;
            for (int i = 1; i <= 8; i++) {
                Vec3 next = start.lerp(end, i / 8.0);
                if (i < 8) next = next.add(l.random.nextDouble() * .55 - .275, l.random.nextDouble() * .75 - .375,
                        l.random.nextDouble() * .55 - .275);
                beam(l, point, next, i % 2 == 0 ? WHITE : CYAN);
                point = next;
            }
            burst(l, end, ParticleTypes.ELECTRIC_SPARK, 15, .3);
            hit.hurt(p.damageSources().playerAttack(p), Math.max(2, 5 + level * 2F - jump * 1.5F));
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

    private static boolean collapse(ServerPlayer p, int level) {
        ServerLevel l = p.serverLevel();
        Vec3 from = p.getEyePosition(), to = trace(p, 8 + level);
        BlockHitResult hit = l.clip(new ClipContext(from, to.add(p.getLookAngle().scale(.07)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos center = hit.getBlockPos();
        int radius = 1 + (level - 1) / 2;
        int storageLevel = Esper.level(Esper.data(p), Esper.Skill.STORAGE);
        Harvest previous = HARVEST.get();
        if (level >= 3 && storageLevel != 0) HARVEST.set(new Harvest(p, new EsperStorage(p, storageLevel)));
        int broken = 0;
        try {
            for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
                if (candidate.distSqr(center) > radius * radius || !l.hasChunkAt(candidate)
                        || !l.mayInteract(p, candidate) || !EsperDomain.allows(l, p, Vec3.atCenterOf(candidate))) continue;
                var state = l.getBlockState(candidate);
                if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(l, candidate) < 0
                        || !state.getFluidState().isEmpty()) continue;
                var be = candidate.immutable();
                if (p.gameMode.destroyBlock(be)) {
                    broken++;
                    burst(l, Vec3.atCenterOf(be), ParticleTypes.REVERSE_PORTAL, 6, .35);
                    if (broken >= 48 + level * 10) break;
                }
            }
        } finally {
            if (previous == null) HARVEST.remove(); else HARVEST.set(previous);
        }
        if (broken == 0) return false;
        Vec3 at = Vec3.atCenterOf(center);
        ring(l, at, radius + .3, VIOLET, false); ring(l, at, radius + .3, CYAN, true);
        sound(l, at, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), .65F);
        return true;
    }
    static void blockDrops(BlockDropsEvent event) {
        Harvest harvest = HARVEST.get();
        if (harvest == null || event.getBreaker() != harvest.caster || event.isCanceled()) return;
        event.getDrops().removeIf(drop -> {
            ItemStack rest = harvest.storage.store(drop.getItem());
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
    private static Vec3 behind(ServerPlayer p, LivingEntity target, double range) {
        Vec3 forward = Vec3.directionFromRotation(0, target.getYRot());
        double gap = (p.getBbWidth() + target.getBbWidth()) / 2 + .55;
        for (double depth = 0; depth <= 1.5; depth += .5) {
            Vec3 base = target.position().subtract(forward.scale(gap + depth));
            for (int dy : new int[]{0, 1, -1}) {
                Vec3 at = base.add(0, dy, 0);
                if (p.distanceToSqr(at) <= (range + 2) * (range + 2) && EsperDomain.allows(p.serverLevel(), p, at)
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
        if (target.isPassenger()) target.stopRiding();
        BINDINGS.put(target, new Binding(owner.getUUID(), owner.serverLevel(), target.position(),
                owner.serverLevel().getGameTime() + duration));
        push(target, Vec3.ZERO);
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
        if (level.getGameTime() % 6 == 0) ring(level, binding.anchor.add(0, .75, 0), .9, VIOLET, false);
    }
    static void endSchoolEffects(ServerPlayer player, CompoundTag data) {
        stopShift(player, data);
        for (String key : new String[]{"Sling", "Barrier", "BarrierHits", "Insulation", "InsulationHits", "Overdrive"})
            data.remove(key);
        List<Portal> portals = PORTALS.get(player.serverLevel());
        if (portals != null) portals.removeIf(portal -> portal.owner.equals(player.getUUID()));
        BINDINGS.values().removeIf(binding -> binding.owner.equals(player.getUUID()));
    }

    static void tickPlayer(ServerPlayer p, CompoundTag data, long now) {
        EsperElectric.tick(p, data, now);
        if (now < data.getLong("Shift") && !p.isPassenger()) {
            p.resetFallDistance();
            if (now % 3 == 0) burst(p.serverLevel(), p.position().add(0, .3, 0), ParticleTypes.END_ROD, 3, .22);
        } else if (data.contains("ShiftGranted")) {
            stopShift(p, data);
            data.putLong("Sling", now + 100);
        }
        if (now < data.getLong("Barrier") && data.getInt("BarrierHits") > 0 && now % 8 == 0) {
            Vec3 center = p.position().add(0, .95, 0);
            ring(p.serverLevel(), center, 1.35, CYAN, false);
            ring(p.serverLevel(), center, 1.35, VIOLET, true);
        }
    }
    static void stopShift(Player p, CompoundTag data) {
        data.remove("Shift");
        if (!data.getBoolean("ShiftGranted")) return;
        data.remove("ShiftGranted");
        if (p.isCreative() || p.isSpectator()) return;
        p.getAbilities().mayfly = false;
        p.getAbilities().flying = false;
        p.resetFallDistance();
        p.onUpdateAbilities();
    }
    static void protect(ServerPlayer p, LivingIncomingDamageEvent event) {
        CompoundTag data = p.getPersistentData().getCompound(Esper.DATA);
        long now = p.serverLevel().getServer().overworld().getGameTime();
        if (EsperElectric.protect(p, data, now, event)) return;
        if (event.getSource().is(DamageTypeTags.IS_FALL) && (now < data.getLong("Sling") || now < data.getLong("Shift"))) {
            event.setCanceled(true); data.remove("Sling"); return;
        }
        if (now >= data.getLong("Barrier") || data.getInt("BarrierHits") <= 0
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || (event.getSource().getDirectEntity() == null && !event.getSource().is(DamageTypeTags.IS_EXPLOSION))) return;
        data.putInt("BarrierHits", data.getInt("BarrierHits") - 1);
        event.setCanceled(true);
        Vec3 center = p.position().add(0, 1, 0);
        burst(p.serverLevel(), center, ParticleTypes.END_ROD, 22, .6);
        sound(p.serverLevel(), center, SoundEvents.AMETHYST_BLOCK_BREAK, .8F);
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
            if (now % 8 == 0) {
                ring(l, portal.a.add(0, 1, 0), 1.05, VIOLET, true);
                ring(l, portal.b.add(0, 1, 0), 1.05, CYAN, true);
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
    static void launch(ServerLevel l, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        ring(l, center.add(0, .7, 0), 1.35, CYAN, false);
        for (int i = 1; i <= 20; i++) {
            double phase = i * .58;
            Vec3 point = center.add(Math.cos(phase) * .42, i * .52, Math.sin(phase) * .42);
            burst(l, point, i < 6 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.END_ROD, 3, .1);
        }
        sound(l, center, SoundEvents.FIREWORK_ROCKET_LAUNCH, .72F);
    }
    static void sound(ServerLevel l, Vec3 at, SoundEvent sound, float pitch) {
        l.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, .75F, pitch);
    }
    private static void dot(ServerLevel l, Vec3 at, ParticleOptions effect) {
        l.sendParticles(effect, at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }
    static void burst(ServerLevel l, Vec3 at, ParticleOptions effect, int count, double spread) {
        l.sendParticles(effect, at.x, at.y, at.z, count, spread, spread, spread, .07);
    }
    static void ring(ServerLevel l, Vec3 at, double radius, ParticleOptions effect, boolean vertical) {
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12;
            double x = Math.cos(angle) * radius, y = Math.sin(angle) * radius;
            dot(l, vertical ? at.add(x, y, 0) : at.add(x, 0, y), effect);
        }
    }
    static void beam(ServerLevel l, Vec3 a, Vec3 b, ParticleOptions effect) {
        int count = Math.clamp((int) (a.distanceTo(b) * 5), 2, 55);
        for (int i = 0; i <= count; i++) dot(l, a.lerp(b, (double) i / count), effect);
    }
}
