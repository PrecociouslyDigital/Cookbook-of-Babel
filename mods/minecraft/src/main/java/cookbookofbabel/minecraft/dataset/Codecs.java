package cookbookofbabel.minecraft.dataset;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.function.Supplier;

final class Codecs {
    private Codecs() {}

    /** For codecs that refer to themselves, like {@link Select}'s choices. */
    static <A> Codec<A> lazy(Supplier<Codec<A>> codec) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                return codec.get().decode(ops, input);
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return codec.get().encode(input, ops, prefix);
            }
        };
    }
}
