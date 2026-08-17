package be.saikono.poincare.model.lesson

import be.saikono.poincare.model.SyncStatus
import be.saikono.poincare.domain.curriculum.v2.LessonGateStateV2
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.util.UUID

@Serializable
enum class LessonProgressState {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}

@Serializable
enum class LessonMasteryLevel {
    NOT_ASSESSED,
    DEVELOPING,
    PROFICIENT,
    MASTERED
}

@Serializable
data class LessonProgress(
    val progressId: String,
    val childProfileId: String,
    val unitId: String,
    val lessonId: String,
    val state: LessonProgressState,
    val masteryLevel: LessonMasteryLevel,
    val masteryPercent: Int,
    val bestScorePercent: Int,
    val latestScorePercent: Int,
    val attemptCount: Int,
    val startedAtUtc: Long?,
    val completedAtUtc: Long?,
    val lastActivityAtUtc: Long,
    val updatedAtUtc: Long,
    val syncId: String,
    val syncStatus: SyncStatus,
    val revision: Long,
    val gateStateV2: LessonGateStateV2? = null,
    val currentStageTypeV2: LessonStageTypeV2? = null,
    val passedAtUtc: Long? = null,
    val masteredAtUtc: Long? = null,
    val contentDefinitionVersion: Int? = null,
    val legacyEvidence: Boolean? = null
) {
    val effectiveGateStateV2: LessonGateStateV2
        get() = gateStateV2 ?: when (state) {
            LessonProgressState.COMPLETED -> LessonGateStateV2.PASSED_LEGACY
            LessonProgressState.IN_PROGRESS -> LessonGateStateV2.IN_PROGRESS
            LessonProgressState.NOT_STARTED -> LessonGateStateV2.NOT_STARTED
        }
}

@Serializable
data class LessonAttempt(
    val attemptId: String,
    val progressId: String,
    val childProfileId: String,
    val unitId: String,
    val lessonId: String,
    val sequenceNumber: Int,
    val startedAtUtc: Long,
    val completedAtUtc: Long,
    val scorePercent: Int,
    val correctCount: Int,
    val questionCount: Int,
    val durationMs: Long,
    val hintsUsed: Int,
    val completed: Boolean,
    val masteryAfterPercent: Int,
    val generatorSeed: Long?,
    val contentVersion: String,
    val syncId: String,
    val syncStatus: SyncStatus,
    val metadata: Map<String, String>
)

/**
 * A caller-generated immutable attempt command. attemptId and syncId must be UUIDs and
 * remain stable when the same offline attempt is retried after a crash or sync failure.
 */
data class RecordLessonAttemptRequest(
    val attemptId: String,
    val syncId: String = attemptId,
    val childProfileId: String,
    val unitId: String,
    val lessonId: String,
    val startedAtUtc: Long,
    val completedAtUtc: Long,
    val scorePercent: Int,
    val correctCount: Int,
    val questionCount: Int,
    val durationMs: Long,
    val hintsUsed: Int = 0,
    val completed: Boolean = true,
    val completionThresholdPercent: Int = 70,
    val masteryThresholdPercent: Int = 85,
    val generatorSeed: Long? = null,
    val contentVersion: String,
    val metadata: Map<String, String> = emptyMap()
) {
    fun validate() {
        requireUuid(attemptId, "attemptId")
        requireUuid(syncId, "syncId")
        require(childProfileId.isNotBlank()) { "childProfileId must not be blank" }
        require(unitId.isNotBlank()) { "unitId must not be blank" }
        require(lessonId.isNotBlank()) { "lessonId must not be blank" }
        require(startedAtUtc >= 0L) { "startedAtUtc must be non-negative" }
        require(completedAtUtc >= startedAtUtc) { "completedAtUtc must be at or after startedAtUtc" }
        require(scorePercent in 0..100) { "scorePercent must be in 0..100" }
        require(questionCount >= 0) { "questionCount must be non-negative" }
        require(correctCount in 0..questionCount) { "correctCount must be in 0..questionCount" }
        require(durationMs >= 0L) { "durationMs must be non-negative" }
        require(hintsUsed >= 0) { "hintsUsed must be non-negative" }
        require(completionThresholdPercent in 0..100) {
            "completionThresholdPercent must be in 0..100"
        }
        require(masteryThresholdPercent in completionThresholdPercent..100) {
            "masteryThresholdPercent must be between completionThresholdPercent and 100"
        }
        require(contentVersion.isNotBlank()) { "contentVersion must not be blank" }
    }
}

sealed interface RecordLessonAttemptResult {
    data class Recorded(
        val progress: LessonProgress,
        val attempt: LessonAttempt
    ) : RecordLessonAttemptResult

    data class Duplicate(
        val existingAttempt: LessonAttempt,
        val progress: LessonProgress?
    ) : RecordLessonAttemptResult
}

