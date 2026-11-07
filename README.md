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
- **Vergroting**: knijp in het beeld of gebruik de zoom-schuif (camera-zoom,
  optisch waar beschikbaar).
- **Bevries**: zet het beeld stil en knijp verder in om rustig te lezen.
- **Foto**: slaat het verwerkte beeld op in `Afbeeldingen/Contrastlezer`
  (wanneer bevroren: precies het bevroren beeld).
- **Zaklamp** en **camera wisselen**.
- Scherm blijft aan, vaste staande oriëntatie, Nederlandse UI, grote knoppen.

## Vereisten

- Android 13 (API 33) of nieuwer.
- Camera op het toestel.

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
2. Richt de camera op de tekst.
3. Kies onderin een kleurcombinatie.
4. Knijp met twee vingers om te vergroten.
5. `Bevries` zet het beeld stil (nogmaals indrukken = hervatten).
6. `Foto` bewaart het verwerkte beeld in de galerij.
7. Via `Instellingen` pas je de gevoeligheid en de zoom aan.

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

## Testen zonder toestel

```bash
./gradlew testDebugUnitTest
```

Dit rendert een testkaart (tekst op gekleurde vlakken) door de verwerking en
schrijft het resultaat naar `app/build/test-output/`.
