package be.saikono.poincare.ui.lesson

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonResultAndClockTest {
    @Test
    fun `answer review survives metadata serialization`() {
        val answers = listOf(
            LessonAnswerReview(
                questionNumber = 1,
                exerciseId = "exercise.clock.1",
                question = "Hoe laat is het?",
                givenAnswer = "9",
                correctAnswer = "10",
                correct = false
            ),
            LessonAnswerReview(
                questionNumber = 2,
                exerciseId = "exercise.clock.2",
                question = "Hoeveel minuten?",
                givenAnswer = "30",
                correctAnswer = "30",
                correct = true
            )
        )

        val restored = LessonAnswerReviewCodec.decode(LessonAnswerReviewCodec.encode(answers))

        assertEquals(answers, restored)
        assertTrue(restored.first().correct.not())
        assertTrue(restored.last().correct)
    }

    @Test
    fun `clock metadata creates matching analogue and digital moments`() {
        val exercise = exercise(
            promptKind = "half_hour_duration",
            values = "9,0,9,30"
        )

        assertEquals(
            listOf(ClockMoment(9, 0), ClockMoment(9, 30)),
            clockMomentsFor(exercise)
        )
    }

    @Test
    fun `non clock exercise does not create a clock visual`() {
        assertTrue(clockMomentsFor(exercise("money_total", "2,5")).isEmpty())
    }

    private fun exercise(promptKind: String, values: String): ExerciseContent = ExerciseContent(
        id = "generated.test.$promptKind",
        domain = LearningDomain.GEOMETRY,
        skill = "test",
        section = ExerciseSection.PRACTICE,
        level = SkillLevel.FIRST_GRADE,
        inputMode = InputMode.CUSTOM_NUMBER_PAD,
        prompt = LocalizedText("Vraag"),
        data = mapOf("promptKind" to promptKind, "values" to values),
        answer = "0"
    )
}
