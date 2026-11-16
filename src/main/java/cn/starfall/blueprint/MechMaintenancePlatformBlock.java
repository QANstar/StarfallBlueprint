package cn.starfall.blueprint;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class MechMaintenancePlatformBlock extends BaseEntityBlock {
    public static final MapCodec<MechMaintenancePlatformBlock> CODEC=simpleCodec(MechMaintenancePlatformBlock::new);
    public MechMaintenancePlatformBlock(Properties p) { super(p); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s) { return new MechMaintenancePlatformBlockEntity(p,s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t) { return l.isClientSide?null:createTickerHelper(t,MechMaintenancePlatforms.ENTITY.get(),MechMaintenancePlatformBlockEntity::tick); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit) {
        if(l.getBlockEntity(p) instanceof MechMaintenancePlatformBlockEntity part) {
            var core=part.controller();
            if(core!=null && !l.isClientSide) core.interact(player,stack);
            return ItemInteractionResult.sidedSuccess(l.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit) {
        if(!l.isClientSide && l.getBlockEntity(p) instanceof MechMaintenancePlatformBlockEntity part && part.controller()!=null) part.controller().interact(player,ItemStack.EMPTY);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override public java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        var be=params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        return be instanceof MechMaintenancePlatformBlockEntity part && part.isController()
                ? java.util.List.of(new ItemStack(MechMaintenancePlatforms.ITEM.get())) : java.util.List.of();
    }
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving) {
        if(!s.is(next.getBlock()) && l.getBlockEntity(p) instanceof MechMaintenancePlatformBlockEntity be) be.dropContents();
        super.onRemove(s,l,p,next,moving);
    }
}
