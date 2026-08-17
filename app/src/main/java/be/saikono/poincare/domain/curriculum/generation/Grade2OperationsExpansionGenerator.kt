package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 2 numbers-and-operations generator.
 *
 * Version 1 stays within 0..100 and covers place value, addition and
 * subtraction across a ten, missing-number relations, equal groups, and exact
 * equal sharing with the introductory factors 2, 5, and 10.
 */
object Grade2OperationsExpansionGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g2.math.operations.expansion"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g2.math.operations.expansion"
    private val introductoryFactors = listOf(2, 5, 10)
    private val allowedParameterKeys = setOf(
        "grade",
        "roadmap_entry",
        "phase",
        "editorial_status"
    )

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) {
            add("unsupported parameters: ${unknown.sorted().joinToString()}")
        }
        if (parameters["grade"] != "2") add("grade must be 2")
        if (parameters["roadmap_entry"] != ROADMAP_ENTRY) {
            add("roadmap_entry must be $ROADMAP_ENTRY")
        }
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
            val candidate = uniqueCandidate(kind, phase, random, used)
            exercise(seed, index, phase, candidate)
        }
    }

    private fun uniqueCandidate(
        kind: PromptKind,
        phase: Phase,
        random: StableLessonRandom,
        used: MutableSet<String>
    ): Candidate {
        repeat(128) {
            val candidate = createCandidate(kind, phase, random)
            if (used.add(candidate.signature())) return candidate
        }
        return fallbackCandidates(kind).firstOrNull { used.add(it.signature()) }
            ?: error("Unable to create a unique Grade 2 operations exercise for ${kind.name}")
    }

    private fun createCandidate(
        kind: PromptKind,
        phase: Phase,
        random: StableLessonRandom
    ): Candidate {
        val maximum = if (phase == Phase.GUIDED) 60 else 100
        return when (kind) {
            PromptKind.TENS_COUNT -> {
                val number = random.nextInt(10, maximum + 1)
                Candidate(kind, number / 10, listOf(number))
            }
            PromptKind.COMPOSE_NUMBER -> {
                val maximumTens = ((maximum - 9) / 10).coerceIn(1, 9)
                val tens = random.nextInt(1, maximumTens + 1)
                val ones = random.nextInt(10)
                Candidate(kind, tens * 10 + ones, listOf(tens, ones))
            }
            PromptKind.ADD -> {
                val (left, right) = crossingAddition(maximum, random)
                Candidate(kind, left + right, listOf(left, right))
            }
            PromptKind.SUBTRACT -> {
                val (left, right) = crossingSubtraction(maximum, random)
                Candidate(kind, left - right, listOf(left, right))
            }
            PromptKind.MISSING_ADDEND -> {
                val (known, missing) = crossingAddition(maximum, random)
                Candidate(kind, missing, listOf(known, known + missing))
            }
            PromptKind.MISSING_SUBTRAHEND -> {
                val (whole, removed) = crossingSubtraction(maximum, random)
                Candidate(kind, removed, listOf(whole, whole - removed))
            }
            PromptKind.EQUAL_GROUPS -> {
                val each = introductoryFactors[random.nextInt(introductoryFactors.size)]
                val maximumGroups = if (phase == Phase.GUIDED) 5 else 10
                val groups = random.nextInt(2, maximumGroups + 1)
                Candidate(kind, groups * each, listOf(groups, each))
            }
            PromptKind.EQUAL_SHARING -> {
                val groups = introductoryFactors[random.nextInt(introductoryFactors.size)]
                val maximumShare = if (phase == Phase.GUIDED) 5 else 10
                val share = random.nextInt(2, maximumShare + 1)
                Candidate(kind, share, listOf(groups * share, groups))
            }
        }
    }

    private fun crossingAddition(
        maximum: Int,
        random: StableLessonRandom
    ): Pair<Int, Int> {
        repeat(128) {
            val left = random.nextInt(11, maximum)
            val right = random.nextInt(1, maximum - left + 1)
            if (left % 10 + right % 10 >= 10) return left to right
        }
        return additionPairs(maximum).first()
    }

    private fun crossingSubtraction(
        maximum: Int,
        random: StableLessonRandom
    ): Pair<Int, Int> {
        repeat(128) {
            val left = random.nextInt(20, maximum + 1)
            val right = random.nextInt(1, left)
            if (right % 10 > left % 10) return left to right
        }
        return subtractionPairs(maximum).first()
    }

    private fun additionPairs(maximum: Int): Sequence<Pair<Int, Int>> = sequence {
        for (left in 11 until maximum) {
            for (right in 1..(maximum - left)) {
                if (left % 10 + right % 10 >= 10) yield(left to right)
            }
        }
    }

    private fun subtractionPairs(maximum: Int): Sequence<Pair<Int, Int>> = sequence {
        for (left in 20..maximum) {
            for (right in 1 until left) {
                if (right % 10 > left % 10) yield(left to right)
            }
        }
    }

    private fun fallbackCandidates(kind: PromptKind): Sequence<Candidate> = sequence {
        when (kind) {
            PromptKind.TENS_COUNT -> for (number in 10..100) {
                yield(Candidate(kind, number / 10, listOf(number)))
            }
            PromptKind.COMPOSE_NUMBER -> for (tens in 1..9) {
                for (ones in 0..9) yield(Candidate(kind, tens * 10 + ones, listOf(tens, ones)))
            }
            PromptKind.ADD -> for ((left, right) in additionPairs(100)) {
                yield(Candidate(kind, left + right, listOf(left, right)))
            }
            PromptKind.SUBTRACT -> for ((left, right) in subtractionPairs(100)) {
                yield(Candidate(kind, left - right, listOf(left, right)))
            }
            PromptKind.MISSING_ADDEND -> for ((known, missing) in additionPairs(100)) {
                yield(Candidate(kind, missing, listOf(known, known + missing)))
            }
            PromptKind.MISSING_SUBTRAHEND -> for ((whole, removed) in subtractionPairs(100)) {
                yield(Candidate(kind, removed, listOf(whole, whole - removed)))
            }
            PromptKind.EQUAL_GROUPS -> introductoryFactors.forEach { each ->
                for (groups in 2..10) yield(Candidate(kind, groups * each, listOf(groups, each)))
            }
            PromptKind.EQUAL_SHARING -> introductoryFactors.forEach { groups ->
                for (share in 2..10) yield(Candidate(kind, share, listOf(groups * share, groups)))
            }
        }
    }

    private fun exercise(
        seed: LessonGenerationSeed,
        index: Int,
        phase: Phase,
        candidate: Candidate
    ): ExerciseContent {
        val prompt = visiblePrompt(candidate)
        return ExerciseContent(
            id = "generated.g2.math.operations_expansion.v1.${seed.fingerprint}.${index + 1}",
            domain = LearningDomain.MATH,
            skill = "operations_expansion",
            section = if (phase == Phase.GUIDED) ExerciseSection.PRACTICE else ExerciseSection.TESTS,
            level = SkillLevel.SECOND_GRADE,
            inputMode = InputMode.CUSTOM_NUMBER_PAD,
            prompt = prompt,
            speechText = spokenPrompt(candidate),
            data = linkedMapOf(
                "generatorId" to generatorId,
                "generatorVersion" to generatorVersion.toString(),
                "seedFingerprint" to seed.fingerprint,
                "roadmapEntry" to ROADMAP_ENTRY,
                "phase" to phase.name.lowercase(),
                "promptKind" to candidate.kind.name.lowercase(),
                "answerRole" to answerRole(candidate.kind),
                "values" to candidate.values.joinToString(","),
                "equation" to equation(candidate),
                "maximumValue" to "100",
                "hint_nl-BE" to hintNl(candidate.kind),
                "hint_fr-BE" to hintFr(candidate.kind),
                "hint_en-US" to hintEn(candidate.kind)
            ),
            answer = candidate.answer.toString()
        )
    }

    private fun visiblePrompt(candidate: Candidate): LocalizedText {
        val values = candidate.values
        return when (candidate.kind) {
            PromptKind.TENS_COUNT -> LocalizedText(
                nl = "Hoeveel tientallen zitten er in ${values[0]}?",
                fr = "Combien de dizaines y a-t-il dans ${values[0]} ?",
                en = "How many tens are in ${values[0]}?"
            )
            PromptKind.COMPOSE_NUMBER -> LocalizedText(
                nl = "Welk getal bestaat uit ${values[0]} tientallen en ${values[1]} eenheden?",
                fr = "Quel nombre est formé de ${values[0]} dizaines et ${values[1]} unités ?",
                en = "Which number has ${values[0]} tens and ${values[1]} ones?"
            )
            PromptKind.ADD -> LocalizedText(
                nl = "Bereken ${values[0]} + ${values[1]}.",
                fr = "Calcule ${values[0]} + ${values[1]}.",
                en = "Calculate ${values[0]} + ${values[1]}."
            )
            PromptKind.SUBTRACT -> LocalizedText(
                nl = "Bereken ${values[0]} - ${values[1]}.",
                fr = "Calcule ${values[0]} - ${values[1]}.",
                en = "Calculate ${values[0]} - ${values[1]}."
            )
            PromptKind.MISSING_ADDEND -> LocalizedText(
                nl = "Vul aan: ${values[0]} + ? = ${values[1]}.",
                fr = "Complète : ${values[0]} + ? = ${values[1]}.",
                en = "Complete: ${values[0]} + ? = ${values[1]}."
            )
            PromptKind.MISSING_SUBTRAHEND -> LocalizedText(
                nl = "Vul aan: ${values[0]} - ? = ${values[1]}.",
                fr = "Complète : ${values[0]} - ? = ${values[1]}.",
                en = "Complete: ${values[0]} - ? = ${values[1]}."
            )
            PromptKind.EQUAL_GROUPS -> LocalizedText(
                nl = "Er zijn ${values[0]} mandjes met telkens ${values[1]} appels. Hoeveel appels zijn er samen?",
                fr = "Il y a ${values[0]} paniers avec ${values[1]} pommes dans chaque panier. Combien y a-t-il de pommes en tout ?",
                en = "There are ${values[0]} baskets with ${values[1]} apples in each basket. How many apples are there altogether?"
            )
            PromptKind.EQUAL_SHARING -> LocalizedText(
                nl = "${values[0]} stickers worden eerlijk verdeeld over ${values[1]} kinderen. Hoeveel krijgt elk kind?",
                fr = "${values[0]} autocollants sont partagés équitablement entre ${values[1]} enfants. Combien chaque enfant en reçoit-il ?",
                en = "${values[0]} stickers are shared equally among ${values[1]} children. How many does each child get?"
            )
        }
    }

    private fun spokenPrompt(candidate: Candidate): LocalizedText {
        val values = candidate.values
        return when (candidate.kind) {
            PromptKind.TENS_COUNT -> LocalizedText(
                nl = "Hoeveel tientallen zitten er in ${values[0]}",
                fr = "Combien de dizaines y a-t-il dans ${values[0]}",
                en = "How many tens are in ${values[0]}"
            )
            PromptKind.COMPOSE_NUMBER -> LocalizedText(
                nl = "Welk getal bestaat uit ${values[0]} tientallen en ${values[1]} eenheden",
                fr = "Quel nombre est formé de ${values[0]} dizaines et ${values[1]} unités",
                en = "Which number has ${values[0]} tens and ${values[1]} ones"
            )
            PromptKind.ADD -> LocalizedText(
                nl = "Hoeveel is ${values[0]} plus ${values[1]}",
                fr = "Combien font ${values[0]} plus ${values[1]}",
                en = "What is ${values[0]} plus ${values[1]}"
            )
            PromptKind.SUBTRACT -> LocalizedText(
                nl = "Hoeveel is ${values[0]} min ${values[1]}",
                fr = "Combien font ${values[0]} moins ${values[1]}",
                en = "What is ${values[0]} minus ${values[1]}"
            )
            PromptKind.MISSING_ADDEND -> LocalizedText(
                nl = "Hoeveel moet je bij ${values[0]} optellen om ${values[1]} te krijgen",
                fr = "Combien faut-il ajouter à ${values[0]} pour obtenir ${values[1]}",
                en = "How much must you add to ${values[0]} to make ${values[1]}"
            )
            PromptKind.MISSING_SUBTRAHEND -> LocalizedText(
                nl = "Hoeveel trek je van ${values[0]} af om ${values[1]} over te houden",
                fr = "Combien faut-il retirer de ${values[0]} pour obtenir ${values[1]}",
                en = "How much must you subtract from ${values[0]} to leave ${values[1]}"
            )
            PromptKind.EQUAL_GROUPS -> LocalizedText(
                nl = "Er zijn ${values[0]} mandjes met telkens ${values[1]} appels. Hoeveel appels zijn er samen",
                fr = "Il y a ${values[0]} paniers avec ${values[1]} pommes dans chaque panier. Combien y a-t-il de pommes en tout",
                en = "There are ${values[0]} baskets with ${values[1]} apples in each basket. How many apples are there altogether"
            )
            PromptKind.EQUAL_SHARING -> LocalizedText(
                nl = "${values[0]} stickers worden eerlijk verdeeld over ${values[1]} kinderen. Hoeveel krijgt elk kind",
                fr = "${values[0]} autocollants sont partagés équitablement entre ${values[1]} enfants. Combien chaque enfant en reçoit-il",
                en = "${values[0]} stickers are shared equally among ${values[1]} children. How many does each child get"
            )
        }
    }

    private fun equation(candidate: Candidate): String {
        val values = candidate.values
        return when (candidate.kind) {
            PromptKind.TENS_COUNT -> "${values[0]}:10=${candidate.answer}"
            PromptKind.COMPOSE_NUMBER -> "${values[0]}x10+${values[1]}=${candidate.answer}"
            PromptKind.ADD -> "${values[0]}+${values[1]}=${candidate.answer}"
            PromptKind.SUBTRACT -> "${values[0]}-${values[1]}=${candidate.answer}"
            PromptKind.MISSING_ADDEND -> "${values[0]}+${candidate.answer}=${values[1]}"
            PromptKind.MISSING_SUBTRAHEND -> "${values[0]}-${candidate.answer}=${values[1]}"
            PromptKind.EQUAL_GROUPS -> "${values[0]}x${values[1]}=${candidate.answer}"
            PromptKind.EQUAL_SHARING -> "${values[0]}:${values[1]}=${candidate.answer}"
        }
    }

    private fun answerRole(kind: PromptKind): String = when (kind) {
        PromptKind.TENS_COUNT -> "tens_count"
        PromptKind.COMPOSE_NUMBER -> "composed_number"
        PromptKind.ADD, PromptKind.SUBTRACT, PromptKind.EQUAL_GROUPS -> "result"
        PromptKind.MISSING_ADDEND -> "missing_addend"
        PromptKind.MISSING_SUBTRAHEND -> "missing_subtrahend"
        PromptKind.EQUAL_SHARING -> "share_size"
    }

    private fun hintNl(kind: PromptKind): String = when (kind) {
        PromptKind.TENS_COUNT -> "Kijk naar het cijfer van de tientallen."
        PromptKind.COMPOSE_NUMBER -> "Een tiental is 10. Tel daarna de eenheden erbij."
        PromptKind.ADD -> "Splits een getal in tientallen en eenheden."
        PromptKind.SUBTRACT -> "Trek eerst de tientallen en daarna de eenheden af."
        PromptKind.MISSING_ADDEND -> "Zoek het verschil tussen het eindgetal en het begingetal."
        PromptKind.MISSING_SUBTRAHEND -> "Zoek het verschil tussen het eerste en het laatste getal."
        PromptKind.EQUAL_GROUPS -> "Tel even grote groepjes samen."
        PromptKind.EQUAL_SHARING -> "Verdeel in even grote groepjes zonder rest."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.TENS_COUNT -> "Regarde le chiffre des dizaines."
        PromptKind.COMPOSE_NUMBER -> "Une dizaine vaut 10. Ajoute ensuite les unités."
        PromptKind.ADD -> "Décompose un nombre en dizaines et unités."
        PromptKind.SUBTRACT -> "Retire d'abord les dizaines, puis les unités."
        PromptKind.MISSING_ADDEND -> "Cherche la différence entre le nombre d'arrivée et le nombre de départ."
        PromptKind.MISSING_SUBTRAHEND -> "Cherche la différence entre le premier et le dernier nombre."
        PromptKind.EQUAL_GROUPS -> "Additionne des groupes de même taille."
        PromptKind.EQUAL_SHARING -> "Partage en groupes égaux, sans reste."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.TENS_COUNT -> "Look at the tens digit."
        PromptKind.COMPOSE_NUMBER -> "One ten is 10. Then add the ones."
        PromptKind.ADD -> "Split a number into tens and ones."
        PromptKind.SUBTRACT -> "Subtract the tens first, then the ones."
        PromptKind.MISSING_ADDEND -> "Find the difference between the ending number and the starting number."
        PromptKind.MISSING_SUBTRAHEND -> "Find the difference between the first and last number."
        PromptKind.EQUAL_GROUPS -> "Add equal-sized groups together."
        PromptKind.EQUAL_SHARING -> "Share into equal groups with nothing left over."
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
        TENS_COUNT,
        COMPOSE_NUMBER,
        ADD,
        SUBTRACT,
        MISSING_ADDEND,
        MISSING_SUBTRAHEND,
        EQUAL_GROUPS,
        EQUAL_SHARING
    }
}
