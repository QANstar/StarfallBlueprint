package cn.starfall.blueprint;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

/** Survival-accessible stand-in for the settlement craftsman, backed by the same world account. */
public final class SupplyTerminalBlock extends Block {
    public static final MapCodec<SupplyTerminalBlock> CODEC = simpleCodec(SupplyTerminalBlock::new);
    public SupplyTerminalBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                       Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (player instanceof ServerPlayer serverPlayer) {
            if (stack.is(StarfallBlueprint.CRYSTAL.get())) {
                feedback(serverPlayer, SupplyLedger.get(serverPlayer.serverLevel()).exchange(serverPlayer, hand));
            } else if(stack.is(net.minecraft.world.item.Items.IRON_INGOT)){
                var ledger=SupplyLedger.get(serverPlayer.serverLevel());var result=ledger.importLunar(serverPlayer,hand);serverPlayer.displayClientMessage(Component.literal("月络进口："+result+" · 今日余 "+ledger.lunarRemaining(serverPlayer.serverLevel())+" / 8"),false);
            } else describe(serverPlayer);
        }
        // Handle this hand once; never fall through to a second hand or consume the held manual/block.
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            describe(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void describe(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.starfall_blueprint.supply.help",
                SupplyLedger.get(player.serverLevel()).remaining(player.serverLevel())), false);
        player.displayClientMessage(Component.literal("持1铁锭右键换1月络晶粒；全部终端共享每日8笔。剩余："+SupplyLedger.get(player.serverLevel()).lunarRemaining(player.serverLevel())),false);
    }

    private static void feedback(ServerPlayer player, SupplyLedger.Result result) {
        player.displayClientMessage(Component.translatable("message.starfall_blueprint.supply."
                + result.name().toLowerCase(Locale.ROOT)), false);
    }

    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(StarfallBlueprint.SUPPLY_TERMINAL_ITEM.get()));
    }
}
