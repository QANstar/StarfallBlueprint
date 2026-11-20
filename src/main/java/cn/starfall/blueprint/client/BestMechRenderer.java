package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class BestMechRenderer extends MobRenderer<BestMechEntity, BestMechModel> {
    public BestMechRenderer(EntityRendererProvider.Context context) {
        super(context, new BestMechModel(context.bakeLayer(BestMechModel.LAYER)), .85f);
    }

    @Override
    protected void scale(BestMechEntity entity, PoseStack pose, float partialTick) {
        // The three rigs are already authored at world scale. Use gameplay dimensions only for the shadow.
        shadowRadius = entity.getBbWidth() * .47f;
    }

    @Override
    public ResourceLocation getTextureLocation(BestMechEntity entity) {
        return BestMechModel.PALETTE_TEXTURE;
    }
}
