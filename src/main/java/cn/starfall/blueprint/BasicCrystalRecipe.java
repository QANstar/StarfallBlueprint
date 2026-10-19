package cn.starfall.blueprint;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Real component checks also apply when a recipe is invoked by automated crafting. */
public final class BasicCrystalRecipe extends CustomRecipe {
    private final String kind;
    public BasicCrystalRecipe(CraftingBookCategory category, String kind) { super(category); this.kind = kind; }
    private boolean upgrade() { return kind.startsWith("reinforce_"); }
    @Override public boolean matches(CraftingInput input, Level level) {
        int crystal = 0, metal = 0, redstone = 0, tool = 0, attachment = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i); if (s.isEmpty()) continue;
            if (upgrade()) {
                boolean pick = kind.equals("reinforce_tool");
                if ((pick ? s.getItem() instanceof PickaxeItem : s.is(Items.SHIELD)) && s.isDamageableItem() && !s.getOrDefault(StarfallBlueprint.REINFORCED.get(), false)) tool++;
                else if (s.is(pick ? StarfallBlueprint.TOOL_BRACE.get() : StarfallBlueprint.SHIELD_LINING.get())) attachment++;
                else return false;
            } else if (s.is(StarfallBlueprint.CRYSTAL.get())) {
                MaterialBatch b = s.get(StarfallBlueprint.BATCH.get()); if (b == null || !b.isQ0()) return false; crystal++;
            } else if (s.is(kind.equals("tool_brace") || kind.equals("shield_lining") ? Items.IRON_INGOT : Items.COPPER_INGOT)) metal++;
            else if (s.is(Items.REDSTONE) && kind.equals("indicator")) redstone++;
            else return false;
        }
        if (upgrade()) return tool == 1 && attachment == 1;
        if (crystal != 1 || metal != 1 || redstone != (kind.equals("indicator") ? 1 : 0)) return false;
        // Same budget, distinct patterns: vertical tool brace / horizontal shield lining.
        if (kind.equals("tool_brace")) return input.width() == 1 && input.height() == 2 && input.getItem(0).is(StarfallBlueprint.CRYSTAL.get());
        if (kind.equals("shield_lining")) return input.width() == 2 && input.height() == 1 && input.getItem(0).is(StarfallBlueprint.CRYSTAL.get());
        return true;
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) {
        if (!upgrade()) return new ItemStack(switch(kind) {
            case "connector" -> StarfallBlueprint.CONNECTOR.get(); case "indicator" -> StarfallBlueprint.SCANNER.get();
            case "tool_brace" -> StarfallBlueprint.TOOL_BRACE.get(); default -> StarfallBlueprint.SHIELD_LINING.get();
        });
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isDamageableItem()) {
                ItemStack result = s.copyWithCount(1);
                int oldMax = s.getMaxDamage(), newMax = oldMax * 6 / 5;
                result.set(DataComponents.MAX_DAMAGE, newMax);
                result.setDamageValue((int)(((long)s.getDamageValue() * newMax + oldMax - 1) / oldMax));
                result.set(StarfallBlueprint.REINFORCED.get(), true);
                return result;
            }
        }
        return ItemStack.EMPTY;
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= (kind.equals("indicator") ? 3 : 2); }
    @Override public RecipeSerializer<?> getSerializer() { return StarfallBlueprint.BASIC_RECIPES.get(kind).get(); }
}
