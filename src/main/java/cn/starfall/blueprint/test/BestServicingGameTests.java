package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.gametest.*;

/** Targeted checks for the new service transactions and their failure boundaries. */
@GameTestHolder("starfall_blueprint_servicing")
@PrefixGameTestTemplate(false)
public final class BestServicingGameTests {
    private static FakePlayer player(GameTestHelper h, int level) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"BestServicing"));
        p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getInventory().selected=0;
        p.getPersistentData().putInt("BestEngineeringResearch",level);
        p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
        return p;
    }
    private static void input(FakePlayer p,int slot,String key,int count){p.getInventory().setItem(slot,CatalogueContent.stack(key,count));}
    private static int count(FakePlayer p,Item item){return p.getInventory().items.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}
    private static ItemStack target(int heat,int wear){
        var s=CatalogueContent.stack("best_cutter",1);var d=CatalogueEquipment.getData(s);
        d.putInt("BestEnergy",12345);d.putInt("BestHeat",heat);d.putInt("BestWear",wear);d.putBoolean("BestOverheated",heat>400);
        d.putString("ServiceSentinel","preserve");CatalogueEquipment.setData(s,d);return s;
    }
    private static void clear(FakePlayer p){p.getInventory().clearContent();p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);}

    @GameTest(template="empty")
    public static void fieldResearchAndManualAssemblyArePersonalAndAtomic(GameTestHelper h){
        var p=player(h,1);
        try{
            input(p,1,"load",2);input(p,2,"conductor",1);input(p,3,"board",1);
            h.assertTrue(!BestEngineering.perform(p,5)&&count(p,CatalogueContent.item("load"))==2,"Field servicing cannot skip drill research or consume its materials");
            p.getPersistentData().putInt("BestEngineeringResearch",2);
            h.assertTrue(BestEngineering.perform(p,5)&&BestEngineering.researchLevel(p)==3,"The third research level unlocks through its actual material transaction");
            input(p,1,"load",2);input(p,2,"conductor",1);input(p,3,"board",1);
            h.assertTrue(!BestEngineering.perform(p,5)&&count(p,CatalogueContent.item("board"))==1,"Repeated field research never charges again");
            for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
            input(p,1,"steel",4);input(p,2,"steel_fix",3);input(p,3,"sheath",2);input(p,4,"board",2);
            h.assertTrue(!BestEngineering.perform(p,6)&&count(p,CatalogueContent.item("steel"))==4,"A full inventory rejects assembly without consuming any input");
            p.getInventory().setItem(5,ItemStack.EMPTY);
            h.assertTrue(BestEngineering.perform(p,6)&&count(p,CatalogueContent.item("best_service_kit"))==1&&count(p,CatalogueContent.item("steel"))==1,"Manual assembly reserves a real output slot and consumes exactly three steel");
        }finally{clear(p);}h.succeed();
    }

    @GameTest(template="empty")
    public static void waterCoolingReturnsContainerAndPreservesEquipment(GameTestHelper h){
        var p=player(h,2);
        try{
            var s=target(800,321);p.setItemInHand(InteractionHand.MAIN_HAND,s);p.getInventory().setItem(1,new ItemStack(Items.WATER_BUCKET));
            h.assertTrue(BestEngineering.perform(p,7),"A qualified operator can cool the main-hand cutter");
            s=p.getMainHandItem();
            h.assertTrue(BestEquipment.heat(s)==400&&!CatalogueEquipment.getData(s).getBoolean("BestOverheated"),"Cooling subtracts 400 and unlocks at the recovery threshold");
            h.assertTrue(BestEquipment.charge(s)==12345&&BestEquipment.wear(s)==321&&CatalogueEquipment.getData(s).getString("ServiceSentinel").equals("preserve"),"Water cooling never repairs, charges or strips custom data");
            h.assertTrue(count(p,Items.WATER_BUCKET)==0&&count(p,Items.BUCKET)==1,"One real water bucket becomes one empty bucket");
            p.setItemInHand(InteractionHand.MAIN_HAND,target(0,321));p.getInventory().setItem(1,new ItemStack(Items.WATER_BUCKET));
            h.assertTrue(!BestEngineering.perform(p,7)&&count(p,Items.WATER_BUCKET)==1,"Already cold equipment consumes no water");
            p.setItemInHand(InteractionHand.MAIN_HAND,target(900,321));p.getPersistentData().putInt("BestEngineeringResearch",1);
            h.assertTrue(!BestEngineering.perform(p,7)&&count(p,Items.WATER_BUCKET)==1,"A frame-only operator cannot service a cutter");
        }finally{clear(p);}h.succeed();
    }

    @GameTest(template="empty")
    public static void portableServicingUsesSteelAndKitWithoutChangingHeatOrEnergy(GameTestHelper h){
        var p=player(h,2);
        try{
            var kit=CatalogueContent.stack("best_service_kit",1);p.setItemInHand(InteractionHand.MAIN_HAND,kit);
            p.setItemInHand(InteractionHand.OFF_HAND,target(700,350));input(p,1,"steel",2);
            kit.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
            h.assertTrue(BestEquipment.wear(p.getOffhandItem())==350&&kit.getDamageValue()==0&&count(p,CatalogueContent.item("steel"))==2,"Unlearned operators consume neither kit nor steel");
            p.getPersistentData().putInt("BestEngineeringResearch",3);
            kit.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
            kit=p.getMainHandItem();
            h.assertTrue(BestEquipment.wear(p.getOffhandItem())==150&&kit.getDamageValue()==1&&count(p,CatalogueContent.item("steel"))==1,"A repair restores exactly 200 wear for one steel and one kit use");
            h.assertTrue(BestEquipment.heat(p.getOffhandItem())==700&&BestEquipment.charge(p.getOffhandItem())==12345&&CatalogueEquipment.getData(p.getOffhandItem()).getBoolean("BestOverheated"),"Field servicing preserves heat, energy and the overheat lock");
            kit.setDamageValue(kit.getMaxDamage()-1);kit.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
            h.assertTrue(p.getMainHandItem().isEmpty()&&BestEquipment.wear(p.getOffhandItem())==0&&count(p,CatalogueContent.item("steel"))==0,"The final kit use completes its repair, caps wear at zero and consumes the kit");
            kit=CatalogueContent.stack("best_service_kit",1);p.setItemInHand(InteractionHand.MAIN_HAND,kit);input(p,1,"steel",1);
            kit.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
            h.assertTrue(kit.getDamageValue()==0&&count(p,CatalogueContent.item("steel"))==1,"Undamaged equipment never consumes repair supplies");
        }finally{clear(p);}h.succeed();
    }
}
