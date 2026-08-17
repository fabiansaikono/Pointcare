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

class Grade3GeneratorsTest {
    private val registry = LessonGeneratorRegistry.default()

    private val choiceCases = listOf(
        ChoiceCase(Grade3ReadingComprehensionInformationGenerator, "language.reading_comprehension.information", LearningDomain.READING, "requiresAdultReadingDiscussion", setOf("main_idea", "explicit_detail", "heading", "sequence", "cause_effect", "table", "inference", "compare", "context_word", "text_purpose")),
        ChoiceCase(Grade3InformationSkillsLookupGenerator, "language.information_skills.lookup", LearningDomain.READING, "requiresAdultSourcePractice", setOf("alphabetical_order", "guide_words", "keyword", "source_author", "cross_check", "safe_search", "index", "contents", "caption", "date_relevance")),
        ChoiceCase(Grade3FunctionalWritingGenerator, "language.writing.functional_texts", LearningDomain.READING, "requiresAdultFunctionalWritingReview", setOf("subject_line", "greeting", "form_field", "instruction_order", "report_fact", "closing", "audience", "list_format", "revise_ambiguity", "required_field")),
        ChoiceCase(Grade3ReadingStrategiesGenerator, "language.reading_strategies.summarise_context", LearningDomain.READING, "requiresAdultStrategyExplanation", setOf("summary", "keyword", "context", "scan", "predict", "verify", "sequence_words", "question_before", "fact_opinion", "reread")),
        ChoiceCase(Grade3WorldTimeSpaceNatureSocietyGenerator, "world.time_space_nature_society", LearningDomain.WORLD_KNOWLEDGE, "requiresAdultFieldObservation", setOf("timeline", "direction", "map_legend", "habitat", "materials", "public_service", "seasons", "life_cycle", "recycling", "local_service")),
        ChoiceCase(Grade3CitizenshipResponsibilityGenerator, "world.citizenship.responsibility", LearningDomain.WORLD_KNOWLEDGE, "requiresAdultSocialPractice", setOf("responsibility", "listen", "fair_share", "rule_reason", "inclusion", "seek_help", "apology", "turn_taking", "shared_decision", "property")),
        ChoiceCase(Grade3ModernLanguagesInitiationGenerator, "language.modern_languages.initiation", LearningDomain.READING, "requiresAdultOralLanguagePractice", setOf("greeting", "thanks", "number", "colour", "classroom", "introduction", "goodbye", "please", "one", "red"))
    )

    private val numberCases = listOf(
        NumberCase(Grade3StatisticsCompareDataGenerator, "math.statistics.compare_data", LearningDomain.MATH, setOf("read_value", "difference", "total", "missing", "combined", "increase")),
        NumberCase(Grade3MultiplicationDivisionFractionsGenerator, "math.multiplication_division_fractions", LearningDomain.MATH, setOf("multiply", "divide", "missing_factor", "equal_groups", "equal_sharing", "numerator", "denominator", "fraction_of_set")),
        NumberCase(Grade3MoneyTimeGenerator, "geometry.money_time", LearningDomain.GEOMETRY, setOf("money_total", "change", "euros_to_cents", "cents_to_euros", "duration", "minutes_after", "weeks_days", "calendar_later"))
    )

    @Test
    fun `all Grade 3 sources replay exactly and change with another stable attempt`() {
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
                    assertEquals(SkillLevel.THIRD_GRADE, exercise.level)
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
    fun `Grade 3 choice sources are localized unambiguous and cover every reviewed kind`() {
        choiceCases.forEach { case ->
            val seen = linkedSetOf<String>()
            repeat(10) { attempt ->
                val generated = success(case.base, source(case.base, "guided", 8), "choice-${case.base.suffix}-$attempt").exercises
                generated.forEach { exercise ->
                    seen += exercise.data.getValue("promptKind")
                    assertEquals(InputMode.TAP_CHOICE, exercise.inputMode)
                    assertEquals("true", exercise.data[case.adultSupportKey])
                    assertTrue(exercise.answer.matches(Regex("^[a-z0-9_]+$")))
                    listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                        val options = exercise.localizedOptions(locale)
                        assertEquals(3, options.size)
                        assertEquals(3, options.distinct().size)
                        assertEquals(1, options.count { it == exercise.localizedAnswer(locale) })
                        options.forEach { option -> assertTrue(exercise.localizedOptionSpeech(option, locale).isNotBlank()) }
                    }
                }
            }
            assertEquals(case.kinds, seen)
        }
    }

