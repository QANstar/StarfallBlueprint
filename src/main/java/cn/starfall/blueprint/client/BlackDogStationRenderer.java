package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

/** Only the controller renders the real configuration, centered inside the two-block gantry. */
public final class BlackDogStationRenderer implements BlockEntityRenderer<IndustryBlockEntity> {
    private final BlackDogArmorModel model;
    public BlackDogStationRenderer(BlockEntityRendererProvider.Context context){model=new BlackDogArmorModel(context.bakeLayer(BlackDogArmorModel.LAYER));}
    @Override public void render(IndustryBlockEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(!be.kind().equals("lc_armor_assembler")&&!be.kind().equals("lc_armor_charger"))return;
        CompoundTag data=be.blackDogDisplay;int[] tiers=data.getIntArray("ChassisTiers");if(tiers.length!=8||java.util.Arrays.stream(tiers).sum()==0)return;
        var right=be.facing().getClockWise();var back=be.facing().getOpposite();
        pose.pushPose();pose.translate(.5+right.getStepX()*.5+back.getStepX()*.5,1.55,.5+right.getStepZ()*.5+back.getStepZ()*.5);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-be.facing().toYRot()));pose.scale(.78f,-.78f,-.78f);
        model.workstation(data);model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(StarfallBlueprint.id("textures/models/armor/blackdog_exosuit.png"))),Math.max(light,0x700070),OverlayTexture.NO_OVERLAY);pose.popPose();
    }
    @Override public AABB getRenderBoundingBox(IndustryBlockEntity be){return new AABB(be.getBlockPos()).inflate(2);}
    public static void preview(net.minecraft.client.gui.GuiGraphics g,BlackDogArmorModel model,CompoundTag data,int x,int bottom,float scale,float yaw){
        g.flush();com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        com.mojang.blaze3d.systems.RenderSystem.setShaderLights(new org.joml.Vector3f(.4f,-.7f,-.6f).normalize(),new org.joml.Vector3f(-.5f,-.4f,.7f).normalize());
        g.pose().pushPose();g.pose().translate(x,bottom,180);g.pose().scale(scale,scale,scale);g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));g.pose().translate(0,-1.5,0);
        model.workstation(data);model.renderToBuffer(g.pose(),g.bufferSource().getBuffer(RenderType.entityCutoutNoCull(StarfallBlueprint.id("textures/models/armor/blackdog_exosuit.png"))),15728880,OverlayTexture.NO_OVERLAY);g.flush();g.pose().popPose();com.mojang.blaze3d.platform.Lighting.setupForFlatItems();com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
    }
}
