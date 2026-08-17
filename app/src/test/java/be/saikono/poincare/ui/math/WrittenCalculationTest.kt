package be.saikono.poincare.ui.math

import be.saikono.poincare.model.SkillLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WrittenCalculationTest {

    @Test
    fun difficultyChanceBucketsMatchRequestedPercentages() {
        (1..5).forEach { missionNumber ->
            val difficulties = (0 until 300).map { bucket ->
                WrittenCalculationGenerator.difficultyFor(missionNumber, bucket)
            }
            assertEquals(300, difficulties.count { it == 1 })
            assertEquals(0, difficulties.count { it == 2 })
            assertEquals(0, difficulties.count { it == 3 })
        }

        val sixToTwentyFive = (0 until 300).map { bucket ->
            WrittenCalculationGenerator.difficultyFor(6, bucket)
        }
        assertEquals(150, sixToTwentyFive.count { it == 1 })
        assertEquals(150, sixToTwentyFive.count { it == 2 })
        assertEquals(0, sixToTwentyFive.count { it == 3 })

        // Mission 25 stays in the previous block. The one-third block starts at 26.
        val twentySixToNinetyNine = (0 until 300).map { bucket ->
            WrittenCalculationGenerator.difficultyFor(26, bucket)
        }
        assertEquals(100, twentySixToNinetyNine.count { it == 1 })
        assertEquals(100, twentySixToNinetyNine.count { it == 2 })
        assertEquals(100, twentySixToNinetyNine.count { it == 3 })

        val oneHundredAndBeyond = (0 until 300).map { bucket ->
            WrittenCalculationGenerator.difficultyFor(100, bucket)
        }
        assertEquals(60, oneHundredAndBeyond.count { it == 1 })
        assertEquals(90, oneHundredAndBeyond.count { it == 2 })
        assertEquals(150, oneHundredAndBeyond.count { it == 3 })
    }

    @Test
    fun generatorIsOpenEndedAndKeepsStableMissionIds() {
        WrittenOperation.entries.forEach { operation ->
            SkillLevel.entries.filter { it != SkillLevel.NOT_APPLICABLE }.forEach { level ->
                val first = WrittenCalculationGenerator.generate(operation, level, 995, 20)
                val second = WrittenCalculationGenerator.generate(operation, level, 995, 20)
                assertEquals(20, first.size)
                assertEquals(first.map { it.id }, second.map { it.id })
                assertEquals(
                    first.map { it.data["difficultyStars"] },
                    second.map { it.data["difficultyStars"] }
                )
                assertEquals((995..1014).map { it.toString() }, first.map { it.data["missionNumber"] })
                assertTrue("Generator did not continue beyond mission 1000", first.last().id.endsWith("001014"))
            }
        }
    }

    @Test
    fun generatedCorrectAnswerIsAccepted() {
        WrittenOperation.entries.forEach { operation ->
            SkillLevel.entries.filter { it != SkillLevel.NOT_APPLICABLE }.forEach { level ->
                WrittenCalculationGenerator.generate(operation, level, 1, 150).forEach { exercise ->
                    val calculation = requireNotNull(WrittenCalculation.fromExercise(exercise))
                    val entries = mutableMapOf<String, String>()
                    if (operation != WrittenOperation.DIVISION && calculation.result < 0) {
                        entries[calculation.resultSignCellId] = "−"
                    }
                    val digits = calculation.expectedResultDigits()
                    calculation.resultCellIds.zip(digits).forEach { (id, digit) ->
                        if (digit.isNotBlank()) entries[id] = digit
                    }
                    if (calculation.hasRemainderField) {
                        val remainderDigits = calculation.remainder.toString()
                            .padStart(calculation.remainderCellIds.size, '0')
                        calculation.remainderCellIds.zip(remainderDigits.map(Char::toString)).forEach { (id, digit) ->
                            entries[id] = digit
                        }
                    }
                    assertTrue("Rejected generated answer for $calculation", calculation.isAnswerCorrect(entries))
                }
            }
        }
    }
}
