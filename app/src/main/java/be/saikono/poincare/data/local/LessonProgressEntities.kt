package be.saikono.poincare.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import be.saikono.poincare.model.SyncStatus
import be.saikono.poincare.domain.curriculum.v2.LessonGateStateV2
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import be.saikono.poincare.model.lesson.LessonMasteryLevel
import be.saikono.poincare.model.lesson.LessonProgressState

@Entity(
    tableName = "lesson_progress",
    indices = [
        Index(
            value = ["childProfileId", "lessonId"],
            unique = true,
            name = "index_lesson_progress_child_lesson"
        ),
        Index(value = ["syncId"], unique = true, name = "index_lesson_progress_sync_id"),
        Index(value = ["syncStatus"], name = "index_lesson_progress_sync_status")
    ]
)
data class LessonProgressEntity(
    @PrimaryKey val progressId: String,
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
)

@Entity(
    tableName = "lesson_attempts",
    indices = [
        Index(value = ["progressId"], name = "index_lesson_attempts_progress_id"),
        Index(
            value = ["childProfileId", "lessonId", "sequenceNumber"],
            unique = true,
            name = "index_lesson_attempts_child_lesson_sequence"
        ),
        Index(value = ["syncId"], unique = true, name = "index_lesson_attempts_sync_id"),
        Index(value = ["syncStatus"], name = "index_lesson_attempts_sync_status")
    ]
)
data class LessonAttemptEntity(
    @PrimaryKey val attemptId: String,
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
    val metadataJson: String
)
