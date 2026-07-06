package be.saikono.poincare.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.saikono.poincare.data.audio.AudioService
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.data.sync.SyncManager
import be.saikono.poincare.model.*
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.repository.ProgressRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ReadingUiState(
    val currentExercise: ExerciseContent? = null,
    val selectedOption: String? = null,
    val builtWord: String = "",
    val isCorrect: Boolean? = null,
    val isWrong: Boolean = false,
    val isFinished: Boolean = false,
    val showFeedback: Boolean = false,
    val isLoading: Boolean = true,
    val currentProfileId: String? = null,
    val exerciseLanguage: String = "nl",
    val supportLanguage: String = "nl"
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReadingViewModel(
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val syncManager: SyncManager,
    private val audioService: AudioService,
    private val profileRepository: be.saikono.poincare.repository.ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadingUiState())
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    private var exercises: List<ExerciseContent> = emptyList()
    private var currentIndex = 0
    private var startTime = 0L
    private var translationsUsed = 0
    private var selectedSubcategoryId: String? = null
    private var selectedDifficultyStars: Int? = null

    init {
        observeProfile()
    }

    private fun observeProfile() {
        viewModelScope.launch {
            settingsDataStore.selectedProfileId
                .flatMapLatest { id ->
                    if (id == null) flowOf(null)
                    else profileRepository.getProfileById(id)
                }
                .collect { profile ->
                    _uiState.update { it.copy(
                        currentProfileId = profile?.id,
                        exerciseLanguage = profile?.exerciseLanguage ?: "nl-BE",
                        supportLanguage = profile?.supportLanguage ?: "nl-BE"
                    ) }
                    
                    if (exercises.isEmpty() && profile != null) {
                        loadContentForProfile(profile)
                    }
                }
        }
    }

    private fun loadContentForProfile(profile: ChildProfile) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Fetch exercises for the child's reading level
            val level = profile.readingLevel
            val loadedExercises = level.toGrade()?.let { grade ->
                val domainExercises = contentRepository.getExercises(LearningDomain.READING, level)
                selectedSubcategoryId?.let { subcategoryId ->
                    contentRepository.getExercises(subcategoryId = subcategoryId, grade = grade, difficultyStars = selectedDifficultyStars)
                }?.ifEmpty { domainExercises } ?: domainExercises
            }.orEmpty()
            
            exercises = orderUnfinishedFirst(loadedExercises, profile.id)
            
            if (exercises.isNotEmpty()) {
                startExercise(0)
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setSubcategoryId(subcategoryId: String?) {
        selectedSubcategoryId = subcategoryId
        viewModelScope.launch {
            val profileId = _uiState.value.currentProfileId
            val profile = profileId?.let { profileRepository.getProfileById(it).first() }
            if (profile != null) loadContentForProfile(profile)
        }
    }

    fun setDifficultyStars(stars: Int?) {
        selectedDifficultyStars = stars?.coerceIn(1, 3)
    }

    private fun startExercise(index: Int) {
        currentIndex = index
        startTime = System.currentTimeMillis()
        translationsUsed = 0
        val exercise = exercises[index]
        _uiState.update { 
            it.copy(
                currentExercise = exercise,
                selectedOption = null,
                builtWord = "",
                isCorrect = null,
                isWrong = false,
                isFinished = false,
                showFeedback = false,
                isLoading = false
            )
        }
        if (exercise.inputMode == InputMode.LISTEN_AND_SELECT) playListeningAudio(autoPlay = true)
    }

    fun playListeningAudio(autoPlay: Boolean = false) {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        val audioText = exercise.audioTextFor(state.exerciseLanguage)
        val audioLocale = exercise.audioLocaleFor(state.exerciseLanguage)
        val audioId = exercise.audioIdFor(state.exerciseLanguage) ?: exercise.id
        val fallback = audioText ?: exercise.speechText?.get(state.exerciseLanguage) ?: exercise.localizedAnswer(state.exerciseLanguage)
        if (autoPlay) {
            audioService.playExerciseAudioDelayed(
                exerciseId = audioId,
                textFallback = fallback,
                locale = audioLocale
            )
        } else {
            audioService.playExerciseAudio(
                exerciseId = audioId,
                textFallback = fallback,
                locale = audioLocale
            )
        }
    }

    private fun ExerciseContent.audioTextFor(locale: String): String? = localizedData("audioText", locale)
    private fun ExerciseContent.audioIdFor(locale: String): String? = localizedData("audioId", locale)
    private fun ExerciseContent.audioLocaleFor(locale: String): String = localizedData("audioTtsLanguage", locale) ?: locale

    fun playQuestion() {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        audioService.speak(exercise.localizedPromptTts(state.exerciseLanguage), state.exerciseLanguage)
    }

    fun stopAudio() {
        audioService.stopPlayback()
    }

    fun onOptionSelected(option: String) {
        if (_uiState.value.isCorrect == true) return
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        if (option.isWholeInteger()) {
            _uiState.update { it.copy(selectedOption = option) }
            checkAnswer(option)
            return
        }
        if (state.selectedOption != option) {
            _uiState.update { it.copy(selectedOption = option, isCorrect = null, isWrong = false, showFeedback = false) }
            audioService.speak(exercise.localizedOptionSpeech(option, state.exerciseLanguage), exercise.optionSpeechLocale(state.exerciseLanguage))
            return
        }
        _uiState.update { it.copy(selectedOption = option) }
        checkAnswer(option)
    }

    fun onLetterAdded(letter: String) {
        if (_uiState.value.isCorrect == true) return
        val newWord = _uiState.value.builtWord + letter
        _uiState.update { it.copy(builtWord = newWord) }
        
        val exercise = _uiState.value.currentExercise ?: return
        if (newWord.length >= exercise.localizedAnswer(_uiState.value.exerciseLanguage).length) {
            checkAnswer(newWord)
        }
    }

    fun onTranslationUsed() {
        translationsUsed++
    }

    private fun checkAnswer(answer: String) {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        val isCorrect = normalizeAnswer(answer) == normalizeAnswer(exercise.localizedAnswer(state.exerciseLanguage))
        val duration = System.currentTimeMillis() - startTime

        _uiState.update { it.copy(isCorrect = isCorrect, isWrong = !isCorrect, showFeedback = true) }

        if (isCorrect) {
            saveProgress(exercise, duration, true)
            audioService.playSuccess()
        } else {
            audioService.playFailure()
            saveProgress(exercise, duration, false)
        }
    }

    private fun SkillLevel.toGrade(): Int? = if (this == SkillLevel.NOT_APPLICABLE) null else ordinal + 1

    private fun String.isWholeInteger(): Boolean = trim().matches(Regex("-?\\d+"))

    private fun ExerciseContent.optionSpeechLocale(defaultLocale: String): String = data["audioTargetLocale"] ?: defaultLocale

    private suspend fun orderUnfinishedFirst(candidates: List<ExerciseContent>, profileId: String): List<ExerciseContent> {
        val completedIds = progressRepository.getProgressByChild(profileId).first()
            .filter { it.answerCorrect }
            .map { it.exerciseId }
            .toSet()
        val unfinished = candidates.filter { it.id !in completedIds }
        return (unfinished.ifEmpty { candidates }).shuffled()
    }

    private fun saveProgress(exercise: ExerciseContent, durationMs: Long, correct: Boolean) {
        val profileId = _uiState.value.currentProfileId ?: return
        viewModelScope.launch {
            val event = ProgressEvent(
                id = UUID.randomUUID().toString(),
                childProfileId = profileId,
                exerciseId = exercise.id,
                domain = exercise.domain,
                skill = exercise.skill,
                timestamp = System.currentTimeMillis(),
                answerCorrect = correct,
                responseTimeMs = durationMs,
                rewardStampsEarned = missionPointsFor(exercise, correct),
                translationsUsed = translationsUsed,
                metadata = mapOf(
                    "progressKey" to (exercise.data["progressKey"] ?: exercise.id),
                    "subcategoryId" to (exercise.data["subcategoryId"] ?: ""),
                    "grade" to (exercise.data["grade"] ?: (exercise.level.toGrade()?.toString() ?: "")),
                    "progressState" to if (correct) "completed" else "retry_wrong"
                )
            )
            progressRepository.addProgress(event)
            syncManager.scheduleSync()
        }
    }

    fun nextExercise() {
        if (currentIndex + 1 < exercises.size) {
            _uiState.update { it.copy(isLoading = true) }
            startExercise(currentIndex + 1)
        } else {
            exercises = exercises.shuffled()
            _uiState.update { it.copy(isLoading = true) }
            startExercise(0)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioService.shutdown()
    }
}
