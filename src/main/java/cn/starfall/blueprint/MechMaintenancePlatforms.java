package cn.starfall.blueprint;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** All maintenance platform registrations live here. */
public final class MechMaintenancePlatforms {
    private MechMaintenancePlatforms() {}
    public static final DeferredBlock<MechMaintenancePlatformBlock> BLOCK = StarfallBlueprint.BLOCKS.register("mech_maintenance_platform", () -> new MechMaintenancePlatformBlock(BlockBehaviour.Properties.of().strength(4f).noOcclusion()));
    public static final DeferredItem<MechMaintenancePlatformItem> ITEM = StarfallBlueprint.ITEMS.registerItem("mech_maintenance_platform", p -> new MechMaintenancePlatformItem(BLOCK.get(), p));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MechMaintenancePlatformBlockEntity>> ENTITY = StarfallBlueprint.ENTITIES.register("mech_maintenance_platform", () -> BlockEntityType.Builder.of(MechMaintenancePlatformBlockEntity::new, BLOCK.get()).build(null));
    public static void initialize() {}
}
