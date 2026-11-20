package cn.starfall.blueprint.client;

import static cn.starfall.blueprint.client.BestMechGeometry.*;
import static cn.starfall.blueprint.client.BestMechGeometry.Feature.*;
import static cn.starfall.blueprint.client.BestMechGeometry.Material.*;
import static cn.starfall.blueprint.client.BestMechGeometry.Motion.*;

/** Fast interceptor with a circular turbine housing and swept shoulder vanes. */
final class TempestCombat {
    static Shape create() {
        Shape hull = new Shape(null, STATIC, 0, 0, 0, 0, 0);
        hull.box(GRAPHITE,-9,-32,-4,18,11,10).box(NAVY,-6,-23,-4,12,13,9)
                .box(STEEL,-7,-11,-3,14,5,8).box(BLACK,-3,-37,-2,6,6,5)
                .box(NAVY,-3.5f,-41.5f,-4,7,6,7).box(BLUE,-3,-42,-4.5f,6,2.2f,6)
                .box(BLACK,-3.1f,-39.8f,-4.7f,6.2f,2.5f,2).box(AMBER_LAMP,-2.6f,-39.5f,-5,5.2f,.9f,1)
                .box(STEEL,-1.6f,-38.3f,-4.8f,3.2f,3,2);
        for(int row=0;row<3;row++) hull.box(row==0?STEEL:BLUE,-5+row*.5f,-21+row*3,-4.8f,10-row,1.8f,2);
        Shape engine=hull.child(null, STATIC,0,-27,-6,0,0);
        for(int i=0;i<8;i++){
            Shape segment=engine.child(null,STATIC,0,0,0,0,i*(float)Math.PI/4);
            segment.box(STEEL,-2,-5.6f,-1.4f,4,2,3).box(GOLD,-1.6f,-4.4f,-1.6f,3.2f,1.5f,2)
                    .box(AMBER_LAMP,-1,-3.6f,-1.8f,2,1.1f,1.5f);
        }
        engine.box(BLACK,-2.45f,-2.45f,-1.5f,4.9f,4.9f,2).box(AMBER_LAMP,-1.35f,-1.35f,-1.9f,2.7f,2.7f,1);
        for(int s:new int[]{-1,1}){
            Shape collar=hull.child(null,STATIC,s*7,-29,-3,0,-s*.42f);
            collar.box(NAVY,-4.7f,-4,-3.3f,9.4f,8,5).box(BLUE,-4.6f,-4.5f,-3.6f,9.2f,3,5)
                    .box(SILVER,-3.8f,-4.6f,-3.75f,7.6f,.9f,2);
            Shape vane=hull.child(null,STATIC,s*8,-28,4,-.70f,s*.43f);
            vane.box(NAVY,-2.2f,-13,-1,4.4f,16,4).box(BLUE,-1.5f,-16,-.8f,3,11,2.5f)
                    .box(SILVER,-.7f,-17,.2f,1.4f,5,1.4f);
            Shape arm=hull.child(null,s<0?RIGHT_ARM:LEFT_ARM,s*13,-29,0,-.13f,-s*.12f);
            arm.box(BLACK,-3.1f,-2,-3.2f,6.2f,18,6.4f).box(BLUE,-3.8f,3,-3.7f,7.6f,6.8f,7.4f)
                    .box(STEEL,-3,10,-3.5f,6,4,7).box(NAVY,-4.2f,14,-5,8.4f,9,9)
                    .box(BLUE,-3.7f,14,-5.7f,7.4f,6.6f,3).box(GRAPHITE,-3.3f,22,-4,6.6f,3.5f,6.5f);
            Shape shield=arm.child(null,STATIC,0,-1,0,.06f,s*.36f);
            shield.box(NAVY,-5.4f,-3,-4.8f,10.8f,6,10).box(BLUE,-5.2f,-4.3f,-5,10.4f,3.2f,10.3f)
                    .box(SILVER,-4.4f,-4.5f,-5.1f,8.8f,.9f,3).box(GOLD,-2,-4.6f,-5.4f,2,3,2);
            shield.child(ARMOR).box(STEEL,-5.8f,-5.2f,-5.6f,11.6f,2,11.2f);
            if(s>0) weapons(arm.child(null,STATIC,0,4,-.8f,0,0),NAVY,AMBER_LAMP);
            Shape leg=hull.child(WALK,leg(s),s*6,-8,0,0,0);
            leg.box(GRAPHITE,-3.1f,0,-3,6.2f,28,7).box(BLUE,-3.7f,1,-4,7.4f,9,8)
                    .box(STEEL,-2.9f,8,-4.5f,5.8f,3.5f,3).box(NAVY,-3.7f,11,-4.7f,7.4f,5,8.8f);
            Shape shin=leg.child(null,STATIC,0,16,0,-.14f,0);
            shin.box(NAVY,-3.4f,0,-4.1f,6.8f,12,7.8f).box(BLUE,-3,1,-4.7f,6,8.5f,3)
                    .box(GOLD,-1,1.4f,-5,2,5,1);
            leg.box(BLACK,-4,29,-7.4f,8,3,12).box(BLUE,-3.8f,27,-6,7.6f,3.6f,9.8f);
            hull.child(SUSTAINED_FIRE).box(GRAPHITE,s*5-2,-28,6,4,14,4).box(GOLD,s*5-1.5f,-27,9.5f,3,8,1);
        }
        hull.child(FIRE_CONTROL).box(GRAPHITE,8,-36,-1,4,5,5).box(RED_LAMP,8.7f,-35,-1.5f,2.6f,2.2f,1);
        hull.child(REINFORCED).box(STEEL,-5.5f,-19,-5.6f,11,3,2);
        rearGear(hull,0,-21,7,BLUE); wirelessReceiver(hull,-6,-30,5,BLUE);
        runningGear(hull,6,-7,0,NAVY,BLUE);
        return hull;
    }
}
