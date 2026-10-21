package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class IndustryEnergy {
    private static final Map<Level,Map<BlockPos,Long>> WHEELS=new WeakHashMap<>();
    public static void tick(IndustryBlockEntity be){
        Level level=be.getLevel();String key=be.kind();long room=be.capacity()-be.getPower();int generated=0;
        if(be.peakCooldown>0){be.peakCooldown--;if(be.peakCooldown==0)be.peakTicks=0;}
        switch(key){
            case "g01"->{if(room>0){if(be.fuelHeat==0&&PreparationBlockEntity.isFuel(be.stock.get(13))){be.stock.get(13).shrink(1);be.fuelHeat=320000;}generated=(int)Math.min(Math.min(500,be.fuelHeat),room);be.fuelHeat-=generated;}}
            case "g02"->{if(level.dimensionType().hasSkyLight()&&level.canSeeSky(be.getBlockPos().above()))generated=(int)Math.min(room,be.getBlockPos().getY()>=96?160:80);}
            case "g04"->{if(level.dimensionType().hasSkyLight()&&level.isDay()&&level.canSeeSky(be.getBlockPos().above()))generated=(int)Math.min(room,level.isRaining()?50:100);}
            case "g03"->{BlockPos flow=null;for(Direction d:Direction.values()){BlockPos p=be.getBlockPos().relative(d);if(level.hasChunkAt(p)&&level.getFluidState(p).is(Fluids.FLOWING_WATER)&&!level.getFluidState(p).isSource()){flow=p;break;}}if(flow!=null&&room>0){var claims=WHEELS.computeIfAbsent(level,l->new HashMap<>());claims.entrySet().removeIf(e->e.getValue()!=level.getGameTime());BlockPos source=waterOrigin(level,flow);if(!claims.containsKey(source)){claims.put(source,level.getGameTime());generated=(int)Math.min(room,100);}}}
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
