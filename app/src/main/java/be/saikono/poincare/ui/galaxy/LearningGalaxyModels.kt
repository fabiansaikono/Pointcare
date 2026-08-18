package be.saikono.poincare.ui.galaxy

import androidx.annotation.DrawableRes
import be.saikono.poincare.R
import be.saikono.poincare.data.story.StoryBook
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifest
import be.saikono.poincare.domain.curriculum.v2.LessonCatalogV2
import be.saikono.poincare.domain.curriculum.v2.LessonDefinitionV2
import be.saikono.poincare.domain.curriculum.v2.LessonGateStateV2
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import be.saikono.poincare.domain.curriculum.v2.LessonStatusServiceV2
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.isMainCategoryEnabled
import be.saikono.poincare.model.isSubcategoryEnabled
import be.saikono.poincare.model.lesson.LessonProgress
import be.saikono.poincare.model.lesson.LessonProgressState
import be.saikono.poincare.model.selectedGradesFor
import be.saikono.poincare.repository.LessonStageProgressSnapshotV2
import be.saikono.poincare.ui.home.getSubCategoriesForDomain

enum class GalaxyNodeState {
    LOCKED,
    AVAILABLE,
    RECOMMENDED,
    IN_PROGRESS,
    COMPLETED,
    MASTERED
}

enum class GalaxyPortalDestination {
    STORY_LIBRARY
}

internal const val GALAXY_STORY_TRACK_ID = "language.stories"
internal fun galaxyStoryThemePortalId(themeId: String): String = "galaxy:portal:story-theme:$themeId"
internal fun isGalaxyStoryTrack(trackId: String): Boolean =
    trackId == GALAXY_STORY_TRACK_ID

data class GalaxyPhaseOrbUi(
    val kind: GalaxyOrbKind,
    val state: GalaxyNodeState,
    val progressPercent: Int,
    val stageId: String? = null
)

data class GalaxyLessonNodeUi(
    val id: String,
    val title: String,
    val categoryTitle: String,
    val subcategoryId: String,
    val subcategoryTitle: String,
    val grade: Int,
    val sequence: Int,
    @param:DrawableRes val fallbackIconResId: Int,
    val iconAssetPath: String?,
    val prerequisiteLessonIds: List<String>,
    val unmetPrerequisiteTitles: List<String>,
    val state: GalaxyNodeState,
    val progressPercent: Int,
    val checkpoint: Boolean = false,
    val portalDestination: GalaxyPortalDestination? = null,
    val phaseOrbs: List<GalaxyPhaseOrbUi> = emptyList()
)

data class GalaxyCheckpointNodeUi(
    val id: String,
    val targetLessonId: String,
    val grade: Int,
    val prerequisiteLessonIds: List<String>,
    val requiredLessonIds: List<String>,
    val unmetPrerequisiteTitles: List<String>,
    val state: GalaxyNodeState,
    val progressPercent: Int
)

data class GalaxyTrackUi(
    val id: String,
    val title: String,
    @param:DrawableRes val iconResId: Int,
    val lessons: List<GalaxyLessonNodeUi>,
    val iconAssetPath: String? = null
)

data class GalaxyCategoryUi(
    val id: String,
    val domain: LearningDomain,
    val title: String,
    val progressPercent: Int,
    val tracks: List<GalaxyTrackUi>,
    val checkpoints: List<GalaxyCheckpointNodeUi> = emptyList(),
    @param:DrawableRes val iconResId: Int = R.drawable.icon_08_space_planet,
    val iconAssetPath: String? = null
)

data class LearningGalaxyUiState(
    val categories: List<GalaxyCategoryUi> = emptyList(),
    val isLoading: Boolean = true
)

internal fun galaxyPrerequisitesSatisfied(
    prerequisiteLessonIds: Collection<String>,
    passedLessonIds: Set<String>
): Boolean = LessonStatusServiceV2.prerequisitesSatisfied(prerequisiteLessonIds) { prerequisiteId ->
    LessonGateStateV2.PASSED.takeIf { prerequisiteId in passedLessonIds }
}

