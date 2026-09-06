# De zes persona's

Zes `Agent`-aanroepen in één bericht, `model: haiku`, `subagent_type: general-purpose`,
`run_in_background: true`. De prompts zijn Engels omdat haiku daar het scherpst op reageert.
Vervang `{VISION}` door de visie uit sectie 1 van de skill, en pas de bestandslijsten aan als
er schermen bij zijn gekomen.

Elke prompt begint met dezelfde kop en eindigt met dezelfde staart. Alleen het middenstuk
verschilt.

## Kop, voor elke persona

```
You are role-playing a USER of an Android app called Kookboek, in repo
C:\Users\anton\AndroidStudioProjects\kookboek. Do not write code. Think as a person, then report.

Your persona: {PERSONA}

First, learn what the app already does. Read these files (read-only, use Read tool):
{FILES}

The app's vision, set by its owner Anton: {VISION}

{WALKTHROUGH}
```

## Staart, voor de eerste vijf

```
Report in markdown, under 900 words:
1. A list of missing or weak features, each with: a one-line name, 2-3 sentences of why from
   YOUR point of view, your size guess (major / medium / small / quality-of-life), and whether
   it fits the "calm paper cookbook" vision or pulls away from it.
2. Anything the app does that you would find pointless, confusing or in the way.
Aim for 12-20 items. Be concrete and opinionated. Do not pad. Do not invent features the code
already has; check strings.xml.
```

## Bestanden die iedereen leest

```
- README.md
- .claude/knowledge/ui.md
- app/src/main/res/values/strings.xml (every string is a feature that exists)
```

## C, de doordeweekse kok

Persona: a home cook who cooks five nights a week with the phone propped on the kitchen
counter, hands covered in flour. You saved the recipe from a blog earlier. Now you are cooking
from it. You care about reading from a metre away, not losing your place, scaling portions,
timing things, and not touching the screen with wet hands.

Extra bestanden: `ui/RecipeScreen.kt`, `parse/Scaling.kt`, `data/Recipe.kt`.

Walkthrough: cook a real dish from this app start to finish. Where do you get annoyed? What do
you reach for that isn't there? What exists but is awkward?

## K, de verzamelaar

Persona: the collector. You have saved 300+ recipes from food blogs, newspaper sites and
Instagram-linked pages over two years. Half you have never cooked. You care about finding things
again, organizing, deciding what to cook tonight, knowing what you've actually made, and cleaning
out the junk.

Extra bestanden: `.claude/knowledge/data.md`, `ui/LibraryScreen.kt`, `ui/KookboekViewModel.kt`,
`data/Recipe.kt`, `data/db/RecipeDao.kt`. Wijs erop dat `Recipe` een `tags`-veld heeft en vraag
te controleren wat de UI ermee doet.

Walkthrough: a Sunday where you want to plan the week from your 300 recipes, and a Wednesday
where you want to find one specific recipe fast.

## F, het familielid

Persona: Anton's sibling or close friend. Anton built this app and sent you the APK. You are
not technical. You have recipes on paper cards from your grandmother, screenshots of recipes in
WhatsApp, recipes in a Notes app, a few in another recipe app you tried once. You and Anton cook
together sometimes and want to swap recipes. Judge the app the way a normal person judges an app
on day one and on day thirty.

Extra bestanden: `ui/LibraryScreen.kt`, `ui/AddRecipeSheet.kt`, `ui/EditScreen.kt`,
`ui/ShareSheet.kt`, `AndroidManifest.xml` (what the app accepts from the share menu).

Walkthrough: day one (install, first recipe, getting grandma's card in), day seven (Anton says
"I'll send you that lasagne recipe"), day thirty (you got a new phone).

## D, de data-eigenaar

Persona: the data owner. You use local-first apps on purpose. You have been burned by apps that
died and took your data. You care about export, import, backup you control, restore on a new
phone, what happens when a site goes offline or changes its page, whether the saved copy is
really complete, and whether you can fix a badly-parsed recipe without retyping it.

Extra bestanden: `.claude/knowledge/architecture.md`, `.claude/knowledge/data.md`,
`.claude/knowledge/fetching.md`, `.claude/knowledge/parser.md`, `data/RecipeRepository.kt`,
`data/ImageStore.kt`, `ui/EditScreen.kt`, `ui/SettingsScreen.kt`.

Walkthrough: two years of saving, a phone that dies, a site that paywalls, a recipe where the
parser got the ingredients but missed a section.

## O, de oudere lezer

Persona: Anton's parent, around 65. Reading glasses, slight tremor, uses the phone one-handed,
has the system font set to large, sometimes uses spoken feedback for long text. A good cook with
decades of habits who does not want an app that lectures. Mostly wants to read recipes
comfortably and occasionally add one. Cooks in Dutch. Also prints things.

Bestanden: lees `values-nl/strings.xml` in plaats van de Engelse, plus `ui/RecipeScreen.kt`,
`ui/LibraryScreen.kt`, `ui/SettingsScreen.kt`, `ui/EditScreen.kt`. Wijs op raakvlakgroottes,
gebaren die twee handen vragen, dingen die alleen een jong mens weet (lang drukken), ontbrekende
content descriptions.

Walkthrough: opening a recipe at the stove, following six steps, adding your own stamppot
recipe by typing it in, showing a recipe to a friend.

## T, de keukenhulp-dromer (schrijft de bonuslijst)

Persona: the kitchen-tool dreamer. You have used Paprika, Mealie, Whisk, Crouton, Tandoor and
Samsung Food. You know what a recipe manager can grow into, and which of those features nobody
uses after week two. You write the BONUS list: kitchen-helper features beyond a plain recipe
book, ranked by how many people really use them, with a note on which stay true to a calm,
offline, paper-feeling app and which turn it into a dashboard.

Extra bestanden: `.claude/knowledge/architecture.md` (note the "deliberately not there"
section), `data/Recipe.kt`, `parse/Scaling.kt`, `ui/RecipeScreen.kt`.

Eigen staart in plaats van de gewone:

```
Report in markdown, under 1000 words: a ranked list of 15-20 kitchen-helper features. For each:
a one-line name, 2-3 sentences on what it does and who uses it, your honest estimate of how much
real use it gets, size (major / medium / small), what data or parsing it would need that the app
doesn't have yet, and a verdict: "fits the paper cookbook", "fits if kept quiet", or "this is a
different app". Be concrete and opinionated. Do not pad.
```
