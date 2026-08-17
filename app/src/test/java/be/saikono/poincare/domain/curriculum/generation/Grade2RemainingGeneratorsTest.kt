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

class Grade2RemainingGeneratorsTest {
    private val registry = LessonGeneratorRegistry.default()
    private val cases = listOf(
        Case(
            Grade2ShortTextWritingGenerator,
            "roadmap.g2.language.writing.short_texts",
            "unit.g2.language.writing.short_texts",
            "lesson.g2.language.writing.short_texts",
            LearningDomain.READING,
            InputMode.TAP_CHOICE,
            "short_text_planning_revision_choice",
            "requiresAdultIndependentTextReview",
            setOf("complete_sentence", "sentence_order", "choose_title", "opening_sentence", "detail_sentence", "closing_sentence", "link_sentences", "revise_short_text")
        ),
        Case(
            Grade2ExtendedListeningGenerator,
            "roadmap.g2.language.listening.extended_instructions",
            "unit.g2.language.listening.extended_instructions",
            "lesson.g2.language.listening.extended_instructions",
            LearningDomain.READING,
            InputMode.LISTEN_AND_SELECT,
            "spoken_instruction_detail_choice",
            "requiresAdultLiveListeningInteraction",
            setOf("two_step_first", "two_step_last", "three_step_middle", "before_after", "story_who", "story_where", "story_reason", "select_detail")
        ),
        Case(
            Grade2DigitalMediaSafetyGenerator,
            "roadmap.g2.world.digital.media_safety",
            "unit.g2.world.digital.media_safety",
            "lesson.g2.world.digital.media_safety",
            LearningDomain.WORLD_KNOWLEDGE,
            InputMode.TAP_CHOICE,
            "digital_media_safety_scenario_choice",
            "requiresAdultDevicePractice",
            setOf("device_purpose", "personal_information", "password_safety", "unkind_message", "advertising_choice", "check_information", "screen_balance", "photo_permission")
        ),
        Case(
            Grade2CitizenshipEmotionsConflictsGenerator,
            "roadmap.g2.world.citizenship.emotions_conflicts",
            "unit.g2.world.citizenship.emotions_conflicts",
            "lesson.g2.world.citizenship.emotions_conflicts",
            LearningDomain.WORLD_KNOWLEDGE,
            InputMode.TAP_CHOICE,
            "emotion_conflict_scenario_choice",
            "requiresAdultSocialPractice",
            setOf("name_emotion", "notice_signal", "calm_strategy", "i_message", "take_turns", "fair_solution", "seek_adult_help", "include_others")
        )
    )

    @Test
    fun `all remaining Grade 2 guided sources are deterministic localized and cover their reviewed kinds`() {
        cases.forEach { case ->
            val source = source(case, "guided", 8)
            val first = success(case, source, "guided-${case.generator.generatorId}")
            val replay = success(case, source, "guided-${case.generator.generatorId}")
            assertEquals(first, replay)
            assertEquals(8, first.exercises.size)
            assertEquals(8, first.exercises.map { it.id }.distinct().size)
            assertEquals(case.kinds, first.exercises.mapTo(linkedSetOf()) { it.data.getValue("promptKind") })
            first.exercises.forEach { exercise ->
                assertEquals(case.domain, exercise.domain)
                assertEquals(SkillLevel.SECOND_GRADE, exercise.level)
                assertEquals(case.inputMode, exercise.inputMode)
                assertEquals(case.scope, exercise.data["automatedScope"])
                assertEquals("true", exercise.data[case.adultSupportKey])
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
            }
        }
    }

    @Test
    fun `all remaining checkpoint sources replay and change by stable attempt`() {
        cases.forEach { case ->
            val source = source(case, "checkpoint", 10)
            val first = success(case, source, "attempt-a")
            val replay = success(case, source, "attempt-a")
            val changed = success(case, source, "attempt-b")
            assertEquals(first, replay)
            assertNotEquals(first.seed, changed.seed)
            assertNotEquals(first.exercises, changed.exercises)
            assertEquals(10, first.exercises.size)
            assertEquals(10, first.exercises.map { it.id }.distinct().size)
        }
    }

