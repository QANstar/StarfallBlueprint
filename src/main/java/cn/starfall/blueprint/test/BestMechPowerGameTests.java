package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("starfall_blueprint_mech_power")
@PrefixGameTestTemplate(false)
public final class BestMechPowerGameTests {
    @GameTest(template = "empty")
    public static void independentSlotAndPersistentPaidLedger(GameTestHelper h) {
        var power = new BestMechPowerSystems();
        h.assertTrue(power.installWireless() && !power.installWireless(), "Exactly one independent wireless slot");
        var tag = new CompoundTag(); power.save(tag);
        tag.putInt("FusionFuelTicks", 731); tag.putInt("FusionWorkTicks", 99);
        power.load(tag);
        h.assertTrue(!power.fusionCanRemove(), "Paid fusion fuel prohibits removal");
        h.assertTrue(power.removeWireless() && !power.removeWireless(), "Wireless returns exactly once");
        var saved = new CompoundTag(); power.save(saved);
        var restored = new BestMechPowerSystems(); restored.load(saved);
        h.assertTrue(restored.fusionFuelTicks() == 731 && restored.fusionWorkTicks() == 99
                && !restored.wirelessInstalled(), "Removing independent slot retains paid fuel and wear progress");
        tag.putInt("FusionFuelTicks", Integer.MAX_VALUE); tag.putInt("FusionWorkTicks", Integer.MAX_VALUE);
        restored.load(tag);
        h.assertTrue(restored.fusionFuelTicks() == BestMechPowerSystems.FUEL_TICKS
                && restored.fusionWorkTicks() == BestMechPowerSystems.WEAR_PERIOD - 1, "Malformed fuel bounded");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void wirelessRequiresOwnerSightAndRealPlatformEnergy(GameTestHelper h) {
        var owner = java.util.UUID.randomUUID();
        var center = h.absolutePos(new net.minecraft.core.BlockPos(4, 1, 4));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            var pos = center.offset(x, 0, z);
            h.getLevel().setBlock(pos, MechMaintenancePlatforms.BLOCK.get().defaultBlockState(), 3);
            ((MechMaintenancePlatformBlockEntity) h.getLevel().getBlockEntity(pos)).configure(center, owner);
        }
        var source = (MechMaintenancePlatformBlockEntity) h.getLevel().getBlockEntity(center);
        source.energyPort().receiveEnergy(4096, false);
        var mech = BestMechs.UNIVERSAL_TYPE.get().create(h.getLevel());
        mech.moveTo(center.getX() + 4.5, center.getY() + 5, center.getZ() + .5, 0, 0);
        var power = new BestMechPowerSystems(); power.installWireless();
        power.tickWireless(mech, java.util.UUID.randomUUID());
        h.assertTrue(mech.energy() == 0 && source.storedEnergy() == 4096, "Stranger cannot drain private platform");
        for (int y = 0; y <= 6; y++) for (int z = -2; z <= 2; z++)
            h.getLevel().setBlock(center.offset(2, y, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        power.tickWireless(mech, owner);
        h.assertTrue(mech.energy() == 0 && source.storedEnergy() == 4096, "Solid wall blocks wireless");
        for (int y = 0; y <= 6; y++) for (int z = -2; z <= 2; z++)
            h.getLevel().setBlock(center.offset(2, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        power.tickWireless(mech, owner);
        h.assertTrue(mech.energy() == 512 && source.storedEnergy() == 3584, "Real platform debit equals mech credit");
        power.tickWireless(mech, owner);
        h.assertTrue(mech.energy() == 512 && source.storedEnergy() == 3584, "Repeated same-tick calls respect receiving budget");
        mech.discard(); h.succeed();
    }
    @GameTest(template = "empty")
    public static void fusionConsumesRealFuelAndPausesWithoutReset(GameTestHelper h) {
        var mech = BestMechs.UNIVERSAL_TYPE.get().create(h.getLevel());
        var power = new BestMechPowerSystems();
        var state = mech.stateTag(); state.putString(BestMechCores.STATE, BestMechCores.FUSION);
        state.putInt("Energy", 0); state.putInt("Heat", 0); state.putInt("Wear", 0);
        state.putBoolean("Isolated", false); state.putBoolean("ThermalLock", false); mech.loadState(state);
        int[] produced = {0};
        mech.cargo().setItem(0, new ItemStack(Items.COAL, 2));
        power.tickFusion(mech, n -> produced[0] += n);
        h.assertTrue(produced[0] == 0 && power.fusionFuelTicks() == 0, "Ordinary fuel cannot power heavy fusion");
        mech.cargo().setItem(1, CatalogueContent.stack(BestMechPowerSystems.FUEL, 2));
        power.tickFusion(mech, n -> produced[0] += n);
        h.assertTrue(produced[0] == BestMechPowerSystems.OUTPUT && power.fusionFuelTicks() == 1199
                && mech.cargo().getItem(1).getCount() == 1 && mech.heat() == BestMechPowerSystems.HEAT,
                "One real advanced fuel purchases duration; output adds heat");
        mech.setIsolated(true); power.tickFusion(mech, n -> produced[0] += n);
        h.assertTrue(power.fusionFuelTicks() == 1199 && power.fusionWorkTicks() == 1, "Isolation preserves paid duration and wear progress");
        state = mech.stateTag(); state.putBoolean("Isolated", false); state.putInt("Energy", mech.capacity()); mech.loadState(state);
        power.tickFusion(mech, n -> produced[0] += n);
        h.assertTrue(power.fusionFuelTicks() == 1199, "Full battery pauses paid fuel");
        state.putInt("Energy", 0); state.putInt("Wear", BestMechEntity.MAX_WEAR); mech.loadState(state);
        power.tickFusion(mech, n -> produced[0] += n);
        h.assertTrue(power.fusionFuelTicks() == 1199, "Wear shutdown preserves paid fuel");
        h.assertTrue(!BestMechPowerSystems.compatible(BestMechProgression.COMBAT)
                && !BestMechPowerSystems.compatible(BestMechProgression.ENGINEERING), "Only Guihai accepts fusion");
        mech.discard(); h.succeed();
    }
}
