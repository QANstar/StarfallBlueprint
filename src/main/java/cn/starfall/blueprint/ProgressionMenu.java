package cn.starfall.blueprint;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Position-independent menu; the vanilla menu button packet is the only learning request. */
public final class ProgressionMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final ContainerData data;
    private final int nodeCount;
    private boolean closed;
    public ProgressionMenu(int id,Inventory inventory) {
        super(Progression.MENU.get(),id);
        this.inventory=inventory;
        nodeCount=Progression.NODES.size();
        final int[] mirror=new int[nodeCount+2];
        data=new ContainerData() {
            @Override public int get(int index) {
                if(inventory.player.level().isClientSide)return mirror[index];
                if(index<nodeCount)return Progression.isLearned(inventory.player,Progression.NODES.get(index).id())?1:0;
                return index==nodeCount?inventory.player.experienceLevel&65535:(inventory.player.experienceLevel>>>16)&65535;
            }
            @Override public void set(int index,int value) { mirror[index]=value; }
            @Override public int getCount() { return mirror.length; }
        };
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)
            addSlot(new Slot(inventory,9+row*9+col,100+col*18,174+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,100+col*18,232));
        addDataSlots(data);
    }
    public boolean isLearned(String id) {
        for(int i=0;i<nodeCount;i++)if(Progression.NODES.get(i).id().equals(id))return data.get(i)!=0;
        return false;
    }
    public int xpLevels() { return (data.get(nodeCount)&65535)|((data.get(nodeCount+1)&65535)<<16); }
    public Progression.Preview preview(int index) {
        Set<String> learned=new HashSet<>();
        for(int i=0;i<nodeCount;i++)if(data.get(i)!=0)learned.add(Progression.NODES.get(i).id());
        String id=index>=0&&index<nodeCount?Progression.NODES.get(index).id():"";
        return Progression.preview(inventory,id,learned,xpLevels());
    }
    @Override public boolean stillValid(Player player) {
        return !closed&&player==inventory.player&&player.isAlive()&&!player.isSpectator();
    }
    @Override public boolean clickMenuButton(Player player,int index) {
        if(player.level().isClientSide||player.containerMenu!=this||!stillValid(player)||index<0||index>=nodeCount)return false;
        boolean result=Progression.learn(player,Progression.NODES.get(index).id());
        broadcastChanges();return result;
    }
    @Override public void removed(Player player) { closed=true;super.removed(player); }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(!stillValid(player)||index<0||index>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(index);
        if(!slot.hasItem()||!slot.mayPickup(player))return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),before=stack.copy();
        if(!(index<27?moveItemStackTo(stack,27,36,false):moveItemStackTo(stack,0,27,false)))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        slot.onTake(player,stack);return before;
    }
}
