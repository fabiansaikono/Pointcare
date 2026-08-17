package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonGeneratorRegistryTest {
    private val registry = LessonGeneratorRegistry.default()

    @Test
    fun `default registry supports the exact reviewed Grade 1 to Grade 6 generator versions`() {
        assertTrue(registry.supports(
            Grade1AdditionSubtractionGenerator.generatorId,
            Grade1AdditionSubtractionGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade1NumberSenseGenerator.generatorId,
            Grade1NumberSenseGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade1MeasurementShapesGenerator.generatorId,
            Grade1MeasurementShapesGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade1EarlyLiteracyGenerator.generatorId,
            Grade1EarlyLiteracyGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade1PhonologyLetterSoundGenerator.generatorId,
            Grade1PhonologyLetterSoundGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade1TechnicalReadingGenerator.generatorId,
            Grade1TechnicalReadingGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade1ListeningComprehensionGenerator.generatorId,
            Grade1ListeningComprehensionGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade1SpeakingInteractionGenerator.generatorId,
            Grade1SpeakingInteractionGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade1WritingLettersSentencesGenerator.generatorId,
            Grade1WritingLettersSentencesGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade1EnvironmentScienceGenerator.generatorId,
            Grade1EnvironmentScienceGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade1CitizenshipCooperationGenerator.generatorId,
            Grade1CitizenshipCooperationGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade1PedestrianSafetyGenerator.generatorId,
            Grade1PedestrianSafetyGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade2OperationsExpansionGenerator.generatorId,
            Grade2OperationsExpansionGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade2MeasurementContextGenerator.generatorId,
            Grade2MeasurementContextGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade2StatisticsIntroductionGenerator.generatorId,
            Grade2StatisticsIntroductionGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade2IntegratedWorldFoundationsGenerator.generatorId,
            Grade2IntegratedWorldFoundationsGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade2TechnicalReadingFluencyGenerator.generatorId,
            Grade2TechnicalReadingFluencyGenerator.generatorVersion
        ))
        assertTrue(registry.supports(
            Grade2SpellingRulesGenerator.generatorId,
            Grade2SpellingRulesGenerator.generatorVersion
        ))
        assertTrue(registry.supports(Grade2ShortTextWritingGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade2ExtendedListeningGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade2DigitalMediaSafetyGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade2CitizenshipEmotionsConflictsGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade2AtomicCurriculumGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade3ReadingComprehensionInformationGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade3InformationSkillsLookupGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade3FunctionalWritingGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade3StatisticsCompareDataGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade3MultiplicationDivisionFractionsGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade3MoneyTimeGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade3ReadingStrategiesGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade3WorldTimeSpaceNatureSocietyGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade3CitizenshipResponsibilityGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade3ModernLanguagesInitiationGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade4IntegratedComprehensionInteractionGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade4PlaceValueOperationsGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade4MetricMeasurementGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade4TemperatureScaleGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade4StatisticsProblemSolvingGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade4ScienceInquiryDesignGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade4GeographyHistoryGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade4DigitalSocialSelfRegulationGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade4ModernLanguagesInitiationGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade5ReadingListeningStrategiesGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade5ListeningEvaluateInformationGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade5WritingStructureRevisionGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade5FractionsDecimalsRatiosGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade5ProblemSolvingModelStrategiesGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade5ScaleSpeedDistanceTimeGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade5FrenchIntegratedSkillsGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade5FrenchGrammarSpellingGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade5IntegratedWorldGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade5DigitalInformationProductionGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade5HealthSocialLearningGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade6CriticalReadingArgumentsGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade6WritingStructuredGenresGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade6SpeakingPresentationArgumentsGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade6LargeNumbersRatiosPercentagesGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade6CompoundMeasurementGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade6StatisticsMeanMedianGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade6MultiStepProblemSolvingGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade6FrenchFunctionalSkillsGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade6FrenchGrammarVocabularyGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade6IntegratedSourcesModelsGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade6DigitalCriticalSafeUseGenerator.generatorId, 1))
        assertTrue(registry.supports(Grade6SocialSelfRegulationGenerator.generatorId, 1))
        assertTrue(!registry.supports(Grade1PedestrianSafetyGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade2OperationsExpansionGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade2MeasurementContextGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade2StatisticsIntroductionGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade2IntegratedWorldFoundationsGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade2TechnicalReadingFluencyGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade2SpellingRulesGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade2ShortTextWritingGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade2ExtendedListeningGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade2DigitalMediaSafetyGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade2CitizenshipEmotionsConflictsGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade2AtomicCurriculumGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade3ReadingComprehensionInformationGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade3InformationSkillsLookupGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade3FunctionalWritingGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade3StatisticsCompareDataGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade3MultiplicationDivisionFractionsGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade3MoneyTimeGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade3ReadingStrategiesGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade3WorldTimeSpaceNatureSocietyGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade3CitizenshipResponsibilityGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade3ModernLanguagesInitiationGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade4IntegratedComprehensionInteractionGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade4PlaceValueOperationsGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade4MetricMeasurementGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade4TemperatureScaleGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade4StatisticsProblemSolvingGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade4ScienceInquiryDesignGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade4GeographyHistoryGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade4DigitalSocialSelfRegulationGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade4ModernLanguagesInitiationGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade5ReadingListeningStrategiesGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade5ListeningEvaluateInformationGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade5WritingStructureRevisionGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade5FractionsDecimalsRatiosGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade5ProblemSolvingModelStrategiesGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade5ScaleSpeedDistanceTimeGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade5FrenchIntegratedSkillsGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade5FrenchGrammarSpellingGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade5IntegratedWorldGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade5DigitalInformationProductionGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade5HealthSocialLearningGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade6CriticalReadingArgumentsGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade6WritingStructuredGenresGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade6SpeakingPresentationArgumentsGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade6LargeNumbersRatiosPercentagesGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade6CompoundMeasurementGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade6StatisticsMeanMedianGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade6MultiStepProblemSolvingGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade6FrenchFunctionalSkillsGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade6FrenchGrammarVocabularyGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade6IntegratedSourcesModelsGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade6DigitalCriticalSafeUseGenerator.generatorId, 2))
        assertTrue(!registry.supports(Grade6SocialSelfRegulationGenerator.generatorId, 2))
    }

    @Test
    fun `planned generator contract cannot execute`() {
        val result = registry.materialize(
            source(editorialStatus = "planned"),
            context(attemptId = "attempt-001")
        )

        assertRejected(result, LessonGenerationRejectionCode.NOT_APPROVED)
    }

    @Test
    fun `attempt based source requires a stable attempt id`() {
        val result = registry.materialize(
            source(editorialStatus = "approved"),
            context(attemptId = null)
        )

        assertRejected(result, LessonGenerationRejectionCode.MISSING_ATTEMPT_ID)
    }

    @Test
    fun `unknown generator and unsupported version fail closed`() {
        val unknown = registry.materialize(
            source(
                editorialStatus = "approved",
                generatorId = "generator.unknown"
            ),
            context("attempt-001")
        )
        val wrongVersion = registry.materialize(
            source(
                editorialStatus = "approved",
                generatorVersion = 99
            ),
            context("attempt-001")
        )

        assertRejected(unknown, LessonGenerationRejectionCode.UNKNOWN_GENERATOR)
        assertRejected(wrongVersion, LessonGenerationRejectionCode.VERSION_MISMATCH)
    }

    @Test
    fun `invalid parameters fail before generation`() {
        val result = registry.materialize(
            source(
                editorialStatus = "approved",
                parameters = mapOf(
                    "grade" to "2",
                    "operation" to "division",
                    "phase" to "guided",
                    "editorial_status" to "approved"
                )
            ),
            context("attempt-001")
        )

        assertRejected(result, LessonGenerationRejectionCode.INVALID_PARAMETERS)
    }

    @Test
    fun `grade one and two runtime exercises are Dutch only`() {
        val result = registry.materialize(
            source(
                editorialStatus = "approved",
                parameters = mapOf(
                    "grade" to "1",
                    "operation" to "subtraction",
                    "phase" to "guided",
                    "editorial_status" to "approved",
                    "exercise_locales" to "nl-BE"
                )
            ),
            context("attempt-dutch-only")
        )

        assertTrue(result is LessonGenerationResult.Success)
        val exercises = (result as LessonGenerationResult.Success).exercises
        assertTrue(exercises.isNotEmpty())
        assertTrue(exercises.all { exercise ->
            exercise.prompt.fr == null &&
                exercise.prompt.en == null &&
                exercise.speechText?.fr == null &&
                exercise.speechText?.en == null &&
                exercise.data["exerciseLocales"] == "nl-BE" &&
                exercise.data.keys.none { it.endsWith("_fr-BE") || it.endsWith("_en-US") }
        })
    }

    @Test
    fun `duplicate generator registration is rejected`() {
        val error = try {
            LessonGeneratorRegistry(
                listOf(
                    Grade1AdditionSubtractionGenerator,
                    Grade1AdditionSubtractionGenerator
                )
            )
            null
        } catch (failure: IllegalArgumentException) {
            failure
        }

        assertTrue(error?.message?.contains("Duplicate lesson generators") == true)
    }

    private fun source(
        editorialStatus: String,
        generatorId: String = Grade1AdditionSubtractionGenerator.generatorId,
        generatorVersion: Int = Grade1AdditionSubtractionGenerator.generatorVersion,
        parameters: Map<String, String> = mapOf(
            "grade" to "1",
            "operation" to "subtraction",
            "phase" to "guided",
            "editorial_status" to editorialStatus
        )
    ): LessonActivitySource.Generated = LessonActivitySource.Generated(
        generatorId = generatorId,
        generatorVersion = generatorVersion,
        exerciseCount = 8,
        seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
        parameters = parameters
    )

    private fun context(attemptId: String?): LessonGenerationContext = LessonGenerationContext(
        profileId = "profile-001",
        unitId = "math.addition.g1.foundations",
        lessonId = "lesson.math.subtraction.g1.within20",
        sectionId = "lesson.math.subtraction.g1.within20.guided",
        attemptId = attemptId
    )

    private fun assertRejected(
        result: LessonGenerationResult,
        code: LessonGenerationRejectionCode
    ) {
        assertTrue("Expected rejection but received $result", result is LessonGenerationResult.Rejected)
        assertEquals(code, (result as LessonGenerationResult.Rejected).code)
    }
}
