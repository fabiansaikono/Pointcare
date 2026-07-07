package be.saikono.poincare.ui.writing

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.data.sync.SyncManager
import be.saikono.poincare.model.*
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.ProgressRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class WritingUiState(
    val currentExercise: ExerciseContent? = null,
    val userPath: List<Offset> = emptyList(),
    val isComplete: Boolean = false,
    val isFinished: Boolean = false,
    val isLoading: Boolean = true,
    val currentProfileId: String? = null,
    val exerciseLanguage: String = "nl-BE",
    val userInput: String = "",
    val isCorrect: Boolean? = null,
    val isWrong: Boolean = false,
    val showFeedback: Boolean = false
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WritingViewModel(
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val syncManager: SyncManager,
    private val audioService: be.saikono.poincare.data.audio.AudioService,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WritingUiState())
    val uiState: StateFlow<WritingUiState> = _uiState.asStateFlow()

    private var exercises: List<ExerciseContent> = emptyList()
    private var currentIndex = 0
    private var startTime = 0L
    private var selectedSubcategoryId: String? = null
    private var selectedDifficultyStars: Int? = null
    private var latestProfile: ChildProfile? = null
    private var hasContentRequest = false

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
                    latestProfile = profile
                    _uiState.update {
                        it.copy(
                            currentProfileId = profile?.id,
                            exerciseLanguage = profile?.exerciseLanguage ?: "nl-BE",
                            isLoading = hasContentRequest && profile != null
                        )
                    }

                    if (hasContentRequest && profile != null) {
                        loadContentForProfile(profile)
                    }
                }
        }
    }

    private fun loadContentForProfile(profile: ChildProfile) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val level = profile.writingLevel
            val loadedExercises = level.toGrade()?.let { grade ->
                val domainExercises = contentRepository.getExercises(LearningDomain.WRITING, level)
                selectedSubcategoryId?.let { subcategoryId ->
                    val subcategoryExercises = contentRepository.getExercises(
                        subcategoryId = subcategoryId,
                        grade = grade,
                        difficultyStars = selectedDifficultyStars
                    )
                    if (profile.showUnassignedExercises) {
                        (subcategoryExercises + domainExercises.unassignedForDifficulty(selectedDifficultyStars)).distinctBy { it.id }
                    } else {
                        subcategoryExercises
                    }
                } ?: if (profile.showUnassignedExercises) {
                    domainExercises
                } else {
                    domainExercises.filter { !it.isUnassignedExercise() }
                }
            }.orEmpty()

            exercises = orderUnfinishedFirst(loadedExercises, profile.id)
            if (exercises.isNotEmpty()) {
                startExercise(0)
            } else {
                _uiState.update {
                    it.copy(
                        currentExercise = null,
                        userPath = emptyList(),
                        userInput = "",
                        isCorrect = null,
                        isWrong = false,
                        showFeedback = false,
                        isComplete = false,
                        isFinished = false,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun setSubcategoryId(subcategoryId: String?) {
        selectedSubcategoryId = subcategoryId
        hasContentRequest = true
        latestProfile?.let { profile ->
            loadContentForProfile(profile)
            return
        }

        viewModelScope.launch {
            val profileId = _uiState.value.currentProfileId
            val profile = profileId?.let { profileRepository.getProfileById(it).first() }
            if (profile != null) {
                latestProfile = profile
                loadContentForProfile(profile)
            }
        }
    }

    fun setDifficultyStars(stars: Int?) {
        selectedDifficultyStars = stars?.coerceIn(1, 3)
    }

    private fun startExercise(index: Int) {
        currentIndex = index
        startTime = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                currentExercise = exercises[index],
                userPath = emptyList(),
                userInput = "",
                isCorrect = null,
                isWrong = false,
                showFeedback = false,
                isComplete = false,
                isFinished = false,
                isLoading = false
            )
        }
    }

    fun onTextInputChanged(value: String) {
        if (_uiState.value.isCorrect == true) return
        _uiState.update { it.copy(userInput = value, isCorrect = null, isWrong = false, showFeedback = false) }
    }

    fun onChoiceSelected(value: String) {
        if (_uiState.value.isCorrect == true) return
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        if (value.isWholeInteger()) {
            _uiState.update { it.copy(userInput = value, isCorrect = null, isWrong = false, showFeedback = false) }
            checkAnswer(value)
            return
        }
        if (state.userInput != value) {
            _uiState.update { it.copy(userInput = value, isCorrect = null, isWrong = false, showFeedback = false) }
            audioService.speak(exercise.localizedOptionSpeech(value, state.exerciseLanguage), exercise.optionSpeechLocale(state.exerciseLanguage))
            return
        }
        checkAnswer(value)
    }

    fun onConfirmClick() {
        checkAnswer(_uiState.value.userInput)
    }

    fun playCurrentQuestion() {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        audioService.speak(exercise.localizedPromptTts(state.exerciseLanguage), state.exerciseLanguage)
    }

    fun stopAudio() {
        audioService.stopPlayback()
    }

    private fun checkAnswer(answer: String) {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        val isCorrect = normalizeAnswer(answer) == normalizeAnswer(exercise.localizedAnswer(state.exerciseLanguage))
        val duration = System.currentTimeMillis() - startTime
        _uiState.update { it.copy(isCorrect = isCorrect, isWrong = !isCorrect, showFeedback = true, isComplete = isCorrect) }
        if (isCorrect) {
            audioService.playSuccess()
            saveProgress(exercise, duration, true)
        } else {
            audioService.playFailure()
            saveProgress(exercise, duration, false)
        }
    }

    private fun SkillLevel.toGrade(): Int? = if (this == SkillLevel.NOT_APPLICABLE) null else ordinal + 1
    private fun String.isWholeInteger(): Boolean = trim().matches(Regex("-?\\d+"))
    private fun ExerciseContent.optionSpeechLocale(defaultLocale: String): String = data["audioTargetLocale"] ?: defaultLocale
    private fun ExerciseContent.isUnassignedExercise(): Boolean = data["subcategoryId"].isNullOrBlank()

    private fun List<ExerciseContent>.unassignedForDifficulty(difficultyStars: Int?): List<ExerciseContent> =
        filter { exercise ->
            exercise.isUnassignedExercise() &&
                (difficultyStars == null || exercise.data["difficultyStars"]?.toIntOrNull() == difficultyStars)
        }

    private suspend fun orderUnfinishedFirst(candidates: List<ExerciseContent>, profileId: String): List<ExerciseContent> {
        val completedIds = progressRepository.getProgressByChild(profileId).first()
            .filter { it.answerCorrect }
            .map { it.exerciseId }
            .toSet()
        val unfinished = candidates.filter { it.id !in completedIds }
        return (unfinished.ifEmpty { candidates }).shuffled()
    }

    fun onPathUpdate(path: List<Offset>) {
        if (_uiState.value.isComplete) return
        _uiState.update { it.copy(userPath = path) }
    }

    fun onTraceComplete() {
        if (_uiState.value.isComplete) return
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        val duration = System.currentTimeMillis() - startTime
        _uiState.update { it.copy(isComplete = true) }
        audioService.playSuccess()
        saveProgress(exercise, duration, true)
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
        } else if (exercises.isNotEmpty()) {
            exercises = exercises.shuffled()
            _uiState.update { it.copy(isLoading = true) }
            startExercise(0)
        }
    }
}
