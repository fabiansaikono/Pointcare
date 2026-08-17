package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.localizedAnswer
import be.saikono.poincare.model.localizedOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade1PedestrianSafetyGeneratorTest {
    private val registry = LessonGeneratorRegistry.default()

    @Test
    fun `guided source is deterministic localized unique and covers every reviewed safety kind`() {
        val source = source("guided", 8)
        val first = success(source, "attempt-guided")
        val replay = success(source, "attempt-guided")

        assertEquals(first, replay)
        assertEquals(8, first.exercises.size)
        assertEquals(8, first.exercises.map { it.id }.distinct().size)
        assertEquals(
            setOf(
                "walking_place",
                "stop_at_edge",
                "safe_crossing_place",
                "look_and_listen",
                "traffic_light",
                "driveway_awareness",
                "be_visible",
                "unexpected_danger"
            ),
            first.exercises.mapTo(linkedSetOf()) { it.data.getValue("promptKind") }
        )

        first.exercises.forEach { exercise ->
            assertEquals(InputMode.TAP_CHOICE, exercise.inputMode)
            assertEquals("pedestrian_safety_scenario_choice", exercise.data["automatedScope"])
            assertEquals("true", exercise.data["requiresAdultRoutePractice"])
            assertTrue(exercise.answer.matches(Regex("^[a-z0-9_]+$")))
            listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                val options = exercise.localizedOptions(locale)
                assertEquals(3, options.size)
                assertEquals(3, options.distinct().size)
                assertEquals(1, options.count { it == exercise.localizedAnswer(locale) })
            }
            assertTrue(exercise.prompt.nl.isNotBlank())
            assertTrue(exercise.prompt.fr?.isNotBlank() == true)
            assertTrue(exercise.prompt.en?.isNotBlank() == true)
            assertTrue(exercise.speechText?.nl?.isNotBlank() == true)
            assertTrue(exercise.speechText?.fr?.isNotBlank() == true)
            assertTrue(exercise.speechText?.en?.isNotBlank() == true)
        }
    }

    @Test
    fun `checkpoint changes with stable attempt id and remains reproducible`() {
        val source = source("checkpoint", 10)
        val first = success(source, "attempt-a")
        val replay = success(source, "attempt-a")
        val changed = success(source, "attempt-b")

        assertEquals(first, replay)
        assertNotEquals(first.seed, changed.seed)
        assertNotEquals(first.exercises, changed.exercises)
        assertEquals(10, first.exercises.size)
        assertEquals(10, first.exercises.map { it.id }.distinct().size)
    }

    @Test
    fun `screen choices never claim to verify real route or crossing skill`() {
        repeat(100) { attempt ->
            success(source("checkpoint", 10), "scope-$attempt").exercises.forEach { exercise ->
                assertEquals("pedestrian_safety_scenario_choice", exercise.data["automatedScope"])
                assertEquals("true", exercise.data["requiresAdultRoutePractice"])
                assertTrue("streetCrossingObserved" !in exercise.data)
                assertTrue("routeMasteryVerified" !in exercise.data)
                assertTrue("trafficJudgementScore" !in exercise.data)
            }
        }
    }

    @Test
    fun `green signal and unexpected danger choices retain a safety check or adult help`() {
        repeat(100) { attempt ->
            val exercises = success(source("checkpoint", 10), "safety-$attempt").exercises
            exercises.filter { it.data["promptKind"] == "traffic_light" }.forEach { exercise ->
                if (exercise.answer == "green_check") {
                    assertTrue(exercise.localizedAnswer("nl-BE").contains("controleren"))
                    assertTrue(exercise.localizedAnswer("fr-BE").contains("Vérifier"))
                    assertTrue(exercise.localizedAnswer("en-US").contains("check"))
                }
            }
            exercises.filter { it.data["promptKind"] == "unexpected_danger" }.forEach { exercise ->
                assertTrue(exercise.localizedAnswer("nl-BE").contains("volwassene"))
                assertTrue(exercise.localizedAnswer("fr-BE").contains("adulte"))
                assertTrue(exercise.localizedAnswer("en-US").contains("adult"))
            }
        }
    }

    @Test
    fun `localized answers options and metadata remain consistent across many attempts`() {
        repeat(100) { attempt ->
            val result = success(source("checkpoint", 10), "localized-$attempt")
            result.exercises.forEach { exercise ->
                listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                    val options = exercise.localizedOptions(locale)
                    assertEquals(3, options.size)
                    assertEquals(1, options.count { it == exercise.localizedAnswer(locale) })
                }
                assertEquals(Grade1PedestrianSafetyGenerator.generatorId, exercise.data["generatorId"])
                assertEquals("1", exercise.data["generatorVersion"])
                assertEquals(result.seed.fingerprint, exercise.data["seedFingerprint"])
                assertEquals("roadmap.g1.world.traffic.pedestrian_safety", exercise.data["roadmapEntry"])
            }
        }
    }

    @Test
    fun `invalid grade roadmap phase status and extra parameters are rejected`() {
        val valid = parameters("guided").toMutableMap()
        val cases = listOf(
            valid + ("grade" to "2"),
            valid + ("roadmap_entry" to "roadmap.unknown"),
            valid + ("phase" to "review"),
            valid + ("editorial_status" to "planned"),
            valid + ("unexpected" to "value")
        )
        cases.forEach { parameters ->
            assertTrue(Grade1PedestrianSafetyGenerator.validate(parameters).isNotEmpty())
        }
    }

    private fun success(
        source: LessonActivitySource.Generated,
        attemptId: String
    ): LessonGenerationResult.Success {
        val phase = source.parameters.getValue("phase")
        val result = registry.materialize(
            source = source,
            context = LessonGenerationContext(
                profileId = "profile-grade1-traffic",
                unitId = "unit.g1.world.traffic.pedestrian_safety",
                lessonId = "lesson.g1.world.traffic.pedestrian_safety.${if (phase == "guided") "practice" else "checkpoint"}",
                sectionId = "section-$phase",
                attemptId = attemptId
            )
        )
        assertTrue("Expected success but received $result", result is LessonGenerationResult.Success)
        return result as LessonGenerationResult.Success
    }

    private fun source(phase: String, count: Int): LessonActivitySource.Generated =
        LessonActivitySource.Generated(
            generatorId = Grade1PedestrianSafetyGenerator.generatorId,
            generatorVersion = Grade1PedestrianSafetyGenerator.generatorVersion,
            exerciseCount = count,
            seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
            parameters = parameters(phase)
        )

    private fun parameters(phase: String): Map<String, String> = linkedMapOf(
        "grade" to "1",
        "roadmap_entry" to "roadmap.g1.world.traffic.pedestrian_safety",
        "phase" to phase,
        "editorial_status" to "approved"
    )
}
