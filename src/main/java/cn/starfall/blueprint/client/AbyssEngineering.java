package cn.starfall.blueprint.client;

import static cn.starfall.blueprint.client.BestMechGeometry.*;
import static cn.starfall.blueprint.client.BestMechGeometry.Feature.*;
import static cn.starfall.blueprint.client.BestMechGeometry.Material.*;
import static cn.starfall.blueprint.client.BestMechGeometry.Motion.*;

/** Abyssal salvage titan, built around a sealed helmet and two shoulder air bottles. */
final class AbyssEngineering {
    static Shape create() {
        var hull = new Shape(null, STATIC, 0, 0, 0, 0, 0);
        hull.box(GLASS, -10, -26, -4, 20, 11, 10)
                .box(GRAPHITE, -6, -16, -3.5f, 12, 11, 8)
                .box(TEAL, -8, -7, -4, 16, 6, 9)
                .box(GOLD, -7, -6, -4.5f, 14, 1.2f, 9.8f)
                .box(GLASS, -4, -4, -5, 8, 4, 3)
                .box(GRAPHITE, -5, -24, 5, 10, 19, 4);
        for (int i = 0; i < 3; i++) {
            float width = 12 - i;
            hull.box(i == 0 ? GOLD : GLASS, -width / 2, -16 + i * 3, -4.9f + i * .24f, width, 2.35f, 3);
        }
        helmet(hull);
        for (int side : new int[]{-1, 1}) {
            var chest = hull.child(null, STATIC, side * 6.4f, -22, -4.4f, -.08f, -side * .24f);
            chest.box(GLASS, -5.8f, -3.6f, -1.6f, 11.6f, 8.2f, 5)
                    .box(TEAL, -5.6f, -3.9f, -2.3f, 11.2f, 5.8f, 4)
                    .box(IVORY, -5.1f, -4.2f, -2.6f, 10.2f, 1.9f, 3.5f)
                    .box(GOLD, -4.8f, 1.5f, -2.55f, 9.6f, .85f, 1.5f);
            tank(hull, side);
            arm(hull, side);
            boot(hull, side);
        }
        hull.box(GOLD, -2.1f, -22.4f, -7.3f, 4.2f, 4.2f, 2)
                .box(GLASS, -1.5f, -21.8f, -7.55f, 3, 3, .6f)
                .box(AMBER_LAMP, -.45f, -21.3f, -7.7f, .9f, 1.7f, .4f);
        equipment(hull);
        return hull;
    }

    private static void helmet(Shape hull) {
        hull.box(GOLD, -5, -28, -4.4f, 10, 2.3f, 8.8f)
                .box(BLACK, -3.6f, -29, -3.8f, 7.2f, 2, 7)
                .box(GLASS, -4, -33.3f, -4.2f, 8, 5.4f, 7.6f)
                .box(IVORY, -3.4f, -34.4f, -3.9f, 6.8f, 2.8f, 7)
                .box(TEAL, -2.6f, -35, -3.1f, 5.2f, 1.3f, 5.6f)
                .box(GOLD, -2.6f, -32.45f, -4.6f, 5.2f, 2.9f, 1.3f)
                .box(BLACK, -2.2f, -32, -4.85f, 4.4f, 1.9f, .6f)
                .box(CYAN_LAMP, -1.65f, -31.55f, -5, 3.3f, .7f, .4f)
                .box(TEAL, -2, -29.7f, -4.55f, 4, 1.45f, 1.1f);
        for (int side : new int[]{-1, 1}) hull.box(GOLD, side * 3.9f - .7f, -32.3f, -2.7f, 1.4f, 3.3f, 4.2f);
    }

    private static void tank(Shape hull, int side) {
        var bottle = hull.child(null, STATIC, side * 9.3f, -26, 4.9f, .08f, side * .09f);
        bottle.box(GOLD, -3, -3.7f, -1.95f, 6, 14, 4)
                .box(GOLD, -2.05f, -3.7f, -3, 4.1f, 14, 6)
                .box(IVORY, -2, -4.5f, -2, 4, 1.6f, 4)
                .box(GRAPHITE, -1.1f, -5.8f, -1.15f, 2.2f, 1.45f, 2.3f);
        for (int y : new int[]{-2, 6}) bottle.box(GLASS, -3.2f, y, -2.1f, 6.4f, 1.35f, 4.2f)
                .box(GLASS, -2.15f, y, -3.15f, 4.3f, 1.35f, 6.3f);
    }

