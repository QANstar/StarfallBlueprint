package cn.starfall.blueprint.test;

import cn.starfall.blueprint.BestMechEntity;
import cn.starfall.blueprint.BestMechLifting;
import cn.starfall.blueprint.BestMechs;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("starfall_blueprint_mech_combat")
@PrefixGameTestTemplate(false)
public final class BestMechCarryGameTests {
    private record Fixture(BestMechEntity mech, ServerPlayer pilot, Zombie target) {}

    private static Fixture fixture(GameTestHelper h) {
        var profile = new GameProfile(UUID.randomUUID(), "LiftTest");
        var pilot = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        pilot.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, pilot,
                CommonListenerCookie.createInitial(profile, false));
        var knowledge = new CompoundTag();
        knowledge.putBoolean("combat_operation", true);
        pilot.getPersistentData().put("BestModuleKnowledge", knowledge);
        var mech = BestMechs.COMBAT_TYPE.get().create(h.getLevel());
        var state = new CompoundTag();
        state.putInt("Energy", 100000);
        state.putFloat("Health", 100);
        mech.loadState(state);
        var pos = h.absolutePos(new BlockPos(3, 1, 3));
        mech.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        h.getLevel().addFreshEntity(mech);
        pilot.moveTo(mech.getX(), mech.getY(), mech.getZ(), 0, 0);
        h.assertTrue(pilot.startRiding(mech, true), "Pilot mounts the mech");
        pilot.moveTo(mech.getX(), mech.getY(), mech.getZ(), 0, 0);
        pilot.setYRot(0);
        pilot.setXRot(0);
        var target = EntityType.ZOMBIE.create(h.getLevel());
        target.moveTo(mech.getX(), mech.getY(), mech.getZ() + 3, 0, 0);
        h.getLevel().addFreshEntity(target);
        return new Fixture(mech, pilot, target);
    }

    @GameTest(template = "combat_arena")
    public static void livingTargetFollowsUntilToggledOff(GameTestHelper h) {
        var f = fixture(h);
        var initial = f.target.position();
        BestMechLifting.toggle(f.mech, f.pilot);
        h.assertTrue(f.target.position().distanceToSqr(initial) > .1, "Aimed living target is picked up");
        var held = f.target.position();
        f.mech.setPos(f.mech.getX() + .5, f.mech.getY(), f.mech.getZ());
        BestMechLifting.tick(f.mech);
        h.assertTrue(f.target.position().distanceToSqr(held) > .1, "Held target follows the mech");
        BestMechLifting.toggle(f.mech, f.pilot);
        var released = f.target.position();
        f.mech.setPos(f.mech.getX() + .5, f.mech.getY(), f.mech.getZ());
        BestMechLifting.tick(f.mech);
        h.assertTrue(f.target.position().distanceToSqr(released) < .0001, "Toggling again releases the target");
        f.pilot.stopRiding(); f.target.discard(); f.mech.discard();
        h.succeed();
    }

    @GameTest(template = "combat_arena")
    public static void losingPilotReleasesTarget(GameTestHelper h) {
        var f = fixture(h);
        var initial = f.target.position();
        BestMechLifting.toggle(f.mech, f.pilot);
        h.assertTrue(f.target.position().distanceToSqr(initial) > .1, "Target was held before pilot leaves");
        f.pilot.stopRiding();
        BestMechLifting.tick(f.mech);
        var released = f.target.position();
        h.assertTrue(f.pilot.startRiding(f.mech, true), "Pilot can remount");
        f.mech.setPos(f.mech.getX() + .5, f.mech.getY(), f.mech.getZ());
        BestMechLifting.tick(f.mech);
        h.assertTrue(f.target.position().distanceToSqr(released) < .0001, "Remounting does not restore the released lift");
        f.pilot.stopRiding(); f.target.discard(); f.mech.discard();
        h.succeed();
    }

    @GameTest(template = "combat_arena")
    public static void playerCanWithdrawSneakingConsent(GameTestHelper h) {
        var f = fixture(h);
        f.target.discard();
        var profile = new GameProfile(UUID.randomUUID(), "LiftPassenger");
        var carried = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        carried.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, carried,
                CommonListenerCookie.createInitial(profile, false));
        carried.moveTo(f.mech.getX(), f.mech.getY(), f.mech.getZ() + 3, 0, 0);
        h.getLevel().addNewPlayer(carried);
        // The lift checks the block beneath the destination for solid support.
        h.setBlock(new BlockPos(3, 0, 5), net.minecraft.world.level.block.Blocks.STONE);
        h.setBlock(new BlockPos(4, 0, 5), net.minecraft.world.level.block.Blocks.STONE);
        var initial = carried.position();
        BestMechLifting.toggle(f.mech, f.pilot);
        h.assertTrue(carried.position().distanceToSqr(initial) < .0001, "A standing player cannot be picked up");
        carried.setShiftKeyDown(true);
        BestMechLifting.toggle(f.mech, f.pilot);
        h.assertTrue(carried.position().distanceToSqr(initial) > .1, "Sneaking player consents to pickup");
        carried.setShiftKeyDown(false);
        BestMechLifting.tick(f.mech);
        var released = carried.position();
        carried.setShiftKeyDown(true);
        f.mech.setPos(f.mech.getX() + .5, f.mech.getY(), f.mech.getZ());
        BestMechLifting.tick(f.mech);
        h.assertTrue(carried.position().distanceToSqr(released) < .0001,
                "Releasing sneak drops the player and sneaking again does not restore the lift");
        carried.discard(); f.pilot.stopRiding(); f.mech.discard();
        h.succeed();
    }

    private BestMechCarryGameTests() {}
}
