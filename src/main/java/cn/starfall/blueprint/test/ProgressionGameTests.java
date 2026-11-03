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
        p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.experienceLevel=100;p.totalExperience=0;p.experienceProgress=0;
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
    static FakePlayer expectedXp(GameTestHelper h,net.minecraft.world.entity.player.Player actual,int costLevels,int rewardPoints) {
        FakePlayer expected=player(h);
        expected.experienceLevel=actual.experienceLevel;expected.totalExperience=actual.totalExperience;
        expected.experienceProgress=actual.experienceProgress;
        expected.giveExperienceLevels(-costLevels);expected.giveExperiencePoints(rewardPoints);return expected;
    }
    static void sameXp(GameTestHelper h,net.minecraft.world.entity.player.Player actual,net.minecraft.world.entity.player.Player expected,String reason) {
        h.assertTrue(actual.experienceLevel==expected.experienceLevel&&actual.totalExperience==expected.totalExperience
                &&Float.compare(actual.experienceProgress,expected.experienceProgress)==0,reason);
    }
    private static int count(FakePlayer p,String key) {
        return p.getInventory().items.stream().filter(s->CatalogueContent.matches(key,s)).mapToInt(ItemStack::getCount).sum();
    }
    private static void rewardOnly(GameTestHelper h,FakePlayer p,Progression.Reward reward,String reason) {
        int total=0;
        for(var item:reward.items()) {
            h.assertTrue(count(p,item.key())==item.count(),reason+"; exact "+item.key());total+=item.count();
        }
        h.assertTrue(p.getInventory().items.stream().mapToInt(ItemStack::getCount).sum()==total,reason+"; no extra items");
        for(var stack:p.getInventory().items)if(!stack.isEmpty()) {
            h.assertTrue(stack.getCount()<=stack.getMaxStackSize(),reason+"; legal stack size");
            h.assertTrue(reward.items().stream().anyMatch(item->ItemStack.isSameItemSameComponents(stack,CatalogueContent.stack(item.key(),1))),reason+"; exact components");
        }
    }

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
        var expected=expectedXp(h,p,1,10);
        h.assertTrue(BestEngineering.perform(p,1),"Exact inputs and the one-level price commit together with drilling rewards");
        sameXp(h,p,expected,"Drilling deducts one level then awards exactly ten points using vanilla XP arithmetic");
        rewardOnly(h,p,Progression.NODES.get(index(drill)).reward(),"Drilling leaves only its reward");
        inputs(p,drill);before=snapshot(p);
        h.assertTrue(!BestEngineering.perform(p,1),"Duplicate legacy calls do not charge again");
        unchanged(h,p,before,expected.experienceLevel,"Learned knowledge cannot be purchased twice");
        p.getPersistentData().putInt("BestEngineeringResearch",3);
        p.getPersistentData().putBoolean("BestLightMobility",true);p.getPersistentData().putBoolean("BestDiagnostics",true);
        p.getPersistentData().putBoolean("BestMechOperation",true);p.getPersistentData().putInt("LingchuanResearch",65535);
        for(var node:Progression.NODES) {
            h.assertTrue(Progression.isLearned(p,node.id()),"Old saves read through every original unlock: "+node.id());
            h.assertTrue(!Progression.learn(p,node.id()),"Old knowledge never recharges: "+node.id());
        }
        unchanged(h,p,before,expected.experienceLevel,"Reading and retrying migrated knowledge changes no inventory or XP");
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
        FakePlayer expected=expectedXp(h,p,0,0);
        for(int action:new int[]{0,1,5,8,13,17}) {
            var node=Progression.NODES.get(index(Progression.bestId(action)));
            expected.giveExperienceLevels(-node.experienceLevels());expected.giveExperiencePoints(node.reward().experiencePoints());
            h.assertTrue(learn(p,node.id()),"Best chain learns action "+action);
        }
        sameXp(h,p,expected,"The full Best tree pays twelve levels and awards its exact points");
        h.assertTrue(BestEngineering.hasMechOperation(p),"The full Best tree unlocks driving");
        inputs(p,Progression.lingchuanId(0));expected=expectedXp(h,p,1,10);
        h.assertTrue(Lingchuan.research(p,null,0,false),
                "The legacy energy-route flag delegates to the unified material plus XP transaction without a station");
        sameXp(h,p,expected,"Legacy energy route pays one level then grants ten points");
        rewardOnly(h,p,Progression.NODES.get(index(Progression.lingchuanId(0))).reward(),"Legacy route delivers its alloy");
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
        var expected=expectedXp(h,p,0,5);
        h.assertTrue(menu.clickMenuButton(p,index(basic))&&menu.isLearned(basic)&&menu.xpLevels()==expected.experienceLevel,
                "A current real menu learns and exposes authoritative synchronized state");
        sameXp(h,p,expected,"The real menu awards exactly five points");
        rewardOnly(h,p,Progression.NODES.get(index(basic)).reward(),"The real menu inserts the exact basic reward");
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
        unchanged(h,p,before,expected.experienceLevel,"Spectator, dead, stale and closed requests leave inputs and XP intact");
        sameXp(h,p,expected,"Rejected menu requests award no points");
        h.assertTrue(!BestEngineering.hasMechOperation(p)&&BestEngineering.researchLevel(p)==1,"Rejected packets grant no extra knowledge");
        h.succeed();
    }
    private static void allKnowledge(FakePlayer p) {
        p.getPersistentData().putInt("BestEngineeringResearch",3);
        p.getPersistentData().putBoolean("BestLightMobility",true);
        p.getPersistentData().putBoolean("BestDiagnostics",true);
        p.getPersistentData().putBoolean("BestMechOperation",true);
        p.getPersistentData().putInt("LingchuanResearch",65535);
    }
    private static Progression.Reward reward(int xp,Progression.Cost... items) {
        return new Progression.Reward(List.of(items),xp);
    }

    @GameTest(template="empty")
    public static void allNodeRewardsPayExactItemsAndPointsOnceAcrossLegacyAndCloneRoutes(GameTestHelper h) {
        int[] points={5,10,20,15,20,50,10,20,35,60,10,10,10,10,35,20,20,35,20,20,35,35};
        int[] actions={0,1,5,8,13,17};
        h.assertTrue(Progression.NODES.size()==points.length,"Every one of the 22 nodes has an explicit reward policy");
        var mutable=new ArrayList<Progression.Cost>();mutable.add(new Progression.Cost("iron",1));
        var immutable=new Progression.Reward(mutable,0);mutable.clear();
        h.assertTrue(immutable.items().size()==1&&Progression.Reward.NONE.items().isEmpty()
                &&Progression.Reward.NONE.experiencePoints()==0,"Reward owns an immutable copy and NONE grants nothing");
        boolean negativeRejected=false;
        try { new Progression.Reward(List.of(),-1); } catch(IllegalArgumentException expected) { negativeRejected=true; }
        h.assertTrue(negativeRejected,"Negative reward points are rejected");
        var first=Progression.NODES.getFirst();
        var compatible=new Progression.Node("test:compatibility",first.category(),first.title(),first.description(),List.of(),List.of(),0,
                first.iconFactory(),first.learned(),first.grant());
        h.assertTrue(compatible.reward().equals(Progression.Reward.NONE),"The original ten-argument Node constructor defaults to no reward");
        for(int n=0;n<points.length;n++) {
            var node=Progression.NODES.get(n);
            Progression.Reward policy=switch(n) {
                case 0 -> reward(points[n],new Progression.Cost("iron",4));
                case 1 -> reward(points[n],new Progression.Cost("steel",2));
                case 2 -> reward(points[n],new Progression.Cost("best_service_kit",1));
                case 3 -> reward(points[n],new Progression.Cost("steel_fix",2));
                case 4 -> reward(points[n],new Progression.Cost("best_diagnostic_tool",1));
                case 5 -> reward(points[n],new Progression.Cost("steel",8),new Progression.Cost("steel_fix",4));
                case 6 -> reward(points[n],new Progression.Cost("laminar_alloy",1));
                case 7 -> reward(points[n],new Progression.Cost("laminar_alloy",2));
                case 8,14 -> reward(points[n],new Progression.Cost("conductor",2));
                case 9 -> reward(points[n],new Progression.Cost("sheath",4));
                default -> reward(points[n]);
            };
            h.assertTrue(node.reward().equals(policy),"Exact item and point policy: "+node.id());
            FakePlayer p=player(h);allKnowledge(p);p.experienceLevel=10;p.totalExperience=731;p.experienceProgress=.91f;
            switch(n) {
                case 0 -> p.getPersistentData().putInt("BestEngineeringResearch",0);
                case 1 -> p.getPersistentData().putInt("BestEngineeringResearch",1);
                case 2 -> p.getPersistentData().putInt("BestEngineeringResearch",2);
                case 3 -> p.getPersistentData().putBoolean("BestLightMobility",false);
                case 4 -> p.getPersistentData().putBoolean("BestDiagnostics",false);
                case 5 -> p.getPersistentData().putBoolean("BestMechOperation",false);
                default -> p.getPersistentData().putInt("LingchuanResearch",65535^(1<<(n-6)));
            }
            inputs(p,node.id());var before=snapshot(p);var unchangedXp=expectedXp(h,p,0,0);
            for(int simulation=0;simulation<2;simulation++)h.assertTrue(Progression.preview(p.getInventory(),node.id(),Progression.learned(p),p.experienceLevel).executable(),"Reward preview is executable: "+node.id());
            unchanged(h,p,before,unchangedXp.experienceLevel,"Successful simulation is read only");sameXp(h,p,unchangedXp,"Simulation preserves every XP field");
            var expected=expectedXp(h,p,node.experienceLevels(),points[n]);
            boolean learned=n<6?BestEngineering.perform(p,actions[n]):Lingchuan.research(p,null,n-6,n%2==0);
            h.assertTrue(learned&&Progression.isLearned(p,node.id()),"Legacy route commits knowledge: "+node.id());
            rewardOnly(h,p,policy,"Exact paid reward with all exact costs consumed: "+node.id());
            sameXp(h,p,expected,"Reward points match giveExperiencePoints including level crossings: "+node.id());
            before=snapshot(p);
            h.assertTrue(!Progression.learn(p,node.id())&&!(n<6?BestEngineering.perform(p,actions[n]):Lingchuan.research(p,null,n-6,n%2!=0)),"Both direct and legacy repeats reject: "+node.id());
            unchanged(h,p,before,expected.experienceLevel,"Repeat preserves reward items");sameXp(h,p,expected,"Repeat awards no XP");
        }
        FakePlayer old=player(h),replacement=player(h);allKnowledge(old);
        var oldXp=expectedXp(h,old,0,0);var oldItems=snapshot(old);
        for(int n=0;n<points.length;n++) {
            h.assertTrue(!Progression.learn(old,Progression.NODES.get(n).id()),"Old learned flags have no retroactive claim");
            h.assertTrue(!(n<6?BestEngineering.perform(old,actions[n]):Lingchuan.research(old,null,n-6,true)),"Old flags reject the legacy claim route too");
        }
        unchanged(h,old,oldItems,oldXp.experienceLevel,"Old knowledge grants no items");sameXp(h,old,oldXp,"Old knowledge grants no XP");
        var cloneXp=expectedXp(h,replacement,0,0);var cloneItems=snapshot(replacement);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement,old,true));
        for(var node:Progression.NODES)h.assertTrue(Progression.isLearned(replacement,node.id())&&!Progression.learn(replacement,node.id()),"Cloned knowledge cannot replay rewards: "+node.id());
        unchanged(h,replacement,cloneItems,cloneXp.experienceLevel,"Clone grants no items");sameXp(h,replacement,cloneXp,"Clone grants no XP");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void rewardSpacePlansMergeBoundariesComponentsAndFreedCostSlotsAtomically(GameTestHelper h) {
        String basic=Progression.bestId(0);
        for(int scenario=0;scenario<5;scenario++) {
            FakePlayer p=player(h);p.experienceLevel=1;p.totalExperience=11;p.experienceProgress=.75f;
            for(int slot=0;slot<36;slot++)p.getInventory().setItem(slot,new ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64));
            p.getInventory().setItem(0,CatalogueContent.stack("steel",scenario==3?2:3));
            p.getInventory().setItem(1,CatalogueContent.stack("board",2));
            if(scenario!=3)p.getInventory().setItem(35,CatalogueContent.stack("iron",scenario==1?61:scenario==4?64:60));
            if(scenario==2)p.getInventory().getItem(35).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Distinct components"));
            var before=snapshot(p);var xp=expectedXp(h,p,0,0);var knowledge=Progression.learned(p);
            boolean fits=scenario==0||scenario==3;
            for(int repeat=0;repeat<2;repeat++) {
                var preview=Progression.preview(p.getInventory(),basic,knowledge,p.experienceLevel);
                h.assertTrue(preview.status()==(fits?Progression.Status.READY:Progression.Status.REWARD_SPACE),"Exact merge/free-slot boundary: "+scenario);
                if(!fits)h.assertTrue(preview.reason().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                        &&text.getKey().equals("growth.starfall_blueprint.reward_space"),"No-space reason is translated");
            }
            unchanged(h,p,before,xp.experienceLevel,"Space preview changes no inventory");sameXp(h,p,xp,"Space preview changes no XP");
            h.assertTrue(Progression.learn(p,basic)==fits,"Commit agrees with space plan: "+scenario);
            if(fits) {
                var expected=expectedXp(h,xp,0,5);sameXp(h,p,expected,"Successful space plan grants five exact points");
                h.assertTrue(count(p,"steel")== (scenario==3?0:1)&&count(p,"board")==1,"Costs retain exact paid counts");
                h.assertTrue(count(p,"iron")== (scenario==3?4:64),"Reward fills exact compatible capacity or the consumed cost slot");
                if(scenario==3)h.assertTrue(CatalogueContent.matches("iron",p.getInventory().getItem(0)),"Consumption frees the actual cost slot for rewards");
            } else {
                unchanged(h,p,before,xp.experienceLevel,"Failed reward space preserves all materials and reward stacks");sameXp(h,p,xp,"Failed reward space preserves every XP field");
                h.assertTrue(Progression.learned(p).equals(knowledge),"Failed reward space grants no knowledge");
            }
        }
        FakePlayer partial=player(h);allKnowledge(partial);partial.getPersistentData().putBoolean("BestMechOperation",false);
        for(int slot=0;slot<36;slot++)partial.getInventory().setItem(slot,new ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64));
        var mech=Progression.NODES.get(index(Progression.bestId(17)));int slot=0;
        for(var cost:mech.costs())partial.getInventory().setItem(slot++,CatalogueContent.stack(cost.key(),cost.count()+1));
        partial.getInventory().setItem(35,CatalogueContent.stack("steel",56));
        var before=snapshot(partial);var xp=expectedXp(h,partial,0,0);var knowledge=Progression.learned(partial);
        h.assertTrue(Progression.preview(partial.getInventory(),mech.id(),knowledge,partial.experienceLevel).status()==Progression.Status.REWARD_SPACE
                &&!Progression.learn(partial,mech.id()),"Second reward failing capacity rolls back the already planned first reward");
        unchanged(h,partial,before,xp.experienceLevel,"Multiple rewards remain one inventory transaction");sameXp(h,partial,xp,"Multiple reward failure preserves XP");
        h.assertTrue(Progression.learned(partial).equals(knowledge),"Multiple reward failure preserves knowledge");
        FakePlayer mergeFirst=player(h);inputs(mergeFirst,basic);mergeFirst.getInventory().setItem(35,CatalogueContent.stack("iron",60));
        h.assertTrue(Progression.learn(mergeFirst,basic)&&mergeFirst.getInventory().getItem(0).isEmpty()
                &&mergeFirst.getInventory().getItem(1).isEmpty()&&mergeFirst.getInventory().getItem(35).getCount()==64,"Existing compatible stacks are filled before earlier empty slots");
        h.succeed();
    }
    private ProgressionGameTests() {}
}
