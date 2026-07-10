package be.saikono.poincare.ui.puzzle.orbitbuilder

import kotlin.math.abs

object OrbitBuilderEngine {
    const val MAX_MOVES = 100

    fun simulateDrawnPath(
        level: OrbitBuilderLevel,
        drawnPath: List<OrbitPosition>,
        attempts: Int = 1
    ): OrbitSimulationResult {
        if (drawnPath.size < 2 || drawnPath.firstOrNull() != level.startPosition) {
            return OrbitSimulationResult(
                success = false,
                finalValue = level.startValue,
                path = listOf(
                    OrbitSimulationStep(
                        level.startRow,
                        level.startColumn,
                        level.initialDirection,
                        level.startValue,
                        level.tileAt(level.startRow, level.startColumn),
                        moveNumber = 0
                    )
                ),
                failureReason = OrbitFailureReason.NO_PATH,
                moves = 0
            )
        }

        if (drawnPath.size > MAX_MOVES) {
            return OrbitSimulationResult(
                success = false,
                finalValue = level.startValue,
                path = emptyList(),
                failureReason = OrbitFailureReason.LOOP,
                moves = drawnPath.size
            )
        }

        var value = level.startValue
        var direction = level.initialDirection
        val steps = mutableListOf(
            OrbitSimulationStep(
                level.startRow,
                level.startColumn,
                direction,
                value,
                level.tileAt(level.startRow, level.startColumn),
                moveNumber = 0
            )
        )
        val visited = mutableSetOf<PathFingerprint>()

        for (index in 1 until drawnPath.size) {
            val previous = drawnPath[index - 1]
            val position = drawnPath[index]
            direction = directionBetween(previous, position)
                ?: return OrbitSimulationResult(false, value, steps, OrbitFailureReason.NO_PATH, steps.size)

            if (position.row !in 0 until level.rows || position.column !in 0 until level.columns) {
                return OrbitSimulationResult(false, value, steps, OrbitFailureReason.LEFT_MAP, steps.size)
            }

            val tile = level.tileAt(position.row, position.column)

            when (tile) {
                OrbitFixedTile.Asteroid -> {
                    steps += OrbitSimulationStep(position.row, position.column, direction, value, tile, index)
                    return OrbitSimulationResult(false, value, steps, OrbitFailureReason.ASTEROID, steps.size)
                }
                is OrbitFixedTile.Gate -> {
                    when (val gateResult = applyGate(value, tile.gate)) {
                        is GateResult.Success -> value = gateResult.value
                        GateResult.InvalidDivision -> {
                            steps += OrbitSimulationStep(position.row, position.column, direction, value, tile, index)
                            return OrbitSimulationResult(false, value, steps, OrbitFailureReason.INVALID_DIVISION, steps.size)
                        }
                    }
                }
                else -> Unit
            }

            steps += OrbitSimulationStep(position.row, position.column, direction, value, tile, index)

            if (tile == OrbitFixedTile.TargetPlanet) {
                val success = value == level.targetValue
                return OrbitSimulationResult(
                    success = success,
                    finalValue = value,
                    path = steps,
                    failureReason = if (success) null else OrbitFailureReason.WRONG_VALUE,
                    moves = steps.size,
                    stars = if (success) calculateStarsForPath(level, attempts, drawnPath.size) else 0
                )
            }

            val fingerprint = PathFingerprint(position.row, position.column, value)
            if (!visited.add(fingerprint)) {
                return OrbitSimulationResult(false, value, steps, OrbitFailureReason.LOOP, steps.size)
            }
        }

        return OrbitSimulationResult(false, value, steps, OrbitFailureReason.NO_PATH, steps.size)
    }

