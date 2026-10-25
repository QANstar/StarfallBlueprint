package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class PressGameTests {
    private static IndustryBlockEntity machine(GameTestHelper h){var pos=new BlockPos(1,2,1);h.setBlock(pos,CatalogueContent.BLOCKS.get("press").get());return (IndustryBlockEntity)h.getBlockEntity(pos);}
    private static int output(IndustryBlockEntity be,Item item){return be.stock.subList(9,13).stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}
    @GameTest(template="empty")
    public static void manualPlateStartupAndOneBlockPressConserveBothMetals(GameTestHelper h){
        var input=CraftingInput.of(1,2,List.of(new ItemStack(Items.IRON_INGOT),new ItemStack(Items.IRON_INGOT)));
        var manual=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input,h.getLevel().registryAccess());
        h.assertTrue(manual.is(StarfallBlueprint.structural("iron_plate"))&&manual.getCount()==2,"Two vertical iron ingots still make two plates without any machine");
        var be=machine(h);be.stock.set(0,new ItemStack(Items.IRON_INGOT,2));be.stock.set(1,new ItemStack(Items.COPPER_INGOT));be.setPower(3000);
        for(int i=0;i<120;i++)IndustryProcessing.tick(be);
        h.assertTrue(output(be,StarfallBlueprint.structural("iron_plate"))==2&&output(be,StarfallBlueprint.structural("copper_plate"))==1&&be.getPower()==0&&be.stock.subList(0,9).stream().allMatch(ItemStack::isEmpty),"Single-block press automatically converts three ingots to exactly three plates for 3000 XH");
        h.assertTrue(!be.getBlockState().hasProperty(LargeMachineBlock.PART),"Press is a one-block machine");h.succeed();
    }
    @GameTest(template="empty")
    public static void pressPortsAndPausedSavedBatchNeverConsumeTwice(GameTestHelper h){
        var be=machine(h);var level=h.getLevel();var top=level.getCapability(Capabilities.ItemHandler.BLOCK,be.getBlockPos(),Direction.UP);var bottom=level.getCapability(Capabilities.ItemHandler.BLOCK,be.getBlockPos(),Direction.DOWN);var side=level.getCapability(Capabilities.ItemHandler.BLOCK,be.getBlockPos(),Direction.EAST);
        h.assertTrue(top!=null&&bottom!=null&&side!=null&&side.getSlots()==0&&top.insertItem(0,new ItemStack(Items.COAL),false).is(Items.COAL),"Fixed ports reject fuel and unsupported input");
        h.assertTrue(top.insertItem(0,new ItemStack(Items.IRON_INGOT),false).isEmpty(),"Top port feeds iron");be.setPower(1000);
        for(int i=0;i<10;i++)IndustryBlockEntity.tick(level,be.getBlockPos(),be.getBlockState(),be);
        level.setBlock(be.getBlockPos().east(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);for(int i=0;i<20;i++)IndustryBlockEntity.tick(level,be.getBlockPos(),be.getBlockState(),be);
        h.assertTrue(be.progressTicks==10&&be.getPower()==750&&be.stock.get(0).isEmpty(),"Redstone stops the reserved batch without spending another ingot or energy");
        var tag=be.saveWithFullMetadata(level.registryAccess());be.loadWithComponents(tag,level.registryAccess());level.removeBlock(be.getBlockPos().east(),false);
        for(int i=9;i<13;i++)be.stock.set(i,new ItemStack(Items.COBBLESTONE,64));for(int i=0;i<20;i++)IndustryProcessing.tick(be);
        h.assertTrue(be.progressTicks==10&&be.getPower()==750,"Saved batch pauses under output backpressure");for(int i=9;i<13;i++)be.stock.set(i,ItemStack.EMPTY);for(int i=0;i<30;i++)IndustryProcessing.tick(be);
        h.assertTrue(output(be,StarfallBlueprint.structural("iron_plate"))==1&&be.getPower()==0&&bottom.extractItem(0,64,false).getCount()==1&&output(be,StarfallBlueprint.structural("iron_plate"))==0,"Resume and extraction settle exactly one plate for 1000 XH");h.succeed();
    }
    @GameTest(template="empty")
    public static void largeMachineAnimationStateChangesOnlyWithProduction(GameTestHelper h){
        var root=h.absolutePos(new BlockPos(1,2,1));var level=h.getLevel();var state=CatalogueContent.BLOCKS.get("crusher").get().defaultBlockState();
        for(int part=0;part<4;part++)level.setBlock(root.offset(LargeMachineBlock.offset(Direction.NORTH,part)),state.setValue(LargeMachineBlock.PART,part),3);
        var be=(IndustryBlockEntity)level.getBlockEntity(root);be.stock.set(0,new ItemStack(Items.BONE));be.setPower(2000);IndustryBlockEntity.tick(level,root,be.getBlockState(),be);
        for(int part=0;part<4;part++)h.assertTrue(level.getBlockState(root.offset(LargeMachineBlock.offset(Direction.NORTH,part))).getValue(IndustryBlock.ACTIVE),"All four parts select the running texture together");
        level.setBlock(root.east(2).south(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);IndustryBlockEntity.tick(level,root,be.getBlockState(),be);
        h.assertTrue(be.progressTicks==1&&be.getPower()==1950,"Animation state adds no production tick or cost");for(int part=0;part<4;part++)h.assertTrue(!level.getBlockState(root.offset(LargeMachineBlock.offset(Direction.NORTH,part))).getValue(IndustryBlock.ACTIVE),"All four parts switch to a static frame on pause");
        level.removeBlock(root.east(2).south(),false);for(int i=0;i<39;i++)IndustryBlockEntity.tick(level,root,be.getBlockState(),be);
        h.assertTrue(output(be,Items.BONE_MEAL)==6&&be.getPower()==0&&!be.getBlockState().getValue(IndustryBlock.ACTIVE),"Visual transitions do not change output and stop at completion");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=600)
    public static void plateBlueprintRunsUsingWindAndHoppersWithSuppliedIngotsOnly(GameTestHelper h){
        ConstructionPlan design;try(var in=PressGameTests.class.getResourceAsStream("/assets/starfall_blueprint/construction_blueprints/L27.sfbp.json")){design=ConstructionPlan.parse(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){throw new AssertionError(e);}
        var level=h.getLevel();var site=h.absolutePos(new BlockPos(3300,180,3300));Set<net.minecraft.world.level.ChunkPos> chunks=new HashSet<>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var chunk=new net.minecraft.world.level.ChunkPos((site.getX()>>4)+x,(site.getZ()>>4)+z);level.setChunkForced(chunk.x,chunk.z,true);level.getChunk(chunk.x,chunk.z);chunks.add(chunk);}
        level.setBlock(site,Blueprints.BUILDER.get().defaultBlockState(),3);var builder=(BlueprintBuilderEntity)level.getBlockEntity(site);var paper=new ItemStack(Blueprints.PLAN.get());Blueprints.write(paper,design);builder.setItem(0,paper);
        int slot=1;for(var entry:builder.required().entrySet()){int n=entry.getValue();while(n>0){int take=Math.min(64,n);builder.setItem(slot++,new ItemStack(entry.getKey(),take));n-=take;}}
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PressBlueprint"));player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(site));h.assertTrue(builder.build(player),"L27 builds from its exact bill");
        var source=(net.minecraft.world.Container)level.getBlockEntity(builder.origin().offset(4,4,-3));var chest=(net.minecraft.world.Container)level.getBlockEntity(builder.origin().offset(5,1,-3));
        source.setItem(0,new ItemStack(Items.IRON_INGOT,3));source.setItem(1,new ItemStack(Items.COPPER_INGOT,2));
        h.startSequence().thenWaitUntil(()->h.assertTrue(count(chest,StarfallBlueprint.structural("iron_plate"))==3&&count(chest,StarfallBlueprint.structural("copper_plate"))==2,"Real wind generator, hoppers and one-block press must deliver exactly five plates from five ingots without supplied energy or fuel"))
                .thenExecute(()->h.assertTrue(source.isEmpty(),"Five real ingots leave supply chest exactly once")).thenIdle(40)
                .thenExecute(()->{try{h.assertTrue(count(chest,StarfallBlueprint.structural("iron_plate"))==3&&count(chest,StarfallBlueprint.structural("copper_plate"))==2,"Idle line cannot create more plates");}finally{for(var chunk:chunks)level.setChunkForced(chunk.x,chunk.z,false);}}).thenSucceed();
    }
    private static int count(net.minecraft.world.Container c,Item item){int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))n+=c.getItem(i).getCount();return n;}
}
