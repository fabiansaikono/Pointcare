package be.saikono.poincare.ui.math

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.saikono.poincare.model.*
import be.saikono.poincare.ui.animations.CorrectAnswerPulse
import be.saikono.poincare.ui.animations.IncorrectAnswerShake
import be.saikono.poincare.ui.animations.MissionTransition
import be.saikono.poincare.ui.components.DifficultyStars
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.localizedExerciseText
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.theme.*
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

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
        onCalculationSymbolDropped = viewModel::onCalculationSymbolDropped,
        onCalculationCellClicked = viewModel::onCalculationCellClicked,
        onConfirmClick = viewModel::onConfirmClick,
        onQuestionClick = viewModel::playCurrentQuestion,
        onNextExercise = viewModel::nextExercise,
        onResetExercise = viewModel::resetExercise
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
    onCalculationSymbolDropped: (String, String) -> Unit,
    onCalculationCellClicked: (String) -> Unit,
    onConfirmClick: () -> Unit,
    onQuestionClick: () -> Unit,
    onNextExercise: () -> Unit,
    onResetExercise: () -> Unit
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
                .widthIn(max = 900.dp)
                .align(Alignment.TopCenter)
                .systemNavigationMargin()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = localizedExerciseText("backToMap", uiState.exerciseLanguage),
                        tint = StarWhite
                    )
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

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize())
                }

                exercise != null -> {
                    MissionTransition(
                        visible = true,
                        reducedMotion = uiState.reducedMotion
                    ) {
                        val calculation = uiState.writtenCalculation
                        if (calculation != null) {
                            WrittenCalculationMission(
                                exercise = exercise,
                                calculation = calculation,
                                entries = uiState.calculationEntries,
                                isCorrect = uiState.isCorrect,
                                isWrong = uiState.isWrong,
                                showFeedback = uiState.showFeedback,
                                reducedMotion = uiState.reducedMotion,
                                locale = uiState.exerciseLanguage,
                                onQuestionClick = onQuestionClick,
                                onSymbolDropped = onCalculationSymbolDropped,
                                onCellClicked = onCalculationCellClicked,
                                onConfirmClick = onConfirmClick,
                                onNextExercise = onNextExercise,
                                onResetExercise = onResetExercise
                            )
                        } else {
                            LegacyMathMission(
                                exercise = exercise,
                                uiState = uiState,
                                onNumberClick = onNumberClick,
                                onDeleteClick = onDeleteClick,
                                onTextInputChanged = onTextInputChanged,
                                onChoiceSelected = onChoiceSelected,
                                onConfirmClick = onConfirmClick,
                                onQuestionClick = onQuestionClick,
                                onNextExercise = onNextExercise,
                                onResetExercise = onResetExercise,
                                onNavigateBack = onNavigateBack
                            )
                        }
                    }
                }

                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = localizedExerciseText("noMissions", uiState.exerciseLanguage),
                            color = StarWhite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WrittenCalculationMission(
    exercise: ExerciseContent,
    calculation: WrittenCalculation,
    entries: Map<String, String>,
    isCorrect: Boolean?,
    isWrong: Boolean,
    showFeedback: Boolean,
    reducedMotion: Boolean,
    locale: String,
    onQuestionClick: () -> Unit,
    onSymbolDropped: (String, String) -> Unit,
    onCellClicked: (String) -> Unit,
    onConfirmClick: () -> Unit,
    onNextExercise: () -> Unit,
    onResetExercise: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val landscapeControlsWidth = 230.dp
    var cellCenters by remember(calculation.missionId) { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    val helperHintAlpha = remember(calculation.missionId) { Animatable(0.03f) }
    LaunchedEffect(calculation.missionId, entries, isCorrect) {
        helperHintAlpha.snapTo(0.03f)
        delay(5_000)
        if (isCorrect != true) {
            repeat(3) {
                helperHintAlpha.animateTo(0.10f, animationSpec = tween(1_200))
                helperHintAlpha.animateTo(0.03f, animationSpec = tween(1_200))
            }
        }
    }

    val dropSymbol: (String, Offset) -> Unit = { symbol, dropPosition ->
        val target = cellCenters
            .filterKeys { calculation.acceptsSymbol(it, symbol) }
            .minByOrNull { (_, center) -> (center - dropPosition).getDistance() }
            ?.takeIf { (_, center) -> (center - dropPosition).getDistance() < 140f }
            ?.key
        if (target != null) onSymbolDropped(target, symbol)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = formatVisibleNumbers(exercise.prompt.get(locale)),
            style = MaterialTheme.typography.titleMedium,
            color = StarWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clickable { onQuestionClick() }
                .semantics { contentDescription = "Vraag: ${formatVisibleNumbers(exercise.prompt.get(locale))}" }
        )
        Spacer(modifier = Modifier.height(10.dp))

        val board: @Composable (Modifier) -> Unit = { modifier ->
            CorrectAnswerPulse(trigger = isCorrect == true, reducedMotion = reducedMotion) { pulseModifier ->
                IncorrectAnswerShake(trigger = isCorrect == false, reducedMotion = reducedMotion) { shakeModifier ->
                    CalculationBoard(
                        calculation = calculation,
                        entries = entries,
                        showFeedback = showFeedback,
                        isCorrect = isCorrect,
                        locale = locale,
                        helperHintAlpha = helperHintAlpha.value,
                        onCellPositioned = { id, center -> cellCenters = cellCenters + (id to center) },
                        onCellClicked = onCellClicked,
                        modifier = modifier.then(pulseModifier).then(shakeModifier)
                    )
                }
            }
        }

        val controls: @Composable (Modifier) -> Unit = { modifier ->
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = writtenText("dragInstruction", locale),
                    color = StarWhite.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                MathSymbolBank(
                    symbols = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                    isLandscape = isLandscape,
                    onSymbolDropped = dropSymbol
                )
            }
        }

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                board(Modifier.weight(1f).fillMaxHeight().heightIn(min = 310.dp))
                controls(Modifier.width(landscapeControlsWidth))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                board(Modifier.fillMaxWidth())
                controls(Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomMissionButton(
                    isFinished = false,
                    isCorrect = isCorrect,
                    isWrong = isWrong,
                    locale = locale,
                    onNextExercise = onNextExercise,
                    onResetExercise = onResetExercise,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(landscapeControlsWidth))
            }
        } else {
            BottomMissionButton(
                isFinished = false,
                isCorrect = isCorrect,
                isWrong = isWrong,
                locale = locale,
                onNextExercise = onNextExercise,
                onResetExercise = onResetExercise
            )
        }
    }
}

