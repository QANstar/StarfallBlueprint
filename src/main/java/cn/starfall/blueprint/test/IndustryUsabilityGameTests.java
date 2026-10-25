package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class IndustryUsabilityGameTests {
    private static final BlockPos POS=new BlockPos(2,2,2);
    @GameTest(template="empty")
    public static void thinConduitsConnectOnlyToCompatibleNeighbors(GameTestHelper h){
        var cable=CatalogueContent.BLOCKS.get("cable").get();h.setBlock(POS,cable);
        h.setBlock(POS.east(),cable);h.setBlock(POS.west(),CatalogueContent.BLOCKS.get("pipe").get());
        var state=h.getBlockState(POS);
        h.assertTrue(state.getValue(ConduitBlock.CONNECTIONS.get(Direction.EAST))&&!state.getValue(ConduitBlock.CONNECTIONS.get(Direction.WEST)),"Cable connects to cable, never to fluid pipe");
        var shape=state.getShape(h.getLevel(),h.absolutePos(POS),CollisionContext.empty()).bounds();
        h.assertTrue(shape.minY==6/16.0&&shape.maxY==10/16.0&&shape.maxX==1,"Cable has a real 4/16-wide selection and collision shape with an attached arm");
        h.setBlock(POS.east(),Blocks.AIR);
        h.assertTrue(!h.getBlockState(POS).getValue(ConduitBlock.CONNECTIONS.get(Direction.EAST)),"Removing a neighbor removes its rendered arm");
        h.setBlock(POS.north(),CatalogueContent.BLOCKS.get("refiner").get());
        h.assertTrue(h.getBlockState(POS).getValue(ConduitBlock.CONNECTIONS.get(Direction.NORTH)),"Cable connects to a powered workstation");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void fixedPortsRotateAndIgnoreOldFaceMasks(GameTestHelper h){
        h.setBlock(POS,CatalogueContent.BLOCKS.get("item_extractor").get().defaultBlockState().setValue(IndustryBlock.FACING,Direction.EAST));
        var be=(IndustryBlockEntity)h.getBlockEntity(POS);be.faceMask=0;
        h.assertTrue(be.connectionOpen(Direction.EAST)&&be.connectionOpen(Direction.WEST)&&!be.connectionOpen(Direction.NORTH)&&!be.connectionOpen(Direction.UP),"Extractor has fixed back/front sockets that rotate with placement");
        h.assertTrue(be.workSlots(Direction.UP).length==0&&!be.canPlaceItem(0,new ItemStack(Items.IRON_INGOT)),"Transport nodes do not advertise fake machine inventory slots");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void oldConfigurationCannotSilentlyDisableSimplifiedMachines(GameTestHelper h){
        h.setBlock(POS,CatalogueContent.BLOCKS.get("plant_cultivator").get());
        var be=(IndustryBlockEntity)h.getBlockEntity(POS);be.stopped=true;be.faceMask=0;be.retain=64;be.filterMode=4;be.fluidFilter="bio";
        be.stock.set(0,new ItemStack(Items.DIRT));be.stock.set(1,new ItemStack(Items.WHEAT_SEEDS));be.setPower(8000);
        be.activeRecipe="plant:test";be.progressTicks=10;be.processTicks=400;be.processCost=8000;be.processPaid=200;
        var saved=be.saveWithFullMetadata(h.getLevel().registryAccess());
        be.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(!be.stopped&&be.faceMask==63&&be.retain==0&&be.filterMode==0&&be.fluidFilter.isEmpty(),"Obsolete switches reset to automatic defaults");
        h.assertTrue(be.getPower()==8000&&be.progressTicks==10&&be.processPaid==200&&be.stock.get(1).is(Items.WHEAT_SEEDS),"Migration preserves inventory, stored energy and reserved work");
        h.assertTrue(be.workSlots(Direction.UP).length==3&&be.workSlots(Direction.DOWN).length==4&&be.workSlots(Direction.NORTH).length==0,"Cultivator has fixed top input and bottom output");
        var menu=new IndustryMenu(0,new net.minecraft.world.entity.player.Inventory(net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel())),be,be.processData);
        for(int id:new int[]{0,1,2,3,4,5,6,7,10,11,12,13,14,15})h.assertTrue(!menu.clickMenuButton(null,id),"Cultivator rejects removed control packet "+id);
        h.assertTrue(!menu.hasItemFilter()&&menu.visible(0)&&menu.visible(1)&&menu.visible(2)&&!menu.visible(3),"Only actual cultivation slots are shown");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void primitiveWorkstationsPauseAndResumeWithRealRedstone(GameTestHelper h){
        h.setBlock(POS,StarfallBlueprint.PREPARATION.get());
        var prep=(PreparationBlockEntity)h.getBlockEntity(POS);prep.water=1000;prep.setPower(320000);prep.setItem(0,new ItemStack(StarfallBlueprint.RAW.get()));
        PreparationBlockEntity.tick(h.getLevel(),prep.getBlockPos(),prep.getBlockState(),prep);
        h.assertTrue(prep.progress==1,"Basic preparation reserves and begins the batch");
        long power=prep.getPower();h.setBlock(POS.east(),Blocks.REDSTONE_BLOCK);
        PreparationBlockEntity.tick(h.getLevel(),prep.getBlockPos(),prep.getBlockState(),prep);
        h.assertTrue(prep.progress==1&&prep.getPower()==power&&prep.state==7,"Redstone freezes the basic preparation batch without charging");
        h.setBlock(POS.east(),Blocks.AIR);PreparationBlockEntity.tick(h.getLevel(),prep.getBlockPos(),prep.getBlockState(),prep);
        h.assertTrue(prep.progress==2,"Removing signal resumes the reserved preparation batch");
        h.setBlock(POS,StarfallBlueprint.ALLOY_FURNACE.get());
        var alloy=(AlloyFurnaceBlockEntity)h.getBlockEntity(POS);alloy.setItem(0,new ItemStack(Items.IRON_INGOT,3));alloy.setItem(1,new ItemStack(Items.COAL));alloy.setItem(2,new ItemStack(Items.COAL));
        AlloyFurnaceBlockEntity.tick(h.getLevel(),alloy.getBlockPos(),alloy.getBlockState(),alloy);
        h.assertTrue(alloy.progress==1,"Alloy furnace starts work");int fuel=alloy.fuelTicks;
        h.setBlock(POS.east(),Blocks.REDSTONE_BLOCK);AlloyFurnaceBlockEntity.tick(h.getLevel(),alloy.getBlockPos(),alloy.getBlockState(),alloy);
        h.assertTrue(alloy.progress==1&&alloy.fuelTicks==fuel&&alloy.state==5,"Redstone preserves alloy progress and fuel");
        h.setBlock(POS.east(),Blocks.AIR);AlloyFurnaceBlockEntity.tick(h.getLevel(),alloy.getBlockPos(),alloy.getBlockState(),alloy);
        h.assertTrue(alloy.progress==2,"Alloy furnace resumes the reserved batch");h.succeed();
    }
}
