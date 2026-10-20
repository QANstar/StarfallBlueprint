package cn.starfall.blueprint;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class OreIndicatorItem extends Item {
    public OreIndicatorItem(Properties p) { super(p.stacksTo(1)); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // The normal server route already checks this; direct callers must obey it as well.
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            boolean found = hasNearbyOre(player.blockPosition(), level::hasChunkAt, pos -> {
                var block = level.getBlockState(pos).getBlock();
                return block == StarfallBlueprint.ORE.get() || block == StarfallBlueprint.DEEP_ORE.get();
            });
            player.displayClientMessage(Component.translatable("message.starfall_blueprint.scan." + (found ? "found" : "empty")), true);
            player.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Query only the loaded part of the radius-eight sphere; the result exposes no location. */
    public static boolean hasNearbyOre(BlockPos origin, Predicate<BlockPos> isLoaded, Predicate<BlockPos> isOre) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -8, -8), origin.offset(8, 8, 8))) {
            // Check geometry and availability before a block read, which could otherwise load a chunk.
            if (pos.distSqr(origin) <= 64 && isLoaded.test(pos) && isOre.test(pos)) return true;
        }
        return false;
    }
}
