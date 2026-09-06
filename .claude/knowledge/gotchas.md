# Wat er eerder misging

Een logboek van dingen die tijd hebben gekost, zodat het niet twee keer hoeft.

## Build

**`org.jetbrains.kotlin.android` toevoegen faalt.** AGP 9 heeft Kotlin ingebouwd; de plugin zit al
op de classpath "with an unknown version". Niet toevoegen. Compose- en serialization-plugin zijn
wél losse artefacten en moeten juist mét een expliciete versie aangevraagd worden, en wel dezelfde
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

**Een launcher-icoon volgt geen thema, een `activity-alias` wel.** Android kiest het app-icoon uit
de manifest voordat er code draait, dus tinten kan niet. Het gaat via één alias per palet met
precies één ingeschakeld; zie [ui.md](ui.md). Nul ingeschakeld haalt de app van je beginscherm af,
en de launcher cachet het icoon. Controleer met `cmd package resolve-activity`, niet met je ogen.

**Een alias uitzetten gooit de tasks weg die erin geworteld zijn.** Dit koste twee pogingen. Zet je
het alias om waarmee de lezer de app geopend heeft, dan verwijdert Android zijn hele task: de app
zakt naar de achtergrond en zijn kaart is uit het app-overzicht verdwenen. `DONT_KILL_APP` helpt
niet. Dat gaat over het proces, niet over de task. Het uitstellen tot de app van het scherm is
helpt óók niet, het verschuift alleen wanneer je het ziet (en dan schrik je in het overzicht).

De oplossing is dat het alias nooit de wortel van je task mag zijn: de aliassen wijzen naar
`LauncherRouter`, die met `taskAffinity=""` zijn eigen wegwerp-task krijgt en `MainActivity` met
`FLAG_ACTIVITY_NEW_TASK` in een eigen task zet.

Merk op waarom ik dit eerst niet zag: ik startte de app in mijn tests met
`am start -n .../.MainActivity`, en dán is de task in `MainActivity` geworteld en gebeurt er niets.
Start bij dit soort werk zoals een gebruiker start, met `monkey -p <pkg> -c android.intent.category.LAUNCHER 1`
of `am start -n <pkg>/.LauncherSinaasappel`, en controleer met `dumpsys window | grep mCurrentFocus`
waar je task op staat.

**Een `onClick`-parameter is geen `clickable`.** De palet-swatches op het instellingenscherm kregen
netjes een `onClick` doorgegeven die nergens aan een modifier hing. Het compileert, het ziet er goed
uit, en zes van de vier keuzes doen niets. Alleen zichtbaar door de app echt aan te tikken. Geen
test en geen review vond dit. Gebruik `selectable(selected, onClick)` voor één keuze uit een groep,
dan staat de semantiek er ook goed in.

**De parser bakte Nederlands in de database.** `localizeYield()` herschreef "4 servings" actief naar
"4 porties" vóór het opslaan, en `urlTitle()` had `"Recept"` als fallbacktitel. Prima toen de app
alleen Nederlands sprak; met een taalkeuze las een Engelse gebruiker "4 porties" op zijn eigen
recept. Zulke plekken zitten niet in de UI-laag, dus grep bij taalwerk óók door `parse/` en `data/`.
De tests legden het oude gedrag vast, dus die moesten mee.

## Deze machine

- **Python heeft geen werkende CA-bundle.** `urllib` valt over "certificate has expired" bij
  sites die prima zijn. Voor het ophalen van testfixtures is een unverified SSL-context de
  praktische uitweg. Speelt niet in de app zelf.
- **Geen Pillow.** Beeldbewerking in Python moet met de standard library (`zlib` + `struct` voor
  een PNG) of helemaal niet.
- **Er is één AVD: `kookboek`** (Android 36). Zie [testing.md](testing.md).
- **`ah.nl` blokkeert scrapers.** De opgeslagen fixture is een botblokkade-pagina en dient als
  test dat de app daar netjes mee omgaat in plaats van te crashen.

## De botcontrole van Cloudflare

Steeds meer receptsites antwoorden op een kaal verzoek met "Just a moment..." en geven de echte
pagina alleen aan iets dat het controlescript uitvoert.

