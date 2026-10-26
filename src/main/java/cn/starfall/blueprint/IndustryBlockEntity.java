package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class IndustryBlockEntity extends BlockEntity implements WorldlyContainer,MenuProvider {
    public static final int CRAFTING_SAMPLE=19;
    /** Sample is an owned item, outside all 54 consumable input slots. Empty keeps the last preset. */
    public void selectCraftingSample(){
        if(!kind().equals("autocrafter")||level==null||level.isClientSide)return;
        var sample=stock.get(CRAFTING_SAMPLE);if(sample.isEmpty())return;
        var key=net.minecraft.resources.ResourceLocation.tryParse(template);
        var current=key==null?Optional.<net.minecraft.world.item.crafting.RecipeHolder<?>>empty():level.getRecipeManager().byKey(key);
        if(current.isPresent()&&AutomaticCrafting.isWorkbenchRecipe(current.get().value())&&current.get().value().getResultItem(level.registryAccess()).is(sample.getItem()))return;
        var choices=AutomaticCrafting.forResult(level,sample);String selected=choices.isEmpty()?"":choices.getFirst().id().toString();
        if(!template.equals(selected)){template=selected;setChanged();}
    }
    public NonNullList<ItemStack> stock=NonNullList.withSize(27,ItemStack.EMPTY),pending=NonNullList.withSize(4,ItemStack.EMPTY);
    private NonNullList<ItemStack> extraCraftingInputs=NonNullList.withSize(45,ItemStack.EMPTY);
    /** Logical input indices; the original nine slots remain compatible with existing saves. */
    public int[] craftingInputSlots(){return java.util.stream.IntStream.concat(java.util.stream.IntStream.range(0,9),java.util.stream.IntStream.range(27,72)).toArray();}
    public final SimpleContainer filters=new SimpleContainer(9){public void setChanged(){super.setChanged();IndustryBlockEntity.this.setChanged();}};
    public final FluidTank[] tanks=new FluidTank[3];
    public FluidStack pendingFluid1=FluidStack.EMPTY,pendingFluid2=FluidStack.EMPTY;
    public int progressTicks,processTicks=1,status,faceMask=63,retain,filterMode,rotationCursor,peakTicks,peakCooldown;
    public long powerStored,processCost,processPaid,heatCredit;
    public int fuelHeat,chargeRemainder;
    public float experience,pendingExperience;
    public boolean stopped,efficientCharge;
    public String activeRecipe="",template="",fluidFilter="";
    private long inputTick=-1,outputTick=-1,peakCountedTick=-1;
    private int inputThisTick,outputThisTick;
    public static final List<String> KINDS=java.util.stream.Stream.concat(CatalogueContent.MACHINE_KEYS.stream(),java.util.stream.Stream.of("prep")).sorted().toList();
    private int templateNumber(){if(kind().equals("autocrafter")&&level!=null){var list=AutomaticCrafting.recipes(level).stream().map(r->r.id().toString()).toList();return list.indexOf(template);}return new ArrayList<>(CatalogueContent.RECIPES.keySet()).indexOf(template);}
    public final ContainerData processData=new ContainerData(){
        public int get(int i){return switch(i){case 0->(int)(getPower()&65535);case 1->(int)(getPower()>>>16);case 2->progressTicks;case 3->processTicks;case 4->status;case 5->retain;case 6->stopped?1:0;case 7->filterMode;case 8->KINDS.indexOf(kind());case 9,10,11->tanks[i-9].getFluidAmount();case 12->templateNumber();case 13->faceMask;case 14->List.of("","water","warm","wastewater","bio","returnwater").indexOf(fluidFilter);default->0;};}
        public void set(int i,int v){} public int getCount(){return 15;}
    };
    public IndustryBlockEntity(BlockPos p,BlockState s){this(CatalogueContent.ENTITY.get(),p,s);}
    protected IndustryBlockEntity(BlockEntityType<?>type,BlockPos p,BlockState s){super(type,p,s);for(int i=0;i<3;i++)tanks[i]=new FluidTank(4000){protected void onContentsChanged(){setChanged();}};if(kind().equals("refiner"))stock.set(14,CatalogueContent.stack("head",1));}
    public String kind(){return getBlockState().getBlock()instanceof IndustryBlock b?b.kind:"prep";}
    public IndustryInventory workInventory(){return new IndustryInventory(this);}
    @Override public void onLoad(){
        super.onLoad();
        if(level!=null&&!level.isClientSide&&getBlockState().getBlock() instanceof ConduitBlock block)level.scheduleTick(worldPosition,block,1);
    }
    public Direction facing(){return getBlockState().hasProperty(IndustryBlock.FACING)?getBlockState().getValue(IndustryBlock.FACING):Direction.NORTH;}
    public long getPower(){return powerStored;}
    public void setPower(long value){powerStored=Math.clamp(value,0L,capacity());setChanged();}
    public int capacity(){return kind().equals("s02")?2_000_000:kind().equals("g05")?1_000_000:320_000;}
    public boolean generator(){return Set.of("g01","g02","g03","g04","g05","g06","g07").contains(kind());}
    public boolean idle(){return activeRecipe.isEmpty();}
    public boolean connectionOpen(Direction side){return !ConduitBlock.isConduit(kind())||ConduitBlock.hasPort(kind(),facing(),side);}
    public boolean hasItemFilter(){return Set.of("item_extractor","item_router","supply_crate").contains(kind());}
    public boolean hasFluidPort(){return Set.of("phase_extractor","prep","refiner","bio_processor","g05","plant_cultivator","pump","dew").contains(kind());}
    public boolean hasEnergyPort(){return !ConduitBlock.isConduit(kind())&&!Set.of("damper","supply_crate").contains(kind());}
    public boolean redstoneStopped(){if(stopped)return true;if(level==null)return false;if(AggregateMachines.large(kind()))for(int p=0;p<LargeMachineBlock.parts(kind());p++){var pos=worldPosition.offset(LargeMachineBlock.offset(facing(),p));if(level.hasChunkAt(pos)&&level.hasNeighborSignal(pos))return true;}return level.hasNeighborSignal(worldPosition);}
    public static void tick(Level level,BlockPos pos,BlockState state,IndustryBlockEntity be){
        if(be.redstoneStopped()){be.status=8;}else{IndustryEnergy.tick(be);IndustryProcessing.tick(be);}
        IndustryNetworks.tick(be);
        boolean lit=be.status==1;if(state.hasProperty(IndustryBlock.ACTIVE)&&state.getValue(IndustryBlock.ACTIVE)!=lit){if(AggregateMachines.large(be.kind()))AggregateMachines.syncActive(be,lit);else level.setBlock(pos,state.setValue(IndustryBlock.ACTIVE,lit),3);}
    }
    public IEnergyStorage energyPort(Direction side){return new IEnergyStorage(){
        private int receiveLimit(){return kind().equals("s02")&&peakCooldown==0&&peakTicks<100?2000:kind().equals("s02")?1000:2048;}
        private int extractLimit(){return switch(kind()){case "g01"->500;case "g02","g07"->160;case "g03","g04"->100;case "g05"->2000;case "g06"->200;case "s02"->peakCooldown==0&&peakTicks<100?2000:1000;default->0;};}
        public int receiveEnergy(int max,boolean simulate){
            if(!canReceive()||max<=0)return 0;
            long now=level==null?0:level.getGameTime();int used=inputTick==now?inputThisTick:0;
            int efficiency=kind().equals("s02")?(efficientCharge?95:90):100;
            int n=(int)Math.min(Math.min(max,Math.max(0,receiveLimit()-used)),Math.max(0,(capacity()-getPower())*100/efficiency));
            if(!simulate&&n>0){if(inputTick!=now){inputTick=now;inputThisTick=0;}inputThisTick+=n;long total=(long)n*efficiency+chargeRemainder;setPower(getPower()+total/100);chargeRemainder=(int)(total%100);}
            return n;
        }
        public int extractEnergy(int max,boolean simulate){
            if(!canExtract()||max<=0)return 0;long now=level==null?0:level.getGameTime();int used=outputTick==now?outputThisTick:0;
            int n=(int)Math.min(Math.min(max,Math.max(0,extractLimit()-used)),getPower());
            if(!simulate&&n>0){if(outputTick!=now){outputTick=now;outputThisTick=0;}outputThisTick+=n;setPower(getPower()-n);if(kind().equals("s02")&&outputThisTick>1000&&peakCountedTick!=now){peakCountedTick=now;peakTicks++;if(peakTicks>=100)peakCooldown=200;}}
            return n;
        }
        public int getEnergyStored(){return (int)getPower();}public int getMaxEnergyStored(){return capacity();}
        public boolean canExtract(){return !redstoneStopped()&&(generator()||kind().equals("s02"));}
        public boolean canReceive(){return !redstoneStopped()&&!generator()&&!Set.of("cable","pipe","item_duct","item_extractor","item_router","fluid_valve","energy_junction","supply_crate").contains(kind());}
    };}
    public IFluidHandler fluidPort(){return new IFluidHandler(){
        public int getTanks(){return 3;}public FluidStack getFluidInTank(int i){return tanks[i].getFluid();}public int getTankCapacity(int i){return 4000;}
        public boolean isFluidValid(int i,FluidStack s){return i==0&&Set.of("phase_extractor","plant_cultivator","prep","refiner","bio_processor","g05").contains(kind())&&s.is(CatalogueFluids.fluid(kind().equals("g05")?"bio":"water"));}
        public int fill(FluidStack s,FluidAction a){return !redstoneStopped()&&isFluidValid(0,s)?tanks[0].fill(s,a):0;}
        public FluidStack drain(FluidStack s,FluidAction a){for(int i:outlets())if(FluidStack.isSameFluidSameComponents(tanks[i].getFluid(),s))return tanks[i].drain(s.getAmount(),a);return FluidStack.EMPTY;}
        public FluidStack drain(int n,FluidAction a){for(int i:outlets())if(!tanks[i].isEmpty())return tanks[i].drain(n,a);return FluidStack.EMPTY;}
        private int[]outlets(){return Set.of("pump","dew").contains(kind())?new int[]{0}:new int[]{1,2};}
    };}
    public boolean filterMatches(ItemStack f,ItemStack s){if(!f.is(s.getItem()))return false;var a=MaterialQuality.of(f);var b=MaterialQuality.of(s);return switch(filterMode){case 1->a.route().equals(b.route());case 2->b.purity()>=a.purity()&&b.integrity()>=a.integrity()&&b.contamination()<=a.contamination()&&(!a.continuous()||b.continuous());case 3->CatalogueEquipment.getData(f).getString("Origin").equals(CatalogueEquipment.getData(s).getString("Origin"));case 4->ItemStack.isSameItemSameComponents(f,s);case 5->b.contamination()<=a.contamination();default->true;};}
    public boolean acceptsFilter(ItemStack s){if(!hasItemFilter())return true;boolean any=false;for(int i=0;i<9;i++){ItemStack f=filters.getItem(i);if(f.isEmpty())continue;any=true;if(filterMatches(f,s))return true;}return !any;}
    public boolean outputFits(List<ItemStack> outputs){return planOutputs(outputs,false);}
    public boolean planOutputs(List<ItemStack> outputs,boolean commit){
        NonNullList<ItemStack> simulated=NonNullList.withSize(4,ItemStack.EMPTY);for(int i=0;i<4;i++)simulated.set(i,stock.get(9+i).copy());
        for(ItemStack supplied:outputs){ItemStack s=supplied.copy();for(int pass=0;pass<2;pass++)for(int i=0;i<4&&!s.isEmpty();i++){
            ItemStack current=simulated.get(i);if(pass==0&&!current.isEmpty()&&ItemStack.isSameItemSameComponents(current,s)){int n=Math.min(s.getCount(),current.getMaxStackSize()-current.getCount());current.grow(n);s.shrink(n);}
            if(pass==1&&current.isEmpty()){int n=Math.min(s.getCount(),s.getMaxStackSize());simulated.set(i,s.copyWithCount(n));s.shrink(n);}
        }if(!s.isEmpty())return false;}
        if(commit){for(int i=0;i<4;i++)stock.set(9+i,simulated.get(i));setChanged();}return true;
    }
    protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider){
        super.saveAdditional(tag,provider);CompoundTag inv=new CompoundTag();ContainerHelper.saveAllItems(inv,stock,provider);tag.put("IndustryInventory",inv);
        CompoundTag extra=new CompoundTag();ContainerHelper.saveAllItems(extra,extraCraftingInputs,provider);tag.put("ExtraCraftingInputs",extra);
        CompoundTag output=new CompoundTag();ContainerHelper.saveAllItems(output,pending,provider);tag.put("IndustryPending",output);
        tag.putLong("Power",getPower());tag.putLong("Cost",processCost);tag.putLong("Paid",processPaid);tag.putLong("HeatCredit",heatCredit);tag.putInt("FuelHeat",fuelHeat);tag.putInt("ChargeRemainder",chargeRemainder);
        tag.putInt("WorkProgress",progressTicks);tag.putInt("Duration",processTicks);tag.putString("ActiveRecipe",activeRecipe);tag.putString("Template",template);tag.putBoolean("Stopped",stopped);
        tag.putInt("FaceMask",faceMask);tag.putInt("Retain",retain);tag.putInt("FilterMode",filterMode);tag.putInt("Cursor",rotationCursor);tag.putInt("PeakTicks",peakTicks);tag.putInt("PeakCooldown",peakCooldown);tag.putBoolean("EfficientCharge",efficientCharge);tag.putString("FluidFilter",fluidFilter);
        tag.put("FilterItems",filters.createTag(provider));
        tag.putFloat("Experience",experience);tag.putFloat("PendingExperience",pendingExperience);
        for(int i=0;i<3;i++)tag.put("Fluid"+i,tanks[i].writeToNBT(provider,new CompoundTag()));
        tag.put("PendingFluid1",pendingFluid1.saveOptional(provider));tag.put("PendingFluid2",pendingFluid2.saveOptional(provider));
    }
    protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider){
        super.loadAdditional(tag,provider);stock=NonNullList.withSize(27,ItemStack.EMPTY);pending=NonNullList.withSize(4,ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("IndustryInventory"),stock,provider);ContainerHelper.loadAllItems(tag.getCompound("IndustryPending"),pending,provider);
        extraCraftingInputs=NonNullList.withSize(45,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag.getCompound("ExtraCraftingInputs"),extraCraftingInputs,provider);
        powerStored=Math.clamp(tag.getLong("Power"),0L,capacity());processCost=Math.clamp(tag.getLong("Cost"),0L,10_000_000L);processPaid=Math.clamp(tag.getLong("Paid"),0L,processCost);heatCredit=Math.clamp(tag.getLong("HeatCredit"),0L,10_000_000L);fuelHeat=Math.clamp(tag.getInt("FuelHeat"),0,kind().equals("g01")?Integer.MAX_VALUE:320000);chargeRemainder=Math.clamp(tag.getInt("ChargeRemainder"),0,99);
        processTicks=Math.clamp(tag.getInt("Duration"),1,12000);progressTicks=Math.clamp(tag.getInt("WorkProgress"),0,processTicks-1);activeRecipe=tag.getString("ActiveRecipe");template=tag.getString("Template");stopped=tag.getBoolean("Stopped");faceMask=tag.contains("FaceMask")?tag.getInt("FaceMask")&63:63;
        retain=Math.clamp(tag.getInt("Retain"),0,64);filterMode=Math.clamp(tag.getInt("FilterMode"),0,5);rotationCursor=Math.max(0,tag.getInt("Cursor"));peakTicks=Math.clamp(tag.getInt("PeakTicks"),0,100);peakCooldown=Math.clamp(tag.getInt("PeakCooldown"),0,200);efficientCharge=tag.getBoolean("EfficientCharge");fluidFilter=tag.getString("FluidFilter");
        experience=Math.max(0,tag.getFloat("Experience"));pendingExperience=Math.max(0,tag.getFloat("PendingExperience"));
        filters.clearContent();filters.fromTag(tag.getList("FilterItems",10),provider);for(int i=0;i<3;i++)tanks[i].readFromNBT(provider,tag.getCompound("Fluid"+i));
        // Retire hidden configuration from older saves without touching reserved work or inventories.
        stopped=false;faceMask=63;retain=0;filterMode=0;fluidFilter="";
        pendingFluid1=FluidStack.parseOptional(provider,tag.getCompound("PendingFluid1"));pendingFluid2=FluidStack.parseOptional(provider,tag.getCompound("PendingFluid2"));
        if(activeRecipe.isEmpty()){progressTicks=0;processPaid=0;pending=NonNullList.withSize(4,ItemStack.EMPTY);pendingFluid1=FluidStack.EMPTY;pendingFluid2=FluidStack.EMPTY;}
    }
    public Component getDisplayName(){return Component.translatable("block.starfall_blueprint."+kind());}
    public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new IndustryMenu(id,inv,this,processData);}
    public int getContainerSize(){return kind().equals("autocrafter")?72:27;}
    public boolean isEmpty(){return stock.stream().allMatch(ItemStack::isEmpty)&&extraCraftingInputs.stream().allMatch(ItemStack::isEmpty);}
    public ItemStack getItem(int i){return i<27?stock.get(i):extraCraftingInputs.get(i-27);}
    public ItemStack removeItem(int i,int n){ItemStack s=ContainerHelper.removeItem(i<27?stock:extraCraftingInputs,i<27?i:i-27,n);if(!s.isEmpty())setChanged();return s;}
    public ItemStack removeItemNoUpdate(int i){return ContainerHelper.takeItem(i<27?stock:extraCraftingInputs,i<27?i:i-27);}
    public void setItem(int i,ItemStack s){if(i<27)stock.set(i,s);else extraCraftingInputs.set(i-27,s);if(i==CRAFTING_SAMPLE)selectCraftingSample();setChanged();}
    public boolean stillValid(Player p){return Container.stillValidBlockEntity(this,p);}
    public void clearContent(){stock=NonNullList.withSize(27,ItemStack.EMPTY);extraCraftingInputs=NonNullList.withSize(45,ItemStack.EMPTY);setChanged();}
    public static boolean nested(ItemStack s){return s.has(DataComponents.CONTAINER)||s.has(DataComponents.BUNDLE_CONTENTS)||s.getItem()instanceof BlockItem b&&b.getBlock()instanceof EntityBlock||s.is(CatalogueContent.item("sample_case"));}
    public boolean canPlaceItem(int i,ItemStack s){
        return workCanPlace(i,s);
    }
    public boolean workCanPlace(int i,ItemStack s){
        if(kind().equals("press"))return i>=0&&i<9&&(s.is(Items.IRON_INGOT)||s.is(Items.COPPER_INGOT));
        if(kind().equals("quarry"))return i==18&&idle()&&s.is(CatalogueContent.item("resonance_upgrade"));
        if(kind().equals("phase_extractor"))return false;
        if(AggregateMachines.large(kind()))return kind().equals("crusher")&&i>=0&&i<9&&IndustryProcessing.available(this).stream().flatMap(r->java.util.stream.StreamSupport.stream(r.getAsJsonArray("inputs").spliterator(),false)).anyMatch(in->CatalogueContent.matches(CatalogueContent.key(in),s));
        if(kind().equals("autocrafter")&&i==CRAFTING_SAMPLE)return true;
        if(ConduitBlock.isConduit(kind())||kind().equals("damper"))return false;
        if(kind().equals("plant_cultivator"))return switch(i){case 0->idle()&&PlantCultivation.isEnvironment(s);case 1->idle()&&PlantCultivation.isSample(s);case 2->s.is(Items.WATER_BUCKET);case 18->s.is(CatalogueContent.item("s01"));default->false;};
        if(kind().equals("g01"))return i==13?GeneratorFuel.isFuel(s):i==18&&s.is(CatalogueContent.item("s01"));
        if(kind().equals("g06")||kind().equals("g07"))return i==18&&s.is(CatalogueContent.item("s01"));
        if(kind().equals("supply_crate"))return !nested(s)&&acceptsFilter(s);
        if(i==13)return PreparationBlockEntity.isFuel(s);
        if(i==14||i==15)return idle()&&CatalogueContent.TOOLS.stream().anyMatch(k->s.is(CatalogueContent.item(k)));
        if(i==16)return idle()&&(s.is(CatalogueContent.item("speed_1"))||s.is(CatalogueContent.item("speed_2")));
        if(i==17)return idle()&&(s.is(CatalogueContent.item("bulk_1"))||s.is(CatalogueContent.item("bulk_2")));
        if(i==18)return s.is(CatalogueContent.item("s01"));
        if(kind().equals("autocrafter"))return i>=0&&i<9||i>=27&&i<72;
        return i>=0&&i<9&&!nested(s);
    }
    public int[]getSlotsForFace(Direction d){return workSlots(d);}
    public int[]workSlots(Direction d){if(kind().equals("phase_extractor"))return d==Direction.DOWN?new int[]{9,10,11,12}:new int[]{};if(kind().equals("press"))return d==Direction.DOWN?new int[]{9,10,11,12}:d==Direction.UP?new int[]{0,1,2,3,4,5,6,7,8}:new int[]{};if(AggregateMachines.large(kind()))return d==Direction.DOWN||d==facing().getOpposite()?new int[]{9,10,11,12}:kind().equals("crusher")?new int[]{0,1,2,3,4,5,6,7,8}:new int[]{};if(ConduitBlock.isConduit(kind())||Set.of("damper","terminal").contains(kind()))return new int[]{};if(kind().equals("supply_crate"))return java.util.stream.IntStream.range(0,27).toArray();if(d==Direction.DOWN)return new int[]{9,10,11,12};if(kind().equals("plant_cultivator"))return d==Direction.UP?new int[]{0,1,2}:new int[]{};if(kind().equals("autocrafter"))return craftingInputSlots();if(d==Direction.UP)return generator()?new int[]{13}:new int[]{0,1,2,3,4,5,6,7,8};return new int[]{13};}
    public boolean canPlaceItemThroughFace(int i,ItemStack s,Direction d){return !redstoneStopped()&&canPlaceItem(i,s);}
    public boolean canTakeItemThroughFace(int i,ItemStack s,Direction d){return !redstoneStopped()&&(kind().equals("supply_crate")||(d==Direction.DOWN||AggregateMachines.large(kind())&&d==facing().getOpposite())&&i>=9&&i<=12);}
    public void awardExperience(Player player){if(player.level()instanceof net.minecraft.server.level.ServerLevel server&&experience>0){int amount=(int)experience;if(server.random.nextFloat()<experience-amount)amount++;experience=0;setChanged();if(amount>0)net.minecraft.world.entity.ExperienceOrb.award(server,player.position(),amount);}}
}
