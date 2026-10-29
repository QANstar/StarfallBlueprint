package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Inventory workstations and a full-window, independently navigable research canvas. */
public final class LingchuanScreen extends AbstractContainerScreen<LingchuanMenu> {
    private int page;
    private String shownKind="";
    private boolean shownComponents;
    private BlackDogResearchCanvas canvas;
    public LingchuanScreen(LingchuanMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);imageWidth=360;imageHeight=240;inventoryLabelX=98;inventoryLabelY=148;titleLabelX=8;titleLabelY=8;
    }
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    private Button action(Component label,Component tooltip,int action,int x,int y,int width){
        var b=Button.builder(label,button->send(action)).bounds(leftPos+x,topPos+y,width,18).tooltip(Tooltip.create(tooltip)).build();addRenderableWidget(b);return b;
    }
    @Override protected void init(){
        boolean research=menu.kind().equals("lc_research_station");imageWidth=research?width-16:360;imageHeight=research?height-16:240;
        super.init();shownKind=menu.kind();shownComponents=menu.componentView();
        switch(shownKind){
            case "lc_research_station"->{
                if(canvas==null)canvas=new BlackDogResearchCanvas(menu,this::send);canvas.bounds(leftPos,topPos,imageWidth,imageHeight);
            }
            case "lc_parts_fabricator"->{
                for(int cell=0;cell<8;cell++){
                    int index=page*8+cell;if(index>=Lingchuan.MODULES.size())break;var m=Lingchuan.MODULES.get(index);
                    var b=action(m.name(),Component.translatable("lc.manufacture_cost",m.alloy(),m.tier(),m.tier(),m.power()).append("\n").append(Component.translatable("lc.ability."+(m.ability().isEmpty()?"passive":m.ability()))),100+index,8+(cell%2)*172,40+(cell/2)*22,164);
                    b.active=menu.researched(m.research());
                }
                int pages=(Lingchuan.MODULES.size()+7)/8;
                addRenderableWidget(Button.builder(Component.translatable("lc.page",page+1,pages),b->{page=(page+1)%pages;rebuildWidgets();}).bounds(leftPos+250,topPos+22,102,16).build());
                action(Component.translatable("item.starfall_blueprint.blackdog_fire_control"),Component.translatable("lc.fire_control_cost"),180,8,22,180);
            }
            case "lc_alloy_forge"->{
                action(Component.translatable("lc.forge_crystal"),Component.translatable("lc.forge_crystal_cost"),40,16,48,328);
                action(Component.translatable("lc.forge_alloy"),Component.translatable("lc.forge_alloy_cost"),41,16,76,328);
            }
            case "lc_armor_assembler"->{
                action(Component.translatable(menu.componentView()?"lc.assembly_tab_base":"lc.assembly_tab_components"),Component.translatable("lc.component_tab_hint"),91,8,38,120);
                action(Component.translatable("lc.assemble"),Component.translatable("lc.assemble_hint"),50,136,38,105);
                action(Component.translatable("lc.refit"),Component.translatable("lc.refit_hint"),51,246,38,106);
            }
            default->{}
        }
    }
    @Override protected void containerTick(){
        super.containerTick();if(!menu.kind().equals(shownKind)||shownComponents!=menu.componentView()){rebuildWidgets();return;}
        if(shownKind.equals("lc_parts_fabricator")){
            // Research data can arrive after the initial screen construction.
            int cell=0;for(var child:children())if(child instanceof Button b&&b.getY()>=topPos+40&&b.getY()<topPos+128){int index=page*8+cell++;if(index<Lingchuan.MODULES.size())b.active=menu.researched(Lingchuan.MODULES.get(index).research());}
        }
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        if(menu.kind().equals("lc_research_station")&&canvas!=null){canvas.render(g,mouseX,mouseY);return;}
        int x=leftPos,y=topPos;g.fill(x,y,x+imageWidth,y+imageHeight,0xff0e1016);g.fill(x+2,y+2,x+imageWidth-2,y+imageHeight-2,0xff252733);
        g.fill(x+2,y+2,x+imageWidth-2,y+20,0xff722934);
        if(menu.kind().equals("lc_armor_assembler")&&menu.componentView())for(int i=0;i<12;i++){
            int cx=x+190+i%4*38,cy=y+68+i/4*19;boolean installed=BlackDogArmor.has(menu.watch(),Lingchuan.ABILITIES.get(i));
            g.fill(cx-1,cy-1,cx+32,cy+17,installed?0xff9e485b:0xff373c4b);g.fill(cx,cy,cx+31,cy+16,0xff191c27);
            if(installed){g.renderItem(CatalogueContent.stack("blackdog_component_"+Lingchuan.ABILITIES.get(i),1),cx,cy);g.drawString(font,"×",cx+22,cy+4,0xffe69aa9,false);}
        }
        for(var slot:menu.slots){if(!slot.isActive())continue;int sx=x+slot.x,sy=y+slot.y;g.fill(sx-1,sy-1,sx+17,sy+17,0xff6d4650);g.fill(sx,sy,sx+16,sy+16,0xff151720);}
    }
    private static void line(GuiGraphics g,int x1,int y1,int x2,int y2,int color){g.fill(Math.min(x1,x2),Math.min(y1,y2),Math.max(x1,x2)+1,Math.max(y1,y2)+1,color);}
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        if(menu.kind().equals("lc_research_station"))return;
        g.drawString(font,title,8,8,0xfff4dce0,false);
        if(menu.kind().equals("lc_parts_fabricator"))g.drawString(font,menu.power()+" XH",220,8,0xffd4bbc1,false);
        else g.drawString(font,Component.translatable("lc.station_power",menu.power()),8,24,0xffd4bbc1,false);
        if(menu.kind().equals("lc_research_station"))g.drawString(font,Component.translatable(menu.materials()?"lc.payment_materials":"lc.payment_energy"),8,131,0xffe6a4ab,false);
        else if(menu.kind().equals("lc_armor_assembler")){
            if(menu.componentView()){
                g.drawString(font,Component.translatable("lc.component_inputs"),8,65,0xffe0cbd0,false);g.drawString(font,Component.translatable("lc.component_installed"),190,58,0xffe0cbd0,false);
            }else {g.drawString(font,Component.translatable("lc.assembler_instruction"),12,62,0xffe0cbd0,false);g.drawString(font,Component.translatable("lc.budget_hint"),12,80,0xffe0cbd0,false);}
        }else if(menu.kind().equals("lc_armor_charger")){
            g.drawString(font,Component.translatable("lc.charger_instruction"),12,52,0xffe0cbd0,false);
            g.drawString(font,Component.translatable("lc.charger_rate"),12,74,0xffe0cbd0,false);
        }
        if(menu.kind().equals("lc_armor_assembler")&&!menu.componentView())for(int i=0;i<8;i++)g.drawString(font,Component.translatable("lc.short_part."+Lingchuan.PARTS.get(i)),8+i*22,120,0xffdca4ad,false);
        if(menu.slots.get(8).isActive())g.drawString(font,Component.translatable("lc.watch_slot"),274,120,0xffdca4ad,false);
        if(menu.slots.get(9).isActive())g.drawString(font,Component.translatable("lc.output_slot"),326,120,0xffdca4ad,false);
        g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xffd8bcc3,false);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);
        int ability=installedAt(mouseX,mouseY);if(ability>=0&&BlackDogArmor.has(menu.watch(),Lingchuan.ABILITIES.get(ability)))g.renderTooltip(font,Component.translatable("lc.component_remove_hint",CatalogueContent.stack("blackdog_component_"+Lingchuan.ABILITIES.get(ability),1).getHoverName()),mouseX,mouseY);
    }
    private int installedAt(double mouseX,double mouseY){if(!menu.kind().equals("lc_armor_assembler")||!menu.componentView())return -1;for(int i=0;i<12;i++){int x=leftPos+190+i%4*38,y=topPos+68+i/4*19;if(mouseX>=x&&mouseX<x+32&&mouseY>=y&&mouseY<y+17)return i;}return -1;}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(canvas!=null&&menu.kind().equals("lc_research_station"))return canvas.click(x,y,button);
        int component=installedAt(x,y);if(button==0&&component>=0&&BlackDogArmor.has(menu.watch(),Lingchuan.ABILITIES.get(component))){send(200+component);return true;}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return canvas!=null&&menu.kind().equals("lc_research_station")?canvas.drag(dx,dy):super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){return canvas!=null&&menu.kind().equals("lc_research_station")?canvas.release():super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return canvas!=null&&menu.kind().equals("lc_research_station")?canvas.scroll(x,y,vertical):super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==82&&canvas!=null&&menu.kind().equals("lc_research_station")){canvas.focus(0);return true;}return super.keyPressed(key,scan,modifiers);}
    public BlackDogResearchCanvas researchCanvas(){return canvas;}
}
