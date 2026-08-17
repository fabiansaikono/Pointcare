package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

internal data class Grade2Choice(
    val key: String,
    val nl: String,
    val fr: String,
    val en: String,
    val speechNl: String? = null,
    val speechFr: String? = null,
    val speechEn: String? = null
)

internal data class Grade2ChoiceTemplate(
    val prompt: LocalizedText,
    val speech: LocalizedText,
    val hint: LocalizedText,
    val answerKey: String,
    val options: List<Grade2Choice>
)

internal fun g2Text(nl: String, fr: String, en: String): LocalizedText =
    LocalizedText(nl = nl, fr = fr, en = en)

internal fun g2Choice(
    key: String,
    nl: String,
    fr: String,
    en: String,
    speechNl: String? = null,
    speechFr: String? = null,
    speechEn: String? = null
): Grade2Choice = Grade2Choice(key, nl, fr, en, speechNl, speechFr, speechEn)

internal fun g2Template(
    prompt: LocalizedText,
    speech: LocalizedText,
    hint: LocalizedText,
    answerKey: String,
    vararg options: Grade2Choice
): Grade2ChoiceTemplate = Grade2ChoiceTemplate(prompt, speech, hint, answerKey, options.toList())

internal abstract class Grade2ChoiceBankGenerator(
    final override val generatorId: String,
    private val roadmapEntry: String,
    private val idNamespace: String,
    private val skill: String,
    private val domain: LearningDomain,
    private val inputMode: InputMode,
    private val automatedScope: String,
    private val adultSupportKey: String,
    private val extraData: Map<String, String> = emptyMap(),
    private val banks: LinkedHashMap<String, List<Grade2ChoiceTemplate>>
) : LessonExerciseGenerator {
    final override val generatorVersion: Int = 1
    private val allowedParameterKeys = setOf("grade", "roadmap_entry", "phase", "editorial_status")

    final override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["grade"] != "2") add("grade must be 2")
        if (parameters["roadmap_entry"] != roadmapEntry) add("roadmap_entry must be $roadmapEntry")
        if (parameters["phase"] !in setOf("guided", "checkpoint")) add("phase must be guided or checkpoint")
        if (parameters["editorial_status"] != LessonGeneratorRegistry.APPROVED_STATUS) {
            add("editorial_status must be approved")
        }
    }

    final override fun generate(
        seed: LessonGenerationSeed,
        exerciseCount: Int,
        parameters: Map<String, String>
    ): List<ExerciseContent> {
        val issues = validate(parameters)
        require(issues.isEmpty()) { issues.joinToString("; ") }
        require(exerciseCount > 0) { "exerciseCount must be positive" }
        val phase = parameters.getValue("phase")
        val random = StableLessonRandom(seed.value)
        val kinds = banks.keys.toList()
        val offset = if (phase == "guided") 0 else random.nextInt(kinds.size)
        val used = linkedSetOf<String>()
        return List(exerciseCount) { index ->
            val kind = kinds[(index + offset) % kinds.size]
            val candidate = uniqueCandidate(kind, random, used)
            val answer = candidate.options.single { it.key == candidate.template.answerKey }
            ExerciseContent(
                id = "generated.$idNamespace.v1.${seed.fingerprint}.${index + 1}",
                domain = domain,
                skill = skill,
                section = if (phase == "guided") ExerciseSection.PRACTICE else ExerciseSection.TESTS,
                level = SkillLevel.SECOND_GRADE,
                inputMode = inputMode,
                prompt = candidate.template.prompt,
                speechText = candidate.template.speech,
                data = linkedMapOf(
                    "generatorId" to generatorId,
                    "generatorVersion" to generatorVersion.toString(),
                    "seedFingerprint" to seed.fingerprint,
                    "roadmapEntry" to roadmapEntry,
                    "phase" to phase,
                    "promptKind" to kind,
                    "contentKey" to "$kind.${candidate.templateIndex + 1}",
                    "answerRole" to "choice",
                    "automatedScope" to automatedScope,
                    adultSupportKey to "true",
                    "options_nl-BE" to candidate.options.joinToString("\n") { it.nl },
                    "options_fr-BE" to candidate.options.joinToString("\n") { it.fr },
                    "options_en-US" to candidate.options.joinToString("\n") { it.en },
                    "optionSpeech_nl-BE" to candidate.options.joinToString("\n") { it.speechNl ?: it.nl },
                    "optionSpeech_fr-BE" to candidate.options.joinToString("\n") { it.speechFr ?: it.fr },
                    "optionSpeech_en-US" to candidate.options.joinToString("\n") { it.speechEn ?: it.en },
                    "answer_nl-BE" to answer.nl,
                    "answer_fr-BE" to answer.fr,
                    "answer_en-US" to answer.en,
                    "hint_nl-BE" to candidate.template.hint.nl,
                    "hint_fr-BE" to candidate.template.hint.fr.orEmpty(),
                    "hint_en-US" to candidate.template.hint.en.orEmpty()
                ).apply { putAll(extraData) },
                answer = candidate.template.answerKey
            )
        }
    }

    private fun uniqueCandidate(
        kind: String,
        random: StableLessonRandom,
        used: MutableSet<String>
    ): Candidate {
        val bank = banks.getValue(kind)
        repeat(64) {
            val templateIndex = random.nextInt(bank.size)
            val template = bank[templateIndex]
            val options = shuffled(template.options, random)
            val candidate = Candidate(templateIndex, template, options)
            if (used.add(candidate.signature(kind))) return candidate
        }
        bank.forEachIndexed { templateIndex, template ->
            template.options.indices.forEach { rotation ->
                val options = template.options.drop(rotation) + template.options.take(rotation)
                val candidate = Candidate(templateIndex, template, options)
                if (used.add(candidate.signature(kind))) return candidate
            }
        }
        error("Unable to create a unique exercise for $generatorId and $kind")
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

    private data class Candidate(
        val templateIndex: Int,
        val template: Grade2ChoiceTemplate,
        val options: List<Grade2Choice>
    ) {
        fun signature(kind: String): String =
            "$kind:$templateIndex:${options.joinToString(":") { it.key }}"
    }
}

