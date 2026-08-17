package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SkillLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade2StatisticsIntroductionGeneratorTest {
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
                "table_lookup",
                "pictogram_lookup",
                "tally_lookup",
                "bar_lookup",
                "total_two",
                "difference",
                "missing_from_total",
                "total_all"
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
            assertTrue(exercise.speechText?.nl?.contains('●') == false)
            assertTrue(exercise.speechText?.nl?.contains('■') == false)
            assertTrue(exercise.speechText?.nl?.contains('|') == false)
            assertEquals(Grade2StatisticsIntroductionGenerator.generatorId, exercise.data["generatorId"])
            assertEquals("1", exercise.data["generatorVersion"])
            assertEquals("1", exercise.data["symbolValue"])
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
            assertTrue(exercise.answer.toInt() in 0..36)
            assertAnswerMatchesMetadata(exercise)
        }
    }

    @Test
    fun `many stable attempts preserve graph bounds representations and exact answers`() {
        repeat(80) { attempt ->
            val phase = if (attempt % 2 == 0) "guided" else "checkpoint"
            val count = if (phase == "guided") 8 else 10
            registry.materialize(source(phase, count), context("bounds-$attempt"))
                .success().exercises.forEach { exercise ->
                    val values = exercise.data.getValue("values").split(',').map(String::toInt)
                    assertTrue(values.all { it in 1..12 })
                    assertAnswerMatchesMetadata(exercise)
                    when (exercise.data.getValue("promptKind")) {
                        "pictogram_lookup" -> assertTrue(exercise.prompt.nl.contains('●'))
                        "tally_lookup" -> assertTrue(exercise.prompt.nl.contains('|'))
                        "bar_lookup" -> assertTrue(exercise.prompt.nl.contains('■'))
                        "difference" -> assertTrue(values[0] > values[1])
                    }
                }
        }
    }

    @Test
    fun `invalid grade roadmap phase editorial status and extra parameters are rejected`() {
        val issues = Grade2StatisticsIntroductionGenerator.validate(
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

    private fun assertAnswerMatchesMetadata(exercise: ExerciseContent) {
        val values = exercise.data.getValue("values").split(',').map(String::toInt)
        val target = exercise.data.getValue("targetIndex").toInt()
        val expected = when (exercise.data.getValue("promptKind")) {
            "table_lookup", "pictogram_lookup", "tally_lookup", "bar_lookup" -> values[target]
            "total_two" -> values[0] + values[1]
            "difference" -> values[0] - values[1]
            "missing_from_total" -> values[2]
            "total_all" -> values.sum()
            else -> error("Unexpected prompt kind")
        }
        assertEquals(expected.toString(), exercise.answer)
    }

    private fun source(phase: String, count: Int): LessonActivitySource.Generated =
        LessonActivitySource.Generated(
            generatorId = Grade2StatisticsIntroductionGenerator.generatorId,
            generatorVersion = Grade2StatisticsIntroductionGenerator.generatorVersion,
            exerciseCount = count,
            seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
            parameters = mapOf(
                "grade" to "2",
                "roadmap_entry" to "roadmap.g2.math.statistics.introduction",
                "phase" to phase,
                "editorial_status" to "approved"
            )
        )

    private fun context(attemptId: String): LessonGenerationContext = LessonGenerationContext(
        profileId = "profile-002",
        unitId = "unit.g2.math.statistics.introduction",
        lessonId = "lesson.g2.math.statistics.introduction.practice",
        sectionId = "lesson.g2.math.statistics.introduction.practice.guided",
        attemptId = attemptId
    )

    private fun LessonGenerationResult.success(): LessonGenerationResult.Success {
        assertTrue("Expected generation success but received $this", this is LessonGenerationResult.Success)
        return this as LessonGenerationResult.Success
    }
}
