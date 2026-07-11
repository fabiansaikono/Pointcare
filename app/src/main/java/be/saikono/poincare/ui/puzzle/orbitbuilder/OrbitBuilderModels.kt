package be.saikono.poincare.ui.puzzle.orbitbuilder

import kotlin.math.abs

enum class OrbitDirection(val rowDelta: Int, val columnDelta: Int, val arrow: String) {
    UP(-1, 0, "↑"),
    DOWN(1, 0, "↓"),
    LEFT(0, -1, "←"),
    RIGHT(0, 1, "→");

    companion object {
        fun fromName(raw: String): OrbitDirection = entries.firstOrNull { it.name == raw.uppercase() } ?: RIGHT
    }
}

data class OrbitPosition(
    val row: Int,
    val column: Int
)

enum class OrbitGateOperation(val symbol: String) {
    ADD("+"),
    SUBTRACT("−"),
    MULTIPLY("×"),
    DIVIDE("÷")
}

data class OrbitMathGate(
    val operation: OrbitGateOperation,
    val amount: Int
) {
    val label: String
        get() = "${operation.symbol}$amount"
}

sealed class OrbitFixedTile {
    data object StartRocket : OrbitFixedTile()
    data object TargetPlanet : OrbitFixedTile()
    data object Asteroid : OrbitFixedTile()
    data class Gate(val gate: OrbitMathGate) : OrbitFixedTile()
}

data class OrbitBuilderLevel(
    val id: String,
    val grade: Int,
    val difficultyStars: Int,
    val generationDifficulty: Int = difficultyStars,
    val title: String,
    val instruction: String,
    val rows: Int = 8,
    val columns: Int = 6,
    val startRow: Int,
    val startColumn: Int,
    val initialDirection: OrbitDirection,
    val targetRow: Int,
    val targetColumn: Int,
    val startValue: Int,
    val targetValue: Int,
    val maxPlaceableArrows: Int,
    val optimalArrowCount: Int,
    val availableArrows: List<OrbitDirection> = OrbitDirection.entries,
    val fixedTiles: Map<OrbitPosition, OrbitFixedTile> = emptyMap(),
    val languageIndependent: Boolean = true
) {
    fun tileAt(row: Int, column: Int): OrbitFixedTile? {
        val position = OrbitPosition(row, column)
        return when {
            row == startRow && column == startColumn -> OrbitFixedTile.StartRocket
            row == targetRow && column == targetColumn -> OrbitFixedTile.TargetPlanet
            else -> fixedTiles[position]
        }
    }

    val startPosition: OrbitPosition
        get() = OrbitPosition(startRow, startColumn)

    val targetPosition: OrbitPosition
        get() = OrbitPosition(targetRow, targetColumn)
}

data class PlacedOrbitArrow(
    val row: Int,
    val column: Int,
    val direction: OrbitDirection
)

data class OrbitSimulationStep(
    val row: Int,
    val column: Int,
    val direction: OrbitDirection,
    val value: Int,
    val tile: OrbitFixedTile?,
    val moveNumber: Int
)

enum class OrbitFailureReason {
    ASTEROID,
    LEFT_MAP,
    WRONG_VALUE,
    LOOP,
    INVALID_DIVISION,
    NO_PATH
}

data class OrbitSimulationResult(
    val success: Boolean,
    val finalValue: Int,
    val path: List<OrbitSimulationStep>,
    val failureReason: OrbitFailureReason? = null,
    val moves: Int = path.size,
    val stars: Int = 0
)

sealed class GateResult {
    data class Success(val value: Int) : GateResult()
    data object InvalidDivision : GateResult()
}

enum class OrbitBuilderPhase {
    PLANNING,
    LAUNCHING,
    SUCCESS,
    FAILURE
}

data class OrbitBuilderUiState(
    val levels: List<OrbitBuilderLevel> = listOf(OrbitBuilderLevels.random(1, 1, 1)),
    val levelIndex: Int = 0,
    val selectedArrow: OrbitDirection = OrbitDirection.RIGHT,
    val placedArrows: Map<OrbitPosition, OrbitDirection> = emptyMap(),
    val drawnPath: List<OrbitPosition> = emptyList(),
    val attempts: Int = 0,
    val phase: OrbitBuilderPhase = OrbitBuilderPhase.PLANNING,
    val launchStepIndex: Int = 0,
    val simulationResult: OrbitSimulationResult? = null,
    val completedStars: Map<String, Int> = emptyMap(),
    val launchRunId: Int = 0
) {
    val currentLevel: OrbitBuilderLevel = levels[levelIndex.coerceIn(levels.indices)]
    val currentValue: Int = simulationResult?.path?.getOrNull(launchStepIndex)?.value ?: currentLevel.startValue
    val rocketPosition: OrbitPosition = simulationResult?.path?.getOrNull(launchStepIndex)
        ?.let { OrbitPosition(it.row, it.column) }
        ?: currentLevel.startPosition
    val pathCellsUsed: Int = drawnPath.size
    val arrowsUsed: Int = pathCellsUsed
    val canLaunch: Boolean = drawnPath.size > 1 && drawnPath.firstOrNull() == currentLevel.startPosition && phase == OrbitBuilderPhase.PLANNING
    val hasNextLevel: Boolean = true
    val hasTouchedTarget: Boolean = drawnPath.lastOrNull() == currentLevel.targetPosition
    val estimatedPathSteps: Int = (drawnPath.size - 1).coerceAtLeast(0)
}

fun OrbitPosition.isAdjacentTo(other: OrbitPosition): Boolean =
    abs(row - other.row) + abs(column - other.column) == 1
