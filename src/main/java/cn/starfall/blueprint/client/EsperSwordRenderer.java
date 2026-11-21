package cn.starfall.blueprint.client;

import cn.starfall.blueprint.EsperProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;

/** Draw the actual synchronized payload, including modded item models. */
final class EsperSwordRenderer extends EntityRenderer<EsperProjectile> {
    private final net.minecraft.client.renderer.entity.ItemRenderer items;
    EsperSwordRenderer(EntityRendererProvider.Context context) { super(context); items = context.getItemRenderer(); }
    @Override public void render(EsperProjectile shot, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(shot,yaw,partial,pose,buffers,light);
        pose.pushPose();
        var motion = shot.getDeltaMovement().normalize();
        if (motion.lengthSqr() > .001) pose.mulPose(new Quaternionf().rotationTo(0,0,1,(float)motion.x,(float)motion.y,(float)motion.z));
        items.renderStatic(shot.getItem(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, shot.level(), shot.getId());
        pose.popPose();
    }
    @Override public ResourceLocation getTextureLocation(EsperProjectile shot) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}
