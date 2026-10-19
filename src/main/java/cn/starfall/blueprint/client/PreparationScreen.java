package cn.starfall.blueprint.client;

import cn.starfall.blueprint.PreparationMenu;
import cn.starfall.blueprint.StarfallBlueprint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class PreparationScreen extends AbstractContainerScreen<PreparationMenu> {
    public PreparationScreen(PreparationMenu menu, Inventory inv, Component title) { super(menu, inv, title); imageWidth = 176; imageHeight = 240; inventoryLabelY = 146; }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.blit(StarfallBlueprint.id("textures/gui/preparation_bench.png"), x, y, 0, 0, imageWidth, imageHeight);
        int progress = menu.data.get(0);
        g.fill(x + 53, y + 49, x + 53 + progress * 49 / 200, y + 57, 0xff55d4c4);
        int water = menu.data.get(1);
        g.fill(x + 80, y + 95 - water * 25 / 4000, x + 92, y + 95, 0xff549fcb);
        g.drawString(font, Component.translatable("gui.starfall_blueprint.ore"), x + 17, y + 31, 0xffb9c6cd, false);
        g.drawString(font, Component.translatable("gui.starfall_blueprint.fuel"), x + 17, y + 68, 0xffb9c6cd, false);
        g.drawString(font, Component.translatable("gui.starfall_blueprint.product"), x + 111, y + 31, 0xffb9c6cd, false);
        g.drawString(font, Component.translatable("gui.starfall_blueprint.tail"), x + 143, y + 31, 0xffb9c6cd, false);
        g.drawString(font, Component.literal(water + "mB"), x + 46, y + 100, 0xff8fbed4, false);
        g.drawString(font, Component.literal(menu.energy() + " XH"), x + 9, y + 116, 0xffd7b877, false);
        g.drawString(font, Component.literal((menu.data.get(5) * 200 / 1000) + "kXH / 10s"), x + 91, y + 116, 0xff99b7b5, false);
        g.drawString(font, Component.translatable("gui.starfall_blueprint.state." + menu.data.get(4)), x + 9, y + 131, 0xffb9d8cc, false);
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 9, 9, 0xffe0eceb, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xffb9c6cd, false);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) { super.render(g, mx, my, delta); renderTooltip(g, mx, my); }
}
