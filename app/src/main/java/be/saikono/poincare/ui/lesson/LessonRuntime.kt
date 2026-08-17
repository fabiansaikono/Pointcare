package be.saikono.poincare.ui.lesson

import be.saikono.poincare.domain.curriculum.CurriculumCatalog
import be.saikono.poincare.domain.curriculum.CurriculumPrerequisite
import be.saikono.poincare.domain.curriculum.CurriculumSubcategoryRegistry
import be.saikono.poincare.domain.curriculum.CurriculumUnit
import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonAvailabilityPolicy
import be.saikono.poincare.domain.curriculum.LessonDefinition
import be.saikono.poincare.domain.curriculum.LessonSectionType
import be.saikono.poincare.domain.curriculum.LessonText
import be.saikono.poincare.domain.curriculum.PrerequisiteState
import be.saikono.poincare.domain.curriculum.PrerequisiteTargetType
import be.saikono.poincare.domain.curriculum.generation.LessonGenerationContext
import be.saikono.poincare.domain.curriculum.generation.LessonGenerationResult
import be.saikono.poincare.domain.curriculum.generation.LessonGeneratorRegistry
import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.lesson.LessonMasteryLevel
import be.saikono.poincare.model.lesson.LessonProgress
import be.saikono.poincare.model.lesson.LessonProgressState
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.domain.curriculum.v2.LessonDefinitionV2
import be.saikono.poincare.domain.curriculum.v2.LessonExercisePurposeV2
import be.saikono.poincare.domain.curriculum.v2.LessonFeedbackPolicyV2
import be.saikono.poincare.domain.curriculum.v2.LessonHintPolicyV2
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import be.saikono.poincare.domain.curriculum.v2.LessonVisualV2

sealed interface LessonStep {
    val sectionId: String
    val sectionType: LessonSectionType

    data class Explanation(
        override val sectionId: String,
        override val sectionType: LessonSectionType,
        val title: LessonText?,
        val content: LessonText,
        val stageTypeV2: LessonStageTypeV2? = null,
        val stageIdV2: String? = null,
        val phaseSectionIdV2: String? = null,
        val visualsV2: List<LessonVisualV2> = emptyList()
    ) : LessonStep

    data class Exercise(
        override val sectionId: String,
        override val sectionType: LessonSectionType,
        val exercise: ExerciseContent,
        val stageTypeV2: LessonStageTypeV2? = null,
        val stageIdV2: String? = null,
        val phaseSectionIdV2: String? = null,
        val exerciseSetIdV2: String? = null,
        val purposeV2: LessonExercisePurposeV2? = null,
        val masteryTagsV2: List<String> = emptyList(),
        val hintPolicyV2: LessonHintPolicyV2? = null,
        val feedbackPolicyV2: LessonFeedbackPolicyV2? = null,
        val wrongAnswersAllowedV2: Int = 1
    ) : LessonStep

    data class Practical(
        override val sectionId: String,
        override val sectionType: LessonSectionType,
        val activityId: String,
        val title: LessonText?,
        val instructions: LessonText,
        val requiresAdultVerification: Boolean,
        val stageTypeV2: LessonStageTypeV2? = null,
        val stageIdV2: String? = null,
        val phaseSectionIdV2: String? = null
    ) : LessonStep
}

internal fun LessonStep.stageTypeV2(): LessonStageTypeV2? = when (this) {
    is LessonStep.Explanation -> stageTypeV2
    is LessonStep.Exercise -> stageTypeV2
    is LessonStep.Practical -> stageTypeV2
}

internal fun LessonStep.stageIdV2(): String? = when (this) {
    is LessonStep.Explanation -> stageIdV2
    is LessonStep.Exercise -> stageIdV2
    is LessonStep.Practical -> stageIdV2
}

internal fun LessonStep.phaseSectionIdV2(): String? = when (this) {
    is LessonStep.Explanation -> phaseSectionIdV2
    is LessonStep.Exercise -> phaseSectionIdV2
    is LessonStep.Practical -> phaseSectionIdV2
}

data class ResolvedLessonSession(
    val unit: CurriculumUnit,
    val lesson: LessonDefinition,
    val attemptId: String,
    val contentVersion: String,
    val steps: List<LessonStep>,
    val generatorSeeds: List<Long>,
    val seedFingerprints: List<String>,
    val lessonV2: LessonDefinitionV2? = null
)

sealed interface LessonResolutionResult {
    data class Success(val session: ResolvedLessonSession) : LessonResolutionResult
    data class Rejected(val message: String, val details: List<String> = emptyList()) : LessonResolutionResult
}