internal fun galaxyCategoryIconResId(domain: LearningDomain): Int = when (domain) {
    LearningDomain.READING -> R.drawable.icon_07_language_person
    LearningDomain.WRITING -> R.drawable.icon_01_abc_pencil
    LearningDomain.MATH -> R.drawable.icon_02_calculator
    LearningDomain.GEOMETRY -> R.drawable.icon_04_cube
    LearningDomain.WORLD_KNOWLEDGE -> R.drawable.icon_08_space_planet
    LearningDomain.PUZZLE -> R.drawable.icon_06_brain_network
}

internal fun galaxyCheckpointRequirementIds(
    lessonId: String,
    prerequisiteIdsByLesson: Map<String, List<String>>
): List<String> {
    val collected = linkedSetOf<String>()
    val visiting = mutableSetOf<String>()
    fun collect(currentId: String) {
        if (!visiting.add(currentId)) return
        prerequisiteIdsByLesson[currentId].orEmpty().forEach { prerequisiteId ->
            if (collected.add(prerequisiteId)) collect(prerequisiteId)
        }
        visiting.remove(currentId)
    }
    collect(lessonId)
    return collected.toList()
}

internal fun galaxyNodeState(
    progressState: LessonProgressState?,
    masteryLevel: be.saikono.poincare.model.lesson.LessonMasteryLevel?,
    gateState: LessonGateStateV2?,
    prerequisitesSatisfied: Boolean
): GalaxyNodeState {
    val passed = progressState == LessonProgressState.COMPLETED ||
        gateState?.let(LessonStatusServiceV2::unlocksDependants) == true
    return when {
        passed && (
            masteryLevel == be.saikono.poincare.model.lesson.LessonMasteryLevel.MASTERED ||
                gateState == LessonGateStateV2.MASTERED
            ) -> GalaxyNodeState.MASTERED
        passed -> GalaxyNodeState.COMPLETED
        !prerequisitesSatisfied -> GalaxyNodeState.LOCKED
        progressState == LessonProgressState.IN_PROGRESS -> GalaxyNodeState.IN_PROGRESS
        else -> GalaxyNodeState.AVAILABLE
    }
}

private fun LessonStageTypeV2.galaxyOrbKind(): GalaxyOrbKind = when (this) {
    LessonStageTypeV2.THEORY,
    LessonStageTypeV2.ACQUIRE_UNDERSTAND -> GalaxyOrbKind.THEORY
    LessonStageTypeV2.EXERCISES,
    LessonStageTypeV2.PRACTISE,
    LessonStageTypeV2.APPLY_DEMONSTRATE -> GalaxyOrbKind.PRACTICE
    LessonStageTypeV2.MASTERY_TEST -> GalaxyOrbKind.TEST
}

