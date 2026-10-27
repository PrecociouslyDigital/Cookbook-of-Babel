---
description: "placeholder rule"
paths: 
  - "data/layers/*/*/*/recipes/**"
---
A recipe is a bit of a misnomer: it's a set of one or more Ingredients, undergoing a single Technique, that results in an output set of Ingredients (so the way Minecraft uses a Recipe than a cookbook does). Every transformation is normalized to a recipe; so bread going stale, for example, is a recipe, as is chocolate melting at a certain temperature, as well as bacterial and fungal spoilage. This is in service of downstream consumers being able to work with each step in their respective systems without needing to special-case anything they don't have to.
