package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestEngineering;
import cn.starfall.blueprint.BestWorkbenchMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Vanilla widgets and solid fills, with no external GUI texture. */
public final class BestWorkbenchScreen extends AbstractContainerScreen<BestWorkbenchMenu> {
    private int page;
    private int selectedMechAction = 18;
    private int selectedArmAction = 21;
    private final List<ActionButton> actionButtons = new ArrayList<>();
    private record ActionButton(int action, Button button) {}

    public BestWorkbenchScreen(BestWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 272;
        titleLabelX = 10;
        titleLabelY = 7;
        inventoryLabelX = 70;
        inventoryLabelY = 176;
    }

    /** Fixture/accessibility entry point: 0 engineering, 1 mobility, 2 diagnostics, 3 mech, 4 tool arms. */
    public void selectPage(int page) {
        if (page < 0 || page > 4) throw new IllegalArgumentException("page must be 0..4");
        this.page = page;
        if (minecraft != null) rebuildWidgets();
    }
    public int page() { return page; }
    /** Select the material table independently of execution, including disabled learned actions. */
    public void selectMechAction(int action) {
        if (action < 17 || (action > 20 && action != 23)) throw new IllegalArgumentException("mech action must be 17..20 or 23");
        selectedMechAction = action;
    }
    public int selectedMechAction() { return selectedMechAction; }
    public void selectArmAction(int action) {
        if (action < 21 || action > 22) throw new IllegalArgumentException("arm action must be 21..22");
        selectedArmAction = action;
    }
    private int materialAction() { return page == 4 ? selectedArmAction : selectedMechAction; }
    private void selectMaterialAction(int action) {
        if (action == 21 || action == 22) selectArmAction(action);
        else selectMechAction(action);
    }
    private static Component text(String key, Object... args) {
        return Component.translatable("ui.starfall_blueprint.best_" + key, args);
    }

    @Override protected void init() {
        super.init();
        actionButtons.clear();
        for (int i = 0; i < 5; i++) {
            final int target = i;
            Button tab = addRenderableWidget(Button.builder(text("tab_" + i), button -> selectPage(target))
                    .bounds(leftPos + 8 + i * 57, topPos + 44, 52, 16).build());
            tab.active = page != i;
        }
        int[] actions = switch(page) {
            case 0 -> new int[]{0, 1, 2, 3, 5, 6, 4, 7};
            case 1 -> new int[]{8, 9, 10, 11, 12};
            case 2 -> new int[]{13, 14, 15, 16};
            case 3 -> new int[]{17, 18, 19, 20, 23};
            default -> new int[]{21, 22};
        };
        for (int i = 0; i < actions.length; i++) {
            int action = actions[i];
            Button button = addRenderableWidget(Button.builder(text("action_" + action), clicked -> {
                if (action >= 17) selectMaterialAction(action);
                if (minecraft != null && minecraft.gameMode != null && menu.preview(action).executable())
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
            }).bounds(leftPos + 8 + (i % 2) * 146, topPos + 63 + (i / 2) * 16, 138, 14).build());
            actionButtons.add(new ActionButton(action, button));
        }
        updateActions();
    }

    private void updateActions() {
        for (ActionButton entry : actionButtons) entry.button().active = menu.preview(entry.action()).executable();
    }
    private boolean hovered(Button button, int mouseX, int mouseY) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }
    private void fitted(GuiGraphics graphics, Component value, int x, int y, int width, int color) {
        // Scale only unusually long translations; keep every knowledge label visible at narrow GUI sizes.
        float scale = Math.min(1f, (float) width / Math.max(1, font.width(value)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, value, 0, 0, color, false);
        graphics.pose().popPose();
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xff233a40);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xffdce5df);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + 19, 0xff344f55);
        graphics.fill(x + 8, y + 173, x + imageWidth - 8, y + 174, 0xffa5b8ac);
        for (var slot : menu.slots) {
            int sx = x + slot.x, sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xff485b55);
            graphics.fill(sx, sy, sx + 16, sy + 16, 0xff96a79c);
            graphics.fill(sx, sy, sx + 16, sy + 1, 0xff71847b);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        fitted(graphics, title, titleLabelX, titleLabelY, 280, 0xffedf4eb);
        fitted(graphics, text("knowledge_research", text("research_" + menu.researchLevel())), 10, 22, 138, 0xff344f43);
        fitted(graphics, text("knowledge_light", text(menu.hasLightMobility() ? "learned" : "unlearned")), 154, 22, 136, 0xff344f43);
        fitted(graphics, text("knowledge_diagnostics", text(menu.hasDiagnostics() ? "learned" : "unlearned")), 10, 33, 138, 0xff344f43);
        fitted(graphics, text("knowledge_mech", text(menu.hasMechOperation() ? "learned" : "unlearned")), 154, 33, 136, 0xff344f43);
        if (page >= 3) {
            BestEngineering.Preview preview = menu.preview(materialAction());
            fitted(graphics, text("materials_for", text("material_action_" + materialAction()), preview.reason()), 10, 117, 280, preview.executable() ? 0xff27653f : 0xff88402f);
            List<BestEngineering.MaterialRequirement> materials = preview.materials();
            for (int i = 0; i < materials.size(); i++) {
                var material = materials.get(i);
                int x = 10 + i % 4 * 71, y = 128 + i / 4 * 18;
                graphics.renderItem(material.item(), x, y);
                fitted(graphics, text("material_count", material.available(), material.required()), x + 18, y + 4, 51,
                        material.available() >= material.required() ? 0xff27653f : 0xffa3332c);
            }
        }
        fitted(graphics, text("hint_" + page), 10, 164, 280, 0xff435b50);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xff344f43, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateActions();
        for (ActionButton entry : actionButtons) {
            if (page >= 3 && hovered(entry.button(), mouseX, mouseY)) selectMaterialAction(entry.action());
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        // Inactive vanilla buttons do not consistently expose tooltips; test bounds directly.
        for (ActionButton entry : actionButtons) {
            if (hovered(entry.button(), mouseX, mouseY)) {
                var preview = menu.preview(entry.action());
                Component tooltip = text("action_" + entry.action()).copy().append("\n").append(preview.reason());
                for (var material : preview.materials())
                    tooltip = tooltip.copy().append("\n").append(text("cost_entry", material.item().getHoverName(), material.available(), material.required()));
                if (preview.materials().isEmpty()) tooltip = tooltip.copy().append("\n").append(text("no_materials"));
                if (entry.action() == 4 || entry.action() == 7 || entry.action() == 10 || entry.action() == 11 || entry.action() == 12)
                    tooltip = tooltip.copy().append("\n").append(text("detail_" + entry.action()));
                graphics.renderTooltip(font, font.split(tooltip, Math.min(280, width - 20)), mouseX, mouseY);
                return;
            }
        }
        if (page >= 3) {
            List<BestEngineering.MaterialRequirement> materials = menu.preview(materialAction()).materials();
            for (int i = 0; i < materials.size(); i++) {
                int x = leftPos + 10 + i % 4 * 71, y = topPos + 128 + i / 4 * 18;
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    graphics.renderTooltip(font, materials.get(i).item(), mouseX, mouseY);
                    return;
                }
            }
        }
    }
}
