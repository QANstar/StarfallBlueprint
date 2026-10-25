package cn.starfall.blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
/** Proxy ports and interaction only; never owns items, energy or production ticks. */
public final class LargeMachinePartEntity extends BlockEntity {
    private IndustryBlockEntity detachedOwner;
    public LargeMachinePartEntity(BlockPos pos,BlockState state){super(AggregateMachines.PART_ENTITY.get(),pos,state);}
    public IndustryBlockEntity owner(){return detachedOwner!=null?detachedOwner:level==null?null:LargeMachineBlock.controller(level,worldPosition);}
    void rememberOwner(IndustryBlockEntity owner){detachedOwner=owner;}
}
