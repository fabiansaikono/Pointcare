package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

internal data class Grade5Choice(val key: String, val nl: String, val fr: String, val en: String)

internal data class Grade5Scenario(
    val kind: String,
    val visiblePrompt: LocalizedText,
    val speechPrompt: LocalizedText,
    val answerKey: String,
    val options: List<Grade5Choice>
)

private fun g5Text(nl: String, fr: String, en: String) = LocalizedText(nl = nl, fr = fr, en = en)
private fun g5Option(key: String, nl: String, fr: String, en: String) = Grade5Choice(key, nl, fr, en)
private fun g5SameOption(key: String, value: String) = Grade5Choice(key, value, value, value)
private fun g5Scenario(
    kind: String,
    nl: String,
    fr: String,
    en: String,
    answerKey: String,
    answer: Grade5Choice,
    distractor1: Grade5Choice,
    distractor2: Grade5Choice
) = Grade5Scenario(kind, g5Text(nl, fr, en), g5Text(nl, fr, en), answerKey, listOf(answer, distractor1, distractor2))

private fun g5ListeningScenario(
    kind: String,
    speechNl: String,
    speechFr: String,
    speechEn: String,
    answerKey: String,
    answer: Grade5Choice,
    distractor1: Grade5Choice,
    distractor2: Grade5Choice
) = Grade5Scenario(
    kind = kind,
    visiblePrompt = g5Text(
        "Luister aandachtig en kies het beste antwoord.",
        "Écoute attentivement et choisis la meilleure réponse.",
        "Listen carefully and choose the best answer."
    ),
    speechPrompt = g5Text(speechNl, speechFr, speechEn),
    answerKey = answerKey,
    options = listOf(answer, distractor1, distractor2)
)

private fun g5FrenchScenario(
    kind: String,
    visibleNl: String,
    visibleFr: String,
    visibleEn: String,
    frenchSpeech: String,
    answerKey: String,
    answer: Grade5Choice,
    distractor1: Grade5Choice,
    distractor2: Grade5Choice
) = Grade5Scenario(
    kind = kind,
    visiblePrompt = g5Text(visibleNl, visibleFr, visibleEn),
    speechPrompt = g5Text(frenchSpeech, frenchSpeech, frenchSpeech),
    answerKey = answerKey,
    options = listOf(answer, distractor1, distractor2)
)

internal abstract class Grade5ChoiceGenerator(
    final override val generatorId: String,
    private val roadmapEntry: String,
    private val idNamespace: String,
    private val skill: String,
    private val domain: LearningDomain,
    private val automatedScope: String,
    private val adultSupportKey: String,
    private val hint: LocalizedText,
    private val inputMode: InputMode = InputMode.TAP_CHOICE,
    private val extraData: Map<String, String> = emptyMap(),
    private val scenarios: List<Grade5Scenario>
) : LessonExerciseGenerator {
    final override val generatorVersion: Int = 1
    private val allowedParameterKeys = setOf("grade", "roadmap_entry", "phase", "editorial_status")

    init {
        require(scenarios.size >= 10)
        require(scenarios.map { it.kind }.distinct().size == scenarios.size)
        scenarios.forEach { scenario ->
            require(scenario.options.size == 3)
            require(scenario.options.map { it.key }.distinct().size == 3)
            require(scenario.options.count { it.key == scenario.answerKey } == 1)
        }
    }

    final override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["grade"] != "5") add("grade must be 5")
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
        require(exerciseCount > 0)
        val phase = parameters.getValue("phase")
        val random = StableLessonRandom(seed.value)
        val offset = random.nextInt(scenarios.size)
        return List(exerciseCount) { index ->
            val scenario = scenarios[(index + offset) % scenarios.size]
            val options = shuffled(scenario.options, random)
            val answer = options.single { it.key == scenario.answerKey }
            ExerciseContent(
                id = "generated.$idNamespace.v1.${seed.fingerprint}.${index + 1}",
                domain = domain,
                skill = skill,
                section = if (phase == "guided") ExerciseSection.PRACTICE else ExerciseSection.TESTS,
                level = SkillLevel.FIFTH_GRADE,
                inputMode = inputMode,
                prompt = scenario.visiblePrompt,
                speechText = scenario.speechPrompt,
                data = linkedMapOf(
                    "generatorId" to generatorId,
                    "generatorVersion" to generatorVersion.toString(),
                    "seedFingerprint" to seed.fingerprint,
                    "roadmapEntry" to roadmapEntry,
                    "phase" to phase,
                    "promptKind" to scenario.kind,
                    "answerRole" to "choice",
                    "automatedScope" to automatedScope,
                    adultSupportKey to "true",
                    "options_nl-BE" to options.joinToString("\n") { it.nl },
                    "options_fr-BE" to options.joinToString("\n") { it.fr },
                    "options_en-US" to options.joinToString("\n") { it.en },
                    "optionSpeech_nl-BE" to options.joinToString("\n") { it.nl },
                    "optionSpeech_fr-BE" to options.joinToString("\n") { it.fr },
                    "optionSpeech_en-US" to options.joinToString("\n") { it.en },
                    "answer_nl-BE" to answer.nl,
                    "answer_fr-BE" to answer.fr,
                    "answer_en-US" to answer.en,
                    "hint_nl-BE" to hint.nl,
                    "hint_fr-BE" to hint.fr.orEmpty(),
                    "hint_en-US" to hint.en.orEmpty()
                ).apply { putAll(extraData) },
                answer = scenario.answerKey
            )
        }
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
}

