package cn.starfall.blueprint.client;

import cn.starfall.blueprint.Esper;
import cn.starfall.blueprint.EsperNetworking;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = StarfallBlueprint.ID, value = Dist.CLIENT)
public final class EsperClient {
    static final KeyMapping[] CAST = new KeyMapping[Esper.SLOTS];
    private static final KeyMapping MODE = new KeyMapping("esper.key.mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "esper.name");
    private static EsperNetworking.Hud state;
    private static int age;
    private static int charging = -1;
    static {
        for (int i = 0; i < CAST.length; i++) CAST[i] = new KeyMapping("esper.key." + (i + 1),
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6 + i, "esper.name");
    }
    static void keys(RegisterKeyMappingsEvent event) {
        event.register(MODE);
        for (var key : CAST) event.register(key);
    }
    public static void receive(EsperNetworking.Hud update) { state = update; age = 0; }
    private static boolean enabled() { return state != null && state.enabled() && age <= 24; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isAlive()) state = null;
        if (age < 25) age++;
        boolean acceptsInput = mc.player != null && mc.player.isAlive() && mc.screen == null && !mc.player.isSpectator();
        boolean toggle = pressed(MODE) && acceptsInput;
        if (toggle) PacketDistributor.sendToServer(new Esper.Cast(Esper.Cast.TOGGLE));
        for (int i = 0; i < CAST.length; i++) {
            if (pressed(CAST[i]) && acceptsInput && enabled() && !toggle) {
                PacketDistributor.sendToServer(new Esper.Cast(i));
                if (state.slots().get(i).skill() >= 0 && Esper.SKILLS[state.slots().get(i).skill()].channelled()) charging = i;
            }
        }
        if (charging >= 0) {
            if (!acceptsInput || !enabled() || toggle) {
                if (mc.getConnection() != null) PacketDistributor.sendToServer(new Esper.Cast(Esper.Cast.CANCEL_CHARGE));
                charging = -1;
            } else if (!CAST[charging].isDown()) {
                PacketDistributor.sendToServer(new Esper.Cast(Esper.Cast.RELEASE + charging));
                charging = -1;
            } else if (mc.player.tickCount % 4 == 0) {
                PacketDistributor.sendToServer(new Esper.Cast(Esper.Cast.HOLD + charging));
            }
        }
    }
    private static boolean pressed(KeyMapping key) {
        boolean result = false;
        while (key.consumeClick()) result = true;
        return result;
    }
    static void hud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(StarfallBlueprint.id("esper_mode"), (g, partial) -> {
            var mc = Minecraft.getInstance();
            if (!enabled() || mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()
                    || mc.screen != null || mc.options.hideGui) return;
            drawHud(g, state, age, charging);
        });
    }

    static void drawHud(GuiGraphics g, EsperNetworking.Hud hud, int age, int pressedSlot) {
        var font = Minecraft.getInstance().font;
        boolean electric = hud.school() == Esper.School.ELECTRIC.ordinal();
        int accent = electric ? 0xff78bdff : 0xffb1e9e6;
        int right = g.guiWidth() - 8, left = right - 104, top = 8;
        g.fill(left, top, right, top + 15, 0xd41a2530);
        g.fill(left, top, left + 2, top + 15, accent);
        String power = hud.power() + "/" + hud.capacity();
        int powerX = right - font.width(power) - 4;
        g.drawString(font, power, powerX, top + 3, 0xfff3f6f7, false);
        String charge = electric ? "\u26A1" + hud.charge() : "";
        int chargeX = powerX - font.width(charge) - 5;
        int meter = Math.min(45, (electric ? chargeX : powerX) - left - 10);
        if (meter > 0) {
            int start = left + 6;
            int fill = meter * Math.clamp(hud.power(), 0, Math.max(1, hud.capacity())) / Math.max(1, hud.capacity());
            g.fill(start, top + 6, start + meter, top + 9, 0xff455565);
            g.fill(start, top + 6, start + fill, top + 9, accent);
        }
        if (electric) g.drawString(font, charge, chargeX, top + 3, 0xffffcd79, false);
        for (int i = 0; i < Esper.SLOTS; i++) {
            int x = left + i * 27, y = top + 18;
            var slot = hud.slots().get(i);
            int ticks = Math.max(0, slot.ticks() - age);
            g.fill(x, y, x + 23, y + 30, 0xd41a2530);
            g.fill(x + 1, y + 1, x + 22, y + 21, i == pressedSlot ? 0xff3d666e : 0xc82a3a49);
            if (slot.skill() >= 0 && slot.skill() < Esper.SKILLS.length) {
                var skill = Esper.SKILLS[slot.skill()];
                EsperSkillIcons.draw(g, skill, x + 4, y + 3);
                if (ticks > 0) {
                    int remaining = 19 * ticks / Math.max(ticks, skill.cooldownAt(slot.rank()));
                    g.fill(x + 2, y + 2, x + 21, y + 2 + remaining, 0x98202b38);
                    g.fill(x + 2, y + 19, x + 2 + remaining, y + 21, accent);
                } else g.fill(x + 2, y + 20, x + 21, y + 21, accent);
            } else g.fill(x + 10, y + 10, x + 13, y + 13, 0xff657784);
            String key = font.plainSubstrByWidth(CAST[i].getTranslatedKeyMessage().getString(), 21);
            g.drawString(font, key, x + (23 - font.width(key)) / 2, y + 22, 0xffc6d7e3, false);
        }
    }

    private EsperClient() {}
}
