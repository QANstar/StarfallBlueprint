package cn.starfall.blueprint.test;

import cn.starfall.blueprint.BasicCrystalRecipe;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class RepairGameTests {
    private static final BlockPos WORKSTATION = new BlockPos(1, 1, 1);

    private static FakePlayer survivalPlayer(GameTestHelper h) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(),
                new GameProfile(UUID.randomUUID(), "RepairTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);
        player.experienceLevel = 20;
        player.experienceProgress = 0;
        player.totalExperience = 0;
        BlockPos absolute = h.absolutePos(WORKSTATION);
        player.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 1.5);
        return player;
    }

    private static Component name(boolean shield) {
        return Component.literal(shield ? "Reinforced camp shield" : "Reinforced camp pick");
    }

    private static ItemStack reinforced(GameTestHelper h, boolean shield) {
        ItemStack original = new ItemStack(shield ? Items.SHIELD : Items.IRON_PICKAXE);
        original.setDamageValue(100);
        original.set(DataComponents.CUSTOM_NAME, name(shield));
        BasicCrystalRecipe recipe = new BasicCrystalRecipe(CraftingBookCategory.MISC,
                shield ? "reinforce_shield" : "reinforce_tool");
        CraftingInput input = CraftingInput.of(2, 1, List.of(original,
                new ItemStack(shield ? StarfallBlueprint.SHIELD_LINING.get() : StarfallBlueprint.TOOL_BRACE.get())));
        h.assertTrue(recipe.matches(input, h.getLevel()), "The ordinary named damaged item accepts reinforcement");
        ItemStack result = recipe.assemble(input, h.getLevel().registryAccess());
        assertReinforcement(h, result, shield);
        h.assertTrue(result.getDamageValue() == 120, "Reinforcement preserves damage fraction with upward rounding");
        h.assertTrue(original.getMaxDamage() == (shield ? 336 : 250) && original.getDamageValue() == 100,
                "Crafting the reinforced fixture does not mutate its source");
        return result;
    }

    private static void assertReinforcement(GameTestHelper h, ItemStack result, boolean shield) {
        h.assertTrue(!result.isEmpty() && result.is(shield ? Items.SHIELD : Items.IRON_PICKAXE)
                && result.getCount() == 1, "The result is exactly one original item type");
        h.assertTrue(result.getMaxDamage() == (shield ? 403 : 300),
                "The reinforced maximum durability survives the vanilla workstation");
        h.assertTrue(result.getOrDefault(StarfallBlueprint.REINFORCED.get(), false),
                "The persistent reinforcement marker survives the vanilla workstation");
        h.assertTrue(name(shield).equals(result.get(DataComponents.CUSTOM_NAME)),
                "The existing custom name survives the vanilla workstation");
    }

    private static ItemStack take(GameTestHelper h, Slot slot, FakePlayer player) {
        h.assertTrue(slot.hasItem() && slot.mayPickup(player), "A survival player can take the workstation result");
        ItemStack result = slot.remove(1);
        slot.onTake(player, result);
        return result;
    }

    private static void checkAnvilRepair(GameTestHelper h, boolean shield) {
        h.setBlock(WORKSTATION.below(), Blocks.STONE);
        h.setBlock(WORKSTATION, Blocks.ANVIL);
        FakePlayer player = survivalPlayer(h);
        AnvilMenu menu = new AnvilMenu(1, player.getInventory(),
                ContainerLevelAccess.create(h.getLevel(), h.absolutePos(WORKSTATION)));
        try {
            ItemStack upgraded = reinforced(h, shield);
            h.assertTrue(menu.stillValid(player), "The fixture uses a real nearby anvil");
            menu.getSlot(0).set(upgraded.copy());
            menu.getSlot(1).set(new ItemStack(shield ? Items.OAK_PLANKS : Items.IRON_INGOT, 3));
            // The real anvil GUI sends its initial displayed name; null would request name removal.
            menu.setItemName(name(shield).getString());
            ItemStack preview = menu.getSlot(2).getItem();
            assertReinforcement(h, preview, shield);
            h.assertTrue(preview.getDamageValue() == 0,
                    "Vanilla repair materials repair the reinforced durability, not just the base item's capacity");
            h.assertTrue(menu.getSlot(1).getItem().getCount() == 3 && upgraded.getDamageValue() == 120,
                    "Result preview consumes no materials and does not mutate the source");
            int cost = menu.getCost();
            h.assertTrue(cost == 2, "120 damage requires two quarter-capacity repair materials and two experience levels");
            ItemStack repaired = take(h, menu.getSlot(2), player);
            assertReinforcement(h, repaired, shield);
            h.assertTrue(repaired.getDamageValue() == 0 && player.experienceLevel == 20 - cost,
                    "Taking the real anvil result repairs damage and charges the survival experience cost");
            h.assertTrue(menu.getSlot(0).getItem().isEmpty()
                    && menu.getSlot(1).getItem().getCount() == 1 && menu.getSlot(2).getItem().isEmpty(),
                    "Taking the result consumes the input and exactly two materials without duplicating output");
        } finally {
            menu.removed(player);
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    private static void checkGrindstone(GameTestHelper h, boolean shield) {
        h.setBlock(WORKSTATION.below(), Blocks.STONE);
        h.setBlock(WORKSTATION, Blocks.GRINDSTONE);
        FakePlayer player = survivalPlayer(h);
        GrindstoneMenu menu = new GrindstoneMenu(1, player.getInventory(),
                ContainerLevelAccess.create(h.getLevel(), h.absolutePos(WORKSTATION)));
        try {
            ItemStack upgraded = reinforced(h, shield);
            var unbreaking = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.UNBREAKING);
            upgraded.enchant(unbreaking, 1);
            h.assertTrue(upgraded.isEnchanted(), "The single-item grindstone fixture has a real removable enchantment");
            h.assertTrue(menu.stillValid(player), "The fixture uses a real nearby grindstone");
            menu.getSlot(0).set(upgraded.copy());
            h.assertTrue(menu.getSlot(1).getItem().isEmpty(), "Disenchantment uses exactly one item, not two-item repair");
            ItemStack preview = menu.getSlot(2).getItem();
            assertReinforcement(h, preview, shield);
            h.assertTrue(!preview.isEnchanted() && preview.getDamageValue() == 120,
                    "Single-item grinding removes Unbreaking without repairing damage or losing reinforcement");
            h.assertTrue(upgraded.isEnchanted() && upgraded.getDamageValue() == 120,
                    "The grindstone preview does not mutate its enchanted source");
            ItemStack ground = take(h, menu.getSlot(2), player);
            assertReinforcement(h, ground, shield);
            h.assertTrue(!ground.isEnchanted() && ground.getDamageValue() == 120,
                    "The taken result keeps reinforced durability, damage and name after disenchantment");
            h.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty()
                    && menu.getSlot(2).getItem().isEmpty(), "Taking the grindstone result consumes its single input exactly once");
        } finally {
            menu.removed(player);
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reinforcedPickSurvivesVanillaAnvilMaterialRepair(GameTestHelper h) {
        checkAnvilRepair(h, false);
    }

    @GameTest(template = "empty")
    public static void reinforcedShieldSurvivesVanillaAnvilMaterialRepair(GameTestHelper h) {
        checkAnvilRepair(h, true);
    }

    @GameTest(template = "empty")
    public static void reinforcedPickSurvivesSingleItemGrindstoneDisenchantment(GameTestHelper h) {
        checkGrindstone(h, false);
    }

    @GameTest(template = "empty")
    public static void reinforcedShieldSurvivesSingleItemGrindstoneDisenchantment(GameTestHelper h) {
        checkGrindstone(h, true);
    }
}
