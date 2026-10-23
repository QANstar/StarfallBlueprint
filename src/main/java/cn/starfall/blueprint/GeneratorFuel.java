package cn.starfall.blueprint;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/** Furnace-compatible fuel handling used only by the ember generator. */
public final class GeneratorFuel {
    private GeneratorFuel() {}

    public static int heat(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int burnTicks = stack.getBurnTime(RecipeType.SMELTING);
        return burnTicks <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, (long) burnTicks * 200);
    }

    public static boolean isFuel(ItemStack stack) {
        return heat(stack) > 0;
    }

    /** Reserve one fuel's heat only after all of its actual remaining items fit. */
    public static boolean refuel(IndustryBlockEntity be) {
        if (be.fuelHeat != 0 || be.getPower() >= be.capacity()) return false;
        ItemStack fuel = be.stock.get(13);
        ItemStack unit = fuel.copyWithCount(1);
        int heat = heat(unit);
        if (heat == 0) return false;
        ItemStack remainder = unit.getCraftingRemainingItem();
        List<ItemStack> outputs = remainder.isEmpty() ? List.of() : List.of(remainder);
        // planOutputs simulates the entire insertion before it commits any slot.
        if (!be.planOutputs(outputs, true)) return false;
        fuel.shrink(1);
        be.fuelHeat = heat;
        be.setChanged();
        return true;
    }
}
