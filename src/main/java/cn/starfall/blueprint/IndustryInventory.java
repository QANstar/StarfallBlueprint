package cn.starfall.blueprint;

import java.util.function.IntSupplier;
import net.minecraft.core.Direction;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Unified machine inventory view, or a per-viewer crafting page. */
public final class IndustryInventory implements WorldlyContainer {
    public final IndustryBlockEntity owner;
    private final IntSupplier page;
    public IndustryInventory(IndustryBlockEntity owner){this(owner,null);}
    public IndustryInventory(IndustryBlockEntity owner,IntSupplier page){this.owner=owner;this.page=page;}
    private int logical(int i){if(page!=null&&owner.kind().equals("autocrafter")&&i>=0&&i<9){int p=Math.clamp(page.getAsInt(),0,5);return p==0?i:27+(p-1)*9+i;}return i;}
    public int getContainerSize(){return page==null&&owner.kind().equals("autocrafter")?72:27;}
    public boolean isEmpty(){for(int i=0;i<getContainerSize();i++)if(!getItem(i).isEmpty())return false;return true;}
    public ItemStack getItem(int i){int slot=logical(i);return slot<27?owner.stock.get(slot):owner.getItem(slot);}
    public ItemStack removeItem(int i,int n){int slot=logical(i);ItemStack s=slot<27?ContainerHelper.removeItem(owner.stock,slot,n):owner.removeItem(slot,n);if(!s.isEmpty())setChanged();return s;}
    public ItemStack removeItemNoUpdate(int i){int slot=logical(i);return slot<27?ContainerHelper.takeItem(owner.stock,slot):owner.removeItemNoUpdate(slot);}
    public void setItem(int i,ItemStack s){int slot=logical(i);if(slot<27)owner.stock.set(slot,s);else owner.setItem(slot,s);setChanged();}
    public void setChanged(){owner.selectCraftingSample();owner.setChanged();}
    public boolean stillValid(Player p){return Container.stillValidBlockEntity(owner,p);}
    public void clearContent(){for(int i=0;i<getContainerSize();i++)setItem(i,ItemStack.EMPTY);setChanged();}
    public boolean canPlaceItem(int i,ItemStack s){return owner.workCanPlace(logical(i),s);}
    public int[]getSlotsForFace(Direction d){if(page==null)return owner.workSlots(d);return d==Direction.DOWN?new int[]{9,10,11,12}:new int[]{0,1,2,3,4,5,6,7,8};}
    public boolean canPlaceItemThroughFace(int i,ItemStack s,Direction d){return !MachineUpgrades.isSlot(owner.kind(),logical(i))&&!owner.redstoneStopped()&&canPlaceItem(i,s);}
    public boolean canTakeItemThroughFace(int i,ItemStack s,Direction d){return !owner.redstoneStopped()&&(owner.kind().equals("supply_crate")||(d==Direction.DOWN||AggregateMachines.large(owner.kind())&&d==owner.facing().getOpposite())&&i>=9&&i<=12);}
}
