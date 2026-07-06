package be.saikono.poincare.data.sync

import be.saikono.poincare.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMapperTest {

    @Test
    fun `test progress event to dto mapping`() {
        val event = ProgressEvent(
            id = "event_123",
            childProfileId = "child_456",
            exerciseId = "ex_789",
            domain = LearningDomain.MATH,
            skill = "addition",
            timestamp = 1625000000000L,
            answerCorrect = true,
            responseTimeMs = 5000L,
            rewardStampsEarned = 2,
            metadata = mapOf("test" to "data")
        )

        val dto = event.toDto()

        assertEquals(event.id, dto.eventId)
        assertEquals(event.childProfileId, dto.childProfileId)
        assertEquals(event.domain.name, dto.domain)
        assertEquals(event.responseTimeMs, dto.durationMs)
        assertEquals("data", dto.metadata["test"])
    }
}
