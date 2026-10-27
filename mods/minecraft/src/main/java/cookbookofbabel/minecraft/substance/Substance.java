package cookbookofbabel.minecraft.substance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.dataset.Form;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * Every ingredient of the dataset that isn't a named dish. Substances are a datapack registry, at
 * {@code data/<namespace>/cookbook_of_babel/substance/<id>.json}, so datapacks can add or override
 * them. Build their stacks only through {@link Substances}.
 *
 * @param waterActivity the dataset's, where it gives one; it bounds which transitions can happen to the substance
 */
public record Substance(
        String label, Form form, int color, Carrier carrier, Optional<Nutrition> nutrition, Optional<Double> waterActivity) {
    public static final ResourceKey<Registry<Substance>> REGISTRY =
            ResourceKey.createRegistryKey(CookbookOfBabel.id("substance"));

    /** {@code carrier} may be left out when it is the form's default ({@link Carrier#forForm}). */
    public static final Codec<Substance> CODEC = Json.CODEC.xmap(Json::substance, Json::of);

    private record Json(
            String label,
            Form form,
            int color,
            Optional<Carrier> carrier,
            Optional<Nutrition> nutrition,
            Optional<Double> waterActivity) {
        static final Codec<Json> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING.fieldOf("label").forGetter(Json::label),
                        Form.CODEC.fieldOf("form").forGetter(Json::form),
                        Colors.CODEC.fieldOf("color").forGetter(Json::color),
                        Carrier.CODEC.optionalFieldOf("carrier").forGetter(Json::carrier),
                        Nutrition.CODEC.optionalFieldOf("nutrition").forGetter(Json::nutrition),
                        Codec.DOUBLE.optionalFieldOf("waterActivity").forGetter(Json::waterActivity))
                .apply(i, Json::new));

        Substance substance() {
            return new Substance(label, form, color, carrier.orElseGet(() -> Carrier.forForm(form)), nutrition, waterActivity);
        }

        static Json of(Substance s) {
            var carrier = Optional.of(s.carrier()).filter(c -> !c.equals(Carrier.forForm(s.form())));
            return new Json(s.label(), s.form(), s.color(), carrier, s.nutrition(), s.waterActivity());
        }
    }
}
