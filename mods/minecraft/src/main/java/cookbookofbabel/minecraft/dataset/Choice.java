package cookbookofbabel.minecraft.dataset;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;

/** One choice of a recipe's inputs: a nested selection, or one ingredient. */
public sealed interface Choice {
    /**
     * Pick between {@code min} and {@code max} of the choices; both default to all of them, so a
     * bare selection means "all of these".
     */
    record Select(Optional<Integer> min, Optional<Integer> max, List<Choice> of) implements Choice {
        public int least() {
            return min.orElse(of.size());
        }

        public int most() {
            return max.orElse(of.size());
        }
    }

    record Leaf(IngredientId ingredient, Amount amount) implements Choice {}

    Codec<Leaf> LEAF = RecordCodecBuilder.create(i -> i.group(
                    IngredientId.CODEC.fieldOf("ingredient").forGetter(Leaf::ingredient),
                    Amount.CODEC.fieldOf("amount").forGetter(Leaf::amount))
            .apply(i, Leaf::new));

    Codec<Select> SELECT = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.optionalFieldOf("min").forGetter(Select::min),
                    Codec.INT.optionalFieldOf("max").forGetter(Select::max),
                    Codecs.lazy(() -> Choice.CODEC).listOf().fieldOf("of").forGetter(Select::of))
            .apply(i, Select::new));

    Codec<Choice> CODEC = Codec.either(SELECT, LEAF).xmap(
            either -> either.map(s -> s, l -> l),
            choice -> choice instanceof Select s ? Either.left(s) : Either.right((Leaf) choice));
}