internal fun buildGalaxyPhaseOrbs(
    lesson: LessonDefinitionV2,
    lessonState: GalaxyNodeState,
    lessonProgress: LessonProgress?,
    stageProgress: List<LessonStageProgressSnapshotV2>,
    prerequisitesSatisfied: Boolean
): List<GalaxyPhaseOrbUi> {
    val orderedStages = lesson.stages.sortedWith(compareBy({ it.sequence }, { it.id }))
    val orderedStageIds = orderedStages.map { it.id }
    val completedStageIds = stageProgress.filter { it.completed }.mapTo(linkedSetOf()) { it.stageId }
    val reachedStageIds = stageProgress.mapTo(linkedSetOf()) { it.stageId }
    val furthestReachedIndex = orderedStageIds.indexOfLast { it in reachedStageIds }
    val currentKind = lessonProgress?.currentStageTypeV2?.galaxyOrbKind()
    val lessonCompleted = lessonState == GalaxyNodeState.COMPLETED || lessonState == GalaxyNodeState.MASTERED

    fun stageAvailable(stageId: String): Boolean {
        if (!prerequisitesSatisfied) return false
        val stageIndex = orderedStageIds.indexOf(stageId)
        if (stageIndex <= 0) return stageIndex == 0
        if (orderedStageIds.take(stageIndex).all { it in completedStageIds }) return true
        return furthestReachedIndex >= stageIndex - 1
    }

    val currentStageId = orderedStages.firstOrNull { stage ->
        stage.type.galaxyOrbKind() == currentKind && stage.id !in completedStageIds
    }?.id
    val firstTestStageId = orderedStages.firstOrNull { it.type.galaxyOrbKind() == GalaxyOrbKind.TEST }?.id

    val stageOrbs = orderedStages.mapIndexed { stageIndex, stage ->
        val kind = stage.type.galaxyOrbKind()
        val completed = lessonCompleted || stage.id in completedStageIds
        val inProgress = !completed && (stage.id in reachedStageIds || stage.id == currentStageId)
        val gateReadyForTest = kind == GalaxyOrbKind.TEST &&
            stage.id == firstTestStageId &&
            lessonProgress?.effectiveGateStateV2 == LessonGateStateV2.READY_FOR_TEST
        val available = completed || inProgress || gateReadyForTest || stageAvailable(stage.id)
        val state = when {
            !prerequisitesSatisfied -> GalaxyNodeState.LOCKED
            completed && kind == GalaxyOrbKind.TEST && lessonState == GalaxyNodeState.MASTERED ->
                GalaxyNodeState.MASTERED
            completed -> GalaxyNodeState.COMPLETED
            inProgress -> GalaxyNodeState.IN_PROGRESS
            available && stageIndex == 0 && lessonState == GalaxyNodeState.RECOMMENDED ->
                GalaxyNodeState.RECOMMENDED
            available -> GalaxyNodeState.AVAILABLE
            else -> GalaxyNodeState.LOCKED
        }
        val progressPercent = when {
            completed -> 100
            inProgress && kind == GalaxyOrbKind.TEST ->
                lessonProgress?.bestScorePercent?.coerceIn(1, 99) ?: 1
            inProgress -> 1
            else -> 0
        }

        GalaxyPhaseOrbUi(
            kind = kind,
            state = state,
            progressPercent = progressPercent,
            stageId = stage.id
        )
    }

    // The curriculum may contain several stages of the same kind. They remain
    // individually available to the lesson player, but the galaxy deliberately
    // exposes one overview orb per kind to keep large, dynamic curricula legible.
    return stageOrbs
        .groupBy { it.kind }
        .values
        .map { groupedStages ->
            val completedCount = groupedStages.count {
                it.state == GalaxyNodeState.COMPLETED || it.state == GalaxyNodeState.MASTERED
            }
            val allCompleted = completedCount == groupedStages.size
            val groupedState = when {
                groupedStages.all { it.state == GalaxyNodeState.LOCKED } -> GalaxyNodeState.LOCKED
                allCompleted && groupedStages.any { it.state == GalaxyNodeState.MASTERED } ->
                    GalaxyNodeState.MASTERED
                allCompleted -> GalaxyNodeState.COMPLETED
                groupedStages.any { it.state == GalaxyNodeState.IN_PROGRESS } || completedCount > 0 ->
                    GalaxyNodeState.IN_PROGRESS
                groupedStages.any { it.state == GalaxyNodeState.RECOMMENDED } -> GalaxyNodeState.RECOMMENDED
                groupedStages.any { it.state == GalaxyNodeState.AVAILABLE } -> GalaxyNodeState.AVAILABLE
                else -> GalaxyNodeState.LOCKED
            }
            val selectedStage = groupedStages.firstOrNull { it.state == GalaxyNodeState.IN_PROGRESS }
                ?: groupedStages.firstOrNull { it.state == GalaxyNodeState.RECOMMENDED }
                ?: groupedStages.firstOrNull { it.state == GalaxyNodeState.AVAILABLE }
                ?: groupedStages.first()
            val groupedProgress = when {
                allCompleted -> 100
                completedCount > 0 -> (completedCount * 100 / groupedStages.size).coerceIn(1, 99)
                groupedState == GalaxyNodeState.IN_PROGRESS ->
                    groupedStages.maxOf { it.progressPercent }.coerceIn(1, 99)
                else -> 0
            }

            GalaxyPhaseOrbUi(
                kind = groupedStages.first().kind,
                state = groupedState,
                progressPercent = groupedProgress,
                stageId = selectedStage.stageId
            )
        }
}

