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
        h.assertTrue(!menu.clickMenuButton(p, slash), "Unpowered awakener cannot train");
        var machine = EsperFacility.controller(h.getLevel(), pos);
        machine.energy.load(250000);
        h.assertTrue(menu.clickMenuButton(p, slash) && Esper.rank(d) == 1 && Esper.slot(d, 0) == slash,
                "A starter awakens the player and occupies the first slot");
        p.giveExperienceLevels(20);
        h.assertTrue(!menu.clickMenuButton(p, slash), "Missing mastery and rank prevent an upgrade");
        d.putInt("RankPractice_SPACE", Esper.rankPractice(1));
        d.putInt("Mastery_blink", Esper.practiceNeeded(1));
        int compute = d.getInt("Power");
        h.assertTrue(menu.clickMenuButton(p, EsperMenu.PROMOTE) && menu.clickMenuButton(p, slash),
                "Rank and skill upgrades consume practice and XP");
        h.assertTrue(p.experienceLevel == 20 - Esper.rankPrice(1) - Esper.price(Esper.Skill.BLINK, 1)
                && machine.energy.getEnergyStored() == 250000 - Esper.trainingEnergy(Esper.Skill.BLINK, 0, true)
                        - Esper.promotionEnergy(1) - Esper.trainingEnergy(Esper.Skill.BLINK, 1, false)
                && d.getInt("Power") == compute && Esper.mastery(d, Esper.Skill.BLINK) == 0
                && Esper.level(d, Esper.Skill.BLINK) == 2, "Exact XP and FE costs; player compute untouched");
        h.assertTrue(menu.clickMenuButton(p, EsperMenu.EQUIP + 4 * slash + 3)
                && Esper.slot(d, 0) == -1 && Esper.slot(d, 3) == slash, "Equipping moves the skill between the four slots");
        d.putInt("Rank_SPACE", 5);
        h.assertTrue(!menu.clickMenuButton(p, Esper.Skill.DOMAIN.ordinal()), "Rank cannot bypass prerequisites");
        var chip = new ItemStack(Esper.RETUNER.get(), 2);
        h.assertTrue(Esper.retune(p, pos, chip) && Esper.school(d) == Esper.School.ELECTRIC && chip.getCount() == 1,
                "Retuning unlocks only the other school's root and consumes a chip");
        h.assertTrue(Esper.rank(d) == 1 && Esper.slot(d, 0) == Esper.Skill.INDUCTION.ordinal()
                && !Esper.equip(p, slash, 1) && !Esper.train(p, Esper.Skill.STORAGE, machine), "Inactive school remains unavailable");
        h.assertTrue(Esper.retune(p, pos, chip) && chip.isEmpty() && Esper.rank(d) == 5
                && Esper.slot(d, 3) == slash, "Switching back restores rank and loadout");
        h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        h.assertTrue(!menu.clickMenuButton(p, EsperMenu.CLEAR + 3), "Removed machine invalidates menu commands");
        h.succeed();
    }

    @GameTest(template = "empty", skyAccess = true)
    public static void compute_stacking_and_terminal_gate(GameTestHelper h) {
        ServerPlayer p = player(h);
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1)).atY(295);
        p.setPos(Vec3.atBottomCenterOf(pos));
        activate(p, Esper.Skill.BLINK);
        var d = Esper.data(p);
        int base = d.getInt("Max"), regen = d.getInt("Regen");
        h.getLevel().setBlockAndUpdate(pos.east(), Esper.COMPUTE.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.south(), Esper.COMPUTE.get().defaultBlockState());
        Esper.refresh(p, d);
        h.assertTrue(d.getInt("Max") == base + 16 && d.getInt("Regen") == regen + 2, "Local units stack");
        h.getLevel().removeBlock(pos.east(), false);
        var fleet = OrbitalFleet.get(h.getLevel()).fleet(p);
        fleet.compute = 2;
        fleet.computeEnabled = false;
        Esper.refresh(p, d);
        h.assertTrue(d.getInt("Max") == base + 8 && d.getInt("Online") == 0,
                "Orbit gives no passive capacity while the uplink is off");
        fleet.computeEnabled = true;
        Esper.refresh(p, d);
        h.assertTrue(d.getInt("Online") == 0, "Uplink requires a carried terminal");
        p.getInventory().setItem(0, Haijia.stack("hj_terminal", 1));
        Esper.refresh(p, d);
        h.assertTrue(d.getInt("Max") == base + 44 && d.getInt("Regen") == regen + 5,
                "Both satellites stack with the one remaining local unit");
        p.setPos(p.position().add(40, 0, 0));
        Esper.refresh(p, d);
        h.assertTrue(d.getInt("Local") == 0 && d.getInt("Online") == 2, "Only the terminal ignores distance");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void multiblock_power_port_and_teardown(GameTestHelper h) {
        var level = h.getLevel();
        var p = player(h);
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1)).atY(280);
        var machine = Esper.AWAKENER.get();
        var root = machine.defaultBlockState().setValue(EsperFacility.PART, 1);
        level.setBlockAndUpdate(pos, root);
        machine.setPlacedBy(level, pos, root, p, new ItemStack(machine));
        var outer = pos.offset(1, 1, 1);
        var energy = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                outer, net.minecraft.core.Direction.UP);
        h.assertTrue(energy != null && energy.receiveEnergy(8000, false) == 8000
                && EsperFacility.controller(level, pos).energy.getEnergyStored() == 8000, "Shell port feeds the root");
        level.destroyBlock(outer, true);
        h.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.east()).isAir(),
                "Removing the outer shell removes the entire structure");
        var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(3), e -> e.getItem().is(machine.asItem()));
        h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1, "Only one device drops");
        h.succeed();
    }

    @GameTest(template = "empty", skyAccess = true)
    public static void compute_launch_and_terminal_use_shared_fleet(GameTestHelper h) {
        var world = h.getLevel();
        var p = player(h);
        var root = h.absolutePos(new BlockPos(2, 3, 2)).atY(310);
        var pad = Haijia.BLOCKS.get("launchpad").get();
        var state = pad.defaultBlockState();
        var definition = OrbitalStructure.of("launchpad");
        for (int i = 0; i < definition.parts(); i++)
            world.setBlockAndUpdate(root.offset(definition.offset(state.getValue(OrbitalBlock.FACING), i)),
                    state.setValue(OrbitalBlock.PART, i));
        var machine = OrbitalBlock.controller(world, root);
        for (int i = 0; i < 25; i++) machine.energy.receiveEnergy(8192, false);
        p.setPos(Vec3.atBottomCenterOf(root));
        p.getPersistentData().put("HaijiaKnowledge", new net.minecraft.nbt.CompoundTag());
        p.getPersistentData().getCompound("HaijiaKnowledge").putBoolean("orbit", true);
        p.getInventory().add(new ItemStack(Esper.SATELLITE.get()));
        p.getInventory().add(Haijia.stack("hj_launch_vehicle", 1));
        var launch = new OrbitalMenu(1, p.getInventory(), ContainerLevelAccess.create(world, root), 2);
        var fleet = OrbitalFleet.get(world).fleet(p);
        h.assertTrue(launch.clickMenuButton(p, OrbitalMenu.LAUNCH_COMPUTE) && fleet.compute == 1
                && machine.energy.getEnergyStored() == 4800, "Haijia launch consumes FE and records a compute satellite");
        h.assertTrue(!launch.clickMenuButton(p, OrbitalMenu.LAUNCH_COMPUTE) && fleet.compute == 1,
                "A launch cannot replay without the rocket and payload");
        p.getInventory().add(Haijia.stack("hj_terminal", 1));
        var terminal = new OrbitalMenu(2, p.getInventory(), ContainerLevelAccess.NULL, 4);
        h.assertTrue(terminal.clickMenuButton(p, OrbitalMenu.TOGGLE_COMPUTE) && !fleet.computeEnabled,
                "Existing terminal controls the fleet compute link");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void collapse_expands_in_all_directions(GameTestHelper h) {
        var world = h.getLevel();
        var p = player(h);
        BlockPos origin = h.absolutePos(new BlockPos(1, 2, 1)).atY(214);
        p.setPos(Vec3.atBottomCenterOf(origin));
        p.setYRot(0);
        p.setXRot(-75);
        p.setNoGravity(true);
        world.addNewPlayer(p);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        var left = origin.offset(-2, 1, 6);
        var right = origin.offset(2, 1, 6);
        var behind = origin.offset(0, 1, -7);
        var near = origin.offset(0, 1, -2);
        var untouched = origin.offset(0, 1, -9);
        var protectedBlock = origin.offset(2, 1, 0);
        world.setBlockAndUpdate(left, Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(right, Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(behind, Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(near, Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(untouched, Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(protectedBlock, Blocks.CHEST.defaultBlockState());
        activate(p, Esper.Skill.BLINK);
        var data = Esper.data(p);
        data.putInt("Rank_SPACE", 5);
        for (var skill : Esper.School.SPACE.skills()) data.putInt("Level_" + skill.key, 3);
        Esper.equip(p, Esper.Skill.COLLAPSE.ordinal(), 0);
        h.assertTrue(Esper.cast(p, 0), "Collapse starts while the player is aiming at empty sky");
        h.runAfterDelay(10, () -> h.assertTrue(world.getBlockState(near).isAir() && !world.getBlockState(behind).isAir(),
                "The shock front reaches nearby terrain before the outer shell"));
        h.runAfterDelay(45, () -> {
            h.assertTrue(world.getBlockState(left).isAir() && world.getBlockState(right).isAir()
                    && world.getBlockState(behind).isAir(), "Wave breaks terrain around the caster, including behind");
            h.assertTrue(world.getBlockState(untouched).is(Blocks.STONE) && world.getBlockState(protectedBlock).is(Blocks.CHEST),
                    "Wave preserves terrain beyond the radius and blocks with inventories");
            p.discard();
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void slash_sweeps_both_sides_at_short_cooldown(GameTestHelper h) {
        var world = h.getLevel();
        var p = player(h);
        Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1)).atY(220));
        p.setPos(origin);
        p.setYRot(0);
        p.setXRot(-60);
        var left = target(world, origin.add(-3, 0, 3));
        var right = target(world, origin.add(3, 0, 3));
        var rear = target(world, origin.add(0, 0, -3));
        activate(p, Esper.Skill.BLINK);
        var data = Esper.data(p);
        data.putInt("Rank_SPACE", 2);
        data.putInt("Level_slash", 1);
        Esper.equip(p, Esper.Skill.SLASH.ordinal(), 0);
        h.assertTrue(Esper.cast(p, 0) && left.getHealth() < 100 && right.getHealth() < 100 && rear.getHealth() == 100,
                "The sweep hits both sides using yaw, preserves targets behind");
        h.assertTrue(data.getLong("Ready_slash") - world.getGameTime() == 10, "Base slash cooldown is half a second");
        left.discard();
        right.discard();
        rear.discard();
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 85)
    public static void volley_release_spawns_and_ticks_swords(GameTestHelper h) {
        var world = h.getLevel();
        var p = player(h);
        Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1)).atY(230));
        p.setPos(origin);
        p.setYRot(0);
        p.setXRot(0);
        p.setNoGravity(true);
        world.addNewPlayer(p);
        activate(p, Esper.Skill.BLINK);
        var data = Esper.data(p);
        data.putInt("Rank_SPACE", 5);
        data.putInt("Level_storage", 3);
        data.putInt("Level_volley", 3);
        Esper.equip(p, Esper.Skill.VOLLEY.ordinal(), 0);
        var box = new net.minecraft.world.phys.AABB(origin.add(-8, -3, -8), origin.add(8, 6, 12));
        new EsperStorage(p, 3).setItem(45, Items.DIAMOND_SWORD.getDefaultInstance());
        int power = data.getInt("Power");
        h.assertTrue(Esper.cast(p, 0) && world.getEntitiesOfClass(EsperProjectile.class, box).isEmpty()
                && data.getInt("Power") == power, "Pressing starts charging without paying or firing");
        h.runAfterDelay(12, () -> {
            long now = world.getServer().overworld().getGameTime();
            h.assertTrue(!EsperVolley.release(p, 1, now) && EsperVolley.release(p, 0, now)
                    && !EsperVolley.release(p, 0, now), "Only the matching release fires once");
            var swords = world.getEntitiesOfClass(EsperProjectile.class, box);
            h.assertTrue(swords.size() >= 7 && swords.stream().filter(s -> s.getItem().is(Items.DIAMOND_SWORD)).count() == 1,
                    "Charged volley creates many swords and consumes the real sword once");
            h.assertTrue(new EsperStorage(p, 3).firstAmmo() < 0 && Esper.mastery(data, Esper.Skill.VOLLEY) == 1
                    && data.getLong("Ready_volley") - now == Esper.Skill.VOLLEY.cooldownAt(3), "Release pays the cast once");
            h.runAfterDelay(3, () -> {
                h.assertTrue(swords.stream().anyMatch(sword -> sword.tickCount > 0), "Spawned swords tick without crashing");
                swords.forEach(net.minecraft.world.entity.Entity::discard);
                p.discard();
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void storage_and_failed_cast(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
        activate(p, Esper.Skill.INDUCTION);
        var d = Esper.data(p);
        d.putInt("Rank_ELECTRIC", 2);
        p.giveExperienceLevels(5);
        BlockPos machine = p.blockPosition().east();
        h.getLevel().setBlockAndUpdate(machine, Esper.AWAKENER.get().defaultBlockState());
        var controller = EsperFacility.controller(h.getLevel(), machine);
        controller.energy.load(60000);
        h.assertTrue(Esper.train(p, Esper.Skill.SPARK, controller) && Esper.equip(p, Esper.Skill.SPARK.ordinal(), 0),
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
        activate(caster, Esper.Skill.INDUCTION);
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
        activate(caster, Esper.Skill.INDUCTION);
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
        for (BlockPos pos : BlockPos.betweenClosed(platform.offset(-2, -1, -2), platform.offset(2, -1, 32)))
            world.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        caster.setPos(Vec3.atBottomCenterOf(platform));
        caster.setYRot(0);
        caster.setXRot(1);
        world.addNewPlayer(caster);
        var victim = target(world, Vec3.atBottomCenterOf(platform.south(26)));
        victim.setYRot(180);
        activate(caster, Esper.Skill.BLINK);
        var d = Esper.data(caster);
        d.remove("Mode");
        h.assertTrue(!Esper.cast(caster, 0), "Server rejects casts with ability mode off");
        d.putBoolean("Mode", true);
        h.runAfterDelay(12, () -> {
            caster.setPos(Vec3.atBottomCenterOf(platform));
            caster.setYRot(0);
            caster.setXRot(1);
            victim.setYRot(180);
            assertBlinkAndLock(h, caster, platform, victim, d);
        });
    }

    private static void assertBlinkAndLock(GameTestHelper h, ServerPlayer caster, BlockPos platform,
            net.minecraft.world.entity.animal.Cow victim, net.minecraft.nbt.CompoundTag d) {
        h.assertTrue(Esper.cast(caster, 0), "Aim at a creature far beyond the free blink range");
        h.assertTrue(caster.getZ() > victim.getZ() + .8, "Blink lands beyond the back of the targeted creature; offset "
                + caster.position().subtract(victim.position()));
        h.assertTrue(caster.getLookAngle().dot(victim.getEyePosition().subtract(caster.getEyePosition()).normalize()) > .99,
                "Caster faces the creature after blinking");
        d.putInt("Rank_SPACE", 4);
        for (Esper.Skill unlocked : java.util.List.of(Esper.Skill.SLASH, Esper.Skill.PULL, Esper.Skill.LOCK))
            d.putInt("Level_" + unlocked.key, 1);
        Esper.equip(caster, Esper.Skill.LOCK.ordinal(), 1);
        h.runAfterDelay(5, () -> {
            caster.setPos(Vec3.atBottomCenterOf(platform.south(21)));
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

    private static void activate(ServerPlayer player, Esper.Skill skill) {
        var data = Esper.data(player);
        Esper.activate(data, skill.school);
        Esper.refresh(player, data);
        data.putInt("Power", data.getInt("Max"));
        data.putBoolean("Mode", true);
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
