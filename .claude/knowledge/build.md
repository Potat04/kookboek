# Build

Gradle 9.3.1 · AGP 9.1.1 · Java 21 · compileSdk 37.1 · minSdk 33 · targetSdk 36

## Het versietouwtje

AGP 9 heeft **Kotlin ingebouwd**. `com.android.application` zet zelf al een Kotlin Gradle Plugin
op de classpath — welke versie dat is, staat in de POM van `com.android.tools.build:gradle`.
Voor AGP 9.1.1 is dat **Kotlin 2.2.10**.

Daaruit volgt alles:

| Wat | Versie | Waarom vast |
|---|---|---|
| Kotlin | 2.2.10 | dicteert door AGP; niet los te kiezen |
| `kotlin.plugin.compose` | 2.2.10 | moet gelijk zijn aan de Kotlin-versie |
| `kotlin.plugin.serialization` | 2.2.10 | idem |
| KSP | 2.2.10-2.0.2 | KSP-releases zijn aan één exacte Kotlin-versie gepind |
| Compose BOM | 2026.02.01 | past bij compose-compiler 2.2.10 |

Wil je Kotlin ophogen, dan gaat **AGP eerst**. Zoek daarna de nieuwe ingebouwde Kotlin-versie op:

```bash
python -c "import urllib.request,re; p=urllib.request.urlopen('https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.1.1/gradle-9.1.1.pom').read().decode(); print([d for d in re.findall(r'<dependency>(.*?)</dependency>',p,re.S) if 'kotlin-gradle-plugin' in d])"
```

En til daarna Compose-plugin, serialization-plugin en KSP in dezelfde stap mee.

## Drie valkuilen die er al ingelopen zijn

**1. `org.jetbrains.kotlin.android` toevoegen faalt.**

```
Error resolving plugin [id: 'org.jetbrains.kotlin.android', version: '2.x']
> the plugin is already on the classpath with an unknown version
```

Klopt: AGP heeft 'm al. Niet toevoegen. De Compose- en serialization-plugins zijn wél losse
artefacten en moeten juist mét versie worden aangevraagd.

**2. `android.disallowKotlinSourceSets=false` in `gradle.properties` is nodig.**

AGP 9 weigert source sets die via de `kotlin.sourceSets` DSL geregistreerd worden. KSP doet dat
nog steeds voor zijn gegenereerde code, dus zonder deze vlag compileert Room's generated code niet.
Weghalen mag pas als KSP overstapt op `android.sourceSets`.

**3. compileSdk moet 37 zijn.**

`androidx.core:core-ktx:1.19.0` en `lifecycle:2.11.0` eisen API 37. Bij 36 krijg je
"Dependency requires libraries and applications that depend on it to compile against version 37
or later". `targetSdk` mag op 36 blijven — dat is een andere knop en verandert runtime-gedrag.

AGP 9 schrijft compileSdk als een blok, niet als een getal:

```kotlin
compileSdk {
    version = release(37) { minorApiLevel = 1 }
}
```

De waarschuwing "not fully supported by this version of AGP" bij 37.1 is bekend en onschadelijk.

## Compose zonder Material Components

Er zit geen `appcompat` of `com.google.android.material` in het project. `res/values/themes.xml`
erft daarom van `android:Theme.Material.Light.NoActionBar`, niet van `Theme.MaterialComponents.*`.
Zet je die dependencies terug, dan pas je het thema aan — en niet andersom, want dat trekt een
heel View-framework binnen dat verder nergens voor gebruikt wordt.

## Signeren

De release-signing wordt gelezen uit `keystore.properties` in de projectroot. Bestaat dat bestand
niet, dan bouwt alles gewoon door en komt er alleen een ongesigneerde release uit. Zie
[release.md](release.md).
