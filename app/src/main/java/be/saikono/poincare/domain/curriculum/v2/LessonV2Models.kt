package be.saikono.poincare.domain.curriculum.v2

import kotlinx.serialization.Serializable

const val CURRENT_LESSON_CATALOG_SCHEMA_VERSION: Int = 2
const val DEFAULT_LESSON_PHASE_SECTION_ID: String = "section-a"
const val WRONG_ANSWERS_ALLOWED_METADATA_KEY: String = "wrong answers allowed"
private val LEGACY_LESSON_PHASE_SECTION_ORDER: List<LessonStageTypeV2> = listOf(
    LessonStageTypeV2.PRACTISE,
    LessonStageTypeV2.APPLY_DEMONSTRATE,
    LessonStageTypeV2.MASTERY_TEST
)
val REQUIRED_LESSON_LOCALES: Set<String> = linkedSetOf("nl-BE", "fr-BE", "en-US")

@Serializable
data class LessonCatalogV2(
    val schemaVersion: Int = CURRENT_LESSON_CATALOG_SCHEMA_VERSION,
    val definitionVersion: Int,
    val lessons: List<LessonDefinitionV2>
)

@Serializable
data class LessonDefinitionV2(
    val id: String,
    val unitId: String,
    val curriculumCategoryId: String,
    val curriculumSubcategoryId: String,
    val grade: Int,
    val sequence: Int,
    val title: LessonTextV2,
    val summary: LessonTextV2,
    val description: LessonTextV2,
    val pedagogicalLogic: LessonTextV2,
    val defaultLocale: String = "nl-BE",
    val translationStatus: Map<String, LessonLocaleTranslationStatusV2>,
    val contentFields: List<LessonContentFieldV2>,
    val legacyRouting: LegacyRoutingV2,
    val learningGoalIds: List<String>,
    val masteryTags: List<String>,
    val prerequisiteLessonIds: List<String> = emptyList(),
    val checkpoint: Boolean = false,
    val publicationStatus: LessonPublicationStatusV2 = LessonPublicationStatusV2.ACTIVE,
    val visuals: List<LessonVisualV2>,
    val stages: List<LessonStageV2>,
    val gatePolicy: LessonGatePolicyV2,
    val errorRepetitionPolicy: ErrorRepetitionPolicyV2,
    val definitionVersion: Int = 1
)

@Serializable
enum class LessonPublicationStatusV2 { ACTIVE, DRAFT_REVIEW_REQUIRED }

@Serializable
data class LessonLocaleTranslationStatusV2(
    val id: String,
    val status: LessonTranslationStatusV2,
    val comment: String = ""
)

@Serializable
enum class LessonTranslationStatusV2 { DEFAULT, COMPLETE, NEEDS_REVIEW, MISSING }

@Serializable
data class LessonContentFieldV2(
    val id: String,
    val ownerId: String,
    val path: String,
    val translations: List<LessonFieldTranslationV2>
)

@Serializable
data class LessonFieldTranslationV2(
    val id: String,
    val locale: String,
    val status: LessonTranslationStatusV2,
    val comment: String = "",
    val fallbackLocale: String? = null
)

@Serializable
data class LessonTextV2(
    val visible: Map<String, String>,
    val spoken: Map<String, String>? = null
) {
    fun visibleFor(locale: String): String = localizedValue(visible, locale)
    fun spokenFor(locale: String): String = localizedValue(spoken ?: visible, locale)

    private fun localizedValue(values: Map<String, String>, locale: String): String =
        values[locale]
            ?: values[locale.replace('_', '-')]
            ?: values[locale.substringBefore('-')]
            ?: values["nl-BE"]
            ?: values.values.firstOrNull()
            .orEmpty()
}

@Serializable
data class LegacyRoutingV2(
    val domain: LessonDomainV2,
    val subcategoryId: String,
    val legacyLessonIds: List<String> = emptyList()
)

@Serializable
enum class LessonDomainV2 { MATH, GEOMETRY, READING, WRITING, WORLD_KNOWLEDGE, PUZZLE }

@Serializable
data class LessonVisualV2(
    val id: String,
    val kind: LessonVisualKindV2,
    val representationId: String? = null,
    val assetPathSvg: String? = null,
    val assetPathPng: String? = null,
    val palettePolicy: LessonPalettePolicyV2? = null,
    val transparentBackground: Boolean = false,
    val altText: Map<String, String>,
    val labels: List<LessonTextV2> = emptyList(),
    val licenseId: String? = null
)

