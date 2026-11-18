package cn.starfall.blueprint;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("starfall_blueprint_esper")
@PrefixGameTestTemplate(false)
public final class EsperGameTests {
    @GameTest(template = "empty")
    public static void progression_and_menu_permissions(GameTestHelper h) {
        ServerPlayer p = player(h);
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        p.setPos(Vec3.atBottomCenterOf(pos.east()));
        h.getLevel().setBlockAndUpdate(pos, Esper.AWAKENER.get().defaultBlockState());
        var menu = new EsperMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
        p.containerMenu = menu;
        var d = Esper.data(p);
        h.assertTrue(!menu.clickMenuButton(p, Esper.Skill.DOMAIN.ordinal()), "Rank B cannot be a starter");
        int slash = Esper.Skill.BLINK.ordinal();
        h.assertTrue(menu.clickMenuButton(p, slash) && Esper.rank(d) == 1 && Esper.slot(d, 0) == slash,
                "A starter awakens the player and occupies the first slot");
        p.giveExperienceLevels(20);
        h.assertTrue(!menu.clickMenuButton(p, slash), "Missing mastery and rank prevent an upgrade");
        d.putInt("RankPractice_SPACE", 10);
        d.putInt("Mastery_blink", 5);
        h.assertTrue(menu.clickMenuButton(p, EsperMenu.PROMOTE) && menu.clickMenuButton(p, slash),
                "Rank and skill upgrades consume practice and XP");
        h.assertTrue(p.experienceLevel == 13 && Esper.mastery(d, Esper.Skill.BLINK) == 0
                && Esper.level(d, Esper.Skill.BLINK) == 2, "Exact progression costs");
        h.assertTrue(menu.clickMenuButton(p, EsperMenu.EQUIP + 4 * slash + 3)
                && Esper.slot(d, 0) == -1 && Esper.slot(d, 3) == slash, "Equipping moves the skill between the four slots");
        d.putInt("Rank_SPACE", 5);
        h.assertTrue(!menu.clickMenuButton(p, Esper.Skill.DOMAIN.ordinal()), "Rank cannot bypass prerequisites");
        var chip = new ItemStack(Esper.RETUNER.get(), 2);
        h.assertTrue(Esper.retune(p, pos, chip) && Esper.school(d) == Esper.School.ELECTRIC && chip.getCount() == 1,
                "Retuning unlocks only the other school's root and consumes a chip");
        h.assertTrue(Esper.rank(d) == 1 && Esper.slot(d, 0) == Esper.Skill.INDUCTION.ordinal()
                && !Esper.equip(p, slash, 1) && !Esper.train(p, Esper.Skill.STORAGE), "Inactive school remains unavailable");
        h.assertTrue(Esper.retune(p, pos, chip) && chip.isEmpty() && Esper.rank(d) == 5
                && Esper.slot(d, 3) == slash, "Switching back restores rank and loadout");
        h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        h.assertTrue(!menu.clickMenuButton(p, EsperMenu.CLEAR + 3), "Removed machine invalidates menu commands");
        h.succeed();
    }

