package cn.starfall.blueprint;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class BestMechNetworking {
    /** No entity id or target position is accepted from the client. */
    public record Action(int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(StarfallBlueprint.id("best_mech_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeVarInt(value.action), buf -> new Action(buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Action.TYPE, Action.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (payload.action == 0 && context.player().getVehicle() instanceof BestMechEntity mech)
                mech.drill(context.player());
        }));
    }
    private BestMechNetworking() {}
}
