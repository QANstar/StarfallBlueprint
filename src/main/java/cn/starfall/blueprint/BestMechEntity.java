package cn.starfall.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** A manually driven industrial vehicle. No goals, targeting, or autonomous work. */
public final class BestMechEntity extends PathfinderMob {
    public static final int CAPACITY = 20_000_000, MAX_HEAT = 1000, RECOVER_HEAT = 400, MAX_WEAR = 2400;
    private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEAT = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WEAR = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ISOLATED = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LOCKED = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SUPPORT_DEPLOYED = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.BOOLEAN);
    // Presentation snapshot of the actual operator; player persistent knowledge is not synced to clients.
    private static final EntityDataAccessor<Boolean> OPERATOR_QUALIFIED = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> UTILITY_MODULE = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ENGINEERING_ARM = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> BODY = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CORE = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> CORE_FUEL_TICKS = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> LEGS = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.STRING);
    private static final net.minecraft.resources.ResourceLocation CRUISE_STEP = StarfallBlueprint.id("best_mech_cruise_step");
    private static final EntityDataAccessor<Integer> UPGRADES = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.INT);
    private int weaponCooldown;
    private java.util.UUID mechId=java.util.UUID.randomUUID(), engineeringSource, combatSource;
    public String platformType() { return getType()==BestMechs.COMBAT_TYPE.get()?BestMechProgression.COMBAT:getType()==BestMechs.UNIVERSAL_TYPE.get()?BestMechProgression.UNIVERSAL:BestMechProgression.ENGINEERING; }
    public int upgradeMask() { return entityData.get(UPGRADES); }
    public boolean hasUpgrade(int bit) { return (upgradeMask()&bit)!=0; }
    public boolean engineeringCapable() { return !BestMechProgression.COMBAT.equals(platformType()); }
    public boolean areaEngineeringCapable() { return engineeringCapable() && (BestMechArms.areaWork(armKey()) || hasUpgrade(BestMechProgression.ENGINEERING_EXTENSION) || hasUpgrade(BestMechProgression.ZHENYUAN)); }
    public boolean combatCapable() { return !BestMechProgression.ENGINEERING.equals(platformType()); }
    private CompoundTag capabilityState() { CompoundTag tag=new CompoundTag(); tag.putInt(BestMechProgression.STATE,upgradeMask()); tag.putString(BestMechBodies.STATE,bodyKey()); return tag; }
    private int coreWorkTicks;
    // Keep one container for the entity's entire lifetime so open menus never retain a replaced inventory.
    private final SimpleContainer cargo = new SimpleContainer(27) {
        @Override public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot >= 0 && slot < cargoSlots() && !(stack.getItem() instanceof BestMechItem);
        }
    };
    private int workTicks, drillCooldown, chargeUsed;
    private long chargeTick = Long.MIN_VALUE;
    private boolean packed;
    private boolean workbenchServiceRequest, workbenchServiceCommit;
    public BestMechEntity(EntityType<? extends BestMechEntity> type, Level level) {
        super(type, level); setNoAi(true); setPersistenceRequired(); setHealth(getMaxHealth());
    }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100).add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.STEP_HEIGHT, 1).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(UPGRADES,0); builder.define(ENERGY, 0); builder.define(HEAT, 0); builder.define(WEAR, 0);
        builder.define(ISOLATED, false); builder.define(LOCKED, false); builder.define(SUPPORT_DEPLOYED, false); builder.define(OPERATOR_QUALIFIED, false);
        builder.define(UTILITY_MODULE, ""); builder.define(ENGINEERING_ARM, BestMechArms.DRILL); builder.define(BODY, "");
        builder.define(CORE, ""); builder.define(CORE_FUEL_TICKS, 0); builder.define(LEGS, "");
    }
    public int energy() { return entityData.get(ENERGY); }
    public int heat() { return entityData.get(HEAT); }
    public int wear() { return entityData.get(WEAR); }
    public boolean isolated() { return entityData.get(ISOLATED); }
    public boolean thermalLocked() { return entityData.get(LOCKED); }
    public boolean supportDeployed() { return supportsParkingSupport() && entityData.get(SUPPORT_DEPLOYED); }
    public boolean operatorQualified() { return entityData.get(OPERATOR_QUALIFIED); }
    public String moduleKey() { return entityData.get(UTILITY_MODULE); }
    public String armKey() { return entityData.get(ENGINEERING_ARM); }
    public String bodyKey() { return entityData.get(BODY); }
    public String coreKey() { return entityData.get(CORE); }
    public int coreFuelTicks() { return entityData.get(CORE_FUEL_TICKS); }
    public String legsKey() { return entityData.get(LEGS); }
    private void syncLegs(String key) {
        entityData.set(LEGS, BestMechLegs.sanitize(key));
        if(!level().isClientSide) {
            var step = getAttribute(Attributes.STEP_HEIGHT);
            step.removeModifier(CRUISE_STEP);
            if(!legsKey().isEmpty()) step.addTransientModifier(new AttributeModifier(CRUISE_STEP, -.4, AttributeModifier.Operation.ADD_VALUE));
        }
    }
    public boolean installLegs(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if(hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !canServiceModule(player)
                || !legsKey().isEmpty() || BestMechLegs.key(held).isEmpty()) return false;
        syncLegs(BestMechLegs.CRUISE); held.shrink(1); player.getInventory().setChanged(); return true;
    }
    public boolean removeLegs(Player player) {
        if(!canServiceModule(player) || legsKey().isEmpty()) return false;
        int slot = player.getInventory().getFreeSlot(); if(slot < 0) return false;
        player.getInventory().setItem(slot, CatalogueContent.stack(legsKey(), 1)); syncLegs("");
        player.getInventory().setChanged(); return true;
    }
    public boolean installCore(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if(hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !canServiceModule(player)
                || !coreKey().isEmpty() || BestMechCores.key(held).isEmpty()) return false;
        entityData.set(CORE, BestMechCores.GENERATOR); held.shrink(1); player.getInventory().setChanged(); return true;
    }
    public boolean removeCore(Player player) {
        if(!canServiceModule(player) || coreKey().isEmpty() || coreFuelTicks() > 0) return false;
        int slot = player.getInventory().getFreeSlot(); if(slot < 0) return false;
        player.getInventory().setItem(slot, CatalogueContent.stack(coreKey(), 1)); entityData.set(CORE, "");
        player.getInventory().setChanged(); return true;
    }
    /** Internal generation follows cooling and latch recovery, before vanilla movement. */
    private void tickCore() {
        if(level().isClientSide || !BestMechCores.GENERATOR.equals(coreKey()) || !ready(0) || capacity() - energy() < coreOutput()) return;
        if(coreFuelTicks() == 0) {
            for(int slot = 0; slot < cargoSlots(); slot++) {
                ItemStack fuel = cargo.getItem(slot);
                if(fuel.isEmpty() || fuel.hasCraftingRemainingItem()) continue;
                int ticks = fuel.getBurnTime(RecipeType.SMELTING);
                if(ticks <= 0 || ticks > BestMechCores.MAX_FUEL_TICKS) continue;
                fuel.shrink(1); cargo.setChanged();
                entityData.set(CORE_FUEL_TICKS, ticks); break;
            }
        }
        if(coreFuelTicks() == 0) return;
        entityData.set(ENERGY, energy() + coreOutput());
        entityData.set(CORE_FUEL_TICKS, coreFuelTicks() - 1);
        int wear = ++coreWorkTicks >= 1000 ? 1 : 0;
        if(wear != 0) coreWorkTicks = 0;
        spend(0, 2, wear);
    }
    /** Tool and support capabilities come from the current platform and installed components. */
    public boolean supportsAreaTools() { return engineeringCapable() && (BestMechProgression.UNIVERSAL.equals(platformType()) || hasUpgrade(BestMechProgression.ENGINEERING_EXTENSION)); }
    public boolean supportsParkingSupport() { return supportsAreaTools(); }
    public boolean hasExpandedCargo() { return supportsAreaTools(); }
    public int coreOutput() { return supportsAreaTools() ? 2000 : 1000; }
    public int capacity() { return BestMechProgression.capacity(platformType(),capabilityState()); }
    public int cargoSlots() { return hasExpandedCargo() ? 27 : BestMechBodies.cargoSlots(bodyKey()); }
    public SimpleContainer cargo() { return cargo; }
    public boolean canAccessCargo(Player player) { return canServiceModule(player) && distanceToSqr(player) <= 64; }
    public boolean supportsBody() { return BestMechProgression.ENGINEERING.equals(platformType()); }
    private void syncBody(String key) {
        entityData.set(BODY, supportsBody() ? BestMechBodies.sanitize(key) : "");
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(BestMechProgression.maxHealth(platformType(),capabilityState()));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(supportsAreaTools() ? .26 : bodyKey().isEmpty() ? .22 : .18);
    }
    public boolean installBody(Player player, InteractionHand hand) {
        if(!supportsBody()) return false;
        ItemStack held = player.getItemInHand(hand);
        if(hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !canServiceModule(player)
                || !bodyKey().isEmpty() || BestMechBodies.key(held).isEmpty()) return false;
        float health = getHealth(); syncBody(BestMechBodies.REINFORCED); setHealth(health);
        held.shrink(1); player.getInventory().setChanged(); return true;
    }
    public boolean removeBody(Player player) {
        if(!supportsBody()) return false;
        if(!canServiceModule(player) || bodyKey().isEmpty() || energy() > CAPACITY || getHealth() > 100) return false;
        for(int i = 9; i < 18; i++) if(!cargo.getItem(i).isEmpty()) return false;
        int slot = player.getInventory().getFreeSlot(); if(slot < 0) return false;
        float health = getHealth(); syncBody(""); setHealth(health);
        player.getInventory().setItem(slot, CatalogueContent.stack(BestMechBodies.REINFORCED, 1));
        player.getInventory().setChanged(); return true;
    }
    public boolean armCompatible() { return supportsAreaTools() || !BestMechArms.areaWork(armKey()); }
    public String workArmKey() { return effectiveArm(); }
    private String effectiveArm() {
        if(!armKey().isEmpty()) return armKey();
        if(engineeringCapable() && (hasUpgrade(BestMechProgression.ENGINEERING_EXTENSION)||hasUpgrade(BestMechProgression.ZHENYUAN))) return BestMechArms.HEAVY_DRILL;
        return BestMechProgression.UNIVERSAL.equals(platformType()) ? BestMechArms.DRILL : "";
    }
    public int workCost() { return BestMechArms.workCost(effectiveArm()); }
    public int workHeat() { return BestMechArms.workHeat(effectiveArm()); }
    public int workCooldown() { return BestMechArms.cooldown(effectiveArm()); }
    public boolean installArm(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand); String key = BestMechArms.key(held);
        if(hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !canServiceModule(player)
                || !armKey().isEmpty() || key.isEmpty() || (!supportsAreaTools() && BestMechArms.areaWork(key))) return false;
        entityData.set(ENGINEERING_ARM, key); held.shrink(1); player.getInventory().setChanged(); return true;
    }
    public boolean removeArm(Player player) {
        if(!canServiceModule(player) || armKey().isEmpty()) return false;
        int slot = player.getInventory().getFreeSlot(); if(slot < 0) return false;
        player.getInventory().setItem(slot, CatalogueContent.stack(armKey(), 1)); entityData.set(ENGINEERING_ARM, "");
        player.getInventory().setChanged(); return true;
    }
    public float movementSpeed() {
        float base = supportsAreaTools() ? (BestMechModules.DRIVE.equals(moduleKey()) ? .34f : .26f) : bodyKey().isEmpty() ? BestMechModules.speed(moduleKey()) : BestMechModules.DRIVE.equals(moduleKey()) ? .23f : .18f;
        base=Math.max(base,combatCapable()?.28f:base); if(hasUpgrade(BestMechProgression.MOBILITY)||hasUpgrade(BestMechProgression.CHANGCHUAN)) base*=1.3f; return legsKey().isEmpty() ? base : base * 1.25f;
    }
    public int movementCost() {
        int base = supportsAreaTools() ? (BestMechModules.DRIVE.equals(moduleKey()) ? 2400 : 1600) : bodyKey().isEmpty() ? BestMechModules.movementCost(moduleKey()) : BestMechModules.DRIVE.equals(moduleKey()) ? 900 : 600;
        return legsKey().isEmpty() ? base : base * 3 / 4;
    }
    public int movementHeat() {
        int base = supportsAreaTools() ? (BestMechModules.DRIVE.equals(moduleKey()) ? 14 : 10) : bodyKey().isEmpty() ? BestMechModules.movementHeat(moduleKey()) : BestMechModules.DRIVE.equals(moduleKey()) ? 8 : 6;
        return legsKey().isEmpty() ? base : base - 1;
    }
    public int coolingRate() { return (hasUpgrade(BestMechProgression.ENDURANCE)||hasUpgrade(BestMechProgression.SUSTAINED_FIRE)?3:0)+(hasUpgrade(BestMechProgression.TIANPU)||hasUpgrade(BestMechProgression.CHANGCHUAN)?5:0)+BestMechModules.coolingRate(moduleKey()) + (supportsAreaTools() ? 2 : 0) + (supportDeployed() ? 2 : 0); }
    public boolean toggleSupport(Player player) {
        if(level().isClientSide || !supportsParkingSupport() || player.getVehicle() != this || getControllingPassenger() != player || !qualifiedFor(player)) return false;
        if(supportDeployed()) { entityData.set(SUPPORT_DEPLOYED, false); return true; }
        if(!isAlive() || !onGround() || isInWater() || isInLava()
                || getDeltaMovement().horizontalDistanceSqr() > 0.001 || Math.abs(getDeltaMovement().y) > 0.1) return false;
        entityData.set(SUPPORT_DEPLOYED, true); stopHorizontal(); return true;
    }
    private boolean canServiceModule(Player player) {
        return isolated() && canServiceAtRest(player);
    }
    private boolean canServiceAtRest(Player player) {
        return !level().isClientSide && qualifiedFor(player) && isAlive() && getPassengers().isEmpty()
                && onGround() && getDeltaMovement().horizontalDistanceSqr() <= 0.001
                && Math.abs(getDeltaMovement().y) <= 0.1 && !isInWater() && !isInLava();
    }
    public boolean installModule(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand); String key = BestMechModules.key(held);
        if(!canServiceModule(player) || !moduleKey().isEmpty() || key.isEmpty()) return false;
        entityData.set(UTILITY_MODULE, key); held.shrink(1); player.getInventory().setChanged(); return true;
    }
    public boolean removeModule(Player player) {
        if(!canServiceModule(player) || moduleKey().isEmpty()) return false;
        int slot = player.getInventory().getFreeSlot(); if(slot < 0) return false;
        ItemStack module = CatalogueContent.stack(moduleKey(), 1);
        player.getInventory().setItem(slot, module); entityData.set(UTILITY_MODULE, "");
        player.getInventory().setChanged(); return true;
    }
    /** Manually service one unambiguous nearby mech from the workbench menu. */
    static boolean serviceAtWorkbench(ServerPlayer player, BlockPos station) {
        if(player instanceof net.neoforged.neoforge.common.util.FakePlayer
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player) return false;
        if(!(player.containerMenu instanceof BestWorkbenchMenu menu) || !menu.stillValid(player)) return false;
        var level = player.serverLevel();
        Vec3 center = Vec3.atCenterOf(station);
        var targets = level.getEntitiesOfClass(BestMechEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(6),
                mech -> !mech.isRemoved() && mech.position().distanceToSqr(center) <= 36);
        if(targets.size() != 1) return serviceMessage(player, targets.isEmpty() ? "no_target" : "multiple");
        BestMechEntity mech = targets.getFirst();
        if(mech.workbenchServiceRequest || !mech.validWorkbenchService(player, station)) return serviceMessage(player, "unsafe");
        mech.workbenchServiceRequest = true;
        try {
        var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, mech);
        if(net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled()) return serviceMessage(player, "unsafe");
        targets = level.getEntitiesOfClass(BestMechEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(6),
                candidate -> !candidate.isRemoved() && candidate.position().distanceToSqr(center) <= 36);
        if(player.containerMenu != menu || !menu.stillValid(player) || targets.size() != 1
                || targets.getFirst() != mech || !mech.validWorkbenchService(player, station))
            return serviceMessage(player, "unsafe");
        var inventory = player.getInventory();
        java.util.List<ItemStack> plan = new java.util.ArrayList<>();
        for(int i = 0; i < 36; i++) plan.add(inventory.getItem(i).copy());
        boolean repairNeeded = mech.wear() > 0 || mech.getHealth() < mech.getMaxHealth();
        boolean coolNeeded = mech.heat() > 0;
        boolean repaired = false, cooled = false;
        if(repairNeeded) for(ItemStack stack : plan) if(stack.is(CatalogueContent.item("steel"))) {
            stack.shrink(1); repaired = true; break;
        }
        if(coolNeeded) for(int i = 0; i < 36; i++) if(plan.get(i).is(Items.WATER_BUCKET)) {
            java.util.List<ItemStack> cooling = new java.util.ArrayList<>();
            for(ItemStack stack : plan) cooling.add(stack.copy());
            cooling.get(i).shrink(1);
            ItemStack bucket = new ItemStack(Items.BUCKET);
            for(ItemStack stack : cooling) if(ItemStack.isSameItemSameComponents(stack, bucket) && stack.getCount() < stack.getMaxStackSize()) {
                stack.grow(1); cooled = true; break;
            }
            if(!cooled) for(int j = 0; j < 36; j++) if(cooling.get(j).isEmpty()) {
                cooling.set(j, bucket); cooled = true; break;
            }
            if(cooled) plan = cooling;
            break;
        }
        if(!repaired && !cooled) return serviceMessage(player, "nothing");
        mech.workbenchServiceCommit = true;
        try {
            for(int i = 0; i < 36; i++) inventory.setItem(i, plan.get(i));
            if(cooled) {
                mech.entityData.set(HEAT, Math.max(0, mech.heat() - 400));
                if(mech.heat() <= RECOVER_HEAT) mech.entityData.set(LOCKED, false);
            }
            if(repaired) {
                mech.entityData.set(WEAR, Math.max(0, mech.wear() - 200));
                mech.setHealth(Math.min(mech.getMaxHealth(), mech.getHealth() + 5));
            }
        } finally { mech.workbenchServiceCommit = false; }
        inventory.setChanged(); player.inventoryMenu.broadcastChanges(); player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("message.starfall_blueprint.mech_service_"
                + (repaired && cooled ? "both" : repaired ? (coolNeeded ? "repair_partial" : "repair") : (repairNeeded ? "cool_partial" : "cool"))), true);
        return true;
        } finally { mech.workbenchServiceRequest = false; }
    }
    private static boolean serviceMessage(ServerPlayer player, String reason) {
        player.displayClientMessage(Component.translatable("message.starfall_blueprint.mech_service_" + reason), true);
        return false;
    }
    private boolean validWorkbenchService(ServerPlayer player, BlockPos station) {
        return !isRemoved() && player.server.getPlayerList().getPlayer(player.getUUID()) == player
                && player.level() == level() && level().getBlockState(station).is(BestEngineering.WORKBENCH.get())
                && level().mayInteract(player, station) && level().mayInteract(player, blockPosition())
                && player.distanceToSqr(Vec3.atCenterOf(station)) <= 64 && distanceToSqr(player) <= 64
                && position().distanceToSqr(Vec3.atCenterOf(station)) <= 36 && canServiceAtRest(player);
    }
    public void setIsolated(boolean value) { entityData.set(ISOLATED, value); if(value) stopHorizontal(); }
    public boolean ready(int cost) { return isAlive() && !isolated() && !workbenchServiceCommit && !entityData.get(LOCKED) && heat() < MAX_HEAT && wear() < MAX_WEAR && energy() >= Math.max(0, cost); }
    public boolean qualifiedFor(Player player) { return qualifiedFor(player,platformType()); }
    public static boolean qualifiedFor(Player player,String type) {
        if(!player.isAlive() || player.isSpectator()) return false;
        if(BestMechProgression.COMBAT.equals(type)) return Progression.isLearned(player,"best:combat_operation");
        if(BestMechProgression.UNIVERSAL.equals(type)) return Progression.isLearned(player,"best:confluence");
        return qualified(player);
    }
    public static boolean qualified(Player player) {
        return player.isAlive() && !player.isSpectator() && BestEngineering.hasMechOperation(player)
                && BestEngineering.researchLevel(player) >= 3 && BestEngineering.hasDiagnostics(player) && BestEngineering.hasLightMobility(player);
    }
    private void stopHorizontal() { Vec3 v = getDeltaMovement(); setDeltaMovement(0, v.y, 0); }
    private void spend(int cost, int heat, int wear) {
        entityData.set(ENERGY, Math.max(0, energy() - cost));
        entityData.set(HEAT, Math.min(MAX_HEAT, heat() + heat));
        entityData.set(WEAR, Math.min(MAX_WEAR, wear() + wear));
        if(heat() >= MAX_HEAT) entityData.set(LOCKED, true);
        if(!ready(movementCost())) stopHorizontal();
    }
    @Override public void tick() {
        if(!level().isClientSide) {
            entityData.set(OPERATOR_QUALIFIED, getControllingPassenger() instanceof Player operator && qualifiedFor(operator));
            if(drillCooldown > 0) drillCooldown--; if(weaponCooldown>0) weaponCooldown--;
            entityData.set(HEAT, Math.max(0, heat() - coolingRate()));
            if(heat() <= RECOVER_HEAT) entityData.set(LOCKED, false);
            tickCore();
            if(supportDeployed() || !(getControllingPassenger() instanceof Player p) || !qualifiedFor(p) || !ready(movementCost())) stopHorizontal();
        }
        super.tick();
    }
    @Override public LivingEntity getControllingPassenger() { return getFirstPassenger() instanceof Player p ? p : null; }
    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty() && passenger instanceof Player; }
    /** The server runs vanilla riding input, collision and gravity; clients interpolate tracking updates. */
    @Override public boolean isControlledByLocalInstance() { return !level().isClientSide; }
    @Override protected Vec3 getRiddenInput(Player player, Vec3 input) {
        return !supportDeployed() && qualifiedFor(player) && ready(movementCost()) && onGround() && !isInWater() && !isInLava()
                ? new Vec3(player.xxa * 0.5, 0, player.zza * (player.zza < 0 ? 0.5 : 1)) : Vec3.ZERO;
    }
    @Override protected void tickRidden(Player player, Vec3 input) {
        if(!level().isClientSide && qualifiedFor(player) && ready(movementCost())) {
            setYRot(player.getYRot()); setXRot(0); yBodyRot = getYRot(); yHeadRot = getYRot();
        }
        setJumping(false);
    }
    @Override protected float getRiddenSpeed(Player player) {
        return movementSpeed();
    }
    @Override public void travel(Vec3 input) {
        boolean commanded = !level().isClientSide && getControllingPassenger() instanceof Player p && qualifiedFor(p)
                && !supportDeployed() && ready(movementCost()) && onGround() && !isInWater() && !isInLava() && input.horizontalDistanceSqr() > 1.0E-6;
        // No acceleration or residual powered motion without a valid grounded command.
        stopHorizontal();
        double beforeX = getX(), beforeZ = getZ();
        super.travel(commanded ? input : Vec3.ZERO);
        if(commanded && Math.abs(getX() - beforeX) + Math.abs(getZ() - beforeZ) > 1.0E-6) {
            spend(movementCost(), movementHeat(), 0); if(++workTicks >= 100) { workTicks = 0; entityData.set(WEAR, Math.min(MAX_WEAR, wear() + 1)); }
        }
        if(!commanded || !ready(movementCost())) stopHorizontal();
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean onClimbable() { return false; }
    @Override public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) { return false; }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // Never gate dismount on power, knowledge, isolation or cooldown.
        BlockPos base = blockPosition();
        for(int radius = 2; radius <= 5; radius++) for(int dy = 1; dy >= -2; dy--)
            for(int dx = -radius; dx <= radius; dx++) for(int dz = -radius; dz <= radius; dz++) {
                if(Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                BlockPos pos = base.offset(dx, dy, dz);
                if(!level().hasChunkAt(pos) || !level().getFluidState(pos).isEmpty()) continue;
                Vec3 safe = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), pos, true);
                if(safe != null && level().noCollision(passenger, passenger.getType().getDimensions().makeBoundingBox(safe))) return safe;
            }
        // Enclosed vehicle: roof exit is collision checked, then vanilla emergency fallback.
        for(int rise = 0; rise <= 16; rise++) {
            Vec3 roof = new Vec3(getX(), getBoundingBox().maxY + 0.01 + rise, getZ());
            if(roof.y + passenger.getBbHeight() >= level().getMaxBuildHeight()) break;
            if(DismountHelper.canDismountTo(level(), roof, passenger, Pose.STANDING)
                    && level().getFluidState(BlockPos.containing(roof)).isEmpty()) return roof;
        }
        return super.getDismountLocationForPassenger(passenger);
    }
    public IEnergyStorage energyPort() { return new IEnergyStorage() {
        public int receiveEnergy(int max, boolean simulate) {
            if(level().isClientSide || isolated() || workbenchServiceCommit || !isAlive()) return 0;
            long now = level().getGameTime(); int used = chargeTick == now ? chargeUsed : 0;
            int n = Math.max(0, Math.min(Math.min(max, 512 - used), capacity() - energy()));
            if(!simulate && n > 0) { entityData.set(ENERGY, energy() + n); chargeTick = now; chargeUsed = used + n; }
            return n;
        }
        public int extractEnergy(int max, boolean simulate) { return 0; }
        public int getEnergyStored() { return energy(); }
        public int getMaxEnergyStored() { return capacity(); }
        public boolean canExtract() { return false; }
        public boolean canReceive() { return !isolated() && !workbenchServiceCommit && isAlive(); }
    }; }
    public CompoundTag stateTag() {
        CompoundTag tag = new CompoundTag(); tag.putInt(BestMechProgression.STATE,upgradeMask()); tag.putInt("WeaponCooldown",weaponCooldown); tag.putInt("Energy", energy()); tag.putInt("Heat", heat()); tag.putInt("Wear", wear());
        tag.putBoolean("Isolated", isolated()); tag.putBoolean("ThermalLock", entityData.get(LOCKED)); tag.putBoolean("SupportDeployed", supportDeployed());
        tag.putInt("WorkTicks", workTicks); tag.putInt("DrillCooldown", drillCooldown); tag.putFloat("Health", getHealth());
        tag.putLong("ChargeTick", chargeTick); tag.putInt("ChargeUsed", chargeUsed);
        tag.putString(BestMechModules.STATE, moduleKey()); tag.putString(BestMechArms.STATE, armKey()); tag.putString(BestMechBodies.STATE, bodyKey());
        tag.putString(BestMechCores.STATE, coreKey()); tag.putInt("CoreFuelTicks", coreFuelTicks()); tag.putInt("CoreWorkTicks", coreWorkTicks);
        tag.putString(BestMechLegs.STATE, legsKey());
        ListTag items = new ListTag();
        for(int i = 0; i < cargoSlots(); i++) if(!cargo.getItem(i).isEmpty()) {
            CompoundTag entry = new CompoundTag(); entry.putInt("Slot", i);
            entry.put("Item", cargo.getItem(i).save(registryAccess())); items.add(entry);
        }
        tag.put("Cargo", items);
        tag.putUUID("MechId",mechId);
        if(engineeringSource!=null) tag.putUUID("EngineeringSource",engineeringSource);
        if(combatSource!=null) tag.putUUID("CombatSource",combatSource);
        if(hasCustomName()) tag.putString("Name", Component.Serializer.toJson(getCustomName(), registryAccess()));
        return tag;
    }
    public void loadState(CompoundTag tag) {
        tag=tag.copy();
        if(tag.hasUUID("MechId")) mechId=tag.getUUID("MechId");
        engineeringSource=tag.hasUUID("EngineeringSource")?tag.getUUID("EngineeringSource"):null;
        combatSource=tag.hasUUID("CombatSource")?tag.getUUID("CombatSource"):null;
        entityData.set(UPGRADES,BestMechProgression.sanitize(platformType(),BestMechProgression.mask(tag)));
        weaponCooldown=Math.clamp(tag.getInt("WeaponCooldown"),0,100);
        entityData.set(UTILITY_MODULE, BestMechModules.sanitize(tag.getString(BestMechModules.STATE)));
        entityData.set(ENGINEERING_ARM, BestMechArms.read(tag));
        syncBody(BestMechBodies.read(tag));
        syncLegs(BestMechLegs.read(tag));
        entityData.set(CORE, BestMechCores.read(tag));
        entityData.set(CORE_FUEL_TICKS, Math.clamp(tag.getInt("CoreFuelTicks"), 0, BestMechCores.MAX_FUEL_TICKS));
        coreWorkTicks = Math.clamp(tag.getInt("CoreWorkTicks"), 0, 999);
        entityData.set(ENERGY, Math.clamp(tag.getInt("Energy"), 0, capacity()));
        cargo.clearContent(); ListTag items = tag.getList("Cargo", Tag.TAG_COMPOUND);
        for(int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i); int slot = entry.getInt("Slot");
            ItemStack stack = ItemStack.parseOptional(registryAccess(), entry.getCompound("Item"));
            // Only active cargo cells belong to this configured platform.
            if(slot >= 0 && slot < cargoSlots() && !(stack.getItem() instanceof BestMechItem)) cargo.setItem(slot, stack);
        }
        entityData.set(HEAT, Math.clamp(tag.getInt("Heat"), 0, MAX_HEAT)); entityData.set(WEAR, Math.clamp(tag.getInt("Wear"), 0, MAX_WEAR));
        entityData.set(ISOLATED, tag.getBoolean("Isolated")); entityData.set(LOCKED, tag.getBoolean("ThermalLock") || heat() >= MAX_HEAT);
        entityData.set(SUPPORT_DEPLOYED, supportsParkingSupport() && tag.getBoolean("SupportDeployed"));
        workTicks = Math.clamp(tag.getInt("WorkTicks"), 0, 99); drillCooldown = Math.clamp(tag.getInt("DrillCooldown"), 0, 40);
        chargeTick = tag.contains("ChargeTick") ? tag.getLong("ChargeTick") : Long.MIN_VALUE;
        chargeUsed = Math.clamp(tag.getInt("ChargeUsed"), 0, 5000);
        setHealth(tag.contains("Health") ? Math.clamp(tag.getFloat("Health"), 0, getMaxHealth()) : getMaxHealth());
        if(tag.contains("Name")) { try { setCustomName(Component.Serializer.fromJson(tag.getString("Name"), registryAccess())); } catch(RuntimeException ignored) {} }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.put("BestMech", stateTag()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        syncBody(BestMechBodies.read(tag.getCompound("BestMech")));
        super.readAdditionalSaveData(tag);
        if(tag.contains("BestMech")) loadState(tag.getCompound("BestMech"));
        setNoAi(true); setPersistenceRequired();
    }
    private ItemStack packedStack() {
        ItemStack stack = CatalogueContent.stack(BestMechProgression.itemKey(platformType()), 1); BestMechItem.setState(stack, stateTag());
        if(hasCustomName()) stack.set(DataComponents.CUSTOM_NAME, getCustomName()); return stack;
    }
    public boolean pack(Player player) {
        if(level().isClientSide || packed || !isAlive() || !qualifiedFor(player) || !getPassengers().isEmpty()
                || !isolated() || !onGround() || getDeltaMovement().horizontalDistanceSqr() > 0.001
                || Math.abs(getDeltaMovement().y) > 0.1 || isInWater() || isInLava()) return false;
        ItemStack stack = packedStack(); int slot = player.getInventory().getFreeSlot();
        if(slot < 0) return false;
        player.getInventory().setItem(slot, stack); player.getInventory().setChanged(); packed = true; cargo.clearContent(); discard(); return true;
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        if(packed) return; packed = true;
        // A destroyed chassis remains destroyed when packed; repairs require a live deployed chassis.
        boolean drops = level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)
                && !(source.getEntity() instanceof Player p && p.isCreative());
        for(int i = 0; i < cargo.getContainerSize(); i++) {
            ItemStack stack = cargo.removeItemNoUpdate(i); if(drops && !stack.isEmpty()) spawnAtLocation(stack);
        }
        cargo.clearContent();
        if(drops) spawnAtLocation(packedStack());
    }
    public boolean drill(Player player) { return work(player); }
    public boolean work(Player player) {
        if(combatCapable() && !engineeringCapable()) return fireWeapon(player);
        if(!engineeringCapable()) return false;
        if(effectiveArm().isEmpty() || !armCompatible() || !(player instanceof ServerPlayer server) || player.getVehicle() != this || getControllingPassenger() != player
                || !qualifiedFor(player) || !ready(workCost()) || drillCooldown > 0 || !onGround() || isInWater() || isInLava()
                || player.isCreative() || !player.mayBuild()) return false;
        Vec3 from = player.getEyePosition(); double reach = Math.min(5, player.blockInteractionRange());
        BlockHitResult hit = level().clip(new ClipContext(from, from.add(player.getLookAngle().scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if(hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos center = hit.getBlockPos();
        boolean areaWork = !player.isShiftKeyDown() && (BestMechArms.areaWork(armKey()) || hasUpgrade(BestMechProgression.ENGINEERING_EXTENSION) || hasUpgrade(BestMechProgression.ZHENYUAN));
        if(!breakWorkBlock(server, center, from, reach, areaWork)) return false;
        // Cooldown belongs to the action. Candidate breaks deliberately do not re-enter work().
        drillCooldown = workCooldown();
        if(areaWork) {
            var axis = hit.getDirection().getAxis();
            // Rows ascend in the second plane coordinate; columns ascend in the first.
            for(int row = -1; row <= 1; row++) for(int column = -1; column <= 1; column++) {
                if(row == 0 && column == 0) continue;
                if(!ready(workCost())) return true;
                BlockPos candidate = switch(axis) {
                    case X -> center.offset(0, column, row);
                    case Y -> center.offset(column, 0, row);
                    case Z -> center.offset(column, row, 0);
                };
                breakWorkBlock(server, candidate, from, reach, true);
            }
        }
        return true;
    }
    /** Server-authoritative direct fire: nearest intersected living target, never through blocks. */
    public boolean fireWeapon(Player player) {
        int cost=hasUpgrade(BestMechProgression.TIANPU)?24000:12000;
        if(level().isClientSide || !combatCapable() || getControllingPassenger()!=player || !qualifiedFor(player) || !ready(cost) || weaponCooldown>0 || player.isSpectator()) return false;
        Vec3 start=player.getEyePosition(), end=start.add(player.getLookAngle().scale(hasUpgrade(BestMechProgression.FIRE_CONTROL)?64:40));
        var block=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(block.getType()!=HitResult.Type.MISS) end=block.getLocation();
        LivingEntity target=null; double nearest=start.distanceToSqr(end); final Vec3 rayEnd=end;
        for(LivingEntity candidate:level().getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(1),e->e!=this&&e!=player&&e.isAlive()&&!e.isSpectator())) {
            var hit=candidate.getBoundingBox().inflate(.15).clip(start,rayEnd);
            if(hit.isPresent() && start.distanceToSqr(hit.get())<nearest) { nearest=start.distanceToSqr(hit.get());target=candidate; }
        }
        spend(cost,hasUpgrade(BestMechProgression.SUSTAINED_FIRE)?35:65,1);
        weaponCooldown=hasUpgrade(BestMechProgression.TIANPU)?8:hasUpgrade(BestMechProgression.SUSTAINED_FIRE)?12:20;
        if(target!=null) {
            target.hurt(damageSources().playerAttack(player),hasUpgrade(BestMechProgression.TIANPU)?18:10);
            if(hasUpgrade(BestMechProgression.FIRE_CONTROL)||hasUpgrade(BestMechProgression.TIANPU)) {
                for(LivingEntity marked:level().getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(6),e->e!=this&&e!=player&&e.isAlive()&&hasLineOfSight(e)))
                    marked.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,100,0));
            }
        }
        ((ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,end.x,end.y,end.z,12,.15,.15,.15,.02);
        return true;
    }
    @Override public boolean hurt(DamageSource source,float damage) {
        if(hasUpgrade(BestMechProgression.ARMOR)||hasUpgrade(BestMechProgression.ZHENYUAN)) damage*=.75f;
        return super.hurt(source,damage);
    }
    private boolean breakWorkBlock(ServerPlayer player, BlockPos pos, Vec3 eye, double reach, boolean areaWork) {
        if(!level().hasChunkAt(pos) || !level().getWorldBorder().isWithinBounds(pos) || !level().mayInteract(player, pos)) return false;
        if(areaWork) {
            double x = net.minecraft.util.Mth.clamp(eye.x, pos.getX(), pos.getX() + 1.0);
            double y = net.minecraft.util.Mth.clamp(eye.y, pos.getY(), pos.getY() + 1.0);
            double z = net.minecraft.util.Mth.clamp(eye.z, pos.getZ(), pos.getZ() + 1.0);
            if(eye.distanceToSqr(x, y, z) > reach * reach) return false;
        }
        var state = level().getBlockState(pos); boolean excavator = BestMechArms.EXCAVATOR.equals(armKey()) || BestMechArms.HEAVY_EXCAVATOR.equals(armKey());
        ItemStack tool = new ItemStack(BestMechArms.HEAVY_DRILL.equals(effectiveArm()) ? Items.DIAMOND_PICKAXE : excavator ? Items.IRON_SHOVEL : Items.IRON_PICKAXE);
        if((excavator && !state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL))
                || state.isAir() || state.hasBlockEntity() || !level().getFluidState(pos).isEmpty() || state.getDestroySpeed(level(), pos) < 0
                || (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state))) return false;
        ItemStack held = player.getMainHandItem(); boolean success;
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        BestMechLoot.Scope loot = BestMechLoot.begin(player, pos, tool);
        try { success = player.gameMode.destroyBlock(pos); }
        finally { loot.close(); player.setItemInHand(InteractionHand.MAIN_HAND, held); }
        if(!success || level().getBlockState(pos).equals(state)) return false;
        loot.collect(this);
        spend(workCost(), workHeat(), 1); return true;
    }
    public Component status() {
        return Component.literal("工程机甲 · " + energy() + "/" + capacity() + " XH · 热 " + heat() + "/1000 · 磨损 " + wear() + "/2400 · 结构 "
                + getHealth() + "/" + getMaxHealth() + " · " + (isolated() ? "已隔离" : "已接通") + (entityData.get(LOCKED) ? " · 热锁定（400恢复）" : "")
                + " · 工时 " + workTicks + "/100 · 工程臂冷却 " + drillCooldown + "/" + Math.max(20, Math.max(workCooldown(), drillCooldown)) + "t")
                .append(Component.literal(" · ")).append(BestMechModules.installed(moduleKey()))
                .append(Component.literal(" · ")).append(BestMechModules.controls())
                .append(Component.literal(" · ")).append(BestMechArms.installed(armKey()))
                .append(armCompatible() ? Component.empty() : Component.translatable("mech.starfall_blueprint.arm.heavy_required"))
                .append(Component.literal(" · ")).append(BestMechArms.controls())
                .append(Component.literal(" · ")).append(supportsBody() ? BestMechBodies.installed(bodyKey()) : getName())
                .append(Component.literal(" · ")).append(supportsBody() ? BestMechBodies.controls() : Component.empty())
                .append(Component.literal(" · ")).append(BestMechCores.installed(coreKey()))
                .append(Component.literal(" · ")).append(BestMechCores.controls())
                .append(Component.literal(" · ")).append(BestMechCores.fuel(coreFuelTicks()))
                .append(Component.literal(" · ")).append(BestMechLegs.installed(legsKey()))
                .append(Component.literal(" · ")).append(BestMechLegs.controls());
    }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if(level().isClientSide) return InteractionResult.SUCCESS;
        if(player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND && held.is(Items.CHEST)) {
            if(canAccessCargo(player)) BestMechCargoMenu.open(player, this);
            return InteractionResult.CONSUME;
        }
        if(!BestMechLegs.key(held).isEmpty()) {
            if(!installLegs(player, hand)) player.displayClientMessage(Component.translatable("mech.starfall_blueprint.legs.failed"), false);
            player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
        }
        if(!BestMechCores.key(held).isEmpty()) {
            if(!installCore(player, hand)) player.displayClientMessage(Component.translatable("mech.starfall_blueprint.core.failed"), false);
            player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
        }
        if(!BestMechBodies.key(held).isEmpty()) {
            if(!installBody(player, hand)) player.displayClientMessage(Component.translatable("mech.starfall_blueprint.body.failed"), false);
            player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
        }
        if(!BestMechArms.key(held).isEmpty()) {
            if(!installArm(player, hand)) player.displayClientMessage(Component.translatable("mech.starfall_blueprint.arm.failed"), false);
            player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
        }
        if(!BestMechModules.key(held).isEmpty()) {
            if(!player.isShiftKeyDown() || !installModule(player, hand))
                player.displayClientMessage(Component.translatable("mech.starfall_blueprint.module.failed"), false);
            player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
        }
        boolean diagnostic = held.is(CatalogueContent.item("best_diagnostic_tool"));
        if(diagnostic) {
            if(player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND && player.getOffhandItem().is(Items.MINECART)) {
                if(!removeLegs(player)) player.displayClientMessage(Component.translatable("mech.starfall_blueprint.legs.failed"), false);
                player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
            }
            if(player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND && player.getOffhandItem().is(Items.FURNACE)) {
                if(!removeCore(player)) player.displayClientMessage(Component.translatable("mech.starfall_blueprint.core.failed"), false);
                player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
            }
            if(player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND && player.getOffhandItem().is(Items.IRON_BLOCK)) {
                if(!removeBody(player)) player.displayClientMessage(Component.translatable("mech.starfall_blueprint.body.failed"), false);
                player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
            }
            if(player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND && player.getOffhandItem().is(Items.IRON_PICKAXE)) {
                if(!removeArm(player)) player.displayClientMessage(Component.translatable("mech.starfall_blueprint.arm.failed"), false);
                player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
            }
            if(player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND
                    && player.getOffhandItem().is(CatalogueContent.item("steel_fix"))) {
                // An explicit tool selects removal even on failure; never fall through to isolation toggle.
                if(!removeModule(player)) player.displayClientMessage(Component.translatable("mech.starfall_blueprint.module.failed"), false);
                player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
            }
            if(!qualifiedFor(player)) return InteractionResult.FAIL;
            if(player.isShiftKeyDown() && getPassengers().isEmpty()) setIsolated(!isolated());
            player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
        }
        if(held.isEmpty()) {
            if(player.isShiftKeyDown()) {
                if(hand == InteractionHand.MAIN_HAND && pack(player)) return InteractionResult.CONSUME;
                player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
            }
            return qualifiedFor(player) && getPassengers().isEmpty() && player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        if(!player.isShiftKeyDown() || !qualifiedFor(player) || !getPassengers().isEmpty()) return InteractionResult.PASS;
        if(held.is(CatalogueContent.item("s01"))) {
            IEnergyStorage source = CatalogueEquipment.energy(held), target = energyPort();
            int n = target.receiveEnergy(source.extractEnergy(512, true), true);
            if(n > 0) target.receiveEnergy(source.extractEnergy(n, false), false);
            player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
        }
        if(held.is(CatalogueContent.item("steel")) && isolated() && isAlive() && (wear() > 0 || getHealth() < getMaxHealth())) {
            entityData.set(WEAR, Math.max(0, wear() - 200)); setHealth(Math.min(getMaxHealth(), getHealth() + 5)); held.shrink(1);
            player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
        }
        if(held.is(Items.WATER_BUCKET) && heat() > 0 && isolated()) {
            int slot = player.getInventory().getFreeSlot();
            if(held.getCount() > 1 && slot < 0) return InteractionResult.FAIL;
            entityData.set(HEAT, Math.max(0, heat() - 400)); if(heat() <= RECOVER_HEAT) entityData.set(LOCKED, false);
            if(held.getCount() == 1) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            else { held.shrink(1); player.getInventory().setItem(slot, new ItemStack(Items.BUCKET)); }
            player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
