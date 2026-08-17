package be.saikono.poincare.repository

import be.saikono.poincare.data.local.ProfileDao
import be.saikono.poincare.data.local.ProfileEntity
import be.saikono.poincare.model.ChildProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ProfileRepository {
    fun getAllProfiles(): Flow<List<ChildProfile>>
    fun getProfileById(id: String): Flow<ChildProfile?>
    suspend fun saveProfile(profile: ChildProfile)
    suspend fun deleteProfile(profile: ChildProfile)
}

class LocalProfileRepository(private val profileDao: ProfileDao) : ProfileRepository {
    override fun getAllProfiles(): Flow<List<ChildProfile>> =
        profileDao.getAllProfiles().map { entities -> entities.map { it.toModel() } }

    override fun getProfileById(id: String): Flow<ChildProfile?> =
        profileDao.getProfileByIdFlow(id).map { it?.toModel() }

    override suspend fun saveProfile(profile: ChildProfile) = profileDao.insertProfile(profile.toEntity())
    override suspend fun deleteProfile(profile: ChildProfile) = profileDao.deleteProfile(profile.toEntity())
}

private fun ProfileEntity.toModel() = ChildProfile(
    id = id,
    name = name,
    avatarId = avatarId,
    rocketColorId = rocketColorId,
    birthYear = birthYear,
    appLanguage = appLanguage,
    exerciseLanguage = exerciseLanguage,
    supportLanguage = supportLanguage,
    speechLanguage = speechLanguage,
    readingLevel = readingLevel,
    writingLevel = writingLevel,
    mathLevel = mathLevel,
    geometryLevel = geometryLevel,
    worldKnowledgeLevel = worldKnowledgeLevel,
    puzzleLevel = puzzleLevel,
    selectedGrades = selectedGrades,
    hiddenMainCategories = hiddenMainCategories,
    hiddenSubcategories = hiddenSubcategories,
    showUnassignedExercises = showUnassignedExercises,
    giftedModeEnabled = giftedModeEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun ChildProfile.toEntity() = ProfileEntity(
    id = id,
    name = name,
    avatarId = avatarId,
    rocketColorId = rocketColorId,
    birthYear = birthYear,
    appLanguage = appLanguage,
    exerciseLanguage = exerciseLanguage,
    supportLanguage = supportLanguage,
    speechLanguage = speechLanguage,
    readingLevel = readingLevel,
    writingLevel = writingLevel,
    mathLevel = mathLevel,
    geometryLevel = geometryLevel,
    worldKnowledgeLevel = worldKnowledgeLevel,
    puzzleLevel = puzzleLevel,
    selectedGrades = selectedGrades,
    hiddenMainCategories = hiddenMainCategories,
    hiddenSubcategories = hiddenSubcategories,
    showUnassignedExercises = showUnassignedExercises,
    giftedModeEnabled = giftedModeEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt
)
