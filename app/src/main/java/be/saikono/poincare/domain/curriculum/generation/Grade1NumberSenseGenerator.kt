package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 1 number-sense generator.
 *
 * Version 1 deliberately stays within 0..20 and covers the reviewed foundations:
 * counting forwards and backwards, predecessor/successor, ordering, tens and ones,
 * and composing a teen number from ten plus loose units.
 */
object Grade1NumberSenseGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g1.math.number_sense.foundations"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g1.math.number_sense.foundations"
    private val allowedParameterKeys = setOf(
        "grade",
        "roadmap_entry",
        "phase",
        "editorial_status",
        "focus",
        "max_count"
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
        if (parameters["editorial_status"] != LessonGeneratorRegistry.APPROVED_STATUS) {
            add("editorial_status must be approved")
        }
        if (parameters["focus"] !in setOf(null, "mixed", "count_objects")) {
            add("focus must be mixed or count_objects")
        }
        if (parameters["max_count"] !in setOf(null, "10", "20")) {
            add("max_count must be 10 or 20")
        }
        if (parameters["max_count"] != null && parameters["focus"] != "count_objects") {
            add("max_count is only supported with focus=count_objects")
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
        val configuredMaximum = parameters["max_count"]?.toInt() ?: 20
        val promptKinds = if (parameters["focus"] == "count_objects") {
            listOf(PromptKind.COUNT_OBJECTS)
        } else {
            PromptKind.entries.filterNot { it == PromptKind.COUNT_OBJECTS }
        }
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
                configuredMaximum = configuredMaximum,
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
        configuredMaximum: Int,
        random: StableLessonRandom,
        used: MutableSet<String>
    ): Candidate {
        repeat(128) {
            val candidate = createCandidate(kind, phase, index, exerciseCount, configuredMaximum, random)
            if (used.add(candidate.signature())) return candidate
        }
        return fallbackCandidates(kind, configuredMaximum).firstOrNull { used.add(it.signature()) }
            ?: error("Unable to create a unique Grade 1 number-sense exercise for ${kind.name}")
    }

    private fun createCandidate(
        kind: PromptKind,
        phase: Phase,
        index: Int,
        exerciseCount: Int,
        configuredMaximum: Int,
        random: StableLessonRandom
    ): Candidate {
        val guidedWithinTen = phase == Phase.GUIDED && index < (exerciseCount + 1) / 2
        val maximum = if (guidedWithinTen) minOf(10, configuredMaximum) else configuredMaximum

        return when (kind) {
            PromptKind.COUNT_OBJECTS -> {
                val count = random.nextInt(1, maximum + 1)
                Candidate(kind, count, listOf(count))
            }
            PromptKind.PREDECESSOR -> {
                val target = random.nextInt(1, maximum + 1)
                Candidate(kind, target - 1, listOf(target))
            }
            PromptKind.SUCCESSOR -> {
                val target = random.nextInt(0, maximum)
                Candidate(kind, target + 1, listOf(target))
            }
            PromptKind.MISSING_FORWARD -> {
                val start = random.nextInt(0, (maximum - 2).coerceAtLeast(1))
                Candidate(kind, start + 2, listOf(start, start + 1, start + 3))
            }
            PromptKind.MISSING_BACKWARD -> {
                val start = random.nextInt(3, maximum + 1)
                Candidate(kind, start - 2, listOf(start, start - 1, start - 3))
            }
            PromptKind.LARGEST -> {
                val values = distinctValues(maximum, random)
                Candidate(kind, values.max(), values)
            }
            PromptKind.SMALLEST -> {
                val values = distinctValues(maximum, random)
                Candidate(kind, values.min(), values)
            }
            PromptKind.TENS_AND_ONES -> {
                val ones = random.nextInt(10)
                Candidate(kind, 10 + ones, listOf(1, ones))
            }
            PromptKind.TEN_PLUS_ONES -> {
                val ones = random.nextInt(0, 11)
                Candidate(kind, 10 + ones, listOf(10, ones))
            }
        }
    }

    private fun distinctValues(maximum: Int, random: StableLessonRandom): List<Int> {
        val values = linkedSetOf<Int>()
        while (values.size < 3) values += random.nextInt(0, maximum + 1)
        val result = values.toMutableList()
        for (index in result.lastIndex downTo 1) {
            val swapIndex = random.nextInt(index + 1)
            val swap = result[index]
            result[index] = result[swapIndex]
            result[swapIndex] = swap
        }
        return result
    }

    private fun fallbackCandidates(kind: PromptKind, configuredMaximum: Int): Sequence<Candidate> = sequence {
        when (kind) {
            PromptKind.COUNT_OBJECTS -> for (count in 1..configuredMaximum) {
                yield(Candidate(kind, count, listOf(count)))
            }
            PromptKind.PREDECESSOR -> for (target in 1..20) {
                yield(Candidate(kind, target - 1, listOf(target)))
            }
            PromptKind.SUCCESSOR -> for (target in 0 until 20) {
                yield(Candidate(kind, target + 1, listOf(target)))
            }
            PromptKind.MISSING_FORWARD -> for (start in 0..17) {
                yield(Candidate(kind, start + 2, listOf(start, start + 1, start + 3)))
            }
            PromptKind.MISSING_BACKWARD -> for (start in 3..20) {
                yield(Candidate(kind, start - 2, listOf(start, start - 1, start - 3)))
            }
            PromptKind.LARGEST -> for (start in 0..18) {
                yield(Candidate(kind, start + 2, listOf(start, start + 2, start + 1)))
            }
            PromptKind.SMALLEST -> for (start in 0..18) {
                yield(Candidate(kind, start, listOf(start + 1, start, start + 2)))
            }
            PromptKind.TENS_AND_ONES -> for (ones in 0..9) {
                yield(Candidate(kind, 10 + ones, listOf(1, ones)))
            }
            PromptKind.TEN_PLUS_ONES -> for (ones in 0..10) {
                yield(Candidate(kind, 10 + ones, listOf(10, ones)))
            }
        }
    }

    private fun exercise(
        seed: LessonGenerationSeed,
        index: Int,
        phase: Phase,
        candidate: Candidate
    ): ExerciseContent {
        val id = "generated.g1.math.number_sense.v1.${seed.fingerprint}.${index + 1}"
        val prompt = visiblePrompt(candidate)
        return ExerciseContent(
            id = id,
            domain = LearningDomain.MATH,
            skill = "number_sense",
            section = if (phase == Phase.GUIDED) ExerciseSection.PRACTICE else ExerciseSection.TESTS,
            level = SkillLevel.FIRST_GRADE,
            inputMode = InputMode.CUSTOM_NUMBER_PAD,
            prompt = prompt,
            speechText = spokenPrompt(candidate.kind, prompt),
            data = linkedMapOf(
                "generatorId" to generatorId,
                "generatorVersion" to generatorVersion.toString(),
                "seedFingerprint" to seed.fingerprint,
                "phase" to phase.name.lowercase(),
                "promptKind" to candidate.kind.name.lowercase(),
                "answerRole" to "number",
                "values" to candidate.values.joinToString(","),
                "hint_nl-BE" to hintNl(candidate.kind),
                "hint_fr-BE" to hintFr(candidate.kind),
                "hint_en-US" to hintEn(candidate.kind)
            ).apply {
                if (candidate.kind == PromptKind.COUNT_OBJECTS) {
                    put("objectChoiceEnabled", "true")
                    put("operation", "counting")
                    put("left", candidate.answer.toString())
                    put("right", "0")
                }
            },
            answer = candidate.answer.toString()
        )
    }

    private fun visiblePrompt(candidate: Candidate): LocalizedText = when (candidate.kind) {
        PromptKind.COUNT_OBJECTS -> LocalizedText(
            nl = "Tel de figuren. Hoeveel zijn er?",
            fr = "Compte les figures. Combien y en a-t-il ?",
            en = "Count the objects. How many are there?"
        )
        PromptKind.PREDECESSOR -> {
            val target = candidate.values[0]
            LocalizedText(
                nl = "Welk getal komt vlak vóór $target?",
                fr = "Quel nombre vient juste avant $target ?",
                en = "Which number comes just before $target?"
            )
        }
        PromptKind.SUCCESSOR -> {
            val target = candidate.values[0]
            LocalizedText(
                nl = "Welk getal komt vlak na $target?",
                fr = "Quel nombre vient juste après $target ?",
                en = "Which number comes just after $target?"
            )
        }
        PromptKind.MISSING_FORWARD -> LocalizedText(
            nl = "Vul aan: ${candidate.values[0]}, ${candidate.values[1]}, ?, ${candidate.values[2]}.",
            fr = "Complète : ${candidate.values[0]}, ${candidate.values[1]}, ?, ${candidate.values[2]}.",
            en = "Complete: ${candidate.values[0]}, ${candidate.values[1]}, ?, ${candidate.values[2]}."
        )
        PromptKind.MISSING_BACKWARD -> LocalizedText(
            nl = "Tel terug en vul aan: ${candidate.values[0]}, ${candidate.values[1]}, ?, ${candidate.values[2]}.",
            fr = "Compte à rebours et complète : ${candidate.values[0]}, ${candidate.values[1]}, ?, ${candidate.values[2]}.",
            en = "Count backwards and complete: ${candidate.values[0]}, ${candidate.values[1]}, ?, ${candidate.values[2]}."
        )
        PromptKind.LARGEST -> LocalizedText(
            nl = "Welk getal is het grootst: ${candidate.values[0]}, ${candidate.values[1]} of ${candidate.values[2]}?",
            fr = "Quel nombre est le plus grand : ${candidate.values[0]}, ${candidate.values[1]} ou ${candidate.values[2]} ?",
            en = "Which number is the largest: ${candidate.values[0]}, ${candidate.values[1]}, or ${candidate.values[2]}?"
        )
        PromptKind.SMALLEST -> LocalizedText(
            nl = "Welk getal is het kleinst: ${candidate.values[0]}, ${candidate.values[1]} of ${candidate.values[2]}?",
            fr = "Quel nombre est le plus petit : ${candidate.values[0]}, ${candidate.values[1]} ou ${candidate.values[2]} ?",
            en = "Which number is the smallest: ${candidate.values[0]}, ${candidate.values[1]}, or ${candidate.values[2]}?"
        )
        PromptKind.TENS_AND_ONES -> {
            val ones = candidate.values[1]
            LocalizedText(
                nl = "Eén tiental en $ones eenheden vormen welk getal?",
                fr = "Une dizaine et $ones unités forment quel nombre ?",
                en = "One ten and $ones ones make which number?"
            )
        }
        PromptKind.TEN_PLUS_ONES -> {
            val ones = candidate.values[1]
            LocalizedText(
                nl = "Bereken 10 + $ones.",
                fr = "Calcule 10 + $ones.",
                en = "Calculate 10 + $ones."
            )
        }
    }

    private fun spokenPrompt(kind: PromptKind, visible: LocalizedText): LocalizedText {
        val isMissingNumber = kind == PromptKind.MISSING_FORWARD ||
            kind == PromptKind.MISSING_BACKWARD
        fun normalize(text: String, missingPhrase: String): String = text
            .replace("+", " plus ")
            .replace("?", if (isMissingNumber) " $missingPhrase " else "")
            .replace(Regex("\\s+"), " ")
            .trim()

        return LocalizedText(
            nl = normalize(visible.nl, "welk getal"),
            fr = visible.fr?.let { normalize(it, "quel nombre") },
            en = visible.en?.let { normalize(it, "which number") }
        )
    }

    private fun hintNl(kind: PromptKind): String = when (kind) {
        PromptKind.COUNT_OBJECTS -> "Wijs elk figuurtje één keer aan. Het laatste telwoord is je antwoord."
        PromptKind.PREDECESSOR -> "Tel één stap terug."
        PromptKind.SUCCESSOR -> "Tel één stap verder."
        PromptKind.MISSING_FORWARD -> "Tel telkens één verder."
        PromptKind.MISSING_BACKWARD -> "Tel telkens één terug."
        PromptKind.LARGEST -> "Vergelijk de getallen en zoek het getal dat het verst ligt."
        PromptKind.SMALLEST -> "Vergelijk de getallen en zoek het getal dat het dichtst bij nul ligt."
        PromptKind.TENS_AND_ONES -> "Eén tiental is 10. Tel de losse eenheden erbij."
        PromptKind.TEN_PLUS_ONES -> "Begin bij 10 en tel de eenheden erbij."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.COUNT_OBJECTS -> "Montre chaque figure une seule fois. Le dernier nombre dit est ta réponse."
        PromptKind.PREDECESSOR -> "Recule d'une unité."
        PromptKind.SUCCESSOR -> "Avance d'une unité."
        PromptKind.MISSING_FORWARD -> "Compte chaque fois une unité de plus."
        PromptKind.MISSING_BACKWARD -> "Compte chaque fois une unité de moins."
        PromptKind.LARGEST -> "Compare les nombres et cherche celui qui est le plus éloigné de zéro."
        PromptKind.SMALLEST -> "Compare les nombres et cherche celui qui est le plus proche de zéro."
        PromptKind.TENS_AND_ONES -> "Une dizaine vaut 10. Ajoute les unités."
        PromptKind.TEN_PLUS_ONES -> "Commence à 10 et ajoute les unités."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.COUNT_OBJECTS -> "Point to each object once. The last number you say is the answer."
        PromptKind.PREDECESSOR -> "Count back one step."
        PromptKind.SUCCESSOR -> "Count forward one step."
        PromptKind.MISSING_FORWARD -> "Count forward by one each time."
        PromptKind.MISSING_BACKWARD -> "Count backwards by one each time."
        PromptKind.LARGEST -> "Compare the numbers and find the one farthest from zero."
        PromptKind.SMALLEST -> "Compare the numbers and find the one closest to zero."
        PromptKind.TENS_AND_ONES -> "One ten is 10. Add the loose ones."
        PromptKind.TEN_PLUS_ONES -> "Start at 10 and add the ones."
    }

    private data class Candidate(
        val kind: PromptKind,
        val answer: Int,
        val values: List<Int>
    ) {
        fun signature(): String = "${kind.name}:${answer}:${values.joinToString(":")}"
    }

    private enum class Phase {
        GUIDED,
        CHECKPOINT
    }

    private enum class PromptKind {
        COUNT_OBJECTS,
        PREDECESSOR,
        SUCCESSOR,
        MISSING_FORWARD,
        MISSING_BACKWARD,
        LARGEST,
        SMALLEST,
        TENS_AND_ONES,
        TEN_PLUS_ONES
    }
}
