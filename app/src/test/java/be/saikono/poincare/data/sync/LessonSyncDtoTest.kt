package be.saikono.poincare.data.sync

import be.saikono.poincare.model.SyncStatus
import be.saikono.poincare.model.lesson.LessonAttempt
import be.saikono.poincare.domain.curriculum.v2.LessonGateStateV2
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import be.saikono.poincare.model.lesson.LessonMasteryLevel
import be.saikono.poincare.model.lesson.LessonProgress
import be.saikono.poincare.model.lesson.LessonProgressState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonSyncDtoTest {
    @Test
    fun `lesson progress and attempts keep stable identifiers in sync DTOs`() {
        val progress = LessonProgress(
            progressId = "11111111-1111-4111-8111-111111111111",
            childProfileId = "child-1",
            unitId = "math.addition.grade1",
            lessonId = "math.addition.grade1.lesson1",
            state = LessonProgressState.COMPLETED,
            masteryLevel = LessonMasteryLevel.MASTERED,
            masteryPercent = 92,
            bestScorePercent = 92,
            latestScorePercent = 35,
            attemptCount = 2,
            startedAtUtc = 1_000L,
            completedAtUtc = 2_000L,
            lastActivityAtUtc = 4_000L,
            updatedAtUtc = 4_000L,
            syncId = "11111111-1111-4111-8111-111111111111",
            syncStatus = SyncStatus.PENDING,
            revision = 2L,
            gateStateV2 = LessonGateStateV2.PASSED,
            currentStageTypeV2 = LessonStageTypeV2.MASTERY_TEST,
            passedAtUtc = 2_000L,
            masteredAtUtc = 4_000L,
            contentDefinitionVersion = 2,
            legacyEvidence = false
        )
        val attempt = LessonAttempt(
            attemptId = "22222222-2222-4222-8222-222222222222",
            progressId = progress.progressId,
            childProfileId = progress.childProfileId,
            unitId = progress.unitId,
            lessonId = progress.lessonId,
            sequenceNumber = 2,
            startedAtUtc = 3_000L,
            completedAtUtc = 4_000L,
            scorePercent = 35,
            correctCount = 3,
            questionCount = 10,
            durationMs = 1_000L,
            hintsUsed = 1,
            completed = true,
            masteryAfterPercent = 92,
            generatorSeed = 42L,
            contentVersion = "1.0.0",
            syncId = "33333333-3333-4333-8333-333333333333",
            syncStatus = SyncStatus.PENDING,
            metadata = mapOf("z" to "last", "a" to "first")
        )

        val progressDto = progress.toDto()
        val attemptDto = attempt.toDto()

        assertEquals(progress.progressId, progressDto.progressId)
        assertEquals(progress.syncId, progressDto.syncId)
        assertEquals("MASTERED", progressDto.masteryLevel)
        assertEquals("PASSED", progressDto.gateStateV2)
        assertEquals("MASTERY_TEST", progressDto.currentStageTypeV2)
        assertEquals(2_000L, progressDto.passedAtUtc)
        assertEquals(2, progressDto.contentDefinitionVersion)
        assertEquals(false, progressDto.legacyEvidence)
        assertEquals(attempt.attemptId, attemptDto.attemptId)
        assertEquals(attempt.syncId, attemptDto.syncId)
        assertEquals(listOf("a", "z"), attemptDto.metadata.keys.toList())

        val encodedAttempt = Json.encodeToString(attemptDto)
        assertTrue(encodedAttempt.contains("\"metadata\":{\"a\":\"first\",\"z\":\"last\"}"))
    }
}
