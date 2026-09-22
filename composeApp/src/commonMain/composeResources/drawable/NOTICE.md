# Dish-type images — provenance

The 24 `dish_*.xml` files in this folder are **not photographs**. They are flat-colored
vector drawables authored for this repository (issue #196) as an interim, visually
distinguishable stand-in — one solid color per `dish-types` taxonomy term (#195/#198) — so
`RecipeImage` has something real to render instead of designing around a placeholder that
does not exist yet.

No external asset, photo library or third-party license is involved: each file is a
hand-generated `<vector>` with a single `<path>` filling the 24x24 viewport with one color.

A human should replace these with licensed or properly sourced photography per dish type
before this ships to users; when that happens, this file should be replaced with the real
source/license notes (one line per image), the same way `font/LICENSE-Inter.txt` documents
the bundled font.
