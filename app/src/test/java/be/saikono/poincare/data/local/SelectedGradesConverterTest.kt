package be.saikono.poincare.data.local

import be.saikono.poincare.model.LearningDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedGradesConverterTest {
    private val converters = Converters()

    @Test
    fun selectedGradesRoundTripIsStableAndNormalized() {
        val original = mapOf(
            LearningDomain.WRITING.name to listOf(6, 4, 4, 2),
            LearningDomain.MATH.name to listOf(3, 1)
        )

        val encoded = converters.fromSelectedGrades(original)
        val decoded = converters.toSelectedGrades(encoded)

        assertEquals(listOf(1, 3), decoded[LearningDomain.MATH.name])
        assertEquals(listOf(2, 4, 6), decoded[LearningDomain.WRITING.name])
    }

    @Test
    fun malformedOrOutOfRangeGradesAreIgnored() {
        val decoded = converters.toSelectedGrades("READING=1,2,9,bad;MATH=0,6")

        assertEquals(listOf(1, 2), decoded[LearningDomain.READING.name])
        assertEquals(listOf(6), decoded[LearningDomain.MATH.name])
    }
}
