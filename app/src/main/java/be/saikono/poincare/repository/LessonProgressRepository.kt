package be.saikono.poincare.repository

import androidx.room.withTransaction
import be.saikono.poincare.data.local.LessonAttemptEntity
import be.saikono.poincare.data.local.LessonAssessmentAttemptEntityV2
import be.saikono.poincare.data.local.LessonProgressEntity
import be.saikono.poincare.data.local.LessonQuestionAttemptEntityV2
import be.saikono.poincare.data.local.LessonSkillErrorMemoryEntityV2
import be.saikono.poincare.data.local.LessonStageProgressEntityV2
import be.saikono.poincare.data.local.PoincareDatabase
import be.saikono.poincare.domain.curriculum.v2.ErrorRepetitionSchedulerV2
import be.saikono.poincare.domain.curriculum.v2.LessonAssessmentEvidenceV2
import be.saikono.poincare.domain.curriculum.v2.LessonEvidenceSourceV2
import be.saikono.poincare.domain.curriculum.v2.LessonGateStateV2
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import be.saikono.poincare.domain.curriculum.v2.LessonStatusServiceV2
import be.saikono.poincare.domain.curriculum.v2.SkillErrorMemoryV2
import be.saikono.poincare.model.SyncStatus
import be.saikono.poincare.model.lesson.LessonAttempt
import be.saikono.poincare.model.lesson.LessonProgress
import be.saikono.poincare.model.lesson.LessonProgressCalculator
import be.saikono.poincare.model.lesson.LessonEvidenceIdsV2
import be.saikono.poincare.model.lesson.LessonMasteryLevel
import be.saikono.poincare.model.lesson.LessonProgressIds
import be.saikono.poincare.model.lesson.LessonProgressState
import be.saikono.poincare.model.lesson.RecordLessonAttemptRequest
import be.saikono.poincare.model.lesson.RecordLessonAttemptResult
import be.saikono.poincare.model.lesson.RecordLessonAssessmentRequestV2
import be.saikono.poincare.model.lesson.RecordLessonAssessmentResultV2
import be.saikono.poincare.model.lesson.RecordLessonStageProgressRequestV2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal object LessonMetadataCodec {
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    fun encode(metadata: Map<String, String>): String =
        Json.encodeToString(serializer, metadata.toSortedMap())

    fun decode(value: String): Map<String, String> = try {
        Json.decodeFromString(serializer, value).toSortedMap().toMap()
    } catch (_: Exception) {
        emptyMap()
    }
}

internal object LessonTagCodecV2 {
    private val serializer = ListSerializer(String.serializer())
    fun encode(values: List<String>): String = Json.encodeToString(serializer, values.distinct().sorted())
}

interface LessonProgressRepository {
    fun observeProgressForChild(childProfileId: String): Flow<List<LessonProgress>>
    fun observeAllStageProgressV2(childProfileId: String): Flow<List<LessonStageProgressSnapshotV2>> =
        flowOf(emptyList())
    fun observeAttempts(childProfileId: String, lessonId: String): Flow<List<LessonAttempt>>
    suspend fun getProgress(childProfileId: String, lessonId: String): LessonProgress?
    suspend fun getAttempts(childProfileId: String, lessonId: String): List<LessonAttempt>
    suspend fun recordAttempt(request: RecordLessonAttemptRequest): RecordLessonAttemptResult
    suspend fun getPendingProgress(): List<LessonProgress>
    suspend fun getPendingAttempts(): List<LessonAttempt>
    suspend fun updateProgressSyncStatus(progressId: String, status: SyncStatus)
    suspend fun updateAttemptSyncStatus(attemptId: String, status: SyncStatus)
    suspend fun getStageProgressV2(
        childProfileId: String,
        lessonId: String
    ): List<LessonStageProgressSnapshotV2> = emptyList()
    suspend fun recordStageProgressV2(request: RecordLessonStageProgressRequestV2): LessonProgress
    suspend fun recordAssessmentV2(request: RecordLessonAssessmentRequestV2): RecordLessonAssessmentResultV2
}

data class LessonStageProgressSnapshotV2(
    val lessonId: String,
    val stageId: String,
    val stageType: LessonStageTypeV2,
    val completed: Boolean,
    val diagnosticScorePercent: Int?
)

/**
 * Local-first lesson persistence. Recording is idempotent for a stable attemptId or syncId:
 * a retried command returns Duplicate and never increments the lesson summary twice.
 */
