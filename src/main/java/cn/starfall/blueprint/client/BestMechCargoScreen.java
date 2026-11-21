package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechCargoMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Standard vanilla inventory interactions over a live mech cargo container. */
public final class BestMechCargoScreen extends AbstractContainerScreen<BestMechCargoMenu> {
    public BestMechCargoScreen(BestMechCargoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176; imageHeight = menu.inventoryTop() + 106;
        titleLabelX = 8; titleLabelY = 7;
        inventoryLabelX = 8; inventoryLabelY = menu.inventoryTop() - 12;
    }
    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatableWithFallback("ui.starfall_blueprint.mech_cargo_transfer", "To Inventory"), button -> {
            if(minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BestMechCargoMenu.TRANSFER_TO_INVENTORY);
        }).bounds(leftPos + 8, topPos + menu.inventoryTop() + 80, 100, 20).build());
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        BestHangarTerminal.frame(graphics, font, x, y, imageWidth, imageHeight, "BAY / " + menu.cargoSlots());
        graphics.fill(x+4,y+menu.inventoryTop()-27,x+imageWidth-4,y+menu.inventoryTop()-14,BestHangarTerminal.PANEL);
        for(var slot : menu.slots) {
            int sx = x + slot.x, sy = y + slot.y;
            BestHangarTerminal.well(graphics,sx,sy);
        }
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xffedf4eb, false);
        Component hint = Component.translatable("ui.starfall_blueprint.mech_cargo_hint");
        float scale = Math.min(1f, 160f / Math.max(1, font.width(hint)));
        graphics.pose().pushPose();
        graphics.pose().translate(8, menu.inventoryTop() - 24, 0); graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, hint, 0, 0, 0xffbed4d9, false); graphics.pose().popPose();
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xff85e0d1, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
