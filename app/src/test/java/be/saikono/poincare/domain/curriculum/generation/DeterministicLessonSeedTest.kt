package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeterministicLessonSeedTest {
    @Test
    fun `same stable inputs reproduce the same seed`() {
        val source = source()
        val context = context(attemptId = "attempt-001")

        assertEquals(
            DeterministicLessonSeed.derive(source, context),
            DeterministicLessonSeed.derive(source, context)
        )
    }

    @Test
    fun `attempt seed changes when the stable attempt id changes`() {
        val source = source()

        assertNotEquals(
            DeterministicLessonSeed.derive(source, context("attempt-001")),
            DeterministicLessonSeed.derive(source, context("attempt-002"))
        )
    }

    @Test
    fun `parameter insertion order does not change the seed`() {
        val first = source(
            parameters = linkedMapOf(
                "grade" to "1",
                "operation" to "subtraction",
                "phase" to "guided",
                "editorial_status" to "approved"
            )
        )
        val second = source(
            parameters = linkedMapOf(
                "editorial_status" to "approved",
                "phase" to "guided",
                "operation" to "subtraction",
                "grade" to "1"
            )
        )

        assertEquals(
            DeterministicLessonSeed.derive(first, context("attempt-001")),
            DeterministicLessonSeed.derive(second, context("attempt-001"))
        )
    }

    @Test
    fun `lesson seed policy ignores attempt id while retaining lesson identity`() {
        val source = source(seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_LESSON)

        assertEquals(
            DeterministicLessonSeed.derive(source, context("attempt-001")),
            DeterministicLessonSeed.derive(source, context("attempt-999"))
        )
        assertNotEquals(
            DeterministicLessonSeed.derive(source, context("attempt-001")),
            DeterministicLessonSeed.derive(
                source,
                context("attempt-001").copy(lessonId = "lesson.other")
            )
        )
    }

    private fun source(
        seedPolicy: LessonSeedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
        parameters: Map<String, String> = mapOf(
            "grade" to "1",
            "operation" to "subtraction",
            "phase" to "guided",
            "editorial_status" to "approved"
        )
    ): LessonActivitySource.Generated = LessonActivitySource.Generated(
        generatorId = Grade1AdditionSubtractionGenerator.generatorId,
        generatorVersion = Grade1AdditionSubtractionGenerator.generatorVersion,
        exerciseCount = 8,
        seedPolicy = seedPolicy,
        parameters = parameters
    )

    private fun context(attemptId: String?): LessonGenerationContext = LessonGenerationContext(
        profileId = "profile-001",
        unitId = "math.addition.g1.foundations",
        lessonId = "lesson.math.subtraction.g1.within20",
        sectionId = "lesson.math.subtraction.g1.within20.guided",
        attemptId = attemptId
    )
}
