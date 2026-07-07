package be.saikono.poincare.ui.math

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import be.saikono.poincare.model.*
import be.saikono.poincare.ui.animations.CorrectAnswerPulse
import be.saikono.poincare.ui.animations.IncorrectAnswerShake
import be.saikono.poincare.ui.animations.MissionTransition
import be.saikono.poincare.ui.localizedExerciseText
import be.saikono.poincare.ui.components.CustomNumberPad
import be.saikono.poincare.ui.components.DifficultyStars
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.theme.*

@Composable
fun MathScreen(
    viewModel: MathViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    DisposableEffect(Unit) { onDispose { viewModel.stopAudio() } }
    MathContent(
        uiState = uiState,
        onNavigateBack = { viewModel.stopAudio(); onNavigateBack() },
        onNumberClick = viewModel::onNumberClick,
        onDeleteClick = viewModel::onDeleteClick,
        onTextInputChanged = viewModel::onTextInputChanged,
        onChoiceSelected = viewModel::onChoiceSelected,
        onConfirmClick = viewModel::onConfirmClick,
        onQuestionClick = viewModel::playCurrentQuestion,
        onNextExercise = viewModel::nextExercise
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MathContent(
    uiState: MathUiState,
    onNavigateBack: () -> Unit,
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onTextInputChanged: (String) -> Unit,
    onChoiceSelected: (String) -> Unit,
    onConfirmClick: () -> Unit,
    onQuestionClick: () -> Unit,
    onNextExercise: () -> Unit
) {
    val exercise = uiState.currentExercise

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlue)
    ) {
        SpaceBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 800.dp) // Optimized for tablets
                .align(Alignment.TopCenter)
                .systemNavigationMargin()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = localizedExerciseText("backToMap", uiState.exerciseLanguage), tint = StarWhite)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = exercise?.localizedTopic(uiState.exerciseLanguage) ?: "Missie",
                    style = MaterialTheme.typography.titleLarge,
                    color = StarWhite
                )
                Spacer(modifier = Modifier.weight(1f))
                DifficultyStars(exercise?.data?.get("difficultyStars")?.toIntOrNull())
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PlanetGold)
                }
            } else if (exercise != null) {
                MissionTransition(
                    visible = true,
                    reducedMotion = uiState.reducedMotion
                ) {
                    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(40.dp))

                        val promptAndAnswer: @Composable ColumnScope.() -> Unit = {
                            Text(
                                text = exercise.prompt.get(uiState.exerciseLanguage),
                                style = MaterialTheme.typography.headlineSmall,
                                color = StarWhite,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .clickable { onQuestionClick() }
                                    .semantics { contentDescription = "Vraag: ${exercise.prompt.get(uiState.exerciseLanguage)}" }
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            CorrectAnswerPulse(trigger = uiState.isCorrect == true, reducedMotion = uiState.reducedMotion) { pulseModifier ->
                                IncorrectAnswerShake(trigger = uiState.isCorrect == false, reducedMotion = uiState.reducedMotion) { shakeModifier ->
                                    Box(modifier = pulseModifier.then(shakeModifier)) {
                                        AnswerDisplay(input = uiState.userInput, isCorrect = uiState.isCorrect, showFeedback = uiState.showFeedback)
                                    }
                                }
                            }
                        }

                        val inputArea: @Composable ColumnScope.() -> Unit = {
                            ExerciseInputArea(
                                exercise = exercise,
                                input = uiState.userInput,
                                onNumberClick = onNumberClick,
                                onDeleteClick = onDeleteClick,
                                onTextInputChanged = onTextInputChanged,
                                onChoiceSelected = onChoiceSelected,
                                onConfirmClick = onConfirmClick,
                                hapticEnabled = uiState.hapticEnabled,
                                locale = uiState.exerciseLanguage,
                                isLandscape = isLandscape,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            )
                        }

                        if (isLandscape) {
                            Column(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, content = promptAndAnswer)
                                Spacer(modifier = Modifier.height(18.dp))
                                Column(modifier = Modifier.fillMaxWidth(), content = inputArea)
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, content = promptAndAnswer)
                            Spacer(modifier = Modifier.weight(1f))
                            Column(content = inputArea)
                        }

                        // Next Button
                        val buttonText = if (uiState.isFinished) localizedExerciseText("backToMap", uiState.exerciseLanguage) else localizedExerciseText("nextMission", uiState.exerciseLanguage)
                        val buttonColor = when {
                            uiState.isFinished -> PlanetGold
                            uiState.isCorrect == true -> PlanetGold
                            uiState.isWrong -> PlanetRed
                            else -> Color.Gray.copy(alpha = 0.5f)
                        }

                        Button(
                            onClick = if (uiState.isFinished) onNavigateBack else onNextExercise,
                            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .semantics { contentDescription = if (uiState.isCorrect == true) localizedExerciseText("nextMissionReady", uiState.exerciseLanguage) else localizedExerciseText("nextMission", uiState.exerciseLanguage) },
                            enabled = uiState.isCorrect == true || uiState.isFinished
                        ) {
                            Text(
                                text = buttonText, 
                                color = if (uiState.isCorrect == true || uiState.isFinished) DeepSpaceBlue else StarWhite,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = localizedExerciseText("noMissions", uiState.exerciseLanguage), color = StarWhite)
                }
            }
        }
    }
}

