package be.saikono.poincare.model.lesson

import be.saikono.poincare.domain.curriculum.v2.ErrorRepetitionPolicyV2
import be.saikono.poincare.domain.curriculum.v2.LessonEvidenceSourceV2
import be.saikono.poincare.domain.curriculum.v2.LessonGateDecisionV2
import be.saikono.poincare.domain.curriculum.v2.LessonGatePolicyV2
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import java.nio.charset.StandardCharsets
import java.util.UUID

data class RecordLessonStageProgressRequestV2(
    val childProfileId: String,
    val unitId: String,
    val lessonId: String,
    val stageId: String,
    val stageType: LessonStageTypeV2,
    val completed: Boolean,
    val diagnosticScorePercent: Int?,
    val timestampUtc: Long,
    val contentDefinitionVersion: Int
)

data class LessonQuestionEvidenceV2(
    val questionAttemptId: String,
    val exerciseId: String,
    val itemFingerprint: String,
    val masteryTags: List<String>,
    val correct: Boolean,
    val hintsUsed: Int,
    val durationMs: Long,
    val answeredAtUtc: Long
)

data class RecordLessonAssessmentRequestV2(
    val assessmentAttemptId: String,
    val syncId: String = assessmentAttemptId,
    val childProfileId: String,
    val unitId: String,
    val lessonId: String,
    val stageId: String,
    val source: LessonEvidenceSourceV2,
    val startedAtUtc: Long,
    val completedAtUtc: Long,
    val hintsUsed: Int,
    val questions: List<LessonQuestionEvidenceV2>,
    val contentVersion: String,
    val contentDefinitionVersion: Int,
    val gatePolicy: LessonGatePolicyV2,
    val errorPolicy: ErrorRepetitionPolicyV2,
    val explicitAdultAssessmentValid: Boolean = false,
    val adultAssessmentAllowed: Boolean = false,
    val retentionCheck: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
) {
    fun validate() {
        requireUuidV2(assessmentAttemptId, "assessmentAttemptId")
        requireUuidV2(syncId, "syncId")
        require(childProfileId.isNotBlank() && unitId.isNotBlank() && lessonId.isNotBlank())
        require(stageId.isNotBlank())
        require(completedAtUtc >= startedAtUtc)
        require(hintsUsed >= 0)
        require(questions.isNotEmpty())
        require(questions.map { it.questionAttemptId }.distinct().size == questions.size)
        require(questions.all { it.masteryTags.isNotEmpty() && it.hintsUsed >= 0 && it.durationMs >= 0 })
        require(contentVersion.isNotBlank() && contentDefinitionVersion >= 1)
    }
}

sealed interface RecordLessonAssessmentResultV2 {
    data class Recorded(
        val progress: LessonProgress,
        val decision: LessonGateDecisionV2
    ) : RecordLessonAssessmentResultV2

    data class Duplicate(val assessmentAttemptId: String) : RecordLessonAssessmentResultV2
}

object LessonEvidenceIdsV2 {
    private const val NAMESPACE = "poincare.lesson-evidence.v2"

    fun stageProgressId(childProfileId: String, lessonId: String, stageId: String): String =
        stableId("stage", childProfileId, lessonId, stageId)

    fun errorMemoryId(childProfileId: String, lessonId: String, masteryTag: String): String =
        stableId("error", childProfileId, lessonId, masteryTag)

    private fun stableId(type: String, vararg values: String): String = UUID.nameUUIDFromBytes(
        (listOf(NAMESPACE, type) + values).joinToString("|").toByteArray(StandardCharsets.UTF_8)
    ).toString()
}

private fun requireUuidV2(value: String, fieldName: String) {
    require(runCatching { UUID.fromString(value) }.isSuccess) { "$fieldName must be a UUID" }
}
