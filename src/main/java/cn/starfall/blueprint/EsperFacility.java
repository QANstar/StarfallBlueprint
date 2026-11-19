package cn.starfall.blueprint;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.energy.EnergyStorage;

public final class EsperFacility extends BaseEntityBlock {
    static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private final int kind;

    EsperFacility(int kind, Properties props) {
        super(props);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(PART, 0));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(PART); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    static BlockPos offset(int part) {
        int index = Math.max(0, part - 1);
        return new BlockPos(index & 1, (index >> 2) & 1, (index >> 1) & 1);
    }
    static Controller controller(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof EsperFacility)) return null;
        BlockPos root = pos.subtract(offset(state.getValue(PART)));
        return level.hasChunkAt(root) && level.getBlockEntity(root) instanceof Controller machine
                && machine.getBlockState().is(state.getBlock()) && machine.getBlockState().getValue(PART) <= 1
                ? machine : null;
    }
    static boolean complete(Controller machine) {
        Level level = machine.getLevel();
        if (level == null) return false;
        BlockState state = machine.getBlockState();
        if (state.getValue(PART) == 0) return true;
        for (int part = 1; part <= 8; part++) {
            BlockPos cell = machine.getBlockPos().offset(offset(part));
            if (!level.hasChunkAt(cell) || level.getBlockState(cell) != state.setValue(PART, part)) return false;
        }
        return true;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (kind == 2) return defaultBlockState();
        var level = context.getLevel();
        for (int part = 1; part <= 8; part++) {
            var cell = context.getClickedPos().offset(offset(part));
            if (!level.hasChunkAt(cell) || level.isOutsideBuildHeight(cell) || !level.getWorldBorder().isWithinBounds(cell)
                    || !level.getBlockState(cell).canBeReplaced(context)) return null;
            if (context.getPlayer() != null && (!level.mayInteract(context.getPlayer(), cell)
                    || !context.getPlayer().mayUseItemAt(cell, context.getClickedFace(), context.getItemInHand()))) return null;
        }
        return defaultBlockState().setValue(PART, 1);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity user, ItemStack stack) {
        if (!level.isClientSide && state.getValue(PART) == 1)
            for (int part = 2; part <= 8; part++) level.setBlock(pos.offset(offset(part)), state.setValue(PART, part), 3);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return kind != 2 ? new Controller(pos, state) : null;
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (kind == 1 && state.getValue(PART) <= 1 && !old.is(this) && level instanceof ServerLevel server)
            EsperDomain.Ledger.get(server).unit(server, pos, true);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!next.is(this) && level instanceof ServerLevel server) {
            int part = state.getValue(PART);
            if (kind == 1 && part <= 1) EsperDomain.Ledger.get(server).unit(server, pos, false);
            if (part > 0 && !REMOVING.get()) {
                REMOVING.set(true);
                try {
                    var root = pos.subtract(offset(part));
                    for (int i = 1; i <= 8; i++) {
                        var cell = root.offset(offset(i));
                        if (i != part && level.hasChunkAt(cell) && level.getBlockState(cell) == state.setValue(PART, i))
                            level.removeBlock(cell, false);
                    }
                } finally { REMOVING.remove(); }
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer p && !p.isSpectator()) {
            var machine = controller(level, pos);
            if (kind == 0 && machine != null && complete(machine)) {
                p.openMenu(new SimpleMenuProvider((id, inv, who) ->
                        new EsperMenu(id, inv, ContainerLevelAccess.create(level, pos)), getName()));
            } else if (kind == 1) {
                p.displayClientMessage(Esper.label("local", EsperDomain.Ledger.get(p.serverLevel()).local(p)), true);
            } else if (kind == 2) p.displayClientMessage(Esper.label("launcher_hint"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (kind != 0 || !stack.is(Esper.RETUNER.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (player instanceof ServerPlayer p && !Esper.retune(p, pos, stack))
            p.displayClientMessage(Esper.label("retune_failed"), true);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    static final class Controller extends BlockEntity {
        final Buffer energy = new Buffer();
        Controller(BlockPos pos, BlockState state) { super(Esper.CONTROLLER.get(), pos, state); }
        boolean spend(int amount) { return energy.spend(amount); }
        @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
            super.saveAdditional(tag, lookup);
            tag.putInt("Energy", energy.getEnergyStored());
        }
        @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
            super.loadAdditional(tag, lookup);
            energy.load(tag.getInt("Energy"));
        }
        final class Buffer extends EnergyStorage {
            Buffer() { super(2_000_000, 8192, 0); }
            @Override public int receiveEnergy(int amount, boolean simulate) {
                int accepted = super.receiveEnergy(amount, simulate);
                if (accepted > 0 && !simulate) setChanged();
                return accepted;
            }
            void load(int stored) { energy = Math.clamp(stored, 0, capacity); }
            boolean spend(int amount) {
                if (energy < amount) return false;
                energy -= amount;
                setChanged();
                return true;
            }
        }
    }
}
