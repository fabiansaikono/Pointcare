package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

internal data class Grade5NumberCandidate(
    val kind: String,
    val answer: Int,
    val values: List<Int>,
    val unit: String = ""
) {
    fun signature(): String = "$kind:${values.joinToString(":")}:$answer:$unit"
}

internal abstract class Grade5NumberGenerator(
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
        if (parameters["grade"] != "5") add("grade must be 5")
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
                level = SkillLevel.FIFTH_GRADE,
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
    ): Grade5NumberCandidate {
        repeat(256) {
            val candidate = create(kind, phase, random)
            if (used.add(candidate.signature())) return candidate
        }
        error("Unable to create a unique Grade 5 exercise for $generatorId/$kind")
    }

    protected abstract fun create(kind: String, phase: String, random: StableLessonRandom): Grade5NumberCandidate
    protected abstract fun visible(candidate: Grade5NumberCandidate): LocalizedText
    protected abstract fun spoken(candidate: Grade5NumberCandidate): LocalizedText
    protected abstract fun hint(candidate: Grade5NumberCandidate): LocalizedText
}

internal object Grade5FractionsDecimalsRatiosGenerator : Grade5NumberGenerator(
    generatorId = "generator.g5.math.fractions_decimals_ratios",
    roadmapEntry = "roadmap.g5.math.fractions_decimals_ratios",
    idNamespace = "g5.math.fractions_decimals_ratios",
    skill = "fractions_decimals_ratios",
    domain = LearningDomain.MATH,
    kinds = listOf(
        "fraction_of",
        "equivalent_numerator",
        "equivalent_denominator",
        "simplify_denominator",
        "decimal_tenths",
        "decimal_hundredths",
        "ratio_missing",
        "decimal_sum_cents"
    ),
    automatedScope = "fractions_decimals_and_simple_ratios_with_integer_entry"
) {
    override fun create(kind: String, phase: String, random: StableLessonRandom): Grade5NumberCandidate {
        val harder = phase == "checkpoint"
        return when (kind) {
            "fraction_of" -> {
                val denominator = listOf(2, 3, 4, 5, 8, 10)[random.nextInt(6)]
                val numerator = random.nextInt(1, denominator)
                val groups = random.nextInt(2, if (harder) 13 else 8)
                val quantity = denominator * groups
                Grade5NumberCandidate(kind, quantity / denominator * numerator, listOf(numerator, denominator, quantity))
            }
            "equivalent_numerator" -> {
                val denominator = listOf(3, 4, 5, 6, 8)[random.nextInt(5)]
                val numerator = random.nextInt(1, denominator)
                val factor = random.nextInt(2, if (harder) 8 else 5)
                Grade5NumberCandidate(kind, numerator * factor, listOf(numerator, denominator, factor))
            }
            "equivalent_denominator" -> {
                val denominator = listOf(3, 4, 5, 6, 8)[random.nextInt(5)]
                val numerator = random.nextInt(1, denominator)
                val factor = random.nextInt(2, if (harder) 8 else 5)
                Grade5NumberCandidate(kind, denominator * factor, listOf(numerator, denominator, factor))
            }
            "simplify_denominator" -> {
                val simpleDenominator = listOf(2, 3, 4, 5)[random.nextInt(4)]
                val simpleNumerator = random.nextInt(1, simpleDenominator)
                val factor = random.nextInt(2, if (harder) 7 else 5)
                Grade5NumberCandidate(
                    kind,
                    simpleDenominator,
                    listOf(simpleNumerator * factor, simpleDenominator * factor, simpleNumerator)
                )
            }
            "decimal_tenths" -> {
                val whole = random.nextInt(0, if (harder) 21 else 11)
                val tenths = random.nextInt(1, 10)
                Grade5NumberCandidate(kind, whole * 10 + tenths, listOf(whole, tenths), "tenths")
            }
            "decimal_hundredths" -> {
                val whole = random.nextInt(0, if (harder) 11 else 6)
                val hundredths = random.nextInt(1, 100)
                Grade5NumberCandidate(kind, whole * 100 + hundredths, listOf(whole, hundredths), "hundredths")
            }
            "ratio_missing" -> {
                val left = random.nextInt(1, 8)
                val right = random.nextInt(1, 8)
                val factor = random.nextInt(2, if (harder) 11 else 7)
                Grade5NumberCandidate(kind, right * factor, listOf(left, right, factor))
            }
            else -> {
                val first = random.nextInt(25, if (harder) 1_001 else 501)
                val second = random.nextInt(25, if (harder) 1_001 else 501)
                Grade5NumberCandidate(kind, first + second, listOf(first, second), "cent")
            }
        }
    }

    override fun visible(candidate: Grade5NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "fraction_of" -> LocalizedText("Hoeveel is ${v[0]}/${v[1]} van ${v[2]}?", "Combien vaut ${v[0]}/${v[1]} de ${v[2]} ?", "What is ${v[0]}/${v[1]} of ${v[2]}?")
            "equivalent_numerator" -> LocalizedText("${v[0]}/${v[1]} = ?/${v[1] * v[2]}. Welk getal komt op de plaats van het vraagteken?", "${v[0]}/${v[1]} = ?/${v[1] * v[2]}. Quel nombre remplace le point d'interrogation ?", "${v[0]}/${v[1]} = ?/${v[1] * v[2]}. What number replaces the question mark?")
            "equivalent_denominator" -> LocalizedText("${v[0]}/${v[1]} = ${v[0] * v[2]}/?. Welke noemer ontbreekt?", "${v[0]}/${v[1]} = ${v[0] * v[2]}/?. Quel dénominateur manque ?", "${v[0]}/${v[1]} = ${v[0] * v[2]}/?. Which denominator is missing?")
            "simplify_denominator" -> LocalizedText("Vereenvoudig ${v[0]}/${v[1]} tot ${v[2]}/?. Welke noemer ontbreekt?", "Simplifie ${v[0]}/${v[1]} en ${v[2]}/?. Quel dénominateur manque ?", "Simplify ${v[0]}/${v[1]} to ${v[2]}/?. Which denominator is missing?")
            "decimal_tenths" -> LocalizedText("Het getal ${v[0]},${v[1]} bestaat uit hoeveel tienden?", "Le nombre ${v[0]},${v[1]} contient combien de dixièmes ?", "How many tenths are in ${v[0]}.${v[1]}?")
            "decimal_hundredths" -> LocalizedText("Het getal ${v[0]},${v[1].toString().padStart(2, '0')} bestaat uit hoeveel honderdsten?", "Le nombre ${v[0]},${v[1].toString().padStart(2, '0')} contient combien de centièmes ?", "How many hundredths are in ${v[0]}.${v[1].toString().padStart(2, '0')}?")
            "ratio_missing" -> LocalizedText("De verhouding is ${v[0]} : ${v[1]}. Als het eerste getal ${v[0] * v[2]} wordt, wat wordt het tweede?", "Le rapport est ${v[0]} : ${v[1]}. Si le premier nombre devient ${v[0] * v[2]}, que devient le second ?", "The ratio is ${v[0]}:${v[1]}. If the first number becomes ${v[0] * v[2]}, what does the second become?")
            else -> LocalizedText("Een boek kost ${eurosNl(v[0])} en een schrift ${eurosNl(v[1])}. Hoeveel cent kosten ze samen?", "Un livre coûte ${eurosFr(v[0])} et un cahier ${eurosFr(v[1])}. Combien de centimes coûtent-ils ensemble ?", "A book costs ${eurosEn(v[0])} and a notebook ${eurosEn(v[1])}. How many cents do they cost altogether?")
        }
    }

    override fun spoken(candidate: Grade5NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "fraction_of" -> LocalizedText("Hoeveel is ${fractionNl(v[0], v[1])} van ${v[2]}?", "Combien vaut la fraction ${v[0]} sur ${v[1]} de ${v[2]} ?", "What is the fraction ${v[0]} over ${v[1]} of ${v[2]}?")
            "equivalent_numerator" -> LocalizedText("${fractionNl(v[0], v[1])} is gelijk aan hoeveel ${fractionPartNl(v[1] * v[2])}?", "La fraction ${v[0]} sur ${v[1]} équivaut à combien sur ${v[1] * v[2]} ?", "The fraction ${v[0]} over ${v[1]} equals how many over ${v[1] * v[2]}?")
            "equivalent_denominator" -> LocalizedText("${fractionNl(v[0], v[1])} is gelijk aan ${v[0] * v[2]} gedeeld door hoeveel?", "La fraction ${v[0]} sur ${v[1]} équivaut à ${v[0] * v[2]} sur combien ?", "The fraction ${v[0]} over ${v[1]} equals ${v[0] * v[2]} over what?")
            else -> visible(candidate)
        }
    }

    override fun hint(candidate: Grade5NumberCandidate): LocalizedText = LocalizedText(
        "Zet breuken of kommagetallen eerst om naar gelijke delen en reken daarna.",
        "Transforme d'abord les fractions ou les nombres décimaux en parts comparables, puis calcule.",
        "First express the fractions or decimals as comparable parts, then calculate."
    )

    private fun eurosNl(cents: Int) = "€ ${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
    private fun eurosFr(cents: Int) = "${cents / 100},${(cents % 100).toString().padStart(2, '0')} €"
    private fun eurosEn(cents: Int) = "€${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    private fun fractionNl(numerator: Int, denominator: Int) = "$numerator gedeeld door $denominator"
    private fun fractionPartNl(denominator: Int) = "delen met noemer $denominator"
}