internal object Grade2ShortTextWritingGenerator : Grade2ChoiceBankGenerator(
    generatorId = "generator.g2.language.writing.short_texts",
    roadmapEntry = "roadmap.g2.language.writing.short_texts",
    idNamespace = "g2.language.writing.short_texts",
    skill = "writing_short_texts",
    domain = LearningDomain.READING,
    inputMode = InputMode.TAP_CHOICE,
    automatedScope = "short_text_planning_revision_choice",
    adultSupportKey = "requiresAdultIndependentTextReview",
    banks = linkedMapOf(
        "complete_sentence" to listOf(
            g2Template(g2Text("Kies een volledig einde: De hond rent ...", "Choisis une fin complète : Le chien court...", "Choose a complete ending: The dog runs..."), g2Text("Kies een volledig einde voor de zin. De hond rent", "Choisis une fin complète. Le chien court", "Choose a complete ending. The dog runs"), g2Text("Een volledige zin vertelt duidelijk wat er gebeurt.", "Une phrase complète dit clairement ce qui se passe.", "A complete sentence clearly tells what happens."), "complete", g2Choice("complete", "naar zijn bal.", "vers sa balle.", "toward its ball."), g2Choice("fragment", "de grote.", "le grand.", "the big."), g2Choice("unrelated", "omdat blauw.", "parce que bleu.", "because blue.")),
            g2Template(g2Text("Kies een volledig einde: Na school ...", "Choisis une fin complète : Après l'école...", "Choose a complete ending: After school..."), g2Text("Kies een volledig einde voor de zin. Na school", "Choisis une fin complète. Après l'école", "Choose a complete ending. After school"), g2Text("Zoek wie iets doet en wat er gebeurt.", "Cherche qui fait quelque chose et ce qui se passe.", "Look for who does something and what happens."), "complete", g2Choice("complete", "speel ik in het park.", "je joue dans le parc.", "I play in the park."), g2Choice("fragment", "de gele fiets.", "le vélo jaune.", "the yellow bicycle."), g2Choice("unrelated", "maar onder.", "mais sous.", "but under."))
        ),
        "sentence_order" to listOf(
            g2Template(g2Text("Welke volgorde maakt een duidelijke zin?", "Quel ordre forme une phrase claire ?", "Which order makes a clear sentence?"), g2Text("Welke woordvolgorde maakt een duidelijke zin", "Quel ordre de mots forme une phrase claire", "Which word order makes a clear sentence"), g2Text("Begin met wie of wat, en vertel daarna wat er gebeurt.", "Commence par qui ou quoi, puis dis ce qui se passe.", "Begin with who or what, then tell what happens."), "ordered", g2Choice("ordered", "De kat drinkt melk.", "Le chat boit du lait.", "The cat drinks milk."), g2Choice("wrong_1", "Drinkt melk de kat.", "Boit du lait le chat.", "Drinks milk the cat."), g2Choice("wrong_2", "Melk de kat drinkt.", "Du lait le chat boit.", "Milk the cat drinks.")),
            g2Template(g2Text("Welke volgorde maakt een duidelijke zin?", "Quel ordre forme une phrase claire ?", "Which order makes a clear sentence?"), g2Text("Welke woordvolgorde maakt een duidelijke zin", "Quel ordre de mots forme une phrase claire", "Which word order makes a clear sentence"), g2Text("Lees elke keuze als één zin.", "Lis chaque choix comme une phrase.", "Read each choice as one sentence."), "ordered", g2Choice("ordered", "Mijn broer bouwt een toren.", "Mon frère construit une tour.", "My brother builds a tower."), g2Choice("wrong_1", "Bouwt mijn een toren broer.", "Construit mon une tour frère.", "Builds my a tower brother."), g2Choice("wrong_2", "Een toren broer mijn bouwt.", "Une tour frère mon construit.", "A tower brother my builds."))
        ),
        "choose_title" to listOf(
            g2Template(g2Text("Lina plant zaadjes en geeft ze water. Welke titel past?", "Lina plante des graines et les arrose. Quel titre convient ?", "Lina plants seeds and waters them. Which title fits?"), g2Text("Lina plant zaadjes en geeft ze water. Welke titel past", "Lina plante des graines et les arrose. Quel titre convient", "Lina plants seeds and waters them. Which title fits"), g2Text("Een titel vertelt kort waar de tekst over gaat.", "Un titre dit brièvement de quoi parle le texte.", "A title briefly tells what the text is about."), "title", g2Choice("title", "Zaadjes planten", "Planter des graines", "Planting Seeds"), g2Choice("other_1", "Een treinreis", "Un voyage en train", "A Train Ride"), g2Choice("other_2", "De sneeuwpop", "Le bonhomme de neige", "The Snowman")),
            g2Template(g2Text("Noah bakt brood met zijn opa. Welke titel past?", "Noah prépare du pain avec son grand-père. Quel titre convient ?", "Noah bakes bread with his grandfather. Which title fits?"), g2Text("Noah bakt brood met zijn opa. Welke titel past", "Noah prépare du pain avec son grand-père. Quel titre convient", "Noah bakes bread with his grandfather. Which title fits"), g2Text("Kies de titel die bij alle belangrijke woorden past.", "Choisis le titre qui correspond aux mots importants.", "Choose the title that matches the important words."), "title", g2Choice("title", "Samen brood bakken", "Préparer du pain ensemble", "Baking Bread Together"), g2Choice("other_1", "Voetballen", "Jouer au football", "Playing Soccer"), g2Choice("other_2", "Naar de maan", "Vers la Lune", "To the Moon"))
        ),
        "opening_sentence" to listOf(
            g2Template(g2Text("Je schrijft over een schooluitstap. Welke openingszin past?", "Tu écris sur une sortie scolaire. Quelle phrase d'ouverture convient ?", "You are writing about a school trip. Which opening sentence fits?"), g2Text("Je schrijft over een schooluitstap. Welke openingszin past", "Tu écris sur une sortie scolaire. Quelle phrase d'ouverture convient", "You are writing about a school trip. Which opening sentence fits"), g2Text("Een openingszin maakt het onderwerp duidelijk.", "Une phrase d'ouverture présente clairement le sujet.", "An opening sentence makes the topic clear."), "opening", g2Choice("opening", "Vandaag ging onze klas naar het bos.", "Aujourd'hui, notre classe est allée dans la forêt.", "Today our class went to the forest."), g2Choice("other_1", "Mijn sok is blauw.", "Ma chaussette est bleue.", "My sock is blue."), g2Choice("other_2", "Daarom was het klaar.", "C'est pourquoi c'était fini.", "That is why it was finished.")),
            g2Template(g2Text("Je schrijft over je huisdier. Welke openingszin past?", "Tu écris sur ton animal. Quelle phrase d'ouverture convient ?", "You are writing about your pet. Which opening sentence fits?"), g2Text("Je schrijft over je huisdier. Welke openingszin past", "Tu écris sur ton animal. Quelle phrase d'ouverture convient", "You are writing about your pet. Which opening sentence fits"), g2Text("Noem het onderwerp in de eerste zin.", "Nomme le sujet dans la première phrase.", "Name the topic in the first sentence."), "opening", g2Choice("opening", "Mijn konijn heet Pluis.", "Mon lapin s'appelle Flocon.", "My rabbit is named Snowy."), g2Choice("other_1", "Gisteren regende het hard.", "Hier, il a beaucoup plu.", "It rained hard yesterday."), g2Choice("other_2", "Toen was alles voorbij.", "Puis tout était terminé.", "Then everything was over."))
        ),
        "detail_sentence" to listOf(
            g2Template(g2Text("Tekst: Mijn kat is speels. Welke detailzin past?", "Texte : Mon chat est joueur. Quelle phrase de détail convient ?", "Text: My cat is playful. Which detail sentence fits?"), g2Text("Mijn kat is speels. Welke detailzin past", "Mon chat est joueur. Quelle phrase de détail convient", "My cat is playful. Which detail sentence fits"), g2Text("Een detailzin geeft meer informatie over hetzelfde onderwerp.", "Une phrase de détail donne plus d'informations sur le même sujet.", "A detail sentence gives more information about the same topic."), "detail", g2Choice("detail", "Hij jaagt graag achter een touwtje.", "Il aime courir après une ficelle.", "He likes to chase a string."), g2Choice("other_1", "De bus vertrekt om acht uur.", "Le bus part à huit heures.", "The bus leaves at eight."), g2Choice("other_2", "Een driehoek heeft drie hoeken.", "Un triangle a trois angles.", "A triangle has three corners.")),
            g2Template(g2Text("Tekst: We maakten fruitsalade. Welke detailzin past?", "Texte : Nous avons préparé une salade de fruits. Quelle phrase de détail convient ?", "Text: We made fruit salad. Which detail sentence fits?"), g2Text("We maakten fruitsalade. Welke detailzin past", "Nous avons préparé une salade de fruits. Quelle phrase de détail convient", "We made fruit salad. Which detail sentence fits"), g2Text("Blijf bij hetzelfde onderwerp.", "Reste sur le même sujet.", "Stay on the same topic."), "detail", g2Choice("detail", "We sneden appels, peren en bananen.", "Nous avons coupé des pommes, des poires et des bananes.", "We cut apples, pears, and bananas."), g2Choice("other_1", "Mijn jas hangt aan de kapstok.", "Ma veste est au portemanteau.", "My coat hangs on the hook."), g2Choice("other_2", "De maan draait om de aarde.", "La Lune tourne autour de la Terre.", "The Moon moves around Earth."))
        ),
        "closing_sentence" to listOf(
            g2Template(g2Text("Je tekst gaat over een fijne picknick. Welke slotzin past?", "Ton texte parle d'un chouette pique-nique. Quelle phrase finale convient ?", "Your text is about a fun picnic. Which closing sentence fits?"), g2Text("Je tekst gaat over een fijne picknick. Welke slotzin past", "Ton texte parle d'un chouette pique-nique. Quelle phrase finale convient", "Your text is about a fun picnic. Which closing sentence fits"), g2Text("Een slotzin rondt het onderwerp af.", "Une phrase finale termine le sujet.", "A closing sentence wraps up the topic."), "closing", g2Choice("closing", "We gingen blij weer naar huis.", "Nous sommes rentrés heureux à la maison.", "We happily went home."), g2Choice("other_1", "Eerst pakten we de mand.", "D'abord, nous avons pris le panier.", "First we packed the basket."), g2Choice("other_2", "Een olifant is zwaar.", "Un éléphant est lourd.", "An elephant is heavy.")),
            g2Template(g2Text("Je tekst gaat over een gebouwd kasteel. Welke slotzin past?", "Ton texte parle d'un château construit. Quelle phrase finale convient ?", "Your text is about a castle you built. Which closing sentence fits?"), g2Text("Je tekst gaat over een gebouwd kasteel. Welke slotzin past", "Ton texte parle d'un château construit. Quelle phrase finale convient", "Your text is about a castle you built. Which closing sentence fits"), g2Text("Kies een zin die het verhaal netjes beëindigt.", "Choisis une phrase qui termine bien le récit.", "Choose a sentence that neatly ends the story."), "closing", g2Choice("closing", "Trots bekeken we ons hoge kasteel.", "Nous avons regardé fièrement notre grand château.", "We proudly looked at our tall castle."), g2Choice("other_1", "Eerst zochten we blokken.", "D'abord, nous avons cherché des blocs.", "First we found some blocks."), g2Choice("other_2", "Bananen zijn geel.", "Les bananes sont jaunes.", "Bananas are yellow."))
        ),
        "link_sentences" to listOf(
            g2Template(g2Text("Kies het verbindingswoord: Ik trok mijn jas aan, ___ het regende.", "Choisis le mot de liaison : J'ai mis ma veste ___ il pleuvait.", "Choose the linking word: I put on my coat ___ it was raining."), g2Text("Ik trok mijn jas aan, welk verbindingswoord, het regende", "J'ai mis ma veste, quel mot de liaison, il pleuvait", "I put on my coat, which linking word, it was raining"), g2Text("Zoek het woord dat de reden aangeeft.", "Cherche le mot qui indique la raison.", "Find the word that gives the reason."), "because", g2Choice("because", "omdat", "parce qu'", "because"), g2Choice("then", "daarna", "ensuite", "then"), g2Choice("but", "maar", "mais", "but")),
            g2Template(g2Text("Kies het verbindingswoord: We ruimden op, ___ gingen we spelen.", "Choisis le mot de liaison : Nous avons rangé, ___ nous sommes allés jouer.", "Choose the linking word: We cleaned up, ___ we went to play."), g2Text("We ruimden op, welk verbindingswoord, gingen we spelen", "Nous avons rangé, quel mot de liaison, nous sommes allés jouer", "We cleaned up, which linking word, we went to play"), g2Text("Zoek het woord dat vertelt wat vervolgens gebeurde.", "Cherche le mot qui indique ce qui s'est passé ensuite.", "Find the word that tells what happened next."), "then", g2Choice("then", "daarna", "puis", "then"), g2Choice("because", "omdat", "parce que", "because"), g2Choice("or", "of", "ou", "or"))
        ),
        "revise_short_text" to listOf(
            g2Template(g2Text("Welke korte tekst is netjes nagekeken?", "Quel petit texte est bien corrigé ?", "Which short text has been checked carefully?"), g2Text("Welke korte tekst gebruikt hoofdletters en leestekens correct", "Quel petit texte utilise correctement les majuscules et la ponctuation", "Which short text uses capitals and punctuation correctly"), g2Text("Controleer hoofdletters, volledige zinnen en leestekens.", "Vérifie les majuscules, les phrases complètes et la ponctuation.", "Check capitals, complete sentences, and punctuation."), "revised", g2Choice("revised", "We gingen naar zee. We bouwden een zandkasteel.", "Nous sommes allés à la mer. Nous avons construit un château de sable.", "We went to the beach. We built a sandcastle."), g2Choice("other_1", "we gingen naar zee we bouwden een zandkasteel", "nous sommes allés à la mer nous avons construit un château de sable", "we went to the beach we built a sandcastle"), g2Choice("other_2", "We gingen naar zee? we bouwden een zandkasteel", "Nous sommes allés à la mer ? nous avons construit un château de sable", "We went to the beach? we built a sandcastle")),
            g2Template(g2Text("Welke korte tekst is netjes nagekeken?", "Quel petit texte est bien corrigé ?", "Which short text has been checked carefully?"), g2Text("Welke korte tekst gebruikt hoofdletters en leestekens correct", "Quel petit texte utilise correctement les majuscules et la ponctuation", "Which short text uses capitals and punctuation correctly"), g2Text("Lees de twee zinnen volledig na.", "Relis entièrement les deux phrases.", "Check both sentences completely."), "revised", g2Choice("revised", "Mijn fiets is rood. Ik draag altijd een helm.", "Mon vélo est rouge. Je porte toujours un casque.", "My bicycle is red. I always wear a helmet."), g2Choice("other_1", "mijn fiets is rood ik draag altijd een helm", "mon vélo est rouge je porte toujours un casque", "my bicycle is red i always wear a helmet"), g2Choice("other_2", "Mijn fiets is rood! ik draag altijd een helm", "Mon vélo est rouge ! je porte toujours un casque", "My bicycle is red! i always wear a helmet"))
        )
    )
)

