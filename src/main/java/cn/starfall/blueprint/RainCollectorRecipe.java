package cn.starfall.blueprint;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Upgrading a previously filled tank transfers the real water instead of erasing it. */
public final class RainCollectorRecipe extends CustomRecipe {
    public RainCollectorRecipe(CraftingBookCategory category) { super(category); }
    @Override public boolean matches(CraftingInput in,Level level) {
        return in.width()==3 && in.height()==3 && in.getItem(0).is(Items.IRON_INGOT) && in.getItem(1).is(Items.GLASS)
                && in.getItem(2).is(Items.IRON_INGOT) && in.getItem(3).is(Items.GLASS) && in.getItem(4).is(StarfallBlueprint.TANK_ITEM.get())
                && in.getItem(5).isEmpty() && in.getItem(6).is(ItemTags.PLANKS) && in.getItem(7).is(ItemTags.PLANKS) && in.getItem(8).is(ItemTags.PLANKS);
    }
    @Override public ItemStack assemble(CraftingInput in,HolderLookup.Provider provider) {
        ItemStack output=new ItemStack(StarfallBlueprint.RAIN_ITEM.get());
        var data=in.getItem(4).get(DataComponents.BLOCK_ENTITY_DATA);
        if (data!=null) output.set(DataComponents.BLOCK_ENTITY_DATA,data);
        return output;
    }
    @Override public boolean canCraftInDimensions(int w,int h) { return w>=3 && h>=3; }
    @Override public RecipeSerializer<?> getSerializer() { return StarfallBlueprint.RAIN_RECIPE.get(); }
}
