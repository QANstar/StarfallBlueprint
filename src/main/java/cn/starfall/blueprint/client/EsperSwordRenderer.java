package cn.starfall.blueprint.client;

import cn.starfall.blueprint.EsperProjectile;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

final class EsperSwordRenderer extends EntityRenderer<EsperProjectile> {
    EsperSwordRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public void render(EsperProjectile sword, float yaw, float partial, PoseStack pose,
            MultiBufferSource buffers, int light) {
        super.render(sword, yaw, partial, pose, buffers, light);
        Vec3 motion = sword.getDeltaMovement();
        Vec3 direction = motion.lengthSqr() > .0001 ? motion : sword.getLookAngle();
        var stack = sword.getItem();
        int color = stack.is(Items.TNT) ? 0xffad87 : stack.is(Items.GOLDEN_SWORD) ? 0xffe3a0
                : stack.is(Items.DIAMOND_SWORD) ? 0x8eeadb : 0xe7fcff;
        EsperSpatialGeometry.sword(buffers.getBuffer(RenderType.debugStructureQuads()), pose.last().pose(),
                Vec3.ZERO, direction, 1.09, color, true);
    }

    @Override public ResourceLocation getTextureLocation(EsperProjectile sword) {
        return StarfallBlueprint.id("textures/item/esper_retuner.png");
    }
}
