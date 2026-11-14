package cn.starfall.blueprint;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class OrbitalNetworking {
    public record Flight(int bits) implements CustomPacketPayload {
        public static final Type<Flight> TYPE=new Type<>(StarfallBlueprint.id("hj_flight"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Flight> CODEC=StreamCodec.of((b,v)->b.writeVarInt(v.bits),b->new Flight(b.readVarInt()));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Weapon(int selection) implements CustomPacketPayload {
        public static final Type<Weapon> TYPE=new Type<>(StarfallBlueprint.id("hj_weapon"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Weapon> CODEC=StreamCodec.of((b,v)->b.writeVarInt(v.selection),b->new Weapon(b.readVarInt()));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToServer(Flight.TYPE,Flight.CODEC,(v,c)->c.enqueueWork(()->{
        if(!(c.player() instanceof ServerPlayer p)||!p.isAlive()||p.isSpectator())return;
        if(v.bits()==1024){
            if(p.getVehicle() instanceof PeregrineEntity f){f.toggleSeat(p);return;}
            var candidates=p.level().getEntitiesOfClass(PeregrineEntity.class,p.getBoundingBox().inflate(5));candidates.sort(java.util.Comparator.comparingDouble(p::distanceToSqr));
            for(var f:candidates)if(Haijia.knows(p,"flight")&&p.hasLineOfSight(f)){f.toggleSeat(p);break;}
        }else if(p.getVehicle() instanceof PeregrineEntity f)f.controls(p,v.bits());
    }));
        e.registrar("1").playToServer(Weapon.TYPE,Weapon.CODEC,(v,c)->c.enqueueWork(()->{if(c.player().getVehicle() instanceof PeregrineEntity f)f.selectWeapon(c.player(),v.selection());}));
    }
}
