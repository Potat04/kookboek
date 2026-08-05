# De parser

`parse/RecipeParser.kt`. Puur: `parse(html, url) -> ParsedRecipe`. Geen netwerk, geen Android-API's.

## Vier pogingen, beste wint

1. **JSON-LD** — `schema.org/Recipe` in `<script type="application/ld+json">`.
2. **Microdata** — hetzelfde schema in `itemprop`-attributen.
3. **Bekende receptplugins** — WP Recipe Maker, Tasty Recipes, Mediavine Create.
4. **Head tags + heuristiek** — `og:title`/`og:image`, plus lijsten onder kopjes als
   "Ingrediënten" of "Bereiding".

Levert stap 1 een volledig recept (ingrediënten én stappen), dan stopt het daar. Anders worden de
resultaten gescoord met `score()` (`ingrediënten + 2 × stappen`) en wint de beste. Zo kan een
pagina met halfbakken JSON-LD alsnog gered worden door de heuristiek.

**De parser geeft nooit niets terug.** Lukt alles niet, dan komt er een `LINK_ONLY`-recept met een
titel uit de URL-slug. De app zegt dat eerlijk in plaats van een leeg recept te tonen.

## Waar het echte werk zit

Het schema is in het wild veel rommeliger dan de documentatie doet vermoeden. Dit is allemaal
echt aangetroffen en wordt afgevangen:

- `image` als string, als array, als `ImageObject`, of als array van `ImageObject`.
- `recipeYield` als getal (`6`), als string (`"2 servings"`), of als array (`["15","15 stuks"]`).
  De beschrijvende variant wint, want "15 stuks" zegt meer dan "15".
- `recipeInstructions` als platte string, als HTML-blob in één string, als lijst van `HowToStep`,
  of als lijst van `HowToSection` met geneste `itemListElement`. Alles wordt platgeslagen naar
  `List<Step>`, waarbij de sectienaam bewaard blijft — tenzij álle stappen dezelfde sectie hebben,
  want dan zegt die niets meer.
- `HowToStep` met de echte tekst in `text` en `"Stap 1"` in `name`. `text` wint.
- ISO-durations met dagen erin: `P0DT0H30M`. En `PT0M` betekent "onbekend", niet "nul minuten".
- Het recept verstopt in een `@graph`, of in een array, of genest in een ander object.
- Kapotte JSON-LD. Wordt overgeslagen, de rest van de pagina gaat gewoon door.
- Nummering die de site zelf al in de stap heeft gezet (`"1. Snijd de ui"`) — die gaat eruit,
  want de app nummert zelf.

## De porties-tekst

`descriptiveYield()` houdt alleen wat méér zegt dan een getal: "15 stuks", "1 loaf", "24 koekjes"
— dat is informatie die je niet moet weggooien. Een kale portie-telling ("4 persons", "Serves 6",
"4 porties") wordt weggegooid; het getal staat al in `servings` en het scherm verwoordt het in de
taal die aan staat.

Dit deed vroeger het omgekeerde: `localizeYield()` herschreef "4 servings" naar "4 porties" vóór
het opslaan. Prima toen de app alleen Nederlands sprak, maar het zette één taal vast in de database
voor de hele levensduur van het recept. Zie [localization.md](localization.md).

Er staat sowieso geen Nederlands meer in deze laag: `urlTitle()` geeft een lege titel terug in
plaats van `"Recept"`, en het scherm vult "Naamloos recept" in.

## Iets aanpassen

Nieuwe site die niet werkt? Sla de pagina op als fixture en schrijf er een test bij, vóór je de
parser aanraakt. Zie [testing.md](testing.md). De verleiding om "even snel" een CSS-selector toe te
voegen zonder fixture is precies hoe deze laag onbetrouwbaar wordt.

Let op bij het bewerken van dit bestand: `clean()` gebruikt een regex met unicode-escapes
(` `, `​`, `﻿`) in plaats van letterlijke tekens. Dat is bewust — onzichtbare
tekens in de source overleven kopiëren en plakken niet.
