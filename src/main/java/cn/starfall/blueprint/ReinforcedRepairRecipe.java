package cn.starfall.blueprint;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RepairItemRecipe;

/** Retain the upgrade slot when vanilla combines durability into a new stack. */
public final class ReinforcedRepairRecipe extends RepairItemRecipe {
    public ReinforcedRepairRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) {
        ItemStack result = super.assemble(input, provider);
        if (!result.isEmpty()) {
            for (int i = 0; i < input.size(); i++) {
                if (input.getItem(i).getOrDefault(StarfallBlueprint.REINFORCED.get(), false)) {
                    result.set(StarfallBlueprint.REINFORCED.get(), true);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return StarfallBlueprint.REINFORCED_REPAIR_RECIPE.get();
    }
}