internal object Grade5ReadingListeningStrategiesGenerator : Grade5ChoiceGenerator(
    generatorId = "generator.g5.language.reading_listening_strategies",
    roadmapEntry = "roadmap.g5.language.reading_listening_strategies",
    idNamespace = "g5.language.reading_listening_strategies",
    skill = "reading_listening_strategies",
    domain = LearningDomain.READING,
    automatedScope = "summary_inference_structure_and_source_reasoning",
    adultSupportKey = "requiresAdultDiscussion",
    hint = g5Text("Zoek bewijs en onderscheid hoofdzaak van detail.", "Cherche des preuves et distingue l'essentiel des détails.", "Find evidence and separate the main idea from details."),
    extraData = mapOf("independentOralSummaryMastered" to "false", "extendedDiscussionMastered" to "false"),
    scenarios = listOf(
        g5Scenario("summary", "Een school vangt regenwater op voor de tuin en gebruikt daardoor minder leidingwater. Wat is de beste samenvatting?", "Une école récupère l'eau de pluie pour le jardin et utilise ainsi moins d'eau du robinet. Quel est le meilleur résumé ?", "A school collects rainwater for its garden and therefore uses less tap water. What is the best summary?", "reuse", g5Option("reuse", "Regenwater hergebruiken bespaart leidingwater.", "Réutiliser l'eau de pluie économise l'eau du robinet.", "Reusing rainwater saves tap water."), g5Option("garden", "Elke schooltuin heeft dezelfde planten.", "Tous les jardins scolaires ont les mêmes plantes.", "Every school garden has the same plants."), g5Option("pipes", "Leidingwater komt altijd uit regenputten.", "L'eau du robinet vient toujours de citernes.", "Tap water always comes from rain tanks.")),
        g5Scenario("inference", "De trein stopt onverwacht. Op het perron wijst een medewerker naar een omleiding. Wat kun je afleiden?", "Le train s'arrête de façon imprévue. Sur le quai, un agent indique une déviation. Que peux-tu déduire ?", "The train stops unexpectedly. On the platform, a worker points to a detour. What can you infer?", "route", g5Option("route", "De gewone reisroute is tijdelijk gewijzigd.", "L'itinéraire habituel est temporairement modifié.", "The usual route has temporarily changed."), g5Option("holiday", "Iedereen krijgt vakantie.", "Tout le monde est en vacances.", "Everyone is on holiday."), g5Option("empty", "Er rijden nooit meer treinen.", "Il n'y aura plus jamais de trains.", "Trains will never run again.")),
        g5Scenario("text_structure", "Een tekst legt eerst een probleem uit en geeft daarna drie mogelijke oplossingen. Welke structuur gebruikt hij?", "Un texte présente d'abord un problème puis trois solutions possibles. Quelle structure utilise-t-il ?", "A text first explains a problem and then gives three possible solutions. Which structure does it use?", "problem_solution", g5Option("problem_solution", "probleem en oplossing", "problème et solutions", "problem and solution"), g5Option("chronology", "alleen tijdsvolgorde", "uniquement la chronologie", "time order only"), g5Option("description", "een losse beschrijving", "une description isolée", "an unrelated description")),
        g5Scenario("evidence", "Een auteur beweert dat een nieuwe fietsroute veiliger is. Welk bewijs ondersteunt dat het best?", "Un auteur affirme qu'une nouvelle piste cyclable est plus sûre. Quelle preuve l'appuie le mieux ?", "An author claims a new cycle route is safer. Which evidence supports that best?", "data", g5Option("data", "Na de aanleg daalde het aantal ongevallen.", "Après sa construction, le nombre d'accidents a diminué.", "After it was built, the number of accidents fell."), g5Option("color", "De route heeft blauwe borden.", "La piste a des panneaux bleus.", "The route has blue signs."), g5Option("length", "De route is precies drie kilometer lang.", "La piste mesure exactement trois kilomètres.", "The route is exactly three kilometers long.")),
        g5Scenario("diagram", "Een schema toont pijlen van zon naar plant en van plant naar konijn. Wat helpt het schema vooral begrijpen?", "Un schéma montre des flèches du soleil vers la plante, puis de la plante vers le lapin. Que permet-il surtout de comprendre ?", "A diagram shows arrows from the sun to a plant and from the plant to a rabbit. What does it mainly help explain?", "energy", g5Option("energy", "hoe energie wordt doorgegeven", "comment l'énergie est transmise", "how energy is transferred"), g5Option("sizes", "de exacte grootte van elk dier", "la taille exacte de chaque animal", "the exact size of each animal"), g5Option("dates", "op welke datum alles gebeurde", "la date de chaque événement", "the date everything happened")),
        g5Scenario("source_purpose", "Een folder van een sportclub benadrukt alleen voordelen van lid worden. Wat is waarschijnlijk het doel?", "Une brochure d'un club sportif souligne uniquement les avantages de l'adhésion. Quel est probablement son but ?", "A sports club leaflet highlights only the benefits of joining. What is its likely purpose?", "persuade", g5Option("persuade", "mensen overtuigen om lid te worden", "convaincre les gens de s'inscrire", "persuade people to join"), g5Option("neutral", "alle nadelen neutraal onderzoeken", "étudier tous les inconvénients de façon neutre", "neutrally investigate every disadvantage"), g5Option("fiction", "een fantasieverhaal vertellen", "raconter une histoire imaginaire", "tell a fantasy story")),
        g5Scenario("fact_opinion", "Welke zin is een controleerbaar feit?", "Quelle phrase est un fait vérifiable ?", "Which sentence is a verifiable fact?", "measured", g5Option("measured", "De brug is 120 meter lang.", "Le pont mesure 120 mètres.", "The bridge is 120 meters long."), g5Option("beautiful", "De brug is prachtig.", "Le pont est magnifique.", "The bridge is beautiful."), g5Option("best", "Dit is de beste brug.", "C'est le meilleur pont.", "This is the best bridge.")),
        g5Scenario("conclusion", "Alle onderzochte zaden kregen evenveel water. Alleen de zaden bij het raam kiemden snel. Welke voorzichtige conclusie past?", "Toutes les graines étudiées ont reçu autant d'eau. Seules celles près de la fenêtre ont germé vite. Quelle conclusion prudente convient ?", "All tested seeds received equal water. Only those by the window sprouted quickly. Which cautious conclusion fits?", "light_helped", g5Option("light_helped", "In deze proef kan meer licht de kieming hebben geholpen.", "Dans cette expérience, davantage de lumière a pu favoriser la germination.", "In this test, more light may have helped germination."), g5Option("always", "Licht is altijd de enige factor.", "La lumière est toujours le seul facteur.", "Light is always the only factor."), g5Option("water", "Water heeft nooit invloed op planten.", "L'eau n'influence jamais les plantes.", "Water never affects plants.")),
        g5Scenario("compare_sources", "Twee bronnen noemen verschillende aantallen bezoekers. Wat doe je eerst?", "Deux sources donnent des nombres de visiteurs différents. Que fais-tu d'abord ?", "Two sources give different visitor counts. What should you do first?", "check", g5Option("check", "datum, meetmethode en afzender vergelijken", "comparer la date, la méthode et l'auteur", "compare the date, method, and author"), g5Option("choose_high", "altijd het hoogste getal kiezen", "toujours choisir le nombre le plus élevé", "always choose the highest number"), g5Option("ignore", "beide bronnen negeren", "ignorer les deux sources", "ignore both sources")),
        g5Scenario("note_taking", "Je moet een uitleg samenvatten. Welke notitie is het bruikbaarst?", "Tu dois résumer une explication. Quelle note est la plus utile ?", "You need to summarize an explanation. Which note is most useful?", "keywords", g5Option("keywords", "kernwoorden met pijlen tussen oorzaken en gevolgen", "des mots-clés avec des flèches entre causes et effets", "keywords with arrows between causes and effects"), g5Option("copy", "elk woord zonder selectie overschrijven", "recopier chaque mot sans sélectionner", "copy every word without selecting"), g5Option("decoration", "alleen de titel versieren", "décorer uniquement le titre", "decorate only the title"))
    )
)

