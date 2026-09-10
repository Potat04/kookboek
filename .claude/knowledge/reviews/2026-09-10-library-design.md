# Build spec: labels on the library screen

Chosen design from [2026-09-10-library-design.html](2026-09-10-library-design.html). Depends on
the labels data layer (`labels`, `recipe_labels`, `Recipe.labels: List<Label>`) built separately.
This spec covers only `LibraryScreen.kt` and `KookboekViewModel.kt`.

## Composables to touch

- **`FilterRow`** — unchanged.
- **`SelectionBar`** — add a third icon action, `onLabel`, between the count and refetch.
  Order stays: close, count, **label (new)**, refetch, delete. Delete stays rightmost and
  stays `error`-tinted; nothing about its position or colour changes.
- **`RecipeCard`** — unchanged. Do not draw labels on it; the card is at its width limit
  (see ui.md, the "15 stuks" note).
- **`NoMatches`** — add a third case, "nothing in this label", alongside the existing
  "nothing found" and "no favourites match" cases. Takes the active label's name.
- **`EmptyLibrary`**, **`Masthead`**, **`SearchField`**, **`Tag`** — unchanged.

## New composables

- **`LabelRow`** — `@Composable fun LabelRow(labels: List<LabelWithCount>, active: LabelFilter, onSelect: (LabelFilter) -> Unit, onMore: () -> Unit)`. A `LazyRow` of `FilterChip`s, same colours as the favourites chip (`selectedContainerColor = primaryContainer`, `selectedLabelColor = onPrimaryContainer`). Renders directly under `FilterRow`, only when `total > 0 && labels.isNotEmpty()`. Chip order: "All" (always), "Unlabelled" (only if any recipe has zero labels), then labels sorted by count desc/name asc, then "More…" (only if `labels.size > 6`).
- **`LabelPickerSheet`** — `ModalBottomSheet` opened from `SelectionBar`'s new label action. Shows existing labels as plain chips (tap to toggle "will add"), a divider, tag-derived suggestion chips (outlined/dashed style, distinct from real labels), a "+ New label" chip opening a text field, and a "Done" button. Add-only: never shows or clears a label some selected recipes already carry. On confirm, applies every toggled label to every selected recipe, clears selection, shows a toast.
- **`LabelsOverviewSheet`** — `ModalBottomSheet` opened from the LabelRow's "More…" chip. Lists every label, name + count, tallest first, each row tappable (acts like tapping its chip) with a rename (pencil) and delete (bin) action. A label at count 0 stays listed here (with "0") even though it drops out of `LabelRow`.
- **`ic_label.xml`** — new vector drawable for the selection bar's label action. `material-icons-core` (the only icons dependency in this app) has no tag/label/folder/bookmark glyph; do not add `material-icons-extended` for one icon. Follow the existing `ic_logo.xml` pattern: single tint-able path, drawn via `Icon(tint = ...)`.

## ViewModel changes

- `LabelFilter` — a small sealed type or nullable `Label?` plus a sentinel for "Unlabelled" (e.g. `sealed interface LabelFilter { object All; object Unlabelled; data class Specific(val label: Label) }`).
- `_activeLabel: MutableStateFlow<LabelFilter>` (default `All`), combined into `visible` the same way `_favouritesOnly` already is: another `.filter` stage, AND'd with the existing ones.
- `visible`'s `combine` call gains `_activeLabel` as an input.
- **Fix while you're in here:** `selected()` currently reads `visible.value.filter { it.id in _selection.value }`. Once a label chip can be tapped while recipes are selected, switching it can filter a selected recipe out of `visible`, silently dropping it from delete/refetch/label actions while it still shows as selected once the filter changes back. Change `selected()` to filter from `all.value` instead of `visible.value`.
- `labelSelected(recipes: List<Recipe>, labels: List<Label>)` — applies (adds) the given labels to the given recipes via the repository, clears selection, sends a toast.
- Label CRUD (rename/create/delete) goes through whatever the labels data layer exposes on `RecipeRepository`; not specified here.

## Strings needed

All new keys go in both `values/strings.xml` (English, the fallback) and `values-nl/strings.xml`
(Dutch), key-for-key, per the project's localization rule. No visible text in Kotlin.

| Key | English | Dutch |
|---|---|---|
| `library_label_all` | All | Alles |
| `library_label_unlabelled` | Unlabelled | Zonder label |
| `library_label_more` | More | Meer |
| `library_label_chip_format` | %1$s · %2$d | %1$s · %2$d |
| `library_selection_label` | Label selected | Geselecteerde labelen |
| `library_no_matches_label` | Nothing in '%1$s' | Niets in '%1$s' |
| `label_sheet_title` (plurals) | Add labels to 1 recipe / Add labels to %d recipes | Label toevoegen aan 1 recept / Labels toevoegen aan %d recepten |
| `label_sheet_suggestions_header` | Suggested from these recipes | Suggesties uit deze recepten |
| `label_sheet_new_chip` | + New label | + Nieuw label |
| `label_sheet_new_hint` | Label name | Naam van het label |
| `label_sheet_empty` | No labels yet. Type a name to make one. | Nog geen labels. Typ een naam om er een te maken. |
| `label_sheet_done` | Done | Klaar |
| `toast_labels_updated` | Labels updated | Labels bijgewerkt |
| `labels_manage_title` | All labels | Alle labels |
| `labels_manage_rename` | Rename label | Naam van label wijzigen |
| `labels_manage_delete` | Delete label | Label verwijderen |
| `toast_label_deleted` | '%1$s' deleted | '%1$s' verwijderd |

`toast_label_deleted` pairs with the existing `action_undo` string if the delete-with-undo
pattern turns out to be feasible for labels (see the HTML report); otherwise it is shown after
a confirm dialog instead, with no undo action.

## Interaction summary

1. Long-press a card → selection mode (unchanged). Tap the new label icon in `SelectionBar` →
   `LabelPickerSheet`. Toggle labels/suggestions, optionally type a new one, tap Done → labels
   applied, selection cleared, toast shown.
2. Tap a chip in `LabelRow` → `_activeLabel` updates → list filters in place. Tap the same chip,
   or "All", → clears back to the full list. Combines with search and favourites-only exactly
   as those two already combine with each other.
3. Tap "More…" in `LabelRow` → `LabelsOverviewSheet`. Tap a row → same as tapping its chip, sheet
   closes. Tap pencil/bin on a row → rename or delete that label.
4. No recipes labelled yet → `LabelRow` does not render at all. First discovery is the label icon
   appearing in `SelectionBar` the first time something is selected.

## Explicitly out of scope here

- Per-recipe label editing from `RecipeScreen` (add/remove one label on one recipe outside
  selection mode). Needed eventually, not part of the main-screen change.
- Multi-select (OR) label filtering. `LabelFilter` is single-valued by design; revisit only if
  single-select turns out to be limiting in practice.
- The landscape/tablet rail layout. One paragraph in the HTML report for whoever picks up the
  width-class work later; same `activeLabel` state, different presentation.
