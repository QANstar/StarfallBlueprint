package cn.starfall.blueprint;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.event.GrindstoneEvent;

/** Fix the mixed-slot case without changing vanilla single-item grinding or anvil semantics. */
public final class ReinforcedRepairHooks {
    private ReinforcedRepairHooks() {}

    public static void grindstone(GrindstoneEvent.OnPlaceItem event) {
        ItemStack top = event.getTopItem();
        ItemStack bottom = event.getBottomItem();
        // Vanilla copies the top stack. Only the unmarked-top/marked-bottom case loses the upgrade.
        if (!event.getOutput().isEmpty() || top.isEmpty() || bottom.isEmpty()
                || top.getCount() != 1 || bottom.getCount() != 1 || !top.is(bottom.getItem())
                || !top.isDamageableItem() || !bottom.isDamageableItem()
                || !top.isRepairable() || !bottom.isRepairable()
                || top.getOrDefault(StarfallBlueprint.REINFORCED.get(), false)
                || !bottom.getOrDefault(StarfallBlueprint.REINFORCED.get(), false)) {
            return;
        }

        ItemStack result = top.copyWithCount(1);
        int maximum = Math.max(top.getMaxDamage(), bottom.getMaxDamage());
        long remaining = (long) top.getMaxDamage() - top.getDamageValue()
                + bottom.getMaxDamage() - bottom.getDamageValue() + maximum * 5L / 100;
        result.set(DataComponents.MAX_DAMAGE, maximum);
        result.setDamageValue((int) Math.max(maximum - remaining, 0));
        result.set(StarfallBlueprint.REINFORCED.get(), true);

        // As in vanilla, top-slot curses win if both stacks carry the same curse.
        ItemEnchantments curses = EnchantmentHelper.updateEnchantments(result, mutable -> {
            mutable.removeIf(holder -> !holder.is(EnchantmentTags.CURSE));
            for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(bottom).entrySet()) {
                if (entry.getKey().is(EnchantmentTags.CURSE) && mutable.getLevel(entry.getKey()) == 0) {
                    mutable.set(entry.getKey(), entry.getIntValue());
                }
            }
        });
        int repairCost = 0;
        for (int i = 0; i < curses.size(); i++) {
            repairCost = AnvilMenu.calculateIncreasedRepairCost(repairCost);
        }
        result.set(DataComponents.REPAIR_COST, repairCost);
        event.setOutput(result);
        // Leaving XP at -1 delegates experience calculation and input consumption to vanilla.
    }
}
