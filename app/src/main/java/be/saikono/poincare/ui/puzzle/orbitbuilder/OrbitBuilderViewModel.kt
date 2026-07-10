package be.saikono.poincare.ui.puzzle.orbitbuilder

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class OrbitBuilderViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(OrbitBuilderUiState())
    val uiState: StateFlow<OrbitBuilderUiState> = _uiState

    fun setDifficultyStars(stars: Int?) {
        val selectedStars = stars?.coerceIn(1, 3) ?: 1
        val filteredLevels = OrbitBuilderLevels.all.filter { it.difficultyStars == selectedStars }.ifEmpty { OrbitBuilderLevels.all }.shuffled()
        _uiState.update { state ->
            state.copy(levels = filteredLevels, levelIndex = 0, drawnPath = emptyList(), placedArrows = emptyMap(), attempts = 0, phase = OrbitBuilderPhase.PLANNING, launchStepIndex = 0, simulationResult = null, launchRunId = state.launchRunId + 1)
        }
    }

    fun startDrawnPath(row: Int, column: Int) {
        _uiState.update { state ->
            if (state.phase != OrbitBuilderPhase.PLANNING) return@update state
            val level = state.currentLevel
            val position = OrbitPosition(row, column)
            if (position != level.startPosition) return@update state
            state.copy(drawnPath = listOf(position), placedArrows = emptyMap(), simulationResult = null, launchStepIndex = 0)
        }
    }

    fun extendDrawnPath(row: Int, column: Int) {
        _uiState.update { state ->
            if (state.phase != OrbitBuilderPhase.PLANNING) return@update state
            val level = state.currentLevel
            val position = OrbitPosition(row, column)
            if (!OrbitBuilderEngine.canDrawOn(level, row, column)) return@update state
            if (state.drawnPath.isEmpty()) return@update if (position == level.startPosition) state.copy(drawnPath = listOf(position), placedArrows = emptyMap(), simulationResult = null) else state
            val last = state.drawnPath.last()
            if (position == last) return@update state
            val previous = state.drawnPath.getOrNull(state.drawnPath.lastIndex - 1)
            if (previous == position) return@update state.copy(drawnPath = state.drawnPath.dropLast(1), placedArrows = emptyMap(), simulationResult = null, launchStepIndex = 0)
            if (!position.isAdjacentTo(last)) return@update state
            val existingIndex = state.drawnPath.indexOf(position)
            val nextPath = if (existingIndex >= 0) state.drawnPath.take(existingIndex + 1) else state.drawnPath + position
            state.copy(drawnPath = nextPath, placedArrows = emptyMap(), simulationResult = null, launchStepIndex = 0)
        }
    }

    fun clearPath(clearAttempts: Boolean = false) {
        _uiState.update { state -> state.copy(drawnPath = emptyList(), placedArrows = emptyMap(), phase = OrbitBuilderPhase.PLANNING, launchStepIndex = 0, simulationResult = null, attempts = if (clearAttempts) 0 else state.attempts) }
    }

    fun launch() {
        _uiState.update { state ->
            if (!state.canLaunch) return@update state
            val attempts = state.attempts + 1
            val result = OrbitBuilderEngine.simulateDrawnPath(state.currentLevel, state.drawnPath, attempts)
            state.copy(attempts = attempts, phase = OrbitBuilderPhase.LAUNCHING, launchStepIndex = 0, simulationResult = result, launchRunId = state.launchRunId + 1)
        }
    }

    fun advanceLaunchStep() {
        _uiState.update { state ->
            val result = state.simulationResult ?: return@update state.copy(phase = OrbitBuilderPhase.PLANNING)
            val nextIndex = state.launchStepIndex + 1
            if (nextIndex < result.path.size) state.copy(launchStepIndex = nextIndex) else if (result.success) state.copy(phase = OrbitBuilderPhase.SUCCESS, completedStars = state.completedStars + (state.currentLevel.id to result.stars)) else state.copy(phase = OrbitBuilderPhase.FAILURE)
        }
    }

    fun resetLevel(clearAttempts: Boolean = false) { clearPath(clearAttempts = clearAttempts) }

    fun retryAfterFailure() {
        _uiState.update { state ->
            state.copy(
                drawnPath = emptyList(),
                placedArrows = emptyMap(),
                phase = OrbitBuilderPhase.PLANNING,
                launchStepIndex = 0,
                simulationResult = null
            )
        }
    }

    fun nextMission() {
        _uiState.update { state ->
            val nextIndex = when {
                state.levels.isEmpty() -> 0
                state.levels.size == 1 -> 0
                else -> state.levels.indices.filter { it != state.levelIndex }.random()
            }
            state.copy(levelIndex = nextIndex, drawnPath = emptyList(), placedArrows = emptyMap(), attempts = 0, phase = OrbitBuilderPhase.PLANNING, launchStepIndex = 0, simulationResult = null, launchRunId = state.launchRunId + 1)
        }
    }

    fun selectArrow(direction: OrbitDirection) = Unit
    fun toggleArrow(row: Int, column: Int) = extendDrawnPath(row, column)
}
