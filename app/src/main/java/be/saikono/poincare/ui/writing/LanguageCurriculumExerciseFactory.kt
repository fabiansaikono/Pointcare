package be.saikono.poincare.ui.writing

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.SubcategoryCatalog
import kotlin.random.Random

/**
 * Small, curriculum-aligned additions to the XML library.
 *
 * The existing XML content remains the primary library. This factory adds five
 * targeted Reading/Writing/Listening missions per grade and supplies the new
 * grammar focus menus without duplicating the repository or progress system.
 */
object LanguageCurriculumExerciseFactory {
    private data class L(val nl: String, val en: String, val fr: String)
    private data class Seed(
        val prompt: L,
        val options: List<L>,
        val correctIndex: Int,
        val speech: L? = null
    )

    private data class GradeData(
        val words: List<L>,
        val sentence: L,
        val longerSentence: L
    )

    fun hasExercises(domain: LearningDomain, subcategoryId: String, grade: Int): Boolean {
        val base = SubcategoryCatalog.baseId(subcategoryId)
        if (grade !in 1..6) return false
        if (domain == LearningDomain.READING && base in setOf("reading", "listening")) return true
        if (domain == LearningDomain.WRITING && base == "writing") return true
        if (domain == LearningDomain.WRITING && base in SubcategoryCatalog.writingFocuses) {
            return SubcategoryCatalog.focusFor(subcategoryId)?.let { grade in it.minGrade..it.maxGrade }
                ?: SubcategoryCatalog.writingFocuses[base].orEmpty().any { grade in it.minGrade..it.maxGrade }
        }
        return false
    }

    fun build(
        domain: LearningDomain,
        subcategoryId: String,
        level: SkillLevel,
        selectedDifficulty: Int?
    ): List<ExerciseContent> {
        val grade = level.toGrade() ?: return emptyList()
        val base = SubcategoryCatalog.baseId(subcategoryId)
        val seeds = when {
            domain == LearningDomain.READING && base == "reading" -> readingSeeds(grade)
            domain == LearningDomain.READING && base == "listening" -> listeningSeeds(grade)
            domain == LearningDomain.WRITING && base == "writing" -> writingSeeds(grade)
            domain == LearningDomain.WRITING && SubcategoryCatalog.focusFor(subcategoryId) != null ->
                focusSeeds(subcategoryId, grade).let { source -> List(20) { source[it % source.size] } }
            else -> emptyList()
        }
        return seeds.mapIndexedNotNull { index, seed ->
            val stars = (index % 3) + 1
            if (selectedDifficulty != null && stars != selectedDifficulty) return@mapIndexedNotNull null
            seed.toExercise(
                id = "curriculum-${subcategoryId.replace(SubcategoryCatalog.FOCUS_SEPARATOR, "-")}-g$grade-${(index + 1).toString().padStart(3, '0')}",
                domain = domain,
                subcategoryId = subcategoryId,
                level = level,
                grade = grade,
                stars = stars,
                listening = base == "listening"
            )
        }
    }

