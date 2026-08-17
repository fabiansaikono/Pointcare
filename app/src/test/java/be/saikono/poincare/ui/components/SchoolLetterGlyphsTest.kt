package be.saikono.poincare.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolLetterGlyphsTest {
    @Test
    fun `templates cover the complete alphabet and all digits`() {
        val expected = (('a'..'z') + ('A'..'Z') + ('0'..'9')).toSet()

        assertEquals(expected, SchoolLetterGlyphs.supportedLetters)
    }

    @Test
    fun `every stroke is traceable and remains inside the canvas`() {
        SchoolLetterGlyphs.supportedLetters.forEach { letter ->
            val glyph = SchoolLetterGlyphs.forLetter(letter.toString())
            assertTrue("$letter needs at least one stroke", glyph.strokes.isNotEmpty())
            glyph.strokes.forEach { stroke ->
                assertTrue("$letter contains an empty stroke", stroke.size >= 2)
                stroke.forEach { point ->
                    assertTrue("$letter x-coordinate is outside the canvas", point.x in 0f..1f)
                    assertTrue("$letter y-coordinate is outside the canvas", point.y in 0f..1f)
                }
            }
        }
    }
}
