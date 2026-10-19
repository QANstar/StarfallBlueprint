package cn.starfall.blueprint;

import java.util.List;
import net.minecraft.core.BlockPos;
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
        int grade = roll < 65 ? 6 : roll < 95 ? 12 : 24;
        ItemStack result = new ItemStack(StarfallBlueprint.RAW.get());
        result.set(StarfallBlueprint.BATCH.get(), MaterialBatch.ore(grade, 30 + random.nextInt(11)));
        return List.of(result);
    }
}
