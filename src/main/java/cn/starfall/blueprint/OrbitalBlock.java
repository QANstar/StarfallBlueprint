package cn.starfall.blueprint;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** One controller and one drop per complete, directional structure. */
public final class OrbitalBlock extends BaseEntityBlock {
    public static final MapCodec<OrbitalBlock> CODEC=simpleCodec(OrbitalBlock::new);
    public static final IntegerProperty PART=IntegerProperty.create("part",0,99);
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    private static final ThreadLocal<Boolean> REMOVING=ThreadLocal.withInitial(()->false);
    public OrbitalBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(PART,0).setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(PART,FACING);}
    public static String key(BlockState s){for(String key:OrbitalStructure.KEYS)if(s.is(Haijia.BLOCKS.get(key).get()))return key;throw new IllegalArgumentException("Unknown orbital block");}
    public static BlockPos anchor(BlockPos pos,BlockState s){return pos.subtract(OrbitalStructure.of(key(s)).offset(s.getValue(FACING),s.getValue(PART)));}
    public static OrbitalBlockEntity controller(Level l,BlockPos pos){if(!l.hasChunkAt(pos))return null;var s=l.getBlockState(pos);if(!(s.getBlock() instanceof OrbitalBlock))return null;var root=anchor(pos,s);if(!l.hasChunkAt(root))return null;return l.getBlockEntity(root) instanceof OrbitalBlockEntity b&&b.getBlockState().is(s.getBlock())&&b.getBlockState().getValue(PART)==0?b:null;}
    public static boolean complete(OrbitalBlockEntity b){var l=b.getLevel();if(l==null)return false;var s=b.getBlockState();var definition=OrbitalStructure.of(key(s));for(int i=0;i<definition.parts();i++){var pos=b.getBlockPos().offset(definition.offset(s.getValue(FACING),i));if(!l.hasChunkAt(pos))return false;var part=l.getBlockState(pos);if(!part.is(s.getBlock())||part.getValue(PART)!=i||part.getValue(FACING)!=s.getValue(FACING))return false;}return true;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){var state=defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());var definition=OrbitalStructure.of(key(state));for(int i=0;i<definition.parts();i++){var pos=c.getClickedPos().offset(definition.offset(state.getValue(FACING),i));if(!c.getLevel().hasChunkAt(pos)||c.getLevel().isOutsideBuildHeight(pos)||!c.getLevel().getWorldBorder().isWithinBounds(pos)||!c.getLevel().getBlockState(pos).canBeReplaced(c))return null;if(c.getPlayer()!=null&&(!c.getLevel().mayInteract(c.getPlayer(),pos)||!c.getPlayer().mayUseItemAt(pos,c.getClickedFace(),c.getItemInHand())))return null;}return state;}
    @Override public void setPlacedBy(Level l,BlockPos root,BlockState s,LivingEntity placer,ItemStack stack){super.setPlacedBy(l,root,s,placer,stack);if(!l.isClientSide){var definition=OrbitalStructure.of(key(s));for(int i=1;i<definition.parts();i++)l.setBlock(root.offset(definition.offset(s.getValue(FACING),i)),s.setValue(PART,i),3);}}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return OrbitalStructure.of(key(s)).shape(s.getValue(FACING),s.getValue(PART));}
    @Override protected RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    public static void validatePlan(List<ConstructionPlan.Cell> cells){
        var states=new java.util.HashMap<BlockPos,BlockState>();for(var cell:cells)states.put(cell.pos(),cell.state());
        for(var cell:cells)if(cell.state().getBlock() instanceof OrbitalBlock){var s=cell.state();var definition=OrbitalStructure.of(key(s));var root=anchor(cell.pos(),s);if(s.getValue(PART)>=definition.parts())throw new IllegalArgumentException("海岬设施外壳编号无效");
            for(int i=0;i<definition.parts();i++){var other=states.get(root.offset(definition.offset(s.getValue(FACING),i)));if(other==null||!other.is(s.getBlock())||other.getValue(PART)!=i||other.getValue(FACING)!=s.getValue(FACING))throw new IllegalArgumentException("请完整测绘海岬设施的全部外壳");}}
    }
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new OrbitalBlockEntity(p,s);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(stack.getItem() instanceof OrbitalTerminalItem||stack.getItem() instanceof PeregrineItem)return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return l.isClientSide||s.getValue(PART)!=0?null:createTickerHelper(t,Haijia.BE.get(),OrbitalBlockEntity::tick);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos pos,Player p,BlockHitResult hit){var b=controller(l,pos);if(b!=null&&!l.isClientSide){if(!complete(b))p.displayClientMessage(Haijia.text("structure_incomplete"),true);else if(b.kind()==0)p.displayClientMessage(Haijia.text("relay.status",b.energy.getEnergyStored(),b.links.size()),true);else p.openMenu(new SimpleMenuProvider((id,inv,who)->new OrbitalMenu(id,inv,ContainerLevelAccess.create(l,b.getBlockPos()),b.kind()),Haijia.text("station")));}return InteractionResult.sidedSuccess(l.isClientSide);}
    @Override public List<ItemStack> getDrops(BlockState s,LootParams.Builder params){var b=params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);return b instanceof OrbitalBlockEntity&&controller(params.getLevel(),b.getBlockPos())!=null?List.of(new ItemStack(asItem())):List.of();}
    @Override protected void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){if(!s.is(next.getBlock())&&!l.isClientSide&&!REMOVING.get()){REMOVING.set(true);try{var root=anchor(pos,s);var definition=OrbitalStructure.of(key(s));for(int i=0;i<definition.parts();i++){var other=root.offset(definition.offset(s.getValue(FACING),i));if(other.equals(pos)||!l.hasChunkAt(other))continue;var part=l.getBlockState(other);if(part.is(this)&&part.getValue(PART)==i&&part.getValue(FACING)==s.getValue(FACING))l.removeBlock(other,false);}}finally{REMOVING.set(false);}}super.onRemove(s,l,pos,next,moving);}
}
