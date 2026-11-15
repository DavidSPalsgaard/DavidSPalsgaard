package dk.binumre.startlist

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.xml.parsers.DocumentBuilderFactory

class StartListException(message: String) : Exception(message)

data class ParsedStartList(val event: String, val entries: List<StartEntry>, val date: String? = null)

/**
 * Læser startlister i de formater O Checklist bruger:
 * - IOF XML 3.0 StartList (individuel og stafet) – fx eksport fra MeOS, OE eller Eventor
 * - IOF XML 2.0.3 StartList
 * - Excel (.xlsx): første ark, overskrifter i første række (Family, Given, Class, Club, Bib, Time, Card, Start …)
 * - CSV med overskrifter, eller i O Checklist-rækkefølgen Name, Club, Class, Bib, StartTime, Card, StartName
 */
object StartListParser {

    fun parse(bytes: ByteArray, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): ParsedStartList {
        val times = TimeParser(zone, today)
        if (bytes.size > 1 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            return ParsedStartList("", rowsToEntries(XlsxReader.firstSheetRows(bytes), times))
        }
        var text = String(bytes, Charsets.UTF_8)
        if ('\uFFFD' in text) text = String(bytes, Charset.forName("windows-1252"))
        text = text.removePrefix("\uFEFF").trimStart()
        return if (text.startsWith("<")) parseXml(bytes, times) else ParsedStartList("", rowsToEntries(Csv.parse(text), times))
    }

    // ---------- IOF XML ----------

