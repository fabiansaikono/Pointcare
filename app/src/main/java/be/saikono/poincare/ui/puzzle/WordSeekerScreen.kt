package be.saikono.poincare.ui.puzzle

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
    grade: Int = 1,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stars = initialStars.coerceIn(1, 3)
    val gradeLevel = grade.coerceIn(1, 6)
    val seed = rememberSaveable(stars, exerciseLanguage, gradeLevel) { Random.nextInt() }
    val puzzle = remember(stars, exerciseLanguage, gradeLevel, seed) {
        WordSeekerGenerator.generate(stars = stars, locale = exerciseLanguage, grade = gradeLevel, seed = seed)
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
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 8.dp, Alignment.CenterVertically)
    ) {
        if (!isLandscape) {
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
                        .weight(1.15f)
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
                        .widthIn(min = 128.dp, max = 170.dp)
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
            modifier = Modifier.padding(if (isLandscape) 9.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp)
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
    fun generate(stars: Int, locale: String, grade: Int, seed: Int): WordSeekerPuzzle {
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
        val candidates = wordBank(locale, grade)
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

    private fun wordBank(locale: String, grade: Int): List<String> {
        val level = grade.coerceIn(1, 6)
        return when (normalizeWordSeekerLocale(locale)) {
            "fr-BE" -> when (level) {
                1 -> listOf("SOLEIL", "LUNE", "ETOILE", "TERRE", "MER", "RUE", "VILLE", "ARBRE", "CHAT", "CHIEN", "NUAGE", "PLUIE")
                2 -> listOf("PLANETE", "FUSEE", "FORET", "RIVIERE", "MONT", "OCEAN", "HIVER", "ETE", "ANIMAL", "FLEUR", "CARTE", "PAYS")
                3 -> listOf("EUROPE", "AFRIQUE", "ASIE", "BELGIQUE", "FRANCE", "ITALIE", "CAPITALE", "VOLCAN", "DESERT", "CLIMAT", "ENERGIE", "NATURE")
                4 -> listOf("CONTINENT", "EQUATEUR", "ORIGINE", "HISTOIRE", "CULTURE", "METEO", "ESPECE", "PLANETE", "SATELLITE", "ORBITE", "BASALTE", "GLACIER")
                5 -> listOf("ATMOSPHERE", "ECOSYSTEME", "BIODIVERS", "LATITUDE", "LONGITUDE", "MIGRATION", "CIVILISER", "DEMOCRATIE", "RESSOURCE", "GRAVITE", "PRESSION", "EROSION")
                else -> listOf("BIOSPHERE", "HEMISPHERE", "TECTONIQUE", "CIVILISATION", "INDUSTRIE", "EVOLUTION", "EXPEDITION", "REVOLUTION", "CONSERVATION", "URBANISME", "NUTRIMENT", "MICROSCOPE")
            }
            "en-US" -> when (level) {
                1 -> listOf("SUN", "MOON", "STAR", "EARTH", "SEA", "ROAD", "CITY", "TREE", "CAT", "DOG", "CLOUD", "RAIN")
                2 -> listOf("PLANET", "ROCKET", "FOREST", "RIVER", "MOUNT", "OCEAN", "WINTER", "SUMMER", "ANIMAL", "FLOWER", "MAP", "COUNTRY")
                3 -> listOf("EUROPE", "AFRICA", "ASIA", "BELGIUM", "FRANCE", "ITALY", "CAPITAL", "VOLCANO", "DESERT", "CLIMATE", "ENERGY", "NATURE")
                4 -> listOf("CONTINENT", "EQUATOR", "ORIGIN", "HISTORY", "CULTURE", "WEATHER", "SPECIES", "PLANET", "SATELLITE", "ORBIT", "BASALT", "GLACIER")
                5 -> listOf("ATMOSPHERE", "ECOSYSTEM", "BIODIVERS", "LATITUDE", "LONGITUDE", "MIGRATION", "CIVILIZED", "DEMOCRACY", "RESOURCE", "GRAVITY", "PRESSURE", "EROSION")
                else -> listOf("BIOSPHERE", "HEMISPHERE", "TECTONIC", "CIVILIZATION", "INDUSTRY", "EVOLUTION", "EXPEDITION", "REVOLUTION", "CONSERVE", "URBANISM", "NUTRIENT", "MICROSCOPE")
            }
            else -> when (level) {
                1 -> listOf("ZON", "MAAN", "STER", "AARDE", "ZEE", "WEG", "STAD", "BOOM", "KAT", "HOND", "WOLK", "REGEN")
                2 -> listOf("PLANEET", "RAKET", "BOS", "RIVIER", "BERG", "OCEAAN", "WINTER", "ZOMER", "DIER", "BLOEM", "KAART", "LAND")
                3 -> listOf("EUROPA", "AFRIKA", "AZIE", "BELGIE", "FRANKRIJK", "ITALIE", "HOOFDSTAD", "VULKAAN", "WOESTIJN", "KLIMAAT", "ENERGIE", "NATUUR")
                4 -> listOf("CONTINENT", "EVENAAR", "OORSPRONG", "HISTORIE", "CULTUUR", "WEER", "SOORTEN", "PLANEET", "SATELLIET", "ORBIT", "BASALT", "GLETSJER")
                5 -> listOf("ATMOSFEER", "ECOSYSTEEM", "BIODIVERS", "BREEDTE", "LENGTE", "MIGRATIE", "BESCHAVING", "DEMOCRATIE", "GRONDSTOF", "ZWAARTE", "LUCHTDRUK", "EROSIE")
                else -> listOf("BIOSFEER", "HALFROND", "TEKTONIEK", "BESCHAVING", "INDUSTRIE", "EVOLUTIE", "EXPEDITIE", "REVOLUTIE", "BEHOUD", "URBANISME", "VOEDINGSSTOF", "MICROSCOOP")
            }
        }
    }

    private fun normalizeWordSeekerLocale(locale: String): String = when (locale.replace('_', '-')) {
        "fr", "fr-BE", "fr-FR" -> "fr-BE"
        "en", "en-US", "en-GB" -> "en-US"
        else -> "nl-BE"
    }

    private const val EMPTY = '\u0000'
}
