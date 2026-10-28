package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Vanilla quarry harvesting, with one persisted roll and payment budget per reserved batch. */
public final class MiningUpgrades {
    private MiningUpgrades() {}
    public static final long SMELTING_ENERGY_PER_ITEM = 100;
    private static final String SNAPSHOT = "MiningSnapshot";
    private static final Set<String> STONE_SOURCES = Set.of("stone", "deepslate", "granite", "diorite", "andesite", "tuff", "calcite", "blackstone", "basalt", "end_stone", "netherrack", "obsidian");
    // Rechecking a saved seed uses the actual loot table once, not once per processing tick.
    private static final Map<IndustryBlockEntity, Validation> VALIDATED = new WeakHashMap<>();
    private record Validation(CompoundTag snapshot, List<ItemStack> outputs, boolean valid) {}
    public record Plan(List<ItemStack> outputs, long extraCost, CompoundTag snapshot) {}

    public static int fortune(IndustryBlockEntity be) {
        for (int level = 3; level > 0; level--) if (MachineUpgrades.count(be, "fortune_" + level) > 0) return level;
        return 0;
    }
    private static boolean silk(IndustryBlockEntity be) { return MachineUpgrades.count(be, "silk_touch") > 0; }
    private static boolean smelting(IndustryBlockEntity be) { return MachineUpgrades.count(be, "auto_smelt") > 0; }
    /** Installation check; runtime also rejects incompatible inventories loaded from external NBT. */
    public static boolean conflicts(IndustryBlockEntity be, ItemStack candidate) {
        String key = CatalogueContent.key(candidate);
        return key.equals("silk_touch") && (fortune(be) > 0 || smelting(be))
                || (key.equals("auto_smelt") || key.matches("fortune_[123]")) && silk(be);
    }
    public static boolean conflicts(IndustryBlockEntity be) { return silk(be) && (fortune(be) > 0 || smelting(be)); }

    /** Caller must first validate the source against ResonanceBases' independent canonical routes. */
    public static Optional<Plan> plan(IndustryBlockEntity be, String source, String output, int count) {
        if (conflicts(be) || !(be.getLevel() instanceof ServerLevel level)) return Optional.empty();
        int fortune = fortune(be); boolean silk = silk(be), smelt = smelting(be);
        if (fortune == 0 && !silk && !smelt)
            return Optional.of(new Plan(List.of(new ItemStack(vanillaItem(output), count)), 0, new CompoundTag()));
        long seed = level.random.nextLong();
        var result = harvest(be, source, output, count, fortune, silk, smelt, seed);
        if (result.isEmpty()) return Optional.empty();
        CompoundTag snapshot = new CompoundTag();
        snapshot.putInt("Version", 1); snapshot.putString("Source", source); snapshot.putInt("Fortune", fortune);
        snapshot.putBoolean("Silk", silk); snapshot.putBoolean("Smelt", smelt); snapshot.putLong("Seed", seed);
        return Optional.of(new Plan(result.get().outputs(), result.get().extraCost(), snapshot));
    }

