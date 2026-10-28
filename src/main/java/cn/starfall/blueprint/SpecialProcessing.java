package cn.starfall.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/** Native special recipes reserve their inputs once; the shared processing loop pays and settles them. */
public final class SpecialProcessing {
    private static final Set<String> KINDS = Set.of("auto_brewer", "auto_smith", "auto_stonecutter", "auto_blast_furnace", "auto_smoker");

    private SpecialProcessing() {}

    public static boolean supports(String kind) { return KINDS.contains(kind); }

    /** Stable real recipe holders also supply the menu's selection indices and labels. */
    public static List<RecipeHolder<?>> choices(Level level, String kind) {
        if (level == null) return List.of();
        RecipeType<?> type = switch (kind) {
            case "auto_smith" -> RecipeType.SMITHING;
            case "auto_stonecutter" -> RecipeType.STONECUTTING;
            case "auto_blast_furnace" -> RecipeType.BLASTING;
            case "auto_smoker" -> RecipeType.SMOKING;
            default -> null;
        };
        if (type == null) return List.of();
        return level.getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.value().getType() == type)
                .sorted(Comparator.comparing(holder -> holder.id().toString())).toList();
    }

    public static List<RecipeHolder<?>> forResult(Level level, String kind, ItemStack sample) {
        if (sample.isEmpty() || level == null) return List.of();
        return choices(level, kind).stream().filter(holder -> {
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            return !result.isEmpty() && result.is(sample.getItem());
        }).toList();
    }

    /** Removing the sample preserves the last selected recipe, including an unavailable recipe. */
    public static void selectSample(IndustryBlockEntity be) {
        if (!be.kind().equals("auto_stonecutter") || be.getLevel() == null) return;
        ItemStack sample = be.stock.get(IndustryBlockEntity.CRAFTING_SAMPLE);
        if (sample.isEmpty()) return;
        if (!be.template.isEmpty()) {
            var key = ResourceLocation.tryParse(be.template);
            if (key == null || be.getLevel().getRecipeManager().byKey(key).isEmpty()) return;
        }
        var options = forResult(be.getLevel(), be.kind(), sample);
        if (options.stream().anyMatch(holder -> holder.id().toString().equals(be.template))) return;
        var input = new SingleRecipeInput(one(be.stock.get(0)));
        be.template = options.stream()
                .filter(holder -> holder.value() instanceof StonecutterRecipe recipe && recipe.matches(input, be.getLevel()))
                .findFirst().or(() -> options.stream().findFirst())
                .map(holder -> holder.id().toString()).orElse("");
        be.setChanged();
    }

    /** Only real ingredient roles are insertable; power cells and samples are handled by the owner. */
    public static boolean accepts(IndustryBlockEntity be, int slot, ItemStack stack) {
        if (stack.isEmpty() || slot < 0 || slot > 8 || !supports(be.kind())) return false;
        Level level = be.getLevel();
        if (be.kind().equals("auto_brewer")) {
            if (slot == 4) return stack.is(Items.BLAZE_POWDER);
            if (level == null) return false;
            if (slot == 3) return level.potionBrewing().isIngredient(stack);
            return slot < 3 && level.potionBrewing().isInput(stack);
        }
        if (level == null) return false;
        if (be.kind().equals("auto_smith")) {
            if (slot > 2) return false;
            return choices(level, be.kind()).stream().anyMatch(holder -> holder.value() instanceof SmithingRecipe recipe
                    && switch (slot) {
                        case 0 -> recipe.isTemplateIngredient(stack);
                        case 1 -> recipe.isBaseIngredient(stack);
                        default -> recipe.isAdditionIngredient(stack);
                    });
        }
        if (be.kind().equals("auto_stonecutter") && slot != 0) return false;
        var input = new SingleRecipeInput(one(stack));
        return choices(level, be.kind()).stream().anyMatch(holder -> {
            if (holder.value() instanceof StonecutterRecipe recipe) return recipe.matches(input, level);
            if (holder.value() instanceof AbstractCookingRecipe recipe) return recipe.matches(input, level);
            return false;
        });
    }

    public static boolean start(IndustryBlockEntity be) {
        if (!supports(be.kind()) || !be.idle() || be.getLevel() == null) return false;
        be.status = 2;
        return switch (be.kind()) {
            case "auto_brewer" -> brew(be);
            case "auto_smith" -> smith(be);
            case "auto_stonecutter" -> stonecut(be);
            default -> cook(be);
        };
    }

    private static boolean selected(IndustryBlockEntity be, RecipeHolder<?> holder) {
        return be.template.isEmpty() || holder.id().toString().equals(be.template);
    }

    private static boolean brew(IndustryBlockEntity be) {
        ItemStack ingredient = one(be.stock.get(3));
        if (ingredient.isEmpty() || !be.stock.get(4).is(Items.BLAZE_POWDER)) return false;
        var brewing = be.getLevel().potionBrewing();
        List<ItemStack> outputs = new ArrayList<>();
        int[] used = new int[9];
        boolean changed = false;
        for (int i = 0; i < 3; i++) {
            ItemStack bottle = one(be.stock.get(i));
            if (bottle.isEmpty()) continue;
            if (brewing.hasMix(bottle, ingredient)) {
                ItemStack result = brewing.mix(ingredient.copy(), bottle.copy());
                if (result.isEmpty()) return false;
                outputs.add(result.copy());
                changed = true;
            } else outputs.add(bottle);
            used[i] = 1;
        }
        if (!changed) return false;
        addRemainder(outputs, ingredient);
        addRemainder(outputs, one(be.stock.get(4)));
        used[3] = used[4] = 1;
        return reserve(be, "brew", outputs, used, 400, 8000, 0);
    }

    private static boolean smith(IndustryBlockEntity be) {
        var input = new SmithingRecipeInput(one(be.stock.get(0)), one(be.stock.get(1)), one(be.stock.get(2)));
        for (var holder : choices(be.getLevel(), be.kind())) {
            if (!selected(be, holder) || !(holder.value() instanceof SmithingRecipe recipe)
                    || !recipe.matches(input, be.getLevel())) continue;
            ItemStack result = recipe.assemble(input, be.getLevel().registryAccess());
            if (result.isEmpty()) continue;
            List<ItemStack> outputs = new ArrayList<>();
            outputs.add(result.copy());
            for (ItemStack remainder : recipe.getRemainingItems(input)) if (!remainder.isEmpty()) outputs.add(remainder.copy());
            int[] used = new int[9];
            for (int i = 0; i < 3; i++) used[i] = input.getItem(i).isEmpty() ? 0 : 1;
            return reserve(be, holder.id().toString(), outputs, used, 80, 4000, 0);
        }
        return false;
    }

    private static boolean stonecut(IndustryBlockEntity be) {
        selectSample(be);
        if (!be.stock.get(IndustryBlockEntity.CRAFTING_SAMPLE).isEmpty() && be.template.isEmpty()) return false;
        var input = new SingleRecipeInput(one(be.stock.get(0)));
        if (input.getItem(0).isEmpty()) return false;
        for (var holder : choices(be.getLevel(), be.kind())) {
            if (!selected(be, holder) || !(holder.value() instanceof StonecutterRecipe recipe)
                    || !recipe.matches(input, be.getLevel())) continue;
            ItemStack result = recipe.assemble(input, be.getLevel().registryAccess());
            if (result.isEmpty()) continue;
            List<ItemStack> outputs = new ArrayList<>();
            outputs.add(result.copy());
            for (ItemStack remainder : recipe.getRemainingItems(input)) if (!remainder.isEmpty()) outputs.add(remainder.copy());
            int[] used = new int[9]; used[0] = 1;
            return reserve(be, holder.id().toString(), outputs, used, 20, 1000, 0);
        }
        return false;
    }

    private static boolean cook(IndustryBlockEntity be) {
        var recipes = choices(be.getLevel(), be.kind());
        for (int i = 0; i < 9; i++) {
            if (be.stock.get(i).isEmpty()) continue;
            var input = new SingleRecipeInput(one(be.stock.get(i)));
            for (var holder : recipes) {
                if (!selected(be, holder) || !(holder.value() instanceof AbstractCookingRecipe recipe)
                        || !recipe.matches(input, be.getLevel())) continue;
                ItemStack result = recipe.assemble(input, be.getLevel().registryAccess());
                if (result.isEmpty()) continue;
                List<ItemStack> outputs = new ArrayList<>();
                outputs.add(result.copy());
                for (ItemStack remainder : recipe.getRemainingItems(input)) if (!remainder.isEmpty()) outputs.add(remainder.copy());
                int[] used = new int[9]; used[i] = 1;
                return reserve(be, holder.id().toString(), outputs, used, 40, 2000, recipe.getExperience());
            }
        }
        return false;
    }

    private static ItemStack one(ItemStack stack) { return stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1); }

    private static void addRemainder(List<ItemStack> outputs, ItemStack consumed) {
        if (consumed.hasCraftingRemainingItem()) {
            ItemStack remainder = consumed.getCraftingRemainingItem();
            if (!remainder.isEmpty()) outputs.add(remainder.copy());
        }
    }

    private static boolean reserve(IndustryBlockEntity be, String recipe, List<ItemStack> outputs, int[] used,
                                   int ticks, long cost, float experience) {
        if (outputs.stream().anyMatch(stack -> CatalogueContent.RESERVED_ITEMS.contains(CatalogueContent.key(stack)))) {
            be.status = 8; return false;
        }
        var packed = AutomaticCrafting.packOutputs(outputs);
        if (packed.isEmpty() || !be.outputFits(packed.get())) { be.status = 5; return false; }
        if (be.getPower() <= 0) { be.status = 4; return false; }
        for (int i = 0; i < used.length; i++) be.stock.get(i).shrink(used[i]);
        be.activeRecipe = "special:" + be.kind() + ":" + recipe;
        be.pending = NonNullList.withSize(4, ItemStack.EMPTY);
        for (int i = 0; i < packed.get().size(); i++) be.pending.set(i, packed.get().get(i).copy());
        be.pendingFluid1 = FluidStack.EMPTY;
        be.pendingFluid2 = FluidStack.EMPTY;
        be.pendingExperience = experience;
        be.progressTicks = 0;
        be.processTicks = ticks;
        be.processCost = cost;
        be.processPaid = 0;
        be.status = 1;
        be.setChanged();
        return true;
    }
}
