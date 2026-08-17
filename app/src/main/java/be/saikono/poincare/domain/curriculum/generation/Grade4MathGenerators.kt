package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel
import kotlin.math.abs

internal data class Grade4NumberCandidate(
    val kind: String,
    val answer: Int,
    val values: List<Int>,
    val unit: String = ""
) {
    fun signature(): String = "$kind:${values.joinToString(":")}:$answer:$unit"
}

internal abstract class Grade4NumberGenerator(
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
        if (parameters["grade"] != "4") add("grade must be 4")
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
                level = SkillLevel.FOURTH_GRADE,
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
    ): Grade4NumberCandidate {
        repeat(256) {
            val candidate = create(kind, phase, random)
            if (used.add(candidate.signature())) return candidate
        }
        error("Unable to create a unique Grade 4 exercise for $generatorId/$kind")
    }

    protected abstract fun create(kind: String, phase: String, random: StableLessonRandom): Grade4NumberCandidate
    protected abstract fun visible(candidate: Grade4NumberCandidate): LocalizedText
    protected abstract fun spoken(candidate: Grade4NumberCandidate): LocalizedText
    protected abstract fun hint(candidate: Grade4NumberCandidate): LocalizedText
}

internal object Grade4PlaceValueOperationsGenerator : Grade4NumberGenerator(
    generatorId = "generator.g4.math.place_value.operations",
    roadmapEntry = "roadmap.g4.math.place_value.operations",
    idNamespace = "g4.math.place_value.operations",
    skill = "place_value_operations",
    domain = LearningDomain.MATH,
    kinds = listOf("digit_value", "compose", "round_10", "round_100", "add", "subtract", "multiply", "divide", "missing_addend"),
    automatedScope = "place_value_rounding_and_operations_to_ten_thousand"
) {
    override fun create(kind: String, phase: String, random: StableLessonRandom): Grade4NumberCandidate {
        val maxNumber = if (phase == "guided") 5_000 else 10_000
        return when (kind) {
            "digit_value" -> {
                val place = listOf(10, 100, 1_000)[random.nextInt(3)]
                val number = random.nextInt(1_001, maxNumber)
                Grade4NumberCandidate(kind, (number / place % 10) * place, listOf(number, place))
            }
            "compose" -> {
                val thousands = random.nextInt(1, if (phase == "guided") 6 else 10)
                val hundreds = random.nextInt(0, 10)
                val tens = random.nextInt(0, 10)
                val ones = random.nextInt(0, 10)
                Grade4NumberCandidate(
                    kind,
                    thousands * 1_000 + hundreds * 100 + tens * 10 + ones,
                    listOf(thousands, hundreds, tens, ones)
                )
            }
            "round_10" -> {
                val number = random.nextInt(101, maxNumber)
                Grade4NumberCandidate(kind, ((number + 5) / 10) * 10, listOf(number))
            }
            "round_100" -> {
                val number = random.nextInt(101, maxNumber)
                Grade4NumberCandidate(kind, ((number + 50) / 100) * 100, listOf(number))
            }
            "add" -> {
                val left = random.nextInt(200, maxNumber / 2)
                val right = random.nextInt(100, maxNumber - left)
                Grade4NumberCandidate(kind, left + right, listOf(left, right))
            }
            "subtract" -> {
                val result = random.nextInt(100, maxNumber / 2)
                val right = random.nextInt(100, maxNumber / 2)
                Grade4NumberCandidate(kind, result, listOf(result + right, right))
            }
            "multiply" -> {
                val left = random.nextInt(11, if (phase == "guided") 51 else 100)
                val right = random.nextInt(2, 10)
                Grade4NumberCandidate(kind, left * right, listOf(left, right))
            }
            "divide" -> {
                val divisor = random.nextInt(2, 10)
                val quotient = random.nextInt(11, if (phase == "guided") 51 else 101)
                Grade4NumberCandidate(kind, quotient, listOf(divisor * quotient, divisor))
            }
            else -> {
                val left = random.nextInt(100, maxNumber / 2)
                val missing = random.nextInt(100, maxNumber / 2)
                Grade4NumberCandidate(kind, missing, listOf(left, left + missing))
            }
        }
    }

    override fun visible(candidate: Grade4NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "digit_value" -> LocalizedText("Wat is de waarde van het cijfer op de plaats van ${placeNl(v[1])} in ${v[0]}?", "Quelle est la valeur du chiffre des ${placeFr(v[1])} dans ${v[0]} ?", "What is the value of the ${placeEn(v[1])} digit in ${v[0]}?")
            "compose" -> LocalizedText("Vorm het getal met ${v[0]} duizendtallen, ${v[1]} honderdtallen, ${v[2]} tientallen en ${v[3]} eenheden.", "Forme le nombre avec ${v[0]} milliers, ${v[1]} centaines, ${v[2]} dizaines et ${v[3]} unités.", "Build the number with ${v[0]} thousands, ${v[1]} hundreds, ${v[2]} tens, and ${v[3]} ones.")
            "round_10" -> LocalizedText("Rond ${v[0]} af op het dichtstbijzijnde tiental.", "Arrondis ${v[0]} à la dizaine la plus proche.", "Round ${v[0]} to the nearest ten.")
            "round_100" -> LocalizedText("Rond ${v[0]} af op het dichtstbijzijnde honderdtal.", "Arrondis ${v[0]} à la centaine la plus proche.", "Round ${v[0]} to the nearest hundred.")
            "add" -> LocalizedText("Bereken ${v[0]} + ${v[1]}.", "Calcule ${v[0]} + ${v[1]}.", "Calculate ${v[0]} + ${v[1]}.")
            "subtract" -> LocalizedText("Bereken ${v[0]} − ${v[1]}.", "Calcule ${v[0]} − ${v[1]}.", "Calculate ${v[0]} − ${v[1]}.")
            "multiply" -> LocalizedText("Bereken ${v[0]} × ${v[1]}.", "Calcule ${v[0]} × ${v[1]}.", "Calculate ${v[0]} × ${v[1]}.")
            "divide" -> LocalizedText("Bereken ${v[0]} : ${v[1]}.", "Calcule ${v[0]} : ${v[1]}.", "Calculate ${v[0]} ÷ ${v[1]}.")
            else -> LocalizedText("Vul aan: ${v[0]} + ? = ${v[1]}.", "Complète : ${v[0]} + ? = ${v[1]}.", "Complete: ${v[0]} + ? = ${v[1]}.")
        }
    }

    override fun spoken(candidate: Grade4NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "add" -> LocalizedText("Hoeveel is ${v[0]} plus ${v[1]}", "Combien font ${v[0]} plus ${v[1]}", "What is ${v[0]} plus ${v[1]}")
            "subtract" -> LocalizedText("Hoeveel is ${v[0]} min ${v[1]}", "Combien font ${v[0]} moins ${v[1]}", "What is ${v[0]} minus ${v[1]}")
            "multiply" -> LocalizedText("Hoeveel is ${v[0]} maal ${v[1]}", "Combien font ${v[0]} fois ${v[1]}", "What is ${v[0]} times ${v[1]}")
            "divide" -> LocalizedText("Hoeveel is ${v[0]} gedeeld door ${v[1]}", "Combien font ${v[0]} divisé par ${v[1]}", "What is ${v[0]} divided by ${v[1]}")
            "missing_addend" -> LocalizedText("${v[0]} plus hoeveel is ${v[1]}", "${v[0]} plus combien font ${v[1]}", "${v[0]} plus what equals ${v[1]}")
            else -> visible(candidate)
        }
    }

    override fun hint(candidate: Grade4NumberCandidate): LocalizedText = LocalizedText(
        "Gebruik plaatswaarde, een getallenlijn of de omgekeerde bewerking.",
        "Utilise la valeur de position, une droite numérique ou l'opération inverse.",
        "Use place value, a number line, or the inverse operation."
    )

    private fun placeNl(place: Int) = when (place) { 10 -> "tientallen"; 100 -> "honderdtallen"; else -> "duizendtallen" }
    private fun placeFr(place: Int) = when (place) { 10 -> "dizaines"; 100 -> "centaines"; else -> "milliers" }
    private fun placeEn(place: Int) = when (place) { 10 -> "tens"; 100 -> "hundreds"; else -> "thousands" }
}