internal object Grade5ProblemSolvingModelStrategiesGenerator : Grade5NumberGenerator(
    generatorId = "generator.g5.math.problem_solving.model_strategies",
    roadmapEntry = "roadmap.g5.math.problem_solving.model_strategies",
    idNamespace = "g5.math.problem_solving.model_strategies",
    skill = "problem_solving_model_strategies",
    domain = LearningDomain.MATH,
    kinds = listOf(
        "total_then_share",
        "budget_change",
        "unit_price",
        "remaining",
        "area_cost",
        "schedule_total",
        "compare_plans",
        "equal_share"
    ),
    automatedScope = "multi_step_model_selection_and_integer_problem_solving"
) {
    override fun create(kind: String, phase: String, random: StableLessonRandom): Grade5NumberCandidate {
        val harder = phase == "checkpoint"
        return when (kind) {
            "total_then_share" -> {
                val groups = random.nextInt(2, if (harder) 11 else 7)
                val perGroup = random.nextInt(3, if (harder) 31 else 16)
                val first = groups * random.nextInt(1, perGroup)
                val second = groups * perGroup - first
                Grade5NumberCandidate(kind, perGroup, listOf(first, second, groups))
            }
            "budget_change" -> {
                val first = random.nextInt(100, if (harder) 2_001 else 1_001)
                val second = random.nextInt(100, if (harder) 2_001 else 1_001)
                val change = random.nextInt(100, if (harder) 1_001 else 501)
                Grade5NumberCandidate(kind, change, listOf(first + second + change, first, second), "cent")
            }
            "unit_price" -> {
                val quantity = random.nextInt(2, if (harder) 13 else 9)
                val unitPrice = random.nextInt(15, if (harder) 251 else 151)
                Grade5NumberCandidate(kind, quantity * unitPrice, listOf(quantity, unitPrice), "cent")
            }
            "remaining" -> {
                val total = random.nextInt(100, if (harder) 2_001 else 1_001)
                val used = random.nextInt(25, total)
                Grade5NumberCandidate(kind, total - used, listOf(total, used))
            }
            "area_cost" -> {
                val length = random.nextInt(2, if (harder) 16 else 11)
                val width = random.nextInt(2, if (harder) 13 else 9)
                val cost = random.nextInt(2, if (harder) 11 else 7)
                Grade5NumberCandidate(kind, length * width * cost, listOf(length, width, cost), "euro")
            }
            "schedule_total" -> {
                val first = random.nextInt(10, if (harder) 91 else 61)
                val breakTime = random.nextInt(5, 31)
                val second = random.nextInt(10, if (harder) 91 else 61)
                Grade5NumberCandidate(kind, first + breakTime + second, listOf(first, breakTime, second), "minute")
            }
            "compare_plans" -> {
                val fixedA = random.nextInt(20, if (harder) 101 else 61)
                val eachA = random.nextInt(2, if (harder) 11 else 7)
                val people = random.nextInt(3, if (harder) 21 else 11)
                val totalA = fixedA + eachA * people
                val totalB = totalA + random.nextInt(5, if (harder) 51 else 26)
                Grade5NumberCandidate(kind, totalB - totalA, listOf(fixedA, eachA, people, totalB))
            }
            else -> {
                val people = random.nextInt(2, if (harder) 13 else 9)
                val each = random.nextInt(4, if (harder) 41 else 21)
                Grade5NumberCandidate(kind, each, listOf(people * each, people))
            }
        }
    }

    override fun visible(candidate: Grade5NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "total_then_share" -> LocalizedText("${v[0]} rode en ${v[1]} blauwe fiches worden gelijk verdeeld over ${v[2]} groepen. Hoeveel fiches krijgt elke groep?", "${v[0]} jetons rouges et ${v[1]} bleus sont répartis également entre ${v[2]} groupes. Combien chaque groupe reçoit-il ?", "${v[0]} red and ${v[1]} blue counters are shared equally among ${v[2]} groups. How many does each group get?")
            "budget_change" -> LocalizedText("Je hebt ${v[0]} cent. Je betaalt ${v[1]} cent en daarna ${v[2]} cent. Hoeveel cent blijft over?", "Tu as ${v[0]} centimes. Tu paies ${v[1]} centimes puis ${v[2]} centimes. Combien reste-t-il ?", "You have ${v[0]} cents. You pay ${v[1]} cents and then ${v[2]} cents. How many cents remain?")
            "unit_price" -> LocalizedText("${v[0]} potloden kosten elk ${v[1]} cent. Hoeveel cent betaal je in totaal?", "${v[0]} crayons coûtent chacun ${v[1]} centimes. Combien paies-tu au total ?", "${v[0]} pencils cost ${v[1]} cents each. How many cents do you pay altogether?")
            "remaining" -> LocalizedText("Een voorraad bevat ${v[0]} stuks. Er worden ${v[1]} gebruikt. Hoeveel blijven er over?", "Un stock contient ${v[0]} pièces. On en utilise ${v[1]}. Combien en reste-t-il ?", "A supply contains ${v[0]} items. ${v[1]} are used. How many remain?")
            "area_cost" -> LocalizedText("Een vloer is ${v[0]} m lang en ${v[1]} m breed. Elke vierkante meter kost € ${v[2]}. Wat is de totale kost in euro?", "Un sol mesure ${v[0]} m sur ${v[1]} m. Chaque mètre carré coûte ${v[2]} €. Quel est le coût total en euros ?", "A floor is ${v[0]} m long and ${v[1]} m wide. Each square meter costs €${v[2]}. What is the total cost in euros?")
            "schedule_total" -> LocalizedText("Een activiteit duurt ${v[0]} min, de pauze ${v[1]} min en een tweede activiteit ${v[2]} min. Hoeveel minuten duurt alles samen?", "Une activité dure ${v[0]} min, la pause ${v[1]} min et une seconde activité ${v[2]} min. Combien de minutes au total ?", "One activity lasts ${v[0]} min, the break ${v[1]} min, and a second activity ${v[2]} min. How many minutes altogether?")
            "compare_plans" -> LocalizedText("Plan A kost ${v[0]} + ${v[1]} per persoon voor ${v[2]} personen. Plan B kost ${v[3]}. Hoeveel duurder is plan B?", "Le plan A coûte ${v[0]} + ${v[1]} par personne pour ${v[2]} personnes. Le plan B coûte ${v[3]}. De combien le plan B est-il plus cher ?", "Plan A costs ${v[0]} plus ${v[1]} per person for ${v[2]} people. Plan B costs ${v[3]}. How much more expensive is plan B?")
            else -> LocalizedText("${v[0]} kaartjes worden gelijk verdeeld over ${v[1]} leerlingen. Hoeveel krijgt elke leerling?", "${v[0]} cartes sont réparties également entre ${v[1]} élèves. Combien chaque élève reçoit-il ?", "${v[0]} cards are shared equally among ${v[1]} students. How many does each student get?")
        }
    }

    override fun spoken(candidate: Grade5NumberCandidate): LocalizedText = visible(candidate)

    override fun hint(candidate: Grade5NumberCandidate): LocalizedText = LocalizedText(
        "Schrijf eerst de benodigde tussenstappen en controleer daarna of je antwoord bij de context past.",
        "Écris d'abord les étapes intermédiaires nécessaires, puis vérifie si ta réponse convient au contexte.",
        "Write the required intermediate steps first, then check whether your answer fits the context."
    )
}

