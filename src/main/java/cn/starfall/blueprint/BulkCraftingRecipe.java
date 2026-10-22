package cn.starfall.blueprint;

import java.util.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Data-pack recipe for counted inventory crafting; quantities are independent of item stack limits and grid size. */
public record BulkCraftingRecipe(List<Requirement> inputs, ItemStack result)
        implements Recipe<BulkCraftingRecipe.Input>, AutomaticCraftingRecipe {
    public record Input(List<ItemStack> items) implements RecipeInput {
        public ItemStack getItem(int slot) { return items.get(slot); }
        public int size() { return items.size(); }
    }
    public record Requirement(Ingredient ingredient, int count) {
        static final Codec<Requirement> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(Requirement::ingredient),
                Codec.intRange(1, 3456).fieldOf("count").forGetter(Requirement::count)
        ).apply(i, Requirement::new));
    }
    public BulkCraftingRecipe {
        inputs = List.copyOf(inputs); result = result.copy();
        if (inputs.isEmpty() || inputs.size() > 54 || inputs.stream().anyMatch(r -> r.count < 1 || r.count > 3456)
                || inputs.stream().mapToLong(Requirement::count).sum() > 3456 || result.isEmpty())
            throw new IllegalArgumentException("Bulk crafting needs 1..54 ingredients and at most 3456 total items");
    }
    public Optional<Plan> planAutomaticCraft(List<ItemStack> inventory, Level level) {
        return AutomaticCrafting.allocate(inventory, inputs.stream().map(Requirement::ingredient).toList(),
                inputs.stream().map(Requirement::count).toList()).map(used -> {
            List<ItemStack> outputs = new ArrayList<>(); outputs.add(result.copy());
            for (int i = 0; i < used.size(); i++) if (used.get(i) > 0) {
                ItemStack unit = inventory.get(i).copyWithCount(1);
                ItemStack remainder = unit.hasCraftingRemainingItem() ? unit.getCraftingRemainingItem() : ItemStack.EMPTY;
                if (!remainder.isEmpty()) {
                    // Keep each unit's remainder; packing into legal stacks is performed before reservation.
                    for (int n = 0; n < used.get(i); n++) outputs.add(remainder.copy());
                }
            }
            return new Plan(used, outputs);
        });
    }
    public boolean matches(Input input, Level level) { return planAutomaticCraft(input.items, level).isPresent(); }
    public ItemStack assemble(Input input, HolderLookup.Provider provider) { return result.copy(); }
    public boolean canCraftInDimensions(int width, int height) { return false; }
    public ItemStack getResultItem(HolderLookup.Provider provider) { return result.copy(); }
    public RecipeSerializer<?> getSerializer() { return StarfallBlueprint.BULK_CRAFTING_RECIPE.get(); }
    public RecipeType<?> getType() { return StarfallBlueprint.BULK_CRAFTING_TYPE.get(); }
    public static final class Serializer implements RecipeSerializer<BulkCraftingRecipe> {
        private static final MapCodec<BulkCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Requirement.CODEC.listOf(1, 54).fieldOf("inputs").forGetter(BulkCraftingRecipe::inputs),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(BulkCraftingRecipe::result)
        ).apply(i, BulkCraftingRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, BulkCraftingRecipe> STREAM = StreamCodec.of((b, r) -> {
            b.writeVarInt(r.inputs.size());
            for (Requirement input : r.inputs) { Ingredient.CONTENTS_STREAM_CODEC.encode(b, input.ingredient); b.writeVarInt(input.count); }
            ItemStack.STREAM_CODEC.encode(b, r.result);
        }, b -> {
            int size = b.readVarInt();
            if (size < 1 || size > 54) throw new IllegalArgumentException("Invalid bulk crafting ingredient count");
            List<Requirement> inputs = new ArrayList<>();
            for (int i = 0; i < size; i++) inputs.add(new Requirement(Ingredient.CONTENTS_STREAM_CODEC.decode(b), b.readVarInt()));
            return new BulkCraftingRecipe(inputs, ItemStack.STREAM_CODEC.decode(b));
        });
        public MapCodec<BulkCraftingRecipe> codec() { return CODEC; }
        public StreamCodec<RegistryFriendlyByteBuf, BulkCraftingRecipe> streamCodec() { return STREAM; }
    }
}
