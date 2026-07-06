package be.saikono.poincare.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import be.saikono.poincare.model.SyncStatus
import be.saikono.poincare.repository.ProgressRepository
import be.saikono.poincare.repository.RemoteProgressRepository

class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val progressRepository: ProgressRepository,
    private val remoteProgressRepository: RemoteProgressRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pendingEvents = progressRepository.getPendingProgress()
        if (pendingEvents.isEmpty()) return Result.success()

        val dtos = pendingEvents.map { it.toDto() }
        
        // Try batch sync
        val result = remoteProgressRepository.syncBatch(dtos)
        
        return if (result.isSuccess) {
            pendingEvents.forEach { event ->
                progressRepository.updateSyncStatus(event.id, SyncStatus.SYNCED)
            }
            Result.success()
        } else {
            // Retry later on failure
            Result.retry()
        }
    }
}
