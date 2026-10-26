package cookbookofbabel.minecraft.datagen;

import cookbookofbabel.minecraft.dataset.Export;
import cookbookofbabel.minecraft.dataset.Ingredient;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.NamedDish;
import cookbookofbabel.minecraft.dataset.Recipe;
import cookbookofbabel.minecraft.datagen.plan.Binding;
import cookbookofbabel.minecraft.datagen.plan.Dataset;
import cookbookofbabel.minecraft.datagen.plan.Elision;
import cookbookofbabel.minecraft.datagen.plan.Mapping;
import cookbookofbabel.minecraft.datagen.plan.Merging;
import cookbookofbabel.minecraft.datagen.plan.Planner;
import cookbookofbabel.minecraft.datagen.plan.Techniques;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Everything datagen reads, loaded once and checked up front: the dataset export, the mappings,
 * and the recipe graph with same-machine preparations merged and time-based steps elided. Paths
 * come from system properties the Gradle data run sets.
 */
public final class DatagenContext {
    public final Export export;
    public final Dataset dataset;
    public final List<Mapping> mappings;
    public final Techniques techniques;
    public final Merging.Result merging;
    public final Elision.Result elision;
    public final Planner planner;
    public final Coverage coverage;
    public final Path coverageFile;
    public final Path candidatesFile;

    private DatagenContext(Export export, List<Mapping> mappings, Path coverageFile, Path candidatesFile) {
        this.export = export;
        this.dataset = new Dataset(export);
        this.mappings = mappings;
        this.techniques = new Techniques(dataset);
        this.merging = Merging.merge(export.recipes(), export.preparations(), techniques::machineOf);
        this.elision = Elision.elide(merging.recipes(), r -> techniques.bindingFor(r.technique()) instanceof Binding.Elidable);
        this.planner = new Planner(dataset, techniques, elision.recipes());
        this.coverage = new Coverage();
        this.coverageFile = coverageFile;
        this.candidatesFile = candidatesFile;
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
        return elision.recipes();
    }

    /** Fail on anything the mappings or tables name that doesn't exist. */
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
        var dishes = Arrays.stream(NamedDish.values()).map(d -> d.id).collect(Collectors.toSet());
        var named = export.ingredients().stream().filter(Ingredient::named).map(i -> i.id().value()).collect(Collectors.toSet());
        if (!dishes.equals(named)) {
            problems.add("NamedDish is out of date with the export; run generateDatasetSources");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("datagen input problems:\n  " + String.join("\n  ", problems));
        }
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
