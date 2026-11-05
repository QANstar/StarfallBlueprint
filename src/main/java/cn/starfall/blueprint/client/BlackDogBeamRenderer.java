package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Additive volumetric cores, spiral rails and expanding impact rings, independent of particle settings. */
public final class BlackDogBeamRenderer extends EntityRenderer<BlackDogBeam> {
    public BlackDogBeamRenderer(EntityRendererProvider.Context context){super(context);}
    public ResourceLocation getTextureLocation(BlackDogBeam entity){return StarfallBlueprint.id("textures/models/armor/blackdog_exosuit.png");}
    @Override public void render(BlackDogBeam beam,float yaw,float partial,PoseStack pose,MultiBufferSource source,int light){
        var buffer=source.getBuffer(RenderType.lightning());float age=beam.tickCount+partial,alpha=Math.max(0,1-age/beam.lifetime());
        int style=beam.style();Vec3 end=beam.end();int color=style==1?0x70ddff:style==2?0xffce65:style==3?0xe44d76:style==4?0xff8a45:0xff4868;
        if(style==3){
            var mc=net.minecraft.client.Minecraft.getInstance();if(mc.options.getCameraType().isFirstPerson()&&mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(beam.position())<3.24)return;
            Vec3 normal=end.normalize(),right=normal.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<.001)right=new Vec3(1,0,0);Vec3 up=right.cross(normal).normalize();
            for(int i=0;i<2;i++){float t=age-i*3;if(t<0)continue;float fade=Math.max(0,1-t/11);ring(buffer,pose,normal.scale(i*.012),right,up,.10+t*.038,.004,0xe5677d,fade*fade*.24f);}
            return;
        }
        if(style==4){sphere(buffer,pose,Vec3.ZERO,1+age*.07,color,alpha*.65f);return;}
        double radius=style==1?.16:style==2?.035:.045;
        tube(buffer,pose,Vec3.ZERO,end,radius*3.4,color,alpha*.16f,8);
        tube(buffer,pose,Vec3.ZERO,end,radius,color,alpha*.75f,8);
        tube(buffer,pose,Vec3.ZERO,end,radius*.28,0xffffff,alpha,6);
        Vec3 axis=end.normalize(),right=axis.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<.01)right=new Vec3(1,0,0);Vec3 up=right.cross(axis).normalize();
        if(style==1){
            for(double distance=1;distance<end.length();distance+=2.5)ring(buffer,pose,axis.scale(distance),right,up,.45+age*.018,.025,color,alpha*.6f);
            for(int rail=0;rail<2;rail++){
                Vec3 last=null;int steps=(int)(end.length()*4);
                for(int i=0;i<=steps;i++){double t=i/4d,angle=t*2+age*.35+rail*Math.PI;Vec3 point=axis.scale(t).add(right.scale(Math.cos(angle)*.34)).add(up.scale(Math.sin(angle)*.34));if(last!=null)tube(buffer,pose,last,point,.015,0xddf9ff,alpha*.8f,4);last=point;}
            }
            ring(buffer,pose,end,right,up,1.1+age*.08,.06,color,alpha*.8f);
        }else {ring(buffer,pose,Vec3.ZERO,right,up,.18+age*.01,.02,color,alpha);ring(buffer,pose,end,right,up,.2+age*.07,.04,color,alpha);}
    }
    private static void vertex(VertexConsumer b,PoseStack p,Vec3 v,int color,float alpha){b.addVertex(p.last().pose(),(float)v.x,(float)v.y,(float)v.z).setColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha);}
    public static void tube(VertexConsumer b,PoseStack p,Vec3 from,Vec3 to,double radius,int color,float alpha,int sides){
        Vec3 axis=to.subtract(from).normalize();if(axis.lengthSqr()<.001)return;Vec3 u=axis.cross(new Vec3(0,1,0)).normalize();if(u.lengthSqr()<.001)u=new Vec3(1,0,0);Vec3 v=u.cross(axis).normalize();
        for(int i=0;i<sides;i++){double a=i*Math.PI*2/sides,c=(i+1)*Math.PI*2/sides;Vec3 one=u.scale(Math.cos(a)*radius).add(v.scale(Math.sin(a)*radius)),two=u.scale(Math.cos(c)*radius).add(v.scale(Math.sin(c)*radius));vertex(b,p,from.add(one),color,alpha);vertex(b,p,to.add(one),color,alpha);vertex(b,p,to.add(two),color,alpha);vertex(b,p,from.add(two),color,alpha);}
    }
    public static void ring(VertexConsumer b,PoseStack p,Vec3 center,Vec3 right,Vec3 up,double radius,double thickness,int color,float alpha){
        for(int i=0;i<32;i++){double a=i*Math.PI/16,c=(i+1)*Math.PI/16;Vec3 from=center.add(right.scale(Math.cos(a)*radius)).add(up.scale(Math.sin(a)*radius)),to=center.add(right.scale(Math.cos(c)*radius)).add(up.scale(Math.sin(c)*radius));tube(b,p,from,to,thickness,color,alpha,4);}
    }
    public static void arc(VertexConsumer b,PoseStack p,Vec3 center,double radius,double start,double length,int color,float alpha){
        for(int i=0;i<10;i++){double a=start+length*i/10,c=start+length*(i+1)/10;tube(b,p,center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),center.add(Math.cos(c)*radius,0,Math.sin(c)*radius),.003,color,alpha,3);}
    }
    public static void sphere(VertexConsumer b,PoseStack p,Vec3 center,double radius,int color,float alpha){
        for(int i=0;i<7;i++){double angle=(i-3)*Math.PI/8,y=Math.sin(angle)*radius,r=Math.cos(angle)*radius;ring(b,p,center.add(0,y,0),new Vec3(1,0,0),new Vec3(0,0,1),r,.008,color,alpha);}
        for(int i=0;i<6;i++){double a=i*Math.PI/6;ring(b,p,center,new Vec3(Math.cos(a),0,Math.sin(a)),new Vec3(0,1,0),radius,.008,color,alpha);}
    }
}
