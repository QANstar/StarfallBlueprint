package cn.starfall.blueprint.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;

import static cn.starfall.blueprint.client.BestMechGeometry.Feature.*;
import static cn.starfall.blueprint.client.BestMechGeometry.Material.*;
import static cn.starfall.blueprint.client.BestMechGeometry.Motion.*;

/** Authored in Minecraft model pixels, with forward at -Z and the floor at Y=24. */
final class BestMechGeometry {
    enum Material {
        BLACK(0x192028), GRAPHITE(0x313c43), STEEL(0x687b81), SILVER(0x9caeb3),
        RUBBER(0x252a2d), YELLOW(0xe8b32c), GOLD(0xa96d19), IVORY(0xe6dfc9),
        RED(0x88343c), RUST(0x592d38), NAVY(0x253b58), BLUE(0x44627e),
        GLASS(0x204650), TEAL(0x2c8286), GREEN(0x5a9b78),
        AMBER_LAMP(0xffbd59, true), CYAN_LAMP(0x72f6ff, true),
        MINT_LAMP(0xa1ffc4, true), RED_LAMP(0xff755d, true);

        final int color;
        final boolean lamp;

        Material(int rgb) { this(rgb, false); }
        Material(int rgb, boolean lamp) { this.color = 0xff000000 | rgb; this.lamp = lamp; }
    }

    enum Feature {
        EXTENSION, MOBILITY, ENDURANCE, FIRE_CONTROL, ARMOR, SUSTAINED_FIRE, ZHENYUAN, TIANPU, CHANGCHUAN,
        WALK, CRUISE, REINFORCED, GENERATOR, FUSION, WIRELESS, RADIATOR, DRIVE, REPAIR, FRIENDLY_REPAIR,
        MAINTENANCE_DRONE, HEALING_DRONE, ATTACK_DRONE, DEFENSE_DRONE,
        TOOL_EMPTY, DRILL, EXCAVATOR, HEAVY_DRILL, HEAVY_EXCAVATOR, ARMORED_DRILL,
        LASER, MELEE, MACHINE_GUN, SNIPER, EXPLOSIVE
    }

    enum Motion { STATIC, LEFT_LEG, RIGHT_LEG, LEFT_ARM, RIGHT_ARM, ROLLER }

    static final class Shape {
        final PartPose pose;
        final Feature feature;
        final Motion motion;
        final float restPitch;
        final EnumMap<Material, CubeListBuilder> materials = new EnumMap<>(Material.class);
        final List<Shape> children = new ArrayList<>();

        Shape(Feature feature, Motion motion, float x, float y, float z, float pitch, float roll) {
            this.feature = feature;
            this.motion = motion;
            this.restPitch = pitch;
            this.pose = PartPose.offsetAndRotation(x, y, z, pitch, 0, roll);
        }

        Shape child(Feature feature, Motion motion, float x, float y, float z, float pitch, float roll) {
            var shape = new Shape(feature, motion, x, y, z, pitch, roll);
            children.add(shape);
            return shape;
        }

        Shape child(Feature feature) { return child(feature, STATIC, 0, 0, 0, 0, 0); }

        Shape box(Material material, float x, float y, float z, float width, float height, float depth) {
            materials.computeIfAbsent(material, ignored -> CubeListBuilder.create()).texOffs(0, 0)
                    .addBox(x, y, z, width, height, depth);
            return this;
        }

        void bake(PartDefinition root, String name) {
            var part = root.addOrReplaceChild(name, CubeListBuilder.create(), pose);
            materials.forEach((material, cubes) -> part.addOrReplaceChild(material.name(), cubes, PartPose.ZERO));
            for (int i = 0; i < children.size(); i++) children.get(i).bake(part, "shape_" + i);
        }
    }

    static Shape create(BestMechModel.Platform platform) {
        return switch (platform) {
            case ENGINEERING -> engineering();
            case COMBAT -> combat();
            case UNIVERSAL -> universal();
        };
    }

