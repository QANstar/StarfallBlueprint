package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class ConstructionBlueprintGameTests {
    private static final BlockPos POS=new BlockPos(1,4,1);
    private static ServerPlayer player(GameTestHelper h,BlockPos pos){var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"BlueprintTest"));player.setGameMode(GameType.SURVIVAL);player.moveTo(Vec3.atCenterOf(pos).add(0,1,1));player.getInventory().clearContent();return player;}
    private static ConstructionPlan simple(){return new ConstructionPlan("两格测试",List.of(new ConstructionPlan.Cell(BlockPos.ZERO,Blocks.COBBLESTONE.defaultBlockState(),"",false),new ConstructionPlan.Cell(new BlockPos(1,0,0),Blocks.CHEST.defaultBlockState(),"",false)));}
    private static void setPlan(BlueprintBuilderEntity builder,ConstructionPlan plan){var stack=new ItemStack(Blueprints.PLAN.get());Blueprints.write(stack,plan);builder.setItem(0,stack);}
    private static BlueprintBuilderEntity builder(GameTestHelper h){
        // Small game-test templates have a boundary casing just outside them;
        // use an isolated, explicitly loaded construction site instead.
        BlockPos pos=h.absolutePos(new BlockPos(200,32,200));var level=h.getLevel();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk((pos.getX()>>4)+x,(pos.getZ()>>4)+z);
        level.setBlock(pos,Blueprints.BUILDER.get().defaultBlockState(),3);var builder=(BlueprintBuilderEntity)level.getBlockEntity(pos);setPlan(builder,simple());return builder;
    }
    private static void fill(BlueprintBuilderEntity builder){int slot=1;for(var entry:builder.required().entrySet()){int n=entry.getValue();while(n>0){int count=Math.min(64,n);if(slot>=28)throw new AssertionError("Blueprint material buffer capacity");builder.setItem(slot++,new ItemStack(entry.getKey(),count));n-=count;}}}
    @GameTest(template="empty")
    public static void blueprintDirectoryListsMetadataAndIsolatesInvalidFiles(GameTestHelper h)throws java.io.IOException{
        var root=java.nio.file.Files.createTempDirectory("starfall-blueprint-picker-");
        var valid=root.resolve("A-植物线.sfbp.json");var bad=root.resolve("B-broken.sfbp.json");var huge=root.resolve("C-large.sfbp.json");
        var ignored=root.resolve("notes.txt");var nested=root.resolve("sub.sfbp.json");var inside=nested.resolve("nested.sfbp.json");
        try{
            java.nio.file.Files.writeString(valid,simple().json(),StandardCharsets.UTF_8);java.nio.file.Files.writeString(bad,"broken");java.nio.file.Files.writeString(huge,"x".repeat(ConstructionPlan.MAX_TEXT*4+1));
            java.nio.file.Files.writeString(ignored,simple().json());java.nio.file.Files.createDirectory(nested);java.nio.file.Files.writeString(inside,simple().json());
            var files=BlueprintFiles.scan(root);
            h.assertTrue(files.size()==3&&files.get(0).filename().equals("A-植物线.sfbp.json")&&files.get(0).title().equals(simple().title())&&files.get(0).blocks()==2&&files.get(0).valid(),"Directory shows sorted portable names, actual titles and block counts; ignores subdirectories and unrelated files");
            h.assertTrue(!files.get(1).valid()&&!files.get(1).error().isBlank()&&!files.get(2).valid(),"Malformed and oversized designs remain visible but cannot be chosen");
            java.nio.file.Files.writeString(valid,"changed after scan");boolean rejected=false;
            try{BlueprintFiles.read(root,files.get(0).filename());}catch(IllegalArgumentException expected){rejected=true;}
            h.assertTrue(rejected,"Selection re-reads and validates the live file rather than trusting cached metadata");
            for(var path:List.of(valid,bad,huge,ignored,inside))java.nio.file.Files.delete(path);java.nio.file.Files.delete(nested);
            h.assertTrue(BlueprintFiles.scan(root).isEmpty(),"Rescanning reflects removals and handles an empty directory");h.succeed();
        }finally{for(var path:List.of(valid,bad,huge,ignored,inside,nested,root))java.nio.file.Files.deleteIfExists(path);}
    }
    @GameTest(template="empty")
    public static void creativeCoreBuildsWithoutSpendingInternalOrExternalMaterials(GameTestHelper h){
        var builder=builder(h);var player=player(h,builder.getBlockPos());
        // Creative acquisition only: deliberately no crafting recipe or world loot source.
        h.assertTrue(h.getLevel().getRecipeManager().getRecipes().stream().noneMatch(r->r.value().getResultItem(h.getLevel().registryAccess()).is(Blueprints.CREATIVE_CORE.get())),"Core has no survival crafting source");
        player.setGameMode(GameType.CREATIVE);player.getInventory().setItem(0,new ItemStack(Blueprints.CREATIVE_CORE.get()));
        var menu=(BlueprintMenu)builder.createMenu(17,player.getInventory(),player);
        h.assertTrue(!menu.quickMoveStack(player,55).isEmpty()&&builder.freeConstruction()&&menu.ready(),"Shift-click installs core and enables build with an incomplete material bill");
        builder.setItem(2,new ItemStack(Items.COBBLESTONE,5));
        h.getLevel().setBlock(builder.getBlockPos().west(),Blocks.CHEST.defaultBlockState(),3);
        var chest=(Container)h.getLevel().getBlockEntity(builder.getBlockPos().west());chest.setItem(0,new ItemStack(Items.CHEST,7));
        h.assertTrue(builder.build(player)&&builder.getItem(1).is(Blueprints.CREATIVE_CORE.get())&&builder.getItem(2).getCount()==5&&chest.getItem(0).getCount()==7,"Free build retains core, design and all internal/external materials");
        h.assertTrue(h.getLevel().getBlockState(builder.origin()).is(Blocks.COBBLESTONE)&&h.getLevel().getBlockState(builder.origin().east()).is(Blocks.CHEST),"Free mode actually places the complete blueprint");
        var copy=new BlueprintBuilderEntity(builder.getBlockPos(),builder.getBlockState());copy.loadWithComponents(builder.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(copy.freeConstruction(),"Installed core persists across save/load");h.succeed();
    }
    @GameTest(template="empty")
    public static void creativeCoreRequiresDesignAndRetainsPlacementGuards(GameTestHelper h){
        var builder=builder(h);var player=player(h,builder.getBlockPos());player.setGameMode(GameType.CREATIVE);
        builder.setItem(1,new ItemStack(Blueprints.CREATIVE_CORE.get()));
        h.getLevel().setBlock(builder.getBlockPos().above(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        h.assertTrue(!builder.build(player)&&builder.status==7,"Core does not bypass redstone pause");h.getLevel().removeBlock(builder.getBlockPos().above(),false);
        h.getLevel().setBlock(builder.origin(),Blocks.BEDROCK.defaultBlockState(),3);
        h.assertTrue(!builder.build(player)&&builder.status==4,"Core never overwrites existing obstacles");h.getLevel().removeBlock(builder.origin(),false);
        java.util.function.Consumer<BlockEvent.EntityPlaceEvent> deny=event->{if(event.getPos().equals(builder.origin().east()))event.setCanceled(true);};NeoForge.EVENT_BUS.addListener(deny);
        try{h.assertTrue(!builder.build(player)&&h.getLevel().isEmptyBlock(builder.origin())&&h.getLevel().isEmptyBlock(builder.origin().east()),"Free placement still rolls back when protection rejects it");}finally{NeoForge.EVENT_BUS.unregister(deny);}
        builder.setItem(0,new ItemStack(Blueprints.PLAN.get()));h.assertTrue(!builder.build(player)&&builder.status==2,"Core cannot replace a valid design");
        setPlan(builder,simple());builder.setItem(1,ItemStack.EMPTY);var menu=(BlueprintMenu)builder.createMenu(17,player.getInventory(),player);
        h.assertTrue(!menu.ready()&&!builder.build(player)&&builder.status==3,"Removing the core restores material checks even for a creative player");h.succeed();
    }
    @GameTest(template="empty")
    public static void relativeInstancePathAcceptsPortableFilesButRejectsTraversal(GameTestHelper h){
        var root=java.nio.file.Path.of(".","blueprints");var file=BlueprintFiles.resolve(root,"L25.sfbp.json");
        h.assertTrue(file.equals(root.toAbsolutePath().normalize().resolve("L25.sfbp.json")),"Relative dot game directories accept normal blueprint filenames");
        h.assertTrue(BlueprintFiles.resolve(root," L25 ").equals(file),"Extension and surrounding whitespace are handled");
        for(String invalid:List.of("../L25","..\\L25","C:\\L25","","..")){
            boolean rejected=false;try{BlueprintFiles.resolve(root,invalid);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"Unsafe paths remain rejected");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void adjacentStorageCountsSyncsAndDebitsOnlyOnSuccess(GameTestHelper h){
        var builder=builder(h);var level=h.getLevel();var player=player(h,builder.getBlockPos());
        level.setBlock(builder.getBlockPos().west(),Blocks.CHEST.defaultBlockState(),3);
        level.setBlock(builder.getBlockPos().above(),Blocks.BARREL.defaultBlockState(),3);
        var chest=(Container)level.getBlockEntity(builder.getBlockPos().west());var barrel=(Container)level.getBlockEntity(builder.getBlockPos().above());
        builder.setItem(1,new ItemStack(Items.COBBLESTONE));chest.setItem(0,new ItemStack(Items.COBBLESTONE,4));barrel.setItem(0,new ItemStack(Items.CHEST,3));
        h.assertTrue(builder.available(Items.COBBLESTONE)==5&&builder.available(Items.CHEST)==3,"Internal and adjacent storage combine");
        var server=(BlueprintMenu)builder.createMenu(17,player.getInventory(),player);var client=new BlueprintMenu(17,player.getInventory());client.container.setItem(0,builder.getItem(0).copy());
        server.addSlotListener(new net.minecraft.world.inventory.ContainerListener(){
            public void slotChanged(net.minecraft.world.inventory.AbstractContainerMenu menu,int slot,ItemStack stack){}
            public void dataChanged(net.minecraft.world.inventory.AbstractContainerMenu menu,int index,int value){client.setData(index,value);}
        });server.broadcastChanges();
        h.assertTrue(client.available(Items.COBBLESTONE)==5&&client.available(Items.CHEST)==3&&client.ready(),"Client list and build button receive adjacent stock");
        level.setBlock(builder.origin(),Blocks.BEDROCK.defaultBlockState(),3);
        h.assertTrue(!builder.build(player)&&chest.getItem(0).getCount()==4&&barrel.getItem(0).getCount()==3,"Blocked build preserves external stock");level.removeBlock(builder.origin(),false);
        h.assertTrue(builder.build(player)&&builder.getItem(1).isEmpty()&&chest.getItem(0).getCount()==4&&barrel.getItem(0).getCount()==2,"Successful construction spends exact bill, internal slots first");
        barrel.setItem(0,ItemStack.EMPTY);server.broadcastChanges();h.assertTrue(client.available(Items.CHEST)==0,"External inventory changes synchronize without reopening");h.succeed();
    }
    @GameTest(template="empty")
    public static void doubleChestsAreCountedOnceAndNonAdjacentStorageIsIgnored(GameTestHelper h){
        var builder=builder(h);var level=h.getLevel();var pos=builder.getBlockPos();
        var left=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.LEFT);
        var right=left.setValue(ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.RIGHT);
        level.setBlock(pos.north(),left,2);level.setBlock(pos.north().east(),right,2);
        var a=(Container)level.getBlockEntity(pos.north());var b=(Container)level.getBlockEntity(pos.north().east());a.setItem(0,new ItemStack(Items.COBBLESTONE,2));b.setItem(0,new ItemStack(Items.CHEST,2));
        level.setBlock(pos.south(2),Blocks.BARREL.defaultBlockState(),3);((Container)level.getBlockEntity(pos.south(2))).setItem(0,new ItemStack(Items.CHEST,64));
        h.assertTrue(builder.available(Items.CHEST)==2,"Second half of adjacent double chest is included; remote barrels excluded");
        h.assertTrue(builder.build(player(h,pos))&&a.getItem(0).getCount()==1&&b.getItem(0).getCount()==1,"Construction consumes both halves of double chest");h.succeed();
    }
    @GameTest(template="empty")
    public static void blueprintRejectsMalformedOrUnsafeFiles(GameTestHelper h){
        String valid=simple().json();h.assertTrue(ConstructionPlan.parse(valid).json().equals(valid),"Portable design round-trips without inventories");
        for(String invalid:List.of(valid.replace("minecraft:chest","minecraft:command_block"),valid.replace("minecraft:chest","missing:unregistered"),valid.replace("\"version\": 1","\"version\": 2"),valid.replace("\"blocks\": [","\"blocks\": [{\"pos\":[0,0,0],\"block\":\"minecraft:stone\"},"),"x".repeat(60001))){
            boolean rejected=false;try{ConstructionPlan.parse(invalid);}catch(RuntimeException expected){rejected=true;}h.assertTrue(rejected,"Invalid design must be rejected");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void missingMaterialsAndObstaclesNeverPartiallyBuildOrDebit(GameTestHelper h){
        var builder=builder(h);var player=player(h,builder.getBlockPos());builder.setItem(1,new ItemStack(Items.COBBLESTONE));
        h.assertTrue(!builder.build(player)&&h.getLevel().isEmptyBlock(builder.origin())&&builder.getItem(1).getCount()==1,"Shortage has no world or material side effects");
        builder.setItem(2,new ItemStack(Items.CHEST));h.getLevel().setBlock(builder.origin().east(),Blocks.BEDROCK.defaultBlockState(),3);
        h.assertTrue(!builder.build(player)&&h.getLevel().isEmptyBlock(builder.origin())&&builder.getItem(2).getCount()==1,"Entire site is checked before any placement");h.succeed();
    }
    @GameTest(template="empty")
    public static void exactMaterialsBuildOnceAndExistingBlocksCostNothing(GameTestHelper h){
        var builder=builder(h);var player=player(h,builder.getBlockPos());fill(builder);
        h.assertTrue(builder.build(player)&&h.getLevel().getBlockState(builder.origin()).is(Blocks.COBBLESTONE)&&h.getLevel().getBlockState(builder.origin().east()).is(Blocks.CHEST),"Build places complete layout");
        h.assertTrue(builder.getItem(1).isEmpty()&&builder.getItem(2).isEmpty()&&Blueprints.read(builder.getItem(0))!=null,"Exact stacks consumed and design retained");
        h.assertTrue(!builder.build(player)&&builder.status==6&&builder.required().isEmpty(),"Second click cannot consume twice or duplicate contents");
        var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(builder.origin().east());h.assertTrue(chest.isEmpty(),"New storage never contains copied items");h.succeed();
    }
    @GameTest(template="empty")
    public static void placementProtectionRollsBackEveryBlockAndKeepsMaterials(GameTestHelper h){
        var builder=builder(h);var player=player(h,builder.getBlockPos());fill(builder);
        java.util.function.Consumer<BlockEvent.EntityPlaceEvent> deny=event->{if(event.getPos().equals(builder.origin().east()))event.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(deny);
        try{h.assertTrue(!builder.build(player)&&h.getLevel().isEmptyBlock(builder.origin())&&h.getLevel().isEmptyBlock(builder.origin().east())&&builder.available(Items.COBBLESTONE)==1&&builder.available(Items.CHEST)==1,"Protection cancels the whole transaction, including previously placed blocks");}
        finally{NeoForge.EVENT_BUS.unregister(deny);}h.succeed();
    }
    @GameTest(template="empty")
    public static void rotationAndRedstoneUseServerAuthoritativeState(GameTestHelper h){
        var builder=builder(h);var player=player(h,builder.getBlockPos());builder.turns=1;fill(builder);
        h.getLevel().setBlock(builder.getBlockPos().above(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        h.assertTrue(!builder.build(player)&&builder.available(Items.CHEST)==1,"Redstone pauses construction without spending materials");h.getLevel().removeBlock(builder.getBlockPos().above(),false);
        h.assertTrue(builder.build(player)&&h.getLevel().getBlockState(builder.origin().south()).is(Blocks.CHEST),"Clockwise rotation moves +X to +Z");
        h.assertTrue(h.getLevel().getBlockState(builder.origin().south()).getValue(ChestBlock.FACING)==Direction.EAST,"Rotation also rotates block-state orientation");h.succeed();
    }
    @GameTest(template="empty")
    public static void plansMaterialsAndRotationSurviveSaveReload(GameTestHelper h){
        var builder=builder(h);builder.turns=3;fill(builder);var tag=builder.saveWithFullMetadata(h.getLevel().registryAccess());
        var copy=new BlueprintBuilderEntity(builder.getBlockPos(),builder.getBlockState());copy.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(copy.turns==3&&copy.plan().blocks().size()==2&&copy.available(Items.CHEST)==1,"Design, material box and orientation persist together");
        var filled=new ItemStack(Items.CHEST);filled.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(!BlueprintBuilderEntity.usable(filled),"Containers carrying saved block-entity data are never spent as empty construction materials");h.succeed();
    }
    @GameTest(template="empty")
    public static void surveyorRecordsBlocksWithoutCopyingContentsOrEnergy(GameTestHelper h){
        h.setBlock(POS,Blocks.CHEST);h.setBlock(POS.east(),CatalogueContent.BLOCKS.get("s02").get());
        ((ChestBlockEntity)h.getBlockEntity(POS)).setItem(0,new ItemStack(Items.DIAMOND,64));((IndustryBlockEntity)h.getBlockEntity(POS.east())).setPower(123456);
        var player=player(h,h.absolutePos(POS));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Blueprints.SURVEYOR.get()));player.getInventory().setItem(1,new ItemStack(Blueprints.PLAN.get()));
        for(BlockPos relative:List.of(POS,POS.east())){BlockPos absolute=h.absolutePos(relative);var hit=new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false);player.gameMode.useItemOn(player,h.getLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,hit);}
        var plan=Blueprints.read(player.getInventory().getItem(1));h.assertTrue(plan!=null&&plan.blocks().size()==2&&!plan.json().contains("diamond")&&!plan.json().contains("123456"),"Survey records structure only, never stored wealth");h.succeed();
    }
    @GameTest(template="empty")
    public static void compressedImportPayloadRoundTripsLargeDesign(GameTestHelper h){
        List<ConstructionPlan.Cell> cells=new ArrayList<>();for(int x=0;x<20;x++)for(int z=0;z<15;z++)cells.add(new ConstructionPlan.Cell(new BlockPos(x,0,-z),Blocks.COBBLESTONE.defaultBlockState(),"",false));
        String json=new ConstructionPlan("三百方块共享设计",cells).json();var buf=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{BlueprintNetworking.Import.CODEC.encode(buf,new BlueprintNetworking.Import(17,json));h.assertTrue(buf.readableBytes()<30000,"Large JSON uses a bounded compressed packet");var packet=BlueprintNetworking.Import.CODEC.decode(buf);h.assertTrue(packet.menuId()==17&&packet.json().equals(json),"Client/server file transfer preserves the complete design");}finally{buf.release();}h.succeed();
    }
    private static ConstructionPlan resource(String file){
        try(var stream=ConstructionBlueprintGameTests.class.getResourceAsStream("/assets/starfall_blueprint/construction_blueprints/"+file)){if(stream==null)throw new AssertionError(file);return ConstructionPlan.parse(new String(stream.readAllBytes(),StandardCharsets.UTF_8));}catch(java.io.IOException e){throw new AssertionError(e);}
    }
    @GameTest(template="empty")
    public static void schematicImportRequiresPaperAndNearbyLiveMenu(GameTestHelper h){
        var builder=builder(h);var player=player(h,builder.getBlockPos());builder.setItem(0,new ItemStack(Blueprints.PLAN.get()));
        var menu=(BlueprintMenu)builder.createMenu(17,player.getInventory(),player);menu.importPlan(player,simple().json());
        h.assertTrue(Blueprints.read(builder.getItem(0))!=null,"Validated file becomes a real reusable design item");String before=Blueprints.text(builder.getItem(0));
        menu.importPlan(player,"not valid JSON");h.assertTrue(Blueprints.text(builder.getItem(0)).equals(before),"Bad imports preserve the previous design");
        player.moveTo(Vec3.atCenterOf(builder.getBlockPos()).add(100,0,0));menu.importPlan(player,simple().json().replace("两格测试","远程改写"));
        h.assertTrue(Blueprints.text(builder.getItem(0)).equals(before),"Remote/stale menu cannot modify the machine design");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void allFourRotationsKeepPublicLayoutsInFrontOfTheConstructor(GameTestHelper h){
        var plan=resource("L25.sfbp.json");var level=h.getLevel();
        for(int turns=0;turns<4;turns++){
            BlockPos site=h.absolutePos(new BlockPos(500+turns*64,60,1200));level.getChunk(site.getX()>>4,site.getZ()>>4);level.setBlock(site,Blueprints.BUILDER.get().defaultBlockState(),3);
            var builder=(BlueprintBuilderEntity)level.getBlockEntity(site);setPlan(builder,plan);builder.turns=turns;
            for(var cell:plan.blocks()){BlockPos pos=builder.target(cell);level.getChunk(pos.getX()>>4,pos.getZ()>>4);h.assertTrue(pos.getZ()<=site.getZ()-2,"Rotated public layout stays ahead of its controller");}
            fill(builder);h.assertTrue(builder.build(player(h,site)),"Every rotation must build without colliding with the constructor: "+turns);
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void everyHandbookBlueprintBuildsUsingItsExactBill(GameTestHelper h){
        List<String> files;
        try(var stream=ConstructionBlueprintGameTests.class.getResourceAsStream("/assets/starfall_blueprint/construction_blueprints/index.json")){files=new com.google.gson.Gson().fromJson(new String(stream.readAllBytes(),StandardCharsets.UTF_8),new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());}catch(Exception e){throw new AssertionError(e);}
        h.assertTrue(files.size()==23,"Every public line has a bundled portable design");int index=0;
        for(String file:files){
            var plan=resource(file);BlockPos pos=h.absolutePos(new BlockPos(1000+index++*64,80,1000));
            var level=h.getLevel();level.getChunk(pos.getX()>>4,pos.getZ()>>4);
            level.setBlock(pos,Blueprints.BUILDER.get().defaultBlockState(),3);var builder=(BlueprintBuilderEntity)level.getBlockEntity(pos);setPlan(builder,plan);
            for(var cell:plan.blocks()){BlockPos target=builder.target(cell);level.getChunk(target.getX()>>4,target.getZ()>>4);}
            fill(builder);var player=player(h,pos);
            h.assertTrue(builder.build(player),"Public blueprint must actually build: "+file+" status="+builder.status);
            h.assertTrue(builder.required().isEmpty(),"All target block states match after construction: "+file);
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void concreteBlueprintConfiguresAWorkingPowderToConcreteChain(GameTestHelper h){
        var plan=resource("L24.sfbp.json");var level=h.getLevel();BlockPos site=h.absolutePos(new BlockPos(600,40,600));level.getChunk(site.getX()>>4,site.getZ()>>4);level.setBlock(site,Blueprints.BUILDER.get().defaultBlockState(),3);
        var builder=(BlueprintBuilderEntity)level.getBlockEntity(site);setPlan(builder,plan);
        for(var cell:plan.blocks()){BlockPos pos=builder.target(cell);level.getChunk(pos.getX()>>4,pos.getZ()>>4);}
        fill(builder);h.assertTrue(builder.build(player(h,builder.getBlockPos())),"Concrete layout builds");
        IndustryBlockEntity crafter=null;PreparationBlockEntity prep=null;
        for(var cell:plan.blocks()){var be=level.getBlockEntity(builder.target(cell));if(be instanceof PreparationBlockEntity p)prep=p;else if(be instanceof IndustryBlockEntity i&&i.kind().equals("autocrafter"))crafter=i;}
        h.assertTrue(prep!=null&&prep.materialMode&&crafter!=null&&crafter.template.equals("minecraft:white_concrete_powder"),"Blueprint configures real machine modes and crafting recipe");
        crafter.stock.set(0,new ItemStack(Items.SAND,4));crafter.stock.set(1,new ItemStack(Items.GRAVEL,4));crafter.stock.set(2,new ItemStack(Items.WHITE_DYE));crafter.setPower(4000);
        for(int i=0;i<40;i++)IndustryProcessing.tick(crafter);
        for(int i=9;i<=12;i++)if(crafter.stock.get(i).is(Items.WHITE_CONCRETE_POWDER)){prep.stock.set(0,crafter.stock.get(i).copy());crafter.stock.set(i,ItemStack.EMPTY);}
        prep.water=1000;prep.setPower(4000);for(int i=0;i<40;i++)IndustryProcessing.tick(prep);
        h.assertTrue(prep.stock.subList(9,13).stream().filter(s->s.is(Items.WHITE_CONCRETE)).mapToInt(ItemStack::getCount).sum()==8,"Built production design actually produces eight concrete from its configured chain");h.succeed();
    }
}
