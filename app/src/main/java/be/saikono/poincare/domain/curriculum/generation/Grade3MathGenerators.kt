package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

internal data class Grade3NumberCandidate(
    val kind: String,
    val answer: Int,
    val values: List<Int>,
    val unit: String = "number"
) {
    fun signature(): String = "$kind:${values.joinToString(":")}:$answer:$unit"
}

internal abstract class Grade3NumberGenerator(
    final override val generatorId: String,
    private val roadmapEntry: String,
    private val idNamespace: String,
    private val skill: String,
    private val domain: LearningDomain,
    private val kinds: List<String>,
    private val automatedScope: String
) : LessonExerciseGenerator {
    final override val generatorVersion: Int = 1
    private val allowedParameterKeys = setOf("grade", "roadmap_entry", "phase", "editorial_status")

    final override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["grade"] != "3") add("grade must be 3")
        if (parameters["roadmap_entry"] != roadmapEntry) add("roadmap_entry must be $roadmapEntry")
        if (parameters["phase"] !in setOf("guided", "checkpoint")) add("phase must be guided or checkpoint")
        if (parameters["editorial_status"] != LessonGeneratorRegistry.APPROVED_STATUS) {
            add("editorial_status must be approved")
        }
    }

    final override fun generate(
        seed: LessonGenerationSeed,
        exerciseCount: Int,
        parameters: Map<String, String>
    ): List<ExerciseContent> {
        val issues = validate(parameters)
        require(issues.isEmpty()) { issues.joinToString("; ") }
        require(exerciseCount > 0)
        val phase = parameters.getValue("phase")
        val random = StableLessonRandom(seed.value)
        val offset = if (phase == "guided") 0 else random.nextInt(kinds.size)
        val used = linkedSetOf<String>()
        return List(exerciseCount) { index ->
            val kind = kinds[(index + offset) % kinds.size]
            val candidate = uniqueCandidate(kind, phase, random, used)
            ExerciseContent(
                id = "generated.$idNamespace.v1.${seed.fingerprint}.${index + 1}",
                domain = domain,
                skill = skill,
                section = if (phase == "guided") ExerciseSection.PRACTICE else ExerciseSection.TESTS,
                level = SkillLevel.THIRD_GRADE,
                inputMode = InputMode.CUSTOM_NUMBER_PAD,
                prompt = visible(candidate),
                speechText = spoken(candidate),
                data = linkedMapOf(
                    "generatorId" to generatorId,
                    "generatorVersion" to generatorVersion.toString(),
                    "seedFingerprint" to seed.fingerprint,
                    "roadmapEntry" to roadmapEntry,
                    "phase" to phase,
                    "promptKind" to candidate.kind,
                    "answerRole" to "numeric",
                    "automatedScope" to automatedScope,
                    "values" to candidate.values.joinToString(","),
                    "answerUnit" to candidate.unit,
                    "answer_nl-BE" to candidate.answer.toString(),
                    "answer_fr-BE" to candidate.answer.toString(),
                    "answer_en-US" to candidate.answer.toString(),
                    "hint_nl-BE" to hint(candidate).nl,
                    "hint_fr-BE" to hint(candidate).fr.orEmpty(),
                    "hint_en-US" to hint(candidate).en.orEmpty()
                ),
                answer = candidate.answer.toString()
            )
        }
    }

    private fun uniqueCandidate(
        kind: String,
        phase: String,
        random: StableLessonRandom,
        used: MutableSet<String>
    ): Grade3NumberCandidate {
        repeat(256) {
            val candidate = create(kind, phase, random)
            if (used.add(candidate.signature())) return candidate
        }
        error("Unable to create a unique Grade 3 exercise for $generatorId/$kind")
    }

    protected abstract fun create(kind: String, phase: String, random: StableLessonRandom): Grade3NumberCandidate
    protected abstract fun visible(candidate: Grade3NumberCandidate): LocalizedText
    protected abstract fun spoken(candidate: Grade3NumberCandidate): LocalizedText
    protected abstract fun hint(candidate: Grade3NumberCandidate): LocalizedText
}