internal object Grade5ListeningEvaluateInformationGenerator : Grade5ChoiceGenerator(
    generatorId = "generator.g5.language.listening.evaluate_information",
    roadmapEntry = "roadmap.g5.language.listening.evaluate_information",
    idNamespace = "g5.language.listening.evaluate_information",
    skill = "listening_evaluate_information",
    domain = LearningDomain.READING,
    automatedScope = "extended_listening_selection_and_evaluation",
    adultSupportKey = "requiresAdultListeningDiscussion",
    inputMode = InputMode.LISTEN_AND_SELECT,
    hint = g5Text("Luister naar kernwoorden, volgorde en bedoeling.", "Écoute les mots-clés, l'ordre et l'intention.", "Listen for keywords, order, and purpose."),
    extraData = mapOf("audioRequired" to "true", "oralResponseMastered" to "false", "realWorldInstructionMastered" to "false"),
    scenarios = listOf(
        g5ListeningScenario("main_message", "Luister: De bibliotheek blijft woensdag langer open zodat leerlingen na school rustig kunnen studeren. Wat is de kernboodschap?", "Écoute : La bibliothèque reste ouverte plus tard mercredi afin que les élèves puissent étudier après l'école. Quel est le message principal ?", "Listen: The library stays open later on Wednesday so students can study after school. What is the main message?", "later", g5Option("later", "Woensdag is de bibliotheek langer open.", "La bibliothèque ferme plus tard mercredi.", "The library stays open later on Wednesday."), g5Option("closed", "De bibliotheek sluit woensdag.", "La bibliothèque ferme mercredi.", "The library closes on Wednesday."), g5Option("books", "Alle boeken worden vervangen.", "Tous les livres sont remplacés.", "All books are replaced.")),
        g5ListeningScenario("detail", "Luister: Neem voor de uitstap een lunch, een drinkfles en een regenjas mee. Welk kledingstuk moet mee?", "Écoute : Pour l'excursion, prends un repas, une gourde et un imperméable. Quel vêtement faut-il emporter ?", "Listen: Bring lunch, a water bottle, and a raincoat for the trip. Which item of clothing is needed?", "coat", g5Option("coat", "een regenjas", "un imperméable", "a raincoat"), g5Option("hat", "een feesthoed", "un chapeau de fête", "a party hat"), g5Option("scarf", "een wollen sjaal", "une écharpe en laine", "a wool scarf")),
        g5ListeningScenario("sequence", "Luister: Controleer eerst de titel, lees daarna de eerste alinea en noteer ten slotte drie kernwoorden. Wat doe je als tweede?", "Écoute : Vérifie d'abord le titre, lis ensuite le premier paragraphe et note enfin trois mots-clés. Que fais-tu en deuxième ?", "Listen: First check the title, then read the first paragraph, and finally note three keywords. What do you do second?", "paragraph", g5Option("paragraph", "de eerste alinea lezen", "lire le premier paragraphe", "read the first paragraph"), g5Option("title", "de titel controleren", "vérifier le titre", "check the title"), g5Option("keywords", "drie kernwoorden noteren", "noter trois mots-clés", "note three keywords")),
        g5ListeningScenario("condition", "Luister: Werk buiten als het droog blijft; ga bij regen naar het lokaal naast de refter. Waar ga je bij regen?", "Écoute : Travaille dehors s'il reste sec ; s'il pleut, va dans la salle à côté du réfectoire. Où vas-tu s'il pleut ?", "Listen: Work outside if it stays dry; if it rains, go to the room beside the cafeteria. Where do you go if it rains?", "room", g5Option("room", "het lokaal naast de refter", "la salle à côté du réfectoire", "the room beside the cafeteria"), g5Option("yard", "de speelplaats", "la cour", "the playground"), g5Option("home", "naar huis", "à la maison", "home")),
        g5ListeningScenario("relevant_information", "Luister: De wedstrijd start om tien uur. De poort opent om half tien en deelnemers melden zich vijftien minuten vooraf. Wanneer meld je je aan?", "Écoute : Le concours commence à dix heures. La porte ouvre à neuf heures et demie et les participants se présentent quinze minutes avant. Quand te présentes-tu ?", "Listen: The contest starts at ten. The gate opens at nine thirty and participants report fifteen minutes early. When should you report?", "quarter", g5Option("quarter", "om kwart voor tien", "à dix heures moins le quart", "at quarter to ten"), g5Option("ten", "om tien uur", "à dix heures", "at ten"), g5Option("nine", "om negen uur", "à neuf heures", "at nine")),
        g5ListeningScenario("fact_opinion", "Luister: De toren is 64 meter hoog en volgens mij het mooiste gebouw van de stad. Welk deel is een mening?", "Écoute : La tour mesure 64 mètres et, selon moi, c'est le plus beau bâtiment de la ville. Quelle partie est une opinion ?", "Listen: The tower is 64 meters tall and, in my view, the most beautiful building in the city. Which part is an opinion?", "beautiful", g5Option("beautiful", "het mooiste gebouw", "le plus beau bâtiment", "the most beautiful building"), g5Option("height", "64 meter hoog", "64 mètres de haut", "64 meters tall"), g5Option("tower", "het gaat over een toren", "il s'agit d'une tour", "it is about a tower")),
        g5ListeningScenario("speaker_purpose", "Luister: Vergeet niet vandaag je formulier in te leveren; zonder formulier kun je niet deelnemen. Waarom zegt de spreker dit?", "Écoute : N'oublie pas de remettre ton formulaire aujourd'hui ; sans lui, tu ne peux pas participer. Pourquoi la personne dit-elle cela ?", "Listen: Do not forget to hand in your form today; without it you cannot participate. Why does the speaker say this?", "remind", g5Option("remind", "om te herinneren aan een noodzakelijke taak", "pour rappeler une tâche nécessaire", "to remind listeners of a required task"), g5Option("entertain", "om een grap te vertellen", "pour raconter une blague", "to tell a joke"), g5Option("describe", "om een landschap te beschrijven", "pour décrire un paysage", "to describe a landscape")),
        g5ListeningScenario("reason_quality", "Luister: We kiezen herbruikbare bekers omdat er dan minder afval ontstaat. Hoe sterk is deze reden?", "Écoute : Nous choisissons des gobelets réutilisables parce qu'ils produisent moins de déchets. Cette raison est-elle solide ?", "Listen: We choose reusable cups because they create less waste. How strong is this reason?", "relevant", g5Option("relevant", "relevant: ze legt het voordeel uit", "pertinente : elle explique l'avantage", "relevant: it explains the benefit"), g5Option("unrelated", "niet relevant voor afval", "sans rapport avec les déchets", "unrelated to waste"), g5Option("none", "er wordt geen reden gegeven", "aucune raison n'est donnée", "no reason is given")),
        g5ListeningScenario("note_selection", "Luister: De rivier trad buiten haar oevers na drie dagen hevige regen, waardoor twee wegen sloten. Welke notitie bevat oorzaak én gevolg?", "Écoute : Après trois jours de forte pluie, la rivière a débordé et deux routes ont été fermées. Quelle note contient la cause et la conséquence ?", "Listen: After three days of heavy rain, the river overflowed and two roads closed. Which note contains both cause and effect?", "cause_effect", g5Option("cause_effect", "hevige regen → rivier overstroomt → wegen dicht", "fortes pluies → rivière déborde → routes fermées", "heavy rain → river floods → roads close"), g5Option("roads", "er zijn wegen", "il y a des routes", "there are roads"), g5Option("days", "het duurde drie dagen", "cela a duré trois jours", "it lasted three days")),
        g5ListeningScenario("conclusion", "Luister: Drie groepen gebruikten dezelfde route. De groep met een kaart kwam telkens sneller aan. Welke conclusie past?", "Écoute : Trois groupes ont suivi le même trajet. Le groupe avec une carte est arrivé plus vite à chaque fois. Quelle conclusion convient ?", "Listen: Three groups used the same route. The group with a map arrived faster each time. Which conclusion fits?", "map_helped", g5Option("map_helped", "In deze proef hielp de kaart de route sneller te volgen.", "Dans cet essai, la carte a aidé à suivre l'itinéraire plus vite.", "In this test, the map helped the group follow the route faster."), g5Option("always", "Een kaart maakt elke reis altijd korter.", "Une carte raccourcit toujours chaque trajet.", "A map always makes every trip shorter."), g5Option("lost", "Alle groepen waren verdwaald.", "Tous les groupes étaient perdus.", "Every group was lost."))
    )
)

