package cn.starfall.blueprint.client;

import cn.starfall.blueprint.Esper;
import cn.starfall.blueprint.EsperMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

final class EsperScreen extends AbstractContainerScreen<EsperMenu> {
    private static final String[] GRADES = {"-", "F", "E", "D", "C", "B", "A", "S"};
    private static final int INK = 0xffeaf4f4, MUTED = 0xffa5bec3, SPACE = 0xffa9e4ee, ELECTRIC = 0xff68baff;
    private static final int CARD_W = 116, CARD_H = 46, STEP_X = 153, STEP_Y = 67;
    private final Button[] equip = new Button[Esper.SLOTS], clear = new Button[Esper.SLOTS];
    private Esper.School preview = Esper.School.SPACE, displayed;
    private Esper.Skill selected = Esper.Skill.BLINK;
    private Button train, promote, space, electric;
    private int panel, treeX, treeY, treeW, treeH, detailScroll, detailHeight;
    private double zoom = 1, panX, panY;
    private boolean dragging, positioned;

    EsperScreen(EsperMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private Esper.School tree() { return menu.school() == null ? preview : menu.school(); }
    private int accent() { return displayed == Esper.School.ELECTRIC ? ELECTRIC : SPACE; }
    private void send(int action) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }

    @Override protected void init() {
        imageWidth = Math.max(1, width - 16);
        imageHeight = Math.max(1, height - 16);
        super.init();
        var school = tree();
        if (school != displayed) {
            displayed = school;
            selected = school.root();
            positioned = false;
            detailScroll = 0;
        }
        panel = Math.min(200, Math.max(105, imageWidth / 3));
        treeX = leftPos + 8;
        treeY = topPos + 47;
        treeW = imageWidth - panel - 22;
        treeH = Math.max(1, imageHeight - 89);
        if (!positioned) fit();
        if (treeW < 50) return;
        int schoolWidth = Math.min(74, (treeW - 22) / 2);
        space = button(8, 23, schoolWidth, 17, Esper.School.SPACE.title(), b -> choose(Esper.School.SPACE));
        electric = button(11 + schoolWidth, 23, schoolWidth, 17, Esper.School.ELECTRIC.title(), b -> choose(Esper.School.ELECTRIC));
        button(8 + treeW - 18, 23, 18, 17, Component.literal("[]"), b -> fit())
                .setTooltip(Tooltip.create(Esper.label("view_fit")));
        int panelX = imageWidth - panel - 8;
        train = button(panelX + 5, imageHeight - 80, panel - 10, 16, Esper.label("train"), b -> send(selected.ordinal()));
        promote = button(panelX + 5, imageHeight - 62, panel - 10, 16, Esper.label("promote"), b -> send(EsperMenu.PROMOTE));
        int slotWidth = (imageWidth - 16) / Esper.SLOTS;
        for (int i = 0; i < Esper.SLOTS; i++) {
            int slot = i, x = 8 + i * slotWidth;
            equip[i] = button(x, imageHeight - 23, slotWidth - 17, 16, Component.empty(),
                    b -> send(EsperMenu.EQUIP + displayed.skills().indexOf(selected) * Esper.SLOTS + slot));
            clear[i] = button(x + slotWidth - 16, imageHeight - 23, 13, 16, Component.literal("x"),
                    b -> send(EsperMenu.CLEAR + slot));
            clear[i].setTooltip(Tooltip.create(Esper.label("clear")));
        }
    }

