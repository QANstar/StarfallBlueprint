package cn.starfall.blueprint;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Reserve inputs once, pause under backpressure, and settle all material/fluid outputs once. */
public final class IndustryProcessing {
    public static void tick(IndustryBlockEntity be){
        if(!Set.of("phase_extractor","press","quarry","crusher","plant_cultivator","refiner","recycler","bio_processor","autocrafter","auto_furnace","prep").contains(be.kind()))return;
        if(!ResonanceBases.canRun(be))return;
        // The former single-block drill already consumed its source; settle that reserved batch once.
        boolean legacyDrill=be.kind().equals("phase_extractor")&&!be.idle()&&be.activeRecipe.startsWith("drill:");
        if(AggregateMachines.large(be.kind())&&!legacyDrill&&!AggregateMachines.ready(be))return;
        if(be.kind().equals("plant_cultivator"))fillCultivationBucket(be);
        // Preserve legacy in-flight batches without completing or consuming more resources.
        var active = CatalogueContent.RECIPES.get(be.activeRecipe);
        if ((active != null && !CatalogueContent.isRecipeEnabled(active))
                || be.pending.stream().anyMatch(s -> !s.isEmpty() && CatalogueContent.RESERVED_ITEMS.contains(CatalogueContent.key(s)))) {
            be.status=8; return;
        }
        if(be.idle()) {
            if(be.kind().equals("phase_extractor")){if(!PhaseExtractor.start(be))return;}
            else if(be.kind().equals("plant_cultivator")){if(!PlantCultivation.start(be))return;}
            else if(be.kind().equals("autocrafter")){if(!startCraft(be))return;}
            else if(be.kind().equals("auto_furnace")){if(!startSmelting(be))return;}
            else if(!startProcess(be))return;
            MachineUpgrades.reserve(be);
        }
        List<ItemStack> outputs=be.pending.stream().filter(s->!s.isEmpty()).toList();
        if(!be.outputFits(outputs)||!fluidsFit(be)){be.status=5;return;}
        long target=MachineUpgrades.paymentTarget(be.processCost,be.progressTicks+1,be.processTicks);
        long cost=target-be.processPaid;
        if(be.kind().equals("auto_furnace")) {
            long need=Math.max(0,cost-be.heatCredit),grant=Math.min(need,Math.min(1600,be.getPower()));
            if(grant>0){be.setPower(be.getPower()-grant);be.heatCredit+=grant;}
            if(be.heatCredit<cost) {
                if(be.fuelHeat==0&&PreparationBlockEntity.isFuel(be.stock.get(13))){be.stock.get(13).shrink(1);be.fuelHeat=320000;}
                long heat=Math.min(Math.min(500,be.fuelHeat),cost-be.heatCredit);be.fuelHeat-=heat;be.heatCredit+=heat;
            }
            if(be.heatCredit<cost){be.status=4;be.setChanged();return;}
            be.heatCredit-=cost;
        } else {
            if(be.getPower()<cost&&be.kind().equals("prep")&&PreparationBlockEntity.isFuel(be.stock.get(13))) {be.stock.get(13).shrink(1);be.setPower(be.getPower()+320000);}
            // Large upgrade stacks may price one work step above the internal buffer.
            // Persist partial payment so repeated energy deliveries can still complete it.
            if(be.getPower()<cost){if(cost>be.capacity()){be.processPaid+=be.getPower();be.setPower(0);be.setChanged();}be.status=4;return;}be.setPower(be.getPower()-cost);
        }
        be.processPaid=target;be.progressTicks++;be.status=1;
        if(be.progressTicks>=be.processTicks){
            be.planOutputs(outputs,true);
            if(!be.pendingFluid1.isEmpty())be.tanks[1].fill(be.pendingFluid1,FluidAction.EXECUTE);
            if(!be.pendingFluid2.isEmpty())be.tanks[2].fill(be.pendingFluid2,FluidAction.EXECUTE);
            be.pending=NonNullList.withSize(4,ItemStack.EMPTY);be.pendingFluid1=FluidStack.EMPTY;be.pendingFluid2=FluidStack.EMPTY;
            be.activeRecipe="";be.progressTicks=0;be.processPaid=0;be.status=0;
            be.experience+=be.pendingExperience;be.pendingExperience=0;
        }
        be.setChanged();
    }
    private static void fillCultivationBucket(IndustryBlockEntity be){
        if(!be.stock.get(2).is(Items.WATER_BUCKET))return;
        var water=new FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000);
        var empty=List.of(new ItemStack(Items.BUCKET));
        if(be.tanks[0].fill(water,FluidAction.SIMULATE)!=1000||!be.outputFits(empty))return;
        be.stock.get(2).shrink(1);be.tanks[0].fill(water,FluidAction.EXECUTE);be.planOutputs(empty,true);be.setChanged();
    }
    private static boolean fluidsFit(IndustryBlockEntity be){return (be.pendingFluid1.isEmpty()||be.tanks[1].fill(be.pendingFluid1,FluidAction.SIMULATE)==be.pendingFluid1.getAmount())&&(be.pendingFluid2.isEmpty()||be.tanks[2].fill(be.pendingFluid2,FluidAction.SIMULATE)==be.pendingFluid2.getAmount());}
    public static List<JsonObject> available(IndustryBlockEntity be){return CatalogueContent.RECIPES.values().stream().filter(CatalogueContent::isRecipeEnabled).filter(r->r.get("station").getAsString().equals(be.kind())&&!r.get("id").getAsString().equals("PC-R01"))
            .filter(r->!be.kind().equals("quarry")||ResonanceBases.matches(be,r)).toList();}
    private static boolean startProcess(IndustryBlockEntity be){
        be.status=2;
        for(JsonObject r:available(be)) {
            if(!be.kind().equals("prep")&&!be.kind().equals("press")&&!AggregateMachines.large(be.kind())&&!be.template.isEmpty()&&!r.get("id").getAsString().equals(be.template))continue;
            int[]used=new int[9];boolean match=true;ItemStack qualitySource=ItemStack.EMPTY;int fluidNeed=0,reuse=0;
            for(var in:r.getAsJsonArray("inputs")) {
                String key=CatalogueContent.key(in);int remaining=CatalogueContent.qty(in);
                if(key.equals("water")){fluidNeed+=remaining;continue;}
                if(key.equals("log")&&r.get("id").getAsString().equals("BIO-R01")&&be.stock.subList(0,9).stream().noneMatch(s->CatalogueContent.matches("log",s))){
                    Item crop=null;for(Item candidate:List.of(Items.WHEAT,Items.CARROT,Items.POTATO))if(be.stock.subList(0,9).stream().filter(s->s.is(candidate)).mapToInt(ItemStack::getCount).sum()>=4){crop=candidate;break;}
                    if(crop==null){match=false;break;}remaining=4;for(int i=0;i<9&&remaining>0;i++)if(be.stock.get(i).is(crop)){int n=Math.min(remaining,be.stock.get(i).getCount());used[i]=n;remaining-=n;}continue;
                }
                for(int i=0;i<9&&remaining>0;i++)if(CatalogueContent.matches(key,be.stock.get(i))) {
                    int n=Math.min(remaining,be.stock.get(i).getCount()-used[i]);
                    if(n>0){used[i]+=n;remaining-=n;if(key.equals("common")||key.equals("refined")||qualitySource.isEmpty()&&be.stock.get(i).has(CatalogueContent.QUALITY.get()))qualitySource=be.stock.get(i);}
                }
                if(remaining>0){match=false;break;}
            }
            if(!match)continue;
            if(!r.get("id").getAsString().equals("RC-R01")){boolean dirty=false;for(int i=0;i<9;i++)if(used[i]>0&&MaterialQuality.of(be.stock.get(i)).contamination()!=0)dirty=true;if(dirty){be.status=7;continue;}}
            String id=r.get("id").getAsString();
            boolean invalid=false;ItemStack combined=ItemStack.EMPTY;
            for(int i=0;i<9;i++)if(used[i]>0&&(CatalogueContent.matches("common",be.stock.get(i))||CatalogueContent.matches("refined",be.stock.get(i)))){
                var source=be.stock.get(i);if(!quality(id,source)){invalid=true;break;}
                if(combined.isEmpty())combined=source.copyWithCount(1);else{
                    var a=MaterialQuality.of(combined);var b=MaterialQuality.of(source);
                    combined.set(CatalogueContent.QUALITY.get(),new MaterialQuality(Math.min(a.purity(),b.purity()),Math.min(a.integrity(),b.integrity()),Math.min(a.orientation(),b.orientation()),Math.max(a.contamination(),b.contamination()),a.continuous()&&b.continuous(),a.route()));
                    var data=CatalogueEquipment.getData(combined);String origin=CatalogueEquipment.getData(source).getString("Origin");if(!data.getString("Origin").equals(origin)){data.putString("Origin","mixed");CatalogueEquipment.setData(combined,data);}
                }
            }
            if(invalid){be.status=7;continue;}if(!combined.isEmpty())qualitySource=combined;
            if(!quality(id,qualitySource)){be.status=7;continue;}
            if(id.equals("BIO-R01")&&be.tanks[2].getFluid().is(CatalogueFluids.fluid("returnwater"))){reuse=Math.min(fluidNeed,be.tanks[2].getFluidAmount());fluidNeed-=reuse;}
            if(fluidNeed>0&&(!be.tanks[0].getFluid().is(CatalogueFluids.fluid("water"))||be.tanks[0].getFluidAmount()<fluidNeed)){be.status=3;continue;}
            var tools=r.getAsJsonArray("tools");List<Integer>wear=new ArrayList<>();boolean toolsReady=true;
            for(var t:tools){String key=t.getAsString();int index=-1;for(int i=14;i<=15;i++)if(CatalogueContent.matches(key,be.stock.get(i))&&(!be.stock.get(i).isDamageableItem()||be.stock.get(i).getDamageValue()<be.stock.get(i).getMaxDamage())){index=i;break;}
                if(index<0){toolsReady=false;break;}wear.add(index);}
            if(!toolsReady){be.status=6;continue;}
            List<ItemStack>outs=new ArrayList<>();FluidStack f1=FluidStack.EMPTY,f2=FluidStack.EMPTY;
            for(var o:r.getAsJsonArray("outputs")) {
                String key=CatalogueContent.key(o);int n=CatalogueContent.qty(o);
                if(key.equals("warm")||key.equals("returnwater")){f1=new FluidStack(CatalogueFluids.fluid(key),n);continue;}
                if(key.equals("wastewater")){f2=new FluidStack(CatalogueFluids.fluid(key),n);continue;}
                if(key.equals("bio")){f1=new FluidStack(CatalogueFluids.fluid(key),n);continue;}
                ItemStack out=be.kind().equals("quarry")?ResonanceBases.output(be,r):CatalogueContent.stack(key,n);
                if(out.isEmpty()){invalid=true;break;}
                inheritQuality(id,key,qualitySource,out);outs.add(out);
            }
            if(invalid)continue;
            // A process may have two clean liquid outputs (bio + return water).
            if(id.equals("BIO-R01")){f1=new FluidStack(CatalogueFluids.fluid("bio"),1000);f2=new FluidStack(CatalogueFluids.fluid("returnwater"),1000);}
            if(outs.size()>4||!be.outputFits(outs)||(!f1.isEmpty()&&be.tanks[1].fill(f1,FluidAction.SIMULATE)!=f1.getAmount())||(!f2.isEmpty()&&(reuse>0?be.tanks[2].getFluidAmount()-reuse+f2.getAmount()>be.tanks[2].getCapacity():be.tanks[2].fill(f2,FluidAction.SIMULATE)!=f2.getAmount()))){be.status=5;continue;}
            long cost=r.get("energy").getAsLong();
            if(be.getPower()==0&&!(be.kind().equals("prep")&&PreparationBlockEntity.isFuel(be.stock.get(13)))){be.status=4;continue;}
            for(int i=0;i<9;i++)be.stock.get(i).shrink(used[i]);
            if(fluidNeed>0)be.tanks[0].drain(fluidNeed,FluidAction.EXECUTE);
            if(reuse>0)be.tanks[2].drain(reuse,FluidAction.EXECUTE);
            for(int index:wear){ItemStack tool=be.stock.get(index);if(tool.isDamageableItem()){tool.setDamageValue(tool.getDamageValue()+1);if(tool.getDamageValue()>=tool.getMaxDamage())tool.shrink(1);}}
            reserve(be,id,outs,(int)Math.ceil(r.get("seconds").getAsDouble()*20),cost);be.pendingFluid1=f1;be.pendingFluid2=f2;return true;
        }
        return false;
    }
    private static boolean quality(String id,ItemStack s){
        if(id.startsWith("AUX-")||Set.of("PM-09","PM-12","BIO-R01","RC-R01").contains(id))return true;
        if(s.isEmpty())return true;
        var q=MaterialQuality.of(s);if(q.contamination()!=0)return false;
        if(Set.of("PM-05","PM-11","PC-R03").contains(id)){
            var b=s.get(StarfallBlueprint.BATCH.get());if(b==null||!b.isQ0())return false;
            return !id.equals("PM-11")||q.integrity()>=21;
        }
        int minimum=switch(id){case "PM-02"->51;case "PM-03"->62;case "PM-04"->72;case "PM-06","PM-07","PM-08"->81;default->0;};
        return q.purity()>=(Set.of("PM-06","PM-07","PM-08").contains(id)?99:95)&&q.integrity()>=minimum&&(!Set.of("PM-04","PM-06","PM-07","PM-08").contains(id)||q.continuous());
    }
    private static void inheritQuality(String id,String key,ItemStack source,ItemStack out){
        if(source.isEmpty())return;var q=MaterialQuality.of(source);
        int loss=switch(id){case "PC-R03"->3;case "PM-03","PM-04"->2;default->1;};
        String route=switch(id){case "PM-02","PM-08"->"conductor";case "PM-03"->"load";case "PM-04"->"laminar";case "PM-06"->"sensor";case "PM-07","PM-11"->"anima";default->"common";};
        int orientation=switch(id){case "PM-02","PM-04"->60;case "PM-03","PM-11"->40;case "PM-06","PM-07","PM-08"->80;default->q.orientation();};
        out.set(CatalogueContent.QUALITY.get(),new MaterialQuality(id.equals("PC-R03")?99:q.purity(),Math.max(0,q.integrity()-loss),orientation,q.contamination(),q.continuous(),route));
        var sourceData=CatalogueEquipment.getData(source);if(sourceData.contains("Origin")){var data=CatalogueEquipment.getData(out);data.putString("Origin",sourceData.getString("Origin"));CatalogueEquipment.setData(out,data);}
        if(id.equals("RC-R01"))out.set(CatalogueContent.QUALITY.get(),new MaterialQuality(q.purity(),Math.min(30,q.integrity()),0,q.contamination(),false,"reclaimed"));
    }
    private static void reserve(IndustryBlockEntity be,String id,List<ItemStack> outputs,int ticks,long cost){be.activeRecipe=id;be.progressTicks=0;be.processTicks=Math.max(1,ticks);be.processCost=cost;be.processPaid=0;be.pending=NonNullList.withSize(4,ItemStack.EMPTY);for(int i=0;i<outputs.size();i++)be.pending.set(i,outputs.get(i));be.setChanged();}
    private static boolean startCraft(IndustryBlockEntity be){
        be.selectCraftingSample();
        if(be.template.isEmpty()){be.status=2;return false;}
        var key=net.minecraft.resources.ResourceLocation.tryParse(be.template);
        if(key==null){be.status=2;return false;}
        var holder=be.getLevel().getRecipeManager().byKey(key);
        if(holder.isEmpty()){be.status=2;return false;}
        int[] slots=be.craftingInputSlots();
        List<ItemStack> actual=Arrays.stream(slots).mapToObj(be::getItem).toList();
        var plan=AutomaticCrafting.plan(holder.get().value(),actual,be.getLevel());
        if(plan.isEmpty()){be.status=2;return false;}
        if(plan.get().outputs().stream().anyMatch(s->CatalogueContent.RESERVED_ITEMS.contains(CatalogueContent.key(s)))){be.status=8;return false;}
        var outputs=AutomaticCrafting.packOutputs(plan.get().outputs());
        if(outputs.isEmpty()||!be.outputFits(outputs.get())){be.status=5;return false;}
        if(be.getPower()==0){be.status=4;return false;}
        for(int i=0;i<slots.length;i++)be.getItem(slots[i]).shrink(plan.get().consumed().get(i));
        reserve(be,"craft:"+be.template,outputs.get(),40,4000);return true;
    }
    private static boolean startSmelting(IndustryBlockEntity be){
        long bulk=MachineUpgrades.bulk(be),lanes=2+2*bulk;
        int duration=400;
        long costPer=40000L*(100+5L*bulk)/100;
        List<ItemStack>out=new ArrayList<>();int[]used=new int[9];int count=0;float experience=0;
        for(int i=0;i<9&&count<lanes;i++){
            ItemStack s=be.stock.get(i);if(s.isEmpty())continue;
            var r=be.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING,new SingleRecipeInput(s),be.getLevel());if(r.isEmpty())continue;
            for(int j=0;j<s.getCount()&&count<lanes;j++){
                var candidate=new ArrayList<>(out);candidate.add(r.get().value().assemble(new SingleRecipeInput(s),be.getLevel().registryAccess()));
                if(!be.outputFits(candidate))break;out=candidate;used[i]++;count++;experience+=r.get().value().getExperience();
            }
        }
        if(count==0){be.status=2;return false;}
        if(be.getPower()==0&&be.fuelHeat==0&&!PreparationBlockEntity.isFuel(be.stock.get(13))){be.status=4;return false;}
        // More than four lanes can merge into the same result stack.
        List<ItemStack>merged=new ArrayList<>();for(ItemStack s:out){ItemStack found=merged.stream().filter(a->ItemStack.isSameItemSameComponents(a,s)&&a.getCount()+s.getCount()<=a.getMaxStackSize()).findFirst().orElse(null);if(found==null)merged.add(s.copy());else found.grow(s.getCount());}
        if(merged.size()>4){be.status=5;return false;}
        for(int i=0;i<9;i++)be.stock.get(i).shrink(used[i]);reserve(be,"smelting",merged,duration,costPer*count);be.pendingExperience=experience;return true;
    }
}