    private static void arm(Shape hull, int side) {
        var arm = joint(hull, null, side < 0 ? RIGHT_ARM : LEFT_ARM, side * 16, -23, 0, side < 0 ? -.10f : -.06f);
        arm.box(GRAPHITE, -3.6f, -2.5f, -3.5f, 7.2f, 8, 7)
                .box(GLASS, -2.9f, 3, -3.2f, 5.8f, 7.4f, 6.4f)
                .box(GOLD, side * 2.6f - .8f, 4, -1.7f, 1.6f, 7.3f, 3.4f)
                .box(STEEL, -3.6f, 9, -2.7f, 7.2f, 4.6f, 5.4f);
        var cap = arm.child(null, STATIC, 0, -1, 0, -.08f, side * .17f);
        cap.box(GLASS, -5.7f, -2.3f, -4.5f, 11.4f, 5.7f, 9)
                .box(TEAL, -5.5f, -3.6f, -4.8f, 11, 4, 9.6f)
                .box(IVORY, -4.4f, -4.45f, -4.2f, 8.8f, 2, 8.4f)
                .box(GOLD, -4.8f, .2f, -4.9f, 9.6f, 1, 1.4f);
        if (side < 0) {
            arm.box(GLASS, -4.9f, 11, -2.2f, 9.8f, 9, 7)
                    .box(TEAL, -5.3f, 12, -2.4f, 2.3f, 6.8f, 7.6f)
                    .box(GOLD, -5.6f, 13.3f, -.8f, 1.4f, 4.3f, 4.3f)
                    .box(IVORY, -4.45f, 18.3f, -2.6f, 8.9f, 2.25f, 7.3f);
            toolHeads(arm.child(null, STATIC, 0, 2.6f, -.4f, 0, 0));
        } else {
            var claw = arm.child(null, STATIC, .5f, 12, 0, -.18f, -.04f);
            claw.box(GLASS, -4.15f, -.7f, -4, 8.3f, 9.2f, 8)
                    .box(IVORY, -4.6f, -.8f, -4.4f, 9.2f, 3.1f, 8.8f)
                    .box(TEAL, -4.6f, 2.5f, -4.3f, 9.2f, 4, 8.6f)
                    .box(GOLD, -4.2f, 7.2f, -4.1f, 8.4f, 1.5f, 8.2f);
            for (int i = -1; i <= 1; i++) claw.box(GRAPHITE, i * 2.5f - 1, 8.4f, -3.4f, 2, 4.2f, 3.3f)
                    .box(STEEL, i * 2.5f - 1, 11.7f, -2.1f, 2, 1.7f, 3.1f);
            claw.box(GOLD, -4.8f, 7.5f, 1.3f, 2.7f, 4.5f, 2.9f);
        }
    }

    private static void boot(Shape hull, int side) {
        var leg = joint(hull, WALK, leg(side), side * 5.8f, -3, .6f, 0);
        leg.box(GRAPHITE, -2.9f, 0, -2.9f, 5.8f, 13, 6.2f)
                .box(GLASS, -3.5f, 1.1f, -3.65f, 7, 8.9f, 7.8f)
                .box(STEEL, -3.7f, 10.2f, -2.7f, 7.4f, 3.8f, 5.7f)
                .box(GOLD, -3.25f, 10, -4.5f, 6.5f, 4, 3)
                .box(GLASS, -3.5f, 14, -2.8f, 7, 9.8f, 7.1f)
                .box(TEAL, -3.7f, 15.5f, -4, 7.4f, 6.9f, 4)
                .box(IVORY, -3.35f, 15.1f, -4.3f, 6.7f, 1.5f, 2.4f)
                .box(GOLD, side * 3.45f - .8f, 12, .2f, 1.6f, 5, 2.4f)
                .box(SILVER, side * 3.45f - .5f, 17, .65f, 1, 5.7f, 1.45f)
                .box(GLASS, -4.1f, 21.6f, -5.1f, 8.2f, 4, 10.3f)
                .box(TEAL, -4.25f, 23.3f, -7, 8.5f, 2.6f, 9)
                .box(IVORY, -3.2f, 23.65f, -7.25f, 6.4f, 1.4f, 2.5f)
                .box(RUBBER, -4.45f, 25.5f, -7.2f, 8.9f, 1.5f, 12.6f);
        tread(leg, 4.45f, 26.2f, -6.8f);
        var thigh = leg.child(null, STATIC, 0, 4.8f, -3, .08f, side * .13f);
        thigh.box(TEAL, -3.7f, -4, -1.2f, 7.4f, 7.6f, 3.4f)
                .box(IVORY, -3.5f, -4.2f, -1.4f, 7, 2.2f, 3.2f);
        leg.child(MOBILITY).box(GRAPHITE, side * 4 - 1.1f, 7.8f, 2, 2.2f, 10.2f, 3.5f)
                .box(SILVER, side * 4 - .6f, 17, 2.5f, 1.2f, 5.6f, 2);
    }

    private static void equipment(Shape hull) {
        var extension = hull.child(EXTENSION);
        extension.box(GLASS, -8, -17, 7, 16, 10, 3.9f).box(GOLD, -8.5f, -17.5f, 8, 17, 1.6f, 3.5f);
        var endurance = hull.child(ENDURANCE);
        for (int side : new int[]{-1, 1}) {
            extension.box(TEAL, side * 7.5f - 1, -16, 9, 2, 9, 2.6f);
            endurance.box(GLASS, side * 8 - 2, -26, 7, 4, 12, 3.6f)
                    .box(IVORY, side * 8 - 2.1f, -25.7f, 9, 4.2f, 2, 2)
                    .box(MINT_LAMP, side * 8 - .65f, -22, 10.4f, 1.3f, 5, .6f);
        }
        hull.child(REINFORCED).box(GLASS, -6.8f, -13, -5.2f, 13.6f, 6, 2.5f)
                .box(IVORY, -6.3f, -13.2f, -5.7f, 12.6f, 2.1f, 2)
                .box(GOLD, -5.4f, -8.7f, -5.5f, 10.8f, 1.1f, 1.2f);
        runningGear(hull, 5.8f, -3, .6f, GLASS, GOLD);
        rearGear(hull, 0, -16, 10, TEAL);
        wirelessReceiver(hull, 0, -24, 6.8f, GOLD);
    }

    private AbyssEngineering() {}
}
