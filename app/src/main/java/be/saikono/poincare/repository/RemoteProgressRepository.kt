package be.saikono.poincare.repository

import be.saikono.poincare.data.sync.ProgressSyncDto
import be.saikono.poincare.data.sync.toDto
import be.saikono.poincare.model.ProgressEvent
import kotlinx.coroutines.delay

interface RemoteProgressRepository {
    suspend fun syncProgressEvent(dto: ProgressSyncDto): Result<Unit>
    suspend fun syncBatch(dtos: List<ProgressSyncDto>): Result<Unit>
}

class FakeRemoteProgressRepository : RemoteProgressRepository {
    override suspend fun syncProgressEvent(dto: ProgressSyncDto): Result<Unit> {
        // Simulate network delay
        delay(500)
        
        // Simulate success (mostly)
        return if (Math.random() > 0.05) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Simulated network error"))
        }
    }

    override suspend fun syncBatch(dtos: List<ProgressSyncDto>): Result<Unit> {
        delay(1000)
        return if (Math.random() > 0.1) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Batch sync failed"))
        }
    }
}
