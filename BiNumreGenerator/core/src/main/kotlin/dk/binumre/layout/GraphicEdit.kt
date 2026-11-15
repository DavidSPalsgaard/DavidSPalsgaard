package dk.binumre.layout

import dk.binumre.model.Graphic
import kotlin.math.abs
import kotlin.math.roundToLong

/** Hvor på et stykke grafik musen (eller fingeren) har fat: hele rammen eller et hjørne. */
enum class Handle { MOVE, NW, NE, SW, SE }

/** Flytning og størrelsesændring af grafik i editoren – i mm, uafhængigt af brugerfladen. */
object GraphicEdit {
    const val MIN_SIZE = 5.0

    /**
     * Hvad der ligger under punktet: først hjørnerne på den valgte grafik (inden for [tolerance] mm),
     * ellers den øverste grafik der dækker punktet. Null når punktet rammer ved siden af.
     */
    fun hit(graphics: List<Graphic>, x: Double, y: Double, selected: Int?, tolerance: Double): Pair<Int, Handle>? {
        graphics.getOrNull(selected ?: -1)?.let { g ->
            val corners = listOf(
                Handle.NW to (g.x to g.y), Handle.NE to (g.x + g.width to g.y),
                Handle.SW to (g.x to g.y + g.height), Handle.SE to (g.x + g.width to g.y + g.height),
            )
            corners.firstOrNull { (_, p) -> abs(p.first - x) <= tolerance && abs(p.second - y) <= tolerance }
                ?.let { return selected!! to it.first }
        }
        val i = graphics.indexOfLast { x >= it.x && x <= it.x + it.width && y >= it.y && y <= it.y + it.height }
        return if (i < 0) null else i to Handle.MOVE
    }

    /** Grafikken efter et træk på ([dx], [dy]) mm fra udgangspunktet [start]. Holdes inden for startnummeret. */
    fun drag(start: Graphic, handle: Handle, dx: Double, dy: Double): Graphic {
        var left = start.x
        var top = start.y
        var right = start.x + start.width
        var bottom = start.y + start.height
        when (handle) {
            Handle.MOVE -> {
                left = (left + dx).coerceIn(0.0, max0(BibLayout.WIDTH - start.width))
                top = (top + dy).coerceIn(0.0, max0(BibLayout.HEIGHT - start.height))
                right = left + start.width
                bottom = top + start.height
            }
            else -> {
                if (handle == Handle.NW || handle == Handle.SW) left = (left + dx).coerceIn(0.0, max0(right - MIN_SIZE))
                else right = (right + dx).coerceIn(left + MIN_SIZE, maxOf(BibLayout.WIDTH, left + MIN_SIZE))
                if (handle == Handle.NW || handle == Handle.NE) top = (top + dy).coerceIn(0.0, max0(bottom - MIN_SIZE))
                else bottom = (bottom + dy).coerceIn(top + MIN_SIZE, maxOf(BibLayout.HEIGHT, top + MIN_SIZE))
            }
        }
        return start.copy(x = round(left), y = round(top), width = round(right - left), height = round(bottom - top))
    }

    /** Ramme til et nyt billede på [pixelWidth] × [pixelHeight]: 40 mm bredt, forskudt lidt for hvert billede der er i forvejen. */
    fun place(file: String, pixelWidth: Int, pixelHeight: Int, existing: Int): Graphic {
        val width = 40.0
        val height = if (pixelWidth > 0 && pixelHeight > 0) (width * pixelHeight / pixelWidth).coerceIn(MIN_SIZE, 60.0) else 20.0
        val offset = 8.0 + 6.0 * existing
        return Graphic(file, offset, offset, width, round(height))
    }

    private fun max0(v: Double) = maxOf(0.0, v)

    /** Afrunder til 0,1 mm. */
    private fun round(v: Double) = (v * 10).roundToLong() / 10.0
}
