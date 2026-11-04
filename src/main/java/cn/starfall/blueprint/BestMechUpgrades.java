package cn.starfall.blueprint;

import net.minecraft.world.item.ItemStack;

/** Pure packed-chassis conversion; the workbench owns payment and returning an installed body kit. */
public final class BestMechUpgrades {
    private BestMechUpgrades() {}

    /** Returns an independent advanced stack, or EMPTY when the source cannot be upgraded. */
    public static ItemStack upgraded(ItemStack source) {
        if(source.isEmpty() || source.getCount() != 1 || !(source.getItem() instanceof BestMechItem item) || item.heavy()) return ItemStack.EMPTY;
        var state = BestMechItem.state(source);
        if(!state.getBoolean("Isolated")) return ItemStack.EMPTY;
        if(state.contains("Health")) {
            float health = state.getFloat("Health");
            if(!Float.isFinite(health) || health <= 0) return ItemStack.EMPTY;
        } else {
            // Resolve legacy light defaults before changing identity, so conversion never heals to 300.
            state.putFloat("Health", BestMechBodies.maxHealth(BestMechBodies.read(state)));
        }
        state.putString(BestMechBodies.STATE, "");
        ItemStack upgraded = CatalogueContent.stack("best_heavy_mech", 1);
        upgraded.applyComponents(source.getComponentsPatch());
        BestMechItem.setState(upgraded, state);
        return upgraded;
    }
}
