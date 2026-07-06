package be.saikono.poincare.domain.adaptive

import be.saikono.poincare.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveDifficultyEngineTest {

    private val engine = AdaptiveDifficultyEngine()

    @Test
    fun `test gifted mode recommendation for high accuracy and speed`() {
        val metrics = PerformanceMetrics(
            accuracy = 0.95f,
            averageResponseTimeMs = 3000,
            totalHintsUsed = 0,
            totalRetries = 0
        )
        
        val recommendation = engine.getRecommendation(metrics, giftedMode = true)
        assertEquals(AdaptiveRecommendation.INCREASE_DIFFICULTY, recommendation)
    }

    @Test
    fun `test puzzle offer for high accuracy and speed without gifted mode`() {
        val metrics = PerformanceMetrics(
            accuracy = 0.95f,
            averageResponseTimeMs = 3000,
            totalHintsUsed = 0,
            totalRetries = 0
        )
        
        val recommendation = engine.getRecommendation(metrics, giftedMode = false)
        assertEquals(AdaptiveRecommendation.OFFER_PUZZLE, recommendation)
    }

    @Test
    fun `test guided practice recommendation for medium accuracy`() {
        val metrics = PerformanceMetrics(
            accuracy = 0.7f,
            averageResponseTimeMs = 8000,
            totalHintsUsed = 2,
            totalRetries = 1
        )
        
        val recommendation = engine.getRecommendation(metrics, giftedMode = false)
        assertEquals(AdaptiveRecommendation.GUIDED_PRACTICE, recommendation)
    }

    @Test
    fun `test repeat practice recommendation for low accuracy`() {
        val metrics = PerformanceMetrics(
            accuracy = 0.4f,
            averageResponseTimeMs = 12000,
            totalHintsUsed = 5,
            totalRetries = 2
        )
        
        val recommendation = engine.getRecommendation(metrics, giftedMode = false)
        assertEquals(AdaptiveRecommendation.REPEAT_PRACTICE, recommendation)
    }

    @Test
    fun `test decrease difficulty for persistent low accuracy and high retries`() {
        val metrics = PerformanceMetrics(
            accuracy = 0.3f,
            averageResponseTimeMs = 15000,
            totalHintsUsed = 10,
            totalRetries = 5
        )
        
        val recommendation = engine.getRecommendation(metrics, giftedMode = false)
        assertEquals(AdaptiveRecommendation.DECREASE_DIFFICULTY, recommendation)
    }

    @Test
    fun `test independent practice for good but not perfect accuracy`() {
        val metrics = PerformanceMetrics(
            accuracy = 0.85f,
            averageResponseTimeMs = 6000,
            totalHintsUsed = 1,
            totalRetries = 1
        )
        
        val recommendation = engine.getRecommendation(metrics, giftedMode = false)
        assertEquals(AdaptiveRecommendation.INDEPENDENT_PRACTICE, recommendation)
    }
}