internal object Grade4MetricMeasurementGenerator : Grade4NumberGenerator(
    generatorId = "generator.g4.geometry.metric_measurement",
    roadmapEntry = "roadmap.g4.geometry.metric_measurement",
    idNamespace = "g4.geometry.metric_measurement",
    skill = "metric_measurement",
    domain = LearningDomain.GEOMETRY,
    kinds = listOf("m_to_cm", "cm_to_mm", "km_to_m", "perimeter_rectangle", "missing_side", "scale_distance", "path_total", "length_difference"),
    automatedScope = "metric_length_conversion_perimeter_and_simple_scale"
) {
    override fun create(kind: String, phase: String, random: StableLessonRandom): Grade4NumberCandidate {
        return when (kind) {
            "m_to_cm" -> {
                val metres = random.nextInt(2, if (phase == "guided") 16 else 31)
                Grade4NumberCandidate(kind, metres * 100, listOf(metres), "cm")
            }
            "cm_to_mm" -> {
                val centimetres = random.nextInt(2, if (phase == "guided") 31 else 81)
                Grade4NumberCandidate(kind, centimetres * 10, listOf(centimetres), "mm")
            }
            "km_to_m" -> {
                val kilometres = random.nextInt(1, if (phase == "guided") 8 else 16)
                Grade4NumberCandidate(kind, kilometres * 1_000, listOf(kilometres), "m")
            }
            "perimeter_rectangle" -> {
                val length = random.nextInt(3, if (phase == "guided") 16 else 31)
                val width = random.nextInt(2, length)
                Grade4NumberCandidate(kind, 2 * (length + width), listOf(length, width), "cm")
            }
            "missing_side" -> {
                val length = random.nextInt(4, if (phase == "guided") 16 else 31)
                val width = random.nextInt(2, length)
                Grade4NumberCandidate(kind, width, listOf(length, 2 * (length + width)), "cm")
            }
            "scale_distance" -> {
                val scale = if (phase == "guided") random.nextInt(2, 6) else random.nextInt(2, 11)
                val mapLength = random.nextInt(2, 9)
                Grade4NumberCandidate(kind, scale * mapLength, listOf(mapLength, scale), "km")
            }
            "path_total" -> {
                val first = random.nextInt(100, if (phase == "guided") 701 else 1_501)
                val second = random.nextInt(100, if (phase == "guided") 701 else 1_501)
                Grade4NumberCandidate(kind, first + second, listOf(first, second), "m")
            }
            else -> {
                val shorter = random.nextInt(20, if (phase == "guided") 101 else 301)
                val difference = random.nextInt(10, if (phase == "guided") 101 else 201)
                Grade4NumberCandidate(kind, difference, listOf(shorter + difference, shorter), "cm")
            }
        }
    }

    override fun visible(candidate: Grade4NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "m_to_cm" -> LocalizedText("Hoeveel centimeter is ${v[0]} meter?", "Combien de centimètres représentent ${v[0]} mètres ?", "How many centimeters are ${v[0]} meters?")
            "cm_to_mm" -> LocalizedText("Hoeveel millimeter is ${v[0]} centimeter?", "Combien de millimètres représentent ${v[0]} centimètres ?", "How many millimeters are ${v[0]} centimeters?")
            "km_to_m" -> LocalizedText("Hoeveel meter is ${v[0]} kilometer?", "Combien de mètres représentent ${v[0]} kilomètres ?", "How many meters are ${v[0]} kilometers?")
            "perimeter_rectangle" -> LocalizedText("Een rechthoek is ${v[0]} cm lang en ${v[1]} cm breed. Hoeveel centimeter is de omtrek?", "Un rectangle mesure ${v[0]} cm de long et ${v[1]} cm de large. Quel est son périmètre en centimètres ?", "A rectangle is ${v[0]} cm long and ${v[1]} cm wide. What is its perimeter in centimeters?")
            "missing_side" -> LocalizedText("Een rechthoek is ${v[0]} cm lang en heeft een omtrek van ${v[1]} cm. Hoeveel centimeter is de breedte?", "Un rectangle mesure ${v[0]} cm de long et son périmètre est de ${v[1]} cm. Quelle est sa largeur en centimètres ?", "A rectangle is ${v[0]} cm long and has a perimeter of ${v[1]} cm. What is its width in centimeters?")
            "scale_distance" -> LocalizedText("Op een kaart is 1 cm gelijk aan ${v[1]} km. Twee plaatsen liggen ${v[0]} cm uit elkaar. Hoeveel kilometer is dat echt?", "Sur une carte, 1 cm représente ${v[1]} km. Deux lieux sont séparés de ${v[0]} cm. Quelle est la distance réelle en kilomètres ?", "On a map, 1 cm represents ${v[1]} km. Two places are ${v[0]} cm apart. What is the real distance in kilometers?")
            "path_total" -> LocalizedText("Een wandelpad bestaat uit ${v[0]} m en daarna ${v[1]} m. Hoeveel meter is het pad samen?", "Un sentier comprend ${v[0]} m puis ${v[1]} m. Quelle est sa longueur totale en mètres ?", "A path is ${v[0]} m and then ${v[1]} m. How many meters long is it altogether?")
            else -> LocalizedText("Een lint is ${v[0]} cm lang en een ander ${v[1]} cm. Hoeveel centimeter is het verschil?", "Un ruban mesure ${v[0]} cm et l'autre ${v[1]} cm. Quelle est la différence en centimètres ?", "One ribbon is ${v[0]} cm long and another is ${v[1]} cm. What is the difference in centimeters?")
        }
    }

    override fun spoken(candidate: Grade4NumberCandidate): LocalizedText = visible(candidate)

    override fun hint(candidate: Grade4NumberCandidate): LocalizedText = LocalizedText(
        "Schrijf eerst de omzettingsfactor of tel alle zijden van de figuur.",
        "Écris d'abord le facteur de conversion ou additionne tous les côtés de la figure.",
        "Write the conversion factor first or add every side of the shape."
    )
}

