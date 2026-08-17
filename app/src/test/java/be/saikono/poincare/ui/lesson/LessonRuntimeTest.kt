package be.saikono.poincare.ui.lesson

import be.saikono.poincare.domain.curriculum.CurriculumCatalog
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifest
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifestLoader
import be.saikono.poincare.domain.curriculum.v2.LessonCatalogV2Loader
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.SyncStatus
import be.saikono.poincare.model.lesson.LessonMasteryLevel
import be.saikono.poincare.model.lesson.LessonProgress
import be.saikono.poincare.model.lesson.LessonProgressState
import be.saikono.poincare.repository.ContentRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonRuntimeTest {
    @Test
    fun `approved generated lesson resolves reproducibly and changes with another attempt`() = runBlocking {
        val manifest = manifest()
        val unit = manifest.lessonCatalog.units.single { it.id == "unit.g2.language.writing.short_texts" }
        val lesson = unit.lessons.single { it.id.endsWith(".practice") }
        val runtime = LessonRuntime(FakeContentRepository())

        val first = runtime.resolve(manifest.lessonCatalog, unit, lesson, "profile-a", ATTEMPT_A)
            .success()
        val replay = runtime.resolve(manifest.lessonCatalog, unit, lesson, "profile-a", ATTEMPT_A)
            .success()
        val next = runtime.resolve(manifest.lessonCatalog, unit, lesson, "profile-a", ATTEMPT_B)
            .success()

        val firstIds = first.steps.filterIsInstance<LessonStep.Exercise>().map { it.exercise.id }
        val replayIds = replay.steps.filterIsInstance<LessonStep.Exercise>().map { it.exercise.id }
        val nextIds = next.steps.filterIsInstance<LessonStep.Exercise>().map { it.exercise.id }
        assertEquals(8, firstIds.size)
        assertEquals(firstIds, replayIds)
        assertNotEquals(firstIds, nextIds)
        assertEquals(first.seedFingerprints, replay.seedFingerprints)
        assertNotEquals(first.seedFingerprints, next.seedFingerprints)
    }

    @Test
    fun `practical checkpoint keeps adult verification separate from generated questions`() = runBlocking {
        val manifest = manifest()
        val unit = manifest.lessonCatalog.units.single { it.id == "unit.g2.world.digital.media_safety" }
        val lesson = unit.lessons.single { it.id.endsWith(".checkpoint") }
        val session = LessonRuntime(FakeContentRepository())
            .resolve(manifest.lessonCatalog, unit, lesson, "profile-a", ATTEMPT_A)
            .success()

        assertEquals(10, session.steps.filterIsInstance<LessonStep.Exercise>().size)
        val practical = session.steps.filterIsInstance<LessonStep.Practical>().single()
        assertTrue(practical.requiresAdultVerification)
        assertTrue(practical.instructions.visibleFor("nl-BE").isNotBlank())
    }

    @Test
    fun `curated pilot exercises resolve in manifest order`() = runBlocking {
        val manifest = manifest()
        val unit = manifest.lessonCatalog.units.single { it.id == "math.addition.g1.foundations" }
        val lesson = unit.lessons.first()
        val ids = lesson.sections.flatMap { section ->
            (section.activitySource as? be.saikono.poincare.domain.curriculum.LessonActivitySource.Curated)
                ?.exerciseIds
                .orEmpty()
        }
        val repository = FakeContentRepository(ids.associateWith(::exercise))
        val session = LessonRuntime(repository)
            .resolve(manifest.lessonCatalog, unit, lesson, "profile-a", ATTEMPT_A)
            .success()

        assertEquals(ids, session.steps.filterIsInstance<LessonStep.Exercise>().map { it.exercise.id })
    }

    @Test
    fun `prerequisite evaluator unlocks the next pilot lesson only after completion`() {
        val catalog = manifest().lessonCatalog
        val unit = catalog.units.single { it.id == "math.addition.g1.foundations" }
        val first = unit.lessons.sortedBy { it.sequence }[0]
        val second = unit.lessons.sortedBy { it.sequence }[1]

        assertTrue(LessonAvailabilityEvaluator.isAvailable(catalog, unit, first, emptyMap()))
        assertFalse(LessonAvailabilityEvaluator.isAvailable(catalog, unit, second, emptyMap()))
        assertTrue(
            LessonAvailabilityEvaluator.isAvailable(
                catalog,
                unit,
                second,
                mapOf(first.id to completedProgress(unit.id, first.id))
            )
        )
    }

    @Test
    fun `a standalone selected grade bypasses prior-grade unit prerequisites but keeps its lesson order`() {
        val catalog = manifest().lessonCatalog
        val unit = catalog.units.single { it.id == "unit.g6.language.critical_reading.arguments" }
        val explore = unit.lessons.single { it.id.endsWith(".explore") }
        val practice = unit.lessons.single { it.id.endsWith(".practice") }

        assertTrue(
            LessonAvailabilityEvaluator.isAvailable(
                catalog,
                unit,
                explore,
                emptyMap(),
                activeGrades = setOf(6)
            )
        )
        assertFalse(
            LessonAvailabilityEvaluator.isAvailable(
                catalog,
                unit,
                practice,
                emptyMap(),
                activeGrades = setOf(6)
            )
        )
    }

    @Test
    fun `a selected prior grade keeps its cross-grade prerequisite active`() {
        val catalog = manifest().lessonCatalog
        val unit = catalog.units.single { it.id == "unit.g6.language.critical_reading.arguments" }
        val explore = unit.lessons.single { it.id.endsWith(".explore") }

        assertFalse(
            LessonAvailabilityEvaluator.isAvailable(
                catalog,
                unit,
                explore,
                emptyMap(),
                activeGrades = setOf(5, 6)
            )
        )
    }

    @Test
    fun `draft codec round trips resume identity and rejects malformed state`() {
        val draft = LessonSessionDraft(
            attemptId = ATTEMPT_A,
            profileId = "profile-a",
            unitId = "unit-a",
            lessonId = "lesson-a",
            contentVersion = "catalog-24/lesson-2",
            currentStepIndex = 7,
            correctCount = 4,
            questionCount = 5,
            startedAtUtc = 1234L,
            stageIdV2 = "lesson-a.practise"
        )
        assertEquals(draft, LessonDraftCodec.decode(LessonDraftCodec.encode(draft)))
        assertEquals(null, LessonDraftCodec.decode("not-json"))
    }

    @Test
    fun `v2 theory follows canonical page ids and keeps its lesson visuals`() = runBlocking {
        val catalog = LessonCatalogV2Loader.fromFile(assetFile("src/main/assets/${LessonCatalogV2Loader.ASSET_PATH}"))
        val lesson = catalog.lessons.single {
            it.id == "lesson.g1.math.addition.within10.meaning-and-model"
        }
        val curatedIds = lesson.stages.flatMap { stage ->
            stage.exerciseSets.flatMap { it.curatedExerciseIds }
        }
        val session = LessonRuntimeV2(FakeContentRepository(curatedIds.associateWith(::exercise)))
            .resolve(lesson, "profile-a", ATTEMPT_A, catalog.definitionVersion)
            .success()
        val acquirePages = session.steps.filterIsInstance<LessonStep.Explanation>()
            .filter { it.stageTypeV2 == LessonStageTypeV2.THEORY }

        val theory = lesson.stages.single { it.type == LessonStageTypeV2.THEORY }
        val expectedPages = theory.contentBlocks
            .filterNot { it.type.name == "UITLEG_OUDERS" }
            .map { it.pageId }
            .distinct()
            .size
        assertEquals(expectedPages, acquirePages.size)
        assertTrue(acquirePages.none { "Wat leert uw kind?" in it.content.visible.nl })
        val childBlocks = theory.contentBlocks.filterNot { it.type.name == "UITLEG_OUDERS" }
        assertEquals(childBlocks[0].visualIds, acquirePages[0].visualsV2.map { it.id })
        assertEquals(childBlocks[1].visualIds, acquirePages[1].visualsV2.map { it.id })
    }

    @Test
    fun `v2 stages are presented as three logical parts with exercise series nested`() {
        val catalog = LessonCatalogV2Loader.fromFile(assetFile("src/main/assets/${LessonCatalogV2Loader.ASSET_PATH}"))
        val lesson = catalog.lessons.single {
            it.id == "lesson.g1.math.addition.within10.meaning-and-model"
        }
        val phases = lesson.stages.map { stage ->
            LessonPhasePreviewUi(
                id = stage.id,
                type = stage.type,
                sequence = stage.sequence,
                title = stage.type.name,
                seriesIndex = stage.seriesIndex?.minus(1),
                itemCount = stage.exerciseSets.sumOf { it.itemCount },
                available = true,
                completed = false,
                progressPercent = 0,
                resumable = stage.type == LessonStageTypeV2.EXERCISES,
                hasSavedDraft = false
            )
        }

        assertEquals(3, logicalLessonPartCount(lesson.stages))
        assertEquals(
            listOf(1, lesson.stages.count { it.type == LessonStageTypeV2.EXERCISES }, 1),
            lessonPartGroups(phases, "nl-BE").map { it.phases.size }
        )
        assertEquals(
            listOf("Theorie", "Oefeningen", "Toetsen"),
            lessonPartGroups(phases, "nl-BE").map { it.title }
        )
        assertEquals("Reeks 1", lessonSeriesLabel(0, "nl-BE"))
        assertEquals("Deel 1", lessonPartCountLabel(1, "nl-BE"))
        assertEquals("3 delen", lessonPartCountLabel(3, "nl-BE"))
        assertEquals("Part 1", lessonPartCountLabel(1, "en-US"))
        assertEquals("Partie 1", lessonPartCountLabel(1, "fr-BE"))
    }

    @Test
    fun `phase unlock and progress require every prior phase to be complete`() {
        val ids = listOf("understand", "practise", "apply", "test")
        assertTrue(isLessonPhaseAvailable(0, ids, emptySet()))
        assertFalse(isLessonPhaseAvailable(1, ids, emptySet()))
        assertTrue(isLessonPhaseAvailable(1, ids, setOf("understand")))
        assertFalse(isLessonPhaseAvailable(3, ids, setOf("understand", "apply")))
        assertTrue(isLessonPhaseAvailable(3, ids, setOf("understand", "practise", "apply")))
        assertEquals(0, lessonPhaseProgressPercent(false, null, 10))
        assertEquals(40, lessonPhaseProgressPercent(false, 4, 10))
        assertEquals(100, lessonPhaseProgressPercent(true, 4, 10))
    }

    @Test
    fun `unlocked exercise series stay available after theory replay and final test`() {
        val ids = listOf("theory", "exercises-1", "exercises-2", "test")
        val reachedAfterTest = ids.toSet()
        val completedWithoutTheory = setOf("exercises-1", "exercises-2", "test")

        assertTrue(isLessonPhaseAvailable(1, ids, completedWithoutTheory, reachedAfterTest))
        assertTrue(isLessonPhaseAvailable(2, ids, completedWithoutTheory, reachedAfterTest))
        assertTrue(isLessonPhaseAvailable(3, ids, completedWithoutTheory, reachedAfterTest))
    }

    @Test
    fun `available exercise series use retry icon`() {
        assertEquals(
            LessonPhaseIconKind.RETRY,
            lessonPhaseIconKind(LessonStageTypeV2.EXERCISES, available = true, completed = false)
        )
        assertEquals(
            LessonPhaseIconKind.RETRY,
            lessonPhaseIconKind(LessonStageTypeV2.EXERCISES, available = true, completed = true)
        )
        assertEquals(
            LessonPhaseIconKind.LOCKED,
            lessonPhaseIconKind(LessonStageTypeV2.EXERCISES, available = false, completed = false)
        )
    }

    @Test
    fun `every Grade 1 to Grade 6 lesson resolves through supported player modes`() = runBlocking {
        val manifest = manifest()
        val curatedIds = manifest.lessonCatalog.units
            .flatMap { it.lessons }
            .flatMap { it.sections }
            .flatMap { section ->
                when (val source = section.activitySource) {
                    is be.saikono.poincare.domain.curriculum.LessonActivitySource.Curated -> source.exerciseIds
                    is be.saikono.poincare.domain.curriculum.LessonActivitySource.Hybrid -> source.curatedExerciseIds
                    else -> emptyList()
                }
            }
            .distinct()
        val runtime = LessonRuntime(FakeContentRepository(curatedIds.associateWith(::exercise)))
        val gradeOneToSix = manifest.lessonCatalog.units.flatMap { unit ->
            unit.lessons.filter { it.grade in 1..6 }.map { unit to it }
        }

        assertEquals(
            be.saikono.poincare.domain.curriculum.manifest.Grade1HundredHourPolicy.EXPECTED_TOTAL_LESSON_COUNT,
            gradeOneToSix.size
        )
        gradeOneToSix.forEach { (unit, lesson) ->
            val result = runtime.resolve(
                manifest.lessonCatalog,
                unit,
                lesson,
                "profile-a",
                ATTEMPT_A
            )
            assertTrue("${lesson.id}: $result", result is LessonResolutionResult.Success)
            val steps = (result as LessonResolutionResult.Success).session.steps
            assertTrue("${lesson.id} has no steps", steps.isNotEmpty())
            steps.filterIsInstance<LessonStep.Exercise>().forEach { step ->
                assertTrue(
                    "${lesson.id} uses ${step.exercise.inputMode}",
                    step.exercise.inputMode in setOf(
                        InputMode.TAP_CHOICE,
                        InputMode.LISTEN_AND_SELECT,
                        InputMode.CUSTOM_NUMBER_PAD
                    )
                )
            }
        }
    }

    private fun LessonResolutionResult.success(): ResolvedLessonSession {
        assertTrue(this is LessonResolutionResult.Success)
        return (this as LessonResolutionResult.Success).session
    }

    private fun manifest(): CurriculumManifest = assetFile(
        "src/main/assets/${CurriculumManifestLoader.ASSET_PATH}"
    ).inputStream().use(CurriculumManifestLoader::fromStream)

    private fun assetFile(relativePath: String): File {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull { it.exists() } ?: error("Missing test asset: $relativePath")
    }

    private fun exercise(id: String): ExerciseContent = ExerciseContent(
        id = id,
        domain = LearningDomain.MATH,
        skill = "addition",
        section = ExerciseSection.EXERCISES,
        level = SkillLevel.FIRST_GRADE,
        inputMode = InputMode.CUSTOM_NUMBER_PAD,
        prompt = LocalizedText("1 + 1 = ?", "1 + 1 = ?", "1 + 1 = ?"),
        data = emptyMap(),
        answer = "2"
    )

    private fun completedProgress(unitId: String, lessonId: String): LessonProgress = LessonProgress(
        progressId = "progress-$lessonId",
        childProfileId = "profile-a",
        unitId = unitId,
        lessonId = lessonId,
        state = LessonProgressState.COMPLETED,
        masteryLevel = LessonMasteryLevel.PROFICIENT,
        masteryPercent = 80,
        bestScorePercent = 80,
        latestScorePercent = 80,
        attemptCount = 1,
        startedAtUtc = 100L,
        completedAtUtc = 200L,
        lastActivityAtUtc = 200L,
        updatedAtUtc = 200L,
        syncId = "sync-$lessonId",
        syncStatus = SyncStatus.PENDING,
        revision = 1L
    )

    private class FakeContentRepository(
        private val exercises: Map<String, ExerciseContent> = emptyMap()
    ) : ContentRepository {
        override suspend fun getExercises(domain: LearningDomain, level: SkillLevel): List<ExerciseContent> = exercises.values.toList()
        override suspend fun getExerciseById(id: String): ExerciseContent? = exercises[id]
        override suspend fun getExercises(
            mainCategory: String?,
            subcategoryId: String?,
            grade: Int?,
            difficultyStars: Int?,
            locale: String?
        ): List<ExerciseContent> = exercises.values.toList()
    }

    private companion object {
        const val ATTEMPT_A = "11111111-1111-4111-8111-111111111111"
        const val ATTEMPT_B = "22222222-2222-4222-8222-222222222222"
    }
}