class LocalLessonProgressRepository(
    private val database: PoincareDatabase
) : LessonProgressRepository {
    private val dao = database.lessonProgressDao()

    override fun observeProgressForChild(childProfileId: String): Flow<List<LessonProgress>> =
        dao.observeProgressForChild(childProfileId).map { rows -> rows.map { it.toModel() } }

    override fun observeAllStageProgressV2(
        childProfileId: String
    ): Flow<List<LessonStageProgressSnapshotV2>> =
        dao.observeAllStageProgressV2(childProfileId).map { rows ->
            rows.map { row ->
                LessonStageProgressSnapshotV2(
                    lessonId = row.lessonId,
                    stageId = row.stageId,
                    stageType = row.stageType,
                    completed = row.state == "COMPLETED",
                    diagnosticScorePercent = row.diagnosticScorePercent
                )
            }
        }

    override fun observeAttempts(
        childProfileId: String,
        lessonId: String
    ): Flow<List<LessonAttempt>> =
        dao.observeAttempts(childProfileId, lessonId).map { rows -> rows.map { it.toModel() } }

    override suspend fun getProgress(childProfileId: String, lessonId: String): LessonProgress? =
        dao.getProgress(childProfileId, lessonId)?.toModel()

    override suspend fun getAttempts(childProfileId: String, lessonId: String): List<LessonAttempt> =
        dao.getAttempts(childProfileId, lessonId).map { it.toModel() }

    override suspend fun recordAttempt(
        request: RecordLessonAttemptRequest
    ): RecordLessonAttemptResult = database.withTransaction {
        request.validate()

        val duplicate = dao.getAttemptById(request.attemptId)
            ?: dao.getAttemptBySyncId(request.syncId)
        if (duplicate != null) {
            return@withTransaction RecordLessonAttemptResult.Duplicate(
                existingAttempt = duplicate.toModel(),
                progress = dao.getProgressById(duplicate.progressId)?.toModel()
            )
        }

        val current = dao.getProgress(request.childProfileId, request.lessonId)?.toModel()
        val sequence = dao.nextSequenceNumber(request.childProfileId, request.lessonId)
        val calculation = LessonProgressCalculator.apply(current, request, sequence)
        val attemptEntity = calculation.attempt.toEntity()

        val inserted = dao.insertAttemptIfAbsent(attemptEntity)
        if (inserted == -1L) {
            val existing = dao.getAttemptById(request.attemptId)
                ?: dao.getAttemptBySyncId(request.syncId)
                ?: error("Attempt insert conflicted without a readable existing row")
            return@withTransaction RecordLessonAttemptResult.Duplicate(
                existingAttempt = existing.toModel(),
                progress = dao.getProgressById(existing.progressId)?.toModel()
            )
        }

        dao.upsertProgress(calculation.progress.toEntity())
        RecordLessonAttemptResult.Recorded(
            progress = calculation.progress,
            attempt = calculation.attempt
        )
    }

    override suspend fun getPendingProgress(): List<LessonProgress> =
        dao.getPendingProgress().map { it.toModel() }

    override suspend fun getPendingAttempts(): List<LessonAttempt> =
        dao.getPendingAttempts().map { it.toModel() }

    override suspend fun updateProgressSyncStatus(progressId: String, status: SyncStatus) {
        dao.updateProgressSyncStatus(progressId, status)
    }

    override suspend fun updateAttemptSyncStatus(attemptId: String, status: SyncStatus) {
        dao.updateAttemptSyncStatus(attemptId, status)
    }

    override suspend fun getStageProgressV2(
        childProfileId: String,
        lessonId: String
    ): List<LessonStageProgressSnapshotV2> = dao.getStageProgressV2(childProfileId, lessonId).map { row ->
        LessonStageProgressSnapshotV2(
            lessonId = row.lessonId,
            stageId = row.stageId,
            stageType = row.stageType,
            completed = row.state == "COMPLETED",
            diagnosticScorePercent = row.diagnosticScorePercent
        )
    }

    override suspend fun recordStageProgressV2(
        request: RecordLessonStageProgressRequestV2
    ): LessonProgress = database.withTransaction {
        require(request.childProfileId.isNotBlank() && request.unitId.isNotBlank() && request.lessonId.isNotBlank())
        require(request.stageId.isNotBlank() && request.timestampUtc >= 0L)
        require(request.diagnosticScorePercent == null || request.diagnosticScorePercent in 0..100)
        require(request.contentDefinitionVersion >= 1)

        val current = dao.getProgress(request.childProfileId, request.lessonId)?.toModel()
        val base = current ?: newProgressV2(request.childProfileId, request.unitId, request.lessonId, request.timestampUtc)
        val existingStage = dao.getStageProgressV2(request.childProfileId, request.lessonId)
            .firstOrNull { it.stageId == request.stageId }
        val effectiveCompleted = request.completed || existingStage?.state == "COMPLETED"
        val stageState = if (effectiveCompleted) "COMPLETED" else "IN_PROGRESS"
        val stageProgressId = LessonEvidenceIdsV2.stageProgressId(
            request.childProfileId,
            request.lessonId,
            request.stageId
        )
        dao.upsertStageProgressV2(
            LessonStageProgressEntityV2(
                stageProgressId = stageProgressId,
                childProfileId = request.childProfileId,
                lessonId = request.lessonId,
                stageId = request.stageId,
                stageType = request.stageType,
                state = stageState,
                diagnosticScorePercent = request.diagnosticScorePercent ?: existingStage?.diagnosticScorePercent,
                startedAtUtc = existingStage?.startedAtUtc ?: base.startedAtUtc ?: request.timestampUtc,
                completedAtUtc = existingStage?.completedAtUtc ?: request.timestampUtc.takeIf { effectiveCompleted },
                updatedAtUtc = request.timestampUtc,
                contentDefinitionVersion = request.contentDefinitionVersion,
                syncId = stageProgressId,
                syncStatus = SyncStatus.PENDING
            )
        )
        val currentGate = base.effectiveGateStateV2
        val nextGate = when {
            LessonStatusServiceV2.unlocksDependants(currentGate) -> currentGate
            effectiveCompleted && request.stageType in setOf(
                LessonStageTypeV2.EXERCISES,
                LessonStageTypeV2.APPLY_DEMONSTRATE
            ) -> LessonGateStateV2.READY_FOR_TEST
            else -> LessonGateStateV2.IN_PROGRESS
        }
        val updated = base.copy(
            state = if (LessonStatusServiceV2.unlocksDependants(nextGate)) LessonProgressState.COMPLETED else LessonProgressState.IN_PROGRESS,
            latestScorePercent = request.diagnosticScorePercent ?: base.latestScorePercent,
            currentStageTypeV2 = request.stageType,
            gateStateV2 = nextGate,
            contentDefinitionVersion = request.contentDefinitionVersion,
            lastActivityAtUtc = request.timestampUtc,
            updatedAtUtc = request.timestampUtc,
            syncStatus = SyncStatus.PENDING,
            revision = base.revision + 1
        )
        dao.upsertProgress(updated.toEntity())
        updated
    }

    override suspend fun recordAssessmentV2(
        request: RecordLessonAssessmentRequestV2
    ): RecordLessonAssessmentResultV2 = database.withTransaction {
        request.validate()
        if (dao.findAssessmentAttemptV2(request.assessmentAttemptId, request.syncId) != null) {
            return@withTransaction RecordLessonAssessmentResultV2.Duplicate(request.assessmentAttemptId)
        }

        val current = dao.getProgress(request.childProfileId, request.lessonId)?.toModel()
        val base = current ?: newProgressV2(request.childProfileId, request.unitId, request.lessonId, request.startedAtUtc)
        val correctCount = request.questions.count { it.correct }
        val correctByTag = request.questions.asSequence()
            .filter { it.correct }
            .flatMap { it.masteryTags.asSequence() }
            .groupingBy { it }
            .eachCount()
        val evidence = LessonAssessmentEvidenceV2(
            source = request.source,
            correctCount = correctCount,
            itemCount = request.questions.size,
            hintsUsed = request.hintsUsed,
            correctByMasteryTag = correctByTag,
            explicitAdultAssessmentValid = request.explicitAdultAssessmentValid,
            retentionCheck = request.retentionCheck
        )
        val decision = LessonStatusServiceV2.evaluate(
            currentState = base.effectiveGateStateV2,
            policy = request.gatePolicy,
            evidence = evidence,
            adultAssessmentAllowed = request.adultAssessmentAllowed
        )
        val scorePercent = ((correctCount * 100.0) / request.questions.size).toInt()
        val attempt = LessonAssessmentAttemptEntityV2(
            assessmentAttemptId = request.assessmentAttemptId,
            progressId = base.progressId,
            childProfileId = request.childProfileId,
            unitId = request.unitId,
            lessonId = request.lessonId,
            stageId = request.stageId,
            evidenceSource = request.source.name,
            startedAtUtc = request.startedAtUtc,
            completedAtUtc = request.completedAtUtc,
            correctCount = correctCount,
            itemCount = request.questions.size,
            scorePercent = scorePercent,
            hintsUsed = request.hintsUsed,
            passed = decision.unlocksDependants,
            retentionCheck = request.retentionCheck,
            contentVersion = request.contentVersion,
            syncId = request.syncId,
            syncStatus = SyncStatus.PENDING,
            metadataJson = LessonMetadataCodec.encode(request.metadata)
        )
        if (dao.insertAssessmentAttemptV2(attempt) == -1L) {
            return@withTransaction RecordLessonAssessmentResultV2.Duplicate(request.assessmentAttemptId)
        }
        dao.insertQuestionAttemptsV2(request.questions.map { question ->
            LessonQuestionAttemptEntityV2(
                questionAttemptId = question.questionAttemptId,
                assessmentAttemptId = request.assessmentAttemptId,
                childProfileId = request.childProfileId,
                lessonId = request.lessonId,
                exerciseId = question.exerciseId,
                itemFingerprint = question.itemFingerprint,
                masteryTagsJson = LessonTagCodecV2.encode(question.masteryTags),
                correct = question.correct,
                hintsUsed = question.hintsUsed,
                durationMs = question.durationMs,
                answeredAtUtc = question.answeredAtUtc
            )
        })

        request.questions.forEach { question ->
            question.masteryTags.forEach { masteryTag ->
                val existingEntity = dao.getSkillErrorMemoryV2(request.childProfileId, request.lessonId, masteryTag)
                val existing = existingEntity?.toDomain()
                val next = if (question.correct) {
                    existing?.let { ErrorRepetitionSchedulerV2.recordCorrect(it, request.completedAtUtc, request.errorPolicy) }
                } else {
                    ErrorRepetitionSchedulerV2.recordWrong(
                        existing,
                        masteryTag,
                        question.itemFingerprint,
                        request.completedAtUtc,
                        request.errorPolicy
                    )
                }
                if (next != null) dao.upsertSkillErrorMemoryV2(
                    next.toEntity(request.childProfileId, request.lessonId, request.completedAtUtc)
                )
            }
        }

        val passedAt = base.passedAtUtc ?: request.completedAtUtc.takeIf { decision.state == LessonGateStateV2.PASSED }
        val masteredAt = base.masteredAtUtc ?: request.completedAtUtc.takeIf { decision.state == LessonGateStateV2.MASTERED }
        val updated = base.copy(
            state = if (decision.unlocksDependants) LessonProgressState.COMPLETED else LessonProgressState.IN_PROGRESS,
            masteryLevel = when (decision.state) {
                LessonGateStateV2.MASTERED -> LessonMasteryLevel.MASTERED
                LessonGateStateV2.PASSED, LessonGateStateV2.PASSED_LEGACY -> LessonMasteryLevel.PROFICIENT
                else -> if (scorePercent > 0) LessonMasteryLevel.DEVELOPING else LessonMasteryLevel.NOT_ASSESSED
            },
            masteryPercent = maxOf(base.masteryPercent, scorePercent),
            bestScorePercent = maxOf(base.bestScorePercent, scorePercent),
            latestScorePercent = scorePercent,
            attemptCount = base.attemptCount + 1,
            completedAtUtc = base.completedAtUtc ?: request.completedAtUtc.takeIf { decision.unlocksDependants },
            lastActivityAtUtc = request.completedAtUtc,
            updatedAtUtc = request.completedAtUtc,
            syncStatus = SyncStatus.PENDING,
            revision = base.revision + 1,
            gateStateV2 = decision.state,
            currentStageTypeV2 = LessonStageTypeV2.MASTERY_TEST,
            passedAtUtc = passedAt,
            masteredAtUtc = masteredAt,
            contentDefinitionVersion = request.contentDefinitionVersion,
            legacyEvidence = base.legacyEvidence
        )
        dao.upsertProgress(updated.toEntity())
        RecordLessonAssessmentResultV2.Recorded(updated, decision)
    }
}

