package cn.starfall.blueprint;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Packed state is the only energy balance; never cached in a capability object. */
public final class BestMechItem extends Item {
    private static final String STATE = "BestMech";
    public BestMechItem(Properties properties) { super(properties.stacksTo(1)); }
    public static CompoundTag state(ItemStack stack) { return CatalogueEquipment.getData(stack).getCompound(STATE).copy(); }
    public static void setState(ItemStack stack, CompoundTag state) {
        CompoundTag data = CatalogueEquipment.getData(stack); data.put(STATE, state.copy()); CatalogueEquipment.setData(stack, data);
    }
    public static IEnergyStorage energy(ItemStack stack) { return new IEnergyStorage() {
        private long tick() { var server = ServerLifecycleHooks.getCurrentServer(); return server == null ? 0 : server.overworld().getGameTime(); }
        public int receiveEnergy(int max, boolean simulate) {
            CompoundTag tag = state(stack);
            if(tag.getBoolean("Isolated") || (tag.contains("Health") && !(tag.getFloat("Health") > 0))) return 0;
            long now = tick(); int used = tag.getLong("ChargeTick") == now ? Math.clamp(tag.getInt("ChargeUsed"), 0, 5000) : 0;
            int stored = getEnergyStored(), n = Math.max(0, Math.min(Math.min(max, 5000 - used), BestMechEntity.CAPACITY - stored));
            if(!simulate && n > 0) { tag.putInt("Energy", stored + n); tag.putLong("ChargeTick", now); tag.putInt("ChargeUsed", used + n); setState(stack, tag); }
            return n;
        }
        public int extractEnergy(int max, boolean simulate) { return 0; }
        public int getEnergyStored() { return Math.clamp(state(stack).getInt("Energy"), 0, BestMechEntity.CAPACITY); }
        public int getMaxEnergyStored() { return BestMechEntity.CAPACITY; }
        public boolean canReceive() { var tag = state(stack); return !tag.getBoolean("Isolated") && (!tag.contains("Health") || tag.getFloat("Health") > 0); }
        public boolean canExtract() { return false; }
    }; }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer(); if(player == null || !BestMechEntity.qualified(player)) return InteractionResult.FAIL;
        Level level = context.getLevel(); ItemStack stack = context.getItemInHand();
        if(player.isShiftKeyDown()) {
            if(!energy(stack).canReceive()) return InteractionResult.FAIL;
            if(level.getCapability(Capabilities.EnergyStorage.BLOCK, context.getClickedPos(), context.getClickedFace()) == null) return InteractionResult.FAIL;
            player.startUsingItem(context.getHand()); return InteractionResult.CONSUME;
        }
        if(level.isClientSide) return InteractionResult.SUCCESS;
        CompoundTag tag = state(stack);
        if(tag.contains("Health") && !(tag.getFloat("Health") > 0)) return InteractionResult.FAIL;
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if(!level.hasChunkAt(pos) || !level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), stack)) return InteractionResult.FAIL;
        BestMechEntity mech = BestMechs.TYPE.get().create(level); if(mech == null) return InteractionResult.FAIL;
        mech.loadState(tag); mech.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), 0);
        if(stack.has(DataComponents.CUSTOM_NAME)) mech.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        AABB box = mech.getBoundingBox();
        if(!level.getWorldBorder().isWithinBounds(box) || !level.noCollision(mech, box)
                || !level.getEntities(mech, box, entity -> !entity.isSpectator()).isEmpty()) return InteractionResult.FAIL;
        for(BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1e-6, box.maxY - 1e-6, box.maxZ - 1e-6)))
            if(!level.hasChunkAt(cell) || !level.getFluidState(cell).isEmpty()) return InteractionResult.FAIL;
        // Every column under the chassis must provide a full upper supporting face.
        for(int x = (int)Math.floor(box.minX); x <= (int)Math.floor(box.maxX - 1e-6); x++)
            for(int z = (int)Math.floor(box.minZ); z <= (int)Math.floor(box.maxZ - 1e-6); z++) {
                BlockPos floor = new BlockPos(x, pos.getY() - 1, z);
                if(!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) || !level.getFluidState(floor).isEmpty()) return InteractionResult.FAIL;
            }
        if(!level.addFreshEntity(mech)) return InteractionResult.FAIL;
        stack.shrink(1); return InteractionResult.CONSUME;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if(toggleIsolation(player, hand, stack)) return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        if(!player.isShiftKeyDown() || !BestMechEntity.qualified(player) || !energy(stack).canReceive()) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand); return InteractionResultHolder.consume(stack);
    }
    private static boolean toggleIsolation(Player player, InteractionHand hand, ItemStack stack) {
        if(player.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND || !BestMechEntity.qualified(player)
                || !player.getOffhandItem().is(CatalogueContent.item("best_diagnostic_tool"))) return false;
        if(!player.level().isClientSide) {
            CompoundTag tag = state(stack); tag.putBoolean("Isolated", !tag.getBoolean("Isolated")); setState(stack, tag);
            player.displayClientMessage(Component.literal("机甲电源：" + (tag.getBoolean("Isolated") ? "已隔离" : "已接通") + " · " + energy(stack).getEnergyStored() + " XH"), true);
        }
        return true;
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if(level.isClientSide || !(user instanceof Player player)) return;
        if(!player.isShiftKeyDown() || !BestMechEntity.qualified(player) || !energy(stack).canReceive()) { player.stopUsingItem(); return; }
        Vec3 from = player.getEyePosition(); double reach = Math.min(5, player.blockInteractionRange());
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(player.getLookAngle().scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if(hit.getType() != HitResult.Type.BLOCK || !level.hasChunkAt(hit.getBlockPos()) || !level.mayInteract(player, hit.getBlockPos())) return;
        IEnergyStorage source = level.getCapability(Capabilities.EnergyStorage.BLOCK, hit.getBlockPos(), hit.getDirection());
        if(source == null || !source.canExtract()) return;
        // Air-use can begin before aiming at a block; authorize the actual source before every transfer.
        var interaction = net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(player, player.getUsedItemHand(), hit.getBlockPos(), hit);
        if(interaction.isCanceled() || interaction.getUseItem().isFalse()) { player.stopUsingItem(); return; }
        IEnergyStorage target = energy(stack); int n = target.receiveEnergy(source.extractEnergy(5000, true), true);
        if(n > 0) target.receiveEnergy(source.extractEnergy(n, false), false);
        if(player.tickCount % 20 == 0) player.displayClientMessage(Component.literal("机甲蓄能 " + target.getEnergyStored() + "/" + BestMechEntity.CAPACITY + " XH"), true);
    }
    @Override public boolean isBarVisible(ItemStack stack) { return energy(stack).getEnergyStored() > 0; }
    @Override public int getBarWidth(ItemStack stack) { return (int)((long)energy(stack).getEnergyStored() * 13 / BestMechEntity.CAPACITY); }
    @Override public int getBarColor(ItemStack stack) { return 0x49bca3; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var tag = state(stack); lines.add(Component.literal(energy(stack).getEnergyStored() + " / " + BestMechEntity.CAPACITY + " XH · 热 " + tag.getInt("Heat") + " · 磨损 " + tag.getInt("Wear")));
        lines.add(Component.literal("结构 " + (tag.contains("Health") ? tag.getFloat("Health") : 100) + "/100 · " + (tag.getBoolean("Isolated") ? "已隔离" : "已接通")));
        lines.add(Component.literal("潜行按住右键对准基地储能方块：蓄能 ≤5000 XH/t；普通右键地面部署"));
        lines.add(Component.literal("副手诊断器，主手机甲普通右键空气：切换隔离/接通"));
        lines.add(BestMechModules.installed(tag.getString(BestMechModules.STATE)));
        lines.add(BestMechModules.controls());
    }
}
