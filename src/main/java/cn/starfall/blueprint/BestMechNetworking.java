package cn.starfall.blueprint;

import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class BestMechNetworking {
    /** No entity id or target position is accepted from the client. */
    public record Action(int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(StarfallBlueprint.id("best_mech_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeVarInt(value.action), buf -> new Action(buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** Continuous piloting input state (jump, sprint, primary/secondary hold). Server validates authority. */
    public record PilotInput(int bits) implements CustomPacketPayload {
        public static final Type<PilotInput> TYPE = new Type<>(StarfallBlueprint.id("best_mech_pilot_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PilotInput> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeVarInt(value.bits), buf -> new PilotInput(buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(PilotInput.TYPE, PilotInput.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer server && server.getVehicle() instanceof BestMechEntity mech) {
                mech.setPilotInput(server, payload.bits());
            }
        }));
        event.registrar("1").playToServer(Action.TYPE, Action.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player().getVehicle() instanceof BestMechEntity mech) {
                if (payload.action == 0) mech.work(context.player());
                else if (payload.action == 1) mech.toggleSupport(context.player());
                else if (payload.action == 2) mech.fireWeapon(context.player());
                else if (payload.action == 7) BestMechLifting.toggle(mech, context.player());
                else if (payload.action == 8) BestMechLoadoutMenu.open(context.player(), mech);
                else if (payload.action == 3 && mech.toggleCollectionMode(context.player()))
                    context.player().displayClientMessage(Component.translatable(mech.silkCollection()
                            ? "message.starfall_blueprint.mech_mining_silk" : "message.starfall_blueprint.mech_mining_fortune"), true);
                else if (payload.action >= 4 && payload.action <= 6 && !mech.level().isClientSide
                        && mech.isAlive() && mech.getControllingPassenger() == context.player() && mech.qualifiedFor(context.player())) {
                    boolean enabled;
                    String key;
                    if (payload.action == 4 && mech.hasUpgrade(BestMechProgression.TIANPU)) {
                        enabled = !mech.combatSystems().friendlyFire();
                        mech.combatSystems().setFriendlyFire(enabled);
                        key = "message.starfall_blueprint.mech_friendly_fire";
                    } else if (payload.action == 5 && mech.hasUpgrade(BestMechProgression.TIANPU)) {
                        enabled = !mech.combatSystems().terrainDamage();
                        mech.combatSystems().setTerrainDamage(enabled);
                        key = "message.starfall_blueprint.mech_terrain_damage";
                    } else if (payload.action == 6 && mech.hasUpgrade(BestMechProgression.CHANGCHUAN)) {
                        enabled = !BestMechSupport.isChangchuanEnabled(mech);
                        BestMechSupport.setChangchuanEnabled(mech, enabled);
                        key = "message.starfall_blueprint.mech_changchuan_support";
                    } else return;
                    context.player().displayClientMessage(Component.translatable(key, Component.translatable(enabled
                            ? "message.starfall_blueprint.mech_toggle_on" : "message.starfall_blueprint.mech_toggle_off")), true);
                }
            }
        }));
    }
    private BestMechNetworking() {}
}
