package cn.starfall.blueprint.test;

import cn.starfall.blueprint.MaterialBatch;
import cn.starfall.blueprint.PreparationBlockEntity;
import cn.starfall.blueprint.StarfallBlueprint;
import cn.starfall.blueprint.SupplyLedger;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Explicit opt-in machine and shared-trade persistence probe for a disposable world across two JVM launches.
 * Prepare uses an ordinary G12 I30 raw-ore fixture, not player resource delivery.
 * Verify never installs or deserializes a replacement fixture: every assertion reads disk-loaded state.
 */
public final class RestartVerification {
    private static final UUID PLAYER = UUID.fromString("7bca1e68-e28c-40b4-91ef-197529a12504");
    private static final BlockPos POS = new BlockPos(0, 90, 0);
    private static MinecraftServer pendingServer;
    private static boolean executed;

    private RestartVerification() { }

    public static void register(IEventBus gameBus) {
        String mode = System.getProperty("starfall.verifyRestart", "");
        if (!mode.equals("prepare") && !mode.equals("verify")) return;
        gameBus.addListener(RestartVerification::started);
        gameBus.addListener(RestartVerification::tick);
    }

    private static void started(ServerStartedEvent event) {
        pendingServer = event.getServer();
        executed = false;
    }

    private static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (executed || pendingServer != server) return;
        executed = true;
        String mode = System.getProperty("starfall.verifyRestart", "");
        try {
            if (mode.equals("prepare")) prepare(server);
            else if (mode.equals("verify")) verify(server);
            else throw new IllegalStateException("Restart verification mode changed after registration");
            // Flush machine chunk and SavedData through the normal server save path before reporting success.
            // halt(false) requests the ordinary server-loop shutdown, which also performs its final save.
            server.saveEverything(true, true, true);
            System.out.println(mode.equals("prepare") ? "STARFALL_RESTART_PREPARED" : "STARFALL_RESTART_VERIFIED");
            server.halt(false);
        } catch (RuntimeException | Error failure) {
            System.out.println("STARFALL_RESTART_FAILED " + mode + " " + failure);
            failure.printStackTrace(System.out);
            server.halt(false);
            throw failure;
        }
    }

    private static FakePlayer fixturePlayer(ServerLevel level) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "RestartProbe"));
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(false);
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.setPos(POS.getX() + 0.5, POS.getY(), POS.getZ() + 1.5);
        return player;
    }

    private static ItemStack sample(int count) {
        ItemStack stack = new ItemStack(StarfallBlueprint.CRYSTAL.get(), count);
        stack.set(StarfallBlueprint.BATCH.get(), MaterialBatch.crystal(28));
        return stack;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static PreparationBlockEntity machine(ServerLevel level) {
        level.getChunkAt(POS);
        check(level.getBlockEntity(POS) instanceof PreparationBlockEntity, "Expected persisted preparation bench at fixture position");
        return (PreparationBlockEntity) level.getBlockEntity(POS);
    }

    private static void ticks(ServerLevel level, PreparationBlockEntity bench, int count) {
        for (int i = 0; i < count; i++) {
            PreparationBlockEntity.tick(level, POS, level.getBlockState(POS), bench);
        }
    }

    private static void checkPaused(ServerLevel level, PreparationBlockEntity bench) {
        check(bench.progress == 73 && bench.energy == 306_860L && bench.water == 750,
                "Paused batch must preserve progress73, energy306860 and water750");
        check(bench.getItem(0).isEmpty() && bench.getItem(1).isEmpty()
                        && bench.getItem(2).is(Items.STONE) && bench.getItem(2).getCount() == 64
                        && bench.getItem(3).isEmpty() && bench.getItem(4).is(StarfallBlueprint.CONNECTOR.get())
                        && bench.getItem(4).getCount() == 1,
                "Paused machine must retain blocker/connector and consumed input exactly");
        CompoundTag tag = bench.saveWithoutMetadata(level.registryAccess());
        ItemStack crystal = ItemStack.parseOptional(level.registryAccess(), tag.getCompound("PendingCrystal"));
        ItemStack tailings = ItemStack.parseOptional(level.registryAccess(), tag.getCompound("PendingTailings"));
        check(tag.getInt("BatchPower") == 180 && crystal.is(StarfallBlueprint.CRYSTAL.get())
                        && crystal.getCount() == 10 && MaterialBatch.crystal(28).equals(crystal.get(StarfallBlueprint.BATCH.get()))
                        && tailings.is(StarfallBlueprint.TAILINGS.get()) && tailings.getCount() == 1
                        && MaterialBatch.tailings(12).equals(tailings.get(StarfallBlueprint.BATCH.get())),
                "Pending outputs and locked batch budget must persist, including exact composition");
    }

    private static void prepare(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.setDayTime(6_000L);
        SupplyLedger ledger = new SupplyLedger();
        level.getDataStorage().set(SupplyLedger.DATA_NAME, ledger);
        ledger.setDirty();

        level.getChunkAt(POS);
        level.setBlockAndUpdate(POS, StarfallBlueprint.PREPARATION.get().defaultBlockState());
        PreparationBlockEntity bench = machine(level);
        bench.water = 1000;
        bench.energy = 320_000L;
        ItemStack ore = new ItemStack(StarfallBlueprint.RAW.get());
        ore.set(StarfallBlueprint.BATCH.get(), MaterialBatch.ore(12, 30));
        bench.setItem(0, ore);
        bench.setItem(4, new ItemStack(StarfallBlueprint.CONNECTOR.get()));
        FakePlayer actor = fixturePlayer(level);
        try {
            ticks(level, bench, 73);
            bench.setItem(2, new ItemStack(Items.STONE, 64));
            ticks(level, bench, 1);
            checkPaused(level, bench);
            actor.setItemInHand(InteractionHand.MAIN_HAND, sample(3));
            for (int i = 0; i < 3; i++) {
                check(ledger.exchange(actor, InteractionHand.MAIN_HAND) == SupplyLedger.Result.SUCCESS,
                        "Prepare must settle exactly three real exchanges");
            }
            check(ledger.remaining(level) == 1, "Prepare leaves exactly one shared trade");
            bench.setChanged();
            System.out.println("STARFALL_RESTART_STATE prepare progress=73 energy=306860 water=750 pendingCrystals=10 integrity=28 tradesRemaining=1");
        } finally {
            actor.getInventory().clearContent();
        }
    }

    private static void verify(MinecraftServer server) {
        ServerLevel level = server.overworld();
        check(level.getDayTime() == 6_000L && !level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT),
                "World date and disabled daylight cycle must survive the process restart");
        PreparationBlockEntity bench = machine(level);
        checkPaused(level, bench);
        FakePlayer actor = fixturePlayer(level);
        try {
            SupplyLedger ledger = SupplyLedger.get(level);
            check(ledger.remaining(level) == 1, "Loaded world account must still have only one trade");
            actor.setItemInHand(InteractionHand.MAIN_HAND, sample(2));
            check(ledger.exchange(actor, InteractionHand.MAIN_HAND) == SupplyLedger.Result.SUCCESS
                            && ledger.remaining(level) == 0,
                    "The one persisted remaining trade succeeds once");
            check(ledger.exchange(actor, InteractionHand.MAIN_HAND) == SupplyLedger.Result.SOLD_OUT
                            && actor.getMainHandItem().getCount() == 1,
                    "A further exchange is rejected without consuming its sample");
            bench.setItem(2, ItemStack.EMPTY);
            ticks(level, bench, 127);
            check(bench.progress == 0 && bench.energy == 284_000L && bench.water == 750,
                    "Resume consumes only the remaining127 ticks, totaling36000 XH and250mB");
            check(bench.getItem(2).is(StarfallBlueprint.CRYSTAL.get()) && bench.getItem(2).getCount() == 10
                            && MaterialBatch.crystal(28).equals(bench.getItem(2).get(StarfallBlueprint.BATCH.get()))
                            && bench.getItem(3).is(StarfallBlueprint.TAILINGS.get()) && bench.getItem(3).getCount() == 1
                            && MaterialBatch.tailings(12).equals(bench.getItem(3).get(StarfallBlueprint.BATCH.get())),
                    "Resumed batch delivers ten I28 crystals and exactly one composition-correct tailings");
            ticks(level, bench, 20);
            check(bench.progress == 0 && bench.energy == 284_000L && bench.water == 750
                            && bench.getItem(2).getCount() == 10 && bench.getItem(3).getCount() == 1,
                    "Idle ticks cannot duplicate outputs after restart completion");
            System.out.println("STARFALL_RESTART_STATE verify progress=0 energy=284000 water=750 crystals=10 integrity=28 tailings=1 tradesRemaining=0");
        } finally {
            actor.getInventory().clearContent();
        }
    }
}
