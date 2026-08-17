package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

object Grade1AdditionSubtractionGenerator : LessonExerciseGenerator {
    override val generatorId: String =
        "generator.g1.math.addition_subtraction.foundations"
    override val generatorVersion: Int = 1

    private val allowedParameterKeys = setOf(
        "grade",
        "operation",
        "phase",
        "max_result",
        "concrete_objects",
        "editorial_status"
    )

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) {
            add("unsupported parameters: ${unknown.sorted().joinToString()}")
        }
        if (parameters["grade"] != "1") add("grade must be 1")
        if (parameters["operation"] !in setOf("addition", "subtraction")) {
            add("operation must be addition or subtraction")
        }
        if (parameters["phase"] !in setOf("guided", "checkpoint")) {
            add("phase must be guided or checkpoint")
        }
        if (parameters["max_result"]?.toIntOrNull() !in setOf(null, 5, 10, 20)) {
            add("max_result must be 5, 10 or 20")
        }
        if (parameters["concrete_objects"] !in setOf(null, "true", "false")) {
            add("concrete_objects must be true or false")
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

        val operation = Operation.valueOf(parameters.getValue("operation").uppercase())
        val phase = Phase.valueOf(parameters.getValue("phase").uppercase())
        val random = StableLessonRandom(seed.value)
        val used = linkedSetOf<String>()

        return List(exerciseCount) { index ->
            val kind = if (parameters["concrete_objects"] == "true") PromptKind.STORY else promptKind(index, phase, random)
            val fact = uniqueFact(
                operation = operation,
                phase = phase,
                index = index,
                exerciseCount = exerciseCount,
                kind = kind,
                random = random,
                used = used,
                maximumResult = parameters["max_result"]?.toIntOrNull()
            )
            exercise(
                seed = seed,
                index = index,
                operation = operation,
                phase = phase,
                kind = kind,
                fact = fact,
                concreteObjects = parameters["concrete_objects"] == "true"
            )
        }
    }

    private fun promptKind(
        index: Int,
        phase: Phase,
        random: StableLessonRandom
    ): PromptKind {
        val offset = if (phase == Phase.GUIDED) 0 else random.nextInt(PromptKind.entries.size)
        return PromptKind.entries[(index + offset) % PromptKind.entries.size]
    }

    private fun uniqueFact(
        operation: Operation,
        phase: Phase,
        index: Int,
        exerciseCount: Int,
        kind: PromptKind,
        random: StableLessonRandom,
        used: MutableSet<String>,
        maximumResult: Int?
    ): Fact {
        repeat(96) {
            val candidate = createFact(operation, phase, index, exerciseCount, random, maximumResult)
            val key = "${operation.name}:${candidate.left}:${candidate.right}:${kind.name}"
            if (used.add(key)) return candidate
        }

        val maximum = maximumResult ?: 20
        val candidates = when (operation) {
            Operation.ADDITION -> buildList {
                for (sum in 1..maximum) {
                    for (left in 0..sum) {
                        add(Fact(left = left, right = sum - left, result = sum))
                    }
                }
            }
            Operation.SUBTRACTION -> buildList {
                for (left in 1..maximum) {
                    for (right in 0..left) {
                        add(Fact(left = left, right = right, result = left - right))
                    }
                }
            }
        }
        return candidates.firstOrNull { candidate ->
            used.add("${operation.name}:${candidate.left}:${candidate.right}:${kind.name}")
        } ?: error("Unable to create a unique grade 1 arithmetic exercise")
    }

    private fun createFact(
        operation: Operation,
        phase: Phase,
        index: Int,
        exerciseCount: Int,
        random: StableLessonRandom,
        maximumResult: Int?
    ): Fact {
        val useWithinTen = phase == Phase.GUIDED && index < (exerciseCount + 1) / 2
        val maximum = maximumResult ?: if (useWithinTen) 10 else 20
        val requestCrossingTen = maximum == 20 && index % 3 == 1

        return when (operation) {
            Operation.ADDITION -> {
                if (requestCrossingTen) {
                    val left = random.nextInt(3, 10)
                    val minimumRight = (11 - left).coerceAtLeast(2)
                    val maximumRightExclusive = (21 - left).coerceAtLeast(minimumRight + 1)
                    val right = random.nextInt(minimumRight, maximumRightExclusive)
                    Fact(left = left, right = right, result = left + right)
                } else {
                    val result = random.nextInt(1, maximum + 1)
                    val left = random.nextInt(result + 1)
                    Fact(left = left, right = result - left, result = result)
                }
            }
            Operation.SUBTRACTION -> {
                if (requestCrossingTen) {
                    val left = random.nextInt(11, 21)
                    val minimumRight = (left - 9).coerceAtLeast(2)
                    val right = random.nextInt(minimumRight, left + 1)
                    Fact(left = left, right = right, result = left - right)
                } else {
                    val left = random.nextInt(1, maximum + 1)
                    val right = random.nextInt(left + 1)
                    Fact(left = left, right = right, result = left - right)
                }
            }
        }
    }

    private fun exercise(
        seed: LessonGenerationSeed,
        index: Int,
        operation: Operation,
        phase: Phase,
        kind: PromptKind,
        fact: Fact,
        concreteObjects: Boolean
    ): ExerciseContent {
        val answerRole = when (kind) {
            PromptKind.RESULT,
            PromptKind.STORY -> AnswerRole.RESULT
            PromptKind.MISSING_PART,
            PromptKind.INVERSE -> AnswerRole.RIGHT
        }
        val answer = when (answerRole) {
            AnswerRole.RESULT -> fact.result
            AnswerRole.RIGHT -> fact.right
        }
        val prompt = if (concreteObjects) concreteObjectPrompt(operation) else visiblePrompt(operation, kind, fact)
        val speech = if (concreteObjects) prompt else spokenPrompt(operation, kind, fact)
        val id = "generated.g1.math.addition_subtraction.v1.${seed.fingerprint}.${index + 1}"

        return ExerciseContent(
            id = id,
            domain = LearningDomain.MATH,
            skill = "addition_subtraction",
            section = if (phase == Phase.GUIDED) {
                ExerciseSection.PRACTICE
            } else {
                ExerciseSection.TESTS
            },
            level = SkillLevel.FIRST_GRADE,
            inputMode = InputMode.CUSTOM_NUMBER_PAD,
            prompt = prompt,
            speechText = speech,
            data = linkedMapOf(
                "generatorId" to generatorId,
                "generatorVersion" to generatorVersion.toString(),
                "seedFingerprint" to seed.fingerprint,
                "operation" to operation.name.lowercase(),
                "phase" to phase.name.lowercase(),
                "promptKind" to kind.name.lowercase(),
                "masteryTag" to "math.${operation.name.lowercase()}",
                "errorTags" to "incorrect_${operation.name.lowercase()}_${answerRole.name.lowercase()}",
                "answerRole" to answerRole.name.lowercase(),
                "left" to fact.left.toString(),
                "right" to fact.right.toString(),
                "result" to fact.result.toString(),
                "equation" to equation(operation, fact),
                "objectChoiceEnabled" to concreteObjects.toString(),
                "objectChoices" to "astronaut|yellow_car|flower|chick",
                "representation" to if (concreteObjects) "chosen_objects" else "number_expression",
                "hint_nl-BE" to hintNl(operation, kind),
                "hint_fr-BE" to hintFr(operation, kind),
                "hint_en-US" to hintEn(operation, kind),
                "feedback_nl-BE" to hintNl(operation, kind),
                "feedback_fr-BE" to hintFr(operation, kind),
                "feedback_en-US" to hintEn(operation, kind)
            ),
            answer = answer.toString()
        )
    }

    private fun concreteObjectPrompt(operation: Operation): LocalizedText = when (operation) {
        Operation.ADDITION -> LocalizedText(
            nl = "Tel de figuren. Hoeveel zijn er samen?",
            fr = "Compte les images. Combien y en a-t-il en tout ?",
            en = "Count the pictures. How many are there altogether?"
        )
        Operation.SUBTRACTION -> LocalizedText(
            nl = "Tel de figuren die overblijven. Hoeveel zijn er nog?",
            fr = "Compte les images qui restent. Combien en reste-t-il ?",
            en = "Count the pictures left. How many remain?"
        )
    }

    private fun visiblePrompt(
        operation: Operation,
        kind: PromptKind,
        fact: Fact
    ): LocalizedText = when (operation) {
        Operation.ADDITION -> when (kind) {
            PromptKind.RESULT -> LocalizedText(
                nl = "Bereken ${fact.left} + ${fact.right}.",
                fr = "Calcule ${fact.left} + ${fact.right}.",
                en = "Calculate ${fact.left} + ${fact.right}."
            )
            PromptKind.MISSING_PART -> LocalizedText(
                nl = "${fact.left} + ? = ${fact.result}. Welk getal ontbreekt?",
                fr = "${fact.left} + ? = ${fact.result}. Quel nombre manque ?",
                en = "${fact.left} + ? = ${fact.result}. Which number is missing?"
            )
            PromptKind.STORY -> LocalizedText(
                nl = "${thereAreStarsNl(fact.left)} ${starsArriveNl(fact.right)} Hoeveel sterren zijn er nu?",
                fr = "${thereAreStarsFr(fact.left)} ${starsArriveFr(fact.right)} Combien y en a-t-il maintenant ?",
                en = "${thereAreStarsEn(fact.left)} ${starsArriveEn(fact.right)} How many stars are there now?"
            )
            PromptKind.INVERSE -> LocalizedText(
                nl = "${fact.result} − ${fact.left} = ?. Welk getal hoort op de plaats van het vraagteken?",
                fr = "${fact.result} − ${fact.left} = ?. Quel nombre remplace le point d'interrogation ?",
                en = "${fact.result} − ${fact.left} = ?. Which number replaces the question mark?"
            )
        }
        Operation.SUBTRACTION -> when (kind) {
            PromptKind.RESULT -> LocalizedText(
                nl = "Bereken ${fact.left} − ${fact.right}.",
                fr = "Calcule ${fact.left} − ${fact.right}.",
                en = "Calculate ${fact.left} − ${fact.right}."
            )
            PromptKind.MISSING_PART -> LocalizedText(
                nl = "${fact.left} − ? = ${fact.result}. Welk getal ontbreekt?",
                fr = "${fact.left} − ? = ${fact.result}. Quel nombre manque ?",
                en = "${fact.left} − ? = ${fact.result}. Which number is missing?"
            )
            PromptKind.STORY -> LocalizedText(
                nl = "${thereAreStarsNl(fact.left)} ${starsDisappearNl(fact.right)} Hoeveel sterren blijven er over?",
                fr = "${thereAreStarsFr(fact.left)} ${starsDisappearFr(fact.right)} Combien en reste-t-il ?",
                en = "${thereAreStarsEn(fact.left)} ${starsDisappearEn(fact.right)} How many stars remain?"
            )
            PromptKind.INVERSE -> LocalizedText(
                nl = "${fact.result} + ? = ${fact.left}. Welk getal ontbreekt?",
                fr = "${fact.result} + ? = ${fact.left}. Quel nombre manque ?",
                en = "${fact.result} + ? = ${fact.left}. Which number is missing?"
            )
        }
    }

    private fun spokenPrompt(
        operation: Operation,
        kind: PromptKind,
        fact: Fact
    ): LocalizedText {
        val visible = visiblePrompt(operation, kind, fact)
        return LocalizedText(
            nl = visible.nl
                .replace("−", "min")
                .replace("?", "vraagteken"),
            fr = visible.fr
                ?.replace("−", "moins")
                ?.replace("?", "point d'interrogation"),
            en = visible.en
                ?.replace("−", "minus")
                ?.replace("?", "question mark")
        )
    }

    private fun equation(operation: Operation, fact: Fact): String = when (operation) {
        Operation.ADDITION -> "${fact.left}+${fact.right}=${fact.result}"
        Operation.SUBTRACTION -> "${fact.left}-${fact.right}=${fact.result}"
    }

    private fun thereAreStarsNl(count: Int): String =
        if (count == 1) "Er is 1 ster." else "Er zijn $count sterren."

    private fun starsArriveNl(count: Int): String =
        if (count == 1) "Er komt 1 ster bij." else "Er komen $count sterren bij."

    private fun starsDisappearNl(count: Int): String =
        if (count == 1) "1 ster verdwijnt." else "$count sterren verdwijnen."

    private fun thereAreStarsFr(count: Int): String =
        if (count == 1) "Il y a 1 étoile." else "Il y a $count étoiles."

    private fun starsArriveFr(count: Int): String =
        if (count == 1) "1 étoile s'ajoute." else "$count étoiles s'ajoutent."

    private fun starsDisappearFr(count: Int): String =
        if (count == 1) "1 étoile disparaît." else "$count étoiles disparaissent."

    private fun thereAreStarsEn(count: Int): String =
        if (count == 1) "There is 1 star." else "There are $count stars."

    private fun starsArriveEn(count: Int): String =
        if (count == 1) "1 more star arrives." else "$count more stars arrive."

    private fun starsDisappearEn(count: Int): String =
        if (count == 1) "1 star disappears." else "$count stars disappear."

    private fun hintNl(operation: Operation, kind: PromptKind): String = when {
        kind == PromptKind.INVERSE || kind == PromptKind.MISSING_PART ->
            "Gebruik de omgekeerde bewerking of vul aan."
        operation == Operation.ADDITION ->
            "Tel verder of splits naar 10."
        else ->
            "Neem weg, tel terug of vul aan."
    }

    private fun hintFr(operation: Operation, kind: PromptKind): String = when {
        kind == PromptKind.INVERSE || kind == PromptKind.MISSING_PART ->
            "Utilise l'opération inverse ou complète."
        operation == Operation.ADDITION ->
            "Continue à compter ou passe par 10."
        else ->
            "Retire, compte à rebours ou complète."
    }

    private fun hintEn(operation: Operation, kind: PromptKind): String = when {
        kind == PromptKind.INVERSE || kind == PromptKind.MISSING_PART ->
            "Use the inverse operation or bridge to the total."
        operation == Operation.ADDITION ->
            "Count on or bridge through 10."
        else ->
            "Take away, count back or bridge up."
    }

    private data class Fact(
        val left: Int,
        val right: Int,
        val result: Int
    )

    private enum class Operation {
        ADDITION,
        SUBTRACTION
    }

    private enum class Phase {
        GUIDED,
        CHECKPOINT
    }

    private enum class PromptKind {
        RESULT,
        MISSING_PART,
        STORY,
        INVERSE
    }

    private enum class AnswerRole {
        RESULT,
        RIGHT
    }
}
