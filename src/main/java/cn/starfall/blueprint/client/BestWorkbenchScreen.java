package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestEngineering;
import cn.starfall.blueprint.BestWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Vanilla widgets and solid fills, with no external GUI texture. */
public final class BestWorkbenchScreen extends AbstractContainerScreen<BestWorkbenchMenu> {
    private int page;

    public BestWorkbenchScreen(BestWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 236;
        titleLabelX = 10;
        titleLabelY = 9;
        inventoryLabelX = 70;
        inventoryLabelY = 140;
    }

    @Override protected void init() {
        super.init();
        int[] actions = switch(page){case 0 -> new int[]{0, 1, 2, 3, 5, 6, 4, 7};case 1 -> new int[]{8, 9, 10, 11, 12};default -> new int[]{13, 14, 15, 16};};
        for (int i = 0; i < actions.length; i++) addAction(actions[i], 8 + (i % 2) * 146, 40 + (i / 2) * 22, 138);
        addRenderableWidget(Button.builder(Component.translatable("message.starfall_blueprint.best_page_"+page).append(" >"), button -> {
            page = (page + 1) % 3;
            rebuildWidgets();
        }).bounds(leftPos + 238, topPos + 3, 54, 18)
                .tooltip(Tooltip.create(Component.translatable("message.starfall_blueprint.best_next_page")))
                .build());
    }

    private void addAction(int action, int x, int y, int buttonWidth) {
        addRenderableWidget(Button.builder(BestEngineering.actionLabelComponent(action), button -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
            }
        }).bounds(leftPos + x, topPos + y, buttonWidth, 20)
                // Vanilla tooltips wrap their text and position it within the screen bounds.
                .tooltip(Tooltip.create(BestEngineering.actionCostComponent(action)))
                .build());
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xff233a40);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xffdce5df);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + 22, 0xff344f55);
        graphics.fill(x + 8, y + 137, x + imageWidth - 8, y + 138, 0xffa5b8ac);
        for (var slot : menu.slots) {
            int sx = x + slot.x, sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xff485b55);
            graphics.fill(sx, sy, sx + 16, sy + 16, 0xff96a79c);
            graphics.fill(sx, sy, sx + 16, sy + 1, 0xff71847b);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xffedf4eb, false);
        Component progress = Component.translatable("message.starfall_blueprint.best_diagnostics_progress", menu.researchLevel(),
                Component.translatable("message.starfall_blueprint.best_state_"+(menu.hasLightMobility()?"yes":"no")),
                Component.translatable("message.starfall_blueprint.best_state_"+(menu.hasDiagnostics()?"yes":"no")));
        graphics.drawString(font, progress, 10, 27, 0xff344f43, false);
        Component hint = page == 2 ? Component.translatable("message.starfall_blueprint.best_diagnostics_hint")
                : Component.literal(page == 0 ? "维修/冷却持主手；整备包主手、装备副手" : "装卸/切档持主手骨架；跨步档站立也耗能");
        graphics.drawString(font, hint, 10, 128, 0xff435b50, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xff344f43, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
