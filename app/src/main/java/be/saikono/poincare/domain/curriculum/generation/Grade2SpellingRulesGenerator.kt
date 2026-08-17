package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 2 spelling-rule and word-pattern generator.
 *
 * Languages use their own suitable capitalization, syllable, and plural
 * examples. Tap choices assess recognition only; independent spelling and
 * handwriting still require adult review of actual written work.
 */
object Grade2SpellingRulesGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g2.spelling.rules.punctuation_syllables_plural"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g2.spelling.rules.punctuation_syllables_plural"
    private val allowedParameterKeys = setOf("grade", "roadmap_entry", "phase", "editorial_status")

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["grade"] != "2") add("grade must be 2")
        if (parameters["roadmap_entry"] != ROADMAP_ENTRY) add("roadmap_entry must be $ROADMAP_ENTRY")
        if (parameters["phase"] !in setOf("guided", "checkpoint")) {
            add("phase must be guided or checkpoint")
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
        val offset = if (phase == Phase.GUIDED) 0 else random.nextInt(PromptKind.entries.size)
        return List(exerciseCount) { index ->
            val kind = PromptKind.entries[(index + offset) % PromptKind.entries.size]
            exercise(seed, index, phase, uniqueCandidate(kind, random, used))
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
                kind,
                templateIndex,
                template.prompt,
                template.speech,
                options,
                template.answerKey
            )
            if (used.add(candidate.signature())) return candidate
        }
        bank.forEachIndexed { templateIndex, template ->
            template.options.indices.forEach { rotation ->
                val options = template.options.drop(rotation) + template.options.take(rotation)
                val candidate = Candidate(
                    kind,
                    templateIndex,
                    template.prompt,
                    template.speech,
                    options,
                    template.answerKey
                )
                if (used.add(candidate.signature())) return candidate
            }
        }
        error("Unable to create a unique Grade 2 spelling exercise for ${kind.name}")
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
        return ExerciseContent(
            id = "generated.g2.spelling.rules.v1.${seed.fingerprint}.${index + 1}",
            domain = LearningDomain.WRITING,
            skill = "spelling_rules_word_patterns",
            section = if (phase == Phase.GUIDED) ExerciseSection.PRACTICE else ExerciseSection.TESTS,
            level = SkillLevel.SECOND_GRADE,
            inputMode = InputMode.TAP_CHOICE,
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
                "answerRole" to "choice",
                "automatedScope" to "spelling_rule_recognition_choice",
                "requiresAdultIndependentWritingCheck" to "true",
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
                "hint_en-US" to hintEn(candidate.kind)
            ),
            answer = candidate.answerKey
        )
    }

    private fun bank(kind: PromptKind): List<Template> = when (kind) {
        PromptKind.CAPITAL_SENTENCE_START -> listOf(
            template(
                localized("Welke zin begint juist?", "Quelle phrase commence correctement ?", "Which sentence begins correctly?"),
                localized("Welke zin begint met de juiste hoofdletter", "Quelle phrase commence par la bonne majuscule", "Which sentence begins with the correct capital letter"),
                "correct",
                choice("correct", "De hond slaapt.", "Le chien dort.", "The dog sleeps.", "Hoofdletter D. De hond slaapt.", "L majuscule. Le chien dort.", "Capital T. The dog sleeps."),
                choice("lowercase", "de hond slaapt.", "le chien dort.", "the dog sleeps.", "Kleine letter d. De hond slaapt.", "L minuscule. Le chien dort.", "Lowercase t. The dog sleeps."),
                choice("wrong_capital", "DE hond slaapt.", "LE chien dort.", "THe dog sleeps.", "Twee hoofdletters aan het begin.", "Deux majuscules au début.", "Two capital letters at the beginning.")
            ),
            template(
                localized("Welke zin begint juist?", "Quelle phrase commence correctement ?", "Which sentence begins correctly?"),
                localized("Welke zin begint met de juiste hoofdletter", "Quelle phrase commence par la bonne majuscule", "Which sentence begins with the correct capital letter"),
                "correct",
                choice("correct", "Morgen gaan we zwemmen.", "Demain, nous allons nager.", "Tomorrow we will swim.", "Hoofdletter M. Morgen gaan we zwemmen.", "D majuscule. Demain, nous allons nager.", "Capital T. Tomorrow we will swim."),
                choice("lowercase", "morgen gaan we zwemmen.", "demain, nous allons nager.", "tomorrow we will swim.", "Kleine letter m aan het begin.", "D minuscule au début.", "Lowercase t at the beginning."),
                choice("wrong_capital", "MOrgen gaan we zwemmen.", "DEmain, nous allons nager.", "TOmorrow we will swim.", "Twee hoofdletters aan het begin.", "Deux majuscules au début.", "Two capital letters at the beginning.")
            )
        )

        PromptKind.CAPITAL_PROPER_NAME -> listOf(
            template(
                localized("Welke zin schrijft de naam juist?", "Quelle phrase écrit correctement le prénom ?", "Which sentence writes the name correctly?"),
                localized("Welke zin schrijft de persoonsnaam met een hoofdletter", "Quelle phrase écrit le prénom avec une majuscule", "Which sentence writes the person's name with a capital letter"),
                "correct",
                choice("correct", "Mila leest een boek.", "Mila lit un livre.", "Mila reads a book.", "De naam Mila begint met een hoofdletter.", "Le prénom Mila commence par une majuscule.", "The name Mila begins with a capital letter."),
                choice("lowercase", "mila leest een boek.", "mila lit un livre.", "mila reads a book.", "De naam mila begint met een kleine letter.", "Le prénom mila commence par une minuscule.", "The name mila begins with a lowercase letter."),
                choice("all_caps", "MILA leest een boek.", "MILA lit un livre.", "MILA reads a book.", "De hele naam staat in hoofdletters.", "Tout le prénom est en majuscules.", "The whole name is in capital letters.")
            ),
            template(
                localized("Welke zin schrijft de plaatsnaam juist?", "Quelle phrase écrit correctement le nom de la ville ?", "Which sentence writes the city name correctly?"),
                localized("Welke zin schrijft de plaatsnaam met een hoofdletter", "Quelle phrase écrit le nom de la ville avec une majuscule", "Which sentence writes the city name with a capital letter"),
                "correct",
                choice("correct", "We reizen naar Gent.", "Nous allons à Bruxelles.", "We travel to Boston.", "De plaatsnaam Gent begint met een hoofdletter.", "Le nom Bruxelles commence par une majuscule.", "The place name Boston begins with a capital letter."),
                choice("lowercase", "We reizen naar gent.", "Nous allons à bruxelles.", "We travel to boston.", "De plaatsnaam gent begint met een kleine letter.", "Le nom bruxelles commence par une minuscule.", "The place name boston begins with a lowercase letter."),
                choice("all_caps", "We reizen naar GENT.", "Nous allons à BRUXELLES.", "We travel to BOSTON.", "De hele plaatsnaam staat in hoofdletters.", "Tout le nom est en majuscules.", "The whole place name is in capital letters.")
            )
        )

        PromptKind.PUNCTUATION_MARK -> listOf(
            punctuationTemplate(
                localized("Welk leesteken hoort achter: Waar is mijn jas", "Quel signe termine : Où est ma veste", "Which punctuation mark ends: Where is my coat"),
                localized("Welk leesteken hoort achter de vraag, waar is mijn jas", "Quel signe termine la question, où est ma veste", "Which punctuation mark ends the question, where is my coat"),
                "question"
            ),
            punctuationTemplate(
                localized("Welk leesteken hoort achter: De zon schijnt", "Quel signe termine : Le soleil brille", "Which punctuation mark ends: The sun is shining"),
                localized("Welk leesteken hoort achter de mededeling, de zon schijnt", "Quel signe termine la phrase déclarative, le soleil brille", "Which punctuation mark ends the statement, the sun is shining"),
                "period"
            ),
            punctuationTemplate(
                localized("Welk leesteken past bij de uitroep: Pas op", "Quel signe convient à l'exclamation : Attention", "Which punctuation mark fits the exclamation: Watch out"),
                localized("Welk leesteken past bij de uitroep, pas op", "Quel signe convient à l'exclamation, attention", "Which punctuation mark fits the exclamation, watch out"),
                "exclamation"
            )
        )

        PromptKind.SYLLABLE_COUNT -> listOf(
            template(
                localized("Hoeveel lettergrepen hoor je in tafel?", "Combien de syllabes entends-tu dans maison ?", "How many syllables do you hear in window?"),
                localized("Hoeveel lettergrepen hoor je in tafel", "Combien de syllabes entends-tu dans maison", "How many syllables do you hear in window"),
                "two",
                choice("one", "1", "1", "1", "één lettergreep", "une syllabe", "one syllable"),
                choice("two", "2", "2", "2", "twee lettergrepen", "deux syllabes", "two syllables"),
                choice("three", "3", "3", "3", "drie lettergrepen", "trois syllabes", "three syllables")
            ),
            template(
                localized("Hoeveel lettergrepen hoor je in olifant?", "Combien de syllabes entends-tu dans animal ?", "How many syllables do you hear in elephant?"),
                localized("Hoeveel lettergrepen hoor je in olifant", "Combien de syllabes entends-tu dans animal", "How many syllables do you hear in elephant"),
                "three",
                choice("one", "1", "1", "1", "één lettergreep", "une syllabe", "one syllable"),
                choice("two", "2", "2", "2", "twee lettergrepen", "deux syllabes", "two syllables"),
                choice("three", "3", "3", "3", "drie lettergrepen", "trois syllabes", "three syllables")
            )
        )

        PromptKind.SYLLABLE_SPLIT -> listOf(
            template(
                localized("Waar splits je tafel in lettergrepen?", "Comment sépares-tu maison en syllabes ?", "How do you split window into syllables?"),
                localized("Waar splits je tafel in lettergrepen", "Comment sépares-tu maison en syllabes", "How do you split window into syllables"),
                "correct",
                choice("correct", "ta-fel", "mai-son", "win-dow", "ta, fel", "mai, son", "win, dow"),
                choice("other_1", "taf-el", "ma-is-on", "wind-ow", "taf, el", "ma, is, on", "wind, ow"),
                choice("other_2", "t-a-fel", "m-aison", "w-indow", "t, a, fel", "m, maison", "w, window")
            ),
            template(
                localized("Waar splits je winter in lettergrepen?", "Comment sépares-tu vélo en syllabes ?", "How do you split rabbit into syllables?"),
                localized("Waar splits je winter in lettergrepen", "Comment sépares-tu vélo en syllabes", "How do you split rabbit into syllables"),
                "correct",
                choice("correct", "win-ter", "vé-lo", "rab-bit", "win, ter", "vé, lo", "rab, bit"),
                choice("other_1", "wint-er", "vél-o", "rabb-it", "wint, er", "vél, o", "rabb, it"),
                choice("other_2", "w-in-ter", "v-élo", "r-abbit", "w, in, ter", "v, vélo", "r, rabbit")
            )
        )

        PromptKind.OPEN_CLOSED_PATTERN -> listOf(
            template(
                localized("Welk woord heeft een open eerste lettergreep?", "Quel mot a une première syllabe ouverte ?", "Which word has an open first syllable?"),
                localized("Welk woord heeft een open eerste lettergreep", "Quel mot a une première syllabe ouverte", "Which word has an open first syllable"),
                "open",
                choice("open", "ra-men", "vé-lo", "ti-ger", "ra, men", "vé, lo", "ti, ger"),
                choice("closed_1", "ram-men", "bal-lon", "kit-ten", "ram, men", "bal, lon", "kit, ten"),
                choice("closed_2", "bak-ker", "car-ton", "pic-nic", "bak, ker", "car, ton", "pic, nic")
            ),
            template(
                localized("Welk woord heeft een gesloten eerste lettergreep?", "Quel mot a une première syllabe fermée ?", "Which word has a closed first syllable?"),
                localized("Welk woord heeft een gesloten eerste lettergreep", "Quel mot a une première syllabe fermée", "Which word has a closed first syllable"),
                "closed",
                choice("closed", "ram-men", "bal-lon", "kit-ten", "ram, men", "bal, lon", "kit, ten"),
                choice("open_1", "ra-men", "vé-lo", "ti-ger", "ra, men", "vé, lo", "ti, ger"),
                choice("open_2", "bo-men", "pa-pa", "mu-sic", "bo, men", "pa, pa", "mu, sic")
            )
        )

        PromptKind.PLURAL_FORM -> listOf(
            template(
                localized("Wat is het meervoud van boek?", "Quel est le pluriel de livre ?", "What is the plural of book?"),
                localized("Wat is het meervoud van boek", "Quel est le pluriel de livre", "What is the plural of book"),
                "plural",
                choice("plural", "boeken", "livres", "books"),
                choice("singular", "boek", "livre", "book"),
                choice("wrong", "boeks", "livreaux", "bookes")
            ),
            template(
                localized("Wat is het meervoud van appel?", "Quel est le pluriel de pomme ?", "What is the plural of box?"),
                localized("Wat is het meervoud van appel", "Quel est le pluriel de pomme", "What is the plural of box"),
                "plural",
                choice("plural", "appels", "pommes", "boxes"),
                choice("singular", "appel", "pomme", "box"),
                choice("wrong", "appelenen", "pommeaux", "boxs")
            ),
            template(
                localized("Wat is het meervoud van kat?", "Quel est le pluriel de chat ?", "What is the plural of baby?"),
                localized("Wat is het meervoud van kat", "Quel est le pluriel de chat", "What is the plural of baby"),
                "plural",
                choice("plural", "katten", "chats", "babies"),
                choice("singular", "kat", "chat", "baby"),
                choice("wrong", "katen", "chates", "babys")
            )
        )

        PromptKind.PLURAL_IN_SENTENCE -> listOf(
            template(
                localized("Kies het juiste woord: Er staan drie ___ in de kast.", "Choisis le bon mot : Il y a trois ___ sur l'étagère.", "Choose the correct word: There are three ___ on the shelf."),
                localized("Kies het juiste woord. Er staan drie boeken in de kast", "Choisis le bon mot. Il y a trois livres sur l'étagère", "Choose the correct word. There are three books on the shelf"),
                "plural",
                choice("plural", "boeken", "livres", "books"),
                choice("singular", "boek", "livre", "book"),
                choice("wrong", "boeks", "livreaux", "bookes")
            ),
            template(
                localized("Kies het juiste woord: Twee ___ spelen in de tuin.", "Choisis le bon mot : Deux ___ jouent dans le jardin.", "Choose the correct word: Two ___ play in the garden."),
                localized("Kies het juiste woord. Twee katten spelen in de tuin", "Choisis le bon mot. Deux chats jouent dans le jardin", "Choose the correct word. Two cats play in the garden"),
                "plural",
                choice("plural", "katten", "chats", "cats"),
                choice("singular", "kat", "chat", "cat"),
                choice("wrong", "katen", "chates", "cates")
            )
        )
    }

    private fun punctuationTemplate(
        prompt: LocalizedText,
        speech: LocalizedText,
        answerKey: String
    ): Template = template(
        prompt,
        speech,
        answerKey,
        choice("period", ".", ".", ".", "een punt", "un point", "a period"),
        choice("question", "?", "?", "?", "een vraagteken", "un point d'interrogation", "a question mark"),
        choice("exclamation", "!", "!", "!", "een uitroepteken", "un point d'exclamation", "an exclamation mark")
    )

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
        PromptKind.CAPITAL_SENTENCE_START -> "Een zin begint met één hoofdletter."
        PromptKind.CAPITAL_PROPER_NAME -> "Een persoonsnaam of plaatsnaam begint met een hoofdletter."
        PromptKind.PUNCTUATION_MARK -> "Luister of het een mededeling, vraag of uitroep is."
        PromptKind.SYLLABLE_COUNT -> "Zeg het woord langzaam en klap bij elk woorddeel."
        PromptKind.SYLLABLE_SPLIT -> "Spreek het woord langzaam uit en zoek de natuurlijke woorddelen."
        PromptKind.OPEN_CLOSED_PATTERN -> "Een open lettergreep eindigt op een klinkerklank; een gesloten lettergreep op een medeklinker."
        PromptKind.PLURAL_FORM -> "Bedenk hoe je zegt dat er meer dan één is."
        PromptKind.PLURAL_IN_SENTENCE -> "Het getal in de zin vraagt om een meervoud."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.CAPITAL_SENTENCE_START -> "Une phrase commence par une majuscule."
        PromptKind.CAPITAL_PROPER_NAME -> "Un prénom ou un nom de lieu commence par une majuscule."
        PromptKind.PUNCTUATION_MARK -> "Écoute s'il s'agit d'une déclaration, d'une question ou d'une exclamation."
        PromptKind.SYLLABLE_COUNT -> "Dis le mot lentement et frappe dans tes mains pour chaque syllabe."
        PromptKind.SYLLABLE_SPLIT -> "Prononce le mot lentement et cherche ses parties naturelles."
        PromptKind.OPEN_CLOSED_PATTERN -> "Une syllabe ouverte se termine par une voyelle prononcée; une syllabe fermée se termine par une consonne."
        PromptKind.PLURAL_FORM -> "Cherche la forme qui indique qu'il y en a plusieurs."
        PromptKind.PLURAL_IN_SENTENCE -> "Le nombre dans la phrase demande un pluriel."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.CAPITAL_SENTENCE_START -> "A sentence begins with one capital letter."
        PromptKind.CAPITAL_PROPER_NAME -> "A person's name or a place name begins with a capital letter."
        PromptKind.PUNCTUATION_MARK -> "Listen for a statement, a question, or an exclamation."
        PromptKind.SYLLABLE_COUNT -> "Say the word slowly and clap once for each syllable."
        PromptKind.SYLLABLE_SPLIT -> "Say the word slowly and find its natural parts."
        PromptKind.OPEN_CLOSED_PATTERN -> "An open syllable ends in a vowel sound; a closed syllable ends in a consonant."
        PromptKind.PLURAL_FORM -> "Find the form that means more than one."
        PromptKind.PLURAL_IN_SENTENCE -> "The number in the sentence calls for a plural."
    }

    private data class Candidate(
        val kind: PromptKind,
        val templateIndex: Int,
        val prompt: LocalizedText,
        val speech: LocalizedText,
        val options: List<Choice>,
        val answerKey: String
    ) {
        fun signature(): String = "${kind.name}:$templateIndex:${options.joinToString(":") { it.key }}"
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
        CAPITAL_SENTENCE_START,
        CAPITAL_PROPER_NAME,
        PUNCTUATION_MARK,
        SYLLABLE_COUNT,
        SYLLABLE_SPLIT,
        OPEN_CLOSED_PATTERN,
        PLURAL_FORM,
        PLURAL_IN_SENTENCE
    }
}
