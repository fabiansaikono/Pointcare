package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 1 technical-reading generator.
 *
 * Version 1 uses tap-choice exercises for visual word recognition, elementary
 * decoding, missing letters, vowel patterns, listening-to-word matching, and
 * short sentence completion. Localized content may use different words per
 * language while retaining a stable, language-neutral answer key.
 */
object Grade1TechnicalReadingGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g1.language.technical_reading.decoding"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g1.language.technical_reading.decoding"
    private val allowedParameterKeys = setOf(
        "grade",
        "roadmap_entry",
        "phase",
        "editorial_status",
        "unit_id",
        "reading_focus"
    )

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["grade"] != "1") add("grade must be 1")
        if (parameters["roadmap_entry"] != ROADMAP_ENTRY) {
            add("roadmap_entry must be $ROADMAP_ENTRY")
        }
        if (parameters["phase"] !in setOf("guided", "checkpoint")) {
            add("phase must be guided or checkpoint")
        }
        if (parameters["unit_id"].isNullOrBlank()) add("unit_id is required")
        if (parameters["reading_focus"] !in FOCUS_KINDS.keys) add("reading_focus is not supported")
        if (parameters["editorial_status"] != LessonGeneratorRegistry.APPROVED_STATUS) {
            add("editorial_status must be approved")
        }
    }

    override fun generate(
        seed: LessonGenerationSeed,
        exerciseCount: Int,
        parameters: Map<String, String>
    ): List<ExerciseContent> {
        val issues = validate(parameters)
        require(issues.isEmpty()) { issues.joinToString("; ") }
        require(exerciseCount > 0) { "exerciseCount must be positive" }

        val phase = Phase.valueOf(parameters.getValue("phase").uppercase())
        val focus = parameters.getValue("reading_focus")
        val kinds = FOCUS_KINDS.getValue(focus)
        val random = StableLessonRandom(seed.value)
        val used = linkedSetOf<String>()
        val offset = if (phase == Phase.GUIDED) 0 else random.nextInt(kinds.size)

        return List(exerciseCount) { index ->
            val kind = kinds[(index + offset) % kinds.size]
            val candidate = uniqueCandidate(kind, random, used)
            exercise(seed, index, phase, focus, candidate)
        }
    }

    private fun uniqueCandidate(
        kind: PromptKind,
        random: StableLessonRandom,
        used: MutableSet<String>
    ): Candidate {
        val bank = bank(kind)
        repeat(64) {
            val templateIndex = random.nextInt(bank.size)
            val template = bank[templateIndex]
            val options = shuffled(template.options, random)
            val candidate = Candidate(
                kind = kind,
                templateIndex = templateIndex,
                prompt = template.prompt,
                speech = template.speech,
                options = options,
                answerKey = template.answerKey
            )
            if (used.add(candidate.signature())) return candidate
        }

        bank.forEachIndexed { templateIndex, template ->
            template.options.indices.forEach { rotation ->
                val options = template.options.drop(rotation) + template.options.take(rotation)
                val candidate = Candidate(
                    kind = kind,
                    templateIndex = templateIndex,
                    prompt = template.prompt,
                    speech = template.speech,
                    options = options,
                    answerKey = template.answerKey
                )
                if (used.add(candidate.signature())) return candidate
            }
        }
        error("Unable to create a unique Grade 1 technical-reading exercise for ${kind.name}")
    }

    private fun <T> shuffled(values: List<T>, random: StableLessonRandom): List<T> {
        val result = values.toMutableList()
        for (index in result.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val value = result[index]
            result[index] = result[other]
            result[other] = value
        }
        return result
    }

    private fun exercise(
        seed: LessonGenerationSeed,
        index: Int,
        phase: Phase,
        focus: String,
        candidate: Candidate
    ): ExerciseContent {
        val answer = candidate.options.single { it.key == candidate.answerKey }
        val id = "generated.g1.language.technical_reading.v1.${seed.fingerprint}.${index + 1}"
        return ExerciseContent(
            id = id,
            domain = LearningDomain.READING,
            skill = "technical_reading",
            section = if (phase == Phase.GUIDED) ExerciseSection.PRACTICE else ExerciseSection.TESTS,
            level = SkillLevel.FIRST_GRADE,
            inputMode = if (candidate.kind == PromptKind.LISTEN_AND_FIND_WORD) {
                InputMode.LISTEN_AND_SELECT
            } else {
                InputMode.TAP_CHOICE
            },
            prompt = candidate.prompt,
            speechText = candidate.speech,
            data = linkedMapOf(
                "generatorId" to generatorId,
                "generatorVersion" to generatorVersion.toString(),
                "seedFingerprint" to seed.fingerprint,
                "roadmapEntry" to ROADMAP_ENTRY,
                "phase" to phase.name.lowercase(),
                "readingFocus" to focus,
                "promptKind" to candidate.kind.name.lowercase(),
                "contentKey" to "${candidate.kind.name.lowercase()}.${candidate.templateIndex + 1}",
                "masteryTag" to masteryTag(candidate.kind),
                "errorTags" to errorTag(candidate.kind),
                "audioRequired" to (candidate.kind == PromptKind.LISTEN_AND_FIND_WORD).toString(),
                "answerRole" to "choice",
                "options_nl-BE" to candidate.options.joinToString("\n") { it.nl },
                "options_fr-BE" to candidate.options.joinToString("\n") { it.fr },
                "options_en-US" to candidate.options.joinToString("\n") { it.en },
                "answer_nl-BE" to answer.nl,
                "answer_fr-BE" to answer.fr,
                "answer_en-US" to answer.en,
                "hint_nl-BE" to hintNl(candidate.kind),
                "hint_fr-BE" to hintFr(candidate.kind),
                "hint_en-US" to hintEn(candidate.kind),
                "feedback_nl-BE" to hintNl(candidate.kind),
                "feedback_fr-BE" to hintFr(candidate.kind),
                "feedback_en-US" to hintEn(candidate.kind)
            ).apply {
                if (candidate.kind in setOf(PromptKind.LISTEN_AND_FIND_WORD, PromptKind.MISSING_LETTER)) {
                    put("optionSpeech_nl-BE", candidate.options.joinToString("\n") { it.speechNl ?: it.nl })
                    put("optionSpeech_fr-BE", candidate.options.joinToString("\n") { it.speechFr ?: it.fr })
                    put("optionSpeech_en-US", candidate.options.joinToString("\n") { it.speechEn ?: it.en })
                }
                if (candidate.kind == PromptKind.MISSING_LETTER) {
                    if (candidate.templateIndex == 0) {
                        put("targetWord_nl-BE", "maan")
                        put("targetWord_fr-BE", "lune")
                        put("targetWord_en-US", "moon")
                    } else {
                        put("targetWord_nl-BE", "vis")
                        put("targetWord_fr-BE", "chat")
                        put("targetWord_en-US", "fish")
                    }
                }
            },
            answer = candidate.answerKey
        )
    }

    private fun bank(kind: PromptKind): List<Template> = when (kind) {
        PromptKind.MATCH_IDENTICAL_WORD -> listOf(
            template(
                localized("Zoek precies hetzelfde woord als maan.", "Trouve exactement le même mot que lune.", "Find exactly the same word as moon."),
                localized("Zoek precies hetzelfde woord als maan", "Trouve exactement le même mot que lune", "Find exactly the same word as moon"),
                "same_word",
                choice("same_word", "maan", "lune", "moon"),
                choice("near_word_1", "naam", "lunee", "moom"),
                choice("near_word_2", "man", "luen", "noon")
            ),
            template(
                localized("Zoek precies hetzelfde woord als boek.", "Trouve exactement le même mot que livre.", "Find exactly the same word as book."),
                localized("Zoek precies hetzelfde woord als boek", "Trouve exactement le même mot que livre", "Find exactly the same word as book"),
                "same_word",
                choice("same_word", "boek", "livre", "book"),
                choice("near_word_1", "boel", "livr", "boon"),
                choice("near_word_2", "beok", "liver", "cook")
            ),
            template(
                localized("Zoek precies hetzelfde woord als vis.", "Trouve exactement le même mot que poisson.", "Find exactly the same word as fish."),
                localized("Zoek precies hetzelfde woord als vis", "Trouve exactement le même mot que poisson", "Find exactly the same word as fish"),
                "same_word",
                choice("same_word", "vis", "poisson", "fish"),
                choice("near_word_1", "vos", "poison", "dish"),
                choice("near_word_2", "vsi", "poussin", "wish")
            )
        )

        PromptKind.BLEND_WORD -> listOf(
            template(
                localized("Welk woord lees je bij m – aa – n?", "Quel mot lis-tu avec l – u – n – e ?", "Which word do you read from m – oo – n?"),
                localized("Welk woord lees je bij de klanken m, aa en n", "Quel mot lis-tu avec les sons l, u, n et e", "Which word do you read from the sounds m, oo, and n"),
                "blended_word",
                choice("blended_word", "maan", "lune", "moon"),
                choice("different_1", "man", "lundi", "mon"),
                choice("different_2", "naam", "lupe", "noon")
            ),
            template(
                localized("Welk woord lees je bij b – oe – k?", "Quel mot lis-tu avec l – i – v – r – e ?", "Which word do you read from b – oo – k?"),
                localized("Welk woord lees je bij de klanken b, oe en k", "Quel mot lis-tu avec les sons l, i, v, r et e", "Which word do you read from the sounds b, oo, and k"),
                "blended_word",
                choice("blended_word", "boek", "livre", "book"),
                choice("different_1", "bek", "libre", "back"),
                choice("different_2", "boer", "vivre", "boot")
            ),
            template(
                localized("Welk woord lees je bij s – o – k?", "Quel mot lis-tu avec s – a – c ?", "Which word do you read from s – u – n?"),
                localized("Welk woord lees je bij de klanken s, o en k", "Quel mot lis-tu avec les sons s, a et c", "Which word do you read from the sounds s, u, and n"),
                "blended_word",
                choice("blended_word", "sok", "sac", "sun"),
                choice("different_1", "soep", "sec", "son"),
                choice("different_2", "rok", "sel", "run")
            )
        )

        PromptKind.MISSING_LETTER -> listOf(
            template(
                localized("Welke letter ontbreekt in m_an zodat er maan staat?", "Quelle lettre manque dans l_ne pour former lune ?", "Which letter is missing in m_on to make moon?"),
                localized("Welke letter ontbreekt in m aan zodat er maan staat", "Quelle lettre manque dans l ne pour former lune", "Which letter is missing in m on to make moon"),
                "letter_a",
                choice("letter_a", "a", "u", "o", "aa", "u", "oo"),
                choice("letter_e", "e", "e", "e", "ee", "e", "ee"),
                choice("letter_i", "i", "i", "i", "ie", "i", "eye")
            ),
            template(
                localized("Welke letter ontbreekt in _is zodat er vis staat?", "Quelle lettre manque dans _oisson pour former poisson ?", "Which letter is missing in _ish to make fish?"),
                localized("Welke letter ontbreekt in is zodat er vis staat", "Quelle lettre manque dans oisson pour former poisson", "Which letter is missing in ish to make fish"),
                "letter_first",
                choice("letter_first", "v", "p", "f", "vee", "pé", "eff"),
                choice("letter_other_1", "m", "b", "d", "em", "bé", "dee"),
                choice("letter_other_2", "s", "t", "w", "es", "té", "double u")
            ),
            template(
                localized("Welke letter ontbreekt in boe_ zodat er boek staat?", "Quelle lettre manque dans liv_e pour former livre ?", "Which letter is missing in boo_ to make book?"),
                localized("Welke letter ontbreekt in boe zodat er boek staat", "Quelle lettre manque dans liv e pour former livre", "Which letter is missing in boo to make book"),
                "letter_last",
                choice("letter_last", "k", "r", "k", "kaa", "erre", "kay"),
                choice("letter_other_1", "t", "n", "t", "tee", "enne", "tee"),
                choice("letter_other_2", "m", "s", "m", "em", "esse", "em")
            )
        )

        PromptKind.VOWEL_PATTERN -> listOf(
            template(
                localized("Welk woord bevat aa?", "Quel mot contient ou ?", "Which word contains oo?"),
                localized("Welk woord bevat de klank aa", "Quel mot contient les lettres ou", "Which word contains the letters oo"),
                "pattern_word",
                choice("pattern_word", "maan", "roue", "moon"),
                choice("different_1", "man", "rue", "mon"),
                choice("different_2", "min", "rat", "map")
            ),
            template(
                localized("Welk woord bevat oe?", "Quel mot contient eau ?", "Which word contains ee?"),
                localized("Welk woord bevat de klank oe", "Quel mot contient les lettres eau", "Which word contains the letters ee"),
                "pattern_word",
                choice("pattern_word", "boek", "bateau", "tree"),
                choice("different_1", "bek", "battu", "try"),
                choice("different_2", "bak", "botte", "train")
            ),
            template(
                localized("Welk woord bevat ui?", "Quel mot contient ai ?", "Which word contains sh?"),
                localized("Welk woord bevat de klank ui", "Quel mot contient les lettres ai", "Which word contains the letters sh"),
                "pattern_word",
                choice("pattern_word", "muis", "maison", "fish"),
                choice("different_1", "mis", "melon", "fin"),
                choice("different_2", "mus", "mouton", "fit")
            )
        )

        PromptKind.LISTEN_AND_FIND_WORD -> listOf(
            template(
                localized("Luister en kies het woord.", "Écoute et choisis le mot.", "Listen and choose the word."),
                localized("Kies het woord maan", "Choisis le mot lune", "Choose the word moon"),
                "spoken_word",
                choice("spoken_word", "maan", "lune", "moon"),
                choice("different_1", "man", "lundi", "moom"),
                choice("different_2", "naam", "lupe", "noon")
            ),
            template(
                localized("Luister en kies het woord.", "Écoute et choisis le mot.", "Listen and choose the word."),
                localized("Kies het woord boek", "Choisis le mot livre", "Choose the word book"),
                "spoken_word",
                choice("spoken_word", "boek", "livre", "book"),
                choice("different_1", "boel", "libre", "boon"),
                choice("different_2", "hoek", "vivre", "cook")
            ),
            template(
                localized("Luister en kies het woord.", "Écoute et choisis le mot.", "Listen and choose the word."),
                localized("Kies het woord vis", "Choisis le mot poisson", "Choose the word fish"),
                "spoken_word",
                choice("spoken_word", "vis", "poisson", "fish"),
                choice("different_1", "vos", "poison", "dish"),
                choice("different_2", "mis", "poussin", "wish")
            )
        )

        PromptKind.COMPLETE_SENTENCE -> listOf(
            template(
                localized("Lees de zin: De kat zit op de ___.", "Lis la phrase : Le chat dort sur le ___.", "Read the sentence: The cat sits on the ___."),
                localized("Lees de zin. De kat zit op de ontbrekende plek", "Lis la phrase. Le chat dort sur le mot manquant", "Read the sentence. The cat sits on the missing word"),
                "sentence_word",
                choice("sentence_word", "mat", "lit", "mat"),
                choice("different_1", "maan", "lune", "moon"),
                choice("different_2", "vis", "poisson", "fish")
            ),
            template(
                localized("Lees de zin: Ik lees een ___.", "Lis la phrase : Je lis un ___.", "Read the sentence: I read a ___."),
                localized("Lees de zin. Ik lees een ontbrekend woord", "Lis la phrase. Je lis un mot manquant", "Read the sentence. I read a missing word"),
                "sentence_word",
                choice("sentence_word", "boek", "livre", "book"),
                choice("different_1", "boom", "arbre", "tree"),
                choice("different_2", "sok", "chaussette", "sock")
            ),
            template(
                localized("Lees de zin: De vis zwemt in de ___.", "Lis la phrase : Le poisson nage dans l'___.", "Read the sentence: The fish swims in the ___."),
                localized("Lees de zin. De vis zwemt in de ontbrekende plek", "Lis la phrase. Le poisson nage dans le mot manquant", "Read the sentence. The fish swims in the missing word"),
                "sentence_word",
                choice("sentence_word", "kom", "eau", "pond"),
                choice("different_1", "maan", "lune", "moon"),
                choice("different_2", "boek", "livre", "book")
            )
        )
    }

    private fun localized(nl: String, fr: String, en: String): LocalizedText =
        LocalizedText(nl = nl, fr = fr, en = en)

    private fun template(
        prompt: LocalizedText,
        speech: LocalizedText,
        answerKey: String,
        vararg options: Choice
    ): Template = Template(prompt, speech, options.toList(), answerKey)

    private fun choice(
        key: String,
        nl: String,
        fr: String,
        en: String,
        speechNl: String? = null,
        speechFr: String? = null,
        speechEn: String? = null
    ): Choice = Choice(key, nl, fr, en, speechNl, speechFr, speechEn)

    private fun hintNl(kind: PromptKind): String = when (kind) {
        PromptKind.MATCH_IDENTICAL_WORD -> "Kijk letter voor letter."
        PromptKind.BLEND_WORD -> "Zeg de klanken langzaam en daarna samen."
        PromptKind.MISSING_LETTER -> "Lees het woord met elke letter en kies wat klopt."
        PromptKind.VOWEL_PATTERN -> "Zoek precies dezelfde lettergroep."
        PromptKind.LISTEN_AND_FIND_WORD -> "Luister eerst, kijk daarna naar alle letters."
        PromptKind.COMPLETE_SENTENCE -> "Lees de hele zin en kies het woord dat past."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.MATCH_IDENTICAL_WORD -> "Regarde lettre par lettre."
        PromptKind.BLEND_WORD -> "Dis les sons lentement, puis assemble-les."
        PromptKind.MISSING_LETTER -> "Lis le mot avec chaque choix et garde celui qui convient."
        PromptKind.VOWEL_PATTERN -> "Cherche exactement le même groupe de lettres."
        PromptKind.LISTEN_AND_FIND_WORD -> "Écoute d'abord, puis regarde toutes les lettres."
        PromptKind.COMPLETE_SENTENCE -> "Lis toute la phrase et choisis le mot qui convient."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.MATCH_IDENTICAL_WORD -> "Look at every letter."
        PromptKind.BLEND_WORD -> "Say the sounds slowly and then blend them."
        PromptKind.MISSING_LETTER -> "Read the word with each choice and keep the one that works."
        PromptKind.VOWEL_PATTERN -> "Find exactly the same letter pattern."
        PromptKind.LISTEN_AND_FIND_WORD -> "Listen first, then look at every letter."
        PromptKind.COMPLETE_SENTENCE -> "Read the whole sentence and choose the word that fits."
    }

    private fun masteryTag(kind: PromptKind): String = when (kind) {
        PromptKind.MATCH_IDENTICAL_WORD -> "grapheme.exact_word_match"
        PromptKind.BLEND_WORD -> "decoding.blend_word"
        PromptKind.MISSING_LETTER -> "decoding.missing_letter"
        PromptKind.VOWEL_PATTERN -> "grapheme.vowel_pattern"
        PromptKind.LISTEN_AND_FIND_WORD -> "phoneme.spoken_word_match"
        PromptKind.COMPLETE_SENTENCE -> "decoding.sentence_completion"
    }

    private fun errorTag(kind: PromptKind): String = when (kind) {
        PromptKind.MATCH_IDENTICAL_WORD -> "confuses_letter_order"
        PromptKind.BLEND_WORD -> "cannot_blend_graphemes"
        PromptKind.MISSING_LETTER -> "selects_wrong_grapheme"
        PromptKind.VOWEL_PATTERN -> "confuses_vowel_pattern"
        PromptKind.LISTEN_AND_FIND_WORD -> "confuses_spoken_word"
        PromptKind.COMPLETE_SENTENCE -> "selects_context_mismatch"
    }

    private data class Candidate(
        val kind: PromptKind,
        val templateIndex: Int,
        val prompt: LocalizedText,
        val speech: LocalizedText,
        val options: List<Choice>,
        val answerKey: String
    ) {
        fun signature(): String =
            "${kind.name}:$templateIndex:${options.joinToString(":") { it.key }}"
    }

    private data class Template(
        val prompt: LocalizedText,
        val speech: LocalizedText,
        val options: List<Choice>,
        val answerKey: String
    )

    private data class Choice(
        val key: String,
        val nl: String,
        val fr: String,
        val en: String,
        val speechNl: String? = null,
        val speechFr: String? = null,
        val speechEn: String? = null
    )

    private enum class Phase { GUIDED, CHECKPOINT }

    private enum class PromptKind {
        MATCH_IDENTICAL_WORD,
        BLEND_WORD,
        MISSING_LETTER,
        VOWEL_PATTERN,
        LISTEN_AND_FIND_WORD,
        COMPLETE_SENTENCE
    }

    private val FOCUS_KINDS = mapOf(
        "mkm" to listOf(PromptKind.BLEND_WORD, PromptKind.MISSING_LETTER, PromptKind.LISTEN_AND_FIND_WORD),
        "vowel_teams" to listOf(PromptKind.VOWEL_PATTERN, PromptKind.MISSING_LETTER, PromptKind.MATCH_IDENTICAL_WORD),
        "consonant_clusters" to listOf(PromptKind.BLEND_WORD, PromptKind.MATCH_IDENTICAL_WORD, PromptKind.COMPLETE_SENTENCE),
        "frequent_words" to listOf(PromptKind.MATCH_IDENTICAL_WORD, PromptKind.COMPLETE_SENTENCE, PromptKind.LISTEN_AND_FIND_WORD)
    )
}
