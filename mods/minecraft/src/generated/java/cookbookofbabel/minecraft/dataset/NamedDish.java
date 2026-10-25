package cookbookofbabel.minecraft.dataset;

import cookbookofbabel.minecraft.substance.Nutrition;
import java.util.Optional;

/**
 * The dataset's named dishes, each registered as an item of its own. Generated from the
 * export; do not edit.
 */
public enum NamedDish {
    BAKED_OATMEAL("baked_oatmeal", "baked Oatmeal", Form.SOLID, Optional.empty()),
    BANGERS_AND_MASH("bangers_and_mash", "bangers and Mash", Form.MASH, Optional.of(new Nutrition(Optional.of(119d), Optional.of(4.7d), Optional.of(5.9d), Optional.of(11.2d)))),
    BASIC_SCONES("basic_scones", "basic Scones", Form.SLICE, Optional.of(new Nutrition(Optional.of(259.4450404114622d), Optional.of(5.586339823659074d), Optional.of(10.455117193240266d), Optional.of(34.54902645113887d)))),
    CROUTONS("croutons", "croutons", Form.PIECE, Optional.of(new Nutrition(Optional.of(448d), Optional.of(10d), Optional.of(15.9d), Optional.of(60d)))),
    DATE_SQUARES("date_squares", "date Squares", Form.SOLID, Optional.empty()),
    FILO_PUFF_PASTRY("filo_puff_pastry", "filo Puff Pastry", Form.SOLID, Optional.of(new Nutrition(Optional.of(651.6969405820639d), Optional.of(2.9544164926662035d), Optional.of(63.7078242389091d), Optional.of(16.538499703554773d)))),
    HASH_BROWNS("hash_browns", "hash Browns", Form.SOLID, Optional.of(new Nutrition(Optional.of(185d), Optional.of(2.4d), Optional.of(9d), Optional.of(21.9d)))),
    HOMEMADE_SEITAN("homemade_seitan", "homemade Seitan", Form.SOLID, Optional.of(new Nutrition(Optional.of(343.6363636363636d), Optional.of(10.988372093023255d), Optional.of(1.2986257928118397d), Optional.of(70.92494714587735d)))),
    NOODLES_FOR_SOUP("noodles_for_soup", "noodles for Soup", Form.SHRED, Optional.of(new Nutrition(Optional.of(140.6681484641468d), Optional.of(12.29574219115985d), Optional.of(9.672488004417486d), Optional.of(0.5315592961528418d)))),
    PANCAKE("pancake", "pancake", Form.SOLID, Optional.of(new Nutrition(Optional.of(290d), Optional.of(6.7d), Optional.of(7.4d), Optional.of(39d)))),
    PANCAKES_NORTH_AMERICAN("pancakes_north_american", "pancakes (North American)", Form.SLICE, Optional.empty()),
    POTATOES_ANNA("potatoes_anna", "potatoes Anna", Form.SLICE, Optional.of(new Nutrition(Optional.of(742d), Optional.of(0.6d), Optional.of(82d), Optional.of(0.6d)))),
    PUFF_PASTRY_I("puff_pastry_i", "puff Pastry I", Form.SLICE, Optional.of(new Nutrition(Optional.of(467.42053368268637d), Optional.of(7.770806040129628d), Optional.of(26.339378059711784d), Optional.of(49.141556919258086d)))),
    QUICK_PUFF_PASTRY("quick_puff_pastry", "quick Puff Pastry", Form.SOLID, Optional.of(new Nutrition(Optional.of(543.0000000000001d), Optional.of(5.8d), Optional.of(41.65d), Optional.of(35.80000000000001d)))),
    QUICK_TOMATO_NOODLE_SOUP("quick_tomato_noodle_soup", "quick Tomato Noodle Soup", Form.LIQUID, Optional.of(new Nutrition(Optional.of(181.59574468085106d), Optional.of(6.004091653027824d), Optional.of(8.563829787234042d), Optional.of(18.301800327332245d)))),
    RICE_WATER_PORRIDGE("rice_water_porridge", "rice Water Porridge", Form.MASH, Optional.empty()),
    SHORTBREAD("shortbread", "shortbread", Form.SLICE, Optional.of(new Nutrition(Optional.of(516d), Optional.of(5.6d), Optional.of(27.5d), Optional.of(60.6d)))),
    SNICKERDOODLES_IDAHO_DAILY_STATESMAN("snickerdoodles_idaho_daily_statesman", "snickerdoodles (Idaho Daily Statesman)", Form.DICE, Optional.empty()),
    STIR_FRIED_SHREDDED_POTATOES("stir_fried_shredded_potatoes", "stir-fried shredded potatoes", Form.SHRED, Optional.empty()),
    STIR_FRIED_TOMATO_AND_SCRAMBLED_EGGS("stir_fried_tomato_and_scrambled_eggs", "stir-fried tomato and scrambled eggs", Form.PIECE, Optional.of(new Nutrition(Optional.of(57.04965255993923d), Optional.of(4.120195949042847d), Optional.of(2.73016254322007d), Optional.of(3.6573849839580537d)))),
    STRACCIATELLA_II("stracciatella_ii", "stracciatella II", Form.CURD, Optional.empty()),
    SWEET_RICE_FRITTERS("sweet_rice_fritters", "sweet Rice Fritters", Form.SOLID, Optional.empty()),
    TEURGOULE_NORMAN_CINNAMON_RICE_PUDDING("teurgoule_norman_cinnamon_rice_pudding", "teurgoule (Norman Cinnamon Rice Pudding)", Form.GEL, Optional.empty()),
    THIN_WAFFLES("thin_waffles", "thin Waffles", Form.SOLID, Optional.of(new Nutrition(Optional.of(379.8885334443224d), Optional.of(6.278678488290495d), Optional.of(23.56059154902312d), Optional.of(35.35871159141835d)))),
    TUWO_ALKAMA_WHEAT_SWALLOW("tuwo_alkama_wheat_swallow", "tuwo Alkama (Wheat Swallow)", Form.PASTE, Optional.of(new Nutrition(Optional.of(113.03840000000001d), Optional.of(3.6146000000000003d), Optional.of(0.42718d), Optional.of(23.330600000000004d)))),
    WAFFLES("waffles", "waffles", Form.SOLID, Optional.of(new Nutrition(Optional.of(449d), Optional.of(6.3d), Optional.of(21d), Optional.of(53.3d))));

    public final String id;
    /** The English name, used when no translation is loaded. */
    public final String label;
    public final Form form;
    public final Optional<Nutrition> nutrition;

    NamedDish(String id, String label, Form form, Optional<Nutrition> nutrition) {
        this.id = id;
        this.label = label;
        this.form = form;
        this.nutrition = nutrition;
    }
}