internal object Grade5WritingStructureRevisionGenerator : Grade5ChoiceGenerator(
    generatorId = "generator.g5.language.writing.structure_revision",
    roadmapEntry = "roadmap.g5.language.writing.structure_revision",
    idNamespace = "g5.language.writing.structure_revision",
    skill = "writing_structure_revision",
    domain = LearningDomain.WRITING,
    automatedScope = "planning_structure_cohesion_and_revision_choices",
    adultSupportKey = "requiresAdultWritingReview",
    hint = g5Text("Kies de formulering die doel, publiek en samenhang het best ondersteunt.", "Choisis la formulation qui soutient le mieux le but, le public et la cohérence.", "Choose the wording that best supports purpose, audience, and coherence."),
    extraData = mapOf("fullTextWritingMastered" to "false", "handwritingMastered" to "false"),
    scenarios = listOf(
        g5Scenario("introduction", "Welke openingszin past bij een verslag over een proef met verdamping?", "Quelle phrase d'introduction convient à un rapport sur une expérience d'évaporation ?", "Which opening sentence fits a report about an evaporation experiment?", "purpose", g5Option("purpose", "We onderzochten welke plek water het snelst laat verdampen.", "Nous avons étudié l'endroit où l'eau s'évapore le plus vite.", "We investigated where water evaporates fastest."), g5Option("hello", "Hallo allemaal, hoe gaat het?", "Bonjour tout le monde, comment ça va ?", "Hello everyone, how are you?"), g5Option("fiction", "Lang geleden sprak een magische druppel.", "Il était une fois une goutte magique.", "Long ago, a magical drop spoke.")),
        g5Scenario("paragraph_order", "Welke volgorde maakt een instructietekst duidelijk?", "Quel ordre rend un texte d'instructions clair ?", "Which order makes an instruction text clear?", "logical", g5Option("logical", "benodigdheden, stappen, controle", "matériel, étapes, vérification", "materials, steps, check"), g5Option("reverse", "controle, slot, benodigdheden", "vérification, fin, matériel", "check, ending, materials"), g5Option("random", "losse stappen zonder volgorde", "étapes sans ordre", "unordered steps")),
        g5Scenario("transition", "Welke verbindingszin past? 'De batterij was opgeladen. … werkte de zaklamp niet.'", "Quel connecteur convient ? « La pile était chargée. … la lampe ne fonctionnait pas. »", "Which transition fits? 'The battery was charged. … the flashlight did not work.'", "however", g5Option("however", "Toch", "Pourtant", "However"), g5Option("because", "Omdat", "Parce que", "Because"), g5Option("first", "Eerst", "D'abord", "First")),
        g5Scenario("remove_repetition", "Welke herziening vermijdt storende herhaling? 'De hond rende. De hond sprong. De hond blafte.'", "Quelle révision évite une répétition gênante ? « Le chien courait. Le chien sautait. Le chien aboyait. »", "Which revision avoids awkward repetition? 'The dog ran. The dog jumped. The dog barked.'", "pronoun", g5Option("pronoun", "De hond rende, sprong en blafte.", "Le chien courait, sautait et aboyait.", "The dog ran, jumped, and barked."), g5Option("repeat", "De hond rende en de hond sprong en de hond blafte.", "Le chien courait et le chien sautait et le chien aboyait.", "The dog ran and the dog jumped and the dog barked."), g5Option("missing", "Rende, sprong, blafte.", "Courait, sautait, aboyait.", "Ran, jumped, barked.")),
        g5Scenario("precise_word", "Welk werkwoord maakt de zin preciezer? 'De onderzoeker … de temperatuur.'", "Quel verbe rend la phrase plus précise ? « Le chercheur … la température. »", "Which verb makes the sentence more precise? 'The researcher … the temperature.'", "measured", g5Option("measured", "mat", "a mesuré", "measured"), g5Option("did", "deed", "a fait", "did"), g5Option("nice", "maakte mooi", "a embelli", "made nice")),
        g5Scenario("conclusion", "Welke slotzin past bij een betogende tekst over minder afval?", "Quelle conclusion convient à un texte argumentatif sur la réduction des déchets ?", "Which conclusion fits a persuasive text about reducing waste?", "call", g5Option("call", "Daarom kiezen we best voor herbruikbare verpakkingen.", "Choisissons donc des emballages réutilisables.", "Therefore, we should choose reusable packaging."), g5Option("newtopic", "Morgen wordt het zonnig.", "Demain, il fera beau.", "Tomorrow will be sunny."), g5Option("unfinished", "En toen misschien…", "Et puis peut-être…", "And then perhaps…")),
        g5Scenario("audience", "Je schrijft aan de directeur. Welke toon past het best?", "Tu écris au directeur. Quel ton convient le mieux ?", "You are writing to the principal. Which tone fits best?", "polite", g5Option("polite", "Kunt u ons voorstel alstublieft bekijken?", "Pourriez-vous examiner notre proposition, s'il vous plaît ?", "Could you please review our proposal?"), g5Option("command", "Doe dit nu meteen!", "Faites-le immédiatement !", "Do this right now!"), g5Option("slang", "Hé, check dit eens.", "Hé, regarde ça.", "Hey, check this out.")),
        g5Scenario("source_note", "Je gebruikt een feit uit een website. Welke notitie helpt bij revisie?", "Tu utilises un fait trouvé sur un site. Quelle note aide lors de la révision ?", "You use a fact from a website. Which note helps during revision?", "source", g5Option("source", "titel, auteur, datum en link noteren", "noter le titre, l'auteur, la date et le lien", "note the title, author, date, and link"), g5Option("memory", "alleen onthouden dat het online stond", "se souvenir seulement que c'était en ligne", "only remember that it was online"), g5Option("copy", "de bron verbergen", "cacher la source", "hide the source")),
        g5Scenario("punctuation", "Welke zin gebruikt leestekens duidelijk?", "Quelle phrase utilise clairement la ponctuation ?", "Which sentence uses punctuation clearly?", "clear", g5Option("clear", "Na de proef noteerden we drie resultaten: kleur, temperatuur en tijd.", "Après l'expérience, nous avons noté trois résultats : couleur, température et durée.", "After the test, we recorded three results: color, temperature, and time."), g5Option("none", "Na de proef noteerden we drie resultaten kleur temperatuur en tijd", "Après l'expérience nous avons noté trois résultats couleur température et durée", "After the test we recorded three results color temperature and time"), g5Option("many", "Na de proef!!! noteerden we: drie: resultaten???", "Après l'expérience !!! nous avons : trois : résultats ???", "After the test!!! we recorded: three: results???")),
        g5Scenario("revision_check", "Welke vraag stel je bij de laatste revisieronde?", "Quelle question poses-tu lors de la dernière révision ?", "Which question should you ask during the final revision?", "reader", g5Option("reader", "Begrijpt mijn lezer het doel en de volgorde?", "Mon lecteur comprend-il le but et l'ordre ?", "Will my reader understand the purpose and order?"), g5Option("length", "Is elk woord even lang?", "Chaque mot a-t-il la même longueur ?", "Is every word the same length?"), g5Option("color", "Staat elke zin in een andere kleur?", "Chaque phrase a-t-elle une couleur différente ?", "Is every sentence a different color?"))
    )
)

