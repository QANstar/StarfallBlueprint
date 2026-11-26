package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechArms;
import cn.starfall.blueprint.BestMechEntity;
import cn.starfall.blueprint.BestMechItem;
import cn.starfall.blueprint.BestMechProgression;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Renders 3x3 area-excavation previews while piloting and chassis footprint previews when deploying. */
@EventBusSubscriber(modid = StarfallBlueprint.ID, value = Dist.CLIENT)
public final class BestMechOverlayRenderer {
    private BestMechOverlayRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (mc.player.getVehicle() instanceof BestMechEntity mech && mech.isAlive() && mech.engineeringCapable() && !mech.workArmKey().isEmpty()) {
            renderExcavationPreview(event.getPoseStack(), mc, mech);
        } else if (mc.player.getMainHandItem().getItem() instanceof BestMechItem item && !mc.player.isShiftKeyDown()) {
            renderDeployPreview(event.getPoseStack(), mc, item);
        }
    }

    private static void renderExcavationPreview(PoseStack pose, Minecraft mc, BestMechEntity mech) {
        Vec3 from = mc.player.getEyePosition(mc.getTimer().getGameTimeDeltaPartialTick(true));
        double reach = Math.max(mech.workReach(), mc.player.blockInteractionRange());
        BlockHitResult hit = mc.level.clip(new ClipContext(from, from.add(mc.player.getLookAngle().scale(reach)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos center = hit.getBlockPos();
        boolean area = !mc.player.isShiftKeyDown() && mech.areaEngineeringCapable();
        boolean excavator = BestMechArms.EXCAVATOR.equals(mech.armKey()) || BestMechArms.HEAVY_EXCAVATOR.equals(mech.armKey());

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        if (!area) {
            drawBlockBox(pose, lines, mc.level, center, excavator, true);
        } else {
            Direction.Axis axis = hit.getDirection().getAxis();
            for (int row = -1; row <= 1; row++) {
                for (int col = -1; col <= 1; col++) {
                    BlockPos pos = switch (axis) {
                        case X -> center.offset(0, col, row);
                        case Y -> center.offset(col, 0, row);
                        case Z -> center.offset(col, row, 0);
                    };
                    double x = Mth.clamp(from.x, pos.getX(), pos.getX() + 1.0);
                    double y = Mth.clamp(from.y, pos.getY(), pos.getY() + 1.0);
                    double z = Mth.clamp(from.z, pos.getZ(), pos.getZ() + 1.0);
                    boolean inReach = from.distanceToSqr(x, y, z) <= reach * reach;
                    drawBlockBox(pose, lines, mc.level, pos, excavator, inReach);
                }
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void drawBlockBox(PoseStack pose, VertexConsumer lines, Level level, BlockPos pos, boolean excavator, boolean inReach) {
        var state = level.getBlockState(pos);
        if (state.isAir()) return;
        boolean valid = inReach && !state.hasBlockEntity() && level.getFluidState(pos).isEmpty()
                && state.getDestroySpeed(level, pos) >= 0 && (!excavator || state.is(BlockTags.MINEABLE_WITH_SHOVEL));
        AABB box = new AABB(pos).inflate(0.004);
        if (valid) {
            LevelRenderer.renderLineBox(pose, lines, box, 0.35f, 0.92f, 0.85f, 0.85f);
        } else {
            LevelRenderer.renderLineBox(pose, lines, box, 0.92f, 0.35f, 0.35f, 0.45f);
        }
    }

    private static void renderDeployPreview(PoseStack pose, Minecraft mc, BestMechItem item) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
        float width = BestMechProgression.UNIVERSAL.equals(item.platformType()) ? 2.6f : 1.8f;
        float height = BestMechProgression.UNIVERSAL.equals(item.platformType()) ? 4.64f
                : BestMechProgression.COMBAT.equals(item.platformType()) ? 4.20f : 3.68f;
        double cx = pos.getX() + 0.5, cy = pos.getY(), cz = pos.getZ() + 0.5;
        AABB box = new AABB(cx - width / 2.0, cy, cz - width / 2.0, cx + width / 2.0, cy + height, cz + width / 2.0);
        boolean clear = mc.level.noCollision(box);
        if (clear) {
            for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX - 1e-6, box.maxY - 1e-6, box.maxZ - 1e-6))) {
                if (!mc.level.getFluidState(cell).isEmpty()) { clear = false; break; }
            }
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        if (clear) LevelRenderer.renderLineBox(pose, lines, box, 0.38f, 0.92f, 0.76f, 0.85f);
        else LevelRenderer.renderLineBox(pose, lines, box, 0.95f, 0.32f, 0.32f, 0.85f);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
