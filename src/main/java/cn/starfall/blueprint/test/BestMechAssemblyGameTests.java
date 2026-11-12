package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("starfall_blueprint_mech_assembly")
@PrefixGameTestTemplate(false)
public final class BestMechAssemblyGameTests {
    private static ServerPlayer player(GameTestHelper h){
        var profile=new GameProfile(UUID.randomUUID(),"AssemblyTest");var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(connection);
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,CommonListenerCookie.createInitial(profile,false)){@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}};
        p.setGameMode(GameType.SURVIVAL);p.getPersistentData().putBoolean("BestMechOperation",true);p.getInventory().selected=0;return p;
    }
    private static ItemStack packed(String id,int energy,float health,int mask){var s=CatalogueContent.stack(id,1);var t=new CompoundTag();t.putBoolean("Isolated",true);t.putInt("Energy",energy);t.putFloat("Health",health);t.putInt("Wear",90);t.putInt("UpgradeMask",mask);t.putUUID("MechId",UUID.randomUUID());BestMechItem.setState(s,t);return s;}
    private static void grant(ServerPlayer p,String...keys){var t=p.getPersistentData().getCompound("BestModuleKnowledge").copy();for(String k:keys)t.putBoolean(k,true);p.getPersistentData().put("BestModuleKnowledge",t);}
    private static void materials(ServerPlayer p,int recipe){for(var c:BestMechAssembly.RECIPES.get(recipe).cost().entrySet())p.getInventory().add(CatalogueContent.stack(c.getKey(),c.getValue()));}
    private static int count(ServerPlayer p,String key){return p.getInventory().items.stream().filter(s->CatalogueContent.matches(key,s)).mapToInt(ItemStack::getCount).sum();}
    @GameTest(template="empty",timeoutTicks=100)
    public static void fusion_without_optional_branches_preserves_damage_and_returns_components(GameTestHelper h){
        var p=player(h);grant(p,"combat_operation","confluence");
        var e=packed("best_mech",1000,50,1);var c=packed("best_combat_mech",2000,100,8);float max=BestMechProgression.maxHealth("engineering",BestMechItem.state(e))+BestMechProgression.maxHealth("combat",BestMechItem.state(c));
        p.getInventory().setItem(0,e);p.getInventory().setItem(1,c);materials(p,15);
        h.assertTrue(BestMechAssembly.perform(p,55),"Basic knowledge can fuse without optional nodes");
        h.assertTrue(count(p,"best_mech")==0&&count(p,"best_combat_mech")==0&&count(p,"best_universal_mech")==1,"Both chassis consumed once");
        var out=p.getInventory().items.stream().filter(s->CatalogueContent.matches("best_universal_mech",s)).findFirst().orElseThrow();var t=BestMechItem.state(out);
        h.assertTrue(t.getInt("Energy")==3000&&Math.abs(t.getFloat("Health")-500*150/max)<0.01&&t.getInt("Wear")==90,"Damage ratio, energy and wear preserved");
        h.assertTrue(count(p,"best_canglan_engineering")==1&&count(p,"best_nutao_fire_control")==1,"Installed modules returned");
        h.assertTrue(!BestMechAssembly.perform(p,55),"Repeated request cannot duplicate chassis");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void failed_fusion_rolls_back_all_inputs(GameTestHelper h){
        var p=player(h);grant(p,"combat_operation","confluence");p.getInventory().setItem(0,packed("best_mech",1000,50,0));p.getInventory().setItem(1,packed("best_combat_mech",2000,100,0));materials(p,15);
        var state=BestMechItem.state(p.getInventory().getItem(0));state.putBoolean("Isolated",false);BestMechItem.setState(p.getInventory().getItem(0),state);
        var before=p.getInventory().items.stream().map(ItemStack::copy).toList();h.assertTrue(!BestMechAssembly.perform(p,55),"Unisolated input refused");for(int i=0;i<36;i++)h.assertTrue(ItemStack.matches(before.get(i),p.getInventory().getItem(i)),"Failed transaction changes no inventory slot");h.succeed();
    }



    @GameTest(template="empty",timeoutTicks=100)
    public static void full_health_armor_removal_and_reinstall_never_heals(GameTestHelper h){
        var p=player(h);grant(p,"combat_armor");p.getInventory().setItem(0,packed("best_combat_mech",0,350,16));
        h.assertTrue(BestMechAssembly.perform(p,124),"Full health armor may be removed");
        h.assertTrue(BestMechItem.state(p.getInventory().getItem(0)).getFloat("Health")==200,"Health scales down with reduced maximum");
        h.assertTrue(BestMechAssembly.perform(p,104),"Returned armor can be reinstalled");
        h.assertTrue(BestMechItem.state(p.getInventory().getItem(0)).getFloat("Health")==200,"Installing armor never heals absolute health");
        h.assertTrue(BestMechAssembly.perform(p,124),"Second removal succeeds");
        h.assertTrue(BestMechItem.state(p.getInventory().getItem(0)).getFloat("Health")<=200,"Repeated cycle cannot heal");h.succeed();
    }

}
