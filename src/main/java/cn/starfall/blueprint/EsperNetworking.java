package cn.starfall.blueprint;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class EsperNetworking {
    public record Slot(int skill, int rank, int ticks) {}

    public record Hud(boolean enabled, int school, int power, int capacity, int charge, List<Slot> slots)
            implements CustomPacketPayload {
        static final Type<Hud> TYPE = new Type<>(StarfallBlueprint.id("esper_hud"));
        static final StreamCodec<RegistryFriendlyByteBuf, Hud> CODEC = StreamCodec.of((buf, msg) -> {
            buf.writeBoolean(msg.enabled).writeByte(msg.school);
            buf.writeVarInt(msg.power).writeVarInt(msg.capacity).writeVarInt(msg.charge);
            for (Slot slot : msg.slots) buf.writeByte(slot.skill).writeByte(slot.rank).writeShort(slot.ticks);
        }, buf -> {
            boolean enabled = buf.readBoolean();
            int school = buf.readByte(), power = buf.readVarInt(), capacity = buf.readVarInt(), charge = buf.readVarInt();
            List<Slot> slots = new ArrayList<>(Esper.SLOTS);
            for (int i = 0; i < Esper.SLOTS; i++) slots.add(new Slot(buf.readByte(), buf.readUnsignedByte(), buf.readUnsignedShort()));
            return new Hud(enabled, school, power, capacity, charge, List.copyOf(slots));
        });
        @Override public Type<Hud> type() { return TYPE; }
    }

    public enum Pattern { RIFT, SLICE, SHATTER, PORTAL, GUARD, BIND, WAKE, COLLAPSE, BUBBLE_IMPACT }

    public record Fracture(Pattern pattern, Vec3 center, Vec3 normal, float radius, int life, long seed)
            implements CustomPacketPayload {
        static final Type<Fracture> TYPE = new Type<>(StarfallBlueprint.id("esper_fracture"));
        static final StreamCodec<RegistryFriendlyByteBuf, Fracture> CODEC = StreamCodec.of((buf, msg) -> {
            buf.writeEnum(msg.pattern);
            writeVec(buf, msg.center);
            writeVec(buf, msg.normal);
            buf.writeFloat(msg.radius).writeByte(msg.life).writeLong(msg.seed);
        }, buf -> new Fracture(buf.readEnum(Pattern.class), readVec(buf), readVec(buf),
                buf.readFloat(), buf.readUnsignedByte(), buf.readLong()));
        @Override public Type<Fracture> type() { return TYPE; }
    }

    public record Aura(int entity, int kind, int amount, int life) implements CustomPacketPayload {
        private static final Type<Aura> TYPE = new Type<>(StarfallBlueprint.id("esper_aura"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Aura> CODEC = StreamCodec.of(
                (buf, msg) -> buf.writeVarInt(msg.entity).writeByte(msg.kind).writeByte(msg.amount).writeByte(msg.life),
                buf -> new Aura(buf.readVarInt(), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte()));
        public static final int GUARD = 0, VOLLEY = 1;
        @Override public Type<Aura> type() { return TYPE; }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        var registry = event.registrar("1");
        registry.playToServer(Esper.Cast.TYPE, Esper.Cast.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer p)) return;
            if (msg.slot() == Esper.Cast.TOGGLE) Esper.toggleMode(p);
            else if (msg.slot() == Esper.Cast.CANCEL_CHARGE) EsperVolley.cancel(p);
            else if (msg.slot() >= Esper.Cast.RELEASE && msg.slot() < Esper.Cast.RELEASE + Esper.SLOTS) {
                long now = p.serverLevel().getServer().overworld().getGameTime();
                if (EsperVolley.release(p, msg.slot() - Esper.Cast.RELEASE, now)) sync(p, Esper.data(p), now);
            }
            else if (Esper.cast(p, msg.slot())) sync(p, Esper.data(p), p.serverLevel().getServer().overworld().getGameTime());
        }));
        registry.playToClient(Hud.TYPE, Hud.CODEC, (msg, ctx) -> ctx.enqueueWork(() ->
                cn.starfall.blueprint.client.EsperClient.receive(msg)));
        registry.playToClient(Fracture.TYPE, Fracture.CODEC, (msg, ctx) -> ctx.enqueueWork(() ->
                cn.starfall.blueprint.client.EsperFractureRenderer.receive(msg)));
        registry.playToClient(Aura.TYPE, Aura.CODEC, (msg, ctx) -> ctx.enqueueWork(() ->
                cn.starfall.blueprint.client.EsperFractureRenderer.receiveAura(msg)));
    }

    static void sync(ServerPlayer p, CompoundTag data, long now) {
        var school = Esper.school(data);
        List<Slot> slots = new ArrayList<>(Esper.SLOTS);
        for (int i = 0; i < Esper.SLOTS; i++) {
            int index = Esper.slot(data, i);
            if (index < 0 || index >= Esper.SKILLS.length) slots.add(new Slot(-1, 0, 0));
            else {
                var skill = Esper.SKILLS[index];
                slots.add(new Slot(index, Esper.level(data, skill),
                        (int) Math.clamp(data.getLong("Ready_" + skill.key) - now, 0, 65535)));
            }
        }
        PacketDistributor.sendToPlayer(p, new Hud(data.getBoolean("Mode"), school == null ? -1 : school.ordinal(),
                data.getInt("Power"), data.getInt("Max"), data.getInt("Charge"), List.copyOf(slots)));
    }

    static void fracture(ServerLevel level, Pattern pattern, Vec3 center, Vec3 normal, float size, int life) {
        PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, 72,
                new Fracture(pattern, center, normal, size, life, level.random.nextLong()));
    }
    static void aura(ServerPlayer player, int kind, int count, int life) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new Aura(player.getId(), kind, count, life));
    }

    private static void writeVec(RegistryFriendlyByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x).writeDouble(vec.y).writeDouble(vec.z);
    }
    private static Vec3 readVec(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
    private EsperNetworking() {}
}
