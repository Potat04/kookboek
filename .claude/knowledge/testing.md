# Testen

```bash
./gradlew :app:testDebugUnitTest
```

Vijf testklassen, allemaal zonder Android-runtime:

- `RecipeParserTest` draait de parser tegen echte, opgeslagen pagina's.
- `ChallengePageTest` doet hetzelfde voor de botcontrole-herkenning, tegen twee bewaarde
  controlepagina's. Zie [fetching.md](fetching.md).
- `LauncherIconTest` leest de manifest: elk palet een alias, precies één aan, allemaal naar de
  router. Zie [ui.md](ui.md).
- `ScalingTest` rekent porties om.
- `StringResourcesTest` legt `values/` (Engels, de fallback) en `values-nl/` naast elkaar: dezelfde
  sleutels, hetzelfde soort, dezelfde meervoudsvormen, dezelfde placeholders. Die laatste is de
  enige die écht crasht op een toestel, en geen compiler ziet hem.

## Fixtures

`app/src/test/resources/fixtures/` bevat opgeslagen pagina's van leukerecepten.nl, cheffatty.com,
24kitchen.nl, bbcgoodfood.com en één pagina die helemaal geen receptdata prijsgeeft (ah.nl, die
een botblokkade teruggeeft). De test controleert dat de app daar alsnog netjes iets
bruikbaars van maakt.

Daarnaast staan er twee controlepagina's: `challenge-redirect.html`, een JavaScript-redirect
zonder Cloudflare-header, en `challenge-turnstile.html`. Ze zijn met een kale curl opgehaald
onder de user agent van de app, en alleen de hostnaam is eruit gehaald. `ChallengePageTest`
toetst beide kanten: deze twee moeten als controle herkend worden, en de vijf receptpagina's
juist niet.

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
unverified SSL-context acceptabel. Het is publieke HTML voor lokaal testmateriaal. In de app
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

**Start zoals een gebruiker start** als je aan het launcher-icoon of aan tasks werkt. Met
`am start -n .../.MainActivity` is je task in `MainActivity` geworteld en mis je precies de bug die
er dan zit:

```bash
adb -s emulator-5554 shell monkey -p nl.potat04.kookboek -c android.intent.category.LAUNCHER 1
```

Nakijken welk launcher-icoon aan staat. Doe dit en niet "kijken op het beginscherm": de launcher
cachet het icoon, dus visueel loopt het achter terwijl de instelling al om is.

```bash
adb -s emulator-5554 shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER nl.potat04.kookboek
```

En of er precies één alias aan staat. Nul betekent dat de app van het beginscherm verdwenen is:

```bash
adb -s emulator-5554 shell dumpsys package nl.potat04.kookboek | grep -A8 disabledComponents
```

Let op dat de reconcile bij het opstarten in een coroutine loopt: vraag je het direct na
`am start`, dan kun je de oude waarde nog zien.

De taal omzetten zonder te tikken. Dit is exact wat het instellingenscherm doet:

```bash
adb shell cmd locale set-app-locales nl.potat04.kookboek --locales en
```

**Geef altijd `-s` mee.** Er hangt vaak ook een echt toestel aan de USB. Valt de emulator om, en dat
gebeurt, dan kiest `adb` stilletjes het andere toestel en installeer je zonder het te merken op een
telefoon. Zoek eerst de serial op en gebruik die overal:

```bash
adb devices; E=emulator-5554; adb -s $E shell am start -n nl.potat04.kookboek/.MainActivity
```

Ziet een screenshot er onbekend uit, of verandert de resolutie? Check `adb devices` voordat je
verder gaat.

Let op bij `adb shell input`: een `swipe` die dicht bij de onderrand begint wordt door het systeem
als navigatiegebaar opgevat en gooit je uit de app. En een `tap` direct na `am start` of tijdens een
navigatie-animatie landt op het verkeerde scherm. Maak eerst een screenshot om te zien waar je
bent. Dat de paletkeuzes niet aanklikbaar waren, is precies zo aan het licht gekomen.

De instellingen staan in SharedPreferences en overleven `adb install -r`. Wil je een schone start
zonder je recepten te verliezen, zet ze dan terug via het instellingenscherm. `pm clear` gooit ook
de recepten weg.

Draait de release-build met R8 aan, test dan de **release-APK** en niet alleen debug. Een
kapotte keep-rule merk je pas als de app na minificatie crasht. Zie [release.md](release.md).

Zet de emulator na afloop weer uit (`adb emu kill`); hij blijft anders CPU en geheugen opeten.

## Volgorde

Eerst de emulator, dan pas een echt toestel.
