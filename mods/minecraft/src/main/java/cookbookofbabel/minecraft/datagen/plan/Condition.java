package cookbookofbabel.minecraft.datagen.plan;

import java.util.List;
import java.util.Set;

/** A load condition on a recipe, as data, so which recipes load can be checked without a game. */
public sealed interface Condition {
    record ModLoaded(Mod mod) implements Condition {}

    record Not(Condition condition) implements Condition {}

    /** An item tag with nothing in it. */
    record TagEmpty(String tag) implements Condition {}

    default boolean holds(Set<Mod> loaded, Set<String> nonEmptyTags) {
        if (this instanceof ModLoaded m) {
            return m.mod().alwaysLoaded() || loaded.contains(m.mod());
        }
        if (this instanceof Not n) {
            return !n.condition().holds(loaded, nonEmptyTags);
        }
        return !nonEmptyTags.contains(((TagEmpty) this).tag());
    }

    static boolean all(List<Condition> conditions, Set<Mod> loaded, Set<String> nonEmptyTags) {
        return conditions.stream().allMatch(c -> c.holds(loaded, nonEmptyTags));
    }
}
