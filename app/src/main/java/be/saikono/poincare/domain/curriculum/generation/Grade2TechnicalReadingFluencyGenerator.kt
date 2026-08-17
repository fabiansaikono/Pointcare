package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 2 technical-reading generator.
 *
 * The automated slice checks accurate decoding choices and recognition of
 * increasingly complex written patterns. Oral pace, accuracy, phrasing, and
 * expression still require a real reading-aloud observation by an adult.
 */
object Grade2TechnicalReadingFluencyGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g2.language.technical_reading.fluency"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g2.language.technical_reading.fluency"
    private val allowedParameterKeys = setOf("grade", "roadmap_entry", "phase", "editorial_status")

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["grade"] != "2") add("grade must be 2")
        if (parameters["roadmap_entry"] != ROADMAP_ENTRY) add("roadmap_entry must be $ROADMAP_ENTRY")
        if (parameters["phase"] !in setOf("guided", "checkpoint")) {
            add("phase must be guided or checkpoint")
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
            val candidate = Candidate(
                kind = kind,
                templateIndex = templateIndex,
                prompt = template.prompt,
                speech = template.speech,
                options = options,
                answerKey = template.answerKey
            )
            if (used.add(candidate.signature())) return candidate
        }
        bank.forEachIndexed { templateIndex, template ->
            template.options.indices.forEach { rotation ->
                val options = template.options.drop(rotation) + template.options.take(rotation)
                val candidate = Candidate(
                    kind,
                    templateIndex,
                    template.prompt,
                    template.speech,
                    options,
                    template.answerKey
                )
                if (used.add(candidate.signature())) return candidate
            }
        }
        error("Unable to create a unique Grade 2 technical-reading exercise for ${kind.name}")
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
            id = "generated.g2.language.technical_reading.v1.${seed.fingerprint}.${index + 1}",
            domain = LearningDomain.READING,
            skill = "technical_reading_fluency",
            section = if (phase == Phase.GUIDED) ExerciseSection.PRACTICE else ExerciseSection.TESTS,
            level = SkillLevel.SECOND_GRADE,
            inputMode = InputMode.TAP_CHOICE,
            prompt = candidate.prompt,
            speechText = candidate.speech,
            data = linkedMapOf(
                "generatorId" to generatorId,
                "generatorVersion" to generatorVersion.toString(),
                "seedFingerprint" to seed.fingerprint,
                "roadmapEntry" to ROADMAP_ENTRY,
                "phase" to phase.name.lowercase(),
                "promptKind" to candidate.kind.name.lowercase(),
                "contentKey" to "${candidate.kind.name.lowercase()}.${candidate.templateIndex + 1}",
                "answerRole" to "choice",
                "automatedScope" to "decoding_recognition_choice",
                "requiresAdultOralFluencyCheck" to "true",
                "audioRequired" to (candidate.kind == PromptKind.LISTEN_AND_FIND_WORD).toString(),
                "options_nl-BE" to candidate.options.joinToString("\n") { it.nl },
                "options_fr-BE" to candidate.options.joinToString("\n") { it.fr },
                "options_en-US" to candidate.options.joinToString("\n") { it.en },
                "optionSpeech_nl-BE" to candidate.options.joinToString("\n") { it.speechNl ?: it.nl },
                "optionSpeech_fr-BE" to candidate.options.joinToString("\n") { it.speechFr ?: it.fr },
                "optionSpeech_en-US" to candidate.options.joinToString("\n") { it.speechEn ?: it.en },
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

    private fun bank(kind: PromptKind): List<Template> = when (kind) {
        PromptKind.EXACT_WORD -> listOf(
            template(
                localized("Zoek precies hetzelfde woord als trein.", "Trouve exactement le même mot que train.", "Find exactly the same word as train."),
                localized("Zoek precies hetzelfde woord als trein", "Trouve exactement le même mot que train", "Find exactly the same word as train"),
                "exact", choice("exact", "trein", "train", "train"),
                choice("near_1", "terin", "trian", "trian"), choice("near_2", "plein", "grain", "trail")
            ),
            template(
                localized("Zoek precies hetzelfde woord als schommel.", "Trouve exactement le même mot que balançoire.", "Find exactly the same word as playground."),
                localized("Zoek precies hetzelfde woord als schommel", "Trouve exactement le même mot que balançoire", "Find exactly the same word as playground"),
                "exact", choice("exact", "schommel", "balançoire", "playground"),
                choice("near_1", "schomel", "balancoire", "playgroudn"), choice("near_2", "school", "balancer", "playhouse")
            ),
            template(
                localized("Zoek precies hetzelfde woord als keuken.", "Trouve exactement le même mot que cuisine.", "Find exactly the same word as kitchen."),
                localized("Zoek precies hetzelfde woord als keuken", "Trouve exactement le même mot que cuisine", "Find exactly the same word as kitchen"),
                "exact", choice("exact", "keuken", "cuisine", "kitchen"),
                choice("near_1", "keuekn", "cuissine", "kithcen"), choice("near_2", "keuren", "cousin", "kitten")
            )
        )

        PromptKind.GRAPHEME_PATTERN -> listOf(
            template(
                localized("Welk woord begint met sch?", "Quel mot commence par ch ?", "Which word begins with sh?"),
                localized("Welk woord begint met de lettergroep s c h", "Quel mot commence par les lettres c h", "Which word begins with the letters s h"),
                "pattern", choice("pattern", "school", "chat", "ship"),
                choice("other_1", "stoel", "sac", "sip"), choice("other_2", "koel", "rat", "chip")
            ),
            template(
                localized("Welk woord bevat eeuw?", "Quel mot contient eau ?", "Which word contains ee?"),
                localized("Welk woord bevat de lettergroep e e u w", "Quel mot contient les lettres e a u", "Which word contains the letters e e"),
                "pattern", choice("pattern", "leeuw", "bateau", "green"),
                choice("other_1", "leuk", "battu", "grain"), choice("other_2", "lief", "botte", "grow")
            ),
            template(
                localized("Welk woord bevat ng?", "Quel mot contient on ?", "Which word contains oa?"),
                localized("Welk woord bevat de lettergroep n g", "Quel mot contient les lettres o n", "Which word contains the letters o a"),
                "pattern", choice("pattern", "zingen", "monde", "boat"),
                choice("other_1", "zinnen", "mode", "boot"), choice("other_2", "zitten", "midi", "bat")
            )
        )

        PromptKind.BLEND_SYLLABLES -> listOf(
            template(
                localized("Welk woord lees je bij ta – fel?", "Quel mot lis-tu avec mai – son ?", "Which word do you read from pic – nic?"),
                localized("Welk woord lees je bij de delen ta en fel", "Quel mot lis-tu avec les parties mai et son", "Which word do you read from the parts pic and nic"),
                "blend", choice("blend", "tafel", "maison", "picnic"),
                choice("other_1", "talent", "mouton", "pencil"), choice("other_2", "tegels", "matin", "planet")
            ),
            template(
                localized("Welk woord lees je bij win – ter?", "Quel mot lis-tu avec hi – ver ?", "Which word do you read from win – dow?"),
                localized("Welk woord lees je bij de delen win en ter", "Quel mot lis-tu avec les parties hi et ver", "Which word do you read from the parts win and dow"),
                "blend", choice("blend", "winter", "hiver", "window"),
                choice("other_1", "winkel", "histoire", "winter"), choice("other_2", "water", "hier", "willow")
            ),
            template(
                localized("Welk woord lees je bij paar – den?", "Quel mot lis-tu avec che – val ?", "Which word do you read from rab – bit?"),
                localized("Welk woord lees je bij de delen paar en den", "Quel mot lis-tu avec les parties che et val", "Which word do you read from the parts rab and bit"),
                "blend", choice("blend", "paarden", "cheval", "rabbit"),
                choice("other_1", "paden", "chenille", "ribbon"), choice("other_2", "parken", "château", "robot")
            )
        )

        PromptKind.COMPOUND_WORD -> listOf(
            template(
                localized("Welk woord maak je met regen + jas?", "Quel mot formes-tu avec porte + monnaie ?", "Which word do you make from rain + coat?"),
                localized("Welk woord maak je met regen en jas", "Quel mot formes-tu avec porte et monnaie", "Which word do you make from rain and coat"),
                "compound", choice("compound", "regenjas", "porte-monnaie", "raincoat"),
                choice("other_1", "regenboog", "portefeuille", "rainbow"), choice("other_2", "winterjas", "monnaie", "coatroom")
            ),
            template(
                localized("Welk woord maak je met voetbal + veld?", "Quel mot formes-tu avec grand + mère ?", "Which word do you make from foot + ball?"),
                localized("Welk woord maak je met voetbal en veld", "Quel mot formes-tu avec grand et mère", "Which word do you make from foot and ball"),
                "compound", choice("compound", "voetbalveld", "grand-mère", "football"),
                choice("other_1", "voetpad", "grandeur", "footpath"), choice("other_2", "speelveld", "maman", "balloon")
            ),
            template(
                localized("Welk woord maak je met boek + kast?", "Quel mot formes-tu avec après + midi ?", "Which word do you make from book + shelf?"),
                localized("Welk woord maak je met boek en kast", "Quel mot formes-tu avec après et midi", "Which word do you make from book and shelf"),
                "compound", choice("compound", "boekenkast", "après-midi", "bookshelf"),
                choice("other_1", "boekentas", "après", "bookshop"), choice("other_2", "kleerkast", "midi", "shelf")
            )
        )

        PromptKind.LISTEN_AND_FIND_WORD -> listOf(
            template(
                localized("Luister en kies het juiste woord.", "Écoute et choisis le bon mot.", "Listen and choose the correct word."),
                localized("Kies het woord schilder", "Choisis le mot château", "Choose the word shoulder"),
                "spoken", choice("spoken", "schilder", "château", "shoulder"),
                choice("other_1", "schilfer", "chapeau", "shorter"), choice("other_2", "school", "bateau", "shelter")
            ),
            template(
                localized("Luister en kies het juiste woord.", "Écoute et choisis le bon mot.", "Listen and choose the correct word."),
                localized("Kies het woord koningin", "Choisis le mot montagne", "Choose the word morning"),
                "spoken", choice("spoken", "koningin", "montagne", "morning"),
                choice("other_1", "konijnen", "montage", "moving"), choice("other_2", "woning", "mouton", "warning")
            ),
            template(
                localized("Luister en kies het juiste woord.", "Écoute et choisis le bon mot.", "Listen and choose the correct word."),
                localized("Kies het woord nieuwsgierig", "Choisis le mot écureuil", "Choose the word curious"),
                "spoken", choice("spoken", "nieuwsgierig", "écureuil", "curious"),
                choice("other_1", "nieuwjaar", "école", "serious"), choice("other_2", "nuttig", "chevreuil", "current")
            )
        )

        PromptKind.COMPLETE_SENTENCE -> listOf(
            template(
                localized("Lees de zin: De trein stopt aan het ___.", "Lis la phrase : Le train s'arrête à la ___.", "Read the sentence: The train stops at the ___."),
                localized("Lees de zin. De trein stopt aan het ontbrekende woord", "Lis la phrase. Le train s'arrête au mot manquant", "Read the sentence. The train stops at the missing word"),
                "sentence", choice("sentence", "station", "gare", "station"),
                choice("other_1", "strand", "jardin", "garden"), choice("other_2", "dak", "rivière", "river")
            ),
            template(
                localized("Lees de zin: De bakker bakt vers ___.", "Lis la phrase : Le boulanger prépare du ___.", "Read the sentence: The baker makes fresh ___."),
                localized("Lees de zin. De bakker bakt vers brood", "Lis la phrase. Le boulanger prépare du pain", "Read the sentence. The baker makes fresh bread"),
                "sentence", choice("sentence", "brood", "pain", "bread"),
                choice("other_1", "gras", "bois", "grass"), choice("other_2", "zand", "sable", "sand")
            ),
            template(
                localized("Lees de zin: In de herfst vallen de ___.", "Lis la phrase : En automne, les ___ tombent.", "Read the sentence: In autumn, the ___ fall."),
                localized("Lees de zin. In de herfst vallen de bladeren", "Lis la phrase. En automne, les feuilles tombent", "Read the sentence. In autumn, the leaves fall"),
                "sentence", choice("sentence", "bladeren", "feuilles", "leaves"),
                choice("other_1", "lepels", "cuillères", "spoons"), choice("other_2", "kussens", "coussins", "pillows")
            )
        )

        PromptKind.CORRECT_WORD -> listOf(
            template(
                localized("Welk woord is juist geschreven?", "Quel mot est écrit correctement ?", "Which word is written correctly?"),
                localized("Welk woord is juist geschreven", "Quel mot est écrit correctement", "Which word is written correctly"),
                "correct", choice("correct", "vriend", "oiseau", "bright"),
                choice("other_1", "vrient", "oisseau", "birght"), choice("other_2", "viend", "oiseu", "brigt")
            ),
            template(
                localized("Welk woord is juist geschreven?", "Quel mot est écrit correctement ?", "Which word is written correctly?"),
                localized("Welk woord is juist geschreven", "Quel mot est écrit correctement", "Which word is written correctly"),
                "correct", choice("correct", "schrijven", "chemin", "school"),
                choice("other_1", "sgrijven", "chemain", "scool"), choice("other_2", "schreiven", "chamin", "scholl")
            ),
            template(
                localized("Welk woord is juist geschreven?", "Quel mot est écrit correctement ?", "Which word is written correctly?"),
                localized("Welk woord is juist geschreven", "Quel mot est écrit correctement", "Which word is written correctly"),
                "correct", choice("correct", "konijn", "beaucoup", "night"),
                choice("other_1", "konyn", "baucoup", "nihgt"), choice("other_2", "konnijn", "beacoup", "nite")
            )
        )

        PromptKind.EXACT_PHRASE -> listOf(
            template(
                localized("Zoek precies dezelfde woordgroep als: met de blauwe fiets", "Trouve exactement le même groupe de mots : avec le vélo bleu", "Find exactly the same phrase: with the blue bicycle"),
                localized("Zoek precies dezelfde woordgroep als met de blauwe fiets", "Trouve exactement le même groupe de mots, avec le vélo bleu", "Find exactly the same phrase, with the blue bicycle"),
                "phrase", choice("phrase", "met de blauwe fiets", "avec le vélo bleu", "with the blue bicycle"),
                choice("other_1", "met de bruine fiets", "avec le vélo vert", "with the green bicycle"), choice("other_2", "met het blauwe feest", "avec le beau vélo", "with the blue basket")
            ),
            template(
                localized("Zoek precies dezelfde woordgroep als: achter het hoge hek", "Trouve exactement le même groupe de mots : derrière la haute clôture", "Find exactly the same phrase: behind the tall fence"),
                localized("Zoek precies dezelfde woordgroep als achter het hoge hek", "Trouve exactement le même groupe de mots, derrière la haute clôture", "Find exactly the same phrase, behind the tall fence"),
                "phrase", choice("phrase", "achter het hoge hek", "derrière la haute clôture", "behind the tall fence"),
                choice("other_1", "achter de hoge heg", "devant la haute clôture", "behind the small fence"), choice("other_2", "onder het hoge hek", "derrière la porte", "beside the tall fence")
            ),
            template(
                localized("Zoek precies dezelfde woordgroep als: morgen na het ontbijt", "Trouve exactement le même groupe de mots : demain après le déjeuner", "Find exactly the same phrase: tomorrow after breakfast"),
                localized("Zoek precies dezelfde woordgroep als morgen na het ontbijt", "Trouve exactement le même groupe de mots, demain après le déjeuner", "Find exactly the same phrase, tomorrow after breakfast"),
                "phrase", choice("phrase", "morgen na het ontbijt", "demain après le déjeuner", "tomorrow after breakfast"),
                choice("other_1", "morgen voor het ontbijt", "demain avant le déjeuner", "tomorrow before breakfast"), choice("other_2", "vanmorgen na de speeltijd", "aujourd'hui après le déjeuner", "today after breakfast")
            )
        )
    }

    private fun localized(nl: String, fr: String, en: String): LocalizedText =
        LocalizedText(nl = nl, fr = fr, en = en)

    private fun template(
        prompt: LocalizedText,
        speech: LocalizedText,
        answerKey: String,
        vararg options: Choice
    ): Template = Template(prompt, speech, options.toList(), answerKey)

    private fun choice(
        key: String,
        nl: String,
        fr: String,
        en: String,
        speechNl: String? = null,
        speechFr: String? = null,
        speechEn: String? = null
    ): Choice = Choice(key, nl, fr, en, speechNl, speechFr, speechEn)

    private fun hintNl(kind: PromptKind): String = when (kind) {
        PromptKind.EXACT_WORD -> "Vergelijk het woord letter voor letter."
        PromptKind.GRAPHEME_PATTERN -> "Zoek precies dezelfde lettergroep."
        PromptKind.BLEND_SYLLABLES -> "Lees elk deel en voeg de delen daarna samen."
        PromptKind.COMPOUND_WORD -> "Lees de twee woorden en plak ze correct aan elkaar."
        PromptKind.LISTEN_AND_FIND_WORD -> "Luister naar het hele woord en controleer daarna alle letters."
        PromptKind.COMPLETE_SENTENCE -> "Lees de volledige zin en kies het woord dat past."
        PromptKind.CORRECT_WORD -> "Controleer de volgorde van elke letter."
        PromptKind.EXACT_PHRASE -> "Vergelijk elk woord en let ook op de volgorde."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.EXACT_WORD -> "Compare le mot lettre par lettre."
        PromptKind.GRAPHEME_PATTERN -> "Cherche exactement le même groupe de lettres."
        PromptKind.BLEND_SYLLABLES -> "Lis chaque partie, puis assemble-les."
        PromptKind.COMPOUND_WORD -> "Lis les deux mots et assemble-les correctement."
        PromptKind.LISTEN_AND_FIND_WORD -> "Écoute le mot entier, puis vérifie toutes les lettres."
        PromptKind.COMPLETE_SENTENCE -> "Lis toute la phrase et choisis le mot qui convient."
        PromptKind.CORRECT_WORD -> "Vérifie l'ordre de chaque lettre."
        PromptKind.EXACT_PHRASE -> "Compare chaque mot et observe aussi leur ordre."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.EXACT_WORD -> "Compare the word one letter at a time."
        PromptKind.GRAPHEME_PATTERN -> "Find exactly the same letter pattern."
        PromptKind.BLEND_SYLLABLES -> "Read each part and then blend the parts."
        PromptKind.COMPOUND_WORD -> "Read both words and join them correctly."
        PromptKind.LISTEN_AND_FIND_WORD -> "Listen to the whole word, then check every letter."
        PromptKind.COMPLETE_SENTENCE -> "Read the whole sentence and choose the word that fits."
        PromptKind.CORRECT_WORD -> "Check the order of every letter."
        PromptKind.EXACT_PHRASE -> "Compare every word and its position."
    }

    private data class Candidate(
        val kind: PromptKind,
        val templateIndex: Int,
        val prompt: LocalizedText,
        val speech: LocalizedText,
        val options: List<Choice>,
        val answerKey: String
    ) {
        fun signature(): String = "${kind.name}:$templateIndex:${options.joinToString(":") { it.key }}"
    }

    private data class Template(
        val prompt: LocalizedText,
        val speech: LocalizedText,
        val options: List<Choice>,
        val answerKey: String
    )

    private data class Choice(
        val key: String,
        val nl: String,
        val fr: String,
        val en: String,
        val speechNl: String? = null,
        val speechFr: String? = null,
        val speechEn: String? = null
    )

    private enum class Phase { GUIDED, CHECKPOINT }
    private enum class PromptKind {
        EXACT_WORD,
        GRAPHEME_PATTERN,
        BLEND_SYLLABLES,
        COMPOUND_WORD,
        LISTEN_AND_FIND_WORD,
        COMPLETE_SENTENCE,
        CORRECT_WORD,
        EXACT_PHRASE
    }
}
