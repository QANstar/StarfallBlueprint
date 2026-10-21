package cn.starfall.blueprint.client;

import cn.starfall.blueprint.MiningBagMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class MiningBagScreen extends AbstractContainerScreen<MiningBagMenu>{
    public MiningBagScreen(MiningBagMenu m,Inventory inv,Component title){super(m,inv,title);imageWidth=176;imageHeight=194;inventoryLabelY=101;}
    protected void init(){super.init();addRenderableWidget(Button.builder(Component.literal("自动"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).bounds(leftPos+8,topPos+26,42,20).build());}
    protected void renderBg(GuiGraphics g,float p,int mx,int my){int x=leftPos,y=topPos;g.fill(x,y,x+176,y+194,0xffc6c6c6);g.fill(x+2,y+2,x+174,y+192,0xffdedede);for(var s:menu.slots){g.fill(x+s.x-1,y+s.y-1,x+s.x+17,y+s.y+17,0xff373737);g.fill(x+s.x,y+s.y,x+s.x+16,y+s.y+16,s.index>=45?0xffa5b3a3:0xff8b8b8b);}g.drawString(font,Component.literal("过滤"),x+30,y+87,0xff404040,false);}
    public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);renderTooltip(g,x,y);}
}
