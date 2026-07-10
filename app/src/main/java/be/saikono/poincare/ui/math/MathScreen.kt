package be.saikono.poincare.ui.math

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
import androidx.compose.material.icons.filled.ArrowBack
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
import be.saikono.poincare.model.*
import be.saikono.poincare.ui.animations.CorrectAnswerPulse
import be.saikono.poincare.ui.animations.IncorrectAnswerShake
import be.saikono.poincare.ui.animations.MissionTransition
import be.saikono.poincare.ui.components.CustomNumberPad
import be.saikono.poincare.ui.components.DifficultyStars
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.localizedExerciseText
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.theme.*
import kotlin.math.roundToInt

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
    onCalculationSymbolDropped: (String, String) -> Unit,
    onCalculationCellClicked: (String) -> Unit,
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
                        Icons.Default.ArrowBack,
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PlanetGold)
                    }
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
                                onNextExercise = onNextExercise
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
    onNextExercise: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var cellCenters by remember(calculation.missionId) { mutableStateOf<Map<String, Offset>>(emptyMap()) }

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
            text = exercise.prompt.get(locale),
            style = MaterialTheme.typography.titleMedium,
            color = StarWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clickable { onQuestionClick() }
                .semantics { contentDescription = "Vraag: ${exercise.prompt.get(locale)}" }
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
                    symbols = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "−"),
                    isLandscape = isLandscape,
                    onSymbolDropped = dropSymbol
                )
                Button(
                    onClick = onConfirmClick,
                    enabled = calculation.isAnswerComplete(entries) && isCorrect != true,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isWrong) PlanetRed else PlanetBlue,
                        contentColor = DeepSpaceBlue,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.35f),
                        disabledContentColor = StarWhite.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(
                        text = localizedExerciseText("check", locale),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                board(Modifier.weight(1f).fillMaxHeight())
                controls(Modifier.widthIn(min = 180.dp, max = 230.dp))
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

        NextMissionButton(
            isFinished = false,
            isCorrect = isCorrect,
            isWrong = isWrong,
            locale = locale,
            onClick = onNextExercise
        )
    }
}

