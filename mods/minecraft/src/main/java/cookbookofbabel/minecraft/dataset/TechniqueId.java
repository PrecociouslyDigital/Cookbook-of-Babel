package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;

public record TechniqueId(String value) {
    public static final Codec<TechniqueId> CODEC = Codec.STRING.xmap(TechniqueId::new, TechniqueId::value);

    @Override
    public String toString() {
        return value;
    }
}
