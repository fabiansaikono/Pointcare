package be.saikono.poincare.ui.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.saikono.poincare.data.audio.AudioService
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.data.story.Story
import be.saikono.poincare.data.story.StoryBook
import be.saikono.poincare.data.story.StoryNavigation
import be.saikono.poincare.data.story.StoryProgress
import be.saikono.poincare.data.story.StoryTextRanges
import be.saikono.poincare.data.story.StoryWordRange
import be.saikono.poincare.data.story.normalizedStoryLocale
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.selectedGradesFor
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.StoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryLibraryItem(val story: Story, val progress: StoryProgress)

internal fun storyGradesConfiguredInParentDashboard(profile: ChildProfile): List<Int> =
    profile.selectedGradesFor(LearningDomain.READING)
        .filter { it in 1..6 }
        .ifEmpty { listOf(1) }

data class StoryBookLibraryItem(
    val book: StoryBook,
    val stories: List<StoryLibraryItem>
)

data class StoryUiState(
    val loading: Boolean = true,
    val locale: String = "nl-BE",
    val selectedGrades: List<Int> = listOf(1),
    val books: List<StoryBookLibraryItem> = emptyList(),
    val library: List<StoryLibraryItem> = emptyList(),
    val story: Story? = null,
    val progress: StoryProgress? = null,
    val pageIndex: Int = 0,
    val words: List<StoryWordRange> = emptyList(),
    val highlightedWord: Int? = null,
    val isNarrating: Boolean = false,
    val isPaused: Boolean = false,
    val showReadingModeDialog: Boolean = false,
    val showQuiz: Boolean = false,
    val questionIndex: Int = 0,
    val previewAnswerIndex: Int? = null,
    val selectedAnswerIndex: Int? = null,
    val answerCorrect: Boolean? = null,
    val error: String? = null
)