**Een verzonnen `User-Agent` maakt het erger.** De app zette er een Chrome/125-string op terwijl de
WebView zijn eigen `Sec-CH-UA` client hints bleef sturen, en zo'n edge vraagt daar met
`Critical-CH` expliciet om. Een user agent die zijn eigen client hints tegenspreekt is een luider
botsignaal dan helemaal geen vermomming: met de string erop bleef de controle hangen tot de
time-out, eraf ging hij in twee seconden voorbij. `BrowserIdentity` vraagt daarom de WebView zelf
wat hij heet, en Jsoup en `ImageStore` sturen datzelfde. Ze moeten wel: Cloudflare koppelt de
`cf_clearance` aan de agent die hem verdiend heeft.

Test dit op de emulator tegen Chrome ernaast. Faalt Chrome op dezelfde pagina ook, dan ligt het aan
de emulator; komt Chrome er wel doorheen, dan ligt het aan jou.

**De WebView hoeft nergens aan te hangen.** Dat is hier eerst anders opgeschreven, en dat was fout.
Tijdens het zoeken naar de user-agent-bug leek het erop dat een losgekoppelde WebView geen frames
tekent en de controle daarom eeuwig doorloopt. Nadat de user agent klopte is dat opnieuw gemeten met
een WebView die aan niets hing: die kwam er gewoon doorheen. Mihon doet het ook zo — `createWebView`
is daar niet meer dan `WebView(context)` met instellingen en een user agent, zonder afmeting of
ouder. Eén oorzaak dus, niet twee. `ChallengeStage` en `ChallengeOverlay` blijven wél nodig, maar om
een andere reden: een controle die om een tik vraagt moet iemand kúnnen aanraken.

**Cloudflare zegt zelf wanneer het interactief wordt.** De challenge post een bericht
(`source: "cloudflare-challenge"`, `event: "interactiveBegin"`). Daar luisteren is beter dan op de
klok kijken: een trage automatische controle laat het scherm dan met rust, en eentje die een tik wil
komt meteen in beeld. Overgenomen van Mihon, dat er de bypass op afbreekt; wij laten de pagina zien.
De klok blijft als achtervang staan voor muren die niet van Cloudflare zijn.

**`cf-mitigated: challenge` is de officiële manier om een controle te herkennen**, en Cloudflare
documenteert dat ook zo. `ChallengePage` kijkt daar als eerste naar. In de body zoeken blijft nodig
voor de hop ervóór: een site kan er zijn eigen JavaScript-redirect voor zetten, zonder
Cloudflare-header.

## Kleur en contrast

**Één kleur droeg élke samenvatting.** `InkMuted` hing aan zowel `onSurfaceVariant` als `secondary`
en kwam op licht papier niet boven 5.6:1, en stond juist op de kleinste maten met de breedste
letterspatiëring. Contrast en formaat faalden op dezelfde plekken, en dat leest als "vaag" in plaats
van als "te klein" of "te licht". Als iemand zegt dat tekst slecht leesbaar is: kijk eerst welke
rol die tekst draagt en waar diezelfde rol nog meer opduikt.

**`tonalElevation` verft je kaart richting het accent.** Material tint een verhoogd `Surface` naar
`surfaceTint` (standaard `primary`). De deel-sheet stond op `tonalElevation = 3.dp` met
`color = surface`, dus die kaart was in werkelijkheid `#F9EEE4` en élke ratio erin lag een half punt
lager dan je op papier berekent. Reken tegen de echte kleur, of laat `tonalElevation` weg.

**De papierkorrel drukt het contrast plaatselijk omlaag.** `Paper.kt` legt vlekjes tot alpha 30/255
neer; onder de donkerste zakt lichte `background` naar ongeveer `#E6E1D7`. Krappe gevallen moet je
daartegen narekenen, niet tegen de vlakke achtergrondkleur.

**Eén `outline` kan geen kaart én geen tekstveld zijn.** Een gedrukte haarlijn wil flauw zijn
(~2:1); de rand van een besturingselement wil 3:1. Dat is niet één kleur. Zie
`ColorScheme.controlOutline` in [ui.md](ui.md).

## UX-bugs die al eens gemeld zijn

**Zwart scherm na verwijderen.** Het receptscherm bleef staan nadat het recept weg was, met alleen
de snackbar erover. Het detailscherm moet zichzelf sluiten zodra het recept dat het toont
verdwijnt, en dat mag pas als `loaded` waar is, anders sluit het al tijdens het opstarten.
