# BiNumreGenerator – startnumre til orienteringsløb

Laver startnumre (A5) som PDF ud fra en startliste: stort startnummer, navn, klub, SI-nummer og
stregkode (Code 128 med startnummeret) – og i bunden, vendt på hovedet så løberen selv kan læse
det, starttid, klasse, bane, start og afstand til start.

**Status:** PC-programmet virker – både med grafisk brugerflade (tabel til at rette/vælge løbere,
opsætning af løbet og editor til grafik med forhåndsvisning) og som kommandolinje. Direkte
forbindelse til MeOS, installationspakker og Android-appen mangler – se "Plan".

## Skabeloner
- **Enkeltløb** (`single`): standardoplysningerne.
- **Stafet** (`relay`): holdets ture har samme startnummer; turen står i et farvet bånd øverst og
  som farvet kant i bunden. Farve pr. tur vælges i projektfilen (`legColors`).
- **Flerdagesløb** (`multiday`): én startliste pr. etape; bunden får én linje pr. etape. Samme
  løber findes på SI-brik, ellers på navn + klub.

## Startlister
IOF XML 3.0 / 2.0.3 (MeOS, OE, Eventor), Excel (.xlsx), CSV og OFeed (QR-linket fra
løbet → Integrationer → O Checklist). Koden i `startlist/` og `ofeed/` er kopieret fra SI Startinfo
og udvidet med tur, holdnavn og banelængde. Løbere uden startnummer springes over.

## Brug
```
gradlew :desktop:run                # starter programmets vindue
gradlew :desktop:installDist        # -> desktop/build/install/binumre/bin/binumre(.bat)
```
Kræver JDK 17 eller nyere. Uden argumenter åbner `binumre` vinduet; med argumenter er det
kommandolinjen (se nedenfor). `binumre --gui startliste.xml [--project loeb.json]` åbner vinduet
med startlisten og projektet indlæst.

### Programmets vindue
- **Startliste:** indlæs IOF XML, Excel eller CSV, eller hent fra OFeed (QR-link). Ved
  flerdagesløb indlæses én fil pr. etape (vælges flere filer på én gang, bliver det et
  flerdagesløb). Tabellen kan søges i og filtreres på klasse; klik på en løber for at rette
  oplysningerne (fx give et manglende startnummer). Fluebenet afgør, om løberen udskrives –
  fluebenet i overskriften gælder alle viste, så en hel klasse fravælges ved at filtrere på den
  først. Løbere uden startnummer er markeret med rødt og tælles i bunden af vinduet.
- **Opsætning:** skabelon, løbets navn, sprog på startnummeret, navneformat, tidszone, afstand
  til start pr. startsted, etapernes overskrifter, farve pr. tur, arkformat og sortering.
- **Layout og grafik:** forhåndsvisning af startnummeret – tegnet med samme kode og skrifter som
  PDF'en. Indsæt op til 10 billeder (PNG/JPG), træk dem på plads, og træk i et hjørne for at
  ændre størrelsen (eller skriv målene i mm). Pilene bladrer mellem løberne.
- **Gem projekt** gemmer opsætningen som JSON (grafikstier relative til projektfilen, så mappen
  kan flyttes). Startlisten og rettelser i den gemmes ikke – ret helst i løbsprogrammet og
  indlæs igen. **Skriv PDF** laver udskriften.
- **Sprog:** programmets tekster ligger i `desktop/src/main/resources/i18n/<sprogkode>.properties`
  (UTF-8). Dansk og engelsk er oversat; et nyt sprog tilføjes ved at oversætte `en.properties`
  til en fil med en af sprogkoderne fra `BibTexts.languages` – så dukker det selv op i menuen.
  Manglende tekster vises på engelsk.

### Kommandolinje
```
binumre startliste.xml -o numre.pdf --distance S1=400m --distance S2=1,2km
binumre stafet.xml --type relay --lang sv
binumre etape1.xml etape2.xml --stage Lørdag --stage Søndag --distance 1:400m --distance 2:S1=600m
binumre --ofeed "<QR-link>" --project loeb.json
binumre --help
```
Udskrift: to A5 pr. A4-ark med skærelinje (`--sheet a4x2`, standard) eller ét A5 pr. side (`--sheet a5`).

### Projektfil (JSON)
`--save-project loeb.json` gemmer opsætningen; `--project loeb.json` bruger den igen.
```json
{
  "type": "RELAY",
  "eventName": "Teststafetten",
  "language": "da",
  "zone": "Europe/Copenhagen",
  "graphics": [ { "file": "logo.png", "x": 8, "y": 20, "width": 32, "height": 14 } ],
  "legColors": ["#FFD400", "#3B82F6", "#22C55E"],
  "stages": [ { "label": "", "distances": { "S1": "400 m", "": "350 m" } } ]
}
```
- `graphics`: 0-10 billeder (PNG/JPG), mål i mm fra startnummerets øverste venstre hjørne
  (210 × 148,5 mm). Stien er relativ til projektfilen. Billedet skaleres ind i rammen og ligger
  under teksten (men over stafettens farvede bånd).
- `distances`: afstand til start pr. startsted; nøglen `""` gælder alle andre startsteder.
- `language`: sprog på startnummeret – engelsk, dansk og 22 andre europæiske sprog
  (`layout/BibTexts.kt`). "Afstand til start", "Tur" og "Etape" er oversat uden gennemsyn af
  modersmålstalende.

## Struktur
- `core/` – uden platformsafhængigheder (hverken Compose eller PDFBox): `startlist/`, `ofeed/`,
  `model/` (projekt, startnumre, fletning af etaper, fravalgte løbere), `layout/` (layout i mm
  mod `BibCanvas`, flytning/skalering af grafik i `GraphicEdit`, Code 128, tekster).
- `desktop/` – PC-program: `PdfWriter` (PDFBox, indlejret DejaVu Sans), `SkiaBibCanvas`
  (`BibCanvas` til skærmen, samme skrifter), `ProjectFiles`, kommandolinjen (`Main.kt`) og
  brugerfladen i `ui/` (Compose Multiplatform): `AppState` (tilstand), `TableLogic` (søgning og
  rettelser), `Strings` (sprogfiler) og ét skærmbillede pr. fane.

Compose hentes fra Maven Central og Googles Maven-arkiv (`google()` i `settings.gradle.kts`).

## Test
```
gradlew :core:test :desktop:test    # opdigtede testdata i core/src/test/resources
```
Brugerfladens logik (tabel, projektfil, sprogfiler, grafik-editorens geometri, og at skærm og PDF
måler teksten ens) er dækket af tests; selve vinduet er afprøvet i hånden med eksempelfilerne.

## Plan
1. ~~Import, datamodel, layout og PDF for alle tre skabeloner (kommandolinje)~~
2. ~~Grafisk brugerflade (Compose Multiplatform) med editor til grafik og tabel til at rette/vælge løbere~~
3. Direkte forbindelse til MeOS
4. Installationspakker til Windows, Linux og Mac
5. Android-app (genbruger `core/` med sin egen `BibCanvas`)