internal object Grade5FrenchIntegratedSkillsGenerator : Grade5ChoiceGenerator(
    generatorId = "generator.g5.language.french.integrated_skills",
    roadmapEntry = "roadmap.g5.language.french.integrated_skills",
    idNamespace = "g5.language.french.integrated_skills",
    skill = "french_integrated_skills",
    domain = LearningDomain.READING,
    automatedScope = "functional_french_listening_reading_and_response_selection",
    adultSupportKey = "requiresAdultFrenchConversation",
    inputMode = InputMode.LISTEN_AND_SELECT,
    hint = g5Text("Luister naar de Franse kernwoorden en kies een passende reactie.", "Écoute les mots-clés français et choisis une réponse adaptée.", "Listen for the key French words and choose a suitable response."),
    extraData = mapOf("audioRequired" to "true", "audioTargetLocale" to "fr-BE", "targetLanguage" to "fr-BE", "spontaneousFrenchSpeechMastered" to "false", "realConversationCompleted" to "false"),
    scenarios = listOf(
        g5FrenchScenario("greeting", "Luister naar het Frans. Welke reactie past?", "Écoute. Quelle réponse convient ?", "Listen to the French. Which reply fits?", "Bonjour, comment ça va ?", "well", g5SameOption("well", "Ça va bien, merci."), g5SameOption("night", "Bonne nuit."), g5SameOption("age", "J'ai onze ans.")),
        g5FrenchScenario("name", "Welke Franse reactie beantwoordt de vraag?", "Quelle réponse répond à la question ?", "Which French reply answers the question?", "Comment tu t'appelles ?", "name", g5SameOption("name", "Je m'appelle Lina."), g5SameOption("place", "J'habite à Gand."), g5SameOption("food", "J'aime les pommes.")),
        g5FrenchScenario("age", "Luister en kies het passende antwoord.", "Écoute et choisis la bonne réponse.", "Listen and choose the suitable answer.", "Quel âge as-tu ?", "eleven", g5SameOption("eleven", "J'ai onze ans."), g5SameOption("name", "Je m'appelle Amir."), g5SameOption("time", "Il est onze heures.")),
        g5FrenchScenario("direction", "Welke richting wordt gevraagd?", "Quelle direction est demandée ?", "Which direction is requested?", "Tourne à gauche après la bibliothèque.", "left", g5Option("left", "links afslaan", "tourner à gauche", "turn left"), g5Option("right", "rechts afslaan", "tourner à droite", "turn right"), g5Option("straight", "rechtdoor blijven gaan", "continuer tout droit", "keep going straight")),
        g5FrenchScenario("shopping", "Wat wil de spreker kopen?", "Que veut acheter la personne ?", "What does the speaker want to buy?", "Je voudrais deux pommes, s'il vous plaît.", "apples", g5Option("apples", "twee appels", "deux pommes", "two apples"), g5Option("pears", "twee peren", "deux poires", "two pears"), g5Option("bread", "twee broden", "deux pains", "two loaves")),
        g5FrenchScenario("family", "Over wie spreekt het kind?", "De qui parle l'enfant ?", "Who is the child talking about?", "Ma sœur s'appelle Zoé et elle aime dessiner.", "sister", g5Option("sister", "zijn zus", "sa sœur", "his sister"), g5Option("brother", "zijn broer", "son frère", "his brother"), g5Option("teacher", "zijn leraar", "son professeur", "his teacher")),
        g5FrenchScenario("hobby", "Welke hobby hoort bij de zin?", "Quel loisir correspond à la phrase ?", "Which hobby matches the sentence?", "Le mercredi, je joue au football.", "football", g5SameOption("football", "le football"), g5SameOption("music", "la musique"), g5SameOption("reading", "la lecture")),
        g5FrenchScenario("time", "Hoe laat begint de les?", "À quelle heure commence le cours ?", "What time does the lesson start?", "Le cours commence à neuf heures et demie.", "nine_thirty", g5Option("nine_thirty", "half tien", "neuf heures et demie", "nine thirty"), g5Option("nine", "negen uur", "neuf heures", "nine o'clock"), g5Option("ten_thirty", "half elf", "dix heures et demie", "ten thirty")),
        g5FrenchScenario("weather", "Welk weer wordt beschreven?", "Quel temps est décrit ?", "What weather is described?", "Il pleut et il y a beaucoup de vent.", "rain_wind", g5Option("rain_wind", "regen en wind", "de la pluie et du vent", "rain and wind"), g5Option("sun", "zon en warmte", "du soleil et de la chaleur", "sun and heat"), g5Option("snow", "sneeuw", "de la neige", "snow")),
        g5FrenchScenario("invitation", "Welke reactie aanvaardt de uitnodiging?", "Quelle réponse accepte l'invitation ?", "Which response accepts the invitation?", "Tu veux venir jouer samedi ?", "accept", g5SameOption("accept", "Oui, avec plaisir !"), g5SameOption("unknown", "Je ne sais pas où est mon livre."), g5SameOption("weather", "Il fait froid aujourd'hui."))
    )
)

internal object Grade5FrenchGrammarSpellingGenerator : Grade5ChoiceGenerator(
    generatorId = "generator.g5.spelling.french.grammar_spelling",
    roadmapEntry = "roadmap.g5.spelling.french.grammar_spelling",
    idNamespace = "g5.spelling.french.grammar_spelling",
    skill = "french_grammar_spelling",
    domain = LearningDomain.WRITING,
    automatedScope = "elementary_french_sentence_grammar_and_spelling",
    adultSupportKey = "requiresAdultFrenchWritingReview",
    hint = g5Text("Let op lidwoord, vervoeging, overeenkomst en woordvolgorde.", "Vérifie l'article, la conjugaison, l'accord et l'ordre des mots.", "Check the article, conjugation, agreement, and word order."),
    extraData = mapOf("independentFrenchWritingMastered" to "false"),
    scenarios = listOf(
        g5Scenario("article_gender", "Kies het juiste Franse lidwoord: … maison.", "Choisis le bon article : … maison.", "Choose the correct French article: … maison.", "la", g5SameOption("la", "la"), g5SameOption("le", "le"), g5SameOption("les", "les")),
        g5Scenario("plural", "Kies het juiste meervoud: un livre → deux …", "Choisis le bon pluriel : un livre → deux …", "Choose the correct plural: un livre → deux …", "livres", g5SameOption("livres", "livres"), g5SameOption("livre", "livre"), g5SameOption("livrez", "livrez")),
        g5Scenario("etre", "Vul aan: Nous … à l'école.", "Complète : Nous … à l'école.", "Complete: Nous … à l'école.", "sommes", g5SameOption("sommes", "sommes"), g5SameOption("êtes", "êtes"), g5SameOption("sont", "sont")),
        g5Scenario("avoir", "Vul aan: J'… un vélo.", "Complète : J'… un vélo.", "Complete: J'… un vélo.", "ai", g5SameOption("ai", "ai"), g5SameOption("as", "as"), g5SameOption("a", "a")),
        g5Scenario("aller", "Vul aan: Ils … au parc.", "Complète : Ils … au parc.", "Complete: Ils … au parc.", "vont", g5SameOption("vont", "vont"), g5SameOption("va", "va"), g5SameOption("allons", "allons")),
        g5Scenario("adjective_agreement", "Kies de juiste vorm: une … voiture.", "Choisis la bonne forme : une … voiture.", "Choose the correct form: une … voiture.", "rouge", g5SameOption("rouge", "rouge"), g5SameOption("rouges", "rouges"), g5SameOption("rouger", "rouger")),
        g5Scenario("negation", "Welke zin is correct ontkennend?", "Quelle phrase négative est correcte ?", "Which negative sentence is correct?", "negative", g5SameOption("negative", "Je n'aime pas le lait."), g5SameOption("missing_ne", "Je aime pas le lait."), g5SameOption("order", "Je pas n'aime le lait.")),
        g5Scenario("question", "Welke Franse vraag is correct?", "Quelle question française est correcte ?", "Which French question is correct?", "question", g5SameOption("question", "Où habites-tu ?"), g5SameOption("statement", "Tu habites où."), g5SameOption("wrong", "Où tu habite ?")),
        g5Scenario("accent", "Welke spelling is correct?", "Quelle orthographe est correcte ?", "Which spelling is correct?", "ecole", g5SameOption("ecole", "école"), g5SameOption("plain", "ecole"), g5SameOption("wrong", "ècole")),
        g5Scenario("word_order", "Welke zin heeft de juiste Franse woordvolgorde?", "Quelle phrase a le bon ordre des mots ?", "Which sentence has correct French word order?", "order", g5SameOption("order", "Nous mangeons une pomme."), g5SameOption("reverse", "Une pomme nous mangeons."), g5SameOption("mixed", "Mangeons nous pomme une."))
    )
)

