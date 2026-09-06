# UI en thema

Jetpack Compose met Material 3, maar het moet niet naar Material ruiken. Het uitgangspunt is
papier: warm, rustig, met inkt in plaats van kleurvlakken.

## Zes paletten, één idee

`ui/theme/Palettes.kt` bevat zes paletten, elk met een licht en een donker schema. **Sinaasappel**
is de standaard en het palet waarmee de app begon: crèmewit papier (`#F6F1E6`) met inkt (`#211D18`)
en terracotta als accent. Daarnaast **Olijf**, **Bosbes**, **Rabarber**, **Espresso** en **Inkt**.

Allemaal hetzelfde idee in een andere kleur: een vel papier, één inkt, één accent dat spaarzaam
gebruikt wordt, en kaarten met een gedrukte haarlijn in plaats van een slagschaduw. Licht is nooit
puur wit, donker is geen grijs maar een gedimde kamer die de kleurzweem van het palet houdt.

Er is **geen dynamic color**. De papier-identiteit is het punt van de app.

De keuze staat in `SettingsStore` (SharedPreferences) en komt via `data.Settings` bij
`KookboekTheme`. `ui/theme/Palettes.kt` is gegenereerd uit gecontroleerde data. Pas het met de
hand aan, maar reken dan na (zie hieronder).

## De contrast-eisen waar het om begonnen is

De oorspronkelijke klacht was dat de samenvattingen slecht leesbaar waren. Dat had één oorzaak:
`InkMuted` hing aan zowel `onSurfaceVariant` als `secondary`, en droeg daarmee élke meta- en
samenvattingsregel in de app, op 4.6:1 tot 5.6:1, afhankelijk van welk van de drie papiertinten
eronder lag. Klein formaat en lage contrast faalden op precies dezelfde plekken, en dat leest als
"vaag" in plaats van als "te klein".

Voeg je een palet toe, dan haalt het deze grenzen in **beide** schema's:

| Paar | Eis |
|---|---|
| `onSurface`/`onBackground` op hun eigen grond | ≥ 11:1 |
| `onSurfaceVariant` op `surface`, `background` **en** `surfaceContainerHigh` | ≥ 6:1 |
| `primary` op `background` en `surface` | ≥ 4.5:1 |
| `onPrimary` op `primary` | ≥ 4.5:1 |
| `onPrimaryContainer` op `primaryContainer` | ≥ 7:1 |
| `error` op `surface` en `background` | ≥ 4.5:1 |
| `outline` op `background` | ≥ 1.86:1 |
| `outlineVariant` op `surface` | ≥ 1.72:1, en altijd zwakker dan `outline` |
| `inverseOnSurface` op `inverseSurface` | ≥ 7:1 |
| `inversePrimary` op `inverseSurface` | ≥ 4.5:1 |

`surfaceContainerHigh` is in beide schema's het slechtste geval, en precies daar staan `Tag()`, de
portiestepper-subtekst en de placeholderletter van een ontbrekende foto. De container-ladder
(`surfaceContainerLowest` … `Highest`) moet monotoon lopen, en `surface` moet in beide schema's
lichter zijn dan `background`. Een kaart licht op, ook 's nachts.

Twee dingen die je niet ziet als je alleen naar de hexwaarden kijkt:

- **De papierkorrel telt mee.** `ui/theme/Paper.kt` legt zwarte vlekjes tot alpha 30/255 over de
  achtergrond. Onder de donkerste vlek zakt licht `background` naar ongeveer `#E6E1D7`. Reken de
  krappe gevallen daar tegen na, niet tegen de vlakke kleur.
- **`tonalElevation` verft mee.** Material tint een verhoogd `Surface` richting `surfaceTint`
  (standaard `primary`). De deel-sheet had `tonalElevation = 3.dp`, waardoor die kaart in
  werkelijkheid `#F9EEE4` was en élke regel erin een half punt contrast verloor. Dat is eruit; de
  sheet heeft zijn schaduw al.

## Twee soorten lijnen

`outline` is bewust flauw: het is een gedrukte lijn om een kaart. Maar de rand van een tekstveld is
het enige wat aangeeft *waar* het veld is, en daar vraagt WCAG 3:1 voor. Vandaar
`ColorScheme.controlOutline` in `Theme.kt`: `outline` gemengd naar de gedempte inkt, gemeten over
alle twaalf schema's tussen 3.3:1 en 6.2:1. Kaarten en scheidingslijnen houden `outline`, dingen
waar je in typt of op tikt krijgen `controlOutline`. Een leeg vinkje gebruikt `onSurfaceVariant`.