    @Test
    fun `writing and listening preserve their honest assessment boundaries`() {
        repeat(100) { attempt ->
            val writing = success(cases[0], source(cases[0], "checkpoint", 10), "writing-$attempt")
            writing.exercises.forEach { exercise ->
                assertEquals("true", exercise.data["requiresAdultIndependentTextReview"])
                assertTrue("independentWritingScore" !in exercise.data)
                assertTrue("handwritingScore" !in exercise.data)
                assertTrue("openTextMastered" !in exercise.data)
            }
            val listening = success(cases[1], source(cases[1], "checkpoint", 10), "listening-$attempt")
            listening.exercises.forEach { exercise ->
                assertEquals("true", exercise.data["audioRequired"])
                assertEquals(InputMode.LISTEN_AND_SELECT, exercise.inputMode)
                assertTrue(exercise.prompt.nl.startsWith("Luister"))
                assertTrue(exercise.prompt.fr?.startsWith("Écoute") == true)
                assertTrue(exercise.prompt.en?.startsWith("Listen") == true)
                assertNotEquals(exercise.prompt.nl, exercise.speechText?.nl)
                assertTrue("realWorldInstructionCompleted" !in exercise.data)
            }
        }
    }

    @Test
    fun `digital scenarios protect personal data passwords photos and online safety`() {
        val case = cases[2]
        val seen = linkedSetOf<String>()
        repeat(120) { attempt ->
            success(case, source(case, "checkpoint", 10), "digital-$attempt").exercises.forEach { exercise ->
                seen += exercise.data.getValue("promptKind")
                assertEquals("true", exercise.data["requiresAdultDevicePractice"])
                assertTrue("capturedPassword" !in exercise.data)
                assertTrue("capturedAddress" !in exercise.data)
                assertTrue("uploadedPhoto" !in exercise.data)
                if (exercise.data["promptKind"] in setOf("personal_information", "password_safety", "photo_permission")) {
                    assertTrue(exercise.answer in setOf("ask_adult", "tell_adult", "trusted_adult", "keep_secret", "permission", "respect"))
                }
            }
        }
        assertEquals(case.kinds, seen)
    }

    @Test
    fun `citizenship scenarios require adult help when conflict is unsafe or repeated`() {
        val case = cases[3]
        var adultHelpItems = 0
        repeat(120) { attempt ->
            success(case, source(case, "checkpoint", 10), "social-$attempt").exercises.forEach { exercise ->
                assertEquals("true", exercise.data["requiresAdultSocialPractice"])
                assertTrue("emotionDiagnosis" !in exercise.data)
                assertTrue("realConflictResolved" !in exercise.data)
                if (exercise.data["promptKind"] == "seek_adult_help") {
                    adultHelpItems++
                    assertEquals("adult", exercise.answer)
                }
            }
        }
        assertTrue(adultHelpItems > 0)
    }

    @Test
    fun `metadata and parameters fail closed for all remaining generators`() {
        cases.forEach { case ->
            val result = success(case, source(case, "guided", 8), "metadata")
            result.exercises.forEach { exercise ->
                assertEquals(case.generator.generatorId, exercise.data["generatorId"])
                assertEquals("1", exercise.data["generatorVersion"])
                assertEquals(result.seed.fingerprint, exercise.data["seedFingerprint"])
                assertEquals(case.roadmap, exercise.data["roadmapEntry"])
            }
            val valid = parameters(case, "guided")
            listOf(
                valid + ("grade" to "1"),
                valid + ("roadmap_entry" to "roadmap.unknown"),
                valid + ("phase" to "review"),
                valid + ("editorial_status" to "planned"),
                valid + ("unexpected" to "value")
            ).forEach { invalid -> assertTrue(case.generator.validate(invalid).isNotEmpty()) }
        }
    }

    private fun success(
        case: Case,
        source: LessonActivitySource.Generated,
        attemptId: String
    ): LessonGenerationResult.Success {
        val phase = source.parameters.getValue("phase")
        val result = registry.materialize(
            source,
            LessonGenerationContext(
                profileId = "profile-grade2-remaining",
                unitId = case.unitId,
                lessonId = "${case.lessonBase}.${if (phase == "guided") "practice" else "checkpoint"}",
                sectionId = "section-$phase",
                attemptId = attemptId
            )
        )
        assertTrue("Expected success but received $result", result is LessonGenerationResult.Success)
        return result as LessonGenerationResult.Success
    }

    private fun source(case: Case, phase: String, count: Int) = LessonActivitySource.Generated(
        generatorId = case.generator.generatorId,
        generatorVersion = case.generator.generatorVersion,
        exerciseCount = count,
        seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
        parameters = parameters(case, phase)
    )

    private fun parameters(case: Case, phase: String) = linkedMapOf(
        "grade" to "2",
        "roadmap_entry" to case.roadmap,
        "phase" to phase,
        "editorial_status" to "approved"
    )

    private data class Case(
        val generator: LessonExerciseGenerator,
        val roadmap: String,
        val unitId: String,
        val lessonBase: String,
        val domain: LearningDomain,
        val inputMode: InputMode,
        val scope: String,
        val adultSupportKey: String,
        val kinds: Set<String>
    )
}
