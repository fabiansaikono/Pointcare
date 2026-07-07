from pathlib import Path
from datetime import datetime
import re
import sys

WORD_SEEKER_SCREEN = r'''package be.saikono.poincare.ui.puzzle

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.NebulaPurple
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.StarWhite
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

@Composable
fun WordSeekerGame(
    initialStars: Int,
    exerciseLanguage: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stars = initialStars.coerceIn(1, 3)
    val seed = rememberSaveable(stars, exerciseLanguage) { Random.nextInt() }
    val puzzle = remember(stars, exerciseLanguage, seed) {
        WordSeekerGenerator.generate(stars = stars, locale = exerciseLanguage, seed = seed)
    }

    var foundWordsList by rememberSaveable(seed) { mutableStateOf(emptyList<String>()) }
    val foundWords = foundWordsList.toSet()
    var currentPath by remember { mutableStateOf<List<WordCell>>(emptyList()) }
    var elapsedSeconds by rememberSaveable(seed) { mutableIntStateOf(0) }
    var completeEventSent by rememberSaveable(seed) { mutableStateOf(false) }

    val isComplete = foundWords.size == puzzle.words.size && puzzle.words.isNotEmpty()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(isComplete) {
        if (isComplete && !completeEventSent) {
            completeEventSent = true
            onComplete()
        }
    }

    LaunchedEffect(seed, isComplete) {
        while (!isComplete) {
            delay(1000)
            elapsedSeconds += 1
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = wordSeekerInstruction(exerciseLanguage),
                color = StarWhite.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatWordSeekerTimer(elapsedSeconds),
                color = PlanetGold,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }

        val grid: @Composable (Modifier) -> Unit = { gridModifier ->
            WordSeekerGrid(
                puzzle = puzzle,
                foundWords = foundWords,
                currentPath = currentPath,
                onPathChanged = { currentPath = it },
                onSelectionFinished = { path ->
                    val found = puzzle.match(path)
                    if (found != null && found !in foundWords) {
                        foundWordsList = (foundWordsList + found).distinct()
                    }
                    currentPath = emptyList()
                },
                modifier = gridModifier
            )
        }

        val wordList: @Composable (Modifier) -> Unit = { listModifier ->
            WordSeekerWordList(
                words = puzzle.words,
                foundWords = foundWords,
                isLandscape = isLandscape,
                modifier = listModifier
            )
        }

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    grid(
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                    )
                }
                wordList(
                    Modifier
                        .widthIn(min = 160.dp, max = 230.dp)
                        .fillMaxHeight()
                )
            }
        } else {
            grid(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
            wordList(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
            )
        }

        if (isComplete) {
            Text(
                text = wordSeekerCompleteText(exerciseLanguage),
                color = PlanetGold,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WordSeekerGrid(
    puzzle: WordSeekerPuzzle,
    foundWords: Set<String>,
    currentPath: List<WordCell>,
    onPathChanged: (List<WordCell>) -> Unit,
    onSelectionFinished: (List<WordCell>) -> Unit,
    modifier: Modifier = Modifier
) {
    var gridPixelSize by remember { mutableStateOf(IntSize.Zero) }
    val foundCells = remember(foundWords, puzzle) { puzzle.cellsFor(foundWords) }
    val currentCells = remember(currentPath) { currentPath.toSet() }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(DeepSpaceBlue.copy(alpha = 0.72f))
            .border(1.dp, PlanetBlue.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .onSizeChanged { gridPixelSize = it }
            .pointerInput(puzzle, foundWords, gridPixelSize) {
                var dragStartCell: WordCell? = null
                var activePath: List<WordCell> = emptyList()
                detectDragGestures(
                    onDragStart = { offset ->
                        val start = cellAt(offset, gridPixelSize, puzzle.size)
                        dragStartCell = start
                        activePath = start?.let { listOf(it) }.orEmpty()
                        onPathChanged(activePath)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val start = dragStartCell
                        val end = cellAt(change.position, gridPixelSize, puzzle.size)
                        activePath = if (start != null && end != null) {
                            buildStraightPath(start, end, puzzle.size)
                        } else if (end != null) {
                            listOf(end)
                        } else {
                            emptyList()
                        }
                        onPathChanged(activePath)
                    },
                    onDragEnd = {
                        onSelectionFinished(activePath)
                        dragStartCell = null
                        activePath = emptyList()
                    },
                    onDragCancel = {
                        dragStartCell = null
                        activePath = emptyList()
                        onPathChanged(emptyList())
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val placedWords = puzzle.placements.filter { it.word in foundWords }
            placedWords.forEach { placement ->
                val cells = placement.cells()
                val start = centerFor(cells.first(), gridPixelSize, puzzle.size)
                val end = centerFor(cells.last(), gridPixelSize, puzzle.size)
                drawLine(
                    color = PlanetGold.copy(alpha = 0.58f),
                    start = start,
                    end = end,
                    strokeWidth = size.minDimension / puzzle.size * 0.56f,
                    cap = StrokeCap.Round
                )
            }
            if (currentPath.size >= 2) {
                drawLine(
                    color = PlanetBlue.copy(alpha = 0.72f),
                    start = centerFor(currentPath.first(), gridPixelSize, puzzle.size),
                    end = centerFor(currentPath.last(), gridPixelSize, puzzle.size),
                    strokeWidth = size.minDimension / puzzle.size * 0.48f,
                    cap = StrokeCap.Round
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            puzzle.grid.forEachIndexed { rowIndex, row ->
                Row(modifier = Modifier.weight(1f)) {
                    row.forEachIndexed { colIndex, letter ->
                        val cell = WordCell(rowIndex, colIndex)
                        val isFound = cell in foundCells
                        val isCurrent = cell in currentCells
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    when {
                                        isCurrent -> PlanetBlue.copy(alpha = 0.34f)
                                        isFound -> PlanetGold.copy(alpha = 0.14f)
                                        else -> Color.White.copy(alpha = 0.045f)
                                    }
                                )
                                .border(0.5.dp, Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter.toString(),
                                color = if (isFound) StarWhite else StarWhite.copy(alpha = 0.94f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordSeekerWordList(
    words: List<String>,
    foundWords: Set<String>,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = NebulaPurple.copy(alpha = 0.42f),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val chunkSize = if (isLandscape) 1 else 2
            words.chunked(chunkSize).forEach { rowWords ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowWords.forEach { word ->
                        val found = word in foundWords
                        Text(
                            text = word,
                            modifier = Modifier.weight(1f),
                            color = if (found) Color.Gray.copy(alpha = 0.72f) else PlanetGold,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (found) TextDecoration.LineThrough else TextDecoration.None,
                            textAlign = if (isLandscape) TextAlign.Start else TextAlign.Center
                        )
                    }
                    if (!isLandscape && rowWords.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun cellAt(offset: Offset, gridPixelSize: IntSize, gridSize: Int): WordCell? {
    if (gridPixelSize.width <= 0 || gridPixelSize.height <= 0) return null
    if (offset.x < 0f || offset.y < 0f || offset.x >= gridPixelSize.width || offset.y >= gridPixelSize.height) return null
    val col = (offset.x / (gridPixelSize.width.toFloat() / gridSize)).toInt().coerceIn(0, gridSize - 1)
    val row = (offset.y / (gridPixelSize.height.toFloat() / gridSize)).toInt().coerceIn(0, gridSize - 1)
    return WordCell(row, col)
}

private fun centerFor(cell: WordCell, gridPixelSize: IntSize, gridSize: Int): Offset {
    if (gridPixelSize.width <= 0 || gridPixelSize.height <= 0) return Offset.Zero
    val cellWidth = gridPixelSize.width.toFloat() / gridSize
    val cellHeight = gridPixelSize.height.toFloat() / gridSize
    return Offset(
        x = (cell.col + 0.5f) * cellWidth,
        y = (cell.row + 0.5f) * cellHeight
    )
}

private fun buildStraightPath(start: WordCell, end: WordCell, gridSize: Int): List<WordCell> {
    val rowDiff = end.row - start.row
    val colDiff = end.col - start.col
    val isStraight = rowDiff == 0 || colDiff == 0 || abs(rowDiff) == abs(colDiff)
    if (!isStraight) return listOf(start, end)

    val rowStep = rowDiff.compareTo(0)
    val colStep = colDiff.compareTo(0)
    val steps = max(abs(rowDiff), abs(colDiff))
    return (0..steps).mapNotNull { index ->
        val row = start.row + rowStep * index
        val col = start.col + colStep * index
        if (row in 0 until gridSize && col in 0 until gridSize) WordCell(row, col) else null
    }
}

private fun formatWordSeekerTimer(seconds: Int): String {
    val minutes = seconds / 60
    val remaining = seconds % 60
    return "%d:%02d".format(minutes, remaining)
}

private fun wordSeekerInstruction(locale: String): String = when (locale) {
    "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> "Commence par la première lettre et termine par la dernière."
    "en", "en-US", "en_US", "en-GB", "en_GB" -> "Start with the first letter and finish on the last."
    else -> "Start bij de eerste letter en eindig op de laatste."
}

private fun wordSeekerCompleteText(locale: String): String = when (locale) {
    "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> "Tous les mots sont trouvés!"
    "en", "en-US", "en_US", "en-GB", "en_GB" -> "All words found!"
    else -> "Alle woorden gevonden!"
}

private data class WordCell(val row: Int, val col: Int)

private data class WordPlacement(
    val word: String,
    val startRow: Int,
    val startCol: Int,
    val rowStep: Int,
    val colStep: Int
) {
    fun cells(): List<WordCell> = word.indices.map { index ->
        WordCell(startRow + rowStep * index, startCol + colStep * index)
    }
}

private data class WordSeekerPuzzle(
    val size: Int,
    val words: List<String>,
    val grid: List<List<Char>>,
    val placements: List<WordPlacement>
) {
    fun cellsFor(foundWords: Set<String>): Set<WordCell> = placements
        .filter { it.word in foundWords }
        .flatMap { it.cells() }
        .toSet()

    fun match(path: List<WordCell>): String? {
        if (path.size < 2) return null
        val candidate = path.joinToString(separator = "") { cell -> grid[cell.row][cell.col].toString() }
        return words.firstOrNull { it == candidate }
    }
}

private object WordSeekerGenerator {
    fun generate(stars: Int, locale: String, seed: Int): WordSeekerPuzzle {
        val random = Random(seed)
        val size = when (stars.coerceIn(1, 3)) {
            1 -> 8
            2 -> 10
            else -> 12
        }
        val targetWordCount = when (stars.coerceIn(1, 3)) {
            1 -> 5
            2 -> 7
            else -> 9
        }
        val candidates = wordBank(locale)
            .filter { it.length <= size }
            .shuffled(random)
            .take(targetWordCount + 5)
            .sortedByDescending { it.length }

        val grid = Array(size) { CharArray(size) { EMPTY } }
        val placements = mutableListOf<WordPlacement>()
        val directions = directionsFor(stars)

        candidates.forEach { word ->
            if (placements.size >= targetWordCount) return@forEach
            val placement = tryPlaceWord(word, grid, directions, random)
            if (placement != null) placements += placement
        }

        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        for (row in 0 until size) {
            for (col in 0 until size) {
                if (grid[row][col] == EMPTY) grid[row][col] = alphabet[random.nextInt(alphabet.length)]
            }
        }

        val finalWords = placements.map { it.word }.sorted()
        return WordSeekerPuzzle(
            size = size,
            words = finalWords,
            grid = grid.map { it.toList() },
            placements = placements
        )
    }

    private fun tryPlaceWord(
        word: String,
        grid: Array<CharArray>,
        directions: List<Pair<Int, Int>>,
        random: Random
    ): WordPlacement? {
        val size = grid.size
        repeat(600) {
            val direction = directions[random.nextInt(directions.size)]
            val rowStep = direction.first
            val colStep = direction.second
            val minRow = if (rowStep < 0) word.length - 1 else 0
            val maxRow = if (rowStep > 0) size - word.length else size - 1
            val minCol = if (colStep < 0) word.length - 1 else 0
            val maxCol = if (colStep > 0) size - word.length else size - 1
            if (minRow > maxRow || minCol > maxCol) return@repeat

            val startRow = random.nextInt(minRow, maxRow + 1)
            val startCol = random.nextInt(minCol, maxCol + 1)
            val fits = word.indices.all { index ->
                val row = startRow + rowStep * index
                val col = startCol + colStep * index
                grid[row][col] == EMPTY || grid[row][col] == word[index]
            }
            if (fits) {
                word.indices.forEach { index ->
                    val row = startRow + rowStep * index
                    val col = startCol + colStep * index
                    grid[row][col] = word[index]
                }
                return WordPlacement(word, startRow, startCol, rowStep, colStep)
            }
        }
        return null
    }

    private fun directionsFor(stars: Int): List<Pair<Int, Int>> = when (stars.coerceIn(1, 3)) {
        1 -> listOf(0 to 1, 1 to 0)
        2 -> listOf(0 to 1, 1 to 0, 1 to 1, -1 to 1)
        else -> listOf(0 to 1, 1 to 0, 1 to 1, -1 to 1, 0 to -1, -1 to 0, 1 to -1, -1 to -1)
    }

    private fun wordBank(locale: String): List<String> = when (locale) {
        "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> listOf(
            "ETOILE", "LUNE", "SOLEIL", "PLANETE", "FUSEE", "COMETE", "GALAXIE", "TERRE", "SATURNE", "ORBITE", "NEBULEUSE", "MARS"
        )
        "en", "en-US", "en_US", "en-GB", "en_GB" -> listOf(
            "STAR", "MOON", "SUN", "PLANET", "ROCKET", "COMET", "GALAXY", "EARTH", "SATURN", "ORBIT", "NEBULA", "MARS"
        )
        else -> listOf(
            "STER", "MAAN", "ZON", "PLANEET", "RAKET", "KOMEET", "MELKWEG", "AARDE", "SATURNUS", "ORBIT", "GALAXIE", "MARS"
        )
    }

    private const val EMPTY = '\u0000'
}
'''

