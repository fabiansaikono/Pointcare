package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

internal data class Grade6Choice(val key: String, val nl: String, val fr: String, val en: String)

internal data class Grade6Scenario(
    val kind: String,
    val visiblePrompt: LocalizedText,
    val speechPrompt: LocalizedText,
    val answerKey: String,
    val options: List<Grade6Choice>,
)

private fun g6Text(nl: String, fr: String, en: String) = LocalizedText(nl = nl, fr = fr, en = en)
private fun g6Option(key: String, nl: String, fr: String, en: String) = Grade6Choice(key, nl, fr, en)
private fun g6SameOption(key: String, value: String) = Grade6Choice(key, value, value, value)

private fun g6Scenario(
    kind: String,
    nl: String,
    fr: String,
    en: String,
    answerKey: String,
    answer: Grade6Choice,
    distractor1: Grade6Choice,
    distractor2: Grade6Choice,
) = Grade6Scenario(
    kind = kind,
    visiblePrompt = g6Text(nl, fr, en),
    speechPrompt = g6Text(nl, fr, en),
    answerKey = answerKey,
    options = listOf(answer, distractor1, distractor2),
)

private fun g6FrenchScenario(
    kind: String,
    visibleNl: String,
    visibleFr: String,
    visibleEn: String,
    frenchSpeech: String,
    answerKey: String,
    answer: Grade6Choice,
    distractor1: Grade6Choice,
    distractor2: Grade6Choice,
) = Grade6Scenario(
    kind = kind,
    visiblePrompt = g6Text(visibleNl, visibleFr, visibleEn),
    speechPrompt = g6Text(frenchSpeech, frenchSpeech, frenchSpeech),
    answerKey = answerKey,
    options = listOf(answer, distractor1, distractor2),
)

internal abstract class Grade6ChoiceGenerator(
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
    private val scenarios: List<Grade6Scenario>,
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
        if (parameters["grade"] != "6") add("grade must be 6")
        if (parameters["roadmap_entry"] != roadmapEntry) add("roadmap_entry must be $roadmapEntry")
        if (parameters["phase"] !in setOf("guided", "checkpoint")) add("phase must be guided or checkpoint")
        if (parameters["editorial_status"] != LessonGeneratorRegistry.APPROVED_STATUS) {
            add("editorial_status must be approved")
        }
    }

    final override fun generate(
        seed: LessonGenerationSeed,
        exerciseCount: Int,
        parameters: Map<String, String>,
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
                level = SkillLevel.SIXTH_GRADE,
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
                    "hint_en-US" to hint.en.orEmpty(),
                ).apply { putAll(extraData) },
                answer = scenario.answerKey,
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

internal object Grade6CriticalReadingArgumentsGenerator : Grade6ChoiceGenerator(
    generatorId = "generator.g6.language.critical_reading.arguments",
    roadmapEntry = "roadmap.g6.language.critical_reading.arguments",
    idNamespace = "g6.language.critical_reading.arguments",
    skill = "critical_reading_arguments",
    domain = LearningDomain.READING,
    automatedScope = "critical_reading_evidence_argument_and_source_reasoning",
    adultSupportKey = "requiresAdultCriticalDiscussion",
    hint = g6Text(
        "Controleer bewering, bewijs, bron en mogelijke tegenargumenten.",
        "Vérifie l'affirmation, les preuves, la source et les contre-arguments possibles.",
        "Check the claim, evidence, source, and possible counterarguments.",
    ),
    extraData = mapOf(
        "independentExtendedReadingMastered" to "false",
        "openArgumentationMastered" to "false",
    ),
    scenarios = listOf(
        g6Scenario("claim", "Welke zin is een bewering die nog bewijs nodig heeft?", "Quelle phrase est une affirmation qui doit encore être prouvée ?", "Which sentence is a claim that still needs evidence?", "claim", g6Option("claim", "Later beginnen verbetert de leerprestaties van elke leerling.", "Commencer plus tard améliore les résultats de chaque élève.", "Starting later improves every student's results."), g6Option("date", "De proef begon op maandag.", "L'expérience a commencé lundi.", "The trial began on Monday."), g6Option("count", "Er namen 120 leerlingen deel.", "Cent vingt élèves ont participé.", "One hundred and twenty students took part.")),
        g6Scenario("evidence", "Welk bewijs ondersteunt de bewering dat een nieuwe leesaanpak werkt het best?", "Quelle preuve soutient le mieux l'affirmation qu'une nouvelle méthode de lecture fonctionne ?", "Which evidence best supports the claim that a new reading approach works?", "comparison", g6Option("comparison", "Voor- en nametingen tonen bij meerdere klassen een duidelijke vooruitgang.", "Des mesures avant et après montrent un progrès net dans plusieurs classes.", "Before-and-after measures show clear progress across several classes."), g6Option("colour", "De werkbladen zijn groen.", "Les fiches sont vertes.", "The worksheets are green."), g6Option("opinion", "Eén leerling vindt de aanpak leuk.", "Un élève trouve la méthode agréable.", "One pupil likes the approach.")),
        g6Scenario("fact_opinion", "Welke zin is een mening?", "Quelle phrase est une opinion ?", "Which sentence is an opinion?", "opinion", g6Option("opinion", "Dit is de meest boeiende tentoonstelling van het jaar.", "C'est l'exposition la plus passionnante de l'année.", "This is the most exciting exhibition of the year."), g6Option("hours", "De tentoonstelling is zes uur per dag open.", "L'exposition est ouverte six heures par jour.", "The exhibition is open six hours a day."), g6Option("rooms", "Ze gebruikt drie zalen.", "Elle occupe trois salles.", "It uses three rooms.")),
        g6Scenario("counterargument", "Een tekst pleit voor meer fietsenstallingen. Wat is een relevant tegenargument?", "Un texte demande davantage de parkings à vélos. Quel est un contre-argument pertinent ?", "A text argues for more bicycle parking. What is a relevant counterargument?", "space", g6Option("space", "De voorgestelde plaats moet ook toegankelijk blijven voor hulpdiensten.", "L'emplacement proposé doit rester accessible aux secours.", "The proposed area must also remain accessible to emergency services."), g6Option("weather", "Gisteren regende het.", "Il a plu hier.", "It rained yesterday."), g6Option("colour", "Sommige fietsen zijn rood.", "Certains vélos sont rouges.", "Some bicycles are red.")),
        g6Scenario("bias", "Een fabrikant vergelijkt alleen zijn beste product met het goedkoopste product van een concurrent. Wat valt op?", "Un fabricant compare seulement son meilleur produit au produit le moins cher d'un concurrent. Que remarques-tu ?", "A manufacturer compares only its best product with a competitor's cheapest product. What stands out?", "selective", g6Option("selective", "De vergelijking is selectief en mogelijk gekleurd.", "La comparaison est sélective et peut être orientée.", "The comparison is selective and may be biased."), g6Option("complete", "Alle mogelijke producten zijn eerlijk vergeleken.", "Tous les produits possibles sont comparés équitablement.", "Every possible product is compared fairly."), g6Option("fiction", "De tekst is daardoor een sprookje.", "Le texte devient donc un conte.", "That makes the text a fairy tale.")),
        g6Scenario("source_reliability", "Welke bron is het meest geschikt om actuele bevolkingscijfers te controleren?", "Quelle source convient le mieux pour vérifier des chiffres de population récents ?", "Which source is most suitable for checking current population figures?", "official", g6Option("official", "een recente publicatie van de officiële statistiekdienst", "une publication récente du service officiel de statistique", "a recent publication from the official statistics service"), g6Option("anonymous", "een anonieme reactie zonder bron", "un commentaire anonyme sans source", "an anonymous comment without a source"), g6Option("old_ad", "een oude reclamefolder", "une ancienne brochure publicitaire", "an old advertising leaflet")),
        g6Scenario("inference", "Een auteur noemt drie voordelen maar laat kosten en risico's weg. Wat kun je voorzichtig afleiden?", "Un auteur cite trois avantages mais omet les coûts et les risques. Que peux-tu déduire avec prudence ?", "An author lists three benefits but leaves out costs and risks. What can you cautiously infer?", "incomplete", g6Option("incomplete", "De voorstelling kan onvolledig zijn.", "La présentation peut être incomplète.", "The presentation may be incomplete."), g6Option("false", "Elk genoemd voordeel is zeker onwaar.", "Chaque avantage cité est forcément faux.", "Every stated benefit must be false."), g6Option("perfect", "Het voorstel heeft geen enkel nadeel.", "La proposition n'a aucun inconvénient.", "The proposal has no disadvantages.")),
        g6Scenario("summary", "Welke samenvatting bewaart zowel standpunt als belangrijkste reden?", "Quel résumé conserve à la fois le point de vue et la raison principale ?", "Which summary preserves both the viewpoint and the main reason?", "balanced", g6Option("balanced", "De auteur steunt deelauto's omdat ze ruimte kunnen besparen, mits ze goed bereikbaar zijn.", "L'auteur soutient l'autopartage parce qu'il peut économiser de l'espace, à condition qu'il soit accessible.", "The author supports car sharing because it can save space, provided it is accessible."), g6Option("short", "De tekst gaat over auto's.", "Le texte parle de voitures.", "The text is about cars."), g6Option("absolute", "Iedereen moet onmiddellijk zijn auto wegdoen.", "Tout le monde doit abandonner sa voiture immédiatement.", "Everyone must give up their car immediately.")),
        g6Scenario("correlation", "Twee waarden stijgen tegelijk. Welke conclusie is verantwoord?", "Deux valeurs augmentent en même temps. Quelle conclusion est justifiée ?", "Two values rise at the same time. Which conclusion is justified?", "investigate", g6Option("investigate", "Er is een verband zichtbaar, maar oorzaak en andere factoren moeten onderzocht worden.", "Un lien est visible, mais la cause et d'autres facteurs doivent être étudiés.", "A relationship is visible, but causation and other factors need investigation."), g6Option("cause", "De ene waarde veroorzaakt zeker altijd de andere.", "Une valeur cause forcément toujours l'autre.", "One value definitely always causes the other."), g6Option("none", "De gegevens kunnen niets betekenen.", "Les données ne peuvent rien signifier.", "The data cannot mean anything.")),
        g6Scenario("argument_structure", "Welke volgorde maakt een argument het duidelijkst?", "Quel ordre rend un argument le plus clair ?", "Which order makes an argument clearest?", "structure", g6Option("structure", "standpunt – reden – bewijs – conclusie", "point de vue – raison – preuve – conclusion", "position – reason – evidence – conclusion"), g6Option("random", "bewijs – nieuw onderwerp – titel – grap", "preuve – nouveau sujet – titre – blague", "evidence – new topic – title – joke"), g6Option("repeat", "alleen hetzelfde standpunt herhalen", "répéter seulement le même point de vue", "only repeat the same position")),
    ),
)

