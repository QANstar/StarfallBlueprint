package cn.starfall.blueprint;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Optional integration contract for recipes whose quantities or matching cannot be inferred from a crafting grid.
 * The input is a detached snapshot, ordered by the machine's input slots. Implementations must be deterministic
 * and side-effect free; a plan consumes the stated counts exactly once and returns all products/remainders.
 * No energy, fluids or player side effects may be performed here. Register implementations as normal recipes.
 */
public interface AutomaticCraftingRecipe {
    Optional<Plan> planAutomaticCraft(List<ItemStack> inventory, Level level);

    record Plan(List<Integer> consumed, List<ItemStack> outputs) {
        public Plan {
            consumed = List.copyOf(consumed);
            outputs = outputs.stream().map(ItemStack::copy).toList();
        }
    }
}
