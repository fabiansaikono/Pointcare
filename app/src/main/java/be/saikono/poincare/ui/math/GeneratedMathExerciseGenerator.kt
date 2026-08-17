package be.saikono.poincare.ui.math

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.effectiveGeneratedDifficulty
import kotlin.math.abs
import kotlin.random.Random

object GeneratedMathExerciseGenerator {
    private val supported = setOf(
        "mixed_arithmetic",
        "number_sequences",
        "fractions",
        "money"
    )

    fun supports(subcategoryId: String?): Boolean = subcategoryId in supported

    fun generate(
        subcategoryId: String,
        level: SkillLevel,
        startMissionNumber: Int,
        count: Int,
        hardModeEnabled: Boolean = false
    ): List<ExerciseContent> {
        val grade = level.toGrade() ?: return emptyList()
        if (!supports(subcategoryId) || count <= 0) return emptyList()
        val start = startMissionNumber.coerceAtLeast(1)
        return (start until start + count).map { mission ->
            val stars = WrittenCalculationGenerator.difficultyFor(
                mission,
                Random(stableSeed(subcategoryId, grade, mission) xor 0x24117).nextInt(300)
            )
            val random = Random(stableSeed(subcategoryId, grade, mission))
            val generationDifficulty = effectiveGeneratedDifficulty(stars, hardModeEnabled)
            when (subcategoryId) {
                "mixed_arithmetic" -> mixed(level, grade, stars, generationDifficulty, mission, random)
                "number_sequences" -> sequence(level, grade, stars, generationDifficulty, mission, random)
                "fractions" -> fractions(level, grade, stars, generationDifficulty, mission, random)
                "money" -> money(level, grade, stars, generationDifficulty, mission, random)
                else -> error("Unsupported generated math category")
            }
        }
    }

    private fun mixed(
        level: SkillLevel,
        grade: Int,
        stars: Int,
        generationDifficulty: Int,
        mission: Int,
        random: Random
    ): ExerciseContent {
        val scenario = (mission + generationDifficulty - stars) % 6
        val gradeMaximum = when (grade) {
            1 -> 20
            2 -> 100
            3 -> 1_000
            4 -> 10_000
            5 -> 100_000
            else -> 1_000_000
        }
        val max = when (generationDifficulty) {
            1 -> gradeMaximum / 2
            2 -> gradeMaximum * 3 / 4
            else -> gradeMaximum
        }.coerceAtLeast(20)
        val a: Int
        val b: Int
        val c: Int
        val answer: Int
        val nl: String
        val fr: String
        val en: String
        when {
            grade <= 2 && generationDifficulty >= 4 -> {
                a = random.nextInt((max / 3).coerceAtLeast(4), (max / 2).coerceAtLeast(5))
                b = random.nextInt(2, (max / 3).coerceAtLeast(3))
                c = random.nextInt(1, (max / 4).coerceAtLeast(2))
                val d = random.nextInt(1, (max / 5).coerceAtLeast(2))
                answer = (a + b + c - d).coerceAtLeast(0)
                nl = "Bereken $a + $b + $c − $d."
                fr = "Calcule $a + $b + $c − $d."
                en = "Calculate $a + $b + $c − $d."
            }
            grade <= 2 || scenario <= 1 -> {
                a = random.nextInt(2, (max / 2).coerceAtLeast(4))
                b = random.nextInt(1, (max / 3).coerceAtLeast(3))
                c = random.nextInt(1, (max / 4).coerceAtLeast(2))
                answer = (a + b - c).coerceAtLeast(0)
                nl = "Bereken $a + $b − $c."
                fr = "Calcule $a + $b − $c."
                en = "Calculate $a + $b − $c."
            }
            scenario == 2 -> {
                b = random.nextInt(2, 10)
                c = random.nextInt(2, 10)
                a = random.nextInt(1, 20)
                answer = a + b * c
                nl = "Bereken $a + $b × $c. Vermenigvuldig eerst."
                fr = "Calcule $a + $b × $c. Fais d'abord la multiplication."
                en = "Calculate $a + $b × $c. Multiply first."
            }
            scenario == 3 -> {
                b = random.nextInt(2, 10)
                c = random.nextInt(2, 10)
                a = random.nextInt(1, 20)
                answer = (a + b) * c
                nl = "Bereken ($a + $b) × $c."
                fr = "Calcule ($a + $b) × $c."
                en = "Calculate ($a + $b) × $c."
            }
            scenario == 4 -> {
                b = random.nextInt(2, 10)
                c = random.nextInt(2, 20)
                a = b * c
                val d = random.nextInt(1, 20)
                answer = a / b + d
                nl = "Bereken $a : $b + $d."
                fr = "Calcule $a : $b + $d."
                en = "Calculate $a ÷ $b + $d."
            }
            else -> {
                b = random.nextInt(2, 10)
                c = random.nextInt(2, 10)
                a = random.nextInt(2, 20)
                val product = a * b
                answer = product - c
                nl = "Een robot heeft $a dozen met telkens $b onderdelen. Hij gebruikt er $c. Hoeveel onderdelen blijven over?"
                fr = "Un robot a $a boîtes de $b pièces. Il en utilise $c. Combien de pièces restent ?"
                en = "A robot has $a boxes with $b parts each. It uses $c. How many parts remain?"
            }
        }
        return exercise(
            "mixed_arithmetic",
            level,
            grade,
            stars,
            generationDifficulty,
            mission,
            answer,
            nl,
            fr,
            en,
            "Gemengde bewerkingen",
            "Opérations mixtes",
            "Mixed operations",
            nlSpeech = mixedArithmeticSpeech(nl)
        )
    }

