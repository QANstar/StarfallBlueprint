package cn.starfall.blueprint.client;

import cn.starfall.blueprint.Esper;
import cn.starfall.blueprint.EsperNetworking;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
                if (state.slots().get(i).skill() == Esper.Skill.VOLLEY.ordinal()) charging = i;
            }
        }
        if (charging >= 0) {
            if (!acceptsInput || !enabled() || toggle) {
                if (mc.getConnection() != null) PacketDistributor.sendToServer(new Esper.Cast(Esper.Cast.CANCEL_CHARGE));
                charging = -1;
            } else if (!CAST[charging].isDown()) {
                PacketDistributor.sendToServer(new Esper.Cast(Esper.Cast.RELEASE + charging));
                charging = -1;
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
            var font = mc.font;
            int x = g.guiWidth() - 141, y = 9, right = x + 132;
            boolean electric = state.school() == Esper.School.ELECTRIC.ordinal();
            int accent = electric ? 0xfff3c775 : 0xffbcebf0;
            g.fill(x, y, right, y + (electric ? 92 : 84), 0xd5142026);
            g.fill(x, y, right, y + 2, accent);
            g.drawString(font, Esper.label("hud_power"), x + 6, y + 6, accent, false);
            String power = state.power() + "/" + state.capacity();
            g.drawString(font, power, right - 6 - font.width(power), y + 6, 0xfff1f6f6, false);
            int fill = 120 * Math.clamp(state.power(), 0, Math.max(1, state.capacity())) / Math.max(1, state.capacity());
            g.fill(x + 6, y + 18, right - 6, y + 21, 0xff32464d);
            g.fill(x + 6, y + 18, x + 6 + fill, y + 21, accent);
            if (electric) g.drawString(font, Esper.label("charge", state.charge()), x + 6, y + 25, accent, false);
            for (int i = 0; i < Esper.SLOTS; i++) {
                var slot = state.slots().get(i);
                int row = y + (electric ? 37 : 27) + i * 13;
                int ticks = Math.max(0, slot.ticks() - age);
                int color = ticks > 0 ? 0xffa2b5b8 : 0xffe9f5f2;
                g.fill(x + 6, row, right - 6, row + 12, 0x8842535b);
                String key = font.plainSubstrByWidth(CAST[i].getTranslatedKeyMessage().getString(), 29);
                g.drawString(font, key, x + 9, row + 2, accent, false);
                if (slot.skill() < 0 || slot.skill() >= Esper.SKILLS.length) {
                    g.drawString(font, "-", x + 42, row + 2, 0xff789098, false);
                    continue;
                }
                var skill = Esper.SKILLS[slot.skill()];
                String cooldown = ticks > 0 ? String.format(java.util.Locale.ROOT, "%.1f", ticks / 20F) : "";
                int cooldownWidth = font.width(cooldown);
                g.drawString(font, font.plainSubstrByWidth(skill.title().getString(), 81 - cooldownWidth),
                        x + 41, row + 2, color, false);
                if (ticks > 0) {
                    int cooldownFill = 120 * ticks / Math.max(ticks, skill.cooldownAt(slot.rank()));
                    g.fill(x + 6, row + 11, x + 6 + cooldownFill, row + 12, accent);
                    g.drawString(font, cooldown, right - 8 - cooldownWidth, row + 2, color, false);
                }
            }
        });
    }
    private EsperClient() {}
}
