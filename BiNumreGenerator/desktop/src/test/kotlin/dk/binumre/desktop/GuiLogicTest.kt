package dk.binumre.desktop

import dk.binumre.desktop.ui.AppState
import dk.binumre.desktop.ui.EntryFields
import dk.binumre.desktop.ui.FieldException
import dk.binumre.desktop.ui.Strings
import dk.binumre.desktop.ui.TableLogic
import dk.binumre.layout.BibFont
import dk.binumre.layout.BibLayout
import dk.binumre.layout.BibTexts
import dk.binumre.model.Bib
import dk.binumre.model.BibProject
import dk.binumre.model.BibStage
import dk.binumre.model.BibType
import dk.binumre.model.Graphic
import dk.binumre.model.Stage
import dk.binumre.startlist.StartEntry
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

class GuiLogicTest {
    private val zone = ZoneId.of("Europe/Copenhagen")
    /** Eksempelfilerne deles med core (testen kører i desktop/). */
    private fun resource(name: String) = File("../core/src/test/resources/$name").also { check(it.isFile) { it.absolutePath } }
    private fun tempDir() = Files.createTempDirectory("binumre").toFile().also { it.deleteOnExit() }

    // ---------- Projektfil ----------

    @Test
    fun grafikstierErRelativeIProjektfilen() {
        val dir = tempDir()
        val project = BibProject(
            eventName = "Testløbet",
            graphics = listOf(
                Graphic(File(dir, "logo.png").path, 1.0, 2.0, 30.0, 10.0),
                Graphic(File(dir, "grafik/sponsor.jpg").path, 5.0, 6.0, 30.0, 10.0),
            ),
        )
        val file = File(dir, "loeb.json")
        ProjectFiles.save(project, file)
        val stored = BibProject.fromJson(file.readText())
        assertEquals(listOf("logo.png", "grafik/sponsor.jpg"), stored.graphics.map { it.file })
        assertEquals(project, ProjectFiles.load(file))

        // Mappen kan flyttes: stierne følger projektfilen
        val moved = File(tempDir(), "loeb.json").also { file.copyTo(it) }
        assertEquals(File(moved.parentFile, "logo.png").path, ProjectFiles.load(moved).graphics[0].file)
    }

    // ---------- Tabel ----------

    private val entries = listOf(
        StartEntry(given = "Anna", family = "Eksempel", club = "OK Testklub", cls = "D21", bib = "101", card = 8000001),
        StartEntry(given = "Bo", family = "Prøve", cls = "D21"),
        StartEntry(name = "Carl Demo", club = "Test OK", cls = "H21", bib = "301"),
    )

    @Test
    fun søgningOgFiltre() {
        assertEquals(listOf(0, 1, 2), TableLogic.filter(entries, "", null, false))
        assertEquals(listOf(0, 1), TableLogic.filter(entries, "", "D21", false))
        assertEquals(listOf(1), TableLogic.filter(entries, "", null, true))
        assertEquals(listOf(0), TableLogic.filter(entries, "ANNA testklub", null, false))
        assertEquals(listOf(0), TableLogic.filter(entries, "8000001", null, false))
        assertEquals(listOf(2), TableLogic.filter(entries, "demo", null, false))
        assertEquals(emptyList<Int>(), TableLogic.filter(entries, "demo", "D21", false))
    }

    @Test
    fun rettelseAfLøber() {
        val date = LocalDate.of(2026, 5, 2)
        val f = TableLogic.fields(entries[1], zone)
        assertEquals("", f.start)
        val e = TableLogic.apply(entries[1], f.copy(bib = " 102 ", card = "2000002", start = "9.43", club = "OK Ny"), zone, date)
        assertEquals("102", e.bib)
        assertEquals(2000002L, e.card)
        assertEquals("OK Ny", e.club)
        assertEquals("09:43:00", TableLogic.time(e.start, zone))
        assertEquals(date, java.time.Instant.ofEpochMilli(e.start!!).atZone(zone).toLocalDate())

        // Ændret klokkeslæt bliver på løberens dag; uændret tekst rører ikke tiden
        val later = TableLogic.apply(e, TableLogic.fields(e, zone).copy(start = "10:15:30"), zone, LocalDate.of(2030, 1, 1))
        assertEquals("10:15:30", TableLogic.time(later.start, zone))
        assertEquals(date, java.time.Instant.ofEpochMilli(later.start!!).atZone(zone).toLocalDate())
        assertEquals(e, TableLogic.apply(e, TableLogic.fields(e, zone), zone, date))

        // Tom starttid = fri start; tomt SI-nummer = intet
        val free = TableLogic.apply(e, TableLogic.fields(e, zone).copy(start = "", card = ""), zone, date)
        assertNull(free.start)
        assertNull(free.card)

        assertEquals("card", assertThrows(FieldException::class.java) { TableLogic.apply(e, EntryFields(card = "abc"), zone, date) }.field)
        assertEquals("start", assertThrows(FieldException::class.java) { TableLogic.apply(e, EntryFields(start = "kl. 10"), zone, date) }.field)
        assertEquals("start", assertThrows(FieldException::class.java) { TableLogic.apply(e, EntryFields(start = "25:00"), zone, date) }.field)
    }

