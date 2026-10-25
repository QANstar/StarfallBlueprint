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

public final class AlloyFurnaceBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    private NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
    private ItemStack pending = ItemStack.EMPTY;
    public int progress, duration = 200, fuelTicks, state;
    public final ContainerData data = new ContainerData() {
        public int get(int i) { return switch(i) { case 0 -> progress; case 1 -> duration; case 2 -> fuelTicks; case 3 -> state; default -> 0; }; }
        public void set(int i, int value) { }
        public int getCount() { return 4; }
    };
    public AlloyFurnaceBlockEntity(BlockPos pos, BlockState state) { super(StarfallBlueprint.ALLOY_ENTITY.get(), pos, state); }
    public static void tick(Level level, BlockPos pos, BlockState state, AlloyFurnaceBlockEntity be) {
        be.process();
        boolean active = be.state == 1;
        if (state.getValue(AlloyFurnaceBlock.ACTIVE) != active) level.setBlock(pos, state.setValue(AlloyFurnaceBlock.ACTIVE, active), 3);
    }
    private boolean fits(ItemStack out) {
        ItemStack current = items.get(3);
        return out.getCount() <= out.getMaxStackSize() && (current.isEmpty()
            || ItemStack.isSameItemSameComponents(current, out) && current.getCount() + out.getCount() <= current.getMaxStackSize());
    }
    private boolean fuelReady() { return fuelTicks > 0 || PreparationBlockEntity.isFuel(items.get(2)); }
    private void process() {
        if(level.hasNeighborSignal(worldPosition)){state=5;return;}
        if (pending.isEmpty()) {
            var input = new AlloyRecipe.Input(items.get(0), items.get(1));
            var found = level.getRecipeManager().getRecipeFor(StarfallBlueprint.ALLOY_TYPE.get(), input, level);
            if (found.isEmpty()) { state = 2; return; }
            AlloyRecipe recipe = found.get().value();
            ItemStack output = recipe.assemble(input, level.registryAccess());
            if (!fits(output)) { state = 4; return; }
            if (!fuelReady()) { state = 3; return; }
            items.get(0).shrink(recipe.baseCount()); items.get(1).shrink(recipe.additiveCount());
            pending = output; duration = recipe.duration(); progress = 0;
        }
        if (!fits(pending)) { state = 4; return; }
        if (!fuelReady()) { state = 3; return; }
        if (fuelTicks == 0) { items.get(2).shrink(1); fuelTicks = 1600; }
        fuelTicks--; progress++; state = 1;
        if (progress >= duration) {
            if (items.get(3).isEmpty()) items.set(3, pending); else items.get(3).grow(pending.getCount());
            pending = ItemStack.EMPTY; progress = 0; state = 0;
        }
        setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider); ContainerHelper.saveAllItems(tag, items, provider);
        tag.putInt("Progress", progress); tag.putInt("Duration", duration); tag.putInt("FuelTicks", fuelTicks);
        if (!pending.isEmpty()) tag.put("Pending", pending.save(provider));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider); items = NonNullList.withSize(4, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items, provider);
        pending = ItemStack.parseOptional(provider, tag.getCompound("Pending"));
        duration = Math.clamp(tag.getInt("Duration"), 1, 12000);
        progress = pending.isEmpty() ? 0 : Math.clamp(tag.getInt("Progress"), 0, duration - 1);
        fuelTicks = Math.clamp(tag.getInt("FuelTicks"), 0, 1600);
    }
    public Component getDisplayName() { return Component.translatable("block.starfall_blueprint.alloy_furnace"); }
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return new AlloyFurnaceMenu(id, inv, this, data); }
    public int getContainerSize() { return 4; }
    public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    public ItemStack getItem(int slot) { return items.get(slot); }
    public ItemStack removeItem(int slot, int count) { ItemStack s = ContainerHelper.removeItem(items, slot, count); if (!s.isEmpty()) setChanged(); return s; }
    public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    public void setItem(int slot, ItemStack stack) { items.set(slot, stack); setChanged(); }
    public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    public void clearContent() { items = NonNullList.withSize(4, ItemStack.EMPTY); setChanged(); }
    public boolean canPlaceItem(int slot, ItemStack s) {
        if(slot==2)return PreparationBlockEntity.isFuel(s);
        if(slot!=0&&slot!=1)return false;
        return level!=null&&level.getRecipeManager().getAllRecipesFor(StarfallBlueprint.ALLOY_TYPE.get()).stream().anyMatch(r->(slot==0?r.value().base():r.value().additive()).test(s));
    }
    public int[] getSlotsForFace(Direction side) { return side == Direction.UP ? new int[]{0, 1} : side == Direction.DOWN ? new int[]{3} : new int[]{2}; }
    public boolean canPlaceItemThroughFace(int slot, ItemStack s, Direction side) { return canPlaceItem(slot, s); }
    public boolean canTakeItemThroughFace(int slot, ItemStack s, Direction side) { return side == Direction.DOWN && slot == 3; }
}
