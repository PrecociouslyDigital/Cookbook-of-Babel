package cookbookofbabel.minecraft.datagen.plan;

import static cookbookofbabel.minecraft.datagen.plan.RouteKind.BLASTING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.BNC_KEG;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CAMPFIRE;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_CUTTING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_DEPLOYING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_HEATED_MIXING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_ITEM_DRAIN;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_MILLING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_MIXING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_SPLASHING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.FD_COOKING_POT;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.FD_CUTTING_BOARD;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.FR_KETTLE;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.GARNISHED_FREEZING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.SHAPELESS_CRAFTING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.SMELTING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.SMOKING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.VD_FERMENTING_JAR;

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
            bind("smoke", SMOKING, CAMPFIRE),
            // Farmer's Delight's skillet takes one input, so it can't take the oil too.
            bind("fry", CREATE_HEATED_MIXING),
            bind("cut", FD_CUTTING_BOARD, CREATE_CUTTING),
            bind("grind", CREATE_MILLING),
            // The dataset lists the water as an input and the starchy water as an output. A fan
            // blowing through water supplies its own.
            bind("rinse", CREATE_MIXING, CREATE_SPLASHING),
            bind("soak", CREATE_MIXING, CREATE_SPLASHING),
            bind("steep", CREATE_MIXING, CREATE_SPLASHING, FR_KETTLE),
            bind("separate", CREATE_ITEM_DRAIN, CREATE_MIXING),
            bind("churn", CREATE_MIXING),
            // A heated basin boils the wash and collects what comes off as a fluid.
            bind("distill", CREATE_HEATED_MIXING),
            bind("coagulate", CREATE_MIXING),
            bind("assemble", SHAPELESS_CRAFTING, CREATE_DEPLOYING),
            // Calcining gives off a gas, which only a basin can collect.
            bind("calcine", CREATE_HEATED_MIXING, BLASTING),
            // Ash needs far more input than a furnace takes at once, so a basin too.
            bind("ash", SMELTING, BLASTING, CREATE_HEATED_MIXING),
            bind("carbonate", CREATE_MIXING),
            bind("evaporate", CREATE_HEATED_MIXING),
            elide("cool"),
            elide("chill"),
            elide("dry"),
            elide("freeze", GARNISHED_FREEZING),
            // Ageing, fermenting, curing and sprouting take a timed container.
            bind("age", VD_FERMENTING_JAR, BNC_KEG),
            Map.entry(new TechniqueId("spoil"), new Binding.Deferred("spoilage is something to prevent, not a recipe")));

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
        return boundTechnique(technique).map(TABLE::get).orElseGet(Binding.Unbound::new);
    }

    /** The technique whose binding this one takes: itself or its nearest ancestor in the table. */
    public Optional<TechniqueId> boundTechnique(TechniqueId technique) {
        return dataset.lineage(technique).stream().filter(TABLE::containsKey).findFirst();
    }

    /** What this game tells a technique's results apart by: the technique whose binding it takes, or itself if unbound. */
    public TechniqueId machineOf(TechniqueId technique) {
        return boundTechnique(technique).orElse(technique);
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

    private static Map.Entry<TechniqueId, Binding> elide(String technique, RouteKind... routes) {
        return Map.entry(new TechniqueId(technique), new Binding.Elidable(List.of(routes)));
    }

    private static Map.Entry<TechniqueId, Binding> bind(String technique, RouteKind... routes) {
        return Map.entry(new TechniqueId(technique), new Binding.Routes(List.of(routes)));
    }
}
