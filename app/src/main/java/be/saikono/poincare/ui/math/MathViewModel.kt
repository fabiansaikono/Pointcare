package be.saikono.poincare.ui.math

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.saikono.poincare.data.audit.AuditLog
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.domain.adaptive.AdaptiveDifficultyEngine
import be.saikono.poincare.domain.adaptive.AdaptiveRecommendation
import be.saikono.poincare.model.*
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.ProgressRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MathUiState(
    val currentExercise: ExerciseContent? = null,
    val userInput: String = "",
    val writtenCalculation: WrittenCalculation? = null,
    val calculationEntries: Map<String, String> = emptyMap(),
    val isCorrect: Boolean? = null,
    val isWrong: Boolean = false,
    val isFinished: Boolean = false,
    val showFeedback: Boolean = false,
    val isLoading: Boolean = true,
    val currentProfileId: String? = null,
    val exerciseLanguage: String = "nl-BE",
    val recommendation: AdaptiveRecommendation? = null,
    val reducedMotion: Boolean = false,
    val hapticEnabled: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class MathViewModel(
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val syncManager: be.saikono.poincare.data.sync.SyncManager,
    private val adaptiveEngine: AdaptiveDifficultyEngine,
    private val audioService: be.saikono.poincare.data.audio.AudioService,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MathUiState())
    val uiState: StateFlow<MathUiState> = _uiState.asStateFlow()

    private var exercises: List<ExerciseContent> = emptyList()
    private var currentIndex = 0
    private var startTime = 0L
    private var selectedSubcategoryId: String? = null
    private var selectedDifficultyStars: Int? = null
    private var selectedGrade: Int? = null
    private var generatedOperation: WrittenOperation? = null
    private var generatedSubcategoryId: String? = null
    private var generatedLevel: SkillLevel? = null
    private var latestProfile: ChildProfile? = null
    private var hasContentRequest = false
    private var contentLoadJob: Job? = null
    private var contentLoadGeneration = 0L

    private companion object {
        const val GENERATED_BATCH_SIZE = 24
        val GEOMETRY_SUBCATEGORIES = setOf("time", "measurement", "geometry_shapes")
    }

    init {
        observeProfile()
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsDataStore.animationSettings.collect { settings ->
                _uiState.update {
                    it.copy(
                        reducedMotion = settings.reducedMotionEnabled,
                        hapticEnabled = settings.hapticFeedbackEnabled
                    )
                }
            }
        }
    }

    private fun observeProfile() {
        viewModelScope.launch {
            settingsDataStore.selectedProfileId
                .flatMapLatest { id ->
                    if (id == null) flowOf(null) else profileRepository.getProfileById(id)
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
                val isGeometry = SubcategoryCatalog.baseId(selectedSubcategoryId) in SubcategoryCatalog.geometryIds
                val level = selectedGrade?.let(::skillLevelForGrade) ?: if (isGeometry) profile.geometryLevel else profile.mathLevel
                val requestedOperation = WrittenOperation.fromSubcategoryId(selectedSubcategoryId)
                val requestedGeneratedId = selectedSubcategoryId?.takeIf {
                    requestedOperation != null || GeneratedMathExerciseGenerator.supports(it)
                }

                val loadedExercises = when {
                    requestedOperation != null -> {
                        val nextMissionNumber = nextGeneratedMissionNumber(
                            profileId = profile.id,
                            subcategoryId = requestedOperation.subcategoryId,
                            level = level
                        )
                        WrittenCalculationGenerator.generate(
                            operation = requestedOperation,
                            level = level,
                            startMissionNumber = nextMissionNumber,
                            count = GENERATED_BATCH_SIZE,
                            hardModeEnabled = profile.hardModeEnabled
                        )
                    }

                    requestedGeneratedId != null -> {
                        val nextMissionNumber = nextGeneratedMissionNumber(
                            profileId = profile.id,
                            subcategoryId = requestedGeneratedId,
                            level = level
                        )
                        GeneratedMathExerciseGenerator.generate(
                            subcategoryId = requestedGeneratedId,
                            level = level,
                            startMissionNumber = nextMissionNumber,
                            count = GENERATED_BATCH_SIZE,
                            hardModeEnabled = profile.hardModeEnabled
                        )
                    }

                    else -> level.toGrade()?.let { grade ->
                        val domain = if (isGeometry) LearningDomain.GEOMETRY else LearningDomain.MATH
                        val mainCategory = if (isGeometry) "geometry" else "math"
                        val domainExercises = contentRepository.getExercises(domain, level)
                        selectedSubcategoryId?.let { subcategoryId ->
                            contentRepository.getExercises(
                                mainCategory = mainCategory,
                                subcategoryId = subcategoryId,
                                grade = grade,
                                difficultyStars = selectedDifficultyStars
                            )
                        }?.ifEmpty {
                            domainExercises.filter { it.data["subcategoryId"] == selectedSubcategoryId }
                        } ?: domainExercises
                    }.orEmpty()
                }

                if (requestGeneration != contentLoadGeneration) return@launch
                generatedOperation = requestedOperation
                generatedSubcategoryId = requestedGeneratedId
                generatedLevel = level.takeIf { requestedGeneratedId != null }
                exercises = if (requestedGeneratedId != null) loadedExercises else orderUnfinishedFirst(loadedExercises, profile.id)

                if (requestGeneration != contentLoadGeneration) return@launch
                if (exercises.isNotEmpty()) {
                    startExercise(0)
                } else {
                    _uiState.update {
                        it.copy(
                            currentExercise = null,
                            writtenCalculation = null,
                            calculationEntries = emptyMap(),
                            isLoading = false
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (requestGeneration == contentLoadGeneration) {
                    exercises = emptyList()
                    _uiState.update {
                        it.copy(
                            currentExercise = null,
                            writtenCalculation = null,
                            calculationEntries = emptyMap(),
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    fun setSubcategoryId(subcategoryId: String?) {
        selectedSubcategoryId = subcategoryId
        hasContentRequest = true
        latestProfile?.let {
            loadContentForProfile(it)
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

    private fun startExercise(index: Int) {
        currentIndex = index
        startTime = System.currentTimeMillis()
        val exercise = exercises.getOrNull(index) ?: run {
            _uiState.update { it.copy(currentExercise = null, isLoading = false) }
            return
        }
        _uiState.update {
            it.copy(
                currentExercise = exercise,
                userInput = "",
                writtenCalculation = WrittenCalculation.fromExercise(exercise),
                calculationEntries = emptyMap(),
                isCorrect = null,
                isWrong = false,
                isFinished = false,
                showFeedback = false,
                isLoading = false
            )
        }
        AuditLog.record("mission_started", _uiState.value.currentProfileId, exercise.domain.name, exercise.data["subcategoryId"], exercise.id)
    }

    fun onCalculationSymbolDropped(cellId: String, symbol: String) {
        val state = _uiState.value
        val calculation = state.writtenCalculation ?: return
        if (state.isCorrect == true || !calculation.acceptsSymbol(cellId, symbol)) return

        val normalizedSymbol = if (symbol == "-") "−" else symbol
        val oldValue = state.calculationEntries[cellId].orEmpty()
        val maxLength = calculation.maxLengthFor(cellId)
        val newValue = when {
            maxLength <= 1 -> normalizedSymbol
            oldValue.length < maxLength -> oldValue + normalizedSymbol
            else -> normalizedSymbol
        }
        val updatedEntries = state.calculationEntries + (cellId to newValue)
        _uiState.update {
            it.copy(
                calculationEntries = updatedEntries,
                userInput = calculation.composeVisibleAnswer(updatedEntries),
                isCorrect = null,
                isWrong = false,
                showFeedback = false
            )
        }
        if (calculation.isAnswerComplete(updatedEntries)) {
            checkWrittenCalculation()
        }
    }

    fun onCalculationCellClicked(cellId: String) {
        val state = _uiState.value
        val calculation = state.writtenCalculation ?: return
        if (state.isCorrect == true || cellId !in calculation.editableCellIds) return
        val updatedEntries = state.calculationEntries - cellId
        _uiState.update {
            it.copy(
                calculationEntries = updatedEntries,
                userInput = calculation.composeVisibleAnswer(updatedEntries),
                isCorrect = null,
                isWrong = false,
                showFeedback = false
            )
        }
    }

    fun onNumberClick(number: String) {
        AuditLog.record("number_key_pressed", _uiState.value.currentProfileId, exerciseId = _uiState.value.currentExercise?.id)
        val state = _uiState.value
        if (state.isCorrect == true) return
        when (number) {
            "." -> {
                if (state.userInput.contains('.') || state.userInput.contains(',')) return
                val next = if (state.userInput.isBlank()) "0." else state.userInput + "."
                _uiState.update { it.copy(userInput = next) }
            }
            else -> _uiState.update { it.copy(userInput = it.userInput + number) }
        }
    }

    fun onDeleteClick() {
        if (_uiState.value.isCorrect == true) return
        _uiState.update { it.copy(userInput = it.userInput.dropLast(1)) }
    }

    fun resetExercise() {
        _uiState.update { state ->
            state.copy(
                userInput = "",
                calculationEntries = if (state.writtenCalculation != null) emptyMap() else state.calculationEntries,
                isCorrect = null,
                isWrong = false,
                showFeedback = false
            )
        }
    }

    fun onTextInputChanged(value: String) {
        if (_uiState.value.isCorrect == true) return
        _uiState.update {
            it.copy(
                userInput = value,
                isCorrect = null,
                isWrong = false,
                showFeedback = false
            )
        }
    }

    fun onChoiceSelected(value: String) {
        AuditLog.record("math_choice_pressed", _uiState.value.currentProfileId, exerciseId = _uiState.value.currentExercise?.id)
        if (_uiState.value.isCorrect == true) return
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        if (value.isWholeInteger()) {
            _uiState.update {
                it.copy(userInput = value, isCorrect = null, isWrong = false, showFeedback = false)
            }
            checkAnswer(value)
            return
        }
        if (state.userInput != value) {
            _uiState.update {
                it.copy(userInput = value, isCorrect = null, isWrong = false, showFeedback = false)
            }
            audioService.speak(
                exercise.localizedOptionSpeech(value, state.exerciseLanguage),
                exercise.optionSpeechLocale(state.exerciseLanguage)
            )
            return
        }
        checkAnswer(value)
    }

    fun onConfirmClick() {
        val state = _uiState.value
        if (state.writtenCalculation != null) {
            checkWrittenCalculation()
        } else {
            checkAnswer(state.userInput)
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

    private fun checkWrittenCalculation() {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        val calculation = state.writtenCalculation ?: return
        if (state.isCorrect == true || !calculation.isAnswerComplete(state.calculationEntries)) return

        val isCorrect = calculation.isAnswerCorrect(state.calculationEntries)
        recordAnswer(exercise, isCorrect)
    }

    private fun checkAnswer(answer: String) {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        if (state.isCorrect == true) return

        val isCorrect = normalizeAnswer(answer) == normalizeAnswer(exercise.localizedAnswer(state.exerciseLanguage))
        recordAnswer(exercise, isCorrect)
    }

    private fun recordAnswer(exercise: ExerciseContent, isCorrect: Boolean) {
        val duration = System.currentTimeMillis() - startTime
        _uiState.update {
            it.copy(isCorrect = isCorrect, isWrong = !isCorrect, showFeedback = true)
        }
        if (isCorrect) {
            audioService.playSuccess()
        } else {
            audioService.playFailure()
        }
        saveProgress(exercise, duration, isCorrect)
    }

    private fun SkillLevel.toGrade(): Int? = if (this == SkillLevel.NOT_APPLICABLE) null else ordinal + 1

    private fun String.isWholeInteger(): Boolean = trim().replace(" ", "").replace(".", "").matches(Regex("-?\\d+"))

    private fun ExerciseContent.optionSpeechLocale(defaultLocale: String): String =
        data["audioTargetLocale"] ?: defaultLocale

    private suspend fun nextGeneratedMissionNumber(
        profileId: String,
        subcategoryId: String,
        level: SkillLevel
    ): Int {
        val grade = level.toGrade() ?: return 1
        val idPrefix = "generated-$subcategoryId-g$grade-"
        val completedMissionNumbers = progressRepository.getProgressByChild(profileId).first()
            .asSequence()
            .filter { it.answerCorrect && it.exerciseId.startsWith(idPrefix) }
            .mapNotNull { event ->
                event.metadata["missionNumber"]?.toIntOrNull()
                    ?: event.exerciseId.removePrefix(idPrefix).toIntOrNull()
            }
            .filter { it > 0 }
            .toSet()

        var missionNumber = 1
        while (missionNumber in completedMissionNumbers) missionNumber += 1
        return missionNumber
    }

    private suspend fun orderUnfinishedFirst(
        candidates: List<ExerciseContent>,
        profileId: String
    ): List<ExerciseContent> {
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
                metadata = mapOf(
                    "progressKey" to (exercise.data["progressKey"] ?: exercise.id),
                    "subcategoryId" to (exercise.data["subcategoryId"] ?: ""),
                    "grade" to (exercise.data["grade"] ?: (exercise.level.toGrade()?.toString() ?: "")),
                    "missionNumber" to (exercise.data["missionNumber"] ?: ""),
                    "difficultyStars" to (exercise.data["difficultyStars"] ?: ""),
                    "progressState" to if (correct) "completed" else "retry_wrong"
                )
            )
            progressRepository.addProgress(event)
            syncManager.scheduleSync()
        }
    }

    fun nextExercise() {
        AuditLog.record("next_mission_pressed", _uiState.value.currentProfileId, exerciseId = _uiState.value.currentExercise?.id)
        if (currentIndex + 1 < exercises.size) {
            startExercise(currentIndex + 1)
            return
        }

        val operation = generatedOperation
        val generatedId = generatedSubcategoryId
        val level = generatedLevel
        if (generatedId != null && level != null) {
            val nextMissionNumber = exercises.lastOrNull()
                ?.data
                ?.get("missionNumber")
                ?.toIntOrNull()
                ?.plus(1)
                ?: 1
            val nextBatch = if (operation != null) {
                WrittenCalculationGenerator.generate(
                    operation = operation,
                    level = level,
                    startMissionNumber = nextMissionNumber,
                    count = GENERATED_BATCH_SIZE,
                    hardModeEnabled = latestProfile?.hardModeEnabled == true
                )
            } else {
                GeneratedMathExerciseGenerator.generate(
                    subcategoryId = generatedId,
                    level = level,
                    startMissionNumber = nextMissionNumber,
                    count = GENERATED_BATCH_SIZE,
                    hardModeEnabled = latestProfile?.hardModeEnabled == true
                )
            }
            if (nextBatch.isNotEmpty()) {
                val nextIndex = exercises.size
                exercises = exercises + nextBatch
                _uiState.update { it.copy(isLoading = true) }
                startExercise(nextIndex)
                return
            }
        }

        exercises = exercises.shuffled()
        if (exercises.isEmpty()) {
            _uiState.update { it.copy(currentExercise = null, isLoading = false, isFinished = true) }
        } else {
            startExercise(0)
        }
    }

    @Suppress("unused")
    private fun generateRecommendation() {
        val profileId = _uiState.value.currentProfileId ?: return
        viewModelScope.launch {
            val events = progressRepository.getProgressByChild(profileId).first().takeLast(10)
            val metrics = adaptiveEngine.analyzePerformance(events)
            val recommendation = adaptiveEngine.getRecommendation(metrics, giftedMode = false)
            _uiState.update { it.copy(recommendation = recommendation) }
        }
    }
}
