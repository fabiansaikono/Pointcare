package be.saikono.poincare.data.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryTextRangesTest {
    @Test
    fun tokenization_keeps_accents_apostrophes_hyphens_and_offsets() {
        val text = "  L’école   d’Aya is co-operatief, écht!"
        val words = StoryTextRanges.tokenize(text)
        assertEquals(listOf("L’école", "d’Aya", "is", "co-operatief", "écht"), words.map { it.text })
        words.forEach { word -> assertEquals(word.text, text.substring(word.start, word.endExclusive)) }
    }

    @Test
    fun range_mapping_selects_overlapping_word_only() {
        val words = StoryTextRanges.tokenize("Aya kijkt naar Nox.")
        assertEquals(1, StoryTextRanges.wordAtRange(words, 4, 9))
        assertEquals(3, StoryTextRanges.wordAtRange(words, 14, 17))
        assertNull(StoryTextRanges.wordAtRange(words, -1, 2))
    }

    @Test
    fun fallback_delay_accounts_for_punctuation() {
        val text = "Hallo, Nox!"
        val words = StoryTextRanges.tokenize(text)
        assertTrue(
            StoryTextRanges.estimatedWordDelayMillis(words[0], text, "nl-BE") >
                StoryTextRanges.estimatedWordDelayMillis(words[0], "Hallo Nox", "nl-BE")
        )
    }
}
