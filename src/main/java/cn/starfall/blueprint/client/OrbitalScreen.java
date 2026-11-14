package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Explicit charging, targeting and weather actions; satellite quantities and costs stay visible. */
public final class OrbitalScreen extends AbstractContainerScreen<OrbitalMenu> {
    private static final int INK=0xffe2edf5,MUTED=0xff9bb3c9,BLUE=0xff82d9ef,ORANGE=0xffefa650;
    private int kind=-1,shownLaser=-1,shownRod=-1;
    private OrbitButton charging;
    private final Map<Integer,OrbitButton> modes=new HashMap<>();
    private final List<OrbitButton> assembly=new ArrayList<>();
    private final List<OrbitButton> weather=new ArrayList<>();
    public OrbitalScreen(OrbitalMenu m,Inventory i,Component t){super(m,i,t);imageWidth=352;imageHeight=280;inventoryLabelX=96;inventoryLabelY=186;}
    private void action(int id){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    private OrbitButton button(Component text,int x,int y,int w,int action,Component tooltip){var b=new OrbitButton(leftPos+x,topPos+y,w,20,text,who->action(action));if(tooltip!=null)b.setTooltip(Tooltip.create(tooltip));return addRenderableWidget(b);}
    @Override protected void init(){super.init();kind=menu.info[0];shownLaser=menu.info[19];shownRod=menu.info[20];modes.clear();assembly.clear();weather.clear();charging=null;
        if(kind==1){for(int i=0;i<OrbitalMenu.RECIPES.size();i++){var r=OrbitalMenu.RECIPES.get(i);var row=button(Component.translatable("item.starfall_blueprint."+r.output()),12,45+i*16,328,i,cost(r));row.setHeight(15);assembly.add(row);}}
        else if(kind==2){for(int i=0;i<7;i++){var row=button(Haijia.text(i<4?"launch."+OrbitalFleet.TYPES[i]:"launch.upgrade."+OrbitalFleet.TYPES[i-4]),12,45+i*18,328,100+i,Haijia.text(i<4?"ui.launch_cost":"ui.upgrade_cost"));row.setHeight(17);}}
        else {
            charging=button(Haijia.text("ui.charge_enable"),18,91,142,110,Haijia.text("ui.charge_hint"));
            modes.put(1,button(Haijia.text("ui.select_laser"),184,49,150,201,Haijia.text("ui.laser_hint").copy().append("\n").append(Haijia.text("ui.upgrade_level",menu.info[19]))));
            modes.put(2,button(Haijia.text("ui.select_rod"),184,91,150,202,Haijia.text("ui.rod_hint").copy().append("\n").append(Haijia.text("ui.upgrade_level",menu.info[20]))));
            weather.add(button(Haijia.text("weather.clear"),82,145,80,120,Haijia.text("ui.weather_hint")));weather.add(button(Haijia.text("weather.rain"),170,145,80,121,Haijia.text("ui.weather_hint")));weather.add(button(Haijia.text("weather.thunder"),258,145,80,122,Haijia.text("ui.weather_hint")));
        }
    }
    private Component cost(OrbitalMenu.Recipe r){var text=Component.empty();r.inputs().forEach((key,n)->text.append(CatalogueContent.stack(key,n).getHoverName()).append(" ×"+n+"\n"));return text.append(Haijia.text("assembly_cost",r.energy())).append("\n").append(Haijia.text("research."+r.knowledge()));}
    @Override protected void containerTick(){super.containerTick();if(kind!=menu.info[0]||shownLaser!=menu.info[19]||shownRod!=menu.info[20])rebuildWidgets();}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,Haijia.text(kind==1?"assembler":kind==2?"launchpad":kind==4?"terminal":"console"),12,9,INK,false);g.drawString(font,playerInventoryTitle,96,186,MUTED,false);}
    private void line(GuiGraphics g,Component text,int x,int y,int color,int width){g.drawString(font,font.plainSubstrByWidth(text.getString(),width),leftPos+x,topPos+y,color,false);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        g.fill(leftPos-1,topPos-1,leftPos+353,topPos+281,0xff42617d);g.fill(leftPos,topPos,leftPos+352,topPos+280,0xff101e31);g.fill(leftPos,topPos,leftPos+352,topPos+3,ORANGE);g.fill(leftPos+1,topPos+22,leftPos+351,topPos+41,0xff172e46);
        if(kind>=3){
            line(g,Haijia.text("ui.energy",menu.info[9]&65535,menu.info[16]&65535),12,28,BLUE,205);line(g,Haijia.text("ui.satellite_summary",menu.info[1]+menu.info[2]+menu.info[3]+menu.info[4]),228,28,MUTED,112);
            g.fill(leftPos+10,topPos+45,leftPos+168,topPos+137,0xff182c40);g.fill(leftPos+176,topPos+45,leftPos+342,topPos+137,0xff182c40);
            line(g,Haijia.text("ui.charge_title"),18,52,INK,142);line(g,Haijia.text(menu.info[1]==0?"ui.no_charge_sat":menu.info[5]!=0?"ui.charging_on":"ui.charging_off"),18,68,menu.info[1]>0&&menu.info[5]!=0?BLUE:ORANGE,142);
            line(g,Haijia.text("ui.charge_count",menu.info[1],menu.info[21]),18,119,MUTED,142);
            line(g,Haijia.text("ui.laser_status",menu.info[2],menu.info[19],menu.info[11]),184,73,MUTED,150);line(g,Haijia.text("ui.rod_status",menu.info[10],menu.info[20],menu.info[12]),184,115,MUTED,150);
            line(g,Haijia.text(menu.info[4]==0?"ui.no_weather_sat":menu.info[13]>0?"ui.weather_wait":"ui.weather_title",menu.info[13]),12,151,INK,66);line(g,Haijia.text("ui.target_hint"),12,172,MUTED,328);
        }else {line(g,Haijia.text("station_energy",menu.info[14]),12,28,BLUE,328);if(kind==2)line(g,Haijia.text("ui.supply_by_satellite"),12,171,ORANGE,328);}
        for(int row=0;row<4;row++)for(int col=0;col<9;col++){int x=leftPos+96+col*18,y=topPos+(row==3?256:198+row*18);g.fill(x-1,y-1,x+17,y+17,0xff293e53);g.fill(x,y,x+16,y+16,0xff0b1727);}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        if(charging!=null){charging.active=menu.info[1]>0;charging.selected=menu.info[1]>0&&menu.info[5]!=0;charging.setMessage(Haijia.text(menu.info[1]==0?"ui.no_charge_sat":menu.info[5]!=0?"ui.charge_disable":"ui.charge_enable"));}
        for(var entry:modes.entrySet()){entry.getValue().selected=menu.info[15]==entry.getKey();entry.getValue().active=menu.info[entry.getKey()+1]>0;}
        for(var b:weather)b.active=menu.info[4]>0&&menu.info[13]==0;
        super.render(g,mx,my,partial);renderTooltip(g,mx,my);
    }
    private static final class OrbitButton extends Button {
        private boolean selected;
        OrbitButton(int x,int y,int w,int h,Component text,OnPress press){super(x,y,w,h,text,press,DEFAULT_NARRATION);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){int accent=selected?ORANGE:BLUE;int fill=!active?0xff182536:isHoveredOrFocused()?0xff34536c:selected?0xff384232:0xff233d55;
            g.fill(getX(),getY(),getX()+width,getY()+height,fill);g.fill(getX(),getY(),getX()+3,getY()+height,active?accent:0xff4e6071);g.drawCenteredString(Minecraft.getInstance().font,getMessage(),getX()+width/2,getY()+(height-8)/2,active?INK:0xff74899d);
        }
    }
}
