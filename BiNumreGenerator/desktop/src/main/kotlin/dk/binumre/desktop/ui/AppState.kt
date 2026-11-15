package dk.binumre.desktop.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dk.binumre.desktop.ImageCache
import dk.binumre.desktop.PdfWriter
import dk.binumre.desktop.ProjectFiles
import dk.binumre.layout.BibLayout
import dk.binumre.layout.GraphicEdit
import dk.binumre.model.Bib
import dk.binumre.model.BibBuilder
import dk.binumre.model.BibProject
import dk.binumre.model.BibSet
import dk.binumre.model.BibStage
import dk.binumre.model.BibType
import dk.binumre.model.Graphic
import dk.binumre.ofeed.OFeedClient
import dk.binumre.ofeed.OFeedConnection
import dk.binumre.startlist.StartEntry
import dk.binumre.startlist.StartList
import dk.binumre.startlist.StartListParser
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.prefs.Preferences

/** Programmets tilstand: projektet, startlisterne og det der er valgt i brugerfladen. */
class AppState {
    private val prefs: Preferences? = runCatching { Preferences.userNodeForPackage(AppState::class.java) }.getOrNull()

    var strings by mutableStateOf(Strings.load(prefs?.get("language", null) ?: Strings.systemLanguage()))
        private set
    var project by mutableStateOf(BibProject())
    /** Projektet som det senest blev gemt eller åbnet – til "ikke gemt". */
    var savedProject by mutableStateOf(BibProject())
        private set
    var projectFile by mutableStateOf<File?>(null)
        private set
    /** Én startliste – ved flerdagesløb én pr. etape. */
    var lists by mutableStateOf(listOf<StartList>())
        private set
    var message by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var lastPdf by mutableStateOf<File?>(null)
        private set
    var previewIndex by mutableStateOf(0)
    var selectedGraphic by mutableStateOf<Int?>(null)
    val images = ImageCache()

    val zone: ZoneId get() = BibLayout.zoneOf(project)
    val dirty: Boolean get() = project != savedProject

    /** Listerne der bruges: alle ved flerdagesløb, ellers kun den første. */
    val usedLists: List<StartList> get() = if (project.type == BibType.MULTIDAY) lists else lists.take(1)

    val bibSet: BibSet by derivedStateOf {
        val used = usedLists
        if (used.isEmpty()) BibSet(emptyList(), 0) else BibBuilder.build(project, used)
    }

    /** Startnummeret i forhåndsvisningen: en løber fra listen, eller et eksempel når der ingen er. */
    val previewBib: Bib get() = bibSet.bibs.getOrNull(previewIndex.coerceIn(0, maxOf(0, bibSet.bibs.size - 1))) ?: sampleBib()

    private fun sampleBib(): Bib {
        val stage = BibStage(null, "H21", "1", 7400, "S1")
        val relay = project.type == BibType.RELAY
        return Bib(
            number = "123", given = strings["sample.given"], family = strings["sample.family"], name = "",
            club = strings["sample.club"], card = 8123456, cls = "H21",
            teamName = if (relay) strings["sample.club"] + " 1" else "", leg = if (relay) "1" else "",
            stages = List(if (project.type == BibType.MULTIDAY) maxOf(2, lists.size) else 1) { stage },
        )
    }

    fun setLanguage(language: String) {
        strings = Strings.load(language)
        runCatching { prefs?.put("language", strings.language) }
    }

    var lastDir: File?
        get() = prefs?.get("dir", null)?.let(::File)?.takeIf { it.isDirectory }
        set(value) { runCatching { if (value != null) prefs?.put("dir", value.path) } }

    // ---------- Projekt ----------

    fun newProject() {
        project = BibProject()
        savedProject = project
        projectFile = null
        lists = emptyList()
        selectedGraphic = null
        previewIndex = 0
        message = ""
    }

