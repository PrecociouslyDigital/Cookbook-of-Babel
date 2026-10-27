package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.clock.Transition;
import java.util.List;
import java.util.Optional;

/** What the mod does with a technique. */
public sealed interface Binding {
    /** The machines a step of the technique is done in. */
    List<RouteKind> routes();

    /** The transition a step becomes while food is kept, if the technique turns one ingredient into another after a set time. */
    default Optional<Transition.Kind> transition() {
        return Optional.empty();
    }

    /** Done in each of these machines. */
    record Routes(List<RouteKind> routes) implements Binding {}

    /** Something that just needs time in the right conditions, like cooling; these machines do it too. */
    record Held(List<RouteKind> routes) implements Binding {
        @Override
        public Optional<Transition.Kind> transition() {
            return Optional.of(Transition.Kind.HELD);
        }
    }

    /** Food going off, which no machine is for. */
    record Spoils() implements Binding {
        @Override
        public List<RouteKind> routes() {
            return List.of();
        }

        @Override
        public Optional<Transition.Kind> transition() {
            return Optional.of(Transition.Kind.SPOIL);
        }
    }

    /** Neither the technique nor any ancestor is in the table. */
    record Unbound() implements Binding {
        @Override
        public List<RouteKind> routes() {
            return List.of();
        }
    }
}
