package cn.starfall.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Integer millimass ledger; one crystal item is 10 M. Never round recovered mass upward. */
public record MaterialBatch(String material, int dryMilliM, int phaseMilliM, int integrity, int contamination, String form, String route) {
    public static final Codec<MaterialBatch> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("material").forGetter(MaterialBatch::material),
            Codec.intRange(1, 1_000_000).fieldOf("dry_milli_m").forGetter(MaterialBatch::dryMilliM),
            Codec.intRange(0, 1_000_000).fieldOf("phase_milli_m").forGetter(MaterialBatch::phaseMilliM),
            Codec.intRange(0, 100).fieldOf("integrity").forGetter(MaterialBatch::integrity),
            Codec.intRange(0, 3).fieldOf("contamination").forGetter(MaterialBatch::contamination),
            Codec.STRING.fieldOf("form").forGetter(MaterialBatch::form),
            Codec.STRING.fieldOf("route").forGetter(MaterialBatch::route)
    ).apply(i, MaterialBatch::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialBatch> STREAM_CODEC = new StreamCodec<>() {
        public MaterialBatch decode(RegistryFriendlyByteBuf b) { return new MaterialBatch(b.readUtf(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(), b.readUtf()); }
        public void encode(RegistryFriendlyByteBuf b, MaterialBatch m) { b.writeUtf(m.material); b.writeVarInt(m.dryMilliM); b.writeVarInt(m.phaseMilliM); b.writeVarInt(m.integrity); b.writeVarInt(m.contamination); b.writeUtf(m.form); b.writeUtf(m.route); }
    };
    public static MaterialBatch ore(int grade, int integrity) { return new MaterialBatch("meteor_phase", 1_000_000, grade * 10_000, integrity, 0, "raw", "common"); }
    public static MaterialBatch crystal(int integrity) { return new MaterialBatch("meteor_phase", 10_000, 9_000, integrity, 0, "granules", "common"); }
    public static MaterialBatch tailings(int grade) {
        int pieces = grade * 5 / 6;
        return new MaterialBatch("meteor_tailings", 1_000_000 - pieces * 10_000, grade * 10_000 - pieces * 9_000, 0, 0, "wet_tailings", "common");
    }
    public boolean isProcessableOre() {
        return material.equals("meteor_phase") && dryMilliM == 1_000_000 && (phaseMilliM == 60_000 || phaseMilliM == 120_000 || phaseMilliM == 240_000)
                && integrity >= 22 && contamination == 0 && form.equals("raw") && route.equals("common");
    }
    public boolean isQ0() { return material.equals("meteor_phase") && dryMilliM == 10_000 && phaseMilliM >= 9_000 && phaseMilliM <= dryMilliM && integrity >= 20 && contamination == 0 && route.equals("common") && form.equals("granules"); }
    public int crystalCount() { return phaseMilliM / 12_000; }
    public String sourceId() { return "meteor_phase"; }
}
