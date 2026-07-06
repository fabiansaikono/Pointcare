package be.saikono.poincare.ui.puzzle

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

data class PuzzleUiState(
    val currentExercise: ExerciseContent? = null,
    val availableDigits: List<String> = emptyList(),
    val placedDigits: Map<Int, String> = emptyMap(), // index to digit
    val placedIndices: Set<Int> = emptySet(), // indices from availableDigits that were placed
    val isSolved: Boolean = false,
    val isWrong: Boolean = false,
    val isFinished: Boolean = false,
    val showFeedback: Boolean = false,
    val isLoading: Boolean = true,
    val currentProfileId: String? = null,
    val exerciseLanguage: String = "nl-BE",
    val pendingChoiceIndex: Int? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PuzzleViewModel(
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val syncManager: SyncManager,
    private val audioService: be.saikono.poincare.data.audio.AudioService,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PuzzleUiState())
    val uiState: StateFlow<PuzzleUiState> = _uiState.asStateFlow()

    private var puzzles: List<ExerciseContent> = emptyList()
    private var currentIndex = 0
    private var startTime = 0L
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
                        exerciseLanguage = profile?.exerciseLanguage ?: "nl-BE"
                    ) }
                    
                    if (puzzles.isEmpty() && profile != null) {
                        loadContentForProfile(profile)
                    }
                }
        }
    }

    private fun loadContentForProfile(profile: ChildProfile) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val level = profile.puzzleLevel
            val loadedPuzzles = level.toGrade()?.let { grade ->
                selectedSubcategoryId?.let { subcategoryId ->
                    generatedPuzzleExercise(subcategoryId, level, selectedDifficultyStars)?.let { generatedPuzzle ->
                        listOf(generatedPuzzle)
                    } ?: contentRepository.getExercises(subcategoryId = subcategoryId, grade = grade, difficultyStars = selectedDifficultyStars)
                } ?: contentRepository.getExercises(LearningDomain.PUZZLE, level)
            }.orEmpty()
            
            puzzles = orderUnfinishedFirst(loadedPuzzles, profile.id)
            
            if (puzzles.isNotEmpty()) {
                startPuzzle(0)
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

    private fun generatedPuzzleExercise(subcategoryId: String, level: SkillLevel, difficultyStars: Int?): ExerciseContent? {
        val stars = difficultyStars?.coerceIn(1, 3) ?: 1
        return when (subcategoryId) {
            "sudoku" -> ExerciseContent(
                id = "generated-sudoku-${level.name.lowercase()}-$stars",
                domain = LearningDomain.PUZZLE,
                skill = "sudoku",
                section = ExerciseSection.PUZZLES,
                level = level,
                inputMode = InputMode.SUDOKU_GRID,
                prompt = LocalizedText(nl = "Sudoku", en = "Sudoku", fr = "Sudoku"),
                data = mapOf(
                    "mainCategory" to "logic_puzzles",
                    "subcategoryId" to "sudoku",
                    "grade" to (level.toGrade()?.toString() ?: ""),
                    "difficultyStars" to stars.toString(),
                    "topic" to "Sudoku",
                    "topic_en-US" to "Sudoku",
                    "topic_fr-BE" to "Sudoku"
                ),
                answer = "completed"
            )
            "math-crosswords" -> ExerciseContent(
                id = "generated-math-crossword-${level.name.lowercase()}-$stars",
                domain = LearningDomain.PUZZLE,
                skill = "math-crosswords",
                section = ExerciseSection.PUZZLES,
                level = level,
                inputMode = InputMode.MATH_CROSSWORD,
                prompt = LocalizedText(nl = "Rekenkruiswoord", en = "Math crossword", fr = "Mots croises de calcul"),
                data = mapOf(
                    "mainCategory" to "logic_puzzles",
                    "subcategoryId" to "math-crosswords",
                    "grade" to (level.toGrade()?.toString() ?: ""),
                    "difficultyStars" to stars.toString(),
                    "topic" to "Rekenkruiswoord",
                    "topic_en-US" to "Math crossword",
                    "topic_fr-BE" to "Mots croises de calcul"
                ),
                answer = "completed"
            )
            else -> null
        }
    }

    fun playCurrentQuestion() {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        audioService.speak(exercise.localizedPromptTts(state.exerciseLanguage), state.exerciseLanguage)
    }

    fun stopAudio() {
        audioService.stopPlayback()
    }

    fun onGeneratedPuzzleComplete() {
        val state = _uiState.value
        if (state.isSolved) return
        val exercise = state.currentExercise ?: return
        val duration = System.currentTimeMillis() - startTime
        _uiState.update { it.copy(isSolved = true, isWrong = false, showFeedback = true) }
        audioService.playSuccess()
        saveProgress(exercise, duration, correct = true, submittedAnswer = "completed")
    }

    private fun startPuzzle(index: Int) {
        currentIndex = index
        startTime = System.currentTimeMillis()
        val puzzle = puzzles[index]
        _uiState.update { 
            it.copy(
                currentExercise = puzzle,
                availableDigits = puzzle.data["available"]?.split(",") ?: puzzle.localizedOptions(_uiState.value.exerciseLanguage),
                placedDigits = emptyMap(),
                placedIndices = emptySet(),
                pendingChoiceIndex = null,
                isSolved = false,
                isWrong = false,
                isFinished = false,
                showFeedback = false,
                isLoading = false
            )
        }
    }

    fun onDigitPlaced(slotIndex: Int, digitIndex: Int) {
        if (_uiState.value.isSolved) return
        
        val puzzle = _uiState.value.currentExercise ?: return
        val digit = _uiState.value.availableDigits.getOrNull(digitIndex) ?: return
        if (_uiState.value.pendingChoiceIndex != digitIndex) {
            _uiState.update { it.copy(pendingChoiceIndex = digitIndex, isWrong = false, showFeedback = false) }
            audioService.speak(puzzle.localizedOptionSpeech(digit, _uiState.value.exerciseLanguage), _uiState.value.exerciseLanguage)
            return
        }
        if (normalizeAnswer(digit) != normalizeAnswer(puzzle.localizedAnswer(_uiState.value.exerciseLanguage))) {
            val duration = System.currentTimeMillis() - startTime
            _uiState.update { it.copy(isWrong = true, showFeedback = true) }
            audioService.playFailure()
            saveProgress(puzzle, duration, correct = false, submittedAnswer = digit)
            return
        }

        val newPlaced = _uiState.value.placedDigits.toMutableMap()
        newPlaced[slotIndex] = digit
        
        val newIndices = _uiState.value.placedIndices.toMutableSet()
        newIndices.add(digitIndex)
        
        _uiState.update { it.copy(placedDigits = newPlaced, placedIndices = newIndices) }

        // Auto-check if all slots (only one for MVP puzzle) are filled
        checkSolution(newPlaced)
    }

    private fun checkSolution(placed: Map<Int, String>) {
        val puzzle = _uiState.value.currentExercise ?: return
        
        // For MVP equation puzzles, we expect the answer to match the placed digit
        val userSolution = placed[0] ?: return
        val isCorrect = normalizeAnswer(userSolution) == normalizeAnswer(puzzle.localizedAnswer(_uiState.value.exerciseLanguage))
        
        if (isCorrect) {
            val duration = System.currentTimeMillis() - startTime
            _uiState.update { it.copy(isSolved = true, isWrong = false, showFeedback = true) }
            audioService.playSuccess()
            saveProgress(puzzle, duration, correct = true, submittedAnswer = userSolution)
        } else {
            _uiState.update { it.copy(isWrong = true, showFeedback = true) }
            audioService.playFailure()
            saveProgress(puzzle, System.currentTimeMillis() - startTime, correct = false, submittedAnswer = userSolution)
        }
    }

    private fun SkillLevel.toGrade(): Int? = if (this == SkillLevel.NOT_APPLICABLE) null else ordinal + 1

    private suspend fun orderUnfinishedFirst(candidates: List<ExerciseContent>, profileId: String): List<ExerciseContent> {
        val completedIds = progressRepository.getProgressByChild(profileId).first()
            .filter { it.answerCorrect }
            .map { it.exerciseId }
            .toSet()
        val unfinished = candidates.filter { it.id !in completedIds }
        return (unfinished.ifEmpty { candidates }).shuffled()
    }

    private fun saveProgress(exercise: ExerciseContent, durationMs: Long, correct: Boolean, submittedAnswer: String) {
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
                retryCount = if (correct) 0 else 1,
                rewardStampsEarned = missionPointsFor(exercise, correct),
                metadata = mapOf(
                    "answer" to submittedAnswer,
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

    fun nextPuzzle() {
        if (currentIndex + 1 < puzzles.size) {
            startPuzzle(currentIndex + 1)
        } else {
            puzzles = puzzles.shuffled()
            startPuzzle(0)
        }
    }
}
