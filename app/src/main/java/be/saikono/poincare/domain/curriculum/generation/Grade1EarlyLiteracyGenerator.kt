package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 1 early-literacy generator for book orientation,
 * picture/story understanding, and simple story structure.
 *
 * Version 1 uses tap-choice answers and language-neutral content keys. It does
 * not depend on a new child-facing lesson route.
 */
object Grade1EarlyLiteracyGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g1.language.early_literacy.story_awareness"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g1.language.early_literacy.story_awareness"
    private val allowedParameterKeys = setOf(
        "grade",
        "roadmap_entry",
        "phase",
        "editorial_status",
        "unit_id",
        "literacy_focus"
    )

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["grade"] != "1") add("grade must be 1")
        if (parameters["roadmap_entry"] != ROADMAP_ENTRY) {
            add("roadmap_entry must be $ROADMAP_ENTRY")
        }
        if (parameters["phase"] !in setOf("guided", "checkpoint")) {
            add("phase must be guided or checkpoint")
        }
        if (parameters["unit_id"].isNullOrBlank()) add("unit_id is required")
        if (parameters["literacy_focus"] !in FOCUS_KINDS.keys) add("literacy_focus is not supported")
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
        val focus = parameters.getValue("literacy_focus")
        val kinds = FOCUS_KINDS.getValue(focus)
        val random = StableLessonRandom(seed.value)
        val used = linkedSetOf<String>()
        val offset = if (phase == Phase.GUIDED) 0 else random.nextInt(kinds.size)

        return List(exerciseCount) { index ->
            val kind = kinds[(index + offset) % kinds.size]
            val candidate = uniqueCandidate(kind, random, used)
            exercise(seed, index, phase, focus, candidate)
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
        error("Unable to create a unique Grade 1 early-literacy exercise for ${kind.name}")
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
        focus: String,
        candidate: Candidate
    ): ExerciseContent {
        val answer = candidate.options.single { it.key == candidate.answerKey }
        val id = "generated.g1.language.early_literacy.v1.${seed.fingerprint}.${index + 1}"
        return ExerciseContent(
            id = id,
            domain = LearningDomain.READING,
            skill = "early_literacy_story_awareness",
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
                "literacyFocus" to focus,
                "promptKind" to candidate.kind.name.lowercase(),
                "contentKey" to "${candidate.kind.name.lowercase()}.${candidate.templateIndex + 1}",
                "masteryTag" to masteryTag(candidate.kind),
                "errorTags" to errorTag(candidate.kind),
                "visualSupport" to if (candidate.kind == PromptKind.PICTURE_CLUE) {
                    "SELF_CONTAINED_TEXTUAL_DESCRIPTION"
                } else {
                    "NOT_REQUIRED"
                },
                "answerRole" to "choice",
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
                "feedback_nl-BE" to hintNl(candidate.kind),
                "feedback_fr-BE" to hintFr(candidate.kind),
                "feedback_en-US" to hintEn(candidate.kind)
            ),
            answer = candidate.answerKey
        )
    }

    private fun spokenPrompt(visible: LocalizedText): LocalizedText = LocalizedText(
        nl = normalizeSpeech(visible.nl),
        fr = visible.fr?.let(::normalizeSpeech),
        en = visible.en?.let(::normalizeSpeech)
    )

    private fun normalizeSpeech(text: String): String = text
        .replace("?", "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun bank(kind: PromptKind): List<Template> = when (kind) {
        PromptKind.FRONT_COVER -> listOf(
            template(
                "Welk deel van een boek zie je meestal als eerste?",
                "Quelle partie d'un livre vois-tu généralement en premier ?",
                "Which part of a book do you usually see first?",
                "front_cover",
                choice("front_cover", "de voorkant", "la couverture avant", "the front cover"),
                choice("back_cover", "de achterkant", "la couverture arrière", "the back cover"),
                choice("inside_page", "een bladzijde binnenin", "une page intérieure", "an inside page")
            ),
            template(
                "Op welk deel van een boek staan vaak de titel en een grote prent?",
                "Sur quelle partie d'un livre trouve-t-on souvent le titre et une grande image ?",
                "On which part of a book do you often find the title and a large picture?",
                "front_cover",
                choice("front_cover", "op de voorkant", "sur la couverture avant", "on the front cover"),
                choice("last_page", "op de laatste bladzijde", "sur la dernière page", "on the last page"),
                choice("bookmark", "op een bladwijzer", "sur un marque-page", "on a bookmark")
            )
        )
        PromptKind.TITLE -> listOf(
            template(
                "Hoe noemen we de woorden die vertellen hoe een boek heet?",
                "Comment appelle-t-on les mots qui indiquent le nom d'un livre ?",
                "What do we call the words that tell us a book's name?",
                "title",
                choice("title", "de titel", "le titre", "the title"),
                choice("author", "de naam van de schrijver", "le nom de l'auteur", "the author's name"),
                choice("page_number", "het bladzijdenummer", "le numéro de page", "the page number")
            ),
            template(
                "Je zoekt de naam van het verhaal. Waar kijk je naar?",
                "Tu cherches le nom de l'histoire. Que regardes-tu ?",
                "You are looking for the story's name. What do you look for?",
                "title",
                choice("title", "de titel", "le titre", "the title"),
                choice("picture", "alleen de prent", "seulement l'image", "only the picture"),
                choice("barcode", "de streepjescode", "le code-barres", "the barcode")
            )
        )
        PromptKind.FIRST_EVENT -> listOf(
            template(
                "Mila trekt haar jas aan. Daarna loopt ze naar buiten. Wat gebeurt eerst?",
                "Mila met son manteau. Ensuite, elle sort. Que se passe-t-il d'abord ?",
                "Mila puts on her coat. Then she goes outside. What happens first?",
                "coat",
                choice("coat", "Mila trekt haar jas aan.", "Mila met son manteau.", "Mila puts on her coat."),
                choice("outside", "Mila loopt naar buiten.", "Mila sort.", "Mila goes outside."),
                choice("sleep", "Mila gaat slapen.", "Mila va dormir.", "Mila goes to sleep.")
            ),
            template(
                "Noor wast haar handen en eet daarna een appel. Wat gebeurt eerst?",
                "Noor se lave les mains puis mange une pomme. Que se passe-t-il d'abord ?",
                "Noor washes her hands and then eats an apple. What happens first?",
                "wash",
                choice("wash", "Noor wast haar handen.", "Noor se lave les mains.", "Noor washes her hands."),
                choice("eat", "Noor eet een appel.", "Noor mange une pomme.", "Noor eats an apple."),
                choice("cycle", "Noor fietst weg.", "Noor part à vélo.", "Noor rides away.")
            )
        )
        PromptKind.LAST_EVENT -> listOf(
            template(
                "Sam bouwt een toren. De toren valt om. Wat gebeurt als laatste?",
                "Sam construit une tour. La tour tombe. Que se passe-t-il en dernier ?",
                "Sam builds a tower. The tower falls down. What happens last?",
                "fall",
                choice("build", "Sam bouwt een toren.", "Sam construit une tour.", "Sam builds a tower."),
                choice("fall", "De toren valt om.", "La tour tombe.", "The tower falls down."),
                choice("swim", "Sam gaat zwemmen.", "Sam va nager.", "Sam goes swimming.")
            ),
            template(
                "Lina opent haar boek. Daarna leest ze de eerste bladzijde. Wat gebeurt als laatste?",
                "Lina ouvre son livre. Ensuite, elle lit la première page. Que se passe-t-il en dernier ?",
                "Lina opens her book. Then she reads the first page. What happens last?",
                "read",
                choice("open", "Lina opent haar boek.", "Lina ouvre son livre.", "Lina opens her book."),
                choice("read", "Lina leest de eerste bladzijde.", "Lina lit la première page.", "Lina reads the first page."),
                choice("close", "Lina sluit het raam.", "Lina ferme la fenêtre.", "Lina closes the window.")
            )
        )
        PromptKind.NEXT_EVENT -> listOf(
            template(
                "Omar plant een zaadje en geeft het water. Wat kan daarna gebeuren?",
                "Omar plante une graine et l'arrose. Que peut-il se passer ensuite ?",
                "Omar plants a seed and waters it. What could happen next?",
                "grow",
                choice("grow", "Er groeit een plantje.", "Une petite plante pousse.", "A small plant grows."),
                choice("dry", "Het zaadje heeft voortaan geen water meer nodig.", "La graine n'a désormais plus besoin d'eau.", "The seed no longer needs any water."),
                choice("instant", "Er staat meteen een grote plant zonder wortels.", "Une grande plante sans racines apparaît tout de suite.", "A large plant without roots appears immediately.")
            ),
            template(
                "Aya doet haar laarzen aan en neemt een paraplu. Wat zal ze waarschijnlijk doen?",
                "Aya met ses bottes et prend un parapluie. Que va-t-elle probablement faire ?",
                "Aya puts on her boots and takes an umbrella. What will she probably do?",
                "rain",
                choice("rain", "Ze gaat naar buiten in de regen.", "Elle sort sous la pluie.", "She goes outside in the rain."),
                choice("without_umbrella", "Ze laat de paraplu thuis en gaat zonder bescherming naar buiten.", "Elle laisse le parapluie à la maison et sort sans protection.", "She leaves the umbrella at home and goes outside unprotected."),
                choice("put_away", "Ze zet de laarzen en paraplu terug in de kast.", "Elle remet les bottes et le parapluie dans l'armoire.", "She puts the boots and umbrella back in the cupboard.")
            )
        )
        PromptKind.CHARACTER -> listOf(
            template(
                "Ravi zoekt zijn rode bal. Hij vindt hem onder de tafel. Over wie gaat het verhaal?",
                "Ravi cherche sa balle rouge. Il la trouve sous la table. De qui parle l'histoire ?",
                "Ravi looks for his red ball. He finds it under the table. Who is the story about?",
                "ravi",
                choice("ravi", "Ravi", "Ravi", "Ravi"),
                choice("teacher", "de juf", "la maîtresse", "the teacher"),
                choice("cat", "een kat", "un chat", "a cat")
            ),
            template(
                "Emma bakt samen met papa pannenkoeken. Emma roert het deeg. Over wie gaat dit stukje vooral?",
                "Emma prépare des crêpes avec papa. Emma mélange la pâte. De qui parle surtout ce petit texte ?",
                "Emma makes pancakes with Dad. Emma stirs the batter. Who is this short text mainly about?",
                "emma",
                choice("emma", "Emma", "Emma", "Emma"),
                choice("neighbour", "de buurman", "le voisin", "the neighbour"),
                choice("dog", "de hond", "le chien", "the dog")
            )
        )
        PromptKind.SETTING -> listOf(
            template(
                "Yara leent een boek en ziet lange rekken vol boeken. Waar is Yara?",
                "Yara emprunte un livre et voit de longues étagères remplies de livres. Où est Yara ?",
                "Yara borrows a book and sees long shelves full of books. Where is Yara?",
                "library",
                choice("library", "in de bibliotheek", "à la bibliothèque", "in the library"),
                choice("pool", "in het zwembad", "à la piscine", "at the swimming pool"),
                choice("bakery", "in de bakkerij", "à la boulangerie", "at the bakery")
            ),
            template(
                "Finn maakt een zandkasteel en hoort de golven. Waar is Finn?",
                "Finn construit un château de sable et entend les vagues. Où est Finn ?",
                "Finn builds a sandcastle and hears the waves. Where is Finn?",
                "beach",
                choice("beach", "op het strand", "à la plage", "at the beach"),
                choice("classroom", "in de klas", "dans la classe", "in the classroom"),
                choice("forest", "in het bos", "dans la forêt", "in the forest")
            )
        )
        PromptKind.PICTURE_CLUE -> listOf(
            template(
                "Lees de beschrijving: iedereen draagt een regenjas en er zijn plassen. Welk weer past daarbij?",
                "Lis la description : tout le monde porte un imperméable et il y a des flaques. Quel temps cela indique-t-il ?",
                "Read the description: everyone wears a raincoat and there are puddles. What weather does that suggest?",
                "rain",
                choice("rain", "Het regent.", "Il pleut.", "It is raining."),
                choice("hot", "Het is heel warm en droog.", "Il fait très chaud et sec.", "It is very hot and dry."),
                choice("night", "Iedereen slaapt.", "Tout le monde dort.", "Everyone is sleeping.")
            ),
            template(
                "Lees de beschrijving: het kind glimlacht en houdt een cadeau vast. Hoe voelt het kind zich waarschijnlijk?",
                "Lis la description : l'enfant sourit et tient un cadeau. Comment se sent-il probablement ?",
                "Read the description: the child smiles and holds a gift. How does the child probably feel?",
                "happy",
                choice("happy", "blij", "heureux", "happy"),
                choice("angry", "boos", "fâché", "angry"),
                choice("tired", "moe", "fatigué", "tired")
            )
        )
    }

    private fun template(
        nl: String,
        fr: String,
        en: String,
        answerKey: String,
        vararg options: Choice
    ): Template = Template(
        prompt = LocalizedText(nl = nl, fr = fr, en = en),
        options = options.toList(),
        answerKey = answerKey
    )

    private fun choice(key: String, nl: String, fr: String, en: String): Choice =
        Choice(key = key, nl = nl, fr = fr, en = en)

    private fun hintNl(kind: PromptKind): String = when (kind) {
        PromptKind.FRONT_COVER -> "Denk aan wat je ziet voordat je een boek opent."
        PromptKind.TITLE -> "De titel is de naam van het boek of verhaal."
        PromptKind.FIRST_EVENT -> "Zoek wat vóór het woord daarna gebeurt."
        PromptKind.LAST_EVENT -> "Zoek wat aan het einde gebeurt."
        PromptKind.NEXT_EVENT -> "Kies wat logisch bij het verhaal past."
        PromptKind.CHARACTER -> "Wie doet het meeste in het verhaal?"
        PromptKind.SETTING -> "Zoek woorden die vertellen waar het gebeurt."
        PromptKind.PICTURE_CLUE -> "Gebruik alleen de details uit de beschrijving."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.FRONT_COVER -> "Pense à ce que tu vois avant d'ouvrir un livre."
        PromptKind.TITLE -> "Le titre est le nom du livre ou de l'histoire."
        PromptKind.FIRST_EVENT -> "Cherche ce qui se passe avant le mot ensuite."
        PromptKind.LAST_EVENT -> "Cherche ce qui se passe à la fin."
        PromptKind.NEXT_EVENT -> "Choisis ce qui suit logiquement l'histoire."
        PromptKind.CHARACTER -> "Qui fait le plus de choses dans l'histoire ?"
        PromptKind.SETTING -> "Cherche les mots qui indiquent où cela se passe."
        PromptKind.PICTURE_CLUE -> "Utilise uniquement les détails de la description."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.FRONT_COVER -> "Think about what you see before opening a book."
        PromptKind.TITLE -> "The title is the name of the book or story."
        PromptKind.FIRST_EVENT -> "Find what happens before the word then."
        PromptKind.LAST_EVENT -> "Find what happens at the end."
        PromptKind.NEXT_EVENT -> "Choose what logically fits the story."
        PromptKind.CHARACTER -> "Who does most of the actions in the story?"
        PromptKind.SETTING -> "Look for words that tell where it happens."
        PromptKind.PICTURE_CLUE -> "Use only the details in the description."
    }

    private fun masteryTag(kind: PromptKind): String = when (kind) {
        PromptKind.FRONT_COVER -> "literacy.book_front_cover"
        PromptKind.TITLE -> "literacy.book_title"
        PromptKind.FIRST_EVENT -> "comprehension.first_event"
        PromptKind.LAST_EVENT -> "comprehension.last_event"
        PromptKind.NEXT_EVENT -> "comprehension.predict_next_event"
        PromptKind.CHARACTER -> "comprehension.main_character"
        PromptKind.SETTING -> "comprehension.setting"
        PromptKind.PICTURE_CLUE -> "comprehension.explicit_clue"
    }

    private fun errorTag(kind: PromptKind): String = when (kind) {
        PromptKind.FRONT_COVER -> "confuses_book_parts"
        PromptKind.TITLE -> "confuses_title_and_author"
        PromptKind.FIRST_EVENT -> "confuses_event_order"
        PromptKind.LAST_EVENT -> "confuses_event_order"
        PromptKind.NEXT_EVENT -> "selects_implausible_prediction"
        PromptKind.CHARACTER -> "confuses_main_character"
        PromptKind.SETTING -> "confuses_setting"
        PromptKind.PICTURE_CLUE -> "ignores_explicit_clue"
    }

    private data class Candidate(
        val kind: PromptKind,
        val templateIndex: Int,
        val prompt: LocalizedText,
        val options: List<Choice>,
        val answerKey: String
    ) {
        fun signature(): String =
            "${kind.name}:$templateIndex:${options.joinToString(":") { it.key }}"
    }

    private data class Template(
        val prompt: LocalizedText,
        val options: List<Choice>,
        val answerKey: String
    )

    private data class Choice(
        val key: String,
        val nl: String,
        val fr: String,
        val en: String
    )

    private enum class Phase {
        GUIDED,
        CHECKPOINT
    }

    private enum class PromptKind {
        FRONT_COVER,
        TITLE,
        FIRST_EVENT,
        LAST_EVENT,
        NEXT_EVENT,
        CHARACTER,
        SETTING,
        PICTURE_CLUE
    }

    private val FOCUS_KINDS = mapOf(
        "book_orientation_text_types" to listOf(PromptKind.FRONT_COVER, PromptKind.TITLE, PromptKind.PICTURE_CLUE),
        "sentence_short_text" to listOf(PromptKind.FIRST_EVENT, PromptKind.LAST_EVENT, PromptKind.CHARACTER, PromptKind.SETTING, PromptKind.PICTURE_CLUE),
        "predict_connect_retell" to listOf(PromptKind.NEXT_EVENT, PromptKind.FIRST_EVENT, PromptKind.LAST_EVENT, PromptKind.CHARACTER, PromptKind.SETTING)
    )
}
