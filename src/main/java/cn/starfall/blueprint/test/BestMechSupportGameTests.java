package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("starfall_blueprint_mech_support")
@PrefixGameTestTemplate(false)
public final class BestMechSupportGameTests {
    @GameTest(template="combat_arena")
    public static void repairConsumesRealCargoAndStopsWhenFull(GameTestHelper h) {
        var profile=new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"RepairPilot");
        var p=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false));
        p.getPersistentData().putInt("BestResearchLevel",3);
        // Universal qualification is the exact learned predicate used by the runtime.
        var knowledge=new CompoundTag(); knowledge.putBoolean("confluence",true); p.getPersistentData().put("BestModuleKnowledge",knowledge);
        var mech=BestMechs.UNIVERSAL_TYPE.get().create(h.getLevel());
        var state=new CompoundTag(); state.putInt("Energy",20000); state.putFloat("Health",100);
        mech.loadState(state); var pos=h.absolutePos(new net.minecraft.core.BlockPos(4,1,2));
        mech.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0); h.getLevel().addFreshEntity(mech);
        p.moveTo(mech.getX(),mech.getY(),mech.getZ(),0,0); p.startRiding(mech,true);
        h.assertTrue(mech.qualifiedFor(p),"Pilot knowledge must qualify");
        mech.tickCount=40; BestMechSupport.tick(mech);
        h.assertTrue(mech.getHealth()==100 && mech.energy()==20000,"No material means no free healing");
        mech.cargo().setItem(0,CatalogueContent.stack("steel",2)); BestMechSupport.tick(mech);
        h.assertTrue(mech.getHealth()==102 && mech.energy()==16000 && mech.cargo().getItem(0).getCount()==1,"Real steel and energy buy exactly one repair");
        mech.setHealth(mech.getMaxHealth()); BestMechSupport.tick(mech);
        h.assertTrue(mech.energy()==16000 && mech.cargo().getItem(0).getCount()==1,"Full health does not consume resources");
        var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());
        h.assertTrue(!BestMechSupport.isFriendly(mech,cow),"Neutral wildlife is not a friendly treatment target");
        var enemy=net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
        float reduced=mech.combatSystems().reduceDamage(mech.damageSources().mobAttack(enemy),20);
        h.assertTrue(reduced==15 && mech.energy()==13500,"Built-in defense consumes energy for one mitigation");
        mech.setIsolated(true);
        h.assertTrue(mech.combatSystems().reduceDamage(mech.damageSources().mobAttack(enemy),20)==20 && mech.energy()==13500,"Isolated defense cannot grant mitigation or spend energy");
        p.stopRiding(); mech.discard(); h.succeed();
    }
}
