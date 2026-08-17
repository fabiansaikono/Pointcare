package be.saikono.poincare.ui.lesson

import be.saikono.poincare.domain.curriculum.generation.DictationLessonGenerator
import be.saikono.poincare.domain.curriculum.generation.LessonGenerationSeed
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.localizedData
import be.saikono.poincare.model.localizedPromptTts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonDictationInputTest {
    @Test
    fun `missing-letter dictation provides spoken target mask and custom keyboard input`() {
        val exercise = DictationLessonGenerator.generate(
            seed = LessonGenerationSeed(42L, "dictation-test"),
            exerciseCount = 1,
            parameters = mapOf(
                "editorial_status" to "approved",
                "grade" to "1",
                "phase" to "guided",
                "exercise_type" to "dictation",
                "dictation_mode" to "MISSING_LETTER_DRAG_DROP",
                "keyboard_layout" to "AZERTY_BE_GENERATED",
                "instruction_nl" to "Schrijf wat je hoort",
                "spoken_content_policy" to "QUESTION_ONLY",
                "wrong answers allowed" to "2"
            )
        ).single()

        val masked = exercise.localizedData("maskedText", "nl-BE").orEmpty()
        assertEquals(InputMode.CUSTOM_TEXT_KEYBOARD, exercise.inputMode)
        assertTrue(masked.contains('_'))
        assertTrue(exercise.localizedData("dictationText", "nl-BE").orEmpty().isNotBlank())
        assertTrue(lessonExerciseRequiresAudio(exercise))
        assertEquals(exercise.answer, exercise.localizedPromptTts("nl-BE"))
        assertFalse(exercise.localizedPromptTts("nl-BE").contains("Schrijf wat je hoort", ignoreCase = true))

        val missingLetters = exercise.answer.zip(masked).filter { (_, visible) -> visible == '_' }.map { it.first }.joinToString("")
        assertEquals(exercise.answer, fillDictationMask(masked, missingLetters))
    }
}
