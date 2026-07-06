package be.saikono.poincare.domain.adaptive

import be.saikono.poincare.model.ProgressEvent

class AdaptiveDifficultyEngine {

    fun analyzePerformance(events: List<ProgressEvent>): PerformanceMetrics {
        if (events.isEmpty()) return PerformanceMetrics(0f, 0, 0, 0)

        val total = events.size
        val correct = events.count { it.answerCorrect }
        val totalTime = events.sumOf { it.responseTimeMs }
        val totalHints = events.sumOf { it.hintsUsed }
        val totalRetries = events.sumOf { it.retryCount }

        return PerformanceMetrics(
            accuracy = correct.toFloat() / total,
            averageResponseTimeMs = totalTime / total,
            totalHintsUsed = totalHints,
            totalRetries = totalRetries
        )
    }

    fun getRecommendation(metrics: PerformanceMetrics, giftedMode: Boolean): AdaptiveRecommendation {
        return when {
            // High accuracy and high speed (low response time)
            metrics.accuracy >= 0.9f && metrics.averageResponseTimeMs < 5000 -> {
                if (giftedMode) AdaptiveRecommendation.INCREASE_DIFFICULTY
                else AdaptiveRecommendation.OFFER_PUZZLE
            }
            
            // Good accuracy
            metrics.accuracy >= 0.8f -> {
                AdaptiveRecommendation.INDEPENDENT_PRACTICE
            }
            
            // Medium accuracy - needs guidance
            metrics.accuracy >= 0.6f -> {
                AdaptiveRecommendation.GUIDED_PRACTICE
            }
            
            // Low accuracy - needs to step back or repeat
            else -> {
                if (metrics.totalRetries > 3) AdaptiveRecommendation.DECREASE_DIFFICULTY
                else AdaptiveRecommendation.REPEAT_PRACTICE
            }
        }
    }
}
