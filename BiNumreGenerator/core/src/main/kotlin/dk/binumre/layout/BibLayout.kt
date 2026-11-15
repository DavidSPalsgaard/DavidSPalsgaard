package dk.binumre.layout

import dk.binumre.model.Bib
import dk.binumre.model.BibProject
import dk.binumre.model.BibStage
import dk.binumre.model.BibType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Tegner ét startnummer i A5 liggende (210 × 148,5 mm):
 * øverst nummer, navn, klub, SI-nummer og stregkode – nederst, vendt på hovedet så løberen
 * selv kan læse det, starttid, klasse, bane, start og afstand til start (én linje pr. etape).
 */
object BibLayout {
    const val WIDTH = 210.0
    const val HEIGHT = 148.5

    private const val MARGIN = 8.0
    private const val GREY = 0x555555
    /** Højden af store bogstaver og cifre i forhold til skriftstørrelsen (DejaVu Sans). */
    private const val CAP = 0.729
    private const val LEG_BAND = 16.0
    private const val ROW = 7.5

    /** Højden af feltet i bunden: fast for ét løb, vokser med antal etaper. */
    fun bottomHeight(bib: Bib): Double = if (bib.stages.size > 1) max(26.0, 9.5 + ROW * bib.stages.size) else 26.0

    fun draw(c: BibCanvas, bib: Bib, project: BibProject, zone: ZoneId = zoneOf(project)) {
        val texts = BibTexts.of(project.language)
        val split = HEIGHT - bottomHeight(bib)

        // Rækkefølge: farvede flader, så grafik, så tekst – grafik må gerne ligge i turens bånd
        var top = 0.0
        val relay = project.type == BibType.RELAY && bib.leg.isNotEmpty()
        val color = if (relay) project.legColor(bib.leg) else 0
        if (relay) {
            c.rect(0.0, 0.0, WIDTH, LEG_BAND, color)
            // Også ved kanten løberen ser, når nummeret vippes op
            c.rect(0.0, HEIGHT - 3.0, WIDTH, 3.0, color)
        }
        for (g in project.graphics) c.image(g.file, g.x, g.y, g.width, g.height)
        if (relay) {
            c.text("${texts.leg} ${bib.leg}", WIDTH / 2, 12.2, BibFont.BOLD, 11.0, Align.CENTER, contrast(color))
            top = LEG_BAND
        }
        if (project.eventName.isNotEmpty()) {
            fit(c, project.eventName, WIDTH / 2, top + 8.5, BibFont.REGULAR, 6.5, WIDTH - 2 * MARGIN, Align.CENTER, GREY)
            top += 11.0
        } else top += 3.0

        // Nedefra: SI-nummer og stregkode, klub, navn – resten er til nummeret
        val barcodeHeight = 12.0
        val barcodeTop = split - 3.0 - barcodeHeight
        bib.card?.let { c.text("SI $it", MARGIN, split - 5.5, BibFont.REGULAR, 7.0) }
        barcode(c, bib.number, WIDTH - MARGIN, barcodeTop, barcodeHeight)

        val clubBase = barcodeTop - 4.0
        val club = if (relay && bib.teamName.isNotEmpty()) bib.teamName else bib.club
        fit(c, club, WIDTH / 2, clubBase, BibFont.REGULAR, 7.5, WIDTH - 2 * MARGIN, Align.CENTER)
        val nameBase = clubBase - 10.0
        fit(c, bib.displayName(project.nameFormat), WIDTH / 2, nameBase, BibFont.BOLD, 11.0, WIDTH - 2 * MARGIN, Align.CENTER)

        val areaBottom = nameBase - 11.0
        val size = fitSize(c, bib.number, BibFont.BOLD, (areaBottom - top - 7.0) / CAP, WIDTH - 2 * MARGIN)
        c.text(bib.number, WIDTH / 2, (top + areaBottom + size * CAP) / 2, BibFont.BOLD, size, Align.CENTER)

        c.line(0.0, split, WIDTH, split, 0.3)
        c.rotated180(WIDTH / 2, (split + HEIGHT) / 2) { bottom(c, bib, project, texts, zone, split) }
    }

