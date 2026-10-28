package cn.starfall.blueprint;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Manually serviced growing storage. Legacy slots stay in place for existing saves. */
public final class MachineUpgradeInventory implements Container {
    private final IndustryBlockEntity owner;
    private final List<ItemStack> extra=new ArrayList<>();
    private final int[] legacySlots;
    public MachineUpgradeInventory(IndustryBlockEntity owner){this.owner=owner;legacySlots=!MachineUpgrades.supports(owner.kind())?new int[0]:owner.kind().equals("prep")?new int[]{16,17,20,21,19}:owner.kind().equals("quarry")?new int[]{16,17,20,21,18}:new int[]{16,17,20,21};}
    private int[] legacy(){return legacySlots;}
    public int getContainerSize(){return legacy().length+extra.size()+1;}
    public ItemStack getItem(int index){int[] old=legacy();if(index<0)return ItemStack.EMPTY;return index<old.length?owner.stock.get(old[index]):index-old.length<extra.size()?extra.get(index-old.length):ItemStack.EMPTY;}
    public void setItem(int index,ItemStack stack){
        int[] old=legacy();if(index<0)return;
        if(index<old.length)owner.stock.set(old[index],stack);
        else{int offset=index-old.length;while(extra.size()<=offset)extra.add(ItemStack.EMPTY);extra.set(offset,stack);}
        setChanged();
    }
    public ItemStack removeItem(int index,int amount){ItemStack current=getItem(index);if(current.isEmpty())return ItemStack.EMPTY;ItemStack result=current.split(amount);if(current.isEmpty())setItem(index,ItemStack.EMPTY);else setChanged();return result;}
    public ItemStack removeItemNoUpdate(int index){ItemStack result=getItem(index);if(!result.isEmpty())setItem(index,ItemStack.EMPTY);return result;}
    public boolean isEmpty(){for(int i=0;i<getContainerSize();i++)if(!getItem(i).isEmpty())return false;return true;}
    public boolean extraIsEmpty(){return extra.stream().allMatch(ItemStack::isEmpty);}
    public void clearContent(){for(int index:legacy())owner.stock.set(index,ItemStack.EMPTY);extra.clear();setChanged();}
    public void setChanged(){owner.refreshUpgradeCapacity();owner.setChanged();}
    public boolean stillValid(Player player){return owner.stillValid(player);}
    public boolean canPlaceItem(int index,ItemStack stack){return MachineUpgrades.accepts(owner,stack);}
    /** Occupied entries followed by exactly one free entry; empty holes are reused. */
    public List<Integer> displayedSlots(){
        List<Integer> result=new ArrayList<>();int empty=-1;
        for(int i=0;i<getContainerSize();i++){if(getItem(i).isEmpty()){if(empty<0)empty=i;}else result.add(i);}
        result.add(empty);return result;
    }
    public boolean insert(ItemStack stack){
        if(!MachineUpgrades.accepts(owner,stack))return false;int before=stack.getCount();
        for(int i=0;i<getContainerSize()&&!stack.isEmpty();i++){
            ItemStack current=getItem(i);if(current.isEmpty()||!ItemStack.isSameItemSameComponents(current,stack))continue;
            int moved=Math.min(stack.getCount(),Math.min(current.getMaxStackSize(),MachineUpgrades.maxStack(stack))-current.getCount());
            if(moved>0){current.grow(moved);stack.shrink(moved);}
        }
        while(!stack.isEmpty()){
            int empty=0;while(!getItem(empty).isEmpty())empty++;
            int moved=Math.min(stack.getCount(),Math.min(stack.getMaxStackSize(),MachineUpgrades.maxStack(stack)));
            setItem(empty,stack.split(moved));
        }
        if(before!=stack.getCount())setChanged();return before!=stack.getCount();
    }
    public ListTag save(HolderLookup.Provider provider){ListTag items=new ListTag();for(ItemStack stack:extra)if(!stack.isEmpty())items.add(stack.save(provider));return items;}
    public void load(ListTag items,HolderLookup.Provider provider){extra.clear();for(int i=0;i<items.size();i++){ItemStack stack=ItemStack.parseOptional(provider,items.getCompound(i));if(!stack.isEmpty())extra.add(stack);}}
}
