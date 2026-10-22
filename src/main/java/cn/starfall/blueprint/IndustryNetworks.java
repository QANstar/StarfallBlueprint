package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.*;

/** Budgets belong to a connected loaded network, not each conduit segment. */
public final class IndustryNetworks {
    private record End(BlockPos pos,Direction side){}
    private static final Map<Level,TickState> STATES=new WeakHashMap<>();
    private static final class TickState{long tick;Set<BlockPos>seen=new HashSet<>();TickState(long t){tick=t;}}
    private static final Set<String> POWER=Set.of("cable","energy_junction","busjoint"),FLUID=Set.of("pipe","fluid_valve"),ITEM=Set.of("item_duct","item_extractor","item_router");
    public static void tick(IndustryBlockEntity be){
        if(be.generator()||be.kind().equals("s02"))directPower(be);
        Set<String>family=POWER.contains(be.kind())?POWER:FLUID.contains(be.kind())?FLUID:ITEM.contains(be.kind())?ITEM:null;
        if(family==null||be.redstoneStopped())return;
        Level level=be.getLevel();TickState state=STATES.get(level);if(state==null||state.tick!=level.getGameTime()){state=new TickState(level.getGameTime());STATES.put(level,state);}
        if(state.seen.contains(be.getBlockPos()))return;
        List<IndustryBlockEntity>nodes=new ArrayList<>();Set<BlockPos>visited=new HashSet<>();ArrayDeque<BlockPos>queue=new ArrayDeque<>();queue.add(be.getBlockPos());
        while(!queue.isEmpty()&&visited.size()<=4096){BlockPos p=queue.removeFirst();if(!visited.add(p)||!level.hasChunkAt(p))continue;
            if(!(level.getBlockEntity(p)instanceof IndustryBlockEntity n)||!family.contains(n.kind())||n.redstoneStopped())continue;nodes.add(n);
            for(Direction d:Direction.values())if((n.faceMask&(1<<d.ordinal()))!=0){BlockPos next=p.relative(d);if(level.hasChunkAt(next)&&level.getBlockEntity(next)instanceof IndustryBlockEntity other&&family.contains(other.kind())&&(other.faceMask&(1<<d.getOpposite().ordinal()))!=0)queue.add(next);}
        }
        for(var n:nodes)state.seen.add(n.getBlockPos());if(visited.size()>4096)return;
        nodes.sort(Comparator.comparingLong(n->n.getBlockPos().asLong()));
        if(family==POWER)power(nodes,level);else if(family==FLUID)fluids(nodes,level);else if(level.getGameTime()%20==0)items(nodes,level);
    }
    private static List<End>ends(List<IndustryBlockEntity>nodes){Set<BlockPos>inside=new HashSet<>();nodes.forEach(n->inside.add(n.getBlockPos()));Set<End>ends=new LinkedHashSet<>();for(var n:nodes)for(Direction d:Direction.values())if((n.faceMask&(1<<d.ordinal()))!=0&&!inside.contains(n.getBlockPos().relative(d))&&n.getLevel().hasChunkAt(n.getBlockPos().relative(d)))ends.add(new End(n.getBlockPos().relative(d),d.getOpposite()));return new ArrayList<>(ends);}
    private static void directPower(IndustryBlockEntity source){if(source.redstoneStopped())return;for(Direction d:Direction.values()){
        BlockPos p=source.getBlockPos().relative(d);if(!source.getLevel().hasChunkAt(p))continue;
        var target=source.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,p,d.getOpposite());if(target==null||!target.canReceive())continue;
        if(source.kind().equals("s02")&&source.getLevel().getBlockEntity(p)instanceof IndustryBlockEntity other&&other.kind().equals("s02"))continue;
        transfer(source.energyPort(d),target,2048);
    }}
    private static int transfer(IEnergyStorage source,IEnergyStorage target,int budget){int offered=source.extractEnergy(budget,true),accepted=target.receiveEnergy(offered,true);if(accepted<=0)return 0;int removed=source.extractEnergy(accepted,false);int stored=target.receiveEnergy(removed,false);if(stored<removed)source.receiveEnergy(removed-stored,false);return stored;}
    private static void power(List<IndustryBlockEntity>nodes,Level level){
        var ends=ends(nodes);if(ends.isEmpty())return;Collections.rotate(ends,(int)(level.getGameTime()%ends.size()));
        int budget=nodes.stream().allMatch(n->n.kind().equals("busjoint"))?32768:2048;
        for(boolean reserve:new boolean[]{false,true})for(End source:ends){
            boolean battery=level.getBlockEntity(source.pos)instanceof IndustryBlockEntity b&&b.kind().equals("s02");if(battery!=reserve)continue;
            var from=level.getCapability(Capabilities.EnergyStorage.BLOCK,source.pos,source.side);if(from==null||!from.canExtract())continue;
            // Rotate reachable consumers too: rotating only sources can fill one large
            // idle buffer forever while other connected workstations receive no power.
            var targets=directedEnds(source,nodes,POWER);if(!targets.isEmpty())Collections.rotate(targets,(int)Math.floorMod(level.getGameTime(),targets.size()));
            for(End target:targets){if(budget==0)return;if(source.pos.equals(target.pos))continue;
                if(reserve&&level.getBlockEntity(target.pos)instanceof IndustryBlockEntity t&&t.kind().equals("s02"))continue;
                var to=level.getCapability(Capabilities.EnergyStorage.BLOCK,target.pos,target.side);if(to==null||!to.canReceive())continue;budget-=transfer(from,to,budget);
            }
        }
    }
    private static void fluids(List<IndustryBlockEntity>nodes,Level level){
        var endpoints=ends(nodes);if(endpoints.isEmpty())return;Collections.rotate(endpoints,(int)(level.getGameTime()%endpoints.size()));
        int budget=100;FluidStack selected=FluidStack.EMPTY;
        String requested=nodes.stream().map(n->n.fluidFilter).filter(s->!s.isEmpty()).findFirst().orElse("");
        for(End source:endpoints){
            boolean valve=nodes.stream().anyMatch(n->n.kind().equals("fluid_valve")&&n.getBlockPos().relative(n.facing().getOpposite()).equals(source.pos));
            if(level.getBlockEntity(source.pos)instanceof CampWaterBlockEntity water&&!water.getBlockState().is(StarfallBlueprint.RAIN_COLLECTOR.get())&&!valve)continue;
            var from=level.getCapability(Capabilities.FluidHandler.BLOCK,source.pos,source.side);if(from==null)continue;
            FluidStack offer=requested.isEmpty()?from.drain(budget,IFluidHandler.FluidAction.SIMULATE):from.drain(new FluidStack(CatalogueFluids.fluid(requested),budget),IFluidHandler.FluidAction.SIMULATE);if(offer.isEmpty())continue;
            if(!selected.isEmpty()&&!FluidStack.isSameFluidSameComponents(selected,offer))continue;
            String key=CatalogueFluids.key(offer.getFluid());if(nodes.stream().anyMatch(n->!n.fluidFilter.isEmpty()&&!n.fluidFilter.equals(key)))continue;
            FluidStack type=offer.copy();
            for(End target:directedEnds(source,nodes,FLUID)){if(budget==0)return;if(source.pos.equals(target.pos))continue;
                var to=level.getCapability(Capabilities.FluidHandler.BLOCK,target.pos,target.side);if(to==null)continue;
                boolean targetIsInputSideOfValve=nodes.stream().anyMatch(n->n.kind().equals("fluid_valve")&&n.getBlockPos().relative(n.facing().getOpposite()).equals(target.pos));if(targetIsInputSideOfValve)continue;
                offer=from.drain(type.copyWithAmount(budget),IFluidHandler.FluidAction.SIMULATE);if(offer.isEmpty())break;
                int n=to.fill(offer,IFluidHandler.FluidAction.SIMULATE);if(n<=0)continue;
                FluidStack drained=from.drain(offer.copyWithAmount(Math.min(n,budget)),IFluidHandler.FluidAction.EXECUTE);
                int accepted=to.fill(drained,IFluidHandler.FluidAction.EXECUTE);if(accepted<drained.getAmount())from.fill(drained.copyWithAmount(drained.getAmount()-accepted),IFluidHandler.FluidAction.EXECUTE);
                if(accepted>0){selected=drained;budget-=accepted;}
            }
        }
    }
    private static void items(List<IndustryBlockEntity>nodes,Level level){
        List<IndustryBlockEntity>extractors=new ArrayList<>(nodes.stream().filter(n->n.kind().equals("item_extractor")).toList());if(extractors.isEmpty())return;
        Collections.rotate(extractors,(int)(level.getGameTime()/20%extractors.size()));Set<BlockPos>sourcePositions=new HashSet<>();for(var e:extractors)sourcePositions.add(e.getBlockPos().relative(e.facing().getOpposite()));
        int networkBudget=8;
        for(var extractor:extractors){
            if(networkBudget==0)return;BlockPos source=extractor.getBlockPos().relative(extractor.facing().getOpposite());if(!level.hasChunkAt(source))continue;
            var from=level.getCapability(Capabilities.ItemHandler.BLOCK,source,extractor.facing());if(from==null)continue;int portBudget=Math.min(4,networkBudget);
            for(int slot=0;slot<from.getSlots()&&portBudget>0;slot++){
                ItemStack original=from.getStackInSlot(slot);if(original.isEmpty()||!extractor.acceptsFilter(original))continue;
                int total=0;for(int i=0;i<from.getSlots();i++)if(ItemStack.isSameItemSameComponents(from.getStackInSlot(i),original))total+=from.getStackInSlot(i).getCount();
                int allowed=Math.min(portBudget,Math.max(0,total-extractor.retain));if(allowed==0)continue;
                var targets=itemTargets(extractor,original,sourcePositions);if(!targets.isEmpty())Collections.rotate(targets,extractor.rotationCursor++%targets.size());
                for(End target:targets){
                    var to=level.getCapability(Capabilities.ItemHandler.BLOCK,target.pos,target.side);if(to==null)continue;
                    ItemStack offered=from.extractItem(slot,allowed,true);if(offered.isEmpty())break;
                    int accepted=offered.getCount()-ItemHandlerHelper.insertItemStacked(to,offered,true).getCount();if(accepted==0)continue;
                    ItemStack extracted=from.extractItem(slot,accepted,false),left=ItemHandlerHelper.insertItemStacked(to,extracted,false);
                    if(!left.isEmpty()){ItemStack rollback=from.insertItem(slot,left,false);if(!rollback.isEmpty())net.minecraft.world.Containers.dropItemStack(level,source.getX()+.5,source.getY()+.5,source.getZ()+.5,rollback);}
                    int moved=extracted.getCount()-left.getCount();allowed-=moved;portBudget-=moved;networkBudget-=moved;extractor.setChanged();if(allowed==0)break;
                }
            }
        }
    }
    private static List<End>itemTargets(IndustryBlockEntity extractor,ItemStack stack,Set<BlockPos>sources){
        Level level=extractor.getLevel();ArrayDeque<Step>queue=new ArrayDeque<>();Set<BlockPos>seen=new HashSet<>();seen.add(extractor.getBlockPos());Set<End>targets=new LinkedHashSet<>();queue.add(new Step(extractor.getBlockPos().relative(extractor.facing()),extractor.getBlockPos()));
        while(!queue.isEmpty()&&seen.size()<4096){var step=queue.removeFirst();BlockPos pos=step.pos;if(!level.hasChunkAt(pos))continue;
            if(level.getBlockEntity(pos)instanceof IndustryBlockEntity router&&router.kind().equals("item_router")&&!step.from.equals(pos.relative(router.facing().getOpposite())))continue;
            if(!seen.add(pos))continue;
            if(!(level.getBlockEntity(pos)instanceof IndustryBlockEntity n)||!ITEM.contains(n.kind())){
                if(!sources.contains(pos)){for(Direction side:Direction.values())if(step.from.equals(pos.relative(side)))targets.add(new End(pos,side));}continue;
            }
            if(n.redstoneStopped()||!n.acceptsFilter(stack)||n.kind().equals("item_extractor"))continue;
            List<Direction>directions=n.kind().equals("item_router")?routerOutputs(n,stack):List.of(Direction.values());
            for(Direction d:directions)if((n.faceMask&(1<<d.ordinal()))!=0){BlockPos next=pos.relative(d);if(level.hasChunkAt(next)&&level.getBlockEntity(next)instanceof IndustryBlockEntity other&&ITEM.contains(other.kind())&&(other.faceMask&(1<<d.getOpposite().ordinal()))==0)continue;queue.add(new Step(next,pos));}
        }
        return new ArrayList<>(targets);
    }
    private static List<Direction>routerOutputs(IndustryBlockEntity node,ItemStack stack){
        List<Direction>outs=List.of(node.facing().getCounterClockWise(),node.facing(),node.facing().getClockWise());
        boolean any=false;for(int row=0;row<3;row++){List<Direction>match=new ArrayList<>();for(int col=0;col<3;col++){var f=node.filters.getItem(row*3+col);if(!f.isEmpty()){any=true;if(node.filterMatches(f,stack))match.add(outs.get(col));}}if(!match.isEmpty()){Collections.rotate(match,node.rotationCursor++%match.size());return match;}}
        return any?List.of():outs;
    }
    private record Step(BlockPos pos,BlockPos from){}
    private static List<End>directedEnds(End source,List<IndustryBlockEntity>nodes,Set<String>family){
        Map<BlockPos,IndustryBlockEntity>inside=new HashMap<>();nodes.forEach(n->inside.put(n.getBlockPos(),n));Set<BlockPos>seen=new HashSet<>();Set<End>ends=new LinkedHashSet<>();ArrayDeque<Step>queue=new ArrayDeque<>();queue.add(new Step(source.pos.relative(source.side),source.pos));
        while(!queue.isEmpty()){var step=queue.removeFirst();var node=inside.get(step.pos);if(node==null)continue;
            boolean directed=node.kind().equals("fluid_valve")||node.kind().equals("energy_junction");if(directed&&!step.from.equals(node.getBlockPos().relative(node.facing().getOpposite())))continue;if(!seen.add(step.pos))continue;
            List<Direction>directions=node.kind().equals("fluid_valve")?List.of(node.facing()):node.kind().equals("energy_junction")?List.of(node.facing(),node.facing().getCounterClockWise(),node.facing().getClockWise()):List.of(Direction.values());
            for(Direction d:directions){if((node.faceMask&(1<<d.ordinal()))==0)continue;BlockPos next=step.pos.relative(d);if(!node.getLevel().hasChunkAt(next))continue;if(inside.containsKey(next)){if((inside.get(next).faceMask&(1<<d.getOpposite().ordinal()))!=0)queue.add(new Step(next,step.pos));}else ends.add(new End(next,d.getOpposite()));}
        }
        return new ArrayList<>(ends);
    }
}
