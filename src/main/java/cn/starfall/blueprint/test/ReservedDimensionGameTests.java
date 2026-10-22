package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

/** Future official dimensions retain save IDs, but expose no acquisition or processing route. */
@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class ReservedDimensionGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static final Set<String> INDUSTRIAL = Set.of("AUX-R03", "PM-07", "PM-11", "PM-12");

    private static IndustryBlockEntity machine(GameTestHelper h, String kind) {
        h.setBlock(POS, Blocks.AIR);
        h.setBlock(POS, kind.equals("prep") ? StarfallBlueprint.PREPARATION.get() : CatalogueContent.BLOCKS.get(kind).get());
        var be = (IndustryBlockEntity) h.getBlockEntity(POS);
        if (be instanceof PreparationBlockEntity prep) prep.materialMode = true;
        return be;
    }

    private static ItemStack material(String key, int count) {
        var stack = CatalogueContent.stack(key, count);
        stack.set(CatalogueContent.QUALITY.get(), new MaterialQuality(99, 100, 80, 0, true, key.startsWith("anima") ? "anima" : "common"));
        if (key.equals("common")) stack.set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(100));
        return stack;
    }

    private static CraftingInput craftingInput(JsonObject definition) {
        var grid = new ArrayList<ItemStack>(Collections.nCopies(9, ItemStack.EMPTY));
        var craft = definition.getAsJsonObject("craft");
        if (craft != null && craft.get("mode").getAsString().startsWith("定形")) {
            var cells = craft.getAsJsonArray("cells");
            for (int i = 0; i < 9; i++) if (!cells.get(i).isJsonNull()) grid.set(i, material(CatalogueContent.key(cells.get(i)), 1));
        } else {
            int slot = 0;
            for (var in : definition.getAsJsonArray("inputs")) for (int n = 0; n < CatalogueContent.qty(in); n++) grid.set(slot++, material(CatalogueContent.key(in), 1));
        }
        return CraftingInput.of(3, 3, grid);
    }

    @GameTest(template = "empty")
    public static void lunarHasNoWorldgenAndLegacyOreNeverDrops(GameTestHelper h) {
        var moon = ResourceLocation.fromNamespaceAndPath(StarfallBlueprint.ID, "moon");
        h.assertTrue(!h.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).containsKey(moon), "No lunar configured feature may load");
        h.assertTrue(!h.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).containsKey(moon), "No lunar placed feature may load");
        for (String path : List.of("worldgen/configured_feature/moon.json", "worldgen/placed_feature/moon.json", "neoforge/biome_modifier/moon.json")) {
            h.assertTrue(h.getLevel().getServer().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(StarfallBlueprint.ID, path)).isEmpty(), "No lunar worldgen resource: " + path);
        }
        var enchantments = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var tools = new ArrayList<ItemStack>();
        tools.add(ItemStack.EMPTY);
        tools.add(new ItemStack(Items.STONE_PICKAXE));
        tools.add(new ItemStack(Items.IRON_PICKAXE));
        for (int tier : new int[]{1, 2, 3, 10}) {
            var tool = new ItemStack(Items.NETHERITE_PICKAXE);
            tool.enchant(enchantments.getHolderOrThrow(Enchantments.FORTUNE), tier);
            tools.add(tool);
        }
        var silk = new ItemStack(Items.NETHERITE_PICKAXE);
        silk.enchant(enchantments.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        tools.add(silk);
        var state = CatalogueContent.ORES.get("moon").get().defaultBlockState();
        for (var tool : tools) for (int sample = 0; sample < 16; sample++) {
            h.assertTrue(Block.getDrops(state, h.getLevel(), h.absolutePos(POS.offset(sample, 0, 0)), null, null, tool).isEmpty(), "Legacy lunar ore remains unobtainable with every tool and enchantment");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reservedWorkbenchRecipesNeverLoadOrAssembleDirectly(GameTestHelper h) {
        var verified = new HashSet<String>();
        for (var definition : CatalogueContent.RECIPES.values()) {
            if (!(definition.get("station").getAsString().equals("workbench") && definition.has("craft") || definition.get("id").getAsString().equals("PM-12")) || CatalogueContent.isRecipeEnabled(definition)) continue;
            String id = definition.get("id").getAsString();
            var location = ResourceLocation.fromNamespaceAndPath(StarfallBlueprint.ID, "catalogue_" + id.toLowerCase(Locale.ROOT));
            h.assertTrue(h.getLevel().getRecipeManager().byKey(location).isEmpty(), "Reserved recipe must not load: " + id);
            h.assertTrue(h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream().noneMatch(holder -> holder.value() instanceof CatalogueCraftingRecipe r && r.id.equals(id)), "No alternate loaded ID may expose reserved recipe: " + id);
            var input = craftingInput(definition);
            var before = new ArrayList<ItemStack>();
            for (int i = 0; i < input.size(); i++) before.add(input.getItem(i).copy());
            var recipe = new CatalogueCraftingRecipe(id);
            h.assertTrue(!recipe.matches(input, h.getLevel()) && recipe.assemble(input, h.getLevel().registryAccess()).isEmpty(), "Direct recipe calls reject fully supplied legacy materials: " + id);
            for (int i = 0; i < input.size(); i++) h.assertTrue(ItemStack.matches(before.get(i), input.getItem(i)), "Rejected direct assembly preserves input: " + id);
            verified.add(id);
        }
        h.assertTrue(h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream().noneMatch(holder -> CatalogueContent.RESERVED_ITEMS.contains(CatalogueContent.key(holder.value().getResultItem(h.getLevel().registryAccess())))), "No crafting serializer or resource name may expose a reserved output");
        h.assertTrue(verified.equals(Set.of("ST-15", "PM-12", "RT-08")), "Cover all three reserved workbench resources: " + verified);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reservedIndustrialBatchesCannotReserveInputsOrEnergy(GameTestHelper h) {
        var verified = new HashSet<String>();
        for (String id : INDUSTRIAL) {
            var definition = CatalogueContent.recipe(id);
            h.assertTrue(!CatalogueContent.isRecipeEnabled(definition), "Industrial recipe is reserved: " + id);
            var be = machine(h, definition.get("station").getAsString());
            be.template = id;
            be.setPower(320000);
            be.stock.set(13, new ItemStack(Items.COAL, 2));
            int slot = 0;
            for (var in : definition.getAsJsonArray("inputs")) {
                String key = CatalogueContent.key(in);
                if (key.equals("water")) be.tanks[0].fill(new FluidStack(Fluids.WATER, CatalogueContent.qty(in)), FluidAction.EXECUTE);
                else be.stock.set(slot++, material(key, CatalogueContent.qty(in)));
            }
            int toolSlot = 14;
            for (var tool : definition.getAsJsonArray("tools")) be.stock.set(toolSlot++, material(tool.getAsString(), 1));
            h.assertTrue(IndustryProcessing.available(be).stream().noneMatch(r -> r.get("id").getAsString().equals(id)), "Reserved recipe hidden from available list: " + id);
            var before = be.saveWithoutMetadata(h.getLevel().registryAccess()).copy();
            for (int tick = 0; tick < 1200; tick++) IndustryProcessing.tick(be);
            h.assertTrue(be.idle() && before.equals(be.saveWithoutMetadata(h.getLevel().registryAccess())), "Rejected batch preserves inventory, fuel, fluids, tools, energy and pending outputs: " + id);
            verified.add(id);
        }
        h.assertTrue(verified.equals(INDUSTRIAL), "Cover every reserved industrial recipe");
        h.succeed();
    }

    private static void assertFrozenAcrossReload(GameTestHelper h, String recipe, String output, String kind) {
        var be = machine(h, kind);
        be.activeRecipe = recipe;
        be.template = recipe;
        be.processTicks = 20;
        be.progressTicks = 19;
        be.processCost = 10000;
        be.processPaid = 9500;
        be.setPower(200000);
        be.stock.set(0, new ItemStack(Items.IRON_INGOT, 7));
        be.stock.set(13, new ItemStack(Items.COAL, 2));
        be.pending.set(0, material(output, 2));
        be.pendingFluid1 = new FluidStack(CatalogueFluids.fluid("warm"), 500);
        be.pendingFluid2 = new FluidStack(CatalogueFluids.fluid("wastewater"), 250);
        be.tanks[0].fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        be.experience = 3;
        be.pendingExperience = 5;
        be.fuelHeat = 10000;
        be.heatCredit = 1000;
        var saved = be.saveWithoutMetadata(h.getLevel().registryAccess()).copy();
        for (int reload = 0; reload < 2; reload++) {
            be = machine(h, kind);
            be.loadWithComponents(saved, h.getLevel().registryAccess());
            for (int tick = 0; tick < 100; tick++) IndustryProcessing.tick(be);
            h.assertTrue(be.status == 8 && !be.idle(), "Legacy batch freezes one tick before completion: " + recipe + " / " + output);
            var after = be.saveWithoutMetadata(h.getLevel().registryAccess());
            h.assertTrue(saved.equals(after), "Reload preserves all batch, energy, fuel, inventory, fluid and experience state: " + recipe + " / " + output);
            saved = after.copy();
        }
    }

    @GameTest(template = "empty")
    public static void reservedActiveRecipesFreezeEvenWithoutReservedPendingItems(GameTestHelper h) {
        for (String id : INDUSTRIAL) assertFrozenAcrossReload(h, id, "iron", CatalogueContent.recipe(id).get("station").getAsString());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reservedPendingItemsFreezeLegacyAutocraftingAcrossReload(GameTestHelper h) {
        h.assertTrue(CatalogueContent.RESERVED_ITEMS.equals(Set.of("moon", "lunar", "moon_tail", "liner", "lined_box", "anima", "anima_basic", "staff_blank")), "Keep the complete future-dimension reservation boundary");
        for (String key : CatalogueContent.RESERVED_ITEMS) assertFrozenAcrossReload(h, "craft:legacy_reserved", key, "autocrafter");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lunarImportRejectsBothHandsWithoutInventoryOrLedgerMutation(GameTestHelper h) {
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ReservedImport"));
        player.getInventory().clearContent();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        try {
            var tag = new CompoundTag();
            tag.putLong("SupplyDay", 0);
            tag.putInt("Trades", 2);
            tag.putInt("LunarTrades", 5);
            var ledger = SupplyLedger.load(tag, h.getLevel().registryAccess());
            var ledgerBefore = ledger.save(new CompoundTag(), h.getLevel().registryAccess()).copy();
            for (boolean full : new boolean[]{false, true}) for (boolean validOffer : new boolean[]{true, false}) {
                player.getInventory().clearContent();
                if (full) for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(validOffer ? Items.IRON_INGOT : Items.DIRT, 9));
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(validOffer ? Items.IRON_INGOT : Items.DIRT, 11));
                var before = new ArrayList<ItemStack>();
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) before.add(player.getInventory().getItem(i).copy());
                for (var hand : InteractionHand.values()) for (int attempt = 0; attempt < 9; attempt++) {
                    h.assertTrue(ledger.importLunar(player, hand) == SupplyLedger.Result.UNAVAILABLE && ledger.lunarRemaining(h.getLevel()) == 0, "Reserved imports reject both hands regardless of offer, capacity or attempt count");
                    for (int i = 0; i < before.size(); i++) h.assertTrue(ItemStack.matches(before.get(i), player.getInventory().getItem(i)), "Rejected import preserves every inventory slot");
                    h.assertTrue(!ledger.isDirty() && ledgerBefore.equals(ledger.save(new CompoundTag(), h.getLevel().registryAccess())), "Rejected import and quota reads cannot change day, normal trades, legacy lunar counter or dirty state");
                }
            }
            var restored = SupplyLedger.load(ledgerBefore, h.getLevel().registryAccess());
            h.assertTrue(restored.lunarRemaining(h.getLevel()) == 0 && ledgerBefore.equals(restored.save(new CompoundTag(), h.getLevel().registryAccess())), "Legacy counters survive reload without reopening imports");
        } finally {
            player.getInventory().clearContent();
        }
        h.succeed();
    }
}
