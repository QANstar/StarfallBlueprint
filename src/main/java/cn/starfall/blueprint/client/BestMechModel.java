package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechArms;
import cn.starfall.blueprint.BestMechBodies;
import cn.starfall.blueprint.BestMechCombatSystems;
import cn.starfall.blueprint.BestMechCores;
import cn.starfall.blueprint.BestMechEntity;
import cn.starfall.blueprint.BestMechLegs;
import cn.starfall.blueprint.BestMechModules;
import cn.starfall.blueprint.BestMechProgression;
import cn.starfall.blueprint.BestMechSupport;
import cn.starfall.blueprint.StarfallBlueprint;
import cn.starfall.blueprint.client.BestMechGeometry.Feature;
import cn.starfall.blueprint.client.BestMechGeometry.Material;
import cn.starfall.blueprint.client.BestMechGeometry.Shape;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/** Three chassis on the same renderer, driven by tracked platform, upgrade and equipment state. */
public final class BestMechModel extends EntityModel<BestMechEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(StarfallBlueprint.ID, "best_engineering_mech"), "main");
    static final ResourceLocation PALETTE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public enum Platform {
        ENGINEERING(BestMechProgression.ENGINEERING), COMBAT(BestMechProgression.COMBAT), UNIVERSAL(BestMechProgression.UNIVERSAL);

        final String key;

        Platform(String key) { this.key = key; }

        static Platform fromKey(String key) {
            return switch (key) {
                case BestMechProgression.COMBAT -> COMBAT;
                case BestMechProgression.UNIVERSAL -> UNIVERSAL;
                default -> ENGINEERING;
            };
        }
    }

    private static final EnumMap<Platform, Shape> PLANS = new EnumMap<>(Platform.class);
    static {
        for (Platform platform : Platform.values()) PLANS.put(platform, BestMechGeometry.create(platform));
    }

    private final EnumMap<Platform, BakedShape> chassis = new EnumMap<>(Platform.class);
    private final EnumSet<Feature> active = EnumSet.noneOf(Feature.class);
    private Platform platform;
    private float stride;
    private float wheelRotation;
    private boolean powered;

    public BestMechModel(ModelPart root) {
        PLANS.forEach((platform, shape) -> chassis.put(platform, new BakedShape(shape, root.getChild(platform.key))));
        posePreview(Platform.ENGINEERING, 0);
    }

    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition();
        PLANS.forEach((platform, shape) -> shape.bake(mesh.getRoot(), platform.key));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(BestMechEntity entity, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
        Platform type = Platform.fromKey(entity.platformType());
        configure(type, entity.upgradeMask(), entity.workArmKey(), entity.legsKey(), entity.bodyKey(), entity.coreKey(),
                entity.moduleKey(), entity.visualWeaponKey(), entity.visualDroneKey(), entity.visualWirelessInstalled());
        stride = Mth.cos(limbSwing * .6662f) * Mth.clamp(limbSwingAmount, 0f, .58f);
        wheelRotation = limbSwing * .62f;
        powered = !entity.isolated() && entity.energy() > 0;
    }

    /** Standalone rendering uses the same rig as the entity, including its default equipment. */
    public void posePreview(Platform type, int upgrades) {
        posePreview(type, upgrades, "", "", "", "", "", "", "");
    }

    /** Optional installed-item keys use the same resolution rules as live tracked equipment. */
    public void posePreview(Platform type, int upgrades, String arm, String legs, String body, String core,
                            String utility, String weapon, String drone) {
        posePreview(type, upgrades, arm, legs, body, core, utility, weapon, drone, false);
    }

    public void posePreview(Platform type, int upgrades, String arm, String legs, String body, String core,
                            String utility, String weapon, String drone, boolean wireless) {
        int mask = BestMechProgression.sanitize(type.key, upgrades);
        String effectiveArm = arm.isEmpty() ? defaultArm(type, mask) : arm;
        configure(type, mask, effectiveArm, legs, body, core, utility, weapon, drone, wireless);
        stride = 0;
        wheelRotation = 0;
        powered = true;
    }

    private static String defaultArm(Platform platform, int mask) {
        if ((mask & (BestMechProgression.ENGINEERING_EXTENSION | BestMechProgression.ZHENYUAN)) != 0) {
            return BestMechArms.HEAVY_DRILL;
        }
        return platform == Platform.UNIVERSAL ? BestMechArms.DRILL : "";
    }

    private void configure(Platform type, int upgrades, String workArm, String legs, String body, String core,
                           String utility, String weapon, String drone, boolean wireless) {
        platform = type;
        active.clear();
        if (wireless) active.add(Feature.WIRELESS);
        int mask = BestMechProgression.sanitize(type.key, upgrades);
        addUpgrade(mask, BestMechProgression.ENGINEERING_EXTENSION, Feature.EXTENSION);
        addUpgrade(mask, BestMechProgression.MOBILITY, Feature.MOBILITY);
        addUpgrade(mask, BestMechProgression.ENDURANCE, Feature.ENDURANCE);
        addUpgrade(mask, BestMechProgression.FIRE_CONTROL, Feature.FIRE_CONTROL);
        addUpgrade(mask, BestMechProgression.ARMOR, Feature.ARMOR);
        addUpgrade(mask, BestMechProgression.SUSTAINED_FIRE, Feature.SUSTAINED_FIRE);
        addUpgrade(mask, BestMechProgression.ZHENYUAN, Feature.ZHENYUAN);
        addUpgrade(mask, BestMechProgression.TIANPU, Feature.TIANPU);
        addUpgrade(mask, BestMechProgression.CHANGCHUAN, Feature.CHANGCHUAN);
        active.add(BestMechLegs.CRUISE.equals(legs) ? Feature.CRUISE : Feature.WALK);
        if (BestMechBodies.REINFORCED.equals(body)) active.add(Feature.REINFORCED);
        if (BestMechCores.GENERATOR.equals(core)) active.add(Feature.GENERATOR);
        if (BestMechCores.FUSION.equals(core)) active.add(Feature.FUSION);
        addFeature(switch (utility) {
            case BestMechModules.RADIATOR -> Feature.RADIATOR;
            case BestMechModules.DRIVE -> Feature.DRIVE;
            case BestMechSupport.REPAIR -> Feature.REPAIR;
            case BestMechSupport.FRIENDLY_REPAIR -> Feature.FRIENDLY_REPAIR;
            case BestMechSupport.MAINTENANCE_DRONE -> Feature.MAINTENANCE_DRONE;
            case BestMechSupport.HEALING_DRONE -> Feature.HEALING_DRONE;
            default -> null;
        });
        if (type != Platform.COMBAT) {
            boolean armored = active.contains(Feature.ZHENYUAN);
            active.add(switch (workArm) {
                case BestMechArms.DRILL -> armored ? Feature.ARMORED_DRILL : Feature.DRILL;
                case BestMechArms.EXCAVATOR -> Feature.EXCAVATOR;
                case BestMechArms.HEAVY_DRILL -> armored ? Feature.ARMORED_DRILL : Feature.HEAVY_DRILL;
                case BestMechArms.HEAVY_EXCAVATOR -> Feature.HEAVY_EXCAVATOR;
                default -> Feature.TOOL_EMPTY;
            });
        }
        if (type != Platform.ENGINEERING) {
            active.add(switch (weapon) {
                case BestMechCombatSystems.MELEE -> Feature.MELEE;
                case BestMechCombatSystems.MACHINE_GUN -> Feature.MACHINE_GUN;
                case BestMechCombatSystems.SNIPER -> Feature.SNIPER;
                case BestMechCombatSystems.EXPLOSIVE -> Feature.EXPLOSIVE;
                default -> Feature.LASER;
            });
            addFeature(switch (drone) {
                case BestMechCombatSystems.ATTACK_DRONE -> Feature.ATTACK_DRONE;
                case BestMechCombatSystems.DEFENSE_DRONE -> Feature.DEFENSE_DRONE;
                default -> null;
            });
        }
    }

    private void addUpgrade(int mask, int bit, Feature feature) {
        if ((mask & bit) != 0) active.add(feature);
    }

    private void addFeature(Feature feature) {
        if (feature != null) active.add(feature);
    }

    public void render(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        renderToBuffer(pose, buffers.getBuffer(renderType(PALETTE_TEXTURE)), light, overlay, -1);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, int color) {
        draw(chassis.get(platform), pose, vertices, light, overlay, color);
    }

    private void draw(BakedShape baked, PoseStack pose, VertexConsumer vertices, int light, int overlay, int color) {
        Shape shape = baked.shape;
        if (shape.feature != null && !active.contains(shape.feature)) return;
        float walking = active.contains(Feature.WALK) ? stride : 0;
        baked.transform.xRot = shape.restPitch + switch (shape.motion) {
            case STATIC -> 0;
            case LEFT_LEG -> walking;
            case RIGHT_LEG -> -walking;
            case LEFT_ARM -> -.31f * walking;
            case RIGHT_ARM -> .22f * walking;
            case ROLLER -> wheelRotation;
        };
        pose.pushPose();
        baked.transform.translateAndRotate(pose);
        for (Surface surface : baked.surfaces) {
            int tint = color == -1 ? surface.material.color : FastColor.ARGB32.multiply(color, surface.material.color);
            int lit = light;
            if (surface.material.lamp) {
                if (powered) lit = LightTexture.FULL_BRIGHT;
                else tint = FastColor.ARGB32.multiply(tint, 0xff65747a);
            }
            surface.model.render(pose, vertices, lit, overlay, tint);
        }
        for (BakedShape child : baked.children) draw(child, pose, vertices, light, overlay, color);
        pose.popPose();
    }

    private record Surface(Material material, ModelPart model) {}

    private static final class BakedShape {
        final Shape shape;
        final ModelPart transform;
        final List<Surface> surfaces = new ArrayList<>();
        final List<BakedShape> children = new ArrayList<>();

        BakedShape(Shape shape, ModelPart transform) {
            this.shape = shape;
            this.transform = transform;
            for (Material material : shape.materials.keySet()) surfaces.add(new Surface(material, transform.getChild(material.name())));
            for (int i = 0; i < shape.children.size(); i++) {
                children.add(new BakedShape(shape.children.get(i), transform.getChild("shape_" + i)));
            }
        }
    }
}
