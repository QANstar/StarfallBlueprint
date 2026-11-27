package cn.starfall.blueprint;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/** Saved per player; missing settings retain the previous terrain behavior. */
public final class PlayerManual {
    private static final String DATA = "StarfallManual";
    public static final int ESPER = 100, BLACK_DOG = 101;
    public static final DeferredItem<Item> ITEM = StarfallBlueprint.ITEMS.registerItem("instruction_manual", p -> new Item(p.stacksTo(1)) {
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (!level.isClientSide) Progression.open(player);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
    });
    public static boolean allows(Player player, int setting) {
        var data = player.getPersistentData().getCompound(DATA);
        String key = key(setting);
        return !data.contains(key) || data.getBoolean(key);
    }
    private static String key(int setting) {
        return switch (setting) { case ESPER -> "EsperBlockDamage"; case BLACK_DOG -> "BlackDogBlockDamage";
            default -> throw new IllegalArgumentException("Unknown personal setting"); };
    }
    public static void toggle(Player player, int setting) {
        var data = player.getPersistentData().getCompound(DATA).copy();
        data.putBoolean(key(setting), !allows(player, setting));
        player.getPersistentData().put(DATA, data);
    }
    public static void give(Player player) {
        if (player.level().isClientSide) return;
        var data = player.getPersistentData().getCompound(DATA).copy();
        if (data.getBoolean("Received")) return;
        // Retry on next login when the inventory is full rather than losing the manual.
        if (!player.getInventory().add(new ItemStack(ITEM.get()))) return;
        data.putBoolean("Received", true);
        player.getPersistentData().put(DATA, data);
    }
    public static void login(PlayerEvent.PlayerLoggedInEvent event) { give(event.getEntity()); }
    public static void clonePlayer(PlayerEvent.Clone event) {
        event.getEntity().getPersistentData().put(DATA, event.getOriginal().getPersistentData().getCompound(DATA).copy());
    }
    public static void initialize() {}
    private PlayerManual() {}
}
