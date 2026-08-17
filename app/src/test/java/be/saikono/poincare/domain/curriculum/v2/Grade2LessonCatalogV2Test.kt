package be.saikono.poincare.domain.curriculum.v2

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade2LessonCatalogV2Test {
    private val catalog by lazy {
        LessonCatalogV2Loader.fromFile(assetFile(LessonCatalogV2Loader.ASSET_PATH))
    }

    private val broadDraftIds = setOf(
        "lesson.g2.language.technical_reading.fluency",
        "lesson.g2.spelling.rules.punctuation_syllables_plural",
        "lesson.g2.language.writing.short_texts",
        "lesson.g2.language.listening.extended_instructions",
        "lesson.g2.math.operations.expansion",
        "lesson.g2.geometry.measurement_context",
        "lesson.g2.math.statistics.introduction",
        "lesson.g2.world.integrated_foundations",
        "lesson.g2.world.digital.media_safety",
        "lesson.g2.world.citizenship.emotions_conflicts"
    )

    @Test
    fun `all canonical grade two units map to atomic draft v2 lessons`() {
        val gradeTwo = catalog.lessons.filter { it.grade == 2 }
        val dictation = gradeTwo.filter { it.curriculumSubcategoryId == "language.dictation" }
        val englishVocabulary = gradeTwo.filter { it.legacyRouting.subcategoryId == "vocabulary_en" }
        val communication = gradeTwo.filter {
            it.id.startsWith("lesson.g2.nl.speaking.") || it.id.startsWith("lesson.g2.nl.writing.")
        }
        val canonical = gradeTwo.filterNot {
            it.curriculumSubcategoryId == "language.dictation" ||
                it.legacyRouting.subcategoryId == "vocabulary_en" ||
                it in communication
        }
        val atomic = canonical.filter {
            it.publicationStatus == LessonPublicationStatusV2.DRAFT_REVIEW_REQUIRED &&
                it.id != "lesson.g2.world.nature.seasons.order-and-signs"
        }

        assertEquals(123, gradeTwo.size)
        assertEquals(4, dictation.size)
        assertEquals(1, englishVocabulary.size)
        assertEquals(10, communication.size)
        assertEquals(108, canonical.size)
        assertEquals(107, atomic.size)
        assertEquals(63, atomic.map { it.unitId }.distinct().size)
        assertTrue(gradeTwo.none { it.id in broadDraftIds })
        val retainedPilot = gradeTwo.single { it.id == "lesson.g2.world.nature.seasons.order-and-signs" }
        assertEquals(
            listOf(LessonStageTypeV2.THEORY) +
                List(8) { LessonStageTypeV2.EXERCISES } +
                LessonStageTypeV2.MASTERY_TEST,
            retainedPilot.stages.map { it.type }
        )
        assertEquals(0.8, retainedPilot.gatePolicy.minimumCorrectRatio, 0.0)
        assertEquals(189, atomic.flatMap { it.legacyRouting.legacyLessonIds }.count {
            it.startsWith("lesson.g2.") && (
                it.endsWith(".acquire") || it.endsWith(".practice") || it.endsWith(".transfer")
            )
        })

        atomic.forEach { lesson ->
            assertEquals("nl-BE", lesson.defaultLocale)
            assertEquals(LessonTranslationStatusV2.DEFAULT, lesson.translationStatus.getValue("nl-BE").status)
            assertEquals(REQUIRED_LESSON_LOCALES, lesson.translationStatus.keys)
            assertEquals(LessonTranslationStatusV2.MISSING, lesson.translationStatus.getValue("fr-BE").status)
            assertEquals(LessonTranslationStatusV2.MISSING, lesson.translationStatus.getValue("en-US").status)
            assertEquals(
                listOf(LessonStageTypeV2.THEORY) +
                    List(8) { LessonStageTypeV2.EXERCISES } +
                    LessonStageTypeV2.MASTERY_TEST,
                lesson.stages.map { it.type }
            )
            assertEquals((1..10).toList(), lesson.stages.map { it.sequence })
            assertEquals(11, lesson.stages.sumOf { it.exerciseSets.size })
            assertTrue(lesson.stages.sumOf { stage -> stage.exerciseSets.sumOf { it.itemCount } } >= 64)
            assertEquals(0.8, lesson.gatePolicy.minimumCorrectRatio, 0.0)
            assertTrue(lesson.visuals.all { it.assetPathSvg.orEmpty().startsWith("poincare/visuals/grade2/") })
            assertTrue(lesson.visuals.all { it.assetPathPng.orEmpty().startsWith("poincare/visuals/grade2/") })

            val practiceSets = lesson.exerciseSeries().flatMap { it.exerciseSets }
            assertEquals(
                listOf(
                    LessonExercisePurposeV2.GUIDED,
                    LessonExercisePurposeV2.FADING_SUPPORT,
                    LessonExercisePurposeV2.INDEPENDENT_PRACTICE
                ),
                practiceSets.take(3).map { it.purpose }
            )
            assertTrue(practiceSets.take(3).all { it.itemCount > 0 })
            assertEquals(setOf("2"), practiceSets.take(3).map { it.parameters[WRONG_ANSWERS_ALLOWED_METADATA_KEY] }.toSet())
            val apply = practiceSets.drop(3).take(1)
            assertTrue(apply.all { it.itemCount > 0 })
            assertEquals(setOf("1"), apply.map { it.parameters[WRONG_ANSWERS_ALLOWED_METADATA_KEY] }.toSet())
            val independent = practiceSets.drop(4).take(3)
            assertTrue(independent.all { it.itemCount > 0 })
            assertEquals(setOf("1"), independent.map { it.parameters[WRONG_ANSWERS_ALLOWED_METADATA_KEY] }.toSet())
            val plus = practiceSets.takeLast(1)
            assertTrue(plus.all { it.itemCount > 0 })
            assertEquals(setOf("2"), plus.map { it.parameters[WRONG_ANSWERS_ALLOWED_METADATA_KEY] }.toSet())
            assertEquals(3, requireNotNull(lesson.theoryStage()).exerciseSets.sumOf { it.itemCount })
            assertTrue(
                lesson.stages.single { it.type == LessonStageTypeV2.MASTERY_TEST }
                    .exerciseSets.sumOf { it.itemCount } >= lesson.gatePolicy.minimumIndependentItems
            )
            lesson.stages.flatMap { it.exerciseSets }.forEach { set ->
                assertEquals("v2", set.generatorVersion)
                assertEquals(LessonValidationStatusV2.NEEDS_REVIEW, set.validationStatus)
                assertEquals(set.itemCount, set.questionBlueprints.size)
                assertTrue(set.questionBlueprints.all { question -> question.answerOptionIds.size == 4 })
                assertTrue(set.editorialItems.isEmpty() || set.editorialItems.size == set.itemCount)
                assertEquals(setOf("nl-BE"), set.title.visible.keys)
                assertTrue(set.parameters.keys.none { it.endsWith("_fr") || it.endsWith("_en") || it.endsWith("_fr-BE") || it.endsWith("_en-US") })
                if (set.sourceMode == LessonSourceModeV2.CURATED) {
                    assertEquals(null, set.generatorId)
                    assertEquals(LessonExerciseOriginV2.FIXED, set.origin)
                    assertEquals(set.itemCount, set.curatedExerciseIds.size)
                } else {
                    assertEquals("generator.g2.atomic-curriculum", set.generatorId)
                    assertEquals(LessonExerciseOriginV2.GENERATED, set.origin)
                    assertTrue(set.parameters.getValue("question_pool_nl").isNotBlank())
                }
            }
        }
        communication.forEach { lesson ->
            assertEquals(
                listOf(LessonStageTypeV2.THEORY) +
                    List(8) { LessonStageTypeV2.EXERCISES } +
                    LessonStageTypeV2.MASTERY_TEST,
                lesson.stages.map { it.type }
            )
            assertEquals(8, lesson.exerciseSeries().size)
            assertEquals(32, lesson.exerciseSeries().flatMap { it.exerciseSets }.sumOf { it.itemCount })
            assertEquals(3, requireNotNull(lesson.theoryStage()).exerciseSets.sumOf { it.itemCount })
            assertTrue(
                requireNotNull(lesson.finalTestStage()).exerciseSets.sumOf { it.itemCount } >=
                    lesson.gatePolicy.minimumIndependentItems
            )
            assertTrue(lesson.stages.flatMap { it.exerciseSets }
                .filter { it.sourceMode == LessonSourceModeV2.GENERATED }
                .all { it.parameters.getValue("question_pool_nl").split('\u001e').size >= it.itemCount })
        }
    }

    @Test
    fun `grade two theory is lesson bound and follows the teaching pattern`() {
        val requiredTypes = setOf(
            LessonContentBlockTypeV2.LEARNING_INTENT,
            LessonContentBlockTypeV2.CHILD_FRIENDLY_DEFINITION,
            LessonContentBlockTypeV2.HOW_IT_WORKS,
            LessonContentBlockTypeV2.CORRECT_RESULT_RULE,
            LessonContentBlockTypeV2.WORKED_EXAMPLE,
            LessonContentBlockTypeV2.NON_EXAMPLE,
            LessonContentBlockTypeV2.UITLEG_OUDERS
        )
        catalog.lessons.filter {
            it.grade == 2 &&
                it.curriculumSubcategoryId != "language.dictation" &&
                it.legacyRouting.subcategoryId != "vocabulary_en" &&
                !it.id.startsWith("lesson.g2.nl.speaking.") &&
                !it.id.startsWith("lesson.g2.nl.writing.") &&
                it.publicationStatus == LessonPublicationStatusV2.DRAFT_REVIEW_REQUIRED &&
                it.id != "lesson.g2.world.nature.seasons.order-and-signs"
        }.forEach { lesson ->
            val theory = requireNotNull(lesson.theoryStage())
            assertTrue(theory.contentBlocks.map { it.type }.toSet().containsAll(requiredTypes))
            assertTrue(theory.contentBlocks.any { it.type == LessonContentBlockTypeV2.WORKED_EXAMPLE })
            assertEquals(1, theory.contentBlocks.count { it.type == LessonContentBlockTypeV2.UITLEG_OUDERS })
            val childPages = theory.contentBlocks
                .filterNot { it.type == LessonContentBlockTypeV2.UITLEG_OUDERS }
                .map { it.pageSequence }
                .distinct()
                .sorted()
            assertEquals((1..childPages.size).toList(), childPages)
            assertTrue(theory.contentBlocks.single { it.type == LessonContentBlockTypeV2.UITLEG_OUDERS }.pageSequence > childPages.last())
            assertFalse(theory.contentBlocks.flatMap { it.visualIds }.isEmpty())
            assertTrue(lesson.description.visibleFor("nl-BE").isNotBlank())
            assertTrue(lesson.contentFields.all { field ->
                field.translations.single { it.locale == "nl-BE" }.status == LessonTranslationStatusV2.DEFAULT &&
                    field.translations.filter { it.locale != "nl-BE" }.all {
                        it.status == LessonTranslationStatusV2.MISSING && it.fallbackLocale == "nl-BE"
                    }
            })
        }
    }

    private fun assetFile(relative: String): File = listOf(
        File("src/main/assets/$relative"),
        File("app/src/main/assets/$relative")
    ).first { it.isFile }
}
