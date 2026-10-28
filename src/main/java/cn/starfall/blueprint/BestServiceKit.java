package cn.starfall.blueprint;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/** A shared tool, with the operator's knowledge checked for every repair. */
public final class BestServiceKit extends Item {
    public BestServiceKit(Properties properties) {
        super(properties.stacksTo(1).durability(32).attributes(ItemAttributeModifiers.EMPTY));
    }

    @Override public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) { return false; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack kit = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(kit);
        if (level.isClientSide) return InteractionResultHolder.success(kit);
        if (BestEngineering.researchLevel(player) < 3) return reject(player, kit, "knowledge");
        ItemStack target = player.getOffhandItem();
        if (!BestEquipment.isEquipment(target) || BestEquipment.wear(target) == 0) return reject(player, kit, "target");
        if (kit.getDamageValue() >= kit.getMaxDamage()) return reject(player, kit, "exhausted");
        int steelSlot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack material = player.getInventory().getItem(i);
            if (CatalogueContent.matches("steel", material)
                    && (!material.has(CatalogueContent.QUALITY.get()) || MaterialQuality.of(material).contamination() == 0)) {
                steelSlot = i;
                break;
            }
        }
        if (steelSlot < 0) return reject(player, kit, "steel");

        // Prepare copies before committing either hand or the material slot.
        ItemStack repaired = target.copy();
        BestEquipment.setWear(repaired, Math.max(0, BestEquipment.wear(repaired) - 200));
        ItemStack remainingSteel = player.getInventory().getItem(steelSlot).copy();
        remainingSteel.shrink(1);
        ItemStack usedKit = kit.copy();
        usedKit.setDamageValue(usedKit.getDamageValue() + 1);
        if (usedKit.getDamageValue() >= usedKit.getMaxDamage()) usedKit.shrink(1);
        player.getInventory().setItem(steelSlot, remainingSteel);
        player.setItemInHand(InteractionHand.OFF_HAND, repaired);
        player.setItemInHand(InteractionHand.MAIN_HAND, usedKit);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("message.starfall_blueprint.best_service_kit.repaired"), true);
        return InteractionResultHolder.success(player.getMainHandItem());
    }

    private static InteractionResultHolder<ItemStack> reject(Player player, ItemStack kit, String reason) {
        player.displayClientMessage(Component.translatable("message.starfall_blueprint.best_service_kit." + reason), true);
        return InteractionResultHolder.fail(kit);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.translatable("tooltip.starfall_blueprint.best_service_kit.usage"));
        lines.add(Component.translatable("tooltip.starfall_blueprint.best_service_kit.cost"));
    }
}
