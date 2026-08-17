package be.saikono.poincare.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import be.saikono.poincare.ui.components.HomeworkTasksGlyph
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.PlanetGreen
import be.saikono.poincare.ui.theme.StarWhite

data class HomeworkTaskUi(
    val id: String,
    val title: String,
    val description: String = "",
    val dueLabel: String? = null,
    val completed: Boolean = false
)

@Composable
fun TasksScreen(
    teacherId: String,
    locale: String,
    tasks: List<HomeworkTaskUi>,
    isLoading: Boolean,
    loadFailed: Boolean = false,
    onNavigateBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(DeepSpaceBlue)) {
        SpaceBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 800.dp)
                .align(Alignment.TopCenter)
                .systemNavigationMargin()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = tasksText("back", locale),
                        tint = StarWhite
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = tasksText("title", locale),
                    color = StarWhite,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(14.dp))

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PlanetGold)
                }
                teacherId.isBlank() -> TasksEmptyState(
                    title = tasksText("noTeacher", locale),
                    body = tasksText("noTeacherBody", locale)
                )
                else -> {
                    Surface(
                        color = PlanetBlue.copy(alpha = 0.13f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "${tasksText("teacherId", locale)}: $teacherId",
                            color = StarWhite.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    if (loadFailed) {
                        TasksEmptyState(
                            title = tasksText("loadFailed", locale),
                            body = tasksText("loadFailedBody", locale)
                        )
                    } else if (tasks.isEmpty()) {
                        TasksEmptyState(
                            title = tasksText("empty", locale),
                            body = tasksText("emptyBody", locale)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(tasks, key = HomeworkTaskUi::id) { task ->
                                HomeworkTaskCard(task = task)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksEmptyState(title: String, body: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = StarWhite.copy(alpha = 0.06f),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeworkTasksGlyph(
                    contentDescription = title,
                    color = PlanetBlue,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = title,
                    color = StarWhite,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = body,
                    color = StarWhite.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun HomeworkTaskCard(task: HomeworkTaskUi) {
    Surface(
        color = StarWhite.copy(alpha = if (task.completed) 0.05f else 0.08f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (task.completed) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = PlanetGreen,
                    modifier = Modifier.size(30.dp)
                )
            } else {
                HomeworkTasksGlyph(
                    contentDescription = task.title,
                    color = PlanetGold,
                    modifier = Modifier.size(30.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = task.title,
                    color = StarWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        color = StarWhite.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                task.dueLabel?.takeIf(String::isNotBlank)?.let { due ->
                    Text(
                        text = due,
                        color = PlanetGold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

internal fun tasksText(key: String, locale: String): String {
    val language = locale.substringBefore('-').substringBefore('_').lowercase()
    return when (language) {
        "fr" -> when (key) {
            "back" -> "Retour"
            "title" -> "Devoirs et tâches"
            "teacherId" -> "Identifiant de l’enseignant"
            "noTeacher" -> "Aucun enseignant associé"
            "noTeacherBody" -> "Ajoutez l’identifiant de l’enseignant dans le tableau de bord parent pour recevoir des tâches."
            "empty" -> "Aucune tâche pour le moment"
            "emptyBody" -> "Les nouvelles tâches apparaîtront ici."
            "loadFailed" -> "Impossible de charger les tâches"
            "loadFailedBody" -> "Réessaie plus tard ou vérifie l’identifiant de l’enseignant."
            else -> key
        }
        "en" -> when (key) {
            "back" -> "Back"
            "title" -> "Homework & tasks"
            "teacherId" -> "Teacher ID"
            "noTeacher" -> "No teacher linked"
            "noTeacherBody" -> "Add the teacher ID in the parent dashboard to receive tasks."
            "empty" -> "No tasks yet"
            "emptyBody" -> "New homework and tasks will appear here."
            "loadFailed" -> "Tasks could not be loaded"
            "loadFailedBody" -> "Try again later or check the teacher ID."
            else -> key
        }
        else -> when (key) {
            "back" -> "Terug"
            "title" -> "Huiswerk & taken"
            "teacherId" -> "ID van de leerkracht"
            "noTeacher" -> "Geen leerkracht gekoppeld"
            "noTeacherBody" -> "Voeg de ID van de leerkracht toe in het ouderdashboard om taken te ontvangen."
            "empty" -> "Nog geen taken"
            "emptyBody" -> "Nieuwe huiswerktaken en opdrachten verschijnen hier."
            "loadFailed" -> "Taken konden niet worden geladen"
            "loadFailedBody" -> "Probeer later opnieuw of controleer de ID van de leerkracht."
            else -> key
        }
    }
}
