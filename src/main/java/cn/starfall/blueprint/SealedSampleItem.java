package cn.starfall.blueprint;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/** A single sealed specimen, preserving its exact components; never a general backpack. */
public final class SealedSampleItem extends Item {
    public SealedSampleItem(Properties p){super(p.stacksTo(1).component(DataComponents.CONTAINER,ItemContainerContents.EMPTY));}
    public static boolean isBox(ItemStack s){return s.getItem()instanceof SealedSampleItem;}
    public InteractionResultHolder<ItemStack>use(Level level,Player p,InteractionHand hand){
        ItemStack box=p.getItemInHand(hand);if(level.isClientSide)return InteractionResultHolder.success(box);
        var contents=box.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY);var specimen=contents.stream().filter(s->!s.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        InteractionHand other=hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;var held=p.getItemInHand(other);
        if(specimen.isEmpty()){
            if(!held.isEmpty()&&!IndustryBlockEntity.nested(held)&&held.getItem()!=Items.AIR){box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(held.copyWithCount(1))));if(!p.isCreative())held.shrink(1);p.displayClientMessage(Component.literal("已封存1件样本；来源、品质和污染标签原样保留。潜行右键取回。"),true);}
            else p.displayClientMessage(Component.literal("副手持样本，右键封存1件；不接受其他容器。"),true);
        }else if(p.isShiftKeyDown()){
            if(held.isEmpty()){p.setItemInHand(other,specimen.copy());box.set(DataComponents.CONTAINER,ItemContainerContents.EMPTY);}
            else {var returned=specimen.copy();if(p.getInventory().add(returned)&&returned.isEmpty())box.set(DataComponents.CONTAINER,ItemContainerContents.EMPTY);else p.displayClientMessage(Component.literal("背包已满，样本仍保留在封存盒中。"),true);}
        }else p.displayClientMessage(Component.literal("封存样本：").append(specimen.getHoverName()).append("；潜行右键取回。"),true);
        p.getInventory().setChanged();return InteractionResultHolder.consume(box);
    }
    public void appendHoverText(ItemStack stack,TooltipContext c,List<Component>lines,TooltipFlag f){var sample=stack.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).stream().filter(s->!s.isEmpty()).findFirst();lines.add(Component.literal(sample.isPresent()?"封存：":"空封存盒；副手持样本后右键").append(sample.map(ItemStack::getHoverName).orElse(Component.empty())));if(sample.isPresent()&&sample.get().has(CatalogueContent.QUALITY.get())){var q=MaterialQuality.of(sample.get());lines.add(Component.literal("P"+q.purity()+" I"+q.integrity()+" C"+q.contamination()+" · "+q.route()));}}
}
