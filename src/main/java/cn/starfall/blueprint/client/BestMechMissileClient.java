package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechMissiles;
import cn.starfall.blueprint.StarfallBlueprint;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Reuses the existing finned missile model and texture; no new assets are needed. */
@EventBusSubscriber(modid=StarfallBlueprint.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BestMechMissileClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BestMechMissiles.TYPE.get(), context -> new BlackDogHardwareRenderer<>(context,true));
    }
    private BestMechMissileClient() {}
}
