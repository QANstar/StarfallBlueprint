package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechEntity;
import cn.starfall.blueprint.BestMechNetworking;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Presentation only: all operation permission and resource costs are server authoritative. */
@EventBusSubscriber(modid=StarfallBlueprint.ID,value=Dist.CLIENT)
public final class BestMechClient {
    private static final KeyMapping DRILL = new KeyMapping("key.starfall_blueprint.mech_drill",KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_G,"key.categories.starfall_blueprint");
    // This rate-limits requests, not successful server drilling. No unsynchronized entity fields are read.
    private static int requestTicks;
    private static BestMechEntity lastVehicle;

    private BestMechClient() {}
    public static void keys(RegisterKeyMappingsEvent event) { event.register(DRILL); }
    public static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(StarfallBlueprint.ID,"mech_status"),(graphics,delta)->render(graphics));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        BestMechEntity mech = mc.player != null && mc.player.getVehicle() instanceof BestMechEntity vehicle ? vehicle : null;
        if (mech != lastVehicle) { requestTicks=0; lastVehicle=mech; }
        if (!mc.isPaused() && requestTicks>0) requestTicks--;
        while (DRILL.consumeClick()) {
            if (mech != null && mc.screen == null && !mc.isPaused() && requestTicks == 0) {
                PacketDistributor.sendToServer(new BestMechNetworking.Action(0));
                requestTicks=20;
            }
        }
    }

    static final int PANEL_WIDTH = 248, PANEL_HEIGHT = 140;
    private static final int TEXT = 0xffe6efeb, MUTED = 0xff9aafb5;
    private static final int NORMAL = 0xff75d9c0, WARNING = 0xffefb45f;
    private static final int DANGER = 0xfff16f70, ISOLATED = 0xffb0a0ed, WORN = 0xffd88b61;
    private static final String PANEL_KEY = "hud.starfall_blueprint.mech.panel.";

    /** Immutable synchronized inputs. requestTicks is only the local request throttle, never drill cooldown. */
    static record Snapshot(float health, float maxHealth, int energy, int heat, int wear,
                           boolean alive, boolean isolated, boolean thermalLocked, boolean qualified,
                           int requestTicks) {}

    private static void render(GuiGraphics graphics) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.options.hideGui || mc.player==null || !(mc.player.getVehicle() instanceof BestMechEntity mech))return;
        Snapshot sample = new Snapshot(mech.getHealth(), mech.getMaxHealth(), mech.energy(), mech.heat(), mech.wear(),
                mech.isAlive(), mech.isolated(), mech.thermalLocked(), mech.operatorQualified(), requestTicks);
        renderPanel(graphics, 8, 8, sample);
    }

    /** Worldless fixture contract: caller supplies position and actual sample; bounds are 248 by 140. */
    static void renderPanel(GuiGraphics graphics, int x, int y, Snapshot sample) {
        Minecraft mc = Minecraft.getInstance();
        PanelStatus status = status(sample);
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xed111e26);
        graphics.fill(x, y, x + 3, y + PANEL_HEIGHT, status.color());
        graphics.fill(x + 3, y, x + PANEL_WIDTH, y + 20, 0xff22333d);
        graphics.fill(x + 8, y + 20, x + 240, y + 21, 0xff42555b);
        drawFit(graphics, label("title"), x + 9, y + 6, 230, TEXT);
        int lineY = y + 25;
        for(var line : mc.font.split(label(status.key()), 230)) {
            if(lineY >= y + 43) break;
            graphics.drawString(mc.font, line, x + 9, lineY, status.color(), false);
            lineY += 9;
        }
        resource(graphics, x, y + 46, "health", decimal(sample.health()) + " / " + decimal(sample.maxHealth()),
                sample.health(), sample.maxHealth(), sample.health() <= sample.maxHealth() * 0.25f ? DANGER : NORMAL);
        resource(graphics, x, y + 63, "energy", compactEnergy(sample.energy()) + " / " + compactEnergy(BestMechEntity.CAPACITY) + " XH",
                sample.energy(), BestMechEntity.CAPACITY, sample.energy() < 400 ? DANGER : sample.energy() < BestMechEntity.CAPACITY / 10 ? WARNING : NORMAL);
        resource(graphics, x, y + 80, "heat", sample.heat() + " / " + BestMechEntity.MAX_HEAT,
                sample.heat(), BestMechEntity.MAX_HEAT, sample.thermalLocked() || sample.heat() >= BestMechEntity.MAX_HEAT ? DANGER : sample.heat() >= 800 ? WARNING : NORMAL);
        resource(graphics, x, y + 97, "wear", sample.wear() + " / " + BestMechEntity.MAX_WEAR,
                sample.wear(), BestMechEntity.MAX_WEAR, sample.wear() >= BestMechEntity.MAX_WEAR ? WORN : sample.wear() >= 2160 ? WARNING : NORMAL);
        Component request = sample.requestTicks() > 0
                ? label("request", DRILL.getTranslatedKeyMessage(), sample.requestTicks())
                : label("standby", DRILL.getTranslatedKeyMessage());
        drawFit(graphics, request, x + 9, y + 116, 230, sample.requestTicks() > 0 ? WARNING : TEXT);
        drawFit(graphics, label("controls", mc.options.keyUp.getTranslatedKeyMessage(), mc.options.keyShift.getTranslatedKeyMessage()),
                x + 9, y + 129, 230, MUTED);
    }

    private record PanelStatus(String key, int color) {}

    /** System readiness only; no claim of movement, a valid drill target, or a successful request. */
    private static PanelStatus status(Snapshot sample) {
        if(!sample.alive() || sample.health() <= 0) return new PanelStatus("destroyed", DANGER);
        if(sample.isolated()) return new PanelStatus("isolated", ISOLATED);
        if(sample.thermalLocked() || sample.heat() >= BestMechEntity.MAX_HEAT) return new PanelStatus("thermal_locked", DANGER);
        if(sample.wear() >= BestMechEntity.MAX_WEAR) return new PanelStatus("worn_out", WORN);
        if(!sample.qualified()) return new PanelStatus("unqualified", ISOLATED);
        if(sample.energy() < 400) return new PanelStatus("no_power", DANGER);
        if(sample.energy() < 32000) return new PanelStatus("drill_power", WARNING);
        if(sample.heat() >= 800) return new PanelStatus("hot", WARNING);
        if(sample.energy() < BestMechEntity.CAPACITY / 10) return new PanelStatus("low_power", WARNING);
        if(sample.wear() >= 2160) return new PanelStatus("worn", WARNING);
        return new PanelStatus("ready", NORMAL);
    }

    private static void resource(GuiGraphics graphics, int x, int y, String key, String value,
                                 double current, double maximum, int color) {
        Minecraft mc = Minecraft.getInstance();
        drawFit(graphics, label(key), x + 9, y, 55, MUTED);
        Component number = Component.literal(value);
        int numberWidth = mc.font.width(number);
        drawFit(graphics, number, x + 240 - Math.min(numberWidth, 170), y, 170, TEXT);
        graphics.fill(x + 9, y + 11, x + 240, y + 14, 0xff34464e);
        double fraction = maximum > 0 ? Math.clamp(current / maximum, 0.0, 1.0) : 0;
        int fill = (int) Math.floor(231 * fraction);
        // Incomplete resources always retain at least one empty pixel.
        if(current < maximum) fill = Math.min(fill, 230);
        if(fill > 0) graphics.fill(x + 9, y + 11, x + 9 + fill, y + 14, color);
    }

    private static void drawFit(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        Minecraft mc = Minecraft.getInstance();
        float scale = Math.min(1.0f, (float) width / Math.max(1, mc.font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(mc.font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static Component label(String key, Object... args) {
        return Component.translatable(PANEL_KEY + key, args);
    }

    private static String decimal(float value) {
        double floored = Math.floor(Math.max(0, value) * 10.0) / 10.0;
        return floored == Math.floor(floored) ? Long.toString((long) floored) : String.format(Locale.ROOT, "%.1f", floored);
    }

    /** Truncate instead of rounding: 31,999 stays 31.999k; 19,999,999 stays below 20M. */
    private static String compactEnergy(int value) {
        int safe = Math.max(0, value);
        if(safe < 1000) return Integer.toString(safe);
        int unit = safe < 1_000_000 ? 1000 : 1_000_000;
        int thousandths = (safe % unit) / (unit / 1000);
        String fraction = thousandths == 0 ? "" : "." + String.format(Locale.ROOT, "%03d", thousandths).replaceFirst("0+$", "");
        return safe / unit + fraction + (unit == 1000 ? "k" : "M");
    }
}
