package cookbookofbabel.minecraft.substance;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.food.FoodProperties;

/** Turns nutrition into Minecraft's hunger and saturation. */
public final class Food {
    /** 100 g of bread, about 265 kcal, is a little over vanilla bread's 5 points. */
    private static final double KCAL_PER_HUNGER_POINT = 50;
    private static final int MAX_HUNGER = 20;
    /** Protein and fat keep you full; carbohydrate-only food gets the floor. */
    private static final float MIN_SATURATION = 0.3f;
    private static final float MAX_SATURATION = 0.9f;

    private Food() {}

    /** Unknown energy means not edible. */
    public static Optional<FoodProperties> of(Nutrition nutrition, double grams) {
        return nutrition.kcal().filter(kcal -> kcal > 0).map(kcal -> new FoodProperties.Builder()
                .nutrition(Mth.clamp((int) Math.round(kcal * grams / 100 / KCAL_PER_HUNGER_POINT), 1, MAX_HUNGER))
                .saturationMod(saturation(nutrition, kcal))
                .build());
    }

    private static float saturation(Nutrition nutrition, double kcal) {
        double fromProteinAndFat = 4 * nutrition.proteinG().orElse(0.0) + 9 * nutrition.fatG().orElse(0.0);
        float share = (float) Mth.clamp(fromProteinAndFat / kcal, 0, 1);
        return Mth.lerp(share, MIN_SATURATION, MAX_SATURATION);
    }
}
