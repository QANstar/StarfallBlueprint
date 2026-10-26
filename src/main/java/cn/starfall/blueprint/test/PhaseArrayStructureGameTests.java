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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class PhaseArrayStructureGameTests {
    private static ServerPlayer player(GameTestHelper h,BlockPos pos){var p=FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PhaseArrayTest"));p.setGameMode(GameType.SURVIVAL);p.moveTo(Vec3.atCenterOf(pos).add(0,1,2));p.getInventory().clearContent();return p;}
    private static BlockPos site(GameTestHelper h,int index){var pos=h.absolutePos(new BlockPos(3200+index*8,70,3200));for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)h.getLevel().getChunk((pos.getX()>>4)+x,(pos.getZ()>>4)+z);return pos;}
    private static net.minecraft.world.level.block.state.BlockState state(){return CatalogueContent.BLOCKS.get("phase_extractor").get().defaultBlockState();}
    private static void foundation(GameTestHelper h,BlockPos root,Direction facing,String key){var block=((BlockItem)CatalogueContent.ITEMS.get(key).get()).getBlock();for(int part=0;part<4;part++)h.getLevel().setBlock(root.offset(LargeMachineBlock.offset(facing,part)).below(),block.defaultBlockState(),3);}
    private static IndustryBlockEntity machine(GameTestHelper h,int index){var root=site(h,index);foundation(h,root,Direction.NORTH,"phase_matrix_meteor");for(int part=0;part<8;part++)h.getLevel().setBlock(root.offset(LargeMachineBlock.offset(Direction.NORTH,part)),state().setValue(LargeMachineBlock.PART,part),3);return (IndustryBlockEntity)h.getLevel().getBlockEntity(root);}
    private static ConstructionPlan design(){List<ConstructionPlan.Cell> cells=new ArrayList<>();for(int part=0;part<8;part++)cells.add(new ConstructionPlan.Cell(new BlockPos(0,0,-1).offset(LargeMachineBlock.offset(Direction.NORTH,part)),state().setValue(LargeMachineBlock.PART,part),"",false));return new ConstructionPlan("八格相核凝矿阵",cells);}
    private static BlueprintBuilderEntity builder(GameTestHelper h,int index,ConstructionPlan design,int turns){var pos=site(h,index);h.getLevel().setBlock(pos,Blueprints.BUILDER.get().defaultBlockState(),3);var builder=(BlueprintBuilderEntity)h.getLevel().getBlockEntity(pos);var paper=new ItemStack(Blueprints.PLAN.get());Blueprints.write(paper,design);builder.setItem(0,paper);builder.turns=turns;return builder;}

    @GameTest(template="empty")
    public static void placementRejectsUpperObstructionAndShellBreakPreservesOneSavedMachine(GameTestHelper h){
        var root=site(h,0);var level=h.getLevel();foundation(h,root,Direction.NORTH,"phase_matrix_meteor");var p=player(h,root);var stack=CatalogueContent.stack("phase_extractor",2);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var upper=root.offset(LargeMachineBlock.offset(Direction.NORTH,7));level.setBlock(upper,Blocks.BEDROCK.defaultBlockState(),3);var hit=new BlockHitResult(Vec3.atCenterOf(root.below()),Direction.UP,root.below(),false);
        p.gameMode.useItemOn(p,level,stack,InteractionHand.MAIN_HAND,hit);h.assertTrue(stack.getCount()==2&&level.isEmptyBlock(root)&&level.getBlockState(upper).is(Blocks.BEDROCK),"An upper obstruction rejects all eight cells without consuming an item");level.removeBlock(upper,false);
        p.gameMode.useItemOn(p,level,stack,InteractionHand.MAIN_HAND,hit);var owner=LargeMachineBlock.controller(level,upper);h.assertTrue(owner!=null&&stack.getCount()==1&&AggregateMachines.ready(owner),"One machine item places eight real blocks");
        owner.stock.set(9,new ItemStack(Items.DIAMOND,3));owner.setPower(12345);owner.tanks[0].fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER,750),IFluidHandler.FluidAction.EXECUTE);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));h.assertTrue(p.gameMode.destroyBlock(upper),"Upper shell is breakable");
        for(int part=0;part<8;part++)h.assertTrue(level.isEmptyBlock(root.offset(LargeMachineBlock.offset(Direction.NORTH,part))),"Breaking upper shell dismantles both layers");
        var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(4)).stream().map(ItemEntity::getItem).filter(s->s.is(CatalogueContent.item("phase_extractor"))).toList();h.assertTrue(drops.stream().mapToInt(ItemStack::getCount).sum()==1,"Eight shells return one item");
        var copy=new IndustryBlockEntity(root,state());copy.loadWithComponents(drops.getFirst().get(DataComponents.BLOCK_ENTITY_DATA).copyTag(),level.registryAccess());h.assertTrue(copy.stock.get(9).getCount()==3&&copy.getPower()==12345&&copy.tanks[0].getFluidAmount()==750,"Single drop preserves shared inventory, energy and tank NBT");h.succeed();
    }

    @GameTest(template="empty")
    public static void allEightShellsSharePortsAndUpperRedstoneStopsTheController(GameTestHelper h){
        var owner=machine(h,2);var level=h.getLevel();int accepted=0;
        for(int part=0;part<8;part++){var pos=owner.getBlockPos().offset(LargeMachineBlock.offset(Direction.NORTH,part));h.assertTrue(LargeMachineBlock.controller(level,pos)==owner,"Every shell resolves the same owner");var power=level.getCapability(Capabilities.EnergyStorage.BLOCK,pos,Direction.WEST);var items=level.getCapability(Capabilities.ItemHandler.BLOCK,pos,Direction.DOWN);var fluid=level.getCapability(Capabilities.FluidHandler.BLOCK,pos,Direction.UP);h.assertTrue(power!=null&&items!=null&&fluid!=null,"Every shell exposes power, item and fluid ports");accepted+=power.receiveEnergy(1000,false);fluid.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER,100),IFluidHandler.FluidAction.EXECUTE);}
        h.assertTrue(accepted==2048&&owner.getPower()==2048&&owner.tanks[0].getFluidAmount()==800,"Proxy ports share one input budget and one tank");owner.stock.set(9,new ItemStack(Items.DIAMOND,2));
        for(int part=0;part<8;part++){var port=level.getCapability(Capabilities.ItemHandler.BLOCK,owner.getBlockPos().offset(LargeMachineBlock.offset(Direction.NORTH,part)),Direction.DOWN);h.assertTrue(port.extractItem(0,1,true).is(Items.DIAMOND),"Every downward shell port sees the same output");}
        var signal=owner.getBlockPos().offset(LargeMachineBlock.offset(Direction.NORTH,7)).east();level.setBlock(signal,Blocks.REDSTONE_BLOCK.defaultBlockState(),3);long stored=owner.getPower();IndustryBlockEntity.tick(level,owner.getBlockPos(),owner.getBlockState(),owner);h.assertTrue(owner.redstoneStopped()&&owner.status==8&&owner.getPower()==stored,"Redstone touching only upper shell stops the shared controller");level.removeBlock(signal,false);h.assertTrue(!owner.redstoneStopped(),"Removing upper signal resumes controller");AggregateMachines.syncActive(owner,true);for(int part=0;part<8;part++)h.assertTrue(level.getBlockState(owner.getBlockPos().offset(LargeMachineBlock.offset(Direction.NORTH,part))).getValue(IndustryBlock.ACTIVE),"Active state reaches all eight parts");h.succeed();
    }

    @GameTest(template="empty")
    public static void requiresDedicatedMatchingBasesAndNeverExpandsLegacyController(GameTestHelper h){
        var owner=machine(h,4);var root=owner.getBlockPos();var level=h.getLevel();level.setBlock(root.north(),Blocks.STONE.defaultBlockState(),3);h.assertTrue(AggregateMachines.ready(owner),"Array has no front clearance requirement");
        for(String key:List.of("phase_matrix_meteor","phase_matrix_rutile","phase_matrix_borate")){foundation(h,root,Direction.NORTH,key);h.assertTrue(AggregateMachines.ready(owner),"All three dedicated matrix types work");}
        level.setBlock(root.east().below(),Blocks.STONE.defaultBlockState(),3);h.assertTrue(!AggregateMachines.ready(owner)&&owner.status==10,"Ordinary stone cannot replace a matrix");foundation(h,root,Direction.NORTH,"resonance_base_stone");h.assertTrue(!AggregateMachines.ready(owner)&&owner.status==10,"Quarry resonance bases cannot power the array");foundation(h,root,Direction.NORTH,"phase_matrix_meteor");level.setBlock(root.east().below(),((BlockItem)CatalogueContent.ITEMS.get("phase_matrix_borate").get()).getBlock().defaultBlockState(),3);h.assertTrue(!AggregateMachines.ready(owner)&&owner.status==10,"Mixed dedicated matrices are rejected");
        var legacy=site(h,6);level.setBlock(legacy,state(),3);var obstruction=legacy.above();level.setBlock(obstruction,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);var old=(IndustryBlockEntity)level.getBlockEntity(legacy);h.assertTrue(!AggregateMachines.ready(old)&&old.status==9&&level.getBlockState(obstruction).is(Blocks.DIAMOND_BLOCK),"Legacy single blocks remain incomplete and never overwrite surrounding world");h.succeed();
    }

    @GameTest(template="empty")
    public static void blueprintsRequireEightCellsRotateAndChargeOneMachine(GameTestHelper h){
        var design=design();h.assertTrue(design.materials().get(CatalogueContent.item("phase_extractor"))==1,"Eight-cell blueprint charges one machine");boolean rejected=false;try{new ConstructionPlan("缺少上层",design.blocks().subList(0,4));}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"Four-cell legacy array blueprints are rejected");
        for(String kind:List.of("quarry","crusher")){boolean invalid=false;try{new ConstructionPlan("非法旧机上层",List.of(new ConstructionPlan.Cell(BlockPos.ZERO,CatalogueContent.BLOCKS.get(kind).get().defaultBlockState().setValue(LargeMachineBlock.PART,4),"",false)));}catch(IllegalArgumentException expected){invalid=true;}h.assertTrue(invalid,"Four-cell machines reject reserved part 4..7 in blueprints");}
        for(int turn=0;turn<4;turn++){var builder=builder(h,10+turn,design,turn);builder.setItem(1,CatalogueContent.stack("phase_extractor",1));h.assertTrue(builder.build(player(h,builder.getBlockPos()))&&builder.getItem(1).isEmpty(),"Each rotation consumes exactly one item");var root=builder.target(design.blocks().getFirst());var owner=LargeMachineBlock.controller(h.getLevel(),root);h.assertTrue(owner!=null,"Rotated controller exists");foundation(h,root,owner.facing(),"phase_matrix_meteor");h.assertTrue(AggregateMachines.ready(owner),"Rotated eight-cell structure is complete");for(var cell:design.blocks())h.assertTrue(LargeMachineBlock.controller(h.getLevel(),builder.target(cell))==owner,"Rotated lower and upper cells share controller");}h.succeed();
    }

    @GameTest(template="empty")
    public static void blueprintRepairRollbackKeepsExistingControllerAndBothLayerState(GameTestHelper h){
        var design=design();var builder=builder(h,18,design,0);var root=builder.target(design.blocks().getFirst());var level=h.getLevel();level.setBlock(root,state(),3);var owner=(IndustryBlockEntity)level.getBlockEntity(root);owner.stock.set(9,new ItemStack(Items.DIAMOND,5));owner.setPower(4567);var blocked=builder.target(design.blocks().get(7));java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> deny=event->{if(event.getPos().equals(blocked))event.setCanceled(true);};net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try{h.assertTrue(!builder.build(player(h,builder.getBlockPos())),"Upper shell protection cancels repair");}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny);}
        h.assertTrue(level.getBlockEntity(root)==owner&&owner.stock.get(9).getCount()==5&&owner.getPower()==4567,"Repair rollback retains exact existing controller and saved stock");for(int part=1;part<8;part++)h.assertTrue(level.isEmptyBlock(builder.target(design.blocks().get(part))),"Repair rollback removes all seven added proxies");h.assertTrue(builder.required().isEmpty()&&builder.build(player(h,builder.getBlockPos())),"Explicit complete blueprint repairs legacy footprint without an extra machine item");foundation(h,root,Direction.NORTH,"phase_matrix_meteor");h.assertTrue(AggregateMachines.ready(owner)&&owner.stock.get(9).getCount()==5,"Only explicit blueprint repair adds shells and preserves stock");h.succeed();
    }

    @GameTest(template="empty")
    public static void blueprintUpperShellProtectionRollsBackAllNewPartsAndMaterial(GameTestHelper h){
        var design=design();var builder=builder(h,16,design,0);builder.setItem(1,CatalogueContent.stack("phase_extractor",1));var blocked=builder.target(design.blocks().get(6));boolean[] fired={false};java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> deny=event->{if(event.getPos().equals(blocked)){fired[0]=true;event.setCanceled(true);}};net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try{h.assertTrue(!builder.build(player(h,builder.getBlockPos())),"Upper-layer protection cancels construction");}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny);}
        h.assertTrue(fired[0]&&builder.getItem(1).getCount()==1,"Rollback retains the single machine material");for(var cell:design.blocks())h.assertTrue(h.getLevel().isEmptyBlock(builder.target(cell)),"Rollback removes every newly placed cell in both layers");h.succeed();
    }
}
