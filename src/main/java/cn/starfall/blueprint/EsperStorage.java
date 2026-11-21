package cn.starfall.blueprint;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

final class EsperStorage extends SimpleContainer {
    private static final int SIZE = 54, AMMO = 45;
    private final ServerPlayer owner;
    private final int cargo;

    EsperStorage(ServerPlayer owner, int level) {
        super(SIZE);
        this.owner = owner;
        cargo = level < 1 ? 0 : Math.min(AMMO, 18 + (level - 1) * 9);
        var items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(Esper.data(owner).getCompound("Storage"), items, owner.registryAccess());
        for (int i = 0; i < SIZE; i++) super.setItem(i, items.get(i));
        addListener(c -> save());
    }
    private void save() {
        var items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        for (int i = 0; i < SIZE; i++) items.set(i, getItem(i));
        Esper.data(owner).put("Storage", ContainerHelper.saveAllItems(new net.minecraft.nbt.CompoundTag(), items, owner.registryAccess()));
    }
    void open() {
        owner.openMenu(new SimpleMenuProvider((id, inv, player) -> {
            var view = new SimpleContainer(cargo + 9) {
                private int map(int slot) { return slot < cargo ? slot : AMMO + slot - cargo; }
                @Override public ItemStack getItem(int slot) { return EsperStorage.this.getItem(map(slot)); }
                @Override public ItemStack removeItem(int slot, int amount) { return EsperStorage.this.removeItem(map(slot), amount); }
                @Override public ItemStack removeItemNoUpdate(int slot) {
                    ItemStack stack = EsperStorage.this.removeItemNoUpdate(map(slot));
                    EsperStorage.this.setChanged();
                    return stack;
                }
                @Override public void setItem(int slot, ItemStack stack) { EsperStorage.this.setItem(map(slot), stack); }
                @Override public void setChanged() { EsperStorage.this.setChanged(); }
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player p) { return p == owner && p.isAlive(); }
            };
            int rows = cargo / 9 + 1;
            var type = switch (rows) {
                case 3 -> MenuType.GENERIC_9x3;
                case 4 -> MenuType.GENERIC_9x4;
                case 5 -> MenuType.GENERIC_9x5;
                default -> MenuType.GENERIC_9x6;
            };
            return new ChestMenu(type, id, inv, view, rows);
        }, Esper.label("storage_title")));
    }
    ItemStack store(ItemStack stack) {
        var rest = stack.copy();
        for (int i = 0; i < cargo && !rest.isEmpty(); i++) {
            ItemStack existing = getItem(i);
            if (!ItemStack.isSameItemSameComponents(existing, rest)) continue;
            int count = Math.min(rest.getCount(), existing.getMaxStackSize() - existing.getCount());
            existing.grow(count); rest.shrink(count);
        }
        for (int i = 0; i < cargo && !rest.isEmpty(); i++)
            if (getItem(i).isEmpty()) setItem(i, rest.split(rest.getMaxStackSize()));
        setChanged();
        return rest;
    }
    ItemStack returnAmmo(int preferred, ItemStack payload) {
        ItemStack rest = payload.copy();
        int slot = Math.clamp(preferred, AMMO, SIZE - 1);
        for (int n = 0; n < SIZE - AMMO && !rest.isEmpty(); n++) {
            int i = AMMO + (slot - AMMO + n) % (SIZE - AMMO);
            ItemStack existing = getItem(i);
            if (existing.isEmpty()) setItem(i, rest.split(rest.getMaxStackSize()));
            else if (ItemStack.isSameItemSameComponents(existing, rest)) {
                int count = Math.min(rest.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(count); rest.shrink(count);
            }
        }
        if (!rest.isEmpty()) rest = store(rest);
        setChanged();
        return rest;
    }
    int firstAmmo() {
        for (int i = AMMO; i < SIZE; i++) if (!getItem(i).isEmpty()) return i;
        return -1;
    }
}
