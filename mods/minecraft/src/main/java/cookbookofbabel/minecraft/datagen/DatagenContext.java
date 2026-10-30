package cookbookofbabel.minecraft.datagen;

import cookbookofbabel.minecraft.dataset.Export;
import cookbookofbabel.minecraft.dataset.Ingredient;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.NamedDish;
import cookbookofbabel.minecraft.dataset.Recipe;
import cookbookofbabel.minecraft.datagen.plan.Amounts;
import cookbookofbabel.minecraft.datagen.plan.Dataset;
import cookbookofbabel.minecraft.datagen.plan.Eliding;
import cookbookofbabel.minecraft.datagen.plan.Mapping;
import cookbookofbabel.minecraft.datagen.plan.Merging;
import cookbookofbabel.minecraft.datagen.plan.Planner;
import cookbookofbabel.minecraft.datagen.plan.Provided;
import cookbookofbabel.minecraft.datagen.plan.RouteKind;
import cookbookofbabel.minecraft.datagen.plan.Techniques;
import cookbookofbabel.minecraft.spice.SpiceGardens;
import cookbookofbabel.minecraft.supplant.Supplanting;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Everything datagen reads, loaded once and checked up front: the dataset export, the mappings,
 * and the recipe graph with same-machine preparations merged and single-ingredient crafts elided.
 * Paths come from system properties the Gradle data run sets.
 */
public final class DatagenContext {
    public final Export export;
    public final Dataset dataset;
    public final List<Mapping> mappings;
    public final Techniques techniques;
    public final Merging.Result merging;
    public final Eliding.Result eliding;
    public final Planner planner;
    public final Coverage coverage;
    public final Path coverageFile;
    public final Path candidatesDirectory;

    private DatagenContext(Export export, List<Mapping> mappings, Path coverageFile, Path candidatesDirectory) {
        this.export = export;
        this.dataset = new Dataset(export);
        this.mappings = mappings;
        this.techniques = new Techniques(dataset);
        this.merging = Merging.merge(export.recipes(), export.preparations(), techniques::machineOf);
        this.eliding = Eliding.elide(
                merging.recipes(),
                t -> techniques.bindingFor(t).routes().contains(RouteKind.SHAPELESS_CRAFTING),
                held(),
                new Amounts(dataset)::ratio,
                techniques::machineOf);
        this.planner = new Planner(dataset, techniques, eliding.recipes(), Provided.of(mappings, dataset));
        this.coverage = new Coverage();
        this.coverageFile = coverageFile;
        this.candidatesDirectory = candidatesDirectory;
    }

    static DatagenContext load() throws IOException {
        var context = new DatagenContext(
                Export.read(path("export")),
                Mapping.readAll(path("mappings")),
                path("coverage"),
                path("candidates"));
        context.check();
        return context;
    }

    public List<Recipe> recipes() {
        return eliding.recipes();
    }

    /** Ingredients eliding must keep: named dishes, and what mapped mods and the mod's gardens give players. */
    private Set<IngredientId> held() {
        var held = new java.util.HashSet<IngredientId>(SpiceGardens.all().keySet());
        mappings.forEach(m -> held.addAll(m.entries().keySet()));
        dataset.ingredients().stream().filter(Ingredient::named).forEach(i -> held.add(i.id()));
        return held;
    }

    /**
     * Fail on anything the mappings or tables name that doesn't exist, on a planted spice that a
     * mapped mod already grows, and on a withdrawn item that is missing or mapped.
     */
    private void check() {
        var problems = new ArrayList<String>();
        var techniqueIds = export.techniques().stream().map(t -> t.id()).collect(Collectors.toSet());
        Techniques.named().stream()
                .filter(t -> !techniqueIds.contains(t))
                .forEach(t -> problems.add("the technique table names " + t + ", which the dataset doesn't have"));
        for (var mapping : mappings) {
            mapping.entries().forEach((ingredient, entry) -> {
                if (!dataset.hasIngredient(ingredient)) {
                    problems.add(mapping.mod().id + " maps " + ingredient + ", which the dataset doesn't have");
                }
                entry.items().stream()
                        .filter(i -> !ForgeRegistries.ITEMS.containsKey(new ResourceLocation(i.id())))
                        .forEach(i -> problems.add(mapping.mod().id + " maps " + ingredient + " to a missing item " + i.id()));
                entry.fluids().stream()
                        .filter(f -> !ForgeRegistries.FLUIDS.containsKey(new ResourceLocation(f)))
                        .forEach(f -> problems.add(mapping.mod().id + " maps " + ingredient + " to a missing fluid " + f));
            });
        }
        SpiceGardens.all().keySet().forEach(spice -> {
            if (!dataset.hasIngredient(spice)) {
                problems.add("spice_gardens.json plants " + spice + ", which the dataset doesn't have");
            } else if (dataset.ingredient(spice).named()) {
                problems.add("spice_gardens.json plants " + spice + ", a named dish");
            }
            mappings.stream()
                    .filter(m -> m.entry(spice).isPresent())
                    .forEach(m -> problems.add("spice_gardens.json plants " + spice + ", which " + m.mod().id + " already provides"));
        });
        Supplanting.curated().forEach(item -> {
            if (!ForgeRegistries.ITEMS.containsKey(item)) {
                problems.add("withdrawn.json withdraws a missing item " + item);
            }
            mappings.stream()
                    .filter(m -> m.entries().values().stream().anyMatch(e -> e.items().stream().anyMatch(i -> i.id().equals(item.toString()))))
                    .forEach(m -> problems.add("withdrawn.json withdraws " + item + ", which " + m.mod().id + " maps"));
        });
        var dishes = NamedDish.values().stream().map(NamedDish::id).collect(Collectors.toSet());
        var named = export.ingredients().stream().filter(Ingredient::named).map(i -> i.id().value()).collect(Collectors.toSet());
        if (!dishes.equals(named)) {
            problems.add("NamedDish is out of date with the export; run generateDatasetSources");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("datagen input problems:\n  " + String.join("\n  ", problems));
        }
    }

    private final Map<IngredientId, Boolean> tagged = new HashMap<>();

    /**
     * Whether an ingredient's {@code ingredients} tag has content: what a mapping maps to it, a dish
     * of its own, or a tagged descendant. A tag with none of these is empty, which JEI shows as an
     * accepted-but-empty ingredient, so it is written at all only for these.
     */
    public boolean tagged(IngredientId id) {
        var cached = tagged.get(id);
        if (cached != null) {
            return cached;
        }
        // The is-a graph is a DAG, so the recursion below never re-enters this ingredient.
        var result = dataset.ingredient(id).named()
                || mappings.stream().anyMatch(m -> m.entry(id).filter(e -> !e.items().isEmpty() || !e.itemTags().isEmpty()).isPresent())
                || dataset.children(id).stream().anyMatch(this::tagged);
        tagged.put(id, result);
        return result;
    }

    public Set<IngredientId> producedSomewhere() {
        return recipes().stream()
                .flatMap(r -> cookbookofbabel.minecraft.datagen.plan.Recipes.outputs(r.outputs()).stream())
                .map(o -> o.ingredient())
                .collect(Collectors.toSet());
    }

    private static Path path(String name) {
        var value = System.getProperty("cookbook_of_babel." + name);
        if (value == null) {
            throw new IllegalStateException("datagen needs -Dcookbook_of_babel." + name + "; run it through ./gradlew runData");
        }
        return Path.of(value);
    }
}
