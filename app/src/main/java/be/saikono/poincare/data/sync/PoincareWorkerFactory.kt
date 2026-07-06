package be.saikono.poincare.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import be.saikono.poincare.repository.ProgressRepository
import be.saikono.poincare.repository.RemoteProgressRepository

class PoincareWorkerFactory(
    private val progressRepository: ProgressRepository,
    private val remoteProgressRepository: RemoteProgressRepository
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            SyncWorker::class.java.name ->
                SyncWorker(appContext, workerParameters, progressRepository, remoteProgressRepository)
            else -> null
        }
    }
}
