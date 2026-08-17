package be.saikono.poincare.ui.lesson

import be.saikono.poincare.domain.curriculum.CurriculumRequirement
import be.saikono.poincare.domain.curriculum.CurriculumUnit
import be.saikono.poincare.domain.curriculum.LearningGoalDefinition
import be.saikono.poincare.domain.curriculum.LessonDefinition
import be.saikono.poincare.domain.curriculum.LessonMasteryCriteria
import be.saikono.poincare.domain.curriculum.LessonSectionType
import be.saikono.poincare.domain.curriculum.LessonText
import be.saikono.poincare.domain.curriculum.ParentSupportLevel
import be.saikono.poincare.domain.curriculum.v2.LegacyXmlExerciseAdapterV2
import be.saikono.poincare.domain.curriculum.v2.LessonContentBlockTypeV2
import be.saikono.poincare.domain.curriculum.v2.LessonDefinitionV2
import be.saikono.poincare.domain.curriculum.v2.LessonDomainV2
import be.saikono.poincare.domain.curriculum.v2.ExerciseContentSignatureV2
import be.saikono.poincare.domain.curriculum.v2.LessonExerciseResolutionV2
import be.saikono.poincare.domain.curriculum.v2.LessonExerciseResolverV2
import be.saikono.poincare.domain.curriculum.v2.LessonResolveContextV2
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import be.saikono.poincare.domain.curriculum.v2.LessonTextV2
import be.saikono.poincare.domain.curriculum.v2.DEFAULT_LESSON_PHASE_SECTION_ID
import be.saikono.poincare.domain.curriculum.v2.isTheory
import be.saikono.poincare.domain.curriculum.v2.wrongAnswersAllowed
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.repository.ContentRepository

class LessonRuntimeV2(
    contentRepository: ContentRepository,
    private val resolver: LessonExerciseResolverV2 = LessonExerciseResolverV2(
        LegacyXmlExerciseAdapterV2(contentRepository)
    )
) {
    suspend fun resolve(
        lesson: LessonDefinitionV2,
        profileId: String,
        attemptId: String,
        catalogDefinitionVersion: Int
    ): LessonResolutionResult {
        if (profileId.isBlank() || attemptId.isBlank()) {
            return LessonResolutionResult.Rejected("Profile and attempt identity are required.")
        }
        val steps = mutableListOf<LessonStep>()
        val seeds = mutableListOf<Long>()
        val fingerprints = mutableListOf<String>()
        val seenExerciseSignatures = linkedSetOf<String>()
        val visualsById = lesson.visuals.associateBy { it.id }

        lesson.stages.sortedBy { it.sequence }.forEach { stage ->
            val phaseSectionId = stage.sectionId
                ?: stage.seriesIndex?.let { "series-$it" }
                ?: stage.type.takeUnless { it.isTheory() }
                    ?.let { DEFAULT_LESSON_PHASE_SECTION_ID }
            val contentBlocks = stage.contentBlocks
                .filterNot { it.type == LessonContentBlockTypeV2.UITLEG_OUDERS }
                .mapNotNull { block -> block.lessonText()?.let { block to it } }
            val contentPages = if (stage.type.isTheory()) {
                contentBlocks
                    .sortedWith(compareBy<Pair<be.saikono.poincare.domain.curriculum.v2.LessonContentBlockV2, LessonText>> { it.first.pageSequence })
                    .groupBy { it.first.pageId }
                    .values
                    .toList()
            } else {
                contentBlocks.map(::listOf)
            }
            contentPages.forEachIndexed { pageIndex, page ->
                val firstBlock = page.first().first
                steps += LessonStep.Explanation(
                    sectionId = if (page.size == 1) firstBlock.id else "${stage.id}.theory-${pageIndex + 1}",
                    sectionType = stage.type.toV1SectionType(),
                    title = firstBlock.title?.toV1() ?: stage.title?.toV1(),
                    content = combineLessonTexts(page.map { it.second }),
                    stageTypeV2 = stage.type,
                    stageIdV2 = stage.id,
                    phaseSectionIdV2 = phaseSectionId,
                    visualsV2 = page.flatMap { it.first.visualIds }
                        .distinct()
                        .mapNotNull(visualsById::get)
                )
            }
            stage.exerciseSets.forEach { set ->
                when (val result = resolver.resolve(
                    set = set,
                    context = LessonResolveContextV2(
                        profileId = profileId,
                        lessonId = lesson.id,
                        unitId = lesson.unitId,
                        stageId = stage.id,
                        attemptId = attemptId,
                        canonicalDomain = lesson.legacyRouting.domain.toLearningDomain(),
                        canonicalSubcategoryId = lesson.curriculumSubcategoryId,
                        excludedExerciseSignatures = seenExerciseSignatures
                    )
                )) {
                    is LessonExerciseResolutionV2.Rejected -> return LessonResolutionResult.Rejected(
                        result.message,
                        result.details
                    )
                    is LessonExerciseResolutionV2.Success -> {
                        result.value.generatorSeed?.let(seeds::add)
                        result.value.seedFingerprint?.let(fingerprints::add)
                        result.value.exercises.forEach { exercise ->
                            if (!stage.type.isTheory()) {
                                seenExerciseSignatures += ExerciseContentSignatureV2.of(exercise)
                            }
                            steps += LessonStep.Exercise(
                                sectionId = stage.id,
                                sectionType = stage.type.toV1SectionType(),
                                exercise = exercise,
                                stageTypeV2 = stage.type,
                                stageIdV2 = stage.id,
                                phaseSectionIdV2 = phaseSectionId,
                                exerciseSetIdV2 = set.id,
                                purposeV2 = set.purpose,
                                masteryTagsV2 = set.masteryTags,
                                hintPolicyV2 = set.hintPolicy,
                                feedbackPolicyV2 = set.feedbackPolicy,
                                wrongAnswersAllowedV2 = if (stage.type == LessonStageTypeV2.MASTERY_TEST) {
                                    1
                                } else {
                                    set.wrongAnswersAllowed()
                                }
                            )
                        }
                    }
                }
            }
        }
        if (steps.isEmpty()) return LessonResolutionResult.Rejected("This v2 lesson has no runnable content.")
        val virtualLesson = lesson.toV1Lesson()
        val virtualUnit = lesson.toV1Unit(virtualLesson)
        return LessonResolutionResult.Success(
            ResolvedLessonSession(
                unit = virtualUnit,
                lesson = virtualLesson,
                attemptId = attemptId,
                contentVersion = "catalog-v2-$catalogDefinitionVersion/lesson-${lesson.definitionVersion}",
                steps = steps,
                generatorSeeds = seeds,
                seedFingerprints = fingerprints,
                lessonV2 = lesson
            )
        )
    }
}