internal object Grade3StatisticsCompareDataGenerator : Grade3NumberGenerator(
    generatorId = "generator.g3.math.statistics.compare_data",
    roadmapEntry = "roadmap.g3.math.statistics.compare_data",
    idNamespace = "g3.math.statistics.compare_data",
    skill = "statistics_compare_data",
    domain = LearningDomain.MATH,
    kinds = listOf("read_value", "difference", "total", "missing", "combined", "increase"),
    automatedScope = "table_and_graph_numeric_interpretation"
) {
    override fun create(kind: String, phase: String, random: StableLessonRandom): Grade3NumberCandidate {
        val maximum = if (phase == "guided") 20 else 40
        val a = random.nextInt(3, maximum)
        val b = random.nextInt(2, maximum)
        val c = random.nextInt(1, maximum)
        return when (kind) {
            "read_value" -> Grade3NumberCandidate(kind, b, listOf(a, b, c))
            "difference" -> Grade3NumberCandidate(kind, kotlin.math.abs(a - b), listOf(a, b, c))
            "total" -> Grade3NumberCandidate(kind, a + b + c, listOf(a, b, c))
            "missing" -> Grade3NumberCandidate(kind, c, listOf(a, b, a + b + c))
            "combined" -> Grade3NumberCandidate(kind, a + c, listOf(a, b, c))
            else -> {
                val higher = maxOf(a, b)
                val lower = minOf(a, b)
                Grade3NumberCandidate(kind, higher - lower, listOf(lower, higher, c))
            }
        }
    }

    override fun visible(candidate: Grade3NumberCandidate): LocalizedText {
        val (a, b, c) = candidate.values
        val tableNl = "Tabel: appels $a, peren $b, pruimen $c."
        val tableFr = "Tableau : pommes $a, poires $b, prunes $c."
        val tableEn = "Table: apples $a, pears $b, plums $c."
        return when (candidate.kind) {
            "read_value" -> LocalizedText("$tableNl Hoeveel peren?", "$tableFr Combien de poires ?", "$tableEn How many pears?")
            "difference" -> LocalizedText("$tableNl Hoeveel verschillen appels en peren?", "$tableFr Quelle est la différence entre les pommes et les poires ?", "$tableEn What is the difference between apples and pears?")
            "total" -> LocalizedText("$tableNl Hoeveel stuks fruit zijn er samen?", "$tableFr Combien de fruits y a-t-il en tout ?", "$tableEn How many pieces of fruit are there altogether?")
            "missing" -> LocalizedText("Appels $a, peren $b, totaal $c. Hoeveel pruimen ontbreken in de tabel?", "Pommes $a, poires $b, total $c. Combien de prunes manquent dans le tableau ?", "Apples $a, pears $b, total $c. How many plums are missing from the table?")
            "combined" -> LocalizedText("$tableNl Hoeveel appels en pruimen samen?", "$tableFr Combien de pommes et de prunes ensemble ?", "$tableEn How many apples and plums altogether?")
            else -> LocalizedText("De eerste balk is $a hoog en de tweede $b. Hoeveel moet de eerste stijgen om even hoog te worden?", "La première barre vaut $a et la deuxième $b. De combien la première doit-elle augmenter pour être aussi haute ?", "The first bar is $a and the second is $b. How much must the first increase to become equal?")
        }
    }

    override fun spoken(candidate: Grade3NumberCandidate): LocalizedText = visible(candidate)

    override fun hint(candidate: Grade3NumberCandidate): LocalizedText = LocalizedText(
        "Lees eerst de juiste rij of balk en kies daarna de bewerking.",
        "Lis d'abord la bonne ligne ou barre, puis choisis l'opération.",
        "Read the correct row or bar first, then choose the operation."
    )
}

