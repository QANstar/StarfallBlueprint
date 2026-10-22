package cn.starfall.blueprint;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/** One finite upgrade slot; all durability changes use its unmodified capacity. */
public final class Reinforcement {
    private static final String PERCENT = "ReinforcementPercent";
    private static final String BASE = "ReinforcementBaseDurability";
    private Reinforcement() {}

    public static int percent(ItemStack stack) {
        if (!stack.getOrDefault(StarfallBlueprint.REINFORCED.get(), false)) return 0;
        int value = CatalogueEquipment.getData(stack).getInt(PERCENT);
        if (value == 10 || value == 20 || value == 30) return value;
        // Older saves sometimes contain only the boolean and the modified maximum.
        int itemBase = new ItemStack(stack.getItem()).getMaxDamage();
        for (int legacy : new int[]{10, 20, 30}) {
            if (itemBase > 0 && maximum(itemBase, legacy) == stack.getMaxDamage()) return legacy;
        }
        return 20; // Unknown custom capacities retain the historical phase interpretation.
    }

    public static int baseDurability(ItemStack stack) {
        int saved = CatalogueEquipment.getData(stack).getInt(BASE);
        if (saved > 0) return saved;
        int percent = percent(stack);
        return (int) (((long) stack.getMaxDamage() * 100 + 99 + percent) / (100 + percent));
    }

    public static int maximum(int base, int percent) {
        return (int) Math.min(Integer.MAX_VALUE, (long) base * (100 + percent) / 100);
    }

    public static void mark(ItemStack stack, int percent, int base) {
        if (percent != 10 && percent != 20 && percent != 30) throw new IllegalArgumentException("Invalid reinforcement level: " + percent);
        stack.set(StarfallBlueprint.REINFORCED.get(), true);
        var data = CatalogueEquipment.getData(stack);
        data.putInt(PERCENT, percent);
        data.putInt(BASE, base);
        CatalogueEquipment.setData(stack, data);
    }

    public static void resize(ItemStack result, ItemStack source, int percent, int base) {
        int maximum = maximum(base, percent);
        result.set(DataComponents.MAX_DAMAGE, maximum);
        result.setDamageValue((int) (((long) source.getDamageValue() * maximum + source.getMaxDamage() - 1) / source.getMaxDamage()));
        mark(result, percent, base);
    }

    /** Repairs retain one existing level; combining 10 and 20 never creates 30. */
    public static void retainStrongest(ItemStack result, ItemStack first, ItemStack second) {
        ItemStack strongest = percent(second) > percent(first) ? second : first;
        if (percent(first) == percent(second) && second.getMaxDamage() > first.getMaxDamage()) strongest = second;
        int percent = percent(strongest);
        if (percent > 0) mark(result, percent, baseDurability(strongest));
    }
}