## Typografie

`ui/theme/Type.kt` is een **functie**: `kookboekTypography(scale)`. De schaal komt uit de
tekstgrootte-instelling (compact 0.92 tot extra groot 1.25) en vermenigvuldigt grootte én
regelhoogte, zodat het ritme van de pagina bij elke stand overeind blijft. Letterspatiëring
schaalt niet mee. Dat is een optische correctie op de vorm van de letter, niet op zijn grootte.

Serif voor wat je leest (titels, koppen), sans voor wat je aantikt. Bewust het systeem-serif: geen
lettertypedownloads, geen megabytes in de APK, en het ziet er meteen uit als een gedrukt kookboek.

Het is niet de Material-schaal. Material is gemaakt voor dichte lijst-UI's; deze voor een telefoon
die verderop op het aanrecht staat. Daarom is de onderkant een stap of twee groter dan je zou
verwachten en gaat er niets onder 12sp:

- `bodyLarge` 17sp/26sp voor ingrediënten en stappen. **Onaangeroerd**, dit is de maat waar niemand
  over klaagde.
- `bodyMedium` 16sp/24sp voor de langste gedempte alinea's: lege staten, foutuitleg, notities.
- `bodySmall` 14sp/20sp voor kaart-metaregel, byline, deel-samenvatting. Was 13sp, onder de
  Android-ondergrens voor ondersteunende tekst.
- `labelSmall` 12sp met 0.3sp spatiëring. Was 11sp met 0.8sp, ruim 7% van de em, en dat leest als
  uitgesmeerd in plaats van als klein.
- `labelLarge` 15sp/20sp voor knoppen. De regelhoogte staat er expliciet in: zonder valt Compose
  terug op de platform-metrics en zakt een label anders weg in een FAB dan in een chip.

De receptbeschrijving staat in **serif italic op `onSurface`**, niet in gedempte sans italic. Sans
italic op klein formaat is de dunste vorm in de app, en dit is een alinea die je léést. De
hiërarchie komt van de cursief en de serif, niet van het weghalen van contrast.

De kaart-metaregel bleef op `bodySmall`. `bodyMedium` is geprobeerd en teruggedraaid: het duwde
"15 stuks" voorbij de enkele regel en kapte de opbrengst eraf, en 16sp sans naast een 19sp serif
titel maakt van twee stemmen één.

## Vormen

`ui/theme/Shape.kt`. Kleine radii (3–18dp). Papier heeft geen zachte ronde hoeken; een kaart is
een geknipt vel.

`ui/theme/Paper.kt` legt een fijne korrel over de achtergrond, subtiel en alleen achter de content,
niet over foto's heen.

## Het opstartvenster

`themes.xml` kan maar één kleur noemen, en noemt die van het standaardpalet. Wie een ander palet
koos zou bij elke koude start een flits crèmewit papier zien. `Activity.paintWindowFor(settings)`
in `Theme.kt` lost dat op en moet **vóór `setContent`** aangeroepen worden. De flits gebeurt
voordat Compose draait, dus vanuit compositie is het te laat.

De statusbalk-iconen volgen het gekozen thema, niet de telefoon: zet je "Donker" op een telefoon
die in de lichtstand staat, dan moeten ze meekantelen. Dat doet `SystemBarIcons` in `Theme.kt`.
`ShareActivity` geeft `applySystemBars = false` mee, want die balk hoort bij de browser eronder.

**Wat hier niet mee op te lossen is.** Het systeem kiest het opstartvenster op basis van zijn eigen
`uiMode`, vóórdat er ook maar één regel app-code draait. Zet je "Donker" op een telefoon die in de
lichtstand staat, dan zie je bij een koude start dus een fractie crèmewit voordat de app zwart
wordt. `values-night/themes.xml` kan daar niet bij, want de keuze staat in onze preferences en niet
in de configuratie van het toestel. Elke app met een eigen donkerstand-schakelaar heeft dit; het
kost een leeg opstartvenster (en dus een merkbare stilte bij het starten) om het weg te halen, en
dat is de ruil niet waard. Wie de standaard "Volg de telefoon" laat staan, merkt er niets van.

## Het icoon in de launcher

Het logo *in* de app is een silhouet (`drawable/ic_logo.xml`, zwart) dat altijd via
`Icon(tint = …)` getekend wordt, dus dat volgt het palet gratis.

