package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

internal data class Grade6NumberCandidate(
    val kind: String,
    val answer: Int,
    val values: List<Int>,
    val unit: String = "",
) {
    fun signature(): String = "$kind:${values.joinToString(":")}:$answer:$unit"
}

internal abstract class Grade6NumberGenerator(
    final override val generatorId: String,
    private val roadmapEntry: String,
    private val idNamespace: String,
    private val skill: String,
    private val domain: LearningDomain,
    private val kinds: List<String>,
    private val automatedScope: String,
) : LessonExerciseGenerator {
    final override val generatorVersion: Int = 1
    private val allowedParameterKeys = setOf("grade", "roadmap_entry", "phase", "editorial_status")

    final override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["grade"] != "6") add("grade must be 6")
        if (parameters["roadmap_entry"] != roadmapEntry) add("roadmap_entry must be $roadmapEntry")
        if (parameters["phase"] !in setOf("guided", "checkpoint")) add("phase must be guided or checkpoint")
        if (parameters["editorial_status"] != LessonGeneratorRegistry.APPROVED_STATUS) {
            add("editorial_status must be approved")
        }
    }

    final override fun generate(
        seed: LessonGenerationSeed,
        exerciseCount: Int,
        parameters: Map<String, String>,
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
                level = SkillLevel.SIXTH_GRADE,
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
                    "hint_en-US" to hint(candidate).en.orEmpty(),
                ),
                answer = candidate.answer.toString(),
            )
        }
    }

    private fun uniqueCandidate(
        kind: String,
        phase: String,
        random: StableLessonRandom,
        used: MutableSet<String>,
    ): Grade6NumberCandidate {
        repeat(256) {
            val candidate = create(kind, phase, random)
            if (used.add(candidate.signature())) return candidate
        }
        error("Unable to create a unique Grade 6 exercise for $generatorId/$kind")
    }

    protected abstract fun create(kind: String, phase: String, random: StableLessonRandom): Grade6NumberCandidate
    protected abstract fun visible(candidate: Grade6NumberCandidate): LocalizedText
    protected open fun spoken(candidate: Grade6NumberCandidate): LocalizedText = visible(candidate)
    protected abstract fun hint(candidate: Grade6NumberCandidate): LocalizedText
}

