package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Shared display snapshot and atomic drag-to-connect transactions. Display data never grants items. */
public final class BlackDogWorkstations {
    public static final int[] PART_X={132,132,300,300,300,132,300,132};
    public static final int[] PART_Y={53,87,53,121,155,121,87,155};
    public static int optionX(int ability){int slot=Lingchuan.MODULES.get(24+ability).slot(),row=0;for(int i=0;i<ability;i++)if(Lingchuan.MODULES.get(24+i).slot()==slot)row++;return PART_X[slot]<200?22+row*30:408-row*30;}
    public static int optionY(int ability){return PART_Y[Lingchuan.MODULES.get(24+ability).slot()];}
    public static CompoundTag preview(Container inventory){
        ItemStack watch=inventory.getItem(8);if(!(watch.getItem() instanceof BlackDogArmor.Watch))watch=inventory.getItem(9);
        CompoundTag data=watch.getItem() instanceof BlackDogArmor.Watch?CatalogueEquipment.getData(watch).copy():new CompoundTag();
        boolean stored=watch.getItem() instanceof BlackDogArmor.Watch;
        int[] tiers=data.getIntArray("ChassisTiers");if(tiers.length!=8)tiers=new int[8];
        if(!stored)for(int i=0;i<8;i++){var base=Lingchuan.module(inventory.getItem(i));if(base!=null&&base.ability().isEmpty()&&base.slot()==i)tiers[i]=base.tier();}
        int mask=data.getInt("Abilities");if(!stored)for(int i=10;i<22;i++){var component=Lingchuan.module(inventory.getItem(i));if(component!=null&&!component.ability().isEmpty()&&tiers[component.slot()]>=component.tier())mask|=1<<Lingchuan.ABILITIES.indexOf(component.ability());}
        int load=0;for(int tier:tiers)if(tier>0)load++;
        for(int i=0;i<Lingchuan.ABILITIES.size();i++)if((mask&(1<<i))!=0)load+=Lingchuan.MODULES.get(24+i).load();
        // Keep packets compact; no serialized inventory, identity or unrelated watch state.
        var out=new CompoundTag();out.putIntArray("ChassisTiers",tiers);out.putInt("Abilities",mask);out.putInt("CoreTier",tiers[2]);out.putInt("Load",load);out.putInt("Energy",data.getInt("Energy"));return out;
    }
    public static int interfaceLoad(CompoundTag data,int part){int used=0;for(int i=0;i<Lingchuan.ABILITIES.size();i++){var m=Lingchuan.MODULES.get(24+i);if(m.slot()==part&&(data.getInt("Abilities")&(1<<i))!=0)used+=m.load();}return used;}
    public static boolean connect(Player player,IndustryBlockEntity be,int ability){
        if(!be.kind().equals("lc_armor_assembler")||ability<0||ability>=Lingchuan.ABILITIES.size())return false;
        var component=Lingchuan.MODULES.get(24+ability);if(!Lingchuan.knows(player,component.research()))return Lingchuan.fail(player,"lc.research_locked");
        var watch=be.getItem(8);boolean refit=watch.getItem() instanceof BlackDogArmor.Watch;
        if(refit&&(!BlackDogArmor.valid(watch)||BlackDogArmor.deployed(watch)))return Lingchuan.fail(player,"lc.watch_missing");
        // Prefer a previously owned staging item; otherwise take exactly one from the main inventory.
        int staged=-1,source=-1;for(int i=10;i<22;i++)if(be.getItem(i).is(CatalogueContent.item(component.key()))){staged=i;break;}
        if(!refit&&staged>=0)return true;
        if(player.containerMenu.getCarried().is(CatalogueContent.item(component.key())))source=-2;
        else for(int i=0;i<36;i++)if(player.getInventory().getItem(i).is(CatalogueContent.item(component.key()))){source=i;break;}
        if(staged<0&&source==-1)return Lingchuan.fail(player,"lc.materials_missing");
        if(refit){
            var components=new ArrayList<>(BlackDogArmor.components(watch));components.add(CatalogueContent.stack(component.key(),1));
            if(!BlackDogArmor.compatible(BlackDogArmor.modules(watch),components))return Lingchuan.fail(player,"lc.incompatible");
            if(be.getPower()<5000)return Lingchuan.fail(player,"lc.power_missing");
            int energy=BlackDogArmor.charge(watch);BlackDogArmor.configure(watch,BlackDogArmor.modules(watch),components);BlackDogArmor.setCharge(watch,energy);
            if(staged>=0)be.removeItem(staged,1);else takeSource(player,source);
            be.setPower(be.getPower()-5000);be.setChanged();return true;
        }
        var preview=preview(be);int[] tiers=preview.getIntArray("ChassisTiers");
        if(tiers[component.slot()]<component.tier()||interfaceLoad(preview,component.slot())+component.load()>Lingchuan.interfaces(tiers[component.slot()])
                ||(tiers[2]>0&&preview.getInt("Load")+component.load()>BlackDogArmor.budget(tiers[2])))return Lingchuan.fail(player,"lc.incompatible");
        int destination=-1;for(int i=10;i<22;i++)if(be.getItem(i).isEmpty()){destination=i;break;}
        if(destination<0)return Lingchuan.fail(player,"lc.output_full");
        var owned=takeSource(player,source);be.setItem(destination,owned);return true;
    }
    private static ItemStack takeSource(Player player,int source){if(source!=-2)return player.getInventory().removeItem(source,1);var carried=player.containerMenu.getCarried();var owned=carried.split(1);if(carried.isEmpty())player.containerMenu.setCarried(ItemStack.EMPTY);return owned;}
    public static boolean disconnect(Player player,IndustryBlockEntity be,int ability){
        if(!be.kind().equals("lc_armor_assembler")||ability<0||ability>=Lingchuan.ABILITIES.size())return false;
        if(be.getItem(8).getItem() instanceof BlackDogArmor.Watch)return Lingchuan.removeComponent(player,be,ability);
        var item=CatalogueContent.item(Lingchuan.MODULES.get(24+ability).key());int source=-1,destination=-1;
        for(int i=10;i<22;i++)if(be.getItem(i).is(item)){source=i;break;}
        if(source<0)return false;for(int i=0;i<36;i++)if(player.getInventory().getItem(i).isEmpty()){destination=i;break;}
        if(destination<0)return Lingchuan.fail(player,"lc.output_full");
        player.getInventory().setItem(destination,be.removeItem(source,1));return true;
    }
    private BlackDogWorkstations(){}
}
