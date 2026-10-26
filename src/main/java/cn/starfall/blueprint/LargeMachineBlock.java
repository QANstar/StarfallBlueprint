package cn.starfall.blueprint;

import com.mojang.serialization.MapCodec;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

/** Real 2x2 footprints (two layers for the phase array), one inventory and one item drop. */
public final class LargeMachineBlock extends IndustryBlock {
    public static final IntegerProperty PART=IntegerProperty.create("part",0,7);
    private static final ThreadLocal<Boolean> DISMANTLING=ThreadLocal.withInitial(()->false);
    public LargeMachineBlock(String kind,Properties p){super(kind,p);registerDefaultState(defaultBlockState().setValue(PART,0));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(p->new LargeMachineBlock(kind,p));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(PART);}
    public static int parts(String kind){return kind.equals("phase_extractor")?8:4;}
    public static BlockPos offset(Direction facing,int part){return BlockPos.ZERO.relative(facing.getClockWise(),part&1).relative(facing.getOpposite(),(part>>1)&1).above(part>>2);}
    public static BlockPos anchor(BlockPos pos,BlockState state){return pos.subtract(offset(state.getValue(FACING),state.getValue(PART)));}
    public static IndustryBlockEntity controller(Level level,BlockPos pos){
        if(!level.hasChunkAt(pos))return null;var state=level.getBlockState(pos);if(!(state.getBlock()instanceof LargeMachineBlock))return null;
        BlockPos root=anchor(pos,state);if(!level.hasChunkAt(root))return null;
        return level.getBlockEntity(root)instanceof IndustryBlockEntity be&&be.getBlockState().getBlock()==state.getBlock()&&be.getBlockState().getValue(PART)==0?be:null;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var state=super.getStateForPlacement(context);var level=context.getLevel();var root=context.getClickedPos();
        for(int part=0;part<parts(kind);part++){
            BlockPos pos=root.offset(offset(state.getValue(FACING),part));
            if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.getBlockState(pos).canBeReplaced(context))return null;
            if(context.getPlayer()!=null&&(!level.mayInteract(context.getPlayer(),pos)||!context.getPlayer().mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand())))return null;
        }return state;
    }
    @Override public void setPlacedBy(Level level,BlockPos root,BlockState state,LivingEntity entity,ItemStack stack){
        super.setPlacedBy(level,root,state,entity,stack);
        if(!level.isClientSide)for(int part=1;part<parts(kind);part++)level.setBlock(root.offset(offset(state.getValue(FACING),part)),state.setValue(PART,part),3);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return state.getValue(PART)==0?new IndustryBlockEntity(pos,state):new LargeMachinePartEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return state.getValue(PART)==0?super.getTicker(level,state,type):null;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide){var owner=controller(level,pos);if(owner!=null)player.openMenu(owner);}return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder params){
        var entity=params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);if(entity==null)return List.of();
        var owner=entity instanceof IndustryBlockEntity be?be:entity instanceof LargeMachinePartEntity part?part.owner():null;if(owner==null)return List.of();
        var stack=CatalogueContent.stack(kind,1);stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(owner.saveWithFullMetadata(params.getLevel().registryAccess())));return List.of(stack);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof LargeMachinePartEntity part&&level.getBlockEntity(anchor(pos,state)) instanceof IndustryBlockEntity owner)part.rememberOwner(owner);
        if(!state.is(next.getBlock())&&!level.isClientSide&&!DISMANTLING.get()){
            DISMANTLING.set(true);
            try{BlockPos root=anchor(pos,state);for(int part=0;part<parts(kind);part++){
                BlockPos other=root.offset(offset(state.getValue(FACING),part));if(other.equals(pos)||!level.hasChunkAt(other))continue;
                var s=level.getBlockState(other);if(s.is(this)&&s.getValue(PART)==part&&s.getValue(FACING)==state.getValue(FACING))level.removeBlock(other,false);
            }}finally{DISMANTLING.set(false);}
        }super.onRemove(state,level,pos,next,moving);
    }
    public static void validatePlan(List<ConstructionPlan.Cell> cells){
        Map<BlockPos,BlockState> states=new HashMap<>();cells.forEach(c->states.put(c.pos(),c.state()));
        for(var cell:cells)if(cell.state().getBlock()instanceof LargeMachineBlock){
            var state=cell.state();var block=(LargeMachineBlock)state.getBlock();BlockPos root=anchor(cell.pos(),state);
            if(state.getValue(PART)>=parts(block.kind))throw new IllegalArgumentException("大型机器含无效的外壳编号");
            for(int part=0;part<parts(block.kind);part++){
                var found=states.get(root.offset(offset(state.getValue(FACING),part)));
                if(found==null||found.getBlock()!=state.getBlock()||found.getValue(PART)!=part||found.getValue(FACING)!=state.getValue(FACING))throw new IllegalArgumentException(parts(block.kind)==8?"相核凝矿阵需要完整的2×2×2八格结构，请扩大测绘范围":"大型机器需要完整的2×2四格结构，请扩大测绘范围");
            }
        }
    }
    public static boolean consumesMaterial(BlockState state){return !(state.getBlock()instanceof LargeMachineBlock)||state.getValue(PART)==0;}
    public static void restoreSnapshot(net.neoforged.neoforge.common.util.BlockSnapshot snapshot){boolean old=DISMANTLING.get();DISMANTLING.set(true);try{snapshot.restore(18);}finally{DISMANTLING.set(old);}}
}
