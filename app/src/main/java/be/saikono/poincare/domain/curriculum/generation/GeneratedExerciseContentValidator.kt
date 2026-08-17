package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.InputMode
import java.text.Normalizer

/**
 * Source-level quality gate for generated exercises.
 *
 * This validates what the child can actually see and hear. Generator metadata,
 * shuffled option order, and stable IDs cannot hide an ambiguous or incomplete
 * question from this gate.
 */
object GeneratedExerciseContentValidator {
    private val allLocales = listOf("nl-BE", "fr-BE", "en-US")
    private val choiceModes = setOf(InputMode.TAP_CHOICE, InputMode.LISTEN_AND_SELECT)
    private val unsafeSpokenNonwords = setOf("vsi", "beok", "chta", "fsih")
    private val forbiddenChildPlaceholders = listOf(
        "docent modelleert",
        "je benoemt het kernbegrip",
        "ik kan controleren of mijn antwoord, uitvoering of conclusie klopt",
        "artistieke kwaliteit"
    )
    private val invalidNumberAgreement = listOf(
        Regex("""\b1\s+sterren\b""", RegexOption.IGNORE_CASE),
        Regex("""\b1\s+étoiles\b""", RegexOption.IGNORE_CASE),
        Regex("""\b1\s+stars\b""", RegexOption.IGNORE_CASE),
        Regex("""\ber\s+komen\s+1\b""", RegexOption.IGNORE_CASE),
        Regex("""\bil\s+y\s+a\s+1\s+étoiles\b""", RegexOption.IGNORE_CASE),
        Regex("""\bthere\s+are\s+1\b""", RegexOption.IGNORE_CASE)
    )

