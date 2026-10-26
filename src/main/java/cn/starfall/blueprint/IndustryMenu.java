package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.network.chat.Component;

public final class IndustryMenu extends AbstractContainerMenu {
    public static final int SAMPLE_MENU_SLOT=72, BATTERY_MENU_SLOT=73, PREPARATION_UPGRADE_MENU_SLOT=78;
    private static final int[] SHARED_UPGRADE_SLOTS={16,17,20,21};
    public final Container container;public final ContainerData data;private final IndustryBlockEntity server;
    public static final int UPGRADE_MENU_START=79, UPGRADE_PAGE_SIZE=6;
    private final DataSlot craftingPage=DataSlot.standalone(),upgradePage=DataSlot.standalone(),upgradeSlotCount=DataSlot.standalone();
    private final int[] upgradeIndices=new int[UPGRADE_PAGE_SIZE];
    private final SimpleContainer clientUpgrades=new SimpleContainer(UPGRADE_PAGE_SIZE);
    private Container upgradeView;
    public int upgradePage(){return upgradePage.get();}
    public int upgradePages(){return (Math.max(1,upgradeSlotCount.get())-1)/UPGRADE_PAGE_SIZE+1;}
    private boolean upgradeVisible(int local){return MachineUpgrades.supports(kind())&&upgradePage()*UPGRADE_PAGE_SIZE+local<upgradeSlotCount.get();}
    private void refreshUpgradeView(){
        if(server==null)return;
        List<Integer> entries=server.upgrades.displayedSlots();upgradeSlotCount.set(entries.size());
        upgradePage.set(Math.clamp(upgradePage.get(),0,upgradePages()-1));
        Arrays.fill(upgradeIndices,-1);
        for(int i=0;i<UPGRADE_PAGE_SIZE;i++){int entry=upgradePage()*UPGRADE_PAGE_SIZE+i;if(entry<entries.size())upgradeIndices[i]=entries.get(entry);}
    }
    // Container properties are signed 16-bit on the wire; page/count need both halves.
    private void addIntData(DataSlot value){
        addDataSlot(new DataSlot(){public int get(){return value.get()&65535;}public void set(int v){value.set((value.get()&0xffff0000)|(v&65535));}});
        addDataSlot(new DataSlot(){public int get(){return value.get()>>>16;}public void set(int v){value.set((value.get()&65535)|((v&65535)<<16));}});
    }
    public int craftingPage(){return craftingPage.get();}
    public IndustryMenu(int id,Inventory inv){this(id,inv,new SimpleContainer(27),new SimpleContainerData(15));}
    public IndustryMenu(int id,Inventory inv,Container container,ContainerData data){
        super(CatalogueContent.MENU.get(),id);this.data=data;checkContainerDataCount(data,15);
        server=container instanceof IndustryInventory view?view.owner:container instanceof IndustryBlockEntity be?be:null;
        this.container=server!=null&&server.kind().equals("autocrafter")?new IndustryInventory(server,craftingPage::get):container;
        checkContainerSize(this.container,27);
        // Coordinates cannot depend on kind before the client receives its synchronized data.
        for(int i=0;i<27;i++){final int index=i;addSlot(new Slot(this.container,i,slotX(i),slotY(i)){
            public boolean mayPlace(ItemStack s){return isActive()&&(isUpgradeMenuSlot(index)?canUpgrade(s):server!=null?IndustryMenu.this.container.canPlaceItem(index,s):index<9||index>=13);}
            public boolean mayPickup(Player p){if(!isActive())return false;if(isUpgradeMenuSlot(index))return upgradesIdle();if(server!=null&&server.kind().equals("plant_cultivator")&&index<2&&!server.idle())return false;return server==null||server.idle()||index<14||index>17;}
            public int getMaxStackSize(){if(isUpgradeMenuSlot(index))return getItem().isEmpty()?64:MachineUpgrades.maxStack(getItem());if(kind().equals("plant_cultivator")&&index<2)return 1;return !kind().equals("supply_crate")&&index>=14&&index<=18?1:64;}
            public int getMaxStackSize(ItemStack stack){return isUpgradeMenuSlot(index)?MachineUpgrades.maxStack(stack):Math.min(getMaxStackSize(),stack.getMaxStackSize());}
            public boolean isActive(){if(index==18||Arrays.stream(SHARED_UPGRADE_SLOTS).anyMatch(i->i==index))return false;return visible(index);}
            public void onTake(Player p,ItemStack s){super.onTake(p,s);if(index>=9&&index<=12&&server!=null)server.awardExperience(p);}
        });}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,col+row*9+9,8+col*18,174+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,8+col*18,232));
        Container filters=server!=null?server.filters:new SimpleContainer(9);
        for(int i=0;i<9;i++)addSlot(new Slot(filters,i,8+i*18,144){public boolean mayPlace(ItemStack s){return false;}public boolean mayPickup(Player p){return false;}public boolean isActive(){return hasItemFilter();}});
        addSlot(new Slot(this.container,IndustryBlockEntity.CRAFTING_SAMPLE,150,22){
            public boolean isActive(){return kind().equals("autocrafter");}
            public boolean mayPlace(ItemStack stack){return isActive();}
            public boolean mayPickup(Player player){return isActive();}
            public int getMaxStackSize(){return 1;}
        });
        addSlot(new Slot(this.container,18,8,120){
            public boolean isActive(){return !kind().equals("quarry")&&visible(18);}
            public boolean mayPlace(ItemStack stack){return isActive()&&(server!=null?IndustryMenu.this.container.canPlaceItem(18,stack):true);}
            public boolean mayPickup(Player player){return isActive();}
            public int getMaxStackSize(){return kind().equals("supply_crate")?64:1;}
        });
        for(int logical:SHARED_UPGRADE_SLOTS)addSlot(new Slot(this.container,logical,8+logical%9*18,48+logical/9*36){
            public boolean isActive(){return kind().equals("supply_crate");}
            public boolean mayPlace(ItemStack stack){return isActive()&&(server==null||IndustryMenu.this.container.canPlaceItem(logical,stack));}
            public boolean mayPickup(Player player){return isActive();}
        });
        addSlot(new Slot(this.container,IndustryBlockEntity.CRAFTING_SAMPLE,-60,88){
            public boolean isActive(){return false;}
            public boolean mayPlace(ItemStack stack){return isActive()&&canUpgrade(stack);}
            public boolean mayPickup(Player player){return isActive()&&upgradesIdle();}
            public int getMaxStackSize(){return getItem().isEmpty()?64:MachineUpgrades.maxStack(getItem());}
            public int getMaxStackSize(ItemStack stack){return MachineUpgrades.maxStack(stack);}
        });
        upgradeSlotCount.set(1);Arrays.fill(upgradeIndices,-1);
        if(server==null){int used=0;for(int i=0;i<27;i++)if(MachineUpgrades.isSlot(kind(),i)&&!container.getItem(i).isEmpty()){clientUpgrades.setItem(used++,container.getItem(i).copy());}upgradeSlotCount.set(used+1);}
        upgradeView=server==null?clientUpgrades:new Container(){
            public int getContainerSize(){return UPGRADE_PAGE_SIZE;}
            public boolean isEmpty(){for(int i=0;i<UPGRADE_PAGE_SIZE;i++)if(!getItem(i).isEmpty())return false;return true;}
            public ItemStack getItem(int i){return upgradeIndices[i]<0?ItemStack.EMPTY:server.upgrades.getItem(upgradeIndices[i]);}
            public ItemStack removeItem(int i,int n){return upgradeIndices[i]<0?ItemStack.EMPTY:server.upgrades.removeItem(upgradeIndices[i],n);}
            public ItemStack removeItemNoUpdate(int i){return upgradeIndices[i]<0?ItemStack.EMPTY:server.upgrades.removeItemNoUpdate(upgradeIndices[i]);}
            public void setItem(int i,ItemStack stack){if(upgradeIndices[i]>=0)server.upgrades.setItem(upgradeIndices[i],stack);}
            public void setChanged(){server.setChanged();}
            public boolean stillValid(Player p){return IndustryMenu.this.container.stillValid(p);}
            public void clearContent(){for(int i=0;i<UPGRADE_PAGE_SIZE;i++)setItem(i,ItemStack.EMPTY);}
        };
        refreshUpgradeView();
        for(int i=0;i<UPGRADE_PAGE_SIZE;i++){final int local=i;addSlot(new Slot(upgradeView,i,-60+i%2*24,40+i/2*24){
            public boolean isActive(){return upgradeVisible(local);}
            public boolean mayPlace(ItemStack stack){return isActive()&&canUpgrade(stack);}
            public boolean mayPickup(Player player){return isActive()&&upgradesIdle();}
            public int getMaxStackSize(){return getItem().isEmpty()?64:MachineUpgrades.maxStack(getItem());}
            public int getMaxStackSize(ItemStack stack){return Math.min(stack.getMaxStackSize(),MachineUpgrades.maxStack(stack));}
        });}
        addDataSlots(data);addDataSlot(craftingPage);addIntData(upgradePage);addIntData(upgradeSlotCount);
    }
    public boolean hasItemFilter(){return Set.of("item_extractor","item_router","supply_crate").contains(kind());}
    public boolean canSelectRecipe(){return Set.of("autocrafter","refiner","recycler","bio_processor").contains(kind());}
    public boolean isUpgradeMenuSlot(int index){return index>=UPGRADE_MENU_START&&index<UPGRADE_MENU_START+UPGRADE_PAGE_SIZE&&MachineUpgrades.supports(kind());}
    private boolean upgradesIdle(){return server==null?data.get(2)==0:MachineUpgrades.idle(server);}
    private boolean canUpgrade(ItemStack stack){return upgradesIdle()&&(server!=null?MachineUpgrades.accepts(server,stack):true);}
    private int upgradePosition(int i){return i==18?4:i==16?0:i==17?1:i==20?2:i==21?3:-1;}
    private int slotX(int i){int n=upgradePosition(i);return n>=0?-60+n%2*24:8+i%9*18;}
    private int slotY(int i){int n=upgradePosition(i);return n>=0?40+n/2*24:48+i/9*36;}
    public String kind(){int i=data.get(8);return i>=0&&i<IndustryBlockEntity.KINDS.size()?IndustryBlockEntity.KINDS.get(i):"prep";}
    public boolean visible(int i){String k=kind();if(k.equals("prep")&&i==19)return false;if(MachineUpgrades.isSlot(k,i))return true;if(k.equals("press"))return i<13;if(k.equals("phase_extractor"))return i>=9&&i<=12||i==14&&!container.getItem(14).isEmpty();if(AggregateMachines.large(k))return i>=9&&i<=12||k.equals("crusher")&&i<9;if(k.equals("supply_crate"))return true;if(Set.of("item_extractor","item_router").contains(k))return false;if(k.equals("plant_cultivator"))return i<3||i>=9&&i<=12||i==18;if(i==18)return true;if(k.equals("g01"))return i==13||i>=9&&i<=12;if(Set.of("prep","refiner","recycler","bio_processor","autocrafter","auto_furnace").contains(k))return i<14||i<16&&!k.equals("autocrafter");return false;}
    public String templateLabel(net.minecraft.world.level.Level level){int i=data.get(12);if(kind().equals("plant_cultivator"))return "环境 / 样本 / 水桶";if(i<0)return kind().equals("autocrafter")?(container.getItem(IndustryBlockEntity.CRAFTING_SAMPLE).isEmpty()?"放入成品样品 →":"无工作台配方"):"自动选择";if(kind().equals("autocrafter")){if(level==null)return container.getItem(IndustryBlockEntity.CRAFTING_SAMPLE).getHoverName().getString();var recipes=AutomaticCrafting.recipes(level);if(i>=recipes.size())return "";var recipe=recipes.get(i);var result=recipe.value().getResultItem(level.registryAccess());return result.isEmpty()?recipe.id().toString():result.getHoverName().getString();}var names=new ArrayList<>(CatalogueContent.RECIPES.keySet());return i<names.size()?names.get(i):"";}
    public long energy(){return (data.get(0)&65535L)|((data.get(1)&65535L)<<16);}
    @Override public void broadcastChanges(){if(server!=null)server.selectCraftingSample();refreshUpgradeView();super.broadcastChanges();}
    @Override public void broadcastFullState(){refreshUpgradeView();super.broadcastFullState();}
    public boolean stillValid(Player p){return container.stillValid(p);}
    public void clicked(int index,int button,ClickType type,Player p){
        if(index>=63&&index<72&&hasItemFilter()){if(server!=null){server.filters.setItem(index-63,getCarried().isEmpty()?ItemStack.EMPTY:getCarried().copyWithCount(1));broadcastChanges();}return;}
        if(index>=0&&index<slots.size()&&!slots.get(index).isActive())return;
        super.clicked(index,button,type,p);
    }
    public boolean clickMenuButton(Player player,int id){
        if(server==null)return false;IndustryBlockEntity be=server;
        if((id==18||id==19)&&MachineUpgrades.supports(kind())){refreshUpgradeView();upgradePage.set(Math.clamp(upgradePage.get()+(id==18?-1:1),0,upgradePages()-1));broadcastChanges();return true;}
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
        if(index<0||index>=slots.size()||index>=63&&index<72)return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.isActive()||!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();boolean moved=false;
        if(index<27||index>=72)moved=moveItemStackTo(stack,27,63,true);
        else{
            // Shift insertion services the whole growing bay, including off-page stacks.
            if(server!=null)moved=server.upgrades.insert(stack);
            else for(int i=UPGRADE_MENU_START;i<slots.size()&&!stack.isEmpty();i++)if(slots.get(i).isActive()&&slots.get(i).mayPlace(stack))moved=moveItemStackTo(stack,i,i+1,false)||moved;
            if(!moved){
                if(kind().equals("supply_crate")){for(int i=0;i<slots.size()&&!stack.isEmpty();i++)if((i<27||i>=BATTERY_MENU_SLOT&&i<PREPARATION_UPGRADE_MENU_SLOT)&&slots.get(i).isActive())moved=moveItemStackTo(stack,i,i+1,false)||moved;}
                else{for(int i:new int[]{13,14,15,BATTERY_MENU_SLOT})if(slots.get(i).isActive()&&slots.get(i).mayPlace(stack))moved=moveItemStackTo(stack,i,i+1,false)||moved;if(!moved)moved=moveItemStackTo(stack,0,9,false);}
            }
        }
        if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return original;
    }
}
