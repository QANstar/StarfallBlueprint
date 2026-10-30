package cn.starfall.blueprint;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Live entity storage, never a copied inventory that could outlive packing or a body change. */
public final class BestMechCargoMenu extends AbstractContainerMenu {
    private final BestMechEntity mech;
    private final int cargoSlots;

    public BestMechCargoMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, null, checkedSize(buffer.readVarInt()));
    }
    public BestMechCargoMenu(int id, Inventory inventory, BestMechEntity mech) {
        this(id, inventory, mech, mech.cargoSlots());
    }
    private static int checkedSize(int size) {
        if(size != 9 && size != 18) throw new IllegalArgumentException("Invalid mech cargo size: " + size);
        return size;
    }
    private BestMechCargoMenu(int id, Inventory inventory, BestMechEntity mech, int size) {
        super(BestMechs.CARGO_MENU.get(), id);
        this.mech = mech; this.cargoSlots = checkedSize(size);
        SimpleContainer cargo = mech == null ? new SimpleContainer(18) : mech.cargo();
        for(int i = 0; i < size; i++) {
            final int index = i;
            addSlot(new Slot(cargo, i, 8 + i % 9 * 18, 29 + i / 9 * 18) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return !(stack.getItem() instanceof BestMechItem) && cargo.canPlaceItem(index, stack);
                }
                @Override public boolean mayPickup(Player player) { return stillValid(player); }
            });
        }
        int top = inventoryTop();
        for(int row = 0; row < 3; row++) for(int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, top + row * 18));
        for(int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, top + 58));
    }
    public int cargoSlots() { return cargoSlots; }
    public int inventoryTop() { return 62 + cargoSlots / 9 * 18; }
    public static void open(Player player, BestMechEntity mech) {
        if(player instanceof ServerPlayer server && mech.canAccessCargo(player))
            server.openMenu(new SimpleMenuProvider((id, inventory, user) -> new BestMechCargoMenu(id, inventory, mech),
                    Component.translatable("menu.starfall_blueprint.best_mech_cargo")), buffer -> buffer.writeVarInt(mech.cargoSlots()));
    }
    @Override public boolean stillValid(Player player) {
        return player.level().isClientSide || mech != null && !mech.isRemoved()
                && mech.cargoSlots() == cargoSlots && mech.canAccessCargo(player);
    }
    @Override public void clicked(int index, int button, ClickType type, Player player) {
        if(stillValid(player)) super.clicked(index, button, type, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if(!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if(!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if(index < cargoSlots) {
            if(!moveItemStackTo(stack, cargoSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if(stack.getItem() instanceof BestMechItem || !moveItemStackTo(stack, 0, cargoSlots, false)) return ItemStack.EMPTY;
        if(stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
