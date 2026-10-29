package cookbookofbabel.minecraft.datagen.plan;

import static cookbookofbabel.minecraft.datagen.plan.RouteKind.BLASTING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.BNC_KEG;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CAMPFIRE;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_COMPACTING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_CUTTING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_DEPLOYING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_HEATED_MIXING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_ITEM_DRAIN;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_MILLING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_MIXING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_SPLASHING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.FD_COOKING_POT;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.FD_COOKING_POT_SEVERAL;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.FD_CUTTING_BOARD;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.FR_KETTLE;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.HEXEREI_DRYING_RACK;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.HEXEREI_MIXING_CAULDRON;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.HEXEREI_PESTLE_AND_MORTAR;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.PNC_THERMO_PLANT;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.GARNISHED_FREEZING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.SHAPELESS_CRAFTING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.SMELTING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.SMOKING;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_PRESS;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.CREATE_SPOUT;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.THERMAL_BOTTLER;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.THERMAL_CENTRIFUGE;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.THERMAL_CHILLER;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.THERMAL_PRESS;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.THERMAL_SMELTER;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.THEURGY_CALCINATION;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.THEURGY_FERMENTATION;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.THEURGY_LIQUEFACTION;
import static cookbookofbabel.minecraft.datagen.plan.RouteKind.VD_FERMENTING_JAR;

import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.TechniqueId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which machines do which technique. A technique not in the table takes the binding of its nearest
 * ancestor, so {@code pan_fry} is done like {@code fry} and {@code chop} like {@code cut}.
 */
public final class Techniques {
    /** Coating and shaping are kinds of assembly, each with a machine of its own besides. */
    private static final List<RouteKind> ASSEMBLY = List.of(SHAPELESS_CRAFTING, CREATE_DEPLOYING, HEXEREI_MIXING_CAULDRON);

    static final Map<TechniqueId, Binding> TABLE = Map.ofEntries(
            bind("mix", CREATE_MIXING, SHAPELESS_CRAFTING, HEXEREI_MIXING_CAULDRON),
            bind("whisk", CREATE_MIXING),
            bind("blend", CREATE_MIXING),
            bind("heat", CREATE_HEATED_MIXING, FD_COOKING_POT, HEXEREI_MIXING_CAULDRON),
            bind("bake", SMELTING, SMOKING, FD_COOKING_POT_SEVERAL),
            // The pot only steps in where the furnace and smoker can't: several ingredients at once.
            bind("roast", SMELTING, SMOKING, THERMAL_SMELTER, FD_COOKING_POT_SEVERAL),
            bind("toast", SMELTING, SMOKING, THERMAL_SMELTER, FD_COOKING_POT_SEVERAL),
            // The pot also stands in for the grill where a step has several ingredients, as in
            // melting cheese on a patty or glazing a steak.
            bind("grill", CAMPFIRE, SMOKING, FD_COOKING_POT_SEVERAL),
            bind("smoke", SMOKING, CAMPFIRE, FD_COOKING_POT_SEVERAL),
            // Farmer's Delight's skillet takes one input, so it can't take the oil too.
            // Hexerei's cauldron is the deep fryer of last resort: oil plus up to eight items.
            bind("fry", CREATE_HEATED_MIXING, PNC_THERMO_PLANT, HEXEREI_MIXING_CAULDRON),
            bind("cut", FD_CUTTING_BOARD, CREATE_CUTTING),
            bind("grind", CREATE_MILLING, HEXEREI_PESTLE_AND_MORTAR),
            // The dataset lists the water as an input and the starchy water as an output. A fan
            // blowing through water supplies its own.
            bind("rinse", CREATE_MIXING, CREATE_SPLASHING),
            bind("soak", CREATE_MIXING, CREATE_SPLASHING),
            bind("steep", CREATE_MIXING, CREATE_SPLASHING, FR_KETTLE, THEURGY_LIQUEFACTION),
            bind("separate", CREATE_ITEM_DRAIN, CREATE_MIXING, THERMAL_CENTRIFUGE),
            bind("press", CREATE_ITEM_DRAIN, CREATE_MIXING, CREATE_PRESS, THERMAL_PRESS),
            bind("churn", CREATE_MIXING, THERMAL_CENTRIFUGE),
            // A heated basin boils the wash and collects what comes off as a fluid.
            bind("distill", CREATE_HEATED_MIXING),
            bind("coagulate", CREATE_MIXING),
            bind("assemble", ASSEMBLY),
            // The spout coats anything with any liquid; the cauldron and crafting grid do the rest.
            bind("coat", ASSEMBLY, CREATE_SPOUT, THERMAL_BOTTLER),
            bind("shape", ASSEMBLY, CREATE_COMPACTING),
            // Calcining gives off a gas, which only a basin can collect.
            bind("calcine", CREATE_HEATED_MIXING, BLASTING, THEURGY_CALCINATION),
            // Ash needs far more input than a furnace takes at once, so a basin too.
            bind("ash", SMELTING, BLASTING, CREATE_HEATED_MIXING, THEURGY_CALCINATION),
            bind("carbonate", CREATE_MIXING),
            bind("evaporate", CREATE_HEATED_MIXING),
            hold("cool"),
            hold("chill", THERMAL_CHILLER),
            hold("dry", HEXEREI_DRYING_RACK),
            hold("freeze", GARNISHED_FREEZING, THERMAL_CHILLER),
            // Ageing, curing and sprouting take a timed container, or just time: a cheese ripens
            // in a jar in a day, or on a shelf over the item clock.
            hold("age", VD_FERMENTING_JAR, BNC_KEG),
            // Food left in a chest also ferments, on the item clock: a starter turns to yeast.
            hold("ferment", VD_FERMENTING_JAR, BNC_KEG, PNC_THERMO_PLANT, THEURGY_FERMENTATION),
            // Staling is cooking, not decay: bread stale enough for croutons and bread crumbs.
            hold("stale"),
            // Spoilage is declined: it stops here, so spoil and everything below it (mould, freezer burn,
            // going rancid and sour) binds to nothing instead of falling through to the age holding.
            Map.entry(new TechniqueId("spoil"), new Binding.Unbound()));

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

    /**
     * What this game tells a technique's results apart by: the technique whose binding it takes, if that puts it in a
     * machine. Otherwise it is told apart by itself: going stale and going mouldy are different transitions.
     */
    public TechniqueId machineOf(TechniqueId technique) {
        return boundTechnique(technique).filter(t -> !TABLE.get(t).routes().isEmpty()).orElse(technique);
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

    private static Map.Entry<TechniqueId, Binding> hold(String technique, RouteKind... routes) {
        return Map.entry(new TechniqueId(technique), new Binding.Held(List.of(routes)));
    }

    private static Map.Entry<TechniqueId, Binding> bind(String technique, RouteKind... routes) {
        return bind(technique, List.of(), routes);
    }

    private static Map.Entry<TechniqueId, Binding> bind(String technique, List<RouteKind> shared, RouteKind... own) {
        var routes = new ArrayList<>(shared);
        routes.addAll(List.of(own));
        return Map.entry(new TechniqueId(technique), new Binding.Routes(List.copyOf(routes)));
    }
}
