package be.saikono.poincare.ui.puzzle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.saikono.poincare.data.audit.AuditLog
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.data.sync.SyncManager
import be.saikono.poincare.model.*
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.ProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class PuzzleUiState(
    val currentExercise: ExerciseContent? = null,
    val availableDigits: List<String> = emptyList(),
    val placedDigits: Map<Int, String> = emptyMap(),
    val placedIndices: Set<Int> = emptySet(),
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
    private var selectedGrade: Int? = null
    private var latestProfile: ChildProfile? = null
    private var hasContentRequest = false
    private var contentLoadJob: Job? = null
    private var contentLoadGeneration = 0L

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
        val requestGeneration = ++contentLoadGeneration
        contentLoadJob?.cancel()
        contentLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val sourceDomain = puzzleGradeSourceDomain(selectedSubcategoryId)
                val level = selectedGrade?.let(::skillLevelForGrade)
                    ?: skillLevelForGrade(profile.selectedGradesFor(sourceDomain).firstOrNull() ?: 1)
                val mathLevel = if (sourceDomain == LearningDomain.MATH) level else {
                    skillLevelForGrade(profile.selectedGradesFor(LearningDomain.MATH).firstOrNull() ?: 1)
                }
                val worldKnowledgeLevel = if (sourceDomain == LearningDomain.WORLD_KNOWLEDGE) level else {
                    skillLevelForGrade(profile.selectedGradesFor(LearningDomain.WORLD_KNOWLEDGE).firstOrNull() ?: 1)
                }
                val loadedPuzzles = level.toGrade()?.let { grade ->
                    selectedSubcategoryId?.let { subcategoryId ->
                        generatedPuzzleExercise(subcategoryId, level, mathLevel, worldKnowledgeLevel, selectedDifficultyStars, profile.hardModeEnabled)?.let { generatedPuzzle ->
                            listOf(generatedPuzzle)
                        } ?: run {
                            val domainPuzzles = contentRepository.getExercises(LearningDomain.PUZZLE, level)
                            val subcategoryPuzzles = contentRepository.getExercises(
                                mainCategory = "puzzle",
                                subcategoryId = subcategoryId,
                                grade = grade,
                                difficultyStars = selectedDifficultyStars
                            )
                            if (profile.showUnassignedExercises) {
                                (subcategoryPuzzles + domainPuzzles.unassignedForDifficulty(selectedDifficultyStars)).distinctBy { it.id }
                            } else {
                                subcategoryPuzzles
                            }
                        }
                    } ?: run {
                        val domainPuzzles = contentRepository.getExercises(LearningDomain.PUZZLE, level)
                        if (profile.showUnassignedExercises) domainPuzzles
                        else domainPuzzles.filter { !it.isUnassignedExercise() }
                    }
                }.orEmpty()

                val orderedPuzzles = orderUnfinishedFirst(loadedPuzzles, profile.id)
                if (requestGeneration != contentLoadGeneration) return@launch
                puzzles = orderedPuzzles

                if (puzzles.isNotEmpty()) {
                    startPuzzle(0)
                } else {
                    showEmptyPuzzleState()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (requestGeneration == contentLoadGeneration) {
                    puzzles = emptyList()
                    showEmptyPuzzleState()
                }
            }
        }
    }

    private fun showEmptyPuzzleState() {
        _uiState.update {
            it.copy(
                currentExercise = null,
                availableDigits = emptyList(),
                placedDigits = emptyMap(),
                placedIndices = emptySet(),
                pendingChoiceIndex = null,
                isLoading = false,
                isSolved = false,
                isWrong = false,
                isFinished = false,
                showFeedback = false
            )
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

    fun setSelectedGrade(grade: Int?) {
        selectedGrade = grade?.coerceIn(1, 6)
    }

    private fun generatedPuzzleExercise(
        subcategoryId: String,
        level: SkillLevel,
        mathLevel: SkillLevel,
        worldKnowledgeLevel: SkillLevel,
        difficultyStars: Int?,
        hardModeEnabled: Boolean
    ): ExerciseContent? {
        val stars = difficultyStars?.coerceIn(1, 3) ?: 1
        val generationDifficulty = effectiveGeneratedDifficulty(stars, hardModeEnabled)
        val mathGrade = mathLevel.toGrade() ?: level.toGrade() ?: 1
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
                    "generationDifficulty" to generationDifficulty.toString(),
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
                    "generationDifficulty" to generationDifficulty.toString(),
                    "topic" to "Rekenkruiswoord",
                    "topic_en-US" to "Math crossword",
                    "topic_fr-BE" to "Mots croises de calcul"
                ),
                answer = "completed"
            )
            "calculator-puzzle" -> ExerciseContent(
                id = "generated-calculator-puzzle-grade-$mathGrade-$stars",
                domain = LearningDomain.PUZZLE,
                skill = "calculator-puzzle",
                section = ExerciseSection.PUZZLES,
                level = level,
                inputMode = InputMode.CALCULATOR_PUZZLE,
                prompt = LocalizedText(
                    nl = "Calculatorpuzzel",
                    en = "Calculator puzzle",
                    fr = "Puzzle calculatrice"
                ),
                data = mapOf(
                    "mainCategory" to "logic_puzzles",
                    "subcategoryId" to "calculator-puzzle",
                    "grade" to mathGrade.toString(),
                    "mathGrade" to mathGrade.toString(),
                    "difficultyStars" to stars.toString(),
                    "generationDifficulty" to generationDifficulty.toString(),
                    "topic" to "Calculatorpuzzel",
                    "topic_en-US" to "Calculator puzzle",
                    "topic_fr-BE" to "Puzzle calculatrice",
                    "progressKey" to "calculator-puzzle-grade-$mathGrade"
                ),
                answer = "completed"
            )
            "constellations" -> ExerciseContent(
                id = "generated-constellations-${level.name.lowercase()}-$stars",
                domain = LearningDomain.PUZZLE,
                skill = "constellations",
                section = ExerciseSection.PUZZLES,
                level = level,
                inputMode = InputMode.TAP_CHOICE,
                prompt = LocalizedText(
                    nl = "Constellations",
                    en = "Constellations",
                    fr = "Constellations"
                ),
                data = mapOf(
                    "mainCategory" to "logic_puzzles",
                    "subcategoryId" to "constellations",
                    "grade" to (level.toGrade()?.toString() ?: "1"),
                    "difficultyStars" to stars.toString(),
                    "generationDifficulty" to generationDifficulty.toString(),
                    "topic" to "Constellations",
                    "topic_en-US" to "Constellations",
                    "topic_fr-BE" to "Constellations",
                    "progressKey" to "constellations-${level.name.lowercase()}-$stars"
                ),
                answer = "completed"
            )
            "orbit-builder" -> ExerciseContent(
                id = "generated-orbit-builder-${level.name.lowercase()}-$stars",
                domain = LearningDomain.PUZZLE,
                skill = "orbit-builder",
                section = ExerciseSection.PUZZLES,
                level = level,
                inputMode = InputMode.TAP_CHOICE,
                prompt = LocalizedText(nl = "Orbitbouwer", en = "Orbit Builder", fr = "Constructeur d’orbite"),
                data = mapOf(
                    "mainCategory" to "logic_puzzles",
                    "subcategoryId" to "orbit-builder",
                    "grade" to (level.toGrade()?.toString() ?: "1"),
                    "difficultyStars" to stars.toString(),
                    "generationDifficulty" to generationDifficulty.toString(),
                    "topic" to "Orbitbouwer",
                    "topic_en-US" to "Orbit Builder",
                    "topic_fr-BE" to "Constructeur d’orbite",
                    "progressKey" to "orbit-builder-${level.name.lowercase()}-$stars"
                ),
                answer = "completed"
            )             "mothership-dock" -> ExerciseContent(
                id = "generated-mothership-dock-${level.name.lowercase()}-$stars",
                domain = LearningDomain.PUZZLE,
                skill = MOTHERSHIP_DOCK_SUBCATEGORY_ID,
                section = ExerciseSection.PUZZLES,
                level = level,
                inputMode = InputMode.TAP_CHOICE,
                prompt = LocalizedText(
                    nl = "Moederschip",
                    en = "Mothership dock",
                    fr = "Vaisseau mère"
                ),
                data = mapOf(
                    "mainCategory" to "logic_puzzles",
                    "subcategoryId" to MOTHERSHIP_DOCK_SUBCATEGORY_ID,
                    "grade" to (level.toGrade()?.toString() ?: "1"),
                    "worldKnowledgeGrade" to (worldKnowledgeLevel.toGrade()?.toString() ?: (level.toGrade()?.toString() ?: "1")),
                    "mathGrade" to mathGrade.toString(),
                    "difficultyStars" to stars.toString(),
                    "generationDifficulty" to generationDifficulty.toString(),
                    "topic" to "Moederschip",
                    "topic_en-US" to "Mothership dock",
                    "topic_fr-BE" to "Vaisseau mère",
                    "progressKey" to "mothership-dock-${level.name.lowercase()}-$stars"
                ),
                answer = "completed"
            )
            "galaxycon" -> ExerciseContent(
            id = "generated-galaxycon-${level.name.lowercase()}-$stars",
            domain = LearningDomain.PUZZLE,
            skill = GALAXYCON_SUBCATEGORY_ID,
            section = ExerciseSection.PUZZLES,
            level = level,
            inputMode = InputMode.DRAG_DROP_LETTERS,
            prompt = LocalizedText(nl = "Galaxycon", en = "Galaxycon", fr = "Galaxycon"),
            data = mapOf(
                "mainCategory" to "logic_puzzles",
                "subcategoryId" to GALAXYCON_SUBCATEGORY_ID,
                "grade" to (level.toGrade()?.toString() ?: ""),
                "difficultyStars" to stars.toString(),
                    "generationDifficulty" to generationDifficulty.toString(),
                "topic" to "Galaxycon",
                "topic_en-US" to "Galaxycon",
                "topic_fr-BE" to "Galaxycon"
            ),
            answer = "planet"
        )

        "word-seeker" -> ExerciseContent(
 id = "generated-word-seeker-${level.name.lowercase()}-$stars",
 domain = LearningDomain.PUZZLE,
 skill = "word-seeker",
 section = ExerciseSection.PUZZLES,
 level = level,
 inputMode = InputMode.WORD_SEEKER,
 prompt = LocalizedText(nl = "Woordzoeker", en = "Word seeker", fr = "Cherche-mots"),
 data = mapOf(
 "mainCategory" to "logic_puzzles",
 "subcategoryId" to "word-seeker",
 "grade" to (level.toGrade()?.toString() ?: ""),
 "difficultyStars" to stars.toString(),
                    "generationDifficulty" to generationDifficulty.toString(),
 "topic" to "Woordzoeker",
 "topic_en-US" to "Word seeker",
 "topic_fr-BE" to "Cherche-mots"
 ),
 answer = "completed"
 )
 else -> null
        }
    }

    fun onCalculatorPuzzleAttempt(attempt: CalculatorPuzzleAttempt) {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        val duration = System.currentTimeMillis() - startTime

        _uiState.update {
            it.copy(
                isSolved = attempt.isCorrect || it.isSolved,
                isWrong = !attempt.isCorrect,
                showFeedback = true
            )
        }

        if (attempt.isCorrect) audioService.playSuccess() else audioService.playFailure()

        saveProgress(
            exercise = exercise,
            durationMs = duration,
            correct = attempt.isCorrect,
            submittedAnswer = attempt.submittedExpression,
            extraMetadata = mapOf(
                "calculatorChallengeId" to attempt.challengeId,
                "calculatorTargetExpression" to attempt.targetExpression,
                "calculatorTargetResult" to CalculatorPuzzleGenerator.formatNumber(attempt.targetResult),
                "calculatorSubmittedResult" to (attempt.submittedResult?.let(CalculatorPuzzleGenerator::formatNumber) ?: ""),
                "calculatorAttemptNumber" to attempt.attemptNumber.toString(),
                "progressState" to if (attempt.isCorrect) "answered_correct" else "answered_wrong"
            )
        )
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
        _uiState.update { it.copy(isSolved = false, isWrong = false, showFeedback = false) }
        audioService.playSuccess()
        saveProgress(exercise, duration, correct = true, submittedAnswer = "completed")
    }

    fun onOrbitBuilderComplete(levelId: String, stars: Int) {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        val duration = System.currentTimeMillis() - startTime
        val orbitExercise = exercise.copy(
            id = "${exercise.id}-$levelId",
            data = exercise.data + mapOf(
                "progressKey" to "orbit-builder-$levelId",
                "orbitBuilderLevelId" to levelId,
                "orbitBuilderStars" to stars.toString()
            )
        )
        _uiState.update { it.copy(isSolved = false, isWrong = false, showFeedback = false) }
        audioService.playSuccess()
        saveProgress(
            exercise = orbitExercise,
            durationMs = duration,
            correct = true,
            submittedAnswer = "completed",
            extraMetadata = mapOf(
                "orbitBuilderLevelId" to levelId,
                "orbitBuilderStars" to stars.toString(),
                "progressState" to "completed"
            )
        )
    }
    fun onGalaxyconComplete(result: GalaxyconRunResult) {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        val duration = if (result.elapsedMs > 0L) {
            result.elapsedMs
        } else {
            System.currentTimeMillis() - startTime
        }

        _uiState.update {
            it.copy(
                isSolved = result.planetReached,
                isWrong = !result.planetReached,
                showFeedback = true
            )
        }
        if (result.planetReached) audioService.playSuccess() else audioService.playFailure()
        saveGalaxyconProgress(exercise, duration, result)
    }

    private fun startPuzzle(index: Int) {
        val puzzle = puzzles.getOrNull(index) ?: run {
            showEmptyPuzzleState()
            return
        }
        currentIndex = index
        startTime = System.currentTimeMillis()
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
        AuditLog.record("mission_started", _uiState.value.currentProfileId, puzzle.domain.name, puzzle.data["subcategoryId"], puzzle.id)
    }

    fun onDigitPlaced(slotIndex: Int, digitIndex: Int) {
        AuditLog.record("puzzle_choice_pressed", _uiState.value.currentProfileId, exerciseId = _uiState.value.currentExercise?.id)
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
        checkSolution(newPlaced)
    }

    private fun checkSolution(placed: Map<Int, String>) {
        val puzzle = _uiState.value.currentExercise ?: return
        val userSolution = placed[0] ?: return
        val isCorrect = normalizeAnswer(userSolution) == normalizeAnswer(puzzle.localizedAnswer(_uiState.value.exerciseLanguage))

        if (isCorrect) {
            val duration = System.currentTimeMillis() - startTime
            _uiState.update { it.copy(isSolved = false, isWrong = false, showFeedback = false) }
            audioService.playSuccess()
            saveProgress(puzzle, duration, correct = true, submittedAnswer = userSolution)
        } else {
            _uiState.update { it.copy(isWrong = true, showFeedback = true) }
            audioService.playFailure()
            saveProgress(puzzle, System.currentTimeMillis() - startTime, correct = false, submittedAnswer = userSolution)
        }
    }

    private fun SkillLevel.toGrade(): Int? = if (this == SkillLevel.NOT_APPLICABLE) null else ordinal + 1

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

    private fun saveProgress(
        exercise: ExerciseContent,
        durationMs: Long,
        correct: Boolean,
        submittedAnswer: String,
        extraMetadata: Map<String, String> = emptyMap()
    ) {
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
                ) + extraMetadata
            )
            progressRepository.addProgress(event)
            syncManager.scheduleSync()
        }
    }

    private fun saveGalaxyconProgress(
        exercise: ExerciseContent,
        durationMs: Long,
        result: GalaxyconRunResult
    ) {
        val profileId = _uiState.value.currentProfileId ?: return
        viewModelScope.launch {
            val event = ProgressEvent(
                id = UUID.randomUUID().toString(),
                childProfileId = profileId,
                exerciseId = exercise.id,
                domain = exercise.domain,
                skill = exercise.skill,
                timestamp = System.currentTimeMillis(),
                answerCorrect = result.planetReached,
                responseTimeMs = durationMs,
                retryCount = if (result.planetReached) 0 else 1,
                rewardStampsEarned = if (result.planetReached) {
                    (missionPointsFor(exercise, true) + result.stickersEarned.size)
                        .coerceAtMost(MAX_LEVEL_POINTS_PER_ANSWER)
                } else {
                    0
                },
                metadata = mapOf(
                    "answer" to if (result.planetReached) "planet_reached" else "run_finished",
                    "progressKey" to (exercise.data["progressKey"] ?: exercise.id),
                    "subcategoryId" to GALAXYCON_SUBCATEGORY_ID,
                    "grade" to (exercise.data["grade"] ?: (exercise.level.toGrade()?.toString() ?: "")),
                    "progressState" to if (result.planetReached) "completed" else "retry_wrong",
                    "galaxyconScorePercent" to result.displayScore,
                    "galaxyconPoints" to result.points.toString(),
                    "galaxyconCollisions" to result.collisions.toString(),
                    "galaxyconWordsCompleted" to "${result.wordsCompleted}/${result.totalWords}",
                    "galaxyconStarsCollected" to result.starsCollected.toString(),
                    "galaxyconStickers" to result.stickersEarned.joinToString("|"),
                    "stickerBookSubgame" to GALAXYCON_SUBCATEGORY_ID
                )
            )
            progressRepository.addProgress(event)
            syncManager.scheduleSync()
        }
    }

        fun onMothershipDockComplete(result: MothershipDockRunResult) {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        val duration = if (result.elapsedMs > 0L) {
            result.elapsedMs
        } else {
            System.currentTimeMillis() - startTime
        }

        _uiState.update {
            it.copy(
                isSolved = false,
                isWrong = false,
                showFeedback = false
            )
        }

        if (result.docked) {
            audioService.playSuccess()
        } else {
            audioService.playFailure()
        }

        saveMothershipDockProgress(exercise, duration, result)
    }

    private fun saveMothershipDockProgress(
        exercise: ExerciseContent,
        durationMs: Long,
        result: MothershipDockRunResult
    ) {
        val profileId = _uiState.value.currentProfileId ?: return
        viewModelScope.launch {
            val event = ProgressEvent(
                id = UUID.randomUUID().toString(),
                childProfileId = profileId,
                exerciseId = exercise.id,
                domain = exercise.domain,
                skill = exercise.skill,
                timestamp = System.currentTimeMillis(),
                answerCorrect = result.docked,
                responseTimeMs = durationMs,
                retryCount = if (result.docked) 0 else 1,
                rewardStampsEarned = missionPointsFor(exercise, result.docked),
                metadata = mapOf(
                    "answer" to if (result.docked) "friendly_docked" else "dock_failed",
                    "progressKey" to (exercise.data["progressKey"] ?: exercise.id),
                    "subcategoryId" to MOTHERSHIP_DOCK_SUBCATEGORY_ID,
                    "grade" to (exercise.data["grade"] ?: (exercise.level.toGrade()?.toString() ?: "")),
                    "progressState" to if (result.docked) "completed" else "retry_wrong",
                    "mothershipDockTarget" to result.target,
                    "mothershipDockMode" to result.mode,
                    "mothershipDockShots" to result.shotsFired.toString(),
                    "mothershipDockEnemiesDestroyed" to result.enemiesDestroyed.toString(),
                    "mothershipDockFriendlyHit" to result.friendlyHit.toString(),
                    "mothershipDockEnemyDocked" to result.enemyDocked.toString(),
                    "stickerBookSubgame" to MOTHERSHIP_DOCK_SUBCATEGORY_ID
                )
            )
            progressRepository.addProgress(event)
            syncManager.scheduleSync()
        }
    }

    fun speakText(text: String) {
        audioService.speak(text, _uiState.value.exerciseLanguage)
    }

    fun nextPuzzle() {
        AuditLog.record("next_mission_pressed", _uiState.value.currentProfileId, exerciseId = _uiState.value.currentExercise?.id)
        if (currentIndex + 1 < puzzles.size) {
            startPuzzle(currentIndex + 1)
        } else if (puzzles.isNotEmpty()) {
            puzzles = puzzles.shuffled()
            startPuzzle(0)
        }
    }
}
