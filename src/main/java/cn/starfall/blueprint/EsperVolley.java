package cn.starfall.blueprint;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class EsperVolley {
    private record Charge(ServerLevel world, int slot, int level, long since) {}
    private static final Map<ServerPlayer, Charge> CHARGING = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> HEARTBEATS = new WeakHashMap<>();
    static boolean active(ServerPlayer p) { return CHARGING.containsKey(p); }
    static void hold(ServerPlayer p, int slot, long now) {
        var c = CHARGING.get(p); if (c != null && c.slot == slot) HEARTBEATS.put(p, now);
    }

    static boolean begin(ServerPlayer player, int slot, int level, long now) {
        if (CHARGING.containsKey(player) || EsperChannel.active(player)
                || new EsperStorage(player, Esper.level(Esper.data(player), Esper.Skill.STORAGE)).firstAmmo() < 0) return false;
        HEARTBEATS.put(player, now);
        CHARGING.put(player, new Charge(player.serverLevel(), slot, level, now));
        EsperNetworking.aura(player, EsperNetworking.Aura.VOLLEY, 1, 12);
        EsperEffects.sound(player.serverLevel(), player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.15F);
        return true;
    }

    static void cancel(ServerPlayer player) {
        HEARTBEATS.remove(player);
        if (CHARGING.remove(player) != null && player.connection != null)
            EsperNetworking.aura(player, EsperNetworking.Aura.VOLLEY, 0, 0);
    }

    private static boolean valid(ServerPlayer player, CompoundTag data, Charge charge, long now) {
        return Esper.canCast(player) && data.getBoolean("Mode") && player.serverLevel() == charge.world
                && Esper.school(data) == Esper.School.SPACE && Esper.slot(data, charge.slot) == Esper.Skill.VOLLEY.ordinal()
                && Esper.level(data, Esper.Skill.VOLLEY) == charge.level
                && now - HEARTBEATS.getOrDefault(player, charge.since) <= 12;
    }

    static void tick(ServerPlayer player, CompoundTag data, long now) {
        var charge = CHARGING.get(player);
        if (charge == null) return;
        if (!valid(player, data, charge, now)) {
            cancel(player);
        } else if ((now - charge.since) % 4 == 0) {
            EsperNetworking.aura(player, EsperNetworking.Aura.VOLLEY, shotCount(charge.level, now - charge.since), 12);
        }
    }

    static int shotCount(int level, long held) { return (int) Math.min(8 + 2 * level, 1 + Math.max(0, held) / 5); }

    static boolean release(ServerPlayer player, int slot, long now) {
        var charge = CHARGING.get(player);
        if (charge == null || slot != charge.slot) return false;
        var data = Esper.data(player);
        boolean allowed = valid(player, data, charge, now);
        cancel(player);
        var skill = Esper.Skill.VOLLEY;
        if (!allowed || data.getInt("Power") < skill.cost
                || now < data.getLong("Ready_" + skill.key)) return false;

        var level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = EsperEffects.trace(player, 44);
        var target = EsperEffects.target(player, end, entity -> EsperEffects.canAffect(player, entity));
        Vec3 aim = target == null ? end : target.getBoundingBox().getCenter();
        if (aim.distanceToSqr(eye) < 1.0) aim = eye.add(player.getLookAngle().scale(2));
        var storage = new EsperStorage(player, Esper.level(data, Esper.Skill.STORAGE));
        int count = shotCount(charge.level, now - charge.since);
        int fired = 0;
        for (int lane = 0; fired < count && lane < 8 + 2 * charge.level; lane++) {
            int ammo = storage.firstAmmo();
            if (ammo < 0) break;
            Vec3 from = origin(player.position(), player.getYRot(), lane);
            if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(from))
                    || level.clip(new ClipContext(eye, from, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                            .getType() != HitResult.Type.MISS) continue;
            var shot = Esper.SHOT.get().create(level);
            if (shot == null) continue;
            shot.prepare(player, storage.getItem(ammo).copyWithCount(1), charge.level, from, false);
            shot.returnSlot = ammo;
            Vec3 direction = aim.subtract(from).normalize();
            shot.shoot(direction.x, direction.y, direction.z, 2.1F + charge.level * .14F + (fired % 4) * .16F, 0);
            if (level.addFreshEntity(shot)) {
                storage.removeItem(ammo, 1);
                fired++;
                EsperNetworking.fracture(level, EsperNetworking.Pattern.RIFT, from, direction, .42F, 12);
            }
        }
        if (fired == 0) return false;
        Esper.completeCast(data, skill, charge.level, now);
        data.putLong("NextCast", now + 4);
        EsperEffects.sound(level, player.position(), SoundEvents.TRIDENT_THROW.value(), .76F);
        return true;
    }

    public static Vec3 origin(Vec3 feet, float yaw, int index) {
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 sideways = forward.cross(new Vec3(0, 1, 0));
        int row = index / 6;
        int column = (index % 6) / 2;
        double side = (index % 2 == 0 ? -1 : 1) * (.95 + column * .62 + row * .11);
        return feet.add(forward.scale(.90 + row * .22 + column * .15))
                .add(sideways.scale(side)).add(0, 1.32 + row * .62 + column * .18, 0);
    }
    private EsperVolley() {}
}
