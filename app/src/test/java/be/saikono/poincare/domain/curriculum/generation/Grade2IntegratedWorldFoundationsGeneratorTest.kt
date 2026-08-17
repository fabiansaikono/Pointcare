package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.localizedAnswer
import be.saikono.poincare.model.localizedOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade2IntegratedWorldFoundationsGeneratorTest {
    private val registry = LessonGeneratorRegistry.default()

    @Test
    fun `guided source is deterministic localized unique and covers every reviewed kind`() {
        val source = source("guided", 8)
        val first = success(source, "attempt-guided")
        val replay = success(source, "attempt-guided")

        assertEquals(first, replay)
        assertEquals(8, first.exercises.size)
        assertEquals(8, first.exercises.map { it.id }.distinct().size)
        assertEquals(
            setOf(
                "seasonal_change",
                "nature_relation",
                "daily_sequence",
                "time_order",
                "map_position",
                "community_place",
                "community_role",
                "shared_space_choice"
            ),
            first.exercises.mapTo(linkedSetOf()) { it.data.getValue("promptKind") }
        )

        first.exercises.forEach { exercise ->
            assertEquals(LearningDomain.WORLD_KNOWLEDGE, exercise.domain)
            assertEquals(SkillLevel.SECOND_GRADE, exercise.level)
            assertEquals(InputMode.TAP_CHOICE, exercise.inputMode)
            assertEquals("integrated_world_scenario_choice", exercise.data["automatedScope"])
            assertEquals("true", exercise.data["requiresAdultOutingObservationConversation"])
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
            assertTrue(exercise.speechText?.nl?.contains('?') == false)
            assertTrue(exercise.speechText?.fr?.contains('?') == false)
            assertTrue(exercise.speechText?.en?.contains('?') == false)
        }
    }

    @Test
    fun `checkpoint changes with attempt id and remains reproducible`() {
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
    fun `automated exercises never claim to verify outings observations or conversations`() {
        repeat(100) { attempt ->
            success(source("checkpoint", 10), "scope-$attempt").exercises.forEach { exercise ->
                assertEquals("integrated_world_scenario_choice", exercise.data["automatedScope"])
                assertEquals("true", exercise.data["requiresAdultOutingObservationConversation"])
                assertTrue("outingCompleted" !in exercise.data)
                assertTrue("observationVerified" !in exercise.data)
                assertTrue("conversationAssessed" !in exercise.data)
                assertTrue("practicalMasteryScore" !in exercise.data)
            }
        }
    }

    @Test
    fun `localized answer options and safeguarding remain consistent across many attempts`() {
        repeat(100) { attempt ->
            val result = success(source("checkpoint", 10), "localized-$attempt")
            result.exercises.forEach { exercise ->
                listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                    val options = exercise.localizedOptions(locale)
                    assertEquals(3, options.size)
                    assertEquals(1, options.count { it == exercise.localizedAnswer(locale) })
                }
                assertEquals(Grade2IntegratedWorldFoundationsGenerator.generatorId, exercise.data["generatorId"])
                assertEquals("1", exercise.data["generatorVersion"])
                assertEquals(result.seed.fingerprint, exercise.data["seedFingerprint"])
                assertEquals("roadmap.g2.world.integrated_foundations", exercise.data["roadmapEntry"])
                if (exercise.data["promptKind"] == "community_role" &&
                    exercise.data["contentKey"] == "community_role.2"
                ) {
                    assertEquals("firefighter", exercise.answer)
                    assertTrue(exercise.localizedOptions("nl-BE").none { it.contains("zelf", true) })
                }
            }
        }
    }

    @Test
    fun `invalid grade roadmap phase status and extra parameters are rejected`() {
        val valid = parameters("guided").toMutableMap()
        val cases = listOf(
            valid + ("grade" to "1"),
            valid + ("roadmap_entry" to "roadmap.unknown"),
            valid + ("phase" to "review"),
            valid + ("editorial_status" to "planned"),
            valid + ("unexpected" to "value")
        )
        cases.forEach { parameters ->
            assertTrue(Grade2IntegratedWorldFoundationsGenerator.validate(parameters).isNotEmpty())
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
                profileId = "profile-grade2-world",
                unitId = "unit.g2.world.integrated_foundations",
                lessonId = "lesson.g2.world.integrated_foundations.${if (phase == "guided") "practice" else "checkpoint"}",
                sectionId = "section-$phase",
                attemptId = attemptId
            )
        )
        assertTrue("Expected success but received $result", result is LessonGenerationResult.Success)
        return result as LessonGenerationResult.Success
    }

    private fun source(phase: String, count: Int): LessonActivitySource.Generated =
        LessonActivitySource.Generated(
            generatorId = Grade2IntegratedWorldFoundationsGenerator.generatorId,
            generatorVersion = Grade2IntegratedWorldFoundationsGenerator.generatorVersion,
            exerciseCount = count,
            seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
            parameters = parameters(phase)
        )

    private fun parameters(phase: String): Map<String, String> = linkedMapOf(
        "grade" to "2",
        "roadmap_entry" to "roadmap.g2.world.integrated_foundations",
        "phase" to phase,
        "editorial_status" to "approved"
    )
}
