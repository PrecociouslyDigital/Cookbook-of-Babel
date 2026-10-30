package cookbookofbabel.minecraft.compat.jei;

import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.clock.Food;
import cookbookofbabel.minecraft.clock.Transition;
import cookbookofbabel.minecraft.dataset.Sensitivity;
import cookbookofbabel.minecraft.substance.Substance;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The kept-food clock: what a food becomes while it sits somewhere, and about how long it takes at
 * the step's reference conditions. The clock runs faster where its step says, and stops where its
 * temperature or water activity leaves the step's bounds, so the conditions ride along as text.
 */
class KeptFoodCategory implements IRecipeCategory<Transition> {
    static final RecipeType<Transition> TYPE = RecipeType.create(CookbookOfBabel.ID, "kept_food", Transition.class);
    /** The clock's own lang keys, shared with the tooltip: hours and days. */
    private static final String KEY = "tooltip." + CookbookOfBabel.ID + ".clock.";
    private static final long TICKS_PER_HOUR = 1000;
    private static final long TICKS_PER_DAY = 24 * TICKS_PER_HOUR;

    private final IDrawable background;
    private final IDrawable arrow;
    private final IDrawable icon;

    KeptFoodCategory(IJeiHelpers helpers) {
        var gui = helpers.getGuiHelper();
        background = gui.createBlankDrawable(120, 44);
        arrow = gui.getRecipeArrow();
        icon = gui.createDrawableItemStack(new ItemStack(Registration.PORTION.get()));
    }

    @Override
    public RecipeType<Transition> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(KEY);
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Transition transition, IFocusGroup focuses) {
        substances().ifPresent(substances -> {
            Food.stackOf(transition.input(), 1, substances)
                    .ifPresent(stack -> builder.addInputSlot(0, 0).addItemStack(stack));
            Food.stackOf(transition.output(), 1, substances)
                    .ifPresent(stack -> builder.addOutputSlot(102, 0).addItemStack(stack));
        });
    }

    @Override
    public void draw(Transition transition, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 46, 1);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, duration(transition.ticks()), 4, 24, 0x404040, false);
        conditions(transition.sensitivity())
                .ifPresent(c -> graphics.drawString(font, c, 4, 34, 0x808080, false));
    }

    @Override
    public List<Component> getTooltipStrings(Transition transition, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseY < 20) {
            return List.of();
        }
        var s = transition.sensitivity();
        var lines = new ArrayList<Component>();
        lines.add(duration(transition.ticks()));
        conditions(s).ifPresent(lines::add);
        activity(s).ifPresent(lines::add);
        return lines;
    }

    private Component duration(long ticks) {
        return ticks < TICKS_PER_DAY
                ? Component.translatable(KEY + "hours", (ticks + TICKS_PER_HOUR - 1) / TICKS_PER_HOUR)
                : Component.translatable(KEY + "days", "%.1f".formatted((double) ticks / TICKS_PER_DAY));
    }

    /** Where the step runs at all: its temperature bounds, else nowhere in particular. */
    private Optional<Component> conditions(Sensitivity s) {
        return Optional.ofNullable(range(s.minTempC(), s.maxTempC(), " °C")).map(Component::literal);
    }

    private Optional<Component> activity(Sensitivity s) {
        return Optional.ofNullable(range(s.minWaterActivity(), s.maxWaterActivity(), "")).map(range ->
                Component.translatable(KEY + "water_activity", range));
    }

    private static String range(Optional<Double> min, Optional<Double> max, String suffix) {
        if (min.isEmpty() && max.isEmpty()) {
            return null;
        }
        var low = min.map(KeptFoodCategory::trim).orElse("");
        var high = max.map(KeptFoodCategory::trim).orElse("");
        return low + "–" + high + suffix;
    }

    private static String trim(double value) {
        return "%.0f".formatted(value);
    }

    private static Optional<HolderLookup.RegistryLookup<Substance>> substances() {
        var level = Minecraft.getInstance().level;
        return level == null ? Optional.empty() : Optional.of(level.registryAccess().lookupOrThrow(Substance.REGISTRY));
    }
}
