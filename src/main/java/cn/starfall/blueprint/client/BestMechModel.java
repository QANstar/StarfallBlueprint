package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechEntity;
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

/** Original industrial walker: open cockpit, braced chassis and asymmetric drill arm. */
public final class BestMechModel extends EntityModel<BestMechEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(StarfallBlueprint.ID, "best_engineering_mech"), "main");
    private final ModelPart root, leftLeg, rightLeg, leftArm, rightArm;

    public BestMechModel(ModelPart root) {
        this.root = root;
        leftLeg = root.getChild("left_leg"); rightLeg = root.getChild("right_leg");
        leftArm = root.getChild("left_arm"); rightArm = root.getChild("right_arm");
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
        for (int side : new int[]{-1,1}) {
            root.addOrReplaceChild(side < 0 ? "right_leg" : "left_leg", CubeListBuilder.create()
                    .texOffs(0,48).addBox(-3,0,-3,6,14,6)
                    .texOffs(28,48).addBox(-4,12,-4,8,4,8)
                    .texOffs(0,72).addBox(-3,16,-3,6,9,6)
                    .texOffs(28,72).addBox(-4,24,-7,8,4,12), PartPose.offset(side*6,-4,0));
        }
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(64,40).addBox(-3,-2,-4,6,7,8)
                .texOffs(64,58).addBox(-2,5,-2,4,11,4)
                .texOffs(84,58).addBox(-3,14,-4,6,7,7)
                .texOffs(108,40).addBox(-3,19,-6,2,5,3)
                .texOffs(108,40).addBox(1,19,-6,2,5,3), PartPose.offset(11,-19,0));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(64,40).addBox(-3,-2,-4,6,7,8)
                .texOffs(64,58).addBox(-2,5,-2,4,9,4)
                .texOffs(64,84).addBox(-4,12,-5,8,8,9)
                .texOffs(100,84).addBox(-3,13,-11,6,6,6)
                .texOffs(100,104).addBox(-2,14,-15,4,4,4)
                .texOffs(116,104).addBox(-1,15,-18,2,2,3), PartPose.offset(-11,-19,0));
        return LayerDefinition.create(mesh,128,128);
    }

    @Override public void setupAnim(BestMechEntity entity, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
        float stride = Mth.cos(limbSwing*0.65F)*Math.min(limbSwingAmount,0.65F);
        leftLeg.xRot = stride; rightLeg.xRot = -stride;
        leftArm.xRot = -stride*0.45F; rightArm.xRot = stride*0.25F;
    }

    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(pose,vertices,light,overlay,color);
    }
}
