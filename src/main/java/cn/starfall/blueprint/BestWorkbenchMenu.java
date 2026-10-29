package cn.starfall.blueprint;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** No private inventory or result slots; closing this menu never moves or drops items. */
public final class BestWorkbenchMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private int syncedResearchLevel;
    private boolean syncedLightMobility;
    private boolean syncedDiagnostics;

    public BestWorkbenchMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public BestWorkbenchMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(BestEngineering.MENU.get(), id);
        this.access = access;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 70 + col * 18, 152 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 70 + col * 18, 210));
        addDataSlot(new DataSlot() {
            @Override public int get() {
                return inventory.player.level().isClientSide
                        ? syncedResearchLevel : BestEngineering.researchLevel(inventory.player);
            }
            @Override public void set(int value) { syncedResearchLevel = value; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() {
                return (inventory.player.level().isClientSide
                        ? syncedLightMobility : BestEngineering.hasLightMobility(inventory.player)) ? 1 : 0;
            }
            @Override public void set(int value) { syncedLightMobility = value != 0; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() {
                return (inventory.player.level().isClientSide
                        ? syncedDiagnostics : BestEngineering.hasDiagnostics(inventory.player)) ? 1 : 0;
            }
            @Override public void set(int value) { syncedDiagnostics = value != 0; }
        });
    }

    public int researchLevel() { return syncedResearchLevel; }
    public boolean hasLightMobility() { return syncedLightMobility; }
    public boolean hasDiagnostics() { return syncedDiagnostics; }

    @Override public boolean stillValid(Player player) {
        return stillValid(access, player, BestEngineering.WORKBENCH.get());
    }

    @Override public boolean clickMenuButton(Player player, int action) {
        if (player.level().isClientSide || action < 0 || action > 16 || !stillValid(player)) return false;
        boolean performed = BestEngineering.perform(player, action);
        broadcastChanges();
        return performed;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!(index < 27 ? moveItemStackTo(stack, 27, 36, false) : moveItemStackTo(stack, 0, 27, false))) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
