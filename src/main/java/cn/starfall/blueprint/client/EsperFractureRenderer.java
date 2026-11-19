package cn.starfall.blueprint.client;

import cn.starfall.blueprint.EsperNetworking.Fracture;
import cn.starfall.blueprint.EsperNetworking.Pattern;
import cn.starfall.blueprint.EsperNetworking.Aura;
import cn.starfall.blueprint.Esper;
import cn.starfall.blueprint.EsperVolley;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = StarfallBlueprint.ID, value = Dist.CLIENT)
public final class EsperFractureRenderer {
    private static final RenderType TYPE = RenderType.debugStructureQuads();
    private static final List<Effect> ACTIVE = new ArrayList<>();
    private static final HashMap<Integer, VisibleAura> GUARDS = new HashMap<>(), VOLLEYS = new HashMap<>();
    private static ClientLevel world;
    private record Shard(Vec3 a, Vec3 b, Vec3 c, Vec3 drift, double spin) {}
    private record Effect(Fracture packet, long started, Vec3 u, Vec3 v, List<Shard> shards) {}
    private record VisibleAura(int amount, long started, long until) {}

    public static void receive(Fracture packet) {
        var level = Minecraft.getInstance().level;
        selectWorld(level);
        if (level == null || packet.life() <= 0) return;
        if (ACTIVE.size() >= 96) ACTIVE.removeFirst();
        Vec3 n = packet.normal().lengthSqr() < .01 ? new Vec3(0, 0, 1) : packet.normal();
        Vec3 u = n.cross(Math.abs(n.y) > .95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = n.cross(u).normalize();
        var random = new Random(packet.seed());
        var shards = new ArrayList<Shard>();
        int count = packet.pattern() == Pattern.SHATTER ? 16 : 11;
        double[] tip = new double[count + 1];
        for (int i = 0; i < count; i++) tip[i] = .68 + random.nextDouble() * .32;
        tip[count] = tip[0];
        for (int i = 0; i < count; i++) {
            double a = Math.TAU * i / count, b = Math.TAU * (i + 1) / count;
            Vec3 p = polar(u, v, a, tip[i]), q = polar(u, v, b, tip[i + 1]);
            double inner = packet.pattern() == Pattern.PORTAL || packet.pattern() == Pattern.GUARD ? .59 : .06;
            Vec3 anchor = polar(u, v, (a + b) * .5, inner + random.nextDouble() * .22);
            Vec3 drift = p.add(q).scale(.35 + random.nextDouble() * .4).add(n.scale(random.nextDouble() - .5));
            shards.add(new Shard(anchor, p, q, drift, random.nextDouble() * 2 - 1));
        }
        ACTIVE.add(new Effect(packet, level.getGameTime(), u, v, List.copyOf(shards)));
    }

    public static void receiveAura(Aura packet) {
        selectWorld(Minecraft.getInstance().level);
        if (world == null) return;
        var auras = packet.kind() == Aura.GUARD ? GUARDS : VOLLEYS;
        if (packet.life() == 0 || packet.amount() == 0) auras.remove(packet.entity());
        else {
            long now = world.getGameTime();
            var prior = auras.get(packet.entity());
            auras.put(packet.entity(), new VisibleAura(packet.amount(), prior == null ? now : prior.started, now + packet.life()));
        }
    }

    private static void selectWorld(ClientLevel level) {
        if (world == level) return;
        ACTIVE.clear();
        GUARDS.clear();
        VOLLEYS.clear();
        world = level;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        selectWorld(mc.level);
        if (world == null) return;
        long tick = world.getGameTime();
        ACTIVE.removeIf(effect -> tick - effect.started >= effect.packet.life());
        GUARDS.values().removeIf(aura -> aura.until <= tick);
        VOLLEYS.values().removeIf(aura -> aura.until <= tick);
        if (ACTIVE.isEmpty() && GUARDS.isEmpty() && VOLLEYS.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        var effects = new ArrayList<>(ACTIVE);
        effects.sort(Comparator.comparingDouble((Effect e) -> camera.distanceToSqr(e.packet.center())).reversed());
        var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(TYPE);
        Matrix4f pose = event.getPoseStack().last().pose();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Effect effect : effects) {
            float age = (tick - effect.started + partial) / effect.packet.life();
            draw(effect, out, pose, camera, Math.clamp(age, 0, 1));
        }
        GUARDS.forEach((id, aura) -> {
            var entity = world.getEntity(id);
            if (entity == null || !entity.isAlive() || entity.distanceToSqr(camera) > 4096) return;
            Vec3 center = entity.getPosition(partial).add(0, .96, 0).subtract(camera);
            float fade = Math.min(1, (aura.until - tick - partial) / 8F);
            double pulse = .98 + .025 * Math.sin((tick - aura.started + partial) * .17);
            EsperSpatialGeometry.shell(out, pose, center, Esper.BARRIER_RADIUS * pulse, fade * .72F, 0);
        });
        VOLLEYS.forEach((id, aura) -> {
            var entity = world.getEntity(id);
            if (entity == null || !entity.isAlive() || entity.distanceToSqr(camera) > 4096) return;
            float yaw = entity.getViewYRot(partial);
            Vec3 axis = Vec3.directionFromRotation(0, yaw);
            for (int i = 0; i < aura.amount; i++) {
                double time = tick - aura.started + partial;
                Vec3 origin = EsperVolley.origin(entity.getPosition(partial), yaw, i).subtract(camera);
                EsperSpatialGeometry.portal(out, pose, origin, axis, time * .052 + i * .36, .32 + (i % 3) * .04);
                Vec3 sword = origin.add(axis.scale(.3)).add(0, Math.sin(time * .21 + i * .57) * .055, 0);
                EsperSpatialGeometry.sword(out, pose, sword, axis.add(0, .05, 0), .94 + (i % 4) * .07, 0xe7fcff, false);
            }
        });
        buffers.endBatch(TYPE);
    }

    private static void draw(Effect e, VertexConsumer out, Matrix4f pose, Vec3 camera, float t) {
        Vec3 center = e.packet.center().subtract(camera);
        Pattern pattern = e.packet.pattern();
        float alpha = (1 - t) * Math.min(1, .35F + t * 7);
        double radius = e.packet.radius();
        if (pattern == Pattern.SLICE) {
            EsperSpatialGeometry.crescent(out, pose, center, e.packet.normal(), e.packet.radius(), t);
            return;
        }
        if (pattern == Pattern.COLLAPSE) {
            float burst = Math.max(0, (t * 44 - 16) / 28);
            double growth = Math.clamp(t * 44 / 16, .18, 1);
            float flash = (float) Math.exp(-Math.pow((t * 44 - 17) / 3.0, 2));
            EsperSpatialGeometry.shell(out, pose, center, radius * growth, (1 - burst) * (.88F + flash * .52F), burst * .95);
            if (burst == 0) EsperSpatialGeometry.shell(out, pose, center, radius * growth * .84, .26F, 0);
            return;
        }
        if (pattern == Pattern.BUBBLE_IMPACT) {
            EsperSpatialGeometry.shell(out, pose, center, radius, alpha * 1.4F, t * .86);
            return;
        }
        if (center.lengthSqr() < .28) return;
        double growth = pattern == Pattern.SHATTER || pattern == Pattern.WAKE ? .72 + t * .74 : .87 + t * .23;
        for (Shard shard : e.shards) {
            double breakup = pattern == Pattern.PORTAL || pattern == Pattern.GUARD || pattern == Pattern.BIND ? .12 : .9;
            Vec3 drift = shard.drift.scale(t * t * breakup);
            double angle = shard.spin * t * t * 1.8;
            Vec3 a = transform(e, shard.a, drift, angle, growth * radius, center);
            Vec3 b = transform(e, shard.b, drift, angle, growth * radius, center);
            Vec3 c = transform(e, shard.c, drift, angle, growth * radius, center);
            int tint = shard.spin > 0 ? 0xeefbff : 0xc2e3ed;
            quad(out, pose, a, b, c, c, tint, alpha * (pattern == Pattern.SHATTER ? .40F : .24F));
            edge(out, pose, a, b, e, radius * .017, 0xf8ffff, alpha * .84F);
            edge(out, pose, b, c, e, radius * .009, 0xffffff, alpha * .65F);
            edge(out, pose, a, c, e, radius * .006, 0xccefff, alpha * .48F);
        }
        if (pattern != Pattern.SHATTER && pattern != Pattern.SLICE) {
            double rim = radius * (.78 + t * .66);
            for (int i = 0; i < 24; i++) {
                if (i % 6 == 5) continue;
                Vec3 a = center.add(polar(e.u, e.v, i * Math.TAU / 24, rim));
                Vec3 b = center.add(polar(e.u, e.v, (i + 1) * Math.TAU / 24, rim));
                edge(out, pose, a, b, e, radius * .012, 0xe5fcff, alpha * .65F);
            }
        }
    }
    private static Vec3 transform(Effect e, Vec3 p, Vec3 drift, double spin, double scale, Vec3 center) {
        double x = p.dot(e.u), y = p.dot(e.v);
        Vec3 planar = e.u.scale(x * Math.cos(spin) - y * Math.sin(spin))
                .add(e.v.scale((x * Math.sin(spin) + y * Math.cos(spin))
                        * (e.packet.pattern() == Pattern.SLICE ? .23 : 1)));
        return center.add(planar.add(drift).scale(scale));
    }
    private static Vec3 polar(Vec3 u, Vec3 v, double angle, double radius) {
        return u.scale(Math.cos(angle) * radius).add(v.scale(Math.sin(angle) * radius));
    }
    private static void edge(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b, Effect e, double width, int rgb, float alpha) {
        Vec3 offset = b.subtract(a).cross(e.packet.normal()).normalize().scale(width);
        quad(out, pose, a.subtract(offset), b.subtract(offset), b.add(offset), a.add(offset), rgb, alpha);
    }
    private static void quad(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alpha) {
        int color = ((int) (alpha * 255) << 24) | rgb;
        vertex(out, pose, a, color); vertex(out, pose, b, color); vertex(out, pose, c, color); vertex(out, pose, d, color);
    }
    private static void vertex(VertexConsumer out, Matrix4f pose, Vec3 vertex, int color) {
        out.addVertex(pose, (float) vertex.x, (float) vertex.y, (float) vertex.z).setColor(color);
    }
    private EsperFractureRenderer() {}
}
