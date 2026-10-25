package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class ProductionGameTests {
    private static PreparationBlockEntity bench(GameTestHelper h) {
        h.setBlock(new BlockPos(1,1,1), StarfallBlueprint.PREPARATION.get());
        return (PreparationBlockEntity)h.getBlockEntity(new BlockPos(1,1,1));
    }
    private static void ticks(PreparationBlockEntity be, int count) {
        for (int i = 0; i < count; i++) PreparationBlockEntity.tick(be.getLevel(), be.getBlockPos(), be.getBlockState(), be);
    }
    private static ItemStack ore(int grade, int count) {
        ItemStack s = new ItemStack(StarfallBlueprint.RAW.get(), count); s.set(StarfallBlueprint.BATCH.get(), MaterialBatch.ore(grade,40)); return s;
    }
    private static void supply(PreparationBlockEntity b, int grade, int count) { b.setItem(0,ore(grade,count)); b.setItem(1,new ItemStack(Items.COAL)); b.water=4000; }

    @GameTest(template = "empty")
    public static void oneCoalEightBatches(GameTestHelper h) {
        PreparationBlockEntity b=bench(h); supply(b,12,8);
        int output=0;
        for (int i=0;i<8;i++) { ticks(b,200); output+=b.removeItem(2,64).getCount(); }
        h.assertTrue(output==80,"One coal must produce eight 10-piece batches");
        h.assertTrue(b.energy==0 && b.water==2000 && b.getItem(1).isEmpty(),"Fuel/water must settle exactly once");
        h.assertTrue(b.getItem(3).getCount()==8,"One wet-tailings parcel per batch");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void lowGradeConservesMass(GameTestHelper h) {
        PreparationBlockEntity b=bench(h); supply(b,6,1); ticks(b,200);
        MaterialBatch c=b.getItem(2).get(StarfallBlueprint.BATCH.get()), t=b.getItem(3).get(StarfallBlueprint.BATCH.get());
        h.assertTrue(b.getItem(2).getCount()==5,"G6 must give 5 pieces");
        h.assertTrue(c.dryMilliM()*5+t.dryMilliM()==1_000_000,"Dry mass conserved");
        h.assertTrue(c.phaseMilliM()*5+t.phaseMilliM()==60_000,"Phase component conserved");
        h.assertTrue(c.integrity()==38,"Preparation reduces integrity by 2"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void connectorSavesTenPercent(GameTestHelper h) {
        PreparationBlockEntity b=bench(h); supply(b,12,8); b.setItem(4,new ItemStack(StarfallBlueprint.CONNECTOR.get()));
        for (int i=0;i<8;i++) { ticks(b,200); b.removeItem(2,64); }
        h.assertTrue(b.energy==32000,"8 upgraded batches use 288kXH, preserving 32kXH"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void dryAndBlockedMachineDoesNotConsume(GameTestHelper h) {
        PreparationBlockEntity b=bench(h); supply(b,12,2); b.water=0; ticks(b,20);
        h.assertTrue(b.getItem(0).getCount()==2 && b.getItem(1).getCount()==1 && b.progress==0,"No water: no inputs consumed");
        b.water=1000;for(int i=9;i<13;i++)b.stock.set(i,new ItemStack(Items.COBBLESTONE,64)); ticks(b,20);
        h.assertTrue(b.getItem(0).getCount()==2 && b.water==1000,"Blocked output: no reservation");
        for(int i=9;i<13;i++)b.stock.set(i,ItemStack.EMPTY);ticks(b,100); long energy=b.energy;
        for(int i=9;i<13;i++)b.stock.set(i,new ItemStack(Items.COBBLESTONE,64));ticks(b,20);
        h.assertTrue(b.progress==100 && b.energy==energy,"Backpressure pauses work without energy use");
        for(int i=9;i<13;i++)b.stock.set(i,ItemStack.EMPTY);ticks(b,100);
        h.assertTrue(b.getItem(2).getCount()==10 && b.water==750,"Resume yields exactly one batch"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void reloadResumesReservedBatch(GameTestHelper h) {
        PreparationBlockEntity b=bench(h); supply(b,12,1); ticks(b,73);
        var saved=b.saveWithoutMetadata(h.getLevel().registryAccess());
        b.loadWithComponents(saved,h.getLevel().registryAccess()); ticks(b,127);
        h.assertTrue(b.getItem(2).getCount()==10 && b.getItem(3).getCount()==1,"Reloaded batch finishes once");
        h.assertTrue(b.water==3750 && b.energy==280000 && b.getItem(0).isEmpty(),"Reload cannot recharge or duplicate inputs");
        ticks(b,200); h.assertTrue(b.getItem(2).getCount()==10,"Finished batch cannot settle twice"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void qualityAndUpgradeCrafting(GameTestHelper h) {
        h.assertTrue(h.getLevel().getRecipeManager().byKey(StarfallBlueprint.id("connector")).isPresent(),"Data pack must load custom connector recipe");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(StarfallBlueprint.id("preparation_bench")).isPresent(),"Data pack must load survival machine recipe");
        BasicCrystalRecipe connector=new BasicCrystalRecipe(CraftingBookCategory.MISC,"connector");
        ItemStack crystal=new ItemStack(StarfallBlueprint.CRYSTAL.get());
        var good=CraftingInput.of(2,1,List.of(crystal,new ItemStack(Items.COPPER_INGOT)));
        h.assertTrue(connector.matches(good,h.getLevel()),"Q0 is accepted");
        crystal.set(StarfallBlueprint.BATCH.get(),MaterialBatch.crystal(19));
        h.assertTrue(!connector.matches(good,h.getLevel()),"Low integrity is rejected");
        ItemStack pick=new ItemStack(Items.IRON_PICKAXE);pick.setDamageValue(100);
        BasicCrystalRecipe reinforce=new BasicCrystalRecipe(CraftingBookCategory.MISC,"reinforce_tool");
        var input=CraftingInput.of(2,1,List.of(pick,new ItemStack(StarfallBlueprint.TOOL_BRACE.get())));
        h.assertTrue(reinforce.matches(input,h.getLevel()),"Unmodified pick accepted");
        ItemStack upgraded=reinforce.assemble(input,h.getLevel().registryAccess());
        h.assertTrue(upgraded.getMaxDamage()==300 && upgraded.getDamageValue()==120,"Durability increases 20%, existing damage fraction preserved");
        h.assertTrue(!reinforce.matches(CraftingInput.of(2,1,List.of(upgraded,new ItemStack(StarfallBlueprint.TOOL_BRACE.get()))),h.getLevel()),"Repeat upgrade rejected");h.succeed();
    }
    @GameTest(template = "empty")
    public static void automationPorts(GameTestHelper h) {
        PreparationBlockEntity b=bench(h);
        h.assertTrue(b.getSlotsForFace(Direction.UP)[0]==0,"Top is ore input");
        h.assertTrue(!b.canPlaceItem(2,ore(12,1)) && !b.canTakeItemThroughFace(4,new ItemStack(StarfallBlueprint.CONNECTOR.get()),Direction.DOWN),"Automation cannot inject output or extract installed connector");
        var port=b.waterPort();
        h.assertTrue(port.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,5000),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE)==4000 && b.water==0,"Simulation does not fill");
        port.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,5000),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        h.assertTrue(b.water==4000,"Water input clamps to 4000mB");h.succeed();
    }
    @GameTest(template = "empty")
    public static void machineItemKeepsInFlightBatch(GameTestHelper h) {
        PreparationBlockEntity b=bench(h); supply(b,12,1); ticks(b,73);
        var pos=b.getBlockPos();
        var loot=new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,net.minecraft.world.phys.Vec3.atCenterOf(pos))
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY,b)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,new ItemStack(Items.IRON_PICKAXE));
        ItemStack dropped=StarfallBlueprint.PREPARATION.get().getDrops(b.getBlockState(),loot).getFirst();
        h.setBlock(new BlockPos(1,1,1),net.minecraft.world.level.block.Blocks.AIR);
        h.setBlock(new BlockPos(1,0,1),net.minecraft.world.level.block.Blocks.STONE);
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false);
        ((BlockItem)dropped.getItem()).place(new net.minecraft.world.item.context.BlockPlaceContext(h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,dropped,hit));
        var restored=(PreparationBlockEntity)h.getLevel().getBlockEntity(pos);
        h.assertTrue(restored!=null && restored.progress==73 && restored.water==3750,"Placing actual machine item restores reserved state");
        ticks(restored,127);
        h.assertTrue(restored.getItem(2).getCount()==10 && restored.energy==280000,"Relocation does not duplicate fuel or product"); h.succeed();
    }
    @GameTest(template = "empty", skyAccess = true)
    public static void rainNeedsRealWeatherAndOpenSky(GameTestHelper h) {
        var local=new BlockPos(1,1,1);h.setBlock(local,StarfallBlueprint.RAIN_COLLECTOR.get());
        var be=(CampWaterBlockEntity)h.getBlockEntity(local);var level=h.getLevel();
        try {
            level.setWeatherParameters(6000,0,false,false);level.setRainLevel(0);
            for(int i=0;i<20;i++) CampWaterBlockEntity.tick(level,be.getBlockPos(),be.getBlockState(),be);
            h.assertTrue(be.tank.isEmpty(),"Clear weather produces no water");
            level.setWeatherParameters(0,6000,true,false);level.setRainLevel(1);
            h.assertTrue(level.isRainingAt(be.getBlockPos().above()),"Test fixture must be genuinely rainy and outdoors");
            for(int i=0;i<20;i++) CampWaterBlockEntity.tick(level,be.getBlockPos(),be.getBlockState(),be);
            h.assertTrue(be.tank.getFluidAmount()==100,"One second of rain collects exactly 100mB");
            h.setBlock(local.above(2),net.minecraft.world.level.block.Blocks.STONE);
            for(int i=0;i<20;i++) CampWaterBlockEntity.tick(level,be.getBlockPos(),be.getBlockState(),be);
            h.assertTrue(be.tank.getFluidAmount()==100,"Roof stops collection");
            h.setBlock(local.above(2),net.minecraft.world.level.block.Blocks.AIR);
            be.tank.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,3900),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            for(int i=0;i<20;i++) CampWaterBlockEntity.tick(level,be.getBlockPos(),be.getBlockState(),be);
            h.assertTrue(be.tank.getFluidAmount()==4000,"Full collector remains at capacity");
            h.succeed();
        } finally { level.setWeatherParameters(6000,0,false,false);level.setRainLevel(0); }
    }
    @GameTest(template = "empty")
    public static void upgradingTankPreservesStoredWater(GameTestHelper h) {
        var local=new BlockPos(1,1,1);h.setBlock(local,StarfallBlueprint.WATER_TANK.get());
        var be=(CampWaterBlockEntity)h.getBlockEntity(local);
        be.tank.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,2500),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        ItemStack tank=new ItemStack(StarfallBlueprint.TANK_ITEM.get());
        tank.set(DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(be.saveWithFullMetadata(h.getLevel().registryAccess())));
        var input=CraftingInput.of(3,3,List.of(new ItemStack(Items.IRON_INGOT),new ItemStack(Items.GLASS),new ItemStack(Items.IRON_INGOT),new ItemStack(Items.GLASS),tank,ItemStack.EMPTY,new ItemStack(Items.OAK_PLANKS),new ItemStack(Items.OAK_PLANKS),new ItemStack(Items.OAK_PLANKS)));
        var recipe=new RainCollectorRecipe(CraftingBookCategory.MISC);
        h.assertTrue(recipe.matches(input,h.getLevel()),"Tank upgrade accepts documented budget");
        ItemStack result=recipe.assemble(input,h.getLevel().registryAccess());
        h.assertTrue(result.get(DataComponents.BLOCK_ENTITY_DATA).equals(tank.get(DataComponents.BLOCK_ENTITY_DATA)),"Upgrade transfers the existing water payload");
        var restored=new CampWaterBlockEntity(be.getBlockPos(),StarfallBlueprint.RAIN_COLLECTOR.get().defaultBlockState());
        restored.loadWithComponents(result.get(DataComponents.BLOCK_ENTITY_DATA).copyTag(),h.getLevel().registryAccess());
        h.assertTrue(restored.tank.getFluidAmount()==2500,"Stored water survives tank-to-collector upgrade");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void collectorFeedsBenchAndHonorsBackpressure(GameTestHelper h) {
        var b=bench(h);b.water=3900;
        var local=new BlockPos(1,2,1);h.setBlock(local,StarfallBlueprint.RAIN_COLLECTOR.get());
        var collector=(CampWaterBlockEntity)h.getBlockEntity(local);
        collector.tank.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        h.startSequence()
                .thenWaitUntil(()->h.assertTrue(b.water==4000,"Collector must feed the bench below"))
                .thenExecute(()->h.assertTrue(collector.tank.getFluidAmount()==900,"Exactly 100mB transferred"))
                .thenIdle(21)
                .thenExecute(()->h.assertTrue(b.water==4000 && collector.tank.getFluidAmount()==900,"Full receiver backpressures without deleting water"))
                .thenSucceed();
    }
}
