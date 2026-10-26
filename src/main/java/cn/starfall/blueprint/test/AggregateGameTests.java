package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class AggregateGameTests {
    private static ServerPlayer player(GameTestHelper h,BlockPos pos){var p=FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AggregateTest"));p.setGameMode(GameType.SURVIVAL);p.moveTo(Vec3.atCenterOf(pos).add(0,1,2));p.getInventory().clearContent();return p;}
    private static IndustryBlockEntity machine(GameTestHelper h,String kind,int index){
        var pos=h.absolutePos(new BlockPos(200+index*8,50,200));var level=h.getLevel();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk((pos.getX()>>4)+x,(pos.getZ()>>4)+z);
        var state=CatalogueContent.BLOCKS.get(kind).get().defaultBlockState();
        for(int part=0;part<4;part++){var target=pos.offset(LargeMachineBlock.offset(Direction.NORTH,part));var foundation=kind.equals("quarry")?((BlockItem)CatalogueContent.ITEMS.get("resonance_base_stone").get()).getBlock():Blocks.STONE;level.setBlock(target.below(),foundation.defaultBlockState(),3);level.setBlock(target,state.setValue(LargeMachineBlock.PART,part),3);}
        return (IndustryBlockEntity)level.getBlockEntity(pos);
    }
    private static int output(IndustryBlockEntity be,Item item){return be.stock.subList(9,13).stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}
    private static void process(IndustryBlockEntity be,int n){for(int i=0;i<n;i++)IndustryProcessing.tick(be);}
    private static ConstructionPlan plan(String file){try(var in=AggregateGameTests.class.getResourceAsStream("/assets/starfall_blueprint/construction_blueprints/"+file)){return ConstructionPlan.parse(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){throw new AssertionError(e);}}
    @GameTest(template="empty")
    public static void quarryRejectsOrdinaryStoneAndNeedsResonanceBasesAndSpace(GameTestHelper h){
        var be=machine(h,"quarry",0);var level=h.getLevel();be.setPower(8000);var floor=be.getBlockPos().below();
        var base=((BlockItem)CatalogueContent.ITEMS.get("resonance_base_stone").get()).getBlock();
        for(int part=0;part<4;part++)level.setBlock(floor.offset(LargeMachineBlock.offset(Direction.NORTH,part)),Blocks.STONE.defaultBlockState(),3);
        process(be,200);h.assertTrue(be.idle()&&be.getPower()==8000&&be.status==10,"Ordinary stone is not a resonance foundation");
        for(int part=0;part<4;part++)level.setBlock(floor.offset(LargeMachineBlock.offset(Direction.NORTH,part)),base.defaultBlockState(),3);
        level.setBlock(be.getBlockPos().north(),Blocks.STONE.defaultBlockState(),3);process(be,200);
        h.assertTrue(be.idle()&&be.getPower()==8000&&be.status==11,"Front work area must remain empty");level.removeBlock(be.getBlockPos().north(),false);
        process(be,200);h.assertTrue(output(be,Items.COBBLESTONE)==4&&be.getPower()==4000&&level.getBlockState(floor).is(base),"Exactly four renewable cobble for 4000 XH without consuming the resonance base");
        process(be,200);h.assertTrue(output(be,Items.COBBLESTONE)==8&&be.getPower()==0,"Repeat batch uses exact additional energy");process(be,200);h.assertTrue(output(be,Items.COBBLESTONE)==8,"Unpowered quarry cannot generate");h.succeed();
    }
    @GameTest(template="empty")
    public static void crusherAllTenRecipesRetainExactInputsOutputsAndSavedReservations(GameTestHelper h){
        int index=0;var definitions=CatalogueContent.RECIPES.values().stream().filter(r->r.get("station").getAsString().equals("crusher")).toList();h.assertTrue(definitions.size()==10,"Ten useful crushing jobs");
        for(var r:definitions){var be=machine(h,"crusher",index++);var input=r.getAsJsonArray("inputs").get(0);var out=r.getAsJsonArray("outputs").get(0);
            be.stock.set(0,CatalogueContent.stack(CatalogueContent.key(input),1));be.setPower(2000);process(be,7);
            h.assertTrue(be.stock.get(0).isEmpty()&&be.progressTicks==7,"Inputs reserved exactly once: "+r.get("id"));var tag=be.saveWithFullMetadata(h.getLevel().registryAccess());be.loadWithComponents(tag,h.getLevel().registryAccess());process(be,33);
            h.assertTrue(be.idle()&&be.getPower()==0&&output(be,CatalogueContent.item(CatalogueContent.key(out)))==CatalogueContent.qty(out),"Exact saved output/energy: "+r.get("id"));process(be,40);h.assertTrue(be.idle(),"No repeat without new material");
        }
        h.assertTrue(CatalogueContent.qty(CatalogueContent.recipe("CR-R03").getAsJsonArray("outputs").get(0))==6&&CatalogueContent.qty(CatalogueContent.recipe("CR-R05").getAsJsonArray("outputs").get(0))==9,"Bones are enhanced but reversible bone blocks cannot multiply bone meal");h.succeed();
    }
    @GameTest(template="empty")
    public static void allFourPartsSharePortsAndRedstonePausesWithoutLosingTheBatch(GameTestHelper h){
        var be=machine(h,"crusher",20);var level=h.getLevel();int accepted=0;
        for(int part=0;part<4;part++){var pos=be.getBlockPos().offset(LargeMachineBlock.offset(Direction.NORTH,part));var port=level.getCapability(Capabilities.EnergyStorage.BLOCK,pos,Direction.WEST);h.assertTrue(port!=null,"Every part exposes shared power");accepted+=port.receiveEnergy(1000,false);}
        h.assertTrue(accepted==2048&&be.getPower()==2048,"Four proxy ports cannot multiply the owner's per-tick input budget");
        var items=level.getCapability(Capabilities.ItemHandler.BLOCK,be.getBlockPos().east().south(),Direction.UP);h.assertTrue(items!=null&&net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(items,new ItemStack(Items.BONE),false).isEmpty()&&be.stock.get(0).is(Items.BONE),"Proxy item input reaches controller stock");
        for(int i=0;i<10;i++)IndustryBlockEntity.tick(level,be.getBlockPos(),be.getBlockState(),be);long power=be.getPower();
        level.setBlock(be.getBlockPos().east(2).south(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        for(int i=0;i<20;i++)IndustryBlockEntity.tick(level,be.getBlockPos(),be.getBlockState(),be);
        h.assertTrue(be.progressTicks==10&&be.getPower()==power,"Redstone touching a shell pauses the single batch");level.removeBlock(be.getBlockPos().east(2).south(),false);
        for(int i=0;i<30;i++)IndustryBlockEntity.tick(level,be.getBlockPos(),be.getBlockState(),be);h.assertTrue(output(be,Items.BONE_MEAL)==6&&be.getPower()==48,"Resume settles the same reserved batch once");h.succeed();
    }
    @GameTest(template="empty")
    public static void placementNeedsFourFreeBlocksAndBreakingAShellReturnsOneSavedMachine(GameTestHelper h){
        var old=machine(h,"crusher",22);var root=old.getBlockPos();var level=h.getLevel();level.removeBlock(root,false);
        var player=player(h,root);var stack=CatalogueContent.stack("crusher",2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        level.setBlock(root.east().south(),Blocks.BEDROCK.defaultBlockState(),3);
        var hit=new BlockHitResult(Vec3.atCenterOf(root.below()),Direction.UP,root.below(),false);
        player.gameMode.useItemOn(player,level,stack,InteractionHand.MAIN_HAND,hit);
        h.assertTrue(stack.getCount()==2&&level.isEmptyBlock(root),"Obstructed 2x2 placement is atomic and does not spend the machine");level.removeBlock(root.east().south(),false);
        player.gameMode.useItemOn(player,level,stack,InteractionHand.MAIN_HAND,hit);
        var be=LargeMachineBlock.controller(level,root);h.assertTrue(be!=null&&stack.getCount()==1,"One real block-item placement creates four parts");
        be.stock.set(0,new ItemStack(Items.BONE,3));be.setPower(12345);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));
        h.assertTrue(player.gameMode.destroyBlock(root.east().south()),"Player can break a non-controller shell");
        for(int part=0;part<4;part++)h.assertTrue(level.isEmptyBlock(root.offset(LargeMachineBlock.offset(Direction.NORTH,part))),"Breaking any part removes the complete footprint");
        var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(4)).stream().map(ItemEntity::getItem).filter(s->s.is(CatalogueContent.item("crusher"))).toList();
        h.assertTrue(drops.stream().mapToInt(ItemStack::getCount).sum()==1,"Four parts return exactly one machine item");
        var copy=new IndustryBlockEntity(root,CatalogueContent.BLOCKS.get("crusher").get().defaultBlockState());copy.loadWithComponents(drops.getFirst().get(DataComponents.BLOCK_ENTITY_DATA).copyTag(),level.registryAccess());
        h.assertTrue(copy.stock.get(0).getCount()==3&&copy.getPower()==12345,"Breaking a shell retains the controller's actual inventory and energy");h.succeed();
    }
    @GameTest(template="empty")
    public static void multiblockBlueprintsChargeOneMachineAndRejectOrphanShells(GameTestHelper h){
        var state=CatalogueContent.BLOCKS.get("crusher").get().defaultBlockState();List<ConstructionPlan.Cell> cells=new ArrayList<>();
        for(int part=0;part<4;part++)cells.add(new ConstructionPlan.Cell(new BlockPos(0,0,-1).offset(LargeMachineBlock.offset(Direction.NORTH,part)),state.setValue(LargeMachineBlock.PART,part),"",false));
        var design=new ConstructionPlan("四格破碎机",cells);h.assertTrue(design.materials().get(CatalogueContent.item("crusher"))==1,"Complete four-block machine costs one item");
        boolean rejected=false;try{new ConstructionPlan("孤立外壳",List.of(cells.get(1)));}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"Orphan shells cannot be imported as free structures");
        for(int rotation=0;rotation<4;rotation++){
            var site=machine(h,"quarry",30+rotation).getBlockPos();h.getLevel().removeBlock(site,false);h.getLevel().setBlock(site,Blueprints.BUILDER.get().defaultBlockState(),3);var builder=(BlueprintBuilderEntity)h.getLevel().getBlockEntity(site);
            var paper=new ItemStack(Blueprints.PLAN.get());Blueprints.write(paper,design);builder.setItem(0,paper);builder.turns=rotation;builder.setItem(1,CatalogueContent.stack("crusher",1));
            h.assertTrue(builder.build(player(h,site))&&builder.getItem(1).isEmpty(),"Blueprint constructs a rotated 2x2 footprint using exactly one machine");
            var controller=LargeMachineBlock.controller(h.getLevel(),builder.target(cells.get(0)));h.assertTrue(controller!=null&&AggregateMachines.ready(controller),"Every rotated part resolves the same controller");
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=2400)
    public static void handbookAfkBlueprintProducesConcreteFromWindAndRetainedLilyOnly(GameTestHelper h){
        var level=h.getLevel();var site=h.absolutePos(new BlockPos(2400,180,2400));var design=plan("L26.sfbp.json");Set<ChunkPos> chunks=new HashSet<>();
        for(int x=-1;x<=2;x++)for(int z=-2;z<=1;z++){var chunk=new ChunkPos((site.getX()>>4)+x,(site.getZ()>>4)+z);level.setChunkForced(chunk.x,chunk.z,true);chunks.add(chunk);}
        level.setBlock(site,Blueprints.BUILDER.get().defaultBlockState(),3);var builder=(BlueprintBuilderEntity)level.getBlockEntity(site);var paper=new ItemStack(Blueprints.PLAN.get());Blueprints.write(paper,design);builder.setItem(0,paper);
        int slot=1;for(var e:builder.required().entrySet()){int remaining=e.getValue();while(remaining>0){int n=Math.min(64,remaining);builder.setItem(slot++,new ItemStack(e.getKey(),n));remaining-=n;}}
        h.assertTrue(builder.build(player(h,site)),"The handbook AFK layout must actually construct");
        IndustryBlockEntity cultivator=null;net.minecraft.world.Container destination=null;List<IndustryBlockEntity> machines=new ArrayList<>();
        for(var cell:design.blocks()){
            var entity=level.getBlockEntity(builder.target(cell));if(entity instanceof IndustryBlockEntity machine){machines.add(machine);if(machine.kind().equals("plant_cultivator")){cultivator=machine;machine.stock.set(0,new ItemStack(Items.DIRT));machine.stock.set(1,new ItemStack(Items.LILY_OF_THE_VALLEY));}}
            if(cell.pos().equals(new BlockPos(6,1,-7)))destination=(net.minecraft.world.Container)entity;
        }
        var plant=cultivator;var chest=destination;h.assertTrue(plant!=null&&chest!=null,"AFK blueprint has a retained lily source and concrete output chest");
        h.runAfterDelay(1000,()->{if(count(chest,Items.WHITE_CONCRETE)<16)for(var machine:machines)org.slf4j.LoggerFactory.getLogger(AggregateGameTests.class).info("AFK probe {} {} power={} status={} input={} output={} water={}",machine.kind(),machine.getBlockPos(),machine.getPower(),machine.status,machine.stock.subList(0,9),machine.stock.subList(9,13),machine.tanks[0].getFluidAmount());});
        h.startSequence().thenWaitUntil(()->h.assertTrue(count(chest,Items.WHITE_CONCRETE)>=16,"Wind, dew, quarries, crushing, retained lily and vanilla recipes must produce at least two concrete batches without supplied fuel or raw materials"))
                .thenExecute(()->{try{h.assertTrue(plant.stock.get(0).is(Items.DIRT)&&plant.stock.get(1).is(Items.LILY_OF_THE_VALLEY),"Environment and sample are retained during continuous production");for(int i=0;i<chest.getContainerSize();i++)h.assertTrue(chest.getItem(i).isEmpty()||chest.getItem(i).is(Items.WHITE_CONCRETE),"Only finished concrete reaches the output chest");org.slf4j.LoggerFactory.getLogger(AggregateGameTests.class).info("AFK L26 verified: {} concrete, retained lily, no supplied energy, water, fuel, sand, gravel or dye",count(chest,Items.WHITE_CONCRETE));}finally{for(var chunk:chunks)level.setChunkForced(chunk.x,chunk.z,false);}}).thenSucceed();
    }
    @GameTest(template="empty")
    public static void placementProtectionRestoresAllFourPartsAndTheMachineItem(GameTestHelper h){
        var be=machine(h,"crusher",60);var root=be.getBlockPos();var level=h.getLevel();level.removeBlock(root,false);var player=player(h,root);var stack=CatalogueContent.stack("crusher",1);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        boolean[] fired={false};java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> deny=event->{if(event.getPos().equals(root)){fired[0]=true;event.setCanceled(true);}};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try{player.gameMode.useItemOn(player,level,stack,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(root.below()),Direction.UP,root.below(),false));}
        finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny);}
        h.assertTrue(fired[0]&&stack.getCount()==1,"Normal NeoForge placement protection cancels the whole item use");
        for(int part=0;part<4;part++)h.assertTrue(level.isEmptyBlock(root.offset(LargeMachineBlock.offset(Direction.NORTH,part))),"Cancelled placement restores every footprint cell");h.succeed();
    }
    @GameTest(template="empty")
    public static void blueprintRollbackKeepsAnExistingControllerAndItsInventory(GameTestHelper h){
        var old=machine(h,"quarry",65);var site=old.getBlockPos();var level=h.getLevel();level.removeBlock(site,false);level.setBlock(site,Blueprints.BUILDER.get().defaultBlockState(),3);var builder=(BlueprintBuilderEntity)level.getBlockEntity(site);
        var state=CatalogueContent.BLOCKS.get("crusher").get().defaultBlockState();List<ConstructionPlan.Cell> cells=new ArrayList<>();for(int part=0;part<4;part++)cells.add(new ConstructionPlan.Cell(new BlockPos(0,0,-1).offset(LargeMachineBlock.offset(Direction.NORTH,part)),state.setValue(LargeMachineBlock.PART,part),"",false));
        var paper=new ItemStack(Blueprints.PLAN.get());Blueprints.write(paper,new ConstructionPlan("修复完整大型机器",cells));builder.setItem(0,paper);var root=builder.target(cells.get(0));level.setBlock(root,state,3);var owner=(IndustryBlockEntity)level.getBlockEntity(root);owner.stock.set(0,new ItemStack(Items.BONE,5));
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> deny=event->{if(event.getPos().equals(builder.target(cells.get(2))))event.setCanceled(true);};net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try{h.assertTrue(!builder.build(player(h,site)),"Protection must stop a partially completed footprint repair");}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny);}
        h.assertTrue(level.getBlockEntity(root)==owner&&owner.stock.get(0).getCount()==5,"Rollback never dismantles an existing controller or loses its inventory");for(int part=1;part<4;part++)h.assertTrue(level.isEmptyBlock(builder.target(cells.get(part))),"All newly added shells roll back");
        h.assertTrue(builder.required().isEmpty()&&builder.build(player(h,site))&&owner.stock.get(0).getCount()==5&&AggregateMachines.ready(owner),"The retained machine can have its missing shells repaired without charging three extra machine items");h.succeed();
    }
    private static int count(net.minecraft.world.Container container,Item item){int n=0;for(int i=0;i<container.getContainerSize();i++)if(container.getItem(i).is(item))n+=container.getItem(i).getCount();return n;}
}
