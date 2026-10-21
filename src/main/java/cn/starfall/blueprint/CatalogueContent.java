package cn.starfall.blueprint;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.*;

/** Canonical IDs bridge the game registry and the offline catalogue. */
public final class CatalogueContent {
    public static final JsonObject DATA;
    public static final Map<String,JsonObject> DEFINITIONS=new LinkedHashMap<>(), RECIPES=new LinkedHashMap<>();
    public static final Map<String,DeferredItem<Item>> ITEMS=new LinkedHashMap<>();
    public static final Map<String,DeferredBlock<IndustryBlock>> BLOCKS=new LinkedHashMap<>();
    public static final Map<String,DeferredBlock<Block>> ORES=new LinkedHashMap<>();
    public static final Set<String> MACHINE_KEYS=Set.of("refiner","recycler","g01","g02","g03","g04","pump","dew","cable","pipe","s02","bio_processor","g05","autocrafter","auto_furnace","supply_crate","energy_junction","item_duct","item_extractor","item_router","fluid_valve","terminal","busjoint","damper");
    public static final Set<String> FLUID_KEYS=Set.of("warm","wastewater","bio","returnwater");
    public static final Set<String> TOOLS=Set.of("basic_jig","head","direction_jig","pressure_module","laminate_jig","clean_module","facet_jig");
    public static final DeferredHolder<DataComponentType<?>,DataComponentType<MaterialQuality>> QUALITY=StarfallBlueprint.COMPONENTS.register("material_quality",()->DataComponentType.<MaterialQuality>builder().persistent(MaterialQuality.CODEC).networkSynchronized(MaterialQuality.STREAM).build());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<IndustryBlockEntity>> ENTITY;
    public static final DeferredHolder<MenuType<?>,MenuType<IndustryMenu>> MENU;
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>,CatalogueCraftingRecipe.Serializer> CRAFTING=StarfallBlueprint.RECIPES.register("catalogue_crafting",CatalogueCraftingRecipe.Serializer::new);
    static {
        try(var stream=CatalogueContent.class.getResourceAsStream("/assets/starfall_blueprint/catalogue.json")) {
            if(stream==null)throw new IllegalStateException("Missing canonical catalogue");
            DATA=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
        DATA.getAsJsonObject("items").entrySet().forEach(e->DEFINITIONS.put(e.getKey(),e.getValue().getAsJsonObject()));
        DATA.getAsJsonArray("recipes").forEach(r->RECIPES.put(r.getAsJsonObject().get("id").getAsString(),r.getAsJsonObject()));
        for(String key:List.of("rutile","borate","moon"))ORES.put(key,StarfallBlueprint.BLOCKS.register(key+"_ore",()->new Block(BlockBehaviour.Properties.of().strength(3f,3f).requiresCorrectToolForDrops()){
            public java.util.List<ItemStack>getDrops(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder params){var tool=params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL);if(tool==null||!tool.isCorrectToolForDrops(state))return List.of();var out=stack(key,1);var data=CatalogueEquipment.getData(out);data.putString("Origin",params.getLevel().dimension().location().toString());CatalogueEquipment.setData(out,data);return List.of(out);}
        }));
        for(var e:DEFINITIONS.entrySet()) {
            String key=e.getKey();if(isExisting(key)||key.equals("power")||FLUID_KEYS.contains(key)||e.getValue().get("vanilla")!=null&&e.getValue().get("vanilla").getAsBoolean()||Set.of("water","water_source","trade_lunar").contains(key))continue;
            if(MACHINE_KEYS.contains(key)) {
                var block=StarfallBlueprint.BLOCKS.register(key,()->new IndustryBlock(key,BlockBehaviour.Properties.of().strength(3f).requiresCorrectToolForDrops()));
                BLOCKS.put(key,block);
                ITEMS.put(key,StarfallBlueprint.ITEMS.registerItem(key,p->new BlockItem(block.get(),p)));
            } else if(key.equals("boroglass")) {
                var block=StarfallBlueprint.BLOCKS.register(key,()->new TransparentBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).strength(1.5f)));
                ITEMS.put(key,StarfallBlueprint.ITEMS.registerItem(key,p->new BlockItem(block.get(),p)));
            } else ITEMS.put(key,StarfallBlueprint.ITEMS.registerItem(key,p->CatalogueEquipment.create(key,p)));
        }
        ENTITY=StarfallBlueprint.ENTITIES.register("industry",()->BlockEntityType.Builder.of(IndustryBlockEntity::new,BLOCKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
        MENU=StarfallBlueprint.MENUS.register("industry",()->new MenuType<>(IndustryMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    }
    public static void initialize(){CatalogueFluids.initialize();}
    private static boolean isExisting(String key){return Set.of("ore","common","tail","joint","brace","shield","finder","prep","tank","rain_collector","alloy_furnace","fix","steel_pre","steel","substrate","board","breaker","sheath","steel_fix","box").contains(key)||StarfallBlueprint.STRUCTURAL_ITEMS.containsKey(key);}
    public static Item item(String key) {
        Item old=existing(key);if(old!=null)return old;
        if(ITEMS.containsKey(key))return ITEMS.get(key).get();
        return switch(key){case "iron"->net.minecraft.world.item.Items.IRON_INGOT;case "copper"->net.minecraft.world.item.Items.COPPER_INGOT;
            case "stone"->net.minecraft.world.item.Items.COBBLESTONE;case "plank"->net.minecraft.world.item.Items.OAK_PLANKS;case "log"->net.minecraft.world.item.Items.OAK_LOG;
            case "wool"->net.minecraft.world.item.Items.WHITE_WOOL;case "clay"->net.minecraft.world.item.Items.CLAY_BALL;case "slime"->net.minecraft.world.item.Items.SLIME_BALL;
            case "iron_pickaxe"->net.minecraft.world.item.Items.IRON_PICKAXE;case "workbench"->net.minecraft.world.item.Items.CRAFTING_TABLE;
            default->BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(switch(key){case "coal"->"coal";default->key;}));};
    }
    private static Item existing(String key) {
        return switch(key){case "ore"->StarfallBlueprint.RAW.get();case "common"->StarfallBlueprint.CRYSTAL.get();case "tail"->StarfallBlueprint.TAILINGS.get();
            case "joint"->StarfallBlueprint.CONNECTOR.get();case "brace"->StarfallBlueprint.TOOL_BRACE.get();case "shield"->StarfallBlueprint.SHIELD_LINING.get();case "finder"->StarfallBlueprint.SCANNER.get();
            case "prep"->StarfallBlueprint.BENCH_ITEM.get();case "tank"->StarfallBlueprint.TANK_ITEM.get();case "rain_collector"->StarfallBlueprint.RAIN_ITEM.get();case "alloy_furnace"->StarfallBlueprint.ALLOY_ITEM.get();
            default->{String name=switch(key){case "fix"->"iron_fasteners";case "steel_pre"->"mixed_steel_billet";case "steel"->"ordinary_steel";case "substrate"->"isolation_substrate";case "board"->"control_board";case "breaker"->"circuit_breaker";case "sheath"->"ceramic_sheath";case "steel_fix"->"steel_fasteners";case "box"->"sealed_box";default->key;};
                yield StarfallBlueprint.STRUCTURAL_ITEMS.containsKey(name)?StarfallBlueprint.structural(name):null;}
        };
    }
    public static boolean matches(String key,ItemStack stack) {
        return switch(key){case "coal"->stack.is(net.minecraft.world.item.Items.COAL)||stack.is(net.minecraft.world.item.Items.CHARCOAL);
            case "plank"->stack.is(net.minecraft.tags.ItemTags.PLANKS);case "log"->stack.is(net.minecraft.tags.ItemTags.LOGS);case "wool"->stack.is(net.minecraft.tags.ItemTags.WOOL);
            default->stack.is(item(key));};
    }
    public static ItemStack stack(String key,int count){return new ItemStack(item(key),count);}
    public static String key(ItemStack stack){for(String k:DEFINITIONS.keySet())if(!FLUID_KEYS.contains(k)&&!Set.of("power","water","water_source","trade_lunar").contains(k)&&matches(k,stack))return k;return "";}
    public static JsonObject recipe(String id){return Objects.requireNonNull(RECIPES.get(id),id);}
    public static int qty(JsonElement e){return e.getAsJsonObject().get("qty").getAsInt();}
    public static String key(JsonElement e){return e.getAsJsonObject().get("item").getAsString();}
}
