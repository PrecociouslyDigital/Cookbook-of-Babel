package cookbookofbabel.gradle;

import groovy.json.JsonSlurper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Turns the parts of the dataset export that code must know at compile time into Java enums: the
 * forms, so every switch over them is checked for exhaustiveness, and the named dishes, which are
 * registered as items. A form added to or a dish renamed in the dataset then fails to compile
 * wherever it matters, instead of failing at runtime.
 */
@CacheableTask
public abstract class GenerateDatasetSources extends DefaultTask {
    private static final String PACKAGE = "cookbookofbabel.minecraft.dataset";

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getExport();

    @OutputDirectory
    public abstract DirectoryProperty getOutput();

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
        write(dir.resolve("NamedDish.java"), namedDish(ingredients, nutrition));
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

    private static String namedDish(List<Map<String, Object>> ingredients, Map<String, Map<String, Object>> nutrition) {
        var constants = ingredients.stream()
                .filter(i -> Boolean.TRUE.equals(i.get("named")))
                .map(i -> "    %s(\"%s\", %s, Form.%s, %s)".formatted(
                        constant(id(i)),
                        id(i),
                        javaString((String) i.get("label")),
                        constant((String) i.get("form")),
                        nutritionOf(nutrition.get(id(i)))))
                .collect(Collectors.joining(",\n"));
        return """
                package %s;

                import cookbookofbabel.minecraft.substance.Nutrition;
                import java.util.Arrays;
                import java.util.Map;
                import java.util.Optional;
                import java.util.function.Function;
                import java.util.stream.Collectors;

                /**
                 * The dataset's named dishes, each registered as an item of its own. Generated from the
                 * export; do not edit.
                 */
                public enum NamedDish {
                %s;

                    public final String id;
                    /** The English name, used when no translation is loaded. */
                    public final String label;
                    public final Form form;
                    public final Optional<Nutrition> nutrition;

                    private static final Map<String, NamedDish> BY_ID =
                            Arrays.stream(values()).collect(Collectors.toMap(d -> d.id, Function.identity()));

                    NamedDish(String id, String label, Form form, Optional<Nutrition> nutrition) {
                        this.id = id;
                        this.label = label;
                        this.form = form;
                        this.nutrition = nutrition;
                    }

                    public static Optional<NamedDish> byId(String id) {
                        return Optional.ofNullable(BY_ID.get(id));
                    }
                }
                """.formatted(PACKAGE, constants);
    }

    private static String nutritionOf(Map<String, Object> per100g) {
        if (per100g == null) {
            return "Optional.empty()";
        }
        var fields = List.of("kcal", "proteinG", "fatG", "carbsG").stream()
                .map(k -> per100g.containsKey(k) ? "Optional.of(%sd)".formatted(per100g.get(k)) : "Optional.empty()")
                .collect(Collectors.joining(", "));
        return "Optional.of(new Nutrition(" + fields + "))";
    }

    private static String javaString(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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
