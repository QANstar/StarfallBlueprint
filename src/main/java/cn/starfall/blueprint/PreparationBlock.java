package cn.starfall.blueprint;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

public final class PreparationBlock extends BaseEntityBlock {
    public static final MapCodec<PreparationBlock> CODEC = simpleCodec(PreparationBlock::new);
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public PreparationBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(FACING, net.minecraft.core.Direction.NORTH)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(ACTIVE, FACING); }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PreparationBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, StarfallBlueprint.PREPARATION_ENTITY.get(), PreparationBlockEntity::tick);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PreparationBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.is(Items.WATER_BUCKET) && be.water <= 3000) {
            if (!level.isClientSide) { be.water += 1000; be.setChanged(); if (!player.isCreative()) player.setItemInHand(hand, new ItemStack(Items.BUCKET)); }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(Items.BUCKET) && player.isShiftKeyDown() && be.water >= 1000) {
            if (!level.isClientSide) { be.water -= 1000; be.setChanged(); player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET))); }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(StarfallBlueprint.CONNECTOR.get()) && be.getItem(4).isEmpty()) {
            if (!level.isClientSide) {
                be.setItem(4, stack.copyWithCount(1));
                if (!player.isCreative()) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PreparationBlockEntity be) {
            player.openMenu(be);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack machine = new ItemStack(StarfallBlueprint.BENCH_ITEM.get());
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof PreparationBlockEntity be)
            machine.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(be.saveWithFullMetadata(params.getLevel().registryAccess())));
        return List.of(machine);
    }
}