private fun newProgressV2(childProfileId: String, unitId: String, lessonId: String, nowUtc: Long): LessonProgress {
    val progressId = LessonProgressIds.progressId(childProfileId, lessonId)
    return LessonProgress(
        progressId = progressId,
        childProfileId = childProfileId,
        unitId = unitId,
        lessonId = lessonId,
        state = LessonProgressState.IN_PROGRESS,
        masteryLevel = LessonMasteryLevel.NOT_ASSESSED,
        masteryPercent = 0,
        bestScorePercent = 0,
        latestScorePercent = 0,
        attemptCount = 0,
        startedAtUtc = nowUtc,
        completedAtUtc = null,
        lastActivityAtUtc = nowUtc,
        updatedAtUtc = nowUtc,
        syncId = progressId,
        syncStatus = SyncStatus.PENDING,
        revision = 0,
        gateStateV2 = LessonGateStateV2.IN_PROGRESS
    )
}

private fun LessonSkillErrorMemoryEntityV2.toDomain(): SkillErrorMemoryV2 = SkillErrorMemoryV2(
    masteryTag = masteryTag,
    wrongCount = wrongCount,
    consecutiveCorrect = consecutiveCorrect,
    nextReviewAtUtc = nextReviewAtUtc,
    resolved = resolved,
    lastItemFingerprint = lastItemFingerprint
)

