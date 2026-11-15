package dk.binumre

import dk.binumre.startlist.Csv
import dk.binumre.startlist.NameFormat
import dk.binumre.startlist.StartListParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class StartListParserTest {

    private val zone = ZoneId.of("Europe/Copenhagen")
    private val today = LocalDate.of(2026, 3, 29)

    private fun clock(millis: Long?) = millis?.let {
        ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), zone).toLocalTime().toString()
    }

    @Test
    fun meosIof3StartList() {
        val bytes = javaClass.getResourceAsStream("/startliste_eksempel.xml")!!.readBytes()
        val list = StartListParser.parse(bytes, zone, today)
        assertEquals("Testløbet", list.event)
        assertEquals("2026-03-29", list.date)
        assertEquals(3, list.entries.size)

        val e = list.entries.first { it.card == 8000001L }
        assertEquals("Anna Marie Eksempel", e.displayName(NameFormat.GIVEN_FAMILY))
        assertEquals("Eksempel, Anna Marie", e.displayName(NameFormat.FAMILY_GIVEN))
        assertEquals("101", e.bib)
        assertEquals("D21", e.cls)
        assertEquals("OK Testklub", e.club)
        assertEquals("3", e.course)
        assertEquals("S1", e.startName)
        assertEquals("11:41", clock(e.start))
        // Uden starttid = fri start
        assertNull(list.entries.first { it.card == 500123L }.start)
    }

    @Test
    fun csvWithDanishHeaders() {
        val csv = """
            Fornavn;Efternavn;Klub;Klasse;Startnr;Starttid;Brik
            Anna;Hansen;OK Ø;D21;12;10:32:00;2004567
            Bo;Jensen;FIF;H21;13;;123456
        """.trimIndent()
        val list = StartListParser.parse(csv.toByteArray(), zone, today)
        assertEquals(2, list.entries.size)
        val a = list.entries[0]
        assertEquals("Anna Hansen", a.displayName(NameFormat.GIVEN_FAMILY))
        assertEquals(2004567L, a.card)
        assertEquals("10:32", clock(a.start))
        assertNull(list.entries[1].start)
    }

    @Test
    fun csvInOChecklistOrderWithoutHeaders() {
        val csv = "\"Hansen, Anna\",OK Ø,D21,12,10:32:00,2004567,S1\n// kommentar\nBo Jensen,FIF,H21,13,10:33,123456,S1\n"
        val list = StartListParser.parse(csv.toByteArray(), zone, today)
        assertEquals(2, list.entries.size)
        assertEquals("Anna", list.entries[0].given)
        assertEquals("Hansen", list.entries[0].family)
        assertEquals("Bo Jensen", list.entries[1].displayName(NameFormat.GIVEN_FAMILY))
        assertEquals("S1", list.entries[1].startName)
    }

    @Test
    fun csvQuotes() {
        val rows = Csv.parse("a,\"b, c\",\"d \"\"e\"\"\"\r\n1,2,3")
        assertEquals(listOf("a", "b, c", "d \"e\""), rows[0])
        assertEquals(listOf("1", "2", "3"), rows[1])
    }

    @Test
    fun excelTimeValues() {
        val csv = "Name,Class,Time,Card\nA,H21,0.4375,1\nB,H21,10.30,2\n"
        val list = StartListParser.parse(csv.toByteArray(), zone, today)
        assertEquals("10:30", clock(list.entries[0].start))
        assertEquals("10:30", clock(list.entries[1].start))
        assertTrue(list.entries.all { it.cls == "H21" })
    }
}