private fun combineLessonTexts(texts: List<LessonText>): LessonText {
    fun visible(locale: String): String = texts.joinToString("\n\n") { it.visibleFor(locale) }
    fun spoken(locale: String): String = texts.joinToString("\n\n") { it.spokenFor(locale) }
    return LessonText(
        visible = LocalizedText(
            nl = visible("nl-BE"),
            fr = visible("fr-BE"),
            en = visible("en-US")
        ),
        spoken = LocalizedText(
            nl = spoken("nl-BE"),
            fr = spoken("fr-BE"),
            en = spoken("en-US")
        )
    )
}

private fun be.saikono.poincare.domain.curriculum.v2.LessonContentBlockV2.lessonText(): LessonText? {
    body?.let { return it.toV1() }
    if (terms.isEmpty()) return null
    fun localized(locale: String): String = terms.joinToString("\n\n") { term ->
        "${term.term.visibleFor(locale)}: ${term.definition.visibleFor(locale)}"
    }
    return LessonText(
        visible = LocalizedText(
            nl = localized("nl-BE"),
            fr = localized("fr-BE"),
            en = localized("en-US")
        ),
        spoken = if (type == LessonContentBlockTypeV2.VOCABULARY) LocalizedText(
            nl = localized("nl-BE"),
            fr = localized("fr-BE"),
            en = localized("en-US")
        ) else null
    )
}

internal fun LessonTextV2.toV1(): LessonText = LessonText(
    visible = LocalizedText(
        nl = visibleFor("nl-BE"),
        fr = visibleFor("fr-BE"),
        en = visibleFor("en-US")
    ),
    spoken = spoken?.let {
        LocalizedText(
            nl = spokenFor("nl-BE"),
            fr = spokenFor("fr-BE"),
            en = spokenFor("en-US")
        )
    }
)

internal fun LessonDomainV2.toLearningDomain(): LearningDomain = when (this) {
    LessonDomainV2.MATH -> LearningDomain.MATH
    LessonDomainV2.GEOMETRY -> LearningDomain.GEOMETRY
    LessonDomainV2.READING -> LearningDomain.READING
    LessonDomainV2.WRITING -> LearningDomain.WRITING
    LessonDomainV2.WORLD_KNOWLEDGE -> LearningDomain.WORLD_KNOWLEDGE
    LessonDomainV2.PUZZLE -> LearningDomain.PUZZLE
}

private fun LessonStageTypeV2.toV1SectionType(): LessonSectionType = when (this) {
    LessonStageTypeV2.THEORY -> LessonSectionType.EXPLANATION
    LessonStageTypeV2.EXERCISES -> LessonSectionType.INDEPENDENT_PRACTICE
    LessonStageTypeV2.ACQUIRE_UNDERSTAND -> LessonSectionType.EXPLANATION
    LessonStageTypeV2.PRACTISE -> LessonSectionType.GUIDED_PRACTICE
    LessonStageTypeV2.APPLY_DEMONSTRATE -> LessonSectionType.INDEPENDENT_PRACTICE
    LessonStageTypeV2.MASTERY_TEST -> LessonSectionType.CHECKPOINT
}

private fun LessonDefinitionV2.toV1Lesson(): LessonDefinition = LessonDefinition(
    id = id,
    grade = grade,
    sequence = sequence,
    title = title.toV1(),
    summary = summary.toV1(),
    requirement = CurriculumRequirement.MANDATORY,
    learningGoalIds = learningGoalIds,
    parentSupport = ParentSupportLevel.NONE,
    estimatedMinutes = stages.mapNotNull { it.estimatedMinutes }.sum(),
    mastery = LessonMasteryCriteria(
        minimumCorrectRatio = gatePolicy.minimumCorrectRatio,
        minimumAttempts = 1,
        maximumHints = gatePolicy.maximumHints,
        requiredSectionIds = stages
            .filter { it.type == LessonStageTypeV2.MASTERY_TEST }
            .map { it.id }
    ),
    sections = emptyList(),
    tags = masteryTags,
    definitionVersion = definitionVersion
)

private fun LessonDefinitionV2.toV1Unit(lesson: LessonDefinition): CurriculumUnit = CurriculumUnit(
    id = unitId,
    domain = legacyRouting.domain.toLearningDomain(),
    subcategoryId = curriculumSubcategoryId,
    grades = listOf(grade),
    title = title.toV1(),
    description = summary.toV1(),
    learningGoals = learningGoalIds.map {
        LearningGoalDefinition(id = it, title = title.toV1())
    },
    lessons = listOf(lesson),
    definitionVersion = definitionVersion
)
