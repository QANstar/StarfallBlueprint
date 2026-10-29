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
    private static final String LIGHT_MOBILITY="BestLightMobility";
    private static final String DIAGNOSTICS="BestDiagnostics";
    private static final ResourceLocation ASSIST=StarfallBlueprint.id("best_walking_assist");
    private static final ResourceLocation STRIDE_ASSIST=StarfallBlueprint.id("best_stride_assist");
    public static void initialize(){}
    public static int researchLevel(Player player){return Math.clamp(player.getPersistentData().getInt(RESEARCH),0,3);}
    public static boolean hasLightMobility(Player player){return player.getPersistentData().getBoolean(LIGHT_MOBILITY);}
    public static boolean hasDiagnostics(Player player){return player.getPersistentData().getBoolean(DIAGNOSTICS);}
    public static void clonePlayer(PlayerEvent.Clone event){
        event.getEntity().getPersistentData().putInt(RESEARCH,researchLevel(event.getOriginal()));
        event.getEntity().getPersistentData().putBoolean(LIGHT_MOBILITY,hasLightMobility(event.getOriginal()));
        event.getEntity().getPersistentData().putBoolean(DIAGNOSTICS,hasDiagnostics(event.getOriginal()));
    }
    public static Component actionLabelComponent(int action){return action>=13&&action<=16?Component.translatable("message.starfall_blueprint.best_action_"+action):Component.literal(actionLabel(action));}
    public static Component actionCostComponent(int action){return action>=13&&action<=16?Component.translatable("message.starfall_blueprint."+switch(action){case 13->"best_diagnostics_cost";case 14->"best_diagnostic_tool_cost";case 15->"best_isolate_cost";default->"best_connect_cost";}):Component.literal(actionCost(action));}
    public static String actionLabel(int action){if(action>=13&&action<=16)return actionLabelComponent(action).getString();return switch(action){case 0->"学习：基础工务";case 1->"学习：工程钻切";case 2->"装配轻型助力骨架";case 3->"装配工程钻切器";case 4->"维修主手装备";case 5->"学习：野外整备";case 6->"装配便携整备包";case 7->"冷却主手装备";case 8->"学习：轻载通行";case 9->"装配跨步组件";case 10->"安装主手骨架组件";case 11->"切换主手骨架跨步档";case 12->"拆回主手骨架组件";default->"未知操作";};}
    public static String actionCost(int action){if(action>=13&&action<=16)return actionCostComponent(action).getString();return switch(action){case 0->"普通钢×2、基础控制板×1";case 1->"承相结构嵌材×1、导相铜复材板×1；需基础工务";case 2->"普通钢×4、承相结构嵌材×2、基础控制板×1、钢制固定件×2";case 3->"普通钢×3、导相铜复材板×2、钻石磨头×1、基础控制板×1";case 4->"普通钢×1，恢复主手装备200点工务耐久";case 5->"承相结构嵌材×2、导相铜复材板×1、基础控制板×1；需工程钻切";case 6->"普通钢×3、钢制固定件×2、陶瓷护套×1、基础控制板×1；需野外整备";case 7->"水桶×1，返还空桶；主手装备降温400，需对应使用知识";case 8->"承相结构嵌材×2、导相铜复材板×1、基础控制板×1；需野外整备，解锁轻载组件装配、安装、切档与拆回";case 9->"普通钢×4、承相结构嵌材×2、钢制固定件×2、基础控制板×1；需本人轻载通行知识";case 10->"跨步组件×1；主手持未安装骨架，需本人轻载通行知识，安装后默认关闭";case 11->"免费；主手持已安装骨架，需本人轻载通行知识；持续地面跨步档站立也耗能";case 12->"返还跨步组件×1；主手持已安装骨架，需本人轻载通行知识及背包空间";default->"";};}
    private static Map<String,Integer> cost(int action){return switch(action){case 0->Map.of("steel",2,"board",1);case 1->Map.of("load",1,"conductor",1);case 2->Map.of("steel",4,"load",2,"board",1,"steel_fix",2);case 3->Map.of("steel",3,"conductor",2,"head",1,"board",1);case 4->Map.of("steel",1);case 5->Map.of("load",2,"conductor",1,"board",1);case 6->Map.of("steel",3,"steel_fix",2,"sheath",1,"board",1);case 7->Map.of("minecraft:water_bucket",1);case 8->Map.of("load",2,"conductor",1,"board",1);case 9->Map.of("steel",4,"load",2,"steel_fix",2,"board",1);case 10->Map.of("best_stride_module",1);case 13->Map.of("load",1,"conductor",2,"board",1);case 14->Map.of("steel",2,"conductor",1,"sensor",1,"board",1,"sheath",1);default->Map.of();};}
    private static boolean diagnosticFail(Player player,String key){player.displayClientMessage(Component.translatable("message.starfall_blueprint."+key),true);return false;}
    private static boolean fail(Player player,String text){player.displayClientMessage(Component.literal(text),true);return false;}
    /** Copy, validate and commit the complete inventory transaction; failure never eats inputs. */
    public static boolean perform(Player player,int action){
        if(player.level().isClientSide||action<0||action>16)return false;
        int learned=researchLevel(player);
        int nextLevel=switch(action){case 0->1;case 1->2;case 5->3;default->0;};
        if(nextLevel>0&&learned>=nextLevel)return fail(player,"这项工务知识已经掌握");
        if(action==8&&hasLightMobility(player))return fail(player,"这项工务知识已经掌握");
        int required=switch(action){case 1,2,4,7->1;case 3,5->2;case 6,8,9,10,11,12,13,14,15,16->3;default->0;};
        if(learned<required)return fail(player,"先学习对应的工务知识");
        if(action>=9&&action<=12&&!hasLightMobility(player))return fail(player,"先学习轻载通行，再操作组件");
        if(action==13&&hasDiagnostics(player))return fail(player,"这项工务知识已经掌握");
        if(action>=14&&!hasDiagnostics(player))return diagnosticFail(player,"best_diagnostics_required");
        ItemStack held=player.getMainHandItem();
        if(action==15||action==16){
            if(!BestEquipment.isEquipment(held))return diagnosticFail(player,"best_isolation_target");
            if(action==15&&BestEquipment.isIsolated(held))return diagnosticFail(player,"best_already_isolated");
            if(action==16&&!BestEquipment.isIsolated(held))return diagnosticFail(player,"best_already_connected");
        }
        if(action>=10&&action<=12){
            if(!held.is(CatalogueContent.item("best_frame")))return fail(player,"请脱下骨架并持于主手");
            boolean installed=BestEquipment.strideInstalled(held);
            if(action==10&&installed)return fail(player,"这具骨架已安装跨步组件");
            if(action!=10&&!installed)return fail(player,"这具骨架尚未安装跨步组件");
        }
        if(action==4&&(!BestEquipment.isEquipment(held)||BestEquipment.wear(held)==0))return fail(player,"请脱下需要维修的骨架或钻切器，并持于主手");
        if(action==7&&(!BestEquipment.isEquipment(held)||BestEquipment.heat(held)==0))return fail(player,"请将需要降温的骨架或钻切器持于主手");
        if((action==4||action==7)&&held.is(CatalogueContent.item("best_cutter"))&&learned<2)return fail(player,"先学习工程钻切，再整备钻切器");
        List<ItemStack> plan=new ArrayList<>();for(int i=0;i<36;i++)plan.add(player.getInventory().getItem(i).copy());
        for(var input:cost(action).entrySet()){
            int need=input.getValue();
            for(ItemStack stack:plan)if(CatalogueContent.matches(input.getKey(),stack)&&usableMaterial(stack)){
                int n=Math.min(need,stack.getCount());stack.shrink(n);need-=n;if(need==0)break;
            }
            if(need>0)return fail(player,"材料不足或晶材受污染："+actionCost(action));
        }
        if(action==2||action==3||action==6||action==9||action==14){
            ItemStack out=CatalogueContent.stack(action==2?"best_frame":action==3?"best_cutter":action==6?"best_service_kit":action==9?"best_stride_module":"best_diagnostic_tool",1);
            if(!insert(plan,out))return fail(player,"背包需腾出一格存放装配成品");
        }
        if(action==4){ItemStack repaired=plan.get(player.getInventory().selected);BestEquipment.setWear(repaired,Math.max(0,BestEquipment.wear(repaired)-200));}
        if(action==7){
            if(!insert(plan,new ItemStack(Items.BUCKET)))return fail(player,"背包需腾出位置存放返还的空桶");
            BestEquipment.cool(plan.get(player.getInventory().selected),400);
        }
        if(action==12&&!insert(plan,CatalogueContent.stack("best_stride_module",1)))return fail(player,"背包需腾出一格存放拆回的组件");
        if(action>=10&&action<=12){
            ItemStack changed=plan.get(player.getInventory().selected);
            var data=CatalogueEquipment.getData(changed);
            data.putBoolean("BestStrideInstalled",action!=12);
            data.putBoolean("BestStrideEnabled",action==11&&!data.getBoolean("BestStrideEnabled"));
            CatalogueEquipment.setData(changed,data);
        }
        if(action==15||action==16){
            ItemStack changed=plan.get(player.getInventory().selected);
            var data=CatalogueEquipment.getData(changed);data.putBoolean("BestBatteryIsolated",action==15);CatalogueEquipment.setData(changed,data);
        }
        for(int i=0;i<36;i++)player.getInventory().setItem(i,plan.get(i));
        if(nextLevel>0)player.getPersistentData().putInt(RESEARCH,nextLevel);
        if(action==8)player.getPersistentData().putBoolean(LIGHT_MOBILITY,true);
        if(action==13)player.getPersistentData().putBoolean(DIAGNOSTICS,true);
        player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();player.containerMenu.broadcastChanges();
        if(action>=13){player.displayClientMessage(Component.translatable("message.starfall_blueprint."+switch(action){case 13->"best_diagnostics_learned";case 14->"best_diagnostic_assembled";case 15->"best_battery_isolated";default->"best_battery_connected";}),true);return true;}
        player.displayClientMessage(Component.literal(nextLevel>0||action==8?"已掌握："+actionLabel(action).replace("学习：",""):action==4?"维修完成；电量与温度保持原状":action==7?"冷却完成；已返还空桶":action==6?"整备包装配完成；主手持包、副手持受损装备右键维修":action==9?"跨步组件装配完成":action==10?"组件安装完成；跨步档已关闭":action==11?"持续地面跨步档："+(BestEquipment.strideEnabled(player.getMainHandItem())?"开启，站立也耗能":"关闭"):action==12?"组件已拆回；骨架电热与耐久保持原状":"装配完成；请用已充电的工具能匣补能"),true);
        return true;
    }
    private static boolean insert(List<ItemStack> plan,ItemStack output){
        for(ItemStack stack:plan)if(ItemStack.isSameItemSameComponents(stack,output)&&stack.getCount()<stack.getMaxStackSize()){
            int count=Math.min(output.getCount(),stack.getMaxStackSize()-stack.getCount());stack.grow(count);output.shrink(count);if(output.isEmpty())return true;
        }
        for(int i=0;i<plan.size();i++)if(plan.get(i).isEmpty()){plan.set(i,output);return true;}
        return false;
    }
    private static boolean usableMaterial(ItemStack stack){
        if(stack.isDamageableItem()&&stack.getDamageValue()>=stack.getMaxDamage())return false;
        return !stack.has(CatalogueContent.QUALITY.get())||MaterialQuality.of(stack).contamination()==0;
    }
    public static void playerTick(PlayerTickEvent.Post event){tickPlayer(event.getEntity());}
    public static void tickPlayer(Player player){
        if(player.level().isClientSide)return;
        ItemStack frame=player.getItemBySlot(EquipmentSlot.LEGS);
        boolean strideMode=BestEquipment.strideEnabled(frame);
        boolean stride=strideMode&&hasLightMobility(player)&&researchLevel(player)>=3&&player.isAlive()&&!player.isSpectator()&&player.onGround()&&!player.isInWater()&&!player.isInLava()&&!player.isPassenger()&&!player.getAbilities().flying&&!player.isFallFlying()&&!player.isShiftKeyDown()&&!player.isSprinting()&&BestEquipment.ready(frame,24);
        var stepHeight=player.getAttribute(Attributes.STEP_HEIGHT);
        if(stride){
            BestEquipment.spend(frame,24,4);
            recordFrameWork(frame);
            if(BestEquipment.ready(frame,24)){
                if(stepHeight!=null&&!stepHeight.hasModifier(STRIDE_ASSIST))stepHeight.addTransientModifier(new AttributeModifier(STRIDE_ASSIST,0.4,AttributeModifier.Operation.ADD_VALUE));
            }else if(stepHeight!=null)stepHeight.removeModifier(STRIDE_ASSIST);
        }else if(stepHeight!=null)stepHeight.removeModifier(STRIDE_ASSIST);
        boolean assist=!strideMode&&frame.is(CatalogueContent.item("best_frame"))&&researchLevel(player)>=1&&player.isSprinting()&&player.onGround()&&!player.isInWater()&&!player.isPassenger()&&!player.getAbilities().flying&&BestEquipment.ready(frame,16);
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);
        if(assist){
            BestEquipment.spend(frame,16,6);
            recordFrameWork(frame);
            if(speed!=null&&!speed.hasModifier(ASSIST))speed.addTransientModifier(new AttributeModifier(ASSIST,0.15,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }else if(speed!=null)speed.removeModifier(ASSIST);
        if(player.tickCount%20==0){
            ItemStack shown=BestEquipment.isEquipment(player.getMainHandItem())?player.getMainHandItem():frame;
            if(BestEquipment.isEquipment(shown)){
                String knowledge=BestEquipment.strideEnabled(shown)&&(!hasLightMobility(player)||researchLevel(player)<3)?" · 尚未学习轻载通行":researchLevel(player)<(shown.is(CatalogueContent.item("best_cutter"))?2:1)?" · 尚未学习":"";
                player.displayClientMessage(Component.literal(BestEquipment.status(shown)+knowledge),true);
            }
        }
    }
    private static void recordFrameWork(ItemStack frame){
        var data=CatalogueEquipment.getData(frame);int ticks=data.getInt("BestWorkTicks")+1;
        if(ticks>=100){ticks=0;BestEquipment.setWear(frame,BestEquipment.wear(frame)+1);data=CatalogueEquipment.getData(frame);}
        data.putInt("BestWorkTicks",ticks);CatalogueEquipment.setData(frame,data);
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
