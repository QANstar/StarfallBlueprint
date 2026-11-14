package cn.starfall.blueprint.client;
import cn.starfall.blueprint.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class OrbitalStrikeRenderer extends EntityRenderer<OrbitalStrike> {
    public OrbitalStrikeRenderer(EntityRendererProvider.Context c){super(c);}
    public ResourceLocation getTextureLocation(OrbitalStrike s){return StarfallBlueprint.id("textures/entity/hj_peregrine.png");}
    @Override public void render(OrbitalStrike s,float yaw,float partial,PoseStack p,MultiBufferSource buffers,int light){
        var b=buffers.getBuffer(RenderType.lightning());float age=s.age()+partial;int color=s.style()==1?0xffb156:0x80dbff;
        if(s.style()==4){BlackDogBeamRenderer.tube(b,p,new Vec3(0,-.4,0),new Vec3(0,.5,0),.19,0x8eabbf,1,8);BlackDogBeamRenderer.ring(b,p,new Vec3(0,.25,0),new Vec3(1,0,0),new Vec3(0,0,1),.27,.035,0xffb156,1);return;}
        if(s.style()==2){
            Vec3 motion=s.getDeltaMovement();if(motion.lengthSqr()<.01)motion=new Vec3(s.getX()-s.xo,s.getY()-s.yo,s.getZ()-s.zo);if(motion.lengthSqr()<.01)motion=new Vec3(0,0,1);
            Vec3 axis=motion.normalize(),tail=axis.scale(-3.8);
            BlackDogBeamRenderer.tube(b,p,axis.scale(.55),axis.scale(-.65),.17,0xe3f3ff,1,8);
            BlackDogBeamRenderer.tube(b,p,Vec3.ZERO,tail,.3,0xff9b39,.32f,8);
            BlackDogBeamRenderer.tube(b,p,Vec3.ZERO,tail.scale(.8),.12,0xffd47c,1,8);
            BlackDogBeamRenderer.tube(b,p,Vec3.ZERO,tail.scale(.45),.055,0xffffff,1,6);return;
        }
        if(s.style()==3){double h=(s.delay()+age)*.35;p.pushPose();p.translate(0,h,0);BlackDogBeamRenderer.tube(b,p,Vec3.ZERO,new Vec3(0,3,0),.35,0xdde9f4,1,12);BlackDogBeamRenderer.tube(b,p,Vec3.ZERO,new Vec3(0,-2,0),.22,0xffa34f,.8f,12);p.popPose();return;}
        double radius=s.radius();float alpha=age<0?.6f:Math.max(0,1-age/24);
        BlackDogBeamRenderer.ring(b,p,new Vec3(0,.08,0),new Vec3(1,0,0),new Vec3(0,0,1),radius,.025,color,alpha);
        BlackDogBeamRenderer.arc(b,p,new Vec3(0,.13,0),radius*.65,age*.06,Math.PI*1.5,color,alpha);
        BlackDogBeamRenderer.tube(b,p,Vec3.ZERO,new Vec3(0,48,0),age<0?.025:s.style()==0?Math.max(.6,radius*.15):.12,color,alpha*.8f,10);
        if(age>=0){BlackDogBeamRenderer.ring(b,p,new Vec3(0,.15,0),new Vec3(1,0,0),new Vec3(0,0,1),Math.max(.1,age)*radius/16,.08,color,alpha);if(s.style()==0)BlackDogBeamRenderer.tube(b,p,Vec3.ZERO,new Vec3(0,256,0),radius*.045,0xffffff,alpha,8);}
        else if(s.style()==1){double h=Math.max(0,-age)*2;BlackDogBeamRenderer.tube(b,p,new Vec3(0,h,0),new Vec3(0,h+5,0),.17,0xffffff,1,8);}
    }
}
