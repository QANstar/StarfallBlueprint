package cn.starfall.blueprint.client;

import static cn.starfall.blueprint.client.BestMechGeometry.*;
import static cn.starfall.blueprint.client.BestMechGeometry.Feature.*;
import static cn.starfall.blueprint.client.BestMechGeometry.Material.*;
import static cn.starfall.blueprint.client.BestMechGeometry.Motion.*;

/** The flagship carries a folding heat mantle over an articulated pressure hull. */
final class AuroraUniversal {
    static Shape create() {
        Shape hull = new Shape(null, STATIC, 0, 0, 0, 0, 0);
        hull.box(GRAPHITE, -9, -38, -4, 18, 13, 13)
                .box(BLACK, -6, -26, -4, 12, 12, 11)
                .box(STEEL, -9, -15, -4, 18, 6, 10)
                .box(RUST, -5, -14, -6, 10, 4, 3);
        for (int row = 0; row < 3; row++) {
            float span = 8 - row;
            hull.box(STEEL, -span, -26 + row * 3, -5, span * 2, 2, 8)
                    .box(GRAPHITE, -span + 1, -26.3f + row * 3, -6, span * 2 - 2, 1.5f, 2);
        }
        hull.box(BLACK, -4, -42, -3, 8, 6, 7)
                .box(RUST, -4, -48, -4, 8, 7, 8)
                .box(IVORY, -3.5f, -49, -5, 7, 3, 8)
                .box(GRAPHITE, -3, -46.3f, -5.8f, 6, 4.8f, 3)
                .box(RED, -1.7f, -44.1f, -6, 3.4f, 3.1f, 2);
        for (int s : new int[]{-1, 1}) {
            Shape cheek = hull.child(null, STATIC, s * 2.2f, -46, -6, 0, -s * .16f);
            cheek.box(MINT_LAMP, -1.75f, 0, -.4f, 3.5f, .95f, 1);
            Shape crown = hull.child(null, STATIC, s * 3.7f, -45, 0, -.17f, s * .37f);
            crown.box(RED, -1, -6.5f, -1, 2, 8, 3).box(SILVER, -.65f, -8, 0, 1.3f, 3, 1.5f);
            Shape chest = hull.child(null, STATIC, s * 7.3f, -35.5f, -5.4f, -.11f, -s * .37f);
            chest.box(STEEL, -7.2f, -3.8f, -1.9f, 14.4f, 8.1f, 5)
                    .box(IVORY, -7, -3.5f, -3, 14, 5.8f, 4)
                    .box(RED, -5.5f, -2.7f, -3.25f, 11, 1.2f, 1)
                    .box(MINT_LAMP, -5.3f, 2.35f, -2.5f, 10.6f, .9f, 1);
            Shape intake = hull.child(null, STATIC, s * 8.5f, -28, -4, .06f, -s * .27f);
            for (int j = 0; j < 3; j++) intake.box(j == 0 ? IVORY : STEEL, -3.1f, j * 2, -.7f + j * .3f, 6.2f, 1.25f, 4);
            mantle(hull, s);
            limb(hull, s);
        }
        Shape keel = hull.child(null, STATIC, 0, -30.4f, -7.5f, -.08f, .7854f);
        keel.box(BLACK, -3, -3, -1, 6, 6, 3).box(TEAL, -2.6f, -2.6f, -1.4f, 5.2f, 5.2f, 2)
                .box(MINT_LAMP, -1.65f, -1.65f, -1.7f, 3.3f, 3.3f, 1);
        Shape battery = hull.child(CHANGCHUAN);
        battery.box(BLACK, -3.7f, -38, 8, 7.4f, 20, 6).box(IVORY, -4, -39, 9, 8, 3, 5.5f);
        for (int i=0; i<5; i++) battery.box(MINT_LAMP, -2.3f, -34+i*3, 13.8f, 4.6f, 1.4f, .7f);
        hull.child(REINFORCED).box(RED, -5, -23, -7.2f, 10, 3.2f, 2.5f);
        runningGear(hull, 8, -10, 1, GRAPHITE, IVORY);
        rearGear(hull, 0, -16, 7, IVORY);
        wirelessReceiver(hull, 5, -19, 8, IVORY);
        return hull;
    }

