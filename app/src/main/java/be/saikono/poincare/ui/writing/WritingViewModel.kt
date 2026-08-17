package be.saikono.poincare.ui.writing

import androidx.compose.ui.geometry.Offset
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
import kotlin.random.Random

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
    val showFeedback: Boolean = false,
    val showLetterChooser: Boolean = false,
    val letterChoices: List<LetterChoice> = emptyList(),
    val randomPracticeUnlocked: Boolean = false
)

data class LetterChoice(
    val exerciseId: String,
    val letter: String,
    val uppercase: Boolean,
    val completed: Boolean,
    val enabled: Boolean
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
    private var selectedGrade: Int? = null
    private var latestProfile: ChildProfile? = null
    private var hasContentRequest = false
    private var contentLoadJob: Job? = null
    private var contentLoadGeneration = 0L
    // POINCARE_REPAIR_V29: mission-cycle
    private var pendingExerciseIds: MutableSet<String> = mutableSetOf()
    private val retryExercises: MutableList<ExerciseContent> = mutableListOf()
    private var firstAttemptForCurrent = true
    private var missionCycleId: String = UUID.randomUUID().toString()
    private var allLetterTracingExercises: List<ExerciseContent> = emptyList()
    private var lessonLetterPracticeActivityId: String? = null

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
                val level = selectedGrade?.let(::skillLevelForGrade) ?: profile.writingLevel
                val isLetterTracing = selectedSubcategoryId == LETTER_TRACING_SUBCATEGORY
                val loadedExercises = if (isLetterTracing) {
                    buildLetterTracingExercises(level, profile.id)
                } else {
                    level.toGrade()?.let { grade ->
                        val domainExercises = contentRepository.getExercises(LearningDomain.WRITING, level)
                        selectedSubcategoryId?.let { subcategoryId ->
                            val subcategoryExercises = contentRepository.getExercises(
                                mainCategory = "writing",
                                subcategoryId = subcategoryId,
                                grade = grade,
                                difficultyStars = selectedDifficultyStars
                            )
                            val supplemental = LanguageCurriculumExerciseFactory.build(
                                domain = LearningDomain.WRITING,
                                subcategoryId = subcategoryId,
                                level = level,
                                selectedDifficulty = selectedDifficultyStars
                            )
                            if (profile.showUnassignedExercises) {
                                (subcategoryExercises + supplemental + domainExercises.unassignedForDifficulty(selectedDifficultyStars)).distinctBy { it.id }
                            } else {
                                (subcategoryExercises + supplemental).distinctBy { it.id }
                            }
                        } ?: if (profile.showUnassignedExercises) {
                            domainExercises
                        } else {
                            domainExercises.filter { !it.isUnassignedExercise() }
                        }
                    }.orEmpty()
                }

                if (requestGeneration != contentLoadGeneration) return@launch
                if (isLetterTracing) {
                    allLetterTracingExercises = loadedExercises
                    if (lessonLetterPracticeSlug() != "woorden_zinnen") {
                        showLetterChooser(profile.id)
                        return@launch
                    }
                }

                exercises = loadedExercises.shuffled()
                pendingExerciseIds = exercises.map { it.id }.toMutableSet()
                retryExercises.clear()
                missionCycleId = UUID.randomUUID().toString()
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
                            showLetterChooser = false,
                            isComplete = false,
                            isFinished = false,
                            isLoading = false
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (requestGeneration == contentLoadGeneration) {
                    exercises = emptyList()
                    _uiState.update { it.copy(currentExercise = null, showLetterChooser = false, isLoading = false) }
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

    fun setLessonLetterPracticeActivity(activityId: String?) {
        lessonLetterPracticeActivityId = activityId?.takeIf { it.startsWith("writing.letter_tracing.") }
    }

    fun setDifficultyStars(stars: Int?) {
        selectedDifficultyStars = stars?.coerceIn(1, 3)
    }

    fun setSelectedGrade(grade: Int?) {
        selectedGrade = grade?.coerceIn(1, 6)
    }

    private fun startExercise(index: Int) {
        val exercise = exercises.getOrNull(index) ?: run {
            _uiState.update { it.copy(currentExercise = null, isLoading = false) }
            return
        }
        currentIndex = index
        startTime = System.currentTimeMillis()
        firstAttemptForCurrent = true
        _uiState.update {
            it.copy(
                currentExercise = exercise,
                userPath = emptyList(),
                userInput = "",
                isCorrect = null,
                isWrong = false,
                showFeedback = false,
                showLetterChooser = false,
                isComplete = false,
                isFinished = false,
                isLoading = false
            )
        }
        AuditLog.record("mission_started", _uiState.value.currentProfileId, exercise.domain.name, exercise.data["subcategoryId"], exercise.id)
    }

    fun onTextInputChanged(value: String) {
        AuditLog.record("writing_text_changed", _uiState.value.currentProfileId, exerciseId = _uiState.value.currentExercise?.id)
        if (_uiState.value.isCorrect == true) return
        _uiState.update { it.copy(userInput = value, isCorrect = null, isWrong = false, showFeedback = false) }
    }

    fun onChoiceSelected(value: String) {
        AuditLog.record("writing_choice_pressed", _uiState.value.currentProfileId, exerciseId = _uiState.value.currentExercise?.id)
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
        val countsForProgress = registerAnswerOutcome(exercise, isCorrect)
        _uiState.update { it.copy(isCorrect = isCorrect, isWrong = !isCorrect, showFeedback = true, isComplete = isCorrect) }
        if (isCorrect) {
            audioService.playSuccess()
            saveProgress(exercise, duration, countsForProgress, attemptCorrect = true)
        } else {
            audioService.playFailure()
            saveProgress(exercise, duration, countsForProgress = false, attemptCorrect = false)
        }
    }

    private fun registerAnswerOutcome(exercise: ExerciseContent, isCorrect: Boolean): Boolean {
        if (!isCorrect) {
            firstAttemptForCurrent = false
            return false
        }
        val countsForProgress = firstAttemptForCurrent && exercise.id in pendingExerciseIds
        if (countsForProgress) {
            pendingExerciseIds.remove(exercise.id)
        } else if (exercise.id in pendingExerciseIds && retryExercises.none { it.id == exercise.id }) {
            retryExercises += exercise
        }
        return countsForProgress
    }

    private fun SkillLevel.toGrade(): Int? = if (this == SkillLevel.NOT_APPLICABLE) null else ordinal + 1
    private fun String.isWholeInteger(): Boolean = trim().replace(" ", "").replace(".", "").matches(Regex("-?\\d+"))
    private fun ExerciseContent.optionSpeechLocale(defaultLocale: String): String = data["audioTargetLocale"] ?: defaultLocale
    private fun ExerciseContent.isUnassignedExercise(): Boolean = data["subcategoryId"].isNullOrBlank()

    private fun List<ExerciseContent>.unassignedForDifficulty(difficultyStars: Int?): List<ExerciseContent> =
        filter { exercise ->
            exercise.isUnassignedExercise() &&
                (difficultyStars == null || exercise.data["difficultyStars"]?.toIntOrNull() == difficultyStars)
        }

    private suspend fun showLetterChooser(profileId: String) {
        val completedIds = progressRepository.getProgressByChild(profileId).first()
            .filter { it.answerCorrect }
            .map { it.exerciseId }
            .toSet()
        val alphabetExercises = allLetterTracingExercises.filter { it.data["tracePhase"] != "random" }
        val targetedLessonPractice = lessonLetterPracticeActivityId != null
        val firstIncompleteIndex = alphabetExercises.indexOfFirst { it.id !in completedIds }
        val allCompleted = firstIncompleteIndex == -1
        val choices = alphabetExercises.mapIndexed { index, exercise ->
            val completed = exercise.id in completedIds
            LetterChoice(
                exerciseId = exercise.id,
                letter = exercise.data["letter"].orEmpty(),
                uppercase = exercise.data["letterCase"] == "uppercase",
                completed = completed,
                enabled = targetedLessonPractice || completed || allCompleted || index == firstIncompleteIndex
            )
        }
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
                isLoading = false,
                showLetterChooser = true,
                letterChoices = choices,
                randomPracticeUnlocked = !targetedLessonPractice && allCompleted
            )
        }
    }

    fun selectTracingLetter(exerciseId: String) {
        val choice = _uiState.value.letterChoices.firstOrNull { it.exerciseId == exerciseId } ?: return
        if (!choice.enabled) return
        val exercise = allLetterTracingExercises.firstOrNull { it.id == exerciseId } ?: return
        exercises = listOf(exercise)
        pendingExerciseIds = mutableSetOf(exercise.id)
        retryExercises.clear()
        missionCycleId = UUID.randomUUID().toString()
        _uiState.update { it.copy(showLetterChooser = false) }
        startExercise(0)
    }

    fun selectRandomTracingLetter() {
        if (!_uiState.value.randomPracticeUnlocked) return
        val randomExercises = allLetterTracingExercises.filter { it.data["tracePhase"] == "random" }
        val exercise = randomExercises.randomOrNull() ?: return
        exercises = listOf(exercise)
        pendingExerciseIds = mutableSetOf(exercise.id)
        retryExercises.clear()
        missionCycleId = UUID.randomUUID().toString()
        _uiState.update { it.copy(showLetterChooser = false) }
        startExercise(0)
    }

    private fun markTracingChoiceCompleted(exerciseId: String) {
        val updated = _uiState.value.letterChoices.map { choice ->
            if (choice.exerciseId == exerciseId) choice.copy(completed = true) else choice
        }
        val firstIncompleteIndex = updated.indexOfFirst { !it.completed }
        val allCompleted = firstIncompleteIndex == -1
        val targetedLessonPractice = lessonLetterPracticeActivityId != null
        _uiState.update { state ->
            state.copy(
                letterChoices = updated.mapIndexed { index, choice ->
                    choice.copy(enabled = targetedLessonPractice || choice.completed || allCompleted || index == firstIncompleteIndex)
                },
                randomPracticeUnlocked = !targetedLessonPractice && allCompleted
            )
        }
    }

    fun onPathUpdate(path: List<Offset>) {
        if (_uiState.value.isComplete) return
        _uiState.update { it.copy(userPath = path) }
    }

    fun onTraceComplete() {
        if (_uiState.value.isComplete) return
        // LetterTracingComponent only calls this after every ordered stroke matched its guide.
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        val duration = System.currentTimeMillis() - startTime
        _uiState.update { it.copy(isComplete = true) }
        pendingExerciseIds.remove(exercise.id)
        if (selectedSubcategoryId == LETTER_TRACING_SUBCATEGORY) {
            markTracingChoiceCompleted(exercise.id)
        }
        audioService.playSuccess()
        saveProgress(exercise, duration, countsForProgress = true, attemptCorrect = true)
    }

    private fun saveProgress(
        exercise: ExerciseContent,
        durationMs: Long,
        countsForProgress: Boolean,
        attemptCorrect: Boolean
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
                answerCorrect = countsForProgress,
                responseTimeMs = durationMs,
                rewardStampsEarned = missionPointsFor(exercise, countsForProgress),
                metadata = mapOf(
                    "progressKey" to (exercise.data["progressKey"] ?: exercise.id),
                    "subcategoryId" to (exercise.data["subcategoryId"] ?: ""),
                    "grade" to (exercise.data["grade"] ?: (exercise.level.toGrade()?.toString() ?: "")),
                    "progressState" to when {
                        countsForProgress -> "completed_first_try"
                        attemptCorrect -> "retry_correct_not_first_try"
                        else -> "retry_wrong"
                    },
                    "missionCycleId" to missionCycleId,
                    "firstTry" to countsForProgress.toString()
                )
            )
            progressRepository.addProgress(event)
            syncManager.scheduleSync()
        }
    }

    fun nextExercise() {
        AuditLog.record("next_mission_pressed", _uiState.value.currentProfileId, exerciseId = _uiState.value.currentExercise?.id)
        if (selectedSubcategoryId == LETTER_TRACING_SUBCATEGORY) {
            _uiState.update {
                it.copy(
                    currentExercise = null,
                    userPath = emptyList(),
                    isComplete = false,
                    isFinished = false,
                    showLetterChooser = true
                )
            }
            return
        }
        if (pendingExerciseIds.isEmpty()) {
            _uiState.update { it.copy(isFinished = true, isLoading = false) }
            return
        }

        if (currentIndex + 1 < exercises.size) {
            _uiState.update { it.copy(isLoading = true) }
            startExercise(currentIndex + 1)
            return
        }

        exercises = retryExercises
            .distinctBy { it.id }
            .filter { it.id in pendingExerciseIds }
            .shuffled()
        retryExercises.clear()
        currentIndex = 0
        if (exercises.isEmpty()) {
            _uiState.update { it.copy(isFinished = true, isLoading = false) }
        } else {
            _uiState.update { it.copy(isLoading = true) }
            startExercise(0)
        }
    }

    private fun buildLetterTracingExercises(
        level: SkillLevel,
        profileId: String
    ): List<ExerciseContent> {
        val lessonSlug = lessonLetterPracticeSlug()
        if (lessonSlug == "woorden_zinnen") return buildWordAndSentenceWritingExercises(level)
        if (lessonSlug == "houding_basislijnen") return buildBasicStrokeExercises(level)
        val lowerCase = ('a'..'z').map { it.toString() }
        val upperCase = ('A'..'Z').map { it.toString() }
        val randomMixed = (lowerCase + upperCase)
            .shuffled(Random(profileId.hashCode() xor LETTER_RANDOM_SEED))
            .take(RANDOM_LETTER_COUNT)

        val ordered = buildList {
            lowerCase.forEachIndexed { index, letter -> add(Triple("lowercase", index, letter)) }
            upperCase.forEachIndexed { index, letter -> add(Triple("uppercase", index, letter)) }
            randomMixed.forEachIndexed { index, letter -> add(Triple("random", index, letter)) }
        }

        val allowedLetters = when (lessonSlug) {
            "kleine_letters_recht" -> setOf("i", "l", "t", "j")
            "kleine_letters_rond" -> setOf("c", "o", "a", "d", "g", "q")
            "kleine_letters_bogen" -> setOf("n", "m", "r", "u", "h", "b", "p")
            "kleine_letters_lussen" -> setOf("e", "f", "s", "k", "v", "w", "x", "y", "z")
            "hoofdletters_a_m" -> ('A'..'M').map(Char::toString).toSet()
            "hoofdletters_n_z" -> ('N'..'Z').map(Char::toString).toSet()
            "alle_letters" -> (lowerCase + upperCase).toSet()
            else -> null
        }
        val selected = ordered.filter { (phase, _, letter) ->
            allowedLetters == null || (phase != "random" && letter in allowedLetters)
        }

        return selected.mapIndexed { sequenceIndex, (phase, phaseIndex, letter) ->
            ExerciseContent(
                id = "letter-tracing-$phase-${phaseIndex + 1}-${letter.lowercase()}",
                domain = LearningDomain.WRITING,
                skill = "letter_tracing",
                section = ExerciseSection.PRACTICE,
                level = level,
                inputMode = InputMode.TRACE_LETTER,
                prompt = LocalizedText(
                    nl = "Trek de letter $letter over.",
                    en = "Trace the letter $letter.",
                    fr = "Repasse la lettre $letter."
                ),
                data = mapOf(
                    "letter" to letter,
                    "letterCase" to if (letter.first().isUpperCase()) "uppercase" else "lowercase",
                    "tracePhase" to phase,
                    "sequenceIndex" to sequenceIndex.toString(),
                    "subcategoryId" to LETTER_TRACING_SUBCATEGORY,
                    "mainCategory" to "writing",
                    "grade" to (level.toGrade()?.toString() ?: "1"),
                    "difficultyStars" to "1",
                    "topic" to "Letters schrijven",
                    "topic_en-US" to "Writing letters",
                    "topic_fr-BE" to "Ecriture des lettres",
                    "progressKey" to "letter-tracing-$phase-${phaseIndex + 1}",
                    "writingStyle" to "flemish-looped-school-hand"
                ),
                answer = "success",
                rewardMetadata = RewardMetadata(stamps = 1)
            )
        }
    }

    private fun lessonLetterPracticeSlug(): String? = lessonLetterPracticeActivityId
        ?.removePrefix("writing.letter_tracing.")
        ?.substringBeforeLast('.')

    private fun buildBasicStrokeExercises(level: SkillLevel): List<ExerciseContent> {
        val strokes = listOf(
            "line-horizontal" to "Trek een horizontale lijn over.",
            "line-vertical" to "Trek een verticale lijn over.",
            "line-slanted" to "Trek een schuine lijn over.",
            "shape-arch" to "Trek de eenvoudige boog over.",
            "shape-circle" to "Trek de ronde vorm over."
        )
        return strokes.mapIndexed { index, (target, prompt) ->
            ExerciseContent(
                id = "prewriting-stroke-${index + 1}",
                domain = LearningDomain.WRITING,
                skill = "prewriting_strokes",
                section = ExerciseSection.PRACTICE,
                level = level,
                inputMode = InputMode.TRACE_LETTER,
                prompt = LocalizedText(nl = prompt, en = "Trace the basic stroke.", fr = "Repasse le tracé de base."),
                data = mapOf(
                    "letter" to target,
                    "letterCase" to "shape",
                    "tracePhase" to "basic-strokes",
                    "subcategoryId" to LETTER_TRACING_SUBCATEGORY,
                    "mainCategory" to "writing",
                    "grade" to "1",
                    "difficultyStars" to "1",
                    "topic" to "Houding en basislijnen",
                    "progressKey" to "prewriting-stroke-${index + 1}"
                ),
                answer = "success",
                rewardMetadata = RewardMetadata(stamps = 1)
            )
        }
    }

    private fun buildWordAndSentenceWritingExercises(level: SkillLevel): List<ExerciseContent> {
        val targets = listOf("maan", "Rob is los.", "Ik zie de maan.")
        return targets.mapIndexed { index, target ->
            ExerciseContent(
                id = "handwriting-word-sentence-${index + 1}",
                domain = LearningDomain.WRITING,
                skill = "word_sentence_writing",
                section = ExerciseSection.PRACTICE,
                level = level,
                inputMode = InputMode.CUSTOM_TEXT_KEYBOARD,
                prompt = LocalizedText(nl = "Schrijf: $target", en = "Write: $target", fr = "Écris : $target"),
                data = mapOf(
                    "subcategoryId" to "writing",
                    "mainCategory" to "writing",
                    "grade" to "1",
                    "difficultyStars" to (index + 1).coerceAtMost(3).toString(),
                    "topic" to "Woorden en korte zinnen schrijven",
                    "progressKey" to "handwriting-word-sentence-${index + 1}"
                ),
                answer = target,
                rewardMetadata = RewardMetadata(stamps = 1)
            )
        }
    }

    companion object {
        private const val LETTER_TRACING_SUBCATEGORY = "letter_tracing"
        private const val RANDOM_LETTER_COUNT = 52
        private const val LETTER_RANDOM_SEED = 0x504F494E
    }
}
