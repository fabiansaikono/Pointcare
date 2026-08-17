package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 1 phonological and letter-sound awareness generator.
 *
 * Version 1 uses tap-choice exercises for rhyme, syllables, first and last
 * sounds, sound blending, and elementary letter-sound matching. Localized
 * content may use different words per language while retaining a stable,
 * language-neutral answer key.
 */
object Grade1PhonologyLetterSoundGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g1.spelling.phonology.sound_letter"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g1.spelling.phonology.sound_letter"
    private val allowedParameterKeys = setOf(
        "grade",
        "roadmap_entry",
        "phase",
        "focus",
        "editorial_status"
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
        if (parameters["focus"] !in setOf(null, "mixed", "first_sound")) {
            add("focus must be mixed or first_sound")
        }
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
        val random = StableLessonRandom(seed.value)
        val used = linkedSetOf<String>()
        val promptKinds = if (parameters["focus"] == "first_sound") {
            listOf(PromptKind.FIRST_SOUND, PromptKind.LETTER_SOUND)
        } else {
            PromptKind.entries
        }
        val offset = if (phase == Phase.GUIDED) 0 else random.nextInt(promptKinds.size)

        return List(exerciseCount) { index ->
            val kind = promptKinds[(index + offset) % promptKinds.size]
            val candidate = uniqueCandidate(kind, random, used)
            exercise(seed, index, phase, candidate)
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
        error("Unable to create a unique Grade 1 phonology exercise for ${kind.name}")
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
        candidate: Candidate
    ): ExerciseContent {
        val answer = candidate.options.single { it.key == candidate.answerKey }
        val id = "generated.g1.spelling.phonology.v1.${seed.fingerprint}.${index + 1}"
        return ExerciseContent(
            id = id,
            domain = LearningDomain.WRITING,
            skill = "phonology_letter_sound",
            section = if (phase == Phase.GUIDED) ExerciseSection.PRACTICE else ExerciseSection.TESTS,
            level = SkillLevel.FIRST_GRADE,
            inputMode = if (candidate.kind == PromptKind.LETTER_SOUND) {
                InputMode.TAP_CHOICE
            } else {
                InputMode.LISTEN_AND_SELECT
            },
            prompt = candidate.prompt,
            speechText = candidate.speech,
            data = linkedMapOf(
                "generatorId" to generatorId,
                "generatorVersion" to generatorVersion.toString(),
                "seedFingerprint" to seed.fingerprint,
                "roadmapEntry" to ROADMAP_ENTRY,
                "phase" to phase.name.lowercase(),
                "promptKind" to candidate.kind.name.lowercase(),
                "contentKey" to "${candidate.kind.name.lowercase()}.${candidate.templateIndex + 1}",
                "masteryTag" to masteryTag(candidate.kind),
                "errorTags" to errorTags(candidate.kind),
                "answerRole" to "choice",
                "audioRequired" to (candidate.kind != PromptKind.LETTER_SOUND).toString(),
                "questionIntent" to questionIntent(candidate.kind),
                "options_nl-BE" to candidate.options.joinToString("\n") { it.nl },
                "options_fr-BE" to candidate.options.joinToString("\n") { it.fr },
                "options_en-US" to candidate.options.joinToString("\n") { it.en },
                "optionSpeech_nl-BE" to candidate.options.joinToString("\n") { it.speechNl ?: it.nl },
                "optionSpeech_fr-BE" to candidate.options.joinToString("\n") { it.speechFr ?: it.fr },
                "optionSpeech_en-US" to candidate.options.joinToString("\n") { it.speechEn ?: it.en },
                "answer_nl-BE" to answer.nl,
                "answer_fr-BE" to answer.fr,
                "answer_en-US" to answer.en,
                "hint_nl-BE" to hintNl(candidate.kind),
                "hint_fr-BE" to hintFr(candidate.kind),
                "hint_en-US" to hintEn(candidate.kind),
                "feedback_nl-BE" to hintNl(candidate.kind),
                "feedback_fr-BE" to hintFr(candidate.kind),
                "feedback_en-US" to hintEn(candidate.kind)
            ),
            answer = candidate.answerKey
        )
    }

    private fun bank(kind: PromptKind): List<Template> = when (kind) {
        PromptKind.RHYME -> listOf(
            template(
                prompt = localized(
                    "Welk woord rijmt op kat?",
                    "Quel mot rime avec chat ?",
                    "Which word rhymes with cat?"
                ),
                speech = localized(
                    "Welk woord rijmt op kat",
                    "Quel mot rime avec chat",
                    "Which word rhymes with cat"
                ),
                answerKey = "rhyme",
                choice("rhyme", "mat", "rat", "hat"),
                choice("tree", "boom", "lune", "tree"),
                choice("fish", "vis", "poisson", "fish")
            ),
            template(
                prompt = localized(
                    "Welk woord rijmt op maan?",
                    "Quel mot rime avec bateau ?",
                    "Which word rhymes with moon?"
                ),
                speech = localized(
                    "Welk woord rijmt op maan",
                    "Quel mot rime avec bateau",
                    "Which word rhymes with moon"
                ),
                answerKey = "rhyme",
                choice("rhyme", "haan", "château", "spoon"),
                choice("book", "boek", "livre", "book"),
                choice("sun", "zon", "soleil", "sun")
            )
        )

        PromptKind.SYLLABLE_COUNT -> listOf(
            template(
                prompt = localized(
                    "Hoeveel klankgroepen hoor je in robot?",
                    "Combien de syllabes entends-tu dans robot ?",
                    "How many syllables do you hear in robot?"
                ),
                speech = localized(
                    "Hoeveel klankgroepen hoor je in robot",
                    "Combien de syllabes entends-tu dans robot",
                    "How many syllables do you hear in robot"
                ),
                answerKey = "count_2",
                choice("count_1", "1", "1", "1"),
                choice("count_2", "2", "2", "2"),
                choice("count_3", "3", "3", "3")
            ),
            template(
                prompt = localized(
                    "Hoeveel klankgroepen hoor je in radio?",
                    "Combien de syllabes entends-tu dans radio ?",
                    "How many syllables do you hear in radio?"
                ),
                speech = localized(
                    "Hoeveel klankgroepen hoor je in radio",
                    "Combien de syllabes entends-tu dans radio",
                    "How many syllables do you hear in radio"
                ),
                answerKey = "count_3",
                choice("count_1", "1", "1", "1"),
                choice("count_2", "2", "2", "2"),
                choice("count_3", "3", "3", "3")
            )
        )

        PromptKind.FIRST_SOUND -> listOf(
            template(
                prompt = localized(
                    "Welk woord heeft dezelfde beginklank als maan?",
                    "Quel mot a le même son initial que maman ?",
                    "Which word has the same initial sound as moon?"
                ),
                speech = localized(
                    "Luister goed. Welk woord heeft dezelfde beginklank als maan",
                    "Écoute bien. Quel mot a le même son initial que maman",
                    "Listen carefully. Which word has the same initial sound as moon"
                ),
                answerKey = "same_sound",
                choice("same_sound", "muis", "moto", "mouse"),
                choice("different_1", "kat", "chat", "cat"),
                choice("different_2", "vis", "soleil", "fish")
            ),
            template(
                prompt = localized(
                    "Welk woord heeft dezelfde beginklank als sok?",
                    "Quel mot a le même son initial que soleil ?",
                    "Which word has the same initial sound as sun?"
                ),
                speech = localized(
                    "Luister goed. Welk woord heeft dezelfde beginklank als sok",
                    "Écoute bien. Quel mot a le même son initial que soleil",
                    "Listen carefully. Which word has the same initial sound as sun"
                ),
                answerKey = "same_sound",
                choice("same_sound", "sap", "sac", "sock"),
                choice("different_1", "maan", "moto", "moon"),
                choice("different_2", "tak", "tasse", "tree")
            )
        )

        PromptKind.LAST_SOUND -> listOf(
            template(
                prompt = localized(
                    "Welk woord heeft dezelfde eindklank als maan?",
                    "Quel mot a le même son final que lune ?",
                    "Which word has the same final sound as moon?"
                ),
                speech = localized(
                    "Luister goed. Welk woord heeft dezelfde eindklank als maan",
                    "Écoute bien. Quel mot a le même son final que lune",
                    "Listen carefully. Which word has the same final sound as moon"
                ),
                answerKey = "same_sound",
                choice("same_sound", "been", "banane", "spoon"),
                choice("different_1", "boek", "vélo", "book"),
                choice("different_2", "kat", "chat", "cat")
            ),
            template(
                prompt = localized(
                    "Welk woord heeft dezelfde eindklank als bus?",
                    "Quel mot a le même son final que bus ?",
                    "Which word has the same final sound as bus?"
                ),
                speech = localized(
                    "Luister goed. Welk woord heeft dezelfde eindklank als bus",
                    "Écoute bien. Quel mot a le même son final que bus",
                    "Listen carefully. Which word has the same final sound as bus"
                ),
                answerKey = "same_sound",
                choice("same_sound", "jas", "tasse", "glass"),
                choice("different_1", "maan", "lune", "moon"),
                choice("different_2", "boek", "livre", "book")
            )
        )

        PromptKind.BLEND_SOUNDS -> listOf(
            template(
                prompt = localized(
                    "Welk woord hoor je als je m – aa – n samenvoegt?",
                    "Quel mot entends-tu quand tu assembles l – u – n ?",
                    "Which word do you hear when you blend m – oo – n?"
                ),
                speech = localized(
                    "Welk woord hoor je als je de klanken m, aa en n samenvoegt",
                    "Quel mot entends-tu quand tu assembles les sons l, u et n",
                    "Which word do you hear when you blend the sounds m, oo, and n"
                ),
                answerKey = "blended_word",
                choice("blended_word", "maan", "lune", "moon"),
                choice("different_1", "mat", "livre", "mouse"),
                choice("different_2", "vis", "sac", "sun")
            ),
            template(
                prompt = localized(
                    "Welk woord hoor je als je s – o – k samenvoegt?",
                    "Quel mot entends-tu quand tu assembles s – a – c ?",
                    "Which word do you hear when you blend s – u – n?"
                ),
                speech = localized(
                    "Welk woord hoor je als je de klanken s, o en k samenvoegt",
                    "Quel mot entends-tu quand tu assembles les sons s, a et c",
                    "Which word do you hear when you blend the sounds s, u, and n"
                ),
                answerKey = "blended_word",
                choice("blended_word", "sok", "sac", "sun"),
                choice("different_1", "sap", "chat", "sock"),
                choice("different_2", "maan", "lune", "moon")
            )
        )

        PromptKind.LETTER_SOUND -> listOf(
            template(
                prompt = localized(
                    "Welke letter staat vooraan in maan?",
                    "Quelle lettre se trouve au début de maman ?",
                    "Which letter is at the start of moon?"
                ),
                speech = localized(
                    "Kijk naar het woord maan. Welke letter staat vooraan",
                    "Regarde le mot maman. Quelle lettre se trouve au début",
                    "Look at the word moon. Which letter is at the start"
                ),
                answerKey = "letter_m",
                choice("letter_m", "m", "m", "m", "em", "ème", "em"),
                choice("letter_s", "s", "s", "s", "es", "esse", "ess"),
                choice("letter_t", "t", "t", "t", "tee", "té", "tee")
            ),
            template(
                prompt = localized(
                    "Welke letter staat vooraan in sok?",
                    "Quelle lettre se trouve au début de soleil ?",
                    "Which letter is at the start of sun?"
                ),
                speech = localized(
                    "Kijk naar het woord sok. Welke letter staat vooraan",
                    "Regarde le mot soleil. Quelle lettre se trouve au début",
                    "Look at the word sun. Which letter is at the start"
                ),
                answerKey = "letter_s",
                choice("letter_s", "s", "s", "s", "es", "esse", "ess"),
                choice("letter_m", "m", "m", "m", "em", "ème", "em"),
                choice("letter_t", "t", "t", "t", "tee", "té", "tee")
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
    ): Template = Template(
        prompt = prompt,
        speech = speech,
        options = options.toList(),
        answerKey = answerKey
    )

    private fun choice(
        key: String,
        nl: String,
        fr: String,
        en: String,
        speechNl: String? = null,
        speechFr: String? = null,
        speechEn: String? = null
    ): Choice = Choice(
        key = key,
        nl = nl,
        fr = fr,
        en = en,
        speechNl = speechNl,
        speechFr = speechFr,
        speechEn = speechEn
    )

    private fun hintNl(kind: PromptKind): String = when (kind) {
        PromptKind.RHYME -> "Luister naar het einde van de woorden."
        PromptKind.SYLLABLE_COUNT -> "Klap één keer voor elke klankgroep."
        PromptKind.FIRST_SOUND -> "Luister alleen naar de eerste klank."
        PromptKind.LAST_SOUND -> "Luister alleen naar de laatste klank."
        PromptKind.BLEND_SOUNDS -> "Zeg de klanken langzaam en daarna samen."
        PromptKind.LETTER_SOUND -> "Kijk naar de eerste geschreven letter van het woord."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.RHYME -> "Écoute la fin des mots."
        PromptKind.SYLLABLE_COUNT -> "Frappe dans tes mains une fois par syllabe."
        PromptKind.FIRST_SOUND -> "Écoute seulement le premier son."
        PromptKind.LAST_SOUND -> "Écoute seulement le dernier son."
        PromptKind.BLEND_SOUNDS -> "Dis les sons lentement, puis assemble-les."
        PromptKind.LETTER_SOUND -> "Regarde la première lettre écrite du mot."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.RHYME -> "Listen to the ends of the words."
        PromptKind.SYLLABLE_COUNT -> "Clap once for each syllable."
        PromptKind.FIRST_SOUND -> "Listen only to the first sound."
        PromptKind.LAST_SOUND -> "Listen only to the last sound."
        PromptKind.BLEND_SOUNDS -> "Say the sounds slowly and then blend them."
        PromptKind.LETTER_SOUND -> "Look at the first written letter of the word."
    }

    private fun masteryTag(kind: PromptKind): String = when (kind) {
        PromptKind.RHYME -> "phoneme.rhyme"
        PromptKind.SYLLABLE_COUNT -> "phoneme.syllable_count"
        PromptKind.FIRST_SOUND -> "phoneme.initial"
        PromptKind.LAST_SOUND -> "phoneme.final"
        PromptKind.BLEND_SOUNDS -> "phoneme.blending"
        PromptKind.LETTER_SOUND -> "grapheme.initial_letter"
    }

    private fun errorTags(kind: PromptKind): String = when (kind) {
        PromptKind.RHYME -> "confuses_rhyme"
        PromptKind.SYLLABLE_COUNT -> "miscounts_syllables"
        PromptKind.FIRST_SOUND -> "confuses_initial_phoneme"
        PromptKind.LAST_SOUND -> "confuses_final_phoneme"
        PromptKind.BLEND_SOUNDS -> "cannot_blend_phonemes"
        PromptKind.LETTER_SOUND -> "confuses_initial_grapheme"
    }

    private fun questionIntent(kind: PromptKind): String = when (kind) {
        PromptKind.RHYME -> "AUDITORY_RHYME"
        PromptKind.SYLLABLE_COUNT -> "AUDITORY_SYLLABLE_COUNT"
        PromptKind.FIRST_SOUND -> "PHONEME_INITIAL"
        PromptKind.LAST_SOUND -> "PHONEME_FINAL"
        PromptKind.BLEND_SOUNDS -> "AUDITORY_PHONEME_BLEND"
        PromptKind.LETTER_SOUND -> "GRAPHEME_INITIAL"
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

    private enum class Phase {
        GUIDED,
        CHECKPOINT
    }

    private enum class PromptKind {
        RHYME,
        SYLLABLE_COUNT,
        FIRST_SOUND,
        LAST_SOUND,
        BLEND_SOUNDS,
        LETTER_SOUND
    }
}
