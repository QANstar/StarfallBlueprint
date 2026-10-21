package cn.starfall.blueprint.client;

import cn.starfall.blueprint.AlloyFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class AlloyFurnaceScreen extends AbstractContainerScreen<AlloyFurnaceMenu> {
    public AlloyFurnaceScreen(AlloyFurnaceMenu menu, Inventory inv, Component title) { super(menu, inv, title); imageWidth=176; imageHeight=202; inventoryLabelY=108; }
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x=leftPos,y=topPos;
        g.fill(x,y,x+176,y+202,0xffc6c6c6); g.fill(x+2,y+2,x+174,y+200,0xffdedede);
        for (var slot:menu.slots) {
            g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff373737);
            g.fill(x+slot.x,y+slot.y,x+slot.x+17,y+slot.y+17,0xff8b8b8b);
        }
        g.drawString(font,Component.translatable("gui.starfall_blueprint.alloy.material"),x+32,y+23,0xff404040,false);
        g.drawString(font,Component.translatable("gui.starfall_blueprint.fuel"),x+49,y+91,0xff404040,false);
        g.fill(x+87,y+50,x+113,y+56,0xff777777);
        int width=menu.data.get(0)*26/Math.max(1,menu.data.get(1));
        g.fill(x+87,y+50,x+87+width,y+56,0xffd29a45);
        if(menu.data.get(3)==1) g.fill(x+53,y+57,x+62,y+66,0xffdb7d30);
        g.drawString(font,Component.translatable("gui.starfall_blueprint.alloy.state."+menu.data.get(3)),x+84,y+80,0xff404040,false);
    }
    public void render(GuiGraphics g, int mx, int my, float delta) { super.render(g,mx,my,delta); renderTooltip(g,mx,my); }
}