@Serializable
enum class LessonVisualKindV2 {
    MATH_REPRESENTATION,
    GEOMETRY_DIAGRAM,
    MEASUREMENT_DIAGRAM,
    WORLD_BICOLOR_ILLUSTRATION,
    PHONICS_DIAGRAM,
    THEORY_ICON,
    GENERIC_DIAGRAM
}

@Serializable
enum class LessonPalettePolicyV2 { THEME, PLANET_GOLD_WHITE_ONLY, POINCARE_TRICOLOUR }

@Serializable
data class LessonStageV2(
    val id: String,
    val type: LessonStageTypeV2,
    val sequence: Int,
    val seriesIndex: Int? = null,
    val sectionId: String? = null,
    val sectionTitle: LessonTextV2? = null,
    val title: LessonTextV2? = null,
    val required: Boolean,
    val estimatedMinutes: Int? = null,
    val completionPolicy: StageCompletionPolicyV2? = null,
    val contentBlocks: List<LessonContentBlockV2>,
    val exerciseSets: List<LessonExerciseSetV2>
)

@Serializable
enum class LessonStageTypeV2 {
    THEORY,
    EXERCISES,
    MASTERY_TEST,

    // Kept so installed versions can still deserialize older v2 drafts and evidence.
    ACQUIRE_UNDERSTAND,
    PRACTISE,
    APPLY_DEMONSTRATE
}

data class LessonPhaseSectionV2(
    val id: String,
    val title: LessonTextV2?,
    val stages: List<LessonStageV2>
)

/**
 * Returns the practice/apply/test cycles after the shared explanation.
 *
 * The original four-stage pilot catalog did not persist a section ID. It is
 * treated as one backwards-compatible section so existing content and drafts
 * remain readable. Multi-section lessons must persist an ID on every stage.
 */
fun LessonDefinitionV2.phaseSections(): List<LessonPhaseSectionV2> {
    val summaryTestId = summaryTestStage()?.id
    val tail = stages.sortedBy { it.sequence }
        .dropWhile { it.type.isTheory() }
        .filterNot { it.id == summaryTestId }
    if (tail.isEmpty()) return emptyList()
    return tail.chunked(LEGACY_LESSON_PHASE_SECTION_ORDER.size).mapIndexed { index, sectionStages ->
        LessonPhaseSectionV2(
            id = sectionStages.firstNotNullOfOrNull { it.sectionId }
                ?: if (index == 0) DEFAULT_LESSON_PHASE_SECTION_ID else "section-${index + 1}",
            title = sectionStages.firstNotNullOfOrNull { it.sectionTitle },
            stages = sectionStages
        )
    }
}

/** The optional theory part in the current catalogue, with legacy v2 support. */
fun LessonDefinitionV2.theoryStage(): LessonStageV2? = stages
    .sortedBy { it.sequence }
    .firstOrNull { it.type in setOf(LessonStageTypeV2.THEORY, LessonStageTypeV2.ACQUIRE_UNDERSTAND) }

/** Ordered, resumable exercise series. Practice and application are one visible part. */
fun LessonDefinitionV2.exerciseSeries(): List<LessonStageV2> {
    val ordered = stages.sortedBy { it.sequence }
    val current = ordered.filter { it.type == LessonStageTypeV2.EXERCISES }
    if (current.isNotEmpty()) return current.sortedWith(
        compareBy<LessonStageV2> { it.seriesIndex ?: Int.MAX_VALUE }.thenBy { it.sequence }
    )

    // Older v2 content remains readable while its practice/apply stages are migrated.
    return ordered.filter {
        it.type in setOf(LessonStageTypeV2.PRACTISE, LessonStageTypeV2.APPLY_DEMONSTRATE)
    }
}

/** The single final test; only this part may unlock the next lesson. */
fun LessonDefinitionV2.finalTestStage(): LessonStageV2? = stages
    .sortedBy { it.sequence }
    .lastOrNull { it.type == LessonStageTypeV2.MASTERY_TEST }

fun LessonStageTypeV2.isTheory(): Boolean = this in setOf(
    LessonStageTypeV2.THEORY,
    LessonStageTypeV2.ACQUIRE_UNDERSTAND
)

