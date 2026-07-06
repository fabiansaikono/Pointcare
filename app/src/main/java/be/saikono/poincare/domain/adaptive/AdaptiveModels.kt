package be.saikono.poincare.domain.adaptive

enum class AdaptiveRecommendation {
    REPEAT_PRACTICE,
    GUIDED_PRACTICE,
    INDEPENDENT_PRACTICE,
    OFFER_PUZZLE,
    INCREASE_DIFFICULTY,
    DECREASE_DIFFICULTY,
    OFFER_CHALLENGE_MODE
}

data class PerformanceMetrics(
    val accuracy: Float,
    val averageResponseTimeMs: Long,
    val totalHintsUsed: Int,
    val totalRetries: Int
)
