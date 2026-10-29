package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class CatalogueEquipment {
    public static final DeferredHolder<MenuType<?>,MenuType<MiningBagMenu>> BAG_MENU=StarfallBlueprint.MENUS.register("mining_bag",()->new MenuType<>(MiningBagMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static void initialize(){}
    public static Item create(String key,Item.Properties p){
        if(key.equals("blackdog_fire_control"))return new BlackDogArmor.FireControl(p);
        if(key.equals("blackdog_watch"))return new BlackDogArmor.Watch(p);
        if(key.startsWith("blackdog_armor_"))return BlackDogArmor.createProjection(key,p);
        if(key.startsWith("blackdog_"))return Lingchuan.moduleItem(key,p);
        if(key.equals("best_frame"))return new BestEquipment.Frame(p);
        if(key.equals("best_cutter"))return new BestEquipment.Cutter(p);
        if(key.equals("best_service_kit"))return new BestServiceKit(p);
        if(key.equals("best_diagnostic_tool"))return new BestDiagnosticTool(p);
        if(key.equals("best_stride_module"))return new Item(p.stacksTo(1));
        if(key.equals("s01"))p.stacksTo(1);
        if(key.equals("lined_box")||key.equals("coated_box"))return new SealedSampleItem(p);
        if(key.equals("sample_pick"))return new SamplePick(p);
        if(key.equals("work_goggles"))return new ArmorItem(ArmorMaterials.LEATHER,ArmorItem.Type.HELMET,p.durability(120));
        if(key.equals("sample_case"))return new MiningBagItem(p.stacksTo(1));
        if(key.equals("rescue_hook"))return new Item(p.durability(160)){
            public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext c){return RopeSystem.use(c);}
            public InteractionResultHolder<ItemStack>use(Level l,Player p,InteractionHand h){return RopeSystem.useAir(l,p,h);}
        };
        if(key.equals("staff_blank"))return new Item(p.stacksTo(1)){
            public InteractionResultHolder<ItemStack>use(Level l,Player player,InteractionHand hand){if(!l.isClientSide)player.displayClientMessage(Component.literal("未激活灵相媒介 · 保留材料品质与路线"),true);return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),l.isClientSide);}
        };
        if(CatalogueContent.TOOLS.contains(key))p.durability(key.equals("head")||key.equals("basic_jig")||key.equals("facet_jig")?100:256);
        if(Set.of("refined","conductor","load","laminar","sensor","anima","anima_basic","directed","coating","liner","lunar").contains(key))
            p.component(CatalogueContent.QUALITY.get(),new MaterialQuality(key.equals("refined")?99:100,100,key.equals("anima_basic")?40:key.equals("anima")?80:0,0,true,key.equals("refined")?"common":key.startsWith("anima")?"anima":key));
        return new Item(p){
            public boolean isBarVisible(ItemStack s){return key.equals("s01")?getData(s).getInt("Energy")>0:super.isBarVisible(s);}
            public int getBarWidth(ItemStack s){return key.equals("s01")?getData(s).getInt("Energy")*13/200000:super.getBarWidth(s);}
            public int getBarColor(ItemStack s){return key.equals("s01")?0x49bca3:super.getBarColor(s);}
            public boolean isValidRepairItem(ItemStack s,ItemStack ingredient){return CatalogueContent.TOOLS.contains(key)?ingredient.is(key.equals("head")||key.equals("facet_jig")?Items.DIAMOND:Items.IRON_INGOT):super.isValidRepairItem(s,ingredient);}
            public void appendHoverText(ItemStack s,TooltipContext c,List<Component>lines,TooltipFlag f){
                if(key.equals("s01"))lines.add(Component.literal(getData(s).getInt("Energy")+" / 200000 XH"));
                for(String line:MachineUpgrades.description(key))lines.add(Component.literal(line));
                if(!MachineUpgrades.description(key).isEmpty())lines.add(Component.literal("装入适配机器左侧升级栏；加工中锁定，开工固定效果"));
                if(s.has(CatalogueContent.QUALITY.get())){var q=MaterialQuality.of(s);lines.add(Component.literal("P"+q.purity()+"% I"+q.integrity()+" O"+q.orientation()+" C"+q.contamination()+" · "+q.route()+(q.continuous()?" · 连续":"")));}
            }
            public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext c){
                if(key.equals("s01")&&c.getPlayer()!=null){var target=c.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,c.getClickedPos(),c.getClickedFace());if(target!=null){if(!c.getLevel().isClientSide){var source=energy(c.getItemInHand());int n=target.receiveEnergy(source.extractEnergy(512,true),true);target.receiveEnergy(source.extractEnergy(n,false),false);}return InteractionResult.sidedSuccess(c.getLevel().isClientSide);}}
                return super.useOn(c);
            }
        };
    }
    public static CompoundTag getData(ItemStack s){return s.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    public static void setData(ItemStack s,CompoundTag tag){s.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
    public static boolean lowImpact(ItemStack s){return s.is(CatalogueContent.item("sample_pick"))&&getData(s).getBoolean("LowImpact");}
    public static int nativeIntegrity(Level level,BlockPos pos){long n=net.minecraft.util.Mth.getSeed(pos)^((net.minecraft.server.level.ServerLevel)level).getSeed();n^=n>>>33;n*=0xff51afd7ed558ccdL;n^=n>>>33;return 30+Math.floorMod(n,71);}
    public static IEnergyStorage energy(ItemStack s){return new IEnergyStorage(){
        private long tick(){var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();return server==null?0:server.overworld().getGameTime();}
        public int receiveEnergy(int max,boolean simulate){var t=getData(s);int stored=Math.clamp(t.getInt("Energy"),0,200000),used=t.getLong("ChargeTick")==tick()?t.getInt("ChargeUsed"):0,n=Math.max(0,Math.min(Math.min(max,512-used),(200000-stored)*100/90));if(n<=0)return 0;
            if(!simulate){int budget=n*90+t.getInt("ChargeRemainder");t.putInt("Energy",stored+budget/100);t.putInt("ChargeRemainder",budget%100);t.putLong("ChargeTick",tick());t.putInt("ChargeUsed",used+n);setData(s,t);}return n;}
        public int extractEnergy(int max,boolean simulate){var t=getData(s);int used=t.getLong("DischargeTick")==tick()?t.getInt("DischargeUsed"):0,n=Math.max(0,Math.min(Math.min(max,512-used),Math.clamp(t.getInt("Energy"),0,200000)));if(!simulate&&n>0){t.putInt("Energy",t.getInt("Energy")-n);t.putLong("DischargeTick",tick());t.putInt("DischargeUsed",used+n);setData(s,t);}return n;}
        public int getEnergyStored(){return getData(s).getInt("Energy");}public int getMaxEnergyStored(){return 200000;}public boolean canExtract(){return true;}public boolean canReceive(){return true;}
    };}
    public static void breakSpeed(PlayerEvent.BreakSpeed event){
        var p=event.getEntity();if(!lowImpact(p.getMainHandItem()))return;
        if(event.getState().is(StarfallBlueprint.ORE.get())||event.getState().is(StarfallBlueprint.DEEP_ORE.get()))event.getPosition().ifPresent(pos->event.setNewSpeed(event.getState().getDestroySpeed(p.level(),pos)*30f/200f));
    }
    public static void playerTick(PlayerTickEvent.Post event){
        Player p=event.getEntity();if(p.level().isClientSide)return;
        MiningBagItem.collect(p);
        if(p.tickCount%20==0&&p.getItemBySlot(EquipmentSlot.HEAD).is(CatalogueContent.item("work_goggles"))){
            Vec3 from=p.getEyePosition(),to=from.add(p.getLookAngle().scale(4));if(!p.level().hasChunkAt(BlockPos.containing(to)))return;
            var hit=p.level().clip(new net.minecraft.world.level.ClipContext(from,to,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            if(hit.getType()==HitResult.Type.BLOCK&&(p.level().getBlockState(hit.getBlockPos()).is(StarfallBlueprint.ORE.get())||p.level().getBlockState(hit.getBlockPos()).is(StarfallBlueprint.DEEP_ORE.get()))){int i=nativeIntegrity(p.level(),hit.getBlockPos());p.displayClientMessage(Component.literal(i>=86?"精密候选":i>=56?"可精制":"基础"),true);}
        }
        RopeSystem.playerTick(p);
    }
    private static final class SamplePick extends PickaxeItem {
        SamplePick(Properties p){super(Tiers.IRON,p.durability(300).attributes(PickaxeItem.createAttributes(Tiers.IRON,1,-2.8f)));}
        public InteractionResultHolder<ItemStack>use(Level l,Player p,InteractionHand hand){ItemStack s=p.getItemInHand(hand);if(p.isShiftKeyDown()){if(!l.isClientSide){var data=getData(s);data.putBoolean("LowImpact",!data.getBoolean("LowImpact"));setData(s,data);p.displayClientMessage(Component.literal(data.getBoolean("LowImpact")?"保晶：10秒/块，4耐久":"普通采掘"),true);}return InteractionResultHolder.sidedSuccess(s,l.isClientSide);}return super.use(l,p,hand);}
        public boolean mineBlock(ItemStack s,Level l,net.minecraft.world.level.block.state.BlockState state,BlockPos pos,LivingEntity miner){
            if(lowImpact(s)&&(state.is(StarfallBlueprint.ORE.get())||state.is(StarfallBlueprint.DEEP_ORE.get()))){if(!l.isClientSide)s.hurtAndBreak(4,miner,EquipmentSlot.MAINHAND);return true;}return super.mineBlock(s,l,state,pos,miner);
        }
    }
}
