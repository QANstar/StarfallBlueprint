package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.registries.*;

/** Haijia's independent orbital and aeronautics branch. */
public final class Haijia {
    public static final Map<String,DeferredItem<Item>> ITEMS=new LinkedHashMap<>();
    public static final Map<String,DeferredBlock<OrbitalBlock>> BLOCKS=new LinkedHashMap<>();
    public static final DeferredBlock<Block> TUNGSTEN_ORE=StarfallBlueprint.BLOCKS.register("hj_tungsten_ore",()->new Block(BlockBehaviour.Properties.of().strength(4.5f).requiresCorrectToolForDrops()));
    static {
        ITEMS.put("hj_tungsten_ore",StarfallBlueprint.ITEMS.register("hj_tungsten_ore",()->new BlockItem(TUNGSTEN_ORE.get(),new Item.Properties())));
        for(String id:List.of("relay","assembler","launchpad","console")) {
            var block=StarfallBlueprint.BLOCKS.register("hj_"+id,()->new OrbitalBlock(BlockBehaviour.Properties.of().strength(4).noOcclusion()));
            BLOCKS.put(id,block); ITEMS.put("hj_"+id,StarfallBlueprint.ITEMS.register("hj_"+id,()->new BlockItem(block.get(),new Item.Properties())));
        }
        for(String id:List.of("coil","processor","solar_wing","bus","thruster","launch_vehicle","laser_array","tungsten_ingot","tungsten_rod","weather_array","airframe","engine","missile","sat_charge","sat_laser","sat_kinetic","sat_weather","sat_charge_upgrade","sat_laser_upgrade","sat_kinetic_upgrade","bomb"))
            ITEMS.put("hj_"+id,StarfallBlueprint.ITEMS.registerSimpleItem("hj_"+id));
        ITEMS.put("hj_terminal",StarfallBlueprint.ITEMS.registerItem("hj_terminal",OrbitalTerminalItem::new));
        ITEMS.put("hj_fighter",StarfallBlueprint.ITEMS.registerItem("hj_fighter",PeregrineItem::new));
        ITEMS.put("hj_creative_terminal",StarfallBlueprint.ITEMS.registerItem("hj_creative_terminal",p->new OrbitalTerminalItem(p,true)));
        ITEMS.put("hj_creative_fighter",StarfallBlueprint.ITEMS.registerItem("hj_creative_fighter",p->new PeregrineItem(p,true)));
    }
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<OrbitalBlockEntity>> BE=StarfallBlueprint.ENTITIES.register("hj_station",()->BlockEntityType.Builder.of(OrbitalBlockEntity::new,BLOCKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<OrbitalMenu>> MENU=StarfallBlueprint.MENUS.register("hj_orbit",()->new MenuType<>(OrbitalMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(Registries.ENTITY_TYPE,StarfallBlueprint.ID);
    public static final DeferredHolder<EntityType<?>,EntityType<PeregrineEntity>> FIGHTER=TYPES.register("hj_peregrine",()->EntityType.Builder.of(PeregrineEntity::new,MobCategory.MISC).sized(2.8f,1.3f).passengerAttachments(1.05f).clientTrackingRange(16).updateInterval(1).build("hj_peregrine"));
    public static final DeferredHolder<EntityType<?>,EntityType<OrbitalStrike>> STRIKE=TYPES.register("hj_strike",()->EntityType.Builder.of(OrbitalStrike::new,MobCategory.MISC).sized(.5f,.5f).clientTrackingRange(32).updateInterval(1).build("hj_strike"));
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=StarfallBlueprint.TABS.register("haijia",()->CreativeModeTab.builder().title(text("tab")).icon(()->stack("hj_terminal",1)).displayItems((p,o)->ITEMS.values().forEach(i->o.accept(i.get()))).build());
    public static Item item(String key){var i=ITEMS.get(key);if(i==null)throw new IllegalArgumentException(key);return i.get();}
    public static ItemStack stack(String key,int count){return new ItemStack(item(key),count);}
    public static Component text(String key,Object...args){return Component.translatable("hj."+key,args);}
    public static final String[] KNOWLEDGE={"power","orbit","charge","laser","kinetic","weather","flight"};
    public static boolean knows(Player p,String key){return java.util.Arrays.asList(KNOWLEDGE).contains(key);}
    public static void register(IEventBus bus){
        TYPES.register(bus);HaijiaSounds.SOUNDS.register(bus);bus.addListener(OrbitalNetworking::register);
        bus.addListener((RegisterCapabilitiesEvent e)->{
            e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,BE.get(),(b,s)->{var owner=OrbitalBlock.controller(b.getLevel(),b.getBlockPos());return owner==null?null:owner.energy;});
            e.registerItem(Capabilities.EnergyStorage.ITEM,(s,c)->PeregrineItem.energy(s),item("hj_fighter"),item("hj_creative_fighter"));
            e.registerEntity(Capabilities.EnergyStorage.ENTITY,FIGHTER.get(),(f,c)->f.energyPort());
            e.registerItem(Capabilities.EnergyStorage.ITEM,(s,c)->watchEnergy(s),CatalogueContent.item("blackdog_watch"),BlackDogArmor.ARMED_WATCH.get());
        });
        NeoForge.EVENT_BUS.addListener(OrbitalFleet::playerTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone e)->{
            if(e.getOriginal().getPersistentData().contains("HaijiaKnowledge"))e.getEntity().getPersistentData().put("HaijiaKnowledge",e.getOriginal().getPersistentData().getCompound("HaijiaKnowledge").copy());
        });
        for(int i=0;i<KNOWLEDGE.length;i++){
            String key=KNOWLEDGE[i];String icon=i==0?"relay":i==1?"assembler":i==6?"fighter":"sat_"+key;
            var parents=i==0?List.<String>of():List.of("haijia:"+(i==1?"power":"orbit"));
            Progression.register(new Progression.Node("haijia:"+key,"haijia",text("research."+key),text("research_desc."+key),parents,List.of(new Progression.Cost("conductor",i<2?4:8),new Progression.Cost("board",i<2?2:4)),i<2?3:8,()->stack("hj_"+icon,1),p->p.getPersistentData().getCompound("HaijiaKnowledge").getBoolean(key),p->{var t=p.getPersistentData().getCompound("HaijiaKnowledge").copy();t.putBoolean(key,true);p.getPersistentData().put("HaijiaKnowledge",t);}));
        }
    }
    private static net.neoforged.neoforge.energy.IEnergyStorage watchEnergy(ItemStack s){return new net.neoforged.neoforge.energy.IEnergyStorage(){
        public int receiveEnergy(int max,boolean sim){int n=BlackDogArmor.valid(s)?Math.max(0,Math.min(Math.min(max,4000),getMaxEnergyStored()-getEnergyStored())):0;if(!sim&&n>0)BlackDogArmor.setCharge(s,getEnergyStored()+n);return n;}
        public int extractEnergy(int n,boolean sim){return 0;}public int getEnergyStored(){return BlackDogArmor.charge(s);}public int getMaxEnergyStored(){return BlackDogArmor.capacity(s);}public boolean canReceive(){return BlackDogArmor.valid(s);}public boolean canExtract(){return false;}
    };}
    private Haijia(){}
}
