package cookbookofbabel.minecraft.dataset;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;

/** What a recipe yields: a fixed set of outputs, or exactly one of several branches by weight. */
public sealed interface Outcome {
    record Outputs(List<Output> outputs) implements Outcome {}

    record OneOf(List<Branch> branches) implements Outcome {}

    record Branch(double weight, Outcome outputs) {}

    enum Role {
        MAIN,
        BYPRODUCT,
        PART
    }

    /** {@code chance} rolls independently per output. */
    record Output(IngredientId ingredient, Amount amount, Role role, Optional<Double> chance) {}

    Codec<Role> ROLE = Codec.STRING.xmap(s -> Role.valueOf(s.toUpperCase(java.util.Locale.ROOT)), r -> r.name().toLowerCase(java.util.Locale.ROOT));

    Codec<Output> OUTPUT = RecordCodecBuilder.create(i -> i.group(
                    IngredientId.CODEC.fieldOf("ingredient").forGetter(Output::ingredient),
                    Amount.CODEC.fieldOf("amount").forGetter(Output::amount),
                    ROLE.optionalFieldOf("role", Role.MAIN).forGetter(Output::role),
                    Codec.DOUBLE.optionalFieldOf("chance").forGetter(Output::chance))
            .apply(i, Output::new));

    Codec<Branch> BRANCH = RecordCodecBuilder.create(i -> i.group(
                    Codec.DOUBLE.fieldOf("weight").forGetter(Branch::weight),
                    Codecs.lazy(() -> Outcome.CODEC).fieldOf("outputs").forGetter(Branch::outputs))
            .apply(i, Branch::new));

    Codec<OneOf> ONE_OF = RecordCodecBuilder.create(i -> i.group(BRANCH.listOf().fieldOf("oneOf").forGetter(OneOf::branches))
            .apply(i, OneOf::new));

    Codec<Outcome> CODEC = Codec.either(OUTPUT.listOf(), ONE_OF).xmap(
            either -> either.map(Outputs::new, o -> o),
            outcome -> outcome instanceof Outputs o ? Either.left(o.outputs()) : Either.right((OneOf) outcome));
}
