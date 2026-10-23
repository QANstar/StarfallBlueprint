package cn.starfall.blueprint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Explicit vanilla cultivation recipes. Environment and sample are permanent catalysts. */
public final class PlantCultivation {
    /** Both construction and access copy mutable stacks; callers cannot alter registered yields. */
    public record Profile(String id, Set<Item> environments, List<ItemStack> outputs) {
        public Profile {
            Objects.requireNonNull(id);
            environments = Set.copyOf(environments);
            outputs = outputs.stream().map(ItemStack::copy).toList();
        }

        @Override
        public List<ItemStack> outputs() {
            return outputs.stream().map(ItemStack::copy).toList();
        }
    }

    private static final Set<Item> SOIL = Set.of(Items.DIRT, Items.GRASS_BLOCK, Items.FARMLAND,
            Items.PODZOL, Items.COARSE_DIRT, Items.ROOTED_DIRT, Items.MOSS_BLOCK);
    private static final Set<Item> SAND = Set.of(Items.SAND, Items.RED_SAND);
    private static final Set<Item> CANE_GROUND = union(SOIL, SAND);
    private static final Set<Item> AQUATIC = Set.of(Items.SAND, Items.GRAVEL, Items.CLAY, Items.DIRT,
            Items.TUBE_CORAL_BLOCK, Items.BRAIN_CORAL_BLOCK, Items.BUBBLE_CORAL_BLOCK,
            Items.FIRE_CORAL_BLOCK, Items.HORN_CORAL_BLOCK,
            Items.DEAD_TUBE_CORAL_BLOCK, Items.DEAD_BRAIN_CORAL_BLOCK, Items.DEAD_BUBBLE_CORAL_BLOCK,
            Items.DEAD_FIRE_CORAL_BLOCK, Items.DEAD_HORN_CORAL_BLOCK);
    private static final Set<Item> MUSHROOM_GROUND = Set.of(Items.MYCELIUM, Items.PODZOL,
            Items.MOSS_BLOCK, Items.DIRT);
    private static final Set<Item> WET_GROUND = Set.of(Items.CLAY, Items.MOSS_BLOCK, Items.DIRT);
    private static final Map<Item, Profile> PROFILES = createProfiles();
    private static final Set<Item> SAMPLES = Collections.unmodifiableSet(new LinkedHashSet<>(PROFILES.keySet()));
    private static final Set<Item> ENVIRONMENTS = collectEnvironments();

    private PlantCultivation() {}

    /** One stack always represents one sample, irrespective of stack count or components. */
    public static Optional<Profile> profile(ItemStack sample) {
        return sample.isEmpty() ? Optional.empty() : Optional.ofNullable(PROFILES.get(sample.getItem()));
    }

    public static boolean isEnvironment(ItemStack stack) {
        return !stack.isEmpty() && ENVIRONMENTS.contains(stack.getItem());
    }

    public static boolean isSample(ItemStack stack) {
        return !stack.isEmpty() && SAMPLES.contains(stack.getItem());
    }

    /** Immutable, explicit supported sample registry, suitable for menus and documentation. */
    public static Set<Item> samples() {
        return SAMPLES;
    }

    /** Reserve a batch once; the common processing loop handles power payment and settlement. */
    public static boolean start(IndustryBlockEntity be) {
        if (!be.idle()) return false;
        ItemStack environment = be.stock.get(0);
        Optional<Profile> candidate = profile(be.stock.get(1));
        if (environment.isEmpty() || candidate.isEmpty()
                || !candidate.get().environments().contains(environment.getItem())) {
            be.status = 2;
            return false;
        }
        if (!be.tanks[0].getFluid().is(Fluids.WATER) || be.tanks[0].getFluidAmount() < 250) {
            be.status = 3;
            return false;
        }
        if (be.getPower() < 20) {
            be.status = 4;
            return false;
        }
        Profile selected = candidate.get();
        List<ItemStack> outputs = selected.outputs();
        if (!be.outputFits(outputs)) {
            be.status = 5;
            return false;
        }

        be.tanks[0].drain(250, FluidAction.EXECUTE);
        be.activeRecipe = "cultivate:" + selected.id();
        be.pending = NonNullList.withSize(4, ItemStack.EMPTY);
        for (int i = 0; i < outputs.size(); i++) be.pending.set(i, outputs.get(i).copy());
        be.pendingFluid1 = FluidStack.EMPTY;
        be.pendingFluid2 = FluidStack.EMPTY;
        be.pendingExperience = 0;
        be.processTicks = 400;
        be.processCost = 8000;
        be.progressTicks = 0;
        be.processPaid = 0;
        be.status = 1;
        be.setChanged();
        return true;
    }

