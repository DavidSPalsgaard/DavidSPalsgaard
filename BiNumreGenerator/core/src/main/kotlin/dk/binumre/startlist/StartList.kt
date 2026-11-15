package dk.binumre.startlist

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class NameFormat { GIVEN_FAMILY, FAMILY_GIVEN }

/** Én løber i startlisten. [start] er epoch-millisekunder (null = fri start). */
data class StartEntry(
    /** OFeeds deltager-id (bruges til at flette ændringer ind); tom for fil/URL. */
    val id: String = "",
    val given: String = "",
    val family: String = "",
    /** Fuldt navn når fornavn/efternavn ikke findes hver for sig (CSV). */
    val name: String = "",
    val club: String = "",
    val cls: String = "",
    val bib: String = "",
    val start: Long? = null,
    val card: Long? = null,
    val course: String = "",
    val startName: String = "",
    val team: String = "",
    /** Stafet: holdets navn og turens nummer hver for sig (tom for individuelle løb). */
    val teamName: String = "",
    val leg: String = "",
    /** Banens længde i meter (IOF XML "Course/Length"). */
    val courseLength: Int? = null,
    /** Status fra OFeed (fx "Inactive", "Active", "OK", "DidNotStart") – tom for fil/URL. */
    val status: String = "",
    /** Fravalgt i brugerfladen: løberen får ikke noget startnummer. */
    val excluded: Boolean = false,
) {
    /** Løberen er registreret som startet (status fra OFeed, fx sat af O Checklist ved starten). */
    val started: Boolean get() = status.isNotEmpty() && status !in NOT_STARTED

    /** Check-in-tidspunkt: [minutes] minutter før starttiden (null ved fri start). */
    fun checkIn(minutes: Int): Long? = start?.minus(minutes * 60_000L)

    /** Forsendt: ikke startet, og check-in-tiden er overskredet (inden for 12 timer). */
    fun late(checkInMinutes: Int, now: Long): Boolean {
        val c = checkIn(checkInMinutes) ?: return false
        return !started && now > c && now - c < 12 * 3600_000L
    }

    /** Samme klokkeslæt på [date] (løbsdato angivet i opsætningen). Uændret når [date] er null. */
    fun onDate(date: LocalDate?, zone: ZoneId = ZoneId.systemDefault()): StartEntry {
        if (date == null || start == null) return this
        val time = Instant.ofEpochMilli(start).atZone(zone).toLocalTime()
        return copy(start = time.atDate(date).atZone(zone).toInstant().toEpochMilli())
    }

    companion object {
        private val NOT_STARTED = setOf("Inactive", "DidNotStart", "DidNotEnter", "NotCompeting", "Cancelled")
    }

    fun displayName(format: NameFormat): String =
        if (given.isEmpty() && family.isEmpty()) name
        else when (format) {
            NameFormat.GIVEN_FAMILY -> listOf(given, family)
            NameFormat.FAMILY_GIVEN -> listOf(family, given)
        }.filter { it.isNotEmpty() }.joinToString(if (format == NameFormat.FAMILY_GIVEN) ", " else " ")
}

data class StartList(
    val event: String,
    /** Hvor listen kom fra (filnavn eller URL). */
    val source: String,
    val loadedAt: Long,
    val entries: List<StartEntry>,
    /** OFeed: tidspunkt til næste "hent ændringer siden" (null = hent alt). */
    val cursor: String? = null,
    /** Løbets dato fra filen (IOF XML "Event/StartTime/Date"), ISO yyyy-MM-dd. */
    val date: String? = null,
) {
    /** Løbets dato: fra filen, ellers dagen for den tidligste starttid. */
    val eventDate: LocalDate? by lazy {
        date?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            ?: entries.mapNotNull { it.start }.minOrNull()?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    }

    /** Opslag på SI-nummer. Et kort kan stå flere gange (fx stafet eller lejebrik). */
    val byCard: Map<Long, List<StartEntry>> by lazy {
        entries.filter { it.card != null }.groupBy { it.card!! }
    }

    val classes: List<String> by lazy {
        entries.map { it.cls }.filter { it.isNotEmpty() }.distinct().sortedWith(NaturalOrder)
    }
}

/** Sorterer "H10, H12, H21" og "1, 2, 10" som et menneske ville. */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")
    override fun compare(a: String, b: String): Int {
        val x = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val y = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                p.trimStart('0').length.compareTo(q.trimStart('0').length).takeIf { it != 0 }
                    ?: p.trimStart('0').compareTo(q.trimStart('0'))
            } else p.compareTo(q)
            if (c != 0) return c
        }
        return x.size.compareTo(y.size)
    }
}