    /** Feltet i bunden – tegnes retvendt her og drejes af [BibCanvas.rotated180]. */
    private fun bottom(c: BibCanvas, bib: Bib, project: BibProject, t: BibTexts, zone: ZoneId, y0: Double) {
        val locale = Locale.forLanguageTag(project.language)
        fun course(s: BibStage) = listOfNotNull(
            s.course.ifEmpty { null },
            s.courseLength?.let { String.format(locale, "%.1f km", it / 1000.0) },
        ).joinToString(" · ")

        if (bib.stages.size <= 1) {
            val s = bib.stages.firstOrNull() ?: return
            val stage = project.stage(0)
            val cols = listOf(
                Triple(t.startTime, time(s.start, zone, t), 46.0),
                Triple(t.cls, s.cls, 36.0),
                Triple(t.course, course(s), 44.0),
                Triple(t.start, s.startName, 30.0),
                Triple(t.distance, stage.distance(s.startName), 38.0),
            )
            var x = MARGIN
            for ((i, col) in cols.withIndex()) {
                val (label, value, w) = col
                if (value.isNotEmpty()) fit(c, label, x, y0 + 8.0, BibFont.REGULAR, 3.8, w - 3.0, Align.LEFT, GREY)
                fit(c, value, x, y0 + 20.0, BibFont.BOLD, if (i == 0) 12.0 else 9.0, w - 3.0, Align.LEFT)
                x += w
            }
            return
        }

        val widths = listOf(30.0, 34.0, 30.0, 40.0, 26.0, 34.0)
        val labels = listOf("", t.startTime, t.cls, t.course, t.start, t.distance)
        var x = MARGIN
        for ((i, w) in widths.withIndex()) {
            fit(c, labels[i], x, y0 + 6.0, BibFont.REGULAR, 3.4, w - 2.5, Align.LEFT, GREY)
            x += w
        }
        for ((i, s) in bib.stages.withIndex()) {
            val rowTop = y0 + 7.5 + ROW * i
            c.line(MARGIN, rowTop, WIDTH - MARGIN, rowTop, 0.15, GREY)
            val stage = project.stage(i)
            val values = listOf(
                stage.label.ifEmpty { "${t.stage} ${i + 1}" },
                if (s == null) "–" else time(s.start, zone, t),
                s?.cls.orEmpty(), s?.let(::course).orEmpty(), s?.startName.orEmpty(),
                s?.let { stage.distance(it.startName) }.orEmpty(),
            )
            x = MARGIN
            for ((j, w) in widths.withIndex()) {
                fit(c, values[j], x, rowTop + 5.6, if (j == 1) BibFont.BOLD else BibFont.REGULAR, 5.4, w - 2.5, Align.LEFT)
                x += w
            }
        }
    }

    private fun barcode(c: BibCanvas, text: String, right: Double, top: Double, height: Double) {
        val modules = Code128.modules(text)
        val unit = min(0.5, 80.0 / modules.size)
        val left = right - modules.size * unit
        var i = 0
        while (i < modules.size) {
            if (!modules[i]) { i++; continue }
            var j = i
            while (j < modules.size && modules[j]) j++
            c.rect(left + i * unit, top, (j - i) * unit, height, 0x000000)
            i = j
        }
    }

    fun zoneOf(project: BibProject): ZoneId =
        project.zone.ifEmpty { null }?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

    /** "11:41", eller "11:41:30" når starttiden ikke er på et helt minut. Uden starttid: "Fri start". */
    fun time(start: Long?, zone: ZoneId, texts: BibTexts): String {
        if (start == null) return texts.freeStart
        val t = Instant.ofEpochMilli(start).atZone(zone)
        return DateTimeFormatter.ofPattern(if (t.second == 0) "HH:mm" else "HH:mm:ss").format(t)
    }

    /** Sort tekst på lyse farver, hvid på mørke. */
    fun contrast(color: Int): Int {
        val lum = 0.299 * (color shr 16 and 0xFF) + 0.587 * (color shr 8 and 0xFF) + 0.114 * (color and 0xFF)
        return if (lum > 150) 0x000000 else 0xFFFFFF
    }

    private fun fitSize(c: BibCanvas, text: String, font: BibFont, size: Double, maxWidth: Double): Double {
        val w = c.textWidth(text, font, size)
        return if (w <= maxWidth || w == 0.0) size else size * maxWidth / w
    }

    /** Skriver teksten og gør den mindre, hvis den er bredere end [maxWidth]. */
    private fun fit(c: BibCanvas, text: String, x: Double, baseline: Double, font: BibFont, size: Double, maxWidth: Double, align: Align, color: Int = 0x000000) {
        if (text.isEmpty()) return
        c.text(text, x, baseline, font, fitSize(c, text, font, size, maxWidth), align, color)
    }
}
