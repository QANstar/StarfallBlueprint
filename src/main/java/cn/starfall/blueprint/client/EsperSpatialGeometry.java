package cn.starfall.blueprint.client;

import cn.starfall.blueprint.Esper;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class EsperSpatialGeometry {
    static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int PEARL = 0xf2ffff, ICE = 0xb9efff, BLUE = 0x689aae, GOLD = 0xf7dca3;
    private static final List<Pane> GLASS = makeGlass();
    private record Pane(Vec3 a, Vec3 b, Vec3 c, Vec3 normal, double seed) {}

    private static List<Pane> makeGlass() {
        Random random = new Random(92631);
        int rows = 8, columns = 14;
        Vec3[][] grid = new Vec3[rows + 1][columns];
        for (int lat = 0; lat <= rows; lat++) for (int i = 0; i < columns; i++) {
            if (lat == 0 || lat == rows) grid[lat][i] = UP.scale(lat == 0 ? -1 : 1);
            else {
                double theta = (i + lat * .5 + random.nextDouble() * .45) * Math.TAU / columns;
                double phi = (lat + (random.nextDouble() - .5) * .46) * Math.PI / rows - Math.PI / 2;
                grid[lat][i] = new Vec3(Math.cos(theta) * Math.cos(phi), Math.sin(phi), Math.sin(theta) * Math.cos(phi));
            }
        }
        var panes = new ArrayList<Pane>();
        for (int lat = 0; lat < rows; lat++) for (int i = 0; i < columns; i++) {
            int next = (i + 1) % columns;
            if (lat != 0) addPane(panes, grid[lat][i], grid[lat][next], grid[lat + 1][i], random.nextDouble());
            if (lat != rows - 1)
                addPane(panes, grid[lat + 1][i], grid[lat][next], grid[lat + 1][next], random.nextDouble());
        }
        return List.copyOf(panes);
    }

    private static void addPane(List<Pane> panes, Vec3 a, Vec3 b, Vec3 c, double seed) {
        panes.add(new Pane(a, b, c, a.add(b).add(c).normalize(), seed));
    }

    static void shell(VertexConsumer out, Matrix4f pose, Vec3 center, double radius, float alpha, double separation) {
        if (alpha <= 0 || radius <= .08) return;
        boolean inside = center.lengthSqr() < radius * radius;
        for (Pane pane : GLASS) {
            double drift = separation * (.38 + pane.seed * .85);
            Vec3 displaced = center.add(pane.normal.scale(radius * drift)).add(0, -separation * separation * .65, 0);
            Vec3 a = displaced.add(pane.a.scale(radius));
            Vec3 b = displaced.add(pane.b.scale(radius));
            Vec3 c = displaced.add(pane.c.scale(radius));
            float rim = 1F - (float) Math.abs(pane.normal.dot(a.normalize()));
            float fill = (inside ? .025F : .055F) + rim * .13F + (float) pane.seed * .048F;
            quad(out, pose, a, b, c, c, pane.seed > .46 ? PEARL : ICE, alpha * fill);
            float edgeAlpha = alpha * ((inside ? .14F : .32F) + rim * .33F);
            double edge = Math.clamp(radius * .0036, .009, .036);
            segment(out, pose, a, b, pane.normal, edge, PEARL, edgeAlpha);
            if (separation > 0 || pane.seed > .22) segment(out, pose, b, c, pane.normal, edge * .7, ICE, edgeAlpha);
            if (separation > 0 || pane.seed < .45) segment(out, pose, c, a, pane.normal, edge * .7, PEARL, edgeAlpha);
            if (pane.seed > .86) {
                Vec3 branch = a.lerp(b, .43).lerp(c, .33);
                segment(out, pose, a.lerp(c, .37), branch, pane.normal, edge * .63, PEARL, edgeAlpha);
                segment(out, pose, branch, b.lerp(c, .14), pane.normal, edge * .63, ICE, edgeAlpha);
            }
        }
    }

    static void crescent(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 forward, float radius, float t) {
        double progress = Math.min(t * 3.3, 1);
        double reach = radius * (.23 + .77 * (1 - Math.pow(1 - progress, 2)));
        float alpha = (float) Math.pow(1 - t, 1.7);
        Vec3 side = forward.cross(UP).normalize();
        for (int i = 0; i < 42; i++) {
            double left = i / 42.0, right = (i + 1) / 42.0;
            Vec3 a = arc(center, forward, side, left, reach, 0);
            Vec3 b = arc(center, forward, side, right, reach, 0);
            double leftTaper = Math.sin(Math.PI * left), rightTaper = Math.sin(Math.PI * right);
            Vec3 c = arc(center, forward, side, right, reach, 1.34 * rightTaper).add(0, -.42 * rightTaper, 0);
            Vec3 d = arc(center, forward, side, left, reach, 1.34 * leftTaper).add(0, -.42 * leftTaper, 0);
            Vec3 upperA = a.add(0, .18 * leftTaper, 0), upperB = b.add(0, .18 * rightTaper, 0);
            quad(out, pose, a, b, c, d, i % 3 == 0 ? ICE : PEARL, alpha * .47F);
            quad(out, pose, a, b, upperB, upperA, ICE, alpha * .25F);
            segment(out, pose, a, b, UP, .064, PEARL, alpha);
            segment(out, pose, a, b, forward, .048, PEARL, alpha);
            segment(out, pose, upperA, upperB, UP, .024, ICE, alpha * .61F);
            if (i % 3 == 0) {
                segment(out, pose, a, c, UP, .012, ICE, alpha * .85F);
                Vec3 tail = a.lerp(center, .19 + t * .14).add(0, -.25 - t * .28, 0);
                segment(out, pose, a, tail, UP, .014, ICE, alpha * .65F);
            }
        }
    }

    private static Vec3 arc(Vec3 center, Vec3 f, Vec3 side, double t, double reach, double width) {
        double angle = (2 * t - 1) * Esper.SLASH_HALF_ANGLE;
        double span = (reach - width) * (.85 + Math.sin(Math.PI * t) * .15);
        return center.add(f.scale(Math.cos(angle) * span)).add(side.scale(Math.sin(angle) * span))
                .add(0, Math.sin((t - .5) * Math.PI) * .28 - width * .1, 0);
    }

    static void portal(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 axis, double clock, double size) {
        Vec3 u = axis.cross(UP).normalize();
        Vec3 v = u.cross(axis).normalize();
        for (int i = 0; i < 32; i++) {
            double start = Math.TAU * i / 32, end = Math.TAU * (i + 1) / 32;
            Vec3 a = ring(center, u, v, start, size), b = ring(center, u, v, end, size);
            Vec3 inner = ring(center, u, v, (start + end) * .5, size * .48);
            if (i % 2 == 0) quad(out, pose, a, b, inner, inner, GOLD, .22F);
            segment(out, pose, a, b, axis, .014, i % 4 == 0 ? PEARL : GOLD, .84F);
            if (i % 4 != 3) segment(out, pose, ring(center, u, v, start + clock, size * 1.27),
                    ring(center, u, v, end + clock, size * 1.27), axis, .009, GOLD, .5F);
        }
    }

    private static Vec3 ring(Vec3 at, Vec3 u, Vec3 v, double angle, double radius) {
        return at.add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
    }

    static void sword(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 direction, double size, int highlight, boolean trail) {
        Vec3 axis = direction.normalize();
        Vec3 u = axis.cross(Math.abs(axis.y) > .97 ? new Vec3(1, 0, 0) : UP).normalize();
        Vec3 v = u.cross(axis).normalize();
        Vec3 blade = center.add(axis.scale(.12 * size));
        Vec3 tip = center.add(axis.scale(.93 * size));
        Vec3[] base = diamond(blade, u, v, size * .137, size * .055);
        Vec3[] bevel = diamond(center.add(axis.scale(.65 * size)), u, v, size * .1, size * .041);
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            int tint = i % 2 == 0 ? highlight : i == 1 ? 0x729ba7 : 0xc5e8ed;
            quad(out, pose, base[i], base[next], bevel[next], bevel[i], tint, .98F);
            quad(out, pose, bevel[i], bevel[next], tip, tip, tint, .98F);
            segment(out, pose, base[i], bevel[i], v, .008 * size, PEARL, .92F);
            segment(out, pose, bevel[i], tip, v, .008 * size, PEARL, .92F);
        }
        Vec3 guard = center.add(axis.scale(.07 * size));
        prism(out, pose, guard, axis, u, v, -.07 * size, .055 * size, .23 * size, .055 * size, GOLD);
        prism(out, pose, guard, axis, u, v, -.35 * size, -.06 * size, .056 * size, .045 * size, 0x3f5860);
        prism(out, pose, guard, axis, u, v, -.41 * size, -.33 * size, .085 * size, .07 * size, highlight);
        if (trail) {
            Vec3 tail = center.subtract(axis.scale(2.3 * size));
            for (Vec3 plane : new Vec3[]{u, v}) {
                var edge = plane.scale(.09 * size);
                quad(out, pose, center.add(edge), center.subtract(edge), tail, tail, ICE, .33F);
                var core = edge.scale(.28);
                quad(out, pose, center.add(core), center.subtract(core), tail, tail, PEARL, .76F);
            }
        }
    }

    private static Vec3[] diamond(Vec3 at, Vec3 u, Vec3 v, double w, double h) {
        return new Vec3[]{at.add(u.scale(w)), at.add(v.scale(h)), at.subtract(u.scale(w)), at.subtract(v.scale(h))};
    }

    private static void prism(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 axis, Vec3 u, Vec3 v,
            double back, double front, double width, double height, int tint) {
        Vec3[] a = diamond(center.add(axis.scale(back)), u, v, width, height);
        Vec3[] b = diamond(center.add(axis.scale(front)), u, v, width, height);
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            quad(out, pose, a[i], a[next], b[next], b[i], tint, 1F);
        }
        quad(out, pose, a[0], a[1], a[2], a[3], tint, 1F);
        quad(out, pose, b[0], b[1], b[2], b[3], tint, 1F);
    }

    static void segment(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b, Vec3 normal, double width, int color, float alpha) {
        Vec3 offset = b.subtract(a).cross(normal).normalize().scale(width);
        quad(out, pose, a.subtract(offset), b.subtract(offset), b.add(offset), a.add(offset), color, alpha);
    }

    private static void quad(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alpha) {
        int argb = (Math.clamp((int) (alpha * 255), 0, 255) << 24) | rgb;
        for (Vec3 vertex : new Vec3[]{a, b, c, d})
            out.addVertex(pose, (float) vertex.x, (float) vertex.y, (float) vertex.z).setColor(argb);
    }
    private EsperSpatialGeometry() {}
}