@Composable
private fun CalculationBoard(
    calculation: WrittenCalculation,
    entries: Map<String, String>,
    showFeedback: Boolean,
    isCorrect: Boolean?,
    locale: String,
    helperHintAlpha: Float,
    onCellPositioned: (String, Offset) -> Unit,
    onCellClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.065f),
        contentColor = StarWhite,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val preferredCellSize = when {
                maxWidth < 340.dp -> 42f
                maxWidth < 500.dp -> 50f
                else -> 58f
            }
            val logicalColumns = when (calculation.operation) {
                WrittenOperation.DIVISION -> calculation.columnCount +
                    maxOf(calculation.resultCellIds.size, calculation.second.toString().length) + 2
                else -> calculation.columnCount + 1
            }.coerceAtLeast(1)
            val logicalRows = when (calculation.operation) {
                WrittenOperation.ADDITION, WrittenOperation.SUBTRACTION -> 4
                WrittenOperation.MULTIPLICATION -> 4 + calculation.partialProductRows
                WrittenOperation.DIVISION -> calculation.divisionScratchRows + 2
            }.coerceAtLeast(1)
            val widthSpacing = 3f * (logicalColumns - 1)
            val fittedToWidth = ((maxWidth.value - 24f - widthSpacing) / logicalColumns)
                .coerceAtLeast(18f)
            val fittedToHeight = if (maxHeight.value.isFinite()) {
                val heightSpacing = 4f * (logicalRows - 1)
                ((maxHeight.value - 24f - heightSpacing) / logicalRows).coerceAtLeast(18f)
            } else {
                preferredCellSize
            }
            val cellSize = minOf(preferredCellSize, fittedToWidth, fittedToHeight).dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                when (calculation.operation) {
                    WrittenOperation.ADDITION -> AdditionBoard(
                        calculation, entries, showFeedback, isCorrect, locale, helperHintAlpha,
                        cellSize, onCellPositioned, onCellClicked
                    )
                    WrittenOperation.SUBTRACTION -> SubtractionBoard(
                        calculation, entries, showFeedback, isCorrect, locale, helperHintAlpha,
                        cellSize, onCellPositioned, onCellClicked
                    )
                    WrittenOperation.MULTIPLICATION -> MultiplicationBoard(
                        calculation, entries, showFeedback, isCorrect, locale, helperHintAlpha,
                        cellSize, onCellPositioned, onCellClicked
                    )
                    WrittenOperation.DIVISION -> DivisionBoard(
                        calculation, entries, showFeedback, isCorrect, locale, helperHintAlpha,
                        cellSize, onCellPositioned, onCellClicked
                    )
                }
            }
        }
    }
}

