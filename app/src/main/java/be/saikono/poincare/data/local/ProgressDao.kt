package be.saikono.poincare.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE childProfileId = :childId")
    fun getProgressByChild(childId: String): Flow<List<ProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: ProgressEntity)

    @Query("SELECT * FROM progress WHERE syncStatus = 'PENDING'")
    suspend fun getPendingProgress(): List<ProgressEntity>

    @Query("UPDATE progress SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: be.saikono.poincare.model.SyncStatus)
}