internal object Grade5IntegratedWorldGenerator : Grade5ChoiceGenerator(
    generatorId = "generator.g5.world.integrated_geography_history_science",
    roadmapEntry = "roadmap.g5.world.integrated_geography_history_science",
    idNamespace = "g5.world.integrated_geography_history_science",
    skill = "integrated_geography_history_science",
    domain = LearningDomain.WORLD_KNOWLEDGE,
    automatedScope = "geography_history_science_connections_and_evidence",
    adultSupportKey = "requiresAdultFieldInquiry",
    hint = g5Text("Gebruik kaart, bron, tijdlijn of onderzoeksbewijs.", "Utilise la carte, la source, la ligne du temps ou les preuves.", "Use the map, source, timeline, or investigation evidence."),
    extraData = mapOf("fieldworkCompleted" to "false", "realExperimentCompleted" to "false", "heritageVisitCompleted" to "false"),
    scenarios = listOf(
        g5Scenario("map_scale", "Op een kaart is 1 cm gelijk aan 5 km. Welke kaartafstand stelt 15 km voor?", "Sur une carte, 1 cm représente 5 km. Quelle distance sur la carte représente 15 km ?", "On a map, 1 cm represents 5 km. What map distance represents 15 km?", "three", g5Option("three", "3 cm", "3 cm", "3 cm"), g5Option("five", "5 cm", "5 cm", "5 cm"), g5Option("fifteen", "15 cm", "15 cm", "15 cm")),
        g5Scenario("climate", "Welke factor verklaart vaak dat kustgebieden mildere temperaturen hebben?", "Quel facteur explique souvent les températures plus douces près de la côte ?", "Which factor often explains milder temperatures near coasts?", "sea", g5Option("sea", "de zee warmt en koelt traag", "la mer se réchauffe et se refroidit lentement", "the sea heats and cools slowly"), g5Option("roads", "er zijn meer wegen", "il y a plus de routes", "there are more roads"), g5Option("clocks", "de klokken lopen anders", "les horloges fonctionnent autrement", "clocks run differently")),
        g5Scenario("river_settlement", "Waarom ontstonden veel oude nederzettingen bij rivieren?", "Pourquoi de nombreuses anciennes colonies se sont-elles développées près des rivières ?", "Why did many early settlements develop near rivers?", "resources", g5Option("resources", "water, vervoer en vruchtbare grond", "eau, transport et sols fertiles", "water, transport, and fertile soil"), g5Option("mountains", "omdat alle rivieren op bergen liggen", "parce que toutes les rivières sont en montagne", "because every river is on a mountain"), g5Option("electricity", "voor moderne elektriciteitsnetten", "pour les réseaux électriques modernes", "for modern power grids")),
        g5Scenario("timeline", "Welke gebeurtenis komt op een tijdlijn het eerst?", "Quel événement vient en premier sur une ligne du temps ?", "Which event comes first on a timeline?", "oldest", g5Option("oldest", "een gebeurtenis uit 1830", "un événement de 1830", "an event from 1830"), g5Option("middle", "een gebeurtenis uit 1914", "un événement de 1914", "an event from 1914"), g5Option("recent", "een gebeurtenis uit 2002", "un événement de 2002", "an event from 2002")),
        g5Scenario("historical_source", "Welke bron is primair voor onderzoek naar een school in 1948?", "Quelle source est primaire pour étudier une école en 1948 ?", "Which is a primary source for studying a school in 1948?", "photo", g5Option("photo", "een klasfoto uit 1948", "une photo de classe de 1948", "a class photo from 1948"), g5Option("modern_book", "een leerboek uit 2025", "un manuel de 2025", "a textbook from 2025"), g5Option("fiction", "een moderne fantasieroman", "un roman fantastique moderne", "a modern fantasy novel")),
        g5Scenario("ecosystem", "Wat gebeurt waarschijnlijk als in een gebied veel insecten verdwijnen?", "Que se passe-t-il probablement si beaucoup d'insectes disparaissent d'une région ?", "What is likely if many insects disappear from an area?", "food_chain", g5Option("food_chain", "dieren die insecten eten vinden minder voedsel", "les animaux insectivores trouvent moins de nourriture", "animals that eat insects find less food"), g5Option("none", "voedselketens veranderen nooit", "les chaînes alimentaires ne changent jamais", "food chains never change"), g5Option("more", "alle planten groeien onmiddellijk sneller", "toutes les plantes poussent immédiatement plus vite", "all plants immediately grow faster")),
        g5Scenario("energy", "Welke energiebron is hernieuwbaar?", "Quelle source d'énergie est renouvelable ?", "Which energy source is renewable?", "wind", g5Option("wind", "wind", "le vent", "wind"), g5Option("coal", "steenkool", "le charbon", "coal"), g5Option("oil", "aardolie", "le pétrole", "oil")),
        g5Scenario("fair_test", "Je vergelijkt twee isolatiematerialen. Wat maakt de proef eerlijk?", "Tu compares deux matériaux isolants. Qu'est-ce qui rend l'expérience équitable ?", "You compare two insulation materials. What makes the test fair?", "same", g5Option("same", "dezelfde hoeveelheid water en begintemperatuur gebruiken", "utiliser la même quantité d'eau et la même température initiale", "use the same water amount and starting temperature"), g5Option("change_all", "ook de bekers en watervolumes veranderen", "changer aussi les gobelets et les volumes d'eau", "also change the cups and water volumes"), g5Option("guess", "alleen vooraf raden", "seulement deviner", "only guess")),
        g5Scenario("cause_effect", "Een stad verhardt steeds meer grond. Welk gevolg is aannemelijk bij hevige regen?", "Une ville imperméabilise de plus en plus le sol. Quelle conséquence est probable lors de fortes pluies ?", "A city covers more ground with hard surfaces. What is a likely result during heavy rain?", "runoff", g5Option("runoff", "water stroomt sneller af en kan overlast geven", "l'eau ruisselle plus vite et peut provoquer des inondations", "water runs off faster and may cause flooding"), g5Option("absorb", "de bodem neemt automatisch meer water op", "le sol absorbe automatiquement plus d'eau", "the soil automatically absorbs more water"), g5Option("dry", "regen verdwijnt voordat hij valt", "la pluie disparaît avant de tomber", "rain disappears before falling")),
        g5Scenario("technology_impact", "Welke uitspraak toont zowel voordeel als nadeel van een nieuwe weg?", "Quelle phrase montre un avantage et un inconvénient d'une nouvelle route ?", "Which statement shows both a benefit and a drawback of a new road?", "balanced", g5Option("balanced", "reizen kan sneller, maar natuurgebied kan verloren gaan", "les trajets peuvent être plus rapides, mais un espace naturel peut disparaître", "travel may be faster, but habitat may be lost"), g5Option("only_good", "een weg heeft alleen voordelen", "une route n'a que des avantages", "a road has only benefits"), g5Option("only_bad", "een weg kan nooit nuttig zijn", "une route ne peut jamais être utile", "a road can never be useful"))
    )
)