    private fun sequence(
        level: SkillLevel,
        grade: Int,
        stars: Int,
        generationDifficulty: Int,
        mission: Int,
        random: Random
    ): ExerciseContent {
        val scenario = (mission + generationDifficulty - stars) % 7
        val startLimit = when (grade) { 1 -> 20; 2 -> 100; else -> 500 } *
            if (generationDifficulty >= 4) 2 else 1
        val start = random.nextInt(0, startLimit)
        val step = when {
            scenario == 0 -> random.nextInt(1, if (generationDifficulty >= 4) 16 else 6)
            scenario == 1 -> -random.nextInt(1, if (generationDifficulty >= 4) 16 else 6)
            scenario == 2 -> random.nextInt(if (generationDifficulty >= 4) 15 else 5, if (generationDifficulty >= 4) 51 else 21)
            scenario == 3 && grade >= 3 -> random.nextInt(2, 6)
            scenario == 4 && grade >= 4 -> random.nextInt(10, 101)
            else -> random.nextInt(2, 11)
        }
        val values = if (step >= 0) List(5) { start + it * step } else {
            val safeStart = start + abs(step) * 5
            List(5) { safeStart + it * step }
        }
        val missing = when (scenario % 3) { 0 -> 2; 1 -> 4; else -> 1 }
        val shown = values.mapIndexed { i, value -> if (i == missing) "…" else value.toString() }.joinToString(" – ")
        val answer = values[missing]
        val nl = when (scenario) {
            5 -> "Een ruimteschip vliegt elke beurt $step kilometer verder. De afstanden zijn $shown. Welk getal ontbreekt?"
            6 -> "Vul het ontbrekende getal in: $shown."
            else -> "Welk getal ontbreekt in de reeks? $shown"
        }
        val fr = "Quel nombre manque dans la suite ? $shown"
        val en = "Which number is missing in the sequence? $shown"
        return exercise("number_sequences", level, grade, stars, generationDifficulty, mission, answer, nl, fr, en, "Getallenreeksen", "Suites de nombres", "Number sequences")
    }

