package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedExerciseContentValidatorTest {
    @Test
    fun `duplicate visible answers are rejected`() {
        val issues = GeneratedExerciseContentValidator.validate(
            exercise(
                data = localizedChoiceData(
                    optionsNl = "maan\nmaan\nvis",
                    optionsFr = "lune\nlune\npoisson",
                    optionsEn = "moon\nmoon\nfish",
                    answerNl = "maan",
                    answerFr = "lune",
                    answerEn = "moon"
                )
            )
        )

        assertTrue(issues.any { "duplicate options" in it })
        assertTrue(issues.any { "exactly once" in it })
    }

    @Test
    fun `missing letter question requires an explicit target in every locale`() {
        val issues = GeneratedExerciseContentValidator.validate(
            exercise(
                data = localizedChoiceData(
                    optionsNl = "a\ne\ni",
                    optionsFr = "a\ne\ni",
                    optionsEn = "a\ne\ni",
                    answerNl = "a",
                    answerFr = "a",
                    answerEn = "a"
                ) + ("promptKind" to "missing_letter")
            )
        )

        assertTrue(issues.count { "explicit target word" in it } == 3)
    }

    @Test
    fun `word form question requires semantic disambiguation or a verified word bank`() {
        val issues = GeneratedExerciseContentValidator.validate(
            exercise(
                data = localizedChoiceData(
                    optionsNl = "vis\nmis\nwis",
                    optionsFr = "chat\nrat\nlac",
                    optionsEn = "fish\ndish\nwish",
                    answerNl = "vis",
                    answerFr = "chat",
                    answerEn = "fish"
                ) + ("promptKind" to "form_word")
            )
        )

        assertTrue(issues.any { "semantic target" in it })
    }

    @Test
    fun `audio task rejects missing localized speech and unsafe tts nonwords`() {
        val issues = GeneratedExerciseContentValidator.validate(
            exercise(
                inputMode = InputMode.LISTEN_AND_SELECT,
                speechText = LocalizedText("Luister.", null, null),
                data = localizedChoiceData(
                    optionsNl = "vis\nmis\nsok",
                    optionsFr = "chat\nrat\nsac",
                    optionsEn = "fish\ndish\nsun",
                    answerNl = "vis",
                    answerFr = "chat",
                    answerEn = "fish"
                ) + mapOf(
                    "audioRequired" to "true",
                    "optionSpeech_nl-BE" to "vsi\nmis\nsok",
                    "optionSpeech_fr-BE" to "chta\nrat\nsac",
                    "optionSpeech_en-US" to "fsih\ndish\nsun"
                )
            )
        )

        assertTrue(issues.any { "no speechText for fr-BE" in it })
        assertTrue(issues.any { "unsafe TTS nonword" in it })
    }

    @Test
    fun `Dutch only exercise does not require French or English fields`() {
        val issues = GeneratedExerciseContentValidator.validate(
            exercise(
                speechText = LocalizedText("Luister."),
                data = mapOf(
                    "exerciseLocales" to "nl-BE",
                    "options_nl-BE" to "maan\nvis\nsok",
                    "answer_nl-BE" to "maan"
                )
            ).copy(prompt = LocalizedText("Kies het juiste woord."))
        )

        assertTrue(issues.isEmpty())
    }

    private fun exercise(
        inputMode: InputMode = InputMode.TAP_CHOICE,
        speechText: LocalizedText? = LocalizedText("Luister.", "Écoute.", "Listen."),
        data: Map<String, String>
    ): ExerciseContent = ExerciseContent(
        id = "generated.g1.audit.validator.v1.seed.1",
        domain = LearningDomain.READING,
        skill = "audit",
        section = ExerciseSection.PRACTICE,
        level = SkillLevel.FIRST_GRADE,
        inputMode = inputMode,
        prompt = LocalizedText("Kies.", "Choisis.", "Choose."),
        speechText = speechText,
        data = data,
        answer = "correct"
    )

    private fun localizedChoiceData(
        optionsNl: String,
        optionsFr: String,
        optionsEn: String,
        answerNl: String,
        answerFr: String,
        answerEn: String
    ): Map<String, String> = mapOf(
        "options_nl-BE" to optionsNl,
        "options_fr-BE" to optionsFr,
        "options_en-US" to optionsEn,
        "answer_nl-BE" to answerNl,
        "answer_fr-BE" to answerFr,
        "answer_en-US" to answerEn
    )
}
