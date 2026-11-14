package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Continuous tapered hull, swept polygon wings, compact fins and recessed engine details. */
public final class PeregrineRenderer extends EntityRenderer<PeregrineEntity> {
    public static final ResourceLocation TEXTURE=StarfallBlueprint.id("textures/entity/hj_peregrine.png");
    public PeregrineRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=1.8f;}
    @Override public ResourceLocation getTextureLocation(PeregrineEntity f){return TEXTURE;}
    @Override public void render(PeregrineEntity f,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();pose.translate(0,.45,0);pose.mulPose(Axis.YP.rotationDegrees(180-yaw));pose.mulPose(Axis.XP.rotationDegrees(-net.minecraft.util.Mth.lerp(partial,f.xRotO,f.getXRot())));pose.mulPose(Axis.ZP.rotationDegrees(-f.visualRoll(partial)));pose.scale(-.85f,-.85f,.85f);
        PeregrineModel.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),light,f.onGround()||f.throttle()<.3);
        if(f.throttle()>.1&&f.energy()>0){
            var b=buffers.getBuffer(RenderType.lightning());for(double x:new double[]{-5.1,5.1}){var start=new Vec3(x/16,.4/16,37.6/16);BlackDogBeamRenderer.tube(b,pose,start,start.add(0,0,(f.boosting()?1.4:.35)+f.throttle()*.9),.095,0x83dfff,.8f,8);}
        }
        pose.popPose();super.render(f,yaw,partial,pose,buffers,light);
    }
}
