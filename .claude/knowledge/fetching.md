# Pagina's ophalen

`data/PageFetcher.kt`. Twee routes naar dezelfde HTML: een gewoon HTTP-verzoek, en als dat op een
botcontrole stuit, dezelfde URL in een WebView.

## Waarom er een tweede route is

Steeds meer receptsites zitten achter een Cloudflare-controle. Die antwoordt op een kaal verzoek
met "Just a moment..." en geeft de echte pagina alleen aan iets dat het challenge-script draait.
Een WebView is echte Chromium, dus die komt er langs zoals je browser er langs komt.

## De volgorde

1. **Jsoup.** Eén round trip, geen browser, geen rendering. Dit is wat vrijwel elke site krijgt en
   het blijft de eerste poging.
2. Ziet `ChallengePage` een controle in het antwoord, of komt er een 403, 429 of 503 terug, dan
   gaat dezelfde URL naar een WebView.
3. Die laadt de pagina en de controle draait. De DOM wordt elke 400 ms uitgelezen tot die klaar is
   met laden, geen interstitial meer is en meer dan 2000 tekens heeft. Na 60 seconden is het over.

Dat "klaar met laden" is `onPageFinished`, en het is geen detail. Staan de cookies van de site al
in de pot, dan komt de echte pagina meteen en is die bij de eerste peiling half opgebouwd: lang
genoeg om voor een pagina door te gaan, te vroeg om het recept te bevatten. Wat je dan bewaart is
een kale link.

Op `cf_clearance` wachten in plaats daarvan klinkt beter en is het niet. Cloudflare ververst dat
koekje al bij het eerste antwoord, ruim voordat de controle klaar is, dus op een toestel met een
gebruikte cookiepot zegt het binnen een halve seconde "klaar". Mihon mag er wel op wachten, want
dat is een OkHttp-interceptor zonder document voor zich. Wij hebben het document.

`FetchResult` is `Page`, `Blocked`, `TimedOut`, `Offline` of `Unreachable`, en elk wordt een eigen
`FailureReason` met een eigen zin en een eigen volgende stap:

| Uitkomst | Reden | Wat het scherm zegt |
|---|---|---|
| `Blocked` | `BLOCKED` | de site hield de app tegen; open de pagina eerst in je browser |
| `TimedOut` | `TIMED_OUT` | de controle liep door tot de tijd op was; probeer het nog eens |
| `Offline` | `OFFLINE` | de telefoon zit niet op een netwerk; verbind en deel opnieuw |
| `Unreachable` | `FETCH_FAILED` | klopt de link? probeer het zo nog eens |

`Offline` komt uit de exception (`UnknownHostException`, `ConnectException`) én uit
`ConnectivityManager`, want een captive portal beantwoordt DNS prima en komt nergens.

Daarnaast is er `NO_RECIPE_ON_PAGE`, en die komt niet van de fetcher maar van de parser: de pagina
laadde en er stond geen recept op. Bij importeren blijft de link gewoon bewaard, met die zin
eronder in de deel-sheet. Bij verversen wordt er níets overschreven: een leeg antwoord zou
handgetypte regels wissen, dus zegt de app wat er aan de hand is en laat het recept staan.

## Afbreken

Elke import is te stoppen: de deel-sheet heeft een Annuleren onder de spinner, en de
toevoeg-sheet in de app blijft daarvoor open staan zolang hij bezig is. `ShareActivity` bewaart de
`Deferred` van `app.scope.async` en breekt die af; de ViewModel bewaart de `Job` van `importUrl`
en heeft `cancelImport()`.

Het Jsoup-verzoek loopt daarom in `runInterruptible`: een socket die in `read()` staat merkt een
afgebroken coroutine niet uit zichzelf. De WebView-route was al afbreekbaar (`delay` en
`suspendCancellableCoroutine`), en de `finally` die hem opruimt draait ook bij een cancel: eerst
`about:blank` zodat de scripts van de controle stoppen, dan uit de view tree, dan `destroy()`.

Er wordt niets bewaard en niets gezegd. Wie afbreekt weet wat hij deed, en de pagina staat nog
gewoon open achter de sheet.

`ChallengePage.isChallenge()` leest eerst de `cf-mitigated`-header, wat Cloudflare aanwijst als de
manier om een controle van een gewone weigering te onderscheiden. Alleen de waarde `challenge`
telt. De body blijft meelezen voor de sprong ervóór, waar de edge van de site zelf een
JavaScript-redirect kan sturen zonder Cloudflare-header. Een `<title>` van "Just a moment..." is
op zichzelf genoeg; de overige script-markers tellen alleen mee als de server óók weigerde, want een
site mag Turnstile op zijn reactieformulier zetten en daarboven een prima leesbaar recept serveren.

