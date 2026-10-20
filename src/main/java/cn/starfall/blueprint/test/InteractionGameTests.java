package cn.starfall.blueprint.test;

import cn.starfall.blueprint.CampWaterBlockEntity;
import cn.starfall.blueprint.MaterialBatch;
import cn.starfall.blueprint.PreparationBlockEntity;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class InteractionGameTests {
    private static final BlockPos WATER_POS = new BlockPos(1, 1, 1);

    private static FakePlayer survivalPlayer(GameTestHelper h) {
        // getMinecraft() is shared between tests; a unique profile isolates inventory and sneak state.
        FakePlayer player = FakePlayerFactory.get(h.getLevel(),
                new GameProfile(UUID.randomUUID(), "InteractionTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);
        player.moveTo(Vec3.atCenterOf(h.absolutePos(WATER_POS)).add(0, 0, 1));
        return player;
    }

    private static void cleanPlayer(FakePlayer player) {
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);
    }

    private static InteractionResult useBucket(GameTestHelper h, BlockPos local, FakePlayer player) {
        return useBucket(h, local, player, InteractionHand.MAIN_HAND);
    }

    private static InteractionResult useBucket(GameTestHelper h, BlockPos local, FakePlayer player, InteractionHand hand) {
        BlockPos absolute = h.absolutePos(local);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        return player.gameMode.useItemOn(player, h.getLevel(), player.getItemInHand(hand), hand, hit);
    }

    private static int count(Container container, Item item) {
        int result = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }

    private static int water(GameTestHelper h, boolean bench) {
        return bench ? ((PreparationBlockEntity) h.getBlockEntity(WATER_POS)).water
                : ((CampWaterBlockEntity) h.getBlockEntity(WATER_POS)).tank.getFluidAmount();
    }

    private static void setWater(GameTestHelper h, boolean bench, int amount) {
        if (bench) {
            PreparationBlockEntity be = (PreparationBlockEntity) h.getBlockEntity(WATER_POS);
            be.water = amount;
            be.setChanged();
        } else {
            ((CampWaterBlockEntity) h.getBlockEntity(WATER_POS)).tank.setFluid(
                    amount == 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, amount));
        }
    }

    private static void checkBuckets(GameTestHelper h, boolean bench) {
        h.setBlock(WATER_POS, bench ? StarfallBlueprint.PREPARATION.get() : StarfallBlueprint.WATER_TANK.get());
        FakePlayer player = survivalPlayer(h);
        try {
            h.assertTrue(!player.isCreative(), "Bucket interactions must run in survival");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            useBucket(h, WATER_POS, player);
            h.assertTrue(water(h, bench) == 1000 && player.getMainHandItem().is(Items.BUCKET)
                    && count(player.getInventory(), Items.WATER_BUCKET) == 0,
                    "Filling consumes one water bucket and returns one empty bucket");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET, 3));
            if (bench) {
                useBucket(h, WATER_POS, player);
                h.assertTrue(water(h, true) == 1000 && player.getMainHandItem().getCount() == 3,
                        "The bench must require sneaking to draw water");
            }
            player.setShiftKeyDown(bench);
            useBucket(h, WATER_POS, player);
            h.assertTrue(water(h, bench) == 0 && player.getMainHandItem().is(Items.BUCKET)
                    && player.getMainHandItem().getCount() == 2
                    && count(player.getInventory(), Items.BUCKET) == 2
                    && count(player.getInventory(), Items.WATER_BUCKET) == 1,
                    "Drawing from three stacked empty buckets preserves two empties and adds exactly one water bucket");
            useBucket(h, WATER_POS, player);
            h.assertTrue(water(h, bench) == 0 && count(player.getInventory(), Items.BUCKET) == 2
                    && count(player.getInventory(), Items.WATER_BUCKET) == 1,
                    "An empty reservoir cannot consume another bucket or duplicate water");

            cleanPlayer(player);
            setWater(h, bench, 3100);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            h.assertTrue(useBucket(h, WATER_POS, player).consumesAction(),
                    "Rejected main-hand fill is consumed by the default block interaction, preventing bucket fallback");
            h.assertTrue(water(h, bench) == 3100 && count(player.getInventory(), Items.WATER_BUCKET) == 1
                    && count(player.getInventory(), Items.BUCKET) == 0,
                    "Only 900mB free: reject the whole bucket without consuming it or partially filling");
            setWater(h, bench, 3000);
            useBucket(h, WATER_POS, player);
            h.assertTrue(water(h, bench) == 4000 && count(player.getInventory(), Items.BUCKET) == 1
                    && count(player.getInventory(), Items.WATER_BUCKET) == 0,
                    "Exactly 1000mB free accepts one complete bucket");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            h.assertTrue(useBucket(h, WATER_POS, player).consumesAction(),
                    "The full reservoir consumes main-hand interaction without falling through to the water bucket");
            h.assertTrue(water(h, bench) == 4000 && player.getMainHandItem().is(Items.WATER_BUCKET),
                    "A full reservoir rejects a further bucket");

            cleanPlayer(player);
            setWater(h, bench, 999);
            player.setShiftKeyDown(bench);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET, 3));
            useBucket(h, WATER_POS, player);
            h.assertTrue(water(h, bench) == 999 && count(player.getInventory(), Items.BUCKET) == 3
                    && count(player.getInventory(), Items.WATER_BUCKET) == 0,
                    "999mB cannot become a full water bucket or consume an empty bucket");
        } finally {
            cleanPlayer(player);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalBucketsAtPreparationBench(GameTestHelper h) {
        checkBuckets(h, true);
    }

    @GameTest(template = "empty")
    public static void survivalBucketsAtWaterTank(GameTestHelper h) {
        checkBuckets(h, false);
    }

    @GameTest(template = "empty")
    public static void survivalBucketOffhandAndSneakRouting(GameTestHelper h) {
        FakePlayer player = survivalPlayer(h);
        try {
            for (boolean bench : new boolean[]{true, false}) {
                h.setBlock(WATER_POS, bench ? StarfallBlueprint.PREPARATION.get() : StarfallBlueprint.WATER_TANK.get());
                cleanPlayer(player);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 7));
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WATER_BUCKET));
                h.assertTrue(useBucket(h, WATER_POS, player, InteractionHand.OFF_HAND).consumesAction()
                        && water(h, bench) == 1000 && player.getOffhandItem().is(Items.BUCKET),
                        "A non-sneaking offhand water bucket fills the targeted reservoir");
                h.assertTrue(player.getMainHandItem().is(Items.STICK) && player.getMainHandItem().getCount() == 7,
                        "Offhand filling must preserve the main-hand item");

                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BUCKET, 3));
                useBucket(h, WATER_POS, player, InteractionHand.OFF_HAND);
                h.assertTrue(water(h, bench) == (bench ? 1000 : 0)
                        && player.getOffhandItem().getCount() == (bench ? 3 : 2),
                        "Offhand drawing requires sneaking at the bench, but ordinary tanks draw without sneaking");

                cleanPlayer(player);
                setWater(h, bench, 1000);
                player.setShiftKeyDown(true);
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BUCKET, 3));
                useBucket(h, WATER_POS, player, InteractionHand.OFF_HAND);
                h.assertTrue(water(h, bench) == (bench ? 0 : 1000)
                        && player.getOffhandItem().is(Items.BUCKET)
                        && player.getOffhandItem().getCount() == (bench ? 2 : 3)
                        && count(player.getInventory(), Items.WATER_BUCKET) == (bench ? 1 : 0),
                        "With an empty main hand, sneaking enables bench extraction only; tank sneak bypass is unchanged");

                cleanPlayer(player);
                setWater(h, bench, 1000);
                player.setShiftKeyDown(true);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 7));
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BUCKET, 3));
                useBucket(h, WATER_POS, player, InteractionHand.OFF_HAND);
                h.assertTrue(water(h, bench) == (bench ? 0 : 1000)
                        && player.getMainHandItem().is(Items.STICK) && player.getMainHandItem().getCount() == 7
                        && player.getOffhandItem().getCount() == (bench ? 2 : 3)
                        && count(player.getInventory(), Items.WATER_BUCKET) == (bench ? 1 : 0),
                        "Sneaking with an offhand empty bucket preserves the occupied main hand and bucket counts");

                cleanPlayer(player);
                setWater(h, bench, 0);
                player.setShiftKeyDown(true);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
                h.assertTrue(!useBucket(h, WATER_POS, player).consumesAction() && water(h, bench) == 0
                        && player.getMainHandItem().is(Items.WATER_BUCKET),
                        "Sneaking with a filled bucket keeps vanilla block bypass; the exception applies only to empty bench buckets");
            }
        } finally {
            cleanPlayer(player);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryTankActualDropRetainsWaterWhenPlaced(GameTestHelper h) {
        h.setBlock(WATER_POS.below(), Blocks.STONE);
        h.setBlock(WATER_POS, StarfallBlueprint.WATER_TANK.get());
        setWater(h, false, 2500);
        FakePlayer player = survivalPlayer(h);
        try {
            BlockPos absolute = h.absolutePos(WATER_POS);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            h.assertTrue(h.getLevel().destroyBlock(absolute, true, player), "Break the actual tank with drops enabled");
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(0.75),
                    entity -> entity.getItem().is(StarfallBlueprint.TANK_ITEM.get()));
            h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1,
                    "Breaking the ordinary tank spawns exactly one real tank item");
            ItemStack dropped = drops.getFirst().getItem().copy();
            drops.getFirst().discard();
            player.setItemInHand(InteractionHand.MAIN_HAND, dropped);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute.below()), Direction.UP,
                    absolute.below(), false);
            ((BlockItem) dropped.getItem()).place(new BlockPlaceContext(h.getLevel(), player,
                    InteractionHand.MAIN_HAND, dropped, hit));
            h.assertTrue(h.getLevel().getBlockState(absolute).is(StarfallBlueprint.WATER_TANK.get()),
                    "The actual dropped item can be placed as an ordinary tank");
            h.assertTrue(water(h, false) == 2500 && player.getMainHandItem().isEmpty(),
                    "Survival placement consumes the tank item and restores all 2500mB, including the partial bucket");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            useBucket(h, WATER_POS, player);
            h.assertTrue(water(h, false) == 1500 && player.getMainHandItem().is(Items.WATER_BUCKET),
                    "Restored water is usable through the real bucket interaction");
        } finally {
            cleanPlayer(player);
        }
        h.succeed();
    }

    private static ItemStack ore(int count) {
        ItemStack stack = new ItemStack(StarfallBlueprint.RAW.get(), count);
        stack.set(StarfallBlueprint.BATCH.get(), MaterialBatch.ore(12, 40));
        return stack;
    }

    private static void assertFinishedAutomation(GameTestHelper h, PreparationBlockEntity bench,
            HopperBlockEntity top, HopperBlockEntity side, HopperBlockEntity bottom, Container chest) {
        h.assertTrue(count(chest, StarfallBlueprint.CRYSTAL.get()) == 20
                && count(chest, StarfallBlueprint.TAILINGS.get()) == 2,
                "Bottom hopper must deliver both batches: 20 crystals and two tailings parcels");
        h.assertTrue(bench.water == 500 && bench.energy == 240000 && bench.progress == 0 && bench.isEmpty(),
                "Two real 200-tick batches consume two ore, 500mB and 80000XH from one coal");
        h.assertTrue(count(top, StarfallBlueprint.RAW.get()) == 0 && count(top, Items.COAL) == 1
                && count(side, Items.COAL) == 0 && count(side, StarfallBlueprint.RAW.get()) == 1
                && bottom.isEmpty(), "Top accepts ore, side accepts coal, and wrong-port items stay in their source hoppers");
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty()) continue;
            h.assertTrue(stack.is(StarfallBlueprint.CRYSTAL.get()) || stack.is(StarfallBlueprint.TAILINGS.get()),
                    "Only products may leave through the bottom");
            MaterialBatch expected = stack.is(StarfallBlueprint.CRYSTAL.get())
                    ? MaterialBatch.crystal(38) : MaterialBatch.tailings(12);
            h.assertTrue(expected.equals(stack.get(StarfallBlueprint.BATCH.get())),
                    "Hopper transport preserves product quality and mass components");
        }
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void vanillaHoppersDriveTwoContinuousBatches(GameTestHelper h) {
        // Keep all equipment inside the empty 5x5x5 template, including the top hopper.
        BlockPos machinePos = new BlockPos(1, 2, 1);
        BlockPos topPos = machinePos.above();
        BlockPos sidePos = machinePos.east();
        BlockPos bottomPos = machinePos.below();
        BlockPos chestPos = bottomPos.north();
        h.setBlock(chestPos, Blocks.CHEST);
        h.setBlock(bottomPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
        h.setBlock(machinePos, StarfallBlueprint.PREPARATION.get());
        h.setBlock(topPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        h.setBlock(sidePos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
        PreparationBlockEntity bench = (PreparationBlockEntity) h.getBlockEntity(machinePos);
        HopperBlockEntity top = (HopperBlockEntity) h.getBlockEntity(topPos);
        HopperBlockEntity side = (HopperBlockEntity) h.getBlockEntity(sidePos);
        HopperBlockEntity bottom = (HopperBlockEntity) h.getBlockEntity(bottomPos);
        Container chest = (Container) h.getBlockEntity(chestPos);
        bench.water = 1000;
        bench.setChanged();
        top.setItem(0, ore(2));
        top.setItem(1, new ItemStack(Items.COAL));
        side.setItem(0, new ItemStack(Items.COAL));
        side.setItem(1, ore(1));
        // No direct machine inventory insertion, extraction, or manual block-entity tick calls.
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(count(chest, StarfallBlueprint.CRYSTAL.get()) >= 10,
                        "The first batch must reach the chest through a ticking vanilla hopper"))
                .thenExecute(() -> h.assertTrue(bench.water == 500 && bench.progress > 0
                        && bench.energy < 280000 && bench.energy > 240000,
                        "The second batch starts automatically while the first batch is being extracted"))
                .thenWaitUntil(() -> h.assertTrue(count(chest, StarfallBlueprint.CRYSTAL.get()) == 20
                        && count(chest, StarfallBlueprint.TAILINGS.get()) == 2,
                        "Wait for real production and hopper cooldowns to deliver the complete second batch"))
                .thenExecute(() -> assertFinishedAutomation(h, bench, top, side, bottom, chest))
                .thenIdle(40)
                .thenExecute(() -> assertFinishedAutomation(h, bench, top, side, bottom, chest))
                .thenSucceed();
    }
}