    private Button button(int x, int y, int w, int h, Component name, Button.OnPress press) {
        return addRenderableWidget(new Control(leftPos + x, topPos + y, w, h, name, press));
    }
    private void choose(Esper.School school) {
        if (tree() == school || menu.school() != null) return;
        preview = school;
        rebuildWidgets();
    }
    private void fit() {
        int cols = displayed.skills().stream().mapToInt(s -> s.column).max().orElse(0);
        int rows = displayed.skills().stream().mapToInt(s -> s.row).max().orElse(0);
        int w = cols * STEP_X + CARD_W, h = rows * STEP_Y + CARD_H;
        zoom = Math.clamp(Math.min((treeW - 20.0) / w, (treeH - 20.0) / h), .36, 1.15);
        panX = (treeW - w * zoom) / 2;
        panY = (treeH - h * zoom) / 2;
        positioned = true;
    }
    private boolean inTree(double x, double y) {
        return x >= treeX && x < treeX + treeW && y >= treeY && y < treeY + treeH;
    }
    private Esper.Skill nodeAt(double mx, double my) {
        if (!inTree(mx, my)) return null;
        double x = (mx - treeX - panX) / zoom, y = (my - treeY - panY) / zoom;
        for (var node : displayed.skills()) {
            int left = node.column * STEP_X, top = node.row * STEP_Y;
            if (x >= left && x < left + CARD_W && y >= top && y < top + CARD_H) return node;
        }
        return null;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inTree(x, y) && (button == 0 || button == 2)) {
            var node = button == 0 ? nodeAt(x, y) : null;
            if (node != null) { selected = node; detailScroll = 0; }
            else dragging = true;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging) { panX += dx; panY += dy; return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (dragging) { dragging = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (inTree(x, y)) {
            double next = Math.clamp(zoom * Math.pow(1.16, dy), .22, 2.0);
            panX = x - treeX - (x - treeX - panX) * next / zoom;
            panY = y - treeY - (y - treeY - panY) * next / zoom;
            zoom = next;
            return true;
        }
        if (x > treeX + treeW && x < leftPos + imageWidth && y >= treeY && y < topPos + imageHeight - 80) {
            detailScroll = Math.clamp(detailScroll - (int) Math.round(dy * 14), 0, detailHeight);
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff142226);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xff54757b);
        g.hLine(leftPos + 1, leftPos + imageWidth - 2, topPos + 1, accent());
        if (treeW < 50) return;
        drawTree(g, mx, my);
        details(g);
        g.hLine(treeX, leftPos + imageWidth - 8, topPos + imageHeight - 35, 0xff456067);
    }

    private void drawTree(GuiGraphics g, int mx, int my) {
        int right = treeX + treeW, bottom = treeY + treeH;
        g.fill(treeX, treeY, right, bottom, 0xff0b161a);
        g.enableScissor(treeX, treeY, right, bottom);
        int grid = Math.max(6, (int) (24 * zoom));
        for (int x = treeX + Math.floorMod((int) panX, grid); x < right; x += grid)
            for (int y = treeY + Math.floorMod((int) panY, grid); y < bottom; y += grid) g.fill(x, y, x + 1, y + 1, 0xff294147);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(treeX + panX, treeY + panY, 0);
        pose.scale((float) zoom, (float) zoom, 1);
        for (var node : displayed.skills()) for (var parent : node.prerequisites()) {
            int x0 = parent.column * STEP_X + CARD_W, x1 = node.column * STEP_X;
            int y0 = parent.row * STEP_Y + CARD_H / 2, y1 = node.row * STEP_Y + CARD_H / 2;
            int mid = (x0 + x1) / 2;
            int color = menu.level(parent.ordinal()) > 0 ? accent() : 0xff496167;
            g.hLine(x0, mid, y0, color);
            g.vLine(mid, y0, y1, color);
            g.hLine(mid, x1, y1, color);
        }
        var hovered = nodeAt(mx, my);
        for (var node : displayed.skills()) {
            int x = node.column * STEP_X, y = node.row * STEP_Y, level = menu.level(node.ordinal());
            int color = level > 0 || menu.canTrain(node.ordinal()) ? accent() : 0xff799096;
            g.fill(x + 3, y + 4, x + CARD_W + 3, y + CARD_H + 4, 0x90000000);
            g.fill(x, y, x + CARD_W, y + CARD_H, node == selected ? 0xff304b51 : node == hovered ? 0xff294047 : 0xff1b2c31);
            g.renderOutline(x, y, CARD_W, CARD_H, node == selected ? accent() : 0xff4c686e);
            g.fill(x + 1, y + 1, x + 4, y + CARD_H - 1, color);
            EsperSkillIcons.draw(g, node, x + 6, y + 5);
            g.drawString(font, font.plainSubstrByWidth(node.title().getString(), CARD_W - 32), x + 25, y + 8, INK, false);
            g.drawString(font, GRADES[node.rank], x + 10, y + 24, color, false);
            for (int i = 0; i < Esper.MAX_LEVEL; i++)
                g.fill(x + 61 + i * 8, y + 29, x + 67 + i * 8, y + 33, i < level ? color : 0xff3d545a);
        }
        pose.popPose();
        g.disableScissor();
        g.renderOutline(treeX, treeY, treeW, treeH, 0xff455f65);
    }

