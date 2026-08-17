package be.saikono.poincare.data.story

import kotlinx.serialization.Serializable

@Serializable
data class StoryCatalogue(
    val schemaVersion: Int,
    val stories: List<Story>
)

@Serializable
data class StoryBookCatalogue(
    val schemaVersion: Int,
    val books: List<StoryBook>
)

@Serializable
data class StoryBook(
    val id: String,
    val grade: Int,
    val order: Int,
    val coverAsset: String,
    val translations: Map<String, StoryTranslation>,
    val storyIds: List<String>,
    val imageStyle: String? = null,
    val seriesId: String? = null,
    val seriesOrder: Int? = null,
    val seriesTitle: StorySeriesTitle? = null,
    val bookNumber: Int? = null,
    val aviLevel: String? = null,
    val publicationStatus: String = "ACTIVE"
) {
    fun translation(locale: String): StoryTranslation =
        translations[locale.normalizedStoryLocale()]
            ?: translations["nl-BE"]
            ?: error("Story book $id has no nl-BE translation")
}

@Serializable
data class StorySeriesTitle(
    val visible: Map<String, String> = emptyMap()
) {
    fun text(locale: String): String = localized(visible, locale)
}

@Serializable
data class Story(
    val id: String,
    val seriesId: String? = null,
    val sourceBookId: String? = null,
    val chapterNumber: Int? = null,
    val grade: Int,
    val difficulty: Int,
    val aviLevel: String,
    val estimatedMinutes: Int,
    val author: String,
    val illustrator: String,
    val license: String,
    val source: String,
    val hasVoiceOver: Boolean,
    val coverAsset: String,
    val imageStyle: String? = null,
    val aviStatus: String? = null,
    val translations: Map<String, StoryTranslation>,
    val pages: List<StoryPage>,
    val questions: List<StoryQuestion> = emptyList()
) {
    fun translation(locale: String): StoryTranslation =
        translations[locale.normalizedStoryLocale()]
            ?: translations["nl-BE"]
            ?: error("Story $id has no nl-BE translation")

    fun page(index: Int): StoryPage = pages[index.coerceIn(0, pages.lastIndex)]
}

@Serializable
data class StoryTranslation(
    val title: String,
    val description: String
)

@Serializable
data class StoryPage(
    val id: String,
    val illustrationAsset: String? = null,
    val chapter: Int? = null,
    val chapterTitles: Map<String, String> = emptyMap(),
    val visibleText: Map<String, String>,
    val speechText: Map<String, String> = emptyMap()
) {
    fun text(locale: String): String = localized(visibleText, locale)
    fun spokenText(locale: String): String = localized(speechText, locale).ifBlank { text(locale) }
    fun chapterTitle(locale: String): String = localized(chapterTitles, locale)
}

@Serializable
data class StoryQuestion(
    val id: String,
    val prompt: Map<String, String>,
    val options: Map<String, List<String>>,
    val correctIndex: Int,
    val explanation: Map<String, String>
) {
    fun prompt(locale: String): String = localized(prompt, locale)
    fun options(locale: String): List<String> =
        options[locale.normalizedStoryLocale()] ?: options["nl-BE"].orEmpty()
    fun explanation(locale: String): String = localized(explanation, locale)
}

@Serializable
data class StoryProgress(
    val profileId: String,
    val storyId: String,
    val locale: String,
    val pageIndex: Int = 0,
    val started: Boolean = false,
    val completed: Boolean = false,
    val lastActivityEpochMillis: Long = 0L,
    val readingMode: String = "silent"
) {
    val identity: String get() = storyProgressIdentity(profileId, storyId, locale)
}

fun storyProgressIdentity(profileId: String, storyId: String, locale: String): String =
    listOf(profileId.trim(), storyId.trim(), locale.normalizedStoryLocale()).joinToString("::")

fun String.normalizedStoryLocale(): String = when (trim().replace('_', '-').lowercase()) {
    "fr", "fr-be" -> "fr-BE"
    "en", "en-us" -> "en-US"
    else -> "nl-BE"
}

private fun localized(values: Map<String, String>, locale: String): String =
    values[locale.normalizedStoryLocale()] ?: values["nl-BE"].orEmpty()
