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
        if (ACTIVE.size() >= 256) ACTIVE.removeFirst();
        ACTIVE.add(makeEffect(packet, level.getGameTime()));
    }

    private static Effect makeEffect(Fracture packet, long started) {
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
        return new Effect(packet, started, u, v, List.copyOf(shards));
    }

    static void preview(Fracture packet, VertexConsumer out, Matrix4f pose, float age) {
        draw(makeEffect(packet, 0), out, pose, Vec3.ZERO, age);
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
            if (entity != mc.player || !mc.options.getCameraType().isFirstPerson())
                EsperSpatialGeometry.shell(out, pose, center, Esper.BARRIER_RADIUS * pulse, fade * .16F, 0);
            else {
                double phase = (tick - aura.started + partial) % 64 / 18;
                if (phase < 1) {
                    Vec3 forward = entity.getViewVector(partial);
                    Vec3 right = Vec3.directionFromRotation(0, entity.getViewYRot(partial) + 90);
                    float opacity = (float) Math.sin(Math.PI * phase);
                    ripple(out, pose, forward.scale(.95), forward, right, .47 + phase * .22,
                            opacity * .23F, .006, 0xa6e9ff, true);
                    ripple(out, pose, forward.scale(.95), forward, right, .43 + phase * .22,
                            opacity * .11F, .004, 0x74caff, true);
                }
            }
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

            }
        });
        buffers.endBatch(TYPE);
    }

    private static void draw(Effect e, VertexConsumer out, Matrix4f pose, Vec3 camera, float t) {
        Vec3 center = e.packet.center().subtract(camera);
        Pattern pattern = e.packet.pattern();
        float alpha = (1 - t) * Math.min(1, .35F + t * 7);
        double radius = e.packet.radius();
        if (pattern == Pattern.LIGHTNING || pattern == Pattern.ELECTRIC_RING || pattern == Pattern.ELECTRIC_HIT) {
            electric(e, out, pose, center, t);
            return;
        }
        if (pattern == Pattern.GUARD || pattern == Pattern.GUARD_MELEE) {
            double outer = radius * (.22 + t * 1.16);
            double inner = radius * (.10 + t * .91);
            ripple(out, pose, center, e.packet.normal(), e.u, outer, alpha * .21F, .043, 0x57b8ff, false);
            ripple(out, pose, center, e.packet.normal(), e.u, outer, alpha * .93F, .012, 0xe3f9ff, false);
            ripple(out, pose, center, e.packet.normal(), e.u, inner, alpha * .67F, .010, 0x8addff, false);
            return;
        }
        if (pattern == Pattern.TARGET_SLASH) {
            for (int slash = 0; slash < 3; slash++) {
                float age = Math.clamp(t * 1.7F - slash * .22F, 0, 1);
                if (age <= 0 || age >= 1) continue;
                double angle = (e.packet.seed() % 100) * .07 + slash * 1.91;
                Vec3 along = e.u.scale(Math.cos(angle)).add(e.v.scale(Math.sin(angle)));
                Vec3 cross = e.packet.normal().cross(along).normalize();
                Vec3 previous = center.add(along.scale(-radius));
                for (int i = 1; i <= 16; i++) {
                    double x = i / 8.0 - 1;
                    Vec3 next = center.add(along.scale(x * radius)).add(cross.scale((1-x*x)*radius*.24));
                    float fade = (1-age) * (float)Math.sin(Math.PI * i / 17);
                    EsperSpatialGeometry.segment(out,pose,previous,next,e.packet.normal(),.11,0x3b82c4,fade*.35F);
                    EsperSpatialGeometry.segment(out,pose,previous,next,e.packet.normal(),.027,0xedffff,fade);
                    previous = next;
                }
            }
            return;
        }
        if (pattern == Pattern.SINGULARITY || pattern == Pattern.DETONATION) {
            double scale = pattern == Pattern.SINGULARITY ? 1 + .025 * Math.sin(t * Math.TAU) : 1 + t * .65;
            EsperSpatialGeometry.shell(out, pose, center, radius * scale, (1-t)*.65F,
                    pattern == Pattern.DETONATION ? t * 1.3 : 0);
            for (int ring = 0; ring < 3; ring++) {
                Vec3 axis = ring == 0 ? e.u : ring == 1 ? e.v : e.packet.normal();
                Vec3 u = axis.cross(Math.abs(axis.y)>.95 ? new Vec3(1,0,0) : new Vec3(0,1,0)).normalize();
                Vec3 v = axis.cross(u).normalize();
                for (int i = 0; i < 44; i++) {
                    if (i%9 == 8) continue;
                    double a = i*Math.TAU/44+t*2, b = (i+1)*Math.TAU/44+t*2;
                    Vec3 left = center.add(polar(u,v,a,radius*scale*(.94+.06*Math.sin(a*5))));
                    Vec3 right = center.add(polar(u,v,b,radius*scale*(.94+.06*Math.sin(b*5))));
                    EsperSpatialGeometry.segment(out,pose,left,right,axis,.018,0xa8dfff,(1-t)*.8F);
                }
            }
            return;
        }
        if (pattern == Pattern.WARP) {
            for (int i = 0; i < 38; i++) {
                double a = i*Math.TAU/38, b=(i+1)*Math.TAU/38;
                Vec3 left = center.add(polar(e.u,e.v,a,radius*(1+.17*Math.sin(a*5+t*4))));
                Vec3 right = center.add(polar(e.u,e.v,b,radius*(1+.17*Math.sin(b*5+t*4))));
                EsperSpatialGeometry.segment(out,pose,left,right,e.packet.normal(),.024,0xd5faff,alpha);
            }
            return;
        }
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
    private static void ripple(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 axis, Vec3 u,
            double radius, float alpha, double width, int color, boolean peripheral) {
        Vec3 v = axis.cross(u).normalize();
        for (int i = 0; i < 48; i++) {
            double a = i * Math.TAU / 48, b = (i + 1) * Math.TAU / 48;
            float taper = peripheral ? (float) Math.pow(Math.abs(Math.cos((a + b) * .5)), 9) : 1F;
            if (taper < .03F) continue;
            Vec3 from = center.add(polar(u, v, a, radius));
            Vec3 to = center.add(polar(u, v, b, radius));
            EsperSpatialGeometry.segment(out, pose, from, to, axis, width, color, alpha * taper);
        }
    }

    private static void electric(Effect e, VertexConsumer out, Matrix4f pose, Vec3 center, float t) {
        var random = new Random(e.packet.seed() + (int)(t * e.packet.life() / 2) * 313L);
        float alpha = (1-t) * .95F;
        double radius = e.packet.radius();
        if (e.packet.pattern() == Pattern.LIGHTNING) {
            Vec3 previous = center;
            int count = Math.clamp((int)(radius*3),8,80);
            for (int i = 1; i <= count; i++) {
                Vec3 next = center.add(e.packet.normal().scale(radius*i/count));
                if (i != count) next = next.add(e.u.scale((random.nextDouble()-.5)*.65))
                        .add(e.v.scale((random.nextDouble()-.5)*.65));
                wire(out,pose,previous,next,e.u,e.v,alpha,1);
                if (i % 4 == 1) {
                    Vec3 branch = previous.add(e.u.scale((random.nextDouble()-.5)*1.8))
                            .add(e.v.scale((random.nextDouble()-.5)*1.8)).add(e.packet.normal().scale(.4));
                    wire(out,pose,previous,previous.lerp(branch,.55).add(e.v.scale(.17)),e.u,e.v,alpha*.7F,.55);
                    wire(out,pose,previous.lerp(branch,.55).add(e.v.scale(.17)),branch,e.u,e.v,alpha*.45F,.3);
                }
                previous = next;
            }
        } else {
            boolean ring = e.packet.pattern() == Pattern.ELECTRIC_RING;
            Vec3 previous = center.add(e.u.scale(radius));
            int count = ring ? 36 : 9;
            for (int i = 1; i <= count; i++) {
                double angle = i*Math.TAU/count;
                double r = radius * (.85 + random.nextDouble()*.3) * (ring ? .85+t*.3 : 1);
                Vec3 next = ring ? center.add(polar(e.u,e.v,angle,r))
                        : center.add(e.u.scale((random.nextDouble()-.5)*r*2)).add(e.v.scale((random.nextDouble()-.5)*r*2));
                wire(out,pose,ring?previous:center,next,e.u,e.packet.normal(),alpha,.7);
                previous = next;
            }
        }
    }
    private static void wire(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b, Vec3 u, Vec3 v, float alpha, double scale) {
        for (Vec3 plane : new Vec3[]{u,v}) {
            EsperSpatialGeometry.segment(out,pose,a,b,plane,.095*scale,0x135cff,alpha*.15F);
            EsperSpatialGeometry.segment(out,pose,a,b,plane,.039*scale,0x399bff,alpha*.65F);
            EsperSpatialGeometry.segment(out,pose,a,b,plane,.012*scale,0xdaf6ff,alpha);
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
