package cn.starfall.blueprint;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Requests carry only controls; the server owns equipment, targets, power and cooldowns. */
public final class BlackDogNetworking {
    public record Control(int action) implements CustomPacketPayload {
        public static final Type<Control> TYPE=new Type<>(StarfallBlueprint.id("blackdog_control"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Control> CODEC=StreamCodec.of((b,p)->b.writeVarInt(p.action),b->new Control(b.readVarInt()));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Flight(boolean flying,boolean cruise,boolean takeoff) implements CustomPacketPayload {
        public static final Type<Flight> TYPE=new Type<>(StarfallBlueprint.id("blackdog_flight"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Flight> CODEC=StreamCodec.of((b,p)->{b.writeBoolean(p.flying);b.writeBoolean(p.cruise);b.writeBoolean(p.takeoff);},b->new Flight(b.readBoolean(),b.readBoolean(),b.readBoolean()));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("1");
        registrar.playToServer(Control.TYPE,Control.CODEC,(p,c)->c.enqueueWork(()->BlackDogArmor.selectSystem(c.player(),p.action)));
        registrar.playToServer(Flight.TYPE,Flight.CODEC,(p,c)->c.enqueueWork(()->BlackDogArmor.flightRequest(c.player(),p.flying,p.cruise,p.takeoff)));
    }
    private BlackDogNetworking(){}
}
