# Testen

```bash
./gradlew :app:testDebugUnitTest
```

Twee testklassen, allebei zonder Android-runtime:

- `RecipeParserTest` — de parser tegen echte, opgeslagen pagina's.
- `ScalingTest` — porties omrekenen.

## Fixtures

`app/src/test/resources/fixtures/` bevat opgeslagen pagina's van leukerecepten.nl, cheffatty.com,
24kitchen.nl, bbcgoodfood.com en één pagina die helemaal geen receptdata prijsgeeft (ah.nl, die
een botblokkade teruggeeft — de test controleert dat de app dan alsnog netjes iets bruikbaars maakt).

"Werkt op mijn zelfgeschreven HTML" zegt niets over het echte web. Daarom draaien de tests tegen
wat sites daadwerkelijk uitleveren.

### Een nieuwe site toevoegen

1. Haal de pagina op en zet 'm in `app/src/test/resources/fixtures/<naam>.html`.
2. Kleed 'm uit: gooi `<style>`, `<svg>`, `<noscript>`, comments en elke `<script>` die géén
   `application/ld+json` is eruit. Dat scheelt een factor drie aan repo-gewicht en haalt niets weg
   wat de parser leest.
3. Schrijf een test die de titel, het aantal ingrediënten, het aantal stappen, de tijd en de
   porties vastlegt.
4. Pas dán de parser aan.

Let op bij het ophalen: de Python op deze machine heeft geen werkende CA-bundle, dus
`urllib` valt over verlopen certificaten. Voor het binnenhalen van een fixture is een
unverified SSL-context acceptabel — het is publieke HTML voor lokaal testmateriaal. In de app
zelf speelt dit niet: Android gebruikt zijn eigen truststore.

## De deel-flow echt uitproberen

Unit tests raken de parser, niet de flow eromheen. Voor dat laatste is er een emulator-AVD
`kookboek` (Android 36, Pixel-formaat).

```bash
"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd kookboek -no-snapshot-load
```

Wachten tot 'ie op is:

```bash
adb wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
```

Installeren en de deel-intent afvuren zoals een browser dat doet:

```bash
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://www.leukerecepten.nl/recepten/loempia-maken-in-de-airfryer/" -n nl.potat04.kookboek/.ShareActivity
```

Screenshot om te zien wat er staat:

```bash
adb exec-out screencap -p > screen.png
```

Draait de release-build met R8 aan, test dan de **release-APK** en niet alleen debug — een
kapotte keep-rule merk je pas als de app na minificatie crasht. Zie [release.md](release.md).

Zet de emulator na afloop weer uit (`adb emu kill`); hij blijft anders CPU en geheugen opeten.

## Volgorde

Eerst de emulator, dan pas een echt toestel.
