package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;

/** Explicit edges, loaded endpoints only. Relays never create power or load chunks. */
public final class OrbitalBlockEntity extends BlockEntity {
    public final Set<BlockPos> links=new LinkedHashSet<>();
    public final EnergyStorage energy=new EnergyStorage(2000000,8192,8192){@Override public int receiveEnergy(int n,boolean sim){int r=super.receiveEnergy(n,sim);if(r>0&&!sim)setChanged();return r;}@Override public int extractEnergy(int n,boolean sim){int r=super.extractEnergy(n,sim);if(r>0&&!sim)setChanged();return r;}};
    public OrbitalBlockEntity(BlockPos p,BlockState s){super(Haijia.BE.get(),p,s);}
    public int kind(){for(int i=0;i<4;i++)if(getBlockState().is(Haijia.BLOCKS.get(List.of("relay","assembler","launchpad","console").get(i)).get()))return i;return -1;}
    public boolean link(OrbitalBlockEntity b){
        if(!OrbitalBlock.complete(this)||!OrbitalBlock.complete(b)||b==this||kind()!=0||b.kind()!=0||level!=b.level||worldPosition.distSqr(b.worldPosition)>4096||links.size()>=8||b.links.size()>=8)return false;
        links.add(b.worldPosition);b.links.add(worldPosition);setChanged();b.setChanged();return true;
    }
    public static void tick(Level l,BlockPos p,BlockState s,OrbitalBlockEntity b){
        if(b.kind()!=0||!OrbitalBlock.complete(b)||l.hasNeighborSignal(p))return;
        for(BlockPos other:new ArrayList<>(b.links)){
            if(!l.hasChunkAt(other))continue;
            if(!(l.getBlockEntity(other) instanceof OrbitalBlockEntity target)||target.kind()!=0||!target.links.contains(p)||p.distSqr(other)>4096){b.links.remove(other);b.setChanged();continue;}
            if(l.hasNeighborSignal(other))continue;
            int amount=Math.min(4096,(b.energy.getEnergyStored()-target.energy.getEnergyStored())/2);
            if(amount>0){int n=target.energy.receiveEnergy(amount,true);int removed=b.energy.extractEnergy(n,false);target.energy.receiveEnergy(removed,false);}
        }
        // Eight-block spherical service radius; stagger inspection across 20 ticks.
        long slot=l.getGameTime()%20;int budget=2048;
        var candidates=new ArrayList<BlockPos>();
        for(BlockPos q:BlockPos.betweenClosed(p.offset(-8,-8,-8),p.offset(8,8,8)))
            if(Math.floorMod(q.asLong(),20)==slot&&p.distSqr(q)<=64&&l.hasChunkAt(q))candidates.add(q.immutable());
        if(!candidates.isEmpty())Collections.rotate(candidates,(int)(l.getGameTime()/20%candidates.size()));
        for(BlockPos q:candidates){
            if(budget==0||b.energy.getEnergyStored()==0)break;
            if(Math.floorMod(q.asLong(),20)!=slot||p.distSqr(q)>64||p.equals(q)||!l.hasChunkAt(q)||l.getBlockEntity(q) instanceof OrbitalBlockEntity r&&r.kind()==0)continue;
            var port=l.getCapability(Capabilities.EnergyStorage.BLOCK,q,null);
            if(port!=null&&port.canReceive()){int n=port.receiveEnergy(Math.min(budget,b.energy.getEnergyStored()),true);int take=b.energy.extractEnergy(n,false);int accepted=port.receiveEnergy(take,false);if(accepted<take)b.energy.receiveEnergy(take-accepted,false);budget-=accepted;}
        }
        if(l.getGameTime()%40==0&&b.energy.getEnergyStored()>0&&l instanceof ServerLevel server)
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,p.getX()+.5,p.getY()+1.2,p.getZ()+.5,2,.15,.1,.15,0);
        if(l.getGameTime()%20==0)l.sendBlockUpdated(p,s,s,2);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r){return saveWithoutMetadata(r);}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider r){super.saveAdditional(t,r);t.putInt("Energy",energy.getEnergyStored());t.putLongArray("Links",links.stream().mapToLong(BlockPos::asLong).toArray());}
    @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider r){super.loadAdditional(t,r);energy.deserializeNBT(r,net.minecraft.nbt.IntTag.valueOf(Math.clamp(t.getInt("Energy"),0,2000000)));links.clear();for(long v:t.getLongArray("Links")){if(links.size()>=8)break;links.add(BlockPos.of(v));}}
}
