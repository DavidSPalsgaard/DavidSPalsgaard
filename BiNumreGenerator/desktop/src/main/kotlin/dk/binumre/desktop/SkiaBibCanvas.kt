package dk.binumre.desktop

import dk.binumre.layout.Align
import dk.binumre.layout.BibCanvas
import dk.binumre.layout.BibFont
import dk.binumre.layout.BibLayout
import dk.binumre.model.Bib
import dk.binumre.model.BibProject
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontEdging
import org.jetbrains.skia.FontHinting
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import java.io.File

/** De samme skrifter som i PDF'en (DejaVu Sans fra core), så skærm og udskrift måler ens. */
object SkiaFonts {
    private val faces: Map<BibFont, Typeface> by lazy {
        mapOf(BibFont.REGULAR to load("DejaVuSans.ttf"), BibFont.BOLD to load("DejaVuSans-Bold.ttf"))
    }

    private fun load(name: String): Typeface {
        val bytes = BibLayout::class.java.getResourceAsStream("/fonts/$name")!!.use { it.readBytes() }
        return FontMgr.default.makeFromData(Data.makeFromBytes(bytes)) ?: error("Kan ikke læse skriften $name")
    }

    fun font(font: BibFont, size: Float): Font = Font(faces.getValue(font), size).apply {
        hinting = FontHinting.NONE
        isSubpixel = true
        edging = FontEdging.ANTI_ALIAS
    }
}

/** Billeder til forhåndsvisningen; læses igen når filen ændres. */
class ImageCache {
    private class Item(val modified: Long, val image: Image?)
    private val items = HashMap<String, Item>()

    fun get(path: String): Image? {
        val file = File(path)
        val modified = if (file.isFile) file.lastModified() else -1L
        val item = items[path]
        if (item != null && item.modified == modified) return item.image
        val image = if (modified < 0) null else runCatching { Image.makeFromEncoded(file.readBytes()) }.getOrNull()
        items[path] = Item(modified, image)
        return image
    }
}

/**
 * [BibCanvas] oven på et Skia-lærred (Compose på PC). [scale] er pixels pr. mm.
 * Bruges både af forhåndsvisningen og af [BibImage].
 */
class SkiaBibCanvas(
    private val canvas: Canvas,
    private val scale: Float,
    private val images: (String) -> Image?,
) : BibCanvas {

    private fun px(mm: Double) = (mm * scale).toFloat()
    private fun paint(color: Int) = Paint().apply { this.color = 0xFF000000.toInt() or color; isAntiAlias = true }

    /** Tegn skriften ikke har, erstattes med "?" – som i PDF'en. */
    private fun clean(text: String, font: Font): String = buildString {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (font.getUTF32Glyph(cp).toInt() != 0) appendCodePoint(cp) else append('?')
            i += Character.charCount(cp)
        }
    }

    private fun width(text: String, font: BibFont, size: Double): Double {
        // Måles i en stor, fast størrelse, så bredden ikke afhænger af zoom
        val f = SkiaFonts.font(font, MEASURE)
        return f.measureTextWidth(clean(text, f)) / MEASURE * size
    }

    override fun textWidth(text: String, font: BibFont, size: Double): Double = width(text, font, size)

    override fun text(text: String, x: Double, baseline: Double, font: BibFont, size: Double, align: Align, color: Int) {
        val f = SkiaFonts.font(font, px(size))
        val w = width(text, font, size)
        val left = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - w / 2
            Align.RIGHT -> x - w
        }
        canvas.drawString(clean(text, f), px(left), px(baseline), f, paint(color))
    }

    override fun rect(x: Double, y: Double, width: Double, height: Double, color: Int) {
        canvas.drawRect(Rect.makeXYWH(px(x), px(y), px(width), px(height)), paint(color).apply { isAntiAlias = false })
    }

    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, width: Double, color: Int) {
        canvas.drawLine(px(x1), px(y1), px(x2), px(y2), paint(color).apply { mode = PaintMode.STROKE; strokeWidth = px(width) })
    }

    override fun image(file: String, x: Double, y: Double, width: Double, height: Double) {
        val img = images(file) ?: return
        val s = minOf(width / img.width, height / img.height)
        val w = img.width * s
        val h = img.height * s
        canvas.drawImageRect(
            img, Rect.makeWH(img.width.toFloat(), img.height.toFloat()),
            Rect.makeXYWH(px(x + (width - w) / 2), px(y + (height - h) / 2), px(w), px(h)),
            FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), null, true,
        )
    }

    override fun rotated180(cx: Double, cy: Double, block: () -> Unit) {
        canvas.save()
        canvas.rotate(180f, px(cx), px(cy))
        block()
        canvas.restore()
    }

    private companion object {
        const val MEASURE = 1000f
    }
}

/** Ét startnummer som PNG – samme tegning som forhåndsvisningen. */
object BibImage {
    fun png(bib: Bib, project: BibProject, pixelsPerMm: Float = 6f, images: ImageCache = ImageCache()): ByteArray {
        val surface = Surface.makeRasterN32Premul((BibLayout.WIDTH * pixelsPerMm).toInt(), (BibLayout.HEIGHT * pixelsPerMm).toInt())
        surface.canvas.clear(0xFFFFFFFF.toInt())
        BibLayout.draw(SkiaBibCanvas(surface.canvas, pixelsPerMm, images::get), bib, project)
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }
}
