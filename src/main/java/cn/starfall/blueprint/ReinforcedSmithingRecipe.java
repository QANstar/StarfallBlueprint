package cn.starfall.blueprint;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

/** The vanilla netherite upgrade, with reinforcement recalculated for its new material. */
public final class ReinforcedSmithingRecipe extends SmithingTransformRecipe {
    public ReinforcedSmithingRecipe() {
        super(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), Ingredient.of(Items.DIAMOND_PICKAXE),
                Ingredient.of(Items.NETHERITE_INGOT), new ItemStack(Items.NETHERITE_PICKAXE));
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider provider) {
        ItemStack result = super.assemble(input, provider);
        ItemStack base = input.base();
        if (base.getOrDefault(StarfallBlueprint.REINFORCED.get(), false)) {
            int maximum = new ItemStack(Items.NETHERITE_PICKAXE).getMaxDamage() * 6 / 5;
            result.set(DataComponents.MAX_DAMAGE, maximum);
            result.setDamageValue((int) (((long) base.getDamageValue() * maximum + base.getMaxDamage() - 1)
                    / base.getMaxDamage()));
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return StarfallBlueprint.REINFORCED_SMITHING_RECIPE.get();
    }

    public static final class Serializer implements RecipeSerializer<ReinforcedSmithingRecipe> {
        private static final MapCodec<ReinforcedSmithingRecipe> CODEC = MapCodec.unit(ReinforcedSmithingRecipe::new);
        private static final StreamCodec<RegistryFriendlyByteBuf, ReinforcedSmithingRecipe> STREAM_CODEC =
                StreamCodec.unit(new ReinforcedSmithingRecipe());

        @Override
        public MapCodec<ReinforcedSmithingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ReinforcedSmithingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
