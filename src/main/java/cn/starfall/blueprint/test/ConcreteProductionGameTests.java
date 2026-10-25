package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.ItemHandlerHelper;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class ConcreteProductionGameTests {
    private static final BlockPos CRAFT = new BlockPos(1, 4, 1);
    private static final BlockPos PREP = new BlockPos(1, 2, 1);
    private static IndustryBlockEntity machine(GameTestHelper h, BlockPos p, String key) {
        h.setBlock(p, Blocks.AIR);
        h.setBlock(p, key.equals("prep") ? StarfallBlueprint.PREPARATION.get() : CatalogueContent.BLOCKS.get(key).get());
        var be = (IndustryBlockEntity) h.getBlockEntity(p);
        if (be instanceof PreparationBlockEntity prep) { prep.materialMode = true; h.getLevel().invalidateCapabilities(h.absolutePos(p)); }
        return be;
    }
    private static Item vanilla(String name) { return BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(name)); }
    private static void process(IndustryBlockEntity be, int ticks) { for (int i = 0; i < ticks; i++) IndustryProcessing.tick(be); }
    private static int output(IndustryBlockEntity be, Item item) {
        return be.stock.subList(9, 13).stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static final class MenuPlayer extends net.neoforged.neoforge.common.util.FakePlayer {
        MenuPlayer(net.minecraft.server.level.ServerLevel level){super(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PreparationMenu"));setGameMode(net.minecraft.world.level.GameType.SURVIVAL);}
        @Override public OptionalInt openMenu(net.minecraft.world.MenuProvider provider,java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> writer){
            containerMenu=provider.createMenu(1,getInventory(),this);return OptionalInt.of(1);
        }
    }
    private static void openBench(GameTestHelper h,MenuPlayer player){
        var pos=h.absolutePos(PREP);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setShiftKeyDown(false);
        player.gameMode.useItemOn(player,h.getLevel(),ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false));
    }
    @GameTest(template="empty")
    public static void oldTwoInventorySavesMergeWithoutLosingOverflowOrDuplicatingItems(GameTestHelper h){
        h.setBlock(PREP,StarfallBlueprint.PREPARATION.get());var be=(PreparationBlockEntity)h.getBlockEntity(PREP);
        be.stock.set(0,new ItemStack(Items.RED_CONCRETE_POWDER,8));be.stock.set(13,new ItemStack(Items.CHARCOAL,2));
        var tag=be.saveWithFullMetadata(h.getLevel().registryAccess());var old=NonNullList.withSize(5,ItemStack.EMPTY);
        old.set(0,new ItemStack(StarfallBlueprint.RAW.get()));old.set(1,new ItemStack(Items.COAL));old.set(2,new ItemStack(StarfallBlueprint.CRYSTAL.get(),5));old.set(3,new ItemStack(StarfallBlueprint.TAILINGS.get()));old.set(4,new ItemStack(StarfallBlueprint.CONNECTOR.get()));
        net.minecraft.world.ContainerHelper.saveAllItems(tag,old,h.getLevel().registryAccess());tag.putBoolean("MaterialMode",false);
        be.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(be.stock.get(0).getCount()==8&&be.stock.get(1).is(StarfallBlueprint.RAW.get())&&output(be,StarfallBlueprint.CRYSTAL.get())==5&&output(be,StarfallBlueprint.TAILINGS.get())==1&&be.stock.get(19).is(StarfallBlueprint.CONNECTOR.get()),"Old ore inventory merges around existing material inventory rather than overwriting it");
        var saved=be.saveWithFullMetadata(h.getLevel().registryAccess());var overflow=NonNullList.withSize(5,ItemStack.EMPTY);net.minecraft.world.ContainerHelper.loadAllItems(saved,overflow,h.getLevel().registryAccess());
        h.assertTrue(overflow.get(1).is(Items.COAL)&&be.stock.get(13).is(Items.CHARCOAL)&&be.stock.get(13).getCount()==2,"Conflicting legacy fuel is retained in save data until the shared fuel slot has room");
        be.loadWithComponents(saved,h.getLevel().registryAccess());be.stock.set(13,ItemStack.EMPTY);
        be.createMenu(1,new net.minecraft.world.entity.player.Inventory(null),null);
        h.assertTrue(be.stock.get(13).is(Items.COAL)&&be.stock.get(13).getCount()==1&&output(be,StarfallBlueprint.CRYSTAL.get())==5,"Deferred migration moves overflow exactly once after room becomes available");h.succeed();
    }
    @GameTest(template="empty")
    public static void unifiedPreparationAcceptsPowderAndOreWithoutModes(GameTestHelper h){
        h.setBlock(PREP,StarfallBlueprint.PREPARATION.get());var be=(PreparationBlockEntity)h.getBlockEntity(PREP);
        var player=new MenuPlayer(h.getLevel());player.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(PREP)).add(0,0,1));
        be.materialMode=false;be.template="PC-R02";
        openBench(h,player);h.assertTrue(player.containerMenu instanceof IndustryMenu,"Ordinary interaction always opens one unified preparation inventory");
        player.getInventory().setItem(9,new ItemStack(Items.WHITE_CONCRETE_POWDER,8));
        var material=(IndustryMenu)player.containerMenu;material.clicked(27,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player);
        h.assertTrue(player.getInventory().getItem(9).isEmpty()&&be.stock.get(0).is(Items.WHITE_CONCRETE_POWDER)&&be.stock.get(0).getCount()==8,"Actual material-menu Shift insertion accepts all eight powders");
        var inlet=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(PREP),Direction.UP);
        h.assertTrue(inlet!=null&&ItemHandlerHelper.insertItemStacked(inlet,new ItemStack(Items.WHITE_CONCRETE_POWDER),true).isEmpty(),"The fixed top port accepts powder without a mode flag");
        be.water=1000;be.setPower(4000);
        for(int i=0;i<10;i++)PreparationBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);
        String reserved=be.activeRecipe;openBench(h,player);
        h.assertTrue(player.containerMenu instanceof IndustryMenu&&be.progressTicks==10&&be.activeRecipe.equals(reserved),"Normal right-click preserves the unified inventory and reserved batch");
        for(int i=0;i<30;i++)PreparationBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);
        h.assertTrue(output(be,Items.WHITE_CONCRETE)==8&&be.water==0&&be.getPower()==0&&be.idle(),"Actual machine tick completes same-color 8:8 hydration for one bucket and 4000 XH after reopening");
        var raw=new ItemStack(StarfallBlueprint.RAW.get());raw.set(StarfallBlueprint.BATCH.get(),MaterialBatch.ore(12,40));
        player.getInventory().setItem(9,raw);material=(IndustryMenu)player.containerMenu;material.clicked(27,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player);
        h.assertTrue(player.getInventory().getItem(9).isEmpty()&&be.stock.get(0).is(StarfallBlueprint.RAW.get())&&!material.canSelectRecipe(),"The exact same input slots accept ore and need no mode or recipe selection");
        be.water=250;be.setPower(40000);
        for(int i=0;i<200;i++)PreparationBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);
        h.assertTrue(output(be,Items.WHITE_CONCRETE)==8&&output(be,StarfallBlueprint.CRYSTAL.get())==10&&output(be,StarfallBlueprint.TAILINGS.get())==1&&be.water==0&&be.getPower()==0,"The same bench automatically completes ore processing after hydration and shares four output slots");
        var tag=be.saveWithFullMetadata(h.getLevel().registryAccess());var copy=new PreparationBlockEntity(be.getBlockPos(),be.getBlockState());copy.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(output(copy,Items.WHITE_CONCRETE)==8&&output(copy,StarfallBlueprint.CRYSTAL.get())==10,"Unified inventory persists without a selected mode");h.succeed();
    }

    @GameTest(template = "empty")
    public static void allSixteenColoursUseRealVanillaPowderRecipesAndWetPreparation(GameTestHelper h) {
        for (DyeColor colour : DyeColor.values()) {
            String name = colour.getName();
            var craft = machine(h, CRAFT, "autocrafter");
            craft.template = "minecraft:" + name + "_concrete_powder";
            craft.setPower(4000);
            // Stacked, unordered inputs including the last page of the material inventory.
            craft.setItem(8, new ItemStack(Items.SAND, 4));
            craft.setItem(1, new ItemStack(vanilla(name + "_dye")));
            craft.setItem(71, new ItemStack(Items.GRAVEL, 4));
            process(craft, 40);
            Item powder = vanilla(name + "_concrete_powder"), concrete = vanilla(name + "_concrete");
            h.assertTrue(output(craft, powder) == 8 && craft.getPower() == 0 && craft.idle(), "Real vanilla powder recipe and 4k XH: " + name);
            h.assertTrue(Arrays.stream(craft.craftingInputSlots()).allMatch(i -> craft.getItem(i).isEmpty()), "No leftover or duplicated sand, gravel or dye: " + name);
            var prep = machine(h, PREP, "prep");
            prep.setPower(4000);
            var inlet = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(PREP), Direction.UP);
            var water = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(PREP), Direction.NORTH);
            h.assertTrue(inlet != null && ItemHandlerHelper.insertItemStacked(inlet, new ItemStack(powder, 8), false).isEmpty(), "Material-mode capability accepts powder: " + name);
            h.assertTrue(water != null && water.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000, "Standard cold-water inlet: " + name);
            process(prep, 40);
            h.assertTrue(prep.idle() && output(prep, concrete) == 8 && prep.getPower() == 0 && prep.tanks[0].isEmpty(), "Same colour, exact 8:8 solid conversion, 1 bucket and 4k XH: " + name);
            h.assertTrue(prep.tanks[1].isEmpty() && prep.tanks[2].isEmpty(), "No free water or buckets are returned: " + name);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hydrationPausesWithoutMixingColoursOrReservingTwiceAcrossReload(GameTestHelper h) {
        var prep = (PreparationBlockEntity) machine(h, PREP, "prep"); prep.setPower(1700);
        prep.stock.set(0, new ItemStack(Items.RED_CONCRETE_POWDER, 7));
        prep.stock.set(1, new ItemStack(Items.BLUE_CONCRETE_POWDER));
        prep.waterPort().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        process(prep, 10);
        h.assertTrue(prep.idle() && prep.water == 1000 && prep.getPower() == 1700, "Seven red plus one blue cannot satisfy an eight-red batch");
        prep.stock.get(0).grow(1); prep.water = 999; process(prep, 10);
        h.assertTrue(prep.idle() && prep.stock.get(0).getCount() == 8 && prep.getPower() == 1700, "Insufficient water never reserves powder or energy");
        prep.water = 1000;
        for (int i = 9; i < 13; i++) prep.stock.set(i, new ItemStack(Items.COBBLESTONE, 64));
        process(prep, 10);
        h.assertTrue(prep.idle() && prep.water == 1000 && prep.stock.get(0).getCount() == 8, "Full output refuses a new batch without consuming water or powder");
        for (int i = 9; i < 13; i++) prep.stock.set(i, ItemStack.EMPTY);
        process(prep, 30);
        h.assertTrue(prep.progressTicks == 17 && prep.getPower() == 0 && prep.water == 0 && prep.stock.get(0).isEmpty() && prep.stock.get(1).getCount() == 1, "Power starvation holds one reserved red batch and leaves blue untouched");
        var tag = prep.saveWithoutMetadata(h.getLevel().registryAccess());
        prep.loadWithComponents(tag, h.getLevel().registryAccess());
        for (int i = 9; i < 13; i++) prep.stock.set(i, new ItemStack(Items.COBBLESTONE, 64));
        prep.setPower(2300); process(prep, 20);
        h.assertTrue(prep.progressTicks == 17 && prep.getPower() == 2300 && prep.water == 0, "In-flight backpressure consumes neither another batch nor more energy");
        for (int i = 9; i < 13; i++) prep.stock.set(i, ItemStack.EMPTY);
        process(prep, 23);
        h.assertTrue(prep.idle() && output(prep, Items.RED_CONCRETE) == 8 && prep.getPower() == 0 && prep.materialMode, "Exactly 23 remaining ticks after NBT reload, full 4k cost and same mode");
        process(prep, 80);
        h.assertTrue(output(prep, Items.RED_CONCRETE) == 8 && output(prep, Items.BLUE_CONCRETE) == 0 && prep.stock.get(1).getCount() == 1, "Idle ticks cannot duplicate concrete or recolour spare material");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 560)
    public static void coalPoweredPumpAndHoppersRunTwoCompleteConcreteBatches(GameTestHelper h) {
        var craft = machine(h, CRAFT, "autocrafter"); craft.template = "minecraft:red_concrete_powder";
        var prep = (PreparationBlockEntity) machine(h, PREP, "prep");
        // A horizontal extractor supplies the crafter; powder enters preparation from UP.
        // The preparation bench's side face is reserved for fuel, not powder.
        BlockPos input = new BlockPos(3, 4, 1), powderHopper = CRAFT.below(), outputHopper = PREP.below(), chestPos = outputHopper.below();
        h.setBlock(input, Blocks.CHEST);
        var supply = (ChestBlockEntity) h.getBlockEntity(input);
        supply.setItem(0, new ItemStack(Items.SAND, 8)); supply.setItem(1, new ItemStack(Items.GRAVEL, 8)); supply.setItem(2, new ItemStack(Items.RED_DYE, 2));
        machine(h, new BlockPos(2, 4, 1), "item_extractor");
        h.setBlock(new BlockPos(2, 4, 1), CatalogueContent.BLOCKS.get("item_extractor").get().defaultBlockState().setValue(IndustryBlock.FACING, Direction.WEST));
        h.setBlock(powderHopper, Blocks.HOPPER);
        h.setBlock(outputHopper, Blocks.HOPPER); h.setBlock(chestPos, Blocks.CHEST);
        BlockPos pumpPos = new BlockPos(2, 2, 3), waterPos = pumpPos.below();
        var pump = machine(h, pumpPos, "pump");
        h.setBlock(waterPos.below(), Blocks.STONE);
        for (Direction d : Direction.Plane.HORIZONTAL) h.setBlock(waterPos.relative(d), Blocks.STONE);
        h.setBlock(waterPos, Blocks.WATER);
        machine(h, new BlockPos(2, 2, 2), "pipe"); machine(h, new BlockPos(2, 2, 1), "pipe");
        var generator = machine(h, new BlockPos(0, 3, 3), "g01"); generator.stock.set(13, new ItemStack(Items.COAL));
        for (BlockPos p : List.of(new BlockPos(1, 3, 3), new BlockPos(1, 3, 2), new BlockPos(1, 4, 2), new BlockPos(1, 2, 2), new BlockPos(2, 3, 3))) machine(h, p, "cable");
        h.runAfterDelay(500, () -> {
            var chest = (ChestBlockEntity) h.getBlockEntity(chestPos);
            int count = 0; for (int i = 0; i < chest.getContainerSize(); i++) { var s = chest.getItem(i); h.assertTrue(s.isEmpty() || s.is(Items.RED_CONCRETE), "Finished chest contains only red concrete"); count += s.getCount(); }
            h.assertTrue(count == 16 && craft.idle() && prep.idle(), "Extractor -> real powder recipe -> top hopper -> wet preparation -> bottom hopper -> chest must finish two batches; count=" + count + " craft=" + craft.activeRecipe + "/" + craft.getPower() + " prep=" + prep.activeRecipe + "/" + prep.getPower() + "/water=" + prep.water + " pump=" + pump.tanks[0].getFluidAmount());
            h.assertTrue(supply.isEmpty() && ((HopperBlockEntity) h.getBlockEntity(powderHopper)).isEmpty() && ((HopperBlockEntity) h.getBlockEntity(outputHopper)).isEmpty(), "No intermediate materials remain in hoppers");
            h.assertTrue(Arrays.stream(craft.craftingInputSlots()).allMatch(i -> craft.getItem(i).isEmpty()) && prep.stock.subList(0, 13).stream().allMatch(ItemStack::isEmpty), "Exactly 8 sand, 8 gravel and 2 dyes become 16 concrete, no hidden leftovers");
            int pumped = pump.tanks[0].getFluidAmount() + prep.water + 2000;
            h.assertTrue(pumped % 1000 == 0 && pump.progressTicks == 0, "Water production consists of whole buckets with no partial work left");
            long retained = generator.getPower() + generator.fuelHeat + craft.getPower() + prep.getPower() + pump.getPower();
            h.assertTrue(generator.stock.get(13).isEmpty() && retained == 320000L - 16000 - pumped, "One coal ledger conserves both crafts, both hydration batches and all actually pumped water");
            h.succeed();
        });
    }
}
