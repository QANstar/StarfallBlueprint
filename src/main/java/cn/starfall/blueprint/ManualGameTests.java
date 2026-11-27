package cn.starfall.blueprint;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("starfall_blueprint_manual")
@PrefixGameTestTemplate(false)
public final class ManualGameTests {
    private static FakePlayer player(GameTestHelper h) {
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ManualTest"));
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.experienceLevel = 0;
        return p;
    }
    @GameTest(template = "empty")
    public static void manualIsGrantedOnceAndFullInventoryCanRetry(GameTestHelper h) {
        var p = player(h);
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Blocks.DIRT, 64));
        PlayerManual.give(p);
        p.getInventory().clearContent();
        PlayerManual.give(p);
        PlayerManual.give(p);
        int count = 0;
        for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).is(PlayerManual.ITEM.get()))
            count += p.getInventory().getItem(i).getCount();
        h.assertTrue(count == 1, "A full inventory can retry delivery, and repeat logins never duplicate the manual");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void freshPlayerCanManufactureWithoutResearch(GameTestHelper h) {
        var p = player(h);
        h.assertTrue(BestEngineering.researchLevel(p) == 3 && BestEngineering.hasMechOperation(p)
                && Lingchuan.knowledge(p) == 65535 && Haijia.knows(p, "orbit")
                && BestMechAssembly.knows(p, "confluence"), "All manufacturing branches are open for fresh players");
        String[] keys = {"steel", "load", "board", "steel_fix"};
        int[] amounts = {4, 2, 1, 2};
        for (int i = 0; i < keys.length; i++) p.getInventory().setItem(i, CatalogueContent.stack(keys[i], amounts[i]));
        h.assertTrue(BestEngineering.perform(p, 2), "Real frame assembly accepts an unresearched player with materials");
        h.assertTrue(p.getInventory().contains(new ItemStack(CatalogueContent.item("best_frame"))), "Assembly grants the frame");
        h.assertTrue(!Progression.learn(p, "best:basic") && p.experienceLevel == 0,
                "Legacy research cannot charge XP or grant research rewards");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void personalSettingsAreIndependentAndSurviveClone(GameTestHelper h) {
        var p = player(h); var other = player(h); var replacement = player(h);
        h.assertTrue(PlayerManual.allows(p, PlayerManual.ESPER) && PlayerManual.allows(p, PlayerManual.BLACK_DOG),
                "Both settings retain enabled defaults");
        var menu = new ProgressionMenu(9, p.getInventory());
        p.containerMenu = menu;
        h.assertTrue(!menu.clickMenuButton(other, PlayerManual.ESPER), "Another player cannot change this menu owner's settings");
        h.assertTrue(!menu.clickMenuButton(p, 999), "Unknown setting packets are rejected");
        h.assertTrue(menu.clickMenuButton(p, PlayerManual.ESPER), "The owner can toggle esper terrain changes");
        h.assertTrue(!PlayerManual.allows(p, PlayerManual.ESPER) && PlayerManual.allows(p, PlayerManual.BLACK_DOG)
                && PlayerManual.allows(other, PlayerManual.ESPER), "The toggle affects only its player and ability family");
        PlayerManual.clonePlayer(new PlayerEvent.Clone(replacement, p, true));
        h.assertTrue(!PlayerManual.allows(replacement, PlayerManual.ESPER), "Death clone retains the setting");
        PlayerManual.toggle(replacement, PlayerManual.ESPER);
        h.assertTrue(!PlayerManual.allows(p, PlayerManual.ESPER), "Clone data is copied independently");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void disabledBlackDogCannonLeavesTerrainAndEnergyIntact(GameTestHelper h) {
        var p = player(h);
        BlockPos stone = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        var watch = new ItemStack(BlackDogArmor.ARMED_WATCH.get());
        BlackDogArmor.setCharge(watch, 10000);
        int charge = BlackDogArmor.charge(watch);
        PlayerManual.toggle(p, PlayerManual.BLACK_DOG);
        Vec3 from = Vec3.atCenterOf(stone).add(0, 0, -2);
        BlackDogWeapons.mine(p, watch, from, new Vec3(0, 0, 1));
        h.assertTrue(h.getLevel().getBlockState(stone).is(Blocks.STONE) && BlackDogArmor.charge(watch) == charge,
                "Disabled mining does not remove blocks or spend block excavation energy");
        h.succeed();
    }
}
