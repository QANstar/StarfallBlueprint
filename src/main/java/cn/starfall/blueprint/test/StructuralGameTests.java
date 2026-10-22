package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class StructuralGameTests {
    private static ItemStack item(String name) { return new ItemStack(StarfallBlueprint.structural(name)); }
    private static ItemStack portion(String name, int mass) {
        ItemStack s = item(name); s.set(StarfallBlueprint.PORTION_MASS.get(), mass); return s;
    }
    private static ItemStack crystal() {
        var stack = CatalogueContent.stack("common", 1);
        stack.set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(100));
        return stack;
    }
    private static CraftingInput input(ItemStack... stacks) { return CraftingInput.of(stacks.length, 1, List.of(stacks)); }
    private static CraftingRecipe recipe(GameTestHelper h, String name) {
        return (CraftingRecipe)h.getLevel().getRecipeManager().byKey(StarfallBlueprint.id(name)).orElseThrow().value();
    }

    @GameTest(template = "empty")
    public static void ordinaryMaterialRecipeBudgets(GameTestHelper h) {
        Object[][] cases = {
            {"iron_plate", 2, CraftingInput.of(1, 2, List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT)))},
            {"copper_plate", 1, input(new ItemStack(Items.COPPER_INGOT))},
            {"iron_fasteners", 3, input(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT))},
            {"insulation", 2, input(new ItemStack(Items.PAPER), CatalogueContent.stack("boron", 1))},
            {"binder", 2, input(new ItemStack(Items.WHEAT), new ItemStack(Items.CLAY_BALL))},
            {"control_board", 1, input(new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.REDSTONE), new ItemStack(Items.GLASS), item("insulation"), crystal())},
            {"circuit_breaker", 1, input(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.REDSTONE), item("insulation"))},
            {"ceramic_sheath", 2, input(item("ceramic"), CatalogueContent.stack("titan",1))},
            {"steel_fasteners", 1, input(item("ordinary_steel"))},
            {"sealed_box", 1, input(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GLASS), item("insulation"))}
        };
        for (Object[] c : cases) {
            String name = (String)c[0]; CraftingInput in = (CraftingInput)c[2]; var r = recipe(h, name);
            h.assertTrue(r.matches(in, h.getLevel()), name + " budget must load and match");
            h.assertTrue(h.getLevel().getRecipeManager().getRecipesFor(RecipeType.CRAFTING, in, h.getLevel()).size() == 1, name + " must not collide with another loaded recipe");
            ItemStack out = r.assemble(in, h.getLevel().registryAccess());
            h.assertTrue(out.is(StarfallBlueprint.structural(name)) && out.getCount() == (int)c[1], name + " output budget");
        }
        h.assertTrue(!recipe(h, "iron_plate").matches(input(new ItemStack(Items.IRON_INGOT)), h.getLevel()), "Single ingot remains the vanilla nugget recipe");
        h.assertTrue(!recipe(h, "control_board").matches(input(new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.REDSTONE), new ItemStack(Items.GLASS), crystal()), h.getLevel()), "Valid Q0 cannot replace the missing insulation in a board");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wholeSubstratesRejectLegacyPortions(GameTestHelper h) {
        var sub = recipe(h, "isolation_substrate");
        var in = input(item("copper_plate"), CatalogueContent.stack("boron", 1));
        h.assertTrue(sub.matches(in, h.getLevel()), "One whole plate and boron must match");
        h.assertTrue(sub.assemble(in, h.getLevel().registryAccess()).getCount() == 2, "Whole ingredients make two substrates");
        h.assertTrue(sub.getRemainingItems(in).stream().allMatch(ItemStack::isEmpty), "No partial-item remainders");
        h.assertTrue(!sub.matches(input(portion("copper_plate", 50), CatalogueContent.stack("boron", 1)), h.getLevel()), "Old half plates cannot become whole ingredients");
        h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
            input(new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.COAL)), h.getLevel()).isEmpty(), "A workbench cannot produce steel billets");
        var frame = recipe(h, "machine_frame");
        var frameInput = CraftingInput.of(3, 3, List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT),
            new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.COBBLESTONE), new ItemStack(Items.COBBLESTONE), item("iron_fasteners")));
        h.assertTrue(frame.matches(frameInput, h.getLevel()) && frame.assemble(frameInput, h.getLevel().registryAccess()).is(StarfallBlueprint.structural("machine_frame")), "Frame is a real nine-item vanilla crafting recipe");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realWorkbenchConsumesWholeItemsOnce(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1); h.setBlock(pos, Blocks.CRAFTING_TABLE);
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "StructuralTest"));
        p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent();
        var menu = new CraftingMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(pos)));
        try {
            menu.getSlot(1).set(new ItemStack(StarfallBlueprint.structural("copper_plate"), 2));
            menu.getSlot(2).set(CatalogueContent.stack("boron", 2));
            h.assertTrue(menu.getSlot(0).getItem().is(StarfallBlueprint.structural("isolation_substrate")), "Real workbench resolves custom recipe");
            h.assertTrue(menu.getSlot(1).getItem().getCount() == 2 && p.getInventory().isEmpty(), "Preview never consumes or returns portions");
            menu.clicked(0, 0, ClickType.QUICK_MOVE, p);
        } finally { menu.removed(p); }
        int copper = 0, boron = 0, output = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(StarfallBlueprint.structural("copper_plate"))) copper += s.getCount() * StructuralMaterialItem.mass(s);
            if (CatalogueContent.matches("boron",s) || s.is(StarfallBlueprint.structural("quartz_portion"))) boron += s.getCount() * StructuralMaterialItem.mass(s);
            if (s.is(StarfallBlueprint.structural("isolation_substrate"))) output += s.getCount();
        }
        h.assertTrue(output == 4 && copper == 0 && boron == 0,
            "Shift-click consumes two whole plates and boron for four substrates, without fragments; actual outputs=" + output + ", copper=" + copper + ", boron=" + boron);
        p.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void furnaceCoalBudgetAndCeramic(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1); h.setBlock(pos, Blocks.FURNACE);
        var furnace = (AbstractFurnaceBlockEntity)h.getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(StarfallBlueprint.structural("mixed_steel_billet"), 8));
        furnace.setItem(1, new ItemStack(Items.COAL));
        for (int i = 0; i < 1600; i++) AbstractFurnaceBlockEntity.serverTick(h.getLevel(), furnace.getBlockPos(), furnace.getBlockState(), furnace);
        h.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(1).isEmpty()
            && furnace.getItem(2).is(StarfallBlueprint.structural("ordinary_steel")) && furnace.getItem(2).getCount() == 8, "Real furnace gives eight steel per coal, 200 ticks each");
        furnace.setItem(2, ItemStack.EMPTY); furnace.setItem(0, new ItemStack(Items.CLAY_BALL));
        furnace.setItem(1, portion("carbon_portion", 20));
        for (int i = 0; i < 200; i++) AbstractFurnaceBlockEntity.serverTick(h.getLevel(), furnace.getBlockPos(), furnace.getBlockState(), furnace);
        h.assertTrue(furnace.getItem(2).is(StarfallBlueprint.structural("ceramic")) && furnace.getItem(2).getCount() == 1, "Fractional carbon fuel fires the actual ceramic recipe");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void portionsPersistAndCannotBecomeWholeVanillaItems(GameTestHelper h) {
        ItemStack s = portion("carbon_portion", 30);
        ItemStack restored = ItemStack.parse(h.getLevel().registryAccess(), s.save(h.getLevel().registryAccess())).orElseThrow();
        h.assertTrue(ItemStack.matches(s, restored) && restored.getBurnTime(RecipeType.SMELTING) == 480, "Persisted carbon retains actual mass and fuel");
        h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input(restored), h.getLevel()).isEmpty(), "Fragment cannot enter whole-coal vanilla crafting");
        h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input(portion("quartz_portion", 50)), h.getLevel()).isEmpty(), "Legacy half quartz cannot enter whole-item recipes");
        h.succeed();
    }
}
