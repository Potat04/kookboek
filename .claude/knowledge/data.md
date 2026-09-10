# Opslag

SQLite via Room, database `kookboek.db`, schema-versie 2. Elk schema ligt vast in
`app/schemas/nl.potat04.kookboek.data.db.KookboekDatabase/<versie>.json` en is ingecheckt.

## Tabellen

| Tabel | Inhoud |
|---|---|
| `recipes` | titel, bron, site, auteur, omschrijving, foto, tijden, porties, notities, favoriet, `addedAt`, `quality`, plus de kolommen van versie 2 hieronder |
| `ingredients` | `recipeId`, `position`, `section`, `text`, `checked` |
| `steps` | `recipeId`, `position`, `section`, `text`, `checked` |
| `labels` | `id`, `name`, `position`: de labels van de lezer zelf, in de volgorde die hij koos |
| `recipe_labels` | `recipeId`, `labelId`: welk recept welk label draagt |

`ingredients`, `steps` en `recipe_labels` hangen met een foreign key aan `recipes` met
`ON DELETE CASCADE`, `recipe_labels` ook aan `labels`. Elke foreign-key-kolom heeft een index.
Een recept of label echt verwijderen ruimt dus vanzelf zijn regels op.

`tags` staat als JSON-kolom op de receptrij, niet als tabel. Het zijn de woorden van de site,
alleen getoond en nooit apart bevraagd. Labels zijn wél een tabel, want die zijn gedeeld: een
label hernoemen moet elk recept raken dat het draagt, en de bibliotheek filtert erop.
Ingrediënten en stappen zijn tabellen omdat ze geordend zijn en doorzocht worden.

Waarom niet één JSON-bestand: een notitie schrijven of één regel afvinken raakt nu één rij in
plaats van de hele collectie opnieuw wegschrijven, en zoeken op ingrediënt is een query.

## De kolommen van versie 2

Allemaal nullable op `recipes`, zodat een bestaande rij na de migratie precies betekent wat hij
daarvoor betekende.

| Kolom | Wat het is |
|---|---|
| `cookedServings` | waar de lezer de portiestepper het laatst liet staan |
| `lastCookedAt` | de laatste tik op "gemaakt", epoch millis |
| `editedAt` | de laatste handmatige wijziging van ingrediënten of stappen; verversen waarschuwt hierop |
| `prepMinutes`, `cookMinutes` | apart bewaard als de pagina beide geeft; `totalMinutes` blijft wat getoond wordt |
| `videoUrl` | de video van de pagina, als die er is |
| `deletedAt` | zacht verwijderd, zie onder |
| `attachmentFile` | een gefotografeerd receptkaartje naast de getypte versie, bestandsnaam in de images-map |
| `openedAt` | de eerste keer dat het receptscherm openging; null is nooit |

`ingredients.section` is de groepskop boven een ingrediënt ("Voor de dressing"), net als
`steps.section`. De parser levert nog geen groepen; het model en de editor kunnen ze al aan.

## Zacht verwijderen

Verwijderen zet `deletedAt`; de rij, zijn regels en zijn foto blijven staan. `observeAll` in de
DAO filtert op `deletedAt IS NULL`, dus de gewone lijst (`RecipeStore.recipes`) ziet zo'n recept
niet meer. `RecipeStore.deleted` is de aparte lijst voor "onlangs verwijderd".
`restoreDeleted(id)` maakt het ongedaan, `deleteForever(id)` gooit de rij echt weg, en
`purgeDeleted(olderThanMillis)` doet dat voor alles wat langer dan die tijd in de prullenbak lag.

De undo in de snackbar werkt onafhankelijk hiervan: `restore(recipe)` schrijft de kopie van vóór
het verwijderen terug, en die had `deletedAt = null`.

## Als je het schema verandert

De migraties staan in `data/db/Migrations.kt`; `RecipeStore` registreert ze met
`.addMigrations(*Migrations.ALL)`. Bij elke wijziging aan een `@Entity`:

1. Hoog `version` op in `KookboekDatabase`.
2. Bouw, zodat het nieuwe schema-JSON in `app/schemas/` verschijnt, en check dat in.
3. Schrijf een `Migration` en kopieer de SQL uit de `createSql` van dat JSON. Room vergelijkt de
   gemigreerde tabellen met wat hij verwacht en weigert de database bij elk verschil.
4. Voeg hem toe aan `Migrations.ALL`.

**Gebruik geen `fallbackToDestructiveMigration()`.** Dat gooit de recepten van iemand weg die de
app al gebruikt, en dat is precies de data die nergens anders bestaat.

## Foto's

Niet in de database. `filesDir/images/<recipeId>.jpg`, bij het importeren verkleind naar
maximaal ~1400px langste zijde en als JPEG (kwaliteit 85) weggeschreven. Op de receptrij staat
alleen de bestandsnaam. Een bijgevoegd receptkaartje (`attachmentFile`) staat in dezelfde map.

`ImageStore` heeft een kleine `LruCache` van gedecodeerde bitmaps, want de lijst scrollt continu
door thumbnails.

**Verwijderen laat de foto met opzet staan.** Anders kan "ongedaan maken" het recept niet compleet
terugzetten, en een zacht verwijderd recept heeft zijn foto nog gewoon nodig. `pruneOrphans()`
draait bij het opstarten en ruimt op wat door geen enkel recept, ook geen verwijderd recept,
meer wordt aangewezen, met een marge van tien minuten, zodat een import die nú loopt niet zijn
eigen net gedownloade plaatje kwijtraakt.

## Cookies

Niet van ons. Wat een site na een botcontrole afgeeft staat in de `CookieManager` van de
WebView, en `SiteCookies` leest dat terug voor de gewone HTTP-verzoeken en voor de
fotodownload. Er is geen eigen administratie en niets ervan raakt de database. Zie
[fetching.md](fetching.md).

## Backup

`allowBackup="true"` staat aan, dus database en foto's verhuizen mee naar een nieuw toestel.

Daarnaast is er een eigen bestandsformaat, `data/RecipeJson.kt`, voor backups naar een map en
voor losse `.kookboek`-bestanden. Zie [architecture.md](architecture.md).
