package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("starfall_blueprint_mech_combat")
@PrefixGameTestTemplate(false)
public final class BestMechDroneGameTests {
    private static java.util.List<BestMechDrone> drones(GameTestHelper h, BestMechEntity mech) {
        return h.getLevel().getEntitiesOfClass(BestMechDrone.class, mech.getBoundingBox().inflate(8),
                drone -> drone.ownerId() == mech.getId() && !drone.isRemoved());
    }
    private static CompoundTag state(String combat, String module) {
        var root = new CompoundTag();
        var systems = new CompoundTag();
        systems.putString("Drone", combat);
        root.put(BestMechCombatSystems.STATE, systems);
        root.putString(BestMechModules.STATE, module);
        return root;
    }
    @GameTest(template = "combat_arena")
    public static void installationReconciliationAndReload(GameTestHelper h) {
        h.assertTrue(BuiltInRegistries.ENTITY_TYPE.get(StarfallBlueprint.id("best_mech_drone")) == BestMechDrones.TYPE.get(),
                "Drone type is registered on the mod bus");
        var mech = BestMechs.COMBAT_TYPE.get().create(h.getLevel());
        var pos = h.absolutePos(new BlockPos(4, 1, 4));
        mech.moveTo(pos.getX()+.5, pos.getY(), pos.getZ()+.5, 0, 0);
        mech.loadState(state(BestMechCombatSystems.ATTACK_DRONE, ""));
        h.getLevel().addFreshEntity(mech);
        BestMechDrones.reconcile(mech);
        var initial = drones(h, mech);
        h.assertTrue(initial.size() == 1 && initial.getFirst().kind() == BestMechDrones.Kind.ATTACK,
                "Installed attack component creates one real attachment");
        var drone = initial.getFirst();
        BestMechDrones.reconcile(mech);
        h.assertTrue(drones(h, mech).size() == 1 && drones(h, mech).getFirst() == drone,
                "Repeated reconciliation does not duplicate attachments");
        h.assertTrue(!drone.save(new CompoundTag()), "Attachments never serialize or drop as independent entities");
        mech.loadState(state(BestMechCombatSystems.DEFENSE_DRONE, ""));
        BestMechDrones.reconcile(mech);
        h.assertTrue(drone.isRemoved() && drones(h, mech).size() == 1 && drones(h, mech).getFirst().kind() == BestMechDrones.Kind.DEFENSE,
                "Replacing a component removes the old attachment");
        var saved = mech.stateTag();
        var old = drones(h, mech).getFirst();
        mech.discard();
        h.assertTrue(old.isRemoved(), "Leaving the level clears attachments");
        var restored = BestMechs.COMBAT_TYPE.get().create(h.getLevel());
        restored.moveTo(pos.getX()+.5, pos.getY(), pos.getZ()+.5, 0, 0);
        restored.loadState(saved); h.getLevel().addFreshEntity(restored);
        BestMechDrones.reconcile(restored);
        h.assertTrue(drones(h, restored).size() == 1 && drones(h, restored).getFirst().kind() == BestMechDrones.Kind.DEFENSE,
                "Saved component state recreates exactly one attachment");
        restored.loadState(state("", "")); BestMechDrones.reconcile(restored);
        h.assertTrue(drones(h, restored).isEmpty(), "Uninstall removes the attachment");
        restored.discard(); h.succeed();
    }
    @GameTest(template = "combat_arena")
    public static void serverTickCreatesBuiltinsAndSupport(GameTestHelper h) {
        var mech = BestMechs.UNIVERSAL_TYPE.get().create(h.getLevel());
        var pos = h.absolutePos(new BlockPos(4, 1, 4));
        mech.moveTo(pos.getX()+.5, pos.getY(), pos.getZ()+.5, 0, 0);
        mech.loadState(state("", BestMechSupport.HEALING_DRONE));
        h.getLevel().addFreshEntity(mech);
        h.runAfterDelay(3, () -> {
            var attached = drones(h, mech);
            h.assertTrue(attached.size() == 3 && attached.stream().anyMatch(d -> d.kind() == BestMechDrones.Kind.ATTACK)
                    && attached.stream().anyMatch(d -> d.kind() == BestMechDrones.Kind.DEFENSE)
                    && attached.stream().anyMatch(d -> d.kind() == BestMechDrones.Kind.HEALING),
                    "Automatic server ticks reconcile both built-ins and installed support");
            var health = mech.getHealth(); var energy = mech.energy();
            BestMechDrones.reconcile(mech);
            h.assertTrue(drones(h, mech).size() == 3 && mech.getHealth() == health && mech.energy() == energy,
                    "Visual reconciliation does not run combat or healing logic");
            mech.discard(); h.succeed();
        });
    }
    private BestMechDroneGameTests() {}
}
