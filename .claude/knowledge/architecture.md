# Architectuur

Eén module, geen DI-framework, geen use-case-laag. De app is klein en mag klein blijven.

```
ShareActivity ─┐                                          ┌─> RecipeStore ─> Room
               ├─> KookboekViewModel ─> RecipeRepository ─┤
MainActivity ──┘                                          └─> ImageStore ─> filesDir/images
       └───────────────────────────────> SettingsStore ─> SharedPreferences
```

## Wie doet wat

**`KookboekApp`** bouwt de `RecipeRepository` en de `SettingsStore`, en houdt één app-brede
`CoroutineScope` vast. Imports draaien in die scope, **niet** in een ViewModel. Het deelvenster mag
dichtgaan terwijl het ophalen nog loopt, zonder dat het recept verdwijnt.

**`SettingsStore`** zit naast de repository en niet erin. Het gaat over hoe de app eruitziet, niet
over recepten. Hij hangt aan de Application omdat beide vensters hem nodig hebben. Het
deelvenster boven je browser moet in hetzelfde palet opkomen als de app zelf. De activities lezen
hem rechtstreeks; hij hoeft niet door de ViewModel heen. SharedPreferences en niet DataStore, en
synchroon gelezen. Het zijn drie waarden en ze moeten er zijn vóór het eerste frame, anders is elke
koude start één frame in het verkeerde palet.

**`RecipeRepository`** is de enige plek die weet hoe je een recept binnenhaalt: URL normaliseren,
dubbele detecteren, pagina ophalen, parsen, opslaan, plaatje ophalen. Geeft een `ImportResult`
terug (`Saved` / `AlreadySaved` / `Failed`), nooit een exception naar de UI.

**`RecipeStore`** is Room erachter en een `StateFlow<List<Recipe>>` ervoor. Heeft ook
`loaded: StateFlow<Boolean>`, want een lege lijst betekent twee heel verschillende dingen:
"je hebt geen recepten" en "we hebben nog niet gekeken". De UI mag pas iets beweren als
`loaded` waar is.

**`RecipeParser`** is puur: HTML in, `ParsedRecipe` uit. Geen netwerk, geen Android. Daarom is
het als enige laag echt te testen. Zie [parser.md](parser.md).

**`Recipe`** (in `data/Recipe.kt`) is het domeinmodel en het enige wat de UI kent. De Room-entities
in `data/db/` zijn een opslagdetail; er lekt geen `RecipeEntity` naar boven.

## Er gaat geen taal naar beneden

De onderste lagen weten niet welke taal gekozen is, en horen dat ook niet te weten. Een recept
wordt jaren bewaard en gelezen in de taal van vandaag. Dus geeft de repository een
`FailureReason` terug in plaats van een zin, draagt een `Toast` een `UiText` in plaats van een
`String`, en staan de labelfuncties voor tijd en porties in `ui/Labels.kt` en niet op `Recipe`.
Alleen de getallen gaan de database in. Zie [localization.md](localization.md).

## Waarom het domeinmodel niet gelijk is aan de tabellen

`Recipe` heeft `ingredients: List<String>` en `checkedIngredients: Set<Int>`. In de database zijn
dat rijen met een `position` en een `checked`-vlag. De mapping zit in `data/db/`.

Dat is bewust. De opslag ging van JSON naar Room zonder dat de UI iets merkte. Wil je het
domeinmodel op de tabellen laten lijken (afvinkstatus ín de ingrediëntregel), dan is dat een
aparte, grotere verandering, en dan raak je elk scherm.

## Navigatie

`MainActivity` gebruikt `navigation-compose` met vier bestemmingen: bibliotheek, recept, bewerken,
instellingen.
`ShareActivity` is een losse activity met een doorzichtig thema (`Theme.Kookboek.Sheet`), zodat
het deelvenster over je browser zweeft in plaats van de hele app te openen.

## Wat er bewust niet is

- Geen server, geen account, geen analytics. De app praat alleen met de site die je deelt.
- Geen DI-container: één `Application` die drie objecten aanmaakt is genoeg.
- Geen aparte `domain`-laag: de repository ís de use case.
- Geen paging: honderden recepten passen prima in een `LazyColumn`.
