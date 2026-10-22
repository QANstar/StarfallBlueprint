package cn.starfall.blueprint;

import java.util.List;
import net.minecraft.world.item.crafting.Ingredient;

/** A logical recipe layout, independent of the inventory layout. Empty ingredients preserve holes.
 * Component checks and dynamic results still run through the original recipe's matches/assemble methods.
 */
public interface AutomaticCraftingLayout {
    Layout automaticCraftingLayout();

    record Layout(int width, int height, List<Ingredient> ingredients) {
        public Layout {
            ingredients = List.copyOf(ingredients);
            if (width < 1 || height < 1 || width > 54 || height > 54
                    || (long) width * height > 54 || ingredients.size() != width * height)
                throw new IllegalArgumentException("Automatic crafting layout must contain 1..54 logical cells");
        }
    }
}