    private fun Seed.toExercise(
        id: String,
        domain: LearningDomain,
        subcategoryId: String,
        level: SkillLevel,
        grade: Int,
        stars: Int,
        listening: Boolean
    ): ExerciseContent {
        val answer = options[correctIndex]
        return ExerciseContent(
            id = id,
            domain = domain,
            skill = SubcategoryCatalog.baseId(subcategoryId),
            section = ExerciseSection.EXERCISES,
            level = level,
            inputMode = if (listening) InputMode.LISTEN_AND_SELECT else InputMode.TAP_CHOICE,
            prompt = LocalizedText(prompt.nl, prompt.fr, prompt.en),
            speechText = speech?.let { LocalizedText(it.nl, it.fr, it.en) },
            data = buildMap {
                put("mainCategory", if (domain == LearningDomain.WRITING) "writing" else "reading")
                put("subcategoryId", subcategoryId)
                put("grade", grade.toString())
                put("difficultyStars", stars.toString())
                put("progressKey", id)
                put("options_nl-BE", options.joinToString("\n") { it.nl })
                put("options_en-US", options.joinToString("\n") { it.en })
                put("options_fr-BE", options.joinToString("\n") { it.fr })
                put("answer_nl-BE", answer.nl)
                put("answer_en-US", answer.en)
                put("answer_fr-BE", answer.fr)
                put("topic", topicName(SubcategoryCatalog.baseId(subcategoryId), "nl"))
                put("topic_en-US", topicName(SubcategoryCatalog.baseId(subcategoryId), "en"))
                put("topic_fr-BE", topicName(SubcategoryCatalog.baseId(subcategoryId), "fr"))
                speech?.let {
                    put("audioText_nl-BE", it.nl)
                    put("audioText_en-US", it.en)
                    put("audioText_fr-BE", it.fr)
                    put("audioTtsLanguage_nl-BE", "nl-BE")
                    put("audioTtsLanguage_en-US", "en-US")
                    put("audioTtsLanguage_fr-BE", "fr-BE")
                }
            },
            answer = answer.nl
        )
    }

    private fun readingSeeds(grade: Int): List<Seed> {
        val data = gradeData(grade)
        val word = data.words[0]
        val other = data.words.drop(1)
        return listOf(
            choice(
                L("Welk woord past bij de betekenis?", "Which word matches the meaning?", "Quel mot correspond au sens ?"),
                listOf(word) + other,
                0
            ),
            choice(
                L("Welke zin is volledig en duidelijk?", "Which sentence is complete and clear?", "Quelle phrase est complète et claire ?"),
                listOf(data.sentence, data.words[1], data.words[2], data.words[3]),
                0
            ),
            choice(
                L("Welke zin geeft de belangrijkste informatie?", "Which sentence gives the main information?", "Quelle phrase donne l'information principale ?"),
                listOf(data.longerSentence, data.words[1], data.words[2], data.words[3]),
                0
            ),
            choice(
                L("Welk woord hoort logisch bij de zin?", "Which word logically belongs in the sentence?", "Quel mot convient logiquement à la phrase ?"),
                listOf(data.words[2], data.words[0], data.words[1], data.words[3]),
                0
            ),
            choice(
                L("Welke tekst is het best leesbaar?", "Which text is easiest to read?", "Quel texte est le plus facile à lire ?"),
                listOf(data.sentence, L(data.sentence.nl.uppercase(), data.sentence.en.uppercase(), data.sentence.fr.uppercase()), data.words[0], data.words[1]),
                0
            )
        )
    }

    private fun writingSeeds(grade: Int): List<Seed> {
        val data = gradeData(grade)
        val correct = data.words[0]
        val wrong1 = L(correct.nl + "n", correct.en + "e", correct.fr + "e")
        val wrong2 = L(correct.nl.dropLast(1), correct.en.dropLast(1), correct.fr.dropLast(1))
        return listOf(
            choice(L("Welk woord is juist geschreven?", "Which word is spelled correctly?", "Quel mot est correctement écrit ?"), listOf(correct, wrong1, wrong2, data.words[1]), 0),
            choice(L("Welke zin begint met een hoofdletter en eindigt met een punt?", "Which sentence starts with a capital and ends with a full stop?", "Quelle phrase commence par une majuscule et se termine par un point ?"), listOf(data.sentence, L(data.sentence.nl.lowercase(), data.sentence.en.lowercase(), data.sentence.fr.lowercase()), data.words[2], data.words[3]), 0),
            choice(L("Welke zin heeft de juiste woordvolgorde?", "Which sentence has the correct word order?", "Quelle phrase a le bon ordre des mots ?"), listOf(data.longerSentence, reverseWords(data.longerSentence), data.words[1], data.words[2]), 0),
            choice(L("Welk woord maakt de zin precies?", "Which word makes the sentence precise?", "Quel mot rend la phrase précise ?"), listOf(data.words[2], data.words[0], data.words[1], data.words[3]), 0),
            choice(L("Welke zin is geschikt voor een korte boodschap?", "Which sentence suits a short message?", "Quelle phrase convient à un court message ?"), listOf(data.sentence, data.longerSentence, data.words[0], data.words[1]), 0)
        )
    }

