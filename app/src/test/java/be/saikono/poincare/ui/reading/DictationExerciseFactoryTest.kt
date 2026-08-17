package be.saikono.poincare.ui.reading

import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationExerciseFactoryTest {
    @Test
    fun `dictation reuses localized words from reading and listening`() {
        val sources = listOf(
            source("reading-one", "reading", 1, "kat", "cat", "chat"),
            source("listening-two", "listening", 2, "maan", "moon", "lune"),
            source("reading-three", "reading", 3, "zee-egel", "sea-star", "porte-clé")
        )

        val exercises = DictationExerciseFactory.build(SkillLevel.FIRST_GRADE, "fr-BE", null, sources)

        assertEquals(listOf("chat", "lune", "porte-clé"), exercises.map { it.answer })
        assertEquals(setOf("reading", "listening"), exercises.map { it.data["sourceSubcategoryId"] }.toSet())
        assertEquals(setOf("1", "2", "3"), exercises.map { it.data["difficultyStars"] }.toSet())
        assertTrue(exercises.all { it.inputMode == InputMode.CUSTOM_TEXT_KEYBOARD })
    }

    @Test
    fun `unsupported source words are excluded`() {
        val sources = listOf(
            source("supported", "reading", 1, "één", "one", "école"),
            source("unsupported", "listening", 1, "ijs", "ice", "garçon")
        )

        val exercises = DictationExerciseFactory.build(SkillLevel.FIRST_GRADE, "fr-BE", null, sources)

        assertEquals(listOf("école"), exercises.map { it.answer })
        assertTrue(exercises.all { answer -> answer.answer.all { it in DictationExerciseFactory.AVAILABLE_CHARACTERS } })
    }

    private fun source(
        id: String,
        subcategory: String,
        difficulty: Int,
        nl: String,
        en: String,
        fr: String
    ) = ExerciseContent(
        id = id,
        domain = LearningDomain.READING,
        skill = subcategory,
        section = ExerciseSection.EXERCISES,
        level = SkillLevel.FIRST_GRADE,
        inputMode = InputMode.TAP_CHOICE,
        prompt = LocalizedText("", "", ""),
        data = mapOf(
            "subcategoryId" to subcategory,
            "difficultyStars" to difficulty.toString(),
            "answer_nl-BE" to nl,
            "answer_en-US" to en,
            "answer_fr-BE" to fr
        ),
        answer = nl
    )
}