    private static Shape engineering() {
        var frame = new Shape(null, STATIC, 0, 0, 0, 0, 0);
        frame.box(GRAPHITE, -10, -9, -5, 20, 12, 12)
                .box(BLACK, -9, 1, -4, 18, 4, 10)
                .box(YELLOW, -11, -10, -8, 22, 5, 4)
                .box(GOLD, -9, -5, -7.5f, 18, 3, 3)
                .box(BLACK, -6, -8, -8.4f, 12, 2, .6f)
                .box(IVORY, -2, -6.5f, -8.55f, 4, 2, .5f)
                .box(GOLD, -8, -20, 2, 16, 13, 7)
                .box(GRAPHITE, -6, -21, 5, 12, 4, 5)
                .box(YELLOW, -8, -12, -6, 16, 4, 10)
                .box(GLASS, -5, -15, -5.7f, 10, 3.1f, 1.8f)
                .box(BLACK, -5.5f, -12, -6.2f, 11, 1, 1.5f)
                .box(BLACK, -5, -10, 8.7f, 10, 6, 1.5f);
        for (int y = -9; y < -4; y += 2) frame.box(STEEL, -4, y, 10, 8, .65f, .65f);
        for (int side : new int[]{-1, 1}) {
            float edge = side * 7;
            frame.box(YELLOW, edge - 1.1f, -23, -5, 2.2f, 2, 11)
                    .box(YELLOW, edge - 1, -22, -5, 2, 13.2f, 2)
                    .box(GRAPHITE, edge - .75f, -21, 3, 1.5f, 11, 2)
                    .box(AMBER_LAMP, edge - 1.4f, -9.4f, -8.7f, 2.8f, 1.2f, .65f)
                    .box(STEEL, edge - 1, -18, 9, 2, 4, 1);
            var leg = joint(frame, WALK, leg(side), side * 7, 0, 1, 0);
            leg.box(GRAPHITE, -3.5f, 0, -3.5f, 7, 15, 7)
                    .box(STEEL, -3.9f, 2, -2.5f, 1.9f, 15, 2.6f)
                    .box(SILVER, 2.3f, 7, -2.6f, 1.6f, 11, 1.4f)
                    .box(GOLD, -3.8f, 8, -4.6f, 7.6f, 5.2f, 2)
                    .box(BLACK, -3.5f, 13, -2.7f, 7, 7, 6)
                    .box(RUBBER, -5, 20.8f, -8, 10, 3.2f, 14)
                    .box(YELLOW, -4.6f, 17.4f, -7.2f, 9.2f, 3.5f, 11.8f)
                    .box(GRAPHITE, -4, 17.8f, -8.15f, 8, 2.3f, 1.2f);
            tread(leg, 5, 23.1f, -6);
            var drive = frame.child(MOBILITY);
            drive.box(GRAPHITE, side * 10.5f - 2, -1, -.7f, 4, 10, 6)
                    .box(GOLD, side * 12.5f - 1, 2, -.25f, 2, 6, 5)
                    .box(TEAL, side * 12.7f - 1, 3.4f, -1.15f, 2, 3.4f, 1.2f);
            joint(drive, WALK, leg(side), side * 7, 0, 1, 0)
                    .box(GRAPHITE, side * 4 - 1.5f, 7, .8f, 3, 12, 3.6f)
                    .box(SILVER, side * 4 - .8f, 13, -.1f, 1.6f, 6.2f, 1.6f);
        }
        var dig = joint(frame, null, RIGHT_ARM, -11, -8, -1, -.10f);
        dig.box(GRAPHITE, -3.7f, -2, -4, 7.4f, 6, 8)
                .box(YELLOW, -4, -3, -4.4f, 8, 3, 9)
                .box(GOLD, -3.2f, 3, -2.9f, 6.4f, 9, 5.8f)
                .box(GRAPHITE, -3.5f, 8, -5.4f, 7, 6, 8)
                .box(SILVER, -4.1f, 2, -3.1f, 1.3f, 8, 1.4f);
        toolHeads(dig);
        var brace = joint(frame, null, LEFT_ARM, 11.2f, -8, 1, .15f);
        brace.box(GRAPHITE, -3, -2, -3, 6.5f, 6, 6)
                .box(YELLOW, -3.5f, -2.7f, -3.4f, 7, 3, 6.8f)
                .box(STEEL, -2, 3, -2, 4, 10, 4)
                .box(SILVER, 2.1f, 5, -.9f, 1.1f, 13, 1.8f)
                .box(GRAPHITE, -3.2f, 12, -3, 6.4f, 5, 6)
                .box(GOLD, -3.8f, 16, -3.6f, 7.6f, 2.5f, 7.2f)
                .box(BLACK, -3.4f, 18, -3.8f, 2.3f, 3, 3.5f)
                .box(BLACK, 1.1f, 18, -3.8f, 2.3f, 3, 3.5f);
        frame.child(EXTENSION).box(GRAPHITE, -11.4f, -12, 7, 22.8f, 13, 6.5f)
                .box(GOLD, -11, -14, 8.5f, 22, 3, 5.7f)
                .box(YELLOW, -11.8f, -11, 10, 2, 10, 4.5f)
                .box(YELLOW, 9.8f, -11, 10, 2, 10, 4.5f)
                .box(BLACK, -7.5f, -9.5f, 13.5f, 15, 8, .8f)
                .box(IVORY, -4.5f, -6.5f, 14.2f, 9, 1.4f, .7f);
        var endurance = frame.child(ENDURANCE);
        for (int side : new int[]{-1, 1}) {
            float x = side * 9 - 2;
            endurance.box(GRAPHITE, x, -21, 5, 4, 11, 7)
                    .box(TEAL, x + .4f, -19, 11.5f, 3.2f, 8, 1.8f)
                    .box(YELLOW, x - .25f, -22, 5.6f, 4.5f, 2, 5.4f)
                    .box(MINT_LAMP, x + 1.35f, -18, 13.35f, 1.3f, 5.5f, .45f);
        }
        frame.child(REINFORCED).box(GRAPHITE, -10.5f, -5.2f, -8, 21, 5, 3)
                .box(GOLD, -10, -4.5f, -9.1f, 20, 3.3f, 1.2f)
                .box(SILVER, -10, -5.15f, -9.35f, 20, .65f, .5f);
        runningGear(frame, 7, 0, 1, GRAPHITE, GOLD);
        rearGear(frame, 0, -12, 12, YELLOW);
        wirelessReceiver(frame, 3.5f, -21.7f, 7, GOLD);
        return frame;
    }

