package be.saikono.poincare.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentModelsSpeechTest {
    @Test
    fun `Dutch first sound consonant is spoken as phoneme instead of alphabet name`() {
        val exercise = exercise("Welke eerste klank hoor je in maan?", "m", "em")

        assertEquals("mmm, zoals in maan", exercise.localizedOptionSpeech("m", "nl-BE"))
    }

    @Test
    fun `Dutch last sound consonant follows the same phoneme rule`() {
        val exercise = exercise("Welke laatste letter hoor je in kat?", "t", "tee")

        assertEquals("tuh, zoals in tak", exercise.localizedOptionSpeech("t", "nl"))
    }

    @Test
    fun `ordinary letter question keeps its explicit alphabet pronunciation`() {
        val exercise = exercise("Welke letter zie je?", "m", "em")

        assertEquals("em", exercise.localizedOptionSpeech("m", "nl-BE"))
    }

    private fun exercise(promptText: String, option: String, speech: String) = ExerciseContent(
        id = "speech-test",
        domain = LearningDomain.READING,
        skill = "initial_sound",
        section = ExerciseSection.PRACTICE,
        level = SkillLevel.FIRST_GRADE,
        inputMode = InputMode.TAP_CHOICE,
        prompt = LocalizedText(nl = promptText),
        data = mapOf("options" to option, "optionSpeech" to speech),
        answer = option
    )
}
