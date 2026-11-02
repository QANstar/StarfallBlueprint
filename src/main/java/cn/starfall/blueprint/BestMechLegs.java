package cn.starfall.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Independent optional running gear; absent and unknown keys retain the original walking legs. */
public final class BestMechLegs {
    public static final String STATE = "Legs", CRUISE = "best_mech_cruise_wheels";
    private BestMechLegs() {}

    public static String sanitize(String key) { return CRUISE.equals(key) ? key : ""; }
    public static String read(CompoundTag tag) { return sanitize(tag.getString(STATE)); }
    public static String key(ItemStack stack) { return !stack.isEmpty() && stack.is(CatalogueContent.item(CRUISE)) ? CRUISE : ""; }
    public static Component name(String key) {
        return Component.translatable(sanitize(key).isEmpty() ? "mech.starfall_blueprint.legs.none" : "item.starfall_blueprint." + CRUISE);
    }
    public static Component installed(String key) { return Component.translatable("mech.starfall_blueprint.legs.installed", name(key)); }
    public static Component controls() { return Component.translatable("mech.starfall_blueprint.legs.controls"); }
}
