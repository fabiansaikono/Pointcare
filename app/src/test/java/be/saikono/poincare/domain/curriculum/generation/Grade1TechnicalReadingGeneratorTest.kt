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

class Grade1TechnicalReadingGeneratorTest {
    private val registry = LessonGeneratorRegistry.default()

    @Test
    fun `guided source is deterministic localized unique and covers every reviewed decoding prompt kind`() {
        val source = source("guided", 8)
        val first = success(source, "attempt-guided")
        val replay = success(source, "attempt-guided")

        assertEquals(first, replay)
        assertEquals(8, first.exercises.size)
        assertEquals(8, first.exercises.map { it.id }.distinct().size)
        assertEquals(
            setOf("blend_word", "missing_letter", "listen_and_find_word"),
            first.exercises.map { it.data.getValue("promptKind") }.toSet()
        )

        first.exercises.forEach { exercise ->
            assertEquals(
                if (exercise.data["promptKind"] == "listen_and_find_word") {
                    InputMode.LISTEN_AND_SELECT
                } else {
                    InputMode.TAP_CHOICE
                },
                exercise.inputMode
            )
            assertTrue(exercise.answer.matches(Regex("^[a-z0-9_]+$")))
            assertEquals("mkm", exercise.data["readingFocus"])
            listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                val options = exercise.localizedOptions(locale)
                assertEquals(3, options.size)
                assertEquals(3, options.distinct().size)
                assertEquals(1, options.count { it == exercise.localizedAnswer(locale) })
            }
            assertTrue(exercise.prompt.nl.isNotBlank())
            assertTrue(exercise.prompt.fr?.isNotBlank() == true)
            assertTrue(exercise.prompt.en?.isNotBlank() == true)
            assertTrue(exercise.speechText?.nl?.contains("?") == false)
            assertTrue(exercise.speechText?.fr?.contains("?") == false)
            assertTrue(exercise.speechText?.en?.contains("?") == false)
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
    fun `localized answers options speech and metadata remain consistent across many attempts`() {
        val source = source("checkpoint", 10)
        repeat(100) { attempt ->
            val result = success(source, "attempt-$attempt")
            result.exercises.forEach { exercise ->
                listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                    val options = exercise.localizedOptions(locale)
                    assertEquals(3, options.size)
                    assertEquals(1, options.count { it == exercise.localizedAnswer(locale) })
                }
                assertEquals(Grade1TechnicalReadingGenerator.generatorId, exercise.data["generatorId"])
                assertEquals("1", exercise.data["generatorVersion"])
                assertEquals(result.seed.fingerprint, exercise.data["seedFingerprint"])
                assertEquals("choice", exercise.data["answerRole"])
                assertEquals(
                    "roadmap.g1.language.technical_reading.decoding",
                    exercise.data["roadmapEntry"]
                )
            }
        }
    }

    @Test
    fun `missing letter options keep visible labels separate from spoken labels`() {
        val exercises = success(source("checkpoint", 10), "letter-speech").exercises
        val letterExercise = exercises.first { it.data["promptKind"] == "missing_letter" }

        assertTrue(letterExercise.localizedOptions("nl-BE").all { it.length <= 2 })
        assertTrue(letterExercise.data.getValue("optionSpeech_nl-BE").isNotBlank())
        assertTrue(letterExercise.data.getValue("optionSpeech_fr-BE").isNotBlank())
        assertTrue(letterExercise.data.getValue("optionSpeech_en-US").isNotBlank())
        listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
            assertTrue(letterExercise.data.getValue("targetWord_$locale").isNotBlank())
        }
    }

    @Test
    fun `audio word matching never sends visual transposition nonwords to speech`() {
        repeat(30) { attempt ->
            success(source("checkpoint", 12), "tts-safe-$attempt").exercises
                .filter { it.data["audioRequired"] == "true" }
                .forEach { exercise ->
                    listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                        val speech = exercise.data.getValue("optionSpeech_$locale").lowercase()
                        assertTrue("vsi" !in speech)
                        assertTrue("beok" !in speech)
                        assertTrue("chta" !in speech)
                        assertTrue("fsih" !in speech)
                    }
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
            assertTrue(Grade1TechnicalReadingGenerator.validate(parameters).isNotEmpty())
        }
    }

    private fun success(
        source: LessonActivitySource.Generated,
        attemptId: String
    ): LessonGenerationResult.Success {
        val result = registry.materialize(
            source = source,
            context = LessonGenerationContext(
                profileId = "profile-grade1-reading",
                unitId = "unit.g1.language.technical_reading.decoding",
                lessonId = if (source.parameters["phase"] == "guided") {
                    "lesson.g1.language.technical_reading.decoding.practice"
                } else {
                    "lesson.g1.language.technical_reading.decoding.checkpoint"
                },
                sectionId = "section-${source.parameters["phase"]}",
                attemptId = attemptId
            )
        )
        assertTrue("Expected success but received $result", result is LessonGenerationResult.Success)
        return result as LessonGenerationResult.Success
    }

    private fun source(phase: String, count: Int): LessonActivitySource.Generated =
        LessonActivitySource.Generated(
            generatorId = Grade1TechnicalReadingGenerator.generatorId,
            generatorVersion = Grade1TechnicalReadingGenerator.generatorVersion,
            exerciseCount = count,
            seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
            parameters = parameters(phase)
        )

    private fun parameters(phase: String): Map<String, String> = linkedMapOf(
        "grade" to "1",
        "roadmap_entry" to "roadmap.g1.language.technical_reading.decoding",
        "unit_id" to "unit.g1.nl.technical_reading.mkm-woorden-synthetiseren-en-analyseren",
        "reading_focus" to "mkm",
        "phase" to phase,
        "editorial_status" to "approved"
    )
}
