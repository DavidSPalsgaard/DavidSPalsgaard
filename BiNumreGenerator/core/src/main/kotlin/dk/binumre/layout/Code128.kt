package dk.binumre.layout

/** Code 128-stregkode: sæt C for et lige antal cifre (mindst 4), ellers sæt B. */
object Code128 {
    private const val START_B = 104
    private const val START_C = 105
    private const val STOP = 106

    // Bredder (streg, mellemrum, streg …) for symbol 0-106
    private val PATTERNS = ("212222 222122 222221 121223 121322 131222 122213 122312 132212 221213 221312 231212 " +
        "112232 122132 122231 113222 123122 123221 223211 221132 221231 213212 223112 312131 311222 321122 321221 " +
        "312212 322112 322211 212123 212321 232121 111323 131123 131321 112313 132113 132311 211313 231113 231311 " +
        "112133 112331 132131 113123 113321 133121 313121 211331 231131 213113 213311 213131 311123 311321 331121 " +
        "312113 312311 332111 314111 221411 431111 111224 111422 121124 121421 141122 141221 112214 112412 122114 " +
        "122411 142112 142211 241211 221114 413111 241112 134111 111242 121142 121241 114212 124112 124211 411212 " +
        "421112 421211 212141 214121 412121 111143 111341 131141 114113 114311 411113 411311 113141 114131 311141 " +
        "411131 211412 211214 211232 2331112").split(' ')

    /** Symbolværdierne inkl. start, kontrolciffer og stop. Tegn uden for ASCII 32-126 udelades. */
    fun symbols(text: String): List<Int> {
        val clean = text.filter { it.code in 32..126 }
        val useC = clean.length >= 4 && clean.length % 2 == 0 && clean.all { it.isDigit() }
        val data = if (useC) clean.chunked(2).map { it.toInt() } else clean.map { it.code - 32 }
        val start = if (useC) START_C else START_B
        val check = (start + data.withIndex().sumOf { (i, v) -> (i + 1) * v }) % 103
        return listOf(start) + data + check + STOP
    }

    /** Stregkoden som moduler: true = sort. */
    fun modules(text: String): BooleanArray {
        val out = ArrayList<Boolean>()
        for (s in symbols(text)) {
            var bar = true
            for (w in PATTERNS[s]) {
                repeat(w - '0') { out += bar }
                bar = !bar
            }
        }
        return out.toBooleanArray()
    }
}