Het icoon op je beginscherm kan dat niet: Android leest dat uit de manifest, lang voordat onze code
draait. Daarom staat er een `activity-alias` per palet in de manifest, elk met zijn eigen
`ic_launcher_<palet>.xml`, en is er precies één ingeschakeld. `LauncherIcon.kt` wisselt om;
`KookboekApp` kijkt naar de palet-flow en houdt het bij, ook na een restore uit een back-up. Daar
komen de preferences wél mee terwijl de manifest-standaard nog aan staat.

**De aliassen wijzen naar `LauncherRouter`, niet naar `MainActivity`.** Dat is geen omweg maar de
kern van de zaak. Android gooit élke task weg die *geworteld* is in een component die je net hebt
uitgezet, en `DONT_KILL_APP` beschermt alleen het proces. Wijzen de aliassen rechtstreeks naar
`MainActivity`, dan staat de task waar de lezer in zit op het alias waarmee hij de app opende, en
gooit een andere kleur kiezen zijn eigen sessie weg: de app zakt naar de achtergrond en zijn kaart
is uit het overzicht verdwenen, precies alsof hij gecrasht was. Dit is echt gebeurd; het uitstellen
tot de app van het scherm is verschuift alleen het moment waarop je het merkt.

`LauncherRouter` heeft daarom `taskAffinity=""` (een eigen wegwerp-task, buiten het overzicht) en
start `MainActivity` met `FLAG_ACTIVITY_NEW_TASK`, zodat de task die je houdt in `MainActivity`
geworteld is waar geen alias bij kan. Hij tekent niets, draagt hetzelfde thema zodat het
opstartvenster niet verspringt, en gebruikt géén `CLEAR_TASK`, dus een bestaande sessie komt terug
zoals je hem verliet.

Vier dingen om te weten:

- **Er mag nooit nul ingeschakeld staan.** Dan verdwijnt de app van je beginscherm en kom je er
  alleen nog via de app-lijst in Instellingen. Vandaar dat het nieuwe alias áán gaat vóórdat de
  andere uit gaan, nooit omgekeerd. `LauncherIconTest` controleert dat elk palet een alias heeft,
  dat er precies één in de manifest aan staat, dat ze allemaal naar de router wijzen, dat de router
  zijn eigen task houdt, en dat `MainActivity` zelf geen launcher-filter meer heeft (twee filters =
  de app staat twee keer in je lijst).
- **De launcher cachet het icoon.** Na het omzetten kan het even duren of een herstart van de
  launcher vragen voordat je het ziet; `cmd package resolve-activity` vertelt je meteen wat er
  echt aan staat. Zie [testing.md](testing.md).
- **De achtergrondkleur is het lichte `primary` van dat palet**, en staat als
  `@color/launcher_<palet>` in `colors.xml`. Een launcher-icoon is één statisch plaatje, dus het
  kan het palet volgen maar niet licht/donker. Verander je een accent, verander het daar dan mee.
  Zet iemand "thematische iconen" aan in Android, dan wint de `monochrome`-laag en doet onze kleur
  niet meer mee. Dat is de bedoeling van die instelling.

## UX-regels die niet onderhandelbaar zijn

- **Nederlands en Engels, met Engels als terugvaloptie.** Zie
  [localization.md](localization.md). Geen zichtbare tekst in Kotlin-literals.
- **Nooit liegen over wat er gelukt is.** Kon de parser de ingrediënten niet vinden, dan zegt het
  scherm dat, met de knoppen om het origineel te openen, opnieuw te proberen of zelf in te vullen.
  Geen leeg recept dat doet alsof.
- **Een lege lijst is pas leeg als `loaded` waar is.** Anders flitst "je hebt nog geen recepten"
  voorbij bij het opstarten.
- **Het scherm blijft aan** op het receptscherm. Je handen zitten onder het deeg.
- **Verwijderen is altijd terug te draaien** via de snackbar, inclusief de foto. Die ene knop is de
  enige weg terug, dus hij mag nooit de zwakst leesbare tekst in de app zijn. Vandaar de
  expliciete `inverse*`-waarden.
- **Delen houdt je waar je was.** `ShareActivity` is een venstertje over je browser, geen
  volledige app-start.
- **Instellingen hebben geen Bewaren-knop.** Je kiest hoe iets eruitziet; het enige nuttige
  voorbeeld is het echte ding.
