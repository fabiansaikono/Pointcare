package be.saikono.poincare.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ProfileUiState(
    val profiles: List<ChildProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val parentPin: String? = null,
    val isLoading: Boolean = false,
    val loadingProgress: Int = 0,
    val loadingProfileId: String? = null,
    val loadingError: String? = null
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val settingsDataStore: SettingsDataStore,
    private val contentRepository: ContentRepository
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
                Triple(profiles, selectedId, parentPin)
            }.collect { (profiles, selectedId, parentPin) ->
                _uiState.update { current ->
                    current.copy(
                        profiles = profiles,
                        selectedProfileId = selectedId,
                        parentPin = parentPin
                    )
                }
            }
        }
    }

    fun isParentPinCorrect(pin: String): Boolean = pin == (_uiState.value.parentPin ?: DEFAULT_PARENT_PIN)

    fun selectProfile(profileId: String, onReady: () -> Unit) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            val profile = profileRepository.getProfileById(profileId).first() ?: return@launch
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingProgress = 0,
                    loadingProfileId = profileId,
                    loadingError = null
                )
            }
            try {
                contentRepository.preloadForProfile(profile) { progress ->
                    _uiState.update { state ->
                        state.copy(loadingProgress = progress.coerceIn(0, 100))
                    }
                }
                _uiState.update { it.copy(isLoading = false, loadingProgress = 100, loadingProfileId = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingProfileId = null,
                        loadingError = error.message ?: "Content kon niet volledig vooraf geladen worden."
                    )
                }
            }
            settingsDataStore.setSelectedProfileId(profileId)
            onReady()
        }
    }

    fun createProfile(
        name: String,
        birthYear: Int,
        readingLevel: SkillLevel,
        writingLevel: SkillLevel,
        mathLevel: SkillLevel,
        puzzleLevel: SkillLevel,
        showUnassignedExercises: Boolean,
        giftedMode: Boolean,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val defaultLang = settingsDataStore.appLanguage.first() ?: "nl"
            val profile = ChildProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                avatarId = "default_avatar",
                rocketColorId = "default_rocket",
                birthYear = birthYear,
                appLanguage = defaultLang,
                exerciseLanguage = defaultLang,
                supportLanguage = defaultLang,
                speechLanguage = defaultLang,
                readingLevel = readingLevel,
                writingLevel = writingLevel,
                mathLevel = mathLevel,
                puzzleLevel = puzzleLevel,
                showUnassignedExercises = showUnassignedExercises,
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
