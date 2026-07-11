package be.saikono.poincare.ui.puzzle

import kotlin.random.Random

object MathCrosswordGenerator {
    fun generate(stars: Int, seed: Int = Random.nextInt()): MathCrosswordPuzzle {
        val random = Random(seed)
        val normalizedStars = stars.coerceIn(1, 4)
        val rowCount = 13
        val columnCount = 13
        val maxValue = when (normalizedStars) {
            1 -> 20
            2 -> 100
            3 -> 500
            else -> 2_000
        }
        val numberValues = mutableMapOf<Pair<Int, Int>, Int>()
        val cells = mutableMapOf<Pair<Int, Int>, MathCrosswordCell>()
        val equations = mutableListOf<MathCrosswordEquation>()

        fun randomOperand(): Int = when (normalizedStars) {
            1 -> random.nextInt(1, 16)
            2 -> random.nextInt(1, 41)
            3 -> random.nextInt(1, 81)
            else -> random.nextInt(10, 201)
        }

        fun evaluate(left: Int, op: String, right: Int): Int? = when (op) {
            "+" -> left + right
            "-" -> (left - right).takeIf { it >= 0 }
            "*" -> (left * right).takeIf { it <= maxValue * 2 }
            "/" -> if (right != 0 && left % right == 0) left / right else null
            else -> null
        }

        fun allowedOps(): List<String> = when (normalizedStars) {
            1 -> listOf("+", "-")
            2 -> listOf("+", "-", "*")
            else -> listOf("+", "-", "*", "/")
        }

        fun resolveValues(first: Int?, second: Int?, result: Int?, op: String): Triple<Int, Int, Int>? {
            repeat(80) {
                val left = first ?: randomOperand()
                val right = second ?: randomOperand()
                val computed = evaluate(left, op, right) ?: return@repeat
                if (computed == 0 && normalizedStars < 3) return@repeat
                if (computed > maxValue * 2) return@repeat
                if (result != null && result != computed) return@repeat
                return Triple(left, right, computed)
            }
            return null
        }

        fun placeEquation(row: Int, col: Int, horizontal: Boolean): Boolean {
            val orientation = if (horizontal) MathCrosswordOrientation.HORIZONTAL else MathCrosswordOrientation.VERTICAL
            val coords = List(5) { index -> if (horizontal) row to (col + index) else (row + index) to col }
            if (equations.any { it.cells == coords }) return false
            if (coords.any { it.first !in 0 until rowCount || it.second !in 0 until columnCount }) return false
            if (!hasBoundaryGap(coords.first(), coords.last(), orientation, cells, rowCount, columnCount)) return false
            if (equations.isNotEmpty() && listOf(coords[0], coords[2], coords[4]).none { it in numberValues }) return false
            if (listOf(coords[0], coords[2], coords[4]).any { cells[it] is MathCrosswordCell.Sign }) return false
            if (listOf(coords[1], coords[3]).any { cells[it] is MathCrosswordCell.Number }) return false
            val signCell = cells[coords[1]] as? MathCrosswordCell.Sign
            val equalsCell = cells[coords[3]] as? MathCrosswordCell.Sign
            if (signCell != null && signCell.text !in allowedOps()) return false
            if (equalsCell != null && equalsCell.text != "=") return false
            val opChoices = signCell?.let { listOf(it.text) } ?: allowedOps().shuffled(random)
            for (op in opChoices) {
                val values = resolveValues(numberValues[coords[0]], numberValues[coords[2]], numberValues[coords[4]], op) ?: continue
                numberValues[coords[0]] = values.first
                numberValues[coords[2]] = values.second
                numberValues[coords[4]] = values.third
                cells[coords[0]] = MathCrosswordCell.Number(values.first)
                cells[coords[1]] = MathCrosswordCell.Sign(op)
                cells[coords[2]] = MathCrosswordCell.Number(values.second)
                cells[coords[3]] = MathCrosswordCell.Sign("=")
                cells[coords[4]] = MathCrosswordCell.Number(values.third)
                equations += MathCrosswordEquation(coords, orientation)
                return true
            }
            return false
        }

        placeEquation(row = 6, col = 4, horizontal = true)

        val candidates = buildList {
            for (row in 0 until rowCount) for (col in 0..columnCount - 5) add(Triple(row, col, true))
            for (row in 0..rowCount - 5) for (col in 0 until columnCount) add(Triple(row, col, false))
        }.shuffled(random)
        val targetEquations = when (normalizedStars) {
            1 -> 8
            2 -> 10
            3 -> 12
            else -> 14
        }
        for ((row, col, horizontal) in candidates) {
            if (equations.size >= targetEquations) break
            placeEquation(row, col, horizontal)
        }
        if (equations.size < 8) return generate(stars, seed + 1)

        val numberCells = numberValues.keys.shuffled(random)
        val fixed = numberCells.take(if (normalizedStars >= 4) 3 else 6).toSet()
        val puzzle = MathCrosswordPuzzle(rowCount = rowCount, columnCount = columnCount, cells = cells, equations = equations, fixedNumberCells = fixed)
        return if (validateGeneratedPuzzle(puzzle)) puzzle else generate(stars, seed + 1)
    }

