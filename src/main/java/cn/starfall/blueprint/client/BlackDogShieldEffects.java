package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BlackDogBeam;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/** Soft third-person arcs and a small, peripheral first-person hit ripple. No full-screen flash. */
public final class BlackDogShieldEffects {
    // Standard alpha blending preserves the rose colour without additive whitening or bloom.
    public static final net.minecraft.client.renderer.RenderType RENDER_TYPE=net.minecraft.client.renderer.RenderType.debugStructureQuads();
    private static final int COLOR=0xd97a8b;
    public static void idle(VertexConsumer buffer,PoseStack pose,float time){
        double drift=Math.sin(time*.035)*.035,rotation=time*.008;
        float opacity=.27f+(float)Math.sin(time*.035)*.025f;
        for(int i=0;i<4;i++)BlackDogBeamRenderer.arc(buffer,pose,new Vec3(0,-.18+drift,0),.72,rotation+i*Math.PI/2,1.03,COLOR,opacity);
        for(int i=0;i<2;i++)BlackDogBeamRenderer.arc(buffer,pose,new Vec3(0,.44-drift,0),.66,-rotation+i*Math.PI,.92,COLOR,opacity*.72f);
    }
    public static void impact(VertexConsumer buffer,PoseStack pose,Vec3 normal,float age){
        normal=normal.normalize();Vec3 right=normal.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<.001)right=new Vec3(1,0,0);Vec3 up=right.cross(normal).normalize();
        for(int i=0;i<2;i++){float t=age-i*4;if(t<0)continue;float fade=Math.max(0,1-t/15);BlackDogBeamRenderer.ring(buffer,pose,normal.scale(i*.015),right,up,.16+t*.045,.008,COLOR,fade*fade*.44f);}
        float fade=Math.max(0,1-age/18);double radius=.18+age*.047;
        for(int i=0;i<6;i++){double angle=i*Math.PI/3+age*.018;Vec3 point=right.scale(Math.cos(angle)*radius).add(up.scale(Math.sin(angle)*radius));BlackDogBeamRenderer.tube(buffer,pose,point,point.add(up.scale(.024)),.009,COLOR,fade*.42f,4);}
    }
    public static void firstPerson(GuiGraphics g,float partial){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!mc.options.getCameraType().isFirstPerson()||mc.options.hideGui)return;
        // Coalesce rapid hits to the newest pulse, so continuous fire cannot cover the view in rings.
        BlackDogBeam newest=null;for(var entity:mc.level.entitiesForRendering())if(entity instanceof BlackDogBeam beam&&beam.style()==3&&beam.shieldOwner()==mc.player.getId()&&(newest==null||beam.tickCount<newest.tickCount))newest=beam;
        if(newest==null)return;float age=newest.tickCount+partial;if(age>=18)return;
        float fade=(float)(Math.sin(Math.min(1,age/3)*Math.PI/2)*Math.pow(Math.max(0,1-age/18),1.3));
        Vec3 normal=newest.end().normalize(),right=mc.player.getLookAngle().cross(new Vec3(0,1,0)).normalize();double side=normal.dot(right);
        int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight();
        double horizontal=Math.abs(side)<.3?.24:Math.clamp(side,-1,1)*.32;
        int cx=(int)(width*(.5+horizontal)),cy=(int)(height*(normal.y>.4?.27:normal.y<-.4?.75:.6));
        for(int wave=0;wave<2;wave++){
            float t=age-wave*4;if(t<0)continue;double radius=12+t*.75;
            for(int segment=0;segment<48;segment++){
                if(segment%8>=6)continue;double angle=segment*Math.PI/24;
                int x=cx+(int)(Math.cos(angle)*radius),y=cy+(int)(Math.sin(angle)*radius*.72);
                g.fill(x,y,x+1,y+1,tint(fade*(wave==0?.5f:.32f)));
            }
        }
        for(int i=0;i<6;i++){double angle=i*Math.PI/3+.2,radius=17+age*.8;int x=cx+(int)(Math.cos(angle)*radius),y=cy+(int)(Math.sin(angle)*radius*.72);g.fill(x,y,x+2,y+2,tint(fade*.58f));}
    }
    private static int tint(float alpha){return Math.clamp((int)(alpha*255),0,255)<<24|COLOR;}
    private BlackDogShieldEffects(){}
}
