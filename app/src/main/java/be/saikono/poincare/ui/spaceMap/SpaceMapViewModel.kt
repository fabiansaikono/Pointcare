package be.saikono.poincare.ui.spaceMap

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.domain.curriculum.CurriculumCatalogStore
import be.saikono.poincare.domain.curriculum.CurriculumRequirement
import be.saikono.poincare.domain.curriculum.CurriculumSubcategoryRegistry
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifest
import be.saikono.poincare.domain.curriculum.v2.LessonCatalogV2
import be.saikono.poincare.domain.curriculum.v2.LessonPublicationStatusV2
import be.saikono.poincare.model.AnimationSettings
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LevelProgress
import be.saikono.poincare.model.ProgressEvent
import be.saikono.poincare.model.SubcategoryCatalog
import be.saikono.poincare.model.gradeNumber
import be.saikono.poincare.model.isMainCategoryEnabled
import be.saikono.poincare.model.isSubcategoryEnabled
import be.saikono.poincare.model.levelProgressFor
import be.saikono.poincare.model.levelPointsEarned
import be.saikono.poincare.model.lesson.LessonProgress
import be.saikono.poincare.model.lesson.LessonProgressState
import be.saikono.poincare.model.selectedGradesFor
import be.saikono.poincare.model.selectedGradesForPuzzle
import be.saikono.poincare.model.skillLevelForGrade
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.repository.LessonProgressRepository
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.ProgressRepository
import be.saikono.poincare.ui.math.GeneratedMathExerciseGenerator
import be.saikono.poincare.ui.math.WrittenOperation
import be.saikono.poincare.ui.home.getSubCategoriesForDomain
import be.saikono.poincare.ui.galaxy.LearningGalaxyUiState
import be.saikono.poincare.ui.galaxy.buildLearningGalaxyUiState
import be.saikono.poincare.ui.lesson.isVisibleForAdaptivePath
import be.saikono.poincare.ui.lesson.toLearningDomain
import be.saikono.poincare.ui.reading.WorldKnowledgeExtraExerciseFactory
import be.saikono.poincare.ui.writing.LanguageCurriculumExerciseFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


