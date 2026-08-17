package be.saikono.poincare.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.StarWhite

data class NextLessonOption(
    val id: String,
    val title: String,
    val subcategoryTitle: String,
    val status: String
)

@Composable
fun NextLessonOverviewDialog(
    lessons: List<NextLessonOption>,
    locale: String,
    onLessonSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepSpaceBlue.copy(alpha = 0.98f),
        title = {
            Text(
                text = nextLessonDialogText("title", locale),
                color = PlanetGold,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (lessons.isEmpty()) {
                Text(nextLessonDialogText("empty", locale), color = StarWhite.copy(alpha = 0.72f))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(lessons, key = { it.id }) { lesson ->
                        Surface(
                            color = PlanetBlue.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().clickable { onLessonSelected(lesson.id) }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text(
                                    text = lesson.title,
                                    color = StarWhite,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${lesson.subcategoryTitle} · ${lesson.status}",
                                    color = StarWhite.copy(alpha = 0.66f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("theory", "practice", "test").forEach { part ->
                                        Surface(
                                            color = StarWhite.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(999.dp)
                                        ) {
                                            Text(
                                                text = nextLessonDialogText(part, locale),
                                                color = PlanetGold,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(nextLessonDialogText("close", locale), color = PlanetGold)
            }
        }
    )
}

private fun nextLessonDialogText(key: String, locale: String): String = when (
    locale.substringBefore('-').substringBefore('_').lowercase()
) {
    "fr" -> when (key) {
        "title" -> "Leçons disponibles"
        "empty" -> "Aucune nouvelle leçon n’est disponible."
        "theory" -> "Théorie"
        "practice" -> "Exercices"
        "test" -> "Test"
        "close" -> "Fermer"
        else -> key
    }
    "en" -> when (key) {
        "title" -> "Available lessons"
        "empty" -> "No new lesson is available."
        "theory" -> "Theory"
        "practice" -> "Practise"
        "test" -> "Test"
        "close" -> "Close"
        else -> key
    }
    else -> when (key) {
        "title" -> "Beschikbare lessen"
        "empty" -> "Er is nog geen nieuwe les beschikbaar."
        "theory" -> "Theorie"
        "practice" -> "Oefen"
        "test" -> "Toets"
        "close" -> "Sluiten"
        else -> key
    }
}
