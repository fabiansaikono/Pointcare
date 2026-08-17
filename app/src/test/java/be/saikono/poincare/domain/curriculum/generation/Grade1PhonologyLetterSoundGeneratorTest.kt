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

class Grade1PhonologyLetterSoundGeneratorTest {
    private val registry = LessonGeneratorRegistry.default()

    @Test
    fun `guided source is deterministic localized unique and covers every reviewed phonology prompt kind`() {
        val source = source("guided", 8)
        val first = success(source, "attempt-guided")
        val replay = success(source, "attempt-guided")

        assertEquals(first, replay)
        assertEquals(8, first.exercises.size)
        assertEquals(8, first.exercises.map { it.id }.distinct().size)
        assertTrue(
            first.exercises.mapTo(linkedSetOf()) { it.data.getValue("promptKind") }.containsAll(
                setOf(
                    "rhyme",
                    "syllable_count",
                    "first_sound",
                    "last_sound",
                    "blend_sounds",
                    "letter_sound"
                )
            )
        )

        first.exercises.forEach { exercise ->
            val isWrittenLetterQuestion = exercise.data["promptKind"] == "letter_sound"
            assertEquals(
                if (isWrittenLetterQuestion) InputMode.TAP_CHOICE else InputMode.LISTEN_AND_SELECT,
                exercise.inputMode
            )
            assertEquals((!isWrittenLetterQuestion).toString(), exercise.data["audioRequired"])
            if (isWrittenLetterQuestion) {
                assertTrue(exercise.data.getValue("masteryTag").startsWith("grapheme."))
                assertTrue(exercise.data.getValue("questionIntent").startsWith("GRAPHEME_"))
            } else {
                assertTrue(exercise.data.getValue("masteryTag").startsWith("phoneme."))
            }
            assertTrue(exercise.answer.matches(Regex("^[a-z0-9_]+$")))
            listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                val options = exercise.localizedOptions(locale)
                assertEquals(3, options.size)
                assertEquals(3, options.distinct().size)
                assertTrue(exercise.localizedAnswer(locale) in options)
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
                assertEquals(Grade1PhonologyLetterSoundGenerator.generatorId, exercise.data["generatorId"])
                assertEquals("1", exercise.data["generatorVersion"])
                assertEquals(result.seed.fingerprint, exercise.data["seedFingerprint"])
                assertEquals("choice", exercise.data["answerRole"])
                assertEquals(
                    "roadmap.g1.spelling.phonology.sound_letter",
                    exercise.data["roadmapEntry"]
                )
            }
        }
    }

    @Test
    fun `letter options keep visible labels separate from spoken labels`() {
        val source = source("checkpoint", 10)
        val exercises = success(source, "letter-speech").exercises
        val letterExercise = exercises.first { it.data["promptKind"] == "letter_sound" }

        assertTrue(letterExercise.localizedOptions("nl-BE").all { it.length == 1 })
        assertTrue(letterExercise.data.getValue("optionSpeech_nl-BE").contains("em"))
        assertTrue(letterExercise.data.getValue("optionSpeech_fr-BE").isNotBlank())
        assertTrue(letterExercise.data.getValue("optionSpeech_en-US").isNotBlank())
    }

    @Test
    fun `initial and final sound questions are explicitly auditory phoneme tasks`() {
        val exercises = success(source("checkpoint", 12), "phoneme-audit").exercises
        val soundQuestions = exercises.filter {
            it.data["promptKind"] in setOf("first_sound", "last_sound")
        }

        assertTrue(soundQuestions.isNotEmpty())
        soundQuestions.forEach { exercise ->
            assertEquals(InputMode.LISTEN_AND_SELECT, exercise.inputMode)
            assertEquals("true", exercise.data["audioRequired"])
            assertTrue(exercise.data.getValue("questionIntent").startsWith("PHONEME_"))
            assertTrue(exercise.data.getValue("masteryTag").startsWith("phoneme."))
            assertTrue(!exercise.prompt.nl.contains("letter", ignoreCase = true))
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
            assertTrue(Grade1PhonologyLetterSoundGenerator.validate(parameters).isNotEmpty())
        }
    }

    private fun success(
        source: LessonActivitySource.Generated,
        attemptId: String
    ): LessonGenerationResult.Success {
        val result = registry.materialize(
            source = source,
            context = LessonGenerationContext(
                profileId = "profile-grade1-phonology",
                unitId = "unit.g1.spelling.phonology.sound_letter",
                lessonId = if (source.parameters["phase"] == "guided") {
                    "lesson.g1.spelling.phonology.sound_letter.practice"
                } else {
                    "lesson.g1.spelling.phonology.sound_letter.checkpoint"
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
            generatorId = Grade1PhonologyLetterSoundGenerator.generatorId,
            generatorVersion = Grade1PhonologyLetterSoundGenerator.generatorVersion,
            exerciseCount = count,
            seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
            parameters = parameters(phase)
        )

    private fun parameters(phase: String): Map<String, String> = linkedMapOf(
        "grade" to "1",
        "roadmap_entry" to "roadmap.g1.spelling.phonology.sound_letter",
        "phase" to phase,
        "editorial_status" to "approved"
    )
}
