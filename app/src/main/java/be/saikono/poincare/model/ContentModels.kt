package be.saikono.poincare.model

import kotlinx.serialization.Serializable

@Serializable
enum class InputMode {
    CUSTOM_NUMBER_PAD,
    DRAG_DROP_DIGITS,
    DRAG_DROP_LETTERS,
    TAP_CHOICE,
    LISTEN_AND_SELECT,
    BUILD_WORD,
    CUSTOM_TEXT_KEYBOARD,
    TRACE_LETTER,
    MATH_CROSSWORD,
    SUDOKU_GRID,
    WORD_SEEKER,
    CALCULATOR_PUZZLE,
    NUMBER_WALL
}

@Serializable
enum class ExerciseSection {
    PRACTICE,
    EXERCISES,
    PUZZLES,
    TESTS
}

@Serializable
data class LocalizedText(
    val nl: String,
    val fr: String? = null,
    val en: String? = null
) {
    fun getRaw(locale: String): String = when (locale) {
        "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> fr ?: nl
        "en", "en-US", "en_US", "en-GB", "en_GB" -> en ?: nl
        else -> nl
    }

    fun get(locale: String): String = formatVisibleNumbers(getRaw(locale))
}

@Serializable
data class ExerciseContent(
    val id: String,
    val domain: LearningDomain,
    val skill: String,
    val section: ExerciseSection,
    val level: SkillLevel,
    val inputMode: InputMode,
    val prompt: LocalizedText,
    val speechText: LocalizedText? = null,
    val translation: LocalizedText? = null,
    val data: Map<String, String>,
    val answer: String,
    val hints: List<String> = emptyList(),
    val rewardMetadata: RewardMetadata = RewardMetadata()
)

fun normalizeContentLocale(locale: String): String = when (locale) {
    "nl", "nl_BE" -> "nl-BE"
    "en", "en_US" -> "en-US"
    "fr", "fr_BE", "fr-FR", "fr_FR" -> "fr-BE"
    else -> locale
}

fun ExerciseContent.localizedData(key: String, locale: String): String? {
    val normalized = normalizeContentLocale(locale)
    return data["${key}_$normalized"] ?: data[key]
}

fun ExerciseContent.localizedOptions(locale: String): List<String> =
    localizedData("options", locale)
        ?.let { rawOptions ->
            val separator = if (rawOptions.contains('\n')) "\n" else ","
            rawOptions.split(separator)
                .map { formatVisibleNumbers(it.trim()) }
                .filter { it.isNotBlank() }
        }
        .orEmpty()

fun ExerciseContent.localizedAnswer(locale: String): String =
    formatVisibleNumbers(localizedData("answer", locale) ?: answer)
fun ExerciseContent.localizedPromptTts(locale: String): String =
    localizedData("promptTts", locale) ?: prompt.getRaw(locale)

fun ExerciseContent.localizedOptionSpeech(option: String, locale: String): String {
    val options = localizedOptions(locale)
    val speeches = localizedData("optionSpeech", locale)
        ?.split('\n')
        ?.map { it.trim() }
        .orEmpty()
    val index = options.indexOf(option)
    return speeches.getOrNull(index)?.takeIf { it.isNotBlank() } ?: option
}

fun ExerciseContent.localizedTopic(locale: String): String =
    formatVisibleNumbers(
        localizedData("topic", locale)?.replaceFirstChar { it.uppercase() }
            ?: skill.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
    )

@Serializable
data class RewardMetadata(
    val stamps: Int = 1,
    val unlockId: String? = null
)
