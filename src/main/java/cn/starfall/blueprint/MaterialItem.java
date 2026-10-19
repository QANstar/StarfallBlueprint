package cn.starfall.blueprint;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public final class MaterialItem extends Item {
    public MaterialItem(Properties properties, MaterialBatch defaultBatch) { super(properties.component(StarfallBlueprint.BATCH.get(), defaultBatch)); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flags) {
        MaterialBatch b = stack.get(StarfallBlueprint.BATCH.get());
        if (b == null) return;
        tooltip.add(Component.translatable("tooltip.starfall_blueprint.mass", b.dryMilliM() / 1000.0).withStyle(ChatFormatting.GRAY));
        if (b.form().equals("raw")) tooltip.add(Component.translatable("tooltip.starfall_blueprint.grade", b.phaseMilliM() / 10000, b.integrity()).withStyle(ChatFormatting.AQUA));
        else if (b.form().equals("granules")) tooltip.add(Component.translatable("tooltip.starfall_blueprint.quality", b.integrity(), b.isQ0() ? "Q0" : "—").withStyle(ChatFormatting.AQUA));
        else tooltip.add(Component.translatable("tooltip.starfall_blueprint.tailings").withStyle(ChatFormatting.DARK_GRAY));
        if (flags.isAdvanced()) tooltip.add(Component.literal("source=" + b.sourceId() + " route=" + b.route() + " phase=" + b.phaseMilliM() + "mM C" + b.contamination()));
    }
}
