package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestEngineering;
import cn.starfall.blueprint.BestMechAssembly;
import cn.starfall.blueprint.BestWorkbenchMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Vanilla widgets and solid fills, organized into 4 readable hangar tabs while preserving page indices 0..9. */
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

    /** Fixture/accessibility entry point: 0..2 light engineering, 3 chassis & fusion, 4..6 parts, 7..9 upgrades. */
    public void selectPage(int page) {
        if (page < 0 || page > 9) throw new IllegalArgumentException("page must be 0..9");
        this.page = page;
        if (minecraft != null) rebuildWidgets();
    }
    public int page() { return page; }
    /** Select the material table independently of execution, including disabled learned actions. */
    public void selectMechAction(int action) {
        if (!BestMechAssembly.handles(action) && (action < 17 || (action > 20 && action != 23 && action != 26))) throw new IllegalArgumentException("mech action must be 17..20, 23 or 26");
        selectedMechAction = action;
    }
    public int selectedMechAction() { return selectedMechAction; }
    public void selectArmAction(int action) {
        if ((action < 21 || action > 22) && action != 27 && action != 28 && action != 24 && action != 25) {
            throw new IllegalArgumentException("arm/part action must be 21..22, 24..25, 27 or 28");
        }
        selectedArmAction = action;
    }
    private int materialAction() { return page == 6 ? 25 : page == 5 ? 24 : page == 4 ? selectedArmAction : selectedMechAction; }
    private void selectMaterialAction(int action) {
        if (action == 24 || action == 25) { selectedArmAction = action; return; }
        if (BestMechAssembly.handles(action)) { selectedMechAction = action; return; }
        if (action == 21 || action == 22 || action == 27 || action == 28) selectArmAction(action);
        else selectMechAction(action);
    }
    private static Component text(String key, Object... args) {
        return Component.translatable("ui.starfall_blueprint.best_" + key, args);
    }

    @Override protected void init() {
        super.init();
        actionButtons.clear();
        String[] groupLabels = {"轻型工务", "机体与融合", "配装部件", "成长组件"};
        int[] groupDefaults = {0, 3, 4, 7};
        int activeGroup = page <= 2 ? 0 : page == 3 ? 1 : page <= 6 ? 2 : 3;
        for (int g = 0; g < 4; g++) {
            final int target = groupDefaults[g];
            Button tab = addRenderableWidget(Button.builder(Component.literal(groupLabels[g]), button -> selectPage(target))
                    .bounds(leftPos + 8 + g * 72, topPos + 43, 69, 15).build());
            tab.active = activeGroup != g;
        }
        addRenderableWidget(Button.builder(ProgressionInventoryTab.text("open"), button -> ProgressionInventoryTab.open())
                .bounds(leftPos + 8, topPos + 22, 284, 17).build());
        int gridTop = topPos + 61;
        if (activeGroup == 3) {
            String[] subLabels = {"制造组件", "主手安装", "主手拆卸"};
            for (int s = 0; s < 3; s++) {
                final int subPage = 7 + s;
                Button sub = addRenderableWidget(Button.builder(Component.literal(subLabels[s]), button -> selectPage(subPage))
                        .bounds(leftPos + 8 + s * 96, topPos + 60, 92, 12).build());
                sub.active = page != subPage;
            }
            gridTop = topPos + 74;
        }
        int[] actions = switch (activeGroup) {
            case 0 -> new int[]{2, 3, 6, 4, 7, 9, 10, 11, 12, 14, 15, 16};
            case 1 -> new int[]{18, 54, 55, 52, 53, 29, 19, 20, 23};
            case 2 -> new int[]{21, 22, 27, 28, 24, 25};
            default -> switch (page) {
                case 8 -> new int[]{100, 101, 102, 103, 104, 105, 106, 107, 108};
                case 9 -> new int[]{120, 121, 122, 123, 124, 125, 126, 127, 128};
                default -> new int[]{40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51};
            };
        };
        int cols = activeGroup == 2 ? 2 : 3;
        int colStep = cols == 3 ? 96 : 146;
        int btnWidth = cols == 3 ? 92 : 138;
        int rowStep = activeGroup == 3 && actions.length > 9 ? 10 : 13;
        int btnHeight = activeGroup == 3 && actions.length > 9 ? 9 : 12;
        for (int i = 0; i < actions.length; i++) {
            int action = actions[i];
            Button button = addRenderableWidget(Button.builder(BestMechAssembly.handles(action) ? BestMechAssembly.label(action) : text("action_" + action), clicked -> {
                if (action >= 17 && action != 29) selectMaterialAction(action);
                if (minecraft != null && minecraft.gameMode != null && (action == 29 || menu.preview(action).executable()))
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
            }).bounds(leftPos + 8 + (i % cols) * colStep, gridTop + (i / cols) * rowStep, btnWidth, btnHeight).build());
            actionButtons.add(new ActionButton(action, button));
        }
        updateActions();
    }

    private void updateActions() {
        for (ActionButton entry : actionButtons) entry.button().active = entry.action() == 29 || menu.preview(entry.action()).executable();
    }
    private boolean hovered(Button button, int mouseX, int mouseY) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }
    private void fitted(GuiGraphics graphics, Component value, int x, int y, int width, int color) {
        float scale = Math.min(1f, (float) width / Math.max(1, font.width(value)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, value, 0, 0, color, false);
        graphics.pose().popPose();
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        int activeGroup = page <= 2 ? 1 : page == 3 ? 2 : page <= 6 ? 3 : 4;
        BestHangarTerminal.frame(graphics, font, x, y, imageWidth, imageHeight, "FAB / " + activeGroup);
        graphics.fill(x + 5, y + 114, x + imageWidth - 5, y + 173, BestHangarTerminal.PANEL);
        for (var slot : menu.slots) {
            int sx = x + slot.x, sy = y + slot.y;
            BestHangarTerminal.well(graphics, sx, sy);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        fitted(graphics, title, titleLabelX, titleLabelY, 280, 0xffedf4eb);
        if (page >= 3) {
            BestEngineering.Preview preview = menu.preview(materialAction());
            fitted(graphics, text("materials_for", BestMechAssembly.handles(materialAction()) ? BestMechAssembly.label(materialAction()) : text("material_action_" + materialAction()), preview.reason()), 10, 117, 280, preview.executable() ? 0xffadf2c5 : 0xffffb377);
            List<BestEngineering.MaterialRequirement> materials = preview.materials();
            for (int i = 0; i < materials.size(); i++) {
                var material = materials.get(i);
                int x = 10 + i % 4 * 71, y = 126 + i / 4 * 14;
                graphics.renderItem(material.item(), x, y);
                fitted(graphics, text("material_count", material.available(), material.required()), x + 18, y + 4, 51,
                        material.available() >= material.required() ? 0xffadf2c5 : 0xffff998b);
            }
        }
        if (page < 3 || menu.preview(materialAction()).materials().size() <= 8)
            fitted(graphics, page >= 3 ? Component.literal("也可潜行空手右键机甲，或驾驶时按 I 打开机甲终端换装与存取货舱") : text("hint_" + page), 10, 164, 280, 0xffbed4d9);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xff85e0d1, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateActions();
        for (ActionButton entry : actionButtons) {
            if (page >= 3 && entry.action() != 29 && hovered(entry.button(), mouseX, mouseY)) selectMaterialAction(entry.action());
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        for (ActionButton entry : actionButtons) {
            if (hovered(entry.button(), mouseX, mouseY)) {
                if (entry.action() == 29) {
                    graphics.renderTooltip(font, font.split(text("service_detail"), Math.min(280, width - 20)), mouseX, mouseY);
                    return;
                }
                var preview = menu.preview(entry.action());
                Component tooltip = (BestMechAssembly.handles(entry.action()) ? BestMechAssembly.label(entry.action()) : text("action_" + entry.action())).copy().append("\n").append(preview.reason());
                if (entry.action() == 26) tooltip = tooltip.copy().append("\n").append(BestEngineering.actionCostComponent(26));
                if (entry.action() == 55) tooltip = tooltip.copy().append("\n涓流与激湍各一台：须隔离、存活、货舱清空；两机与所列材料全部消耗，已装组件及配装返还。能源与损伤保留，返还空间不足则整笔不变。");
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
                int x = leftPos + 10 + i % 4 * 71, y = topPos + 126 + i / 4 * 14;
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    graphics.renderTooltip(font, materials.get(i).item(), mouseX, mouseY);
                    return;
                }
            }
        }
    }
}
