package cn.starfall.blueprint;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Persistent charge/thermal/wear state; worn-out equipment remains serviceable instead of vanishing. */
public final class BestEquipment {
    public static final int CAPACITY=200000, MAX_HEAT=1000, RECOVER_HEAT=400, MAX_WEAR=1200;
    public static boolean isEquipment(ItemStack stack){return stack.getItem() instanceof Frame||stack.getItem() instanceof Cutter;}
    public static int charge(ItemStack stack){return Math.clamp(CatalogueEquipment.getData(stack).getInt("BestEnergy"),0,CAPACITY);}
    public static int heat(ItemStack stack){return Math.clamp(CatalogueEquipment.getData(stack).getInt("BestHeat"),0,MAX_HEAT);}
    public static int wear(ItemStack stack){return Math.clamp(CatalogueEquipment.getData(stack).getInt("BestWear"),0,MAX_WEAR);}
    public static void setWear(ItemStack stack,int wear){var data=CatalogueEquipment.getData(stack);data.putInt("BestWear",Math.clamp(wear,0,MAX_WEAR));CatalogueEquipment.setData(stack,data);}
    public static boolean ready(ItemStack stack,int energy){return isEquipment(stack)&&charge(stack)>=energy&&wear(stack)<MAX_WEAR&&!CatalogueEquipment.getData(stack).getBoolean("BestOverheated")&&heat(stack)<MAX_HEAT;}
    public static void spend(ItemStack stack,int energy,int heating){
        var data=CatalogueEquipment.getData(stack);data.putInt("BestEnergy",Math.max(0,charge(stack)-energy));int h=Math.min(MAX_HEAT,heat(stack)+heating);data.putInt("BestHeat",h);if(h==MAX_HEAT)data.putBoolean("BestOverheated",true);CatalogueEquipment.setData(stack,data);
    }
    public static void cool(ItemStack stack){
        int old=heat(stack),next=Math.max(0,old-2);if(old==0&&!CatalogueEquipment.getData(stack).getBoolean("BestOverheated"))return;
        var data=CatalogueEquipment.getData(stack);data.putInt("BestHeat",next);if(next<=RECOVER_HEAT)data.putBoolean("BestOverheated",false);CatalogueEquipment.setData(stack,data);
    }
    public static String status(ItemStack stack){
        String state=wear(stack)>=MAX_WEAR?"需维修":CatalogueEquipment.getData(stack).getBoolean("BestOverheated")?"过热停机":charge(stack)<(stack.getItem() instanceof Cutter?256:16)?"电量不足":"就绪";
        return state+" · "+charge(stack)+"/"+CAPACITY+"XH · 温度 "+heat(stack)+"/"+MAX_HEAT+" · 耐久 "+(MAX_WEAR-wear(stack))+"/"+MAX_WEAR;
    }
    public static IEnergyStorage energy(ItemStack stack){return new IEnergyStorage(){
        private long tick(){var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();return server==null?0:server.overworld().getGameTime();}
        public int receiveEnergy(int max,boolean simulate){var data=CatalogueEquipment.getData(stack);int used=data.getLong("BestChargeTick")==tick()?data.getInt("BestChargeUsed"):0;int n=Math.max(0,Math.min(Math.min(max,512-used),CAPACITY-charge(stack)));if(n>0&&!simulate){data.putInt("BestEnergy",charge(stack)+n);data.putLong("BestChargeTick",tick());data.putInt("BestChargeUsed",used+n);CatalogueEquipment.setData(stack,data);}return n;}
        public int extractEnergy(int max,boolean simulate){return 0;}
        public int getEnergyStored(){return charge(stack);}public int getMaxEnergyStored(){return CAPACITY;}public boolean canReceive(){return true;}public boolean canExtract(){return false;}
    };}
    public static int recharge(Player player,ItemStack target){
        if(player.level().isClientSide||!isEquipment(target))return 0;
        int total=0;
        for(int i=0;i<36;i++){
            var source=player.getInventory().getItem(i);if(!source.is(CatalogueContent.item("s01")))continue;
            var battery=CatalogueEquipment.energy(source);var receiver=energy(target);int n=receiver.receiveEnergy(battery.extractEnergy(512,true),true);
            if(n>0){int extracted=battery.extractEnergy(n,false);total+=receiver.receiveEnergy(extracted,false);}
            if(total>=512||charge(target)>=CAPACITY)break;
        }
        return total;
    }
    private static InteractionResultHolder<ItemStack> startCharging(Level level,Player player,InteractionHand hand){
        player.startUsingItem(hand);return InteractionResultHolder.consume(player.getItemInHand(hand));
    }
    private static void tooltip(ItemStack stack,List<Component> lines){
        lines.add(Component.literal(status(stack)));lines.add(Component.literal("主手潜行按住右键：从背包工具能匣补能（512XH/刻）"));
        lines.add(Component.literal("过热后冷却至400恢复；装配台持主手维修，1钢恢复200耐久"));
        lines.add(Component.literal(stack.getItem() instanceof Frame?"腿部穿戴；基础工务；地面疾跑速度+15%，16XH/刻":"工程钻切；铁级单点采掘；每块256XH、热量120、耐久1"));
    }
    public static final class Frame extends ArmorItem {
        public Frame(Properties p){super(ArmorMaterials.LEATHER,Type.LEGGINGS,p.stacksTo(1).attributes(ItemAttributeModifiers.EMPTY));}
        @Override public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack){return ItemAttributeModifiers.EMPTY;}
        @Override public net.minecraft.resources.ResourceLocation getArmorTexture(ItemStack stack,Entity entity,EquipmentSlot slot,ArmorMaterial.Layer layer,boolean innerModel){return StarfallBlueprint.id("textures/models/armor/best_frame_layer_2.png");}
        @Override public boolean isValidRepairItem(ItemStack stack,ItemStack ingredient){return false;}
        @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){if(!level.isClientSide)cool(stack);}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){return player.isShiftKeyDown()?startCharging(level,player,hand):super.use(level,player,hand);}
        @Override public int getUseDuration(ItemStack stack,LivingEntity entity){return 72000;}
        @Override public void onUseTick(Level level,LivingEntity entity,ItemStack stack,int remaining){if(entity instanceof Player player)recharge(player,stack);}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags){tooltip(stack,lines);}
        @Override public boolean isBarVisible(ItemStack stack){return true;}
        @Override public int getBarWidth(ItemStack stack){return charge(stack)*13/CAPACITY;}
        @Override public int getBarColor(ItemStack stack){return 0x49bca3;}
    }
    public static final class Cutter extends Item {
        public Cutter(Properties p){super(p.stacksTo(1).component(net.minecraft.core.component.DataComponents.TOOL,Tiers.IRON.createToolProperties(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)));}
        @Override public boolean isValidRepairItem(ItemStack stack,ItemStack ingredient){return false;}
        @Override public float getDestroySpeed(ItemStack stack,BlockState state){return ready(stack,256)?super.getDestroySpeed(stack,state)*2:0;}
        @Override public boolean canAttackBlock(BlockState state,Level level,BlockPos pos,Player player){return BestEngineering.researchLevel(player)>=2&&ready(player.getMainHandItem(),256)&&super.canAttackBlock(state,level,pos,player);}
        @Override public boolean mineBlock(ItemStack stack,Level level,BlockState state,BlockPos pos,LivingEntity entity){
            if(!level.isClientSide&&state.getDestroySpeed(level,pos)!=0){if(!ready(stack,256))return false;spend(stack,256,120);setWear(stack,wear(stack)+1);}return true;
        }
        @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){return true;}
        @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){if(!level.isClientSide)cool(stack);}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){return player.isShiftKeyDown()?startCharging(level,player,hand):super.use(level,player,hand);}
        @Override public int getUseDuration(ItemStack stack,LivingEntity entity){return 72000;}
        @Override public void onUseTick(Level level,LivingEntity entity,ItemStack stack,int remaining){if(entity instanceof Player player)recharge(player,stack);}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags){tooltip(stack,lines);}
        @Override public boolean isBarVisible(ItemStack stack){return true;}
        @Override public int getBarWidth(ItemStack stack){return charge(stack)*13/CAPACITY;}
        @Override public int getBarColor(ItemStack stack){return 0x49bca3;}
    }
}
