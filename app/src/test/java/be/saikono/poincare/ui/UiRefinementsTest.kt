package be.saikono.poincare.ui

import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifestLoader
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SubcategoryCatalog
import be.saikono.poincare.repository.MonotonicProgressReporter
import be.saikono.poincare.ui.home.getSubCategoriesForDomain
import be.saikono.poincare.ui.home.isSubcategorySequenceUnlocked
import be.saikono.poincare.ui.home.resolveSubcategoryTileAvailability
import be.saikono.poincare.ui.home.SubCategory
import be.saikono.poincare.ui.home.subcategoryTileEnabled
import be.saikono.poincare.ui.home.subcategoryMetricText
import be.saikono.poincare.ui.lesson.lessonThemeDescription
import be.saikono.poincare.ui.lesson.lessonThemeTitle
import be.saikono.poincare.ui.lesson.highlightPhonemeReferences
import be.saikono.poincare.ui.parent.parentPinMatches
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiRefinementsTest {
    @Test
    fun profilePreloadProgressNeverMovesBackwardOrRepeats() {
        val reported = mutableListOf<Int>()
        val reporter = MonotonicProgressReporter(reported::add)

        listOf(0, 18, 44, 85, 12, 62, 85, 91, 91, 100).forEach(reporter::report)

        assertEquals(listOf(0, 18, 44, 85, 91, 100), reported)
    }

    @Test
    fun deletingAProfileRequiresTheCurrentParentPin() {
        assertTrue(parentPinMatches("2468", "2468"))
        assertFalse(parentPinMatches("1234", "2468"))
        assertTrue(parentPinMatches("1234", null))
        assertFalse(parentPinMatches("", null))
    }

    @Test
    fun puzzleTilesShowEarnedXpAndOnlyLessonTilesShowPercentages() {
        assertEquals("17 XP", subcategoryMetricText(lessonProgressPercent = null, earnedXp = 17))
        assertEquals("0 XP", subcategoryMetricText(lessonProgressPercent = null, earnedXp = -3))
        assertEquals("25%", subcategoryMetricText(lessonProgressPercent = 25, earnedXp = null))
        assertEquals(null, subcategoryMetricText(lessonProgressPercent = null, earnedXp = null))
    }

    @Test
    fun subcategoryVisibilityCombinesLessonsAndExtraExercises() {
        val lessonsAndExtra = resolveSubcategoryTileAvailability(
            targetId = "reading",
            selectedGrades = listOf(1, 2),
            extraExerciseGradeAvailability = mapOf("reading" to setOf(1)),
            lessonGradeAvailability = mapOf("reading" to setOf(1, 2))
        )
        val extraOnly = resolveSubcategoryTileAvailability(
            targetId = "dictation",
            selectedGrades = listOf(1),
            extraExerciseGradeAvailability = mapOf("dictation" to setOf(1)),
            lessonGradeAvailability = mapOf("reading" to setOf(1))
        )
        val empty = resolveSubcategoryTileAvailability(
            targetId = "unused",
            selectedGrades = listOf(1),
            extraExerciseGradeAvailability = mapOf("reading" to setOf(1)),
            lessonGradeAvailability = mapOf("reading" to setOf(1))
        )

        assertTrue(lessonsAndExtra.visible)
        assertTrue(lessonsAndExtra.hasLessons)
        assertTrue(lessonsAndExtra.hasExtraExercises)
        assertTrue(extraOnly.visible)
        assertFalse(extraOnly.hasLessons)
        assertTrue(extraOnly.hasExtraExercises)
        assertFalse(empty.visible)
    }

    @Test
    fun laterSubcategorySequenceUnlocksOnlyAfterEarlierLessonsAreComplete() {
        val first = SubCategory("first", "Eerst", 0, gradeSequences = mapOf(1 to 1))
        val second = SubCategory("second", "Daarna", 0, gradeSequences = mapOf(1 to 2))
        val availability = mapOf("first" to setOf(1), "second" to setOf(1))

        assertFalse(isSubcategorySequenceUnlocked(
            subcategory = second,
            subcategories = listOf(first, second),
            selectedGrades = listOf(1),
            lessonGradeAvailability = availability,
            lessonProgressPercentByGrade = mapOf("first" to mapOf(1 to 99))
        ))
        assertTrue(isSubcategorySequenceUnlocked(
            subcategory = second,
            subcategories = listOf(first, second),
            selectedGrades = listOf(1),
            lessonGradeAvailability = availability,
            lessonProgressPercentByGrade = mapOf("first" to mapOf(1 to 100))
        ))
    }

    @Test
    fun storiesRemainEnabledBecauseTheyOpenTheDedicatedLibrary() {
        val storyAvailability = resolveSubcategoryTileAvailability(
            targetId = "stories",
            selectedGrades = listOf(1),
            extraExerciseGradeAvailability = mapOf("stories" to setOf(1)),
            lessonGradeAvailability = emptyMap()
        )

        assertTrue(
            subcategoryTileEnabled(
                targetId = "stories",
                lessonModeAvailable = true,
                availability = storyAvailability
            )
        )
    }

    @Test
    fun storiesAreTheFirstDoubleWidthLanguageTile() {
        val manifestFile = listOf(
            File("src/main/assets/${CurriculumManifestLoader.ASSET_PATH}"),
            File("app/src/main/assets/${CurriculumManifestLoader.ASSET_PATH}")
        ).first(File::exists)
        val manifest = manifestFile.inputStream().use(CurriculumManifestLoader::fromStream)

        val language = getSubCategoriesForDomain(
            domain = LearningDomain.READING.name,
            manifest = manifest,
            locale = "nl-BE",
            selectedGrades = listOf(1)
        )

        assertEquals("language.stories", language.first().id)
        assertEquals(2, language.first().columnSpan)
        assertTrue(language.drop(1).all { it.columnSpan == 1 })
    }

    @Test
    fun puzzleTilesUseTheirExerciseBankWhileEmptyLessonTilesStayGreyedOut() {
        val puzzleAvailability = resolveSubcategoryTileAvailability(
            targetId = "sudoku",
            selectedGrades = listOf(1),
            extraExerciseGradeAvailability = emptyMap(),
            lessonGradeAvailability = emptyMap()
        )
        val emptyLessonAvailability = resolveSubcategoryTileAvailability(
            targetId = "writing",
            selectedGrades = listOf(1),
            extraExerciseGradeAvailability = mapOf("writing" to setOf(1)),
            lessonGradeAvailability = mapOf("reading" to setOf(1))
        )

        assertTrue(subcategoryTileEnabled("sudoku", lessonModeAvailable = true, puzzleAvailability))
        assertFalse(subcategoryTileEnabled("writing", lessonModeAvailable = true, emptyLessonAvailability))
    }

    @Test
    fun puzzlesUseDirectRuntimeTilesInsteadOfManifestLessonGroups() {
        val manifestFile = listOf(
            File("src/main/assets/${CurriculumManifestLoader.ASSET_PATH}"),
            File("app/src/main/assets/${CurriculumManifestLoader.ASSET_PATH}")
        ).first(File::exists)
        val manifest = manifestFile.inputStream().use(CurriculumManifestLoader::fromStream)

        val puzzles = getSubCategoriesForDomain(
            domain = LearningDomain.PUZZLE.name,
            manifest = manifest,
            locale = "nl-BE",
            selectedGrades = listOf(1)
        )

        assertEquals(
            listOf(
                "sudoku", "math-crosswords", "word-seeker", "mothership-dock",
                "galaxycon", "calculator-puzzle", "constellations", "orbit-builder"
            ),
            puzzles.map { it.id }
        )
    }

    @Test
    fun writingManifestShowsFutureSubcategoriesAsDisabledTiles() {
        val manifestFile = listOf(
            File("src/main/assets/${CurriculumManifestLoader.ASSET_PATH}"),
            File("app/src/main/assets/${CurriculumManifestLoader.ASSET_PATH}")
        ).first(File::exists)
        val manifest = manifestFile.inputStream().use(CurriculumManifestLoader::fromStream)

        val writing = getSubCategoriesForDomain(
            domain = LearningDomain.WRITING.name,
            manifest = manifest,
            locale = "nl-BE",
            selectedGrades = listOf(1)
        )

        assertEquals(9, writing.size)
        assertEquals("letter_tracing", writing.first().id)
        assertTrue(writing.any { it.id == "spelling.rules_word_patterns" })
        val futureAvailability = resolveSubcategoryTileAvailability(
            targetId = "spelling.rules_word_patterns",
            selectedGrades = listOf(1),
            extraExerciseGradeAvailability = emptyMap(),
            lessonGradeAvailability = mapOf("letter_tracing" to setOf(1))
        )
        assertFalse(subcategoryTileEnabled("spelling.rules_word_patterns", lessonModeAvailable = true, futureAvailability))
    }

    @Test
    fun FrenchVocabularyTileHasAnExplicitLanguageName() {
        assertEquals(
            "Woordenschat Frans",
            getSubCategoriesForDomain(LearningDomain.READING.name).single { it.id == "vocabulary" }.nameNl
        )
        assertEquals(
            "Woordenschat Engels",
            getSubCategoriesForDomain(LearningDomain.READING.name).single { it.id == "vocabulary_en" }.nameNl
        )
    }

    @Test
    fun lessonGroupsExposeCurriculumThemesInsteadOfArbitraryChunks() {
        assertEquals(
            "Schrijven & schriftelijke expressie",
            lessonThemeTitle("unit.g1.nl.writing", "Schrijven", "nl-BE")
        )
        assertEquals(
            "5 lessen, van eenvoudig naar moeilijker",
            lessonThemeDescription(5, "nl-BE")
        )
    }

    @Test
    fun phonemeMarkersAreHiddenWhileTheLetterKeepsEmphasis() {
        val annotated = highlightPhonemeReferences("Welke letter hoort bij /m/?")
        assertEquals("Welke letter hoort bij m?", annotated.text)
        assertEquals(1, annotated.spanStyles.size)
        assertEquals("m", annotated.text.substring(annotated.spanStyles.single().start, annotated.spanStyles.single().end))
    }

    @Test
    fun geometryComparisonLessonsHaveOneTileAndAllLegacyPracticeChoices() {
        val geometry = getSubCategoriesForDomain(LearningDomain.GEOMETRY.name)
        val comparison = geometry.single { it.id == "compare_size" }

        assertEquals(
            listOf("time", "measurement", "temperature", "compare_size", "geometry_shapes"),
            geometry.map { it.id }
        )
        assertEquals(
            listOf("compare_size", "compare_weight", "compare_height", "compare_length", "compare_distance"),
            comparison.options.map { it.targetId }
        )
    }
}
