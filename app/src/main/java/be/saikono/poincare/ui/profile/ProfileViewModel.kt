package be.saikono.poincare.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.repository.ProfileRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ProfileUiState(
    val profiles: List<ChildProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val parentPin: String? = null,
    val isLoading: Boolean = false
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                profileRepository.getAllProfiles(),
                settingsDataStore.selectedProfileId,
                settingsDataStore.parentPin
            ) { profiles, selectedId, parentPin ->
                ProfileUiState(profiles = profiles, selectedProfileId = selectedId, parentPin = parentPin)
            }.collect {
                _uiState.value = it
            }
        }
    }

    fun isParentPinCorrect(pin: String): Boolean = pin == (_uiState.value.parentPin ?: DEFAULT_PARENT_PIN)

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            settingsDataStore.setSelectedProfileId(profileId)
        }
    }

    fun createProfile(
        name: String,
        birthYear: Int,
        readingLevel: SkillLevel,
        writingLevel: SkillLevel,
        mathLevel: SkillLevel,
        puzzleLevel: SkillLevel,
        giftedMode: Boolean,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val defaultLang = settingsDataStore.appLanguage.first() ?: "nl"
            val profile = ChildProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                avatarId = "default_avatar", // Placeholder for now
                rocketColorId = "default_rocket", // Placeholder for now
                birthYear = birthYear,
                appLanguage = defaultLang,
                exerciseLanguage = defaultLang,
                supportLanguage = defaultLang,
                speechLanguage = defaultLang,
                readingLevel = readingLevel,
                writingLevel = writingLevel,
                mathLevel = mathLevel,
                puzzleLevel = puzzleLevel,
                giftedModeEnabled = giftedMode
            )
            profileRepository.saveProfile(profile)
            settingsDataStore.setSelectedProfileId(profile.id)
            onComplete()
        }
    }

    private companion object {
        const val DEFAULT_PARENT_PIN = "1234"
    }
}
