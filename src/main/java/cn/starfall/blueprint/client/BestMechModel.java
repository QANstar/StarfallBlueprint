package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechEntity;
import cn.starfall.blueprint.BestMechArms;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Original industrial walker: open cockpit, braced chassis and interchangeable right tool arm. */
public final class BestMechModel extends EntityModel<BestMechEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(StarfallBlueprint.ID, "best_engineering_mech"), "main");
    private final ModelPart root, leftLeg, rightLeg, leftArm, rightArm, drillHead, bucketHead, heavyDrillHead, reinforcement, generator, wheels, heavyFrame;
    private final ModelPart[] wheelRollers = new ModelPart[4];

    public BestMechModel(ModelPart root) {
        this.root = root;
        heavyFrame = root.getChild("heavy_frame");
        reinforcement = root.getChild("reinforcement");
        generator = root.getChild("generator");
        wheels = root.getChild("wheels");
        for (int i = 0; i < wheelRollers.length; i++) wheelRollers[i] = wheels.getChild("roller_" + i);
        leftLeg = root.getChild("left_leg"); rightLeg = root.getChild("right_leg");
        leftArm = root.getChild("left_arm"); rightArm = root.getChild("right_arm");
        drillHead = rightArm.getChild("drill_head"); bucketHead = rightArm.getChild("bucket_head"); heavyDrillHead = rightArm.getChild("heavy_drill_head");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("chassis", CubeListBuilder.create()
                .texOffs(0,0).addBox(-9,-13,-6,18,9,12)
                .texOffs(0,24).addBox(-7,-25,4,14,15,5)
                .texOffs(80,0).addBox(-9,-27,-5,3,15,3)
                .texOffs(80,0).addBox(6,-27,-5,3,15,3)
                // Leave the center open above the 2.2-block passenger attachment.
                .texOffs(48,0).addBox(-9,-27,-5,3,2,12)
                .texOffs(48,0).addBox(6,-27,-5,3,2,12)
                .texOffs(64,24).addBox(-7,-14,-8,14,3,2)
                .texOffs(104,0).addBox(-11,-12,-5,2,6,10)
                .texOffs(104,0).addBox(9,-12,-5,2,6,10), PartPose.ZERO);
        // Rear cargo housing and side bracing keep the cockpit and passenger head space open.
        root.addOrReplaceChild("reinforcement", CubeListBuilder.create()
                .texOffs(0,24).addBox(-9,-23,9,18,17,5)
                .texOffs(0,48).addBox(-10,-13,-7,3,10,14)
                .texOffs(0,48).addBox(7,-13,-7,3,10,14)
                .texOffs(64,24).addBox(-9,-7,-8,18,4,2), PartPose.ZERO);
        // Broader shoulders and rear cargo structure distinguish the heavy platform while leaving the cockpit open.
        root.addOrReplaceChild("heavy_frame", CubeListBuilder.create()
                .texOffs(0,24).addBox(-10,-24,9,20,18,6)
                .texOffs(64,40).addBox(-14,-23,-4,4,10,8)
                .texOffs(64,40).addBox(10,-23,-4,4,10,8)
                .texOffs(0,0).addBox(-10,-11,-9,20,5,3), PartPose.ZERO);
        // Compact rear generator and twin exhausts; cockpit and the original collision box stay clear.
        root.addOrReplaceChild("generator", CubeListBuilder.create()
                .texOffs(64,84).addBox(-4,-25,9,8,8,5.25f)
                .texOffs(80,0).addBox(-8,-26,10,3,11,3)
                .texOffs(80,0).addBox(5,-26,10,3,11,3), PartPose.ZERO);
        for (int side : new int[]{-1,1}) {
            root.addOrReplaceChild(side < 0 ? "right_leg" : "left_leg", CubeListBuilder.create()
                    .texOffs(0,48).addBox(-3,0,-3,6,14,6)
                    .texOffs(28,48).addBox(-4,12,-4,8,4,8)
                    .texOffs(0,72).addBox(-3,16,-3,6,9,6)
                    .texOffs(28,72).addBox(-4,24,-7,8,4,12), PartPose.offset(side*6,-4,0));
        }
        // Four road wheels replace the visible walking struts, retaining the same vehicle envelope.
        var wheelFrame = root.addOrReplaceChild("wheels", CubeListBuilder.create()
                .texOffs(0,48).addBox(-8,-4,-2,4,24,4)
                .texOffs(0,48).addBox(4,-4,-2,4,24,4)
                .texOffs(28,48).addBox(-8,17,-7,16,3,14), PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            wheelFrame.addOrReplaceChild("roller_" + i, CubeListBuilder.create()
                    .texOffs(0,72).addBox(-2,-4,-3,4,8,6)
                    .texOffs(28,72).addBox(-2,-3,-4,4,6,8)
                    .texOffs(64,24).addBox(-2.25f,-2,-2,4.5f,4,4),
                    PartPose.offset(i < 2 ? -8 : 8,20,i % 2 == 0 ? -6 : 6));
        }
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(64,40).addBox(-3,-2,-4,6,7,8)
                .texOffs(64,58).addBox(-2,5,-2,4,11,4)
                .texOffs(84,58).addBox(-3,14,-4,6,7,7)
                .texOffs(108,40).addBox(-3,19,-6,2,5,3)
                .texOffs(108,40).addBox(1,19,-6,2,5,3), PartPose.offset(11,-19,0));
        var toolArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(64,40).addBox(-3,-2,-4,6,7,8)
                .texOffs(64,58).addBox(-2,5,-2,4,9,4), PartPose.offset(-11,-19,0));
        toolArm.addOrReplaceChild("drill_head", CubeListBuilder.create()
                .texOffs(64,84).addBox(-4,12,-5,8,8,9)
                .texOffs(100,84).addBox(-3,13,-11,6,6,6)
                .texOffs(100,104).addBox(-2,14,-15,4,4,4)
                .texOffs(116,104).addBox(-1,15,-18,2,2,3), PartPose.ZERO);
        // Open bucket with two side cheeks, a back plate and four visible teeth.
        var bucket = CubeListBuilder.create()
                .texOffs(64,84).addBox(-3,12,-4,6,7,6)
                .texOffs(28,72).addBox(-6,17,-7,12,7,2)
                .texOffs(28,72).addBox(-6,22,-16,12,2,9)
                .texOffs(0,48).addBox(-6,17,-16,2,5,9)
                .texOffs(0,48).addBox(4,17,-16,2,5,9);
        for (int tooth = 0; tooth < 4; tooth++)
            bucket.texOffs(108,40).addBox(-6 + tooth * 3,22,-19,2,2,3);
        toolArm.addOrReplaceChild("bucket_head", bucket, PartPose.ZERO);
        var heavyDrill = CubeListBuilder.create()
                .texOffs(64,84).addBox(-5,11,-7,10,11,7)
                .texOffs(0,48).addBox(-6,12,-10,12,9,3);
        for(int x : new int[]{-4,0,4}) {
            heavyDrill.texOffs(100,84).addBox(x-1.75f,13,-15,3.5f,7,5)
                    .texOffs(100,104).addBox(x-1.25f,14,-19,2.5f,5,4)
                    .texOffs(116,104).addBox(x-.75f,15,-22,1.5f,3,3);
        }
        toolArm.addOrReplaceChild("heavy_drill_head", heavyDrill, PartPose.ZERO);
        return LayerDefinition.create(mesh,128,128);
    }

    @Override public void setupAnim(BestMechEntity entity, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
        heavyFrame.visible = entity.heavy();
        reinforcement.visible = !entity.heavy() && cn.starfall.blueprint.BestMechBodies.REINFORCED.equals(entity.bodyKey());
        generator.visible = cn.starfall.blueprint.BestMechCores.GENERATOR.equals(entity.coreKey());
        drillHead.visible = BestMechArms.DRILL.equals(entity.armKey());
        bucketHead.visible = BestMechArms.EXCAVATOR.equals(entity.armKey());
        heavyDrillHead.visible = BestMechArms.HEAVY_DRILL.equals(entity.armKey());
        boolean cruising = cn.starfall.blueprint.BestMechLegs.CRUISE.equals(entity.legsKey());
        wheels.visible = cruising;
        leftLeg.visible = !cruising; rightLeg.visible = !cruising;
        for (ModelPart roller : wheelRollers) roller.xRot = limbSwing * 0.5f;
        float stride = (cruising ? 0 : 1) * Mth.cos(limbSwing*0.65F)*Math.min(limbSwingAmount,0.65F);
        leftLeg.xRot = stride; rightLeg.xRot = -stride;
        leftArm.xRot = -stride*0.45F; rightArm.xRot = stride*0.25F;
    }

    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(pose,vertices,light,overlay,color);
    }
}
