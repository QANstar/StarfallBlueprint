package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class ReinforcementProgressionGameTests {
    private static final BlockPos STATION = new BlockPos(1, 1, 1);

    private static CraftingInput input(String kind, ItemStack source) {
        return kind.equals("reinforce_composite")
                ? CraftingInput.of(2, 2, List.of(source, new ItemStack(CatalogueContent.item("wear")),
                        new ItemStack(CatalogueContent.item("boroglass")), new ItemStack(Items.BLAZE_POWDER)))
                : CraftingInput.of(2, 1, List.of(source, new ItemStack(kind.equals("reinforce_wear")
                        ? CatalogueContent.item("wear") : StarfallBlueprint.TOOL_BRACE.get())));
    }

    private static ItemStack upgrade(GameTestHelper h, ItemStack source, String kind) {
        var recipe = new BasicCrystalRecipe(CraftingBookCategory.EQUIPMENT, kind);
        var input = input(kind, source);
        ItemStack snapshot = source.copy();
        h.assertTrue(recipe.matches(input, h.getLevel()), "The next finite reinforcement level matches");
        ItemStack result = recipe.assemble(input, h.getLevel().registryAccess());
        h.assertTrue(ItemStack.matches(source, snapshot), "Assembly leaves all source components unchanged");
        long scaled = (long) source.getDamageValue() * result.getMaxDamage();
        int expectedDamage = (int) ((scaled + source.getMaxDamage() - 1) / source.getMaxDamage());
        h.assertTrue(result.getCount() == 1 && result.getDamageValue() == expectedDamage,
                "Each transition preserves damage fraction with upward rounding");
        return result;
    }

    private static ItemStack tool(GameTestHelper h, Item item, int percent) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(100);
        if (percent == 10) return upgrade(h, stack, "reinforce_wear");
        if (percent >= 20) stack = upgrade(h, stack, "reinforce_tool");
        if (percent == 30) stack = upgrade(h, stack, "reinforce_composite");
        return stack;
    }

    private static FakePlayer player(GameTestHelper h, Block station) {
        h.setBlock(STATION.below(), Blocks.STONE);
        h.setBlock(STATION, station);
        FakePlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "UpgradeTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.experienceLevel = 100;
        BlockPos absolute = h.absolutePos(STATION);
        player.setPos(absolute.getX() + .5, absolute.getY(), absolute.getZ() + 1.5);
        return player;
    }

    private static ContainerLevelAccess access(GameTestHelper h) {
        return ContainerLevelAccess.create(h.getLevel(), h.absolutePos(STATION));
    }

    private static ItemStack take(GameTestHelper h, Slot slot, FakePlayer player) {
        h.assertTrue(slot.hasItem() && slot.mayPickup(player), "Real survival workstation output is available");
        ItemStack result = slot.remove(1);
        slot.onTake(player, result);
        return result;
    }

    private static void assertLevel(GameTestHelper h, ItemStack stack, int percent, int base) {
        h.assertTrue(!stack.isEmpty() && Reinforcement.percent(stack) == percent
                        && Reinforcement.baseDurability(stack) == base
                        && stack.getMaxDamage() == base * (100 + percent) / 100,
                "Upgrade level, original capacity and actual maximum remain consistent");
    }

    @GameTest(template = "empty")
    public static void finiteProgressionUsesOriginalCapacityAndPreservesComponents(GameTestHelper h) {
        var enchantment = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.UNBREAKING);
        for (Item item : new Item[]{Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, CatalogueContent.item("sample_pick")}) {
            int base = new ItemStack(item).getMaxDamage();
            for (int damage : new int[]{0, 1, base / 2, base - 1}) {
                ItemStack original = new ItemStack(item);
                original.setDamageValue(damage);
                original.set(DataComponents.CUSTOM_NAME, Component.literal("Expedition"));
                original.enchant(enchantment, 2);
                var data = CatalogueEquipment.getData(original);
                data.putBoolean("LowImpact", true);
                data.putString("Unrelated", "retained");
                CatalogueEquipment.setData(original, data);
                ItemStack wear = upgrade(h, original, "reinforce_wear");
                assertLevel(h, wear, 10, base);
                ItemStack phase = upgrade(h, wear, "reinforce_tool");
                assertLevel(h, phase, 20, base);
                ItemStack composite = upgrade(h, phase, "reinforce_composite");
                assertLevel(h, composite, 30, base);
                ItemStack restored = ItemStack.parse(h.getLevel().registryAccess(), composite.save(h.getLevel().registryAccess())).orElseThrow();
                h.assertTrue(ItemStack.matches(composite, restored), "Upgrade level and original basis survive item serialization");
                h.assertTrue(composite.get(DataComponents.CUSTOM_NAME).equals(original.get(DataComponents.CUSTOM_NAME))
                                && composite.getEnchantmentLevel(enchantment) == 2
                                && CatalogueEquipment.getData(composite).getBoolean("LowImpact")
                                && CatalogueEquipment.getData(composite).getString("Unrelated").equals("retained"),
                        "Upgrade copies names, enchantments, sampling mode and unrelated custom data");
                for (String kind : new String[]{"reinforce_wear", "reinforce_tool", "reinforce_composite"}) {
                    var recipe = new BasicCrystalRecipe(CraftingBookCategory.EQUIPMENT, kind);
                    h.assertTrue(!recipe.matches(input(kind, composite), h.getLevel()), "The final slot cannot stack or downgrade");
                }
                h.assertTrue(!new BasicCrystalRecipe(CraftingBookCategory.EQUIPMENT, "reinforce_wear")
                                .matches(input("reinforce_wear", wear), h.getLevel()), "Wear cannot be repeated");
                h.assertTrue(!new BasicCrystalRecipe(CraftingBookCategory.EQUIPMENT, "reinforce_composite")
                                .matches(input("reinforce_composite", wear), h.getLevel()), "10% cannot skip the phase stage");
            }
        }
        ItemStack shield = new ItemStack(Items.SHIELD);
        var shieldRecipe = new BasicCrystalRecipe(CraftingBookCategory.EQUIPMENT, "reinforce_shield");
        var shieldInput = CraftingInput.of(2, 1, List.of(shield, new ItemStack(StarfallBlueprint.SHIELD_LINING.get())));
        h.assertTrue(shieldRecipe.matches(shieldInput, h.getLevel()), "An ordinary shield accepts its lining");
        ItemStack lined = shieldRecipe.assemble(shieldInput, h.getLevel().registryAccess());
        h.assertTrue(!shieldRecipe.matches(CraftingInput.of(2, 1, List.of(lined,
                        new ItemStack(StarfallBlueprint.SHIELD_LINING.get()))), h.getLevel()), "Shield lining remains single-use");
        h.assertTrue(!new BasicCrystalRecipe(CraftingBookCategory.EQUIPMENT, "reinforce_composite")
                        .matches(input("reinforce_composite", lined), h.getLevel()), "Composite is restricted to pickaxes");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyUpgradeRestoresBaseAndCompositePlanningRequiresEveryMaterial(GameTestHelper h) {
        for (int percent : new int[]{10, 20, 30}) {
            for (boolean explicitPercent : new boolean[]{false, true}) {
                ItemStack old = new ItemStack(Items.DIAMOND_PICKAXE);
                old.set(DataComponents.MAX_DAMAGE, 1561 * (100 + percent) / 100);
                old.setDamageValue(old.getMaxDamage() - 1);
                old.set(StarfallBlueprint.REINFORCED.get(), true);
                if (explicitPercent) {
                    var data = CatalogueEquipment.getData(old);
                    data.putInt("ReinforcementPercent", percent);
                    CatalogueEquipment.setData(old, data);
                }
                h.assertTrue(Reinforcement.percent(old) == percent && Reinforcement.baseDurability(old) == 1561,
                        "Explicit and boolean-only legacy levels recover their original capacity without rewriting the source");
                var phaseRecipe = new BasicCrystalRecipe(CraftingBookCategory.EQUIPMENT, "reinforce_tool");
                h.assertTrue(phaseRecipe.matches(input("reinforce_tool", old), h.getLevel()) == (percent == 10),
                        "Legacy wear can progress but legacy phase and composite cannot repeat the brace");
                if (percent == 10) old = upgrade(h, old, "reinforce_tool");
                if (percent != 30) old = upgrade(h, old, "reinforce_composite");
                assertLevel(h, old, 30, 1561);
            }
        }
        ItemStack unusualLegacy = new ItemStack(Items.NETHERITE_PICKAXE);
        unusualLegacy.set(StarfallBlueprint.REINFORCED.get(), true);
        unusualLegacy.set(DataComponents.MAX_DAMAGE, 1873);
        h.assertTrue(Reinforcement.percent(unusualLegacy) == 20 && unusualLegacy.getMaxDamage() == 1873,
                "An unrecognized old smithing capacity falls back to phase without expanding the stored maximum");
        ItemStack phase = tool(h, Items.IRON_PICKAXE, 20);
        var recipe = new BasicCrystalRecipe(CraftingBookCategory.EQUIPMENT, "reinforce_composite");
        var stock = List.of(new ItemStack(Items.BLAZE_POWDER, 4), new ItemStack(CatalogueContent.item("boroglass"), 3),
                phase, new ItemStack(CatalogueContent.item("wear"), 2));
        var plan = AutomaticCrafting.plan(recipe, stock, h.getLevel()).orElseThrow();
        h.assertTrue(plan.consumed().equals(List.of(1, 1, 1, 1)), "Unordered automation consumes one of each of four different inputs");
        assertLevel(h, plan.outputs().getFirst(), 30, 250);
        h.assertTrue(stock.getFirst().getCount() == 4 && stock.get(1).getCount() == 3
                        && stock.get(3).getCount() == 2 && Reinforcement.percent(phase) == 20,
                "Planning preserves source stacks and components");
        for (int missing = 0; missing < stock.size(); missing++) {
            var incomplete = new java.util.ArrayList<>(stock);
            incomplete.remove(missing);
            h.assertTrue(AutomaticCrafting.plan(recipe, incomplete, h.getLevel()).isEmpty(), "Every composite ingredient is mandatory");
        }
        h.assertTrue(!recipe.canCraftInDimensions(3, 1) && recipe.canCraftInDimensions(2, 2), "Composite requires four crafting slots");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realCompositeCraftingConsumesOneOfEveryIngredient(GameTestHelper h) {
        FakePlayer player = player(h, Blocks.CRAFTING_TABLE);
        CraftingMenu menu = new CraftingMenu(1, player.getInventory(), access(h));
        try {
            ItemStack phase = tool(h, Items.IRON_PICKAXE, 20);
            phase.set(DataComponents.CUSTOM_NAME, Component.literal("Composite source"));
            menu.getSlot(1).set(phase.copy());
            menu.getSlot(2).set(new ItemStack(CatalogueContent.item("wear"), 3));
            menu.getSlot(3).set(new ItemStack(CatalogueContent.item("boroglass"), 3));
            h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Composite has no output before the catalyst arrives");
            menu.getSlot(4).set(new ItemStack(Items.GLOWSTONE_DUST, 3));
            h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "A different powder cannot replace blaze powder");
            menu.getSlot(4).set(new ItemStack(Items.BLAZE_POWDER, 3));
            assertLevel(h, menu.getSlot(0).getItem(), 30, 250);
            h.assertTrue(ItemStack.matches(phase, menu.getSlot(1).getItem())
                            && menu.getSlot(2).getItem().getCount() == 3 && menu.getSlot(3).getItem().getCount() == 3
                            && menu.getSlot(4).getItem().getCount() == 3,
                    "A real composite preview changes none of the four source stacks");
            ItemStack result = take(h, menu.getSlot(0), player);
            assertLevel(h, result, 30, 250);
            h.assertTrue(result.get(DataComponents.CUSTOM_NAME).equals(phase.get(DataComponents.CUSTOM_NAME))
                            && menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().getCount() == 2
                            && menu.getSlot(3).getItem().getCount() == 2 && menu.getSlot(4).getItem().getCount() == 2
                            && menu.getSlot(0).getItem().isEmpty(),
                    "Taking the result consumes one tool, wear, glass and catalyst exactly once, retaining its name");
            menu.getSlot(1).set(result.copy());
            h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Remaining materials cannot apply a second composite layer");
        } finally {
            menu.removed(player);
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mixedLevelWorkbenchAndGrindstoneRetainHighestExistingLevel(GameTestHelper h) {
        for (boolean grindstone : new boolean[]{false, true}) {
            for (int firstLevel : new int[]{0, 10, 20, 30}) {
                for (int secondLevel : new int[]{0, 10, 20, 30}) {
                    FakePlayer player = player(h, grindstone ? Blocks.GRINDSTONE : Blocks.CRAFTING_TABLE);
                    AbstractContainerMenu menu = grindstone ? new GrindstoneMenu(1, player.getInventory(), access(h))
                            : new CraftingMenu(1, player.getInventory(), access(h));
                    try {
                        ItemStack first = tool(h, Items.IRON_PICKAXE, firstLevel);
                        ItemStack second = tool(h, Items.IRON_PICKAXE, secondLevel);
                        first.setDamageValue(first.getMaxDamage() - 30);
                        second.setDamageValue(second.getMaxDamage() - 40);
                        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
                        var unbreaking = registry.getHolderOrThrow(Enchantments.UNBREAKING);
                        var curse = registry.getHolderOrThrow(Enchantments.VANISHING_CURSE);
                        first.enchant(unbreaking, 1);
                        second.enchant(curse, 1);
                        int output = grindstone ? 2 : 0;
                        int firstSlot = grindstone ? 0 : 1;
                        int secondSlot = grindstone ? 1 : 2;
                        menu.getSlot(firstSlot).set(first.copy());
                        menu.getSlot(secondSlot).set(second.copy());
                        ItemStack preview = menu.getSlot(output).getItem();
                        int level = Math.max(firstLevel, secondLevel);
                        int maximum = 250 * (100 + level) / 100;
                        assertLevel(h, preview, level, 250);
                        h.assertTrue(preview.getDamageValue() == maximum - 70 - maximum * 5 / 100
                                        && preview.getEnchantmentLevel(unbreaking) == 0
                                        && preview.getEnchantmentLevel(curse) == 1,
                                "Repair conserves remaining durability, adds vanilla 5%, removes ordinary enchantments and retains curse");
                        h.assertTrue(ItemStack.matches(first, menu.getSlot(firstSlot).getItem())
                                        && ItemStack.matches(second, menu.getSlot(secondSlot).getItem()), "Preview does not mutate either source");
                        ItemStack result = take(h, menu.getSlot(output), player);
                        assertLevel(h, result, level, 250);
                        h.assertTrue(menu.getSlot(firstSlot).getItem().isEmpty() && menu.getSlot(secondSlot).getItem().isEmpty()
                                        && menu.getSlot(output).getItem().isEmpty(), "Real repair consumes each source once and leaves no second output");
                    } finally {
                        menu.removed(player);
                        player.getInventory().clearContent();
                    }
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void anvilKeepsLeftLevelAndSmithingRebasesEveryLevel(GameTestHelper h) {
        for (int level : new int[]{10, 20, 30}) {
            FakePlayer player = player(h, Blocks.ANVIL);
            AnvilMenu anvil = new AnvilMenu(1, player.getInventory(), access(h));
            try {
                ItemStack left = tool(h, Items.IRON_PICKAXE, level);
                ItemStack right = tool(h, Items.IRON_PICKAXE, 30);
                left.setDamageValue(left.getMaxDamage() - 30);
                right.setDamageValue(right.getMaxDamage() - 40);
                anvil.getSlot(0).set(left.copy());
                anvil.getSlot(1).set(right.copy());
                assertLevel(h, anvil.getSlot(2).getItem(), level, 250);
                int cost = anvil.getCost();
                ItemStack result = take(h, anvil.getSlot(2), player);
                assertLevel(h, result, level, 250);
                h.assertTrue(player.experienceLevel == 100 - cost && anvil.getSlot(0).getItem().isEmpty()
                                && anvil.getSlot(1).getItem().isEmpty(), "Anvil charges experience and consumes the donor without transferring its 30% upgrade");
            } finally {
                anvil.removed(player);
                player.getInventory().clearContent();
            }
            player = player(h, Blocks.SMITHING_TABLE);
            SmithingMenu smithing = new SmithingMenu(2, player.getInventory(), access(h));
            try {
                ItemStack diamond = tool(h, Items.DIAMOND_PICKAXE, level);
                diamond.set(DataComponents.CUSTOM_NAME, Component.literal("Retained expedition pick"));
                var unbreaking = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                        .getHolderOrThrow(Enchantments.UNBREAKING);
                diamond.enchant(unbreaking, 1);
                smithing.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2));
                smithing.getSlot(1).set(diamond.copy());
                smithing.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT, 2));
                ItemStack preview = smithing.getSlot(3).getItem();
                assertLevel(h, preview, level, 2031);
                int expected = (int) (((long) diamond.getDamageValue() * preview.getMaxDamage()
                        + diamond.getMaxDamage() - 1) / diamond.getMaxDamage());
                h.assertTrue(preview.getDamageValue() == expected && preview.getEnchantmentLevel(unbreaking) == 1
                                && preview.get(DataComponents.CUSTOM_NAME).equals(diamond.get(DataComponents.CUSTOM_NAME))
                                && ItemStack.matches(diamond, smithing.getSlot(1).getItem()),
                        "Smithing uses netherite base, preserves damage fraction and components, and previews without consuming");
                ItemStack result = take(h, smithing.getSlot(3), player);
                assertLevel(h, result, level, 2031);
                h.assertTrue(smithing.getSlot(0).getItem().getCount() == 1 && smithing.getSlot(2).getItem().getCount() == 1
                                && smithing.getSlot(1).getItem().isEmpty() && smithing.getSlot(3).getItem().isEmpty()
                                && player.experienceLevel == 100, "Smithing consumes one template, base and ingot without XP or duplicate output");
            } finally {
                smithing.removed(player);
                player.getInventory().clearContent();
            }
        }
        h.succeed();
    }
}
