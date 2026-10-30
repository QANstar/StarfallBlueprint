package cn.starfall.blueprint.client;

import cn.starfall.blueprint.StarfallBlueprint;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = StarfallBlueprint.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent public static void armorLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event){event.registerLayerDefinition(BlackDogArmorModel.LAYER,BlackDogArmorModel::layer);}
    @SubscribeEvent public static void armor(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event){
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions(){
            private BlackDogArmorModel model;
            @Override @SuppressWarnings({"rawtypes","unchecked"}) public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(net.minecraft.world.entity.LivingEntity entity,net.minecraft.world.item.ItemStack stack,net.minecraft.world.entity.EquipmentSlot slot,net.minecraft.client.model.HumanoidModel<?> original){
                if(model==null)model=new BlackDogArmorModel(net.minecraft.client.Minecraft.getInstance().getEntityModels().bakeLayer(BlackDogArmorModel.LAYER));
                ((net.minecraft.client.model.HumanoidModel)original).copyPropertiesTo(model);model.prepare(slot,cn.starfall.blueprint.CatalogueEquipment.getData(stack));return model;
            }
        },cn.starfall.blueprint.CatalogueContent.item("blackdog_armor_head"),cn.starfall.blueprint.CatalogueContent.item("blackdog_armor_chest"),cn.starfall.blueprint.CatalogueContent.item("blackdog_armor_legs"),cn.starfall.blueprint.CatalogueContent.item("blackdog_armor_feet"));
    }
    @SubscribeEvent public static void mechRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(cn.starfall.blueprint.BestMechs.TYPE.get(), BestMechRenderer::new); }
    @SubscribeEvent public static void mechLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) { event.registerLayerDefinition(BestMechModel.LAYER, BestMechModel::createLayer); }
    @SubscribeEvent public static void mechKeys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) { BestMechClient.keys(event); }
    @SubscribeEvent public static void mechHud(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) { BestMechClient.layers(event); }
    @SubscribeEvent public static void fluids(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event){
        cn.starfall.blueprint.CatalogueFluids.ENTRIES.forEach((key,entry)->event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions(){
            public net.minecraft.resources.ResourceLocation getStillTexture(){return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_still");}
            public net.minecraft.resources.ResourceLocation getFlowingTexture(){return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_flow");}
            public int getTintColor(){return switch(key){case "warm"->0xffbca88a;case "wastewater"->0xff837a61;case "bio"->0xffa6bf59;default->0xff699997;};}
        },entry.type().get()));
    }
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){event.enqueueWork(()->{
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BlackDogClient::render);
        if(Boolean.getBoolean("starfall.lingchuanVisualCheck"))net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(LingchuanVisualCheck::tick);
        BlueprintScreen.installExamples();
        if(Boolean.getBoolean("starfall.visualCheck"))net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(IndustryVisualCheck::tick);
        cn.starfall.blueprint.CatalogueFluids.ENTRIES.values().forEach(e->{net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(e.source().get(),net.minecraft.client.renderer.RenderType.translucent());net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(e.flowing().get(),net.minecraft.client.renderer.RenderType.translucent());});
        net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(((net.minecraft.world.item.BlockItem)cn.starfall.blueprint.CatalogueContent.item("boroglass")).getBlock(),net.minecraft.client.renderer.RenderType.translucent());
        net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(cn.starfall.blueprint.RopeSystem.ROPE.get(),net.minecraft.client.renderer.RenderType.cutout());
    });}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(cn.starfall.blueprint.Blueprints.MENU.get(),BlueprintScreen::new);event.register(StarfallBlueprint.PREPARATION_MENU.get(), PreparationScreen::new); event.register(StarfallBlueprint.ALLOY_MENU.get(), AlloyFurnaceScreen::new); event.register(cn.starfall.blueprint.CatalogueContent.MENU.get(), IndustryScreen::new); event.register(cn.starfall.blueprint.CatalogueEquipment.BAG_MENU.get(), MiningBagScreen::new); event.register(cn.starfall.blueprint.BestEngineering.MENU.get(), BestWorkbenchScreen::new); event.register(cn.starfall.blueprint.BestMechs.CARGO_MENU.get(), BestMechCargoScreen::new); event.register(cn.starfall.blueprint.Lingchuan.MENU.get(),LingchuanScreen::new); }
}
