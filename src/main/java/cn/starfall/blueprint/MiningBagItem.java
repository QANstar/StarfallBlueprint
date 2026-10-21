package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

public final class MiningBagItem extends Item {
    public MiningBagItem(Properties p){super(p);}
    public InteractionResultHolder<ItemStack>use(Level l,Player p,InteractionHand hand){
        if(!l.isClientSide){int slot=hand==InteractionHand.MAIN_HAND?p.getInventory().selected:40;
            var s=p.getItemInHand(hand);var tag=CatalogueEquipment.getData(s);if(!tag.hasUUID("BagId")){tag.putUUID("BagId",UUID.randomUUID());CatalogueEquipment.setData(s,tag);}
            p.openMenu(new SimpleMenuProvider((id,inv,who)->new MiningBagMenu(id,inv,slot),Component.translatable("item.starfall_blueprint.sample_case")));
        }
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand),l.isClientSide);
    }
    public static boolean accepts(ItemStack bag,ItemStack input){
        if(input.isEmpty()||IndustryBlockEntity.nested(input))return false;
        String id=BuiltInRegistries.ITEM.getKey(input.getItem()).toString(),path=BuiltInRegistries.ITEM.getKey(input.getItem()).getPath();
        boolean mineral=input.has(StarfallBlueprint.BATCH.get())||input.has(CatalogueContent.QUALITY.get())||path.endsWith("_ore")||path.startsWith("raw_")||Set.of(Items.COAL,Items.DIAMOND,Items.EMERALD,Items.QUARTZ,Items.LAPIS_LAZULI,Items.REDSTONE,Items.AMETHYST_SHARD).contains(input.getItem())||Set.of("rutile","borate","moon","titan","boron","lunar").stream().anyMatch(k->input.is(CatalogueContent.item(k)));
        if(!mineral)return false;
        var filters=CatalogueEquipment.getData(bag).getList("BagFilters",8);return filters.isEmpty()||filters.stream().anyMatch(t->t.getAsString().equals(id));
    }
    public static void collect(Player p){
        for(int b=0;b<41;b++){
            if(b>=36&&b<40)continue;
            ItemStack bag=p.getInventory().getItem(b);if(!bag.is(CatalogueContent.item("sample_case"))||!CatalogueEquipment.getData(bag).getBoolean("AutoCollect")||p.containerMenu instanceof MiningBagMenu menu&&menu.pinnedSlot==b)continue;
            NonNullList<ItemStack> contents=NonNullList.withSize(9,ItemStack.EMPTY);bag.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).copyInto(contents);boolean changed=false;
            for(int i=0;i<36;i++){if(i==b)continue;ItemStack source=p.getInventory().getItem(i);if(!accepts(bag,source))continue;
                for(int pass=0;pass<2;pass++)for(int j=0;j<9&&!source.isEmpty();j++){ItemStack current=contents.get(j);if(pass==0&&!current.isEmpty()&&ItemStack.isSameItemSameComponents(current,source)){int n=Math.min(source.getCount(),current.getMaxStackSize()-current.getCount());if(n>0){current.grow(n);source.shrink(n);changed=true;}}
                    if(pass==1&&current.isEmpty()){int n=Math.min(source.getCount(),source.getMaxStackSize());contents.set(j,source.copyWithCount(n));source.shrink(n);changed=true;}}
            }
            if(changed){bag.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(contents));p.getInventory().setChanged();}
        }
    }
}
