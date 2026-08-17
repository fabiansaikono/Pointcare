package be.saikono.poincare.model

/** Stable catalogue shared by navigation, content availability and generated exercises. */
data class SubcategoryFocus(
    val id: String,
    val minGrade: Int = 1,
    val maxGrade: Int = 6
)

object SubcategoryCatalog {
    const val FOCUS_SEPARATOR = "__"

    val worldThemeIds: Set<String> = linkedSetOf(
        "space", "nature", "geography", "science", "history", "everyday_life",
        "animals", "human_body", "plants", "weather_climate", "technology",
        "arts_culture", "citizenship_society", "environment_sustainability",
        "traffic_safety"
    )

    val writingFocuses: Map<String, List<SubcategoryFocus>> = linkedMapOf(
        "singular_plural" to listOf(
            SubcategoryFocus("regular"),
            SubcategoryFocus("apostrophe", 2),
            SubcategoryFocus("f_v", 2),
            SubcategoryFocus("s_z", 2),
            SubcategoryFocus("irregular", 2)
        ),
        "conjugations" to listOf(
            SubcategoryFocus("first_person", 3),
            SubcategoryFocus("second_person", 3),
            SubcategoryFocus("third_person", 3),
            SubcategoryFocus("plural_persons", 3),
            SubcategoryFocus("inversion", 4)
        ),
        "verbs" to listOf(
            SubcategoryFocus("infinitive", 2),
            SubcategoryFocus("stem", 2),
            SubcategoryFocus("finite_verb", 2),
            SubcategoryFocus("verb_or_noun", 2),
            SubcategoryFocus("strong_weak", 4)
        ),
        "past_tense" to listOf(
            SubcategoryFocus("imperfect", 4),
            SubcategoryFocus("perfect", 4),
            SubcategoryFocus("kofschip", 5),
            SubcategoryFocus("irregular_past", 4),
            SubcategoryFocus("past_sentence", 4)
        ),
        "future_tense" to listOf(
            SubcategoryFocus("will_future", 4),
            SubcategoryFocus("going_to", 4),
            SubcategoryFocus("present_future", 5),
            SubcategoryFocus("future_word_order", 5),
            SubcategoryFocus("future_time_signal", 4)
        )
    )

    val readingIds: Set<String> = linkedSetOf(
        "reading", "listening", "stories", "speaking", "writing", "dictation", "vocabulary", "vocabulary_en",
        "translation_nl_to_en", "translation_nl_to_fr", "translation_en_to_nl",
        "translation_en_to_fr", "translation_fr_to_nl", "translation_fr_to_en"
    )

    val writingBaseIds: Set<String> = linkedSetOf(
        "letter_tracing", "writing", "spelling", "sentence-structure", "sentence_order", "grammar",
        "question_words", *writingFocuses.keys.toTypedArray()
    )

    val mathIds: Set<String> = linkedSetOf(
        "addition", "subtraction", "multiplication", "division", "mixed_arithmetic",
        "number_sequences", "statistics", "fractions", "money"
    )

    val geometryIds: Set<String> = linkedSetOf(
        "time", "measurement", "compare_size", "compare_weight", "compare_height",
        "compare_length", "compare_distance", "geometry_shapes", "temperature"
    )

    val puzzleIds: Set<String> = linkedSetOf(
        "sudoku", "math-crosswords", "word-seeker", "mothership-dock", "galaxycon",
        "calculator-puzzle", "constellations", "orbit-builder"
    )

    fun baseId(id: String?): String = id.orEmpty().substringBefore(FOCUS_SEPARATOR)

    fun fullFocusId(baseId: String, focusId: String): String = "$baseId$FOCUS_SEPARATOR$focusId"

    fun focusFor(fullId: String?): SubcategoryFocus? {
        val value = fullId.orEmpty()
        val base = baseId(value)
        val focusId = value.substringAfter(FOCUS_SEPARATOR, missingDelimiterValue = "")
        if (focusId.isBlank()) return null
        return writingFocuses[base]?.firstOrNull { it.id == focusId }
    }

    fun isFocusAvailable(fullId: String, grade: Int): Boolean {
        val focus = focusFor(fullId) ?: return false
        return grade in focus.minGrade..focus.maxGrade
    }

    /**
     * Resolves the selected grades for a navigation target by combining the
     * catalogue's pedagogical grade range with the runtime content index.
     *
     * An empty runtime index means that content availability is still loading,
     * so catalogue constraints are applied on their own. Once the index is
     * populated, a missing target is known to have no content and is hidden.
     */
    fun applicableGrades(
        targetId: String,
        selectedGrades: List<Int>,
        gradeAvailability: Map<String, Set<Int>>
    ): List<Int> {
        val runtimeGrades = gradeAvailability[targetId]
        val runtimeAvailabilityKnown = gradeAvailability.isNotEmpty()

        return selectedGrades
            .asSequence()
            .filter { it in 1..6 }
            .filter { isTargetAvailable(targetId, it) }
            .filter { grade -> !runtimeAvailabilityKnown || runtimeGrades?.contains(grade) == true }
            .distinct()
            .sorted()
            .toList()
    }

    private fun isTargetAvailable(targetId: String, grade: Int): Boolean {
        if (targetId == "speaking") return grade in setOf(1, 2, 6)
        if (targetId == "statistics") return grade in setOf(2, 3, 4, 6)
        focusFor(targetId)?.let { focus ->
            return grade in focus.minGrade..focus.maxGrade
        }

        val baseFocuses = writingFocuses[baseId(targetId)] ?: return true
        return baseFocuses.any { focus -> grade in focus.minGrade..focus.maxGrade }
    }

    fun optionIdsFor(baseId: String): List<String> = writingFocuses[baseId].orEmpty().map {
        fullFocusId(baseId, it.id)
    }
}
