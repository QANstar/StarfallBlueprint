package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.util.*;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Screen-space hit testing and cursor-anchored zoom over a separate research world space. */
public final class BlackDogResearchCanvas {
    private static final int CARD_W=144,CARD_H=62;
    private static final int[][] POS={{0,0},{230,0},{460,0},{690,0},{460,-300},{690,-300},{460,300},{690,300},{920,300},{690,-150},{920,-150},{1150,-150},{690,150},{920,150},{920,0},{1150,150}};
    private final LingchuanMenu menu;
    private final IntConsumer send;
    private final Font font=Minecraft.getInstance().font;
    private int x,y,w,h,vx,vy,vw,vh,px,pw,selected=1,detailScroll;
    private double zoom=.85,panX,panY;
    private boolean initialized,dragging;
    public BlackDogResearchCanvas(LingchuanMenu menu,IntConsumer send){this.menu=menu;this.send=send;}
    public void bounds(int x,int y,int width,int height){
        boolean changed=w!=width||h!=height;this.x=x;this.y=y;w=width;h=height;pw=Math.min(192,Math.max(150,width/3));
        vx=x+8;vy=y+39;vw=width-pw-24;vh=height-65;px=x+width-pw-8;
        if(!initialized){selected=0;for(int i=0;i<Lingchuan.RESEARCH.size();i++)if(!menu.researched(i)&&Lingchuan.prerequisites(menu.knowledge(),i)){selected=i;break;}focus(selected);initialized=true;}
        else if(changed)focus(selected);
    }
    private boolean in(double mx,double my,int ax,int ay,int aw,int ah){return mx>=ax&&my>=ay&&mx<ax+aw&&my<ay+ah;}
    private boolean viewport(double mx,double my){return in(mx,my,vx,vy,vw,vh);}
    public double zoom(){return zoom;}
    public double worldX(double screenX){return (screenX-vx-panX)/zoom;}
    public double worldY(double screenY){return (screenY-vy-panY)/zoom;}
    public void zoomAt(double mx,double my,double factor){
        double wx=worldX(mx),wy=worldY(my);zoom=Math.clamp(zoom*factor,.2,2.2);panX=mx-vx-wx*zoom;panY=my-vy-wy*zoom;
    }
    public void focus(int node){selected=Math.clamp(node,0,15);panX=vw*.42-(POS[selected][0]+CARD_W*.5)*zoom;panY=vh*.5-(POS[selected][1]+CARD_H*.5)*zoom;detailScroll=0;}
    public void overview(){zoom=Math.clamp(Math.min((vw-32)/1294d,(vh-32)/662d),.2,1);panX=(vw-1294*zoom)/2;panY=(vh-662*zoom)/2+300*zoom;}
    public boolean scroll(double mx,double my,double delta){
        if(viewport(mx,my)){zoomAt(mx,my,Math.pow(1.16,delta));return true;}
        if(in(mx,my,px,vy,pw,vh)){detailScroll=Math.clamp(detailScroll-(int)(delta*20),0,300);return true;}return false;
    }
    private int hitNode(double mx,double my){
        if(!viewport(mx,my))return -1;double wx=worldX(mx),wy=worldY(my);
        for(int i=0;i<POS.length;i++)if(in(wx,wy,POS[i][0],POS[i][1],CARD_W,CARD_H))return i;return -1;
    }
    public boolean click(double mx,double my,int button){
        if(button!=0&&button!=1&&button!=2)return false;
        if(button==0&&in(mx,my,vx+vw-116,y+10,24,20)){zoomAt(vx+vw*.5,vy+vh*.5,1/1.2);return true;}
        if(button==0&&in(mx,my,vx+vw-88,y+10,24,20)){zoomAt(vx+vw*.5,vy+vh*.5,1.2);return true;}
        if(button==0&&in(mx,my,vx+vw-60,y+10,60,20)){overview();return true;}
        if(button==0&&in(mx,my,px+8,vy+vh-57,pw-16,20)){send.accept(90);return true;}
        if(button==0&&in(mx,my,px+8,vy+vh-30,pw-16,22)){
            if(!menu.researched(selected)&&Lingchuan.prerequisites(menu.knowledge(),selected))send.accept(selected);return true;
        }
        if(viewport(mx,my)){
            int hit=hitNode(mx,my);if(hit>=0&&button==0){selected=hit;detailScroll=0;}dragging=true;return true;
        }return in(mx,my,x,y,w,h);
    }
    public boolean drag(double dx,double dy){if(!dragging)return false;panX+=dx;panY+=dy;return true;}
    public boolean release(){boolean before=dragging;dragging=false;return before;}
    public void render(GuiGraphics g,int mx,int my){
        g.fill(x,y,x+w,y+h,0xff101219);g.fill(x+1,y+1,x+w-1,y+35,0xff1c202b);g.fill(x+1,y+34,x+w-1,y+35,0xff773441);
        g.drawString(font,Component.translatable("lc.canvas_title"),x+12,y+10,0xfff5d8de,false);
        g.drawString(font,Component.translatable("lc.canvas_progress",Integer.bitCount(menu.knowledge()),16),x+12,y+23,0xff9fa6b7,false);
        drawButton(g,vx+vw-116,y+10,24,20,Component.literal("−"),true,mx,my);drawButton(g,vx+vw-88,y+10,24,20,Component.literal("+"),true,mx,my);drawButton(g,vx+vw-60,y+10,60,20,Component.translatable("lc.canvas_overview"),true,mx,my);
        g.fill(vx,vy,vx+vw,vy+vh,0xff131721);g.enableScissor(vx,vy,vx+vw,vy+vh);
        double spacing=32*zoom;int grid=0xff202734;
        if(spacing>=8){for(double sx=vx+panX%spacing;sx<vx+vw;sx+=spacing)g.fill((int)sx,vy,(int)sx+1,vy+vh,grid);for(double sy=vy+panY%spacing;sy<vy+vh;sy+=spacing)g.fill(vx,(int)sy,vx+vw,(int)sy+1,grid);}
        g.pose().pushPose();g.pose().translate(vx+panX,vy+panY,0);g.pose().scale((float)zoom,(float)zoom,1);
        for(int i=0;i<POS.length;i++){
            var r=Lingchuan.RESEARCH.get(i);if(r.parent()>=0)edge(g,r.parent(),i,false);
            if(r.tier()>0&&r.tier()!=r.parent())edge(g,r.tier(),i,true);
        }
        int hovered=hitNode(mx,my);for(int i=0;i<POS.length;i++)card(g,i,i==hovered);
        g.pose().popPose();g.disableScissor();
        g.fill(vx,vy+vh-17,vx+vw,vy+vh,0xe812161f);g.drawString(font,Component.literal(Math.round(zoom*100)+"%"),vx+7,vy+vh-12,0xffb9beca,false);
        g.drawString(font,font.plainSubstrByWidth(Component.translatable("lc.canvas_hint").getString(),w-20),x+10,y+h-17,0xff9099ab,false);
        details(g,mx,my);
    }
    private void edge(GuiGraphics g,int from,int to,boolean secondary){
        int ax=POS[from][0]+CARD_W,ay=POS[from][1]+CARD_H/2,bx=POS[to][0],by=POS[to][1]+CARD_H/2;
        int middle=ax+(bx-ax)/2,color=secondary?0xff525d76:menu.researched(from)?0xffa64a5c:0xff414959;
        if(secondary){for(int dx=ax;dx<middle;dx+=9)g.fill(dx,ay,Math.min(dx+4,middle),ay+1,color);for(int dy=Math.min(ay,by);dy<Math.max(ay,by);dy+=9)g.fill(middle,dy,middle+1,Math.min(dy+4,Math.max(ay,by)),color);for(int dx=middle;dx<bx;dx+=9)g.fill(dx,by,Math.min(dx+4,bx),by+1,color);}
        else {line(g,ax,ay,middle,ay,color);line(g,middle,ay,middle,by,color);line(g,middle,by,bx-4,by,color);for(int n=0;n<4;n++)g.fill(bx-5+n,by-3+n,bx-4+n,by+4-n,color);}
    }
    private static void line(GuiGraphics g,int ax,int ay,int bx,int by,int color){g.fill(Math.min(ax,bx),Math.min(ay,by),Math.max(ax,bx)+2,Math.max(ay,by)+2,color);}
    private int stateColor(int i){return menu.researched(i)?0xff75b29b:Lingchuan.prerequisites(menu.knowledge(),i)?0xffee6b7d:0xff586277;}
    private void card(GuiGraphics g,int i,boolean hovered){
        int nx=POS[i][0],ny=POS[i][1],accent=stateColor(i);boolean chosen=i==selected;
        g.fill(nx+4,ny+5,nx+CARD_W+4,ny+CARD_H+5,0x66000000);
        g.fill(nx,ny,nx+CARD_W,ny+CARD_H,chosen?0xff9d4658:hovered?0xff6c4b5b:0xff343e50);
        g.fill(nx+1,ny+1,nx+CARD_W-1,ny+CARD_H-1,chosen?0xff2e2530:0xff202634);
        g.fill(nx+1,ny+1,nx+4,ny+CARD_H-1,accent);g.fill(nx+CARD_W-12,ny,nx+CARD_W,ny+3,accent);
        g.fill(nx+10,ny+13,nx+36,ny+39,0xff121824);
        String icon=switch(i){case 0->"laminar_alloy";case 1->"blackdog_head_basic";case 2->"blackdog_core_compressed";case 3->"blackdog_core_reactor";default->"blackdog_component_"+Lingchuan.ABILITIES.get(i-4);};
        g.renderItem(CatalogueContent.stack(icon,1),nx+15,ny+18);
        var name=Lingchuan.RESEARCH.get(i).name();g.drawString(font,font.plainSubstrByWidth(name.getString(),96),nx+42,ny+15,menu.researched(i)?0xffd1e2dc:0xffe2e2e9,false);
        g.drawString(font,"LC-"+String.format("%02d",i),nx+42,ny+31,0xff929bac,false);
        g.fill(nx+10,ny+46,nx+CARD_W-10,ny+47,0xff394252);
        g.drawString(font,Component.translatable(menu.researched(i)?"lc.canvas_done":Lingchuan.prerequisites(menu.knowledge(),i)?"lc.canvas_available":"lc.canvas_locked"),nx+11,ny+50,accent,false);
    }
    private void details(GuiGraphics g,int mx,int my){
        g.fill(px,vy,px+pw,vy+vh,0xff1d222e);g.fill(px,vy,px+pw,vy+2,stateColor(selected));
        var node=Lingchuan.RESEARCH.get(selected);g.drawString(font,node.name(),px+10,vy+12,0xfff4dbe1,false);
        g.drawString(font,Component.translatable("lc.canvas_node",selected),px+10,vy+27,0xff929bad,false);
        g.enableScissor(px+5,vy+42,px+pw-5,vy+vh-64);int cy=vy+45-detailScroll;
        cy=text(g,Component.translatable("lc.research_description."+node.key()),px+10,cy,pw-20,0xffc0c7d5)+12;
        cy=text(g,Component.translatable("lc.canvas_prerequisites"),px+10,cy,pw-20,0xffdf9da9)+5;
        if(node.parent()<0)cy=text(g,Component.translatable("lc.no_prerequisite"),px+10,cy,pw-20,0xff98b6a9);
        else cy=prerequisite(g,node.parent(),cy);
        if(node.tier()>0&&node.tier()!=node.parent())cy=prerequisite(g,node.tier(),cy+3);
        cy+=12;cy=text(g,Component.translatable("lc.canvas_payment"),px+10,cy,pw-20,0xffdf9da9)+5;
        cy=text(g,Component.translatable(menu.materials()?"lc.canvas_material_cost":"lc.canvas_energy_cost",menu.materials()?node.alloy():node.power(),node.crystal()),px+10,cy,pw-20,0xffd7dbe4)+8;
        text(g,Component.translatable("lc.station_power",menu.power()),px+10,cy,pw-20,0xff8c98ac);g.disableScissor();
        drawButton(g,px+8,vy+vh-57,pw-16,20,Component.translatable(menu.materials()?"lc.canvas_materials":"lc.canvas_energy"),true,mx,my);
        boolean available=!menu.researched(selected)&&Lingchuan.prerequisites(menu.knowledge(),selected);
        drawButton(g,px+8,vy+vh-30,pw-16,22,Component.translatable(menu.researched(selected)?"lc.canvas_done":"lc.canvas_research"),available,mx,my);
    }
    private int prerequisite(GuiGraphics g,int id,int cy){return text(g,Component.literal(menu.researched(id)?"✓ ":"○ ").append(Lingchuan.RESEARCH.get(id).name()),px+10,cy,pw-20,menu.researched(id)?0xff87b6a1:0xffa0909c);}
    private int text(GuiGraphics g,Component component,int tx,int ty,int width,int color){for(var line:font.split(component,width)){g.drawString(font,line,tx,ty,color,false);ty+=11;}return ty;}
    private void drawButton(GuiGraphics g,int bx,int by,int bw,int bh,Component label,boolean active,int mx,int my){
        boolean hover=in(mx,my,bx,by,bw,bh);g.fill(bx,by,bx+bw,by+bh,active?hover?0xffd2677b:0xff884252:0xff363b49);g.fill(bx+1,by+1,bx+bw-1,by+bh-1,active?hover?0xff54313f:0xff322733:0xff252a35);g.drawCenteredString(font,label,bx+bw/2,by+(bh-8)/2,active?0xfff5dde3:0xff737d90);
    }
}
