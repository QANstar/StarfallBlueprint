package cn.starfall.blueprint;

import java.util.List;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Reserves renewable condensation from a complete array and four permanent phase matrices. */
public final class PhaseExtractor {
    private static final Map<String, String> OUTPUTS = Map.of(
            "phase_matrix_meteor", "ore", "phase_matrix_rutile", "rutile", "phase_matrix_borate", "borate");

    private PhaseExtractor() {}

    /** Only reserves work: the common processing loop owns every 200 XH payment and all 400 ticks. */
    public static boolean start(IndustryBlockEntity be) {
        if (!be.idle() || !be.kind().equals("phase_extractor")) return false;
        if (!(be.getLevel() instanceof ServerLevel level)) return false;
        if (!AggregateMachines.ready(be)) return false;
        String base = foundation(be);
        if (base.isEmpty()) { be.status = 10; return false; }
        if (!be.tanks[0].getFluid().is(Fluids.WATER) || be.tanks[0].getFluidAmount() < 500) {
            be.status = 3; return false;
        }
        if (be.getPower() < 200) { be.status = 4; return false; }
        ItemStack output = CatalogueContent.stack(OUTPUTS.get(base), 1);
        if (base.equals("phase_matrix_meteor")) output.set(StarfallBlueprint.BATCH.get(), MaterialBatch.ore(12, 58));
        var data = CatalogueEquipment.getData(output);
        data.putString("Origin", level.dimension().location().toString());
        CatalogueEquipment.setData(output, data);
        if (output.isEmpty() || !be.outputFits(List.of(output))) { be.status = 5; return false; }

        be.tanks[0].drain(500, FluidAction.EXECUTE);
        be.activeRecipe = "condense:" + base;
        be.pending = NonNullList.withSize(4, ItemStack.EMPTY);
        be.pending.set(0, output);
        be.pendingFluid1 = FluidStack.EMPTY;
        be.pendingFluid2 = FluidStack.EMPTY;
        be.pendingExperience = 0;
        be.processTicks = 400;
        be.processCost = 80000;
        be.processPaid = 0;
        be.progressTicks = 0;
        be.status = 1;
        be.setChanged();
        return true;
    }

    private static String foundation(IndustryBlockEntity be) {
        for (var route : OUTPUTS.entrySet()) {
            var registered = CatalogueContent.PHASE_MATRICES.get(route.getKey());
            if (registered == null) continue;
            boolean same = true;
            for (int part = 0; part < 4; part++) {
                var pos = be.getBlockPos().offset(LargeMachineBlock.offset(be.facing(), part)).below();
                if (!be.getLevel().hasChunkAt(pos) || !be.getLevel().getBlockState(pos).is(registered.get())) {
                    same = false; break;
                }
            }
            if (same) return route.getKey();
        }
        return "";
    }
}
