package dk.binumre.model

import dk.binumre.startlist.NameFormat
import org.json.JSONArray
import org.json.JSONObject

enum class BibType { SINGLE, RELAY, MULTIDAY }

enum class Sheet { A4_2UP, A5 }

enum class SortBy { BIB, CLUB, CLASS, NAME }

/** Et stykke grafik (logo, sponsor …) på startnummeret. Mål i mm fra øverste venstre hjørne. */
data class Graphic(val file: String, val x: Double, val y: Double, val width: Double, val height: Double)

/** Ét løb/én etape: overskrift i bunden og afstand til start pr. startsted ("" = alle startsteder). */
data class Stage(val label: String = "", val distances: Map<String, String> = emptyMap()) {
    fun distance(startName: String): String = distances[startName] ?: distances[""] ?: ""
}

/** Opsætningen af et løb: skabelon, grafik, farver og tekster. Gemmes som JSON. */
data class BibProject(
    val type: BibType = BibType.SINGLE,
    val eventName: String = "",
    /** Sprog på selve startnummeret (uafhængigt af programmets sprog). */
    val language: String = "da",
    val nameFormat: NameFormat = NameFormat.GIVEN_FAMILY,
    val sheet: Sheet = Sheet.A4_2UP,
    val sortBy: SortBy = SortBy.BIB,
    /** Tidszone for starttider (IANA-navn); tom = computerens. */
    val zone: String = "",
    val graphics: List<Graphic> = emptyList(),
    /** Stafet: farve pr. tur (#RRGGBB). Flere ture end farver → farverne gentages. */
    val legColors: List<String> = DEFAULT_LEG_COLORS,
    /** Enkeltløb/stafet: ét element. Flerdagesløb: ét pr. etape i samme rækkefølge som startlisterne. */
    val stages: List<Stage> = emptyList(),
) {
    init {
        require(graphics.size <= MAX_GRAPHICS) { "Højst $MAX_GRAPHICS stykker grafik" }
    }

    fun stage(index: Int): Stage = stages.getOrNull(index) ?: Stage()

    fun legColor(leg: String): Int {
        val n = leg.filter { it.isDigit() }.toIntOrNull() ?: 1
        if (legColors.isEmpty()) return 0xFFFFFF
        return parseColor(legColors[(n - 1).mod(legColors.size)])
    }

    fun toJson(): String = JSONObject().apply {
        put("type", type.name)
        put("eventName", eventName)
        put("language", language)
        put("nameFormat", nameFormat.name)
        put("sheet", sheet.name)
        put("sortBy", sortBy.name)
        put("zone", zone)
        put("graphics", JSONArray(graphics.map {
            JSONObject().put("file", it.file).put("x", it.x).put("y", it.y).put("width", it.width).put("height", it.height)
        }))
        put("legColors", JSONArray(legColors))
        put("stages", JSONArray(stages.map { JSONObject().put("label", it.label).put("distances", JSONObject(it.distances)) }))
    }.toString(2)

    companion object {
        const val MAX_GRAPHICS = 10
        val DEFAULT_LEG_COLORS = listOf("#FFD400", "#3B82F6", "#22C55E", "#EF4444", "#F97316", "#A855F7", "#14B8A6", "#EC4899")

        fun fromJson(json: String): BibProject {
            val o = JSONObject(json)
            val d = BibProject()
            return BibProject(
                type = o.optString("type").uppercase().let { n -> BibType.entries.firstOrNull { it.name == n } } ?: d.type,
                eventName = o.optString("eventName"),
                language = o.optString("language", d.language),
                nameFormat = NameFormat.entries.firstOrNull { it.name == o.optString("nameFormat") } ?: d.nameFormat,
                sheet = Sheet.entries.firstOrNull { it.name == o.optString("sheet") } ?: d.sheet,
                sortBy = SortBy.entries.firstOrNull { it.name == o.optString("sortBy") } ?: d.sortBy,
                zone = o.optString("zone"),
                graphics = o.optJSONArray("graphics").objects().map {
                    Graphic(it.getString("file"), it.optDouble("x", 0.0), it.optDouble("y", 0.0), it.optDouble("width", 30.0), it.optDouble("height", 30.0))
                },
                legColors = o.optJSONArray("legColors")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: d.legColors,
                stages = o.optJSONArray("stages").objects().map { s ->
                    val dist = s.optJSONObject("distances")
                    Stage(s.optString("label"), dist?.keySet()?.associateWith { dist.optString(it) } ?: emptyMap())
                },
            )
        }

        /** "#RRGGBB" → 0xRRGGBB (ukendt tekst giver hvid). */
        fun parseColor(hex: String): Int = hex.trim().removePrefix("#").toIntOrNull(16)?.and(0xFFFFFF) ?: 0xFFFFFF

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
