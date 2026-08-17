package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SkillLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade2OperationsExpansionGeneratorTest {
    private val registry = LessonGeneratorRegistry.default()

    @Test
    fun `guided source is deterministic localized unique and covers every reviewed kind`() {
        val source = source(phase = "guided", count = 8)
        val context = context("attempt-a")

        val first = registry.materialize(source, context).success()
        val replay = registry.materialize(source, context).success()

        assertEquals(first.seed, replay.seed)
        assertEquals(first.exercises, replay.exercises)
        assertEquals(8, first.exercises.size)
        assertEquals(8, first.exercises.map { it.id }.distinct().size)
        assertEquals(
            setOf(
                "tens_count",
                "compose_number",
                "add",
                "subtract",
                "missing_addend",
                "missing_subtrahend",
                "equal_groups",
                "equal_sharing"
            ),
            first.exercises.mapTo(linkedSetOf()) { it.data.getValue("promptKind") }
        )

        first.exercises.forEach { exercise ->
            assertEquals(LearningDomain.MATH, exercise.domain)
            assertEquals(SkillLevel.SECOND_GRADE, exercise.level)
            assertEquals(ExerciseSection.PRACTICE, exercise.section)
            assertEquals(InputMode.CUSTOM_NUMBER_PAD, exercise.inputMode)
            assertTrue(exercise.prompt.nl.isNotBlank())
            assertTrue(exercise.prompt.fr?.isNotBlank() == true)
            assertTrue(exercise.prompt.en?.isNotBlank() == true)
            assertTrue(exercise.speechText?.nl?.isNotBlank() == true)
            assertTrue(exercise.speechText?.fr?.isNotBlank() == true)
            assertTrue(exercise.speechText?.en?.isNotBlank() == true)
            assertTrue(exercise.speechText?.nl?.contains('?') == false)
            assertTrue(exercise.speechText?.fr?.contains('?') == false)
            assertTrue(exercise.speechText?.en?.contains('?') == false)
            assertEquals(Grade2OperationsExpansionGenerator.generatorId, exercise.data["generatorId"])
            assertEquals("1", exercise.data["generatorVersion"])
            assertEquals(first.seed.fingerprint, exercise.data["seedFingerprint"])
            assertAnswerMatchesMetadata(
                exercise.data.getValue("promptKind"),
                exercise.data.getValue("values"),
                exercise.answer
            )
        }
    }

    @Test
    fun `checkpoint stays within one hundred and different attempts remain reproducible`() {
        val source = source(phase = "checkpoint", count = 10)

        val first = registry.materialize(source, context("attempt-a")).success()
        val second = registry.materialize(source, context("attempt-b")).success()
        val replay = registry.materialize(source, context("attempt-a")).success()

        assertEquals(10, first.exercises.size)
        assertNotEquals(first.seed, second.seed)
        assertNotEquals(first.exercises, second.exercises)
        assertEquals(first.exercises, replay.exercises)
        first.exercises.forEach { exercise ->
            assertEquals(ExerciseSection.TESTS, exercise.section)
            assertTrue(exercise.answer.toInt() in 0..100)
            val values = exercise.data.getValue("values").split(',').map(String::toInt)
            assertTrue(values.all { it in 0..100 })
            assertAnswerMatchesMetadata(
                exercise.data.getValue("promptKind"),
                exercise.data.getValue("values"),
                exercise.answer
            )
        }
    }

    @Test
    fun `many stable attempts preserve arithmetic bounds crossings and exact sharing`() {
        repeat(80) { attempt ->
            val phase = if (attempt % 2 == 0) "guided" else "checkpoint"
            val count = if (phase == "guided") 8 else 10
            val generated = registry.materialize(
                source(phase, count),
                context("bounds-$attempt")
            ).success().exercises

            generated.forEach { exercise ->
                val kind = exercise.data.getValue("promptKind")
                val values = exercise.data.getValue("values").split(',').map(String::toInt)
                val answer = exercise.answer.toInt()
                assertTrue(answer in 0..100)
                assertTrue(values.all { it in 0..100 })
                when (kind) {
                    "add" -> assertTrue(values[0] % 10 + values[1] % 10 >= 10)
                    "subtract" -> assertTrue(values[1] % 10 > values[0] % 10)
                    "missing_addend" -> assertTrue(values[0] % 10 + answer % 10 >= 10)
                    "missing_subtrahend" -> assertTrue(answer % 10 > values[0] % 10)
                    "equal_groups" -> assertTrue(values[1] in setOf(2, 5, 10))
                    "equal_sharing" -> {
                        assertTrue(values[1] in setOf(2, 5, 10))
                        assertEquals(0, values[0] % values[1])
                    }
                }
                assertAnswerMatchesMetadata(kind, values.joinToString(","), answer.toString())
            }
        }
    }

    @Test
    fun `invalid grade roadmap phase editorial status and extra parameters are rejected`() {
        val invalid = mapOf(
            "grade" to "1",
            "roadmap_entry" to "roadmap.other",
            "phase" to "exam",
            "editorial_status" to "planned",
            "unexpected" to "value"
        )

        val issues = Grade2OperationsExpansionGenerator.validate(invalid)

        assertTrue(issues.any { it.contains("unsupported parameters") })
        assertTrue(issues.any { it.contains("grade must be 2") })
        assertTrue(issues.any { it.contains("roadmap_entry") })
        assertTrue(issues.any { it.contains("phase") })
        assertTrue(issues.any { it.contains("editorial_status") })
    }

    private fun assertAnswerMatchesMetadata(kind: String, rawValues: String, rawAnswer: String) {
        val values = rawValues.split(',').map(String::toInt)
        val expected = when (kind) {
            "tens_count" -> values[0] / 10
            "compose_number" -> values[0] * 10 + values[1]
            "add" -> values[0] + values[1]
            "subtract" -> values[0] - values[1]
            "missing_addend" -> values[1] - values[0]
            "missing_subtrahend" -> values[0] - values[1]
            "equal_groups" -> values[0] * values[1]
            "equal_sharing" -> values[0] / values[1]
            else -> error("Unexpected prompt kind: $kind")
        }
        assertEquals(expected.toString(), rawAnswer)
    }

    private fun source(phase: String, count: Int): LessonActivitySource.Generated =
        LessonActivitySource.Generated(
            generatorId = Grade2OperationsExpansionGenerator.generatorId,
            generatorVersion = Grade2OperationsExpansionGenerator.generatorVersion,
            exerciseCount = count,
            seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
            parameters = mapOf(
                "grade" to "2",
                "roadmap_entry" to "roadmap.g2.math.operations.expansion",
                "phase" to phase,
                "editorial_status" to "approved"
            )
        )

    private fun context(attemptId: String): LessonGenerationContext = LessonGenerationContext(
        profileId = "profile-002",
        unitId = "unit.g2.math.operations.expansion",
        lessonId = "lesson.g2.math.operations.expansion.practice",
        sectionId = "lesson.g2.math.operations.expansion.practice.guided",
        attemptId = attemptId
    )

    private fun LessonGenerationResult.success(): LessonGenerationResult.Success {
        assertTrue("Expected generation success but received $this", this is LessonGenerationResult.Success)
        return this as LessonGenerationResult.Success
    }
}
