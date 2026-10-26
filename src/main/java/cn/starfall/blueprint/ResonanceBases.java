package cn.starfall.blueprint;

import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;

/** Non-consumed foundations select canonical vanilla outputs; upgrades never become batch inputs. */
public final class ResonanceBases {
    private ResonanceBases() {}
    private record Route(String source, String output, int count, String recipe) {}
    // Deliberately independent of catalogue tier/category metadata: only these five sources are basic.
    private static final Set<String> BASIC = Set.of("stone", "iron_ore", "deepslate_iron_ore", "copper_ore", "deepslate_copper_ore");
    private static final Map<String, Route> ROUTES = routes();
    private static Map<String, Route> routes() {
        Map<String, Route> routes = new HashMap<>();
        String[] sources = {"stone", "deepslate", "granite", "diorite", "andesite", "tuff", "calcite", "blackstone", "basalt", "end_stone", "netherrack", "obsidian",
                "coal_ore", "deepslate_coal_ore", "iron_ore", "deepslate_iron_ore", "copper_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore",
                "redstone_ore", "deepslate_redstone_ore", "lapis_ore", "deepslate_lapis_ore", "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore",
                "nether_gold_ore", "nether_quartz_ore", "ancient_debris"};
        String[] outputs = {"cobblestone", "cobbled_deepslate", "granite", "diorite", "andesite", "tuff", "calcite", "blackstone", "basalt", "end_stone", "netherrack", "obsidian",
                "coal", "coal", "raw_iron", "raw_iron", "raw_copper", "raw_copper", "raw_gold", "raw_gold", "redstone", "redstone", "lapis_lazuli", "lapis_lazuli",
                "diamond", "diamond", "emerald", "emerald", "gold_nugget", "quartz", "ancient_debris"};
        for (int i = 0; i < sources.length; i++) add(routes, sources[i], outputs[i], i < 12 ? 4 : 1, i + 1);
        add(routes, "amethyst_cluster", "amethyst_shard", 4, 36);
        add(routes, "gilded_blackstone", "gold_nugget", 1, 37);
        add(routes, "glowstone", "glowstone_dust", 4, 38);
        return Map.copyOf(routes);
    }
    private static void add(Map<String, Route> routes, String source, String output, int count, int number) {
        routes.put("resonance_base_" + source, new Route(source, output, count, String.format(Locale.ROOT, "QM-R%02d", number)));
    }
    public static String key(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals(StarfallBlueprint.ID) && CatalogueContent.RESONANCE_BASES.containsKey(id.getPath()) ? id.getPath() : "";
    }
    public static String foundation(IndustryBlockEntity be) {
        if (be.getLevel() == null || !be.getLevel().hasChunkAt(be.getBlockPos().below())) return "";
        return key(be.getLevel().getBlockState(be.getBlockPos().below()));
    }
    private static boolean vanilla(String value, String path) {
        var id = ResourceLocation.tryParse(value);
        return id != null && id.getNamespace().equals("minecraft") && id.getPath().equals(path);
    }
    private static Route canonical(String key) {
        Route route = ROUTES.get(key);
        var definition = CatalogueContent.DEFINITIONS.get(key);
        if (route == null || definition == null) return null;
        try {
            return vanilla(definition.get("resonance_source").getAsString(), route.source())
                    && vanilla(definition.get("resonance_output").getAsString(), route.output())
                    && definition.get("resonance_count").getAsInt() == route.count() ? route : null;
        } catch (RuntimeException invalid) { return null; }
    }
    private static Route recipeRoute(JsonObject recipe) {
        if (recipe == null) return null;
        try {
            Route route = canonical(recipe.get("resonance_base").getAsString());
            if (route == null || !recipe.get("station").getAsString().equals("quarry")
                    || !recipe.get("id").getAsString().equals(route.recipe())) return null;
            var outputs = recipe.getAsJsonArray("outputs");
            if (outputs.size() != 1 || CatalogueContent.qty(outputs.get(0)) != route.count()) return null;
            var item = CatalogueContent.item(CatalogueContent.key(outputs.get(0)));
            return vanilla(BuiltInRegistries.ITEM.getKey(item).toString(), route.output()) ? route : null;
        } catch (RuntimeException invalid) { return null; }
    }
    private static boolean upgraded(IndustryBlockEntity be) {
        return be.stock.get(18).is(CatalogueContent.item("resonance_upgrade"));
    }
    private static boolean permitted(IndustryBlockEntity be, Route route) {
        return BASIC.contains(route.source()) || upgraded(be);
    }
    public static boolean matches(IndustryBlockEntity be, JsonObject recipe) {
        Route route = recipeRoute(recipe);
        return route != null && permitted(be, route)
                && recipe.get("resonance_base").getAsString().equals(foundation(be));
    }
    public static ItemStack output(IndustryBlockEntity be, JsonObject recipe) {
        if (!matches(be, recipe)) return ItemStack.EMPTY;
        Route route = recipeRoute(recipe);
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("minecraft", route.output()));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, route.count());
    }
    /** Must run before aggregate validation, including for retired recipes absent from the catalogue. */
    public static boolean canRun(IndustryBlockEntity be) {
        if (!be.kind().equals("quarry")) return true;
        if (be.pending.stream().anyMatch(s -> !s.isEmpty() && !BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("minecraft"))) {
            be.status = 14; return false;
        }
        Route route;
        if (be.idle()) {
            String key = foundation(be);
            if (key.isEmpty()) return true; // Aggregate validation supplies the structural status.
            route = canonical(key);
            if (route == null) { be.status = 14; return false; }
        } else {
            route = recipeRoute(CatalogueContent.RECIPES.get(be.activeRecipe));
            if (route == null) { be.status = 14; return false; }
            int stacks = 0;
            for (var stack : be.pending) if (!stack.isEmpty()) {
                if (!vanilla(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), route.output()) || stack.getCount() != route.count()) {
                    be.status = 14; return false;
                }
                stacks++;
            }
            if (stacks != 1 || !be.pendingFluid1.isEmpty() || !be.pendingFluid2.isEmpty()) { be.status = 14; return false; }
        }
        if (!permitted(be, route)) { be.status = 13; return false; }
        return true;
    }
}
