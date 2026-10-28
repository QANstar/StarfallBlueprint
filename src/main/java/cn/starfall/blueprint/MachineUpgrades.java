package cn.starfall.blueprint;

import java.util.Set;
import java.math.BigInteger;
import net.minecraft.world.item.ItemStack;

/** Shared, manually serviced upgrade bay. Effects are frozen when a batch is reserved. */
public final class MachineUpgrades {
    private MachineUpgrades() {}
    private static final Set<String> PROCESSORS=Set.of("prep","press","quarry","crusher","phase_extractor","plant_cultivator","refiner","recycler","bio_processor","autocrafter","auto_furnace","auto_brewer","auto_smith","auto_stonecutter","auto_blast_furnace","auto_smoker");
    private static final Set<String> UTILITIES=Set.of("s02","g01","g02","g03","g04","g05","g06","g07","pump","dew");
    public static boolean supports(String kind){return PROCESSORS.contains(kind)||UTILITIES.contains(kind);}
    public static boolean isSlot(String kind,int index){return supports(kind)&&(index==16||index==17||index==20||index==21||kind.equals("quarry")&&index==18||kind.equals("prep")&&index==19);}
    public static boolean idle(IndustryBlockEntity be){return be.idle();}
    private static boolean is(ItemStack stack,String... keys){for(String key:keys)if(stack.is(CatalogueContent.item(key)))return true;return false;}
    public static boolean accepts(IndustryBlockEntity be,ItemStack stack){
        if(!supports(be.kind())||!idle(be)||stack.isEmpty())return false;
        if(is(stack,"capacity_1","capacity_2"))return true;
        if(is(stack,"intake_1","intake_2"))return !be.generator();
        if(is(stack,"fluid_capacity_1","fluid_capacity_2"))return be.hasFluidPort();
        if(!PROCESSORS.contains(be.kind()))return false;
        if(is(stack,"speed_1","speed_2","energy_saving_1","energy_saving_2","efficiency_1","efficiency_2")||stack.is(StarfallBlueprint.CONNECTOR.get()))return true;
        if(be.kind().equals("auto_furnace")&&is(stack,"bulk_1","bulk_2"))return true;
        if(!be.kind().equals("quarry"))return false;
        if(is(stack,"resonance_upgrade"))return true;
        if(is(stack,"silk_touch"))return count(be,"fortune_1")+count(be,"fortune_2")+count(be,"fortune_3")+count(be,"auto_smelt")==0;
        return is(stack,"fortune_1","fortune_2","fortune_3","auto_smelt")&&count(be,"silk_touch")==0;
    }
    public static int maxStack(ItemStack stack){return is(stack,"resonance_upgrade","silk_touch","auto_smelt","fortune_1","fortune_2","fortune_3")?1:64;}
    public static long count(IndustryBlockEntity be,String key){long count=0;for(int i=0;i<be.upgrades.getContainerSize();i++)if(be.upgrades.getItem(i).is(CatalogueContent.item(key)))count+=be.upgrades.getItem(i).getCount();return count;}
    public static long connectors(IndustryBlockEntity be){long count=0;for(int i=0;i<be.upgrades.getContainerSize();i++)if(be.upgrades.getItem(i).is(StarfallBlueprint.CONNECTOR.get()))count+=be.upgrades.getItem(i).getCount();return count;}
    public static long speed(IndustryBlockEntity be){return count(be,"speed_1")+2*count(be,"speed_2")+count(be,"efficiency_1")+2*count(be,"efficiency_2");}
    public static long bulk(IndustryBlockEntity be){return count(be,"bulk_1")+2*count(be,"bulk_2");}
    private static BigInteger ceil(BigInteger value,long divisor){return value.add(BigInteger.valueOf(divisor-1)).divide(BigInteger.valueOf(divisor));}
    public static int duration(IndustryBlockEntity be,int ticks){
        long slowdown=100+10*count(be,"energy_saving_1")+20*count(be,"energy_saving_2");
        return ceil(BigInteger.valueOf(ticks).multiply(BigInteger.valueOf(slowdown)),100+25*speed(be)).max(BigInteger.ONE).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
    }
    public static long cost(IndustryBlockEntity be,long base){
        long premium=100L+10L*count(be,"speed_1")+25L*count(be,"speed_2");
        long saving=connectors(be)+2*count(be,"energy_saving_1")+4*count(be,"energy_saving_2")+2*count(be,"efficiency_1")+4*count(be,"efficiency_2");
        BigInteger accelerated=ceil(BigInteger.valueOf(base).multiply(BigInteger.valueOf(premium)),100);
        return ceil(accelerated.multiply(BigInteger.valueOf(9)),9+saving).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
    public static int baseCapacity(String kind){return kind.equals("s02")?2_000_000:kind.equals("g05")?1_000_000:320_000;}
    private static int expanded(int base,long weight,int percent){return (int)Math.min(Integer.MAX_VALUE,(long)base+((long)base*percent/100)*weight);}
    private static long weight(IndustryBlockEntity be,String key){return count(be,key+"_1")+2*count(be,key+"_2");}
    public static int capacity(IndustryBlockEntity be){return expanded(baseCapacity(be.kind()),weight(be,"capacity"),50);}
    public static int receiveLimit(IndustryBlockEntity be,int base){return expanded(base,weight(be,"intake"),25);}
    public static int fluidCapacity(IndustryBlockEntity be){return expanded(4000,weight(be,"fluid_capacity"),50);}
    /** Removing an expansion must not silently destroy its stored contents. */
    public static boolean canRemove(IndustryBlockEntity be,ItemStack stack){
        if(!idle(be))return false;
        if(is(stack,"capacity_1","capacity_2")){
            long remaining=weight(be,"capacity")-(stack.is(CatalogueContent.item("capacity_2"))?2L:1L)*stack.getCount();
            if(be.getPower()>(long)expanded(baseCapacity(be.kind()),Math.max(0,remaining),50)+(be.kind().equals("prep")?199:0))return false;
        }
        if(is(stack,"fluid_capacity_1","fluid_capacity_2")){
            long remaining=weight(be,"fluid_capacity")-(stack.is(CatalogueContent.item("fluid_capacity_2"))?2L:1L)*stack.getCount();
            int limit=expanded(4000,Math.max(0,remaining),50);
            for(var tank:be.tanks)if(tank.getFluidAmount()>limit)return false;
        }
        return true;
    }
    public static java.util.List<String> description(String key){
        return switch(key){
            case "speed_1" -> java.util.List.of("加工速度 +25%，单批能耗 +10%");
            case "speed_2" -> java.util.List.of("加工速度 +50%，单批能耗 +25%");
            case "energy_saving_1" -> java.util.List.of("省能权重 +2；减速权重 +10%","无提速时工时延长10%，省能按9/(9+总权重)");
            case "energy_saving_2" -> java.util.List.of("省能权重 +4；减速权重 +20%","无提速时工时延长20%，省能按9/(9+总权重)");
            case "efficiency_1" -> java.util.List.of("加工速度 +25%，省能权重 +2","不附带超频增耗或节能减速");
            case "efficiency_2" -> java.util.List.of("加工速度 +50%，省能权重 +4","不附带超频增耗或节能减速");
            case "capacity_1","capacity_2" -> java.util.List.of("每件增加基础储能容量的"+(key.endsWith("2")?100:50)+"%","取下前请腾出对应储能空间");
            case "intake_1","intake_2" -> java.util.List.of("每件增加基础能量接收速率的"+(key.endsWith("2")?50:25)+"%","电源与线路的共享供能上限仍然有效");
            case "fluid_capacity_1","fluid_capacity_2" -> java.util.List.of("每件增加基础液槽容量的"+(key.endsWith("2")?100:50)+"%","仅有液槽的设备接受；取下前请腾出空间");
            case "fortune_1","fortune_2","fortune_3" -> java.util.List.of("地脉共振仪：时运 "+key.charAt(key.length()-1)+" 级","只取最高等级；与精准采集互斥，可配合炽热熔炼");
            case "silk_touch" -> java.util.List.of("地脉共振仪：按原版精准采集掉落","与富矿采集、炽热熔炼互斥");
            case "auto_smelt" -> java.util.List.of("地脉共振仪：按实际熔炉配方熔炼采集产物","每件熔炼先加100XH基础能耗，再按升级修正","可配合富矿采集；与精准采集互斥");
            case "bulk_1","bulk_2" -> java.util.List.of("仅并列熔炼炉接受；按件数增加真实加工批量");
            case "resonance_upgrade" -> java.util.List.of("地脉共振仪：开放其他原版共振基底","每格1件；重复安装不增加产量");
            default -> java.util.List.of();
        };
    }
    public static long paymentTarget(long cost,int step,int duration){return BigInteger.valueOf(cost).multiply(BigInteger.valueOf(step)).divide(BigInteger.valueOf(duration)).longValueExact();}
    public static void reserve(IndustryBlockEntity be){be.processTicks=duration(be,be.processTicks);be.processCost=cost(be,be.processCost);}
}
