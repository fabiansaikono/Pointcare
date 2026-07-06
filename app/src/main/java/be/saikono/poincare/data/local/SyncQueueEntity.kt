package be.saikono.poincare.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val eventId: String,
    val timestamp: Long = System.currentTimeMillis()
)