    private static Shape combat() {
        var frame = new Shape(null, STATIC, 0, 0, 0, 0, 0);
        frame.box(GRAPHITE, -6.5f, -9, -3, 13, 9, 10)
                .box(BLACK, -6, -.5f, -4, 12, 4, 9);
        var torso = joint(frame, null, STATIC, 0, -9, 2, .17f);
        torso.box(GRAPHITE, -7, -17, -5, 14, 17, 11)
                .box(RUST, -7.8f, -16, -7.1f, 15.6f, 8, 4.5f)
                .box(BLACK, -5, -11, -7.6f, 10, 4, 2)
                .box(RED, -6, -23, -7, 12, 7, 9)
                .box(GRAPHITE, -4.8f, -24, -6.2f, 9.6f, 3, 7.5f)
                .box(BLACK, -5.5f, -20.7f, -7.7f, 11, 3, 1.6f)
                .box(AMBER_LAMP, -4.4f, -20.08f, -7.83f, 8.8f, 1.1f, .45f)
                .box(BLACK, -6.5f, -9, 5.5f, 13, 8, 3)
                .box(RED, -5.2f, -8.3f, 8.15f, 10.4f, 6, .7f);
        for (int side : new int[]{-1, 1}) {
            float x = side * 5 - .75f;
            torso.box(GRAPHITE, x - .6f, -18, -7.6f, 2.7f, 6.8f, 2);
            for (int y = -16; y < -11; y += 2) torso.box(AMBER_LAMP, x, y, -7.94f, 1.5f, .55f, .5f);
            for (int y = -8; y < -2; y += 2) torso.box(BLACK, side * 2.7f - 1.9f, y, 8.8f, 3.8f, .65f, .75f);
            var leg = joint(frame, WALK, leg(side), side * 4.2f, 0, 0, 0);
            leg.box(BLACK, -2.5f, -.5f, -2.9f, 5, 11, 5.5f)
                    .box(GRAPHITE, -2.8f, 3, -3.4f, 5.6f, 6, 5)
                    .box(RED, -3, 8.7f, -4.2f, 6, 4.3f, 3.8f)
                    .box(GRAPHITE, -2.5f, 12, -1.5f, 5, 8.5f, 6)
                    .box(STEEL, -2.8f, 14, 3.7f, 1.5f, 6.7f, 1.5f)
                    .box(BLACK, -2.9f, 20.6f, -6.7f, 5.8f, 3.4f, 12)
                    .box(RUST, -2.7f, 18.5f, -6.25f, 5.4f, 3.2f, 9.5f);
            leg.child(ARMOR).box(RUST, -3.1f, 10, -4.65f, 6.2f, 8.6f, 3.5f)
                    .box(RED, -2.5f, 11, -5, 5, 4.8f, .65f);
        }
        var left = joint(frame, null, LEFT_ARM, 8.8f, -24, 0, -.05f);
        var right = joint(frame, null, RIGHT_ARM, -8.8f, -24, 0, -.16f);
        for (var arm : List.of(left, right)) {
            arm.box(BLACK, -3.6f, -5, -4.2f, 7.2f, 9, 8.4f)
                    .box(RUST, -3.8f, -6.8f, -4.6f, 7.6f, 4.2f, 9.3f)
                    .box(RED, -3.15f, -7.2f, -3.9f, 6.3f, 1.6f, 8)
                    .box(GRAPHITE, -2.3f, 2, -2.25f, 4.6f, 10, 4.5f)
                    .box(STEEL, -1.6f, 5, -2.6f, 3.2f, 5, 1.5f)
                    .box(GRAPHITE, -2.7f, 10, -3.6f, 5.4f, 6, 6.5f);
            arm.child(ARMOR).box(STEEL, -4.7f, -6.7f, -3.7f, 9.4f, 7.8f, 7.4f)
                    .box(RED, -4.8f, -7.7f, -4.4f, 9.6f, 3.4f, 8.5f)
                    .box(GRAPHITE, -4.65f, -4.6f, -4.1f, 9.3f, 2, 1);
        }
        left.box(BLACK, -2.1f, 15, -3.4f, 4.2f, 4, 4.1f);
        weapons(right, RED, AMBER_LAMP);
        var optics = frame.child(FIRE_CONTROL);
        optics.box(GRAPHITE, -9.6f, -34, 1.5f, 5.4f, 5, 5.6f)
                .box(IVORY, -9.8f, -35, -1.1f, 5.7f, 2, 5)
                .box(AMBER_LAMP, -9, -33.7f, -1.3f, 4.1f, 1.2f, .55f)
                .box(STEEL, 4.6f, -35, 2.9f, 1, 8.7f, 1.1f)
                .box(AMBER_LAMP, 4.55f, -35.5f, 2.85f, 1.1f, .9f, 1.2f);
        var cooling = frame.child(SUSTAINED_FIRE);
        for (int side : new int[]{-1, 1}) {
            float x = side * 5.5f - 2.3f;
            cooling.box(GRAPHITE, x, -28, 7, 4.6f, 16, 6)
                    .box(RUST, x - .2f, -29, 7.7f, 5, 2.2f, 6.2f)
                    .box(AMBER_LAMP, x + 1.7f, -26.3f, 13, 1.2f, 12, .3f);
            for (int y = -26; y < -13; y += 3) cooling.box(STEEL, x - .35f, y, 12.9f, 5.3f, .9f, 1.15f);
        }
        runningGear(frame, 5, 0, 0, GRAPHITE, RED);
        rearGear(frame, 0, -10, 11, RED);
        wirelessReceiver(frame, .5f, -26, 9.5f, RUST);
        return frame;
    }