data class SpaceMapUiState(
    val currentProfile: ChildProfile? = null,
    val curriculumManifest: CurriculumManifest? = null,
    val animationSettings: AnimationSettings = AnimationSettings(),
    val overallProgress: LevelProgress = levelProgressFor(0),
    val categoryProgress: Map<LearningDomain, LevelProgress> = emptyMap(),
    val overallLessonProgressPercent: Int = 0,
    val categoryLessonProgressPercent: Map<LearningDomain, Int> = emptyMap(),
    val subcategoryCompletions: Map<String, Int> = emptyMap(),
    val subcategoryEarnedXp: Map<String, Int> = emptyMap(),
    val subcategoryGradeCompletions: Map<String, Map<Int, Int>> = emptyMap(),
    val subcategoryGradeAvailability: Map<String, Set<Int>> = emptyMap(),
    val subcategoryLessonProgressPercent: Map<String, Int> = emptyMap(),
    val subcategoryLessonProgressPercentByGrade: Map<String, Map<Int, Int>> = emptyMap(),
    val subcategoryLessonGradeAvailability: Map<String, Set<Int>> = emptyMap(),
    val learningGalaxy: LearningGalaxyUiState = LearningGalaxyUiState(),
    val availableGrades: List<Int> = emptyList(),
    val selectedGrade: Int? = null,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class SpaceMapViewModel(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val progressRepository: ProgressRepository,
    private val lessonProgressRepository: LessonProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpaceMapUiState())
    val uiState: StateFlow<SpaceMapUiState> = _uiState.asStateFlow()
    private val selectedGradeOverride = MutableStateFlow<Int?>(null)
    private val availabilityCache = mutableMapOf<String, Map<String, Set<Int>>>()

    init {
        viewModelScope.launch {
            val loaded = CurriculumCatalogStore.load(context)
            val curriculumManifest = loaded.manifest
            val lessonCatalogV2 = loaded.lessonCatalogV2
            settingsDataStore.selectedProfileId
                .flatMapLatest { id ->
                    if (id != null) {
                        combine(
                            profileRepository.getProfileById(id),
                            progressRepository.getProgressByChild(id),
                            combine(
                                lessonProgressRepository.observeProgressForChild(id),
                                lessonProgressRepository.observeAllStageProgressV2(id)
                            ) { lessonProgress, stageProgress -> lessonProgress to stageProgress },
                            settingsDataStore.animationSettings,
                            selectedGradeOverride
                        ) { profile, progress, lessonEvidence, animations, requestedGrade ->
                            val (lessonProgress, stageProgress) = lessonEvidence
                            val completed = progress.filter { it.answerCorrect }
                            val categoryProgress = LearningDomain.entries.associateWith { domain ->
                                levelProgressFor(
                                    completed.filter { it.domain == domain && !it.isUnassignedExercise() }
                                        .sumOf { it.levelPointsEarned() }
                                )
                            }
                            val selectedGradeEvents = completed.filter { event ->
                                profile?.selectedGradesFor(event.domain)?.contains(event.grade()) == true
                            }
                            val subcategoryCompletions = selectedGradeEvents.groupingBy { it.subcategoryId() }.eachCount()
                            val subcategoryEarnedXp = completed
                                .asSequence()
                                .filter { it.domain == LearningDomain.PUZZLE }
                                .groupBy { it.subcategoryId() }
                                .mapValues { (_, events) -> events.sumOf { it.levelPointsEarned() } }
                            val successfulEvents = progress.filter { event ->
                                event.answerCorrect || event.metadata["progressState"] == "retry_correct_not_first_try"
                            }
                            val gradeCompletions = successfulEvents
                                .filter { event -> event.grade()?.let { it in 1..6 } == true }
                                .groupBy { it.subcategoryId() }
                                .mapValues { (_, events) ->
                                    events.groupBy { it.grade() ?: 1 }
                                        .mapValues { (_, gradeEvents) -> gradeEvents.map { it.exerciseId }.distinct().size }
                                }
                            val availability = profile?.let { buildSubcategoryGradeAvailability(it) }.orEmpty()
                            val lessonPercentages = profile?.let {
                                calculateLessonProgressPercentages(
                                    profile = it,
                                    manifest = curriculumManifest,
                                    progress = lessonProgress,
                                    catalogV2 = lessonCatalogV2
                                )
                            } ?: LessonProgressPercentages()
                            val availableGrades = profile
                                ?.let { child ->
                                    LearningDomain.entries
                                        .filter(child::isMainCategoryEnabled)
                                        .flatMap(child::selectedGradesFor)
                                        .distinct()
                                        .sorted()
                                }
                                .orEmpty()
                            val selectedGrade = requestedGrade
                                ?.takeIf { it in availableGrades }
                                ?: availableGrades.firstOrNull()
                            val selectedCategoryProgressPercent = selectedGrade?.let { grade ->
                                lessonPercentages.byDomainAndGrade.mapValues { (_, percentagesByGrade) ->
                                    percentagesByGrade[grade] ?: 0
                                }
                            } ?: lessonPercentages.byDomain
                            val learningGalaxy = profile?.let { child ->
                                buildLearningGalaxyUiState(
                                    profile = child,
                                    manifest = curriculumManifest,
                                    catalog = lessonCatalogV2,
                                    progress = lessonProgress,
                                    categoryProgressPercent = selectedCategoryProgressPercent,
                                    selectedGrade = selectedGrade,
                                    stageProgress = stageProgress
                                )
                            } ?: LearningGalaxyUiState(isLoading = false)
                            SpaceMapUiState(
                                currentProfile = profile,
                                curriculumManifest = curriculumManifest,
                                animationSettings = animations,
                                overallProgress = levelProgressFor(completed.sumOf { it.levelPointsEarned() }),
                                categoryProgress = categoryProgress,
                                overallLessonProgressPercent = lessonPercentages.overallPercent,
                                categoryLessonProgressPercent = selectedCategoryProgressPercent,
                                subcategoryCompletions = subcategoryCompletions,
                                subcategoryEarnedXp = subcategoryEarnedXp,
                                subcategoryGradeCompletions = gradeCompletions,
                                subcategoryGradeAvailability = availability,
                                subcategoryLessonProgressPercent = lessonPercentages.bySubcategory,
                                subcategoryLessonProgressPercentByGrade = lessonPercentages.bySubcategoryAndGrade,
                                subcategoryLessonGradeAvailability = lessonPercentages.subcategoryGradeAvailability,
                                learningGalaxy = learningGalaxy,
                                availableGrades = availableGrades,
                                selectedGrade = selectedGrade,
                                isLoading = false
                            )
                        }
                    } else flowOf(SpaceMapUiState(curriculumManifest = curriculumManifest, isLoading = false))
                }
                .collect { _uiState.value = it }
        }
    }

    fun selectGrade(grade: Int) {
        if (grade in _uiState.value.availableGrades) {
            selectedGradeOverride.value = grade
        }
    }

    private suspend fun buildSubcategoryGradeAvailability(profile: ChildProfile): Map<String, Set<Int>> {
        val cacheKey = LearningDomain.entries.joinToString("|") { domain ->
            "${domain.name}:${profile.selectedGradesFor(domain).joinToString(",")}"
        }
        availabilityCache[cacheKey]?.let { return it }

        val result = linkedMapOf<String, MutableSet<Int>>()
        fun mark(id: String, grade: Int) { result.getOrPut(id) { linkedSetOf() }.add(grade) }
        suspend fun xmlAvailable(mainCategory: String, id: String, grade: Int): Boolean = runCatching {
            contentRepository.getExercises(mainCategory, id, grade, difficultyStars = null).isNotEmpty()
        }.getOrDefault(false)

        profile.selectedGradesFor(LearningDomain.MATH).forEach { grade ->
            SubcategoryCatalog.mathIds.forEach { id ->
                if (
                    (id == "statistics" && grade in setOf(2, 3, 4, 6)) ||
                    GeneratedMathExerciseGenerator.supports(id) ||
                    WrittenOperation.fromSubcategoryId(id) != null ||
                    xmlAvailable("math", id, grade)
                ) mark(id, grade)
            }
        }

        profile.selectedGradesFor(LearningDomain.GEOMETRY).forEach { grade ->
            SubcategoryCatalog.geometryIds.forEach { id ->
                if (GeneratedMathExerciseGenerator.supports(id) || xmlAvailable("geometry", id, grade)) mark(id, grade)
            }
        }

        profile.selectedGradesFor(LearningDomain.READING).forEach { grade ->
            val level = skillLevelForGrade(grade)
            SubcategoryCatalog.readingIds.forEach { id ->
                val generated = id in setOf("reading", "listening", "dictation") ||
                    (id == "stories" && grade in 1..6) ||
                    (id == "speaking" && grade in setOf(1, 2, 6)) ||
                    id.startsWith("translation_") ||
                    LanguageCurriculumExerciseFactory.hasExercises(LearningDomain.READING, id, grade)
                if (generated || xmlAvailable("reading", id, grade)) mark(id, grade)
            }
        }

        profile.selectedGradesFor(LearningDomain.WRITING).forEach { grade ->
            SubcategoryCatalog.writingBaseIds.forEach { id ->
                val available = when {
                    id == "letter_tracing" || id == "writing" -> true
                    id in SubcategoryCatalog.writingFocuses -> {
                        SubcategoryCatalog.optionIdsFor(id).forEach { optionId ->
                            if (SubcategoryCatalog.isFocusAvailable(optionId, grade) &&
                                LanguageCurriculumExerciseFactory.hasExercises(LearningDomain.WRITING, optionId, grade)
                            ) mark(optionId, grade)
                        }
                        SubcategoryCatalog.optionIdsFor(id).any { grade in result[it].orEmpty() }
                    }
                    else -> LanguageCurriculumExerciseFactory.hasExercises(LearningDomain.WRITING, id, grade) || xmlAvailable("writing", id, grade)
                }
                if (available) mark(id, grade)
            }
        }

        profile.selectedGradesFor(LearningDomain.WORLD_KNOWLEDGE).forEach { grade ->
            SubcategoryCatalog.worldThemeIds.forEach { id ->
                if (WorldKnowledgeExtraExerciseFactory.supports(id) || xmlAvailable("world-knowledge", id, grade)) mark(id, grade)
            }
        }

        SubcategoryCatalog.puzzleIds.forEach { id ->
            profile.selectedGradesForPuzzle(id).forEach { grade -> mark(id, grade) }
        }

        val immutable = result.mapValues { it.value.toSet() }
        availabilityCache[cacheKey] = immutable
        return immutable
    }

    private fun ProgressEvent.subcategoryId(): String = metadata["subcategoryId"]
        ?: subcategoryIdFromProgressKey(metadata["progressKey"] ?: exerciseId)

    private fun subcategoryIdFromProgressKey(progressKey: String): String = when {
        progressKey.startsWith("generated-sudoku") -> "sudoku"
        progressKey.startsWith("generated-math-crossword") -> "math-crosswords"
        else -> progressKey.substringBefore("_d", progressKey)
    }

    private fun ProgressEvent.grade(): Int? = metadata["grade"]?.toIntOrNull()
        ?: GRADE_IN_PROGRESS_KEY.find(metadata["progressKey"] ?: exerciseId)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun ProgressEvent.isUnassignedExercise(): Boolean = metadata["subcategoryId"].isNullOrBlank()

    companion object {
        private val GRADE_IN_PROGRESS_KEY = Regex("_g(\\d+)(?:_|$)")
    }
}

