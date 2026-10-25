package cookbookofbabel.minecraft.datagen.plan;

import java.util.List;

/** What the mod does with a technique. */
public sealed interface Binding {
    /** Done in each of these machines. */
    record Routes(List<RouteKind> routes) implements Binding {}

    /**
     * Something that should just happen over time, like cooling. Until the mod has a time mechanic,
     * a one-in, one-out step of it is skipped (see {@link Elision}).
     */
    record Elidable() implements Binding {}

    /** Left out on purpose, for now. */
    record Deferred(String reason) implements Binding {}

    /** Neither the technique nor any ancestor is in the table. */
    record Unbound() implements Binding {}
}
