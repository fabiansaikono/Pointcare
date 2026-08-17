package be.saikono.poincare.domain.curriculum.v2

/** Concrete, localized evidence contracts for Grade 1 skills that cannot be mastered by taps alone. */
data class Grade1HybridEvidenceProfile(
    val deliveryMode: String,
    val evidenceMode: String,
    val taskNl: String,
    val taskFr: String,
    val taskEn: String,
    val criteriaNl: String,
    val criteriaFr: String,
    val criteriaEn: String,
    val rubric: String
)

object Grade1HybridEvidenceProfiles {
    fun requireFor(unitId: String): Grade1HybridEvidenceProfile = when {
        unitId.contains("fr.listening") -> profile(
            "ADULT_GUIDED_LISTEN_AND_REPEAT", "SPOKEN_PERFORMANCE",
            "Luister naar drie Franse begroetingen en zeg elke begroeting rustig na tegen een volwassene.",
            "Écoute trois salutations françaises et répète calmement chacune devant un adulte.",
            "Listen to three French greetings and calmly repeat each one to an adult.",
            "De begroeting is herkenbaar, het kind luistert eerst en probeert het Franse ritme te volgen.",
            "La salutation est reconnaissable, l'enfant écoute d'abord et essaie de suivre le rythme français.",
            "The greeting is recognisable, the child listens first, and tries to follow the French rhythm.",
            "recognisable_greeting;listens_first;attempts_french_rhythm"
        )
        unitId.contains("fr.culture") -> profile(
            "ADULT_GUIDED_LANGUAGE_PLAY", "SPOKEN_AND_PARTICIPATION_PERFORMANCE",
            "Tel in het Frans mee tot tien en wijs daarna drie genoemde kleuren aan.",
            "Compte en français jusqu'à dix, puis montre trois couleurs nommées.",
            "Count along to ten in French, then point to three named colours.",
            "Het kind telt in de juiste volgorde en herkent minstens twee van de drie kleuren.",
            "L'enfant compte dans le bon ordre et reconnaît au moins deux des trois couleurs.",
            "The child counts in the correct order and recognises at least two of the three colours.",
            "number_sequence;colour_recognition;participates"
        )
        unitId.contains("arts.visual") -> profile(
            "ADULT_GUIDED_CREATIVE_WORK", "CREATED_ARTEFACT_AND_OBSERVATION",
            "Maak een kleine tekening met minstens twee soorten lijnen, twee kleuren en één duidelijke vorm.",
            "Réalise un petit dessin avec au moins deux types de lignes, deux couleurs et une forme claire.",
            "Make a small drawing with at least two kinds of lines, two colours, and one clear shape.",
            "De afgesproken lijnen, kleuren en vorm zijn zichtbaar en het kind kan één keuze aanwijzen.",
            "Les lignes, couleurs et la forme demandées sont visibles et l'enfant peut montrer un de ses choix.",
            "The requested lines, colours, and shape are visible, and the child can point out one choice.",
            "two_line_types;two_colours;clear_shape;explains_choice"
        )
        unitId.contains("arts.music") -> profile(
            "ADULT_GUIDED_MUSIC_PERFORMANCE", "LIVE_RHYTHM_PERFORMANCE",
            "Klap een vaste puls en herhaal daarna een kort ritme van vier klappen.",
            "Frappe une pulsation régulière, puis répète un court rythme de quatre frappes.",
            "Clap a steady beat, then repeat a short four-clap rhythm.",
            "De puls blijft gelijk en de vier klappen worden in dezelfde volgorde herhaald.",
            "La pulsation reste régulière et les quatre frappes sont répétées dans le même ordre.",
            "The beat stays steady and the four claps are repeated in the same order.",
            "steady_beat;four_event_order;listens_before_copying"
        )
        unitId.contains("arts.drama") -> profile(
            "ADULT_GUIDED_DRAMA_PLAY", "LIVE_ROLE_PERFORMANCE",
            "Beeld zonder woorden uit dat je iets zwaars optilt en reageer daarna op een medespeler.",
            "Mime sans parler que tu soulèves quelque chose de lourd, puis réagis à un partenaire.",
            "Act out lifting something heavy without words, then respond to a partner.",
            "De handeling is herkenbaar, het lichaam toont inspanning en het kind reageert op de ander.",
            "L'action est reconnaissable, le corps montre l'effort et l'enfant réagit à l'autre.",
            "The action is recognisable, the body shows effort, and the child responds to the other person.",
            "recognisable_action;body_expression;responds_to_partner"
        )
        unitId.contains("arts.media") -> profile(
            "ADULT_GUIDED_MEDIA_PRESENTATION", "CREATED_ARTEFACT_AND_SHORT_PRESENTATION",
            "Kies één foto of tekening, toon ze aan iemand en vertel in één zin wat belangrijk is.",
            "Choisis une photo ou un dessin, montre-le à quelqu'un et dis en une phrase ce qui est important.",
            "Choose one photo or drawing, show it to someone, and say in one sentence what matters.",
            "Het beeld is bewust gekozen, duidelijk getoond en de zin past bij het beeld.",
            "L'image est choisie volontairement, montrée clairement et la phrase correspond à l'image.",
            "The image is chosen deliberately, shown clearly, and the sentence matches it.",
            "deliberate_choice;visible_presentation;matching_sentence"
        )
        unitId.contains("pe.movement") -> movement(
            "Loop, spring en land op een afgesproken signaal.",
            "Marche, saute et atterris au signal convenu.",
            "Run, jump, and land on the agreed signal.",
            "changes_movement;responds_to_signal;balanced_landing"
        )
        unitId.contains("pe.gymnastics") -> movement(
            "Steun met beide handen, hang kort met hulp en rol veilig over een mat.",
            "Prends appui sur les deux mains, suspends-toi brièvement avec aide et roule en sécurité sur un tapis.",
            "Support your weight on both hands, hang briefly with help, and roll safely on a mat.",
            "two_hand_support;controlled_hang;safe_roll"
        )
        unitId.contains("pe.ball_games") -> movement(
            "Gooi een zachte bal naar een partner, vang hem en rol hem naar een doel.",
            "Lance une balle souple à un partenaire, attrape-la et fais-la rouler vers une cible.",
            "Throw a soft ball to a partner, catch it, and roll it towards a target.",
            "controlled_throw;attempted_catch;rolls_towards_target"
        )
        unitId.contains("pe.dance") -> movement(
            "Beweeg vier tellen op de puls en stop wanneer het afgesproken signaal klinkt.",
            "Bouge pendant quatre temps sur la pulsation et arrête-toi au signal convenu.",
            "Move for four counts with the beat and stop when the agreed signal sounds.",
            "four_counts;keeps_beat;stops_on_signal"
        )
        unitId.contains("pe.health_safety") -> movement(
            "Speel een kort bewegingsspel en pas drie afgesproken veiligheidsregels toe.",
            "Joue à un court jeu de mouvement et applique trois règles de sécurité convenues.",
            "Play a short movement game and follow three agreed safety rules.",
            "follows_space_rule;uses_material_safely;stops_on_signal"
        )
        unitId.contains("ict.devices_data") -> digital(
            "Open met begeleiding een tekenprogramma, maak één lijn en sluit het programma correct af.",
            "Avec de l'aide, ouvre un programme de dessin, trace une ligne et ferme correctement le programme.",
            "With guidance, open a drawing program, make one line, and close the program correctly.",
            "opens_requested_app;creates_line;closes_safely"
        )
        unitId.contains("ict.coding") -> digital(
            "Leg vier opdrachtkaartjes in volgorde en laat een figuur die route stap voor stap volgen.",
            "Place quatre cartes d'instruction dans l'ordre et fais suivre ce trajet à un personnage pas à pas.",
            "Put four instruction cards in order and make a figure follow the route step by step.",
            "four_steps_ordered;route_matches;corrects_one_error"
        )
        unitId.contains("ict.create_communicate") -> digital(
            "Maak met begeleiding een tekening, typ je voornaam erbij en toon het resultaat alleen aan de afgesproken persoon.",
            "Avec de l'aide, réalise un dessin, ajoute ton prénom et montre le résultat uniquement à la personne convenue.",
            "With guidance, make a drawing, add your first name, and show it only to the agreed person.",
            "creates_drawing;types_name;shares_with_agreed_person"
        )
        unitId.contains("ict.safety_ethics") -> digital(
            "Oefen met een volwassene wat je doet wanneer een scherm om je naam, foto of wachtwoord vraagt.",
            "Avec un adulte, entraîne-toi à réagir lorsqu'un écran demande ton nom, ta photo ou ton mot de passe.",
            "Practise with an adult what to do when a screen asks for your name, photo, or password.",
            "does_not_share;stops_action;asks_trusted_adult"
        )
        unitId.contains("history.chronology") -> conversation(
            "Leg drie kaartjes van je dag in de volgorde vroeger, nu en later en vertel de volgorde.",
            "Range trois cartes de ta journée dans l'ordre avant, maintenant et plus tard, puis raconte cet ordre.",
            "Put three cards from your day in before, now, and later order, then tell the order.",
            "three_events_ordered;uses_time_words;explains_order",
            "ORDERED_TIMELINE"
        )
        unitId.contains("history.sources") -> conversation(
            "Bekijk samen een oude foto en noem twee dingen die je ziet en één vraag die de foto niet beantwoordt.",
            "Observe une ancienne photo avec un adulte et nomme deux choses visibles et une question à laquelle la photo ne répond pas.",
            "Look at an old photo with an adult and name two things you see and one question the photo does not answer.",
            "two_observations;one_open_question;separates_seeing_from_guessing",
            "SOURCE_CONVERSATION"
        )
        unitId.contains("history.society") -> conversation(
            "Vergelijk een foto van een klas vroeger met je klas nu en benoem één verschil en één overeenkomst.",
            "Compare une photo d'une classe d'autrefois avec ta classe actuelle et nomme une différence et un point commun.",
            "Compare a photo of a classroom long ago with your classroom now and name one difference and one similarity.",
            "one_difference;one_similarity;uses_past_and_now",
            "PAST_PRESENT_COMPARISON"
        )
        unitId.contains("history.eras") -> conversation(
            "Vraag een volwassene naar een feest van vroeger en vertel daarna één traditie die bleef en één die veranderde.",
            "Interroge un adulte sur une fête d'autrefois, puis raconte une tradition restée la même et une qui a changé.",
            "Ask an adult about a celebration in the past, then tell one tradition that stayed and one that changed.",
            "asks_relevant_question;identifies_continuity;identifies_change",
            "ORAL_HISTORY_COMPARISON"
        )
        unitId.contains("history.heritage") -> conversation(
            "Kies een oud gebouw of voorwerp in je buurt en vertel waarom mensen het bewaren.",
            "Choisis un ancien bâtiment ou objet de ton quartier et explique pourquoi les gens le conservent.",
            "Choose an old building or object nearby and explain why people preserve it.",
            "identifies_heritage;gives_preservation_reason;uses_local_example",
            "HERITAGE_EXPLANATION"
        )
        unitId.contains("geo.maps") -> observation(
            "Teken een eenvoudige plattegrond van je tafel tot aan de deur en leg de route uit.",
            "Dessine un plan simple de ta table jusqu'à la porte et explique le trajet.",
            "Draw a simple plan from your table to the door and explain the route.",
            "start_and_finish;two_landmarks;route_matches_plan",
            "MAP_AND_ROUTE"
        )
        unitId.contains("geo.human") -> observation(
            "Wandel met een volwassene door de buurt en benoem een woning, een schoolplek en een plek voor iedereen.",
            "Promène-toi dans le quartier avec un adulte et nomme un logement, un lieu scolaire et un lieu pour tous.",
            "Walk around the neighbourhood with an adult and name a home, a school place, and a public place.",
            "three_place_types;names_function;stays_with_adult",
            "NEIGHBOURHOOD_OBSERVATION"
        )
        unitId.contains("geo.physical") -> observation(
            "Observeer buiten het weer, kies een passend weersymbool en leg je keuze uit.",
            "Observe le temps dehors, choisis un symbole météo adapté et explique ton choix.",
            "Observe the weather outside, choose a matching weather symbol, and explain your choice.",
            "observes_sky;chooses_matching_symbol;gives_weather_reason",
            "WEATHER_OBSERVATION"
        )
        unitId.contains("geo.environment") -> observation(
            "Kies met een volwassene één stukje van de speelplaats en ruim daar veilig zwerfvuil op.",
            "Avec un adulte, choisis une partie de la cour et ramasse les déchets en toute sécurité.",
            "With an adult, choose one part of the playground and safely pick up litter there.",
            "uses_safe_materials;sorts_found_waste;leaves_area_cleaner",
            "CARE_ACTION"
        )
        unitId.contains("geo.fieldwork") -> observation(
            "Bekijk één plek buiten, noteer met tekeningen drie waarnemingen en vertel wat je nog wilt weten.",
            "Observe un endroit dehors, note trois observations par des dessins et dis ce que tu veux encore savoir.",
            "Observe one outdoor place, record three observations with drawings, and say what you still want to know.",
            "three_observations;drawn_record;one_inquiry_question",
            "FIELDWORK_NOTE"
        )
        unitId.contains("science_tech.organisms.levend") -> observation(
            "Vergelijk buiten een plant, een steen en een gevallen blad en bespreek wat leeft of ooit leefde.",
            "Compare dehors une plante, une pierre et une feuille tombée, puis discute de ce qui vit ou a vécu.",
            "Compare a plant, a stone, and a fallen leaf outside, then discuss what is living or once lived.",
            "classifies_three_objects;uses_growth_or_need_evidence;handles_safely",
            "LIVING_CLASSIFICATION"
        )
        unitId.contains("science_tech.organisms.planten") -> observation(
            "Verzorg zeven dagen een plant en teken op twee dagen wat er veranderde.",
            "Soigne une plante pendant sept jours et dessine deux fois ce qui a changé.",
            "Care for a plant for seven days and draw what changed on two days.",
            "provides_water_as_agreed;two_observation_drawings;names_one_change",
            "PLANT_GROWTH_RECORD"
        )
        unitId.contains("science_tech.organisms.dieren") -> observation(
            "Observeer veilig een klein dier en benoem hoe het beweegt, waar het schuilt en wat het nodig heeft.",
            "Observe un petit animal en sécurité et nomme comment il se déplace, où il s'abrite et ce dont il a besoin.",
            "Safely observe a small animal and name how it moves, where it shelters, and what it needs.",
            "movement_observed;shelter_observed;one_need_named;does_not_disturb",
            "ANIMAL_HABITAT_OBSERVATION"
        )
        unitId.contains("science_tech.human_body") -> observation(
            "Voer met een volwassene drie zintuigproefjes uit en zeg welk zintuig je telkens gebruikt.",
            "Réalise trois petits tests sensoriels avec un adulte et nomme le sens utilisé chaque fois.",
            "Do three sensory tests with an adult and name the sense used each time.",
            "three_safe_tests;correct_sense_named;describes_observation",
            "SENSES_OBSERVATION"
        )
        unitId.contains("science_tech.matter") -> observation(
            "Test met een volwassene drie materialen op buigzaamheid of wateropname en noteer de resultaten.",
            "Avec un adulte, teste trois matériaux pour leur flexibilité ou leur absorption d'eau et note les résultats.",
            "With an adult, test three materials for flexibility or water absorption and record the results.",
            "three_materials;fair_comparison;records_results",
            "MATERIAL_TEST"
        )
        unitId.contains("science_tech.forces_energy") -> observation(
            "Duw en trek hetzelfde speelgoedvoorwerp zacht en harder; vergelijk richting en afgelegde afstand.",
            "Pousse et tire doucement puis plus fort le même jouet ; compare la direction et la distance parcourue.",
            "Push and pull the same toy gently and harder; compare its direction and distance travelled.",
            "uses_same_object;compares_push_and_pull;describes_direction;compares_distance",
            "FORCE_AND_MOTION_TEST"
        )
        unitId.contains("science_tech.technology") -> observation(
            "Bouw met begeleiding een eenvoudige houder voor drie potloden, test hem en verbeter één onderdeel.",
            "Avec de l'aide, construis un support simple pour trois crayons, teste-le et améliore un élément.",
            "With guidance, build a simple holder for three pencils, test it, and improve one part.",
            "meets_purpose;stands_stably;tests_result;makes_improvement",
            "DESIGN_BUILD_TEST"
        )
        unitId.contains("nl.handwriting") -> profile(
            "ADULT_GUIDED_HANDWRITING", "WRITTEN_ARTEFACT_AND_OBSERVATION",
            "Schrijf de aangegeven letters of cijfers op papier volgens het getoonde startpunt en de schrijfrichting.",
            "Écris sur papier les lettres ou chiffres indiqués en respectant le point de départ et le sens du tracé.",
            "Write the indicated letters or numbers on paper using the shown starting point and stroke direction.",
            "Het startpunt, de schrijfrichting, de vorm en de ruimte tussen tekens zijn zichtbaar beoordeeld.",
            "Le point de départ, le sens du tracé, la forme et l'espace entre les signes sont observés.",
            "The starting point, stroke direction, shape, and spacing between marks are observed.",
            "starting_point;stroke_direction;shape;spacing;readability"
        )
        unitId.contains("nl.composition") -> profile(
            "ADULT_GUIDED_WRITING", "INDEPENDENT_WRITTEN_ARTEFACT",
            "Schrijf op papier één eigen zin die begint met een hoofdletter en eindigt met een punt.",
            "Écris sur papier une phrase personnelle qui commence par une majuscule et se termine par un point.",
            "Write one sentence of your own on paper that starts with a capital letter and ends with a full stop.",
            "De zin heeft passende woorden, leesbare letters, spaties, een hoofdletter en een eindpunt.",
            "La phrase contient des mots adaptés, des lettres lisibles, des espaces, une majuscule et un point final.",
            "The sentence has suitable words, readable letters, spaces, a capital letter, and a final full stop.",
            "meaningful_sentence;readable_letters;word_spacing;capital;full_stop"
        )
        unitId.contains("nl.oral") -> profile(
            "ADULT_GUIDED_CONVERSATION", "LIVE_SPEAKING_AND_LISTENING",
            "Vertel in drie zinnen over een gebeurtenis en beantwoord daarna één vraag van je gesprekspartner.",
            "Raconte un événement en trois phrases, puis réponds à une question de ton interlocuteur.",
            "Tell about an event in three sentences, then answer one question from your conversation partner.",
            "De stem is hoorbaar, de zinnen passen bij het onderwerp en het antwoord sluit aan bij de vraag.",
            "La voix est audible, les phrases correspondent au sujet et la réponse correspond à la question.",
            "The voice is audible, the sentences match the topic, and the answer responds to the question.",
            "audible_voice;three_relevant_sentences;answers_partner;turn_taking"
        )
        unitId.contains("citizenship_safety") -> profile(
            "ADULT_GUIDED_ROUTE_PRACTICE", "OBSERVED_SAFE_ROUTE",
            "Oefen met een volwassene één echte oversteek: stop, kijk, luister en steek zichtbaar over op een veilige plek.",
            "Avec un adulte, entraîne-toi à traverser une fois : arrête-toi, regarde, écoute et traverse à un endroit sûr en restant visible.",
            "Practise one real crossing with an adult: stop, look, listen, and cross visibly at a safe place.",
            "Het kind stopt aan de rand, kijkt in beide richtingen, luistert en blijft zichtbaar tijdens het oversteken.",
            "L'enfant s'arrête au bord, regarde dans les deux directions, écoute et reste visible en traversant.",
            "The child stops at the edge, looks both ways, listens, and stays visible while crossing.",
            "stops_at_edge;looks_both_ways;listens;safe_place;visible_crossing"
        )
        unitId.startsWith("unit.g1.attitudes.") -> profile(
            "ADULT_GUIDED_REAL_INTERACTION", "OBSERVED_SOCIAL_BEHAVIOUR",
            "Voer de sociale afspraak van deze les uit tijdens een echt spel- of samenwerkmoment.",
            "Applique la règle sociale de cette leçon pendant un vrai moment de jeu ou de coopération.",
            "Use the social rule from this lesson during a real play or cooperation moment.",
            "Het kind wacht op zijn beurt, gebruikt respectvolle woorden en herstelt de situatie na een herinnering.",
            "L'enfant attend son tour, utilise des mots respectueux et répare la situation après un rappel.",
            "The child waits for a turn, uses respectful words, and repairs the situation after a reminder.",
            "turn_taking;respectful_words;cooperation;repairs_after_prompt"
        )
        else -> error("Missing Grade 1 hybrid evidence profile for $unitId")
    }