object LessonProgressIds {
    private const val PROGRESS_NAMESPACE = "poincare.lesson-progress.v1"

    /** Stable across devices, locales and application versions for one child/lesson pair. */
    fun progressId(childProfileId: String, lessonId: String): String {
        require(childProfileId.isNotBlank()) { "childProfileId must not be blank" }
        require(lessonId.isNotBlank()) { "lessonId must not be blank" }
        val source = "$PROGRESS_NAMESPACE|$childProfileId|$lessonId"
        return UUID.nameUUIDFromBytes(source.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}

object LessonProgressCalculator {
    data class Calculation(
        val progress: LessonProgress,
        val attempt: LessonAttempt
    )

    fun apply(
        current: LessonProgress?,
        request: RecordLessonAttemptRequest,
        sequenceNumber: Int
    ): Calculation {
        request.validate()
        require(sequenceNumber >= 1) { "sequenceNumber must be at least 1" }

        val progressId = LessonProgressIds.progressId(request.childProfileId, request.lessonId)
        require(current == null || current.progressId == progressId) {
            "current progress does not belong to this child and lesson"
        }
        require(current == null || current.childProfileId == request.childProfileId) {
            "current progress belongs to a different child"
        }
        require(current == null || current.lessonId == request.lessonId) {
            "current progress belongs to a different lesson"
        }
        require(current == null || current.unitId == request.unitId) {
            "unitId changed for an existing lesson progress record"
        }

        val bestScore = maxOf(current?.bestScorePercent ?: 0, request.scorePercent)
        val masteryPercent = maxOf(current?.masteryPercent ?: 0, request.scorePercent)
        val qualifiesAsCompleted = request.completed && bestScore >= request.completionThresholdPercent
        val state = when {
            qualifiesAsCompleted || current?.state == LessonProgressState.COMPLETED ->
                LessonProgressState.COMPLETED
            else -> LessonProgressState.IN_PROGRESS
        }
        val masteryLevel = when {
            masteryPercent >= request.masteryThresholdPercent -> LessonMasteryLevel.MASTERED
            masteryPercent >= request.completionThresholdPercent -> LessonMasteryLevel.PROFICIENT
            masteryPercent > 0 -> LessonMasteryLevel.DEVELOPING
            else -> LessonMasteryLevel.NOT_ASSESSED
        }
        val startedAt = current?.startedAtUtc?.let { minOf(it, request.startedAtUtc) }
            ?: request.startedAtUtc
        val completedAt = current?.completedAtUtc
            ?: request.completedAtUtc.takeIf { state == LessonProgressState.COMPLETED }
        val revision = (current?.revision ?: 0L) + 1L

        val progress = LessonProgress(
            progressId = progressId,
            childProfileId = request.childProfileId,
            unitId = request.unitId,
            lessonId = request.lessonId,
            state = state,
            masteryLevel = masteryLevel,
            masteryPercent = masteryPercent,
            bestScorePercent = bestScore,
            latestScorePercent = request.scorePercent,
            attemptCount = (current?.attemptCount ?: 0) + 1,
            startedAtUtc = startedAt,
            completedAtUtc = completedAt,
            lastActivityAtUtc = request.completedAtUtc,
            updatedAtUtc = request.completedAtUtc,
            syncId = current?.syncId ?: progressId,
            syncStatus = SyncStatus.PENDING,
            revision = revision,
            gateStateV2 = current?.gateStateV2,
            currentStageTypeV2 = current?.currentStageTypeV2,
            passedAtUtc = current?.passedAtUtc,
            masteredAtUtc = current?.masteredAtUtc,
            contentDefinitionVersion = current?.contentDefinitionVersion,
            legacyEvidence = current?.legacyEvidence
        )
        val attempt = LessonAttempt(
            attemptId = request.attemptId,
            progressId = progressId,
            childProfileId = request.childProfileId,
            unitId = request.unitId,
            lessonId = request.lessonId,
            sequenceNumber = sequenceNumber,
            startedAtUtc = request.startedAtUtc,
            completedAtUtc = request.completedAtUtc,
            scorePercent = request.scorePercent,
            correctCount = request.correctCount,
            questionCount = request.questionCount,
            durationMs = request.durationMs,
            hintsUsed = request.hintsUsed,
            completed = request.completed,
            masteryAfterPercent = masteryPercent,
            generatorSeed = request.generatorSeed,
            contentVersion = request.contentVersion,
            syncId = request.syncId,
            syncStatus = SyncStatus.PENDING,
            metadata = request.metadata.toSortedMap().toMap()
        )
        return Calculation(progress, attempt)
    }
}

private fun requireUuid(value: String, fieldName: String) {
    require(runCatching { UUID.fromString(value) }.isSuccess) { "$fieldName must be a UUID" }
}
