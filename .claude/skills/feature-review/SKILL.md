---
name: feature-review
description: Bedenk wat Kookboek mist en beoordeel het tegen de visie. Zes haiku-persona's lopen als gebruiker door de app, jij toetst hun wensen aan de code en aan wat Anton wil, en het resultaat is een HTML-rapport met major, medium, klein, quality-of-life, een bonuslijst met keukenhulpjes en een lijst afwijzingen. Gebruik dit als er gevraagd wordt om een nieuwe ronde features, een feature-review, of "wat mist de app".
---

# Een feature-ronde voor Kookboek

Eén ronde levert een rapport op. Anton kiest eruit wat gebouwd wordt. Het rapport van de vorige
ronde staat in `.claude/knowledge/reviews/` en is tegelijk de sjabloon voor de volgende.

Jij bent de beoordelaar, niet de bedenker. De persona's bedenken, jij toetst. Ze zitten
regelmatig fout over wat de code al doet, en een wens die de visie breekt is geen feature.

## 1. De visie ophalen

Lees eerst wat er ligt:

- `.claude/knowledge/reviews/` voor de vorige ronde. Streep af wat inmiddels gebouwd is
  (kijk in `git log` en `strings.xml`), zodat het niet opnieuw op de lijst komt.
- `README.md`, `.claude/knowledge/architecture.md` (de sectie "Wat er bewust niet is") en
  `.claude/knowledge/ui.md` (de UX-regels die niet onderhandelbaar zijn).

De visie zoals Anton die op 6 september 2026 gaf:

- Semi-hobby, maar ook voor familie en vrienden. Play Store is gewenst, Google's verificatie
  zit in de weg, dus de familie krijgt de APK direct.
- Kookboek eerst. Keukenhulpjes (boodschappenlijst, timers, weekmenu) zijn "misschien later"
  en horen in een aparte bonuslijst.
- Back-ups mogen, ook een accountkoppeling puur voor back-up. Verder geen externe diensten
  behalve de sites waar recepten vandaan komen. Parsen blijft op het toestel.
- De eindvorm van de app staat nog niet vast.

Is dat langer dan een paar maanden geleden, of gaat de ronde over iets nieuws, stel dan vier
vragen vóór je begint, met `AskUserQuestion`, in één keer:

1. Voor wie is het nu (alleen Anton, familie en vrienden, Play Store)?
2. Boekenkast of keukenhulp: is de boodschappenlijst een grens of een backlog-item?
3. Hoe ver gaat "alles offline": zijn bestanden goed, is een sync die de gebruiker zelf
   beheert goed?
4. Wat is al afgewezen en mag niet terugkomen, en waar jeukt het?

## 2. Zelf de code lezen

Voordat de persona's iets zeggen, weet je zelf wat er is. Minstens:

- `app/src/main/res/values/strings.xml`, want elke string is een feature die bestaat.
- `data/Recipe.kt`, `data/RecipeRepository.kt`, `ui/KookboekViewModel.kt`.
- De vier schermen in `ui/` en `AndroidManifest.xml` (welke mime-types het deelmenu pakt).

Dit is wat persona's in de vorige ronde ten onrechte als ontbrekend opgaven. Controleer deze
eerst, ze komen terug:

| Bewering | Werkelijkheid |
|---|---|
| Geen dubbeldetectie | `RecipeRepository.import` normaliseert de URL en geeft `AlreadySaved` |
| Byline toont de site twee keer | `RecipeScreen` dedupliceert auteur en site |
| Opnieuw ophalen wist notities | `refresh` bewaart notities, favoriet en vinkjes; het vervangt wél handbewerkte regels |
| Tags zijn dode data | Ze worden doorzocht en twee ervan getoond; filteren en bewerken kan niet |
| Niet op tijd te zoeken | Sorteren op snelste bestaat; een filterchip niet |
| Stepper-knoppen zijn 18dp | Het icoon is 18dp, het raakvlak 48dp |
| Schaling toont "1.5" in het Nederlands | `Scaling.format` volgt de locale |

## 3. De persona's uitzetten

