package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("starfall_blueprint_area_excavator")
@PrefixGameTestTemplate(false)
public final class BestMechAreaExcavatorGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var profile = new GameProfile(UUID.randomUUID(), "AreaShovelTest");
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        p.setGameMode(GameType.SURVIVAL);
        p.getPersistentData().putInt("BestEngineeringResearch", 3);
        for(String key : List.of("BestDiagnostics", "BestLightMobility", "BestMechOperation")) p.getPersistentData().putBoolean(key, true);
        return p;
    }
    private static BestMechEntity mech(GameTestHelper h, boolean heavy) {
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z), Blocks.STONE);
        var m=(heavy?BestMechs.HEAVY_TYPE:BestMechs.TYPE).get().create(h.getLevel());
        var pos=h.absolutePos(new BlockPos(3,1,3)); m.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        h.getLevel().addFreshEntity(m); reset(m,1_000_000,0,0); return m;
    }
    private static void reset(BestMechEntity m, int energy, int heat, int wear) {
        var t=m.stateTag(); t.putString(BestMechArms.STATE,BestMechArms.HEAVY_EXCAVATOR);
        t.putInt("Energy",energy); t.putInt("Heat",heat); t.putInt("Wear",wear); t.putInt("DrillCooldown",0);
        t.putBoolean("ThermalLock",false); t.putBoolean("Isolated",false); m.loadState(t); m.setOnGround(true); m.setDeltaMovement(Vec3.ZERO);
    }
    private static List<BlockPos> plane(BlockPos c) {
        var list=new ArrayList<BlockPos>(); list.add(c);
        for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) if(x!=0||y!=0) list.add(c.offset(x,y,0)); return list;
    }
    private static void fill(GameTestHelper h,List<BlockPos> positions) { positions.forEach(pos->h.getLevel().setBlockAndUpdate(pos,Blocks.DIRT.defaultBlockState())); }
    private static void aim(ServerPlayer p,BestMechEntity m,BlockPos c) {
        p.setShiftKeyDown(false); p.startRiding(m); p.moveTo(c.getX()+.5,c.getY()+.5-p.getEyeHeight(),c.getZ()-1.5,0,0);
    }
    @GameTest(template="empty")
    public static void soilCargoActualShovelAndCenterProtection(GameTestHelper h) {
        var p=player(h); var m=mech(h,true); var c=h.absolutePos(new BlockPos(6,5,6)); var positions=plane(c);
        var events=new ArrayList<BlockPos>(); var denied=new HashSet<BlockPos>();
        Consumer<BlockEvent.BreakEvent> protect=e->{if(e.getPlayer()==p){events.add(e.getPos().immutable()); h.assertTrue(p.getMainHandItem().is(Items.IRON_SHOVEL),"Actual iron shovel in break hook"); if(denied.contains(e.getPos())) e.setCanceled(true);}};
        NeoForge.EVENT_BUS.addListener(protect);
        try {
            var held=new ItemStack(Items.EMERALD,3); p.setItemInHand(InteractionHand.MAIN_HAND,held);
            fill(h,positions); aim(p,m,c);
            h.assertTrue(m.work(p)&&events.equals(positions)&&m.cargo().countItem(Items.DIRT)==9,"Center-first nine soil blocks enter active cargo");
            h.assertTrue(m.energy()==838000&&m.heat()==162&&m.wear()==9&&p.getMainHandItem()==held&&m.stateTag().getInt("DrillCooldown")==40,"Exact success costs and hand restoration");
            reset(m,1_000_000,0,0); fill(h,positions); h.getLevel().setBlockAndUpdate(c,Blocks.STONE.defaultBlockState()); events.clear(); var before=m.stateTag();
            h.assertTrue(!m.work(p)&&events.isEmpty()&&before.equals(m.stateTag())&&positions.subList(1,9).stream().allMatch(pos->h.getLevel().getBlockState(pos).is(Blocks.DIRT)),"Stone center aborts entire action");
            fill(h,positions); denied.add(c); events.clear();
            h.assertTrue(!m.work(p)&&events.equals(List.of(c))&&before.equals(m.stateTag()),"Cancelled center changes no costs or neighbors");
            denied.clear(); denied.add(positions.get(1)); events.clear();
            h.assertTrue(m.work(p)&&m.energy()==856000&&m.wear()==8&&h.getLevel().getBlockState(positions.get(1)).is(Blocks.DIRT)&&h.getLevel().getBlockState(positions.get(8)).isAir(),"Protected neighbor skipped independently");
        } finally {NeoForge.EVENT_BUS.unregister(protect);p.stopRiding();m.discard();} h.succeed();
    }
    @GameTest(template="empty")
    public static void partialBudgetThermalWearAndSharedCooldown(GameTestHelper h) {
        var p=player(h);var m=mech(h,true);var c=h.absolutePos(new BlockPos(6,5,6));var positions=plane(c);
        try {
            for(int budget:new int[]{18000,36000}) {
                reset(m,budget,0,0);fill(h,positions);aim(p,m,c);
                h.assertTrue(m.work(p)&&m.energy()==0&&m.wear()==budget/18000&&m.heat()==budget/1000,"Partial budget charges only successful blocks");
                h.assertTrue(positions.stream().filter(pos->h.getLevel().getBlockState(pos).isAir()).count()==budget/18000,"Stops before unaffordable neighbor");
            }
            for(boolean thermal:new boolean[]{true,false}) {
                reset(m,1_000_000,thermal?990:0,thermal?0:BestMechEntity.MAX_WEAR-1);fill(h,positions);aim(p,m,c);
                h.assertTrue(m.work(p)&&m.energy()==982000&&(thermal?m.thermalLocked():m.wear()==BestMechEntity.MAX_WEAR),"Stops at thermal or wear boundary");
                h.assertTrue(positions.subList(1,9).stream().allMatch(pos->h.getLevel().getBlockState(pos).is(Blocks.DIRT)),"Boundary prevents neighbor breaks");
            }
            reset(m,1_000_000,0,0);fill(h,positions);aim(p,m,c);h.assertTrue(m.work(p),"Creates cooldown");
            p.stopRiding();p.setShiftKeyDown(true);m.setIsolated(true);h.assertTrue(m.removeArm(p),"Service removes shovel");
            p.setItemInHand(InteractionHand.MAIN_HAND,CatalogueContent.stack(BestMechArms.HEAVY_DRILL,1));h.assertTrue(m.installArm(p,InteractionHand.MAIN_HAND),"Heavy drill installs");
            h.assertTrue(m.stateTag().getInt("DrillCooldown")==40&&m.workCost()==48000,"Swap retains shared cooldown and drill economics");
            m.setIsolated(false);aim(p,m,c);var before=m.stateTag();h.assertTrue(!m.work(p)&&before.equals(m.stateTag()),"Shared cooldown prevents swap bypass");
        } finally {p.stopRiding();m.discard();}h.succeed();
    }
    @GameTest(template="empty")
    public static void heavyOnlyInstallLoadedRecoveryAndPackedState(GameTestHelper h) {
        var p=player(h);var m=mech(h,true);var light=mech(h,false);
        try {
            p.setShiftKeyDown(true);light.setIsolated(true);var t=light.stateTag();t.putString(BestMechArms.STATE,"");light.loadState(t);
            var arm=CatalogueContent.stack(BestMechArms.HEAVY_EXCAVATOR,1);p.setItemInHand(InteractionHand.MAIN_HAND,arm);var before=light.stateTag();
            h.assertTrue(!light.installArm(p,InteractionHand.MAIN_HAND)&&before.equals(light.stateTag())&&p.getMainHandItem()==arm&&arm.getCount()==1,"Light install refuses atomically");
            t.putString(BestMechArms.STATE,BestMechArms.HEAVY_EXCAVATOR);t.putInt("DrillCooldown",40);light.loadState(t);before=light.stateTag();
            h.assertTrue(!light.armCompatible()&&!light.work(p)&&before.equals(light.stateTag())&&light.removeArm(p),"Known incompatible key is inert but removable");
            h.assertTrue(p.getInventory().items.stream().anyMatch(s->s.is(CatalogueContent.item(BestMechArms.HEAVY_EXCAVATOR))),"Removal returns exact arm");
            m.setIsolated(true);h.assertTrue(m.removeArm(p),"Heavy service clears slot");p.setItemInHand(InteractionHand.MAIN_HAND,arm);
            h.assertTrue(m.installArm(p,InteractionHand.MAIN_HAND)&&arm.isEmpty()&&m.armCompatible(),"Heavy main hand installs one physical arm");
            t=m.stateTag();t.putInt("DrillCooldown",40);m.loadState(t);before=m.stateTag();p.getInventory().clearContent();
            h.assertTrue(m.pack(p)&&BestMechItem.state(p.getMainHandItem()).equals(before),"Packed state preserves heavy shovel and shared cooldown");
            h.assertTrue(BestMechArms.read(new net.minecraft.nbt.CompoundTag()).equals(BestMechArms.DRILL),"Legacy missing arm stays drill");
        } finally {p.stopRiding();m.discard();light.discard();}h.succeed();
    }
}