    private static Shape universal() {
        var frame = new Shape(null, STATIC, 0, 0, 0, 0, 0);
        frame.box(NAVY, -12.5f, -18, -7, 25, 17, 15)
                .box(GRAPHITE, -10.5f, -4.2f, -5.5f, 21, 5.2f, 11)
                .box(IVORY, -11.7f, -22, -8.4f, 23.4f, 10, 3.4f)
                .box(NAVY, -8, -28, -9.2f, 16, 9, 5)
                .box(IVORY, -7, -29.2f, -10, 14, 3, 5)
                .box(BLACK, -6, -26.4f, -10.4f, 12, 3.7f, 1.3f)
                .box(GLASS, -5.3f, -26, -10.7f, 10.6f, 2.55f, .75f)
                .box(TEAL, -4.7f, -25.8f, -10.9f, 9.4f, .6f, .3f)
                .box(NAVY, -8.2f, -30, 1.7f, 16.4f, 13, 8.8f)
                .box(BLACK, -6.5f, -20.2f, -9, 13, 12.2f, 3.7f)
                .box(BLUE, -5.2f, -19, -9.7f, 10.4f, 9.9f, 1.55f)
                .box(TEAL, -4.2f, -17.9f, -10.14f, 8.4f, 7.8f, 1.15f)
                .box(CYAN_LAMP, -2.9f, -16.9f, -10.55f, 5.8f, 5.8f, .9f)
                .box(CYAN_LAMP, -3.7f, -15.8f, -10.42f, 7.4f, 3.6f, .7f);
        for (int side : new int[]{-1, 1}) {
            float x = side * 10;
            frame.box(IVORY, x - 1.15f, -32, -7.5f, 2.3f, 4, 12.4f)
                    .box(BLUE, x - 1.1f, -29, -7.4f, 2.2f, 8, 2.3f)
                    .box(BLUE, x - 1.1f, -29, 2, 2.2f, 8, 2.3f)
                    .box(CYAN_LAMP, x - 1.25f, -18.7f, -8.8f, 2.5f, 1, .65f);
            var radiator = frame.child(null, STATIC, x, -24, 8, .04f, side * .10f);
            radiator.box(NAVY, -3.8f, -22, -2.3f, 7.6f, 27, 8.4f)
                    .box(IVORY, -4, -23, -2.5f, 8, 4, 8.7f)
                    .box(BLACK, -2.9f, -17.7f, 5.7f, 5.8f, 19, 1.8f)
                    .box(BLUE, -4.1f, -18, -.6f, 8.2f, 11, 2.1f)
                    .box(CYAN_LAMP, -2.55f, -21.85f, -2.8f, 5.1f, 1.1f, .55f)
                    .box(TEAL, -1.6f, -16.5f, 7.04f, 3.2f, 16, .53f);
            for (int y = -16; y <= -1; y += 3) radiator.box(STEEL, -3.05f, y, 7.25f, 6.1f, .9f, 1);
            var leg = joint(frame, WALK, leg(side), side * 8, -2, 0, 0);
            leg.box(NAVY, -4.5f, 0, -4, 9, 14, 8)
                    .box(SILVER, -4.8f, 3, 3.6f, 2.2f, 14, 2.5f)
                    .box(IVORY, -4.9f, 8, -5, 9.8f, 5.5f, 3)
                    .box(GRAPHITE, -4.2f, 13, -3.5f, 8.4f, 10, 8.5f)
                    .box(BLUE, -4.45f, 15, -4.35f, 8.9f, 5, 2.6f)
                    .box(RUBBER, -5.8f, 22.4f, -9.1f, 11.6f, 3.6f, 16)
                    .box(IVORY, -5.45f, 19.7f, -8.3f, 10.9f, 3.7f, 13.5f)
                    .box(NAVY, -4.5f, 21.5f, -9.45f, 9, 1.9f, 1.35f);
            tread(leg, 5.8f, 25.2f, -6.5f);
            leg.child(ZHENYUAN).box(NAVY, -5.3f, 10.1f, -5.3f, 10.6f, 10.1f, 3.2f)
                    .box(BLUE, -4.6f, 10.3f, -5.9f, 9.2f, 7.3f, 1.2f)
                    .box(GOLD, -4.8f, 16.7f, -6, 9.6f, 1.2f, .65f);
        }
        var left = joint(frame, null, LEFT_ARM, 14.2f, -20, -1, -.13f);
        var right = joint(frame, null, RIGHT_ARM, -14.2f, -20, 1, -.08f);
        for (var arm : List.of(left, right)) {
            arm.box(NAVY, -4.3f, -4.6f, -4.8f, 8.6f, 9.2f, 9.6f)
                    .box(IVORY, -4.55f, -6, -5, 9.1f, 4, 10)
                    .box(BLUE, -3.6f, 2.3f, -3.2f, 7.2f, 10.2f, 6.4f)
                    .box(GRAPHITE, -3.5f, 9, -5.4f, 7, 7, 9)
                    .box(CYAN_LAMP, -2.8f, -4.2f, -5.3f, 5.6f, 1.15f, .6f);
        }
        toolHeads(left);
        weapons(right, IVORY, CYAN_LAMP);
        frame.child(ZHENYUAN).box(NAVY, -12.9f, -17, -9.55f, 8.1f, 12, 4.7f)
                .box(NAVY, 4.8f, -17, -9.55f, 8.1f, 12, 4.7f)
                .box(IVORY, -12.8f, -14.5f, -10.35f, 6.8f, 6.4f, 1.5f)
                .box(IVORY, 6, -14.5f, -10.35f, 6.8f, 6.4f, 1.5f)
                .box(GOLD, -10.6f, -8.2f, -10.8f, 21.2f, 1.6f, 1.4f);
        left.child(ZHENYUAN).box(NAVY, -4.8f, 3, -3.9f, 9.6f, 9, 7.5f)
                .box(IVORY, -5.1f, 4, -4.5f, 10.2f, 4, 2.5f)
                .box(GOLD, -5.05f, 8.1f, -4.7f, 10.1f, 1.05f, 1.2f);
        var fire = right.child(TIANPU);
        fire.box(NAVY, -5.1f, -10.8f, -6.2f, 9.4f, 7.8f, 13.4f)
                .box(IVORY, -5.7f, -12, -7.5f, 10.5f, 3, 15.4f)
                .box(BLACK, -4.4f, -10.1f, -7.7f, 7.8f, 4.4f, 2.2f)
                .box(NAVY, -4.9f, 5.2f, -5.9f, 2.5f, 12.1f, 9.1f)
                .box(CYAN_LAMP, -5.2f, 6, -5.35f, .65f, 10, 1.3f)
                .box(AMBER_LAMP, -4.4f, 1, -5.3f, .85f, 3.8f, 1.4f);
        for (int x = -4; x <= 2; x += 3) {
            fire.box(TEAL, x, -9.5f, -8.2f, 2.35f, 2.65f, 1.3f)
                    .box(CYAN_LAMP, x + .4f, -9, -8.35f, 1.55f, 1.65f, .52f);
        }
        frame.child(TIANPU).box(NAVY, 12.1f, -32.3f, 3, 3.5f, 9.5f, 4.7f)
                .box(IVORY, 12.9f, -35.1f, 3.5f, 1.4f, 5, 1.7f)
                .box(CYAN_LAMP, 12.6f, -35, 3.3f, 2, 1, 2.05f);
        var battery = frame.child(CHANGCHUAN);
        battery.box(NAVY, -5, -47, 9, 10, 31.5f, 8)
                .box(IVORY, -5.5f, -49, 9.1f, 11, 4, 8.4f)
                .box(BLACK, -4, -44.5f, 16.5f, 8, 23.5f, 1.75f)
                .box(TEAL, -2.6f, -43, 17.85f, 5.2f, 19.5f, .65f)
                .box(MINT_LAMP, -3.5f, -48.25f, 8.6f, 7, 1.25f, .75f)
                .box(TEAL, -13.5f, -14, -8.1f, 27, 1.4f, 1.4f);
        for (int y = -42; y <= -25; y += 4) battery.box(MINT_LAMP, -2, y, 18.25f, 4, 2.15f, .6f);
        for (int side : new int[]{-1, 1}) {
            battery.box(BLUE, side * 4.5f - .8f, -43, 13.5f, 1.6f, 20.5f, 4.4f)
                    .box(MINT_LAMP, side * 12 - 1, -14.4f, -8.65f, 2, 2, .65f)
                    .box(MINT_LAMP, side * 13.55f - .35f, -14, -6.1f, .7f, 1.4f, 7.8f);
        }
        runningGear(frame, 8, -2, 0, NAVY, IVORY);
        rearGear(frame, 0, -12, 16, IVORY);
        wirelessReceiver(frame, 0, -3.6f, -7.15f, IVORY);
        return frame;
    }

