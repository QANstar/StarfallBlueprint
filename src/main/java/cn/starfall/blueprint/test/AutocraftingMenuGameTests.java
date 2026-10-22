package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class AutocraftingMenuGameTests {
    private static FakePlayer player(GameTestHelper h){
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"CraftingPages"));
        player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();return player;
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
