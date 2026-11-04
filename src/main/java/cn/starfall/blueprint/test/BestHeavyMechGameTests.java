package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

/** Three real-server checks for the independent heavy platform and unchanged light platform. */
@GameTestHolder("starfall_blueprint_heavy_mech")
@PrefixGameTestTemplate(false)
public final class BestHeavyMechGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var profile = new GameProfile(UUID.randomUUID(), "HeavyMechTest");
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent(); p.getInventory().selected = 0;
        p.getPersistentData().putInt("BestEngineeringResearch", 3);
        for(String key : List.of("BestDiagnostics", "BestLightMobility", "BestMechOperation", "BestAdvancedEngineering")) p.getPersistentData().putBoolean(key, true);
        return p;
    }
    private static void clear(ServerPlayer p) {
        p.closeContainer(); p.stopRiding(); p.stopUsingItem(); p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); p.setShiftKeyDown(false); p.setPlayerInput(0, 0, false, false);
    }
    private static BestMechEntity mech(GameTestHelper h) {
        for(int x = 0; x < 12; x++) for(int z = 0; z < 12; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        var m = BestMechs.HEAVY_TYPE.get().create(h.getLevel()); var pos = h.absolutePos(new BlockPos(5, 1, 3));
        m.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0); h.getLevel().addFreshEntity(m);
        for(int i = 0; i < 3; i++) m.tick();
        return m;
    }
    private static void service(ServerPlayer p, BestMechEntity m) {
        p.stopRiding(); p.moveTo(m.getX() + 3, m.getY(), m.getZ(), 0, 0); p.setShiftKeyDown(true);
        m.setIsolated(true); m.setOnGround(true); m.setDeltaMovement(Vec3.ZERO);
    }
    private static List<ItemStack> inventory(ServerPlayer p) { return p.getInventory().items.stream().map(ItemStack::copy).toList(); }
    private static boolean unchanged(ServerPlayer p, List<ItemStack> before) {
        for(int i = 0; i < 36; i++) if(!ItemStack.matches(before.get(i), p.getInventory().getItem(i))) return false;
        return true;
    }
    private static void set(BestMechEntity m, String key, int value) { var t = m.stateTag(); t.putInt(key, value); m.loadState(t); }
    private static InteractionResult deploy(GameTestHelper h, ServerPlayer p, BlockPos relativeFloor) {
        var pos = h.absolutePos(relativeFloor);
        return p.getMainHandItem().getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false)));
    }
    private static BestMechEntity deployed(GameTestHelper h) {
        var area = new AABB(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)), Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(12, 10, 12))));
        return h.getLevel().getEntitiesOfClass(BestMechEntity.class, area, e -> !e.isRemoved()).stream().findFirst().orElseThrow();
    }

    @GameTest(template = "empty")
    public static void upgradeHeavyIsAtomicAndFactoryAndLightBodiesKeepTheirLimits(GameTestHelper h) {
        var p = player(h); var m = mech(h); var light = BestMechs.TYPE.get().create(h.getLevel());
        try {
            var costs = Map.of("steel", 64, "load", 32, "conductor", 16, "board", 8, "steel_fix", 32, "head", 2, "sensor", 4, "sheath", 16);
            var source = CatalogueContent.stack("best_mech", 1); var sourceState = new CompoundTag(); sourceState.putBoolean("Isolated", true); BestMechItem.setState(source, sourceState); p.setItemInHand(InteractionHand.MAIN_HAND, source);
            int slot = 1;
            for(var cost : costs.entrySet()) {
                int remaining = cost.getValue() * 2;
                while(remaining > 0) { int count = Math.min(64, remaining); p.getInventory().setItem(slot++, CatalogueContent.stack(cost.getKey(), count)); remaining -= count; }
            }
            var inv = inventory(p); var knowledge = p.getPersistentData().copy();
            h.assertTrue(BestEngineering.preview(p, 26).executable() && unchanged(p, inv) && knowledge.equals(p.getPersistentData()), "Heavy manufacture preview changes neither materials nor knowledge");
            for(String prerequisite : List.of("BestMechOperation", "BestDiagnostics", "BestLightMobility", "BestAdvancedEngineering")) {
                p.getPersistentData().putBoolean(prerequisite, false);
                h.assertTrue(!BestEngineering.perform(p, 26) && unchanged(p, inv), "Existing personal knowledge required: " + prerequisite);
                p.getPersistentData().putBoolean(prerequisite, true);
            }
            p.getPersistentData().putInt("BestEngineeringResearch", 2);
            h.assertTrue(!BestEngineering.perform(p, 26) && unchanged(p, inv), "Research three required"); p.getPersistentData().putInt("BestEngineeringResearch", 3);
            // Remove every steel stack: the 64-steel recipe must count multiple legal stacks.
            var steel = new HashMap<Integer, ItemStack>();
            for(int i = 1; i < 36; i++) if(p.getInventory().getItem(i).is(CatalogueContent.item("steel"))) { steel.put(i, p.getInventory().getItem(i).copy()); p.getInventory().setItem(i, ItemStack.EMPTY); }
            var missing = inventory(p); h.assertTrue(!BestEngineering.perform(p, 26) && unchanged(p, missing), "Missing steel spends nothing");
            steel.forEach((i, stack) -> p.getInventory().setItem(i, stack));
            for(int i = 0; i < 36; i++) if(p.getInventory().getItem(i).isEmpty()) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            inv = inventory(p); h.assertTrue(BestEngineering.preview(p, 26).executable() && unchanged(p, inv),
                    "Full inventory preview accounts for a steel slot freed by consuming64 across legal stacks without mutating inventory");
            h.assertTrue(BestEngineering.perform(p, 26), "Heavy manufacture atomically uses a material slot freed by consumption");
            var packed = p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item("best_heavy_mech"))).findFirst().orElseThrow();
            h.assertTrue(packed.is(CatalogueContent.item("best_heavy_mech")) && packed.getCount() == 1 && packed.getMaxStackSize() == 1
                    && packed.getItem() instanceof BestMechItem item && item.heavy() && BestMechItem.energy(packed).getMaxEnergyStored() == 60_000_000,
                    "Manufacture produces the independent packed heavy platform");
            for(var cost : costs.entrySet()) h.assertTrue(p.getInventory().items.stream().filter(s -> s.is(CatalogueContent.item(cost.getKey())))
                    .mapToInt(ItemStack::getCount).sum() == cost.getValue(), "Exact multi-stack material cost: " + cost.getKey());
            h.assertTrue(knowledge.equals(p.getPersistentData()), "Upgrade introduces no knowledge flag");
            h.assertTrue(m.heavy() && m.getType() == BestMechs.HEAVY_TYPE.get() && m.getHealth() == 300 && m.getMaxHealth() == 300 && m.energy() == 0
                    && m.capacity() == 60_000_000 && m.cargoSlots() == 27 && m.cargo().getContainerSize() == 27 && m.cargo().isEmpty()
                    && m.moduleKey().isEmpty() && m.bodyKey().isEmpty() && m.coreKey().isEmpty() && m.legsKey().isEmpty()
                    && Math.abs(m.getBbWidth() - 2.6) < 1e-6 && Math.abs(m.getBbHeight() - 4.4) < 1e-6, "Fresh heavy factory initializes 300 HP, empty independent slots and native dimensions");
            h.assertTrue(!light.heavy() && light.getHealth() == 100 && light.capacity() == 20_000_000 && light.cargoSlots() == 9
                    && light.cargo().getContainerSize() == 27 && light.movementCost() == 400 && Math.abs(light.movementSpeed() - .22) < 1e-6,
                    "Light platform keeps original operational limits with the shared fixed backing container");
            var t = light.stateTag(); t.putString(BestMechBodies.STATE, BestMechBodies.REINFORCED); t.putFloat("Health", 42.25f); light.loadState(t);
            h.assertTrue(light.getMaxHealth() == 160 && light.getHealth() == 42.25f && light.capacity() == 30_000_000 && light.cargoSlots() == 18
                    && light.stateTag().getList("Cargo", 10).isEmpty(), "Reinforced light remains unchanged without healing or serialized empty extra cells");
            clear(p); service(p, m); m.setHealth(142.25f); p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack(BestMechBodies.REINFORCED, 1));
            var before = m.stateTag(); inv = inventory(p);
            h.assertTrue(!m.installBody(p, InteractionHand.MAIN_HAND) && m.interact(p, InteractionHand.MAIN_HAND).consumesAction()
                    && unchanged(p, inv) && before.equals(m.stateTag()), "Heavy explicitly rejects light body installation without consuming kit or healing");
            p.setItemInHand(InteractionHand.MAIN_HAND, CatalogueContent.stack("best_diagnostic_tool", 1)); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_BLOCK));
            m.setIsolated(false); before = m.stateTag(); inv = inventory(p);
            h.assertTrue(!m.removeBody(p) && m.interact(p, InteractionHand.MAIN_HAND).consumesAction() && before.equals(m.stateTag()) && unchanged(p, inv),
                    "Heavy body removal fails explicitly without isolation fallback or free kit");
            for(String body : List.of(BestMechBodies.REINFORCED, "unknown")) { t = m.stateTag(); t.putString(BestMechBodies.STATE, body); m.loadState(t);
                h.assertTrue(m.bodyKey().isEmpty() && m.getMaxHealth() == 300 && m.getHealth() == 142.25f && m.capacity() == 60_000_000, "Heavy body payload normalizes empty without upgrading or healing"); }
        } finally { clear(p); m.discard(); light.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualCargoSavePackDeployDeathPreservesHeavyIdentityAndEveryCounter(GameTestHelper h) {
        var p = player(h); var m = mech(h); boolean drops = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(true, h.getLevel().getServer());
        try {
            service(p, m); BestMechCargoMenu.open(p, m);
            h.assertTrue(p.containerMenu instanceof BestMechCargoMenu menu && menu.cargoSlots() == 27 && menu.slots.size() == 63 && menu.stillValid(p), "Actual server menu opens all 27 heavy cargo cells");
            var menu = p.containerMenu;
            for(int i = 0; i < 26; i++) m.cargo().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            p.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 7)); menu.quickMoveStack(p, 27);
            h.assertTrue(m.cargo().getItem(26).is(Items.DIAMOND) && m.cargo().getItem(26).getCount() == 7 && p.getInventory().getItem(9).isEmpty(), "Real shift transfer reaches cargo index26");
            for(String key : List.of("best_mech", "best_heavy_mech")) {
                p.getInventory().setItem(9, CatalogueContent.stack(key, 1)); var inv = inventory(p); var before = m.stateTag(); menu.quickMoveStack(p, 27);
                h.assertTrue(unchanged(p, inv) && before.equals(m.stateTag()) && !menu.getSlot(26).mayPlace(p.getInventory().getItem(9))
                        && !m.cargo().canPlaceItem(26, p.getInventory().getItem(9)), "Neither packed platform nests through menu or container predicate");
            }
            menu.quickMoveStack(p, 26); h.assertTrue(m.cargo().getItem(26).isEmpty() && p.getInventory().items.stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() == 7, "Real index26 extraction returns cargo once");
            clear(p); service(p, m); m.cargo().clearContent(); m.cargo().setItem(26, new ItemStack(Items.COAL, 7));
            var t = m.stateTag(); t.putString(BestMechCores.STATE, BestMechCores.GENERATOR); t.putBoolean("Isolated", false); m.loadState(t); m.tick();
            int fuel = new ItemStack(Items.COAL).getBurnTime(RecipeType.SMELTING);
            h.assertTrue(m.energy() == 2000 && m.coreFuelTicks() == fuel - 1 && m.cargo().getItem(26).getCount() == 6, "Generator buys one paid fuel interval from active heavy index26");
            m.tick(); h.assertTrue(m.energy() == 4000 && m.coreFuelTicks() == fuel - 2 && m.cargo().getItem(26).getCount() == 6, "Paid fuel advances once without buying another item");
            set(m, "CoreWorkTicks", 999); m.tick();
            h.assertTrue(m.energy() == 6000 && m.coreFuelTicks() == fuel - 3 && m.wear() == 1 && m.stateTag().getInt("CoreWorkTicks") == 0
                    && m.stateTag().getInt("WorkTicks") == 0, "Heavy generator retains independent1000-tick wear accounting");
            service(p, m); t = m.stateTag(); t.putString(BestMechArms.STATE, BestMechArms.EXCAVATOR); t.putString(BestMechModules.STATE, BestMechModules.RADIATOR);
            t.putString(BestMechLegs.STATE, BestMechLegs.CRUISE); t.putInt("Energy", 45_123_456); t.putInt("Heat", 678); t.putInt("Wear", 321);
            t.putInt("WorkTicks", 87); t.putInt("CoreWorkTicks", 731); t.putInt("DrillCooldown", 13); t.putFloat("Health", 242.25f);
            t.putBoolean("ThermalLock", true); t.putLong("ChargeTick", h.getLevel().getGameTime()); t.putInt("ChargeUsed", 123); m.loadState(t);
            h.assertTrue(m.workCost() == BestMechArms.workCost(BestMechArms.EXCAVATOR) && m.workHeat() == BestMechArms.workHeat(BestMechArms.EXCAVATOR)
                    && m.workCooldown() == BestMechArms.cooldown(BestMechArms.EXCAVATOR), "Heavy platform retains installed arm economics");
            m.setCustomName(Component.literal("Persistent heavy")); m.cargo().setItem(0, new ItemStack(Items.DIAMOND, 7)); var before = m.stateTag();
            var saved = new CompoundTag(); m.save(saved); var restored = EntityType.loadEntityRecursive(saved, h.getLevel(), entity -> entity);
            h.assertTrue(restored instanceof BestMechEntity loaded && loaded.heavy() && loaded.stateTag().equals(before)
                    && loaded.getHealth() == 242.25f && loaded.getMaxHealth() == 300, "Vanilla entity id restores actual heavy type and every cargo, fuel and operating counter");
            if(restored != null) restored.discard();
            BestMechCargoMenu.open(p, m); var oldMenu = p.containerMenu; var oldCargo = m.cargo();
            h.assertTrue(m.pack(p) && oldCargo.isEmpty() && !oldMenu.stillValid(p) && p.getMainHandItem().is(CatalogueContent.item("best_heavy_mech"))
                    && BestMechItem.state(p.getMainHandItem()).equals(before), "Actual packing emits heavy item with exact state and invalidates stale menu");
            p.closeContainer(); p.setShiftKeyDown(false); var packed = p.getMainHandItem().copy();
            h.setBlock(new BlockPos(5, 0, 5), Blocks.AIR);
            h.assertTrue(deploy(h, p, new BlockPos(6, 0, 6)) == InteractionResult.FAIL && ItemStack.matches(packed, p.getMainHandItem()), "Missing corner support under heavy footprint refuses deployment without item loss");
            h.setBlock(new BlockPos(5, 0, 5), Blocks.STONE); h.setBlock(new BlockPos(5, 1, 6), Blocks.STONE);
            h.assertTrue(deploy(h, p, new BlockPos(6, 0, 6)) == InteractionResult.FAIL && ItemStack.matches(packed, p.getMainHandItem()), "Narrow heavy placement collision refuses without item loss");
            h.setBlock(new BlockPos(5, 1, 6), Blocks.AIR);
            h.assertTrue(deploy(h, p, new BlockPos(6, 0, 6)).consumesAction() && p.getMainHandItem().isEmpty(), "Actual heavy deployment consumes one item after full-footprint checks");
            m = deployed(h); h.assertTrue(m.heavy() && m.stateTag().equals(before) && m.cargo().getItem(26).getCount() == 6, "Deployment preserves exact heavy state and last cargo cell");
            var deathBox = m.getBoundingBox().inflate(3); m.setHealth(0); m.die(h.getLevel().damageSources().generic()); m.die(h.getLevel().damageSources().generic());
            var loot = h.getLevel().getEntitiesOfClass(ItemEntity.class, deathBox); var chassis = loot.stream().filter(e -> e.getItem().is(CatalogueContent.item("best_heavy_mech"))).toList();
            h.assertTrue(chassis.size() == 1 && loot.stream().noneMatch(e -> e.getItem().is(CatalogueContent.item("best_mech")))
                    && loot.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum() == 7
                    && loot.stream().filter(e -> e.getItem().is(Items.COAL)).mapToInt(e -> e.getItem().getCount()).sum() == 6, "Death emits one heavy chassis and each cargo item exactly once");
            before.putFloat("Health", 0); before.put("Cargo", new net.minecraft.nbt.ListTag());
            var dead = chassis.getFirst().getItem(); h.assertTrue(BestMechItem.state(dead).equals(before) && !BestMechItem.energy(dead).canReceive(), "Dead packed heavy retains every counter and paid fuel while refusing charging");
            p.setItemInHand(InteractionHand.MAIN_HAND, dead.copy()); h.assertTrue(deploy(h, p, new BlockPos(3, 0, 8)) == InteractionResult.FAIL && p.getMainHandItem().getCount() == 1, "Dead heavy cannot redeploy");
            loot.forEach(Entity::discard); m.discard();
            var forged = CatalogueContent.stack("best_mech", 1); var forgedTag = before.copy(); forgedTag.putFloat("Health", 300); forgedTag.putBoolean("Isolated", true);
            forgedTag.putString("Platform", "heavy"); forgedTag.putString("id", "starfall_blueprint:best_heavy_engineering_mech"); BestMechItem.setState(forged, forgedTag);
            p.setItemInHand(InteractionHand.MAIN_HAND, forged);
            h.assertTrue(deploy(h, p, new BlockPos(3, 0, 8)).consumesAction(), "Light item with forged heavy payload still deploys"); m = deployed(h);
            h.assertTrue(!m.heavy() && m.getType() == BestMechs.TYPE.get() && m.getMaxHealth() == 100 && m.capacity() == 20_000_000 && m.cargoSlots() == 9,
                    "Packed item identity prevents state payload from creating heavy platform");
        } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(drops, h.getLevel().getServer()); clear(p); if(!m.isRemoved()) m.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realRidingHeavyEconomyCollisionSeatExitAndChargingBoundaries(GameTestHelper h) {
        var p = player(h); var m = mech(h);
        try {
            var start = m.position(); double baseline = 0;
            for(String module : List.of("", BestMechModules.DRIVE)) for(String legs : List.of("", BestMechLegs.CRUISE)) {
                p.stopRiding(); var t = m.stateTag(); t.putString(BestMechModules.STATE, module); t.putString(BestMechLegs.STATE, legs);
                t.putInt("Energy", 100000); t.putInt("Heat", 0); t.putInt("Wear", 0); t.putInt("WorkTicks", 99); t.putBoolean("Isolated", false); t.putFloat("Health", 242.25f); m.loadState(t);
                m.moveTo(start.x, start.y, start.z, 0, 0); m.setDeltaMovement(Vec3.ZERO); m.setOnGround(true); p.setShiftKeyDown(false);
                h.assertTrue(p.startRiding(m), "Actual operator mounts heavy configuration"); m.positionRider(p); double seat = p.getY() - m.getY();
                p.setPlayerInput(0, 1, false, false); m.tick(); m.positionRider(p);
                int cost = module.isEmpty() ? 1600 : 2400, heat = module.isEmpty() ? 10 : 14; double speed = module.isEmpty() ? .26 : .34;
                if(!legs.isEmpty()) { cost = cost * 3 / 4; heat--; speed *= 1.25; }
                double distance = m.getZ() - start.z; if(baseline == 0) baseline = distance;
                h.assertTrue(distance > 0 && Math.abs(distance / baseline - speed / .26) < .001 && Math.abs(m.movementSpeed() - speed) < 1e-6
                        && m.energy() == 100000 - cost && m.heat() == heat && m.wear() == 1 && m.stateTag().getInt("WorkTicks") == 0
                        && m.getHealth() == 242.25f && m.coolingRate() == BestMechModules.coolingRate(module) + 2, "Actual heavy travel applies exact speed, energy, heat, wear and advanced cooling");
                var anchor = m.getType().getDimensions().attachments().get(EntityAttachment.PASSENGER, 0, 0);
                h.assertTrue(Math.abs(anchor.y - 3.1) < 1e-5 && Math.abs(p.getY() - m.getY() - seat) < 1e-5,
                        "Native heavy passenger attachment is3.1 and actual riding preserves the seat offset");
                set(m, "Energy", cost - 1); var stopped = m.position(); m.setOnGround(true); m.tick();
                h.assertTrue(m.energy() == cost - 1 && Math.abs(m.getZ() - stopped.z) < 1e-6, "Below actual heavy movement cost refuses command");
                set(m, "Energy", cost); m.setOnGround(true); m.tick();
                h.assertTrue(m.energy() == 0 && m.getZ() > stopped.z && m.getDeltaMovement().horizontalDistanceSqr() == 0, "Last affordable heavy tick moves once then stops");
                var exit = m.getDismountLocationForPassenger(p); p.stopRiding();
                h.assertTrue(!p.isPassenger() && h.getLevel().noCollision(p, p.getBoundingBox())
                        && h.getLevel().noCollision(p, p.getType().getDimensions().makeBoundingBox(exit))
                        && h.getLevel().getFluidState(BlockPos.containing(exit)).isEmpty(), "Actual unpowered dismount stays collision checked and dry");
            }
            // Test the real vanilla move path through tall two-wide and three-wide wall openings.
            for(int opening : List.of(2, 3)) {
                for(int x = 0; x < 12; x++) for(int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
                for(int x = 4; x < 4 + opening; x++) for(int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, 6), Blocks.AIR);
                var pos = h.absolutePos(new BlockPos(4, 1, 3)); double center = pos.getX() + opening / 2.0;
                m.moveTo(center, pos.getY(), pos.getZ() + .5, 0, 0); m.setDeltaMovement(Vec3.ZERO); m.setOnGround(true);
                m.move(MoverType.SELF, new Vec3(0, -.01, 5)); var wall = h.absolutePos(new BlockPos(4, 1, 6));
                h.assertTrue(opening == 2 ? m.getZ() + m.getBbWidth() / 2.0 <= wall.getZ() + 1e-5 : m.getZ() > wall.getZ() + 1,
                        "Actual2.6-wide chassis is blocked by two-wide opening and passes three-wide opening");
            }
            for(int x = 0; x < 12; x++) for(int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, 6), Blocks.AIR);
            for(int x = 2; x <= 8; x++) h.setBlock(new BlockPos(x, 1, 6), Blocks.STONE);
            for(String legs : List.of("", BestMechLegs.CRUISE)) {
                var t = m.stateTag(); t.putString(BestMechLegs.STATE, legs); m.loadState(t);
                m.moveTo(start.x, start.y, start.z, 0, 0); m.setDeltaMovement(Vec3.ZERO); m.setOnGround(true); m.move(MoverType.SELF, new Vec3(0, -.01, 4));
                h.assertTrue(legs.isEmpty() ? m.getY() > start.y + .99 : Math.abs(m.getY() - start.y) < 1e-6,
                        "Heavy walking steps one block while cruise wheels retain0.6 step limit");
            }
            for(int x = 2; x <= 8; x++) h.setBlock(new BlockPos(x, 1, 6), Blocks.STONE_SLAB);
            m.moveTo(start.x, start.y, start.z, 0, 0); m.setDeltaMovement(Vec3.ZERO); m.setOnGround(true); m.move(MoverType.SELF, new Vec3(0, -.01, 4));
            h.assertTrue(Math.abs(m.getY() - start.y - .5) < 1e-6, "Heavy cruise wheels actually step half slab");
            var t = m.stateTag(); t.putString(BestMechModules.STATE, ""); t.putString(BestMechLegs.STATE, ""); t.putInt("Energy", 10000); t.putInt("Heat", 999);
            t.putBoolean("ThermalLock", false); t.putInt("Wear", 0); m.loadState(t); m.moveTo(start.x, start.y, start.z, 0, 0); m.setDeltaMovement(Vec3.ZERO); m.setOnGround(true);
            h.assertTrue(p.startRiding(m), "Operator mounts for heavy thermal boundary"); p.setPlayerInput(0, 1, true, false); m.tick();
            h.assertTrue(m.energy() == 8400 && m.heat() == 1000 && m.thermalLocked() && m.getDeltaMovement().horizontalDistanceSqr() == 0
                    && m.getY() <= start.y + 1e-5, "Heavy heat latches at1000 and riding jump input gives no flight"); p.stopRiding();
            set(m, "Heat", 405); m.tick(); h.assertTrue(m.thermalLocked() && m.heat() == 401, "Thermal latch holds above400");
            m.tick(); h.assertTrue(!m.thermalLocked() && m.heat() == 397, "Cooling recovers through400 boundary");
            t = m.stateTag(); t.putInt("Energy", 59_999_000); t.putInt("Wear", BestMechEntity.MAX_WEAR); t.putLong("ChargeTick", Long.MIN_VALUE); t.putInt("ChargeUsed", 0); m.loadState(t);
            var port = m.energyPort(); var before = m.stateTag();
            h.assertTrue(!m.ready(0) && port.getMaxEnergyStored() == 60_000_000 && port.receiveEnergy(1000, true) == 512 && before.equals(m.stateTag()), "Worn heavy simulation sees dynamic cap and leaves all counters unchanged");
            h.assertTrue(port.receiveEnergy(300, false) == 300 && m.energyPort().receiveEnergy(512, false) == 212 && port.receiveEnergy(1, false) == 0,
                    "All external entity handles share512 per tick budget even when worn");
            set(m, "Energy", 59_999_999); set(m, "ChargeUsed", 0);
            h.assertTrue(port.receiveEnergy(512, false) == 1 && m.energy() == 60_000_000 && port.receiveEnergy(1, false) == 0, "Entity receives exactly to60M cap");
            t = m.stateTag(); t.putInt("Energy", 59_990_000); t.putLong("ChargeTick", Long.MIN_VALUE); t.putInt("ChargeUsed", 0);
            var packed = CatalogueContent.stack("best_heavy_mech", 1); BestMechItem.setState(packed, t); var energy = packed.getCapability(Capabilities.EnergyStorage.ITEM); before = BestMechItem.state(packed);
            h.assertTrue(energy != null && energy.getMaxEnergyStored() == 60_000_000 && energy.receiveEnergy(9000, true) == 5000 && before.equals(BestMechItem.state(packed)), "Packed heavy capability simulation is read-only at60M cap");
            h.assertTrue(energy.receiveEnergy(3000, false) == 3000 && BestMechItem.energy(packed).receiveEnergy(5000, false) == 2000 && energy.receiveEnergy(1, false) == 0,
                    "Packed heavy shares5000 per tick budget between handles and permits worn charging");
            t = BestMechItem.state(packed); t.putInt("Energy", 59_999_999); t.putLong("ChargeTick", Long.MIN_VALUE); BestMechItem.setState(packed, t);
            h.assertTrue(energy.receiveEnergy(5000, false) == 1 && energy.getEnergyStored() == 60_000_000 && packed.getItem().getBarWidth(packed) == 13, "Packed heavy clamps charge and bar to60M");
            m.setHealth(0); t.putFloat("Health", 0); BestMechItem.setState(packed, t);
            h.assertTrue(port.receiveEnergy(1, false) == 0 && !port.canReceive() && energy.receiveEnergy(1, false) == 0 && !energy.canReceive(), "Both dead heavy forms reject external charging");
        } finally { clear(p); m.discard(); }
        h.succeed();
    }
}
