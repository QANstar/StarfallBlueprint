package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Blocks;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class AlloyGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static AlloyFurnaceBlockEntity furnace(GameTestHelper h, Item carbon) {
        h.setBlock(POS, Blocks.AIR);
        h.setBlock(POS, StarfallBlueprint.ALLOY_FURNACE.get());
        var be=(AlloyFurnaceBlockEntity)h.getBlockEntity(POS);
        be.setItem(0,new ItemStack(Items.IRON_INGOT,24));
        be.setItem(1,new ItemStack(carbon,8));
        be.setItem(2,new ItemStack(Items.COAL));
        return be;
    }
    private static void ticks(AlloyFurnaceBlockEntity be,int count) {
        for(int i=0;i<count;i++) AlloyFurnaceBlockEntity.tick(be.getLevel(),be.getBlockPos(),be.getBlockState(),be);
    }
    @GameTest(template="empty")
    public static void integerBatchCountsAndSeparateCoalBudget(GameTestHelper h) {
        for(Item carbon:new Item[]{Items.COAL,Items.CHARCOAL}) {
            var be=furnace(h,carbon); ticks(be,1600);
            h.assertTrue(be.getItem(0).isEmpty() && be.getItem(1).isEmpty() && be.getItem(2).isEmpty(),"24 iron + 8 carbon ingredients + 1 separate fuel must be consumed");
            h.assertTrue(be.getItem(3).is(StarfallBlueprint.structural("mixed_steel_billet")) && be.getItem(3).getCount()==24 && be.fuelTicks==0,"Eight ten-second batches yield 24 billets with no fractional leftovers");
            ticks(be,200); h.assertTrue(be.getItem(3).getCount()==24,"Idle machine cannot duplicate the last batch");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void shortagesOutputBackpressureAndReload(GameTestHelper h) {
        var be=furnace(h,Items.COAL);
        be.setItem(0,new ItemStack(Items.IRON_INGOT,2)); ticks(be,20);
        h.assertTrue(be.getItem(0).getCount()==2 && be.getItem(2).getCount()==1 && be.progress==0,"Two iron cannot start; no fuel is consumed");
        be.setItem(0,new ItemStack(Items.IRON_INGOT,3)); be.setItem(2,ItemStack.EMPTY); ticks(be,20);
        h.assertTrue(be.getItem(0).getCount()==3 && be.getItem(1).getCount()==8,"Carbon ingredient is never used as heating fuel");
        be.setItem(2,new ItemStack(Items.CHARCOAL));
        be.setItem(3,new ItemStack(StarfallBlueprint.structural("mixed_steel_billet"),62)); ticks(be,20);
        h.assertTrue(be.getItem(0).getCount()==3 && be.getItem(2).getCount()==1,"Reserve space for all three outputs before consuming");
        be.setItem(3,ItemStack.EMPTY);ticks(be,73);
        var save=be.saveWithoutMetadata(h.getLevel().registryAccess());
        be.loadWithComponents(save,h.getLevel().registryAccess());
        be.setItem(3,new ItemStack(StarfallBlueprint.structural("mixed_steel_billet"),63));
        int fuel=be.fuelTicks;ticks(be,20);
        h.assertTrue(be.progress==73 && be.fuelTicks==fuel,"Output backpressure pauses work and fuel together");
        be.setItem(3,ItemStack.EMPTY);ticks(be,127);
        h.assertTrue(be.getItem(3).getCount()==3 && be.getItem(0).isEmpty() && be.getItem(1).getCount()==7 && be.fuelTicks==1400,"Saved batch finishes once and preserves real fuel/input balances");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void sidedCapabilitiesDoNotExtractReservedInputs(GameTestHelper h) {
        var be=furnace(h,Items.COAL);
        var level=h.getLevel();var pos=h.absolutePos(POS);
        IItemHandler top=level.getCapability(Capabilities.ItemHandler.BLOCK,pos,Direction.UP);
        IItemHandler side=level.getCapability(Capabilities.ItemHandler.BLOCK,pos,Direction.EAST);
        IItemHandler bottom=level.getCapability(Capabilities.ItemHandler.BLOCK,pos,Direction.DOWN);
        h.assertTrue(top!=null && top.getSlots()==2 && side.getSlots()==1 && bottom.getSlots()==1,"Top materials, side fuel, bottom product");
        h.assertTrue(top.extractItem(0,3,false).isEmpty() && side.extractItem(0,1,false).isEmpty(),"Automation cannot steal ingredients or fuel");
        ticks(be,200);
        h.assertTrue(bottom.extractItem(0,64,true).getCount()==3 && be.getItem(3).getCount()==3,"Output simulation never consumes");
        h.assertTrue(bottom.extractItem(0,64,false).getCount()==3 && be.getItem(3).isEmpty(),"Output extraction settles once");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void shiftClickRoutesMaterialsFuelAndOutput(GameTestHelper h) {
        var be=furnace(h,Items.COAL);
        be.clearContent();
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"AlloyMenu"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.getInventory().clearContent();
        var menu=new AlloyFurnaceMenu(1,player.getInventory(),be,be.data);
        player.getInventory().setItem(0,new ItemStack(Items.COAL,2));
        menu.quickMoveStack(player,31);
        player.getInventory().setItem(1,new ItemStack(Items.CHARCOAL));
        menu.quickMoveStack(player,32);
        player.getInventory().setItem(2,new ItemStack(Items.IRON_INGOT,4));
        menu.quickMoveStack(player,33);
        h.assertTrue(be.getItem(0).getCount()==4 && be.getItem(1).getCount()==2 && be.getItem(2).is(Items.CHARCOAL),"Shift-click first coal goes to additive, second to fuel; iron goes to base");
        ticks(be,200);
        menu.quickMoveStack(player,3);
        int products=player.getInventory().items.stream().filter(s->s.is(StarfallBlueprint.structural("mixed_steel_billet"))).mapToInt(ItemStack::getCount).sum();
        h.assertTrue(products==3 && be.getItem(3).isEmpty() && be.getItem(0).getCount()==1 && be.getItem(1).getCount()==1,"Shift output moves exactly one completed batch and preserves surplus ingredients");
        menu.removed(player);player.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty")
    public static void realDropAndReplacePreservesInFlightBatch(GameTestHelper h) {
        var be=furnace(h,Items.COAL);ticks(be,73);
        ItemStack dropped=Block.getDrops(be.getBlockState(),h.getLevel(),h.absolutePos(POS),be).getFirst();
        h.assertTrue(dropped.is(StarfallBlueprint.ALLOY_ITEM.get()),"One machine item must carry saved inventory and batch");
        h.setBlock(POS,Blocks.AIR);h.setBlock(POS.below(),Blocks.STONE);
        var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND,dropped);
        var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(POS.below())),Direction.UP,h.absolutePos(POS.below()),false);
        var ctx=new BlockPlaceContext(p,InteractionHand.MAIN_HAND,dropped,hit);
        ((BlockItem)StarfallBlueprint.ALLOY_ITEM.get()).place(ctx);
        var restored=(AlloyFurnaceBlockEntity)h.getBlockEntity(POS);
        h.assertTrue(restored.progress==73,"Actual BlockItem placement restores progress");
        ticks(restored,127);
        h.assertTrue(restored.getItem(3).getCount()==3 && restored.getItem(0).getCount()==21 && restored.getItem(1).getCount()==7 && restored.fuelTicks==1400,"Relocation never charges the reserved ingredients twice");
        h.succeed();
    }
}
