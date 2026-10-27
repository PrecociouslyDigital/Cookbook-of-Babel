package cookbookofbabel.minecraft.compat.brewinandchewin;

import cookbookofbabel.minecraft.clock.Exposure;
import cookbookofbabel.minecraft.clock.Holder;
import umpaz.brewinandchewin.common.block.entity.KegBlockEntity;

/** Brewin' and Chewin's keg: sealed, at the temperature its level stands for. Loaded only with the mod. */
public record KegHolder(KegBlockEntity keg) implements Holder {
    @Override
    public Exposure exposure() {
        var temperature = KegTemperature.ofLevel(keg.getTemperature()).orElse(KegTemperature.NORMAL);
        return new Exposure.Closed(temperature.typicalC);
    }
}
