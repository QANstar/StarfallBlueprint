package cn.starfall.blueprint;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class PreparationBlockEntity extends IndustryBlockEntity implements WorldlyContainer, MenuProvider {
    private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
    private ItemStack pendingCrystal = ItemStack.EMPTY, pendingTailings = ItemStack.EMPTY;
    public int water, progress, state;
    public long energy;
    /** Legacy blueprint/save flag, no longer controls operation. */
    public boolean materialMode=true;
    private static final int[] LEGACY_SLOTS={0,13,9,10,19};
    private int batchPower = 200, batchDuration = 200;
    private long oreCost = 40000, orePaid;
    @Override public boolean idle(){return super.idle()&&pendingCrystal.isEmpty();}
    // 0 ready; 1 processing; 2 input; 3 water; 4 fuel; 5 output; 6 invalid batch
    public final ContainerData data = new ContainerData() {
        public int get(int i) { return switch(i) { case 0 -> progress; case 1 -> water; case 2 -> (int)(energy & 65535); case 3 -> (int)(energy >>> 16); case 4 -> state; case 5 -> batchPower; default -> 0; }; }
        public void set(int i, int v) { }
        public int getCount() { return 6; }
    };
    public PreparationBlockEntity(BlockPos pos, BlockState state) { super(StarfallBlueprint.PREPARATION_ENTITY.get(), pos, state);
        tanks[0]=new net.neoforged.neoforge.fluids.capability.templates.FluidTank(4000){
            public FluidStack getFluid(){return water==0?FluidStack.EMPTY:new FluidStack(Fluids.WATER,water);}
            public int getFluidAmount(){return water;}public boolean isEmpty(){return water==0;}
            public int fill(FluidStack s,IFluidHandler.FluidAction a){if(!s.is(Fluids.WATER))return 0;int n=Math.min(s.getAmount(),4000-water);if(a.execute()&&n>0){water+=n;setChanged();}return n;}
            public FluidStack drain(int n,IFluidHandler.FluidAction a){int amount=Math.min(n,water);if(amount<=0)return FluidStack.EMPTY;if(a.execute()){water-=amount;setChanged();}return new FluidStack(Fluids.WATER,amount);}
        };
    }
    @Override public long getPower(){return energy;}
    @Override public void setPower(long value){energy=Math.clamp(value,0L,320199L);setChanged();}

    public static void tick(Level level, BlockPos pos, BlockState blockState, PreparationBlockEntity be) {
        be.migrateLegacyItems();
        boolean oreWork=!be.pendingCrystal.isEmpty()||be.activeRecipe.isEmpty()&&be.oreInput()>=0;
        if(oreWork){
            be.serverTick();be.status=switch(be.state){case 6->7;case 7->8;default->be.state;};
            // An unstartable ore batch must not prevent a different valid material job.
            if(be.pendingCrystal.isEmpty()&&be.state!=0&&be.state!=1&&!be.redstoneStopped()){
                int oreStatus=be.status;IndustryProcessing.tick(be);if(be.idle()&&be.status==2)be.status=oreStatus;
            }
            IndustryNetworks.tick(be);
        }else{IndustryBlockEntity.tick(level,pos,blockState,be);return;}
        boolean active = be.status == 1;
        if (blockState.getValue(PreparationBlock.ACTIVE) != active) level.setBlock(pos, blockState.setValue(PreparationBlock.ACTIVE, active), 3);
    }
    private int oreInput(){for(int i=0;i<9;i++)if(stock.get(i).is(StarfallBlueprint.RAW.get()))return i;return -1;}
    private boolean fuelReady() { return energy >= Math.min(capacity(),batchPower) || isFuel(stock.get(13)); }
    private void migrateLegacyItems(){
        for(int i=0;i<5;i++){
            var stack=items.get(i);if(stack.isEmpty())continue;
            if(i==2||i==3){if(planOutputs(java.util.List.of(stack),true))items.set(i,ItemStack.EMPTY);continue;}
            int first=i==0?0:LEGACY_SLOTS[i],end=i==0?9:first+1;
            for(int slot=first;slot<end&&!stack.isEmpty();slot++){
                var target=stock.get(slot);if(target.isEmpty()){stock.set(slot,stack.copy());stack.setCount(0);setChanged();}
                else if(ItemStack.isSameItemSameComponents(target,stack)){int n=Math.min(stack.getCount(),target.getMaxStackSize()-target.getCount());target.grow(n);stack.shrink(n);if(n>0)setChanged();}
            }
        }
    }
    private final ContainerData unifiedData=new ContainerData(){
        public int get(int i){if(!pendingCrystal.isEmpty()){if(i==2)return progress;if(i==3)return batchDuration;}return processData.get(i);}
        public void set(int i,int value){}public int getCount(){return 15;}
    };
    public static boolean isFuel(ItemStack stack) { return stack.is(Items.COAL) || stack.is(Items.CHARCOAL); }
    private void serverTick() {
        if(redstoneStopped()){state=7;return;}
        if (pendingCrystal.isEmpty()) {
            int source=oreInput();ItemStack ore=source<0?ItemStack.EMPTY:stock.get(source);
            if (ore.isEmpty()) { state = 2; return; }
            MaterialBatch batch = ore.get(StarfallBlueprint.BATCH.get());
            if (!ore.is(StarfallBlueprint.RAW.get()) || batch == null || !batch.isProcessableOre()) { state = 6; return; }
            ItemStack crystal = new ItemStack(StarfallBlueprint.CRYSTAL.get(), batch.crystalCount());
            crystal.set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(batch.integrity() - 2));
            var origin=CatalogueEquipment.getData(ore);if(origin.contains("Origin"))CatalogueEquipment.setData(crystal,origin);
            if(ore.has(CatalogueContent.QUALITY.get())){var q=MaterialQuality.of(ore);crystal.set(CatalogueContent.QUALITY.get(),new MaterialQuality(90,batch.integrity()-2,0,0,q.continuous(),"common"));}
            ItemStack tail = new ItemStack(StarfallBlueprint.TAILINGS.get());
            tail.set(StarfallBlueprint.BATCH.get(), MaterialBatch.tailings(batch.phaseMilliM() / 10_000));
            if (!outputFits(java.util.List.of(crystal,tail))) { state = 5; return; }
            if (water < 250) { state = 3; return; }
            batchDuration = MachineUpgrades.duration(this,200);
            oreCost = MachineUpgrades.cost(this,40000);orePaid=0;
            batchPower = (int)Math.min(Integer.MAX_VALUE,oreCost/batchDuration+(oreCost%batchDuration==0?0:1));
            if (!fuelReady()) { state = 4; return; }
            // All validation precedes reservation. Reserved input survives save, unload and relocation.
            ore.shrink(1); water -= 250; pendingCrystal = crystal; pendingTailings = tail; progress = 0;
            setChanged();
        }
        if (!outputFits(java.util.List.of(pendingCrystal,pendingTailings))) { state = 5; return; }
        long target=MachineUpgrades.paymentTarget(oreCost,progress+1,batchDuration), payment=target-orePaid;
        if (energy < payment && !isFuel(stock.get(13))) { if(payment>capacity()){orePaid+=energy;energy=0;setChanged();}state = 4; return; }
        if (energy < payment) { stock.get(13).shrink(1); energy += 320_000; }
        if(energy<payment){orePaid+=energy;energy=0;state=4;setChanged();return;}
        energy -= payment;orePaid=target; progress++; state = 1;
        if (progress == batchDuration) {
            planOutputs(java.util.List.of(pendingCrystal,pendingTailings),true);
            pendingCrystal = ItemStack.EMPTY; pendingTailings = ItemStack.EMPTY; progress = 0; state = 0;
        }
        setChanged();
    }
    public IFluidHandler waterPort() { return new IFluidHandler() {
        public int getTanks() { return 1; }
        public FluidStack getFluidInTank(int tank) { return water == 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, water); }
        public int getTankCapacity(int tank) { return 4000; }
        public boolean isFluidValid(int tank, FluidStack stack) { return stack.is(Fluids.WATER); }
        public int fill(FluidStack stack, FluidAction action) {
            if (!stack.is(Fluids.WATER)) return 0;
            int amount = Math.min(stack.getAmount(), 4000 - water);
            if (action.execute() && amount > 0) { water += amount; setChanged(); }
            return amount;
        }
        public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
        public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
    }; }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider); ContainerHelper.saveAllItems(tag, items, provider);
        tag.putInt("Water", water); tag.putLong("EnergyXH", energy); tag.putInt("Progress", progress); tag.putInt("BatchPower", batchPower);
        tag.putInt("OreDuration",batchDuration);tag.putLong("OreCost",oreCost);tag.putLong("OrePaid",orePaid);
        if (!pendingCrystal.isEmpty()) tag.put("PendingCrystal", pendingCrystal.save(provider));
        if (!pendingTailings.isEmpty()) tag.put("PendingTailings", pendingTailings.save(provider));
        tag.putInt("RecipeVersion", 1);
        tag.putBoolean("MaterialMode",materialMode);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider); items = NonNullList.withSize(5, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items, provider);
        materialMode=true;migrateLegacyItems();
        water = Math.clamp(tag.getInt("Water"), 0, 4000); energy = Math.clamp(tag.getLong("EnergyXH"), 0L, 320_199L);
        batchDuration=tag.contains("OreDuration")?Math.clamp(tag.getInt("OreDuration"),1,200):200;
        progress = Math.clamp(tag.getInt("Progress"), 0, batchDuration-1); batchPower = tag.getInt("BatchPower") == 180 ? 180 : 200;
        oreCost=tag.contains("OreCost")?Math.max(0L,tag.getLong("OreCost")):(long)batchPower*200;
        orePaid=tag.contains("OrePaid")?Math.clamp(tag.getLong("OrePaid"),0L,oreCost):(long)progress*batchPower;
        batchPower=(int)Math.min(Integer.MAX_VALUE,oreCost/batchDuration+(oreCost%batchDuration==0?0:1));
        pendingCrystal = ItemStack.parseOptional(provider, tag.getCompound("PendingCrystal"));
        pendingTailings = ItemStack.parseOptional(provider, tag.getCompound("PendingTailings"));
        if (pendingCrystal.isEmpty() || pendingTailings.isEmpty()) { pendingCrystal = ItemStack.EMPTY; pendingTailings = ItemStack.EMPTY; progress = 0; }
    }
    @Override public Component getDisplayName() { return Component.translatable("block.starfall_blueprint.preparation_bench"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { migrateLegacyItems();return new IndustryMenu(id,inv,workInventory(),unifiedData); }
    @Override public int getContainerSize() { return 5; }
    @Override public boolean isEmpty() { return super.isEmpty()&&items.stream().allMatch(ItemStack::isEmpty); }
    // Preserve the historical five-slot Java API while the UI/ports share stock through workInventory.
    @Override public ItemStack getItem(int slot) { return stock.get(LEGACY_SLOTS[slot]); }
    @Override public ItemStack removeItem(int slot, int count) { return super.removeItem(LEGACY_SLOTS[slot],count); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return super.removeItemNoUpdate(LEGACY_SLOTS[slot]); }
    @Override public void setItem(int slot, ItemStack stack) { stock.set(LEGACY_SLOTS[slot],stack);setChanged(); }
    @Override public boolean stillValid(Player p) { return Container.stillValidBlockEntity(this, p); }
    @Override public void clearContent() { items=NonNullList.withSize(5,ItemStack.EMPTY);super.clearContent(); }
    @Override public boolean workCanPlace(int slot,ItemStack stack){
        if(MachineUpgrades.isSlot(kind(),slot))return MachineUpgrades.accepts(this,stack);
        if(slot>=0&&slot<9){
            if(stack.is(StarfallBlueprint.RAW.get()))return true;
            if(nested(stack))return false;
            return IndustryProcessing.available(this).stream().flatMap(r->java.util.stream.StreamSupport.stream(r.getAsJsonArray("inputs").spliterator(),false))
                    .map(CatalogueContent::key).filter(key->!key.equals("water")&&!key.equals("power")).anyMatch(key->CatalogueContent.matches(key,stack));
        }
        return super.workCanPlace(slot,stack);
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return switch(slot) { case 0 -> stack.is(StarfallBlueprint.RAW.get()); case 1 -> isFuel(stack); case 4 -> stack.is(StarfallBlueprint.CONNECTOR.get()); default -> false; }; }
    @Override public int[] getSlotsForFace(Direction direction) { return switch(direction) { case UP -> new int[]{0}; case DOWN -> new int[]{2,3}; default -> new int[]{1}; }; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) { return slot!=4&&canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) { return direction == Direction.DOWN && (slot == 2 || slot == 3); }
}
