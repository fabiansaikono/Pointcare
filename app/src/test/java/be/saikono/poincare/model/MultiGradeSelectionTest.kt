package be.saikono.poincare.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiGradeSelectionTest {
    private fun profile(
        readingLevel: SkillLevel = SkillLevel.THIRD_GRADE,
        selectedGrades: Map<String, List<Int>> = emptyMap()
    ) = ChildProfile(
        id = "child-1",
        name = "Test",
        avatarId = "avatar",
        rocketColorId = "rocket",
        birthYear = 2018,
        readingLevel = readingLevel,
        selectedGrades = selectedGrades
    )

    @Test
    fun selectedGradesForFallsBackToLegacySingleGrade() {
        assertEquals(listOf(3), profile().selectedGradesFor(LearningDomain.READING))
    }

    @Test
    fun explicitGradesAreNormalizedAndSorted() {
        val result = profile(
            selectedGrades = mapOf(LearningDomain.READING.name to listOf(6, 2, 2, 0, 7, 4))
        ).selectedGradesFor(LearningDomain.READING)

        assertEquals(listOf(2, 4, 6), result)
    }

    @Test
    fun withSelectedGradesKeepsAtLeastOneGradeAndUpdatesLegacyPrimary() {
        val updated = profile().withSelectedGrades(LearningDomain.READING, emptySet())

        assertEquals(listOf(3), updated.selectedGradesFor(LearningDomain.READING))
        assertEquals(SkillLevel.THIRD_GRADE, updated.readingLevel)
    }

    @Test
    fun withSelectedGradesUsesLowestSelectedGradeAsCompatiblePrimaryLevel() {
        val updated = profile().withSelectedGrades(LearningDomain.READING, setOf(5, 2, 4))

        assertEquals(listOf(2, 4, 5), updated.selectedGradesFor(LearningDomain.READING))
        assertEquals(SkillLevel.SECOND_GRADE, updated.readingLevel)
    }
}
