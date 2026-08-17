package be.saikono.poincare.data.sync

import be.saikono.poincare.model.*
import be.saikono.poincare.model.lesson.LessonAttempt
import be.saikono.poincare.model.lesson.LessonProgress
import be.saikono.poincare.data.local.LessonAssessmentAttemptEntityV2
import be.saikono.poincare.data.local.LessonQuestionAttemptEntityV2
import be.saikono.poincare.data.local.LessonSkillErrorMemoryEntityV2
import be.saikono.poincare.data.local.LessonStageProgressEntityV2
import kotlinx.serialization.Serializable

@Serializable
data class ProgressSyncDto(
    val eventId: String,
    val childProfileId: String,
    val exerciseId: String,
    val domain: String,
    val skill: String,
    val timestampUtc: Long,
    val isCorrect: Boolean,
    val durationMs: Long,
    val hintsUsed: Int,
    val rewardStamps: Int,
    val metadata: Map<String, String>
)


@Serializable
data class LessonProgressSyncDto(
    val progressId: String,
    val syncId: String,
    val childProfileId: String,
    val unitId: String,
    val lessonId: String,
    val state: String,
    val masteryLevel: String,
    val masteryPercent: Int,
    val bestScorePercent: Int,
    val latestScorePercent: Int,
    val attemptCount: Int,
    val startedAtUtc: Long?,
    val completedAtUtc: Long?,
    val lastActivityAtUtc: Long,
    val updatedAtUtc: Long,
    val revision: Long,
    val gateStateV2: String? = null,
    val currentStageTypeV2: String? = null,
    val passedAtUtc: Long? = null,
    val masteredAtUtc: Long? = null,
    val contentDefinitionVersion: Int? = null,
    val legacyEvidence: Boolean? = null
)

@Serializable
data class LessonAttemptSyncDto(
    val attemptId: String,
    val syncId: String,
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
    val metadata: Map<String, String>
)

@Serializable
data class LessonStageProgressSyncDtoV2(
    val stageProgressId: String,
    val syncId: String,
    val childProfileId: String,
    val lessonId: String,
    val stageId: String,
    val stageType: String,
    val state: String,
    val diagnosticScorePercent: Int?,
    val startedAtUtc: Long?,
    val completedAtUtc: Long?,
    val updatedAtUtc: Long,
    val contentDefinitionVersion: Int
)

@Serializable
data class LessonAssessmentAttemptSyncDtoV2(
    val assessmentAttemptId: String,
    val syncId: String,
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
    val metadataJson: String
)

