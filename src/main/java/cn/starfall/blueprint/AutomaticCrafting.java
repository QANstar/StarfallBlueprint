package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Plans against copies, then lets the machine reserve counts and output exactly once. */
public final class AutomaticCrafting {
    private AutomaticCrafting() {}
    private static final int SEARCH_LIMIT = 8192;

    public static List<RecipeHolder<?>> recipes(Level level) {
        return level.getRecipeManager().getRecipes().stream()
                .filter(h -> h.value() instanceof AutomaticCraftingRecipe
                        || h.value() instanceof CraftingRecipe r && (r instanceof AutomaticCraftingLayout
                            || r instanceof ShapedRecipe || r instanceof ShapelessRecipe))
                .sorted(Comparator.comparing(h -> h.id().toString())).toList();
    }

    private static AutomaticCraftingLayout.Layout layout(CraftingRecipe recipe) {
        if (recipe instanceof AutomaticCraftingLayout explicit) return explicit.automaticCraftingLayout();
        var ingredients = recipe.getIngredients();
        if (ingredients.isEmpty() || ingredients.size() > 54) return null;
        if (recipe instanceof ShapedRecipe shaped)
            return new AutomaticCraftingLayout.Layout(shaped.getWidth(), shaped.getHeight(), ingredients);
        // Only shapeless recipes have an inferable ingredient order. Other custom recipes opt in explicitly.
        if (recipe instanceof ShapelessRecipe)
            return new AutomaticCraftingLayout.Layout(ingredients.size(), 1, ingredients);
        return null;
    }

    public static Optional<AutomaticCraftingRecipe.Plan> plan(Recipe<?> recipe, List<ItemStack> inventory, Level level) {
        List<ItemStack> snapshot = inventory.stream().map(ItemStack::copy).toList();
        Optional<AutomaticCraftingRecipe.Plan> result;
        if (recipe instanceof AutomaticCraftingRecipe explicit) result = explicit.planAutomaticCraft(snapshot, level);
        else if (recipe instanceof CraftingRecipe crafting) {
            var shape = layout(crafting);
            result = shape == null ? Optional.empty() : new Search(crafting, shape, snapshot, level).run();
        } else return Optional.empty();
        return result.filter(p -> valid(p, inventory));
    }

    private static boolean valid(AutomaticCraftingRecipe.Plan plan, List<ItemStack> inventory) {
        if (plan.consumed().size() != inventory.size() || plan.outputs().isEmpty()) return false;
        long consumed = 0;
        for (int i = 0; i < inventory.size(); i++) {
            int count = plan.consumed().get(i);
            if (count < 0 || count > inventory.get(i).getCount()) return false;
            consumed += count;
        }
        return consumed > 0 && plan.outputs().stream().noneMatch(ItemStack::isEmpty);
    }

    /** Normalizes oversized output lists into the four pending slots without merging unlike components. */
    public static Optional<List<ItemStack>> packOutputs(List<ItemStack> outputs) {
        List<ItemStack> packed = new ArrayList<>();
        for (ItemStack output : outputs) {
            if (output.isEmpty()) return Optional.empty();
            int left = output.getCount();
            for (ItemStack existing : packed) if (ItemStack.isSameItemSameComponents(existing, output)) {
                int n = Math.min(left, existing.getMaxStackSize() - existing.getCount());
                existing.grow(n); left -= n;
            }
            while (left > 0) {
                if (packed.size() == 4) return Optional.empty();
                int n = Math.min(left, output.getMaxStackSize());
                if (n <= 0) return Optional.empty();
                packed.add(output.copyWithCount(n)); left -= n;
            }
        }
        return packed.isEmpty() ? Optional.empty() : Optional.of(packed);
    }

