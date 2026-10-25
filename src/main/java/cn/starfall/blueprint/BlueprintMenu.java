package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Rotation;

public final class BlueprintMenu extends AbstractContainerMenu {
    public final Container container;public final ContainerData data;public final Inventory inventory;
    private String cachedText="";private ConstructionPlan cachedPlan;
    private List<Item> materialTypes=List.of();
    private Map<Item,Integer> serverStock=Map.of();
    private final int[] syncedStock=new int[ConstructionPlan.MAX_BLOCKS];
    public BlueprintMenu(int id,Inventory inventory){this(id,inventory,new SimpleContainer(28),new SimpleContainerData(8));}
    public BlueprintMenu(int id,Inventory inventory,Container container,ContainerData data){
        super(Blueprints.MENU.get(),id);this.inventory=inventory;this.container=container;this.data=data;checkContainerSize(container,28);checkContainerDataCount(data,8);
        addSlot(new Slot(container,0,8,30){public boolean mayPlace(ItemStack stack){return stack.is(Blueprints.PLAN.get());}public int getMaxStackSize(){return 1;}});
        for(int i=1;i<28;i++)addSlot(new Slot(container,i,8+(i-1)%9*18,88+(i-1)/9*18){public boolean mayPlace(ItemStack stack){return stack.is(Blueprints.CREATIVE_CORE.get())||stack.getItem()instanceof BlockItem&&BlueprintBuilderEntity.usable(stack);}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,8+col*18,166+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,8+col*18,224));addDataSlots(data);
        for(int i=0;i<syncedStock.length;i++){final int index=i;addDataSlot(new DataSlot(){
            public int get(){var plan=plan();return container instanceof BlueprintBuilderEntity&&index<materialTypes.size()?Math.min(32767,serverStock.getOrDefault(materialTypes.get(index),0)):syncedStock[index];}
            public void set(int value){syncedStock[index]=value;}
        });}
    }
    public ConstructionPlan plan(){String text=Blueprints.text(container.getItem(0));if(!text.equals(cachedText)){cachedText=text;cachedPlan=Blueprints.read(container.getItem(0));materialTypes=cachedPlan==null?List.of():new ArrayList<>(cachedPlan.materials().keySet());}return cachedPlan;}
    @Override public void broadcastChanges(){if(container instanceof BlueprintBuilderEntity builder)serverStock=builder.availableMaterials();super.broadcastChanges();}
    @Override public void broadcastFullState(){if(container instanceof BlueprintBuilderEntity builder)serverStock=builder.availableMaterials();super.broadcastFullState();}
    public Map<Item,Integer> required(){return required(false);}
    private Map<Item,Integer> required(boolean includeParts){
        if(!includeParts&&container instanceof BlueprintBuilderEntity builder)return builder.required();
        Map<Item,Integer> result=new LinkedHashMap<>();var plan=plan();if(plan==null)return result;
        var level=inventory.player==null?null:inventory.player.level();Direction facing=Direction.from2DDataValue(data.get(7));Rotation rotation=BlueprintBuilderEntity.rotation(facing,data.get(0));
        BlockPos origin=BlueprintBuilderEntity.origin(new BlockPos((data.get(2)&65535)|(data.get(3)<<16),data.get(4),(data.get(5)&65535)|(data.get(6)<<16)),facing,data.get(0),plan);
        for(var cell:plan.blocks()){BlockPos pos=origin.offset(cell.pos().rotate(rotation));if((includeParts||LargeMachineBlock.consumesMaterial(cell.state()))&&(level==null||!level.hasChunkAt(pos)||!plan.matches(cell,level.getBlockState(pos),rotation)))result.merge(cell.state().getBlock().asItem(),1,Integer::sum);}
        return result;
    }
    public int available(Item item){if(container instanceof BlueprintBuilderEntity builder)return builder.available(item);plan();int index=materialTypes.indexOf(item);return index<0?0:syncedStock[index];}
    public boolean freeConstruction(){for(int i=1;i<28;i++)if(container.getItem(i).is(Blueprints.CREATIVE_CORE.get()))return true;return false;}
    public boolean ready(){var plan=plan();var bill=required();return plan!=null&&!required(true).isEmpty()&&(freeConstruction()||bill.entrySet().stream().allMatch(e->available(e.getKey())>=e.getValue()));}
    @Override public boolean stillValid(Player player){return container.stillValid(player);}
    @Override public boolean clickMenuButton(Player player,int id){
        if(!(container instanceof BlueprintBuilderEntity builder)||!stillValid(player))return false;
        if(id==0)return builder.build(player);
        if(id==1){builder.turns=(builder.turns+1)%4;builder.status=0;builder.setChanged();broadcastChanges();return true;}
        return false;
    }
    public void importPlan(Player player,String json){
        if(!(container instanceof BlueprintBuilderEntity builder)||!stillValid(player)||!container.getItem(0).is(Blueprints.PLAN.get()))return;
        try{ConstructionPlan plan=ConstructionPlan.parse(json);Blueprints.write(container.getItem(0),plan);builder.status=1;builder.setChanged();broadcastChanges();}
        catch(RuntimeException e){player.displayClientMessage(net.minecraft.network.chat.Component.literal("导入失败："+e.getMessage()),false);}
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var original=stack.copy();
        boolean moved=index<28?moveItemStackTo(stack,28,64,true):stack.is(Blueprints.PLAN.get())?moveItemStackTo(stack,0,1,false):moveItemStackTo(stack,1,28,false);
        if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return original;
    }
}
