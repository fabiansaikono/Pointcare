package be.saikono.poincare.data.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class SyncManager(private val context: Context) {
    
    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "progress_sync",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }
}