    @Test
    fun `Grade 3 numeric answers agree with their generation metadata`() {
        numberCases.forEach { case ->
            val seen = linkedSetOf<String>()
            repeat(80) { attempt ->
                val phase = if (attempt % 2 == 0) "guided" else "checkpoint"
                success(case.base, source(case.base, phase, if (phase == "guided") 8 else 10), "numeric-$attempt")
                    .exercises.forEach { exercise ->
                        assertEquals(InputMode.CUSTOM_NUMBER_PAD, exercise.inputMode)
                        val kind = exercise.data.getValue("promptKind")
                        val values = exercise.data.getValue("values").split(',').map(String::toInt)
                        val answer = exercise.answer.toInt()
                        seen += kind
                        assertTrue(answer >= 0)
                        assertEquals(expectedAnswer(kind, values), answer)
                    }
            }
            assertEquals(case.kinds, seen)
        }
    }

    @Test
    fun `Grade 3 assessment boundaries remain honest and privacy preserving`() {
        val bySuffix = choiceCases.associateBy { it.base.suffix }
        repeat(40) { attempt ->
            fun exercises(suffix: String) = bySuffix.getValue(suffix).let { case ->
                success(case.base, source(case.base, "checkpoint", 10), "boundary-$suffix-$attempt").exercises
            }

            exercises("language.information_skills.lookup").forEach {
                assertTrue("capturedSearch" !in it.data)
                assertTrue("capturedAddress" !in it.data)
                assertTrue("sourceQualityMastered" !in it.data)
            }
            exercises("language.writing.functional_texts").forEach {
                assertTrue("independentWritingScore" !in it.data)
                assertTrue("handwritingScore" !in it.data)
                assertTrue("openTextMastered" !in it.data)
            }
            exercises("language.reading_strategies.summarise_context").forEach {
                assertTrue("oralExplanationMastered" !in it.data)
            }
            exercises("world.time_space_nature_society").forEach {
                assertTrue("fieldObservationCompleted" !in it.data)
            }
            exercises("world.citizenship.responsibility").forEach {
                assertTrue("realCooperationMastered" !in it.data)
            }
            exercises("language.modern_languages.initiation").forEach {
                assertEquals("false", it.data["formalFrenchInstruction"])
                assertEquals("false", it.data["oralMasteryInferred"])
            }
        }
    }

    @Test
    fun `all Grade 3 generators reject wrong or expanded contracts`() {
        (choiceCases.map { it.base } + numberCases.map { it.base }).forEach { case ->
            val valid = parameters(case, "guided")
            listOf(
                valid + ("grade" to "2"),
                valid + ("roadmap_entry" to "roadmap.unknown"),
                valid + ("phase" to "review"),
                valid + ("editorial_status" to "planned"),
                valid + ("unexpected" to "value")
            ).forEach { invalid -> assertTrue(case.generator.validate(invalid).isNotEmpty()) }
        }
    }

    private fun expectedAnswer(kind: String, values: List<Int>): Int = when (kind) {
        "read_value" -> values[1]
        "difference" -> abs(values[0] - values[1])
        "total" -> values.sum()
        "missing" -> values[2] - values[0] - values[1]
        "combined" -> values[0] + values[2]
        "increase" -> values[1] - values[0]
        "multiply", "equal_groups" -> values[0] * values[1]
        "divide", "equal_sharing" -> values[0] / values[1]
        "missing_factor" -> values[1] / values[0]
        "numerator" -> values[0]
        "denominator" -> values[1]
        "fraction_of_set" -> values[2] / values[1]
        "money_total" -> values[0] + values[1]
        "change" -> values[1] - values[0]
        "euros_to_cents" -> values[0] * 100
        "cents_to_euros" -> values[0] / 100
        "duration" -> (values[2] - values[0]) * 60 + values[3] - values[1]
        "minutes_after" -> (values[0] + values[1]) % 60
        "weeks_days" -> values[0] * 7
        "calendar_later" -> values[0] + values[1]
        else -> error("Unexpected Grade 3 prompt kind: $kind")
    }

    private fun source(case: BaseCase, phase: String, count: Int) = LessonActivitySource.Generated(
        generatorId = case.generator.generatorId,
        generatorVersion = case.generator.generatorVersion,
        exerciseCount = count,
        seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
        parameters = parameters(case, phase)
    )

    private fun parameters(case: BaseCase, phase: String) = linkedMapOf(
        "grade" to "3",
        "roadmap_entry" to case.roadmap,
        "phase" to phase,
        "editorial_status" to "approved"
    )

    private fun success(case: BaseCase, source: LessonActivitySource.Generated, attemptId: String): LessonGenerationResult.Success {
        val phase = source.parameters.getValue("phase")
        val result = registry.materialize(
            source,
            LessonGenerationContext(
                profileId = "profile-grade3",
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
        val roadmap = "roadmap.g3.$suffix"
        val unitId = "unit.g3.$suffix"
        val lessonBase = "lesson.g3.$suffix"
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
