package cookbookofbabel.gradle;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Turns the parts of the dataset export that the mod needs before any data loads into sources. The
 * forms become a Java enum, so every switch over them is checked for exhaustiveness and a form
 * added to the dataset fails to compile wherever it matters. The named dishes become a resource
 * instead: no code names a dish, so they gain nothing from being code, and there are too many of
 * them for one enum's initializer.
 */
@CacheableTask
public abstract class GenerateDatasetSources extends DefaultTask {
    private static final String PACKAGE = "cookbookofbabel.minecraft.dataset";
    /** Where {@code NamedDish} reads the dishes from, relative to the resources root. */
    private static final String NAMED_DISHES = "cookbook_of_babel/named_dishes.json";

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getExport();

    @OutputDirectory
    public abstract DirectoryProperty getOutput();

    @OutputDirectory
    public abstract DirectoryProperty getResources();

    @TaskAction
    @SuppressWarnings("unchecked")
    public void generate() throws IOException {
        var export = (Map<String, Object>) new JsonSlurper().parse(getExport().get().getAsFile());
        var forms = (List<Map<String, Object>>) export.get("forms");
        var ingredients = (List<Map<String, Object>>) export.get("ingredients");
        var nutrition = (Map<String, Map<String, Object>>) export.getOrDefault("nutrition", Map.of());
        var dir = getOutput().get().getAsFile().toPath().resolve(PACKAGE.replace('.', '/'));
        getProject().delete(dir.toFile());
        Files.createDirectories(dir);
        write(dir.resolve("Form.java"), form(forms));
        var dishes = getResources().get().getAsFile().toPath().resolve(NAMED_DISHES);
        Files.createDirectories(dishes.getParent());
        write(dishes, namedDishes(ingredients, nutrition));
    }

    private static String form(List<Map<String, Object>> forms) {
        var constants = forms.stream()
                .map(f -> "    %s(\"%s\")".formatted(constant(id(f)), id(f)))
                .collect(Collectors.joining(",\n"));
        return """
                package %s;

                import com.mojang.serialization.Codec;
                import net.minecraft.util.StringRepresentable;

                /** The dataset's form registry. Generated from the export; do not edit. */
                public enum Form implements StringRepresentable {
                %s;

                    public static final Codec<Form> CODEC = StringRepresentable.fromEnum(Form::values);

                    private final String id;

                    Form(String id) {
                        this.id = id;
                    }

                    @Override
                    public String getSerializedName() {
                        return id;
                    }
                }
                """.formatted(PACKAGE, constants);
    }

    /** One line per dish, so a dataset change shows up as a readable diff. */
    private static String namedDishes(List<Map<String, Object>> ingredients, Map<String, Map<String, Object>> nutrition) {
        return ingredients.stream()
                .filter(i -> Boolean.TRUE.equals(i.get("named")))
                .map(i -> {
                    var dish = new LinkedHashMap<String, Object>();
                    dish.put("id", id(i));
                    dish.put("label", i.get("label"));
                    dish.put("form", i.get("form"));
                    var per100g = nutrition.get(id(i));
                    if (per100g != null) {
                        dish.put("nutrition", per100g);
                    }
                    if (i.get("waterActivity") != null) {
                        dish.put("waterActivity", i.get("waterActivity"));
                    }
                    return "  " + JsonOutput.toJson(dish);
                })
                .collect(Collectors.joining(",\n", "[\n", "\n]\n"));
    }

    private static String id(Map<String, Object> entity) {
        return (String) entity.get("id");
    }

    /** Slugs may start with a digit, which a Java identifier may not. */
    private static String constant(String slug) {
        var upper = slug.toUpperCase(Locale.ROOT);
        return Character.isDigit(upper.charAt(0)) ? "_" + upper : upper;
    }

    private static void write(Path path, String source) throws IOException {
        Files.writeString(path, source, StandardCharsets.UTF_8);
    }
}
