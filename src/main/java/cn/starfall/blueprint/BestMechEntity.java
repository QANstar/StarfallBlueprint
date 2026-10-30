package cn.starfall.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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
        builder.define(UTILITY_MODULE, "");
    }
    public int energy() { return entityData.get(ENERGY); }
    public int heat() { return entityData.get(HEAT); }
    public int wear() { return entityData.get(WEAR); }
    public boolean isolated() { return entityData.get(ISOLATED); }
    public boolean thermalLocked() { return entityData.get(LOCKED); }
    public boolean operatorQualified() { return entityData.get(OPERATOR_QUALIFIED); }
    public String moduleKey() { return entityData.get(UTILITY_MODULE); }
    public int movementCost() { return BestMechModules.movementCost(moduleKey()); }
    public int movementHeat() { return BestMechModules.movementHeat(moduleKey()); }
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
    @Override protected float getRiddenSpeed(Player player) { return BestMechModules.speed(moduleKey()); }
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
            int n = Math.max(0, Math.min(Math.min(max, 512 - used), CAPACITY - energy()));
            if(!simulate && n > 0) { entityData.set(ENERGY, energy() + n); chargeTick = now; chargeUsed = used + n; }
            return n;
        }
        public int extractEnergy(int max, boolean simulate) { return 0; }
        public int getEnergyStored() { return energy(); }
        public int getMaxEnergyStored() { return CAPACITY; }
        public boolean canExtract() { return false; }
        public boolean canReceive() { return !isolated() && isAlive(); }
    }; }
    public CompoundTag stateTag() {
        CompoundTag tag = new CompoundTag(); tag.putInt("Energy", energy()); tag.putInt("Heat", heat()); tag.putInt("Wear", wear());
        tag.putBoolean("Isolated", isolated()); tag.putBoolean("ThermalLock", entityData.get(LOCKED));
        tag.putInt("WorkTicks", workTicks); tag.putInt("DrillCooldown", drillCooldown); tag.putFloat("Health", getHealth());
        tag.putLong("ChargeTick", chargeTick); tag.putInt("ChargeUsed", chargeUsed);
        tag.putString(BestMechModules.STATE, moduleKey());
        if(hasCustomName()) tag.putString("Name", Component.Serializer.toJson(getCustomName(), registryAccess()));
        return tag;
    }
    public void loadState(CompoundTag tag) {
        entityData.set(UTILITY_MODULE, BestMechModules.sanitize(tag.getString(BestMechModules.STATE)));
        entityData.set(ENERGY, Math.clamp(tag.getInt("Energy"), 0, CAPACITY));
        entityData.set(HEAT, Math.clamp(tag.getInt("Heat"), 0, MAX_HEAT)); entityData.set(WEAR, Math.clamp(tag.getInt("Wear"), 0, MAX_WEAR));
        entityData.set(ISOLATED, tag.getBoolean("Isolated")); entityData.set(LOCKED, tag.getBoolean("ThermalLock") || heat() >= MAX_HEAT);
        workTicks = Math.clamp(tag.getInt("WorkTicks"), 0, 99); drillCooldown = Math.clamp(tag.getInt("DrillCooldown"), 0, 20);
        chargeTick = tag.contains("ChargeTick") ? tag.getLong("ChargeTick") : Long.MIN_VALUE;
        chargeUsed = Math.clamp(tag.getInt("ChargeUsed"), 0, 5000);
        setHealth(tag.contains("Health") ? Math.clamp(tag.getFloat("Health"), 0, 100) : 100);
        if(tag.contains("Name")) { try { setCustomName(Component.Serializer.fromJson(tag.getString("Name"), registryAccess())); } catch(RuntimeException ignored) {} }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.put("BestMech", stateTag()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); if(tag.contains("BestMech")) loadState(tag.getCompound("BestMech")); setNoAi(true); setPersistenceRequired(); }
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
        player.getInventory().setItem(slot, stack); player.getInventory().setChanged(); packed = true; discard(); return true;
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        if(packed) return; packed = true;
        // A destroyed chassis remains destroyed when packed; repairs require a live deployed chassis.
        if(level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)
                && !(source.getEntity() instanceof Player p && p.isCreative())) spawnAtLocation(packedStack());
    }
    public boolean drill(Player player) {
        if(!(player instanceof ServerPlayer server) || player.getVehicle() != this || getControllingPassenger() != player
                || !qualified(player) || !ready(32000) || drillCooldown > 0 || !onGround() || isInWater() || isInLava()
                || player.isCreative() || !player.mayBuild()) return false;
        Vec3 from = player.getEyePosition(); double reach = Math.min(5, player.blockInteractionRange());
        BlockHitResult hit = level().clip(new ClipContext(from, from.add(player.getLookAngle().scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if(hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = hit.getBlockPos(); if(!level().hasChunkAt(pos) || !level().mayInteract(player, pos) || !level().getWorldBorder().isWithinBounds(pos)) return false;
        var state = level().getBlockState(pos); ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
        if(state.isAir() || state.hasBlockEntity() || !level().getFluidState(pos).isEmpty() || state.getDestroySpeed(level(), pos) < 0
                || (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state))) return false;
        ItemStack held = player.getMainHandItem(); boolean success;
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        try { success = server.gameMode.destroyBlock(pos); }
        finally { player.setItemInHand(InteractionHand.MAIN_HAND, held); }
        if(!success || level().getBlockState(pos).equals(state)) return false;
        spend(32000, 120, 1); drillCooldown = 20; return true;
    }
    public Component status() {
        return Component.literal("工程机甲 · " + energy() + "/" + CAPACITY + " XH · 热 " + heat() + "/1000 · 磨损 " + wear() + "/2400 · 结构 "
                + getHealth() + "/100 · " + (isolated() ? "已隔离" : "已接通") + (entityData.get(LOCKED) ? " · 热锁定（400恢复）" : "")
                + " · 工时 " + workTicks + "/100 · 钻臂冷却 " + drillCooldown + "/20t")
                .append(Component.literal(" · ")).append(BestMechModules.installed(moduleKey()))
                .append(Component.literal(" · ")).append(BestMechModules.controls());
    }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if(level().isClientSide) return InteractionResult.SUCCESS;
        if(!BestMechModules.key(held).isEmpty()) {
            if(!player.isShiftKeyDown() || !installModule(player, hand))
                player.displayClientMessage(Component.translatable("mech.starfall_blueprint.module.failed"), false);
            player.displayClientMessage(status(), false); return InteractionResult.CONSUME;
        }
        boolean diagnostic = held.is(CatalogueContent.item("best_diagnostic_tool"));
        if(diagnostic) {
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
        if(held.is(CatalogueContent.item("steel")) && isolated() && isAlive() && (wear() > 0 || getHealth() < 100)) {
            entityData.set(WEAR, Math.max(0, wear() - 200)); setHealth(Math.min(100, getHealth() + 5)); held.shrink(1);
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
