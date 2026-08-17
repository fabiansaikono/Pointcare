package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 1 citizenship and cooperation generator.
 *
 * Screen exercises assess recognition of fair, respectful and safe choices.
 * Actual cooperation and social behaviour remain part of the adult-observed
 * practical activity and are never inferred from a tap-choice score.
 */
object Grade1CitizenshipCooperationGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g1.world.citizenship.cooperation"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g1.world.citizenship.cooperation"
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
        error("Unable to create a unique Grade 1 citizenship exercise for ${kind.name}")
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
            id = "generated.g1.world.citizenship.cooperation.v1.${seed.fingerprint}.${index + 1}",
            domain = LearningDomain.WORLD_KNOWLEDGE,
            skill = "citizenship_cooperation",
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
                "automatedScope" to "prosocial_scenario_choice",
                "requiresAdultCooperationObservation" to "true",
                "masteryTag" to "citizenship.${candidate.kind.name.lowercase()}",
                "errorTags" to "social.partial_or_unsafe_strategy",
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
                "feedback_nl-BE" to "Kies een oplossing die veilig, eerlijk en respectvol is voor iedereen.",
                "feedback_fr-BE" to "Choisis une solution sûre, juste et respectueuse pour tout le monde.",
                "feedback_en-US" to "Choose a solution that is safe, fair, and respectful for everyone."
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
        PromptKind.FAIR_SHARING -> listOf(
            template(
                "Mila en Noor willen allebei met de blokken spelen. Wat is eerlijk?",
                "Mila et Noor veulent toutes les deux jouer avec les blocs. Qu'est-ce qui est juste ?",
                "Mila and Noor both want to play with the blocks. What is fair?",
                "share",
                choice("share", "De blokken verdelen en samen bouwen.", "Partager les blocs et construire ensemble.", "Share the blocks and build together."),
                choice("take_all", "Alle blokken zelf nemen.", "Prendre tous les blocs pour soi.", "Take all the blocks for yourself."),
                choice("uneven", "Mila bijna alle blokken geven en Noor er maar één geven.", "Donner presque tous les blocs à Mila et un seul à Noor.", "Give Mila almost all the blocks and Noor only one.")
            ),
            template(
                "Er zijn twee stukken fruit voor twee kinderen. Wat is een eerlijke keuze?",
                "Il y a deux fruits pour deux enfants. Quel choix est juste ?",
                "There are two pieces of fruit for two children. What is a fair choice?",
                "one_each",
                choice("one_each", "Ieder kind krijgt één stuk.", "Chaque enfant reçoit un fruit.", "Each child gets one piece."),
                choice("both", "Eén kind neemt ze allebei.", "Un enfant prend les deux.", "One child takes both."),
                choice("throw", "De stukken weggooien.", "Jeter les fruits.", "Throw the pieces away.")
            ),
            template(
                "Drie kinderen willen zes kleurpotloden gebruiken. Wat is eerlijk?",
                "Trois enfants veulent utiliser six crayons de couleur. Qu'est-ce qui est juste ?",
                "Three children want to use six coloured pencils. What is fair?",
                "two_each",
                choice("two_each", "Elk kind krijgt twee potloden en daarna kunnen ze ruilen.", "Chaque enfant reçoit deux crayons et ils peuvent ensuite les échanger.", "Each child gets two pencils and they can swap later."),
                choice("first_all", "Het eerste kind houdt alle zes potloden.", "Le premier enfant garde les six crayons.", "The first child keeps all six pencils."),
                choice("hide", "De potloden verstoppen zodat niemand ze gebruikt.", "Cacher les crayons pour que personne ne les utilise.", "Hide the pencils so nobody can use them.")
            ),
            template(
                "Er zijn vier rollen voor vier spelers. Wat is eerlijk?",
                "Il y a quatre rôles pour quatre joueurs. Qu'est-ce qui est juste ?",
                "There are four roles for four players. What is fair?",
                "one_role_each",
                choice("one_role_each", "Iedere speler kiest of krijgt één rol.", "Chaque joueur choisit ou reçoit un rôle.", "Each player chooses or receives one role."),
                choice("all_roles", "Eén speler neemt alle rollen.", "Un joueur prend tous les rôles.", "One player takes every role."),
                choice("leave_none", "Twee spelers krijgen helemaal geen rol.", "Deux joueurs ne reçoivent aucun rôle.", "Two players get no role at all.")
            )
        )

        PromptKind.TAKE_TURNS -> listOf(
            template(
                "Je wilt ook op de schommel. Wat kun je doen?",
                "Tu veux aussi aller sur la balançoire. Que peux-tu faire ?",
                "You also want to use the swing. What can you do?",
                "wait_turn",
                choice("wait_turn", "Vragen wanneer jij aan de beurt bent en wachten.", "Demander quand ce sera ton tour et attendre.", "Ask when it will be your turn and wait."),
                choice("push", "Het andere kind wegduwen.", "Pousser l'autre enfant.", "Push the other child away."),
                choice("grab", "De schommel meteen vastgrijpen.", "Saisir tout de suite la balançoire.", "Grab the swing right away.")
            ),
            template(
                "Tijdens een spel is iemand anders aan de beurt. Wat doe je?",
                "Pendant un jeu, c'est au tour de quelqu'un d'autre. Que fais-tu ?",
                "During a game, it is someone else's turn. What do you do?",
                "watch_wait",
                choice("watch_wait", "Kijken, luisteren en op je beurt wachten.", "Regarder, écouter et attendre ton tour.", "Watch, listen, and wait for your turn."),
                choice("interrupt", "Steeds tussendoor spelen.", "Jouer sans cesse à la place de l'autre.", "Keep taking turns out of order."),
                choice("quit", "Het spel voor iedereen stoppen.", "Arrêter le jeu pour tout le monde.", "Stop the game for everyone.")
            ),
            template(
                "Twee kinderen willen als eerste de boekhoek kiezen. Wat helpt?",
                "Deux enfants veulent choisir le coin lecture en premier. Qu'est-ce qui aide ?",
                "Two children both want to choose the reading corner first. What helps?",
                "agree_order",
                choice("agree_order", "Een volgorde afspreken en die volgen.", "Décider d'un ordre et le respecter.", "Agree on an order and follow it."),
                choice("shout", "Steeds harder roepen dat jij eerst bent.", "Crier de plus en plus fort que tu es le premier.", "Shout louder and louder that you are first."),
                choice("block", "De doorgang blokkeren voor de ander.", "Bloquer le passage pour l'autre.", "Block the way for the other child.")
            ),
            template(
                "De tablet wordt door twee kinderen gebruikt. Wat doe je na jouw beurt?",
                "La tablette est utilisée par deux enfants. Que fais-tu après ton tour ?",
                "Two children are sharing the tablet. What do you do after your turn?",
                "pass_tablet",
                choice("pass_tablet", "Je bewaart je werk en geeft de tablet rustig door.", "Tu enregistres ton travail et passes calmement la tablette.", "Save your work and pass the tablet calmly."),
                choice("keep_tablet", "Je blijft verder spelen en negeert de afspraak.", "Tu continues à jouer et ignores l'accord.", "Keep playing and ignore the agreement."),
                choice("switch_off", "Je schakelt hem uit zodat de ander niet kan werken.", "Tu l'éteins pour empêcher l'autre de travailler.", "Switch it off so the other child cannot work.")
            )
        )

        PromptKind.HELPING -> listOf(
            template(
                "Sam laat zijn potloden vallen. Wat is behulpzaam?",
                "Sam fait tomber ses crayons. Qu'est-ce qui est serviable ?",
                "Sam drops his pencils. What is helpful?",
                "help_pick_up",
                choice("help_pick_up", "Vragen of je mag helpen oprapen.", "Demander si tu peux aider à les ramasser.", "Ask if you may help pick them up."),
                choice("laugh", "Hem uitlachen.", "Se moquer de lui.", "Laugh at him."),
                choice("kick", "De potloden verder weg schoppen.", "Éloigner les crayons avec le pied.", "Kick the pencils farther away.")
            ),
            template(
                "Een nieuw kind vindt de klas niet. Wat kun je doen?",
                "Un nouvel élève ne trouve pas la classe. Que peux-tu faire ?",
                "A new child cannot find the classroom. What can you do?",
                "show_way",
                choice("show_way", "De weg tonen of samen een volwassene vragen.", "Montrer le chemin ou demander ensemble à un adulte.", "Show the way or ask an adult together."),
                choice("ignore", "Doen alsof je niets hoort.", "Faire comme si tu n'entendais rien.", "Pretend you did not hear."),
                choice("wrong_way", "Met opzet de verkeerde weg wijzen.", "Indiquer exprès le mauvais chemin.", "Deliberately point the wrong way.")
            ),
            template(
                "Lina kan haar jasrits niet sluiten. Wat is behulpzaam?",
                "Lina n'arrive pas à fermer la tirette de sa veste. Qu'est-ce qui est serviable ?",
                "Lina cannot close her coat zip. What is helpful?",
                "ask_help",
                choice("ask_help", "Vragen of ze hulp wil en samen rustig proberen.", "Lui demander si elle veut de l'aide et essayer calmement ensemble.", "Ask whether she wants help and try calmly together."),
                choice("take_over", "De jas zonder te vragen uit haar handen nemen.", "Prendre la veste de ses mains sans demander.", "Take the coat from her without asking."),
                choice("tease", "Zeggen dat ze het nooit zal kunnen.", "Dire qu'elle n'y arrivera jamais.", "Say that she will never manage it.")
            ),
            template(
                "Een klasgenoot mist een blad voor de opdracht. Wat kun je doen?",
                "Un camarade n'a pas de feuille pour l'activité. Que peux-tu faire ?",
                "A classmate is missing a sheet for the task. What can you do?",
                "find_sheet",
                choice("find_sheet", "Samen een extra blad vragen of er één delen.", "Demander ensemble une feuille supplémentaire ou en partager une.", "Ask together for an extra sheet or share one."),
                choice("hide_stack", "De stapel bladen verstoppen.", "Cacher la pile de feuilles.", "Hide the stack of sheets."),
                choice("say_problem", "Zeggen dat het alleen hun probleem is.", "Dire que c'est seulement son problème.", "Say it is only their problem.")
            )
        )

        PromptKind.INCLUDE_OTHERS -> listOf(
            template(
                "Een kind staat alleen tijdens een groepsspel. Wat is vriendelijk?",
                "Un enfant est seul pendant un jeu de groupe. Qu'est-ce qui est gentil ?",
                "A child is alone during a group game. What is kind?",
                "invite",
                choice("invite", "Vragen of het kind wil meedoen.", "Demander à l'enfant s'il veut participer.", "Ask whether the child wants to join."),
                choice("exclude", "Zeggen dat het kind nooit mag meedoen.", "Dire que l'enfant ne pourra jamais participer.", "Say the child may never join."),
                choice("point", "Wijzen en lachen.", "Le montrer du doigt et rire.", "Point and laugh.")
            ),
            template(
                "Je groep kiest een spel. Eén kind kent de regels nog niet. Wat helpt?",
                "Ton groupe choisit un jeu. Un enfant ne connaît pas encore les règles. Qu'est-ce qui aide ?",
                "Your group chooses a game. One child does not know the rules yet. What helps?",
                "explain",
                choice("explain", "De regels rustig uitleggen.", "Expliquer calmement les règles.", "Explain the rules calmly."),
                choice("mock", "Lachen omdat het kind ze niet kent.", "Rire parce que l'enfant ne les connaît pas.", "Laugh because the child does not know them."),
                choice("send_away", "Het kind wegsturen.", "Renvoyer l'enfant.", "Send the child away.")
            ),
            template(
                "Een klasgenoot spreekt nog weinig Nederlands. Hoe laat je die meedoen?",
                "Un camarade parle encore peu le néerlandais. Comment l'aider à participer ?",
                "A classmate speaks little Dutch yet. How do you include them?",
                "show_invite",
                choice("show_invite", "Met een gebaar tonen wat je doet en vriendelijk uitnodigen.", "Montrer par un geste ce que tu fais et l'inviter gentiment.", "Show with a gesture what you are doing and invite them kindly."),
                choice("ignore_language", "Doen alsof de klasgenoot er niet is.", "Faire comme si le camarade n'était pas là.", "Pretend the classmate is not there."),
                choice("copy_mock", "De uitspraak nadoen om te lachen.", "Imiter sa prononciation pour rire.", "Copy their pronunciation to make fun of it.")
            ),
            template(
                "Er komt een nieuw kind bij het bouwspel. Wat helpt?",
                "Un nouvel enfant rejoint le jeu de construction. Qu'est-ce qui aide ?",
                "A new child joins the building game. What helps?",
                "offer_task",
                choice("offer_task", "Vertellen wat jullie bouwen en een passende taak aanbieden.", "Expliquer ce que vous construisez et proposer une tâche adaptée.", "Explain what you are building and offer a suitable task."),
                choice("closed_group", "Zeggen dat de groep voor altijd gesloten is.", "Dire que le groupe est fermé pour toujours.", "Say the group is closed forever."),
                choice("bad_task", "Alleen een onmogelijke taak geven en lachen.", "Donner seulement une tâche impossible et rire.", "Give only an impossible task and laugh.")
            )
        )

        PromptKind.LISTEN_RESPECTFULLY -> listOf(
            template(
                "Iemand vertelt iets aan de kring. Hoe luister je respectvol?",
                "Quelqu'un raconte quelque chose au groupe. Comment écouter avec respect ?",
                "Someone is telling the group something. How do you listen respectfully?",
                "listen",
                choice("listen", "Stil zijn, kijken en de ander laten uitspreken.", "Être calme, regarder et laisser l'autre finir.", "Be quiet, look, and let the other person finish."),
                choice("interrupt", "Meteen antwoorden zodra je denkt te weten wat de ander bedoelt.", "Répondre tout de suite dès que tu penses savoir ce que l'autre veut dire.", "Answer as soon as you think you know what the other person means."),
                choice("look_away", "Naar iets anders kijken en maar een deel van het verhaal beluisteren.", "Regarder ailleurs et n'écouter qu'une partie de l'histoire.", "Look elsewhere and listen to only part of the story.")
            ),
            template(
                "Je begrijpt niet wat je klasgenoot bedoelt. Wat kun je zeggen?",
                "Tu ne comprends pas ce que ton camarade veut dire. Que peux-tu dire ?",
                "You do not understand what your classmate means. What can you say?",
                "ask_again",
                choice("ask_again", "Kun je dat rustig nog eens uitleggen?", "Peux-tu l'expliquer encore une fois calmement ?", "Can you explain that calmly one more time?"),
                choice("pretend", "Doen alsof je het begrijpt en niets vragen.", "Faire semblant de comprendre et ne rien demander.", "Pretend you understand and ask nothing."),
                choice("change_topic", "Over je eigen verhaal beginnen zonder verder te luisteren.", "Commencer à raconter ta propre histoire sans continuer à écouter.", "Start telling your own story without listening further.")
            ),
            template(
                "Een klasgenoot legt een spelregel uit. Wat toont dat je luistert?",
                "Un camarade explique une règle du jeu. Qu'est-ce qui montre que tu écoutes ?",
                "A classmate explains a game rule. What shows that you are listening?",
                "repeat_rule",
                choice("repeat_rule", "De regel in je eigen woorden herhalen en één vraag stellen.", "Répéter la règle avec tes mots et poser une question.", "Repeat the rule in your own words and ask one question."),
                choice("talk_over", "Door de uitleg heen blijven praten.", "Continuer à parler pendant l'explication.", "Keep talking over the explanation."),
                choice("walk_away", "Weglopen voordat de uitleg klaar is.", "Partir avant la fin de l'explication.", "Walk away before the explanation is finished.")
            ),
            template(
                "Je partner vertelt een idee voor de tekening. Wat doe je?",
                "Ton partenaire propose une idée pour le dessin. Que fais-tu ?",
                "Your partner shares an idea for the drawing. What do you do?",
                "hear_idea",
                choice("hear_idea", "Luisteren, het idee samenvatten en daarna jouw idee geven.", "Écouter, résumer son idée, puis donner la tienne.", "Listen, summarize the idea, and then share yours."),
                choice("dismiss", "Meteen zeggen dat alleen jouw idee goed is.", "Dire tout de suite que seule ton idée est bonne.", "Immediately say only your idea is good."),
                choice("ignore", "Beginnen tekenen zonder te reageren.", "Commencer à dessiner sans répondre.", "Start drawing without responding.")
            )
        )

        PromptKind.EXPRESS_FEELINGS -> listOf(
            template(
                "Je bent verdrietig omdat een spel niet lukt. Wat kun je zeggen?",
                "Tu es triste parce qu'un jeu ne réussit pas. Que peux-tu dire ?",
                "You are sad because a game is not working out. What can you say?",
                "name_feeling",
                choice("name_feeling", "Ik ben verdrietig. Wil je me helpen?", "Je suis triste. Veux-tu m'aider ?", "I feel sad. Will you help me?"),
                choice("break", "Ik maak het spel kapot!", "Je vais casser le jeu !", "I will break the game!"),
                choice("blame", "Het is altijd jouw schuld!", "C'est toujours ta faute !", "It is always your fault!")
            ),
            template(
                "Iemand komt te dicht bij je en je vindt dat niet fijn. Wat kun je zeggen?",
                "Quelqu'un s'approche trop près de toi et cela te gêne. Que peux-tu dire ?",
                "Someone comes too close and you do not like it. What can you say?",
                "boundary",
                choice("boundary", "Stop alsjeblieft. Ik wil wat meer ruimte.", "Arrête, s'il te plaît. J'ai besoin de plus d'espace.", "Please stop. I need more space."),
                choice("hit", "Meteen slaan zonder iets te zeggen.", "Frapper tout de suite sans rien dire.", "Hit right away without saying anything."),
                choice("hide_feeling", "Niets zeggen en doen alsof het fijn is.", "Ne rien dire et faire comme si cela te plaisait.", "Say nothing and pretend you like it.")
            ),
            template(
                "Je bent boos omdat je bouwwerk omvalt. Wat kun je doen?",
                "Tu es fâché parce que ta construction tombe. Que peux-tu faire ?",
                "You are angry because your building falls down. What can you do?",
                "calm_words",
                choice("calm_words", "Zeggen dat je boos bent, rustig ademen en opnieuw beginnen.", "Dire que tu es fâché, respirer calmement et recommencer.", "Say you feel angry, breathe calmly, and start again."),
                choice("throw_blocks", "De blokken door de klas gooien.", "Jeter les blocs dans la classe.", "Throw the blocks across the classroom."),
                choice("hurt_friend", "Een vriend de schuld geven en duwen.", "Accuser un ami et le pousser.", "Blame and push a friend.")
            ),
            template(
                "Je bent zenuwachtig voor een korte presentatie. Wat kun je zeggen?",
                "Tu es nerveux avant une courte présentation. Que peux-tu dire ?",
                "You feel nervous before a short presentation. What can you say?",
                "name_nervous",
                choice("name_nervous", "Ik ben zenuwachtig. Mag ik eerst één keer oefenen?", "Je suis nerveux. Puis-je d'abord m'entraîner une fois ?", "I feel nervous. May I practise once first?"),
                choice("mock_self", "Ik kan dit nooit en ik ben dom.", "Je n'y arriverai jamais et je suis nul.", "I can never do this and I am stupid."),
                choice("leave", "Zonder iets te zeggen de klas uitlopen.", "Quitter la classe sans rien dire.", "Walk out of class without saying anything.")
            )
        )

        PromptKind.RESOLVE_CONFLICT -> listOf(
            template(
                "Twee kinderen willen dezelfde stift. Wat kan een rustige oplossing zijn?",
                "Deux enfants veulent le même feutre. Quelle peut être une solution calme ?",
                "Two children want the same marker. What can be a calm solution?",
                "solution",
                choice("solution", "Om de beurt gebruiken of een andere stift kiezen.", "L'utiliser chacun à son tour ou choisir un autre feutre.", "Take turns using it or choose another marker."),
                choice("fight", "Trekkend en duwend vechten.", "Se battre en tirant et en poussant.", "Fight by pulling and pushing."),
                choice("destroy", "De stift breken zodat niemand hem heeft.", "Casser le feutre pour que personne ne l'ait.", "Break the marker so nobody has it.")
            ),
            template(
                "Je botst per ongeluk tegen iemand aan. Wat helpt om het goed te maken?",
                "Tu bouscules quelqu'un par accident. Qu'est-ce qui aide à réparer la situation ?",
                "You accidentally bump into someone. What helps make it right?",
                "apologize",
                choice("apologize", "Sorry zeggen en vragen of alles goed gaat.", "Dire pardon et demander si tout va bien.", "Say sorry and ask whether they are okay."),
                choice("blame", "Zeggen dat het hun schuld is.", "Dire que c'est leur faute.", "Say it is their fault."),
                choice("run", "Weglopen en lachen.", "Partir en courant et rire.", "Run away and laugh.")
            ),
            template(
                "Jij en een klasgenoot willen verschillende spellen spelen. Wat is een oplossing?",
                "Ton camarade et toi voulez jouer à des jeux différents. Quelle est une solution ?",
                "You and a classmate want to play different games. What is a solution?",
                "choose_both",
                choice("choose_both", "Eerst kort het ene spel en daarna het andere spelen.", "Jouer d'abord un peu à un jeu, puis à l'autre.", "Play one game briefly and then the other."),
                choice("insult", "De keuze van de ander belachelijk maken.", "Se moquer du choix de l'autre.", "Make fun of the other person's choice."),
                choice("grab_material", "Al het materiaal voor jouw spel nemen.", "Prendre tout le matériel pour ton jeu.", "Take all the materials for your game.")
            ),
            template(
                "Twee kinderen denken dat zij dezelfde plaats kregen. Wat helpt?",
                "Deux enfants pensent avoir reçu la même place. Qu'est-ce qui aide ?",
                "Two children think they were given the same place. What helps?",
                "check_agreement",
                choice("check_agreement", "Samen de afspraak nakijken en zo nodig een volwassene vragen.", "Vérifier ensemble l'accord et demander un adulte si nécessaire.", "Check the agreement together and ask an adult if needed."),
                choice("push_seat", "De ander van de plaats duwen.", "Pousser l'autre hors de la place.", "Push the other child out of the place."),
                choice("tear_card", "Het kaartje met de afspraak verscheuren.", "Déchirer la carte avec l'accord.", "Tear up the card showing the agreement.")
            )
        )

        PromptKind.SEEK_ADULT_HELP -> listOf(
            template(
                "Iemand blijft je pijn doen nadat je stop hebt gezegd. Wat doe je?",
                "Quelqu'un continue à te faire mal après que tu as dit d'arrêter. Que fais-tu ?",
                "Someone keeps hurting you after you have said stop. What do you do?",
                "trusted_adult",
                choice("trusted_adult", "Weggaan en meteen een vertrouwde volwassene vertellen wat er gebeurt.", "T'éloigner et raconter tout de suite à un adulte de confiance ce qui se passe.", "Move away and immediately tell a trusted adult what is happening."),
                choice("keep_secret", "Het geheimhouden en alleen blijven.", "Garder le secret et rester seul.", "Keep it secret and stay alone."),
                choice("revenge", "Later stiekem wraak nemen.", "Se venger plus tard en cachette.", "Secretly get revenge later.")
            ),
            template(
                "Je ziet dat een kind vaak wordt buitengesloten en verdrietig is. Wat is een veilige keuze?",
                "Tu vois qu'un enfant est souvent exclu et triste. Quel est un choix sûr ?",
                "You see that a child is often left out and is sad. What is a safe choice?",
                "tell_adult",
                choice("tell_adult", "Het kind steunen en samen een vertrouwde volwassene vertellen wat je zag.", "Soutenir l'enfant et raconter ensemble à un adulte de confiance ce que tu as vu.", "Support the child and together tell a trusted adult what you saw."),
                choice("join_in", "Meedoen met buitensluiten.", "Participer à l'exclusion.", "Join in leaving the child out."),
                choice("spread", "Er een grappig verhaal over rondvertellen.", "Raconter une histoire amusante à ce sujet.", "Spread a funny story about it.")
            ),
            template(
                "Je vindt een onbekend voorwerp op de speelplaats. Wat is veilig?",
                "Tu trouves un objet inconnu dans la cour. Qu'est-ce qui est sûr ?",
                "You find an unfamiliar object in the playground. What is safe?",
                "tell_teacher",
                choice("tell_teacher", "Het laten liggen, afstand houden en een volwassene waarschuwen.", "Le laisser, garder ses distances et prévenir un adulte.", "Leave it, keep your distance, and tell an adult."),
                choice("open", "Het voorwerp meteen openen en onderzoeken.", "Ouvrir l'objet tout de suite pour l'examiner.", "Open the object immediately to inspect it."),
                choice("throw", "Het naar andere kinderen gooien.", "Le lancer vers d'autres enfants.", "Throw it toward other children.")
            ),
            template(
                "Een ruzie blijft doorgaan nadat jullie rustig probeerden te praten. Wat nu?",
                "Une dispute continue après avoir essayé de parler calmement. Que faire maintenant ?",
                "An argument continues after you tried to talk calmly. What now?",
                "ask_mediator",
                choice("ask_mediator", "Afstand nemen en een vertrouwde volwassene om hulp vragen.", "Prendre de la distance et demander l'aide d'un adulte de confiance.", "Step away and ask a trusted adult for help."),
                choice("restart_fight", "Opnieuw beginnen roepen en duwen.", "Recommencer à crier et pousser.", "Start shouting and pushing again."),
                choice("secret_plan", "Stiekem plannen maken om terug te pakken.", "Préparer en secret une revanche.", "Secretly plan to get even.")
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
        PromptKind.FAIR_SHARING -> "Eerlijk delen betekent dat iedereen een passende kans of deel krijgt."
        PromptKind.TAKE_TURNS -> "Spreek af wie wanneer aan de beurt is en wacht rustig."
        PromptKind.HELPING -> "Vraag vriendelijk of je kunt helpen zonder iets over te nemen."
        PromptKind.INCLUDE_OTHERS -> "Nodig anderen uit en leg regels rustig uit."
        PromptKind.LISTEN_RESPECTFULLY -> "Laat de ander uitspreken en vraag verduidelijking als iets niet duidelijk is."
        PromptKind.EXPRESS_FEELINGS -> "Zeg rustig wat je voelt en wat je nodig hebt."
        PromptKind.RESOLVE_CONFLICT -> "Zoek een oplossing zonder slaan, duwen, schelden of spullen kapot te maken."
        PromptKind.SEEK_ADULT_HELP -> "Bij pijn, dreiging of herhaald buitensluiten ga je weg en vraag je hulp aan een vertrouwde volwassene."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.FAIR_SHARING -> "Partager équitablement signifie que chacun reçoit une part ou une chance adaptée."
        PromptKind.TAKE_TURNS -> "Décidez qui joue quand et attends calmement ton tour."
        PromptKind.HELPING -> "Demande gentiment si tu peux aider sans tout faire à la place de l'autre."
        PromptKind.INCLUDE_OTHERS -> "Invite les autres et explique calmement les règles."
        PromptKind.LISTEN_RESPECTFULLY -> "Laisse l'autre finir et demande une explication si quelque chose n'est pas clair."
        PromptKind.EXPRESS_FEELINGS -> "Dis calmement ce que tu ressens et ce dont tu as besoin."
        PromptKind.RESOLVE_CONFLICT -> "Cherche une solution sans frapper, pousser, insulter ni casser des objets."
        PromptKind.SEEK_ADULT_HELP -> "En cas de douleur, de menace ou d'exclusion répétée, éloigne-toi et demande l'aide d'un adulte de confiance."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.FAIR_SHARING -> "Fair sharing means everyone gets a suitable part or chance."
        PromptKind.TAKE_TURNS -> "Agree who goes when and wait calmly for your turn."
        PromptKind.HELPING -> "Kindly ask whether you can help without taking over."
        PromptKind.INCLUDE_OTHERS -> "Invite others and explain rules calmly."
        PromptKind.LISTEN_RESPECTFULLY -> "Let the other person finish and ask for clarification when something is unclear."
        PromptKind.EXPRESS_FEELINGS -> "Calmly say how you feel and what you need."
        PromptKind.RESOLVE_CONFLICT -> "Look for a solution without hitting, pushing, insulting, or breaking things."
        PromptKind.SEEK_ADULT_HELP -> "When there is harm, a threat, or repeated exclusion, move away and ask a trusted adult for help."
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
        FAIR_SHARING,
        TAKE_TURNS,
        HELPING,
        INCLUDE_OTHERS,
        LISTEN_RESPECTFULLY,
        EXPRESS_FEELINGS,
        RESOLVE_CONFLICT,
        SEEK_ADULT_HELP
    }
}
