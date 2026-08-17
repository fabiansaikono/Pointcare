package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/** Deterministic introduction to reading small tables and one-symbol-per-item diagrams. */
object Grade2StatisticsIntroductionGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g2.math.statistics.introduction"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g2.math.statistics.introduction"
    private val allowedParameterKeys = setOf(
        "grade",
        "roadmap_entry",
        "phase",
        "editorial_status"
    )

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
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
            ?: error("Unable to create a unique Grade 2 statistics exercise for ${kind.name}")
    }

    private fun createCandidate(
        kind: PromptKind,
        phase: Phase,
        random: StableLessonRandom
    ): Candidate {
        val maximum = if (phase == Phase.GUIDED) 8 else 12
        val counts = MutableList(Category.entries.size) { random.nextInt(1, maximum + 1) }
        val target = random.nextInt(Category.entries.size)
        if (kind == PromptKind.DIFFERENCE) {
            val smaller = random.nextInt(1, maximum)
            counts[1] = smaller
            counts[0] = random.nextInt(smaller + 1, maximum + 1)
        }
        val answer = when (kind) {
            PromptKind.TABLE_LOOKUP,
            PromptKind.PICTOGRAM_LOOKUP,
            PromptKind.TALLY_LOOKUP,
            PromptKind.BAR_LOOKUP -> counts[target]
            PromptKind.TOTAL_TWO -> counts[0] + counts[1]
            PromptKind.DIFFERENCE -> counts[0] - counts[1]
            PromptKind.MISSING_FROM_TOTAL -> counts[2]
            PromptKind.TOTAL_ALL -> counts.sum()
        }
        return Candidate(kind, answer, counts, target)
    }

    private fun fallbackCandidates(kind: PromptKind): Sequence<Candidate> = sequence {
        for (first in 1..12) {
            for (second in 1..12) {
                for (third in 1..12) {
                    val counts = listOf(first, second, third)
                    for (target in Category.entries.indices) {
                        if (kind == PromptKind.DIFFERENCE && first <= second) continue
                        val answer = when (kind) {
                            PromptKind.TABLE_LOOKUP,
                            PromptKind.PICTOGRAM_LOOKUP,
                            PromptKind.TALLY_LOOKUP,
                            PromptKind.BAR_LOOKUP -> counts[target]
                            PromptKind.TOTAL_TWO -> first + second
                            PromptKind.DIFFERENCE -> first - second
                            PromptKind.MISSING_FROM_TOTAL -> third
                            PromptKind.TOTAL_ALL -> counts.sum()
                        }
                        yield(Candidate(kind, answer, counts, target))
                    }
                }
            }
        }
    }

    private fun exercise(
        seed: LessonGenerationSeed,
        index: Int,
        phase: Phase,
        candidate: Candidate
    ): ExerciseContent = ExerciseContent(
        id = "generated.g2.math.statistics_introduction.v1.${seed.fingerprint}.${index + 1}",
        domain = LearningDomain.MATH,
        skill = "statistics_introduction",
        section = if (phase == Phase.GUIDED) ExerciseSection.PRACTICE else ExerciseSection.TESTS,
        level = SkillLevel.SECOND_GRADE,
        inputMode = InputMode.CUSTOM_NUMBER_PAD,
        prompt = visiblePrompt(candidate),
        speechText = spokenPrompt(candidate),
        data = linkedMapOf(
            "generatorId" to generatorId,
            "generatorVersion" to generatorVersion.toString(),
            "seedFingerprint" to seed.fingerprint,
            "roadmapEntry" to ROADMAP_ENTRY,
            "phase" to phase.name.lowercase(),
            "promptKind" to candidate.kind.name.lowercase(),
            "answerRole" to answerRole(candidate.kind),
            "values" to candidate.counts.joinToString(","),
            "targetIndex" to candidate.targetIndex.toString(),
            "representation" to representation(candidate.kind),
            "symbolValue" to "1",
            "hint_nl-BE" to hintNl(candidate.kind),
            "hint_fr-BE" to hintFr(candidate.kind),
            "hint_en-US" to hintEn(candidate.kind)
        ),
        answer = candidate.answer.toString()
    )

    private fun visiblePrompt(candidate: Candidate): LocalizedText {
        val target = Category.entries[candidate.targetIndex]
        val counts = candidate.counts
        return when (candidate.kind) {
            PromptKind.TABLE_LOOKUP -> LocalizedText(
                nl = "Tabel: ${rows(counts, Language.NL)}. Hoeveel ${target.nl} staan in de tabel?",
                fr = "Tableau : ${rows(counts, Language.FR)}. Combien de ${target.fr} figurent dans le tableau ?",
                en = "Table: ${rows(counts, Language.EN)}. How many ${target.en} are in the table?"
            )
            PromptKind.PICTOGRAM_LOOKUP -> LocalizedText(
                nl = "Pictogram (elke ● is 1): ${symbolRows(counts, Language.NL, '●')}. Hoeveel ${target.nl} zijn er?",
                fr = "Pictogramme (chaque ● vaut 1) : ${symbolRows(counts, Language.FR, '●')}. Combien y a-t-il de ${target.fr} ?",
                en = "Pictogram (each ● means 1): ${symbolRows(counts, Language.EN, '●')}. How many ${target.en} are there?"
            )
            PromptKind.TALLY_LOOKUP -> LocalizedText(
                nl = "Turftabel: ${tallyRows(counts, Language.NL)}. Hoeveel turfstrepen staan bij ${target.nl}?",
                fr = "Tableau de bâtons : ${tallyRows(counts, Language.FR)}. Combien de bâtons y a-t-il pour les ${target.fr} ?",
                en = "Tally table: ${tallyRows(counts, Language.EN)}. How many tally marks are shown for ${target.en}?"
            )
            PromptKind.BAR_LOOKUP -> LocalizedText(
                nl = "Staafdiagram (elk blokje is 1): ${symbolRows(counts, Language.NL, '■')}. Hoeveel ${target.nl} zijn er?",
                fr = "Diagramme en barres (chaque carré vaut 1) : ${symbolRows(counts, Language.FR, '■')}. Combien y a-t-il de ${target.fr} ?",
                en = "Bar chart (each square means 1): ${symbolRows(counts, Language.EN, '■')}. How many ${target.en} are there?"
            )
            PromptKind.TOTAL_TWO -> LocalizedText(
                nl = "Tabel: ${rows(counts, Language.NL)}. Hoeveel ${Category.APPLES.nl} en ${Category.PEARS.nl} zijn er samen?",
                fr = "Tableau : ${rows(counts, Language.FR)}. Combien y a-t-il de ${Category.APPLES.fr} et de ${Category.PEARS.fr} en tout ?",
                en = "Table: ${rows(counts, Language.EN)}. How many ${Category.APPLES.en} and ${Category.PEARS.en} are there altogether?"
            )
            PromptKind.DIFFERENCE -> LocalizedText(
                nl = "Tabel: ${rows(counts, Language.NL)}. Hoeveel meer ${Category.APPLES.nl} dan ${Category.PEARS.nl} zijn er?",
                fr = "Tableau : ${rows(counts, Language.FR)}. Combien y a-t-il de ${Category.APPLES.fr} de plus que de ${Category.PEARS.fr} ?",
                en = "Table: ${rows(counts, Language.EN)}. How many more ${Category.APPLES.en} than ${Category.PEARS.en} are there?"
            )
            PromptKind.MISSING_FROM_TOTAL -> LocalizedText(
                nl = "Er zijn ${counts.sum()} vruchten: ${counts[0]} ${Category.APPLES.nl}, ${counts[1]} ${Category.PEARS.nl} en de rest zijn ${Category.BANANAS.nl}. Hoeveel ${Category.BANANAS.nl} zijn er?",
                fr = "Il y a ${counts.sum()} fruits : ${counts[0]} ${Category.APPLES.fr}, ${counts[1]} ${Category.PEARS.fr} et le reste sont des ${Category.BANANAS.fr}. Combien y a-t-il de ${Category.BANANAS.fr} ?",
                en = "There are ${counts.sum()} pieces of fruit: ${counts[0]} ${Category.APPLES.en}, ${counts[1]} ${Category.PEARS.en}, and the rest are ${Category.BANANAS.en}. How many ${Category.BANANAS.en} are there?"
            )
            PromptKind.TOTAL_ALL -> LocalizedText(
                nl = "Tabel: ${rows(counts, Language.NL)}. Hoeveel vruchten zijn er in totaal?",
                fr = "Tableau : ${rows(counts, Language.FR)}. Combien y a-t-il de fruits en tout ?",
                en = "Table: ${rows(counts, Language.EN)}. How many pieces of fruit are there in total?"
            )
        }
    }

    private fun spokenPrompt(candidate: Candidate): LocalizedText {
        val target = Category.entries[candidate.targetIndex]
        val counts = candidate.counts
        val nlData = spokenRows(counts, Language.NL)
        val frData = spokenRows(counts, Language.FR)
        val enData = spokenRows(counts, Language.EN)
        return when (candidate.kind) {
            PromptKind.TABLE_LOOKUP,
            PromptKind.PICTOGRAM_LOOKUP,
            PromptKind.TALLY_LOOKUP,
            PromptKind.BAR_LOOKUP -> LocalizedText(
                nl = "De gegevens zijn $nlData. Hoeveel ${target.nl} zijn er",
                fr = "Les données sont $frData. Combien y a-t-il de ${target.fr}",
                en = "The data are $enData. How many ${target.en} are there"
            )
            PromptKind.TOTAL_TWO -> LocalizedText(
                nl = "De gegevens zijn $nlData. Hoeveel ${Category.APPLES.nl} en ${Category.PEARS.nl} zijn er samen",
                fr = "Les données sont $frData. Combien y a-t-il de ${Category.APPLES.fr} et de ${Category.PEARS.fr} en tout",
                en = "The data are $enData. How many ${Category.APPLES.en} and ${Category.PEARS.en} are there altogether"
            )
            PromptKind.DIFFERENCE -> LocalizedText(
                nl = "De gegevens zijn $nlData. Hoeveel meer ${Category.APPLES.nl} dan ${Category.PEARS.nl} zijn er",
                fr = "Les données sont $frData. Combien y a-t-il de ${Category.APPLES.fr} de plus que de ${Category.PEARS.fr}",
                en = "The data are $enData. How many more ${Category.APPLES.en} than ${Category.PEARS.en} are there"
            )
            PromptKind.MISSING_FROM_TOTAL -> LocalizedText(
                nl = "Er zijn ${counts.sum()} vruchten, met ${counts[0]} ${Category.APPLES.nl} en ${counts[1]} ${Category.PEARS.nl}. De rest zijn ${Category.BANANAS.nl}. Hoeveel ${Category.BANANAS.nl} zijn er",
                fr = "Il y a ${counts.sum()} fruits, avec ${counts[0]} ${Category.APPLES.fr} et ${counts[1]} ${Category.PEARS.fr}. Le reste sont des ${Category.BANANAS.fr}. Combien y a-t-il de ${Category.BANANAS.fr}",
                en = "There are ${counts.sum()} pieces of fruit, with ${counts[0]} ${Category.APPLES.en} and ${counts[1]} ${Category.PEARS.en}. The rest are ${Category.BANANAS.en}. How many ${Category.BANANAS.en} are there"
            )
            PromptKind.TOTAL_ALL -> LocalizedText(
                nl = "De gegevens zijn $nlData. Hoeveel vruchten zijn er in totaal",
                fr = "Les données sont $frData. Combien y a-t-il de fruits en tout",
                en = "The data are $enData. How many pieces of fruit are there in total"
            )
        }
    }

    private fun rows(counts: List<Int>, language: Language): String = Category.entries
        .mapIndexed { index, category -> "${category.label(language)} ${counts[index]}" }
        .joinToString("; ")

    private fun symbolRows(counts: List<Int>, language: Language, symbol: Char): String =
        Category.entries.mapIndexed { index, category ->
            "${category.label(language)} ${symbol.toString().repeat(counts[index])}"
        }.joinToString("; ")

    private fun tallyRows(counts: List<Int>, language: Language): String = Category.entries
        .mapIndexed { index, category -> "${category.label(language)} ${tally(counts[index])}" }
        .joinToString("; ")

    private fun tally(count: Int): String = buildList {
        repeat(count / 5) { add("|||||") }
        if (count % 5 != 0) add("|".repeat(count % 5))
    }.joinToString(" ")

    private fun spokenRows(counts: List<Int>, language: Language): String = Category.entries
        .mapIndexed { index, category -> "${category.label(language)} ${counts[index]}" }
        .joinToString(", ")

    private fun representation(kind: PromptKind): String = when (kind) {
        PromptKind.PICTOGRAM_LOOKUP -> "pictogram"
        PromptKind.TALLY_LOOKUP -> "tally_table"
        PromptKind.BAR_LOOKUP -> "bar_chart"
        else -> "number_table"
    }

    private fun answerRole(kind: PromptKind): String = when (kind) {
        PromptKind.TABLE_LOOKUP,
        PromptKind.PICTOGRAM_LOOKUP,
        PromptKind.TALLY_LOOKUP,
        PromptKind.BAR_LOOKUP -> "category_count"
        PromptKind.TOTAL_TWO, PromptKind.TOTAL_ALL -> "total_count"
        PromptKind.DIFFERENCE -> "count_difference"
        PromptKind.MISSING_FROM_TOTAL -> "missing_count"
    }

    private fun hintNl(kind: PromptKind): String = when (kind) {
        PromptKind.TABLE_LOOKUP -> "Zoek de juiste rij en lees het getal."
        PromptKind.PICTOGRAM_LOOKUP -> "Elk bolletje stelt één voorwerp voor."
        PromptKind.TALLY_LOOKUP -> "Tel elke turfmarkering één keer."
        PromptKind.BAR_LOOKUP -> "Elk blokje stelt één voorwerp voor."
        PromptKind.TOTAL_TWO -> "Tel de twee gevraagde aantallen op."
        PromptKind.DIFFERENCE -> "Trek het kleinste aantal af van het grootste."
        PromptKind.MISSING_FROM_TOTAL -> "Trek de bekende aantallen af van het totaal."
        PromptKind.TOTAL_ALL -> "Tel alle drie de aantallen op."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.TABLE_LOOKUP -> "Cherche la bonne ligne et lis le nombre."
        PromptKind.PICTOGRAM_LOOKUP -> "Chaque rond représente un objet."
        PromptKind.TALLY_LOOKUP -> "Compte chaque bâton une seule fois."
        PromptKind.BAR_LOOKUP -> "Chaque carré représente un objet."
        PromptKind.TOTAL_TWO -> "Additionne les deux quantités demandées."
        PromptKind.DIFFERENCE -> "Soustrais la plus petite quantité de la plus grande."
        PromptKind.MISSING_FROM_TOTAL -> "Soustrais les quantités connues du total."
        PromptKind.TOTAL_ALL -> "Additionne les trois quantités."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.TABLE_LOOKUP -> "Find the correct row and read the number."
        PromptKind.PICTOGRAM_LOOKUP -> "Each circle represents one item."
        PromptKind.TALLY_LOOKUP -> "Count each tally mark once."
        PromptKind.BAR_LOOKUP -> "Each square represents one item."
        PromptKind.TOTAL_TWO -> "Add the two requested counts."
        PromptKind.DIFFERENCE -> "Subtract the smaller count from the larger count."
        PromptKind.MISSING_FROM_TOTAL -> "Subtract the known counts from the total."
        PromptKind.TOTAL_ALL -> "Add all three counts."
    }

    private data class Candidate(
        val kind: PromptKind,
        val answer: Int,
        val counts: List<Int>,
        val targetIndex: Int
    ) {
        fun signature(): String =
            "${kind.name}:${answer}:${counts.joinToString(":")}:$targetIndex"
    }

    private enum class Phase { GUIDED, CHECKPOINT }

    private enum class PromptKind {
        TABLE_LOOKUP,
        PICTOGRAM_LOOKUP,
        TALLY_LOOKUP,
        BAR_LOOKUP,
        TOTAL_TWO,
        DIFFERENCE,
        MISSING_FROM_TOTAL,
        TOTAL_ALL
    }

    private enum class Language { NL, FR, EN }

    private enum class Category(val nl: String, val fr: String, val en: String) {
        APPLES("appels", "pommes", "apples"),
        PEARS("peren", "poires", "pears"),
        BANANAS("bananen", "bananes", "bananas");

        fun label(language: Language): String = when (language) {
            Language.NL -> nl
            Language.FR -> fr
            Language.EN -> en
        }
    }
}
