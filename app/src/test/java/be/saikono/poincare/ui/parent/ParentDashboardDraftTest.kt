package be.saikono.poincare.ui.parent

import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.selectedGradesFor
import be.saikono.poincare.model.withSelectedGrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentDashboardDraftTest {
    private val profile = ChildProfile(
        id = "child",
        name = "Child",
        avatarId = "1",
        rocketColorId = "1",
        birthYear = 2018
    )

    @Test
    fun draftBuffersProfileChangesUntilConvertedForPersistence() {
        val source = ParentDashboardUiState(
            currentProfile = profile,
            teacherId = "teacher-old",
            isLoading = false
        ).toDraft()!!
        val edited = source.copy(
            profile = source.profile.withSelectedGrades(LearningDomain.MATH, setOf(1, 2)),
            profileName = "  Explorer  ",
            birthYearText = "2017",
            teacherIdText = "  teacher-42  "
        )

        assertEquals("Child", profile.name)
        assertEquals(listOf(1), profile.selectedGradesFor(LearningDomain.MATH))
        assertTrue(edited.isValid)
        assertEquals("Explorer", edited.persistedProfile()!!.name)
        assertEquals(2017, edited.persistedProfile()!!.birthYear)
        assertEquals(listOf(1, 2), edited.persistedProfile()!!.selectedGradesFor(LearningDomain.MATH))
        assertEquals("teacher-42", edited.persistedTeacherId())
    }

    @Test
    fun blankNameOrInvalidBirthYearCannotBeSaved() {
        val source = ParentDashboardUiState(currentProfile = profile, isLoading = false).toDraft()!!

        assertFalse(source.copy(profileName = " ").isValid)
        assertFalse(source.copy(birthYearText = "").isValid)
    }
}
