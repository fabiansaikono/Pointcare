package be.saikono.poincare.domain.curriculum

import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val CURRENT_CURRICULUM_SCHEMA_VERSION: Int = 1

/**
 * Immutable, language-neutral curriculum catalogue.
 *
 * User progress never belongs in this model. It is intentionally separate from
 * Room-backed progress records that will be introduced in Step 2B.2.
 */
@Serializable
data class CurriculumCatalog(
    val schemaVersion: Int = CURRENT_CURRICULUM_SCHEMA_VERSION,
    val definitionVersion: Int = 1,
    val units: List<CurriculumUnit>
)

@Serializable
data class CurriculumUnit(
    val id: String,
    val domain: LearningDomain,
    val subcategoryId: String,
    val grades: List<Int>,
    val title: LessonText,
    val description: LessonText? = null,
    val requirement: CurriculumRequirement = CurriculumRequirement.MANDATORY,
    val prerequisites: List<CurriculumPrerequisite> = emptyList(),
    val learningGoals: List<LearningGoalDefinition>,
    val lessons: List<LessonDefinition>,
    val tags: List<String> = emptyList(),
    val definitionVersion: Int = 1
)

@Serializable
data class LearningGoalDefinition(
    val id: String,
    val title: LessonText,
    val description: LessonText? = null,
    val requirement: CurriculumRequirement = CurriculumRequirement.MANDATORY,
    val parentSupport: ParentSupportLevel = ParentSupportLevel.NONE,
    val definitionVersion: Int = 1
)

@Serializable
data class LessonDefinition(
    val id: String,
    val grade: Int,
    val sequence: Int,
    val title: LessonText,
    val summary: LessonText? = null,
    val requirement: CurriculumRequirement = CurriculumRequirement.MANDATORY,
    val availability: LessonAvailabilityPolicy = LessonAvailabilityPolicy.ALWAYS,
    val prerequisites: List<CurriculumPrerequisite> = emptyList(),
    val learningGoalIds: List<String>,
    val parentSupport: ParentSupportLevel = ParentSupportLevel.NONE,
    val estimatedMinutes: Int? = null,
    val mastery: LessonMasteryCriteria = LessonMasteryCriteria(),
    val sections: List<LessonSection>,
    val tags: List<String> = emptyList(),
    val definitionVersion: Int = 1
)

@Serializable
data class LessonSection(
    val id: String,
    val type: LessonSectionType,
    val title: LessonText? = null,
    val content: LessonText? = null,
    val activitySource: LessonActivitySource? = null,
    val required: Boolean = true,
    val parentSupport: ParentSupportLevel = ParentSupportLevel.NONE,
    val estimatedMinutes: Int? = null
)

/**
 * Keeps text shown to the child separate from text sent to speech.
 * Spoken text falls back to the unmodified visible source text and must still
 * pass through the shared application-wide speech normalizer at playback time.
 */
@Serializable
data class LessonText(
    val visible: LocalizedText,
    val spoken: LocalizedText? = null
) {
    fun visibleFor(locale: String): String = visible.get(locale)

    fun spokenFor(locale: String): String = (spoken ?: visible).getRaw(locale)
}

@Serializable
enum class CurriculumRequirement {
    MANDATORY,
    RECOMMENDED,
    OPTIONAL
}

@Serializable
enum class LessonAvailabilityPolicy {
    ALWAYS,
    AFTER_PREREQUISITES,
    PARENT_ASSIGNED
}

@Serializable
enum class ParentSupportLevel {
    NONE,
    RECOMMENDED,
    REQUIRED
}

@Serializable
enum class LessonSectionType {
    EXPLANATION,
    GUIDED_PRACTICE,
    INDEPENDENT_PRACTICE,
    REVIEW,
    CHECKPOINT,
    PRACTICAL_ACTIVITY
}

@Serializable
enum class PrerequisiteTargetType {
    UNIT,
    LESSON,
    LEARNING_GOAL
}

@Serializable
enum class PrerequisiteState {
    COMPLETED,
    MASTERED
}

@Serializable
data class CurriculumPrerequisite(
    val targetType: PrerequisiteTargetType,
    val targetId: String,
    val requiredState: PrerequisiteState = PrerequisiteState.COMPLETED
)

@Serializable
data class LessonMasteryCriteria(
    val minimumCorrectRatio: Double = 0.8,
    val minimumAttempts: Int = 1,
    val maximumHints: Int? = null,
    val requiredSectionIds: List<String> = emptyList()
)

@Serializable
enum class LessonSeedPolicy {
    /** Same profile and lesson always reproduce the same generated set. */
    STABLE_PER_PROFILE_LESSON,

    /** A stable attempt identifier reproduces one attempt without repeating all future attempts. */
    STABLE_PER_PROFILE_ATTEMPT
}

@Serializable
sealed class LessonActivitySource {
    @Serializable
    @SerialName("generated")
    data class Generated(
        val generatorId: String,
        val generatorVersion: Int = 1,
        val exerciseCount: Int,
        val seedPolicy: LessonSeedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_LESSON,
        val parameters: Map<String, String> = emptyMap()
    ) : LessonActivitySource()

    @Serializable
    @SerialName("curated")
    data class Curated(
        val exerciseIds: List<String>
    ) : LessonActivitySource()

    @Serializable
    @SerialName("hybrid")
    data class Hybrid(
        val generatorId: String,
        val generatorVersion: Int = 1,
        val generatedExerciseCount: Int,
        val curatedExerciseIds: List<String>,
        val seedPolicy: LessonSeedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_LESSON,
        val parameters: Map<String, String> = emptyMap()
    ) : LessonActivitySource()

    @Serializable
    @SerialName("practical")
    data class Practical(
        val activityId: String,
        val instructions: LessonText,
        val requiresAdultVerification: Boolean = true
    ) : LessonActivitySource()
}

fun CurriculumCatalog.allLessons(): List<LessonDefinition> = units.flatMap { it.lessons }

fun CurriculumCatalog.findUnit(unitId: String): CurriculumUnit? = units.firstOrNull { it.id == unitId }

fun CurriculumCatalog.findLesson(lessonId: String): LessonDefinition? =
    units.asSequence().flatMap { it.lessons.asSequence() }.firstOrNull { it.id == lessonId }

fun CurriculumCatalog.findLearningGoal(goalId: String): LearningGoalDefinition? =
    units.asSequence().flatMap { it.learningGoals.asSequence() }.firstOrNull { it.id == goalId }
