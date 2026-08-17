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

class Grade2TechnicalReadingFluencyGeneratorTest {
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
                "exact_word",
                "grapheme_pattern",
                "blend_syllables",
                "compound_word",
                "listen_and_find_word",
                "complete_sentence",
                "correct_word",
                "exact_phrase"
            ),
            first.exercises.mapTo(linkedSetOf()) { it.data.getValue("promptKind") }
        )

        first.exercises.forEach { exercise ->
            assertEquals(LearningDomain.READING, exercise.domain)
            assertEquals(SkillLevel.SECOND_GRADE, exercise.level)
            assertEquals(InputMode.TAP_CHOICE, exercise.inputMode)
            assertEquals("decoding_recognition_choice", exercise.data["automatedScope"])
            assertEquals("true", exercise.data["requiresAdultOralFluencyCheck"])
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
    fun `listening items keep the visible instruction separate from the spoken target`() {
        repeat(100) { attempt ->
            success(source("checkpoint", 10), "audio-$attempt").exercises
                .filter { it.data["promptKind"] == "listen_and_find_word" }
                .forEach { exercise ->
                    assertEquals("true", exercise.data["audioRequired"])
                    assertTrue(exercise.prompt.nl.startsWith("Luister"))
                    assertTrue(exercise.prompt.fr?.startsWith("Écoute") == true)
                    assertTrue(exercise.prompt.en?.startsWith("Listen") == true)
                    assertNotEquals(exercise.prompt.nl, exercise.speechText?.nl)
                    assertNotEquals(exercise.prompt.fr, exercise.speechText?.fr)
                    assertNotEquals(exercise.prompt.en, exercise.speechText?.en)
                }
        }
    }

    @Test
    fun `automated choices never claim to assess oral fluency`() {
        repeat(100) { attempt ->
            success(source("checkpoint", 10), "scope-$attempt").exercises.forEach { exercise ->
                assertEquals("decoding_recognition_choice", exercise.data["automatedScope"])
                assertEquals("true", exercise.data["requiresAdultOralFluencyCheck"])
                assertTrue("wordsPerMinute" !in exercise.data)
                assertTrue("oralAccuracy" !in exercise.data)
                assertTrue("prosodyScore" !in exercise.data)
                assertTrue("oralFluencyMastered" !in exercise.data)
            }
        }
    }

    @Test
    fun `localized answers metadata and invalid parameters remain consistent`() {
        repeat(100) { attempt ->
            val result = success(source("checkpoint", 10), "localized-$attempt")
            result.exercises.forEach { exercise ->
                listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                    val options = exercise.localizedOptions(locale)
                    assertEquals(3, options.size)
                    assertEquals(1, options.count { it == exercise.localizedAnswer(locale) })
                }
                assertEquals(Grade2TechnicalReadingFluencyGenerator.generatorId, exercise.data["generatorId"])
                assertEquals("1", exercise.data["generatorVersion"])
                assertEquals(result.seed.fingerprint, exercise.data["seedFingerprint"])
                assertEquals("roadmap.g2.language.technical_reading.fluency", exercise.data["roadmapEntry"])
            }
        }

        val valid = parameters("guided").toMutableMap()
        listOf(
            valid + ("grade" to "1"),
            valid + ("roadmap_entry" to "roadmap.unknown"),
            valid + ("phase" to "review"),
            valid + ("editorial_status" to "planned"),
            valid + ("unexpected" to "value")
        ).forEach { parameters ->
            assertTrue(Grade2TechnicalReadingFluencyGenerator.validate(parameters).isNotEmpty())
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
                profileId = "profile-grade2-technical-reading",
                unitId = "unit.g2.language.technical_reading.fluency",
                lessonId = "lesson.g2.language.technical_reading.fluency.${if (phase == "guided") "practice" else "checkpoint"}",
                sectionId = "section-$phase",
                attemptId = attemptId
            )
        )
        assertTrue("Expected success but received $result", result is LessonGenerationResult.Success)
        return result as LessonGenerationResult.Success
    }

    private fun source(phase: String, count: Int): LessonActivitySource.Generated =
        LessonActivitySource.Generated(
            generatorId = Grade2TechnicalReadingFluencyGenerator.generatorId,
            generatorVersion = Grade2TechnicalReadingFluencyGenerator.generatorVersion,
            exerciseCount = count,
            seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
            parameters = parameters(phase)
        )

    private fun parameters(phase: String): Map<String, String> = linkedMapOf(
        "grade" to "2",
        "roadmap_entry" to "roadmap.g2.language.technical_reading.fluency",
        "phase" to phase,
        "editorial_status" to "approved"
    )
}
