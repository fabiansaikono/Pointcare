package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 1 speaking-and-interaction generator.
 *
 * The automated activity assesses recognition of an appropriate utterance. It
 * deliberately does not claim to assess pronunciation, spontaneous speech, or
 * live conversation; those remain part of the adult-verified practical section.
 */
object Grade1SpeakingInteractionGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g1.language.speaking.basic_interaction"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g1.language.speaking.basic_interaction"
    private val allowedParameterKeys = setOf(
        "grade",
        "roadmap_entry",
        "phase",
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
            val candidate = Candidate(kind, templateIndex, template.prompt, options, template.answerKey)
            if (used.add(candidate.signature())) return candidate
        }

        bank.forEachIndexed { templateIndex, template ->
            template.options.indices.forEach { rotation ->
                val options = template.options.drop(rotation) + template.options.take(rotation)
                val candidate = Candidate(kind, templateIndex, template.prompt, options, template.answerKey)
                if (used.add(candidate.signature())) return candidate
            }
        }
        error("Unable to create a unique Grade 1 speaking exercise for ${kind.name}")
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
            id = "generated.g1.language.speaking.v1.${seed.fingerprint}.${index + 1}",
            domain = LearningDomain.READING,
            skill = "speaking_basic_interaction",
            section = if (phase == Phase.GUIDED) ExerciseSection.PRACTICE else ExerciseSection.TESTS,
            level = SkillLevel.FIRST_GRADE,
            inputMode = InputMode.TAP_CHOICE,
            prompt = candidate.prompt,
            speechText = spokenPrompt(candidate.prompt),
            data = linkedMapOf(
                "generatorId" to generatorId,
                "generatorVersion" to generatorVersion.toString(),
                "seedFingerprint" to seed.fingerprint,
                "roadmapEntry" to ROADMAP_ENTRY,
                "phase" to phase.name.lowercase(),
                "promptKind" to candidate.kind.name.lowercase(),
                "contentKey" to "${candidate.kind.name.lowercase()}.${candidate.templateIndex + 1}",
                "answerRole" to "choice",
                "automatedScope" to "utterance_choice",
                "requiresAdultSpeakingPractice" to "true",
                "evidenceMode" to "HYBRID_RECOGNITION_PLUS_ADULT_SPEAKING",
                "adultObservationRubric" to "audible_voice;relevant_utterance;turn_taking;responds_to_partner",
                "masteryTag" to "speaking.${candidate.kind.name.lowercase()}",
                "errorTags" to "speaking.inappropriate_or_incomplete_utterance",
                "options_nl-BE" to candidate.options.joinToString("\n") { it.nl },
                "options_fr-BE" to candidate.options.joinToString("\n") { it.fr },
                "options_en-US" to candidate.options.joinToString("\n") { it.en },
                "optionSpeech_nl-BE" to candidate.options.joinToString("\n") { spoken(it.nl) },
                "optionSpeech_fr-BE" to candidate.options.joinToString("\n") { spoken(it.fr) },
                "optionSpeech_en-US" to candidate.options.joinToString("\n") { spoken(it.en) },
                "answer_nl-BE" to answer.nl,
                "answer_fr-BE" to answer.fr,
                "answer_en-US" to answer.en,
                "hint_nl-BE" to hintNl(candidate.kind),
                "hint_fr-BE" to hintFr(candidate.kind),
                "hint_en-US" to hintEn(candidate.kind),
                "feedback_nl-BE" to "Kies de passende zin en oefen ze daarna hardop met een volwassene.",
                "feedback_fr-BE" to "Choisis la phrase adaptée, puis entraîne-toi à la dire à voix haute avec un adulte.",
                "feedback_en-US" to "Choose the suitable sentence, then practise saying it aloud with an adult."
            ),
            answer = candidate.answerKey
        )
    }

    private fun spokenPrompt(visible: LocalizedText): LocalizedText = LocalizedText(
        nl = spoken(visible.nl),
        fr = visible.fr?.let(::spoken),
        en = visible.en?.let(::spoken)
    )

    private fun spoken(text: String): String = text
        .replace("?", "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun bank(kind: PromptKind): List<Template> = when (kind) {
        PromptKind.POLITE_REQUEST -> listOf(
            template(
                localized(
                    "Je wilt het kleurpotlood van Noor lenen. Wat kun je beleefd zeggen?",
                    "Tu veux emprunter le crayon de couleur de Noor. Que peux-tu dire poliment ?",
                    "You want to borrow Noor's colored pencil. What can you say politely?"
                ),
                "polite",
                choice("polite", "Mag ik jouw kleurpotlood lenen, alsjeblieft?", "Puis-je emprunter ton crayon de couleur, s'il te plaît ?", "May I borrow your colored pencil, please?"),
                choice("command", "Geef mij dat kleurpotlood.", "Donne-moi ce crayon de couleur.", "Give me that colored pencil."),
                choice("unrelated", "Ik heb een blauwe boekentas.", "J'ai un cartable bleu.", "I have a blue schoolbag.")
            ),
            template(
                localized(
                    "Je wilt in de klas een raam openen. Wat vraag je aan de juf of meester?",
                    "Tu veux ouvrir une fenêtre en classe. Que demandes-tu à l'enseignant ?",
                    "You want to open a classroom window. What do you ask the teacher?"
                ),
                "polite",
                choice("polite", "Mag ik het raam openen, alsjeblieft?", "Puis-je ouvrir la fenêtre, s'il vous plaît ?", "May I open the window, please?"),
                choice("command", "Open nu het raam.", "Ouvrez la fenêtre maintenant.", "Open the window now."),
                choice("unrelated", "Het raam is vierkant.", "La fenêtre est carrée.", "The window is square.")
            )
        )

        PromptKind.ASK_WHAT -> listOf(
            template(
                localized(
                    "Je ziet een gesloten doos en wilt weten wat erin zit. Welke vraag stel je?",
                    "Tu vois une boîte fermée et tu veux savoir ce qu'elle contient. Quelle question poses-tu ?",
                    "You see a closed box and want to know what is inside. Which question do you ask?"
                ),
                "what_question",
                choice("what_question", "Wat zit er in de doos?", "Qu'y a-t-il dans la boîte ?", "What is in the box?"),
                choice("where_question", "Waar staat de doos?", "Où se trouve la boîte ?", "Where is the box?"),
                choice("statement", "De doos is dicht.", "La boîte est fermée.", "The box is closed.")
            ),
            template(
                localized(
                    "Je wilt weten wat de klas morgen gaat maken. Welke vraag past?",
                    "Tu veux savoir ce que la classe va fabriquer demain. Quelle question convient ?",
                    "You want to know what the class will make tomorrow. Which question fits?"
                ),
                "what_question",
                choice("what_question", "Wat gaan we morgen maken?", "Qu'allons-nous fabriquer demain ?", "What are we going to make tomorrow?"),
                choice("who_question", "Wie is er morgen jarig?", "Qui fête son anniversaire demain ?", "Whose birthday is tomorrow?"),
                choice("statement", "Morgen is na vandaag.", "Demain vient après aujourd'hui.", "Tomorrow comes after today.")
            )
        )

        PromptKind.ASK_WHERE -> listOf(
            template(
                localized(
                    "Je vindt je jas niet. Welke vraag helpt je om hem te zoeken?",
                    "Tu ne trouves pas ta veste. Quelle question t'aide à la chercher ?",
                    "You cannot find your coat. Which question helps you look for it?"
                ),
                "where_question",
                choice("where_question", "Waar hangt mijn jas?", "Où est accrochée ma veste ?", "Where is my coat hanging?"),
                choice("what_question", "Wat is een jas?", "Qu'est-ce qu'une veste ?", "What is a coat?"),
                choice("unrelated", "Mijn jas is warm.", "Ma veste est chaude.", "My coat is warm.")
            ),
            template(
                localized(
                    "Je wilt weten waar de turnles doorgaat. Wat vraag je?",
                    "Tu veux savoir où a lieu le cours de gymnastique. Que demandes-tu ?",
                    "You want to know where gym class takes place. What do you ask?"
                ),
                "where_question",
                choice("where_question", "Waar is de turnles?", "Où a lieu le cours de gymnastique ?", "Where is gym class?"),
                choice("when_question", "Wanneer eten we?", "Quand mangeons-nous ?", "When do we eat?"),
                choice("unrelated", "Ik heb sportschoenen.", "J'ai des chaussures de sport.", "I have gym shoes.")
            )
        )

        PromptKind.ASK_WHO -> listOf(
            template(
                localized(
                    "Je wilt weten welk kind vandaag de boeken uitdeelt. Welke vraag stel je?",
                    "Tu veux savoir quel enfant distribue les livres aujourd'hui. Quelle question poses-tu ?",
                    "You want to know which child is handing out books today. Which question do you ask?"
                ),
                "who_question",
                choice("who_question", "Wie deelt vandaag de boeken uit?", "Qui distribue les livres aujourd'hui ?", "Who is handing out the books today?"),
                choice("where_question", "Waar liggen de boeken?", "Où sont les livres ?", "Where are the books?"),
                choice("statement", "De boeken liggen klaar.", "Les livres sont prêts.", "The books are ready.")
            ),
            template(
                localized(
                    "Je wilt weten wie je kan helpen met je rits. Wat vraag je?",
                    "Tu veux savoir qui peut t'aider avec ta fermeture éclair. Que demandes-tu ?",
                    "You want to know who can help with your zipper. What do you ask?"
                ),
                "who_question",
                choice("who_question", "Wie kan mij helpen met mijn rits?", "Qui peut m'aider avec ma fermeture éclair ?", "Who can help me with my zipper?"),
                choice("what_question", "Wat is een rits?", "Qu'est-ce qu'une fermeture éclair ?", "What is a zipper?"),
                choice("unrelated", "Mijn jas is groen.", "Ma veste est verte.", "My coat is green.")
            )
        )

        PromptKind.ASK_CLARIFICATION -> listOf(
            template(
                localized(
                    "Je hebt de opdracht niet goed gehoord. Wat kun je zeggen?",
                    "Tu n'as pas bien entendu la consigne. Que peux-tu dire ?",
                    "You did not hear the instruction clearly. What can you say?"
                ),
                "repeat",
                choice("repeat", "Wil je dat nog eens zeggen, alsjeblieft?", "Peux-tu le répéter, s'il te plaît ?", "Could you say that again, please?"),
                choice("pretend", "Ik heb alles begrepen.", "J'ai tout compris.", "I understood everything."),
                choice("unrelated", "Ik speel graag buiten.", "J'aime jouer dehors.", "I like playing outside.")
            ),
            template(
                localized(
                    "Iemand gebruikt een woord dat je niet begrijpt. Welke vraag helpt?",
                    "Quelqu'un utilise un mot que tu ne comprends pas. Quelle question peut t'aider ?",
                    "Someone uses a word you do not understand. Which question helps?"
                ),
                "meaning",
                choice("meaning", "Wat betekent dat woord?", "Que veut dire ce mot ?", "What does that word mean?"),
                choice("pretend", "Ik weet het al.", "Je le sais déjà.", "I already know."),
                choice("unrelated", "Hoe laat is het?", "Quelle heure est-il ?", "What time is it?" )
            )
        )

        PromptKind.TURN_TAKING -> listOf(
            template(
                localized(
                    "Een klasgenoot is nog aan het vertellen. Wat doe je als je ook iets wilt zeggen?",
                    "Un camarade parle encore. Que fais-tu si tu veux aussi dire quelque chose ?",
                    "A classmate is still talking. What do you do if you also want to say something?"
                ),
                "wait",
                choice("wait", "Ik luister en wacht tot mijn klasgenoot klaar is.", "J'écoute et j'attends que mon camarade ait terminé.", "I listen and wait until my classmate is finished."),
                choice("interrupt", "Ik praat er meteen doorheen.", "Je parle tout de suite en même temps.", "I start talking over them right away."),
                choice("leave", "Ik loop zonder iets te zeggen weg.", "Je pars sans rien dire.", "I walk away without saying anything.")
            ),
            template(
                localized(
                    "De juf of meester stelt je een vraag. Wat helpt om goed te antwoorden?",
                    "L'enseignant te pose une question. Qu'est-ce qui t'aide à bien répondre ?",
                    "The teacher asks you a question. What helps you answer well?"
                ),
                "listen",
                choice("listen", "Ik luister eerst naar de hele vraag.", "J'écoute d'abord toute la question.", "I listen to the whole question first."),
                choice("interrupt", "Ik antwoord voordat de vraag klaar is.", "Je réponds avant la fin de la question.", "I answer before the question is finished."),
                choice("ignore", "Ik doe alsof ik niets hoor.", "Je fais comme si je n'entendais rien.", "I pretend I cannot hear anything.")
            )
        )

        PromptKind.RELEVANT_RESPONSE -> listOf(
            template(
                localized(
                    "Een kind vraagt: ‘Wat speel je graag?’ Welk antwoord past bij de vraag?",
                    "Un enfant demande : « À quoi aimes-tu jouer ? » Quelle réponse convient ?",
                    "A child asks, ‘What do you like to play?’ Which answer fits the question?"
                ),
                "answer",
                choice("answer", "Ik speel graag verstoppertje.", "J'aime jouer à cache-cache.", "I like playing hide-and-seek."),
                choice("unrelated_1", "Mijn jas hangt aan de kapstok.", "Ma veste est au portemanteau.", "My coat is on the hook."),
                choice("unrelated_2", "Morgen is het woensdag.", "Demain, nous serons mercredi.", "Tomorrow is Wednesday.")
            ),
            template(
                localized(
                    "Iemand vraagt: ‘Hoe ben je naar school gekomen?’ Welk antwoord past?",
                    "Quelqu'un demande : « Comment es-tu venu à l'école ? » Quelle réponse convient ?",
                    "Someone asks, ‘How did you get to school?’ Which answer fits?"
                ),
                "answer",
                choice("answer", "Ik ben met de fiets gekomen.", "Je suis venu à vélo.", "I came by bike."),
                choice("unrelated_1", "De school heeft veel ramen.", "L'école a beaucoup de fenêtres.", "The school has many windows."),
                choice("unrelated_2", "Ik eet graag appels.", "J'aime manger des pommes.", "I like eating apples.")
            )
        )

        PromptKind.GREETING_CLOSING -> listOf(
            template(
                localized(
                    "Je komt 's morgens de klas binnen. Wat kun je zeggen?",
                    "Tu entres en classe le matin. Que peux-tu dire ?",
                    "You enter the classroom in the morning. What can you say?"
                ),
                "greeting",
                choice("greeting", "Goedemorgen!", "Bonjour !", "Good morning!"),
                choice("closing", "Tot morgen!", "À demain !", "See you tomorrow!"),
                choice("unrelated", "Mijn potlood is geel.", "Mon crayon est jaune.", "My pencil is yellow.")
            ),
            template(
                localized(
                    "Je gesprek met een klasgenoot is klaar. Wat is een vriendelijke afsluiting?",
                    "Ta conversation avec un camarade est terminée. Comment peux-tu la terminer gentiment ?",
                    "Your conversation with a classmate is finished. What is a friendly way to end it?"
                ),
                "closing",
                choice("closing", "Dank je wel, tot straks!", "Merci, à tout à l'heure !", "Thank you, see you later!"),
                choice("command", "Ga nu weg.", "Va-t'en maintenant.", "Go away now."),
                choice("unrelated", "Mijn stoel is rood.", "Ma chaise est rouge.", "My chair is red.")
            )
        )
    }

    private fun localized(nl: String, fr: String, en: String): LocalizedText =
        LocalizedText(nl = nl, fr = fr, en = en)

    private fun template(
        prompt: LocalizedText,
        answerKey: String,
        vararg options: Choice
    ): Template = Template(prompt, options.toList(), answerKey)

    private fun choice(key: String, nl: String, fr: String, en: String): Choice =
        Choice(key, nl, fr, en)

    private fun hintNl(kind: PromptKind): String = when (kind) {
        PromptKind.POLITE_REQUEST -> "Een beleefde vraag begint vaak met ‘mag ik’ en eindigt met ‘alsjeblieft’."
        PromptKind.ASK_WHAT -> "Gebruik ‘wat’ als je naar een ding of activiteit vraagt."
        PromptKind.ASK_WHERE -> "Gebruik ‘waar’ als je een plaats zoekt."
        PromptKind.ASK_WHO -> "Gebruik ‘wie’ als je naar een persoon vraagt."
        PromptKind.ASK_CLARIFICATION -> "Zeg eerlijk wat je niet hoorde of begreep en vraag om hulp."
        PromptKind.TURN_TAKING -> "Luister tot de ander klaar is en neem daarna je beurt."
        PromptKind.RELEVANT_RESPONSE -> "Kies een antwoord dat echt bij de gestelde vraag past."
        PromptKind.GREETING_CLOSING -> "Begroet iemand bij het begin en sluit vriendelijk af."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.POLITE_REQUEST -> "Une demande polie contient souvent « puis-je » et « s'il te plaît »."
        PromptKind.ASK_WHAT -> "Utilise « que » ou « quoi » pour demander une chose ou une activité."
        PromptKind.ASK_WHERE -> "Utilise « où » pour demander un lieu."
        PromptKind.ASK_WHO -> "Utilise « qui » pour demander une personne."
        PromptKind.ASK_CLARIFICATION -> "Dis ce que tu n'as pas entendu ou compris et demande de l'aide."
        PromptKind.TURN_TAKING -> "Écoute jusqu'à ce que l'autre ait terminé, puis prends ton tour."
        PromptKind.RELEVANT_RESPONSE -> "Choisis une réponse qui correspond vraiment à la question."
        PromptKind.GREETING_CLOSING -> "Salue au début et termine la conversation gentiment."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.POLITE_REQUEST -> "A polite request often includes ‘may I’ and ‘please’."
        PromptKind.ASK_WHAT -> "Use ‘what’ to ask about a thing or activity."
        PromptKind.ASK_WHERE -> "Use ‘where’ to ask about a place."
        PromptKind.ASK_WHO -> "Use ‘who’ to ask about a person."
        PromptKind.ASK_CLARIFICATION -> "Say what you did not hear or understand and ask for help."
        PromptKind.TURN_TAKING -> "Listen until the other person finishes, then take your turn."
        PromptKind.RELEVANT_RESPONSE -> "Choose an answer that truly fits the question."
        PromptKind.GREETING_CLOSING -> "Greet someone at the start and end the conversation kindly."
    }

    private data class Candidate(
        val kind: PromptKind,
        val templateIndex: Int,
        val prompt: LocalizedText,
        val options: List<Choice>,
        val answerKey: String
    ) {
        fun signature(): String =
            "${kind.name}:$templateIndex:${options.joinToString(":") { it.key }}"
    }

    private data class Template(
        val prompt: LocalizedText,
        val options: List<Choice>,
        val answerKey: String
    )

    private data class Choice(
        val key: String,
        val nl: String,
        val fr: String,
        val en: String
    )

    private enum class Phase { GUIDED, CHECKPOINT }

    private enum class PromptKind {
        POLITE_REQUEST,
        ASK_WHAT,
        ASK_WHERE,
        ASK_WHO,
        ASK_CLARIFICATION,
        TURN_TAKING,
        RELEVANT_RESPONSE,
        GREETING_CLOSING
    }
}
