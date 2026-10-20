package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class BatchLifecycleGameTests {
    private static PreparationBlockEntity bench(GameTestHelper h) {
        h.setBlock(new BlockPos(1, 1, 1), StarfallBlueprint.PREPARATION.get());
        var b = (PreparationBlockEntity) h.getBlockEntity(new BlockPos(1, 1, 1));
        var ore = new ItemStack(StarfallBlueprint.RAW.get(), 2);
        ore.set(StarfallBlueprint.BATCH.get(), MaterialBatch.ore(12, 30));
        b.setItem(0, ore);
        b.setItem(1, new ItemStack(Items.COAL));
        b.water = 1000;
        return b;
    }
    private static void ticks(PreparationBlockEntity b, int count) {
        for (int i = 0; i < count; i++) PreparationBlockEntity.tick(b.getLevel(), b.getBlockPos(), b.getBlockState(), b);
    }

    @GameTest(template = "empty")
    public static void connectorChangeOnlyAffectsNextBatchAcrossSave(GameTestHelper h) {
        var b = bench(h);
        ticks(b, 73);
        b.setItem(4, new ItemStack(StarfallBlueprint.CONNECTOR.get()));
        var saved = b.saveWithoutMetadata(h.getLevel().registryAccess());
        b.loadWithComponents(saved, h.getLevel().registryAccess());
        ticks(b, 127);
        h.assertTrue(b.energy == 280000 && b.getItem(2).getCount() == 10,
                "Installing a connector during a saved batch cannot discount its reserved power");
        ticks(b, 73);
        b.setItem(4, ItemStack.EMPTY);
        saved = b.saveWithoutMetadata(h.getLevel().registryAccess());
        b.loadWithComponents(saved, h.getLevel().registryAccess());
        ticks(b, 127);
        h.assertTrue(b.energy == 244000 && b.getItem(2).getCount() == 20 && b.water == 500,
                "Removing connector cannot increase the saved upgraded batch's locked 36kXH budget");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fuelStarvationResumesWithoutReservingTwice(GameTestHelper h) {
        var b = bench(h);
        b.setItem(1, ItemStack.EMPTY);
        b.energy = 2000;
        ticks(b, 50);
        h.assertTrue(b.progress == 10 && b.energy == 0 && b.water == 750 && b.getItem(0).getCount() == 1,
                "Fuel starvation pauses after ten paid ticks with a single reserved input");
        var saved = b.saveWithoutMetadata(h.getLevel().registryAccess());
        b.loadWithComponents(saved, h.getLevel().registryAccess());
        ticks(b, 20);
        h.assertTrue(b.progress == 10 && b.water == 750 && b.getItem(2).isEmpty(),
                "Reloading without fuel cannot progress or reserve a second input");
        b.setItem(1, new ItemStack(Items.CHARCOAL));
        ticks(b, 190);
        h.assertTrue(b.energy == 282000 && b.getItem(2).getCount() == 10 && b.getItem(3).getCount() == 1,
                "Charcoal resumes the remaining 190 ticks and settles once");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shieldUpgradePreservesComponentsAndRoundsDamageUp(GameTestHelper h) {
        var shield = new ItemStack(Items.SHIELD);
        shield.setDamageValue(1);
        shield.set(DataComponents.CUSTOM_NAME, Component.literal("Camp shield"));
        var recipe = new BasicCrystalRecipe(CraftingBookCategory.MISC, "reinforce_shield");
        var input = CraftingInput.of(2, 1, List.of(shield, new ItemStack(StarfallBlueprint.SHIELD_LINING.get())));
        h.assertTrue(recipe.matches(input, h.getLevel()), "Ordinary damaged shield accepts lining");
        var result = recipe.assemble(input, h.getLevel().registryAccess());
        h.assertTrue(result.getMaxDamage() == 403 && result.getDamageValue() == 2,
                "Shield 336 becomes 403; damage rounds up so installation never repairs the damage fraction");
        h.assertTrue(Component.literal("Camp shield").equals(result.get(DataComponents.CUSTOM_NAME)),
                "Shield custom components survive reinforcement");
        h.assertTrue(!recipe.matches(CraftingInput.of(2, 1, List.of(result,
                new ItemStack(StarfallBlueprint.SHIELD_LINING.get()))), h.getLevel()), "Repeat shield reinforcement is refused");
        h.assertTrue(shield.getMaxDamage() == 336 && shield.getDamageValue() == 1,
                "Previewing recipe must not mutate the input shield");
        h.succeed();
    }
}
