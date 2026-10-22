package cn.starfall.blueprint;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** One whole plate + one quartz -> two substrates. Legacy fractional plates cannot enter. */
public final class WholeSubstrateRecipe extends CustomRecipe implements AutomaticCraftingLayout {
    public WholeSubstrateRecipe(CraftingBookCategory category) { super(category); }
    public boolean matches(CraftingInput input, Level level) {
        int plate = 0, quartz = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i); if (s.isEmpty()) continue;
            if (s.is(StarfallBlueprint.structural("copper_plate")) && StructuralMaterialItem.mass(s) == 100) plate++;
            else if (s.is(Items.QUARTZ)) quartz++;
            else return false;
        }
        return plate == 1 && quartz == 1;
    }
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) { return new ItemStack(StarfallBlueprint.structural("isolation_substrate"), 2); }
    public ItemStack getResultItem(HolderLookup.Provider provider) { return new ItemStack(StarfallBlueprint.structural("isolation_substrate"), 2); }
    public boolean isSpecial() { return false; }
    public net.minecraft.core.NonNullList<Ingredient> getIngredients() {
        return net.minecraft.core.NonNullList.of(Ingredient.EMPTY, Ingredient.of(StarfallBlueprint.structural("copper_plate")), Ingredient.of(Items.QUARTZ));
    }
    public AutomaticCraftingLayout.Layout automaticCraftingLayout() { return new AutomaticCraftingLayout.Layout(2,1,getIngredients()); }
    public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    public RecipeSerializer<?> getSerializer() { return StarfallBlueprint.SUBSTRATE_RECIPE.get(); }
}