@Composable
fun ExerciseInputArea(
    exercise: ExerciseContent,
    input: String,
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onTextInputChanged: (String) -> Unit,
    onChoiceSelected: (String) -> Unit,
    onConfirmClick: () -> Unit,
    hapticEnabled: Boolean,
    locale: String = "nl-BE",
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val xmlType = exercise.data["xmlType"]
    val options = exercise.localizedOptions(locale)
    when {
        exercise.inputMode == InputMode.TAP_CHOICE && options.isNotEmpty() -> {
            Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                options.chunked(if (isLandscape) 2 else 1).forEach { rowOptions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        rowOptions.forEach { option ->
                            OptionSurface(
                                option = option,
                                selected = input == option,
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                onClick = { onChoiceSelected(option) }
                            )
                        }
                        if (isLandscape && rowOptions.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        xmlType == "open_text" || xmlType == "fill_blank" -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onTextInputChanged,
                    label = { Text(localizedExerciseText("answer", locale)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = StarWhite, unfocusedTextColor = StarWhite)
                )
                Button(onClick = onConfirmClick, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(localizedExerciseText("check", locale))
                }
            }
        }
        else -> {
            CustomNumberPad(
                onNumberClick = onNumberClick,
                onDeleteClick = onDeleteClick,
                onConfirmClick = onConfirmClick,
                hapticEnabled = hapticEnabled,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun OptionSurface(option: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) PlanetBlue.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
        contentColor = StarWhite,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = option,
                style = if (isDisplayNumber(option)) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AnswerDisplay(
    input: String,
    isCorrect: Boolean?,
    showFeedback: Boolean
) {
    val backgroundColor = when {
        !showFeedback -> Color.White.copy(alpha = 0.1f)
        isCorrect == true -> Color(0xFF4CAF50).copy(alpha = 0.2f)
        else -> Color(0xFFF44336).copy(alpha = 0.2f)
    }

    val borderColor = when {
        !showFeedback -> Color.Transparent
        isCorrect == true -> Color(0xFF4CAF50)
        else -> Color(0xFFF44336)
    }

    Box(
        modifier = Modifier
            .width(200.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .then(
                if (showFeedback) Modifier.background(borderColor, shape = RoundedCornerShape(16.dp))
                else Modifier
            )
            .padding(2.dp)
            .semantics { contentDescription = "${localizedExerciseText("yourAnswer", "nl-BE")}: ${if (input.isEmpty()) localizedExerciseText("empty", "nl-BE") else input}" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(DeepSpaceBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = input.ifEmpty { "?" },
                style = MaterialTheme.typography.displayMedium,
                color = if (input.isEmpty()) StarWhite.copy(alpha = 0.3f) else StarWhite
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MathScreenPreview() {
    PoincareTheme {
        MathContent(
            uiState = MathUiState(
                currentExercise = ExerciseContent(
                    id = "preview",
                    domain = LearningDomain.MATH,
                    skill = "multiplication",
                    section = ExerciseSection.EXERCISES,
                    level = SkillLevel.FIRST_GRADE,
                    inputMode = InputMode.CUSTOM_NUMBER_PAD,
                    prompt = LocalizedText("Hoeveel is 7 x 8?"),
                    answer = "56",
                    data = emptyMap()
                ),
                userInput = "5",
                isLoading = false
            ),
            onNavigateBack = {},
            onNumberClick = {},
            onDeleteClick = {},
            onTextInputChanged = {},
            onChoiceSelected = {},
            onConfirmClick = {},
            onQuestionClick = {},
            onNextExercise = {}
        )
    }
}