internal object Grade5ScaleSpeedDistanceTimeGenerator : Grade5NumberGenerator(
    generatorId = "generator.g5.geometry.scale_speed_distance_time",
    roadmapEntry = "roadmap.g5.geometry.scale_speed_distance_time",
    idNamespace = "g5.geometry.scale_speed_distance_time",
    skill = "scale_speed_distance_time",
    domain = LearningDomain.GEOMETRY,
    kinds = listOf(
        "map_to_real",
        "real_to_map",
        "model_scale",
        "distance",
        "time",
        "speed",
        "route_total",
        "remaining_distance"
    ),
    automatedScope = "simple_scale_and_speed_distance_time_relations"
) {
    override fun create(kind: String, phase: String, random: StableLessonRandom): Grade5NumberCandidate {
        val harder = phase == "checkpoint"
        return when (kind) {
            "map_to_real" -> {
                val map = random.nextInt(2, if (harder) 16 else 11)
                val scale = listOf(2, 5, 10)[random.nextInt(3)]
                Grade5NumberCandidate(kind, map * scale, listOf(map, scale), "kilometre")
            }
            "real_to_map" -> {
                val scale = listOf(2, 5, 10)[random.nextInt(3)]
                val map = random.nextInt(2, if (harder) 16 else 11)
                Grade5NumberCandidate(kind, map, listOf(map * scale, scale), "centimetre")
            }
            "model_scale" -> {
                val model = random.nextInt(2, if (harder) 31 else 16)
                val factor = listOf(10, 20, 50, 100)[random.nextInt(4)]
                Grade5NumberCandidate(kind, model * factor, listOf(model, factor), "centimetre")
            }
            "distance" -> {
                val speed = listOf(4, 5, 6, 8, 10, 12, 15, 20)[random.nextInt(8)]
                val time = random.nextInt(2, if (harder) 9 else 6)
                Grade5NumberCandidate(kind, speed * time, listOf(speed, time), "kilometre")
            }
            "time" -> {
                val speed = listOf(4, 5, 6, 8, 10, 12, 15, 20)[random.nextInt(8)]
                val time = random.nextInt(2, if (harder) 9 else 6)
                Grade5NumberCandidate(kind, time, listOf(speed * time, speed), "hour")
            }
            "speed" -> {
                val speed = listOf(4, 5, 6, 8, 10, 12, 15, 20)[random.nextInt(8)]
                val time = random.nextInt(2, if (harder) 9 else 6)
                Grade5NumberCandidate(kind, speed, listOf(speed * time, time), "kilometres_per_hour")
            }
            "route_total" -> {
                val first = random.nextInt(2, if (harder) 41 else 21)
                val second = random.nextInt(2, if (harder) 41 else 21)
                val third = random.nextInt(2, if (harder) 41 else 21)
                Grade5NumberCandidate(kind, first + second + third, listOf(first, second, third), "kilometre")
            }
            else -> {
                val completed = random.nextInt(2, if (harder) 51 else 26)
                val remaining = random.nextInt(2, if (harder) 51 else 26)
                Grade5NumberCandidate(kind, remaining, listOf(completed + remaining, completed), "kilometre")
            }
        }
    }

    override fun visible(candidate: Grade5NumberCandidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            "map_to_real" -> LocalizedText("Op een kaart betekent 1 cm ${v[1]} km. Twee plaatsen liggen ${v[0]} cm uit elkaar. Hoeveel kilometer is dat in werkelijkheid?", "Sur une carte, 1 cm représente ${v[1]} km. Deux lieux sont distants de ${v[0]} cm. Quelle est la distance réelle ?", "On a map, 1 cm represents ${v[1]} km. Two places are ${v[0]} cm apart. What is the real distance in kilometers?")
            "real_to_map" -> LocalizedText("Op een kaart betekent 1 cm ${v[1]} km. Hoeveel centimeter stelt ${v[0]} km voor?", "Sur une carte, 1 cm représente ${v[1]} km. Combien de centimètres représentent ${v[0]} km ?", "On a map, 1 cm represents ${v[1]} km. How many centimeters represent ${v[0]} km?")
            "model_scale" -> LocalizedText("Een model is ${v[0]} cm lang. In werkelijkheid is alles ${v[1]} keer zo groot. Hoeveel centimeter is de echte lengte?", "Un modèle mesure ${v[0]} cm. En réalité, tout est ${v[1]} fois plus grand. Quelle est la longueur réelle en centimètres ?", "A model is ${v[0]} cm long. In reality, everything is ${v[1]} times larger. What is the real length in centimeters?")
            "distance" -> LocalizedText("Een fietser rijdt ${v[0]} km per uur gedurende ${v[1]} uur. Hoeveel kilometer legt hij af?", "Un cycliste roule à ${v[0]} km par heure pendant ${v[1]} heures. Quelle distance parcourt-il ?", "A cyclist travels at ${v[0]} km per hour for ${v[1]} hours. How many kilometers does the cyclist travel?")
            "time" -> LocalizedText("Een voertuig legt ${v[0]} km af met ${v[1]} km per uur. Hoeveel uur duurt de rit?", "Un véhicule parcourt ${v[0]} km à ${v[1]} km par heure. Combien d'heures dure le trajet ?", "A vehicle covers ${v[0]} km at ${v[1]} km per hour. How many hours does the trip take?")
            "speed" -> LocalizedText("Een wandelaar legt ${v[0]} km af in ${v[1]} uur. Hoeveel kilometer per uur is dat?", "Un marcheur parcourt ${v[0]} km en ${v[1]} heures. Quelle est sa vitesse en kilomètres par heure ?", "A walker covers ${v[0]} km in ${v[1]} hours. What is the speed in kilometers per hour?")
            "route_total" -> LocalizedText("Een route bestaat uit stukken van ${v[0]} km, ${v[1]} km en ${v[2]} km. Hoeveel kilometer is de hele route?", "Un itinéraire comprend des étapes de ${v[0]} km, ${v[1]} km et ${v[2]} km. Quelle est la distance totale ?", "A route has legs of ${v[0]} km, ${v[1]} km, and ${v[2]} km. How many kilometers is the whole route?")
            else -> LocalizedText("Een tocht is ${v[0]} km lang. Je hebt ${v[1]} km afgelegd. Hoeveel kilometer blijft over?", "Un trajet mesure ${v[0]} km. Tu as parcouru ${v[1]} km. Combien reste-t-il ?", "A trip is ${v[0]} km long. You have completed ${v[1]} km. How many kilometers remain?")
        }
    }

    override fun spoken(candidate: Grade5NumberCandidate): LocalizedText = visible(candidate)

    override fun hint(candidate: Grade5NumberCandidate): LocalizedText = LocalizedText(
        "Noteer welke grootheid ontbreekt en gebruik schaal × afstand of snelheid × tijd.",
        "Note la grandeur manquante et utilise échelle × distance ou vitesse × temps.",
        "Identify the missing quantity and use scale × distance or speed × time."
    )
}
