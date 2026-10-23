package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class IndustryEnergy {
    private static final Map<Level,Map<BlockPos,Long>> WHEELS=new WeakHashMap<>();
    private static final Map<Level,Map<BlockPos,Long>> LAVA_WHEELS=new WeakHashMap<>();
    public static void tick(IndustryBlockEntity be){
        Level level=be.getLevel();String key=be.kind();long room=be.capacity()-be.getPower();int generated=0;
        if(be.peakCooldown>0){be.peakCooldown--;if(be.peakCooldown==0)be.peakTicks=0;}
        switch(key){
            case "g01"->{if(room>0){if(be.fuelHeat==0)GeneratorFuel.refuel(be);generated=(int)Math.min(Math.min(500,be.fuelHeat),room);be.fuelHeat-=generated;}}
            case "g02"->{if(level.dimensionType().hasSkyLight()&&level.canSeeSky(be.getBlockPos().above()))generated=(int)Math.min(room,be.getBlockPos().getY()>=96?160:80);}
            case "g04"->{if(level.dimensionType().hasSkyLight()&&level.isDay()&&level.canSeeSky(be.getBlockPos().above()))generated=(int)Math.min(room,level.isRaining()?50:100);}
            case "g03"->{BlockPos flow=null;for(Direction d:Direction.values()){BlockPos p=be.getBlockPos().relative(d);if(level.hasChunkAt(p)&&level.getFluidState(p).is(Fluids.FLOWING_WATER)&&!level.getFluidState(p).isSource()){flow=p;break;}}if(flow!=null&&room>0){var claims=WHEELS.computeIfAbsent(level,l->new HashMap<>());claims.entrySet().removeIf(e->e.getValue()!=level.getGameTime());BlockPos source=waterOrigin(level,flow);if(!claims.containsKey(source)){claims.put(source,level.getGameTime());generated=(int)Math.min(room,100);}}}
            case "g06"->generated=lavaFlowPower(be,room);
            case "g07"->generated=(int)Math.min(room,geothermalPower(level,be.getBlockPos().below()));
            case "g05"->{if(be.tanks[0].getFluid().is(CatalogueFluids.fluid("bio"))){int n=(int)Math.min(Math.min(room/1000,2),be.tanks[0].getFluidAmount());if(n>0){be.tanks[0].drain(n,FluidAction.EXECUTE);generated=n*1000;}}}
            case "pump","dew"->pump(be);
            default->{}
        }
        if(generated>0){be.setPower(be.getPower()+generated);be.status=1;}else if(be.generator())be.status=room==0?5:2;
        if(be.stock.get(18).is(CatalogueContent.item("s01"))&&be.getPower()>0){var target=CatalogueEquipment.energy(be.stock.get(18));int accepted=target.receiveEnergy((int)Math.min(512,be.getPower()),false);be.setPower(be.getPower()-accepted);}
    }
    private static BlockPos waterOrigin(Level level,BlockPos start){
        BlockPos current=start;Set<BlockPos>seen=new HashSet<>();
        for(int i=0;i<64&&seen.add(current);i++){
            BlockPos up=current.above();if(level.hasChunkAt(up)&&level.getFluidState(up).is(Fluids.WATER)){current=up;continue;}
            if(level.getFluidState(current).isSource())return current;
            BlockPos next=null;int amount=level.getFluidState(current).getAmount();
            for(Direction d:Direction.Plane.HORIZONTAL){BlockPos p=current.relative(d);if(level.hasChunkAt(p)&&level.getFluidState(p).is(Fluids.WATER)&&level.getFluidState(p).getAmount()>amount&&(next==null||p.asLong()<next.asLong()))next=p;}
            if(next==null)return current;current=next;
        }return current;
    }
    private static int lavaFlowPower(IndustryBlockEntity be,long room){
        if(room<=0)return 0;
        Level level=be.getLevel();
        var claims=LAVA_WHEELS.computeIfAbsent(level,l->new HashMap<>());long tick=level.getGameTime();
        claims.entrySet().removeIf(e->e.getValue()!=tick);
        for(Direction d:Direction.values()){
            BlockPos flow=be.getBlockPos().relative(d);
            if(!level.hasChunkAt(flow)||!level.getFluidState(flow).is(Fluids.FLOWING_LAVA)||level.getFluidState(flow).isSource())continue;
            BlockPos source=lavaOrigin(level,flow);
            if(source==null||claims.containsKey(source))continue;
            claims.put(source,tick);
            return (int)Math.min(room,200);
        }
        return 0;
    }
    private static boolean isLava(Level level,BlockPos pos){
        return level.hasChunkAt(pos)&&(level.getFluidState(pos).is(Fluids.LAVA)||level.getFluidState(pos).is(Fluids.FLOWING_LAVA));
    }
    /** Find a real source within 64 loaded upstream positions; unresolved flows supply no power. */
    private static BlockPos lavaOrigin(Level level,BlockPos start){
        BlockPos current=start;Set<BlockPos> seen=new HashSet<>();
        for(int i=0;i<64&&seen.add(current);i++){
            if(!isLava(level,current))return null;
            if(level.getFluidState(current).is(Fluids.LAVA)&&level.getFluidState(current).isSource())return current;
            BlockPos up=current.above();if(isLava(level,up)){current=up;continue;}
            BlockPos next=null;int amount=level.getFluidState(current).getAmount();
            for(Direction d:Direction.Plane.HORIZONTAL){
                BlockPos p=current.relative(d);
                if(isLava(level,p)&&level.getFluidState(p).getAmount()>amount&&(next==null||p.asLong()<next.asLong()))next=p;
            }
            if(next==null)return null;current=next;
        }
        return null;
    }
    private static int geothermalPower(Level level,BlockPos below){
        if(!level.hasChunkAt(below))return 0;
        var state=level.getBlockState(below);
        // Heat sources are persistent: this simplified utilization never consumes them.
        if(level.getFluidState(below).is(Fluids.LAVA)&&level.getFluidState(below).isSource())return 160;
        if(state.is(Blocks.MAGMA_BLOCK))return 80;
        if(state.is(Blocks.SOUL_CAMPFIRE)&&state.getValue(CampfireBlock.LIT))return 60;
        if(state.is(Blocks.CAMPFIRE)&&state.getValue(CampfireBlock.LIT))return 40;
        return 0;
    }
    private static void pump(IndustryBlockEntity be){
        boolean pump=be.kind().equals("pump");Level level=be.getLevel();BlockPos below=be.getBlockPos().below();
        boolean source=!pump||level.hasChunkAt(below)&&level.getBlockState(below).is(Blocks.WATER)&&level.getFluidState(below).isSource();
        if(!source){be.status=3;return;}
        FluidStack output=new FluidStack(Fluids.WATER,1000);
        if(be.tanks[0].fill(output,FluidAction.SIMULATE)<1000){be.status=5;return;}
        int cost=pump?50:200;if(be.getPower()<cost){be.status=4;return;}
        be.setPower(be.getPower()-cost);be.progressTicks++;be.processTicks=20;be.status=1;
        if(be.progressTicks>=20){
            if(pump&&!level.getGameRules().getBoolean(GameRules.RULE_WATER_SOURCE_CONVERSION))level.setBlock(below,Blocks.AIR.defaultBlockState(),3);
            be.tanks[0].fill(output,FluidAction.EXECUTE);be.progressTicks=0;
        }
        be.setChanged();
    }
}
