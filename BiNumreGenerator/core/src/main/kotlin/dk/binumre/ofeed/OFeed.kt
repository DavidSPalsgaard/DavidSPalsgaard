package dk.binumre.ofeed

import dk.binumre.startlist.StartEntry
import dk.binumre.startlist.StartListException
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

/*
 * OFeed (https://docs.orienteerfeed.com) – samme forbindelse som O Checklist bruger.
 *
 * QR-koden fra OFeed (Event → Integrationer → O Checklist) er et link:
 *   https://stigning.se/ofeed?url=<API events-endpoint>&auth=basic&id=<løbs-id>&pwd=<løbets adgangskode>
 *
 * Deltagere: GET <url>/<id>/competitors[?lastUpdate=<ISO-tid>]  (Basic-auth id:pwd)
 *   { results: { data: { name, classes: [ { name, startName, competitors: [...] | teams: [...] } ] } },
 *     meta: { timestamp } }
 * Med lastUpdate kommer kun deltagere ændret siden da – det bruges til løbende opdatering.
 */

data class OFeedConnection(val url: String, val eventId: String, val password: String) {

    val competitorsUrl: String
        get() = url.trim().trimEnd('/') + "/" + URLEncoder.encode(eventId.trim(), "UTF-8") + "/competitors"

    companion object {
        const val DEFAULT_URL = "https://api.orienteerfeed.com/rest/v1/events"

        /** Læser OFeed/O Checklist-linket fra QR-koden (eller indsat tekst). Null hvis det ikke er et. */
        fun fromLink(text: String): OFeedConnection? {
            val query = text.trim().substringAfter('?', "").substringBefore('#')
            if (query.isEmpty()) return null
            val params = query.split('&').mapNotNull { p ->
                val k = URLDecoder.decode(p.substringBefore('='), "UTF-8").trim()
                if (k.isEmpty()) null else k to URLDecoder.decode(p.substringAfter('=', ""), "UTF-8").trim()
            }.toMap()
            val id = params["id"].orEmpty()
            if (id.isEmpty()) return null
            return OFeedConnection(params["url"].orEmpty().ifEmpty { DEFAULT_URL }, id, params["pwd"].orEmpty())
        }
    }
}

/** Én hentning fra OFeed. [serverTime] bruges som udgangspunkt for næste opdatering. */
data class OFeedResult(val event: String, val entries: List<StartEntry>, val serverTime: String?)

object OFeedParser {

    fun parse(json: String): OFeedResult {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw StartListException("Uventet svar fra OFeed")
        }
        if (root.optBoolean("error")) throw StartListException("OFeed: " + root.str("message"))
        val data = root.optJSONObject("results")?.optJSONObject("data")
            ?: root.optJSONObject("data")
            ?: throw StartListException("Uventet svar fra OFeed")

        val out = mutableListOf<StartEntry>()
        data.optJSONArray("classes").objects().forEach { cls ->
            val className = cls.str("name")
            val startName = cls.str("startName")
            cls.optJSONArray("competitors").objects().forEach { out += entry(it, className, startName) }
            cls.optJSONArray("teams").objects().forEach { team ->
                val teamName = team.str("name")
                team.optJSONArray("competitors").objects().forEach { c ->
                    val leg = c.str("leg")
                    out += entry(c, className, startName).copy(
                        team = if (leg.isEmpty()) teamName else "$teamName · $leg",
                        teamName = teamName,
                        leg = leg,
                        // Holdets startnummer når løberen ikke har sit eget
                        bib = c.str("bibNumber").ifEmpty { team.str("bibNumber") },
                    )
                }
            }
        }
        return OFeedResult(data.str("name"), out, root.optJSONObject("meta")?.str("timestamp")?.ifEmpty { null })
    }

    private fun entry(c: JSONObject, cls: String, startName: String) = StartEntry(
        id = c.str("id"),
        given = c.str("firstname"),
        family = c.str("lastname"),
        club = c.str("organisation").ifEmpty { c.str("shortName") },
        cls = cls,
        bib = c.str("bibNumber"),
        start = c.str("startTime").ifEmpty { null }?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        card = c.str("card").filter { it.isDigit() }.toLongOrNull(),
        startName = startName,
        status = c.str("status"),
    )

    /** JSON-null og manglende felter bliver til "" (optString giver ellers "null"). */
    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key).trim()

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

/** Fletter ændrede deltagere ind (samme OFeed-id erstattes, nye tilføjes). */
fun mergeChanges(old: List<StartEntry>, changed: List<StartEntry>): List<StartEntry> {
    val ids = changed.mapNotNull { it.id.ifEmpty { null } }.toSet()
    return old.filter { it.id.isEmpty() || it.id !in ids } + changed
}

object OFeedClient {

    /** Henter deltagere – alle, eller kun ændrede siden [lastUpdate] (ISO-tid). */
    fun fetch(conn: OFeedConnection, lastUpdate: String? = null): OFeedResult {
        var target = conn.competitorsUrl + (lastUpdate?.let { "?lastUpdate=" + URLEncoder.encode(it, "UTF-8") } ?: "")
        val auth = "Basic " + Base64.getEncoder().encodeToString("${conn.eventId.trim()}:${conn.password}".toByteArray())
        val host = URL(target).host
        repeat(5) {
            val u = URL(target)
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = false
            // Adgangskoden sendes kun krypteret og kun til OFeed-serveren – ikke videre ved en omdirigering
            if (u.protocol == "https" && u.host == host) c.setRequestProperty("Authorization", auth)
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Cache-Control", "no-cache")
            try {
                val code = c.responseCode
                when {
                    code in 200..299 -> {
                        val body = c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
                        val result = OFeedParser.parse(body)
                        // Serverens tid som udgangspunkt for næste opdatering (ikke telefonens ur)
                        return if (result.serverTime != null) result else result.copy(serverTime = dateHeader(c))
                    }
                    code in 300..399 -> target = URL(URL(target), c.getHeaderField("Location") ?: error("HTTP $code")).toString()
                    code == 401 || code == 403 -> throw StartListException("OFeed afviste løbs-id eller adgangskode (HTTP $code)")
                    code == 404 -> throw StartListException("Løbet findes ikke på OFeed (HTTP 404)")
                    else -> throw StartListException("OFeed svarede HTTP $code")
                }
            } finally {
                c.disconnect()
            }
        }
        throw StartListException("For mange omdirigeringer")
    }

    private fun dateHeader(c: HttpURLConnection): String? = c.getHeaderField("Date")?.let {
        runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toString() }.getOrNull()
    }

    /**
     * Tidspunkt til næste "lastUpdate": serverens tid fra sidste hentning minus en overlapning,
     * så en ændring der sker mens vi henter ikke går tabt (sammenfletningen tåler gentagelser).
     */
    fun nextCursor(serverTime: String?): String? =
        serverTime?.let { runCatching { Instant.parse(it).minusSeconds(120).toString() }.getOrNull() }
}
