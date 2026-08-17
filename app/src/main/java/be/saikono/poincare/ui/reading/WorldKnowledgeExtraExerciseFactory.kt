package be.saikono.poincare.ui.reading

import be.saikono.poincare.model.*
import kotlin.random.Random

object WorldKnowledgeExtraExerciseFactory {
    private data class L(val nl: String, val en: String, val fr: String)
    private data class Record(val subject: L, val answer: L)
    private data class LevelData(val question: L, val records: List<Record>)

    private val banks: Map<String, List<LevelData>> = mapOf(
        "animals" to listOf(
            LevelData(
                L("Welk geluid maakt {s}?", "What sound does {s} make?", "Quel son fait {s} ?"),
                listOf(
                    Record(L("hond", "dog", "chien"), L("woef", "woof", "ouaf")),
                    Record(L("kat", "cat", "chat"), L("miauw", "meow", "miaou")),
                    Record(L("koe", "cow", "vache"), L("boe", "moo", "meuh")),
                    Record(L("eend", "duck", "canard"), L("kwak", "quack", "coin-coin"))
                )
            ),
            LevelData(
                L("Waar leeft {s}?", "Where does {s} live?", "Où vit {s} ?"),
                listOf(
                    Record(L("vis", "fish", "poisson"), L("water", "water", "eau")),
                    Record(L("mol", "mole", "taupe"), L("onder de grond", "underground", "sous terre")),
                    Record(L("uil", "owl", "hibou"), L("bos", "forest", "forêt")),
                    Record(L("kameel", "camel", "chameau"), L("woestijn", "desert", "désert"))
                )
            ),
            LevelData(
                L("Tot welke diergroep behoort {s}?", "Which animal group does {s} belong to?", "À quel groupe appartient {s} ?"),
                listOf(
                    Record(L("kikker", "frog", "grenouille"), L("amfibieën", "amphibians", "amphibiens")),
                    Record(L("adelaar", "eagle", "aigle"), L("vogels", "birds", "oiseaux")),
                    Record(L("dolfijn", "dolphin", "dauphin"), L("zoogdieren", "mammals", "mammifères")),
                    Record(L("slang", "snake", "serpent"), L("reptielen", "reptiles", "reptiles"))
                )
            ),
            LevelData(
                L("Welke aanpassing helpt {s}?", "Which adaptation helps {s}?", "Quelle adaptation aide {s} ?"),
                listOf(
                    Record(L("ijsbeer", "polar bear", "ours polaire"), L("dikke vacht", "thick fur", "fourrure épaisse")),
                    Record(L("eend", "duck", "canard"), L("zwemvliezen", "webbed feet", "pattes palmées")),
                    Record(L("kameel", "camel", "chameau"), L("vet in de bult", "fat in its hump", "graisse dans la bosse")),
                    Record(L("uil", "owl", "hibou"), L("nachtzicht", "night vision", "vision nocturne"))
                )
            ),
            LevelData(
                L("Welke rol heeft {s} in een voedselketen?", "What role does {s} have in a food chain?", "Quel rôle a {s} dans une chaîne alimentaire ?"),
                listOf(
                    Record(L("gras", "grass", "herbe"), L("producent", "producer", "producteur")),
                    Record(L("konijn", "rabbit", "lapin"), L("planteneter", "herbivore", "herbivore")),
                    Record(L("vos", "fox", "renard"), L("roofdier", "predator", "prédateur")),
                    Record(L("schimmel", "fungus", "champignon"), L("afbreker", "decomposer", "décomposeur"))
                )
            ),
            LevelData(
                L("Welk biologisch begrip past bij {s}?", "Which biological concept fits {s}?", "Quel concept biologique correspond à {s} ?"),
                listOf(
                    Record(L("natuurlijke selectie", "natural selection", "sélection naturelle"), L("evolutie", "evolution", "évolution")),
                    Record(L("soortenrijkdom", "biodiversity", "biodiversité"), L("biodiversiteit", "biodiversity", "biodiversité")),
                    Record(L("trek van vogels", "bird migration", "migration des oiseaux"), L("migratie", "migration", "migration")),
                    Record(L("winterrust", "hibernation", "hibernation"), L("energiebesparing", "energy conservation", "économie d’énergie"))
                )
            )
        ),
        "human_body" to listOf(
            LevelData(
                L("Waarvoor gebruik je {s}?", "What do you use {s} for?", "À quoi sert {s} ?"),
                listOf(
                    Record(L("ogen", "eyes", "yeux"), L("zien", "seeing", "voir")),
                    Record(L("oren", "ears", "oreilles"), L("horen", "hearing", "entendre")),
                    Record(L("neus", "nose", "nez"), L("ruiken", "smelling", "sentir")),
                    Record(L("tong", "tongue", "langue"), L("proeven", "tasting", "goûter"))
                )
            ),
            LevelData(
                L("Welk orgaan hoort bij {s}?", "Which organ belongs to {s}?", "Quel organe correspond à {s} ?"),
                listOf(
                    Record(L("ademhalen", "breathing", "respirer"), L("longen", "lungs", "poumons")),
                    Record(L("bloed rondpompen", "pumping blood", "pomper le sang"), L("hart", "heart", "cœur")),
                    Record(L("denken", "thinking", "penser"), L("hersenen", "brain", "cerveau")),
                    Record(L("verteren", "digesting", "digérer"), L("maag", "stomach", "estomac"))
                )
            ),
            LevelData(
                L("Welk stelsel doet vooral {s}?", "Which system mainly does {s}?", "Quel système fait surtout {s} ?"),
                listOf(
                    Record(L("zuurstof vervoeren", "transport oxygen", "transporter l’oxygène"), L("bloedsomloop", "circulatory system", "système circulatoire")),
                    Record(L("voedsel afbreken", "break down food", "décomposer les aliments"), L("spijsvertering", "digestive system", "système digestif")),
                    Record(L("bewegen", "move", "bouger"), L("spierstelsel", "muscular system", "système musculaire")),
                    Record(L("prikkels verwerken", "process signals", "traiter les signaux"), L("zenuwstelsel", "nervous system", "système nerveux"))
                )
            ),
            LevelData(
                L("Wat helpt het lichaam bij {s}?", "What helps the body with {s}?", "Qu’est-ce qui aide le corps à {s} ?"),
                listOf(
                    Record(L("botten beschermen", "protect bones", "protéger les os"), L("gewrichten en spieren", "joints and muscles", "articulations et muscles")),
                    Record(L("temperatuur regelen", "control temperature", "réguler la température"), L("zweten", "sweating", "transpiration")),
                    Record(L("ziekte bestrijden", "fight disease", "combattre la maladie"), L("afweersysteem", "immune system", "système immunitaire")),
                    Record(L("wonden sluiten", "close wounds", "fermer les plaies"), L("bloedstolling", "blood clotting", "coagulation"))
                )
            ),
            LevelData(
                L("Welke stof hoort bij {s}?", "Which substance belongs to {s}?", "Quelle substance correspond à {s} ?"),
                listOf(
                    Record(L("energie uit voeding", "energy from food", "énergie des aliments"), L("glucose", "glucose", "glucose")),
                    Record(L("zuurstoftransport", "oxygen transport", "transport de l’oxygène"), L("hemoglobine", "hemoglobin", "hémoglobine")),
                    Record(L("sterke botten", "strong bones", "os solides"), L("calcium", "calcium", "calcium")),
                    Record(L("lichaamsopbouw", "body building", "construction du corps"), L("eiwitten", "proteins", "protéines"))
                )
            ),
            LevelData(
                L("Welk gezondheidsprincipe past bij {s}?", "Which health principle fits {s}?", "Quel principe de santé correspond à {s} ?"),
                listOf(
                    Record(L("regelmatig bewegen", "regular exercise", "activité régulière"), L("conditie", "fitness", "condition physique")),
                    Record(L("voldoende slapen", "enough sleep", "sommeil suffisant"), L("herstel", "recovery", "récupération")),
                    Record(L("gevarieerd eten", "varied diet", "alimentation variée"), L("evenwichtige voeding", "balanced nutrition", "alimentation équilibrée")),
                    Record(L("handen wassen", "washing hands", "se laver les mains"), L("infectiepreventie", "infection prevention", "prévention des infections"))
                )
            )
        ),
        "plants" to listOf(
            LevelData(
                L("Welk deel van de plant is {s}?", "Which plant part is {s}?", "Quelle partie de la plante est {s} ?"),
                listOf(
                    Record(L("onder de grond", "underground", "sous terre"), L("wortel", "root", "racine")),
                    Record(L("groen en plat", "green and flat", "verte et plate"), L("blad", "leaf", "feuille")),
                    Record(L("kleurrijk en geurig", "colorful and scented", "colorée et parfumée"), L("bloem", "flower", "fleur")),
                    Record(L("stevig en rechtop", "firm and upright", "ferme et droite"), L("stengel", "stem", "tige"))
                )
            ),
            LevelData(
                L("Wat heeft een plant nodig voor {s}?", "What does a plant need for {s}?", "De quoi une plante a-t-elle besoin pour {s} ?"),
                listOf(
                    Record(L("groeien", "grow", "grandir"), L("water en licht", "water and light", "eau et lumière")),
                    Record(L("wortels maken", "make roots", "faire des racines"), L("bodem", "soil", "sol")),
                    Record(L("zaad vormen", "make seeds", "former des graines"), L("bestuiving", "pollination", "pollinisation")),
                    Record(L("suiker maken", "make sugar", "fabriquer du sucre"), L("zonlicht", "sunlight", "lumière solaire"))
                )
            ),
            LevelData(
                L("Welk proces hoort bij {s}?", "Which process belongs to {s}?", "Quel processus correspond à {s} ?"),
                listOf(
                    Record(L("licht omzetten in voedsel", "turn light into food", "transformer la lumière en nourriture"), L("fotosynthese", "photosynthesis", "photosynthèse")),
                    Record(L("stuifmeel overbrengen", "transfer pollen", "transporter le pollen"), L("bestuiving", "pollination", "pollinisation")),
                    Record(L("zaad begint te groeien", "seed starts growing", "la graine commence à pousser"), L("kieming", "germination", "germination")),
                    Record(L("water verliezen via bladeren", "lose water through leaves", "perdre de l’eau par les feuilles"), L("verdamping", "transpiration", "transpiration"))
                )
            ),
            LevelData(
                L("Welke aanpassing past bij {s}?", "Which adaptation fits {s}?", "Quelle adaptation correspond à {s} ?"),
                listOf(
                    Record(L("cactus", "cactus", "cactus"), L("water opslaan", "store water", "stocker l’eau")),
                    Record(L("klimplant", "climbing plant", "plante grimpante"), L("steun zoeken", "seek support", "chercher un support")),
                    Record(L("waterlelie", "water lily", "nénuphar"), L("drijvende bladeren", "floating leaves", "feuilles flottantes")),
                    Record(L("den", "pine tree", "pin"), L("naalden tegen uitdroging", "needles reduce water loss", "aiguilles contre le dessèchement"))
                )
            ),
            LevelData(
                L("Welke rol heeft {s} in het ecosysteem?", "What role does {s} have in the ecosystem?", "Quel rôle a {s} dans l’écosystème ?"),
                listOf(
                    Record(L("bloemplant", "flowering plant", "plante à fleurs"), L("nectar leveren", "provide nectar", "fournir du nectar")),
                    Record(L("boom", "tree", "arbre"), L("koolstof opslaan", "store carbon", "stocker du carbone")),
                    Record(L("gras", "grass", "herbe"), L("bodem bedekken", "cover soil", "couvrir le sol")),
                    Record(L("haag", "hedge", "haie"), L("schuilplaats bieden", "provide shelter", "offrir un abri"))
                )
            ),
            LevelData(
                L("Welk begrip past bij {s}?", "Which concept fits {s}?", "Quel concept correspond à {s} ?"),
                listOf(
                    Record(L("erfelijke verschillen", "inherited differences", "différences héréditaires"), L("variatie", "variation", "variation")),
                    Record(L("plant reageert op licht", "plant responds to light", "plante réagit à la lumière"), L("tropisme", "tropism", "tropisme")),
                    Record(L("uitwisseling van gassen", "gas exchange", "échange de gaz"), L("huidmondjes", "stomata", "stomates")),
                    Record(L("samenleven met schimmel", "living with fungus", "vivre avec un champignon"), L("symbiose", "symbiosis", "symbiose"))
                )
            )
        ),
        "weather_climate" to listOf(
            LevelData(
                L("Welk weer hoort bij {s}?", "Which weather belongs to {s}?", "Quel temps correspond à {s} ?"),
                listOf(
                    Record(L("donkere wolken", "dark clouds", "nuages sombres"), L("regen", "rain", "pluie")),
                    Record(L("witte vlokken", "white flakes", "flocons blancs"), L("sneeuw", "snow", "neige")),
                    Record(L("bewegende lucht", "moving air", "air en mouvement"), L("wind", "wind", "vent")),
                    Record(L("heldere hemel", "clear sky", "ciel dégagé"), L("zon", "sun", "soleil"))
                )
            ),
            LevelData(
                L("Welk instrument meet {s}?", "Which instrument measures {s}?", "Quel instrument mesure {s} ?"),
                listOf(
                    Record(L("temperatuur", "temperature", "température"), L("thermometer", "thermometer", "thermomètre")),
                    Record(L("regen", "rainfall", "pluie"), L("regenmeter", "rain gauge", "pluviomètre")),
                    Record(L("windsnelheid", "wind speed", "vitesse du vent"), L("anemometer", "anemometer", "anémomètre")),
                    Record(L("luchtdruk", "air pressure", "pression de l’air"), L("barometer", "barometer", "baromètre"))
                )
            ),
            LevelData(
                L("Welk begrip past bij {s}?", "Which concept fits {s}?", "Quel concept correspond à {s} ?"),
                listOf(
                    Record(L("weer van vandaag", "today’s weather", "temps d’aujourd’hui"), L("weer", "weather", "météo")),
                    Record(L("gemiddelde over vele jaren", "average over many years", "moyenne sur de nombreuses années"), L("klimaat", "climate", "climat")),
                    Record(L("waterdamp wordt druppels", "water vapor becomes droplets", "la vapeur devient gouttes"), L("condensatie", "condensation", "condensation")),
                    Record(L("water stijgt als damp", "water rises as vapor", "l’eau monte en vapeur"), L("verdamping", "evaporation", "évaporation"))
                )
            ),
            LevelData(
                L("Welke luchtmassa brengt vaak {s}?", "Which air mass often brings {s}?", "Quelle masse d’air apporte souvent {s} ?"),
                listOf(
                    Record(L("zachte vochtige lucht", "mild moist air", "air doux et humide"), L("maritieme lucht", "maritime air", "air maritime")),
                    Record(L("droge koude lucht", "dry cold air", "air sec et froid"), L("continentale lucht", "continental air", "air continental")),
                    Record(L("wisselvallig weer", "changeable weather", "temps changeant"), L("front", "front", "front")),
                    Record(L("sterke stijgende lucht", "strong rising air", "air ascendant fort"), L("onweer", "thunderstorm", "orage"))
                )
            ),
            LevelData(
                L("Welk gevolg hoort bij {s}?", "Which effect belongs to {s}?", "Quelle conséquence correspond à {s} ?"),
                listOf(
                    Record(L("meer broeikasgassen", "more greenhouse gases", "plus de gaz à effet de serre"), L("opwarming", "warming", "réchauffement")),
                    Record(L("langdurig weinig regen", "long period with little rain", "longue période sans pluie"), L("droogte", "drought", "sécheresse")),
                    Record(L("veel regen in korte tijd", "much rain in a short time", "beaucoup de pluie en peu de temps"), L("overstroming", "flooding", "inondation")),
                    Record(L("warme stad", "warm city", "ville chaude"), L("hitte-eiland", "heat island", "îlot de chaleur"))
                )
            ),
            LevelData(
                L("Welke maatregel helpt tegen {s}?", "Which measure helps against {s}?", "Quelle mesure aide contre {s} ?"),
                listOf(
                    Record(L("hitte in de stad", "urban heat", "chaleur urbaine"), L("meer bomen", "more trees", "plus d’arbres")),
                    Record(L("wateroverlast", "flooding", "inondations"), L("water bufferen", "store water", "retenir l’eau")),
                    Record(L("CO2-uitstoot", "CO2 emissions", "émissions de CO2"), L("hernieuwbare energie", "renewable energy", "énergie renouvelable")),
                    Record(L("droogte", "drought", "sécheresse"), L("zuinig watergebruik", "save water", "économiser l’eau"))
                )
            )
        ),
        "technology" to listOf(
            LevelData(
                L("Waarvoor gebruik je {s}?", "What do you use {s} for?", "À quoi sert {s} ?"),
                listOf(
                    Record(L("toetsenbord", "keyboard", "clavier"), L("typen", "typing", "taper")),
                    Record(L("camera", "camera", "caméra"), L("foto maken", "take a photo", "prendre une photo")),
                    Record(L("luidspreker", "speaker", "haut-parleur"), L("geluid afspelen", "play sound", "diffuser du son")),
                    Record(L("batterij", "battery", "batterie"), L("energie opslaan", "store energy", "stocker l’énergie"))
                )
            ),
            LevelData(
                L("Welk onderdeel hoort bij {s}?", "Which component belongs to {s}?", "Quel composant correspond à {s} ?"),
                listOf(
                    Record(L("computergeheugen", "computer memory", "mémoire informatique"), L("gegevens bewaren", "store data", "conserver des données")),
                    Record(L("sensor", "sensor", "capteur"), L("meten", "measure", "mesurer")),
                    Record(L("motor", "motor", "moteur"), L("bewegen", "move", "bouger")),
                    Record(L("schakelaar", "switch", "interrupteur"), L("stroom aan of uit", "turn current on or off", "allumer ou couper le courant"))
                )
            ),
            LevelData(
                L("Welk digitaal begrip past bij {s}?", "Which digital concept fits {s}?", "Quel concept numérique correspond à {s} ?"),
                listOf(
                    Record(L("geheime toegangscode", "secret access code", "code d’accès secret"), L("wachtwoord", "password", "mot de passe")),
                    Record(L("stappenplan voor een computer", "steps for a computer", "étapes pour un ordinateur"), L("algoritme", "algorithm", "algorithme")),
                    Record(L("verbonden computers", "connected computers", "ordinateurs connectés"), L("netwerk", "network", "réseau")),
                    Record(L("kopie van gegevens", "copy of data", "copie des données"), L("back-up", "backup", "sauvegarde"))
                )
            ),
            LevelData(
                L("Welke energieomzetting gebeurt in {s}?", "Which energy conversion happens in {s}?", "Quelle conversion d’énergie a lieu dans {s} ?"),
                listOf(
                    Record(L("lamp", "lamp", "lampe"), L("elektrisch naar licht", "electrical to light", "électrique vers lumière")),
                    Record(L("motor", "motor", "moteur"), L("elektrisch naar beweging", "electrical to motion", "électrique vers mouvement")),
                    Record(L("zonnepaneel", "solar panel", "panneau solaire"), L("licht naar elektrisch", "light to electrical", "lumière vers électrique")),
                    Record(L("microfoon", "microphone", "microphone"), L("geluid naar elektrisch", "sound to electrical", "son vers électrique"))
                )
            ),
            LevelData(
                L("Welk ontwerpprincipe past bij {s}?", "Which design principle fits {s}?", "Quel principe de conception correspond à {s} ?"),
                listOf(
                    Record(L("veilig gebruiken", "safe use", "usage sûr"), L("beveiliging", "safety", "sécurité")),
                    Record(L("makkelijk herstellen", "easy to repair", "facile à réparer"), L("herstelbaarheid", "repairability", "réparabilité")),
                    Record(L("weinig energie gebruiken", "use little energy", "utiliser peu d’énergie"), L("efficiëntie", "efficiency", "efficacité")),
                    Record(L("geschikt voor iedereen", "suitable for everyone", "adapté à tous"), L("toegankelijkheid", "accessibility", "accessibilité"))
                )
            ),
            LevelData(
                L("Welk maatschappelijk begrip past bij {s}?", "Which social concept fits {s}?", "Quel concept social correspond à {s} ?"),
                listOf(
                    Record(L("persoonlijke gegevens", "personal data", "données personnelles"), L("privacy", "privacy", "vie privée")),
                    Record(L("nepbericht", "false message", "fausse information"), L("desinformatie", "misinformation", "désinformation")),
                    Record(L("machine leert uit voorbeelden", "machine learns from examples", "machine apprend par exemples"), L("kunstmatige intelligentie", "artificial intelligence", "intelligence artificielle")),
                    Record(L("oude apparaten hergebruiken", "reuse old devices", "réutiliser les appareils"), L("circulaire technologie", "circular technology", "technologie circulaire"))
                )
            )
        ),
        "arts_culture" to listOf(
            LevelData(
                L("Welke kunstvorm hoort bij {s}?", "Which art form belongs to {s}?", "Quelle forme d’art correspond à {s} ?"),
                listOf(
                    Record(L("verf en doek", "paint and canvas", "peinture et toile"), L("schilderkunst", "painting", "peinture")),
                    Record(L("beweging op muziek", "movement to music", "mouvement sur musique"), L("dans", "dance", "danse")),
                    Record(L("rollen op een podium", "roles on a stage", "rôles sur scène"), L("theater", "theatre", "théâtre")),
                    Record(L("klanken en ritme", "sounds and rhythm", "sons et rythme"), L("muziek", "music", "musique"))
                )
            ),
            LevelData(
                L("Welk element past bij {s}?", "Which element fits {s}?", "Quel élément correspond à {s} ?"),
                listOf(
                    Record(L("muziek", "music", "musique"), L("ritme", "rhythm", "rythme")),
                    Record(L("schilderij", "painting", "tableau"), L("kleur", "color", "couleur")),
                    Record(L("verhaal", "story", "histoire"), L("personage", "character", "personnage")),
                    Record(L("gebouw", "building", "bâtiment"), L("vorm", "shape", "forme"))
                )
            ),
            LevelData(
                L("Welk erfgoed hoort bij {s}?", "Which heritage belongs to {s}?", "Quel patrimoine correspond à {s} ?"),
                listOf(
                    Record(L("oude gebouwen", "old buildings", "anciens bâtiments"), L("materieel erfgoed", "tangible heritage", "patrimoine matériel")),
                    Record(L("tradities", "traditions", "traditions"), L("immaterieel erfgoed", "intangible heritage", "patrimoine immatériel")),
                    Record(L("schilderijen in museum", "paintings in a museum", "tableaux au musée"), L("kunstcollectie", "art collection", "collection d’art")),
                    Record(L("lokale verhalen", "local stories", "récits locaux"), L("mondeling erfgoed", "oral heritage", "patrimoine oral"))
                )
            ),
            LevelData(
                L("Welke stijlkenmerk past bij {s}?", "Which style feature fits {s}?", "Quel trait de style correspond à {s} ?"),
                listOf(
                    Record(L("symmetrische gevel", "symmetrical façade", "façade symétrique"), L("klassiek", "classical", "classique")),
                    Record(L("abstracte vormen", "abstract shapes", "formes abstraites"), L("modern", "modern", "moderne")),
                    Record(L("veel versiering", "much decoration", "beaucoup de décoration"), L("barok", "baroque", "baroque")),
                    Record(L("eenvoudige lijnen", "simple lines", "lignes simples"), L("minimalistisch", "minimalist", "minimaliste"))
                )
            ),
            LevelData(
                L("Welke functie heeft kunst bij {s}?", "What function does art have in {s}?", "Quelle fonction l’art a-t-il dans {s} ?"),
                listOf(
                    Record(L("herdenken", "commemorate", "commémorer"), L("herinneren", "remember", "se souvenir")),
                    Record(L("protesteren", "protest", "protester"), L("mening uiten", "express an opinion", "exprimer une opinion")),
                    Record(L("feest vieren", "celebrate", "célébrer"), L("verbinden", "connect people", "rassembler")),
                    Record(L("verhaal vertellen", "tell a story", "raconter une histoire"), L("betekenis geven", "create meaning", "donner du sens"))
                )
            ),
            LevelData(
                L("Welk analysebegrip past bij {s}?", "Which analysis concept fits {s}?", "Quel concept d’analyse correspond à {s} ?"),
                listOf(
                    Record(L("wie het werk maakte", "who made the work", "qui a créé l’œuvre"), L("maker", "creator", "créateur")),
                    Record(L("voor wie het bedoeld is", "who it is intended for", "à qui l’œuvre est destinée"), L("publiek", "audience", "public")),
                    Record(L("wanneer het gemaakt is", "when it was made", "quand elle a été créée"), L("context", "context", "contexte")),
                    Record(L("wat het wil zeggen", "what it means", "ce qu’elle veut dire"), L("interpretatie", "interpretation", "interprétation"))
                )
            )
        ),
        "citizenship_society" to listOf(
            LevelData(
                L("Welke regel past bij {s}?", "Which rule fits {s}?", "Quelle règle correspond à {s} ?"),
                listOf(
                    Record(L("oversteken", "crossing the road", "traverser"), L("kijk links en rechts", "look both ways", "regarder des deux côtés")),
                    Record(L("samen spelen", "playing together", "jouer ensemble"), L("beurt nemen", "take turns", "attendre son tour")),
                    Record(L("afval", "waste", "déchets"), L("in de juiste bak", "use the correct bin", "dans la bonne poubelle")),
                    Record(L("bibliotheek", "library", "bibliothèque"), L("stil zijn", "be quiet", "rester calme"))
                )
            ),
            LevelData(
                L("Welke dienst helpt bij {s}?", "Which service helps with {s}?", "Quel service aide pour {s} ?"),
                listOf(
                    Record(L("brand", "fire", "incendie"), L("brandweer", "fire service", "pompiers")),
                    Record(L("ziekte", "illness", "maladie"), L("dokter", "doctor", "médecin")),
                    Record(L("veiligheid", "safety", "sécurité"), L("politie", "police", "police")),
                    Record(L("boeken lenen", "borrowing books", "emprunter des livres"), L("bibliotheek", "library", "bibliothèque"))
                )
            ),
            LevelData(
                L("Welk recht hoort bij {s}?", "Which right belongs to {s}?", "Quel droit correspond à {s} ?"),
                listOf(
                    Record(L("naar school gaan", "go to school", "aller à l’école"), L("recht op onderwijs", "right to education", "droit à l’éducation")),
                    Record(L("mening geven", "give an opinion", "donner son avis"), L("vrije meningsuiting", "freedom of expression", "liberté d’expression")),
                    Record(L("veilig wonen", "live safely", "vivre en sécurité"), L("recht op bescherming", "right to protection", "droit à la protection")),
                    Record(L("gezondheidszorg", "healthcare", "soins de santé"), L("recht op zorg", "right to care", "droit aux soins"))
                )
            ),
            LevelData(
                L("Welke bestuurslaag hoort bij {s}?", "Which level of government fits {s}?", "Quel niveau de pouvoir correspond à {s} ?"),
                listOf(
                    Record(L("lokale straat", "local street", "rue locale"), L("gemeente", "municipality", "commune")),
                    Record(L("onderwijsbeleid", "education policy", "politique éducative"), L("gemeenschap", "community", "communauté")),
                    Record(L("nationale wetten", "national laws", "lois nationales"), L("federale staat", "federal state", "État fédéral")),
                    Record(L("Europese regels", "European rules", "règles européennes"), L("Europese Unie", "European Union", "Union européenne"))
                )
            ),
            LevelData(
                L("Welk democratisch begrip past bij {s}?", "Which democratic concept fits {s}?", "Quel concept démocratique correspond à {s} ?"),
                listOf(
                    Record(L("stem uitbrengen", "cast a vote", "voter"), L("verkiezing", "election", "élection")),
                    Record(L("verschillende meningen", "different opinions", "opinions différentes"), L("debat", "debate", "débat")),
                    Record(L("iedereen gelijk behandelen", "treat everyone equally", "traiter chacun également"), L("gelijkheid", "equality", "égalité")),
                    Record(L("regels controleren", "check rules", "contrôler les règles"), L("rechtsstaat", "rule of law", "État de droit"))
                )
            ),
            LevelData(
                L("Welk maatschappelijk principe past bij {s}?", "Which social principle fits {s}?", "Quel principe social correspond à {s} ?"),
                listOf(
                    Record(L("hulp aan wie het nodig heeft", "help for those who need it", "aide à ceux qui en ont besoin"), L("solidariteit", "solidarity", "solidarité")),
                    Record(L("verschillen respecteren", "respect differences", "respecter les différences"), L("diversiteit", "diversity", "diversité")),
                    Record(L("beslissingen uitleggen", "explain decisions", "expliquer les décisions"), L("transparantie", "transparency", "transparence")),
                    Record(L("verantwoordelijkheid nemen", "take responsibility", "assumer ses responsabilités"), L("burgerschap", "citizenship", "citoyenneté"))
                )
            )
        ),
        "environment_sustainability" to listOf(
            LevelData(
                L("Waar hoort {s}?", "Where does {s} belong?", "Où va {s} ?"),
                listOf(
                    Record(L("papier", "paper", "papier"), L("papierbak", "paper bin", "bac papier")),
                    Record(L("glazen fles", "glass bottle", "bouteille en verre"), L("glasbak", "glass bin", "conteneur à verre")),
                    Record(L("bananenschil", "banana peel", "peau de banane"), L("gft", "organic waste", "déchets organiques")),
                    Record(L("batterij", "battery", "pile"), L("inzamelpunt", "collection point", "point de collecte"))
                )
            ),
            LevelData(
                L("Welke keuze bespaart {s}?", "Which choice saves {s}?", "Quel choix économise {s} ?"),
                listOf(
                    Record(L("water", "water", "eau"), L("kort douchen", "short shower", "douche courte")),
                    Record(L("elektriciteit", "electricity", "électricité"), L("licht uitdoen", "turn off lights", "éteindre la lumière")),
                    Record(L("grondstoffen", "raw materials", "matières premières"), L("hergebruiken", "reuse", "réutiliser")),
                    Record(L("brandstof", "fuel", "carburant"), L("fietsen", "cycle", "faire du vélo"))
                )
            ),
            LevelData(
                L("Welk begrip past bij {s}?", "Which concept fits {s}?", "Quel concept correspond à {s} ?"),
                listOf(
                    Record(L("opnieuw gebruiken", "use again", "utiliser à nouveau"), L("hergebruik", "reuse", "réemploi")),
                    Record(L("materiaal opnieuw verwerken", "process material again", "retransformer le matériau"), L("recyclage", "recycling", "recyclage")),
                    Record(L("minder kopen", "buy less", "acheter moins"), L("preventie", "prevention", "prévention")),
                    Record(L("product herstellen", "repair a product", "réparer un produit"), L("reparatie", "repair", "réparation"))
                )
            ),
            LevelData(
                L("Welke energiebron is {s}?", "Which energy source is {s}?", "Quelle source d’énergie est {s} ?"),
                listOf(
                    Record(L("zon", "sun", "soleil"), L("hernieuwbaar", "renewable", "renouvelable")),
                    Record(L("wind", "wind", "vent"), L("hernieuwbaar", "renewable", "renouvelable")),
                    Record(L("steenkool", "coal", "charbon"), L("fossiel", "fossil", "fossile")),
                    Record(L("aardgas", "natural gas", "gaz naturel"), L("fossiel", "fossil", "fossile"))
                )
            ),
            LevelData(
                L("Welk effect hoort bij {s}?", "Which effect belongs to {s}?", "Quel effet correspond à {s} ?"),
                listOf(
                    Record(L("ontbossing", "deforestation", "déforestation"), L("verlies van leefgebied", "habitat loss", "perte d’habitat")),
                    Record(L("plastic in zee", "plastic in the sea", "plastique en mer"), L("schade aan dieren", "harm to animals", "danger pour les animaux")),
                    Record(L("te veel mest", "too much fertilizer", "trop d’engrais"), L("watervervuiling", "water pollution", "pollution de l’eau")),
                    Record(L("meer bomen", "more trees", "plus d’arbres"), L("koolstofopslag", "carbon storage", "stockage du carbone"))
                )
            ),
            LevelData(
                L("Welke strategie past bij {s}?", "Which strategy fits {s}?", "Quelle stratégie correspond à {s} ?"),
                listOf(
                    Record(L("producten lang gebruiken", "use products for a long time", "utiliser longtemps les produits"), L("circulaire economie", "circular economy", "économie circulaire")),
                    Record(L("natuurgebieden verbinden", "connect nature areas", "relier les espaces naturels"), L("ecologische corridor", "ecological corridor", "corridor écologique")),
                    Record(L("uitstoot verminderen", "reduce emissions", "réduire les émissions"), L("klimaatmitigatie", "climate mitigation", "atténuation climatique")),
                    Record(L("aanpassen aan hitte", "adapt to heat", "s’adapter à la chaleur"), L("klimaatadaptatie", "climate adaptation", "adaptation climatique"))
                )
            )
        )
    )

