# Contrastlezer

Android-app die de camera gebruikt om tekst **direct hoog-contrast en vergroot**
weer te geven. Bedoeld voor situaties waarin tekst slecht leesbaar is door
kleurgebruik, bijvoorbeeld zwarte letters op een rode verpakking.

De app zet het camerabeeld live om naar twee kleuren (voorgrond/achtergrond)
met een adaptieve drempel op basis van het lokale contrast. Kleurverschillen
verdwijnen, het vormverschil van de letters blijft over.

## Functies

- **Live hoog-contrast beeld** (RGBA-camera → lokale box-mean + lokale
  contrastdrempel → twee kleuren).
- **Zes kleurmodi**: zwart op wit, wit op zwart, geel op zwart, zwart op geel,
  blauw op wit, cyaan op zwart.
- **Gevoeligheid** instelbaar met een schuif (hoger = meer pixels worden tekst).
- **Beeldstabilisatie**: optische/elektronische stabilisatie waar het toestel
  dat ondersteunt, plus een rustige softwarematige modus. Bij beweging wordt
  het laatste scherpe beeld even vastgehouden en pixels klappen niet meer bij
  elk frame heen en weer. De stabilisatie is in de instellingen uit te zetten.
- **Vastzetten**: maakt een echte foto en zet die om naar hoog contrast. Dat
  geeft een scherper en rustiger leesbeeld dan het laatste live-frame.
- **Vergroting**: grote plus- en minknoppen, een zoom-schuif of knijpen. Het
  vastgezette beeld heeft eigen plus- en minknoppen en kan met één vinger
  worden verschoven.
- **Foto**: slaat het verwerkte beeld op in `Afbeeldingen/Contrastlezer`
  (wanneer vastgezet: precies het vastgezette beeld).
- **Tekst herkennen (OCR)**: zet het beeld vast en druk op `Tekst`. De app
  herkent de letters met ML Kit (offline) en toont ze in zeer groot contrast.
  Lukt herkennen op het originele beeld niet, dan probeert de app het
  hoog-contrast-beeld.
- **Voorlezen**: de herkende tekst kan in het Nederlands worden voorgelezen
  met de tekst-naar-spraak-engine van Android. In de instellingen is
  `Automatisch voorlezen` aan te zetten.
- **Zaklamp** en **camera wisselen** (in de instellingen).
- Grote knoppen en royale raakvlakken, zodat de app ook met een minder goede
  motoriek te bedienen is. Scherm blijft aan, vaste staande oriëntatie,
  Nederlandse UI.

## Codeboek voor beginners

Wil je begrijpen hoe de code in elkaar zit? In [`docs/index.html`](docs/index.html)
staat een uitgebreid **codeboek in gewone taal**. Het legt per bestand uit wat de
code doet, hoe het camerabeeld door de app stroomt en hoe je zelf een kleur,
tekst of instelling kunt aanpassen. De pagina heeft een inhoudsopgave, zoekfunctie,
codevoorbeelden en een begrippenlijst.

Open de pagina lokaal in je browser, of bekijk de online versie via GitHub Pages:

- **Online:** <https://simondewaal1980.github.io/contrastlezer/>
- **Bron:** [`docs/index.html`](docs/index.html)

## Vereisten

- Android 13 (API 33) of nieuwer.
- Camera op het toestel.
- Voor voorlezen: een Nederlandse tekst-naar-spraak-stem (meestal al aanwezig;
  anders te installeren via de instellingen van het toestel).

## Bouwen

De SDK staat in `~/Android/Sdk` (of stel `ANDROID_HOME` in). Java 17+ is nodig.

```bash
./gradlew assembleDebug
```

Resultaat: `app/build/outputs/apk/debug/app-debug.apk`.

Installeren op een aangesloten toestel:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Gebruik

1. Geef cameratoegang.
2. Richt de camera op de tekst. Het beeld blijft rustig bij beweging.
3. Kies onderin een kleurcombinatie.
4. `Vastzetten` maakt een scherpe foto en zet het beeld stil
   (`Hervat` = weer live).
5. Vergroot met de grote `+`- en `−`-knoppen, de zoom-schuif of door te
   knijpen. In een vastgezet beeld kun je met één vinger verschuiven.
6. `Foto` bewaart het verwerkte beeld in de galerij.
7. `Tekst` herkent de tekst in het vastgezette beeld en toont die groot.
   Met `Lees voor` spreekt de app de tekst uit; `Sluiten` of de terugknop
   gaat terug naar het camerabeeld.
8. Via `Instellingen` pas je stabilisatie, gevoeligheid, zoom, zaklamp,
   camera en automatisch voorlezen aan.

## Techniek

- Kotlin, Jetpack Compose, CameraX (ImageAnalysis met RGBA-output, 1280×720).
- `image/FrameProcessor.kt` doet de beeldbewerking in pure Kotlin:
  blokgemiddelden (4 px), integraalbeelden voor een box-filter, lokale
  standaardafwijking (Sauvola-principe) en een kleurverschil-term. Hierdoor
  blijven zowel dunne als dikke letters gevuld en ontstaan geen
  samengesmolten vlekken.
- Verwerking gebeurt op een achtergrondthread; twee roterende bitmaps
  voorkomen flikkering. `KEEP_ONLY_LATEST` voorkomt een frame-achterstand.
- Foto's gaan door dezelfde verwerking (max. 1600 px) en worden via
  MediaStore opgeslagen.
- `image/FrameMotionDetector.kt` meet op een verkleind grayscale-rooster hoe
  sterk het beeld beweegt. Bij beweging toont de app tijdelijk minder frames;
  bij stilstand wordt elk frame verwerkt.
- `FrameProcessor` ondersteunt temporele hysterese: een pixel blijft tekst of
  achtergrond tot het verschil duidelijk genoeg verandert. Dat voorkomt
  flikkerende letters.
- Optische en elektronische stabilisatie worden via `Camera2Interop`
  aangevraagd als het toestel die modi ondersteunt.
- Tekstherkenning gebruikt **ML Kit Text Recognition v2** (bundled,
  `com.google.mlkit:text-recognition`): volledig offline, Latin-model (dekt
  Nederlands), geen extra permissies. Bij vastzetten blijven zowel het
  originele als het hoog-contrast-beeld bewaard; OCR probeert eerst het
  origineel en daarna het hoog-contrast-beeld.
- Voorlezen gebruikt Android `TextToSpeech` met locale `nl-NL`. Lange tekst
  wordt in stukken geknipt (`SpeechTextSplitter`) en als één wachtrij
  afgespeeld.
- Omdat de bundled ML Kit-bibliotheek ±11 MB per ABI kost, bevat de
  release-APK alleen `arm64-v8a` (alle Android 13+-telefoons); debug-builds
  houden alle architecturen voor de emulator.

## Testen zonder toestel

```bash
./gradlew testDebugUnitTest
```

Dit rendert een testkaart (tekst op gekleurde vlakken) door de verwerking en
schrijft het resultaat naar `app/build/test-output/`. Daarnaast testen
`FrameMotionDetectorTest` en `FrameProcessorStabilityTest` de
bewegingsdetectie en het vasthouden van pixels bij kleine verschillen.
`OcrTextCleanerTest` en `SpeechTextSplitterTest` controleren het opruimen van
herkende tekst en het knippen van lange tekst voor de spraak-engine.
