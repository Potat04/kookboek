# Signeren en uitbrengen

## Hoe het staat

De signing-gegevens komen uit `keystore.properties` in de projectroot:

```
storeFile=C:/Users/anton/.android-keystores/kookboek-release.jks
keyAlias=kookboek
storePassword=…
keyPassword=…
```

De keystore staat **buiten de repo**, in `~/.android-keystores/`. `keystore.properties`, `*.jks`
en `*.keystore` staan in `.gitignore`.

Ontbreekt `keystore.properties`, dan bouwt en test alles gewoon door — alleen `release` komt er
dan ongesigneerd uit. Iemand anders kan het project dus zonder sleutel gebruiken.

## Regels rond de sleutel

- **Nooit in de repo, nooit in output, nooit in een log of transcript.** Wie de sleutel heeft, kan
  updates uitbrengen alsof hij jij is.
- **Nooit vervangen of opnieuw genereren** zonder dat de eigenaar het expliciet vraagt. Raakt de
  sleutel kwijt, dan kan die app-installatie nooit meer geüpdatet worden — dan moet er een nieuwe
  `applicationId` komen en installeert iedereen opnieuw.
- De sleutel is 27 jaar geldig (10.000 dagen), zoals Play verlangt.
- Wachtwoorden staan alleen in `keystore.properties` op de machine zelf. Lees ze niet uit en
  echo ze niet.

## Bouwen

```bash
./gradlew :app:bundleRelease
```

Levert `app/build/outputs/bundle/release/app-release.aab` — het formaat dat Play wil.

Voor installeren op een toestel of testen op de emulator wil je een APK:

```bash
./gradlew :app:assembleRelease
```

## Voor elke uitgave

1. Hoog `versionCode` **en** `versionName` op in `app/build.gradle.kts`. Play weigert een bundle
   met een `versionCode` die al bestaat.
2. Draai de unit tests.
3. Test de **release**-build op de emulator, niet alleen debug. R8 en resource shrinking staan aan
   voor release; een ontbrekende keep-rule zie je pas ná minificatie.
4. Pas dan uploaden of installeren.

## R8

`isMinifyEnabled` en `isShrinkResources` staan aan. De keep-rules staan in
`app/proguard-rules.pro`. Room, Compose en kotlinx-serialization brengen hun eigen consumer-rules
mee; wat daar staat is voor wat dáár niet onder valt. Voeg je een library toe die reflectie
gebruikt, dan hoort daar een keep-rule bij én een test van de release-build.
