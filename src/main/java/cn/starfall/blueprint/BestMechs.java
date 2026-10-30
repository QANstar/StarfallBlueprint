package cn.starfall.blueprint;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity types are deliberately separate from the block entity registry. */
public final class BestMechs {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, StarfallBlueprint.ID);
    public static final DeferredHolder<EntityType<?>, EntityType<BestMechEntity>> TYPE = TYPES.register("best_engineering_mech",
            () -> EntityType.Builder.of(BestMechEntity::new, MobCategory.MISC).sized(1.8f, 3.2f).passengerAttachments(2.2f)
                    .clientTrackingRange(10).updateInterval(1).build(StarfallBlueprint.id("best_engineering_mech").toString()));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<BestMechCargoMenu>> CARGO_MENU = StarfallBlueprint.MENUS.register("best_mech_cargo",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(BestMechCargoMenu::new));
    public static void initialize() {}
    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(BestMechs::attributes);
        bus.addListener(BestMechNetworking::register);
    }
    private static void attributes(EntityAttributeCreationEvent event) { event.put(TYPE.get(), BestMechEntity.attributes().build()); }
    private BestMechs() {}
}
