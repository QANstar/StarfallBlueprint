package cn.starfall.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class OreIndicatorItem extends Item {
    public OreIndicatorItem(Properties p) { super(p.stacksTo(1)); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            boolean found = false;
            for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-8,-8,-8), player.blockPosition().offset(8,8,8))) {
                if (pos.distSqr(player.blockPosition()) > 64 || !level.hasChunkAt(pos)) continue;
                var block = level.getBlockState(pos).getBlock();
                if (block == StarfallBlueprint.ORE.get() || block == StarfallBlueprint.DEEP_ORE.get()) { found = true; break; }
            }
            player.displayClientMessage(Component.translatable("message.starfall_blueprint.scan." + (found ? "found" : "empty")), true);
            player.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
