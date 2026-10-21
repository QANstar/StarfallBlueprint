package cn.starfall.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The prep bench retains its original five-slot view and exposes this separate material view. */
public final class IndustryInventory implements WorldlyContainer {
    public final IndustryBlockEntity owner;
    public IndustryInventory(IndustryBlockEntity owner){this.owner=owner;}
    public int getContainerSize(){return 27;}public boolean isEmpty(){return owner.stock.stream().allMatch(ItemStack::isEmpty);}
    public ItemStack getItem(int i){return owner.stock.get(i);}public ItemStack removeItem(int i,int n){var s=ContainerHelper.removeItem(owner.stock,i,n);setChanged();return s;}
    public ItemStack removeItemNoUpdate(int i){return ContainerHelper.takeItem(owner.stock,i);}
    public void setItem(int i,ItemStack s){owner.stock.set(i,s);setChanged();}public void setChanged(){owner.setChanged();}
    public boolean stillValid(Player p){return Container.stillValidBlockEntity(owner,p);}public void clearContent(){for(int i=0;i<27;i++)owner.stock.set(i,ItemStack.EMPTY);setChanged();}
    public boolean canPlaceItem(int i,ItemStack s){return owner.workCanPlace(i,s);}
    public int[]getSlotsForFace(Direction d){return owner.workSlots(d);}
    public boolean canPlaceItemThroughFace(int i,ItemStack s,Direction d){return !owner.redstoneStopped()&&canPlaceItem(i,s);}
    public boolean canTakeItemThroughFace(int i,ItemStack s,Direction d){return !owner.redstoneStopped()&&(owner.kind().equals("supply_crate")||d==Direction.DOWN&&i>=9&&i<=12);}
}
