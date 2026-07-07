package be.saikono.poincare.ui.puzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import be.saikono.poincare.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
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
    var elapsedSeconds by rememberSaveable(seed) { mutableIntStateOf(0) }
    val complete = puzzle.solution.indices.all { row ->
        puzzle.solution[row].indices.all { col ->
            val cell = row to col
            val value = if (cell in puzzle.givens) puzzle.solution[row][col] else entries[cell]
            value == puzzle.solution[row][col]
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
            SudokuGrid(puzzle, entries, complete, modifier = gridModifier, onCellPositioned = { cell, center ->
                cellCenters = cellCenters + (cell to center)
            }, onCellClicked = { cell ->
                if (cell !in puzzle.givens && !complete) entries = entries - cell
            })
        }
        val bank: @Composable (Modifier) -> Unit = { bankModifier ->
            NumberBank(values = (1..9).toList(), maxValue = puzzle.size, modifier = bankModifier, onValueDropped = { value, dropPosition ->
                val target = cellCenters
                    .filterKeys { it !in puzzle.givens }
                    .minByOrNull { (_, center) -> (center - dropPosition).getDistance() }
                    ?.takeIf { (_, center) -> (center - dropPosition).getDistance() < 140f }
                    ?.key
                if (target != null) entries = entries + (target to value)
            })
        }
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { grid(Modifier.fillMaxSize()) }
                bank(Modifier.widthIn(max = 156.dp).fillMaxHeight())
            }
        } else {
            grid(Modifier.fillMaxWidth().aspectRatio(1f))
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
    modifier: Modifier = Modifier,
    onCellPositioned: (Pair<Int, Int>, Offset) -> Unit,
    onCellClicked: (Pair<Int, Int>) -> Unit
) {
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
                                        isGiven -> StarWhite
                                        value == null -> StarWhite
                                        correct -> PlanetGreen
                                        else -> PlanetRed
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isGiven || value != null) FontWeight.Black else FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
            SudokuGridLines(puzzle = puzzle, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun SudokuGridLines(puzzle: SudokuPuzzle, modifier: Modifier = Modifier) {
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
    }
}

@Composable
private fun NumberBank(values: List<Int>, maxValue: Int, modifier: Modifier = Modifier, onValueDropped: (Int, Offset) -> Unit) {
    val isPortrait = LocalConfiguration.current.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val rows = if (isPortrait) values.chunked(5) else values.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        rows.forEach { rowValues ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                rowValues.forEach { value ->
                    DraggableSudokuValue(value = value, enabled = value <= maxValue, onDropped = onValueDropped)
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