internal fun GalaxyCategoryUi.nextLessonCandidates(): List<GalaxyLessonNodeUi> = tracks
    .asSequence()
    .flatMap { it.lessons.asSequence() }
    .filter { lesson ->
        lesson.portalDestination == null && lesson.state in setOf(
            GalaxyNodeState.RECOMMENDED,
            GalaxyNodeState.IN_PROGRESS,
            GalaxyNodeState.AVAILABLE
        )
    }
    .sortedWith(
        compareBy<GalaxyLessonNodeUi>(
            { if (it.state == GalaxyNodeState.RECOMMENDED) 0 else if (it.state == GalaxyNodeState.IN_PROGRESS) 1 else 2 },
            { it.sequence },
            { it.id }
        )
    )
    .toList()

internal fun buildLearningGalaxyUiState(
    profile: ChildProfile,
    manifest: CurriculumManifest,
    catalog: LessonCatalogV2,
    progress: List<LessonProgress>,
    categoryProgressPercent: Map<LearningDomain, Int>,
    selectedGrade: Int? = null,
    stageProgress: List<LessonStageProgressSnapshotV2> = emptyList()
): LearningGalaxyUiState {
    val locale = profile.appLanguage.ifBlank { "nl-BE" }
    val enabledDomains = LearningDomain.entries.filter(profile::isMainCategoryEnabled)
    val categoryDomainById = manifest.categories.associate { it.id to it.domain }
    fun lessonDomain(categoryId: String, legacyDomainName: String): LearningDomain? =
        categoryDomainById[categoryId]
            ?: runCatching { LearningDomain.valueOf(legacyDomainName) }.getOrNull()
    val activeLessons = catalog.lessons.filter { lesson ->
        val domain = lessonDomain(lesson.curriculumCategoryId, lesson.legacyRouting.domain.name)
            domain != null &&
            domain in enabledDomains &&
            profile.isSubcategoryEnabled(lesson.curriculumSubcategoryId) &&
            lesson.grade in profile.selectedGradesFor(domain) &&
            (selectedGrade == null || lesson.grade == selectedGrade)
    }
    val progressByLessonId = progress.associateBy { it.lessonId }
    val stageProgressByLessonId = stageProgress.groupBy { it.lessonId }
    val lessonsById = catalog.lessons.associateBy { it.id }
    val prerequisiteIdsByLesson = catalog.lessons.associate { it.id to it.prerequisiteLessonIds }
    val passedLessonIds = progressByLessonId
        .asSequence()
        .filter { (_, lessonProgress) ->
            LessonStatusServiceV2.unlocksDependants(lessonProgress.effectiveGateStateV2)
        }
        .mapTo(linkedSetOf()) { it.key }

    val categories = manifest.categories
        .filter { category ->
            val storiesRemainAvailable = category.domain == LearningDomain.READING &&
                profile.isSubcategoryEnabled("language.stories") &&
                profile.selectedGradesFor(LearningDomain.READING).isNotEmpty()
            category.domain in enabledDomains && (
                selectedGrade == null ||
                    selectedGrade in profile.selectedGradesFor(category.domain) ||
                    storiesRemainAvailable
                )
        }
        .map { category ->
            val subcategories = getSubCategoriesForDomain(
                domain = category.domain.name,
                manifest = manifest,
                locale = locale,
                selectedGrades = selectedGrade?.let(::listOf) ?: profile.selectedGradesFor(category.domain)
            )
            val tracks = subcategories
                .filter { profile.isSubcategoryEnabled(it.id) }
                .mapNotNull { subcategory ->
                if (category.domain == LearningDomain.READING && subcategory.id == "language.stories") {
                    return@mapNotNull GalaxyTrackUi(
                        id = subcategory.id,
                        title = subcategory.nameNl,
                        iconResId = subcategory.iconResId,
                        lessons = emptyList(),
                        iconAssetPath = subcategory.iconAssetPath
                    )
                }
                val lessons = activeLessons
                    .asSequence()
                    .filter { lesson ->
                        lesson.curriculumCategoryId == category.id ||
                            (lesson.curriculumCategoryId.isBlank() && lesson.legacyRouting.domain.name == category.domain.name)
                    }
                    .filter { it.curriculumSubcategoryId == subcategory.id }
                    .sortedWith(compareBy({ it.grade }, { it.sequence }, { it.unitId }, { it.id }))
                    .map { lesson ->
                        val lessonProgress = progressByLessonId[lesson.id]
                        val effectivePrerequisiteIds = if (lesson.checkpoint) {
                            galaxyCheckpointRequirementIds(lesson.id, prerequisiteIdsByLesson)
                        } else {
                            lesson.prerequisiteLessonIds
                        }
                        val prerequisitesSatisfied = galaxyPrerequisitesSatisfied(
                            prerequisiteLessonIds = effectivePrerequisiteIds,
                            passedLessonIds = passedLessonIds
                        )
                        val lessonState = galaxyNodeState(
                            progressState = lessonProgress?.state,
                            masteryLevel = lessonProgress?.masteryLevel,
                            gateState = lessonProgress?.effectiveGateStateV2,
                            prerequisitesSatisfied = prerequisitesSatisfied
                        )
                        val lessonProgressPercent = when (lessonProgress?.state) {
                            LessonProgressState.COMPLETED -> 100
                            LessonProgressState.IN_PROGRESS -> lessonProgress.masteryPercent.coerceIn(0, 99)
                            else -> 0
                        }
                        GalaxyLessonNodeUi(
                            id = lesson.id,
                            title = lesson.title.visibleFor(locale),
                            categoryTitle = category.title.get(locale),
                            subcategoryId = subcategory.id,
                            subcategoryTitle = subcategory.nameNl,
                            grade = lesson.grade,
                            sequence = lesson.sequence,
                            fallbackIconResId = subcategory.iconResId,
                            iconAssetPath = lesson.visuals.firstNotNullOfOrNull { visual ->
                                visual.assetPathSvg ?: visual.assetPathPng
                            } ?: subcategory.iconAssetPath,
                            prerequisiteLessonIds = lesson.prerequisiteLessonIds,
                            unmetPrerequisiteTitles = effectivePrerequisiteIds
                                .filterNot { it in passedLessonIds }
                                .map { prerequisiteId ->
                                    lessonsById[prerequisiteId]?.title?.visibleFor(locale) ?: prerequisiteId
                                },
                            state = lessonState,
                            progressPercent = lessonProgressPercent,
                            checkpoint = lesson.checkpoint,
                            phaseOrbs = buildGalaxyPhaseOrbs(
                                lesson = lesson,
                                lessonState = lessonState,
                                lessonProgress = lessonProgress,
                                stageProgress = stageProgressByLessonId[lesson.id].orEmpty(),
                                prerequisitesSatisfied = prerequisitesSatisfied
                            )
                        )
                    }
                    .toList()
                lessons.takeIf { it.isNotEmpty() }?.let {
                    GalaxyTrackUi(
                        id = subcategory.id,
                        title = subcategory.nameNl,
                        iconResId = subcategory.iconResId,
                        lessons = it,
                        iconAssetPath = subcategory.iconAssetPath
                    )
                }
            }
            val recommendedLessonId = tracks
                .asSequence()
                .flatMap { it.lessons.asSequence() }
                .filter { it.portalDestination == null && it.state == GalaxyNodeState.AVAILABLE }
                .minWithOrNull(compareBy<GalaxyLessonNodeUi>({ it.grade }, { it.sequence }, { it.id }))
                ?.id
            val categoryLessons = tracks.flatMap { it.lessons }
            val visibleLessonIds = categoryLessons.mapTo(linkedSetOf()) { it.id }
            val checkpoints = categoryLessons.filter { it.checkpoint }.map { targetLesson ->
                val requiredIds = galaxyCheckpointRequirementIds(targetLesson.id, prerequisiteIdsByLesson)
                val completedCount = requiredIds.count { it in passedLessonIds }
                val completed = requiredIds.isNotEmpty() && completedCount == requiredIds.size
                GalaxyCheckpointNodeUi(
                    id = "checkpoint:${targetLesson.id}",
                    targetLessonId = targetLesson.id,
                    grade = targetLesson.grade,
                    prerequisiteLessonIds = targetLesson.prerequisiteLessonIds.filter { it in visibleLessonIds },
                    requiredLessonIds = requiredIds,
                    unmetPrerequisiteTitles = requiredIds
                        .filterNot { it in passedLessonIds }
                        .map { prerequisiteId ->
                            lessonsById[prerequisiteId]?.title?.visibleFor(locale) ?: prerequisiteId
                        },
                    state = when {
                        completed -> GalaxyNodeState.COMPLETED
                        requiredIds.isEmpty() -> GalaxyNodeState.AVAILABLE
                        else -> GalaxyNodeState.LOCKED
                    },
                    progressPercent = if (requiredIds.isEmpty()) 0 else {
                        (completedCount * 100 / requiredIds.size).coerceIn(0, 100)
                    }
                )
            }
            GalaxyCategoryUi(
                id = category.id,
                domain = category.domain,
                title = category.title.get(locale),
                progressPercent = categoryProgressPercent[category.domain]?.coerceIn(0, 100) ?: 0,
                tracks = tracks.map { track ->
                    track.copy(lessons = track.lessons.map { lesson ->
                        if (lesson.id == recommendedLessonId) {
                            lesson.copy(
                                state = GalaxyNodeState.RECOMMENDED,
                                phaseOrbs = lesson.phaseOrbs.map { orb ->
                                    if (orb.kind == GalaxyOrbKind.THEORY && orb.state == GalaxyNodeState.AVAILABLE) {
                                        orb.copy(state = GalaxyNodeState.RECOMMENDED)
                                    } else {
                                        orb
                                    }
                                }
                            )
                        } else lesson
                    })
                },
                checkpoints = checkpoints,
                iconResId = galaxyCategoryIconResId(category.domain),
                iconAssetPath = category.iconAssetPath
            )
        }

    return LearningGalaxyUiState(categories = categories, isLoading = false)
}

