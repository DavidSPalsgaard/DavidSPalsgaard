package dk.binumre.desktop.ui

import dk.binumre.model.BibProject
import dk.binumre.model.Stage
import dk.binumre.startlist.NaturalOrder
import dk.binumre.startlist.StartEntry
import dk.binumre.startlist.StartList
import dk.binumre.startlist.TimeParser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Felterne i "Ret løber" som tekst. */
data class EntryFields(
    val bib: String = "", val given: String = "", val family: String = "", val name: String = "",
    val club: String = "", val cls: String = "", val card: String = "", val start: String = "",
    val course: String = "", val startName: String = "", val teamName: String = "", val leg: String = "",
)

/** Et felt der ikke kan forstås ("card" eller "start"). */
class FieldException(val field: String) : Exception(field)

/** Logikken bag startlistens tabel: søgning, filtrering og rettelser. Uden brugerflade, så den kan testes. */
object TableLogic {

    /** Indeks på de linjer der skal vises. Alle ord i [query] skal findes i løberens oplysninger. */
    fun filter(entries: List<StartEntry>, query: String, cls: String?, onlyMissingBib: Boolean): List<Int> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return entries.indices.filter { i ->
            val e = entries[i]
            if (cls != null && e.cls != cls) return@filter false
            if (onlyMissingBib && e.bib.isNotEmpty()) return@filter false
            if (words.isEmpty()) return@filter true
            val text = listOf(e.bib, e.given, e.family, e.name, e.club, e.cls, e.card?.toString().orEmpty(), e.teamName, e.course, e.startName)
                .joinToString(" ").lowercase()
            words.all { it in text }
        }
    }

    fun time(start: Long?, zone: ZoneId): String {
        if (start == null) return ""
        val t = Instant.ofEpochMilli(start).atZone(zone)
        return DateTimeFormatter.ofPattern("HH:mm:ss").format(t)
    }

    fun fields(e: StartEntry, zone: ZoneId) = EntryFields(
        bib = e.bib, given = e.given, family = e.family, name = e.name, club = e.club, cls = e.cls,
        card = e.card?.toString().orEmpty(), start = time(e.start, zone), course = e.course,
        startName = e.startName, teamName = e.teamName, leg = e.leg,
    )

    private val clock = Regex("^\\d{1,2}[:.]\\d{2}([:.]\\d{2})?$")

    /**
     * Løberen med de rettede felter. Starttiden er et klokkeslæt ("10:30" eller "10:30:15", tom = fri start)
     * på løberens hidtidige dag – ellers på [date].
     */
    fun apply(e: StartEntry, f: EntryFields, zone: ZoneId, date: LocalDate): StartEntry {
        val card = f.card.trim().let { if (it.isEmpty()) null else it.toLongOrNull()?.takeIf { n -> n > 0 } ?: throw FieldException("card") }
        val startText = f.start.trim()
        val start = when {
            startText.isEmpty() -> null
            startText == time(e.start, zone) -> e.start
            !clock.matches(startText) -> throw FieldException("start")
            else -> {
                val day = e.start?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: date
                TimeParser(zone, day).parse(startText.replace('.', ':')) ?: throw FieldException("start")
            }
        }
        return e.copy(
            bib = f.bib.trim(), given = f.given.trim(), family = f.family.trim(), name = f.name.trim(),
            club = f.club.trim(), cls = f.cls.trim(), card = card, start = start, course = f.course.trim(),
            startName = f.startName.trim(), teamName = f.teamName.trim(), leg = f.leg.trim(),
        )
    }

    /** Startstederne i en startliste (uden tomme), sorteret. */
    fun startNames(list: StartList?): List<String> =
        list?.entries.orEmpty().map { it.startName }.filter { it.isNotEmpty() }.distinct().sortedWith(NaturalOrder)

    /** Projektet med etape [index] ændret; listen af etaper forlænges efter behov. */
    fun withStage(project: BibProject, index: Int, change: (Stage) -> Stage): BibProject {
        val stages = MutableList(maxOf(project.stages.size, index + 1)) { project.stage(it) }
        stages[index] = change(stages[index])
        return project.copy(stages = stages)
    }

    /** Afstanden for ét startsted sat eller (ved tom tekst) fjernet. */
    fun withDistance(stage: Stage, startName: String, text: String): Stage =
        stage.copy(distances = if (text.isEmpty()) stage.distances - startName else stage.distances + (startName to text))
}
