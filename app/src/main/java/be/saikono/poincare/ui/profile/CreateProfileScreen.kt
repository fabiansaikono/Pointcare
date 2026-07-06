package be.saikono.poincare.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.StarWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileScreen(
    viewModel: ProfileViewModel,
    onProfileCreated: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf("2018") }
    var readingLevel by remember { mutableStateOf(SkillLevel.FIRST_GRADE) }
    var writingLevel by remember { mutableStateOf(SkillLevel.FIRST_GRADE) }
    var mathLevel by remember { mutableStateOf(SkillLevel.FIRST_GRADE) }
    var puzzleLevel by remember { mutableStateOf(SkillLevel.FIRST_GRADE) }
    var geometryLevel by remember { mutableStateOf(SkillLevel.FIRST_GRADE) }
    var worldKnowledgeLevel by remember { mutableStateOf(SkillLevel.FIRST_GRADE) }
    var giftedMode by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlue)
            .systemNavigationMargin()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Nieuwe missie starten",
                style = MaterialTheme.typography.headlineMedium,
                color = StarWhite
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Naam van de astronaut") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = StarWhite,
                    unfocusedTextColor = StarWhite,
                    focusedLabelColor = StarWhite,
                    unfocusedLabelColor = StarWhite
                )
            )

            OutlinedTextField(
                value = birthYear,
                onValueChange = { birthYear = it },
                label = { Text("Geboortejaar") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = StarWhite,
                    unfocusedTextColor = StarWhite,
                    focusedLabelColor = StarWhite,
                    unfocusedLabelColor = StarWhite
                )
            )

            LevelSelector("Leesniveau", readingLevel) { readingLevel = it }
            LevelSelector("Schrijfniveau", writingLevel) { writingLevel = it }
            LevelSelector("Wiskundeniveau", mathLevel) { mathLevel = it }
            LevelSelector("Puzzelniveau", puzzleLevel) { puzzleLevel = it }
            LevelSelector("Meetkundeniveau", geometryLevel) { geometryLevel = it }
            LevelSelector("Wereldkennis niveau", worldKnowledgeLevel) { worldKnowledgeLevel = it }


            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(text = "Gifted Mode", color = StarWhite)
                Switch(checked = giftedMode, onCheckedChange = { giftedMode = it })
            }

            Button(
                onClick = {
                    viewModel.createProfile(
                        name = name,
                        birthYear = birthYear.toIntOrNull() ?: 2018,
                        readingLevel = readingLevel,
                        writingLevel = writingLevel,
                        mathLevel = mathLevel,
                        puzzleLevel = puzzleLevel,
                        geometryLevel = geometryLevel,
                        worldKnowledgeLevel = worldKnowledgeLevel,
                        giftedMode = giftedMode,
                        onComplete = onProfileCreated
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                enabled = name.isNotBlank()
            ) {
                Text("Start Missie")
            }
        }
    }
}

@Composable
fun LevelSelector(
    label: String,
    currentLevel: SkillLevel,
    onLevelSelected: (SkillLevel) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = StarWhite)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkillLevel.entries.forEach { level ->
                FilterChip(
                    selected = currentLevel == level,
                    onClick = { onLevelSelected(level) },
                    label = { Text(level.displayLabel()) },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = StarWhite,
                        selectedLabelColor = DeepSpaceBlue,
                        selectedContainerColor = StarWhite
                    )
                )
            }
        }
    }
}

private fun SkillLevel.displayLabel(): String = if (this == SkillLevel.NOT_APPLICABLE) "N/A" else (ordinal + 1).toString()
