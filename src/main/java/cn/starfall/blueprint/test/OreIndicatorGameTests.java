package cn.starfall.blueprint.test;

import cn.starfall.blueprint.OreIndicatorItem;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.authlib.GameProfile;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class OreIndicatorGameTests {
    // A separate batch and a high, otherwise untouched scan volume isolate this from template ores.
    private static final String BATCH = "ore_indicator";

    private static final class RecordingPlayer extends FakePlayer {
        Component message;
        boolean actionBar;
        int messages;

        RecordingPlayer(ServerLevel level, BlockPos origin) {
            super(level, new GameProfile(UUID.randomUUID(), "IndicatorTest"));
            setGameMode(GameType.SURVIVAL);
            getInventory().clearContent();
            moveTo(Vec3.atCenterOf(origin));
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            this.message = message.copy();
            this.actionBar = actionBar;
            messages++;
        }
    }

    private static ItemStack indicator() {
        return new ItemStack(StarfallBlueprint.SCANNER.get());
    }

    private static InteractionResult use(RecordingPlayer player, InteractionHand hand) {
        return player.gameMode.useItem(player, player.level(), player.getItemInHand(hand), hand);
    }

    private static void assertMessage(GameTestHelper h, RecordingPlayer player, boolean found, int messages) {
        h.assertTrue(player.messages == messages && player.actionBar && player.message != null,
                "One scan must produce exactly one action-bar message");
        h.assertTrue(player.message.getContents() instanceof TranslatableContents,
                "Scan feedback must remain a translation, not literal coordinates or grade");
        TranslatableContents contents = (TranslatableContents) player.message.getContents();
        h.assertTrue(contents.getKey().equals("message.starfall_blueprint.scan." + (found ? "found" : "empty"))
                        && contents.getArgs().length == 0 && contents.getFallback() == null
                        && player.message.getSiblings().isEmpty()
                        && player.message.getStyle().isEmpty(),
                "Feedback contains only found/empty, with no coordinates, grade, arguments or hover data");
    }

    private static void expireCooldown(RecordingPlayer player) {
        // FakePlayer.tick is deliberately empty; advance its real cooldown tracker explicitly.
        for (int i = 0; i < 40; i++) player.getCooldowns().tick();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void scanPredicateUsesSphereAndNeverReadsUnloadedPositions(GameTestHelper h) {
        BlockPos origin = new BlockPos(-19, 80, 23);
        Set<BlockPos> reads = new HashSet<>();
        h.assertTrue(!OreIndicatorItem.hasNearbyOre(origin, pos -> true, pos -> {
            reads.add(pos.immutable());
            return false;
        }), "An empty exact-input sphere has no signal");
        Set<BlockPos> expected = new HashSet<>();
        for (int x = -9; x <= 9; x++) {
            for (int y = -9; y <= 9; y++) {
                for (int z = -9; z <= 9; z++) {
                    if (x * x + y * y + z * z <= 64) expected.add(origin.offset(x, y, z));
                }
            }
        }
        h.assertTrue(reads.equals(expected), "The scan visits exactly the inclusive radius-eight lattice sphere");
        h.assertTrue(OreIndicatorItem.hasNearbyOre(origin, pos -> true, origin.offset(8, 0, 0)::equals),
                "Ore exactly eight blocks away on X is included");
        h.assertTrue(!OreIndicatorItem.hasNearbyOre(origin, pos -> true, origin.offset(6, 6, 0)::equals),
                "The cube corner at (6,6,0) is outside the sphere");
        h.assertTrue(!OreIndicatorItem.hasNearbyOre(origin, pos -> pos.getX() <= origin.getX(), pos -> {
            h.assertTrue(pos.getX() <= origin.getX(), "Unavailable positions must never reach the block reader");
            return pos.equals(origin.offset(8, 0, 0));
        }), "An unloaded candidate cannot report ore");
        h.assertTrue(!OreIndicatorItem.hasNearbyOre(origin, pos -> false, pos -> {
            throw new AssertionError("A wholly unloaded sphere must not read a block");
        }), "No loaded positions means no signal");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void realServerUseChecksBothHandsMessagesAndFortyTickCooldown(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos template = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos origin = new BlockPos((template.getX() & ~15) + 7, template.getY() + 32,
                (template.getZ() & ~15) + 7);
        // These exact X/diagonal boundaries fit inside the already prepared origin chunk.
        BlockPos orePos = origin.east(8);
        BlockPos cornerPos = origin.offset(6, 6, 0);
        h.assertTrue(level.hasChunkAt(orePos) && level.hasChunkAt(cornerPos),
                "Both exact boundary fixtures stay in the prepared chunk");
        h.assertTrue(!OreIndicatorItem.hasNearbyOre(origin, level::hasChunkAt, pos -> {
            Block block = level.getBlockState(pos).getBlock();
            return block == StarfallBlueprint.ORE.get() || block == StarfallBlueprint.DEEP_ORE.get();
        }), "The elevated scan fixture must initially contain no mod ore");
        BlockState previous = level.getBlockState(orePos);
        BlockState previousCorner = level.getBlockState(cornerPos);
        h.assertTrue(!previous.hasBlockEntity() && !previousCorner.hasBlockEntity(),
                "Restorable ore fixtures must not replace a block entity");
        RecordingPlayer player = new RecordingPlayer(level, origin);
        ItemStack main = indicator();
        ItemStack off = indicator();
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        try {
            h.assertTrue(use(player, InteractionHand.MAIN_HAND).consumesAction(), "Real server main-hand scan succeeds");
            assertMessage(h, player, false, 1);
            h.assertTrue(player.getCooldowns().isOnCooldown(main.getItem()), "A scan starts the shared item cooldown");
            h.assertTrue(use(player, InteractionHand.OFF_HAND) == InteractionResult.PASS,
                    "Switching hands cannot bypass the normal server cooldown gate");
            h.assertTrue(main.getItem().use(level, player, InteractionHand.OFF_HAND).getResult() == InteractionResult.PASS,
                    "Direct item callers obey the same cooldown");
            for (int i = 0; i < 39; i++) {
                player.getCooldowns().tick();
                h.assertTrue(use(player, (i & 1) == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND)
                                == InteractionResult.PASS,
                        "Repeated attempts remain blocked through cooldown tick 39");
            }
            h.assertTrue(player.messages == 1, "Blocked uses emit no feedback and never reset the cooldown");
            player.getCooldowns().tick();
            h.assertTrue(!player.getCooldowns().isOnCooldown(main.getItem()), "Cooldown expires at exactly 40 ticks");
            level.setBlock(orePos, StarfallBlueprint.ORE.get().defaultBlockState(), 2);
            h.assertTrue(use(player, InteractionHand.OFF_HAND).consumesAction(), "Off-hand scan succeeds after two seconds");
            assertMessage(h, player, true, 2);
            expireCooldown(player);
            level.setBlock(orePos, StarfallBlueprint.DEEP_ORE.get().defaultBlockState(), 2);
            h.assertTrue(use(player, InteractionHand.MAIN_HAND).consumesAction(), "Normal and deepslate ore are detectable");
            assertMessage(h, player, true, 3);
            expireCooldown(player);
            level.setBlock(orePos, previous, 2);
            level.setBlock(cornerPos, StarfallBlueprint.ORE.get().defaultBlockState(), 2);
            h.assertTrue(use(player, InteractionHand.OFF_HAND).consumesAction(), "A diagonal candidate still permits a scan");
            assertMessage(h, player, false, 4);
            expireCooldown(player);
            level.setBlock(cornerPos, previousCorner, 2);
            // useItemOn is a different server route: Item.useOn defaults to PASS, then a client may send useItem.
            BlockPos hitPos = h.absolutePos(new BlockPos(2, 0, 2));
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(hitPos), Direction.UP, hitPos, false);
            h.assertTrue(player.gameMode.useItemOn(player, level, main, InteractionHand.MAIN_HAND, hit)
                            == InteractionResult.PASS && player.messages == 4
                            && !player.getCooldowns().isOnCooldown(main.getItem()),
                    "Noninteractive block use neither scans nor starts cooldown before the separate item-use route");
            h.assertTrue(use(player, InteractionHand.MAIN_HAND).consumesAction(), "Fallback item-use route scans once");
            assertMessage(h, player, false, 5);
            h.assertTrue(player.getMainHandItem() == main && player.getOffhandItem() == off
                            && main.getCount() == 1 && off.getCount() == 1
                            && main.getDamageValue() == 0 && off.getDamageValue() == 0,
                    "Successful, blocked and block-route uses neither consume, replace nor damage either indicator");
        } finally {
            level.setBlock(orePos, previous, 2);
            level.setBlock(cornerPos, previousCorner, 2);
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void realServerScanAtLoadedChunkEdgeDoesNotLoadNeighbor(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos template = h.absolutePos(new BlockPos(2, 2, 2));
        int chunkX = template.getX() >> 4;
        int chunkZ = template.getZ() >> 4;
        BlockPos origin = null;
        // Read-only search along the already prepared region; never generate or edit a distant fixture.
        for (int offset = -32; offset <= 32 && origin == null; offset++) {
            int x = chunkX + offset;
            BlockPos candidate = new BlockPos(x * 16 + 15, level.getMaxBuildHeight() - 16, chunkZ * 16 + 8);
            if (level.hasChunkAt(candidate) && !level.hasChunkAt(candidate.east())) origin = candidate;
        }
        h.assertTrue(origin != null, "Fixture requires a loaded-to-unloaded east edge within 32 chunks of the prepared row");
        BlockPos unloaded = origin.east();
        int loadedBefore = level.getChunkSource().getLoadedChunksCount();
        RecordingPlayer player = new RecordingPlayer(level, origin);
        player.setItemInHand(InteractionHand.MAIN_HAND, indicator());
        try {
            h.assertTrue(use(player, InteractionHand.MAIN_HAND).consumesAction(), "Real server scans across a prepared loaded edge");
            h.assertTrue(!level.hasChunkAt(unloaded)
                            && level.getChunkSource().getLoadedChunksCount() == loadedBefore,
                    "Scanning cannot load the neighboring unavailable chunk or increase loaded chunk count");
            // The scan result depends on preexisting world content; only its information contract is fixed.
            h.assertTrue(player.message != null && player.message.getContents() instanceof TranslatableContents,
                    "Boundary scan still uses translatable feedback");
            String key = ((TranslatableContents) player.message.getContents()).getKey();
            h.assertTrue(key.equals("message.starfall_blueprint.scan.found")
                            || key.equals("message.starfall_blueprint.scan.empty"), "Boundary result is only found or empty");
            assertMessage(h, player, key.endsWith(".found"), 1);
        } finally {
            player.getInventory().clearContent();
        }
        h.succeed();
    }
}