    fun openProject(file: File) = guarded {
        project = ProjectFiles.load(file)
        savedProject = project
        projectFile = file
        selectedGraphic = null
        message = strings.format("msg.opened", file.name)
    }

    fun saveProject(file: File) = guarded {
        ProjectFiles.save(project, file)
        savedProject = project
        projectFile = file
        message = strings.format("msg.saved", file.name)
    }

    fun writePdf(file: File) = guarded {
        val bibs = bibSet.bibs
        if (bibs.isEmpty()) throw IllegalStateException(strings["error.noBibs"])
        PdfWriter.write(bibs, project, file)
        lastPdf = file
        message = strings.format("msg.pdf", bibs.size, file.name)
    }

    // ---------- Startlister ----------

    /** Indlæser filer: ved flerdagesløb føjes de til som etaper, ellers erstatter filen startlisten. */
    fun loadFiles(files: List<File>) = guarded {
        if (files.isEmpty()) return@guarded
        val loaded = files.map { f ->
            val p = StartListParser.parse(f.readBytes(), zone)
            StartList(p.event, f.name, System.currentTimeMillis(), p.entries, date = p.date)
        }
        addLists(loaded)
    }

    /** Henter startlisten fra OFeed. Kaldes fra en baggrundstråd; [addLists] med resultatet på UI-tråden. */
    fun fetchOFeed(link: String, password: String): StartList {
        val conn = OFeedConnection.fromLink(link) ?: OFeedConnection(OFeedConnection.DEFAULT_URL, link.trim(), password)
        val r = OFeedClient.fetch(conn)
        return StartList(r.event, "OFeed", System.currentTimeMillis(), r.entries)
    }

    fun addLists(loaded: List<StartList>) {
        if (loaded.size > 1 && project.type != BibType.MULTIDAY) project = project.copy(type = BibType.MULTIDAY)
        lists = if (project.type == BibType.MULTIDAY) lists + loaded else loaded
        if (project.eventName.isEmpty()) project = project.copy(eventName = lists.first().event)
        previewIndex = 0
        message = strings.format("msg.loaded", loaded.sumOf { it.entries.size }, loaded.joinToString(", ") { it.source })
    }

    /** Fjerner en startliste (etape) og dens opsætning. */
    fun removeList(index: Int) {
        lists = lists.filterIndexed { i, _ -> i != index }
        if (index < project.stages.size && project.type == BibType.MULTIDAY) {
            project = project.copy(stages = project.stages.filterIndexed { i, _ -> i != index })
        }
    }

    fun updateEntries(list: Int, change: (Int, StartEntry) -> StartEntry) {
        lists = lists.mapIndexed { i, l -> if (i != list) l else l.copy(entries = l.entries.mapIndexed(change)) }
    }

    /** Dagen som nye starttider i liste [list] hører til. */
    fun dateOf(list: Int): LocalDate = lists.getOrNull(list)?.eventDate ?: LocalDate.now(zone)

    // ---------- Grafik ----------

    val canAddGraphic: Boolean get() = project.graphics.size < BibProject.MAX_GRAPHICS

    fun addGraphic(file: File) {
        if (!canAddGraphic) return
        val image = images.get(file.absolutePath)
        if (image == null) {
            error = strings.format("error.image", file.name)
            return
        }
        project = project.copy(graphics = project.graphics + GraphicEdit.place(file.absolutePath, image.width, image.height, project.graphics.size))
        selectedGraphic = project.graphics.lastIndex
    }

    fun updateGraphic(index: Int, graphic: Graphic) {
        if (index !in project.graphics.indices) return
        project = project.copy(graphics = project.graphics.mapIndexed { i, g -> if (i == index) graphic else g })
    }

    fun removeGraphic(index: Int) {
        if (index !in project.graphics.indices) return
        project = project.copy(graphics = project.graphics.filterIndexed { i, _ -> i != index })
        selectedGraphic = null
    }

    private fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
        }
    }
}
