package be.saikono.poincare.domain.curriculum.v2

import kotlin.math.floor

enum class LessonGateStateV2 {
    NOT_STARTED,
    IN_PROGRESS,
    READY_FOR_TEST,
    PASSED,
    MASTERED,
    PASSED_LEGACY
}

enum class LessonEvidenceSourceV2 {
    PRACTISE,
    APPLY_DEMONSTRATE,
    MASTERY_TEST,
    ADULT_ASSESSMENT,
    LEGACY_COMPLETION
}

data class LessonAssessmentEvidenceV2(
    val source: LessonEvidenceSourceV2,
    val correctCount: Int,
    val itemCount: Int,
    val hintsUsed: Int,
    val correctByMasteryTag: Map<String, Int> = emptyMap(),
    val explicitAdultAssessmentValid: Boolean = false,
    val retentionCheck: Boolean = false
) {
    val correctRatio: Double get() = if (itemCount == 0) 0.0 else correctCount.toDouble() / itemCount
}

data class LessonGateDecisionV2(
    val state: LessonGateStateV2,
    val unlocksDependants: Boolean,
    val reasons: List<String>
)

object LessonStatusServiceV2 {
    fun evaluate(
        currentState: LessonGateStateV2,
        policy: LessonGatePolicyV2,
        evidence: LessonAssessmentEvidenceV2,
        adultAssessmentAllowed: Boolean = false
    ): LessonGateDecisionV2 {
        if (currentState == LessonGateStateV2.PASSED_LEGACY ||
            evidence.source == LessonEvidenceSourceV2.LEGACY_COMPLETION
        ) return decision(LessonGateStateV2.PASSED_LEGACY, "Legacy completion remains unlocked.")

        if (currentState in setOf(LessonGateStateV2.PASSED, LessonGateStateV2.MASTERED)) {
            val retainedState = if (evidence.retentionCheck && passes(policy, evidence).isEmpty()) {
                LessonGateStateV2.MASTERED
            } else currentState
            return decision(retainedState, "A passed lesson is never relocked.")
        }

        val validGateEvidence = evidence.source == LessonEvidenceSourceV2.MASTERY_TEST ||
            (adultAssessmentAllowed && evidence.source == LessonEvidenceSourceV2.ADULT_ASSESSMENT &&
                evidence.explicitAdultAssessmentValid)
        if (!validGateEvidence) {
            val next = if (evidence.source == LessonEvidenceSourceV2.PRACTISE ||
                evidence.source == LessonEvidenceSourceV2.APPLY_DEMONSTRATE
            ) LessonGateStateV2.READY_FOR_TEST else LessonGateStateV2.IN_PROGRESS
            return LessonGateDecisionV2(
                state = next,
                unlocksDependants = false,
                reasons = listOf("Practice and application are diagnostic; only valid assessment evidence can pass.")
            )
        }

        val failures = passes(policy, evidence)
        if (failures.isNotEmpty()) {
            return LessonGateDecisionV2(LessonGateStateV2.READY_FOR_TEST, false, failures)
        }
        val state = if (evidence.retentionCheck && policy.retentionCheckRequiredForMastered) {
            LessonGateStateV2.MASTERED
        } else LessonGateStateV2.PASSED
        return decision(state, "Assessment gate passed.")
    }

    fun unlocksDependants(state: LessonGateStateV2): Boolean = state in setOf(
        LessonGateStateV2.PASSED,
        LessonGateStateV2.MASTERED,
        LessonGateStateV2.PASSED_LEGACY
    )

    /**
     * One prerequisite policy shared by every learner-facing navigation and the
     * lesson player. A prerequisite that is filtered out of the current view is
     * still a real prerequisite and therefore does not become implicitly passed.
     */
    fun prerequisitesSatisfied(
        prerequisiteLessonIds: Collection<String>,
        gateStateForLesson: (String) -> LessonGateStateV2?
    ): Boolean = prerequisiteLessonIds.all { prerequisiteId ->
        gateStateForLesson(prerequisiteId)?.let(::unlocksDependants) == true
    }

    private fun passes(
        policy: LessonGatePolicyV2,
        evidence: LessonAssessmentEvidenceV2
    ): List<String> = buildList {
        if (evidence.itemCount < policy.minimumIndependentItems) {
            add("At least ${policy.minimumIndependentItems} independent items are required.")
        }
        if (evidence.correctRatio < policy.minimumCorrectRatio) {
            add("Correct ratio is below ${policy.minimumCorrectRatio}.")
        }
        if (evidence.hintsUsed > policy.maximumHints) {
            add("Hints exceed the allowed maximum of ${policy.maximumHints}.")
        }
        policy.requiredMasteryTags.forEach { tag ->
            if ((evidence.correctByMasteryTag[tag] ?: 0) < policy.criticalTagMinimumCorrect) {
                add("Required mastery tag has insufficient evidence: $tag")
            }
        }
    }

    private fun decision(state: LessonGateStateV2, reason: String) = LessonGateDecisionV2(
        state = state,
        unlocksDependants = unlocksDependants(state),
        reasons = listOf(reason)
    )
}

data class SkillErrorMemoryV2(
    val masteryTag: String,
    val wrongCount: Int,
    val consecutiveCorrect: Int,
    val nextReviewAtUtc: Long,
    val resolved: Boolean,
    val lastItemFingerprint: String
)

object ErrorRepetitionSchedulerV2 {
    private const val DAY_MS = 86_400_000L

    fun recordWrong(
        existing: SkillErrorMemoryV2?,
        masteryTag: String,
        itemFingerprint: String,
        nowUtc: Long,
        policy: ErrorRepetitionPolicyV2
    ): SkillErrorMemoryV2 {
        val wrongCount = (existing?.wrongCount ?: 0) + 1
        val intervalIndex = (wrongCount - 1).coerceAtMost(policy.reviewIntervalsDays.lastIndex)
        return SkillErrorMemoryV2(
            masteryTag = masteryTag,
            wrongCount = wrongCount,
            consecutiveCorrect = 0,
            nextReviewAtUtc = nowUtc + policy.reviewIntervalsDays[intervalIndex] * DAY_MS,
            resolved = false,
            lastItemFingerprint = itemFingerprint
        )
    }

    fun recordCorrect(
        existing: SkillErrorMemoryV2,
        nowUtc: Long,
        policy: ErrorRepetitionPolicyV2
    ): SkillErrorMemoryV2 {
        val consecutive = existing.consecutiveCorrect + 1
        val resolved = consecutive >= policy.resolveAfterConsecutiveCorrect
        return existing.copy(
            consecutiveCorrect = consecutive,
            nextReviewAtUtc = if (resolved) nowUtc else existing.nextReviewAtUtc,
            resolved = resolved
        )
    }

    fun maximumInjectedItems(setSize: Int, policy: ErrorRepetitionPolicyV2): Int =
        floor(setSize * policy.maxInjectedShare).toInt()

    fun eligibleForInjection(
        memories: List<SkillErrorMemoryV2>,
        nowUtc: Long,
        currentItemFingerprint: String,
        setSize: Int,
        policy: ErrorRepetitionPolicyV2
    ): List<SkillErrorMemoryV2> = if (!policy.enabled) emptyList() else memories
        .asSequence()
        .filter { !it.resolved && it.nextReviewAtUtc <= nowUtc }
        .filter { !policy.repeatConceptNotExactItem || it.lastItemFingerprint != currentItemFingerprint }
        .sortedBy { it.nextReviewAtUtc }
        .take(maximumInjectedItems(setSize, policy))
        .toList()
}
