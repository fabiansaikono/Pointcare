package be.saikono.poincare.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import be.saikono.poincare.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonProgressDao {
    @Query(
        """SELECT * FROM lesson_progress
            WHERE childProfileId = :childProfileId
            ORDER BY lastActivityAtUtc DESC, lessonId ASC"""
    )
    fun observeProgressForChild(childProfileId: String): Flow<List<LessonProgressEntity>>

    @Query(
        """SELECT * FROM lesson_progress
            WHERE childProfileId = :childProfileId AND lessonId = :lessonId
            LIMIT 1"""
    )
    suspend fun getProgress(childProfileId: String, lessonId: String): LessonProgressEntity?

    @Query("SELECT * FROM lesson_progress WHERE progressId = :progressId LIMIT 1")
    suspend fun getProgressById(progressId: String): LessonProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: LessonProgressEntity)

    @Query(
        """SELECT * FROM lesson_attempts
            WHERE childProfileId = :childProfileId AND lessonId = :lessonId
            ORDER BY sequenceNumber ASC"""
    )
    fun observeAttempts(childProfileId: String, lessonId: String): Flow<List<LessonAttemptEntity>>

    @Query(
        """SELECT * FROM lesson_attempts
            WHERE childProfileId = :childProfileId AND lessonId = :lessonId
            ORDER BY sequenceNumber ASC"""
    )
    suspend fun getAttempts(childProfileId: String, lessonId: String): List<LessonAttemptEntity>

    @Query("SELECT * FROM lesson_attempts WHERE attemptId = :attemptId LIMIT 1")
    suspend fun getAttemptById(attemptId: String): LessonAttemptEntity?

    @Query("SELECT * FROM lesson_attempts WHERE syncId = :syncId LIMIT 1")
    suspend fun getAttemptBySyncId(syncId: String): LessonAttemptEntity?

    @Query(
        """SELECT COALESCE(MAX(sequenceNumber), 0) + 1 FROM lesson_attempts
            WHERE childProfileId = :childProfileId AND lessonId = :lessonId"""
    )
    suspend fun nextSequenceNumber(childProfileId: String, lessonId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttemptIfAbsent(attempt: LessonAttemptEntity): Long

    @Query("SELECT * FROM lesson_progress WHERE syncStatus != 'SYNCED' ORDER BY updatedAtUtc ASC")
    suspend fun getPendingProgress(): List<LessonProgressEntity>

    @Query("SELECT * FROM lesson_attempts WHERE syncStatus != 'SYNCED' ORDER BY completedAtUtc ASC")
    suspend fun getPendingAttempts(): List<LessonAttemptEntity>

    @Query(
        """UPDATE lesson_progress SET syncStatus = :status
            WHERE progressId = :progressId"""
    )
    suspend fun updateProgressSyncStatus(progressId: String, status: SyncStatus)

    @Query(
        """UPDATE lesson_attempts SET syncStatus = :status
            WHERE attemptId = :attemptId"""
    )
    suspend fun updateAttemptSyncStatus(attemptId: String, status: SyncStatus)

    @Query("SELECT * FROM lesson_stage_progress WHERE childProfileId = :childProfileId AND lessonId = :lessonId ORDER BY stageType ASC")
    fun observeStageProgressV2(childProfileId: String, lessonId: String): Flow<List<LessonStageProgressEntityV2>>

    @Query("SELECT * FROM lesson_stage_progress WHERE childProfileId = :childProfileId ORDER BY lessonId ASC, updatedAtUtc ASC")
    fun observeAllStageProgressV2(childProfileId: String): Flow<List<LessonStageProgressEntityV2>>

    @Query("SELECT * FROM lesson_stage_progress WHERE childProfileId = :childProfileId AND lessonId = :lessonId ORDER BY updatedAtUtc ASC")
    suspend fun getStageProgressV2(childProfileId: String, lessonId: String): List<LessonStageProgressEntityV2>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStageProgressV2(progress: LessonStageProgressEntityV2)

    @Query("SELECT * FROM lesson_assessment_attempts WHERE childProfileId = :childProfileId AND lessonId = :lessonId ORDER BY completedAtUtc ASC")
    fun observeAssessmentAttemptsV2(childProfileId: String, lessonId: String): Flow<List<LessonAssessmentAttemptEntityV2>>

    @Query("SELECT * FROM lesson_assessment_attempts WHERE assessmentAttemptId = :attemptId OR syncId = :syncId LIMIT 1")
    suspend fun findAssessmentAttemptV2(attemptId: String, syncId: String): LessonAssessmentAttemptEntityV2?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAssessmentAttemptV2(attempt: LessonAssessmentAttemptEntityV2): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuestionAttemptsV2(attempts: List<LessonQuestionAttemptEntityV2>)

    @Query("SELECT * FROM lesson_question_attempts WHERE assessmentAttemptId = :assessmentAttemptId ORDER BY answeredAtUtc ASC")
    suspend fun getQuestionAttemptsV2(assessmentAttemptId: String): List<LessonQuestionAttemptEntityV2>

    @Query("SELECT * FROM lesson_skill_error_memory WHERE childProfileId = :childProfileId AND lessonId = :lessonId ORDER BY nextReviewAtUtc ASC")
    fun observeSkillErrorMemoryV2(childProfileId: String, lessonId: String): Flow<List<LessonSkillErrorMemoryEntityV2>>

    @Query("SELECT * FROM lesson_skill_error_memory WHERE childProfileId = :childProfileId AND lessonId = :lessonId AND masteryTag = :masteryTag LIMIT 1")
    suspend fun getSkillErrorMemoryV2(childProfileId: String, lessonId: String, masteryTag: String): LessonSkillErrorMemoryEntityV2?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkillErrorMemoryV2(memory: LessonSkillErrorMemoryEntityV2)
}
