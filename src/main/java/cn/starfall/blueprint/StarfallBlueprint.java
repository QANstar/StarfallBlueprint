package cn.starfall.blueprint;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.*;

@Mod(StarfallBlueprint.ID)
public final class StarfallBlueprint {
    public static final String ID = "starfall_blueprint";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, ID);
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, ID);
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeType<?>, net.minecraft.world.item.crafting.RecipeType<AlloyRecipe>> ALLOY_TYPE = RECIPE_TYPES.register("alloy_mixing", () -> net.minecraft.world.item.crafting.RecipeType.simple(id("alloy_mixing")));
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, AlloyRecipe.Serializer> ALLOY_RECIPE = RECIPES.register("alloy_mixing", AlloyRecipe.Serializer::new);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MaterialBatch>> BATCH = COMPONENTS.register("material_batch",
            () -> DataComponentType.<MaterialBatch>builder().persistent(MaterialBatch.CODEC).networkSynchronized(MaterialBatch.STREAM_CODEC).build());
    public static final DeferredBlock<Block> ORE = BLOCKS.register("ore_meteor_phase", () -> new MeteorOreBlock(BlockBehaviour.Properties.of().strength(3.5f, 3).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> DEEP_ORE = BLOCKS.register("deepslate_ore_meteor_phase", () -> new MeteorOreBlock(BlockBehaviour.Properties.of().strength(4.5f, 3).requiresCorrectToolForDrops()));
    public static final DeferredBlock<PreparationBlock> PREPARATION = BLOCKS.register("preparation_bench", () -> new PreparationBlock(BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredItem<BlockItem> ORE_ITEM = ITEMS.registerSimpleBlockItem(ORE);
    public static final DeferredItem<BlockItem> DEEP_ORE_ITEM = ITEMS.registerSimpleBlockItem(DEEP_ORE);
    public static final DeferredItem<BlockItem> BENCH_ITEM = ITEMS.registerSimpleBlockItem(PREPARATION);
    public static final DeferredBlock<AlloyFurnaceBlock> ALLOY_FURNACE = BLOCKS.register("alloy_furnace", () -> new AlloyFurnaceBlock(BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredItem<BlockItem> ALLOY_ITEM = ITEMS.registerSimpleBlockItem(ALLOY_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlloyFurnaceBlockEntity>> ALLOY_ENTITY = ENTITIES.register("alloy_furnace", () -> BlockEntityType.Builder.of(AlloyFurnaceBlockEntity::new, ALLOY_FURNACE.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<AlloyFurnaceMenu>> ALLOY_MENU = MENUS.register("alloy_furnace", () -> new MenuType<>(AlloyFurnaceMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredBlock<CampWaterBlock> WATER_TANK = BLOCKS.register("water_tank", () -> new CampWaterBlock(BlockBehaviour.Properties.of().strength(2f).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredBlock<CampWaterBlock> RAIN_COLLECTOR = BLOCKS.register("rain_collector", () -> new CampWaterBlock(BlockBehaviour.Properties.of().strength(2f).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredItem<BlockItem> TANK_ITEM = ITEMS.registerSimpleBlockItem(WATER_TANK);
    public static final DeferredItem<BlockItem> RAIN_ITEM = ITEMS.registerSimpleBlockItem(RAIN_COLLECTOR);
    public static final DeferredBlock<SupplyTerminalBlock> SUPPLY_TERMINAL = BLOCKS.register("supply_terminal", () -> new SupplyTerminalBlock(BlockBehaviour.Properties.of().strength(2f).noOcclusion()));
    public static final DeferredItem<BlockItem> SUPPLY_TERMINAL_ITEM = ITEMS.registerSimpleBlockItem(SUPPLY_TERMINAL);
    public static final DeferredItem<Item> RAW = ITEMS.registerItem("raw_meteor_phase", p -> new MaterialItem(p, MaterialBatch.ore(12, 40)));
    public static final DeferredItem<Item> CRYSTAL = ITEMS.registerItem("stable_crystal", p -> new MaterialItem(p, MaterialBatch.crystal(38)));
    public static final DeferredItem<Item> TAILINGS = ITEMS.registerItem("wet_tailings", p -> new MaterialItem(p.stacksTo(16), MaterialBatch.tailings(12)));
    public static final DeferredItem<Item> CONNECTOR = ITEMS.registerSimpleItem("efficient_connector");
    public static final DeferredItem<Item> TOOL_BRACE = ITEMS.registerSimpleItem("tool_brace");
    public static final DeferredItem<Item> SHIELD_LINING = ITEMS.registerSimpleItem("shield_lining");
    public static final DeferredItem<Item> SCANNER = ITEMS.registerItem("ore_indicator", OreIndicatorItem::new);
    public static final DeferredItem<Item> GUIDE = ITEMS.registerItem("field_manual", FieldManualItem::new);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PORTION_MASS = COMPONENTS.register("portion_mass",
            () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(1, 100)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final java.util.Map<String, DeferredItem<Item>> STRUCTURAL_ITEMS = new java.util.LinkedHashMap<>();
    static {
        for (String name : new String[]{"machine_frame", "iron_plate", "copper_plate", "iron_fasteners", "mixed_steel_billet", "ordinary_steel", "ceramic", "insulation", "binder", "isolation_substrate", "control_board", "circuit_breaker", "ceramic_sheath", "steel_fasteners", "sealed_box", "carbon_portion", "quartz_portion"})
            STRUCTURAL_ITEMS.put(name, ITEMS.registerItem(name, p -> name.equals("sealed_box")?new SealedSampleItem(p):new StructuralMaterialItem(p, name)));
    }
    public static Item structural(String name) { return STRUCTURAL_ITEMS.get(name).get(); }
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<WholeSubstrateRecipe>> SUBSTRATE_RECIPE = RECIPES.register("isolation_substrate", () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(WholeSubstrateRecipe::new));
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<RainCollectorRecipe>> RAIN_RECIPE = RECIPES.register("rain_collector", () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(RainCollectorRecipe::new));
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<ReinforcedRepairRecipe>> REINFORCED_REPAIR_RECIPE = RECIPES.register("reinforced_repair", () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(ReinforcedRepairRecipe::new));
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, ReinforcedSmithingRecipe.Serializer> REINFORCED_SMITHING_RECIPE = RECIPES.register("reinforced_smithing", ReinforcedSmithingRecipe.Serializer::new);
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, BulkCraftingRecipe.Serializer> BULK_CRAFTING_RECIPE = RECIPES.register("bulk_crafting", BulkCraftingRecipe.Serializer::new);
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeType<?>, net.minecraft.world.item.crafting.RecipeType<BulkCraftingRecipe>> BULK_CRAFTING_TYPE = RECIPE_TYPES.register("bulk_crafting", () -> new net.minecraft.world.item.crafting.RecipeType<>() {});
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> REINFORCED = COMPONENTS.register("reinforced", () -> DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
    public static final java.util.Map<String, DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<BasicCrystalRecipe>>> BASIC_RECIPES = new java.util.LinkedHashMap<>();
    static {
        for (String kind : new String[]{"connector", "indicator", "tool_brace", "shield_lining", "reinforce_tool", "reinforce_shield", "reinforce_wear", "reinforce_composite"}) {
            BASIC_RECIPES.put(kind, RECIPES.register(kind, () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(category -> new BasicCrystalRecipe(category, kind))));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PreparationBlockEntity>> PREPARATION_ENTITY = ENTITIES.register("preparation_bench",
            () -> BlockEntityType.Builder.of(PreparationBlockEntity::new, PREPARATION.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CampWaterBlockEntity>> WATER_ENTITY = ENTITIES.register("camp_water",
            () -> BlockEntityType.Builder.of(CampWaterBlockEntity::new, WATER_TANK.get(), RAIN_COLLECTOR.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<PreparationMenu>> PREPARATION_MENU = MENUS.register("preparation_bench",
            () -> new MenuType<>(PreparationMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("industry", () -> CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.starfall_blueprint"))
            .icon(() -> new ItemStack(CRYSTAL.get())).displayItems((parameters, output) -> {
                output.accept(BestEngineering.WORKBENCH_ITEM.get());output.accept(GUIDE.get()); output.accept(Blueprints.BUILDER_ITEM.get());output.accept(Blueprints.PLAN.get());output.accept(Blueprints.SURVEYOR.get()); output.accept(BENCH_ITEM.get()); output.accept(TANK_ITEM.get()); output.accept(RAIN_ITEM.get()); output.accept(SUPPLY_TERMINAL_ITEM.get());
                for (int grade : new int[]{6,12,24}) {
                    ItemStack ore = new ItemStack(RAW.get()); ore.set(BATCH.get(), MaterialBatch.ore(grade, 40)); output.accept(ore);
                }
                output.accept(ORE_ITEM.get()); output.accept(DEEP_ORE_ITEM.get());
                output.accept(CRYSTAL.get()); output.accept(CONNECTOR.get()); output.accept(TOOL_BRACE.get()); output.accept(SHIELD_LINING.get()); output.accept(SCANNER.get()); output.accept(TAILINGS.get());
                output.accept(Blueprints.CREATIVE_CORE.get());output.accept(ALLOY_ITEM.get());
                STRUCTURAL_ITEMS.forEach((name, item) -> { if (!name.endsWith("_portion")) output.accept(item.get()); });
                CatalogueContent.ITEMS.forEach((key, item) -> { if (!CatalogueContent.RESERVED_ITEMS.contains(key)&&!CatalogueContent.RETIRED_BASES.contains(key)) output.accept(item.get()); });
                CatalogueFluids.ENTRIES.values().forEach(fluid -> output.accept(fluid.bucket().get()));
            }).build());

    public StarfallBlueprint(IEventBus bus) {
        CatalogueContent.initialize(); CatalogueEquipment.initialize(); RopeSystem.initialize();Blueprints.initialize();AggregateMachines.initialize();BestEngineering.initialize();
        CatalogueFluids.register(bus);
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); COMPONENTS.register(bus); TABS.register(bus); RECIPES.register(bus); RECIPE_TYPES.register(bus);
        bus.addListener(this::capabilities);
        bus.addListener(BlueprintNetworking::register);
        NeoForge.EVENT_BUS.addListener(this::bucketInteraction);
        NeoForge.EVENT_BUS.addListener(CatalogueEquipment::playerTick);
        NeoForge.EVENT_BUS.addListener(CatalogueEquipment::breakSpeed);
        NeoForge.EVENT_BUS.addListener(BestEngineering::playerTick);
        NeoForge.EVENT_BUS.addListener(BestEngineering::breakSpeed);
        NeoForge.EVENT_BUS.addListener(BestEngineering::clonePlayer);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,BestEngineering::breakBlock);
        if (System.getProperty("starfall.verifyRestart") != null)
            cn.starfall.blueprint.test.RestartVerification.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, ReinforcedRepairHooks::grindstone);
    }
    private void bucketInteraction(PlayerInteractEvent.RightClickBlock event) {
        if(event.getItemStack().is(Blueprints.SURVEYOR.get())){
            // Surveying a chest or machine must select the corner instead of opening its menu.
            event.setUseBlock(TriState.FALSE);event.setUseItem(TriState.TRUE);
        }
        // Vanilla buckets do not bypass sneak-use, so opt into the bench's documented draw-water action.
        if (event.getUseBlock() != TriState.FALSE && event.getEntity().isShiftKeyDown()
                && (event.getItemStack().is(Items.BUCKET)||event.getItemStack().isEmpty())
                && event.getLevel().getBlockState(event.getPos()).is(PREPARATION.get())) {
            event.setUseBlock(TriState.TRUE);
        }
    }
    private void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,AggregateMachines.PART_ENTITY.get(),(part,side)->{var owner=part.owner();return owner!=null&&owner.hasFluidPort()?owner.fluidPort():null;});
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,AggregateMachines.PART_ENTITY.get(),(part,side)->{var owner=part.owner();return owner==null?null:owner.energyPort(side);});
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,AggregateMachines.PART_ENTITY.get(),(part,side)->{var owner=part.owner();return owner==null?null:new SidedInvWrapper(owner.workInventory(),side==null?net.minecraft.core.Direction.UP:side);});
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,Blueprints.ENTITY.get(),(be,side)->new SidedInvWrapper(be,side==null?net.minecraft.core.Direction.UP:side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CatalogueContent.ENTITY.get(), (be, side) -> new SidedInvWrapper(be, side == null ? net.minecraft.core.Direction.UP : side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CatalogueContent.ENTITY.get(), (be, side) -> be.hasFluidPort()?be.fluidPort():null);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CatalogueContent.ENTITY.get(), (be, side) -> be.hasEnergyPort()?be.energyPort(side):null);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PREPARATION_ENTITY.get(), (be, side) -> be.energyPort(side));
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> CatalogueEquipment.energy(stack), CatalogueContent.item("s01"));
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> BestEquipment.energy(stack), CatalogueContent.item("best_frame"),CatalogueContent.item("best_cutter"));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ALLOY_ENTITY.get(), (be, side) -> new SidedInvWrapper(be, side == null ? net.minecraft.core.Direction.UP : side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PREPARATION_ENTITY.get(), (be, side) -> new SidedInvWrapper(be.workInventory(), side == null ? net.minecraft.core.Direction.UP : side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PREPARATION_ENTITY.get(), (be, side) -> be.fluidPort());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, WATER_ENTITY.get(), (be, side) -> be.tank);
    }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }
}
