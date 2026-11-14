package cn.starfall.blueprint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Faceted aircraft mesh in sixteenth-block model units, shared by the entity and visual review. */
public final class PeregrineModel {
    private static final int HULL=0,NAVY=1,GLASS=2,BLACK=3,METAL=4,ORANGE=5,GLOW=6,WHITE=7;
    private record Face(Vec3[] points,Vec3 normal,int material) {}
    private static final List<Face> AIRFRAME=new ArrayList<>(),GEAR=new ArrayList<>();
    static {
        // Continuous chine sections replace the former staircase-shaped nose and separate engine boxes.
        body(AIRFRAME,new double[][]{{-46,0,.06,.06},{-38,0,1.7,1.1},{-32,0,2.9,1.8}},NAVY);
        body(AIRFRAME,new double[][]{{-32,0,2.9,1.8},{-23,-.1,4.0,2.5},{-10,0,5.3,3.0},{4,.1,8.0,3.2},{20,.2,7.6,2.8},{33,.1,6.7,2.4}},HULL);
        // Lower centreline keel and the gently descending dorsal spine.
        body(AIRFRAME,new double[][]{{-16,2.1,2,1.0},{8,2.7,3.5,1.0},{29,1.6,2.5,.8}},NAVY);
        body(AIRFRAME,new double[][]{{-7,-3.1,2.1,.7},{0,-3.4,2.4,.9},{18,-2.8,1.9,.4},{27,-2.4,.6,.2}},HULL);
        canopy();
        for(int side:new int[]{-1,1}) {
            // Thin trapezoidal surfaces with genuinely swept leading/trailing edges.
            wing(side,new double[][]{{4,-13},{12,-4},{33,13},{30.5,20},{12,17},{5,11}},-.15,.8,HULL);
            wing(side,new double[][]{{3.2,-24},{8,-12},{12,0},{6,12}},-1.25,.65,HULL);
            wing(side,new double[][]{{28.8,10.7},{33,13},{30.5,20},{27,19.4}},-.24,.16,NAVY);
            wing(side,new double[][]{{26.6,9.2},{28,10.2},{26.1,19.2},{24.7,19}},-.26,.1,ORANGE);
            // Separate, smaller all-moving tailplanes below the vertical fins.
            wing(side,new double[][]{{6,23},{17.5,30},{16.8,36},{7,32.5}},-.3,.65,NAVY);
            // Outward-canted fins: narrow leading edge, swept tip, modest height.
            var fin=new Vec3[]{v(side*5.4,-2.1,18),v(side*8.4,-11.5,28),v(side*8.3,-10.8,32),v(side*5.4,-2.1,35)};
            prism(AIRFRAME,fin,v(side*.55,0,0),NAVY,v(side,0,0));
            var finCap=new Vec3[]{v(side*8.15,-10.8,27.5),v(side*8.4,-11.5,28),v(side*8.3,-10.8,32),v(side*8.05,-10.1,32.2)};
            prism(AIRFRAME,finCap,v(side*.58,0,0),ORANGE,v(side,0,0));
            // Recessed intakes and segmented twin exhausts, integrated into the rear body.
            double x=side*5.1;
            duct(side);
            tube(AIRFRAME,x,.4,30,35.4,2.65,2.5,METAL,false);
            tube(AIRFRAME,x,.4,35.4,37.2,2.5,2.2,NAVY,false);
            disc(AIRFRAME,x,.4,37.28,2.05,BLACK,1);
            tube(AIRFRAME,x,.4,37.3,37.55,1.15,1.05,GLOW,false);
            disc(AIRFRAME,x,.4,37.57,1.02,BLACK,1);
            // Flush forward emitters rather than oversized external gun barrels.
            tube(AIRFRAME,side*3.0,.6,-27.3,-23,.38,.5,METAL,true);
            disc(AIRFRAME,side*3.0,.6,-27.35,.28,GLOW,-1);
            for(int station=0;station<2;station++) {
                double mx=side*(15+station*8),front=station==0?5:10;
                box(AIRFRAME,mx-.35,.65,front+3,.7,1.9,7,METAL);
                tube(AIRFRAME,mx,3.1,front,front+2,.04,.58,NAVY,true);
                tube(AIRFRAME,mx,3.1,front+2,front+11,.58,.58,WHITE,true);
                tube(AIRFRAME,mx,3.1,front+10.6,front+11.7,.6,.35,METAL,true);
                for(int axis=0;axis<2;axis++) {
                    Vec3[] stabilizer=axis==0?new Vec3[]{v(mx,3.1,front+8),v(mx+side*1.65,3.1,front+10),v(mx+side*1.65,3.1,front+11.5),v(mx,3.1,front+11)}:new Vec3[]{v(mx,3.1,front+8),v(mx,4.65,front+10),v(mx,4.65,front+11.5),v(mx,3.1,front+11)};
                    prism(AIRFRAME,stabilizer,axis==0?v(0,.1,0):v(.1,0,0),NAVY,axis==0?v(0,-1,0):v(side,0,0));
                }
            }
            // Two streamlined gravity-bomb bodies under the wing roots.
            tube(AIRFRAME,side*8,4.3,12,15,.06,1.0,NAVY,true);
            tube(AIRFRAME,side*8,4.3,15,22,1.0,1.0,HULL,true);
            tube(AIRFRAME,side*8,4.3,20,21,1.02,1.02,ORANGE,false);
            // Landing-gear geometry is grouped and disappears completely when retracted.
            box(GEAR,side*4.8-.35,2.5,15,.7,4.8,.7,METAL);
            wheel(side*4.8,7.0,15.3,1.45,1.25);
            box(AIRFRAME,side*4.8-1,3.1,12,2,.15,7,NAVY);
        }
        box(GEAR,-.28,2,-26,.56,5.2,.56,METAL);wheel(0,6.9,-25.7,1.15,.9);
        box(AIRFRAME,-.8,2.2,-29,1.6,.15,6,NAVY);
    }
    private static Vec3 v(double x,double y,double z){return new Vec3(x,y,z);}
    private static void canopy(){
        // Faceted teardrop: low windscreen, full crown, tapered rear frame.
        double[][] sections={{-25,-2.35,.9,.08},{-21,-2.8,2.05,2.2},{-15,-3.0,2.55,3.55},{-10,-3.1,2.35,2.85},{-6,-3.0,1.5,.15}};
        for(int i=0;i<sections.length-1;i++){
            var a=glassRing(sections[i]);var b=glassRing(sections[i+1]);
            for(int j=0;j<4;j++)face(AIRFRAME,new Vec3[]{a[j],a[j+1],b[j+1],b[j]},GLASS,v((a[j].x+a[j+1].x)*.5,-2,0));
        }
        // Slender metal canopy rails and the windscreen arch.
        for(int side:new int[]{-1,1})beam(AIRFRAME,v(side*.9,-2.4,-25),v(side*2.5,-3.05,-14),.13,METAL);
        for(int side:new int[]{-1,1})beam(AIRFRAME,v(side*2.5,-3.05,-14),v(side*1.5,-3.05,-6),.13,METAL);
        var arch=glassRing(sections[1]);for(int j=0;j<4;j++)beam(AIRFRAME,arch[j],arch[j+1],.1,METAL);
    }
    private static Vec3[] glassRing(double[] s){double z=s[0],y=s[1],w=s[2],h=s[3];return new Vec3[]{v(-w,y,z),v(-w*.7,y-h*.78,z),v(0,y-h,z),v(w*.7,y-h*.78,z),v(w,y,z)};}
    private static void duct(int side){
        double x=side*5.4;
        Vec3[] front={v(x-1.7,-1.5,-12),v(x+1.7,-1.5,-12),v(x+2.0,2.0,-10),v(x-1.4,2.0,-10)};
        Vec3[] rear={v(x-2.0,-1.9,-1),v(x+2.0,-1.9,-1),v(x+2.0,2.6,-1),v(x-2.0,2.6,-1)};
        for(int j=0;j<4;j++)face(AIRFRAME,new Vec3[]{front[j],front[(j+1)%4],rear[(j+1)%4],rear[j]},j==2?NAVY:HULL,v((j==1?1:j==3?-1:0),j==0?-1:j==2?1:0,0));
        face(AIRFRAME,front,BLACK,v(0,0,-1));
        for(int j=0;j<4;j++)beam(AIRFRAME,front[j],front[(j+1)%4],.16,METAL);
    }
    private static Vec3[] ring(double[] s){double z=s[0],y=s[1],w=s[2],h=s[3];return new Vec3[]{v(-w*.62,y-h,z),v(w*.62,y-h,z),v(w,y-h*.25,z),v(w,y+h*.45,z),v(w*.55,y+h,z),v(-w*.55,y+h,z),v(-w,y+h*.45,z),v(-w,y-h*.25,z)};}
    private static void body(List<Face> out,double[][] sections,int material){
        for(int i=0;i<sections.length-1;i++){var a=ring(sections[i]);var b=ring(sections[i+1]);
            for(int j=0;j<8;j++){var center=a[j].add(a[(j+1)%8]).scale(.5);face(out,new Vec3[]{a[j],a[(j+1)%8],b[(j+1)%8],b[j]},material==HULL&&j>=3&&j<=5?NAVY:material,v(center.x,center.y-(sections[i][1]),0));}}
        cap(out,ring(sections[0]),material,v(0,0,-1));cap(out,ring(sections[sections.length-1]),material,v(0,0,1));
    }
    private static void wing(int side,double[][] points,double y,double thickness,int material){var vertices=new Vec3[points.length];for(int i=0;i<points.length;i++)vertices[i]=v(side*points[i][0],y,points[i][1]);prism(AIRFRAME,vertices,v(0,thickness,0),material,v(0,-1,0));}
    private static void prism(List<Face> out,Vec3[] points,Vec3 offset,int material,Vec3 normal){
        Vec3[] back=new Vec3[points.length];Vec3 center=Vec3.ZERO;for(int i=0;i<points.length;i++){back[i]=points[i].add(offset);center=center.add(points[i]);}center=center.scale(1d/points.length);
        cap(out,points,material,normal);cap(out,back,material,normal.scale(-1));
        for(int i=0;i<points.length;i++){int j=(i+1)%points.length;face(out,new Vec3[]{points[i],points[j],back[j],back[i]},material,points[i].add(points[j]).scale(.5).subtract(center));}
    }
    private static void cap(List<Face> out,Vec3[] polygon,int material,Vec3 normal){for(int i=1;i<polygon.length-1;i++)face(out,new Vec3[]{polygon[0],polygon[i],polygon[i+1],polygon[i+1]},material,normal);}
    private static void box(List<Face> out,double x,double y,double z,double w,double h,double d,int material){prism(out,new Vec3[]{v(x,y,z),v(x+w,y,z),v(x+w,y,z+d),v(x,y,z+d)},v(0,h,0),material,v(0,-1,0));}
    private static void beam(List<Face> out,Vec3 a,Vec3 b,double radius,int material){var axis=b.subtract(a).normalize();var u=axis.cross(v(0,1,0)).normalize();if(u.lengthSqr()<.01)u=v(1,0,0);var right=u.scale(radius);var up=axis.cross(u).scale(radius);var aRing=new Vec3[]{a.add(right).add(up),a.subtract(right).add(up),a.subtract(right).subtract(up),a.add(right).subtract(up)};prism(out,aRing,b.subtract(a),material,axis.scale(-1));}
    private static void tube(List<Face> out,double x,double y,double z,double end,double r1,double r2,int material,boolean caps){
        Vec3[] a=new Vec3[8],b=new Vec3[8];for(int i=0;i<8;i++){double angle=(i+.5)*Math.PI/4;a[i]=v(x+Math.cos(angle)*r1,y+Math.sin(angle)*r1,z);b[i]=v(x+Math.cos(angle)*r2,y+Math.sin(angle)*r2,end);}
        for(int i=0;i<8;i++){int j=(i+1)%8;face(out,new Vec3[]{a[i],a[j],b[j],b[i]},material,a[i].add(a[j]).scale(.5).subtract(v(x,y,z)));}if(caps){cap(out,a,material,v(0,0,-1));cap(out,b,material,v(0,0,1));}
    }
    private static void disc(List<Face> out,double x,double y,double z,double radius,int material,int normal){var vertices=new Vec3[8];for(int i=0;i<8;i++){double a=(i+.5)*Math.PI/4;vertices[i]=v(x+Math.cos(a)*radius,y+Math.sin(a)*radius,z);}cap(out,vertices,material,v(0,0,normal));}
    private static void wheel(double x,double y,double z,double radius,double width){
        var points=new Vec3[10];for(int i=0;i<10;i++){double a=i*Math.PI/5;points[i]=v(x-width/2,y+Math.cos(a)*radius,z+Math.sin(a)*radius);}prism(GEAR,points,v(width,0,0),BLACK,v(-1,0,0));
        var hub=new Vec3[10];for(int i=0;i<10;i++){double a=i*Math.PI/5;hub[i]=v(x-width*.51,y+Math.cos(a)*radius*.45,z+Math.sin(a)*radius*.45);}prism(GEAR,hub,v(width*1.02,0,0),METAL,v(-1,0,0));
    }
    private static void face(List<Face> out,Vec3[] p,int material,Vec3 expected){
        var normal=p[1].subtract(p[0]).cross(p[2].subtract(p[0])).normalize();if(normal.lengthSqr()<.001)return;
        if(normal.dot(expected)<0){p=new Vec3[]{p[3],p[2],p[1],p[0]};normal=normal.scale(-1);}out.add(new Face(p,normal,material));
    }
    public static void render(PoseStack pose,VertexConsumer buffer,int light,boolean gear){renderFaces(AIRFRAME,pose,buffer,light);if(gear)renderFaces(GEAR,pose,buffer,light);}
    private static void renderFaces(List<Face> faces,PoseStack pose,VertexConsumer b,int light){
        for(var face:faces){var n=face.normal();int tileX=face.material()%4,tileY=face.material()/4;
            // Project the material onto its dominant plane; triangulation never introduces UV seams.
            for(var p:face.points()){
                double u,v;if(Math.abs(n.y)>.55){u=p.x;v=p.z;}else if(Math.abs(n.x)>.55){u=p.z;v=p.y;}else{u=p.x;v=p.y;}
                float tu=(float)(tileX*64+3+(u+64)*.31)/256f,tv=(float)(tileY*64+3+(v+64)*.31)/256f;
                b.addVertex(pose.last().pose(),(float)p.x/16,(float)p.y/16,(float)p.z/16).setColor(-1).setUv(tu,tv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),(float)n.x,(float)n.y,(float)n.z);
            }
        }
    }
    private PeregrineModel(){}
}
