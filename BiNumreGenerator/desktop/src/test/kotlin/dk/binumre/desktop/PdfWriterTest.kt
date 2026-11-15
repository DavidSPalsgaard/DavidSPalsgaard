package dk.binumre.desktop

import dk.binumre.model.Bib
import dk.binumre.model.BibProject
import dk.binumre.model.BibStage
import dk.binumre.model.Sheet
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfWriterTest {
    private fun bib(n: Int, name: String = "Løber $n") = Bib(
        number = n.toString(), given = "", family = "", name = name, club = "OK Testklub", card = 8000000L + n,
        cls = "H21", stages = listOf(BibStage(null, "H21", "1", 5000, "S1")),
    )

    @Test
    fun toPrArkOgEtPrSide() {
        val bibs = (1..5).map { bib(it) }
        val out = File.createTempFile("bib", ".pdf")
        try {
            PdfWriter.write(bibs, BibProject(), out)
            Loader.loadPDF(out).use { assertEquals(3, it.numberOfPages) }
            PdfWriter.write(bibs, BibProject(sheet = Sheet.A5), out)
            Loader.loadPDF(out).use { doc ->
                assertEquals(5, doc.numberOfPages)
                assertEquals(210.0, doc.getPage(0).mediaBox.width / 72.0 * 25.4, 0.1)
            }
        } finally {
            out.delete()
        }
    }

    @Test
    fun europaeiskeTegnOgUkendteTegn() {
        val out = File.createTempFile("bib", ".pdf")
        try {
            PdfWriter.write(listOf(bib(7, "Ærø Ødegård Żółć Ελένη Олена 山")), BibProject(language = "el"), out)
            val text = Loader.loadPDF(out).use { PDFTextStripper().getText(it) }
            assertTrue(text, "Ærø Ødegård Żółć Ελένη Олена ?" in text)
        } finally {
            out.delete()
        }
    }
}
