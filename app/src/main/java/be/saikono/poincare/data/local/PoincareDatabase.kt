package be.saikono.poincare.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ProfileEntity::class,
        ProgressEntity::class,
        ParentRewardEntity::class,
        StampCardEntity::class,
        SyncQueueEntity::class,
        LessonProgressEntity::class,
        LessonAttemptEntity::class,
        LessonStageProgressEntityV2::class,
        LessonAssessmentAttemptEntityV2::class,
        LessonQuestionAttemptEntityV2::class,
        LessonSkillErrorMemoryEntityV2::class
    ],
    version = 11,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PoincareDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun progressDao(): ProgressDao
    abstract fun rewardDao(): RewardDao
    abstract fun lessonProgressDao(): LessonProgressDao

    companion object {
        const val DATABASE_NAME: String = "poincare-db"

        fun build(context: Context, databaseName: String = DATABASE_NAME): PoincareDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                PoincareDatabase::class.java,
                databaseName
            )
                .addMigrations(*DatabaseMigrations.ALL)
                .build()
    }
}
