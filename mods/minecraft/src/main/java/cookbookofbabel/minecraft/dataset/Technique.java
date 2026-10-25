package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;

public record Technique(TechniqueId id, List<TechniqueId> parents, Optional<TempRange> typicalTemp) {
    public record TempRange(double minC, double maxC) {
        static final Codec<TempRange> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.DOUBLE.fieldOf("minC").forGetter(TempRange::minC),
                        Codec.DOUBLE.fieldOf("maxC").forGetter(TempRange::maxC))
                .apply(i, TempRange::new));

        public double midpoint() {
            return (minC + maxC) / 2;
        }
    }

    public static final Codec<Technique> CODEC = RecordCodecBuilder.create(i -> i.group(
                    TechniqueId.CODEC.fieldOf("id").forGetter(Technique::id),
                    TechniqueId.CODEC.listOf().optionalFieldOf("parents", List.of()).forGetter(Technique::parents),
                    TempRange.CODEC.optionalFieldOf("typicalTemp").forGetter(Technique::typicalTemp))
            .apply(i, Technique::new));
}
