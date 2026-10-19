package cn.starfall.blueprint.client;

import cn.starfall.blueprint.StarfallBlueprint;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = StarfallBlueprint.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(StarfallBlueprint.PREPARATION_MENU.get(), PreparationScreen::new); }
}
