package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

public record Source(String id, String name, Optional<String> url, String licence) {
    public static final Codec<Source> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("id").forGetter(Source::id),
                    Codec.STRING.fieldOf("name").forGetter(Source::name),
                    Codec.STRING.optionalFieldOf("url").forGetter(Source::url),
                    Codec.STRING.fieldOf("licence").forGetter(Source::licence))
            .apply(i, Source::new));
}
