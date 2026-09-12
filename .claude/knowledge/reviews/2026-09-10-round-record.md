# Feature round 2026-09-10: what was built

Record of the round that implemented the [2026-09-06 review](2026-09-06-features.html). Read
this before the next feature review so nothing here comes back as "missing". The round landed
in master as PR #9 (squash, `e0bf138`); the follow-up fixes are on the branch
`round-2026-09-fixes`.

## Built

Major: backup file (zip with `recipes.json` and images, system file picker, merge by id),
daily automatic backup into a folder the reader picks with seven files kept (WorkManager plus a
catch-up at launch), share as text, the `.kookboek` file (encrypted JSON plus images, opened
with a view intent, "Already in there" with Replace), cook mode (one step per page, tap halves,
ingredients pull-up, landscape two columns), own photos and the photographed card as attachment,
tablet and landscape two-page recipe screen.

Medium: servings remembered per recipe, ingredients pull-up in the method, print or save as PDF,
honest specific failure reasons (offline, blocked, timed out, no recipe on the page), "Help
Kookboek read this site" (shares the kept HTML), made it (date stamp plus note line), refetch
warns when the recipe was hand-edited, ingredient groups (parser, editor, screen), timers handed
to the clock app.

Small and quality of life: everything in the review except the library filter chips (see below),
plus sort and favourites persistence, accent-insensitive search, launcher shortcuts (on the
aliases; debug build has its own copy of `shortcuts.xml` because of the `.debug` suffix), Back
clears the search, scroll position kept, selection bar shares several recipes, failed refetches
stay selected, haptic tick behind a setting, recently deleted with a thirty-day bin.

Schema is at version 2 with all columns and the labels tables already in place. The interchange
format is `RecipeJson` version 1.

## Deliberately not built

- **Sections of the book, the library layout.** The data layer (labels, `recipe_labels`,
  `Recipe.labels`, create/rename/delete/assign) exists and is unused by the UI. The persona
  round proposed a chip row; Anton rejected it as right in philosophy but not fun with a lot of
  data. Four clickable prototypes were built in an arena (chapters grid, shelves, thumb-index
  tabs, typographic index) and judged; they live on the branch `prototype/library-layouts`
  with the judge's report. Anton is reviewing. The filter chips that depend on the layout
  (labels, time, site, stacking, shuffle) wait with it.
- **Recipes that arrive as text** (review item 6): after the recipe editor rework.
- **The bonus list** (shopping list, unit conversion, importers, OCR): later.

## What the reviews and the smoke test caught

Two adversarial reviews over the merged diff found 23 issues, all fixed on the fixes branch:
among them a JSON-LD `ingredientGroups: []` wiping the ingredient list, unfiltered image names
in incoming files, the keep-screen-on flag being cleared when entering cook mode, and rotation
losing a new recipe or an edit in progress.

The emulator smoke test (AVD `Sandbox`) confirmed the version 1 to 2 migration keeps recipes,
ticks and notes, and found four more: an undo snackbar that never left (no duration), debug
shortcuts pointing at the release package, a dismissed add sheet still navigating to the fetched
recipe, and empty-state wording that assumed a search. Fixed on the fixes branch.

Not verified on a device: automatic backup firing from WorkManager after 24 hours, and the
`.kookboek` file arriving through WhatsApp.
