package be.saikono.poincare.ui.reading

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.RewardMetadata
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.localizedAnswer
import be.saikono.poincare.model.normalizeContentLocale
import java.util.Locale

internal object DictationExerciseFactory {
    fun build(
        level: SkillLevel,
        exerciseLocale: String,
        selectedDifficulty: Int?,
        sourceExercises: List<ExerciseContent>
    ): List<ExerciseContent> {
        val locale = normalizeContentLocale(exerciseLocale)
        val grade = if (level == SkillLevel.NOT_APPLICABLE) 1 else level.ordinal + 1
        val language = Locale.forLanguageTag(locale)
        return sourceExercises
            .asSequence()
            .mapNotNull { source ->
                val sourceGrade = source.data["grade"]?.toIntOrNull()
                    ?: if (source.level == SkillLevel.NOT_APPLICABLE) null else source.level.ordinal + 1
                if (sourceGrade != grade) return@mapNotNull null
                val difficulty = source.data["difficultyStars"]?.toIntOrNull() ?: return@mapNotNull null
                if (selectedDifficulty != null && difficulty != selectedDifficulty) return@mapNotNull null
                val word = source.localizedAnswer(locale).trim().lowercase(language)
                if (!word.isKeyboardCompatible()) return@mapNotNull null
                DictationSource(source, difficulty, word)
            }
            .distinctBy { it.difficulty to it.word }
            .mapIndexed { index, (source, difficulty, sourceWord) ->
                val mode = DictationMode.entries[index % DictationMode.entries.size]
                val answer = if (mode == DictationMode.WRITE_SENTENCE) sourceWord.inSentence(locale) else sourceWord
                val id = "dictation-${mode.id}-${locale.lowercase(Locale.ROOT)}-${source.id}"
                ExerciseContent(
                    id = id,
                    domain = LearningDomain.READING,
                    skill = "dictation",
                    section = ExerciseSection.EXERCISES,
                    level = level,
                    inputMode = InputMode.CUSTOM_TEXT_KEYBOARD,
                    prompt = LocalizedText(
                        nl = "Luister naar het gesproken woord/de gesproken zin en schrijf wat je hoort.",
                        fr = "Écoute le mot ou la phrase prononcée et écris ce que tu entends.",
                        en = "Listen to the spoken word or sentence and write what you hear."
                    ),
                    speechText = LocalizedText(answer, answer, answer),
                    data = buildMap {
                        put("mainCategory", "language")
                        put("subcategoryId", "dictation")
                        put("grade", grade.toString())
                        put("readingGrade", grade.toString())
                        put("difficultyStars", difficulty.toString())
                        put("progressKey", id)
                        put("sourceExerciseId", source.id)
                        put("sourceSubcategoryId", source.data["subcategoryId"].orEmpty())
                        put("dictationMode", mode.id)
                        put("keyboardLayout", "AZERTY_BE_GENERATED")
                        put("spokenContentPolicy", "QUESTION_ONLY")
                        if (mode.missingLetterCount > 0) {
                            put("missingLetterCount", mode.missingLetterCount.toString())
                            put("maskedText", sourceWord.maskLetters(mode.missingLetterCount))
                        }
                        put("topic", "Dictee")
                        put("topic_en-US", "Dictation")
                        put("topic_fr-BE", "Dictée")
                        put("answer_$locale", answer)
                        put("audioText_$locale", answer)
                        put("audioTtsLanguage_$locale", locale)
                        put("audioId_$locale", id)
                    },
                    answer = answer,
                    rewardMetadata = RewardMetadata(stamps = 1)
                )
            }
            .toList()
    }

    private fun String.isKeyboardCompatible(): Boolean =
        isNotBlank() && all { it in AVAILABLE_CHARACTERS }

    private fun String.maskLetters(count: Int): String {
        val letterIndices = indices.filter { this[it].isLetter() }
        if (letterIndices.isEmpty()) return this
        val selected = letterIndices.takeLast(count.coerceAtMost(letterIndices.size)).toSet()
        return mapIndexed { index, character -> if (index in selected) '_' else character }.joinToString("")
    }

    private fun String.inSentence(locale: String): String = when {
        locale.startsWith("fr") -> "J'écris $this."
        locale.startsWith("en") -> "I write $this."
        else -> "Ik schrijf $this."
    }

    private enum class DictationMode(val id: String, val missingLetterCount: Int = 0) {
        MISSING_ONE("MISSING_LETTER_DRAG_DROP", 1),
        MISSING_MANY("MISSING_LETTERS_DRAG_DROP", 2),
        WRITE_WORD("WRITE_WORD"),
        WRITE_SENTENCE("WRITE_SENTENCE")
    }

    private data class DictationSource(
        val exercise: ExerciseContent,
        val difficulty: Int,
        val word: String
    )

    internal val AVAILABLE_CHARACTERS = ('a'..'z').toSet() + setOf(' ', '-', 'é', 'è', 'à', 'ô')
}
