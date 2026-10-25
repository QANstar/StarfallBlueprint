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
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

public class IndustryBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE=BlockStateProperties.LIT;
    public final String kind;
    public IndustryBlock(String kind,Properties p){super(p);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ACTIVE,false));}
    protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(p->new IndustryBlock(kind,p));}
    protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(FACING,ACTIVE);}
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    protected RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new IndustryBlockEntity(p,s);}
    public <T extends BlockEntity>BlockEntityTicker<T>getTicker(Level l,BlockState s,BlockEntityType<T>type){return l.isClientSide?null:createTickerHelper(type,CatalogueContent.ENTITY.get(),IndustryBlockEntity::tick);}
    protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(level.getBlockEntity(pos)instanceof IndustryBlockEntity be) {
            if(net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(player,hand,level,pos,hit.getDirection()))return ItemInteractionResult.sidedSuccess(level.isClientSide);
            if(stack.is(CatalogueContent.item("guide"))&&kind.equals("s02")&&!be.efficientCharge){if(!level.isClientSide){be.efficientCharge=true;if(!player.isCreative())stack.shrink(1);be.setChanged();}return ItemInteractionResult.sidedSuccess(level.isClientSide);}
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        if(!l.isClientSide&&l.getBlockEntity(p)instanceof IndustryBlockEntity be){
            if(kind.equals("terminal")){
                if(player.isShiftKeyDown()){be.efficientCharge=true;be.setChanged();player.displayClientMessage(net.minecraft.network.chat.Component.literal("检测终端已校准：读取背面相邻工位"),false);}
                else if(!be.efficientCharge)player.displayClientMessage(net.minecraft.network.chat.Component.literal("潜行空手右键校准；背面朝向需要检测的工位"),false);
                else if(be.getPower()<20)player.displayClientMessage(net.minecraft.network.chat.Component.literal("检测终端缺少供能（每次20 XH）"),false);
                else {var target=p.relative(be.facing().getOpposite());if(l.hasChunkAt(target)){
                    var energy=l.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,target,be.facing());var fluids=l.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,target,be.facing());
                    StringBuilder report=new StringBuilder("工位检测：");boolean found=false;
                    if(energy!=null){found=true;report.append("储能 ").append(energy.getEnergyStored()).append('/').append(energy.getMaxEnergyStored()).append(" XH；");}
                    if(fluids!=null){found=true;for(int i=0;i<fluids.getTanks();i++){var fluid=fluids.getFluidInTank(i);report.append("罐").append(i+1).append(' ').append(fluid.getAmount()).append('/').append(fluids.getTankCapacity(i)).append("mB");if(!fluid.isEmpty())report.append(' ').append(CatalogueFluids.key(fluid.getFluid())).append(' ').append(fluid.getFluidType().getTemperature(fluid)).append('K');report.append("；");}}
                    if(l.getBlockEntity(target)instanceof IndustryBlockEntity work){found=true;report.append("进度 ").append(work.progressTicks).append('/').append(work.processTicks).append("，状态 ").append(work.status);}
                    if(found){be.setPower(be.getPower()-20);player.displayClientMessage(net.minecraft.network.chat.Component.literal(report.toString()),false);}else player.displayClientMessage(net.minecraft.network.chat.Component.literal("背面没有可读取的许可工位"),false);
                }}
            }else if(ConduitBlock.isConduit(kind)&&!be.hasItemFilter()){
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("自动连接相邻设备；箭头表示流向。红石信号关闭输送。"),true);
            }else player.openMenu(be);
        }return InteractionResult.sidedSuccess(l.isClientSide);
    }
    public List<ItemStack>getDrops(BlockState s,LootParams.Builder p){ItemStack stack=CatalogueContent.stack(kind,1);if(p.getOptionalParameter(LootContextParams.BLOCK_ENTITY)instanceof IndustryBlockEntity be)stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(be.saveWithFullMetadata(p.getLevel().registryAccess())));return List.of(stack);}
}
