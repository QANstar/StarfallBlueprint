package cn.starfall.blueprint.client;



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


/** Instruction articles and personal settings, with no learning or payment controls. */
public final class ProgressionScreen extends AbstractContainerScreen<ProgressionMenu> {
    private static final int INK = 0xffdce9e7, MUTED = 0xff9cb5b4, GOOD = 0xff8bd7ab, BAD = 0xffeead91;
    private final List<Integer> visibleNodes = new ArrayList<>();
    private final List<ManualArticles.Article> articles = ManualArticles.entries();
    private String category = "all";
    private int selected = -1, listScroll, detailScroll, detailHeight;
    private float uiScale = 1;
    private Button esperSetting, blackDogSetting;

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
        String[] categories = {"all", "camp", "best", "lingchuan", "haijia"};
        for (int i = 0; i < categories.length; i++) {
            String target = categories[i];
            Button tab = addRenderableWidget(Button.builder(text("category." + target), button -> {
                category = target;
                listScroll = detailScroll = 0;
                filter();
                rebuildWidgets();
            }).bounds(leftPos + 8 + i * 69, topPos + 25, 65, 18).build());
            tab.active = !category.equals(target);
        }
        Button settings = addRenderableWidget(Button.builder(text("category.settings"), button -> {
            category = "settings";
            listScroll = detailScroll = 0;
            filter();
            rebuildWidgets();
        }).bounds(leftPos + 176, topPos + 5, 94, 16).build());
        settings.active = !category.equals("settings");
        addRenderableWidget(Button.builder(text("back"), button -> onClose())
                .bounds(leftPos + 274, topPos + 5, 78, 16).build());
        esperSetting = settingButton(cn.starfall.blueprint.PlayerManual.ESPER, 65);
        blackDogSetting = settingButton(cn.starfall.blueprint.PlayerManual.BLACK_DOG, 95);
        filter();
    }

    private Button settingButton(int setting, int y) {
        return addRenderableWidget(Button.builder(settingLabel(setting), button -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, setting);
        }).bounds(leftPos + 16, topPos + y, 328, 22).build());
    }
    private Component settingLabel(int setting) {
        return text(setting == cn.starfall.blueprint.PlayerManual.ESPER ? "setting.esper" : "setting.black_dog").copy()
                .append(": ").append(text(menu.allows(setting) ? "setting.on" : "setting.off"));
    }

    private void filter() {
        visibleNodes.clear();
        for (int i = 0; i < articles.size(); i++)
            if ((category.equals("all") || articles.get(i).category().equals(category))) visibleNodes.add(i);
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
        if (category.equals("settings")) g.fill(x + 8, y + 47, x + 352, y + 129, 0xff192e36);
        g.fill(x + 8, y + 159, x + 352, y + 160, 0xff39565b);
        for (var slot : menu.slots) {
            int sx = x + slot.x, sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xff4e6a6b);
            g.fill(sx, sy, sx + 16, sy + 16, 0xff253a42);
        }
        for (int row = 0; row < 5 && row + listScroll < visibleNodes.size(); row++) {
            int index = visibleNodes.get(row + listScroll), ry = y + 49 + row * 20;
            var node = articles.get(index);

            if (index == selected) g.fill(x + 9, ry - 1, x + 139, ry + 18, 0xff36545b);
            g.renderItem(node.icon(), x + 11, ry);
            fitted(g, node.title(), x + 30, ry + 1, 106, INK);
            fitted(g, text("state.reference"), x + 30, ry + 10, 106,
                    MUTED);
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
        var node = articles.get(selected);
        int x = leftPos + 153, y = topPos + 51 - detailScroll;
        g.enableScissor((int) ((leftPos + 150) * uiScale), (int) ((topPos + 49) * uiScale),
                (int) ((leftPos + 348) * uiScale), (int) ((topPos + 127) * uiScale));
        fitted(g, node.title(), x, y, 190, INK);
        y += 13;
        for (FormattedCharSequence line : font.split(node.description(), 190)) {
            g.drawString(font, line, x, y, MUTED, false);
            y += 10;
        }
        if (!node.related().isEmpty()) {
            y += 4;
            g.drawString(font, text("related"), x, y, INK, false);
            y += 12;
            for (String id : node.related()) {
                var related = articles.stream().filter(article -> article.id().equals(id)).findFirst();
                if (related.isPresent()) {
                    fitted(g, related.get().title(), x, y, 190, MUTED);
                    y += 12;
                }
            }
        }
        detailHeight = y - (topPos + 51 - detailScroll);
        g.disableScissor();
        detailScroll = Math.clamp(detailScroll, 0, Math.max(0, detailHeight - 74));
        scrollBar(g, leftPos + 349, topPos + 49, 78, detailScroll, Math.max(0, detailHeight - 74), 74, detailHeight);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        fitted(g, text("title"), 9, 7, 160, INK);
        if (category.equals("settings")) fitted(g, text("settings_hint"), 9, 137, 340, MUTED);
        else {
            fitted(g, text("details_scroll"), 151, 137, 196, MUTED);
            fitted(g, text("article_count", visibleNodes.size()), 9, 164, 85, MUTED);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int mx = (int) (mouseX / uiScale), my = (int) (mouseY / uiScale);
        esperSetting.visible = blackDogSetting.visible = category.equals("settings");
        esperSetting.setMessage(settingLabel(cn.starfall.blueprint.PlayerManual.ESPER));
        blackDogSetting.setMessage(settingLabel(cn.starfall.blueprint.PlayerManual.BLACK_DOG));
        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1);
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
        for (int row = 0; row < 5 && row + listScroll < visibleNodes.size(); row++) {
            if (mx >= leftPos + 8 && mx < leftPos + 140 && my >= topPos + 49 + row * 20 && my < topPos + 69 + row * 20) {
                var node = articles.get(visibleNodes.get(row + listScroll));
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
