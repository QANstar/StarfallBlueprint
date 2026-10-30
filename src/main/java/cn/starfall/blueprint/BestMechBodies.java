package cn.starfall.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** An independent physical chassis upgrade; absent and unknown keys mean the standard body. */
public final class BestMechBodies {
    public static final String STATE = "Body", REINFORCED = "best_mech_reinforced_body";
    private BestMechBodies() {}

    public static String sanitize(String key) { return REINFORCED.equals(key) ? key : ""; }
    public static String read(CompoundTag tag) { return sanitize(tag.getString(STATE)); }
    public static int capacity(String key) { return REINFORCED.equals(key) ? 30_000_000 : 20_000_000; }
    public static float maxHealth(String key) { return REINFORCED.equals(key) ? 160 : 100; }
    public static int cargoSlots(String key) { return REINFORCED.equals(key) ? 18 : 9; }
    public static String key(ItemStack stack) { return !stack.isEmpty() && stack.is(CatalogueContent.item(REINFORCED)) ? REINFORCED : ""; }
    public static Component name(String key) {
        return Component.translatable(sanitize(key).isEmpty() ? "mech.starfall_blueprint.body.none" : "item.starfall_blueprint." + REINFORCED);
    }
    public static Component installed(String key) { return Component.translatable("mech.starfall_blueprint.body.installed", name(key)); }
    public static Component controls() { return Component.translatable("mech.starfall_blueprint.body.controls"); }
}