internal object Grade3MultiplicationDivisionFractionsGenerator : Grade3NumberGenerator(
    generatorId = "generator.g3.math.multiplication_division_fractions",
    roadmapEntry = "roadmap.g3.math.multiplication_division_fractions",
    idNamespace = "g3.math.multiplication_division_fractions",
    skill = "multiplication_division_fractions",
    domain = LearningDomain.MATH,
    kinds = listOf("multiply", "divide", "missing_factor", "equal_groups", "equal_sharing", "numerator", "denominator", "fraction_of_set"),
    automatedScope = "tables_exact_division_and_first_fraction_number"
) {
    override fun create(kind: String, phase: String, random: StableLessonRandom): Grade3NumberCandidate {
        val maxFactor = if (phase == "guided") 7 else 10
        val left = random.nextInt(2, maxFactor + 1)
        val right = random.nextInt(2, 11)
        return when (kind) {
            "multiply" -> Grade3NumberCandidate(kind, left * right, listOf(left, right))
            "divide" -> Grade3NumberCandidate(kind, right, listOf(left * right, left))
            "missing_factor" -> Grade3NumberCandidate(kind, right, listOf(left, left * right))
            "equal_groups" -> Grade3NumberCandidate(kind, left * right, listOf(left, right))
            "equal_sharing" -> Grade3NumberCandidate(kind, right, listOf(left * right, left))
            "numerator" -> {
                val denominator = random.nextInt(3, 9)
                val numerator = random.nextInt(1, denominator)
                Grade3NumberCandidate(kind, numerator, listOf(numerator, denominator))
            }
            "denominator" -> {
                val denominator = random.nextInt(3, 9)
                Grade3NumberCandidate(kind, denominator, listOf(1, denominator))
            }
            else -> {
                val denominator = listOf(2, 3, 4)[random.nextInt(3)]
                val share = random.nextInt(2, if (phase == "guided") 6 else 10)
                Grade3NumberCandidate(kind, share, listOf(1, denominator, share * denominator))
            }
        }
    }

    override fun visible(candidate: Grade3NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "multiply" -> LocalizedText("Bereken ${v[0]} × ${v[1]}.", "Calcule ${v[0]} × ${v[1]}.", "Calculate ${v[0]} × ${v[1]}.")
            "divide" -> LocalizedText("Bereken ${v[0]} : ${v[1]}.", "Calcule ${v[0]} : ${v[1]}.", "Calculate ${v[0]} ÷ ${v[1]}.")
            "missing_factor" -> LocalizedText("Vul aan: ${v[0]} × ? = ${v[1]}.", "Complète : ${v[0]} × ? = ${v[1]}.", "Complete: ${v[0]} × ? = ${v[1]}.")
            "equal_groups" -> LocalizedText("Er zijn ${v[0]} dozen met ${v[1]} potloden. Hoeveel potloden samen?", "Il y a ${v[0]} boîtes de ${v[1]} crayons. Combien de crayons en tout ?", "There are ${v[0]} boxes with ${v[1]} pencils each. How many pencils altogether?")
            "equal_sharing" -> LocalizedText("${v[0]} kaarten worden gelijk verdeeld over ${v[1]} kinderen. Hoeveel krijgt ieder?", "${v[0]} cartes sont partagées entre ${v[1]} enfants. Combien chacun en reçoit-il ?", "${v[0]} cards are shared equally among ${v[1]} children. How many does each child get?")
            "numerator" -> LocalizedText("${v[0]} van de ${v[1]} gelijke delen zijn gekleurd. Wat is de teller?", "${v[0]} des ${v[1]} parts égales sont coloriées. Quel est le numérateur ?", "${v[0]} of ${v[1]} equal parts are shaded. What is the numerator?")
            "denominator" -> LocalizedText("Een geheel is verdeeld in ${v[1]} gelijke delen. Wat is de noemer van 1/${v[1]}?", "Un tout est partagé en ${v[1]} parts égales. Quel est le dénominateur de 1/${v[1]} ?", "A whole is split into ${v[1]} equal parts. What is the denominator of 1/${v[1]}?")
            else -> LocalizedText("Hoeveel is ${v[0]}/${v[1]} van ${v[2]}?", "Combien vaut ${v[0]}/${v[1]} de ${v[2]} ?", "What is ${v[0]}/${v[1]} of ${v[2]}?")
        }
    }

    override fun spoken(candidate: Grade3NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "multiply" -> LocalizedText("Hoeveel is ${v[0]} maal ${v[1]}", "Combien font ${v[0]} fois ${v[1]}", "What is ${v[0]} times ${v[1]}")
            "divide" -> LocalizedText("Hoeveel is ${v[0]} gedeeld door ${v[1]}", "Combien font ${v[0]} divisé par ${v[1]}", "What is ${v[0]} divided by ${v[1]}")
            "missing_factor" -> LocalizedText("${v[0]} maal hoeveel is ${v[1]}", "${v[0]} fois combien font ${v[1]}", "${v[0]} times what equals ${v[1]}")
            "fraction_of_set" -> LocalizedText("Hoeveel is één ${fractionNameNl(v[1])} van ${v[2]}", "Combien vaut un ${fractionNameFr(v[1])} de ${v[2]}", "What is one ${fractionNameEn(v[1])} of ${v[2]}")
            else -> visible(candidate)
        }
    }

    override fun hint(candidate: Grade3NumberCandidate): LocalizedText = LocalizedText(
        "Gebruik een gekende tafel, de omgekeerde deelbewerking of verdeel in gelijke delen.",
        "Utilise une table connue, l'opération inverse ou partage en parts égales.",
        "Use a known times table, the inverse division, or split into equal parts."
    )

    private fun fractionNameNl(denominator: Int) = when (denominator) { 2 -> "helft"; 3 -> "derde"; else -> "vierde" }
    private fun fractionNameFr(denominator: Int) = when (denominator) { 2 -> "demi"; 3 -> "tiers"; else -> "quart" }
    private fun fractionNameEn(denominator: Int) = when (denominator) { 2 -> "half"; 3 -> "third"; else -> "quarter" }
}

