package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechEntity;
import cn.starfall.blueprint.BestMechProgression;
import cn.starfall.blueprint.StarfallBlueprint;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Defaults to a zoomed-out third-person camera on mount, while allowing free F5 switching to the enclosed cockpit. */
@EventBusSubscriber(modid = StarfallBlueprint.ID, value = Dist.CLIENT)
public final class BestCockpitCamera {
    private static CameraType previousView;
    private static BestMechEntity activeVehicle;
    private static float detachedDistance = 7.0f;

    private static BestMechEntity cockpit(Player player) {
        return player != null && player.isAlive() && player.getVehicle() instanceof BestMechEntity mech
                && mech.isAlive() && mech.getControllingPassenger() == player ? mech : null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        var mech = cockpit(mc.player);
        if (mech == null) {
            activeVehicle = null;
            restoreView();
        } else if (activeVehicle != mech) {
            activeVehicle = mech;
            if (previousView == null) previousView = mc.options.getCameraType();
            detachedDistance = baseDistance(mech);
            // Switch once on mount so the player can immediately see their mech, while F5 remains freely toggleable.
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }
    }

    private static float baseDistance(BestMechEntity mech) {
        return switch (mech.platformType()) {
            case BestMechProgression.UNIVERSAL -> 8.5f;
            case BestMechProgression.COMBAT -> 7.2f;
            default -> 6.5f;
        };
    }

    @SubscribeEvent
    public static void detachedDistance(CalculateDetachedCameraDistanceEvent event) {
        var mc = Minecraft.getInstance();
        var mech = cockpit(mc.player);
        if (mech == null) return;
        float target = baseDistance(mech) + (mech.boosting() ? 1.0f : 0.0f);
        detachedDistance = Mth.lerp(0.15f, detachedDistance, target);
        event.setDistance(detachedDistance);
    }

    private static void restoreView() {
        if (previousView != null) {
            Minecraft.getInstance().options.setCameraType(previousView);
            previousView = null;
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        activeVehicle = null;
        restoreView();
    }

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
