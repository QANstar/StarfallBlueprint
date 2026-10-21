package cn.starfall.blueprint.client;

import cn.starfall.blueprint.IndustryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class IndustryScreen extends AbstractContainerScreen<IndustryMenu>{
    public IndustryScreen(IndustryMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=176;imageHeight=264;inventoryLabelY=171;}
    protected void init(){
        super.init();String[]labels={"‹","›","停","精","留−","留+","液","滤"};
        String[]tips={"上一配方／已学模板","下一配方／已学模板","切换停机","切换六种物品过滤模式","减少保留量","增加保留量","切换液体白名单","显示九格幽灵过滤器：持物点击设置，空手点击清除"};
        for(int i=0;i<labels.length;i++){int id=i;addRenderableWidget(Button.builder(Component.literal(labels[i]),b->{if(id==7)menu.showFilters=!menu.showFilters;minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}).bounds(leftPos+8+i*20,topPos+20,20,17).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tips[i]))).build());}
        String[]faces={"下","上","北","南","西","东"};for(int i=0;i<6;i++){int id=10+i;addRenderableWidget(Button.builder(Component.literal(faces[i]),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id)).bounds(leftPos+8+i*26,topPos+40,25,17).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("切换"+faces[i]+"面连接；亮色表示连接开启"))).build());}
    }
    protected void renderBg(GuiGraphics g,float partial,int mx,int my){int x=leftPos,y=topPos;g.fill(x,y,x+176,y+264,0xffc6c6c6);g.fill(x+2,y+2,x+174,y+262,0xffdedede);
        for(var slot:menu.slots)if(slot.isActive()){g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff373737);g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,slot.index>=63?0xffa2b2a4:slot.index>=9&&slot.index<=12?0xffaab8a1:slot.index>=14&&slot.index<=17?0xffb8aa85:0xff8b8b8b);}
        for(int i=0;i<6;i++)g.fill(x+9+i*26,y+56,x+31+i*26,y+58,(menu.data.get(13)&1<<i)!=0?0xff329c77:0xff925b52);
        g.drawString(font,Component.literal(menu.energy()+" XH  "+menu.data.get(2)+"/"+menu.data.get(3)),x+8,y+134,0xff30423d,false);
        String fluidNames=menu.kind().equals("bio_processor")?"水/生辉/回水":menu.kind().equals("g05")?"生辉/—/—":menu.kind().equals("pump")||menu.kind().equals("dew")?"出水/—/—":"水/温水/废水";
        g.drawString(font,Component.literal(fluidNames+" "+menu.data.get(9)+"/"+menu.data.get(10)+"/"+menu.data.get(11)),x+8,y+145,0xff30423d,false);
        g.drawString(font,Component.translatable("gui.starfall_blueprint.industry.state."+menu.data.get(4)),x+8,y+156,0xff404040,false);
        String[]modes={"ID","路","质","源","全","污"};g.drawString(font,"留"+menu.data.get(5)+" "+modes[Math.floorMod(menu.data.get(7),6)],x+108,y+156,0xff404040,false);
    }
    protected void renderLabels(GuiGraphics g,int mx,int my){super.renderLabels(g,mx,my);String text=menu.templateLabel(minecraft.level);if(!text.isEmpty())g.drawString(font,font.plainSubstrByWidth(text,90),imageWidth-98,6,0xff30423d,false);}
    public void render(GuiGraphics g,int mx,int my,float delta){super.render(g,mx,my,delta);renderTooltip(g,mx,my);if(hoveredSlot!=null&&!hoveredSlot.hasItem()&&hoveredSlot.index<27){int i=hoveredSlot.index;String label=menu.kind().equals("supply_crate")?"补给箱库存（接受白名单物品）":i<9?"原料／自动合成九宫格":i<13?"成品输出":i==13?"煤／木炭燃料":i<16?"工序附件／夹具（每批扣1寿命）":i==16?"提速模块（同槽择一）":i==17?"扩批模块（同槽择一）":"小型电池充电槽";g.renderTooltip(font,Component.literal(label),mx,my);}}
}
