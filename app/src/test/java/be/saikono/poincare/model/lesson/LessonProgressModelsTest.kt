package be.saikono.poincare.model.lesson

import be.saikono.poincare.model.SyncStatus
import be.saikono.poincare.repository.LessonMetadataCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TreeMap

class LessonProgressModelsTest {
    @Test
    fun `progress identifiers are deterministic and language neutral`() {
        val first = LessonProgressIds.progressId("child-1", "math.addition.grade1.lesson1")
        val second = LessonProgressIds.progressId("child-1", "math.addition.grade1.lesson1")
        val other = LessonProgressIds.progressId("child-1", "math.addition.grade1.lesson2")

        assertEquals(first, second)
        assertNotEquals(first, other)
        assertEquals(36, first.length)
    }

    @Test
    fun `first partial attempt starts progress without completing the lesson`() {
        val result = LessonProgressCalculator.apply(
            current = null,
            request = request(score = 45, completedAt = 2_000L),
            sequenceNumber = 1
        )

        assertEquals(LessonProgressState.IN_PROGRESS, result.progress.state)
        assertEquals(LessonMasteryLevel.DEVELOPING, result.progress.masteryLevel)
        assertEquals(45, result.progress.masteryPercent)
        assertEquals(1, result.progress.attemptCount)
        assertNull(result.progress.completedAtUtc)
        assertEquals(SyncStatus.PENDING, result.progress.syncStatus)
        assertEquals(1, result.attempt.sequenceNumber)
    }

    @Test
    fun `best score and mastery never regress while latest score remains accurate`() {
        val first = LessonProgressCalculator.apply(
            current = null,
            request = request(score = 92, completedAt = 2_000L),
            sequenceNumber = 1
        )
        val second = LessonProgressCalculator.apply(
            current = first.progress,
            request = request(
                attemptId = "22222222-2222-4222-8222-222222222222",
                score = 30,
                startedAt = 3_000L,
                completedAt = 4_000L
            ),
            sequenceNumber = 2
        )

        assertEquals(LessonProgressState.COMPLETED, second.progress.state)
        assertEquals(LessonMasteryLevel.MASTERED, second.progress.masteryLevel)
        assertEquals(92, second.progress.bestScorePercent)
        assertEquals(92, second.progress.masteryPercent)
        assertEquals(30, second.progress.latestScorePercent)
        assertEquals(2, second.progress.attemptCount)
        assertEquals(2L, second.progress.revision)
        assertEquals(2_000L, second.progress.completedAtUtc)
    }

    @Test
    fun `metadata codec serializes a concrete tree map without polymorphic lookup`() {
        val metadata = TreeMap<String, String>().apply {
            put("z", "last")
            put("a", "first")
        }

        val encoded = LessonMetadataCodec.encode(metadata)
        val decoded = LessonMetadataCodec.decode(encoded)

        assertEquals("{\"a\":\"first\",\"z\":\"last\"}", encoded)
        assertEquals(listOf("a", "z"), decoded.keys.toList())
        assertEquals(metadata, decoded)
    }

    @Test
    fun `invalid attempt identifiers scores and thresholds are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            request(attemptId = "not-a-uuid").validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            request(score = 101).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            request(completionThreshold = 90, masteryThreshold = 80).validate()
        }
    }

    @Test
    fun `calculator refuses unit changes for existing lesson progress`() {
        val first = LessonProgressCalculator.apply(null, request(score = 70), 1)
        val changed = request(
            attemptId = "33333333-3333-4333-8333-333333333333",
            unitId = "math.other-unit",
            score = 80,
            startedAt = 3_000L,
            completedAt = 4_000L
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            LessonProgressCalculator.apply(first.progress, changed, 2)
        }
        assertTrue(error.message.orEmpty().contains("unitId changed"))
    }

    private fun request(
        attemptId: String = "11111111-1111-4111-8111-111111111111",
        unitId: String = "math.addition.grade1",
        score: Int = 75,
        startedAt: Long = 1_000L,
        completedAt: Long = 2_000L,
        completionThreshold: Int = 70,
        masteryThreshold: Int = 85
    ): RecordLessonAttemptRequest = RecordLessonAttemptRequest(
        attemptId = attemptId,
        childProfileId = "child-1",
        unitId = unitId,
        lessonId = "math.addition.grade1.lesson1",
        startedAtUtc = startedAt,
        completedAtUtc = completedAt,
        scorePercent = score,
        correctCount = score / 10,
        questionCount = 10,
        durationMs = completedAt - startedAt,
        completionThresholdPercent = completionThreshold,
        masteryThresholdPercent = masteryThreshold,
        generatorSeed = 42L,
        contentVersion = "1.0.0",
        metadata = mapOf("locale" to "nl-BE")
    )
}
