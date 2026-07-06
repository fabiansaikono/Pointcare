package be.saikono.poincare.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parent_rewards")
data class ParentRewardEntity(
    @PrimaryKey val id: String,
    val childProfileId: String,
    val tier: Int,
    val labelsJson: String,
    val stampsRequired: Int,
    val currentStamps: Int,
    val isClaimed: Boolean,
    val requiresParentApproval: Boolean
)

@Entity(tableName = "stamp_cards")
data class StampCardEntity(
    @PrimaryKey val id: String,
    val childProfileId: String,
    val totalStamps: Int,
    val lastUpdated: Long
)
