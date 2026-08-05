# Kookboek

Een receptenboek op je telefoon. Vind je een recept in je browser, tik je op **Delen → Kookboek**,
en de app leest het recept van de pagina en bewaart het — tekst én foto — lokaal op je toestel.
Ook als de site later achter een paywall verdwijnt of offline gaat, staat het recept er nog.

Alles is offline. Geen account, geen server, geen tracking.

## Hoe je het gebruikt

1. Open een recept in Chrome (of welke browser dan ook).
2. Deelmenu → **Bewaar in Kookboek**.
3. Er verschijnt een klein venster over je browser: opgehaald, gelezen, klaar. Je blijft waar je was.

Werkt ook zonder browser: in de app op **Toevoegen** tik je om een link te plakken of zelf een
recept te schrijven.

## Hoe het recepten leest

`RecipeParser` probeert vier dingen, in volgorde van betrouwbaarheid, en houdt het beste resultaat:

1. **JSON-LD** — `schema.org/Recipe`, wat de meeste receptsites en foodblogs publiceren.
   Inclusief `@graph`, geneste arrays, `HowToSection` en instructies die als één HTML-blob komen.
2. **Microdata** — hetzelfde schema, maar in `itemprop`-attributen.
3. **Bekende receptplugins** — WP Recipe Maker, Tasty Recipes, Mediavine Create.
4. **Head tags + heuristiek** — `og:title` / `og:image`, en lijsten onder kopjes als
   "Ingrediënten" of "Bereiding".

Lukt niets, dan wordt de link alsnog bewaard met een titel uit de URL, en zegt de app dat eerlijk
in plaats van een leeg recept te tonen. Je kunt dan alsnog het origineel openen, opnieuw proberen
of het zelf invullen.

## Waar je spullen staan

- `databases/kookboek.db` — SQLite via Room, met drie tabellen:
  - `recipes` — de losse velden (titel, bron, tijd, porties, notities, favoriet).
  - `ingredients` en `steps` — één rij per regel, met `position` voor de volgorde en
    `checked` voor het afvinken. Verwijder je een recept, dan ruimt een foreign key met
    `ON DELETE CASCADE` de bijbehorende regels mee op.
- `filesDir/images/<id>.jpg` — de foto's, verkleind naar max ~1400px bij het importeren.

Een notitie schrijven of één regel afvinken raakt zo één rij, in plaats van de hele collectie
opnieuw weg te schrijven. Zoeken op ingrediënt is een join en geen scan door alles heen.

Het schema staat in `app/schemas/`, zodat een volgende versie een migratie kan schrijven tegen
iets wat vastligt.

Alles valt onder Android-backup, dus het verhuist mee naar een nieuw toestel.

## Bouwen en testen

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

De parsertests draaien tegen **echte, opgeslagen pagina's** in `app/src/test/resources/fixtures`
(leukerecepten.nl, cheffatty.com, 24kitchen.nl, bbcgoodfood.com, plus een pagina die geen recept
prijsgeeft). "Werkt op mijn zelfgeschreven HTML" zegt namelijk niets over het echte web.

## Wat het (nog) niet doet

- Geen eigen foto's kiezen bij een handgeschreven recept.
- Geen boodschappenlijst over meerdere recepten.
- Alleen `text/plain` in het deelmenu — dat is wat browsers sturen, en zo blijft Kookboek uit
  ieder ander deelmenu op je telefoon.
