# UI en thema

Jetpack Compose met Material 3, maar het moet niet naar Material ruiken. Het uitgangspunt is
papier: warm, rustig, met inkt in plaats van kleurvlakken.

## Het palet

`ui/theme/Color.kt`. Licht is crèmewit papier (`#F6F1E6`) met inkt (`#211D18`) en terracotta
(`#B4522A`) als accent. Donker is geen grijs maar een gedimde warme kamer (`#15130F`) met amber.

Let op de `inverseSurface`/`inversePrimary`-waarden in `Theme.kt`: snackbars worden daaruit
opgebouwd, en met de Material-defaults krijg je een lavendelkleurige balk met paarse knoppen
midden op het papier.

Er is **geen dynamic color**. De papier-identiteit is het punt van de app.

## Typografie

`ui/theme/Type.kt`. Serif voor wat je leest (titels, koppen), sans voor wat je aantikt. Bewust
het systeem-serif: geen lettertypedownloads, geen megabytes in de APK, en het ziet er meteen uit
als een gedrukt kookboek.

`bodyLarge` is 17sp met regelhoogte 26sp. Dat is groter dan Material voorschrijft, en met opzet:
ingrediënten en stappen lees je vanaf een telefoon die verderop op het aanrecht staat.

## Vormen

`ui/theme/Shape.kt`. Kleine radii (3–18dp). Papier heeft geen zachte ronde hoeken; een kaart is
een geknipt vel. Kaarten krijgen een haarlijn-rand in plaats van een slagschaduw.

`ui/theme/Paper.kt` legt een fijne korrel over de achtergrond — subtiel, alleen achter de content,
niet over foto's heen.

## UX-regels die niet onderhandelbaar zijn

- **Nederlands.** Alle zichtbare tekst, inclusief foutmeldingen en lege staten.
- **Nooit liegen over wat er gelukt is.** Kon de parser de ingrediënten niet vinden, dan zegt het
  scherm dat, met de knoppen om het origineel te openen, opnieuw te proberen of zelf in te vullen.
  Geen leeg recept dat doet alsof.
- **Een lege lijst is pas leeg als `loaded` waar is.** Anders flitst "je hebt nog geen recepten"
  voorbij bij het opstarten.
- **Het scherm blijft aan** op het receptscherm. Je handen zitten onder het deeg.
- **Verwijderen is altijd terug te draaien** via de snackbar, inclusief de foto.
- **Delen houdt je waar je was.** `ShareActivity` is een venstertje over je browser, geen
  volledige app-start.
