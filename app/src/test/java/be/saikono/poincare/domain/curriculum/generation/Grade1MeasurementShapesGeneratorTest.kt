package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import be.saikono.poincare.model.LearningDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade1MeasurementShapesGeneratorTest {
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
                "longer_length",
                "shorter_length",
                "reasonable_length",
                "hour_after",
                "hour_before",
                "money_total",
                "shape_sides",
                "shape_corners"
            ),
            first.exercises.mapTo(linkedSetOf()) { it.data.getValue("promptKind") }
        )

        first.exercises.forEach { exercise ->
            assertEquals(LearningDomain.GEOMETRY, exercise.domain)
            assertTrue(exercise.answer.toInt() in 0..25)
            assertTrue(exercise.prompt.nl.isNotBlank())
            assertTrue(exercise.prompt.fr?.isNotBlank() == true)
            assertTrue(exercise.prompt.en?.isNotBlank() == true)
            assertTrue(exercise.speechText?.nl?.isNotBlank() == true)
            assertTrue(exercise.speechText?.nl?.contains("?") == false)
            assertTrue(exercise.speechText?.fr?.contains("?") == false)
            assertTrue(exercise.speechText?.en?.contains("?") == false)
            assertTrue(Regex("\\bcm\\b").containsMatchIn(exercise.speechText!!.nl).not())
            assertTrue(Regex("\\bcm\\b").containsMatchIn(exercise.speechText!!.fr!!).not())
            assertTrue(Regex("\\bcm\\b").containsMatchIn(exercise.speechText!!.en!!).not())
            assertEquals(Grade1MeasurementShapesGenerator.generatorId, exercise.data["generatorId"])
            assertEquals("1", exercise.data["generatorVersion"])
            assertEquals(first.seed.fingerprint, exercise.data["seedFingerprint"])
            assertEquals(
                "roadmap.g1.geometry.measurement_shapes.foundations",
                exercise.data["roadmapEntry"]
            )
            assertAnswerMatchesMetadata(
                kind = exercise.data.getValue("promptKind"),
                rawValues = exercise.data.getValue("values"),
                label = exercise.data.getValue("label"),
                rawAnswer = exercise.answer
            )
        }
    }

    @Test
    fun `checkpoint stays within reviewed Grade 1 limits and different attempts remain reproducible`() {
        val source = source(phase = "checkpoint", count = 10)

        val first = registry.materialize(source, context("attempt-a")).success()
        val second = registry.materialize(source, context("attempt-b")).success()
        val replay = registry.materialize(source, context("attempt-a")).success()

        assertEquals(10, first.exercises.size)
        assertNotEquals(first.seed, second.seed)
        assertNotEquals(first.exercises, second.exercises)
        assertEquals(first.exercises, replay.exercises)
        assertTrue(first.exercises.all { it.answer.toInt() in 0..25 })
        assertEquals(10, first.exercises.map { it.id }.distinct().size)
    }

    @Test
    fun `semantic focus limits generation to one curriculum strand`() {
        val expectedKinds = mapOf(
            "comparison" to (8 to setOf("longer_length", "shorter_length")),
            "measurement" to (6 to setOf("reasonable_length")),
            "time" to (8 to setOf("hour_after", "hour_before")),
            "money" to (6 to setOf("money_total")),
            "shapes" to (8 to setOf("shape_sides", "shape_corners"))
        )

        expectedKinds.forEach { (focus, countAndExpected) ->
            val (count, expected) = countAndExpected
            val result = registry.materialize(
                source(phase = "guided", count = count, focus = focus),
                context(attemptId = "attempt-$focus")
            ).success()

            assertEquals(expected, result.exercises.mapTo(linkedSetOf()) {
                it.data.getValue("promptKind")
            })
            assertTrue(result.exercises.all { it.data["focus"] == focus })
        }
    }

    @Test
    fun `invalid grade roadmap phase and extra parameters are rejected`() {
        val invalid = mapOf(
            "grade" to "2",
            "roadmap_entry" to "roadmap.other",
            "phase" to "exam",
            "focus" to "everything",
            "editorial_status" to "approved",
            "unexpected" to "value"
        )

        val issues = Grade1MeasurementShapesGenerator.validate(invalid)

        assertTrue(issues.any { it.contains("unsupported parameters") })
        assertTrue(issues.any { it.contains("grade must be 1") })
        assertTrue(issues.any { it.contains("roadmap_entry") })
        assertTrue(issues.any { it.contains("phase") })
        assertTrue(issues.any { it.contains("focus") })
    }

    private fun assertAnswerMatchesMetadata(
        kind: String,
        rawValues: String,
        label: String,
        rawAnswer: String
    ) {
        val values = rawValues.split(',').filter(String::isNotBlank).map(String::toInt)
        val expected = when (kind) {
            "longer_length" -> values.max()
            "shorter_length" -> values.min()
            "reasonable_length" -> when (label) {
                "pencil" -> 15
                "eraser" -> 5
                "schoolbook" -> 25
                "spoon" -> 12
                "shoe" -> 20
                "toothbrush" -> 18
                else -> error("Unexpected everyday object: $label")
            }
            "hour_after" -> values[0] + 1
            "hour_before" -> values[0] - 1
            "money_total" -> values.sum()
            "shape_sides" -> values[0]
            "shape_corners" -> values[1]
            else -> error("Unexpected prompt kind: $kind")
        }
        assertEquals(expected.toString(), rawAnswer)
    }

    private fun source(
        phase: String,
        count: Int,
        focus: String? = null
    ): LessonActivitySource.Generated = LessonActivitySource.Generated(
        generatorId = Grade1MeasurementShapesGenerator.generatorId,
        generatorVersion = Grade1MeasurementShapesGenerator.generatorVersion,
        exerciseCount = count,
        seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
        parameters = buildMap {
            focus?.let { put("focus", it) }
            putAll(mapOf(
            "grade" to "1",
            "roadmap_entry" to "roadmap.g1.geometry.measurement_shapes.foundations",
            "phase" to phase,
            "editorial_status" to "approved"
            ))
        }
    )

    private fun context(attemptId: String): LessonGenerationContext = LessonGenerationContext(
        profileId = "profile-001",
        unitId = "unit.g1.geometry.measurement_shapes.foundations",
        lessonId = "lesson.g1.geometry.measurement_shapes.foundations.practice",
        sectionId = "lesson.g1.geometry.measurement_shapes.foundations.practice.guided",
        attemptId = attemptId
    )

    private fun LessonGenerationResult.success(): LessonGenerationResult.Success {
        assertTrue("Expected generation success but received $this", this is LessonGenerationResult.Success)
        return this as LessonGenerationResult.Success
    }
}