    private fun hasBoundaryGap(
        start: Pair<Int, Int>,
        end: Pair<Int, Int>,
        orientation: MathCrosswordOrientation,
        cells: Map<Pair<Int, Int>, MathCrosswordCell>,
        rowCount: Int,
        columnCount: Int
    ): Boolean {
        val before = when (orientation) {
            MathCrosswordOrientation.HORIZONTAL -> start.first to start.second - 1
            MathCrosswordOrientation.VERTICAL -> start.first - 1 to start.second
        }
        val after = when (orientation) {
            MathCrosswordOrientation.HORIZONTAL -> end.first to end.second + 1
            MathCrosswordOrientation.VERTICAL -> end.first + 1 to end.second
        }
        fun outside(cell: Pair<Int, Int>) = cell.first !in 0 until rowCount || cell.second !in 0 until columnCount
        return (outside(before) || before !in cells) && (outside(after) || after !in cells)
    }

    private fun validateGeneratedPuzzle(puzzle: MathCrosswordPuzzle): Boolean {
        if (puzzle.rowCount != 13 || puzzle.columnCount != 13) return false
        if (puzzle.cells.keys.any { it.first !in 0 until puzzle.rowCount || it.second !in 0 until puzzle.columnCount }) return false
        val equationCellUse = mutableMapOf<Pair<Int, Int>, MutableList<MathCrosswordOrientation>>()
        val equationCellSets = puzzle.equations.map { it.cells.toSet() }.toSet()

        puzzle.equations.forEach { equation ->
            if (equation.cells.size != 5) return false
            if (!hasBoundaryGap(equation.cells.first(), equation.cells.last(), equation.orientation, puzzle.cells, puzzle.rowCount, puzzle.columnCount)) return false
            equation.cells.forEachIndexed { index, cell ->
                equationCellUse.getOrPut(cell) { mutableListOf() }.add(equation.orientation)
                val content = puzzle.cells[cell] ?: return false
                when (index) {
                    0, 2, 4 -> if (content !is MathCrosswordCell.Number) return false
                    1 -> if ((content as? MathCrosswordCell.Sign)?.text !in setOf("+", "-", "*", "/")) return false
                    3 -> if ((content as? MathCrosswordCell.Sign)?.text != "=") return false
                }
            }
            if (!equation.isCorrect(puzzle)) return false
        }

        equationCellUse.forEach { (cell, orientations) ->
            val content = puzzle.cells[cell] ?: return false
            if (content !is MathCrosswordCell.Number && orientations.size > 1) return false
            if (orientations.count { it == MathCrosswordOrientation.HORIZONTAL } > 1) return false
            if (orientations.count { it == MathCrosswordOrientation.VERTICAL } > 1) return false
        }

        fun validateLine(line: List<Pair<Int, Int>>): Boolean {
            val visible = line.filter { it in puzzle.cells }
            if (visible.size <= 1) return true
            return visible.toSet() in equationCellSets && visible.count { (puzzle.cells[it] as? MathCrosswordCell.Sign)?.text == "=" } == 1
        }

        for (row in 0 until puzzle.rowCount) {
            val rowCells = (0 until puzzle.columnCount).map { col -> row to col }
            rowCells.splitVisibleSequences(puzzle.cells).forEach { if (!validateLine(it)) return false }
        }
        for (col in 0 until puzzle.columnCount) {
            val columnCells = (0 until puzzle.rowCount).map { row -> row to col }
            columnCells.splitVisibleSequences(puzzle.cells).forEach { if (!validateLine(it)) return false }
        }
        return true
    }

    private fun MathCrosswordEquation.isCorrect(puzzle: MathCrosswordPuzzle): Boolean {
        val left = (puzzle.cells[cells[0]] as? MathCrosswordCell.Number)?.value ?: return false
        val op = (puzzle.cells[cells[1]] as? MathCrosswordCell.Sign)?.text ?: return false
        val right = (puzzle.cells[cells[2]] as? MathCrosswordCell.Number)?.value ?: return false
        val expected = (puzzle.cells[cells[4]] as? MathCrosswordCell.Number)?.value ?: return false
        val actual = when (op) {
            "+" -> left + right
            "-" -> left - right
            "*" -> left * right
            "/" -> if (right != 0 && left % right == 0) left / right else return false
            else -> return false
        }
        return actual == expected
    }

    private fun List<Pair<Int, Int>>.splitVisibleSequences(cells: Map<Pair<Int, Int>, MathCrosswordCell>): List<List<Pair<Int, Int>>> {
        val sequences = mutableListOf<List<Pair<Int, Int>>>()
        var current = mutableListOf<Pair<Int, Int>>()
        forEach { cell ->
            if (cell in cells) {
                current.add(cell)
            } else {
                if (current.isNotEmpty()) sequences += current
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) sequences += current
        return sequences
    }
}
