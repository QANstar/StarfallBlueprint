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
    private static final int MAX_HOLD = 160;

    static boolean begin(ServerPlayer player, int slot, int level, long now) {
        if (CHARGING.containsKey(player)) return false;
        CHARGING.put(player, new Charge(player.serverLevel(), slot, level, now));
        EsperNetworking.aura(player, EsperNetworking.Aura.VOLLEY, blades(level, 0), 12);
        EsperEffects.sound(player.serverLevel(), player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.15F);
        return true;
    }

    static void cancel(ServerPlayer player) {
        if (CHARGING.remove(player) != null && player.connection != null)
            EsperNetworking.aura(player, EsperNetworking.Aura.VOLLEY, 0, 0);
    }

    private static boolean valid(ServerPlayer player, CompoundTag data, Charge charge, long now) {
        return Esper.canCast(player) && data.getBoolean("Mode") && player.serverLevel() == charge.world
                && Esper.school(data) == Esper.School.SPACE && Esper.slot(data, charge.slot) == Esper.Skill.VOLLEY.ordinal()
                && Esper.level(data, Esper.Skill.VOLLEY) == charge.level && now - charge.since < MAX_HOLD;
    }

    static void tick(ServerPlayer player, CompoundTag data, long now) {
        var charge = CHARGING.get(player);
        if (charge == null) return;
        if (!valid(player, data, charge, now)) {
            cancel(player);
        } else if ((now - charge.since) % 4 == 0) {
            EsperNetworking.aura(player, EsperNetworking.Aura.VOLLEY, blades(charge.level, now - charge.since), 12);
        }
    }

    static int blades(int level, long held) { return Math.min(8 + 2 * level, 2 + (int) Math.max(0, held) / 2); }

    static boolean release(ServerPlayer player, int slot, long now) {
        var charge = CHARGING.get(player);
        if (charge == null || slot != charge.slot) return false;
        cancel(player);
        var data = Esper.data(player);
        var skill = Esper.Skill.VOLLEY;
        if (!valid(player, data, charge, now) || data.getInt("Power") < skill.cost
                || now < data.getLong("Ready_" + skill.key)) return false;

        var level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = EsperEffects.trace(player, 44);
        var target = EsperEffects.target(player, end, entity -> EsperEffects.canAffect(player, entity));
        Vec3 aim = target == null ? end : target.getBoundingBox().getCenter();
        if (aim.distanceToSqr(eye) < 1.0) aim = eye.add(player.getLookAngle().scale(2));
        var storage = new EsperStorage(player, Esper.level(data, Esper.Skill.STORAGE));
        int fired = 0;
        for (int i = 0; i < blades(charge.level, now - charge.since); i++) {
            int ammo = storage.firstAmmo();
            var shot = Esper.SHOT.get().create(level);
            if (shot == null) continue;
            Vec3 summoned = origin(player.position(), player.getYRot(), i);
            boolean blocked = !level.hasChunkAt(net.minecraft.core.BlockPos.containing(summoned))
                    || level.clip(new ClipContext(eye, summoned, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                            .getType() != HitResult.Type.MISS;
            Vec3 from = blocked ? eye : summoned;
            shot.prepare(player, ammo < 0 ? Items.IRON_SWORD.getDefaultInstance() : storage.getItem(ammo).copyWithCount(1),
                    charge.level, from, ammo < 0);
            Vec3 direction = aim.subtract(from).normalize();
            shot.shoot(direction.x, direction.y, direction.z, 2.1F + charge.level * .14F + (i % 4) * .16F, 0);
            if (level.addFreshEntity(shot)) {
                if (ammo >= 0) storage.removeItem(ammo, 1);
                fired++;
            }
        }
        if (fired == 0) return false;
        Esper.completeCast(data, skill, charge.level, now);
        data.putLong("NextCast", now + 4);
        EsperNetworking.fracture(level, EsperNetworking.Pattern.RIFT, eye.add(player.getLookAngle().scale(.7)),
                player.getLookAngle(), .85F, 16);
        EsperEffects.sound(level, player.position(), SoundEvents.TRIDENT_THROW.value(), .76F);
        return true;
    }

    public static Vec3 origin(Vec3 feet, float yaw, int index) {
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 sideways = forward.cross(new Vec3(0, 1, 0));
        int row = index / 6;
        int column = (index % 6) / 2;
        double side = (index % 2 == 0 ? -1 : 1) * (.68 + column * .8 + row * .11);
        return feet.subtract(forward.scale(1.15 + row * .34 - column * .16))
                .add(sideways.scale(side)).add(0, 1.55 + row * .79 + column * .24, 0);
    }
    private EsperVolley() {}
}