private val listeningPrompt = g2Text(
    "Luister goed en kies het juiste antwoord.",
    "Écoute attentivement et choisis la bonne réponse.",
    "Listen carefully and choose the correct answer."
)

internal object Grade2ExtendedListeningGenerator : Grade2ChoiceBankGenerator(
    generatorId = "generator.g2.language.listening.extended_instructions",
    roadmapEntry = "roadmap.g2.language.listening.extended_instructions",
    idNamespace = "g2.language.listening.extended_instructions",
    skill = "listening_extended_instructions",
    domain = LearningDomain.READING,
    inputMode = InputMode.LISTEN_AND_SELECT,
    automatedScope = "spoken_instruction_detail_choice",
    adultSupportKey = "requiresAdultLiveListeningInteraction",
    extraData = mapOf("audioRequired" to "true"),
    banks = linkedMapOf(
        "two_step_first" to listOf(
            g2Template(listeningPrompt, g2Text("Leg eerst je potlood neer en open daarna je boek. Wat doe je eerst?", "Pose d'abord ton crayon, puis ouvre ton livre. Que fais-tu d'abord ?", "First put down your pencil, then open your book. What do you do first?"), g2Text("Onthoud de eerste stap.", "Retiens la première étape.", "Remember the first step."), "first", g2Choice("first", "het potlood neerleggen", "poser le crayon", "put down the pencil"), g2Choice("second", "het boek openen", "ouvrir le livre", "open the book"), g2Choice("other", "de deur sluiten", "fermer la porte", "close the door")),
            g2Template(listeningPrompt, g2Text("Doe eerst je jas uit en hang hem daarna aan de kapstok. Wat doe je eerst?", "Enlève d'abord ta veste, puis accroche-la au portemanteau. Que fais-tu d'abord ?", "First take off your coat, then hang it on the hook. What do you do first?"), g2Text("Luister naar het woord eerst.", "Écoute le mot d'abord.", "Listen for the word first."), "first", g2Choice("first", "de jas uitdoen", "enlever la veste", "take off the coat"), g2Choice("second", "de jas ophangen", "accrocher la veste", "hang up the coat"), g2Choice("other", "de schoenen wassen", "laver les chaussures", "wash the shoes"))
        ),
        "two_step_last" to listOf(
            g2Template(listeningPrompt, g2Text("Neem een rood blad en teken daarna een cirkel. Wat doe je als laatste?", "Prends une feuille rouge, puis dessine un cercle. Que fais-tu en dernier ?", "Take a red sheet of paper, then draw a circle. What do you do last?"), g2Text("Onthoud de laatste stap.", "Retiens la dernière étape.", "Remember the last step."), "last", g2Choice("last", "een cirkel tekenen", "dessiner un cercle", "draw a circle"), g2Choice("first", "een rood blad nemen", "prendre une feuille rouge", "take red paper"), g2Choice("other", "een vierkant uitknippen", "découper un carré", "cut out a square")),
            g2Template(listeningPrompt, g2Text("Was je handen en droog ze daarna af. Wat doe je als laatste?", "Lave tes mains, puis sèche-les. Que fais-tu en dernier ?", "Wash your hands, then dry them. What do you do last?"), g2Text("Welke handeling komt na daarna?", "Quelle action vient après puis ?", "Which action comes after then?"), "last", g2Choice("last", "de handen afdrogen", "sécher les mains", "dry the hands"), g2Choice("first", "de handen wassen", "laver les mains", "wash the hands"), g2Choice("other", "de tafel dekken", "mettre la table", "set the table"))
        ),
        "three_step_middle" to listOf(
            g2Template(listeningPrompt, g2Text("Zet de beker klaar, vul hem met water en zet hem daarna op tafel. Wat is de middelste stap?", "Prépare le gobelet, remplis-le d'eau, puis pose-le sur la table. Quelle est l'étape du milieu ?", "Set out the cup, fill it with water, then put it on the table. What is the middle step?"), g2Text("Zoek de stap tussen de eerste en de laatste.", "Cherche l'étape entre la première et la dernière.", "Find the step between the first and last."), "middle", g2Choice("middle", "de beker vullen", "remplir le gobelet", "fill the cup"), g2Choice("first", "de beker klaarzetten", "préparer le gobelet", "set out the cup"), g2Choice("last", "de beker op tafel zetten", "poser le gobelet sur la table", "put the cup on the table")),
            g2Template(listeningPrompt, g2Text("Pak je sporttas, doe je schoenen erin en sluit daarna de rits. Wat is de middelste stap?", "Prends ton sac de sport, mets-y tes chaussures, puis ferme la tirette. Quelle est l'étape du milieu ?", "Take your sports bag, put your shoes in it, then close the zipper. What is the middle step?"), g2Text("Onthoud alle drie stappen in volgorde.", "Retiens les trois étapes dans l'ordre.", "Remember all three steps in order."), "middle", g2Choice("middle", "de schoenen in de tas doen", "mettre les chaussures dans le sac", "put the shoes in the bag"), g2Choice("first", "de sporttas nemen", "prendre le sac de sport", "take the sports bag"), g2Choice("last", "de rits sluiten", "fermer la tirette", "close the zipper"))
        ),
        "before_after" to listOf(
            g2Template(listeningPrompt, g2Text("Voordat Amir naar buiten ging, trok hij zijn laarzen aan. Wat deed Amir vóór hij naar buiten ging?", "Avant de sortir, Amir a mis ses bottes. Qu'a fait Amir avant de sortir ?", "Before Amir went outside, he put on his boots. What did Amir do before going outside?"), g2Text("Let op het woord voordat.", "Écoute le mot avant.", "Listen for the word before."), "answer", g2Choice("answer", "zijn laarzen aantrekken", "mettre ses bottes", "put on his boots"), g2Choice("other_1", "zijn laarzen uittrekken", "enlever ses bottes", "take off his boots"), g2Choice("other_2", "zijn lunch opeten", "manger son repas", "eat his lunch")),
            g2Template(listeningPrompt, g2Text("Nadat Zoë haar kamer had opgeruimd, ging ze tekenen. Wat deed Zoë na het opruimen?", "Après avoir rangé sa chambre, Zoé a dessiné. Qu'a fait Zoé après avoir rangé ?", "After Zoey cleaned her room, she started drawing. What did Zoey do after cleaning?"), g2Text("Let op het woord nadat.", "Écoute le mot après.", "Listen for the word after."), "answer", g2Choice("answer", "tekenen", "dessiner", "draw"), g2Choice("other_1", "haar kamer rommelig maken", "mettre sa chambre en désordre", "make her room messy"), g2Choice("other_2", "gaan zwemmen", "aller nager", "go swimming"))
        ),
        "story_who" to listOf(
            g2Template(listeningPrompt, g2Text("Mats vond een gewonde vogel. Zijn buurvrouw belde een dierenopvang. Wie belde de opvang?", "Mats a trouvé un oiseau blessé. Sa voisine a appelé un centre pour animaux. Qui a appelé le centre ?", "Mats found an injured bird. His neighbor called a wildlife center. Who called the center?"), g2Text("Luister naar wie de handeling uitvoert.", "Écoute qui réalise l'action.", "Listen for who performs the action."), "who", g2Choice("who", "de buurvrouw", "la voisine", "the neighbor"), g2Choice("other_1", "Mats", "Mats", "Mats"), g2Choice("other_2", "de vogel", "l'oiseau", "the bird")),
            g2Template(listeningPrompt, g2Text("Lina maakte limonade. Haar broer zette de glazen klaar. Wie zette de glazen klaar?", "Lina a préparé de la limonade. Son frère a placé les verres. Qui a placé les verres ?", "Lina made lemonade. Her brother set out the glasses. Who set out the glasses?"), g2Text("Onthoud wie bij elke taak hoort.", "Retiens qui fait chaque tâche.", "Remember who does each task."), "who", g2Choice("who", "haar broer", "son frère", "her brother"), g2Choice("other_1", "Lina", "Lina", "Lina"), g2Choice("other_2", "de buurvrouw", "la voisine", "the neighbor"))
        ),
        "story_where" to listOf(
            g2Template(listeningPrompt, g2Text("De klas zocht bladeren in het park en sorteerde ze later op school. Waar zocht de klas de bladeren?", "La classe a cherché des feuilles dans le parc et les a triées plus tard à l'école. Où la classe a-t-elle cherché les feuilles ?", "The class looked for leaves in the park and sorted them later at school. Where did the class look for leaves?"), g2Text("Luister naar de plek van de eerste handeling.", "Écoute le lieu de la première action.", "Listen for the place of the first action."), "where", g2Choice("where", "in het park", "dans le parc", "in the park"), g2Choice("other_1", "op school", "à l'école", "at school"), g2Choice("other_2", "in de bakkerij", "à la boulangerie", "at the bakery")),
            g2Template(listeningPrompt, g2Text("Oma kocht appels op de markt en bakte thuis een taart. Waar kocht oma de appels?", "Grand-mère a acheté des pommes au marché et a préparé une tarte à la maison. Où a-t-elle acheté les pommes ?", "Grandma bought apples at the market and baked a pie at home. Where did she buy the apples?"), g2Text("Koppel de plaats aan de juiste gebeurtenis.", "Associe le lieu au bon événement.", "Match the place to the correct event."), "where", g2Choice("where", "op de markt", "au marché", "at the market"), g2Choice("other_1", "thuis", "à la maison", "at home"), g2Choice("other_2", "in de bibliotheek", "à la bibliothèque", "at the library"))
        ),
        "story_reason" to listOf(
            g2Template(listeningPrompt, g2Text("Nora nam een paraplu mee omdat donkere wolken verschenen. Waarom nam ze een paraplu mee?", "Nora a pris un parapluie parce que des nuages sombres apparaissaient. Pourquoi a-t-elle pris un parapluie ?", "Nora took an umbrella because dark clouds appeared. Why did she take an umbrella?"), g2Text("Luister naar de reden na omdat.", "Écoute la raison après parce que.", "Listen for the reason after because."), "reason", g2Choice("reason", "er kwamen donkere wolken", "des nuages sombres arrivaient", "dark clouds were appearing"), g2Choice("other_1", "ze wilde gaan zwemmen", "elle voulait aller nager", "she wanted to swim"), g2Choice("other_2", "haar tas was leeg", "son sac était vide", "her bag was empty")),
            g2Template(listeningPrompt, g2Text("De wedstrijd werd binnen gespeeld omdat het hard regende. Waarom speelden ze binnen?", "Le match s'est joué à l'intérieur parce qu'il pleuvait fort. Pourquoi ont-ils joué à l'intérieur ?", "The game was played indoors because it was raining hard. Why did they play indoors?"), g2Text("Zoek de oorzaak van de keuze.", "Cherche la cause du choix.", "Find the reason for the choice."), "reason", g2Choice("reason", "het regende hard", "il pleuvait fort", "it was raining hard"), g2Choice("other_1", "de zon scheen", "le soleil brillait", "the sun was shining"), g2Choice("other_2", "niemand had een bal", "personne n'avait de ballon", "nobody had a ball"))
        ),
        "select_detail" to listOf(
            g2Template(listeningPrompt, g2Text("Voor de proef heb je een lepel, een glas en koud water nodig. Welk water heb je nodig?", "Pour l'expérience, il faut une cuillère, un verre et de l'eau froide. Quelle eau faut-il ?", "For the experiment you need a spoon, a glass, and cold water. What kind of water do you need?"), g2Text("Luister alleen naar het gevraagde detail.", "Écoute seulement le détail demandé.", "Listen only for the requested detail."), "detail", g2Choice("detail", "koud water", "de l'eau froide", "cold water"), g2Choice("other_1", "warm water", "de l'eau chaude", "hot water"), g2Choice("other_2", "bruiswater", "de l'eau pétillante", "sparkling water")),
            g2Template(listeningPrompt, g2Text("De bus vertrekt vrijdag om negen uur bij de schoolpoort. Op welke dag vertrekt de bus?", "Le bus part vendredi à neuf heures devant l'école. Quel jour part-il ?", "The bus leaves Friday at nine o'clock by the school gate. On which day does the bus leave?"), g2Text("Luister naar het gevraagde tijdsdetail.", "Écoute le détail de temps demandé.", "Listen for the requested time detail."), "detail", g2Choice("detail", "vrijdag", "vendredi", "Friday"), g2Choice("other_1", "donderdag", "jeudi", "Thursday"), g2Choice("other_2", "zaterdag", "samedi", "Saturday"))
        )
    )
)

