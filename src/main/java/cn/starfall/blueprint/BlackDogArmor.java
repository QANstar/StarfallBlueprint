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
import net.minecraft.world.entity.projectile.LargeFireball;
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
        if(!active(player).isEmpty())return Lingchuan.fail(player,"lc.already_deployed");
        for(var slot:SLOTS)if(!player.getItemBySlot(slot).isEmpty())return Lingchuan.fail(player,"lc.armor_slots_full");
        var data=CatalogueEquipment.getData(watch);data.putBoolean("Deployed",true);data.putUUID("Wearer",player.getUUID());CatalogueEquipment.setData(watch,data);
        for(int i=0;i<4;i++){
            var projection=CatalogueContent.stack("blackdog_armor_"+PROJECTIONS[i],1);var d=new CompoundTag();d.putUUID("SuitId",data.getUUID("SuitId"));d.putUUID("Wearer",player.getUUID());
            d.putIntArray("ChassisTiers",data.getIntArray("ChassisTiers"));d.putInt("Abilities",data.getInt("Abilities"));d.putInt("CoreTier",tier(watch));d.putInt("VisualVersion",2);CatalogueEquipment.setData(projection,d);player.setItemSlot(SLOTS[i],projection);
        }
        lockSlots(player.inventoryMenu);player.inventoryMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("lc.deployed"),true);return true;
    }
    public static void recall(Player player,ItemStack watch){
        for(var slot:SLOTS)if(linked(player.getItemBySlot(slot),watch))player.setItemSlot(slot,ItemStack.EMPTY);
        var data=CatalogueEquipment.getData(watch);data.putBoolean("Deployed",false);data.remove("Wearer");CatalogueEquipment.setData(watch,data);
        clearEffects(player);player.inventoryMenu.broadcastChanges();player.displayClientMessage(Component.translatable("lc.recalled"),true);
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
        attribute(player,Attributes.ARMOR,DEFENSE,0,AttributeModifier.Operation.ADD_VALUE);
        attribute(player,Attributes.MOVEMENT_SPEED,SPRINT,0,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attribute(player,Attributes.JUMP_STRENGTH,JUMP,0,AttributeModifier.Operation.ADD_VALUE);
        for(String ability:List.of("night","breathing","cloak"))clearEffect(player,ability);
        if(player.getPersistentData().getBoolean("BlackDogFlight")){
            if(!player.isCreative()&&!player.isSpectator()){player.getAbilities().mayfly=false;player.getAbilities().flying=false;player.onUpdateAbilities();}
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
        if(player.getAbilities().flying&&flight){if(spend(watch,100))player.fallDistance=0;}
        if(player.tickCount%20==0){
            int upkeep=(has(watch,"sprint")&&player.isSprinting()?300:0)+(has(watch,"jump")?150:0);
            if(upkeep>0)spend(watch,upkeep);
            effect(player,watch,"night",MobEffects.NIGHT_VISION,80);
            effect(player,watch,"breathing",MobEffects.WATER_BREATHING,120);
            effect(player,watch,"cloak",MobEffects.INVISIBILITY,800);
            if(has(watch,"turret")&&charge(watch)>=600&&player.level() instanceof ServerLevel level){
                var target=level.getEntitiesOfClass(Monster.class,player.getBoundingBox().inflate(12),e->e.isAlive()&&player.hasLineOfSight(e)&&!player.isAlliedTo(e)).stream().min(Comparator.comparingDouble(player::distanceToSqr));
                if(target.isPresent()&&spend(watch,600)){var t=target.get();beam(level,player.getEyePosition().add(0,0.5,0),t.getEyePosition());t.hurt(player.damageSources().playerAttack(player),4);}
                level.sendParticles(ParticleTypes.END_ROD,player.getX()+Math.cos(player.tickCount*0.1),player.getY()+1.9,player.getZ()+Math.sin(player.tickCount*0.1),2,0.03,0.03,0.03,0);
            }
            player.displayClientMessage(Component.translatable("lc.hud",charge(watch),capacity(watch),tier(watch)),true);
        }
    }
    private static void effect(Player player,ItemStack watch,String ability,net.minecraft.core.Holder<MobEffect> effect,int power){
        if(has(watch,ability)&&spend(watch,power)){
            var current=player.getEffect(effect);
            if(current==null||current.getDuration()<220){player.addEffect(new MobEffectInstance(effect,220,0,false,false,true));player.getPersistentData().putBoolean("BlackDogEffect_"+ability,true);}
        }else clearEffect(player,ability);
    }
    private static void clearEffect(Player player,String ability){
        if(!player.getPersistentData().getBoolean("BlackDogEffect_"+ability))return;
        var effect=switch(ability){case "night"->MobEffects.NIGHT_VISION;case "breathing"->MobEffects.WATER_BREATHING;default->MobEffects.INVISIBILITY;};
        var current=player.getEffect(effect);
        if(current!=null&&current.getAmplifier()==0&&current.getDuration()<=220)player.removeEffect(effect);
        player.getPersistentData().putBoolean("BlackDogEffect_"+ability,false);
    }
    public static void incomingDamage(LivingIncomingDamageEvent event){
        if(!(event.getEntity() instanceof Player player)||player.level().isClientSide||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        var watch=active(player);if(watch.isEmpty()||!complete(player,watch))return;
        float absorb=Math.min(event.getAmount()*(has(watch,"shield")?0.8f:0.2f),charge(watch)/500f);
        if(absorb>0&&spend(watch,(int)Math.ceil(absorb*500)))event.setAmount(Math.max(0,event.getAmount()-absorb));
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
        @Override public boolean onDroppedByPlayer(ItemStack stack,Player player){return false;}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){return InteractionResultHolder.fail(player.getItemInHand(hand));}
        @Override public ResourceLocation getArmorTexture(ItemStack stack,Entity entity,EquipmentSlot slot,ArmorMaterial.Layer layer,boolean inner){return StarfallBlueprint.id("textures/models/armor/blackdog_exosuit.png");}
        @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int index,boolean selected){
            if(!level.isClientSide&&entity instanceof Player p&&Arrays.stream(SLOTS).noneMatch(s->p.getItemBySlot(s)==stack))stack.setCount(0);
        }
    }
    public static final class Watch extends Item {
        public Watch(Properties p){super(p.stacksTo(1).fireResistant());}
        @Override public boolean onDroppedByPlayer(ItemStack stack,Player player){return !deployed(stack);}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            var stack=player.getItemInHand(hand);if(!level.isClientSide)toggle(player,stack);return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
        }
        @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){
            if(!level.isClientSide&&entity instanceof Player player&&player.tickCount%20==0&&valid(stack))setCharge(stack,charge(stack)+switch(tier(stack)){case 1->80;case 2->240;default->800;});
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
    public static final class FireControl extends Item {
        public FireControl(Properties p){super(p.stacksTo(1).component(DataComponents.TOOL,Tiers.NETHERITE.createToolProperties(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)));}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            ItemStack terminal=player.getItemInHand(hand);if(level.isClientSide)return InteractionResultHolder.success(terminal);
            var watch=active(player);if(watch.isEmpty()||!complete(player,watch)){Lingchuan.fail(player,"lc.require_deployed");return InteractionResultHolder.fail(terminal);}
            List<String> weapons=List.of("laser","missile","cannon").stream().filter(a->has(watch,a)).toList();
            if(weapons.isEmpty()){Lingchuan.fail(player,"lc.weapon_missing");return InteractionResultHolder.fail(terminal);}
            var data=CatalogueEquipment.getData(terminal);int mode=Math.floorMod(data.getInt("Mode"),weapons.size());
            if(player.isShiftKeyDown()){mode=(mode+1)%weapons.size();data.putInt("Mode",mode);CatalogueEquipment.setData(terminal,data);player.displayClientMessage(Component.translatable("lc.ability."+weapons.get(mode)),true);return InteractionResultHolder.success(terminal);}
            String weapon=weapons.get(mode);int cost=weapon.equals("laser")?1200:weapon.equals("missile")?5000:8000;
            if(player.getCooldowns().isOnCooldown(this)||!spend(watch,cost)){Lingchuan.fail(player,"lc.power_missing");return InteractionResultHolder.fail(terminal);}
            player.getCooldowns().addCooldown(this,weapon.equals("laser")?10:40);
            fire(player,watch,weapon);return InteractionResultHolder.success(terminal);
        }
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){lines.add(Component.translatable("lc.fire_control_hint"));}
    }
    private static void fire(Player player,ItemStack watch,String weapon){
        var level=(ServerLevel)player.level();Vec3 from=player.getEyePosition(),direction=player.getLookAngle(),to=from.add(direction.scale(weapon.equals("cannon")?12:32));
        if(weapon.equals("missile")){
            var rocket=new LargeFireball(level,player,direction,1);rocket.setPos(from.add(direction.scale(1.2)));level.addFreshEntity(rocket);return;
        }
        var hit=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        Vec3 end=weapon.equals("cannon")?to:hit.getLocation();
        if(weapon.equals("cannon")&&player instanceof ServerPlayer serverPlayer){
            var checked=new HashSet<BlockPos>();int broken=0;
            for(int step=1;step<=12&&broken<24;step++){
                BlockPos pos=BlockPos.containing(from.add(direction.scale(step)));
                if(!checked.add(pos)||!level.hasChunkAt(pos))continue;var state=level.getBlockState(pos);
                if(state.isAir())continue;
                if(!level.mayInteract(player,pos)||state.getDestroySpeed(level,pos)<0||state.getDestroySpeed(level,pos)>50||level.getBlockEntity(pos)!=null){end=from.add(direction.scale(step-0.75));break;}
                if(!spend(watch,1000)){end=from.add(direction.scale(step-0.75));break;}
                if(!serverPlayer.gameMode.destroyBlock(pos)){end=from.add(direction.scale(step-0.75));break;}broken++;
            }
        }
        beam(level,from,end);
        var box=new AABB(from,end).inflate(1);
        for(var target:level.getEntitiesOfClass(LivingEntity.class,box,e->e!=player&&e.isAlive()&&!player.isAlliedTo(e))){
            if(target instanceof Player other&&!player.canHarmPlayer(other))continue;
            if(target.getBoundingBox().inflate(0.3).clip(from,end).isPresent())target.hurt(player.damageSources().playerAttack(player),weapon.equals("cannon")?16:8);
        }
    }
    private static void beam(ServerLevel level,Vec3 from,Vec3 to){
        int steps=Math.min(96,(int)(from.distanceTo(to)*3));for(int i=0;i<=steps;i++){var p=from.lerp(to,i/(double)Math.max(1,steps));level.sendParticles(ParticleTypes.ELECTRIC_SPARK,p.x,p.y,p.z,1,0,0,0,0);}
    }
    private BlackDogArmor(){}
}
