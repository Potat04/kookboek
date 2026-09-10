# De parser

`parse/RecipeParser.kt`. Puur: `parse(html, url) -> ParsedRecipe`. Geen netwerk, geen Android-API's.

Het ophalen zit hier niet in. Dat doet `PageFetcher`, die ook de botcontroles voor zijn
rekening neemt. Zie [fetching.md](fetching.md).

## Vier pogingen, beste wint

1. **JSON-LD**. `schema.org/Recipe` in `<script type="application/ld+json">`.
2. **Microdata**. Hetzelfde schema in `itemprop`-attributen.
3. **Bekende receptplugins**. WP Recipe Maker, Tasty Recipes, Mediavine Create.
4. **Head tags + heuristiek**. `og:title`/`og:image`, plus lijsten onder kopjes als
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
  `List<Step>`, waarbij de sectienaam bewaard blijft, tenzij álle stappen dezelfde sectie hebben,
  want dan zegt die niets meer.
- `HowToStep` met de echte tekst in `text` en `"Stap 1"` in `name`. `text` wint.
- ISO-durations met dagen erin: `P0DT0H30M`. En `PT0M` betekent "onbekend", niet "nul minuten".
- Het recept verstopt in een `@graph`, of in een array, of genest in een ander object.
- Kapotte JSON-LD. Wordt overgeslagen, de rest van de pagina gaat gewoon door.
- Nummering die de site zelf al in de stap heeft gezet (`"1. Snijd de ui"`) gaat eruit,
  want de app nummert zelf.

## Het vangnet op klasnamen

Vindt de parser geen plugin-markup, dan zoekt `looseList()` een container die zichzelf naar
ingrediënten of instructies vernoemt (`[class*=ingredient]`). Dat is een gok, en die gok stond
ooit los in `PLUGIN_INGREDIENTS`. Uitkomst: uitpaulineskeuken.nl zet `wprm-no-ingredients` op zijn
`<body>` om te zeggen dat de receptkaart leeg is, `body li` pakte daarna elke `<li>` op de pagina,
en het recept kreeg 216 ingrediënten uit het navigatiemenu.

Vandaar drie grenzen. `body` en `html` tellen niet als container. Een klasse die het woord
ontkent (`no-ingredient`, `without-ingredient`) telt niet als een belofte. En `itemTexts()` gooit
alles weg dat in `nav`, `header`, `footer`, `aside` of een menu-klasse zit, en laat een lijst
vallen zodra die boven `MAX_ITEMS` uitkomt. Geen recept telt tachtig dingen op.

## Lijsten in één alinea

Blogs van vóór de receptplugins typen de hele lijst in één `<p>`: het kopje vet, daarna een regel
per `<br>`. Die regels zijn tekstknopen, geen elementen, dus `listFollowing()` liep er dwars
overheen naar de alinea's erna en gaf de bereiding terug als ingrediëntenlijst.

`inlineLinesAfter()` leest ze wel. Twee of meer regels achter het kopje zijn de lijst. Staat er
één regel, dan is dat het eerste item en volgt de rest in de alinea's erna, wat precies is hoe
"Zo maak je het" op zo'n pagina geschreven staat.

## De porties-tekst

`descriptiveYield()` houdt alleen wat méér zegt dan een getal. "15 stuks", "1 loaf" en
"24 koekjes" zijn informatie die je niet moet weggooien. Een kale portie-telling ("4 persons",
"Serves 6", "4 porties") wordt weggegooid. Het getal staat al in `servings` en het scherm
verwoordt het in de taal die aan staat.

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
(` `, `​`, `﻿`) in plaats van letterlijke tekens. Dat is bewust. Onzichtbare
tekens in de source overleven kopiëren en plakken niet.
