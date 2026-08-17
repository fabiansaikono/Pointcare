package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 2 integrated-world-knowledge generator.
 *
 * The automated slice assesses recognition and ordering in supplied scenarios.
 * Outings, observations, conversations, and real-world application remain in
 * the existing adult-verified practical activity.
 */
object Grade2IntegratedWorldFoundationsGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g2.world.integrated_foundations"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g2.world.integrated_foundations"
    private val allowedParameterKeys = setOf("grade", "roadmap_entry", "phase", "focus", "editorial_status")

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["grade"] != "2") add("grade must be 2")
        if (parameters["roadmap_entry"] != ROADMAP_ENTRY) add("roadmap_entry must be $ROADMAP_ENTRY")
        if (parameters["phase"] !in setOf("guided", "checkpoint")) {
            add("phase must be guided or checkpoint")
        }
        if (parameters["focus"] !in setOf(null, "mixed", "seasons")) {
            add("focus must be mixed or seasons")
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
        val promptKinds = if (parameters["focus"] == "seasons") {
            listOf(PromptKind.SEASONAL_CHANGE)
        } else {
            PromptKind.entries
        }
        val offset = if (phase == Phase.GUIDED) 0 else random.nextInt(promptKinds.size)
        return List(exerciseCount) { index ->
            val kind = promptKinds[(index + offset) % promptKinds.size]
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
        error("Unable to create a unique Grade 2 integrated-world exercise for ${kind.name}")
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
            id = "generated.g2.world.integrated_foundations.v1.${seed.fingerprint}.${index + 1}",
            domain = LearningDomain.WORLD_KNOWLEDGE,
            skill = "integrated_foundations",
            section = if (phase == Phase.GUIDED) ExerciseSection.PRACTICE else ExerciseSection.TESTS,
            level = SkillLevel.SECOND_GRADE,
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
                "automatedScope" to "integrated_world_scenario_choice",
                "requiresAdultOutingObservationConversation" to "true",
                "options_nl-BE" to candidate.options.joinToString("\n") { it.nl },
                "options_fr-BE" to candidate.options.joinToString("\n") { it.fr },
                "options_en-US" to candidate.options.joinToString("\n") { it.en },
                "optionSpeech_nl-BE" to candidate.options.joinToString("\n") { it.nl },
                "optionSpeech_fr-BE" to candidate.options.joinToString("\n") { it.fr },
                "optionSpeech_en-US" to candidate.options.joinToString("\n") { it.en },
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

    private fun spokenPrompt(visible: LocalizedText): LocalizedText = LocalizedText(
        nl = spoken(visible.nl),
        fr = visible.fr?.let(::spoken),
        en = visible.en?.let(::spoken)
    )

    private fun spoken(text: String): String = text.replace("?", "").replace(Regex("\\s+"), " ").trim()

    private fun bank(kind: PromptKind): List<Template> = when (kind) {
        PromptKind.SEASONAL_CHANGE -> listOf(
            template(
                "Aan veel bomen verschijnen nieuwe knoppen en jonge blaadjes. Welk seizoen begint?",
                "De nouveaux bourgeons et de jeunes feuilles apparaissent sur beaucoup d'arbres. Quelle saison commence ?",
                "New buds and young leaves appear on many trees. Which season is beginning?",
                "spring",
                choice("spring", "de lente", "le printemps", "spring"),
                choice("autumn", "de herfst", "l'automne", "autumn"),
                choice("winter", "de winter", "l'hiver", "winter")
            ),
            template(
                "Veel bladeren verkleuren en vallen van de bomen. Welk seizoen past hierbij?",
                "Beaucoup de feuilles changent de couleur et tombent des arbres. Quelle saison correspond ?",
                "Many leaves change color and fall from trees. Which season matches?",
                "autumn",
                choice("autumn", "de herfst", "l'automne", "autumn"),
                choice("spring", "de lente", "le printemps", "spring"),
                choice("summer", "de zomer", "l'été", "summer")
            ),
            template(
                "De dagen zijn lang en de zon staat vaak hoog. Welk seizoen past het best?",
                "Les journées sont longues et le soleil est souvent haut. Quelle saison convient le mieux ?",
                "The days are long and the sun is often high. Which season fits best?",
                "summer",
                choice("summer", "de zomer", "l'été", "summer"),
                choice("winter", "de winter", "l'hiver", "winter"),
                choice("spring", "de lente", "le printemps", "spring")
            ),
            template(
                "De dagen zijn kort en veel bomen zijn kaal. Welk seizoen past het best?",
                "Les journées sont courtes et beaucoup d'arbres sont nus. Quelle saison convient le mieux ?",
                "The days are short and many trees are bare. Which season fits best?",
                "winter",
                choice("winter", "de winter", "l'hiver", "winter"),
                choice("summer", "de zomer", "l'été", "summer"),
                choice("autumn", "de herfst", "l'automne", "autumn")
            )
        )

        PromptKind.NATURE_RELATION -> listOf(
            template(
                "Waarom bezoeken bijen bloemen?",
                "Pourquoi les abeilles visitent-elles les fleurs ?",
                "Why do bees visit flowers?",
                "nectar",
                choice("nectar", "om nectar en stuifmeel te verzamelen", "pour récolter du nectar et du pollen", "to collect nectar and pollen"),
                choice("plastic", "om plastic te maken", "pour fabriquer du plastique", "to make plastic"),
                choice("sleep", "om de hele winter in elke bloem te slapen", "pour dormir tout l'hiver dans chaque fleur", "to sleep all winter in every flower")
            ),
            template(
                "Welke plek past het best bij een regenworm?",
                "Quel endroit convient le mieux à un ver de terre ?",
                "Which place suits an earthworm best?",
                "moist_soil",
                choice("moist_soil", "vochtige aarde met plantenresten", "une terre humide avec des restes de plantes", "moist soil with plant remains"),
                choice("dry_shelf", "een droge boekenplank", "une étagère sèche", "a dry bookshelf"),
                choice("freezer", "een diepvriezer", "un congélateur", "a freezer")
            )
        )

        PromptKind.DAILY_SEQUENCE -> listOf(
            template(
                "Wat doe je best vóór je naar school vertrekt?",
                "Que vaut-il mieux faire avant de partir à l'école ?",
                "What should you do before leaving for school?",
                "prepare",
                choice("prepare", "controleren of je schooltas klaar is", "vérifier si ton cartable est prêt", "check that your schoolbag is ready"),
                choice("leave_bag", "je schooltas thuis leegmaken", "vider ton cartable à la maison", "empty your schoolbag at home"),
                choice("bed", "weer in bed gaan liggen", "retourner au lit", "go back to bed")
            ),
            template(
                "Je hebt je tanden gepoetst. Wat doe je daarna met je tandenborstel?",
                "Tu as brossé tes dents. Que fais-tu ensuite avec ta brosse à dents ?",
                "You have brushed your teeth. What do you do with your toothbrush next?",
                "rinse",
                choice("rinse", "afspoelen en netjes wegzetten", "la rincer et la ranger proprement", "rinse it and put it away neatly"),
                choice("floor", "op de vloer leggen", "la poser par terre", "put it on the floor"),
                choice("share", "ongewassen aan iemand anders geven", "la donner sans la laver à quelqu'un d'autre", "give it unwashed to someone else")
            )
        )

        PromptKind.TIME_ORDER -> listOf(
            template(
                "Welke foto is het oudst?",
                "Quelle photo est la plus ancienne ?",
                "Which photo is the oldest?",
                "grandparent_child",
                choice("grandparent_child", "een foto van je grootouder als kind", "une photo de ton grand-parent enfant", "a photo of your grandparent as a child"),
                choice("today", "een foto die vandaag is gemaakt", "une photo prise aujourd'hui", "a photo taken today"),
                choice("tomorrow", "een foto die morgen nog moet worden gemaakt", "une photo qui doit encore être prise demain", "a photo that will be taken tomorrow")
            ),
            template(
                "Vandaag is dinsdag. Welke dag komt daarna?",
                "Aujourd'hui, nous sommes mardi. Quel jour vient ensuite ?",
                "Today is Tuesday. Which day comes next?",
                "wednesday",
                choice("wednesday", "woensdag", "mercredi", "Wednesday"),
                choice("monday", "maandag", "lundi", "Monday"),
                choice("sunday", "zondag", "dimanche", "Sunday")
            )
        )

        PromptKind.MAP_POSITION -> listOf(
            template(
                "Op een eenvoudige kaart staat de boom links van het huis. Waar staat de boom?",
                "Sur une carte simple, l'arbre est à gauche de la maison. Où se trouve l'arbre ?",
                "On a simple map, the tree is left of the house. Where is the tree?",
                "left",
                choice("left", "links van het huis", "à gauche de la maison", "left of the house"),
                choice("right", "rechts van het huis", "à droite de la maison", "right of the house"),
                choice("inside", "in het huis", "dans la maison", "inside the house")
            ),
            template(
                "Je loopt van de school rechtdoor naar het park en slaat nergens af. Welke route volg je?",
                "Tu vas tout droit de l'école au parc sans tourner. Quel trajet suis-tu ?",
                "You walk straight from the school to the park without turning. Which route do you follow?",
                "straight",
                choice("straight", "de rechte route", "le trajet tout droit", "the straight route"),
                choice("turn", "een route met een afslag", "un trajet avec un virage", "a route with a turn"),
                choice("back", "eerst terug naar huis", "d'abord retourner à la maison", "go home first")
            )
        )

        PromptKind.COMMUNITY_PLACE -> listOf(
            template(
                "Waar kun je meestal een boek lenen?",
                "Où peut-on généralement emprunter un livre ?",
                "Where can you usually borrow a book?",
                "library",
                choice("library", "in de bibliotheek", "à la bibliothèque", "at the library"),
                choice("bakery", "in de bakkerij", "à la boulangerie", "at the bakery"),
                choice("pool", "in het zwembad", "à la piscine", "at the swimming pool")
            ),
            template(
                "Waar wordt brood gemaakt en verkocht?",
                "Où prépare-t-on et vend-on du pain ?",
                "Where is bread made and sold?",
                "bakery",
                choice("bakery", "in de bakkerij", "à la boulangerie", "at the bakery"),
                choice("library", "in de bibliotheek", "à la bibliothèque", "at the library"),
                choice("station", "op het perron", "sur le quai", "on a train platform")
            )
        )

        PromptKind.COMMUNITY_ROLE -> listOf(
            template(
                "Wie bezorgt brieven en pakjes bij huizen?",
                "Qui distribue les lettres et les colis aux maisons ?",
                "Who delivers letters and parcels to homes?",
                "postal_worker",
                choice("postal_worker", "een postbode", "un facteur ou une factrice", "a postal worker"),
                choice("baker", "een bakker", "un boulanger ou une boulangère", "a baker"),
                choice("librarian", "een bibliotheekmedewerker", "un bibliothécaire", "a librarian")
            ),
            template(
                "Wie helpt een brand veilig te bestrijden?",
                "Qui aide à combattre un incendie en sécurité ?",
                "Who helps fight a fire safely?",
                "firefighter",
                choice("firefighter", "de brandweer", "les pompiers", "firefighters"),
                choice("visitor", "een bezoeker zonder uitrusting", "un visiteur sans équipement", "a visitor without equipment"),
                choice("child", "een kind alleen", "un enfant seul", "a child alone")
            )
        )

        PromptKind.SHARED_SPACE_CHOICE -> listOf(
            template(
                "Je ziet afval op de speelplaats. Wat is een zorgzame keuze?",
                "Tu vois un déchet dans la cour de récréation. Quel est le choix responsable ?",
                "You see litter in the playground. What is a responsible choice?",
                "bin",
                choice("bin", "het veilig in de juiste vuilnisbak doen", "le mettre prudemment dans la bonne poubelle", "put it safely in the correct bin"),
                choice("leave", "het laten liggen zodat iemand erover struikelt", "le laisser pour que quelqu'un trébuche", "leave it where someone may trip"),
                choice("throw", "het over de omheining gooien", "le jeter par-dessus la clôture", "throw it over the fence")
            ),
            template(
                "Iedereen wacht op zijn beurt bij de kassa. Wat doe je?",
                "Tout le monde attend son tour à la caisse. Que fais-tu ?",
                "Everyone is waiting for their turn at the checkout. What do you do?",
                "wait_turn",
                choice("wait_turn", "aansluiten en je beurt afwachten", "faire la file et attendre ton tour", "join the line and wait your turn"),
                choice("push", "voordringen en duwen", "passer devant et pousser", "push ahead of everyone"),
                choice("block", "de doorgang blokkeren", "bloquer le passage", "block the way")
            )
        )
    }

    private fun template(
        nl: String,
        fr: String,
        en: String,
        answerKey: String,
        vararg options: Choice
    ): Template = Template(LocalizedText(nl, fr, en), options.toList(), answerKey)

    private fun choice(key: String, nl: String, fr: String, en: String): Choice =
        Choice(key, nl, fr, en)

    private fun hintNl(kind: PromptKind): String = when (kind) {
        PromptKind.SEASONAL_CHANGE -> "Let op veranderingen aan planten en bomen doorheen het jaar."
        PromptKind.NATURE_RELATION -> "Denk aan wat het dier nodig heeft en in de natuur doet."
        PromptKind.DAILY_SEQUENCE -> "Bedenk welke stap logisch en verzorgd volgt."
        PromptKind.TIME_ORDER -> "Zet gebeurtenissen of dagen in de juiste volgorde."
        PromptKind.MAP_POSITION -> "Lees de plaatswoorden en volg de beschreven route."
        PromptKind.COMMUNITY_PLACE -> "Denk aan waarvoor elke plek in de buurt dient."
        PromptKind.COMMUNITY_ROLE -> "Koppel de taak aan de persoon of dienst die ervoor is opgeleid."
        PromptKind.SHARED_SPACE_CHOICE -> "Kies wat veilig, netjes en respectvol is voor iedereen."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.SEASONAL_CHANGE -> "Observe les changements des plantes et des arbres au fil de l'année."
        PromptKind.NATURE_RELATION -> "Pense aux besoins de l'animal et à son rôle dans la nature."
        PromptKind.DAILY_SEQUENCE -> "Cherche l'étape logique et soignée qui suit."
        PromptKind.TIME_ORDER -> "Place les événements ou les jours dans le bon ordre."
        PromptKind.MAP_POSITION -> "Lis les mots de position et suis le trajet décrit."
        PromptKind.COMMUNITY_PLACE -> "Pense à l'utilité de chaque lieu du quartier."
        PromptKind.COMMUNITY_ROLE -> "Associe la tâche à la personne ou au service formé pour la réaliser."
        PromptKind.SHARED_SPACE_CHOICE -> "Choisis ce qui est sûr, propre et respectueux pour tous."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.SEASONAL_CHANGE -> "Notice how plants and trees change through the year."
        PromptKind.NATURE_RELATION -> "Think about what the animal needs and does in nature."
        PromptKind.DAILY_SEQUENCE -> "Choose the logical and tidy next step."
        PromptKind.TIME_ORDER -> "Put events or days in the correct order."
        PromptKind.MAP_POSITION -> "Read the position words and follow the described route."
        PromptKind.COMMUNITY_PLACE -> "Think about what each neighborhood place is used for."
        PromptKind.COMMUNITY_ROLE -> "Match the task to the person or service trained to do it."
        PromptKind.SHARED_SPACE_CHOICE -> "Choose what is safe, tidy, and respectful for everyone."
    }

    private data class Candidate(
        val kind: PromptKind,
        val templateIndex: Int,
        val prompt: LocalizedText,
        val options: List<Choice>,
        val answerKey: String
    ) {
        fun signature(): String = "${kind.name}:$templateIndex:${options.joinToString(":") { it.key }}"
    }

    private data class Template(val prompt: LocalizedText, val options: List<Choice>, val answerKey: String)
    private data class Choice(val key: String, val nl: String, val fr: String, val en: String)
    private enum class Phase { GUIDED, CHECKPOINT }
    private enum class PromptKind {
        SEASONAL_CHANGE,
        NATURE_RELATION,
        DAILY_SEQUENCE,
        TIME_ORDER,
        MAP_POSITION,
        COMMUNITY_PLACE,
        COMMUNITY_ROLE,
        SHARED_SPACE_CHOICE
    }
}