class StoryViewModel(
    private val storyRepository: StoryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val profileRepository: ProfileRepository,
    private val audioService: AudioService
) : ViewModel() {
    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()
    private var libraryJob: Job? = null
    private var fallbackJob: Job? = null
    private var autoNarrationJob: Job? = null
    private var localeOverride: String? = null
    private var loadedProfileId: String? = null

    init {
        refreshLibrary()
    }

    fun refreshLibrary() {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            runCatching {
                val profileId = settingsDataStore.selectedProfileId.first()
                    ?: error("No active profile")
                val profile = profileRepository.getProfileById(profileId).first()
                    ?: error("Active profile not found")
                if (loadedProfileId != null && loadedProfileId != profileId) {
                    localeOverride = null
                }
                loadedProfileId = profileId
                val locale = (localeOverride ?: profile.exerciseLanguage).normalizedStoryLocale()
                // Deliberately ignore the temporary main-screen grade filter here.
                // Story availability is governed only by the Reading grades configured
                // for this profile in the parent dashboard.
                val selectedGrades = storyGradesConfiguredInParentDashboard(profile)
                val stories = storyRepository.catalogue().stories
                    .filter { it.grade in selectedGrades }
                    .sortedWith(compareBy(Story::grade, Story::difficulty, Story::id))
                val items = stories.map { story ->
                    val saved = settingsDataStore.getStoryProgress(profileId, story.id, locale).first()
                    StoryLibraryItem(
                        story = story,
                        progress = saved.copy(
                            pageIndex = StoryNavigation.clampPage(saved.pageIndex, story.pages.size)
                        )
                    )
                }
                val itemById = items.associateBy { it.story.id }
                val books = storyRepository.bookCatalogue().books
                    .filter { it.grade in selectedGrades }
                    .sortedWith(
                        compareBy<StoryBook> { it.seriesOrder ?: Int.MAX_VALUE }
                            .thenBy { it.bookNumber ?: Int.MAX_VALUE }
                            .thenBy(StoryBook::grade)
                            .thenBy(StoryBook::order)
                            .thenBy(StoryBook::id)
                    )
                    .mapNotNull { book ->
                        val bookStories = book.storyIds.mapNotNull(itemById::get)
                        book.takeIf { bookStories.isNotEmpty() }?.let {
                            StoryBookLibraryItem(book = book, stories = bookStories)
                        }
                    }
                LoadedLibrary(locale, selectedGrades, books, items)
            }.onSuccess { loaded ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        locale = loaded.locale,
                        selectedGrades = loaded.grades,
                        books = loaded.books,
                        library = loaded.items,
                        error = null
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(loading = false, error = error.message ?: "Story catalogue unavailable") }
            }
        }
    }

    fun selectStoryLocale(locale: String) {
        val normalized = locale.normalizedStoryLocale()
        if (localeOverride == normalized && _uiState.value.locale == normalized) return
        stopNarration()
        localeOverride = normalized
        _uiState.update { it.copy(loading = true, error = null) }
        refreshLibrary()
    }

    fun openStory(storyId: String) {
        stopNarration()
        viewModelScope.launch {
            runCatching {
                val story = storyRepository.story(storyId) ?: error("Story not found")
                val profileId = settingsDataStore.selectedProfileId.first()
                    ?: error("No active profile")
                val locale = _uiState.value.locale
                val saved = settingsDataStore.getStoryProgress(profileId, story.id, locale).first()
                val progress = saved.copy(
                    pageIndex = StoryNavigation.clampPage(saved.pageIndex, story.pages.size),
                    lastActivityEpochMillis = System.currentTimeMillis()
                )
                story to progress
            }.onSuccess { (story, progress) ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        story = story,
                        progress = progress,
                        pageIndex = progress.pageIndex,
                        words = StoryTextRanges.tokenize(story.page(progress.pageIndex).text(it.locale)),
                        highlightedWord = null,
                        isNarrating = false,
                        isPaused = false,
                        showReadingModeDialog = true,
                        showQuiz = false,
                        questionIndex = 0,
                        previewAnswerIndex = null,
                        selectedAnswerIndex = null,
                        answerCorrect = null,
                        error = null
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(loading = false, error = error.message ?: "Story unavailable") }
            }
        }
    }

    fun selectReadingMode(readWithMe: Boolean) {
        val state = _uiState.value
        val story = state.story ?: return
        val progress = state.progress?.copy(
            started = true,
            readingMode = if (readWithMe) READING_MODE_LISTEN else READING_MODE_SILENT,
            lastActivityEpochMillis = System.currentTimeMillis()
        ) ?: return
        _uiState.update {
            it.copy(
                progress = progress,
                showReadingModeDialog = false,
                highlightedWord = null,
                isNarrating = false,
                isPaused = false
            )
        }
        saveProgress(progress)
        if (readWithMe && story.hasVoiceOver) scheduleAutomaticNarration()
    }

    fun previousPage() = showPage(StoryNavigation.previousPage(_uiState.value.pageIndex, pageCount()))

    fun nextPage() {
        val state = _uiState.value
        val story = state.story ?: return
        if (state.pageIndex < story.pages.lastIndex) {
            showPage(StoryNavigation.nextPage(state.pageIndex, story.pages.size))
        } else if (story.questions.isNotEmpty()) {
            stopNarration()
            _uiState.update { it.copy(showQuiz = true, questionIndex = 0, previewAnswerIndex = null, selectedAnswerIndex = null, answerCorrect = null) }
        } else {
            markCompleted()
        }
    }

    fun answerQuestion(index: Int) {
        val state = _uiState.value
        val story = state.story ?: return
        val question = story.questions.getOrNull(state.questionIndex) ?: return
        if (state.selectedAnswerIndex != null) return
        if (story.grade == 1 && state.previewAnswerIndex != index) {
            audioService.stopPlayback()
            audioService.speak(question.options(state.locale).getOrNull(index).orEmpty(), state.locale)
            _uiState.update { it.copy(previewAnswerIndex = index) }
            return
        }
        val correct = index == question.correctIndex
        audioService.stopPlayback()
        if (correct) audioService.playSuccess() else audioService.playFailure()
        _uiState.update { it.copy(previewAnswerIndex = null, selectedAnswerIndex = index, answerCorrect = correct) }
    }

    fun readQuestionAloud() {
        val state = _uiState.value
        val question = state.story?.questions?.getOrNull(state.questionIndex) ?: return
        audioService.stopPlayback()
        audioService.speak(question.prompt(state.locale), state.locale)
    }

    fun nextQuestion() {
        audioService.stopPlayback()
        val state = _uiState.value
        val story = state.story ?: return
        if (state.questionIndex < story.questions.lastIndex) {
            _uiState.update {
                it.copy(
                    questionIndex = state.questionIndex + 1,
                    previewAnswerIndex = null,
                    selectedAnswerIndex = null,
                    answerCorrect = null
                )
            }
        } else {
            markCompleted()
        }
    }

    fun finishStory() = markCompleted()

    fun playOrResumeNarration() {
        playOrResumeNarrationFrom(0)
    }

    fun playOrResumeNarrationFrom(characterOffset: Int) {
        val state = _uiState.value
        val story = state.story ?: return
        if (!story.hasVoiceOver) return
        if (state.isNarrating) {
            pauseNarration()
        } else {
            startNarration(
                if (state.isPaused) state.highlightedWord ?: 0
                else wordIndexAtOrAfter(characterOffset)
            )
        }
    }

    fun replayPage() = startNarration(0)

    fun replayPageFrom(characterOffset: Int) = startNarration(wordIndexAtOrAfter(characterOffset))

    fun stopNarration() {
        autoNarrationJob?.cancel()
        autoNarrationJob = null
        fallbackJob?.cancel()
        fallbackJob = null
        audioService.stopPlayback()
        _uiState.update { it.copy(isNarrating = false, isPaused = false, highlightedWord = null) }
    }

    private fun pauseNarration() {
        fallbackJob?.cancel()
        fallbackJob = null
        audioService.stopPlayback()
        _uiState.update { it.copy(isNarrating = false, isPaused = true) }
    }

    private fun startNarration(startWord: Int) {
        val state = _uiState.value
        val story = state.story ?: return
        if (!story.hasVoiceOver) return
        val pageText = story.page(state.pageIndex).spokenText(state.locale)
        val spokenWords = StoryTextRanges.tokenize(pageText)
        if (spokenWords.isEmpty()) return
        val safeStart = startWord.coerceIn(spokenWords.indices)
        val spokenSlice = pageText.substring(spokenWords[safeStart].start)

        fallbackJob?.cancel()
        audioService.stopPlayback()
        _uiState.update {
            it.copy(
                words = StoryTextRanges.tokenize(story.page(state.pageIndex).text(state.locale)),
                highlightedWord = safeStart,
                isNarrating = true,
                isPaused = false
            )
        }
        audioService.speakStory(
            text = spokenSlice,
            locale = state.locale,
            rateMultiplier = if (story.grade == 1) 0.78f else 0.9f,
            endingPauseMillis = 1_000L,
            onRangeStart = { start, end ->
                val sliceWords = StoryTextRanges.tokenize(spokenSlice)
                StoryTextRanges.wordAtRange(sliceWords, start, end)?.let { relative ->
                    _uiState.update {
                        it.copy(highlightedWord = (safeStart + relative).coerceAtMost(it.words.lastIndex))
                    }
                }
            },
            onRangesUnavailable = { startFallback(safeStart, pageText, spokenWords) },
            onDone = {
                fallbackJob?.cancel()
                fallbackJob = null
                _uiState.update { it.copy(isNarrating = false, isPaused = false, highlightedWord = null) }
            }
        )
    }

    private fun wordIndexAtOrAfter(characterOffset: Int): Int {
        val words = _uiState.value.words
        if (words.isEmpty()) return 0
        return words.indexOfFirst { it.endExclusive > characterOffset }
            .takeIf { it >= 0 }
            ?: words.lastIndex
    }

    private fun startFallback(startWord: Int, text: String, words: List<StoryWordRange>) {
        fallbackJob?.cancel()
        fallbackJob = viewModelScope.launch {
            for (index in startWord..words.lastIndex) {
                if (!_uiState.value.isNarrating) break
                _uiState.update { it.copy(highlightedWord = index) }
                delay(StoryTextRanges.estimatedWordDelayMillis(words[index], text, _uiState.value.locale))
            }
        }
    }

    private fun showPage(pageIndex: Int) {
        val state = _uiState.value
        val story = state.story ?: return
        stopNarration()
        val safePage = StoryNavigation.clampPage(pageIndex, story.pages.size)
        val progress = state.progress?.copy(
            pageIndex = safePage,
            started = true,
            lastActivityEpochMillis = System.currentTimeMillis()
        )
        _uiState.update {
            it.copy(
                pageIndex = safePage,
                progress = progress,
                words = StoryTextRanges.tokenize(story.page(safePage).text(it.locale)),
                showQuiz = false
            )
        }
        progress?.let(::saveProgress)
        if (progress?.readingMode == READING_MODE_LISTEN && story.hasVoiceOver) {
            scheduleAutomaticNarration()
        }
    }

    private fun scheduleAutomaticNarration() {
        autoNarrationJob?.cancel()
        autoNarrationJob = viewModelScope.launch {
            delay(1_000L)
            if (!_uiState.value.showReadingModeDialog) startNarration(0)
        }
    }

    private fun markCompleted() {
        stopNarration()
        val state = _uiState.value
        val story = state.story ?: return
        val firstPage = StoryNavigation.completionPage()
        val completed = state.progress?.copy(
            completed = true,
            started = true,
            pageIndex = firstPage,
            lastActivityEpochMillis = System.currentTimeMillis()
        ) ?: return
        _uiState.update {
            it.copy(
                progress = completed,
                pageIndex = firstPage,
                words = StoryTextRanges.tokenize(story.page(firstPage).text(it.locale)),
                highlightedWord = null,
                showQuiz = false,
                questionIndex = 0,
                previewAnswerIndex = null,
                selectedAnswerIndex = null,
                answerCorrect = null
            )
        }
        viewModelScope.launch {
            settingsDataStore.setStoryProgress(completed)
            refreshLibrary()
        }
    }

    private fun saveProgress(progress: StoryProgress) {
        viewModelScope.launch { settingsDataStore.setStoryProgress(progress) }
    }

    private fun pageCount(): Int = _uiState.value.story?.pages?.size ?: 0

    override fun onCleared() {
        libraryJob?.cancel()
        stopNarration()
        super.onCleared()
    }

    private data class LoadedLibrary(
        val locale: String,
        val grades: List<Int>,
        val books: List<StoryBookLibraryItem>,
        val items: List<StoryLibraryItem>
    )

    companion object {
        private const val READING_MODE_SILENT = "silent"
        private const val READING_MODE_LISTEN = "listen"
    }
}
