package be.saikono.poincare.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import be.saikono.poincare.model.SyncStatus

@Entity(
    tableName = "lesson_stage_progress",
    indices = [
        Index(value = ["childProfileId", "lessonId", "stageId"], unique = true, name = "index_lesson_stage_progress_child_lesson_stage"),
        Index(value = ["syncId"], unique = true, name = "index_lesson_stage_progress_sync_id"),
        Index(value = ["syncStatus"], name = "index_lesson_stage_progress_sync_status")
    ]
)
data class LessonStageProgressEntityV2(
    @PrimaryKey val stageProgressId: String,
    val childProfileId: String,
    val lessonId: String,
    val stageId: String,
    val stageType: LessonStageTypeV2,
    val state: String,
    val diagnosticScorePercent: Int?,
    val startedAtUtc: Long?,
    val completedAtUtc: Long?,
    val updatedAtUtc: Long,
    val contentDefinitionVersion: Int,
    val syncId: String,
    val syncStatus: SyncStatus
)

@Entity(
    tableName = "lesson_assessment_attempts",
    indices = [
        Index(value = ["progressId"], name = "index_lesson_assessment_attempts_progress_id"),
        Index(value = ["childProfileId", "lessonId"], name = "index_lesson_assessment_attempts_child_lesson"),
        Index(value = ["syncId"], unique = true, name = "index_lesson_assessment_attempts_sync_id"),
        Index(value = ["syncStatus"], name = "index_lesson_assessment_attempts_sync_status")
    ]
)
data class LessonAssessmentAttemptEntityV2(
    @PrimaryKey val assessmentAttemptId: String,
    val progressId: String,
    val childProfileId: String,
    val unitId: String,
    val lessonId: String,
    val stageId: String,
    val evidenceSource: String,
    val startedAtUtc: Long,
    val completedAtUtc: Long,
    val correctCount: Int,
    val itemCount: Int,
    val scorePercent: Int,
    val hintsUsed: Int,
    val passed: Boolean,
    val retentionCheck: Boolean,
    val contentVersion: String,
    val syncId: String,
    val syncStatus: SyncStatus,
    val metadataJson: String
)

@Entity(
    tableName = "lesson_question_attempts",
    indices = [
        Index(value = ["assessmentAttemptId"], name = "index_lesson_question_attempts_assessment_id"),
        Index(value = ["childProfileId", "lessonId", "masteryTagsJson"], name = "index_lesson_question_attempts_child_lesson_tags")
    ]
)
data class LessonQuestionAttemptEntityV2(
    @PrimaryKey val questionAttemptId: String,
    val assessmentAttemptId: String,
    val childProfileId: String,
    val lessonId: String,
    val exerciseId: String,
    val itemFingerprint: String,
    val masteryTagsJson: String,
    val correct: Boolean,
    val hintsUsed: Int,
    val durationMs: Long,
    val answeredAtUtc: Long
)

@Entity(
    tableName = "lesson_skill_error_memory",
    indices = [
        Index(value = ["childProfileId", "lessonId", "masteryTag"], unique = true, name = "index_lesson_skill_error_child_lesson_tag"),
        Index(value = ["childProfileId", "nextReviewAtUtc", "resolved"], name = "index_lesson_skill_error_due"),
        Index(value = ["syncId"], unique = true, name = "index_lesson_skill_error_sync_id"),
        Index(value = ["syncStatus"], name = "index_lesson_skill_error_sync_status")
    ]
)
data class LessonSkillErrorMemoryEntityV2(
    @PrimaryKey val memoryId: String,
    val childProfileId: String,
    val lessonId: String,
    val masteryTag: String,
    val wrongCount: Int,
    val consecutiveCorrect: Int,
    val nextReviewAtUtc: Long,
    val resolved: Boolean,
    val lastItemFingerprint: String,
    val updatedAtUtc: Long,
    val syncId: String,
    val syncStatus: SyncStatus
)
