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
- **De snelkoppelingen hangen aan de aliassen, niet aan de router.** Lang drukken op het icoon
  geeft "Favorieten" en "Link plakken" (`res/xml/shortcuts.xml`). Android leest
  `android.app.shortcuts` van het component dat MAIN/LAUNCHER beantwoordt, en dat is het
  ingeschakelde alias; `LauncherRouter` heeft zelf geen filter en zou dus niets opleveren, zonder
  dat je dat in een build of een log ziet. Vandaar dat alle zes de aliassen dezelfde
  `@xml/shortcuts` noemen: van palet wisselen verplaatst alleen wélk alias ze aanbiedt, en omdat de
  ids gelijk blijven overleeft een vastgezette snelkoppeling dat. Beide starten `MainActivity` met
  een extra die `Shortcuts.consume` één keer afleest (net als `EXTRA_OPEN_RECIPE`), en beide landen
  in de bibliotheek. `LauncherIconTest` controleert de meta-data en de inhoud van `shortcuts.xml`.
  Het icoontje ernaast is inkt op papier en volgt het palet niet: het is één statisch plaatje, net
  als het app-icoon, maar het kan geen alias per palet krijgen.
- **De achtergrondkleur is het lichte `primary` van dat palet**, en staat als
  `@color/launcher_<palet>` in `colors.xml`. Een launcher-icoon is één statisch plaatje, dus het
  kan het palet volgen maar niet licht/donker. Verander je een accent, verander het daar dan mee.
  Zet iemand "thematische iconen" aan in Android, dan wint de `monochrome`-laag en doet onze kleur
  niet meer mee. Dat is de bedoeling van die instelling.

## De botcontrole in beeld

`ui/ChallengeOverlay.kt` tekent wat `PageFetcher` op de `ChallengeStage` zet, en elk scherm dat
een import kan beginnen zet het bovenaan zijn content. Meestal is het niets: de WebView zit in
een doos van nul bij nul, vangt geen tikken weg en is toch op een echt formaat uitgemeten,
zodat hem zichtbaar maken geen relayout kost. Pas als de controle om een tik vraagt komt hij
naar voren, met een kop en een regel uitleg erboven. Zie [fetching.md](fetching.md).

## Papier dat echt papier is

`ui/PrintRecipe.kt` bouwt een HTML-pagina en laat een WebView buiten beeld hem afdrukken; de
systeemdialoog erachter doet ook "opslaan als PDF", dus dat is één weg voor twee dingen.

Die pagina is **zwarte inkt op wit**, en niet het gekozen palet. Een afdruk heeft geen
donkerstand, en crèmewit papier natekenen betekent een vel volspuiten om niets te zeggen. Serif
overal, een lijn onder de titel, en op breed papier de ingrediënten naast de bereiding. Het is de
enige plek in de app waar de paletregels bewust niet gelden.

## Wat de bibliotheek onthoudt

De sortering en de favorietenfilter staan de volgende keer nog zoals je ze zette
(`Settings.librarySort` en `favouritesOnly`; de ViewModel leest ze bij het opstarten uit de
`SettingsStore` en schrijft bij elke wijziging terug). De zoekopdracht juist níet: die gaat over het
ene ding waar je een minuut geleden naar zocht, en een kookboek dat morgen nog dichtgefilterd
openstaat leest als een leeg kookboek.

Terug wist eerst de zoekopdracht en laat het veld los; pas een tweede keer verlaat je het scherm.
Dat is dezelfde `BackHandler`-volgorde als bij de selectie, en die twee staan naast elkaar op de
bibliotheek-route in `MainActivity`.

De scrollpositie hoort bij de route, niet bij het scherm: `rememberLazyListState()` staat in
`composable("library")` en gaat als `listState` naar binnen. navigation-compose bewaart de saveable
state van een bestemming zolang die op de backstack staat, dus een recept openen en teruggaan komt
uit op dezelfde kaart.

**Zoeken kijkt niet naar accenten.** `data/Search.kt` heeft `foldForSearch`: NFD, combinerende
tekens eruit, kleine letters. `Recipe.searchBlob()` en de query gaan er allebei doorheen, dus
"creme" vindt "crème fraîche" en "jalapeno" vindt "jalapeño". Een letter die geen basis-plus-teken
is blijft heel ("ß", "ø"), want daar valt geen accent af te halen. Puur en getest
(`SearchFoldingTest`).

