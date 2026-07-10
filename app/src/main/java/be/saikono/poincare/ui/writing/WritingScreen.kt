package be.saikono.poincare.ui.writing

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import be.saikono.poincare.model.*
import be.saikono.poincare.ui.components.DifficultyStars
import be.saikono.poincare.ui.missionCompleteGoBackText
import be.saikono.poincare.ui.components.LetterTracingComponent
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.localizedExerciseText
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.theme.*

@Composable
fun WritingScreen(
    viewModel: WritingViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    DisposableEffect(Unit) { onDispose { viewModel.stopAudio() } }
    WritingContent(
        uiState = uiState,
        onNavigateBack = { viewModel.stopAudio(); onNavigateBack() },
        onPathUpdate = { viewModel.onPathUpdate(it) },
        onComplete = { viewModel.onTraceComplete() },
        onTextInputChanged = viewModel::onTextInputChanged,
        onChoiceSelected = viewModel::onChoiceSelected,
        onConfirmClick = viewModel::onConfirmClick,
        onQuestionClick = viewModel::playCurrentQuestion,
        onNextExercise = { viewModel.nextExercise() }
    )
}

@Composable
fun WritingContent(
    uiState: WritingUiState,
    onNavigateBack: () -> Unit,
    onPathUpdate: (List<Offset>) -> Unit,
    onComplete: () -> Unit,
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
                .widthIn(max = 800.dp) // Tablet support
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
                Spacer(modifier = Modifier.height(40.dp))
                val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                val targetLetter = exercise.data["letter"]
                val promptBlock: @Composable ColumnScope.() -> Unit = {
                    MissingLetterPrompt(
                        exercise = exercise,
                        locale = uiState.exerciseLanguage,
                        onClick = onQuestionClick
                    )
                }
                val answerBlock: @Composable ColumnScope.() -> Unit = {
                    if (targetLetter != null || exercise.inputMode == InputMode.TRACE_LETTER) {
                        LetterTracingComponent(
                            targetLetter = targetLetter ?: "",
                            onPathUpdate = onPathUpdate,
                            onComplete = onComplete,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .semantics { contentDescription = "Teken-vlak voor de letter $targetLetter" }
                        )
                    } else {
                        WritingAnswerArea(
                            exercise = exercise,
                            locale = uiState.exerciseLanguage,
                            input = uiState.userInput,
                            onTextInputChanged = onTextInputChanged,
                            onChoiceSelected = onChoiceSelected,
                            onConfirmClick = onConfirmClick,
                            isLandscape = isLandscape,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (isLandscape) {
                    Column(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, content = promptBlock)
                        Spacer(modifier = Modifier.height(18.dp))
                        Column(modifier = Modifier.fillMaxWidth(), content = answerBlock)
                        Spacer(modifier = Modifier.weight(1f))
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, content = promptBlock)
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(modifier = Modifier.weight(1f).fillMaxWidth(), content = answerBlock)
                }

                Spacer(modifier = Modifier.height(24.dp))

                val buttonText = if (uiState.isFinished) missionCompleteGoBackText(uiState.exerciseLanguage) else localizedExerciseText("nextMission", uiState.exerciseLanguage)
                Button(
                    onClick = if (uiState.isFinished) onNavigateBack else onNextExercise,
                    enabled = uiState.isComplete || uiState.isFinished,
                    colors = ButtonDefaults.buttonColors(containerColor = if (uiState.isComplete || uiState.isFinished) PlanetGold else Color.Gray.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics { contentDescription = localizedExerciseText("nextMissionReady", uiState.exerciseLanguage) }
                ) {
                    Text(buttonText, color = if (uiState.isComplete || uiState.isFinished) DeepSpaceBlue else StarWhite, fontWeight = FontWeight.Medium)
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
private fun MissingLetterPrompt(
    exercise: ExerciseContent,
    locale: String,
    onClick: () -> Unit
) {
    val prompt = exercise.prompt.get(locale)
    val visibleLine = exercise.data["missingVisibleLine"]
    val marker = exercise.data["missingMarker"] ?: "?"
    val markerLine = if (exercise.data["xmlType"] == "missing_letter_choice" && !visibleLine.isNullOrBlank()) {
        buildAnnotatedString {
            val line = visibleLine
            var index = 0
            while (index < line.length) {
                if (line.startsWith(marker, index)) {
                    pushStyle(SpanStyle(color = PlanetGold, fontWeight = FontWeight.Bold))
                    append(marker)
                    pop()
                    index += marker.length
                } else {
                    append(line[index])
                    index += 1
                }
            }
        }
    } else null
    Text(
        text = prompt,
        style = MaterialTheme.typography.headlineSmall,
        color = StarWhite,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clickable { onClick() }
            .semantics { contentDescription = "Vraag: $prompt" }
    )
    if (markerLine != null) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = markerLine,
            style = MaterialTheme.typography.headlineMedium,
            color = StarWhite,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WritingAnswerArea(
    exercise: ExerciseContent,
    locale: String,
    input: String,
    onTextInputChanged: (String) -> Unit,
    onChoiceSelected: (String) -> Unit,
    onConfirmClick: () -> Unit,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    // POINCARE_REPAIR_V29: stable-answer-shuffle
    val options = remember(exercise.id, locale) { exercise.localizedOptions(locale).shuffled() }
    if (options.isNotEmpty()) {
        Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            options.chunked(if (isLandscape) 2 else 1).forEach { rowOptions ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    rowOptions.forEach { option ->
                        WritingOptionSurface(
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
    } else {
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
}

@Composable
private fun WritingOptionSurface(option: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
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

@Preview(showBackground = true)
@Composable
fun WritingScreenPreview() {
    PoincareTheme {
        WritingContent(
            uiState = WritingUiState(
                currentExercise = ExerciseContent(
                    id = "preview",
                    domain = LearningDomain.WRITING,
                    skill = "letter_tracing",
                    section = ExerciseSection.EXERCISES,
                    level = SkillLevel.FIRST_GRADE,
                    inputMode = InputMode.TRACE_LETTER,
                    prompt = LocalizedText("Schrijf de letter 'a'"),
                    answer = "success",
                    data = mapOf("letter" to "a")
                ),
                isLoading = false
            ),
            onNavigateBack = {},
            onPathUpdate = {},
            onComplete = {},
            onTextInputChanged = {},
            onChoiceSelected = {},
            onConfirmClick = {},
            onQuestionClick = {},
            onNextExercise = {}
        )
    }
}