    @Test
    fun etaperOgAfstande() {
        var p = TableLogic.withStage(BibProject(), 1) { it.copy(label = "Søndag") }
        assertEquals(listOf(Stage(), Stage("Søndag")), p.stages)
        p = TableLogic.withStage(p, 0) { TableLogic.withDistance(it, "S1", "400 m") }
        p = TableLogic.withStage(p, 0) { TableLogic.withDistance(it, "", "1 km") }
        assertEquals("400 m", p.stage(0).distance("S1"))
        assertEquals("1 km", p.stage(0).distance("S2"))
        p = TableLogic.withStage(p, 0) { TableLogic.withDistance(it, "S1", "") }
        assertEquals(mapOf("" to "1 km"), p.stage(0).distances)
    }

    // ---------- Programtilstand ----------

    @Test
    fun indlæsRetOgSkrivPdf() {
        val state = AppState()
        state.loadFiles(listOf(resource("startliste_eksempel.xml")))
        assertNull(state.error)
        assertEquals(BibType.SINGLE, state.project.type)
        assertEquals("Testløbet", state.project.eventName)
        assertEquals(2, state.bibSet.bibs.size)
        assertEquals(1, state.bibSet.withoutNumber)
        assertEquals(listOf("S1"), TableLogic.startNames(state.lists[0]))

        // Giv løberen uden nummer et startnummer, og fravælg en anden
        state.updateEntries(0) { _, e -> if (e.bib.isEmpty()) e.copy(bib = "102") else e }
        assertEquals(listOf("101", "102", "301"), state.bibSet.bibs.map { it.number })
        assertEquals(0, state.bibSet.withoutNumber)
        state.updateEntries(0) { _, e -> if (e.bib == "301") e.copy(excluded = true) else e }
        assertEquals(listOf("101", "102"), state.bibSet.bibs.map { it.number })

        // En ny fil erstatter listen ved enkeltløb
        state.loadFiles(listOf(resource("stafet_eksempel.xml")))
        assertEquals(1, state.lists.size)
        state.project = state.project.copy(type = BibType.RELAY)
        assertEquals(listOf("12", "12", "12"), state.bibSet.bibs.map { it.number })

        val dir = tempDir()
        val pdf = File(dir, "ud.pdf")
        state.writePdf(pdf)
        assertNull(state.error)
        assertTrue(pdf.length() > 1000)
        assertEquals(pdf, state.lastPdf)
    }

    @Test
    fun flereFilerBliverTilEtaper() {
        val state = AppState()
        state.loadFiles(listOf(resource("startliste_eksempel.xml"), resource("etape2_eksempel.xml")))
        assertEquals(BibType.MULTIDAY, state.project.type)
        assertEquals(2, state.lists.size)
        assertEquals(2, state.previewBib.stages.size)
        state.project = TableLogic.withStage(state.project, 1) { it.copy(label = "Søndag") }
        state.removeList(0)
        assertEquals(listOf(Stage("Søndag")), state.project.stages)
        assertEquals("etape2_eksempel.xml", state.lists.single().source)

        state.loadFiles(listOf(File("findes-ikke.xml")))
        assertTrue(state.error != null)
    }

