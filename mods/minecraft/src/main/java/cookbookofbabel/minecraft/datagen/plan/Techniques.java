package cookbookofbabel.minecraft.datagen.plan;

import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CAMPFIRE;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_CUTTING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_DEPLOYING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_HEATED_MIXING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_ITEM_DRAIN;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_MILLING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_MIXING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.FD_COOKING_POT;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.FD_CUTTING_BOARD;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.SHAPELESS_CRAFTING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.SMELTING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.SMOKING;

import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.TechniqueId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which machines do which technique. A technique not in the table takes the binding of its nearest
 * ancestor, so {@code pan_fry} is done like {@code fry} and {@code chop} like {@code cut}.
 */
public final class Techniques {
    static final Map<TechniqueId, Binding> TABLE = Map.ofEntries(
            bind("mix", CREATE_MIXING, SHAPELESS_CRAFTING),
            bind("whisk", CREATE_MIXING),
            bind("blend", CREATE_MIXING),
            bind("heat", CREATE_HEATED_MIXING, FD_COOKING_POT),
            bind("bake", SMELTING, SMOKING),
            bind("roast", SMELTING, SMOKING),
            bind("toast", SMELTING, SMOKING),
            bind("grill", CAMPFIRE, SMOKING),
            // Farmer's Delight's skillet takes one input, so it can't take the oil too.
            bind("fry", CREATE_HEATED_MIXING),
            bind("cut", FD_CUTTING_BOARD, CREATE_CUTTING),
            bind("grind", CREATE_MILLING),
            // The dataset lists the water as an input and the starchy water as an output.
            bind("rinse", CREATE_MIXING),
            bind("separate", CREATE_ITEM_DRAIN, CREATE_MIXING),
            bind("coagulate", CREATE_MIXING),
            bind("assemble", SHAPELESS_CRAFTING, CREATE_DEPLOYING),
            Map.entry(new TechniqueId("cool"), new Binding.Elidable()),
            Map.entry(new TechniqueId("chill"), new Binding.Elidable()),
            Map.entry(new TechniqueId("age"), new Binding.Deferred("time-based changes wait for a time mechanic")));

    /**
     * Media a technique uses up. Minecraft has no "already in the pan", so a step of one of these
     * techniques must take the medium as an input.
     */
    static final Map<TechniqueId, IngredientId> MEDIA =
            Map.of(new TechniqueId("fry"), new IngredientId("oil_and_fat"));

    private final Dataset dataset;

    public Techniques(Dataset dataset) {
        this.dataset = dataset;
    }

    public Binding bindingFor(TechniqueId technique) {
        return dataset.lineage(technique).stream()
                .flatMap(t -> Optional.ofNullable(TABLE.get(t)).stream())
                .findFirst()
                .orElseGet(Binding.Unbound::new);
    }

    public Optional<IngredientId> mediumFor(TechniqueId technique) {
        return dataset.lineage(technique).stream()
                .flatMap(t -> Optional.ofNullable(MEDIA.get(t)).stream())
                .findFirst();
    }

    /** Every technique and ingredient the tables name, so datagen can check they exist. */
    public static Set<TechniqueId> named() {
        return java.util.stream.Stream.concat(TABLE.keySet().stream(), MEDIA.keySet().stream())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Map.Entry<TechniqueId, Binding> bind(String technique, RouteKind... routes) {
        return Map.entry(new TechniqueId(technique), new Binding.Routes(List.of(routes)));
    }
}
