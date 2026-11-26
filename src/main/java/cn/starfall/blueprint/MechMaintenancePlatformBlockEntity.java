package cn.starfall.blueprint;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class MechMaintenancePlatformBlockEntity extends BlockEntity implements BestMechPowerSystems.WirelessSource {
    public static final int CAPACITY=10_000_000, OUTPUT=100_000;
    private BlockPos center;
    private UUID owner;
    private int energy, steel, outputUsed;
    private long outputTick=Long.MIN_VALUE;
    public MechMaintenancePlatformBlockEntity(BlockPos p,BlockState s) { super(MechMaintenancePlatforms.ENTITY.get(),p,s); center=p; }
    public void configure(BlockPos p,UUID id) { center=p.immutable(); owner=id; setChanged(); }
    public MechMaintenancePlatformBlockEntity controller() {
        if(level==null || !level.hasChunkAt(center)) return null;
        return level.getBlockEntity(center) instanceof MechMaintenancePlatformBlockEntity be && be.center.equals(center)?be:null;
    }
    public boolean complete() {
        if(level==null || !worldPosition.equals(center) || owner==null) return false;
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
            BlockPos p=center.offset(x,0,z);
            if(!level.hasChunkAt(p) || !(level.getBlockEntity(p) instanceof MechMaintenancePlatformBlockEntity part)
                    || !part.center.equals(center) || !owner.equals(part.owner)) return false;
        }
        return true;
    }
    @Override public UUID wirelessOwner() { return owner; }
    @Override public int extractWirelessEnergy(int max,boolean simulate) {
        if(!complete()) return 0;
        if(outputTick!=level.getGameTime()) { outputTick=level.getGameTime(); outputUsed=0; }
        int n=Math.min(Math.max(0,max),Math.min(energy,OUTPUT-outputUsed));
        if(!simulate && n>0) { energy-=n; outputUsed+=n; setChanged(); }
        return n;
    }
    public IEnergyStorage energyPort() { return new IEnergyStorage() {
        public int receiveEnergy(int max,boolean simulate) {
            if(!worldPosition.equals(center)) return 0;
            int n=Math.min(Math.max(0,max),CAPACITY-energy);
            if(!simulate && n>0) { energy+=n; setChanged(); } return n;
        }
        public int extractEnergy(int max,boolean simulate) { return 0; }
        public int getEnergyStored() { return energy; }
        public int getMaxEnergyStored() { return CAPACITY; }
        public boolean canExtract() { return false; }
        public boolean canReceive() { return worldPosition.equals(center); }
    }; }
    public int storedSteel() { return steel; }
    public int storedEnergy() { return energy; }
    public boolean containsMech(BestMechEntity mech) {
        AABB b=mech.getBoundingBox();
        return mech.isAlive() && mech.onGround() && Math.abs(b.minY-(center.getY()+1))<0.06
                && mech.getDeltaMovement().horizontalDistanceSqr()<0.0025 && Math.abs(mech.getDeltaMovement().y)<0.1
                && b.minX>=center.getX()-1 && b.maxX<=center.getX()+2
                && b.minZ>=center.getZ()-1 && b.maxZ<=center.getZ()+2;
    }
    public static void tick(Level level,BlockPos p,BlockState s,MechMaintenancePlatformBlockEntity be) {
        if(!be.complete()) return;
        Player owner=level.getPlayerByUUID(be.owner);
        if(owner==null || !owner.isAlive()) return;
        var targets=level.getEntitiesOfClass(BestMechEntity.class,new AABB(p.getX()-1,p.getY()+1,p.getZ()-1,p.getX()+2,p.getY()+6,p.getZ()+2),be::containsMech);
        if(targets.size()!=1) return;
        BestMechEntity mech=targets.getFirst();
        if(!mech.maintenanceAuthorized(owner)) return;
        int repairReserve=be.steel>0 && (mech.wear()>0 || mech.getHealth()<mech.getMaxHealth())?1000:0;
        int offered=be.extractWirelessEnergy(Math.min(OUTPUT,Math.max(0,be.energy-repairReserve)),true);
        int accepted=mech.receiveMaintenanceEnergy(offered,true);
        if(accepted>0) { int spent=be.extractWirelessEnergy(accepted,false); mech.receiveMaintenanceEnergy(spent,false); }
        if(level.getGameTime()%20==0 && be.steel>0 && be.energy>=1000 && mech.repairFromMaintenancePlatform()) {
            be.steel--; be.energy-=1000; be.setChanged();
        }
    }
    public void interact(Player player,ItemStack stack) {
        if(owner==null || !owner.equals(player.getUUID())) { player.displayClientMessage(Component.translatable("message.starfall_blueprint.platform_denied"),true); return; }
        if(stack.is(CatalogueContent.item("steel")) || stack.is(StarfallBlueprint.structural("ordinary_steel"))) {
            int n=Math.min(stack.getCount(),256-steel); if(n>0) {steel+=n; stack.shrink(n);setChanged();}
        } else {
            IEnergyStorage source=stack.getCapability(Capabilities.EnergyStorage.ITEM);
            if(source!=null && source.canExtract()) {
                int n=energyPort().receiveEnergy(source.extractEnergy(100_000,true),true);
                if(n>0) energyPort().receiveEnergy(source.extractEnergy(n,false),false);
            }
        }
        player.displayClientMessage(Component.translatable("message.starfall_blueprint.platform_status",energy,steel,complete()),true);
    }
    public boolean isController() { return worldPosition.equals(center); }
    public void dropContents() {
        if(level!=null && !level.isClientSide && isController()) {
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) if(x!=0||z!=0) {
                BlockPos p=center.offset(x,0,z);
                if(level.hasChunkAt(p) && level.getBlockEntity(p) instanceof MechMaintenancePlatformBlockEntity part && part.center.equals(center)) level.removeBlock(p,false);
            }
        }
        if(level!=null && !level.isClientSide && steel>0) {
            while(steel>0) { int n=Math.min(64,steel); net.minecraft.world.Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+1,worldPosition.getZ()+.5,new ItemStack(CatalogueContent.item("steel"),n)); steel-=n; }
        }
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries); tag.putLong("Center",center.asLong()); if(owner!=null)tag.putUUID("Owner",owner); tag.putInt("Energy",energy); tag.putInt("Steel",steel);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries); center=tag.contains("Center")?BlockPos.of(tag.getLong("Center")):worldPosition; owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null; energy=Math.clamp(tag.getInt("Energy"),0,CAPACITY); steel=Math.clamp(tag.getInt("Steel"),0,256);
    }
}
