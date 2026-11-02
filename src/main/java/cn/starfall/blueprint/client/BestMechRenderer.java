package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechEntity;
import cn.starfall.blueprint.StarfallBlueprint;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class BestMechRenderer extends MobRenderer<BestMechEntity, BestMechModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(StarfallBlueprint.ID,"textures/entity/best_engineering_mech.png");
    private static final ResourceLocation HEAVY_TEXTURE = ResourceLocation.fromNamespaceAndPath(StarfallBlueprint.ID,"textures/entity/best_heavy_engineering_mech.png");
    public BestMechRenderer(EntityRendererProvider.Context context) {
        super(context,new BestMechModel(context.bakeLayer(BestMechModel.LAYER)),0.85F);
    }
    @Override protected void scale(BestMechEntity entity, com.mojang.blaze3d.vertex.PoseStack pose, float partialTick) {
        if(entity.heavy()) pose.scale(2.6f / 1.8f, 4.4f / 3.2f, 2.6f / 1.8f);
        shadowRadius = entity.heavy() ? 1.25f : .85f;
    }
    @Override public ResourceLocation getTextureLocation(BestMechEntity entity) { return entity.heavy() ? HEAVY_TEXTURE : TEXTURE; }
}