internal object Grade6LargeNumbersRatiosPercentagesGenerator : Grade6NumberGenerator(
    generatorId = "generator.g6.math.large_numbers_ratios_percentages",
    roadmapEntry = "roadmap.g6.math.large_numbers_ratios_percentages",
    idNamespace = "g6.math.large_numbers_ratios_percentages",
    skill = "large_numbers_ratios_percentages",
    domain = LearningDomain.MATH,
    kinds = listOf(
        "place_value",
        "round_thousand",
        "percent_of",
        "percentage_rate",
        "ratio_missing",
        "fraction_percent",
        "decimal_percent",
        "large_operation",
    ),
    automatedScope = "large_numbers_ratios_percentages_and_exact_integer_operations",
) {
    override fun create(kind: String, phase: String, random: StableLessonRandom): Grade6NumberCandidate {
        val harder = phase == "checkpoint"
        return when (kind) {
            "place_value" -> {
                val digit = random.nextInt(1, 10)
                val power = listOf(1_000, 10_000, 100_000)[random.nextInt(3)]
                val lower = random.nextInt(0, power)
                val upper = random.nextInt(1, 9) * power * 10
                Grade6NumberCandidate(kind, digit * power, listOf(upper + digit * power + lower, digit, power))
            }
            "round_thousand" -> {
                val base = random.nextInt(10, if (harder) 950 else 500) * 1_000
                val remainder = random.nextInt(0, 1_000)
                val number = base + remainder
                Grade6NumberCandidate(kind, ((number + 500) / 1_000) * 1_000, listOf(number))
            }
            "percent_of" -> {
                val percent = listOf(5, 10, 20, 25, 40, 50, 75)[random.nextInt(7)]
                val factor = 100 / gcd(percent, 100)
                val quantity = factor * random.nextInt(2, if (harder) 101 else 51)
                Grade6NumberCandidate(kind, quantity * percent / 100, listOf(percent, quantity))
            }
            "percentage_rate" -> {
                val percent = listOf(10, 20, 25, 40, 50, 60, 75, 80)[random.nextInt(8)]
                val total = (100 / gcd(percent, 100)) * random.nextInt(2, if (harder) 51 else 31)
                val part = total * percent / 100
                Grade6NumberCandidate(kind, percent, listOf(part, total), "percent")
            }
            "ratio_missing" -> {
                val left = random.nextInt(2, 13)
                val right = random.nextInt(2, 13)
                val factor = random.nextInt(2, if (harder) 21 else 11)
                Grade6NumberCandidate(kind, right * factor, listOf(left, right, factor))
            }
            "fraction_percent" -> {
                val denominator = listOf(2, 4, 5, 10, 20)[random.nextInt(5)]
                val numerator = random.nextInt(1, denominator)
                Grade6NumberCandidate(kind, numerator * 100 / denominator, listOf(numerator, denominator), "percent")
            }
            "decimal_percent" -> {
                val hundredths = random.nextInt(1, 100)
                Grade6NumberCandidate(kind, hundredths, listOf(hundredths), "percent")
            }
            else -> {
                val divisor = random.nextInt(12, if (harder) 101 else 51)
                val quotient = random.nextInt(20, if (harder) 1_001 else 301)
                Grade6NumberCandidate(kind, quotient, listOf(divisor * quotient, divisor))
            }
        }
    }

    override fun visible(candidate: Grade6NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "place_value" -> LocalizedText(
                "Welke waarde heeft het cijfer ${v[1]} in het getal ${v[0]}?",
                "Quelle est la valeur du chiffre ${v[1]} dans le nombre ${v[0]} ?",
                "What is the value of digit ${v[1]} in the number ${v[0]}?",
            )
            "round_thousand" -> LocalizedText(
                "Rond ${v[0]} af op het dichtstbijzijnde duizendtal.",
                "Arrondis ${v[0]} au millier le plus proche.",
                "Round ${v[0]} to the nearest thousand.",
            )
            "percent_of" -> LocalizedText(
                "Hoeveel is ${v[0]}% van ${v[1]}?",
                "Combien vaut ${v[0]} % de ${v[1]} ?",
                "What is ${v[0]}% of ${v[1]}?",
            )
            "percentage_rate" -> LocalizedText(
                "${v[0]} van de ${v[1]} leerlingen kozen voor optie A. Welk percentage is dat?",
                "${v[0]} élèves sur ${v[1]} ont choisi l'option A. Quel pourcentage cela représente-t-il ?",
                "${v[0]} out of ${v[1]} pupils chose option A. What percentage is that?",
            )
            "ratio_missing" -> LocalizedText(
                "De verhouding is ${v[0]} : ${v[1]}. Als het eerste getal ${v[0] * v[2]} wordt, wat wordt het tweede?",
                "Le rapport est ${v[0]} : ${v[1]}. Si le premier nombre devient ${v[0] * v[2]}, que devient le second ?",
                "The ratio is ${v[0]}:${v[1]}. If the first number becomes ${v[0] * v[2]}, what does the second become?",
            )
            "fraction_percent" -> LocalizedText(
                "Hoeveel procent is ${v[0]}/${v[1]}?",
                "Quel pourcentage représente ${v[0]}/${v[1]} ?",
                "What percentage is ${v[0]}/${v[1]}?",
            )
            "decimal_percent" -> LocalizedText(
                "Schrijf 0,${v[0].toString().padStart(2, '0')} als een percentage.",
                "Écris 0,${v[0].toString().padStart(2, '0')} sous forme de pourcentage.",
                "Write 0.${v[0].toString().padStart(2, '0')} as a percentage.",
            )
            else -> LocalizedText(
                "${v[0]} : ${v[1]} = ?",
                "${v[0]} : ${v[1]} = ?",
                "${v[0]} ÷ ${v[1]} = ?",
            )
        }
    }

    override fun spoken(candidate: Grade6NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "percent_of" -> LocalizedText("Hoeveel is ${v[0]} procent van ${v[1]}?", "Combien vaut ${v[0]} pour cent de ${v[1]} ?", "What is ${v[0]} percent of ${v[1]}?")
            "fraction_percent" -> LocalizedText("Hoeveel procent is ${v[0]} gedeeld door ${v[1]}?", "Quel pourcentage représente la fraction ${v[0]} sur ${v[1]} ?", "What percentage is the fraction ${v[0]} over ${v[1]}?")
            "large_operation" -> LocalizedText("${v[0]} gedeeld door ${v[1]} is hoeveel?", "${v[0]} divisé par ${v[1]} vaut combien ?", "What is ${v[0]} divided by ${v[1]}?")
            else -> visible(candidate)
        }
    }

    override fun hint(candidate: Grade6NumberCandidate) = LocalizedText(
        "Zet verhoudingen en percentages om naar gelijke delen en controleer de plaatswaarde.",
        "Transforme les rapports et pourcentages en parts équivalentes et vérifie la valeur de position.",
        "Convert ratios and percentages into equivalent parts and check place value.",
    )

    private fun gcd(first: Int, second: Int): Int {
        var a = first
        var b = second
        while (b != 0) {
            val remainder = a % b
            a = b
            b = remainder
        }
        return a
    }
}

