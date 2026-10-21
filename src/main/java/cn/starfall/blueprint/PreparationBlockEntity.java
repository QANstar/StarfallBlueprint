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
    public boolean materialMode;
    private int batchPower = 200;
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
        if(be.materialMode){IndustryBlockEntity.tick(level,pos,blockState,be);return;}
        be.serverTick();
        boolean active = be.state == 1;
        if (blockState.getValue(PreparationBlock.ACTIVE) != active) level.setBlock(pos, blockState.setValue(PreparationBlock.ACTIVE, active), 3);
    }
    private boolean fits(int slot, ItemStack output) {
        ItemStack current = items.get(slot);
        return current.isEmpty() || (ItemStack.isSameItemSameComponents(current, output) && current.getCount() + output.getCount() <= current.getMaxStackSize());
    }
    private boolean fuelReady() { return energy >= batchPower || isFuel(items.get(1)); }
    public static boolean isFuel(ItemStack stack) { return stack.is(Items.COAL) || stack.is(Items.CHARCOAL); }
    private void serverTick() {
        if (pendingCrystal.isEmpty()) {
            ItemStack ore = items.get(0);
            if (ore.isEmpty()) { state = 2; return; }
            MaterialBatch batch = ore.get(StarfallBlueprint.BATCH.get());
            if (!ore.is(StarfallBlueprint.RAW.get()) || batch == null || !batch.isProcessableOre()) { state = 6; return; }
            ItemStack crystal = new ItemStack(StarfallBlueprint.CRYSTAL.get(), batch.crystalCount());
            crystal.set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(batch.integrity() - 2));
            var origin=CatalogueEquipment.getData(ore);if(origin.contains("Origin"))CatalogueEquipment.setData(crystal,origin);
            if(ore.has(CatalogueContent.QUALITY.get())){var q=MaterialQuality.of(ore);crystal.set(CatalogueContent.QUALITY.get(),new MaterialQuality(90,batch.integrity()-2,0,0,q.continuous(),"common"));}
            ItemStack tail = new ItemStack(StarfallBlueprint.TAILINGS.get());
            tail.set(StarfallBlueprint.BATCH.get(), MaterialBatch.tailings(batch.phaseMilliM() / 10_000));
            if (!fits(2, crystal) || !fits(3, tail)) { state = 5; return; }
            if (water < 250) { state = 3; return; }
            batchPower = items.get(4).is(StarfallBlueprint.CONNECTOR.get()) ? 180 : 200;
            if (!fuelReady()) { state = 4; return; }
            // All validation precedes reservation. Reserved input survives save, unload and relocation.
            ore.shrink(1); water -= 250; pendingCrystal = crystal; pendingTailings = tail; progress = 0;
            setChanged();
        }
        if (!fits(2, pendingCrystal) || !fits(3, pendingTailings)) { state = 5; return; }
        if (!fuelReady()) { state = 4; return; }
        if (energy < batchPower) { items.get(1).shrink(1); energy += 320_000; }
        energy -= batchPower; progress++; state = 1;
        if (progress == 200) {
            insertOutput(2, pendingCrystal); insertOutput(3, pendingTailings);
            pendingCrystal = ItemStack.EMPTY; pendingTailings = ItemStack.EMPTY; progress = 0; state = 0;
        }
        setChanged();
    }
    private void insertOutput(int slot, ItemStack stack) { if (items.get(slot).isEmpty()) items.set(slot, stack); else items.get(slot).grow(stack.getCount()); }
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
        if (!pendingCrystal.isEmpty()) tag.put("PendingCrystal", pendingCrystal.save(provider));
        if (!pendingTailings.isEmpty()) tag.put("PendingTailings", pendingTailings.save(provider));
        tag.putInt("RecipeVersion", 1);
        tag.putBoolean("MaterialMode",materialMode);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider); items = NonNullList.withSize(5, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items, provider);
        materialMode=tag.getBoolean("MaterialMode");
        water = Math.clamp(tag.getInt("Water"), 0, 4000); energy = Math.clamp(tag.getLong("EnergyXH"), 0L, 320_199L);
        progress = Math.clamp(tag.getInt("Progress"), 0, 199); batchPower = tag.getInt("BatchPower") == 180 ? 180 : 200;
        pendingCrystal = ItemStack.parseOptional(provider, tag.getCompound("PendingCrystal"));
        pendingTailings = ItemStack.parseOptional(provider, tag.getCompound("PendingTailings"));
        if (pendingCrystal.isEmpty() || pendingTailings.isEmpty()) { pendingCrystal = ItemStack.EMPTY; pendingTailings = ItemStack.EMPTY; progress = 0; }
    }
    @Override public Component getDisplayName() { return Component.translatable("block.starfall_blueprint.preparation_bench"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return new PreparationMenu(id, inv, this, data); }
    @Override public int getContainerSize() { return 5; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) { ItemStack result = ContainerHelper.removeItem(items, slot, count); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); setChanged(); }
    @Override public boolean stillValid(Player p) { return Container.stillValidBlockEntity(this, p); }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return switch(slot) { case 0 -> stack.is(StarfallBlueprint.RAW.get()); case 1 -> isFuel(stack); case 4 -> stack.is(StarfallBlueprint.CONNECTOR.get()); default -> false; }; }
    @Override public int[] getSlotsForFace(Direction direction) { return switch(direction) { case UP -> new int[]{0}; case DOWN -> new int[]{2,3}; default -> new int[]{1}; }; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) { return direction == Direction.DOWN && (slot == 2 || slot == 3); }
}
