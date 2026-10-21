package cn.starfall.blueprint;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.UUID;

public final class MiningBagMenu extends AbstractContainerMenu {
    public final int pinnedSlot;private final Inventory inventory;private final SimpleContainer storage=new SimpleContainer(9),filters=new SimpleContainer(3);private UUID identity;
    public MiningBagMenu(int id,Inventory inv){this(id,inv,-1);}
    public MiningBagMenu(int id,Inventory inv,int pinned){
        super(CatalogueEquipment.BAG_MENU.get(),id);inventory=inv;pinnedSlot=pinned;
        if(pinned>=0){ItemStack bag=inv.getItem(pinned);identity=CatalogueEquipment.getData(bag).getUUID("BagId");NonNullList<ItemStack>items=NonNullList.withSize(9,ItemStack.EMPTY);bag.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).copyInto(items);for(int i=0;i<9;i++)storage.setItem(i,items.get(i));var names=CatalogueEquipment.getData(bag).getList("BagFilters",8);for(int i=0;i<Math.min(3,names.size());i++)filters.setItem(i,new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(names.getString(i)))));storage.addListener(c->save());}
        for(int i=0;i<9;i++)addSlot(new Slot(storage,i,62+(i%3)*18,22+(i/3)*18){public boolean mayPlace(ItemStack s){return pinnedSlot<0||MiningBagItem.accepts(inventory.getItem(pinnedSlot),s);}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++){int slot=col+row*9+9;addSlot(new Slot(inv,slot,8+col*18,112+row*18){public boolean mayPickup(Player p){return slot!=pinnedSlot;}public boolean mayPlace(ItemStack s){return slot!=pinnedSlot;}});}
        for(int i=0;i<9;i++){int slot=i;addSlot(new Slot(inv,i,8+i*18,170){public boolean mayPickup(Player p){return slot!=pinnedSlot;}public boolean mayPlace(ItemStack s){return slot!=pinnedSlot;}});}
        for(int i=0;i<3;i++)addSlot(new Slot(filters,i,62+i*18,84){public boolean mayPlace(ItemStack s){return false;}public boolean mayPickup(Player p){return false;}});
    }
    private void save(){if(pinnedSlot<0||!stillValid(inventory.player))return;NonNullList<ItemStack>items=NonNullList.withSize(9,ItemStack.EMPTY);for(int i=0;i<9;i++)items.set(i,storage.getItem(i));inventory.getItem(pinnedSlot).set(DataComponents.CONTAINER,ItemContainerContents.fromItems(items));inventory.setChanged();}
    public boolean stillValid(Player p){return pinnedSlot<0||inventory.getItem(pinnedSlot).is(CatalogueContent.item("sample_case"))&&CatalogueEquipment.getData(inventory.getItem(pinnedSlot)).hasUUID("BagId")&&CatalogueEquipment.getData(inventory.getItem(pinnedSlot)).getUUID("BagId").equals(identity);}
    public void clicked(int index,int button,ClickType type,Player p){
        if(index>=45&&index<48){if(pinnedSlot>=0&&stillValid(p)){filters.setItem(index-45,getCarried().copyWithCount(1));ListTag names=new ListTag();for(int i=0;i<3;i++)if(!filters.getItem(i).isEmpty())names.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(filters.getItem(i).getItem()).toString()));var data=CatalogueEquipment.getData(inventory.getItem(pinnedSlot));data.put("BagFilters",names);CatalogueEquipment.setData(inventory.getItem(pinnedSlot),data);broadcastChanges();}return;}
        if(type==ClickType.SWAP&&(button==pinnedSlot||button==40&&pinnedSlot==40))return;
        super.clicked(index,button,type,p);
    }
    public boolean clickMenuButton(Player p,int id){if(id==0&&pinnedSlot>=0){var bag=inventory.getItem(pinnedSlot);var data=CatalogueEquipment.getData(bag);data.putBoolean("AutoCollect",!data.getBoolean("AutoCollect"));CatalogueEquipment.setData(bag,data);p.displayClientMessage(net.minecraft.network.chat.Component.literal(data.getBoolean("AutoCollect")?"自动收纳：开":"自动收纳：关"),true);return true;}return false;}
    public ItemStack quickMoveStack(Player p,int index){if(index>=45)return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;var s=slot.getItem();var old=s.copy();if(index<9){if(!moveItemStackTo(s,9,45,true))return ItemStack.EMPTY;}else if(!moveItemStackTo(s,0,9,false))return ItemStack.EMPTY;if(s.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,s);save();return old;}
}