internal object Grade6WritingStructuredGenresGenerator : Grade6ChoiceGenerator(
    generatorId = "generator.g6.language.writing.structured_genres",
    roadmapEntry = "roadmap.g6.language.writing.structured_genres",
    idNamespace = "g6.language.writing.structured_genres",
    skill = "writing_structured_genres",
    domain = LearningDomain.READING,
    automatedScope = "planning_structure_revision_and_genre_decisions",
    adultSupportKey = "requiresAdultWritingReview",
    hint = g6Text("Stem doel, structuur, toon en bronvermelding op de lezer af.", "Adapte le but, la structure, le ton et les sources au lecteur.", "Match purpose, structure, tone, and source credit to the reader."),
    extraData = mapOf("independentExtendedWritingMastered" to "false", "handwritingMastered" to "false"),
    scenarios = listOf(
        g6Scenario("genre_report", "Welke opening past bij een onderzoeksverslag?", "Quelle introduction convient à un rapport de recherche ?", "Which opening suits a research report?", "report", g6Option("report", "We onderzochten welke ondergrond het meeste regenwater doorlaat.", "Nous avons étudié quel sol laisse passer le plus d'eau de pluie.", "We investigated which surface lets through the most rainwater."), g6Option("story", "Er was eens een magische plas.", "Il était une fois une flaque magique.", "Once there was a magical puddle."), g6Option("invitation", "Kom zaterdag zeker naar ons feest!", "Venez absolument à notre fête samedi !", "Be sure to come to our party on Saturday!")),
        g6Scenario("argument_intro", "Welke inleiding kondigt een argumentatieve tekst duidelijk aan?", "Quelle introduction annonce clairement un texte argumentatif ?", "Which introduction clearly signals an argumentative text?", "position", g6Option("position", "Onze school heeft meer schaduwplekken nodig; hieronder geef ik drie redenen.", "Notre école a besoin de plus d'ombre ; voici trois raisons.", "Our school needs more shaded areas; here are three reasons."), g6Option("weather", "Gisteren was het twaalf graden.", "Hier, il faisait douze degrés.", "Yesterday it was twelve degrees."), g6Option("ending", "Tot slot bedank ik iedereen.", "Enfin, je remercie tout le monde.", "Finally, I thank everyone.")),
        g6Scenario("paragraph", "Welke alinea heeft één duidelijke kernzin met ondersteuning?", "Quel paragraphe contient une idée principale claire et des détails qui la soutiennent ?", "Which paragraph has one clear topic sentence with supporting details?", "coherent", g6Option("coherent", "Bomen verkoelen de speelplaats. Hun bladeren geven schaduw en verdamping verlaagt de temperatuur.", "Les arbres rafraîchissent la cour. Leurs feuilles donnent de l'ombre et l'évaporation réduit la température.", "Trees cool the playground. Their leaves provide shade and evaporation lowers the temperature."), g6Option("mixed", "Bomen zijn hoog. Mijn fiets is blauw. Morgen eten we soep.", "Les arbres sont hauts. Mon vélo est bleu. Demain, nous mangeons de la soupe.", "Trees are tall. My bicycle is blue. Tomorrow we eat soup."), g6Option("repeat", "Bomen helpen. Bomen helpen. Bomen helpen.", "Les arbres aident. Les arbres aident. Les arbres aident.", "Trees help. Trees help. Trees help.")),
        g6Scenario("linking_word", "Welk verbindingswoord geeft een tegenstelling aan?", "Quel connecteur exprime une opposition ?", "Which linking word shows contrast?", "however", g6Option("however", "echter", "cependant", "however"), g6Option("because", "omdat", "parce que", "because"), g6Option("first", "eerst", "d'abord", "first")),
        g6Scenario("formal_tone", "Welke zin past in een formele e-mail aan de gemeente?", "Quelle phrase convient à un courriel formel adressé à la commune ?", "Which sentence suits a formal email to the municipality?", "formal", g6Option("formal", "Kunt u toelichten wanneer het zebrapad wordt hersteld?", "Pourriez-vous préciser quand le passage piéton sera réparé ?", "Could you explain when the pedestrian crossing will be repaired?"), g6Option("slang", "Hé, fix dat zebrapad eens.", "Hé, réparez donc ce passage.", "Hey, fix that crossing."), g6Option("rude", "U doet dit altijd fout!", "Vous faites toujours tout de travers !", "You always do this wrong!")),
        g6Scenario("revision_logic", "Welke revisie maakt de redenering sterker?", "Quelle révision renforce le raisonnement ?", "Which revision strengthens the reasoning?", "evidence", g6Option("evidence", "na elke reden een relevante bron of waarneming toevoegen", "ajouter une source ou une observation pertinente après chaque raison", "add a relevant source or observation after each reason"), g6Option("adjectives", "meer uitroeptekens toevoegen", "ajouter davantage de points d'exclamation", "add more exclamation marks"), g6Option("font", "elk woord een ander lettertype geven", "donner une police différente à chaque mot", "use a different font for every word")),
        g6Scenario("source_credit", "Hoe verwerk je informatie uit een bron correct?", "Comment intégrer correctement une information provenant d'une source ?", "How should you use information from a source correctly?", "credit", g6Option("credit", "in eigen woorden schrijven en auteur, titel en datum vermelden", "reformuler et mentionner l'auteur, le titre et la date", "paraphrase it and credit the author, title, and date"), g6Option("copy", "zonder vermelding letterlijk kopiëren", "copier mot pour mot sans référence", "copy it word for word without credit"), g6Option("hide", "de bron bewust verbergen", "cacher volontairement la source", "deliberately hide the source")),
        g6Scenario("conclusion", "Welke conclusie past bij een vergelijkend verslag?", "Quelle conclusion convient à un rapport comparatif ?", "Which conclusion suits a comparative report?", "result", g6Option("result", "Materiaal A is lichter, maar materiaal B is sterker; de beste keuze hangt dus af van het doel.", "Le matériau A est plus léger, mais le matériau B est plus solide ; le meilleur choix dépend donc du but.", "Material A is lighter, but material B is stronger; the best choice therefore depends on the purpose."), g6Option("newtopic", "Overigens bestaan er veel soorten vogels.", "Par ailleurs, il existe beaucoup d'espèces d'oiseaux.", "By the way, there are many kinds of birds."), g6Option("unsupported", "Materiaal A is altijd in alles het beste.", "Le matériau A est toujours le meilleur pour tout.", "Material A is always best for everything.")),
        g6Scenario("editing", "Welke correctie verbetert de verwijswoorden?", "Quelle correction améliore les mots de reprise ?", "Which correction improves the reference words?", "specific", g6Option("specific", "vervang een onduidelijk 'dit' door de maatregel waarnaar het verwijst", "remplacer un « cela » ambigu par la mesure concernée", "replace an unclear 'this' with the measure it refers to"), g6Option("delete", "verwijder alle zelfstandige naamwoorden", "supprimer tous les noms", "remove every noun"), g6Option("random", "wissel willekeurig hij en zij af", "alterner il et elle au hasard", "alternate he and she randomly")),
        g6Scenario("checklist", "Wat hoort in een laatste schrijfcontrole?", "Que faut-il vérifier lors de la dernière relecture ?", "What belongs in a final writing check?", "complete", g6Option("complete", "doel, opbouw, samenhang, spelling en bronvermelding", "but, structure, cohérence, orthographe et sources", "purpose, structure, cohesion, spelling, and source credit"), g6Option("length", "alleen het aantal woorden", "uniquement le nombre de mots", "only the word count"), g6Option("colour", "alleen de kleur van de titel", "uniquement la couleur du titre", "only the title colour")),
    ),
)