    private static final class Group {
        final ItemStack sample;
        int remaining;
        Group(ItemStack stack) { sample = stack.copyWithCount(1); remaining = stack.getCount(); }
    }
    private static final class Search {
        final CraftingRecipe recipe;
        final AutomaticCraftingLayout.Layout shape;
        final List<ItemStack> inventory;
        final Level level;
        final List<Group> groups = new ArrayList<>();
        final List<Integer> order = new ArrayList<>();
        final List<List<Integer>> candidates = new ArrayList<>();
        final int[] choice;
        int visits;
        AutomaticCraftingRecipe.Plan answer;
        Search(CraftingRecipe recipe, AutomaticCraftingLayout.Layout shape, List<ItemStack> inventory, Level level) {
            this.recipe = recipe; this.shape = shape; this.inventory = inventory; this.level = level;
            for (ItemStack stack : inventory) if (!stack.isEmpty()) {
                Group found = groups.stream().filter(g -> ItemStack.isSameItemSameComponents(g.sample, stack)).findFirst().orElse(null);
                if (found == null) groups.add(new Group(stack)); else found.remaining += stack.getCount();
            }
            choice = new int[shape.ingredients().size()]; Arrays.fill(choice, -1);
            for (int i = 0; i < choice.length; i++) {
                Ingredient ingredient = shape.ingredients().get(i);
                List<Integer> options = new ArrayList<>();
                if (!ingredient.isEmpty()) {
                    for (int j = 0; j < groups.size(); j++) if (ingredient.test(groups.get(j).sample)) options.add(j);
                    order.add(i);
                }
                candidates.add(options);
            }
            order.sort(Comparator.comparingInt(i -> candidates.get(i).size()));
        }
        Optional<AutomaticCraftingRecipe.Plan> run() { search(0); return Optional.ofNullable(answer); }
        boolean search(int depth) {
            if (++visits > SEARCH_LIMIT) return false;
            if (depth == order.size()) return finish();
            int cell = order.get(depth);
            for (int groupIndex : candidates.get(cell)) {
                Group group = groups.get(groupIndex);
                if (group.remaining == 0) continue;
                group.remaining--; choice[cell] = groupIndex;
                if (search(depth + 1)) return true;
                group.remaining++; choice[cell] = -1;
            }
            return false;
        }
        boolean finish() {
            List<ItemStack> cells = new ArrayList<>();
            for (int group : choice) cells.add(group < 0 ? ItemStack.EMPTY : groups.get(group).sample.copy());
            CraftingInput input = CraftingInput.of(shape.width(), shape.height(), cells);
            if (!recipe.matches(input, level)) return false;
            ItemStack result = recipe.assemble(input, level.registryAccess());
            if (result.isEmpty()) return false;
            List<ItemStack> outputs = new ArrayList<>(); outputs.add(result.copy());
            for (ItemStack remainder : recipe.getRemainingItems(input)) if (!remainder.isEmpty()) outputs.add(remainder.copy());
            int[] needed = new int[groups.size()];
            for (int group : choice) if (group >= 0) needed[group]++;
            List<Integer> consumed = new ArrayList<>();
            for (ItemStack stack : inventory) {
                int used = 0;
                if (!stack.isEmpty()) for (int j = 0; j < groups.size(); j++) if (ItemStack.isSameItemSameComponents(stack, groups.get(j).sample)) {
                    used = Math.min(stack.getCount(), needed[j]); needed[j] -= used; break;
                }
                consumed.add(used);
            }
            answer = new AutomaticCraftingRecipe.Plan(consumed, outputs); return true;
        }
    }

    /** Integral maximum flow allocates overlapping/tag ingredients without greedily stealing scarce inputs. */
    public static Optional<List<Integer>> allocate(List<ItemStack> inventory, List<Ingredient> ingredients, List<Integer> counts) {
        int n = inventory.size(), m = ingredients.size(), sink = n + m + 1, size = sink + 1;
        int[][] residual = new int[size][size];
        int demand = 0;
        for (int i = 0; i < n; i++) residual[0][i + 1] = inventory.get(i).getCount();
        for (int j = 0; j < m; j++) {
            int count = counts.get(j);
            if (count < 1 || count > 3456) return Optional.empty();
            demand += count; residual[n + 1 + j][sink] = count;
            for (int i = 0; i < n; i++) if (ingredients.get(j).test(inventory.get(i))) residual[i + 1][n + 1 + j] = count;
        }
        if (demand > 3456) return Optional.empty();
        int total = 0;
        while (total < demand) {
            int[] parent = new int[size]; Arrays.fill(parent, -1); parent[0] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(0);
            while (!queue.isEmpty() && parent[sink] < 0) {
                int u = queue.removeFirst();
                for (int v = 1; v < size; v++) if (parent[v] < 0 && residual[u][v] > 0) { parent[v] = u; queue.add(v); }
            }
            if (parent[sink] < 0) return Optional.empty();
            int amount = demand - total;
            for (int v = sink; v != 0; v = parent[v]) amount = Math.min(amount, residual[parent[v]][v]);
            for (int v = sink; v != 0; v = parent[v]) { residual[parent[v]][v] -= amount; residual[v][parent[v]] += amount; }
            total += amount;
        }
        List<Integer> used = new ArrayList<>();
        for (int i = 0; i < n; i++) used.add(residual[i + 1][0]);
        return Optional.of(used);
    }
}