    private static Shape joint(Shape parent, Feature feature, Motion motion, float x, float y, float z, float pitch) {
        return parent.child(feature, motion, x, y, z, pitch, 0);
    }

    private static Motion leg(int side) { return side == 1 ? LEFT_LEG : RIGHT_LEG; }

    private static void tread(Shape foot, float halfWidth, float y, float front) {
        for (int i = 0; i < 4; i++) {
            foot.box(BLACK, -halfWidth - .12f, y, front + i * 3, halfWidth * 2 + .24f, .8f, 1);
        }
    }

    private static void runningGear(Shape frame, float track, float hip, float offset, Material frameColor, Material cap) {
        var assembly = frame.child(CRUISE);
        assembly.box(frameColor, -track - 2.5f, 16, offset - 6, track * 2 + 5, 3, 12);
        for (int side : new int[]{-1, 1}) {
            var strut = joint(assembly, null, STATIC, side * track, hip, offset, 0);
            strut.box(frameColor, -2.6f, 0, -2.8f, 5.2f, 17.8f - hip, 5.6f)
                    .box(SILVER, -1, 6, -3.3f, 2, 10 - hip, 1.8f)
                    .box(cap, -3.25f, 5, -3.7f, 6.5f, 5, 6.8f);
            for (int z : new int[]{-5, 5}) {
                var roll = joint(assembly, null, ROLLER, side * (track + 2.15f), 19.75f, offset + z, 0);
                roll.box(RUBBER, -2.4f, -4.25f, -3, 4.8f, 8.5f, 6)
                        .box(RUBBER, -2.45f, -3, -4.25f, 4.9f, 6, 8.5f)
                        .box(GRAPHITE, -2.57f, -1.9f, -2.75f, 5.14f, 3.8f, 5.5f)
                        .box(cap, -2.75f, -.95f, -1.7f, 5.5f, 1.9f, 3.4f);
            }
        }
    }

