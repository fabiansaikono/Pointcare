package be.saikono.poincare.domain.curriculum

import be.saikono.poincare.model.InputMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseCapabilityRegistryTest {
    @Test
    fun `lesson player only advertises implemented renderers`() {
        val active = InputMode.entries.filter {
            ExerciseCapabilityRegistry.capability(it, ExerciseRuntime.LESSON_V2).status == ExerciseCapabilityStatus.ACTIVE
        }
        assertEquals(
            setOf(InputMode.TAP_CHOICE, InputMode.LISTEN_AND_SELECT, InputMode.CUSTOM_NUMBER_PAD, InputMode.CUSTOM_TEXT_KEYBOARD, InputMode.TRACE_LETTER),
            active.toSet()
        )
    }

    @Test
    fun `unfinished modes are explicitly unsupported`() {
        for (mode in listOf(InputMode.BUILD_WORD, InputMode.NUMBER_WALL)) {
            for (runtime in ExerciseRuntime.entries) {
                assertEquals(
                    ExerciseCapabilityStatus.UNSUPPORTED,
                    ExerciseCapabilityRegistry.capability(mode, runtime).status
                )
            }
        }
    }
}
