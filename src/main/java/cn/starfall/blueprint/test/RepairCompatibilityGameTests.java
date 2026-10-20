package cn.starfall.blueprint.test;

import cn.starfall.blueprint.BasicCrystalRecipe;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class RepairCompatibilityGameTests {
    private static final BlockPos WORKSTATION = new BlockPos(1, 1, 1);

    private static FakePlayer player(GameTestHelper h, Block workstation) {
        h.setBlock(WORKSTATION.below(), Blocks.STONE);
        h.setBlock(WORKSTATION, workstation);
        FakePlayer player = FakePlayerFactory.get(h.getLevel(),
                new GameProfile(UUID.randomUUID(), "RepairCompat"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);
        player.experienceLevel = 100;
        BlockPos pos = h.absolutePos(WORKSTATION);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
        return player;
    }

    private static ContainerLevelAccess access(GameTestHelper h) {
        return ContainerLevelAccess.create(h.getLevel(), h.absolutePos(WORKSTATION));
    }

    private static Holder<Enchantment> enchantment(GameTestHelper h, ResourceKey<Enchantment> key) {
        return h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    private static boolean reinforced(ItemStack stack) {
        return stack.getOrDefault(StarfallBlueprint.REINFORCED.get(), false);
    }

    private static ItemStack tool(GameTestHelper h, Item item, boolean upgrade) {
        ItemStack input = new ItemStack(item);
        input.setDamageValue(input.getMaxDamage() * 4 / 5);
        if (!upgrade) return input;
        boolean shield = item == Items.SHIELD;
        BasicCrystalRecipe recipe = new BasicCrystalRecipe(CraftingBookCategory.MISC,
                shield ? "reinforce_shield" : "reinforce_tool");
        CraftingInput crafting = CraftingInput.of(2, 1, List.of(input,
                new ItemStack(shield ? StarfallBlueprint.SHIELD_LINING.get() : StarfallBlueprint.TOOL_BRACE.get())));
        h.assertTrue(recipe.matches(crafting, h.getLevel()), "Fixture must use a valid real reinforcement recipe");
        return recipe.assemble(crafting, h.getLevel().registryAccess());
    }

    private static ItemStack take(GameTestHelper h, Slot slot, FakePlayer player) {
        h.assertTrue(slot.hasItem() && slot.mayPickup(player), "Survival player must be able to take the real result");
        ItemStack result = slot.remove(1);
        slot.onTake(player, result);
        return result;
    }

    private static void assertNoSecondReinforcement(GameTestHelper h, ItemStack stack) {
        if (!reinforced(stack)) return;
        boolean shield = stack.is(Items.SHIELD);
        BasicCrystalRecipe recipe = new BasicCrystalRecipe(CraftingBookCategory.MISC,
                shield ? "reinforce_shield" : "reinforce_tool");
        h.assertTrue(!recipe.matches(CraftingInput.of(2, 1, List.of(stack,
                        new ItemStack(shield ? StarfallBlueprint.SHIELD_LINING.get() : StarfallBlueprint.TOOL_BRACE.get()))),
                        h.getLevel()), "Repair output must keep its marker and refuse a second 20% upgrade");
    }

    @GameTest(template = "empty")
    public static void grindstoneDoubleRepairPreservesUpgradeInEitherSlot(GameTestHelper h) {
        for (Item type : new Item[]{Items.IRON_PICKAXE, Items.SHIELD}) {
            // Low two bits cover upgrade order; each group of four adds a curse arrangement.
            for (int flags = 0; flags < 24; flags++) {
                int curseCase = flags / 4;
                FakePlayer player = player(h, Blocks.GRINDSTONE);
                GrindstoneMenu menu = new GrindstoneMenu(1, player.getInventory(), access(h));
                try {
                    boolean topUpgraded = (flags & 1) != 0, bottomUpgraded = (flags & 2) != 0;
                    ItemStack top = tool(h, type, topUpgraded), bottom = tool(h, type, bottomUpgraded);
                    top.set(DataComponents.CUSTOM_NAME, Component.literal("Top item"));
                    bottom.set(DataComponents.CUSTOM_NAME, Component.literal("Bottom item"));
                    top.enchant(enchantment(h, Enchantments.UNBREAKING), 1);
                    bottom.enchant(enchantment(h, Enchantments.UNBREAKING), 2);
                    if (curseCase == 0 || curseCase >= 2) {
                        bottom.enchant(enchantment(h, Enchantments.VANISHING_CURSE), curseCase == 3 ? 2 : 1);
                    }
                    if (curseCase >= 1 && curseCase <= 4) {
                        top.enchant(enchantment(h, Enchantments.VANISHING_CURSE), curseCase == 4 ? 2 : 1);
                    }
                    if (curseCase == 5) top.enchant(enchantment(h, Enchantments.BINDING_CURSE), 1);
                    int expectedVanishing = curseCase == 4 ? 2 : 1;
                    int expectedBinding = curseCase == 5 ? 1 : 0;
                    int maximum = Math.max(top.getMaxDamage(), bottom.getMaxDamage());
                    int remaining = top.getMaxDamage() - top.getDamageValue()
                            + bottom.getMaxDamage() - bottom.getDamageValue() + maximum * 5 / 100;
                    int expectedDamage = Math.max(maximum - remaining, 0);
                    menu.getSlot(0).set(top.copy());
                    menu.getSlot(1).set(bottom.copy());
                    ItemStack preview = menu.getSlot(2).getItem();
                    h.assertTrue(preview.getMaxDamage() == maximum && preview.getDamageValue() == expectedDamage
                                    && reinforced(preview) == (topUpgraded || bottomUpgraded),
                            "Grindstone uses maximum capacity and absolute remaining durability, preserving either upgrade");
                    h.assertTrue(Component.literal("Top item").equals(preview.get(DataComponents.CUSTOM_NAME))
                                    && preview.getEnchantmentLevel(enchantment(h, Enchantments.UNBREAKING)) == 0
                                    && preview.getEnchantmentLevel(enchantment(h, Enchantments.VANISHING_CURSE)) == expectedVanishing
                                    && preview.getEnchantmentLevel(enchantment(h, Enchantments.BINDING_CURSE)) == expectedBinding
                                    && preview.getOrDefault(DataComponents.REPAIR_COST, 0) == (curseCase == 5 ? 3 : 1),
                            "Grindstone keeps the top name, strips ordinary enchantments, prefers the top curse level and resets repair cost by distinct curse count");
                    h.assertTrue(ItemStack.matches(menu.getSlot(0).getItem(), top)
                                    && ItemStack.matches(menu.getSlot(1).getItem(), bottom),
                            "Computing the grindstone preview cannot mutate or consume either input");
                    ItemStack result = take(h, menu.getSlot(2), player);
                    h.assertTrue(result.getMaxDamage() == maximum && result.getDamageValue() == expectedDamage
                                    && reinforced(result) == (topUpgraded || bottomUpgraded),
                            "Taken grindstone output must match preview durability and marker");
                    h.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty()
                                    && menu.getSlot(2).getItem().isEmpty(),
                            "Taking a merged grindstone result consumes both input items exactly once");
                    assertNoSecondReinforcement(h, result);
                } finally {
                    menu.removed(player);
                    player.getInventory().clearContent();
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void anvilDoubleRepairKeepsLeftTargetSemantics(GameTestHelper h) {
        for (Item type : new Item[]{Items.IRON_PICKAXE, Items.SHIELD}) {
            for (int flags = 0; flags < 4; flags++) {
                FakePlayer player = player(h, Blocks.ANVIL);
                AnvilMenu menu = new AnvilMenu(1, player.getInventory(), access(h));
                try {
                    boolean leftUpgraded = (flags & 1) != 0, rightUpgraded = (flags & 2) != 0;
                    ItemStack left = tool(h, type, leftUpgraded), right = tool(h, type, rightUpgraded);
                    left.set(DataComponents.CUSTOM_NAME, Component.literal("Left target"));
                    right.set(DataComponents.CUSTOM_NAME, Component.literal("Right donor"));
                    left.enchant(enchantment(h, Enchantments.UNBREAKING), 1);
                    right.enchant(enchantment(h, Enchantments.UNBREAKING), 1);
                    int maximum = left.getMaxDamage();
                    int remaining = maximum - left.getDamageValue()
                            + right.getMaxDamage() - right.getDamageValue() + maximum * 12 / 100;
                    int expectedDamage = Math.max(maximum - remaining, 0);
                    menu.getSlot(0).set(left.copy());
                    menu.getSlot(1).set(right.copy());
                    menu.setItemName("Left target");
                    ItemStack preview = menu.getSlot(2).getItem();
                    h.assertTrue(preview.getMaxDamage() == maximum && preview.getDamageValue() == expectedDamage
                                    && reinforced(preview) == leftUpgraded,
                            "Anvil preserves the left target's capacity and upgrade; right-side reinforcement is consumed as donor material");
                    h.assertTrue(Component.literal("Left target").equals(preview.get(DataComponents.CUSTOM_NAME))
                                    && preview.getEnchantmentLevel(enchantment(h, Enchantments.UNBREAKING)) == 2,
                            "Vanilla anvil naming and equal-level enchantment merging remain intact");
                    h.assertTrue(ItemStack.matches(menu.getSlot(0).getItem(), left)
                                    && ItemStack.matches(menu.getSlot(1).getItem(), right),
                            "Anvil preview cannot consume or change either input");
                    int cost = menu.getCost();
                    h.assertTrue(cost > 0 && cost < 40, "Ordinary combination keeps a finite vanilla level cost");
                    ItemStack result = take(h, menu.getSlot(2), player);
                    h.assertTrue(player.experienceLevel == 100 - cost && result.getMaxDamage() == maximum
                                    && result.getDamageValue() == expectedDamage && reinforced(result) == leftUpgraded,
                            "Taking the anvil result charges its actual displayed cost and preserves left-target semantics");
                    h.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty()
                                    && menu.getSlot(2).getItem().isEmpty(),
                            "Anvil combination consumes the target and donor exactly once");
                    assertNoSecondReinforcement(h, result);
                } finally {
                    menu.removed(player);
                    player.getInventory().clearContent();
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingDoubleRepairKeepsUpgradeAndVanillaCurseRules(GameTestHelper h) {
        h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("repair_item")).isPresent(),
                "The actual vanilla repair recipe ID must load");
        for (Item type : new Item[]{Items.IRON_PICKAXE, Items.SHIELD}) {
            for (int flags = 0; flags < 4; flags++) {
                FakePlayer player = player(h, Blocks.CRAFTING_TABLE);
                CraftingMenu menu = new CraftingMenu(1, player.getInventory(), access(h));
                try {
                    boolean firstUpgraded = (flags & 1) != 0, secondUpgraded = (flags & 2) != 0;
                    ItemStack first = tool(h, type, firstUpgraded), second = tool(h, type, secondUpgraded);
                    first.set(DataComponents.CUSTOM_NAME, Component.literal("First name"));
                    second.set(DataComponents.CUSTOM_NAME, Component.literal("Second name"));
                    first.enchant(enchantment(h, Enchantments.UNBREAKING), 1);
                    second.enchant(enchantment(h, Enchantments.VANISHING_CURSE), 1);
                    int maximum = Math.max(first.getMaxDamage(), second.getMaxDamage());
                    int remaining = first.getMaxDamage() - first.getDamageValue()
                            + second.getMaxDamage() - second.getDamageValue() + maximum * 5 / 100;
                    int expectedDamage = Math.max(maximum - remaining, 0);
                    menu.getSlot(1).set(first.copy());
                    h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "One tool is not a workbench repair recipe");
                    menu.getSlot(2).set(second.copy());
                    ItemStack preview = menu.getSlot(0).getItem();
                    h.assertTrue(preview.getMaxDamage() == maximum && preview.getDamageValue() == expectedDamage
                                    && reinforced(preview) == (firstUpgraded || secondUpgraded),
                            "Real workbench lookup uses the larger capacity and retains either input's upgrade");
                    h.assertTrue(!preview.has(DataComponents.CUSTOM_NAME)
                                    && preview.getEnchantmentLevel(enchantment(h, Enchantments.UNBREAKING)) == 0
                                    && preview.getEnchantmentLevel(enchantment(h, Enchantments.VANISHING_CURSE)) == 1,
                            "Workbench repair retains vanilla removal of names/ordinary enchantments and preservation of curses");
                    h.assertTrue(ItemStack.matches(menu.getSlot(1).getItem(), first)
                                    && ItemStack.matches(menu.getSlot(2).getItem(), second),
                            "Workbench preview cannot consume or change either input");
                    ItemStack result = take(h, menu.getSlot(0), player);
                    h.assertTrue(result.getMaxDamage() == maximum && result.getDamageValue() == expectedDamage
                                    && reinforced(result) == (firstUpgraded || secondUpgraded),
                            "Taking a real workbench result keeps the preview's capacity, damage and marker");
                    h.assertTrue(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty()
                                    && menu.getSlot(0).getItem().isEmpty() && player.experienceLevel == 100,
                            "Workbench repair consumes both tools once and charges no experience");
                    assertNoSecondReinforcement(h, result);
                    menu.getSlot(1).set(first.copy());
                    menu.getSlot(2).set(second.copy());
                    menu.getSlot(3).set(new ItemStack(Items.DIRT));
                    h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "An unrelated third input must reject the repair recipe");
                } finally {
                    menu.removed(player);
                    player.getInventory().clearContent();
                }
            }
        }
        h.succeed();
    }

    private static void checkSmithing(GameTestHelper h, boolean upgrade) {
        h.assertTrue(h.getLevel().getRecipeManager()
                        .byKey(ResourceLocation.withDefaultNamespace("netherite_pickaxe_smithing")).isPresent(),
                "The actual netherite pickaxe smithing recipe ID must load");
        FakePlayer player = player(h, Blocks.SMITHING_TABLE);
        SmithingMenu menu = new SmithingMenu(1, player.getInventory(), access(h));
        try {
            ItemStack diamond = new ItemStack(Items.DIAMOND_PICKAXE);
            diamond.setDamageValue(100);
            diamond.set(DataComponents.CUSTOM_NAME, Component.literal("Expedition pick"));
            diamond.enchant(enchantment(h, Enchantments.UNBREAKING), 1);
            diamond.enchant(enchantment(h, Enchantments.VANISHING_CURSE), 1);
            if (upgrade) {
                BasicCrystalRecipe recipe = new BasicCrystalRecipe(CraftingBookCategory.MISC, "reinforce_tool");
                CraftingInput input = CraftingInput.of(2, 1,
                        List.of(diamond, new ItemStack(StarfallBlueprint.TOOL_BRACE.get())));
                h.assertTrue(recipe.matches(input, h.getLevel()), "Diamond fixture accepts real reinforcement");
                diamond = recipe.assemble(input, h.getLevel().registryAccess());
                h.assertTrue(diamond.getMaxDamage() == 1873 && diamond.getDamageValue() == 120,
                        "Reinforced diamond fixture has the known base and rounded damage");
            }
            menu.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2));
            menu.getSlot(1).set(diamond.copy());
            h.assertTrue(menu.getSlot(3).getItem().isEmpty(), "Missing netherite ingredient cannot create a smithing result");
            menu.getSlot(2).set(new ItemStack(Items.IRON_INGOT));
            h.assertTrue(menu.getSlot(3).getItem().isEmpty(), "Wrong upgrade ingredient cannot create a result");
            menu.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT, 2));
            int maximum = upgrade ? 2437 : 2031;
            int damage = upgrade ? 157 : 100;
            ItemStack preview = menu.getSlot(3).getItem();
            h.assertTrue(preview.is(Items.NETHERITE_PICKAXE) && preview.getCount() == 1
                            && preview.getMaxDamage() == maximum && preview.getDamageValue() == damage
                            && reinforced(preview) == upgrade,
                    "Smithing derives 20% durability from the new netherite base and preserves reinforced damage fraction; normal picks stay vanilla");
            h.assertTrue(Component.literal("Expedition pick").equals(preview.get(DataComponents.CUSTOM_NAME))
                            && preview.getEnchantmentLevel(enchantment(h, Enchantments.UNBREAKING)) == 1
                            && preview.getEnchantmentLevel(enchantment(h, Enchantments.VANISHING_CURSE)) == 1,
                    "Smithing preserves source name, ordinary enchantments and curse");
            h.assertTrue(ItemStack.matches(menu.getSlot(1).getItem(), diamond)
                            && menu.getSlot(0).getItem().getCount() == 2 && menu.getSlot(2).getItem().getCount() == 2,
                    "Smithing preview cannot consume template, source tool or ingot");
            ItemStack result = take(h, menu.getSlot(3), player);
            h.assertTrue(result.is(Items.NETHERITE_PICKAXE) && result.getMaxDamage() == maximum
                            && result.getDamageValue() == damage && reinforced(result) == upgrade,
                    "The taken result matches the computed smithing preview");
            h.assertTrue(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(0).getItem().getCount() == 1
                            && menu.getSlot(2).getItem().getCount() == 1 && menu.getSlot(3).getItem().isEmpty()
                            && player.experienceLevel == 100,
                    "Smithing consumes exactly one template, one base and one ingot with no experience charge");
            assertNoSecondReinforcement(h, result);
        } finally {
            menu.removed(player);
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void smithingReinforcedDiamondPickUsesNetheriteBase(GameTestHelper h) {
        checkSmithing(h, true);
    }

    @GameTest(template = "empty")
    public static void smithingOrdinaryDiamondPickRemainsVanilla(GameTestHelper h) {
        checkSmithing(h, false);
    }
}