    private static void toolHeads(Shape arm) {
        var coupler = arm.child(TOOL_EMPTY);
        coupler.box(GRAPHITE, -2.9f, 10.8f, -6.3f, 5.8f, 5.5f, 4.5f)
                .box(GOLD, -3.1f, 12.1f, -6.55f, 6.2f, 3.8f, 1.2f)
                .box(STEEL, -1.95f, 13.05f, -8.65f, 3.9f, 2, 2.2f)
                .box(BLACK, -.72f, 13.1f, -8.8f, 1.44f, 1.88f, .35f);
        var drill = arm.child(DRILL);
        drill.box(GRAPHITE, -3.8f, 9.8f, -10.15f, 7.6f, 7.6f, 10)
                .box(GOLD, -4.15f, 10.25f, -9.55f, 8.3f, 6.6f, 2.05f);
        drillBit(drill, 0, 13.6f, -9.7f, 1);
        bucket(arm.child(EXCAVATOR), 5.2f, 8.5f, 4);
        var heavy = arm.child(HEAVY_DRILL);
        heavy.box(GRAPHITE, -5.1f, 9.15f, -10.5f, 10.2f, 9.5f, 9.4f)
                .box(YELLOW, -5.45f, 9.7f, -10.9f, 10.9f, 2.15f, 7.3f)
                .box(BLACK, -5.2f, 12, -11.8f, 10.4f, 5.3f, 2);
        for (int x : new int[]{-3, 0, 3}) drillBit(heavy, x, 14.6f, -10.8f, .67f);
        bucket(arm.child(HEAVY_EXCAVATOR), 7.1f, 11, 5);
        var armored = arm.child(ARMORED_DRILL);
        armored.box(NAVY, -6.05f, 8.25f, -12.45f, 12.1f, 10.5f, 13.2f)
                .box(IVORY, -6.5f, 8.1f, -10.7f, 13, 3.1f, 10.7f)
                .box(BLUE, -6.7f, 11, -11.7f, 2.45f, 6.6f, 10.3f)
                .box(BLUE, 4.25f, 11, -11.7f, 2.45f, 6.6f, 10.3f)
                .box(GOLD, -5.65f, 10.8f, -12.7f, 11.3f, 1.5f, 2.4f)
                .box(BLACK, -5.7f, 12.3f, -13.1f, 11.4f, 5.4f, 1.5f);
        drillBit(armored, 0, 14.55f, -12.6f, 1.16f);
        for (int side : new int[]{-1, 1}) {
            drillBit(armored, side * 3.95f, 14.7f, -11.9f, .54f);
            armored.box(AMBER_LAMP, side * 4.85f - .62f, 8.7f, -10.95f, 1.24f, 1, .55f);
        }
    }

