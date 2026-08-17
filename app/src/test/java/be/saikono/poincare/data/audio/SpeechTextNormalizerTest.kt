package be.saikono.poincare.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextNormalizerTest {
    @Test
    fun `new Flemish sound is expanded without an audio asset`() {
        assertEquals(
            "de klank eu zoals in neus",
            SpeechTextNormalizer.normalize("eu", "nl-BE")
        )
    }

    @Test
    fun `complete multiword label remains one utterance`() {
        assertEquals("de auto", SpeechTextNormalizer.normalize("de auto", "nl-BE"))
    }

    @Test
    fun `Dutch calculation operators become spoken words`() {
        assertEquals(
            "Bereken 5 plus 4 min 1 maal 2 gedeeld door 4 gedeeld door 2 is 8",
            SpeechTextNormalizer.normalize("Bereken 5 + 4 - 1 x 2 : 4 / 2 = 8", "nl-BE")
        )
    }

    @Test
    fun `compact Dutch fraction is read as a fraction`() {
        assertEquals(
            "één derde van de taart",
            SpeechTextNormalizer.normalize("1/3 van de taart", "nl-BE")
        )
    }

    @Test
    fun `known compound pronunciation can be corrected without changing display text`() {
        assertEquals(
            "De bananen schil is geel.",
            SpeechTextNormalizer.normalize("De bananenschil is geel.", "nl-BE")
        )
    }

    @Test
    fun `time is not misread as division`() {
        assertEquals("Het is 9 05.", SpeechTextNormalizer.normalize("Het is 09:05.", "nl-BE"))
    }

    @Test
    fun `phonics cues are slower than ordinary speech`() {
        assertTrue(SpeechTextNormalizer.speedFor("de klank eu zoals in neus", "nl-BE", 1.0f) < 1.0f)
        assertEquals(
            1.0f,
            SpeechTextNormalizer.speedFor("watermeloen", "nl-BE", 1.0f),
            0.001f
        )
    }

    @Test
    fun `missing numerator fraction becomes a Dutch question`() {
        assertEquals(
            "Hoeveel achtste",
            SpeechTextNormalizer.normalize("?/8", "nl-BE")
        )
    }

    @Test
    fun `Dutch measurement abbreviations become spoken units`() {
        assertEquals(
            "3 meter = centimeter; 2 kilogram = gram; 500 milliliter = liter; " +
                "4 minuten; 1 minuut; 6 vierkante meter; 20 gram",
            SpeechTextNormalizer.normalize(
                "3 m = ___ cm; 2 kg = ___ gr; 500 ml = ___ l; " +
                    "4 min; 1 min; 6 m²; 20 g",
                "nl-BE"
            )
        )
    }

    @Test
    fun `only Dutch vowel labels receive leading silence`() {
        val vowelLabel = SpeechTextNormalizer.normalize("eu", "nl-BE")
        val consonantLabel = SpeechTextNormalizer.normalize("m", "nl-BE")

        assertEquals(200L, SpeechTextNormalizer.leadingSilenceMillisFor(vowelLabel, "nl-BE"))
        assertEquals(0L, SpeechTextNormalizer.leadingSilenceMillisFor(consonantLabel, "nl-BE"))
        assertEquals(0L, SpeechTextNormalizer.leadingSilenceMillisFor(vowelLabel, "fr-BE"))
    }

}
