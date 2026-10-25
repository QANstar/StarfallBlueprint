package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.network.chat.Component;

public final class IndustryMenu extends AbstractContainerMenu {
    public static final int SAMPLE_MENU_SLOT=72;
    public final Container container;public final ContainerData data;private final IndustryBlockEntity server;
    private final DataSlot craftingPage=DataSlot.standalone();
    public int craftingPage(){return craftingPage.get();}
    public IndustryMenu(int id,Inventory inv){this(id,inv,new SimpleContainer(27),new SimpleContainerData(15));}
    public IndustryMenu(int id,Inventory inv,Container container,ContainerData data){
        super(CatalogueContent.MENU.get(),id);this.data=data;checkContainerDataCount(data,15);
        server=container instanceof IndustryInventory view?view.owner:container instanceof IndustryBlockEntity be?be:null;
        this.container=server!=null&&server.kind().equals("autocrafter")?new IndustryInventory(server,craftingPage::get):container;
        checkContainerSize(this.container,27);
        for(int i=0;i<27;i++){final int index=i;addSlot(new Slot(this.container,i,slotX(i),slotY(i)){
            public boolean mayPlace(ItemStack s){return server!=null?IndustryMenu.this.container.canPlaceItem(index,s):index<9||index>=13;}
            public boolean mayPickup(Player p){if(server!=null&&server.kind().equals("plant_cultivator")&&index<2&&!server.idle())return false;return server==null||server.idle()||index<14||index>17;}
            public int getMaxStackSize(){if(kind().equals("plant_cultivator")&&index<2)return 1;return !kind().equals("supply_crate")&&index>=14&&index<=18?1:64;}
            public boolean isActive(){return visible(index);}
            public void onTake(Player p,ItemStack s){super.onTake(p,s);if(index>=9&&index<=12&&server!=null)server.awardExperience(p);}
        });}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,col+row*9+9,8+col*18,174+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,8+col*18,232));
        Container filters=server!=null?server.filters:new SimpleContainer(9);
        for(int i=0;i<9;i++)addSlot(new Slot(filters,i,8+i*18,144){public boolean mayPlace(ItemStack s){return false;}public boolean mayPickup(Player p){return false;}public boolean isActive(){return hasItemFilter();}});
        addSlot(new Slot(this.container,IndustryBlockEntity.CRAFTING_SAMPLE,150,22){
            public boolean isActive(){return kind().equals("autocrafter")||kind().equals("prep");}
            public boolean mayPlace(ItemStack stack){return kind().equals("prep")?stack.is(StarfallBlueprint.CONNECTOR.get()):kind().equals("autocrafter");}
            public int getMaxStackSize(){return 1;}
        });
        addDataSlots(data);addDataSlot(craftingPage);
    }
    public boolean hasItemFilter(){return Set.of("item_extractor","item_router","supply_crate").contains(kind());}
    public boolean canSelectRecipe(){return Set.of("autocrafter","refiner","recycler","bio_processor").contains(kind());}
    private int slotX(int i){
        return 8+i%9*18;
    }
    private int slotY(int i){
        return 48+i/9*36;
    }
    public String kind(){int i=data.get(8);return i>=0&&i<IndustryBlockEntity.KINDS.size()?IndustryBlockEntity.KINDS.get(i):"prep";}
    public boolean visible(int i){String k=kind();if(k.equals("press"))return i<13;if(AggregateMachines.large(k))return i>=9&&i<=12||k.equals("crusher")&&i<9;if(k.equals("supply_crate"))return true;if(Set.of("item_extractor","item_router").contains(k))return false;if(k.equals("plant_cultivator"))return i<3||i>=9&&i<=12||i==18;if(i==18)return true;if(k.equals("g01"))return i==13||i>=9&&i<=12;if(Set.of("prep","refiner","recycler","bio_processor","autocrafter","auto_furnace").contains(k))return i<14||i<16&&!k.equals("autocrafter")||i<18&&k.equals("auto_furnace");return false;}
    public String templateLabel(net.minecraft.world.level.Level level){int i=data.get(12);if(kind().equals("plant_cultivator"))return "环境 / 样本 / 水桶";if(i<0)return kind().equals("autocrafter")?(container.getItem(IndustryBlockEntity.CRAFTING_SAMPLE).isEmpty()?"放入成品样品 →":"无工作台配方"):"自动选择";if(kind().equals("autocrafter")){if(level==null)return container.getItem(IndustryBlockEntity.CRAFTING_SAMPLE).getHoverName().getString();var recipes=AutomaticCrafting.recipes(level);if(i>=recipes.size())return "";var recipe=recipes.get(i);var result=recipe.value().getResultItem(level.registryAccess());return result.isEmpty()?recipe.id().toString():result.getHoverName().getString();}var names=new ArrayList<>(CatalogueContent.RECIPES.keySet());return i<names.size()?names.get(i):"";}
    public long energy(){return (data.get(0)&65535L)|((data.get(1)&65535L)<<16);}
    @Override public void broadcastChanges(){if(server!=null)server.selectCraftingSample();super.broadcastChanges();}
    public boolean stillValid(Player p){return container.stillValid(p);}
    public void clicked(int index,int button,ClickType type,Player p){
        if(index>=63&&index<72&&hasItemFilter()){if(server!=null){server.filters.setItem(index-63,getCarried().isEmpty()?ItemStack.EMPTY:getCarried().copyWithCount(1));broadcastChanges();}return;}
        super.clicked(index,button,type,p);
    }
    public boolean clickMenuButton(Player player,int id){
        if(server==null)return false;IndustryBlockEntity be=server;
        if((id==16||id==17)&&be.kind().equals("autocrafter")){craftingPage.set(Math.floorMod(craftingPage.get()+(id==16?-1:1),6));broadcastChanges();return true;}
        if((id==0||id==1)&&canSelectRecipe()&&be.idle()){
            List<String>choices=new ArrayList<>();
            if(be.kind().equals("autocrafter")){
                for(var holder:AutomaticCrafting.forResult(player.level(),be.stock.get(IndustryBlockEntity.CRAFTING_SAMPLE)))choices.add(holder.id().toString());
            }else IndustryProcessing.available(be).forEach(r->choices.add(r.get("id").getAsString()));
            if(choices.isEmpty())return false;int current=choices.indexOf(be.template);be.template=choices.get(Math.floorMod(current+(id==0?-1:1),choices.size()));
            player.displayClientMessage(Component.literal("已选配方："+be.template),true);be.setChanged();return true;
        }
        return false;
    }
    public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size()||index>=63&&index!=SAMPLE_MENU_SLOT)return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.isActive()||!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();boolean moved;
        if(index<27||index==SAMPLE_MENU_SLOT)moved=moveItemStackTo(stack,27,63,true);
        else if(kind().equals("supply_crate"))moved=moveItemStackTo(stack,0,27,false);
        else if(kind().equals("prep")&&stack.is(StarfallBlueprint.CONNECTOR.get()))moved=moveItemStackTo(stack,SAMPLE_MENU_SLOT,SAMPLE_MENU_SLOT+1,false);
        else{moved=false;for(int i=13;i<19&&!moved;i++)if(visible(i)&&slots.get(i).mayPlace(stack))moved=moveItemStackTo(stack,i,i+1,false);if(!moved)moved=moveItemStackTo(stack,0,9,false);}
        if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return original;
    }
}