internal object Grade5DigitalInformationProductionGenerator : Grade5ChoiceGenerator(
    generatorId = "generator.g5.world.digital.information_production",
    roadmapEntry = "roadmap.g5.world.digital.information_production",
    idNamespace = "g5.world.digital.information_production",
    skill = "digital_information_production",
    domain = LearningDomain.WORLD_KNOWLEDGE,
    automatedScope = "search_source_evaluation_privacy_and_production_choices",
    adultSupportKey = "requiresAdultDigitalProduction",
    hint = g5Text("Controleer doel, bron, privacy en auteursrecht.", "Vérifie le but, la source, la vie privée et le droit d'auteur.", "Check purpose, source, privacy, and copyright."),
    extraData = mapOf("realDigitalProductionMastered" to "false", "capturedPersonalData" to "false", "accountCreationRequired" to "false"),
    scenarios = listOf(
        g5Scenario("search_query", "Welke zoekopdracht is het meest gericht voor informatie over waterbesparing thuis?", "Quelle recherche est la plus précise pour trouver des informations sur l'économie d'eau à la maison ?", "Which search query is most focused for information about saving water at home?", "focused", g5Option("focused", "water besparen thuis tips België", "économiser l'eau à la maison conseils Belgique", "save water at home tips Belgium"), g5Option("broad", "water", "eau", "water"), g5Option("unrelated", "mooie foto's", "belles photos", "pretty pictures")),
        g5Scenario("source_reliability", "Welke bron is het geschiktst voor actuele vaccinatie-informatie?", "Quelle source convient le mieux pour des informations actuelles sur la vaccination ?", "Which source is best for current vaccination information?", "official", g5Option("official", "een actuele officiële gezondheidswebsite", "un site officiel de santé à jour", "an up-to-date official health website"), g5Option("anonymous", "een anoniem bericht zonder datum", "un message anonyme sans date", "an anonymous undated post"), g5Option("ad", "een reclame zonder bronnen", "une publicité sans sources", "an advertisement without sources")),
        g5Scenario("author_date", "Wat controleer je eerst bij een online artikel?", "Que vérifies-tu d'abord dans un article en ligne ?", "What should you check first in an online article?", "metadata", g5Option("metadata", "auteur, datum en gebruikte bronnen", "l'auteur, la date et les sources", "author, date, and sources"), g5Option("font", "alleen het lettertype", "uniquement la police", "only the font"), g5Option("likes", "alleen het aantal likes", "uniquement le nombre de mentions J'aime", "only the number of likes")),
        g5Scenario("privacy", "Welke informatie deel je niet in een openbare presentatie?", "Quelle information ne partages-tu pas dans une présentation publique ?", "Which information should not be shared in a public presentation?", "address", g5Option("address", "je thuisadres", "ton adresse personnelle", "your home address"), g5Option("topic", "de titel van je onderwerp", "le titre de ton sujet", "your topic title"), g5Option("source", "de naam van een geraadpleegde bron", "le nom d'une source consultée", "the name of a source used")),
        g5Scenario("copyright", "Je wilt een foto online gebruiken. Wat doe je?", "Tu veux utiliser une photo trouvée en ligne. Que fais-tu ?", "You want to use an online photo. What should you do?", "license", g5Option("license", "licentie controleren en bron vermelden", "vérifier la licence et citer la source", "check the license and cite the source"), g5Option("copy", "zonder controle kopiëren", "copier sans vérifier", "copy it without checking"), g5Option("remove", "de naam van de maker verwijderen", "supprimer le nom du créateur", "remove the creator's name")),
        g5Scenario("file_organization", "Welke bestandsnaam is het duidelijkst?", "Quel nom de fichier est le plus clair ?", "Which file name is clearest?", "clear", g5Option("clear", "wateronderzoek_verslag_v2", "recherche_eau_rapport_v2", "water_research_report_v2"), g5Option("random", "nieuwnieuw123", "nouveaunouveau123", "newnew123"), g5Option("blank", "document", "document", "document")),
        g5Scenario("chart_choice", "Je wilt aantallen per maand vergelijken. Welke weergave past?", "Tu veux comparer des quantités par mois. Quelle représentation convient ?", "You want to compare quantities by month. Which display fits?", "bar", g5Option("bar", "een staafdiagram", "un diagramme en barres", "a bar chart"), g5Option("paragraph", "één lange alinea", "un long paragraphe", "one long paragraph"), g5Option("decoration", "een afbeelding zonder cijfers", "une image sans nombres", "a picture without numbers")),
        g5Scenario("presentation", "Welke dia is het duidelijkst?", "Quelle diapositive est la plus claire ?", "Which slide is clearest?", "simple", g5Option("simple", "korte titel, kernpunten en relevante afbeelding", "titre court, points clés et image pertinente", "short title, key points, and relevant image"), g5Option("crowded", "een volledige tekstpagina in klein lettertype", "une page entière en petits caractères", "a full page in tiny text"), g5Option("effects", "veel animaties zonder doel", "beaucoup d'animations sans but", "many purposeless animations")),
        g5Scenario("collaboration", "Twee leerlingen bewerken hetzelfde document. Wat voorkomt verlies van werk?", "Deux élèves modifient le même document. Qu'est-ce qui évite de perdre le travail ?", "Two students edit the same document. What prevents work from being lost?", "coordinate", g5Option("coordinate", "afspraken maken en versiegeschiedenis gebruiken", "se mettre d'accord et utiliser l'historique des versions", "coordinate and use version history"), g5Option("overwrite", "elkaars tekst zonder overleg wissen", "effacer le texte de l'autre sans concertation", "delete each other's text without discussion"), g5Option("copies", "veel naamloze kopieën maken", "créer de nombreuses copies sans nom", "make many unnamed copies")),
        g5Scenario("misinformation", "Een spectaculair bericht heeft geen auteur of bron. Wat is de beste reactie?", "Un message spectaculaire n'a ni auteur ni source. Quelle est la meilleure réaction ?", "A dramatic post has no author or source. What is the best response?", "verify", g5Option("verify", "niet delen en eerst via betrouwbare bronnen controleren", "ne pas partager et vérifier d'abord auprès de sources fiables", "do not share it; verify with reliable sources first"), g5Option("share", "meteen doorsturen", "le transférer immédiatement", "forward it immediately"), g5Option("believe", "aannemen dat het waar is omdat het spannend klinkt", "le croire parce qu'il semble spectaculaire", "believe it because it sounds exciting"))
    )
)

