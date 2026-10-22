package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

/** In-world regression coverage for starting industry with auxiliary ores. */
@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class EarlyMaterialsGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static final Set<String> STARTER_INPUTS = Set.of(
        "copper_plate", "iron_plate", "boron", "titan", "paper", "wheat", "clay", "clay_ball",
        "ceramic", "wear", "boroglass", "iron", "copper", "glass", "redstone", "leather", "string", "wool",
        "iron_pickaxe", "plank", "stick", "fix", "insulation", "binder", "substrate", "sheath");

    private static JsonObject workbench(String output) {
        return CatalogueContent.RECIPES.values().stream()
            .filter(r -> r.get("station").getAsString().equals("workbench") && r.has("craft"))
            .filter(CatalogueContent::isRecipeEnabled)
            .filter(r -> CatalogueContent.key(r.getAsJsonArray("outputs").get(0)).equals(output))
            .findFirst().orElseThrow(() -> new IllegalStateException("Missing starter recipe for " + output));
    }

    // Keep the same canonical cells/expanded-input construction as CatalogueGameTests.
    private static List<ItemStack> grid(JsonObject definition) {
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        var craft = definition.getAsJsonObject("craft");
        if (craft.get("mode").getAsString().startsWith("定形")) {
            var cells = craft.getAsJsonArray("cells");
            for (int i = 0; i < 9; i++) if (!cells.get(i).isJsonNull())
                items.set(i, CatalogueContent.stack(CatalogueContent.key(cells.get(i)), 1));
        } else {
            int slot = 0;
            for (var entry : definition.getAsJsonArray("inputs"))
                for (int n = 0; n < CatalogueContent.qty(entry); n++)
                    items.set(slot++, CatalogueContent.stack(CatalogueContent.key(entry), 1));
        }
        return items;
    }

    private static CraftingRecipe loaded(GameTestHelper h, JsonObject definition, CraftingInput input) {
        String id = definition.get("id").getAsString();
        var matches = h.getLevel().getRecipeManager().getRecipesFor(RecipeType.CRAFTING, input, h.getLevel());
        h.assertTrue(matches.size() == 1, "Expected one loaded recipe for " + id + "; actual="
            + matches.stream().map(r -> r.id().toString()).toList());
        var recipe = matches.getFirst().value();
        var expected = definition.getAsJsonArray("outputs").get(0);
        var out = recipe.assemble(input, h.getLevel().registryAccess());
        h.assertTrue(CatalogueContent.matches(CatalogueContent.key(expected), out)
            && out.getCount() == CatalogueContent.qty(expected), "Loaded output budget: " + id);
        h.assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
            "No duplicate material/container remainders: " + id);
        return recipe;
    }

    private static int outputCount(IndustryBlockEntity machine, String key) {
        return machine.stock.subList(9, 13).stream().filter(s -> CatalogueContent.matches(key, s))
            .mapToInt(ItemStack::getCount).sum();
    }

    private static void process(IndustryBlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) IndustryProcessing.tick(machine);
    }

    @GameTest(template = "empty")
    public static void ironToolsAndRealFurnacesStartBothAuxiliaryMaterials(GameTestHelper h) {
        var enchantments = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var iron = new ItemStack(Items.IRON_PICKAXE);
        var fortune = iron.copy(); fortune.enchant(enchantments.getHolderOrThrow(Enchantments.FORTUNE), 3);
        var silk = iron.copy(); silk.enchant(enchantments.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        var stone = new ItemStack(Items.STONE_PICKAXE);
        stone.enchant(enchantments.getHolderOrThrow(Enchantments.FORTUNE), 3);
        for (String ore : List.of("rutile", "borate")) {
            var state = CatalogueContent.ORES.get(ore).get().defaultBlockState();
            h.setBlock(POS, state);
            var pos = h.absolutePos(POS);
            h.assertTrue(iron.isCorrectToolForDrops(h.getBlockState(POS)) && !stone.isCorrectToolForDrops(state),
                "Actual auxiliary ore tags require iron: " + ore);
            var drops = Block.getDrops(state, h.getLevel(), pos, null, null, iron);
            h.assertTrue(drops.size() == 1 && CatalogueContent.matches(ore, drops.getFirst())
                && drops.getFirst().getCount() == 1, "Iron pick yields one actual raw ore: " + ore);
            h.assertTrue(Block.getDrops(state, h.getLevel(), pos, null, null, stone).isEmpty()
                && Block.getDrops(state, h.getLevel(), pos, null, null, ItemStack.EMPTY).isEmpty(),
                "Fortune and bare hands cannot bypass tool tier: " + ore);
            var silkDrops = Block.getDrops(state, h.getLevel(), pos, null, null, silk);
            h.assertTrue(silkDrops.size() == 1 && ItemStack.matches(drops.getFirst(), silkDrops.getFirst()),
                "Silk Touch retains raw-ore identity and origin: " + ore);
            boolean increased = false;
            for (int i = 0; i < 32; i++) {
                var sample = pos.offset(i % 8, 0, i / 8);
                var extra = Block.getDrops(state, h.getLevel(), sample, null, null, fortune);
                h.assertTrue(extra.size() == 1 && CatalogueContent.matches(ore, extra.getFirst())
                    && extra.getFirst().getCount() >= 1 && extra.getFirst().getCount() <= 4,
                    "Iron Fortune III keeps its real ore/count budget: " + ore);
                increased |= extra.getFirst().getCount() > 1;
                h.assertTrue(ItemStack.matches(extra.getFirst(),
                    Block.getDrops(state, h.getLevel(), sample, null, null, fortune).getFirst()),
                    "Repeated loot evaluation cannot reroll Fortune: " + ore);
            }
            h.assertTrue(increased, "Fortune still increases auxiliary ore yield: " + ore);

            // Resolve by the actual harvested input; generated smelting IDs are deliberately irrelevant.
            var input = new SingleRecipeInput(drops.getFirst());
            var holder = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, h.getLevel());
            h.assertTrue(holder.isPresent(), "Real smelting recipe must load for " + ore);
            String product = ore.equals("rutile") ? "titan" : "boron";
            var expected = holder.orElseThrow().value().assemble(input, h.getLevel().registryAccess());
            h.assertTrue(CatalogueContent.matches(product, expected) && expected.getCount() == 2,
                "Ordinary furnace is the two-item entry route: " + ore);
            h.setBlock(POS, Blocks.AIR); h.setBlock(POS, Blocks.FURNACE);
            var furnace = (AbstractFurnaceBlockEntity) h.getBlockEntity(POS);
            furnace.setItem(0, drops.getFirst().copy()); furnace.setItem(1, new ItemStack(Items.COAL));
            for (int tick = 0; tick < holder.orElseThrow().value().getCookingTime(); tick++)
                AbstractFurnaceBlockEntity.serverTick(h.getLevel(), furnace.getBlockPos(), furnace.getBlockState(), furnace);
            h.assertTrue(furnace.getItem(0).isEmpty() && ItemStack.matches(expected, furnace.getItem(2)),
                "Actual vanilla furnace consumes one ore and produces two: " + ore);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void preparationUpgradesYieldWithExactWaterTimeAndEnergy(GameTestHelper h) {
        for (String id : List.of("AUX-R01", "AUX-R02")) {
            boolean wet = id.equals("AUX-R01");
            String ore = wet ? "rutile" : "borate", product = wet ? "titan" : "boron";
            var definition = CatalogueContent.recipe(id);
            h.assertTrue(definition.get("station").getAsString().equals("prep")
                && definition.get("seconds").getAsDouble() * 20 == 200
                && definition.get("energy").getAsLong() == 40000, "Canonical 200-tick/40k preparation budget: " + id);
            h.setBlock(POS, Blocks.AIR); h.setBlock(POS, StarfallBlueprint.PREPARATION.get());
            var machine = (PreparationBlockEntity) h.getBlockEntity(POS);
            machine.materialMode = true; machine.template = id; machine.setPower(80000);
            machine.stock.set(0, CatalogueContent.stack(ore, 1));
            machine.tanks[0].fill(new FluidStack(Fluids.WATER, 249), FluidAction.EXECUTE);
            if (wet) {
                process(machine, 1);
                h.assertTrue(machine.idle() && machine.stock.get(0).getCount() == 1
                    && machine.tanks[0].getFluidAmount() == 249 && machine.getPower() == 80000,
                    "249mB cannot reserve or charge the wet recipe");
                machine.tanks[0].fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE);
            }
            process(machine, 1);
            h.assertTrue(machine.activeRecipe.equals(id) && machine.processTicks == 200
                && machine.stock.get(0).isEmpty() && machine.tanks[0].getFluidAmount() == (wet ? 0 : 249),
                "Reserve one ore and exactly 250 wet/zero dry water: " + id);
            process(machine, 198);
            h.assertTrue(!machine.idle() && outputCount(machine, product) == 0,
                "No products before tick 200: " + id);
            process(machine, 1);
            h.assertTrue(machine.idle() && machine.getPower() == 40000 && outputCount(machine, product) == 10,
                "Actual preparation yields ten for exactly 40k XH: " + id);
            for (var out : definition.getAsJsonArray("outputs"))
                h.assertTrue(outputCount(machine, CatalogueContent.key(out)) == CatalogueContent.qty(out),
                    "All canonical byproducts are conserved: " + id);
            var smelting = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                new SingleRecipeInput(CatalogueContent.stack(ore, 1)), h.getLevel()).orElseThrow();
            h.assertTrue(outputCount(machine, product) == 5 * smelting.value().getResultItem(h.getLevel().registryAccess()).getCount(),
                "Preparation gives five times the real furnace yield: " + id);
            process(machine, 200);
            h.assertTrue(machine.getPower() == 40000 && outputCount(machine, product) == 10
                && machine.tanks[0].getFluidAmount() == (wet ? 0 : 249), "Idle processing duplicates nothing: " + id);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void starterWorkbenchRecipesNeedOnlyAuxiliaryAndOrdinaryMaterials(GameTestHelper h) {
        for (String product : List.of("substrate", "insulation", "binder", "sheath", "finder",
                "sample_pick", "work_goggles", "rescue_hook", "sample_case")) {
            var definition = workbench(product);
            String id = definition.get("id").getAsString();
            for (var entry : definition.getAsJsonArray("inputs"))
                h.assertTrue(STARTER_INPUTS.contains(CatalogueContent.key(entry)),
                    "Starter recipe cannot require crystal, refined phase, or Nether materials: " + id + " " + entry);
            Map<Item, Integer> budget = switch (product) {
                case "substrate" -> Map.of(CatalogueContent.item("copper_plate"), 1, CatalogueContent.item("boron"), 1);
                case "insulation" -> Map.of(Items.PAPER, 1, CatalogueContent.item("boron"), 1);
                case "binder" -> Map.of(Items.WHEAT, 1, Items.CLAY_BALL, 1);
                case "sheath" -> Map.of(CatalogueContent.item("ceramic"), 1, CatalogueContent.item("titan"), 1);
                default -> Map.of();
            };
            if (!budget.isEmpty()) {
                Map<Item, Integer> actual = new HashMap<>();
                for (var entry : definition.getAsJsonArray("inputs"))
                    actual.merge(CatalogueContent.item(CatalogueContent.key(entry)), CatalogueContent.qty(entry), Integer::sum);
                h.assertTrue(actual.equals(budget) && CatalogueContent.qty(definition.getAsJsonArray("outputs").get(0)) == 2,
                    "Exact early structural whole-item inputs and two-item output: " + id + "; actual=" + actual);
            }
            var items = grid(definition);
            for (var item : items) if (!item.isEmpty())
                h.assertTrue(STARTER_INPUTS.stream().anyMatch(key -> CatalogueContent.matches(key, item))
                    && !item.is(StarfallBlueprint.CRYSTAL.get()) && !item.has(StarfallBlueprint.BATCH.get()),
                    "Canonical craft cells must also be ordinary starting materials: " + id);
            var input = CraftingInput.of(3, 3, items);
            var recipe = loaded(h, definition, input);
            if (product.equals("finder")) {
                var indicator = h.getLevel().getRecipeManager().byKey(StarfallBlueprint.id("indicator")).orElseThrow().value();
                h.assertTrue(indicator instanceof CatalogueCraftingRecipe catalogue && catalogue.id.equals("CB-04")
                    && recipe == indicator, "indicator must load the canonical CB-04 recipe");
            }
            var out = recipe.assemble(input, h.getLevel().registryAccess());
            h.assertTrue(!out.has(CatalogueContent.QUALITY.get()), "Auxiliary-only assembly has no phase quality: " + id);
            for (var item : items) if (CatalogueContent.matches("titan", item) || CatalogueContent.matches("boron", item)) {
                h.assertTrue(!item.has(CatalogueContent.QUALITY.get()), "New auxiliary items have no phase quality");
                item.set(CatalogueContent.QUALITY.get(), new MaterialQuality(1, 2, 3, 3, false, "legacy_auxiliary"));
            }
            var legacy = CraftingInput.of(3, 3, items);
            h.assertTrue(recipe.matches(legacy, h.getLevel())
                && !recipe.assemble(legacy, h.getLevel().registryAccess()).has(CatalogueContent.QUALITY.get()),
                "Legacy auxiliary quality is ignored in real starter assembly: " + id);
            if (product.equals("substrate")) {
                boolean replaced = false;
                for (int i = 0; i < items.size(); i++) if (CatalogueContent.matches("boron", items.get(i))) {
                    items.set(i, new ItemStack(Items.QUARTZ)); replaced = true;
                }
                h.assertTrue(replaced && out.getCount() == 2, "Two substrates require actual boron");
                var obsolete = CraftingInput.of(3, 3, items);
                h.assertTrue(!recipe.matches(obsolete, h.getLevel())
                    && h.getLevel().getRecipeManager().getRecipesFor(RecipeType.CRAFTING, obsolete, h.getLevel()).isEmpty(),
                    "Old Nether quartz cannot substitute for boron");
            }
        }
        h.succeed();
    }

    /** Construction/material reachability, not a throughput or power simulation. */
    private static Set<String> reachable(boolean meteorOre) {
        Set<String> available = new HashSet<>(Set.of("workbench", "furnace", "water", "rutile", "borate"));
        CatalogueContent.DEFINITIONS.forEach((key, definition) -> {
            if (definition.has("vanilla") && definition.get("vanilla").getAsBoolean()) available.add(key);
        });
        if (meteorOre) available.add("ore");
        boolean changed;
        do {
            changed = false;
            for (var recipe : CatalogueContent.RECIPES.values()) {
                if (!CatalogueContent.isRecipeEnabled(recipe)
                    || !available.contains(recipe.get("station").getAsString())) continue;
                boolean inputsReady = true;
                for (var input : recipe.getAsJsonArray("inputs"))
                    inputsReady &= available.contains(CatalogueContent.key(input));
                for (var tool : recipe.getAsJsonArray("tools"))
                    inputsReady &= available.contains(tool.getAsString());
                if (inputsReady) for (var output : recipe.getAsJsonArray("outputs"))
                    changed |= available.add(CatalogueContent.key(output));
            }
        } while (changed);
        return available;
    }

    @GameTest(template = "empty")
    public static void meteorGatesConsumeQ0AndLeaveBootstrapReachable(GameTestHelper h) {
        h.setBlock(POS, Blocks.CRAFTING_TABLE);
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "MeteorGate"));
        player.setGameMode(GameType.SURVIVAL);
        for (String id : List.of("ST-10", "SV-SP01", "SV-BK01", "EN-G01", "EN-G02", "EN-G03", "EN-G04")) {
            var definition = CatalogueContent.recipe(id);
            var items = grid(definition);
            int crystalSlot = -1;
            for (int slot = 0; slot < items.size(); slot++) if (CatalogueContent.matches("common", items.get(slot))) {
                h.assertTrue(crystalSlot == -1, "Exactly one whole crystal is the direct meteor gate: " + id);
                crystalSlot = slot;
                items.get(slot).set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(100));
            }
            h.assertTrue(crystalSlot >= 0, "Upgrade must actually consume meteor material: " + id);
            var recipe = loaded(h, definition, CraftingInput.of(3, 3, items));
            h.assertTrue(recipe instanceof CatalogueCraftingRecipe catalogue && catalogue.id.equals(id),
                "Loaded recipe must enforce canonical Q0 validation: " + id);
            player.getInventory().clearContent();
            var menu = new CraftingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(h.getLevel(), h.absolutePos(POS)));
            try {
                for (int slot = 0; slot < 9; slot++) if (slot != crystalSlot)
                    menu.getSlot(slot + 1).set(items.get(slot).copy());
                h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Missing crystal gives no real preview: " + id);
                var missing = new ArrayList<>(items);
                missing.set(crystalSlot, ItemStack.EMPTY);
                h.assertTrue(!recipe.matches(CraftingInput.of(3, 3, missing), h.getLevel()),
                    "Other materials alone cannot bypass meteor gate: " + id);
                for (int integrity : new int[]{-1, 19}) {
                    var bad = CatalogueContent.stack("common", 1);
                    if (integrity >= 0) bad.set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(integrity));
                    else bad.remove(StarfallBlueprint.BATCH.get());
                    var invalid = new ArrayList<>(items);
                    invalid.set(crystalSlot, bad);
                    h.assertTrue(!recipe.matches(CraftingInput.of(3, 3, invalid), h.getLevel()),
                        "Unverified or sub-Q0 crystal cannot match: " + id + " integrity=" + integrity);
                    menu.getSlot(crystalSlot + 1).set(bad);
                    h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Invalid Q0 gives no real preview: " + id);
                }
                menu.getSlot(crystalSlot + 1).set(items.get(crystalSlot).copy());
                var output = definition.getAsJsonArray("outputs").get(0);
                String product = CatalogueContent.key(output);
                int count = CatalogueContent.qty(output);
                h.assertTrue(CatalogueContent.matches(product, menu.getSlot(0).getItem())
                    && menu.getSlot(0).getItem().getCount() == count, "Q0 unlocks real workbench output: " + id);
                for (int slot = 0; slot < 9; slot++)
                    h.assertTrue(ItemStack.matches(items.get(slot), menu.getSlot(slot + 1).getItem()),
                        "Preview consumes no source material: " + id);
                menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
                for (int slot = 1; slot <= 9; slot++)
                    h.assertTrue(menu.getSlot(slot).getItem().isEmpty(), "Taking output consumes every input exactly once: " + id);
                h.assertTrue(player.getInventory().items.stream().filter(s -> CatalogueContent.matches(product, s))
                    .mapToInt(ItemStack::getCount).sum() == count && menu.getSlot(0).getItem().isEmpty(),
                    "One actual output batch, no repeated output or returned crystal: " + id);
            } finally {
                menu.removed(player);
                player.getInventory().clearContent();
            }
        }
        var beforeMeteor = reachable(false);
        var afterMeteor = reachable(true);
        h.assertTrue(!beforeMeteor.contains("ore") && !beforeMeteor.contains("common"),
            "Ordinary recipes cannot create meteor resources from auxiliary ores");
        for (String key : List.of("prep", "alloy_furnace", "machine_frame", "finder", "sample_pick",
                "work_goggles", "rescue_hook", "sample_case", "wear"))
            h.assertTrue(beforeMeteor.contains(key), "Bootstrap and exploration have no reverse meteor dependency: " + key);
        for (String key : List.of("board", "speed_1", "bulk_1", "g01", "g02", "g03", "g04",
                "autocrafter", "auto_furnace", "item_extractor", "s02"))
            h.assertTrue(!beforeMeteor.contains(key) && afterMeteor.contains(key),
                "Direct/indirect upgrade needs meteor, then becomes reachable without a crafting cycle: " + key);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyPhaseCarriersContributeConservativeAssemblyQuality(GameTestHelper h) {
        var definition = workbench("busjoint");
        var items = grid(definition);
        var first = new MaterialQuality(97, 38, 75, 1, true, "conductor");
        var second = new MaterialQuality(91, 66, 24, 2, false, "conductor");
        int carriers = 0;
        for (var item : items) if (CatalogueContent.matches("conductor", item))
            item.set(CatalogueContent.QUALITY.get(), carriers++ == 0 ? first : second);
        h.assertTrue(carriers == 2, "Bus joint exercises two distinct real phase carriers");
        var input = CraftingInput.of(3, 3, items);
        var recipe = loaded(h, definition, input);
        h.assertTrue(recipe instanceof CatalogueCraftingRecipe, "Quality is inherited by the loaded catalogue recipe");
        var expected = new MaterialQuality(91, 38, 24, 2, false, "conductor");
        h.assertTrue(expected.equals(recipe.assemble(input, h.getLevel().registryAccess()).get(CatalogueContent.QUALITY.get())),
            "Real multi-carrier assembly uses min P/I/O, max C and continuous AND");

        // Exercise assemble's component contract independently of recipe matching, using the loaded recipe.
        // These diagnostic inputs are not claimed to be an alternative bus-joint crafting recipe.
        var batchOnly = CatalogueContent.stack("common", 1);
        batchOnly.set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(29));
        var refined = CatalogueContent.stack("refined", 1);
        refined.set(CatalogueContent.QUALITY.get(), new MaterialQuality(99, 80, 60, 1, true, "common"));
        var conductor = CatalogueContent.stack("conductor", 1);
        conductor.set(CatalogueContent.QUALITY.get(), new MaterialQuality(95, 70, 40, 2, true, "conductor"));
        var mixed = CraftingInput.of(3, 1, List.of(batchOnly, refined, conductor));
        h.assertTrue(new MaterialQuality(90, 29, 0, 2, false, "common").equals(
            recipe.assemble(mixed, h.getLevel().registryAccess()).get(CatalogueContent.QUALITY.get())),
            "BATCH fallback and distinct refined/conductor carriers all constrain inherited quality");
        for (String carrier : List.of("common", "refined", "conductor")) {
            var phase = CatalogueContent.stack(carrier, 1);
            phase.set(CatalogueContent.QUALITY.get(), first);
            if (carrier.equals("common")) phase.set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(100));
            var titan = CatalogueContent.stack("titan", 1);
            var boron = CatalogueContent.stack("boron", 1);
            for (boolean legacy : List.of(false, true)) {
                if (legacy) {
                    titan.set(CatalogueContent.QUALITY.get(), new MaterialQuality(100, 1, 0, 3, false, "auxiliary"));
                    boron.set(CatalogueContent.QUALITY.get(), new MaterialQuality(1, 100, 100, 0, true, "auxiliary"));
                }
                for (var diagnostic : List.of(List.of(titan, phase, boron), List.of(boron, titan, phase))) {
                    var out = recipe.assemble(CraftingInput.of(3, 1, diagnostic), h.getLevel().registryAccess());
                    h.assertTrue(first.equals(out.get(CatalogueContent.QUALITY.get())),
                        "Auxiliary items cannot dilute or overwrite " + carrier + " quality, legacy=" + legacy);
                }
            }
        }
        h.succeed();
    }
}
