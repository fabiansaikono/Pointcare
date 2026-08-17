package be.saikono.poincare.ui.tasks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import be.saikono.poincare.ui.theme.PoincareTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TasksScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun linkedTeacherTasksAreRenderedFromTheProvidedData() {
        composeRule.setContent {
            PoincareTheme {
                TasksScreen(
                    teacherId = "DEMO-POINCARE",
                    locale = "nl-BE",
                    tasks = listOf(
                        HomeworkTaskUi(
                            id = "task-1",
                            title = "Oefen de getallenlijn tot 20",
                            description = "Gerichte oefening",
                            dueLabel = "Voorbeeldtaak"
                        )
                    ),
                    isLoading = false,
                    onNavigateBack = {}
                )
            }
        }

        composeRule.onNodeWithText("ID van de leerkracht: DEMO-POINCARE").assertIsDisplayed()
        composeRule.onNodeWithText("Oefen de getallenlijn tot 20").assertIsDisplayed()
        composeRule.onNodeWithText("Voorbeeldtaak").assertIsDisplayed()
    }

    @Test
    fun missingTeacherShowsTheParentDashboardGuidance() {
        composeRule.setContent {
            PoincareTheme {
                TasksScreen(
                    teacherId = "",
                    locale = "nl-BE",
                    tasks = emptyList(),
                    isLoading = false,
                    onNavigateBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Geen leerkracht gekoppeld").assertIsDisplayed()
    }
}
