package cn.starfall.blueprint.client;

import net.minecraft.client.gui.GuiGraphics;

/** Distinct sixteen-pixel HUD silhouettes, independent of inventory item textures. */
final class BlackDogHudIcons {
    private static void box(GuiGraphics g,int x,int y,int w,int h,int c){g.fill(x,y,x+w,y+h,c);}
    static void draw(GuiGraphics g,String system,int x,int y,int color){
        g.pose().pushPose();g.pose().translate(x,y,0);
        switch(system){
            case "armor" -> {
                box(g,0,2,5,4,color);box(g,11,2,5,4,color);box(g,3,5,10,9,color);box(g,6,2,4,2,color);box(g,6,6,4,5,0xff263848);box(g,7,7,2,3,0xffa8d4de);
            }
            case "laser" -> {
                box(g,0,6,4,5,color);box(g,4,7,9,2,color);box(g,11,3,2,3,color);box(g,11,10,2,3,color);box(g,14,7,2,2,0xfff4e7ed);box(g,7,1,1,3,color);box(g,7,12,1,3,color);
            }
            case "missile", "swarm" -> {
                box(g,6,3,4,9,color);box(g,7,1,2,2,color);box(g,5,4,6,2,color);box(g,3,9,3,5,color);box(g,10,9,3,5,color);box(g,7,13,2,3,0xfff5b25e);box(g,7,6,2,4,0xfff2f1e6);
            }
            case "cannon" -> {
                box(g,0,4,8,3,color);box(g,0,10,8,3,color);box(g,2,7,4,3,color);box(g,8,7,3,3,0xffe3faff);box(g,12,2,4,4,color);box(g,12,11,4,4,color);box(g,13,7,3,2,color);box(g,9,0,1,4,color);box(g,9,12,1,4,color);
            }
            case "turret" -> {
                box(g,5,5,6,6,color);box(g,0,6,4,4,color);box(g,12,6,4,4,color);box(g,3,7,10,2,color);box(g,5,1,2,5,color);box(g,9,1,2,5,color);box(g,6,7,4,2,0xfff0e3ac);box(g,2,12,3,2,color);box(g,11,12,3,2,color);
            }
            case "shield" -> {
                box(g,3,1,10,2,color);box(g,2,3,2,7,color);box(g,12,3,2,7,color);box(g,4,10,2,3,color);box(g,10,10,2,3,color);box(g,6,13,4,2,color);box(g,7,4,2,7,color);box(g,5,6,6,2,color);
            }
            case "cloak" -> {
                box(g,4,3,8,2,color);box(g,2,5,3,2,color);box(g,0,7,3,2,color);box(g,3,10,3,2,color);box(g,6,12,6,2,color);box(g,12,9,3,2,color);box(g,13,6,3,2,color);box(g,7,6,3,4,color);
                for(int i=0;i<6;i++)box(g,2+i*2,13-i*2,2,2,0xfff1dfeb);
            }
            case "flight" -> {
                box(g,7,2,2,11,color);box(g,5,4,6,3,color);box(g,3,7,10,2,color);box(g,1,9,14,2,color);box(g,4,13,3,2,color);box(g,9,13,3,2,color);
            }
        }
        g.pose().popPose();
    }
    private BlackDogHudIcons(){}
}