@Serializable
data class LessonQuestionAttemptSyncDtoV2(
    val questionAttemptId: String,
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

@Serializable
data class LessonSkillErrorMemorySyncDtoV2(
    val memoryId: String,
    val syncId: String,
    val childProfileId: String,
    val lessonId: String,
    val masteryTag: String,
    val wrongCount: Int,
    val consecutiveCorrect: Int,
    val nextReviewAtUtc: Long,
    val resolved: Boolean,
    val lastItemFingerprint: String,
    val updatedAtUtc: Long
)

@Serializable
data class ProfileSyncDto(
    val id: String,
    val name: String,
    val levels: Map<String, String>,
    val selectedGrades: Map<String, List<Int>> = emptyMap(),
    val hiddenMainCategories: List<String>,
    val hiddenSubcategories: List<String>,
    val giftedMode: Boolean,
    val updatedAtUtc: Long
)

// Mapper functions
fun ProgressEvent.toDto() = ProgressSyncDto(
    eventId = id,
    childProfileId = childProfileId,
    exerciseId = exerciseId,
    domain = domain.name,
    skill = skill,
    timestampUtc = timestamp,
    isCorrect = answerCorrect,
    durationMs = responseTimeMs,
    hintsUsed = hintsUsed,
    rewardStamps = rewardStampsEarned,
    metadata = metadata
)

fun ChildProfile.toDto() = ProfileSyncDto(
    id = id,
    name = name,
    levels = mapOf(
        "reading" to readingLevel.name,
        "writing" to writingLevel.name,
        "math" to mathLevel.name,
        "puzzle" to puzzleLevel.name,
        "geometry" to geometryLevel.name,
        "worldKnowledge" to worldKnowledgeLevel.name,
    ),
    selectedGrades = LearningDomain.entries.associate { domain -> domain.name to selectedGradesFor(domain) },
    hiddenMainCategories = hiddenMainCategories,
    hiddenSubcategories = hiddenSubcategories,
    giftedMode = giftedModeEnabled,
    updatedAtUtc = updatedAt
)

fun LessonProgress.toDto() = LessonProgressSyncDto(
    progressId = progressId,
    syncId = syncId,
    childProfileId = childProfileId,
    unitId = unitId,
    lessonId = lessonId,
    state = state.name,
    masteryLevel = masteryLevel.name,
    masteryPercent = masteryPercent,
    bestScorePercent = bestScorePercent,
    latestScorePercent = latestScorePercent,
    attemptCount = attemptCount,
    startedAtUtc = startedAtUtc,
    completedAtUtc = completedAtUtc,
    lastActivityAtUtc = lastActivityAtUtc,
    updatedAtUtc = updatedAtUtc,
    revision = revision,
    gateStateV2 = gateStateV2?.name,
    currentStageTypeV2 = currentStageTypeV2?.name,
    passedAtUtc = passedAtUtc,
    masteredAtUtc = masteredAtUtc,
    contentDefinitionVersion = contentDefinitionVersion,
    legacyEvidence = legacyEvidence
)

fun LessonAttempt.toDto() = LessonAttemptSyncDto(
    attemptId = attemptId,
    syncId = syncId,
    progressId = progressId,
    childProfileId = childProfileId,
    unitId = unitId,
    lessonId = lessonId,
    sequenceNumber = sequenceNumber,
    startedAtUtc = startedAtUtc,
    completedAtUtc = completedAtUtc,
    scorePercent = scorePercent,
    correctCount = correctCount,
    questionCount = questionCount,
    durationMs = durationMs,
    hintsUsed = hintsUsed,
    completed = completed,
    masteryAfterPercent = masteryAfterPercent,
    generatorSeed = generatorSeed,
    contentVersion = contentVersion,
    metadata = metadata.toSortedMap().toMap()
)

fun LessonStageProgressEntityV2.toDto() = LessonStageProgressSyncDtoV2(
    stageProgressId = stageProgressId,
    syncId = syncId,
    childProfileId = childProfileId,
    lessonId = lessonId,
    stageId = stageId,
    stageType = stageType.name,
    state = state,
    diagnosticScorePercent = diagnosticScorePercent,
    startedAtUtc = startedAtUtc,
    completedAtUtc = completedAtUtc,
    updatedAtUtc = updatedAtUtc,
    contentDefinitionVersion = contentDefinitionVersion
)

fun LessonAssessmentAttemptEntityV2.toDto() = LessonAssessmentAttemptSyncDtoV2(
    assessmentAttemptId = assessmentAttemptId,
    syncId = syncId,
    progressId = progressId,
    childProfileId = childProfileId,
    unitId = unitId,
    lessonId = lessonId,
    stageId = stageId,
    evidenceSource = evidenceSource,
    startedAtUtc = startedAtUtc,
    completedAtUtc = completedAtUtc,
    correctCount = correctCount,
    itemCount = itemCount,
    scorePercent = scorePercent,
    hintsUsed = hintsUsed,
    passed = passed,
    retentionCheck = retentionCheck,
    contentVersion = contentVersion,
    metadataJson = metadataJson
)

fun LessonQuestionAttemptEntityV2.toDto() = LessonQuestionAttemptSyncDtoV2(
    questionAttemptId = questionAttemptId,
    assessmentAttemptId = assessmentAttemptId,
    childProfileId = childProfileId,
    lessonId = lessonId,
    exerciseId = exerciseId,
    itemFingerprint = itemFingerprint,
    masteryTagsJson = masteryTagsJson,
    correct = correct,
    hintsUsed = hintsUsed,
    durationMs = durationMs,
    answeredAtUtc = answeredAtUtc
)

fun LessonSkillErrorMemoryEntityV2.toDto() = LessonSkillErrorMemorySyncDtoV2(
    memoryId = memoryId,
    syncId = syncId,
    childProfileId = childProfileId,
    lessonId = lessonId,
    masteryTag = masteryTag,
    wrongCount = wrongCount,
    consecutiveCorrect = consecutiveCorrect,
    nextReviewAtUtc = nextReviewAtUtc,
    resolved = resolved,
    lastItemFingerprint = lastItemFingerprint,
    updatedAtUtc = updatedAtUtc
)
