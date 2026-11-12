package cn.starfall.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Shared packed/runtime capability rules. Platform identity always comes from registry identity. */
public final class BestMechProgression {
    public static final String ENGINEERING="engineering", COMBAT="combat", UNIVERSAL="universal", STATE="UpgradeMask";
    public static final int ENGINEERING_EXTENSION=1, MOBILITY=2, ENDURANCE=4, FIRE_CONTROL=8, ARMOR=16, SUSTAINED_FIRE=32, ZHENYUAN=64, TIANPU=128, CHANGCHUAN=256;
    public static String type(ItemStack stack) { return stack.getItem() instanceof BestMechItem item ? item.platformType() : ""; }
    public static int mask(CompoundTag tag) { return tag.getInt(STATE)&511; }
    public static boolean has(CompoundTag tag,int bit) { return (mask(tag)&bit)!=0; }
    public static boolean compatible(String type,int bit) { return Integer.bitCount(bit)==1 && (bit & (ENGINEERING.equals(type)?7:COMBAT.equals(type)?56:UNIVERSAL.equals(type)?448:0))!=0; }
    public static int sanitize(String type,int mask) { return mask & (ENGINEERING.equals(type)?7:COMBAT.equals(type)?56:UNIVERSAL.equals(type)?448:0); }
    public static int capacity(String type,CompoundTag tag) {
        if(UNIVERSAL.equals(type)) return has(tag,CHANGCHUAN)?160_000_000:120_000_000;
        if(COMBAT.equals(type)) return has(tag,SUSTAINED_FIRE)?60_000_000:40_000_000;
        return has(tag,ENDURANCE)?60_000_000:BestMechBodies.capacity(BestMechBodies.read(tag));
    }
    public static float maxHealth(String type,CompoundTag tag) {
        if(UNIVERSAL.equals(type)) return has(tag,ZHENYUAN)?650:500;
        if(COMBAT.equals(type)) return has(tag,ARMOR)?350:200;
        return BestMechBodies.maxHealth(BestMechBodies.read(tag));
    }
    public static String itemKey(String type) { return COMBAT.equals(type)?"best_combat_mech":UNIVERSAL.equals(type)?"best_universal_mech":"best_mech"; }
    private BestMechProgression() {}
}
