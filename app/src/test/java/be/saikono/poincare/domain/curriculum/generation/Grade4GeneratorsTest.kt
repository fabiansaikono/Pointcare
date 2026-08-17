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

class Grade4GeneratorsTest {
    private val registry = LessonGeneratorRegistry.default()

    private val choiceCases = listOf(
        ChoiceCase(
            Grade4IntegratedComprehensionInteractionGenerator,
            "language.integrated_comprehension_interaction",
            LearningDomain.READING,
            "requiresAdultOralInteraction",
            setOf("main_idea", "inference", "text_structure", "viewpoint", "evidence", "dialogue_turn", "presentation_order", "active_listening", "clarify_question", "summary")
        ),
        ChoiceCase(
            Grade4ScienceInquiryDesignGenerator,
            "world.science.inquiry_design",
            LearningDomain.WORLD_KNOWLEDGE,
            "requiresAdultExperiment",
            setOf("question", "hypothesis", "fair_test", "variable", "observation", "measurement", "conclusion", "repeat", "safety", "design_improve")
        ),
        ChoiceCase(
            Grade4GeographyHistoryGenerator,
            "world.geography_history",
            LearningDomain.WORLD_KNOWLEDGE,
            "requiresAdultMapHistoryPractice",
            setOf("map_scale", "cardinal_direction", "legend", "landscape", "region", "chronology", "source", "cause_change", "past_present", "heritage")
        ),
        ChoiceCase(
            Grade4DigitalSocialSelfRegulationGenerator,
            "world.digital_social_self_regulation",
            LearningDomain.WORLD_KNOWLEDGE,
            "requiresAdultDigitalSocialPractice",
            setOf("password", "privacy", "source_check", "online_kindness", "screen_break", "planning", "distraction", "emotion_pause", "conflict", "help")
        ),
        ChoiceCase(
            Grade4ModernLanguagesInitiationGenerator,
            "language.modern_languages.initiation",
            LearningDomain.READING,
            "requiresAdultOralLanguagePractice",
            setOf("greeting_formal", "introduce_name", "age", "classroom_instruction", "colour", "number", "family", "likes", "question_word", "farewell")
        )
    )

    private val numberCases = listOf(
        NumberCase(
            Grade4PlaceValueOperationsGenerator,
            "math.place_value.operations",
            LearningDomain.MATH,
            setOf("digit_value", "compose", "round_10", "round_100", "add", "subtract", "multiply", "divide", "missing_addend")
        ),
        NumberCase(
            Grade4MetricMeasurementGenerator,
            "geometry.metric_measurement",
            LearningDomain.GEOMETRY,
            setOf("m_to_cm", "cm_to_mm", "km_to_m", "perimeter_rectangle", "missing_side", "scale_distance", "path_total", "length_difference")
        ),
        NumberCase(
            Grade4TemperatureScaleGenerator,
            "geometry.temperature_scale",
            LearningDomain.GEOMETRY,
            setOf("read_temperature", "temperature_rise", "temperature_fall", "difference", "target_after_rise", "degrees_to_freezing", "daily_range", "compare_warmer")
        ),
        NumberCase(
            Grade4StatisticsProblemSolvingGenerator,
            "math.statistics.problem_solving",
            LearningDomain.MATH,
            setOf("read_table", "total", "difference", "missing", "pictogram", "combined", "chart_scale", "multi_step")
        )
    )

