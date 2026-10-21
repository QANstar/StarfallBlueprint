package cn.starfall.blueprint;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class CampWaterBlock extends BaseEntityBlock {
    public static final MapCodec<CampWaterBlock> CODEC=simpleCodec(CampWaterBlock::new);
    public CampWaterBlock(Properties p) { super(p); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new CampWaterBlockEntity(p,s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t) { return l.isClientSide ? null : createTickerHelper(t,StarfallBlueprint.WATER_ENTITY.get(),CampWaterBlockEntity::tick); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit) {
        if (!(l.getBlockEntity(pos) instanceof CampWaterBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        boolean otherFluid=!be.tank.isEmpty()&&!be.tank.getFluid().is(net.minecraft.world.level.material.Fluids.WATER)
                ||stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM)!=null&&!stack.is(Items.WATER_BUCKET)&&!stack.is(Items.BUCKET);
        if(otherFluid){return net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(p,hand,l,pos,hit.getDirection())?ItemInteractionResult.sidedSuccess(l.isClientSide):ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
        if (stack.is(Items.WATER_BUCKET) && be.tank.getFluidAmount()<=3000) {
            if (!l.isClientSide) { be.tank.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000),FluidAction.EXECUTE); if (!p.isCreative()) p.setItemInHand(hand,new ItemStack(Items.BUCKET)); }
            return ItemInteractionResult.sidedSuccess(l.isClientSide);
        }
        if (stack.is(Items.BUCKET) && be.tank.getFluidAmount()>=1000) {
            if (!l.isClientSide) { be.tank.drain(1000,FluidAction.EXECUTE); p.setItemInHand(hand,ItemUtils.createFilledResult(stack,p,new ItemStack(Items.WATER_BUCKET))); }
            return ItemInteractionResult.sidedSuccess(l.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos pos,Player p,BlockHitResult hit) {
        if (!l.isClientSide && l.getBlockEntity(pos) instanceof CampWaterBlockEntity be) p.displayClientMessage(Component.translatable("message.starfall_blueprint.water",be.tank.getFluidAmount()),true);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override public List<ItemStack> getDrops(BlockState s,LootParams.Builder params) {
        ItemStack stack=new ItemStack(s.is(StarfallBlueprint.RAIN_COLLECTOR.get()) ? StarfallBlueprint.RAIN_ITEM.get() : StarfallBlueprint.TANK_ITEM.get());
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CampWaterBlockEntity be) stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(be.saveWithFullMetadata(params.getLevel().registryAccess())));
        return List.of(stack);
    }
}
