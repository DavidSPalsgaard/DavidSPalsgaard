package dk.binumre.desktop

import dk.binumre.layout.BibTexts
import dk.binumre.model.BibBuilder
import dk.binumre.model.BibProject
import dk.binumre.model.BibType
import dk.binumre.model.Sheet
import dk.binumre.model.SortBy
import dk.binumre.model.Stage
import dk.binumre.ofeed.OFeedClient
import dk.binumre.ofeed.OFeedConnection
import dk.binumre.startlist.StartList
import dk.binumre.startlist.StartListException
import dk.binumre.startlist.StartListParser
import java.io.File
import java.time.ZoneId
import kotlin.system.exitProcess

private val USAGE = """
    Brug: binumre                        Åbner programmets vindue
          binumre --gui [<startliste> ...] [--project <fil.json>]
                                         Åbner vinduet med startlisten/projektet indlæst
          binumre [valg] <startliste> [<startliste etape 2> ...]
                                         Skriver PDF'en direkte (kommandolinje)

    Startlisten er IOF XML 3.0 / 2.0.3 (fx fra MeOS), Excel (.xlsx) eller CSV.
    Ved flerdagesløb angives én startliste pr. etape i rækkefølge.

      -o, --out <fil>          PDF-filen der skrives (standard: startnumre.pdf)
      --project <fil.json>     Opsætning af løbet (grafik, farver, afstande ...)
      --save-project <fil>     Gem den samlede opsætning som JSON
      --type <single|relay|multiday>
      --ofeed <link|løbs-id>   Hent startlisten fra OFeed (QR-linket), evt. --password <kode>
      --event <navn>           Løbets navn (standard: fra startlisten)
      --lang <kode>            Sprog på startnummeret: ${BibTexts.languages.joinToString(" ")}
      --sheet <a4x2|a5>        To A5 pr. A4-ark (standard) eller ét A5 pr. side
      --sort <bib|club|class|name>
      --class <klasse>         Kun denne klasse (kan gentages)
      --distance <start=tekst> Afstand til start, fx S1=400m eller blot 400m for alle
                               (flerdagesløb: 2:S1=600m gælder etape 2)
      --stage <tekst>          Overskrift for en etape (gentages i rækkefølge)
      --zone <tidszone>        Fx Europe/Copenhagen (standard: computerens)
""".trimIndent()

fun main(args: Array<String>) {
    // Uden argumenter (dobbeltklik) åbnes brugerfladen; med argumenter er det kommandolinjen
    if (args.isEmpty() || args[0] == "--gui") {
        dk.binumre.desktop.ui.runGui(args.drop(1))
        return
    }
    try {
        run(args)
    } catch (e: StartListException) {
        fail(e.message ?: "Fejl i startlisten")
    } catch (e: IllegalArgumentException) {
        fail(e.message ?: "Ugyldigt valg")
    }
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}

private fun run(args: Array<String>) {
    if ("-h" in args || "--help" in args) {
        println(USAGE)
        return
    }
    val files = mutableListOf<String>()
    val opts = mutableMapOf<String, MutableList<String>>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a.startsWith("-")) {
            val key = if (a == "-o") "out" else a.trimStart('-')
            opts.getOrPut(key) { mutableListOf() } += args.getOrNull(++i) ?: throw IllegalArgumentException("$a mangler en værdi")
        } else files += a
        i++
    }
    fun opt(key: String) = opts[key]?.last()

    val projectFile = opt("project")?.let(::File)
    var project = projectFile?.let { BibProject.fromJson(it.readText()) } ?: BibProject()
    opt("type")?.let { v -> project = project.copy(type = enumOf<BibType>(v, "--type")) }
    opt("lang")?.let { project = project.copy(language = it) }
    opt("sort")?.let { v -> project = project.copy(sortBy = enumOf<SortBy>(v, "--sort")) }
    opt("zone")?.let { project = project.copy(zone = ZoneId.of(it).id) }
    opt("sheet")?.let { v ->
        project = project.copy(sheet = when (v.lowercase()) {
            "a4x2" -> Sheet.A4_2UP
            "a5" -> Sheet.A5
            else -> throw IllegalArgumentException("--sheet skal være a4x2 eller a5")
        })
    }

    val zone = project.zone.ifEmpty { null }?.let(ZoneId::of) ?: ZoneId.systemDefault()
    val lists = mutableListOf<StartList>()
    opt("ofeed")?.let { link ->
        val conn = OFeedConnection.fromLink(link) ?: OFeedConnection(OFeedConnection.DEFAULT_URL, link, opt("password").orEmpty())
        val r = OFeedClient.fetch(conn)
        lists += StartList(r.event, "OFeed", System.currentTimeMillis(), r.entries)
    }
    for (f in files) {
        val file = File(f)
        if (!file.isFile) throw IllegalArgumentException("Filen findes ikke: $f")
        val p = StartListParser.parse(file.readBytes(), zone)
        lists += StartList(p.event, file.name, System.currentTimeMillis(), p.entries, date = p.date)
    }
    if (lists.isEmpty()) throw IllegalArgumentException("Angiv en startliste (fil eller --ofeed)")
    if (lists.size > 1 && opt("type") == null && projectFile == null) project = project.copy(type = BibType.MULTIDAY)

    project = project.copy(
        eventName = opt("event") ?: project.eventName.ifEmpty { lists.first().event },
        stages = stages(project, lists.size, opts["stage"].orEmpty(), opts["distance"].orEmpty()),
    )

    val set = BibBuilder.build(project, lists, opts["class"].orEmpty().toSet())
    if (set.bibs.isEmpty()) fail("Ingen løbere med startnummer i startlisten (${set.withoutNumber} uden startnummer)")
    val out = File(opt("out") ?: "startnumre.pdf")
    PdfWriter.write(set.bibs, project, out, projectFile?.absoluteFile?.parentFile)
    opt("save-project")?.let { File(it).writeText(project.toJson()) }

    println("${set.bibs.size} startnumre skrevet til ${out.path}")
    if (set.withoutNumber > 0) println("${set.withoutNumber} løbere uden startnummer er sprunget over")
}

/** Etaperne fra projektet, udvidet til [count] og rettet med --stage og --distance. */
private fun stages(project: BibProject, count: Int, labels: List<String>, distances: List<String>): List<Stage> {
    val out = MutableList(maxOf(count, project.stages.size, 1)) { project.stage(it) }
    labels.forEachIndexed { i, label -> if (i < out.size) out[i] = out[i].copy(label = label) }
    for (d in distances) {
        // "400m", "S1=400m" eller "2:S1=400m"
        val m = Regex("^(?:(\\d+):)?(?:([^=]*)=)?(.*)$").find(d)!!
        val (stageNo, start, text) = m.destructured
        val targets = if (stageNo.isEmpty()) out.indices.toList() else listOf(stageNo.toInt() - 1)
        for (t in targets) {
            if (t !in out.indices) throw IllegalArgumentException("--distance $d: etape $stageNo findes ikke")
            out[t] = out[t].copy(distances = out[t].distances + (start.trim() to text.trim()))
        }
    }
    return out
}

private inline fun <reified T : Enum<T>> enumOf(value: String, option: String): T =
    enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("$option skal være en af: " + enumValues<T>().joinToString(", ") { it.name.lowercase() })
