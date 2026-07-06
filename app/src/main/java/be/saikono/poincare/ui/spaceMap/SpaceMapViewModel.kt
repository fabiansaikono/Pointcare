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
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.ProgressRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SpaceMapUiState(
    val currentProfile: ChildProfile? = null,
    val animationSettings: AnimationSettings = AnimationSettings(),
    val overallProgress: LevelProgress = levelProgressFor(0),
    val categoryProgress: Map<LearningDomain, LevelProgress> = emptyMap(),
    val subcategoryCompletions: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class SpaceMapViewModel(
    private val profileRepository: ProfileRepository,
    private val progressRepository: ProgressRepository,
    private val settingsDataStore: SettingsDataStore
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
                                levelProgressFor(completed.filter { it.domain == domain }.sumOf { it.rewardStampsEarned })
                            }
                            val subcategoryCompletions = completed
                                .filter { event -> profile?.selectedGradeFor(event.domain)?.let { selectedGrade -> event.grade() == selectedGrade } == true }
                                .groupingBy { event ->
                                    event.metadata["subcategoryId"] ?: subcategoryIdFromProgressKey(event.metadata["progressKey"] ?: event.exerciseId)
                                }.eachCount()
                            SpaceMapUiState(
                                currentProfile = profile,
                                animationSettings = animations,
                                overallProgress = levelProgressFor(completed.sumOf { it.rewardStampsEarned }),
                                categoryProgress = categoryProgress,
                                subcategoryCompletions = subcategoryCompletions,
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

    private fun subcategoryIdFromProgressKey(progressKey: String): String = when {
        progressKey.startsWith("generated-sudoku") -> "sudoku"
        progressKey.startsWith("generated-math-crossword") -> "math-crosswords"
        else -> progressKey.substringBefore("_d", progressKey)
    }

    private fun ChildProfile.selectedGradeFor(domain: LearningDomain): Int? = when (domain) {
        LearningDomain.READING -> readingLevel.toGrade()
        LearningDomain.WRITING -> writingLevel.toGrade()
        LearningDomain.MATH -> mathLevel.toGrade()
        LearningDomain.PUZZLE -> puzzleLevel.toGrade()
        LearningDomain.WORLD_KNOWLEDGE -> readingLevel.toGrade()
        LearningDomain.GEOMETRY -> mathLevel.toGrade()
    }

    private fun SkillLevel.toGrade(): Int? = if (this == SkillLevel.NOT_APPLICABLE) null else ordinal + 1

    private fun ProgressEvent.grade(): Int? = metadata["grade"]?.toIntOrNull()
        ?: GRADE_IN_PROGRESS_KEY.find(metadata["progressKey"] ?: exerciseId)?.groupValues?.getOrNull(1)?.toIntOrNull()

    companion object {
        private val GRADE_IN_PROGRESS_KEY = Regex("_g(\\d+)(?:_|$)")
    }
}
