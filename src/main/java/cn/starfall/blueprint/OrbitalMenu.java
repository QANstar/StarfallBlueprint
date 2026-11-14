package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** All costs are planned against copies before any inventory, energy or orbit mutation. */
public final class OrbitalMenu extends AbstractContainerMenu {
    public record Recipe(String output,String knowledge,int energy,Map<String,Integer> inputs){}
    public static final List<Recipe> RECIPES=List.of(
        new Recipe("hj_sat_charge","charge",160000,Map.of("hj_bus",1,"hj_solar_wing",4,"hj_coil",4,"hj_thruster",1)),
        new Recipe("hj_sat_laser","laser",320000,Map.of("hj_bus",1,"hj_solar_wing",2,"hj_laser_array",2,"hj_thruster",1)),
        new Recipe("hj_sat_kinetic","kinetic",320000,Map.of("hj_bus",1,"hj_solar_wing",2,"hj_tungsten_rod",8,"hj_thruster",1)),
        new Recipe("hj_sat_weather","weather",240000,Map.of("hj_bus",1,"hj_solar_wing",2,"hj_weather_array",2,"hj_thruster",1)),
        new Recipe("hj_fighter","flight",640000,Map.of("hj_airframe",1,"hj_engine",2,"hj_laser_array",2,"hj_processor",4,"hj_coil",8)),
        new Recipe("hj_sat_charge_upgrade","charge",320000,Map.of("hj_bus",1,"hj_solar_wing",6,"hj_coil",8,"hj_thruster",1)),
        new Recipe("hj_sat_laser_upgrade","laser",480000,Map.of("hj_bus",1,"hj_solar_wing",2,"hj_laser_array",4,"hj_processor",4,"hj_thruster",1)),
        new Recipe("hj_sat_kinetic_upgrade","kinetic",480000,Map.of("hj_bus",1,"hj_solar_wing",2,"hj_tungsten_rod",8,"hj_processor",4,"hj_thruster",1)));
    private final ContainerLevelAccess access;
    private final Inventory inventory;
    public final int[] info=new int[24];
    public OrbitalMenu(int id,Inventory inv){this(id,inv,ContainerLevelAccess.NULL,4);}
    public OrbitalMenu(int id,Inventory inv,ContainerLevelAccess access,int kind){
        super(Haijia.MENU.get(),id);this.access=access;inventory=inv;info[0]=kind;
        for(int i=0;i<info.length;i++){final int n=i;addDataSlot(new DataSlot(){public int get(){return info[n];}public void set(int v){info[n]=v;}});}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,col+row*9+9,96+col*18,198+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,96+col*18,256));
    }
    @Override public void broadcastChanges(){
        if(inventory.player instanceof ServerPlayer p){var data=OrbitalFleet.get(p.serverLevel());var f=info[0]>=3?data.serviceFleet(p):data.fleet(p);for(int i=0;i<4;i++){info[1+i]=f.count[i];info[5+i]=(i==0?f.chargingEnabled&&f.active[0]:f.active[i])?1:0;}info[9]=(int)(f.energy/1000);info[10]=f.rods;info[11]=(int)Math.max(0,(f.laserReady-p.level().getGameTime()+19)/20);info[12]=(int)Math.max(0,(f.rodReady-p.level().getGameTime()+19)/20);info[13]=(int)Math.max(0,(f.weatherReady-p.level().getGameTime()+19)/20);for(int i=0;i<3;i++)info[18+i]=f.upgrades[i];info[21]=f.chargePerTick()/50;info[16]=f.capacity()/1000;info[17]=OrbitalFleet.hasTerminal(p)?1:0;info[15]=CatalogueEquipment.getData(OrbitalTerminalItem.find(p)).getInt("OrbitalMode");access.execute((l,pos)->{if(l.getBlockEntity(pos) instanceof OrbitalBlockEntity b)info[14]=b.energy.getEnergyStored()/1000;});}
        super.broadcastChanges();
    }
    public static boolean transact(Player p,Map<String,Integer> cost,ItemStack output){
        var copies=p.getInventory().items.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for(var entry:cost.entrySet()){int left=entry.getValue();for(ItemStack s:copies)if(s.is(CatalogueContent.item(entry.getKey()))){int n=Math.min(left,s.getCount());s.shrink(n);left-=n;}if(left>0)return false;}
        var rest=output.copy();for(ItemStack s:copies)if(!s.isEmpty()&&ItemStack.isSameItemSameComponents(s,rest)){int n=Math.min(rest.getCount(),s.getMaxStackSize()-s.getCount());s.grow(n);rest.shrink(n);}
        for(int i=0;i<copies.size()&&!rest.isEmpty();i++)if(copies.get(i).isEmpty()){int n=Math.min(rest.getCount(),rest.getMaxStackSize());copies.set(i,rest.copyWithCount(n));rest.shrink(n);}if(!rest.isEmpty())return false;
        for(int i=0;i<36;i++)p.getInventory().setItem(i,copies.get(i));p.getInventory().setChanged();return true;
    }
    @Override public boolean clickMenuButton(Player player,int action){
        if(!(player instanceof ServerPlayer p)||!stillValid(p)||!p.isAlive()||p.isSpectator())return false;
        var data=OrbitalFleet.get(p.serverLevel());var f=info[0]>=3?data.serviceFleet(p):data.fleet(p);boolean[] success={false};
        if(info[0]==1&&action>=0&&action<RECIPES.size())access.execute((l,pos)->{
            if(!(l.getBlockEntity(pos) instanceof OrbitalBlockEntity b)||b.kind()!=1||!OrbitalBlock.complete(b))return;
            var r=RECIPES.get(action);if(!Haijia.knows(p,r.knowledge())||b.energy.getEnergyStored()<r.energy())return;
            if(transact(p,r.inputs(),Haijia.stack(r.output(),1))){b.energy.extractEnergy(r.energy(),false); // drain all assembly cost, bypass per-tick port limit below
                int remaining=r.energy()-Math.min(8192,r.energy());while(remaining>0){int n=b.energy.extractEnergy(remaining,false);if(n==0)break;remaining-=n;}success[0]=true;}
        });
        if(info[0]==2&&(action>=100&&action<104||action>=104&&action<=106))access.execute((l,pos)->{
            boolean upgrade=action>=104;int type=upgrade?action-104:action-100;if(!(l.getBlockEntity(pos) instanceof OrbitalBlockEntity b)||b.kind()!=2||!Haijia.knows(p,OrbitalFleet.TYPES[type])||(!upgrade&&type!=2&&f.count[type]>=16)||(type==2&&f.rods>248)||(upgrade&&(f.count[type]==0||f.upgrades[type]>=OrbitalFleet.Fleet.MAX_UPGRADE))||b.energy.getEnergyStored()<200000||!OrbitalBlock.complete(b))return;
            var def=OrbitalStructure.of("launchpad");var facing=b.getBlockState().getValue(OrbitalBlock.FACING);
            var center=pos.offset(def.offset(facing,(def.depth/2)*def.width+def.width/2));
            for(int y=pos.getY()+def.height;y<l.getMaxBuildHeight();y++){var above=new BlockPos(center.getX(),y,center.getZ());if(!l.getBlockState(above).getCollisionShape(l,above).isEmpty())return;}
            if(!transact(p,Map.of("hj_sat_"+OrbitalFleet.TYPES[type]+(upgrade?"_upgrade":""),1,"hj_launch_vehicle",1),ItemStack.EMPTY))return;
            for(int n=200000;n>0;){int take=b.energy.extractEnergy(n,false);n-=take;}
            if(upgrade)f.upgrades[type]++;else f.count[type]=Math.min(16,f.count[type]+1);f.lastTick=l.getGameTime();if(type==2)f.rods=Math.min(256,f.rods+8);data.setDirty();success[0]=true;
            var fx=Haijia.STRIKE.get().create(l);if(fx!=null){fx.setPos(center.getX()+.5,pos.getY()+.7,center.getZ()+.5);fx.configure(3,p.getUUID(),80);l.addFreshEntity(fx);}
        });
        if(info[0]>=3){
            if(action==110&&f.count[0]>0){f.chargingEnabled=!(f.chargingEnabled&&f.active[0]);f.active[0]=true;success[0]=true;}
            if(action>=120&&action<=122)success[0]=OrbitalTerminalItem.weather(p,action-120);
            if(action>=200&&action<=204){if(action==201&&f.count[1]>0)f.active[1]=true;if(action==202&&f.count[2]>0)f.active[2]=true;ItemStack terminal=OrbitalTerminalItem.find(p);if(!terminal.isEmpty()){var t=CatalogueEquipment.getData(terminal);t.putInt("OrbitalMode",action-200);CatalogueEquipment.setData(terminal,t);success[0]=true;}}
        }
        if(success[0])data.setDirty();else p.displayClientMessage(Haijia.text("operation_failed"),true);broadcastChanges();return success[0];
    }
    @Override public boolean stillValid(Player p){if(info[0]==4)return p.isAlive()&&!OrbitalTerminalItem.find(p).isEmpty();return access.evaluate((l,pos)->l.getBlockEntity(pos) instanceof OrbitalBlockEntity b&&b.kind()==info[0]&&OrbitalBlock.complete(b)&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64,false);}
    @Override public ItemStack quickMoveStack(Player p,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);var s=slot.getItem();var copy=s.copy();if(!moveItemStackTo(s,index<27?27:0,index<27?36:27,false))return ItemStack.EMPTY;slot.setChanged();return copy;}
}