Zes `Agent`-aanroepen in één bericht, `model: haiku`, `subagent_type: general-purpose`, op de
achtergrond. De prompts staan in [references/personas.md](references/personas.md). Elke persona
krijgt de visie mee, de bestanden die hij moet lezen, en de opdracht om 12 tot 20 punten te
leveren met grootte en een oordeel of het bij "een rustig papieren kookboek" past.

De zes: de doordeweekse kok, de verzamelaar, het familielid dat de APK kreeg, de
data-eigenaar, de oudere lezer, en de keukenhulp-dromer die de bonuslijst schrijft. Verander de
mix alleen als de vraag erom vraagt (een tablet-ronde wil bijvoorbeeld een persona aan het
aanrecht met een tablet).

Anton wil hier fan-out; niet in je eentje gaan zitten bedenken.

## 4. Beoordelen

Terwijl ze lopen, lees je de resterende code. Als alle zes klaar zijn:

1. Gooi weg wat de code al doet. Zet het onder "Waar de persona's fout zaten", met de plek
   in de code, zodat Anton er geen tijd aan kwijt is.
2. Toets elk overgebleven punt aan één vraag: zou een papieren kookboek dit doen? Tabbladen,
   kantlijnnotities, een foto van oma's kaartje, een recept doorgeven aan tafel, printen: ja.
   Dashboards, sterren, kalenders, voorraadbeheer, accounts: nee.
3. Drie stempels: **past**, **past als het stil blijft** (geen eigen scherm, geen eigen
   dienst), **drijft af**. Afdrijvers gaan naar "Afgewezen" met de reden, niet stilletjes weg.
4. Bij keukenhulpjes de vuistregel uit de vorige ronde: wat een klus aan de telefoon zelf
   geeft (bestandskiezer, klok-app voor timers) of een bestaande structuur hergebruikt,
   overleeft. Wat de gebruiker data laat onderhouden, sterft in week twee.
5. Voeg je eigen punten toe waar de persona's ze misten en markeer ze als van jou.
6. Let op de regels die een feature breekt. Vorige ronde: labels toekennen breekt de
   "twee acties in de selectiemodus"-regel uit `ui.md`. Zeg dat erbij en geef je oordeel.

Aantallen die Anton vroeg: minstens 5 major, 5 tot 10 medium, 10 tot 20 klein, 10
quality-of-life, plus de bonuslijst en de afwijzingen.

## 5. Het rapport

Laad de skill `artifact-design` en schrijf het als HTML. Gebruik het vorige rapport in
`.claude/knowledge/reviews/` als sjabloon: dezelfde CSS (het Sinaasappel-palet uit
`ui/theme/Palettes.kt`, systeem-serif voor koppen, sans voor de rest, licht én donker), en
dezelfde volgorde van secties:

1. Het oordeel in één alinea, en de bouwvolgorde die jij zou kiezen.
2. Wie er gevraagd is.
3. Major, elk met naam, stempel, wie het vroeg, waarom, en welke bestanden het raakt.
4. Medium, zelfde vorm.
5. Klein en quality-of-life als tabellen.
6. Bonus keukenhulpjes met oordeel per stuk.
7. Afgewezen, met reden.
8. Waar de persona's fout zaten.
9. Wat je in de gaten zou houden (bijvoorbeeld: bundel schema-wijzigingen in één migratie).

Schrijf Engels of Nederlands, wat Anton in de vraag gebruikte. Volg de `unslop`-regels: geen
gedachtestreepjes, geen dubbele punten als verbinding, meningen hebben, "ik" mag.

Daarna:

- Sla het op als `.claude/knowledge/reviews/<datum>-features.html`, zodat de volgende ronde
  het kan lezen. Dit hoort in de repo.
- Publiceer het met `Artifact` en stuur het bestand mee met `SendUserFile`.
- Is de visie veranderd, werk dan sectie 1 van deze skill bij.

## Wat dit niet is

Geen implementatie. De ronde stopt bij het rapport. Bouwen gebeurt daarna, per feature, met
de gewone regels: eerst testen, strings in beide talen, migratie bij elke entity-wijziging,
en controleren op de emulator (`smoke-test`).
