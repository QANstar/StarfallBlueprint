package cn.starfall.blueprint;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Real component checks also apply when a recipe is invoked by automated crafting. */
public final class BasicCrystalRecipe extends CustomRecipe implements AutomaticCraftingLayout {
    private final String kind;
    public BasicCrystalRecipe(CraftingBookCategory category, String kind) { super(category); this.kind = kind; }
    private boolean upgrade() { return kind.startsWith("reinforce_"); }
    @Override public boolean matches(CraftingInput input, Level level) {
        int crystal = 0, metal = 0, redstone = 0, tool = 0, attachment = 0, glass = 0, catalyst = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i); if (s.isEmpty()) continue;
            if (upgrade()) {
                boolean pick = !kind.equals("reinforce_shield");
                if ((pick ? s.getItem() instanceof PickaxeItem : s.is(Items.SHIELD)) && s.isDamageableItem()) {
                    int percent = Reinforcement.percent(s);
                    boolean allowed = switch (kind) {
                        case "reinforce_tool" -> percent == 0 || percent == 10;
                        case "reinforce_composite" -> percent == 20;
                        default -> percent == 0;
                    };
                    if (!allowed) return false;
                    tool++;
                } else if (s.is(kind.equals("reinforce_wear") || kind.equals("reinforce_composite") ? CatalogueContent.item("wear") : pick ? StarfallBlueprint.TOOL_BRACE.get() : StarfallBlueprint.SHIELD_LINING.get())) attachment++;
                else if (kind.equals("reinforce_composite") && s.is(CatalogueContent.item("boroglass"))) glass++;
                else if (kind.equals("reinforce_composite") && s.is(Items.BLAZE_POWDER)) catalyst++;
                else return false;
            } else if (s.is(StarfallBlueprint.CRYSTAL.get())) {
                MaterialBatch b = s.get(StarfallBlueprint.BATCH.get()); if (b == null || !b.isQ0()) return false; crystal++;
            } else if (s.is(kind.equals("tool_brace") || kind.equals("shield_lining") ? Items.IRON_INGOT : Items.COPPER_INGOT)) metal++;
            else if (s.is(Items.REDSTONE) && kind.equals("indicator")) redstone++;
            else return false;
        }
        if (upgrade()) return tool == 1 && attachment == 1 && glass == (kind.equals("reinforce_composite") ? 1 : 0) && catalyst == glass;
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
                int percent = kind.equals("reinforce_wear") ? 10 : kind.equals("reinforce_composite") ? 30 : 20;
                Reinforcement.resize(result, s, percent, Reinforcement.baseDurability(s));
                return result;
            }
        }
        return ItemStack.EMPTY;
    }
    public AutomaticCraftingLayout.Layout automaticCraftingLayout() {
        if (upgrade()) {
            Ingredient tool = kind.equals("reinforce_shield") ? Ingredient.of(Items.SHIELD)
                    : Ingredient.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(i -> i instanceof PickaxeItem).map(ItemStack::new));
            Ingredient attachment = Ingredient.of(kind.equals("reinforce_wear") || kind.equals("reinforce_composite") ? CatalogueContent.item("wear")
                    : kind.equals("reinforce_shield") ? StarfallBlueprint.SHIELD_LINING.get() : StarfallBlueprint.TOOL_BRACE.get());
            if (kind.equals("reinforce_composite")) return new AutomaticCraftingLayout.Layout(2, 2,
                    java.util.List.of(tool, attachment, Ingredient.of(CatalogueContent.item("boroglass")), Ingredient.of(Items.BLAZE_POWDER)));
            return new AutomaticCraftingLayout.Layout(2, 1, java.util.List.of(tool, attachment));
        }
        var ingredients = new java.util.ArrayList<Ingredient>();
        ingredients.add(Ingredient.of(StarfallBlueprint.CRYSTAL.get()));
        ingredients.add(Ingredient.of(kind.equals("tool_brace") || kind.equals("shield_lining") ? Items.IRON_INGOT : Items.COPPER_INGOT));
        if (kind.equals("indicator")) ingredients.add(Ingredient.of(Items.REDSTONE));
        return new AutomaticCraftingLayout.Layout(kind.equals("tool_brace") ? 1 : ingredients.size(),
                kind.equals("tool_brace") ? 2 : 1, ingredients);
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= (kind.equals("reinforce_composite") ? 4 : kind.equals("indicator") ? 3 : 2); }
    @Override public RecipeSerializer<?> getSerializer() { return StarfallBlueprint.BASIC_RECIPES.get(kind).get(); }
}
