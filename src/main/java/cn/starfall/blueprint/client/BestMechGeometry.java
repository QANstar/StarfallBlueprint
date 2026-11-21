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
            case ENGINEERING -> AbyssEngineering.create();
            case COMBAT -> TempestCombat.create();
            case UNIVERSAL -> AuroraUniversal.create();
        };
    }

    static Shape joint(Shape parent, Feature feature, Motion motion, float x, float y, float z, float pitch) {
        return parent.child(feature, motion, x, y, z, pitch, 0);
    }

    static Motion leg(int side) { return side == 1 ? LEFT_LEG : RIGHT_LEG; }

    static void tread(Shape foot, float halfWidth, float y, float front) {
        for (int i = 0; i < 4; i++) {
            foot.box(BLACK, -halfWidth - .12f, y, front + i * 3, halfWidth * 2 + .24f, .8f, 1);
        }
    }

    static void runningGear(Shape frame, float track, float hip, float offset, Material frameColor, Material cap) {
        var assembly = frame.child(CRUISE);
        assembly.box(frameColor, -track - 2.5f, 16, offset - 6, track * 2 + 5, 3, 12);
        for (int side : new int[]{-1, 1}) {
            var strut = joint(assembly, null, STATIC, side * track, hip, offset, 0);
            strut.box(frameColor, -2.6f, 0, -2.8f, 5.2f, 17.8f - hip, 5.6f)
                    .box(SILVER, -1, 6, -3.3f, 2, 10 - hip, 1.8f)
                    .box(cap, -3.25f, 5, -3.7f, 6.5f, 5, 6.8f);
            var fairing = strut.child(null, STATIC, 0, 5, 0, -.12f, side * .08f);
            fairing.box(frameColor, -4.2f, -3, -4, 8.4f, 9, 8.5f)
                    .box(cap, -3.7f, -2.5f, -4.8f, 7.4f, 5.5f, 3.5f);
            strut.box(frameColor, -3.3f, 15 - hip, -6.2f, 6.6f, 4.4f, 13)
                    .box(cap, -3.5f, 14.5f - hip, -4.8f, 7, 2, 10.6f);
            for (int z : new int[]{-5, 5}) {
                var roll = joint(assembly, null, ROLLER, side * (track + 2.15f), 19.75f, offset + z, 0);
                roll.box(RUBBER, -2.4f, -4.25f, -3, 4.8f, 8.5f, 6)
                        .box(RUBBER, -2.45f, -3, -4.25f, 4.9f, 6, 8.5f)
                        .box(GRAPHITE, -2.57f, -1.9f, -2.75f, 5.14f, 3.8f, 5.5f)
                        .box(cap, -2.75f, -.95f, -1.7f, 5.5f, 1.9f, 3.4f);
            }
        }
    }

    static void toolHeads(Shape arm) {
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

    static void drillBit(Shape shape, float x, float y, float z, float size) {
        for (int step = 0; step < 4; step++) {
            float width = (4.2f - step * .91f) * size;
            float length = (step == 0 ? 2.8f : 2.4f) * size;
            shape.box(step % 2 == 0 ? STEEL : SILVER, x - width / 2, y - width / 2,
                    z - (2.15f * step * size) - length, width, width, length + .2f);
        }
    }

    static void bucket(Shape scoop, float halfWidth, float depth, int teeth) {
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
    static void weapons(Shape arm, Material panel, Material light) {
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

    static void wirelessReceiver(Shape frame, float x, float y, float z, Material shell) {
        frame.child(WIRELESS).box(GRAPHITE, x - 2, y, z - 1.5f, 4, 3.4f, 3)
                .box(shell, x - 2.35f, y - .75f, z - 1.85f, 4.7f, 1.75f, 3.7f)
                .box(CYAN_LAMP, x - 1.15f, y + 1, z - 1.87f, 2.3f, .9f, .48f)
                .box(CYAN_LAMP, x - 1.15f, y + 1, z + 1.4f, 2.3f, .9f, .48f)
                .box(STEEL, x + 1.15f, y - 3.35f, z, .75f, 2.85f, .75f)
                .box(TEAL, x + .8f, y - 3.75f, z - .3f, 1.45f, .65f, 1.25f);
    }

    static void rearGear(Shape frame, float x, float y, float z, Material shell) {
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

    static void supportBox(Shape module, float x, float y, float z, Material shell, Material light, boolean dock) {
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