private fun SkillErrorMemoryV2.toEntity(
    childProfileId: String,
    lessonId: String,
    nowUtc: Long
): LessonSkillErrorMemoryEntityV2 {
    val id = LessonEvidenceIdsV2.errorMemoryId(childProfileId, lessonId, masteryTag)
    return LessonSkillErrorMemoryEntityV2(
        memoryId = id,
        childProfileId = childProfileId,
        lessonId = lessonId,
        masteryTag = masteryTag,
        wrongCount = wrongCount,
        consecutiveCorrect = consecutiveCorrect,
        nextReviewAtUtc = nextReviewAtUtc,
        resolved = resolved,
        lastItemFingerprint = lastItemFingerprint,
        updatedAtUtc = nowUtc,
        syncId = id,
        syncStatus = SyncStatus.PENDING
    )
}

private fun LessonProgressEntity.toModel(): LessonProgress = LessonProgress(
    progressId = progressId,
    childProfileId = childProfileId,
    unitId = unitId,
    lessonId = lessonId,
    state = state,
    masteryLevel = masteryLevel,
    masteryPercent = masteryPercent,
    bestScorePercent = bestScorePercent,
    latestScorePercent = latestScorePercent,
    attemptCount = attemptCount,
    startedAtUtc = startedAtUtc,
    completedAtUtc = completedAtUtc,
    lastActivityAtUtc = lastActivityAtUtc,
    updatedAtUtc = updatedAtUtc,
    syncId = syncId,
    syncStatus = syncStatus,
    revision = revision,
    gateStateV2 = gateStateV2,
    currentStageTypeV2 = currentStageTypeV2,
    passedAtUtc = passedAtUtc,
    masteredAtUtc = masteredAtUtc,
    contentDefinitionVersion = contentDefinitionVersion,
    legacyEvidence = legacyEvidence
)