internal data class LessonProgressPercentages(
    val overallPercent: Int = 0,
    val byDomain: Map<LearningDomain, Int> = emptyMap(),
    val byDomainAndGrade: Map<LearningDomain, Map<Int, Int>> = emptyMap(),
    val bySubcategory: Map<String, Int> = emptyMap(),
    val bySubcategoryAndGrade: Map<String, Map<Int, Int>> = emptyMap(),
    val subcategoryGradeAvailability: Map<String, Set<Int>> = emptyMap()
)

private data class PublishedLessonRef(
    val id: String,
    val grade: Int,
    val domain: LearningDomain,
    val tileId: String,
    val required: Boolean
)

internal fun calculateLessonProgressPercentages(
    profile: ChildProfile,
    manifest: CurriculumManifest,
    progress: List<LessonProgress>,
    catalogV2: LessonCatalogV2? = null
): LessonProgressPercentages {
    val completedLessonIds = progress
        .asSequence()
        .filter { it.state == LessonProgressState.COMPLETED }
        .mapTo(linkedSetOf()) { it.lessonId }
    val progressByLessonId = progress.associateBy { it.lessonId }
    // The web curriculum V2 catalog is the learner-facing source of truth. Do not
    // mix the historical V1 manifest units into the same hubs/progress counters.
    val canonicalDomainByCategoryId = manifest.categories.associate { category ->
        category.id to category.domain
    }
    val publishedLessons = catalogV2?.lessons.orEmpty()
        .filter { lesson -> profile.isSubcategoryEnabled(lesson.curriculumSubcategoryId) }
        .map { lesson ->
        PublishedLessonRef(
            id = lesson.id,
            grade = lesson.grade,
            domain = canonicalDomainByCategoryId[lesson.curriculumCategoryId]
                ?: lesson.legacyRouting.domain.toLearningDomain(),
            tileId = lesson.curriculumSubcategoryId,
            required = true
        )
        }

    fun percentFor(lessons: List<PublishedLessonRef>): Int? {
        val required = lessons.filter { it.required }
        val denominator = required.ifEmpty { lessons }.distinctBy { it.id }
        return denominator.takeIf { it.isNotEmpty() }?.let { selected ->
            (selected.count { it.id in completedLessonIds } * 100f / selected.size).roundToInt()
        }
    }

    val percentagesByDomainAndGrade = LearningDomain.entries.associateWith { domain ->
        profile.selectedGradesFor(domain)
            .filter { it in ACTIVE_LESSON_GRADES }
            .mapNotNull { grade ->
                percentFor(publishedLessons.filter { it.domain == domain && it.grade == grade })
                    ?.let { grade to it }
            }
            .toMap()
    }
    val byDomain = percentagesByDomainAndGrade.mapValues { (_, values) ->
        values.values.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0
    }
    val overallValues = percentagesByDomainAndGrade
        .filterKeys(profile::isMainCategoryEnabled)
        .values
        .flatMap { it.values }
    val bySubcategory = linkedMapOf<String, Int>()
    val bySubcategoryAndGrade = linkedMapOf<String, Map<Int, Int>>()
    val subcategoryGradeAvailability = linkedMapOf<String, Set<Int>>()
    LearningDomain.entries
        .filterNot { it == LearningDomain.PUZZLE }
        .forEach { domain ->
            getSubCategoriesForDomain(
                domain.name,
                manifest,
                profile.appLanguage,
                profile.selectedGradesFor(domain)
            ).forEach { subcategory ->
                val selectedGrades = profile.selectedGradesFor(domain)
                    .filter { it in ACTIVE_LESSON_GRADES }
                val gradeLessons = selectedGrades.associateWith { grade ->
                    publishedLessons.filter {
                        it.domain == domain && it.tileId == subcategory.id && it.grade == grade
                    }
                }.filterValues { it.isNotEmpty() }
                if (gradeLessons.isNotEmpty()) {
                    subcategoryGradeAvailability[subcategory.id] = gradeLessons.keys
                    val percentagesByGrade = gradeLessons.mapValues { (_, lessons) -> percentFor(lessons) ?: 0 }
                    bySubcategoryAndGrade[subcategory.id] = percentagesByGrade
                    val percentages = percentagesByGrade.values
                    if (percentages.isNotEmpty()) {
                        bySubcategory[subcategory.id] = percentages.average().roundToInt()
                    }
                }
            }
        }
    return LessonProgressPercentages(
        overallPercent = overallValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0,
        byDomain = byDomain,
        byDomainAndGrade = percentagesByDomainAndGrade,
        bySubcategory = bySubcategory,
        bySubcategoryAndGrade = bySubcategoryAndGrade,
        subcategoryGradeAvailability = subcategoryGradeAvailability
    )
}

private val ACTIVE_LESSON_GRADES = 1..6
