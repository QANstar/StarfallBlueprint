package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Overworld-owned personal and opt-in scoreboard team fleets. */
public final class OrbitalFleet extends SavedData {
    public static final String[] TYPES={"charge","laser","kinetic","weather"};
    public static final class Fleet {
        public static final int MAX_UPGRADE=8;
        public static final int MAX_COMPUTE = 16;
        public int compute;
        public boolean computeEnabled = true;
        private boolean legacyComputeImported;
        public final int[] count=new int[4],upgrades=new int[3]; public final boolean[] active={true,true,true,true};
        public long energy,lastTick,laserReady,rodReady,weatherReady; public int rods; public boolean chargingEnabled=true,blockDestruction;
        public float strikeDamage(int type){return (type==1?80:220)*(1+.15f*Math.max(0,count[type]-1)+.25f*upgrades[type]);}
        public float strikeRadius(int type){return (type==1?4:12)+(type==1?.15f:.5f)*Math.max(0,count[type]-1)+(type==1?.6f:1.5f)*upgrades[type];}
        public float strikeBlast(int type){return (type==1?2.5f:8)+(type==1?.08f:.15f)*Math.max(0,count[type]-1)+(type==1?.25f:.4f)*upgrades[type];}
        public int chargePerTick(){return 200*count[0]*(1+upgrades[0]);}
        public int capacity(){return Math.min(64000000,(Arrays.stream(count).sum()+compute)*2000000);}
        public void advance(long now){long elapsed=Math.clamp(now-lastTick,0,24000);lastTick=now;int solar=compute*80;for(int i=0;i<4;i++)if(active[i])solar+=count[i]*(i==0?400:80);energy=Math.min(capacity(),energy+elapsed*solar);}

        void loadCompute(CompoundTag tag) {
            compute = Math.clamp(tag.getInt("Compute"), 0, 512);
            computeEnabled = !tag.getBoolean("ComputeDisabled");
            legacyComputeImported = tag.getBoolean("LegacyComputeImported");
        }
        void saveCompute(CompoundTag tag) {
            tag.putInt("Compute", compute);
            tag.putBoolean("ComputeDisabled", !computeEnabled);
            tag.putBoolean("LegacyComputeImported", legacyComputeImported);
        }
    }
    private final Map<UUID,Fleet> fleets=new HashMap<>();
    private final Set<UUID> teamUsers=new HashSet<>();
    public boolean usesTeam(ServerPlayer p){return p.getTeam()!=null&&teamUsers.contains(p.getUUID());}
    public boolean toggleTeam(ServerPlayer p){
        if(teamUsers.remove(p.getUUID())){setDirty();return true;}
        if(p.getTeam()==null)return false;
        teamUsers.add(p.getUUID());setDirty();return true;
    }
    private UUID fleetOwner(ServerPlayer p){return usesTeam(p)?UUID.nameUUIDFromBytes(("starfall_blueprint:team:"+p.getTeam().getName()).getBytes(java.nio.charset.StandardCharsets.UTF_8)):p.getUUID();}
    // Creative services are transient and never saved into a player's real satellite constellation.
    private final Map<UUID,Fleet> creativeFleets=new HashMap<>();
    public Fleet serviceFleet(ServerPlayer player){
        if(!OrbitalTerminalItem.creativeAccess(player))return fleet(player);
        var f=creativeFleets.computeIfAbsent(player.getUUID(),id->{var result=new Fleet();Arrays.fill(result.count,16);Arrays.fill(result.upgrades,Fleet.MAX_UPGRADE);result.compute=Fleet.MAX_COMPUTE;return result;});
        f.energy=f.capacity();f.rods=256;f.lastTick=player.level().getGameTime();return f;
    }
    public static OrbitalFleet get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(OrbitalFleet::new,OrbitalFleet::load),"starfall_haijia_fleets");}
    public Fleet fleet(ServerPlayer player){var f=fleets.computeIfAbsent(fleetOwner(player),id->new Fleet());f.advance(player.serverLevel().getGameTime());setDirty();return f;}
    void migrateEsper(ServerPlayer player, CompoundTag esper) {
        int old = Math.clamp(esper.getInt("Satellites"), 0, 512);
        if (old == 0) return;
        var personal = fleets.computeIfAbsent(player.getUUID(), id -> new Fleet());
        if (personal.legacyComputeImported) return;
        if (personal.compute == 0) personal.computeEnabled = esper.getBoolean("Uplink");
        personal.compute = Math.min(512, personal.compute + old);
        personal.legacyComputeImported = true;
        setDirty();
    }
    public static OrbitalFleet load(CompoundTag tag,HolderLookup.Provider provider){
        var data=new OrbitalFleet();for(var raw:tag.getList("TeamUsers",Tag.TAG_COMPOUND)){var user=(CompoundTag)raw;if(user.hasUUID("Player"))data.teamUsers.add(user.getUUID("Player"));}for(var raw:tag.getList("Fleets",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(!t.hasUUID("Owner"))continue;var f=new Fleet();var counts=t.getIntArray("Count");for(int i=0;i<Math.min(4,counts.length);i++){f.count[i]=Math.clamp(counts[i],0,16);f.active[i]=!t.getBoolean("Disabled"+i);}var upgrades=t.getIntArray("Upgrades");for(int i=0;i<Math.min(3,upgrades.length);i++)f.upgrades[i]=Math.clamp(upgrades[i],0,Fleet.MAX_UPGRADE);f.blockDestruction=t.getBoolean("BlockDestruction");f.chargingEnabled=t.contains("ChargingEnabled")?t.getBoolean("ChargingEnabled"):!t.getBoolean("Disabled0");f.loadCompute(t);f.energy=Math.clamp(t.getLong("Energy"),0,f.capacity());f.lastTick=t.getLong("Time");f.laserReady=t.getLong("Laser");f.rodReady=t.getLong("Rod");f.weatherReady=t.getLong("Weather");f.rods=Math.clamp(t.getInt("Rods"),0,256);data.fleets.put(t.getUUID("Owner"),f);}return data;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider provider){var list=new ListTag();fleets.forEach((owner,f)->{var t=new CompoundTag();t.putUUID("Owner",owner);f.saveCompute(t);t.putIntArray("Count",f.count);t.putIntArray("Upgrades",f.upgrades);for(int i=0;i<4;i++)t.putBoolean("Disabled"+i,!f.active[i]);t.putBoolean("ChargingEnabled",f.chargingEnabled);t.putBoolean("BlockDestruction",f.blockDestruction);t.putLong("Energy",f.energy);t.putLong("Time",f.lastTick);t.putLong("Laser",f.laserReady);t.putLong("Rod",f.rodReady);t.putLong("Weather",f.weatherReady);t.putInt("Rods",f.rods);list.add(t);});root.put("Fleets",list);var users=new ListTag();for(UUID id:teamUsers){var user=new CompoundTag();user.putUUID("Player",id);users.add(user);}root.put("TeamUsers",users);return root;}
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
