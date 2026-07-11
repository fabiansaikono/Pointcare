package be.saikono.poincare.ui.puzzle.orbitbuilder

import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Generates a fresh, guaranteed-solvable Orbit Builder mission.
 *
 * The visible star tier stays 1..3. [generationDifficulty] may be 4 when
 * Hard Mode shifts a visible three-star mission one step higher.
 */
object OrbitBuilderLevels {
    /**
     * Compatibility samples for previews and older tests. Runtime missions are
     * generated through [random] and never selected from this list.
     */
    val all: List<OrbitBuilderLevel>
        get() = listOf(
            random(displayedStars = 1, generationDifficulty = 1, grade = 1),
            random(displayedStars = 2, generationDifficulty = 2, grade = 3),
            random(displayedStars = 3, generationDifficulty = 3, grade = 5)
        )

    fun random(
        displayedStars: Int,
        generationDifficulty: Int,
        grade: Int,
        random: Random = Random.Default
    ): OrbitBuilderLevel {
        val visible = displayedStars.coerceIn(1, 3)
        val effective = generationDifficulty.coerceIn(1, 4)
        val normalizedGrade = grade.coerceIn(1, 6)
        val manipulationCount = when (effective) {
            1 -> 1
            2 -> random.nextInt(1, 3)
            3 -> random.nextInt(3, 6)
            else -> random.nextInt(5, 8)
        }

        val rows = 8
        val columns = 6
        val solutionPath = buildSolutionPath(
            rows = rows,
            columns = columns,
            minimumInternalCells = manipulationCount,
            random = random
        )
        val start = solutionPath.first()
        val target = solutionPath.last()
        val gatePositions = solutionPath
            .drop(1)
            .dropLast(1)
            .shuffled(random)
            .take(manipulationCount)
            .toSet()

        var currentValue = startValue(normalizedGrade, effective, random)
        val initialValue = currentValue
        val fixedTiles = mutableMapOf<OrbitPosition, OrbitFixedTile>()

        solutionPath.drop(1).dropLast(1).forEach { position ->
            if (position in gatePositions) {
                val gate = nextGate(
                    value = currentValue,
                    grade = normalizedGrade,
                    generationDifficulty = effective,
                    random = random
                )
                currentValue = applyGuaranteed(currentValue, gate)
                fixedTiles[position] = OrbitFixedTile.Gate(gate)
            }
        }

        // Avoid a visually confusing no-change mission if operations happened
        // to cancel each other out.
        if (currentValue == initialValue) {
            val lastGatePosition = solutionPath.drop(1).dropLast(1).last { it in gatePositions }
            val replacement = OrbitMathGate(OrbitGateOperation.ADD, (effective + normalizedGrade).coerceAtLeast(2))
            val priorGates = solutionPath.drop(1).dropLast(1)
                .filter { it in gatePositions && it != lastGatePosition }
                .mapNotNull { (fixedTiles[it] as? OrbitFixedTile.Gate)?.gate }
            currentValue = priorGates.fold(initialValue, ::applyGuaranteed)
            fixedTiles[lastGatePosition] = OrbitFixedTile.Gate(replacement)
            currentValue = applyGuaranteed(currentValue, replacement)
        }

        val reserved = solutionPath.toSet()
        val freeCells = buildList {
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    val position = OrbitPosition(row, column)
                    if (position !in reserved) add(position)
                }
            }
        }.shuffled(random).toMutableList()

        val distractorGateCount = when (effective) {
            1 -> 1
            2 -> 2
            3 -> 4
            else -> 6
        }
        repeat(distractorGateCount.coerceAtMost(freeCells.size)) {
            val position = freeCells.removeAt(freeCells.lastIndex)
            val amount = when (effective) {
                1 -> random.nextInt(1, 5)
                2 -> random.nextInt(2, 10)
                3 -> random.nextInt(2, 13)
                else -> random.nextInt(3, 21)
            }
            val operation = when (effective) {
                1 -> listOf(OrbitGateOperation.ADD, OrbitGateOperation.SUBTRACT).random(random)
                2 -> listOf(OrbitGateOperation.ADD, OrbitGateOperation.SUBTRACT, OrbitGateOperation.MULTIPLY).random(random)
                else -> OrbitGateOperation.entries.random(random)
            }
            fixedTiles[position] = OrbitFixedTile.Gate(OrbitMathGate(operation, amount))
        }

        val asteroidCount = when (effective) {
            1 -> 2
            2 -> 4
            3 -> 7
            else -> 10
        }
        repeat(asteroidCount.coerceAtMost(freeCells.size)) {
            fixedTiles[freeCells.removeAt(freeCells.lastIndex)] = OrbitFixedTile.Asteroid
        }

        val randomSuffix = (
            System.nanoTime() xor random.nextLong()
        ).absoluteValue.toString(36)

        return OrbitBuilderLevel(
            id = "orbit-random-g$normalizedGrade-v$visible-e$effective-$randomSuffix",
            grade = normalizedGrade,
            difficultyStars = visible,
            generationDifficulty = effective,
            title = "Willekeurige missie",
            instruction = "Teken een route door de juiste waardepoorten en bereik de doelwaarde.",
            rows = rows,
            columns = columns,
            startRow = start.row,
            startColumn = start.column,
            initialDirection = directionBetween(solutionPath[0], solutionPath[1]),
            targetRow = target.row,
            targetColumn = target.column,
            startValue = initialValue,
            targetValue = currentValue,
            maxPlaceableArrows = rows * columns,
            optimalArrowCount = solutionPath.size - 1,
            availableArrows = OrbitDirection.entries,
            fixedTiles = fixedTiles
        )
    }

    private fun buildSolutionPath(
        rows: Int,
        columns: Int,
        minimumInternalCells: Int,
        random: Random
    ): List<OrbitPosition> {
        repeat(100) {
            val path = mutableListOf(OrbitPosition(rows - 1, random.nextInt(columns)))
            var row = rows - 1
            var column = path.first().column

            while (row > 0) {
                val mustLengthen = path.size - 1 < minimumInternalCells + 1
                if (mustLengthen || random.nextFloat() < 0.62f) {
                    val horizontalOptions = listOf(column - 1, column + 1)
                        .filter { it in 0 until columns }
                    if (horizontalOptions.isNotEmpty()) {
                        column = horizontalOptions.random(random)
                        path += OrbitPosition(row, column)
                    }
                }
                row -= 1
                path += OrbitPosition(row, column)
            }

            if (path.size - 2 >= minimumInternalCells) return path
        }

        // Deterministic fallback with more than enough internal cells.
        return buildList {
            var column = 0
            add(OrbitPosition(rows - 1, column))
            for (row in rows - 1 downTo 1) {
                column = if (column == 0) 1 else 0
                add(OrbitPosition(row, column))
                add(OrbitPosition(row - 1, column))
            }
        }
    }

    private fun startValue(grade: Int, effective: Int, random: Random): Int {
        val upper = when (grade) {
            1 -> 20
            2 -> 50
            3 -> 100
            4 -> 250
            5 -> 500
            else -> 1_000
        } * if (effective >= 4) 2 else 1
        return random.nextInt(2, upper.coerceAtLeast(3))
    }

    private fun nextGate(
        value: Int,
        grade: Int,
        generationDifficulty: Int,
        random: Random
    ): OrbitMathGate {
        val operations = when (generationDifficulty) {
            1 -> listOf(OrbitGateOperation.ADD, OrbitGateOperation.SUBTRACT)
            2 -> listOf(OrbitGateOperation.ADD, OrbitGateOperation.SUBTRACT, OrbitGateOperation.MULTIPLY)
            3 -> OrbitGateOperation.entries
            else -> listOf(
                OrbitGateOperation.MULTIPLY,
                OrbitGateOperation.DIVIDE,
                OrbitGateOperation.ADD,
                OrbitGateOperation.SUBTRACT
            )
        }.shuffled(random)

        operations.forEach { operation ->
            when (operation) {
                OrbitGateOperation.ADD -> {
                    val max = (grade * generationDifficulty + 4).coerceAtLeast(5)
                    return OrbitMathGate(operation, random.nextInt(2, max + 1))
                }
                OrbitGateOperation.SUBTRACT -> {
                    val maxAmount = (value - 1).coerceAtMost(grade * generationDifficulty + 8)
                    if (maxAmount >= 2) return OrbitMathGate(operation, random.nextInt(1, maxAmount + 1))
                }
                OrbitGateOperation.MULTIPLY -> {
                    val maxFactor = if (generationDifficulty >= 4) 6 else 4
                    val factor = random.nextInt(2, maxFactor + 1)
                    if (value <= 50_000 / factor) return OrbitMathGate(operation, factor)
                }
                OrbitGateOperation.DIVIDE -> {
                    val factors = (2..12).filter { value % it == 0 }
                    if (factors.isNotEmpty()) return OrbitMathGate(operation, factors.random(random))
                }
            }
        }
        return OrbitMathGate(OrbitGateOperation.ADD, (grade + generationDifficulty).coerceAtLeast(2))
    }

    private fun applyGuaranteed(value: Int, gate: OrbitMathGate): Int = when (gate.operation) {
        OrbitGateOperation.ADD -> value + gate.amount
        OrbitGateOperation.SUBTRACT -> value - gate.amount
        OrbitGateOperation.MULTIPLY -> value * gate.amount
        OrbitGateOperation.DIVIDE -> value / gate.amount
    }

    private fun directionBetween(from: OrbitPosition, to: OrbitPosition): OrbitDirection = when {
        to.row < from.row -> OrbitDirection.UP
        to.row > from.row -> OrbitDirection.DOWN
        to.column < from.column -> OrbitDirection.LEFT
        else -> OrbitDirection.RIGHT
    }
}
