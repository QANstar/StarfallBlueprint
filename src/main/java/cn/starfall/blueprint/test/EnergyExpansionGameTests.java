package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** All fixtures fit the existing empty 5x5x5 template; fluid checks run synchronously. */
@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class EnergyExpansionGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static final List<Item> FUELS = List.of(Items.COAL, Items.CHARCOAL, Items.OAK_LOG,
        Items.OAK_PLANKS, Items.STICK, Items.BLAZE_ROD, Items.COAL_BLOCK, Items.LAVA_BUCKET);

    private static IndustryBlockEntity machine(GameTestHelper h, BlockPos pos, String key) {
        h.setBlock(pos, Blocks.AIR);
        h.setBlock(pos, key.equals("prep") ? StarfallBlueprint.PREPARATION.get() : CatalogueContent.BLOCKS.get(key).get());
        return (IndustryBlockEntity) h.getBlockEntity(pos);
    }

    private static ServerPlayer player(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "EnergyExpansion"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    private static void tick(IndustryBlockEntity be) {
        IndustryBlockEntity.tick(be.getLevel(), be.getBlockPos(), be.getBlockState(), be);
    }

    private static void reload(GameTestHelper h, IndustryBlockEntity be) {
        var tag = be.saveWithoutMetadata(h.getLevel().registryAccess());
        be.loadWithComponents(tag, h.getLevel().registryAccess());
    }

    private static int outputs(IndustryBlockEntity be, Item item) {
        return be.stock.subList(9, 13).stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static int inventory(ServerPlayer player, Item item) {
        return player.getInventory().items.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static BlockState lavaFlow() {
        return Blocks.LAVA.defaultBlockState().setValue(BlockStateProperties.LEVEL, 1);
    }

    @GameTest(template = "empty")
    public static void vanillaBurnTimesAndLongResidualHeatSurviveNbt(GameTestHelper h) {
        Set<Long> distinctBudgets = new HashSet<>();
        for (Item item : FUELS) {
            var be = machine(h, POS, "g01");
            var fuel = new ItemStack(item);
            long expected = (long) fuel.getBurnTime(RecipeType.SMELTING) * 200;
            h.assertTrue(expected > 0, "Real NeoForge smelting fuel must burn: " + item);
            if (item == Items.COAL) h.assertTrue(expected == 320_000, "Coal remains exactly 320k XH");
            distinctBudgets.add(expected);
            var remainder = fuel.getCraftingRemainingItem();
            be.stock.set(13, fuel.copy());
            IndustryEnergy.tick(be);
            long generated = be.getPower();
            h.assertTrue(be.stock.get(13).isEmpty() && generated == Math.min(500L, expected)
                && (long) be.fuelHeat + generated == expected, "Reserve real burn-time energy exactly once: " + item);
            int residual = be.fuelHeat;
            if (item == Items.COAL_BLOCK || item == Items.LAVA_BUCKET)
                h.assertTrue(residual > 320_000, "Long-burning fuel exceeds one coal after its first tick: " + item);
            reload(h, be);
            h.assertTrue(be.fuelHeat == residual && be.getPower() == generated, "NBT preserves all residual heat: " + item);
            // Drain the buffer between ticks so fuel totals cannot be hidden by the 320k storage limit.
            be.setPower(0);
            for (long i = 0, bound = (expected + 499) / 500; i < bound && be.fuelHeat > 0; i++) {
                IndustryEnergy.tick(be);
                generated += be.getPower();
                be.setPower(0);
            }
            h.assertTrue(generated == expected && be.fuelHeat == 0 && be.stock.get(13).isEmpty(),
                "Complete fuel yield equals getBurnTime(SMELTING)*200: " + item + " actual=" + generated);
            for (int i = 0; i < 3; i++) IndustryEnergy.tick(be);
            h.assertTrue(be.getPower() == 0 && be.fuelHeat == 0, "Exhausted fuel cannot generate again: " + item);
            int remainderCount = be.stock.subList(9, 13).stream().mapToInt(ItemStack::getCount).sum();
            h.assertTrue(remainderCount == remainder.getCount()
                && (remainder.isEmpty() || outputs(be, remainder.getItem()) == remainder.getCount()),
                "Return exactly the real crafting remainder once: " + item);
        }
        h.assertTrue(distinctBudgets.size() >= 5, "Different real fuel burn times must produce different totals");
        var be = machine(h, POS, "g01");
        var tag = be.saveWithoutMetadata(h.getLevel().registryAccess());
        tag.putInt("FuelHeat", Integer.MAX_VALUE);
        be.loadWithComponents(tag, h.getLevel().registryAccess());
        h.assertTrue(be.fuelHeat == Integer.MAX_VALUE, "NBT must retain positive residual heat up to int max");
        IndustryEnergy.tick(be);
        reload(h, be);
        h.assertTrue(be.fuelHeat == Integer.MAX_VALUE - 500 && be.getPower() == 500,
            "Int-max residual survives burning and another NBT round trip without overflow");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lavaBucketReturnsThroughMenuAndBottomCapabilityWithoutBackpressureLoss(GameTestHelper h) {
        var player = player(h);
        try {
            var be = machine(h, POS, "g01");
            be.stock.set(13, new ItemStack(Items.LAVA_BUCKET));
            IndustryEnergy.tick(be);
            h.assertTrue(outputs(be, Items.BUCKET) == 1, "Lava fuel returns one real bucket to slots 9..12");
            var menu = new IndustryMenu(1, player.getInventory(), be, be.processData);
            try {
                int slot = -1;
                for (int i = 9; i <= 12; i++) if (menu.getSlot(i).getItem().is(Items.BUCKET)) slot = i;
                h.assertTrue(slot >= 9 && menu.getSlot(slot).isActive() && menu.getSlot(slot).mayPickup(player),
                    "Actual generator menu exposes a takeable bucket output");
                menu.clicked(slot, 0, ClickType.QUICK_MOVE, player);
                menu.clicked(slot, 0, ClickType.QUICK_MOVE, player);
                h.assertTrue(inventory(player, Items.BUCKET) == 1 && outputs(be, Items.BUCKET) == 0,
                    "Actual menu shift-click takes one bucket and cannot duplicate it");
            } finally { menu.removed(player); }

            be = machine(h, POS, "g01");
            be.stock.set(13, new ItemStack(Items.LAVA_BUCKET));
            IndustryEnergy.tick(be);
            var bottom = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(POS), Direction.DOWN);
            h.assertTrue(bottom != null && bottom.getSlots() == 4, "Real registered bottom capability exposes all four outputs");
            int extracted = 0;
            for (int i = 0; i < bottom.getSlots(); i++) {
                int beforeSimulation = outputs(be, Items.BUCKET);
                var simulated = bottom.extractItem(i, 64, true);
                h.assertTrue(outputs(be, Items.BUCKET) == beforeSimulation, "Simulation never removes the returned bucket");
                var actual = bottom.extractItem(i, 64, false);
                h.assertTrue(ItemStack.matches(simulated, actual), "Execute matches bottom capability simulation");
                if (!actual.isEmpty()) {
                    h.assertTrue(actual.is(Items.BUCKET), "Only the actual bucket remainder is exported");
                    extracted += actual.getCount();
                }
                h.assertTrue(bottom.extractItem(i, 64, false).isEmpty(), "Repeated extraction cannot duplicate the bucket");
            }
            IndustryEnergy.tick(be);
            h.assertTrue(extracted == 1 && outputs(be, Items.BUCKET) == 0, "Residual burning never returns a second bucket");

            be = machine(h, POS, "g01");
            for (int i = 9; i <= 12; i++) be.stock.set(i, new ItemStack(Items.COBBLESTONE, 64));
            be.stock.set(13, new ItemStack(Items.LAVA_BUCKET));
            for (int i = 0; i < 3; i++) IndustryEnergy.tick(be);
            h.assertTrue(be.stock.get(13).is(Items.LAVA_BUCKET) && be.getPower() == 0 && be.fuelHeat == 0
                && be.heatCredit == 0 && outputs(be, Items.COBBLESTONE) == 256,
                "Blocked remainder output never consumes fuel or credits heat/energy");
            be.stock.set(9, ItemStack.EMPTY);
            be.setPower(be.capacity());
            IndustryEnergy.tick(be);
            h.assertTrue(be.stock.get(13).is(Items.LAVA_BUCKET) && be.fuelHeat == 0 && outputs(be, Items.BUCKET) == 0,
                "Full energy buffer never consumes fresh fuel or creates a remainder");
            be.setPower(0);
            IndustryEnergy.tick(be);
            h.assertTrue(be.stock.get(13).isEmpty() && outputs(be, Items.BUCKET) == 1, "Freed capacity resumes fuel reservation once");
            int residual = be.fuelHeat;
            be.setPower(be.capacity());
            IndustryEnergy.tick(be);
            h.assertTrue(be.fuelHeat == residual && be.getPower() == be.capacity(), "Full buffer also preserves already reserved heat");
        } finally { player.getInventory().clearContent(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualFuelSlotsAndShiftClickKeepLegacyCoalRestrictions(GameTestHelper h) {
        var player = player(h);
        try {
            var be = machine(h, POS, "g01");
            var menu = new IndustryMenu(1, player.getInventory(), be, be.processData);
            try {
                h.assertTrue(new ItemStack(Items.STONE).getBurnTime(RecipeType.SMELTING) == 0
                    && !menu.getSlot(13).mayPlace(new ItemStack(Items.STONE)), "Actual fuel slot rejects non-burning stone");
                for (Item fuel : FUELS) {
                    h.assertTrue(menu.getSlot(13).mayPlace(new ItemStack(fuel)), "Actual g01 slot accepts API fuel: " + fuel);
                    player.getInventory().setItem(9, new ItemStack(fuel));
                    menu.clicked(27, 0, ClickType.QUICK_MOVE, player);
                    h.assertTrue(be.stock.get(13).is(fuel) && be.stock.get(13).getCount() == 1
                        && player.getInventory().getItem(9).isEmpty(), "Real shift-click routes API fuel to slot 13: " + fuel);
                    be.stock.set(13, ItemStack.EMPTY);
                }
                var top = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(POS), Direction.UP);
                h.assertTrue(top != null && top.getSlots() == 1
                    && top.insertItem(0, new ItemStack(Items.STONE), false).is(Items.STONE), "Actual top capability rejects stone");
                h.assertTrue(top.insertItem(0, new ItemStack(Items.OAK_PLANKS), true).isEmpty() && be.stock.get(13).isEmpty(),
                    "Fuel insertion simulation does not mutate storage");
                h.assertTrue(top.insertItem(0, new ItemStack(Items.OAK_PLANKS), false).isEmpty()
                    && be.stock.get(13).is(Items.OAK_PLANKS), "Actual capability inserts wood fuel");
            } finally { menu.removed(player); }
            for (String kind : List.of("prep", "auto_furnace")) {
                be = machine(h, POS, kind);
                var legacy = be.createMenu(2, player.getInventory(), player);
                int fuelSlot = 13;
                int inventorySlot = 27;
                try {
                    for (Item fuel : List.of(Items.COAL, Items.CHARCOAL)) {
                        h.assertTrue(legacy.getSlot(fuelSlot).mayPlace(new ItemStack(fuel)), "Legacy slot accepts coal/charcoal: " + kind);
                        player.getInventory().setItem(9, new ItemStack(fuel));
                        legacy.clicked(inventorySlot, 0, ClickType.QUICK_MOVE, player);
                        h.assertTrue(legacy.getSlot(fuelSlot).getItem().is(fuel) && player.getInventory().getItem(9).isEmpty(),
                            "Legacy fuel shift-click remains usable: " + kind);
                        legacy.getSlot(fuelSlot).set(ItemStack.EMPTY);
                    }
                    for (Item rejected : List.of(Items.STONE, Items.OAK_PLANKS, Items.STICK, Items.LAVA_BUCKET))
                        h.assertTrue(!legacy.getSlot(fuelSlot).mayPlace(new ItemStack(rejected)), "Legacy fuel scope stays coal/charcoal: " + kind + " " + rejected);
                } finally { legacy.removed(player); }
            }
        } finally { player.getInventory().clearContent(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void geothermalUsesOnlyUnconsumedHeatDirectlyBelowAndHonoursStops(GameTestHelper h) {
        var be = machine(h, POS, "g07");
        List<BlockState> sources = List.of(
            Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true),
            Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true),
            Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.LAVA.defaultBlockState(),
            Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false),
            Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false),
            lavaFlow(), Blocks.WATER.defaultBlockState(), Blocks.AIR.defaultBlockState());
        int[] yields = {40, 60, 80, 160, 0, 0, 0, 0, 0};
        for (int i = 0; i < sources.size(); i++) {
            h.setBlock(POS.below(), sources.get(i));
            be.setPower(0);
            tick(be);
            tick(be);
            h.assertTrue(be.getPower() == 2L * yields[i] && h.getBlockState(POS.below()).equals(sources.get(i)),
                "Only exact heat below generates without consuming it: " + sources.get(i));
        }
        h.setBlock(POS.east(), Blocks.MAGMA_BLOCK);
        h.setBlock(POS.above(), Blocks.MAGMA_BLOCK);
        tick(be);
        h.assertTrue(be.getPower() == 0, "Adjacent and overhead heat do not count");
        h.setBlock(POS.east(), Blocks.AIR);
        h.setBlock(POS.above(), Blocks.AIR);
        h.setBlock(POS.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        tick(be);
        h.setBlock(POS.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false));
        tick(be);
        h.assertTrue(be.getPower() == 40, "Extinguishing a working campfire stops new generation immediately");
        h.setBlock(POS.below(), Blocks.LAVA);
        tick(be);
        h.setBlock(POS.below(), lavaFlow());
        tick(be);
        h.setBlock(POS.below(), Blocks.AIR);
        tick(be);
        h.assertTrue(be.getPower() == 200, "Source becoming flowing or disappearing immediately stops heat");
        h.setBlock(POS.below(), Blocks.MAGMA_BLOCK);
        be.stopped = true;
        tick(be);
        h.assertTrue(be.getPower() == 200 && be.status == 8, "Outer block-entity tick honours manual stop");
        be.stopped = false;
        h.setBlock(POS.east(), Blocks.REDSTONE_BLOCK);
        h.assertTrue(be.redstoneStopped(), "Real adjacent redstone block powers the generator");
        tick(be);
        h.assertTrue(be.getPower() == 200 && be.status == 8, "Outer tick honours redstone stop");
        h.setBlock(POS.east(), Blocks.AIR);
        tick(be);
        h.assertTrue(be.getPower() == 280 && h.getBlockState(POS.below()).is(Blocks.MAGMA_BLOCK), "Removing the stop resumes unconsumed heat");
        h.succeed();
    }

    private static void sharedLavaFixture(GameTestHelper h) {
        // Distinct downward flows trace through distinct upper flows to one central source.
        for (int x : new int[]{1, 3}) {
            h.setBlock(new BlockPos(x, 2, 2), Blocks.LAVA.defaultBlockState().setValue(BlockStateProperties.LEVEL, 8));
            h.setBlock(new BlockPos(x, 3, 2), lavaFlow());
        }
        h.setBlock(new BlockPos(2, 3, 2), Blocks.LAVA);
        // Keep water one block lower: face-adjacent water would solidify the lava fixture.
        h.setBlock(new BlockPos(2, 1, 3), Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 8));
        h.setBlock(new BlockPos(2, 2, 3), Blocks.WATER);
    }

    @GameTest(template = "empty")
    public static void moltenFlowTracesSharedSourcesPerTickAndDoesNotClaimWater(GameTestHelper h) {
        long time = h.getLevel().getGameTime();
        var left = machine(h, new BlockPos(1, 2, 1), "g06");
        var right = machine(h, new BlockPos(3, 2, 1), "g06");
        var wheel = machine(h, new BlockPos(2, 1, 4), "g03");
        try {
            // Source lava, flowing water and source water are all invalid molten-flow inputs.
            for (BlockState rejected : List.of(Blocks.LAVA.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                    Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 1))) {
                h.setBlock(new BlockPos(1, 2, 2), rejected);
                IndustryEnergy.tick(left);
                h.assertTrue(left.getPower() == 0, "Molten generator requires non-source FLOWING_LAVA: " + rejected);
            }
            h.setBlock(new BlockPos(1, 2, 2), Blocks.AIR);
            // NORTH is visited before the valid SOUTH flow below: a failed trace must not hide another neighbour.
            h.setBlock(new BlockPos(1, 2, 0), lavaFlow());
            IndustryEnergy.tick(left);
            h.assertTrue(left.getPower() == 0, "An isolated flowing-lava block without a real source generates and claims nothing");
            for (int round = 0; round < 2; round++) {
                h.getLevel().getServer().getWorldData().overworldData().setGameTime(time + round);
                sharedLavaFixture(h);
                h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(1, 2, 2))).is(Fluids.FLOWING_LAVA)
                    && !h.getLevel().getFluidState(h.absolutePos(new BlockPos(1, 2, 2))).isSource(), "Fixture contains real non-source flowing lava");
                long lavaBefore = left.getPower() + right.getPower(), waterBefore = wheel.getPower();
                var first = round == 0 ? left : right;
                var second = round == 0 ? right : left;
                IndustryEnergy.tick(first);
                IndustryEnergy.tick(second);
                IndustryEnergy.tick(first);
                IndustryEnergy.tick(second);
                IndustryEnergy.tick(wheel);
                IndustryEnergy.tick(wheel);
                h.assertTrue(left.getPower() + right.getPower() == lavaBefore + 200,
                    "Distinct flows from the same lava source share exactly one 200/t claim, round=" + round);
                h.assertTrue(first.getPower() == 200 && second.getPower() == (round == 0 ? 0 : 200),
                    "The next tick releases the claim for the other generator, round=" + round);
                h.assertTrue(wheel.getPower() == waterBefore + 100, "Water wheel keeps its independent once-per-tick water-source claim");
                for (int x : new int[]{1, 3}) {
                    h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(x, 2, 2))).is(Fluids.FLOWING_LAVA),
                        "Molten flow is not consumed");
                }
                h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(2, 3, 2))).isSource(), "Shared lava source is not consumed");
            }
            // Still in the second tick: new source identities must each get their own claim.
            h.setBlock(new BlockPos(2, 3, 2), Blocks.AIR);
            h.setBlock(new BlockPos(1, 3, 2), Blocks.LAVA);
            h.setBlock(new BlockPos(3, 3, 2), Blocks.LAVA);
            IndustryEnergy.tick(left);
            IndustryEnergy.tick(right);
            IndustryEnergy.tick(left);
            IndustryEnergy.tick(right);
            h.assertTrue(left.getPower() == 400 && right.getPower() == 400,
                "Independent source positions each generate 200 once even in a tick that already claimed another source");
            h.assertTrue(wheel.getPower() == 200, "Changing lava claims does not alter water-wheel energy");
        } finally { h.getLevel().getServer().getWorldData().overworldData().setGameTime(time); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void newGeneratorCapabilitiesShareSimulationBudgetsAndPersistStoppedEnergy(GameTestHelper h) {
        long time = h.getLevel().getGameTime();
        try {
            int machineIndex = 0;
            for (String kind : List.of("g06", "g07")) {
                BlockPos pos = machineIndex++ == 0 ? POS : new BlockPos(3, 2, 3);
                var be = machine(h, pos, kind);
                int limit = kind.equals("g06") ? 200 : 160;
                be.setPower(5000);
                var north = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(pos), Direction.NORTH);
                var east = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(pos), Direction.EAST);
                h.assertTrue(north != null && east != null && !north.canReceive() && north.canExtract(), "Real new-generator energy capabilities are output-only: " + kind);
                h.assertTrue(north.receiveEnergy(1000, true) == 0 && east.receiveEnergy(1000, false) == 0 && be.getPower() == 5000,
                    "Neither simulated nor executed input can charge a generator: " + kind);
                h.assertTrue(north.extractEnergy(1000, true) == limit && east.extractEnergy(1000, true) == limit && be.getPower() == 5000,
                    "Repeated simulation neither consumes energy nor the shared tick budget: " + kind);
                h.assertTrue(north.extractEnergy(70, false) == 70 && east.extractEnergy(1000, true) == limit - 70
                    && east.extractEnergy(1000, false) == limit - 70 && north.extractEnergy(1, false) == 0
                    && be.getPower() == 5000 - limit, "All faces and capability objects share one extraction budget: " + kind);
                reload(h, be);
                h.assertTrue(be.generator() && be.getPower() == 5000 - limit, "New-generator identity and stored power survive NBT: " + kind);
                h.getLevel().getServer().getWorldData().overworldData().setGameTime(time + machineIndex);
                h.assertTrue(north.extractEnergy(1000, true) == limit && north.extractEnergy(1000, false) == limit
                    && be.getPower() == 5000 - 2L * limit, "Next tick renews exactly one output budget: " + kind);
                if (kind.equals("g06")) {
                    h.setBlock(pos.east(), lavaFlow());
                    h.setBlock(pos.east().above(), Blocks.LAVA);
                } else h.setBlock(pos.below(), Blocks.LAVA);
                be.stopped = true;
                reload(h, be);
                h.assertTrue(!be.stopped,"Legacy manual stop is cleared during interface migration: "+kind);
                h.setBlock(pos.west(), Blocks.REDSTONE_BLOCK);
                long stored = be.getPower();
                tick(be);
                h.assertTrue(be.redstoneStopped() && be.status == 8 && be.getPower() == stored
                    && !east.canExtract() && east.extractEnergy(1000, false) == 0, "Redstone stop blocks generation and extraction after migration: " + kind);
                be.stopped = false;
                h.setBlock(pos.west(), Blocks.REDSTONE_BLOCK);
                tick(be);
                h.assertTrue(be.redstoneStopped() && be.status == 8 && be.getPower() == stored
                    && east.extractEnergy(1000, true) == 0 && !east.canReceive(), "Real redstone stop blocks both outer generation tick and ports: " + kind);
                h.setBlock(pos.west(), Blocks.AIR);
                tick(be);
                h.assertTrue(be.getPower() == stored + limit, "Removing stop resumes actual environmental generation: " + kind);
            }
        } finally { h.getLevel().getServer().getWorldData().overworldData().setGameTime(time); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canonicalGeneratorRecipesRequireOneQ0AndConsumeActualCraftingInputs(GameTestHelper h) {
        h.setBlock(POS, Blocks.CRAFTING_TABLE);
        var player = player(h);
        try {
            for (String id : List.of("EN-G06", "EN-G07")) {
                var definition = CatalogueContent.recipe(id);
                var recipe = h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .map(holder -> holder.value()).filter(value -> value instanceof CatalogueCraftingRecipe catalogue && catalogue.id.equals(id))
                    .findFirst().orElseThrow(() -> new AssertionError("Canonical recipe is not actually loaded: " + id));
                List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
                var craft = definition.getAsJsonObject("craft");
                if (craft.get("mode").getAsString().startsWith("定形")) {
                    var cells = craft.getAsJsonArray("cells");
                    for (int i = 0; i < 9; i++) if (!cells.get(i).isJsonNull()) grid.set(i, CatalogueContent.stack(CatalogueContent.key(cells.get(i)), 1));
                } else {
                    int i = 0;
                    for (var input : definition.getAsJsonArray("inputs"))
                        for (int n = 0; n < CatalogueContent.qty(input); n++) grid.set(i++, CatalogueContent.stack(CatalogueContent.key(input), 1));
                }
                int crystalSlot = -1;
                for (int i = 0; i < 9; i++) if (CatalogueContent.matches("common", grid.get(i))) {
                    h.assertTrue(crystalSlot == -1, "Canonical recipe directly consumes exactly one common crystal: " + id);
                    crystalSlot = i;
                    grid.get(i).set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(100));
                }
                h.assertTrue(crystalSlot >= 0 && recipe.matches(CraftingInput.of(3, 3, grid), h.getLevel()), "Loaded recipe accepts its canonical Q0 inputs: " + id);
                var menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(POS)));
                try {
                    for (int i = 0; i < 9; i++) if (i != crystalSlot) menu.getSlot(i + 1).set(grid.get(i).copy());
                    var missing = new ArrayList<>(grid);
                    missing.set(crystalSlot, ItemStack.EMPTY);
                    h.assertTrue(!recipe.matches(CraftingInput.of(3, 3, missing), h.getLevel()) && menu.getSlot(0).getItem().isEmpty(),
                        "Missing common fails actual matches and real menu preview: " + id);
                    for (int integrity : new int[]{-1, 19}) {
                        var bad = CatalogueContent.stack("common", 1);
                        if (integrity >= 0) bad.set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(integrity));
                        else bad.remove(StarfallBlueprint.BATCH.get());
                        var invalid = new ArrayList<>(grid);
                        invalid.set(crystalSlot, bad);
                        menu.getSlot(crystalSlot + 1).set(bad.copy());
                        h.assertTrue(!recipe.matches(CraftingInput.of(3, 3, invalid), h.getLevel()) && menu.getSlot(0).getItem().isEmpty(),
                            "Missing batch or bad integrity cannot bypass Q0: " + id + " integrity=" + integrity);
                    }
                    menu.getSlot(crystalSlot + 1).set(grid.get(crystalSlot).copy());
                    var output = definition.getAsJsonArray("outputs").get(0);
                    String product = CatalogueContent.key(output);
                    int quantity = CatalogueContent.qty(output);
                    h.assertTrue(product.equals(id.equals("EN-G06") ? "g06" : "g07") && quantity == 1
                        && CatalogueContent.matches(product, menu.getSlot(0).getItem()), "Canonical real menu preview gives exactly the new generator: " + id);
                    for (int i = 0; i < 9; i++) h.assertTrue(ItemStack.matches(grid.get(i), menu.getSlot(i + 1).getItem()), "Preview never consumes ingredients: " + id);
                    menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
                    menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
                    for (int i = 1; i <= 9; i++) h.assertTrue(menu.getSlot(i).getItem().isEmpty(), "Actual crafting consumes every whole input once: " + id);
                    h.assertTrue(inventory(player, CatalogueContent.item(product)) == quantity && menu.getSlot(0).getItem().isEmpty(),
                        "Actual output take consumes Q0 once and cannot duplicate a generator: " + id);
                } finally {
                    menu.removed(player);
                    player.getInventory().clearContent();
                }
            }
        } finally { player.getInventory().clearContent(); }
        h.succeed();
    }
}
