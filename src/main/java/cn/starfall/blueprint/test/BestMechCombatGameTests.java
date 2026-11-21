package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("starfall_blueprint_mech_combat")
@PrefixGameTestTemplate(false)
public final class BestMechCombatGameTests {
    @GameTest(template="combat_arena")
    public static void combatStateSanitizesAndPersistsCooldown(GameTestHelper h) {
        var mech=BestMechs.COMBAT_TYPE.get().create(h.getLevel());
        var systems=mech.combatSystems();
        var root=new CompoundTag(); var state=new CompoundTag();
        state.putString("Weapon",BestMechCombatSystems.SNIPER); state.putString("Drone",BestMechCombatSystems.ATTACK_DRONE);
        state.putInt("Cooldown",40); state.putInt("DroneCooldown",30); root.put(BestMechCombatSystems.STATE,state);
        systems.load(root); var saved=new CompoundTag(); systems.save(saved);
        h.assertTrue(saved.equals(root),"Packing preserves physical components and cooldowns");
        h.assertTrue(mech.visualWeaponKey().equals(BestMechCombatSystems.SNIPER)
                && mech.visualDroneKey().equals(BestMechCombatSystems.ATTACK_DRONE), "Synced visual keys match the installed equipment");
        state.putString("Weapon","forged_weapon"); state.putString("Drone","forged_drone"); state.putInt("Cooldown",Integer.MAX_VALUE);
        systems.load(root); saved=new CompoundTag(); systems.save(saved);
        h.assertTrue(systems.weapon().equals(BestMechCombatSystems.LASER) && systems.drone().isEmpty(),"Unknown component IDs cannot grant abilities");
        h.assertTrue(mech.visualWeaponKey().equals(BestMechCombatSystems.LASER)
                && mech.visualDroneKey().isEmpty(), "Unknown equipment restores the default chassis appearance");
        h.assertTrue(saved.getCompound(BestMechCombatSystems.STATE).getInt("Cooldown")==100,"Cooldown bounded");
        h.assertTrue(BestMechCombatSystems.ammunition(BestMechCombatSystems.SNIPER).equals(BestMechCombatSystems.SNIPER_ROUND),"Sniper uses distinct real cargo ammunition");
        h.assertTrue(BestMechCombatSystems.ammunition(BestMechCombatSystems.LASER).isEmpty(),"Laser consumes energy instead of ammunition");
        h.succeed();
    }
    @GameTest(template="combat_arena")
    public static void realCargoAmmoAndCooldown(GameTestHelper h) {
        var profile=new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"CombatAmmoTest");
        var p=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false));
        // Use an engineering-qualified universal pilot, whose knowledge is set through progression below.
        var mech=BestMechs.COMBAT_TYPE.get().create(h.getLevel());
        var root=new CompoundTag(); root.putInt("Energy",100000); root.putFloat("Health",100);
        var state=new CompoundTag(); state.putString("Weapon",BestMechCombatSystems.MACHINE_GUN); root.put(BestMechCombatSystems.STATE,state); mech.loadState(root);
        var knowledge=new CompoundTag(); knowledge.putBoolean("combat_operation",true); p.getPersistentData().put("BestModuleKnowledge",knowledge);
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(3,1,3)); mech.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0); h.getLevel().addFreshEntity(mech);
        p.moveTo(mech.getX(),mech.getY()+2,mech.getZ(),0,0); p.startRiding(mech,true); mech.positionRider(p); p.setYRot(0); p.setXRot(0);
        mech.cargo().setItem(0,CatalogueContent.stack(BestMechCombatSystems.BULLET,2));
        h.assertTrue(mech.combatSystems().fire(p),"Qualified pilot fires actual machine gun");
        h.assertTrue(mech.cargo().getItem(0).getCount()==1 && mech.energy()==98000,"One real cargo bullet and energy are consumed");
        h.assertTrue(!mech.combatSystems().fire(p) && mech.cargo().getItem(0).getCount()==1,"Cooldown prevents duplicated ammo charge");
        p.stopRiding(); mech.discard(); h.succeed();
    }
    @GameTest(template="combat_arena")
    public static void inactiveDroneCannotGrantFreeDefense(GameTestHelper h) {
        var mech=BestMechs.UNIVERSAL_TYPE.get().create(h.getLevel());
        var systems=new BestMechCombatSystems(mech); var zombie=EntityType.ZOMBIE.create(h.getLevel());
        float damage=systems.reduceDamage(mech.damageSources().mobAttack(zombie),20);
        h.assertTrue(damage==20,"Unpiloted universal mech receives no free drone shield");
        var before=mech.stateTag(); systems.tick();
        h.assertTrue(before.equals(mech.stateTag()),"Inactive drones do not spend or invent energy"); h.succeed();
    }
}
