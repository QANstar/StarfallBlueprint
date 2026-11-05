package cn.starfall.blueprint;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class LingchuanMenu extends AbstractContainerMenu {
    private final Container inventory;
    private final IndustryBlockEntity station;
    private final ContainerData data;
    public LingchuanMenu(int id,Inventory playerInventory){this(id,playerInventory,null);}
    public LingchuanMenu(int id,Inventory playerInventory,IndustryBlockEntity station){
        this(id,playerInventory,station,null,null);
    }
    public LingchuanMenu(int id,Inventory playerInventory,Container preview,ContainerData previewData){this(id,playerInventory,null,preview,previewData);}
    private LingchuanMenu(int id,Inventory playerInventory,IndustryBlockEntity station,Container preview,ContainerData previewData){
        super(Lingchuan.MENU.get(),id);this.station=station;
        inventory=preview!=null?preview:station==null?new SimpleContainer(27):station;
        data=previewData!=null?previewData:station==null?new SimpleContainerData(6):new ContainerData(){
            private boolean materials,components;
            public int get(int i){return switch(i){case 0->Lingchuan.STATIONS.indexOf(station.kind());case 1->(int)station.getPower()&65535;case 2->(int)(station.getPower()>>>16);case 3->Lingchuan.knowledge(playerInventory.player);case 4->materials?1:0;case 5->components?1:0;default->0;};}
            public void set(int i,int value){if(i==4)materials=value!=0;if(i==5)components=value!=0;}
            public int getCount(){return 6;}
        };
        for(int i=0;i<8;i++){final int part=i;addSlot(new Slot(inventory,i,BlackDogWorkstations.PART_X[i],BlackDogWorkstations.PART_Y[i]){
            @Override public boolean mayPlace(ItemStack stack){return isActive()&&Lingchuan.accepts(kind(),part,stack);}
            @Override public int getMaxStackSize(){return 1;}
            @Override public boolean isActive(){return kind().equals("lc_armor_assembler");}
        });}
        addSlot(new Slot(inventory,8,380,222){
            @Override public boolean mayPlace(ItemStack stack){return Lingchuan.accepts(kind(),8,stack);}
            @Override public int getMaxStackSize(){return 1;}
            @Override public boolean isActive(){return kind().equals("lc_armor_assembler")||kind().equals("lc_armor_charger");}
        });
        addSlot(new Slot(inventory,9,422,222){@Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public boolean isActive(){return !kind().equals("lc_research_station")&&!kind().equals("lc_armor_charger");}
        });
        for(int i=0;i<12;i++){final int slot=10+i;addSlot(new Slot(inventory,slot,12+i%6*20,220+i/6*20){
            @Override public boolean isActive(){return kind().equals("lc_armor_assembler");}
            @Override public boolean mayPlace(ItemStack stack){return isActive()&&Lingchuan.accepts(kind(),slot,stack);}
            @Override public int getMaxStackSize(){return 1;}
        });}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(playerSlot(playerInventory,9+row*9+col,150+col*18,212+row*18));
        for(int col=0;col<9;col++)addSlot(playerSlot(playerInventory,col,150+col*18,270));
        addDataSlots(data);
    }
    private Slot playerSlot(Inventory inventory,int slot,int x,int y){return new Slot(inventory,slot,x,y){@Override public boolean isActive(){return !kind().equals("lc_research_station");}};}
    public String kind(){return Lingchuan.STATIONS.get(Math.clamp(data.get(0),0,Lingchuan.STATIONS.size()-1));}
    public int power(){return (data.get(1)&65535)|((data.get(2)&65535)<<16);}
    public int knowledge(){return data.get(3)&65535;}
    public boolean materials(){return data.get(4)!=0;}
    public boolean componentView(){return data.get(5)!=0;}
    public ItemStack watch(){return inventory.getItem(8);}
    public net.minecraft.nbt.CompoundTag preview(){return BlackDogWorkstations.preview(inventory);}
    public int connectedAbilities(){return watch().getItem() instanceof BlackDogArmor.Watch?CatalogueEquipment.getData(watch()).getInt("Abilities"):preview().getInt("Abilities");}
    public boolean researched(int node){return (knowledge()&(1<<node))!=0;}
    @Override public boolean stillValid(Player player){return station==null||Container.stillValidBlockEntity(station,player);}
    @Override public boolean clickMenuButton(Player player,int action){
        if(Boolean.getBoolean("starfall.blackdogWorkshopCheck"))org.slf4j.LoggerFactory.getLogger(getClass()).info("WORKSHOP QA server action={} valid={} ready={} station={}",action,stillValid(player),station!=null&&AggregateMachines.ready(station),station==null?"null":station.kind());
        if(player.level().isClientSide||station==null||!stillValid(player)||station.redstoneStopped()||!AggregateMachines.ready(station))return false;
        boolean result;
        if(action==90&&kind().equals("lc_research_station")){data.set(4,materials()?0:1);result=true;}
        else if(action==91&&kind().equals("lc_armor_assembler")){data.set(5,componentView()?0:1);result=true;}
        else if(action>=300&&action<312)result=BlackDogWorkstations.connect(player,station,action-300);
        else if(action>=0&&action<16)result=Lingchuan.research(player,station,action,materials());
        else if(action>=100&&action<100+Lingchuan.MODULES.size())result=Lingchuan.manufacture(player,station,action-100);
        else if(action==180&&kind().equals("lc_parts_fabricator"))result=Lingchuan.makeFireControl(player,station);
        else if(action>=200&&action<212)result=BlackDogWorkstations.disconnect(player,station,action-200);
        else if(action==40||action==41)result=Lingchuan.forge(player,station,action-40);
        else if(action==50)result=Lingchuan.assemble(player,station);
        else if(action==51)result=Lingchuan.refit(player,station);
        else return false;
        broadcastChanges();return result;
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);
        if(!slot.hasItem()||!slot.mayPickup(player)||BlackDogArmor.deployed(slot.getItem()))return ItemStack.EMPTY;
        var stack=slot.getItem();var before=stack.copy();
        if(!(index<22?moveItemStackTo(stack,22,58,true):moveItemStackTo(stack,0,22,false)))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return before;
    }
}
