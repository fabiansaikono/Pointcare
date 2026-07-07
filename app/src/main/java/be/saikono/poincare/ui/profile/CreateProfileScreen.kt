package be.saikono.poincare.ui.profile

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.PlanetGold
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
    var showUnassignedExercises by remember { mutableStateOf(false) }
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show unassigned exercises",
                        color = StarWhite,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Only enable this when you want exercises without a subcategory to appear.",
                        color = StarWhite.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = showUnassignedExercises,
                    onCheckedChange = { showUnassignedExercises = it }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
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
                        showUnassignedExercises = showUnassignedExercises,
                        giftedMode = giftedMode,
                        onComplete = onProfileCreated
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
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
    val selectableLevels = remember { SkillLevel.entries.filterNot { it == SkillLevel.NOT_APPLICABLE } }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = StarWhite)
        Spacer(modifier = Modifier.height(4.dp))
        selectableLevels.chunked(3).forEach { rowLevels ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowLevels.forEach { level ->
                    FilterChip(
                        selected = currentLevel == level,
                        onClick = { onLevelSelected(level) },
                        label = { Text(level.displayLabel()) },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = StarWhite,
                            selectedLabelColor = DeepSpaceBlue,
                            selectedContainerColor = PlanetGold
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowLevels.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun SkillLevel.displayLabel(): String = (ordinal + 1).toString()
