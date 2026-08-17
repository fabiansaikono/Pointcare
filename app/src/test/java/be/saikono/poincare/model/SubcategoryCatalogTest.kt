package be.saikono.poincare.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SubcategoryCatalogTest {
    @Test
    fun conjugationsAreHiddenWhenOnlyGradesOneAndTwoAreSelected() {
        val result = SubcategoryCatalog.applicableGrades(
            targetId = "conjugations",
            selectedGrades = listOf(1, 2),
            gradeAvailability = emptyMap()
        )

        assertEquals(emptyList<Int>(), result)
    }

    @Test
    fun conjugationBaseUsesTheUnionOfItsFocusGradeRanges() {
        val result = SubcategoryCatalog.applicableGrades(
            targetId = "conjugations",
            selectedGrades = listOf(2, 3, 4),
            gradeAvailability = emptyMap()
        )

        assertEquals(listOf(3, 4), result)
    }

    @Test
    fun individualFocusUsesItsOwnGradeRange() {
        val result = SubcategoryCatalog.applicableGrades(
            targetId = SubcategoryCatalog.fullFocusId("conjugations", "inversion"),
            selectedGrades = listOf(3, 4, 5),
            gradeAvailability = emptyMap()
        )

        assertEquals(listOf(4, 5), result)
    }

    @Test
    fun populatedRuntimeIndexHidesTargetsWithoutContent() {
        val result = SubcategoryCatalog.applicableGrades(
            targetId = "conjugations",
            selectedGrades = listOf(3, 4),
            gradeAvailability = mapOf("spelling" to setOf(3, 4))
        )

        assertEquals(emptyList<Int>(), result)
    }

    @Test
    fun runtimeAndCatalogueAvailabilityAreIntersected() {
        val targetId = SubcategoryCatalog.fullFocusId("conjugations", "first_person")
        val result = SubcategoryCatalog.applicableGrades(
            targetId = targetId,
            selectedGrades = listOf(2, 3, 4),
            gradeAvailability = mapOf(targetId to setOf(2, 4))
        )

        assertEquals(listOf(4), result)
    }

    @Test
    fun emptyRuntimeIndexKeepsUnconstrainedTargetsVisibleWhileLoading() {
        val result = SubcategoryCatalog.applicableGrades(
            targetId = "spelling",
            selectedGrades = listOf(1, 2),
            gradeAvailability = emptyMap()
        )

        assertEquals(listOf(1, 2), result)
    }

    @Test
    fun speakingTileOnlyOffersGradesWithSpeakingLessonUnits() {
        val result = SubcategoryCatalog.applicableGrades(
            targetId = "speaking",
            selectedGrades = (1..6).toList(),
            gradeAvailability = emptyMap()
        )

        assertEquals(listOf(1, 2, 6), result)
    }

    @Test
    fun statisticsTileOnlyOffersGradesWithStatisticsLessonUnits() {
        val result = SubcategoryCatalog.applicableGrades(
            targetId = "statistics",
            selectedGrades = (1..6).toList(),
            gradeAvailability = emptyMap()
        )

        assertEquals(listOf(2, 3, 4, 6), result)
    }
}