internal object Grade4TemperatureScaleGenerator : Grade4NumberGenerator(
    generatorId = "generator.g4.geometry.temperature_scale",
    roadmapEntry = "roadmap.g4.geometry.temperature_scale",
    idNamespace = "g4.geometry.temperature_scale",
    skill = "temperature_scale",
    domain = LearningDomain.GEOMETRY,
    kinds = listOf("read_temperature", "temperature_rise", "temperature_fall", "difference", "target_after_rise", "degrees_to_freezing", "daily_range", "compare_warmer"),
    automatedScope = "thermometer_scale_change_and_temperature_comparison"
) {
    override fun create(kind: String, phase: String, random: StableLessonRandom): Grade4NumberCandidate {
        val maximum = if (phase == "guided") 25 else 40
        return when (kind) {
            "read_temperature" -> {
                val temperature = random.nextInt(0, maximum + 1)
                Grade4NumberCandidate(kind, temperature, listOf(temperature), "degree_celsius")
            }
            "temperature_rise" -> {
                val start = random.nextInt(0, maximum / 2)
                val rise = random.nextInt(2, maximum - start + 1)
                Grade4NumberCandidate(kind, rise, listOf(start, start + rise), "degree_celsius")
            }
            "temperature_fall" -> {
                val end = random.nextInt(0, maximum / 2)
                val fall = random.nextInt(2, maximum - end + 1)
                Grade4NumberCandidate(kind, fall, listOf(end + fall, end), "degree_celsius")
            }
            "difference", "compare_warmer" -> {
                val lower = random.nextInt(0, maximum / 2)
                val difference = random.nextInt(2, maximum - lower + 1)
                Grade4NumberCandidate(kind, difference, listOf(lower, lower + difference), "degree_celsius")
            }
            "target_after_rise" -> {
                val start = random.nextInt(0, maximum / 2)
                val rise = random.nextInt(2, maximum - start + 1)
                Grade4NumberCandidate(kind, start + rise, listOf(start, rise), "degree_celsius")
            }
            "degrees_to_freezing" -> {
                val temperature = random.nextInt(2, maximum + 1)
                Grade4NumberCandidate(kind, temperature, listOf(temperature), "degree_celsius")
            }
            else -> {
                val minimum = random.nextInt(0, maximum / 2)
                val range = random.nextInt(2, maximum - minimum + 1)
                Grade4NumberCandidate(kind, range, listOf(minimum, minimum + range), "degree_celsius")
            }
        }
    }

    override fun visible(candidate: Grade4NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "read_temperature" -> LocalizedText("De vloeistof staat bij ${v[0]} op de thermometer. Hoeveel graden Celsius is dat?", "Le liquide atteint ${v[0]} sur le thermomètre. Combien de degrés Celsius cela représente-t-il ?", "The liquid reaches ${v[0]} on the thermometer. How many degrees Celsius is that?")
            "temperature_rise" -> LocalizedText("De temperatuur stijgt van ${v[0]} °C naar ${v[1]} °C. Hoeveel graden stijgt ze?", "La température passe de ${v[0]} °C à ${v[1]} °C. De combien de degrés augmente-t-elle ?", "The temperature rises from ${v[0]} °C to ${v[1]} °C. How many degrees does it rise?")
            "temperature_fall" -> LocalizedText("De temperatuur daalt van ${v[0]} °C naar ${v[1]} °C. Hoeveel graden daalt ze?", "La température descend de ${v[0]} °C à ${v[1]} °C. De combien de degrés diminue-t-elle ?", "The temperature falls from ${v[0]} °C to ${v[1]} °C. How many degrees does it fall?")
            "difference" -> LocalizedText("Plaats A is ${v[0]} °C en plaats B ${v[1]} °C. Hoeveel graden is het verschil?", "Le lieu A est à ${v[0]} °C et le lieu B à ${v[1]} °C. Quelle est la différence ?", "Place A is ${v[0]} °C and place B is ${v[1]} °C. What is the difference?")
            "target_after_rise" -> LocalizedText("Het is ${v[0]} °C. De temperatuur stijgt ${v[1]} graden. Hoeveel graden Celsius wordt het?", "Il fait ${v[0]} °C. La température augmente de ${v[1]} degrés. Quelle sera la température ?", "It is ${v[0]} °C. The temperature rises by ${v[1]} degrees. What will the temperature be?")
            "degrees_to_freezing" -> LocalizedText("Het is ${v[0]} °C. Hoeveel graden moet de temperatuur dalen om 0 °C te bereiken?", "Il fait ${v[0]} °C. De combien de degrés la température doit-elle baisser pour atteindre 0 °C ?", "It is ${v[0]} °C. How many degrees must the temperature fall to reach 0 °C?")
            "daily_range" -> LocalizedText("De minimumtemperatuur is ${v[0]} °C en de maximumtemperatuur ${v[1]} °C. Hoe groot is het verschil?", "La température minimale est de ${v[0]} °C et la maximale de ${v[1]} °C. Quel est l'écart ?", "The minimum temperature is ${v[0]} °C and the maximum is ${v[1]} °C. What is the range?")
            else -> LocalizedText("Stad A heeft ${v[0]} °C en stad B ${v[1]} °C. Hoeveel graden warmer is stad B?", "La ville A a ${v[0]} °C et la ville B ${v[1]} °C. De combien de degrés la ville B est-elle plus chaude ?", "City A is ${v[0]} °C and city B is ${v[1]} °C. How many degrees warmer is city B?")
        }
    }

    override fun spoken(candidate: Grade4NumberCandidate): LocalizedText = visible(candidate)

    override fun hint(candidate: Grade4NumberCandidate): LocalizedText = LocalizedText(
        "Lees de schaal zorgvuldig en bereken de afstand tussen beide temperaturen.",
        "Lis attentivement l'échelle et calcule l'écart entre les températures.",
        "Read the scale carefully and calculate the distance between the temperatures."
    )
}

