package cn.starfall.blueprint;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class CampWaterBlockEntity extends BlockEntity {
    public final FluidTank tank=new FluidTank(4000,stack->!getBlockState().is(StarfallBlueprint.RAIN_COLLECTOR.get())||stack.is(Fluids.WATER)) { @Override protected void onContentsChanged() { setChanged(); } };
    public CampWaterBlockEntity(BlockPos pos,BlockState state) { super(StarfallBlueprint.WATER_ENTITY.get(),pos,state); }
    public static void tick(Level l,BlockPos pos,BlockState state,CampWaterBlockEntity be) {
        if (!state.is(StarfallBlueprint.RAIN_COLLECTOR.get())) return;
        if (l.isRainingAt(pos.above())) be.tank.fill(new FluidStack(Fluids.WATER,5),FluidAction.EXECUTE);
        // Local gravity outlet; no conduit graph, no remote-chunk loading, at most 100mB/s.
        if (l.getGameTime()%20!=0 || !l.hasChunkAt(pos.below()) || be.tank.isEmpty()) return;
        var target=l.getCapability(Capabilities.FluidHandler.BLOCK,pos.below(),Direction.UP);
        if (target==null) return;
        FluidStack offered=be.tank.drain(100,FluidAction.SIMULATE);
        int accepted=target.fill(offered,FluidAction.EXECUTE);
        if (accepted>0) be.tank.drain(accepted,FluidAction.EXECUTE);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider) { super.saveAdditional(tag,provider);tag.put("Tank",tank.writeToNBT(provider,new CompoundTag())); }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider) { super.loadAdditional(tag,provider);tank.readFromNBT(provider,tag.getCompound("Tank")); }
}
