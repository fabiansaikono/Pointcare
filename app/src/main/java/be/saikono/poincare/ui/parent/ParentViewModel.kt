package be.saikono.poincare.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.data.settings.VoiceSettings
import be.saikono.poincare.data.settings.normalizedTeacherId
import be.saikono.poincare.model.*
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.ProgressRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ParentDashboardUiState(
    val currentProfile: ChildProfile? = null,
    val teacherId: String = "",
    val animationSettings: AnimationSettings = AnimationSettings(),
    val parentPin: String? = null,
    val pendingSyncCount: Int = 0,
    val lessonMasteryThresholdPercent: Int = 75,
    val lessonMasteryThresholdOverrides: Map<LearningDomain, Int> = emptyMap(),
    val isLoading: Boolean = true
)

data class ParentDashboardDraft(
    val profile: ChildProfile,
    val profileName: String,
    val birthYearText: String,
    val teacherIdText: String,
    val animationSettings: AnimationSettings,
    val lessonMasteryThresholdPercent: Int,
    val lessonMasteryThresholdOverrides: Map<LearningDomain, Int>
) {
    val isValid: Boolean
        get() = profileName.isNotBlank() && birthYearText.toIntOrNull() != null

    fun persistedProfile(): ChildProfile? = birthYearText.toIntOrNull()?.let { birthYear ->
        profile.copy(name = profileName.trim(), birthYear = birthYear)
    }

    fun persistedTeacherId(): String = normalizedTeacherId(teacherIdText)
}

internal fun ParentDashboardUiState.toDraft(): ParentDashboardDraft? = currentProfile?.let { profile ->
    ParentDashboardDraft(
        profile = profile,
        profileName = profile.name,
        birthYearText = profile.birthYear.toString(),
        teacherIdText = teacherId,
        animationSettings = animationSettings,
        lessonMasteryThresholdPercent = lessonMasteryThresholdPercent,
        lessonMasteryThresholdOverrides = lessonMasteryThresholdOverrides
    )
}

internal fun parentPinMatches(enteredPin: String, configuredPin: String?): Boolean =
    enteredPin == (configuredPin ?: "1234")

internal class SelectedGradeUpdateCoordinator(
    private val profileRepository: ProfileRepository
) {
    private val updateMutex = Mutex()

    suspend fun update(profileId: String, domain: LearningDomain, grades: Set<Int>) {
        updateMutex.withLock {
            val latestProfile = profileRepository.getProfileById(profileId).first() ?: return@withLock
            profileRepository.saveProfile(
                latestProfile.withSelectedGrades(domain, grades).copy(updatedAt = System.currentTimeMillis())
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ParentViewModel(
    private val profileRepository: ProfileRepository,
    private val progressRepository: ProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val audioService: be.saikono.poincare.data.audio.AudioService
) : ViewModel() {

    private val selectedGradeUpdates = SelectedGradeUpdateCoordinator(profileRepository)

    private val _uiState = MutableStateFlow(ParentDashboardUiState())
    val uiState: StateFlow<ParentDashboardUiState> = _uiState.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<android.speech.tts.Voice>>(emptyList())
    val availableVoices = _availableVoices.asStateFlow()

    fun isParentPinCorrect(pin: String): Boolean = parentPinMatches(pin, _uiState.value.parentPin)

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
                        combine(
                            profileRepository.getProfileById(profileId),
                            settingsDataStore.getTeacherId(profileId)
                        ) { profile, teacherId -> profile to teacherId },
                        settingsDataStore.animationSettings,
                        settingsDataStore.parentPin,
                        progressRepository.getProgressByChild(profileId).map { events ->
                            events.count { it.syncStatus == SyncStatus.PENDING }
                        },
                        settingsDataStore.getLessonMasteryThresholdSettings(profileId)
                    ) { profileAndTeacher, animations, parentPin, pendingCount, masterySettings ->
                        ParentDashboardUiState(
                            currentProfile = profileAndTeacher.first,
                            teacherId = profileAndTeacher.second,
                            animationSettings = animations,
                            parentPin = parentPin,
                            pendingSyncCount = pendingCount,
                            lessonMasteryThresholdPercent = masterySettings.defaultPercent,
                            lessonMasteryThresholdOverrides = masterySettings.overrides,
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

    fun updateSelectedGrades(domain: LearningDomain, grades: Set<Int>) {
        val profileId = _uiState.value.currentProfile?.id ?: return
        viewModelScope.launch {
            selectedGradeUpdates.update(profileId, domain, grades)
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
            val profileWithGrade = if (enabled && profile.selectedGradesFor(domain).isEmpty()) {
                profile.withSelectedGrades(domain, setOf(1))
            } else {
                profile
            }
            profileRepository.saveProfile(
                profileWithGrade.copy(hiddenMainCategories = hiddenCategories, updatedAt = System.currentTimeMillis())
            )
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

    fun updateLessonMasteryThreshold(percent: Int) {
        val profileId = _uiState.value.currentProfile?.id ?: return
        viewModelScope.launch {
            settingsDataStore.setLessonMasteryThresholdPercent(profileId, percent)
        }
    }

    fun updateLessonMasteryThresholdOverrideEnabled(domain: LearningDomain, enabled: Boolean) {
        val profileId = _uiState.value.currentProfile?.id ?: return
        val initialPercent = _uiState.value.lessonMasteryThresholdOverrides[domain]
            ?: _uiState.value.lessonMasteryThresholdPercent
        viewModelScope.launch {
            settingsDataStore.setLessonMasteryThresholdOverride(
                profileId = profileId,
                domain = domain,
                percent = initialPercent.takeIf { enabled }
            )
        }
    }

    fun updateLessonMasteryThresholdForDomain(domain: LearningDomain, percent: Int) {
        val profileId = _uiState.value.currentProfile?.id ?: return
        if (domain !in _uiState.value.lessonMasteryThresholdOverrides) return
        viewModelScope.launch {
            settingsDataStore.setLessonMasteryThresholdOverride(profileId, domain, percent)
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

    fun saveDashboard(draft: ParentDashboardDraft, onComplete: (Boolean) -> Unit = {}) {
        val profile = draft.persistedProfile() ?: return onComplete(false)
        viewModelScope.launch {
            val saved = runCatching {
                settingsDataStore.setAppLanguage(profile.appLanguage)
                profileRepository.saveProfile(profile.copy(updatedAt = System.currentTimeMillis()))
                settingsDataStore.setTeacherId(profile.id, draft.persistedTeacherId())
                settingsDataStore.updateAnimationSettings(draft.animationSettings)
                settingsDataStore.setLessonMasteryThresholdPercent(
                    profile.id,
                    draft.lessonMasteryThresholdPercent
                )
                LearningDomain.entries.forEach { domain ->
                    settingsDataStore.setLessonMasteryThresholdOverride(
                        profileId = profile.id,
                        domain = domain,
                        percent = draft.lessonMasteryThresholdOverrides[domain]
                    )
                }
            }.isSuccess
            onComplete(saved)
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
