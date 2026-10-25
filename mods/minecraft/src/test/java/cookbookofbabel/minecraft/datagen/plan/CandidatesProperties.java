package cookbookofbabel.minecraft.datagen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import cookbookofbabel.minecraft.dataset.IngredientId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

class CandidatesProperties {
    private static final IngredientId X = new IngredientId("x");
    private static final String TAG = "cookbook_of_babel:external/x";

    /**
     * Whichever mods provide the ingredient and whichever are loaded, at most one candidate's
     * recipe loads, and exactly one while nothing fills its external tag.
     */
    @Property
    void exactlyOneCandidateHolds(@ForAll Set<Mod> providers, @ForAll Set<Mod> loaded, @ForAll boolean tagFilled) {
        var mappings = new ArrayList<Mapping>();
        for (var mod : Mod.values()) {
            if (providers.contains(mod)) {
                var entry = new Mapping.Entry(List.of(), List.of(), List.of(new Mapping.ExternalItem(mod.id + ":x", Optional.empty())), List.of());
                mappings.add(new Mapping(mod, Map.of(X, entry)));
            }
        }
        var candidates = Candidates.of(X, mappings, Candidates::items, TAG);
        var mods = EnumSet.noneOf(Mod.class);
        mods.addAll(loaded);
        var nonEmpty = tagFilled ? Set.of(TAG) : Set.<String>of();
        var holding = candidates.stream().filter(c -> Condition.all(c.conditions(), mods, nonEmpty)).count();
        assertThat(holding).isLessThanOrEqualTo(1);
        if (!tagFilled) {
            assertThat(holding).isEqualTo(1);
        }
    }
}