    /**
     * Legacy arrow-based simulation kept for compatibility with existing tests and earlier local builds.
     * The UI no longer shows arrows; it uses simulateDrawnPath instead.
     */
    fun simulate(
        level: OrbitBuilderLevel,
        placedArrows: Map<OrbitPosition, OrbitDirection>,
        attempts: Int = 1
    ): OrbitSimulationResult {
        var row = level.startRow
        var column = level.startColumn
        var direction = level.initialDirection
        var value = level.startValue
        val path = mutableListOf(
            OrbitSimulationStep(row, column, direction, value, level.tileAt(row, column), moveNumber = 0)
        )
        val visited = mutableSetOf<SimulationFingerprint>()

        repeat(MAX_MOVES) { moveIndex ->
            val nextRow = row + direction.rowDelta
            val nextColumn = column + direction.columnDelta

            if (nextRow !in 0 until level.rows || nextColumn !in 0 until level.columns) {
                return OrbitSimulationResult(false, value, path, OrbitFailureReason.LEFT_MAP, path.size)
            }

            row = nextRow
            column = nextColumn
            val position = OrbitPosition(row, column)
            val tile = level.tileAt(row, column)

            when (tile) {
                OrbitFixedTile.Asteroid -> {
                    path += OrbitSimulationStep(row, column, direction, value, tile, moveIndex + 1)
                    return OrbitSimulationResult(false, value, path, OrbitFailureReason.ASTEROID, path.size)
                }
                is OrbitFixedTile.Gate -> {
                    when (val gateResult = applyGate(value, tile.gate)) {
                        is GateResult.Success -> value = gateResult.value
                        GateResult.InvalidDivision -> {
                            path += OrbitSimulationStep(row, column, direction, value, tile, moveIndex + 1)
                            return OrbitSimulationResult(false, value, path, OrbitFailureReason.INVALID_DIVISION, path.size)
                        }
                    }
                }
                else -> Unit
            }

            placedArrows[position]?.let { direction = it }
            path += OrbitSimulationStep(row, column, direction, value, tile, moveIndex + 1)

            if (tile == OrbitFixedTile.TargetPlanet) {
                val success = value == level.targetValue
                return OrbitSimulationResult(
                    success = success,
                    finalValue = value,
                    path = path,
                    failureReason = if (success) null else OrbitFailureReason.WRONG_VALUE,
                    moves = path.size,
                    stars = if (success) calculateStars(level, attempts, placedArrows.size) else 0
                )
            }

            val fingerprint = SimulationFingerprint(row, column, direction, value)
            if (!visited.add(fingerprint)) {
                return OrbitSimulationResult(false, value, path, OrbitFailureReason.LOOP, path.size)
            }
        }

        return OrbitSimulationResult(false, value, path, OrbitFailureReason.LOOP, path.size)
    }

    fun canDrawOn(level: OrbitBuilderLevel, row: Int, column: Int): Boolean {
        if (row !in 0 until level.rows || column !in 0 until level.columns) return false
        val tile = level.tileAt(row, column)
        return tile != OrbitFixedTile.Asteroid
    }

    fun canPlaceArrow(level: OrbitBuilderLevel, row: Int, column: Int): Boolean {
        if (row !in 0 until level.rows || column !in 0 until level.columns) return false
        return level.tileAt(row, column) == null
    }

    fun applyGate(value: Int, gate: OrbitMathGate): GateResult = when (gate.operation) {
        OrbitGateOperation.ADD -> GateResult.Success(value + gate.amount)
        OrbitGateOperation.SUBTRACT -> GateResult.Success(value - gate.amount)
        OrbitGateOperation.MULTIPLY -> GateResult.Success(value * gate.amount)
        OrbitGateOperation.DIVIDE -> {
            if (gate.amount == 0 || value % gate.amount != 0) GateResult.InvalidDivision
            else GateResult.Success(value / gate.amount)
        }
    }

    fun calculateStars(level: OrbitBuilderLevel, attempts: Int, placedArrowCount: Int): Int = when {
        attempts <= 1 && placedArrowCount <= level.optimalArrowCount -> 3
        placedArrowCount <= level.maxPlaceableArrows -> 2
        else -> 1
    }

    fun calculateStarsForPath(level: OrbitBuilderLevel, attempts: Int, pathCellCount: Int): Int {
        val steps = (pathCellCount - 1).coerceAtLeast(0)
        val directDistance = abs(level.targetRow - level.startRow) + abs(level.targetColumn - level.startColumn)
        val generousOptimal = (directDistance + level.optimalArrowCount + 4).coerceAtLeast(6)
        return when {
            attempts <= 1 && steps <= generousOptimal -> 3
            steps <= level.rows * level.columns -> 2
            else -> 1
        }
    }

    private fun directionBetween(from: OrbitPosition, to: OrbitPosition): OrbitDirection? = when {
        to.row == from.row - 1 && to.column == from.column -> OrbitDirection.UP
        to.row == from.row + 1 && to.column == from.column -> OrbitDirection.DOWN
        to.row == from.row && to.column == from.column - 1 -> OrbitDirection.LEFT
        to.row == from.row && to.column == from.column + 1 -> OrbitDirection.RIGHT
        else -> null
    }

    private data class SimulationFingerprint(
        val row: Int,
        val column: Int,
        val direction: OrbitDirection,
        val value: Int
    )

    private data class PathFingerprint(
        val row: Int,
        val column: Int,
        val value: Int
    )
}
