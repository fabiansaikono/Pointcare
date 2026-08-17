package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.localizedAnswer
import be.saikono.poincare.model.localizedOptionSpeech
import be.saikono.poincare.model.localizedOptions
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade6GeneratorsTest {
    private val registry = LessonGeneratorRegistry.default()

    private val choiceCases = listOf(
        ChoiceCase(
            Grade6CriticalReadingArgumentsGenerator,
            "language.critical_reading.arguments",
            LearningDomain.READING,
            "requiresAdultCriticalDiscussion",
            InputMode.TAP_CHOICE,
            setOf("claim", "evidence", "fact_opinion", "counterargument", "bias", "source_reliability", "inference", "summary", "correlation", "argument_structure"),
        ),
        ChoiceCase(
            Grade6WritingStructuredGenresGenerator,
            "language.writing.structured_genres",
            LearningDomain.READING,
            "requiresAdultWritingReview",
            InputMode.TAP_CHOICE,
            setOf("genre_report", "argument_intro", "paragraph", "linking_word", "formal_tone", "revision_logic", "source_credit", "conclusion", "editing", "checklist"),
        ),
        ChoiceCase(
            Grade6SpeakingPresentationArgumentsGenerator,
            "language.speaking.presentation_arguments",
            LearningDomain.READING,
            "requiresAdultOralObservation",
            InputMode.TAP_CHOICE,
            setOf("opening", "structure", "evidence", "active_listening", "clarification", "counterpoint", "visual_aid", "audience", "closing", "feedback"),
        ),
        ChoiceCase(
            Grade6FrenchFunctionalSkillsGenerator,
            "language.french.functional_skills",
            LearningDomain.READING,
            "requiresAdultFrenchConversation",
            InputMode.LISTEN_AND_SELECT,
            setOf("directions", "appointment", "shopping", "preference", "invitation", "problem", "travel", "past_event", "future_plan", "message"),
        ),
        ChoiceCase(
            Grade6FrenchGrammarVocabularyGenerator,
            "spelling.french.grammar_vocabulary",
            LearningDomain.WRITING,
            "requiresAdultFrenchReview",
            InputMode.TAP_CHOICE,
            setOf("present_etre", "present_avoir", "regular_er", "article", "adjective_agreement", "negation", "question", "preposition", "past", "vocabulary_context"),
        ),
        ChoiceCase(
            Grade6IntegratedSourcesModelsGenerator,
            "world.integrated.sources_models",
            LearningDomain.WORLD_KNOWLEDGE,
            "requiresAdultInquiry",
            InputMode.TAP_CHOICE,
            setOf("primary_source", "source_context", "map_model", "system", "fair_test", "model_prediction", "historical_cause", "geographic_scale", "evidence_conclusion", "sustainability_tradeoff"),
        ),
        ChoiceCase(
            Grade6DigitalCriticalSafeUseGenerator,
            "world.digital.critical_safe_use",
            LearningDomain.WORLD_KNOWLEDGE,
            "requiresAdultDigitalSupervision",
            InputMode.TAP_CHOICE,
            setOf("source_check", "phishing", "privacy", "consent", "copyright", "algorithm", "cyberbullying", "ai_output", "screen_balance", "digital_footprint"),
        ),
        ChoiceCase(
            Grade6SocialSelfRegulationGenerator,
            "world.social_self_regulation",
            LearningDomain.WORLD_KNOWLEDGE,
            "requiresAdultSocialObservation",
            InputMode.TAP_CHOICE,
            setOf("planning", "feedback", "group_roles", "conflict", "emotion_pause", "help_seeking", "inclusion", "study_strategy", "responsibility", "wellbeing_balance"),
        ),
    )

    private val numberCases = listOf(
        NumberCase(
            Grade6LargeNumbersRatiosPercentagesGenerator,
            "math.large_numbers_ratios_percentages",
            LearningDomain.MATH,
            setOf("place_value", "round_thousand", "percent_of", "percentage_rate", "ratio_missing", "fraction_percent", "decimal_percent", "large_operation"),
        ),
        NumberCase(
            Grade6CompoundMeasurementGenerator,
            "geometry.scale.compound_measurement",
            LearningDomain.GEOMETRY,
            setOf("map_scale", "model_scale", "rectangle_perimeter", "rectangle_area", "cuboid_volume", "length_conversion", "distance_speed", "missing_side"),
        ),
        NumberCase(
            Grade6StatisticsMeanMedianGenerator,
            "math.statistics.mean_median",
            LearningDomain.MATH,
            setOf("mean", "median_odd", "median_even", "range", "total_from_mean", "missing_from_mean", "pictogram_total", "mean_difference"),
        ),
        NumberCase(
            Grade6MultiStepProblemSolvingGenerator,
            "math.problem_solving.multi_step",
            LearningDomain.MATH,
            setOf("discount", "recipe_scale", "journey_remaining", "rate_total", "budget_percent", "tile_area", "combined_groups", "compare_plans"),
        ),
    )

    @Test
    fun `all Grade 6 sources replay exactly and change with another stable attempt`() {
        (choiceCases.map { it.base } + numberCases.map { it.base }).forEach { case ->
            listOf("guided" to 8, "checkpoint" to 10).forEach { (phase, count) ->
                val source = source(case, phase, count)
                val first = success(case, source, "attempt-a")
                val replay = success(case, source, "attempt-a")
                val changed = success(case, source, "attempt-b")
                assertEquals(first, replay)
                assertNotEquals(first.seed, changed.seed)
                assertNotEquals(first.exercises, changed.exercises)
                assertEquals(count, first.exercises.size)
                assertEquals(count, first.exercises.map { it.id }.distinct().size)
                first.exercises.forEach { exercise ->
                    assertEquals(SkillLevel.SIXTH_GRADE, exercise.level)
                    assertEquals(case.domain, exercise.domain)
                    assertEquals(case.generator.generatorId, exercise.data["generatorId"])
                    assertEquals("1", exercise.data["generatorVersion"])
                    assertEquals(case.roadmap, exercise.data["roadmapEntry"])
                    assertEquals(first.seed.fingerprint, exercise.data["seedFingerprint"])
                    assertTrue(exercise.prompt.nl.isNotBlank())
                    assertTrue(exercise.prompt.fr?.isNotBlank() == true)
                    assertTrue(exercise.prompt.en?.isNotBlank() == true)
                    assertTrue(exercise.speechText?.nl?.isNotBlank() == true)
                    assertTrue(exercise.speechText?.fr?.isNotBlank() == true)
                    assertTrue(exercise.speechText?.en?.isNotBlank() == true)
                }
            }
        }
    }

    @Test
    fun `Grade 6 choice sources are localized unambiguous and cover every reviewed kind`() {
        choiceCases.forEach { case ->
            val seen = linkedSetOf<String>()
            repeat(12) { attempt ->
                success(case.base, source(case.base, "checkpoint", 10), "choice-${case.base.suffix}-$attempt")
                    .exercises.forEach { exercise ->
                        seen += exercise.data.getValue("promptKind")
                        assertEquals(case.inputMode, exercise.inputMode)
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
            assertEquals(case.kinds, seen)
        }
    }

    @Test
    fun `Grade 6 numeric answers agree with generation metadata`() {
        numberCases.forEach { case ->
            val seen = linkedSetOf<String>()
            repeat(120) { attempt ->
                val phase = if (attempt % 2 == 0) "guided" else "checkpoint"
                success(case.base, source(case.base, phase, if (phase == "guided") 8 else 10), "numeric-$attempt")
                    .exercises.forEach { exercise ->
                        assertEquals(InputMode.CUSTOM_NUMBER_PAD, exercise.inputMode)
                        val kind = exercise.data.getValue("promptKind")
                        val values = exercise.data.getValue("values").split(',').map(String::toInt)
                        val answer = exercise.answer.toInt()
                        seen += kind
                        assertTrue(answer >= 0)
                        assertEquals("$kind/$values", expectedAnswer(kind, values), answer)
                    }
            }
            assertEquals(case.kinds, seen)
        }
    }

    @Test
    fun `Grade 6 assessment boundaries remain honest private and safe`() {
        val bySuffix = choiceCases.associateBy { it.base.suffix }
        repeat(10) { attempt ->
            fun exercises(suffix: String) = bySuffix.getValue(suffix).let { case ->
                success(case.base, source(case.base, "checkpoint", 10), "boundary-$suffix-$attempt").exercises
            }
            exercises("language.critical_reading.arguments").forEach {
                assertEquals("false", it.data["independentExtendedReadingMastered"])
                assertEquals("false", it.data["openArgumentationMastered"])
            }
            exercises("language.writing.structured_genres").forEach {
                assertEquals("false", it.data["independentExtendedWritingMastered"])
                assertEquals("false", it.data["handwritingMastered"])
            }
            exercises("language.speaking.presentation_arguments").forEach {
                assertEquals("false", it.data["spontaneousSpeechMastered"])
                assertEquals("false", it.data["presentationCompleted"])
            }
            exercises("language.french.functional_skills").forEach {
                assertEquals("fr-BE", it.data["audioTargetLocale"])
                assertEquals("false", it.data["spontaneousFrenchSpeechMastered"])
            }
            exercises("world.integrated.sources_models").forEach {
                assertEquals("false", it.data["realInquiryCompleted"])
                assertEquals("false", it.data["experimentCompleted"])
            }
            exercises("world.digital.critical_safe_use").forEach {
                assertEquals("false", it.data["collectsPersonalData"])
                assertEquals("false", it.data["collectsCredentials"])
                assertEquals("false", it.data["collectsBrowsingData"])
                assertEquals("false", it.data["realAccountRequired"])
            }
            exercises("world.social_self_regulation").forEach {
                assertEquals("false", it.data["diagnosisMade"])
                assertEquals("false", it.data["healthScoreAssigned"])
                assertEquals("false", it.data["realSocialMasteryInferred"])
            }
        }
    }

    @Test
    fun `all Grade 6 generators reject wrong or expanded contracts`() {
        (choiceCases.map { it.base } + numberCases.map { it.base }).forEach { case ->
            val valid = parameters(case, "guided")
            listOf(
                valid + ("grade" to "5"),
                valid + ("roadmap_entry" to "roadmap.unknown"),
                valid + ("phase" to "review"),
                valid + ("editorial_status" to "planned"),
                valid + ("unexpected" to "value"),
            ).forEach { invalid -> assertTrue(case.generator.validate(invalid).isNotEmpty()) }
        }
    }

    private fun expectedAnswer(kind: String, values: List<Int>): Int = when (kind) {
        "place_value" -> values[1] * values[2]
        "round_thousand" -> ((values[0] + 500) / 1_000) * 1_000
        "percent_of" -> values[0] * values[1] / 100
        "percentage_rate" -> values[0] * 100 / values[1]
        "ratio_missing" -> values[1] * values[2]
        "fraction_percent" -> values[0] * 100 / values[1]
        "decimal_percent" -> values[0]
        "large_operation" -> values[0] / values[1]
        "map_scale", "model_scale", "rectangle_area", "distance_speed" -> values[0] * values[1]
        "rectangle_perimeter" -> 2 * (values[0] + values[1])
        "cuboid_volume" -> values[0] * values[1] * values[2]
        "length_conversion" -> values[0] * 100 + values[1]
        "missing_side" -> values[0] / 2 - values[1]
        "mean" -> values.sum() / values.size
        "median_odd" -> values.sorted()[values.size / 2]
        "median_even" -> values.sorted().let { (it[1] + it[2]) / 2 }
        "range" -> values.max() - values.min()
        "total_from_mean" -> values[0] * values[1]
        "missing_from_mean" -> values[2] * 3 - values[0] - values[1]
        "pictogram_total" -> values[0] * (values[1] + values[2])
        "mean_difference" -> abs(values[1] - values[0])
        "discount" -> values[0] * (100 - values[1]) / 100
        "recipe_scale" -> values[0] * values[2] / values[1]
        "journey_remaining" -> values[0] - values[1] - values[2]
        "rate_total" -> values[0] * values[1] + values[2]
        "budget_percent" -> values[0] * (100 - values[1]) / 100
        "tile_area" -> values[0] * values[1] * values[2]
        "combined_groups" -> values[0] * values[1] + values[2] * values[3]
        "compare_plans" -> abs(
            (values[1] + values[0] * values[2]) -
                (values[3] + values[0] * values[4]),
        )
        else -> error("Unexpected Grade 6 prompt kind: $kind")
    }

    private fun source(case: BaseCase, phase: String, count: Int) = LessonActivitySource.Generated(
        generatorId = case.generator.generatorId,
        generatorVersion = case.generator.generatorVersion,
        exerciseCount = count,
        seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
        parameters = parameters(case, phase),
    )

    private fun parameters(case: BaseCase, phase: String) = linkedMapOf(
        "grade" to "6",
        "roadmap_entry" to case.roadmap,
        "phase" to phase,
        "editorial_status" to "approved",
    )

    private fun success(
        case: BaseCase,
        source: LessonActivitySource.Generated,
        attemptId: String,
    ): LessonGenerationResult.Success {
        val phase = source.parameters.getValue("phase")
        val result = registry.materialize(
            source,
            LessonGenerationContext(
                profileId = "profile-grade6",
                unitId = case.unitId,
                lessonId = "${case.lessonBase}.${if (phase == "guided") "practice" else "checkpoint"}",
                sectionId = "${case.lessonBase}.${if (phase == "guided") "practice.guided" else "checkpoint.checkpoint"}",
                attemptId = attemptId,
            ),
        )
        assertTrue("Expected success but received $result", result is LessonGenerationResult.Success)
        return result as LessonGenerationResult.Success
    }

    private data class BaseCase(
        val generator: LessonExerciseGenerator,
        val suffix: String,
        val domain: LearningDomain,
    ) {
        val roadmap = "roadmap.g6.$suffix"
        val unitId = "unit.g6.$suffix"
        val lessonBase = "lesson.g6.$suffix"
    }

    private data class ChoiceCase(
        val generator: LessonExerciseGenerator,
        val suffix: String,
        val domain: LearningDomain,
        val adultSupportKey: String,
        val inputMode: InputMode,
        val kinds: Set<String>,
    ) {
        val base = BaseCase(generator, suffix, domain)
    }

    private data class NumberCase(
        val generator: LessonExerciseGenerator,
        val suffix: String,
        val domain: LearningDomain,
        val kinds: Set<String>,
    ) {
        val base = BaseCase(generator, suffix, domain)
    }
}
