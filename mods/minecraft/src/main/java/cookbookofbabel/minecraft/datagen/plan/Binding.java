package cookbookofbabel.minecraft.datagen.plan;

import java.util.List;

/** What the mod does with a technique. */
public sealed interface Binding {
    /** The machines a step of the technique is done in, if it isn't skipped. */
    List<RouteKind> routes();

    /** Done in each of these machines. */
    record Routes(List<RouteKind> routes) implements Binding {}

    /**
     * Something that should just happen over time, like cooling. A one-in, one-out step of it is
     * skipped where the recipe graph allows (see {@link Elision}); a step that can't be skipped is
     * done in these machines, if there are any.
     */
    record Elidable(List<RouteKind> routes) implements Binding {}

    /** Left out on purpose, for now. */
    record Deferred(String reason) implements Binding {
        public List<RouteKind> routes() {
            return List.of();
        }
    }

    /** Neither the technique nor any ancestor is in the table. */
    record Unbound() implements Binding {
        public List<RouteKind> routes() {
            return List.of();
        }
    }
}