internal object Grade2DigitalMediaSafetyGenerator : Grade2ChoiceBankGenerator(
    generatorId = "generator.g2.world.digital.media_safety",
    roadmapEntry = "roadmap.g2.world.digital.media_safety",
    idNamespace = "g2.world.digital.media_safety",
    skill = "digital_media_safety",
    domain = LearningDomain.WORLD_KNOWLEDGE,
    inputMode = InputMode.TAP_CHOICE,
    automatedScope = "digital_media_safety_scenario_choice",
    adultSupportKey = "requiresAdultDevicePractice",
    banks = linkedMapOf(
        "device_purpose" to listOf(
            g2Template(g2Text("Welk toestel gebruik je meestal om een foto te maken?", "Quel appareil utilise-t-on généralement pour prendre une photo ?", "Which device do you usually use to take a photo?"), g2Text("Welk toestel gebruik je meestal om een foto te maken", "Quel appareil utilise-t-on généralement pour prendre une photo", "Which device do you usually use to take a photo"), g2Text("Denk aan waarvoor elk toestel is gemaakt.", "Pense à l'usage de chaque appareil.", "Think about what each device is made to do."), "camera", g2Choice("camera", "een camera of tablet", "un appareil photo ou une tablette", "a camera or tablet"), g2Choice("toaster", "een broodrooster", "un grille-pain", "a toaster"), g2Choice("lamp", "een bureaulamp", "une lampe de bureau", "a desk lamp")),
            g2Template(g2Text("Welk hulpmiddel gebruik je om tekst in een vak te typen?", "Quel outil utilise-t-on pour taper du texte dans une case ?", "Which tool do you use to type text into a box?"), g2Text("Welk hulpmiddel gebruik je om tekst te typen", "Quel outil utilise-t-on pour taper du texte", "Which tool do you use to type text"), g2Text("Kies het invoermiddel voor letters en woorden.", "Choisis l'outil de saisie des lettres et des mots.", "Choose the input tool for letters and words."), "keyboard", g2Choice("keyboard", "een toetsenbord", "un clavier", "a keyboard"), g2Choice("speaker", "een luidspreker", "un haut-parleur", "a speaker"), g2Choice("charger", "een oplader", "un chargeur", "a charger"))
        ),
        "personal_information" to listOf(
            g2Template(g2Text("Een spel vraagt je thuisadres. Wat doe je?", "Un jeu demande ton adresse. Que fais-tu ?", "A game asks for your home address. What do you do?"), g2Text("Een spel vraagt je thuisadres. Wat is de veilige keuze", "Un jeu demande ton adresse. Quel est le choix sûr", "A game asks for your home address. What is the safe choice"), g2Text("Deel persoonlijke informatie alleen met toestemming van een vertrouwde volwassene.", "Ne partage une information personnelle qu'avec l'accord d'un adulte de confiance.", "Share personal information only with permission from a trusted adult."), "ask_adult", g2Choice("ask_adult", "niets invullen en een vertrouwde volwassene vragen", "ne rien écrire et demander à un adulte de confiance", "enter nothing and ask a trusted adult"), g2Choice("share", "het adres meteen invullen", "écrire immédiatement l'adresse", "enter the address right away"), g2Choice("guess", "een willekeurig adres verzinnen", "inventer une adresse", "make up a random address")),
            g2Template(g2Text("Een onbekende vraagt online naar je school. Wat doe je?", "Un inconnu en ligne demande le nom de ton école. Que fais-tu ?", "A stranger online asks which school you attend. What do you do?"), g2Text("Een onbekende vraagt online naar je school. Wat is veilig", "Un inconnu en ligne demande ton école. Que faire en sécurité", "A stranger online asks about your school. What is safe"), g2Text("Reageer niet en toon het bericht aan een vertrouwde volwassene.", "Ne réponds pas et montre le message à un adulte de confiance.", "Do not reply; show the message to a trusted adult."), "tell_adult", g2Choice("tell_adult", "niet antwoorden en het aan een vertrouwde volwassene tonen", "ne pas répondre et le montrer à un adulte de confiance", "do not reply and show a trusted adult"), g2Choice("share", "de naam en klas doorsturen", "envoyer le nom et la classe", "send the school name and class"), g2Choice("meet", "vragen om na school af te spreken", "proposer de se rencontrer après l'école", "ask to meet after school"))
        ),
        "password_safety" to listOf(
            g2Template(g2Text("Wie mag je wachtwoord kennen?", "Qui peut connaître ton mot de passe ?", "Who may know your password?"), g2Text("Wie mag je wachtwoord kennen", "Qui peut connaître ton mot de passe", "Who may know your password"), g2Text("Een wachtwoord is geheim; volg de afspraken van je ouder of verzorger.", "Un mot de passe est secret; suis les règles de ton parent.", "A password is secret; follow your parent or caregiver's rules."), "trusted_adult", g2Choice("trusted_adult", "jij en een ouder of verzorger volgens jullie afspraak", "toi et un parent selon vos règles", "you and a parent or caregiver under your family rules"), g2Choice("class", "de hele klas", "toute la classe", "the whole class"), g2Choice("stranger", "iemand die je online net ontmoet", "une personne rencontrée en ligne", "someone you just met online")),
            g2Template(g2Text("Een vriend vraagt je wachtwoord om voor jou te spelen. Wat doe je?", "Un ami demande ton mot de passe pour jouer à ta place. Que fais-tu ?", "A friend asks for your password to play for you. What do you do?"), g2Text("Een vriend vraagt je wachtwoord. Wat is veilig", "Un ami demande ton mot de passe. Quel est le choix sûr", "A friend asks for your password. What is safe"), g2Text("Deel het niet en vraag hulp als je niet kunt inloggen.", "Ne le partage pas et demande de l'aide si tu ne peux pas te connecter.", "Do not share it; ask for help if you cannot sign in."), "keep_secret", g2Choice("keep_secret", "het geheim houden en zo nodig een volwassene vragen", "le garder secret et demander de l'aide à un adulte", "keep it secret and ask an adult if needed"), g2Choice("send", "het in een bericht sturen", "l'envoyer dans un message", "send it in a message"), g2Choice("post", "het bij je profielfoto zetten", "l'ajouter à ta photo de profil", "post it by your profile picture"))
        ),
        "unkind_message" to listOf(
            g2Template(g2Text("Je krijgt online een gemeen bericht. Wat doe je best?", "Tu reçois un message méchant en ligne. Que fais-tu ?", "You receive a mean message online. What should you do?"), g2Text("Je krijgt een gemeen online bericht. Kies een veilige reactie", "Tu reçois un message méchant en ligne. Choisis une réaction sûre", "You receive a mean online message. Choose a safe response"), g2Text("Antwoord niet boos; bewaar het bericht en vraag hulp.", "Ne réponds pas avec colère; garde le message et demande de l'aide.", "Do not reply angrily; keep the message and ask for help."), "report", g2Choice("report", "niet terugschelden en het aan een vertrouwde volwassene tonen", "ne pas insulter et le montrer à un adulte de confiance", "do not insult back and show a trusted adult"), g2Choice("fight", "een nog gemener bericht sturen", "envoyer un message encore plus méchant", "send an even meaner message"), g2Choice("forward", "het naar iedereen doorsturen", "le transférer à tout le monde", "forward it to everyone")),
            g2Template(g2Text("Iemand wordt uitgelachen in een groepschat. Wat is een zorgzame keuze?", "Quelqu'un est ridiculisé dans une discussion de groupe. Quel est le choix bienveillant ?", "Someone is being mocked in a group chat. What is a caring choice?"), g2Text("Iemand wordt online uitgelachen. Wat is een zorgzame en veilige keuze", "Quelqu'un est ridiculisé en ligne. Quel choix est bienveillant et sûr", "Someone is mocked online. What is a caring and safe choice"), g2Text("Doe niet mee en betrek een vertrouwde volwassene.", "Ne participe pas et demande l'aide d'un adulte de confiance.", "Do not join in; involve a trusted adult."), "support", g2Choice("support", "niet meedoen en hulp vragen aan een vertrouwde volwassene", "ne pas participer et demander l'aide d'un adulte de confiance", "do not join in and ask a trusted adult for help"), g2Choice("join", "ook een grap sturen", "envoyer aussi une moquerie", "send another joke about them"), g2Choice("share", "een screenshot rondsturen voor plezier", "partager une capture pour rire", "share a screenshot for fun"))
        ),
        "advertising_choice" to listOf(
            g2Template(g2Text("Een filmpje zegt: Koop dit nu! Wat kan dit zijn?", "Une vidéo dit : Achète ceci maintenant ! Qu'est-ce que cela peut être ?", "A video says: Buy this now! What might it be?"), g2Text("Een filmpje probeert je iets te laten kopen. Wat kan dit zijn", "Une vidéo essaie de te faire acheter quelque chose. Qu'est-ce que cela peut être", "A video tries to make you buy something. What might it be"), g2Text("Reclame probeert aandacht te trekken en iets te verkopen.", "La publicité cherche à attirer l'attention et à vendre quelque chose.", "Advertising tries to get attention and sell something."), "ad", g2Choice("ad", "reclame", "une publicité", "an advertisement"), g2Choice("homework", "een huistaak", "un devoir", "homework"), g2Choice("weather", "een weerbericht", "un bulletin météo", "a weather report")),
            g2Template(g2Text("Een spel toont een grote knop met GRATIS en vraagt daarna om geld. Wat doe je?", "Un jeu affiche GRATUIT puis demande de l'argent. Que fais-tu ?", "A game shows a big FREE button and then asks for money. What do you do?"), g2Text("Een spel vraagt na de knop gratis toch om geld. Wat doe je", "Un jeu demande de l'argent après le bouton gratuit. Que fais-tu", "A game asks for money after a free button. What do you do"), g2Text("Koop niets zonder toestemming van een ouder of verzorger.", "N'achète rien sans l'accord d'un parent.", "Do not buy anything without a parent or caregiver's permission."), "ask", g2Choice("ask", "stoppen en een ouder of verzorger vragen", "arrêter et demander à un parent", "stop and ask a parent or caregiver"), g2Choice("buy", "zo snel mogelijk betalen", "payer le plus vite possible", "pay as quickly as possible"), g2Choice("click", "op alle knoppen blijven tikken", "continuer à toucher tous les boutons", "keep tapping every button"))
        ),
        "check_information" to listOf(
            g2Template(g2Text("Een website zegt iets verrassends over dieren. Hoe controleer je het?", "Un site dit quelque chose de surprenant sur les animaux. Comment vérifier ?", "A website says something surprising about animals. How do you check it?"), g2Text("Hoe controleer je verrassende online informatie", "Comment vérifier une information surprenante en ligne", "How do you check surprising online information"), g2Text("Vraag een volwassene en vergelijk met een betrouwbare bron.", "Demande à un adulte et compare avec une source fiable.", "Ask an adult and compare with a reliable source."), "check", g2Choice("check", "een vertrouwde volwassene vragen en een betrouwbare bron vergelijken", "demander à un adulte de confiance et comparer avec une source fiable", "ask a trusted adult and compare with a reliable source"), g2Choice("believe", "het meteen geloven omdat het online staat", "le croire immédiatement parce que c'est en ligne", "believe it immediately because it is online"), g2Choice("repeat", "het aan iedereen vertellen zonder te controleren", "le raconter à tout le monde sans vérifier", "tell everyone without checking")),
            g2Template(g2Text("Twee websites geven een ander antwoord. Wat doe je?", "Deux sites donnent une réponse différente. Que fais-tu ?", "Two websites give different answers. What do you do?"), g2Text("Twee websites spreken elkaar tegen. Wat doe je", "Deux sites se contredisent. Que fais-tu", "Two websites disagree. What do you do"), g2Text("Een verschil is een signaal om verder te controleren.", "Une différence est un signal qu'il faut vérifier davantage.", "A difference is a signal to check further."), "check", g2Choice("check", "een vertrouwde volwassene vragen en een gekende bron zoeken", "demander à un adulte de confiance et chercher une source connue", "ask a trusted adult and find a known source"), g2Choice("first", "altijd de eerste website kiezen", "toujours choisir le premier site", "always choose the first website"), g2Choice("bright", "de website met de felste kleuren geloven", "croire le site aux couleurs les plus vives", "believe the site with the brightest colors"))
        ),
        "screen_balance" to listOf(
            g2Template(g2Text("Je hebt lang naar een scherm gekeken. Wat is een goede volgende stap?", "Tu as regardé un écran longtemps. Quelle est une bonne étape suivante ?", "You have looked at a screen for a long time. What is a good next step?"), g2Text("Na lang schermgebruik, wat is een gezonde volgende stap", "Après un long moment devant un écran, quelle est une bonne étape", "After a long time on a screen, what is a healthy next step"), g2Text("Volg de gezinsafspraak en neem regelmatig een pauze.", "Suis la règle familiale et fais régulièrement une pause.", "Follow the family rule and take regular breaks."), "break", g2Choice("break", "een pauze nemen en bewegen of ver kijken", "faire une pause, bouger ou regarder au loin", "take a break and move or look far away"), g2Choice("continue", "zonder pauze blijven kijken", "continuer sans pause", "keep watching without a break"), g2Choice("dark", "het scherm in een donkere kamer dichterbij houden", "tenir l'écran plus près dans une pièce sombre", "hold the screen closer in a dark room")),
            g2Template(g2Text("Het is tijd om te slapen, maar een filmpje speelt nog. Wat doe je?", "C'est l'heure de dormir, mais une vidéo continue. Que fais-tu ?", "It is bedtime, but a video is still playing. What do you do?"), g2Text("Het is bedtijd en een filmpje speelt nog. Wat is een goede keuze", "C'est l'heure de dormir et une vidéo continue. Quel est le bon choix", "It is bedtime and a video is still playing. What is a good choice"), g2Text("Stop volgens de gezinsafspraak en leg het toestel weg.", "Arrête selon la règle familiale et range l'appareil.", "Stop according to the family rule and put the device away."), "stop", g2Choice("stop", "stoppen en het toestel op de afgesproken plek leggen", "arrêter et ranger l'appareil à l'endroit prévu", "stop and put the device in its agreed place"), g2Choice("hide", "onder de deken verder kijken", "continuer à regarder sous la couverture", "keep watching under the blanket"), g2Choice("louder", "het geluid harder zetten", "augmenter le son", "turn up the sound"))
        ),
        "photo_permission" to listOf(
            g2Template(g2Text("Je wilt een foto van een vriend delen. Wat doe je eerst?", "Tu veux partager une photo d'un ami. Que fais-tu d'abord ?", "You want to share a photo of a friend. What do you do first?"), g2Text("Voor je een foto van een vriend deelt, wat doe je eerst", "Avant de partager la photo d'un ami, que fais-tu d'abord", "Before sharing a photo of a friend, what do you do first"), g2Text("Vraag toestemming aan de persoon en volg de afspraken van een volwassene.", "Demande l'autorisation à la personne et suis les règles d'un adulte.", "Ask the person for permission and follow an adult's rules."), "permission", g2Choice("permission", "toestemming vragen en een vertrouwde volwassene betrekken", "demander l'autorisation et consulter un adulte de confiance", "ask permission and involve a trusted adult"), g2Choice("post", "de foto meteen openbaar plaatsen", "publier immédiatement la photo", "post the photo publicly right away"), g2Choice("edit", "de foto veranderen en dan stiekem delen", "modifier la photo puis la partager en secret", "edit the photo and share it secretly")),
            g2Template(g2Text("Iemand zegt dat je niet op de foto wilt. Wat doe je?", "Quelqu'un dit qu'il ne veut pas être sur la photo. Que fais-tu ?", "Someone says they do not want to be in the photo. What do you do?"), g2Text("Iemand wil niet op de foto. Wat doe je", "Quelqu'un ne veut pas être sur la photo. Que fais-tu", "Someone does not want to be in the photo. What do you do"), g2Text("Respecteer een nee en maak of deel de foto niet.", "Respecte le refus et ne prends ni ne partage la photo.", "Respect no; do not take or share the photo."), "respect", g2Choice("respect", "hun keuze respecteren en geen foto maken of delen", "respecter son choix et ne pas prendre ni partager la photo", "respect their choice and do not take or share the photo"), g2Choice("secret", "toch stiekem een foto maken", "prendre quand même une photo en secret", "take a secret photo anyway"), g2Choice("pressure", "blijven aandringen tot die persoon ja zegt", "insister jusqu'à ce que la personne accepte", "keep pressuring them to say yes"))
        )
    )
)

