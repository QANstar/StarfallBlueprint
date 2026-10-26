package cn.starfall.blueprint;

import java.util.Set;
import java.math.BigInteger;
import net.minecraft.world.item.ItemStack;

/** Shared, manually serviced upgrade bay. Effects are frozen when a batch is reserved. */
public final class MachineUpgrades {
    private MachineUpgrades() {}
    private static final Set<String> PROCESSORS=Set.of("prep","press","quarry","crusher","phase_extractor","plant_cultivator","refiner","recycler","bio_processor","autocrafter","auto_furnace");
    public static boolean supports(String kind){return PROCESSORS.contains(kind);}
    public static boolean isSlot(String kind,int index){return supports(kind)&&(index==16||index==17||index==20||index==21||kind.equals("quarry")&&index==18||kind.equals("prep")&&index==19);}
    public static boolean idle(IndustryBlockEntity be){return be.idle();}
    public static boolean accepts(IndustryBlockEntity be,ItemStack stack){
        if(!supports(be.kind())||!idle(be))return false;
        return stack.is(CatalogueContent.item("speed_1"))||stack.is(CatalogueContent.item("speed_2"))||stack.is(StarfallBlueprint.CONNECTOR.get())
                ||be.kind().equals("auto_furnace")&&(stack.is(CatalogueContent.item("bulk_1"))||stack.is(CatalogueContent.item("bulk_2")))
                ||be.kind().equals("quarry")&&stack.is(CatalogueContent.item("resonance_upgrade"));
    }
    public static int maxStack(ItemStack stack){return stack.is(CatalogueContent.item("resonance_upgrade"))?1:64;}
    public static long count(IndustryBlockEntity be,String key){long count=0;for(int i=0;i<be.upgrades.getContainerSize();i++)if(be.upgrades.getItem(i).is(CatalogueContent.item(key)))count+=be.upgrades.getItem(i).getCount();return count;}
    public static long connectors(IndustryBlockEntity be){long count=0;for(int i=0;i<be.upgrades.getContainerSize();i++)if(be.upgrades.getItem(i).is(StarfallBlueprint.CONNECTOR.get()))count+=be.upgrades.getItem(i).getCount();return count;}
    public static long speed(IndustryBlockEntity be){return count(be,"speed_1")+2*count(be,"speed_2");}
    public static long bulk(IndustryBlockEntity be){return count(be,"bulk_1")+2*count(be,"bulk_2");}
    public static int duration(IndustryBlockEntity be,int ticks){long divisor=100+25*speed(be);return Math.max(1,(int)(((long)ticks*100+divisor-1)/divisor));}
    private static BigInteger ceil(BigInteger value,long divisor){return value.add(BigInteger.valueOf(divisor-1)).divide(BigInteger.valueOf(divisor));}
    public static long cost(IndustryBlockEntity be,long base){
        long premium=100L+10L*count(be,"speed_1")+25L*count(be,"speed_2");
        BigInteger accelerated=ceil(BigInteger.valueOf(base).multiply(BigInteger.valueOf(premium)),100);
        return ceil(accelerated.multiply(BigInteger.valueOf(9)),9+connectors(be)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
    public static long paymentTarget(long cost,int step,int duration){return BigInteger.valueOf(cost).multiply(BigInteger.valueOf(step)).divide(BigInteger.valueOf(duration)).longValueExact();}
    public static void reserve(IndustryBlockEntity be){be.processTicks=duration(be,be.processTicks);be.processCost=cost(be,be.processCost);}
}
