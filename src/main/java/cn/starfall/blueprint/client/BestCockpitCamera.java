package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechEntity;
import cn.starfall.blueprint.StarfallBlueprint;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** The pilot's eye at the internal seat is also the origin for server-side aiming. */
@EventBusSubscriber(modid = StarfallBlueprint.ID, value = Dist.CLIENT)
public final class BestCockpitCamera {
    private static CameraType previousView;

    private static BestMechEntity cockpit(Player player) {
        return player != null && player.isAlive() && player.getVehicle() instanceof BestMechEntity mech
                && mech.isAlive() && mech.getControllingPassenger() == player ? mech : null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (cockpit(mc.player) == null) {
            restoreView();
        } else {
            if (previousView == null) previousView = mc.options.getCameraType();
            if (!mc.options.getCameraType().isFirstPerson()) mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    private static void restoreView() {
        if (previousView != null) {
            Minecraft.getInstance().options.setCameraType(previousView);
            previousView = null;
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { restoreView(); }

    @SubscribeEvent
    public static void hidePilot(RenderPlayerEvent.Pre event) {
        if (cockpit(event.getEntity()) != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hideHands(RenderHandEvent event) {
        if (cockpit(Minecraft.getInstance().player) != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hideExterior(RenderLivingEvent.Pre<?, ?> event) {
        var mc = Minecraft.getInstance();
        if (mc.getCameraEntity() == mc.player && mc.options.getCameraType().isFirstPerson()
                && event.getEntity() == cockpit(mc.player)) event.setCanceled(true);
    }
}