    private static void mantle(Shape hull, int side) {
        Shape spar = hull.child(null, STATIC, side * 7.8f, -33, 5.5f, -.42f, side * .69f);
        spar.box(GRAPHITE, -3, -19, -2, 6, 22, 5)
                .box(RED, -2.6f, -21, -2.2f, 5.2f, 17, 3.6f)
                .box(IVORY, -1.6f, -22.5f, -2.4f, 3.2f, 14, 2.5f)
                .box(SILVER, -.65f, -24, -1, 1.3f, 4, 1.8f);
        for (int i=0; i<5; i++) spar.box(STEEL, -3.2f, -16+i*3.1f, 2.5f, 6.4f, 1.25f, 2.4f);
    }

    private static void limb(Shape hull, int s) {
        Shape arm = hull.child(null, s < 0 ? RIGHT_ARM : LEFT_ARM, s * 15, -34, 0, -.10f, -s * .11f);
        arm.box(GRAPHITE, -4.2f, -3, -4.5f, 8.4f, 11, 9).box(STEEL, -2.8f, 5, -2.8f, 5.6f, 13, 6)
                .box(RED, -3.3f, 7, -3.5f, 6.6f, 5.5f, 4).box(BLACK, -3.6f, 14, -4, 7.2f, 5.5f, 8);
        Shape cap = arm.child(null, STATIC, 0, -2, 0, -.13f, s * .29f);
        cap.box(RUST, -6, -2, -6, 12, 7, 12).box(IVORY, -6.5f, -3.5f, -6.5f, 13, 4.4f, 12.8f)
                .box(SILVER, -5, -4.3f, -5, 10, 2.3f, 10).box(RED, -3, -3.7f, -6.8f, 6, 1.9f, 1);
        Shape gauntlet = arm.child(null, STATIC, 0, 16, -1, -.17f, 0);
        gauntlet.box(GRAPHITE, -4.3f, 0, -4, 8.6f, 11, 9).box(IVORY, -4.7f, 1, -5.3f, 9.4f, 6.5f, 3)
                .box(RED, -3.5f, 7.3f, -4.7f, 7, 4, 2).box(STEEL, -3.4f, 11, -3.5f, 6.8f, 2.8f, 6);
        for (int i=0; i<3; i++) gauntlet.box(BLACK, -3.4f+i*2.4f, 12.4f, -3.9f, 2.05f, 3, 5);
        Shape hardpoint = gauntlet.child(null, STATIC, 0, -9, -1, 0, 0);
        if (s < 0) {
            toolHeads(hardpoint);
            gauntlet.child(ZHENYUAN).box(RUST, -5.6f, -1, -5.4f, 11.2f, 4.2f, 10.8f);
        } else {
            weapons(hardpoint, IVORY, MINT_LAMP);
            arm.child(TIANPU).box(RED, -3, -7, -2, 6, 4, 6).box(MINT_LAMP, -1.6f, -6.3f, -2.4f, 3.2f, 1.4f, 1);
            gauntlet.child(TIANPU).box(TEAL, 3.7f, 1, -4, 2, 7, 5).box(MINT_LAMP, 4.1f, 2, -4.45f, 1.3f, 4, 1);
        }
        Shape leg = hull.child(WALK, leg(s), s * 7.5f, -11, 1, 0, 0);
        leg.box(BLACK, -3.7f, 0, -3.7f, 7.4f, 16, 8).box(STEEL, -3.8f, 13, -3.6f, 7.6f, 6, 8)
                .box(GRAPHITE, -3.3f, 17, -3, 6.6f, 14.4f, 7.4f);
        Shape thigh = leg.child(null, STATIC, 0, 3, 0, .12f, -s*.10f);
        thigh.box(IVORY, -4.5f, -1, -4.5f, 9, 10, 8.4f).box(RED, -3.6f, 6, -4.8f, 7.2f, 4.2f, 4);
        Shape knee = leg.child(null, STATIC, 0, 14, -3, -.2f, 0);
        knee.box(RUST, -4.2f, -2, -3.4f, 8.4f, 7.3f, 5.9f).box(IVORY, -3, -3, -4, 6, 5.5f, 4);
        Shape shin = leg.child(null, STATIC, 0, 20, 0, -.09f, 0);
        shin.box(IVORY, -3.9f, -1, -4.1f, 7.8f, 10.5f, 7.7f).box(STEEL, -1.7f, 4, -4.6f, 3.4f, 7, 1.3f)
                .box(RED, -3.8f, -1.2f, -4.4f, 7.6f, 2.2f, 2);
        leg.box(BLACK, -4.7f, 31.5f, -9, 9.4f, 3.5f, 14.5f).box(IVORY, -4.3f, 29, -7.8f, 8.6f, 3.3f, 11.7f)
                .box(STEEL, -3.1f, 30.6f, -9, 6.2f, 2, 3);
    }
}