internal object Grade3MoneyTimeGenerator : Grade3NumberGenerator(
    generatorId = "generator.g3.geometry.money_time",
    roadmapEntry = "roadmap.g3.geometry.money_time",
    idNamespace = "g3.geometry.money_time",
    skill = "money_time",
    domain = LearningDomain.GEOMETRY,
    kinds = listOf("money_total", "change", "euros_to_cents", "cents_to_euros", "duration", "minutes_after", "weeks_days", "calendar_later"),
    automatedScope = "money_clock_duration_and_calendar_number"
) {
    override fun create(kind: String, phase: String, random: StableLessonRandom): Grade3NumberCandidate {
        return when (kind) {
            "money_total" -> {
                val a = random.nextInt(50, if (phase == "guided") 301 else 501)
                val b = random.nextInt(20, if (phase == "guided") 201 else 401)
                Grade3NumberCandidate(kind, a + b, listOf(a, b), "cent")
            }
            "change" -> {
                val paid = if (phase == "guided") 500 else 1000
                val price = random.nextInt(100, paid - 49)
                Grade3NumberCandidate(kind, paid - price, listOf(price, paid), "cent")
            }
            "euros_to_cents" -> {
                val euros = random.nextInt(1, if (phase == "guided") 10 else 21)
                Grade3NumberCandidate(kind, euros * 100, listOf(euros), "cent")
            }
            "cents_to_euros" -> {
                val euros = random.nextInt(1, if (phase == "guided") 10 else 21)
                Grade3NumberCandidate(kind, euros, listOf(euros * 100), "euro")
            }
            "duration" -> {
                val start = random.nextInt(7, 18)
                val minutes = listOf(15, 30, 45, 60, 90)[random.nextInt(if (phase == "guided") 4 else 5)]
                Grade3NumberCandidate(kind, minutes, listOf(start, 0, start + minutes / 60, minutes % 60), "minute")
            }
            "minutes_after" -> {
                val startMinute = listOf(0, 15, 30)[random.nextInt(3)]
                val add = listOf(15, 30)[random.nextInt(2)]
                Grade3NumberCandidate(kind, (startMinute + add) % 60, listOf(startMinute, add), "minute_on_clock")
            }
            "weeks_days" -> {
                val weeks = random.nextInt(2, if (phase == "guided") 6 else 10)
                Grade3NumberCandidate(kind, weeks * 7, listOf(weeks), "day")
            }
            else -> {
                val day = random.nextInt(1, 21)
                val later = random.nextInt(2, if (phase == "guided") 8 else 11)
                Grade3NumberCandidate(kind, day + later, listOf(day, later), "calendar_day")
            }
        }
    }

    override fun visible(candidate: Grade3NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "money_total" -> LocalizedText("Een boek kost ${v[0]} cent en een pen ${v[1]} cent. Hoeveel cent samen?", "Un livre coûte ${v[0]} cents et un stylo ${v[1]} cents. Combien de cents en tout ?", "A book costs ${v[0]} cents and a pen ${v[1]} cents. How many cents altogether?")
            "change" -> LocalizedText("Je betaalt ${v[1]} cent voor iets van ${v[0]} cent. Hoeveel cent krijg je terug?", "Tu paies ${v[1]} cents pour un article à ${v[0]} cents. Combien de cents reçois-tu ?", "You pay ${v[1]} cents for something costing ${v[0]} cents. How many cents change do you get?")
            "euros_to_cents" -> LocalizedText("Hoeveel cent is ${v[0]} euro?", "Combien de cents représentent ${v[0]} euros ?", "How many cents is ${v[0]} euros?")
            "cents_to_euros" -> LocalizedText("Hoeveel hele euro is ${v[0]} cent?", "Combien d'euros entiers représentent ${v[0]} cents ?", "How many whole euros is ${v[0]} cents?")
            "duration" -> LocalizedText("Een activiteit start om ${v[0]}:00 en eindigt om ${v[2]}:${v[3].toString().padStart(2, '0')}. Hoeveel minuten duurt ze?", "Une activité commence à ${v[0]} h 00 et finit à ${v[2]} h ${v[3].toString().padStart(2, '0')}. Combien de minutes dure-t-elle ?", "An activity starts at ${v[0]}:00 and ends at ${v[2]}:${v[3].toString().padStart(2, '0')}. How many minutes does it last?")
            "minutes_after" -> LocalizedText("De klok toont 10:${v[0].toString().padStart(2, '0')}. Hoeveel minuten na het uur toont ze ${v[1]} minuten later?", "Il est 10 h ${v[0].toString().padStart(2, '0')}. Combien de minutes après l'heure indiquera l'horloge ${v[1]} minutes plus tard ?", "The clock shows 10:${v[0].toString().padStart(2, '0')}. How many minutes past the hour will it show ${v[1]} minutes later?")
            "weeks_days" -> LocalizedText("Hoeveel dagen zijn ${v[0]} weken?", "Combien de jours représentent ${v[0]} semaines ?", "How many days are ${v[0]} weeks?")
            else -> LocalizedText("Vandaag is het de ${v[0]}e. Welke datum is het ${v[1]} dagen later? Geef het dagnummer.", "Nous sommes le ${v[0]}. Quelle date sera-t-on ${v[1]} jours plus tard ? Donne le numéro du jour.", "Today is the ${v[0]}th. What date is it ${v[1]} days later? Enter the day number.")
        }
    }

    override fun spoken(candidate: Grade3NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "money_total" -> LocalizedText("Een boek kost ${v[0]} cent en een pen ${v[1]} cent. Hoeveel cent samen", "Un livre coûte ${v[0]} cents et un stylo ${v[1]} cents. Combien de cents en tout", "A book costs ${v[0]} cents and a pen ${v[1]} cents. How many cents altogether")
            "change" -> LocalizedText("Je betaalt ${v[1]} cent voor iets van ${v[0]} cent. Hoeveel cent krijg je terug", "Tu paies ${v[1]} cents pour un article à ${v[0]} cents. Combien de cents reçois-tu", "You pay ${v[1]} cents for something costing ${v[0]} cents. How many cents change do you get")
            else -> visible(candidate)
        }
    }

    override fun hint(candidate: Grade3NumberCandidate): LocalizedText = LocalizedText(
        "Zet bedragen in dezelfde eenheid en teken bij tijd een korte tijdlijn.",
        "Mets les montants dans la même unité et trace une petite ligne du temps.",
        "Use the same unit for money and draw a short timeline for time."
    )
}
