package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.capabilities.Capabilities;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class CatalogueGameTests {
    private static final BlockPos POS=new BlockPos(1,2,1);
    private static IndustryBlockEntity machine(GameTestHelper h,String key){h.setBlock(POS,Blocks.AIR);h.setBlock(POS,key.equals("prep")?StarfallBlueprint.PREPARATION.get():CatalogueContent.BLOCKS.get(key).get());var be=(IndustryBlockEntity)h.getBlockEntity(POS);if(be instanceof PreparationBlockEntity prep)prep.materialMode=true;return be;}
    private static ItemStack material(String key,int count){var s=CatalogueContent.stack(key,count);if(key.equals("common")){s.set(StarfallBlueprint.BATCH.get(),MaterialBatch.crystal(100));s.set(CatalogueContent.QUALITY.get(),new MaterialQuality(90,100,0,0,true,"common"));}return s;}
    private static void process(IndustryBlockEntity be,int n){for(int i=0;i<n;i++)IndustryProcessing.tick(be);}
    private static int count(IndustryBlockEntity be,String key){return be.stock.subList(9,13).stream().filter(s->CatalogueContent.matches(key,s)).mapToInt(ItemStack::getCount).sum();}
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h){var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Catalogue"));p.getInventory().clearContent();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);return p;}

    @GameTest(template="empty")
    public static void everyCatalogueWorkbenchRecipeIsExecutable(GameTestHelper h){
        var existing=Set.of("ST-01","ST-02","ST-03","ST-04","ST-05","ST-06","ST-07","ST-08","ST-09","ST-11","ST-12","ST-13","ST-14","MC-C01","MC-M01","PC-M03","EN-T01","SV-M03","CB-01","CB-02","CB-03","CB-05");
        Set<String> expected=new HashSet<>(),verified=new HashSet<>();
        for(var r:CatalogueContent.RECIPES.values())if(r.get("station").getAsString().equals("workbench")&&r.has("craft")&&!r.get("id").getAsString().startsWith("V-")&&!existing.contains(r.get("id").getAsString())&&CatalogueContent.isRecipeEnabled(r))expected.add(r.get("id").getAsString());
        for(var holder:h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING))if(holder.value()instanceof CatalogueCraftingRecipe recipe){
            h.assertTrue(expected.contains(recipe.id),"Only enabled catalogue workbench recipes may load: "+recipe.id);
            var d=CatalogueContent.recipe(recipe.id);List<ItemStack>grid=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));var craft=d.getAsJsonObject("craft");
            if(craft.get("mode").getAsString().startsWith("定形")){var cells=craft.getAsJsonArray("cells");for(int i=0;i<9;i++)if(!cells.get(i).isJsonNull())grid.set(i,material(CatalogueContent.key(cells.get(i)),1));}
            else {int i=0;for(var input:d.getAsJsonArray("inputs"))for(int n=0;n<CatalogueContent.qty(input);n++)grid.set(i++,material(CatalogueContent.key(input),1));}
            var input=CraftingInput.of(3,3,grid);h.assertTrue(recipe.matches(input,h.getLevel()),"Recipe rejects its real 3x3 inputs: "+recipe.id);
            var out=recipe.assemble(input,h.getLevel().registryAccess());var expectedOutput=d.getAsJsonArray("outputs").get(0);h.assertTrue(CatalogueContent.matches(CatalogueContent.key(expectedOutput),out)&&out.getCount()==CatalogueContent.qty(expectedOutput),"Wrong result: "+recipe.id);
            var remaining=recipe.getRemainingItems(input);h.assertTrue(remaining.stream().allMatch(ItemStack::isEmpty),"Catalogue assembly must not duplicate containers: "+recipe.id);h.assertTrue(verified.add(recipe.id),"Duplicate catalogue recipe: "+recipe.id);
        }
        h.assertTrue(expected.size()==52&&verified.equals(expected),"All 52 active workbench recipes must load and execute: expected="+expected+" verified="+verified);h.succeed();
    }
    @GameTest(template="empty")
    public static void everyIndustrialProcessSurvivesReloadAndConservesInputs(GameTestHelper h){
        var active=CatalogueContent.RECIPES.values().stream().filter(CatalogueContent::isRecipeEnabled).filter(r->Set.of("prep","refiner","recycler","bio_processor").contains(r.get("station").getAsString())&&!r.get("id").getAsString().equals("PC-R01")).toList();
        int verified=0;
        for(var r:active){
            String id=r.get("id").getAsString();var be=machine(h,r.get("station").getAsString());be.template=id;be.setPower(320000);int slot=0;
            for(var in:r.getAsJsonArray("inputs")){String key=CatalogueContent.key(in);if(key.equals("water"))be.tanks[0].fill(new FluidStack(Fluids.WATER,CatalogueContent.qty(in)),FluidAction.EXECUTE);else be.stock.set(slot++,material(key,CatalogueContent.qty(in)));}
            int tool=14;for(var in:r.getAsJsonArray("tools"))be.stock.set(tool++,material(in.getAsString(),1));
            int duration=(int)Math.ceil(r.get("seconds").getAsDouble()*20);process(be,1);h.assertTrue(be.activeRecipe.equals(id),"Cannot start "+id+" status="+be.status);
            h.assertTrue(be.stock.subList(0,9).stream().allMatch(ItemStack::isEmpty)&&be.tanks[0].isEmpty(),"Reserve exactly once: "+id);
            var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());process(be,duration-1);
            h.assertTrue(be.idle()&&be.getPower()==320000-r.get("energy").getAsLong(),"Cost/duration/reload mismatch: "+id+" energy="+be.getPower());
            for(var out:r.getAsJsonArray("outputs")){String key=CatalogueContent.key(out);int n=CatalogueContent.qty(out);if(CatalogueContent.FLUID_KEYS.contains(key)){int actual=Arrays.stream(be.tanks).filter(t->t.getFluid().is(CatalogueFluids.fluid(key))).mapToInt(t->t.getFluidAmount()).sum();h.assertTrue(actual==n,"Fluid conservation: "+id+" "+key);}else h.assertTrue(count(be,key)==n,"Solid conservation: "+id+" "+key);}
            int total=be.stock.subList(9,13).stream().mapToInt(ItemStack::getCount).sum();process(be,duration);h.assertTrue(total==be.stock.subList(9,13).stream().mapToInt(ItemStack::getCount).sum(),"Idle duplicate: "+id);verified++;
        }
        h.assertTrue(active.size()==28&&verified==active.size(),"All 12 material processes and 16 concrete colours must survive reload and conserve inputs: expected="+active.size()+" verified="+verified);h.succeed();
    }
    @GameTest(template="empty")
    public static void mixedLowQualityCannotBeLaunderedAndBackpressureConsumesNothing(GameTestHelper h){
        var be=machine(h,"refiner");be.template="PC-R03";be.setPower(320000);be.stock.set(0,material("common",4));be.stock.set(1,material("common",1));be.stock.get(1).set(StarfallBlueprint.BATCH.get(),MaterialBatch.crystal(19));be.tanks[0].fill(new FluidStack(Fluids.WATER,500),FluidAction.EXECUTE);
        process(be,10);h.assertTrue(be.idle()&&be.stock.get(0).getCount()==4&&be.getPower()==320000&&be.tanks[0].getFluidAmount()==500,"Check every consumed crystal before reserving");
        be.stock.set(1,material("common",1));for(int i=9;i<13;i++)be.stock.set(i,new ItemStack(Items.COBBLESTONE,64));process(be,10);h.assertTrue(be.idle()&&be.stock.get(0).getCount()==4&&be.stock.get(14).getDamageValue()==0,"Full output never consumes ingredients or head life");h.succeed();
    }
    @GameTest(template="empty")
    public static void fluidIdentityAndByproductSeparation(GameTestHelper h){
        var be=machine(h,"refiner");for(String key:CatalogueContent.FLUID_KEYS)h.assertTrue(be.fluidPort().fill(new FluidStack(CatalogueFluids.fluid(key),1000),FluidAction.EXECUTE)==0,"Process input only accepts cold water: "+key);
        be.tanks[1].fill(new FluidStack(CatalogueFluids.fluid("warm"),1000),FluidAction.EXECUTE);h.assertTrue(be.fluidPort().drain(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE).isEmpty()&&be.tanks[1].getFluidAmount()==1000,"Warm water cannot be extracted as cold water");
        for(String key:CatalogueContent.FLUID_KEYS){var e=CatalogueFluids.ENTRIES.get(key);h.assertTrue(net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(new ItemStack(e.bucket().get())).map(f->f.is(e.source().get())).orElse(false)&&CatalogueFluids.key(e.flowing().get()).equals(key),"Real bucket/flow identity: "+key);}h.succeed();
    }
    @GameTest(template="empty")
    public static void batteryTickBudgetsEfficiencyAndUpgradePersist(GameTestHelper h){
        var be=machine(h,"s02");var port=be.energyPort(Direction.UP);h.assertTrue(port.receiveEnergy(1000,false)==1000&&be.getPower()==900,"90% charge consumes 1000 and stores 900");h.assertTrue(port.receiveEnergy(1000,false)==1000&&port.receiveEnergy(1,false)==0&&be.getPower()==1800,"Charge budget is shared by all calls");
        be.setPower(200000);for(int i=0;i<20;i++)port.extractEnergy(100,false);h.assertTrue(be.getPower()==198000&&be.peakTicks==1&&port.extractEnergy(1,false)==0,"Peak counted once per tick across twenty calls");
        be.efficientCharge=true;var tag=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(tag,h.getLevel().registryAccess());h.assertTrue(be.efficientCharge&&be.peakTicks==1,"Upgrade/peak survive relocation");h.succeed();
    }
    @GameTest(template="empty")
    public static void compactBatteryChargeRemainderNeverCreatesEnergy(GameTestHelper h){
        var s=material("s01",1);var battery=CatalogueEquipment.energy(s);for(int i=0;i<100;i++)battery.receiveEnergy(1,false);h.assertTrue(battery.getEnergyStored()==90,"Split 1-unit charging preserves 90% efficiency");h.assertTrue(battery.extractEnergy(512,false)==90&&battery.extractEnergy(512,false)==0,"Discharge cannot create energy");h.succeed();
    }
    @GameTest(template="empty")
    public static void generationFuelAndBioliquidAreConserved(GameTestHelper h){
        var be=machine(h,"g01");be.stock.set(13,new ItemStack(Items.COAL));for(int i=0;i<640;i++)IndustryEnergy.tick(be);h.assertTrue(be.getPower()==320000&&be.fuelHeat==0&&be.stock.get(13).isEmpty(),"One coal creates exactly 320k XH");
        be=machine(h,"g05");be.tanks[0].fill(new FluidStack(CatalogueFluids.fluid("bio"),17),FluidAction.EXECUTE);for(int i=0;i<20;i++)IndustryEnergy.tick(be);h.assertTrue(be.getPower()==17000&&be.tanks[0].isEmpty(),"17mB bioliquid creates exactly 17k XH");h.succeed();
    }
    @GameTest(template="empty")
    public static void pumpsRespectFiniteWaterAndDewEnergy(GameTestHelper h){
        boolean old=h.getLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_WATER_SOURCE_CONVERSION);
        try{h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WATER_SOURCE_CONVERSION).set(false,h.getLevel().getServer());var be=machine(h,"pump");h.setBlock(POS.below(),Blocks.WATER);be.setPower(1000);for(int i=0;i<20;i++)IndustryEnergy.tick(be);h.assertTrue(be.tanks[0].getFluidAmount()==1000&&be.getPower()==0&&h.getBlockState(POS.below()).isAir(),"Finite-water pump consumes real source and 1k XH");be=machine(h,"dew");be.setPower(4000);for(int i=0;i<20;i++)IndustryEnergy.tick(be);h.assertTrue(be.tanks[0].getFluidAmount()==1000&&be.getPower()==0,"Dew creates one bucket for 4k XH");}finally{h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WATER_SOURCE_CONVERSION).set(old,h.getLevel().getServer());}h.succeed();
    }
    @GameTest(template="empty")
    public static void extractorDirectInventoryRespectsFourItemsAndRetain(GameTestHelper h){
        var be=machine(h,"item_extractor");h.setBlock(POS.south(),Blocks.CHEST);h.setBlock(POS.north(),Blocks.CHEST);var input=(ChestBlockEntity)h.getBlockEntity(POS.south());var output=(ChestBlockEntity)h.getBlockEntity(POS.north());input.setItem(0,new ItemStack(Items.IRON_INGOT,10));be.retain=8;
        h.runAfterDelay(21,()->{h.assertTrue(input.getItem(0).getCount()==8&&output.getItem(0).getCount()==2,"Direct extractor must find front inventory and preserve reserve");h.succeed();});
    }
    @GameTest(template="empty")
    public static void powerJunctionCannotReceiveFromOutputFace(GameTestHelper h){
        var node=machine(h,"energy_junction");h.setBlock(POS.north(),CatalogueContent.BLOCKS.get("g01").get());var source=(IndustryBlockEntity)h.getBlockEntity(POS.north());source.setPower(10000);h.setBlock(POS.south(),CatalogueContent.BLOCKS.get("refiner").get());var target=(IndustryBlockEntity)h.getBlockEntity(POS.south());IndustryNetworks.tick(node);h.assertTrue(target.getPower()==0&&source.getPower()==10000,"Junction accepts power only at its rear");h.succeed();
    }
    @GameTest(template="empty")
    public static void supplyCrateFiltersAllTwentySevenSlotsAndRejectsNesting(GameTestHelper h){
        var be=machine(h,"supply_crate");be.filters.setItem(0,new ItemStack(Items.IRON_INGOT));h.assertTrue(be.workSlots(Direction.UP).length==27&&be.canPlaceItem(26,new ItemStack(Items.IRON_INGOT))&&!be.canPlaceItem(0,new ItemStack(Items.GOLD_INGOT))&&!be.canPlaceItem(0,new ItemStack(Items.CHEST)),"27 ordinary storage slots enforce whitelist and no nested storage");h.succeed();
    }
    @GameTest(template="empty")
    public static void parallelFurnaceModulesUseRealRecipesAndPersistExperience(GameTestHelper h){
        for(int speed=0;speed<=2;speed++)for(int bulk=0;bulk<=2;bulk++){
            var be=machine(h,"auto_furnace");be.stock.set(0,new ItemStack(Items.RAW_IRON,new int[]{2,4,6}[bulk]));be.stock.set(13,new ItemStack(Items.COAL));if(speed>0)be.stock.set(16,material("speed_"+speed,1));if(bulk>0)be.stock.set(17,material("bulk_"+bulk,1));be.setPower(320000);int lanes=new int[]{2,4,6}[bulk],ticks=new int[]{400,320,267}[speed];process(be,1);h.assertTrue(be.processTicks==ticks&&be.stock.get(0).isEmpty(),"Module controls actual lanes/time");var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());process(be,ticks+1000);long cost=new long[]{40000,44000,50000}[speed]*(bulk==2?110:bulk==1?105:100)/100*lanes;h.assertTrue(count(be,"iron")==lanes&&be.getPower()+be.fuelHeat+be.heatCredit+be.stock.get(13).getCount()*320000L==640000-cost&&be.experience>0,"Furnace exact output/cost/XP, speed="+speed+" bulk="+bulk);}
        h.succeed();
    }
    @GameTest(template="empty")
    public static void automatedCraftingReturnsBucketsAndChargesOnlyOnce(GameTestHelper h){
        var be=machine(h,"autocrafter");be.template="minecraft:cake";be.setPower(10000);
        for(int i=0;i<3;i++)be.stock.set(i,new ItemStack(Items.MILK_BUCKET));be.stock.set(3,new ItemStack(Items.SUGAR));be.stock.set(4,new ItemStack(Items.EGG));be.stock.set(5,new ItemStack(Items.SUGAR));for(int i=6;i<9;i++)be.stock.set(i,new ItemStack(Items.WHEAT));process(be,40);
        int cakes=be.stock.subList(9,13).stream().filter(s->s.is(Items.CAKE)).mapToInt(ItemStack::getCount).sum(),buckets=be.stock.subList(9,13).stream().filter(s->s.is(Items.BUCKET)).mapToInt(ItemStack::getCount).sum();h.assertTrue(cakes==1&&buckets==3&&be.getPower()==6000&&be.stock.subList(0,9).stream().allMatch(ItemStack::isEmpty),"Autocrafter uses real recipe, returns 3 buckets, spends 4k once");process(be,40);h.assertTrue(be.getPower()==6000,"Missing inputs never charge");h.succeed();
    }
    @GameTest(template="empty")
    public static void wearUpgradeIsTenPercentAndCannotStack(GameTestHelper h){
        var pick=new ItemStack(Items.DIAMOND_PICKAXE);pick.setDamageValue(156);var input=CraftingInput.of(2,1,List.of(pick,material("wear",1)));var recipe=new BasicCrystalRecipe(CraftingBookCategory.EQUIPMENT,"reinforce_wear");h.assertTrue(recipe.matches(input,h.getLevel()),"Wear installs on a normal pick");var out=recipe.assemble(input,h.getLevel().registryAccess());h.assertTrue(out.getMaxDamage()==pick.getMaxDamage()*110/100&&out.getDamageValue()>156,"10% durable, preserve damage proportion");h.assertTrue(!recipe.matches(CraftingInput.of(2,1,List.of(out,material("wear",1))),h.getLevel())&&new BasicCrystalRecipe(CraftingBookCategory.EQUIPMENT,"reinforce_tool").matches(CraftingInput.of(2,1,List.of(out,material("brace",1))),h.getLevel()),"Wear cannot repeat but can progress to 20% phase reinforcement");
        var upgraded=new ReinforcedSmithingRecipe().assemble(new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),out,new ItemStack(Items.NETHERITE_INGOT)),h.getLevel().registryAccess());h.assertTrue(upgraded.getMaxDamage()==new ItemStack(Items.NETHERITE_PICKAXE).getMaxDamage()*110/100,"Smithing retains 10% instead of granting 20%");h.succeed();
    }
    @GameTest(template="empty")
    public static void miningBagAutoCollectPersistsAndNeverSwallowsContainers(GameTestHelper h){
        var p=player(h);var bag=material("sample_case",1);var data=CatalogueEquipment.getData(bag);data.putBoolean("AutoCollect",true);CatalogueEquipment.setData(bag,data);p.getInventory().setItem(0,bag);p.getInventory().setItem(1,new ItemStack(Items.RAW_IRON,64));p.getInventory().setItem(2,new ItemStack(Items.CHEST));MiningBagItem.collect(p);
        var contents=bag.get(DataComponents.CONTAINER);h.assertTrue(contents!=null&&contents.stream().mapToInt(ItemStack::getCount).sum()==64&&p.getInventory().getItem(1).isEmpty()&&p.getInventory().getItem(2).is(Items.CHEST),"Bag collects minerals into persistent storage, never containers");MiningBagItem.collect(p);h.assertTrue(bag.get(DataComponents.CONTAINER).stream().mapToInt(ItemStack::getCount).sum()==64,"No duplicate collection");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty")
    public static void lunarImportRemainsUnavailableAfterLedgerReload(GameTestHelper h){
        var p=player(h);var ledger=new SupplyLedger();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_INGOT,9));var before=ledger.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()).copy();for(int i=0;i<9;i++)h.assertTrue(ledger.importLunar(p,InteractionHand.MAIN_HAND)==SupplyLedger.Result.UNAVAILABLE,"Future-dimension imports stay unavailable on every attempt");h.assertTrue(p.getMainHandItem().getCount()==9&&before.equals(ledger.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess())),"Rejected imports preserve iron and ledger");var restored=SupplyLedger.load(before,h.getLevel().registryAccess());h.assertTrue(restored.lunarRemaining(h.getLevel())==0&&restored.importLunar(p,InteractionHand.MAIN_HAND)==SupplyLedger.Result.UNAVAILABLE,"Reload cannot reopen import quota");p.getInventory().clearContent();h.succeed();
    }

    @GameTest(template="empty")
    public static void bioprocessorAcceptsWholeCropLotsAndReusesItsOwnWater(GameTestHelper h){
        var be=machine(h,"bio_processor");be.setPower(320000);be.stock.set(0,new ItemStack(Items.WHEAT,4));be.tanks[0].fill(new FluidStack(Fluids.WATER,2000),FluidAction.EXECUTE);process(be,500);
        h.assertTrue(be.tanks[1].getFluidAmount()==1000&&be.tanks[2].getFluidAmount()==1000,"First crop batch gives fuel + separate return water");
        be.stock.set(0,new ItemStack(Items.CARROT,2));be.stock.set(1,new ItemStack(Items.POTATO,2));be.tanks[0].fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE);process(be,1);h.assertTrue(be.idle()&&be.tanks[2].getFluidAmount()==1000,"Mixed 2+2 crops cannot pretend to be a single four-item lot");
        be.stock.set(0,new ItemStack(Items.CARROT,4));be.stock.set(1,ItemStack.EMPTY);process(be,500);h.assertTrue(be.tanks[0].isEmpty()&&be.tanks[1].getFluidAmount()==2000&&be.tanks[2].getFluidAmount()==1000&&be.getPower()==120000,"Second batch consumes real 1000 cold + 1000 reused, retains 1000 return");h.succeed();
    }
    @GameTest(template="empty")
    public static void publicPowerNetworkSharesOneBudgetAcrossGenerators(GameTestHelper h){
        var node=machine(h,"cable");for(Direction d:List.of(Direction.NORTH,Direction.EAST,Direction.WEST)){h.setBlock(POS.relative(d),CatalogueContent.BLOCKS.get("g05").get());((IndustryBlockEntity)h.getBlockEntity(POS.relative(d))).setPower(50000);}
        h.setBlock(POS.south(),CatalogueContent.BLOCKS.get("refiner").get());var target=(IndustryBlockEntity)h.getBlockEntity(POS.south());IndustryNetworks.tick(node);long left=0;for(Direction d:List.of(Direction.NORTH,Direction.EAST,Direction.WEST))left+=((IndustryBlockEntity)h.getBlockEntity(POS.relative(d))).getPower();h.assertTrue(target.getPower()==2048&&left==150000-2048,"Three producers still share public 2048/t and conserve charge");h.succeed();
    }
    @GameTest(template="empty")
    public static void fluidPipeNetworkSharesOneHundredMillibucketsPerTick(GameTestHelper h){
        var pipe=machine(h,"pipe");for(Direction d:List.of(Direction.NORTH,Direction.WEST)){h.setBlock(POS.relative(d),CatalogueContent.BLOCKS.get("pump").get());((IndustryBlockEntity)h.getBlockEntity(POS.relative(d))).tanks[0].fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE);}
        h.setBlock(POS.south(),StarfallBlueprint.PREPARATION.get());var target=(PreparationBlockEntity)h.getBlockEntity(POS.south());IndustryNetworks.tick(pipe);int left=0;for(Direction d:List.of(Direction.NORTH,Direction.WEST))left+=((IndustryBlockEntity)h.getBlockEntity(POS.relative(d))).tanks[0].getFluidAmount();h.assertTrue(target.water==100&&left==1900,"Two pumps share 100mB/t; output-only pumps cannot absorb another pump's water");h.succeed();
    }
    @GameTest(template="empty")
    public static void valveCannotDrainFromItsFrontOrReverseTheFlow(GameTestHelper h){
        var valve=machine(h,"fluid_valve");h.setBlock(POS.north(),CatalogueContent.BLOCKS.get("pump").get());var source=(IndustryBlockEntity)h.getBlockEntity(POS.north());source.tanks[0].fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE);h.setBlock(POS.south(),StarfallBlueprint.PREPARATION.get());var target=(PreparationBlockEntity)h.getBlockEntity(POS.south());IndustryNetworks.tick(valve);h.assertTrue(target.water==0&&source.tanks[0].getFluidAmount()==1000,"Valve cannot run backwards");h.succeed();
    }
    @GameTest(template="empty")
    public static void auxiliaryOreHarvestRequiresRealIronTierAndNeverRerolls(GameTestHelper h){
        var pos=h.absolutePos(POS);
        for(String key:List.of("rutile","borate")){
            var state=CatalogueContent.ORES.get(key).get().defaultBlockState();var good=Block.getDrops(state,h.getLevel(),pos,null,null,new ItemStack(Items.IRON_PICKAXE));var bad=Block.getDrops(state,h.getLevel(),pos,null,null,new ItemStack(Items.STONE_PICKAXE));h.assertTrue(good.size()==1&&CatalogueContent.matches(key,good.get(0))&&good.get(0).getCount()==1&&bad.isEmpty(),"Auxiliary ore tier/count: "+key);
            var silk=new ItemStack(Items.NETHERITE_PICKAXE);var drops=Block.getDrops(state,h.getLevel(),pos,null,null,silk);h.assertTrue(drops.size()==1&&CatalogueContent.matches(key,drops.get(0)),"Always raw ore, never placeable reroll block");
        }h.assertTrue(CatalogueContent.BLOCKS.get("damper").get().defaultBlockState().is(net.minecraft.tags.BlockTags.DAMPENS_VIBRATIONS),"Damper has actual vanilla vibration absorption");h.succeed();
    }
    @GameTest(template="empty")
    public static void rescueHookPlacesClimbableRopeAndRecoversWithoutFreeString(GameTestHelper h){
        var p=player(h);var anchor=h.absolutePos(POS);p.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(anchor).add(0,0,2));h.setBlock(POS,Blocks.STONE);p.setItemInHand(InteractionHand.MAIN_HAND,material("rescue_hook",1));var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(anchor).add(0,0,.5),Direction.SOUTH,anchor,false);var context=new net.minecraft.world.item.context.UseOnContext(p,InteractionHand.MAIN_HAND,hit);
        h.assertTrue(RopeSystem.use(context).consumesAction()&&h.getLevel().getBlockState(anchor.south()).is(RopeSystem.ROPE.get())&&p.getMainHandItem().getDamageValue()==1,"Successful legal placement creates actual rope for one durability");var rope=h.getLevel().getBlockState(anchor.south());h.assertTrue(rope.isLadder(h.getLevel(),anchor.south(),p),"Rope is a real climbable block");RopeSystem.use(context);h.assertTrue(h.getLevel().getBlockState(anchor.south()).isAir()&&p.getMainHandItem().getDamageValue()==1,"Second use removes owned rope without free material or extra wear");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty")
    public static void staffBlankRejectsEvenValidLegacyAnimaQuality(GameTestHelper h){
        var d=CatalogueContent.recipe("RT-08");List<ItemStack>items=new ArrayList<>();for(var cell:d.getAsJsonObject("craft").getAsJsonArray("cells")){var stack=cell.isJsonNull()?ItemStack.EMPTY:material(CatalogueContent.key(cell),1);if(CatalogueContent.matches("anima_basic",stack))stack.set(CatalogueContent.QUALITY.get(),new MaterialQuality(99,100,80,0,true,"anima"));items.add(stack);}var before=items.stream().map(ItemStack::copy).toList();var recipe=new CatalogueCraftingRecipe("RT-08");var input=CraftingInput.of(3,3,items);h.assertTrue(!recipe.matches(input,h.getLevel())&&recipe.assemble(input,h.getLevel().registryAccess()).isEmpty(),"Reserved staff rejects valid legacy material and direct assembly");for(int i=0;i<items.size();i++)h.assertTrue(ItemStack.matches(items.get(i),before.get(i)),"Rejected staff preserves every input");h.succeed();
    }

    @GameTest(template="empty")
    public static void terminalCalibrationAndBatteryGuideUseRealInteractions(GameTestHelper h){
        var p=player(h);var terminal=machine(h,"terminal");terminal.setPower(100);h.setBlock(POS.south(),StarfallBlueprint.PREPARATION.get());var absolute=h.absolutePos(POS);p.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(absolute).add(0,0,2));var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(absolute),Direction.UP,absolute,false);
        p.setShiftKeyDown(true);p.gameMode.useItemOn(p,h.getLevel(),ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit);p.setShiftKeyDown(false);h.assertTrue(terminal.efficientCharge,"Sneak empty-hand calibrates terminal");p.gameMode.useItemOn(p,h.getLevel(),ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit);h.assertTrue(terminal.getPower()==80,"Actual inspection consumes 20 XH once");
        var battery=machine(h,"s02");p.setItemInHand(InteractionHand.MAIN_HAND,material("guide",2));p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,hit);h.assertTrue(battery.efficientCharge&&p.getMainHandItem().getCount()==1,"Installing guide consumes exactly one component");var energy=battery.energyPort(Direction.UP);energy.receiveEnergy(1000,false);h.assertTrue(battery.getPower()==950,"Installed guide really changes charging to 95%");p.getInventory().clearContent();h.succeed();
    }

    @GameTest(template="empty")
    public static void sealedSampleRejectsReservedLiningAndUnpacksExactlyOnce(GameTestHelper h){
        var p=player(h);var box=material("box",1);var sample=material("common",2);sample.set(CatalogueContent.QUALITY.get(),new MaterialQuality(90,72,60,1,true,"common"));p.setItemInHand(InteractionHand.MAIN_HAND,box);p.setItemInHand(InteractionHand.OFF_HAND,sample);box.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);h.assertTrue(p.getOffhandItem().getCount()==1&&box.get(DataComponents.CONTAINER).stream().mapToInt(ItemStack::getCount).sum()==1,"Seal exactly one specimen");
        var recipe=new CatalogueCraftingRecipe("ST-15");var liner=material("liner",1);var before=box.copy();var input=CraftingInput.of(2,1,List.of(box,liner));h.assertTrue(!recipe.matches(input,h.getLevel())&&recipe.assemble(input,h.getLevel().registryAccess()).isEmpty(),"Reserved lining cannot be crafted even around a sealed specimen");h.assertTrue(ItemStack.matches(before,box)&&liner.getCount()==1,"Rejected lining preserves box, specimen quality, and liner");
        p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.setShiftKeyDown(true);box.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);h.assertTrue(p.getOffhandItem().getCount()==1&&MaterialQuality.of(p.getOffhandItem()).contamination()==1&&MaterialQuality.of(p.getOffhandItem()).integrity()==72&&box.get(DataComponents.CONTAINER).stream().allMatch(ItemStack::isEmpty),"Unpack original specimen and its quality atomically once");p.getInventory().setItem(1,p.getOffhandItem().copy());p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);box.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);h.assertTrue(p.getOffhandItem().isEmpty()&&p.getInventory().getItem(1).getCount()==1&&box.get(DataComponents.CONTAINER).stream().allMatch(ItemStack::isEmpty),"Repeated empty unpack cannot duplicate specimen");p.setShiftKeyDown(false);p.getInventory().clearContent();h.succeed();
    }
}
