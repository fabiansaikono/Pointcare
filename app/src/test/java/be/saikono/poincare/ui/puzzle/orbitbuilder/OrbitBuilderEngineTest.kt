package be.saikono.poincare.ui.puzzle.orbitbuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitBuilderEngineTest {
    @Test
    fun successPathReachesTargetWithCorrectValue() {
        val level = OrbitBuilderLevel(
            id = "test_success",
            grade = 1,
            difficultyStars = 1,
            title = "Test",
            instruction = "Test",
            rows = 3,
            columns = 4,
            startRow = 2,
            startColumn = 0,
            initialDirection = OrbitDirection.RIGHT,
            targetRow = 0,
            targetColumn = 3,
            startValue = 6,
            targetValue = 24,
            maxPlaceableArrows = 3,
            optimalArrowCount = 2,
            fixedTiles = mapOf(
                OrbitPosition(2, 1) to OrbitFixedTile.Gate(OrbitMathGate(OrbitGateOperation.ADD, 6)),
                OrbitPosition(0, 1) to OrbitFixedTile.Gate(OrbitMathGate(OrbitGateOperation.MULTIPLY, 2))
            )
        )
        val arrows = mapOf(
            OrbitPosition(2, 1) to OrbitDirection.UP,
            OrbitPosition(0, 1) to OrbitDirection.RIGHT
        )

        val result = OrbitBuilderEngine.simulate(level, arrows, attempts = 1)

        assertTrue(result.success)
        assertEquals(24, result.finalValue)
        assertEquals(3, result.stars)
    }

    @Test
    fun wrongValueFails() {
        val level = OrbitBuilderLevel(
            id = "test_wrong_value",
            grade = 1,
            difficultyStars = 1,
            title = "Test",
            instruction = "Test",
            rows = 1,
            columns = 3,
            startRow = 0,
            startColumn = 0,
            initialDirection = OrbitDirection.RIGHT,
            targetRow = 0,
            targetColumn = 2,
            startValue = 1,
            targetValue = 3,
            maxPlaceableArrows = 1,
            optimalArrowCount = 0
        )

        val result = OrbitBuilderEngine.simulate(level, emptyMap(), attempts = 1)

        assertFalse(result.success)
        assertEquals(OrbitFailureReason.WRONG_VALUE, result.failureReason)
    }

    @Test
    fun asteroidCollisionFails() {
        val level = OrbitBuilderLevel(
            id = "test_asteroid",
            grade = 1,
            difficultyStars = 1,
            title = "Test",
            instruction = "Test",
            rows = 1,
            columns = 3,
            startRow = 0,
            startColumn = 0,
            initialDirection = OrbitDirection.RIGHT,
            targetRow = 0,
            targetColumn = 2,
            startValue = 1,
            targetValue = 1,
            maxPlaceableArrows = 1,
            optimalArrowCount = 0,
            fixedTiles = mapOf(OrbitPosition(0, 1) to OrbitFixedTile.Asteroid)
        )

        val result = OrbitBuilderEngine.simulate(level, emptyMap(), attempts = 1)

        assertFalse(result.success)
        assertEquals(OrbitFailureReason.ASTEROID, result.failureReason)
    }

    @Test
    fun invalidDivisionFails() {
        val result = OrbitBuilderEngine.applyGate(7, OrbitMathGate(OrbitGateOperation.DIVIDE, 2))
        assertEquals(GateResult.InvalidDivision, result)
    }

    @Test
    fun loopFails() {
        val level = OrbitBuilderLevel(
            id = "test_loop",
            grade = 1,
            difficultyStars = 1,
            title = "Test",
            instruction = "Test",
            rows = 2,
            columns = 2,
            startRow = 0,
            startColumn = 0,
            initialDirection = OrbitDirection.RIGHT,
            targetRow = 1,
            targetColumn = 1,
            startValue = 1,
            targetValue = 99,
            maxPlaceableArrows = 4,
            optimalArrowCount = 4
        )
        val arrows = mapOf(
            OrbitPosition(0, 1) to OrbitDirection.DOWN,
            OrbitPosition(1, 1) to OrbitDirection.LEFT,
            OrbitPosition(1, 0) to OrbitDirection.UP,
            OrbitPosition(0, 0) to OrbitDirection.RIGHT
        )

        val result = OrbitBuilderEngine.simulate(level, arrows, attempts = 1)

        assertFalse(result.success)
    }
}
