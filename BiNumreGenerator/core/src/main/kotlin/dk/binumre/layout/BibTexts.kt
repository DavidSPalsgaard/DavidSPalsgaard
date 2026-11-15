package dk.binumre.layout

/** De faste tekster på startnummeret på ét sprog. */
data class BibTexts(
    val startTime: String,
    val cls: String,
    val course: String,
    val start: String,
    val distance: String,
    val leg: String,
    val stage: String,
    val freeStart: String,
) {
    companion object {
        /** Sprogkoder som i SI Startinfo (ukendt sprog giver engelsk). */
        fun of(language: String): BibTexts = ALL[language.lowercase().take(2)] ?: ALL.getValue("en")

        val languages: List<String> get() = ALL.keys.toList()

        // Starttid, klasse, bane, start og fri start er de samme ord som i SI Startinfo
        private val ALL: Map<String, BibTexts> = mapOf(
            "en" to BibTexts("Start time", "Class", "Course", "Start", "Distance to start", "Leg", "Stage", "Free start"),
            "da" to BibTexts("Starttid", "Klasse", "Bane", "Start", "Afstand til start", "Tur", "Etape", "Fri start"),
            "sv" to BibTexts("Starttid", "Klass", "Bana", "Start", "Avstånd till start", "Sträcka", "Etapp", "Fri start"),
            "nb" to BibTexts("Starttid", "Klasse", "Løype", "Start", "Avstand til start", "Etappe", "Dag", "Fri start"),
            "fi" to BibTexts("Lähtöaika", "Sarja", "Rata", "Lähtö", "Matka lähtöön", "Osuus", "Päivä", "Vapaa lähtö"),
            "de" to BibTexts("Startzeit", "Kategorie", "Bahn", "Start", "Weg zum Start", "Strecke", "Etappe", "Freier Start"),
            "nl" to BibTexts("Starttijd", "Categorie", "Parcours", "Start", "Afstand naar start", "Etappe", "Dag", "Vrije start"),
            "fr" to BibTexts("Heure de départ", "Catégorie", "Circuit", "Départ", "Distance au départ", "Relais", "Étape", "Départ libre"),
            "es" to BibTexts("Hora de salida", "Categoría", "Recorrido", "Salida", "Distancia a la salida", "Posta", "Etapa", "Salida libre"),
            "pt" to BibTexts("Hora de partida", "Escalão", "Percurso", "Partida", "Distância para a partida", "Percurso", "Etapa", "Partida livre"),
            "it" to BibTexts("Ora di partenza", "Categoria", "Percorso", "Partenza", "Distanza alla partenza", "Frazione", "Tappa", "Partenza libera"),
            "pl" to BibTexts("Czas startu", "Kategoria", "Trasa", "Start", "Odległość do startu", "Zmiana", "Etap", "Start dowolny"),
            "cs" to BibTexts("Čas startu", "Kategorie", "Trať", "Start", "Vzdálenost na start", "Úsek", "Etapa", "Volný start"),
            "sk" to BibTexts("Čas štartu", "Kategória", "Trať", "Štart", "Vzdialenosť na štart", "Úsek", "Etapa", "Voľný štart"),
            "sl" to BibTexts("Čas starta", "Kategorija", "Proga", "Start", "Razdalja do starta", "Predaja", "Etapa", "Prosti start"),
            "hr" to BibTexts("Vrijeme starta", "Kategorija", "Staza", "Start", "Udaljenost do starta", "Dionica", "Etapa", "Slobodan start"),
            "hu" to BibTexts("Rajtidő", "Kategória", "Pálya", "Rajt", "Távolság a rajtig", "Szakasz", "Nap", "Szabad rajt"),
            "ro" to BibTexts("Ora de start", "Categorie", "Traseu", "Start", "Distanța până la start", "Etapă", "Ziua", "Start liber"),
            "bg" to BibTexts("Начален час", "Категория", "Трасе", "Старт", "Разстояние до старта", "Етап", "Ден", "Свободен старт"),
            "el" to BibTexts("Ώρα εκκίνησης", "Κατηγορία", "Διαδρομή", "Εκκίνηση", "Απόσταση έως την εκκίνηση", "Σκέλος", "Ημέρα", "Ελεύθερη εκκίνηση"),
            "et" to BibTexts("Stardiaeg", "Klass", "Rada", "Start", "Kaugus starti", "Etapp", "Päev", "Vaba start"),
            "lv" to BibTexts("Starta laiks", "Grupa", "Distance", "Starts", "Attālums līdz startam", "Posms", "Diena", "Brīvais starts"),
            "lt" to BibTexts("Starto laikas", "Grupė", "Trasa", "Startas", "Atstumas iki starto", "Etapas", "Diena", "Laisvas startas"),
            "uk" to BibTexts("Час старту", "Група", "Дистанція", "Старт", "Відстань до старту", "Етап", "День", "Вільний старт"),
        )
    }
}
