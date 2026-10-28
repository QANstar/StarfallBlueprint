package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.*;

/** First Best engineering loop. Final assemblies require an actual, qualified operator. */
public final class BestEngineering {
    public static final DeferredBlock<BestWorkbenchBlock> WORKBENCH=StarfallBlueprint.BLOCKS.register("best_workbench",()->new BestWorkbenchBlock(BlockBehaviour.Properties.of().strength(3f).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> WORKBENCH_ITEM=StarfallBlueprint.ITEMS.registerSimpleBlockItem(WORKBENCH);
    public static final DeferredHolder<MenuType<?>,MenuType<BestWorkbenchMenu>> MENU=StarfallBlueprint.MENUS.register("best_workbench",()->new MenuType<>(BestWorkbenchMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    private static final String RESEARCH="BestEngineeringResearch";
    private static final ResourceLocation ASSIST=StarfallBlueprint.id("best_walking_assist");
    public static void initialize(){}
    public static int researchLevel(Player player){return Math.clamp(player.getPersistentData().getInt(RESEARCH),0,2);}
    public static void clonePlayer(PlayerEvent.Clone event){event.getEntity().getPersistentData().putInt(RESEARCH,researchLevel(event.getOriginal()));}
    public static String actionLabel(int action){return switch(action){case 0->"学习：基础工务";case 1->"学习：工程钻切";case 2->"装配轻型助力骨架";case 3->"装配工程钻切器";case 4->"维修主手装备";default->"未知操作";};}
    public static String actionCost(int action){return switch(action){case 0->"普通钢×2、基础控制板×1";case 1->"承相结构嵌材×1、导相铜复材板×1；需基础工务";case 2->"普通钢×4、承相结构嵌材×2、基础控制板×1、钢制固定件×2";case 3->"普通钢×3、导相铜复材板×2、钻石磨头×1、基础控制板×1";case 4->"普通钢×1，恢复主手装备200点工务耐久";default->"";};}
    private static Map<String,Integer> cost(int action){return switch(action){case 0->Map.of("steel",2,"board",1);case 1->Map.of("load",1,"conductor",1);case 2->Map.of("steel",4,"load",2,"board",1,"steel_fix",2);case 3->Map.of("steel",3,"conductor",2,"head",1,"board",1);case 4->Map.of("steel",1);default->Map.of();};}
    private static boolean fail(Player player,String text){player.displayClientMessage(Component.literal(text),true);return false;}
    /** Copy, validate and commit the complete inventory transaction; failure never eats inputs. */
    public static boolean perform(Player player,int action){
        if(player.level().isClientSide||action<0||action>4)return false;
        int learned=researchLevel(player);
        if(action<2&&learned>=action+1)return fail(player,"这项工务知识已经掌握");
        int required=switch(action){case 1,2,4->1;case 3->2;default->0;};
        if(learned<required)return fail(player,"先学习对应的工务知识");
        ItemStack held=player.getMainHandItem();
        if(action==4&&(!BestEquipment.isEquipment(held)||BestEquipment.wear(held)==0))return fail(player,"请脱下需要维修的骨架或钻切器，并持于主手");
        if(action==4&&held.is(CatalogueContent.item("best_cutter"))&&learned<2)return fail(player,"先学习工程钻切，再维修钻切器");
        List<ItemStack> plan=new ArrayList<>();for(int i=0;i<36;i++)plan.add(player.getInventory().getItem(i).copy());
        for(var input:cost(action).entrySet()){
            int need=input.getValue();
            for(ItemStack stack:plan)if(CatalogueContent.matches(input.getKey(),stack)&&usableMaterial(stack)){
                int n=Math.min(need,stack.getCount());stack.shrink(n);need-=n;if(need==0)break;
            }
            if(need>0)return fail(player,"材料不足或晶材受污染："+actionCost(action));
        }
        if(action==2||action==3){
            ItemStack out=CatalogueContent.stack(action==2?"best_frame":"best_cutter",1);
            int free=-1;for(int i=0;i<36;i++)if(plan.get(i).isEmpty()){free=i;break;}
            if(free<0)return fail(player,"背包需腾出一格存放装配成品");
            plan.set(free,out);
        }
        if(action==4){ItemStack repaired=plan.get(player.getInventory().selected);BestEquipment.setWear(repaired,Math.max(0,BestEquipment.wear(repaired)-200));}
        for(int i=0;i<36;i++)player.getInventory().setItem(i,plan.get(i));
        if(action<2)player.getPersistentData().putInt(RESEARCH,action+1);
        player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.literal(action<2?"已掌握："+actionLabel(action).replace("学习：",""):action==4?"维修完成；电量与温度保持原状":"装配完成；请用已充电的工具能匣补能"),true);
        return true;
    }
    private static boolean usableMaterial(ItemStack stack){
        if(stack.isDamageableItem()&&stack.getDamageValue()>=stack.getMaxDamage())return false;
        return !stack.has(CatalogueContent.QUALITY.get())||MaterialQuality.of(stack).contamination()==0;
    }
    public static void playerTick(PlayerTickEvent.Post event){tickPlayer(event.getEntity());}
    public static void tickPlayer(Player player){
        if(player.level().isClientSide)return;
        ItemStack frame=player.getItemBySlot(EquipmentSlot.LEGS);
        boolean assist=frame.is(CatalogueContent.item("best_frame"))&&researchLevel(player)>=1&&player.isSprinting()&&player.onGround()&&!player.isInWater()&&!player.isPassenger()&&!player.getAbilities().flying&&BestEquipment.ready(frame,16);
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);
        if(assist){
            BestEquipment.spend(frame,16,6);
            var data=CatalogueEquipment.getData(frame);int ticks=data.getInt("BestWorkTicks")+1;
            if(ticks>=100){ticks=0;BestEquipment.setWear(frame,BestEquipment.wear(frame)+1);data=CatalogueEquipment.getData(frame);}
            data.putInt("BestWorkTicks",ticks);CatalogueEquipment.setData(frame,data);
            if(speed!=null&&!speed.hasModifier(ASSIST))speed.addTransientModifier(new AttributeModifier(ASSIST,0.15,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }else if(speed!=null)speed.removeModifier(ASSIST);
        if(player.tickCount%20==0){
            ItemStack shown=BestEquipment.isEquipment(player.getMainHandItem())?player.getMainHandItem():frame;
            if(BestEquipment.isEquipment(shown))player.displayClientMessage(Component.literal(BestEquipment.status(shown)+(researchLevel(player)<(shown.is(CatalogueContent.item("best_cutter"))?2:1)?" · 尚未学习":"")),true);
        }
    }
    public static void breakSpeed(PlayerEvent.BreakSpeed event){
        var player=event.getEntity();var stack=player.getMainHandItem();
        if(stack.is(CatalogueContent.item("best_cutter"))&&(researchLevel(player)<2||!BestEquipment.ready(stack,256)))event.setNewSpeed(0);
    }
    public static void breakBlock(BlockEvent.BreakEvent event){
        var player=event.getPlayer();if(player==null)return;
        if(player.getMainHandItem().is(CatalogueContent.item("best_cutter"))&&(researchLevel(player)<2||!BestEquipment.ready(player.getMainHandItem(),256))){event.setCanceled(true);fail(player,"钻切器未就绪：请检查学习、电量、温度与耐久");}
    }
}