    private fun parseXml(bytes: ByteArray, times: TimeParser): ParsedStartList {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Ingen DTD/eksterne entiteter i en startliste
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { isExpandEntityReferences = false }
        }
        val doc = try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw StartListException("Ugyldig XML: ${e.message}")
        }
        val root = doc.documentElement
        if (root.localName != "StartList") throw StartListException("XML-filen er ikke en IOF StartList (<${root.localName}>)")

        val v3 = root.getAttribute("iofVersion").startsWith("3") ||
            (root.child("Event") != null && root.child("IOFVersion") == null)
        val out = mutableListOf<StartEntry>()
        for (cs in root.children("ClassStart")) {
            if (v3) classStartV3(cs, times, out) else classStartV2(cs, times, out)
        }
        // Løbets dato: IOF 3 Event/StartTime/Date, IOF 2 Event/StartDate/Date
        val date = root.path("Event", "StartTime", "Date").text()
            .ifEmpty { root.path("Event", "StartDate", "Date").text() }
            .ifEmpty { null }
        return ParsedStartList(root.path("Event", "Name").text(), out, date)
    }

    private fun classStartV3(cs: Element, times: TimeParser, out: MutableList<StartEntry>) {
        val cls = cs.path("Class", "ShortName").text().ifEmpty { cs.path("Class", "Name").text() }
        val classCourse = cs.path("Course", "Name").text()
        val classLength = cs.path("Course", "Length").text().toDoubleOrNull()?.toInt()
        val startName = cs.child("StartName").text()

        for (ps in cs.children("PersonStart")) {
            val person = ps.child("Person")
            val org = ps.child("Organisation")
            val base = StartEntry(
                given = person.path("Name", "Given").text(),
                family = person.path("Name", "Family").text(),
                club = org.orgName(),
                cls = cls,
                course = classCourse,
                courseLength = classLength,
                startName = startName,
            )
            // Flerdagsløb: første Start (som O Checklist – én konkurrence pr. fil)
            val st = ps.child("Start")
            if (st == null) {
                out += base
                continue
            }
            val entry = base.copy(
                bib = st.child("BibNumber").text(),
                start = times.parse(st.child("StartTime").text()),
                course = st.path("Course", "Name").text().ifEmpty { classCourse },
                courseLength = st.path("Course", "Length").text().toDoubleOrNull()?.toInt() ?: classLength,
            )
            out += withCards(entry, st)
        }

        for (ts in cs.children("TeamStart")) {
            val teamName = ts.child("Name").text()
            val teamBib = ts.child("BibNumber").text()
            val teamOrg = ts.child("Organisation")
            for (tm in ts.children("TeamMemberStart")) {
                val person = tm.child("Person")
                val org = tm.child("Organisation") ?: teamOrg
                for (st in tm.children("Start")) {
                    val leg = st.child("Leg").text()
                    val entry = StartEntry(
                        given = person.path("Name", "Given").text(),
                        family = person.path("Name", "Family").text(),
                        club = org.orgName(),
                        cls = cls,
                        bib = st.child("BibNumber").text().ifEmpty { teamBib },
                        start = times.parse(st.child("StartTime").text()),
                        course = st.path("Course", "Name").text().ifEmpty { classCourse },
                        courseLength = st.path("Course", "Length").text().toDoubleOrNull()?.toInt() ?: classLength,
                        startName = startName,
                        team = if (leg.isEmpty()) teamName else "$teamName · $leg",
                        teamName = teamName,
                        leg = leg,
                    )
                    out += withCards(entry, st)
                }
            }
        }
    }

    private fun classStartV2(cs: Element, times: TimeParser, out: MutableList<StartEntry>) {
        val cls = cs.child("ClassShortName").text()
            .ifEmpty { cs.path("Class", "ClassShortName").text() }
            .ifEmpty { cs.child("ClassId").text() }
        for (ps in cs.children("PersonStart")) {
            val person = ps.child("Person")
            val club = ps.child("Club")
            val st = ps.child("Start") ?: ps.child("RaceStart")?.child("Start")
            val clock = st.path("StartTime", "Clock").text()
            val date = st.path("StartTime", "Date").text()
            out += StartEntry(
                given = person.path("PersonName", "Given").text(),
                family = person.path("PersonName", "Family").text(),
                club = club.child("ShortName").text().ifEmpty { club.child("Name").text() },
                cls = cls,
                bib = st.child("BibNumber").text().ifEmpty { ps.child("BibNumber").text() },
                start = if (clock.isEmpty()) null else times.parse(if (date.isEmpty()) clock else "${date}T$clock"),
                card = st.path("CCard", "CCardId").text().toLongOrNull(),
            )
        }
    }

    /** Én række pr. SI-brik (en løber kan have flere). */
    private fun withCards(entry: StartEntry, st: Element): List<StartEntry> {
        val cards = st.children("ControlCard").mapNotNull { it.text().toLongOrNull() }
        return if (cards.isEmpty()) listOf(entry) else cards.map { entry.copy(card = it) }
    }

    private fun Element?.orgName(): String = child("ShortName").text().ifEmpty { child("Name").text() }

    // ---------- CSV / Excel ----------

    private enum class Col { NAME, GIVEN, FAMILY, CLUB, CLS, BIB, START, CARD, COURSE, START_NAME, TEAM }

    /** Kolonneoverskrifter på engelsk, dansk, svensk og norsk (normaliseret: små bogstaver, uden mellemrum/tegn). */
    private val headers: Map<Col, List<String>> = mapOf(
        Col.NAME to listOf("name", "navn", "namn", "fullname", "fuldtnavn", "deltager", "deltagare", "løber", "löpare", "loper"),
        Col.GIVEN to listOf("given", "givenname", "firstname", "first", "fornavn", "förnamn", "fornamn"),
        Col.FAMILY to listOf("family", "familyname", "lastname", "last", "surname", "efternavn", "efternamn", "etternavn"),
        Col.CLUB to listOf("club", "klub", "klubb", "org", "organisation", "organization", "forening", "förening"),
        Col.CLS to listOf("class", "classname", "klasse", "klass", "category"),
        Col.BIB to listOf("bib", "bibnumber", "startnr", "startnummer", "startno", "startnumber", "nummerlapp", "brystnr", "nr"),
        Col.START to listOf("time", "starttime", "starttid", "tid"),
        Col.CARD to listOf("card", "cardno", "cardnumber", "si", "sicard", "sinr", "sinummer", "sibrik", "brik", "brikke", "bricka", "bricknummer", "brikkenummer", "ecard", "chip", "emit"),
        Col.COURSE to listOf("course", "bane", "bana", "løype"),
        Col.START_NAME to listOf("start", "startname", "startsted", "startplats", "startplass"),
        Col.TEAM to listOf("team", "hold", "lag"),
    )

    private fun norm(s: String) = s.lowercase().replace(Regex("[\\s_\\-.#:]"), "")

    private fun rowsToEntries(rows: List<List<String>>, times: TimeParser): List<StartEntry> {
        if (rows.isEmpty()) return emptyList()
        val header = rows[0].map(::norm)
        val col = mutableMapOf<Col, Int>()
        for ((c, names) in headers) {
            val i = header.indexOfFirst { it in names }
            if (i >= 0) col[c] = i
        }
        var data = rows.drop(1)
        if (col.size < 2 || (Col.CLS !in col && Col.CARD !in col)) {
            // Ingen genkendte overskrifter: O Checklist-rækkefølgen Name, Club, Class, Bib, StartTime, Card, StartName
            col.clear()
            col += listOf(Col.NAME to 0, Col.CLUB to 1, Col.CLS to 2, Col.BIB to 3, Col.START to 4, Col.CARD to 5, Col.START_NAME to 6)
            data = rows
        }
        fun List<String>.get(c: Col) = col[c]?.let { getOrNull(it)?.trim() } ?: ""

        return data.mapNotNull { r ->
            var given = r.get(Col.GIVEN)
            var family = r.get(Col.FAMILY)
            var name = r.get(Col.NAME)
            if (given.isEmpty() && family.isEmpty() && ',' in name) {
                // "Efternavn, Fornavn"
                family = name.substringBefore(',').trim()
                given = name.substringAfter(',').trim()
                name = ""
            }
            if (given.isEmpty() && family.isEmpty() && name.isEmpty()) return@mapNotNull null
            StartEntry(
                given = given, family = family, name = name,
                club = r.get(Col.CLUB), cls = r.get(Col.CLS), bib = r.get(Col.BIB),
                start = times.parse(r.get(Col.START)),
                card = r.get(Col.CARD).filter { it.isDigit() }.toLongOrNull(),
                course = r.get(Col.COURSE), startName = r.get(Col.START_NAME), team = r.get(Col.TEAM),
            )
        }
    }
}