    private static Item vanillaItem(String path) { return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("minecraft", path)); }
    private static Optional<Plan> harvest(IndustryBlockEntity be, String source, String output, int count,
            int fortune, boolean silk, boolean smelt, long seed) {
        if (!(be.getLevel() instanceof ServerLevel level) || fortune < 0 || fortune > 3 || silk && (fortune > 0 || smelt)) return Optional.empty();
        List<ItemStack> drops = new ArrayList<>();
        if (fortune == 0 && !silk) drops.add(new ItemStack(vanillaItem(output), count));
        else {
            var state = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecraft", source)).defaultBlockState();
            ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
            var enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            if (silk) tool.enchant(enchantments.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
            else tool.enchant(enchantments.getHolderOrThrow(Enchantments.FORTUNE), fortune);
            var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(be.getBlockPos()))
                    .withParameter(LootContextParams.BLOCK_STATE, state).withParameter(LootContextParams.TOOL, tool).create(LootContextParamSets.BLOCK);
            var table = level.getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable());
            var random = RandomSource.create(seed);
            // Stone foundations produce four blocks per cycle; mineral foundations represent one harvest.
            int harvests = STONE_SOURCES.contains(source) ? count : 1;
            for (int i = 0; i < harvests; i++) drops.addAll(table.getRandomItems(params, random));
        }
        if (drops.isEmpty() || drops.stream().anyMatch(s -> s.isEmpty() || !BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("minecraft"))) return Optional.empty();
        List<ItemStack> products = new ArrayList<>(); long converted = 0;
        for (ItemStack drop : drops) {
            if (smelt) {
                var input = new SingleRecipeInput(drop.copyWithCount(1));
                var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level);
                if (recipe.isPresent()) {
                    ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
                    if (result.isEmpty() || !BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace().equals("minecraft")) return Optional.empty();
                    long total = (long) result.getCount() * drop.getCount();
                    if (total <= 0 || total > 256) return Optional.empty();
                    products.add(result.copyWithCount((int) total)); converted += drop.getCount();
                    continue;
                }
            }
            products.add(drop.copy());
        }
        var packed = AutomaticCrafting.packOutputs(products);
        long extraCost = converted * SMELTING_ENERGY_PER_ITEM;
        return packed.map(outputs -> new Plan(outputs, extraCost, new CompoundTag()));
    }

    public static void reserve(IndustryBlockEntity be, Plan plan) {
        clear(be);
        if (plan.snapshot().isEmpty()) return;
        be.miningSnapshot = plan.snapshot().copy();
        be.miningSnapshot.putString("Recipe", be.activeRecipe);
    }
    /** Called after MachineUpgrades.reserve has fixed general speed and energy adjustments. */
    public static void lock(IndustryBlockEntity be) {
        if (be.miningSnapshot.isEmpty()) return;
        be.miningSnapshot.putLong("Cost", be.processCost); be.miningSnapshot.putInt("Ticks", be.processTicks);
    }
    public static void clear(IndustryBlockEntity be) { be.miningSnapshot = new CompoundTag(); VALIDATED.remove(be); }
    public static boolean hasSnapshot(IndustryBlockEntity be) { return !be.miningSnapshot.isEmpty(); }

    /** The canonical recipe gate remains mandatory, even for an otherwise valid mining snapshot. */
    public static boolean validSnapshot(IndustryBlockEntity be, String source, String output, int count) {
        CompoundTag snapshot = be.miningSnapshot;
        int fortune = snapshot.getInt("Fortune"); boolean silk = snapshot.getBoolean("Silk"), smelt = snapshot.getBoolean("Smelt");
        if (snapshot.getInt("Version") != 1 || !snapshot.getString("Source").equals(source)
                || !snapshot.getString("Recipe").equals(be.activeRecipe) || !snapshot.contains("Seed", 4)
                || fortune < 0 || fortune > 3 || silk && (fortune > 0 || smelt) || fortune == 0 && !silk && !smelt
                || fortune != fortune(be) || silk != silk(be) || smelt != smelting(be)
                || snapshot.getLong("Cost") != be.processCost || snapshot.getInt("Ticks") != be.processTicks
                || !be.pendingFluid1.isEmpty() || !be.pendingFluid2.isEmpty()) return false;
        var outputs = be.pending.stream().filter(s -> !s.isEmpty()).toList();
        Validation previous = VALIDATED.get(be);
        if (previous != null && previous.snapshot().equals(snapshot) && sameOutputs(previous.outputs(), outputs)) return previous.valid();
        var plan = harvest(be, source, output, count, fortune, silk, smelt, snapshot.getLong("Seed"));
        boolean valid = plan.isPresent() && sameOutputs(plan.get().outputs(), outputs);
        VALIDATED.put(be, new Validation(snapshot.copy(), outputs.stream().map(ItemStack::copy).toList(), valid));
        return valid;
    }
    private static boolean sameOutputs(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        return true;
    }
    public static void save(IndustryBlockEntity be, CompoundTag tag) {
        if (!be.idle() && hasSnapshot(be)) tag.put(SNAPSHOT, be.miningSnapshot.copy());
        else tag.remove(SNAPSHOT);
    }
    public static void load(IndustryBlockEntity be, CompoundTag tag) {
        clear(be);
        if (!be.idle() && tag.contains(SNAPSHOT, 10)) be.miningSnapshot = tag.getCompound(SNAPSHOT).copy();
    }
}
