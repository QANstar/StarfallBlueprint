package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("starfall_blueprint_mech_combat")
@PrefixGameTestTemplate(false)
public final class BestMechFinalCombatGameTests {
    private static ServerPlayer pilot(GameTestHelper h) {
        var profile=new GameProfile(java.util.UUID.randomUUID(),"FinalCombatTest");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,CommonListenerCookie.createInitial(profile,false));
        var knowledge=new CompoundTag(); knowledge.putBoolean("confluence",true); p.getPersistentData().put("BestModuleKnowledge",knowledge);
        return p;
    }
    @GameTest(template="combat_arena")
    public static void finalWeaponsPayRealCosts(GameTestHelper h) {
        String[] weapons={BestMechCombatSystems.MELEE,BestMechCombatSystems.MACHINE_GUN,BestMechCombatSystems.SNIPER,BestMechCombatSystems.LASER,BestMechCombatSystems.EXPLOSIVE};
        int[] costs={500,500,500,500,500};
        int[] cooldowns={15,4,30,8,24};
        int[] heat={0,0,0,0,0};
        var p=pilot(h);
        for(int i=0;i<weapons.length;i++) {
            var mech=BestMechs.UNIVERSAL_TYPE.get().create(h.getLevel());
            var state=new CompoundTag(); state.putInt("Energy",100000); state.putFloat("Health",100); state.putInt("UpgradeMask",BestMechProgression.TIANPU);
            var combat=new CompoundTag(); combat.putString("Weapon",weapons[i]); state.put(BestMechCombatSystems.STATE,combat); mech.loadState(state);
            var pos=h.absolutePos(new BlockPos(3,1,3)); mech.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0); h.getLevel().addFreshEntity(mech);
            p.moveTo(mech.getX(),mech.getY()+2,mech.getZ(),0,0); p.startRiding(mech,true); mech.positionRider(p); p.setYRot(0); p.setXRot(0);
            String ammo=BestMechCombatSystems.ammunition(weapons[i]);
            h.assertTrue(mech.combatSystems().fire(p),"Final weapon fires: "+weapons[i]);
            h.assertTrue(mech.energy()==100000-costs[i] && mech.heat()==heat[i] && mech.wear()==0,"Only energy is charged: "+weapons[i]);
            h.assertTrue(mech.combatSystems().cooldown()==cooldowns[i],"Final cooldown: "+weapons[i]);
            if(!ammo.isEmpty()) h.assertTrue(mech.cargo().getItem(0).isEmpty(),"Final weapon needs no ammunition: "+weapons[i]);
            h.assertTrue(!mech.combatSystems().fire(p),"Cooldown prevents another shot: "+weapons[i]);
            p.stopRiding(); mech.discard();
        }
        h.succeed();
    }
    @GameTest(template="combat_arena", batch="terrain_destruction")
    public static void terrainDestructionRequiresSwitch(GameTestHelper h) {
        var p=pilot(h); var mech=BestMechs.UNIVERSAL_TYPE.get().create(h.getLevel());
        var state=new CompoundTag(); state.putInt("Energy",100000); state.putInt("UpgradeMask",BestMechProgression.TIANPU);
        var combat=new CompoundTag(); combat.putString("Weapon",BestMechCombatSystems.EXPLOSIVE); state.put(BestMechCombatSystems.STATE,combat); mech.loadState(state);
        var pos=h.absolutePos(new BlockPos(4,1,2)); mech.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0); h.getLevel().addFreshEntity(mech);
        p.moveTo(mech.getX(),mech.getY(),mech.getZ(),0,0); h.getLevel().addNewPlayer(p); p.startRiding(mech,true); mech.positionRider(p); p.setYRot(0); p.setXRot(0);
        mech.setNoGravity(true); var block=BlockPos.containing(p.getEyePosition().add(0,0,3)); h.getLevel().setBlockAndUpdate(block,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        h.assertTrue(mech.fireWeapon(p),"Missile launches with terrain damage disabled");
        h.runAfterDelay(8, () -> {
            h.assertTrue(!h.getLevel().getBlockState(block).isAir(),"Terrain survives missile impact with switch off");
            mech.combatSystems().setTerrainDamage(true); var saved=mech.stateTag(); saved.putInt("WeaponCooldown",0); saved.getCompound(BestMechCombatSystems.STATE).putInt("Cooldown",0); mech.loadState(saved);
            h.assertTrue(mech.combatSystems().terrainDamage(),"Terrain switch survives state roundtrip");
            h.assertTrue(mech.fireWeapon(p),"Missile launches with terrain damage enabled");
            h.runAfterDelay(8, () -> {
                h.assertTrue(h.getLevel().getBlockState(block).isAir(),"Terrain is broken after missile impact when enabled");
                p.stopRiding(); p.discard(); mech.discard(); h.succeed();
            });
        });
    }
    @GameTest(template="combat_arena")
    public static void friendlyBodyStopsShotWithoutCharge(GameTestHelper h) {
        var p=pilot(h); var mech=BestMechs.UNIVERSAL_TYPE.get().create(h.getLevel());
        var state=new CompoundTag(); state.putInt("Energy",100000); state.putFloat("Health",100); state.putInt("UpgradeMask",BestMechProgression.TIANPU);
        var combat=new CompoundTag(); combat.putString("Weapon",BestMechCombatSystems.MACHINE_GUN); state.put(BestMechCombatSystems.STATE,combat); mech.loadState(state);
        var pos=h.absolutePos(new BlockPos(3,1,3)); mech.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0); h.getLevel().addFreshEntity(mech);
        p.moveTo(mech.getX(),mech.getY()+2,mech.getZ(),0,0); p.startRiding(mech,true); mech.positionRider(p); p.setYRot(0); p.setXRot(0);
        var friend=net.minecraft.world.entity.EntityType.WOLF.create(h.getLevel()); friend.setTame(true,true); friend.setOwnerUUID(p.getUUID());
        var eye=p.getEyePosition(); friend.moveTo(eye.x,eye.y-.4,eye.z+2,0,0); h.getLevel().addFreshEntity(friend);
        float health=friend.getHealth();
        h.assertTrue(mech.combatSystems().fire(p) && friend.getHealth()==health && mech.energy()==99500,"Shot fires without friendly damage by default");
        mech.combatSystems().setFriendlyFire(true); var saved=mech.stateTag(); saved.putInt("WeaponCooldown",0); saved.getCompound(BestMechCombatSystems.STATE).putInt("Cooldown",0); mech.loadState(saved);
        h.assertTrue(mech.combatSystems().friendlyFire(),"Friendly fire survives state roundtrip");
        h.assertTrue(mech.combatSystems().fire(p) && friend.getHealth()<health && mech.energy()==99000,"Enabled friendly fire damages ally");
        friend.discard(); p.stopRiding(); mech.discard(); h.succeed();
    }
}