    private fun fractions(
        level: SkillLevel,
        grade: Int,
        stars: Int,
        generationDifficulty: Int,
        mission: Int,
        random: Random
    ): ExerciseContent {
        val scenario = (mission + generationDifficulty - stars) % 6
        val denominator = when {
            generationDifficulty >= 4 && grade <= 2 -> listOf(3, 4, 5, 6, 8).random(random)
            generationDifficulty >= 4 && grade == 3 -> random.nextInt(5, 13)
            generationDifficulty >= 4 -> random.nextInt(7, 21)
            grade <= 2 -> listOf(2, 3, 4).random(random)
            grade == 3 -> random.nextInt(2, 9)
            else -> random.nextInt(2, 13)
        }
        val numerator = random.nextInt(1, denominator)
        val answer: Int
        val nl: String
        val fr: String
        val en: String
        when (scenario) {
            0 -> {
                answer = denominator - numerator
                nl = "Een geheel is verdeeld in $denominator gelijke delen. $numerator delen zijn gekleurd. Hoeveel delen zijn niet gekleurd?"
                fr = "Un tout est partagé en $denominator parts égales. $numerator sont coloriées. Combien ne le sont pas ?"
                en = "A whole is split into $denominator equal parts. $numerator are coloured. How many are not coloured?"
            }
            1 -> {
                val factor = random.nextInt(2, 6)
                answer = numerator * factor
                nl = "Maak de breuk gelijkwaardig: $numerator/$denominator = ?/${denominator * factor}. Vul de teller in."
                fr = "Complète la fraction équivalente : $numerator/$denominator = ?/${denominator * factor}. Donne le numérateur."
                en = "Complete the equivalent fraction: $numerator/$denominator = ?/${denominator * factor}. Enter the numerator."
            }
            2 -> {
                val other = random.nextInt(1, denominator)
                answer = numerator + other
                nl = "Bereken $numerator/$denominator + $other/$denominator. Vul alleen de teller van het antwoord in."
                fr = "Calcule $numerator/$denominator + $other/$denominator. Donne seulement le numérateur."
                en = "Calculate $numerator/$denominator + $other/$denominator. Enter only the numerator."
            }
            3 -> {
                val whole = random.nextInt(2, 9)
                answer = whole * numerator
                nl = "Hoeveel is $numerator/$denominator van ${whole * denominator}?"
                fr = "Combien vaut $numerator/$denominator de ${whole * denominator} ?"
                en = "What is $numerator/$denominator of ${whole * denominator}?"
            }
            4 -> {
                answer = denominator
                nl = "In hoeveel gelijke stukken moet je een pizza verdelen om stukken van 1/$denominator te krijgen?"
                fr = "En combien de parts égales faut-il couper une pizza pour obtenir des parts de 1/$denominator ?"
                en = "Into how many equal pieces must a pizza be cut to make pieces of 1/$denominator?"
            }
            else -> {
                val groups = random.nextInt(2, 7)
                answer = numerator * groups
                nl = "Er zijn $groups repen. Van elke reep wordt $numerator/$denominator gebruikt. Hoeveel $denominator-den zijn dat samen? Vul de teller in."
                fr = "On utilise $numerator/$denominator de chacune des $groups barres. Combien de $denominator-ièmes au total ? Donne le numérateur."
                en = "$numerator/$denominator of each of $groups bars is used. How many $denominator-ths is that altogether? Enter the numerator."
            }
        }
        return exercise("fractions", level, grade, stars, generationDifficulty, mission, answer, nl, fr, en, "Breuken", "Fractions", "Fractions")
    }

