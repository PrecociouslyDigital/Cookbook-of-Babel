package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.IngredientId;
import java.util.Optional;

/**
 * The machines a step can be done in, and what each one can take. Limits come from the machines
 * themselves, e.g. a Create basin's two fluid tanks and four output slots, or a smelter's one input.
 */
public enum RouteKind {
    CREATE_MIXING(Mod.CREATE, new Constraints(64, 2, 4, 2, true, 20, 400).fluidsAlone()),
    CREATE_HEATED_MIXING(Mod.CREATE, new Constraints(64, 2, 4, 2, true, 20, 400).fluidsAlone()),
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
    CREATE_DEPLOYING(Mod.CREATE, new Constraints(2, 0, 4, 0, true, 0, 0)),
    /** A Create fan blowing through water. Fans take one item at a time and time every recipe alike. */
    CREATE_SPLASHING(Mod.CREATE, new Constraints(1, 0, 12, 0, true, 0, 0), Nbt.KEPT, Optional.of(new IngredientId("water"))),
    /** A Create fan blowing through powder snow, from Create: Garnished. */
    GARNISHED_FREEZING(Mod.GARNISHED, new Constraints(1, 0, 12, 0, true, 0, 0)),
    /** Brewin' and Chewin's keg: four items and a base fluid ferment into one thing. */
    BNC_KEG(Mod.BREWIN_AND_CHEWIN, new Constraints(4, 1, 1, 1, false, 200, Ticks.LONGEST).oneOutput(), Nbt.IGNORED, Optional.empty()),
    /** Farmer's Respite's kettle: one or two items brewed in a fluid make a fluid. */
    FR_KETTLE(Mod.FARMERS_RESPITE, new Constraints(2, 1, 0, 1, false, 200, 4800).needsFluid(), Nbt.IGNORED, Optional.empty()),
    /** Vintage Delight's fermenting jar: six items, one per slot, make one or two items. */
    VD_FERMENTING_JAR(Mod.VINTAGE_DELIGHT, new Constraints(6, 0, 2, 0, false, 100, Ticks.LONGEST)),
    /** PneumaticCraft's thermopneumatic processing plant: an item, a fluid or both make an item, a fluid or both, with or without heat. */
    PNC_THERMO_PLANT(Mod.PNEUMATICCRAFT, new Constraints(1, 1, 1, 1, false, 0, 0).fluidsAlone()),
    /**
     * Thermal's multiservo press, without a die. Thermal machines take one item at a time here: a
     * count above one wraps the ingredient in CoFH's IngredientWithCount, which can't read Forge's
     * compound ingredients and crashes the server's tag reload.
     */
    THERMAL_PRESS(Mod.THERMAL_EXPANSION, new Constraints(1, 0, 1, 1, true, 20, 1200)),
    /** Thermal's centrifugal separator. */
    THERMAL_CENTRIFUGE(Mod.THERMAL_EXPANSION, new Constraints(1, 0, 4, 1, true, 20, 1200)),
    /** Theurgy's calcination oven: a stack of one item burns down to another. */
    THEURGY_CALCINATION(Mod.THEURGY, new Constraints(16, 0, 1, 0, false, 20, 1200).oneItemKind()),
    /** Theurgy's fermentation vat: two items in a fluid ferment into an item. */
    THEURGY_FERMENTATION(Mod.THEURGY, new Constraints(2, 1, 1, 0, false, 20, Ticks.LONGEST).needsFluid(), Nbt.ON_RESULTS, Optional.empty()),
    /** Theurgy's liquefaction cauldron: an item steeped in a solvent becomes another item. */
    THEURGY_LIQUEFACTION(Mod.THEURGY, new Constraints(1, 1, 1, 0, false, 20, 1200).needsFluid(), Nbt.ON_RESULTS, Optional.empty()),
    /** Hexerei's drying rack. */
    HEXEREI_DRYING_RACK(Mod.HEXEREI, new Constraints(1, 0, 1, 0, false, 20, Ticks.LONGEST));


