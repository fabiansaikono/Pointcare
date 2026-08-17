package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 1 generator for comparing measures, early clock and money
 * understanding, and simple two-dimensional shapes.
 *
 * Version 1 deliberately uses numeric answers compatible with the existing
 * custom number pad. It does not require a new child-facing lesson screen.
 */
object Grade1MeasurementShapesGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g1.geometry.measurement_shapes.foundations"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g1.geometry.measurement_shapes.foundations"
    private val allowedParameterKeys = setOf(
        "grade",
        "roadmap_entry",
        "phase",
        "focus",
        "editorial_status"
    )

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) {
            add("unsupported parameters: ${unknown.sorted().joinToString()}")
        }
        if (parameters["grade"] != "1") add("grade must be 1")
        if (parameters["roadmap_entry"] != ROADMAP_ENTRY) {
            add("roadmap_entry must be $ROADMAP_ENTRY")
        }
        if (parameters["phase"] !in setOf("guided", "checkpoint")) {
            add("phase must be guided or checkpoint")
        }
        if (parameters["focus"] !in setOf(
                null,
                "mixed",
                "comparison",
                "measurement",
                "time",
                "money",
                "shapes"
            )
        ) {
            add("focus must be mixed, comparison, measurement, time, money, or shapes")
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
        val focus = Focus.from(parameters["focus"])
        val promptKinds = focus.promptKinds
        val random = StableLessonRandom(seed.value)
        val used = linkedSetOf<String>()
        val offset = if (phase == Phase.GUIDED) 0 else random.nextInt(promptKinds.size)

        return List(exerciseCount) { index ->
            val kind = promptKinds[(index + offset) % promptKinds.size]
            val candidate = uniqueCandidate(
                kind = kind,
                phase = phase,
                index = index,
                exerciseCount = exerciseCount,
                random = random,
                used = used
            )
            exercise(seed, index, phase, candidate)
        }
    }

    private fun uniqueCandidate(
        kind: PromptKind,
        phase: Phase,
        index: Int,
        exerciseCount: Int,
        random: StableLessonRandom,
        used: MutableSet<String>
    ): Candidate {
        repeat(128) {
            val candidate = createCandidate(kind, phase, index, exerciseCount, random)
            if (used.add(candidate.signature())) return candidate
        }
        return fallbackCandidates(kind).firstOrNull { used.add(it.signature()) }
            ?: error("Unable to create a unique Grade 1 measurement-and-shapes exercise for ${kind.name}")
    }

    private fun createCandidate(
        kind: PromptKind,
        phase: Phase,
        index: Int,
        exerciseCount: Int,
        random: StableLessonRandom
    ): Candidate {
        val guidedSimple = phase == Phase.GUIDED && index < (exerciseCount + 1) / 2
        val maximumLength = if (guidedSimple) 10 else 20

        return when (kind) {
            PromptKind.LONGER_LENGTH -> {
                val pair = distinctPair(maximumLength, random)
                Candidate(kind, pair.max(), pair, unit = "cm")
            }
            PromptKind.SHORTER_LENGTH -> {
                val pair = distinctPair(maximumLength, random)
                Candidate(kind, pair.min(), pair, unit = "cm")
            }
            PromptKind.REASONABLE_LENGTH -> {
                val item = EverydayObject.entries[random.nextInt(EverydayObject.entries.size)]
                val distractor = item.distractors[random.nextInt(item.distractors.size)]
                val options = if (random.nextInt(2) == 0) {
                    listOf(item.plausibleCentimetres, distractor)
                } else {
                    listOf(distractor, item.plausibleCentimetres)
                }
                Candidate(
                    kind = kind,
                    answer = item.plausibleCentimetres,
                    values = options,
                    label = item.name.lowercase(),
                    unit = "cm"
                )
            }
            PromptKind.HOUR_AFTER -> {
                val start = random.nextInt(1, 12)
                Candidate(kind, start + 1, listOf(start), unit = "hour")
            }
            PromptKind.HOUR_BEFORE -> {
                val start = random.nextInt(2, 13)
                Candidate(kind, start - 1, listOf(start), unit = "hour")
            }
            PromptKind.MONEY_TOTAL -> {
                val bank = moneySets(if (guidedSimple) MoneyDifficulty.SIMPLE else MoneyDifficulty.FULL)
                val coins = bank[random.nextInt(bank.size)]
                Candidate(kind, coins.sum(), coins, unit = "euro")
            }
            PromptKind.SHAPE_SIDES -> {
                val shape = Shape.entries[random.nextInt(Shape.entries.size)]
                Candidate(
                    kind = kind,
                    answer = shape.straightSides,
                    values = listOf(shape.straightSides, shape.corners),
                    label = shape.name.lowercase(),
                    unit = "count"
                )
            }
            PromptKind.SHAPE_CORNERS -> {
                val shape = Shape.entries[random.nextInt(Shape.entries.size)]
                Candidate(
                    kind = kind,
                    answer = shape.corners,
                    values = listOf(shape.straightSides, shape.corners),
                    label = shape.name.lowercase(),
                    unit = "count"
                )
            }
        }
    }

    private fun distinctPair(maximum: Int, random: StableLessonRandom): List<Int> {
        val first = random.nextInt(1, maximum + 1)
        var second = random.nextInt(1, maximum + 1)
        while (second == first) second = random.nextInt(1, maximum + 1)
        return listOf(first, second)
    }

    private fun fallbackCandidates(kind: PromptKind): Sequence<Candidate> = sequence {
        when (kind) {
            PromptKind.LONGER_LENGTH -> for (first in 1..19) {
                yield(Candidate(kind, first + 1, listOf(first, first + 1), unit = "cm"))
            }
            PromptKind.SHORTER_LENGTH -> for (first in 1..19) {
                yield(Candidate(kind, first, listOf(first + 1, first), unit = "cm"))
            }
            PromptKind.REASONABLE_LENGTH -> for (item in EverydayObject.entries) {
                for (distractor in item.distractors) {
                    yield(
                        Candidate(
                            kind,
                            item.plausibleCentimetres,
                            listOf(item.plausibleCentimetres, distractor),
                            item.name.lowercase(),
                            "cm"
                        )
                    )
                }
            }
            PromptKind.HOUR_AFTER -> for (start in 1..11) {
                yield(Candidate(kind, start + 1, listOf(start), unit = "hour"))
            }
            PromptKind.HOUR_BEFORE -> for (start in 2..12) {
                yield(Candidate(kind, start - 1, listOf(start), unit = "hour"))
            }
            PromptKind.MONEY_TOTAL -> for (coins in moneySets(MoneyDifficulty.FULL)) {
                yield(Candidate(kind, coins.sum(), coins, unit = "euro"))
            }
            PromptKind.SHAPE_SIDES -> for (shape in Shape.entries) {
                yield(
                    Candidate(
                        kind,
                        shape.straightSides,
                        listOf(shape.straightSides, shape.corners),
                        shape.name.lowercase(),
                        "count"
                    )
                )
            }
            PromptKind.SHAPE_CORNERS -> for (shape in Shape.entries) {
                yield(
                    Candidate(
                        kind,
                        shape.corners,
                        listOf(shape.straightSides, shape.corners),
                        shape.name.lowercase(),
                        "count"
                    )
                )
            }
        }
    }

    private fun exercise(
        seed: LessonGenerationSeed,
        index: Int,
        phase: Phase,
        candidate: Candidate
    ): ExerciseContent {
        val id = "generated.g1.geometry.measurement_shapes.v1.${seed.fingerprint}.${index + 1}"
        val prompt = visiblePrompt(candidate)
        return ExerciseContent(
            id = id,
            domain = LearningDomain.GEOMETRY,
            skill = "measurement_shapes",
            section = if (phase == Phase.GUIDED) ExerciseSection.PRACTICE else ExerciseSection.TESTS,
            level = SkillLevel.FIRST_GRADE,
            inputMode = InputMode.CUSTOM_NUMBER_PAD,
            prompt = prompt,
            speechText = spokenPrompt(prompt),
            data = linkedMapOf(
                "generatorId" to generatorId,
                "generatorVersion" to generatorVersion.toString(),
                "seedFingerprint" to seed.fingerprint,
                "roadmapEntry" to ROADMAP_ENTRY,
                "phase" to phase.name.lowercase(),
                "focus" to Focus.forPromptKind(candidate.kind).parameterValue,
                "promptKind" to candidate.kind.name.lowercase(),
                "answerRole" to "number",
                "values" to candidate.values.joinToString(","),
                "label" to (candidate.label ?: ""),
                "unit" to candidate.unit,
                "hint_nl-BE" to hintNl(candidate.kind),
                "hint_fr-BE" to hintFr(candidate.kind),
                "hint_en-US" to hintEn(candidate.kind)
            ),
            answer = candidate.answer.toString()
        )
    }

    private fun visiblePrompt(candidate: Candidate): LocalizedText = when (candidate.kind) {
        PromptKind.LONGER_LENGTH -> LocalizedText(
            nl = "Een rood lint is ${candidate.values[0]} cm lang en een blauw lint is ${candidate.values[1]} cm lang. Hoeveel centimeter lang is het langste lint?",
            fr = "Un ruban rouge mesure ${candidate.values[0]} cm et un ruban bleu ${candidate.values[1]} cm. Quelle est la longueur du ruban le plus long, en centimètres ?",
            en = "A red ribbon is ${candidate.values[0]} cm long and a blue ribbon is ${candidate.values[1]} cm long. How many centimeters long is the longer ribbon?"
        )
        PromptKind.SHORTER_LENGTH -> LocalizedText(
            nl = "Een rood lint is ${candidate.values[0]} cm lang en een blauw lint is ${candidate.values[1]} cm lang. Hoeveel centimeter lang is het kortste lint?",
            fr = "Un ruban rouge mesure ${candidate.values[0]} cm et un ruban bleu ${candidate.values[1]} cm. Quelle est la longueur du ruban le plus court, en centimètres ?",
            en = "A red ribbon is ${candidate.values[0]} cm long and a blue ribbon is ${candidate.values[1]} cm long. How many centimeters long is the shorter ribbon?"
        )
        PromptKind.REASONABLE_LENGTH -> {
            val item = EverydayObject.valueOf(candidate.label!!.uppercase())
            LocalizedText(
                nl = "Welke lengte past het best bij ${item.nl}: ${candidate.values[0]} cm of ${candidate.values[1]} cm? Vul de beste lengte in.",
                fr = "Quelle longueur convient le mieux pour ${item.fr} : ${candidate.values[0]} cm ou ${candidate.values[1]} cm ? Indique la meilleure longueur.",
                en = "Which length is more reasonable for ${item.en}: ${candidate.values[0]} cm or ${candidate.values[1]} cm? Enter the best length."
            )
        }
        PromptKind.HOUR_AFTER -> {
            val start = candidate.values[0]
            LocalizedText(
                nl = "Het is $start uur. Welk uur is het één uur later?",
                fr = "Il est $start heures. Quelle heure sera-t-il une heure plus tard ?",
                en = "It is $start o'clock. What hour will it be one hour later?"
            )
        }
        PromptKind.HOUR_BEFORE -> {
            val start = candidate.values[0]
            LocalizedText(
                nl = "Het is $start uur. Welk uur was het één uur eerder?",
                fr = "Il est $start heures. Quelle heure était-il une heure plus tôt ?",
                en = "It is $start o'clock. What hour was it one hour earlier?"
            )
        }
        PromptKind.MONEY_TOTAL -> {
            val nlCoins = candidate.values.joinToString(" + ") { "$it euro" }
            val frCoins = candidate.values.joinToString(" + ") {
                if (it == 1) "1 euro" else "$it euros"
            }
            val enCoins = candidate.values.joinToString(" + ") {
                if (it == 1) "1 euro" else "$it euros"
            }
            LocalizedText(
                nl = "Je hebt $nlCoins. Hoeveel euro is dat samen?",
                fr = "Tu as $frCoins. Combien d'euros as-tu en tout ?",
                en = "You have $enCoins. How many euros do you have altogether?"
            )
        }
        PromptKind.SHAPE_SIDES -> {
            val shape = Shape.valueOf(candidate.label!!.uppercase())
            LocalizedText(
                nl = "Hoeveel rechte zijden heeft ${shape.nlArticle} ${shape.nl}?",
                fr = "Combien de côtés droits possède ${shape.frArticle} ${shape.fr} ?",
                en = "How many straight sides does ${shape.enArticle} ${shape.en} have?"
            )
        }
        PromptKind.SHAPE_CORNERS -> {
            val shape = Shape.valueOf(candidate.label!!.uppercase())
            LocalizedText(
                nl = "Hoeveel hoeken heeft ${shape.nlArticle} ${shape.nl}?",
                fr = "Combien de coins possède ${shape.frArticle} ${shape.fr} ?",
                en = "How many corners does ${shape.enArticle} ${shape.en} have?"
            )
        }
    }

    private fun spokenPrompt(visible: LocalizedText): LocalizedText {
        fun normalize(text: String, centimetres: String): String = text
            .replace(Regex("\\bcm\\b"), centimetres)
            .replace("+", " plus ")
            .replace("?", "")
            .replace(Regex("\\s+"), " ")
            .trim()

        return LocalizedText(
            nl = normalize(visible.nl, "centimeter"),
            fr = visible.fr?.let { normalize(it, "centimètres") },
            en = visible.en?.let { normalize(it, "centimeters") }
        )
    }

    private fun hintNl(kind: PromptKind): String = when (kind) {
        PromptKind.LONGER_LENGTH -> "Vergelijk de twee lengtes en kies het grootste getal."
        PromptKind.SHORTER_LENGTH -> "Vergelijk de twee lengtes en kies het kleinste getal."
        PromptKind.REASONABLE_LENGTH -> "Denk aan hoe groot het voorwerp in het echt is."
        PromptKind.HOUR_AFTER -> "Ga één uur vooruit op de klok."
        PromptKind.HOUR_BEFORE -> "Ga één uur terug op de klok."
        PromptKind.MONEY_TOTAL -> "Tel de waarden van alle munten samen."
        PromptKind.SHAPE_SIDES -> "Volg de rechte randen van de vorm en tel ze."
        PromptKind.SHAPE_CORNERS -> "Wijs elke hoek één keer aan en tel."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.LONGER_LENGTH -> "Compare les deux longueurs et choisis le plus grand nombre."
        PromptKind.SHORTER_LENGTH -> "Compare les deux longueurs et choisis le plus petit nombre."
        PromptKind.REASONABLE_LENGTH -> "Pense à la taille réelle de l'objet."
        PromptKind.HOUR_AFTER -> "Avance d'une heure sur l'horloge."
        PromptKind.HOUR_BEFORE -> "Recule d'une heure sur l'horloge."
        PromptKind.MONEY_TOTAL -> "Additionne la valeur de toutes les pièces."
        PromptKind.SHAPE_SIDES -> "Suis les bords droits de la forme et compte-les."
        PromptKind.SHAPE_CORNERS -> "Montre chaque coin une fois et compte."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.LONGER_LENGTH -> "Compare the two lengths and choose the larger number."
        PromptKind.SHORTER_LENGTH -> "Compare the two lengths and choose the smaller number."
        PromptKind.REASONABLE_LENGTH -> "Think about the object's real-life size."
        PromptKind.HOUR_AFTER -> "Move one hour forward on the clock."
        PromptKind.HOUR_BEFORE -> "Move one hour back on the clock."
        PromptKind.MONEY_TOTAL -> "Add the values of all the coins."
        PromptKind.SHAPE_SIDES -> "Trace the straight edges of the shape and count them."
        PromptKind.SHAPE_CORNERS -> "Point to each corner once and count."
    }

    private data class Candidate(
        val kind: PromptKind,
        val answer: Int,
        val values: List<Int>,
        val label: String? = null,
        val unit: String
    ) {
        fun signature(): String =
            "${kind.name}:${answer}:${values.joinToString(":")}:${label.orEmpty()}:$unit"
    }

    private enum class MoneyDifficulty { SIMPLE, FULL }

    private fun moneySets(difficulty: MoneyDifficulty): List<List<Int>> {
        val simple = listOf(
            listOf(1, 1), listOf(1, 2), listOf(2, 2),
            listOf(1, 1, 1), listOf(1, 1, 2), listOf(1, 2, 2), listOf(2, 2, 2)
        )
        if (difficulty == MoneyDifficulty.SIMPLE) return simple
        return simple + listOf(
            listOf(1, 5), listOf(2, 5), listOf(5, 5),
            listOf(1, 10), listOf(2, 10), listOf(5, 10), listOf(10, 10),
            listOf(1, 1, 5), listOf(1, 2, 5), listOf(2, 2, 5),
            listOf(1, 5, 5), listOf(2, 5, 5), listOf(5, 5, 5),
            listOf(1, 1, 10), listOf(1, 2, 10), listOf(2, 2, 10),
            listOf(1, 5, 10), listOf(2, 5, 10)
        )
    }

    private enum class Phase {
        GUIDED,
        CHECKPOINT
    }

    private enum class Focus(
        val parameterValue: String,
        val promptKinds: List<PromptKind>
    ) {
        MIXED("mixed", PromptKind.entries),
        COMPARISON(
            "comparison",
            listOf(
                PromptKind.LONGER_LENGTH,
                PromptKind.SHORTER_LENGTH
            )
        ),
        MEASUREMENT("measurement", listOf(PromptKind.REASONABLE_LENGTH)),
        TIME("time", listOf(PromptKind.HOUR_AFTER, PromptKind.HOUR_BEFORE)),
        MONEY("money", listOf(PromptKind.MONEY_TOTAL)),
        SHAPES("shapes", listOf(PromptKind.SHAPE_SIDES, PromptKind.SHAPE_CORNERS));

        companion object {
            fun from(value: String?): Focus = entries.firstOrNull {
                it.parameterValue == (value ?: "mixed")
            } ?: error("Unsupported Grade 1 measurement focus: $value")

            fun forPromptKind(kind: PromptKind): Focus = when (kind) {
                PromptKind.LONGER_LENGTH,
                PromptKind.SHORTER_LENGTH -> COMPARISON
                PromptKind.REASONABLE_LENGTH -> MEASUREMENT
                PromptKind.HOUR_AFTER,
                PromptKind.HOUR_BEFORE -> TIME
                PromptKind.MONEY_TOTAL -> MONEY
                PromptKind.SHAPE_SIDES,
                PromptKind.SHAPE_CORNERS -> SHAPES
            }
        }
    }

    private enum class PromptKind {
        LONGER_LENGTH,
        SHORTER_LENGTH,
        REASONABLE_LENGTH,
        HOUR_AFTER,
        HOUR_BEFORE,
        MONEY_TOTAL,
        SHAPE_SIDES,
        SHAPE_CORNERS
    }

    private enum class EverydayObject(
        val nl: String,
        val fr: String,
        val en: String,
        val plausibleCentimetres: Int,
        val distractors: IntArray
    ) {
        PENCIL("een potlood", "un crayon", "a pencil", 15, intArrayOf(2, 80)),
        ERASER("een gom", "une gomme", "an eraser", 5, intArrayOf(40, 50)),
        SCHOOLBOOK("een schoolboek", "un livre scolaire", "a schoolbook", 25, intArrayOf(3, 80)),
        SPOON("een lepel", "une cuillère", "a spoon", 12, intArrayOf(2, 60)),
        SHOE("een kinderschoen", "une chaussure d'enfant", "a child's shoe", 20, intArrayOf(3, 70)),
        TOOTHBRUSH("een tandenborstel", "une brosse à dents", "a toothbrush", 18, intArrayOf(2, 60))
    }

    private enum class Shape(
        val nl: String,
        val nlArticle: String,
        val fr: String,
        val frArticle: String,
        val en: String,
        val enArticle: String,
        val straightSides: Int,
        val corners: Int
    ) {
        TRIANGLE("driehoek", "een", "triangle", "un", "triangle", "a", 3, 3),
        SQUARE("vierkant", "een", "carré", "un", "square", "a", 4, 4),
        RECTANGLE("rechthoek", "een", "rectangle", "un", "rectangle", "a", 4, 4),
        CIRCLE("cirkel", "een", "cercle", "un", "circle", "a", 0, 0),
        OVAL("ovaal", "een", "ovale", "un", "oval", "an", 0, 0)
    }
}
