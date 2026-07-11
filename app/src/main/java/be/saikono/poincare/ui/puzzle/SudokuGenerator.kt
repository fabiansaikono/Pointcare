package be.saikono.poincare.ui.puzzle

import kotlin.random.Random

object SudokuGenerator {
    fun generate(stars: Int, seed: Int = Random.nextInt()): SudokuPuzzle {
        val size = when (stars.coerceIn(1, 4)) {
            1 -> 4
            2 -> 6
            else -> 9
        }
        val boxRows = if (size == 9) 3 else 2
        val boxCols = when (size) {
            4 -> 2
            6 -> 3
            else -> 3
        }
        val random = Random(seed)
        val base = List(size) { row ->
            List(size) { col -> ((row * boxCols + row / boxRows + col) % size) + 1 }
        }
        val digitMap = (1..size).shuffled(random).withIndex().associate { (index, value) -> index + 1 to value }
        val solution = base.map { row -> row.map { digitMap.getValue(it) } }
        val fixedRatio = when (stars.coerceIn(1, 4)) {
            1 -> 0.58f
            2 -> 0.42f
            3 -> 0.32f
            else -> 0.23f
        }
        val allCells = buildList {
            for (row in 0 until size) for (col in 0 until size) add(row to col)
        }.shuffled(random)
        val givenCount = (size * size * fixedRatio).toInt().coerceAtLeast(size)
        return SudokuPuzzle(size, boxRows, boxCols, solution, allCells.take(givenCount).toSet())
    }
}