WORD_SEEKER_ICON = r'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp"
    android:height="96dp"
    android:viewportWidth="96"
    android:viewportHeight="96">
    <path
        android:fillColor="#050119"
        android:pathData="M12,12 L84,12 L92,20 L92,76 L84,84 L12,84 L4,76 L4,20 Z" />
    <path
        android:fillColor="#234F65"
        android:fillAlpha="0.72"
        android:pathData="M18,20 L78,20 L82,24 L82,72 L78,76 L18,76 L14,72 L14,24 Z" />
    <path
        android:strokeColor="#F6D64A"
        android:strokeWidth="7"
        android:strokeLineCap="round"
        android:fillColor="@android:color/transparent"
        android:pathData="M24,30 L70,66" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M24,27h8v8h-8zM42,27h8v8h-8zM60,27h8v8h-8zM24,45h8v8h-8zM42,45h8v8h-8zM60,45h8v8h-8zM24,63h8v8h-8zM42,63h8v8h-8zM60,63h8v8h-8z" />
    <path
        android:fillColor="#F6D64A"
        android:pathData="M25,28h6v6h-6zM43,46h6v6h-6zM61,64h6v6h-6z" />
</vector>
'''


def find_root() -> Path:
    cwd = Path.cwd()
    candidates = [cwd, Path(__file__).resolve().parent]
    candidates.extend(cwd.parents)
    for candidate in candidates:
        if (candidate / "app" / "src" / "main").exists():
            return candidate
    raise SystemExit("Could not find the Android project root. Run this from C:\\AndroidApps\\Poincare or from the folder that contains app/src/main.")


def backup(path: Path) -> None:
    if path.exists():
        suffix = datetime.now().strftime("%Y%m%d_%H%M%S")
        backup_path = path.with_suffix(path.suffix + f".bak_word_seeker_{suffix}")
        backup_path.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")


def write_file(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == content:
        return
    backup(path)
    path.write_text(content, encoding="utf-8")
    print(f"Wrote {path}")


def patch_file(path: Path, patcher, label: str) -> None:
    if not path.exists():
        raise SystemExit(f"Missing required file: {path}")
    original = path.read_text(encoding="utf-8")
    updated = patcher(original)
    if updated == original:
        print(f"No changes needed in {path.name} ({label})")
        return
    backup(path)
    path.write_text(updated, encoding="utf-8")
    print(f"Patched {path.name} ({label})")


def patch_content_models(text: str) -> str:
    if "WORD_SEEKER" in text:
        return text
    return re.sub(r"(SUDOKU_GRID\s*,\s*)(NUMBER_WALL)", r"\1\n\n WORD_SEEKER,\n\n \2", text, count=1)


def patch_models(text: str) -> str:
    if "InputMode.WORD_SEEKER" in text:
        return text
    return text.replace(
        "InputMode.MATH_CROSSWORD, InputMode.SUDOKU_GRID -> 18",
        "InputMode.MATH_CROSSWORD, InputMode.SUDOKU_GRID, InputMode.WORD_SEEKER -> 18"
    )


def patch_puzzle_screen(text: str) -> str:
    updated = text
    updated = re.sub(
        r'setOf\("sudoku",\s*"math-crosswords"\)',
        'setOf("sudoku", "math-crosswords", "word-seeker")',
        updated,
        count=1
    )
    if 'subcategoryId == "word-seeker"' not in updated:
        branch = '''} else if (subcategoryId == "word-seeker") {
 WordSeekerGame(
 initialStars = puzzle.data["difficultyStars"]?.toIntOrNull() ?: 1,
 exerciseLanguage = uiState.exerciseLanguage,
 onComplete = onGeneratedPuzzleComplete,
 modifier = Modifier.weight(1f)
 )
 } else if (subcategoryId == "math-crosswords") {'''
        updated = updated.replace('} else if (subcategoryId == "math-crosswords") {', branch, 1)
    return updated


def patch_puzzle_view_model(text: str) -> str:
    if '"word-seeker" -> ExerciseContent' in text:
        return text
    start = text.find("private fun generatedPuzzleExercise")
    if start == -1:
        raise SystemExit("Could not find generatedPuzzleExercise in PuzzleViewModel.kt")
    marker = "else -> null"
    insert_at = text.find(marker, start)
    if insert_at == -1:
        raise SystemExit("Could not find else -> null in generatedPuzzleExercise")
    branch = '''"word-seeker" -> ExerciseContent(
 id = "generated-word-seeker-${level.name.lowercase()}-$stars",
 domain = LearningDomain.PUZZLE,
 skill = "word-seeker",
 section = ExerciseSection.PUZZLES,
 level = level,
 inputMode = InputMode.WORD_SEEKER,
 prompt = LocalizedText(nl = "Woordzoeker", en = "Word seeker", fr = "Cherche-mots"),
 data = mapOf(
 "mainCategory" to "logic_puzzles",
 "subcategoryId" to "word-seeker",
 "grade" to (level.toGrade()?.toString() ?: ""),
 "difficultyStars" to stars.toString(),
 "topic" to "Woordzoeker",
 "topic_en-US" to "Word seeker",
 "topic_fr-BE" to "Cherche-mots"
 ),
 answer = "completed"
 )
 '''
    return text[:insert_at] + branch + text[insert_at:]


def patch_subcategories(text: str) -> str:
    updated = text
    updated = updated.replace(
        '"math-crosswords" to "Rekenkruiswoord"',
        '"math-crosswords" to "Rekenkruiswoord", "word-seeker" to "Woordzoeker"'
    )
    updated = updated.replace(
        '"math-crosswords" to "Math crossword"',
        '"math-crosswords" to "Math crossword", "word-seeker" to "Word seeker"'
    )
    updated = updated.replace(
        '"math-crosswords" to "Mots croises de calcul"',
        '"math-crosswords" to "Mots croises de calcul", "word-seeker" to "Cherche-mots"'
    )
    if 'SubCategory("word-seeker"' not in updated:
        updated = updated.replace(
            'SubCategory("math-crosswords", "Rekenkruiswoord", R.drawable.ic_topic_math_crosswords, null),',
            'SubCategory("math-crosswords", "Rekenkruiswoord", R.drawable.ic_topic_math_crosswords, null),\n\n SubCategory("word-seeker", "Word seeker", R.drawable.ic_topic_word_seeker, null),',
            1
        )
    return updated


def main() -> None:
    root = find_root()
    src_main = root / "app" / "src" / "main"
    kotlin_root = src_main / "java" / "be" / "saikono" / "poincare"

    write_file(kotlin_root / "ui" / "puzzle" / "WordSeekerScreen.kt", WORD_SEEKER_SCREEN)
    write_file(src_main / "res" / "drawable" / "ic_topic_word_seeker.xml", WORD_SEEKER_ICON)

    patch_file(kotlin_root / "model" / "ContentModels.kt", patch_content_models, "InputMode.WORD_SEEKER")
    patch_file(kotlin_root / "model" / "Models.kt", patch_models, "mission points")
    patch_file(kotlin_root / "ui" / "puzzle" / "PuzzleScreen.kt", patch_puzzle_screen, "screen routing")
    patch_file(kotlin_root / "ui" / "puzzle" / "PuzzleViewModel.kt", patch_puzzle_view_model, "generated exercise")
    patch_file(kotlin_root / "ui" / "home" / "SubCategorySelectionScreen.kt", patch_subcategories, "puzzle tile + translations")

    print("\nWord seeker patch applied.")
    print("Build with: .\\gradlew.bat assembleDebug")


if __name__ == "__main__":
    main()
