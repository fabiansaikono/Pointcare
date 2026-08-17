package be.saikono.poincare.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import be.saikono.poincare.ui.theme.PoincareTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainCategoryHeaderInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sharedCategoryHeaderShowsFullTitleNextLessonAndProgress() {
        var nextLessonClicked = false
        composeRule.setContent {
            PoincareTheme {
                MainCategoryHeader(
                    title = "Rekenen & getalbegrip",
                    progressPercent = 42,
                    locale = "nl-BE",
                    onBack = {},
                    onNextLesson = { nextLessonClicked = true }
                )
            }
        }

        composeRule.onNodeWithText("Rekenen & getalbegrip").assertIsDisplayed()
        composeRule.onNodeWithText("42%").assertIsDisplayed()
        composeRule.onNodeWithText("Volgende les").performClick()
        composeRule.runOnIdle { assertTrue(nextLessonClicked) }
    }
}
