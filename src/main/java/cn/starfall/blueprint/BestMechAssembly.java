package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Inventory-copy transactions: no input, return or chassis changes escape a failed plan. */
public final class BestMechAssembly {
    private BestMechAssembly() {}
    public static final List<String> MODULES=List.of("best_canglan_engineering","best_canglan_mobility","best_canglan_endurance","best_nutao_fire_control","best_nutao_armor","best_nutao_sustained","best_zhenyuan_module","best_tianpu_module","best_changchuan_module");
    public static final List<String> KNOWLEDGE=List.of("engineering_extension","engineering_mobility","engineering_endurance","combat_fire_control","combat_armor","combat_sustained","zhenyuan","tianpu","changchuan","combat_operation","confluence");
    public record Recipe(String output,String title,String knowledge,Map<String,Integer> cost) {}
    private static Map<String,Integer> cost(Object... pairs){Map<String,Integer> out=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],(Integer)pairs[i+1]);return Collections.unmodifiableMap(out);}
    public static final List<Recipe> RECIPES=List.of(
        new Recipe(MODULES.get(0),"沧澜·工程扩展",KNOWLEDGE.get(0),cost("best_mech_heavy_drill_arm",1,"steel_fix",8,"board",2,"sheath",4)),
        new Recipe(MODULES.get(1),"沧澜·机动驱动",KNOWLEDGE.get(1),cost("best_stride_module",2,"load",8,"conductor",4,"steel_fix",8)),
        new Recipe(MODULES.get(2),"沧澜·长时作业",KNOWLEDGE.get(2),cost("best_service_kit",2,"s01",2,"conductor",8,"sheath",8)),
        new Recipe(MODULES.get(3),"怒涛·协同火控",KNOWLEDGE.get(3),cost("best_fire_control",2,"sensor",4,"board",4,"conductor",4)),
        new Recipe(MODULES.get(4),"怒涛·结构防护",KNOWLEDGE.get(4),cost("best_load_armor",2,"load",8,"steel_fix",8,"sheath",4)),
        new Recipe(MODULES.get(5),"怒涛·持续输出",KNOWLEDGE.get(5),cost("best_weapon_radiator",2,"s01",2,"conductor",8,"sheath",8)),
        new Recipe(MODULES.get(6),"镇渊最终组件",KNOWLEDGE.get(6),cost(MODULES.get(0),1,MODULES.get(2),1,"best_engineering_confluence",1,"load",16,"board",4,"sheath",8)),
        new Recipe(MODULES.get(7),"天瀑最终组件",KNOWLEDGE.get(7),cost(MODULES.get(3),1,MODULES.get(5),1,"best_combat_confluence",1,"load",8,"sensor",8,"board",4,"sheath",8)),
        new Recipe(MODULES.get(8),"长川最终组件",KNOWLEDGE.get(8),cost(MODULES.get(1),1,MODULES.get(4),1,"best_service_kit",2,"s01",2,"conductor",16,"sensor",4,"board",4,"sheath",8)),
        new Recipe("best_fire_control","基础火控器","combat_operation",cost("board",2,"sensor",2,"conductor",4)),
        new Recipe("best_load_armor","承相装甲组件","combat_operation",cost("steel",8,"load",4,"steel_fix",4)),
        new Recipe("best_weapon_radiator","武器散热组件","combat_operation",cost("steel",4,"conductor",4,"sheath",4)),
        new Recipe("best_engineering_confluence","工程汇流总成","mech_operation",cost("best_cutter",2,"best_service_kit",1,"best_diagnostic_tool",1,"load",8,"board",4,"steel_fix",8)),
        new Recipe("best_combat_confluence","武装汇流总成","combat_operation",cost("best_fire_control",2,"best_load_armor",1,"best_weapon_radiator",1,"load",8,"sensor",4,"board",4)),
        new Recipe("best_combat_mech","激湍战斗机","combat_operation",cost("steel",32,"load",16,"conductor",8,"board",4,"steel_fix",16,"best_fire_control",1,"best_load_armor",1,"best_weapon_radiator",1)),
        new Recipe("best_universal_mech","归海全能融合","confluence",cost("best_engineering_confluence",1,"best_combat_confluence",1,"steel",64,"load",32,"conductor",16,"sensor",8,"board",8,"steel_fix",16,"sheath",16)));
    public static boolean handles(int action){return (action>=40&&action<40+RECIPES.size())||(action>=100&&action<109)||(action>=120&&action<129);}
    private static int normalized(int a){return a;}
    public static Component label(int a){a=normalized(a);return Component.literal(a>=120?"拆卸 "+RECIPES.get(a-120).title():a>=100?"安装 "+RECIPES.get(a-100).title():"制造 "+RECIPES.get(a-40).title());}
    public static boolean knows(Player p,String key){return Progression.isLearned(p,"best:"+key);}
    public static int knowledgeMask(Player p){int mask=BestEngineering.hasMechOperation(p)?1<<11:0;for(int i=0;i<KNOWLEDGE.size();i++)if(knows(p,KNOWLEDGE.get(i)))mask|=1<<i;return mask;}
    private static boolean learned(Player p,String key,int mask){int index=KNOWLEDGE.indexOf(key);return index<0?(mask&(1<<11))!=0:(mask&(1<<index))!=0;}
    private record Plan(BestEngineering.Preview preview,List<ItemStack> inventory) {}
    private static Plan reject(String text,List<BestEngineering.MaterialRequirement> materials){return new Plan(new BestEngineering.Preview(BestEngineering.Status.TARGET,Component.literal(text),materials),List.of());}
    public static BestEngineering.Preview preview(Player p,int action,int knowledge){return plan(p,normalized(action),knowledge).preview();}
    private static boolean pristine(ItemStack s){
        if(!Progression.usableMaterial(s)||s.getDamageValue()!=0)return false;
        var data=CatalogueEquipment.getData(s);
        return data.getInt("Energy")==0&&data.getInt("BestEnergy")==0&&CatalogueEquipment.energy(s).getEnergyStored()==0&&BestEquipment.wear(s)==0;
    }
    private static boolean insert(List<ItemStack> inventory,ItemStack out){
        if(out.isEmpty())return false;
        for(ItemStack s:inventory)if(ItemStack.isSameItemSameComponents(s,out)){int n=Math.min(out.getCount(),s.getMaxStackSize()-s.getCount());if(n>0){s.grow(n);out.shrink(n);}if(out.isEmpty())return true;}
        for(int i=0;i<36;i++)if(inventory.get(i).isEmpty()){inventory.set(i,out.copy());return true;}return false;
    }
    private static boolean ready(ItemStack s){if(!(s.getItem() instanceof BestMechItem)||s.getCount()!=1)return false;var t=BestMechItem.state(s);return t.getBoolean("Isolated")&&(!t.contains("Health")||(Float.isFinite(t.getFloat("Health"))&&t.getFloat("Health")>0));}
    private static Plan plan(Player p,int action,int knowledge){
        List<ItemStack> inv=new ArrayList<>();for(int i=0;i<36;i++)inv.add(p.getInventory().getItem(i).copy());
        List<BestEngineering.MaterialRequirement> materials=new ArrayList<>();
        if(action>=100){
            int index=action>=120?action-120:action-100;boolean remove=action>=120;ItemStack held=inv.get(p.getInventory().selected);
            if(!ready(held))return reject("主手须持已隔离、存活的收纳机甲",materials);
            if(!learned(p,KNOWLEDGE.get(index),knowledge))return reject("请先在成长中学习对应组件知识",materials);
            String type=BestMechProgression.type(held);var state=BestMechItem.state(held);int bit=1<<index,mask=BestMechProgression.mask(state);
            if(!BestMechProgression.compatible(type,bit))return reject("组件与机体不兼容",materials);
            if(remove?(mask&bit)==0:(mask&bit)!=0)return reject(remove?"尚未安装":"已经安装",materials);
            if(!state.contains("Health"))state.putFloat("Health",BestMechProgression.maxHealth(type,state));
            if(remove){float oldMax=BestMechProgression.maxHealth(type,state);state.putInt("UpgradeMask",mask&~bit);float newMax=BestMechProgression.maxHealth(type,state);if(newMax<oldMax)state.putFloat("Health",state.getFloat("Health")*newMax/oldMax);
                int cargoSlots=(type.equals("universal")||(state.getInt("UpgradeMask")&1)!=0)?27:BestMechBodies.cargoSlots(BestMechBodies.read(state));
                var cargo=state.getList("Cargo",10);for(int i=0;i<cargo.size();i++){var entry=cargo.getCompound(i);if(entry.getInt("Slot")>=cargoSlots&&!entry.getCompound("Item").isEmpty())return reject("请先清空拆卸后超出货舱容量的物品（槽位 "+(entry.getInt("Slot")+1)+"）",materials);}
                if(state.getInt("Energy")>BestMechProgression.capacity(type,state))return reject("先降低储能后再拆卸扩容组件",materials);if(!insert(inv,CatalogueContent.stack(MODULES.get(index),1)))return reject("背包没有返还空间",materials);}
            else {boolean found=false;for(ItemStack s:inv)if(CatalogueContent.matches(MODULES.get(index),s)&&pristine(s)){s.shrink(1);found=true;break;}if(!found)return reject("缺少未充能、完好的实物组件",materials);state.putInt("UpgradeMask",mask|bit);}
            BestMechItem.setState(held,state);
        }else{
            Recipe recipe=RECIPES.get(action-40);
            for(var c:recipe.cost().entrySet()){int available=0;for(ItemStack s:inv)if(CatalogueContent.matches(c.getKey(),s)&&pristine(s))available+=s.getCount();materials.add(new BestEngineering.MaterialRequirement(CatalogueContent.stack(c.getKey(),1),c.getValue(),available));}
            if(action==55){
                for(String type:List.of("engineering","combat")){
                    int available=0;for(ItemStack stack:inv)if(ready(stack)&&BestMechProgression.type(stack).equals(type)&&BestMechItem.state(stack).getList("Cargo",10).isEmpty())available++;
                    materials.add(new BestEngineering.MaterialRequirement(CatalogueContent.stack(type.equals("engineering")?"best_mech":"best_combat_mech",1),1,available));
                }
            }
            if(!learned(p,recipe.knowledge(),knowledge))return reject("请先在成长中学习对应知识",materials);
            for(var c:recipe.cost().entrySet()){int need=c.getValue();for(ItemStack s:inv)if(CatalogueContent.matches(c.getKey(),s)&&pristine(s)){int n=Math.min(need,s.getCount());s.shrink(n);need-=n;if(need==0)break;}if(need>0)return reject("材料不足；工具须完好且未储能",materials);}
            ItemStack out=CatalogueContent.stack(recipe.output(),1);
            if(action==55){String error=fuse(inv,out);if(error!=null)return reject(error,materials);}
            if(!insert(inv,out))return reject("背包没有产物或返还空间",materials);
        }
        return new Plan(new BestEngineering.Preview(BestEngineering.Status.READY,Component.literal("可执行；确认后整笔结算"),List.copyOf(materials)),inv);
    }
    private static String fuse(List<ItemStack> inv,ItemStack out){
        ItemStack engineering=null,combat=null;int ei=-1,ci=-1;
        for(int i=0;i<36;i++){ItemStack s=inv.get(i);if(!ready(s))continue;String type=BestMechProgression.type(s);if(type.equals("engineering")&&engineering==null){engineering=s;ei=i;}else if(type.equals("combat")&&combat==null){combat=s;ci=i;}}
        if(engineering==null||combat==null)return "需要隔离存活的涓流与激湍各一台";
        var a=BestMechItem.state(engineering);var b=BestMechItem.state(combat);
        if(!a.getList("Cargo",10).isEmpty()||!b.getList("Cargo",10).isEmpty())return "请先清空两台机甲货舱";
        if(a.hasUUID("MechId")&&b.hasUUID("MechId")&&a.getUUID("MechId").equals(b.getUUID("MechId")))return "两台机甲身份重复";
        CompoundTag result=new CompoundTag();long energy=(long)a.getInt("Energy")+b.getInt("Energy");if(energy<0||energy>BestMechProgression.capacity("universal",result))return "输入总能源超出归海容量，请先取出能源";
        float am=BestMechProgression.maxHealth("engineering",a),bm=BestMechProgression.maxHealth("combat",b);
        float ah=a.contains("Health")?a.getFloat("Health"):am,bh=b.contains("Health")?b.getFloat("Health"):bm;
        if(!Float.isFinite(ah+bh)||ah>am||bh>bm)return "机体生命数据无效";
        result.putInt("Energy",(int)energy);result.putFloat("Health",BestMechProgression.maxHealth("universal",result)*(ah+bh)/(am+bm));result.putBoolean("Isolated",true);
        for(String key:List.of("Wear","Heat","DrillCooldown","WeaponCooldown","ChargeUsed"))result.putInt(key,Math.max(a.getInt(key),b.getInt(key)));
        for(String key:List.of("WorkTicks","CoreFuelTicks","CoreWorkTicks")){long n=(long)a.getInt(key)+b.getInt(key);if(n<0||n>Integer.MAX_VALUE)return "机体计数超出范围";result.putInt(key,(int)n);}
        if(result.getInt("CoreFuelTicks")>BestMechCores.MAX_FUEL_TICKS)return "请先消耗部分核心燃料后融合";
        int carry=result.getInt("WorkTicks")/100+result.getInt("CoreWorkTicks")/1000;
        if((long)result.getInt("Wear")+carry>=2400)return "合并累计工时将耗尽结构，请先维护机体";
        result.putInt("Wear",result.getInt("Wear")+carry);result.putInt("WorkTicks",result.getInt("WorkTicks")%100);result.putInt("CoreWorkTicks",result.getInt("CoreWorkTicks")%1000);
        result.putBoolean("ThermalLock",a.getBoolean("ThermalLock")||b.getBoolean("ThermalLock"));result.putLong("ChargeTick",Math.max(a.getLong("ChargeTick"),b.getLong("ChargeTick")));
        result.putUUID("MechId",UUID.randomUUID());result.putUUID("EngineeringSource",a.hasUUID("MechId")?a.getUUID("MechId"):UUID.randomUUID());result.putUUID("CombatSource",b.hasUUID("MechId")?b.getUUID("MechId"):UUID.randomUUID());
        inv.set(ei,ItemStack.EMPTY);inv.set(ci,ItemStack.EMPTY);
        for(CompoundTag tag:List.of(a,b)){
            for(int i=0;i<9;i++)if((BestMechProgression.mask(tag)&(1<<i))!=0&&!insert(inv,CatalogueContent.stack(MODULES.get(i),1)))return "背包没有组件返还空间";
            for(String key:List.of(BestMechModules.STATE,BestMechArms.STATE,BestMechBodies.STATE,BestMechCores.STATE,BestMechLegs.STATE)){String id=tag.getString(key);if(!id.isEmpty()&&!insert(inv,CatalogueContent.stack(id,1)))return "背包没有配装返还空间";}
        }
        BestMechItem.setState(out,result);return null;
    }
    public static boolean perform(Player p,int action){if(p.level().isClientSide||!p.isAlive()||p.isSpectator()||!handles(action))return false;Plan plan=plan(p,normalized(action),knowledgeMask(p));if(!plan.preview().executable()){p.displayClientMessage(plan.preview().reason(),true);return false;}for(int i=0;i<36;i++)p.getInventory().setItem(i,plan.inventory().get(i));p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();p.containerMenu.broadcastChanges();return true;}
}