internal object Grade2CitizenshipEmotionsConflictsGenerator : Grade2ChoiceBankGenerator(
    generatorId = "generator.g2.world.citizenship.emotions_conflicts",
    roadmapEntry = "roadmap.g2.world.citizenship.emotions_conflicts",
    idNamespace = "g2.world.citizenship.emotions_conflicts",
    skill = "citizenship_emotions_conflicts",
    domain = LearningDomain.WORLD_KNOWLEDGE,
    inputMode = InputMode.TAP_CHOICE,
    automatedScope = "emotion_conflict_scenario_choice",
    adultSupportKey = "requiresAdultSocialPractice",
    banks = linkedMapOf(
        "name_emotion" to listOf(
            g2Template(g2Text("Sam zoekt zijn verdwenen hond en weet niet waar hij is. Hoe kan Sam zich voelen?", "Sam cherche son chien disparu et ne sait pas où il est. Comment peut-il se sentir ?", "Sam is looking for his missing dog and does not know where it is. How might Sam feel?"), g2Text("Hoe kan Sam zich voelen wanneer zijn hond verdwenen is", "Comment Sam peut-il se sentir quand son chien a disparu", "How might Sam feel when his dog is missing"), g2Text("Meer dan één gevoel is mogelijk; kies het gevoel dat duidelijk bij de situatie past.", "Plusieurs émotions sont possibles; choisis celle qui correspond clairement à la situation.", "More than one feeling can be possible; choose the one that clearly fits the situation."), "worried", g2Choice("worried", "bezorgd", "inquiet", "worried"), g2Choice("proud", "trots op een prestatie", "fier d'une réussite", "proud of an achievement"), g2Choice("bored", "verveeld door niets te doen", "ennuyé de ne rien faire", "bored from having nothing to do")),
            g2Template(g2Text("Aya oefende lang en kan nu zonder hulp fietsen. Hoe kan Aya zich voelen?", "Aya s'est beaucoup entraînée et sait maintenant rouler à vélo sans aide. Comment peut-elle se sentir ?", "Aya practiced for a long time and can now ride a bicycle without help. How might Aya feel?"), g2Text("Hoe kan Aya zich voelen na wat ze geleerd heeft", "Comment Aya peut-elle se sentir après ce qu'elle a appris", "How might Aya feel after what she learned"), g2Text("Kijk naar wat er net gebeurd is.", "Regarde ce qui vient de se passer.", "Look at what just happened."), "proud", g2Choice("proud", "trots", "fière", "proud"), g2Choice("worried", "bezorgd omdat iets zoek is", "inquiète parce que quelque chose est perdu", "worried because something is missing"), g2Choice("lonely", "eenzaam omdat niemand er is", "seule parce que personne n'est là", "lonely because nobody is there"))
        ),
        "notice_signal" to listOf(
            g2Template(g2Text("Je hart klopt snel en je handen zijn gespannen vóór je moet spreken. Wat kan helpen?", "Ton cœur bat vite et tes mains sont tendues avant de parler. Qu'est-ce qui peut aider ?", "Your heart beats fast and your hands feel tense before you speak. What might help?"), g2Text("Wat kan helpen wanneer je lichaam spanning aangeeft voor je spreekt", "Que peut aider quand ton corps montre de la tension avant de parler", "What might help when your body shows tension before speaking"), g2Text("Een lichaamssignaal kan je eraan herinneren rustig te ademen en hulp te vragen.", "Un signal du corps peut te rappeler de respirer calmement et de demander de l'aide.", "A body signal can remind you to breathe calmly and ask for help."), "calm", g2Choice("calm", "rustig ademen en zo nodig een volwassene om steun vragen", "respirer calmement et demander du soutien à un adulte si nécessaire", "breathe calmly and ask an adult for support if needed"), g2Choice("hide", "doen alsof je niets voelt en weglopen zonder iets te zeggen", "faire semblant de ne rien sentir et partir sans rien dire", "pretend you feel nothing and run away without saying anything"), g2Choice("shout", "tegen iemand schreeuwen", "crier sur quelqu'un", "shout at someone")),
            g2Template(g2Text("Je merkt dat je kaken strak worden tijdens een ruzie. Wat is een veilige eerste stap?", "Tu remarques que ta mâchoire se serre pendant une dispute. Quelle est une première étape sûre ?", "You notice your jaw tightening during an argument. What is a safe first step?"), g2Text("Wat is een veilige eerste stap wanneer je spanning merkt tijdens een ruzie", "Quelle est une première étape sûre quand tu sens de la tension pendant une dispute", "What is a safe first step when you notice tension during an argument"), g2Text("Pauzeer voordat je reageert.", "Fais une pause avant de réagir.", "Pause before reacting."), "pause", g2Choice("pause", "even stoppen, afstand nemen en rustig ademen", "s'arrêter, prendre un peu de distance et respirer calmement", "pause, take a little space, and breathe calmly"), g2Choice("push", "de ander duwen", "pousser l'autre", "push the other person"), g2Choice("insult", "meteen een belediging roepen", "lancer immédiatement une insulte", "immediately yell an insult"))
        ),
        "calm_strategy" to listOf(
            g2Template(g2Text("Je bent boos maar iedereen is veilig. Wat is een rustige keuze?", "Tu es en colère mais tout le monde est en sécurité. Quel est un choix calme ?", "You are angry, but everyone is safe. What is a calm choice?"), g2Text("Welke rustige keuze kan helpen als je boos bent en iedereen veilig is", "Quel choix calme peut aider quand tu es en colère et que tout le monde est en sécurité", "Which calm choice can help when you are angry and everyone is safe"), g2Text("Kies een pauze die niemand pijn doet.", "Choisis une pause qui ne fait de mal à personne.", "Choose a pause that does not hurt anyone."), "calm", g2Choice("calm", "tot vijf tellen en rustig ademen", "compter jusqu'à cinq et respirer calmement", "count to five and breathe calmly"), g2Choice("break", "iets kapotmaken", "casser quelque chose", "break something"), g2Choice("blame", "iemand uitschelden", "insulter quelqu'un", "call someone names")),
            g2Template(g2Text("Je bent teleurgesteld omdat je team verloor. Wat kan helpen?", "Tu es déçu parce que ton équipe a perdu. Qu'est-ce qui peut aider ?", "You are disappointed because your team lost. What can help?"), g2Text("Wat kan helpen wanneer je teleurgesteld bent na een verloren spel", "Qu'est-ce qui peut aider quand tu es déçu après un jeu perdu", "What can help when you are disappointed after losing a game"), g2Text("Erken het gevoel en kies een respectvolle volgende stap.", "Reconnais l'émotion et choisis une étape respectueuse.", "Notice the feeling and choose a respectful next step."), "accept", g2Choice("accept", "zeggen dat je teleurgesteld bent en de anderen feliciteren", "dire que tu es déçu et féliciter les autres", "say you feel disappointed and congratulate the others"), g2Choice("cheat", "zeggen dat iedereen vals speelde", "dire que tout le monde a triché", "say everyone cheated"), g2Choice("throw", "het spelmateriaal weggooien", "jeter le matériel de jeu", "throw the game pieces"))
        ),
        "i_message" to listOf(
            g2Template(g2Text("Iemand pakt je potlood zonder te vragen. Welke zin legt je gevoel en wens rustig uit?", "Quelqu'un prend ton crayon sans demander. Quelle phrase explique calmement ton émotion et ton souhait ?", "Someone takes your pencil without asking. Which sentence calmly explains your feeling and wish?"), g2Text("Welke ik-boodschap legt je gevoel en wens rustig uit", "Quel message en je explique calmement ton émotion et ton souhait", "Which I-message calmly explains your feeling and wish"), g2Text("Vertel wat je voelt, waarom, en wat je graag wilt.", "Dis ce que tu ressens, pourquoi, et ce que tu souhaites.", "Say what you feel, why, and what you would like."), "i_message", g2Choice("i_message", "Ik vind het vervelend als je mijn potlood neemt. Wil je het eerst vragen?", "Je n'aime pas quand tu prends mon crayon. Peux-tu d'abord demander ?", "I feel upset when you take my pencil. Could you ask first?"), g2Choice("attack", "Jij bent altijd gemeen!", "Tu es toujours méchant !", "You are always mean!"), g2Choice("threat", "Geef het terug of ik maak iets van jou stuk.", "Rends-le ou je casse quelque chose à toi.", "Give it back or I will break something of yours.")),
            g2Template(g2Text("Een klasgenoot praat door je uitleg heen. Welke zin is rustig en duidelijk?", "Un camarade parle pendant ton explication. Quelle phrase est calme et claire ?", "A classmate talks over your explanation. Which sentence is calm and clear?"), g2Text("Welke rustige zin legt uit wat je nodig hebt", "Quelle phrase calme explique ce dont tu as besoin", "Which calm sentence explains what you need"), g2Text("Gebruik een concrete situatie en een vriendelijk verzoek.", "Utilise une situation précise et une demande aimable.", "Use a specific situation and a kind request."), "i_message", g2Choice("i_message", "Ik kan moeilijk uitleggen als je praat. Wil je even luisteren?", "J'ai du mal à expliquer quand tu parles. Peux-tu écouter un moment ?", "It is hard for me to explain while you talk. Could you listen for a moment?"), g2Choice("attack", "Jij luistert nooit!", "Tu n'écoutes jamais !", "You never listen!"), g2Choice("silence", "Niets zeggen en boos blijven.", "Ne rien dire et rester fâché.", "Say nothing and stay angry."))
        ),
        "take_turns" to listOf(
            g2Template(g2Text("Twee kinderen willen tegelijk vertellen. Wat helpt?", "Deux enfants veulent parler en même temps. Qu'est-ce qui aide ?", "Two children want to talk at the same time. What helps?"), g2Text("Wat helpt wanneer twee kinderen tegelijk willen vertellen", "Qu'est-ce qui aide quand deux enfants veulent parler en même temps", "What helps when two children want to talk at the same time"), g2Text("Spreek een beurt af en luister tot de ander klaar is.", "Décidez d'un tour et écoutez jusqu'à ce que l'autre ait fini.", "Agree on turns and listen until the other person is done."), "turns", g2Choice("turns", "om de beurt spreken en naar elkaar luisteren", "parler à tour de rôle et s'écouter", "take turns speaking and listen to each other"), g2Choice("louder", "steeds harder praten", "parler de plus en plus fort", "talk louder and louder"), g2Choice("leave", "zonder uitleg allebei weglopen", "partir tous les deux sans expliquer", "both walk away without explaining")),
            g2Template(g2Text("Je werkt samen aan een tekening. Hoe zorg je dat iedereen meedoet?", "Vous faites un dessin ensemble. Comment faire participer tout le monde ?", "You are making a drawing together. How do you include everyone?"), g2Text("Hoe zorg je dat iedereen aan de gezamenlijke tekening kan meedoen", "Comment faire participer tout le monde au dessin commun", "How do you make sure everyone can join the shared drawing"), g2Text("Verdeel beurten of taken in overleg.", "Répartissez les tours ou les tâches ensemble.", "Share turns or tasks by agreement."), "share", g2Choice("share", "samen taken afspreken en materiaal delen", "décider ensemble des tâches et partager le matériel", "agree on tasks and share materials"), g2Choice("control", "één kind laat alles alleen doen", "un enfant fait tout seul", "one child does everything"), g2Choice("exclude", "iemand zeggen dat die niet mag tekenen", "dire à quelqu'un qu'il ne peut pas dessiner", "tell someone they cannot draw"))
        ),
        "fair_solution" to listOf(
            g2Template(g2Text("Er is één bal en twee kinderen willen ermee spelen. Wat is een eerlijke oplossing?", "Il y a un ballon et deux enfants veulent jouer avec. Quelle est une solution équitable ?", "There is one ball and two children want to play with it. What is a fair solution?"), g2Text("Welke oplossing houdt rekening met beide kinderen", "Quelle solution tient compte des deux enfants", "Which solution considers both children"), g2Text("Een eerlijke oplossing geeft iedereen een kans.", "Une solution équitable donne une chance à chacun.", "A fair solution gives everyone a chance."), "fair", g2Choice("fair", "samen spelen of duidelijke beurten afspreken", "jouer ensemble ou décider de tours clairs", "play together or agree on clear turns"), g2Choice("grab", "de sterkste pakt de bal", "le plus fort prend le ballon", "the strongest person takes the ball"), g2Choice("hide", "de bal verstoppen zodat niemand speelt", "cacher le ballon pour que personne ne joue", "hide the ball so nobody plays")),
            g2Template(g2Text("Twee kinderen willen dezelfde blauwe stift. Wat is een werkbare oplossing?", "Deux enfants veulent le même feutre bleu. Quelle solution peut fonctionner ?", "Two children want the same blue marker. What solution can work?"), g2Text("Welke oplossing helpt beide kinderen verder", "Quelle solution aide les deux enfants", "Which solution helps both children continue"), g2Text("Zoek een afspraak die veilig en haalbaar is.", "Cherche un accord sûr et réalisable.", "Find an agreement that is safe and workable."), "fair", g2Choice("fair", "om de beurt gebruiken of samen een andere kleur kiezen", "l'utiliser à tour de rôle ou choisir ensemble une autre couleur", "take turns or choose another color together"), g2Choice("tear", "de stift uit elkaars handen trekken", "arracher le feutre des mains", "pull the marker from each other's hands"), g2Choice("throw", "de stift weggooien", "jeter le feutre", "throw the marker away"))
        ),
        "seek_adult_help" to listOf(
            g2Template(g2Text("Een ruzie wordt dreigend en iemand duwt. Wat doe je?", "Une dispute devient menaçante et quelqu'un pousse. Que fais-tu ?", "An argument becomes threatening and someone pushes. What do you do?"), g2Text("Wat doe je wanneer een ruzie onveilig wordt", "Que fais-tu quand une dispute devient dangereuse", "What do you do when an argument becomes unsafe"), g2Text("Ga naar veiligheid en haal meteen een vertrouwde volwassene.", "Mets-toi en sécurité et appelle immédiatement un adulte de confiance.", "Move to safety and get a trusted adult immediately."), "adult", g2Choice("adult", "afstand nemen en meteen een vertrouwde volwassene halen", "s'éloigner et appeler immédiatement un adulte de confiance", "move away and get a trusted adult immediately"), g2Choice("fight", "terugduwen om te winnen", "repousser pour gagner", "push back to win"), g2Choice("watch", "blijven kijken zonder hulp te halen", "rester regarder sans chercher d'aide", "keep watching without getting help")),
            g2Template(g2Text("Iemand wordt elke dag expres buitengesloten. Wat is belangrijk?", "Quelqu'un est volontairement exclu chaque jour. Qu'est-ce qui est important ?", "Someone is deliberately excluded every day. What is important?"), g2Text("Wat is belangrijk wanneer iemand herhaaldelijk wordt buitengesloten", "Qu'est-ce qui est important quand quelqu'un est exclu à plusieurs reprises", "What is important when someone is repeatedly excluded"), g2Text("Herhaald kwetsend gedrag vraagt hulp van een volwassene; het kind hoeft dit niet alleen op te lossen.", "Un comportement blessant répété demande l'aide d'un adulte; l'enfant ne doit pas le résoudre seul.", "Repeated hurtful behavior needs adult help; a child should not solve it alone."), "adult", g2Choice("adult", "een vertrouwde volwassene vertellen wat er gebeurt", "dire à un adulte de confiance ce qui se passe", "tell a trusted adult what is happening"), g2Choice("ignore", "zeggen dat het kind zich niet moet aanstellen", "dire à l'enfant d'arrêter de se plaindre", "tell the child to stop making a fuss"), g2Choice("revenge", "de anderen later ook buitensluiten", "exclure les autres plus tard", "exclude the others later"))
        ),
        "include_others" to listOf(
            g2Template(g2Text("Een nieuw kind staat alleen op de speelplaats. Wat is een vriendelijke keuze?", "Un nouvel élève est seul dans la cour. Quel est un choix amical ?", "A new child is alone on the playground. What is a friendly choice?"), g2Text("Wat is een vriendelijke keuze voor een nieuw kind dat alleen staat", "Quel est un choix amical pour un nouvel élève seul", "What is a friendly choice for a new child standing alone"), g2Text("Bied contact aan zonder druk uit te oefenen.", "Propose un contact sans mettre de pression.", "Offer connection without pressure."), "invite", g2Choice("invite", "vriendelijk vragen of het kind wil meedoen", "demander gentiment si l'enfant veut participer", "kindly ask whether the child wants to join"), g2Choice("laugh", "lachen omdat het kind niemand kent", "rire parce que l'enfant ne connaît personne", "laugh because the child knows nobody"), g2Choice("force", "het kind dwingen mee te spelen", "forcer l'enfant à jouer", "force the child to play")),
            g2Template(g2Text("Een klasgenoot heeft een ander idee voor het groepswerk. Wat doe je?", "Un camarade a une autre idée pour le travail de groupe. Que fais-tu ?", "A classmate has a different idea for the group project. What do you do?"), g2Text("Wat doe je met een ander idee in groepswerk", "Que fais-tu avec une autre idée dans un travail de groupe", "What do you do with a different idea in group work"), g2Text("Luister en bespreek wat bruikbaar is.", "Écoute et discute de ce qui peut être utile.", "Listen and discuss what might be useful."), "listen", g2Choice("listen", "luisteren en samen de ideeën vergelijken", "écouter et comparer les idées ensemble", "listen and compare the ideas together"), g2Choice("reject", "het idee meteen belachelijk noemen", "se moquer immédiatement de l'idée", "mock the idea right away"), g2Choice("silence", "zeggen dat alleen jouw idee telt", "dire que seule ton idée compte", "say only your idea matters"))
        )
    )
)
