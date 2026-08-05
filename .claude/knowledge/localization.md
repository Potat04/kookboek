# Taal

De app spreekt Nederlands en Engels. Engels staat in `res/values/strings.xml` — het
ongekwalificeerde bestand, en daarmee de **fallback** — en Nederlands in
`res/values-nl/strings.xml`. De twee moeten sleutel voor sleutel gelijk blijven.

Staat de telefoon op een derde taal, dan krijg je dus **Engels**. Dat is met opzet: wie zijn
telefoon op Duits heeft staan kan waarschijnlijk wél Engels lezen en vrijwel zeker geen Nederlands.
Een Nederlandse telefoon krijgt gewoon `values-nl` — voor het eigenlijke publiek verandert er niets.

Let op dat dit betekent dat een nieuwe string die je alleen in `values-nl` zet, voor iedereen
buiten Nederland ontbreekt (en dan terugvalt op de sleutel in `values/`, die er dan niet is → een
compileerfout, dus je merkt het). Zet je hem alleen in `values/`, dan zien Nederlanders Engels en
merk je het níet. Dat is de richting waar je moet opletten.

## De keuze zelf bewaren we niet

Android 13 houdt een taal per app bij en laat die zien in de instellingen van de telefoon zelf.
Een tweede kopie in onze eigen preferences zou twee antwoorden op één vraag geven zodra iemand het
daar wijzigt. Dus is het platform de enige administratie: `ui/Language.kt` leest en schrijft
`LocaleManager.applicationLocales`, en `res/xml/locales_config.xml` (aangewezen met
`android:localeConfig` in de manifest) vertelt Android welke talen er zijn.

Schrijven herstart de activity. Dat is precies wat de nieuwe teksten laat verschijnen; de
navigatie-backstack wordt hersteld, dus je komt terug waar je was.

`minSdk` is 33, dus `LocaleManager` is er altijd. Geen appcompat nodig.

## Geen Nederlands buiten de resources

De regel is niet "de UI is Nederlands", maar **geen zichtbare tekst in Kotlin**. Dat raakt meer
lagen dan je zou denken, want de app bewaart recepten jaren en leest ze in de taal van vandaag.

- **`ImportResult.Failed` draagt een `FailureReason`**, geen zin. De repository weet niet welke
  taal gekozen is en hoort dat ook niet te weten.
- **`Toast` draagt een `UiText`** (`Res` / `Quantity` / `Joined` / `Raw`), die het scherm oplost
  met `resolve(context)`. Een afgemaakte `String` uit de ViewModel zou de snackbar vastzetten in de
  taal die gold toen het werk begon.
- **`Recipe` heeft geen labelfuncties meer.** `timeText()`, `servingsText()` en `displayTitle()`
  zijn `@Composable` extensies in `ui/Labels.kt`. Alleen de getallen staan in het model.
- **Een lege titel blijft leeg in de database.** Het scherm vult "Naamloos recept" in. De parser
  had hier `"Recept"` als fallback en zette dat dus in Room; dat is eruit.
- **De parser bakt geen porties meer in.** `RecipeParser.descriptiveYield()` houdt alleen wat méér
  zegt dan een getal — "15 stuks", "1 loaf", "24 koekjes". Een kale portie-telling wordt weggegooid
  in beide talen, want het getal staat al in `servings` en het scherm verwoordt het. Dit hééft ooit
  "4 servings" naar "4 porties" herschreven; dat bevroor één taal in de opslag.
- **`EditScreen` zet `servingsLabel` op null** zodra je een getal invult, om dezelfde reden.
- **Oude recepten hebben nog wél "4 porties" in de database staan**, van vóór deze wijziging.
  Daarom gaat het opgeslagen label bij het *lezen* óók door `descriptiveYield()` heen
  (`ui/Labels.kt`): een kale portie-telling wordt daar herkend en genegeerd, zodat een bestaand
  recept in het Engels "4 servings" zegt. Geen migratie nodig — er verandert niets aan het schema.
- **`Accept-Language`** volgt de gekozen taal (`Locale.getDefault()`, wat de app-locale ís). Dit is
  de enige plek waar de taalkeuze de *inhoud* van een recept raakt en niet alleen de app eromheen.

De inhoud van een recept wordt natuurlijk nooit vertaald. Een Nederlandse site geeft Nederlandse
ingrediënten, ook als de app op Engels staat.

## Een string toevoegen

1. Zet hem in **beide** bestanden, met dezelfde sleutel: `values/` (Engels) én `values-nl/`.
2. Verandert een getal de tekst, gebruik `<plurals>` — in beide talen alleen `one` en `other`.
   Let op: `quantity="zero"` vuurt nooit in nl of en, dus "nog leeg" is een aparte string.
3. Placeholders positioneel (`%1$s`, `%2$d`) en in beide bestanden hetzelfde aantal en type. Een
   verschil daar is een `IllegalFormatException` op het toestel, niet een compileerfout.
4. Ontbreekt een sleutel in `values-nl`, dan valt Android terug op het Engels in `values/`. Dat
   crasht niet en is niet te zien in de build — daarom is er `StringResourcesTest`, die de twee
   bestanden op sleutels, soort, meervoudsvormen en placeholders naast elkaar legt. Vergeet je een
   vertaling, dan faalt de test.

Engels is Brits (`favourites`, `colours`) en houdt dezelfde toon als het Nederlands: nuchter, warm,
kort, samentrekkingen waar het Nederlands spreektalig is. `Bereiding` is `Method`, niet
`Preparation`. De app-naam blijft "Kookboek" in beide talen; de taalnamen staan in hun eigen taal.

## Nakijken

```bash
adb shell cmd locale set-app-locales nl.potat04.kookboek --locales en
```

Dat is exact wat het instellingenscherm doet, en sneller dan tikken. Zonder `--locales` volgt de
app de telefoon weer.
