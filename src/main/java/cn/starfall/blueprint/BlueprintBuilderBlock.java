package cn.starfall.blueprint;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

public final class BlueprintBuilderBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public BlueprintBuilderBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(BlueprintBuilderBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new BlueprintBuilderEntity(pos,state);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide&&level.getBlockEntity(pos)instanceof BlueprintBuilderEntity builder)player.openMenu(builder);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.is(Blueprints.PLAN.get())&&level.getBlockEntity(pos)instanceof BlueprintBuilderEntity builder&&builder.getItem(0).isEmpty()){
            if(!level.isClientSide){builder.setItem(0,stack.split(1));player.openMenu(builder);}return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder params){
        ItemStack stack=new ItemStack(Blueprints.BUILDER_ITEM.get());
        if(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)instanceof BlueprintBuilderEntity builder)stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(builder.saveWithFullMetadata(params.getLevel().registryAccess())));
        return List.of(stack);
    }
}
