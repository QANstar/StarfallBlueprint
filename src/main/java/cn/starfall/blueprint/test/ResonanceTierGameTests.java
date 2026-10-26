package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class ResonanceTierGameTests {
    private static final Set<String> BASIC = Set.of("stone", "iron_ore", "deepslate_iron_ore", "copper_ore", "deepslate_copper_ore");
    // Independent acceptance list, not an enumeration of whatever the catalogue happens to contain.
    private static final Set<String> SOURCES = Set.of("stone", "deepslate", "granite", "diorite", "andesite", "tuff", "calcite", "blackstone", "basalt", "end_stone", "netherrack", "obsidian",
            "coal_ore", "deepslate_coal_ore", "iron_ore", "deepslate_iron_ore", "copper_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore",
            "redstone_ore", "deepslate_redstone_ore", "lapis_ore", "deepslate_lapis_ore", "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore",
            "nether_gold_ore", "nether_quartz_ore", "ancient_debris", "amethyst_cluster", "gilded_blackstone", "glowstone");
    private static String key(String source) { return "resonance_base_" + source; }
    private static Block base(String key) { return ((BlockItem) CatalogueContent.item(key)).getBlock(); }
    private static void floor(IndustryBlockEntity be, String key) {
        for (int p = 0; p < 4; p++) be.getLevel().setBlock(be.getBlockPos().offset(LargeMachineBlock.offset(Direction.NORTH, p)).below(), base(key).defaultBlockState(), 3);
    }
    private static IndustryBlockEntity machine(GameTestHelper h, int i, String source) {
        var root = h.absolutePos(new BlockPos(1200 + i * 8, 70, 1200));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) h.getLevel().getChunk((root.getX() >> 4) + x, (root.getZ() >> 4) + z);
        var state = CatalogueContent.BLOCKS.get("quarry").get().defaultBlockState().setValue(IndustryBlock.FACING, Direction.NORTH);
        for (int p = 0; p < 4; p++) {
            var pos = root.offset(LargeMachineBlock.offset(Direction.NORTH, p));
            h.getLevel().setBlock(pos.below(), base(key(source)).defaultBlockState(), 3);
            h.getLevel().setBlock(pos, state.setValue(LargeMachineBlock.PART, p), 3);
            if (p < 2) h.getLevel().removeBlock(pos.north(), false);
        }
        var be = (IndustryBlockEntity) h.getLevel().getBlockEntity(root);
        be.setPower(8000);
        return be;
    }
    private static JsonObject recipe(String source) {
        return CatalogueContent.RECIPES.values().stream().filter(r -> r.has("resonance_base") && r.get("resonance_base").getAsString().equals(key(source))).findFirst().orElseThrow();
    }
    private static void tick(IndustryBlockEntity be, int n) { for (int i = 0; i < n; i++) IndustryProcessing.tick(be); }
    private static void reload(GameTestHelper h, IndustryBlockEntity be) { be.loadWithComponents(be.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess()); }
    private static int outputs(IndustryBlockEntity be) { return be.stock.subList(9, 13).stream().mapToInt(ItemStack::getCount).sum(); }

    @GameTest(template = "empty")
    public static void independentCompleteVanillaCatalogueAndFiveBasicTwentyNineAdvanced(GameTestHelper h) {
        Set<String> actual = new HashSet<>();
        CatalogueContent.DEFINITIONS.forEach((k, d) -> { if (d.has("resonance_source")) actual.add(d.get("resonance_source").getAsString()); });
        h.assertTrue(actual.equals(SOURCES.stream().map(s -> "minecraft:" + s).collect(java.util.stream.Collectors.toSet())), "Exact complete 34 vanilla source acceptance list");
        int index = 0, basics = 0, advanced = 0;
        for (String source : SOURCES) {
            var be = machine(h, index++, source);
            var route = recipe(source);
            var definition = CatalogueContent.DEFINITIONS.get(key(source));
            boolean basic = BASIC.contains(source);
            if (basic) basics++; else advanced++;
            h.assertTrue(definition.get("resonance_tier").getAsInt() == (basic ? 0 : 1), "Canonical tier: " + source);
            h.assertTrue(Set.of("stone", "mineral").contains(definition.get("resonance_category").getAsString()), "Canonical category: " + source);
            h.assertTrue(be.stock.get(18).isEmpty() && ResonanceBases.matches(be, route) == basic, "No upgrade opens exactly five sources: " + source);
            tick(be, 1);
            h.assertTrue(basic ? be.progressTicks == 1 : be.idle() && be.status == 13 && be.getPower() == 8000, "Basic production or advanced pause: " + source);
            if (!basic) {
                be.stock.set(18, CatalogueContent.stack("speed_2", 1));
                h.assertTrue(!ResonanceBases.canRun(be), "Unrelated upgrade is rejected: " + source);
                var savedTier = definition.get("resonance_tier").deepCopy();
                try {
                    definition.addProperty("resonance_tier", 0);
                    h.assertTrue(!ResonanceBases.matches(be, route) && !ResonanceBases.canRun(be), "Misconfigured tier zero cannot bypass exact source gate: " + source);
                } finally { definition.add("resonance_tier", savedTier); }
                be.stock.set(18, CatalogueContent.stack("resonance_upgrade", 1));
                tick(be, 200);
                h.assertTrue(be.idle() && outputs(be) > 0 && be.getPower() == 4000, "All advanced sources complete with retained exact upgrade: " + source);
                h.assertTrue(be.stock.get(18).is(CatalogueContent.item("resonance_upgrade")), "Upgrade retained: " + source);
            }
        }
        h.assertTrue(basics == 5 && advanced == 29, "Exactly five basic and 29 advanced sources");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realMenuShiftMoveComponentsAndInFlightUpgradeLockSurviveNbt(GameTestHelper h) {
        var be = machine(h, 40, "diamond_ore");
        var player = FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ResonanceTier"));
        player.getInventory().clearContent();
        var menu = new IndustryMenu(1, player.getInventory(), be, be.processData);
        var upgrade = CatalogueContent.stack("resonance_upgrade", 1);
        upgrade.set(DataComponents.CUSTOM_NAME, Component.literal("retained resonance component"));
        var expected = upgrade.copy();
        int bay = IndustryMenu.UPGRADE_MENU_START;
        try {
            player.getInventory().setItem(9, upgrade);
            h.assertTrue(menu.getSlot(bay).isActive() && menu.getSlot(bay).getMaxStackSize(expected) == 1, "Visible dynamic upgrade slot retains the dedicated module stack limit");
            menu.clicked(27, 0, ClickType.QUICK_MOVE, player);
            h.assertTrue(ItemStack.matches(expected, be.upgrades.getItem(0)) && be.stock.get(18).isEmpty(), "Shift insertion uses the first free bay and preserves components");
            h.assertTrue(!menu.getSlot(bay).mayPlace(new ItemStack(Items.DIAMOND)), "Upgrade slot rejects ordinary items");
            for (var direction : Direction.values()) h.assertTrue(Arrays.stream(new IndustryInventory(be).getSlotsForFace(direction)).noneMatch(i -> i == 18), "Automation never exposes upgrade slot");
            tick(be, 73);
            var locked = be.pending.get(0).copy();
            h.assertTrue(!menu.getSlot(bay).mayPickup(player) && !menu.getSlot(bay).mayPlace(expected), "Running menu locks removal and insertion");
            menu.clicked(bay, 0, ClickType.QUICK_MOVE, player);
            h.assertTrue(ItemStack.matches(expected, be.upgrades.getItem(0)), "Running shift extraction cannot take upgrade");
            reload(h, be);
            h.assertTrue(ItemStack.matches(expected, be.upgrades.getItem(0)), "NBT preserves upgrade components");
            be.upgrades.setItem(0, ItemStack.EMPTY); // Simulate corrupt external removal; normal UI is locked.
            floor(be, key("stone"));
            tick(be, 20);
            h.assertTrue(be.status == 13 && be.progressTicks == 73 && be.getPower() == 6540 && ItemStack.matches(locked, be.pending.get(0)), "Advanced pending still requires upgrade after foundation swap");
            reload(h, be);
            be.upgrades.setItem(0, expected.copy());
            tick(be, 127);
            h.assertTrue(be.idle() && be.stock.get(9).is(Items.DIAMOND) && outputs(be) == 1 && be.getPower() == 4000, "Reinstall resumes original advanced batch once");
            h.assertTrue(ItemStack.matches(expected, be.upgrades.getItem(0)), "Settlement retains exact upgrade components");
            menu.clicked(bay, 0, ClickType.QUICK_MOVE, player);
            h.assertTrue(be.upgrades.isEmpty(), "Idle shift extraction is permitted");
            tick(be, 200);
            h.assertTrue(outputs(be) == 5 && be.getPower() == 0, "Next batch follows stone foundation without upgrade");
        } finally { menu.removed(player); player.getInventory().clearContent(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void retiredFoundationsMissingRecipesAndModPendingAlwaysFreeze(GameTestHelper h) {
        var be = machine(h, 41, "stone");
        be.stock.set(18, CatalogueContent.stack("resonance_upgrade", 1));
        for (String retired : CatalogueContent.RETIRED_BASES) {
            h.assertTrue(!CatalogueContent.DEFINITIONS.containsKey(retired), "Retired base has no active definition");
            floor(be, retired);
            tick(be, 20);
            h.assertTrue(be.idle() && be.status == 14 && be.getPower() == 8000 && outputs(be) == 0, "Registered legacy foundation cannot work even upgraded");
        }
        floor(be, key("stone"));
        for (int number = 32; number <= 35; number++) {
            be.activeRecipe = "QM-R" + number;
            be.processTicks = 200; be.progressTicks = 73; be.processCost = 4000; be.processPaid = 1460;
            be.pending.set(0, new ItemStack(Items.COBBLESTONE, 4));
            reload(h, be);
            tick(be, 200);
            h.assertTrue(be.status == 14 && be.progressTicks == 73 && be.getPower() == 8000 && outputs(be) == 0, "Removed mod recipe cannot launder vanilla pending on stone: " + number);
        }
        be.activeRecipe = "QM-R01";
        be.pending.set(0, new ItemStack(StarfallBlueprint.RAW.get()));
        reload(h, be);
        tick(be, 200);
        h.assertTrue(be.status == 14 && be.progressTicks == 73 && be.pending.get(0).is(StarfallBlueprint.RAW.get()) && outputs(be) == 0, "Any mod pending fails closed under an otherwise valid vanilla recipe");
        var definition = CatalogueContent.DEFINITIONS.get(key("stone"));
        for (String field : List.of("resonance_source", "resonance_output")) {
            var saved = definition.get(field).deepCopy();
            try {
                definition.addProperty(field, field.equals("resonance_source") ? "starfall_blueprint:ore_meteor_phase" : "starfall_blueprint:raw_meteor_phase");
                h.assertTrue(!ResonanceBases.matches(be, recipe("stone")) && ResonanceBases.output(be, recipe("stone")).isEmpty(), "Either mod namespace is rejected: " + field);
            } finally { definition.add(field, saved); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void amethystGildedBlackstoneGlowstoneHaveFixedVanillaOutputs(GameTestHelper h) {
        String[] sources = {"amethyst_cluster", "gilded_blackstone", "glowstone"};
        Item[] products = {Items.AMETHYST_SHARD, Items.GOLD_NUGGET, Items.GLOWSTONE_DUST};
        int[] counts = {4, 1, 4};
        for (int i = 0; i < sources.length; i++) {
            var be = machine(h, 42 + i, sources[i]);
            h.assertTrue(recipe(sources[i]).get("id").getAsString().equals("QM-R" + (36 + i)), "New routes preserve frozen IDs");
            tick(be, 200);
            h.assertTrue(be.status == 13 && outputs(be) == 0 && be.getPower() == 8000, "New mineral requires upgrade");
            be.stock.set(18, CatalogueContent.stack("resonance_upgrade", 1));
            tick(be, 200);
            h.assertTrue(be.stock.get(9).is(products[i]) && outputs(be) == counts[i] && be.getPower() == 4000, "Exact canonical new-source output");
        }
        h.succeed();
    }
}
