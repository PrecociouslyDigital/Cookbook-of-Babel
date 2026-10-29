# Can the item clock be repurposed for melting and cooling?

Survey of the dataset (v1.2.0) after removing the mod's spoilage mapping,
asking whether there is enough temperature-driven state content to justify
reusing the clock as a melt/cool simulator instead of a spoilage one.

## What the dataset actually has

- **No melting states to speak of.** Exactly one ingredient is a melted
  state: `butter_heat` ("melted butter"), produced by ~200 `heat` prep
  steps and consumed by `mix`/`assemble` steps. There is no melted
  chocolate, no tempered chocolate, no hardened sugar, no `melt`
  technique at all (melting is folded into the machine-bound `heat`
  technique).
- **Temperature-triggered steps are vanishingly rare.** Of ~30,800
  recipes, only 14 end on `coreTemp` (13 are bake/fry doneness checks;
  the melt-adjacent one is `caramel.heat`, caramel at 170 °C core temp)
  and 24 on `both`. Everything else is time-based.
- **No warm-gated held steps.** Zero held-technique recipes with
  `minTempC >= 15` (nothing "only happens when warm", e.g. no proofing
  data). 424 held steps are cold-gated (`maxTempC <= 8`) — chilling and
  freezing that only proceed in a fridge or freezer.
- **No reverse transitions.** The "leave melted sugar out and it sets
  back" mechanic needs recipes like `melted caramel -> caramel` gated on
  cooling. None exist; states like caramel are reached by machine steps
  and never undone. Even `crystallize` has just 3 recipes (jaggery,
  cream of tartar, maple cream).
- **Frozen desserts don't melt in the data.** Ice cream and popsicles
  exist as recipe intermediates; their only kept-food transitions were
  the freezer-burn spoilage ones (now removed from the mod).

## What the clock's temperature machinery already does well

- Time × temperature-band held transitions: `cool` (736 steps), `chill`
  (377), `dry` (185), `ferment` (229), `age` (124), `freeze` (34),
  `stale` (319). The Q10 `Sensitivity` model (`Rate.java` /
  `Babel.Rate.timeAt`) plus temp-band gating is exactly the right shape
  for these — and they are the overwhelming majority of what the data
  offers.

## Verdict

Repurposing the clock as a **melt/cool simulator is not justified by the
current dataset** — there is essentially no content: one melted
ingredient, no reverse/setting recipes, no warm-gated steps. It would
require authoring a new mini-dataset by hand (melted/set state
ingredients, reverse transitions, warm-gated sensitivities).

The clock remains worth keeping for what the data does support: held
steps (cooling, chilling, freezing, drying, ageing, fermenting, and
staling-as-cooking). If melt/cool gameplay is wanted later, the work is
in the dataset first: add `melt`/`set` techniques with state ingredients
and warm-gated reverse transitions; the mod's `Transition` registry and
`StackClock` would then carry it with no structural change.
