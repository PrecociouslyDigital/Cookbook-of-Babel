package cookbookofbabel.minecraft.datagen.plan;

import java.util.List;
import java.util.Set;

/** A load condition on a recipe, as data, so which recipes load can be checked without a game. */
public sealed interface Condition {
    record ModLoaded(Mod mod) implements Condition {
        @Override
        public <T> T match(Cases<T> cases) {
            return cases.modLoaded(mod);
        }
    }

    record Not(Condition condition) implements Condition {
        @Override
        public <T> T match(Cases<T> cases) {
            return cases.not(condition);
        }
    }

    /** An item tag with nothing in it. */
    record TagEmpty(String tag) implements Condition {
        @Override
        public <T> T match(Cases<T> cases) {
            return cases.tagEmpty(tag);
        }
    }

    /** Never holds: the recipe is written but disabled. */
    record False() implements Condition {
        @Override
        public <T> T match(Cases<T> cases) {
            return cases.never();
        }
    }

    /**
     * One case per kind of condition. Java 17 can't switch over a sealed type, so a new kind of
     * condition goes here, and nothing compiles until every implementation of {@code Cases} handles it.
     */
    interface Cases<T> {
        T modLoaded(Mod mod);

        T not(Condition condition);

        T tagEmpty(String tag);

        T never();
    }

    <T> T match(Cases<T> cases);

    default boolean holds(Set<Mod> loaded, Set<String> nonEmptyTags) {
        return match(new Cases<>() {
            @Override
            public Boolean modLoaded(Mod mod) {
                return mod.alwaysLoaded() || loaded.contains(mod);
            }

            @Override
            public Boolean not(Condition condition) {
                return !condition.holds(loaded, nonEmptyTags);
            }

            @Override
            public Boolean tagEmpty(String tag) {
                return !nonEmptyTags.contains(tag);
            }

            @Override
            public Boolean never() {
                return false;
            }
        });
    }

    static boolean all(List<Condition> conditions, Set<Mod> loaded, Set<String> nonEmptyTags) {
        return conditions.stream().allMatch(c -> c.holds(loaded, nonEmptyTags));
    }
}
