package cn.starfall.blueprint.test;

import cn.starfall.blueprint.BestMechEntity;
import cn.starfall.blueprint.BestMechs;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("starfall_blueprint_mech")
@PrefixGameTestTemplate(false)
public final class BestCockpitGameTests {
    @GameTest(template = "empty")
    public static void pilotInsideEachHullAndVulnerableAfterExit(GameTestHelper helper) {
        checkSeat(helper, BestMechs.TYPE.get(), 3.45);
        checkSeat(helper, BestMechs.COMBAT_TYPE.get(), 3.94);
        checkSeat(helper, BestMechs.UNIVERSAL_TYPE.get(), 4.375);
        helper.succeed();
    }

    private static void checkSeat(GameTestHelper helper, EntityType<BestMechEntity> type, double eyeHeight) {
        var mech = type.create(helper.getLevel());
        var pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        mech.moveTo(pilot.getX(), pilot.getY(), pilot.getZ());
        try {
            helper.assertTrue(pilot.startRiding(mech, true), "Pilot must mount the mech");
            mech.positionRider(pilot);
            helper.assertTrue(Math.abs(pilot.getEyeY() - mech.getY() - eyeHeight) < .001, "Pilot eye must align with the visor");
            var hull = mech.getBoundingBox();
            var body = pilot.getBoundingBox();
            helper.assertTrue(body.minX > hull.minX && body.maxX < hull.maxX && body.minZ > hull.minZ
                    && body.maxZ < hull.maxZ && body.minY > hull.minY && body.maxY < hull.maxY,
                    "Hull must enclose the entire pilot hitbox");
            float before = pilot.getHealth();
            pilot.stopRiding();
            pilot.hurt(pilot.damageSources().generic(), 3);
            helper.assertTrue(pilot.getHealth() < before, "Dismount must immediately restore pilot damage");
        } finally {
            mech.discard();
        }
    }

    @GameTest(template = "empty")
    public static void pairedHullAndPilotHitsApplyOnceInEitherOrder(GameTestHelper helper) {
        for (boolean pilotFirst : new boolean[]{true, false}) {
            var mech = BestMechs.TYPE.get().create(helper.getLevel());
            var pilot = helper.makeMockPlayer(GameType.SURVIVAL);
            var attacker = helper.makeMockPlayer(GameType.SURVIVAL);
            try {
                helper.assertTrue(pilot.startRiding(mech, true), "Pilot must occupy the cockpit");
                var hit = attacker.damageSources().playerAttack(attacker);
                float hullBefore = mech.getHealth();
                float pilotBefore = pilot.getHealth();
                if (pilotFirst) pilot.hurt(hit, 6);
                else mech.hurt(hit, 6);
                float once = mech.getHealth();
                helper.assertTrue(once < hullBefore, "Either target must route the first hit to the hull");
                // Force the damage path to demonstrate pairing independently of vanilla immunity frames.
                mech.invulnerableTime = 0;
                if (pilotFirst) mech.hurt(hit, 6);
                else pilot.hurt(hit, 6);
                helper.assertTrue(mech.getHealth() == once && pilot.getHealth() == pilotBefore,
                        "A single attack visiting both targets must damage only the mech, once");
            } finally {
                pilot.stopRiding();
                mech.discard();
            }
        }
        helper.succeed();
    }
}
