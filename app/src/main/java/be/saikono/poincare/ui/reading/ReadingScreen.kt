package be.saikono.poincare.ui.reading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.saikono.poincare.model.*
import be.saikono.poincare.ui.components.AzertyBeKeyboard
import be.saikono.poincare.ui.components.DifficultyStars
import be.saikono.poincare.ui.missionCompleteGoBackText
import be.saikono.poincare.ui.components.TapToTranslateText
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.localizedExerciseText
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.theme.*

@Composable
fun ReadingScreen(
    viewModel: ReadingViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    DisposableEffect(Unit) { onDispose { viewModel.stopAudio() } }
    ReadingContent(
        uiState = uiState,
        onNavigateBack = { viewModel.stopAudio(); onNavigateBack() },
        onPlayPrompt = viewModel::playListeningAudio,
        onPlayQuestion = viewModel::playQuestion,
        onOptionSelected = viewModel::onOptionSelected,
        onTextCharacter = viewModel::onTextCharacterAdded,
        onTextBackspace = viewModel::onTextBackspace,
        onTextSubmit = viewModel::submitDictation,
        onNextExercise = viewModel::nextExercise,
        onTranslationUsed = viewModel::onTranslationUsed
    )
}

@Composable
fun ReadingContent(
    uiState: ReadingUiState,
    onNavigateBack: () -> Unit,
    onPlayPrompt: () -> Unit,
    onPlayQuestion: () -> Unit,
    onOptionSelected: (String) -> Unit,
    onTextCharacter: (String) -> Unit,
    onTextBackspace: () -> Unit,
    onTextSubmit: () -> Unit,
    onNextExercise: () -> Unit,
    onTranslationUsed: () -> Unit
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
                .systemNavigationMargin()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Terug", tint = StarWhite)
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
                Box(modifier = Modifier.fillMaxSize())
            } else if (exercise != null) {
                Spacer(modifier = Modifier.height(if (exercise.inputMode == InputMode.CUSTOM_TEXT_KEYBOARD) 12.dp else 40.dp))
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                val promptBlock: @Composable ColumnScope.() -> Unit = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        TapToTranslateText(
                            text = exercise.prompt.get(uiState.exerciseLanguage),
                            translation = exercise.translation?.get(uiState.supportLanguage) ?: exercise.prompt.get(uiState.supportLanguage),
                            onClick = onPlayQuestion,
                            onTranslationUsed = onTranslationUsed
                        )

                        val visualIcon = exercise.localizedData("visualIcon", uiState.exerciseLanguage)
                        if (!visualIcon.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = visualIcon,
                                fontSize = 72.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics {
                                    contentDescription = exercise.localizedData("visualAlt", uiState.exerciseLanguage).orEmpty()
                                }
                            )
                        }

                        if (exercise.inputMode == InputMode.LISTEN_AND_SELECT || exercise.inputMode == InputMode.CUSTOM_TEXT_KEYBOARD) {
                            Spacer(modifier = Modifier.height(16.dp))
                            FilledIconButton(
                                onClick = onPlayPrompt,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = PlanetBlue, contentColor = DeepSpaceBlue)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Luister")
                            }
                        }
                    }
                }

                val contentBlock: @Composable ColumnScope.() -> Unit = {
                    when (exercise.inputMode) {
                        InputMode.LISTEN_AND_SELECT, InputMode.TAP_CHOICE -> {
                            LetterSoundRenderer(
                                options = remember(exercise.id, uiState.exerciseLanguage) {
                                    // POINCARE_REPAIR_V29: stable-answer-shuffle
                                    exercise.localizedOptions(uiState.exerciseLanguage).shuffled()
                                },
                                selectedOption = uiState.selectedOption,
                                isCorrect = uiState.isCorrect,
                                isLandscape = isLandscape,
                                onOptionSelected = onOptionSelected
                            )
                        }
                        InputMode.BUILD_WORD -> Text("Woord Bouwen (Binnenkort)", color = StarWhite)
                        InputMode.CUSTOM_TEXT_KEYBOARD -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
                            ) {
                                Surface(
                                    color = when {
                                        uiState.isCorrect == true -> PlanetGold.copy(alpha = .24f)
                                        uiState.isWrong -> PlanetRed.copy(alpha = .24f)
                                        else -> Color.White.copy(alpha = .08f)
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().height(if (isLandscape) 48.dp else 60.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                                        Text(
                                            text = uiState.builtWord.ifBlank { "…" },
                                            color = when {
                                                uiState.isCorrect == true -> PlanetGold
                                                uiState.isWrong -> PlanetRed
                                                else -> StarWhite
                                            },
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                AzertyBeKeyboard(
                                    locale = uiState.exerciseLanguage,
                                    onCharacter = onTextCharacter,
                                    onBackspace = onTextBackspace,
                                    onSubmit = onTextSubmit,
                                    compact = isLandscape,
                                    enabled = uiState.isCorrect != true,
                                    wrongKey = uiState.dictationWrongKey,
                                    hintKey = uiState.dictationHintKey,
                                    hintSequence = uiState.dictationHintSequence,
                                    revealedKeys = uiState.dictationRevealKeys,
                                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
                                )
                            }
                        }
                        else -> Text(localizedExerciseText("unknownType", uiState.exerciseLanguage), color = StarWhite)
                    }
                }

                if (isLandscape) {
                    Column(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(modifier = Modifier.fillMaxWidth(), content = promptBlock)
                        Spacer(modifier = Modifier.height(18.dp))
                        Column(modifier = Modifier.fillMaxWidth(), content = contentBlock)
                        Spacer(modifier = Modifier.weight(1f))
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), content = promptBlock)
                    Spacer(modifier = Modifier.weight(1f))
                    Column(modifier = Modifier.fillMaxWidth(), content = contentBlock)
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Next Button
                val buttonText = if (uiState.isFinished) missionCompleteGoBackText(uiState.exerciseLanguage) else localizedExerciseText("nextMission", uiState.exerciseLanguage)
                val buttonColor = when {
                    uiState.isFinished -> PlanetGold
                    uiState.isCorrect == true -> PlanetGold
                    uiState.isWrong -> PlanetRed
                    else -> Color.Gray.copy(alpha = 0.5f)
                }

                Button(
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    onClick = if (uiState.isFinished) onNavigateBack else onNextExercise,
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp)
                        .semantics { contentDescription = if (uiState.isCorrect == true) localizedExerciseText("nextMissionReady", uiState.exerciseLanguage) else localizedExerciseText("nextMission", uiState.exerciseLanguage) },
                    enabled = uiState.isCorrect == true || uiState.isFinished
                ) {
                    Text(
                        text = buttonText, 
                        color = if (uiState.isCorrect == true || uiState.isFinished) DeepSpaceBlue else StarWhite,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = localizedExerciseText("noMissions", uiState.exerciseLanguage), color = StarWhite)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReadingScreenPreview() {
    PoincareTheme {
        ReadingContent(
            uiState = ReadingUiState(
                currentExercise = ExerciseContent(
                    id = "preview",
                    domain = LearningDomain.READING,
                    skill = "letter_sound",
                    section = ExerciseSection.EXERCISES,
                    level = SkillLevel.FIRST_GRADE,
                    inputMode = InputMode.LISTEN_AND_SELECT,
                    prompt = LocalizedText("Welke letter hoor je?"),
                    answer = "m",
                    data = mapOf("options" to "m,s,p,r")
                ),
                isLoading = false,
                selectedOption = "m",
                isCorrect = true
            ),
            onNavigateBack = {},
            onPlayPrompt = {},
            onPlayQuestion = {},
            onOptionSelected = {},
            onTextCharacter = {},
            onTextBackspace = {},
            onTextSubmit = {},
            onNextExercise = {},
            onTranslationUsed = {}
        )
    }
}

@Composable
fun LetterSoundRenderer(
    options: List<String>,
    selectedOption: String?,
    isCorrect: Boolean?,
    isLandscape: Boolean,
    onOptionSelected: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Column(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.chunked(if (isLandscape) 2 else 1).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                rowOptions.forEach { option ->
                    LetterTile(option, selectedOption, isCorrect, onOptionSelected, modifier = Modifier.weight(1f))
                }
                if (isLandscape && rowOptions.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
    }
}

@Composable
fun LetterTile(
    option: String,
    selectedOption: String?,
    isCorrect: Boolean?,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isThisSelected = selectedOption == option
    val backgroundColor = when {
        isThisSelected && isCorrect == true -> Color(0xFF4CAF50).copy(alpha = 0.35f)
        isThisSelected && isCorrect == false -> Color(0xFFF44336).copy(alpha = 0.35f)
        isThisSelected -> PlanetBlue.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    Surface(
        color = backgroundColor,
        contentColor = StarWhite,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable { onOptionSelected(option) }
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
