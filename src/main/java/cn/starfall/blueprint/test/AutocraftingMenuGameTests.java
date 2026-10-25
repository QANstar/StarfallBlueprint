package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class AutocraftingMenuGameTests {
    private static IndustryBlockEntity machine(GameTestHelper h){h.setBlock(new BlockPos(1,2,1),CatalogueContent.BLOCKS.get("autocrafter").get());return (IndustryBlockEntity)h.getBlockEntity(new BlockPos(1,2,1));}
    private static int output(IndustryBlockEntity be,Item item){return be.stock.subList(9,13).stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}
    @GameTest(template="empty")
    public static void actualSampleSlotSelectsRecipeWithoutSpendingOrCopyingTheSample(GameTestHelper h){
        var be=machine(h);var player=player(h);var menu=(IndustryMenu)be.createMenu(1,player.getInventory(),player);
        var sample=new ItemStack(Items.TORCH,64);sample.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("我的样品"));menu.setCarried(sample);
        menu.clicked(IndustryMenu.SAMPLE_MENU_SLOT,0,ClickType.PICKUP,player);
        h.assertTrue(be.stock.get(IndustryBlockEntity.CRAFTING_SAMPLE).getCount()==1&&menu.getCarried().getCount()==63,"Real menu puts exactly one owned item in the independent sample slot");
        h.assertTrue(!be.template.isEmpty()&&h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(be.template)).orElseThrow().value().getResultItem(h.getLevel().registryAccess()).is(Items.TORCH),"Sample selects its actual workbench output recipe without requiring a recipe-book entry");
        be.setItem(0,new ItemStack(Items.COAL,2));be.setItem(1,new ItemStack(Items.STICK,2));be.setPower(8000);for(int i=0;i<80;i++)IndustryProcessing.tick(be);
        h.assertTrue(output(be,Items.TORCH)==8&&be.getPower()==0&&be.stock.get(19).getCount()==1&&menu.getCarried().getCount()==63,"Two batches consume real inputs and exactly 8000 XH, never the sample or carried stack");
        h.assertTrue(be.stock.subList(9,13).stream().filter(s->!s.isEmpty()).noneMatch(s->s.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)),"Sample components do not get copied onto crafted products");
        var copy=new IndustryBlockEntity(be.getBlockPos(),be.getBlockState());copy.loadWithComponents(be.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(copy.stock.get(19).getCount()==1&&copy.stock.get(19).getHoverName().getString().equals("我的样品")&&copy.template.equals(be.template),"Sample identity and selected recipe persist");
        menu.clicked(IndustryMenu.SAMPLE_MENU_SLOT,0,ClickType.QUICK_MOVE,player);
        h.assertTrue(be.stock.get(19).isEmpty()&&player.getInventory().countItem(Items.TORCH)==1&&!be.template.isEmpty(),"Shift-click returns the real sample while keeping the chosen recipe");
        h.assertTrue(java.util.Arrays.stream(be.craftingInputSlots()).noneMatch(i->i==19)&&java.util.Arrays.stream(be.workSlots(Direction.UP)).noneMatch(i->i==19)&&!be.canTakeItemThroughFace(19,new ItemStack(Items.TORCH),Direction.DOWN),"Sample is outside consumable input inventory and automation ports");h.succeed();
    }
    @GameTest(template="empty")
    public static void sampleRejectsUnknownOutputsAndDoesNotCompleteAnIncompleteMaterialBill(GameTestHelper h){
        var be=machine(h);be.template="minecraft:iron_block";be.setItem(19,new ItemStack(Items.BEDROCK));be.setItem(0,new ItemStack(Items.IRON_INGOT,8));be.setPower(8000);
        IndustryProcessing.tick(be);h.assertTrue(be.template.isEmpty()&&be.idle()&&be.stock.get(0).getCount()==8&&be.getPower()==8000,"Unknown sample clears stale target and does not spend anything");
        be.setItem(19,new ItemStack(Items.IRON_BLOCK));IndustryProcessing.tick(be);
        h.assertTrue(be.idle()&&be.stock.get(0).getCount()==8&&be.stock.get(19).is(Items.IRON_BLOCK),"An output sample never substitutes for missing craft ingredients");
        be.setItem(0,new ItemStack(Items.IRON_INGOT,9));for(int i=0;i<40;i++)IndustryProcessing.tick(be);
        h.assertTrue(output(be,Items.IRON_BLOCK)==1&&be.stock.get(19).is(Items.IRON_BLOCK),"Complete bill crafts normally and retains sample");h.succeed();
    }
    @GameTest(template="empty")
    public static void swappingSamplesOnlyChangesTheNextBatchAndCyclesOnlyMatchingRecipes(GameTestHelper h){
        var be=machine(h);var player=player(h);var menu=(IndustryMenu)be.createMenu(1,player.getInventory(),player);be.setItem(19,new ItemStack(Items.IRON_BLOCK));
        be.setItem(0,new ItemStack(Items.IRON_INGOT,9));be.setItem(1,new ItemStack(Items.GOLD_INGOT,9));be.setPower(8000);for(int i=0;i<10;i++)IndustryProcessing.tick(be);
        String reserved=be.activeRecipe;be.setItem(19,new ItemStack(Items.GOLD_BLOCK));
        h.assertTrue(be.activeRecipe.equals(reserved)&&be.progressTicks==10&&be.pending.get(0).is(Items.IRON_BLOCK),"Changing the sample never rewrites the reserved batch");
        for(int i=0;i<70;i++)IndustryProcessing.tick(be);
        h.assertTrue(output(be,Items.IRON_BLOCK)==1&&output(be,Items.GOLD_BLOCK)==1&&be.getPower()==0&&be.stock.get(19).is(Items.GOLD_BLOCK),"Old batch finishes once, then new sample governs next batch");
        be.setItem(19,new ItemStack(Items.STICK));var choices=AutomaticCrafting.forResult(h.getLevel(),be.stock.get(19));
        h.assertTrue(choices.size()>1,"Stick fixture has multiple workbench alternatives");
        for(int i=0;i<choices.size()+1;i++){h.assertTrue(menu.clickMenuButton(player,1),"Sample alternatives can be selected");h.assertTrue(choices.stream().anyMatch(r->r.id().toString().equals(be.template)),"Selection cannot cycle to unrelated item recipes");}
        h.succeed();
    }
    private static FakePlayer player(GameTestHelper h){
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"CraftingPages"));
        player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();return player;
    }
    @GameTest(template="empty")
    public static void sampleChoicesExcludeMachineAndOversizedRecipes(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var original=new java.util.ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var bulkId=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StarfallBlueprint.ID,"preview_bulk_test");
        var bulk=new BulkCraftingRecipe(java.util.List.of(new BulkCraftingRecipe.Requirement(Ingredient.of(Items.OAK_PLANKS),80)),new ItemStack(Items.STICK));
        var replacement=new java.util.ArrayList<RecipeHolder<?>>(original);replacement.add(new RecipeHolder<>(bulkId,bulk));
        try{
            manager.replaceRecipes(replacement);var be=machine(h);be.template=bulkId.toString();be.setItem(19,new ItemStack(Items.STICK));
            var choices=AutomaticCrafting.forResult(h.getLevel(),be.stock.get(19));
            h.assertTrue(!be.template.equals(bulkId.toString())&&!choices.isEmpty()&&choices.stream().noneMatch(r->r.id().equals(bulkId)),"Sample selection replaces a legacy bulk preset with an actual workbench recipe");
            h.assertTrue(AutomaticCrafting.recipes(h.getLevel()).stream().allMatch(r->r.value().getType()==RecipeType.CRAFTING&&r.value().canCraftInDimensions(3,3)&&AutomaticCrafting.previewGrid(r.value()).size()==9),"Every visible choice is a previewable 3x3 workbench recipe");
            h.assertTrue(AutomaticCrafting.previewGrid(bulk).isEmpty(),"Bulk recipes cannot leak a misleading workbench preview");
            h.assertTrue(AutomaticCrafting.forResult(h.getLevel(),new ItemStack(Items.IRON_INGOT)).stream().noneMatch(r->r.value().getType()==RecipeType.SMELTING||r.value().getType()==RecipeType.BLASTING),"Furnace and blast furnace outputs do not appear as crafting choices");
        }finally{manager.replaceRecipes(original);}h.succeed();
    }
    @GameTest(template="empty")
    public static void previewPreservesShapedHolesAndTracksServerRecipeSwitches(GameTestHelper h){
        var chest=h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.withDefaultNamespace("chest")).orElseThrow();
        var grid=AutomaticCrafting.previewGrid(chest.value());
        h.assertTrue(grid.size()==9&&grid.get(4).isEmpty()&&java.util.stream.IntStream.range(0,9).filter(i->!grid.get(i).isEmpty()).count()==8,"Chest preview preserves the empty center and all eight planks");
        var be=machine(h);var viewer=player(h);var menu=(IndustryMenu)be.createMenu(1,viewer.getInventory(),viewer);be.setItem(19,new ItemStack(Items.STICK));
        var choices=AutomaticCrafting.forResult(h.getLevel(),be.stock.get(19));
        for(int i=0;i<choices.size()+1;i++){
            var selected=AutomaticCrafting.recipes(h.getLevel()).get(be.processData.get(12));
            h.assertTrue(selected.id().toString().equals(be.template)&&!AutomaticCrafting.previewGrid(selected.value()).isEmpty()&&selected.value().getResultItem(h.getLevel().registryAccess()).is(Items.STICK),"Synchronized recipe index resolves to the selected server recipe and its matching preview");
            h.assertTrue(menu.clickMenuButton(viewer,1),"Cycle to next actual workbench alternative");
        }
        h.assertTrue(be.stock.get(19).getCount()==1&&be.stock.subList(0,9).stream().allMatch(ItemStack::isEmpty)&&be.getPower()==0,"Previewing and switching never consume the sample, manufacture inputs or spend energy");
        var torch=h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.withDefaultNamespace("torch")).orElseThrow();
        var torchGrid=AutomaticCrafting.previewGrid(torch.value());h.assertTrue(!torchGrid.get(0).isEmpty()&&!torchGrid.get(3).isEmpty()&&torchGrid.get(1).isEmpty()&&torch.value().getResultItem(h.getLevel().registryAccess()).getCount()==4,"Narrow shaped recipes retain their vertical layout and actual output quantity");h.succeed();
    }
    @GameTest(template="empty")
    public static void craftingPagesArePerViewerAndPreserveReservedWork(GameTestHelper h){
        BlockPos pos=new BlockPos(1,2,1);h.setBlock(pos,CatalogueContent.BLOCKS.get("autocrafter").get());
        var be=(IndustryBlockEntity)h.getBlockEntity(pos);
        var first=player(h);var second=player(h);
        var a=(IndustryMenu)be.createMenu(1,first.getInventory(),first);
        var b=(IndustryMenu)be.createMenu(2,second.getInventory(),second);
        try{
            h.assertTrue(be.getContainerSize()==72&&be.craftingInputSlots().length==54,"54 logical inputs coexist with the original utility slots");
            h.assertTrue(be.workSlots(Direction.NORTH).length==54&&be.workSlots(Direction.DOWN).length==4,"Side input exposes every page; bottom exposes outputs only");
            h.assertTrue(a.clickMenuButton(first,17)&&a.craftingPage()==1&&b.craftingPage()==0,"Viewer A changes only their own input page");
            first.getInventory().setItem(9,new ItemStack(Items.IRON_INGOT,12));
            a.clicked(27,0,ClickType.QUICK_MOVE,first);
            second.getInventory().setItem(9,new ItemStack(Items.COPPER_INGOT,7));
            b.clicked(27,0,ClickType.QUICK_MOVE,second);
            h.assertTrue(be.getItem(27).is(Items.IRON_INGOT)&&be.getItem(27).getCount()==12&&be.getItem(0).is(Items.COPPER_INGOT)&&be.getItem(0).getCount()==7,"Shift insertion follows each viewer's visible page");
            h.assertTrue(a.getSlot(0).getItem().is(Items.IRON_INGOT)&&b.getSlot(0).getItem().is(Items.COPPER_INGOT),"Both menus retain their independent mappings");
            a.clicked(0,0,ClickType.QUICK_MOVE,first);
            h.assertTrue(be.getItem(27).isEmpty()&&be.getItem(0).getCount()==7&&first.getInventory().getItem(8).getCount()==12,"Shift extraction removes only the currently viewed input");
            for(int i=0;i<5;i++)a.clickMenuButton(first,17);
            h.assertTrue(a.craftingPage()==0&&b.craftingPage()==0,"Six pages wrap forward to zero");
            a.clickMenuButton(first,16);
            h.assertTrue(a.craftingPage()==5,"Previous from page zero wraps to page five");
            a.container.setItem(8,new ItemStack(Items.DIAMOND,3));
            h.assertTrue(be.getItem(71).getCount()==3,"Last visible input maps to logical slot 71");
            h.assertTrue(a.getSlot(0).mayPlace(new ItemStack(StarfallBlueprint.TANK_ITEM.get())),"Automatic crafting accepts block-entity ingredients");
            be.activeRecipe="test:reserved";be.template="minecraft:iron_block";be.progressTicks=17;be.processTicks=40;be.processCost=4000;be.processPaid=1700;be.setPower(2300);be.pending.set(0,new ItemStack(Items.IRON_BLOCK));
            a.clickMenuButton(first,17);b.clickMenuButton(second,16);
            h.assertTrue(be.progressTicks==17&&be.processPaid==1700&&be.getPower()==2300&&be.activeRecipe.equals("test:reserved")&&be.template.equals("minecraft:iron_block")&&be.pending.get(0).is(Items.IRON_BLOCK),"Paging while working changes neither reservation nor energy ledger");
            var copy=new IndustryBlockEntity(be.getBlockPos(),be.getBlockState());
            copy.loadWithComponents(be.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(copy.getItem(71).getCount()==3&&copy.getItem(0).getCount()==7&&copy.progressTicks==17&&copy.processPaid==1700,"Extra inputs and original inputs survive together with in-flight work");
            var legacy=be.saveWithFullMetadata(h.getLevel().registryAccess());legacy.remove("ExtraCraftingInputs");
            copy.loadWithComponents(legacy,h.getLevel().registryAccess());
            h.assertTrue(copy.getItem(0).getCount()==7&&copy.getItem(71).isEmpty(),"Legacy saves retain original slots and initialize empty extra inputs");
        }finally{a.removed(first);b.removed(second);first.getInventory().clearContent();second.getInventory().clearContent();}
        h.succeed();
    }
}
