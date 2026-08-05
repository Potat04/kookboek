# Wat er eerder misging

Een logboek van dingen die tijd hebben gekost, zodat het niet twee keer hoeft.

## Build

**`org.jetbrains.kotlin.android` toevoegen faalt.** AGP 9 heeft Kotlin ingebouwd; de plugin zit al
op de classpath "with an unknown version". Niet toevoegen. Compose- en serialization-plugin zijn
wél losse artefacten en moeten juist mét een expliciete versie aangevraagd worden — dezelfde
versie als de Kotlin die AGP meebrengt (2.2.10).

**KSP compileert niet zonder `android.disallowKotlinSourceSets=false`.** AGP 9 weigert source sets
uit de `kotlin.sourceSets` DSL, en KSP registreert zijn gegenereerde code nog steeds zo. Zonder de
vlag in `gradle.properties` vindt de compiler Room's generated code niet.

**compileSdk 36 is niet genoeg.** `core-ktx:1.19.0` en `lifecycle:2.11.0` eisen API 37.

**Resource linking faalde op `Theme.MaterialComponents.*`.** Het template-thema erfde daarvan,
maar er zit geen `com.google.android.material` in het project. Opgelost door `themes.xml` te laten
erven van `android:Theme.Material.Light.NoActionBar`.

## Code

**Onzichtbare tekens in Kotlin-source overleven bewerkingen niet.** `RecipeParser.clean()` haalt
non-breaking spaces en zero-width spaces weg. Dat gebeurt met een regex met unicode-escapes
(` ` enzovoort), niet met letterlijke tekens in de source. Eerdere pogingen met echte
tekens gingen stuk bij het kopiëren.

**`List` heeft al `component1()` t/m `component5()`.** Zelf een `component4()` schrijven voor
destructuring geeft een redeclaratie-conflict.

## Deze machine

- **Python heeft geen werkende CA-bundle.** `urllib` valt over "certificate has expired" bij
  sites die prima zijn. Voor het ophalen van testfixtures is een unverified SSL-context de
  praktische uitweg. Speelt niet in de app zelf.
- **Geen Pillow.** Beeldbewerking in Python moet met de standard library (`zlib` + `struct` voor
  een PNG) of helemaal niet.
- **Er is één AVD: `kookboek`** (Android 36). Zie [testing.md](testing.md).
- **`ah.nl` blokkeert scrapers.** De opgeslagen fixture is een botblokkade-pagina en dient als
  test dat de app daar netjes mee omgaat in plaats van te crashen.

## UX-bugs die al eens gemeld zijn

**Zwart scherm na verwijderen.** Het receptscherm bleef staan nadat het recept weg was, met alleen
de snackbar erover. Het detailscherm moet zichzelf sluiten zodra het recept dat het toont
verdwijnt — en dat mag pas als `loaded` waar is, anders sluit het al tijdens het opstarten.
