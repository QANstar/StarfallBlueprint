package cn.starfall.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Manufacturing specifications are independent of counted-item quantities. */
public record MaterialQuality(int purity, int integrity, int orientation, int contamination, boolean continuous, String route) {
    public static final Codec<MaterialQuality> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.intRange(0,100).fieldOf("purity").forGetter(MaterialQuality::purity),
        Codec.intRange(0,100).fieldOf("integrity").forGetter(MaterialQuality::integrity),
        Codec.intRange(0,100).fieldOf("orientation").forGetter(MaterialQuality::orientation),
        Codec.intRange(0,3).fieldOf("contamination").forGetter(MaterialQuality::contamination),
        Codec.BOOL.fieldOf("continuous").forGetter(MaterialQuality::continuous),Codec.STRING.fieldOf("route").forGetter(MaterialQuality::route)
    ).apply(i,MaterialQuality::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,MaterialQuality> STREAM=StreamCodec.of((b,q)->{
        b.writeVarInt(q.purity);b.writeVarInt(q.integrity);b.writeVarInt(q.orientation);b.writeVarInt(q.contamination);b.writeBoolean(q.continuous);b.writeUtf(q.route);
    },b->new MaterialQuality(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readUtf()));
    public static MaterialQuality of(ItemStack stack) {
        var q=stack.get(CatalogueContent.QUALITY.get());if(q!=null)return q;
        MaterialBatch b=stack.get(StarfallBlueprint.BATCH.get());
        return b==null?new MaterialQuality(100,100,0,0,false,"ordinary")
            :new MaterialQuality(b.phaseMilliM()*100/b.dryMilliM(),b.integrity(),0,b.contamination(),false,b.route());
    }
}
