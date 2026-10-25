package cn.starfall.blueprint;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class BlueprintNetworking {
    public record Import(int menuId,String json) implements CustomPacketPayload {
        public static final Type<Import> TYPE=new Type<>(StarfallBlueprint.id("import_construction_plan"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Import> CODEC=StreamCodec.of((buf,value)->{
            if(value.json.length()>ConstructionPlan.MAX_TEXT)throw new IllegalArgumentException("Blueprint too large");
            buf.writeVarInt(value.menuId);buf.writeByteArray(compress(value.json));
        },buf->{int menu=buf.readVarInt();return new Import(menu,decompress(buf.readByteArray(30000)));});
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private static byte[] compress(String text){
        try{var output=new java.io.ByteArrayOutputStream();try(var gzip=new java.util.zip.GZIPOutputStream(output)){gzip.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            byte[] bytes=output.toByteArray();if(bytes.length>30000)throw new IllegalArgumentException("Compressed blueprint too large");return bytes;
        }catch(java.io.IOException e){throw new IllegalArgumentException("Unable to encode blueprint",e);}
    }
    private static String decompress(byte[] bytes){
        try(var gzip=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes))){
            byte[] decoded=gzip.readNBytes(ConstructionPlan.MAX_TEXT*4+1);if(decoded.length>ConstructionPlan.MAX_TEXT*4)throw new IllegalArgumentException("Blueprint expands beyond limit");
            String text=new String(decoded,java.nio.charset.StandardCharsets.UTF_8);if(text.length()>ConstructionPlan.MAX_TEXT)throw new IllegalArgumentException("Blueprint too large");return text;
        }catch(java.io.IOException e){throw new IllegalArgumentException("Invalid compressed blueprint",e);}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("1").playToServer(Import.TYPE,Import.CODEC,(payload,context)->context.enqueueWork(()->{
            var player=context.player();
            if(player.containerMenu instanceof BlueprintMenu menu&&menu.containerId==payload.menuId&&menu.stillValid(player))menu.importPlan(player,payload.json);
        }));
    }
}
