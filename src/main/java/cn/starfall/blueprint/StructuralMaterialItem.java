package cn.starfall.blueprint;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;

/** A portion is the mass of ONE item; stack counts never multiply a crafting slot's deduction. */
public final class StructuralMaterialItem extends Item {
    private final String name;
    public StructuralMaterialItem(Properties properties, String name) { super(properties); this.name = name; }
    public static int mass(ItemStack stack) { return stack.getOrDefault(StarfallBlueprint.PORTION_MASS.get(), 100); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (stack.has(StarfallBlueprint.PORTION_MASS.get()))
            lines.add(Component.translatable("tooltip.starfall_blueprint.portion_mass", mass(stack)));
    }
    @Override public int getBurnTime(ItemStack stack, RecipeType<?> recipeType) {
        return name.equals("carbon_portion") ? 16 * mass(stack) : 0;
    }
}