    @Test
    fun projektGemmesOgÅbnesMedGrafik() {
        val dir = tempDir()
        val logo = File(dir, "logo.png")
        ImageIO.write(BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB), "png", logo)
        val state = AppState()
        assertFalse(state.dirty)
        state.addGraphic(logo)
        assertEquals(Graphic(logo.absolutePath, 8.0, 8.0, 40.0, 20.0), state.project.graphics.single())
        assertEquals(0, state.selectedGraphic)
        assertTrue(state.dirty)

        state.addGraphic(File(dir, "mangler.png"))
        assertTrue(state.error != null)
        assertEquals(1, state.project.graphics.size)

        val file = File(dir, "loeb.json")
        state.saveProject(file)
        assertFalse(state.dirty)
        assertTrue("\"logo.png\"" in file.readText())

        val other = AppState()
        other.openProject(file)
        assertEquals(state.project, other.project)
        other.removeGraphic(0)
        assertTrue(other.dirty)

        // Eksempel-startnummeret bruges, når der ingen startliste er
        assertEquals("123", other.previewBib.number)
    }

    // ---------- Sprog ----------

    @Test
    fun danskOgEngelskHarDeSammeTekster() {
        val da = Strings.read("da")
        val en = Strings.read("en")
        assertTrue(en.size > 80)
        assertEquals(en.keys.sorted(), da.keys.sorted())
        assertTrue(Strings.available.containsAll(listOf("da", "en")))
        assertTrue(BibTexts.languages.containsAll(Strings.available))
        // Samme pladsholdere i begge sprog
        val placeholder = Regex("\\{\\d}")
        for (k in en.keys) assertEquals(k, placeholder.findAll(en.getValue(k)).map { it.value }.toSet(), placeholder.findAll(da.getValue(k)).map { it.value }.toSet())
    }

    @Test
    fun teksterMedPladsholdereOgReserve() {
        val da = Strings.load("da")
        assertEquals("3 startnumre", da.format("status.bibs", 3))
        assertEquals("Startliste", da["tab.startlist"])
        assertEquals("Start list", Strings.load("en")["tab.startlist"])
        // Ukendt sprog giver engelsk; ukendt nøgle giver nøglen
        assertEquals("en", Strings.load("xx").language)
        assertEquals("findes.ikke", da["findes.ikke"])
        val partial = Strings("sv", mapOf("ok" to "Okej"), Strings.read("en"))
        assertEquals("Okej", partial["ok"])
        assertEquals("Cancel", partial["cancel"])
    }

    // ---------- Forhåndsvisning ----------

    @Test
    fun skærmOgPdfMålerTekstEns() {
        val canvas = SkiaBibCanvas(Surface.makeRasterN32Premul(10, 10).canvas, 4f) { null }
        PDDocument().use { doc ->
            for ((font, file) in listOf(BibFont.REGULAR to "DejaVuSans.ttf", BibFont.BOLD to "DejaVuSans-Bold.ttf")) {
                val pdf = BibLayout::class.java.getResourceAsStream("/fonts/$file").use { PDType0Font.load(doc, it!!, true) }
                for (text in listOf("101", "Anna Marie Eksempel", "Åse Østergård-Ærø", "Afstand til start")) {
                    val expected = pdf.getStringWidth(text) / 1000.0 * 11.0
                    assertEquals("$text ($font)", expected, canvas.textWidth(text, font, 11.0), expected * 0.002)
                }
            }
        }
    }

    @Test
    fun startnummerTegnesSomBillede() {
        val bib = Bib(
            number = "101", given = "Anna", family = "Eksempel", name = "", club = "OK Testklub", card = 8000001L,
            cls = "D21", stages = listOf(BibStage(null, "D21", "3", 6100, "S1")),
        )
        val png = BibImage.png(bib, BibProject(eventName = "Testløbet"), pixelsPerMm = 4f)
        val image = Image.makeFromEncoded(png)
        assertEquals(840, image.width)
        assertEquals(594, image.height)
        // Der er tegnet noget sort (nummeret) på den hvide baggrund
        val pixels = ImageIO.read(png.inputStream())
        val dark = (0 until pixels.width step 4).sumOf { x -> (0 until pixels.height step 4).count { y -> pixels.getRGB(x, y) and 0xFFFFFF == 0 } }
        assertTrue(dark > 1000)
        assertEquals(0xFFFFFF, pixels.getRGB(2, 2) and 0xFFFFFF)
    }
}
