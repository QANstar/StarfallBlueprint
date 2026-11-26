package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechAssembly;
import cn.starfall.blueprint.BestMechEntity;
import cn.starfall.blueprint.BestMechLoadoutMenu;
import cn.starfall.blueprint.BestMechProgression;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Unified mech terminal screen: interactive 3D chassis preview, 8 hardware slots, upgrades, cargo and quick service. */
public final class BestMechLoadoutScreen extends AbstractContainerScreen<BestMechLoadoutMenu> {
    private static final String[] SLOT_LABELS = {"通用", "工臂", "机身", "核心", "行走", "武器", "僚机", "无线"};
    private float previewYaw = 32f;
    private boolean draggingPreview;
    private Button isolateButton;
    private final List<UpgradeButton> upgradeButtons = new ArrayList<>();
    private record UpgradeButton(int index, Button button) {}

    public BestMechLoadoutScreen(BestMechLoadoutMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 304;
        imageHeight = 244;
        titleLabelX = 8;
        titleLabelY = 7;
        inventoryLabelX = 69;
        inventoryLabelY = 148;
    }

    @Override
    protected void init() {
        super.init();
        upgradeButtons.clear();
        isolateButton = addRenderableWidget(Button.builder(Component.literal("隔离/接通"), b -> send(BestMechLoadoutMenu.ACTION_TOGGLE_ISOLATION))
                .bounds(leftPos + 134, topPos + 124, 52, 16).build());
        addRenderableWidget(Button.builder(Component.literal("维修降温"), b -> send(BestMechLoadoutMenu.ACTION_QUICK_SERVICE))
                .bounds(leftPos + 189, topPos + 124, 52, 16).build());
        addRenderableWidget(Button.builder(Component.literal("收纳机甲"), b -> send(BestMechLoadoutMenu.ACTION_PACK_MECH))
                .bounds(leftPos + 244, topPos + 124, 52, 16).build());
        addRenderableWidget(Button.builder(Component.literal("入包"), b -> send(BestMechLoadoutMenu.ACTION_TRANSFER_CARGO))
                .bounds(leftPos + 266, topPos + 144, 30, 13).build());

        BestMechEntity mech = menu.mech();
        if (mech != null) {
            int[] indices = switch (mech.platformType()) {
                case BestMechProgression.COMBAT -> new int[]{3, 4, 5};
                case BestMechProgression.UNIVERSAL -> new int[]{6, 7, 8};
                default -> new int[]{0, 1, 2};
            };
            for (int i = 0; i < indices.length; i++) {
                int idx = indices[i];
                String full = BestMechAssembly.RECIPES.get(idx).title();
                String shortName = full.contains("·") ? full.substring(full.indexOf('·') + 1) : full.replace("最终组件", "");
                Button btn = addRenderableWidget(Button.builder(Component.literal(shortName),
                        b -> send(BestMechLoadoutMenu.ACTION_UPGRADE_BASE + idx))
                        .bounds(leftPos + 8 + i * 39, topPos + 124, 37, 16).build());
                upgradeButtons.add(new UpgradeButton(idx, btn));
            }
        }
        refreshButtons();
    }

    private void send(int action) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
        }
    }

    private void refreshButtons() {
        BestMechEntity mech = menu.mech();
        if (isolateButton != null && mech != null) {
            isolateButton.setMessage(Component.literal(mech.isolated() ? "接通电源" : "隔离断电"));
        }
        for (UpgradeButton entry : upgradeButtons) {
            boolean installed = mech != null && mech.hasUpgrade(1 << entry.index());
            String full = BestMechAssembly.RECIPES.get(entry.index()).title();
            String shortName = full.contains("·") ? full.substring(full.indexOf('·') + 1) : full.replace("最终组件", "");
            entry.button().setMessage(Component.literal((installed ? "✓" : "+") + shortName));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= leftPos + 28 && mouseX <= leftPos + 104 && mouseY >= topPos + 28 && mouseY <= topPos + 118) {
            draggingPreview = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingPreview) {
            previewYaw -= (float) dx * 1.4f;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingPreview = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        BestHangarTerminal.frame(graphics, font, x, y, imageWidth, imageHeight, "MECH / " + menu.cargoSlots());
        // Left 3D preview well
        graphics.fill(x + 28, y + 28, x + 104, y + 118, 0xff0b151c);
        graphics.fill(x + 28, y + 28, x + 104, y + 29, BestHangarTerminal.EDGE);
        graphics.fill(x + 28, y + 117, x + 104, y + 118, BestHangarTerminal.EDGE);
        // Right cargo background
        graphics.fill(x + 131, y + 24, x + 298, y + 120, BestHangarTerminal.PANEL);
        for (var slot : menu.slots) {
            BestHangarTerminal.well(graphics, x + slot.x, y + slot.y);
        }
        BestMechEntity mech = menu.mech();
        if (mech != null) {
            float oldYRot = mech.getYRot(), oldBody = mech.yBodyRot, oldHead = mech.yHeadRot;
            mech.setYRot(0); mech.yBodyRot = 0; mech.yHeadRot = 0;
            Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.toRadians(previewYaw));
            float scale = BestMechProgression.UNIVERSAL.equals(mech.platformType()) ? 16f
                    : BestMechProgression.COMBAT.equals(mech.platformType()) ? 18f : 20f;
            InventoryScreen.renderEntityInInventory(graphics, x + 66f, y + 110f, scale, new Vector3f(0, 0, 0), rot, null, mech);
            mech.setYRot(oldYRot); mech.yBodyRot = oldBody; mech.yHeadRot = oldHead;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        BestMechEntity mech = menu.mech();
        graphics.drawString(font, mech != null ? mech.getName() : title, titleLabelX, titleLabelY, 0xffedf4eb, false);
        boolean editable = menu.hardwareEditable();
        Component lockHint = Component.literal(editable ? "已隔离 · 拖放换装 / 点击强化" : "需下车停稳并「隔离」后换装");
        drawFit(graphics, lockHint, 8, 22, 118, editable ? 0xff75d9c0 : 0xffefb45f);
        String cargoHeader = "货舱 (" + menu.cargoSlots() + "格)";
        if (mech != null) {
            cargoHeader += " · 电量 " + (mech.energy() / 1000) + "k / 热 " + mech.heat() + " / 磨 " + mech.wear();
        }
        drawFit(graphics, Component.literal(cargoHeader), 134, 22, 162, 0xffbed4d9);
        // Tiny label next to each hardware slot when empty
        int[] lx = {8, 8, 8, 8, 108, 108, 108, 108};
        int[] ly = {32, 54, 76, 98, 32, 54, 76, 98};
        for (int i = 0; i < BestMechLoadoutMenu.HARDWARE_SLOTS; i++) {
            if (menu.slots.get(i).getItem().isEmpty()) {
                drawFit(graphics, Component.literal(SLOT_LABELS[i]), lx[i] + 1, ly[i] + 5, 14, 0xff6b848e);
            }
        }
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xff85e0d1, false);
    }

    private void drawFit(GuiGraphics g, Component text, int x, int y, int width, int color) {
        float s = Math.min(1f, (float) width / Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(s, s, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.index < BestMechLoadoutMenu.HARDWARE_SLOTS && !hoveredSlot.hasItem()) {
            graphics.renderTooltip(font, Component.literal("配装槽位：" + SLOT_LABELS[hoveredSlot.index]), mouseX, mouseY);
        }
    }
}
