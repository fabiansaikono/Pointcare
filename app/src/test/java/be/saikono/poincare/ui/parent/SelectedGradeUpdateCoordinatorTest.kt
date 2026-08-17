package be.saikono.poincare.ui.parent

import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.selectedGradesFor
import be.saikono.poincare.repository.ProfileRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedGradeUpdateCoordinatorTest {

    @Test
    fun rapidUpdatesForDifferentDomainsPreserveBothSelections() = runBlocking {
        val initialProfile = ChildProfile(
            id = "child",
            name = "Child",
            avatarId = "1",
            rocketColorId = "1",
            birthYear = 2018,
            selectedGrades = LearningDomain.entries.associate { it.name to listOf(1) }
        )
        val repository = DelayedProfileRepository(initialProfile)
        val coordinator = SelectedGradeUpdateCoordinator(repository)

        coroutineScope {
            launch { coordinator.update(initialProfile.id, LearningDomain.READING, setOf(1, 2)) }
            launch { coordinator.update(initialProfile.id, LearningDomain.MATH, setOf(1, 2)) }
        }

        val saved = repository.profile.value
        assertEquals(listOf(1, 2), saved.selectedGradesFor(LearningDomain.READING))
        assertEquals(listOf(1, 2), saved.selectedGradesFor(LearningDomain.MATH))
    }

    private class DelayedProfileRepository(initialProfile: ChildProfile) : ProfileRepository {
        val profile = MutableStateFlow(initialProfile)

        override fun getAllProfiles(): Flow<List<ChildProfile>> = profile.map(::listOf)

        override fun getProfileById(id: String): Flow<ChildProfile?> =
            profile.map { current -> current.takeIf { it.id == id } }

        override suspend fun saveProfile(profile: ChildProfile) {
            delay(20)
            this.profile.value = profile
        }

        override suspend fun deleteProfile(profile: ChildProfile) = Unit
    }
}
