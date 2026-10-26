package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/** When a step is done. {@link ByBoth}: reach the core temperature, then hold it for the duration. */
public sealed interface End {
    record ByCoreTemp(double coreTempC) implements End {}

    record ByTime(Amount duration, Optional<Sensitivity> sensitivity) implements End {}

    record ByBoth(double coreTempC, Amount duration) implements End {}

    /** How long the step takes, where the dataset says. */
    default Optional<Amount> timeTaken() {
        if (this instanceof ByTime t) {
            return Optional.of(t.duration());
        }
        return this instanceof ByBoth b ? Optional.of(b.duration()) : Optional.empty();
    }

    Codec<ByCoreTemp> BY_CORE_TEMP = RecordCodecBuilder.create(i -> i.group(
                    Codec.DOUBLE.fieldOf("coreTempC").forGetter(ByCoreTemp::coreTempC))
            .apply(i, ByCoreTemp::new));

    Codec<ByTime> BY_TIME = RecordCodecBuilder.create(i -> i.group(
                    Amount.CODEC.fieldOf("duration").forGetter(ByTime::duration),
                    Sensitivity.CODEC.optionalFieldOf("sensitivity").forGetter(ByTime::sensitivity))
            .apply(i, ByTime::new));

    Codec<ByBoth> BY_BOTH = RecordCodecBuilder.create(i -> i.group(
                    Codec.DOUBLE.fieldOf("coreTempC").forGetter(ByBoth::coreTempC),
                    Amount.CODEC.fieldOf("duration").forGetter(ByBoth::duration))
            .apply(i, ByBoth::new));

    Codec<End> CODEC = Codec.STRING.dispatch("by", End::tag, End::codecFor);

    private static String tag(End end) {
        if (end instanceof ByCoreTemp) {
            return "coreTemp";
        }
        return end instanceof ByTime ? "time" : "both";
    }

    @SuppressWarnings("unchecked")
    private static Codec<? extends End> codecFor(String tag) {
        return switch (tag) {
            case "coreTemp" -> BY_CORE_TEMP;
            case "time" -> BY_TIME;
            case "both" -> BY_BOTH;
            default -> Codec.unit((End) null).flatXmap(e -> DataResult.error(() -> "unknown end " + tag), DataResult::success);
        };
    }
}
