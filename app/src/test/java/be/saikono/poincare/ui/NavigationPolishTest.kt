package be.saikono.poincare.ui

import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.ui.home.SUBCATEGORY_FIRST_ROW_TOP_PADDING_DP
import be.saikono.poincare.ui.home.SUBCATEGORY_GRID_BOTTOM_PADDING_DP
import be.saikono.poincare.ui.home.SUBCATEGORY_SCREEN_BOTTOM_PADDING_DP
import be.saikono.poincare.ui.home.SUBCATEGORY_SCREEN_TOP_PADDING_DP
import be.saikono.poincare.ui.home.automaticPuzzleGrade
import be.saikono.poincare.ui.home.getSubCategoriesForDomain
import be.saikono.poincare.ui.lesson.LESSON_EXERCISE_FIRST_ROW_TOP_PADDING_DP
import be.saikono.poincare.ui.lesson.LESSON_HUB_BOTTOM_PADDING_DP
import be.saikono.poincare.ui.lesson.LESSON_HUB_FIRST_ROW_TOP_PADDING_DP
import be.saikono.poincare.ui.lesson.LESSON_ROW_ICON_WEIGHT
import be.saikono.poincare.ui.lesson.LESSON_ROW_META_WEIGHT
import be.saikono.poincare.ui.lesson.LESSON_SCREEN_BOTTOM_PADDING_DP
import be.saikono.poincare.ui.lesson.LESSON_SCREEN_TOP_PADDING_DP
import be.saikono.poincare.ui.lesson.defaultPhaseSectionTitle
import be.saikono.poincare.ui.spaceMap.MAIN_MENU_BOTTOM_PADDING_DP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolishTest {
    @Test
    fun lessonSelectionUsesAnIntroductionRouteBeforeThePlayer() {
        assertEquals("learning_galaxy", Screen.LearningGalaxy.route)
        assertEquals("lesson_intro/lesson.sample", Screen.LessonIntro.createRoute("lesson.sample"))
        assertEquals("Sectie A", defaultPhaseSectionTitle(0, "nl-BE"))
        assertEquals("Section B", defaultPhaseSectionTitle(1, "fr-BE"))
        assertEquals("Section C", defaultPhaseSectionTitle(2, "en-US"))
    }

    @Test
    fun puzzleGradeIsSelectedAutomaticallyFromTheAuthoritativeSourceGrades() {
        assertEquals(2, automaticPuzzleGrade(listOf(2, 4), fallbackGrade = 1))
        assertEquals(3, automaticPuzzleGrade(emptyList(), fallbackGrade = 3))
        assertEquals(1, automaticPuzzleGrade(emptyList(), fallbackGrade = null))
        assertEquals(5, automaticPuzzleGrade(listOf(0, 7, 5), fallbackGrade = 2))
    }

    @Test
    fun worldKnowledgeExposesEveryRoutedCurriculumTile() {
        val world = getSubCategoriesForDomain(LearningDomain.WORLD_KNOWLEDGE.name)
        val ids = world.map { it.id }

        assertEquals(ids.toSet().size, ids.size)
        assertTrue(ids.containsAll(setOf("space", "animals", "plants", "weather_climate")))
        assertEquals(
            listOf("nature", "animals", "plants"),
            world.single { it.id == "nature" }.options.map { it.targetId }
        )
        assertEquals(
            listOf("geography", "space", "weather_climate"),
            world.single { it.id == "geography" }.options.map { it.targetId }
        )
    }

    @Test
    fun calibratedScreenInsetsAlignVisibleFirstRowsAndReduceBottomSpace() {
        assertEquals(20, SUBCATEGORY_SCREEN_TOP_PADDING_DP)
        assertEquals(20, LESSON_SCREEN_TOP_PADDING_DP)
        assertEquals(28, SUBCATEGORY_FIRST_ROW_TOP_PADDING_DP)
        assertEquals(20, LESSON_HUB_FIRST_ROW_TOP_PADDING_DP)
        assertEquals(12, LESSON_EXERCISE_FIRST_ROW_TOP_PADDING_DP)
        assertEquals(6, SUBCATEGORY_SCREEN_BOTTOM_PADDING_DP)
        assertEquals(6, SUBCATEGORY_GRID_BOTTOM_PADDING_DP)
        assertEquals(10, LESSON_SCREEN_BOTTOM_PADDING_DP)
        assertEquals(16, LESSON_HUB_BOTTOM_PADDING_DP)
        assertEquals(8, MAIN_MENU_BOTTOM_PADDING_DP)
        assertEquals(.14f, LESSON_ROW_ICON_WEIGHT)
        assertEquals(.18f, LESSON_ROW_META_WEIGHT)
    }
}