internal object Grade6SpeakingPresentationArgumentsGenerator : Grade6ChoiceGenerator(
    generatorId = "generator.g6.language.speaking.presentation_arguments",
    roadmapEntry = "roadmap.g6.language.speaking.presentation_arguments",
    idNamespace = "g6.language.speaking.presentation_arguments",
    skill = "speaking_presentation_arguments",
    domain = LearningDomain.READING,
    automatedScope = "presentation_planning_listening_and_argument_choice",
    adultSupportKey = "requiresAdultOralObservation",
    hint = g6Text("Bouw je boodschap op, luister actief en reageer met redenen.", "Structure ton message, écoute activement et réponds avec des raisons.", "Structure your message, listen actively, and respond with reasons."),
    extraData = mapOf("spontaneousSpeechMastered" to "false", "presentationCompleted" to "false", "discussionMastered" to "false"),
    scenarios = listOf(
        g6Scenario("opening", "Welke opening maakt doel en onderwerp van een presentatie duidelijk?", "Quelle ouverture clarifie le but et le sujet d'une présentation ?", "Which opening clearly states a presentation's purpose and topic?", "clear", g6Option("clear", "Vandaag leg ik uit hoe onze school afval kan verminderen.", "Aujourd'hui, j'explique comment notre école peut réduire ses déchets.", "Today I will explain how our school can reduce waste."), g6Option("vague", "Ik ga iets vertellen.", "Je vais raconter quelque chose.", "I am going to say something."), g6Option("ending", "Bedankt voor het luisteren.", "Merci de votre écoute.", "Thank you for listening.")),
        g6Scenario("structure", "Welke volgorde past bij een overtuigende korte presentatie?", "Quel ordre convient à une courte présentation persuasive ?", "Which order suits a short persuasive presentation?", "order", g6Option("order", "standpunt – redenen met bewijs – besluit", "point de vue – raisons et preuves – conclusion", "position – reasons with evidence – conclusion"), g6Option("random", "besluit – grap – nieuw onderwerp", "conclusion – blague – nouveau sujet", "conclusion – joke – new topic"), g6Option("only", "dezelfde zin driemaal", "la même phrase trois fois", "the same sentence three times")),
        g6Scenario("evidence", "Welke ondersteuning maakt een mondeling argument sterker?", "Quel élément renforce un argument oral ?", "What makes an oral argument stronger?", "evidence", g6Option("evidence", "een controleerbaar cijfer uit een betrouwbare bron", "un chiffre vérifiable provenant d'une source fiable", "a verifiable figure from a reliable source"), g6Option("volume", "alleen luider spreken", "parler seulement plus fort", "only speak louder"), g6Option("insult", "iemand persoonlijk aanvallen", "attaquer quelqu'un personnellement", "attack someone personally")),
        g6Scenario("active_listening", "Wat toont actief luisteren tijdens een discussie?", "Qu'est-ce qui montre une écoute active pendant une discussion ?", "What shows active listening during a discussion?", "summarise", g6Option("summarise", "de kern van de vorige spreker samenvatten voor je reageert", "résumer l'idée principale de l'orateur précédent avant de répondre", "summarise the previous speaker's main point before responding"), g6Option("interrupt", "voortdurend onderbreken", "interrompre sans cesse", "interrupt constantly"), g6Option("ignore", "je antwoord voorbereiden zonder te luisteren", "préparer sa réponse sans écouter", "prepare your reply without listening")),
        g6Scenario("clarification", "Welke vraag vraagt beleefd om verduidelijking?", "Quelle question demande poliment une clarification ?", "Which question politely asks for clarification?", "clarify", g6Option("clarify", "Kun je uitleggen welk voorbeeld je bedoelt?", "Peux-tu expliquer quel exemple tu veux dire ?", "Could you explain which example you mean?"), g6Option("dismiss", "Dat slaat nergens op.", "Cela n'a aucun sens.", "That makes no sense."), g6Option("change", "Wat eten we straks?", "Qu'allons-nous manger ?", "What are we eating later?")),
        g6Scenario("counterpoint", "Hoe reageer je inhoudelijk op een tegenargument?", "Comment répondre sur le fond à un contre-argument ?", "How should you respond substantively to a counterargument?", "acknowledge", g6Option("acknowledge", "Dat risico bestaat; daarom stel ik deze extra veiligheidsmaatregel voor.", "Ce risque existe ; c'est pourquoi je propose cette mesure de sécurité supplémentaire.", "That risk exists; that is why I propose this extra safety measure."), g6Option("deny", "Jij hebt gewoon ongelijk.", "Tu as simplement tort.", "You are simply wrong."), g6Option("repeat", "het eigen standpunt harder herhalen", "répéter son point de vue plus fort", "repeat your position more loudly")),
        g6Scenario("visual_aid", "Wanneer helpt een grafiek in een presentatie?", "Quand un graphique aide-t-il dans une présentation ?", "When does a graph help in a presentation?", "explain", g6Option("explain", "als hij de besproken gegevens duidelijker maakt en wordt toegelicht", "s'il clarifie les données présentées et qu'il est expliqué", "when it clarifies the data being discussed and is explained"), g6Option("decorate", "als hij alleen als versiering dient", "s'il sert uniquement de décoration", "when it is only decoration"), g6Option("tiny", "als niemand de labels kan lezen", "si personne ne peut lire les étiquettes", "when nobody can read the labels")),
        g6Scenario("audience", "Je spreekt voor jongere leerlingen. Wat pas je aan?", "Tu parles à des élèves plus jeunes. Qu'adaptes-tu ?", "You are speaking to younger pupils. What should you adapt?", "language", g6Option("language", "duidelijke woorden, korte uitleg en herkenbare voorbeelden", "des mots clairs, une explication courte et des exemples familiers", "clear words, short explanations, and familiar examples"), g6Option("jargon", "meer vakjargon zonder uitleg", "davantage de jargon sans explication", "more jargon without explanation"), g6Option("speed", "zo snel mogelijk spreken", "parler le plus vite possible", "speak as quickly as possible")),
        g6Scenario("closing", "Welke afsluiting verbindt besluit en oproep?", "Quelle conclusion relie le bilan et l'appel à l'action ?", "Which ending links the conclusion and a call to action?", "closing", g6Option("closing", "Omdat hergebruik afval vermindert, stel ik voor dat elke klas deze week één herbruikbaar alternatief kiest.", "Comme la réutilisation réduit les déchets, je propose que chaque classe choisisse cette semaine une solution réutilisable.", "Because reuse reduces waste, I suggest each class choose one reusable alternative this week."), g6Option("newtopic", "Nu vertel ik over mijn huisdier.", "Maintenant, je parle de mon animal.", "Now I will talk about my pet."), g6Option("stop", "Dat was het.", "C'est tout.", "That's it.")),
        g6Scenario("feedback", "Welke feedback helpt een spreker concreet vooruit?", "Quel retour aide concrètement un orateur à progresser ?", "Which feedback helps a speaker improve concretely?", "specific", g6Option("specific", "Je begreep je standpunt; spreek bij de grafiek iets trager en benoem de assen.", "J'ai compris ton point de vue ; parle un peu plus lentement au graphique et nomme les axes.", "I understood your position; slow down at the graph and name the axes."), g6Option("empty", "Goed.", "Bien.", "Good."), g6Option("personal", "Ik vind je stem vervelend.", "Je trouve ta voix agaçante.", "I find your voice annoying.")),
    ),
)

