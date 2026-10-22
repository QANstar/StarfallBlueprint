package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class AutomaticCraftingGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static IndustryBlockEntity machine(GameTestHelper h) {
        h.setBlock(POS, CatalogueContent.BLOCKS.get("autocrafter").get());
        var be = (IndustryBlockEntity) h.getBlockEntity(POS);
        be.setPower(320000);
        return be;
    }
    private static void process(IndustryBlockEntity be, int ticks) {
        for (int i = 0; i < ticks; i++) IndustryProcessing.tick(be);
    }
    private static int output(IndustryBlockEntity be, Item item) {
        return be.stock.subList(9, 13).stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static void temporaryRecipe(GameTestHelper h, Recipe<?> recipe, java.util.function.Consumer<String> action) {
        var manager = h.getLevel().getRecipeManager();
        List<RecipeHolder<?>> original = new ArrayList<>(manager.getRecipes());
        var id = ResourceLocation.fromNamespaceAndPath(StarfallBlueprint.ID, "automatic_test");
        List<RecipeHolder<?>> replacement = new ArrayList<>(original);
        replacement.add(new RecipeHolder<>(id, recipe));
        try { manager.replaceRecipes(replacement); action.accept(id.toString()); }
        finally { manager.replaceRecipes(original); }
    }

    @GameTest(template = "empty", timeoutTicks = 340)
    public static void extractorAndDuctFeedRepeatedShapedCrafts(GameTestHelper h) {
        var be = machine(h);
        be.template = "minecraft:iron_block";
        BlockPos duct = POS.above(), extractor = duct.south(), chest = extractor.south();
        h.setBlock(duct, CatalogueContent.BLOCKS.get("item_duct").get());
        h.setBlock(extractor, CatalogueContent.BLOCKS.get("item_extractor").get());
        h.setBlock(chest, Blocks.CHEST);
        ((ChestBlockEntity) h.getBlockEntity(chest)).setItem(0, new ItemStack(Items.IRON_INGOT, 27));
        // Default extractor faces north: chest -> extractor -> duct -> top of machine.
        h.runAfterDelay(300, () -> {
            h.assertTrue(output(be, Items.IRON_BLOCK) == 3, "Pipe-fed inventory must make three shaped crafts without nine-grid placement");
            h.assertTrue(be.getPower() == 308000 && be.idle(), "Three crafts charge exactly 12k XH");
            h.assertTrue(((ChestBlockEntity) h.getBlockEntity(chest)).isEmpty(), "All 27 supplied ingots must leave source exactly once");
            h.assertTrue(Arrays.stream(be.craftingInputSlots()).allMatch(i -> be.getItem(i).isEmpty()), "No spare or duplicated ingots");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void shapedRecipeUsesUnorderedInventoryAndLeavesUnrelatedItems(GameTestHelper h) {
        var be = machine(h); be.template = "minecraft:wooden_pickaxe";
        be.setItem(0, new ItemStack(Items.STICK, 2));
        be.setItem(4, new ItemStack(Items.COBBLESTONE, 7));
        be.setItem(8, new ItemStack(Items.OAK_PLANKS, 3));
        process(be, 40);
        h.assertTrue(output(be, Items.WOODEN_PICKAXE) == 1 && be.getPower() == 316000, "Shaped recipe must reconstruct virtual layout from stacked inventory");
        h.assertTrue(be.getItem(0).isEmpty() && be.getItem(8).isEmpty() && be.getItem(4).getCount() == 7, "Only assigned materials are consumed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void componentCandidatesBacktrackAndDynamicAssemblyKeepsToolData(GameTestHelper h) {
        ItemStack bad = new ItemStack(StarfallBlueprint.CRYSTAL.get());
        bad.set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(19));
        ItemStack good = new ItemStack(StarfallBlueprint.CRYSTAL.get());
        good.set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(38));
        var recipe = new BasicCrystalRecipe(CraftingBookCategory.MISC, "connector");
        var inventory = List.of(bad, good, new ItemStack(Items.COPPER_INGOT));
        var plan = AutomaticCrafting.plan(recipe, inventory, h.getLevel()).orElseThrow();
        h.assertTrue(plan.consumed().equals(List.of(0, 1, 1)), "Failed low-quality component candidate must backtrack to valid crystal");
        h.assertTrue(plan.outputs().getFirst().is(StarfallBlueprint.CONNECTOR.get()) && bad.getCount() == 1 && good.getCount() == 1, "Planning must not mutate live inputs");
        h.assertTrue(AutomaticCrafting.plan(recipe, List.of(bad, new ItemStack(Items.COPPER_INGOT)), h.getLevel()).isEmpty(), "Insufficient quality cannot be replaced by matching item ID");
        var pick = new ItemStack(Items.IRON_PICKAXE); pick.setDamageValue(100);
        pick.set(DataComponents.CUSTOM_NAME, Component.literal("Inventory pick"));
        var upgrade = new BasicCrystalRecipe(CraftingBookCategory.EQUIPMENT, "reinforce_tool");
        var enhanced = AutomaticCrafting.plan(upgrade, List.of(new ItemStack(StarfallBlueprint.TOOL_BRACE.get()), pick), h.getLevel()).orElseThrow().outputs().getFirst();
        h.assertTrue(enhanced.getMaxDamage() == 300 && enhanced.getDamageValue() == 120 && enhanced.getOrDefault(StarfallBlueprint.REINFORCED.get(), false), "Real assemble must preserve proportional damage and reinforcement");
        h.assertTrue(Component.literal("Inventory pick").equals(enhanced.get(DataComponents.CUSTOM_NAME)) && pick.getMaxDamage() == 250, "Dynamic assembly copies components and leaves source unchanged");
        h.succeed();
    }

    private static final class LargeLayoutRecipe extends CustomRecipe implements AutomaticCraftingLayout {
        LargeLayoutRecipe() { super(CraftingBookCategory.MISC); }
        public AutomaticCraftingLayout.Layout automaticCraftingLayout() {
            return new AutomaticCraftingLayout.Layout(4, 4, Collections.nCopies(16, Ingredient.of(Items.IRON_INGOT)));
        }
        public boolean matches(CraftingInput input, Level level) {
            return input.width() == 4 && input.height() == 4 && input.ingredientCount() == 16
                    && input.items().stream().allMatch(s -> s.is(Items.IRON_INGOT) && s.getCount() == 1);
        }
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) { return new ItemStack(Items.DIAMOND); }
        public boolean canCraftInDimensions(int w, int h) { return w >= 4 && h >= 4; }
        public RecipeSerializer<?> getSerializer() { return RecipeSerializer.SHAPELESS_RECIPE; }
    }

    @GameTest(template = "empty")
    public static void overlappingIngredientsAndFourByFourLayoutUseRealMatching(GameTestHelper h) {
        var ingredients = NonNullList.<Ingredient>create();
        ingredients.add(Ingredient.of(Items.IRON_INGOT, Items.COPPER_INGOT));
        ingredients.add(Ingredient.of(Items.IRON_INGOT));
        var recipe = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND), ingredients);
        var plan = AutomaticCrafting.plan(recipe, List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COPPER_INGOT)), h.getLevel()).orElseThrow();
        h.assertTrue(plan.consumed().equals(List.of(1, 1)), "Broad ingredient must not steal the only iron from narrow ingredient");
        var be = machine(h); be.setItem(8, new ItemStack(Items.IRON_INGOT, 16));
        temporaryRecipe(h, new LargeLayoutRecipe(), id -> { be.template = id; process(be, 40); });
        h.assertTrue(output(be, Items.DIAMOND) == 1 && be.getItem(8).isEmpty(), "Opt-in 4x4 layout must execute through machine without fake oversized logical stacks");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void countedRecipeConsumesBeyondOneStackAndNineIngredients(GameTestHelper h) {
        var be = machine(h);
        var extras = List.of(Items.COPPER_INGOT, Items.GOLD_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI,
                Items.QUARTZ, Items.COAL, Items.FLINT, Items.CLAY_BALL, Items.WHEAT, Items.STICK);
        List<BulkCraftingRecipe.Requirement> requirements = new ArrayList<>();
        requirements.add(new BulkCraftingRecipe.Requirement(Ingredient.of(Items.IRON_INGOT), 128));
        for (Item item : extras) requirements.add(new BulkCraftingRecipe.Requirement(Ingredient.of(item), 1));
        var recipe = new BulkCraftingRecipe(requirements, new ItemStack(Items.DIAMOND, 2));
        int[] slots = be.craftingInputSlots();
        h.assertTrue(slots.length == 54, "Bulk input capacity is 54 actual inventory slots");
        be.setItem(slots[0], new ItemStack(Items.IRON_INGOT, 64));
        be.setItem(slots[53], new ItemStack(Items.IRON_INGOT, 64));
        for (int i = 0; i < extras.size(); i++) be.setItem(slots[i + 1], new ItemStack(extras.get(i)));
        temporaryRecipe(h, recipe, id -> {
            be.template = id; process(be, 13);
            h.assertTrue(!be.idle() && Arrays.stream(slots).allMatch(i -> be.getItem(i).isEmpty()), "128 items plus ten distinct ingredients reserve once across all pages");
            var saved = be.saveWithoutMetadata(h.getLevel().registryAccess());
            be.loadWithComponents(saved, h.getLevel().registryAccess()); process(be, 27); process(be, 80);
            h.assertTrue(output(be, Items.DIAMOND) == 2 && be.getPower() == 316000, "Counted recipe reload must finish once without needing original recipe inventory");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void containerRemaindersReserveSpaceAndSurviveReloadOnce(GameTestHelper h) {
        var be = machine(h); be.template = "minecraft:cake";
        for (int i = 0; i < 3; i++) be.setItem(i, new ItemStack(Items.MILK_BUCKET));
        be.setItem(3, new ItemStack(Items.SUGAR, 2)); be.setItem(4, new ItemStack(Items.EGG)); be.setItem(5, new ItemStack(Items.WHEAT, 3));
        // One free output can hold cake, but the three returned buckets need another.
        for (int i = 9; i < 12; i++) be.stock.set(i, new ItemStack(Items.COBBLESTONE, 64));
        process(be, 3);
        h.assertTrue(be.idle() && be.getPower() == 320000 && be.getItem(0).is(Items.MILK_BUCKET), "Remainder backpressure must reject before input or energy reservation");
        be.stock.set(11, ItemStack.EMPTY); process(be, 17);
        h.assertTrue(!be.idle() && be.getItem(0).isEmpty(), "Ingredients reserve once after all outputs fit");
        var saved = be.saveWithoutMetadata(h.getLevel().registryAccess());
        be.loadWithComponents(saved, h.getLevel().registryAccess()); process(be, 23); process(be, 80);
        h.assertTrue(output(be, Items.CAKE) == 1 && output(be, Items.BUCKET) == 3 && be.getPower() == 316000, "Dynamic remainder collection and NBT resume must not duplicate returned buckets");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hopperCanFillExtendedInputsWithoutAccessingOutputs(GameTestHelper h) {
        var be = machine(h); int[] slots = be.craftingInputSlots();
        h.assertTrue(slots.length == 54 && Arrays.stream(slots).distinct().count() == 54, "All 54 input slots must be distinct");
        for (int i = 0; i < 9; i++) be.setItem(slots[i], new ItemStack(Items.COBBLESTONE, 64));
        var left = HopperBlockEntity.addItem(null, be, new ItemStack(Items.IRON_INGOT, 64), Direction.UP);
        h.assertTrue(left.isEmpty() && be.getItem(slots[9]).is(Items.IRON_INGOT), "Vanilla hopper insertion must continue into extended inventory");
        be.setItem(slots[53], new ItemStack(Items.GOLD_INGOT, 37));
        be.stock.set(9, new ItemStack(Items.DIAMOND));
        var tag = be.saveWithoutMetadata(h.getLevel().registryAccess()); be.loadWithComponents(tag, h.getLevel().registryAccess());
        h.assertTrue(be.getItem(slots[53]).getCount() == 37 && be.getItem(slots[9]).getCount() == 64 && output(be, Items.DIAMOND) == 1, "Extended input NBT must remain separate from machine outputs");
        h.assertTrue(Arrays.stream(be.getSlotsForFace(Direction.DOWN)).allMatch(i -> i >= 9 && i <= 12)
                && !be.canTakeItemThroughFace(slots[53], be.getItem(slots[53]), Direction.DOWN), "Bottom extraction must never drain input page slots");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void countedRecipeCodecAndNetworkPreserveCountsAndComponents(GameTestHelper h) {
        var result = new ItemStack(Items.DIAMOND, 3); result.set(DataComponents.CUSTOM_NAME, Component.literal("Counted result"));
        var recipe = new BulkCraftingRecipe(List.of(
                new BulkCraftingRecipe.Requirement(Ingredient.of(Items.IRON_INGOT, Items.COPPER_INGOT), 128),
                new BulkCraftingRecipe.Requirement(Ingredient.of(Items.IRON_INGOT), 1)), result);
        var serializer = new BulkCraftingRecipe.Serializer();
        var ops = RegistryOps.create(JsonOps.INSTANCE, h.getLevel().registryAccess());
        var json = serializer.codec().codec().encodeStart(ops, recipe).getOrThrow();
        var fromJson = serializer.codec().codec().parse(ops, json).getOrThrow();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            serializer.streamCodec().encode(buffer, fromJson);
            var decoded = serializer.streamCodec().decode(buffer);
            h.assertTrue(!buffer.isReadable() && decoded.inputs().getFirst().count() == 128, "Counted ingredient must survive data and network codecs without count truncation");
            h.assertTrue(ItemStack.isSameItemSameComponents(decoded.result(), result) && decoded.result().getCount() == 3, "Result count and components survive both codecs");
            var inventory = List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COPPER_INGOT, 64), new ItemStack(Items.COPPER_INGOT, 64));
            var plan = AutomaticCrafting.plan(decoded, inventory, h.getLevel()).orElseThrow();
            h.assertTrue(plan.consumed().equals(List.of(1, 64, 64)), "Counted overlapping ingredients must reassign broad demand instead of starving narrow requirement");
        } finally { buffer.release(); }
        h.succeed();
    }
}