internal object Grade6CompoundMeasurementGenerator : Grade6NumberGenerator(
    generatorId = "generator.g6.geometry.scale.compound_measurement",
    roadmapEntry = "roadmap.g6.geometry.scale.compound_measurement",
    idNamespace = "g6.geometry.scale.compound_measurement",
    skill = "scale_compound_measurement",
    domain = LearningDomain.GEOMETRY,
    kinds = listOf(
        "map_scale",
        "model_scale",
        "rectangle_perimeter",
        "rectangle_area",
        "cuboid_volume",
        "length_conversion",
        "distance_speed",
        "missing_side",
    ),
    automatedScope = "scale_perimeter_area_volume_conversion_and_rate_with_integer_entry",
) {
    override fun create(kind: String, phase: String, random: StableLessonRandom): Grade6NumberCandidate {
        val harder = phase == "checkpoint"
        return when (kind) {
            "map_scale" -> {
                val scaleKm = random.nextInt(2, if (harder) 21 else 11)
                val mapCm = random.nextInt(2, if (harder) 16 else 9)
                Grade6NumberCandidate(kind, scaleKm * mapCm, listOf(scaleKm, mapCm), "km")
            }
            "model_scale" -> {
                val scale = listOf(10, 20, 25, 50, 100)[random.nextInt(5)]
                val modelCm = random.nextInt(2, if (harder) 51 else 21)
                Grade6NumberCandidate(kind, scale * modelCm, listOf(scale, modelCm), "cm")
            }
            "rectangle_perimeter" -> {
                val length = random.nextInt(8, if (harder) 101 else 51)
                val width = random.nextInt(4, length)
                Grade6NumberCandidate(kind, 2 * (length + width), listOf(length, width), "m")
            }
            "rectangle_area" -> {
                val length = random.nextInt(5, if (harder) 51 else 26)
                val width = random.nextInt(3, if (harder) 31 else 16)
                Grade6NumberCandidate(kind, length * width, listOf(length, width), "m2")
            }
            "cuboid_volume" -> {
                val length = random.nextInt(2, if (harder) 16 else 9)
                val width = random.nextInt(2, if (harder) 11 else 7)
                val height = random.nextInt(2, if (harder) 9 else 6)
                Grade6NumberCandidate(kind, length * width * height, listOf(length, width, height), "cm3")
            }
            "length_conversion" -> {
                val metres = random.nextInt(1, if (harder) 101 else 41)
                val centimetres = random.nextInt(0, 100)
                Grade6NumberCandidate(kind, metres * 100 + centimetres, listOf(metres, centimetres), "cm")
            }
            "distance_speed" -> {
                val speed = random.nextInt(4, if (harder) 31 else 16)
                val time = random.nextInt(2, if (harder) 9 else 6)
                Grade6NumberCandidate(kind, speed * time, listOf(speed, time), "km")
            }
            else -> {
                val length = random.nextInt(10, if (harder) 101 else 51)
                val width = random.nextInt(4, length)
                val perimeter = 2 * (length + width)
                Grade6NumberCandidate(kind, width, listOf(perimeter, length), "m")
            }
        }
    }

    override fun visible(candidate: Grade6NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "map_scale" -> LocalizedText(
                "Op een kaart stelt 1 cm ${v[0]} km voor. Twee plaatsen liggen ${v[1]} cm uit elkaar. Hoeveel kilometer is dat?",
                "Sur une carte, 1 cm représente ${v[0]} km. Deux lieux sont séparés de ${v[1]} cm. Combien de kilomètres cela représente-t-il ?",
                "On a map, 1 cm represents ${v[0]} km. Two places are ${v[1]} cm apart. How many kilometres is that?",
            )
            "model_scale" -> LocalizedText(
                "Een model op schaal 1:${v[0]} is ${v[1]} cm lang. Hoeveel centimeter is het echte voorwerp?",
                "Une maquette à l'échelle 1:${v[0]} mesure ${v[1]} cm. Combien de centimètres mesure l'objet réel ?",
                "A 1:${v[0]} scale model is ${v[1]} cm long. How many centimetres is the real object?",
            )
            "rectangle_perimeter" -> LocalizedText(
                "Een rechthoek is ${v[0]} m lang en ${v[1]} m breed. Wat is de omtrek in meter?",
                "Un rectangle mesure ${v[0]} m sur ${v[1]} m. Quel est son périmètre en mètres ?",
                "A rectangle is ${v[0]} m long and ${v[1]} m wide. What is its perimeter in metres?",
            )
            "rectangle_area" -> LocalizedText(
                "Een rechthoek is ${v[0]} m lang en ${v[1]} m breed. Wat is de oppervlakte in m²?",
                "Un rectangle mesure ${v[0]} m sur ${v[1]} m. Quelle est son aire en m² ?",
                "A rectangle is ${v[0]} m long and ${v[1]} m wide. What is its area in m²?",
            )
            "cuboid_volume" -> LocalizedText(
                "Een balk is ${v[0]} cm lang, ${v[1]} cm breed en ${v[2]} cm hoog. Wat is de inhoud in cm³?",
                "Un pavé mesure ${v[0]} cm de long, ${v[1]} cm de large et ${v[2]} cm de haut. Quel est son volume en cm³ ?",
                "A cuboid is ${v[0]} cm long, ${v[1]} cm wide, and ${v[2]} cm high. What is its volume in cm³?",
            )
            "length_conversion" -> LocalizedText(
                "Hoeveel centimeter is ${v[0]} m en ${v[1]} cm samen?",
                "Combien de centimètres représentent ${v[0]} m et ${v[1]} cm ?",
                "How many centimetres are ${v[0]} m and ${v[1]} cm altogether?",
            )
            "distance_speed" -> LocalizedText(
                "Een fietser rijdt ${v[0]} km per uur gedurende ${v[1]} uur. Welke afstand legt de fietser af in kilometer?",
                "Un cycliste roule à ${v[0]} km par heure pendant ${v[1]} heures. Quelle distance parcourt-il en kilomètres ?",
                "A cyclist rides at ${v[0]} km per hour for ${v[1]} hours. What distance is covered in kilometres?",
            )
            else -> LocalizedText(
                "Een rechthoek heeft een omtrek van ${v[0]} m en is ${v[1]} m lang. Hoe breed is hij in meter?",
                "Un rectangle a un périmètre de ${v[0]} m et une longueur de ${v[1]} m. Quelle est sa largeur en mètres ?",
                "A rectangle has a perimeter of ${v[0]} m and is ${v[1]} m long. What is its width in metres?",
            )
        }
    }

    override fun spoken(candidate: Grade6NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "rectangle_area" -> LocalizedText("Een rechthoek is ${v[0]} meter lang en ${v[1]} meter breed. Wat is de oppervlakte in vierkante meter?", "Un rectangle mesure ${v[0]} mètres sur ${v[1]} mètres. Quelle est son aire en mètres carrés ?", "A rectangle is ${v[0]} metres long and ${v[1]} metres wide. What is its area in square metres?")
            "cuboid_volume" -> LocalizedText("Een balk is ${v[0]} centimeter lang, ${v[1]} centimeter breed en ${v[2]} centimeter hoog. Wat is de inhoud in kubieke centimeter?", "Un pavé mesure ${v[0]} centimètres de long, ${v[1]} de large et ${v[2]} de haut. Quel est son volume en centimètres cubes ?", "A cuboid is ${v[0]} centimetres long, ${v[1]} wide, and ${v[2]} high. What is its volume in cubic centimetres?")
            else -> visible(candidate)
        }
    }

    override fun hint(candidate: Grade6NumberCandidate) = LocalizedText(
        "Schrijf de schaal of formule op, zet alle maten in dezelfde eenheid en reken stap voor stap.",
        "Note l'échelle ou la formule, convertis toutes les mesures dans la même unité et calcule étape par étape.",
        "Write down the scale or formula, convert all measures to the same unit, and calculate step by step.",
    )
}

