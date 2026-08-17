package be.saikono.poincare.data.audit

import android.content.Context
import be.saikono.poincare.model.ProgressEvent
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * Append-only, local-first audit trail for navigation, user actions and missions.
 * Records are stored as JSON Lines under the app's private files directory.
 */
object AuditLog {
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private val sessionId = UUID.randomUUID().toString()
    @Volatile private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        record(action = "app_started")
    }

    fun record(
        action: String,
        profileId: String? = null,
        domain: String? = null,
        subcategoryId: String? = null,
        exerciseId: String? = null,
        details: Map<String, String> = emptyMap()
    ) {
        val context = applicationContext ?: return
        val event = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("sessionId", sessionId)
            put("timestamp", System.currentTimeMillis())
            put("action", action)
            profileId?.let { put("profileId", it) }
            domain?.let { put("domain", it) }
            subcategoryId?.let { put("subcategoryId", it) }
            exerciseId?.let { put("exerciseId", it) }
            put("details", JSONObject(details))
        }.toString()
        executor.execute {
            runCatching {
                synchronized(lock) {
                    val directory = File(context.filesDir, "audit").apply { mkdirs() }
                    val file = File(directory, "poincare-audit.jsonl")
                    if (file.exists() && file.length() > MAX_FILE_BYTES) {
                        File(directory, "poincare-audit-previous.jsonl").delete()
                        file.renameTo(File(directory, "poincare-audit-previous.jsonl"))
                    }
                    file.appendText(event + "\n", Charsets.UTF_8)
                }
            }
        }
    }

    fun recordProgress(event: ProgressEvent) {
        record(
            action = "mission_attempt_saved",
            profileId = event.childProfileId,
            domain = event.domain.name,
            subcategoryId = event.metadata["subcategoryId"],
            exerciseId = event.exerciseId,
            details = event.metadata + mapOf(
                "answerCorrect" to event.answerCorrect.toString(),
                "responseTimeMs" to event.responseTimeMs.toString(),
                "rewardPoints" to event.rewardStampsEarned.toString()
            )
        )
    }

    private const val MAX_FILE_BYTES = 10L * 1024L * 1024L
}
