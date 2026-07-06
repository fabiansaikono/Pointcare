package be.saikono.poincare.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ProfileEntity::class,
        ProgressEntity::class,
        ParentRewardEntity::class,
        StampCardEntity::class,
        SyncQueueEntity::class
    ],
    version = 5
)
@TypeConverters(Converters::class)
abstract class PoincareDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun progressDao(): ProgressDao
    abstract fun rewardDao(): RewardDao
}