    private static void drillBit(Shape shape, float x, float y, float z, float size) {
        for (int step = 0; step < 4; step++) {
            float width = (4.2f - step * .91f) * size;
            float length = (step == 0 ? 2.8f : 2.4f) * size;
            shape.box(step % 2 == 0 ? STEEL : SILVER, x - width / 2, y - width / 2,
                    z - (2.15f * step * size) - length, width, width, length + .2f);
        }
    }

    private static void bucket(Shape scoop, float halfWidth, float depth, int teeth) {
        float front = -6 - depth;
        scoop.box(STEEL, -3.2f, 10.45f, -7.6f, 6.4f, 6.2f, 5.9f)
                .box(GRAPHITE, -halfWidth, 13.1f, -8, 2 * halfWidth, 7.2f, 2.2f)
                .box(STEEL, -halfWidth, 19.2f, front, 2 * halfWidth, 1.7f, depth + .6f)
                .box(GRAPHITE, -halfWidth, 14.9f, front + .35f, 1.4f, 5, depth - .35f)
                .box(GRAPHITE, halfWidth - 1.4f, 14.9f, front + .35f, 1.4f, 5, depth - .35f)
                .box(GOLD, -halfWidth, 13.55f, -8.35f, 2 * halfWidth, 1.2f, 1.05f);
        for (int i = 0; i < teeth; i++) {
            float x = -halfWidth + (i + .5f) * 2 * halfWidth / teeth;
            scoop.box(SILVER, x - .75f, 19.3f, front - 2.05f, 1.5f, 1.4f, 2.2f);
        }
    }

    // Stylized, interchangeable in-game forearm silhouettes.
    private static void weapons(Shape arm, Material panel, Material light) {
        arm.child(LASER).box(BLACK, -2.75f, 10.65f, -11, 5.5f, 6.6f, 11)
                .box(panel, -2.95f, 10.4f, -9.2f, 5.9f, 2.25f, 8.7f)
                .box(GRAPHITE, -2.35f, 11.4f, -12.15f, 4.7f, 4.75f, 2)
                .box(light, -1.15f, 12.7f, -12.4f, 2.3f, 2.25f, .6f);
        var melee = arm.child(MELEE);
        melee.box(GRAPHITE, -2.7f, 11.35f, -7.5f, 5.4f, 5.4f, 6)
                .box(panel, -3.7f, 11.7f, -8.2f, 7.4f, 4, 1.8f)
                .box(STEEL, -1.2f, 11.1f, -17.7f, 2.4f, 4.8f, 10.2f)
                .box(SILVER, -1, 10.65f, -16.6f, 2, 1.3f, 8.8f)
                .box(SILVER, -.7f, 12, -20, 1.4f, 2.6f, 2.7f);
        var repeat = arm.child(MACHINE_GUN);
        repeat.box(GRAPHITE, -3.5f, 10.5f, -8, 7, 7, 8)
                .box(panel, -3.75f, 10.3f, -8.45f, 7.5f, 2.1f, 7.6f)
                .box(BLACK, -2.9f, 12.1f, -14, 5.8f, 4, 2.1f);
        for (int x : new int[]{-2, 0, 2}) repeat.box(STEEL, x - .58f, 13, -14.9f, 1.16f, 2.1f, 8);
        arm.child(SNIPER).box(GRAPHITE, -2.2f, 10.3f, -10.5f, 4.4f, 6, 10)
                .box(panel, -2.35f, 10, -12, 4.7f, 2.15f, 10.8f)
                .box(STEEL, -.95f, 12.5f, -20.3f, 1.9f, 2.1f, 11.3f)
                .box(BLACK, -1.35f, 12.15f, -21.4f, 2.7f, 2.8f, 2.8f)
                .box(light, -.6f, 8.95f, -11.75f, 1.2f, 1.1f, 3.1f);
        var pod = arm.child(EXPLOSIVE);
        pod.box(GRAPHITE, -3.7f, 8.8f, -10, 7.4f, 9, 10)
                .box(panel, -3.9f, 8.35f, -10.45f, 7.8f, 2.25f, 10)
                .box(STEEL, -3.6f, 10.7f, -11, 7.2f, 6.6f, 1.8f);
        for (int x : new int[]{-2, 1}) for (int y : new int[]{11, 14}) pod.box(BLACK, x, y, -11.35f, 2, 2.1f, .62f);
    }

