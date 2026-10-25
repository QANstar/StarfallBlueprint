package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.capabilities.Capabilities;

/** The rendered arms, selection box and network use the same physical connections. */
public final class ConduitBlock extends IndustryBlock {
    public static final Set<String> POWER=Set.of("cable","energy_junction","busjoint");
    public static final Set<String> FLUID=Set.of("pipe","fluid_valve");
    public static final Set<String> ITEM=Set.of("item_duct","item_extractor","item_router");
    public static final Map<Direction,BooleanProperty> CONNECTIONS=Map.of(
            Direction.DOWN,BlockStateProperties.DOWN,Direction.UP,BlockStateProperties.UP,
            Direction.NORTH,BlockStateProperties.NORTH,Direction.SOUTH,BlockStateProperties.SOUTH,
            Direction.WEST,BlockStateProperties.WEST,Direction.EAST,BlockStateProperties.EAST);
    public static boolean isConduit(String kind){return POWER.contains(kind)||FLUID.contains(kind)||ITEM.contains(kind);}
    public static boolean hasPort(String kind,Direction facing,Direction side){
        if(kind.equals("item_extractor")||kind.equals("fluid_valve"))return side==facing||side==facing.getOpposite();
        if(kind.equals("item_router")||kind.equals("energy_junction"))return side.getAxis().isHorizontal();
        return true;
    }
    public ConduitBlock(String kind,Properties properties){
        super(kind,properties);
        BlockState state=defaultBlockState();
        for(var property:CONNECTIONS.values())state=state.setValue(property,false);
        registerDefaultState(state);
    }
    @Override protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(p->new ConduitBlock(kind,p));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        super.createBlockStateDefinition(builder);CONNECTIONS.values().forEach(builder::add);
    }
    private Set<String> family(){return POWER.contains(kind)?POWER:FLUID.contains(kind)?FLUID:ITEM;}
    public boolean connects(BlockState state,BlockGetter world,BlockPos pos,Direction side){
        if(!hasPort(kind,state.getValue(FACING),side))return false;
        BlockPos next=pos.relative(side);
        if(world.getBlockState(next).getBlock() instanceof ConduitBlock other)
            return family().contains(other.kind)&&hasPort(other.kind,world.getBlockState(next).getValue(FACING),side.getOpposite());
        if(!(world instanceof Level level)||!level.hasChunkAt(next))return false;
        if(POWER.contains(kind)){
            var port=level.getCapability(Capabilities.EnergyStorage.BLOCK,next,side.getOpposite());
            // Redstone can disable transfer without changing the physical socket.
            return port!=null&&(!(level.getBlockEntity(next) instanceof IndustryBlockEntity be)
                    ||!Set.of("damper","supply_crate").contains(be.kind()));
        }
        if(FLUID.contains(kind))return level.getCapability(Capabilities.FluidHandler.BLOCK,next,side.getOpposite())!=null;
        var port=level.getCapability(Capabilities.ItemHandler.BLOCK,next,side.getOpposite());
        return port!=null&&port.getSlots()>0;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        BlockState state=super.getStateForPlacement(context);
        for(Direction side:Direction.values())state=state.setValue(CONNECTIONS.get(side),connects(state,context.getLevel(),context.getClickedPos(),side));
        return state;
    }
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor world,BlockPos pos,BlockPos neighborPos){
        return state.setValue(CONNECTIONS.get(side),connects(state,world,pos,side));
    }
    @Override protected void tick(BlockState state,net.minecraft.server.level.ServerLevel level,BlockPos pos,net.minecraft.util.RandomSource random){
        BlockState connected=state;
        for(Direction side:Direction.values())connected=connected.setValue(CONNECTIONS.get(side),connects(state,level,pos,side));
        if(connected!=state)level.setBlock(pos,connected,3);
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){
        double low=kind.equals("cable")?6:5,high=16-low;
        VoxelShape shape=Block.box(low,low,low,high,high,high);
        if(!Set.of("cable","pipe","item_duct","busjoint").contains(kind))shape=Shapes.or(shape,Block.box(4,4,4,12,12,12));
        for(Direction side:Direction.values())if(state.getValue(CONNECTIONS.get(side)))shape=Shapes.or(shape,switch(side){
            case DOWN->Block.box(low,0,low,high,low,high);case UP->Block.box(low,high,low,high,16,high);
            case NORTH->Block.box(low,low,0,high,high,low);case SOUTH->Block.box(low,low,high,high,high,16);
            case WEST->Block.box(0,low,low,low,high,high);case EAST->Block.box(high,low,low,16,high,high);
        });
        return shape;
    }
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return getShape(state,world,pos,context);}
}
