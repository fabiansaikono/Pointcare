package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 1 pedestrian-safety generator.
 *
 * Screen exercises assess recognition of basic safe choices. Real routes,
 * traffic judgement and street crossing remain adult-supervised practical
 * activities and are never inferred from a tap-choice score.
 */
object Grade1PedestrianSafetyGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g1.world.traffic.pedestrian_safety"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g1.world.traffic.pedestrian_safety"
    private val allowedParameterKeys = setOf("grade", "roadmap_entry", "phase", "editorial_status")

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["grade"] != "1") add("grade must be 1")
        if (parameters["roadmap_entry"] != ROADMAP_ENTRY) add("roadmap_entry must be $ROADMAP_ENTRY")
        if (parameters["phase"] !in setOf("guided", "checkpoint")) add("phase must be guided or checkpoint")
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
        error("Unable to create a unique Grade 1 pedestrian-safety exercise for ${kind.name}")
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
            id = "generated.g1.world.traffic.pedestrian_safety.v1.${seed.fingerprint}.${index + 1}",
            domain = LearningDomain.WORLD_KNOWLEDGE,
            skill = "pedestrian_safety",
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
                "automatedScope" to "pedestrian_safety_scenario_choice",
                "requiresAdultRoutePractice" to "true",
                "adultObservationRubric" to "stops_at_edge;looks_both_ways;listens;crosses_at_safe_place;stays_visible",
                "masteryTag" to "traffic.${candidate.kind.name.lowercase()}",
                "errorTags" to "traffic.unsafe_or_incomplete_strategy",
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
                "hint_en-US" to hintEn(candidate.kind),
                "feedback_nl-BE" to "Kies de veilige stap en oefen de echte route samen met een volwassene.",
                "feedback_fr-BE" to "Choisis l'étape sûre et entraîne-toi sur le vrai trajet avec un adulte.",
                "feedback_en-US" to "Choose the safe step and practise the real route with an adult."
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
        PromptKind.WALKING_PLACE -> listOf(
            template(
                "Waar wandel je als er een vrij voetpad is?",
                "Où marches-tu lorsqu'il y a un trottoir dégagé ?",
                "Where do you walk when there is a clear sidewalk?",
                "sidewalk",
                choice("sidewalk", "Op het voetpad.", "Sur le trottoir.", "On the sidewalk."),
                choice("road", "Midden op de rijbaan.", "Au milieu de la chaussée.", "In the middle of the road."),
                choice("driveway", "Door elke oprit heen.", "À travers chaque entrée de garage.", "Through every driveway.")
            ),
            template(
                "Je wandelt naast een drukke straat. Welke plek is bedoeld voor voetgangers?",
                "Tu marches à côté d'une rue animée. Quel endroit est prévu pour les piétons ?",
                "You are walking beside a busy street. Which place is meant for pedestrians?",
                "footpath",
                choice("footpath", "Het voetpad naast de weg.", "Le trottoir à côté de la route.", "The sidewalk beside the road."),
                choice("lane", "De rijstrook tussen de auto's.", "La bande de circulation entre les voitures.", "The traffic lane between cars."),
                choice("parking", "Een vrije parkeerplaats op de rijbaan.", "Une place de stationnement libre sur la chaussée.", "An empty parking space on the road.")
            )
        )

        PromptKind.STOP_AT_EDGE -> listOf(
            template(
                "Je komt aan de rand van de straat. Wat doe je eerst?",
                "Tu arrives au bord de la rue. Que fais-tu d'abord ?",
                "You reach the edge of the street. What do you do first?",
                "stop",
                choice("stop", "Stoppen vóór je de rijbaan opgaat.", "T'arrêter avant d'entrer sur la chaussée.", "Stop before stepping into the road."),
                choice("run", "Zonder kijken doorlopen.", "Continuer sans regarder.", "Keep going without looking."),
                choice("play", "Op de rand beginnen spelen.", "Commencer à jouer au bord.", "Start playing at the edge.")
            ),
            template(
                "Een geparkeerde auto maakt het uitzicht moeilijk. Wat is veilig?",
                "Une voiture garée cache la vue. Qu'est-ce qui est prudent ?",
                "A parked car makes it hard to see. What is safe?",
                "wait_clear_view",
                choice("wait_clear_view", "Stoppen en met een volwassene een plek zoeken waar je goed kunt zien.", "T'arrêter et chercher avec un adulte un endroit où tu vois bien.", "Stop and find a place with an adult where you can see clearly."),
                choice("dash", "Tussen de auto's de straat oprennen.", "Courir sur la route entre les voitures.", "Run into the road between the cars."),
                choice("guess", "Gokken dat er niets aankomt.", "Deviner qu'il n'arrive rien.", "Guess that nothing is coming.")
            )
        )

        PromptKind.SAFE_CROSSING_PLACE -> listOf(
            template(
                "Waar steek je het best over als er dichtbij een zebrapad is?",
                "Où vaut-il mieux traverser lorsqu'il y a un passage pour piétons tout près ?",
                "Where is the best place to cross when there is a crosswalk nearby?",
                "crosswalk",
                choice("crosswalk", "Aan het zebrapad, nadat je goed hebt gecontroleerd.", "Au passage pour piétons, après avoir bien vérifié.", "At the crosswalk, after checking carefully."),
                choice("curve", "In een bocht waar bestuurders je slecht zien.", "Dans un virage où les conducteurs te voient mal.", "On a bend where drivers cannot see you well."),
                choice("between_cars", "Tussen geparkeerde auto's.", "Entre des voitures garées.", "Between parked cars.")
            ),
            template(
                "Welke plek is duidelijk aangeduid om over te steken?",
                "Quel endroit est clairement indiqué pour traverser ?",
                "Which place is clearly marked for crossing?",
                "marked_crossing",
                choice("marked_crossing", "Een aangeduid zebrapad.", "Un passage pour piétons signalé.", "A marked crosswalk."),
                choice("hidden", "Een verborgen plek achter een bestelwagen.", "Un endroit caché derrière une camionnette.", "A hidden place behind a van."),
                choice("driveway", "Een drukke uitrit.", "Une sortie très fréquentée.", "A busy driveway.")
            )
        )

        PromptKind.LOOK_AND_LISTEN -> listOf(
            template(
                "Je staat stil vóór het oversteken. Wat controleer je?",
                "Tu es arrêté avant de traverser. Que vérifies-tu ?",
                "You are stopped before crossing. What do you check?",
                "both_directions",
                choice("both_directions", "Links en rechts kijken en luisteren naar verkeer.", "Regarder à gauche et à droite et écouter la circulation.", "Look left and right and listen for traffic."),
                choice("shoes", "Alleen naar je schoenen kijken.", "Regarder seulement tes chaussures.", "Look only at your shoes."),
                choice("phone", "Naar een scherm kijken terwijl je doorloopt.", "Regarder un écran en avançant.", "Look at a screen while walking on.")
            ),
            template(
                "Je ziet geen auto, maar hoort een motor. Wat doe je?",
                "Tu ne vois pas de voiture, mais tu entends un moteur. Que fais-tu ?",
                "You do not see a car, but you hear an engine. What do you do?",
                "wait_check",
                choice("wait_check", "Blijven staan en opnieuw goed kijken en luisteren.", "Rester arrêté et regarder et écouter à nouveau attentivement.", "Stay stopped and look and listen carefully again."),
                choice("ignore", "Het geluid negeren en oversteken.", "Ignorer le bruit et traverser.", "Ignore the sound and cross."),
                choice("close_eyes", "Je ogen sluiten en snel lopen.", "Fermer les yeux et courir vite.", "Close your eyes and run quickly.")
            )
        )

        PromptKind.TRAFFIC_LIGHT -> listOf(
            template(
                "Het voetgangerslicht is rood. Wat betekent dat?",
                "Le feu pour piétons est rouge. Qu'est-ce que cela signifie ?",
                "The pedestrian signal is red. What does that mean?",
                "red_stop",
                choice("red_stop", "Stoppen en wachten.", "S'arrêter et attendre.", "Stop and wait."),
                choice("red_go", "Meteen oversteken.", "Traverser tout de suite.", "Cross right away."),
                choice("play", "Op de rijbaan spelen.", "Jouer sur la chaussée.", "Play in the road.")
            ),
            template(
                "Het voetgangerslicht wordt groen. Wat doe je vóór je vertrekt?",
                "Le feu pour piétons passe au vert. Que fais-tu avant de partir ?",
                "The pedestrian signal turns green. What do you do before you start?",
                "green_check",
                choice("green_check", "Nog controleren of verkeer echt stopt en dan rustig oversteken.", "Vérifier encore que la circulation s'arrête vraiment, puis traverser calmement.", "Still check that traffic has actually stopped, then cross calmly."),
                choice("blind_run", "Zonder kijken de straat oprennen.", "Courir sur la route sans regarder.", "Run into the street without looking."),
                choice("wait_road", "Midden op de rijbaan wachten.", "Attendre au milieu de la chaussée.", "Wait in the middle of the road.")
            )
        )

        PromptKind.DRIVEWAY_AWARENESS -> listOf(
            template(
                "Je loopt over een oprit. Waar let je op?",
                "Tu passes devant une entrée de garage. À quoi fais-tu attention ?",
                "You are walking across a driveway. What do you watch for?",
                "vehicles",
                choice("vehicles", "Voertuigen die de oprit in- of uitrijden.", "Les véhicules qui entrent ou sortent.", "Vehicles entering or leaving the driveway."),
                choice("clouds", "Alleen de wolken boven je.", "Seulement les nuages au-dessus de toi.", "Only the clouds above you."),
                choice("laces", "Alleen de kleur van je veters.", "Seulement la couleur de tes lacets.", "Only the color of your shoelaces.")
            ),
            template(
                "Een auto staat bij een uitrit en de bestuurder ziet je misschien niet. Wat is veilig?",
                "Une voiture est près d'une sortie et le conducteur ne te voit peut-être pas. Qu'est-ce qui est prudent ?",
                "A car is by a driveway and the driver may not see you. What is safe?",
                "wait_seen",
                choice("wait_seen", "Stoppen op een veilige plek en wachten tot je zeker veilig verder kunt.", "T'arrêter dans un endroit sûr et attendre d'être certain de pouvoir continuer.", "Stop in a safe place and wait until you are sure you can continue safely."),
                choice("behind", "Vlak achter de auto doorlopen.", "Passer juste derrière la voiture.", "Walk right behind the car."),
                choice("race", "Voor de auto langs rennen.", "Courir devant la voiture.", "Run in front of the car.")
            )
        )

        PromptKind.BE_VISIBLE -> listOf(
            template(
                "Het is donker buiten. Wat helpt bestuurders je beter te zien?",
                "Il fait sombre dehors. Qu'est-ce qui aide les conducteurs à mieux te voir ?",
                "It is dark outside. What helps drivers see you better?",
                "reflective",
                choice("reflective", "Lichte kleding of reflecterend materiaal.", "Des vêtements clairs ou du matériel réfléchissant.", "Light-colored clothing or reflective material."),
                choice("dark", "Alleen donkere kleding zonder reflectie.", "Uniquement des vêtements sombres sans éléments réfléchissants.", "Only dark clothing without reflection."),
                choice("hide", "Je verstoppen tussen geparkeerde auto's.", "Te cacher entre des voitures garées.", "Hide between parked cars.")
            ),
            template(
                "In regen en schemering ben je minder goed zichtbaar. Wat is verstandig?",
                "Sous la pluie et au crépuscule, tu es moins visible. Qu'est-ce qui est raisonnable ?",
                "In rain and dusk, you are less visible. What is sensible?",
                "visible_adult",
                choice("visible_adult", "Goed zichtbaar blijven en de veilige route met een volwassene nemen.", "Rester bien visible et suivre l'itinéraire sûr avec un adulte.", "Stay clearly visible and take the safe route with an adult."),
                choice("shortcut", "Een donkere, onoverzichtelijke afkorting nemen.", "Prendre un raccourci sombre sans bonne visibilité.", "Take a dark shortcut with poor visibility."),
                choice("road_play", "Op de rijbaan spelen.", "Jouer sur la chaussée.", "Play in the road.")
            )
        )

        PromptKind.UNEXPECTED_DANGER -> listOf(
            template(
                "Je bal rolt de straat op. Wat doe je?",
                "Ton ballon roule sur la route. Que fais-tu ?",
                "Your ball rolls into the street. What do you do?",
                "ask_adult",
                choice("ask_adult", "Stoppen en een volwassene om hulp vragen.", "T'arrêter et demander l'aide d'un adulte.", "Stop and ask an adult for help."),
                choice("chase", "Meteen achter de bal aan rennen.", "Courir tout de suite après le ballon.", "Run after the ball right away."),
                choice("crawl", "Onder een rijdende auto kruipen.", "Ramper sous une voiture en mouvement.", "Crawl under a moving car.")
            ),
            template(
                "De gewone route is geblokkeerd en je weet niet waar je veilig kunt lopen. Wat doe je?",
                "Le chemin habituel est bloqué et tu ne sais pas où marcher en sécurité. Que fais-tu ?",
                "The usual route is blocked and you do not know where it is safe to walk. What do you do?",
                "trusted_adult",
                choice("trusted_adult", "Stoppen op een veilige plek en hulp vragen aan een vertrouwde volwassene.", "T'arrêter dans un endroit sûr et demander l'aide d'un adulte de confiance.", "Stop in a safe place and ask a trusted adult for help."),
                choice("road", "Zelf een weg zoeken midden op de rijbaan.", "Chercher seul un chemin au milieu de la chaussée.", "Find your own way in the middle of the road."),
                choice("barrier", "Over een afsluiting klimmen.", "Escalader une barrière.", "Climb over a barrier.")
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

    private fun choice(key: String, nl: String, fr: String, en: String): Choice = Choice(key, nl, fr, en)

    private fun hintNl(kind: PromptKind): String = when (kind) {
        PromptKind.WALKING_PLACE -> "Gebruik het voetpad wanneer dat beschikbaar en begaanbaar is."
        PromptKind.STOP_AT_EDGE -> "Stop vóór de rijbaan en zorg dat je het verkeer goed kunt zien."
        PromptKind.SAFE_CROSSING_PLACE -> "Kies een zichtbare, aangeduide oversteekplaats en controleer altijd het verkeer."
        PromptKind.LOOK_AND_LISTEN -> "Kijk links en rechts, luister en steek alleen over wanneer het veilig is."
        PromptKind.TRAFFIC_LIGHT -> "Rood betekent wachten; ook bij groen controleer je eerst of het verkeer stopt."
        PromptKind.DRIVEWAY_AWARENESS -> "Bij een oprit kunnen voertuigen het voetpad kruisen."
        PromptKind.BE_VISIBLE -> "Lichte en reflecterende materialen helpen anderen je te zien."
        PromptKind.UNEXPECTED_DANGER -> "Ga niet onverwacht de rijbaan op; stop en vraag een volwassene om hulp."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.WALKING_PLACE -> "Utilise le trottoir lorsqu'il est disponible et praticable."
        PromptKind.STOP_AT_EDGE -> "Arrête-toi avant la chaussée et assure-toi de bien voir la circulation."
        PromptKind.SAFE_CROSSING_PLACE -> "Choisis un passage visible et signalé, et vérifie toujours la circulation."
        PromptKind.LOOK_AND_LISTEN -> "Regarde à gauche et à droite, écoute et traverse seulement quand c'est sûr."
        PromptKind.TRAFFIC_LIGHT -> "Le rouge signifie attendre ; même au vert, vérifie d'abord que la circulation s'arrête."
        PromptKind.DRIVEWAY_AWARENESS -> "À une entrée de garage, des véhicules peuvent traverser le trottoir."
        PromptKind.BE_VISIBLE -> "Les éléments clairs et réfléchissants aident les autres à te voir."
        PromptKind.UNEXPECTED_DANGER -> "Ne va pas soudainement sur la chaussée ; arrête-toi et demande l'aide d'un adulte."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.WALKING_PLACE -> "Use the sidewalk when it is available and clear."
        PromptKind.STOP_AT_EDGE -> "Stop before the road and make sure you can see traffic clearly."
        PromptKind.SAFE_CROSSING_PLACE -> "Choose a visible, marked crossing and always check traffic."
        PromptKind.LOOK_AND_LISTEN -> "Look left and right, listen, and cross only when it is safe."
        PromptKind.TRAFFIC_LIGHT -> "Red means wait; even on green, first check that traffic is stopping."
        PromptKind.DRIVEWAY_AWARENESS -> "At a driveway, vehicles may cross the sidewalk."
        PromptKind.BE_VISIBLE -> "Light-colored and reflective materials help others see you."
        PromptKind.UNEXPECTED_DANGER -> "Do not step suddenly into the road; stop and ask an adult for help."
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
        WALKING_PLACE,
        STOP_AT_EDGE,
        SAFE_CROSSING_PLACE,
        LOOK_AND_LISTEN,
        TRAFFIC_LIGHT,
        DRIVEWAY_AWARENESS,
        BE_VISIBLE,
        UNEXPECTED_DANGER
    }
}