internal object Grade6FrenchFunctionalSkillsGenerator : Grade6ChoiceGenerator(
    generatorId = "generator.g6.language.french.functional_skills",
    roadmapEntry = "roadmap.g6.language.french.functional_skills",
    idNamespace = "g6.language.french.functional_skills",
    skill = "french_functional_skills",
    domain = LearningDomain.READING,
    automatedScope = "functional_french_listening_reading_and_response_selection",
    adultSupportKey = "requiresAdultFrenchConversation",
    inputMode = InputMode.LISTEN_AND_SELECT,
    hint = g6Text("Luister naar de Franse kernwoorden en kies een passende reactie.", "Écoute les mots-clés et choisis une réponse adaptée.", "Listen for the key French words and choose a suitable reply."),
    extraData = mapOf(
        "audioRequired" to "true",
        "audioTargetLocale" to "fr-BE",
        "targetLanguage" to "fr-BE",
        "spontaneousFrenchSpeechMastered" to "false",
        "realConversationCompleted" to "false",
    ),
    scenarios = listOf(
        g6FrenchScenario("directions", "Luister naar het Frans. Waar moet je heen?", "Écoute. Où faut-il aller ?", "Listen to the French. Where should you go?", "Tourne à gauche après la bibliothèque.", "left", g6SameOption("left", "À gauche après la bibliothèque."), g6SameOption("right", "À droite avant la gare."), g6SameOption("straight", "Tout droit jusqu'au parc.")),
        g6FrenchScenario("appointment", "Welke tijd wordt genoemd?", "Quelle heure est mentionnée ?", "Which time is mentioned?", "Le rendez-vous est à quatorze heures trente.", "time", g6SameOption("time", "À quatorze heures trente."), g6SameOption("early", "À treize heures."), g6SameOption("late", "À seize heures trente.")),
        g6FrenchScenario("shopping", "Welke reactie past bij de vraag?", "Quelle réponse convient à la question ?", "Which reply fits the question?", "Combien coûte ce cahier ?", "price", g6SameOption("price", "Il coûte trois euros cinquante."), g6SameOption("colour", "Il est bleu."), g6SameOption("place", "Il est sur la table.")),
        g6FrenchScenario("preference", "Welke reactie drukt een voorkeur met een reden uit?", "Quelle réponse exprime une préférence avec une raison ?", "Which reply expresses a preference with a reason?", "Quel sport préfères-tu et pourquoi ?", "prefer", g6SameOption("prefer", "Je préfère nager parce que j'aime l'eau."), g6SameOption("age", "J'ai douze ans."), g6SameOption("weather", "Il pleut aujourd'hui.")),
        g6FrenchScenario("invitation", "Welke reactie aanvaardt de uitnodiging beleefd?", "Quelle réponse accepte poliment l'invitation ?", "Which reply politely accepts the invitation?", "Veux-tu venir à mon anniversaire samedi ?", "accept", g6SameOption("accept", "Oui, merci pour l'invitation !"), g6SameOption("unrelated", "Je prends le bus à huit heures."), g6SameOption("question", "Où est la boulangerie ?")),
        g6FrenchScenario("problem", "Welke hulpvraag past?", "Quelle demande d'aide convient ?", "Which request for help fits?", "Tu ne comprends pas l'exercice.", "help", g6SameOption("help", "Pouvez-vous expliquer la consigne, s'il vous plaît ?"), g6SameOption("food", "Je voudrais un sandwich."), g6SameOption("leave", "Au revoir et à demain.")),
        g6FrenchScenario("travel", "Welk vervoermiddel neemt de spreker?", "Quel moyen de transport la personne prend-elle ?", "Which transport does the speaker take?", "Pour aller à Bruxelles, je prends le train.", "train", g6SameOption("train", "Le train."), g6SameOption("bike", "Le vélo."), g6SameOption("walk", "À pied.")),
        g6FrenchScenario("past_event", "Wat deed de spreker gisteren?", "Qu'a fait la personne hier ?", "What did the speaker do yesterday?", "Hier, j'ai visité le musée avec ma classe.", "museum", g6SameOption("museum", "Elle a visité le musée."), g6SameOption("school", "Elle est restée à l'école."), g6SameOption("sport", "Elle a joué au football.")),
        g6FrenchScenario("future_plan", "Wat is het plan voor morgen?", "Quel est le projet pour demain ?", "What is the plan for tomorrow?", "Demain, nous allons préparer une affiche.", "poster", g6SameOption("poster", "Préparer une affiche."), g6SameOption("film", "Regarder un film."), g6SameOption("exam", "Passer un examen.")),
        g6FrenchScenario("message", "Wat is de kern van het bericht?", "Quel est le message principal ?", "What is the main message?", "Attention : la piscine est fermée ce matin, mais elle ouvre à quatorze heures.", "afternoon", g6SameOption("afternoon", "La piscine ouvre à quatorze heures."), g6SameOption("never", "La piscine reste fermée toute la semaine."), g6SameOption("morning", "La piscine ouvre ce matin.")),
    ),
)