    private fun listeningSeeds(grade: Int): List<Seed> {
        val data = gradeData(grade)
        val eu = L("de klank eu", "the sound eu", "le son eu")
        return listOf(
            choice(L("Luister en kies wat je hoort.", "Listen and choose what you hear.", "Écoute et choisis ce que tu entends."), data.words, 0, data.words[0]),
            choice(L("Luister naar de klank.", "Listen to the sound.", "Écoute le son."), listOf(L("eu", "eu", "eu"), L("ui", "ui", "ui"), L("oe", "oo", "ou"), L("ie", "ee", "i")), 0, eu),
            choice(L("Luister naar de zin en kies het kernwoord.", "Listen to the sentence and choose the key word.", "Écoute la phrase et choisis le mot-clé."), data.words, 1, data.sentence),
            choice(L("Luister en kies de juiste geschreven vorm.", "Listen and choose the correct written form.", "Écoute et choisis la bonne forme écrite."), data.words, 2, data.words[2]),
            choice(L("Luister naar de boodschap.", "Listen to the message.", "Écoute le message."), listOf(data.longerSentence, data.words[0], data.words[1], data.words[3]), 0, data.longerSentence)
        )
    }

    private fun focusSeeds(fullId: String, grade: Int): List<Seed> {
        val base = SubcategoryCatalog.baseId(fullId)
        val focus = fullId.substringAfter(SubcategoryCatalog.FOCUS_SEPARATOR)
        return when (base) {
            "singular_plural" -> singularPluralSeeds(focus)
            "conjugations" -> conjugationSeeds(focus)
            "verbs" -> verbSeeds(focus)
            "past_tense" -> pastSeeds(focus)
            "future_tense" -> futureSeeds(focus)
            else -> emptyList()
        }.ifEmpty {
            listOf(choice(L("Kies het juiste antwoord.", "Choose the correct answer.", "Choisis la bonne réponse."), gradeData(grade).words, 0))
        }
    }

    private fun singularPluralSeeds(focus: String): List<Seed> {
        val records: List<Pair<L, L>> = when (focus) {
            "apostrophe" -> listOf(
                L("auto", "baby", "photo") to L("auto's", "babies", "photos"),
                L("baby", "city", "bateau") to L("baby's", "cities", "bateaux"),
                L("foto", "photo", "jeu") to L("foto's", "photos", "jeux"),
                L("menu", "hero", "animal") to L("menu's", "heroes", "animaux")
            )
            "f_v" -> listOf(
                L("brief", "wolf", "cheval") to L("brieven", "wolves", "chevaux"),
                L("duif", "knife", "journal") to L("duiven", "knives", "journaux"),
                L("wolf", "leaf", "travail") to L("wolven", "leaves", "travaux"),
                L("golf", "life", "œil") to L("golven", "lives", "yeux")
            )
            "s_z" -> listOf(
                L("huis", "house", "maison") to L("huizen", "houses", "maisons"),
                L("muis", "mouse", "souris") to L("muizen", "mice", "souris"),
                L("doos", "box", "boîte") to L("dozen", "boxes", "boîtes"),
                L("reis", "journey", "voyage") to L("reizen", "journeys", "voyages")
            )
            "irregular" -> listOf(
                L("kind", "child", "enfant") to L("kinderen", "children", "enfants"),
                L("ei", "tooth", "œil") to L("eieren", "teeth", "yeux"),
                L("schip", "person", "cheval") to L("schepen", "people", "chevaux"),
                L("stad", "mouse", "travail") to L("steden", "mice", "travaux")
            )
            else -> listOf(
                L("kat", "cat", "chat") to L("katten", "cats", "chats"),
                L("boek", "book", "livre") to L("boeken", "books", "livres"),
                L("stoel", "chair", "chaise") to L("stoelen", "chairs", "chaises"),
                L("hond", "dog", "chien") to L("honden", "dogs", "chiens")
            )
        }
        val plurals = records.map { it.second }
        return records.map { (singular, plural) ->
            val options = listOf(plural) + plurals.filterNot { it == plural }.take(3)
            choice(
                L(
                    "Wat is het meervoud van ${singular.nl}?",
                    "What is the plural of ${singular.en}?",
                    "Quel est le pluriel de ${singular.fr} ?"
                ),
                options,
                0
            )
        }
    }

