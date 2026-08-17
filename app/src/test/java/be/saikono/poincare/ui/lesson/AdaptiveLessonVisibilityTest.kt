package be.saikono.poincare.ui.lesson

import be.saikono.poincare.domain.curriculum.CurriculumRequirement
import be.saikono.poincare.domain.curriculum.LessonDefinition
import be.saikono.poincare.domain.curriculum.LessonSection
import be.saikono.poincare.domain.curriculum.LessonSectionType
import be.saikono.poincare.domain.curriculum.LessonText
import be.saikono.poincare.domain.curriculum.manifest.CurriculumEnrichmentPolicy
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SyncStatus
import be.saikono.poincare.model.lesson.LessonMasteryLevel
import be.saikono.poincare.model.lesson.LessonProgress
import be.saikono.poincare.model.lesson.LessonProgressState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLessonVisibilityTest {
    private val targetLessonId = "lesson.target.checkpoint"
    private val remediation = lesson(
        id = "lesson.target.adaptive-support",
        tags = listOf(
            CurriculumEnrichmentPolicy.ENRICHMENT_TAG,
            CurriculumEnrichmentPolicy.REMEDIATION_TAG,
            "${CurriculumEnrichmentPolicy.TARGET_TAG_PREFIX}$targetLessonId"
        )
    )

    @Test
    fun `remediation stays hidden until its target was attempted below eighty percent`() {
        assertFalse(remediation.isVisibleForAdaptivePath(emptyMap()))
        assertFalse(remediation.isVisibleForAdaptivePath(mapOf(
            targetLessonId to progress(targetLessonId, score = 90, attempts = 1)
        )))
        assertTrue(remediation.isVisibleForAdaptivePath(mapOf(
            targetLessonId to progress(targetLessonId, score = 79, attempts = 1)
        )))
    }

    @Test
    fun `started remediation remains visible after later target improvement`() {
        val progress = mapOf(
            targetLessonId to progress(targetLessonId, score = 95, attempts = 2),
            remediation.id to progress(remediation.id, score = 70, attempts = 1)
        )
        assertTrue(remediation.isVisibleForAdaptivePath(progress))
    }

    @Test
    fun `ordinary and extension lessons are never removed by remediation filtering`() {
        assertTrue(lesson("lesson.ordinary", emptyList()).isVisibleForAdaptivePath(emptyMap()))
        assertTrue(lesson(
            "lesson.extension",
            listOf(CurriculumEnrichmentPolicy.EXTENSION_TAG)
        ).isVisibleForAdaptivePath(emptyMap()))
    }

    private fun lesson(id: String, tags: List<String>): LessonDefinition = LessonDefinition(
        id = id,
        grade = 1,
        sequence = 1,
        title = LessonText(LocalizedText("Test", "Test", "Test")),
        requirement = CurriculumRequirement.RECOMMENDED,
        learningGoalIds = listOf("goal.test"),
        sections = listOf(
            LessonSection(
                id = "$id.section",
                type = LessonSectionType.EXPLANATION,
                content = LessonText(LocalizedText("Test", "Test", "Test"))
            )
        ),
        tags = tags
    )

    private fun progress(lessonId: String, score: Int, attempts: Int): LessonProgress =
        LessonProgress(
            progressId = "progress-$lessonId",
            childProfileId = "child",
            unitId = "unit",
            lessonId = lessonId,
            state = if (score >= 70) {
                LessonProgressState.COMPLETED
            } else {
                LessonProgressState.IN_PROGRESS
            },
            masteryLevel = when {
                score >= 85 -> LessonMasteryLevel.MASTERED
                score >= 70 -> LessonMasteryLevel.PROFICIENT
                else -> LessonMasteryLevel.DEVELOPING
            },
            masteryPercent = score,
            bestScorePercent = score,
            latestScorePercent = score,
            attemptCount = attempts,
            startedAtUtc = 1L,
            completedAtUtc = 2L.takeIf { score >= 70 },
            lastActivityAtUtc = 2L,
            updatedAtUtc = 2L,
            syncId = "sync-$lessonId",
            syncStatus = SyncStatus.PENDING,
            revision = attempts.toLong()
        )
}
