package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

internal data class Grade3Choice(val key: String, val nl: String, val fr: String, val en: String)

internal data class Grade3Scenario(
    val kind: String,
    val prompt: LocalizedText,
    val answerKey: String,
    val options: List<Grade3Choice>
)

private fun g3Text(nl: String, fr: String, en: String) = LocalizedText(nl = nl, fr = fr, en = en)
private fun g3Option(key: String, nl: String, fr: String, en: String) = Grade3Choice(key, nl, fr, en)
private fun g3Scenario(
    kind: String,
    nl: String,
    fr: String,
    en: String,
    answerKey: String,
    answer: Grade3Choice,
    distractor1: Grade3Choice,
    distractor2: Grade3Choice
) = Grade3Scenario(kind, g3Text(nl, fr, en), answerKey, listOf(answer, distractor1, distractor2))

internal abstract class Grade3ChoiceGenerator(
    final override val generatorId: String,
    private val roadmapEntry: String,
    private val idNamespace: String,
    private val skill: String,
    private val domain: LearningDomain,
    private val automatedScope: String,
    private val adultSupportKey: String,
    private val hint: LocalizedText,
    private val extraData: Map<String, String> = emptyMap(),
    private val scenarios: List<Grade3Scenario>
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
        if (parameters["grade"] != "3") add("grade must be 3")
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
                level = SkillLevel.THIRD_GRADE,
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

internal object Grade3ReadingComprehensionInformationGenerator : Grade3ChoiceGenerator(
    generatorId = "generator.g3.language.reading_comprehension.information",
    roadmapEntry = "roadmap.g3.language.reading_comprehension.information",
    idNamespace = "g3.language.reading_comprehension.information",
    skill = "reading_comprehension_information",
    domain = LearningDomain.READING,
    automatedScope = "short_information_text_comprehension_choice",
    adultSupportKey = "requiresAdultReadingDiscussion",
    hint = g3Text("Zoek het antwoord dat rechtstreeks door de tekst wordt ondersteund.", "Cherche la réponse directement soutenue par le texte.", "Choose the answer directly supported by the text."),
    scenarios = listOf(
        g3Scenario("main_idea", "Bijen brengen stuifmeel van bloem naar bloem. Daardoor kunnen veel planten vruchten maken. Wat is de hoofdgedachte?", "Les abeilles transportent le pollen de fleur en fleur. Ainsi, de nombreuses plantes peuvent produire des fruits. Quelle est l'idée principale ?", "Bees carry pollen from flower to flower. This helps many plants make fruit. What is the main idea?", "pollination", g3Option("pollination", "Bijen helpen planten zich voortplanten.", "Les abeilles aident les plantes à se reproduire.", "Bees help plants reproduce."), g3Option("honey", "Alle bijen maken elke dag honing.", "Toutes les abeilles font du miel chaque jour.", "All bees make honey every day."), g3Option("winter", "Bloemen groeien alleen in de winter.", "Les fleurs poussent seulement en hiver.", "Flowers grow only in winter.")),
        g3Scenario("explicit_detail", "Een egel eet insecten en zoekt 's nachts voedsel. Wanneer zoekt de egel voedsel?", "Un hérisson mange des insectes et cherche sa nourriture la nuit. Quand cherche-t-il sa nourriture ?", "A hedgehog eats insects and looks for food at night. When does it look for food?", "night", g3Option("night", "'s nachts", "la nuit", "at night"), g3Option("morning", "'s morgens", "le matin", "in the morning"), g3Option("noon", "'s middags", "à midi", "at noon")),
        g3Scenario("heading", "De tekst legt uit hoe water verdampt, wolken vormt en als regen terugvalt. Welke titel past het best?", "Le texte explique comment l'eau s'évapore, forme des nuages et retombe en pluie. Quel titre convient le mieux ?", "The text explains how water evaporates, forms clouds, and falls as rain. Which title fits best?", "cycle", g3Option("cycle", "De kringloop van water", "Le cycle de l'eau", "The water cycle"), g3Option("desert", "Dieren in de woestijn", "Les animaux du désert", "Desert animals"), g3Option("boats", "Boten op zee", "Les bateaux en mer", "Boats at sea")),
        g3Scenario("sequence", "Een zaadje kiemt. Daarna groeit een stengel. Vervolgens verschijnen bladeren. Wat gebeurt als tweede?", "Une graine germe. Ensuite, une tige pousse. Puis des feuilles apparaissent. Que se passe-t-il en deuxième ?", "A seed sprouts. Next, a stem grows. Then leaves appear. What happens second?", "stem", g3Option("stem", "Er groeit een stengel.", "Une tige pousse.", "A stem grows."), g3Option("seed", "Het zaadje kiemt.", "La graine germe.", "The seed sprouts."), g3Option("leaves", "Er verschijnen bladeren.", "Des feuilles apparaissent.", "Leaves appear.")),
        g3Scenario("cause_effect", "De stoep was bevroren, omdat het 's nachts vroor. Waarom was de stoep glad?", "Le trottoir était gelé parce qu'il avait gelé pendant la nuit. Pourquoi était-il glissant ?", "The pavement was icy because it froze overnight. Why was it slippery?", "frost", g3Option("frost", "Het had 's nachts gevroren.", "Il avait gelé pendant la nuit.", "It froze overnight."), g3Option("sun", "De zon scheen fel.", "Le soleil brillait fort.", "The sun was bright."), g3Option("swept", "De stoep was geveegd.", "Le trottoir avait été balayé.", "The pavement was swept.")),
        g3Scenario("table", "Bibliotheekbezoek: maandag 24, dinsdag 31, woensdag 28. Op welke dag kwamen de meeste bezoekers?", "Visites à la bibliothèque : lundi 24, mardi 31, mercredi 28. Quel jour y a-t-il eu le plus de visiteurs ?", "Library visits: Monday 24, Tuesday 31, Wednesday 28. Which day had the most visitors?", "tuesday", g3Option("tuesday", "dinsdag", "mardi", "Tuesday"), g3Option("monday", "maandag", "lundi", "Monday"), g3Option("wednesday", "woensdag", "mercredi", "Wednesday")),
        g3Scenario("inference", "Mila hangt haar natte jas aan de kapstok en zet haar paraplu open om te drogen. Welk weer was het waarschijnlijk?", "Mila suspend sa veste mouillée et ouvre son parapluie pour le faire sécher. Quel temps faisait-il probablement ?", "Mila hangs up her wet coat and opens her umbrella to dry. What was the weather probably like?", "rain", g3Option("rain", "het regende", "il pleuvait", "it was raining"), g3Option("heat", "het was heet en droog", "il faisait chaud et sec", "it was hot and dry"), g3Option("snow", "er lag zeker diepe sneeuw", "il y avait certainement beaucoup de neige", "there was definitely deep snow")),
        g3Scenario("compare", "Een ijsbeer heeft een dikke vacht. Een zeehond heeft een dikke vetlaag. Wat hebben beide kenmerken als doel?", "L'ours polaire a une fourrure épaisse. Le phoque a une épaisse couche de graisse. Quel est le but commun de ces caractéristiques ?", "A polar bear has thick fur. A seal has a thick layer of fat. What is the shared purpose of these features?", "warm", g3Option("warm", "warm blijven in de kou", "rester au chaud dans le froid", "stay warm in the cold"), g3Option("fly", "hoger vliegen", "voler plus haut", "fly higher"), g3Option("plants", "planten laten groeien", "faire pousser des plantes", "make plants grow")),
        g3Scenario("context_word", "De beek is ondiep: het water komt maar tot aan je enkels. Wat betekent 'ondiep'?", "Le ruisseau est peu profond : l'eau arrive seulement aux chevilles. Que signifie « peu profond » ?", "The stream is shallow: the water only reaches your ankles. What does 'shallow' mean?", "little_depth", g3Option("little_depth", "met weinig diepte", "avec peu de profondeur", "not very deep"), g3Option("fast", "heel snel stromend", "qui coule très vite", "flowing very fast"), g3Option("dirty", "erg vuil", "très sale", "very dirty")),
        g3Scenario("text_purpose", "Een tekst legt stap voor stap uit hoe je zaadjes plant. Wat is het doel van de tekst?", "Un texte explique étape par étape comment semer des graines. Quel est son but ?", "A text explains step by step how to plant seeds. What is its purpose?", "instruct", g3Option("instruct", "uitleggen hoe je iets doet", "expliquer comment faire quelque chose", "explain how to do something"), g3Option("entertain", "een grappig verhaal vertellen", "raconter une histoire drôle", "tell a funny story"), g3Option("invite", "uitnodigen voor een feest", "inviter à une fête", "invite someone to a party"))
    )
)

internal object Grade3InformationSkillsLookupGenerator : Grade3ChoiceGenerator(
    generatorId = "generator.g3.language.information_skills.lookup",
    roadmapEntry = "roadmap.g3.language.information_skills.lookup",
    idNamespace = "g3.language.information_skills.lookup",
    skill = "information_skills_lookup",
    domain = LearningDomain.READING,
    automatedScope = "lookup_strategy_and_source_choice",
    adultSupportKey = "requiresAdultSourcePractice",
    hint = g3Text("Kies de snelste, veilige en controleerbare zoekstap.", "Choisis l'étape de recherche la plus rapide, sûre et vérifiable.", "Choose the quickest, safest, and verifiable search step."),
    scenarios = listOf(
        g3Scenario("alphabetical_order", "Welk woord staat alfabetisch eerst?", "Quel mot vient en premier dans l'ordre alphabétique ?", "Which word comes first alphabetically?", "apple", g3Option("apple", "appel", "abricot", "apple"), g3Option("pear", "peer", "poire", "pear"), g3Option("plum", "pruim", "prune", "plum")),
        g3Scenario("guide_words", "Bovenaan de woordenboekpagina staan 'maan – muts'. Welk woord vind je op die pagina?", "En haut de la page du dictionnaire figurent « maison – musique ». Quel mot se trouve sur cette page ?", "The dictionary page shows the guide words 'map – music.' Which word is on that page?", "middle", g3Option("middle", "melk", "moteur", "mirror"), g3Option("before", "lamp", "livre", "lake"), g3Option("after", "noot", "navire", "night")),
        g3Scenario("keyword", "Je zoekt waarom bladeren in de herfst verkleuren. Welke zoekwoorden zijn het best?", "Tu cherches pourquoi les feuilles changent de couleur en automne. Quels mots-clés sont les meilleurs ?", "You want to know why leaves change color in autumn. Which keywords are best?", "specific", g3Option("specific", "bladeren kleur herfst waarom", "feuilles couleur automne pourquoi", "leaves color autumn why"), g3Option("broad", "planten", "plantes", "plants"), g3Option("question", "ik wil iets weten", "je veux savoir quelque chose", "I want to know something")),
        g3Scenario("source_author", "Welke bron kun je het best controleren?", "Quelle source peux-tu le mieux vérifier ?", "Which source can you verify most easily?", "named", g3Option("named", "een artikel met auteur, datum en bronnen", "un article avec auteur, date et sources", "an article with an author, date, and sources"), g3Option("anonymous", "een bericht zonder auteur", "un message sans auteur", "a post without an author"), g3Option("advert", "een reclame zonder uitleg", "une publicité sans explication", "an ad without explanation")),
        g3Scenario("cross_check", "Eén website zegt iets verrassends. Wat doe je daarna?", "Un site affirme quelque chose de surprenant. Que fais-tu ensuite ?", "One website makes a surprising claim. What should you do next?", "compare", g3Option("compare", "controleren bij een tweede betrouwbare bron", "vérifier dans une deuxième source fiable", "check a second reliable source"), g3Option("share", "het meteen doorsturen", "le partager immédiatement", "share it immediately"), g3Option("believe", "het altijd geloven", "toujours le croire", "always believe it")),
        g3Scenario("safe_search", "Een zoekresultaat vraagt je adres om de pagina te openen. Wat doe je?", "Un résultat demande ton adresse pour ouvrir la page. Que fais-tu ?", "A search result asks for your home address before opening. What should you do?", "adult", g3Option("adult", "stoppen en een vertrouwde volwassene vragen", "arrêter et demander à un adulte de confiance", "stop and ask a trusted adult"), g3Option("enter", "het adres invullen", "indiquer l'adresse", "enter the address"), g3Option("guess", "een ander adres verzinnen", "inventer une autre adresse", "make up another address")),
        g3Scenario("index", "Je zoekt in een boek informatie over vulkanen. Waar kijk je om snel de juiste pagina te vinden?", "Tu cherches des informations sur les volcans dans un livre. Où regardes-tu pour trouver rapidement la bonne page ?", "You need information about volcanoes in a book. Where should you look to find the page quickly?", "index", g3Option("index", "in het register achteraan", "dans l'index à la fin", "in the index at the back"), g3Option("cover", "alleen op de voorkaft", "uniquement sur la couverture", "only on the front cover"), g3Option("random", "op een willekeurige pagina", "à une page au hasard", "on a random page")),
        g3Scenario("contents", "Je wilt weten welk hoofdstuk over planeten gaat. Welk onderdeel gebruik je eerst?", "Tu veux savoir quel chapitre parle des planètes. Quelle partie utilises-tu d'abord ?", "You want to know which chapter is about planets. Which part should you use first?", "contents", g3Option("contents", "de inhoudsopgave", "la table des matières", "the table of contents"), g3Option("colophon", "het colofon", "les mentions légales", "the publication details"), g3Option("last", "de laatste zin", "la dernière phrase", "the last sentence")),
        g3Scenario("caption", "Onder een foto staat: 'Een bever bouwt een dam met takken.' Waarvoor dient die zin?", "Sous une photo, on lit : « Un castor construit un barrage avec des branches. » À quoi sert cette phrase ?", "A photo says, 'A beaver builds a dam with branches.' What is that sentence for?", "explain_photo", g3Option("explain_photo", "de foto uitleggen", "expliquer la photo", "explain the photo"), g3Option("page", "het paginanummer vervangen", "remplacer le numéro de page", "replace the page number"), g3Option("author", "de auteur verbergen", "cacher l'auteur", "hide the author")),
        g3Scenario("date_relevance", "Je zoekt de vertrektijd van de trein voor morgen. Welke bron is het meest bruikbaar?", "Tu cherches l'heure de départ du train pour demain. Quelle source est la plus utile ?", "You need tomorrow's train departure time. Which source is most useful?", "current", g3Option("current", "de actuele dienstregeling", "l'horaire actuel", "the current timetable"), g3Option("old", "een dienstregeling van vijf jaar geleden", "un horaire d'il y a cinq ans", "a timetable from five years ago"), g3Option("story", "een verhaal over treinen", "une histoire sur les trains", "a story about trains"))
    )
)

internal object Grade3FunctionalWritingGenerator : Grade3ChoiceGenerator(
    generatorId = "generator.g3.language.writing.functional_texts",
    roadmapEntry = "roadmap.g3.language.writing.functional_texts",
    idNamespace = "g3.language.writing.functional_texts",
    skill = "functional_writing",
    domain = LearningDomain.READING,
    automatedScope = "functional_text_planning_revision_choice",
    adultSupportKey = "requiresAdultFunctionalWritingReview",
    hint = g3Text("Denk aan doel, lezer, volledigheid en passende toon.", "Pense au but, au lecteur, aux informations complètes et au ton adapté.", "Consider purpose, reader, completeness, and an appropriate tone."),
    scenarios = listOf(
        g3Scenario("subject_line", "Je mailt de juf omdat je morgen later komt. Welke onderwerpregel is duidelijk?", "Tu écris à l'enseignante parce que tu arriveras plus tard demain. Quel objet est clair ?", "You email your teacher because you will arrive late tomorrow. Which subject line is clear?", "clear", g3Option("clear", "Morgen 15 minuten later", "Demain : 15 minutes de retard", "15 minutes late tomorrow"), g3Option("vague", "Hallo", "Bonjour", "Hello"), g3Option("empty", "Geen onderwerp", "Sans objet", "No subject")),
        g3Scenario("greeting", "Welke aanhef past in een bericht aan de directeur?", "Quelle formule d'appel convient dans un message à la direction ?", "Which greeting fits a message to the principal?", "polite", g3Option("polite", "Beste directeur,", "Madame la Directrice, Monsieur le Directeur,", "Dear Principal,"), g3Option("casual", "Hé jij!", "Hé, toi !", "Hey you!"), g3Option("none", "zonder aanhef", "sans formule d'appel", "no greeting")),
        g3Scenario("form_field", "Op een formulier staat 'noodcontact'. Wat vul je daar met een volwassene in?", "Un formulaire demande un « contact d'urgence ». Que complètes-tu avec un adulte ?", "A form asks for an 'emergency contact.' What do you enter with an adult?", "contact", g3Option("contact", "de afgesproken contactpersoon", "la personne de contact convenue", "the agreed contact person"), g3Option("password", "je wachtwoord", "ton mot de passe", "your password"), g3Option("nickname", "een verzonnen bijnaam", "un surnom inventé", "a made-up nickname")),
        g3Scenario("instruction_order", "Welke instructie is het duidelijkst?", "Quelle instruction est la plus claire ?", "Which instruction is clearest?", "ordered", g3Option("ordered", "Vouw eerst het blad en knip daarna langs de lijn.", "Plie d'abord la feuille, puis découpe le long de la ligne.", "First fold the paper, then cut along the line."), g3Option("vague", "Doe daarna alles.", "Fais ensuite tout.", "Then do everything."), g3Option("reverse", "Knip de lijn voordat je weet waar.", "Découpe avant de savoir où.", "Cut before you know where.")),
        g3Scenario("report_fact", "Welke zin past in een feitelijk verslag van een proef?", "Quelle phrase convient dans le compte rendu factuel d'une expérience ?", "Which sentence belongs in a factual experiment report?", "fact", g3Option("fact", "Na twee minuten werd het water warmer.", "Après deux minutes, l'eau est devenue plus chaude.", "After two minutes, the water became warmer."), g3Option("opinion", "Dit was de leukste proef ooit.", "C'était la meilleure expérience de tous les temps.", "This was the best experiment ever."), g3Option("unrelated", "Mijn fiets is blauw.", "Mon vélo est bleu.", "My bicycle is blue.")),
        g3Scenario("closing", "Welke afsluiting past bij een beleefde vraag per e-mail?", "Quelle formule de fin convient à une demande polie par e-mail ?", "Which closing fits a polite request by email?", "thanks", g3Option("thanks", "Alvast bedankt. Met vriendelijke groet,", "Merci d'avance. Bien cordialement,", "Thank you in advance. Kind regards,"), g3Option("demand", "Doe dit nu!", "Faites-le maintenant !", "Do this now!"), g3Option("abrupt", "Einde.", "Fin.", "The end.")),
        g3Scenario("audience", "Je schrijft voor jongere kinderen. Welke zin is het duidelijkst?", "Tu écris pour de jeunes enfants. Quelle phrase est la plus claire ?", "You are writing for younger children. Which sentence is clearest?", "simple", g3Option("simple", "Was je handen voor het eten.", "Lave-toi les mains avant de manger.", "Wash your hands before eating."), g3Option("complex", "Voorafgaand aan de maaltijd is handhygiëne vereist.", "L'hygiène manuelle est requise préalablement au repas.", "Hand hygiene is required prior to the meal."), g3Option("unrelated", "De maan draait om de aarde.", "La Lune tourne autour de la Terre.", "The Moon orbits Earth.")),
        g3Scenario("list_format", "Je noteert vier benodigdheden voor een proef. Welke vorm is het overzichtelijkst?", "Tu notes quatre éléments nécessaires pour une expérience. Quelle forme est la plus claire ?", "You list four things needed for an experiment. Which format is clearest?", "bullets", g3Option("bullets", "een lijst met elk materiaal op een nieuwe regel", "une liste avec chaque matériel sur une nouvelle ligne", "a list with each item on a new line"), g3Option("one_word", "alleen het woord 'spullen'", "seulement le mot « matériel »", "only the word 'things'"), g3Option("mixed", "woorden zonder spaties of tekens", "des mots sans espaces ni signes", "words without spaces or marks")),
        g3Scenario("revise_ambiguity", "In een routebeschrijving staat: 'Ga daarheen.' Welke verbetering maakt de zin duidelijk?", "Un itinéraire dit : « Va là-bas. » Quelle amélioration rend la phrase plus claire ?", "Directions say, 'Go there.' Which revision makes the sentence clearer?", "specific", g3Option("specific", "Ga bij de bakker linksaf.", "Tourne à gauche devant la boulangerie.", "Turn left at the bakery."), g3Option("same", "Ga gewoon daarheen.", "Va simplement là-bas.", "Just go there."), g3Option("less", "Ga.", "Va.", "Go.")),
        g3Scenario("required_field", "Een inschrijfformulier heeft een vak met een sterretje. Wat betekent dat meestal?", "Une case d'un formulaire porte un astérisque. Que signifie-t-il généralement ?", "A form field has an asterisk. What does that usually mean?", "required", g3Option("required", "het vak moet worden ingevuld", "la case doit être complétée", "the field must be completed"), g3Option("secret", "het vak is geheim", "la case est secrète", "the field is secret"), g3Option("decoration", "het is alleen versiering", "c'est seulement décoratif", "it is only decoration"))
    )
)

internal object Grade3ReadingStrategiesGenerator : Grade3ChoiceGenerator(
    generatorId = "generator.g3.language.reading_strategies.summarise_context",
    roadmapEntry = "roadmap.g3.language.reading_strategies.summarise_context",
    idNamespace = "g3.language.reading_strategies.summarise_context",
    skill = "reading_strategies",
    domain = LearningDomain.READING,
    automatedScope = "reading_strategy_recognition_choice",
    adultSupportKey = "requiresAdultStrategyExplanation",
    hint = g3Text("Kies de strategie die past bij je leesdoel en controleer met de tekst.", "Choisis la stratégie adaptée à ton objectif et vérifie dans le texte.", "Choose the strategy that fits your reading goal and check it against the text."),
    scenarios = listOf(
        g3Scenario("summary", "Een tekst vertelt dat bomen schaduw geven, lucht zuiveren en dieren huisvesten. Welke samenvatting past?", "Un texte dit que les arbres donnent de l'ombre, purifient l'air et abritent des animaux. Quel résumé convient ?", "A text says trees give shade, clean the air, and shelter animals. Which summary fits?", "benefits", g3Option("benefits", "Bomen zijn op verschillende manieren belangrijk.", "Les arbres sont importants de plusieurs façons.", "Trees are important in several ways."), g3Option("one", "Alle bomen zijn even hoog.", "Tous les arbres ont la même hauteur.", "All trees are the same height."), g3Option("opposite", "Dieren vermijden bomen.", "Les animaux évitent les arbres.", "Animals avoid trees.")),
        g3Scenario("keyword", "Welke twee kernwoorden passen bij een tekst over hoe trekvogels in de herfst naar warmere landen vliegen?", "Quels mots-clés conviennent à un texte sur les oiseaux migrateurs qui partent vers des pays plus chauds en automne ?", "Which keywords fit a text about migrating birds flying to warmer countries in autumn?", "migration", g3Option("migration", "trekvogels – herfst", "oiseaux migrateurs – automne", "migrating birds – autumn"), g3Option("unrelated", "fiets – straat", "vélo – rue", "bicycle – street"), g3Option("tiny", "een – de", "un – le", "a – the")),
        g3Scenario("context", "De woestijn is dor: er valt bijna geen regen en weinig planten groeien er. Wat betekent 'dor'?", "Le désert est aride : il pleut très peu et peu de plantes y poussent. Que signifie « aride » ?", "The desert is arid: very little rain falls and few plants grow there. What does 'arid' mean?", "dry", g3Option("dry", "erg droog", "très sec", "very dry"), g3Option("wet", "erg nat", "très humide", "very wet"), g3Option("cold", "erg koud", "très froid", "very cold")),
        g3Scenario("scan", "Je wilt in een lange tekst alleen de openingsuren vinden. Welke strategie gebruik je?", "Tu veux seulement trouver les heures d'ouverture dans un long texte. Quelle stratégie utilises-tu ?", "You only need to find opening hours in a long text. Which strategy should you use?", "scan", g3Option("scan", "gericht zoeken naar cijfers en het woord 'open'", "chercher les chiffres et le mot « ouvert »", "scan for numbers and the word 'open'"), g3Option("memorize", "de hele tekst uit het hoofd leren", "mémoriser tout le texte", "memorize the whole text"), g3Option("skip", "niets lezen", "ne rien lire", "read nothing")),
        g3Scenario("predict", "De titel is 'Een onweersbui op komst' en de lucht wordt donker. Wat kun je voorspellen?", "Le titre est « Un orage approche » et le ciel s'assombrit. Que peux-tu prévoir ?", "The title is 'A Storm Is Coming' and the sky grows dark. What can you predict?", "rain", g3Option("rain", "Het gaat waarschijnlijk regenen en donderen.", "Il va probablement pleuvoir et tonner.", "It will probably rain and thunder."), g3Option("snow", "Er komt zeker een sneeuwstorm.", "Une tempête de neige va certainement arriver.", "A snowstorm will definitely arrive."), g3Option("summer", "De zomer begint meteen.", "L'été commence immédiatement.", "Summer begins immediately.")),
        g3Scenario("verify", "Je voorspelling klopt niet met de volgende alinea. Wat doe je?", "Ta prédiction ne correspond pas au paragraphe suivant. Que fais-tu ?", "Your prediction does not match the next paragraph. What should you do?", "adjust", g3Option("adjust", "de voorspelling aanpassen met nieuwe informatie", "adapter la prédiction avec les nouvelles informations", "adjust the prediction using the new information"), g3Option("ignore", "de nieuwe informatie negeren", "ignorer la nouvelle information", "ignore the new information"), g3Option("quit", "altijd stoppen met lezen", "toujours arrêter de lire", "always stop reading")),
        g3Scenario("sequence_words", "Welke woorden helpen je de volgorde in een tekst te volgen?", "Quels mots t'aident à suivre l'ordre dans un texte ?", "Which words help you follow the order in a text?", "order_words", g3Option("order_words", "eerst – daarna – ten slotte", "d'abord – ensuite – enfin", "first – next – finally"), g3Option("colors", "rood – groen – blauw", "rouge – vert – bleu", "red – green – blue"), g3Option("opinions", "leuk – mooi – fijn", "amusant – joli – agréable", "fun – pretty – nice")),
        g3Scenario("question_before", "Je gaat een tekst over bijen lezen. Welke vraag helpt je gericht te lezen?", "Tu vas lire un texte sur les abeilles. Quelle question t'aide à lire avec un objectif ?", "You are about to read a text about bees. Which question gives you a reading purpose?", "useful", g3Option("useful", "Hoe communiceren bijen?", "Comment les abeilles communiquent-elles ?", "How do bees communicate?"), g3Option("unrelated", "Welke kleur heeft mijn jas?", "De quelle couleur est ma veste ?", "What color is my coat?"), g3Option("none", "Ik stel nooit een vraag.", "Je ne pose jamais de question.", "I never ask a question.")),
        g3Scenario("fact_opinion", "Welke zin is een controleerbaar feit?", "Quelle phrase est un fait vérifiable ?", "Which sentence is a checkable fact?", "fact", g3Option("fact", "Een week telt zeven dagen.", "Une semaine compte sept jours.", "A week has seven days."), g3Option("opinion", "Maandag is de leukste dag.", "Le lundi est le meilleur jour.", "Monday is the best day."), g3Option("taste", "Iedereen houdt van regen.", "Tout le monde aime la pluie.", "Everyone likes rain.")),
        g3Scenario("reread", "Een zin is moeilijk en je begrijpt hem niet meteen. Wat doe je eerst?", "Une phrase est difficile et tu ne la comprends pas tout de suite. Que fais-tu d'abord ?", "A sentence is difficult and you do not understand it at first. What should you do first?", "reread", g3Option("reread", "de zin rustiger opnieuw lezen", "relire la phrase plus lentement", "reread the sentence more slowly"), g3Option("guess", "een antwoord kiezen zonder te lezen", "choisir une réponse sans lire", "choose an answer without reading"), g3Option("skip_all", "de hele tekst overslaan", "sauter tout le texte", "skip the whole text"))
    )
)

internal object Grade3WorldTimeSpaceNatureSocietyGenerator : Grade3ChoiceGenerator(
    generatorId = "generator.g3.world.time_space_nature_society",
    roadmapEntry = "roadmap.g3.world.time_space_nature_society",
    idNamespace = "g3.world.time_space_nature_society",
    skill = "time_space_nature_society",
    domain = LearningDomain.WORLD_KNOWLEDGE,
    automatedScope = "world_orientation_concept_choice",
    adultSupportKey = "requiresAdultFieldObservation",
    hint = g3Text("Gebruik tijd, plaats, kenmerken en oorzaak-gevolg om te vergelijken.", "Utilise le temps, le lieu, les caractéristiques et les causes pour comparer.", "Use time, place, features, and cause and effect to compare."),
    scenarios = listOf(
        g3Scenario("timeline", "Welke volgorde is juist?", "Quel ordre est correct ?", "Which order is correct?", "order", g3Option("order", "geboorte – kleuter – schoolkind", "naissance – petit enfant – écolier", "birth – young child – schoolchild"), g3Option("reverse", "schoolkind – geboorte – kleuter", "écolier – naissance – petit enfant", "schoolchild – birth – young child"), g3Option("mixed", "kleuter – schoolkind – geboorte", "petit enfant – écolier – naissance", "young child – schoolchild – birth")),
        g3Scenario("direction", "Je kijkt naar het noorden. Welke richting ligt rechts van je?", "Tu regardes vers le nord. Quelle direction est à ta droite ?", "You are facing north. Which direction is on your right?", "east", g3Option("east", "oost", "est", "east"), g3Option("west", "west", "ouest", "west"), g3Option("south", "zuid", "sud", "south")),
        g3Scenario("map_legend", "Op een kaart staat een blauwe lijn volgens de legende voor een rivier. Wat betekent de blauwe lijn?", "Sur une carte, la légende indique qu'une ligne bleue représente une rivière. Que signifie la ligne bleue ?", "A map legend says a blue line represents a river. What does the blue line mean?", "river", g3Option("river", "een rivier", "une rivière", "a river"), g3Option("road", "een weg", "une route", "a road"), g3Option("border", "een landsgrens", "une frontière", "a border")),
        g3Scenario("habitat", "Welke aanpassing helpt een eend in en rond water?", "Quelle adaptation aide un canard dans l'eau et autour de l'eau ?", "Which adaptation helps a duck live in and around water?", "feet", g3Option("feet", "vliezen tussen de tenen", "des palmures entre les doigts", "webbed feet"), g3Option("fur", "een dikke wintervacht", "une épaisse fourrure", "thick winter fur"), g3Option("claws", "klauwen om rotsen te beklimmen", "des griffes pour grimper aux rochers", "claws for climbing rocks")),
        g3Scenario("materials", "Welk materiaal is geschikt voor een regenjas?", "Quel matériau convient pour un imperméable ?", "Which material is suitable for a raincoat?", "waterproof", g3Option("waterproof", "waterdichte kunststof", "un tissu synthétique imperméable", "waterproof synthetic material"), g3Option("paper", "dun papier", "du papier fin", "thin paper"), g3Option("sponge", "een spons", "une éponge", "a sponge")),
        g3Scenario("public_service", "Welke dienst helpt wanneer er brand uitbreekt?", "Quel service intervient lorsqu'un incendie se déclare ?", "Which service helps when a fire starts?", "fire", g3Option("fire", "de brandweer", "les pompiers", "the fire service"), g3Option("library", "de bibliotheek", "la bibliothèque", "the library"), g3Option("post", "de post", "la poste", "the postal service")),
        g3Scenario("seasons", "In welk seizoen worden de dagen meestal langer en verschijnen veel nieuwe bladeren?", "Pendant quelle saison les jours s'allongent-ils généralement et de nouvelles feuilles apparaissent-elles ?", "In which season do days usually grow longer and many new leaves appear?", "spring", g3Option("spring", "de lente", "le printemps", "spring"), g3Option("autumn", "de herfst", "l'automne", "autumn"), g3Option("winter", "de winter", "l'hiver", "winter")),
        g3Scenario("life_cycle", "Wat komt in de levenscyclus van een vlinder na de rups?", "Dans le cycle de vie du papillon, qu'est-ce qui vient après la chenille ?", "What comes after the caterpillar in a butterfly's life cycle?", "pupa", g3Option("pupa", "de pop", "la chrysalide", "the pupa"), g3Option("egg", "het ei", "l'œuf", "the egg"), g3Option("seed", "het zaad", "la graine", "the seed")),
        g3Scenario("recycling", "Waar hoort een lege glazen fles zonder statiegeld?", "Où faut-il déposer une bouteille en verre vide non consignée ?", "Where should an empty non-returnable glass bottle go?", "glass", g3Option("glass", "in de glascontainer", "dans la bulle à verre", "in the glass recycling bin"), g3Option("paper", "bij papier en karton", "avec le papier et le carton", "with paper and cardboard"), g3Option("nature", "in de berm", "dans le fossé", "on the roadside")),
        g3Scenario("local_service", "Welke lokale dienst leent boeken uit?", "Quel service local prête des livres ?", "Which local service lends books?", "library", g3Option("library", "de bibliotheek", "la bibliothèque", "the library"), g3Option("fire", "de brandweer", "les pompiers", "the fire service"), g3Option("park", "het stadspark", "le parc communal", "the town park"))
    )
)

internal object Grade3CitizenshipResponsibilityGenerator : Grade3ChoiceGenerator(
    generatorId = "generator.g3.world.citizenship.responsibility",
    roadmapEntry = "roadmap.g3.world.citizenship.responsibility",
    idNamespace = "g3.world.citizenship.responsibility",
    skill = "citizenship_responsibility",
    domain = LearningDomain.WORLD_KNOWLEDGE,
    automatedScope = "responsibility_and_cooperation_scenario_choice",
    adultSupportKey = "requiresAdultSocialPractice",
    hint = g3Text("Kies een veilige, eerlijke en respectvolle handeling.", "Choisis une action sûre, équitable et respectueuse.", "Choose a safe, fair, and respectful action."),
    scenarios = listOf(
        g3Scenario("responsibility", "Je taak in de groep is de materialen opruimen. Wat doe je?", "Dans le groupe, ta tâche est de ranger le matériel. Que fais-tu ?", "Your group job is to put away the materials. What should you do?", "do_task", g3Option("do_task", "de materialen zorgvuldig opruimen", "ranger soigneusement le matériel", "put the materials away carefully"), g3Option("leave", "alles laten liggen", "tout laisser sur place", "leave everything out"), g3Option("blame", "iemand anders de schuld geven", "accuser quelqu'un d'autre", "blame someone else")),
        g3Scenario("listen", "Een klasgenoot legt een ander idee uit. Wat helpt de samenwerking?", "Un camarade explique une autre idée. Qu'est-ce qui aide la coopération ?", "A classmate explains a different idea. What helps cooperation?", "listen", g3Option("listen", "luisteren en daarna rustig reageren", "écouter puis répondre calmement", "listen and then respond calmly"), g3Option("interrupt", "steeds onderbreken", "interrompre sans cesse", "keep interrupting"), g3Option("mock", "het idee uitlachen", "se moquer de l'idée", "make fun of the idea")),
        g3Scenario("fair_share", "Er zijn zes stiften voor drie kinderen. Wat is een eerlijke verdeling?", "Il y a six marqueurs pour trois enfants. Quel partage est équitable ?", "There are six markers for three children. What is a fair share?", "two_each", g3Option("two_each", "ieder twee stiften", "deux marqueurs chacun", "two markers each"), g3Option("one_all", "één kind krijgt alles", "un enfant reçoit tout", "one child gets all"), g3Option("hide", "de stiften verstoppen", "cacher les marqueurs", "hide the markers")),
        g3Scenario("rule_reason", "Waarom spreken we af om rustig door de gang te lopen?", "Pourquoi convenons-nous de marcher calmement dans le couloir ?", "Why do we agree to walk calmly in the hallway?", "safety", g3Option("safety", "zodat iedereen zich veilig kan verplaatsen", "pour que chacun puisse se déplacer en sécurité", "so everyone can move safely"), g3Option("winner", "zodat iemand kan winnen", "pour que quelqu'un gagne", "so someone can win"), g3Option("secret", "omdat de reden geheim is", "parce que la raison est secrète", "because the reason is secret")),
        g3Scenario("inclusion", "Een nieuw kind kent de groepsregels nog niet. Wat doe je?", "Un nouvel élève ne connaît pas encore les règles du groupe. Que fais-tu ?", "A new child does not know the group rules yet. What should you do?", "explain", g3Option("explain", "vriendelijk uitleggen en hulp aanbieden", "expliquer gentiment et proposer de l'aide", "explain kindly and offer help"), g3Option("exclude", "het kind uitsluiten", "exclure l'enfant", "exclude the child"), g3Option("laugh", "lachen om een fout", "rire d'une erreur", "laugh at a mistake")),
        g3Scenario("seek_help", "Iemand wordt herhaaldelijk bedreigd. Wat is de veilige stap?", "Quelqu'un est menacé à plusieurs reprises. Quelle est l'étape sûre ?", "Someone is repeatedly threatened. What is the safe step?", "adult", g3Option("adult", "meteen een vertrouwde volwassene inschakelen", "prévenir immédiatement un adulte de confiance", "tell a trusted adult immediately"), g3Option("retaliate", "terug bedreigen", "menacer en retour", "threaten back"), g3Option("secret", "het altijd geheimhouden", "toujours garder le secret", "always keep it secret")),
        g3Scenario("apology", "Je stoot per ongeluk iemands bouwwerk om. Wat is verantwoordelijk gedrag?", "Tu renverses accidentellement la construction de quelqu'un. Quel comportement est responsable ?", "You accidentally knock over someone's model. What is responsible behavior?", "repair", g3Option("repair", "sorry zeggen en helpen herstellen", "s'excuser et aider à réparer", "apologize and help rebuild it"), g3Option("leave", "weglopen zonder iets te zeggen", "partir sans rien dire", "walk away without saying anything"), g3Option("blame", "een ander beschuldigen", "accuser quelqu'un d'autre", "blame someone else")),
        g3Scenario("turn_taking", "Drie kinderen willen hetzelfde spel gebruiken. Wat helpt?", "Trois enfants veulent utiliser le même jeu. Qu'est-ce qui aide ?", "Three children want to use the same game. What helps?", "turns", g3Option("turns", "beurten afspreken", "organiser des tours", "agree on turns"), g3Option("grab", "het spel afpakken", "arracher le jeu", "grab the game"), g3Option("hide", "het spel verstoppen", "cacher le jeu", "hide the game")),
        g3Scenario("shared_decision", "De groep moet één affichekleur kiezen. Wat is een eerlijke aanpak?", "Le groupe doit choisir une couleur pour l'affiche. Quelle démarche est équitable ?", "The group must choose one poster color. What is a fair approach?", "discuss_vote", g3Option("discuss_vote", "ideeën beluisteren en samen kiezen", "écouter les idées et choisir ensemble", "listen to ideas and choose together"), g3Option("boss", "één kind beslist altijd alleen", "un enfant décide toujours seul", "one child always decides alone"), g3Option("insult", "anderen uitlachen om hun keuze", "se moquer du choix des autres", "mock others for their choice")),
        g3Scenario("property", "Je wilt de schaar van een klasgenoot gebruiken. Wat doe je?", "Tu veux utiliser les ciseaux d'un camarade. Que fais-tu ?", "You want to use a classmate's scissors. What should you do?", "ask", g3Option("ask", "eerst toestemming vragen", "demander d'abord la permission", "ask permission first"), g3Option("take", "de schaar zomaar nemen", "prendre les ciseaux sans demander", "take the scissors without asking"), g3Option("hide", "de schaar daarna verstoppen", "cacher ensuite les ciseaux", "hide the scissors afterward"))
    )
)

internal object Grade3ModernLanguagesInitiationGenerator : Grade3ChoiceGenerator(
    generatorId = "generator.g3.language.modern_languages.initiation",
    roadmapEntry = "roadmap.g3.language.modern_languages.initiation",
    idNamespace = "g3.language.modern_languages.initiation",
    skill = "modern_languages_initiation",
    domain = LearningDomain.READING,
    automatedScope = "early_modern_language_recognition_choice",
    adultSupportKey = "requiresAdultOralLanguagePractice",
    hint = g3Text("Gebruik de begroeting, afbeelding of situatie als betekenisaanwijzing.", "Utilise la salutation, l'image ou la situation comme indice de sens.", "Use the greeting, picture, or situation as a meaning clue."),
    extraData = mapOf("formalFrenchInstruction" to "false", "oralMasteryInferred" to "false"),
    scenarios = listOf(
        g3Scenario("greeting", "Welk Frans woord betekent 'hallo'?", "Quel mot anglais signifie « bonjour » ?", "Which French word means 'hello'?", "hello", g3Option("hello", "bonjour", "hello", "bonjour"), g3Option("thanks", "merci", "thank you", "merci"), g3Option("bye", "au revoir", "goodbye", "au revoir")),
        g3Scenario("thanks", "Hoe zeg je 'dank je' in het Frans?", "Comment dit-on « merci » en anglais ?", "How do you say 'thank you' in French?", "thanks", g3Option("thanks", "merci", "thank you", "merci"), g3Option("please", "s'il vous plaît", "please", "s'il vous plaît"), g3Option("hello", "bonjour", "hello", "bonjour")),
        g3Scenario("number", "Welk Frans woord betekent 'drie'?", "Quel mot anglais signifie « trois » ?", "Which French word means 'three'?", "three", g3Option("three", "trois", "three", "trois"), g3Option("two", "deux", "two", "deux"), g3Option("five", "cinq", "five", "cinq")),
        g3Scenario("colour", "Welke Franse kleur betekent 'blauw'?", "Quelle couleur anglaise signifie « bleu » ?", "Which French color means 'blue'?", "blue", g3Option("blue", "bleu", "blue", "bleu"), g3Option("red", "rouge", "red", "rouge"), g3Option("green", "vert", "green", "vert")),
        g3Scenario("classroom", "Wat betekent de Franse zin 'Ouvre le livre'?", "Que signifie la phrase anglaise « Open the book » ?", "What does the French sentence 'Ouvre le livre' mean?", "open", g3Option("open", "Open het boek.", "Ouvre le livre.", "Open the book."), g3Option("close", "Sluit het boek.", "Ferme le livre.", "Close the book."), g3Option("write", "Schrijf je naam.", "Écris ton nom.", "Write your name.")),
        g3Scenario("introduction", "Wat betekent 'Je m'appelle Lina'?", "Que signifie « My name is Lina » ?", "What does 'Je m'appelle Lina' mean?", "name", g3Option("name", "Ik heet Lina.", "Je m'appelle Lina.", "My name is Lina."), g3Option("age", "Ik ben negen jaar.", "J'ai neuf ans.", "I am nine."), g3Option("home", "Ik woon in Brussel.", "J'habite à Bruxelles.", "I live in Brussels.")),
        g3Scenario("goodbye", "Welk Frans woord betekent 'tot ziens'?", "Quel mot anglais signifie « au revoir » ?", "Which French phrase means 'goodbye'?", "goodbye", g3Option("goodbye", "au revoir", "goodbye", "au revoir"), g3Option("hello", "bonjour", "hello", "bonjour"), g3Option("thanks", "merci", "thank you", "merci")),
        g3Scenario("please", "Hoe zeg je 'alsjeblieft' in het Frans?", "Comment dit-on « s'il vous plaît » en anglais ?", "How do you say 'please' in French?", "please", g3Option("please", "s'il vous plaît", "please", "s'il vous plaît"), g3Option("thanks", "merci", "thank you", "merci"), g3Option("bye", "au revoir", "goodbye", "au revoir")),
        g3Scenario("one", "Welk Frans woord betekent 'één'?", "Quel mot anglais signifie « un » ?", "Which French word means 'one'?", "one", g3Option("one", "un", "one", "un"), g3Option("two", "deux", "two", "deux"), g3Option("four", "quatre", "four", "quatre")),
        g3Scenario("red", "Welke Franse kleur betekent 'rood'?", "Quelle couleur anglaise signifie « rouge » ?", "Which French color means 'red'?", "red", g3Option("red", "rouge", "red", "rouge"), g3Option("blue", "bleu", "blue", "bleu"), g3Option("yellow", "jaune", "yellow", "jaune"))
    )
)