    private fun conjugationSeeds(focus: String): List<Seed> {
        val verbs = listOf(
            L("werken", "work", "travailler"),
            L("lopen", "walk", "marcher"),
            L("lezen", "read", "lire"),
            L("spelen", "play", "jouer")
        )
        val answers = when (focus) {
            "first_person" -> listOf(L("ik werk", "I work", "je travaille"), L("ik loop", "I walk", "je marche"), L("ik lees", "I read", "je lis"), L("ik speel", "I play", "je joue"))
            "second_person" -> listOf(L("jij werkt", "you work", "tu travailles"), L("jij loopt", "you walk", "tu marches"), L("jij leest", "you read", "tu lis"), L("jij speelt", "you play", "tu joues"))
            "third_person" -> listOf(L("hij werkt", "he works", "il travaille"), L("zij loopt", "she walks", "elle marche"), L("hij leest", "he reads", "il lit"), L("zij speelt", "she plays", "elle joue"))
            "plural_persons" -> listOf(L("wij werken", "we work", "nous travaillons"), L("jullie lopen", "you walk", "vous marchez"), L("zij lezen", "they read", "ils lisent"), L("wij spelen", "we play", "nous jouons"))
            else -> listOf(L("werk jij?", "do you work?", "travailles-tu ?"), L("loop jij?", "do you walk?", "marches-tu ?"), L("lees jij?", "do you read?", "lis-tu ?"), L("speel jij?", "do you play?", "joues-tu ?"))
        }
        return verbs.indices.map { index ->
            val verb = verbs[index]
            val answer = answers[index]
            choice(
                L(
                    "Kies de juiste vervoeging van ${verb.nl}.",
                    "Choose the correct conjugation of ${verb.en}.",
                    "Choisis la bonne conjugaison de ${verb.fr}."
                ),
                listOf(answer) + answers.filterNot { it == answer }.take(3),
                0
            )
        }
    }

