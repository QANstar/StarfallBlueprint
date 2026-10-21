package cn.starfall.blueprint;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.*;
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

public final class AlloyFurnaceBlock extends BaseEntityBlock {
    public static final MapCodec<AlloyFurnaceBlock> CODEC = simpleCodec(AlloyFurnaceBlock::new);
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public AlloyFurnaceBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(FACING, Direction.NORTH)); }
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(ACTIVE, FACING); }
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    protected BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
    protected BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }
    protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new AlloyFurnaceBlockEntity(p, s); }
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> type) {
        return l.isClientSide ? null : createTickerHelper(type, StarfallBlueprint.ALLOY_ENTITY.get(), AlloyFurnaceBlockEntity::tick);
    }
    protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (!l.isClientSide && l.getBlockEntity(p) instanceof AlloyFurnaceBlockEntity be) player.openMenu(be);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    public List<ItemStack> getDrops(BlockState s, LootParams.Builder params) {
        ItemStack item = new ItemStack(StarfallBlueprint.ALLOY_ITEM.get());
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof AlloyFurnaceBlockEntity be)
            item.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(be.saveWithFullMetadata(params.getLevel().registryAccess())));
        return List.of(item);
    }
}