internal object Grade6FrenchGrammarVocabularyGenerator : Grade6ChoiceGenerator(
    generatorId = "generator.g6.spelling.french.grammar_vocabulary",
    roadmapEntry = "roadmap.g6.spelling.french.grammar_vocabulary",
    idNamespace = "g6.spelling.french.grammar_vocabulary",
    skill = "french_grammar_vocabulary",
    domain = LearningDomain.WRITING,
    automatedScope = "reviewed_french_grammar_spelling_and_vocabulary_recognition",
    adultSupportKey = "requiresAdultFrenchReview",
    hint = g6Text("Controleer onderwerp, werkwoord, lidwoord en betekenis.", "Vérifie le sujet, le verbe, l'article et le sens.", "Check the subject, verb, article, and meaning."),
    extraData = mapOf("targetLanguage" to "fr-BE", "openFrenchWritingMastered" to "false", "oralFrenchMastered" to "false"),
    scenarios = listOf(
        g6FrenchScenario("present_etre", "Kies de correcte Franse vorm.", "Choisis la forme correcte.", "Choose the correct French form.", "Nous ... prêts.", "sommes", g6SameOption("sommes", "sommes"), g6SameOption("etes", "êtes"), g6SameOption("sont", "sont")),
        g6FrenchScenario("present_avoir", "Kies de correcte Franse vorm.", "Choisis la forme correcte.", "Choose the correct French form.", "Ils ... un nouveau livre.", "ont", g6SameOption("ont", "ont"), g6SameOption("avez", "avez"), g6SameOption("avons", "avons")),
        g6FrenchScenario("regular_er", "Welk werkwoord past?", "Quel verbe convient ?", "Which verb fits?", "Vous ... français en classe.", "parlez", g6SameOption("parlez", "parlez"), g6SameOption("parlons", "parlons"), g6SameOption("parlent", "parlent")),
        g6FrenchScenario("article", "Welk lidwoord past?", "Quel article convient ?", "Which article fits?", "Je visite ... musée près de la gare.", "le", g6SameOption("le", "le"), g6SameOption("la", "la"), g6SameOption("les", "les")),
        g6FrenchScenario("adjective_agreement", "Welke vorm past bij het zelfstandig naamwoord?", "Quelle forme s'accorde avec le nom ?", "Which form agrees with the noun?", "une histoire ...", "interesting_f", g6SameOption("interesting_f", "intéressante"), g6SameOption("interesting_m", "intéressant"), g6SameOption("interesting_p", "intéressants")),
        g6FrenchScenario("negation", "Welke zin is correct ontkennend?", "Quelle phrase est correctement négative ?", "Which sentence is correctly negative?", "Je comprends la question.", "negative", g6SameOption("negative", "Je ne comprends pas la question."), g6SameOption("missing", "Je comprends ne pas la question."), g6SameOption("double", "Je pas comprends la question.")),
        g6FrenchScenario("question", "Welke vraagvorm is correct?", "Quelle forme interrogative est correcte ?", "Which question form is correct?", "Vraag waar de bibliotheek is.", "where", g6SameOption("where", "Où est la bibliothèque ?"), g6SameOption("when", "Quand est la bibliothèque ?"), g6SameOption("why", "Pourquoi la bibliothèque ?")),
        g6FrenchScenario("preposition", "Welk voorzetsel past?", "Quelle préposition convient ?", "Which preposition fits?", "Nous allons ... Bruxelles.", "to", g6SameOption("to", "à"), g6SameOption("from", "de"), g6SameOption("with", "avec")),
        g6FrenchScenario("past", "Welke zin vertelt over gisteren?", "Quelle phrase parle d'hier ?", "Which sentence refers to yesterday?", "Hier ...", "visited", g6SameOption("visited", "j'ai visité le musée."), g6SameOption("visit", "je visite le musée."), g6SameOption("future", "je vais visiter le musée demain.")),
        g6FrenchScenario("vocabulary_context", "Welk woord past in de context?", "Quel mot convient dans ce contexte ?", "Which word fits the context?", "Il pleut. Je prends mon ...", "umbrella", g6SameOption("umbrella", "parapluie"), g6SameOption("pencil", "crayon"), g6SameOption("plate", "assiette")),
    ),
)

