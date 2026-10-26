package cookbookofbabel.minecraft.datagen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import cookbookofbabel.minecraft.dataset.Form;
import cookbookofbabel.minecraft.dataset.Ingredient;
import cookbookofbabel.minecraft.dataset.IngredientId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ProposalsProperties {
    /** However a name is cased and punctuated, it matches the item whose ID path spells it with underscores. */
    @Property
    void nameMatchesItsIdPath(@ForAll("words") List<String> words, @ForAll boolean upper, @ForAll("separators") String separator) {
        var label = String.join(separator, words);
        var ingredient = ingredient("x", upper ? label.toUpperCase(Locale.ROOT) : label);
        var item = new Proposals.Named("mod:" + String.join("_", words), Optional.empty());
        assertThat(Proposals.match(List.of(ingredient), List.of(item))).containsKey(ingredient.id());
    }

    /** Matching is exact: a longer name doesn't match a shorter one it contains. */
    @Property
    void containingIsNotMatching(@ForAll("words") List<String> words) {
        var ingredient = ingredient(String.join("_", words), String.join(" ", words));
        var item = new Proposals.Named("mod:" + String.join("_", words) + "_pie", Optional.empty());
        assertThat(Proposals.match(List.of(ingredient), List.of(item))).isEmpty();
    }

    @Example
    void pluralsMatchTheirSingulars() {
        for (var pair : List.of(
                List.of("tomato", "tomatoes"), List.of("sweet berry", "sweet_berries"), List.of("cookie", "cookies"),
                List.of("peach", "peaches"), List.of("onion", "onions"), List.of("glass", "glasses"))) {
            var ingredient = ingredient("x", pair.get(0));
            var item = new Proposals.Named("mod:" + pair.get(1), Optional.empty());
            assertThat(Proposals.match(List.of(ingredient), List.of(item))).as(pair.toString()).containsKey(ingredient.id());
        }
    }

    @Example
    void englishNamesMatchToo() {
        var ingredient = ingredient("kimchi", "kimchi");
        var item = new Proposals.Named("brewinandchewin:kimchi_jar", Optional.of("Kimchi"));
        assertThat(Proposals.match(List.of(ingredient), List.of(item))).containsKey(ingredient.id());
    }

    @Provide
    Arbitrary<List<String>> words() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(8).list().ofMinSize(1).ofMaxSize(3);
    }

    @Provide
    Arbitrary<String> separators() {
        return Arbitraries.of(" ", "_", "-", ", ");
    }

    private static Ingredient ingredient(String id, String label) {
        return new Ingredient(new IngredientId(id), label, List.of(), List.of(), Form.PIECE, Optional.empty(), Optional.empty(), false);
    }
}
