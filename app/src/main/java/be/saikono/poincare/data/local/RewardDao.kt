package be.saikono.poincare.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardDao {
    @Query("SELECT * FROM parent_rewards WHERE childProfileId = :childId")
    fun getRewardsForChild(childId: String): Flow<List<ParentRewardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReward(reward: ParentRewardEntity)

    @Update
    suspend fun updateReward(reward: ParentRewardEntity)

    @Query("SELECT * FROM stamp_cards WHERE childProfileId = :childId")
    fun getStampCardForChild(childId: String): Flow<StampCardEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStampCard(stampCard: StampCardEntity)

    @Update
    suspend fun updateStampCard(stampCard: StampCardEntity)
}
