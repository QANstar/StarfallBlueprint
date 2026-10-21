package cn.starfall.blueprint;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Whole items in two material slots; fuel is a separate inventory and never a free ingredient. */
public record AlloyRecipe(Ingredient base, int baseCount, Ingredient additive, int additiveCount,
                          ItemStack result, int duration) implements Recipe<AlloyRecipe.Input> {
    public record Input(ItemStack base, ItemStack additive) implements RecipeInput {
        public ItemStack getItem(int slot) { return switch(slot) { case 0 -> base; case 1 -> additive; default -> throw new IndexOutOfBoundsException(slot); }; }
        public int size() { return 2; }
    }
    public boolean matches(Input input, Level level) {
        return base.test(input.base) && input.base.getCount() >= baseCount
            && additive.test(input.additive) && input.additive.getCount() >= additiveCount
            && StructuralMaterialItem.mass(input.base) == 100 && StructuralMaterialItem.mass(input.additive) == 100;
    }
    public ItemStack assemble(Input input, HolderLookup.Provider provider) { return result.copy(); }
    public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    public ItemStack getResultItem(HolderLookup.Provider provider) { return result; }
    public RecipeSerializer<?> getSerializer() { return StarfallBlueprint.ALLOY_RECIPE.get(); }
    public RecipeType<?> getType() { return StarfallBlueprint.ALLOY_TYPE.get(); }
    public static final class Serializer implements RecipeSerializer<AlloyRecipe> {
        private static final MapCodec<AlloyRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("base").forGetter(AlloyRecipe::base),
            Codec.intRange(1, 64).fieldOf("base_count").forGetter(AlloyRecipe::baseCount),
            Ingredient.CODEC_NONEMPTY.fieldOf("additive").forGetter(AlloyRecipe::additive),
            Codec.intRange(1, 64).fieldOf("additive_count").forGetter(AlloyRecipe::additiveCount),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(AlloyRecipe::result),
            Codec.intRange(1, 12000).fieldOf("duration").forGetter(AlloyRecipe::duration)
        ).apply(i, AlloyRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, AlloyRecipe> STREAM = StreamCodec.of((b, r) -> {
            Ingredient.CONTENTS_STREAM_CODEC.encode(b, r.base); b.writeVarInt(r.baseCount);
            Ingredient.CONTENTS_STREAM_CODEC.encode(b, r.additive); b.writeVarInt(r.additiveCount);
            ItemStack.STREAM_CODEC.encode(b, r.result); b.writeVarInt(r.duration);
        }, b -> new AlloyRecipe(Ingredient.CONTENTS_STREAM_CODEC.decode(b), b.readVarInt(),
            Ingredient.CONTENTS_STREAM_CODEC.decode(b), b.readVarInt(), ItemStack.STREAM_CODEC.decode(b), b.readVarInt()));
        public MapCodec<AlloyRecipe> codec() { return CODEC; }
        public StreamCodec<RegistryFriendlyByteBuf, AlloyRecipe> streamCodec() { return STREAM; }
    }
}
