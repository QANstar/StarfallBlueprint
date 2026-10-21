package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.registries.*;
import net.neoforged.bus.api.IEventBus;

public final class CatalogueFluids {
    public static final DeferredRegister<FluidType> TYPES=DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES,StarfallBlueprint.ID);
    public static final DeferredRegister<Fluid> FLUIDS=DeferredRegister.create(Registries.FLUID,StarfallBlueprint.ID);
    public record Entry(DeferredHolder<FluidType,FluidType> type,DeferredHolder<Fluid,BaseFlowingFluid.Source> source,
        DeferredHolder<Fluid,BaseFlowingFluid.Flowing> flowing,DeferredHolder<Block,LiquidBlock> block,DeferredHolder<Item,BucketItem> bucket){}
    public static final Map<String,Entry> ENTRIES=new LinkedHashMap<>();
    static {
        for(String key:List.of("warm","wastewater","bio","returnwater")) {
            var type=TYPES.register(key,()->new FluidType(FluidType.Properties.create().density(key.equals("bio")?900:1000).viscosity(key.equals("bio")?1500:1000).temperature(key.equals("warm")?330:300)));
            var source=FLUIDS.register(key,()->new BaseFlowingFluid.Source(properties(key)));
            var flow=FLUIDS.register(key+"_flowing",()->new BaseFlowingFluid.Flowing(properties(key)));
            var block=StarfallBlueprint.BLOCKS.register(key+"_fluid",()->new LiquidBlock(source.get(),BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)));
            var bucket=StarfallBlueprint.ITEMS.registerItem(key+"_bucket",p->new BucketItem(source.get(),p.craftRemainder(Items.BUCKET).stacksTo(1)));
            ENTRIES.put(key,new Entry(type,source,flow,block,bucket));
        }
    }
    private static BaseFlowingFluid.Properties properties(String key){var e=ENTRIES.get(key);return new BaseFlowingFluid.Properties(e.type,e.source,e.flowing).block(e.block).bucket(e.bucket);}
    public static Fluid fluid(String key){return key.equals("water")||key.equals("water_source")?Fluids.WATER:ENTRIES.get(key).source.get();}
    public static String key(Fluid f){if(f.isSame(Fluids.WATER))return "water";for(var e:ENTRIES.entrySet())if(e.getValue().source.get().isSame(f))return e.getKey();return "";}
    public static void initialize(){}
    public static void register(IEventBus bus){TYPES.register(bus);FLUIDS.register(bus);}
}
