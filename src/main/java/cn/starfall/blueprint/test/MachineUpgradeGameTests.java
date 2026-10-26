package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class MachineUpgradeGameTests {
    private static final BlockPos POS=new BlockPos(1,2,1);
    private static IndustryBlockEntity machine(GameTestHelper h,String kind){h.setBlock(POS,Blocks.AIR);h.setBlock(POS,kind.equals("prep")?StarfallBlueprint.PREPARATION.get():CatalogueContent.BLOCKS.get(kind).get());return (IndustryBlockEntity)h.getBlockEntity(POS);}
    private static void ticks(IndustryBlockEntity be,int ticks){for(int i=0;i<ticks;i++)IndustryProcessing.tick(be);}
    private static int output(IndustryBlockEntity be,Item item){return be.stock.subList(9,13).stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}

    @GameTest(template="empty")
    public static void stackedAndMixedModulesFreezeRealCraftBudgetAcrossReload(GameTestHelper h){
        var be=machine(h,"autocrafter");be.template="minecraft:iron_block";be.stock.set(0,new ItemStack(Items.IRON_INGOT,9));be.stock.set(16,CatalogueContent.stack("speed_1",2));be.stock.set(20,CatalogueContent.stack("speed_2",1));be.stock.set(21,new ItemStack(StarfallBlueprint.CONNECTOR.get(),2));be.setPower(20000);
        ticks(be,1);h.assertTrue(be.processTicks==20&&be.processCost==4746&&be.stock.get(0).isEmpty(),"Two I + one II yield 2x speed; 45% premium then two efficiency modules reserve exactly 4746 XH");
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());ticks(be,19);
        h.assertTrue(be.idle()&&output(be,Items.IRON_BLOCK)==1&&be.getPower()==15254&&be.stock.get(16).getCount()==2&&be.stock.get(21).getCount()==2,"Reload settles once with exact budget and retains every module");h.succeed();
    }
    @GameTest(template="empty")
    public static void furnaceStackedBulkConsumesEveryLaneAndPreservesExperience(GameTestHelper h){
        var be=machine(h,"auto_furnace");be.stock.set(0,new ItemStack(Items.RAW_IRON,6));be.stock.set(20,CatalogueContent.stack("bulk_1",2));be.setPower(320000);ticks(be,400);
        h.assertTrue(be.idle()&&output(be,Items.IRON_INGOT)==6&&be.stock.get(0).isEmpty()&&be.getPower()==56000&&be.experience>0,"Two stacked bulk modules consume six real inputs, 264000 XH and produce six outputs with XP");h.succeed();
    }
    @GameTest(template="empty")
    public static void orePreparationLocksUpgradesAndKeepsSnapshotAcrossReload(GameTestHelper h){
        var be=(PreparationBlockEntity)machine(h,"prep");var ore=new ItemStack(StarfallBlueprint.RAW.get());ore.set(StarfallBlueprint.BATCH.get(),MaterialBatch.ore(12,58));be.stock.set(0,ore);be.water=250;be.setPower(100000);be.stock.set(16,CatalogueContent.stack("speed_1",4));be.stock.set(19,new ItemStack(StarfallBlueprint.CONNECTOR.get(),2));
        PreparationBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);h.assertTrue(!be.idle()&&!be.workCanPlace(20,CatalogueContent.stack("speed_2",1)),"Ore batch participates in upgrade idle lock");
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"OreUpgrade"));var menu=(IndustryMenu)be.createMenu(1,p.getInventory(),p);h.assertTrue(!menu.getSlot(IndustryMenu.UPGRADE_MENU_START).mayPickup(p),"Running ore modules cannot be extracted");
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());for(int i=1;i<100;i++)PreparationBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);
        h.assertTrue(be.idle()&&be.water==0&&be.getPower()==54181&&output(be,StarfallBlueprint.CRYSTAL.get())>0,"Ore batch finishes at 100 ticks for ceil(56000*9/11)=45819 XH after reload");h.succeed();
    }
    @GameTest(template="empty")
    public static void extremeOreSpeedStacksKeepPartialEnergyAcrossReload(GameTestHelper h){
        var be=(PreparationBlockEntity)machine(h,"prep");var ore=new ItemStack(StarfallBlueprint.RAW.get());ore.set(StarfallBlueprint.BATCH.get(),MaterialBatch.ore(12,58));be.stock.set(0,ore);be.water=250;be.setPower(be.capacity());
        for(int i:new int[]{16,17,20,21,19})be.stock.set(i,CatalogueContent.stack("speed_2",64));
        PreparationBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);h.assertTrue(!be.idle()&&be.getPower()==0,"Ore can accept payment for steps larger than capacity");
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());long supplied=320000;int guard=0;
        while(!be.idle()&&guard++<40){long grant=Math.min(be.capacity()-be.getPower(),3_240_000-supplied);if(grant>0){be.setPower(be.getPower()+grant);supplied+=grant;}PreparationBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);}
        h.assertTrue(be.idle()&&supplied==3_240_000&&be.getPower()==0&&output(be,StarfallBlueprint.CRYSTAL.get())>0&&be.water==0,"Five full stacks settle one ore batch with exact budget after reload");h.succeed();
    }
    @GameTest(template="empty")
    public static void sideBayShiftStacksAndRejectsAutomationAndHiddenClicks(GameTestHelper h){
        var be=machine(h,"autocrafter");var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"UpgradeMenu"));p.getInventory().clearContent();var menu=(IndustryMenu)be.createMenu(2,p.getInventory(),p);p.getInventory().setItem(9,CatalogueContent.stack("speed_1",64));menu.quickMoveStack(p,27);
        h.assertTrue(MachineUpgrades.count(be,"speed_1")==64&&p.getInventory().getItem(9).isEmpty(),"Shift installs entire stack in compatible bay");
        for(var d:Direction.values())for(int i:be.workSlots(d))h.assertTrue(!MachineUpgrades.isSlot(be.kind(),i),"No upgrade bay exposed to automation");
        var ports=new IndustryInventory(be);for(var d:Direction.values())for(int i:new int[]{16,17,20,21})h.assertTrue(!ports.canPlaceItemThroughFace(i,CatalogueContent.stack("speed_1",1),d)&&!be.canPlaceItemThroughFace(i,CatalogueContent.stack("speed_1",1),d),"Even direct sided insertion refuses upgrade slots");
        menu.setCarried(new ItemStack(Items.DIAMOND));menu.clicked(22,0,ClickType.PICKUP,p);h.assertTrue(be.stock.get(22).isEmpty()&&menu.getCarried().is(Items.DIAMOND),"Forged hidden-slot click cannot stash items");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty")
    public static void growingBayPagesAndLargeSaveRetainEveryModuleAndWorkLock(GameTestHelper h){
        var be=machine(h,"autocrafter");var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"GrowingUpgrade"));p.getInventory().clearContent();
        var menu=(IndustryMenu)be.createMenu(3,p.getInventory(),p);int bay=IndustryMenu.UPGRADE_MENU_START,size=IndustryMenu.UPGRADE_PAGE_SIZE;
        try{
            h.assertTrue(menu.upgradePage()==0&&menu.upgradePages()==1&&menu.getSlot(bay).isActive()&&menu.getSlot(bay).getItem().isEmpty(),"New bay starts with one append slot");
            for(int i=1;i<size;i++)h.assertTrue(!menu.getSlot(bay+i).isActive(),"Unused upgrade positions are hidden");
            for(int i:new int[]{16,17,18,20,21,IndustryMenu.PREPARATION_UPGRADE_MENU_SLOT})h.assertTrue(!menu.getSlot(i).isActive(),"Legacy menu positions remain inactive");
            List<ItemStack> expected=new ArrayList<>();for(int i=0;i<300;i++){ItemStack stack=CatalogueContent.stack("speed_1",64);stack.set(DataComponents.CUSTOM_NAME,Component.literal("upgrade bay "+i));expected.add(stack);}
            for(int i=0;i<size;i++)be.upgrades.setItem(i,expected.get(i).copy());menu.broadcastChanges();
            h.assertTrue(menu.upgradePages()==2,"Six occupied entries add an append slot on a second page");
            for(int i=0;i<size;i++)h.assertTrue(menu.getSlot(bay+i).isActive()&&ItemStack.matches(menu.getSlot(bay+i).getItem(),expected.get(i)),"First page displays six real stacks");
            h.assertTrue(menu.clickMenuButton(p,19)&&menu.upgradePage()==1&&menu.getSlot(bay).isActive()&&menu.getSlot(bay).getItem().isEmpty()&&!menu.getSlot(bay+1).isActive(),"Next page exposes exactly one empty append slot");
            menu.setCarried(expected.get(size).copy());menu.clicked(bay,0,ClickType.PICKUP,p);menu.broadcastChanges();
            h.assertTrue(menu.getCarried().isEmpty()&&ItemStack.matches(be.upgrades.getItem(size),expected.get(size))&&menu.getSlot(bay+1).isActive(),"Placing through the real menu grows the bay by one empty slot");
            for(int i=size+1;i<expected.size();i++)be.upgrades.setItem(i,expected.get(i).copy());
            var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());menu.broadcastChanges();
            h.assertTrue(be.upgrades.getContainerSize()>256&&be.upgrades.displayedSlots().size()==301&&MachineUpgrades.count(be,"speed_1")==19200&&menu.upgradePages()==51,"All 300 stacks survive NBT and participate across all pages");
            for(int i=0;i<expected.size();i++)h.assertTrue(ItemStack.matches(be.upgrades.getItem(i),expected.get(i)),"NBT retains components and order beyond byte-sized indices: "+i);
            while(menu.upgradePage()<menu.upgradePages()-1)menu.clickMenuButton(p,19);
            h.assertTrue(menu.upgradePage()==50&&menu.getSlot(bay).getItem().isEmpty()&&!menu.getSlot(bay+1).isActive(),"Final page contains just the append slot");
            be.upgrades.removeItemNoUpdate(2);be.upgrades.removeItemNoUpdate(258);menu.broadcastChanges();
            var entries=be.upgrades.displayedSlots();long empty=entries.stream().filter(i->be.upgrades.getItem(i).isEmpty()).count();
            h.assertTrue(entries.size()==299&&empty==1&&entries.get(entries.size()-1)==2&&menu.upgradePages()==50&&menu.upgradePage()==49,"Removal reuses the first hole, exposes one empty slot and clamps the current page");
            saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());menu.broadcastChanges();
            h.assertTrue(MachineUpgrades.count(be,"speed_1")==19072&&be.upgrades.displayedSlots().size()==299,"Reload after removal retains every remaining off-page module");
            be.template="minecraft:iron_block";be.stock.set(0,new ItemStack(Items.IRON_INGOT,9));be.setPower(be.capacity());ticks(be,1);menu.broadcastChanges();
            h.assertTrue(!be.idle()&&!menu.getSlot(bay).mayPickup(p)&&!menu.getSlot(bay).mayPlace(CatalogueContent.stack("speed_1",1)),"Working machine locks both removal and insertion on later pages");
            long before=MachineUpgrades.count(be,"speed_1");p.getInventory().setItem(9,CatalogueContent.stack("speed_1",1));menu.quickMoveStack(p,bay);menu.quickMoveStack(p,27);
            h.assertTrue(MachineUpgrades.count(be,"speed_1")==before,"Neither off-page extraction nor Shift insertion bypasses the running lock");
        }finally{menu.removed(p);p.getInventory().clearContent();}
        h.succeed();
    }
    @GameTest(template="empty")
    public static void dedicatedModulesShareCompatibleBayAndProcessorsHaveUsefulSlots(GameTestHelper h){
        for(String kind:List.of("prep","press","quarry","crusher","phase_extractor","plant_cultivator","refiner","recycler","bio_processor","autocrafter","auto_furnace")){
            var be=machine(h,kind);for(int index:new int[]{16,17,20,21})h.assertTrue(be.workCanPlace(index,CatalogueContent.stack("speed_1",64))&&be.workCanPlace(index,new ItemStack(StarfallBlueprint.CONNECTOR.get(),64)),"Universal modules fit each bay: "+kind);
            h.assertTrue(be.workCanPlace(20,CatalogueContent.stack("resonance_upgrade",1))==kind.equals("quarry"),"Broad spectrum stays specific to resonance machines");h.assertTrue(be.workCanPlace(21,CatalogueContent.stack("bulk_2",1))==kind.equals("auto_furnace"),"Parallel smelting stays specific to furnace");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void blockedOutputsNeverConsumeUpgradedCraftInputs(GameTestHelper h){
        var be=machine(h,"autocrafter");be.template="minecraft:iron_block";be.stock.set(0,new ItemStack(Items.IRON_INGOT,9));be.stock.set(16,CatalogueContent.stack("speed_2",64));be.setPower(320000);for(int i=9;i<13;i++)be.stock.set(i,new ItemStack(Items.COBBLESTONE,64));ticks(be,2);
        h.assertTrue(be.idle()&&be.getPower()==320000&&be.stock.get(0).getCount()==9&&be.stock.get(16).getCount()==64,"Full outputs preserve all real inputs, power and upgrades");h.succeed();
    }
}
