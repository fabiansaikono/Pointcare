package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.localizedAnswer
import be.saikono.poincare.model.localizedOptionSpeech
import be.saikono.poincare.model.localizedOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade1WritingLettersSentencesGeneratorTest {
    private val registry = LessonGeneratorRegistry.default()

    @Test
    fun `guided source is deterministic localized unique and covers every reviewed writing kind`() {
        val source = source("guided", 8)
        val first = success(source, "attempt-guided")
        val replay = success(source, "attempt-guided")

        assertEquals(first, replay)
        assertEquals(8, first.exercises.size)
        assertEquals(8, first.exercises.map { it.id }.distinct().size)
        assertEquals(
            setOf(
                "letter_case",
                "missing_letter",
                "form_word",
                "word_order",
                "capital_start",
                "end_punctuation",
                "complete_sentence",
                "complete_with_word"
            ),
            first.exercises.mapTo(linkedSetOf()) { it.data.getValue("promptKind") }
        )

        first.exercises.forEach { exercise ->
            assertEquals(InputMode.TAP_CHOICE, exercise.inputMode)
            assertEquals("letter_word_sentence_choice", exercise.data["automatedScope"])
            assertEquals("true", exercise.data["requiresAdultHandwritingPractice"])
            assertTrue(exercise.answer.matches(Regex("^[a-z0-9_]+$")))
            listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                val options = exercise.localizedOptions(locale)
                assertEquals(3, options.size)
                assertEquals(3, options.distinct().size)
                assertEquals(1, options.count { it == exercise.localizedAnswer(locale) })
                options.forEach { option ->
                    assertTrue(exercise.localizedOptionSpeech(option, locale).isNotBlank())
                }
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
    fun `punctuation choices have explicit distinct speech instead of relying on symbols`() {
        repeat(100) { attempt ->
            val exercises = success(source("checkpoint", 10), "punctuation-$attempt").exercises
            exercises.filter { it.data["promptKind"] == "end_punctuation" }.forEach { exercise ->
                listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                    val speeches = exercise.localizedOptions(locale)
                        .map { exercise.localizedOptionSpeech(it, locale) }
                    assertEquals(3, speeches.distinct().size)
                    assertTrue(speeches.all { it.isNotBlank() })
                }
            }
        }
    }

    @Test
    fun `automated exercises never claim to assess handwriting performance`() {
        repeat(100) { attempt ->
            success(source("checkpoint", 10), "scope-$attempt").exercises.forEach { exercise ->
                assertEquals(InputMode.TAP_CHOICE, exercise.inputMode)
                assertEquals("letter_word_sentence_choice", exercise.data["automatedScope"])
                assertEquals("true", exercise.data["requiresAdultHandwritingPractice"])
                assertTrue("handwritingScore" !in exercise.data)
                assertTrue("pencilGripScore" !in exercise.data)
                assertTrue("motorSkillScore" !in exercise.data)
            }
        }
    }

    @Test
    fun `letter tracing mode creates interactive trace exercises for the requested group`() {
        val tracingSource = LessonActivitySource.Generated(
            generatorId = Grade1WritingLettersSentencesGenerator.generatorId,
            generatorVersion = Grade1WritingLettersSentencesGenerator.generatorVersion,
            exerciseCount = 10,
            seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
            parameters = parameters("guided") + mapOf(
                "mode" to "letter_tracing",
                "letter_group" to "lowercase_q_z"
            )
        )

        val result = success(tracingSource, "trace-lowercase")
        assertEquals(('q'..'z').map { it.toString() }, result.exercises.map { it.data.getValue("letter") })
        result.exercises.forEach { exercise ->
            assertEquals(InputMode.TRACE_LETTER, exercise.inputMode)
            assertEquals("letter_tracing", exercise.skill)
            assertEquals("guided_letter_trace", exercise.data["automatedScope"])
            assertEquals("completed", exercise.answer)
        }
    }

    @Test
    fun `number tracing mode covers every digit from zero to nine`() {
        val tracingSource = LessonActivitySource.Generated(
            generatorId = Grade1WritingLettersSentencesGenerator.generatorId,
            generatorVersion = Grade1WritingLettersSentencesGenerator.generatorVersion,
            exerciseCount = 10,
            seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
            parameters = parameters("checkpoint") + mapOf(
                "mode" to "letter_tracing",
                "letter_group" to "number_0_9"
            )
        )

        val result = success(tracingSource, "trace-numbers")
        assertEquals(('0'..'9').map { it.toString() }, result.exercises.map { it.data.getValue("letter") })
        result.exercises.forEach { exercise ->
            assertEquals(InputMode.TRACE_LETTER, exercise.inputMode)
            assertEquals("number_tracing", exercise.skill)
            assertEquals("number", exercise.data["letterCase"])
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
                assertEquals(Grade1WritingLettersSentencesGenerator.generatorId, exercise.data["generatorId"])
                assertEquals("1", exercise.data["generatorVersion"])
                assertEquals(result.seed.fingerprint, exercise.data["seedFingerprint"])
                assertEquals("roadmap.g1.language.writing.letters_sentences", exercise.data["roadmapEntry"])
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
            assertTrue(Grade1WritingLettersSentencesGenerator.validate(parameters).isNotEmpty())
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
                profileId = "profile-grade1-writing",
                unitId = "unit.g1.language.writing.letters_sentences",
                lessonId = "lesson.g1.language.writing.letters_sentences.${if (phase == "guided") "practice" else "checkpoint"}",
                sectionId = "section-$phase",
                attemptId = attemptId
            )
        )
        assertTrue("Expected success but received $result", result is LessonGenerationResult.Success)
        return result as LessonGenerationResult.Success
    }

    private fun source(phase: String, count: Int): LessonActivitySource.Generated =
        LessonActivitySource.Generated(
            generatorId = Grade1WritingLettersSentencesGenerator.generatorId,
            generatorVersion = Grade1WritingLettersSentencesGenerator.generatorVersion,
            exerciseCount = count,
            seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
            parameters = parameters(phase)
        )

    private fun parameters(phase: String): Map<String, String> = linkedMapOf(
        "grade" to "1",
        "roadmap_entry" to "roadmap.g1.language.writing.letters_sentences",
        "phase" to phase,
        "editorial_status" to "approved"
    )
}
