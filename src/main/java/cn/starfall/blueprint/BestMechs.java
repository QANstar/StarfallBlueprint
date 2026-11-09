package cn.starfall.blueprint;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity types are deliberately separate from the block entity registry. */
public final class BestMechs {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, StarfallBlueprint.ID);
    public static final DeferredHolder<EntityType<?>, EntityType<BestMechEntity>> TYPE = TYPES.register("best_engineering_mech",
            () -> EntityType.Builder.of(BestMechEntity::new, MobCategory.MISC).sized(1.8f, 3.2f).passengerAttachments(2.2f)
                    .clientTrackingRange(10).updateInterval(1).build(StarfallBlueprint.id("best_engineering_mech").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<BestMechEntity>> HEAVY_TYPE = TYPES.register("best_heavy_engineering_mech",
            () -> EntityType.Builder.of(BestMechEntity::new, MobCategory.MISC).sized(2.6f, 4.4f).passengerAttachments(3.1f)
                    .clientTrackingRange(12).updateInterval(1).build(StarfallBlueprint.id("best_heavy_engineering_mech").toString()));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<BestMechCargoMenu>> CARGO_MENU = StarfallBlueprint.MENUS.register("best_mech_cargo",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(BestMechCargoMenu::new));
    private static final String LOGOUT_SLOW_FALL = "BestMechLogoutSlowFall";
    public static void initialize() {}
    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(BestMechs::attributes);
        bus.addListener(BestMechNetworking::register);
        NeoForge.EVENT_BUS.addListener(BestMechs::playerLogout);
        NeoForge.EVENT_BUS.addListener(BestMechs::playerLogin);
    }
    public static void playerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || !(player.getVehicle() instanceof BestMechEntity)) return;
        var position = player.position(); var level = player.level(); var pose = player.getPose();
        float yaw = player.getYRot(), pitch = player.getXRot();
        player.stopRiding();
        if(player.getVehicle() != null || player.level() != level) return;
        player.moveTo(position.x, position.y, position.z);
        player.setYRot(yaw); player.setXRot(pitch); player.setPose(pose);
        player.getPersistentData().putBoolean(LOGOUT_SLOW_FALL, true);
    }
    public static void playerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || !player.getPersistentData().getBoolean(LOGOUT_SLOW_FALL)) return;
        player.getPersistentData().remove(LOGOUT_SLOW_FALL);
        if(!player.hasEffect(MobEffects.SLOW_FALLING))
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(TYPE.get(), BestMechEntity.attributes().build());
        event.put(HEAVY_TYPE.get(), BestMechEntity.attributes().add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,300).add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,.26).build());
    }
    private BestMechs() {}
}
