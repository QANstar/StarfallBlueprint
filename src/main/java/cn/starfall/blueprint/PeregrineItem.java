package cn.starfall.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class PeregrineItem extends Item {
    private final boolean creativeOnly;
    public PeregrineItem(Properties p){this(p,false);}
    public PeregrineItem(Properties p,boolean creativeOnly){super(p.stacksTo(1));this.creativeOnly=creativeOnly;}
    public static void prepareCreative(ItemStack s){var t=CatalogueEquipment.getData(s);t.putBoolean("CreativeAircraft",true);t.putInt("FlightEnergy",2000000);t.putInt("Missiles",16);t.putInt("Bombs",8);t.putFloat("Hull",160);CatalogueEquipment.setData(s,t);}
    @Override public void inventoryTick(ItemStack s,net.minecraft.world.level.Level l,net.minecraft.world.entity.Entity e,int slot,boolean selected){if(!l.isClientSide&&creativeOnly&&e instanceof Player p&&p.isCreative()&&(energy(s).getEnergyStored()!=2000000||!CatalogueEquipment.getData(s).getBoolean("CreativeAircraft")))prepareCreative(s);}

    public static IEnergyStorage energy(ItemStack s){return new IEnergyStorage(){
        public int receiveEnergy(int max,boolean simulate){var t=CatalogueEquipment.getData(s);int n=Math.max(0,Math.min(Math.min(8192,max),2000000-getEnergyStored()));if(!simulate&&n>0){t.putInt("FlightEnergy",getEnergyStored()+n);CatalogueEquipment.setData(s,t);}return n;}
        public int extractEnergy(int n,boolean sim){return 0;}public int getEnergyStored(){return Math.clamp(CatalogueEquipment.getData(s).getInt("FlightEnergy"),0,2000000);}public int getMaxEnergyStored(){return 2000000;}public boolean canReceive(){return true;}public boolean canExtract(){return false;}};}
    @Override public InteractionResult useOn(UseOnContext c){
        var p=c.getPlayer();if(p==null||creativeOnly&&!p.isCreative()||!Haijia.knows(p,"flight"))return InteractionResult.FAIL;var l=c.getLevel();var s=c.getItemInHand();if(creativeOnly&&!l.isClientSide)prepareCreative(s);
        if(p.isShiftKeyDown()){
            if(!l.isClientSide){var from=l.getCapability(Capabilities.EnergyStorage.BLOCK,c.getClickedPos(),c.getClickedFace());if(from==null||!from.canExtract())return InteractionResult.FAIL;int n=energy(s).receiveEnergy(from.extractEnergy(8192,true),true);int got=from.extractEnergy(n,false);energy(s).receiveEnergy(got,false);}
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        if(l.isClientSide)return InteractionResult.SUCCESS;var pos=c.getClickedPos().relative(c.getClickedFace());if(!l.hasChunkAt(pos)||!l.mayInteract(p,pos))return InteractionResult.FAIL;
        var f=Haijia.FIGHTER.get().create(l);if(f==null)return InteractionResult.FAIL;f.setPos(pos.getX()+.5,pos.getY()+.1,pos.getZ()+.5);f.setYRot(p.getYRot());f.unpack(CatalogueEquipment.getData(s));
        if(!l.noCollision(f)||!l.addFreshEntity(f))return InteractionResult.FAIL;if(!p.isCreative())s.shrink(1);return InteractionResult.CONSUME;
    }
}
