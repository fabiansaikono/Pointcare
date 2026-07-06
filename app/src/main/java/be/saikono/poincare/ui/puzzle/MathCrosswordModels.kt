package be.saikono.poincare.ui.puzzle

data class MathCrosswordPuzzle(
    val rowCount: Int,
    val columnCount: Int,
    val cells: Map<Pair<Int, Int>, MathCrosswordCell>,
    val equations: List<MathCrosswordEquation>,
    val fixedNumberCells: Set<Pair<Int, Int>>
) {
    val missingNumberCells: List<Pair<Int, Int>> = cells.filterValues { it is MathCrosswordCell.Number }.keys.filter { it !in fixedNumberCells }
    val bankTiles: List<MathCrosswordTile> = missingNumberCells.mapNotNull { cell ->
        (cells[cell] as? MathCrosswordCell.Number)?.let { MathCrosswordTile(cell = cell, value = it.value) }
    }.shuffled()
}

data class MathCrosswordTile(val cell: Pair<Int, Int>, val value: Int)

enum class MathCrosswordOrientation {
    HORIZONTAL,
    VERTICAL
}

sealed interface MathCrosswordCell {
    data class Number(val value: Int) : MathCrosswordCell
    data class Sign(val text: String) : MathCrosswordCell
}

data class MathCrosswordEquation(
    val cells: List<Pair<Int, Int>>,
    val orientation: MathCrosswordOrientation
) {
    val numberCells: List<Pair<Int, Int>> = cells.filterIndexed { index, _ -> index % 2 == 0 }
}