internal object Grade6IntegratedSourcesModelsGenerator : Grade6ChoiceGenerator(
    generatorId = "generator.g6.world.integrated.sources_models",
    roadmapEntry = "roadmap.g6.world.integrated.sources_models",
    idNamespace = "g6.world.integrated.sources_models",
    skill = "integrated_sources_models",
    domain = LearningDomain.WORLD_KNOWLEDGE,
    automatedScope = "source_model_system_and_inquiry_reasoning",
    adultSupportKey = "requiresAdultInquiry",
    hint = g6Text("Controleer bron, schaal, variabelen en grenzen van het model.", "Vérifie la source, l'échelle, les variables et les limites du modèle.", "Check the source, scale, variables, and limits of the model."),
    extraData = mapOf("realInquiryCompleted" to "false", "fieldworkMastered" to "false", "experimentCompleted" to "false"),
    scenarios = listOf(
        g6Scenario("primary_source", "Welke bron is een primaire bron voor onderzoek naar een staking uit 1960?", "Quelle source est primaire pour étudier une grève de 1960 ?", "Which is a primary source for researching a strike in 1960?", "letter", g6Option("letter", "een brief die een deelnemer tijdens de staking schreef", "une lettre écrite par un participant pendant la grève", "a letter written by a participant during the strike"), g6Option("textbook", "een recent handboek", "un manuel récent", "a recent textbook"), g6Option("video", "een hedendaagse fictiefilm", "un film de fiction actuel", "a modern fictional film")),
        g6Scenario("source_context", "Waarom noteer je maker, datum en doel van een bron?", "Pourquoi noter l'auteur, la date et le but d'une source ?", "Why record a source's creator, date, and purpose?", "evaluate", g6Option("evaluate", "om perspectief en betrouwbaarheid beter te beoordelen", "pour mieux évaluer le point de vue et la fiabilité", "to better evaluate perspective and reliability"), g6Option("decorate", "om de bladzijde te versieren", "pour décorer la page", "to decorate the page"), g6Option("prove", "om te bewijzen dat elke bron waar is", "pour prouver que toute source est vraie", "to prove every source is true")),
        g6Scenario("map_model", "Wat is een belangrijke beperking van een wegenkaart?", "Quelle est une limite importante d'une carte routière ?", "What is an important limitation of a road map?", "selection", g6Option("selection", "ze selecteert informatie en toont niet elk detail van de werkelijkheid", "elle sélectionne des informations et ne montre pas chaque détail du réel", "it selects information and does not show every real-world detail"), g6Option("exact", "ze is de werkelijkheid op ware grootte", "elle est la réalité en grandeur nature", "it is reality at full size"), g6Option("future", "ze voorspelt altijd toekomstige files", "elle prédit toujours les embouteillages futurs", "it always predicts future traffic jams")),
        g6Scenario("system", "In een ecosysteem daalt het aantal insecten sterk. Welk gevolg is aannemelijk?", "Dans un écosystème, le nombre d'insectes diminue fortement. Quelle conséquence est plausible ?", "In an ecosystem, insect numbers fall sharply. Which consequence is plausible?", "food", g6Option("food", "insectenetende dieren vinden mogelijk minder voedsel", "les animaux insectivores peuvent trouver moins de nourriture", "insect-eating animals may find less food"), g6Option("none", "geen enkel ander organisme kan beïnvloed worden", "aucun autre organisme ne peut être affecté", "no other organism can be affected"), g6Option("instant", "alle planten verdwijnen onmiddellijk", "toutes les plantes disparaissent immédiatement", "all plants disappear immediately")),
        g6Scenario("fair_test", "Welke opstelling test eerlijk of licht de plantengroei beïnvloedt?", "Quel dispositif teste équitablement l'influence de la lumière sur la croissance ?", "Which setup fairly tests whether light affects plant growth?", "one_variable", g6Option("one_variable", "gelijke planten met dezelfde bodem en water, maar een verschillende hoeveelheid licht", "des plantes identiques avec le même sol et la même eau, mais une quantité de lumière différente", "matching plants with the same soil and water but different light"), g6Option("many", "verschillende planten, bodem, water en licht", "des plantes, sols, quantités d'eau et lumières différents", "different plants, soil, water, and light"), g6Option("no_measure", "één plant zonder metingen", "une seule plante sans mesure", "one plant with no measurements")),
        g6Scenario("model_prediction", "Een model voorspelt een temperatuurstijging. Wat doe je wetenschappelijk?", "Un modèle prévoit une hausse de température. Que fais-tu scientifiquement ?", "A model predicts a temperature rise. What should you do scientifically?", "compare", g6Option("compare", "aannames controleren en voorspellingen met nieuwe metingen vergelijken", "vérifier les hypothèses et comparer les prévisions à de nouvelles mesures", "check assumptions and compare predictions with new measurements"), g6Option("certain", "de voorspelling als absolute zekerheid behandelen", "traiter la prévision comme une certitude absolue", "treat the prediction as absolute certainty"), g6Option("ignore", "alle waarnemingen negeren", "ignorer toutes les observations", "ignore all observations")),
        g6Scenario("historical_cause", "Welke uitspraak over historische oorzaken is zorgvuldig?", "Quelle affirmation sur les causes historiques est rigoureuse ?", "Which statement about historical causes is careful?", "multiple", g6Option("multiple", "Grote veranderingen hebben vaak meerdere oorzaken die elkaar beïnvloeden.", "Les grands changements ont souvent plusieurs causes qui interagissent.", "Major changes often have several interacting causes."), g6Option("single", "Elke verandering heeft precies één oorzaak.", "Chaque changement a exactement une cause.", "Every change has exactly one cause."), g6Option("none", "Bronnen zijn niet nodig om oorzaken te onderzoeken.", "Les sources ne sont pas nécessaires pour étudier les causes.", "Sources are not needed to study causes.")),
        g6Scenario("geographic_scale", "Waarom kan een patroon op buurtniveau anders lijken dan op landniveau?", "Pourquoi un motif peut-il sembler différent à l'échelle d'un quartier et d'un pays ?", "Why can a pattern look different at neighbourhood and country scale?", "scale", g6Option("scale", "schaal bepaalt welke details en verschillen zichtbaar zijn", "l'échelle détermine les détails et différences visibles", "scale affects which details and differences are visible"), g6Option("wrong", "één van beide kaarten moet altijd fout zijn", "l'une des cartes doit toujours être fausse", "one map must always be wrong"), g6Option("colour", "alleen de kleur van de kaart telt", "seule la couleur de la carte compte", "only the map colour matters")),
        g6Scenario("evidence_conclusion", "Drie metingen geven uiteenlopende resultaten. Welke conclusie past?", "Trois mesures donnent des résultats différents. Quelle conclusion convient ?", "Three measurements give different results. Which conclusion fits?", "repeat", g6Option("repeat", "meer metingen doen en mogelijke meetfouten onderzoeken", "effectuer davantage de mesures et examiner les erreurs possibles", "take more measurements and investigate possible measurement errors"), g6Option("choose", "alleen het gewenste resultaat kiezen", "choisir uniquement le résultat souhaité", "choose only the desired result"), g6Option("average_without", "zonder controle één getal verzinnen", "inventer un chiffre sans contrôle", "invent a number without checking")),
        g6Scenario("sustainability_tradeoff", "Welke aanpak onderzoekt een duurzaamheidskeuze volledig?", "Quelle approche étudie complètement un choix de durabilité ?", "Which approach examines a sustainability choice fully?", "tradeoffs", g6Option("tradeoffs", "milieu-effect, kosten, gebruik en gevolgen op lange termijn vergelijken", "comparer l'impact environnemental, le coût, l'usage et les effets à long terme", "compare environmental impact, cost, use, and long-term effects"), g6Option("one", "alleen de aankoopprijs bekijken", "examiner uniquement le prix d'achat", "look only at purchase price"), g6Option("slogan", "een slogan als bewijs gebruiken", "utiliser un slogan comme preuve", "use a slogan as evidence")),
    ),
)

