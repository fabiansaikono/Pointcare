package be.saikono.poincare.ui.lesson

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import be.saikono.poincare.data.audio.AudioService
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.domain.curriculum.CurriculumRequirement
import be.saikono.poincare.domain.curriculum.CurriculumCatalogStore
import be.saikono.poincare.domain.curriculum.CurriculumSubcategoryRegistry
import be.saikono.poincare.domain.curriculum.CurriculumUnit
import be.saikono.poincare.domain.curriculum.LessonDefinition
import be.saikono.poincare.domain.curriculum.ParentSupportLevel
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifest
import be.saikono.poincare.domain.curriculum.manifest.CurriculumEnrichmentPolicy
import be.saikono.poincare.domain.curriculum.v2.LessonCatalogV2
import be.saikono.poincare.domain.curriculum.v2.LessonContentBlockTypeV2
import be.saikono.poincare.domain.curriculum.v2.LessonDefinitionV2
import be.saikono.poincare.domain.curriculum.v2.LessonEvidenceSourceV2
import be.saikono.poincare.domain.curriculum.v2.LessonFeedbackPolicyV2
import be.saikono.poincare.domain.curriculum.v2.LessonGateStateV2
import be.saikono.poincare.domain.curriculum.v2.LessonPublicationStatusV2
import be.saikono.poincare.domain.curriculum.v2.LessonStageV2
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import be.saikono.poincare.domain.curriculum.v2.LessonStatusServiceV2
import be.saikono.poincare.domain.curriculum.v2.exerciseSeries
import be.saikono.poincare.domain.curriculum.v2.isExercises
import be.saikono.poincare.domain.curriculum.v2.isTheory
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.localizedAnswer
import be.saikono.poincare.model.localizedData
import be.saikono.poincare.model.localizedOptionSpeech
import be.saikono.poincare.model.localizedPromptTts
import be.saikono.poincare.model.selectedGradesFor
import be.saikono.poincare.model.selectedGradesForPuzzle
import be.saikono.poincare.model.lesson.LessonMasteryLevel
import be.saikono.poincare.model.lesson.LessonProgress
import be.saikono.poincare.model.lesson.LessonProgressState
import be.saikono.poincare.model.lesson.RecordLessonAttemptRequest
import be.saikono.poincare.model.lesson.RecordLessonAttemptResult
import be.saikono.poincare.model.lesson.LessonQuestionEvidenceV2
import be.saikono.poincare.model.lesson.RecordLessonAssessmentRequestV2
import be.saikono.poincare.model.lesson.RecordLessonAssessmentResultV2
import be.saikono.poincare.model.lesson.RecordLessonStageProgressRequestV2
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.repository.LessonProgressRepository
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.ui.home.localizedSubcategoryName
import java.util.UUID
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val RESUMABLE_STAGE_TYPES = setOf(
    LessonStageTypeV2.EXERCISES,
    LessonStageTypeV2.PRACTISE,
    LessonStageTypeV2.APPLY_DEMONSTRATE
)

private fun stageEstimatedStepCount(stage: LessonStageV2): Int {
    val contentPages = when {
        stage.contentBlocks.isEmpty() -> 0
        stage.type.isTheory() -> stage.contentBlocks
            .filterNot { it.type == LessonContentBlockTypeV2.UITLEG_OUDERS }
            .map { it.pageId }
            .distinct()
            .size
        else -> stage.contentBlocks.size
    }
    return contentPages + stage.exerciseSets.sumOf { it.itemCount }
}

internal fun isLessonPhaseAvailable(
    phaseIndex: Int,
    orderedStageIds: List<String>,
    completedStageIds: Set<String>,
    reachedStageIds: Set<String> = emptySet()
): Boolean {
    if (phaseIndex !in orderedStageIds.indices) return false
    if (phaseIndex == 0) return true
    if (orderedStageIds.take(phaseIndex).all { it in completedStageIds }) return true

    // Unlocking is monotonic: replaying Theory or completing the final test may
    // never lock an exercise series that the child already reached.
    val furthestReachedIndex = orderedStageIds.indexOfLast { it in reachedStageIds }
    return furthestReachedIndex >= phaseIndex - 1
}

internal fun lessonPhaseProgressPercent(
    completed: Boolean,
    currentStepIndex: Int?,
    totalSteps: Int
): Int = when {
    completed -> 100
    currentStepIndex == null || totalSteps <= 0 -> 0
    else -> (currentStepIndex * 100 / totalSteps).coerceIn(0, 99)
}

/** User-facing lesson parts: Theory, Exercises (with nested series), and Test. */
internal fun logicalLessonPartCount(stages: List<LessonStageV2>): Int = listOf(
    stages.any { it.type.isTheory() },
    stages.any { it.type.isExercises() },
    stages.any { it.type == LessonStageTypeV2.MASTERY_TEST }
).count { it }

internal fun lessonThemeTitle(unitId: String, fallback: String, locale: String): String {
    val key = when {
        unitId.endsWith(".nl.writing") -> "writing_expression"
        unitId.endsWith(".nl.speaking") -> "speaking"
        unitId.endsWith(".concrete-objects.addition") -> "concrete_addition"
        unitId.endsWith(".concrete-objects.subtraction") -> "concrete_subtraction"
        else -> ""
    }
    return when (locale.substringBefore('-').substringBefore('_').lowercase()) {
        "fr" -> when (key) {
            "writing_expression" -> "Écriture et expression écrite"
            "speaking" -> "Expression orale et présentation"
            "concrete_addition" -> "Addition avec des images choisies"
            "concrete_subtraction" -> "Soustraction avec des images choisies"
            else -> fallback
        }
        "en" -> when (key) {
            "writing_expression" -> "Writing and written expression"
            "speaking" -> "Speaking and presenting"
            "concrete_addition" -> "Addition with chosen pictures"
            "concrete_subtraction" -> "Subtraction with chosen pictures"
            else -> fallback
        }
        else -> when (key) {
            "writing_expression" -> "Schrijven & schriftelijke expressie"
            "speaking" -> "Spreken & presenteren"
            "concrete_addition" -> "Optellen met gekozen figuren"
            "concrete_subtraction" -> "Aftrekken met gekozen figuren"
            else -> fallback
        }
    }
}

internal fun lessonThemeDescription(lessonCount: Int, locale: String): String = when {
    lessonCount <= 1 -> ""
    locale.substringBefore('-').substringBefore('_').lowercase() == "fr" -> "$lessonCount leçons, de la plus simple à la plus difficile"
    locale.substringBefore('-').substringBefore('_').lowercase() == "en" -> "$lessonCount lessons, from easier to harder"
    else -> "$lessonCount lessen, van eenvoudig naar moeilijker"
}

data class LessonCardUi(
    val id: String,
    val title: String,
    val summary: String,
    val grade: Int,
    val sequence: Int,
    val estimatedMinutes: Int?,
    val requirement: CurriculumRequirement,
    val parentSupport: ParentSupportLevel,
    val available: Boolean,
    val state: LessonProgressState,
    val masteryLevel: LessonMasteryLevel,
    val bestScorePercent: Int,
    val attemptCount: Int,
    val hasSavedDraft: Boolean,
    val isDraft: Boolean = false,
    val architectureVersion: Int = 1,
    val phaseCount: Int? = null,
    val gateStateV2: LessonGateStateV2? = null
)

data class LessonPhasePreviewUi(
    val id: String,
    val type: LessonStageTypeV2,
    val sequence: Int,
    val title: String,
    val sectionTitle: String? = null,
    val seriesIndex: Int? = null,
    val itemCount: Int,
    val available: Boolean,
    val completed: Boolean,
    val progressPercent: Int,
    val resumable: Boolean,
    val hasSavedDraft: Boolean,
    val correctCount: Int? = null,
    val questionCount: Int? = null
)

