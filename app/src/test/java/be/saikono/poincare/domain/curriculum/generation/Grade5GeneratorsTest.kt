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

class Grade5GeneratorsTest {
    private val registry = LessonGeneratorRegistry.default()

    private val choiceCases = listOf(
        ChoiceCase(
            Grade5ReadingListeningStrategiesGenerator,
            "language.reading_listening_strategies",
            LearningDomain.READING,
            "requiresAdultDiscussion",
            InputMode.TAP_CHOICE,
            setOf("summary", "inference", "text_structure", "evidence", "diagram", "source_purpose", "fact_opinion", "conclusion", "compare_sources", "note_taking")
        ),
        ChoiceCase(
            Grade5ListeningEvaluateInformationGenerator,
            "language.listening.evaluate_information",
            LearningDomain.READING,
            "requiresAdultListeningDiscussion",
            InputMode.LISTEN_AND_SELECT,
            setOf("main_message", "detail", "sequence", "condition", "relevant_information", "fact_opinion", "speaker_purpose", "reason_quality", "note_selection", "conclusion")
        ),
        ChoiceCase(
            Grade5WritingStructureRevisionGenerator,
            "language.writing.structure_revision",
            LearningDomain.WRITING,
            "requiresAdultWritingReview",
            InputMode.TAP_CHOICE,
            setOf("introduction", "paragraph_order", "transition", "remove_repetition", "precise_word", "conclusion", "audience", "source_note", "punctuation", "revision_check")
        ),
        ChoiceCase(
            Grade5FrenchIntegratedSkillsGenerator,
            "language.french.integrated_skills",
            LearningDomain.READING,
            "requiresAdultFrenchConversation",
            InputMode.LISTEN_AND_SELECT,
            setOf("greeting", "name", "age", "direction", "shopping", "family", "hobby", "time", "weather", "invitation")
        ),
        ChoiceCase(
            Grade5FrenchGrammarSpellingGenerator,
            "spelling.french.grammar_spelling",
            LearningDomain.WRITING,
            "requiresAdultFrenchWritingReview",
            InputMode.TAP_CHOICE,
            setOf("article_gender", "plural", "etre", "avoir", "aller", "adjective_agreement", "negation", "question", "accent", "word_order")
        ),
        ChoiceCase(
            Grade5IntegratedWorldGenerator,
            "world.integrated_geography_history_science",
            LearningDomain.WORLD_KNOWLEDGE,
            "requiresAdultFieldInquiry",
            InputMode.TAP_CHOICE,
            setOf("map_scale", "climate", "river_settlement", "timeline", "historical_source", "ecosystem", "energy", "fair_test", "cause_effect", "technology_impact")
        ),
        ChoiceCase(
            Grade5DigitalInformationProductionGenerator,
            "world.digital.information_production",
            LearningDomain.WORLD_KNOWLEDGE,
            "requiresAdultDigitalProduction",
            InputMode.TAP_CHOICE,
            setOf("search_query", "source_reliability", "author_date", "privacy", "copyright", "file_organization", "chart_choice", "presentation", "collaboration", "misinformation")
        ),
        ChoiceCase(
            Grade5HealthSocialLearningGenerator,
            "world.health_social_learning",
            LearningDomain.WORLD_KNOWLEDGE,
            "requiresAdultHealthSocialReflection",
            InputMode.TAP_CHOICE,
            setOf("balanced_meal", "sleep", "movement", "stress_plan", "collaboration", "conflict", "assertive", "study_strategy", "reflection", "seek_help")
        )
    )

    private val numberCases = listOf(
        NumberCase(
            Grade5FractionsDecimalsRatiosGenerator,
            "math.fractions_decimals_ratios",
            LearningDomain.MATH,
            setOf("fraction_of", "equivalent_numerator", "equivalent_denominator", "simplify_denominator", "decimal_tenths", "decimal_hundredths", "ratio_missing", "decimal_sum_cents")
        ),
        NumberCase(
            Grade5ProblemSolvingModelStrategiesGenerator,
            "math.problem_solving.model_strategies",
            LearningDomain.MATH,
            setOf("total_then_share", "budget_change", "unit_price", "remaining", "area_cost", "schedule_total", "compare_plans", "equal_share")
        ),
        NumberCase(
            Grade5ScaleSpeedDistanceTimeGenerator,
            "geometry.scale_speed_distance_time",
            LearningDomain.GEOMETRY,
            setOf("map_to_real", "real_to_map", "model_scale", "distance", "time", "speed", "route_total", "remaining_distance")
        )
    )