internal object Grade4StatisticsProblemSolvingGenerator : Grade4NumberGenerator(
    generatorId = "generator.g4.math.statistics.problem_solving",
    roadmapEntry = "roadmap.g4.math.statistics.problem_solving",
    idNamespace = "g4.math.statistics.problem_solving",
    skill = "statistics_problem_solving",
    domain = LearningDomain.MATH,
    kinds = listOf("read_table", "total", "difference", "missing", "pictogram", "combined", "chart_scale", "multi_step"),
    automatedScope = "table_graph_statistics_and_multistep_numeric_reasoning"
) {
    override fun create(kind: String, phase: String, random: StableLessonRandom): Grade4NumberCandidate {
        val maximum = if (phase == "guided") 30 else 60
        val a = random.nextInt(5, maximum)
        val b = random.nextInt(5, maximum)
        val c = random.nextInt(5, maximum)
        return when (kind) {
            "read_table" -> Grade4NumberCandidate(kind, b, listOf(a, b, c))
            "total" -> Grade4NumberCandidate(kind, a + b + c, listOf(a, b, c))
            "difference" -> Grade4NumberCandidate(kind, abs(a - b), listOf(a, b, c))
            "missing" -> Grade4NumberCandidate(kind, c, listOf(a, b, a + b + c))
            "pictogram" -> {
                val symbols = random.nextInt(2, 11)
                val value = listOf(2, 5, 10)[random.nextInt(3)]
                Grade4NumberCandidate(kind, symbols * value, listOf(symbols, value))
            }
            "combined" -> Grade4NumberCandidate(kind, a + c, listOf(a, b, c))
            "chart_scale" -> {
                val marks = random.nextInt(2, 11)
                val value = listOf(2, 5, 10)[random.nextInt(3)]
                Grade4NumberCandidate(kind, marks * value, listOf(marks, value))
            }
            else -> {
                val sold = a + b
                val returned = random.nextInt(1, minOf(sold, if (phase == "guided") 11 else 21))
                Grade4NumberCandidate(kind, sold - returned, listOf(a, b, returned))
            }
        }
    }

    override fun visible(candidate: Grade4NumberCandidate): LocalizedText {
        val v = candidate.values
        val tableNl = "Tabel: maandag ${v[0]}, dinsdag ${v[1]}, woensdag ${v.getOrElse(2) { 0 }}."
        val tableFr = "Tableau : lundi ${v[0]}, mardi ${v[1]}, mercredi ${v.getOrElse(2) { 0 }}."
        val tableEn = "Table: Monday ${v[0]}, Tuesday ${v[1]}, Wednesday ${v.getOrElse(2) { 0 }}."
        return when (candidate.kind) {
            "read_table" -> LocalizedText("$tableNl Hoeveel staan er bij dinsdag?", "$tableFr Combien y en a-t-il mardi ?", "$tableEn How many are shown for Tuesday?")
            "total" -> LocalizedText("$tableNl Hoeveel zijn het er in totaal?", "$tableFr Quel est le total ?", "$tableEn What is the total?")
            "difference" -> LocalizedText("$tableNl Hoeveel verschillen maandag en dinsdag?", "$tableFr Quelle est la différence entre lundi et mardi ?", "$tableEn What is the difference between Monday and Tuesday?")
            "missing" -> LocalizedText("Maandag ${v[0]}, dinsdag ${v[1]}, totaal ${v[2]}. Hoeveel horen bij woensdag?", "Lundi ${v[0]}, mardi ${v[1]}, total ${v[2]}. Combien correspondent à mercredi ?", "Monday ${v[0]}, Tuesday ${v[1]}, total ${v[2]}. How many belong to Wednesday?")
            "pictogram" -> LocalizedText("Een pictogram toont ${v[0]} symbolen. Elk symbool betekent ${v[1]}. Welke waarde stelt het pictogram voor?", "Un pictogramme montre ${v[0]} symboles. Chaque symbole vaut ${v[1]}. Quelle valeur représente-t-il ?", "A pictograph shows ${v[0]} symbols. Each symbol means ${v[1]}. What value does it represent?")
            "combined" -> LocalizedText("$tableNl Hoeveel zijn maandag en woensdag samen?", "$tableFr Combien y en a-t-il lundi et mercredi ensemble ?", "$tableEn How many are there on Monday and Wednesday altogether?")
            "chart_scale" -> LocalizedText("Een staafdiagram heeft ${v[0]} schaalstappen. Elke stap betekent ${v[1]}. Welke waarde bereikt de staaf?", "Un diagramme en barres compte ${v[0]} graduations. Chaque graduation vaut ${v[1]}. Quelle valeur atteint la barre ?", "A bar chart has ${v[0]} scale steps. Each step represents ${v[1]}. What value does the bar reach?")
            else -> LocalizedText("Een winkel verkoopt ${v[0]} boeken op maandag en ${v[1]} op dinsdag. ${v[2]} worden teruggebracht. Hoeveel blijven verkocht?", "Un magasin vend ${v[0]} livres lundi et ${v[1]} mardi. ${v[2]} sont rapportés. Combien restent vendus ?", "A shop sells ${v[0]} books on Monday and ${v[1]} on Tuesday. ${v[2]} are returned. How many remain sold?")
        }
    }

    override fun spoken(candidate: Grade4NumberCandidate): LocalizedText = visible(candidate)

    override fun hint(candidate: Grade4NumberCandidate): LocalizedText = LocalizedText(
        "Bepaal eerst welke gegevens nodig zijn en voer de bewerkingen stap voor stap uit.",
        "Repère d'abord les données nécessaires et effectue les opérations étape par étape.",
        "Identify the needed data first and work through the operations step by step."
    )
}
