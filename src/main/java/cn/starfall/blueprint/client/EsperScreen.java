package cn.starfall.blueprint.client;

import cn.starfall.blueprint.Esper;
import cn.starfall.blueprint.EsperMenu;
import java.util.EnumMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

final class EsperScreen extends AbstractContainerScreen<EsperMenu> {
    private Esper.School preview = Esper.School.SPACE;
    private Esper.School renderedSchool;
    private Esper.Skill selected = Esper.Skill.BLINK;
    private Button train, promote, space, electric;
    private final Button[] equip = new Button[Esper.SLOTS], clear = new Button[Esper.SLOTS];
    private final EnumMap<Esper.Skill, Button> nodes = new EnumMap<>(Esper.Skill.class);
    private static final String[] GRADES = {"-", "F", "E", "D", "C", "B", "A", "S"};
    private static final int COL = 61, ROW = 24, NODE_W = 53, NODE_H = 17;

    EsperScreen(EsperMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 316; imageHeight = 220;
    }
    private Button button(int x, int y, int w, int h, Component label, Button.OnPress action) {
        return addRenderableWidget(Button.builder(label, action).bounds(leftPos + x, topPos + y, w, h).build());
    }
    private Esper.School tree() { return menu.school() == null ? preview : menu.school(); }
    private void select(Esper.School school) { preview = school; selected = school.root(); rebuildWidgets(); }
    private static int x(Esper.Skill skill) { return 9 + skill.column * COL; }
    private static int y(Esper.Skill skill) { return 43 + skill.row * ROW; }
    private Component shortName(Component title, int width) {
        if (font.width(title) <= width) return title;
        return Component.literal(font.plainSubstrByWidth(title.getString(), width - 5) + "…");
    }
    @Override protected void init() {
        super.init();
        renderedSchool = tree();
        if (selected.school != renderedSchool) selected = renderedSchool.root();
        nodes.clear();
        space = button(9, 21, 62, 16, Esper.School.SPACE.title(), b -> select(Esper.School.SPACE));
        electric = button(75, 21, 62, 16, Esper.School.ELECTRIC.title(), b -> select(Esper.School.ELECTRIC));
        for (Esper.Skill skill : renderedSchool.skills()) {
            Button node = button(x(skill), y(skill), NODE_W, NODE_H, shortName(skill.title(), NODE_W - 6), b -> selected = skill);
            var tooltip = Component.empty().append(skill.title()).append("\n").append(Esper.label("desc." + skill.key));
            if (!skill.prerequisites().isEmpty()) {
                var needs = Component.empty();
                for (Esper.Skill required : skill.prerequisites()) {
                    if (!needs.getString().isEmpty()) needs.append(" + ");
                    needs.append(required.title());
                }
                tooltip.append("\n").append(Esper.label("requires", needs));
            }
            node.setTooltip(Tooltip.create(tooltip));
            nodes.put(skill, node);
        }
        train = button(9, 164, 72, 17, Esper.label("train"), b -> send(selected.ordinal()));
        promote = button(86, 164, 82, 17, Esper.label("promote"), b -> send(EsperMenu.PROMOTE));
        for (int i = 0; i < Esper.SLOTS; i++) {
            final int slot = i;
            equip[i] = button(9 + i * 77, 188, 56, 17, Component.empty(),
                    b -> send(EsperMenu.EQUIP + renderedSchool.skills().indexOf(selected) * Esper.SLOTS + slot));
            clear[i] = button(67 + i * 77, 188, 13, 17, Component.literal("×"), b -> send(EsperMenu.CLEAR + slot));
            clear[i].setTooltip(Tooltip.create(Esper.label("clear")));
        }
    }
    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int left = leftPos, top = topPos;
        g.fill(left, top, left + imageWidth, top + imageHeight, 0xff152028);
        g.renderOutline(left, top, imageWidth, imageHeight, 0xff547980);
        g.fill(left + 1, top + 39, left + 315, top + 136, 0xff0e171d);
        g.fill(left + 8, top + 139, left + 308, top + 140, 0xff45636a);
        for (Esper.Skill skill : renderedSchool.skills()) {
            for (Esper.Skill required : skill.prerequisites()) {
                int x0 = left + x(required) + NODE_W, x1 = left + x(skill);
                int y0 = top + y(required) + NODE_H / 2, y1 = top + y(skill) + NODE_H / 2;
                int corner = x1 - 4;
                int color = menu.level(required.ordinal()) > 0 ? 0xff5ad7c1 : 0xff576475;
                g.hLine(x0, corner, y0, color);
                g.vLine(corner, Math.min(y0, y1), Math.max(y0, y1), color);
                g.hLine(corner, x1, y1, color);
            }
            if (skill == selected || menu.level(skill.ordinal()) > 0)
                g.renderOutline(left + x(skill) - 1, top + y(skill) - 1, NODE_W + 2, NODE_H + 2,
                        skill == selected ? 0xfff4cf7b : 0xff51a790);
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        int rank = Math.clamp(menu.rank(), 0, Esper.MAX_RANK);
        g.drawString(font, title, 9, 7, 0xffe3fff9, false);
        Component gauge = Esper.label("gauge", GRADES[rank], menu.power(), menu.capacity());
        g.drawString(font, gauge, 307 - font.width(gauge), 7, 0xff9deae1, false);
        if (renderedSchool == Esper.School.ELECTRIC)
            g.drawString(font, Esper.label("charge", menu.charge()), 145, 25, 0xfff4cf7b, false);
        var stat = Esper.label("skill_stats", menu.level(selected.ordinal()), GRADES[selected.rank], selected.cost);
        g.drawString(font, selected.title(), 9, 146, 0xffe8fbff, false);
        g.drawString(font, stat, 307 - font.width(stat), 146, 0xffa9c7d1, false);
        var practice = Esper.label("practice", menu.mastery(selected.ordinal()), menu.practiceCost(selected.ordinal()),
                menu.practice(), menu.rankPractice());
        g.drawString(font, shortName(practice, 133), 174, 169, 0xffe5c995, false);
        for (int i = 0; i < Esper.SLOTS; i++) {
            int index = menu.slot(i);
            Component equipped = index < 0 ? Esper.label("empty") : Esper.SKILLS[index].title();
            g.drawString(font, shortName(equipped, 71), 10 + i * 77, 207, index < 0 ? 0xff85a3ab : 0xffe8fbff, false);
        }
    }
    @Override protected void containerTick() {
        super.containerTick();
        if (renderedSchool != tree()) rebuildWidgets();
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        space.active = menu.school() == null && renderedSchool != Esper.School.SPACE;
        electric.active = menu.school() == null && renderedSchool != Esper.School.ELECTRIC;
        for (var node : nodes.entrySet()) {
            Esper.Skill skill = node.getKey();
            int color = skill == selected ? 0xfff9da8d : menu.level(skill.ordinal()) > 0 ? 0xffa7ffdb
                    : menu.canTrain(skill.ordinal()) ? 0xffe8fbff : 0xffafb8bf;
            node.getValue().setMessage(shortName(skill.title(), NODE_W - 6).copy().withColor(color));
        }
        int index = selected.ordinal();
        train.active = menu.canTrain(index);
        train.setMessage(Esper.label(menu.rank() == 0 ? "awaken" : menu.level(index) == 0 ? "learn" : "train"));
        train.setTooltip(Tooltip.create(Esper.label(selected.charge == 0 ? "xp" : "electric_cost", menu.trainCost(index), selected.charge)));
        promote.active = menu.canPromote();
        promote.setTooltip(Tooltip.create(Esper.label("xp", menu.rankCost())));
        for (int i = 0; i < Esper.SLOTS; i++) {
            equip[i].setMessage(shortName(EsperClient.CAST[i].getTranslatedKeyMessage(), 50));
            equip[i].active = menu.school() == renderedSchool && menu.level(index) > 0 && menu.slot(i) != index;
            equip[i].setTooltip(Tooltip.create(Esper.label("equip", i + 1)));
            clear[i].active = menu.slot(i) >= 0;
        }
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }
}
