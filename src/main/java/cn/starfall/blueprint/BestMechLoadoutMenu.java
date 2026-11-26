package cn.starfall.blueprint;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Unified live mech terminal: 8 hardware slots, upgrade chips, 9-27 cargo slots and quick service actions. */
public final class BestMechLoadoutMenu extends AbstractContainerMenu {
    public static final int HARDWARE_SLOTS = 8;
    public static final int SLOT_UTILITY = 0, SLOT_ARM = 1, SLOT_BODY = 2, SLOT_CORE = 3,
            SLOT_LEGS = 4, SLOT_WEAPON = 5, SLOT_DRONE = 6, SLOT_WIRELESS = 7;

    public static final int ACTION_TOGGLE_ISOLATION = 0;
    public static final int ACTION_QUICK_SERVICE = 1;
    public static final int ACTION_TRANSFER_CARGO = 2;
    public static final int ACTION_PACK_MECH = 3;
    public static final int ACTION_UPGRADE_BASE = 10; // 10..18 toggle install/remove upgrade index 0..8

    private final BestMechEntity mech;
    private final Player owner;
    private final int mechEntityId;
    private final int cargoSlots;

    public BestMechLoadoutMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, resolveClientMech(inventory.player, buffer.readVarInt()), buffer.readVarInt());
    }

    public BestMechLoadoutMenu(int id, Inventory inventory, BestMechEntity mech) {
        this(id, inventory, mech, mech.cargoSlots());
    }

    private static BestMechEntity resolveClientMech(Player player, int entityId) {
        Entity e = player != null && player.level() != null ? player.level().getEntity(entityId) : null;
        return e instanceof BestMechEntity m ? m : null;
    }

    private BestMechLoadoutMenu(int id, Inventory inventory, BestMechEntity mech, int cargoSize) {
        super(BestMechs.LOADOUT_MENU.get(), id);
        this.mech = mech;
        this.owner = inventory.player;
        this.mechEntityId = mech == null ? -1 : mech.getId();
        this.cargoSlots = (cargoSize == 9 || cargoSize == 18 || cargoSize == 27) ? cargoSize : 9;

        Container hardware = new HardwareContainer();
        // 8 hardware slots arranged in two 4-row vertical columns flanking the 3D preview on the left.
        int[] hx = {8, 8, 8, 8, 108, 108, 108, 108};
        int[] hy = {32, 54, 76, 98, 32, 54, 76, 98};
        for (int i = 0; i < HARDWARE_SLOTS; i++) {
            final int slotIdx = i;
            addSlot(new Slot(hardware, i, hx[i], hy[i]) {
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean mayPlace(ItemStack stack) { return canInsertHardware(slotIdx, stack); }
                @Override public boolean mayPickup(Player player) { return canRemoveHardware(slotIdx, player); }
            });
        }

        SimpleContainer cargo = mech == null ? new SimpleContainer(this.cargoSlots) : mech.cargo();
        for (int i = 0; i < this.cargoSlots; i++) {
            final int index = i;
            addSlot(new Slot(cargo, i, 134 + (i % 9) * 18, 32 + (i / 9) * 18) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return !(stack.getItem() instanceof BestMechItem) && cargo.canPlaceItem(index, stack);
                }
                @Override public boolean mayPickup(Player player) { return stillValid(player); }
            });
        }

        int invTop = 160;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 69 + col * 18, invTop + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 69 + col * 18, invTop + 58));
        }
    }

    public static void open(Player player, BestMechEntity mech) {
        if (player instanceof ServerPlayer server && mech.canAccessLoadout(player)) {
            server.openMenu(new SimpleMenuProvider(
                    (id, inv, user) -> new BestMechLoadoutMenu(id, inv, mech),
                    Component.translatable("menu.starfall_blueprint.best_mech_loadout")),
                    buf -> { buf.writeVarInt(mech.getId()); buf.writeVarInt(mech.cargoSlots()); });
        }
    }

    public BestMechEntity mech() { return mech; }
    public int mechEntityId() { return mechEntityId; }
    public int cargoSlots() { return cargoSlots; }

    public boolean hardwareEditable() {
        return mech != null && mech.isAlive() && mech.isolated() && mech.getPassengers().isEmpty()
                && mech.onGround() && mech.getDeltaMovement().horizontalDistanceSqr() <= 0.001
                && Math.abs(mech.getDeltaMovement().y) <= 0.1 && !mech.isInWater() && !mech.isInLava();
    }

    private boolean canInsertHardware(int slot, ItemStack stack) {
        if (mech == null || stack.isEmpty() || !stillValid(owner)) return false;
        if (!owner.level().isClientSide && !mech.canEditHardware(owner)) return false;
        if (owner.level().isClientSide && !hardwareEditable()) return false;
        return switch (slot) {
            case SLOT_UTILITY -> {
                String key = BestMechModules.key(stack);
                yield !key.isEmpty() && (!BestMechSupport.isModule(key) || mech.engineeringCapable());
            }
            case SLOT_ARM -> {
                String key = BestMechArms.key(stack);
                yield mech.engineeringCapable() && !key.isEmpty() && (mech.supportsAreaTools() || !BestMechArms.areaWork(key));
            }
            case SLOT_BODY -> mech.supportsBody() && !BestMechBodies.key(stack).isEmpty();
            case SLOT_CORE -> {
                String key = BestMechCores.key(stack);
                yield !key.isEmpty() && (!BestMechCores.FUSION.equals(key) || BestMechProgression.UNIVERSAL.equals(mech.platformType()));
            }
            case SLOT_LEGS -> !BestMechLegs.key(stack).isEmpty();
            case SLOT_WEAPON -> mech.combatCapable() && !BestMechCombatSystems.weaponKey(stack).isEmpty();
            case SLOT_DRONE -> mech.combatCapable() && !BestMechCombatSystems.droneKey(stack).isEmpty();
            case SLOT_WIRELESS -> stack.is(CatalogueContent.item(BestMechPowerSystems.WIRELESS));
            default -> false;
        };
    }

    private boolean canRemoveHardware(int slot, Player player) {
        if (mech == null || !stillValid(player)) return false;
        if (!player.level().isClientSide && !mech.canEditHardware(player)) return false;
        if (player.level().isClientSide && !hardwareEditable()) return false;
        if (slot == SLOT_CORE && (mech.coreFuelTicks() > 0 || !mech.powerSystems().fusionCanRemove())) return false;
        if (slot == SLOT_BODY) {
            if (mech.energy() > BestMechEntity.CAPACITY || mech.getHealth() > 100) return false;
            for (int i = 9; i < 18; i++) if (!mech.cargo().getItem(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide) return true;
        return mech != null && !mech.isRemoved() && mech.cargoSlots() == cargoSlots && mech.canAccessLoadout(player);
    }

    @Override
    public void clicked(int index, int button, ClickType type, Player player) {
        if (stillValid(player)) super.clicked(index, button, type, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int action) {
        if (!(player instanceof ServerPlayer server) || player instanceof net.neoforged.neoforge.common.util.FakePlayer
                || player.level().isClientSide || player != owner || player.containerMenu != this || !stillValid(player)) {
            return false;
        }
        if (action == ACTION_TOGGLE_ISOLATION) {
            if (!mech.getPassengers().isEmpty()) {
                server.displayClientMessage(Component.literal("请先下车再切换隔离状态"), true);
                return false;
            }
            mech.setIsolated(!mech.isolated());
            server.displayClientMessage(Component.literal(mech.isolated() ? "机甲已隔离，可换装硬件与整备" : "机甲已接通电源"), true);
            broadcastChanges();
            return true;
        }
        if (action == ACTION_QUICK_SERVICE) {
            boolean ok = mech.quickServiceFromPlayer(server);
            broadcastChanges();
            return ok;
        }
        if (action == ACTION_TRANSFER_CARGO) {
            for (int i = HARDWARE_SLOTS; i < HARDWARE_SLOTS + cargoSlots; i++) quickMoveStack(player, i);
            broadcastChanges();
            return true;
        }
        if (action == ACTION_PACK_MECH) {
            if (!mech.getPassengers().isEmpty()) {
                server.displayClientMessage(Component.literal("请先下车再收纳机甲"), true);
                return false;
            }
            boolean wasIsolated = mech.isolated();
            mech.setIsolated(true);
            if (mech.pack(server)) {
                server.closeContainer();
                server.displayClientMessage(Component.literal("机甲已收纳至背包"), true);
                return true;
            }
            mech.setIsolated(wasIsolated);
            server.displayClientMessage(Component.literal("无法收纳：请确认停稳在地面、不在水中且背包有空位"), true);
            return false;
        }
        if (action >= ACTION_UPGRADE_BASE && action < ACTION_UPGRADE_BASE + 9) {
            int idx = action - ACTION_UPGRADE_BASE;
            boolean ok = mech.toggleUpgradeFromGui(server, idx);
            if (ok) {
                if (mech.cargoSlots() != cargoSlots) {
                    // Reopen terminal cleanly if cargo size changed (e.g. Canglan Engineering Extension).
                    BestMechLoadoutMenu.open(server, mech);
                } else {
                    broadcastChanges();
                }
            }
            return ok;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        int cargoStart = HARDWARE_SLOTS;
        int invStart = HARDWARE_SLOTS + cargoSlots;
        if (index < HARDWARE_SLOTS) {
            if (!moveItemStackTo(stack, invStart, slots.size(), true)) return ItemStack.EMPTY;
        } else if (index < invStart) {
            if (!moveItemStackTo(stack, invStart, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // Try matching hardware slot first if editable, otherwise move into cargo.
            boolean moved = false;
            if (hardwareEditable()) {
                for (int i = 0; i < HARDWARE_SLOTS; i++) {
                    if (slots.get(i).getItem().isEmpty() && canInsertHardware(i, stack)) {
                        if (moveItemStackTo(stack, i, i + 1, false)) { moved = true; break; }
                    }
                }
            }
            if (!moved && (stack.getItem() instanceof BestMechItem || !moveItemStackTo(stack, cargoStart, invStart, false))) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    private final class HardwareContainer implements Container {
        @Override public int getContainerSize() { return HARDWARE_SLOTS; }
        @Override public boolean isEmpty() {
            for (int i = 0; i < HARDWARE_SLOTS; i++) if (!getItem(i).isEmpty()) return false;
            return true;
        }
        @Override public ItemStack getItem(int slot) {
            if (mech == null) return ItemStack.EMPTY;
            String key = switch (slot) {
                case SLOT_UTILITY -> mech.moduleKey();
                case SLOT_ARM -> mech.armKey();
                case SLOT_BODY -> mech.bodyKey();
                case SLOT_CORE -> mech.coreKey();
                case SLOT_LEGS -> mech.legsKey();
                case SLOT_WEAPON -> mech.combatSystems().installedWeaponRaw();
                case SLOT_DRONE -> mech.combatSystems().installedDroneRaw();
                case SLOT_WIRELESS -> mech.visualWirelessInstalled() ? BestMechPowerSystems.WIRELESS : "";
                default -> "";
            };
            return key.isEmpty() ? ItemStack.EMPTY : CatalogueContent.stack(key, 1);
        }
        @Override public ItemStack removeItem(int slot, int amount) {
            if (amount <= 0) return ItemStack.EMPTY;
            return removeItemNoUpdate(slot);
        }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            ItemStack current = getItem(slot);
            if (current.isEmpty() || mech == null) return ItemStack.EMPTY;
            if (!owner.level().isClientSide) {
                mech.guiSetHardwareSlot(slot, "", owner);
                if (slot == SLOT_BODY && mech.cargoSlots() != cargoSlots && owner instanceof ServerPlayer server) {
                    BestMechLoadoutMenu.open(server, mech);
                }
            }
            return current;
        }
        @Override public void setItem(int slot, ItemStack stack) {
            if (mech == null || owner.level().isClientSide) return;
            String key = switch (slot) {
                case SLOT_UTILITY -> BestMechModules.key(stack);
                case SLOT_ARM -> BestMechArms.key(stack);
                case SLOT_BODY -> BestMechBodies.key(stack);
                case SLOT_CORE -> BestMechCores.key(stack);
                case SLOT_LEGS -> BestMechLegs.key(stack);
                case SLOT_WEAPON -> BestMechCombatSystems.weaponKey(stack);
                case SLOT_DRONE -> BestMechCombatSystems.droneKey(stack);
                case SLOT_WIRELESS -> stack.is(CatalogueContent.item(BestMechPowerSystems.WIRELESS)) ? BestMechPowerSystems.WIRELESS : "";
                default -> "";
            };
            mech.guiSetHardwareSlot(slot, stack.isEmpty() ? "" : key, owner);
            if (slot == SLOT_BODY && mech.cargoSlots() != cargoSlots && owner instanceof ServerPlayer server) {
                BestMechLoadoutMenu.open(server, mech);
            }
        }
        @Override public int getMaxStackSize() { return 1; }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player player) { return BestMechLoadoutMenu.this.stillValid(player); }
        @Override public void clearContent() {}
    }
}