    public final Mod mod;
    public final Constraints constraints;
    public final Nbt nbt;
    /** What the machine provides itself, like the water a fan blows through, so a step's input of it is left out. */
    public final Optional<IngredientId> supplies;

    RouteKind(Mod mod, Constraints constraints) {
        this(mod, constraints, Nbt.KEPT, Optional.empty());
    }

    RouteKind(Mod mod, Constraints constraints, Nbt nbt, Optional<IngredientId> supplies) {
        // Only a step's main output can be another mod's (see Candidates).
        if (nbt == Nbt.IGNORED && constraints.maxOutputs() != 1) {
            throw new IllegalArgumentException(name() + " ignores NBT, so it can make only one thing");
        }
        this.mod = mod;
        this.constraints = constraints;
        this.nbt = nbt;
        this.supplies = supplies;
    }

    /**
     * {@code maxItemInputs} counts items, not slots, and {@code minItemKinds} and {@code itemKinds}
     * the different items; most machines need at least one. A duration range of 0 means the machine
     * ignores durations.
     */
    public record Constraints(
            int maxItemInputs,
            int minItemKinds,
            int itemKinds,
            int minFluidInputs,
            int maxFluidInputs,
            int maxItemOutputs,
            int maxFluidOutputs,
            int maxOutputs,
            boolean chanceOutputs,
            int minTicks,
            int maxTicks) {
        public Constraints(int maxItemInputs, int maxFluidInputs, int maxItemOutputs, int maxFluidOutputs, boolean chanceOutputs, int minTicks, int maxTicks) {
            this(maxItemInputs, 1, maxItemInputs, 0, maxFluidInputs, maxItemOutputs, maxFluidOutputs, maxItemOutputs + maxFluidOutputs, chanceOutputs, minTicks, maxTicks);
        }

        /** Takes a stack of one item, not several items. */
        Constraints oneItemKind() {
            return new Constraints(maxItemInputs, minItemKinds, 1, minFluidInputs, maxFluidInputs, maxItemOutputs, maxFluidOutputs, maxOutputs, chanceOutputs, minTicks, maxTicks);
        }

        Constraints needsFluid() {
            return new Constraints(maxItemInputs, minItemKinds, itemKinds, 1, maxFluidInputs, maxItemOutputs, maxFluidOutputs, maxOutputs, chanceOutputs, minTicks, maxTicks);
        }

        /** Can work on fluids alone, with no item at all. */
        Constraints fluidsAlone() {
            return new Constraints(maxItemInputs, 0, itemKinds, minFluidInputs, maxFluidInputs, maxItemOutputs, maxFluidOutputs, maxOutputs, chanceOutputs, minTicks, maxTicks);
        }

        /** Makes one thing, an item or a fluid. */
        Constraints oneOutput() {
            return new Constraints(maxItemInputs, minItemKinds, itemKinds, minFluidInputs, maxFluidInputs, maxItemOutputs, maxFluidOutputs, 1, chanceOutputs, minTicks, maxTicks);
        }

        public boolean takesDuration() {
            return maxTicks > 0;
        }
    }

    /**
     * The NBT a machine looks at. NBT alone tells the mod's own substances apart, so a machine that
     * ignores it can't take or make them; it can still take items, since every machine reads item
     * inputs as ingredients.
     */
    public enum Nbt {
        /** Matches it on fluid inputs and keeps it on results. */
        KEPT,
        /** Keeps it on results, but takes any fluid of the right kind. */
        ON_RESULTS,
        /** Ignores it: only other mods' fluids go in, and only their things or named dishes come out. */
        IGNORED;

        public boolean onFluidInputs() {
            return this == KEPT;
        }

        public boolean onResults() {
            return this != IGNORED;
        }
    }

    public boolean heated() {
        return this == CREATE_HEATED_MIXING;
    }

    static final class Ticks {
        /** The longest wait any machine is given: twenty minutes, a day and night in the game. */
        static final int LONGEST = 24000;

        private Ticks() {}
    }
}