    private fun verbSeeds(focus: String): List<Seed> = when (focus) {
        "infinitive" -> listOf(
            L("lopen", "to walk", "marcher"), L("werken", "to work", "travailler"),
            L("lezen", "to read", "lire"), L("spelen", "to play", "jouer")
        ).map { verb ->
            choice(
                L("Wat is de infinitief?", "Which word is an infinitive?", "Quel mot est à l'infinitif ?"),
                listOf(verb, L("loopt", "walks", "marche"), L("werkte", "worked", "travaillait"), L("boek", "book", "livre")),
                0
            )
        }
        "stem" -> listOf(
            L("werken", "working", "travailler") to L("werk", "work", "travaill"),
            L("maken", "making", "fabriquer") to L("maak", "make", "fabriqu"),
            L("spelen", "playing", "jouer") to L("speel", "play", "jou"),
            L("leren", "learning", "apprendre") to L("leer", "learn", "apprend")
        ).map { (verb, stem) ->
            choice(
                L("Wat is de stam van ${verb.nl}?", "What is the stem of ${verb.en}?", "Quel est le radical de ${verb.fr} ?"),
                listOf(stem, verb, L(stem.nl + "t", stem.en + "s", stem.fr + "e"), L(stem.nl + "de", stem.en + "ed", stem.fr + "ait")),
                0
            )
        }
        "finite_verb" -> listOf(
            L("De raket vliegt snel.", "The rocket flies fast.", "La fusée vole vite.") to L("vliegt", "flies", "vole"),
            L("Wij lezen een boek.", "We read a book.", "Nous lisons un livre.") to L("lezen", "read", "lisons"),
            L("De kinderen spelen buiten.", "The children play outside.", "Les enfants jouent dehors.") to L("spelen", "play", "jouent"),
            L("Jij maakt een tekening.", "You make a drawing.", "Tu fais un dessin.") to L("maakt", "make", "fais")
        ).map { (sentence, answer) ->
            choice(
                L("Welke persoonsvorm staat in: ${sentence.nl}", "Which is the finite verb in: ${sentence.en}", "Quel est le verbe conjugué dans : ${sentence.fr}"),
                listOf(answer, L("de", "the", "le"), L("snel", "quickly", "vite"), L("boek", "book", "livre")),
                0
            )
        }
        "verb_or_noun" -> listOf(
            L("lopen", "walk", "marcher"), L("schrijven", "write", "écrire"),
            L("denken", "think", "penser"), L("bouwen", "build", "construire")
        ).map { verb ->
            choice(L("Welk woord is een werkwoord?", "Which word is a verb?", "Quel mot est un verbe ?"), listOf(verb, L("tafel", "table", "table"), L("blauw", "blue", "bleu"), L("stil", "quiet", "calme")), 0)
        }
        else -> listOf(
            L("werken", "work", "travailler") to L("zwak", "regular", "régulier"),
            L("lopen", "go", "aller") to L("sterk", "irregular", "irrégulier"),
            L("spelen", "play", "jouer") to L("zwak", "regular", "régulier"),
            L("vinden", "find", "venir") to L("sterk", "irregular", "irrégulier")
        ).map { (verb, answer) ->
            val opposite = L(if (answer.nl == "sterk") "zwak" else "sterk", if (answer.en == "irregular") "regular" else "irregular", if (answer.fr == "irrégulier") "régulier" else "irrégulier")
            choice(
                L("Is ${verb.nl} een sterk of zwak werkwoord?", "Is ${verb.en} regular or irregular?", "${verb.fr} est-il régulier ou irrégulier ?"),
                listOf(answer, opposite, L("zelfstandig naamwoord", "noun", "nom"), L("bijvoeglijk naamwoord", "adjective", "adjectif")),
                0
            )
        }
    }

    private fun pastSeeds(focus: String): List<Seed> {
        val verbs = listOf(L("werken", "work", "travailler"), L("spelen", "play", "jouer"), L("lopen", "go", "aller"), L("lezen", "read", "lire"))
        val answers = when (focus) {
            "perfect" -> listOf(L("heeft gewerkt", "has worked", "a travaillé"), L("heeft gespeeld", "has played", "a joué"), L("heeft gelopen", "has gone", "est allé"), L("heeft gelezen", "has read", "a lu"))
            "kofschip" -> listOf(L("werkte", "worked", "travaillait"), L("speelde", "played", "jouait"), L("liep", "went", "allait"), L("las", "read", "lisait"))
            "irregular_past" -> listOf(L("ging", "went", "est allé"), L("zag", "saw", "a vu"), L("kwam", "came", "est venu"), L("vond", "found", "a trouvé"))
            "past_sentence" -> listOf(L("Gisteren werkte ik thuis.", "Yesterday I worked at home.", "Hier, j'ai travaillé à la maison."), L("Gisteren speelde ik buiten.", "Yesterday I played outside.", "Hier, j'ai joué dehors."), L("Toen liep ik naar school.", "Then I walked to school.", "Alors, je suis allé à l'école."), L("Vorige week las ik dit boek.", "Last week I read this book.", "La semaine passée, j'ai lu ce livre."))
            else -> listOf(L("werkte", "worked", "travaillait"), L("speelde", "played", "jouait"), L("liep", "went", "allait"), L("las", "read", "lisait"))
        }
        return verbs.indices.map { index ->
            val verb=verbs[index]; val answer=answers[index]
            choice(
                L("Kies de juiste verleden tijd van ${verb.nl}.", "Choose the correct past form of ${verb.en}.", "Choisis la forme correcte au passé de ${verb.fr}."),
                listOf(answer)+answers.filterNot { it==answer }.take(3),0
            )
        }
    }

