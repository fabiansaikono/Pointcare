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

class Grade2MeasurementContextGeneratorTest {
    private val registry = LessonGeneratorRegistry.default()

    @Test
    fun `guided source is deterministic localized unique and covers every reviewed kind`() {
        val source = source("guided", 8)
        val context = context("attempt-a")

        val first = registry.materialize(source, context).success()
        val replay = registry.materialize(source, context).success()

        assertEquals(first.seed, replay.seed)
        assertEquals(first.exercises, replay.exercises)
        assertEquals(8, first.exercises.size)
        assertEquals(8, first.exercises.map { it.id }.distinct().size)
        assertEquals(
            setOf(
                "half_hour_duration",
                "hour_duration",
                "money_total",
                "money_change",
                "metres_to_centimetres",
                "length_difference",
                "mass_total",
                "mass_difference"
            ),
            first.exercises.mapTo(linkedSetOf()) { it.data.getValue("promptKind") }
        )

        first.exercises.forEach { exercise ->
            assertEquals(LearningDomain.GEOMETRY, exercise.domain)
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
            assertTrue(exercise.speechText?.nl?.contains("€") == false)
            assertTrue(Regex("\\b(?:cm|kg|m)\\b").containsMatchIn(exercise.speechText!!.nl).not())
            assertEquals(Grade2MeasurementContextGenerator.generatorId, exercise.data["generatorId"])
            assertEquals("1", exercise.data["generatorVersion"])
            assertEquals(first.seed.fingerprint, exercise.data["seedFingerprint"])
            assertAnswerMatchesMetadata(exercise)
        }
    }

    @Test
    fun `checkpoint stays within reviewed bounds and different attempts remain reproducible`() {
        val source = source("checkpoint", 10)

        val first = registry.materialize(source, context("attempt-a")).success()
        val second = registry.materialize(source, context("attempt-b")).success()
        val replay = registry.materialize(source, context("attempt-a")).success()

        assertEquals(10, first.exercises.size)
        assertNotEquals(first.seed, second.seed)
        assertNotEquals(first.exercises, second.exercises)
        assertEquals(first.exercises, replay.exercises)
        first.exercises.forEach { exercise ->
            assertEquals(ExerciseSection.TESTS, exercise.section)
            assertTrue(exercise.answer.toInt() in 0..300)
            assertAnswerMatchesMetadata(exercise)
        }
    }

    @Test
    fun `many stable attempts preserve exact units relations and whole number answers`() {
        repeat(80) { attempt ->
            val phase = if (attempt % 2 == 0) "guided" else "checkpoint"
            val count = if (phase == "guided") 8 else 10
            val exercises = registry.materialize(
                source(phase, count),
                context("bounds-$attempt")
            ).success().exercises

            exercises.forEach { exercise ->
                assertTrue(exercise.answer.toInt() >= 0)
                assertAnswerMatchesMetadata(exercise)
                when (exercise.data.getValue("promptKind")) {
                    "half_hour_duration", "hour_duration" ->
                        assertEquals("min", exercise.data["unit"])
                    "money_total", "money_change" ->
                        assertEquals("euro", exercise.data["unit"])
                    "metres_to_centimetres", "length_difference" ->
                        assertEquals("cm", exercise.data["unit"])
                    "mass_total", "mass_difference" ->
                        assertEquals("kg", exercise.data["unit"])
                }
            }
        }
    }

    @Test
    fun `invalid grade roadmap phase editorial status and extra parameters are rejected`() {
        val issues = Grade2MeasurementContextGenerator.validate(
            mapOf(
                "grade" to "1",
                "roadmap_entry" to "roadmap.other",
                "phase" to "exam",
                "editorial_status" to "planned",
                "unexpected" to "value"
            )
        )

        assertTrue(issues.any { it.contains("unsupported parameters") })
        assertTrue(issues.any { it.contains("grade must be 2") })
        assertTrue(issues.any { it.contains("roadmap_entry") })
        assertTrue(issues.any { it.contains("phase") })
        assertTrue(issues.any { it.contains("editorial_status") })
    }

    private fun assertAnswerMatchesMetadata(exercise: be.saikono.poincare.model.ExerciseContent) {
        val kind = exercise.data.getValue("promptKind")
        val values = exercise.data.getValue("values").split(',').map(String::toInt)
        val expected = when (kind) {
            "half_hour_duration" -> 30
            "hour_duration" -> (values[1] - values[0]) * 60
            "money_total" -> values[0] + values[1]
            "money_change" -> values[1] - values[0]
            "metres_to_centimetres" -> values[0] * 100
            "length_difference", "mass_difference" -> values[0] - values[1]
            "mass_total" -> values[0] + values[1]
            else -> error("Unexpected prompt kind: $kind")
        }
        assertEquals(expected.toString(), exercise.answer)
    }

    private fun source(phase: String, count: Int): LessonActivitySource.Generated =
        LessonActivitySource.Generated(
            generatorId = Grade2MeasurementContextGenerator.generatorId,
            generatorVersion = Grade2MeasurementContextGenerator.generatorVersion,
            exerciseCount = count,
            seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
            parameters = mapOf(
                "grade" to "2",
                "roadmap_entry" to "roadmap.g2.geometry.measurement_context",
                "phase" to phase,
                "editorial_status" to "approved"
            )
        )

    private fun context(attemptId: String): LessonGenerationContext = LessonGenerationContext(
        profileId = "profile-002",
        unitId = "unit.g2.geometry.measurement_context",
        lessonId = "lesson.g2.geometry.measurement_context.practice",
        sectionId = "lesson.g2.geometry.measurement_context.practice.guided",
        attemptId = attemptId
    )

    private fun LessonGenerationResult.success(): LessonGenerationResult.Success {
        assertTrue("Expected generation success but received $this", this is LessonGenerationResult.Success)
        return this as LessonGenerationResult.Success
    }
}