private fun LessonProgress.toEntity(): LessonProgressEntity = LessonProgressEntity(
    progressId = progressId,
    childProfileId = childProfileId,
    unitId = unitId,
    lessonId = lessonId,
    state = state,
    masteryLevel = masteryLevel,
    masteryPercent = masteryPercent,
    bestScorePercent = bestScorePercent,
    latestScorePercent = latestScorePercent,
    attemptCount = attemptCount,
    startedAtUtc = startedAtUtc,
    completedAtUtc = completedAtUtc,
    lastActivityAtUtc = lastActivityAtUtc,
    updatedAtUtc = updatedAtUtc,
    syncId = syncId,
    syncStatus = syncStatus,
    revision = revision,
    gateStateV2 = gateStateV2,
    currentStageTypeV2 = currentStageTypeV2,
    passedAtUtc = passedAtUtc,
    masteredAtUtc = masteredAtUtc,
    contentDefinitionVersion = contentDefinitionVersion,
    legacyEvidence = legacyEvidence
)

private fun LessonAttemptEntity.toModel(): LessonAttempt = LessonAttempt(
    attemptId = attemptId,
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
    syncId = syncId,
    syncStatus = syncStatus,
    metadata = LessonMetadataCodec.decode(metadataJson)
)

private fun LessonAttempt.toEntity(): LessonAttemptEntity = LessonAttemptEntity(
    attemptId = attemptId,
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
    syncId = syncId,
    syncStatus = syncStatus,
    metadataJson = LessonMetadataCodec.encode(metadata)
)
