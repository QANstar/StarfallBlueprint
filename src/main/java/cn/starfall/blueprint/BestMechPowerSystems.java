package cn.starfall.blueprint;

import java.util.UUID;
import java.util.function.IntConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Independent wireless slot and paid, resumable late-game fusion fuel ledger. Server only. */
public final class BestMechPowerSystems {
    public static final String WIRELESS = "best_mech_wireless_charger";
    public static final String FUSION = "best_mech_heavy_fusion_core";
    public static final String FUEL = "best_mech_heavy_fusion_fuel";
    public static final String WIRELESS_STATE = "WirelessCharger";
    public static final int FUEL_TICKS = 1200, OUTPUT = 16000, HEAT = 8, WEAR_PERIOD = 100;
    private boolean wireless;
    private int fuelTicks, workTicks;
    private final java.util.List<BlockPos> wirelessSources = new java.util.ArrayList<>();
    private long nextWirelessScan = Long.MIN_VALUE;

    /** Implement only on explicitly owned, authorized energy sources; never generic capabilities. */
    public interface WirelessSource {
        UUID wirelessOwner();
        int extractWirelessEnergy(int max, boolean simulate);
    }
    public boolean wirelessInstalled() { return wireless; }
    public boolean installWireless() { if (wireless) return false; wireless = true; return true; }
    public boolean removeWireless() { if (!wireless) return false; wireless = false; return true; }
    public int fusionFuelTicks() { return fuelTicks; }
    public int fusionWorkTicks() { return workTicks; }
    public boolean fusionCanRemove() { return fuelTicks == 0; }
    public void save(CompoundTag tag) {
        tag.putBoolean(WIRELESS_STATE, wireless);
        tag.putInt("FusionFuelTicks", fuelTicks); tag.putInt("FusionWorkTicks", workTicks);
    }
    public void load(CompoundTag tag) {
        wirelessSources.clear(); nextWirelessScan = Long.MIN_VALUE;
        wireless = tag.getBoolean(WIRELESS_STATE);
        fuelTicks = Math.clamp(tag.getInt("FusionFuelTicks"), 0, FUEL_TICKS);
        workTicks = Math.clamp(tag.getInt("FusionWorkTicks"), 0, WEAR_PERIOD - 1);
    }
    public static boolean compatible(String platform) { return BestMechProgression.UNIVERSAL.equals(platform); }

    public void tickFusion(BestMechEntity mech, IntConsumer addEnergy) {
        if (mech.level().isClientSide || !compatible(mech.platformType()) || !FUSION.equals(mech.coreKey())
                || !mech.ready(0) || mech.capacity() - mech.energy() < OUTPUT) return;
        if (fuelTicks == 0) {
            for (int i = 0; i < mech.cargoSlots(); i++) {
                ItemStack fuel = mech.cargo().getItem(i);
                if (!fuel.is(CatalogueContent.item(FUEL))) continue;
                fuel.shrink(1); mech.cargo().setChanged(); fuelTicks = FUEL_TICKS; break;
            }
        }
        if (fuelTicks == 0) return;
        addEnergy.accept(OUTPUT); fuelTicks--;
        int wear = ++workTicks >= WEAR_PERIOD ? 1 : 0;
        if (wear != 0) workTicks = 0;
        mech.spendSystems(0, HEAT, wear);
    }

    /** Existing receiving port enforces the shared 512 XH/tick external charging budget. */
    public void tickWireless(BestMechEntity mech, UUID owner) {
        if (!wireless || owner == null || mech.level().isClientSide || !mech.ready(0)) return;
        var target = mech.energyPort();
        int needed = target.receiveEnergy(512, true);
        if (needed <= 0) return;
        BlockPos center = mech.blockPosition();
        Vec3 start = mech.position().add(0, 1, 0);
        if (mech.level().getGameTime() >= nextWirelessScan) {
            wirelessSources.clear();
            nextWirelessScan = mech.level().getGameTime() + 20;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -8, -8), center.offset(8, 8, 8))) {
                if (mech.position().distanceToSqr(Vec3.atCenterOf(pos)) <= 64 && mech.level().hasChunkAt(pos)
                        && mech.level().getBlockEntity(pos) instanceof WirelessSource) wirelessSources.add(pos.immutable());
            }
        }
        for (BlockPos pos : wirelessSources) {
            if (mech.position().distanceToSqr(Vec3.atCenterOf(pos)) > 64 || !mech.level().hasChunkAt(pos)) continue;
            if (!(mech.level().getBlockEntity(pos) instanceof WirelessSource source) || !owner.equals(source.wirelessOwner())) continue;
            // Aim just above the platform top so its own neighboring frame is not an obstruction.
            Vec3 end = Vec3.atCenterOf(pos).add(0, .51, 0);
            // Check every traversed chunk before ray tracing so a ray cannot load an intermediate chunk.
            boolean loaded = true;
            for (int step = 0; step <= 32; step++) {
                if (!mech.level().hasChunkAt(BlockPos.containing(start.lerp(end, step / 32.0)))) { loaded = false; break; }
            }
            if (!loaded) continue;
            var hit = mech.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mech));
            if (hit.getType() != HitResult.Type.MISS && !hit.getBlockPos().equals(pos)) continue;
            int offered = Math.clamp(source.extractWirelessEnergy(needed, true), 0, needed);
            if (offered == 0) continue;
            int extracted = Math.clamp(source.extractWirelessEnergy(offered, false), 0, offered);
            if (extracted > 0) target.receiveEnergy(extracted, false);
            needed = target.receiveEnergy(512, true);
            if (needed <= 0) return;
        }
    }
}
