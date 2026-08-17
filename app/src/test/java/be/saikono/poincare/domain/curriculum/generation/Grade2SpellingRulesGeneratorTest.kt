package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.localizedAnswer
import be.saikono.poincare.model.localizedOptionSpeech
import be.saikono.poincare.model.localizedOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade2SpellingRulesGeneratorTest {
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
                "capital_sentence_start",
                "capital_proper_name",
                "punctuation_mark",
                "syllable_count",
                "syllable_split",
                "open_closed_pattern",
                "plural_form",
                "plural_in_sentence"
            ),
            first.exercises.mapTo(linkedSetOf()) { it.data.getValue("promptKind") }
        )

        first.exercises.forEach { exercise ->
            assertEquals(LearningDomain.WRITING, exercise.domain)
            assertEquals(SkillLevel.SECOND_GRADE, exercise.level)
            assertEquals(InputMode.TAP_CHOICE, exercise.inputMode)
            assertEquals("spelling_rule_recognition_choice", exercise.data["automatedScope"])
            assertEquals("true", exercise.data["requiresAdultIndependentWritingCheck"])
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
    fun `punctuation symbols always have explicit localized spoken labels`() {
        repeat(100) { attempt ->
            success(source("checkpoint", 10), "punctuation-$attempt").exercises
                .filter { it.data["promptKind"] == "punctuation_mark" }
                .forEach { exercise ->
                    listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                        val options = exercise.localizedOptions(locale)
                        assertEquals(setOf(".", "?", "!"), options.toSet())
                        val speech = options.map { exercise.localizedOptionSpeech(it, locale) }
                        assertEquals(3, speech.distinct().size)
                        assertTrue(speech.none { it in setOf(".", "?", "!") })
                    }
                }
        }
    }

    @Test
    fun `languages use their own syllable and plural examples`() {
        repeat(100) { attempt ->
            success(source("checkpoint", 10), "language-$attempt").exercises.forEach { exercise ->
                listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                    val options = exercise.localizedOptions(locale)
                    assertEquals(3, options.size)
                    assertEquals(1, options.count { it == exercise.localizedAnswer(locale) })
                }
                if (exercise.data["promptKind"] == "open_closed_pattern") {
                    assertTrue(exercise.localizedOptions("nl-BE").any { '-' in it })
                    assertTrue(exercise.localizedOptions("fr-BE").any { '-' in it })
                    assertTrue(exercise.localizedOptions("en-US").any { '-' in it })
                }
                if (exercise.data["promptKind"] in setOf("plural_form", "plural_in_sentence")) {
                    assertTrue(exercise.localizedAnswer("nl-BE").isNotBlank())
                    assertTrue(exercise.localizedAnswer("fr-BE").isNotBlank())
                    assertTrue(exercise.localizedAnswer("en-US").isNotBlank())
                }
            }
        }
    }

    @Test
    fun `automated recognition never claims independent spelling or handwriting mastery`() {
        repeat(100) { attempt ->
            success(source("checkpoint", 10), "scope-$attempt").exercises.forEach { exercise ->
                assertEquals("spelling_rule_recognition_choice", exercise.data["automatedScope"])
                assertEquals("true", exercise.data["requiresAdultIndependentWritingCheck"])
                assertTrue("independentSpellingScore" !in exercise.data)
                assertTrue("handwritingScore" !in exercise.data)
                assertTrue("pencilGripScore" !in exercise.data)
                assertTrue("writtenTextMastered" !in exercise.data)
            }
        }
    }

    @Test
    fun `metadata and invalid parameters remain consistent`() {
        repeat(100) { attempt ->
            val result = success(source("checkpoint", 10), "metadata-$attempt")
            result.exercises.forEach { exercise ->
                assertEquals(Grade2SpellingRulesGenerator.generatorId, exercise.data["generatorId"])
                assertEquals("1", exercise.data["generatorVersion"])
                assertEquals(result.seed.fingerprint, exercise.data["seedFingerprint"])
                assertEquals(
                    "roadmap.g2.spelling.rules.punctuation_syllables_plural",
                    exercise.data["roadmapEntry"]
                )
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
            assertTrue(Grade2SpellingRulesGenerator.validate(parameters).isNotEmpty())
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
                profileId = "profile-grade2-spelling",
                unitId = "unit.g2.spelling.rules.punctuation_syllables_plural",
                lessonId = "lesson.g2.spelling.rules.punctuation_syllables_plural.${if (phase == "guided") "practice" else "checkpoint"}",
                sectionId = "section-$phase",
                attemptId = attemptId
            )
        )
        assertTrue("Expected success but received $result", result is LessonGenerationResult.Success)
        return result as LessonGenerationResult.Success
    }

    private fun source(phase: String, count: Int): LessonActivitySource.Generated =
        LessonActivitySource.Generated(
            generatorId = Grade2SpellingRulesGenerator.generatorId,
            generatorVersion = Grade2SpellingRulesGenerator.generatorVersion,
            exerciseCount = count,
            seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
            parameters = parameters(phase)
        )

    private fun parameters(phase: String): Map<String, String> = linkedMapOf(
        "grade" to "2",
        "roadmap_entry" to "roadmap.g2.spelling.rules.punctuation_syllables_plural",
        "phase" to phase,
        "editorial_status" to "approved"
    )
}
