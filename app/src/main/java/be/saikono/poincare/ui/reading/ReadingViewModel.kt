package be.saikono.poincare.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.saikono.poincare.data.audio.AudioService
import be.saikono.poincare.data.audit.AuditLog
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.data.sync.SyncManager
import be.saikono.poincare.model.*
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.ProgressRepository
import be.saikono.poincare.ui.writing.LanguageCurriculumExerciseFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
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
    val supportLanguage: String = "nl",
    val dictationWrongKey: String? = null,
    val dictationHintKey: String? = null,
    val dictationHintSequence: Long = 0L,
    val dictationRevealKeys: Set<String> = emptySet(),
    val dictationWrongAttempts: Int = 0
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReadingViewModel(
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val syncManager: SyncManager,
    private val audioService: AudioService,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadingUiState())
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    private var exercises: List<ExerciseContent> = emptyList()
    private var currentIndex = 0
    private var startTime = 0L
    private var translationsUsed = 0
    private var selectedSubcategoryId: String? = null
    private var selectedDifficultyStars: Int? = null
    private var selectedGrade: Int? = null
    private var dictationInactivityJob: Job? = null
    private var dictationWrongKeyJob: Job? = null
    private var latestProfile: ChildProfile? = null
    private var hasContentRequest = false
    private var contentLoadJob: Job? = null
    private var contentLoadGeneration = 0L
    // POINCARE_REPAIR_V29: mission-cycle
    private var pendingExerciseIds: MutableSet<String> = mutableSetOf()
    private val retryExercises: MutableList<ExerciseContent> = mutableListOf()
    private var firstAttemptForCurrent = true
    private var missionCycleId: String = UUID.randomUUID().toString()

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
                            supportLanguage = profile?.supportLanguage ?: "nl-BE",
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
                val level = selectedGrade?.let(::skillLevelForGrade) ?: profile.readingLevel
                val isDictation = selectedSubcategoryId == DICTATION_SUBCATEGORY
                val isTranslation = TranslationExerciseFactory.supports(selectedSubcategoryId)
                val isWorldKnowledge = selectedSubcategoryId in WORLD_SUBCATEGORIES
                val loadedExercises = if (isDictation) {
                    val grade = level.toGrade() ?: 1
                    val sourceExercises = listOf(READING_SUBCATEGORY, LISTENING_SUBCATEGORY)
                        .flatMap { sourceSubcategory ->
                            contentRepository.getExercises(
                                mainCategory = "reading",
                                subcategoryId = sourceSubcategory,
                                grade = grade,
                                difficultyStars = selectedDifficultyStars
                            ) + LanguageCurriculumExerciseFactory.build(
                                domain = LearningDomain.READING,
                                subcategoryId = sourceSubcategory,
                                level = level,
                                selectedDifficulty = selectedDifficultyStars
                            )
                        }
                        .distinctBy { it.id }
                    DictationExerciseFactory.build(
                        level = level,
                        exerciseLocale = profile.exerciseLanguage,
                        selectedDifficulty = selectedDifficultyStars,
                        sourceExercises = sourceExercises
                    )
                } else if (isTranslation) {
                    val vocabularySources = (
                        contentRepository.getExercises(LearningDomain.READING, level) +
                            contentRepository.getExercises(LearningDomain.WRITING, level)
                        ).filter { it.data["subcategoryId"] !in TRANSLATION_SUBCATEGORIES }
                    TranslationExerciseFactory.build(
                        subcategoryId = selectedSubcategoryId.orEmpty(),
                        level = level,
                        selectedDifficulty = selectedDifficultyStars,
                        sourceExercises = vocabularySources
                    )
                } else if (isWorldKnowledge) {
                    val worldGrade = selectedGrade ?: profile.selectedGradesFor(LearningDomain.WORLD_KNOWLEDGE).firstOrNull() ?: 1
                    val themeId = selectedSubcategoryId.orEmpty()
                    val xml = contentRepository.getExercises(
                        mainCategory = "world-knowledge",
                        subcategoryId = themeId,
                        grade = worldGrade,
                        difficultyStars = selectedDifficultyStars
                    )
                    val generated = if (WorldKnowledgeExtraExerciseFactory.supports(themeId)) {
                        WorldKnowledgeExtraExerciseFactory.build(themeId, worldGrade)
                    } else emptyList()
                    (xml + generated).distinctBy { it.id }
                } else {
                    level.toGrade()?.let { grade ->
                        val domainExercises = contentRepository.getExercises(LearningDomain.READING, level)
                        selectedSubcategoryId?.let { subcategoryId ->
                            val subcategoryExercises = contentRepository.getExercises(
                                mainCategory = "reading",
                                subcategoryId = subcategoryId,
                                grade = grade,
                                difficultyStars = selectedDifficultyStars
                            )
                            val supplemental = LanguageCurriculumExerciseFactory.build(
                                domain = LearningDomain.READING,
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
                            selectedOption = null,
                            builtWord = "",
                            isCorrect = null,
                            isWrong = false,
                            isFinished = false,
                            showFeedback = false,
                            isLoading = false
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (requestGeneration == contentLoadGeneration) {
                    exercises = emptyList()
                    _uiState.update { it.copy(currentExercise = null, isLoading = false, isFinished = false) }
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

    fun setSelectedGrade(grade: Int?) {
        selectedGrade = grade?.coerceIn(1, 6)
    }

    private fun startExercise(index: Int) {
        currentIndex = index
        startTime = System.currentTimeMillis()
        firstAttemptForCurrent = true
        translationsUsed = 0
        val exercise = exercises.getOrNull(index) ?: run {
            _uiState.update { it.copy(currentExercise = null, isLoading = false) }
            return
        }
        _uiState.update {
            it.copy(
                currentExercise = exercise,
                selectedOption = null,
                builtWord = "",
                isCorrect = null,
                isWrong = false,
                isFinished = false,
                showFeedback = false,
                isLoading = false,
                dictationWrongKey = null,
                dictationHintKey = null,
                dictationRevealKeys = emptySet(),
                dictationWrongAttempts = 0
            )
        }
        AuditLog.record("mission_started", _uiState.value.currentProfileId, exercise.domain.name, exercise.data["subcategoryId"], exercise.id)
        scheduleDictationHint()
        if (exercise.inputMode == InputMode.LISTEN_AND_SELECT || exercise.inputMode == InputMode.CUSTOM_TEXT_KEYBOARD) {
            playListeningAudio(autoPlay = true)
        }
    }

    fun playListeningAudio(autoPlay: Boolean = false) {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        val audioText = exercise.audioTextFor(state.exerciseLanguage)
        val audioLocale = exercise.audioLocaleFor(state.exerciseLanguage)
        val audioId = exercise.audioIdFor(state.exerciseLanguage) ?: exercise.id
        val fallback = (audioText ?: exercise.speechText?.get(state.exerciseLanguage) ?: exercise.localizedAnswer(state.exerciseLanguage))
            .normalizeListeningCue(audioLocale)
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

    private fun ExerciseContent.audioTextFor(locale: String): String? =
        localizedData("audioText", locale)?.normalizeListeningCue(locale)

    private fun String.normalizeListeningCue(locale: String): String {
        if (!normalizeContentLocale(locale).startsWith("nl")) return this
        val match = DUTCH_SOUND_EXAMPLE.matchEntire(trim()) ?: return this
        return "de klank ${match.groupValues[1].trim()}"
    }
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
        AuditLog.record("answer_option_pressed", _uiState.value.currentProfileId, exerciseId = _uiState.value.currentExercise?.id)
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

    fun onTextCharacterAdded(letter: String) {
        if (_uiState.value.isCorrect == true) return
        val target = normalizeAnswer(_uiState.value.currentExercise?.localizedAnswer(_uiState.value.exerciseLanguage).orEmpty())
        val normalizedLetter = normalizeAnswer(letter)
        val isWrongLetter = normalizedLetter.isNotBlank() && normalizedLetter !in target
        val newWord = _uiState.value.builtWord + letter
        _uiState.update {
            it.copy(
                builtWord = newWord,
                isCorrect = null,
                isWrong = false,
                showFeedback = false,
                dictationWrongKey = if (isWrongLetter) letter.lowercase() else null,
                dictationHintKey = null
            )
        }
        AuditLog.record("dictation_key_pressed", _uiState.value.currentProfileId, exerciseId = _uiState.value.currentExercise?.id, details = mapOf("wrongLetter" to isWrongLetter.toString()))
        if (isWrongLetter) {
            dictationWrongKeyJob?.cancel()
            dictationWrongKeyJob = viewModelScope.launch {
                delay(700)
                _uiState.update { state -> state.copy(dictationWrongKey = null) }
            }
        }
        scheduleDictationHint()
    }

    fun onTextBackspace() {
        if (_uiState.value.isCorrect == true) return
        _uiState.update {
            it.copy(
                builtWord = it.builtWord.dropLast(1),
                isCorrect = null,
                isWrong = false,
                showFeedback = false,
                dictationHintKey = null
            )
        }
        AuditLog.record("dictation_backspace", _uiState.value.currentProfileId, exerciseId = _uiState.value.currentExercise?.id)
        scheduleDictationHint()
    }

    fun submitDictation() {
        val answer = _uiState.value.builtWord
        if (answer.isBlank()) return
        AuditLog.record("dictation_submitted", _uiState.value.currentProfileId, exerciseId = _uiState.value.currentExercise?.id)
        checkAnswer(answer)
    }

    fun onTranslationUsed() {
        translationsUsed++
    }

    private fun checkAnswer(answer: String) {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        val isCorrect = normalizeAnswer(answer) == normalizeAnswer(exercise.localizedAnswer(state.exerciseLanguage))
        val duration = System.currentTimeMillis() - startTime
        val countsForProgress = registerAnswerOutcome(exercise, isCorrect)
        _uiState.update {
            val wrongAttempts = if (isCorrect) 0 else it.dictationWrongAttempts + 1
            val reveal = if (!isCorrect && wrongAttempts >= 2 && exercise.inputMode == InputMode.CUSTOM_TEXT_KEYBOARD) {
                exercise.localizedAnswer(state.exerciseLanguage).lowercase().filterNot { character -> character.isWhitespace() }.map { character -> character.toString() }.toSet()
            } else it.dictationRevealKeys
            it.copy(
                isCorrect = isCorrect,
                isWrong = !isCorrect,
                showFeedback = true,
                dictationWrongAttempts = wrongAttempts,
                dictationRevealKeys = reveal,
                dictationHintKey = null
            )
        }
        if (isCorrect) {
            dictationInactivityJob?.cancel()
            saveProgress(exercise, duration, countsForProgress, attemptCorrect = true)
            audioService.playSuccess()
        } else {
            audioService.playFailure()
            saveProgress(exercise, duration, countsForProgress = false, attemptCorrect = false)
        }
    }

    private fun scheduleDictationHint() {
        dictationInactivityJob?.cancel()
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        if (exercise.inputMode != InputMode.CUSTOM_TEXT_KEYBOARD || state.isCorrect == true) return
        dictationInactivityJob = viewModelScope.launch {
            delay(5_000)
            val current = _uiState.value
            if (current.currentExercise?.id != exercise.id || current.isCorrect == true) return@launch
            val answer = exercise.localizedAnswer(current.exerciseLanguage)
            val typed = current.builtWord
            val firstMismatch = typed.indices.firstOrNull { index -> answer.getOrNull(index)?.lowercaseChar() != typed[index].lowercaseChar() }
            val nextIndex = firstMismatch ?: typed.length
            val next = answer.getOrNull(nextIndex)?.toString()?.lowercase()?.takeIf { it.isNotBlank() } ?: return@launch
            _uiState.update { it.copy(dictationHintKey = next, dictationHintSequence = it.dictationHintSequence + 1) }
            delay(1_000)
            _uiState.update { stateAfter -> if (stateAfter.dictationHintKey == next) stateAfter.copy(dictationHintKey = null) else stateAfter }
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
                translationsUsed = translationsUsed,
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
        if (pendingExerciseIds.isEmpty()) {
            _uiState.update { it.copy(isFinished = true, isLoading = false) }
            return
        }

        if (currentIndex + 1 < exercises.size) {
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
            startExercise(0)
        }
    }

    companion object {
        private const val DICTATION_SUBCATEGORY = "dictation"
private val DUTCH_SOUND_EXAMPLE = Regex("(?i)^(?:de\\s+klank\\s+)?(.+?)\\s+zoals\\s+in\\s+.+$")
        private const val READING_SUBCATEGORY = "reading"
        private const val LISTENING_SUBCATEGORY = "listening"
        private val WORLD_SUBCATEGORIES = SubcategoryCatalog.worldThemeIds
        private val TRANSLATION_SUBCATEGORIES = setOf(
            "translation_nl_to_en", "translation_nl_to_fr", "translation_en_to_nl",
            "translation_en_to_fr", "translation_fr_to_nl", "translation_fr_to_en"
        )
    }
}
