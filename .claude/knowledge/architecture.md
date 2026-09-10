# Architectuur

Eén module, geen DI-framework, geen use-case-laag. De app is klein en mag klein blijven.

```
                                                           ┌─> RecipeStore ─> Room
ShareActivity ──┐                                          │
OpenFileActivity├─> KookboekViewModel ─> RecipeRepository ─┼─> ImageStore ─> filesDir/images
MainActivity ───┘                                          │
       │                                                   └─> PageFetcher ─> Jsoup
       │                                                           │            of WebView
       │                                                           v
       └── ChallengeOverlay <───────────────────────────── ChallengeStage
       └────────────────────────────────> SettingsStore ─> SharedPreferences
```

## Wie doet wat

**`KookboekApp`** bouwt de `RecipeRepository` en de `SettingsStore`, en houdt één app-brede
`CoroutineScope` vast. Imports draaien in die scope, **niet** in een ViewModel. Het deelvenster mag
dichtgaan terwijl het ophalen nog loopt, zonder dat het recept verdwijnt.

**`SettingsStore`** zit naast de repository en niet erin. Het gaat over hoe de app eruitziet en
wat de lezer verder instelde (trillen, sortering, backupmap), niet over recepten. Hij hangt aan de Application omdat beide vensters hem nodig hebben. Het
deelvenster boven je browser moet in hetzelfde palet opkomen als de app zelf. De activities lezen
hem rechtstreeks; hij hoeft niet door de ViewModel heen. SharedPreferences en niet DataStore, en
synchroon gelezen. Het is een handvol waarden en ze moeten er zijn vóór het eerste frame, anders is
elke koude start één frame in het verkeerde palet.

**`RecipeRepository`** is de enige plek die weet hoe een import verloopt: URL normaliseren,
dubbele detecteren, de pagina bij `PageFetcher` opvragen, parsen, opslaan, plaatje ophalen.
Geeft een `ImportResult` terug (`Saved` / `AlreadySaved` / `Failed`), nooit een exception naar
de UI.

**`RecipeStore`** is Room erachter en drie `StateFlow`s ervoor: `recipes` (wat de lezer ziet),
`deleted` (zacht verwijderd, voor "onlangs verwijderd") en `labels` (de eigen labels, op
volgorde). Heeft ook `loaded: StateFlow<Boolean>`, want een lege lijst betekent twee heel
verschillende dingen: "je hebt geen recepten" en "we hebben nog niet gekeken". De UI mag pas
iets beweren als `loaded` waar is. De repository geeft alle drie door en voegt er niets aan toe.

Verwijderen is zacht: `delete` zet `deletedAt`, en `restoreDeleted`, `deleteForever` en
`purgeDeleted` doen de rest. Labels gaan via `createLabel`, `renameLabel`, `deleteLabel`,
`moveLabel` en per recept `setLabels(recipeId, labelIds)`. Een `Recipe` draagt zijn labels als
`List<Label>`; `upsert` schrijft die koppelingen mee, net als de ingrediënten. Zie
[data.md](data.md).

**`PageFetcher`** haalt de HTML op, langs twee wegen. Eerst een gewoon HTTP-verzoek met Jsoup.
Dat is wat bijna elke site krijgt en het blijft de eerste poging. Komt daar een botcontrole terug
(`ChallengePage` herkent die), dan gaat dezelfde URL naar een WebView, want een echte Chromium
komt er wél doorheen. De `cf_clearance` die de site daarna afgeeft blijft in de gedeelde
`CookieManager` staan, dus het volgende recept van diezelfde site gaat weer over gewoon HTTP en
`ImageStore` mag de foto ook ophalen. Zie [fetching.md](fetching.md).

**`ChallengeStage`** is het doorgeefluik tussen die WebView en het scherm dat vooraan staat.
Niet omdat de controle een venster nodig heeft, want dat is nagemeten en dat hoeft niet, maar
omdat een controle die om een tik vraagt iemand moet kunnen bereiken. Zie
[gotchas.md](gotchas.md).

**`RecipeParser`** is puur: HTML in, `ParsedRecipe` uit. Geen netwerk, geen Android. Daarom is
het, met `ChallengePage`, de laag die echt te testen is. Zie [parser.md](parser.md).

**`Recipe`** (in `data/Recipe.kt`) is het domeinmodel en het enige wat de UI kent. De Room-entities
in `data/db/` zijn een opslagdetail; er lekt geen `RecipeEntity` naar boven.

**`RecipeJson`** (in `data/RecipeJson.kt`) is het uitwisselformaat: de tekst in een backup en in
een los `.kookboek`-bestand. Eigen DTO's, niet `Recipe` zelf, want het opslagmodel mag blijven
schuiven terwijl een bestand van twee jaar geleden nog moet openen. Een envelop met `format`,
`version`, `exportedAt` en `recipes`; labels reizen als naam, want ids zijn lokaal. `decode`
geeft een `Result` terug en nooit een exception: onbekende sleutels worden genegeerd, een hogere
`version` wordt geweigerd met een `DecodeError.NewerVersion`. Zip en bestands-IO horen hier niet;
dat doet de laag erboven.