/**
 * Resolves immutable catalogue sections into one replayable local attempt.
 * It never records progress; persistence happens only after the complete attempt is submitted.
 */
class LessonRuntime(
    private val contentRepository: ContentRepository,
    private val generatorRegistry: LessonGeneratorRegistry = LessonGeneratorRegistry.default()
) {
    suspend fun resolve(
        catalog: CurriculumCatalog,
        unit: CurriculumUnit,
        lesson: LessonDefinition,
        profileId: String,
        attemptId: String
    ): LessonResolutionResult {
        if (profileId.isBlank() || attemptId.isBlank()) {
            return LessonResolutionResult.Rejected("Profile and attempt identity are required.")
        }
        if (lesson !in unit.lessons) {
            return LessonResolutionResult.Rejected("Lesson does not belong to the selected unit.")
        }

        val steps = mutableListOf<LessonStep>()
        val seeds = mutableListOf<Long>()
        val fingerprints = mutableListOf<String>()
        val tile = CurriculumSubcategoryRegistry.tileFor(unit, lesson)

        for (section in lesson.sections) {
            section.content?.let { content ->
                steps += LessonStep.Explanation(
                    sectionId = section.id,
                    sectionType = section.type,
                    title = section.title,
                    content = content
                )
            }

            when (val source = section.activitySource) {
                null -> Unit
                is LessonActivitySource.Generated -> {
                    when (val generated = generatorRegistry.materialize(
                        source = source,
                        context = LessonGenerationContext(
                            profileId = profileId,
                            unitId = unit.id,
                            lessonId = lesson.id,
                            sectionId = section.id,
                            canonicalDomain = tile.domain,
                            canonicalSubcategoryId = tile.id,
                            attemptId = attemptId
                        )
                    )) {
                        is LessonGenerationResult.Success -> {
                            seeds += generated.seed.value
                            fingerprints += generated.seed.fingerprint
                            generated.exercises.forEach { exercise ->
                                steps += LessonStep.Exercise(section.id, section.type, exercise)
                            }
                        }
                        is LessonGenerationResult.Rejected -> return LessonResolutionResult.Rejected(
                            message = generated.message,
                            details = generated.details
                        )
                    }
                }
                is LessonActivitySource.Curated -> {
                    val resolved = source.exerciseIds.map { id -> id to contentRepository.getExerciseById(id) }
                    val missing = resolved.filter { it.second == null }.map { it.first }
                    if (missing.isNotEmpty()) {
                        return LessonResolutionResult.Rejected(
                            message = "Curated lesson content is incomplete.",
                            details = missing
                        )
                    }
                    resolved.forEach { (_, exercise) ->
                        steps += LessonStep.Exercise(section.id, section.type, requireNotNull(exercise))
                    }
                }
                is LessonActivitySource.Hybrid -> {
                    val generatedSource = LessonActivitySource.Generated(
                        generatorId = source.generatorId,
                        generatorVersion = source.generatorVersion,
                        exerciseCount = source.generatedExerciseCount,
                        seedPolicy = source.seedPolicy,
                        parameters = source.parameters
                    )
                    when (val generated = generatorRegistry.materialize(
                        source = generatedSource,
                        context = LessonGenerationContext(
                            profileId = profileId,
                            unitId = unit.id,
                            lessonId = lesson.id,
                            sectionId = section.id,
                            canonicalDomain = tile.domain,
                            canonicalSubcategoryId = tile.id,
                            attemptId = attemptId
                        )
                    )) {
                        is LessonGenerationResult.Success -> {
                            seeds += generated.seed.value
                            fingerprints += generated.seed.fingerprint
                            generated.exercises.forEach { exercise ->
                                steps += LessonStep.Exercise(section.id, section.type, exercise)
                            }
                        }
                        is LessonGenerationResult.Rejected -> return LessonResolutionResult.Rejected(
                            message = generated.message,
                            details = generated.details
                        )
                    }
                    val curated = source.curatedExerciseIds.map { id -> id to contentRepository.getExerciseById(id) }
                    val missing = curated.filter { it.second == null }.map { it.first }
                    if (missing.isNotEmpty()) {
                        return LessonResolutionResult.Rejected(
                            message = "Hybrid lesson content is incomplete.",
                            details = missing
                        )
                    }
                    curated.forEach { (_, exercise) ->
                        steps += LessonStep.Exercise(section.id, section.type, requireNotNull(exercise))
                    }
                }
                is LessonActivitySource.Practical -> {
                    steps += LessonStep.Practical(
                        sectionId = section.id,
                        sectionType = section.type,
                        activityId = source.activityId,
                        title = section.title,
                        instructions = source.instructions,
                        requiresAdultVerification = source.requiresAdultVerification
                    )
                }
            }
        }

        if (steps.isEmpty()) {
            return LessonResolutionResult.Rejected("This lesson has no runnable sections.")
        }
        val unsupported = steps.filterIsInstance<LessonStep.Exercise>()
            .filter { it.exercise.inputMode !in SUPPORTED_INPUT_MODES }
            .map { "${it.exercise.id}:${it.exercise.inputMode}" }
        if (unsupported.isNotEmpty()) {
            return LessonResolutionResult.Rejected(
                message = "This lesson uses an unsupported input mode.",
                details = unsupported
            )
        }
        return LessonResolutionResult.Success(
            ResolvedLessonSession(
                unit = unit,
                lesson = lesson,
                attemptId = attemptId,
                contentVersion = "catalog-${catalog.definitionVersion}/lesson-${lesson.definitionVersion}",
                steps = steps,
                generatorSeeds = seeds,
                seedFingerprints = fingerprints
            )
        )
    }

    private companion object {
        val SUPPORTED_INPUT_MODES = setOf(
            InputMode.TAP_CHOICE,
            InputMode.LISTEN_AND_SELECT,
            InputMode.CUSTOM_NUMBER_PAD
        )
    }
}