    private void details(GuiGraphics g) {
        int x = leftPos + imageWidth - panel - 8, bottom = topPos + imageHeight - 81;
        g.fill(x, treeY, x + panel, treeY + treeH, 0xff203338);
        g.hLine(x, x + panel - 1, treeY, accent());
        if (bottom <= treeY + 8) return;
        g.enableScissor(x + 4, treeY + 3, x + panel - 4, bottom);
        int y = treeY + 8 - detailScroll;
        int index = selected.ordinal(), level = menu.level(index);
        y = paragraph(g, selected.title(), x + 8, y, accent()) + 5;
        y = paragraph(g, Esper.label("desc." + selected.key), x + 8, y, INK) + 5;
        var requirements = Component.empty();
        for (var parent : selected.prerequisites()) {
            if (!requirements.getSiblings().isEmpty()) requirements.append(Component.literal(" + "));
            requirements.append(parent.title());
        }
        if (!requirements.getSiblings().isEmpty())
            y = paragraph(g, Esper.label("requires", requirements), x + 8, y, MUTED) + 4;
        y = paragraph(g, Esper.label("tree_skill", GRADES[selected.rank], level, Esper.MAX_LEVEL), x + 8, y, MUTED);
        y = paragraph(g, Esper.label("tree_cast", selected.cost, selected.charge,
                String.format(java.util.Locale.ROOT, "%.1f", selected.cooldownAt(Math.max(1, level)) / 20F)), x + 8, y, MUTED) + 5;
        if (level < Esper.MAX_LEVEL) {
            y = paragraph(g, Esper.label("tree_train", menu.trainCost(index), menu.trainEnergy(index)), x + 8, y, accent());
            if (level > 0) y = paragraph(g, Esper.label("tree_mastery", menu.mastery(index), menu.practiceCost(index)), x + 8, y, MUTED);
        }
        if (menu.rank() > 0 && menu.rank() < Esper.MAX_RANK) {
            y = paragraph(g, Esper.label("tree_promote", menu.rankCost(), menu.rankEnergy()), x + 8, y + 5, accent());
            y = paragraph(g, Esper.label("tree_practice", menu.practice(), menu.rankPractice()), x + 8, y, MUTED);
        }
        detailHeight = Math.max(0, y + detailScroll - bottom + 4);
        detailScroll = Math.min(detailScroll, detailHeight);
        g.disableScissor();
    }
    private int paragraph(GuiGraphics g, Component text, int x, int y, int color) {
        for (var row : font.split(text, panel - 16)) {
            g.drawString(font, row, x, y, color, false);
            y += 11;
        }
        return y;
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        var status = Esper.label("tree_status", GRADES[menu.rank()], menu.xp(), menu.energy());
        int statusWidth = Math.min(font.width(status), imageWidth / 2);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(1, imageWidth - statusWidth - 25)), 8, 8, INK, false);
        g.drawString(font, font.plainSubstrByWidth(status.getString(), statusWidth), imageWidth - 8 - statusWidth, 8, accent(), false);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        if (displayed != tree()) rebuildWidgets();
        if (treeW >= 50) {
            int selectedIndex = selected.ordinal();
            space.active = menu.school() == null && displayed != Esper.School.SPACE;
            electric.active = menu.school() == null && displayed != Esper.School.ELECTRIC;
            train.setMessage(Esper.label(menu.school() == null ? "awaken" : menu.level(selectedIndex) == 0 ? "learn" : "train"));
            train.active = menu.canTrain(selectedIndex);
            promote.active = menu.canPromote();
            for (int i = 0; i < Esper.SLOTS; i++) {
                int index = menu.slot(i);
                var key = EsperClient.CAST[i].getTranslatedKeyMessage();
                var label = key.copy().append(" ").append(index < 0 ? Component.literal("-") : Esper.SKILLS[index].title());
                equip[i].setMessage(Component.literal(font.plainSubstrByWidth(label.getString(), equip[i].getWidth() - 8)));
                equip[i].setTooltip(Tooltip.create(index < 0 ? Esper.label("equip", i + 1) : label));
                equip[i].active = !selected.passive() && menu.school() == displayed && menu.level(selectedIndex) > 0 && index != selectedIndex;
                clear[i].active = index >= 0;
            }
        }
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
        var hovered = nodeAt(mx, my);
        if (hovered != null && zoom < .6) g.renderTooltip(font, hovered.title(), mx, my);
    }

    private final class Control extends Button {
        Control(int x, int y, int w, int h, Component name, OnPress press) { super(x, y, w, h, name, press, DEFAULT_NARRATION); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            int color = active ? accent() : 0xff7b989e;
            int x = getX(), y = getY();
            g.fill(x, y, x + width, y + height, active && isHoveredOrFocused() ? 0xff3e555a : active ? 0xff2b4146 : 0xff1c2b30);
            g.fill(x, y, x + 2, y + height, color);
            String name = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, width - 6));
            g.drawString(font, name, x + (width - font.width(name)) / 2, y + (height - 8) / 2, color, false);
        }
    }
}
