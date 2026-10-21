package cn.starfall.blueprint;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Ore blocks have no survival block item: replacing/remining cannot reroll a batch. */
public final class MeteorOreBlock extends Block {
    public MeteorOreBlock(Properties p) { super(p); }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        var origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null) return List.of();
        var tool = params.getOptionalParameter(LootContextParams.TOOL);
        if (tool == null || !tool.isCorrectToolForDrops(state)) return List.of();
        BlockPos pos = BlockPos.containing(origin);
        RandomSource random = RandomSource.create(params.getLevel().getSeed() ^ pos.asLong());
        int roll = random.nextInt(100);
        var fortune = params.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.FORTUNE);
        boolean lowImpact = CatalogueEquipment.lowImpact(tool);
        int level = lowImpact ? 0 : Math.clamp(tool.getEnchantmentLevel(fortune), 0, 3);
        int grade = roll < 65 - 10 * level ? 6 : roll < 95 - 5 * level ? 12 : 24;
        int ordinaryIntegrity = 30 + random.nextInt(11);
        int integrity = lowImpact ? CatalogueEquipment.nativeIntegrity(params.getLevel(), pos) : ordinaryIntegrity;
        // Match vanilla ore's Fortune multiplier, keeping the original quality RNG draws intact.
        int count = level == 0 ? 1 : Math.max(1, random.nextInt(level + 2));
        ItemStack result = new ItemStack(StarfallBlueprint.RAW.get(), count);
        result.set(StarfallBlueprint.BATCH.get(), MaterialBatch.ore(grade, integrity));
        var data=CatalogueEquipment.getData(result);data.putString("Origin",params.getLevel().dimension().location().toString());CatalogueEquipment.setData(result,data);
        if (lowImpact) result.set(CatalogueContent.QUALITY.get(), new MaterialQuality(grade, integrity, 0, 0, integrity >= 50, "common"));
        return List.of(result);
    }
}
