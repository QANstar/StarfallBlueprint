package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Synchronous fixtures fit the existing empty 5x5x5 template. */
@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class PlantCultivationGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static IndustryBlockEntity machine(GameTestHelper h) {
        h.setBlock(POS, Blocks.AIR);
        h.setBlock(POS, CatalogueContent.BLOCKS.get("plant_cultivator").get());
        return (IndustryBlockEntity) h.getBlockEntity(POS);
    }

    private static ItemStack sample() {
        return new ItemStack(PlantCultivation.samples().iterator().next());
    }

    private static PlantCultivation.Profile profile(ItemStack sample) {
        return PlantCultivation.profile(sample).orElseThrow();
    }

    private static void install(IndustryBlockEntity be, ItemStack sample) {
        be.stock.set(0, new ItemStack(profile(sample).environments().iterator().next()));
        be.stock.set(1, sample.copy());
    }

    private static void water(GameTestHelper h, IndustryBlockEntity be, int amount) {
        h.assertTrue(be.fluidPort().fill(new FluidStack(Fluids.WATER, amount), FluidAction.EXECUTE) == amount,
            "Cultivator input accepts exactly the requested ordinary water");
    }

    private static void process(IndustryBlockEntity be, int ticks) {
        for (int i = 0; i < ticks; i++) IndustryProcessing.tick(be);
    }

    private static void outerTick(IndustryBlockEntity be) {
        IndustryBlockEntity.tick(be.getLevel(), be.getBlockPos(), be.getBlockState(), be);
    }

    private static List<ItemStack> snapshot(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    private static boolean sameStacks(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) if (!ItemStack.matches(left.get(i), right.get(i))) return false;
        return true;
    }

    // Compare a component-aware multiset: output planning may merge/reorder stacks.
    private static void assertProducts(GameTestHelper h, IndustryBlockEntity be, List<ItemStack> expected, int batches) {
        List<ItemStack> remaining = new ArrayList<>();
        for (int n = 0; n < batches; n++) for (var stack : expected) if (!stack.isEmpty()) remaining.add(stack.copy());
        for (var actual : be.stock.subList(9, 13)) {
            int count = actual.getCount();
            for (var wanted : remaining) if (!actual.isEmpty() && ItemStack.isSameItemSameComponents(actual, wanted)) {
                int take = Math.min(count, wanted.getCount());
                wanted.shrink(take); count -= take;
            }
            h.assertTrue(count == 0, "No extra or component-changed output: " + actual);
        }
        h.assertTrue(remaining.stream().allMatch(ItemStack::isEmpty), "Every pending product is settled exactly once");
    }

    private static void clearOutputs(IndustryBlockEntity be) {
        for (int i = 9; i <= 12; i++) be.stock.set(i, ItemStack.EMPTY);
    }

    private static void blockOutputs(IndustryBlockEntity be) {
        for (int i = 9; i <= 12; i++) be.stock.set(i, new ItemStack(Items.COBBLESTONE, 64));
    }

    @GameTest(template = "empty")
    public static void vanillaPlantFamiliesIncludeChorusCoralsAndNetherWart(GameTestHelper h) {
        for (var tag : List.of(net.minecraft.tags.ItemTags.SAPLINGS, net.minecraft.tags.ItemTags.FLOWERS,
                net.minecraft.tags.ItemTags.VILLAGER_PLANTABLE_SEEDS)) {
            var entries = net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(tag).orElseThrow();
            for (var holder : entries) {
                if (holder.is(net.minecraft.tags.ItemTags.LEAVES)) continue;
                h.assertTrue(PlantCultivation.isSample(new ItemStack(holder.value())),
                    "Vanilla tagged plant must have a cultivation plan: " + holder);
            }
        }
        for (Item item : List.of(Items.NETHER_WART, Items.CHORUS_FLOWER, Items.CHORUS_PLANT,
                Items.TUBE_CORAL, Items.BRAIN_CORAL, Items.BUBBLE_CORAL, Items.FIRE_CORAL, Items.HORN_CORAL,
                Items.TUBE_CORAL_FAN, Items.BRAIN_CORAL_FAN, Items.BUBBLE_CORAL_FAN, Items.FIRE_CORAL_FAN, Items.HORN_CORAL_FAN,
                Items.TUBE_CORAL_BLOCK, Items.BRAIN_CORAL_BLOCK, Items.BUBBLE_CORAL_BLOCK, Items.FIRE_CORAL_BLOCK, Items.HORN_CORAL_BLOCK)) {
            h.assertTrue(PlantCultivation.isSample(new ItemStack(item)), "Required species cannot silently disappear: " + item);
        }
        h.assertTrue(PlantCultivation.samples().size() == 88, "Pinned MC1.21.1 coverage has 88 explicit samples");
        var flower = profile(new ItemStack(Items.CHORUS_FLOWER));
        h.assertTrue(flower.environments().equals(Set.of(Items.END_STONE))
            && sameStacks(flower.outputs(), List.of(new ItemStack(Items.CHORUS_FRUIT, 2), new ItemStack(Items.CHORUS_FLOWER))),
            "Chorus needs end stone and yields both fruit and a new flower");
        var wart = profile(new ItemStack(Items.NETHER_WART));
        h.assertTrue(wart.environments().equals(Set.of(Items.SOUL_SAND))
            && sameStacks(wart.outputs(), List.of(new ItemStack(Items.NETHER_WART, 2))), "Nether wart retains soul-sand cultivation");
        for (var pair : List.of(List.of(Items.CRIMSON_FUNGUS, Items.CRIMSON_STEM), List.of(Items.WARPED_FUNGUS, Items.WARPED_STEM))) {
            var products = profile(new ItemStack(pair.get(0))).outputs();
            h.assertTrue(sameStacks(products, List.of(new ItemStack(pair.get(0), 2), new ItemStack(pair.get(1), 2))),
                "Fungus grows its matching stem without removing the original fungus yield");
        }
        for (Item item : List.of(Items.DEAD_TUBE_CORAL, Items.DEAD_BRAIN_CORAL_FAN, Items.DEAD_FIRE_CORAL_BLOCK, Items.SCULK)) {
            h.assertTrue(!PlantCultivation.isSample(new ItemStack(item)), "Nonliving coral and sculk are not living plant samples");
        }
        var be = machine(h); install(be, new ItemStack(Items.CHORUS_FLOWER));
        be.stock.set(0, new ItemStack(Items.DIRT)); water(h, be, 1000); be.setPower(8000); process(be, 1);
        h.assertTrue(be.idle() && be.getPower() == 8000 && be.tanks[0].getFluidAmount() == 1000,
            "A real chorus batch rejects ordinary soil without consuming resources");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyRegisteredSamplePreservesComponentsAndSettlesItsPendingSnapshot(GameTestHelper h) {
        h.assertTrue(!PlantCultivation.samples().isEmpty(), "At least one real cultivation profile is registered");
        for (Item item : PlantCultivation.samples()) {
            var be = machine(h);
            var specimen = new ItemStack(item);
            specimen.set(DataComponents.CUSTOM_NAME, Component.literal("Retained specimen " + item));
            var definition = profile(specimen);
            h.assertTrue(!definition.environments().isEmpty() && !definition.outputs().isEmpty(), "Profile has environment and products: " + definition.id());
            install(be, specimen);
            // Directly loaded legacy stacks still retain their complete count; GUI limits are tested separately.
            be.stock.get(0).setCount(2); be.stock.get(1).setCount(3);
            be.stock.get(0).set(DataComponents.CUSTOM_NAME, Component.literal("Retained environment"));
            var environment = be.stock.get(0).copy();
            var originalSample = be.stock.get(1).copy();
            h.assertTrue(PlantCultivation.isEnvironment(environment) && PlantCultivation.isSample(specimen), "Public classifiers accept profile fixtures");
            water(h, be, 1000); be.setPower(8000);
            process(be, 1);
            var pending = snapshot(be.pending);
            h.assertTrue(be.activeRecipe.equals("cultivate:" + definition.id()) && be.progressTicks == 1
                && be.processTicks == 400 && be.processCost == 8000 && be.processPaid == 20
                && be.getPower() == 7980 && be.tanks[0].getFluidAmount() == 750,
                "One start reserves exactly 250mB and pays only its first 20XH: " + definition.id());
            h.assertTrue(sameStacks(definition.outputs(), pending.stream().filter(s -> !s.isEmpty()).toList()),
                "Actual start locks exactly its registered profile products, components and counts");
            var detachedOutputs = definition.outputs(); detachedOutputs.get(0).grow(17);
            h.assertTrue(sameStacks(pending, be.pending)
                && sameStacks(definition.outputs(), pending.stream().filter(s -> !s.isEmpty()).toList()),
                "Mutating a public output copy cannot change the registry or pending batch");
            process(be, 398);
            h.assertTrue(be.progressTicks == 399 && be.getPower() == 20 && be.processPaid == 7980
                && be.stock.subList(9, 13).stream().allMatch(ItemStack::isEmpty)
                && sameStacks(pending, be.pending), "No early products or changing pending across 399 ticks");
            process(be, 1);
            h.assertTrue(be.idle() && be.getPower() == 0 && be.tanks[0].getFluidAmount() == 750
                && be.pending.stream().allMatch(ItemStack::isEmpty), "Exactly tick 400 settles and clears the reservation");
            assertProducts(h, be, pending, 1);
            h.assertTrue(ItemStack.matches(environment, be.stock.get(0)) && ItemStack.matches(originalSample, be.stock.get(1)),
                "Environment and sample retain all components and counts: " + definition.id());
            var completed = snapshot(be.stock);
            process(be, 20);
            h.assertTrue(be.idle() && sameStacks(completed, be.stock) && be.tanks[0].getFluidAmount() == 750,
                "No power cannot start another batch or duplicate the finished products");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidEnvironmentWaterPowerAndFourFullOutputsNeverReserveResources(GameTestHelper h) {
        for (String failure : List.of("environment", "water", "zeroPower", "partialTickPower", "outputs")) {
            var be = machine(h); install(be, sample());
            water(h, be, failure.equals("water") ? 249 : 1000);
            be.setPower(failure.equals("zeroPower") ? 0 : failure.equals("partialTickPower") ? 19 : 8000);
            if (failure.equals("environment")) be.stock.set(0, new ItemStack(Items.COBBLESTONE));
            if (failure.equals("outputs")) blockOutputs(be);
            var stock = snapshot(be.stock); var pending = snapshot(be.pending);
            int fluid = be.tanks[0].getFluidAmount(); long power = be.getPower();
            process(be, 10);
            h.assertTrue(be.idle() && be.progressTicks == 0 && be.processPaid == 0 && sameStacks(stock, be.stock)
                && sameStacks(pending, be.pending) && be.getPower() == power && be.tanks[0].getFluidAmount() == fluid,
                "Failed start changes no inventory, pending, water or energy: " + failure);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void twoAutomaticBatchesResumeAt73AfterNbtAndPauseWithoutAnotherReservation(GameTestHelper h) {
        var be = machine(h); install(be, sample()); water(h, be, 500); be.setPower(16000);
        var environment = be.stock.get(0).copy(); var specimen = be.stock.get(1).copy();
        process(be, 73);
        var pending = snapshot(be.pending); String recipe = be.activeRecipe;
        h.assertTrue(be.progressTicks == 73 && be.processPaid == 1460 && be.getPower() == 14540
            && be.tanks[0].getFluidAmount() == 250, "73 ticks reserve one water dose and pay exactly 1460XH");
        var saved = be.saveWithoutMetadata(h.getLevel().registryAccess());
        be.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(be.progressTicks == 73 && be.processPaid == 1460 && be.activeRecipe.equals(recipe)
            && sameStacks(pending, be.pending) && be.processTicks == 400 && be.processCost == 8000,
            "Actual NBT round trip retains duration, paid energy and the locked products");
        be.setPower(19); process(be, 11);
        h.assertTrue(be.progressTicks == 73 && be.getPower() == 19 && be.processPaid == 1460
            && sameStacks(pending, be.pending) && be.tanks[0].getFluidAmount() == 250,
            "Insufficient energy pauses without partial payment or a second reservation");
        be.setPower(14540); blockOutputs(be); process(be, 11);
        h.assertTrue(be.progressTicks == 73 && be.getPower() == 14540 && be.processPaid == 1460
            && sameStacks(pending, be.pending) && be.tanks[0].getFluidAmount() == 250,
            "In-flight output backpressure pays nothing and never re-reserves water");
        clearOutputs(be);
        be.stopped = true; outerTick(be);
        h.assertTrue(be.progressTicks == 73 && be.getPower() == 14540 && be.status == 8, "Actual outer ticker honours manual stop");
        be.stopped = false; h.setBlock(POS.east(), Blocks.REDSTONE_BLOCK); outerTick(be);
        h.assertTrue(be.redstoneStopped() && be.progressTicks == 73 && be.getPower() == 14540 && be.status == 8,
            "Actual outer ticker honours real redstone while preserving the batch");
        h.setBlock(POS.east(), Blocks.AIR);
        process(be, 327);
        h.assertTrue(be.idle() && be.getPower() == 8000 && be.tanks[0].getFluidAmount() == 250,
            "Exactly the remaining 327 ticks complete the reloaded batch");
        assertProducts(h, be, pending, 1);
        process(be, 1);
        h.assertTrue(be.activeRecipe.equals(recipe) && be.progressTicks == 1 && be.getPower() == 7980
            && be.tanks[0].isEmpty() && sameStacks(pending, be.pending), "Next tick starts the second automatic batch once");
        process(be, 399); assertProducts(h, be, pending, 2);
        h.assertTrue(be.idle() && be.getPower() == 0 && be.tanks[0].isEmpty()
            && ItemStack.matches(environment, be.stock.get(0)) && ItemStack.matches(specimen, be.stock.get(1)),
            "Two batches cost exactly 500mB and 16000XH while retaining the fixture");
        var completed = snapshot(be.stock); process(be, 30);
        h.assertTrue(be.idle() && sameStacks(completed, be.stock), "Exhausted machine cannot duplicate its two batches");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realMenuLimitsFixturesToOneAndBlocksPickupShiftSwapAndThrowInFlight(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "PlantCultivation"));
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
        var be = machine(h);
        var menu = new IndustryMenu(1, player.getInventory(), be, be.processData);
        try {
            Set<Integer> visible = Set.of(0, 1, 2, 9, 10, 11, 12, 16, 17, 20, 21);
            for (int i = 0; i < 27; i++) h.assertTrue(menu.getSlot(i).isActive() == visible.contains(i), "Actual visible machine slot " + i);
            var specimen = sample(); var environment = new ItemStack(profile(specimen).environments().iterator().next());
            h.assertTrue(menu.getSlot(0).getMaxStackSize() == 1 && menu.getSlot(1).getMaxStackSize() == 1
                && menu.getSlot(0).mayPlace(environment) && menu.getSlot(1).mayPlace(specimen)
                && !menu.getSlot(2).mayPlace(new ItemStack(Items.LAVA_BUCKET)), "Real slots limit fixtures and accept only water buckets");
            menu.setCarried(environment.copyWithCount(2)); menu.clicked(0, 0, ClickType.PICKUP, player);
            h.assertTrue(be.stock.get(0).getCount() == 1 && menu.getCarried().getCount() == 1, "Real pickup puts only one environment into its fixed slot");
            menu.setCarried(ItemStack.EMPTY);
            player.getInventory().setItem(9, specimen.copyWithCount(2)); menu.clicked(27, 0, ClickType.QUICK_MOVE, player);
            h.assertTrue(ItemStack.matches(specimen, be.stock.get(1)) && player.getInventory().getItem(9).getCount() == 1,
                "Real player shift-click inserts exactly one sample and retains its surplus");
            water(h, be, 250); be.setPower(8000); process(be, 1);
            var originalEnvironment = be.stock.get(0).copy(); var originalSample = be.stock.get(1).copy();
            var pending = snapshot(be.pending);
            for (int slot : new int[]{0, 1}) {
                h.assertTrue(!menu.getSlot(slot).mayPickup(player) && !menu.getSlot(slot).mayPlace(slot == 0 ? environment : specimen),
                    "Actual in-flight fixture slot refuses pickup and replacement: " + slot);
                menu.clicked(slot, 0, ClickType.PICKUP, player);
                h.assertTrue(menu.getCarried().isEmpty(), "Ordinary click cannot take the active fixture");
                menu.clicked(slot, 0, ClickType.QUICK_MOVE, player);
                player.getInventory().setItem(0, new ItemStack(Items.STONE));
                menu.clicked(slot, 0, ClickType.SWAP, player);
                h.assertTrue(player.getInventory().getItem(0).is(Items.STONE), "Hotbar swap cannot replace the active fixture");
                menu.clicked(slot, 1, ClickType.THROW, player);
                h.assertTrue(ItemStack.matches(originalEnvironment, be.stock.get(0)) && ItemStack.matches(originalSample, be.stock.get(1))
                    && sameStacks(pending, be.pending), "Pickup/shift/swap/throw preserve both fixtures and pending");
            }
            process(be, 399);
            h.assertTrue(menu.getSlot(0).mayPickup(player) && menu.getSlot(1).mayPickup(player), "Completed batch unlocks both real slots");
            for (int slot : new int[]{0, 1}) {
                menu.clicked(slot, 0, ClickType.PICKUP, player);
                h.assertTrue(!menu.getCarried().isEmpty() && be.stock.get(slot).isEmpty(), "Idle fixture is actually takeable again");
                menu.setCarried(ItemStack.EMPTY);
            }
        } finally {
            menu.setCarried(ItemStack.EMPTY); menu.removed(player); player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canonicalAgM01ConsumesEightActualWorkbenchInputsAndExactlyOneQ0(GameTestHelper h) {
        h.setBlock(POS, Blocks.CRAFTING_TABLE);
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "PlantWorkbench"));
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
        var definition = CatalogueContent.recipe("AG-M01");
        var recipe = h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
            .map(holder -> holder.value())
            .filter(value -> value instanceof CatalogueCraftingRecipe catalogue && catalogue.id.equals("AG-M01"))
            .findFirst().orElseThrow(() -> new AssertionError("Canonical AG-M01 recipe is not actually loaded"));
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        var cells = definition.getAsJsonObject("craft").getAsJsonArray("cells");
        int crystalSlot = -1, count = 0;
        for (int i = 0; i < 9; i++) if (!cells.get(i).isJsonNull()) {
            grid.set(i, CatalogueContent.stack(CatalogueContent.key(cells.get(i)), 1)); count++;
            if (CatalogueContent.matches("common", grid.get(i))) {
                h.assertTrue(crystalSlot == -1, "Canonical AG-M01 directly requires exactly one common crystal");
                crystalSlot = i;
                grid.get(i).set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(100));
            }
        }
        h.assertTrue(count == 8 && crystalSlot >= 0 && recipe.matches(CraftingInput.of(3, 3, grid), h.getLevel()),
            "Loaded AG-M01 accepts the eight canonical inputs with a valid Q0 crystal");
        var menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(POS)));
        try {
            for (int i = 0; i < 9; i++) if (i != crystalSlot) menu.getSlot(i + 1).set(grid.get(i).copy());
            var missing = new ArrayList<>(grid); missing.set(crystalSlot, ItemStack.EMPTY);
            h.assertTrue(!recipe.matches(CraftingInput.of(3, 3, missing), h.getLevel()) && menu.getSlot(0).getItem().isEmpty(),
                "Missing Q0 crystal fails both actual recipe matches and real menu preview");
            for (int integrity : new int[]{-1, 19}) {
                var invalidCrystal = CatalogueContent.stack("common", 1);
                if (integrity < 0) invalidCrystal.remove(StarfallBlueprint.BATCH.get());
                else invalidCrystal.set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(integrity));
                var invalid = new ArrayList<>(grid); invalid.set(crystalSlot, invalidCrystal);
                menu.getSlot(crystalSlot + 1).set(invalidCrystal.copy());
                h.assertTrue(!recipe.matches(CraftingInput.of(3, 3, invalid), h.getLevel()) && menu.getSlot(0).getItem().isEmpty(),
                    "Missing batch or integrity below Q0 cannot craft AG-M01: " + integrity);
            }
            menu.getSlot(crystalSlot + 1).set(grid.get(crystalSlot).copy());
            h.assertTrue(menu.getSlot(0).getItem().is(CatalogueContent.item("plant_cultivator"))
                && menu.getSlot(0).getItem().getCount() == 1, "Actual workbench previews exactly one cultivator");
            for (int i = 0; i < 9; i++) h.assertTrue(ItemStack.matches(grid.get(i), menu.getSlot(i + 1).getItem()),
                "Preview preserves every input and its Q0 component: slot " + i);
            menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
            menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
            for (int i = 1; i <= 9; i++) h.assertTrue(menu.getSlot(i).getItem().isEmpty(), "Real output take consumes every whole input once");
            h.assertTrue(player.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item("plant_cultivator")))
                .mapToInt(ItemStack::getCount).sum() == 1 && menu.getSlot(0).getItem().isEmpty(),
                "Real workbench consumes one Q0 and eight total ingredients without duplicating its output");
        } finally {
            menu.removed(player); player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredPortsAndBucketRemaindersRespectSimulationCapacityAndExtractionOnce(GameTestHelper h) {
        var be = machine(h);
        var top = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(POS), Direction.UP);
        var bottom = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(POS), Direction.DOWN);
        var fluid = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(POS), Direction.NORTH);
        h.assertTrue(top != null && top.getSlots() == 3 && bottom != null && bottom.getSlots() == 4 && fluid != null,
            "Actually registered ports expose top inputs 0..2 and bottom products 9..12");
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            var side = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(POS), direction);
            h.assertTrue(side == null || side.getSlots() == 0, "Horizontal faces expose no item slots: " + direction);
        }
        h.assertTrue(Arrays.equals(be.getSlotsForFace(Direction.UP), new int[]{0, 1, 2})
            && Arrays.equals(be.getSlotsForFace(Direction.DOWN), new int[]{9, 10, 11, 12}), "Sided slot indices retain their exact meaning");
        h.assertTrue(fluid.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.SIMULATE) == 0
            && fluid.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 0 && be.tanks[0].isEmpty(), "Real fluid capability rejects non-water without mutation");
        h.assertTrue(fluid.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.SIMULATE) == 1000 && be.tanks[0].isEmpty(), "Water fill simulation reserves nothing");
        h.assertTrue(fluid.fill(new FluidStack(Fluids.WATER, 4000), FluidAction.EXECUTE) == 4000, "Real ordinary-water fill reaches full tank");
        h.assertTrue(top.insertItem(2, new ItemStack(Items.LAVA_BUCKET), false).is(Items.LAVA_BUCKET), "Real top bucket slot rejects lava");
        h.assertTrue(top.insertItem(2, new ItemStack(Items.WATER_BUCKET), true).isEmpty() && be.stock.get(2).isEmpty(), "Bucket insertion simulation does not mutate slot");
        h.assertTrue(top.insertItem(2, new ItemStack(Items.WATER_BUCKET), false).isEmpty(), "Real top capability inserts the actual water bucket");
        process(be, 3);
        h.assertTrue(be.stock.get(2).is(Items.WATER_BUCKET) && be.tanks[0].getFluidAmount() == 4000
            && be.stock.subList(9, 13).stream().allMatch(ItemStack::isEmpty), "Full tank preserves bucket and creates no empty remainder");
        be.tanks[0].drain(1000, FluidAction.EXECUTE); blockOutputs(be); process(be, 3);
        h.assertTrue(be.stock.get(2).is(Items.WATER_BUCKET) && be.tanks[0].getFluidAmount() == 3000,
            "Four blocked outputs preserve the unconsumed bucket and water capacity");
        clearOutputs(be); process(be, 1);
        h.assertTrue(be.stock.get(2).isEmpty() && be.tanks[0].getFluidAmount() == 4000 && be.getPower() == 0,
            "One actual bucket fills exactly 1000mB without needing process energy");
        assertProducts(h, be, List.of(new ItemStack(Items.BUCKET)), 1);
        int extracted = 0;
        for (int i = 0; i < bottom.getSlots(); i++) {
            var before = snapshot(be.stock); var simulated = bottom.extractItem(i, 64, true);
            h.assertTrue(sameStacks(before, be.stock), "Bottom extraction simulation is pure");
            var actual = bottom.extractItem(i, 64, false);
            h.assertTrue(ItemStack.matches(simulated, actual) && (actual.isEmpty() || actual.is(Items.BUCKET)), "Actual extraction matches the real remainder simulation");
            extracted += actual.getCount();
            h.assertTrue(bottom.extractItem(i, 64, false).isEmpty(), "Second extraction cannot duplicate a bucket");
        }
        process(be, 3);
        h.assertTrue(extracted == 1 && be.stock.subList(9, 13).stream().allMatch(ItemStack::isEmpty)
            && be.tanks[0].getFluidAmount() == 4000, "Exactly one bucket is exported, with no repeated water or empty buckets");
        h.succeed();
    }
}
