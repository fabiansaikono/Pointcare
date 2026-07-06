package be.saikono.poincare.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageLogicTest {

    @Test
    fun `test localized text returns correct language`() {
        val text = LocalizedText(
            nl = "Hallo",
            fr = "Bonjour",
            en = "Hello"
        )
        
        assertEquals("Hallo", text.get("nl"))
        assertEquals("Bonjour", text.get("fr"))
        assertEquals("Hello", text.get("en"))
    }

    @Test
    fun `test localized text falls back to Dutch when translation is missing`() {
        val text = LocalizedText(
            nl = "Hallo"
        )
        
        assertEquals("Hallo", text.get("fr"))
        assertEquals("Hallo", text.get("en"))
        assertEquals("Hallo", text.get("unknown"))
    }

    @Test
    fun `test localized text partial translation fallback`() {
        val text = LocalizedText(
            nl = "Hallo",
            fr = "Bonjour"
        )
        
        assertEquals("Bonjour", text.get("fr"))
        assertEquals("Hallo", text.get("en")) // English missing, fall back to NL
    }
}