@Composable
private fun AdditionBoard(
    calculation: WrittenCalculation,
    entries: Map<String, String>,
    showFeedback: Boolean,
    isCorrect: Boolean?,
    locale: String,
    helperHintAlpha: Float,
    cellSize: Dp,
    onCellPositioned: (String, Offset) -> Unit,
    onCellClicked: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        EditableGridRow(calculation.supportCellIds, entries, cellSize, true, null, calculation, helperHintAlpha, onCellPositioned, onCellClicked)
        FixedGridRow("", calculation.fixedDigits(calculation.first), cellSize)
        FixedGridRow(calculation.operation.symbol, calculation.fixedDigits(calculation.second), cellSize)
        GridDivider(calculation.columnCount + 1, cellSize)
        ResultGridRow(calculation, entries, cellSize, showFeedback, isCorrect, onCellPositioned, onCellClicked)
    }
}

@Composable
private fun SubtractionBoard(
    calculation: WrittenCalculation,
    entries: Map<String, String>,
    showFeedback: Boolean,
    isCorrect: Boolean?,
    locale: String,
    helperHintAlpha: Float,
    cellSize: Dp,
    onCellPositioned: (String, Offset) -> Unit,
    onCellClicked: (String) -> Unit
) {
    val crossedOutColumns = calculation.supportCellIds.mapIndexedNotNull { index, id ->
        index.takeIf { entries[id].orEmpty().isNotBlank() }
    }.toSet()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        EditableGridRow(calculation.supportCellIds, entries, cellSize, true, null, calculation, helperHintAlpha, onCellPositioned, onCellClicked)
        FixedGridRow("", calculation.fixedDigits(calculation.first), cellSize, crossedOutColumns)
        FixedGridRow(calculation.operation.symbol, calculation.fixedDigits(calculation.second), cellSize)
        GridDivider(calculation.columnCount + 1, cellSize)
        ResultGridRow(calculation, entries, cellSize, showFeedback, isCorrect, onCellPositioned, onCellClicked)
    }
}