    @Test
    fun `all Grade 4 sources replay exactly and change with another stable attempt`() {
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
                    assertEquals(SkillLevel.FOURTH_GRADE, exercise.level)
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
    fun `Grade 4 choice sources are localized unambiguous and cover every reviewed kind`() {
        choiceCases.forEach { case ->
            val seen = linkedSetOf<String>()
            repeat(10) { attempt ->
                success(case.base, source(case.base, "checkpoint", 10), "choice-${case.base.suffix}-$attempt")
                    .exercises.forEach { exercise ->
                        seen += exercise.data.getValue("promptKind")
                        assertEquals(InputMode.TAP_CHOICE, exercise.inputMode)
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
    fun `Grade 4 numeric answers agree with their generation metadata`() {
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
    fun `Grade 4 assessment boundaries remain honest and privacy preserving`() {
        val bySuffix = choiceCases.associateBy { it.base.suffix }
        repeat(25) { attempt ->
            fun exercises(suffix: String) = bySuffix.getValue(suffix).let { case ->
                success(case.base, source(case.base, "checkpoint", 10), "boundary-$suffix-$attempt").exercises
            }

            exercises("language.integrated_comprehension_interaction").forEach {
                assertEquals("false", it.data["openOralMasteryInferred"])
                assertEquals("false", it.data["independentPresentationMastered"])
            }
            exercises("world.science.inquiry_design").forEach {
                assertEquals("false", it.data["realExperimentCompleted"])
                assertEquals("false", it.data["practicalSkillMastered"])
            }
            exercises("world.geography_history").forEach {
                assertEquals("false", it.data["fieldworkCompleted"])
                assertEquals("false", it.data["heritageVisitCompleted"])
            }
            exercises("world.digital_social_self_regulation").forEach {
                assertEquals("false", it.data["realDigitalActionMastered"])
                assertEquals("false", it.data["realSocialBehaviorMastered"])
                assertTrue("capturedPassword" !in it.data)
                assertTrue("capturedAddress" !in it.data)
            }
            exercises("language.modern_languages.initiation").forEach {
                assertEquals("false", it.data["formalFrenchInstruction"])
                assertEquals("false", it.data["oralMasteryInferred"])
            }
        }
    }

    @Test
    fun `all Grade 4 generators reject wrong or expanded contracts`() {
        (choiceCases.map { it.base } + numberCases.map { it.base }).forEach { case ->
            val valid = parameters(case, "guided")
            listOf(
                valid + ("grade" to "3"),
                valid + ("roadmap_entry" to "roadmap.unknown"),
                valid + ("phase" to "review"),
                valid + ("editorial_status" to "planned"),
                valid + ("unexpected" to "value")
            ).forEach { invalid -> assertTrue(case.generator.validate(invalid).isNotEmpty()) }
        }
    }

    private fun expectedAnswer(kind: String, values: List<Int>): Int = when (kind) {
        "digit_value" -> (values[0] / values[1] % 10) * values[1]
        "compose" -> values[0] * 1_000 + values[1] * 100 + values[2] * 10 + values[3]
        "round_10" -> ((values[0] + 5) / 10) * 10
        "round_100" -> ((values[0] + 50) / 100) * 100
        "add", "path_total" -> values[0] + values[1]
        "combined" -> values[0] + values[2]
        "subtract", "length_difference", "difference", "compare_warmer" -> abs(values[0] - values[1])
        "multiply" -> values[0] * values[1]
        "divide" -> values[0] / values[1]
        "missing_addend" -> values[1] - values[0]
        "m_to_cm" -> values[0] * 100
        "cm_to_mm" -> values[0] * 10
        "km_to_m" -> values[0] * 1_000
        "perimeter_rectangle" -> 2 * (values[0] + values[1])
        "missing_side" -> values[1] / 2 - values[0]
        "scale_distance", "pictogram", "chart_scale" -> values[0] * values[1]
        "read_temperature", "read_table" -> values[if (kind == "read_table") 1 else 0]
        "temperature_rise", "temperature_fall", "daily_range" -> abs(values[1] - values[0])
        "target_after_rise" -> values[0] + values[1]
        "degrees_to_freezing" -> values[0]
        "total" -> values.sum()
        "missing" -> values[2] - values[0] - values[1]
        "multi_step" -> values[0] + values[1] - values[2]
        else -> error("Unexpected Grade 4 prompt kind: $kind")
    }

    private fun source(case: BaseCase, phase: String, count: Int) = LessonActivitySource.Generated(
        generatorId = case.generator.generatorId,
        generatorVersion = case.generator.generatorVersion,
        exerciseCount = count,
        seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
        parameters = parameters(case, phase)
    )

    private fun parameters(case: BaseCase, phase: String) = linkedMapOf(
        "grade" to "4",
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
                profileId = "profile-grade4",
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
        val roadmap = "roadmap.g4.$suffix"
        val unitId = "unit.g4.$suffix"
        val lessonBase = "lesson.g4.$suffix"
    }

    private data class ChoiceCase(
        val generator: LessonExerciseGenerator,
        val suffix: String,
        val domain: LearningDomain,
        val adultSupportKey: String,
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
