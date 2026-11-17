package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechDrones;
import cn.starfall.blueprint.StarfallBlueprint;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Reuses the existing Black Dog drone mesh, texture and registered model layer. */
@EventBusSubscriber(modid = StarfallBlueprint.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BestMechDroneClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BestMechDrones.TYPE.get(), context -> new BlackDogHardwareRenderer<>(context, false));
    }
    private BestMechDroneClient() {}
}
