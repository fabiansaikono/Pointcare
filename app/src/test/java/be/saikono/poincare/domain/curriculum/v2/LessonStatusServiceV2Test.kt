package be.saikono.poincare.domain.curriculum.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonStatusServiceV2Test {
    private val policy = LessonGatePolicyV2(
        evidenceSource = LessonGateEvidenceSourceV2.MASTERY_TEST_ONLY,
        minimumCorrectRatio = 0.8,
        minimumIndependentItems = 10,
        maximumHints = 0,
        requiredMasteryTags = listOf("a", "b"),
        criticalTagMinimumCorrect = 1,
        unlockOnPass = true
    )

    @Test
    fun `perfect practice is diagnostic and never unlocks`() {
        val decision = LessonStatusServiceV2.evaluate(
            LessonGateStateV2.IN_PROGRESS,
            policy,
            evidence(LessonEvidenceSourceV2.PRACTISE, correct = 10)
        )
        assertEquals(LessonGateStateV2.READY_FOR_TEST, decision.state)
        assertFalse(decision.unlocksDependants)
    }

    @Test
    fun `mastery test unlocks only when every gate condition passes`() {
        val passed = LessonStatusServiceV2.evaluate(
            LessonGateStateV2.READY_FOR_TEST,
            policy,
            evidence(LessonEvidenceSourceV2.MASTERY_TEST, correct = 8)
        )
        assertEquals(LessonGateStateV2.PASSED, passed.state)
        assertTrue(passed.unlocksDependants)

        val failed = LessonStatusServiceV2.evaluate(
            LessonGateStateV2.READY_FOR_TEST,
            policy,
            evidence(LessonEvidenceSourceV2.MASTERY_TEST, correct = 7)
        )
        assertEquals(LessonGateStateV2.READY_FOR_TEST, failed.state)
        assertFalse(failed.unlocksDependants)
    }

    @Test
    fun `legacy pass never relocks`() {
        val decision = LessonStatusServiceV2.evaluate(
            LessonGateStateV2.PASSED_LEGACY,
            policy,
            evidence(LessonEvidenceSourceV2.PRACTISE, correct = 0)
        )
        assertEquals(LessonGateStateV2.PASSED_LEGACY, decision.state)
        assertTrue(decision.unlocksDependants)
    }

    @Test
    fun `filtered or missing prerequisite never becomes implicitly passed`() {
        val states = mapOf("passed" to LessonGateStateV2.PASSED)

        assertTrue(
            LessonStatusServiceV2.prerequisitesSatisfied(listOf("passed"), states::get)
        )
        assertFalse(
            LessonStatusServiceV2.prerequisitesSatisfied(
                listOf("passed", "outside-active-filter"),
                states::get
            )
        )
    }

    @Test
    fun `error memory schedules concept variants and resolves after consecutive success`() {
        val errorPolicy = ErrorRepetitionPolicyV2(true, true, .25, listOf(0, 1, 3, 7), 2)
        val wrong = ErrorRepetitionSchedulerV2.recordWrong(null, "a", "exact-1", 1_000L, errorPolicy)
        assertTrue(ErrorRepetitionSchedulerV2.eligibleForInjection(
            listOf(wrong), 1_000L, "exact-2", 8, errorPolicy
        ).isNotEmpty())
        assertTrue(ErrorRepetitionSchedulerV2.eligibleForInjection(
            listOf(wrong), 1_000L, "exact-1", 8, errorPolicy
        ).isEmpty())
        val once = ErrorRepetitionSchedulerV2.recordCorrect(wrong, 2_000L, errorPolicy)
        val twice = ErrorRepetitionSchedulerV2.recordCorrect(once, 3_000L, errorPolicy)
        assertFalse(once.resolved)
        assertTrue(twice.resolved)
    }

    private fun evidence(source: LessonEvidenceSourceV2, correct: Int) = LessonAssessmentEvidenceV2(
        source = source,
        correctCount = correct,
        itemCount = 10,
        hintsUsed = 0,
        correctByMasteryTag = mapOf("a" to 1, "b" to 1)
    )
}