@Composable
private fun CalculationBoard(
    calculation: WrittenCalculation,
    entries: Map<String, String>,
    showFeedback: Boolean,
    isCorrect: Boolean?,
    locale: String,
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
            val cellSize = when {
                maxWidth < 340.dp -> 34.dp
                maxWidth < 500.dp -> 40.dp
                else -> 48.dp
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                when (calculation.operation) {
                    WrittenOperation.ADDITION -> AdditionBoard(
                        calculation, entries, showFeedback, isCorrect, locale,
                        cellSize, onCellPositioned, onCellClicked
                    )
                    WrittenOperation.SUBTRACTION -> SubtractionBoard(
                        calculation, entries, showFeedback, isCorrect, locale,
                        cellSize, onCellPositioned, onCellClicked
                    )
                    WrittenOperation.MULTIPLICATION -> MultiplicationBoard(
                        calculation, entries, showFeedback, isCorrect, locale,
                        cellSize, onCellPositioned, onCellClicked
                    )
                    WrittenOperation.DIVISION -> DivisionBoard(
                        calculation, entries, showFeedback, isCorrect, locale,
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
    cellSize: Dp,
    onCellPositioned: (String, Offset) -> Unit,
    onCellClicked: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        PlaceValueHeader(calculation.columnCount, locale, cellSize, includePrefix = true)
        SupportCaption(writtenText("carry", locale))
        EditableGridRow(calculation.supportCellIds, entries, cellSize, true, null, onCellPositioned, onCellClicked)
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
    cellSize: Dp,
    onCellPositioned: (String, Offset) -> Unit,
    onCellClicked: (String) -> Unit
) {
    val crossedOutColumns = calculation.supportCellIds.mapIndexedNotNull { index, id ->
        index.takeIf { entries[id].orEmpty().isNotBlank() }
    }.toSet()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        PlaceValueHeader(calculation.columnCount, locale, cellSize, includePrefix = true)
        SupportCaption(writtenText("borrow", locale))
        EditableGridRow(calculation.supportCellIds, entries, cellSize, true, null, onCellPositioned, onCellClicked)
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
    cellSize: Dp,
    onCellPositioned: (String, Offset) -> Unit,
    onCellClicked: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        PlaceValueHeader(calculation.columnCount, locale, cellSize, includePrefix = true)
        SupportCaption(writtenText("carry", locale))
        EditableGridRow(calculation.supportCellIds, entries, cellSize, true, null, onCellPositioned, onCellClicked)
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
                    prefix = (row + 1).toString(),
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
    cellSize: Dp,
    onCellPositioned: (String, Offset) -> Unit,
    onCellClicked: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SupportCaption(writtenText("longDivision", locale))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                PlaceValueHeader(calculation.columnCount, locale, cellSize, includePrefix = false)
                FixedGridRowWithoutPrefix(calculation.fixedDigits(calculation.first), cellSize)
                GridDivider(calculation.columnCount, cellSize)
                repeat(calculation.divisionScratchRows) { row ->
                    val ids = (0 until calculation.columnCount).map { col -> "scratch_${row}_$col" }
                    EditableGridRowWithoutPrefix(ids, entries, cellSize, onCellPositioned, onCellClicked)
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
            id = calculation.resultSignCellId,
            text = entries[calculation.resultSignCellId].orEmpty(),
            cellSize = cellSize,
            editable = true,
            support = false,
            feedback = feedback,
            onCellPositioned = onCellPositioned,
            onCellClicked = onCellClicked
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
    crossedOut: Boolean = false,
    onCellPositioned: (String, Offset) -> Unit = { _, _ -> },
    onCellClicked: (String) -> Unit = {}
) {
    val background = when {
        feedback == true -> PlanetBlue.copy(alpha = 0.32f)
        feedback == false -> PlanetRed.copy(alpha = 0.34f)
        editable && support -> PlanetBlue.copy(alpha = 0.10f)
        editable -> Color.White.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val borderColor = when {
        feedback == true -> PlanetBlue
        feedback == false -> PlanetRed
        editable && support -> PlanetBlue.copy(alpha = 0.42f)
        editable -> StarWhite.copy(alpha = 0.38f)
        else -> Color.Transparent
    }
    val textColor = when {
        editable && support -> PlanetGold
        feedback == false -> StarWhite
        else -> StarWhite
    }

    Box(
        modifier = Modifier
            .size(cellSize)
            .clip(RoundedCornerShape(if (support) 7.dp else 9.dp))
            .background(background)
            .then(
                if (editable) Modifier.border(
                    width = if (feedback != null) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(if (support) 7.dp else 9.dp)
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
            style = if (cellSize >= 44.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            textDecoration = if (crossedOut) TextDecoration.LineThrough else TextDecoration.None
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
        color = PlanetGold.copy(alpha = 0.85f),
        contentColor = DeepSpaceBlue,
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
    onNavigateBack: () -> Unit
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

        if (isLandscape) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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

        NextMissionButton(
            isFinished = uiState.isFinished,
            isCorrect = uiState.isCorrect,
            isWrong = uiState.isWrong,
            locale = uiState.exerciseLanguage,
            onClick = if (uiState.isFinished) onNavigateBack else onNextExercise
        )
    }
}

@Composable
private fun NextMissionButton(
    isFinished: Boolean,
    isCorrect: Boolean?,
    isWrong: Boolean,
    locale: String,
    onClick: () -> Unit
) {
    val buttonText = if (isFinished) {
        localizedExerciseText("backToMap", locale)
    } else {
        localizedExerciseText("nextMission", locale)
    }
    val buttonColor = when {
        isFinished -> PlanetGold
        isCorrect == true -> PlanetGold
        isWrong -> PlanetRed
        else -> Color.Gray.copy(alpha = 0.5f)
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics {
                contentDescription = if (isCorrect == true) {
                    localizedExerciseText("nextMissionReady", locale)
                } else {
                    localizedExerciseText("nextMission", locale)
                }
            },
        enabled = isCorrect == true || isFinished
    ) {
        Text(
            text = buttonText,
            color = if (isCorrect == true || isFinished) CockpitGray else StarWhite,
            fontWeight = FontWeight.Bold
        )
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
            Column(
                modifier = modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StarWhite,
                        unfocusedTextColor = StarWhite
                    )
                )
                Button(
                    onClick = onConfirmClick,
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
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
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = option,
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
                contentDescription = "${localizedExerciseText("yourAnswer", "nl-BE")}: ${if (input.isEmpty()) localizedExerciseText("empty", "nl-BE") else input}"
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
            onNextExercise = {}
        )
    }
}
