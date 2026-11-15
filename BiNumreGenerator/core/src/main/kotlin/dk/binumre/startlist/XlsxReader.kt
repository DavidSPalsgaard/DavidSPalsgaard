package dk.binumre.startlist

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal læser af Excel (.xlsx): første ark som tekst-rækker.
 * Tal (fx starttider) returneres som Excel-værdien, og [TimeParser] omsætter dem.
 */
object XlsxReader {

    private const val MAX_ENTRY = 50L * 1024 * 1024

    fun firstSheetRows(bytes: ByteArray): List<List<String>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.removePrefix("/")
                if (name.endsWith(".xml") || name.endsWith(".rels")) {
                    files[name] = readLimited(zip)
                }
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { xml(it) }
            ?.children("si")?.map { si -> si.getElementsByTagNameNS("*", "t").let { t -> (0 until t.length).joinToString("") { t.item(it).textContent } } }
            ?: emptyList()

        val sheet = files[firstSheetPath(files)] ?: throw StartListException("Excel-filen har intet regneark")
        val rows = mutableListOf<List<String>>()
        val data = xml(sheet).child("sheetData")
        for (row in data.children("row")) {
            val cells = mutableListOf<String>()
            for (c in row.children("c")) {
                val col = columnIndex(c.getAttribute("r")) ?: cells.size
                while (cells.size < col) cells += ""
                val v = c.child("v").text()
                cells += when (c.getAttribute("t")) {
                    "s" -> v.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> c.child("is").let { it?.getElementsByTagNameNS("*", "t")?.let { t -> (0 until t.length).joinToString("") { i -> t.item(i).textContent } } } ?: ""
                    else -> v
                }
            }
            if (cells.any { it.isNotBlank() }) rows += cells
        }
        return rows
    }

    /** Læser højst [MAX_ENTRY] bytes, så en manipuleret fil ikke kan fylde hukommelsen. */
    private fun readLimited(zip: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = zip.read(buf)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > MAX_ENTRY) throw StartListException("Excel-filen er for stor")
            out.write(buf, 0, n)
        }
    }

    /** Første ark i projektmappen (workbook.xml → relationer), ellers sheet1.xml. */
    private fun firstSheetPath(files: Map<String, ByteArray>): String {
        val fallback = "xl/worksheets/sheet1.xml"
        val wb = files["xl/workbook.xml"]?.let { xml(it) } ?: return fallback
        val first = wb.child("sheets").children("sheet").firstOrNull() ?: return fallback
        val rid = first.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
        val rels = files["xl/_rels/workbook.xml.rels"]?.let { xml(it) } ?: return fallback
        val target = rels.children("Relationship").firstOrNull { it.getAttribute("Id") == rid }?.getAttribute("Target")
            ?: return fallback
        return if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
    }

    private fun columnIndex(ref: String): Int? {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return null
        return letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
    }

    private fun xml(bytes: ByteArray): Element {
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    }
}
