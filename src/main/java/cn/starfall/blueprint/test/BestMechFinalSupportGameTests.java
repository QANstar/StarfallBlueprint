package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("starfall_blueprint_mech_support")
@PrefixGameTestTemplate(false)
public final class BestMechFinalSupportGameTests {
    @GameTest(template = "combat_arena")
    public static void changchuanSupportsFriendsWithoutSupplies(GameTestHelper h) {
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "FinalSupportPilot");
        var p = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, net.minecraft.server.level.ClientInformation.createDefault());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        var knowledge = new CompoundTag(); knowledge.putBoolean("confluence", true); p.getPersistentData().put("BestModuleKnowledge", knowledge);
        var mech = BestMechs.UNIVERSAL_TYPE.get().create(h.getLevel());
        var state = new CompoundTag(); state.putInt("Energy", 20000); state.putInt("UpgradeMask", BestMechProgression.CHANGCHUAN); mech.loadState(state);
        var pos = h.absolutePos(new BlockPos(4, 1, 2));
        mech.moveTo(pos.getX()+.5, pos.getY(), pos.getZ()+.5, 0, 0); h.getLevel().addFreshEntity(mech);
        p.moveTo(mech.getX(), mech.getY(), mech.getZ(), 0, 0); p.startRiding(mech, true);
        h.assertTrue(mech.qualifiedFor(p) && mech.hasUpgrade(BestMechProgression.CHANGCHUAN), "qualified pilot and final component required");
        var pet = EntityType.WOLF.create(h.getLevel()); pet.setTame(true, true); pet.setOwnerUUID(p.getUUID());
        pet.moveTo(mech.getX()+3, mech.getY(), mech.getZ(), 0, 0); h.getLevel().addFreshEntity(pet);
        var neutral = EntityType.COW.create(h.getLevel()); neutral.moveTo(mech.getX()+2, mech.getY(), mech.getZ()+1, 0, 0); h.getLevel().addFreshEntity(neutral);
        h.assertTrue(BestMechSupport.isChangchuanEnabled(mech), "legacy default is enabled");
        mech.tickCount = 40; BestMechSupport.tick(mech);
        h.assertTrue(mech.cargo().isEmpty() && mech.energy()==17000 && mech.heat()==0 && mech.wear()==0 && pet.hasEffect(MobEffects.DAMAGE_BOOST)
                && pet.hasEffect(MobEffects.MOVEMENT_SPEED) && pet.hasEffect(MobEffects.REGENERATION)
                && pet.hasEffect(MobEffects.DAMAGE_RESISTANCE) && pet.hasEffect(MobEffects.DIG_SPEED)
                && pet.getEffect(MobEffects.REGENERATION).getDuration()==BestMechSupport.CHANGCHUAN_DURATION
                && !neutral.hasEffect(MobEffects.REGENERATION), "one energy-only pulse buffs healthy allies, not neutral animals");
        pet.removeAllEffects();
        BestMechSupport.setChangchuanEnabled(mech, false);
        var saved = new CompoundTag(); BestMechSupport.writeChangchuanState(mech, saved);
        BestMechSupport.setChangchuanEnabled(mech, true); BestMechSupport.readChangchuanState(mech, saved);
        mech.tickCount=80; BestMechSupport.tick(mech);
        h.assertTrue(!BestMechSupport.isChangchuanEnabled(mech) && !pet.hasEffect(MobEffects.REGENERATION) && mech.energy()==17000,
                "disabled state round trips and blocks pulse and cost");
        BestMechSupport.readChangchuanState(mech, new CompoundTag());
        h.assertTrue(BestMechSupport.isChangchuanEnabled(mech), "legacy state defaults enabled");
        state = mech.stateTag(); state.putInt("Energy", BestMechSupport.CHANGCHUAN_COST-1); mech.loadState(state);
        mech.tickCount=120; BestMechSupport.tick(mech);
        h.assertTrue(!pet.hasEffect(MobEffects.REGENERATION) && mech.energy()==BestMechSupport.CHANGCHUAN_COST-1,
                "insufficient energy grants no effects and spends nothing");
        state = mech.stateTag(); state.putInt("Energy", 20000); mech.loadState(state);
        pet.moveTo(mech.getX()+30, mech.getY(), mech.getZ(), 0, 0);
        mech.tickCount=160; BestMechSupport.tick(mech);
        h.assertTrue(!pet.hasEffect(MobEffects.REGENERATION) && mech.energy()==17000, "out-of-range ally gets no effect");
        pet.moveTo(mech.getX()+3, mech.getY(), mech.getZ(), 0, 0);
        for (int y=0; y<4; y++) for (int z=-1; z<=1; z++)
            h.getLevel().setBlockAndUpdate(pos.offset(1,y,z), Blocks.STONE.defaultBlockState());
        mech.tickCount=200; BestMechSupport.tick(mech);
        h.assertTrue(!pet.hasEffect(MobEffects.REGENERATION) && mech.energy()==14000, "wall blocks support line of sight");
        for (int y=0; y<4; y++) for (int z=-1; z<=1; z++)
            h.getLevel().setBlockAndUpdate(pos.offset(1,y,z), Blocks.AIR.defaultBlockState());
        mech.setIsolated(true); mech.tickCount=240; BestMechSupport.tick(mech);
        h.assertTrue(!pet.hasEffect(MobEffects.REGENERATION) && mech.energy()==14000, "shutdown blocks support");
        mech.setIsolated(false); p.stopRiding(); mech.tickCount=280; BestMechSupport.tick(mech);
        h.assertTrue(!pet.hasEffect(MobEffects.REGENERATION) && mech.energy()==14000, "unpiloted support is inactive");
        p.startRiding(mech, true); knowledge.putBoolean("confluence", false);
        mech.tickCount=320; BestMechSupport.tick(mech);
        h.assertTrue(!pet.hasEffect(MobEffects.REGENERATION) && mech.energy()==14000, "unqualified pilot cannot trigger support");
        knowledge.putBoolean("confluence", true);
        state = mech.stateTag(); state.putInt("Energy", BestMechSupport.CHANGCHUAN_COST); mech.loadState(state);
        mech.tickCount=360; BestMechSupport.tick(mech);
        h.assertTrue(pet.hasEffect(MobEffects.REGENERATION) && mech.energy()==0, "exact energy pays for one pulse");
        pet.discard(); neutral.discard(); mech.discard(); h.succeed();
    }
}
