package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class ResonanceGameTests {
    private static final String STONE = "resonance_base_stone";
    private static final BlockPos TABLE = new BlockPos(1, 1, 1);

    private static List<Map.Entry<String, JsonObject>> bases() {
        return CatalogueContent.DEFINITIONS.entrySet().stream()
                .filter(e -> e.getValue().has("resonance_source")).toList();
    }

    private static Block base(String key) {
        return ((BlockItem) CatalogueContent.ITEMS.get(key).get()).getBlock();
    }

    private static Block source(JsonObject definition) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(definition.get("resonance_source").getAsString()));
    }

    private static Item product(JsonObject definition) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(definition.get("resonance_output").getAsString()));
    }

    private static JsonObject definition(String key) {
        return CatalogueContent.DEFINITIONS.get(key);
    }

    private static boolean mineralSource(JsonObject definition) {
        var id = ResourceLocation.parse(definition.get("resonance_source").getAsString());
        return id.getNamespace().equals(StarfallBlueprint.ID) || id.getPath().endsWith("_ore")
                || source(definition) == Blocks.ANCIENT_DEBRIS;
    }

    private static String otherBase() {
        return "resonance_base_iron_ore";
    }

    private static IndustryBlockEntity machine(GameTestHelper h, int index, String key) {
        var level = h.getLevel();
        var root = h.absolutePos(new BlockPos(800 + index * 8, 70, 800));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            level.getChunk((root.getX() >> 4) + x, (root.getZ() >> 4) + z);
        var state = CatalogueContent.BLOCKS.get("quarry").get().defaultBlockState()
                .setValue(IndustryBlock.FACING, Direction.NORTH);
        for (int part = 0; part < 4; part++) {
            var pos = root.offset(LargeMachineBlock.offset(Direction.NORTH, part));
            level.setBlock(pos.below(), base(key).defaultBlockState(), 3);
            level.setBlock(pos, state.setValue(LargeMachineBlock.PART, part), 3);
            if (part < 2) level.removeBlock(pos.north(), false);
        }
        var be = (IndustryBlockEntity) level.getBlockEntity(root);
        String source = definition(key).get("resonance_source").getAsString();
        if (!Set.of("minecraft:stone", "minecraft:iron_ore", "minecraft:deepslate_iron_ore", "minecraft:copper_ore", "minecraft:deepslate_copper_ore").contains(source))
            be.stock.set(18, CatalogueContent.stack("resonance_upgrade", 1));
        return be;
    }

    private static void foundations(IndustryBlockEntity be, String key) {
        for (int part = 0; part < 4; part++) be.getLevel().setBlock(floor(be, part), base(key).defaultBlockState(), 3);
    }

    private static BlockPos floor(IndustryBlockEntity be, int part) {
        return be.getBlockPos().offset(LargeMachineBlock.offset(be.facing(), part)).below();
    }

    private static void process(IndustryBlockEntity be, int ticks) {
        for (int i = 0; i < ticks; i++) IndustryProcessing.tick(be);
    }

    private static int total(IndustryBlockEntity be) {
        return be.stock.subList(9, 13).stream().mapToInt(ItemStack::getCount).sum();
    }

    private static int output(IndustryBlockEntity be, Item item) {
        return be.stock.subList(9, 13).stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static List<ItemStack> pending(IndustryBlockEntity be) {
        return be.pending.stream().map(ItemStack::copy).toList();
    }

    private static void unchangedPending(GameTestHelper h, IndustryBlockEntity be, List<ItemStack> expected, String message) {
        for (int i = 0; i < expected.size(); i++) h.assertTrue(ItemStack.matches(expected.get(i), be.pending.get(i)), message);
        h.assertTrue(be.processTicks == 200 && be.processCost == 4000, message + ": duration and cost stay locked");
    }

    private static void retained(GameTestHelper h, IndustryBlockEntity be, String key) {
        for (int part = 0; part < 4; part++) h.assertTrue(be.getLevel().getBlockState(floor(be, part)).is(base(key)),
                "Every foundation is retained: " + key + " part=" + part);
        for (int i = 0; i < 9; i++) h.assertTrue(be.stock.get(i).isEmpty(), "No hidden consumable quarry input: " + key);
    }

    private static void reload(GameTestHelper h, IndustryBlockEntity be) {
        var tag = be.saveWithFullMetadata(h.getLevel().registryAccess());
        be.loadWithComponents(tag, h.getLevel().registryAccess());
    }

    private static ServerPlayer player(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ResonanceTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(Vec3.atCenterOf(h.absolutePos(TABLE)).add(0, 1, 2));
        player.getInventory().clearContent();
        return player;
    }

    @GameTest(template = "empty")
    public static void canonicalBasesAndQuarryRecipesHaveCompleteRegistryMetadata(GameTestHelper h) {
        h.assertTrue(bases().size() == 34 && definition(STONE) != null, "Exactly 34 active definitions include stone; four legacy registrations are excluded");
        h.assertTrue(CatalogueContent.DEFINITIONS.get("quarry").get("name").getAsString().equals("地脉共振仪"),
                "Quarry is displayed as 地脉共振仪");
        Set<String> recipeBases = new HashSet<>();
        for (var recipe : CatalogueContent.RECIPES.values()) {
            if (!recipe.get("station").getAsString().equals("quarry")) continue;
            String id = recipe.get("id").getAsString();
            h.assertTrue(id.matches("QM-R[0-9]+") && recipe.has("resonance_base"), "Every quarry recipe declares its base: " + id);
            String key = recipe.get("resonance_base").getAsString();
            h.assertTrue(definition(key) != null && recipeBases.add(key), "Exactly one recipe for each registered base: " + key);
            h.assertTrue(id.equals("QM-R01") == key.equals(STONE), "QM-R01 remains the stone route");
            var metadata = definition(key);
            var outputs = recipe.getAsJsonArray("outputs");
            h.assertTrue(outputs.size() == 1 && CatalogueContent.item(CatalogueContent.key(outputs.get(0))) == product(metadata)
                    && CatalogueContent.qty(outputs.get(0)) == metadata.get("resonance_count").getAsInt(),
                    "Recipe output agrees with base registry metadata: " + id);
            h.assertTrue(recipe.get("energy").getAsInt() == 4000 && recipe.get("seconds").getAsDouble() == 10
                    && recipe.getAsJsonArray("inputs").isEmpty(), "200 ticks and 4000 XH with no consumed base: " + id);
        }
        h.assertTrue(recipeBases.equals(bases().stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet())),
                "No omitted or extra resonance route");
        boolean ancientDebris = false;
        for (var entry : bases()) {
            String key = entry.getKey();
            var metadata = entry.getValue();
            h.assertTrue(CatalogueContent.ITEMS.get(key).get() instanceof BlockItem, "Every base is a real placeable BlockItem: " + key);
            for (String field : List.of("resonance_source", "resonance_output"))
                h.assertTrue(metadata.has(field) && metadata.get(field).getAsString().contains(":"), "Full registry ID: " + key + "/" + field);
            h.assertTrue(source(metadata) != Blocks.AIR && product(metadata) != Items.AIR, "Metadata resolves to real registry entries: " + key);
            int expected = mineralSource(metadata) || source(metadata) == Blocks.GILDED_BLACKSTONE ? 1 : 4;
            h.assertTrue(metadata.get("resonance_count").getAsInt() == expected,
                    "Canonical stone/shard/dust yield four and ore/nugget yields one: " + key);
            h.assertTrue(metadata.get("resonance_source").getAsString().startsWith("minecraft:")
                    && metadata.get("resonance_output").getAsString().startsWith("minecraft:"), "Only vanilla sources and outputs: " + key);
            ancientDebris |= source(metadata) == Blocks.ANCIENT_DEBRIS;
            h.assertTrue(!metadata.get("resonance_source").getAsString().contains("moon")
                    && !CatalogueContent.RESERVED_ITEMS.contains(CatalogueContent.key(new ItemStack(product(metadata)))),
                    "Future moon and reserved material have no resonance base: " + key);
        }
        h.assertTrue(ancientDebris, "Ancient debris has a canonical resonance base");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyCanonicalBaseActuallyCompletesExactlyTwoHundredTicks(GameTestHelper h) {
        int index = 0;
        for (var entry : bases()) {
            String key = entry.getKey();
            var metadata = entry.getValue();
            var be = machine(h, index++, key);
            be.setPower(8000);
            var witness = be.getBlockPos().below(3);
            h.getLevel().setBlock(witness, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
            process(be, 1);
            h.assertTrue(!be.idle() && be.progressTicks == 1 && be.processTicks == 200 && be.processCost == 4000
                    && be.processPaid == 20 && be.getPower() == 7980 && total(be) == 0, "Reserve one fixed batch on its first tick: " + key);
            var locked = pending(be);
            h.assertTrue(locked.stream().filter(s -> !s.isEmpty()).count() == 1
                    && locked.stream().filter(s -> !s.isEmpty()).allMatch(s -> s.is(product(metadata))
                    && s.getCount() == metadata.get("resonance_count").getAsInt()), "Pending locks exact category and quantity: " + key);
            process(be, 72);
            reload(h, be);
            unchangedPending(h, be, locked, "Every base preserves pending components through a 73-tick NBT reload: " + key);
            h.assertTrue(be.progressTicks == 73 && be.processPaid == 1460 && be.getPower() == 6540,
                    "All base types retain exactly 73 paid ticks after reload: " + key);
            process(be, 126);
            unchangedPending(h, be, locked, "199 ticks keep the original pending: " + key);
            h.assertTrue(be.progressTicks == 199 && be.processPaid == 3980 && be.getPower() == 4020 && total(be) == 0,
                    "No early output and exact per-tick energy: " + key);
            process(be, 1);
            int expected = metadata.get("resonance_count").getAsInt();
            h.assertTrue(be.idle() && total(be) == expected && output(be, product(metadata)) == expected && be.getPower() == 4000,
                    "Full 200 tick batch pays exactly 4000 XH and produces only its category: " + key);
            var expectedStack = locked.stream().filter(s -> !s.isEmpty()).findFirst().orElseThrow();
            h.assertTrue(be.stock.subList(9, 13).stream().filter(s -> !s.isEmpty()).allMatch(s -> ItemStack.matches(s, expectedStack)),
                    "Settlement retains the reserved stack's exact metadata and components: " + key);
            retained(h, be, key);
            h.assertTrue(h.getLevel().getBlockState(witness).is(Blocks.DIAMOND_ORE), "Quarry never mines through its base: " + key);
            process(be, 200);
            h.assertTrue(be.idle() && total(be) == expected * 2 && output(be, product(metadata)) == expected * 2 && be.getPower() == 0,
                    "Second complete batch reuses every base with exact energy: " + key);
            process(be, 200);
            h.assertTrue(total(be) == expected * 2 && be.getPower() == 0, "Zero energy cannot duplicate a batch: " + key);
            retained(h, be, key);
        }
        h.assertTrue(index > 0, "The complete canonical base enumeration was exercised");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fourIdenticalBasesAndBothFrontCellsAreRequired(GameTestHelper h) {
        var be = machine(h, 50, STONE);
        be.setPower(4000);
        for (int part = 0; part < 4; part++) {
            for (Block invalid : new Block[]{Blocks.AIR, Blocks.STONE, Blocks.DEEPSLATE, base(otherBase())}) {
                h.getLevel().setBlock(floor(be, part), invalid.defaultBlockState(), 3);
                process(be, 200);
                h.assertTrue(be.status == 10 && be.idle() && total(be) == 0 && be.getPower() == 4000,
                        "Missing, ordinary or mixed foundation cannot start: part=" + part + " block=" + invalid);
                foundations(be, STONE);
            }
        }
        for (int part = 0; part < 2; part++) {
            var front = be.getBlockPos().offset(LargeMachineBlock.offset(Direction.NORTH, part)).north();
            h.getLevel().setBlock(front, Blocks.GLASS.defaultBlockState(), 3);
            process(be, 200);
            h.assertTrue(be.status == 11 && be.idle() && total(be) == 0 && be.getPower() == 4000,
                    "Each front cell independently requires empty space: " + part);
            h.getLevel().removeBlock(front, false);
        }
        process(be, 200);
        h.assertTrue(output(be, Items.COBBLESTONE) == 4 && total(be) == 4 && be.getPower() == 0, "Restored structure resumes normal stone production");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void replacingAllBasesKeepsCurrentPendingAndSelectsNewKindNextBatch(GameTestHelper h) {
        var be = machine(h, 51, STONE);
        be.setPower(8000);
        process(be, 73);
        var locked = pending(be);
        String originalRecipe = be.activeRecipe;
        String replacement = otherBase();
        foundations(be, replacement);
        process(be, 126);
        unchangedPending(h, be, locked, "Changing all four bases never rewrites the current output");
        h.assertTrue(be.activeRecipe.equals(originalRecipe) && be.progressTicks == 199 && total(be) == 0 && be.getPower() == 4020,
                "Current recipe and cost remain reserved after a complete base swap");
        process(be, 1);
        h.assertTrue(be.idle() && output(be, Items.COBBLESTONE) == 4 && total(be) == 4 && be.getPower() == 4000,
                "Old stone batch settles once after 73 + 127 ticks");
        process(be, 1);
        h.assertTrue(!be.activeRecipe.equals(originalRecipe) && be.progressTicks == 1 && be.pending.stream().filter(s -> !s.isEmpty())
                .allMatch(s -> s.is(product(definition(replacement)))), "Only the next batch selects the replacement mineral");
        process(be, 199);
        h.assertTrue(be.idle() && output(be, Items.COBBLESTONE) == 4 && output(be, product(definition(replacement))) == 1
                && total(be) == 5 && be.getPower() == 0, "New base produces exactly one new mineral for its own full 200 ticks");
        retained(h, be, replacement);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void missingOrMixedBasesPowerAndOutputBackpressureSurviveNbtAtSeventyThreeTicks(GameTestHelper h) {
        var be = machine(h, 52, STONE);
        be.setPower(4000);
        process(be, 73);
        var locked = pending(be);
        String recipe = be.activeRecipe;
        h.assertTrue(be.progressTicks == 73 && be.processPaid == 1460 && be.getPower() == 2540, "Exactly 73 ticks paid before persistence");
        reload(h, be);
        unchangedPending(h, be, locked, "NBT retains the original component-bearing pending");
        h.assertTrue(be.progressTicks == 73 && be.processPaid == 1460 && be.getPower() == 2540 && be.activeRecipe.equals(recipe),
                "NBT retains progress, paid energy and selected recipe");
        for (int part = 0; part < 4; part++) {
            h.getLevel().removeBlock(floor(be, part), false);
            process(be, 40);
            h.assertTrue(be.status == 10 && be.progressTicks == 73 && be.getPower() == 2540 && total(be) == 0,
                    "Removing any one base pauses without spending energy: " + part);
            unchangedPending(h, be, locked, "Missing base preserves pending");
            foundations(be, STONE);
            h.getLevel().setBlock(floor(be, part), base(otherBase()).defaultBlockState(), 3);
            process(be, 40);
            h.assertTrue(be.status == 10 && be.progressTicks == 73 && be.getPower() == 2540 && total(be) == 0,
                    "A mixed base cannot advance or charge a saved batch: " + part);
            unchangedPending(h, be, locked, "Mixed base preserves pending");
            foundations(be, STONE);
        }
        be.setPower(19);
        process(be, 200);
        h.assertTrue(be.status == 4 && be.progressTicks == 73 && be.processPaid == 1460 && be.getPower() == 19 && total(be) == 0,
                "Less than one tick's energy cannot be charged or advance progress");
        unchangedPending(h, be, locked, "Energy shortage preserves pending");
        be.setPower(2540);
        for (int slot = 9; slot < 13; slot++) be.stock.set(slot, new ItemStack(Items.DIRT, 64));
        process(be, 200);
        h.assertTrue(be.status == 5 && be.progressTicks == 73 && be.processPaid == 1460 && be.getPower() == 2540 && total(be) == 256,
                "Output backpressure pauses before charging energy");
        reload(h, be);
        unchangedPending(h, be, locked, "A blocked save/load preserves pending exactly once");
        for (int slot = 9; slot < 13; slot++) be.stock.set(slot, ItemStack.EMPTY);
        foundations(be, otherBase());
        process(be, 126);
        h.assertTrue(be.progressTicks == 199 && be.getPower() == 20 && total(be) == 0, "Any restored homogeneous base allows original pending to resume");
        unchangedPending(h, be, locked, "Resuming on another homogeneous base keeps old stone output");
        process(be, 1);
        h.assertTrue(be.idle() && be.getPower() == 0 && total(be) == 4 && output(be, Items.COBBLESTONE) == 4,
                "Only the remaining 127 ticks settle the original batch");
        reload(h, be);
        process(be, 400);
        h.assertTrue(total(be) == 4 && output(be, Items.COBBLESTONE) == 4 && be.getPower() == 0
                && be.pending.stream().allMatch(ItemStack::isEmpty), "Reload and extra ticks cannot settle the paid pending twice");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void restoringOriginalBaseAndClearingFrontResumesTheSavedBatch(GameTestHelper h) {
        var be = machine(h, 53, STONE);
        be.setPower(4000);
        process(be, 73);
        var locked = pending(be);
        h.getLevel().removeBlock(floor(be, 0), false);
        process(be, 200);
        reload(h, be);
        h.assertTrue(be.progressTicks == 73 && be.getPower() == 2540 && total(be) == 0,
                "Saving while a foundation is absent cannot advance or discard the batch");
        foundations(be, STONE);
        for (int part = 0; part < 2; part++) {
            var front = be.getBlockPos().offset(LargeMachineBlock.offset(Direction.NORTH, part)).north();
            h.getLevel().setBlock(front, Blocks.STONE.defaultBlockState(), 3);
            process(be, 200);
            h.assertTrue(be.status == 11 && be.progressTicks == 73 && be.processPaid == 1460 && be.getPower() == 2540,
                    "Obstructing either front cell pauses an in-flight batch without charging");
            unchangedPending(h, be, locked, "Front obstruction preserves reserved output");
            h.getLevel().removeBlock(front, false);
        }
        process(be, 127);
        h.assertTrue(be.idle() && be.getPower() == 0 && output(be, Items.COBBLESTONE) == 4 && total(be) == 4,
                "Restoring the original base and front completes precisely the remaining 127 ticks");
        retained(h, be, STONE);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyBaseUsesExactRawCenterAndEightIdenticalSourceBlocksInRealCrafting(GameTestHelper h) {
        h.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        var player = player(h);
        Set<Block> silkSources = new HashSet<>();
        var silk = new ItemStack(Items.IRON_PICKAXE);
        silk.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        try {
            for (var entry : bases()) {
                String key = entry.getKey();
                var recipes = CatalogueContent.RECIPES.values().stream().filter(r -> r.has("craft"))
                        .filter(r -> r.getAsJsonArray("outputs").asList().stream().anyMatch(o -> CatalogueContent.key(o).equals(key))).toList();
                h.assertTrue(recipes.size() == 1, "Exactly one canonical crafting recipe per base: " + key);
                var definition = recipes.getFirst();
                String id = definition.get("id").getAsString();
                var recipe = h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream().map(RecipeHolder::value)
                        .filter(r -> r instanceof CatalogueCraftingRecipe catalogue && catalogue.id.equals(id)).findFirst().orElseThrow();
                var cells = definition.getAsJsonObject("craft").getAsJsonArray("cells");
                h.assertTrue(definition.getAsJsonObject("craft").get("mode").getAsString().startsWith("定形") && cells.size() == 9,
                        "Resonance recipe is an exact full shaped grid: " + key);
                Item source = source(entry.getValue()).asItem();
                h.assertTrue(source != Items.AIR, "Crafting source has its own placeable block item: " + key);
                List<ItemStack> grid = new ArrayList<>();
                for (int slot = 0; slot < 9; slot++) {
                    h.assertTrue(!cells.get(slot).isJsonNull(), "All nine precise recipe cells are occupied: " + key);
                    Item expected = slot == 4 ? StarfallBlueprint.RAW.get() : source;
                    h.assertTrue(CatalogueContent.item(CatalogueContent.key(cells.get(slot))) == expected,
                            "RAW at center and matching source at each ring position: " + key + " slot=" + slot);
                    if (slot == 4) {
                        var raw = Block.getDrops(StarfallBlueprint.ORE.get().defaultBlockState(), h.getLevel(), h.absolutePos(TABLE),
                                null, null, new ItemStack(Items.IRON_PICKAXE));
                        h.assertTrue(raw.size() == 1 && raw.getFirst().is(StarfallBlueprint.RAW.get()) && raw.getFirst().getCount() == 1,
                                "Crafting center comes from a real ordinary raw-ore harvest");
                        grid.add(raw.getFirst().copy());
                    } else if (BuiltInRegistries.BLOCK.getKey(source(entry.getValue())).getNamespace().equals(StarfallBlueprint.ID)) {
                        var drops = Block.getDrops(source(entry.getValue()).defaultBlockState(), h.getLevel(), h.absolutePos(TABLE).offset(slot, 0, 0),
                                null, null, silk);
                        h.assertTrue(drops.size() == 1 && drops.getFirst().is(expected) && drops.getFirst().getCount() == 1
                                && drops.getFirst().getItem() instanceof BlockItem,
                                "Each custom source block is actually obtainable by correct-tool Silk Touch: " + key);
                        grid.add(drops.getFirst().copy());
                        silkSources.add(source(entry.getValue()));
                    } else grid.add(new ItemStack(expected));
                }
                h.assertTrue(recipe.matches(CraftingInput.of(3, 3, grid), h.getLevel()), "Loaded recipe accepts its exact grid: " + key);
                var menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(TABLE)));
                try {
                    for (int slot = 0; slot < 9; slot++) menu.getSlot(slot + 1).set(grid.get(slot).copy());
                    h.assertTrue(menu.getSlot(0).getItem().is(base(key).asItem()) && menu.getSlot(0).getItem().getCount() == 1,
                            "Real crafting menu previews exactly one base: " + key);
                    for (int slot = 0; slot < 9; slot++) {
                        var invalid = new ArrayList<>(grid);
                        invalid.set(slot, ItemStack.EMPTY);
                        menu.getSlot(slot + 1).set(ItemStack.EMPTY);
                        h.assertTrue(!recipe.matches(CraftingInput.of(3, 3, invalid), h.getLevel()) && !menu.getSlot(0).getItem().is(base(key).asItem()),
                                "Every required position rejects missing material: " + key + " slot=" + slot);
                        Item wrong = slot == 4 ? source : (source == Items.STONE ? Items.COBBLESTONE : Items.STONE);
                        invalid.set(slot, new ItemStack(wrong));
                        menu.getSlot(slot + 1).set(new ItemStack(wrong));
                        h.assertTrue(!recipe.matches(CraftingInput.of(3, 3, invalid), h.getLevel()) && !menu.getSlot(0).getItem().is(base(key).asItem()),
                                "Wrong center or mixed ring cannot craft: " + key + " slot=" + slot);
                        menu.getSlot(slot + 1).set(grid.get(slot).copy());
                    }
                    var swapped = new ArrayList<>(grid);
                    Collections.swap(swapped, 0, 4);
                    h.assertTrue(!recipe.matches(CraftingInput.of(3, 3, swapped), h.getLevel()), "Same multiset in wrong positions is rejected: " + key);
                    for (int slot = 0; slot < 9; slot++) h.assertTrue(ItemStack.matches(grid.get(slot), menu.getSlot(slot + 1).getItem()),
                            "Preview and invalid attempts never consume material: " + key);
                    menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
                    menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
                    for (int slot = 1; slot <= 9; slot++) h.assertTrue(menu.getSlot(slot).getItem().isEmpty(), "Actual take consumes eight sources and one RAW once: " + key);
                    int count = 0;
                    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) if (player.getInventory().getItem(slot).is(base(key).asItem())) count += player.getInventory().getItem(slot).getCount();
                    h.assertTrue(count == 1 && menu.getSlot(0).getItem().isEmpty(), "Repeated result take cannot duplicate the crafted base: " + key);
                } finally {
                    menu.removed(player);
                    player.getInventory().clearContent();
                }
            }
        } finally { player.getInventory().clearContent(); }
        for (var ore : Set.of(StarfallBlueprint.ORE.get(), StarfallBlueprint.DEEP_ORE.get(),
                CatalogueContent.ORES.get("rutile").get(), CatalogueContent.ORES.get("borate").get())) {
            var drops = Block.getDrops(ore.defaultBlockState(), h.getLevel(), h.absolutePos(TABLE), null, null, silk);
            h.assertTrue(drops.size() == 1 && drops.getFirst().is(ore.asItem()) && drops.getFirst().getCount() == 1,
                    "Retired resonance support does not change ordinary Silk Touch ore harvesting");
            silkSources.add(ore);
        }
        h.assertTrue(silkSources.size() == 4, "All four retired custom ores retain normal Silk Touch materials");
        var moon = CatalogueContent.ORES.get("moon").get();
        h.assertTrue(moon.asItem() == Items.AIR,
                "Future moon source has no survival BlockItem or reachable resonance recipe");
        var fortune = new ItemStack(Items.IRON_PICKAXE);
        fortune.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FORTUNE), 3);
        for (var tool : List.of(ItemStack.EMPTY, new ItemStack(Items.IRON_PICKAXE), silk, fortune))
            h.assertTrue(Block.getDrops(moon.defaultBlockState(), h.getLevel(), h.absolutePos(TABLE), null, null, tool).isEmpty(),
                    "Ordinary tools, Silk Touch and Fortune cannot bypass the future moon reservation");
        h.succeed();
    }
}
