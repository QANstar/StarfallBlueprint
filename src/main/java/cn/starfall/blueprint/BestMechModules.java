package cn.starfall.blueprint;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** One exclusive utility slot. Modules are ordinary physical items, without their own stored state. */
public final class BestMechModules {
    public static final String SLOT = "utility", STATE = "UtilityModule";
    public static final String RADIATOR = "best_mech_radiator", DRIVE = "best_mech_drive";
    private BestMechModules() {}

    public static String sanitize(String key) { return RADIATOR.equals(key) || DRIVE.equals(key) ? key : ""; }
    public static String key(ItemStack stack) {
        if(stack.isEmpty()) return "";
        if(stack.is(CatalogueContent.item(RADIATOR))) return RADIATOR;
        if(stack.is(CatalogueContent.item(DRIVE))) return DRIVE;
        return "";
    }
    public static int movementCost(String key) { return DRIVE.equals(key) ? 600 : 400; }
    public static int movementHeat(String key) { return DRIVE.equals(key) ? 6 : 4; }
    public static int coolingRate(String key) { return RADIATOR.equals(key) ? 4 : 2; }
    public static float speed(String key) { return DRIVE.equals(key) ? 0.28f : 0.22f; }
    public static Component name(String key) {
        String safe = sanitize(key);
        return Component.translatable(safe.isEmpty() ? "mech.starfall_blueprint.module.none" : "item.starfall_blueprint." + safe);
    }
    public static Component installed(String key) {
        return Component.translatable("mech.starfall_blueprint.module.installed", name(key));
    }
    public static Component controls() { return Component.translatable("mech.starfall_blueprint.module.controls"); }
}
