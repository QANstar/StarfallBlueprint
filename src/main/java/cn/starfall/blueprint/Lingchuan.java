package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Server-authoritative research, manufacture and reversible eight-bay assembly. */
public final class Lingchuan {
    public static final List<String> STATIONS=List.of("lc_alloy_forge","lc_parts_fabricator","lc_armor_assembler","lc_armor_charger","lc_research_station");
    public static final List<String> PARTS=List.of("head","chest","core","legs","feet","hands","back","shoulders");
    public static final DeferredHolder<MenuType<?>,MenuType<LingchuanMenu>> MENU=StarfallBlueprint.MENUS.register("lingchuan",()->new MenuType<>(LingchuanMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public record Research(String key,int parent,int tier,int power,int alloy,int crystal,int column,int row) {
        public Component name(){return Component.translatable("research.starfall_blueprint."+key);}
        public Component cost(){return Component.translatable("lc.research_cost",power,alloy,crystal);}
    }
    public static final List<Research> RESEARCH=List.of(
        new Research("laminar_metallurgy",-1,0,10000,0,4,0,0),
        new Research("blackdog_basics",0,0,20000,2,4,1,0),
        new Research("compressed_core",1,1,60000,4,8,2,0),
        new Research("micro_reactor",2,2,180000,8,16,3,0),
        new Research("night_optics",1,1,24000,2,4,0,1),
        new Research("aquatic_support",4,1,32000,3,4,0,2),
        new Research("sprint_drive",1,1,24000,2,4,1,1),
        new Research("jump_drive",6,1,32000,3,4,1,2),
        new Research("flight_drive",7,2,100000,6,8,1,3),
        new Research("energy_shield",2,2,60000,4,8,2,1),
        new Research("heavy_plating",9,2,100000,6,8,2,2),
        new Research("optical_cloak",10,3,160000,8,12,2,3),
        new Research("laser_emitter",2,2,60000,4,8,3,1),
        new Research("missile_pod",12,2,100000,6,8,3,2),
        new Research("mining_cannon",12,3,160000,8,12,0,3),
        new Research("floating_turret",13,3,180000,8,16,3,3));
    public record Module(String key,int slot,int tier,int research,int load,String ability) {
        public Component name(){return Component.translatable("item.starfall_blueprint."+key);}
        public int alloy(){return tier==1?2:tier==2?4:8;}
        public int power(){return tier*10000;}
    }
    public static final List<Module> MODULES;
    public static final List<String> ABILITIES=List.of("night","breathing","sprint","jump","flight","shield","heavy","cloak","laser","missile","cannon","turret");
    private static final Map<String,Module> LEGACY=new HashMap<>();
    static {
        var list=new ArrayList<Module>();
        for(int tier=1;tier<=3;tier++)for(int i=0;i<PARTS.size();i++)list.add(new Module(baseKey(i,tier),i,tier,tier,1,""));
        int[] slots={0,0,3,4,6,1,1,6,5,7,5,7},tiers={1,1,1,1,2,2,2,3,2,2,3,3},nodes={4,5,6,7,8,9,10,11,12,13,14,15},loads={1,1,1,1,3,2,2,3,2,2,3,3};
        String[] old={"head_night","head_aquatic","legs_sprint","feet_jump","back_flight","chest_shield","chest_heavy","back_cloak","hands_laser","shoulders_missile","hands_cannon","shoulders_turret"};
        for(int i=0;i<ABILITIES.size();i++){
            var m=new Module("blackdog_component_"+ABILITIES.get(i),slots[i],tiers[i],nodes[i],loads[i],ABILITIES.get(i));list.add(m);
            LEGACY.put("blackdog_"+old[i],new Module("blackdog_"+old[i],m.slot(),m.tier(),m.research(),m.load(),m.ability()));
        }
        MODULES=List.copyOf(list);
    }
    public static String baseKey(int slot,int tier){return slot==2?switch(tier){case 2->"blackdog_core_compressed";case 3->"blackdog_core_reactor";default->"blackdog_core_basic";}:"blackdog_"+PARTS.get(slot)+switch(tier){case 2->"_reinforced";case 3->"_elite";default->"_basic";};}
    public static int interfaces(int tier){return Math.clamp(tier,1,3)*2;}
    public static boolean legacy(String key){return LEGACY.containsKey(key);}
    public static void initialize(){}
    public static boolean isStation(String key){return STATIONS.contains(key);}
    public static boolean isContent(String key){return isStation(key)||key.startsWith("blackdog_")||key.equals("laminar_alloy")||key.equals("laminar_crystal");}
    public static Module module(ItemStack stack){
        var found=MODULES.stream().filter(m->stack.is(CatalogueContent.item(m.key()))).findFirst().orElse(null);
        if(found!=null)return found;
        return LEGACY.values().stream().filter(m->stack.is(CatalogueContent.item(m.key()))).findFirst().orElse(null);
    }
    public static boolean base(ItemStack stack){var m=module(stack);return m!=null&&m.ability().isEmpty();}
    public static boolean component(ItemStack stack){var m=module(stack);return m!=null&&!m.ability().isEmpty()&&!legacy(m.key());}
    public static Item moduleItem(String key,Item.Properties properties){return new Item(properties.stacksTo(1)){
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
            MODULES.stream().filter(m->m.key().equals(key)).findFirst().ifPresent(m->{
                lines.add(Component.translatable("lc.module_spec",Component.translatable("lc.part."+PARTS.get(m.slot())),m.tier(),m.load()));
                lines.add(Component.translatable("lc.ability."+(m.ability().isEmpty()?"passive":m.ability())));
                lines.add(Component.translatable(m.ability().isEmpty()?(m.slot()==2?"lc.core_budget":"lc.chassis_interfaces"):"lc.component_install",m.slot()==2?BlackDogArmor.budget(m.tier()):interfaces(m.tier())));
                if(legacy(key))lines.add(Component.translatable("lc.legacy_component"));
            });
        }
    };}
    public static int knowledge(Player player){return player.getPersistentData().getInt("LingchuanResearch")&65535;}
    public static boolean knows(Player player,int node){return node>=0&&node<RESEARCH.size()&&(knowledge(player)&(1<<node))!=0;}
    public static boolean prerequisites(int mask,int index){
        if(index<0||index>=RESEARCH.size())return false;
        var n=RESEARCH.get(index);
        return (n.parent()<0||(mask&(1<<n.parent()))!=0)&&(n.tier()==0||(mask&(1<<n.tier()))!=0);
    }
    public static boolean accepts(String kind,int slot,ItemStack stack){
        if(kind.equals("lc_armor_assembler")){
            if(slot==8)return stack.getItem() instanceof BlackDogArmor.Watch&&!BlackDogArmor.deployed(stack);
            if(slot>=10&&slot<22)return component(stack);
            var module=module(stack);return slot>=0&&slot<8&&base(stack)&&module.slot()==slot;
        }
        return kind.equals("lc_armor_charger")&&slot==8&&stack.getItem() instanceof BlackDogArmor.Watch&&!BlackDogArmor.deployed(stack);
    }
    public static void tickStation(IndustryBlockEntity be){
        if(be.redstoneStopped()||!AggregateMachines.ready(be)){be.status=8;return;}
        be.status=0;
        if(be.kind().equals("lc_armor_charger")){
            var watch=be.getItem(8);
            if(watch.getItem() instanceof BlackDogArmor.Watch&&!BlackDogArmor.deployed(watch)&&BlackDogArmor.valid(watch)){
                int received=Math.min((int)Math.min(be.getPower(),4096),BlackDogArmor.capacity(watch)-BlackDogArmor.charge(watch));
                if(received>0){BlackDogArmor.setCharge(watch,BlackDogArmor.charge(watch)+received);be.setPower(be.getPower()-received);be.status=1;}
            }
        }
        AggregateMachines.syncActive(be,be.status==1);
    }
    public static boolean research(Player player,IndustryBlockEntity be,int node,boolean materials){
        if(!be.kind().equals("lc_research_station")||node<0||node>=RESEARCH.size()||knows(player,node)||!prerequisites(knowledge(player),node))return fail(player,"lc.research_locked");
        var r=RESEARCH.get(node);
        if(materials){if(!consume(player,Map.of("laminar_alloy",r.alloy(),"common",r.crystal()),false))return fail(player,"lc.materials_missing");}
        else if(be.getPower()<r.power())return fail(player,"lc.power_missing");
        if(materials)consume(player,Map.of("laminar_alloy",r.alloy(),"common",r.crystal()),true);
        else be.setPower(be.getPower()-r.power());
        player.getPersistentData().putInt("LingchuanResearch",knowledge(player)|(1<<node));
        player.displayClientMessage(Component.translatable("lc.researched",r.name()),true);return true;
    }
    /** Reservation checks precede all input mutation, even for creative operators. */
    public static boolean consume(Player player,Map<String,Integer> inputs,boolean commit){
        for(var e:inputs.entrySet()){
            int found=0;for(int i=0;i<36;i++){var s=player.getInventory().getItem(i);if(CatalogueContent.matches(e.getKey(),s))found+=s.getCount();}
            if(found<e.getValue())return false;
        }
        if(commit)for(var e:inputs.entrySet()){
            int remaining=e.getValue();for(int i=0;i<36&&remaining>0;i++){var s=player.getInventory().getItem(i);if(CatalogueContent.matches(e.getKey(),s)){int n=Math.min(remaining,s.getCount());s.shrink(n);remaining-=n;}}
        }
        return true;
    }
    public static boolean manufacture(Player player,IndustryBlockEntity be,int index){
        if(!be.kind().equals("lc_parts_fabricator")||index<0||index>=MODULES.size())return false;
        var m=MODULES.get(index);
        if(!knows(player,m.research()))return fail(player,"lc.research_locked");
        return produce(player,be,m.key(),1,m.power(),Map.of("laminar_alloy",m.alloy(),"laminar_crystal",m.tier(),"board",m.tier()));
    }
    public static boolean makeFireControl(Player player,IndustryBlockEntity be){
        if(!knows(player,12))return fail(player,"lc.research_locked");
        return produce(player,be,"blackdog_fire_control",1,10000,Map.of("laminar_alloy",2,"board",2,"minecraft:redstone",4));
    }
    public static boolean forge(Player player,IndustryBlockEntity be,int recipe){
        if(!be.kind().equals("lc_alloy_forge")||!knows(player,0))return fail(player,"lc.research_locked");
        return switch(recipe){
            case 0->produce(player,be,"laminar_crystal",2,10000,Map.of("common",4,"minecraft:quartz",4,"minecraft:amethyst_shard",2));
            case 1->produce(player,be,"laminar_alloy",4,40000,Map.of("laminar_crystal",2,"minecraft:netherite_ingot",1,"steel",4));
            default->false;
        };
    }
    private static boolean produce(Player player,IndustryBlockEntity be,String key,int count,int power,Map<String,Integer> inputs){
        ItemStack result=CatalogueContent.stack(key,count),out=be.getItem(9);
        if(!out.isEmpty()&&(!ItemStack.isSameItemSameComponents(result,out)||out.getCount()+count>out.getMaxStackSize()))return fail(player,"lc.output_full");
        if(be.getPower()<power)return fail(player,"lc.power_missing");
        if(!consume(player,inputs,false))return fail(player,"lc.materials_missing");
        consume(player,inputs,true);be.setPower(be.getPower()-power);
        if(out.isEmpty())be.setItem(9,result);else{out.grow(count);be.setChanged();}
        return true;
    }
    public static boolean assemble(Player player,IndustryBlockEntity be){
        if(!be.kind().equals("lc_armor_assembler")||!knows(player,1))return fail(player,"lc.research_locked");
        if(!be.getItem(8).isEmpty()||!be.getItem(9).isEmpty())return fail(player,"lc.output_full");
        var modules=new ArrayList<ItemStack>();for(int i=0;i<8;i++)modules.add(be.getItem(i).copyWithCount(1));
        var components=new ArrayList<ItemStack>();for(int i=10;i<22;i++)if(!be.getItem(i).isEmpty())components.add(be.getItem(i).copyWithCount(1));
        if(!BlackDogArmor.compatible(modules,components))return fail(player,"lc.incompatible");
        for(var s:modules)if(!knows(player,Objects.requireNonNull(module(s)).research()))return fail(player,"lc.research_locked");
        for(var s:components)if(!knows(player,Objects.requireNonNull(module(s)).research()))return fail(player,"lc.research_locked");
        if(be.getPower()<10000)return fail(player,"lc.power_missing");
        ItemStack watch=BlackDogArmor.build(modules,components);for(int i=0;i<8;i++)be.getItem(i).shrink(1);
        for(int i=10;i<22;i++)if(!be.getItem(i).isEmpty())be.getItem(i).shrink(1);
        be.setPower(be.getPower()-10000);be.setItem(9,watch);return true;
    }
    /** Swap only occupied bays; displaced parts return to those same bays atomically. */
    public static boolean refit(Player player,IndustryBlockEntity be){
        if(!be.kind().equals("lc_armor_assembler"))return false;
        ItemStack watch=be.getItem(8);
        if(!BlackDogArmor.valid(watch)||BlackDogArmor.deployed(watch))return fail(player,"lc.watch_missing");
        var previous=BlackDogArmor.modules(watch);var proposed=new ArrayList<>(previous);boolean changed=false;
        for(int i=0;i<8;i++)if(!be.getItem(i).isEmpty()){
            var m=module(be.getItem(i));if(!base(be.getItem(i))||m.slot()!=i||be.getItem(i).getCount()!=1||!knows(player,m.research()))return fail(player,"lc.research_locked");
            proposed.set(i,be.getItem(i).copy());changed=true;
        }
        var components=new ArrayList<>(BlackDogArmor.components(watch));
        for(int i=10;i<22;i++)if(!be.getItem(i).isEmpty()){
            var s=be.getItem(i);var m=module(s);
            if(!component(s)||s.getCount()!=1||!knows(player,m.research()))return fail(player,"lc.research_locked");
            components.add(s.copy());changed=true;
        }
        if(!changed||!BlackDogArmor.compatible(proposed,components))return fail(player,"lc.incompatible");
        if(be.getPower()<5000)return fail(player,"lc.power_missing");
        int charge=BlackDogArmor.charge(watch);
        for(int i=0;i<8;i++)if(!be.getItem(i).isEmpty())be.setItem(i,previous.get(i));
        for(int i=10;i<22;i++)be.setItem(i,ItemStack.EMPTY);
        BlackDogArmor.configure(watch,proposed,components);BlackDogArmor.setCharge(watch,Math.min(charge,BlackDogArmor.capacity(watch)));
        be.setPower(be.getPower()-5000);be.setChanged();return true;
    }
    public static boolean removeComponent(Player player,IndustryBlockEntity be,int index){
        if(!be.kind().equals("lc_armor_assembler"))return false;var watch=be.getItem(8);
        if(!BlackDogArmor.valid(watch)||BlackDogArmor.deployed(watch))return fail(player,"lc.watch_missing");
        var components=new ArrayList<>(BlackDogArmor.components(watch));if(index<0||index>=ABILITIES.size())return false;
        String ability=ABILITIES.get(index);int found=-1;for(int i=0;i<components.size();i++)if(module(components.get(i)).ability().equals(ability)){found=i;break;}if(found<0)return false;
        int output=-1;for(int i=10;i<22;i++)if(be.getItem(i).isEmpty()){output=i;break;}
        if(output<0)return fail(player,"lc.output_full");if(be.getPower()<1000)return fail(player,"lc.power_missing");
        var removed=components.remove(found);BlackDogArmor.configure(watch,BlackDogArmor.modules(watch),components);be.setItem(output,removed);be.setPower(be.getPower()-1000);return true;
    }
    public static boolean fail(Player player,String key){player.displayClientMessage(Component.translatable(key),true);return false;}
    private Lingchuan(){}
}
