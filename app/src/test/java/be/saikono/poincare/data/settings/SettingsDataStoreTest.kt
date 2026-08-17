package be.saikono.poincare.data.settings

import be.saikono.poincare.model.LearningDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDataStoreTest {
    @Test
    fun `teacher id is trimmed and limited before persistence`() {
        assertEquals("teacher-42", normalizedTeacherId("  teacher-42  "))
        assertEquals(64, normalizedTeacherId("x".repeat(80)).length)
    }

    @Test
    fun `lesson mastery threshold is clamped and rounded to five percent`() {
        assertEquals(50, normalizedLessonMasteryThresholdPercent(42))
        assertEquals(75, normalizedLessonMasteryThresholdPercent(73))
        assertEquals(80, normalizedLessonMasteryThresholdPercent(78))
        assertEquals(100, normalizedLessonMasteryThresholdPercent(104))
    }

    @Test
    fun `category override replaces the general threshold only for that category`() {
        val settings = LessonMasteryThresholdSettings(
            defaultPercent = 75,
            overrides = mapOf(LearningDomain.MATH to 85)
        )

        assertEquals(85, settings.effectivePercent(LearningDomain.MATH))
        assertEquals(75, settings.effectivePercent(LearningDomain.READING))
        assertEquals(75, settings.effectivePercent(LearningDomain.GEOMETRY))
    }
}
