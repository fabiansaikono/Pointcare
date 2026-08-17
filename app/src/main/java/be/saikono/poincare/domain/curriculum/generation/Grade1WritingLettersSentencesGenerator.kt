package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 1 writing generator for letters, words, and short sentences.
 *
 * Besides recognition and construction choices, this generator can materialize
 * actual on-screen letter-tracing practice. Pencil grip, spacing on paper, and
 * handwriting legibility still remain part of adult-observed practice.
 */
object Grade1WritingLettersSentencesGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g1.language.writing.letters_sentences"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g1.language.writing.letters_sentences"
    private val allowedParameterKeys = setOf(
        "grade",
        "roadmap_entry",
        "phase",
        "editorial_status",
        "mode",
        "letter_group"
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
        if (parameters["editorial_status"] != LessonGeneratorRegistry.APPROVED_STATUS) {
            add("editorial_status must be approved")
        }
        val mode = parameters["mode"] ?: "choice"
        if (mode !in setOf("choice", "letter_tracing")) {
            add("mode must be choice or letter_tracing")
        }
        val letterGroup = parameters["letter_group"]
        if (mode == "letter_tracing" && letterGroup !in letterGroups.keys) {
            add("letter_group must identify a supported tracing group")
        }
        if (mode != "letter_tracing" && letterGroup != null) {
            add("letter_group is only supported for letter_tracing")
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
        if (parameters["mode"] == "letter_tracing") {
            return tracingExercises(
                seed = seed,
                exerciseCount = exerciseCount,
                phase = phase,
                letterGroup = parameters.getValue("letter_group")
            )
        }
        val random = StableLessonRandom(seed.value)
        val used = linkedSetOf<String>()
        val offset = if (phase == Phase.GUIDED) 0 else random.nextInt(PromptKind.entries.size)

        return List(exerciseCount) { index ->
            val kind = PromptKind.entries[(index + offset) % PromptKind.entries.size]
            val candidate = uniqueCandidate(kind, random, used)
            exercise(seed, index, phase, candidate)
        }
    }

    private fun tracingExercises(
        seed: LessonGenerationSeed,
        exerciseCount: Int,
        phase: Phase,
        letterGroup: String
    ): List<ExerciseContent> {
        val letters = letterGroups.getValue(letterGroup)
        return List(exerciseCount) { index ->
            val letter = letters[index % letters.size]
            val isNumber = letter.first().isDigit()
            ExerciseContent(
                id = "generated.g1.language.letter_tracing.v1.${seed.fingerprint}.${index + 1}",
                domain = LearningDomain.WRITING,
                skill = if (isNumber) "number_tracing" else "letter_tracing",
                section = if (phase == Phase.GUIDED) ExerciseSection.PRACTICE else ExerciseSection.TESTS,
                level = SkillLevel.FIRST_GRADE,
                inputMode = InputMode.TRACE_LETTER,
                prompt = LocalizedText(
                    nl = if (isNumber) "Trek het cijfer $letter over." else "Trek de letter $letter over.",
                    fr = if (isNumber) "Repasse le chiffre $letter." else "Repasse la lettre $letter.",
                    en = if (isNumber) "Trace the number $letter." else "Trace the letter $letter."
                ),
                speechText = LocalizedText(
                    nl = if (isNumber) "Trek het cijfer $letter over." else "Trek de letter $letter over.",
                    fr = if (isNumber) "Repasse le chiffre $letter." else "Repasse la lettre $letter.",
                    en = if (isNumber) "Trace the number $letter." else "Trace the letter $letter."
                ),
                data = linkedMapOf(
                    "generatorId" to generatorId,
                    "generatorVersion" to generatorVersion.toString(),
                    "seedFingerprint" to seed.fingerprint,
                    "roadmapEntry" to ROADMAP_ENTRY,
                    "phase" to phase.name.lowercase(),
                    "mode" to "letter_tracing",
                    "letterGroup" to letterGroup,
                    "letter" to letter,
                    "letterCase" to when {
                        isNumber -> "number"
                        letter.first().isUpperCase() -> "uppercase"
                        else -> "lowercase"
                    },
                    "answer_nl-BE" to "voltooid",
                    "answer_fr-BE" to "terminé",
                    "answer_en-US" to "completed",
                    "automatedScope" to "guided_letter_trace",
                    "evidenceMode" to "PRODUCTIVE_TRACE_PLUS_ADULT_OBSERVATION",
                    "requiresAdultHandwritingPractice" to "true",
                    "adultObservationRubric" to "starting_point;stroke_direction;letter_shape;spacing;readability",
                    "masteryTag" to if (isNumber) "writing.number_tracing" else "writing.letter_tracing",
                    "errorTags" to "requires_adult_motor_observation",
                    "feedback_nl-BE" to "Trek rustig over en let op het startpunt en de richting van elke lijn.",
                    "feedback_fr-BE" to "Repasse lentement en respectant le point de départ et le sens de chaque trait.",
                    "feedback_en-US" to "Trace slowly and notice the starting point and direction of every stroke."
                ),
                answer = "completed"
            )
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
                val candidate = Candidate(kind, templateIndex, template.prompt, template.speech, options, template.answerKey)
                if (used.add(candidate.signature())) return candidate
            }
        }
        error("Unable to create a unique Grade 1 writing exercise for ${kind.name}")
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
            id = "generated.g1.language.writing.v1.${seed.fingerprint}.${index + 1}",
            domain = LearningDomain.WRITING,
            skill = "writing_letters_sentences",
            section = if (phase == Phase.GUIDED) ExerciseSection.PRACTICE else ExerciseSection.TESTS,
            level = SkillLevel.FIRST_GRADE,
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
                "masteryTag" to masteryTag(candidate.kind),
                "errorTags" to errorTags(candidate.kind),
                "answerRole" to "choice",
                "automatedScope" to "letter_word_sentence_choice",
                "requiresAdultHandwritingPractice" to "true",
                "evidenceMode" to "HYBRID_RECOGNITION_PLUS_PRODUCTIVE_WRITING",
                "requiresAdultIndependentWriting" to "true",
                "adultObservationRubric" to "correct_letters;word_spacing;sentence_readability;independent_composition",
                "options_nl-BE" to candidate.options.joinToString("\n") { it.nl },
                "options_fr-BE" to candidate.options.joinToString("\n") { it.fr },
                "options_en-US" to candidate.options.joinToString("\n") { it.en },
                "optionSpeech_nl-BE" to candidate.options.joinToString("\n") { it.speechNl ?: spoken(it.nl) },
                "optionSpeech_fr-BE" to candidate.options.joinToString("\n") { it.speechFr ?: spoken(it.fr) },
                "optionSpeech_en-US" to candidate.options.joinToString("\n") { it.speechEn ?: spoken(it.en) },
                "answer_nl-BE" to answer.nl,
                "answer_fr-BE" to answer.fr,
                "answer_en-US" to answer.en,
                "hint_nl-BE" to hintNl(candidate.kind),
                "hint_fr-BE" to hintFr(candidate.kind),
                "hint_en-US" to hintEn(candidate.kind),
                "feedback_nl-BE" to feedbackNl(candidate.kind),
                "feedback_fr-BE" to feedbackFr(candidate.kind),
                "feedback_en-US" to feedbackEn(candidate.kind)
            ).apply { putAll(auditMetadata(candidate)) },
            answer = candidate.answerKey
        )
    }

    private fun bank(kind: PromptKind): List<Template> = when (kind) {
        PromptKind.LETTER_CASE -> listOf(
            template(
                localized("Welke kleine letter hoort bij de hoofdletter M?", "Quelle minuscule correspond à la majuscule M ?", "Which lowercase letter matches the uppercase M?"),
                "correct",
                choice("correct", "m", "m", "m", "de kleine letter m", "la lettre minuscule m", "lowercase m"),
                choice("other_1", "n", "n", "n", "de kleine letter n", "la lettre minuscule n", "lowercase n"),
                choice("other_2", "w", "w", "w", "de kleine letter w", "la lettre minuscule w", "lowercase w")
            ),
            template(
                localized("Welke hoofdletter hoort bij de kleine letter a?", "Quelle majuscule correspond à la minuscule a ?", "Which uppercase letter matches the lowercase a?"),
                "correct",
                choice("correct", "A", "A", "A", "de hoofdletter a", "la lettre majuscule a", "uppercase a"),
                choice("other_1", "O", "O", "O", "de hoofdletter o", "la lettre majuscule o", "uppercase o"),
                choice("other_2", "E", "E", "E", "de hoofdletter e", "la lettre majuscule e", "uppercase e")
            )
        )

        PromptKind.MISSING_LETTER -> listOf(
            spokenTemplate(
                localized(
                    "Welke letter ontbreekt in bal? b _ l",
                    "Quelle lettre manque dans sac ? s _ c",
                    "Which letter is missing in cat? c _ t"
                ),
                localized("Welke letter ontbreekt in het woord bal", "Quelle lettre manque dans le mot sac", "Which letter is missing in the word cat"),
                "correct",
                choice("correct", "a", "a", "a", "de letter a", "la lettre a", "the letter a"),
                choice("other_1", "o", "o", "o", "de letter o", "la lettre o", "the letter o"),
                choice("other_2", "e", "e", "e", "de letter e", "la lettre e", "the letter e")
            ),
            spokenTemplate(
                localized(
                    "Welke letter ontbreekt in bus? b _ s",
                    "Quelle lettre manque dans lune ? l _ ne",
                    "Which letter is missing in sun? s _ n"
                ),
                localized("Welke letter ontbreekt in het woord bus", "Quelle lettre manque dans le mot lune", "Which letter is missing in the word sun"),
                "correct",
                choice("correct", "u", "u", "u", "de letter u", "la lettre u", "the letter u"),
                choice("other_1", "a", "a", "a", "de letter a", "la lettre a", "the letter a"),
                choice("other_2", "i", "i", "i", "de letter i", "la lettre i", "the letter i")
            )
        )

        PromptKind.FORM_WORD -> listOf(
            template(
                localized(
                    "Welk woord schrijf je voor het licht aan de nachtelijke hemel?",
                    "Quel mot désigne l'astre lumineux dans le ciel nocturne ?",
                    "Which word names the light in the night sky?"
                ),
                "correct",
                choice("correct", "maan", "lune", "moon"),
                choice("other_1", "man", "luge", "moan"),
                choice("other_2", "mat", "lundi", "noon")
            ),
            template(
                localized(
                    "Welk woord schrijf je voor een dier dat in het water zwemt?",
                    "Quel mot désigne un animal qui nage dans l'eau ?",
                    "Which word names an animal that swims in water?"
                ),
                "correct",
                choice("correct", "vis", "poisson", "fish"),
                choice("other_1", "vos", "rat", "fox"),
                choice("other_2", "mus", "lion", "bird")
            )
        )

        PromptKind.WORD_ORDER -> listOf(
            template(
                localized("Welke zin staat in de juiste volgorde?", "Quelle phrase est dans le bon ordre ?", "Which sentence is in the correct order?"),
                "correct",
                choice("correct", "De kat slaapt.", "Le chat dort.", "The cat sleeps."),
                choice("other_1", "Kat de slaapt.", "Chat le dort.", "Cat the sleeps."),
                choice("other_2", "Slaapt de kat de.", "Dort le chat le.", "Sleeps the cat the.")
            ),
            template(
                localized("Welke woorden vormen samen een goede zin?", "Quels mots forment une bonne phrase ?", "Which words make a good sentence?"),
                "correct",
                choice("correct", "Mila leest een boek.", "Mila lit un livre.", "Mila reads a book."),
                choice("other_1", "Leest Mila boek een.", "Lit Mila livre un.", "Reads Mila book a."),
                choice("other_2", "Een Mila boek leest.", "Un Mila livre lit.", "A Mila book reads.")
            )
        )

        PromptKind.CAPITAL_START -> listOf(
            template(
                localized(
                    "Welke zin gebruikt hoofdletters correct?",
                    "Quelle phrase utilise correctement les majuscules ?",
                    "Which sentence uses capital letters correctly?"
                ),
                "correct",
                choice("correct", "Lina tekent een zon.", "Lina dessine un soleil.", "Lina draws a sun."),
                choice("lowercase", "lina tekent een zon.", "lina dessine un soleil.", "lina draws a sun."),
                choice("all_caps", "LINA TEKENT EEN ZON.", "LINA DESSINE UN SOLEIL.", "LINA DRAWS A SUN.")
            ),
            template(
                localized(
                    "Kies de zin die hoofdletters correct gebruikt.",
                    "Choisis la phrase qui utilise correctement les majuscules.",
                    "Choose the sentence that uses capital letters correctly."
                ),
                "correct",
                choice("correct", "Vandaag regent het.", "Aujourd'hui, il pleut.", "Today it is raining."),
                choice("lowercase", "vandaag regent het.", "aujourd'hui, il pleut.", "today it is raining."),
                choice("middle_capital", "Vandaag RegEnt het.", "Aujourd'hui, Il Pleut.", "Today It Is Raining.")
            )
        )

        PromptKind.END_PUNCTUATION -> listOf(
            template(
                localized("Welke zin is een vraag en eindigt met een vraagteken?", "Quelle phrase est une question et se termine par un point d'interrogation ?", "Which sentence is a question and ends with a question mark?"),
                "correct",
                choice("correct", "Waar is mijn bal?", "Où est mon ballon ?", "Where is my ball?", "Waar is mijn bal, met een vraagteken", "Où est mon ballon, avec un point d'interrogation", "Where is my ball, with a question mark"),
                choice("period", "Waar is mijn bal.", "Où est mon ballon.", "Where is my ball.", "Waar is mijn bal, met een punt", "Où est mon ballon, avec un point", "Where is my ball, with a period"),
                choice("exclamation", "Waar is mijn bal!", "Où est mon ballon !", "Where is my ball!", "Waar is mijn bal, met een uitroepteken", "Où est mon ballon, avec un point d'exclamation", "Where is my ball, with an exclamation mark")
            ),
            template(
                localized("Welke gewone vertelzin eindigt correct met een punt?", "Quelle phrase déclarative se termine correctement par un point ?", "Which statement correctly ends with a period?"),
                "correct",
                choice("correct", "De hond loopt buiten.", "Le chien court dehors.", "The dog runs outside.", "De hond loopt buiten, met een punt", "Le chien court dehors, avec un point", "The dog runs outside, with a period"),
                choice("question", "De hond loopt buiten?", "Le chien court dehors ?", "The dog runs outside?", "De hond loopt buiten, met een vraagteken", "Le chien court dehors, avec un point d'interrogation", "The dog runs outside, with a question mark"),
                choice("missing", "De hond loopt buiten", "Le chien court dehors", "The dog runs outside", "De hond loopt buiten, zonder leesteken", "Le chien court dehors, sans signe de ponctuation", "The dog runs outside, without punctuation")
            )
        )

        PromptKind.COMPLETE_SENTENCE -> listOf(
            template(
                localized("Welke keuze is een volledige zin?", "Quel choix est une phrase complète ?", "Which choice is a complete sentence?"),
                "correct",
                choice("correct", "De vogel zingt.", "L'oiseau chante.", "The bird sings."),
                choice("fragment_1", "De vogel.", "L'oiseau.", "The bird."),
                choice("fragment_2", "Zingt in de.", "Chante dans le.", "Sings in the.")
            ),
            template(
                localized("Welke zin vertelt duidelijk wie iets doet?", "Quelle phrase dit clairement qui fait quelque chose ?", "Which sentence clearly tells who does something?"),
                "correct",
                choice("correct", "Omar bouwt een toren.", "Omar construit une tour.", "Omar builds a tower."),
                choice("fragment_1", "Bouwt een toren.", "Construit une tour.", "Builds a tower."),
                choice("fragment_2", "Omar een toren.", "Omar une tour.", "Omar a tower.")
            )
        )

        PromptKind.COMPLETE_WITH_WORD -> listOf(
            template(
                localized("Welk woord maakt de zin af? De kat ___ op de mat.", "Quel mot complète la phrase ? Le chat ___ sur le tapis.", "Which word completes the sentence? The cat ___ on the mat."),
                "correct",
                choice("correct", "slaapt", "dort", "sleeps"),
                choice("other_1", "blauw", "bleu", "blue"),
                choice("other_2", "tafel", "table", "table")
            ),
            template(
                localized("Welk woord past in de zin? Ik ___ een appel.", "Quel mot convient dans la phrase ? Je ___ une pomme.", "Which word fits the sentence? I ___ an apple."),
                "correct",
                choice("correct", "eet", "mange", "eat"),
                choice("other_1", "groen", "verte", "green"),
                choice("other_2", "stoel", "chaise", "chair")
            )
        )
    }

    private fun template(
        prompt: LocalizedText,
        answerKey: String,
        vararg options: Choice
    ): Template = Template(prompt, spokenPrompt(prompt), options.toList(), answerKey)

    private fun spokenTemplate(
        prompt: LocalizedText,
        speech: LocalizedText,
        answerKey: String,
        vararg options: Choice
    ): Template = Template(prompt, speech, options.toList(), answerKey)

    private fun localized(nl: String, fr: String, en: String): LocalizedText =
        LocalizedText(nl = nl, fr = fr, en = en)

    private fun spokenPrompt(visible: LocalizedText): LocalizedText = LocalizedText(
        nl = spoken(visible.nl),
        fr = visible.fr?.let(::spoken),
        en = visible.en?.let(::spoken)
    )

    private fun spoken(text: String): String = text
        .replace("?", "")
        .replace(Regex("\\s+"), " ")
        .trim()

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
        PromptKind.LETTER_CASE -> "Een hoofdletter en kleine letter kunnen dezelfde letter voorstellen."
        PromptKind.MISSING_LETTER -> "Zeg het hele woord langzaam en luister naar de ontbrekende klank."
        PromptKind.FORM_WORD -> "Lees de letters van links naar rechts en zoek het bekende woord."
        PromptKind.WORD_ORDER -> "Een zin vertelt meestal eerst wie of wat, en daarna wat er gebeurt."
        PromptKind.CAPITAL_START -> "Een zin begint met een hoofdletter."
        PromptKind.END_PUNCTUATION -> "Een vraag krijgt een vraagteken; een gewone vertelzin meestal een punt."
        PromptKind.COMPLETE_SENTENCE -> "Een volledige zin vertelt wie of wat en wat er gebeurt."
        PromptKind.COMPLETE_WITH_WORD -> "Lees de hele zin en kies het woord dat grammaticaal en inhoudelijk past."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.LETTER_CASE -> "Une majuscule et une minuscule peuvent représenter la même lettre."
        PromptKind.MISSING_LETTER -> "Dis le mot entier lentement et écoute le son manquant."
        PromptKind.FORM_WORD -> "Lis les lettres de gauche à droite et cherche le mot connu."
        PromptKind.WORD_ORDER -> "Une phrase indique généralement d'abord qui ou quoi, puis ce qui se passe."
        PromptKind.CAPITAL_START -> "Une phrase commence par une majuscule."
        PromptKind.END_PUNCTUATION -> "Une question prend un point d'interrogation ; une phrase déclarative prend généralement un point."
        PromptKind.COMPLETE_SENTENCE -> "Une phrase complète dit qui ou quoi et ce qui se passe."
        PromptKind.COMPLETE_WITH_WORD -> "Lis toute la phrase et choisis le mot qui convient au sens et à la grammaire."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.LETTER_CASE -> "An uppercase and lowercase form can represent the same letter."
        PromptKind.MISSING_LETTER -> "Say the whole word slowly and listen for the missing sound."
        PromptKind.FORM_WORD -> "Read the letters from left to right and find the familiar word."
        PromptKind.WORD_ORDER -> "A sentence usually tells who or what first, then what happens."
        PromptKind.CAPITAL_START -> "A sentence starts with a capital letter."
        PromptKind.END_PUNCTUATION -> "A question uses a question mark; a statement usually uses a period."
        PromptKind.COMPLETE_SENTENCE -> "A complete sentence tells who or what and what happens."
        PromptKind.COMPLETE_WITH_WORD -> "Read the whole sentence and choose the word that fits its grammar and meaning."
    }

    private fun masteryTag(kind: PromptKind): String = when (kind) {
        PromptKind.LETTER_CASE -> "grapheme.letter_case"
        PromptKind.MISSING_LETTER -> "spelling.missing_letter"
        PromptKind.FORM_WORD -> "spelling.word_form"
        PromptKind.WORD_ORDER -> "grammar.word_order"
        PromptKind.CAPITAL_START -> "grammar.capitalization"
        PromptKind.END_PUNCTUATION -> "grammar.punctuation"
        PromptKind.COMPLETE_SENTENCE -> "grammar.complete_sentence"
        PromptKind.COMPLETE_WITH_WORD -> "grammar.sentence_completion"
    }

    private fun errorTags(kind: PromptKind): String = when (kind) {
        PromptKind.LETTER_CASE -> "confuses_letter_case"
        PromptKind.MISSING_LETTER -> "selects_wrong_grapheme"
        PromptKind.FORM_WORD -> "selects_wrong_word_for_meaning"
        PromptKind.WORD_ORDER -> "misorders_sentence"
        PromptKind.CAPITAL_START -> "misuses_capitalization"
        PromptKind.END_PUNCTUATION -> "misuses_end_punctuation"
        PromptKind.COMPLETE_SENTENCE -> "selects_sentence_fragment"
        PromptKind.COMPLETE_WITH_WORD -> "selects_semantically_or_grammatically_wrong_word"
    }

    private fun feedbackNl(kind: PromptKind): String = when (kind) {
        PromptKind.MISSING_LETTER -> "Lees het genoemde doelwoord opnieuw en controleer iedere letter."
        PromptKind.FORM_WORD -> "Koppel het woord aan de beschreven betekenis en controleer daarna de letters."
        else -> hintNl(kind)
    }

    private fun feedbackFr(kind: PromptKind): String = when (kind) {
        PromptKind.MISSING_LETTER -> "Relis le mot cible indiqué et vérifie chaque lettre."
        PromptKind.FORM_WORD -> "Relie le mot au sens décrit, puis vérifie les lettres."
        else -> hintFr(kind)
    }

    private fun feedbackEn(kind: PromptKind): String = when (kind) {
        PromptKind.MISSING_LETTER -> "Read the named target word again and check every letter."
        PromptKind.FORM_WORD -> "Match the word to the described meaning, then check the letters."
        else -> hintEn(kind)
    }

    private fun auditMetadata(candidate: Candidate): Map<String, String> = when (candidate.kind) {
        PromptKind.MISSING_LETTER -> if (candidate.templateIndex == 0) {
            mapOf(
                "targetWord_nl-BE" to "bal",
                "targetWord_fr-BE" to "sac",
                "targetWord_en-US" to "cat"
            )
        } else {
            mapOf(
                "targetWord_nl-BE" to "bus",
                "targetWord_fr-BE" to "lune",
                "targetWord_en-US" to "sun"
            )
        }
        PromptKind.FORM_WORD -> mapOf("semanticTargetProvided" to "true")
        else -> emptyMap()
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

    private val letterGroups = linkedMapOf(
        "lowercase_a_e" to ('a'..'e').map(Char::toString),
        "lowercase_f_j" to ('f'..'j').map(Char::toString),
        "lowercase_k_p" to ('k'..'p').map(Char::toString),
        "lowercase_q_t" to ('q'..'t').map(Char::toString),
        "lowercase_q_z" to ('q'..'z').map(Char::toString),
        "lowercase_u_z_review" to (('u'..'z') + ('a'..'d')).map(Char::toString),
        "uppercase_a_e" to ('A'..'E').map(Char::toString),
        "uppercase_f_j" to ('F'..'J').map(Char::toString),
        "uppercase_k_p" to ('K'..'P').map(Char::toString),
        "uppercase_q_t" to ('Q'..'T').map(Char::toString),
        "uppercase_q_z" to ('Q'..'Z').map(Char::toString),
        "uppercase_u_z_review" to (('U'..'Z') + ('A'..'D')).map(Char::toString),
        "uppercase_a_f" to ('A'..'F').map(Char::toString),
        "uppercase_g_l" to ('G'..'L').map(Char::toString),
        "uppercase_m_r" to ('M'..'R').map(Char::toString),
        "uppercase_s_z" to ('S'..'Z').map(Char::toString),
        "number_0_9" to ('0'..'9').map(Char::toString)
    )

    private enum class Phase { GUIDED, CHECKPOINT }

    private enum class PromptKind {
        LETTER_CASE,
        MISSING_LETTER,
        FORM_WORD,
        WORD_ORDER,
        CAPITAL_START,
        END_PUNCTUATION,
        COMPLETE_SENTENCE,
        COMPLETE_WITH_WORD
    }
}
