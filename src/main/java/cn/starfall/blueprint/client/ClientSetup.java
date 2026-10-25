package cn.starfall.blueprint.client;

import cn.starfall.blueprint.StarfallBlueprint;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = StarfallBlueprint.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent public static void fluids(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event){
        cn.starfall.blueprint.CatalogueFluids.ENTRIES.forEach((key,entry)->event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions(){
            public net.minecraft.resources.ResourceLocation getStillTexture(){return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_still");}
            public net.minecraft.resources.ResourceLocation getFlowingTexture(){return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_flow");}
            public int getTintColor(){return switch(key){case "warm"->0xffbca88a;case "wastewater"->0xff837a61;case "bio"->0xffa6bf59;default->0xff699997;};}
        },entry.type().get()));
    }
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){event.enqueueWork(()->{
        BlueprintScreen.installExamples();
        if(Boolean.getBoolean("starfall.visualCheck"))net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(IndustryVisualCheck::tick);
        cn.starfall.blueprint.CatalogueFluids.ENTRIES.values().forEach(e->{net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(e.source().get(),net.minecraft.client.renderer.RenderType.translucent());net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(e.flowing().get(),net.minecraft.client.renderer.RenderType.translucent());});
        net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(((net.minecraft.world.item.BlockItem)cn.starfall.blueprint.CatalogueContent.item("boroglass")).getBlock(),net.minecraft.client.renderer.RenderType.translucent());
        net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(cn.starfall.blueprint.RopeSystem.ROPE.get(),net.minecraft.client.renderer.RenderType.cutout());
    });}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(cn.starfall.blueprint.Blueprints.MENU.get(),BlueprintScreen::new);event.register(StarfallBlueprint.PREPARATION_MENU.get(), PreparationScreen::new); event.register(StarfallBlueprint.ALLOY_MENU.get(), AlloyFurnaceScreen::new); event.register(cn.starfall.blueprint.CatalogueContent.MENU.get(), IndustryScreen::new); event.register(cn.starfall.blueprint.CatalogueEquipment.BAG_MENU.get(), MiningBagScreen::new); }
}
