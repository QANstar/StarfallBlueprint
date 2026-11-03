package cn.starfall.blueprint;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Opening accepts no world position, costs, knowledge or learning action from the client. */
public final class ProgressionNetworking {
    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE=new Type<>(StarfallBlueprint.id("progression_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Open> CODEC=StreamCodec.of((buf,value)->{},buf->new Open());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Open.TYPE,Open.CODEC,(payload,context)->
                context.enqueueWork(()->Progression.open(context.player())));
    }
    private ProgressionNetworking() {}
}
