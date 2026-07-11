package be.saikono.poincare.repository

import be.saikono.poincare.data.audit.AuditLog
import be.saikono.poincare.data.local.ProgressDao
import be.saikono.poincare.data.local.ProgressEntity
import be.saikono.poincare.model.ProgressEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface ProgressRepository {
    fun getProgressByChild(childId: String): Flow<List<ProgressEvent>>
    suspend fun addProgress(event: ProgressEvent)
    suspend fun getPendingProgress(): List<ProgressEvent>
    suspend fun updateSyncStatus(id: String, status: be.saikono.poincare.model.SyncStatus)
}

class LocalProgressRepository(private val progressDao: ProgressDao) : ProgressRepository {
    override fun getProgressByChild(childId: String): Flow<List<ProgressEvent>> =
        progressDao.getProgressByChild(childId).map { entities ->
            entities.map { it.toModel() }
        }

    override suspend fun addProgress(event: ProgressEvent) {
        progressDao.insertProgress(event.toEntity())
        AuditLog.recordProgress(event)
    }

    override suspend fun getPendingProgress(): List<ProgressEvent> =
        progressDao.getPendingProgress().map { it.toModel() }

    override suspend fun updateSyncStatus(id: String, status: be.saikono.poincare.model.SyncStatus) =
        progressDao.updateSyncStatus(id, status)
}

private fun ProgressEntity.toModel() = ProgressEvent(
    id = id,
    childProfileId = childProfileId,
    exerciseId = exerciseId,
    domain = domain,
    skill = skill,
    timestamp = timestamp,
    answerCorrect = answerCorrect,
    responseTimeMs = responseTimeMs,
    hintsUsed = hintsUsed,
    retryCount = retryCount,
    languageSwitches = languageSwitches,
    translationsUsed = translationsUsed,
    rewardStampsEarned = rewardStampsEarned,
    syncStatus = syncStatus,
    metadata = try {
        Json.decodeFromString<Map<String, String>>(metadataJson)
    } catch (e: Exception) {
        emptyMap()
    }
)

private fun ProgressEvent.toEntity() = ProgressEntity(
    id = id,
    childProfileId = childProfileId,
    exerciseId = exerciseId,
    domain = domain,
    skill = skill,
    timestamp = timestamp,
    answerCorrect = answerCorrect,
    responseTimeMs = responseTimeMs,
    hintsUsed = hintsUsed,
    retryCount = retryCount,
    languageSwitches = languageSwitches,
    translationsUsed = translationsUsed,
    rewardStampsEarned = rewardStampsEarned,
    syncStatus = syncStatus,
    metadataJson = Json.encodeToString(metadata)
)