internal object Grade6DigitalCriticalSafeUseGenerator : Grade6ChoiceGenerator(
    generatorId = "generator.g6.world.digital.critical_safe_use",
    roadmapEntry = "roadmap.g6.world.digital.critical_safe_use",
    idNamespace = "g6.world.digital.critical_safe_use",
    skill = "digital_critical_safe_use",
    domain = LearningDomain.WORLD_KNOWLEDGE,
    automatedScope = "critical_safe_private_and_responsible_digital_decisions",
    adultSupportKey = "requiresAdultDigitalSupervision",
    hint = g6Text("Controleer bron, privacy, toestemming en mogelijke gevolgen.", "Vérifie la source, la vie privée, le consentement et les conséquences possibles.", "Check the source, privacy, consent, and possible consequences."),
    extraData = mapOf(
        "collectsPersonalData" to "false",
        "collectsCredentials" to "false",
        "collectsBrowsingData" to "false",
        "realAccountRequired" to "false",
        "independentOnlineSafetyMastered" to "false",
    ),
    scenarios = listOf(
        g6Scenario("source_check", "Een opvallend bericht noemt geen auteur of bron. Wat doe je eerst?", "Un message frappant ne cite ni auteur ni source. Que fais-tu d'abord ?", "A striking post names no author or source. What should you do first?", "verify", g6Option("verify", "de bewering vergelijken met meerdere betrouwbare bronnen", "comparer l'affirmation à plusieurs sources fiables", "compare the claim with several reliable sources"), g6Option("share", "het meteen doorsturen", "le partager immédiatement", "forward it immediately"), g6Option("believe", "het geloven omdat veel mensen reageren", "le croire parce que beaucoup de personnes réagissent", "believe it because many people comment")),
        g6Scenario("phishing", "Een bericht vraagt dringend om je wachtwoord via een link. Wat is veilig?", "Un message demande d'urgence ton mot de passe via un lien. Que faire en sécurité ?", "A message urgently asks for your password through a link. What is safe?", "stop", g6Option("stop", "niet klikken, niets invullen en een vertrouwde volwassene of beheerder waarschuwen", "ne pas cliquer, ne rien saisir et prévenir un adulte de confiance ou l'administrateur", "do not click or enter anything; alert a trusted adult or administrator"), g6Option("enter", "het wachtwoord invullen om tijd te winnen", "saisir le mot de passe pour gagner du temps", "enter the password to save time"), g6Option("reply", "het wachtwoord antwoorden in een bericht", "envoyer le mot de passe en réponse", "reply with the password")),
        g6Scenario("privacy", "Welke informatie deel je niet in een openbaar profiel?", "Quelle information ne faut-il pas partager sur un profil public ?", "Which information should not be shared on a public profile?", "private", g6Option("private", "adres, dagelijkse route en contactgegevens", "adresse, trajet quotidien et coordonnées", "address, daily route, and contact details"), g6Option("hobby", "een algemene hobby zonder locatie", "un loisir général sans localisation", "a general hobby without location"), g6Option("book", "de titel van een favoriet boek", "le titre d'un livre préféré", "the title of a favourite book")),
        g6Scenario("consent", "Je wilt een klasfoto online zetten. Wat moet eerst?", "Tu veux publier une photo de classe. Que faut-il faire d'abord ?", "You want to post a class photo online. What must happen first?", "permission", g6Option("permission", "toestemming en schoolafspraken controleren", "vérifier le consentement et les règles de l'école", "check consent and school rules"), g6Option("post", "plaatsen omdat jij de foto maakte", "la publier parce que tu as pris la photo", "post it because you took the photo"), g6Option("tag", "iedereen zonder overleg taggen", "identifier tout le monde sans demander", "tag everyone without asking")),
        g6Scenario("copyright", "Hoe gebruik je een online afbeelding verantwoord in een presentatie?", "Comment utiliser une image en ligne de manière responsable dans un exposé ?", "How should you responsibly use an online image in a presentation?", "licensed", g6Option("licensed", "een toegelaten afbeelding kiezen en maker en bron vermelden", "choisir une image autorisée et citer l'auteur et la source", "choose a permitted image and credit its creator and source"), g6Option("copy", "elke afbeelding kopiëren zonder bron", "copier n'importe quelle image sans source", "copy any image without a source"), g6Option("remove", "het watermerk wegknippen", "supprimer le filigrane", "crop out the watermark")),
        g6Scenario("algorithm", "Waarom zie je online vaak meer van hetzelfde?", "Pourquoi vois-tu souvent davantage de contenus similaires en ligne ?", "Why do you often see more of the same online?", "recommendation", g6Option("recommendation", "aanbevelingssystemen gebruiken eerdere keuzes en interacties", "les systèmes de recommandation utilisent les choix et interactions précédents", "recommendation systems use previous choices and interactions"), g6Option("truth", "omdat het daardoor altijd waar is", "parce que cela devient toujours vrai", "because that makes it always true"), g6Option("random", "omdat elk bericht volledig willekeurig verschijnt", "parce que chaque message apparaît au hasard", "because every post appears completely at random")),
        g6Scenario("cyberbullying", "Iemand wordt herhaaldelijk online vernederd. Wat is een veilige stap?", "Une personne est humiliée en ligne à plusieurs reprises. Quelle étape est sûre ?", "Repeated online humiliation is happening. What is a safe step?", "support", g6Option("support", "bewijs veilig bewaren, niet verder verspreiden, blokkeren/melden en een vertrouwde volwassene inschakelen", "conserver les preuves sans les diffuser, bloquer/signaler et prévenir un adulte de confiance", "save evidence without spreading it, block/report, and involve a trusted adult"), g6Option("retaliate", "publiek terugschelden", "insulter publiquement en retour", "insult them back publicly"), g6Option("alone", "alles geheimhouden en alleen oplossen", "tout garder secret et gérer seul", "keep it secret and handle it alone")),
        g6Scenario("ai_output", "Een digitaal hulpmiddel geeft een overtuigend antwoord. Wat blijft nodig?", "Un outil numérique donne une réponse convaincante. Que faut-il encore faire ?", "A digital tool gives a convincing answer. What is still needed?", "check", g6Option("check", "feiten, bronnen, vooroordelen en passend gebruik controleren", "vérifier les faits, les sources, les biais et l'usage approprié", "check facts, sources, bias, and appropriate use"), g6Option("trust", "het antwoord altijd letterlijk overnemen", "reprendre toujours la réponse mot pour mot", "always copy the answer word for word"), g6Option("personal", "persoonlijke gegevens toevoegen voor zekerheid", "ajouter des données personnelles pour être sûr", "add personal data for certainty")),
        g6Scenario("screen_balance", "Welke keuze ondersteunt gezond digitaal gebruik?", "Quel choix favorise un usage numérique sain ?", "Which choice supports healthy digital use?", "balance", g6Option("balance", "pauzes plannen, meldingen beperken en schermgebruik afwisselen met beweging en slaap", "planifier des pauses, limiter les notifications et alterner écran, mouvement et sommeil", "plan breaks, limit notifications, and balance screens with movement and sleep"), g6Option("night", "meldingen de hele nacht aanlaten", "laisser les notifications toute la nuit", "leave notifications on all night"), g6Option("endless", "zonder pauze doorgaan", "continuer sans pause", "continue without breaks")),
        g6Scenario("digital_footprint", "Wat betekent een digitale voetafdruk?", "Qu'est-ce qu'une empreinte numérique ?", "What is a digital footprint?", "trace", g6Option("trace", "sporen die online handelingen en gedeelde informatie kunnen achterlaten", "les traces que les actions et informations partagées peuvent laisser en ligne", "traces that online actions and shared information may leave"), g6Option("password", "alleen je wachtwoord", "uniquement ton mot de passe", "only your password"), g6Option("device", "alleen de vorm van je toestel", "uniquement la forme de ton appareil", "only the shape of your device")),
    ),
)

