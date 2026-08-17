package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.LearningDomain

/**
 * Runtime identity for one generated lesson section.
 *
 * These values are language-neutral and are never derived from visible text.
 */
data class LessonGenerationContext(
    val profileId: String,
    val unitId: String,
    val lessonId: String,
    val sectionId: String,
    val canonicalDomain: LearningDomain? = null,
    val canonicalSubcategoryId: String? = null,
    val attemptId: String? = null
)

data class LessonGenerationSeed(
    val value: Long,
    val fingerprint: String
)

enum class LessonGenerationRejectionCode {
    NOT_APPROVED,
    UNKNOWN_GENERATOR,
    VERSION_MISMATCH,
    INVALID_CONTEXT,
    MISSING_ATTEMPT_ID,
    INVALID_PARAMETERS,
    GENERATION_FAILED,
    INVALID_OUTPUT
}

sealed interface LessonGenerationResult {
    data class Success(
        val generatorId: String,
        val generatorVersion: Int,
        val seed: LessonGenerationSeed,
        val exercises: List<ExerciseContent>
    ) : LessonGenerationResult

    data class Rejected(
        val code: LessonGenerationRejectionCode,
        val message: String,
        val details: List<String> = emptyList()
    ) : LessonGenerationResult
}

interface LessonExerciseGenerator {
    val generatorId: String
    val generatorVersion: Int

    /**
     * Returns human-readable validation issues. An empty list means the request is supported.
     */
    fun validate(parameters: Map<String, String>): List<String>

    /**
     * Generates exactly [exerciseCount] exercises for the supplied stable seed.
     */
    fun generate(
        seed: LessonGenerationSeed,
        exerciseCount: Int,
        parameters: Map<String, String>
    ): List<ExerciseContent>
}

internal data class GeneratorKey(
    val id: String,
    val version: Int
)

internal fun LessonActivitySource.Generated.editorialStatus(): String? =
    parameters["editorial_status"]
