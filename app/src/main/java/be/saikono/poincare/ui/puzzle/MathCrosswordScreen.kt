package be.saikono.poincare.ui.puzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import be.saikono.poincare.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun MathCrosswordGame(
    initialStars: Int,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stars = initialStars.coerceIn(1, 3)
    val seed = rememberSaveable(stars) { Random.nextInt() }
    val puzzle = remember(stars, seed) { MathCrosswordGenerator.generate(stars, seed) }
    var entries by rememberSaveable(seed, saver = crosswordEntriesSaver) { mutableStateOf<Map<Pair<Int, Int>, Int>>(emptyMap()) }
    var entryTiles by rememberSaveable(seed, saver = crosswordTileEntriesSaver) { mutableStateOf<Map<Pair<Int, Int>, MathCrosswordTile>>(emptyMap()) }
    var cellCenters by remember(puzzle) { mutableStateOf<Map<Pair<Int, Int>, Offset>>(emptyMap()) }
    var elapsedSeconds by rememberSaveable(seed) { mutableIntStateOf(0) }
    val complete = puzzle.missingNumberCells.all { it in entries } && puzzle.equations.all { it.isCorrect(puzzle, entries) }

    LaunchedEffect(puzzle, complete) {
        while (!complete) {
            delay(1000)
            elapsedSeconds += 1
        }
    }
    LaunchedEffect(complete) { if (complete) onComplete() }
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(crosswordTimer(elapsedSeconds), color = PlanetGold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        val grid: @Composable (Modifier) -> Unit = { gridModifier ->
            CrosswordGrid(puzzle, entries, modifier = gridModifier, onCellPositioned = { cell, center ->
                cellCenters = cellCenters + (cell to center)
            }, onCellClicked = { cell ->
                if (cell !in puzzle.fixedNumberCells) {
                    entries = entries - cell
                    entryTiles = entryTiles - cell
                }
            }, onPlacedValueDropped = { sourceCell, value, dropPosition ->
                val target = cellCenters
                    .filterKeys { cell -> cell in puzzle.missingNumberCells && cell != sourceCell }
                    .minByOrNull { (_, center) -> (center - dropPosition).getDistance() }
                    ?.takeIf { (_, center) -> (center - dropPosition).getDistance() < 140f }
                    ?.key
                entryTiles = if (target != null) {
                    val sourceTile = entryTiles[sourceCell] ?: MathCrosswordTile(sourceCell, value)
                    entryTiles - sourceCell + (target to sourceTile)
                } else {
                    entryTiles - sourceCell
                }
                entries = if (target != null) entries - sourceCell + (target to value) else entries - sourceCell
            })
        }
        val bank: @Composable (Modifier) -> Unit = { bankModifier ->
            CrosswordBank(puzzle.bankTiles, entryTiles.values.map { it.cell }.toSet(), modifier = bankModifier, onTileDropped = { tile, dropPosition ->
                val target = cellCenters
                    .filterKeys { cell -> cell in puzzle.missingNumberCells }
                    .minByOrNull { (_, center) -> (center - dropPosition).getDistance() }
                    ?.takeIf { (_, center) -> (center - dropPosition).getDistance() < 140f }
                    ?.key
                if (target != null) {
                    entryTiles = entryTiles + (target to tile)
                    entries = entries + (target to tile.value)
                }
            })
        }
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { grid(Modifier.fillMaxSize()) }
                bank(Modifier.widthIn(max = 164.dp).fillMaxHeight())
            }
        } else {
            grid(Modifier.fillMaxWidth().weight(1f))
            bank(Modifier.fillMaxWidth())
        }
        if (complete) {
            Text("Rekenkruiswoord opgelost!", color = PlanetGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CrosswordGrid(
    puzzle: MathCrosswordPuzzle,
    entries: Map<Pair<Int, Int>, Int>,
    modifier: Modifier = Modifier,
    onCellPositioned: (Pair<Int, Int>, Offset) -> Unit,
    onCellClicked: (Pair<Int, Int>) -> Unit,
    onPlacedValueDropped: (Pair<Int, Int>, Int, Offset) -> Unit
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val occupiedRows = puzzle.cells.keys.map { it.first }
        val occupiedColumns = puzzle.cells.keys.map { it.second }
        val minRow = occupiedRows.minOrNull() ?: 0
        val maxRow = occupiedRows.maxOrNull() ?: puzzle.rowCount - 1
        val minColumn = occupiedColumns.minOrNull() ?: 0
        val maxColumn = occupiedColumns.maxOrNull() ?: puzzle.columnCount - 1
        val visibleRowCount = maxRow - minRow + 1
        val visibleColumnCount = maxColumn - minColumn + 1
        val rawCellSize = minOf(maxWidth / visibleColumnCount, maxHeight / visibleRowCount)
        val cellSize = rawCellSize.coerceAtMost(48.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            for (row in minRow..maxRow) {
                Row {
                    for (col in minColumn..maxColumn) {
                        val key = row to col
                        val cell = puzzle.cells[key]
                        if (cell == null) {
                            Spacer(modifier = Modifier.size(cellSize))
                        } else {
                            CrosswordCell(key, cell, puzzle, entries, cellSize, onCellPositioned, onCellClicked, onPlacedValueDropped)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrosswordCell(
    key: Pair<Int, Int>,
    cell: MathCrosswordCell,
    puzzle: MathCrosswordPuzzle,
    entries: Map<Pair<Int, Int>, Int>,
    cellSize: androidx.compose.ui.unit.Dp,
    onCellPositioned: (Pair<Int, Int>, Offset) -> Unit,
    onCellClicked: (Pair<Int, Int>) -> Unit,
    onPlacedValueDropped: (Pair<Int, Int>, Int, Offset) -> Unit
) {
    val isFixed = key in puzzle.fixedNumberCells
    val expected = (cell as? MathCrosswordCell.Number)?.value
    val shown = if (isFixed) expected else entries[key]
    val completedEquations = puzzle.equations.filter { equation -> key in equation.numberCells && equation.numberCells.all { it in puzzle.fixedNumberCells || entries[it] != null } }
    val hasCorrectLine = completedEquations.isNotEmpty() && completedEquations.all { it.isCorrect(puzzle, entries) }
    val valueIsAllowed = shown == null || completedEquations.isEmpty() || hasCorrectLine
    var offsetX by remember(key) { mutableFloatStateOf(0f) }
    var offsetY by remember(key) { mutableFloatStateOf(0f) }
    var startPosition by remember(key) { mutableStateOf(Offset.Zero) }
    var dragging by remember(key) { mutableStateOf(false) }
    val textColor = when {
        cell is MathCrosswordCell.Sign -> PlanetGold
        isFixed -> StarWhite
        shown == null -> StarWhite.copy(alpha = 0.35f)
        !valueIsAllowed -> Color(0xFF8B0000)
        hasCorrectLine -> Color(0xFF006400)
        shown == expected -> Color(0xFF006400)
        else -> DeepSpaceBlue
    }
    Box(
        modifier = Modifier
            .size(cellSize)
            .padding(2.dp)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                val center = position + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                onCellPositioned(key, center)
                if (!dragging) startPosition = center
            }
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .background(
                when {
                    isFixed -> Color.White.copy(alpha = 0.16f)
                    shown != null -> Color.White.copy(alpha = 0.20f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                RoundedCornerShape(6.dp)
            )
            .clickable(enabled = cell is MathCrosswordCell.Number && !isFixed && shown != null) { onCellClicked(key) }
            .pointerInput(isFixed, shown) {
                val value = shown ?: return@pointerInput
                if (isFixed) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        onPlacedValueDropped(key, value, startPosition + Offset(offsetX, offsetY))
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
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (cell) {
                is MathCrosswordCell.Number -> shown?.toString() ?: "?"
                is MathCrosswordCell.Sign -> cell.text
            },
            color = textColor,
            style = if (shown?.toString()?.length ?: 0 >= 3) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (isFixed || cell is MathCrosswordCell.Sign || shown != null) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun CrosswordBank(tiles: List<MathCrosswordTile>, usedCells: Set<Pair<Int, Int>>, modifier: Modifier = Modifier, onTileDropped: (MathCrosswordTile, Offset) -> Unit) {
    val chunks = tiles.withIndex().chunked(if (LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 3 else 6)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        chunks.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                row.forEach { indexed ->
                    val index = indexed.index
                    val tile = indexed.value
                    DraggableCrosswordValue(tile = tile, enabled = tile.cell !in usedCells, key = index, onDropped = onTileDropped)
                }
            }
        }
    }
}

@Composable
private fun DraggableCrosswordValue(tile: MathCrosswordTile, enabled: Boolean, key: Int, onDropped: (MathCrosswordTile, Offset) -> Unit) {
    var offsetX by remember(key) { mutableFloatStateOf(0f) }
    var offsetY by remember(key) { mutableFloatStateOf(0f) }
    var startPosition by remember(key) { mutableStateOf(Offset.Zero) }
    var dragging by remember(key) { mutableStateOf(false) }
    Surface(
        color = if (enabled) PlanetGold.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
        contentColor = if (enabled) DeepSpaceBlue else StarWhite.copy(alpha = 0.35f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .onGloballyPositioned { if (!dragging) startPosition = it.positionInWindow() + Offset(it.size.width / 2f, it.size.height / 2f) }
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(40.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        onDropped(tile, startPosition + Offset(offsetX, offsetY))
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
    ) { Box(contentAlignment = Alignment.Center) { Text(tile.value.toString(), style = if (tile.value.toString().length >= 3) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) } }
}

private fun MathCrosswordEquation.isCorrect(puzzle: MathCrosswordPuzzle, entries: Map<Pair<Int, Int>, Int>): Boolean {
    val values = cells.map { cell ->
        when (val content = puzzle.cells[cell]) {
            is MathCrosswordCell.Number -> if (cell in puzzle.fixedNumberCells) content.value.toString() else entries[cell]?.toString() ?: return false
            is MathCrosswordCell.Sign -> content.text
            null -> return false
        }
    }
    val equalsIndex = values.indexOf("=")
    if (equalsIndex <= 0) return false
    return evaluate(values.take(equalsIndex)) == values.drop(equalsIndex + 1).singleOrNull()?.toIntOrNull()
}

private fun evaluate(parts: List<String>): Int? {
    var result = parts.firstOrNull()?.toIntOrNull() ?: return null
    var index = 1
    while (index < parts.size - 1) {
        val next = parts[index + 1].toIntOrNull() ?: return null
        result = when (parts[index]) {
            "+" -> result + next
            "-" -> result - next
            "*" -> result * next
            "/" -> if (next != 0 && result % next == 0) result / next else return null
            else -> return null
        }
        index += 2
    }
    return result
}

private fun crosswordTimer(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private val crosswordEntriesSaver = listSaver<MutableState<Map<Pair<Int, Int>, Int>>, String>(
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

private val crosswordTileEntriesSaver = listSaver<MutableState<Map<Pair<Int, Int>, MathCrosswordTile>>, String>(
    save = { state ->
        state.value.map { (targetCell, tile) ->
            "${targetCell.first},${targetCell.second},${tile.cell.first},${tile.cell.second},${tile.value}"
        }
    },
    restore = { saved ->
        mutableStateOf(saved.mapNotNull { item ->
            val parts = item.split(",")
            if (parts.size != 5) return@mapNotNull null
            val targetRow = parts[0].toIntOrNull() ?: return@mapNotNull null
            val targetCol = parts[1].toIntOrNull() ?: return@mapNotNull null
            val sourceRow = parts[2].toIntOrNull() ?: return@mapNotNull null
            val sourceCol = parts[3].toIntOrNull() ?: return@mapNotNull null
            val value = parts[4].toIntOrNull() ?: return@mapNotNull null
            (targetRow to targetCol) to MathCrosswordTile(sourceRow to sourceCol, value)
        }.toMap())
    }
)
