package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class AuxiliaryFortuneGameTests {
    @GameTest(template = "empty")
    public static void auxiliaryFortuneIncreasesYieldWithoutChangingOreIdentity(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var fortune = registry.getHolderOrThrow(Enchantments.FORTUNE);
        ItemStack[] tools = new ItemStack[5];
        for (int i = 0; i < tools.length; i++) {
            tools[i] = new ItemStack(Items.IRON_PICKAXE);
            if (i > 0) tools[i].enchant(fortune, i == 4 ? 10 : i);
        }
        var wrongTool = new ItemStack(Items.STONE_PICKAXE);
        wrongTool.enchant(fortune, 3);
        var silk = new ItemStack(Items.IRON_PICKAXE);
        silk.enchant(registry.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        for (String key : List.of("rutile", "borate")) {
            var state = CatalogueContent.ORES.get(key).get().defaultBlockState();
            int[] totals = new int[4];
            boolean[] maximumSeen = new boolean[4];
            for (int sample = 0; sample < 1024; sample++) {
                var pos = h.absolutePos(new BlockPos(sample % 32, 1, sample / 32));
                var ordinary = Block.getDrops(state, h.getLevel(), pos, null, null, tools[0]).getFirst();
                h.assertTrue(ordinary.getCount() == 1 && CatalogueContent.matches(key, ordinary), "Unenchanted auxiliary ore remains one raw item");
                for (int tier = 0; tier <= 3; tier++) {
                    var drops = Block.getDrops(state, h.getLevel(), pos, null, null, tools[tier]);
                    h.assertTrue(drops.size() == 1, "Exactly one output stack");
                    var out = drops.getFirst();
                    int max = tier == 0 ? 1 : tier + 1;
                    h.assertTrue(out.getCount() >= 1 && out.getCount() <= max, "Fortune quantity stays within tier budget");
                    h.assertTrue(ItemStack.isSameItemSameComponents(ordinary, out), "Ore identity and origin components stay unchanged");
                    h.assertTrue(ItemStack.matches(out, Block.getDrops(state, h.getLevel(), pos, null, null, tools[tier]).getFirst()), "Repeated loot queries cannot reroll quantity");
                    totals[tier] += out.getCount();
                    maximumSeen[tier] |= out.getCount() == max;
                    if (tier == 3) h.assertTrue(ItemStack.matches(out, Block.getDrops(state, h.getLevel(), pos, null, null, tools[4]).getFirst()), "Fortune above III uses the III budget");
                }
            }
            for (int tier = 1; tier <= 3; tier++) h.assertTrue(totals[tier] > totals[tier - 1] && maximumSeen[tier], "Each Fortune tier increases sampled yield and reaches its maximum");
            var pos = h.absolutePos(new BlockPos(1, 1, 1));
            h.assertTrue(Block.getDrops(state, h.getLevel(), pos, null, null, wrongTool).isEmpty(), "Fortune cannot bypass iron-tier requirement");
            var silkDrops = Block.getDrops(state, h.getLevel(), pos, null, null, silk);
            h.assertTrue(silkDrops.size() == 1 && CatalogueContent.matches(key, silkDrops.getFirst()) && silkDrops.getFirst().getCount() == 1, "Silk Touch still gives raw ore rather than a placeable reroll block");
        }
        for (var tool : tools) h.assertTrue(tool.getCount() == 1 && tool.getDamageValue() == 0, "Loot queries do not consume or damage tools");
        h.succeed();
    }
}
