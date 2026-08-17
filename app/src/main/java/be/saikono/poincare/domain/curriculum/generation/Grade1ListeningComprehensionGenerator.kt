package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 1 listening-comprehension generator.
 *
 * Version 1 keeps the child-facing prompt generic while placing the complete
 * instruction or short story in [ExerciseContent.speechText]. Every exercise
 * uses three localized tap choices and a stable language-neutral answer key.
 */
object Grade1ListeningComprehensionGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g1.language.listening.instructions_stories"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY =
        "roadmap.g1.language.listening.instructions_stories"

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
        error("Unable to create a unique Grade 1 listening exercise for ${kind.name}")
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
        val id = "generated.g1.language.listening.v1.${seed.fingerprint}.${index + 1}"
        return ExerciseContent(
            id = id,
            domain = LearningDomain.READING,
            skill = "listening_comprehension",
            section = if (phase == Phase.GUIDED) ExerciseSection.PRACTICE else ExerciseSection.TESTS,
            level = SkillLevel.FIRST_GRADE,
            inputMode = InputMode.LISTEN_AND_SELECT,
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
                "audioRequired" to "true",
                "masteryTag" to "listening.${candidate.kind.name.lowercase()}",
                "errorTags" to "listening.sequence_or_detail",
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
                "feedback_nl-BE" to "Luister opnieuw naar de volledige opdracht en let op de volgorde en de details.",
                "feedback_fr-BE" to "Réécoute toute la consigne et fais attention à l'ordre et aux détails.",
                "feedback_en-US" to "Listen to the full instruction again and notice the order and details."
            ),
            answer = candidate.answerKey
        )
    }

    private fun bank(kind: PromptKind): List<Template> = when (kind) {
        PromptKind.ONE_STEP_INSTRUCTION -> listOf(
            template(
                genericPrompt(),
                localized(
                    "Luister goed. Tik op de rode ster. Wat kies je.",
                    "Écoute bien. Touche l'étoile rouge. Que choisis-tu.",
                    "Listen carefully. Tap the red star. What do you choose."
                ),
                "target",
                choice("target", "de rode ster", "l'étoile rouge", "the red star"),
                choice("other_colour", "de blauwe ster", "l'étoile bleue", "the blue star"),
                choice("other_shape", "de rode maan", "la lune rouge", "the red moon")
            ),
            template(
                genericPrompt(),
                localized(
                    "Luister goed. Kies de kleine groene cirkel. Wat kies je.",
                    "Écoute bien. Choisis le petit cercle vert. Que choisis-tu.",
                    "Listen carefully. Choose the small green circle. What do you choose."
                ),
                "target",
                choice("target", "de kleine groene cirkel", "le petit cercle vert", "the small green circle"),
                choice("other_size", "de grote groene cirkel", "le grand cercle vert", "the large green circle"),
                choice("other_colour", "de kleine gele cirkel", "le petit cercle jaune", "the small yellow circle")
            ),
            template(
                genericPrompt(),
                localized(
                    "Luister goed. Wijs de gele raket aan. Wat kies je.",
                    "Écoute bien. Montre la fusée jaune. Que choisis-tu.",
                    "Listen carefully. Point to the yellow rocket. What do you choose."
                ),
                "target",
                choice("target", "de gele raket", "la fusée jaune", "the yellow rocket"),
                choice("other_colour", "de blauwe raket", "la fusée bleue", "the blue rocket"),
                choice("other_object", "de gele planeet", "la planète jaune", "the yellow planet")
            )
        )

        PromptKind.TWO_STEP_FIRST -> listOf(
            template(
                genericPrompt(),
                localized(
                    "Pak eerst je potlood en open daarna je boek. Wat doe je eerst.",
                    "Prends d'abord ton crayon puis ouvre ton livre. Que fais-tu d'abord.",
                    "First pick up your pencil and then open your book. What do you do first."
                ),
                "first_action",
                choice("first_action", "het potlood pakken", "prendre le crayon", "pick up the pencil"),
                choice("second_action", "het boek openen", "ouvrir le livre", "open the book"),
                choice("unmentioned", "de tas sluiten", "fermer le sac", "close the bag")
            ),
            template(
                genericPrompt(),
                localized(
                    "Zet eerst je beker neer en klap daarna in je handen. Wat doe je eerst.",
                    "Pose d'abord ton gobelet puis frappe dans tes mains. Que fais-tu d'abord.",
                    "First put down your cup and then clap your hands. What do you do first."
                ),
                "first_action",
                choice("first_action", "de beker neerzetten", "poser le gobelet", "put down the cup"),
                choice("second_action", "in de handen klappen", "frapper dans les mains", "clap your hands"),
                choice("unmentioned", "de beker vasthouden", "tenir le gobelet", "hold the cup")
            ),
            template(
                genericPrompt(),
                localized(
                    "Doe eerst je jas aan en neem daarna je boekentas. Wat doe je eerst.",
                    "Mets d'abord ta veste puis prends ton cartable. Que fais-tu d'abord.",
                    "First put on your coat and then take your schoolbag. What do you do first."
                ),
                "first_action",
                choice("first_action", "de jas aandoen", "mettre la veste", "put on the coat"),
                choice("second_action", "de boekentas nemen", "prendre le cartable", "take the schoolbag"),
                choice("unmentioned", "de schoenen uitdoen", "enlever les chaussures", "take off the shoes")
            )
        )

        PromptKind.TWO_STEP_LAST -> listOf(
            template(
                genericPrompt(),
                localized(
                    "Ga eerst zitten en leg daarna je handen op tafel. Wat doe je als laatste.",
                    "Assieds-toi d'abord puis pose tes mains sur la table. Que fais-tu en dernier.",
                    "First sit down and then put your hands on the table. What do you do last."
                ),
                "last_action",
                choice("last_action", "de handen op tafel leggen", "poser les mains sur la table", "put your hands on the table"),
                choice("first_action", "gaan zitten", "s'asseoir", "sit down"),
                choice("unmentioned", "rechtstaan", "se lever", "stand up")
            ),
            template(
                genericPrompt(),
                localized(
                    "Open eerst de doos en leg daarna de bal erin. Wat doe je als laatste.",
                    "Ouvre d'abord la boîte puis mets la balle dedans. Que fais-tu en dernier.",
                    "First open the box and then put the ball inside. What do you do last."
                ),
                "last_action",
                choice("last_action", "de bal in de doos leggen", "mettre la balle dans la boîte", "put the ball in the box"),
                choice("first_action", "de doos openen", "ouvrir la boîte", "open the box"),
                choice("unmentioned", "de doos wegzetten", "ranger la boîte", "put the box away")
            ),
            template(
                genericPrompt(),
                localized(
                    "Was eerst je handen en droog ze daarna af. Wat doe je als laatste.",
                    "Lave d'abord tes mains puis sèche-les. Que fais-tu en dernier.",
                    "First wash your hands and then dry them. What do you do last."
                ),
                "last_action",
                choice("last_action", "de handen afdrogen", "sécher les mains", "dry the hands"),
                choice("first_action", "de handen wassen", "laver les mains", "wash the hands"),
                choice("unmentioned", "de handen nat laten", "laisser les mains mouillées", "leave the hands wet")
            )
        )

        PromptKind.WHO -> listOf(
            template(
                storyPrompt(),
                localized(
                    "Lina geeft de bal aan Omar. Wie krijgt de bal.",
                    "Lina donne le ballon à Omar. Qui reçoit le ballon.",
                    "Lina gives the ball to Omar. Who receives the ball."
                ),
                "person",
                choice("person", "Omar", "Omar", "Omar"),
                choice("giver", "Lina", "Lina", "Lina"),
                choice("other", "Mila", "Mila", "Mila")
            ),
            template(
                storyPrompt(),
                localized(
                    "Noor leest een boek voor aan Jules. Wie luistert naar het verhaal.",
                    "Noor lit une histoire à Jules. Qui écoute l'histoire.",
                    "Noor reads a story to Jules. Who listens to the story."
                ),
                "person",
                choice("person", "Jules", "Jules", "Jules"),
                choice("speaker", "Noor", "Noor", "Noor"),
                choice("other", "Sam", "Sam", "Sam")
            ),
            template(
                storyPrompt(),
                localized(
                    "Mila helpt Finn met zijn jas. Wie krijgt hulp.",
                    "Mila aide Finn à mettre sa veste. Qui reçoit de l'aide.",
                    "Mila helps Finn with his coat. Who receives help."
                ),
                "person",
                choice("person", "Finn", "Finn", "Finn"),
                choice("helper", "Mila", "Mila", "Mila"),
                choice("other", "Lina", "Lina", "Lina")
            )
        )

        PromptKind.WHAT -> listOf(
            template(
                storyPrompt(),
                localized(
                    "Ravi legt een appel en een banaan in zijn tas. Daarna eet hij de banaan. Wat eet Ravi.",
                    "Ravi met une pomme et une banane dans son sac. Ensuite il mange la banane. Que mange Ravi.",
                    "Ravi puts an apple and a banana in his bag. Then he eats the banana. What does Ravi eat."
                ),
                "object",
                choice("object", "de banaan", "la banane", "the banana"),
                choice("other_1", "de appel", "la pomme", "the apple"),
                choice("other_2", "de tas", "le sac", "the bag")
            ),
            template(
                storyPrompt(),
                localized(
                    "Aya tekent een zon en kleurt daarna een regenboog. Wat kleurt Aya.",
                    "Aya dessine un soleil puis colorie un arc-en-ciel. Que colorie Aya.",
                    "Aya draws a sun and then colors a rainbow. What does Aya color."
                ),
                "object",
                choice("object", "de regenboog", "l'arc-en-ciel", "the rainbow"),
                choice("other_1", "de zon", "le soleil", "the sun"),
                choice("other_2", "de wolk", "le nuage", "the cloud")
            ),
            template(
                storyPrompt(),
                localized(
                    "Leo zet zijn schoenen bij de deur en hangt zijn jas aan de kapstok. Wat hangt Leo op.",
                    "Leo pose ses chaussures près de la porte et accroche sa veste au portemanteau. Qu'accroche Leo.",
                    "Leo puts his shoes by the door and hangs his coat on the hook. What does Leo hang up."
                ),
                "object",
                choice("object", "zijn jas", "sa veste", "his coat"),
                choice("other_1", "zijn schoenen", "ses chaussures", "his shoes"),
                choice("other_2", "de deur", "la porte", "the door")
            )
        )

        PromptKind.WHERE -> listOf(
            template(
                storyPrompt(),
                localized(
                    "Mila legt haar boek in de rugzak. Waar ligt het boek.",
                    "Mila met son livre dans le sac à dos. Où se trouve le livre.",
                    "Mila puts her book in the backpack. Where is the book."
                ),
                "place",
                choice("place", "in de rugzak", "dans le sac à dos", "in the backpack"),
                choice("other_1", "op de tafel", "sur la table", "on the table"),
                choice("other_2", "onder de stoel", "sous la chaise", "under the chair")
            ),
            template(
                storyPrompt(),
                localized(
                    "De kat slaapt op de mat naast de deur. Waar slaapt de kat.",
                    "Le chat dort sur le tapis près de la porte. Où dort le chat.",
                    "The cat sleeps on the mat beside the door. Where does the cat sleep."
                ),
                "place",
                choice("place", "op de mat", "sur le tapis", "on the mat"),
                choice("other_1", "in de kast", "dans l'armoire", "in the cupboard"),
                choice("other_2", "op het bed", "sur le lit", "on the bed")
            ),
            template(
                storyPrompt(),
                localized(
                    "Finn zet de beker op het aanrecht. Waar staat de beker.",
                    "Finn pose le gobelet sur le plan de travail. Où se trouve le gobelet.",
                    "Finn puts the cup on the counter. Where is the cup."
                ),
                "place",
                choice("place", "op het aanrecht", "sur le plan de travail", "on the counter"),
                choice("other_1", "in de koelkast", "dans le réfrigérateur", "in the refrigerator"),
                choice("other_2", "onder de tafel", "sous la table", "under the table")
            )
        )

        PromptKind.WHY -> listOf(
            template(
                storyPrompt(),
                localized(
                    "Het regent buiten. Sami neemt een paraplu mee. Waarom neemt Sami een paraplu.",
                    "Il pleut dehors. Sami prend un parapluie. Pourquoi Sami prend-il un parapluie.",
                    "It is raining outside. Sami takes an umbrella. Why does Sami take an umbrella."
                ),
                "reason",
                choice("reason", "omdat het regent", "parce qu'il pleut", "because it is raining"),
                choice("other_1", "omdat het donker is", "parce qu'il fait sombre", "because it is dark"),
                choice("other_2", "omdat hij honger heeft", "parce qu'il a faim", "because he is hungry")
            ),
            template(
                storyPrompt(),
                localized(
                    "Lina heeft koude handen. Ze trekt wanten aan. Waarom trekt Lina wanten aan.",
                    "Lina a froid aux mains. Elle met des moufles. Pourquoi met-elle des moufles.",
                    "Lina has cold hands. She puts on mittens. Why does Lina put on mittens."
                ),
                "reason",
                choice("reason", "om haar handen warm te houden", "pour garder ses mains au chaud", "to keep her hands warm"),
                choice("other_1", "om sneller te lopen", "pour courir plus vite", "to run faster"),
                choice("other_2", "om beter te horen", "pour mieux entendre", "to hear better")
            ),
            template(
                storyPrompt(),
                localized(
                    "De plant hangt slap. Noor geeft de plant water. Waarom geeft Noor water.",
                    "La plante est toute molle. Noor lui donne de l'eau. Pourquoi Noor donne-t-elle de l'eau.",
                    "The plant is drooping. Noor waters the plant. Why does Noor water it."
                ),
                "reason",
                choice("reason", "omdat de plant water nodig heeft", "parce que la plante a besoin d'eau", "because the plant needs water"),
                choice("other_1", "omdat de plant lawaai maakt", "parce que la plante fait du bruit", "because the plant is noisy"),
                choice("other_2", "omdat de pot leeg is", "parce que le pot est vide", "because the pot is empty")
            )
        )

        PromptKind.DETAIL -> listOf(
            template(
                storyPrompt(),
                localized(
                    "Eli heeft een rode beker en een blauwe lepel. Welke kleur heeft de beker.",
                    "Eli a un gobelet rouge et une cuillère bleue. De quelle couleur est le gobelet.",
                    "Eli has a red cup and a blue spoon. What color is the cup."
                ),
                "detail",
                choice("detail", "rood", "rouge", "red"),
                choice("other_1", "blauw", "bleu", "blue"),
                choice("other_2", "groen", "vert", "green")
            ),
            template(
                storyPrompt(),
                localized(
                    "Maya ziet drie eenden en één zwaan bij de vijver. Hoeveel eenden ziet Maya.",
                    "Maya voit trois canards et un cygne près de l'étang. Combien de canards voit Maya.",
                    "Maya sees three ducks and one swan by the pond. How many ducks does Maya see."
                ),
                "detail",
                choice("detail", "drie", "trois", "three"),
                choice("other_1", "één", "un", "one"),
                choice("other_2", "twee", "deux", "two")
            ),
            template(
                storyPrompt(),
                localized(
                    "De kleine raket heeft twee ronde ramen en één deur. Hoeveel ramen heeft de raket.",
                    "La petite fusée a deux fenêtres rondes et une porte. Combien de fenêtres a la fusée.",
                    "The small rocket has two round windows and one door. How many windows does the rocket have."
                ),
                "detail",
                choice("detail", "twee", "deux", "two"),
                choice("other_1", "één", "un", "one"),
                choice("other_2", "drie", "trois", "three")
            )
        )
    }

    private fun genericPrompt(): LocalizedText = localized(
        "Luister goed en kies het juiste antwoord.",
        "Écoute bien et choisis la bonne réponse.",
        "Listen carefully and choose the correct answer."
    )

    private fun storyPrompt(): LocalizedText = localized(
        "Luister naar het korte verhaal en kies het antwoord.",
        "Écoute la courte histoire et choisis la réponse.",
        "Listen to the short story and choose the answer."
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
        PromptKind.ONE_STEP_INSTRUCTION -> "Luister naar het voorwerp, de kleur en de vorm."
        PromptKind.TWO_STEP_FIRST -> "Onthoud wat je eerst moet doen."
        PromptKind.TWO_STEP_LAST -> "Onthoud de tweede stap."
        PromptKind.WHO -> "Luister naar de namen en naar wie iets krijgt of doet."
        PromptKind.WHAT -> "Luister naar het voorwerp of de handeling."
        PromptKind.WHERE -> "Luister naar de plaatswoorden."
        PromptKind.WHY -> "Zoek de reden die in het verhaal wordt verteld."
        PromptKind.DETAIL -> "Luister nog eens naar het gevraagde detail."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.ONE_STEP_INSTRUCTION -> "Écoute l'objet, la couleur et la forme."
        PromptKind.TWO_STEP_FIRST -> "Retiens ce que tu dois faire d'abord."
        PromptKind.TWO_STEP_LAST -> "Retiens la deuxième étape."
        PromptKind.WHO -> "Écoute les prénoms et qui reçoit ou fait quelque chose."
        PromptKind.WHAT -> "Écoute l'objet ou l'action."
        PromptKind.WHERE -> "Écoute les mots qui indiquent le lieu."
        PromptKind.WHY -> "Cherche la raison racontée dans l'histoire."
        PromptKind.DETAIL -> "Réécoute le détail demandé."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.ONE_STEP_INSTRUCTION -> "Listen for the object, color, and shape."
        PromptKind.TWO_STEP_FIRST -> "Remember what you must do first."
        PromptKind.TWO_STEP_LAST -> "Remember the second step."
        PromptKind.WHO -> "Listen for the names and who receives or does something."
        PromptKind.WHAT -> "Listen for the object or action."
        PromptKind.WHERE -> "Listen for the place words."
        PromptKind.WHY -> "Find the reason stated in the story."
        PromptKind.DETAIL -> "Listen again for the requested detail."
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
        ONE_STEP_INSTRUCTION,
        TWO_STEP_FIRST,
        TWO_STEP_LAST,
        WHO,
        WHAT,
        WHERE,
        WHY,
        DETAIL
    }
}