    private fun movement(taskNl: String, taskFr: String, taskEn: String, rubric: String) = profile(
        "ADULT_SUPERVISED_MOVEMENT", "OBSERVED_MOVEMENT_PERFORMANCE",
        taskNl, taskFr, taskEn,
        "De beweging gebeurt veilig, op het afgesproken signaal en met controle over het lichaam.",
        "Le mouvement est réalisé en sécurité, au signal convenu et avec contrôle du corps.",
        "The movement is performed safely, on the agreed signal, and with body control.",
        rubric
    )

    private fun digital(taskNl: String, taskFr: String, taskEn: String, rubric: String) = profile(
        "ADULT_SUPERVISED_DEVICE_OR_UNPLUGGED_TASK", "OBSERVED_DIGITAL_TASK",
        taskNl, taskFr, taskEn,
        "Het kind volgt de afgesproken stappen, gebruikt materiaal veilig en vraagt hulp wanneer dat nodig is.",
        "L'enfant suit les étapes convenues, utilise le matériel en sécurité et demande de l'aide si nécessaire.",
        "The child follows the agreed steps, uses materials safely, and asks for help when needed.",
        rubric
    )

    private fun conversation(taskNl: String, taskFr: String, taskEn: String, rubric: String, evidence: String) = profile(
        "ADULT_GUIDED_CONVERSATION", evidence,
        taskNl, taskFr, taskEn,
        "Het kind gebruikt het concrete voorbeeld, noemt waarneembare informatie en legt één verband uit.",
        "L'enfant utilise l'exemple concret, nomme des informations observables et explique un lien.",
        "The child uses the concrete example, names observable information, and explains one connection.",
        rubric
    )

    private fun observation(taskNl: String, taskFr: String, taskEn: String, rubric: String, evidence: String) = profile(
        "ADULT_SUPERVISED_REAL_WORLD_TASK", evidence,
        taskNl, taskFr, taskEn,
        "De opdracht wordt veilig uitgevoerd; waarnemingen en resultaat worden concreet getoond of verteld.",
        "La tâche est réalisée en sécurité ; les observations et le résultat sont montrés ou racontés concrètement.",
        "The task is completed safely; observations and the result are shown or described concretely.",
        rubric
    )

    private fun profile(
        deliveryMode: String,
        evidenceMode: String,
        taskNl: String,
        taskFr: String,
        taskEn: String,
        criteriaNl: String,
        criteriaFr: String,
        criteriaEn: String,
        rubric: String
    ) = Grade1HybridEvidenceProfile(
        deliveryMode, evidenceMode, taskNl, taskFr, taskEn,
        criteriaNl, criteriaFr, criteriaEn, rubric
    )
}