    @Test
    fun `all Grade 5 sources replay exactly and change with another stable attempt`() {
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
                    assertEquals(SkillLevel.FIFTH_GRADE, exercise.level)
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
    fun `Grade 5 choice sources are localized unambiguous and cover every reviewed kind`() {
        choiceCases.forEach { case ->
            val seen = linkedSetOf<String>()
            repeat(10) { attempt ->
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
    fun `Grade 5 numeric answers agree with their generation metadata`() {
        numberCases.forEach { case ->
            val seen = linkedSetOf<String>()
            repeat(100) { attempt ->
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
    fun `Grade 5 assessment boundaries remain honest and privacy preserving`() {
        val bySuffix = choiceCases.associateBy { it.base.suffix }
        repeat(10) { attempt ->
            fun exercises(suffix: String) = bySuffix.getValue(suffix).let { case ->
                success(case.base, source(case.base, "checkpoint", 10), "boundary-$suffix-$attempt").exercises
            }
            exercises("language.reading_listening_strategies").forEach {
                assertEquals("false", it.data["independentOralSummaryMastered"])
                assertEquals("false", it.data["extendedDiscussionMastered"])
            }
            exercises("language.listening.evaluate_information").forEach {
                assertEquals("true", it.data["audioRequired"])
                assertEquals("false", it.data["oralResponseMastered"])
            }
            exercises("language.writing.structure_revision").forEach {
                assertEquals("false", it.data["fullTextWritingMastered"])
                assertEquals("false", it.data["handwritingMastered"])
            }
            exercises("language.french.integrated_skills").forEach {
                assertEquals("fr-BE", it.data["audioTargetLocale"])
                assertEquals("false", it.data["spontaneousFrenchSpeechMastered"])
            }
            exercises("world.integrated_geography_history_science").forEach {
                assertEquals("false", it.data["fieldworkCompleted"])
                assertEquals("false", it.data["realExperimentCompleted"])
            }
            exercises("world.digital.information_production").forEach {
                assertEquals("false", it.data["realDigitalProductionMastered"])
                assertEquals("false", it.data["capturedPersonalData"])
                assertTrue("capturedPassword" !in it.data)
                assertTrue("capturedAddress" !in it.data)
            }
            exercises("world.health_social_learning").forEach {
                assertEquals("false", it.data["realHealthBehaviorMastered"])
                assertEquals("false", it.data["diagnosisInferred"])
            }
        }
    }

    @Test
    fun `all Grade 5 generators reject wrong or expanded contracts`() {
        (choiceCases.map { it.base } + numberCases.map { it.base }).forEach { case ->
            val valid = parameters(case, "guided")
            listOf(
                valid + ("grade" to "4"),
                valid + ("roadmap_entry" to "roadmap.unknown"),
                valid + ("phase" to "review"),
                valid + ("editorial_status" to "planned"),
                valid + ("unexpected" to "value")
            ).forEach { invalid -> assertTrue(case.generator.validate(invalid).isNotEmpty()) }
        }
    }

    private fun expectedAnswer(kind: String, values: List<Int>): Int = when (kind) {
        "fraction_of" -> values[2] / values[1] * values[0]
        "equivalent_numerator" -> values[0] * values[2]
        "equivalent_denominator" -> values[1] * values[2]
        "simplify_denominator" -> values[1] / (values[0] / values[2])
        "decimal_tenths" -> values[0] * 10 + values[1]
        "decimal_hundredths" -> values[0] * 100 + values[1]
        "ratio_missing" -> values[1] * values[2]
        "decimal_sum_cents" -> values[0] + values[1]
        "total_then_share" -> (values[0] + values[1]) / values[2]
        "budget_change" -> values[0] - values[1] - values[2]
        "unit_price" -> values[0] * values[1]
        "remaining", "remaining_distance" -> values[0] - values[1]
        "area_cost" -> values[0] * values[1] * values[2]
        "schedule_total", "route_total" -> values.sum()
        "compare_plans" -> values[3] - (values[0] + values[1] * values[2])
        "equal_share" -> values[0] / values[1]
        "map_to_real", "model_scale", "distance" -> values[0] * values[1]
        "real_to_map", "time", "speed" -> values[0] / values[1]
        else -> error("Unexpected Grade 5 prompt kind: $kind")
    }

    private fun source(case: BaseCase, phase: String, count: Int) = LessonActivitySource.Generated(
        generatorId = case.generator.generatorId,
        generatorVersion = case.generator.generatorVersion,
        exerciseCount = count,
        seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
        parameters = parameters(case, phase)
    )

    private fun parameters(case: BaseCase, phase: String) = linkedMapOf(
        "grade" to "5",
        "roadmap_entry" to case.roadmap,
        "phase" to phase,
        "editorial_status" to "approved"
    )

    private fun success(
        case: BaseCase,
        source: LessonActivitySource.Generated,
        attemptId: String
    ): LessonGenerationResult.Success {
        val phase = source.parameters.getValue("phase")
        val result = registry.materialize(
            source,
            LessonGenerationContext(
                profileId = "profile-grade5",
                unitId = case.unitId,
                lessonId = "${case.lessonBase}.${if (phase == "guided") "practice" else "checkpoint"}",
                sectionId = "${case.lessonBase}.${if (phase == "guided") "practice.guided" else "checkpoint.checkpoint"}",
                attemptId = attemptId
            )
        )
        assertTrue("Expected success but received $result", result is LessonGenerationResult.Success)
        return result as LessonGenerationResult.Success
    }

    private data class BaseCase(
        val generator: LessonExerciseGenerator,
        val suffix: String,
        val domain: LearningDomain
    ) {
        val roadmap = "roadmap.g5.$suffix"
        val unitId = "unit.g5.$suffix"
        val lessonBase = "lesson.g5.$suffix"
    }

    private data class ChoiceCase(
        val generator: LessonExerciseGenerator,
        val suffix: String,
        val domain: LearningDomain,
        val adultSupportKey: String,
        val inputMode: InputMode,
        val kinds: Set<String>
    ) {
        val base = BaseCase(generator, suffix, domain)
    }

    private data class NumberCase(
        val generator: LessonExerciseGenerator,
        val suffix: String,
        val domain: LearningDomain,
        val kinds: Set<String>
    ) {
        val base = BaseCase(generator, suffix, domain)
    }
}