    fun validate(exercise: ExerciseContent): List<String> = buildList {
        val locales = localesFor(exercise)
        if (exercise.inputMode in choiceModes) validateLocalizedChoices(exercise, this)
        // The substantive-translation gate was introduced for the Grade 1
        // content heraudit. Higher-grade banks have not yet been migrated to
        // this stricter contract and may intentionally reuse language-neutral
        // source text in localized fields.
        if (exercise.level == be.saikono.poincare.model.SkillLevel.FIRST_GRADE && locales.size > 1) {
            validateSubstantiveLocalization(exercise, this)
        }
        validateSpokenMetadata(exercise, this)
        if (exercise.inputMode == InputMode.LISTEN_AND_SELECT || exercise.data["audioRequired"] == "true") {
            validateRequiredAudio(exercise, this)
        }

        when (exercise.data["promptKind"]) {
            "missing_letter" -> locales.forEach { locale ->
                if (exercise.data["targetWord_$locale"].isNullOrBlank()) {
                    add("missing-letter question has no explicit target word for $locale")
                }
            }
            "form_word" -> if (
                exercise.data["semanticTargetProvided"] != "true" &&
                locales.none { !exercise.data["validWords_$it"].isNullOrBlank() }
            ) {
                add("word-form question has neither a semantic target nor a verified word bank")
            }
        }

        val intent = exercise.data["questionIntent"].orEmpty()
        val masteryTag = exercise.data["masteryTag"].orEmpty()
        if (intent.startsWith("PHONEME_")) {
            if (!masteryTag.startsWith("phoneme.")) add("phoneme question is not tagged as phoneme mastery")
            if (exercise.data["audioRequired"] != "true") add("phoneme question is not explicitly audio-required")
        }
        if (intent.startsWith("GRAPHEME_") && !masteryTag.startsWith("grapheme.")) {
            add("grapheme question is not tagged as grapheme mastery")
        }

        val visibleAndSpoken = buildList {
            add(exercise.prompt.nl)
            add(exercise.prompt.fr.orEmpty())
            add(exercise.prompt.en.orEmpty())
            exercise.speechText?.let {
                add(it.nl)
                add(it.fr.orEmpty())
                add(it.en.orEmpty())
            }
            addAll(exercise.data.filterKeys { key ->
                key.startsWith("options_") || key.startsWith("answer_") ||
                    key.startsWith("feedback_") || key.startsWith("hint_")
            }.values)
        }
        visibleAndSpoken.forEach { value ->
            forbiddenChildPlaceholders.firstOrNull { forbidden ->
                normalize(value).lowercase().contains(forbidden)
            }?.let { forbidden -> add("child content contains generic placeholder: $forbidden") }
        }
        invalidNumberAgreement.forEach { pattern ->
            if (visibleAndSpoken.any(pattern::containsMatchIn)) {
                add("number agreement is invalid for a singular quantity: ${pattern.pattern}")
            }
        }

        if (
            exercise.data["generatorId"]?.contains(".phonology.") == true &&
            visibleAndSpoken.any { Regex("""\bklant\b""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        ) {
            add("phonology content contains 'klant' where 'klank' is required")
        }
    }

    private fun validateLocalizedChoices(
        exercise: ExerciseContent,
        issues: MutableList<String>
    ) {
        localesFor(exercise).forEach { locale ->
            val options = splitLines(exercise.data["options_$locale"])
            val answer = exercise.data["answer_$locale"].orEmpty().trim()
            if (options.isEmpty()) {
                issues += "choice question has no options for $locale"
                return@forEach
            }
            if (answer.isBlank()) {
                issues += "choice question has no answer for $locale"
                return@forEach
            }

            val normalizedOptions = options.map(::normalize)
            if (normalizedOptions.distinct().size != normalizedOptions.size) {
                issues += "choice question contains duplicate options for $locale"
            }
            val answerMatches = normalizedOptions.count { it == normalize(answer) }
            if (answerMatches != 1) {
                issues += "choice answer must occur exactly once in $locale options (found $answerMatches)"
            }
        }
    }

    private fun validateRequiredAudio(
        exercise: ExerciseContent,
        issues: MutableList<String>
    ) {
        val speech = exercise.speechText
        localesFor(exercise).forEach { locale ->
            val promptSpeech = when (locale) {
                "nl-BE" -> speech?.nl
                "fr-BE" -> speech?.fr
                else -> speech?.en
            }
            if (promptSpeech.isNullOrBlank()) {
                issues += "audio-required question has no speechText for $locale"
            }

            val options = splitLines(exercise.data["options_$locale"])
            val optionSpeech = splitLines(exercise.data["optionSpeech_$locale"])
            if (options.isNotEmpty() && optionSpeech.size != options.size) {
                issues += "audio-required question has ${optionSpeech.size} spoken options for ${options.size} visible options in $locale"
            }
        }
    }

    private fun validateSpokenMetadata(exercise: ExerciseContent, issues: MutableList<String>) {
        localesFor(exercise).forEach { locale ->
            val unsafe = splitLines(exercise.data["optionSpeech_$locale"]).flatMap { spoken ->
                spoken.split(Regex("""[^\p{L}]+"""))
                    .map { normalize(it).lowercase() }
                    .filter { it in unsafeSpokenNonwords }
            }.distinct()
            if (unsafe.isNotEmpty()) {
                issues += "spoken option metadata contains unsafe TTS nonword(s) for $locale: ${unsafe.joinToString()}"
            }
        }
    }

    private fun validateSubstantiveLocalization(
        exercise: ExerciseContent,
        issues: MutableList<String>
    ) {
        validateLocalizedTriple(
            label = "prompt",
            nl = exercise.prompt.nl,
            fr = exercise.prompt.fr.orEmpty(),
            en = exercise.prompt.en.orEmpty(),
            issues = issues
        )
        exercise.speechText?.let { speech ->
            validateLocalizedTriple("speechText", speech.nl, speech.fr.orEmpty(), speech.en.orEmpty(), issues)
        }
        listOf("options", "answer", "feedback", "hint", "practicalTask", "observableCriteria").forEach { prefix ->
            validateLocalizedTriple(
                label = prefix,
                nl = exercise.data["${prefix}_nl-BE"].orEmpty(),
                fr = exercise.data["${prefix}_fr-BE"].orEmpty(),
                en = exercise.data["${prefix}_en-US"].orEmpty(),
                issues = issues
            )
        }
    }

    private fun validateLocalizedTriple(
        label: String,
        nl: String,
        fr: String,
        en: String,
        issues: MutableList<String>
    ) {
        if (!isSubstantive(nl)) return
        val normalizedNl = normalize(nl).lowercase()
        if (normalizedNl == normalize(fr).lowercase()) issues += "$label is substantively untranslated in fr-BE"
        if (normalizedNl == normalize(en).lowercase()) issues += "$label is substantively untranslated in en-US"
    }

    private fun isSubstantive(value: String): Boolean {
        val letters = value.count(Char::isLetter)
        val words = value.split(Regex("""\s+""")).count { token -> token.any(Char::isLetter) }
        return letters >= 18 && words >= 4
    }

    private fun splitLines(value: String?): List<String> =
        value.orEmpty().split('\n').map(String::trim).filter(String::isNotBlank)

    private fun normalize(value: String): String = Normalizer
        .normalize(value.trim(), Normalizer.Form.NFKC)
        .replace(Regex("""\s+"""), " ")

    private fun localesFor(exercise: ExerciseContent): List<String> =
        if (exercise.data["exerciseLocales"] == "nl-BE") listOf("nl-BE") else allLocales
}
