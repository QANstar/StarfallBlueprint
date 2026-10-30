package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechEntity;
import cn.starfall.blueprint.StarfallBlueprint;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class BestMechRenderer extends MobRenderer<BestMechEntity, BestMechModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(StarfallBlueprint.ID,"textures/entity/best_engineering_mech.png");
    public BestMechRenderer(EntityRendererProvider.Context context) {
        super(context,new BestMechModel(context.bakeLayer(BestMechModel.LAYER)),0.85F);
    }
    @Override public ResourceLocation getTextureLocation(BestMechEntity entity) { return TEXTURE; }
}
