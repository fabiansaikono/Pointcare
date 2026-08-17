package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade1NumberSenseGeneratorTest {
    private val registry = LessonGeneratorRegistry.default()

    @Test
    fun `guided source is deterministic localized unique and covers every reviewed prompt kind`() {
        val source = source(phase = "guided", count = 8)
        val context = context(attemptId = "attempt-a")

        val first = registry.materialize(source, context).success()
        val replay = registry.materialize(source, context).success()

        assertEquals(first.seed, replay.seed)
        assertEquals(first.exercises, replay.exercises)
        assertEquals(8, first.exercises.size)
        assertEquals(8, first.exercises.map { it.id }.distinct().size)
        assertEquals(
            setOf(
                "predecessor",
                "successor",
                "missing_forward",
                "missing_backward",
                "largest",
                "smallest",
                "tens_and_ones",
                "ten_plus_ones"
            ),
            first.exercises.mapTo(linkedSetOf()) { it.data.getValue("promptKind") }
        )

        first.exercises.forEach { exercise ->
            assertTrue(exercise.answer.toInt() in 0..20)
            assertTrue(exercise.prompt.nl.isNotBlank())
            assertTrue(exercise.prompt.fr?.isNotBlank() == true)
            assertTrue(exercise.prompt.en?.isNotBlank() == true)
            assertTrue(exercise.speechText?.nl?.isNotBlank() == true)
            assertTrue(exercise.speechText?.nl?.contains("?") == false)
            assertTrue(exercise.speechText?.fr?.contains("?") == false)
            assertTrue(exercise.speechText?.en?.contains("?") == false)
            assertEquals(Grade1NumberSenseGenerator.generatorId, exercise.data["generatorId"])
            assertEquals("1", exercise.data["generatorVersion"])
            assertEquals(first.seed.fingerprint, exercise.data["seedFingerprint"])
            assertAnswerMatchesMetadata(exercise.data.getValue("promptKind"), exercise.data.getValue("values"), exercise.answer)
        }
    }

    @Test
    fun `checkpoint remains within zero to twenty and different attempts remain reproducible`() {
        val source = source(phase = "checkpoint", count = 10)

        val first = registry.materialize(source, context("attempt-a")).success()
        val second = registry.materialize(source, context("attempt-b")).success()
        val firstReplay = registry.materialize(source, context("attempt-a")).success()

        assertEquals(10, first.exercises.size)
        assertNotEquals(first.seed, second.seed)
        assertNotEquals(first.exercises, second.exercises)
        assertEquals(first.exercises, firstReplay.exercises)
        first.exercises.forEach { exercise ->
            assertTrue(exercise.answer.toInt() in 0..20)
            val values = exercise.data.getValue("values").split(',').map(String::toInt)
            assertTrue(values.all { it in 0..20 })
        }
    }

    @Test
    fun `count objects focus renders one chosen object group and respects the requested range`() {
        val source = source(phase = "guided", count = 10, focus = "count_objects", maximum = 10)

        val result = registry.materialize(source, context("counting-attempt")).success()

        assertEquals(10, result.exercises.size)
        result.exercises.forEach { exercise ->
            assertEquals("count_objects", exercise.data["promptKind"])
            assertEquals("true", exercise.data["objectChoiceEnabled"])
            assertEquals("counting", exercise.data["operation"])
            assertEquals("0", exercise.data["right"])
            assertEquals(exercise.answer, exercise.data["left"])
            assertTrue(exercise.answer.toInt() in 1..10)
        }
    }

    @Test
    fun `invalid grade roadmap phase and extra parameters are rejected`() {
        val invalid = mapOf(
            "grade" to "2",
            "roadmap_entry" to "roadmap.other",
            "phase" to "exam",
            "editorial_status" to "approved",
            "unexpected" to "value"
        )

        val issues = Grade1NumberSenseGenerator.validate(invalid)

        assertTrue(issues.any { it.contains("unsupported parameters") })
        assertTrue(issues.any { it.contains("grade must be 1") })
        assertTrue(issues.any { it.contains("roadmap_entry") })
        assertTrue(issues.any { it.contains("phase") })
    }

    private fun assertAnswerMatchesMetadata(kind: String, rawValues: String, rawAnswer: String) {
        val values = rawValues.split(',').map(String::toInt)
        val expected = when (kind) {
            "predecessor" -> values[0] - 1
            "successor" -> values[0] + 1
            "missing_forward" -> values[0] + 2
            "missing_backward" -> values[0] - 2
            "largest" -> values.max()
            "smallest" -> values.min()
            "tens_and_ones" -> values[0] * 10 + values[1]
            "ten_plus_ones" -> values[0] + values[1]
            else -> error("Unexpected prompt kind: $kind")
        }
        assertEquals(expected.toString(), rawAnswer)
    }

    private fun source(
        phase: String,
        count: Int,
        focus: String? = null,
        maximum: Int? = null
    ): LessonActivitySource.Generated = LessonActivitySource.Generated(
        generatorId = Grade1NumberSenseGenerator.generatorId,
        generatorVersion = Grade1NumberSenseGenerator.generatorVersion,
        exerciseCount = count,
        seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
        parameters = buildMap {
            putAll(mapOf(
            "grade" to "1",
            "roadmap_entry" to "roadmap.g1.math.number_sense.foundations",
            "phase" to phase,
            "editorial_status" to "approved"
            ))
            focus?.let { put("focus", it) }
            maximum?.let { put("max_count", it.toString()) }
        }
    )

    private fun context(attemptId: String): LessonGenerationContext = LessonGenerationContext(
        profileId = "profile-001",
        unitId = "unit.g1.math.number_sense.foundations",
        lessonId = "lesson.g1.math.number_sense.foundations.practice",
        sectionId = "lesson.g1.math.number_sense.foundations.practice.guided",
        attemptId = attemptId
    )

    private fun LessonGenerationResult.success(): LessonGenerationResult.Success {
        assertTrue("Expected generation success but received $this", this is LessonGenerationResult.Success)
        return this as LessonGenerationResult.Success
    }
}