/** Starttider: ISO-dato/tid (IOF 3), "HH:MM(:SS)" eller Excel-tal (brøkdel af døgn). */
class TimeParser(private val zone: ZoneId, private val today: LocalDate) {
    private val clock = Regex("^(\\d{1,2})[:.](\\d{2})(?:[:.](\\d{2}))?")
    private val excel = Regex("^\\d*\\.\\d+$|^\\d+$")

    fun parse(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.length > 10 && s[4] == '-' && s[10] == 'T') {
            runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            runCatching { return LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli() }
            return null
        }
        if (excel.matches(s)) {
            val v = s.toDouble()
            val decimals = s.substringAfter('.', "").length
            // "10.30" er et klokkeslæt; "0.4375" og "45745.4375" er Excel-værdier
            if (v < 1 || decimals > 2 || v >= 100) return fromExcel(v)
        }
        val m = clock.find(s) ?: return null
        val (h, min, sec) = m.destructured
        val t = runCatching { LocalTime.of(h.toInt(), min.toInt(), sec.ifEmpty { "0" }.toInt()) }.getOrNull() ?: return null
        return t.atDate(today).atZone(zone).toInstant().toEpochMilli()
    }

    private fun fromExcel(v: Double): Long {
        val days = v.toLong()
        val secs = Math.round((v - days) * 86400)
        val date = if (days == 0L) today else LocalDate.of(1899, 12, 30).plusDays(days)
        // Klokkeslæt på dagen (ikke en varighed fra midnat – ellers forkert på sommertidsdagen)
        return date.atStartOfDay().plusSeconds(secs).atZone(zone).toInstant().toEpochMilli()
    }
}

/** CSV med komma, semikolon eller tabulator (afgøres ud fra første linje). Linjer der starter med // er kommentarer. */
object Csv {
    fun parse(text: String): List<List<String>> {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("//") } ?: return emptyList()
        val delim = listOf(';', ',', '\t').maxBy { d -> countOutsideQuotes(first, d) }

        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val f = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (text.getOrNull(i + 1) == '"') { f.append('"'); i++ } else quoted = false
                } else f.append(c)
            } else when (c) {
                '"' -> quoted = true
                delim -> { row += f.toString(); f.clear() }
                '\r', '\n' -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += f.toString(); f.clear()
                    rows += row; row = mutableListOf()
                }
                else -> f.append(c)
            }
            i++
        }
        if (f.isNotEmpty() || row.isNotEmpty()) { row += f.toString(); rows += row }
        return rows.filter { r -> r.any { it.isNotBlank() } && !r[0].trimStart().startsWith("//") }
    }

    private fun countOutsideQuotes(line: String, d: Char): Int {
        var q = false
        var n = 0
        for (c in line) if (c == '"') q = !q else if (!q && c == d) n++
        return n
    }
}