    private fun futureSeeds(focus: String): List<Seed> {
        val answers = when (focus) {
            "going_to" -> listOf(L("Ik ga morgen lezen.", "I am going to read tomorrow.", "Je vais lire demain."), L("Wij gaan straks spelen.", "We are going to play soon.", "Nous allons jouer bientôt."), L("Zij gaat later koken.", "She is going to cook later.", "Elle va cuisiner plus tard."), L("Jullie gaan een raket bouwen.", "You are going to build a rocket.", "Vous allez construire une fusée."))
            "present_future" -> listOf(L("Morgen vertrek ik om acht uur.", "Tomorrow I leave at eight.", "Demain, je pars à huit heures."), L("Volgende week bezoeken wij het museum.", "Next week we visit the museum.", "La semaine prochaine, nous visitons le musée."), L("Straks begint de film.", "The film starts soon.", "Le film commence bientôt."), L("Vanavond eten we samen.", "Tonight we eat together.", "Ce soir, nous mangeons ensemble."))
            "future_word_order" -> listOf(L("Morgen zal ik een boek lezen.", "Tomorrow I will read a book.", "Demain, je lirai un livre."), L("Later zullen wij naar Mars reizen.", "Later we will travel to Mars.", "Plus tard, nous voyagerons vers Mars."), L("Straks zal zij haar taak maken.", "Soon she will do her task.", "Bientôt, elle fera son travail."), L("Volgende week zullen jullie oefenen.", "Next week you will practise.", "La semaine prochaine, vous vous exercerez."))
            "future_time_signal" -> listOf(L("morgen", "tomorrow", "demain"), L("straks", "soon", "bientôt"), L("volgende week", "next week", "la semaine prochaine"), L("later", "later", "plus tard"))
            else -> listOf(L("Ik zal morgen lezen.", "I will read tomorrow.", "Je lirai demain."), L("Wij zullen straks spelen.", "We will play soon.", "Nous jouerons bientôt."), L("Zij zal later koken.", "She will cook later.", "Elle cuisinera plus tard."), L("Jullie zullen een raket bouwen.", "You will build a rocket.", "Vous construirez une fusée."))
        }
        return answers.map { answer ->
            choice(
                L("Welke vorm drukt de toekomst uit?", "Which form expresses the future?", "Quelle forme exprime le futur ?"),
                listOf(answer, L("Gisteren gebeurde het.", "It happened yesterday.", "C'est arrivé hier."), L("Nu is het bezig.", "It is happening now.", "Cela se passe maintenant."), L("Vroeger was het anders.", "It was different before.", "Avant, c'était différent.")),
                0
            )
        }
    }

