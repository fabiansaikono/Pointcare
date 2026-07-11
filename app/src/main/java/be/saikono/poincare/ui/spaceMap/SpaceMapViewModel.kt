package be.saikono.poincare.ui.spaceMap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.model.AnimationSettings
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LevelProgress
import be.saikono.poincare.model.levelProgressFor
import be.saikono.poincare.model.ProgressEvent
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.SubcategoryCatalog
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.ProgressRepository
import be.saikono.poincare.ui.writing.LanguageCurriculumExerciseFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SpaceMapUiState(
    val currentProfile: ChildProfile? = null,
    val animationSettings: AnimationSettings = AnimationSettings(),
    val overallProgress: LevelProgress = levelProgressFor(0),
    val categoryProgress: Map<LearningDomain, LevelProgress> = emptyMap(),
    val subcategoryCompletions: Map<String, Int> = emptyMap(),
    val worldLevelCompletions: Map<String, Map<Int, Int>> = emptyMap(),
    val subcategoryAvailability: Map<String, Boolean> = emptyMap(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class SpaceMapViewModel(
    private val profileRepository: ProfileRepository,
    private val progressRepository: ProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpaceMapUiState())
    val uiState: StateFlow<SpaceMapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.selectedProfileId
                .flatMapLatest { id ->
                    if (id != null) {
                        combine(
                            profileRepository.getProfileById(id),
                            progressRepository.getProgressByChild(id),
                            settingsDataStore.animationSettings
                        ) { profile, progress, animations ->
                            val completed = progress.filter { it.answerCorrect }
                            val categoryProgress = LearningDomain.entries.associateWith { domain ->
                                levelProgressFor(
                                    completed
                                        .filter { it.domain == domain && !it.isUnassignedExercise() }
                                        .sumOf { it.rewardStampsEarned }
                                )
                            }
                            val subcategoryCompletions = completed
                                .filter { event -> profile?.selectedGradeFor(event.domain)?.let { selectedGrade -> event.grade() == selectedGrade } == true }
                                .groupingBy { event ->
                                    event.metadata["subcategoryId"] ?: subcategoryIdFromProgressKey(event.metadata["progressKey"] ?: event.exerciseId)
                                }.eachCount()
                            val successfulWorldEvents = progress.filter { event ->
                                event.domain == LearningDomain.WORLD_KNOWLEDGE &&
                                    (event.answerCorrect || event.metadata["progressState"] == "retry_correct_not_first_try")
                            }
                            val worldLevelCompletions = successfulWorldEvents
                                .groupBy { event -> event.metadata["subcategoryId"] ?: subcategoryIdFromProgressKey(event.metadata["progressKey"] ?: event.exerciseId) }
                                .mapValues { (_, events) ->
                                    events.groupBy { it.grade() ?: 1 }
                                        .mapValues { (_, gradeEvents) -> gradeEvents.map { it.exerciseId }.distinct().size }
                                }
                            val availability = profile?.let { buildSubcategoryAvailability(it) }.orEmpty()
                            SpaceMapUiState(
                                currentProfile = profile,
                                animationSettings = animations,
                                overallProgress = levelProgressFor(completed.sumOf { it.rewardStampsEarned }),
                                categoryProgress = categoryProgress,
                                subcategoryCompletions = subcategoryCompletions,
                                worldLevelCompletions = worldLevelCompletions,
                                subcategoryAvailability = availability,
                                isLoading = false
                            )
                        }
                    } else {
                        flowOf(SpaceMapUiState(isLoading = false))
                    }
                }
                .collect {
                    _uiState.value = it
                }
        }
    }


    private suspend fun buildSubcategoryAvailability(profile: ChildProfile): Map<String, Boolean> {
        val result = linkedMapOf<String, Boolean>()
        suspend fun xmlAvailable(mainCategory: String, id: String, grade: Int): Boolean = runCatching {
            contentRepository.getExercises(mainCategory, id, grade, difficultyStars = null).isNotEmpty()
        }.getOrDefault(false)

        val mathGrade = profile.mathLevel.toGrade()
        SubcategoryCatalog.mathIds.forEach { id -> result[id] = mathGrade != null }

        val geometryGrade = profile.geometryLevel.toGrade()
        SubcategoryCatalog.geometryIds.forEach { id ->
            result[id] = geometryGrade?.let { xmlAvailable("geometry", id, it) } ?: false
        }

        val readingGrade = profile.readingLevel.toGrade()
        SubcategoryCatalog.readingIds.forEach { id ->
            result[id] = if (readingGrade == null) false else when {
                id in setOf("reading", "listening", "dictation") -> true
                id.startsWith("translation_") -> true
                else -> xmlAvailable("reading", id, readingGrade)
            }
        }

        val writingGrade = profile.writingLevel.toGrade()
        SubcategoryCatalog.writingBaseIds.forEach { id ->
            result[id] = if (writingGrade == null) false else when {
                id == "letter_tracing" || id == "writing" -> true
                id in SubcategoryCatalog.writingFocuses -> {
                    val options = SubcategoryCatalog.optionIdsFor(id)
                    options.forEach { optionId ->
                        result[optionId] = LanguageCurriculumExerciseFactory.hasExercises(
                            LearningDomain.WRITING, optionId, writingGrade
                        )
                    }
                    options.any { result[it] == true }
                }
                else -> xmlAvailable("writing", id, writingGrade)
            }
        }
        return result
    }

    private fun subcategoryIdFromProgressKey(progressKey: String): String = when {
        progressKey.startsWith("generated-sudoku") -> "sudoku"
        progressKey.startsWith("generated-math-crossword") -> "math-crosswords"
        else -> progressKey.substringBefore("_d", progressKey)
    }

    private fun ChildProfile.selectedGradeFor(domain: LearningDomain): Int? = when (domain) {
        LearningDomain.READING -> readingLevel.toGrade()
        LearningDomain.WRITING -> writingLevel.toGrade()
        LearningDomain.MATH -> mathLevel.toGrade()
        LearningDomain.PUZZLE -> mathLevel.toGrade()
        LearningDomain.WORLD_KNOWLEDGE -> null
        LearningDomain.GEOMETRY -> geometryLevel.toGrade()
    }

    private fun SkillLevel.toGrade(): Int? = if (this == SkillLevel.NOT_APPLICABLE) null else ordinal + 1

    private fun ProgressEvent.grade(): Int? = metadata["grade"]?.toIntOrNull()
        ?: GRADE_IN_PROGRESS_KEY.find(metadata["progressKey"] ?: exerciseId)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun ProgressEvent.isUnassignedExercise(): Boolean = metadata["subcategoryId"].isNullOrBlank()

    companion object {
        private val GRADE_IN_PROGRESS_KEY = Regex("_g(\\d+)(?:_|$)")
    }
}
