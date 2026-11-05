package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** The watch owns all eight real modules. Worn armour is a non-extractable projection. */
public final class BlackDogArmor {
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> ARMED_WATCH=
        StarfallBlueprint.ITEMS.registerItem("blackdog_armed_watch",ArmedWatch::new);
    public static void initialize(){}
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final String[] PROJECTIONS={"head","chest","legs","feet"};
    private static final ResourceLocation DEFENSE=StarfallBlueprint.id("blackdog_defense"),SPRINT=StarfallBlueprint.id("blackdog_sprint"),JUMP=StarfallBlueprint.id("blackdog_jump");
    public static List<ItemStack> modules(ItemStack watch){
        var list=NonNullList.withSize(8,ItemStack.EMPTY);
        if(watch.getItem() instanceof Watch){var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if(server!=null)ContainerHelper.loadAllItems(CatalogueEquipment.getData(watch).getCompound("Modules"),list,server.registryAccess());}
        return list;
    }
    public static void setModules(ItemStack watch,List<ItemStack> modules){
        configure(watch,modules,components(watch));
    }
    public static List<ItemStack> components(ItemStack watch){
        var list=NonNullList.withSize(12,ItemStack.EMPTY);
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server!=null&&watch.getItem() instanceof Watch)ContainerHelper.loadAllItems(CatalogueEquipment.getData(watch).getCompound("Components"),list,server.registryAccess());
        return list.stream().filter(s->!s.isEmpty()).map(ItemStack::copy).toList();
    }
    public static void configure(ItemStack watch,List<ItemStack> modules,List<ItemStack> components){
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server==null)throw new IllegalStateException("Armour modules are written on the server");
        var list=NonNullList.withSize(8,ItemStack.EMPTY);for(int i=0;i<8;i++)list.set(i,modules.get(i).copyWithCount(1));
        var data=CatalogueEquipment.getData(watch);var stored=new CompoundTag();ContainerHelper.saveAllItems(stored,list,server.registryAccess());data.put("Modules",stored);
        var functions=NonNullList.withSize(12,ItemStack.EMPTY);for(int i=0;i<components.size();i++)functions.set(i,components.get(i).copyWithCount(1));
        var functionsTag=new CompoundTag();ContainerHelper.saveAllItems(functionsTag,functions,server.registryAccess());data.put("Components",functionsTag);data.putInt("Format",2);
        var core=Lingchuan.module(list.get(2));data.putInt("CoreTier",core==null?0:core.tier());
        int abilities=0;for(int i=0;i<ABILITIES.size();i++){String a=ABILITIES.get(i);if(components.stream().map(Lingchuan::module).anyMatch(m->m!=null&&m.ability().equals(a)))abilities|=1<<i;}
        data.putIntArray("ChassisTiers",modules.stream().map(Lingchuan::module).mapToInt(m->m==null?0:m.tier()).toArray());
        int[][] defense={{1,2,3},{3,4,5},{0,0,0},{2,3,4},{1,2,2},{0,1,1},{0,1,1},{1,1,2}};int armor=0;
        for(int i=0;i<8;i++){var base=Lingchuan.module(modules.get(i));if(base!=null)armor+=defense[i][Math.clamp(base.tier()-1,0,2)];}data.putInt("Armor",armor);
        data.putInt("Load",8+components.stream().map(Lingchuan::module).filter(Objects::nonNull).mapToInt(Lingchuan.Module::load).sum());
        data.putInt("Abilities",abilities);CatalogueEquipment.setData(watch,data);
    }
    private static final List<String> ABILITIES=Lingchuan.ABILITIES;
    public static final List<String> SYSTEMS=List.of("laser","missile","cannon","turret","shield","cloak");
    public static boolean enabled(ItemStack watch,String ability){
        var data=CatalogueEquipment.getData(watch);
        boolean defaultOn=!ability.equals("turret")&&!ability.equals("cloak");
        return has(watch,ability)&&(data.contains("Enabled_"+ability)?data.getBoolean("Enabled_"+ability):defaultOn);
    }
    public static String selectedSystem(ItemStack watch){
        int index=Math.floorMod(CatalogueEquipment.getData(watch).getInt("SelectedSystem"),SYSTEMS.size());
        if(has(watch,SYSTEMS.get(index)))return SYSTEMS.get(index);
        return SYSTEMS.stream().filter(a->has(watch,a)).findFirst().orElse("");
    }
    public static boolean holdingTerminal(Player player){return player.getMainHandItem().getItem() instanceof FireControl||player.getOffhandItem().getItem() instanceof FireControl;}
    public static boolean selectSystem(Player player,int selection){
        var watch=active(player);
        if(!player.isAlive()||player.isSpectator()||!holdingTerminal(player)||watch.isEmpty()||!complete(player,watch))return false;
        int current=SYSTEMS.indexOf(selectedSystem(watch)),next=selection;
        if(selection==-1||selection==-2){
            next=current;
            for(int i=0;i<SYSTEMS.size();i++){next=Math.floorMod(next+(selection==-1?1:-1),SYSTEMS.size());if(has(watch,SYSTEMS.get(next)))break;}
        }
        if(next<0||next>=SYSTEMS.size()||!has(watch,SYSTEMS.get(next)))return false;
        var data=CatalogueEquipment.getData(watch);data.putInt("SelectedSystem",next);CatalogueEquipment.setData(watch,data);return true;
    }
    public static boolean cruise(Player player){return player.getPersistentData().getBoolean("BlackDogCruise");}
    public static void flightRequest(Player player,boolean flying,boolean cruise){
        flightRequest(player,flying,cruise,true);
    }
    public static void flightRequest(Player player,boolean flying,boolean cruise,boolean takeoff){
        var watch=active(player);
        if(!flying){stopFlight(player,watch,player.onGround());return;}
        if(!watch.isEmpty()&&!ensurePower(player,watch))return;
        boolean accepted=player.isAlive()&&!player.isSpectator()&&!player.isPassenger()&&!watch.isEmpty()&&complete(player,watch)&&has(watch,"flight")&&charge(watch)>=100;
        if(player.getPersistentData().getBoolean("BlackDogGroundLock")&&!takeoff)return;
        if(accepted){
            if(takeoff){player.getPersistentData().putBoolean("BlackDogGroundLock",false);player.getPersistentData().putBoolean("BlackDogAirborne",false);player.getPersistentData().putLong("BlackDogTakeoffUntil",player.level().getGameTime()+8);}
            cruise=cruise&&charge(watch)>=350;
            boolean hover=flying&&!cruise,changed=player.getAbilities().flying!=hover;player.getAbilities().flying=hover;
            if(takeoff&&player.onGround())player.setDeltaMovement(player.getDeltaMovement().add(0,.42,0));
            if(changed){player.onUpdateAbilities();if(flying)BlackDogWeapons.sound(player,net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH,.7f,.6f);}
            var data=CatalogueEquipment.getData(watch);if(data.getBoolean("Hovering")!=flying){data.putBoolean("Hovering",flying);CatalogueEquipment.setData(watch,data);}
        }
        player.getPersistentData().putBoolean("BlackDogCruise",accepted&&flying&&cruise);
        player.getPersistentData().putLong("BlackDogFlightInput",player.level().getGameTime());
    }
    /** A landed suit cannot be restarted by an in-flight heartbeat arriving late. */
    public static void stopFlight(Player player,ItemStack watch,boolean groundLock){
        boolean owned=player.getPersistentData().getBoolean("BlackDogFlight")||cruise(player)||CatalogueEquipment.getData(watch).getBoolean("Hovering");
        boolean moving=player.getAbilities().flying||player.isFallFlying()||CatalogueEquipment.getData(watch).getBoolean("Hovering")||cruise(player);
        if(owned&&moving){player.getAbilities().flying=false;player.stopFallFlying();player.setPose(Pose.STANDING);player.fallDistance=0;if(!player.level().isClientSide)player.onUpdateAbilities();}
        player.getPersistentData().putBoolean("BlackDogCruise",false);player.getPersistentData().putBoolean("BlackDogCruisePose",false);player.getPersistentData().putBoolean("BlackDogAirborne",false);player.getPersistentData().putBoolean("BlackDogGroundLock",groundLock);
        if(!watch.isEmpty()){var data=CatalogueEquipment.getData(watch);if(data.getBoolean("Hovering")){data.putBoolean("Hovering",false);CatalogueEquipment.setData(watch,data);}}
    }
    /** Below the reserve, recall once; passive charging must not redeploy the armour automatically. */
    public static boolean ensurePower(Player player,ItemStack watch){
        if(!deployed(watch)||charge(watch)>=100)return true;
        var data=CatalogueEquipment.getData(watch);for(var system:List.of("turret","shield","cloak"))data.putBoolean("Enabled_"+system,false);data.putLong("ShutdownAt",player.level().getGameTime()+1);CatalogueEquipment.setData(watch,data);
        recall(player,watch);return false;
    }
    public static void markCombat(Player player,ItemStack watch){
        var data=CatalogueEquipment.getData(watch);data.putLong("CombatUntil",player.level().getGameTime()+100);CatalogueEquipment.setData(watch,data);
    }
    public static boolean canRegenerate(Player player,ItemStack watch){
        return player.level().getGameTime()>=CatalogueEquipment.getData(watch).getLong("CombatUntil")&&(!deployed(watch)||!enabled(watch,"turret"))&&!(deployed(watch)&&(player.getAbilities().flying||cruise(player)));
    }
    public static void regenerate(Player player,ItemStack watch){
        if(valid(watch)&&canRegenerate(player,watch))setCharge(watch,charge(watch)+switch(tier(watch)){case 1->80;case 2->240;default->800;});
    }
    public static boolean compatible(List<ItemStack> modules){
        return compatible(modules,List.of());
    }
    public static boolean compatible(List<ItemStack> bases,List<ItemStack> components){
        if(bases.size()!=8||components.size()>12)return false;var core=Lingchuan.module(bases.get(2));if(core==null||core.slot()!=2||!core.ability().isEmpty())return false;
        int load=8;int[] used=new int[8];var unique=new HashSet<String>();
        for(int i=0;i<8;i++){var m=Lingchuan.module(bases.get(i));if(!Lingchuan.base(bases.get(i))||m.slot()!=i||m.tier()>core.tier())return false;}
        for(var s:components){
            var m=Lingchuan.module(s);if(!Lingchuan.component(s)||!unique.add(m.ability()))return false;
            var base=Lingchuan.module(bases.get(m.slot()));used[m.slot()]+=m.load();load+=m.load();
            if(m.tier()>base.tier()||used[m.slot()]>Lingchuan.interfaces(base.tier()))return false;
        }
        return load<=budget(core.tier());
    }
    public static int budget(int tier){return tier==1?12:tier==2?24:tier==3?40:0;}
    public static ItemStack build(List<ItemStack> modules){
        return build(modules,List.of());
    }
    public static ItemStack build(List<ItemStack> modules,List<ItemStack> components){
        if(!compatible(modules,components))throw new IllegalArgumentException("Incompatible Black Dog configuration");
        var watch=CatalogueContent.stack("blackdog_watch",1);configure(watch,modules,components);
        var data=CatalogueEquipment.getData(watch);data.putUUID("SuitId",UUID.randomUUID());CatalogueEquipment.setData(watch,data);return watch;
    }
    /** Convert each old ability-bearing part to an equivalent chassis plus a real detachable component. */
    public static void migrate(ItemStack watch){
        if(!(watch.getItem() instanceof Watch)||CatalogueEquipment.getData(watch).getInt("Format")>=2)return;
        var bases=new ArrayList<>(modules(watch));var components=new ArrayList<ItemStack>();
        for(int i=0;i<bases.size();i++){
            var m=Lingchuan.module(bases.get(i));if(m==null)return;
            if(!m.ability().isEmpty()){
                bases.set(i,CatalogueContent.stack(Lingchuan.baseKey(i,m.tier()),1));
                components.add(CatalogueContent.stack("blackdog_component_"+m.ability(),1));
            }
        }
        if(compatible(bases,components))configure(watch,bases,components);
    }
    public static boolean valid(ItemStack watch){
        if(!(watch.getItem() instanceof Watch))return false;migrate(watch);
        return compatible(modules(watch),components(watch))&&CatalogueEquipment.getData(watch).hasUUID("SuitId");
    }
    public static int tier(ItemStack watch){return Math.clamp(CatalogueEquipment.getData(watch).getInt("CoreTier"),0,3);}
    public static int capacity(ItemStack watch){return switch(tier(watch)){case 1->100000;case 2->400000;case 3->1000000;default->0;};}
    public static int charge(ItemStack watch){return Math.clamp(CatalogueEquipment.getData(watch).getInt("Energy"),0,capacity(watch));}
    public static void setCharge(ItemStack watch,int energy){var data=CatalogueEquipment.getData(watch);data.putInt("Energy",Math.clamp(energy,0,capacity(watch)));CatalogueEquipment.setData(watch,data);}
    public static boolean spend(ItemStack watch,int amount){if(amount<0||charge(watch)<amount)return false;setCharge(watch,charge(watch)-amount);return true;}
    public static boolean has(ItemStack watch,String ability){int i=ABILITIES.indexOf(ability);return i>=0&&(CatalogueEquipment.getData(watch).getInt("Abilities")&(1<<i))!=0;}
    public static boolean deployed(ItemStack watch){return watch.getItem() instanceof Watch&&CatalogueEquipment.getData(watch).getBoolean("Deployed");}
    public static ItemStack active(Player player){for(int i=0;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);if(deployed(stack))return stack;}return ItemStack.EMPTY;}
    private static boolean linked(ItemStack projection,ItemStack watch){var a=CatalogueEquipment.getData(projection);var b=CatalogueEquipment.getData(watch);return projection.getItem() instanceof Projection&&a.hasUUID("SuitId")&&b.hasUUID("SuitId")&&a.getUUID("SuitId").equals(b.getUUID("SuitId"));}
    public static boolean toggle(Player player,ItemStack watch){
        if(player.level().isClientSide)return false;
        if(deployed(watch)){recall(player,watch);return true;}
        if(!valid(watch))return Lingchuan.fail(player,"lc.watch_empty");
        if(charge(watch)<100)return Lingchuan.fail(player,"lc.power_missing");
        if(!active(player).isEmpty())return Lingchuan.fail(player,"lc.already_deployed");
        for(var slot:SLOTS)if(!player.getItemBySlot(slot).isEmpty())return Lingchuan.fail(player,"lc.armor_slots_full");
        var data=CatalogueEquipment.getData(watch);data.putBoolean("Deployed",true);data.remove("ShutdownAt");data.putUUID("Wearer",player.getUUID());CatalogueEquipment.setData(watch,data);
        for(int i=0;i<4;i++){
            var projection=CatalogueContent.stack("blackdog_armor_"+PROJECTIONS[i],1);var d=new CompoundTag();d.putUUID("SuitId",data.getUUID("SuitId"));d.putUUID("Wearer",player.getUUID());
            d.putIntArray("ChassisTiers",data.getIntArray("ChassisTiers"));d.putInt("Abilities",data.getInt("Abilities"));d.putInt("CoreTier",tier(watch));d.putInt("VisualVersion",2);CatalogueEquipment.setData(projection,d);player.setItemSlot(SLOTS[i],projection);
        }
        lockSlots(player.inventoryMenu);player.inventoryMenu.broadcastChanges();
        return true;
    }
    public static void recall(Player player,ItemStack watch){
        stopFlight(player,watch,player.onGround());
        BlackDogWeapons.clearDrones(player);
        for(var slot:SLOTS)if(linked(player.getItemBySlot(slot),watch))player.setItemSlot(slot,ItemStack.EMPTY);
        var data=CatalogueEquipment.getData(watch);data.putBoolean("Deployed",false);data.putBoolean("Hovering",false);data.remove("Wearer");CatalogueEquipment.setData(watch,data);
        clearEffects(player);player.inventoryMenu.broadcastChanges();
    }
    public static boolean complete(Player player,ItemStack watch){for(var slot:SLOTS)if(!linked(player.getItemBySlot(slot),watch))return false;return true;}
    private static boolean locked(ItemStack stack){return stack.getItem() instanceof Projection||deployed(stack);}
    /** Wrap existing slots without changing their underlying inventory or slot IDs. */
    public static void lockSlots(AbstractContainerMenu menu){
        for(int i=0;i<menu.slots.size();i++){var original=menu.slots.get(i);if(original instanceof LockedSlot||!(original.container instanceof net.minecraft.world.entity.player.Inventory)||!locked(original.getItem()))continue;var slot=new LockedSlot(original);slot.index=original.index;menu.slots.set(i,slot);}
    }
    private static final class LockedSlot extends Slot {
        final Slot original;
        LockedSlot(Slot original){super(original.container,original.getContainerSlot(),original.x,original.y);this.original=original;}
        @Override public boolean mayPickup(Player player){return !locked(getItem())&&original.mayPickup(player);}
        @Override public boolean mayPlace(ItemStack stack){return !locked(getItem())&&!locked(stack)&&original.mayPlace(stack);}
        @Override public int getMaxStackSize(){return original.getMaxStackSize();}
        @Override public int getMaxStackSize(ItemStack stack){return original.getMaxStackSize(stack);}
        @Override public void onTake(Player player,ItemStack stack){original.onTake(player,stack);}
        @Override public void set(ItemStack stack){original.set(stack);}
        @Override public void setByPlayer(ItemStack stack,ItemStack previous){original.setByPlayer(stack,previous);}
        @Override public ItemStack remove(int amount){return original.remove(amount);}
        @Override public boolean isActive(){return original.isActive();}
        @Override public com.mojang.datafixers.util.Pair<ResourceLocation,ResourceLocation> getNoItemIcon(){return original.getNoItemIcon();}
    }
    private static void attribute(Player player,net.minecraft.core.Holder<Attribute> type,ResourceLocation id,double amount,AttributeModifier.Operation operation){
        var instance=player.getAttribute(type);if(instance==null)return;var existing=instance.getModifier(id);
        if(existing!=null&&existing.amount()==amount)return;
        instance.removeModifier(id);if(amount!=0)instance.addTransientModifier(new AttributeModifier(id,amount,operation));
    }
    private static void clearEffects(Player player){
        player.getPersistentData().putBoolean("BlackDogCruise",false);
        if(player.getPersistentData().getBoolean("BlackDogCruisePose")){player.stopFallFlying();player.setPose(Pose.STANDING);player.getPersistentData().putBoolean("BlackDogCruisePose",false);}
        attribute(player,Attributes.ARMOR,DEFENSE,0,AttributeModifier.Operation.ADD_VALUE);
        attribute(player,Attributes.MOVEMENT_SPEED,SPRINT,0,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attribute(player,Attributes.JUMP_STRENGTH,JUMP,0,AttributeModifier.Operation.ADD_VALUE);
        for(String ability:List.of("night","breathing","cloak"))clearEffect(player,ability);
        if(player.getPersistentData().getBoolean("BlackDogFlight")){
            player.getAbilities().flying=false;
            if(!player.isCreative()&&!player.isSpectator())player.getAbilities().mayfly=false;
            player.onUpdateAbilities();
            player.getPersistentData().putBoolean("BlackDogFlight",false);
        }
    }
    public static void playerTick(PlayerTickEvent.Post event){tickPlayer(event.getEntity());}
    public static void tickPlayer(Player player){
        // Inventory UI and server both enforce locking; creative overwrite is reconciled below.
        if(!active(player).isEmpty()){lockSlots(player.inventoryMenu);lockSlots(player.containerMenu);}
        if(player.level().isClientSide)return;
        var watch=active(player);
        if(watch.isEmpty()){
            for(var slot:SLOTS)if(player.getItemBySlot(slot).getItem() instanceof Projection)player.setItemSlot(slot,ItemStack.EMPTY);
            clearEffects(player);return;
        }
        var data=CatalogueEquipment.getData(watch);
        if(!data.hasUUID("Wearer")||!data.getUUID("Wearer").equals(player.getUUID())||!complete(player,watch)||!valid(watch)){
            recall(player,watch);return;
        }
        if(!ensurePower(player,watch))return;
        if(data.getBoolean("Hovering")){
            if(!player.onGround())player.getPersistentData().putBoolean("BlackDogAirborne",true);
            else if(player.getPersistentData().getBoolean("BlackDogAirborne")||player.level().getGameTime()>player.getPersistentData().getLong("BlackDogTakeoffUntil")){
                stopFlight(player,watch,true);data=CatalogueEquipment.getData(watch);
            }
        }
        boolean powered=charge(watch)>0;
        for(var slot:SLOTS){var projection=player.getItemBySlot(slot);var visual=CatalogueEquipment.getData(projection);if(visual.getInt("VisualVersion")<2){var configuration=CatalogueEquipment.getData(watch);visual.putIntArray("ChassisTiers",configuration.getIntArray("ChassisTiers"));visual.putInt("Abilities",configuration.getInt("Abilities"));visual.putInt("CoreTier",tier(watch));visual.putInt("VisualVersion",2);CatalogueEquipment.setData(projection,visual);}}
        attribute(player,Attributes.ARMOR,DEFENSE,CatalogueEquipment.getData(watch).getInt("Armor")+(powered&&has(watch,"heavy")?6:0),AttributeModifier.Operation.ADD_VALUE);
        attribute(player,Attributes.MOVEMENT_SPEED,SPRINT,powered&&has(watch,"sprint")&&player.isSprinting()?0.25:0,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attribute(player,Attributes.JUMP_STRENGTH,JUMP,powered&&has(watch,"jump")?0.25:0,AttributeModifier.Operation.ADD_VALUE);
        boolean flight=has(watch,"flight")&&charge(watch)>=100;
        if(flight&&!player.getAbilities().mayfly){player.getAbilities().mayfly=true;player.onUpdateAbilities();}
        if(flight)player.getPersistentData().putBoolean("BlackDogFlight",true);
        if(!flight&&player.getPersistentData().getBoolean("BlackDogFlight")){
            if(!player.isCreative()&&!player.isSpectator()){player.getAbilities().mayfly=false;player.getAbilities().flying=false;player.onUpdateAbilities();}
            player.getPersistentData().putBoolean("BlackDogFlight",false);
        }
        boolean cruise=flight&&charge(watch)>=350&&data.getBoolean("Hovering")&&cruise(player)&&!player.isPassenger()&&!player.isInWater()&&player.level().getGameTime()-player.getPersistentData().getLong("BlackDogFlightInput")<=12;
        if(!cruise)player.getPersistentData().putBoolean("BlackDogCruise",false);
        if(cruise){player.startFallFlying();player.setPose(Pose.FALL_FLYING);player.getPersistentData().putBoolean("BlackDogCruisePose",true);}
        else if(player.getPersistentData().getBoolean("BlackDogCruisePose")){player.stopFallFlying();player.setPose(Pose.STANDING);player.getPersistentData().putBoolean("BlackDogCruisePose",false);if(flight&&data.getBoolean("Hovering")){player.getAbilities().flying=true;player.onUpdateAbilities();}}
        if((player.getAbilities().flying||cruise)&&flight){if(!spend(watch,cruise?350:100)){recall(player,watch);return;}player.fallDistance=0;}
        if(!ensurePower(player,watch))return;
        BlackDogWeapons.tickDrones(player,watch);
        syncVisuals(player,watch,cruise);
        if(player.tickCount%20==0){
            int upkeep=(has(watch,"sprint")&&player.isSprinting()&&!player.getAbilities().flying?300:0)+(has(watch,"jump")&&!player.onGround()&&!player.getAbilities().flying?150:0);
            if(upkeep>0)spend(watch,upkeep);
            effect(player,watch,"night",MobEffects.NIGHT_VISION,80);
            effect(player,watch,"breathing",MobEffects.WATER_BREATHING,120);
            effect(player,watch,"cloak",MobEffects.INVISIBILITY,800);
            if(enabled(watch,"turret")&&charge(watch)>=600&&player.level() instanceof ServerLevel level){
                var target=level.getEntitiesOfClass(Monster.class,player.getBoundingBox().inflate(12),e->e.isAlive()&&player.hasLineOfSight(e)&&!player.isAlliedTo(e)).stream().min(Comparator.comparingDouble(player::distanceToSqr));
                if(target.isPresent()&&spend(watch,600)){var t=target.get();markCombat(player,watch);BlackDogWeapons.droneShot(player,t);t.hurt(player.damageSources().playerAttack(player),6);}
            }
        }
        ensurePower(player,watch);
    }
    private static void syncVisuals(Player player,ItemStack watch,boolean cruise){
        for(var slot:SLOTS){
            var projection=player.getItemBySlot(slot);var d=CatalogueEquipment.getData(projection);
            boolean shield=enabled(watch,"shield")&&charge(watch)>0,turret=enabled(watch,"turret")&&charge(watch)>0;
            if(d.getBoolean("ShieldActive")!=shield||d.getBoolean("TurretActive")!=turret||d.getBoolean("Cruise")!=cruise){
                d.putBoolean("ShieldActive",shield);d.putBoolean("TurretActive",turret);d.putBoolean("Cruise",cruise);CatalogueEquipment.setData(projection,d);
            }
        }
    }
    private static void effect(Player player,ItemStack watch,String ability,net.minecraft.core.Holder<MobEffect> effect,int power){
        if(enabled(watch,ability)&&spend(watch,power)){
            var current=player.getEffect(effect);
            if(current==null||!current.isInfiniteDuration()&&current.getAmplifier()==0&&current.getDuration()<500){player.addEffect(new MobEffectInstance(effect,600,0,false,false,true));player.getPersistentData().putBoolean("BlackDogEffect_"+ability,true);}
        }else clearEffect(player,ability);
    }
    private static void clearEffect(Player player,String ability){
        if(!player.getPersistentData().getBoolean("BlackDogEffect_"+ability))return;
        var effect=switch(ability){case "night"->MobEffects.NIGHT_VISION;case "breathing"->MobEffects.WATER_BREATHING;default->MobEffects.INVISIBILITY;};
        var current=player.getEffect(effect);
        if(current!=null&&!current.isInfiniteDuration()&&current.getAmplifier()==0&&current.getDuration()<=600)player.removeEffect(effect);
        player.getPersistentData().putBoolean("BlackDogEffect_"+ability,false);
    }
    public static void incomingDamage(LivingIncomingDamageEvent event){
        if(!(event.getEntity() instanceof Player player)||player.level().isClientSide||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        var watch=active(player);if(watch.isEmpty()||!complete(player,watch))return;
        if(event.getAmount()<=0)return;markCombat(player,watch);
        float absorb=Math.min(event.getAmount()*(enabled(watch,"shield")?0.8f:0.2f),charge(watch)/500f);
        if(absorb>0&&spend(watch,(int)Math.ceil(absorb*500))){event.setAmount(Math.max(0,event.getAmount()-absorb));if(enabled(watch,"shield"))BlackDogWeapons.shieldImpact(player);ensurePower(player,watch);}
    }
    public static void drops(LivingDropsEvent event){
        if(event.getEntity() instanceof Player){event.getDrops().removeIf(e->e.getItem().getItem() instanceof Projection);
            for(var drop:event.getDrops())if(drop.getItem().getItem() instanceof Watch){var data=CatalogueEquipment.getData(drop.getItem());data.putBoolean("Deployed",false);data.remove("Wearer");CatalogueEquipment.setData(drop.getItem(),data);}}
    }
    public static void clonePlayer(PlayerEvent.Clone event){
        event.getEntity().getPersistentData().putInt("LingchuanResearch",Lingchuan.knowledge(event.getOriginal()));
        for(int i=0;i<event.getEntity().getInventory().getContainerSize();i++){
            var stack=event.getEntity().getInventory().getItem(i);
            if(stack.getItem() instanceof Projection)event.getEntity().getInventory().setItem(i,ItemStack.EMPTY);
            else if(deployed(stack)){var d=CatalogueEquipment.getData(stack);d.putBoolean("Deployed",false);d.remove("Wearer");CatalogueEquipment.setData(stack,d);}
        }
    }
    public static Item createProjection(String key,Item.Properties p){
        ArmorItem.Type type=key.endsWith("head")?ArmorItem.Type.HELMET:key.endsWith("chest")?ArmorItem.Type.CHESTPLATE:key.endsWith("legs")?ArmorItem.Type.LEGGINGS:ArmorItem.Type.BOOTS;
        return new Projection(type,p);
    }
    public static final class Projection extends ArmorItem {
        Projection(Type type,Properties p){super(ArmorMaterials.NETHERITE,type,p.stacksTo(1).attributes(ItemAttributeModifiers.EMPTY));}
        @Override public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack){return ItemAttributeModifiers.EMPTY;}
        @Override public boolean isValidRepairItem(ItemStack stack,ItemStack ingredient){return false;}
        @Override public boolean canElytraFly(ItemStack stack,LivingEntity entity){return CatalogueEquipment.getData(stack).getBoolean("Cruise");}
        @Override public boolean elytraFlightTick(ItemStack stack,LivingEntity entity,int flightTicks){return canElytraFly(stack,entity);}
        @Override public boolean onDroppedByPlayer(ItemStack stack,Player player){return false;}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){return InteractionResultHolder.fail(player.getItemInHand(hand));}
        @Override public ResourceLocation getArmorTexture(ItemStack stack,Entity entity,EquipmentSlot slot,ArmorMaterial.Layer layer,boolean inner){return StarfallBlueprint.id("textures/models/armor/blackdog_exosuit.png");}
        @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int index,boolean selected){
            if(!level.isClientSide&&entity instanceof Player p&&Arrays.stream(SLOTS).noneMatch(s->p.getItemBySlot(s)==stack))stack.setCount(0);
        }
    }
    public static class Watch extends Item {
        public Watch(Properties p){super(p.stacksTo(1).fireResistant());}
        @Override public boolean onDroppedByPlayer(ItemStack stack,Player player){return !deployed(stack);}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            var stack=player.getItemInHand(hand);if(!level.isClientSide)toggle(player,stack);return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
        }
        @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){
            if(!level.isClientSide&&entity instanceof Player player&&player.tickCount%20==0)regenerate(player,stack);
        }
        @Override public boolean isBarVisible(ItemStack stack){return tier(stack)>0;}
        @Override public int getBarWidth(ItemStack stack){return capacity(stack)==0?0:charge(stack)*13/capacity(stack);}
        @Override public int getBarColor(ItemStack stack){return 0xd03946;}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("lc.watch_hint"));
            if(tier(stack)==0){lines.add(Component.translatable("lc.watch_empty"));return;}
            lines.add(Component.translatable("lc.hud",charge(stack),capacity(stack),tier(stack)));
            lines.add(Component.translatable("lc.core_budget",budget(tier(stack))));
            var data=CatalogueEquipment.getData(stack);lines.add(Component.translatable("lc.watch_configuration",data.getInt("Armor"),data.getInt("Load"),budget(tier(stack)),Integer.bitCount(data.getInt("Abilities"))));
            for(var a:ABILITIES)if(has(stack,a))lines.add(Component.translatable("lc.ability."+a));
        }
    }
    /** Creative preset is materialized on the server, including its own unique suit identity. */
    public static final class ArmedWatch extends Watch {
        public ArmedWatch(Properties p){super(p);}
        public void prepare(ItemStack stack){
            if(CatalogueEquipment.getData(stack).getInt("Format")>=2)return;
            var bases=new ArrayList<ItemStack>();
            for(int i=0;i<8;i++)bases.add(CatalogueContent.stack(Lingchuan.baseKey(i,3),1));
            var functions=ABILITIES.stream().map(a->CatalogueContent.stack("blackdog_component_"+a,1)).toList();
            var configured=build(bases,functions);
            CatalogueEquipment.setData(stack,CatalogueEquipment.getData(configured));
            setCharge(stack,capacity(stack));
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            var stack=player.getItemInHand(hand);
            if(!level.isClientSide){
                prepare(stack);
                if(toggle(player,stack)&&deployed(stack)){
                    var data=CatalogueEquipment.getData(stack);
                    if(!data.getBoolean("FireControlIssued")){
                        var terminal=CatalogueContent.stack("blackdog_fire_control",1);
                        if(!player.getInventory().contains(terminal)&&!player.getInventory().add(terminal))player.drop(terminal,false);
                        data.putBoolean("FireControlIssued",true);CatalogueEquipment.setData(stack,data);
                    }
                }
            }
            return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
        }
        @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){
            if(!level.isClientSide)prepare(stack);
            super.inventoryTick(stack,level,entity,slot,selected);
        }
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("lc.armed_watch_hint"));
            lines.add(Component.translatable("lc.armed_watch_control"));
            if(tier(stack)>0)super.appendHoverText(stack,context,lines,flag);
        }
    }
    public static final class FireControl extends Item {
        public FireControl(Properties p){super(p.stacksTo(1).component(DataComponents.TOOL,Tiers.NETHERITE.createToolProperties(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)));}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            ItemStack terminal=player.getItemInHand(hand);if(level.isClientSide)return InteractionResultHolder.success(terminal);
            var watch=active(player);if(watch.isEmpty()||!complete(player,watch)){Lingchuan.fail(player,"lc.require_deployed");return InteractionResultHolder.fail(terminal);}
            String weapon=selectedSystem(watch);
            if(weapon.isEmpty()){Lingchuan.fail(player,"lc.weapon_missing");return InteractionResultHolder.fail(terminal);}
            if(player.isShiftKeyDown()){selectSystem(player,-1);return InteractionResultHolder.success(terminal);}
            if(player.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(terminal);
            if(List.of("turret","shield","cloak").contains(weapon)){
                var data=CatalogueEquipment.getData(watch);boolean on=!enabled(watch,weapon);data.putBoolean("Enabled_"+weapon,on);CatalogueEquipment.setData(watch,data);
                player.getCooldowns().addCooldown(this,4);
                return InteractionResultHolder.success(terminal);
            }
            int cost=weapon.equals("laser")?1200:weapon.equals("missile")?5000:8000;
            if(player.getCooldowns().isOnCooldown(this)||!spend(watch,cost)){Lingchuan.fail(player,"lc.power_missing");return InteractionResultHolder.fail(terminal);}
            player.getCooldowns().addCooldown(this,weapon.equals("laser")?10:40);
            markCombat(player,watch);BlackDogWeapons.fire(player,watch,weapon);ensurePower(player,watch);return InteractionResultHolder.success(terminal);
        }
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){lines.add(Component.translatable("lc.fire_control_hint"));}
    }
    private BlackDogArmor(){}
}
