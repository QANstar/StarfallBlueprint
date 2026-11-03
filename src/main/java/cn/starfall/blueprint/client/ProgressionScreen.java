package cn.starfall.blueprint.client;

import cn.starfall.blueprint.CatalogueContent;
import cn.starfall.blueprint.Progression;
import cn.starfall.blueprint.ProgressionMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** One server-authoritative growth catalogue; both panes scroll without obscuring inventory slots. */
public final class ProgressionScreen extends AbstractContainerScreen<ProgressionMenu> {
    private static final int INK = 0xffdce9e7, MUTED = 0xff9cb5b4, GOOD = 0xff8bd7ab, BAD = 0xffeead91;
    private final List<Integer> visibleNodes = new ArrayList<>();
    private String category = "all";
    private int selected = -1, listScroll, detailScroll, detailHeight;
    private float uiScale = 1;
    private Button learn;

    public ProgressionScreen(ProgressionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 360;
        imageHeight = 256;
        inventoryLabelX = 100;
        inventoryLabelY = 162;
    }

    private static Component text(String key, Object... args) {
        return ProgressionInventoryTab.text(key, args);
    }

    @Override protected void init() {
        // Keep the server's real slot coordinates while fitting small GUI resolutions.
        uiScale = Math.min(1f, Math.min(width / 376f, height / 272f));
        super.init();
        leftPos = (int) (width / uiScale - imageWidth) / 2;
        topPos = (int) (height / uiScale - imageHeight) / 2;
        String[] categories = {"all", "best", "lingchuan"};
        for (int i = 0; i < categories.length; i++) {
            String target = categories[i];
            Button tab = addRenderableWidget(Button.builder(text("category." + target), button -> {
                category = target;
                listScroll = detailScroll = 0;
                filter();
                rebuildWidgets();
            }).bounds(leftPos + 8 + i * 84, topPos + 25, 80, 18).build());
            tab.active = !category.equals(target);
        }
        addRenderableWidget(Button.builder(text("back"), button -> onClose())
                .bounds(leftPos + 274, topPos + 5, 78, 16).build());
        learn = addRenderableWidget(Button.builder(text("learn"), button -> {
            if (selected >= 0 && minecraft != null && minecraft.gameMode != null && menu.preview(selected).executable())
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, selected);
        }).bounds(leftPos + 267, topPos + 132, 85, 18).build());
        filter();
    }

    private void filter() {
        visibleNodes.clear();
        for (int i = 0; i < Progression.NODES.size(); i++)
            if (category.equals("all") || Progression.NODES.get(i).category().equals(category)) visibleNodes.add(i);
        if (!visibleNodes.contains(selected)) {
            selected = visibleNodes.isEmpty() ? -1 : visibleNodes.getFirst();
            detailScroll = 0;
        }
        listScroll = Math.clamp(listScroll, 0, Math.max(0, visibleNodes.size() - 5));
    }

    @Override public void onClose() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
            minecraft.setScreen(new InventoryScreen(minecraft.player));
        }
    }

    private void fitted(GuiGraphics g, Component value, int x, int y, int maxWidth, int color) {
        float scale = Math.min(1f, maxWidth / (float) Math.max(1, font.width(value)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, value, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.pose().pushPose();
        g.pose().scale(1 / uiScale, 1 / uiScale, 1);
        renderTransparentBackground(g);
        g.pose().popPose();
        renderBg(g, partial, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + 360, y + 256, 0xff101c24);
        g.fill(x + 2, y + 2, x + 358, y + 22, 0xff26464d);
        g.fill(x + 8, y + 47, x + 143, y + 151, 0xff192e36);
        g.fill(x + 148, y + 47, x + 352, y + 129, 0xff192e36);
        g.fill(x + 8, y + 159, x + 352, y + 160, 0xff39565b);
        for (var slot : menu.slots) {
            int sx = x + slot.x, sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xff4e6a6b);
            g.fill(sx, sy, sx + 16, sy + 16, 0xff253a42);
        }
        for (int row = 0; row < 5 && row + listScroll < visibleNodes.size(); row++) {
            int index = visibleNodes.get(row + listScroll), ry = y + 49 + row * 20;
            var node = Progression.NODES.get(index);
            boolean learned = menu.isLearned(node.id());
            boolean ready = menu.preview(index).executable();
            if (index == selected) g.fill(x + 9, ry - 1, x + 139, ry + 18, 0xff36545b);
            g.renderItem(node.icon(), x + 11, ry);
            fitted(g, node.title(), x + 30, ry + 1, 106, learned ? GOOD : INK);
            fitted(g, text(learned ? "state.learned" : ready ? "state.available" : "state.locked"), x + 30, ry + 10, 106,
                    learned || ready ? GOOD : MUTED);
        }
        scrollBar(g, x + 140, y + 49, 98, listScroll, Math.max(0, visibleNodes.size() - 5), 5, visibleNodes.size());
        if (selected >= 0) renderDetails(g);
    }

    private void scrollBar(GuiGraphics g, int x, int y, int height, int scroll, int max, int shown, int total) {
        if (max <= 0) return;
        g.fill(x, y, x + 2, y + height, 0xff30474e);
        int thumb = Math.max(10, height * shown / Math.max(1, total));
        int top = y + (height - thumb) * scroll / max;
        g.fill(x, top, x + 2, top + thumb, 0xff8db8b4);
    }

    private void renderDetails(GuiGraphics g) {
        var node = Progression.NODES.get(selected);
        int x = leftPos + 153, y = topPos + 51 - detailScroll;
        g.enableScissor((int) ((leftPos + 150) * uiScale), (int) ((topPos + 49) * uiScale),
                (int) ((leftPos + 348) * uiScale), (int) ((topPos + 127) * uiScale));
        fitted(g, node.title(), x, y, 190, INK);
        y += 13;
        fitted(g, text("experience_summary", menu.xpLevels(), node.experienceLevels()), x, y, 190,
                menu.xpLevels() >= node.experienceLevels() ? GOOD : BAD);
        y += 13;
        for (FormattedCharSequence line : font.split(node.description(), 190)) {
            g.drawString(font, line, x, y, MUTED, false);
            y += 10;
        }
        y += 4;
        g.drawString(font, text("prerequisites"), x, y, INK, false);
        y += 12;
        if (node.prerequisites().isEmpty()) {
            g.drawString(font, text("none"), x, y, MUTED, false);
            y += 12;
        }
        for (String id : node.prerequisites()) {
            var prerequisite = Progression.NODES.stream().filter(n -> n.id().equals(id)).findFirst();
            Component name = prerequisite.map(Progression.Node::title).orElse(Component.literal(id));
            fitted(g, text(menu.isLearned(id) ? "prerequisite_met" : "prerequisite_missing", name), x, y, 190,
                    menu.isLearned(id) ? GOOD : BAD);
            y += 12;
        }
        y += 3;
        fitted(g, text("materials_heading"), x, y, 190, INK);
        y += 12;
        if (node.costs().isEmpty()) {
            g.drawString(font, text("none"), x, y, MUTED, false);
            y += 12;
        }
        for (var cost : node.costs()) {
            ItemStack item = CatalogueContent.stack(cost.key(), 1);
            int available = available(cost.key());
            g.renderItem(item, x, y);
            fitted(g, item.getHoverName(), x + 19, y, 169, INK);
            fitted(g, text("material_count", available, cost.count()), x + 19, y + 9, 169,
                    available >= cost.count() ? GOOD : BAD);
            y += 20;
        }
        y += 4;
        fitted(g, text("rewards_heading"), x, y, 190, INK);
        y += 12;
        fitted(g, text("rewards_once"), x, y, 190, MUTED);
        y += 12;
        if (node.reward().experiencePoints() > 0) {
            fitted(g, text("experience_reward", node.reward().experiencePoints()), x, y, 190, GOOD);
            y += 12;
        }
        for (var reward : node.reward().items()) {
            ItemStack item = CatalogueContent.stack(reward.key(), 1);
            g.renderItem(item, x, y);
            fitted(g, item.getHoverName(), x + 19, y, 169, INK);
            fitted(g, text("item_reward", reward.count()), x + 19, y + 9, 169, GOOD);
            y += 20;
        }
        if (node.reward().experiencePoints() == 0 && node.reward().items().isEmpty()) {
            g.drawString(font, text("none"), x, y, MUTED, false);
            y += 12;
        }
        detailHeight = y - (topPos + 51 - detailScroll);
        g.disableScissor();
        detailScroll = Math.clamp(detailScroll, 0, Math.max(0, detailHeight - 74));
        scrollBar(g, leftPos + 349, topPos + 49, 78, detailScroll, Math.max(0, detailHeight - 74), 74, detailHeight);
    }

    private int available(String key) {
        int count = 0;
        for (var slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (CatalogueContent.matches(key, stack) && Progression.usableMaterial(stack)) count += stack.getCount();
        }
        return count;
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        fitted(g, text("title"), 9, 7, 255, INK);
        fitted(g, text("details_scroll"), 151, 137, 112, MUTED);
        if (selected >= 0) fitted(g, menu.preview(selected).reason(), 150, 151, 202,
                menu.preview(selected).executable() || menu.isLearned(Progression.NODES.get(selected).id()) ? GOOD : BAD);
        fitted(g, text("progress", Progression.NODES.stream().filter(n -> menu.isLearned(n.id())).count(), Progression.NODES.size()),
                9, 164, 85, MUTED);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int mx = (int) (mouseX / uiScale), my = (int) (mouseY / uiScale);
        learn.active = selected >= 0 && menu.preview(selected).executable();
        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1);
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
        if (selected >= 0 && mx >= leftPos + 148 && mx < leftPos + 352 && my >= topPos + 132 && my < topPos + 159)
            g.renderTooltip(font, font.split(menu.preview(selected).reason(), 230), mx, my);
        for (int row = 0; row < 5 && row + listScroll < visibleNodes.size(); row++) {
            if (mx >= leftPos + 8 && mx < leftPos + 140 && my >= topPos + 49 + row * 20 && my < topPos + 69 + row * 20) {
                var node = Progression.NODES.get(visibleNodes.get(row + listScroll));
                g.renderTooltip(font, font.split(node.title().copy().append("\n").append(node.description()), 230), mx, my);
            }
        }
        g.pose().popPose();
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        x /= uiScale;
        y /= uiScale;
        if (button == 0 && x >= leftPos + 8 && x < leftPos + 143 && y >= topPos + 49 && y < topPos + 149) {
            int row = (int) (y - topPos - 49) / 20 + listScroll;
            if (row < visibleNodes.size()) { selected = visibleNodes.get(row); detailScroll = 0; return true; }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(x / uiScale, y / uiScale, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x / uiScale, y / uiScale, button, dx / uiScale, dy / uiScale);
    }
    @Override public void mouseMoved(double x, double y) { super.mouseMoved(x / uiScale, y / uiScale); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        x /= uiScale;
        y /= uiScale;
        if (x >= leftPos + 8 && x < leftPos + 143 && y >= topPos + 47 && y < topPos + 151) {
            listScroll = Math.clamp(listScroll - (int) Math.signum(vertical), 0, Math.max(0, visibleNodes.size() - 5));
            return true;
        }
        if (x >= leftPos + 148 && x < leftPos + 352 && y >= topPos + 47 && y < topPos + 129) {
            detailScroll = Math.clamp(detailScroll - (int) (vertical * 14), 0, Math.max(0, detailHeight - 74));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
}
