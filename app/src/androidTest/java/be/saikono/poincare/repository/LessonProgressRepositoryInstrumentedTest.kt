package be.saikono.poincare.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import be.saikono.poincare.data.local.PoincareDatabase
import be.saikono.poincare.model.lesson.LessonMasteryLevel
import be.saikono.poincare.model.lesson.LessonProgressState
import be.saikono.poincare.model.lesson.RecordLessonAttemptRequest
import be.saikono.poincare.model.lesson.RecordLessonAttemptResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LessonProgressRepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var database: PoincareDatabase
    private lateinit var repository: LocalLessonProgressRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "lesson-progress-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        database = PoincareDatabase.build(context, databaseName)
        repository = LocalLessonProgressRepository(database)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun duplicateAttemptIsIdempotentAndProgressPersistsAfterReopen() = runBlocking {
        val firstRequest = request(
            attemptId = "11111111-1111-4111-8111-111111111111",
            score = 92,
            startedAt = 1_000L,
            completedAt = 2_000L
        )

        val first = repository.recordAttempt(firstRequest)
        assertTrue(first is RecordLessonAttemptResult.Recorded)

        val duplicate = repository.recordAttempt(firstRequest)
        assertTrue(duplicate is RecordLessonAttemptResult.Duplicate)
        assertEquals(1, repository.getAttempts("child-1", LESSON_ID).size)
        assertEquals(1, repository.getProgress("child-1", LESSON_ID)?.attemptCount)

        val second = repository.recordAttempt(
            request(
                attemptId = "22222222-2222-4222-8222-222222222222",
                score = 35,
                startedAt = 3_000L,
                completedAt = 4_000L
            )
        )
        assertTrue(second is RecordLessonAttemptResult.Recorded)

        val progress = repository.getProgress("child-1", LESSON_ID)
            ?: error("Missing lesson progress")
        assertEquals(2, progress.attemptCount)
        assertEquals(92, progress.bestScorePercent)
        assertEquals(35, progress.latestScorePercent)
        assertEquals(92, progress.masteryPercent)
        assertEquals(LessonProgressState.COMPLETED, progress.state)
        assertEquals(LessonMasteryLevel.MASTERED, progress.masteryLevel)
        assertEquals(2, repository.getAttempts("child-1", LESSON_ID).size)
        assertEquals(2, repository.getPendingAttempts().size)
        assertEquals(1, repository.getPendingProgress().size)

        database.close()
        database = PoincareDatabase.build(context, databaseName)
        repository = LocalLessonProgressRepository(database)

        val reopened = repository.getProgress("child-1", LESSON_ID)
            ?: error("Lesson progress was not persisted after reopen")
        assertEquals(2, reopened.attemptCount)
        assertEquals(92, reopened.bestScorePercent)
        assertEquals(2, repository.getAttempts("child-1", LESSON_ID).size)
    }

    @Test
    fun duplicateSyncIdWithDifferentAttemptIdDoesNotIncrementProgress() = runBlocking {
        val sharedSyncId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        val original = request(
            attemptId = "33333333-3333-4333-8333-333333333333",
            syncId = sharedSyncId,
            score = 70
        )
        val conflictingRetry = request(
            attemptId = "44444444-4444-4444-8444-444444444444",
            syncId = sharedSyncId,
            score = 100
        )

        assertTrue(repository.recordAttempt(original) is RecordLessonAttemptResult.Recorded)
        assertTrue(repository.recordAttempt(conflictingRetry) is RecordLessonAttemptResult.Duplicate)
        assertEquals(1, repository.getAttempts("child-1", LESSON_ID).size)
        assertEquals(1, repository.getProgress("child-1", LESSON_ID)?.attemptCount)
        assertEquals(70, repository.getProgress("child-1", LESSON_ID)?.bestScorePercent)
    }

    private fun request(
        attemptId: String,
        syncId: String = attemptId,
        score: Int,
        startedAt: Long = 1_000L,
        completedAt: Long = 2_000L
    ) = RecordLessonAttemptRequest(
        attemptId = attemptId,
        syncId = syncId,
        childProfileId = "child-1",
        unitId = "math.addition.grade1",
        lessonId = LESSON_ID,
        startedAtUtc = startedAt,
        completedAtUtc = completedAt,
        scorePercent = score,
        correctCount = score / 10,
        questionCount = 10,
        durationMs = completedAt - startedAt,
        generatorSeed = 42L,
        contentVersion = "1.0.0",
        metadata = mapOf("locale" to "nl-BE")
    )

    private companion object {
        const val LESSON_ID = "math.addition.grade1.lesson1"
    }
}
