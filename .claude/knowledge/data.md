# Opslag

SQLite via Room, database `kookboek.db`, schema-versie 1. Het schema ligt vast in
`app/schemas/nl.potat04.kookboek.data.db.KookboekDatabase/1.json` en is ingecheckt.

## Tabellen

| Tabel | Inhoud |
|---|---|
| `recipes` | titel, bron, site, auteur, omschrijving, foto, tijd, porties, notities, favoriet, `addedAt`, `quality` |
| `ingredients` | `recipeId`, `position`, `text`, `checked` |
| `steps` | `recipeId`, `position`, `section`, `text`, `checked` |

`ingredients` en `steps` hangen met een foreign key aan `recipes` met `ON DELETE CASCADE`, en
hebben een index op `recipeId`. Een recept verwijderen ruimt dus vanzelf zijn regels op.

`tags` staat als JSON-kolom op de receptrij, niet als tabel. Het zijn labels die alleen getoond
worden en nooit apart bevraagd. Ingrediënten en stappen zijn wél tabellen, want die zijn geordend
en worden doorzocht.

Waarom niet één JSON-bestand: een notitie schrijven of één regel afvinken raakt nu één rij in
plaats van de hele collectie opnieuw wegschrijven, en zoeken op ingrediënt is een query.

## Als je het schema verandert

Room's `version` staat op 1 en er zijn nog geen migraties. Bij elke wijziging aan een `@Entity`:

1. Hoog `version` op in `KookboekDatabase`.
2. Schrijf een `Migration` en registreer die met `.addMigrations(...)` in `RecipeStore`.
3. Bouw, zodat het nieuwe schema-JSON in `app/schemas/` verschijnt, en check dat in.

**Gebruik geen `fallbackToDestructiveMigration()`.** Dat gooit de recepten van iemand weg die de
app al gebruikt, en dat is precies de data die nergens anders bestaat.

## Foto's

Niet in de database. `filesDir/images/<recipeId>.jpg`, bij het importeren verkleind naar
maximaal ~1400px langste zijde en als JPEG (kwaliteit 85) weggeschreven. Op de receptrij staat
alleen de bestandsnaam.

`ImageStore` heeft een kleine `LruCache` van gedecodeerde bitmaps, want de lijst scrollt continu
door thumbnails.

**Verwijderen laat de foto met opzet staan.** Anders kan "ongedaan maken" het recept niet compleet
terugzetten. `pruneOrphans()` draait bij het opstarten en ruimt op wat door geen enkel recept meer
wordt aangewezen, met een marge van tien minuten, zodat een import die nú loopt niet zijn eigen
net gedownloade plaatje kwijtraakt.

## Backup

`allowBackup="true"` staat aan, dus database en foto's verhuizen mee naar een nieuw toestel.
