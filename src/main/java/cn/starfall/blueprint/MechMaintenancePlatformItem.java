package cn.starfall.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** One item places nine real collision blocks; preflight prevents partial placement. */
public final class MechMaintenancePlatformItem extends BlockItem {
    public MechMaintenancePlatformItem(Block block, Properties properties) { super(block, properties); }
    @Override public InteractionResult place(BlockPlaceContext context) {
        BlockPos center = context.getClickedPos();
        var level = context.getLevel();
        for (int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
            BlockPos p=center.offset(x,0,z);
            if(context.getPlayer()==null || !level.hasChunkAt(p) || !level.isInWorldBounds(p) || !level.getBlockState(p).canBeReplaced()
                    || !level.getBlockState(p.below()).isFaceSturdy(level,p.below(),net.minecraft.core.Direction.UP)
                    || !context.getPlayer().mayUseItemAt(p,context.getClickedFace(),context.getItemInHand())
                    || !level.mayInteract(context.getPlayer(),p)
                    || !level.noCollision(net.minecraft.world.phys.shapes.Shapes.block().bounds().move(p))) return InteractionResult.FAIL;
        }
        InteractionResult result=super.place(context);
        if(result.consumesAction() && !level.isClientSide && level.getBlockEntity(center) instanceof MechMaintenancePlatformBlockEntity core) {
            core.configure(center, context.getPlayer()==null?null:context.getPlayer().getUUID());
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) if(x!=0||z!=0) {
                BlockPos p=center.offset(x,0,z);
                level.setBlock(p,getBlock().defaultBlockState(),3);
                if(level.getBlockEntity(p) instanceof MechMaintenancePlatformBlockEntity part) part.configure(center,core.wirelessOwner());
            }
        }
        return result;
    }
}