internal fun LearningGalaxyUiState.withStoryThemes(
    books: List<StoryBook>,
    locale: String
): LearningGalaxyUiState {
    return copy(
        categories = categories.map { category ->
            if (category.domain != LearningDomain.READING) return@map category
            category.copy(
                tracks = category.tracks.flatMap { track ->
                    if (track.id != GALAXY_STORY_TRACK_ID) return@flatMap listOf(track)
                    val orderedBooks = books
                        .distinctBy { it.id }
                        .sortedWith(
                            compareBy<StoryBook> { it.seriesOrder ?: Int.MAX_VALUE }
                                .thenBy { it.bookNumber ?: Int.MAX_VALUE }
                                .thenBy(StoryBook::grade)
                                .thenBy(StoryBook::order)
                                .thenBy(StoryBook::id)
                        )
                    if (orderedBooks.isEmpty()) return@flatMap listOf(track)
                    val storyThemes = orderedBooks
                        .groupBy { book -> book.seriesId?.takeIf(String::isNotBlank) ?: "standalone" }
                        .entries
                        .sortedWith(
                            compareBy<Map.Entry<String, List<StoryBook>>> {
                                it.value.minOfOrNull { book -> book.seriesOrder ?: Int.MAX_VALUE }
                                    ?: Int.MAX_VALUE
                            }.thenBy { it.key }
                        )
                        .mapIndexed { index, (themeId, themeBooks) ->
                            val themeTitle = themeBooks.firstNotNullOfOrNull { book ->
                                book.seriesTitle?.text(locale)?.takeIf(String::isNotBlank)
                            } ?: track.title
                            GalaxyLessonNodeUi(
                                id = galaxyStoryThemePortalId(themeId),
                                title = themeTitle,
                                categoryTitle = category.title,
                                subcategoryId = track.id,
                                subcategoryTitle = track.title,
                                grade = themeBooks.minOf(StoryBook::grade),
                                sequence = index + 1,
                                fallbackIconResId = track.iconResId,
                                iconAssetPath = null,
                                prerequisiteLessonIds = emptyList(),
                                unmetPrerequisiteTitles = emptyList(),
                                state = GalaxyNodeState.AVAILABLE,
                                progressPercent = 0,
                                portalDestination = GalaxyPortalDestination.STORY_LIBRARY,
                                phaseOrbs = listOf(
                                    GalaxyPhaseOrbUi(
                                        kind = GalaxyOrbKind.PORTAL,
                                        state = GalaxyNodeState.AVAILABLE,
                                        progressPercent = 0
                                    )
                                )
                            )
                        }
                    listOf(track.copy(lessons = storyThemes))
                }
            )
        }
    )
}