@Composable
private fun MultiplicationBoard(
    calculation: WrittenCalculation,
    entries: Map<String, String>,
    showFeedback: Boolean,
    isCorrect: Boolean?,
    locale: String,
    helperHintAlpha: Float,
    cellSize: Dp,
    onCellPositioned: (String, Offset) -> Unit,
    onCellClicked: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        EditableGridRow(calculation.supportCellIds, entries, cellSize, true, null, calculation, helperHintAlpha, onCellPositioned, onCellClicked)
        FixedGridRow("", calculation.fixedDigits(calculation.first), cellSize)
        FixedGridRow(calculation.operation.symbol, calculation.fixedDigits(calculation.second), cellSize)
        GridDivider(calculation.columnCount + 1, cellSize)
        if (calculation.partialProductRows > 0) {
            repeat(calculation.partialProductRows) { row ->
                val ids = (0 until calculation.columnCount).map { col -> "partial_${row}_$col" }
                EditableGridRow(
                    ids = ids,
                    entries = entries,
                    cellSize = cellSize,
                    support = true,
                    prefix = null,
                    calculation = calculation,
                    helperHintAlpha = helperHintAlpha,
                    onCellPositioned = onCellPositioned,
                    onCellClicked = onCellClicked
                )
            }
            GridDivider(calculation.columnCount + 1, cellSize)
        }
        ResultGridRow(calculation, entries, cellSize, showFeedback, isCorrect, onCellPositioned, onCellClicked)
    }
}

