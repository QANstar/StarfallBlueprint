package cn.starfall.blueprint;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RepairItemRecipe;

/** Retain the upgrade slot when vanilla combines durability into a new stack. */
public final class ReinforcedRepairRecipe extends RepairItemRecipe implements AutomaticCraftingLayout {
    public ReinforcedRepairRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) {
        ItemStack result = super.assemble(input, provider);
        if (!result.isEmpty()) {
            ItemStack first = ItemStack.EMPTY;
            for (int i = 0; i < input.size(); i++) {
                ItemStack stack = input.getItem(i);
                if (stack.isEmpty()) continue;
                if (first.isEmpty()) first = stack;
                else Reinforcement.retainStrongest(result, first, stack);
            }
        }
        return result;
    }

    public AutomaticCraftingLayout.Layout automaticCraftingLayout() {
        var damageable=net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                .map(ItemStack::new).filter(ItemStack::isDamageableItem));
        return new AutomaticCraftingLayout.Layout(2,1,java.util.List.of(damageable,damageable));
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return StarfallBlueprint.REINFORCED_REPAIR_RECIPE.get();
    }
}
