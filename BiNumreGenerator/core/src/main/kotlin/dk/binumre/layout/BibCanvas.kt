package dk.binumre.layout

enum class BibFont { REGULAR, BOLD }

enum class Align { LEFT, CENTER, RIGHT }

/**
 * Tegnefladen for ét startnummer. Alle mål er i mm med (0,0) i øverste venstre hjørne;
 * skriftstørrelsen er skriftens højde (em) i mm. Farver er 0xRRGGBB.
 * PC-programmet tegner til PDF (PDFBox); Android-appen får sin egen udgave.
 */
interface BibCanvas {
    fun textWidth(text: String, font: BibFont, size: Double): Double
    fun text(text: String, x: Double, baseline: Double, font: BibFont, size: Double, align: Align = Align.LEFT, color: Int = 0x000000)
    fun rect(x: Double, y: Double, width: Double, height: Double, color: Int)
    fun line(x1: Double, y1: Double, x2: Double, y2: Double, width: Double, color: Int = 0x000000)

    /** Tegner billedet så stort som muligt inden for rammen uden at ændre forholdet. */
    fun image(file: String, x: Double, y: Double, width: Double, height: Double)

    /** Alt tegnet i [block] drejes 180° om punktet – bruges til teksten løberen selv læser. */
    fun rotated180(cx: Double, cy: Double, block: () -> Unit)
}
