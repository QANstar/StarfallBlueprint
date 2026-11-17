package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("starfall_blueprint_platform")
@PrefixGameTestTemplate(false)
public final class MechMaintenancePlatformGameTests {
    private static MechMaintenancePlatformBlockEntity platform(GameTestHelper h) {
        BlockPos center=h.absolutePos(new BlockPos(3,2,3));
        UUID owner=UUID.randomUUID();
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
            BlockPos p=center.offset(x,0,z);
            h.getLevel().setBlock(p,MechMaintenancePlatforms.BLOCK.get().defaultBlockState(),3);
            ((MechMaintenancePlatformBlockEntity)h.getLevel().getBlockEntity(p)).configure(center,owner);
        }
        return (MechMaintenancePlatformBlockEntity)h.getLevel().getBlockEntity(center);
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void missingTileStopsRealEnergyOutput(GameTestHelper h) {
        var be=platform(h);
        be.energyPort().receiveEnergy(50000,false);
        h.assertTrue(be.complete(),"Nine linked tiles form a complete platform");
        h.assertTrue(be.extractWirelessEnergy(3000,false)==3000 && be.storedEnergy()==47000,"Output consumes actual stored energy");
        h.getLevel().setBlock(be.getBlockPos().offset(1,0,1),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!be.complete() && be.extractWirelessEnergy(3000,false)==0 && be.storedEnergy()==47000,"Missing corner immediately stops output");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void fullFootprintAndAliveRequired(GameTestHelper h) {
        var be=platform(h);
        var mech=BestMechs.UNIVERSAL_TYPE.get().create(h.getLevel());
        BlockPos p=be.getBlockPos();
        mech.moveTo(p.getX()+.5,p.getY()+1,p.getZ()+.5,0,0);
        mech.setOnGround(true);
        h.getLevel().addFreshEntity(mech);
        h.assertTrue(be.containsMech(mech),"2.6-wide universal mech fits centered on three tiles");
        mech.setPos(p.getX()+1,p.getY()+1,p.getZ()+.5);
        h.assertTrue(!be.containsMech(mech),"A foot over the edge rejects maintenance");
        mech.setPos(p.getX()+.5,p.getY()+1,p.getZ()+.5);
        mech.setHealth(0);
        h.assertTrue(!be.containsMech(mech),"Destroyed chassis cannot receive maintenance");
        mech.discard();
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void parkedMechReceivesPaidRepairAndCharge(GameTestHelper h) {
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PlatformPilot");
        var owner=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        owner.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,owner,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        h.getLevel().addNewPlayer(owner);
        var knowledge=new net.minecraft.nbt.CompoundTag(); knowledge.putBoolean("confluence",true); owner.getPersistentData().put("BestModuleKnowledge",knowledge);
        var be=platform(h); var center=be.getBlockPos();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) ((MechMaintenancePlatformBlockEntity)h.getLevel().getBlockEntity(center.offset(x,0,z))).configure(center,owner.getUUID());
        be.energyPort().receiveEnergy(100000,false); be.interact(owner,CatalogueContent.stack("steel",2));
        var mech=BestMechs.UNIVERSAL_TYPE.get().create(h.getLevel()); var state=mech.stateTag(); state.putFloat("Health",100);state.putInt("Wear",400);state.putBoolean("Isolated",true); state.putUUID("BestSupportPilot",owner.getUUID());mech.loadState(state);
        mech.moveTo(center.getX()+.5,center.getY()+1,center.getZ()+.5,0,0);mech.setOnGround(true);h.getLevel().addFreshEntity(mech);
        h.runAfterDelay(22-h.getLevel().getGameTime()%20,()->{
            h.assertTrue(mech.energy()>0 && mech.getHealth()==105 && mech.wear()==200 && be.storedSteel()==1,"Parked isolated mech receives real paid charge and one repair: energy="+mech.energy()+" hp="+mech.getHealth()+" wear="+mech.wear()+" steel="+be.storedSteel()+" contains="+be.containsMech(mech)+" auth="+mech.maintenanceAuthorized(owner)+" complete="+be.complete()+" owner="+(h.getLevel().getPlayerByUUID(owner.getUUID())!=null)+" y="+mech.getY()+" expected="+(center.getY()+1)+" ground="+mech.onGround()+" motion="+mech.getDeltaMovement()+" box="+mech.getBoundingBox());
            h.assertTrue(be.storedEnergy()+mech.energy()==99000,"Platform charge is conserved; repair costs exactly 1000");
            mech.discard();owner.discard();h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void sharedBudgetAndSimulation(GameTestHelper h) {
        var be=platform(h);
        be.energyPort().receiveEnergy(100000,false);
        h.assertTrue(be.extractWirelessEnergy(20000,true)==20000 && be.storedEnergy()==100000,"Simulation must not spend energy");
        h.assertTrue(be.extractWirelessEnergy(15000,false)==15000 && be.extractWirelessEnergy(15000,false)==5000,"All consumers share per tick budget");
        h.assertTrue(be.energyPort().extractEnergy(1000,false)==0,"Capability cannot steal stored power");
        h.runAfterDelay(1,()->{h.assertTrue(be.extractWirelessEnergy(20000,false)==20000,"Next tick restores output budget");h.succeed();});
    }
}
