package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("starfall_blueprint_progression")
@PrefixGameTestTemplate(false)
public final class ProgressionGameTests {
    private static FakePlayer player(GameTestHelper h) {
        FakePlayer p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"ProgressionTest"));
        p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.experienceLevel=100;
        p.getPersistentData().remove("BestEngineeringResearch");p.getPersistentData().remove("BestLightMobility");
        p.getPersistentData().remove("BestDiagnostics");p.getPersistentData().remove("BestMechOperation");
        p.getPersistentData().remove("LingchuanResearch");return p;
    }
    private static int index(String id) {
        for(int i=0;i<Progression.NODES.size();i++)if(Progression.NODES.get(i).id().equals(id))return i;
        throw new IllegalArgumentException(id);
    }
    private static void inputs(FakePlayer p,String id) {
        p.getInventory().clearContent();int slot=0;
        for(var cost:Progression.NODES.get(index(id)).costs())p.getInventory().setItem(slot++,CatalogueContent.stack(cost.key(),cost.count()));
    }
    private static List<ItemStack> snapshot(FakePlayer p) {
        List<ItemStack> result=new ArrayList<>();for(int i=0;i<36;i++)result.add(p.getInventory().getItem(i).copy());return result;
    }
    private static void unchanged(GameTestHelper h,FakePlayer p,List<ItemStack> before,int xp,String reason) {
        h.assertTrue(p.experienceLevel==xp,reason+"; XP levels");
        for(int i=0;i<36;i++)h.assertTrue(ItemStack.matches(before.get(i),p.getInventory().getItem(i)),reason+"; slot "+i);
    }
    private static boolean learn(FakePlayer p,String id) { inputs(p,id);return Progression.learn(p,id); }

    @GameTest(template="empty")
    public static void itemAndExperiencePaymentIsAtomicAndLegacyKnowledgeNeverCharges(GameTestHelper h) {
        FakePlayer p=player(h);
        h.assertTrue(learn(p,Progression.bestId(0)),"The original zero-XP basic materials still unlock basic engineering");
        String drill=Progression.bestId(1);inputs(p,drill);p.experienceLevel=0;
        var before=snapshot(p);
        h.assertTrue(!BestEngineering.perform(p,1),"Legacy direct calls cannot bypass the new XP price");
        unchanged(h,p,before,0,"Insufficient XP leaves all inputs untouched");
        p.experienceLevel=7;p.experienceProgress=0.375f;
        p.getInventory().setItem(1,ItemStack.EMPTY);before=snapshot(p);
        h.assertTrue(!BestEngineering.perform(p,1),"Missing the second material cannot partially charge the first");
        unchanged(h,p,before,7,"Missing items preserve XP and earlier ingredients");
        inputs(p,drill);
        ItemStack dirty=p.getInventory().getItem(0);
        dirty.set(CatalogueContent.QUALITY.get(),new MaterialQuality(100,100,0,1,false,"test"));before=snapshot(p);
        h.assertTrue(!BestEngineering.perform(p,1),"Contaminated costs never count as usable materials");
        unchanged(h,p,before,7,"Dirty material rejection is atomic");
        dirty.set(CatalogueContent.QUALITY.get(),new MaterialQuality(100,100,0,0,false,"test"));
        dirty.set(StarfallBlueprint.BATCH.get(),new MaterialBatch("meteor_phase",10000,9000,100,1,"granules","common"));
        before=snapshot(p);
        h.assertTrue(!BestEngineering.perform(p,1),"A clean quality component cannot hide dirty legacy batch data");
        unchanged(h,p,before,7,"Conflicting quality data cannot bypass material cleanliness");
        inputs(p,drill);
        h.assertTrue(BestEngineering.perform(p,1)&&p.experienceLevel==6&&p.getInventory().isEmpty()
                &&p.experienceProgress==0.375f,"Exact inputs and exactly one level are committed together, retaining fractional XP");
        inputs(p,drill);before=snapshot(p);
        h.assertTrue(!BestEngineering.perform(p,1),"Duplicate legacy calls do not charge again");
        unchanged(h,p,before,6,"Learned knowledge cannot be purchased twice");
        p.getPersistentData().putInt("BestEngineeringResearch",3);
        p.getPersistentData().putBoolean("BestLightMobility",true);p.getPersistentData().putBoolean("BestDiagnostics",true);
        p.getPersistentData().putBoolean("BestMechOperation",true);p.getPersistentData().putInt("LingchuanResearch",65535);
        for(var node:Progression.NODES) {
            h.assertTrue(Progression.isLearned(p,node.id()),"Old saves read through every original unlock: "+node.id());
            h.assertTrue(!Progression.learn(p,node.id()),"Old knowledge never recharges: "+node.id());
        }
        unchanged(h,p,before,6,"Reading and retrying migrated knowledge changes no inventory or XP");
        FakePlayer creative=player(h);creative.setGameMode(GameType.CREATIVE);creative.experienceLevel=0;
        h.assertTrue(!Progression.learn(creative,Progression.lingchuanId(0))&&!Lingchuan.knows(creative,0),
                "Creative mode grants neither free XP nor materials");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void bothTreesKeepOrderPersonalKnowledgeAndRegisteredClonePersistence(GameTestHelper h) {
        FakePlayer p=player(h),other=player(h),replacement=player(h);
        h.assertTrue(Progression.NODES.size()==22,"The shared catalog contains six Best and sixteen Lingchuan nodes");
        inputs(p,Progression.bestId(17));var before=snapshot(p);
        h.assertTrue(!Progression.learn(p,Progression.bestId(17)),"Mech operation cannot precede its three engineering prerequisites");
        unchanged(h,p,before,100,"Out-of-order mech learning is atomic");
        for(int action:new int[]{0,1,5,8,13,17})h.assertTrue(learn(p,Progression.bestId(action)),"Best chain learns action "+action);
        h.assertTrue(p.experienceLevel==88&&BestEngineering.hasMechOperation(p),"The full Best tree costs twelve levels and unlocks driving");
        inputs(p,Progression.lingchuanId(0));
        h.assertTrue(Lingchuan.research(p,null,0,false)&&p.experienceLevel==87&&p.getInventory().isEmpty(),
                "The legacy energy-route flag delegates to the unified material plus XP transaction without a station");
        h.assertTrue(learn(p,Progression.lingchuanId(1)),"Lingchuan bootstraps with common crystal then alloy");
        // Parent present but compressed-core research absent: tier requirement is independent of parent.
        p.getPersistentData().putInt("LingchuanResearch",Lingchuan.knowledge(p)|(1<<7));
        inputs(p,Progression.lingchuanId(8));before=snapshot(p);int xp=p.experienceLevel;
        h.assertTrue(!Progression.learn(p,Progression.lingchuanId(8)),"Flight requires compressed core even when its jump parent exists");
        unchanged(h,p,before,xp,"Missing tier core consumes nothing");
        h.assertTrue(learn(p,Progression.lingchuanId(2))&&learn(p,Progression.lingchuanId(8)),"The missing core then allows flight");
        for(int i=3;i<Lingchuan.RESEARCH.size();i++)if(!Lingchuan.knows(p,i))
            h.assertTrue(learn(p,Progression.lingchuanId(i)),"Remaining Lingchuan branch can learn in dependency order: "+i);
        h.assertTrue(Lingchuan.knowledge(p)==65535,"Every Lingchuan branch uses the unified engine");
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement,p,true));
        for(var node:Progression.NODES) {
            h.assertTrue(Progression.isLearned(replacement,node.id()),"Existing registered clone listeners retain "+node.id());
            h.assertTrue(!Progression.isLearned(other,node.id()),"Knowledge belongs to its player: "+node.id());
        }
        h.assertTrue(replacement.getInventory().isEmpty(),"Cloning knowledge grants no inventory rewards");h.succeed();
    }

    @GameTest(template="empty")
    public static void actualMenuRejectsForgedRepeatedWrongOwnerAndClosedRequests(GameTestHelper h) {
        FakePlayer p=player(h),other=player(h);
        ProgressionMenu menu=new ProgressionMenu(17,p.getInventory());
        String basic=Progression.bestId(0);inputs(p,basic);var before=snapshot(p);
        h.assertTrue(menu.slots.size()==36&&menu.stillValid(p),"Personal menu exposes the actual 36 player inventory slots without a station");
        for(var slot:menu.slots)h.assertTrue(slot.container==p.getInventory(),"All menu slots are owned inventory slots");
        h.assertTrue(!menu.clickMenuButton(p,index(basic)),"A constructed but noncurrent menu cannot execute learning");
        p.containerMenu=menu;
        h.assertTrue(!menu.clickMenuButton(p,-1)&&!menu.clickMenuButton(p,Integer.MAX_VALUE)
                &&!menu.clickMenuButton(other,index(basic)),"Forged IDs and a different owner are rejected");
        unchanged(h,p,before,100,"Forged requests mutate nothing");
        h.assertTrue(menu.clickMenuButton(p,index(basic))&&menu.isLearned(basic)&&menu.xpLevels()==100,
                "A current real menu learns and exposes authoritative synchronized state");
        inputs(p,basic);before=snapshot(p);
        h.assertTrue(!menu.clickMenuButton(p,index(basic)),"Repeated current-menu requests cannot double charge");
        unchanged(h,p,before,100,"Repeated menu request consumes nothing");
        String drill=Progression.bestId(1);inputs(p,drill);before=snapshot(p);
        p.setGameMode(GameType.SPECTATOR);
        h.assertTrue(!menu.stillValid(p)&&!menu.clickMenuButton(p,index(drill)),"Spectators cannot transact through an old menu");
        p.setGameMode(GameType.SURVIVAL);p.setHealth(0);
        h.assertTrue(!menu.stillValid(p)&&!menu.clickMenuButton(p,index(drill)),"Dead players cannot transact");
        p.setHealth(p.getMaxHealth());
        AbstractContainerMenu unrelated=new PreparationMenu(18,p.getInventory());p.containerMenu=unrelated;
        Progression.open(p);
        h.assertTrue(p.containerMenu==unrelated&&!menu.clickMenuButton(p,index(drill)),"Opening preserves unrelated active containers and rejects stale requests");
        p.containerMenu=menu;menu.removed(p);p.containerMenu=p.inventoryMenu;
        h.assertTrue(!menu.stillValid(p)&&!menu.clickMenuButton(p,index(drill)),"A closed menu rejects subsequent requests");
        unchanged(h,p,before,100,"Spectator, dead, stale and closed requests leave inputs and XP intact");
        h.assertTrue(!BestEngineering.hasMechOperation(p)&&BestEngineering.researchLevel(p)==1,"Rejected packets grant no extra knowledge");
        h.succeed();
    }
    private ProgressionGameTests() {}
}