`cf_chl_opt` en `__cf_chl` tellen wél op zichzelf. Die staan alleen in de interstitial van
Cloudflare, en anders dan de titel staan ze er in elke taal. Zodra het script gedraaid heeft heet
de pagina namelijk "Even geduld..." op een Nederlands toestel, en dan matcht geen enkele Engelse
titel meer. Zie [gotchas.md](gotchas.md).

## De user agent is die van de WebView, vermomd als Chrome

`BrowserIdentity` vraagt het platform (`WebSettings.getDefaultUserAgent`) wat de WebView heet en
laat `ChromeUserAgent` er de verklikkers uit halen: het `; wv)` in het platformdeel en de
`Version/4.0` ervoor. Het toestelmodel wordt "Android 10; K", wat Chrome op Android zelf ook
stuurt. Het Chrome-versienummer blijft staan zoals het toestel het opgaf. Jsoup en `ImageStore`
sturen diezelfde string, want Cloudflare bindt zijn clearance-cookie aan de agent die hem verdiend
heeft. `ImageStore.USER_AGENT` is nog alleen de terugval voor een toestel zonder bruikbare WebView.

Alleen de string aanpassen is erger dan niets doen. Chromium blijft dan `Sec-CH-UA` client hints
sturen die zijn echte merk en versie noemen, en een edge die erom geeft vraagt daar met
`Critical-CH` expliciet om. `BrowserIdentity.disguise` zet daarom de string én herschrijft via
`androidx.webkit` de merkenlijst in de `UserAgentMetadata`: "Android WebView" wordt "Google Chrome"
op de versie die de string claimt. Een WebView die te oud is voor die API houdt zijn eigen hints;
de string helpt daar nog steeds, meer is er niet.

Verzin dus geen user agent, maar laat de WebView ook niet zichzelf zijn. Beide kanten van dat
verhaal staan in [gotchas.md](gotchas.md), inclusief hoe lang de verkeerde conclusie er gestaan
heeft.

## De cookie

Wat de controle uitdeelt blijft in de gedeelde `CookieManager`. `SiteCookies` leest dat terug voor
Jsoup (als map) en voor `ImageStore` (als headerwaarde). Het volgende recept van die site komt
daardoor meestal weer over gewoon HTTP binnen, en de foto wordt niet alsnog geweigerd.

## Wanneer je de controle ziet

Meestal niet. De controle is in een seconde of twee klaar en heeft geen venster nodig om te
draaien; dat is nagemeten. De WebView staat daarom in een doos van nul bij nul, wel op een echt
formaat uitgemeten zodat zichtbaar maken geen relayout kost. Zegt Cloudflare dat zijn controle
interactief geworden is, met een `message` met `event: "interactiveBegin"`, dan zet
`ChallengeStage` hem in het zicht en tik je hem zelf af. Voor muren die niks aankondigen staat er
een klok op tien seconden.

`ui/ChallengeOverlay.kt` tekent wat op de `ChallengeStage` staat, en elk scherm dat een import kan
beginnen zet dat bovenaan zijn content. Dat zijn er twee, en ze kunnen tegelijk leven: de deel-sheet
is doorzichtig, dus de bibliotheek eronder blijft gestart en blijft componeren. Eén WebView aan twee
composities aanbieden zet hem twee keer in een view tree, en `AndroidView` gooit daarop. Daarom
claimt alleen het scherm dat *resumed* is de stage, via `ChallengeStage.claim`. Dat is meteen het
scherm waar een tik op aankomt. De `factory` haalt de view nog los van een oude parent, want een
overdracht tussen twee schermen kost een frame of twee.

De WebView is van de fetcher, en die ruimt hem op. Let op dat een WebView vernietigen die nog in
een view tree hangt crasht, dus `PageFetcher` haalt hem er zelf uit voor `destroy()` in plaats van
te vertrouwen op het frame waarin het scherm hem laat vallen.

## Iets aanpassen

`ChallengePage` is puur, zonder netwerk en zonder Android-API's, en wordt net als de parser getest
tegen echt bewaarde pagina's. Nieuw soort controle betekent een nieuwe fixture. Zie
[testing.md](testing.md).

De route erlangs is daarmee niet gedekt, en met een fixture kan dat ook niet: daarvoor heb je een
site nodig die op dat moment echt een controle opgooit. Schiet er een deel-intent in en kijk mee
in `adb logcat -s PageFetcher`.
