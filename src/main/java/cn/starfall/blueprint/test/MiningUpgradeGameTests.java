package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class MiningUpgradeGameTests {
    private static IndustryBlockEntity machine(GameTestHelper h, int index) {
        var root = h.absolutePos(new BlockPos(1600 + index * 8, 70, 1600));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) h.getLevel().getChunk((root.getX() >> 4) + x, (root.getZ() >> 4) + z);
        Block base = ((BlockItem) CatalogueContent.item("resonance_base_copper_ore")).getBlock();
        var state = CatalogueContent.BLOCKS.get("quarry").get().defaultBlockState().setValue(IndustryBlock.FACING, Direction.NORTH);
        for (int part = 0; part < 4; part++) {
            var pos = root.offset(LargeMachineBlock.offset(Direction.NORTH, part));
            h.getLevel().setBlock(pos.below(), base.defaultBlockState(), 3);
            h.getLevel().setBlock(pos, state.setValue(LargeMachineBlock.PART, part), 3);
            if (part < 2) h.getLevel().removeBlock(pos.north(), false);
        }
        var be = (IndustryBlockEntity) h.getLevel().getBlockEntity(root);
        be.setPower(20000);
        return be;
    }
    private static void tick(IndustryBlockEntity be, int ticks) { for (int i = 0; i < ticks; i++) IndustryProcessing.tick(be); }
    private static void reload(GameTestHelper h, IndustryBlockEntity be) { be.loadWithComponents(be.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess()); }
    private static boolean same(List<ItemStack> expected, IndustryBlockEntity be) {
        for (int i = 0; i < expected.size(); i++) if (!ItemStack.matches(expected.get(i), be.pending.get(i))) return false;
        return true;
    }

    @GameTest(template = "empty")
    public static void fortuneSmeltingLocksRealDropsBudgetAndSeedAcrossBlockedReload(GameTestHelper h) {
        var be = machine(h, 0);
        be.upgrades.setItem(0, CatalogueContent.stack("fortune_1", 1));
        be.upgrades.setItem(1, CatalogueContent.stack("fortune_3", 1));
        be.upgrades.setItem(2, CatalogueContent.stack("auto_smelt", 1));
        be.upgrades.setItem(3, CatalogueContent.stack("fortune_3", 1));
        h.assertTrue(MiningUpgrades.fortune(be) == 3, "Fortune uses the highest tier, never the sum of installed tiers or copies");
        tick(be, 1);
        int count = be.pending.stream().mapToInt(ItemStack::getCount).sum();
        h.assertTrue(be.progressTicks == 1 && count >= 2 && count <= 20
                && be.pending.stream().filter(s -> !s.isEmpty()).allMatch(s -> s.is(Items.COPPER_INGOT)),
                "Copper uses real Fortune III ore loot, then the real raw-copper smelting recipe");
        long cost = 4000 + 100L * count;
        h.assertTrue(be.processCost == cost && be.processTicks == 200 && MiningUpgrades.hasSnapshot(be),
                "Only actually converted pieces add 100 FE each to the reserved batch");
        var expected = be.pending.stream().map(ItemStack::copy).toList();
        var snapshot = be.miningSnapshot.copy();
        for (int slot = 9; slot < 13; slot++) be.stock.set(slot, new ItemStack(Items.DIRT, 64));
        long power = be.getPower(), paid = be.processPaid;
        reload(h, be); tick(be, 20);
        h.assertTrue(be.status == 5 && be.progressTicks == 1 && be.getPower() == power && be.processPaid == paid
                && same(expected, be) && be.miningSnapshot.equals(snapshot),
                "Reload under output backpressure never rerolls, reprices, charges or advances a reserved batch");
        for (int slot = 9; slot < 13; slot++) be.stock.set(slot, ItemStack.EMPTY);
        tick(be, 199);
        h.assertTrue(be.idle() && !MiningUpgrades.hasSnapshot(be) && be.getPower() == 20000 - cost
                && be.stock.subList(9, 13).stream().filter(s -> s.is(Items.COPPER_INGOT)).mapToInt(ItemStack::getCount).sum() == count,
                "Reserved smelted fortune output settles once and pays its exact fixed budget");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void silkHarvestAndConflictingOrForgedSavedMiningFreezeSafely(GameTestHelper h) {
        var be = machine(h, 1);
        be.upgrades.setItem(0, CatalogueContent.stack("silk_touch", 1));
        tick(be, 1);
        h.assertTrue(be.pending.get(0).is(Items.COPPER_ORE) && be.pending.get(0).getCount() == 1 && be.processCost == 4000,
                "Silk Touch uses the original block's actual vanilla loot rule");
        reload(h, be); tick(be, 199);
        h.assertTrue(be.idle() && be.stock.get(9).is(Items.COPPER_ORE) && be.stock.get(9).getCount() == 1,
                "Saved silk output settles exactly once");
        long power = be.getPower();
        for (String conflict : List.of("fortune_1", "auto_smelt")) {
            be.upgrades.setItem(1, CatalogueContent.stack(conflict, 1));
            reload(h, be); tick(be, 5);
            h.assertTrue(be.idle() && be.status == 14 && be.getPower() == power,
                    "Incompatible silk configuration loaded from NBT cannot start or consume power: " + conflict);
        }
        be.upgrades.setItem(1, ItemStack.EMPTY);
        tick(be, 1);
        var validSave = be.saveWithFullMetadata(h.getLevel().registryAccess());
        int progress = be.progressTicks; power = be.getPower();
        be.pending.set(0, new ItemStack(Items.DIAMOND, 64));
        tick(be, 5);
        h.assertTrue(be.status == 14 && be.progressTicks == progress && be.getPower() == power,
                "A vanilla namespace alone cannot authorize forged pending items or counts");
        be.loadWithComponents(validSave, h.getLevel().registryAccess());
        be.activeRecipe = "QM-R32"; // Retired custom-mineral route, despite the saved vanilla mining marker.
        tick(be, 5);
        h.assertTrue(be.status == 14 && be.progressTicks == progress && be.getPower() == power,
                "Mining snapshots cannot bypass retired-route validation");
        h.succeed();
    }
}
