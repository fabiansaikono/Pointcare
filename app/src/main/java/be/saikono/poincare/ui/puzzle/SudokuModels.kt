package be.saikono.poincare.ui.puzzle

data class SudokuPuzzle(
    val size: Int,
    val boxRows: Int,
    val boxCols: Int,
    val solution: List<List<Int>>,
    val givens: Set<Pair<Int, Int>>
) {
    val values: List<Int> = (1..size).toList()
}
