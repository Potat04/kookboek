---
name: smoke-test
description: Bouw Kookboek en probeer de deel-flow echt uit op de emulator. Gebruik dit wanneer er gevraagd wordt de app te draaien, een wijziging in het echt te controleren, of te testen of delen vanuit de browser nog werkt. Werkt met de debug- of de release-build.
---

# Kookboek op de emulator draaien

Unit tests raken de parser, niet de flow eromheen. Alles wat met delen, opslaan, navigatie of
de UI te maken heeft controleer je hier.

## 1. Bouwen

Draai altijd eerst de unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Dan de build die je wilt testen. Debug voor gewoon werk:

```bash
./gradlew :app:assembleDebug
```

Ging het over R8, keep-rules, de keystore of een uitgave, dan test je de **release**-APK.
Minificatiefouten zie je niet in debug:

```bash
./gradlew :app:assembleRelease
```

## 2. Emulator starten

Er is één AVD: `Sandbox` (Android 36). Controleer het met `emulator -list-avds` als het misgaat.

```bash
"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd Sandbox -no-snapshot-load
```

Start 'm op de achtergrond en wacht daarna tot hij op is:

```bash
adb wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
```

## 3. Installeren

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Voor release: `app/build/outputs/apk/release/app-release.apk`. Wisselt de signing tussen debug en
release, dan eerst `adb uninstall nl.potat04.kookboek`.

## 4. De deel-flow afvuren

Dit is precies wat een browser stuurt bij Delen → Kookboek:

```bash
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://www.leukerecepten.nl/recepten/loempia-maken-in-de-airfryer/" -n nl.potat04.kookboek/.ShareActivity
```

Goede testlinks, elk met een ander soort pagina:

- `https://www.leukerecepten.nl/recepten/loempia-maken-in-de-airfryer/` levert JSON-LD met secties
- `https://www.cheffatty.com/recipes/chili-crisp-scallion-oil-noodles` is Engels, met foto
- `https://www.ah.nl/allerhande/recept/R-R1197438/pasta-pesto-met-kip` blokkeert scrapers en
  moet netjes falen met een bewaarde link

De app zelf openen:

```bash
adb shell am start -n nl.potat04.kookboek/.MainActivity
```

## 5. Kijken wat er staat

```bash
adb exec-out screencap -p > screen.png
```

Lees de screenshot ook echt terug. "Geen crash" is niet hetzelfde als "het ziet er goed uit".
Controleer minstens: is de tekst Nederlands, klopt het aantal ingrediënten en stappen, staat de
foto er, en is de lege staat niet zichtbaar terwijl er wel recepten zijn.

Bij een crash of leeg scherm:

```bash
adb logcat -d -s AndroidRuntime:E RecipeRepository:W ImageStore:W RecipeStore:W
```

## 6. Opruimen

```bash
adb emu kill
```

De emulator blijft anders CPU en geheugen opeten.

## Volgorde

Emulator eerst, echt toestel daarna, en alleen als daarom gevraagd is.
