package be.saikono.poincare.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.data.settings.VoiceSettings
import be.saikono.poincare.model.*
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.ProgressRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ParentDashboardUiState(
    val currentProfile: ChildProfile? = null,
    val animationSettings: AnimationSettings = AnimationSettings(),
    val parentPin: String? = null,
    val pendingSyncCount: Int = 0,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class ParentViewModel(
    private val profileRepository: ProfileRepository,
    private val progressRepository: ProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val audioService: be.saikono.poincare.data.audio.AudioService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParentDashboardUiState())
    val uiState: StateFlow<ParentDashboardUiState> = _uiState.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<android.speech.tts.Voice>>(emptyList())
    val availableVoices = _availableVoices.asStateFlow()

    init {
        loadData()
        loadVoices()
    }

    private fun loadVoices() {
        audioService.getTtsVoiceService().setOnReadyListener {
            _availableVoices.value = audioService.getTtsVoiceService().getAvailableVoices(listOf("nl-BE", "fr-BE", "fr-FR", "en"))
        }
    }

    fun getVoiceSettings(locale: String): Flow<VoiceSettings> {
        val profileId = _uiState.value.currentProfile?.id ?: return flowOf(VoiceSettings(null, 1.0f, 1.0f))
        return settingsDataStore.getVoiceSettings(profileId, locale)
    }

    fun updateVoiceSettings(locale: String, settings: VoiceSettings) {
        val profileId = _uiState.value.currentProfile?.id ?: return
        viewModelScope.launch {
            settingsDataStore.setVoiceSettings(profileId, locale, settings)
        }
    }

    fun testVoice(text: String, locale: String, settings: VoiceSettings) {
        viewModelScope.launch {
            // Temporarily apply settings to test
            if (settings.voiceId != null) audioService.getTtsVoiceService().setVoice(settings.voiceId)
            audioService.getTtsVoiceService().setPitch(settings.pitch)
            audioService.getTtsVoiceService().setSpeechRate(settings.rate)
            audioService.speak(text, locale)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            settingsDataStore.selectedProfileId
                .filterNotNull()
                .flatMapLatest { profileId ->
                    combine(
                        profileRepository.getProfileById(profileId),
                        settingsDataStore.animationSettings,
                        settingsDataStore.parentPin,
                        progressRepository.getProgressByChild(profileId).map { events ->
                            events.count { it.syncStatus == SyncStatus.PENDING }
                        }
                    ) { profile, animations, parentPin, pendingCount ->
                        ParentDashboardUiState(
                            currentProfile = profile,
                            animationSettings = animations,
                            parentPin = parentPin,
                            pendingSyncCount = pendingCount,
                            isLoading = false
                        )
                    }
                }
                .collect {
                    _uiState.value = it
                }
        }
    }

    fun updateProfileName(name: String) {
        val profile = _uiState.value.currentProfile ?: return
        viewModelScope.launch {
            profileRepository.saveProfile(profile.copy(name = name, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateBirthYear(year: Int) {
        val profile = _uiState.value.currentProfile ?: return
        viewModelScope.launch {
            profileRepository.saveProfile(profile.copy(birthYear = year, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateSkillLevel(domain: LearningDomain, level: SkillLevel) {
        val profile = _uiState.value.currentProfile ?: return
        viewModelScope.launch {
            val updatedProfile = when (domain) {
                LearningDomain.READING -> profile.copy(readingLevel = level)
                LearningDomain.WRITING -> profile.copy(writingLevel = level)
                LearningDomain.MATH -> profile.copy(mathLevel = level)
                LearningDomain.PUZZLE -> profile.copy(puzzleLevel = level)
                LearningDomain.WORLD_KNOWLEDGE -> profile.copy(worldKnowledgeLevel = level)
                LearningDomain.GEOMETRY -> profile.copy(geometryLevel = level)
            }
            profileRepository.saveProfile(updatedProfile.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateMainCategoryEnabled(domain: LearningDomain, enabled: Boolean) {
        val profile = _uiState.value.currentProfile ?: return
        viewModelScope.launch {
            val hiddenCategories = if (enabled) {
                profile.hiddenMainCategories - domain.name
            } else {
                (profile.hiddenMainCategories + domain.name).distinct()
            }
            profileRepository.saveProfile(profile.copy(hiddenMainCategories = hiddenCategories, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateSubcategoryEnabled(subcategoryId: String, enabled: Boolean) {
        val profile = _uiState.value.currentProfile ?: return
        viewModelScope.launch {
            val hiddenSubcategories = if (enabled) {
                profile.hiddenSubcategories - subcategoryId
            } else {
                (profile.hiddenSubcategories + subcategoryId).distinct()
            }
            profileRepository.saveProfile(profile.copy(hiddenSubcategories = hiddenSubcategories, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateGiftedMode(enabled: Boolean) {
        val profile = _uiState.value.currentProfile ?: return
        viewModelScope.launch {
            profileRepository.saveProfile(profile.copy(giftedModeEnabled = enabled, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateShowUnassignedExercises(enabled: Boolean) {
        val profile = _uiState.value.currentProfile ?: return
        viewModelScope.launch {
            profileRepository.saveProfile(profile.copy(showUnassignedExercises = enabled, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateAnimationSettings(settings: AnimationSettings) {
        viewModelScope.launch {
            settingsDataStore.updateAnimationSettings(settings)
        }
    }

    fun updateLanguage(type: LanguageLayer, languageCode: String) {
        val profile = _uiState.value.currentProfile ?: return
        viewModelScope.launch {
            val updatedProfile = when (type) {
                LanguageLayer.APP -> {
                    settingsDataStore.setAppLanguage(languageCode)
                    profile.copy(appLanguage = languageCode)
                }
                LanguageLayer.EXERCISE -> profile.copy(exerciseLanguage = languageCode)
                LanguageLayer.SUPPORT -> profile.copy(supportLanguage = languageCode)
                LanguageLayer.SPEECH -> profile.copy(speechLanguage = languageCode)
                LanguageLayer.ALL -> {
                    settingsDataStore.setAppLanguage(languageCode)
                    profile.copy(
                        appLanguage = languageCode,
                        exerciseLanguage = languageCode,
                        supportLanguage = languageCode,
                        speechLanguage = languageCode
                    )
                }
            }
            profileRepository.saveProfile(updatedProfile.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteCurrentProfile(onDeleted: () -> Unit) {
        val profile = _uiState.value.currentProfile ?: return
        viewModelScope.launch {
            profileRepository.deleteProfile(profile)
            val nextProfile = profileRepository.getAllProfiles().first().firstOrNull()
            settingsDataStore.setSelectedProfileId(nextProfile?.id)
            onDeleted()
        }
    }
}

enum class LanguageLayer {
    APP, EXERCISE, SUPPORT, SPEECH, ALL
}
