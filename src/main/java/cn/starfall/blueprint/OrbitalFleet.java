package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Overworld-owned per-player fleets; dimension changes and death do not duplicate satellites. */
public final class OrbitalFleet extends SavedData {
    public static final String[] TYPES={"charge","laser","kinetic","weather"};
    public static final class Fleet {
        public static final int MAX_UPGRADE=8;
        public final int[] count=new int[4],upgrades=new int[3]; public final boolean[] active={true,true,true,true};
        public long energy,lastTick,laserReady,rodReady,weatherReady; public int rods; public boolean chargingEnabled=true;
        public float strikeDamage(int type){return (type==1?80:220)*(1+.15f*Math.max(0,count[type]-1)+.25f*upgrades[type]);}
        public float strikeRadius(int type){return (type==1?4:12)+(type==1?.15f:.5f)*Math.max(0,count[type]-1)+(type==1?.6f:1.5f)*upgrades[type];}
        public float strikeBlast(int type){return (type==1?2.5f:8)+(type==1?.08f:.15f)*Math.max(0,count[type]-1)+(type==1?.25f:.4f)*upgrades[type];}
        public int chargePerTick(){return 200*count[0]*(1+upgrades[0]);}
        public int capacity(){return Math.min(64000000,Arrays.stream(count).sum()*2000000);}
        public void advance(long now){long elapsed=Math.clamp(now-lastTick,0,24000);lastTick=now;int solar=0;for(int i=0;i<4;i++)if(active[i])solar+=count[i]*(i==0?400:80);energy=Math.min(capacity(),energy+elapsed*solar);}
    }
    private final Map<UUID,Fleet> fleets=new HashMap<>();
    // Creative services are transient and never saved into a player's real satellite constellation.
    private final Map<UUID,Fleet> creativeFleets=new HashMap<>();
    public Fleet serviceFleet(ServerPlayer player){
        if(!OrbitalTerminalItem.creativeAccess(player))return fleet(player);
        var f=creativeFleets.computeIfAbsent(player.getUUID(),id->{var result=new Fleet();Arrays.fill(result.count,16);Arrays.fill(result.upgrades,Fleet.MAX_UPGRADE);return result;});
        f.energy=f.capacity();f.rods=256;f.lastTick=player.level().getGameTime();return f;
    }
    public static OrbitalFleet get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(OrbitalFleet::new,OrbitalFleet::load),"starfall_haijia_fleets");}
    public Fleet fleet(ServerPlayer player){var f=fleets.computeIfAbsent(player.getUUID(),id->new Fleet());f.advance(player.serverLevel().getGameTime());setDirty();return f;}
    public static OrbitalFleet load(CompoundTag tag,HolderLookup.Provider provider){
        var data=new OrbitalFleet();for(var raw:tag.getList("Fleets",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(!t.hasUUID("Owner"))continue;var f=new Fleet();var counts=t.getIntArray("Count");for(int i=0;i<Math.min(4,counts.length);i++){f.count[i]=Math.clamp(counts[i],0,16);f.active[i]=!t.getBoolean("Disabled"+i);}var upgrades=t.getIntArray("Upgrades");for(int i=0;i<Math.min(3,upgrades.length);i++)f.upgrades[i]=Math.clamp(upgrades[i],0,Fleet.MAX_UPGRADE);f.chargingEnabled=t.contains("ChargingEnabled")?t.getBoolean("ChargingEnabled"):!t.getBoolean("Disabled0");f.energy=Math.clamp(t.getLong("Energy"),0,f.capacity());f.lastTick=t.getLong("Time");f.laserReady=t.getLong("Laser");f.rodReady=t.getLong("Rod");f.weatherReady=t.getLong("Weather");f.rods=Math.clamp(t.getInt("Rods"),0,256);data.fleets.put(t.getUUID("Owner"),f);}return data;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider provider){var list=new ListTag();fleets.forEach((owner,f)->{var t=new CompoundTag();t.putUUID("Owner",owner);t.putIntArray("Count",f.count);t.putIntArray("Upgrades",f.upgrades);for(int i=0;i<4;i++)t.putBoolean("Disabled"+i,!f.active[i]);t.putBoolean("ChargingEnabled",f.chargingEnabled);t.putLong("Energy",f.energy);t.putLong("Time",f.lastTick);t.putLong("Laser",f.laserReady);t.putLong("Rod",f.rodReady);t.putLong("Weather",f.weatherReady);t.putInt("Rods",f.rods);list.add(t);});root.put("Fleets",list);return root;}
    public static boolean hasTerminal(ServerPlayer p){return !OrbitalTerminalItem.find(p).isEmpty();}
    public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!p.isAlive()||p.isSpectator()||!hasTerminal(p))return;
        var data=get(p.serverLevel());var f=data.serviceFleet(p);if(f.count[0]==0||!f.active[0]||!f.chargingEnabled||!p.level().dimensionType().hasSkyLight())return;
        // One shared per-tick budget. Standard receiver rate limits remain authoritative.
        int budget=(int)Math.min(f.energy,f.chargePerTick());var slots=new ArrayList<ItemStack>();slots.addAll(p.getInventory().items);slots.addAll(p.getInventory().armor);slots.addAll(p.getInventory().offhand);Collections.rotate(slots,-(p.tickCount%slots.size()));
        for(ItemStack s:slots){if(budget<=0)break;var port=s.getCapability(Capabilities.EnergyStorage.ITEM);if(port==null||!port.canReceive())continue;int accepted=port.receiveEnergy(budget,false);budget-=accepted;f.energy-=accepted;}
        data.setDirty();p.getInventory().setChanged();
    }
}
