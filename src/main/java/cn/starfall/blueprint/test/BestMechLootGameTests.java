package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("starfall_blueprint_mech_loot")
@PrefixGameTestTemplate(false)
public final class BestMechLootGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var profile = new GameProfile(UUID.randomUUID(), "MechLootTest");
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
    private static BestMechEntity mech(GameTestHelper h, ServerPlayer p, int slots) {
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z), Blocks.STONE);
        var m=(slots==27?BestMechs.HEAVY_TYPE.get():BestMechs.TYPE.get()).create(h.getLevel());
        var t=new net.minecraft.nbt.CompoundTag(); t.putInt("Energy",100_000);
        if(slots==18) t.putString(BestMechBodies.STATE,BestMechBodies.REINFORCED); m.loadState(t);
        var pos=h.absolutePos(new BlockPos(5,1,3)); m.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        h.getLevel().addFreshEntity(m); m.setOnGround(true); p.startRiding(m);
        pos=h.absolutePos(new BlockPos(5,2,3)); p.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0); return m;
    }
    private static BlockPos target(GameTestHelper h) { return h.absolutePos(new BlockPos(5,3,6)); }
    private static void reset(BestMechEntity m) { var t=m.stateTag(); t.putInt("DrillCooldown",0); m.loadState(t); m.setOnGround(true); }
    private static int cargo(BestMechEntity m, Item item) { int n=0; for(int i=0;i<m.cargoSlots();i++) if(m.cargo().getItem(i).is(item)) n+=m.cargo().getItem(i).getCount(); return n; }

    @GameTest(template="empty")
    public static void actualToolDropsEnterCargoAndAmbientAndCostsRemainCorrect(GameTestHelper h) {
        var p=player(h); var m=mech(h,p,9); var held=new ItemStack(Items.STICK); p.setItemInHand(InteractionHand.MAIN_HAND,held);
        var ambient=new ItemEntity(h.getLevel(),p.getX(),p.getY(),p.getZ(),new ItemStack(Items.DIAMOND,7)); h.getLevel().addFreshEntity(ambient);
        try {
            h.getLevel().setBlockAndUpdate(target(h),Blocks.STONE.defaultBlockState()); int energy=m.energy();
            h.assertTrue(m.work(p) && cargo(m,Items.COBBLESTONE)==1,"Actual iron pickaxe stone drop enters cargo");
            h.assertTrue(p.getMainHandItem()==held && m.energy()==energy-m.workCost() && m.heat()==m.workHeat() && m.wear()==1,"Hand and exact successful block costs preserved");
            h.assertTrue(!ambient.isRemoved() && ambient.getItem().getCount()==7,"Nearby ambient loot is untouched");
            var t=m.stateTag(); t.putString(BestMechArms.STATE,BestMechArms.EXCAVATOR); m.loadState(t); reset(m);
            h.getLevel().setBlockAndUpdate(target(h),Blocks.DIRT.defaultBlockState());
            h.assertTrue(m.work(p) && cargo(m,Items.DIRT)==1,"Actual shovel drops enter cargo");
            p.stopRiding(); m.discard(); m=mech(h,p,27);
            t=m.stateTag(); t.putString(BestMechArms.STATE,BestMechArms.HEAVY_DRILL); t.putInt("Energy",1_000_000); m.loadState(t);
            for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) h.getLevel().setBlockAndUpdate(target(h).offset(x,y,0),Blocks.STONE.defaultBlockState());
            h.assertTrue(m.work(p) && cargo(m,Items.COBBLESTONE)==9 && m.energy()==1_000_000-9*m.workCost() && m.wear()==9 && m.heat()==9*m.workHeat(),"Real heavy 3x3 collects each block and charges each successful break");
        } finally { p.stopRiding(); m.discard(); ambient.discard(); }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void modifiedComponentsMergePartiallyAcrossOnlyActiveCargo(GameTestHelper h) {
        var p=player(h);
        Consumer<BlockDropsEvent> change=e->{ if(e.getBreaker()==p) { e.getDrops().clear(); var s=new ItemStack(Items.DIAMOND,5); s.set(DataComponents.CUSTOM_NAME,Component.literal("current")); e.getDrops().add(new ItemEntity(h.getLevel(),e.getPos().getX()+.5,e.getPos().getY(),e.getPos().getZ()+.5,s)); }};
        NeoForge.EVENT_BUS.addListener(change);
        try {
            for(int slots:new int[]{9,18,27}) {
                var m=mech(h,p,slots);
                try {
                    for(int i=0;i<slots;i++) m.cargo().setItem(i,new ItemStack(Items.COBBLESTONE,64));
                    var named=new ItemStack(Items.DIAMOND,62); named.set(DataComponents.CUSTOM_NAME,Component.literal("current")); m.cargo().setItem(slots-1,named);
                    m.cargo().setItem(0,new ItemStack(Items.DIAMOND,63));
                    h.getLevel().setBlockAndUpdate(target(h),Blocks.STONE.defaultBlockState());
                    h.assertTrue(m.work(p) && m.cargo().getItem(slots-1).getCount()==64 && m.cargo().getItem(0).getCount()==63,"Only exact components merge up to maximum in active last cell: "+slots);
                    var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,m.getBoundingBox().inflate(8),e->e.getItem().has(DataComponents.CUSTOM_NAME));
                    h.assertTrue(drops.size()==1 && drops.getFirst().getItem().getCount()==3,"Partial overflow stays on ground"); drops.forEach(ItemEntity::discard);
                    if(slots<27) h.assertTrue(m.cargo().getItem(slots).isEmpty(),"Hidden backing cells stay empty");
                    reset(m); h.getLevel().setBlockAndUpdate(target(h),Blocks.STONE.defaultBlockState());
                    h.assertTrue(m.work(p),"Full cargo still permits paid work");
                    drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,m.getBoundingBox().inflate(8),e->e.getItem().has(DataComponents.CUSTOM_NAME));
                    h.assertTrue(drops.size()==1 && drops.getFirst().getItem().getCount()==5,"Full cargo leaves all modified loot on ground"); drops.forEach(ItemEntity::discard);
                } finally { p.stopRiding(); m.discard(); }
            }
        } finally { NeoForge.EVENT_BUS.unregister(change); }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void cancelledLootGamerulesAndUnrelatedBreaksRemainVanilla(GameTestHelper h) {
        var p=player(h); var m=mech(h,p,9); var rules=h.getLevel().getGameRules(); boolean old=rules.getBoolean(GameRules.RULE_DOBLOCKDROPS);
        Consumer<BlockDropsEvent> cancel=e->{ if(e.getBreaker()==p) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            h.getLevel().setBlockAndUpdate(target(h),Blocks.STONE.defaultBlockState());
            h.assertTrue(m.work(p) && cargo(m,Items.COBBLESTONE)==0 && m.wear()==1,"Cancelled loot creates no cargo but successful removal still costs");
            NeoForge.EVENT_BUS.unregister(cancel); reset(m);
            Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> protect=e->{ if(e.getPlayer()==p) e.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(protect);
            try {
                h.getLevel().setBlockAndUpdate(target(h),Blocks.STONE.defaultBlockState()); var before=m.stateTag();
                h.assertTrue(!m.work(p) && h.getLevel().getBlockState(target(h)).is(Blocks.STONE) && before.equals(m.stateTag()),"Protected actual break changes no block, cargo, cooldown or costs");
            } finally { NeoForge.EVENT_BUS.unregister(protect); }
            rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(false,h.getLevel().getServer()); h.getLevel().setBlockAndUpdate(target(h),Blocks.STONE.defaultBlockState());
            h.assertTrue(m.work(p) && cargo(m,Items.COBBLESTONE)==0,"Disabled block drops cannot be synthesized into cargo");
            rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(true,h.getLevel().getServer());
            var unrelated=target(h).offset(2,0,0); h.getLevel().setBlockAndUpdate(unrelated,Blocks.STONE.defaultBlockState());
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE)); p.gameMode.destroyBlock(unrelated);
            h.assertTrue(cargo(m,Items.COBBLESTONE)==0,"Same operator ordinary break outside work is never collected");
            Consumer<BlockDropsEvent> nested=e->{ if(e.getBreaker()==p) {
                e.getDrops().clear(); e.getDrops().add(new ItemEntity(h.getLevel(),e.getPos().getX()+.5,e.getPos().getY(),e.getPos().getZ()+.5,CatalogueContent.stack("best_mech",1)));
                e.setDroppedExperience(5);
            }};
            NeoForge.EVENT_BUS.addListener(nested);
            try {
                reset(m); h.getLevel().setBlockAndUpdate(target(h),Blocks.STONE.defaultBlockState());
                h.assertTrue(m.work(p),"Modified packed-mech loot still permits work");
                for(int i=0;i<m.cargoSlots();i++) h.assertTrue(!(m.cargo().getItem(i).getItem() instanceof BestMechItem),"Packed mechs cannot nest in cargo");
                var packed=h.getLevel().getEntitiesOfClass(ItemEntity.class,m.getBoundingBox().inflate(8),e->e.getItem().getItem() instanceof BestMechItem);
                h.assertTrue(packed.size()==1,"Rejected nesting remains on ground"); packed.forEach(ItemEntity::discard);
                var xp=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,m.getBoundingBox().inflate(8));
                h.assertTrue(xp.stream().mapToInt(net.minecraft.world.entity.ExperienceOrb::getValue).sum()==5,"Event-modified XP remains in world"); xp.forEach(net.minecraft.world.entity.Entity::discard);
            } finally { NeoForge.EVENT_BUS.unregister(nested); }
        } finally { NeoForge.EVENT_BUS.unregister(cancel); rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(old,h.getLevel().getServer()); p.stopRiding(); m.discard(); }
        h.succeed();
    }
}
