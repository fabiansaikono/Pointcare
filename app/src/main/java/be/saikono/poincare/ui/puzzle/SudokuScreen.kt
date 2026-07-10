package be.saikono.poincare.ui.puzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.saikono.poincare.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SudokuGame(
    initialStars: Int,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stars = initialStars.coerceIn(1, 3)
    val seed = rememberSaveable(stars) { Random.nextInt() }
    val puzzle = remember(stars, seed) { SudokuGenerator.generate(stars, seed) }
    var entries by rememberSaveable(seed, saver = sudokuEntriesSaver) { mutableStateOf<Map<Pair<Int, Int>, Int>>(emptyMap()) }
    var cellCenters by remember(puzzle) { mutableStateOf<Map<Pair<Int, Int>, Offset>>(emptyMap()) }
    var wrongFeedback by remember(puzzle) { mutableStateOf<SudokuWrongFeedback?>(null) }
    var wrongFeedbackRunId by remember(puzzle) { mutableIntStateOf(0) }
    var elapsedSeconds by rememberSaveable(seed) { mutableIntStateOf(0) }
    val complete = puzzle.solution.indices.all { row ->
        puzzle.solution[row].indices.all { col ->
            val cell = row to col
            val value = if (cell in puzzle.givens) puzzle.solution[row][col] else entries[cell]
            value == puzzle.solution[row][col]
        }
    }

    LaunchedEffect(wrongFeedbackRunId) {
        if (wrongFeedbackRunId > 0) {
            delay(1000)
            wrongFeedback = null
        }
    }

    LaunchedEffect(puzzle, complete) {
        while (!complete) {
            delay(1000)
            elapsedSeconds += 1
        }
    }
    LaunchedEffect(complete) {
        if (complete) onComplete()
    }
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(formatTimer(elapsedSeconds), color = PlanetGold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        val grid: @Composable (Modifier) -> Unit = { gridModifier ->
            SudokuGrid(
                puzzle = puzzle,
                entries = entries,
                complete = complete,
                wrongFeedback = wrongFeedback,
                wrongFeedbackRunId = wrongFeedbackRunId,
                modifier = gridModifier,
                onCellPositioned = { cell, center ->
                    cellCenters = cellCenters + (cell to center)
                },
                onCellClicked = { cell ->
                    if (cell !in puzzle.givens && !complete) {
                        entries = entries - cell
                        wrongFeedback = null
                    }
                }
            )
        }
        val bank: @Composable (Modifier) -> Unit = { bankModifier ->
            NumberBank(values = (1..9).toList(), maxValue = puzzle.size, modifier = bankModifier, onValueDropped = { value, dropPosition ->
                val target = cellCenters
                    .filterKeys { it !in puzzle.givens }
                    .minByOrNull { (_, center) -> (center - dropPosition).getDistance() }
                    ?.takeIf { (_, center) -> (center - dropPosition).getDistance() < 140f }
                    ?.key
                if (target != null) {
                    val feedback = buildSudokuWrongFeedback(puzzle, entries, target, value)
                    entries = entries + (target to value)
                    if (feedback != null) {
                        wrongFeedback = feedback
                        wrongFeedbackRunId += 1
                    } else {
                        wrongFeedback = null
                    }
                }
            })
        }
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    grid(Modifier.fillMaxSize())
                }
                Box(
                    modifier = Modifier
                        .width(178.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    bank(Modifier.wrapContentWidth())
                }
            }
        } else {
            grid(Modifier.fillMaxWidth().aspectRatio(1f))
            Spacer(modifier = Modifier.height(18.dp))
            bank(Modifier.fillMaxWidth())
        }
        if (complete) {
            Text("Sudoku opgelost!", color = PlanetGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SudokuGrid(
    puzzle: SudokuPuzzle,
    entries: Map<Pair<Int, Int>, Int>,
    complete: Boolean,
    wrongFeedback: SudokuWrongFeedback?,
    wrongFeedbackRunId: Int,
    modifier: Modifier = Modifier,
    onCellPositioned: (Pair<Int, Int>, Offset) -> Unit,
    onCellClicked: (Pair<Int, Int>) -> Unit
) {
    val conflictAnimation = remember { Animatable(0f) }
    LaunchedEffect(wrongFeedbackRunId) {
        conflictAnimation.snapTo(0f)
        if (wrongFeedback != null) {
            conflictAnimation.animateTo(1f, animationSpec = tween(durationMillis = 1000, easing = LinearEasing))
        }
    }
    val conflictProgress = conflictAnimation.value

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val gridSize = minOf(maxWidth, maxHeight)
        Box(modifier = Modifier.size(gridSize)) {
            Column(modifier = Modifier.matchParentSize()) {
                for (row in 0 until puzzle.size) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (col in 0 until puzzle.size) {
                            val cell = row to col
                            val isGiven = cell in puzzle.givens
                            val value = if (isGiven) puzzle.solution[row][col] else entries[cell]
                            val correct = value == null || value == puzzle.solution[row][col]
                            val isConflictingExistingValue = wrongFeedback?.conflictingCells?.contains(cell) == true
                            val conflictTextGrowth = if (isConflictingExistingValue) conflictProgress else 0f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .onGloballyPositioned { coordinates ->
                                        val position = coordinates.positionInWindow()
                                        onCellPositioned(cell, position + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f))
                                    }
                                    .background(
                                        when {
                                            isGiven -> Color.White.copy(alpha = 0.16f)
                                            value != null -> Color.White.copy(alpha = 0.70f)
                                            else -> Color.White.copy(alpha = 0.06f)
                                        }
                                    )
                                    .clickable(enabled = !isGiven && !complete && value != null) { onCellClicked(cell) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = value?.toString().orEmpty(),
                                    color = when {
                                        isConflictingExistingValue -> PlanetRed
                                        isGiven -> StarWhite
                                        value == null -> StarWhite
                                        correct -> PlanetGreen
                                        else -> PlanetRed
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = if (isConflictingExistingValue) (22f + 12f * conflictTextGrowth).sp else MaterialTheme.typography.titleMedium.fontSize
                                    ),
                                    fontWeight = when {
                                        isConflictingExistingValue -> FontWeight((650 + 350 * conflictTextGrowth).roundToInt().coerceIn(650, 1000))
                                        isGiven || value != null -> FontWeight.Black
                                        else -> FontWeight.Medium
                                    },
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
            SudokuGridLines(
                puzzle = puzzle,
                wrongFeedback = wrongFeedback,
                conflictProgress = conflictProgress,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun SudokuGridLines(
    puzzle: SudokuPuzzle,
    wrongFeedback: SudokuWrongFeedback?,
    conflictProgress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cellWidth = size.width / puzzle.size
        val cellHeight = size.height / puzzle.size
        val thinLineColor = Color.White.copy(alpha = 0.18f)
        val boxLineColor = Color.White.copy(alpha = 0.52f)
        for (col in 1 until puzzle.size) {
            val x = col * cellWidth
            drawLine(thinLineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f, cap = StrokeCap.Square)
        }
        for (row in 1 until puzzle.size) {
            val y = row * cellHeight
            drawLine(thinLineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, cap = StrokeCap.Square)
        }
        for (col in puzzle.boxCols until puzzle.size step puzzle.boxCols) {
            val x = col * cellWidth
            drawLine(boxLineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f, cap = StrokeCap.Square)
        }
        for (row in puzzle.boxRows until puzzle.size step puzzle.boxRows) {
            val y = row * cellHeight
            drawLine(boxLineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f, cap = StrokeCap.Square)
        }

        wrongFeedback?.let { feedback ->
            val pulse = abs(sin((conflictProgress * PI.toFloat() * 6f).toDouble())).toFloat()
            val red = PlanetRed.copy(alpha = 0.20f + 0.80f * pulse)
            val strokeWidth = 5f + 3f * pulse
            feedback.rows.forEach { row ->
                drawRect(
                    color = red,
                    topLeft = Offset(0f, row * cellHeight),
                    size = Size(size.width, cellHeight),
                    style = Stroke(width = strokeWidth)
                )
            }
            feedback.columns.forEach { col ->
                drawRect(
                    color = red,
                    topLeft = Offset(col * cellWidth, 0f),
                    size = Size(cellWidth, size.height),
                    style = Stroke(width = strokeWidth)
                )
            }
            feedback.boxes.forEach { box ->
                val top = box.first * puzzle.boxRows * cellHeight
                val left = box.second * puzzle.boxCols * cellWidth
                drawRect(
                    color = red,
                    topLeft = Offset(left, top),
                    size = Size(puzzle.boxCols * cellWidth, puzzle.boxRows * cellHeight),
                    style = Stroke(width = strokeWidth)
                )
            }
        }
    }
}

@Composable
private fun NumberBank(values: List<Int>, maxValue: Int, modifier: Modifier = Modifier, onValueDropped: (Int, Offset) -> Unit) {
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) 3 else 5
    val rows = values.chunked(columns)
    val verticalGap = if (isLandscape) 8.dp else 10.dp

    Column(
        verticalArrangement = Arrangement.spacedBy(verticalGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.wrapContentWidth(align = Alignment.CenterHorizontally)
    ) {
        rows.forEach { rowValues ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowValues.forEach { value ->
                    DraggableSudokuValue(value = value, enabled = value <= maxValue, onDropped = onValueDropped)
                }
                repeat(columns - rowValues.size) {
                    Spacer(modifier = Modifier.size(50.dp))
                }
            }
        }
    }
}

@Composable
private fun DraggableSudokuValue(value: Int, enabled: Boolean, onDropped: (Int, Offset) -> Unit) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var startPosition by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    Surface(
        color = if (enabled) PlanetGold.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
        contentColor = if (enabled) DeepSpaceBlue else StarWhite.copy(alpha = 0.35f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .onGloballyPositioned { if (!dragging) startPosition = it.positionInWindow() + Offset(it.size.width / 2f, it.size.height / 2f) }
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(50.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        onDropped(value, startPosition + Offset(offsetX, offsetY))
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
    ) { Box(contentAlignment = Alignment.Center) { Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) } }
}


private data class SudokuWrongFeedback(
    val target: Pair<Int, Int>,
    val value: Int,
    val rows: Set<Int>,
    val columns: Set<Int>,
    val boxes: Set<Pair<Int, Int>>,
    val conflictingCells: Set<Pair<Int, Int>>
)

private fun buildSudokuWrongFeedback(
    puzzle: SudokuPuzzle,
    entries: Map<Pair<Int, Int>, Int>,
    target: Pair<Int, Int>,
    value: Int
): SudokuWrongFeedback? {
    val correctValue = puzzle.solution[target.first][target.second]
    if (value == correctValue) return null

    fun valueAt(row: Int, col: Int): Int? {
        val cell = row to col
        return if (cell in puzzle.givens) puzzle.solution[row][col] else entries[cell]
    }

    val rowConflicts = mutableSetOf<Pair<Int, Int>>()
    val columnConflicts = mutableSetOf<Pair<Int, Int>>()
    val boxConflicts = mutableSetOf<Pair<Int, Int>>()

    for (col in 0 until puzzle.size) {
        val cell = target.first to col
        if (cell != target && valueAt(target.first, col) == value) rowConflicts += cell
    }
    for (row in 0 until puzzle.size) {
        val cell = row to target.second
        if (cell != target && valueAt(row, target.second) == value) columnConflicts += cell
    }

    val boxStartRow = (target.first / puzzle.boxRows) * puzzle.boxRows
    val boxStartCol = (target.second / puzzle.boxCols) * puzzle.boxCols
    for (row in boxStartRow until boxStartRow + puzzle.boxRows) {
        for (col in boxStartCol until boxStartCol + puzzle.boxCols) {
            val cell = row to col
            if (cell != target && valueAt(row, col) == value) boxConflicts += cell
        }
    }

    val rows = if (rowConflicts.isNotEmpty()) setOf(target.first) else emptySet()
    val columns = if (columnConflicts.isNotEmpty()) setOf(target.second) else emptySet()
    val boxes = if (boxConflicts.isNotEmpty()) setOf((target.first / puzzle.boxRows) to (target.second / puzzle.boxCols)) else emptySet()
    val conflicts = rowConflicts + columnConflicts + boxConflicts

    return if (conflicts.isEmpty()) {
        null
    } else {
        SudokuWrongFeedback(
            target = target,
            value = value,
            rows = rows,
            columns = columns,
            boxes = boxes,
            conflictingCells = conflicts
        )
    }
}

private fun formatTimer(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private val sudokuEntriesSaver = listSaver<MutableState<Map<Pair<Int, Int>, Int>>, String>(
    save = { state -> state.value.map { (cell, value) -> "${cell.first},${cell.second},$value" } },
    restore = { saved ->
        mutableStateOf(saved.mapNotNull { item ->
            val parts = item.split(",")
            if (parts.size != 3) return@mapNotNull null
            val row = parts[0].toIntOrNull() ?: return@mapNotNull null
            val col = parts[1].toIntOrNull() ?: return@mapNotNull null
            val value = parts[2].toIntOrNull() ?: return@mapNotNull null
            (row to col) to value
        }.toMap())
    }
)
