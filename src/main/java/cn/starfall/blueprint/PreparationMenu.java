package cn.starfall.blueprint;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class PreparationMenu extends AbstractContainerMenu {
    public final Container container;
    public final ContainerData data;
    public PreparationMenu(int id, Inventory inv) { this(id, inv, new SimpleContainer(5), new SimpleContainerData(6)); }
    public PreparationMenu(int id, Inventory inv, Container container, ContainerData data) {
        super(StarfallBlueprint.PREPARATION_MENU.get(), id); this.container = container; this.data = data;
        checkContainerSize(container, 5); checkContainerDataCount(data, 6);
        addSlot(new Slot(container, 0, 20, 44) { public boolean mayPlace(ItemStack s) { return s.is(StarfallBlueprint.RAW.get()); } });
        addSlot(new Slot(container, 1, 20, 80) { public boolean mayPlace(ItemStack s) { return PreparationBlockEntity.isFuel(s); } });
        addSlot(new Slot(container, 2, 116, 44) { public boolean mayPlace(ItemStack s) { return false; } });
        addSlot(new Slot(container, 3, 146, 44) { public boolean mayPlace(ItemStack s) { return false; } });
        addSlot(new Slot(container, 4, 146, 80) { public boolean mayPlace(ItemStack s) { return s.is(StarfallBlueprint.CONNECTOR.get()); } public int getMaxStackSize() { return 1; } });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 158 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 216));
        addDataSlots(data);
    }
    public long energy() { return (data.get(2) & 65535L) | ((data.get(3) & 65535L) << 16); }
    @Override public boolean stillValid(Player player) { return container.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index < 5) { if (!moveItemStackTo(stack, 5, slots.size(), true)) return ItemStack.EMPTY; }
        else if (stack.is(StarfallBlueprint.RAW.get())) { if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY; }
        else if (PreparationBlockEntity.isFuel(stack)) { if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY; }
        else if (stack.is(StarfallBlueprint.CONNECTOR.get())) { if (!moveItemStackTo(stack, 4, 5, false)) return ItemStack.EMPTY; }
        else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return copy;
    }
}
