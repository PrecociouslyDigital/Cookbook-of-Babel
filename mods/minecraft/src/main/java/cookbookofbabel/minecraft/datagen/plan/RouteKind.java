package cookbookofbabel.minecraft.datagen.plan;

/**
 * The machines a step can be done in, and what each one can take. Limits come from the machines
 * themselves, e.g. a Create basin's two fluid tanks and four output slots, or a smelter's one input.
 */
public enum RouteKind {
    CREATE_MIXING(Mod.CREATE, new Constraints(64, 2, 4, 2, true, 20, 400)),
    CREATE_HEATED_MIXING(Mod.CREATE, new Constraints(64, 2, 4, 2, true, 20, 400)),
    SHAPELESS_CRAFTING(Mod.MINECRAFT, new Constraints(9, 0, 1, 0, false, 0, 0)),
    FD_COOKING_POT(Mod.FARMERS_DELIGHT, new Constraints(6, 0, 1, 0, false, 100, 1200)),
    SMELTING(Mod.MINECRAFT, new Constraints(1, 0, 1, 0, false, 100, 600)),
    SMOKING(Mod.MINECRAFT, new Constraints(1, 0, 1, 0, false, 50, 300)),
    /** A blast furnace, or a Create fan blowing through lava. */
    BLASTING(Mod.MINECRAFT, new Constraints(1, 0, 1, 0, false, 50, 300)),
    CAMPFIRE(Mod.MINECRAFT, new Constraints(1, 0, 1, 0, false, 200, 800)),
    FD_CUTTING_BOARD(Mod.FARMERS_DELIGHT, new Constraints(1, 0, 4, 0, true, 0, 0)),
    CREATE_CUTTING(Mod.CREATE, new Constraints(1, 0, 4, 0, true, 50, 400)),
    CREATE_MILLING(Mod.CREATE, new Constraints(1, 0, 4, 0, true, 50, 400)),
    /** The item drain, standing in for a strainer: one item in, one item and one fluid out. */
    CREATE_ITEM_DRAIN(Mod.CREATE, new Constraints(1, 0, 1, 1, false, 0, 0)),
    /** One item on the belt, one in the deployer's hand. */
    CREATE_DEPLOYING(Mod.CREATE, new Constraints(2, 0, 4, 0, true, 0, 0));

    public final Mod mod;
    public final Constraints constraints;

    RouteKind(Mod mod, Constraints constraints) {
        this.mod = mod;
        this.constraints = constraints;
    }

    /**
     * {@code maxItemInputs} counts items, not slots. A duration range of 0 means the machine
     * ignores durations.
     */
    public record Constraints(
            int maxItemInputs,
            int maxFluidInputs,
            int maxItemOutputs,
            int maxFluidOutputs,
            boolean chanceOutputs,
            int minTicks,
            int maxTicks) {
        public boolean takesDuration() {
            return maxTicks > 0;
        }
    }

    public boolean heated() {
        return this == CREATE_HEATED_MIXING;
    }
}