**`RecipeFile`** (in `data/RecipeFile.kt`) is die laag voor het losse bestand: een zip met
`recipe.json` (een `RecipeJson.Document`) en `images/<naam>` voor de foto's en de gefotografeerde
receptkaarten. Elke entry gaat door AES-256-GCM. De sleutel zit in de app, en dat kan niet anders:
beide telefoons moeten hem kennen en er is geen server om hem uit te delen. Het is dus
**versluiering, geen geheim** — het houdt andere apps weg, houdt een bestand uit een editor, en
zorgt dat wat de app terugleest is wat de app geschreven heeft. Zet er niets in wat erg zou zijn
als het gelezen werd. Geen Android in dit bestand, dus het formaat is gewoon op de JVM te testen
(`RecipeFileTest`).

**`Backup`** (in `data/Backup.kt`) ís die laag: `RecipeJson` plus de foto's in één zip, en terug.
Hij praat alleen met de `RecipeRepository`, nooit met de DAO, zodat een restore in dezelfde
molen valt als een import. `data/BackupFolder.kt` ernaast is het enige wat SAF spreekt.
Zie [data.md](data.md).

## Eén geplande taak

WorkManager (`androidx.work:work-runtime-ktx`) draait precies één ding: `BackupWorker`, elke 24 uur,
onder de unieke naam `auto-backup`. Dat is de reden dat de library er is — een back-up moet ook
gebeuren op de dagen dat niemand de app opent, en dat is nu net de dag dat je erachter komt dat je
er een nodig had.

`KookboekApp` plant hem in door naar de instellingen te kijken (`autoBackup` en `backupFolder`) en
niet vanuit het instellingenscherm. Zo landt ook een restore van de preferences op een nieuw
toestel bij de planner. Bij het opstarten draait er daarnaast een inhaalslag als de laatste
back-up ouder is dan een dag.

De worker zoekt de repository op via `KookboekApp`, want hij kan de reden zijn dat het proces
draait, en een tweede `RecipeStore` zou een tweede Room op hetzelfde bestand zijn. Hij wacht op
`repository.loaded` voordat hij schrijft: een lege lijst back-uppen over een goede back-up heen is
het enige wat deze functie echt fout kan doen.

## Er gaat geen taal naar beneden

De onderste lagen weten niet welke taal gekozen is, en horen dat ook niet te weten. Een recept
wordt jaren bewaard en gelezen in de taal van vandaag. Dus geeft de repository een
`FailureReason` terug in plaats van een zin, draagt een `Toast` een `UiText` in plaats van een
`String`, en staan de labelfuncties voor tijd en porties in `ui/Labels.kt` en niet op `Recipe`.
Alleen de getallen gaan de database in. Zie [localization.md](localization.md).

## Waarom het domeinmodel niet gelijk is aan de tabellen

`Recipe` heeft `ingredients: List<Ingredient>` (tekst plus een optionele groepskop, net als
`Step`) en `checkedIngredients: Set<Int>`. In de database zijn dat rijen met een `position` en een
`checked`-vlag. De mapping zit in `data/db/`. De parser levert nog kale regels; die worden
`Ingredient` in `ParsedRecipe.toRecipe()`.

Dat is bewust. De opslag ging van JSON naar Room zonder dat de UI iets merkte. Wil je het
domeinmodel op de tabellen laten lijken (afvinkstatus ín de ingrediëntregel), dan is dat een
aparte, grotere verandering, en dan raak je elk scherm.

## Navigatie

`MainActivity` gebruikt `navigation-compose` met zes bestemmingen: bibliotheek, recept, koken,
bewerken, instellingen en `deleted` (de prullenbak, vanuit de instellingen). `cook/{id}` komt
bovenop `recipe/{id}` te liggen, dus Terug (en het kruisje) zetten je terug bij hetzelfde recept.
Net als de andere bestemmingen met een id heeft het de `LeaveWhenGone`-wacht: verdwijnt het
recept onder je vandaan, dan loopt het scherm weg in plaats van leeg te blijven staan.
`ShareActivity` is een losse activity met een doorzichtig thema (`Theme.Kookboek.Sheet`), zodat
het deelvenster over je browser zweeft in plaats van de hele app te openen.

`OpenFileActivity` is de derde ingang, met hetzelfde thema en dezelfde belofte: iemand tikt een
`.kookboek`-bestand aan in zijn chat-app en krijgt een venstertje dat zegt wat erin zit. Er gaat
niets naar de database voordat er op "Toevoegen aan kookboek" getikt is; tot dan staan de foto's in
`cacheDir/incoming`. Hij luistert alleen naar het eigen mimetype (`application/vnd.kookboek`) en
naar de `application/octet-stream` waar sommige chat-apps op terugvallen. **Geen `text/plain`**: die
filter hoort bij `ShareActivity` alleen, anders staat Kookboek twee keer in elk deelmenu.

Naar buiten gaat het via `ui/Sharing.kt` (`Recipe.toShareText`, `Context.shareRecipeText`,
`Context.shareRecipeFile`, `Context.sharePage`) en `ui/PrintRecipe.kt`. Bestanden die een andere
app mag lezen gaan door een `FileProvider` met authority `<applicationId>.files`; welke mappen dat
zijn staat in `res/xml/file_paths.xml` en in `data/ShareFiles.kt`, en die twee moeten het eens
blijven. Alles daarin ligt in de cache: een kopie op weg naar buiten is geen kookboek.

## Wat er bewust niet is

- Geen server, geen account, geen analytics. De app praat alleen met de site die je deelt.
- Geen DI-container: één `Application` die een handvol objecten aanmaakt is genoeg.
- Geen aparte `domain`-laag: de repository ís de use case.
- Geen paging: honderden recepten passen prima in een `LazyColumn`.