    private static void wirelessReceiver(Shape frame, float x, float y, float z, Material shell) {
        frame.child(WIRELESS).box(GRAPHITE, x - 2, y, z - 1.5f, 4, 3.4f, 3)
                .box(shell, x - 2.35f, y - .75f, z - 1.85f, 4.7f, 1.75f, 3.7f)
                .box(CYAN_LAMP, x - 1.15f, y + 1, z - 1.87f, 2.3f, .9f, .48f)
                .box(CYAN_LAMP, x - 1.15f, y + 1, z + 1.4f, 2.3f, .9f, .48f)
                .box(STEEL, x + 1.15f, y - 3.35f, z, .75f, 2.85f, .75f)
                .box(TEAL, x + .8f, y - 3.75f, z - .3f, 1.45f, .65f, 1.25f);
    }

    private static void rearGear(Shape frame, float x, float y, float z, Material shell) {
        frame.child(GENERATOR).box(GRAPHITE, x - 3.5f, y - 3, z - 2, 7, 9, 6)
                .box(GOLD, x - 3.75f, y - 4.2f, z - 2, 7.5f, 2.2f, 6)
                .box(BLACK, x - 3.1f, y - 9, z, 2, 9, 2)
                .box(BLACK, x + 1.1f, y - 7, z, 2, 7, 2)
                .box(RED_LAMP, x - 2, y + 1.25f, z + 3.8f, 4, 1.3f, .55f);
        frame.child(FUSION).box(NAVY, x - 4.6f, y - 4, z - 1, 9.2f, 11, 6)
                .box(IVORY, x - 4.8f, y - 5, z + .1f, 9.6f, 2, 5)
                .box(TEAL, x - 2.6f, y - 2, z + 4.65f, 5.2f, 7.1f, .75f)
                .box(CYAN_LAMP, x - 1.5f, y - 1, z + 5.25f, 3, 5, .5f);
        var cooler = frame.child(RADIATOR);
        cooler.box(GRAPHITE, x - 9.6f, y + .2f, z - .5f, 5.1f, 8, 4.1f)
                .box(TEAL, x - 9.4f, y + 1, z + 3.3f, 4.5f, 6, .7f);
        for (int fin = 0; fin < 4; fin++) cooler.box(STEEL, x - 9.8f, y + 1 + fin * 1.6f, z + 3.5f, 5.4f, .65f, 1.3f);
        frame.child(DRIVE).box(GRAPHITE, x + 4.6f, y + 1.6f, z - 1.5f, 5.2f, 6.2f, 6.1f)
                .box(shell, x + 4.4f, y + 1.3f, z - 1.45f, 5.6f, 2, 6.3f)
                .box(AMBER_LAMP, x + 6.1f, y + 4.8f, z + 4.5f, 2.25f, 1.25f, .6f);
        supportBox(frame.child(REPAIR), x - 5.3f, y, z, GOLD, AMBER_LAMP, false);
        supportBox(frame.child(FRIENDLY_REPAIR), x - 5.3f, y, z, GREEN, MINT_LAMP, false);
        supportBox(frame.child(MAINTENANCE_DRONE), x - 5.3f, y, z, TEAL, CYAN_LAMP, true);
        supportBox(frame.child(HEALING_DRONE), x - 5.3f, y, z, IVORY, MINT_LAMP, true);
        supportBox(frame.child(ATTACK_DRONE), x + 6.5f, y - 2.5f, z, RED, AMBER_LAMP, true);
        supportBox(frame.child(DEFENSE_DRONE), x + 6.5f, y - 2.5f, z, BLUE, CYAN_LAMP, true);
    }

    private static void supportBox(Shape module, float x, float y, float z, Material shell, Material light, boolean dock) {
        module.box(GRAPHITE, x - 2.4f, y, z, 4.8f, 5.2f, 4.2f)
                .box(shell, x - 2.45f, y + .35f, z + 3.5f, 4.9f, 4.8f, 1.1f)
                .box(light, x - 1.35f, y + 2.2f, z + 4.5f, 2.7f, .9f, .45f);
        if (dock) {
            module.box(shell, x - 3.25f, y - .5f, z + 1.8f, 6.5f, 1.5f, 4.4f)
                    .box(BLACK, x - 2.9f, y + .1f, z + 5.3f, 5.8f, 2.6f, 1.15f);
        } else {
            module.box(light, x - .45f, y + 1.35f, z + 4.5f, .9f, 2.6f, .45f);
        }
    }

    private BestMechGeometry() {}
}