    @GameTest(template = "empty", skyAccess = true)
    public static void compute_stacking_and_terminal_gate(GameTestHelper h) {
        ServerPlayer p = player(h);
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        p.setPos(Vec3.atBottomCenterOf(pos));
        h.assertTrue(Esper.train(p, Esper.Skill.BLINK), "Starter unlock");
        var d = Esper.data(p);
        int base = d.getInt("Max"), regen = d.getInt("Regen");
        h.getLevel().setBlockAndUpdate(pos.east(), Esper.COMPUTE.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.south(), Esper.COMPUTE.get().defaultBlockState());
        Esper.refresh(p, d);
        h.assertTrue(d.getInt("Max") == base + 16 && d.getInt("Regen") == regen + 2, "Local units stack");
        h.getLevel().setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState());
        var stack = new ItemStack(Esper.SATELLITE.get(), 2);
        BlockPos launch = pos.atY(300);
        h.getLevel().setBlockAndUpdate(launch, Esper.LAUNCHER.get().defaultBlockState());
        h.assertTrue(Esper.launch(p, launch, stack) && Esper.launch(p, launch, stack) && stack.isEmpty(),
                "Two launches consume and record two satellites");
        h.assertTrue(d.getInt("Max") == base + 8, "Orbit gives no passive capacity while the uplink is off");
        d.putBoolean("Uplink", true);
        Esper.refresh(p, d);
        h.assertTrue(d.getInt("Online") == 0, "Uplink requires a carried terminal");
        p.getInventory().setItem(0, new ItemStack(Esper.TERMINAL.get()));
        Esper.refresh(p, d);
        h.assertTrue(d.getInt("Max") == base + 44 && d.getInt("Regen") == regen + 5,
                "Both satellites stack with the one remaining local unit");
        p.setPos(p.position().add(40, 0, 0));
        Esper.refresh(p, d);
        h.assertTrue(d.getInt("Local") == 0 && d.getInt("Online") == 2, "Only the terminal ignores distance");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void storage_and_failed_cast(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
        h.assertTrue(Esper.train(p, Esper.Skill.INDUCTION), "Electric root");
        var d = Esper.data(p);
        d.putInt("Rank_ELECTRIC", 2);
        p.giveExperienceLevels(3);
        h.assertTrue(Esper.train(p, Esper.Skill.SPARK) && Esper.equip(p, Esper.Skill.SPARK.ordinal(), 0),
                "Directed shock unlocks after induction");
        int power = d.getInt("Power");
        int charge = d.getInt("Charge");
        h.assertTrue(!Esper.cast(p, 0) && d.getInt("Power") == power && d.getInt("Charge") == charge
                && Esper.mastery(d, Esper.Skill.SPARK) == 0, "An empty shock spends neither resource and earns no mastery");
        var storage = new EsperStorage(p, 1);
        storage.setItem(45, new ItemStack(Items.TNT, 2));
        h.assertTrue(storage.store(new ItemStack(Items.QUARTZ, 12)).isEmpty(), "Cargo receives captured items");
        var restored = new EsperStorage(p, 1);
        h.assertTrue(restored.firstAmmo() == 45 && restored.getItem(0).getCount() == 12,
                "Storage and ammo stay distinct after reloading from NBT");
        restored.removeItem(45, 1);
        h.assertTrue(new EsperStorage(p, 1).getItem(45).getCount() == 1, "Ammo consumption is persisted");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void domain_return_and_remote_compute(GameTestHelper h) {
        var overworld = h.getLevel();
        ServerPlayer p = player(h);
        BlockPos start = h.absolutePos(new BlockPos(1, 3, 1));
        overworld.setBlockAndUpdate(start.below(), Blocks.STONE.defaultBlockState());
        p.setPos(Vec3.atBottomCenterOf(start));
        overworld.addNewPlayer(p);
        try {
            h.assertTrue(overworld.getServer().getLevel(EsperDomain.KEY) != null, "Datapack domain dimension is loaded");
            h.assertTrue(EsperDomain.move(p, p) && p.level().dimension().equals(EsperDomain.KEY),
                    "Owner travels to a safe platform in the private domain");
            BlockPos unit = p.blockPosition().east(2);
            p.serverLevel().setBlockAndUpdate(unit, Esper.COMPUTE.get().defaultBlockState());
            var ledger = EsperDomain.Ledger.get(p.serverLevel());
            h.assertTrue(ledger.remote(p) == 1 && ledger.local(p) == 0, "Domain computers are counted once");
            h.assertTrue(EsperDomain.move(p, p) && p.serverLevel() == overworld && p.blockPosition().equals(start),
                    "Domain return restores the original location");
            h.assertTrue(ledger.remote(p) == 1, "Domain computer is reachable across dimensions");
            overworld.getServer().getLevel(EsperDomain.KEY).setBlockAndUpdate(unit, Blocks.AIR.defaultBlockState());
            h.assertTrue(ledger.remote(p) == 0, "Remote index reflects removal");
        } finally { p.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 85)
    public static void electric_wave_expands_and_hits_once(GameTestHelper h) {
        var world = h.getLevel();
        ServerPlayer caster = player(h);
        BlockPos platform = h.absolutePos(new BlockPos(1, 3, 1)).atY(120);
        for (BlockPos pos : BlockPos.betweenClosed(platform.offset(-6, -1, -6), platform.offset(6, -1, 6)))
            world.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        caster.setPos(Vec3.atBottomCenterOf(platform));
        world.addNewPlayer(caster);
        var victim = net.minecraft.world.entity.EntityType.COW.create(world);
        victim.setNoAi(true);
        victim.setNoGravity(true);
        victim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
        victim.setHealth(100);
        victim.setPos(Vec3.atBottomCenterOf(platform.east(4)));
        world.addFreshEntity(victim);
        h.assertTrue(Esper.train(caster, Esper.Skill.INDUCTION), "Electric activation");
        var d = Esper.data(caster);
        d.putInt("Rank_ELECTRIC", 4);
        for (Esper.Skill unlocked : java.util.List.of(Esper.Skill.SPARK, Esper.Skill.BOOST, Esper.Skill.ARC,
                Esper.Skill.INSULATE, Esper.Skill.PULSE)) d.putInt("Level_" + unlocked.key, 1);
        Esper.equip(caster, Esper.Skill.PULSE.ordinal(), 0);
        h.assertTrue(Esper.cast(caster, 0) && d.getInt("Charge") == 16 && Esper.mastery(d, Esper.Skill.PULSE) == 1,
                "Electric blast consumes charge and earns mastery");
        h.runAfterDelay(2, () -> h.assertTrue(victim.getHealth() == 100,
                "Distant victim is outside early rings; health " + victim.getHealth() + ", distance " + victim.distanceTo(caster)));
        h.runAfterDelay(26, () -> {
            h.assertTrue(Math.abs(100 - victim.getHealth() - 10.3F) < .1F,
                    "Exactly one hit from the expanding wave; health " + victim.getHealth());
            victim.discard();
            caster.discard();
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void thunder_barrage_hits_in_separate_waves(GameTestHelper h) {
        var world = h.getLevel();
        ServerPlayer caster = player(h);
        BlockPos platform = h.absolutePos(new BlockPos(1, 3, 1)).atY(150);
        for (BlockPos pos : BlockPos.betweenClosed(platform.offset(-2, -1, -2), platform.offset(2, -1, 12)))
            world.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        caster.setPos(Vec3.atBottomCenterOf(platform));
        caster.setYRot(0);
        caster.setXRot(3);
        world.addNewPlayer(caster);
        var victim = target(world, Vec3.atBottomCenterOf(platform.south(6)));
        h.assertTrue(Esper.train(caster, Esper.Skill.INDUCTION), "Electric activation");
        var d = Esper.data(caster);
        d.putInt("Rank_ELECTRIC", 5);
        d.putInt("Charge", 80);
        for (Esper.Skill unlocked : Esper.School.ELECTRIC.skills()) d.putInt("Level_" + unlocked.key, 1);
        Esper.equip(caster, Esper.Skill.STORM.ordinal(), 0);
        h.assertTrue(Esper.cast(caster, 0), "Aim at a mob to center the storm");
        h.runAfterDelay(8, () -> h.assertTrue(victim.getHealth() <= 95 && victim.getHealth() > 94,
                "First wave hits before the following waves; health " + victim.getHealth()));
        h.runAfterDelay(50, () -> {
            h.assertTrue(victim.getHealth() < 77, "Later waves deal repeated damage; health " + victim.getHealth());
            victim.discard();
            caster.discard();
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 85)
    public static void blink_behind_and_lock_a_target(GameTestHelper h) {
        var world = h.getLevel();
        ServerPlayer caster = player(h);
        BlockPos platform = h.absolutePos(new BlockPos(1, 3, 1)).atY(190);
        for (BlockPos pos : BlockPos.betweenClosed(platform.offset(-2, -1, -2), platform.offset(2, -1, 10)))
            world.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        caster.setPos(Vec3.atBottomCenterOf(platform));
        caster.setYRot(0);
        caster.setXRot(5);
        world.addNewPlayer(caster);
        var victim = target(world, Vec3.atBottomCenterOf(platform.south(5)));
        victim.setYRot(180);
        h.assertTrue(Esper.train(caster, Esper.Skill.BLINK) && Esper.cast(caster, 0), "Blink on an aimed creature");
        h.assertTrue(caster.getZ() > victim.getZ() + .8, "Blink lands beyond the back of the targeted creature; offset "
                + caster.position().subtract(victim.position()));
        var d = Esper.data(caster);
        d.putInt("Rank_SPACE", 4);
        for (Esper.Skill unlocked : java.util.List.of(Esper.Skill.SLASH, Esper.Skill.PULL, Esper.Skill.LOCK))
            d.putInt("Level_" + unlocked.key, 1);
        Esper.equip(caster, Esper.Skill.LOCK.ordinal(), 1);
        h.runAfterDelay(5, () -> {
            caster.setPos(Vec3.atBottomCenterOf(platform));
            caster.setYRot(0);
            caster.setXRot(5);
            h.assertTrue(Esper.cast(caster, 1), "Lock on an aimed creature");
            Vec3 anchor = victim.position();
            victim.setPos(anchor.add(.75, 0, 0));
            h.runAfterDelay(6, () -> {
                h.assertTrue(victim.position().distanceToSqr(anchor) < .02, "Locked creature stays at its anchor");
                victim.discard();
                caster.discard();
                h.succeed();
            });
        });
    }

    private static net.minecraft.world.entity.animal.Cow target(ServerLevel world, Vec3 position) {
        var victim = net.minecraft.world.entity.EntityType.COW.create(world);
        victim.setNoAi(true);
        victim.setNoGravity(true);
        victim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
        victim.setHealth(100);
        victim.setPos(position);
        world.addFreshEntity(victim);
        return victim;
    }

    private static ServerPlayer player(GameTestHelper h) {
        var server = h.getLevel().getServer();
        UUID id = UUID.randomUUID();
        var profile = new GameProfile(id, "Baiwan_" + id.toString().substring(0, 8));
        var player = new ServerPlayer(server, h.getLevel(), profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(server, connection, player, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
    private EsperGameTests() {}
}
