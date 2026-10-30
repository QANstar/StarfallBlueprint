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
    // Presentation snapshot of the actual operator; player persistent knowledge is not synced to clients.
    private static final EntityDataAccessor<Boolean> OPERATOR_QUALIFIED = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> UTILITY_MODULE = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ENGINEERING_ARM = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> BODY = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CORE = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> CORE_FUEL_TICKS = SynchedEntityData.defineId(BestMechEntity.class, EntityDataSerializers.INT);
    private int coreWorkTicks;
    // Keep one container for the entity's entire lifetime so open menus never retain a replaced inventory.
    private final SimpleContainer cargo = new SimpleContainer(18) {
        @Override public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot >= 0 && slot < cargoSlots() && !(stack.getItem() instanceof BestMechItem);
        }
    };
    private int workTicks, drillCooldown, chargeUsed;
    private long chargeTick = Long.MIN_VALUE;
    private boolean packed;
    public BestMechEntity(EntityType<? extends BestMechEntity> type, Level level) {
        super(type, level); setNoAi(true); setPersistenceRequired(); setHealth(100);
    }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100).add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.STEP_HEIGHT, 1).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(ENERGY, 0); builder.define(HEAT, 0); builder.define(WEAR, 0);
        builder.define(ISOLATED, false); builder.define(LOCKED, false); builder.define(OPERATOR_QUALIFIED, false);
        builder.define(UTILITY_MODULE, ""); builder.define(ENGINEERING_ARM, BestMechArms.DRILL); builder.define(BODY, "");
        builder.define(CORE, ""); builder.define(CORE_FUEL_TICKS, 0);
    }
    public int energy() { return entityData.get(ENERGY); }
    public int heat() { return entityData.get(HEAT); }
    public int wear() { return entityData.get(WEAR); }
    public boolean isolated() { return entityData.get(ISOLATED); }
    public boolean thermalLocked() { return entityData.get(LOCKED); }
    public boolean operatorQualified() { return entityData.get(OPERATOR_QUALIFIED); }
    public String moduleKey() { return entityData.get(UTILITY_MODULE); }
    public String armKey() { return entityData.get(ENGINEERING_ARM); }
    public String bodyKey() { return entityData.get(BODY); }
    public String coreKey() { return entityData.get(CORE); }
    public int coreFuelTicks() { return entityData.get(CORE_FUEL_TICKS); }
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
        if(level().isClientSide || !BestMechCores.GENERATOR.equals(coreKey()) || !ready(0) || capacity() - energy() < 1000) return;
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
        entityData.set(ENERGY, energy() + 1000);
        entityData.set(CORE_FUEL_TICKS, coreFuelTicks() - 1);
        int wear = ++coreWorkTicks >= 1000 ? 1 : 0;
        if(wear != 0) coreWorkTicks = 0;
        spend(0, 2, wear);
    }
    public int capacity() { return BestMechBodies.capacity(bodyKey()); }
    public int cargoSlots() { return BestMechBodies.cargoSlots(bodyKey()); }
    public SimpleContainer cargo() { return cargo; }
    public boolean canAccessCargo(Player player) { return canServiceModule(player) && distanceToSqr(player) <= 64; }
    private void syncBody(String key) {
        entityData.set(BODY, BestMechBodies.sanitize(key));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(BestMechBodies.maxHealth(bodyKey()));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(bodyKey().isEmpty() ? .22 : .18);
    }
    public boolean installBody(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if(hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !canServiceModule(player)
                || !bodyKey().isEmpty() || BestMechBodies.key(held).isEmpty()) return false;
        float health = getHealth(); syncBody(BestMechBodies.REINFORCED); setHealth(health);
        held.shrink(1); player.getInventory().setChanged(); return true;
    }
    public boolean removeBody(Player player) {
        if(!canServiceModule(player) || bodyKey().isEmpty() || energy() > CAPACITY || getHealth() > 100) return false;
        for(int i = 9; i < 18; i++) if(!cargo.getItem(i).isEmpty()) return false;
        int slot = player.getInventory().getFreeSlot(); if(slot < 0) return false;
        float health = getHealth(); syncBody(""); setHealth(health);
        player.getInventory().setItem(slot, CatalogueContent.stack(BestMechBodies.REINFORCED, 1));
        player.getInventory().setChanged(); return true;
    }
    public int workCost() { return BestMechArms.workCost(armKey()); }
    public int workHeat() { return BestMechArms.workHeat(armKey()); }
    public int workCooldown() { return BestMechArms.cooldown(armKey()); }
    public boolean installArm(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand); String key = BestMechArms.key(held);
        if(hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !canServiceModule(player)
                || !armKey().isEmpty() || key.isEmpty()) return false;
        entityData.set(ENGINEERING_ARM, key); held.shrink(1); player.getInventory().setChanged(); return true;
    }
    public boolean removeArm(Player player) {
        if(!canServiceModule(player) || armKey().isEmpty()) return false;
        int slot = player.getInventory().getFreeSlot(); if(slot < 0) return false;
        player.getInventory().setItem(slot, CatalogueContent.stack(armKey(), 1)); entityData.set(ENGINEERING_ARM, "");
        player.getInventory().setChanged(); return true;
    }
    public int movementCost() { return bodyKey().isEmpty() ? BestMechModules.movementCost(moduleKey()) : BestMechModules.DRIVE.equals(moduleKey()) ? 900 : 600; }
    public int movementHeat() { return bodyKey().isEmpty() ? BestMechModules.movementHeat(moduleKey()) : BestMechModules.DRIVE.equals(moduleKey()) ? 8 : 6; }
    public int coolingRate() { return BestMechModules.coolingRate(moduleKey()); }
    private boolean canServiceModule(Player player) {
        return !level().isClientSide && qualified(player) && isAlive() && isolated() && getPassengers().isEmpty()
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
    public void setIsolated(boolean value) { entityData.set(ISOLATED, value); if(value) stopHorizontal(); }
    public boolean ready(int cost) { return isAlive() && !isolated() && !entityData.get(LOCKED) && heat() < MAX_HEAT && wear() < MAX_WEAR && energy() >= Math.max(0, cost); }
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
            entityData.set(OPERATOR_QUALIFIED, getControllingPassenger() instanceof Player operator && qualified(operator));
            if(drillCooldown > 0) drillCooldown--;
            entityData.set(HEAT, Math.max(0, heat() - coolingRate()));
            if(heat() <= RECOVER_HEAT) entityData.set(LOCKED, false);
            tickCore();
            if(!(getControllingPassenger() instanceof Player p) || !qualified(p) || !ready(movementCost())) stopHorizontal();
        }
        super.tick();
    }
    @Override public LivingEntity getControllingPassenger() { return getFirstPassenger() instanceof Player p ? p : null; }
    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty() && passenger instanceof Player; }
    /** The server runs vanilla riding input, collision and gravity; clients interpolate tracking updates. */
    @Override public boolean isControlledByLocalInstance() { return !level().isClientSide; }
    @Override protected Vec3 getRiddenInput(Player player, Vec3 input) {
        return qualified(player) && ready(movementCost()) && onGround() && !isInWater() && !isInLava()
                ? new Vec3(player.xxa * 0.5, 0, player.zza * (player.zza < 0 ? 0.5 : 1)) : Vec3.ZERO;
    }
    @Override protected void tickRidden(Player player, Vec3 input) {
        if(!level().isClientSide && qualified(player) && ready(movementCost())) {
            setYRot(player.getYRot()); setXRot(0); yBodyRot = getYRot(); yHeadRot = getYRot();
        }
        setJumping(false);
    }
    @Override protected float getRiddenSpeed(Player player) {
        return bodyKey().isEmpty() ? BestMechModules.speed(moduleKey()) : BestMechModules.DRIVE.equals(moduleKey()) ? .23f : .18f;
    }
    @Override public void travel(Vec3 input) {
        boolean commanded = !level().isClientSide && getControllingPassenger() instanceof Player p && qualified(p)
                && ready(movementCost()) && onGround() && !isInWater() && !isInLava() && input.horizontalDistanceSqr() > 1.0E-6;
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
            if(level().isClientSide || isolated() || !isAlive()) return 0;
            long now = level().getGameTime(); int used = chargeTick == now ? chargeUsed : 0;
            int n = Math.max(0, Math.min(Math.min(max, 512 - used), capacity() - energy()));
            if(!simulate && n > 0) { entityData.set(ENERGY, energy() + n); chargeTick = now; chargeUsed = used + n; }
            return n;
        }
        public int extractEnergy(int max, boolean simulate) { return 0; }
        public int getEnergyStored() { return energy(); }
        public int getMaxEnergyStored() { return capacity(); }
        public boolean canExtract() { return false; }
        public boolean canReceive() { return !isolated() && isAlive(); }
    }; }
    public CompoundTag stateTag() {
        CompoundTag tag = new CompoundTag(); tag.putInt("Energy", energy()); tag.putInt("Heat", heat()); tag.putInt("Wear", wear());
        tag.putBoolean("Isolated", isolated()); tag.putBoolean("ThermalLock", entityData.get(LOCKED));
        tag.putInt("WorkTicks", workTicks); tag.putInt("DrillCooldown", drillCooldown); tag.putFloat("Health", getHealth());
        tag.putLong("ChargeTick", chargeTick); tag.putInt("ChargeUsed", chargeUsed);
        tag.putString(BestMechModules.STATE, moduleKey()); tag.putString(BestMechArms.STATE, armKey()); tag.putString(BestMechBodies.STATE, bodyKey());
        tag.putString(BestMechCores.STATE, coreKey()); tag.putInt("CoreFuelTicks", coreFuelTicks()); tag.putInt("CoreWorkTicks", coreWorkTicks);
        ListTag items = new ListTag();
        for(int i = 0; i < cargo.getContainerSize(); i++) if(!cargo.getItem(i).isEmpty()) {
            CompoundTag entry = new CompoundTag(); entry.putInt("Slot", i);
            entry.put("Item", cargo.getItem(i).save(registryAccess())); items.add(entry);
        }
        tag.put("Cargo", items);
        if(hasCustomName()) tag.putString("Name", Component.Serializer.toJson(getCustomName(), registryAccess()));
        return tag;
    }
    public void loadState(CompoundTag tag) {
        entityData.set(UTILITY_MODULE, BestMechModules.sanitize(tag.getString(BestMechModules.STATE)));
        entityData.set(ENGINEERING_ARM, BestMechArms.read(tag));
        syncBody(BestMechBodies.read(tag));
        entityData.set(CORE, BestMechCores.read(tag));
        entityData.set(CORE_FUEL_TICKS, Math.clamp(tag.getInt("CoreFuelTicks"), 0, BestMechCores.MAX_FUEL_TICKS));
        coreWorkTicks = Math.clamp(tag.getInt("CoreWorkTicks"), 0, 999);
        entityData.set(ENERGY, Math.clamp(tag.getInt("Energy"), 0, capacity()));
        cargo.clearContent(); ListTag items = tag.getList("Cargo", Tag.TAG_COMPOUND);
        for(int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i); int slot = entry.getInt("Slot");
            ItemStack stack = ItemStack.parseOptional(registryAccess(), entry.getCompound("Item"));
            // Preserve all eighteen physical slots, including legacy/malformed standard-body overflow.
            if(slot >= 0 && slot < 18 && !(stack.getItem() instanceof BestMechItem)) cargo.setItem(slot, stack);
        }
        entityData.set(HEAT, Math.clamp(tag.getInt("Heat"), 0, MAX_HEAT)); entityData.set(WEAR, Math.clamp(tag.getInt("Wear"), 0, MAX_WEAR));
        entityData.set(ISOLATED, tag.getBoolean("Isolated")); entityData.set(LOCKED, tag.getBoolean("ThermalLock") || heat() >= MAX_HEAT);
        workTicks = Math.clamp(tag.getInt("WorkTicks"), 0, 99); drillCooldown = Math.clamp(tag.getInt("DrillCooldown"), 0, 20);
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
        ItemStack stack = CatalogueContent.stack("best_mech", 1); BestMechItem.setState(stack, stateTag());
        if(hasCustomName()) stack.set(DataComponents.CUSTOM_NAME, getCustomName()); return stack;
    }
    public boolean pack(Player player) {
        if(level().isClientSide || packed || !isAlive() || !BestEngineering.hasDiagnostics(player) || !getPassengers().isEmpty()
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
        if(armKey().isEmpty() || !(player instanceof ServerPlayer server) || player.getVehicle() != this || getControllingPassenger() != player
                || !qualified(player) || !ready(workCost()) || drillCooldown > 0 || !onGround() || isInWater() || isInLava()
                || player.isCreative() || !player.mayBuild()) return false;
        Vec3 from = player.getEyePosition(); double reach = Math.min(5, player.blockInteractionRange());
        BlockHitResult hit = level().clip(new ClipContext(from, from.add(player.getLookAngle().scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if(hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = hit.getBlockPos(); if(!level().hasChunkAt(pos) || !level().mayInteract(player, pos) || !level().getWorldBorder().isWithinBounds(pos)) return false;
        var state = level().getBlockState(pos); boolean excavator = BestMechArms.EXCAVATOR.equals(armKey());
        ItemStack tool = new ItemStack(excavator ? Items.IRON_SHOVEL : Items.IRON_PICKAXE);
        if((excavator && !state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL))
                || state.isAir() || state.hasBlockEntity() || !level().getFluidState(pos).isEmpty() || state.getDestroySpeed(level(), pos) < 0
                || (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state))) return false;
        ItemStack held = player.getMainHandItem(); boolean success;
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        try { success = server.gameMode.destroyBlock(pos); }
        finally { player.setItemInHand(InteractionHand.MAIN_HAND, held); }
        if(!success || level().getBlockState(pos).equals(state)) return false;
        spend(workCost(), workHeat(), 1); drillCooldown = workCooldown(); return true;
    }
    public Component status() {
        return Component.literal("工程机甲 · " + energy() + "/" + capacity() + " XH · 热 " + heat() + "/1000 · 磨损 " + wear() + "/2400 · 结构 "
                + getHealth() + "/" + getMaxHealth() + " · " + (isolated() ? "已隔离" : "已接通") + (entityData.get(LOCKED) ? " · 热锁定（400恢复）" : "")
                + " · 工时 " + workTicks + "/100 · 工程臂冷却 " + drillCooldown + "/20t")
                .append(Component.literal(" · ")).append(BestMechModules.installed(moduleKey()))
                .append(Component.literal(" · ")).append(BestMechModules.controls())
                .append(Component.literal(" · ")).append(BestMechArms.installed(armKey()))
                .append(Component.literal(" · ")).append(BestMechArms.controls())
                .append(Component.literal(" · ")).append(BestMechBodies.installed(bodyKey()))
                .append(Component.literal(" · ")).append(BestMechBodies.controls())
                .append(Component.literal(" · ")).append(BestMechCores.installed(coreKey()))
                .append(Component.literal(" · ")).append(BestMechCores.controls())
                .append(Component.literal(" · ")).append(BestMechCores.fuel(coreFuelTicks()));
    }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if(level().isClientSide) return InteractionResult.SUCCESS;
        if(player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND && held.is(Items.CHEST)) {
            if(canAccessCargo(player)) BestMechCargoMenu.open(player, this);
            return InteractionResult.CONSUME;
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
            if(!BestEngineering.hasDiagnostics(player)) return InteractionResult.FAIL;
            if(player.isShiftKeyDown() && getPassengers().isEmpty()) setIsolated(!isolated());
            player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
        }
        if(held.isEmpty()) {
            if(player.isShiftKeyDown()) {
                if(hand == InteractionHand.MAIN_HAND && pack(player)) return InteractionResult.CONSUME;
                player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
            }
            return qualified(player) && getPassengers().isEmpty() && player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        if(!player.isShiftKeyDown() || !BestEngineering.hasDiagnostics(player) || !getPassengers().isEmpty()) return InteractionResult.PASS;
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