internal object Grade6StatisticsMeanMedianGenerator : Grade6NumberGenerator(
    generatorId = "generator.g6.math.statistics.mean_median",
    roadmapEntry = "roadmap.g6.math.statistics.mean_median",
    idNamespace = "g6.math.statistics.mean_median",
    skill = "statistics_mean_median",
    domain = LearningDomain.MATH,
    kinds = listOf(
        "mean",
        "median_odd",
        "median_even",
        "range",
        "total_from_mean",
        "missing_from_mean",
        "pictogram_total",
        "mean_difference",
    ),
    automatedScope = "mean_median_range_and_table_calculation_with_integer_entry",
) {
    override fun create(kind: String, phase: String, random: StableLessonRandom): Grade6NumberCandidate {
        val harder = phase == "checkpoint"
        return when (kind) {
            "mean" -> {
                val mean = random.nextInt(5, if (harder) 51 else 31)
                val a = mean - random.nextInt(1, mean.coerceAtMost(8))
                val b = mean + random.nextInt(1, if (harder) 10 else 6)
                val c = 3 * mean - a - b
                Grade6NumberCandidate(kind, mean, listOf(a, b, c))
            }
            "median_odd" -> {
                val values = List(5) { random.nextInt(1, if (harder) 101 else 51) }.sorted()
                Grade6NumberCandidate(kind, values[2], values)
            }
            "median_even" -> {
                val middle = random.nextInt(5, if (harder) 61 else 31)
                val gap = random.nextInt(1, middle.coerceAtMost(8))
                val values = listOf(
                    random.nextInt(1, (middle - gap).coerceAtLeast(2)),
                    middle - gap,
                    middle + gap,
                    middle + gap + random.nextInt(1, 10),
                ).sorted()
                Grade6NumberCandidate(kind, middle, values)
            }
            "range" -> {
                val minimum = random.nextInt(1, if (harder) 51 else 21)
                val range = random.nextInt(5, if (harder) 81 else 41)
                val maximum = minimum + range
                val middle1 = random.nextInt(minimum, maximum + 1)
                val middle2 = random.nextInt(minimum, maximum + 1)
                Grade6NumberCandidate(kind, range, listOf(minimum, middle1, middle2, maximum).shuffledBy(random))
            }
            "total_from_mean" -> {
                val count = random.nextInt(3, if (harder) 13 else 9)
                val mean = random.nextInt(4, if (harder) 51 else 31)
                Grade6NumberCandidate(kind, count * mean, listOf(count, mean))
            }
            "missing_from_mean" -> {
                val mean = random.nextInt(8, if (harder) 51 else 31)
                val first = random.nextInt(1, mean)
                val second = random.nextInt(1, mean)
                val missing = 3 * mean - first - second
                Grade6NumberCandidate(kind, missing, listOf(first, second, mean))
            }
            "pictogram_total" -> {
                val scale = random.nextInt(2, if (harder) 11 else 7)
                val first = random.nextInt(2, if (harder) 13 else 9)
                val second = random.nextInt(2, if (harder) 13 else 9)
                Grade6NumberCandidate(kind, scale * (first + second), listOf(scale, first, second))
            }
            else -> {
                val firstMean = random.nextInt(5, if (harder) 61 else 31)
                val difference = random.nextInt(2, if (harder) 21 else 11)
                Grade6NumberCandidate(kind, difference, listOf(firstMean, firstMean + difference))
            }
        }
    }

    override fun visible(candidate: Grade6NumberCandidate): LocalizedText {
        val v = candidate.values
        val values = v.joinToString(", ")
        return when (candidate.kind) {
            "mean" -> LocalizedText("Wat is het gemiddelde van $values?", "Quelle est la moyenne de $values ?", "What is the mean of $values?")
            "median_odd", "median_even" -> LocalizedText("Wat is de mediaan van $values?", "Quelle est la médiane de $values ?", "What is the median of $values?")
            "range" -> LocalizedText("Wat is het verschil tussen de grootste en de kleinste waarde in $values?", "Quelle est l'étendue, la différence entre la plus grande et la plus petite valeur, de $values ?", "What is the range, the difference between the largest and smallest value, of $values?")
            "total_from_mean" -> LocalizedText(
                "${v[0]} waarden hebben een gemiddelde van ${v[1]}. Wat is hun totaal?",
                "${v[0]} valeurs ont une moyenne de ${v[1]}. Quel est leur total ?",
                "${v[0]} values have a mean of ${v[1]}. What is their total?",
            )
            "missing_from_mean" -> LocalizedText(
                "De waarden ${v[0]}, ${v[1]} en ? hebben een gemiddelde van ${v[2]}. Welke waarde ontbreekt?",
                "Les valeurs ${v[0]}, ${v[1]} et ? ont une moyenne de ${v[2]}. Quelle valeur manque ?",
                "The values ${v[0]}, ${v[1]}, and ? have a mean of ${v[2]}. Which value is missing?",
            )
            "pictogram_total" -> LocalizedText(
                "In een pictogram stelt elk symbool ${v[0]} voor. Rij A heeft ${v[1]} symbolen en rij B ${v[2]}. Hoeveel stellen beide rijen samen voor?",
                "Dans un pictogramme, chaque symbole vaut ${v[0]}. La ligne A compte ${v[1]} symboles et la ligne B ${v[2]}. Quel est le total ?",
                "In a pictogram, each symbol represents ${v[0]}. Row A has ${v[1]} symbols and row B has ${v[2]}. What is the total?",
            )
            else -> LocalizedText(
                "Groep A heeft een gemiddelde van ${v[0]} en groep B van ${v[1]}. Hoeveel verschillen de gemiddelden?",
                "Le groupe A a une moyenne de ${v[0]} et le groupe B de ${v[1]}. Quelle est la différence entre les moyennes ?",
                "Group A has a mean of ${v[0]} and group B has a mean of ${v[1]}. What is the difference between the means?",
            )
        }
    }

    override fun hint(candidate: Grade6NumberCandidate) = LocalizedText(
        "Sorteer voor de mediaan; tel op en deel door het aantal voor het gemiddelde.",
        "Trie les valeurs pour la médiane ; additionne et divise par le nombre de valeurs pour la moyenne.",
        "Sort for the median; add and divide by the number of values for the mean.",
    )

    private fun <T> List<T>.shuffledBy(random: StableLessonRandom): List<T> {
        val result = toMutableList()
        for (index in result.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val value = result[index]
            result[index] = result[other]
            result[other] = value
        }
        return result
    }
}