    private fun gradeData(grade: Int): GradeData = when (grade) {
        1 -> GradeData(
            listOf(L("maan", "moon", "lune"), L("boek", "book", "livre"), L("neus", "nose", "nez"), L("boom", "tree", "arbre")),
            L("De kat slaapt.", "The cat sleeps.", "Le chat dort."),
            L("De kleine kat slaapt op de stoel.", "The small cat sleeps on the chair.", "Le petit chat dort sur la chaise.")
        )
        2 -> GradeData(
            listOf(L("school", "school", "école"), L("vriend", "friend", "ami"), L("deur", "door", "porte"), L("regen", "rain", "pluie")),
            L("Mila leest een boek.", "Mila reads a book.", "Mila lit un livre."),
            L("Na school leest Mila rustig een boek.", "After school Mila quietly reads a book.", "Après l'école, Mila lit calmement un livre.")
        )
        3 -> GradeData(
            listOf(L("onderzoek", "research", "recherche"), L("rivier", "river", "rivière"), L("geheugen", "memory", "mémoire"), L("avontuur", "adventure", "aventure")),
            L("De klas onderzoekt water.", "The class studies water.", "La classe étudie l'eau."),
            L("De klas onderzoekt hoe schoon rivierwater wordt.", "The class studies how river water becomes clean.", "La classe étudie comment l'eau de rivière devient propre.")
        )
        4 -> GradeData(
            listOf(L("verklaring", "explanation", "explication"), L("verschil", "difference", "différence"), L("oorzaak", "cause", "cause"), L("gevolg", "effect", "conséquence")),
            L("De tekst legt een oorzaak uit.", "The text explains a cause.", "Le texte explique une cause."),
            L("De schrijver toont het verschil tussen oorzaak en gevolg.", "The writer shows the difference between cause and effect.", "L'auteur montre la différence entre la cause et la conséquence.")
        )
        5 -> GradeData(
            listOf(L("argument", "argument", "argument"), L("bewijs", "evidence", "preuve"), L("standpunt", "viewpoint", "point de vue"), L("samenvatting", "summary", "résumé")),
            L("Het bewijs ondersteunt het argument.", "The evidence supports the argument.", "La preuve soutient l'argument."),
            L("Een goede samenvatting noemt het standpunt en het sterkste bewijs.", "A good summary states the viewpoint and the strongest evidence.", "Un bon résumé présente le point de vue et la preuve la plus forte.")
        )
        else -> GradeData(
            listOf(L("betrouwbaarheid", "reliability", "fiabilité"), L("doelgroep", "audience", "public"), L("nuance", "nuance", "nuance"), L("bron", "source", "source")),
            L("De bron vermeldt haar doelgroep.", "The source states its audience.", "La source indique son public."),
            L("Een kritische lezer vergelijkt bronnen, bedoeling en betrouwbaarheid.", "A critical reader compares sources, purpose and reliability.", "Un lecteur critique compare les sources, l'intention et la fiabilité.")
        )
    }

    private fun choice(prompt: L, options: List<L>, correct: Int, speech: L? = null): Seed {
        require(options.size >= 4)
        return Seed(prompt, options.take(4), correct.coerceIn(0, 3), speech)
    }

    private fun reverseWords(value: L): L = L(
        value.nl.removeSuffix(".").split(" ").reversed().joinToString(" ") + ".",
        value.en.removeSuffix(".").split(" ").reversed().joinToString(" ") + ".",
        value.fr.removeSuffix(".").split(" ").reversed().joinToString(" ") + "."
    )

    private fun englishPlural(value: String): String = when (value) {
        "kinderen" -> "children"; "eieren" -> "eggs"; "schepen" -> "ships"; "steden" -> "cities"
        else -> value.replace("'s", "s")
    }

    private fun frenchPlural(value: String): String = when (value) {
        "kinderen" -> "enfants"; "eieren" -> "œufs"; "schepen" -> "navires"; "steden" -> "villes"
        else -> value.replace("'s", "s")
    }

    private fun topicName(base: String, language: String): String = when (base) {
        "reading" -> when (language) { "en" -> "Reading"; "fr" -> "Lecture"; else -> "Lezen" }
        "listening" -> when (language) { "en" -> "Listening"; "fr" -> "Écoute"; else -> "Luisteren" }
        "writing" -> when (language) { "en" -> "Writing"; "fr" -> "Écriture"; else -> "Schrijven" }
        "singular_plural" -> when (language) { "en" -> "Singular and plural"; "fr" -> "Singulier et pluriel"; else -> "Enkelvoud en meervoud" }
        "conjugations" -> when (language) { "en" -> "Conjugations"; "fr" -> "Conjugaisons"; else -> "Vervoegingen" }
        "verbs" -> when (language) { "en" -> "Verbs"; "fr" -> "Verbes"; else -> "Werkwoorden" }
        "past_tense" -> when (language) { "en" -> "Past tenses"; "fr" -> "Temps du passé"; else -> "Verleden tijden" }
        "future_tense" -> when (language) { "en" -> "Future tenses"; "fr" -> "Temps du futur"; else -> "Toekomende tijden" }
        else -> base.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun SkillLevel.toGrade(): Int? = if (this == SkillLevel.NOT_APPLICABLE) null else ordinal + 1
}
