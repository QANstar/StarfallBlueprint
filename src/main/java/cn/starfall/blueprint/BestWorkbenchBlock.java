package cn.starfall.blueprint;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/** A stateless workbench: materials and products stay in the player's inventory. */
public final class BestWorkbenchBlock extends Block {
    public static final MapCodec<BestWorkbenchBlock> CODEC = simpleCodec(BestWorkbenchBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BestWorkbenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                        Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, who) -> new BestWorkbenchMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
                    Component.literal("贝斯特专用装配台")));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
