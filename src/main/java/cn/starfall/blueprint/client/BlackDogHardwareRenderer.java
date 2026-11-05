package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Original multi-part hovering gun and finned missile meshes, with articulated aiming. */
public final class BlackDogHardwareRenderer<T extends Entity> extends EntityRenderer<T> {
    public static final ModelLayerLocation DRONE_LAYER=new ModelLayerLocation(StarfallBlueprint.id("blackdog_drone"),"main"),MISSILE_LAYER=new ModelLayerLocation(StarfallBlueprint.id("blackdog_missile"),"main");
    private static final ResourceLocation TEXTURE=StarfallBlueprint.id("textures/models/armor/blackdog_exosuit.png");
    private final ModelPart model;
    public BlackDogHardwareRenderer(EntityRendererProvider.Context context,boolean missile){super(context);model=context.bakeLayer(missile?MISSILE_LAYER:DRONE_LAYER);shadowRadius=missile?0:.25f;}
    public ResourceLocation getTextureLocation(T entity){return TEXTURE;}
    @Override public net.minecraft.world.phys.Vec3 getRenderOffset(T entity,float partial){
        return entity instanceof BlackDogDrone drone?drone.renderPosition(partial).subtract(entity.getPosition(partial)):super.getRenderOffset(entity,partial);
    }
    private static void box(PartDefinition root,String name,int uv,float x,float y,float z,float w,float h,float d){root.addOrReplaceChild(name,CubeListBuilder.create().texOffs(uv,0).addBox(x,y,z,w,h,d),PartPose.ZERO);}
    public static LayerDefinition droneLayer(){
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        box(root,"body",0,-4,-2,-4,8,4,8);box(root,"spine",96,-2,-3,-2,4,1,6);box(root,"reactor",64,-1,-3.3f,-1,2,.4f,3);
        for(int side:new int[]{-1,1}){
            box(root,"wing"+side,0,side<0?-7:4,-1.5f,-1,3,2,5);box(root,"wing_light"+side,64,side<0?-7.2f:6.8f,-1.8f,-.5f,.4f,.5f,4);
            box(root,"engine"+side,96,side<0?-5.5f:3.5f,-.5f,3,2,2,2);box(root,"exhaust"+side,64,side<0?-5:4,.2f,5,.9f,.9f,.3f);
        }
        var gimbal=root.addOrReplaceChild("gimbal",CubeListBuilder.create().texOffs(0,0).addBox(-2,-1.8f,-5,4,3.6f,3),PartPose.ZERO);
        box(gimbal,"barrel_left",96,-2,-.8f,-10,1.3f,1.6f,6);box(gimbal,"barrel_right",96,.7f,-.8f,-10,1.3f,1.6f,6);
        box(gimbal,"emitter",64,-.6f,-.6f,-10.4f,1.2f,1.2f,.5f);box(gimbal,"lens",64,-.8f,-2,-5.5f,1.6f,.6f,.5f);
        return LayerDefinition.create(mesh,128,128);
    }
    public static LayerDefinition missileLayer(){
        var mesh=new MeshDefinition();var root=mesh.getRoot();box(root,"body",0,-1.2f,-1.2f,-5,2.4f,2.4f,10);box(root,"nose",96,-.8f,-.8f,-8,1.6f,1.6f,3);box(root,"tip",64,-.4f,-.4f,-9,.8f,.8f,1);
        box(root,"ring",64,-1.35f,-1.35f,2,2.7f,2.7f,.6f);box(root,"horizontal_fins",0,-3.5f,-.25f,2,7,.5f,4);box(root,"vertical_fins",0,-.25f,-3.5f,2,.5f,7,4);box(root,"nozzle",96,-1.5f,-1.5f,5,3,3,1.4f);box(root,"fire",64,-.8f,-.8f,6.4f,1.6f,1.6f,2);
        return LayerDefinition.create(mesh,128,128);
    }
    @Override public void render(T entity,float yaw,float partial,PoseStack pose,MultiBufferSource source,int light){
        pose.pushPose();
        if(entity instanceof BlackDogDrone drone){
            float heading=drone.aimYaw(partial),pitch=drone.aimPitch(partial);
            pose.mulPose(Axis.YP.rotationDegrees(180-heading));model.getChild("gimbal").xRot=(float)Math.toRadians(-pitch);
            pose.scale(.95f,.95f,.95f);
        }else {pose.mulPose(Axis.YP.rotationDegrees(180-entity.getYRot()));pose.mulPose(Axis.XP.rotationDegrees(-entity.getXRot()));}
        model.render(pose,source.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);pose.popPose();
        super.render(entity,yaw,partial,pose,source,light);
    }
}
