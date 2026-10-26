package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class PhaseExtractorGameTests {
    private static final BlockPos POS = new BlockPos(2, 3, 2);
    private static final List<String> BASES = List.of("phase_matrix_meteor", "phase_matrix_rutile", "phase_matrix_borate");

    private static BlockPos part(int part) { return POS.offset(LargeMachineBlock.offset(Direction.NORTH, part)); }

    private static IndustryBlockEntity machine(GameTestHelper h, String base) {
        h.setBlock(POS, Blocks.AIR);
        var state = CatalogueContent.BLOCKS.get("phase_extractor").get().defaultBlockState()
                .setValue(IndustryBlock.FACING, Direction.NORTH);
        for (int p = 0; p < LargeMachineBlock.parts("phase_extractor"); p++)
            h.setBlock(part(p), state.setValue(LargeMachineBlock.PART, p));
        bases(h, CatalogueContent.PHASE_MATRICES.get(base).get());
        return (IndustryBlockEntity) h.getBlockEntity(POS);
    }

    private static void bases(GameTestHelper h, Block base) {
        for (int p = 0; p < 4; p++) h.setBlock(part(p).below(), base);
    }

    private static void ready(IndustryBlockEntity be) {
        be.fluidPort().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        be.setPower(80000);
    }

    @GameTest(template="empty")
    public static void fullUpgradeStacksPayStepsLargerThanBufferAcrossReload(GameTestHelper h){
        var be=machine(h,"phase_matrix_meteor");ready(be);
        for(int i:new int[]{16,17,20,21})be.stock.set(i,CatalogueContent.stack("speed_2",64));
        be.setPower(be.capacity());long supplied=be.getPower();process(be,1);
        h.assertTrue(!be.idle()&&be.processCost==5_200_000&&be.processTicks==4&&be.processPaid>0&&be.progressTicks==0,"A 1.3 million XH step accepts partial power without exceeding the buffer");
        var expected=be.pending.stream().filter(s->!s.isEmpty()).findFirst().orElseThrow().copy();
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());long paid=be.processPaid;be.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(be.processPaid==paid,"Partial payment survives reload");
        int guard=0;while(!be.idle()&&guard++<100){long grant=Math.min(be.capacity()-be.getPower(),5_200_000-supplied);if(grant>0){be.setPower(be.getPower()+grant);supplied+=grant;}process(be,1);}
        h.assertTrue(be.idle()&&supplied==5_200_000&&be.getPower()==0&&be.tanks[0].getFluidAmount()==500,"Maximum stacks finish once with exact energy and one real water charge");assertProduct(h,be,expected);h.succeed();
    }

    private static void process(IndustryBlockEntity be, int ticks) {
        for (int i = 0; i < ticks; i++) IndustryProcessing.tick(be);
    }

    private static List<ItemStack> snapshot(List<ItemStack> stacks) { return stacks.stream().map(ItemStack::copy).toList(); }

    private static boolean same(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        return true;
    }

    private static void blockOutputs(IndustryBlockEntity be) {
        for (int i = 9; i <= 12; i++) be.stock.set(i, new ItemStack(Items.COBBLESTONE, 64));
    }

    private static void assertProduct(GameTestHelper h, IndustryBlockEntity be, ItemStack expected) {
        int total = 0;
        for (var actual : be.stock.subList(9, 13)) if (!actual.isEmpty()) {
            h.assertTrue(ItemStack.isSameItemSameComponents(actual, expected), "Output preserves reserved components");
            total += actual.getCount();
        }
        h.assertTrue(total == expected.getCount(), "Exactly the reserved quantity settles once");
    }

    private static void assertBases(GameTestHelper h, String key) {
        for (int p = 0; p < 4; p++) h.assertTrue(h.getBlockState(part(p).below()).is(
                CatalogueContent.PHASE_MATRICES.get(key).get()), "Permanent matrix survives: " + p);
    }

    @GameTest(template = "empty")
    public static void threeMatricesCondenseFixedProductsAt400TicksAndExactCost(GameTestHelper h) {
        for (String base : BASES) {
            var be = machine(h, base); ready(be);
            h.assertTrue(be.stock.get(14).isEmpty() && PhaseExtractor.start(be), "Complete array starts without a tool");
            var expected = be.pending.getFirst().copy();
            String output = switch (base) { case "phase_matrix_meteor" -> "ore"; case "phase_matrix_rutile" -> "rutile"; default -> "borate"; };
            h.assertTrue(expected.is(CatalogueContent.item(output)) && expected.getCount() == 1
                    && CatalogueEquipment.getData(expected).getString("Origin").equals(h.getLevel().dimension().location().toString()),
                    "Canonical product has the current server dimension Origin");
            if (output.equals("ore")) h.assertTrue(MaterialBatch.ore(12, 58).equals(expected.get(StarfallBlueprint.BATCH.get()))
                    && MaterialQuality.of(expected).purity() == 12 && MaterialQuality.of(expected).integrity() == 58,
                    "Meteor output always has common grade 12 and integrity 58");
            h.assertTrue(be.activeRecipe.equals("condense:" + base) && be.processTicks == 400 && be.processCost == 80000
                    && be.processPaid == 0 && be.progressTicks == 0 && be.getPower() == 80000
                    && be.tanks[0].getFluidAmount() == 500 && be.pending.stream().filter(s -> !s.isEmpty()).count() == 1,
                    "Reservation consumes water once and leaves payment to tick");
            h.assertTrue(!PhaseExtractor.start(be), "Active reservation cannot be restarted");
            process(be, 1);
            h.assertTrue(be.progressTicks == 1 && be.processPaid == 200 && be.getPower() == 79800, "First tick pays 200 XH");
            process(be, 398);
            h.assertTrue(be.progressTicks == 399 && be.processPaid == 79800 && be.getPower() == 200
                    && be.stock.subList(9, 13).stream().allMatch(ItemStack::isEmpty), "No early output at tick 399");
            process(be, 1); assertProduct(h, be, expected); assertBases(h, base);
            h.assertTrue(be.idle() && be.getPower() == 0 && be.tanks[0].getFluidAmount() == 500
                    && be.experience == 0 && be.pendingExperience == 0, "One batch costs precisely 80000 XH and 500 water");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void changedMatricesSelectOnlyNextBatchAndNeverConsumeWorldBlocks(GameTestHelper h) {
        var be = machine(h, BASES.getFirst()); ready(be);
        h.setBlock(POS.below(2), StarfallBlueprint.DEEP_ORE.get());
        h.setBlock(POS.east(2), Blocks.CHEST);
        var chest = h.getBlockEntity(POS.east(2));
        process(be, 73); var first = be.pending.getFirst().copy();
        bases(h, CatalogueContent.PHASE_MATRICES.get("phase_matrix_borate").get());
        process(be, 327); assertProduct(h, be, first);
        be.setPower(80000); process(be, 1);
        h.assertTrue(be.activeRecipe.equals("condense:phase_matrix_borate") && be.pending.getFirst().is(CatalogueContent.item("borate")),
                "A changed permanent matrix selects only the next reservation");
        var second = be.pending.getFirst().copy(); process(be, 399);
        h.assertTrue(be.stock.subList(9, 13).stream().filter(s -> !s.isEmpty()).mapToInt(ItemStack::getCount).sum() == 2
                && be.stock.subList(9, 13).stream().anyMatch(s -> ItemStack.matches(s, second))
                && be.tanks[0].isEmpty() && be.getPower() == 0, "Two renewable batches settle once each");
        assertBases(h, "phase_matrix_borate");
        h.assertTrue(h.getBlockState(POS.below(2)).is(StarfallBlueprint.DEEP_ORE.get())
                && h.getBlockEntity(POS.east(2)) == chest, "Nearby ore and block entities are never mined or replaced");
        h.setBlock(POS.east(2), Blocks.AIR);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidStructuresMatricesWaterPowerAndOutputConsumeNothing(GameTestHelper h) {
        for (String failure : List.of("mixed", "missing", "vanilla", "retired", "shell", "water", "wrongFluid", "power", "outputs")) {
            var be = machine(h, BASES.getFirst()); ready(be);
            switch (failure) {
                case "mixed" -> h.setBlock(part(3).below(), CatalogueContent.PHASE_MATRICES.get("phase_matrix_rutile").get());
                case "missing" -> h.setBlock(part(3).below(), Blocks.AIR);
                case "vanilla" -> bases(h, Blocks.DIAMOND_ORE);
                case "retired" -> bases(h, StarfallBlueprint.ORE.get());
                case "shell" -> h.setBlock(part(7), Blocks.AIR);
                case "water" -> be.tanks[0].drain(501, FluidAction.EXECUTE);
                case "wrongFluid" -> be.tanks[0].setFluid(new FluidStack(Fluids.LAVA, 1000));
                case "power" -> be.setPower(199);
                case "outputs" -> blockOutputs(be);
            }
            var stock = snapshot(be.stock); var pending = snapshot(be.pending);
            int water = be.tanks[0].getFluidAmount(); long power = be.getPower();
            h.assertTrue(!PhaseExtractor.start(be) && be.idle() && same(stock, be.stock) && same(pending, be.pending)
                    && be.getPower() == power && be.tanks[0].getFluidAmount() == water, "Direct start is atomic: " + failure);
        }
        // All four output positions participate in the simulation: the last free slot is usable.
        var be = machine(h, BASES.getFirst()); ready(be); blockOutputs(be); be.stock.set(12, ItemStack.EMPTY);
        h.assertTrue(PhaseExtractor.start(be), "Fourth output position is included in preflight");
        process(be, 400);
        h.assertTrue(be.stock.get(12).is(CatalogueContent.item("ore")), "Settlement uses the simulated fourth position");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nbt73PowerFullOutputAndRedstoneResumeSameReservation(GameTestHelper h) {
        var be = machine(h, BASES.getFirst()); ready(be); process(be, 73);
        var pending = snapshot(be.pending); String recipe = be.activeRecipe;
        var saved = be.saveWithoutMetadata(h.getLevel().registryAccess());
        be.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(be.progressTicks == 73 && be.processPaid == 14600 && be.getPower() == 65400
                && be.processTicks == 400 && be.processCost == 80000 && be.activeRecipe.equals(recipe)
                && same(pending, be.pending) && be.tanks[0].getFluidAmount() == 500, "Real NBT preserves tick 73 and pending components");
        be.setPower(199); process(be, 10);
        h.assertTrue(be.progressTicks == 73 && be.processPaid == 14600 && be.getPower() == 199 && be.status == 4,
                "Power shortage never partially pays a tick");
        be.setPower(65400); blockOutputs(be); process(be, 10);
        h.assertTrue(be.progressTicks == 73 && be.getPower() == 65400 && be.status == 5 && same(pending, be.pending),
                "A full warehouse pauses the reserved batch");
        for (int i = 9; i <= 12; i++) be.stock.set(i, ItemStack.EMPTY);
        h.setBlock(POS.west(), Blocks.REDSTONE_BLOCK);
        IndustryBlockEntity.tick(h.getLevel(), be.getBlockPos(), be.getBlockState(), be);
        h.assertTrue(be.redstoneStopped() && be.progressTicks == 73 && be.getPower() == 65400 && be.status == 8,
                "Actual server tick honors redstone pause");
        h.setBlock(POS.west(), Blocks.AIR); process(be, 326);
        h.assertTrue(be.progressTicks == 399 && be.getPower() == 200, "Only 327 paid ticks remain after reload and pauses");
        process(be, 1); assertProduct(h, be, pending.getFirst());
        var stock = snapshot(be.stock); process(be, 5);
        h.assertTrue(be.idle() && same(stock, be.stock) && be.getPower() == 0 && be.tanks[0].getFluidAmount() == 500,
                "Pause and reload do not consume a second water dose or settle twice");
        assertBases(h, BASES.getFirst()); h.succeed();
    }

    @GameTest(template = "empty")
    public static void noToolRequiredAndOldDrillRemainsRetrievableThroughRealMenu(GameTestHelper h) {
        var be = machine(h, BASES.getFirst()); ready(be);
        h.assertTrue(PhaseExtractor.start(be) && be.stock.get(14).isEmpty(), "No drill is required");
        var oldDrill = new ItemStack(Items.DIAMOND_PICKAXE); oldDrill.setDamageValue(17); be.stock.set(14, oldDrill.copy());
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "PhaseMenu"));
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
        var menu = new IndustryMenu(1, player.getInventory(), be, be.processData);
        try {
            h.assertTrue(menu.getSlot(14).isActive() && !menu.getSlot(14).mayPickup(player)
                    && !menu.getSlot(14).mayPlace(oldDrill) && !menu.getSlot(14).mayPlace(new ItemStack(Items.IRON_PICKAXE)),
                    "Legacy tool slot stays visible, rejects insertion and keeps the existing active-work lock");
            var pending = snapshot(be.pending); process(be, 400); assertProduct(h, be, pending.getFirst());
            h.assertTrue(menu.getSlot(14).mayPickup(player) && ItemStack.matches(oldDrill, be.stock.get(14)),
                    "Completed condensation never wears the legacy drill and allows retrieval");
            menu.clicked(14, 0, ClickType.PICKUP, player);
            h.assertTrue(be.stock.get(14).isEmpty() && ItemStack.matches(oldDrill, menu.getCarried()) && !menu.getSlot(14).isActive(),
                    "Real pickup retrieves the old drill with its original durability and hides the emptied legacy slot");
        } finally { menu.setCarried(ItemStack.EMPTY); menu.removed(player); player.getInventory().clearContent(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredFluidAndSharedOutputPortsSimulateAndExportOnlyOnce(GameTestHelper h) {
        var be = machine(h, "phase_matrix_rutile");
        var level = h.getLevel(); var root = h.absolutePos(POS); var proxy = h.absolutePos(part(7));
        var fluid = level.getCapability(Capabilities.FluidHandler.BLOCK, proxy, Direction.NORTH);
        var bottom = level.getCapability(Capabilities.ItemHandler.BLOCK, root, Direction.DOWN);
        var shared = level.getCapability(Capabilities.ItemHandler.BLOCK, proxy, Direction.DOWN);
        h.assertTrue(fluid != null && bottom != null && shared != null && bottom.getSlots() == 4 && shared.getSlots() == 4,
                "Registered upper shell ports delegate to the same controller");
        h.assertTrue(fluid.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 0
                && fluid.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.SIMULATE) == 1000 && be.tanks[0].isEmpty(),
                "Registered water port rejects lava and simulates without mutation");
        h.assertTrue(fluid.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000
                && fluid.drain(1000, FluidAction.EXECUTE).isEmpty(), "Shared input water is filled once and cannot be exported");
        be.setPower(80000); process(be, 1); var expected = be.pending.getFirst().copy();
        for (int i = 0; i < 4; i++) h.assertTrue(shared.extractItem(i, 64, false).isEmpty(), "Pending output is hidden from every shell");
        process(be, 399); assertProduct(h, be, expected);
        int total = 0;
        for (int i = 0; i < 4; i++) {
            h.assertTrue(shared.insertItem(i, new ItemStack(Items.STONE), false).is(Items.STONE), "Outputs reject automation insertion");
            var stock = snapshot(be.stock); var simulated = shared.extractItem(i, 64, true);
            h.assertTrue(same(stock, be.stock), "Shared extraction simulation is pure");
            var actual = bottom.extractItem(i, 64, false); total += actual.getCount();
            h.assertTrue(ItemStack.matches(simulated, actual) && (actual.isEmpty() || ItemStack.isSameItemSameComponents(actual, expected))
                    && shared.extractItem(i, 64, false).isEmpty(), "Different shell ports preserve components and cannot duplicate extraction");
        }
        h.assertTrue(total == 1 && be.getPower() == 0 && be.tanks[0].getFluidAmount() == 500, "Shared controller pays and exports one batch");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyDrillPendingSettlesOnceAndSingleBlockNeverExpands(GameTestHelper h) {
        h.setBlock(POS, CatalogueContent.BLOCKS.get("phase_extractor").get());
        var be = (IndustryBlockEntity) h.getBlockEntity(POS); ready(be);
        h.setBlock(part(1), Blocks.STONE); h.setBlock(part(4), Blocks.CHEST);
        var chest = h.getBlockEntity(part(4));
        h.setBlock(POS.below(), StarfallBlueprint.ORE.get());
        var expected = CatalogueContent.stack("ore", 1); expected.set(StarfallBlueprint.BATCH.get(), MaterialBatch.ore(24, 81));
        var data = CatalogueEquipment.getData(expected); data.putString("Origin", "legacy:origin"); CatalogueEquipment.setData(expected, data);
        be.activeRecipe = "drill:starfall_blueprint:meteor_ore";
        be.pending = NonNullList.withSize(4, ItemStack.EMPTY); be.pending.set(0, expected.copy());
        be.processTicks = 400; be.processCost = 80000; be.processPaid = 14600; be.progressTicks = 73;
        be.tanks[0].drain(500, FluidAction.EXECUTE); be.setPower(65400);
        var saved = be.saveWithoutMetadata(h.getLevel().registryAccess()); be.loadWithComponents(saved, h.getLevel().registryAccess());
        process(be, 327); assertProduct(h, be, expected);
        h.assertTrue(be.idle() && be.getPower() == 0 && be.tanks[0].getFluidAmount() == 500, "Legacy pending settles once with only its remaining energy payments");
        be.setPower(80000); var stock = snapshot(be.stock); process(be, 10);
        h.assertTrue(be.idle() && !PhaseExtractor.start(be) && same(stock, be.stock) && be.getPower() == 80000
                && be.tanks[0].getFluidAmount() == 500 && h.getBlockState(POS.below()).is(StarfallBlueprint.ORE.get())
                && h.getBlockState(part(1)).is(Blocks.STONE) && h.getBlockEntity(part(4)) == chest
                && h.getBlockState(part(7)).isAir(), "Old single block stays stopped without mining, expansion, or overwriting its surroundings");
        h.setBlock(part(1), Blocks.AIR); h.setBlock(part(4), Blocks.AIR);
        h.succeed();
    }
}
