package be.saikono.poincare.domain.curriculum

import be.saikono.poincare.model.InputMode

enum class ExerciseRuntime { LESSON_V2, LEGACY_READING, LEGACY_MATH, PUZZLE }
enum class ExerciseCapabilityStatus { ACTIVE, EXPERIMENTAL, UNSUPPORTED }

data class ExerciseCapability(
    val inputMode: InputMode,
    val runtime: ExerciseRuntime,
    val status: ExerciseCapabilityStatus,
    val rendererVersion: Int? = null
)

/** Single capability registry for publication checks and safe runtime fallback. */
object ExerciseCapabilityRegistry {
    private val lessonV2Active = setOf(
        InputMode.TAP_CHOICE,
        InputMode.LISTEN_AND_SELECT,
        InputMode.CUSTOM_NUMBER_PAD,
        InputMode.CUSTOM_TEXT_KEYBOARD,
        InputMode.TRACE_LETTER
    )
    private val puzzleActive = setOf(
        InputMode.MATH_CROSSWORD,
        InputMode.SUDOKU_GRID,
        InputMode.WORD_SEEKER,
        InputMode.CALCULATOR_PUZZLE
    )

    fun capability(inputMode: InputMode, runtime: ExerciseRuntime): ExerciseCapability {
        val status = when {
            inputMode in setOf(InputMode.BUILD_WORD, InputMode.NUMBER_WALL) -> ExerciseCapabilityStatus.UNSUPPORTED
            runtime == ExerciseRuntime.LESSON_V2 && inputMode in lessonV2Active -> ExerciseCapabilityStatus.ACTIVE
            runtime == ExerciseRuntime.LEGACY_READING && inputMode in setOf(
                InputMode.TAP_CHOICE,
                InputMode.LISTEN_AND_SELECT,
                InputMode.CUSTOM_TEXT_KEYBOARD
            ) -> ExerciseCapabilityStatus.ACTIVE
            runtime == ExerciseRuntime.LEGACY_MATH && inputMode in setOf(
                InputMode.TAP_CHOICE,
                InputMode.CUSTOM_NUMBER_PAD,
                InputMode.DRAG_DROP_DIGITS
            ) -> ExerciseCapabilityStatus.ACTIVE
            runtime == ExerciseRuntime.PUZZLE && inputMode in puzzleActive -> ExerciseCapabilityStatus.ACTIVE
            else -> ExerciseCapabilityStatus.EXPERIMENTAL
        }
        return ExerciseCapability(
            inputMode = inputMode,
            runtime = runtime,
            status = status,
            rendererVersion = if (status == ExerciseCapabilityStatus.ACTIVE) 1 else null
        )
    }
}
