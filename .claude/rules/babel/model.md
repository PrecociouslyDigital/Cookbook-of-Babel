---
description: "placeholder rule"
paths: 
  - "babel/src/Babel/Model.hs"
  - "babel/src/Babel/Model/**"
  - "babel/src/Babel/Codec.hs"
---

# The data model

The types in `Babel.Model.*` are the source of truth for the data format. The
JSON in `data/` is decoded through their codecs, and JSON Schema and the
Java/C#/TS/Python bindings will be derived from the same codecs. Design changes
start here, not in the JSON.

## Types

- Rule out invalid data in the types where you can: give a value with an
  invariant a newtype or enum whose smart constructor and codec reject bad
  input.
- Each entity kind has its own ID newtype, never a type alias, so an
  `IngredientId` can't be used where a `TechniqueId` is expected.
- Don't store anything that can be computed, such as whether an ingredient is
  an intermediate or what its spoilage paths are.
- Spoilage, melting and setting are recipes, not ingredient fields. An
  ingredient in a different state is a different ingredient: melted chocolate
  is not chocolate with a flag.

## Codecs

- A new record derives `stock (Generic)` and sets `codec = genericCodec`. Its
  field names become the JSON keys, and their declaration order is the
  canonical key order used by `Babel.Format`.
- `genericCodec` picks how to encode a field from the field's type:
  - `Maybe a` is an optional key;
  - a list, a `Map` or a `Bool` is omitted when it is empty or `False`;
  - `Role` is omitted when it is `Main`;
  - `Either a b` needs alternatives that differ in JSON shape;
  - anything else is a required key.
  For a different default, give the type its own `FieldCodec` instance.
- Write a codec by hand only when the JSON isn't a plain record. Today that is
  `Select` (`of` is a Haskell keyword), `Outcome`, `End`, `MeasureValue`, the
  validated wrappers (IDs, `Intensity`, `Version`, `TempBands`) and enums.
- Renaming or removing a field changes the JSON format. It is a breaking model
  change and needs a major version bump in `data/dataset.json`.

## Wiring

- Add a new model module to `exposed-modules` in `babel.cabal` and to the
  re-exports in `Babel.Model`.
- When the model changes, update `test/Gen.hs` so the generator covers the new
  shape while staying valid by construction. The codec properties then
  round-trip it.
