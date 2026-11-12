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
        p.getPersistentData().remove("LingchuanResearch");p.getPersistentData().remove("BestModuleKnowledge");return p;
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
    public static void itemAndExperiencePaymentIsAtomic(GameTestHelper h) {
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
        FakePlayer creative=player(h);creative.setGameMode(GameType.CREATIVE);creative.experienceLevel=0;
        h.assertTrue(!Progression.learn(creative,Progression.lingchuanId(0))&&!Lingchuan.knows(creative,0),
                "Creative mode grants neither free XP nor materials");
        h.succeed();
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
        var modules=new net.minecraft.nbt.CompoundTag();for(String key:BestMechAssembly.KNOWLEDGE)modules.putBoolean(key,true);p.getPersistentData().put("BestModuleKnowledge",modules);
    }
    private static Progression.Reward reward(int xp,Progression.Cost... items) {
        return new Progression.Reward(List.of(items),xp);
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
