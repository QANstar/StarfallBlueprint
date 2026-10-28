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
        addAction(0, 8, 41, 138);
        addAction(1, 154, 41, 138);
        addAction(2, 8, 64, 138);
        addAction(3, 154, 64, 138);
        addAction(4, 8, 87, 284);
    }

    private void addAction(int action, int x, int y, int buttonWidth) {
        addRenderableWidget(Button.builder(Component.literal(BestEngineering.actionLabel(action)), button -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
            }
        }).bounds(leftPos + x, topPos + y, buttonWidth, 20)
                // Vanilla tooltips wrap their text and position it within the screen bounds.
                .tooltip(Tooltip.create(Component.literal("消耗：" + BestEngineering.actionCost(action))))
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
        String progress = switch (menu.researchLevel()) {
            case 0 -> "基础未学习";
            case 1 -> "基础已学习 · 钻切未学习";
            default -> "基础、钻切均已学习";
        };
        graphics.drawString(font, "个人进度：" + progress, 10, 27, 0xff344f43, false);
        graphics.drawString(font, "装备脱下持主手可维修；", 10, 114, 0xff435b50, false);
        graphics.drawString(font, "主手潜行右键从背包能匣补能", 10, 126, 0xff435b50, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xff344f43, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
