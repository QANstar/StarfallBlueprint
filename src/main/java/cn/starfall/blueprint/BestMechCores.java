package cn.starfall.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Independent optional power core; absent and unknown keys retain electric operation. */
public final class BestMechCores {
    public static final String STATE = "Core", GENERATOR = "best_mech_generator_core";
    // Vanilla's longest furnace burn is 20,000 ticks; remainder-bearing fuels are refused.
    public static final int MAX_FUEL_TICKS = 20_000;
    private BestMechCores() {}

    public static String sanitize(String key) { return GENERATOR.equals(key) ? key : ""; }
    public static String read(CompoundTag tag) { return sanitize(tag.getString(STATE)); }
    public static String key(ItemStack stack) { return !stack.isEmpty() && stack.is(CatalogueContent.item(GENERATOR)) ? GENERATOR : ""; }
    public static Component name(String key) {
        return Component.translatable(sanitize(key).isEmpty() ? "mech.starfall_blueprint.core.none" : "item.starfall_blueprint." + GENERATOR);
    }
    public static Component installed(String key) { return Component.translatable("mech.starfall_blueprint.core.installed", name(key)); }
    public static Component controls() { return Component.translatable("mech.starfall_blueprint.core.controls"); }
    public static Component fuel(int ticks) { return Component.translatable("mech.starfall_blueprint.core.fuel", ticks); }
}
