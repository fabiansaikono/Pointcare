package be.saikono.poincare.ui.story

import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LearningDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryGradeScopeTest {
    @Test
    fun `story grades come only from the parent dashboard profile settings`() {
        val profile = ChildProfile(
            id = "story-child",
            name = "Story",
            avatarId = "1",
            rocketColorId = "1",
            birthYear = 2018,
            selectedGrades = mapOf(LearningDomain.READING.name to listOf(1, 2, 4))
        )

        assertEquals(listOf(1, 2, 4), storyGradesConfiguredInParentDashboard(profile))
    }
}
