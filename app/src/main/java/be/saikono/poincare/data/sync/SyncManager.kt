package be.saikono.poincare.data.sync

import android.content.Context

/**
 * Compatibility boundary for lesson runtimes that persist progress locally.
 *
 * Remote sync is deliberately disabled: presenting a simulated upload as a
 * successful sync risks data loss. This method can become a real enqueue call
 * only after an authenticated, idempotent server contract is available.
 */
class SyncManager(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun scheduleSync() {
        // Progress has already been committed to Room by the caller.
    }
}
