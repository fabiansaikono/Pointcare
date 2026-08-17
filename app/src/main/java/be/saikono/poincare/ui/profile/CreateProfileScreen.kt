package be.saikono.poincare.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalAppLanguage
import be.saikono.poincare.ui.components.GradeMultiSelector
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.parent.ParentPinGate
import be.saikono.poincare.ui.parent.parentText
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.StarWhite

private enum class CreateProfileStep { LANGUAGE, PIN, DETAILS }

@Composable
fun CreateProfileScreen(
    viewModel: ProfileViewModel,
    onProfileCreated: () -> Unit,
    onCancel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val locale = LocalAppLanguage.current
    var stepName by rememberSaveable { mutableStateOf(CreateProfileStep.LANGUAGE.name) }
    val step = CreateProfileStep.valueOf(stepName)

    when (step) {
        CreateProfileStep.LANGUAGE -> CreateProfileLanguageScreen(
            onLanguageSelected = { languageCode ->
                viewModel.setAppLanguage(languageCode)
                stepName = CreateProfileStep.PIN.name
            },
            onCancel = onCancel
        )

        CreateProfileStep.PIN -> ParentPinGate(
            correctPin = uiState.parentPin ?: DEFAULT_PARENT_PIN,
            onPinCorrect = { stepName = CreateProfileStep.DETAILS.name },
            onCancel = onCancel,
            supportingText = if (uiState.profiles.isEmpty() && uiState.parentPin == null) {
                firstProfilePinHint(locale)
            } else {
                null
            }
        )

        CreateProfileStep.DETAILS -> CreateProfileDetails(
            viewModel = viewModel,
            onProfileCreated = onProfileCreated
        )
    }
}

@Composable
private fun CreateProfileLanguageScreen(
    onLanguageSelected: (String) -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlue)
            .systemNavigationMargin()
    ) {
        SpaceBackground()
        IconButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = StarWhite)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Kies je taal · Choisissez votre langue · Choose your language",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = StarWhite,
                textAlign = TextAlign.Center
            )
            listOf(
                "nl-BE" to "Nederlands",
                "fr-BE" to "Français",
                "en-US" to "English"
            ).forEach { (code, label) ->
                Button(
                    onClick = { onLanguageSelected(code) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun CreateProfileDetails(
    viewModel: ProfileViewModel,
    onProfileCreated: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var birthYear by rememberSaveable { mutableStateOf("") }
    var readingGrades by remember { mutableStateOf(setOf(1)) }
    var writingGrades by remember { mutableStateOf(setOf(1)) }
    var mathGrades by remember { mutableStateOf(setOf(1)) }
    var geometryGrades by remember { mutableStateOf(setOf(1)) }
    var worldGrades by remember { mutableStateOf(setOf(1)) }
    var showUnassignedExercises by rememberSaveable { mutableStateOf(false) }
    var hardMode by rememberSaveable { mutableStateOf(false) }
    val gradeHint = parentText("gradeSelectionHint")
    val focusManager = LocalFocusManager.current
    val validBirthYear = birthYear.toIntOrNull()?.takeIf { it in 1900..2100 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlue)
            .systemNavigationMargin()
            .padding(24.dp)
    ) {
        SpaceBackground()
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = parentText("createProfileTitle"),
                style = MaterialTheme.typography.headlineMedium,
                color = StarWhite
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(parentText("astronautName")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
                colors = createProfileTextFieldColours()
            )

            OutlinedTextField(
                value = birthYear,
                onValueChange = { value -> birthYear = value.filter(Char::isDigit).take(4) },
                label = { Text(parentText("birthYear")) },
                placeholder = { Text("2018", color = StarWhite.copy(alpha = 0.42f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                isError = birthYear.isNotEmpty() && validBirthYear == null,
                modifier = Modifier.fillMaxWidth(),
                colors = createProfileTextFieldColours()
            )

            GradeMultiSelector(parentText("reading"), readingGrades, { readingGrades = it }, gradeHint)
            GradeMultiSelector(parentText("writing"), writingGrades, { writingGrades = it }, gradeHint)
            GradeMultiSelector(parentText("math"), mathGrades, { mathGrades = it }, gradeHint)
            GradeMultiSelector(parentText("geometry"), geometryGrades, { geometryGrades = it }, gradeHint)
            GradeMultiSelector(parentText("worldKnowledge"), worldGrades, { worldGrades = it }, gradeHint)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = parentText("showMixedExercises"),
                        color = StarWhite,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = parentText("mixedExerciseHint"),
                        color = StarWhite.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = showUnassignedExercises, onCheckedChange = { showUnassignedExercises = it })
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(text = parentText("giftedMode"), color = StarWhite)
                Switch(checked = hardMode, onCheckedChange = { hardMode = it })
            }

            Button(
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                onClick = {
                    viewModel.createProfile(
                        name = name,
                        birthYear = requireNotNull(validBirthYear),
                        selectedGrades = mapOf(
                            LearningDomain.READING to readingGrades,
                            LearningDomain.WRITING to writingGrades,
                            LearningDomain.MATH to mathGrades,
                            LearningDomain.GEOMETRY to geometryGrades,
                            LearningDomain.WORLD_KNOWLEDGE to worldGrades
                        ),
                        showUnassignedExercises = showUnassignedExercises,
                        giftedMode = hardMode,
                        onComplete = onProfileCreated
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                enabled = name.isNotBlank() && validBirthYear != null
            ) {
                Text(parentText("startMission"))
            }
        }
    }
}

@Composable
private fun createProfileTextFieldColours() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = StarWhite,
    unfocusedTextColor = StarWhite,
    focusedLabelColor = StarWhite,
    unfocusedLabelColor = StarWhite,
    focusedBorderColor = PlanetGold,
    cursorColor = PlanetGold
)

private fun firstProfilePinHint(locale: String): String = when (
    locale.substringBefore('-').substringBefore('_').lowercase()
) {
    "fr" -> "C’est le premier profil. Le code PIN par défaut est 1234."
    "en" -> "This is the first profile. The default PIN is 1234."
    else -> "Dit is het eerste profiel. De standaardpincode is 1234."
}
