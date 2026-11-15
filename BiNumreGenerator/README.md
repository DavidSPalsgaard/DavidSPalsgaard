# BiNumreGenerator – startnumre til orienteringsløb

Laver startnumre (A5) som PDF ud fra en startliste: stort startnummer, navn, klub, SI-nummer og
stregkode (Code 128 med startnummeret) – og i bunden, vendt på hovedet så løberen selv kan læse
det, starttid, klasse, bane, start og afstand til start.

**Status:** kernen og et kommandolinje-program virker. Den grafiske brugerflade (med editor til
grafik) og Android-appen mangler – se "Plan".

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
gradlew :desktop:installDist        # -> desktop/build/install/binumre/bin/binumre(.bat)

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
  (210 × 148,5 mm). Stien er relativ til projektfilen. Billedet skaleres ind i rammen.
- `distances`: afstand til start pr. startsted; nøglen `""` gælder alle andre startsteder.
- `language`: sprog på startnummeret – engelsk, dansk og 22 andre europæiske sprog
  (`layout/BibTexts.kt`). "Afstand til start", "Tur" og "Etape" er oversat uden gennemsyn af
  modersmålstalende.

## Struktur
- `core/` – uden platformsafhængigheder: `startlist/`, `ofeed/`, `model/` (projekt, startnumre,
  fletning af etaper), `layout/` (layout i mm mod `BibCanvas`, Code 128, tekster).
- `desktop/` – PC-program: `PdfWriter` (PDFBox, indlejret DejaVu Sans) og kommandolinjen.

## Test
```
gradlew :core:test :desktop:test    # opdigtede testdata i core/src/test/resources
```

## Plan
1. ~~Import, datamodel, layout og PDF for alle tre skabeloner (kommandolinje)~~
2. Grafisk brugerflade (Compose Multiplatform) med editor til grafik og tabel til at rette/vælge løbere
3. Direkte forbindelse til MeOS
4. Installationspakker til Windows, Linux og Mac
5. Android-app (genbruger `core/` med sin egen `BibCanvas`)