@Composable
private fun DivisionBoard(
    calculation: WrittenCalculation,
    entries: Map<String, String>,
    showFeedback: Boolean,
    isCorrect: Boolean?,
    locale: String,
    helperHintAlpha: Float,
    cellSize: Dp,
    onCellPositioned: (String, Offset) -> Unit,
    onCellClicked: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FixedGridRowWithoutPrefix(calculation.fixedDigits(calculation.first), cellSize)
                GridDivider(calculation.columnCount, cellSize)
                repeat(calculation.divisionScratchRows) { row ->
                    val ids = (0 until calculation.columnCount).map { col -> "scratch_${row}_$col" }
                    EditableGridRowWithoutPrefix(ids, entries, cellSize, calculation, helperHintAlpha, onCellPositioned, onCellClicked)
                    if (row < calculation.divisionScratchRows - 1 && row % 2 == 1) {
                        GridDivider(calculation.columnCount, cellSize)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height((cellSize.value * (calculation.divisionScratchRows + 2)).dp)
                    .background(StarWhite.copy(alpha = 0.72f))
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                MathGridCell(text = calculation.second.toString(), cellSize = cellSize, editable = false)
                HorizontalDivider(color = StarWhite.copy(alpha = 0.72f), thickness = 2.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    calculation.resultCellIds.forEach { id ->
                        MathGridCell(
                            id = id,
                            text = entries[id].orEmpty(),
                            cellSize = cellSize,
                            editable = true,
                            support = false,
                            feedback = feedbackValue(showFeedback, isCorrect),
                            onCellPositioned = onCellPositioned,
                            onCellClicked = onCellClicked
                        )
                    }
                }
                if (calculation.hasRemainderField) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("r =", color = PlanetGold, fontWeight = FontWeight.Bold)
                        calculation.remainderCellIds.forEach { id ->
                            MathGridCell(
                                id = id,
                                text = entries[id].orEmpty(),
                                cellSize = cellSize,
                                editable = true,
                                support = false,
                                feedback = feedbackValue(showFeedback, isCorrect),
                                onCellPositioned = onCellPositioned,
                                onCellClicked = onCellClicked
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportCaption(text: String) {
    Text(
        text = text,
        color = PlanetGold.copy(alpha = 0.9f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun PlaceValueHeader(count: Int, locale: String, cellSize: Dp, includePrefix: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (includePrefix) Spacer(modifier = Modifier.size(cellSize))
        placeValueLabels(count, locale).forEach { label ->
            Box(modifier = Modifier.size(cellSize), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    color = PlanetBlue.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FixedGridRow(
    prefix: String,
    digits: List<String>,
    cellSize: Dp,
    crossedOutColumns: Set<Int> = emptySet()
) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        MathGridCell(text = prefix, cellSize = cellSize, editable = false)
        digits.forEachIndexed { index, digit ->
            MathGridCell(
                text = digit,
                cellSize = cellSize,
                editable = false,
                crossedOut = index in crossedOutColumns
            )
        }
    }
}

@Composable
private fun FixedGridRowWithoutPrefix(digits: List<String>, cellSize: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        digits.forEach { digit -> MathGridCell(text = digit, cellSize = cellSize, editable = false) }
    }
}

@Composable
private fun EditableGridRow(
    ids: List<String>,
    entries: Map<String, String>,
    cellSize: Dp,
    support: Boolean,
    prefix: String?,
    calculation: WrittenCalculation,
    helperHintAlpha: Float,
    onCellPositioned: (String, Offset) -> Unit,
    onCellClicked: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        MathGridCell(text = prefix.orEmpty(), cellSize = cellSize, editable = false, support = support)
        ids.forEach { id ->
            MathGridCell(
                id = id,
                text = entries[id].orEmpty(),
                cellSize = cellSize,
                editable = true,
                support = support,
                helperHintAlpha = helperHintAlpha,
                helperCorrect = calculation.isHelperEntryCorrect(id, entries[id].orEmpty()),
                onCellPositioned = onCellPositioned,
                onCellClicked = onCellClicked
            )
        }
    }
}

@Composable
private fun EditableGridRowWithoutPrefix(
    ids: List<String>,
    entries: Map<String, String>,
    cellSize: Dp,
    calculation: WrittenCalculation,
    helperHintAlpha: Float,
    onCellPositioned: (String, Offset) -> Unit,
    onCellClicked: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        ids.forEach { id ->
            MathGridCell(
                id = id,
                text = entries[id].orEmpty(),
                cellSize = cellSize,
                editable = true,
                support = true,
                helperHintAlpha = helperHintAlpha,
                helperCorrect = calculation.isHelperEntryCorrect(id, entries[id].orEmpty()),
                onCellPositioned = onCellPositioned,
                onCellClicked = onCellClicked
            )
        }
    }
}

@Composable
private fun ResultGridRow(
    calculation: WrittenCalculation,
    entries: Map<String, String>,
    cellSize: Dp,
    showFeedback: Boolean,
    isCorrect: Boolean?,
    onCellPositioned: (String, Offset) -> Unit,
    onCellClicked: (String) -> Unit
) {
    val feedback = feedbackValue(showFeedback, isCorrect)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        MathGridCell(
            text = if (calculation.result < 0) "−" else "",
            cellSize = cellSize,
            editable = false,
            support = false,
            feedback = feedback
        )
        calculation.resultCellIds.forEach { id ->
            MathGridCell(
                id = id,
                text = entries[id].orEmpty(),
                cellSize = cellSize,
                editable = true,
                support = false,
                feedback = feedback,
                onCellPositioned = onCellPositioned,
                onCellClicked = onCellClicked
            )
        }
    }
}

@Composable
private fun MathGridCell(
    text: String,
    cellSize: Dp,
    editable: Boolean,
    id: String? = null,
    support: Boolean = false,
    feedback: Boolean? = null,
    helperHintAlpha: Float = 0.03f,
    helperCorrect: Boolean? = null,
    crossedOut: Boolean = false,
    onCellPositioned: (String, Offset) -> Unit = { _, _ -> },
    onCellClicked: (String) -> Unit = {}
) {
    val wrongTextAlpha = remember(id, text) { Animatable(1f) }
    LaunchedEffect(helperCorrect, text, id) {
        wrongTextAlpha.snapTo(1f)
        if (helperCorrect == false && text.isNotBlank()) {
            repeat(3) {
                wrongTextAlpha.animateTo(0.35f, animationSpec = tween(1_200))
                wrongTextAlpha.animateTo(1f, animationSpec = tween(1_200))
            }
        }
    }
    val background = when {
        feedback == true -> PlanetBlue.copy(alpha = 0.32f)
        feedback == false -> PlanetRed.copy(alpha = 0.34f)
        editable && support && helperCorrect == false -> PlanetRed.copy(alpha = 0.12f)
        editable && support -> PlanetBlue.copy(alpha = helperHintAlpha.coerceIn(0.03f, 0.10f))
        editable -> Color.White.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val borderColor = when {
        feedback == true -> PlanetBlue
        feedback == false -> PlanetRed
        editable -> StarWhite.copy(alpha = 0.38f)
        else -> Color.Transparent
    }
    val textColor = when {
        editable && support && helperCorrect == false -> PlanetRed.copy(alpha = wrongTextAlpha.value)
        editable && support -> PlanetBlue
        else -> StarWhite
    }
    val fittedFontSize = (cellSize.value * 0.58f).coerceIn(14f, 34f).sp

    Box(
        modifier = Modifier
            .size(cellSize)
            .clip(RoundedCornerShape(if (support) 7.dp else 9.dp))
            .background(background)
            .then(
                if (editable && !support) Modifier.border(
                    width = if (feedback != null) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(9.dp)
                ) else Modifier
            )
            .then(
                if (editable && id != null) {
                    Modifier
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInWindow()
                            onCellPositioned(
                                id,
                                position + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                            )
                        }
                        .clickable(enabled = text.isNotBlank()) { onCellClicked(id) }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = fittedFontSize,
                lineHeight = fittedFontSize
            ),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            textDecoration = if (crossedOut) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 1
        )
    }
}

@Composable
private fun GridDivider(cellCount: Int, cellSize: Dp) {
    HorizontalDivider(
        modifier = Modifier.width((cellSize.value * cellCount + 3f * (cellCount - 1)).dp),
        color = StarWhite.copy(alpha = 0.72f),
        thickness = 2.dp
    )
}

@Composable
private fun MathSymbolBank(
    symbols: List<String>,
    isLandscape: Boolean,
    onSymbolDropped: (String, Offset) -> Unit
) {
    val rows = if (isLandscape) symbols.chunked(3) else symbols.chunked(5)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { rowValues ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                rowValues.forEach { symbol ->
                    DraggableMathSymbol(symbol = symbol, onDropped = onSymbolDropped)
                }
            }
        }
    }
}

@Composable
private fun DraggableMathSymbol(
    symbol: String,
    onDropped: (String, Offset) -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var startPosition by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }

    Surface(
        color = DeepSpaceBlue.copy(alpha = 0.88f),
        contentColor = PlanetGold,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .onGloballyPositioned {
                if (!dragging) {
                    startPosition = it.positionInWindow() + Offset(it.size.width / 2f, it.size.height / 2f)
                }
            }
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(50.dp)
            .pointerInput(symbol) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        onDropped(symbol, startPosition + Offset(offsetX, offsetY))
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDragCancel = {
                        dragging = false
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

private fun feedbackValue(showFeedback: Boolean, isCorrect: Boolean?): Boolean? =
    if (!showFeedback) null else isCorrect

private fun placeValueLabels(count: Int, locale: String): List<String> {
    val fromRight = when {
        locale.startsWith("fr") -> listOf("U", "D", "C", "UM", "DM", "CM", "M", "DM", "CM")
        locale.startsWith("en") -> listOf("O", "T", "H", "Th", "TTh", "HTh", "M", "T M", "H M")
        else -> listOf("E", "T", "H", "D", "TD", "HD", "M", "TM", "HM")
    }
    val labels = if (count <= fromRight.size) {
        fromRight.take(count)
    } else {
        fromRight + List(count - fromRight.size) { "" }
    }
    return labels.reversed()
}

private fun writtenText(key: String, locale: String): String {
    val language = when {
        locale.startsWith("fr") -> "fr"
        locale.startsWith("en") -> "en"
        else -> "nl"
    }
    return when (key) {
        "carry" -> when (language) { "fr" -> "Retenues (facultatif)"; "en" -> "Carries (optional)"; else -> "Onthouden (optioneel)" }
        "borrow" -> when (language) { "fr" -> "Emprunts (facultatif)"; "en" -> "Borrowing (optional)"; else -> "Lenen / ontlenen (optioneel)" }
        "longDivision" -> when (language) { "fr" -> "Division écrite"; "en" -> "Long division"; else -> "Staartdeling" }
        "dragInstruction" -> when (language) {
            "fr" -> "Glisse les chiffres vers le résultat ou vers les cases d'aide. Touche une case remplie pour l'effacer."
            "en" -> "Drag digits to the result or helper cells. Tap a filled cell to clear it."
            else -> "Sleep de cijfers naar het resultaat of de hulpcellen. Tik op een ingevuld vakje om het te wissen."
        }
        else -> key
    }
}

@Composable
private fun LegacyMathMission(
    exercise: ExerciseContent,
    uiState: MathUiState,
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onTextInputChanged: (String) -> Unit,
    onChoiceSelected: (String) -> Unit,
    onConfirmClick: () -> Unit,
    onQuestionClick: () -> Unit,
    onNextExercise: () -> Unit,
    onResetExercise: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        val promptAndAnswer: @Composable ColumnScope.() -> Unit = {
            Text(
                text = formatVisibleNumbers(exercise.prompt.get(uiState.exerciseLanguage)),
                style = MaterialTheme.typography.headlineSmall,
                color = StarWhite,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable { onQuestionClick() }
                    .semantics { contentDescription = "Vraag: ${formatVisibleNumbers(exercise.prompt.get(uiState.exerciseLanguage))}" }
            )
            Spacer(modifier = Modifier.height(20.dp))
            CorrectAnswerPulse(trigger = uiState.isCorrect == true, reducedMotion = uiState.reducedMotion) { pulseModifier ->
                IncorrectAnswerShake(trigger = uiState.isCorrect == false, reducedMotion = uiState.reducedMotion) { shakeModifier ->
                    Box(modifier = pulseModifier.then(shakeModifier)) {
                        AnswerDisplay(
                            input = uiState.userInput,
                            isCorrect = uiState.isCorrect,
                            showFeedback = uiState.showFeedback
                        )
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

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.widthIn(min = 240.dp, max = 340.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = promptAndAnswer
                    )
                    Column(
                        modifier = Modifier.widthIn(min = 320.dp, max = 420.dp),
                        content = inputArea
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, content = promptAndAnswer)
                    Spacer(modifier = Modifier.height(20.dp))
                    Column(content = inputArea)
                }
            }
        }

        BottomMissionButton(
            isFinished = uiState.isFinished,
            isCorrect = uiState.isCorrect,
            isWrong = uiState.isWrong,
            locale = uiState.exerciseLanguage,
            onNextExercise = if (uiState.isFinished) onNavigateBack else onNextExercise,
            onResetExercise = onResetExercise
        )
    }
}

@Composable
private fun BottomMissionButton(
    isFinished: Boolean,
    isCorrect: Boolean?,
    isWrong: Boolean,
    locale: String,
    onNextExercise: () -> Unit,
    onResetExercise: () -> Unit,
    modifier: Modifier = Modifier
) {
    val solved = isCorrect == true || isFinished
    val buttonText = when {
        isFinished -> localizedExerciseText("backToMap", locale)
        isCorrect == true -> localizedExerciseText("nextMission", locale)
        else -> resetExerciseText(locale)
    }
    val buttonColor = when {
        isFinished -> PlanetGold
        isCorrect == true -> PlanetGold
        isWrong -> PlanetRed
        else -> PlanetBlue
    }

    Button(
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        onClick = if (solved) onNextExercise else onResetExercise,
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        modifier = modifier
            .fillMaxWidth()
            .height(45.dp)
            .semantics {
                contentDescription = when {
                    isCorrect == true -> localizedExerciseText("nextMissionReady", locale)
                    solved -> localizedExerciseText("backToMap", locale)
                    else -> resetExerciseText(locale)
                }
            },
        enabled = true
    ) {
        if (!solved) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = if (isWrong) StarWhite else DeepSpaceBlue)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = buttonText,
            color = if (isWrong && !solved) StarWhite else DeepSpaceBlue,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun resetExerciseText(locale: String): String = when {
    locale.startsWith("fr") -> "Réinitialiser l'exercice"
    locale.startsWith("en") -> "Reset exercise"
    else -> "Reset oefening"
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
            Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                options.chunked(if (isLandscape) 2 else 1).forEach { rowOptions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        rowOptions.forEach { option ->
                            OptionSurface(
                                option = option,
                                selected = input == option,
                                modifier = Modifier.weight(1f).heightIn(min = if (isLandscape) 48.dp else 56.dp, max = if (isLandscape) 96.dp else 140.dp),
                                onClick = { onChoiceSelected(option) }
                            )
                        }
                        if (isLandscape && rowOptions.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StarWhite,
                        unfocusedTextColor = StarWhite
                    )
                )
                Button(
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    onClick = onConfirmClick,
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(45.dp)
                ) {
                    Text(localizedExerciseText("check", locale))
                }
            }
        }

        else -> {
            MathAnswerPad(
                onNumberClick = onNumberClick,
                onConfirmClick = onConfirmClick,
                hapticEnabled = hapticEnabled,
                isLandscape = isLandscape,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun MathAnswerPad(
    onNumberClick: (String) -> Unit,
    onConfirmClick: () -> Unit,
    hapticEnabled: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "OK")
    )
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Column(
        modifier = Modifier.widthIn(max = if (isLandscape) 440.dp else 560.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
            ) {
                row.forEach { item ->
                    val isAction = item == "OK"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(if (isLandscape) 56.dp else 72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DeepSpaceBlue.copy(alpha = if (isAction) .94f else .86f))
                            .border(1.dp, PlanetGold.copy(alpha = if (isAction) .58f else .28f), RoundedCornerShape(16.dp))
                            .clickable {
                                if (hapticEnabled) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                }
                                if (item == "OK") onConfirmClick() else onNumberClick(item)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isAction) {
                            Text("✓", style = MaterialTheme.typography.headlineSmall, color = PlanetGold, fontWeight = FontWeight.Bold)
                        } else {
                            Text(item, style = if (isLandscape) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium, color = PlanetGold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
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
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = formatVisibleNumbers(option),
                style = if (isDisplayNumber(option)) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.bodyLarge
                },
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
            .semantics {
                contentDescription = "${localizedExerciseText("yourAnswer", "nl-BE")}: ${if (input.isEmpty()) localizedExerciseText("empty", "nl-BE") else formatVisibleNumbers(input)}"
            },
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
                text = if (input.isEmpty()) "?" else formatVisibleNumbers(input),
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
        val previewExercise = ExerciseContent(
            id = "preview",
            domain = LearningDomain.MATH,
            skill = "addition",
            section = ExerciseSection.EXERCISES,
            level = SkillLevel.THIRD_GRADE,
            inputMode = InputMode.DRAG_DROP_DIGITS,
            prompt = LocalizedText("Bereken 347 + 289. Gebruik de onthoudvakjes wanneer dat helpt."),
            answer = "636",
            data = mapOf(
                "writtenOperation" to WrittenOperation.ADDITION.name,
                "first" to "347",
                "second" to "289",
                "result" to "636",
                "remainder" to "0",
                "hasRemainderField" to "false",
                "grade" to "3",
                "difficultyStars" to "2"
            )
        )
        MathContent(
            uiState = MathUiState(
                currentExercise = previewExercise,
                writtenCalculation = WrittenCalculation.fromExercise(previewExercise),
                isLoading = false
            ),
            onNavigateBack = {},
            onNumberClick = {},
            onDeleteClick = {},
            onTextInputChanged = {},
            onChoiceSelected = {},
            onCalculationSymbolDropped = { _, _ -> },
            onCalculationCellClicked = {},
            onConfirmClick = {},
            onQuestionClick = {},
            onNextExercise = {},
            onResetExercise = {}
        )
    }
}
