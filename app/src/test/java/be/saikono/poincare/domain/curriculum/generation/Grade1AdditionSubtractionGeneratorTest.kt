package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade1AdditionSubtractionGeneratorTest {
    private val registry = LessonGeneratorRegistry.default()

    @Test
    fun `approved subtraction source is deterministic complete and grade appropriate`() {
        val source = source(operation = "subtraction", phase = "guided", count = 8)
        val context = context(attemptId = "attempt-a")

        val first = registry.materialize(source, context).success()
        val second = registry.materialize(source, context).success()

        assertEquals(first.seed, second.seed)
        assertEquals(first.exercises, second.exercises)
        assertEquals(8, first.exercises.size)
        assertEquals(8, first.exercises.map { it.id }.distinct().size)

        first.exercises.forEach { exercise ->
            val left = exercise.data.getValue("left").toInt()
            val right = exercise.data.getValue("right").toInt()
            val result = exercise.data.getValue("result").toInt()
            val answerRole = exercise.data.getValue("answerRole")

            assertTrue(left in 1..20)
            assertTrue(right in 0..left)
            assertEquals(left - right, result)
            assertTrue(result >= 0)
            assertEquals(
                if (answerRole == "result") result.toString() else right.toString(),
                exercise.answer
            )
            assertTrue(exercise.prompt.nl.isNotBlank())
            assertTrue(exercise.prompt.fr?.isNotBlank() == true)
            assertTrue(exercise.prompt.en?.isNotBlank() == true)
            assertTrue(exercise.speechText?.nl?.isNotBlank() == true)
        }
    }

    @Test
    fun `different stable attempt id creates a different reproducible set`() {
        val source = source(operation = "subtraction", phase = "checkpoint", count = 10)

        val first = registry.materialize(source, context("attempt-a")).success()
        val second = registry.materialize(source, context("attempt-b")).success()
        val firstReplay = registry.materialize(source, context("attempt-a")).success()

        assertNotEquals(first.seed, second.seed)
        assertNotEquals(first.exercises, second.exercises)
        assertEquals(first.exercises, firstReplay.exercises)
    }

    @Test
    fun `version one also supports the future grade one addition contract`() {
        val source = source(operation = "addition", phase = "checkpoint", count = 10)
        val generated = registry.materialize(source, context("addition-attempt")).success()

        generated.exercises.forEach { exercise ->
            val left = exercise.data.getValue("left").toInt()
            val right = exercise.data.getValue("right").toInt()
            val result = exercise.data.getValue("result").toInt()
            val answerRole = exercise.data.getValue("answerRole")

            assertTrue(left in 0..20)
            assertTrue(right in 0..20)
            assertTrue(result in 1..20)
            assertEquals(left + right, result)
            assertEquals(
                if (answerRole == "result") result.toString() else right.toString(),
                exercise.answer
            )
        }
    }

    @Test
    fun `within five contract never exceeds five`() {
        listOf("addition", "subtraction").forEach { operation ->
            val generated = registry.materialize(
                source(operation = operation, phase = "checkpoint", count = 20, maxResult = 5),
                context("within-five-$operation")
            ).success()

            generated.exercises.forEach { exercise ->
                val left = exercise.data.getValue("left").toInt()
                val right = exercise.data.getValue("right").toInt()
                val result = exercise.data.getValue("result").toInt()
                assertTrue(left in 0..5)
                assertTrue(right in 0..5)
                assertTrue(result in 0..5)
            }
        }
    }

    @Test
    fun `localized story grammar agrees with singular and plural quantities`() {
        val allText = buildList {
            repeat(80) { attempt ->
                listOf("addition", "subtraction").forEach { operation ->
                    registry.materialize(
                        source(operation, "checkpoint", 20, maxResult = 5),
                        context("grammar-$operation-$attempt")
                    ).success().exercises.forEach { exercise ->
                        add(exercise.prompt.nl)
                        add(exercise.prompt.fr.orEmpty())
                        add(exercise.prompt.en.orEmpty())
                        add(exercise.speechText?.nl.orEmpty())
                        add(exercise.speechText?.fr.orEmpty())
                        add(exercise.speechText?.en.orEmpty())
                    }
                }
            }
        }

        val invalid = listOf(
            Regex("""\b1 sterren\b""", RegexOption.IGNORE_CASE),
            Regex("""\b1 étoiles\b""", RegexOption.IGNORE_CASE),
            Regex("""\b1 stars\b""", RegexOption.IGNORE_CASE),
            Regex("""\ber komen 1\b""", RegexOption.IGNORE_CASE),
            Regex("""\bthere are 1\b""", RegexOption.IGNORE_CASE)
        )
        invalid.forEach { pattern ->
            assertTrue("Invalid number agreement: ${pattern.pattern}", allText.none(pattern::containsMatchIn))
        }
    }

    private fun source(
        operation: String,
        phase: String,
        count: Int,
        maxResult: Int? = null
    ): LessonActivitySource.Generated = LessonActivitySource.Generated(
        generatorId = Grade1AdditionSubtractionGenerator.generatorId,
        generatorVersion = Grade1AdditionSubtractionGenerator.generatorVersion,
        exerciseCount = count,
        seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
        parameters = mapOf(
            "grade" to "1",
            "operation" to operation,
            "phase" to phase,
            "editorial_status" to "approved"
        ) + listOfNotNull(maxResult?.let { "max_result" to it.toString() })
    )

    private fun context(attemptId: String): LessonGenerationContext = LessonGenerationContext(
        profileId = "profile-001",
        unitId = "math.addition.g1.foundations",
        lessonId = "lesson.math.subtraction.g1.within20",
        sectionId = "lesson.math.subtraction.g1.within20.guided",
        attemptId = attemptId
    )

    private fun LessonGenerationResult.success(): LessonGenerationResult.Success {
        assertTrue("Expected generation success but received $this", this is LessonGenerationResult.Success)
        return this as LessonGenerationResult.Success
    }
}
