package cn.starfall.blueprint;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Requests carry only controls; the server owns equipment, targets, power and cooldowns. */
public final class BlackDogNetworking {
    /** Private targeting feedback, sent exclusively to the player operating the terminal. */
    public record TargetLock(int entityId,java.util.UUID target,int ticks) implements CustomPacketPayload {
        public static final Type<TargetLock> TYPE=new Type<>(StarfallBlueprint.id("blackdog_target_lock"));
        public static final StreamCodec<RegistryFriendlyByteBuf,TargetLock> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.entityId);b.writeUUID(p.target);b.writeVarInt(p.ticks);},b->new TargetLock(b.readVarInt(),b.readUUID(),b.readVarInt()));
        public static TargetLock empty(){return new TargetLock(-1,new java.util.UUID(0,0),0);}
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
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
        registrar.playToClient(TargetLock.TYPE,TargetLock.CODEC,(p,c)->c.enqueueWork(()->cn.starfall.blueprint.client.BlackDogLockMarker.receive(p)));
        registrar.playToServer(Control.TYPE,Control.CODEC,(p,c)->c.enqueueWork(()->BlackDogArmor.selectSystem(c.player(),p.action)));
        registrar.playToServer(Flight.TYPE,Flight.CODEC,(p,c)->c.enqueueWork(()->BlackDogArmor.flightRequest(c.player(),p.flying,p.cruise,p.takeoff)));
    }
    private BlackDogNetworking(){}
}