    fun supports(themeId: String): Boolean = themeId in banks

    fun build(themeId: String, levelNumber: Int): List<ExerciseContent> {
        val level = levelNumber.coerceIn(1, 6)
        val data = banks[themeId]?.getOrNull(level - 1) ?: return emptyList()
        val answerPool = data.records.map { it.answer }.distinctBy { it.nl }
        return List(100) { index ->
            val record = data.records[index % data.records.size]
            val random = Random((themeId + level + index).hashCode())
            val distractors = answerPool.filterNot { it.nl == record.answer.nl }.shuffled(random).take(3)
            val options = (listOf(record.answer) + distractors).shuffled(random)
            val question = L(
                data.question.nl.replace("{s}", record.subject.nl),
                data.question.en.replace("{s}", record.subject.en),
                data.question.fr.replace("{s}", record.subject.fr)
            )
            val id = "world-extra-$themeId-l$level-${(index + 1).toString().padStart(3, '0')}"
            ExerciseContent(
                id = id,
                domain = LearningDomain.WORLD_KNOWLEDGE,
                skill = themeId,
                section = ExerciseSection.EXERCISES,
                level = level.toSkillLevel(),
                inputMode = InputMode.TAP_CHOICE,
                prompt = LocalizedText(question.nl, question.fr, question.en),
                data = mapOf(
                    "mainCategory" to "world-knowledge",
                    "subcategoryId" to themeId,
                    "grade" to level.toString(),
                    "difficultyStars" to (((index % 3) + 1).toString()),
                    "progressKey" to id,
                    "options_nl-BE" to options.joinToString("\n") { it.nl },
                    "options_en-US" to options.joinToString("\n") { it.en },
                    "options_fr-BE" to options.joinToString("\n") { it.fr },
                    "answer_nl-BE" to record.answer.nl,
                    "answer_en-US" to record.answer.en,
                    "answer_fr-BE" to record.answer.fr,
                    "topic" to themeId.replace('_', ' ').replaceFirstChar { it.uppercase() }
                ),
                answer = record.answer.nl
            )
        }
    }

    private fun Int.toSkillLevel(): SkillLevel = when (this) {
        1 -> SkillLevel.FIRST_GRADE
        2 -> SkillLevel.SECOND_GRADE
        3 -> SkillLevel.THIRD_GRADE
        4 -> SkillLevel.FOURTH_GRADE
        5 -> SkillLevel.FIFTH_GRADE
        else -> SkillLevel.SIXTH_GRADE
    }
}
