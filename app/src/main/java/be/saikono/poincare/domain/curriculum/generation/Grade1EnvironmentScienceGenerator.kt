package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 1 environment and elementary-science generator.
 *
 * The generated slice checks elementary concepts and safe scenario choices.
 * Outdoor observations and experiments remain in the adult-verified practical
 * activity and are never inferred from a tap-choice score.
 */
object Grade1EnvironmentScienceGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g1.world.environment_science"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g1.world.environment_science"
    private val allowedParameterKeys = setOf("grade", "roadmap_entry", "phase", "editorial_status", "unit_id")

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["grade"] != "1") add("grade must be 1")
        if (parameters["roadmap_entry"] != ROADMAP_ENTRY) add("roadmap_entry must be $ROADMAP_ENTRY")
        if (parameters["phase"] !in setOf("guided", "checkpoint")) add("phase must be guided or checkpoint")
        val unitId = parameters["unit_id"].orEmpty()
        if (unitId.isBlank()) add("unit_id is required")
        else if (!Grade1UnitKnowledgeProfiles.supports(unitId)) add("unit_id has no reviewed environment/science profile")
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
        val profile = Grade1UnitKnowledgeProfiles.require(parameters.getValue("unit_id"))
        val random = StableLessonRandom(seed.value)
        val candidates = profile.facts.flatMapIndexed { factIndex, fact ->
            (0..2).map { mode ->
                ProfileCandidate(
                    key = "fact.${factIndex + 1}.mode.${mode + 1}",
                    prompt = profilePrompt(profile.title, mode),
                    answer = fact,
                    distractors = Grade1UnitKnowledgeProfiles.distractors(profile.unitId, factIndex)
                )
            }
        }
        val offset = if (phase == Phase.GUIDED) 0 else random.nextInt(candidates.size)
        return List(exerciseCount) { index ->
            profileExercise(seed, index, phase, profile, candidates[(index + offset) % candidates.size], random)
        }
    }

    private fun profilePrompt(title: LocalizedText, mode: Int): LocalizedText = when (mode) {
        0 -> LocalizedText(
            "Welke concrete uitspraak hoort bij ${title.nl}?",
            "Quelle affirmation concrète correspond à ${title.fr} ?",
            "Which concrete statement belongs with ${title.en}?"
        )
        1 -> LocalizedText(
            "Je onderzoekt ${title.nl}. Welke keuze gebruik je?",
            "Tu étudies ${title.fr}. Quel choix utilises-tu ?",
            "You are investigating ${title.en}. Which choice do you use?"
        )
        else -> LocalizedText(
            "Welk voorbeeld toont wat je bij ${title.nl} hebt geleerd?",
            "Quel exemple montre ce que tu as appris sur ${title.fr} ?",
            "Which example shows what you learned about ${title.en}?"
        )
    }

    private fun profileExercise(
        seed: LessonGenerationSeed,
        index: Int,
        phase: Phase,
        profile: Grade1UnitKnowledgeProfile,
        candidate: ProfileCandidate,
        random: StableLessonRandom
    ): ExerciseContent {
        val options = (listOf(ProfileOption(candidate.answer, true)) +
            candidate.distractors.map { ProfileOption(it, false) }).toMutableList()
        for (position in options.lastIndex downTo 1) {
            val other = random.nextInt(position + 1)
            val value = options[position]
            options[position] = options[other]
            options[other] = value
        }
        val answer = options.single { it.correct }.text
        return ExerciseContent(
            id = "generated.g1.world.environment_science.v1.${seed.fingerprint}.${index + 1}",
            domain = LearningDomain.WORLD_KNOWLEDGE,
            skill = "environment_science",
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
                "unitId" to profile.unitId,
                "phase" to phase.name.lowercase(),
                "promptKind" to "unit_specific_concept",
                "questionIntent" to "UNIT_SPECIFIC_ENVIRONMENT_SCIENCE",
                "contentKey" to candidate.key,
                "answerRole" to "choice",
                "automatedScope" to "unit_specific_concept_scenario_choice",
                "requiresAdultObservationExperiment" to "true",
                "masteryTag" to "world.${profile.unitId.substringAfter("unit.g1.").replace('.', '_')}",
                "errorTags" to "misconception.unit_specific_environment_science",
                "options_nl-BE" to options.joinToString("\n") { it.text.nl },
                "options_fr-BE" to options.joinToString("\n") { it.text.fr.orEmpty() },
                "options_en-US" to options.joinToString("\n") { it.text.en.orEmpty() },
                "answer_nl-BE" to answer.nl,
                "answer_fr-BE" to answer.fr.orEmpty(),
                "answer_en-US" to answer.en.orEmpty(),
                "hint_nl-BE" to profile.warning.nl,
                "hint_fr-BE" to profile.warning.fr.orEmpty(),
                "hint_en-US" to profile.warning.en.orEmpty(),
                "feedback_nl-BE" to profile.warning.nl,
                "feedback_fr-BE" to profile.warning.fr.orEmpty(),
                "feedback_en-US" to profile.warning.en.orEmpty()
            ),
            answer = answer.nl
        )
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
        error("Unable to create a unique Grade 1 environment/science exercise for ${kind.name}")
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
            id = "generated.g1.world.environment_science.v1.${seed.fingerprint}.${index + 1}",
            domain = LearningDomain.WORLD_KNOWLEDGE,
            skill = "environment_science",
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
                "automatedScope" to "concept_scenario_choice",
                "requiresAdultObservationExperiment" to "true",
                "masteryTag" to "world.${candidate.kind.name.lowercase()}",
                "errorTags" to "misconception.${candidate.kind.name.lowercase()}",
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
                "feedback_nl-BE" to "Kijk opnieuw naar het kenmerk of de veilige handeling die in de vraag wordt genoemd.",
                "feedback_fr-BE" to "Observe à nouveau la propriété ou l'action sûre mentionnée dans la question.",
                "feedback_en-US" to "Look again at the property or safe action named in the question."
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
        PromptKind.LIVING_NONLIVING -> listOf(
            template(
                "Welke keuze leeft en groeit?", "Quel élément vit et grandit ?", "Which choice is alive and grows?", "living",
                choice("living", "een jonge plant", "une jeune plante", "a young plant"),
                choice("stone", "een steen", "une pierre", "a stone"),
                choice("spoon", "een lepel", "une cuillère", "a spoon")
            ),
            template(
                "Waaraan merk je dat een slak een levend dier is?", "Comment sais-tu qu'un escargot est un animal vivant ?", "How do you know a snail is a living animal?", "living_sign",
                choice("living_sign", "Ze groeit en heeft voedsel nodig.", "Il grandit et a besoin de nourriture.", "It grows and needs food."),
                choice("made", "Ze is in een fabriek gemaakt.", "Il est fabriqué dans une usine.", "It is made in a factory."),
                choice("unchanging", "Ze blijft altijd precies hetzelfde.", "Il reste toujours exactement pareil.", "It always stays exactly the same.")
            )
        )

        PromptKind.PLANT_NEEDS -> listOf(
            template(
                "De aarde van een plant is erg droog. Wat heeft de plant nodig?", "La terre d'une plante est très sèche. De quoi la plante a-t-elle besoin ?", "A plant's soil is very dry. What does the plant need?", "water",
                choice("water", "water", "de l'eau", "water"),
                choice("toy", "een speelgoedauto", "une petite voiture", "a toy car"),
                choice("paint", "verf", "de la peinture", "paint")
            ),
            template(
                "Wat helpt een plant om gezond te groeien?", "Qu'est-ce qui aide une plante à bien pousser ?", "What helps a plant grow well?", "needs",
                choice("needs", "licht en water", "de la lumière et de l'eau", "light and water"),
                choice("dark_box", "altijd in een donkere doos staan", "rester toujours dans une boîte sombre", "always staying in a dark box"),
                choice("plastic", "plastic blaadjes krijgen", "recevoir des feuilles en plastique", "getting plastic leaves")
            )
        )

        PromptKind.ANIMAL_HABITAT -> listOf(
            template(
                "Waar kan een vis goed leven?", "Où un poisson peut-il bien vivre ?", "Where can a fish live well?", "water",
                choice("water", "in schoon water", "dans de l'eau propre", "in clean water"),
                choice("tree", "hoog in een droge boom", "en haut d'un arbre sec", "high in a dry tree"),
                choice("box", "in een gesloten schoendoos", "dans une boîte à chaussures fermée", "in a closed shoe box")
            ),
            template(
                "Welke plek past het best bij een regenworm?", "Quel endroit convient le mieux à un ver de terre ?", "Which place suits an earthworm best?", "soil",
                choice("soil", "vochtige aarde", "de la terre humide", "moist soil"),
                choice("freezer", "een diepvriezer", "un congélateur", "a freezer"),
                choice("roof", "een heet dak", "un toit brûlant", "a hot roof")
            )
        )

        PromptKind.WEATHER_CHOICE -> listOf(
            template(
                "Het regent en je gaat naar buiten. Wat helpt je droog te blijven?", "Il pleut et tu vas dehors. Qu'est-ce qui t'aide à rester au sec ?", "It is raining and you are going outside. What helps you stay dry?", "raincoat",
                choice("raincoat", "een regenjas", "un imperméable", "a raincoat"),
                choice("slippers", "pantoffels", "des pantoufles", "slippers"),
                choice("towel", "alleen een kleine handdoek", "seulement une petite serviette", "only a small towel")
            ),
            template(
                "Het is warm en zonnig. Wat is een verstandige keuze buiten?", "Il fait chaud et ensoleillé. Quel choix est raisonnable dehors ?", "It is hot and sunny. What is a sensible choice outside?", "sun_safe",
                choice("sun_safe", "water drinken en een pet dragen", "boire de l'eau et porter une casquette", "drink water and wear a cap"),
                choice("winter", "een dikke winterjas aantrekken", "mettre un gros manteau d'hiver", "put on a heavy winter coat"),
                choice("no_water", "de hele dag niets drinken", "ne rien boire de toute la journée", "drink nothing all day")
            )
        )

        PromptKind.MATERIAL_PROPERTY -> listOf(
            template(
                "Welk materiaal neemt gemakkelijk water op?", "Quel matériau absorbe facilement l'eau ?", "Which material absorbs water easily?", "sponge",
                choice("sponge", "een spons", "une éponge", "a sponge"),
                choice("plastic", "een plastic lepel", "une cuillère en plastique", "a plastic spoon"),
                choice("metal", "een metalen sleutel", "une clé en métal", "a metal key")
            ),
            template(
                "Door welk voorwerp kun je meestal heen kijken?", "À travers quel objet peut-on généralement voir ?", "Which object can you usually see through?", "glass",
                choice("glass", "een schoon glazen raam", "une vitre propre", "a clean glass window"),
                choice("brick", "een baksteen", "une brique", "a brick"),
                choice("wood", "een houten plank", "une planche en bois", "a wooden board")
            )
        )

        PromptKind.SIMPLE_TECHNOLOGY -> listOf(
            template(
                "Waarom heeft een kar wielen?", "Pourquoi un chariot a-t-il des roues ?", "Why does a cart have wheels?", "roll",
                choice("roll", "zodat de kar gemakkelijker rolt", "pour que le chariot roule plus facilement", "so the cart rolls more easily"),
                choice("steer", "zodat de kar vanzelf de juiste kant op stuurt", "pour que le chariot se dirige tout seul dans la bonne direction", "so the cart steers itself in the right direction"),
                choice("tip", "zodat de kar nooit kan omvallen", "pour que le chariot ne puisse jamais basculer", "so the cart can never tip over")
            ),
            template(
                "Waarvoor gebruik je de ritssluiting van een jas?", "À quoi sert la fermeture éclair d'une veste ?", "What is a coat zipper used for?", "close",
                choice("close", "om de jas te openen en te sluiten", "à ouvrir et fermer la veste", "to open and close the coat"),
                choice("measure", "om je lengte te meten", "à mesurer ta taille", "to measure your height"),
                choice("draw", "om een tekening te kleuren", "à colorier un dessin", "to color a picture")
            )
        )

        PromptKind.OBSERVE_SAFELY -> listOf(
            template(
                "Welke zin is een echte waarneming van een blad?", "Quelle phrase est une vraie observation d'une feuille ?", "Which sentence is a real observation of a leaf?", "observation",
                choice("observation", "Het blad is groen en heeft kartelranden.", "La feuille est verte et a des bords dentelés.", "The leaf is green and has jagged edges."),
                choice("opinion", "Dit is het mooiste blad van de wereld.", "C'est la plus belle feuille du monde.", "This is the prettiest leaf in the world."),
                choice("guess", "Dit blad komt zeker van de oudste boom.", "Cette feuille vient sûrement du plus vieil arbre.", "This leaf must come from the oldest tree.")
            ),
            template(
                "Je vindt onbekende bessen buiten. Wat is de veilige keuze?", "Tu trouves des baies inconnues dehors. Quel est le choix prudent ?", "You find unknown berries outside. What is the safe choice?", "ask_adult",
                choice("ask_adult", "Niet proeven en het aan een volwassene vragen.", "Ne pas les goûter et demander à un adulte.", "Do not taste them and ask an adult."),
                choice("taste", "Eerst één bes proeven om te testen of ze veilig is.", "Goûter d'abord une baie pour vérifier si elle est sûre.", "Taste one berry first to test whether it is safe."),
                choice("take_home", "De bessen meenemen zonder het aan iemand te vertellen.", "Emporter les baies sans le dire à personne.", "Take the berries home without telling anyone.")
            )
        )

        PromptKind.DAILY_SELF_CARE -> listOf(
            template(
                "Wat doe je best voordat je gaat eten?", "Que vaut-il mieux faire avant de manger ?", "What should you do before eating?", "wash",
                choice("wash", "je handen wassen", "se laver les mains", "wash your hands"),
                choice("wipe", "je handen alleen aan je trui afvegen", "essuyer seulement tes mains sur ton pull", "only wipe your hands on your jumper"),
                choice("rinse", "je handen heel even zonder zeep natmaken", "mouiller brièvement tes mains sans savon", "briefly wet your hands without soap")
            ),
            template(
                "Waar blijft melk het best koel?", "Où le lait reste-t-il le mieux au frais ?", "Where does milk stay cool best?", "fridge",
                choice("fridge", "in de koelkast", "dans le réfrigérateur", "in the refrigerator"),
                choice("sun", "op een vensterbank in de zon", "sur un appui de fenêtre au soleil", "on a sunny windowsill"),
                choice("heater", "naast de verwarming", "près du radiateur", "next to a heater")
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
        PromptKind.LIVING_NONLIVING -> "Levende dingen groeien en hebben water of voedsel nodig."
        PromptKind.PLANT_NEEDS -> "Planten hebben onder andere water en licht nodig."
        PromptKind.ANIMAL_HABITAT -> "Kies een plek die lucht, water, voedsel en beschutting voor het dier kan bieden."
        PromptKind.WEATHER_CHOICE -> "Kijk naar regen, zon en temperatuur en kies passende bescherming."
        PromptKind.MATERIAL_PROPERTY -> "Denk aan wat het materiaal doorlaat of opneemt."
        PromptKind.SIMPLE_TECHNOLOGY -> "Een hulpmiddel is ontworpen om een taak gemakkelijker te maken."
        PromptKind.OBSERVE_SAFELY -> "Een waarneming beschrijft wat je echt ziet of voelt; proef nooit iets onbekends."
        PromptKind.DAILY_SELF_CARE -> "Kies wat hygiëne en veilig bewaren ondersteunt."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.LIVING_NONLIVING -> "Les êtres vivants grandissent et ont besoin d'eau ou de nourriture."
        PromptKind.PLANT_NEEDS -> "Les plantes ont notamment besoin d'eau et de lumière."
        PromptKind.ANIMAL_HABITAT -> "Choisis un lieu qui peut fournir de l'air, de l'eau, de la nourriture et un abri."
        PromptKind.WEATHER_CHOICE -> "Observe la pluie, le soleil et la température, puis choisis une protection adaptée."
        PromptKind.MATERIAL_PROPERTY -> "Pense à ce que le matériau laisse passer ou absorbe."
        PromptKind.SIMPLE_TECHNOLOGY -> "Un outil est conçu pour rendre une tâche plus facile."
        PromptKind.OBSERVE_SAFELY -> "Une observation décrit ce que tu vois ou sens vraiment ; ne goûte jamais quelque chose d'inconnu."
        PromptKind.DAILY_SELF_CARE -> "Choisis ce qui favorise l'hygiène et une conservation sûre."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.LIVING_NONLIVING -> "Living things grow and need water or food."
        PromptKind.PLANT_NEEDS -> "Plants need water and light, among other things."
        PromptKind.ANIMAL_HABITAT -> "Choose a place that can provide air, water, food, and shelter."
        PromptKind.WEATHER_CHOICE -> "Notice rain, sun, and temperature, then choose suitable protection."
        PromptKind.MATERIAL_PROPERTY -> "Think about what the material lets through or absorbs."
        PromptKind.SIMPLE_TECHNOLOGY -> "A tool is designed to make a task easier."
        PromptKind.OBSERVE_SAFELY -> "An observation describes what you truly see or feel; never taste something unknown."
        PromptKind.DAILY_SELF_CARE -> "Choose what supports hygiene and safe storage."
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
    private data class ProfileCandidate(
        val key: String,
        val prompt: LocalizedText,
        val answer: LocalizedText,
        val distractors: List<LocalizedText>
    )
    private data class ProfileOption(val text: LocalizedText, val correct: Boolean)
    private enum class Phase { GUIDED, CHECKPOINT }
    private enum class PromptKind {
        LIVING_NONLIVING,
        PLANT_NEEDS,
        ANIMAL_HABITAT,
        WEATHER_CHOICE,
        MATERIAL_PROPERTY,
        SIMPLE_TECHNOLOGY,
        OBSERVE_SAFELY,
        DAILY_SELF_CARE
    }
}
