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
    @Override protected void scale(BestMechEntity entity, com.mojang.blaze3d.vertex.PoseStack pose, float partialTick) {
        pose.scale(entity.getBbWidth() / 1.8f, entity.getBbHeight() / 3.2f, entity.getBbWidth() / 1.8f);
        shadowRadius = entity.getBbWidth() * .47f;
    }
    @Override public ResourceLocation getTextureLocation(BestMechEntity entity) {
        if(entity.getType()==cn.starfall.blueprint.BestMechs.COMBAT_TYPE.get()) return StarfallBlueprint.id("textures/entity/best_combat_mech.png");
        if(entity.getType()==cn.starfall.blueprint.BestMechs.UNIVERSAL_TYPE.get()) return StarfallBlueprint.id("textures/entity/best_universal_mech.png");
        return TEXTURE;
    }
}