internal object Grade6MultiStepProblemSolvingGenerator : Grade6NumberGenerator(
    generatorId = "generator.g6.math.problem_solving.multi_step",
    roadmapEntry = "roadmap.g6.math.problem_solving.multi_step",
    idNamespace = "g6.math.problem_solving.multi_step",
    skill = "multi_step_problem_solving",
    domain = LearningDomain.MATH,
    kinds = listOf(
        "discount",
        "recipe_scale",
        "journey_remaining",
        "rate_total",
        "budget_percent",
        "tile_area",
        "combined_groups",
        "compare_plans",
    ),
    automatedScope = "multi_step_whole_number_models_rates_percentages_and_geometry",
) {
    override fun create(kind: String, phase: String, random: StableLessonRandom): Grade6NumberCandidate {
        val harder = phase == "checkpoint"
        return when (kind) {
            "discount" -> {
                val discount = listOf(10, 20, 25, 50)[random.nextInt(4)]
                val factor = 100 / gcd(discount, 100)
                val price = factor * random.nextInt(5, if (harder) 101 else 51)
                Grade6NumberCandidate(kind, price * (100 - discount) / 100, listOf(price, discount), "euro")
            }
            "recipe_scale" -> {
                val basePersons = random.nextInt(2, 7)
                val factor = random.nextInt(2, if (harder) 7 else 5)
                val baseAmount = random.nextInt(2, if (harder) 21 else 11) * 25
                Grade6NumberCandidate(kind, baseAmount * factor, listOf(baseAmount, basePersons, basePersons * factor), "g")
            }
            "journey_remaining" -> {
                val total = random.nextInt(20, if (harder) 201 else 101)
                val first = random.nextInt(5, total)
                val second = random.nextInt(1, total - first + 1)
                Grade6NumberCandidate(kind, total - first - second, listOf(total, first, second), "km")
            }
            "rate_total" -> {
                val hourly = random.nextInt(8, if (harder) 41 else 26)
                val hours = random.nextInt(2, if (harder) 13 else 8)
                val fixed = random.nextInt(5, if (harder) 51 else 26)
                Grade6NumberCandidate(kind, hourly * hours + fixed, listOf(hourly, hours, fixed), "euro")
            }
            "budget_percent" -> {
                val percent = listOf(10, 20, 25, 40, 50)[random.nextInt(5)]
                val factor = 100 / gcd(percent, 100)
                val budget = factor * random.nextInt(10, if (harder) 101 else 51)
                Grade6NumberCandidate(kind, budget * (100 - percent) / 100, listOf(budget, percent), "euro")
            }
            "tile_area" -> {
                val length = random.nextInt(4, if (harder) 21 else 13)
                val width = random.nextInt(3, if (harder) 16 else 9)
                val tilesPerSquareMetre = random.nextInt(4, if (harder) 17 else 10)
                Grade6NumberCandidate(kind, length * width * tilesPerSquareMetre, listOf(length, width, tilesPerSquareMetre), "tiles")
            }
            "combined_groups" -> {
                val firstCount = random.nextInt(2, if (harder) 11 else 7)
                val firstSize = random.nextInt(5, if (harder) 31 else 16)
                val secondCount = random.nextInt(2, if (harder) 11 else 7)
                val secondSize = random.nextInt(5, if (harder) 31 else 16)
                Grade6NumberCandidate(kind, firstCount * firstSize + secondCount * secondSize, listOf(firstCount, firstSize, secondCount, secondSize))
            }
            else -> {
                val days = random.nextInt(3, if (harder) 15 else 9)
                val dailyA = random.nextInt(5, if (harder) 41 else 21)
                val fixedA = random.nextInt(10, if (harder) 101 else 51)
                val dailyB = dailyA + random.nextInt(1, 8)
                val fixedB = (fixedA + days * (dailyA - dailyB)).coerceAtLeast(0)
                val totalA = fixedA + days * dailyA
                val totalB = fixedB + days * dailyB
                Grade6NumberCandidate(kind, kotlin.math.abs(totalA - totalB), listOf(days, fixedA, dailyA, fixedB, dailyB), "euro")
            }
        }
    }

    override fun visible(candidate: Grade6NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "discount" -> LocalizedText(
                "Een artikel kost €${v[0]}. Er is ${v[1]}% korting. Hoeveel euro betaal je?",
                "Un article coûte ${v[0]} €. Il y a ${v[1]} % de réduction. Combien d'euros paies-tu ?",
                "An item costs €${v[0]}. It has a ${v[1]}% discount. How many euros do you pay?",
            )
            "recipe_scale" -> LocalizedText(
                "Voor ${v[1]} personen heb je ${v[0]} g nodig. Hoeveel gram heb je nodig voor ${v[2]} personen?",
                "Il faut ${v[0]} g pour ${v[1]} personnes. Combien de grammes faut-il pour ${v[2]} personnes ?",
                "You need ${v[0]} g for ${v[1]} people. How many grams are needed for ${v[2]} people?",
            )
            "journey_remaining" -> LocalizedText(
                "Een route is ${v[0]} km lang. Je legt eerst ${v[1]} km en daarna ${v[2]} km af. Hoeveel kilometer blijft over?",
                "Un trajet mesure ${v[0]} km. Tu parcours d'abord ${v[1]} km puis ${v[2]} km. Combien de kilomètres restent-ils ?",
                "A route is ${v[0]} km long. You travel ${v[1]} km and then ${v[2]} km. How many kilometres remain?",
            )
            "rate_total" -> LocalizedText(
                "Een huur kost €${v[0]} per uur plus €${v[2]} vast. Wat kost ${v[1]} uur in euro?",
                "Une location coûte ${v[0]} € par heure plus ${v[2]} € fixes. Combien coûtent ${v[1]} heures ?",
                "A rental costs €${v[0]} per hour plus a fixed €${v[2]}. How many euros do ${v[1]} hours cost?",
            )
            "budget_percent" -> LocalizedText(
                "Van een budget van €${v[0]} wordt ${v[1]}% gebruikt. Hoeveel euro blijft over?",
                "On utilise ${v[1]} % d'un budget de ${v[0]} €. Combien d'euros restent-ils ?",
                "${v[1]}% of a €${v[0]} budget is used. How many euros remain?",
            )
            "tile_area" -> LocalizedText(
                "Een vloer is ${v[0]} m bij ${v[1]} m. Per m² zijn ${v[2]} tegels nodig. Hoeveel tegels zijn nodig?",
                "Un sol mesure ${v[0]} m sur ${v[1]} m. Il faut ${v[2]} carreaux par m². Combien de carreaux faut-il ?",
                "A floor is ${v[0]} m by ${v[1]} m. ${v[2]} tiles are needed per m². How many tiles are needed?",
            )
            "combined_groups" -> LocalizedText(
                "Er zijn ${v[0]} groepen van ${v[1]} en ${v[2]} groepen van ${v[3]}. Hoeveel personen zijn er samen?",
                "Il y a ${v[0]} groupes de ${v[1]} et ${v[2]} groupes de ${v[3]}. Combien de personnes y a-t-il en tout ?",
                "There are ${v[0]} groups of ${v[1]} and ${v[2]} groups of ${v[3]}. How many people are there altogether?",
            )
            else -> LocalizedText(
                "Plan A kost €${v[1]} vast en €${v[2]} per dag. Plan B kost €${v[3]} vast en €${v[4]} per dag. Hoeveel euro verschillen ze na ${v[0]} dagen?",
                "Le plan A coûte ${v[1]} € fixes et ${v[2]} € par jour. Le plan B coûte ${v[3]} € fixes et ${v[4]} € par jour. Quelle est la différence après ${v[0]} jours ?",
                "Plan A costs a fixed €${v[1]} plus €${v[2]} per day. Plan B costs a fixed €${v[3]} plus €${v[4]} per day. How many euros apart are they after ${v[0]} days?",
            )
        }
    }

    override fun spoken(candidate: Grade6NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "tile_area" -> LocalizedText("Een vloer is ${v[0]} meter bij ${v[1]} meter. Per vierkante meter zijn ${v[2]} tegels nodig. Hoeveel tegels zijn nodig?", "Un sol mesure ${v[0]} mètres sur ${v[1]} mètres. Il faut ${v[2]} carreaux par mètre carré. Combien de carreaux faut-il ?", "A floor is ${v[0]} metres by ${v[1]} metres. ${v[2]} tiles are needed per square metre. How many tiles are needed?")
            else -> visible(candidate)
        }
    }

    override fun hint(candidate: Grade6NumberCandidate) = LocalizedText(
        "Schrijf de tussenstappen op, controleer de eenheden en schat of je antwoord redelijk is.",
        "Note les étapes intermédiaires, vérifie les unités et estime si ta réponse est raisonnable.",
        "Write down the intermediate steps, check the units, and estimate whether your answer is reasonable.",
    )

    private fun gcd(first: Int, second: Int): Int {
        var a = first
        var b = second
        while (b != 0) {
            val remainder = a % b
            a = b
            b = remainder
        }
        return a
    }
}