internal object Grade6SocialSelfRegulationGenerator : Grade6ChoiceGenerator(
    generatorId = "generator.g6.world.social_self_regulation",
    roadmapEntry = "roadmap.g6.world.social_self_regulation",
    idNamespace = "g6.world.social_self_regulation",
    skill = "social_self_regulation",
    domain = LearningDomain.WORLD_KNOWLEDGE,
    automatedScope = "planning_cooperation_emotion_conflict_and_help_seeking_choices",
    adultSupportKey = "requiresAdultSocialObservation",
    hint = g6Text("Kies een haalbare, respectvolle en veilige volgende stap.", "Choisis une prochaine étape réaliste, respectueuse et sûre.", "Choose a realistic, respectful, and safe next step."),
    extraData = mapOf("diagnosisMade" to "false", "healthScoreAssigned" to "false", "realSocialMasteryInferred" to "false"),
    scenarios = listOf(
        g6Scenario("planning", "Een groot werkstuk moet over twee weken klaar zijn. Wat helpt het best?", "Un grand travail doit être terminé dans deux semaines. Qu'est-ce qui aide le plus ?", "A large project is due in two weeks. What helps most?", "plan", g6Option("plan", "taken opdelen, tussendoelen plannen en voortgang controleren", "diviser les tâches, fixer des étapes et suivre les progrès", "break down tasks, schedule milestones, and check progress"), g6Option("delay", "alles tot de laatste avond uitstellen", "tout reporter au dernier soir", "leave everything until the final evening"), g6Option("guess", "zonder planning beginnen en hopen dat het lukt", "commencer sans plan et espérer", "start without a plan and hope")),
        g6Scenario("feedback", "Je krijgt kritische feedback op je werk. Wat is een constructieve reactie?", "Tu reçois un retour critique sur ton travail. Quelle réaction est constructive ?", "You receive critical feedback on your work. What is a constructive response?", "use", g6Option("use", "luisteren, om een concreet voorbeeld vragen en kiezen wat je verbetert", "écouter, demander un exemple concret et choisir quoi améliorer", "listen, ask for a concrete example, and choose what to improve"), g6Option("attack", "de ander persoonlijk aanvallen", "attaquer l'autre personnellement", "attack the person"), g6Option("ignore", "alle feedback zonder nadenken negeren", "ignorer tout retour sans réfléchir", "ignore all feedback without thinking")),
        g6Scenario("group_roles", "Een groepstaak loopt vast omdat niemand weet wie wat doet. Wat helpt?", "Un travail de groupe bloque parce que personne ne sait qui fait quoi. Qu'est-ce qui aide ?", "A group task stalls because nobody knows who does what. What helps?", "roles", g6Option("roles", "taken, rollen, deadlines en een gezamenlijk controlemoment afspreken", "convenir des tâches, rôles, délais et d'un moment de vérification commun", "agree tasks, roles, deadlines, and a shared check-in"), g6Option("leader", "één persoon alles laten doen", "laisser une personne tout faire", "have one person do everything"), g6Option("blame", "zoeken wie schuld heeft", "chercher un coupable", "look for someone to blame")),
        g6Scenario("conflict", "Twee leerlingen willen hetzelfde materiaal gebruiken. Wat is eerlijk?", "Deux élèves veulent utiliser le même matériel. Qu'est-ce qui est équitable ?", "Two pupils want the same material. What is fair?", "solution", g6Option("solution", "behoeften bespreken en een beurtrol of bruikbaar alternatief afspreken", "discuter des besoins et convenir d'un tour de rôle ou d'une solution de remplacement", "discuss needs and agree turns or a workable alternative"), g6Option("strongest", "de sterkste beslist", "le plus fort décide", "the strongest decides"), g6Option("hide", "het materiaal verstoppen", "cacher le matériel", "hide the material")),
        g6Scenario("emotion_pause", "Je merkt dat je erg boos wordt tijdens een discussie. Wat is een verstandige stap?", "Tu remarques que tu te mets très en colère pendant une discussion. Quelle étape est raisonnable ?", "You notice you are becoming very angry during a discussion. What is a sensible step?", "pause", g6Option("pause", "een korte pauze vragen, rustig worden en daarna respectvol verder praten", "demander une courte pause, se calmer puis reprendre respectueusement", "ask for a short pause, calm down, then continue respectfully"), g6Option("shout", "harder roepen om te winnen", "crier plus fort pour gagner", "shout louder to win"), g6Option("threat", "de ander bedreigen", "menacer l'autre", "threaten the other person")),
        g6Scenario("help_seeking", "Een probleem blijft onveilig of herhaalt zich ondanks eigen pogingen. Wat doe je?", "Un problème reste dangereux ou se répète malgré tes efforts. Que fais-tu ?", "A problem remains unsafe or keeps recurring despite your efforts. What should you do?", "adult", g6Option("adult", "een vertrouwde volwassene inschakelen en duidelijk vertellen wat er gebeurt", "prévenir un adulte de confiance et expliquer clairement ce qui se passe", "involve a trusted adult and clearly explain what is happening"), g6Option("secret", "het geheimhouden", "le garder secret", "keep it secret"), g6Option("revenge", "wraak nemen", "se venger", "take revenge")),
        g6Scenario("inclusion", "Een nieuwe leerling wordt niet betrokken bij de groep. Wat kun je doen?", "Un nouvel élève n'est pas intégré au groupe. Que peux-tu faire ?", "A new pupil is left out of the group. What can you do?", "invite", g6Option("invite", "de leerling uitnodigen, luisteren wat helpt en rollen eerlijk verdelen", "inviter l'élève, écouter ce qui aide et répartir équitablement les rôles", "invite the pupil, listen to what helps, and share roles fairly"), g6Option("assume", "beslissen dat die leerling liever alleen is", "décider que cet élève préfère être seul", "decide the pupil prefers being alone"), g6Option("joke", "er een grap over maken", "en faire une blague", "make a joke about it")),
        g6Scenario("study_strategy", "Je begrijpt een hoofdstuk nog niet. Welke aanpak is effectief?", "Tu ne comprends pas encore un chapitre. Quelle approche est efficace ?", "You do not yet understand a chapter. Which approach is effective?", "strategy", g6Option("strategy", "voorkennis ophalen, kernvragen maken, gericht oefenen en hulp vragen waar nodig", "activer les connaissances, formuler des questions, s'exercer et demander de l'aide si nécessaire", "recall prior knowledge, make key questions, practise, and ask for help where needed"), g6Option("reread", "dezelfde bladzijde gedachteloos blijven herlezen", "relire la même page sans réfléchir", "keep rereading the same page mindlessly"), g6Option("quit", "onmiddellijk stoppen", "arrêter immédiatement", "stop immediately")),
        g6Scenario("responsibility", "Je merkt dat jouw deel van de groepstaak vertraging heeft. Wat doe je?", "Tu remarques que ta partie du travail de groupe est en retard. Que fais-tu ?", "You notice your part of a group task is delayed. What should you do?", "communicate", g6Option("communicate", "tijdig melden, een nieuw haalbaar plan voorstellen en hulp afspreken", "le signaler à temps, proposer un nouveau plan réaliste et convenir d'une aide", "say so early, propose a realistic new plan, and arrange help"), g6Option("hide", "zwijgen tot de deadline voorbij is", "se taire jusqu'après l'échéance", "stay silent until after the deadline"), g6Option("blame", "iemand anders de schuld geven", "accuser quelqu'un d'autre", "blame someone else")),
        g6Scenario("wellbeing_balance", "Welke weekplanning is het meest evenwichtig?", "Quel planning hebdomadaire est le plus équilibré ?", "Which weekly plan is most balanced?", "balanced", g6Option("balanced", "schoolwerk spreiden en tijd behouden voor slaap, beweging, ontspanning en contact", "répartir le travail et garder du temps pour dormir, bouger, se détendre et voir les autres", "spread schoolwork and keep time for sleep, movement, relaxation, and connection"), g6Option("work", "elke vrije minuut vullen met schoolwerk", "remplir chaque minute libre de travail scolaire", "fill every free minute with schoolwork"), g6Option("none", "geen verplichtingen plannen en alles vergeten", "ne rien planifier et tout oublier", "plan no responsibilities and forget everything")),
    ),
)
