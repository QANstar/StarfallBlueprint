package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class OreHarvestGameTests {
    private static ItemStack pick(GameTestHelper h, int fortune) {
        ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
        if (fortune > 0) tool.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.FORTUNE), fortune);
        return tool;
    }

    private static ItemStack harvest(GameTestHelper h, BlockState state, BlockPos pos, ItemStack tool) {
        List<ItemStack> drops = Block.getDrops(state, h.getLevel(), pos, null, null, tool);
        h.assertTrue(drops.size() == 1 && drops.getFirst().is(StarfallBlueprint.RAW.get()),
                "Correct mining produces a single stack of component-bearing raw ore");
        return drops.getFirst();
    }

    @GameTest(template = "empty")
    public static void fortuneImprovesOreYieldAndGrade(GameTestHelper h) {
        ItemStack[] tools = {pick(h, 0), pick(h, 1), pick(h, 2), pick(h, 3)};
        BlockState ordinary = StarfallBlueprint.ORE.get().defaultBlockState();
        BlockState deep = StarfallBlueprint.DEEP_ORE.get().defaultBlockState();
        int[] totals = new int[4];
        int[] grades = new int[4];
        boolean[] reachedMaximum = new boolean[4];
        for (int sample = 0; sample < 1024; sample++) {
            // Synthetic loot positions sample the seeded distribution without generating extra chunks.
            BlockPos pos = h.absolutePos(new BlockPos(sample % 32, 1, sample / 32));
            RandomSource original = RandomSource.create(h.getLevel().getSeed() ^ pos.asLong());
            int roll = original.nextInt(100);
            MaterialBatch originalBatch = MaterialBatch.ore(roll < 65 ? 6 : roll < 95 ? 12 : 24,
                    30 + original.nextInt(11));
            int previousGrade = 0;
            for (int level = 0; level <= 3; level++) {
                ItemStack drop = harvest(h, ordinary, pos, tools[level]);
                MaterialBatch batch = drop.get(StarfallBlueprint.BATCH.get());
                h.assertTrue(batch != null && batch.isProcessableOre() && batch.dryMilliM() == 1_000_000,
                        "Every extra ore item retains its full processable 1000M batch");
                h.assertTrue(batch.integrity() == originalBatch.integrity() && batch.integrity() <= 40,
                        "Fortune changes grade but preserves ordinary harvest integrity");
                h.assertTrue(batch.phaseMilliM() >= previousGrade,
                        "Increasing Fortune cannot lower the same position's grade");
                if (level == 0) h.assertTrue(drop.getCount() == 1 && batch.equals(originalBatch),
                        "Unenchanted drops keep the original seeded quantity and quality");
                int maximum = level == 0 ? 1 : level + 1;
                h.assertTrue(drop.getCount() >= 1 && drop.getCount() <= maximum,
                        "Fortune uses the vanilla ore count bounds, including a possible single drop");
                reachedMaximum[level] |= drop.getCount() == maximum;
                h.assertTrue(ItemStack.matches(drop, harvest(h, ordinary, pos, tools[level]))
                                && ItemStack.matches(drop, harvest(h, deep, pos, tools[level])),
                        "Repeated loot queries and both host rocks preserve the same seeded result");
                totals[level] += drop.getCount();
                grades[level] += batch.phaseMilliM();
                previousGrade = batch.phaseMilliM();
            }
        }
        for (int level = 1; level <= 3; level++) {
            h.assertTrue(totals[level] > totals[level - 1] && grades[level] > grades[level - 1]
                            && reachedMaximum[level],
                    "Each Fortune tier increases sampled yield and grade, and can reach its stated maximum");
            h.assertTrue(tools[level].getCount() == 1 && tools[level].getDamageValue() == 0,
                    "Loot evaluation does not consume or mutate the mining tool");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oreRequiresCorrectToolAndSilkCannotCreatePlaceableOre(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        ItemStack silk = pick(h, 0);
        silk.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        ItemStack wood = new ItemStack(Items.WOODEN_PICKAXE);
        wood.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.FORTUNE), 3);
        for (Block block : new Block[]{StarfallBlueprint.ORE.get(), StarfallBlueprint.DEEP_ORE.get()}) {
            BlockState state = block.defaultBlockState();
            h.assertTrue(Block.getDrops(state, h.getLevel(), pos, null, null, ItemStack.EMPTY).isEmpty()
                            && Block.getDrops(state, h.getLevel(), pos, null, null, wood).isEmpty(),
                    "Fortune cannot bypass the iron-tier mining requirement");
            h.assertTrue(ItemStack.matches(harvest(h, state, pos, silk), harvest(h, state, pos, pick(h, 0))),
                    "Silk Touch still yields ordinary raw material rather than a placeable reroll source");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fortuneAboveThreeUsesTierThreeBudget(GameTestHelper h) {
        ItemStack three = pick(h, 3);
        ItemStack ten = pick(h, 10);
        BlockState state = StarfallBlueprint.ORE.get().defaultBlockState();
        for (int sample = 0; sample < 64; sample++) {
            BlockPos pos = h.absolutePos(new BlockPos(sample % 8, 1, sample / 8));
            h.assertTrue(ItemStack.matches(harvest(h, state, pos, three), harvest(h, state, pos, ten)),
                    "Command-created Fortune levels above III cannot exceed the configured count or quality budget");
        }
        h.succeed();
    }
}
