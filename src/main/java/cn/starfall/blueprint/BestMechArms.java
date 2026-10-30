package cn.starfall.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** An independent physical engineering arm; legacy chassis include their original drill. */
public final class BestMechArms {
    public static final String STATE = "EngineeringArm";
    public static final String DRILL = "best_mech_drill_arm", EXCAVATOR = "best_mech_excavator_arm";
    private BestMechArms() {}

    public static String sanitize(String key) { return DRILL.equals(key) || EXCAVATOR.equals(key) ? key : ""; }
    /** Absence is legacy/default drill; an explicit empty or invalid slot never grants an item. */
    public static String read(CompoundTag tag) { return tag.contains(STATE) ? sanitize(tag.getString(STATE)) : DRILL; }
    public static String key(ItemStack stack) {
        if(stack.isEmpty()) return "";
        if(stack.is(CatalogueContent.item(DRILL))) return DRILL;
        if(stack.is(CatalogueContent.item(EXCAVATOR))) return EXCAVATOR;
        return "";
    }
    public static int workCost(String key) { return DRILL.equals(key) ? 32000 : EXCAVATOR.equals(key) ? 12000 : 0; }
    public static int workHeat(String key) { return DRILL.equals(key) ? 120 : EXCAVATOR.equals(key) ? 45 : 0; }
    public static int cooldown(String key) { return sanitize(key).isEmpty() ? 0 : 20; }
    public static Component name(String key) {
        String safe = sanitize(key);
        return Component.translatable(safe.isEmpty() ? "mech.starfall_blueprint.arm.none" : "item.starfall_blueprint." + safe);
    }
    public static Component installed(String key) { return Component.translatable("mech.starfall_blueprint.arm.installed", name(key)); }
    public static Component controls() { return Component.translatable("mech.starfall_blueprint.arm.controls"); }
}