internal object Grade5HealthSocialLearningGenerator : Grade5ChoiceGenerator(
    generatorId = "generator.g5.world.health_social_learning",
    roadmapEntry = "roadmap.g5.world.health_social_learning",
    idNamespace = "g5.world.health_social_learning",
    skill = "health_social_learning",
    domain = LearningDomain.WORLD_KNOWLEDGE,
    automatedScope = "health_planning_social_and_metacognitive_strategy_choices",
    adultSupportKey = "requiresAdultHealthSocialReflection",
    hint = g5Text("Kies een veilige, respectvolle en haalbare strategie.", "Choisis une stratégie sûre, respectueuse et réalisable.", "Choose a safe, respectful, and realistic strategy."),
    extraData = mapOf("realHealthBehaviorMastered" to "false", "realSocialBehaviorMastered" to "false", "diagnosisInferred" to "false"),
    scenarios = listOf(
        g5Scenario("balanced_meal", "Welke lunch is het meest evenwichtig?", "Quel repas de midi est le plus équilibré ?", "Which lunch is most balanced?", "balanced", g5Option("balanced", "volkorenbrood, groente, fruit en water", "pain complet, légumes, fruit et eau", "whole-grain bread, vegetables, fruit, and water"), g5Option("sweets", "alleen snoep en frisdrank", "seulement des bonbons et du soda", "only candy and soda"), g5Option("none", "geen eten of drinken", "ni nourriture ni boisson", "no food or drink")),
        g5Scenario("sleep", "Welke gewoonte helpt een regelmatig slaapritme?", "Quelle habitude favorise un rythme de sommeil régulier ?", "Which habit supports a regular sleep schedule?", "routine", g5Option("routine", "ongeveer op hetzelfde tijdstip slapen en schermen vooraf wegleggen", "se coucher à une heure régulière et ranger les écrans avant", "sleep at a regular time and put screens away beforehand"), g5Option("late", "elke avond veel later slapen", "se coucher beaucoup plus tard chaque soir", "go to sleep much later every night"), g5Option("caffeine", "vlak voor bed energiedrank drinken", "boire une boisson énergisante avant le coucher", "drink an energy drink right before bed")),
        g5Scenario("movement", "Wat is een haalbare manier om meer te bewegen?", "Quelle est une manière réaliste de bouger davantage ?", "What is a realistic way to move more?", "daily", g5Option("daily", "dagelijks wandelen, fietsen of actief spelen", "marcher, rouler à vélo ou jouer activement chaque jour", "walk, cycle, or play actively each day"), g5Option("never", "alle beweging vermijden", "éviter tout mouvement", "avoid all movement"), g5Option("extreme", "zonder opbouw urenlang zwaar trainen", "s'entraîner intensément pendant des heures sans préparation", "train intensely for hours without building up")),
        g5Scenario("stress_plan", "Je hebt drie taken en voelt stress. Wat helpt eerst?", "Tu as trois tâches et tu te sens stressé. Qu'est-ce qui aide d'abord ?", "You have three tasks and feel stressed. What helps first?", "plan", g5Option("plan", "taken ordenen, klein beginnen en pauzes plannen", "classer les tâches, commencer petit et prévoir des pauses", "order the tasks, start small, and plan breaks"), g5Option("ignore", "alles negeren tot het laatste moment", "tout ignorer jusqu'au dernier moment", "ignore everything until the last moment"), g5Option("all", "alles tegelijk proberen", "essayer de tout faire en même temps", "try everything at once")),
        g5Scenario("collaboration", "Een groepslid komt niet aan het woord. Wat doe je?", "Un membre du groupe ne peut pas s'exprimer. Que fais-tu ?", "A group member is not getting a chance to speak. What should you do?", "invite", g5Option("invite", "ruimte maken en zijn of haar idee vragen", "lui laisser de la place et demander son idée", "make space and ask for their idea"), g5Option("continue", "blijven onderbreken", "continuer à l'interrompre", "keep interrupting"), g5Option("exclude", "de leerling uitsluiten", "exclure l'élève", "exclude the student")),
        g5Scenario("conflict", "Twee leerlingen willen hetzelfde materiaal. Welke oplossing is eerlijk?", "Deux élèves veulent le même matériel. Quelle solution est équitable ?", "Two students want the same material. Which solution is fair?", "agreement", g5Option("agreement", "beurten afspreken of samen gebruiken", "convenir de tours ou l'utiliser ensemble", "agree on turns or share it"), g5Option("grab", "het materiaal afpakken", "arracher le matériel", "grab the material"), g5Option("hide", "het verstoppen voor iedereen", "le cacher à tout le monde", "hide it from everyone")),
        g5Scenario("assertive", "Welke zin is duidelijk én respectvol?", "Quelle phrase est claire et respectueuse ?", "Which statement is clear and respectful?", "assertive", g5Option("assertive", "Ik wil graag uitspreken; daarna luister ik naar jou.", "J'aimerais terminer, puis je t'écouterai.", "I would like to finish speaking, then I will listen to you."), g5Option("aggressive", "Zwijg, alleen mijn idee telt!", "Tais-toi, seule mon idée compte !", "Be quiet, only my idea matters!"), g5Option("passive", "Het maakt nooit iets uit wat ik denk.", "Ce que je pense n'a jamais d'importance.", "What I think never matters.")),
        g5Scenario("study_strategy", "Je moet begrippen leren. Welke strategie controleert echt begrip?", "Tu dois apprendre des notions. Quelle stratégie vérifie vraiment la compréhension ?", "You need to learn concepts. Which strategy truly checks understanding?", "explain", g5Option("explain", "ze zonder boek in eigen woorden uitleggen", "les expliquer sans le livre avec tes propres mots", "explain them in your own words without the book"), g5Option("look", "alleen lang naar de pagina kijken", "regarder longtemps la page", "only stare at the page"), g5Option("copy", "de titel tien keer overschrijven", "recopier le titre dix fois", "copy the title ten times")),
        g5Scenario("reflection", "Na een toets merk je dat tijdsplanning moeilijk was. Welke reflectie helpt?", "Après un test, tu constates que la gestion du temps était difficile. Quelle réflexion aide ?", "After a test, you notice time management was difficult. Which reflection helps?", "specific", g5Option("specific", "Volgende keer verdeel ik eerst de tijd per onderdeel.", "La prochaine fois, je répartirai d'abord le temps par partie.", "Next time, I will divide my time across sections first."), g5Option("fixed", "Ik kan hier nooit beter in worden.", "Je ne pourrai jamais m'améliorer.", "I can never improve at this."), g5Option("blame", "Ik bekijk mijn aanpak niet.", "Je ne regarde pas ma méthode.", "I will not review my approach.")),
        g5Scenario("seek_help", "Wanneer vraag je best hulp aan een vertrouwde volwassene?", "Quand vaut-il mieux demander l'aide d'un adulte de confiance ?", "When should you ask a trusted adult for help?", "unsafe", g5Option("unsafe", "als iets onveilig voelt of een probleem blijft terugkomen", "si quelque chose semble dangereux ou si un problème persiste", "when something feels unsafe or a problem keeps returning"), g5Option("never", "nooit, ook niet bij gevaar", "jamais, même en cas de danger", "never, even when in danger"), g5Option("secret", "alle problemen geheimhouden", "garder tous les problèmes secrets", "keep every problem secret"))
    )
)
