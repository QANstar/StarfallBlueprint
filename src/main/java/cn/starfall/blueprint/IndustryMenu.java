package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.network.chat.Component;

public final class IndustryMenu extends AbstractContainerMenu {
    public final Container container;public final ContainerData data;public boolean showFilters;private final IndustryBlockEntity server;
    private final DataSlot craftingPage=DataSlot.standalone();
    public int craftingPage(){return craftingPage.get();}
    public IndustryMenu(int id,Inventory inv){this(id,inv,new SimpleContainer(27),new SimpleContainerData(15));}
    public IndustryMenu(int id,Inventory inv,Container container,ContainerData data){
        super(CatalogueContent.MENU.get(),id);this.data=data;checkContainerDataCount(data,15);
        server=container instanceof IndustryInventory view?view.owner:container instanceof IndustryBlockEntity be?be:null;
        this.container=server!=null&&server.kind().equals("autocrafter")?new IndustryInventory(server,craftingPage::get):container;
        checkContainerSize(this.container,27);
        for(int i=0;i<27;i++){final int index=i;addSlot(new Slot(this.container,i,8+(i%9)*18,62+(i/9)*26){
            public boolean mayPlace(ItemStack s){return server!=null?IndustryMenu.this.container.canPlaceItem(index,s):index<9||index>=13;}
            public boolean mayPickup(Player p){if(server!=null&&server.kind().equals("plant_cultivator")&&index<2&&!server.idle())return false;return server==null||server.idle()||index<14||index>17;}
            public int getMaxStackSize(){if(kind().equals("plant_cultivator")&&index<2)return 1;return !kind().equals("supply_crate")&&index>=14&&index<=18?1:64;}
            public boolean isActive(){return !(showFilters&&index>=18)&&visible(index);}
            public void onTake(Player p,ItemStack s){super.onTake(p,s);if(index>=9&&index<=12&&server!=null)server.awardExperience(p);}
        });}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,col+row*9+9,8+col*18,182+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,8+col*18,240));
        Container filters=server!=null?server.filters:new SimpleContainer(9);
        for(int i=0;i<9;i++)addSlot(new Slot(filters,i,8+i*18,114){public boolean mayPlace(ItemStack s){return false;}public boolean mayPickup(Player p){return false;}public boolean isActive(){return showFilters;}});
        addDataSlots(data);addDataSlot(craftingPage);
    }
    public String kind(){int i=data.get(8);return i>=0&&i<IndustryBlockEntity.KINDS.size()?IndustryBlockEntity.KINDS.get(i):"prep";}
    public boolean visible(int i){String k=kind();if(k.equals("supply_crate"))return true;if(k.equals("plant_cultivator"))return i<3||i>=9&&i<=12||i==18;if(i==18)return true;if(k.equals("g01"))return i==13||i>=9&&i<=12;if(Set.of("prep","refiner","recycler","bio_processor","autocrafter","auto_furnace").contains(k))return i<14||i<16&&!k.equals("autocrafter")||i<18&&k.equals("auto_furnace");return false;}
    public String templateLabel(net.minecraft.world.level.Level level){int i=data.get(12);if(kind().equals("plant_cultivator"))return "环境 / 样本 / 水桶";if(i<0)return "自动选择";if(kind().equals("autocrafter")){var recipes=AutomaticCrafting.recipes(level);if(i>=recipes.size())return "";var recipe=recipes.get(i);var result=recipe.value().getResultItem(level.registryAccess());return result.isEmpty()?recipe.id().toString():result.getHoverName().getString();}var names=new ArrayList<>(CatalogueContent.RECIPES.keySet());return i<names.size()?names.get(i):"";}
    public long energy(){return (data.get(0)&65535L)|((data.get(1)&65535L)<<16);}
    public boolean stillValid(Player p){return container.stillValid(p);}
    public void clicked(int index,int button,ClickType type,Player p){
        if(index>=63&&index<72&&showFilters){if(server!=null){server.filters.setItem(index-63,getCarried().isEmpty()?ItemStack.EMPTY:getCarried().copyWithCount(1));broadcastChanges();}return;}
        super.clicked(index,button,type,p);
    }
    public boolean clickMenuButton(Player player,int id){
        if(id==7){showFilters=!showFilters;return true;}
        if(server==null)return false;IndustryBlockEntity be=server;
        if((id==16||id==17)&&be.kind().equals("autocrafter")){craftingPage.set(Math.floorMod(craftingPage.get()+(id==16?-1:1),6));broadcastChanges();return true;}
        if(id==2){be.stopped=!be.stopped;be.setChanged();return true;}
        if(id==3){be.filterMode=(be.filterMode+1)%6;player.displayClientMessage(Component.literal("过滤："+List.of("物品ID","路线","品质下限","来源","全部组件","污染上限").get(be.filterMode)),true);be.setChanged();return true;}
        if(id==4||id==5){be.retain=Math.clamp(be.retain+(id==4?-1:1),0,64);be.setChanged();return true;}
        if(id==6){List<String>choices=List.of("","water","warm","wastewater","bio","returnwater");be.fluidFilter=choices.get((choices.indexOf(be.fluidFilter)+1)%choices.size());player.displayClientMessage(Component.literal("流体过滤："+(be.fluidFilter.isEmpty()?"自动":be.fluidFilter)),true);be.setChanged();return true;}
        if(id>=10&&id<16){be.faceMask^=1<<(id-10);be.setChanged();return true;}
        if((id==0||id==1)&&be.idle()){
            List<String>choices=new ArrayList<>();
            if(be.kind().equals("autocrafter")){
                for(var holder:AutomaticCrafting.recipes(player.level()))
                    if(holder.value() instanceof AutomaticCraftingRecipe||holder.value().isSpecial()
                            ||player instanceof net.minecraft.server.level.ServerPlayer sp&&sp.getRecipeBook().contains(holder))choices.add(holder.id().toString());
                choices.sort(String::compareTo);
            }else IndustryProcessing.available(be).forEach(r->choices.add(r.get("id").getAsString()));
            if(choices.isEmpty())return false;int current=choices.indexOf(be.template);be.template=choices.get(Math.floorMod(current+(id==0?-1:1),choices.size()));
            player.displayClientMessage(Component.literal("已选配方："+be.template),true);be.setChanged();return true;
        }
        return false;
    }
    public ItemStack quickMoveStack(Player p,int index){
        if(index>=63)return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();boolean moved;
        if(index<27)moved=moveItemStackTo(stack,27,63,true);
        else if(kind().equals("supply_crate"))moved=moveItemStackTo(stack,0,27,false);
        else{moved=false;for(int i=13;i<19&&!moved;i++)if(visible(i)&&slots.get(i).mayPlace(stack))moved=moveItemStackTo(stack,i,i+1,false);if(!moved)moved=moveItemStackTo(stack,0,9,false);}
        if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return original;
    }
}
