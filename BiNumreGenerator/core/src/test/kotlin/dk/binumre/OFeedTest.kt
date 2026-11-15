package dk.binumre

import dk.binumre.ofeed.OFeedClient
import dk.binumre.ofeed.OFeedConnection
import dk.binumre.ofeed.OFeedParser
import dk.binumre.ofeed.mergeChanges
import dk.binumre.startlist.NameFormat
import dk.binumre.startlist.StartEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class OFeedTest {

    @Test
    fun qrLinkFromOFeed() {
        // Som OFeed bygger linket til O Checklist (URL-kodede værdier)
        val link = "https://stigning.se/ofeed?url=https%3A%2F%2Fapi.orienteerfeed.com%2Frest%2Fv1%2Fevents&auth=basic&id=testloeb123&pwd=a%2Bb%26c%3D1"
        val c = OFeedConnection.fromLink(link)!!
        assertEquals("https://api.orienteerfeed.com/rest/v1/events", c.url)
        assertEquals("testloeb123", c.eventId)
        assertEquals("a+b&c=1", c.password)
        assertEquals("https://api.orienteerfeed.com/rest/v1/events/testloeb123/competitors", c.competitorsUrl)
    }

    @Test
    fun qrLinkWithLineBreakAndNoUrl() {
        val c = OFeedConnection.fromLink("https://stigning.se/ofeed?auth=basic&id=\n abc123 &pwd=x")!!
        assertEquals("abc123", c.eventId)
        assertEquals(OFeedConnection.DEFAULT_URL, c.url)
        assertNull(OFeedConnection.fromLink("https://example.com/ingen-parametre"))
        assertNull(OFeedConnection.fromLink("hej"))
    }

    @Test
    fun individualCompetitors() {
        val json = """
            {"message":"OK","error":false,"code":200,
             "results":{"data":{"name":"Testløbet","classes":[
               {"id":1,"name":"W21E","startName":"Start2","length":6050,"competitors":[
                 {"id":289235,"lastname":"Eksempel","firstname":"Anna Marie","organisation":"OK Testklub","shortName":"OKT",
                  "bibNumber":101,"card":8000001,"startTime":"2026-03-29T09:41:00.000Z","status":"Inactive"},
                 {"id":289236,"lastname":"Hansen","firstname":"Bo","organisation":null,"shortName":"FIF",
                  "bibNumber":null,"card":null,"startTime":null,"status":"Inactive"}]}]}},
             "meta":{"timestamp":"2026-10-26T10:00:00.000Z"}}
        """.trimIndent()
        val r = OFeedParser.parse(json)
        assertEquals("Testløbet", r.event)
        assertEquals("2026-10-26T10:00:00.000Z", r.serverTime)
        assertEquals(2, r.entries.size)
        val e = r.entries[0]
        assertEquals("289235", e.id)
        assertEquals("Anna Marie Eksempel", e.displayName(NameFormat.GIVEN_FAMILY))
        assertEquals("OK Testklub", e.club)
        assertEquals("W21E", e.cls)
        assertEquals("101", e.bib)
        assertEquals(8000001L, e.card)
        assertEquals(Instant.parse("2026-03-29T09:41:00Z").toEpochMilli(), e.start)
        assertEquals("Start2", e.startName)
        val b = r.entries[1]
        assertEquals("FIF", b.club)
        assertEquals("", b.bib)
        assertNull(b.card)
        assertNull(b.start)
    }

    @Test
    fun relayTeams() {
        val json = """
            {"results":{"data":{"name":"Stafet","classes":[{"name":"H21","teams":[
              {"id":5,"name":"OK Ø 1","bibNumber":12,"competitors":[
                {"id":11,"leg":1,"lastname":"A","firstname":"Anna","card":2001,"bibNumber":"12.1","startTime":"2026-10-26T09:00:00Z"},
                {"id":12,"leg":2,"lastname":"B","firstname":"Bo","card":2002,"bibNumber":"12.2","startTime":null}]}]}]}}}
        """.trimIndent()
        val r = OFeedParser.parse(json)
        assertEquals(2, r.entries.size)
        assertEquals("OK Ø 1 · 2", r.entries[1].team)
        assertEquals("12.2", r.entries[1].bib)
        assertEquals(2002L, r.entries[1].card)
    }

    @Test
    fun mergeReplacesChangedAndAddsNew() {
        val old = listOf(StartEntry(id = "1", given = "A", card = 100), StartEntry(id = "2", given = "B", card = 200))
        val changed = listOf(StartEntry(id = "2", given = "B", card = 999), StartEntry(id = "3", given = "C", card = 300))
        val merged = mergeChanges(old, changed).associateBy { it.id }
        assertEquals(3, merged.size)
        assertEquals(999L, merged["2"]!!.card)
        assertEquals(100L, merged["1"]!!.card)
    }

    @Test
    fun startedAndCheckIn() {
        val json = """
            {"results":{"data":{"name":"X","classes":[{"name":"H21","competitors":[
              {"id":1,"lastname":"A","firstname":"Anna","status":"Active","startTime":"2026-10-26T10:00:00Z"},
              {"id":2,"lastname":"B","firstname":"Bo","status":"Inactive","startTime":"2026-10-26T10:00:00Z"},
              {"id":3,"lastname":"C","firstname":"Cy","status":"DidNotStart","startTime":null}]}]}}}
        """.trimIndent()
        val (a, b, c) = OFeedParser.parse(json).entries
        assertEquals(true, a.started)
        assertEquals(false, b.started)
        assertEquals(false, c.started)
        // Check-in 4 minutter før start; fri start har ingen check-in
        assertEquals(Instant.parse("2026-10-26T09:56:00Z").toEpochMilli(), b.checkIn(4))
        assertNull(c.checkIn(4))
        // Fil/URL-lister har ingen status
        assertEquals(false, StartEntry(given = "X").started)
    }

    @Test
    fun eventDateOverrideAndLate() {
        val cph = java.time.ZoneId.of("Europe/Copenhagen")
        // Start 11:41 dansk tid den 29. marts 
        val e = StartEntry(start = java.time.ZonedDateTime.of(2026, 3, 29, 11, 41, 0, 0, cph).toInstant().toEpochMilli())
        val moved = e.onDate(java.time.LocalDate.of(2026, 10, 26), cph)
        assertEquals(java.time.ZonedDateTime.of(2026, 10, 26, 11, 41, 0, 0, cph).toInstant().toEpochMilli(), moved.start)
        assertEquals(e, e.onDate(null, cph))

        val start = moved.start!!
        // Check-in 11:37: kl. 11:36 er ikke forsendt, kl. 11:38 er forsendt – men ikke hvis startet
        assertEquals(false, moved.late(4, start - 5 * 60_000))
        assertEquals(true, moved.late(4, start - 3 * 60_000))
        assertEquals(false, moved.copy(status = "Active").late(4, start - 3 * 60_000))
        // Et løb for længe siden er ikke "forsendt"
        assertEquals(false, moved.late(4, start + 24 * 3600_000L))
    }

    @Test
    fun cursorOverlapsTwoMinutes() {
        assertEquals("2026-10-26T09:58:00Z", OFeedClient.nextCursor("2026-10-26T10:00:00.000Z"))
        assertNull(OFeedClient.nextCursor(null))
    }
}
