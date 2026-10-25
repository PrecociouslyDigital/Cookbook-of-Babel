package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;

public record IngredientId(String value) implements Comparable<IngredientId> {
    public static final Codec<IngredientId> CODEC = Codec.STRING.xmap(IngredientId::new, IngredientId::value);

    @Override
    public int compareTo(IngredientId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
