# Kookboek — instructies voor agents

Android-app (Kotlin, Compose, Room). Je deelt een recept vanuit je browser, de app leest het
van de pagina en bewaart het offline. Eén module: `:app`, package `nl.potat04.kookboek`.

De uitleg voor mensen staat in [README.md](README.md). Dit bestand gaat over werken aan de code.

## Lees dit eerst

De build hangt aan een strak versietouwtje en de app is bewust klein gehouden. Voordat je iets
verandert aan de build of de opslag, lees de betreffende pagina in `.claude/knowledge/`:

| Onderwerp | Pagina |
|---|---|
| Versies, AGP 9 eigenaardigheden, waarom de build zo staat | [build.md](.claude/knowledge/build.md) |
| Lagen, datastromen, waar welke beslissing valt | [architecture.md](.claude/knowledge/architecture.md) |
| Hoe recepten van een pagina gelezen worden | [parser.md](.claude/knowledge/parser.md) |
| Room-schema, migraties, afbeeldingen | [data.md](.claude/knowledge/data.md) |
| De zes paletten, contrast-eisen, typografie, UX-regels | [ui.md](.claude/knowledge/ui.md) |
| Nederlands/Engels, en waarom er geen tekst in Kotlin staat | [localization.md](.claude/knowledge/localization.md) |
| Testen, emulator, de deel-flow echt uitproberen | [testing.md](.claude/knowledge/testing.md) |
| Signeren en een bundle uitbrengen | [release.md](.claude/knowledge/release.md) |
| Wat er eerder misging en hoe het opgelost is | [gotchas.md](.claude/knowledge/gotchas.md) |

## Harde regels

1. **Raak de versies in `gradle/libs.versions.toml` niet aan zonder [build.md](.claude/knowledge/build.md) te lezen.**
   AGP 9.1.1 brengt zijn eigen Kotlin 2.2.10 mee. De Compose-, serialization- en KSP-plugins
   moeten daar exact op aansluiten. Kotlin los updaten breekt de build gegarandeerd.
2. **Geen `org.jetbrains.kotlin.android` plugin toevoegen.** Die zit al in AGP en botst.
3. **Geen zichtbare tekst in Kotlin.** De app spreekt Nederlands en Engels: alles staat in
   `res/values/strings.xml` (Engels, en de fallback voor elke taal die de app niet heeft) en
   `res/values-nl/strings.xml`, sleutel voor sleutel gelijk. Ook foutmeldingen en
   `contentDescription`. Lever je een string, lever dan beide — `StringResourcesTest` faalt anders.
   Code, commentaar en commits zijn Engels. Zie [localization.md](.claude/knowledge/localization.md).
4. **`keystore.properties` en `*.jks` gaan nooit de repo in.** Staan in `.gitignore`. Niet
   opnemen in output, niet loggen, niet naar buiten sturen.
5. **Wijzig je een `@Entity`, dan hoort daar een migratie bij** en een opgehoogde
   `version` in `KookboekDatabase`. Zie [data.md](.claude/knowledge/data.md).
6. **De parser wordt getest tegen echte opgeslagen pagina's**, niet tegen zelfverzonnen HTML.
   Nieuwe site-ondersteuning = nieuwe fixture. Zie [testing.md](.claude/knowledge/testing.md).
7. **Eerst testen, dan pas installeren op een echt toestel.**

## Commando's

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:bundleRelease
```

## Waar wat staat

```
app/src/main/java/nl/potat04/kookboek/
  KookboekApp.kt        Application: bouwt repository + settings, houdt de app-brede scope
  MainActivity.kt       de app zelf (Compose, navigatie)
  ShareActivity.kt      het venstertje dat over je browser verschijnt bij Delen
  data/
    Recipe.kt           het domeinmodel — dit is wat de UI kent
    RecipeRepository.kt ophalen, importeren, verversen, verwijderen
    PageFetcher.kt      de HTML halen: eerst Jsoup, bij een botcontrole een WebView
    ChallengePage.kt    herkent een botcontrole, puur en getest tegen echte pagina's
    ChallengeStage.kt   waar die WebView aan het scherm hangt dat vooraan staat
    RecipeStore.kt      Room erachter, StateFlow ervoor
    ImageStore.kt       foto's downloaden, verkleinen, opruimen
    Settings.kt         welk palet, licht/donker, tekstgrootte
    SettingsStore.kt    SharedPreferences erachter, StateFlow ervoor
    db/                 entities, DAO, database, converters
  parse/
    RecipeParser.kt     JSON-LD > microdata > plugins > heuristiek
    Scaling.kt          porties omrekenen ("1½ el")
  ui/
    ChallengeOverlay.kt tekent de botcontrole waar PageFetcher doorheen werkt
    Labels.kt           getallen uit het model naar tekst in de gekozen taal
    UiText.kt           tekst die nog geen taal heeft (voor snackbars)
    Language.kt         de taalkeuze, via de LocaleManager van het platform
    SettingsScreen.kt   palet, licht/donker, tekstgrootte, taal
    theme/Palettes.kt   de zes paletten, contrast-gecontroleerd
    ...                 overige schermen en sheets
app/src/main/res/
  values/strings.xml    Engels — het ongekwalificeerde bestand, dus ook de fallback
  values-nl/strings.xml Nederlands, sleutel voor sleutel gelijk
app/src/test/           unit tests + opgeslagen pagina's als fixtures
app/schemas/            Room-schema, ingecheckt voor toekomstige migraties
```