data class LessonIntroUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val lessonId: String = "",
    val title: String = "",
    val summary: String = "",
    val appLocale: String = "nl-BE",
    val estimatedMinutes: Int? = null,
    val architectureVersion: Int = 1,
    val phaseCount: Int = 0,
    val seriesCount: Int = 0,
    val phases: List<LessonPhasePreviewUi> = emptyList(),
    val hasSavedDraft: Boolean = false,
    val completed: Boolean = false
)

data class LessonStageNavUi(
    val id: String,
    val type: LessonStageTypeV2,
    val sequence: Int,
    val sectionTitle: String? = null
)

data class LessonUnitUi(
    val id: String,
    val title: String,
    val description: String,
    val subcategoryTitle: String,
    val grade: Int,
    val requirement: CurriculumRequirement,
    val completedLessons: Int,
    val totalLessons: Int,
    val lessons: List<LessonCardUi>
)

data class LessonHubUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val domain: LearningDomain? = null,
    val profileName: String = "",
    val appLocale: String = "nl-BE",
    val exerciseLocale: String = "nl-BE",
    val subcategoryTitle: String = "",
    val legacySubcategoryId: String = "",
    val selectedGrades: List<Int> = emptyList(),
    val units: List<LessonUnitUi> = emptyList()
)

data class LessonResultUi(
    val scorePercent: Int,
    val correctCount: Int,
    val questionCount: Int,
    val state: LessonProgressState,
    val masteryLevel: LessonMasteryLevel,
    val bestScorePercent: Int,
    val answerReviews: List<LessonAnswerReview> = emptyList(),
    val requiresRetry: Boolean = false
)

data class LessonPlayerUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val lessonId: String? = null,
    val lessonTitle: String = "",
    val lessonGrade: Int? = null,
    val unitTitle: String = "",
    val appLocale: String = "nl-BE",
    val exerciseLocale: String = "nl-BE",
    val parentSupport: ParentSupportLevel = ParentSupportLevel.NONE,
    val stageTypeV2: LessonStageTypeV2? = null,
    val stageIdV2: String? = null,
    val stageRailV2: List<LessonStageNavUi> = emptyList(),
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val step: LessonStep? = null,
    val answerInput: String = "",
    val previewedChoice: String? = null,
    val countingIconChoice: String? = null,
    val feedbackCorrect: Boolean? = null,
    val wrongAnswers: List<String> = emptyList(),
    val correctAnswerRevealed: Boolean = false,
    val answerFinalized: Boolean = false,
    val practicalConfirmed: Boolean = false,
    val result: LessonResultUi? = null
)

private data class ProfileProgressSnapshot(
    val profile: ChildProfile?,
    val progress: List<LessonProgress>
)

internal enum class AssistedChoiceTapAction { READ, CONFIRM }

internal data class LessonAnswerAttemptOutcome(
    val wrongAnswerCount: Int,
    val revealCorrectAnswer: Boolean,
    val finalized: Boolean
)

internal fun lessonAnswerAttemptOutcome(
    isCorrect: Boolean,
    currentWrongAnswerCount: Int,
    wrongAnswersAllowed: Int
): LessonAnswerAttemptOutcome {
    if (isCorrect) return LessonAnswerAttemptOutcome(
        wrongAnswerCount = currentWrongAnswerCount,
        revealCorrectAnswer = true,
        finalized = true
    )
    val nextWrongAnswerCount = currentWrongAnswerCount + 1
    val finalized = nextWrongAnswerCount >= wrongAnswersAllowed.coerceIn(1, 2)
    return LessonAnswerAttemptOutcome(
        wrongAnswerCount = nextWrongAnswerCount,
        revealCorrectAnswer = finalized,
        finalized = finalized
    )
}

internal fun assistedChoiceTapAction(
    lessonGrade: Int?,
    parentSupport: ParentSupportLevel,
    previewedChoice: String?,
    tappedChoice: String
): AssistedChoiceTapAction = when {
    previewedChoice == tappedChoice -> AssistedChoiceTapAction.CONFIRM
    lessonGrade == 1 -> AssistedChoiceTapAction.READ
    parentSupport == ParentSupportLevel.NONE -> AssistedChoiceTapAction.CONFIRM
    else -> AssistedChoiceTapAction.READ
}

internal fun defaultPhaseSectionTitle(index: Int, locale: String): String {
    val label = ('A'.code + index).takeIf { it <= 'Z'.code }?.toChar()?.toString()
        ?: (index + 1).toString()
    return when (locale.substringBefore('-').substringBefore('_').lowercase()) {
        "fr" -> "Section $label"
        "en" -> "Section $label"
        else -> "Sectie $label"
    }
}

internal fun lessonSeriesTitle(lessonTitle: String, index: Int, locale: String): String = when (
    locale.substringBefore('-').substringBefore('_').lowercase()
) {
    "fr" -> "$lessonTitle, série ${index + 1}"
    "en" -> "$lessonTitle, set ${index + 1}"
    else -> "$lessonTitle, reeks ${index + 1}"
}

internal fun CurriculumManifest.subcategoryIdsForLegacyTile(
    domain: LearningDomain,
    legacySubcategoryId: String
): Set<String> = CurriculumSubcategoryRegistry.conceptualIdsFor(domain, legacySubcategoryId)

internal fun CurriculumManifest.unitMatchesLegacyTile(
    unit: CurriculumUnit,
    domain: LearningDomain,
    legacySubcategoryId: String
): Boolean = CurriculumSubcategoryRegistry.tileFor(unit).let {
    it.domain == domain && it.id == legacySubcategoryId
}

internal fun CurriculumManifest.lessonMatchesLegacyTile(
    unit: CurriculumUnit,
    lesson: LessonDefinition,
    domain: LearningDomain,
    legacySubcategoryId: String
): Boolean = CurriculumSubcategoryRegistry.tileFor(unit, lesson).let {
    it.domain == domain && it.id == legacySubcategoryId
}

private fun CurriculumManifest.canonicalSubcategoryIdFor(
    unit: CurriculumUnit
): String? = categories
    .asSequence()
    .filter { it.domain == unit.domain }
    .flatMap { it.subcategories.asSequence() }
    .firstOrNull { subcategory ->
        unit.subcategoryId == subcategory.id ||
            unit.subcategoryId in subcategory.legacySubcategoryIds
    }
    ?.id

internal fun LessonDefinition.isVisibleForAdaptivePath(
    progressByLessonId: Map<String, LessonProgress>
): Boolean {
    if (CurriculumEnrichmentPolicy.REMEDIATION_TAG !in tags) return true
    val ownProgress = progressByLessonId[id]
    if ((ownProgress?.attemptCount ?: 0) > 0) return true
    val targetLessonId = CurriculumEnrichmentPolicy.adaptiveTargetLessonId(this) ?: return false
    val targetProgress = progressByLessonId[targetLessonId] ?: return false
    return targetProgress.attemptCount > 0 &&
        targetProgress.latestScorePercent < ADAPTIVE_REMEDIATION_THRESHOLD_PERCENT
}

private const val ADAPTIVE_REMEDIATION_THRESHOLD_PERCENT: Int = 80

