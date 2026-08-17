package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy

class LessonGeneratorRegistry(
    generators: Collection<LessonExerciseGenerator>
) {
    private val generatorsByKey: Map<GeneratorKey, LessonExerciseGenerator>

    init {
        val duplicateKeys = generators
            .groupingBy { GeneratorKey(it.generatorId, it.generatorVersion) }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateKeys.isEmpty()) {
            "Duplicate lesson generators: ${duplicateKeys.joinToString { "${it.id}@${it.version}" }}"
        }
        generatorsByKey = generators.associateBy { GeneratorKey(it.generatorId, it.generatorVersion) }
    }

    fun supports(generatorId: String, generatorVersion: Int): Boolean =
        GeneratorKey(generatorId, generatorVersion) in generatorsByKey

    fun materialize(
        source: LessonActivitySource.Generated,
        context: LessonGenerationContext
    ): LessonGenerationResult {
        if (source.editorialStatus() != APPROVED_STATUS) {
            return LessonGenerationResult.Rejected(
                code = LessonGenerationRejectionCode.NOT_APPROVED,
                message = "Generator source is not editorially approved.",
                details = listOf("editorial_status=${source.editorialStatus() ?: "<missing>"}")
            )
        }

        val contextIssues = validateContext(source, context)
        if (contextIssues.isNotEmpty()) {
            val code = if (
                source.seedPolicy == LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT &&
                context.attemptId.isNullOrBlank()
            ) {
                LessonGenerationRejectionCode.MISSING_ATTEMPT_ID
            } else {
                LessonGenerationRejectionCode.INVALID_CONTEXT
            }
            return LessonGenerationResult.Rejected(
                code = code,
                message = "Lesson generation context is incomplete.",
                details = contextIssues
            )
        }

        if (source.exerciseCount !in MIN_EXERCISE_COUNT..MAX_EXERCISE_COUNT) {
            return LessonGenerationResult.Rejected(
                code = LessonGenerationRejectionCode.INVALID_PARAMETERS,
                message = "Exercise count is outside the supported runtime range.",
                details = listOf("exerciseCount=${source.exerciseCount}")
            )
        }

        val key = GeneratorKey(source.generatorId, source.generatorVersion)
        val generator = generatorsByKey[key]
            ?: return unknownOrVersionMismatch(source)

        val dutchOnlyExercise = source.parameters[EXERCISE_LOCALES_PARAMETER] == DUTCH_ONLY_LOCALE
        val generatorParameters = source.parameters - EXERCISE_LOCALES_PARAMETER
        val parameterIssues = generator.validate(generatorParameters)
        if (parameterIssues.isNotEmpty()) {
            return LessonGenerationResult.Rejected(
                code = LessonGenerationRejectionCode.INVALID_PARAMETERS,
                message = "Generator parameters are invalid.",
                details = parameterIssues
            )
        }

        val seed = try {
            DeterministicLessonSeed.derive(source, context)
        } catch (error: IllegalArgumentException) {
            return LessonGenerationResult.Rejected(
                code = LessonGenerationRejectionCode.INVALID_CONTEXT,
                message = "Stable seed derivation failed.",
                details = listOf(error.message ?: error::class.java.simpleName)
            )
        } catch (error: IllegalStateException) {
            return LessonGenerationResult.Rejected(
                code = LessonGenerationRejectionCode.MISSING_ATTEMPT_ID,
                message = "Stable seed derivation requires an attempt ID.",
                details = listOf(error.message ?: error::class.java.simpleName)
            )
        }

        val exercises = try {
            generator.generate(
                seed = seed,
                exerciseCount = source.exerciseCount,
                parameters = generatorParameters
            ).map { exercise ->
                val canonicalData = (
                    exercise.data + (
                        "subcategoryId" to (
                            context.canonicalSubcategoryId
                                ?: exercise.data["subcategoryId"]
                                ?: exercise.skill
                            )
                        )
                    ).toMutableMap()
                if (source.parameters["grade"] == "1") {
                    val promptKind = canonicalData["promptKind"]
                        ?: canonicalData["mode"]
                        ?: exercise.skill
                    canonicalData.putIfAbsent(
                        "masteryTag",
                        "g1.${exercise.skill}.${promptKind.replace('_', '.')}"
                    )
                    canonicalData.putIfAbsent(
                        "errorTags",
                        if (exercise.inputMode.name.startsWith("TRACE_")) {
                            "requires_adult_motor_observation"
                        } else {
                            "incorrect.${promptKind.replace('_', '.')}"
                        }
                    )
                    canonicalData.putIfAbsent(
                        "feedback_nl-BE",
                        canonicalData["hint_nl-BE"]
                            ?: "Bekijk de vraag opnieuw en controleer je antwoord met de lesregel."
                    )
                }
                if (dutchOnlyExercise) {
                    canonicalData.keys.filter { key ->
                        key.endsWith("_fr-BE", ignoreCase = true) ||
                            key.endsWith("_en-US", ignoreCase = true)
                    }.forEach(canonicalData::remove)
                    canonicalData["exerciseLocales"] = "nl-BE"
                }
                exercise.copy(
                    domain = context.canonicalDomain ?: exercise.domain,
                    prompt = if (dutchOnlyExercise) {
                        be.saikono.poincare.model.LocalizedText(exercise.prompt.nl)
                    } else exercise.prompt,
                    speechText = if (dutchOnlyExercise) {
                        exercise.speechText?.let { be.saikono.poincare.model.LocalizedText(it.nl) }
                    } else exercise.speechText,
                    translation = if (dutchOnlyExercise) {
                        exercise.translation?.let { be.saikono.poincare.model.LocalizedText(it.nl) }
                    } else exercise.translation,
                    data = canonicalData
                )
            }
        } catch (error: Exception) {
            return LessonGenerationResult.Rejected(
                code = LessonGenerationRejectionCode.GENERATION_FAILED,
                message = "Generator execution failed.",
                details = listOf(error.message ?: error::class.java.simpleName)
            )
        }

        val outputIssues = validateOutput(source, seed, exercises)
        if (outputIssues.isNotEmpty()) {
            return LessonGenerationResult.Rejected(
                code = LessonGenerationRejectionCode.INVALID_OUTPUT,
                message = "Generator output violated the lesson-content contract.",
                details = outputIssues
            )
        }

        return LessonGenerationResult.Success(
            generatorId = source.generatorId,
            generatorVersion = source.generatorVersion,
            seed = seed,
            exercises = exercises
        )
    }

    private fun unknownOrVersionMismatch(
        source: LessonActivitySource.Generated
    ): LessonGenerationResult.Rejected {
        val availableVersions = generatorsByKey.keys
            .filter { it.id == source.generatorId }
            .map { it.version }
            .sorted()
        return if (availableVersions.isEmpty()) {
            LessonGenerationResult.Rejected(
                code = LessonGenerationRejectionCode.UNKNOWN_GENERATOR,
                message = "No lesson generator is registered for ${source.generatorId}."
            )
        } else {
            LessonGenerationResult.Rejected(
                code = LessonGenerationRejectionCode.VERSION_MISMATCH,
                message = "Generator version ${source.generatorVersion} is not registered.",
                details = listOf("availableVersions=${availableVersions.joinToString()}")
            )
        }
    }

    private fun validateContext(
        source: LessonActivitySource.Generated,
        context: LessonGenerationContext
    ): List<String> = buildList {
        if (context.profileId.isBlank()) add("profileId is blank")
        if (context.unitId.isBlank()) add("unitId is blank")
        if (context.lessonId.isBlank()) add("lessonId is blank")
        if (context.sectionId.isBlank()) add("sectionId is blank")
        if (context.canonicalSubcategoryId != null && context.canonicalSubcategoryId.isBlank()) {
            add("canonicalSubcategoryId is blank")
        }
        if (
            source.seedPolicy == LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT &&
            context.attemptId.isNullOrBlank()
        ) {
            add("attemptId is required by STABLE_PER_PROFILE_ATTEMPT")
        }
    }

    private fun validateOutput(
        source: LessonActivitySource.Generated,
        seed: LessonGenerationSeed,
        exercises: List<be.saikono.poincare.model.ExerciseContent>
    ): List<String> = buildList {
        if (exercises.size != source.exerciseCount) {
            add("expected ${source.exerciseCount} exercises but received ${exercises.size}")
        }
        val ids = exercises.map { it.id }
        if (ids.size != ids.distinct().size) add("generated exercise IDs are not unique")
        exercises.forEachIndexed { index, exercise ->
            if (!STABLE_EXERCISE_ID.matches(exercise.id)) {
                add("exercise[$index] has an unstable ID: ${exercise.id}")
            }
            if (exercise.answer.isBlank()) add("exercise[$index] has a blank answer")
            if (exercise.prompt.nl.isBlank()) add("exercise[$index] has blank nl-BE prompt text")
            if (source.parameters[EXERCISE_LOCALES_PARAMETER] != DUTCH_ONLY_LOCALE) {
                if (exercise.prompt.fr.isNullOrBlank()) add("exercise[$index] has blank fr-BE prompt text")
                if (exercise.prompt.en.isNullOrBlank()) add("exercise[$index] has blank en-US prompt text")
            }
            if (exercise.data["generatorId"] != source.generatorId) {
                add("exercise[$index] generatorId metadata does not match")
            }
            if (exercise.data["generatorVersion"] != source.generatorVersion.toString()) {
                add("exercise[$index] generatorVersion metadata does not match")
            }
            if (exercise.data["seedFingerprint"] != seed.fingerprint) {
                add("exercise[$index] seed fingerprint metadata does not match")
            }
            if (exercise.data["subcategoryId"].isNullOrBlank()) {
                add("exercise[$index] canonical subcategory metadata is missing")
            }
            GeneratedExerciseContentValidator.validate(exercise).forEach { issue ->
                add("exercise[$index] $issue")
            }
        }
    }

    companion object {
        const val APPROVED_STATUS: String = "approved"
        private const val MIN_EXERCISE_COUNT: Int = 1
        private const val MAX_EXERCISE_COUNT: Int = 100
        private const val EXERCISE_LOCALES_PARAMETER: String = "exercise_locales"
        private const val DUTCH_ONLY_LOCALE: String = "nl-BE"
        private val STABLE_EXERCISE_ID = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")

        fun default(): LessonGeneratorRegistry = LessonGeneratorRegistry(
            listOf(
                Grade1AdditionSubtractionGenerator,
                Grade1LetterTracingGenerator,
                Grade1NumberSenseGenerator,
                Grade1MeasurementShapesGenerator,
                Grade1EarlyLiteracyGenerator,
                Grade1PhonologyLetterSoundGenerator,
                Grade1TechnicalReadingGenerator,
                Grade1ListeningComprehensionGenerator,
                Grade1SpeakingInteractionGenerator,
                Grade1WritingLettersSentencesGenerator,
                Grade1EnvironmentScienceGenerator,
                Grade1CitizenshipCooperationGenerator,
                Grade1PedestrianSafetyGenerator,
                Grade1MathConceptsGenerator,
                Grade1FrenchBasicsGenerator,
                Grade1AppliedDomainsGenerator,
                Grade1CurriculumCatalogGenerator,
                ReviewedQuestionPoolGenerator,
                DictationLessonGenerator,
                Grade2OperationsExpansionGenerator,
                Grade2MeasurementContextGenerator,
                Grade2StatisticsIntroductionGenerator,
                Grade2IntegratedWorldFoundationsGenerator,
                Grade2TechnicalReadingFluencyGenerator,
                Grade2SpellingRulesGenerator,
                Grade2ShortTextWritingGenerator,
                Grade2ExtendedListeningGenerator,
                Grade2DigitalMediaSafetyGenerator,
                Grade2CitizenshipEmotionsConflictsGenerator,
                Grade2AtomicCurriculumGenerator,
                Grade3ReadingComprehensionInformationGenerator,
                Grade3InformationSkillsLookupGenerator,
                Grade3FunctionalWritingGenerator,
                Grade3StatisticsCompareDataGenerator,
                Grade3MultiplicationDivisionFractionsGenerator,
                Grade3MoneyTimeGenerator,
                Grade3ReadingStrategiesGenerator,
                Grade3WorldTimeSpaceNatureSocietyGenerator,
                Grade3CitizenshipResponsibilityGenerator,
                Grade3ModernLanguagesInitiationGenerator,
                Grade4IntegratedComprehensionInteractionGenerator,
                Grade4PlaceValueOperationsGenerator,
                Grade4MetricMeasurementGenerator,
                Grade4TemperatureScaleGenerator,
                Grade4StatisticsProblemSolvingGenerator,
                Grade4ScienceInquiryDesignGenerator,
                Grade4GeographyHistoryGenerator,
                Grade4DigitalSocialSelfRegulationGenerator,
                Grade4ModernLanguagesInitiationGenerator,
                Grade5ReadingListeningStrategiesGenerator,
                Grade5ListeningEvaluateInformationGenerator,
                Grade5WritingStructureRevisionGenerator,
                Grade5FractionsDecimalsRatiosGenerator,
                Grade5ProblemSolvingModelStrategiesGenerator,
                Grade5ScaleSpeedDistanceTimeGenerator,
                Grade5FrenchIntegratedSkillsGenerator,
                Grade5FrenchGrammarSpellingGenerator,
                Grade5IntegratedWorldGenerator,
                Grade5DigitalInformationProductionGenerator,
                Grade5HealthSocialLearningGenerator,
                Grade6CriticalReadingArgumentsGenerator,
                Grade6WritingStructuredGenresGenerator,
                Grade6SpeakingPresentationArgumentsGenerator,
                Grade6LargeNumbersRatiosPercentagesGenerator,
                Grade6CompoundMeasurementGenerator,
                Grade6StatisticsMeanMedianGenerator,
                Grade6MultiStepProblemSolvingGenerator,
                Grade6FrenchFunctionalSkillsGenerator,
                Grade6FrenchGrammarVocabularyGenerator,
                Grade6IntegratedSourcesModelsGenerator,
                Grade6DigitalCriticalSafeUseGenerator,
                Grade6SocialSelfRegulationGenerator
            )
        )
    }
}
