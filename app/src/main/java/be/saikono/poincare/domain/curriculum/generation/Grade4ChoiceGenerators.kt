package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

internal data class Grade4Choice(val key: String, val nl: String, val fr: String, val en: String)

internal data class Grade4Scenario(
    val kind: String,
    val prompt: LocalizedText,
    val answerKey: String,
    val options: List<Grade4Choice>
)

private fun g4Text(nl: String, fr: String, en: String) = LocalizedText(nl = nl, fr = fr, en = en)
private fun g4Option(key: String, nl: String, fr: String, en: String) = Grade4Choice(key, nl, fr, en)
private fun g4Scenario(
    kind: String,
    nl: String,
    fr: String,
    en: String,
    answerKey: String,
    answer: Grade4Choice,
    distractor1: Grade4Choice,
    distractor2: Grade4Choice
) = Grade4Scenario(kind, g4Text(nl, fr, en), answerKey, listOf(answer, distractor1, distractor2))

internal abstract class Grade4ChoiceGenerator(
    final override val generatorId: String,
    private val roadmapEntry: String,
    private val idNamespace: String,
    private val skill: String,
    private val domain: LearningDomain,
    private val automatedScope: String,
    private val adultSupportKey: String,
    private val hint: LocalizedText,
    private val extraData: Map<String, String> = emptyMap(),
    private val scenarios: List<Grade4Scenario>
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
        if (parameters["grade"] != "4") add("grade must be 4")
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
                level = SkillLevel.FOURTH_GRADE,
                inputMode = InputMode.TAP_CHOICE,
                prompt = scenario.prompt,
                speechText = scenario.prompt,
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

internal object Grade4IntegratedComprehensionInteractionGenerator : Grade4ChoiceGenerator(
    generatorId = "generator.g4.language.integrated_comprehension_interaction",
    roadmapEntry = "roadmap.g4.language.integrated_comprehension_interaction",
    idNamespace = "g4.language.integrated_comprehension_interaction",
    skill = "integrated_comprehension_interaction",
    domain = LearningDomain.READING,
    automatedScope = "reading_listening_interaction_strategy_choice",
    adultSupportKey = "requiresAdultOralInteraction",
    hint = g4Text(
        "Gebruik de tekst of gesprekssituatie als bewijs voor je keuze.",
        "Utilise le texte ou la situation de communication pour justifier ton choix.",
        "Use the text or conversation as evidence for your choice."
    ),
    extraData = mapOf(
        "openOralMasteryInferred" to "false",
        "independentPresentationMastered" to "false"
    ),
    scenarios = listOf(
        g4Scenario("main_idea", "Een buurt plant bomen langs drukke straten. De bomen geven schaduw, vangen fijnstof op en maken de straat koeler. Wat is de hoofdgedachte?", "Un quartier plante des arbres le long des rues très fréquentées. Ils donnent de l'ombre, retiennent les particules fines et rafraîchissent la rue. Quelle est l'idée principale ?", "A neighborhood plants trees along busy streets. They provide shade, trap fine dust, and cool the street. What is the main idea?", "benefits", g4Option("benefits", "Bomen maken een buurt gezonder en aangenamer.", "Les arbres rendent le quartier plus sain et agréable.", "Trees make a neighborhood healthier and more pleasant."), g4Option("traffic", "Alle straten worden voor verkeer gesloten.", "Toutes les rues sont fermées à la circulation.", "All streets are closed to traffic."), g4Option("winter", "Bomen groeien alleen in de winter.", "Les arbres poussent seulement en hiver.", "Trees only grow in winter.")),
        g4Scenario("inference", "Noor komt binnen met modder aan haar laarzen en druppels op haar jas. Wat kun je besluiten?", "Noor entre avec de la boue sur ses bottes et des gouttes sur sa veste. Que peux-tu en déduire ?", "Noor comes inside with mud on her boots and drops on her coat. What can you infer?", "wet", g4Option("wet", "Buiten was het nat.", "Il faisait humide dehors.", "It was wet outside."), g4Option("heat", "Buiten was het zeker erg heet.", "Il faisait certainement très chaud.", "It was definitely very hot outside."), g4Option("indoors", "Noor bleef de hele dag binnen.", "Noor est restée à l'intérieur toute la journée.", "Noor stayed indoors all day.")),
        g4Scenario("text_structure", "Een tekst beschrijft eerst twee vervoermiddelen en legt daarna uit wat hetzelfde en verschillend is. Welke tekststructuur gebruikt de schrijver?", "Un texte décrit d'abord deux moyens de transport, puis explique leurs ressemblances et différences. Quelle structure utilise l'auteur ?", "A text first describes two kinds of transport and then explains their similarities and differences. Which text structure is used?", "compare", g4Option("compare", "vergelijken", "comparaison", "compare and contrast"), g4Option("sequence", "alleen tijdsvolgorde", "uniquement l'ordre chronologique", "only time order"), g4Option("recipe", "een recept", "une recette", "a recipe")),
        g4Scenario("viewpoint", "Lina zegt: 'De speelplaats heeft meer bomen nodig, want dan is er schaduw.' Wat is haar standpunt?", "Lina dit : « La cour a besoin de plus d'arbres, car ils donnent de l'ombre. » Quel est son point de vue ?", "Lina says, 'The playground needs more trees because they provide shade.' What is her viewpoint?", "trees", g4Option("trees", "Er moeten meer bomen komen.", "Il faut planter plus d'arbres.", "More trees should be planted."), g4Option("remove", "Alle bomen moeten weg.", "Il faut enlever tous les arbres.", "All trees should be removed."), g4Option("unknown", "Schaduw heeft geen nut.", "L'ombre ne sert à rien.", "Shade is useless.")),
        g4Scenario("evidence", "Welke zin is het sterkste bewijs dat fietsen gezond kan zijn?", "Quelle phrase prouve le mieux que faire du vélo peut être bon pour la santé ?", "Which sentence is the strongest evidence that cycling can be healthy?", "movement", g4Option("movement", "Tijdens het fietsen bewegen je benen en stijgt je hartslag.", "À vélo, tes jambes bougent et ton rythme cardiaque augmente.", "Cycling moves your legs and raises your heart rate."), g4Option("colors", "Fietsen bestaan in veel kleuren.", "Les vélos existent en plusieurs couleurs.", "Bicycles come in many colors."), g4Option("bells", "Veel fietsen hebben een bel.", "Beaucoup de vélos ont une sonnette.", "Many bicycles have a bell.")),
        g4Scenario("dialogue_turn", "Sam vertelt dat hij zenuwachtig is voor zijn spreekbeurt. Welke reactie helpt het gesprek vooruit?", "Sam dit qu'il est nerveux avant son exposé. Quelle réponse fait avancer la conversation ?", "Sam says he is nervous about his presentation. Which response helps the conversation continue?", "ask", g4Option("ask", "Wat maakt je precies zenuwachtig?", "Qu'est-ce qui te rend exactement nerveux ?", "What exactly makes you nervous?"), g4Option("dismiss", "Dat is niet belangrijk.", "Ce n'est pas important.", "That is not important."), g4Option("change", "Ik praat liever over iets anders.", "Je préfère parler d'autre chose.", "I would rather talk about something else.")),
        g4Scenario("presentation_order", "Je geeft een korte presentatie over bijen. Welke volgorde is het duidelijkst?", "Tu présentes brièvement les abeilles. Quel ordre est le plus clair ?", "You are giving a short presentation about bees. Which order is clearest?", "logical", g4Option("logical", "onderwerp noemen, uitleg geven, kort samenvatten", "annoncer le sujet, expliquer, résumer", "state the topic, explain it, summarize"), g4Option("ending_first", "afsluiten, onderwerp noemen, stoppen", "conclure, annoncer le sujet, arrêter", "conclude, state the topic, stop"), g4Option("random", "losse weetjes zonder verband", "des faits sans lien", "unconnected facts in random order")),
        g4Scenario("active_listening", "Iemand legt een spelregel uit. Wat toont dat je actief luistert?", "Quelqu'un explique une règle de jeu. Qu'est-ce qui montre que tu écoutes activement ?", "Someone explains a game rule. What shows active listening?", "repeat", g4Option("repeat", "de regel in je eigen woorden herhalen", "reformuler la règle avec tes propres mots", "repeat the rule in your own words"), g4Option("interrupt", "voortdurend onderbreken", "interrompre sans cesse", "interrupt constantly"), g4Option("ignore", "wegkijken en iets anders doen", "regarder ailleurs et faire autre chose", "look away and do something else")),
        g4Scenario("clarify_question", "Je begrijpt niet waar je een opdracht moet inleveren. Welke vraag verduidelijkt dit het best?", "Tu ne comprends pas où remettre un travail. Quelle question clarifie le mieux la situation ?", "You do not understand where to hand in an assignment. Which question best clarifies this?", "where", g4Option("where", "Waar moet ik de opdracht precies inleveren?", "Où dois-je remettre exactement le travail ?", "Where exactly should I hand in the assignment?"), g4Option("when", "Is dit vak soms moeilijk?", "Cette matière est-elle parfois difficile ?", "Is this subject sometimes difficult?"), g4Option("unrelated", "Welke kleur heeft de map?", "De quelle couleur est la farde ?", "What color is the folder?")),
        g4Scenario("summary", "Tekst: Een composthoop verwerkt groente- en tuinafval. Kleine organismen breken het af tot voedselrijke aarde. Welke samenvatting is het best?", "Texte : Un compost transforme les déchets de cuisine et de jardin. De petits organismes les décomposent en terre riche. Quel résumé convient le mieux ?", "Text: A compost pile processes kitchen and garden waste. Small organisms break it down into rich soil. Which is the best summary?", "compost", g4Option("compost", "Organisch afval wordt afgebroken tot rijke aarde.", "Les déchets organiques deviennent une terre riche.", "Organic waste is broken down into rich soil."), g4Option("all_waste", "Alle soorten afval horen op de composthoop.", "Tous les déchets vont au compost.", "Every kind of waste belongs in compost."), g4Option("garden", "Een tuin heeft altijd veel bloemen.", "Un jardin a toujours beaucoup de fleurs.", "A garden always has many flowers."))
    )
)

internal object Grade4ScienceInquiryDesignGenerator : Grade4ChoiceGenerator(
    generatorId = "generator.g4.world.science.inquiry_design",
    roadmapEntry = "roadmap.g4.world.science.inquiry_design",
    idNamespace = "g4.world.science.inquiry_design",
    skill = "science_inquiry_design",
    domain = LearningDomain.WORLD_KNOWLEDGE,
    automatedScope = "inquiry_planning_evidence_and_design_reasoning",
    adultSupportKey = "requiresAdultExperiment",
    hint = g4Text(
        "Kies een veilige, eerlijke en meetbare onderzoeksstap.",
        "Choisis une étape d'enquête sûre, équitable et mesurable.",
        "Choose a safe, fair, and measurable investigation step."
    ),
    extraData = mapOf(
        "realExperimentCompleted" to "false",
        "practicalSkillMastered" to "false"
    ),
    scenarios = listOf(
        g4Scenario("question", "Welke vraag kun je met een proef onderzoeken?", "Quelle question peut-on étudier par une expérience ?", "Which question can be investigated with an experiment?", "testable", g4Option("testable", "Smelt ijs sneller in zonlicht of in de schaduw?", "La glace fond-elle plus vite au soleil ou à l'ombre ?", "Does ice melt faster in sunlight or shade?"), g4Option("opinion", "Welke kleur is het mooist?", "Quelle couleur est la plus belle ?", "Which color is the prettiest?"), g4Option("past", "Wat dacht iemand honderd jaar geleden?", "Que pensait quelqu'un il y a cent ans ?", "What did someone think one hundred years ago?")),
        g4Scenario("hypothesis", "Je onderzoekt of warm water suiker sneller oplost. Welke hypothese past?", "Tu étudies si le sucre se dissout plus vite dans l'eau chaude. Quelle hypothèse convient ?", "You investigate whether sugar dissolves faster in warm water. Which hypothesis fits?", "warm", g4Option("warm", "In warm water lost suiker sneller op dan in koud water.", "Le sucre se dissout plus vite dans l'eau chaude que dans l'eau froide.", "Sugar dissolves faster in warm water than cold water."), g4Option("color", "Suiker is altijd wit.", "Le sucre est toujours blanc.", "Sugar is always white."), g4Option("taste", "Ik vind suiker lekker.", "J'aime le sucre.", "I like sugar.")),
        g4Scenario("fair_test", "Je vergelijkt hoe snel twee papieren vliegtuigen vliegen. Wat moet gelijk blijven?", "Tu compares la vitesse de deux avions en papier. Qu'est-ce qui doit rester identique ?", "You compare how fast two paper planes fly. What should stay the same?", "throw", g4Option("throw", "de werper en de startplaats", "la personne qui lance et le point de départ", "the thrower and starting point"), g4Option("many", "zoveel mogelijk dingen tegelijk veranderen", "changer le plus de choses possible", "change as many things as possible"), g4Option("guess", "alleen vooraf raden", "seulement deviner à l'avance", "only guess beforehand")),
        g4Scenario("variable", "Je onderzoekt het effect van licht op plantengroei. Welke factor verander je bewust?", "Tu étudies l'effet de la lumière sur la croissance d'une plante. Quel facteur modifies-tu volontairement ?", "You investigate the effect of light on plant growth. Which factor do you deliberately change?", "light", g4Option("light", "de hoeveelheid licht", "la quantité de lumière", "the amount of light"), g4Option("all", "licht, water én grond tegelijk", "la lumière, l'eau et la terre à la fois", "light, water, and soil all at once"), g4Option("result", "de gemeten hoogte achteraf", "la hauteur mesurée ensuite", "the measured height afterward")),
        g4Scenario("observation", "Welke zin is een waarneming en geen mening?", "Quelle phrase est une observation et non une opinion ?", "Which sentence is an observation rather than an opinion?", "measure", g4Option("measure", "De stengel is 12 centimeter lang.", "La tige mesure 12 centimètres.", "The stem is 12 centimeters long."), g4Option("pretty", "De plant is prachtig.", "La plante est magnifique.", "The plant is beautiful."), g4Option("best", "Dit is de beste plant.", "C'est la meilleure plante.", "This is the best plant.")),
        g4Scenario("measurement", "Welk hulpmiddel gebruik je om de temperatuur van water te meten?", "Quel outil utilises-tu pour mesurer la température de l'eau ?", "Which tool should you use to measure water temperature?", "thermometer", g4Option("thermometer", "een thermometer", "un thermomètre", "a thermometer"), g4Option("ruler", "een meetlat", "une règle", "a ruler"), g4Option("scale", "een weegschaal", "une balance", "a scale")),
        g4Scenario("conclusion", "Drie ijsblokjes in de zon smolten sneller dan drie in de schaduw. Welke conclusie past bij deze proef?", "Trois glaçons au soleil ont fondu plus vite que trois à l'ombre. Quelle conclusion convient à cette expérience ?", "Three ice cubes in the sun melted faster than three in the shade. Which conclusion fits this test?", "sun", g4Option("sun", "In deze proef smolt ijs sneller in de zon.", "Dans cette expérience, la glace a fondu plus vite au soleil.", "In this test, ice melted faster in the sun."), g4Option("always", "IJs smelt overal altijd even snel.", "La glace fond toujours partout à la même vitesse.", "Ice always melts at the same speed everywhere."), g4Option("boil", "Zonlicht laat water onmiddellijk koken.", "Le soleil fait bouillir l'eau immédiatement.", "Sunlight makes water boil immediately.")),
        g4Scenario("repeat", "Waarom herhaal je een meting meerdere keren?", "Pourquoi répète-t-on une mesure plusieurs fois ?", "Why repeat a measurement several times?", "reliable", g4Option("reliable", "om te controleren of het resultaat betrouwbaar is", "pour vérifier si le résultat est fiable", "to check whether the result is reliable"), g4Option("change", "om telkens een andere vraag te onderzoeken", "pour étudier une autre question à chaque fois", "to investigate a different question each time"), g4Option("hide", "om afwijkende resultaten te verbergen", "pour cacher les résultats différents", "to hide different results")),
        g4Scenario("safety", "Je wilt onderzoeken welk materiaal warmte goed geleidt. Wat is de veiligste aanpak?", "Tu veux étudier quel matériau conduit bien la chaleur. Quelle approche est la plus sûre ?", "You want to investigate which material conducts heat well. What is the safest approach?", "adult", g4Option("adult", "gebruik lauw water en werk met een volwassene", "utiliser de l'eau tiède avec un adulte", "use warm water and work with an adult"), g4Option("flame", "houd materialen zelf in een vlam", "tenir toi-même les matériaux dans une flamme", "hold materials in a flame yourself"), g4Option("taste", "proef de materialen", "goûter les matériaux", "taste the materials")),
        g4Scenario("design_improve", "Een papieren brug zakt door onder vijf blokjes. Welke verbetering test je het best als volgende?", "Un pont en papier s'affaisse sous cinq blocs. Quelle amélioration testes-tu ensuite ?", "A paper bridge bends under five blocks. Which improvement should you test next?", "fold", g4Option("fold", "vouw dezelfde soort papier in een stevige vorm", "plier le même papier dans une forme plus solide", "fold the same paper into a stronger shape"), g4Option("unrelated", "kleur het papier blauw", "colorier le papier en bleu", "color the paper blue"), g4Option("many", "verander tegelijk papier, lengte en blokjes", "changer à la fois le papier, la longueur et les blocs", "change the paper, length, and blocks all at once"))
    )
)

internal object Grade4GeographyHistoryGenerator : Grade4ChoiceGenerator(
    generatorId = "generator.g4.world.geography_history",
    roadmapEntry = "roadmap.g4.world.geography_history",
    idNamespace = "g4.world.geography_history",
    skill = "geography_history",
    domain = LearningDomain.WORLD_KNOWLEDGE,
    automatedScope = "map_chronology_source_and_change_reasoning",
    adultSupportKey = "requiresAdultMapHistoryPractice",
    hint = g4Text(
        "Gebruik legenda, richting, schaal, tijdsvolgorde en broninformatie.",
        "Utilise la légende, l'orientation, l'échelle, la chronologie et les sources.",
        "Use the legend, direction, scale, chronology, and source information."
    ),
    extraData = mapOf(
        "fieldworkCompleted" to "false",
        "heritageVisitCompleted" to "false"
    ),
    scenarios = listOf(
        g4Scenario("map_scale", "Op een kaart is 1 centimeter gelijk aan 2 kilometer. Twee plaatsen liggen 3 centimeter uit elkaar. Wat is de echte afstand?", "Sur une carte, 1 centimètre représente 2 kilomètres. Deux lieux sont séparés de 3 centimètres. Quelle est la distance réelle ?", "On a map, 1 centimeter represents 2 kilometers. Two places are 3 centimeters apart. What is the real distance?", "six", g4Option("six", "6 kilometer", "6 kilomètres", "6 kilometers"), g4Option("five", "5 kilometer", "5 kilomètres", "5 kilometers"), g4Option("one", "1 kilometer", "1 kilomètre", "1 kilometer")),
        g4Scenario("cardinal_direction", "De bibliotheek ligt ten noorden van de school. In welke richting ga je van de school naar de bibliotheek?", "La bibliothèque est au nord de l'école. Dans quelle direction vas-tu de l'école à la bibliothèque ?", "The library is north of the school. Which direction do you travel from school to the library?", "north", g4Option("north", "noord", "nord", "north"), g4Option("south", "zuid", "sud", "south"), g4Option("west", "west", "ouest", "west")),
        g4Scenario("legend", "In de legende staat een blauwe lijn voor een rivier. Wat betekent een blauwe lijn op de kaart?", "Dans la légende, une ligne bleue représente une rivière. Que signifie une ligne bleue sur la carte ?", "The legend says a blue line represents a river. What does a blue line on the map mean?", "river", g4Option("river", "een rivier", "une rivière", "a river"), g4Option("road", "een autosnelweg", "une autoroute", "a motorway"), g4Option("border", "een landsgrens", "une frontière", "a national border")),
        g4Scenario("landscape", "Welk kenmerk past het best bij een stedelijk landschap?", "Quelle caractéristique correspond le mieux à un paysage urbain ?", "Which feature best matches an urban landscape?", "dense", g4Option("dense", "veel gebouwen en wegen dicht bij elkaar", "beaucoup de bâtiments et de routes proches", "many buildings and roads close together"), g4Option("fields", "uitgestrekte akkers met weinig huizen", "de vastes champs avec peu de maisons", "wide fields with few houses"), g4Option("wild", "alleen ongerepte natuur zonder wegen", "uniquement une nature sauvage sans routes", "only untouched nature without roads")),
        g4Scenario("region", "Welke uitspraak over België is juist?", "Quelle affirmation sur la Belgique est correcte ?", "Which statement about Belgium is correct?", "country", g4Option("country", "België is een land in Europa.", "La Belgique est un pays d'Europe.", "Belgium is a country in Europe."), g4Option("city", "België is alleen een stad.", "La Belgique est seulement une ville.", "Belgium is only a city."), g4Option("continent", "België is een continent.", "La Belgique est un continent.", "Belgium is a continent.")),
        g4Scenario("chronology", "Zet in de juiste volgorde: een middeleeuws kasteel, een stoomtrein, een smartphone.", "Mets dans l'ordre : un château médiéval, un train à vapeur, un smartphone.", "Put these in order: a medieval castle, a steam train, a smartphone.", "order", g4Option("order", "kasteel – stoomtrein – smartphone", "château – train à vapeur – smartphone", "castle – steam train – smartphone"), g4Option("reverse", "smartphone – kasteel – stoomtrein", "smartphone – château – train à vapeur", "smartphone – castle – steam train"), g4Option("mixed", "stoomtrein – smartphone – kasteel", "train à vapeur – smartphone – château", "steam train – smartphone – castle")),
        g4Scenario("source", "Welke bron helpt het meest om te weten hoe een schoolklas er honderd jaar geleden uitzag?", "Quelle source aide le mieux à savoir à quoi ressemblait une classe il y a cent ans ?", "Which source best shows what a classroom looked like one hundred years ago?", "photo", g4Option("photo", "een gedateerde foto van een klas uit die tijd", "une photo datée d'une classe de l'époque", "a dated photograph of a classroom from that time"), g4Option("game", "een modern computerspel", "un jeu vidéo moderne", "a modern computer game"), g4Option("weather", "de weersvoorspelling van morgen", "la météo de demain", "tomorrow's weather forecast")),
        g4Scenario("cause_change", "Door de komst van een spoorlijn konden goederen sneller worden vervoerd. Wat is hier de oorzaak?", "Grâce à l'arrivée du chemin de fer, les marchandises ont été transportées plus vite. Quelle est la cause ?", "After a railway arrived, goods could be transported faster. What is the cause?", "railway", g4Option("railway", "de aanleg van de spoorlijn", "la construction du chemin de fer", "building the railway"), g4Option("faster", "het snellere vervoer", "le transport plus rapide", "faster transport"), g4Option("goods", "het bestaan van goederen", "l'existence des marchandises", "the existence of goods")),
        g4Scenario("past_present", "Welke verandering tussen vroeger en nu is juist?", "Quel changement entre autrefois et aujourd'hui est correct ?", "Which change from past to present is correct?", "communication", g4Option("communication", "Berichten kunnen nu veel sneller over grote afstand worden verstuurd.", "Les messages peuvent maintenant être envoyés beaucoup plus vite sur de longues distances.", "Messages can now be sent much faster over long distances."), g4Option("same", "Alle mensen reizen nog precies zoals vijfhonderd jaar geleden.", "Tout le monde voyage exactement comme il y a cinq cents ans.", "Everyone travels exactly as they did five hundred years ago."), g4Option("none", "Er is niets veranderd in communicatie.", "Rien n'a changé dans la communication.", "Nothing has changed in communication.")),
        g4Scenario("heritage", "Waarom bewaren mensen oude gebouwen, voorwerpen en verhalen?", "Pourquoi conserve-t-on d'anciens bâtiments, objets et récits ?", "Why do people preserve old buildings, objects, and stories?", "learn", g4Option("learn", "om over het verleden en erfgoed te leren", "pour apprendre le passé et le patrimoine", "to learn about the past and heritage"), g4Option("hide", "om alle informatie te verbergen", "pour cacher toutes les informations", "to hide all information"), g4Option("replace", "om nieuwe gebouwen onmogelijk te maken", "pour empêcher toute nouvelle construction", "to prevent all new buildings"))
    )
)

internal object Grade4DigitalSocialSelfRegulationGenerator : Grade4ChoiceGenerator(
    generatorId = "generator.g4.world.digital_social_self_regulation",
    roadmapEntry = "roadmap.g4.world.digital_social_self_regulation",
    idNamespace = "g4.world.digital_social_self_regulation",
    skill = "digital_social_self_regulation",
    domain = LearningDomain.WORLD_KNOWLEDGE,
    automatedScope = "digital_safety_social_choice_and_self_regulation",
    adultSupportKey = "requiresAdultDigitalSocialPractice",
    hint = g4Text(
        "Kies de veilige, respectvolle stap die helpt om doelgericht te blijven.",
        "Choisis l'étape sûre et respectueuse qui aide à rester concentré.",
        "Choose the safe, respectful step that helps you stay focused."
    ),
    extraData = mapOf(
        "realDigitalActionMastered" to "false",
        "realSocialBehaviorMastered" to "false"
    ),
    scenarios = listOf(
        g4Scenario("password", "Welk wachtwoord is het sterkst?", "Quel mot de passe est le plus fort ?", "Which password is strongest?", "strong", g4Option("strong", "Maan!Fiets27Boom", "Lune!Vélo27Arbre", "Moon!Bike27Tree"), g4Option("name", "mijnnaam", "monnom", "myname"), g4Option("simple", "123456", "123456", "123456")),
        g4Scenario("privacy", "Een onbekende speler vraagt je volledige naam en adres. Wat doe je?", "Un joueur inconnu demande ton nom complet et ton adresse. Que fais-tu ?", "An unknown player asks for your full name and address. What should you do?", "stop", g4Option("stop", "niet delen en een vertrouwde volwassene verwittigen", "ne rien partager et prévenir un adulte de confiance", "do not share it and tell a trusted adult"), g4Option("share", "alles meteen doorsturen", "tout envoyer immédiatement", "send everything immediately"), g4Option("trade", "alleen delen als je iets terugkrijgt", "partager seulement en échange de quelque chose", "share it only if you get something back")),
        g4Scenario("source_check", "Een opvallend bericht heeft geen auteur, datum of bronnen. Wat is de beste volgende stap?", "Un message surprenant n'a ni auteur, ni date, ni sources. Quelle est la meilleure étape suivante ?", "A surprising post has no author, date, or sources. What is the best next step?", "verify", g4Option("verify", "controleren bij betrouwbare bronnen", "vérifier auprès de sources fiables", "check reliable sources"), g4Option("forward", "het meteen doorsturen", "le transférer immédiatement", "forward it immediately"), g4Option("believe", "het geloven omdat het opvallend is", "le croire parce qu'il est frappant", "believe it because it is surprising")),
        g4Scenario("online_kindness", "Welke reactie is respectvol in een groepschat?", "Quelle réponse est respectueuse dans une discussion de groupe ?", "Which reply is respectful in a group chat?", "kind", g4Option("kind", "Ik ben het niet eens; mag ik uitleggen waarom?", "Je ne suis pas d'accord ; puis-je expliquer pourquoi ?", "I disagree; may I explain why?"), g4Option("insult", "Wat een dom idee!", "Quelle idée stupide !", "What a stupid idea!"), g4Option("exclude", "Jij mag nooit meer meedoen.", "Tu ne peux plus jamais participer.", "You may never join again.")),
        g4Scenario("screen_break", "Je merkt dat je ogen moe worden tijdens schermwerk. Wat helpt?", "Tu remarques que tes yeux fatiguent pendant un travail sur écran. Qu'est-ce qui aide ?", "You notice that your eyes are tired during screen work. What helps?", "break", g4Option("break", "een korte pauze nemen en in de verte kijken", "faire une courte pause et regarder au loin", "take a short break and look into the distance"), g4Option("closer", "het scherm dichter bij je ogen houden", "rapprocher l'écran des yeux", "hold the screen closer to your eyes"), g4Option("dark", "in het donker blijven doorgaan", "continuer dans le noir", "keep going in the dark")),
        g4Scenario("planning", "Je hebt drie taken. Wat is een goede eerste stap?", "Tu as trois tâches. Quelle est une bonne première étape ?", "You have three tasks. What is a good first step?", "plan", g4Option("plan", "taken noteren en een haalbare volgorde kiezen", "noter les tâches et choisir un ordre réaliste", "write down the tasks and choose a realistic order"), g4Option("avoid", "alles uitstellen zonder plan", "tout reporter sans plan", "delay everything without a plan"), g4Option("random", "telkens willekeurig wisselen", "changer de tâche au hasard", "switch randomly between tasks")),
        g4Scenario("distraction", "Een melding onderbreekt je terwijl je leert. Wat helpt om te focussen?", "Une notification t'interrompt pendant que tu étudies. Qu'est-ce qui aide à rester concentré ?", "A notification interrupts you while you study. What helps you focus?", "silence", g4Option("silence", "meldingen tijdelijk uitschakelen", "désactiver temporairement les notifications", "temporarily turn notifications off"), g4Option("open", "elke melding onmiddellijk openen", "ouvrir immédiatement chaque notification", "open every notification immediately"), g4Option("more", "nog meer apps tegelijk starten", "ouvrir encore plus d'applications", "start even more apps at once")),
        g4Scenario("emotion_pause", "Je wordt erg boos tijdens een spel. Wat is een verstandige stap?", "Tu te mets très en colère pendant un jeu. Quelle est une étape raisonnable ?", "You become very angry during a game. What is a sensible step?", "pause", g4Option("pause", "pauzeren, rustig ademen en daarna praten", "faire une pause, respirer calmement puis parler", "pause, breathe calmly, then talk"), g4Option("shout", "meteen beginnen roepen", "commencer immédiatement à crier", "start shouting immediately"), g4Option("break", "materiaal stukmaken", "casser le matériel", "break the equipment")),
        g4Scenario("conflict", "Twee kinderen willen tegelijk hetzelfde toestel gebruiken. Welke oplossing is eerlijk?", "Deux enfants veulent utiliser le même appareil en même temps. Quelle solution est équitable ?", "Two children want to use the same device at the same time. Which solution is fair?", "turns", g4Option("turns", "een timer gebruiken en om de beurt gaan", "utiliser un minuteur et jouer à tour de rôle", "use a timer and take turns"), g4Option("strongest", "de sterkste beslist", "le plus fort décide", "the strongest person decides"), g4Option("hide", "het toestel verstoppen", "cacher l'appareil", "hide the device")),
        g4Scenario("help", "Je ziet online iets dat je bang of ongemakkelijk maakt. Wat doe je?", "Tu vois en ligne quelque chose qui te fait peur ou te met mal à l'aise. Que fais-tu ?", "You see something online that makes you afraid or uncomfortable. What should you do?", "adult", g4Option("adult", "stoppen en het aan een vertrouwde volwassene tonen", "arrêter et le montrer à un adulte de confiance", "stop and show it to a trusted adult"), g4Option("alone", "het geheimhouden en alleen verder zoeken", "le garder secret et continuer seul", "keep it secret and continue alone"), g4Option("reply", "de afzender uitdagen", "provoquer l'expéditeur", "challenge the sender"))
    )
)

internal object Grade4ModernLanguagesInitiationGenerator : Grade4ChoiceGenerator(
    generatorId = "generator.g4.language.modern_languages.initiation",
    roadmapEntry = "roadmap.g4.language.modern_languages.initiation",
    idNamespace = "g4.language.modern_languages.initiation",
    skill = "modern_languages_initiation",
    domain = LearningDomain.READING,
    automatedScope = "optional_french_english_word_and_sentence_recognition",
    adultSupportKey = "requiresAdultOralLanguagePractice",
    hint = g4Text(
        "Luister naar de hele uitdrukking en verbind ze met de juiste betekenis.",
        "Écoute l'expression complète et relie-la au bon sens.",
        "Listen to the whole expression and match it to the correct meaning."
    ),
    extraData = mapOf(
        "formalFrenchInstruction" to "false",
        "oralMasteryInferred" to "false"
    ),
    scenarios = listOf(
        g4Scenario("greeting_formal", "Welke Franse begroeting gebruik je overdag?", "Quelle salutation française utilise-t-on pendant la journée ?", "Which French greeting is used during the day?", "bonjour", g4Option("bonjour", "bonjour", "bonjour", "bonjour"), g4Option("merci", "merci", "merci", "merci"), g4Option("goodbye", "au revoir", "au revoir", "au revoir")),
        g4Scenario("introduce_name", "Wat betekent 'Je m'appelle Lina'?", "Que signifie « Je m'appelle Lina » ?", "What does 'Je m'appelle Lina' mean?", "name", g4Option("name", "Ik heet Lina.", "Je m'appelle Lina.", "My name is Lina."), g4Option("age", "Ik ben tien jaar.", "J'ai dix ans.", "I am ten years old."), g4Option("like", "Ik hou van lezen.", "J'aime lire.", "I like reading.")),
        g4Scenario("age", "Welke Franse zin betekent 'Ik ben negen jaar'?", "Quelle phrase française signifie « J'ai neuf ans » ?", "Which French sentence means 'I am nine years old'?", "nine", g4Option("nine", "J'ai neuf ans.", "J'ai neuf ans.", "J'ai neuf ans."), g4Option("name", "Je m'appelle Neuf.", "Je m'appelle Neuf.", "Je m'appelle Neuf."), g4Option("number", "Il y a neuf livres.", "Il y a neuf livres.", "Il y a neuf livres.")),
        g4Scenario("classroom_instruction", "Wat moet je doen bij de Franse instructie 'Ouvre ton livre'?", "Que dois-tu faire quand tu entends « Ouvre ton livre » ?", "What should you do when you hear the French instruction 'Ouvre ton livre'?", "open", g4Option("open", "je boek openen", "ouvrir ton livre", "open your book"), g4Option("close", "je boek sluiten", "fermer ton livre", "close your book"), g4Option("stand", "rechtstaan", "te lever", "stand up")),
        g4Scenario("colour", "Welke Franse kleur betekent 'groen'?", "Quel mot français signifie « vert » ?", "Which French color word means 'green'?", "green", g4Option("green", "vert", "vert", "vert"), g4Option("red", "rouge", "rouge", "rouge"), g4Option("blue", "bleu", "bleu", "bleu")),
        g4Scenario("number", "Welk getal is 'douze'?", "Quel nombre est « douze » ?", "Which number is 'douze'?", "twelve", g4Option("twelve", "12", "12", "12"), g4Option("two", "2", "2", "2"), g4Option("twenty", "20", "20", "20")),
        g4Scenario("family", "Wat betekent het Engelse woord 'sister'?", "Que signifie le mot anglais « sister » ?", "What does the English word 'sister' mean?", "sister", g4Option("sister", "zus", "sœur", "sister"), g4Option("brother", "broer", "frère", "brother"), g4Option("mother", "moeder", "mère", "mother")),
        g4Scenario("likes", "Welke Engelse zin betekent 'Ik hou van muziek'?", "Quelle phrase anglaise signifie « J'aime la musique » ?", "Which English sentence means 'I like music'?", "music", g4Option("music", "I like music.", "I like music.", "I like music."), g4Option("book", "I have a book.", "I have a book.", "I have a book."), g4Option("name", "My name is Music.", "My name is Music.", "My name is Music.")),
        g4Scenario("question_word", "Welk Engels vraagwoord gebruik je voor een plaats?", "Quel mot interrogatif anglais utilise-t-on pour un lieu ?", "Which English question word asks about a place?", "where", g4Option("where", "where", "where", "where"), g4Option("when", "when", "when", "when"), g4Option("who", "who", "who", "who")),
        g4Scenario("farewell", "Welke Engelse uitdrukking gebruik je om afscheid te nemen?", "Quelle expression anglaise utilise-t-on pour prendre congé ?", "Which English expression is used to say goodbye?", "goodbye", g4Option("goodbye", "See you later.", "See you later.", "See you later."), g4Option("thanks", "Thank you.", "Thank you.", "Thank you."), g4Option("morning", "Good morning.", "Good morning.", "Good morning."))
    )
)