@OptIn(ExperimentalCoroutinesApi::class)
class LessonViewModel(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val settingsDataStore: SettingsDataStore,
    private val lessonProgressRepository: LessonProgressRepository,
    contentRepository: ContentRepository,
    private val audioService: AudioService,
    private val draftStore: LessonDraftStore = SharedPreferencesLessonDraftStore(context)
) : ViewModel() {
    private val runtime = LessonRuntime(contentRepository)
    private val runtimeV2 = LessonRuntimeV2(contentRepository)
    private val _hubState = MutableStateFlow(LessonHubUiState())
    val hubState: StateFlow<LessonHubUiState> = _hubState.asStateFlow()
    private val _introState = MutableStateFlow(LessonIntroUiState())
    val introState: StateFlow<LessonIntroUiState> = _introState.asStateFlow()
    private val _playerState = MutableStateFlow(LessonPlayerUiState())
    val playerState: StateFlow<LessonPlayerUiState> = _playerState.asStateFlow()

    private var manifest: CurriculumManifest? = null
    private var catalogV2: LessonCatalogV2? = null
    private var profile: ChildProfile? = null
    private var lessonProgress: List<LessonProgress> = emptyList()
    private var selectedDomain: LearningDomain? = null
    private var selectedLegacySubcategoryId: String? = null
    private var selectedIntroLessonId: String? = null
    private var activeSession: ResolvedLessonSession? = null
    private var activeDraft: LessonSessionDraft? = null

    init {
        viewModelScope.launch {
            val loaded = CurriculumCatalogStore.load(context)
            manifest = loaded.manifest
            catalogV2 = loaded.lessonCatalogV2
            refreshHub()
            refreshLessonIntro()
        }
        viewModelScope.launch {
            settingsDataStore.selectedProfileId
                .flatMapLatest { profileId ->
                    if (profileId == null) {
                        flowOf(ProfileProgressSnapshot(null, emptyList()))
                    } else {
                        combine(
                            profileRepository.getProfileById(profileId),
                            lessonProgressRepository.observeProgressForChild(profileId)
                        ) { child, progress -> ProfileProgressSnapshot(child, progress) }
                    }
                }
                .collect { snapshot ->
                    profile = snapshot.profile
                    lessonProgress = snapshot.progress
                    refreshHub()
                    refreshLessonIntro()
                }
        }
    }

    fun showDomain(domainName: String, legacySubcategoryId: String) {
        val domain = runCatching { LearningDomain.valueOf(domainName) }.getOrNull()
        if (selectedDomain != domain || selectedLegacySubcategoryId != legacySubcategoryId) {
            selectedDomain = domain
            selectedLegacySubcategoryId = legacySubcategoryId
            refreshHub()
        }
    }

    fun showLessonIntro(lessonId: String) {
        selectedIntroLessonId = lessonId
        refreshLessonIntro()
    }

    private fun refreshLessonIntro() {
        val lessonId = selectedIntroLessonId ?: return
        val child = profile
        val loadedManifest = manifest
        val loadedCatalogV2 = catalogV2
        if (child == null || loadedManifest == null || loadedCatalogV2 == null) {
            _introState.value = LessonIntroUiState(isLoading = true, lessonId = lessonId)
            return
        }
        val locale = child.appLanguage.ifBlank { "nl-BE" }
        val progress = lessonProgress.firstOrNull { it.lessonId == lessonId }
        loadedCatalogV2.lessons.firstOrNull { it.id == lessonId }?.let { lesson ->
            _introState.value = LessonIntroUiState(
                isLoading = true,
                lessonId = lesson.id,
                title = lesson.title.visibleFor(locale),
                summary = lesson.summary.visibleFor(locale),
                appLocale = locale,
                architectureVersion = 2
            )
            viewModelScope.launch {
                val stageProgress = lessonProgressRepository.getStageProgressV2(child.id, lesson.id)
                if (selectedIntroLessonId != lesson.id || profile?.id != child.id) return@launch
                val completedStageIds = stageProgress.filter { it.completed }.mapTo(mutableSetOf()) { it.stageId }
                val reachedStageIds = stageProgress.mapTo(mutableSetOf()) { it.stageId }
                val exerciseSeries = lesson.exerciseSeries()
                val seriesIndexes = exerciseSeries.mapIndexed { index, stage -> stage.id to index }.toMap()
                val stageProgressById = stageProgress.associateBy { it.stageId }
                val orderedStages = lesson.stages.sortedBy { it.sequence }
                val orderedStageIds = orderedStages.map { it.id }
                val phases = orderedStages.mapIndexed { index, stage ->
                    val draft = draftStore.loadStage(child.id, lesson.id, stage.id)
                        ?.takeIf { it.contentVersion == "catalog-v2-${loadedCatalogV2.definitionVersion}/lesson-${lesson.definitionVersion}" }
                    val totalSteps = stageEstimatedStepCount(stage)
                    val completed = stage.id in completedStageIds
                    val available = isLessonPhaseAvailable(
                        phaseIndex = index,
                        orderedStageIds = orderedStageIds,
                        completedStageIds = completedStageIds,
                        reachedStageIds = reachedStageIds
                    )
                    LessonPhasePreviewUi(
                        id = stage.id,
                        type = stage.type,
                        sequence = stage.sequence,
                        title = stage.title?.visibleFor(locale) ?: stage.type.name,
                        sectionTitle = null,
                        seriesIndex = seriesIndexes[stage.id],
                        itemCount = stage.exerciseSets.sumOf { it.itemCount },
                        available = available,
                        completed = completed,
                        progressPercent = lessonPhaseProgressPercent(completed, draft?.currentStepIndex, totalSteps),
                        resumable = stage.type in RESUMABLE_STAGE_TYPES,
                        hasSavedDraft = draft != null,
                        correctCount = if (stage.type == LessonStageTypeV2.MASTERY_TEST && completed) {
                            stageProgressById[stage.id]?.diagnosticScorePercent?.let { percent ->
                                (percent * stage.exerciseSets.sumOf { it.itemCount } / 100.0).roundToInt()
                            }
                        } else null,
                        questionCount = if (stage.type == LessonStageTypeV2.MASTERY_TEST && completed) {
                            stage.exerciseSets.sumOf { it.itemCount }
                        } else null
                    )
                }
                _introState.value = LessonIntroUiState(
                    isLoading = false,
                    lessonId = lesson.id,
                    title = lesson.title.visibleFor(locale),
                    summary = lesson.summary.visibleFor(locale),
                    appLocale = locale,
                    estimatedMinutes = lesson.stages.mapNotNull { it.estimatedMinutes }.sum(),
                    architectureVersion = 2,
                    phaseCount = logicalLessonPartCount(orderedStages),
                    seriesCount = exerciseSeries.size,
                    phases = phases,
                    hasSavedDraft = phases.any { it.hasSavedDraft },
                    completed = progress?.state == LessonProgressState.COMPLETED
                )
            }
            return
        }
        val unit = loadedManifest.lessonCatalog.units.firstOrNull { candidate ->
            candidate.lessons.any { it.id == lessonId }
        }
        val lesson = unit?.lessons?.firstOrNull { it.id == lessonId }
        if (unit == null || lesson == null) {
            _introState.value = LessonIntroUiState(
                isLoading = false,
                error = "Lesson not found.",
                lessonId = lessonId,
                appLocale = locale
            )
            return
        }
        _introState.value = LessonIntroUiState(
            isLoading = false,
            lessonId = lesson.id,
            title = lesson.title.visibleFor(locale),
            summary = lesson.summary?.visibleFor(locale).orEmpty(),
            appLocale = locale,
            estimatedMinutes = lesson.estimatedMinutes,
            architectureVersion = 1,
            phaseCount = 0,
            hasSavedDraft = draftStore.load(child.id, lesson.id) != null,
            completed = progress?.state == LessonProgressState.COMPLETED
        )
    }

    private fun refreshHub() {
        val loadedManifest = manifest
        val loadedCatalogV2 = catalogV2
        val child = profile
        val domain = selectedDomain
        if (loadedManifest == null || loadedCatalogV2 == null || child == null || domain == null) {
            _hubState.value = _hubState.value.copy(
                isLoading = loadedManifest == null || loadedCatalogV2 == null || child == null,
                domain = domain,
                error = null
            )
            return
        }

        val appLocale = child.appLanguage.ifBlank { "nl-BE" }
        val exerciseLocale = child.exerciseLanguage.ifBlank { appLocale }
        val requestedSubcategoryId = selectedLegacySubcategoryId.orEmpty()
        val canonicalSubcategory = loadedManifest.categories
            .asSequence()
            .filter { it.domain == domain }
            .flatMap { it.subcategories.asSequence() }
            .firstOrNull { requestedSubcategoryId == it.id || requestedSubcategoryId in it.legacySubcategoryIds }
        val canonicalSubcategoryId = canonicalSubcategory?.id ?: requestedSubcategoryId
        val selectedGrades = if (domain == LearningDomain.PUZZLE) {
            child.selectedGradesForPuzzle(requestedSubcategoryId)
        } else {
            child.selectedGradesFor(domain)
        }.filter { it in 1..6 }
        val progressByLesson = lessonProgress.associateBy { it.lessonId }
        val routedV2Lessons = loadedCatalogV2.lessons
            .filter { it.grade in selectedGrades }
            .filter { it.legacyRouting.domain.toLearningDomain() == domain }
            .filter { it.curriculumSubcategoryId == canonicalSubcategoryId }
        val tileTitle = canonicalSubcategory?.title?.get(appLocale)
            ?: canonicalSubcategoryId.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() }
        val v2UnitCards = routedV2Lessons
            .asSequence()
            .groupBy { it.grade to it.unitId }
            .map { (gradeAndUnit, themeLessons) ->
                    val grade = gradeAndUnit.first
                    val unitId = gradeAndUnit.second
                    val orderedThemeLessons = themeLessons.sortedBy { it.sequence }
                    val legacyUnitTitle = loadedManifest.lessonCatalog.units
                        .firstOrNull { it.id == unitId }
                        ?.title
                        ?.visibleFor(appLocale)
                    Triple(grade, orderedThemeLessons.minOf { it.sequence }, orderedThemeLessons.toUi(
                        grade = grade,
                        appLocale = appLocale,
                        progressByLesson = progressByLesson,
                        themeTitle = lessonThemeTitle(
                            unitId = unitId,
                            fallback = legacyUnitTitle ?: if (orderedThemeLessons.size == 1) {
                                orderedThemeLessons.first().title.visibleFor(appLocale)
                            } else tileTitle,
                            locale = appLocale
                        ),
                        themeDescription = lessonThemeDescription(orderedThemeLessons.size, appLocale)
                    ))
            }
            .sortedWith(compareBy<Triple<Int, Int, LessonUnitUi>> { it.first }
                .thenBy { it.second }
                .thenBy { it.third.id })
            .map { it.third }
            .toList()

        _hubState.value = LessonHubUiState(
            isLoading = false,
            domain = domain,
            profileName = child.name,
            appLocale = appLocale,
            exerciseLocale = exerciseLocale,
            subcategoryTitle = tileTitle,
            legacySubcategoryId = canonicalSubcategoryId,
            selectedGrades = selectedGrades,
            units = v2UnitCards
        )
    }

    private fun CurriculumUnit.toUi(
        lessons: List<LessonDefinition>,
        grade: Int,
        appLocale: String,
        subcategoryTitle: String,
        catalog: CurriculumManifest,
        progressByLesson: Map<String, LessonProgress>,
        activeGrades: Set<Int>
    ): LessonUnitUi {
        val lessonCards = lessons.map { lesson ->
            val progress = progressByLesson[lesson.id]
            LessonCardUi(
                id = lesson.id,
                title = lesson.title.visibleFor(appLocale),
                summary = lesson.summary?.visibleFor(appLocale).orEmpty(),
                grade = lesson.grade,
                sequence = lesson.sequence,
                estimatedMinutes = lesson.estimatedMinutes,
                requirement = lesson.requirement,
                parentSupport = lesson.parentSupport,
                available = progress?.state == LessonProgressState.COMPLETED ||
                    LessonAvailabilityEvaluator.isAvailable(
                        catalog.lessonCatalog,
                        this,
                        lesson,
                        progressByLesson,
                        activeGrades
                    ),
                state = progress?.state ?: LessonProgressState.NOT_STARTED,
                masteryLevel = progress?.masteryLevel ?: LessonMasteryLevel.NOT_ASSESSED,
                bestScorePercent = progress?.bestScorePercent ?: 0,
                attemptCount = progress?.attemptCount ?: 0,
                hasSavedDraft = draftStore.load(profile?.id.orEmpty(), lesson.id) != null,
                phaseCount = null
            )
        }
        val progressLessons = if (requirement == CurriculumRequirement.OPTIONAL) {
            lessonCards
        } else {
            lessonCards.filter { it.requirement != CurriculumRequirement.OPTIONAL }
        }
        return LessonUnitUi(
            id = id,
            title = title.visibleFor(appLocale),
            description = description?.visibleFor(appLocale).orEmpty(),
            subcategoryTitle = subcategoryTitle,
            grade = grade,
            requirement = requirement,
            completedLessons = progressLessons.count { it.state == LessonProgressState.COMPLETED },
            totalLessons = progressLessons.size,
            lessons = lessonCards
        )
    }

    private fun List<LessonDefinitionV2>.toUi(
        grade: Int,
        appLocale: String,
        progressByLesson: Map<String, LessonProgress>,
        themeTitle: String,
        themeDescription: String
    ): LessonUnitUi {
        require(isNotEmpty())
        val lessonCards = map { lesson ->
            val progress = progressByLesson[lesson.id]
            val gateState = progress?.effectiveGateStateV2 ?: LessonGateStateV2.NOT_STARTED
            val prerequisitesPassed = LessonStatusServiceV2.prerequisitesSatisfied(
                prerequisiteLessonIds = lesson.prerequisiteLessonIds,
                gateStateForLesson = { prerequisiteId ->
                    progressByLesson[prerequisiteId]?.effectiveGateStateV2
                }
            )
            LessonCardUi(
                id = lesson.id,
                title = lesson.title.visibleFor(appLocale),
                summary = lesson.summary.visibleFor(appLocale),
                grade = lesson.grade,
                sequence = lesson.sequence,
                estimatedMinutes = lesson.stages.mapNotNull { it.estimatedMinutes }.sum(),
                requirement = CurriculumRequirement.MANDATORY,
                parentSupport = ParentSupportLevel.NONE,
                available = LessonStatusServiceV2.unlocksDependants(gateState) || prerequisitesPassed,
                state = progress?.state ?: LessonProgressState.NOT_STARTED,
                masteryLevel = progress?.masteryLevel ?: LessonMasteryLevel.NOT_ASSESSED,
                bestScorePercent = progress?.bestScorePercent ?: 0,
                attemptCount = progress?.attemptCount ?: 0,
                hasSavedDraft = draftStore.load(profile?.id.orEmpty(), lesson.id) != null,
                isDraft = lesson.publicationStatus != LessonPublicationStatusV2.ACTIVE,
                architectureVersion = 2,
                phaseCount = logicalLessonPartCount(lesson.stages),
                gateStateV2 = gateState
            )
        }
        return LessonUnitUi(
            id = "v2.${first().unitId}.g$grade",
            title = themeTitle,
            description = themeDescription,
            subcategoryTitle = first().legacyRouting.subcategoryId,
            grade = grade,
            requirement = CurriculumRequirement.MANDATORY,
            completedLessons = lessonCards.count { it.state == LessonProgressState.COMPLETED },
            totalLessons = lessonCards.size,
            lessons = lessonCards
        )
    }

    fun openLesson(
        lessonId: String,
        forceNewAttempt: Boolean = false,
        stageIdV2: String? = null
    ) {
        catalogV2?.lessons?.firstOrNull { it.id == lessonId }?.let {
            openLessonV2(it, stageIdV2, forceNewAttempt)
            return
        }
        if (!forceNewAttempt && activeSession?.lesson?.id == lessonId && _playerState.value.error == null) return
        val loadedManifest = manifest ?: return playerError("Curriculum is still loading.")
        val child = profile ?: return playerError("No child profile is selected.")
        val unit = loadedManifest.lessonCatalog.units.firstOrNull { unit -> unit.lessons.any { it.id == lessonId } }
            ?: return playerError("Lesson not found.")
        val lesson = unit.lessons.first { it.id == lessonId }
        val progressByLesson = lessonProgress.associateBy { it.lessonId }
        val activeGrades = selectedDomain
            ?.let { domain ->
                if (domain == LearningDomain.PUZZLE) {
                    child.selectedGradesForPuzzle(selectedLegacySubcategoryId)
                } else {
                    child.selectedGradesFor(domain)
                }
            }
            .orEmpty()
            .toSet()
        if (!LessonAvailabilityEvaluator.isAvailable(
                loadedManifest.lessonCatalog,
                unit,
                lesson,
                progressByLesson,
                activeGrades
            ) &&
            progressByLesson[lesson.id]?.state != LessonProgressState.COMPLETED
        ) {
            return playerError("Complete the prerequisite lesson first.")
        }

        _playerState.value = LessonPlayerUiState(
            isLoading = true,
            lessonId = lessonId,
            lessonGrade = lesson.grade,
            appLocale = child.appLanguage,
            exerciseLocale = child.exerciseLanguage,
            parentSupport = lesson.parentSupport
        )
        viewModelScope.launch {
            val expectedVersion = "catalog-${loadedManifest.lessonCatalog.definitionVersion}/lesson-${lesson.definitionVersion}"
            val storedDraft = if (forceNewAttempt) null else draftStore.load(child.id, lesson.id)
                ?.takeIf { it.contentVersion == expectedVersion && it.unitId == unit.id }
            if (forceNewAttempt) draftStore.clear(child.id, lesson.id)
            val completedProgress = progressByLesson[lesson.id]
                ?.takeIf { it.state == LessonProgressState.COMPLETED }
            if (!forceNewAttempt && storedDraft == null && completedProgress != null) {
                val latestAttempt = lessonProgressRepository.getAttempts(child.id, lesson.id)
                    .maxByOrNull { it.sequenceNumber }
                activeSession = null
                activeDraft = null
                _playerState.value = LessonPlayerUiState(
                    isLoading = false,
                    lessonId = lesson.id,
                    lessonTitle = lesson.title.visibleFor(child.appLanguage),
                    lessonGrade = lesson.grade,
                    unitTitle = unit.title.visibleFor(child.appLanguage),
                    appLocale = child.appLanguage,
                    exerciseLocale = child.exerciseLanguage,
                    parentSupport = lesson.parentSupport,
                    result = LessonResultUi(
                        scorePercent = latestAttempt?.scorePercent
                            ?: completedProgress.latestScorePercent,
                        correctCount = latestAttempt?.correctCount ?: 0,
                        questionCount = latestAttempt?.questionCount ?: 0,
                        state = completedProgress.state,
                        masteryLevel = completedProgress.masteryLevel,
                        bestScorePercent = completedProgress.bestScorePercent,
                        answerReviews = LessonAnswerReviewCodec.decode(
                            latestAttempt?.metadata?.get(LessonAnswerReviewCodec.METADATA_KEY)
                        )
                    )
                )
                return@launch
            }
            val draft = storedDraft ?: LessonSessionDraft(
                attemptId = UUID.randomUUID().toString(),
                profileId = child.id,
                unitId = unit.id,
                lessonId = lesson.id,
                contentVersion = expectedVersion,
                currentStepIndex = 0,
                correctCount = 0,
                questionCount = 0,
                startedAtUtc = System.currentTimeMillis()
            )
            when (val resolution = runtime.resolve(
                catalog = loadedManifest.lessonCatalog,
                unit = unit,
                lesson = lesson,
                profileId = child.id,
                attemptId = draft.attemptId
            )) {
                is LessonResolutionResult.Rejected -> playerError(
                    (listOf(resolution.message) + resolution.details).joinToString("\n")
                )
                is LessonResolutionResult.Success -> {
                    val session = resolution.session
                    val usableDraft = if (draft.currentStepIndex in session.steps.indices) draft else draft.copy(
                        currentStepIndex = 0,
                        correctCount = 0,
                        questionCount = 0,
                        answerReviews = emptyList(),
                        startedAtUtc = System.currentTimeMillis()
                    )
                    activeSession = session
                    activeDraft = usableDraft
                    draftStore.save(usableDraft)
                    showCurrentStep()
                }
            }
        }
    }

    private fun openLessonV2(
        lesson: LessonDefinitionV2,
        requestedStageId: String?,
        forceNewAttempt: Boolean
    ) {
        if (!forceNewAttempt && activeSession?.lessonV2?.id == lesson.id &&
            _playerState.value.stageIdV2 == requestedStageId && _playerState.value.error == null
        ) return
        val child = profile ?: return playerError("No child profile is selected.")
        val loadedCatalog = catalogV2 ?: return playerError("Lesson catalogue is still loading.")
        val progressByLesson = lessonProgress.associateBy { it.lessonId }
        val prerequisitesPassed = LessonStatusServiceV2.prerequisitesSatisfied(
            prerequisiteLessonIds = lesson.prerequisiteLessonIds,
            gateStateForLesson = { prerequisiteId ->
                progressByLesson[prerequisiteId]?.effectiveGateStateV2
            }
        )
        val ownProgress = progressByLesson[lesson.id]
        if (!prerequisitesPassed && ownProgress?.effectiveGateStateV2?.let(LessonStatusServiceV2::unlocksDependants) != true) {
            return playerError("Complete the prerequisite lesson first.")
        }
        val orderedStages = lesson.stages.sortedBy { it.sequence }
        val requestedStage = requestedStageId?.let { id -> orderedStages.firstOrNull { it.id == id } }
            ?: orderedStages.firstOrNull()
            ?: return playerError("This lesson has no parts.")
        if (requestedStageId != null && requestedStage.id != requestedStageId) {
            return playerError("The selected part was not found.")
        }
        _playerState.value = LessonPlayerUiState(
            isLoading = true,
            lessonId = lesson.id,
            lessonTitle = lesson.title.visibleFor(child.appLanguage),
            lessonGrade = lesson.grade,
            appLocale = child.appLanguage,
            exerciseLocale = child.exerciseLanguage,
            parentSupport = ParentSupportLevel.NONE,
            stageTypeV2 = requestedStage.type,
            stageIdV2 = requestedStage.id
        )
        viewModelScope.launch {
            val stageProgress = lessonProgressRepository.getStageProgressV2(child.id, lesson.id)
            val completedStageIds = stageProgress.filter { it.completed }.mapTo(mutableSetOf()) { it.stageId }
            val reachedStageIds = stageProgress.mapTo(mutableSetOf()) { it.stageId }
            val requestedIndex = orderedStages.indexOfFirst { it.id == requestedStage.id }
            if (!isLessonPhaseAvailable(
                    phaseIndex = requestedIndex,
                    orderedStageIds = orderedStages.map { it.id },
                    completedStageIds = completedStageIds,
                    reachedStageIds = reachedStageIds
                )
            ) {
                playerError("Complete the previous part first.")
                return@launch
            }
            val expectedVersion = "catalog-v2-${loadedCatalog.definitionVersion}/lesson-${lesson.definitionVersion}"
            val resumable = requestedStage.type in RESUMABLE_STAGE_TYPES
            val storedDraft = if (!forceNewAttempt && resumable) {
                draftStore.loadStage(child.id, lesson.id, requestedStage.id)
                    ?.takeIf {
                        it.contentVersion == expectedVersion &&
                            it.unitId == lesson.unitId &&
                            it.stageIdV2 == requestedStage.id
                    }
            } else null
            if (forceNewAttempt || !resumable) {
                draftStore.clearStage(child.id, lesson.id, requestedStage.id)
            }
            val draft = storedDraft ?: LessonSessionDraft(
                attemptId = UUID.randomUUID().toString(),
                profileId = child.id,
                unitId = lesson.unitId,
                lessonId = lesson.id,
                contentVersion = expectedVersion,
                currentStepIndex = 0,
                correctCount = 0,
                questionCount = 0,
                startedAtUtc = System.currentTimeMillis(),
                stageIdV2 = requestedStage.id
            )
            when (val resolution = runtimeV2.resolve(
                lesson = lesson,
                profileId = child.id,
                attemptId = draft.attemptId,
                catalogDefinitionVersion = loadedCatalog.definitionVersion
            )) {
                is LessonResolutionResult.Rejected -> playerError(
                    (listOf(resolution.message) + resolution.details).joinToString("\n")
                )
                is LessonResolutionResult.Success -> {
                    val stageSteps = resolution.session.steps.filter { it.stageIdV2() == requestedStage.id }
                    if (stageSteps.isEmpty()) {
                        playerError("The selected part has no runnable content.")
                        return@launch
                    }
                    val session = resolution.session.copy(steps = stageSteps)
                    val usableDraft = if (draft.currentStepIndex in session.steps.indices) draft else draft.copy(
                        currentStepIndex = 0,
                        correctCount = 0,
                        questionCount = 0,
                        answerReviews = emptyList(),
                        startedAtUtc = System.currentTimeMillis()
                    )
                    activeSession = session
                    activeDraft = usableDraft
                    saveActiveDraftIfResumable()
                    showCurrentStep()
                }
            }
        }
    }

    private fun showCurrentStep() {
        val session = activeSession ?: return
        val draft = activeDraft ?: return
        val child = profile ?: return
        val retainedCountingIcon = _playerState.value
            .takeIf { it.lessonId == session.lesson.id }
            ?.countingIconChoice
        _playerState.value = LessonPlayerUiState(
            isLoading = false,
            lessonId = session.lesson.id,
            lessonTitle = session.lesson.title.visibleFor(child.appLanguage),
            lessonGrade = session.lesson.grade,
            unitTitle = session.unit.title.visibleFor(child.appLanguage),
            appLocale = child.appLanguage,
            exerciseLocale = child.exerciseLanguage,
            parentSupport = session.lesson.parentSupport,
            stageTypeV2 = session.steps[draft.currentStepIndex].stageTypeV2(),
            stageIdV2 = session.steps[draft.currentStepIndex].stageIdV2(),
            stageRailV2 = session.lessonV2?.let { lesson ->
                lesson.stages.sortedBy { it.sequence }.map { stage ->
                    LessonStageNavUi(
                        id = stage.id,
                        type = stage.type,
                        sequence = stage.sequence,
                        sectionTitle = null
                    )
                }
            }.orEmpty(),
            currentStepIndex = draft.currentStepIndex,
            totalSteps = session.steps.size,
            step = session.steps[draft.currentStepIndex],
            countingIconChoice = retainedCountingIcon
        )
    }

    fun selectCountingIcon(value: String) {
        if (value !in COUNTING_ICON_CHOICES) return
        _playerState.value = _playerState.value.copy(countingIconChoice = value)
    }

    fun selectChoice(value: String) {
        val state = _playerState.value
        val step = state.step as? LessonStep.Exercise ?: return
        val exercise = step.exercise
        if (state.answerFinalized || exercise.inputMode !in setOf(InputMode.TAP_CHOICE, InputMode.LISTEN_AND_SELECT)) return
        if (assistedChoiceTapAction(state.lessonGrade, state.parentSupport, state.previewedChoice, value) == AssistedChoiceTapAction.READ) {
            audioService.speak(
                exercise.localizedOptionSpeech(value, state.exerciseLocale),
                exercise.data["audioTargetLocale"] ?: state.exerciseLocale
            )
            _playerState.value = state.copy(answerInput = "", previewedChoice = value)
            return
        }
        val correct = answersMatch(value, exercise.localizedAnswer(state.exerciseLocale))
        val outcome = lessonAnswerAttemptOutcome(correct, state.wrongAnswers.size, step.wrongAnswersAllowedV2)
        _playerState.value = state.copy(
            answerInput = value,
            previewedChoice = null,
            feedbackCorrect = correct,
            wrongAnswers = if (correct) state.wrongAnswers else state.wrongAnswers + value,
            correctAnswerRevealed = outcome.revealCorrectAnswer && step.feedbackPolicyV2 != LessonFeedbackPolicyV2.AFTER_TEST,
            answerFinalized = outcome.finalized
        )
        if (step.feedbackPolicyV2 != LessonFeedbackPolicyV2.AFTER_TEST) {
            if (correct) audioService.playSuccess() else audioService.playFailure()
        }
    }

    fun appendNumber(value: String) {
        val state = _playerState.value
        if (state.answerFinalized || value !in NUMBER_KEYS) return
        _playerState.value = state.copy(answerInput = (state.answerInput + value).take(12))
    }

    fun backspaceNumber() {
        val state = _playerState.value
        if (state.answerFinalized) return
        _playerState.value = state.copy(answerInput = state.answerInput.dropLast(1))
    }

    fun submitNumber() {
        val state = _playerState.value
        val step = state.step as? LessonStep.Exercise ?: return
        val exercise = step.exercise
        if (state.answerFinalized || state.answerInput.isBlank()) return
        val correct = answersMatch(state.answerInput, exercise.localizedAnswer(state.exerciseLocale))
        val submittedAnswer = state.answerInput
        val outcome = lessonAnswerAttemptOutcome(correct, state.wrongAnswers.size, step.wrongAnswersAllowedV2)
        _playerState.value = state.copy(
            answerInput = if (!correct && !outcome.finalized) "" else submittedAnswer,
            feedbackCorrect = correct,
            wrongAnswers = if (correct) state.wrongAnswers else state.wrongAnswers + submittedAnswer,
            correctAnswerRevealed = outcome.revealCorrectAnswer && step.feedbackPolicyV2 != LessonFeedbackPolicyV2.AFTER_TEST,
            answerFinalized = outcome.finalized
        )
        if (step.feedbackPolicyV2 != LessonFeedbackPolicyV2.AFTER_TEST) {
            if (correct) audioService.playSuccess() else audioService.playFailure()
        }
    }

    fun appendText(value: String) {
        val state = _playerState.value
        val exercise = (state.step as? LessonStep.Exercise)?.exercise ?: return
        if (state.answerFinalized || exercise.inputMode != InputMode.CUSTOM_TEXT_KEYBOARD || value.isEmpty()) return
        val missingCount = exercise.localizedData("maskedText", state.exerciseLocale)?.count { it == '_' }
        val limit = missingCount?.takeIf { it > 0 }
            ?: exercise.localizedAnswer(state.exerciseLocale).length.coerceAtLeast(1)
        _playerState.value = state.copy(answerInput = (state.answerInput + value).take(limit))
    }

    fun backspaceText() {
        val state = _playerState.value
        if (state.answerFinalized) return
        _playerState.value = state.copy(answerInput = state.answerInput.dropLast(1))
    }

    fun submitText() {
        val state = _playerState.value
        val step = state.step as? LessonStep.Exercise ?: return
        val exercise = step.exercise
        if (state.answerFinalized || exercise.inputMode != InputMode.CUSTOM_TEXT_KEYBOARD || state.answerInput.isBlank()) return
        val maskedText = exercise.localizedData("maskedText", state.exerciseLocale)
        val submittedAnswer = fillDictationMask(maskedText, state.answerInput)
        if (maskedText?.contains('_') == true && submittedAnswer.contains('_')) return
        val correct = answersMatch(submittedAnswer, exercise.localizedAnswer(state.exerciseLocale))
        val outcome = lessonAnswerAttemptOutcome(correct, state.wrongAnswers.size, step.wrongAnswersAllowedV2)
        _playerState.value = state.copy(
            answerInput = if (!correct && !outcome.finalized) "" else state.answerInput,
            feedbackCorrect = correct,
            wrongAnswers = if (correct) state.wrongAnswers else state.wrongAnswers + submittedAnswer,
            correctAnswerRevealed = outcome.revealCorrectAnswer && step.feedbackPolicyV2 != LessonFeedbackPolicyV2.AFTER_TEST,
            answerFinalized = outcome.finalized
        )
        if (step.feedbackPolicyV2 != LessonFeedbackPolicyV2.AFTER_TEST) {
            if (correct) audioService.playSuccess() else audioService.playFailure()
        }
    }

    fun completeTracing() {
        val state = _playerState.value
        val exercise = (state.step as? LessonStep.Exercise)?.exercise ?: return
        if (exercise.inputMode != InputMode.TRACE_LETTER || state.answerFinalized) return
        _playerState.value = state.copy(
            answerInput = "completed",
            feedbackCorrect = true,
            correctAnswerRevealed = true,
            answerFinalized = true
        )
        if ((state.step as LessonStep.Exercise).feedbackPolicyV2 != LessonFeedbackPolicyV2.AFTER_TEST) {
            audioService.playSuccess()
        }
    }

    fun confirmPractical() {
        if (_playerState.value.step is LessonStep.Practical) {
            _playerState.value = _playerState.value.copy(practicalConfirmed = true)
        }
    }

    fun continueLesson() {
        val session = activeSession ?: return
        val draft = activeDraft ?: return
        val state = _playerState.value
        var correctCount = draft.correctCount
        var questionCount = draft.questionCount
        var answerReviews = draft.answerReviews
        when (state.step) {
            is LessonStep.Exercise -> {
                if (!state.answerFinalized) return
                val correct = state.feedbackCorrect == true && state.wrongAnswers.isEmpty()
                questionCount += 1
                if (correct) correctCount += 1
                val exercise = state.step.exercise
                answerReviews = answerReviews + LessonAnswerReview(
                    questionNumber = questionCount,
                    exerciseId = exercise.id,
                    question = exercise.prompt.get(state.exerciseLocale),
                    givenAnswer = state.wrongAnswers.lastOrNull() ?: state.answerInput,
                    correctAnswer = exercise.localizedAnswer(state.exerciseLocale),
                    correct = correct
                )
            }
            is LessonStep.Practical -> if (!state.practicalConfirmed) return
            is LessonStep.Explanation -> Unit
            null -> return
        }

        val nextIndex = draft.currentStepIndex + 1
        if (nextIndex >= session.steps.size) {
            finishAttempt(correctCount, questionCount, answerReviews)
        } else {
            val updated = draft.copy(
                currentStepIndex = nextIndex,
                correctCount = correctCount,
                questionCount = questionCount,
                answerReviews = answerReviews
            )
            activeDraft = updated
            saveActiveDraftIfResumable()
            showCurrentStep()
        }
    }

    fun previousTheoryPage() {
        val session = activeSession ?: return
        val draft = activeDraft ?: return
        if (_playerState.value.step !is LessonStep.Explanation || draft.currentStepIndex <= 0) return
        val previousIndex = draft.currentStepIndex - 1
        if (session.steps.getOrNull(previousIndex) !is LessonStep.Explanation) return
        activeDraft = draft.copy(currentStepIndex = previousIndex)
        saveActiveDraftIfResumable()
        showCurrentStep()
    }

    private fun finishAttempt(
        correctCount: Int,
        questionCount: Int,
        answerReviews: List<LessonAnswerReview>
    ) {
        val session = activeSession ?: return
        if (session.lessonV2 != null) {
            finishAttemptV2(session, answerReviews)
            return
        }
        val draft = activeDraft ?: return
        val score = if (questionCount == 0) 100 else (correctCount * 100.0 / questionCount).roundToInt()
        _playerState.value = _playerState.value.copy(isLoading = true)
        viewModelScope.launch {
            val completedAt = System.currentTimeMillis()
            val lessonDomain = CurriculumSubcategoryRegistry.tileFor(session.unit, session.lesson).domain
            val masteryThreshold = settingsDataStore.getEffectiveLessonMasteryThresholdPercent(
                profileId = draft.profileId,
                domain = lessonDomain
            ).first()
            val request = RecordLessonAttemptRequest(
                attemptId = draft.attemptId,
                syncId = draft.attemptId,
                childProfileId = draft.profileId,
                unitId = draft.unitId,
                lessonId = draft.lessonId,
                startedAtUtc = draft.startedAtUtc,
                completedAtUtc = completedAt,
                scorePercent = score,
                correctCount = correctCount,
                questionCount = questionCount,
                durationMs = (completedAt - draft.startedAtUtc).coerceAtLeast(0L),
                completed = true,
                completionThresholdPercent = 70,
                masteryThresholdPercent = masteryThreshold,
                generatorSeed = session.generatorSeeds.firstOrNull(),
                contentVersion = session.contentVersion,
                metadata = mapOf(
                    "lessonDefinitionVersion" to session.lesson.definitionVersion.toString(),
                    "seedFingerprints" to session.seedFingerprints.joinToString(","),
                    "stepCount" to session.steps.size.toString(),
                    LessonAnswerReviewCodec.METADATA_KEY to LessonAnswerReviewCodec.encode(answerReviews)
                )
            )
            val recorded = lessonProgressRepository.recordAttempt(request)
            val progress = when (recorded) {
                is RecordLessonAttemptResult.Recorded -> recorded.progress
                is RecordLessonAttemptResult.Duplicate -> recorded.progress
            }
            draftStore.clear(draft.profileId, draft.lessonId)
            activeDraft = null
            _playerState.value = _playerState.value.copy(
                isLoading = false,
                step = null,
                result = LessonResultUi(
                    scorePercent = score,
                    correctCount = correctCount,
                    questionCount = questionCount,
                    state = progress?.state ?: LessonProgressState.IN_PROGRESS,
                    masteryLevel = progress?.masteryLevel ?: LessonMasteryLevel.NOT_ASSESSED,
                    bestScorePercent = progress?.bestScorePercent ?: score,
                    answerReviews = answerReviews
                )
            )
        }
    }

    private fun finishAttemptV2(
        session: ResolvedLessonSession,
        answerReviews: List<LessonAnswerReview>
    ) {
        val lesson = session.lessonV2 ?: return
        val draft = activeDraft ?: return
        val stage = lesson.stages.firstOrNull { it.id == draft.stageIdV2 }
            ?: return playerError("The active part was not found.")
        val reviewByExerciseId = answerReviews.associateBy { it.exerciseId }
        val exerciseSteps = session.steps.filterIsInstance<LessonStep.Exercise>()
        val stageReviews = exerciseSteps.mapNotNull { reviewByExerciseId[it.exercise.id] }
        if (stageReviews.size != exerciseSteps.size) {
            playerError("The part evidence is incomplete.")
            return
        }
        val finalTestStageId = lesson.stages.lastOrNull { it.type == LessonStageTypeV2.MASTERY_TEST }?.id
        val isFinalMasteryTest = stage.type == LessonStageTypeV2.MASTERY_TEST && stage.id == finalTestStageId
        val theoryNeedsRetry = stage.type.isTheory() && stageReviews.any { !it.correct }
        if (isFinalMasteryTest && exerciseSteps.size < lesson.gatePolicy.minimumIndependentItems) {
            playerError("The mastery test evidence is incomplete.")
            return
        }
        _playerState.value = _playerState.value.copy(isLoading = true)
        viewModelScope.launch {
            val completedAt = System.currentTimeMillis()
            val masteryThreshold = settingsDataStore.getEffectiveLessonMasteryThresholdPercent(
                profileId = draft.profileId,
                domain = lesson.legacyRouting.domain.toLearningDomain()
            ).first()
            val stageScore = stageReviews.takeIf { it.isNotEmpty() }
                ?.let { reviews -> (reviews.count { it.correct } * 100.0 / reviews.size).roundToInt() }
            var progress: LessonProgress? = if (theoryNeedsRetry) {
                lessonProgressRepository.getProgress(draft.profileId, lesson.id)
            } else {
                lessonProgressRepository.recordStageProgressV2(
                    RecordLessonStageProgressRequestV2(
                        childProfileId = draft.profileId,
                        unitId = lesson.unitId,
                        lessonId = lesson.id,
                        stageId = stage.id,
                        stageType = stage.type,
                        completed = true,
                        diagnosticScorePercent = stageScore,
                        timestampUtc = completedAt,
                        contentDefinitionVersion = lesson.definitionVersion
                    )
                )
            }
            if (isFinalMasteryTest) {
                val durationPerQuestion = ((completedAt - draft.startedAtUtc).coerceAtLeast(0L) / exerciseSteps.size)
                val questions = exerciseSteps.mapIndexed { index, step ->
                    val review = requireNotNull(reviewByExerciseId[step.exercise.id])
                    LessonQuestionEvidenceV2(
                        questionAttemptId = UUID.nameUUIDFromBytes(
                            "${draft.attemptId}|${step.exercise.id}|$index".toByteArray(StandardCharsets.UTF_8)
                        ).toString(),
                        exerciseId = step.exercise.id,
                        itemFingerprint = step.exercise.data["seedFingerprint"]
                            ?.let { "$it:$index" }
                            ?: step.exercise.id,
                        masteryTags = step.masteryTagsV2,
                        correct = review.correct,
                        hintsUsed = 0,
                        durationMs = durationPerQuestion,
                        answeredAtUtc = completedAt + index
                    )
                }
                val request = RecordLessonAssessmentRequestV2(
                    assessmentAttemptId = draft.attemptId,
                    syncId = draft.attemptId,
                    childProfileId = draft.profileId,
                    unitId = lesson.unitId,
                    lessonId = lesson.id,
                    stageId = stage.id,
                    source = LessonEvidenceSourceV2.MASTERY_TEST,
                    startedAtUtc = draft.startedAtUtc,
                    completedAtUtc = completedAt,
                    hintsUsed = 0,
                    questions = questions,
                    contentVersion = session.contentVersion,
                    contentDefinitionVersion = lesson.definitionVersion,
                    gatePolicy = lesson.gatePolicy.copy(
                        minimumCorrectRatio = masteryThreshold / 100.0
                    ),
                    errorPolicy = lesson.errorRepetitionPolicy,
                    metadata = mapOf(
                        "catalogSchemaVersion" to "2",
                        "seedFingerprints" to session.seedFingerprints.joinToString(","),
                        LessonAnswerReviewCodec.METADATA_KEY to LessonAnswerReviewCodec.encode(stageReviews)
                    )
                )
                progress = when (val result = lessonProgressRepository.recordAssessmentV2(request)) {
                    is RecordLessonAssessmentResultV2.Recorded -> result.progress
                    is RecordLessonAssessmentResultV2.Duplicate ->
                        lessonProgressRepository.getProgress(draft.profileId, lesson.id)
                }
            }
            draftStore.clearStage(draft.profileId, draft.lessonId, stage.id)
            activeDraft = null
            refreshLessonIntro()
            val displayedScore = stageScore ?: 100
            _playerState.value = _playerState.value.copy(
                isLoading = false,
                step = null,
                result = LessonResultUi(
                    scorePercent = displayedScore,
                    correctCount = stageReviews.count { it.correct },
                    questionCount = stageReviews.size,
                    state = progress?.state ?: LessonProgressState.IN_PROGRESS,
                    masteryLevel = progress?.masteryLevel ?: LessonMasteryLevel.NOT_ASSESSED,
                    bestScorePercent = progress?.bestScorePercent ?: displayedScore,
                    answerReviews = stageReviews,
                    requiresRetry = theoryNeedsRetry
                )
            )
        }
    }

    fun replayLesson() {
        val lessonId = activeSession?.lesson?.id ?: _playerState.value.lessonId ?: return
        openLesson(
            lessonId = lessonId,
            forceNewAttempt = true,
            stageIdV2 = _playerState.value.stageIdV2
        )
    }

    fun playCurrentStep() {
        val state = _playerState.value
        when (val step = state.step) {
            is LessonStep.Exercise -> {
                val exercise = step.exercise
                val spoken = exercise.localizedPromptTts(state.exerciseLocale)
                val spokenLocale = exercise.data["audioTargetLocale"] ?: state.exerciseLocale
                audioService.playExerciseAudio(exercise.id, spoken, spokenLocale)
            }
            is LessonStep.Explanation -> audioService.speak(
                step.content.spokenFor(state.exerciseLocale),
                state.exerciseLocale
            )
            is LessonStep.Practical -> audioService.speak(
                step.instructions.spokenFor(state.exerciseLocale),
                state.exerciseLocale
            )
            null -> Unit
        }
    }

    fun pauseLesson() {
        val draft = activeDraft
        val stageType = _playerState.value.stageTypeV2
        when {
            draft == null -> Unit
            activeSession?.lessonV2 == null -> draftStore.save(draft)
            stageType in RESUMABLE_STAGE_TYPES -> draftStore.save(draft)
            draft.stageIdV2 != null -> draftStore.clearStage(draft.profileId, draft.lessonId, draft.stageIdV2)
        }
        audioService.stopPlayback()
    }

    private fun saveActiveDraftIfResumable() {
        val draft = activeDraft ?: return
        if (activeSession?.lessonV2 == null || _playerState.value.stageTypeV2 in RESUMABLE_STAGE_TYPES) {
            draftStore.save(draft)
        }
    }

    private fun playerError(message: String) {
        activeSession = null
        activeDraft = null
        _playerState.value = LessonPlayerUiState(
            isLoading = false,
            error = message,
            appLocale = profile?.appLanguage ?: "nl-BE",
            exerciseLocale = profile?.exerciseLanguage ?: "nl-BE"
        )
    }

    private fun answersMatch(actual: String, expected: String): Boolean =
        normalizeAnswer(actual) == normalizeAnswer(expected)

    private fun normalizeAnswer(value: String): String = value
        .trim()
        .replace('\u00a0', ' ')
        .replace(" ", "")
        .replace(',', '.')
        .lowercase()

    companion object {
        private val NUMBER_KEYS = (0..9).map(Int::toString).toSet() + setOf(",", "-")
        private val COUNTING_ICON_CHOICES = setOf("astronaut", "yellow_car", "flower", "chick")
    }
}

internal fun fillDictationMask(maskedText: String?, enteredLetters: String): String {
    if (maskedText.isNullOrBlank() || '_' !in maskedText) return enteredLetters
    val iterator = enteredLetters.iterator()
    return buildString(maskedText.length) {
        maskedText.forEach { character ->
            append(if (character == '_' && iterator.hasNext()) iterator.nextChar() else character)
        }
    }
}

class LessonViewModelFactory(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val settingsDataStore: SettingsDataStore,
    private val lessonProgressRepository: LessonProgressRepository,
    private val contentRepository: ContentRepository,
    private val audioService: AudioService
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LessonViewModel(
            context = context.applicationContext,
            profileRepository = profileRepository,
            settingsDataStore = settingsDataStore,
            lessonProgressRepository = lessonProgressRepository,
            contentRepository = contentRepository,
            audioService = audioService
        ) as T
    }
}
