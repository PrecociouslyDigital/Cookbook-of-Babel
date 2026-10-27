package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import cookbookofbabel.minecraft.CookbookOfBabel;
import net.minecraft.resources.ResourceLocation;

public record IngredientId(String value) implements Comparable<IngredientId> {
    public static final Codec<IngredientId> CODEC = Codec.STRING.xmap(IngredientId::new, IngredientId::value);

    /** What the mod names the ingredient's substance or named dish by. */
    public ResourceLocation location() {
        return CookbookOfBabel.id(value);
    }

    @Override
    public int compareTo(IngredientId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
