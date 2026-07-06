package be.saikono.poincare.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SyncStatus

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val id: String,
    val childProfileId: String,
    val exerciseId: String,
    val domain: LearningDomain,
    val skill: String,
    val timestamp: Long,
    val answerCorrect: Boolean,
    val responseTimeMs: Long,
    val hintsUsed: Int,
    val retryCount: Int,
    val languageSwitches: Int,
    val translationsUsed: Int,
    val rewardStampsEarned: Int,
    val syncStatus: SyncStatus,
    val metadataJson: String
)
