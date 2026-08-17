package be.saikono.poincare.ui.reading

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.RewardMetadata
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.localizedAnswer
import be.saikono.poincare.model.normalizeAnswer
import kotlin.random.Random

object TranslationExerciseFactory {
    private val translationPattern = Regex("translation_(nl|en|fr)_to_(nl|en|fr)")

    fun supports(subcategoryId: String?): Boolean = subcategoryId?.matches(translationPattern) == true

    fun build(
        subcategoryId: String,
        level: SkillLevel,
        selectedDifficulty: Int?,
        sourceExercises: List<ExerciseContent>
    ): List<ExerciseContent> {
        val match = translationPattern.matchEntire(subcategoryId) ?: return emptyList()
        val sourceLocale = localeFor(match.groupValues[1])
        val targetLocale = localeFor(match.groupValues[2])
        val grade = level.toGrade() ?: 1

        val vocabulary = sourceExercises.asSequence()
            .filter { it.inputMode in setOf(InputMode.TAP_CHOICE, InputMode.LISTEN_AND_SELECT, InputMode.CUSTOM_TEXT_KEYBOARD) }
            .filter { exercise -> selectedDifficulty == null || exercise.data["difficultyStars"]?.toIntOrNull() == selectedDifficulty }
            .mapNotNull { exercise ->
                val source = exercise.localizedAnswer(sourceLocale).trim()
                val target = exercise.localizedAnswer(targetLocale).trim()
                val difficulty = exercise.data["difficultyStars"]?.toIntOrNull()?.coerceIn(1, 3) ?: 1
                if (!isVocabulary(source) || !isVocabulary(target)) return@mapNotNull null
                if (normalizeAnswer(source) == normalizeAnswer(target)) return@mapNotNull null
                VocabularyItem(exercise.id, source, target, difficulty)
            }
            .distinctBy { normalizeAnswer(it.source) to normalizeAnswer(it.target) }
            .toList()

        val targetPool = vocabulary.map { it.target }.distinctBy(::normalizeAnswer)
        return vocabulary.mapNotNull { item ->
            val distractors = targetPool
                .filter { normalizeAnswer(it) != normalizeAnswer(item.target) }
                .shuffled(Random((item.sourceId + subcategoryId).hashCode()))
                .take(3)
            if (distractors.size < 2) return@mapNotNull null
            val options = (distractors + item.target).shuffled(Random((subcategoryId + item.sourceId).hashCode()))
            val prompt = promptFor(item.source, sourceLocale, targetLocale)
            ExerciseContent(
                id = "translation-${subcategoryId}-${item.sourceId}",
                domain = LearningDomain.READING,
                skill = "translation",
                section = ExerciseSection.EXERCISES,
                level = level,
                inputMode = InputMode.TAP_CHOICE,
                prompt = prompt,
                data = buildMap {
                    put("mainCategory", "language")
                    put("subcategoryId", subcategoryId)
                    put("grade", grade.toString())
                    put("difficultyStars", item.difficulty.toString())
                    put("progressKey", "${subcategoryId}_g${grade}_${item.sourceId}")
                    put("audioTargetLocale", targetLocale)
                    put("topic", topicFor(subcategoryId, "nl-BE"))
                    put("topic_en-US", topicFor(subcategoryId, "en-US"))
                    put("topic_fr-BE", topicFor(subcategoryId, "fr-BE"))
                    listOf("nl-BE", "en-US", "fr-BE").forEach { locale ->
                        put("answer_$locale", item.target)
                        put("options_$locale", options.joinToString("\n"))
                        put("optionSpeech_$locale", options.joinToString("\n"))
                    }
                },
                answer = item.target,
                rewardMetadata = RewardMetadata(stamps = 1)
            )
        }
    }

    private fun promptFor(source: String, sourceLocale: String, targetLocale: String) = LocalizedText(
        nl = "Vertaal ‘$source’ van ${languageName(sourceLocale, "nl-BE")} naar ${languageName(targetLocale, "nl-BE")}.",
        en = "Translate ‘$source’ from ${languageName(sourceLocale, "en-US")} to ${languageName(targetLocale, "en-US")}.",
        fr = "Traduis ‘$source’ du ${languageName(sourceLocale, "fr-BE")} vers ${languageName(targetLocale, "fr-BE")}.")

    private fun topicFor(id: String, locale: String): String {
        val match = translationPattern.matchEntire(id) ?: return "Vertalen"
        return when {
            locale.startsWith("fr") -> "${languageName(localeFor(match.groupValues[1]), locale)} vers ${languageName(localeFor(match.groupValues[2]), locale)}"
            locale.startsWith("en") -> "${languageName(localeFor(match.groupValues[1]), locale)} to ${languageName(localeFor(match.groupValues[2]), locale)}"
            else -> "${languageName(localeFor(match.groupValues[1]), locale)} naar ${languageName(localeFor(match.groupValues[2]), locale)}"
        }
    }

    private fun languageName(language: String, uiLocale: String): String = when {
        uiLocale.startsWith("fr") -> when (language) { "nl-BE" -> "néerlandais"; "fr-BE" -> "français"; else -> "anglais" }
        uiLocale.startsWith("en") -> when (language) { "nl-BE" -> "Dutch"; "fr-BE" -> "French"; else -> "English" }
        else -> when (language) { "nl-BE" -> "Nederlands"; "fr-BE" -> "Frans"; else -> "Engels" }
    }

    private fun localeFor(value: String): String = when (value) { "fr" -> "fr-BE"; "en" -> "en-US"; else -> "nl-BE" }
    private fun SkillLevel.toGrade(): Int? = if (this == SkillLevel.NOT_APPLICABLE) null else ordinal + 1
    private fun isVocabulary(value: String): Boolean = value.isNotBlank() && value.length <= 32 && value.count { it == ' ' } <= 2 && value.none { it.isDigit() }
    private data class VocabularyItem(val sourceId: String, val source: String, val target: String, val difficulty: Int)
}
