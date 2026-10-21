package cn.starfall.blueprint;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

public final class AlloyFurnaceMenu extends AbstractContainerMenu {
    public final Container container;
    public final ContainerData data;
    public AlloyFurnaceMenu(int id, Inventory inv) { this(id, inv, new SimpleContainer(4), new SimpleContainerData(4)); }
    public AlloyFurnaceMenu(int id, Inventory inv, Container container, ContainerData data) {
        super(StarfallBlueprint.ALLOY_MENU.get(), id); this.container = container; this.data = data;
        checkContainerSize(container, 4); checkContainerDataCount(data, 4);
        addSlot(new Slot(container, 0, 38, 35) { public boolean mayPlace(ItemStack s) { return container.canPlaceItem(0,s); } });
        addSlot(new Slot(container, 1, 62, 35) { public boolean mayPlace(ItemStack s) { return container.canPlaceItem(1,s); } });
        addSlot(new Slot(container, 2, 50, 71) { public boolean mayPlace(ItemStack s) { return PreparationBlockEntity.isFuel(s); } });
        addSlot(new Slot(container, 3, 122, 47) { public boolean mayPlace(ItemStack s) { return false; } });
        for (int row=0; row<3; row++) for (int col=0; col<9; col++) addSlot(new Slot(inv, col+row*9+9, 8+col*18, 120+row*18));
        for (int col=0; col<9; col++) addSlot(new Slot(inv, col, 8+col*18, 178));
        addDataSlots(data);
    }
    public boolean stillValid(Player p) { return container.stillValid(p); }
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot=slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(), original=stack.copy();
        boolean moved;
        if (index<4) moved=moveItemStackTo(stack, 4, slots.size(), true);
        else if (container.canPlaceItem(0,stack)) moved=moveItemStackTo(stack, 0, 1, false);
        else if (container.canPlaceItem(1,stack)&&container.getItem(1).isEmpty()) moved=moveItemStackTo(stack,1,2,false);
        else if (PreparationBlockEntity.isFuel(stack)) moved=moveItemStackTo(stack,2,3,false);
        else moved=index<31?moveItemStackTo(stack,31,40,false):moveItemStackTo(stack,4,31,false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(p,stack); return original;
    }
}
