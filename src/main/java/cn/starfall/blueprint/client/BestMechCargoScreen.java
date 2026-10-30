package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechCargoMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Standard vanilla inventory interactions over a live mech cargo container. */
public final class BestMechCargoScreen extends AbstractContainerScreen<BestMechCargoMenu> {
    public BestMechCargoScreen(BestMechCargoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176; imageHeight = menu.inventoryTop() + 82;
        titleLabelX = 8; titleLabelY = 7;
        inventoryLabelX = 8; inventoryLabelY = menu.inventoryTop() - 12;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xff233a40);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xffdce5df);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + 20, 0xff344f55);
        for(var slot : menu.slots) {
            int sx = x + slot.x, sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xff485b55);
            graphics.fill(sx, sy, sx + 16, sy + 16, 0xff96a79c);
        }
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xffedf4eb, false);
        Component hint = Component.translatable("ui.starfall_blueprint.mech_cargo_hint");
        float scale = Math.min(1f, 160f / Math.max(1, font.width(hint)));
        graphics.pose().pushPose();
        graphics.pose().translate(8, menu.inventoryTop() - 24, 0); graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, hint, 0, 0, 0xff435b50, false); graphics.pose().popPose();
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xff344f43, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