object LessonAvailabilityEvaluator {
    fun isAvailable(
        catalog: CurriculumCatalog,
        unit: CurriculumUnit,
        lesson: LessonDefinition,
        progressByLessonId: Map<String, LessonProgress>,
        activeGrades: Set<Int>? = null
    ): Boolean {
        if (lesson.availability == LessonAvailabilityPolicy.PARENT_ASSIGNED) return false
        return (unit.prerequisites + lesson.prerequisites).all { prerequisite ->
            prerequisiteSatisfied(catalog, prerequisite, progressByLessonId, activeGrades)
        }
    }

    private fun prerequisiteSatisfied(
        catalog: CurriculumCatalog,
        prerequisite: CurriculumPrerequisite,
        progressByLessonId: Map<String, LessonProgress>,
        activeGrades: Set<Int>?
    ): Boolean {
        val selectedGrades = activeGrades?.takeIf { it.isNotEmpty() }
        if (selectedGrades != null && prerequisiteGrades(catalog, prerequisite).none { it in selectedGrades }) {
            return true
        }
        val relatedProgress = when (prerequisite.targetType) {
            PrerequisiteTargetType.LESSON -> listOfNotNull(progressByLessonId[prerequisite.targetId])
            PrerequisiteTargetType.UNIT -> catalog.units
                .firstOrNull { it.id == prerequisite.targetId }
                ?.lessons
                .orEmpty()
                .mapNotNull { progressByLessonId[it.id] }
            PrerequisiteTargetType.LEARNING_GOAL -> catalog.units
                .flatMap { it.lessons }
                .filter { prerequisite.targetId in it.learningGoalIds }
                .mapNotNull { progressByLessonId[it.id] }
        }
        val expectedCount = when (prerequisite.targetType) {
            PrerequisiteTargetType.LESSON -> 1
            PrerequisiteTargetType.UNIT -> catalog.units
                .firstOrNull { it.id == prerequisite.targetId }
                ?.lessons
                ?.size
                ?: 0
            PrerequisiteTargetType.LEARNING_GOAL -> catalog.units
                .flatMap { it.lessons }
                .count { prerequisite.targetId in it.learningGoalIds }
        }
        if (expectedCount == 0 || relatedProgress.size != expectedCount) return false
        return relatedProgress.all { progress ->
            when (prerequisite.requiredState) {
                PrerequisiteState.COMPLETED -> progress.state == LessonProgressState.COMPLETED
                PrerequisiteState.MASTERED -> progress.masteryLevel == LessonMasteryLevel.MASTERED
            }
        }
    }

    private fun prerequisiteGrades(
        catalog: CurriculumCatalog,
        prerequisite: CurriculumPrerequisite
    ): Set<Int> = when (prerequisite.targetType) {
        PrerequisiteTargetType.LESSON -> catalog.units
            .asSequence()
            .flatMap { it.lessons.asSequence() }
            .firstOrNull { it.id == prerequisite.targetId }
            ?.let { setOf(it.grade) }
            .orEmpty()
            .toSet()
        PrerequisiteTargetType.UNIT -> catalog.units
            .firstOrNull { it.id == prerequisite.targetId }
            ?.grades
            .orEmpty()
            .toSet()
        PrerequisiteTargetType.LEARNING_GOAL -> catalog.units
            .asSequence()
            .flatMap { it.lessons.asSequence() }
            .filter { prerequisite.targetId in it.learningGoalIds }
            .map { it.grade }
            .toSet()
    }
}
