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
3. Die laadt de pagina, de controle draait, en de DOM wordt elke 400 ms uitgelezen tot die geen
   interstitial meer is en meer dan 2000 tekens heeft. Na 60 seconden is het over.

`FetchResult` is `Page`, `Blocked` of `Unreachable`. `Blocked` wordt `FailureReason.BLOCKED`, en
het scherm zegt dan dat de site de app tegenhield, met de raad de pagina eerst in je browser te
openen en daarna opnieuw te delen.

`ChallengePage.isChallenge()` leest eerst de `cf-mitigated`-header, wat Cloudflare aanwijst als de
manier om een controle van een gewone weigering te onderscheiden. Alleen de waarde `challenge`
telt. De body blijft meelezen voor de sprong ervóór, waar de edge van de site zelf een
JavaScript-redirect kan sturen zonder Cloudflare-header. Een `<title>` van "Just a moment..." is
op zichzelf genoeg; de script-markers tellen alleen mee als de server óók weigerde, want een site
mag Turnstile op zijn reactieformulier zetten en daarboven een prima leesbaar recept serveren.

## De user agent is die van de WebView zelf

`BrowserIdentity` vraagt het platform (`WebSettings.getDefaultUserAgent`) en onthoudt het
antwoord. Jsoup en `ImageStore` sturen diezelfde agent, want Cloudflare bindt zijn
clearance-cookie aan de agent die hem verdiend heeft. `ImageStore.USER_AGENT` is nog alleen de
terugval voor een toestel zonder bruikbare WebView.

Verzin er dus geen. Een string die de client hints van de WebView tegenspreekt kostte een minuut
per controle in plaats van twee seconden; dat verhaal staat in [gotchas.md](gotchas.md).

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
beginnen zet dat bovenaan zijn content. Er is één `AndroidView`-aanroep, met opzet: een WebView
tussen twee aanroepen verplaatsen geeft `AndroidView` een view die nog een parent heeft.

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