fun LessonStageTypeV2.isExercises(): Boolean = this in setOf(
    LessonStageTypeV2.EXERCISES,
    LessonStageTypeV2.PRACTISE,
    LessonStageTypeV2.APPLY_DEMONSTRATE
)

/**
 * A lesson with more than three practice series ends with one unsectioned,
 * cumulative mastery test. It is deliberately kept outside the repeated
 * practise/apply/test series.
 */
fun LessonDefinitionV2.summaryTestStage(): LessonStageV2? {
    val tail = stages.sortedBy { it.sequence }
        .dropWhile { it.type.isTheory() }
    val candidate = tail.lastOrNull() ?: return null
    val seriesStageCount = tail.size - 1
    val legacyCycleSize = 3
    val seriesCount = seriesStageCount / legacyCycleSize
    return candidate.takeIf {
        seriesCount > 3 &&
            seriesStageCount % legacyCycleSize == 0 &&
            it.type == LessonStageTypeV2.MASTERY_TEST &&
            it.sectionId == null
    }
}

@Serializable
enum class StageCompletionPolicyV2 {
    VIEWED_REQUIRED_BLOCKS,
    COMPLETED_REQUIRED_SETS,
    ASSESSMENT_RESULT
}

@Serializable
data class LessonContentBlockV2(
    val id: String,
    val pageId: String,
    val pageSequence: Int,
    val type: LessonContentBlockTypeV2,
    val title: LessonTextV2? = null,
    val body: LessonTextV2? = null,
    val terms: List<LessonVocabularyTermV2> = emptyList(),
    val visualIds: List<String> = emptyList(),
    val contentFieldIds: Map<String, String> = emptyMap(),
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class LessonContentBlockTypeV2 {
    LEARNING_INTENT,
    VOCABULARY,
    CHILD_FRIENDLY_DEFINITION,
    HOW_IT_WORKS,
    CORRECT_RESULT_RULE,
    WORKED_EXAMPLE,
    NON_EXAMPLE,
    VISUAL_MODEL,
    SELF_CHECK,
    SUMMARY,
    PRACTICAL_INSTRUCTION,
    RUBRIC,
    UITLEG_OUDERS
}

@Serializable
data class LessonVocabularyTermV2(
    val id: String,
    val term: LessonTextV2,
    val definition: LessonTextV2,
    val example: LessonTextV2? = null,
    val nonExample: LessonTextV2? = null,
    val contentFieldIds: Map<String, String> = emptyMap()
)

@Serializable
data class LessonExerciseSetV2(
    val id: String,
    val exerciseSetId: String,
    val lessonId: String,
    val stageId: String,
    val title: LessonTextV2,
    val sequence: Int,
    val sourceMode: LessonSourceModeV2,
    val origin: LessonExerciseOriginV2,
    val validationStatus: LessonValidationStatusV2,
    val publicationStatus: LessonPublicationStatusV2,
    val technicalExecution: Boolean = true,
    val purpose: LessonExercisePurposeV2,
    val itemCount: Int,
    val curatedExerciseIds: List<String> = emptyList(),
    val generatorId: String? = null,
    val generatorVersion: String = "v2",
    val generatorImplementationVersion: Int? = null,
    val seedPolicy: LessonSeedPolicyV2? = null,
    val parameters: Map<String, String> = emptyMap(),
    val masteryTags: List<String>,
    val representationIds: List<String> = emptyList(),
    val hintPolicy: LessonHintPolicyV2 = LessonHintPolicyV2.NONE,
    val feedbackPolicy: LessonFeedbackPolicyV2 = LessonFeedbackPolicyV2.IMMEDIATE_MINIMAL,
    val injectErrorMemory: Boolean = false,
    val questionBlueprints: List<LessonQuestionBlueprintV2> = emptyList(),
    val editorialItems: List<LessonEditorialQuestionV2> = emptyList()
)

/** Number of wrong responses accepted before the right answer is revealed. */
fun LessonExerciseSetV2.wrongAnswersAllowed(): Int =
    parameters[WRONG_ANSWERS_ALLOWED_METADATA_KEY]
        ?.toIntOrNull()
        ?.coerceIn(1, 2)
        ?: when (purpose) {
            LessonExercisePurposeV2.GUIDED,
            LessonExercisePurposeV2.FADING_SUPPORT,
            LessonExercisePurposeV2.INDEPENDENT_PRACTICE -> 2
            else -> 1
        }

/**
 * The reviewable source behind a generated runtime question.
 *
 * Runtime generation remains authoritative in the child app. Keeping the
 * editorial item beside its stable blueprint lets Curriculum Room review the
 * actual lesson-bound prompt, answer logic and translation state without
 * turning a technically generated question into a hand-edited runtime item.
 */
@Serializable
data class LessonEditorialQuestionV2(
    val id: String,
    val lessonId: String,
    val stageId: String,
    val exerciseSetId: String,
    val type: String,
    val question: String,
    val translations: Map<String, LessonEditorialQuestionTranslationV2>,
    val generated: Boolean = true,
    val automaticGenerationEnabled: Boolean = true,
    val generatorVersion: String = "v2",
    val origin: LessonExerciseOriginV2,
    val validationStatus: LessonValidationStatusV2,
    val publicationStatus: LessonPublicationStatusV2,
    val translationStatus: Map<String, LessonLocaleTranslationStatusV2>,
    val editorComments: Map<String, String> = emptyMap(),
    val answers: List<LessonEditorialAnswerV2> = emptyList(),
    val acceptedAnswers: List<LessonEditorialAcceptedAnswerV2> = emptyList(),
    val explanation: String = "",
    val contentFieldIds: Map<String, String> = emptyMap()
)

@Serializable
data class LessonEditorialQuestionTranslationV2(
    val question: String,
    val explanation: String = ""
)

@Serializable
data class LessonEditorialAnswerV2(
    val id: String,
    val value: String,
    val correct: Boolean,
    val displays: Map<String, String>,
    val speaks: Map<String, String> = emptyMap(),
    val translationStatus: Map<String, LessonLocaleTranslationStatusV2> = emptyMap(),
    val contentFieldId: String? = null
)

@Serializable
data class LessonEditorialAcceptedAnswerV2(
    val id: String,
    val value: String = "",
    val displays: Map<String, String>,
    val halfCredit: Boolean = false,
    val oneCharacterTolerance: Boolean = false,
    val translationStatus: Map<String, LessonLocaleTranslationStatusV2> = emptyMap(),
    val contentFieldId: String? = null
)

@Serializable
data class LessonQuestionBlueprintV2(
    val id: String,
    val lessonId: String,
    val stageId: String,
    val exerciseSetId: String,
    val generatorVersion: String = "v2",
    val origin: LessonExerciseOriginV2,
    val validationStatus: LessonValidationStatusV2,
    val publicationStatus: LessonPublicationStatusV2,
    val answerOptionIds: List<String> = emptyList()
)

@Serializable
enum class LessonExerciseOriginV2 { GENERATED, FIXED, MANUAL }

@Serializable
enum class LessonValidationStatusV2 { DRAFT, NEEDS_REVIEW, VALIDATED }

@Serializable
enum class LessonSourceModeV2 { CURATED, GENERATED }

@Serializable
enum class LessonExercisePurposeV2 {
    GUIDED,
    FADING_SUPPORT,
    INDEPENDENT_PRACTICE,
    NEAR_TRANSFER,
    FAR_TRANSFER,
    ERROR_REVIEW,
    MASTERY_TEST
}

@Serializable
enum class LessonSeedPolicyV2 { STABLE_PER_PROFILE_LESSON, STABLE_PER_PROFILE_ATTEMPT }

@Serializable
enum class LessonHintPolicyV2 { FULL, FADING, TIERED, NONE }

@Serializable
enum class LessonFeedbackPolicyV2 {
    IMMEDIATE_EXPLANATORY,
    IMMEDIATE_MINIMAL,
    AFTER_SET,
    AFTER_TEST
}

@Serializable
data class LessonGatePolicyV2(
    val evidenceSource: LessonGateEvidenceSourceV2,
    val minimumCorrectRatio: Double,
    val minimumIndependentItems: Int,
    val maximumHints: Int,
    val requiredMasteryTags: List<String> = emptyList(),
    val criticalTagMinimumCorrect: Int = 0,
    val unlockOnPass: Boolean,
    val retentionCheckRequiredForMastered: Boolean = true
)

@Serializable
enum class LessonGateEvidenceSourceV2 { MASTERY_TEST_ONLY }

@Serializable
data class ErrorRepetitionPolicyV2(
    val enabled: Boolean,
    val repeatConceptNotExactItem: Boolean,
    val maxInjectedShare: Double,
    val reviewIntervalsDays: List<Int>,
    val resolveAfterConsecutiveCorrect: Int = 2
)