Twee dingen op een kaart die geen regel kosten. Een half gelezen import die nog nooit geopend is
(`quality == PARTIAL` en `openedAt == null`) krijgt een stipje in de hoek van de foto, weg zodra je
hem opent; de metaregel is één regel breed en elk woord daarop is al nodig. En de regel bij een
`LINK_ONLY`-recept staat in `onSurfaceVariant` en niet in `error`, met de actie erin ("alleen de
link, tik om aan te vullen"): dat de site zijn recept niet gaf is een toestand van de kaart, geen
fout van de lezer.

De eerste keer dat er twee of meer recepten staan komt er één gedempte regel onder het zoekveld die
zegt dat je een kaart kunt vasthouden. `Settings.holdHintSeen` gaat aan bij de eerste selectie en
daarna is hij weg. Lang drukken is het enige in de bibliotheek dat je niet kunt vinden door te
kijken.

## Meerdere recepten tegelijk

Lang drukken op een kaart in de bibliotheek begint een selectie; daarna voegt een gewone tik toe
en haalt weer weg. De kop wordt dan een balk met "N geselecteerd", een kruisje, en dezelfde acties
die één recept in zijn eigen menu heeft: opnieuw ophalen, delen als tekst, versturen als
Kookboek-bestand, en verwijderen. De plusknop gaat weg zolang je kiest, en Terug laat de selectie
los in plaats van het scherm.

Waarom juist deze: wie tien mislukte imports opruimt wil ze weggooien of alsnog goed ophalen, en
wie een stapel recepten naar zijn moeder stuurt wil dat in één keer. Alle vier staan ze ook op een
los recept, in dezelfde woorden, dus er is niets extra's te onthouden.

Ophalen en verwijderen staan op de balk; de twee manieren naar buiten zitten achter een
overloopmenu ernaast. Vijf knoppen plus de teller passen niet op 360dp bij "Extra groot", en de
teller is het deel dat leesbaar moet blijven. Verwijderen blijft rechts staan.

**Wat mislukte blijft geselecteerd.** Na "opnieuw ophalen" wordt de selectie niet leeggemaakt maar
teruggebracht tot de recepten waarbij het misging, en de snackbar zegt "3 bijgewerkt, 2 mislukt en
nog geselecteerd". Een getal waar je niets mee kunt is geen bericht: zo tik je nog eens op ophalen
of gooi je die twee weg, zonder ze tussen vijftig kaarten terug te zoeken.

De selectie leest uit álle levende recepten, niet uit wat de lijst laat zien. Kies er vijf, typ dan
iets in de zoekbalk of zet Favorieten aan, en wat uit beeld schuift blijft gekozen. Uit de zichtbare
lijst lezen liet die stilletjes vallen bij het verwijderen, en dat is precies wat een selectie nooit
mag doen.

Het vinkje staat op de foto, niet naast de tekst. Naast de tekst pakt het de breedte af die
"15 stuks" nodig heeft en dan valt de opbrengst weg achter een beletselteken.

De selectie leeft in `KookboekViewModel` als een verzameling ids, niet als recepten. De lijst
eronder beweegt namelijk: opnieuw ophalen vervangt een recept in zijn geheel, en een vastgehouden
kopie zou daarna naar iets wijzen dat er niet meer is.

Opnieuw ophalen gaat één voor één. Er is maar één `ChallengeStage`, dus meerdere WebViews
tegelijk zouden om die ene plek vechten. Zie [fetching.md](fetching.md).

## Kookstand

Hetzelfde recept heeft twee lezingen. Op de bank lees je een document: foto, inleiding, alles
onder elkaar. Bij het fornuis wil je één ding weten en dan je handen weer vrij hebben. Daarom is
er naast `RecipeScreen` een `ui/CookScreen.kt`, te bereiken via de knop in de kop van *Bereiding*
en de route `cook/{id}`.

Eén stap per pagina in `headlineMedium` (serif, en dus mee-schalend met de tekstgrootte), de
groepskop van die stap klein erboven, en verder alleen een teller "3 / 9" en een kruisje. Tikken
op de rechterhelft is verder, links is terug, vegen doet hetzelfde; de eerste keer staat er
onderaan een regel die dat zegt, en die verdwijnt zodra je het één keer gedaan hebt. De helften
liggen ónder de tekst en luisteren als ouder mee, zodat een aangetikte tijdsduur (`DurationText`)
en het vinkje hun tik houden en de rest van het scherm doorpakt.

De ingrediënten komen als sheet omhoog en laten de stap staan; ligt de telefoon dwars, dan staan
ze als kolom links en is er niets om te trekken. Ze zijn omgerekend naar `cookedServings` en de
vinkjes zijn dezelfde als op het receptscherm. Een stap afvinken blijft een bewuste tik: automatisch
afvinken bij het doorbladeren zou de stap aankruisen waar je alleen even naar vooruit keek.
## Het receptscherm

Staand op een telefoon is `ui/RecipeScreen.kt` één `LazyColumn` met sleutels per item (`"bar"`, `"head"`, `"method"`,
`"step:3"`, `"notes"`). Die sleutels zijn niet alleen voor Compose: het scherm leest eraan af
waar je bent. Staat het eerste zichtbare item in de bereiding, dan verschijnt rechtsonder een
klein pilletje "Ingrediënten" dat een `ModalBottomSheet` opent (`ui/IngredientsSheet.kt`) met
dezelfde omgerekende, afvinkbare lijst. Gesloten staat het nergens voor; scroll je terug naar
boven, dan is het weg.

De ingrediëntregels zelf staan in `ui/IngredientLines.kt` en worden door het scherm én de sheet
gebruikt, zodat een groepskop, een vinkje en een omgerekende hoeveelheid op beide plekken
hetzelfde doen. "Afgevinkte verbergen" op de kop klapt de aangevinkte regels in tot één regel
"3 klaargezet"; de rest houdt zijn volgorde, en een groep waarvan alles is afgevinkt verliest
ook zijn kopje. Groepskoppen (`GroupHeading`) zijn voor ingrediënten en stappen dezelfde
`titleMedium` in `primary`.

**Is er breedte, dan ligt het boek open.** Hetzelfde recept op twee pagina's, met de balk en de
kop (foto, titel, byline) erboven, daaronder links de ingrediënten en rechts de bereiding met de
notities eronder. Elke kolom scrollt zelf. `ui/WindowWidth.kt` beslist dat uit niets dan het venster: twee pagina's vanaf
600dp breed, of vanaf 480dp als het venster breder is dan hoog. Puur en getest
(`PageShapeTest`), dus er hoeft geen window-size-class-bibliotheek bij voor één boolean.

Ligt de telefoon dwars, dan wordt de foto een vierkantje náást de titel in plaats van een band
erboven, en gaat de titel van `displaySmall` naar `headlineMedium`. Elke centimeter bovenaan
gaat namelijk van beide pagina's tegelijk af. De tags (tijd, opbrengst, sitetags) staan dan bij
de ingrediënten en de inleiding boven de bereiding. Dat houdt de kop kort en zet proza bij proza.
Het pilletje en de sheet zijn weg, want de ingrediënten staan al in beeld.

Twee dingen die het tegenhouden. Een recept zonder ingrediënten blijft één kolom, want een lege
halve pagina leest als een storing. En de kop is boven de helft van de vensterhoogte scrollbaar,
zodat een lange titel op "Extra groot" de pagina's er niet af duwt; bij een normale maat valt er
niets te scrollen.

De onderdelen zelf (`StepRow`, `CheckLine`, `GroupHeading`, `ServingsStepper`, `NotesField`,
`Byline`, de ingrediënt- en bereidingsblokken) worden één keer opgebouwd en aan beide lezingen
uitgedeeld. Staand verandert er dus niets, en er is geen tweede kopie die kan gaan afwijken.

Wat het scherm verder onthoudt en doet:

- **De stepper begint waar je hem achterliet.** `cookedServings` is de laatste stand; het getal
  van de pagina blijft de basis waar vanaf omgerekend wordt, en de regel "omgerekend vanaf 4"
  blijft staan. Staat de stepper weer op het origineel, dan schrijft de ViewModel `null`, zodat
  een opnieuw opgehaalde portie-telling niet onder een oude stand blijft zitten.
- **"Gemaakt"** (in het menu en stil onderaan de bereiding) zet `lastCookedAt` op nu en neemt
  één regel mee die met de datum onder de notities komt (`ui/CookedNote.kt`, puur en getest).
  Onder de byline staat dan "Laatst gemaakt op 10 sep. 2026". Geen sterren, geen teller.
- **Opnieuw ophalen vraagt eerst** als `editedAt` gezet is. Dat is het enige op dit scherm dat
  de snackbar niet ongedaan kan maken. Zonder eigen aanpassingen gaat het meteen.
- **Verwijderen vraagt niks meer.** De dialoog is weg, om dezelfde reden als bij de selectie in
  de bibliotheek; de snackbar is de weg terug.
- **Tijden in een stap zijn timers.** `DurationText` onderstreept "20 minuten", een tik zet via
  `Context.startTimer` een timer in de klok-app zonder die te openen. Is er geen klok-app, dan
  zegt een snackbar dat (`vm.notify(UiText)`), in plaats van niets te doen.
- **Een tag of de sitenaam is een zoekopdracht.** `onSearch` zet de query in de ViewModel en
  gaat terug naar de bibliotheek. Alle tags worden getoond, in een `FlowRow`.
- **Ingrediënten kopiëren** zet de omgerekende lijst met groepskoppen op het klembord, één regel
  per ingrediënt (`ui/IngredientsText.kt`, puur en getest).
- **Foto's gaan open op een tik**: `ui/FullScreenPicture.kt` is een `Dialog` over alles heen,
  knijpen zoomt, slepen schuift, een tik sluit. Een gefotografeerd receptkaartje
  (`attachmentFile`) staat heel, niet bijgesneden, onder de notities en opent op dezelfde manier.
- **Voorbereiding en koken apart** waar de pagina beide gaf: "15 min voorbereiden, 40 min koken"
  (`timeDetailText()` in `Labels.kt`). De kaart in de bibliotheek houdt het totaal; die heeft
  één regel.
- **De plus en min van de stepper zijn getekend**, op de hoogte van de cijfers ertussen. Een
  typografische min is een derde van het getal en leest als een vlekje vanaf het aanrecht.
- **"Bewaard"** verschijnt even onder de notities nadat de autosave gevuurd heeft, in een vakje
  met vaste hoogte zodat de pagina niet verspringt.
- **Tekst in stappen en ingrediënten is te selecteren** (`SelectionContainer`); een korte tik
  vinkt nog steeds af, want de selectie claimt de aanraking pas als hij lang wordt.
- **Een vinkje trilt kort** (`HapticFeedbackType.Confirm`) als `Settings.hapticFeedback` aan
  staat; het scherm krijgt dat als `haptics: Boolean`.
- **`markOpened`** loopt één keer bij het openen; de repository zet `openedAt` alleen als hij
  nog leeg is.

Het overloopmenu en de sectiekoppen zijn geordende lijsten (`MenuEntry`, `HeaderAction`), niet
geneste `if`s: wie er iets aan toevoegt, voegt één regel toe.

## UX-regels die niet onderhandelbaar zijn

- **Nederlands en Engels, met Engels als terugvaloptie.** Zie
  [localization.md](localization.md). Geen zichtbare tekst in Kotlin-literals.
- **Nooit liegen over wat er gelukt is.** Kon de parser de ingrediënten niet vinden, dan zegt het
  scherm dat, met de knoppen om het origineel te openen, opnieuw te proberen of zelf in te vullen.
  Geen leeg recept dat doet alsof.
- **Een lege lijst is pas leeg als `loaded` waar is.** Anders flitst "je hebt nog geen recepten"
  voorbij bij het opstarten.
- **Het scherm blijft aan** op het receptscherm en in de kookstand. Je handen zitten onder het
  deeg.
- **Verwijderen is altijd terug te draaien** via de snackbar, inclusief de foto. Die ene knop is de
  enige weg terug, dus hij mag nooit de zwakst leesbare tekst in de app zijn. Vandaar de
  expliciete `inverse*`-waarden.
- **Delen houdt je waar je was.** `ShareActivity` is een venstertje over je browser, geen
  volledige app-start.
- **Een botcontrole komt alleen in beeld als hij een tik nodig heeft.** De rest gebeurt buiten
  het zicht, in een seconde of twee. Een browser die zomaar over het deelvenster klapt zou
  precies de belofte breken die de regel hierboven maakt.
- **Terug doet eerst het kleinste ding.** Op de bibliotheek laat Terug de selectie los, of anders
  de zoekopdracht, en pas als er niets meer los te laten is verlaat het het scherm.
- **Ook een selectie verwijderen is terug te draaien.** Eén snackbar, één Ongedaan maken, alles
  terug. Een dialoog vooraf is er niet: die vraagt om bevestiging op het moment dat je het het
  zekerst weet, en helpt niet op het moment daarna.
- **Instellingen hebben geen Bewaren-knop.** Je kiest hoe iets eruitziet; het enige nuttige
  voorbeeld is het echte ding.