    private fun money(
        level: SkillLevel,
        grade: Int,
        stars: Int,
        generationDifficulty: Int,
        mission: Int,
        random: Random
    ): ExerciseContent {
        val scenario = (mission + generationDifficulty - stars) % 7
        val baseMaxCents = when (grade) { 1 -> 500; 2 -> 2_000; 3 -> 5_000; else -> 20_000 }
        val maxCents = baseMaxCents * if (generationDifficulty >= 4) 3 else if (generationDifficulty >= 3) 2 else 1
        val price = random.nextInt(20, maxCents.coerceAtLeast(100))
        val paid = ((price + 99) / 100 * 100) + random.nextInt(0, 6) * 100
        val answer: Int
        val nl: String
        val fr: String
        val en: String
        when (scenario) {
            0 -> {
                answer = paid - price
                nl = "Je betaalt ${euro(paid)} voor iets dat ${euro(price)} kost. Hoeveel cent krijg je terug?"
                fr = "Tu paies ${euro(paid)} pour un achat de ${euro(price)}. Combien de centimes reçois-tu ?"
                en = "You pay ${euro(paid)} for something costing ${euro(price)}. How many cents change do you receive?"
            }
            1 -> {
                val quantity = random.nextInt(2, if (generationDifficulty >= 4) 13 else 7)
                val unit = random.nextInt(25, (maxCents / quantity).coerceAtLeast(26))
                answer = quantity * unit
                nl = "$quantity voorwerpen kosten elk ${euro(unit)}. Hoeveel cent kosten ze samen?"
                fr = "$quantity objets coûtent chacun ${euro(unit)}. Combien de centimes au total ?"
                en = "$quantity items cost ${euro(unit)} each. How many cents do they cost altogether?"
            }
            2 -> {
                val second = random.nextInt(20, maxCents.coerceAtLeast(100))
                answer = price + second
                nl = "Je koopt iets van ${euro(price)} en iets van ${euro(second)}. Hoeveel cent betaal je samen?"
                fr = "Tu achètes un article à ${euro(price)} et un autre à ${euro(second)}. Combien de centimes au total ?"
                en = "You buy one item for ${euro(price)} and another for ${euro(second)}. How many cents altogether?"
            }
            3 -> {
                val discount = random.nextInt(10, price.coerceAtLeast(11))
                answer = price - discount
                nl = "Een prijs van ${euro(price)} wordt met ${euro(discount)} verlaagd. Hoeveel cent kost het nu?"
                fr = "Un prix de ${euro(price)} baisse de ${euro(discount)}. Combien de centimes coûte-t-il maintenant ?"
                en = "A price of ${euro(price)} is reduced by ${euro(discount)}. How many cents does it cost now?"
            }
            4 -> {
                val coin = listOf(5, 10, 20, 50, 100, 200).filter { it <= maxCents }.random(random)
                val count = random.nextInt(2, if (generationDifficulty >= 4) 21 else 10)
                answer = coin * count
                nl = "Je hebt $count munten van ${coin} cent. Hoeveel cent heb je?"
                fr = "Tu as $count pièces de $coin centimes. Combien de centimes as-tu ?"
                en = "You have $count coins of $coin cents. How many cents do you have?"
            }
            5 -> {
                val people = random.nextInt(2, 6)
                val total = people * random.nextInt(100, (maxCents / people).coerceAtLeast(101))
                answer = total / people
                nl = "$people kinderen delen ${euro(total)} gelijk. Hoeveel cent krijgt elk kind?"
                fr = "$people enfants partagent ${euro(total)} également. Combien de centimes chacun ?"
                en = "$people children share ${euro(total)} equally. How many cents does each child get?"
            }
            else -> {
                answer = price
                nl = "Zet ${euro(price)} om naar cent."
                fr = "Convertis ${euro(price)} en centimes."
                en = "Convert ${euro(price)} to cents."
            }
        }
        return exercise("money", level, grade, stars, generationDifficulty, mission, answer, nl, fr, en, "Geld", "Argent", "Money")
    }

    private fun exercise(
        subcategoryId: String,
        level: SkillLevel,
        grade: Int,
        stars: Int,
        generationDifficulty: Int,
        mission: Int,
        answer: Int,
        nl: String,
        fr: String,
        en: String,
        topicNl: String,
        topicFr: String,
        topicEn: String,
        nlSpeech: String? = null
    ) = ExerciseContent(
        id = "generated-$subcategoryId-g$grade-${mission.toString().padStart(6, '0')}",
        domain = LearningDomain.MATH,
        skill = subcategoryId,
        section = ExerciseSection.EXERCISES,
        level = level,
        inputMode = InputMode.CUSTOM_NUMBER_PAD,
        prompt = LocalizedText(nl = nl, fr = fr, en = en),
        speechText = LocalizedText(nl = nl, fr = fr, en = en),
        answer = answer.toString(),
        data = buildMap {
            put("generatedMathExercise", "true")
            put("subcategoryId", subcategoryId)
            put("grade", grade.toString())
            put("difficultyStars", stars.toString())
            put("generationDifficulty", generationDifficulty.toString())
            put("missionNumber", mission.toString())
            put("progressKey", "generated-$subcategoryId-g$grade-${mission.toString().padStart(6, '0')}")
            put("topic_nl-BE", topicNl)
            put("topic_fr-BE", topicFr)
            put("topic_en-US", topicEn)
            nlSpeech?.takeIf { it.isNotBlank() }?.let { put("promptTts_nl-BE", it) }
        }
    )

    private fun mixedArithmeticSpeech(text: String): String = text
        .replace("(", " ")
        .replace(")", " ")
        .replace("×", " keer ")
        .replace("÷", " gedeeld door ")
        .replace(":", " gedeeld door ")
        .replace("+", " plus ")
        .replace("−", " min ")
        .replace("-", " min ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun euro(cents: Int): String = "€ ${cents / 100},${(cents % 100).toString().padStart(2, '0')}"

    private fun stableSeed(id: String, grade: Int, mission: Int): Int =
        31 * (31 * id.hashCode() + grade) + mission
}

private fun SkillLevel.toGrade(): Int? = if (this == SkillLevel.NOT_APPLICABLE) null else ordinal + 1
