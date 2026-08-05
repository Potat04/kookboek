# Architectuur

Eén module, geen DI-framework, geen use-case-laag. De app is klein en mag klein blijven.

```
ShareActivity ─┐
               ├─> KookboekViewModel ─> RecipeRepository ─┬─> RecipeStore ─> Room
MainActivity ──┘                                         └─> ImageStore ─> filesDir/images
```

## Wie doet wat

**`KookboekApp`** bouwt de `RecipeRepository` en houdt één app-brede `CoroutineScope` vast.
Imports draaien in die scope, **niet** in een ViewModel: het deelvenster mag dichtgaan terwijl
het ophalen nog loopt, zonder dat het recept verdwijnt.

**`RecipeRepository`** is de enige plek die weet hoe je een recept binnenhaalt: URL normaliseren,
dubbele detecteren, pagina ophalen, parsen, opslaan, plaatje ophalen. Geeft een `ImportResult`
terug (`Saved` / `AlreadySaved` / `Failed`) — nooit een exception naar de UI.

**`RecipeStore`** is Room erachter en een `StateFlow<List<Recipe>>` ervoor. Heeft ook
`loaded: StateFlow<Boolean>`, want een lege lijst betekent twee heel verschillende dingen:
"je hebt geen recepten" en "we hebben nog niet gekeken". De UI mag pas iets beweren als
`loaded` waar is.

**`RecipeParser`** is puur: HTML in, `ParsedRecipe` uit. Geen netwerk, geen Android. Daarom is
het als enige laag echt te testen. Zie [parser.md](parser.md).

**`Recipe`** (in `data/Recipe.kt`) is het domeinmodel en het enige wat de UI kent. De Room-entities
in `data/db/` zijn een opslagdetail; er lekt geen `RecipeEntity` naar boven.

## Waarom het domeinmodel niet gelijk is aan de tabellen

`Recipe` heeft `ingredients: List<String>` en `checkedIngredients: Set<Int>`. In de database zijn
dat rijen met een `position` en een `checked`-vlag. De mapping zit in `data/db/`.

Dat is bewust: de opslag ging van JSON naar Room zonder dat de UI iets merkte. Wil je het
domeinmodel op de tabellen laten lijken (afvinkstatus ín de ingrediëntregel), dan is dat een
aparte, grotere verandering — en dan raak je elk scherm.

## Navigatie

`MainActivity` gebruikt `navigation-compose` met drie bestemmingen: bibliotheek, recept, bewerken.
`ShareActivity` is een losse activity met een doorzichtig thema (`Theme.Kookboek.Sheet`), zodat
het deelvenster over je browser zweeft in plaats van de hele app te openen.

## Wat er bewust niet is

- Geen server, geen account, geen analytics. De app praat alleen met de site die je deelt.
- Geen DI-container: één `Application` die drie objecten aanmaakt is genoeg.
- Geen aparte `domain`-laag: de repository ís de use case.
- Geen paging: honderden recepten passen prima in een `LazyColumn`.