    private static Map<Item, Profile> createProfiles() {
        Map<Item, Profile> profiles = new LinkedHashMap<>();
        add(profiles, "wheat", SOIL, Items.WHEAT_SEEDS, Items.WHEAT, 2);
        add(profiles, "beetroot", SOIL, Items.BEETROOT_SEEDS, Items.BEETROOT, 2);
        add(profiles, "carrot", SOIL, Items.CARROT, Items.CARROT, 2);
        add(profiles, "potato", SOIL, Items.POTATO, Items.POTATO, 2);
        add(profiles, "pumpkin", SOIL, Items.PUMPKIN_SEEDS, Items.PUMPKIN, 1);
        add(profiles, "melon", SOIL, Items.MELON_SEEDS, Items.MELON_SLICE, 4);
        add(profiles, "torchflower", SOIL, Items.TORCHFLOWER_SEEDS, Items.TORCHFLOWER, 2);
        add(profiles, "pitcher_plant", SOIL, Items.PITCHER_POD, Items.PITCHER_PLANT, 2);
        add(profiles, "cocoa", Set.of(Items.JUNGLE_LOG, Items.JUNGLE_WOOD), Items.COCOA_BEANS, Items.COCOA_BEANS, 2);
        add(profiles, "sweet_berries", SOIL, Items.SWEET_BERRIES, Items.SWEET_BERRIES, 2);
        add(profiles, "glow_berries", SOIL, Items.GLOW_BERRIES, Items.GLOW_BERRIES, 2);

        add(profiles, "oak", SOIL, Items.OAK_SAPLING, Items.OAK_LOG, 2);
        add(profiles, "spruce", SOIL, Items.SPRUCE_SAPLING, Items.SPRUCE_LOG, 2);
        add(profiles, "birch", SOIL, Items.BIRCH_SAPLING, Items.BIRCH_LOG, 2);
        add(profiles, "jungle", SOIL, Items.JUNGLE_SAPLING, Items.JUNGLE_LOG, 2);
        add(profiles, "acacia", SOIL, Items.ACACIA_SAPLING, Items.ACACIA_LOG, 2);
        add(profiles, "dark_oak", SOIL, Items.DARK_OAK_SAPLING, Items.DARK_OAK_LOG, 2);
        add(profiles, "mangrove", SOIL, Items.MANGROVE_PROPAGULE, Items.MANGROVE_LOG, 2);
        add(profiles, "cherry", SOIL, Items.CHERRY_SAPLING, Items.CHERRY_LOG, 2);
        add(profiles, "azalea", SOIL, Items.AZALEA, Items.OAK_LOG, 2);
        add(profiles, "flowering_azalea", SOIL, Items.FLOWERING_AZALEA, Items.OAK_LOG, 2);
        self(profiles, "bamboo", SOIL, Items.BAMBOO, 4);
        self(profiles, "cactus", SAND, Items.CACTUS, 2);
        self(profiles, "sugar_cane", CANE_GROUND, Items.SUGAR_CANE, 2);

        self(profiles, "dandelion", SOIL, Items.DANDELION, 2);
        self(profiles, "poppy", SOIL, Items.POPPY, 2);
        self(profiles, "blue_orchid", SOIL, Items.BLUE_ORCHID, 2);
        self(profiles, "allium", SOIL, Items.ALLIUM, 2);
        self(profiles, "azure_bluet", SOIL, Items.AZURE_BLUET, 2);
        self(profiles, "red_tulip", SOIL, Items.RED_TULIP, 2);
        self(profiles, "orange_tulip", SOIL, Items.ORANGE_TULIP, 2);
        self(profiles, "white_tulip", SOIL, Items.WHITE_TULIP, 2);
        self(profiles, "pink_tulip", SOIL, Items.PINK_TULIP, 2);
        self(profiles, "oxeye_daisy", SOIL, Items.OXEYE_DAISY, 2);
        self(profiles, "cornflower", SOIL, Items.CORNFLOWER, 2);
        self(profiles, "lily_of_the_valley", SOIL, Items.LILY_OF_THE_VALLEY, 2);
        self(profiles, "wither_rose", SOIL, Items.WITHER_ROSE, 2);
        self(profiles, "torchflower", SOIL, Items.TORCHFLOWER, 2);
        self(profiles, "sunflower", SOIL, Items.SUNFLOWER, 2);
        self(profiles, "lilac", SOIL, Items.LILAC, 2);
        self(profiles, "rose_bush", SOIL, Items.ROSE_BUSH, 2);
        self(profiles, "peony", SOIL, Items.PEONY, 2);
        self(profiles, "pitcher_plant", SOIL, Items.PITCHER_PLANT, 2);
        self(profiles, "pink_petals", SOIL, Items.PINK_PETALS, 2);
        self(profiles, "short_grass", SOIL, Items.SHORT_GRASS, 2);
        self(profiles, "fern", SOIL, Items.FERN, 2);
        self(profiles, "dead_bush", CANE_GROUND, Items.DEAD_BUSH, 2);
        self(profiles, "tall_grass", SOIL, Items.TALL_GRASS, 2);
        self(profiles, "large_fern", SOIL, Items.LARGE_FERN, 2);

        self(profiles, "kelp", AQUATIC, Items.KELP, 2);
        self(profiles, "sea_pickle", AQUATIC, Items.SEA_PICKLE, 2);
        self(profiles, "seagrass", AQUATIC, Items.SEAGRASS, 2);
        self(profiles, "brown_mushroom", MUSHROOM_GROUND, Items.BROWN_MUSHROOM, 2);
        self(profiles, "red_mushroom", MUSHROOM_GROUND, Items.RED_MUSHROOM, 2);
        self(profiles, "nether_wart", Set.of(Items.SOUL_SAND), Items.NETHER_WART, 2);
        add(profiles, "crimson_fungus", Set.of(Items.CRIMSON_NYLIUM), Items.CRIMSON_FUNGUS,
                List.of(new ItemStack(Items.CRIMSON_FUNGUS, 2), new ItemStack(Items.CRIMSON_STEM, 2)));
        add(profiles, "warped_fungus", Set.of(Items.WARPED_NYLIUM), Items.WARPED_FUNGUS,
                List.of(new ItemStack(Items.WARPED_FUNGUS, 2), new ItemStack(Items.WARPED_STEM, 2)));

        self(profiles, "hanging_roots", Set.of(Items.ROOTED_DIRT), Items.HANGING_ROOTS, 2);
        self(profiles, "moss", SOIL, Items.MOSS_BLOCK, 2);
        self(profiles, "moss_carpet", SOIL, Items.MOSS_CARPET, 2);
        self(profiles, "vine", SOIL, Items.VINE, 2);
        self(profiles, "weeping_vines", Set.of(Items.CRIMSON_NYLIUM, Items.NETHER_WART_BLOCK), Items.WEEPING_VINES, 2);
        self(profiles, "twisting_vines", Set.of(Items.WARPED_NYLIUM, Items.WARPED_WART_BLOCK), Items.TWISTING_VINES, 2);
        self(profiles, "crimson_roots", Set.of(Items.CRIMSON_NYLIUM), Items.CRIMSON_ROOTS, 2);
        self(profiles, "warped_roots", Set.of(Items.WARPED_NYLIUM), Items.WARPED_ROOTS, 2);
        self(profiles, "nether_sprouts", Set.of(Items.WARPED_NYLIUM), Items.NETHER_SPROUTS, 2);
        self(profiles, "glow_lichen", Set.of(Items.STONE, Items.COBBLESTONE, Items.MOSS_BLOCK), Items.GLOW_LICHEN, 2);
        self(profiles, "big_dripleaf", WET_GROUND, Items.BIG_DRIPLEAF, 2);
        self(profiles, "small_dripleaf", WET_GROUND, Items.SMALL_DRIPLEAF, 2);
        self(profiles, "lily_pad", WET_GROUND, Items.LILY_PAD, 2);
        self(profiles, "spore_blossom", Set.of(Items.MOSS_BLOCK), Items.SPORE_BLOSSOM, 2);

        add(profiles, "chorus_flower", Set.of(Items.END_STONE), Items.CHORUS_FLOWER,
                List.of(new ItemStack(Items.CHORUS_FRUIT, 2), new ItemStack(Items.CHORUS_FLOWER)));
        add(profiles, "chorus_plant", Set.of(Items.END_STONE), Items.CHORUS_PLANT, Items.CHORUS_FRUIT, 2);
        self(profiles, "tube_coral", AQUATIC, Items.TUBE_CORAL, 2);
        self(profiles, "brain_coral", AQUATIC, Items.BRAIN_CORAL, 2);
        self(profiles, "bubble_coral", AQUATIC, Items.BUBBLE_CORAL, 2);
        self(profiles, "fire_coral", AQUATIC, Items.FIRE_CORAL, 2);
        self(profiles, "horn_coral", AQUATIC, Items.HORN_CORAL, 2);
        self(profiles, "tube_coral_fan", AQUATIC, Items.TUBE_CORAL_FAN, 2);
        self(profiles, "brain_coral_fan", AQUATIC, Items.BRAIN_CORAL_FAN, 2);
        self(profiles, "bubble_coral_fan", AQUATIC, Items.BUBBLE_CORAL_FAN, 2);
        self(profiles, "fire_coral_fan", AQUATIC, Items.FIRE_CORAL_FAN, 2);
        self(profiles, "horn_coral_fan", AQUATIC, Items.HORN_CORAL_FAN, 2);
        self(profiles, "tube_coral_block", AQUATIC, Items.TUBE_CORAL_BLOCK, 2);
        self(profiles, "brain_coral_block", AQUATIC, Items.BRAIN_CORAL_BLOCK, 2);
        self(profiles, "bubble_coral_block", AQUATIC, Items.BUBBLE_CORAL_BLOCK, 2);
        self(profiles, "fire_coral_block", AQUATIC, Items.FIRE_CORAL_BLOCK, 2);
        self(profiles, "horn_coral_block", AQUATIC, Items.HORN_CORAL_BLOCK, 2);
        return Collections.unmodifiableMap(profiles);
    }

    private static void self(Map<Item, Profile> profiles, String id, Set<Item> environments, Item sample, int count) {
        add(profiles, id, environments, sample, sample, count);
    }

    private static void add(Map<Item, Profile> profiles, String id, Set<Item> environments,
                            Item sample, Item output, int count) {
        add(profiles, id, environments, sample, List.of(new ItemStack(output, count)));
    }

    private static void add(Map<Item, Profile> profiles, String id, Set<Item> environments,
                            Item sample, List<ItemStack> outputs) {
        Profile previous = profiles.put(sample, new Profile(id, environments, outputs));
        if (previous != null) throw new IllegalStateException("Duplicate cultivation sample: " + id);
    }

    private static Set<Item> union(Set<Item> first, Set<Item> second) {
        Set<Item> combined = new LinkedHashSet<>(first);
        combined.addAll(second);
        return Collections.unmodifiableSet(combined);
    }

    private static Set<Item> collectEnvironments() {
        Set<Item> environments = new LinkedHashSet<>();
        for (Profile profile : PROFILES.values()) environments.addAll(profile.environments());
        return Collections.unmodifiableSet(environments);
    }
}
