package dk.binumre.desktop.ui

import dk.binumre.layout.BibTexts
import java.io.InputStreamReader
import java.util.Locale
import java.util.Properties

/**
 * Programmets egne tekster på ét sprog. Hvert sprog er en fil `i18n/<kode>.properties` (UTF-8);
 * nøgler der mangler, tages fra engelsk. Et nyt sprog tilføjes ved at oversætte `en.properties`.
 */
class Strings(val language: String, private val texts: Map<String, String>, private val fallback: Map<String, String>) {

    operator fun get(key: String): String = texts[key] ?: fallback[key] ?: key

    /** Teksten med {0}, {1} … erstattet af [args]. */
    fun format(key: String, vararg args: Any?): String =
        args.foldIndexed(get(key)) { i, s, a -> s.replace("{$i}", a.toString()) }

    companion object {
        const val FALLBACK = "en"

        /** Sprog der har en sprogfil – blandt startnummerets sprog. */
        val available: List<String> by lazy { BibTexts.languages.filter { resource(it) != null } }

        fun load(language: String): Strings {
            val lang = if (language in available) language else FALLBACK
            return Strings(lang, read(lang), if (lang == FALLBACK) emptyMap() else read(FALLBACK))
        }

        /** Computerens sprog, hvis det findes; ellers engelsk. */
        fun systemLanguage(): String = Locale.getDefault().language.let { l ->
            when {
                l in available -> l
                l == "no" || l == "nn" -> "nb".takeIf { it in available } ?: FALLBACK
                else -> FALLBACK
            }
        }

        /** Sprogets eget navn, fx "dansk" og "English". */
        fun languageName(code: String): String = Locale.forLanguageTag(code).let { it.getDisplayLanguage(it) }
            .replaceFirstChar { it.uppercase() }.ifEmpty { code }

        fun read(language: String): Map<String, String> {
            val stream = resource(language) ?: return emptyMap()
            val p = Properties()
            InputStreamReader(stream, Charsets.UTF_8).use(p::load)
            return p.entries.associate { it.key.toString() to it.value.toString() }
        }

        private fun resource(language: String) = Strings::class.java.getResourceAsStream("/i18n/$language.properties")
    }
}
