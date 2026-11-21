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

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void collapse_aimed_growth_binding_and_detonation(GameTestHelper h) {
        var world = h.getLevel();
        var p = spacePlayer(h, 214);
        Vec3 focus = p.getEyePosition().add(0,0,13.5);
        BlockPos loose = BlockPos.containing(focus.add(2, 0, 0));
        BlockPos protectedBlock = loose.above();
        world.setBlockAndUpdate(loose, Blocks.DIRT.defaultBlockState());
        world.setBlockAndUpdate(protectedBlock, Blocks.BEDROCK.defaultBlockState());
        var victim = target(world, focus.add(0,-.5,0));
        var rear = target(world, p.position().add(0,0,-3));
        Esper.equip(p, Esper.Skill.COLLAPSE.ordinal(), 0);
        h.assertTrue(Esper.cast(p,0), "Collapse starts at the aimed location");
        keepChannel(h,p,0,84,victim);
        h.runAfterDelay(25, () -> {
            h.assertTrue(victim.getHealth()<100 && rear.getHealth()==100, "Remote sphere ticks damage without hitting behind caster: victim="+victim.getHealth()+", rear="+rear.getHealth()+", active="+EsperChannel.active(p));
            victim.setPos(victim.position().add(.75,0,0));
            EsperEffects.tickBinding(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(victim));
        });
        h.runAfterDelay(28, () -> h.assertTrue(Math.abs(victim.getX()-focus.x)<.1, "Sphere binds its victims"));
        h.runAfterDelay(84, () -> {
            h.assertTrue(!EsperChannel.active(p) && victim.getHealth()<60, "Maximum sphere explodes strongly and ends its channel: "+victim.getHealth()+", active="+EsperChannel.active(p));
            h.assertTrue(rear.getHealth()==100, "Caster location is not the explosion center");
            h.assertTrue(world.getBlockState(loose).isAir() && world.getBlockState(protectedBlock).is(Blocks.BEDROCK),
                    "Detonation breaks ordinary blocks inside the aimed sphere and preserves bedrock");
            victim.discard(); rear.discard(); p.discard(); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void slash_hits_visible_remote_targets_and_holds(GameTestHelper h) {
        var p=spacePlayer(h,220); var world=h.getLevel();
        var left=target(world,p.position().add(-3,0,15));
        var right=target(world,p.position().add(3,0,15));
        var rear=target(world,p.position().add(0,0,-3));
        var hidden=target(world,p.position().add(0,0,17));
        for (int x=-1;x<=1;x++) for(int y=0;y<4;y++)
            world.setBlockAndUpdate(p.blockPosition().offset(x,y,12),Blocks.STONE.defaultBlockState());
        Esper.equip(p,Esper.Skill.SLASH.ordinal(),0);
        h.assertTrue(Esper.cast(p,0) && left.getHealth()<100 && right.getHealth()<100
                && rear.getHealth()==100 && hidden.getHealth()==100, "Remote slash respects view cone and walls");
        keepChannel(h,p,0,24,left,right);
        h.runAfterDelay(25,()->{
            h.assertTrue(left.getHealth()<90 && right.getHealth()<90, "Holding repeats group slashes: "+left.getHealth()+" / "+right.getHealth()+", active="+EsperChannel.active(p));
            h.assertTrue(EsperChannel.release(p,0,world.getGameTime()) && !EsperChannel.active(p), "Release ends slashes");
            for(var e:java.util.List.of(left,right,rear,hidden,p))e.discard(); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void volley_launches_only_real_items_and_recalls(GameTestHelper h) {
        var p=spacePlayer(h,230); var world=h.getLevel(); var data=Esper.data(p);
        Esper.equip(p,Esper.Skill.VOLLEY.ordinal(),0);
        var storage=new EsperStorage(p,1);
        storage.setItem(45,Items.DIAMOND_SWORD.getDefaultInstance());
        int power=data.getInt("Power");
        h.assertTrue(Esper.cast(p,0) && data.getInt("Power")==power,"Press reserves a channel without paying");
        h.assertTrue(EsperVolley.shotCount(1, 0) == 1 && EsperVolley.shotCount(1, 15) == 4,
                "A tap fires once and holding adds one shot per five ticks");
        h.runAfterDelay(2,()->{
            h.assertTrue(!EsperVolley.release(p,1,world.getGameTime()) && EsperVolley.release(p,0,world.getGameTime())
                    && !EsperVolley.release(p,0,world.getGameTime()),"Matching release fires once");
            var shots=world.getEntitiesOfClass(EsperProjectile.class,p.getBoundingBox().inflate(10));
            h.assertTrue(shots.size()==1 && shots.getFirst().getItem().is(Items.DIAMOND_SWORD),"No fake fallback projectiles");
            h.assertTrue(Math.abs(shots.getFirst().getX() - p.getX()) > .5, "The first shot spawns to the side");
            h.assertTrue(new EsperStorage(p,1).firstAmmo()<0,"Actual payload is in flight");
        });
        h.runAfterDelay(16,()->{
            for(var shot:world.getEntitiesOfClass(EsperProjectile.class,p.getBoundingBox().inflate(500)))
                for(int i=0;i<101&&!shot.isRemoved();i++)shot.tick();
            h.assertTrue(new EsperStorage(p,1).getItem(45).is(Items.DIAMOND_SWORD),"Payload returns to its ammo slot on timeout: "+new EsperStorage(p,1).getItem(45)+" shots="+world.getEntitiesOfClass(EsperProjectile.class,p.getBoundingBox().inflate(500)).stream().map(e->e.tickCount+"@"+e.position()+" removed="+e.isRemoved()+" owner="+e.getOwner()).toList());
            h.assertTrue(world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(60), e->e.getItem().is(Items.DIAMOND_SWORD)).isEmpty(),
                    "No dropped payload is emitted");
            p.discard();h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void passive_charge_cap_and_ionized_refunds(GameTestHelper h) {
        var p=player(h); activate(p,Esper.Skill.INDUCTION); var d=Esper.data(p);
        d.putInt("Level_capacitor",3); d.putInt("Level_recycling",2);d.putInt("Charge",35);
        p.tickCount=20; EsperElectric.tick(p,d,20);
        h.assertTrue(d.getInt("Charge")==36,"Passive accumulation cannot overshoot passive cap");
        EsperElectric.tick(p,d,40);h.assertTrue(d.getInt("Charge")==36,"Passive charge stops at cap");
        h.assertTrue(EsperElectric.cast(p,d,Esper.Skill.INDUCTION,1,40) && d.getInt("Charge")>36,"Active charge exceeds passive cap");
        var victim=target(h.getLevel(),p.position().add(0,0,3));
        victim.getPersistentData().putUUID("EsperIonOwner",p.getUUID());
        victim.getPersistentData().putLong("EsperIonUntil",h.getLevel().getGameTime()+100);
        d.putInt("Charge",10);EsperElectric.onHit(p,victim);EsperElectric.onHit(p,victim);
        h.assertTrue(d.getInt("Charge")==14,"Ionized refund is limited once per target per ten ticks");
        h.assertTrue(!Esper.equip(p,Esper.Skill.CAPACITOR.ordinal(),0),"Passives do not consume skill slots");
        victim.discard();p.discard();h.succeed();
    }

    @GameTest(template = "empty")
    public static void flight_drains_until_empty_and_barrier_only_pays_on_hit(GameTestHelper h) {
        var p=spacePlayer(h,240);var d=Esper.data(p);long now=h.getLevel().getGameTime();
        h.assertTrue(EsperEffects.cast(p,d,Esper.Skill.SHIFT,1)&&p.getAbilities().flying,"Flight starts immediately without a target");
        d.putInt("Power",3);
        for (int i = 0; i < 6; i++) EsperEffects.tickPlayer(p, d, now+i);
        EsperEffects.tickPlayer(p,d,now+6);
        h.assertTrue(!p.getAbilities().mayfly&&!p.getAbilities().flying&&!d.contains("Shift"),"Compute exhaustion revokes granted flight");
        d.putInt("Power",30);EsperEffects.cast(p,d,Esper.Skill.BARRIER,1);
        EsperEffects.tickPlayer(p,d,now+2000);
        h.assertTrue(d.contains("Barrier")&&d.getInt("Power")==30,"Idle barrier persists without drain");
        var attacker=target(h.getLevel(),p.position().add(0,0,4));
        var arrow=net.minecraft.world.entity.EntityType.ARROW.create(h.getLevel());
        arrow.setOwner(attacker);arrow.setPos(p.position().add(0,.96,2.5));arrow.setDeltaMovement(0,0,-1);
        h.getLevel().addFreshEntity(arrow);EsperEffects.tickPlayer(p,d,now+2001);
        h.assertTrue(arrow.isRemoved()&&d.getInt("Power")==25,"Approaching projectile is intercepted outside the player");
        var incoming=new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(p,
                new net.neoforged.neoforge.common.damagesource.DamageContainer(p.damageSources().mobAttack(attacker),4));
        EsperEffects.protect(p,incoming);
        h.assertTrue(incoming.isCanceled()&&d.getInt("Power")==17,"Actual melee event is blocked and charged once");
        h.assertTrue(!EsperEffects.barrierHit(p,d,40,p.position().add(0,0,4),true)&&d.getInt("Power")==17,"Insufficient compute cannot block for free");
        Esper.toggleMode(p);h.assertTrue(d.contains("Barrier"),"Leaving ability mode preserves the barrier");
        d.putInt("Power", 5);
        h.assertTrue(EsperEffects.barrierHit(p,d,5,p.position().add(0,0,4),false) && !d.contains("Barrier"),
                "The final paid interception shuts down an exhausted barrier");
        attacker.discard();p.discard();h.succeed();
    }

    @GameTest(template = "empty")
    public static void channel_timeout_and_early_release(GameTestHelper h) {
        var p=spacePlayer(h,250);var d=Esper.data(p);long now=h.getLevel().getGameTime();
        Esper.equip(p,Esper.Skill.COLLAPSE.ordinal(),0);
        h.assertTrue(Esper.cast(p,0),"Start channel");
        EsperChannel.tick(p,d,now+13);h.assertTrue(!EsperChannel.active(p),"Missing input heartbeat cancels channel");
        h.assertTrue(EsperChannel.begin(p,0,Esper.Skill.COLLAPSE,1,now+20),"Restart channel");
        EsperChannel.hold(p,0,now+30);EsperChannel.tick(p,d,now+30);
        h.assertTrue(EsperChannel.release(p,0,now+30)&&!EsperChannel.active(p),"Early release terminates and detonates once");
        p.discard();h.succeed();
    }

    @GameTest(template = "empty")
    public static void aimed_collapse_binds_immediately_and_cancels_cleanly(GameTestHelper h) {
        var p = spacePlayer(h, 245);
        var d = Esper.data(p);
        var world = h.getLevel();
        var selected = target(world, p.position().add(0, 0, 6));
        var distant = target(world, p.position().add(0, 0, 13));
        Vec3 anchor = selected.position();
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, selected.getBoundingBox().getCenter());
        Esper.equip(p, Esper.Skill.COLLAPSE.ordinal(), 0);
        h.assertTrue(Esper.cast(p, 0), "Aimed collapse starts");
        selected.setPos(anchor.add(1, 0, 0));
        EsperEffects.tickBinding(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(selected));
        h.assertTrue(selected.position().distanceToSqr(anchor) < .02, "The selected target is pinned before the first growth tick");
        long now = world.getGameTime() + 10;
        EsperChannel.hold(p, 0, now);
        EsperChannel.tick(p, d, now);
        h.assertTrue(selected.getHealth() < 100 && distant.getHealth() == 100, "The sphere grows at the nearest aimed target");
        EsperChannel.cancel(p);
        selected.setPos(anchor.add(1, 0, 0));
        EsperEffects.tickBinding(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(selected));
        h.assertTrue(selected.position().distanceToSqr(anchor) > .9, "Cancel releases the exact channel binding immediately");
        selected.discard();
        distant.discard();
        p.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shift_takes_off_and_ends_on_landing(GameTestHelper h) {
        var p = spacePlayer(h, 247);
        var d = Esper.data(p);
        long now = h.getLevel().getGameTime();
        Esper.equip(p, Esper.Skill.SHIFT.ordinal(), 0);
        p.setOnGround(true);
        h.assertTrue(Esper.cast(p, 0) && p.getAbilities().flying && p.getDeltaMovement().y > 0,
                "Casting from the ground immediately enables flight and lifts the player");
        Esper.toggleMode(p);
        p.getAbilities().flying = false;
        EsperEffects.tickPlayer(p, d, now + 7);
        h.assertTrue(d.contains("Shift") && p.getAbilities().flying, "Airborne shift survives mode changes and vanilla flight toggles");
        p.setOnGround(true);
        EsperEffects.tickPlayer(p, d, now + 8);
        h.assertTrue(!d.contains("Shift") && !p.getAbilities().mayfly && !p.getAbilities().flying,
                "Landing removes the temporary flight ability");
        p.discard();
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=70)
    public static void distortion_moves_blocks_without_duplication(GameTestHelper h) {
        var p=spacePlayer(h,260);var world=h.getLevel();
        BlockPos stone=BlockPos.containing(p.getEyePosition().add(0,0,5));
        BlockPos chest=stone.south(2),bedrock=stone.south(4);
        world.setBlockAndUpdate(stone,Blocks.DIAMOND_BLOCK.defaultBlockState());
        world.setBlockAndUpdate(chest,Blocks.CHEST.defaultBlockState());
        world.setBlockAndUpdate(bedrock,Blocks.BEDROCK.defaultBlockState());
        var container=(net.minecraft.world.level.block.entity.ChestBlockEntity)world.getBlockEntity(chest);
        container.setItem(0,Items.EMERALD.getDefaultInstance());
        var victim=target(world,p.position().add(0,0,10));
        h.assertTrue(EsperWarp.cast(p,1)&&victim.getHealth()<100,"Distortion damages along the line");
        h.runAfterDelay(60,()->{
            int diamonds=0;
            for(var pos:BlockPos.betweenClosed(stone.offset(-6,-6,-3),stone.offset(6,6,10)))
                if(world.getBlockState(pos).is(Blocks.DIAMOND_BLOCK))diamonds++;
            h.assertTrue(world.getBlockState(stone).isAir()&&diamonds==1,"Distortion moves the actual block exactly once");
            h.assertTrue(world.getBlockState(bedrock).is(Blocks.BEDROCK)&&world.getBlockEntity(chest)==container
                    &&container.getItem(0).is(Items.EMERALD),"Unbreakable terrain and real inventory stay intact");
            victim.discard();p.discard();h.succeed();
        });
    }

    private static void keepChannel(GameTestHelper h,ServerPlayer p,int slot,int ticks,net.minecraft.world.entity.LivingEntity... victims) {
        long start=h.getLevel().getGameTime();
        for(int i=1;i<=ticks;i++) {
            final int elapsed=i;
            h.runAfterDelay(i,()->{
                long now=start+elapsed;
                // Simulated players do not belong to the server connection tick list.
                // Advance channel time and the victims' hit immunity explicitly.
                if(elapsed%10==0)for(var victim:victims)victim.invulnerableTime=0;
                EsperChannel.hold(p,slot,now);EsperChannel.tick(p,Esper.data(p),now);
            });
        }
    }

    private static ServerPlayer spacePlayer(GameTestHelper h,int y) {
        var p=player(h);p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1,2,1)).atY(y)));
        p.setYRot(0);p.setXRot(0);p.setNoGravity(true);h.getLevel().addNewPlayer(p);
        activate(p,Esper.Skill.BLINK);var d=Esper.data(p);d.putInt("Rank_SPACE",7);
        for(var skill:Esper.School.SPACE.skills())d.putInt("Level_"+skill.key,1);
        Esper.refresh(p,d);d.putInt("Power",d.getInt("Max"));return p;
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
            h.assertTrue(victim.getHealth() < 85, "Later waves deal repeated damage; health " + victim.getHealth());
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
            caster.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,victim.getEyePosition());
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
            EsperEffects.tickBinding(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(victim));
            h.runAfterDelay(6, () -> {
                h.assertTrue(victim.position().distanceToSqr(anchor) < .02, "Locked creature stays at its anchor: "+victim.position().subtract(anchor)+", tick="+victim.tickCount);
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
        var chunk = new net.minecraft.world.level.ChunkPos(h.absolutePos(BlockPos.ZERO));
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)h.getLevel().setChunkForced(chunk.x+x,chunk.z+z,true);
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
