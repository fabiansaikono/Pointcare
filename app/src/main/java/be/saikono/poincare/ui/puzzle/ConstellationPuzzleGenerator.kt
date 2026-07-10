package be.saikono.poincare.ui.puzzle

import be.saikono.poincare.model.LocalizedText
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object ConstellationPuzzleGenerator {
    fun generate(
        grade: Int,
        difficultyStars: Int,
        seed: Long = System.currentTimeMillis()
    ): ConstellationPuzzle {
        val normalizedGrade = grade.coerceIn(1, 6)
        val normalizedStars = difficultyStars.coerceIn(1, 3)
        val random = Random(seed)
        val groupLengths = groupLengthsFor(normalizedStars, random)
        val groupCount = groupLengths.size
        val patterns = buildPatterns(
            grade = normalizedGrade,
            difficultyStars = normalizedStars,
            groupLengths = groupLengths,
            random = random
        )
        val stars = mutableListOf<ConstellationStar>()
        val connections = mutableListOf<ConstellationConnection>()
        val targetStarIds = mutableSetOf<String>()

        val layouts = generateLayouts(
            groupCount = groupCount,
            groupLengths = groupLengths,
            difficultyStars = normalizedStars,
            random = random
        )

        patterns.forEachIndexed { groupIndex, pattern ->
            val groupId = "path-${groupIndex + 1}"
            val symbol = listOf("A", "B", "C").getOrElse(groupIndex) { ('A' + groupIndex).toString() }
            val visibleSymbol = symbol.takeIf { groupCount > 1 }
            val points = layouts[groupIndex].take(groupLengths[groupIndex])
            val values = pattern.values.take(points.size)

            points.forEachIndexed { orderIndex, point ->
                val starId = "$groupId-star-${orderIndex + 1}"
                targetStarIds += starId
                stars += ConstellationStar(
                    id = starId,
                    name = if (visibleSymbol != null) "${symbol}${orderIndex + 1}" else "Star ${orderIndex + 1}",
                    x = clamp01(point.first + randomJitter(random, 0.008f + normalizedStars * 0.003f)),
                    y = clamp01(point.second + randomJitter(random, 0.008f + normalizedStars * 0.003f)),
                    isTarget = true,
                    order = orderIndex + 1,
                    label = values[orderIndex],
                    brightness = 0.80f + random.nextFloat() * 0.18f,
                    groupId = groupId,
                    groupSymbol = visibleSymbol,
                    groupRule = pattern.rule
                )
            }

            for (i in 0 until points.lastIndex) {
                connections += ConstellationConnection(
                    fromStarId = "$groupId-star-${i + 1}",
                    toStarId = "$groupId-star-${i + 2}"
                )
            }
        }

        val distractorCount = when (normalizedStars) {
            1 -> 2
            2 -> 4
            else -> 6
        }
        stars += generateDistractors(random, stars, distractorCount)

        val constellationName = randomConstellationName(random)
        val rules = patterns.mapIndexed { index, pattern ->
            if (groupCount == 1) {
                pattern.rule
            } else {
                val symbol = listOf("A", "B", "C").getOrElse(index) { ('A' + index).toString() }
                LocalizedText(
                    nl = "$symbol: ${pattern.rule.nl}",
                    en = "$symbol: ${pattern.rule.en ?: pattern.rule.nl}",
                    fr = "$symbol : ${pattern.rule.fr ?: pattern.rule.nl}"
                )
            }
        }

        val mission = if (groupCount == 1) {
            LocalizedText(
                nl = "Verbind de reeks van start naar einde. Het pad kan alle richtingen uitgaan, maar je volgt altijd de volgende waarde.",
                en = "Connect the sequence from start to finish. The path can go in any direction, but you always follow the next value.",
                fr = "Relie la suite du début à la fin. Le chemin peut aller dans toutes les directions, mais tu suis toujours la valeur suivante."
            )
        } else {
            LocalizedText(
                nl = "Verbind elke reeks van start naar einde. Je mag zelf kiezen of je met A, B of C begint.",
                en = "Connect each sequence from start to finish. You may choose whether to start with A, B or C.",
                fr = "Relie chaque suite du début à la fin. Tu peux choisir de commencer par A, B ou C."
            )
        }

        return ConstellationPuzzle(
            id = "constellation-dynamic-paths-$normalizedGrade-$normalizedStars-$seed",
            templateId = "dynamic-paths-v4",
            constellationName = constellationName,
            mission = mission,
            fact = LocalizedText(
                nl = "Sterrenbeelden lijken soms rommelig, maar een duidelijk startpunt en een vaste volgorde maken het patroon herkenbaar.",
                en = "Constellations can look messy, but a clear starting point and a fixed order make the pattern easier to recognize.",
                fr = "Les constellations peuvent sembler désordonnées, mais un début clair et un ordre fixe rendent le motif plus facile à reconnaître."
            ),
            difficultyStars = normalizedStars,
            grade = normalizedGrade,
            stars = stars.shuffled(random),
            connections = connections,
            targetStarIds = targetStarIds,
            seed = seed,
            rules = rules
        )
    }

    private data class Pattern(
        val values: List<String>,
        val rule: LocalizedText
    )

    private data class Zone(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    private fun groupLengthsFor(difficultyStars: Int, random: Random): List<Int> {
        return when (difficultyStars) {
            1 -> listOf(3)
            2 -> if (random.nextBoolean()) listOf(3, 3) else listOf(4, 3)
            else -> when (random.nextInt(3)) {
                0 -> listOf(4, 3, 3)
                1 -> listOf(3, 4, 3)
                else -> listOf(4, 4, 3)
            }
        }
    }

    private fun buildPatterns(
        grade: Int,
        difficultyStars: Int,
        groupLengths: List<Int>,
        random: Random
    ): List<Pattern> {
        val patterns = mutableListOf<Pattern>()
        val usedStarts = mutableSetOf<Int>()

        groupLengths.forEachIndexed { index, length ->
            patterns += when {
                grade <= 1 -> additionPattern(
                    start = uniqueStart(random, usedStarts, 1, 12) + index * 8,
                    step = if (difficultyStars == 1) 1 else listOf(1, 2)[random.nextInt(2)],
                    length = length
                )
                grade == 2 -> additionPattern(
                    start = uniqueStart(random, usedStarts, 2, 30) + index * 12,
                    step = listOf(2, 3, 5)[(index + random.nextInt(3)) % 3],
                    length = length
                )
                grade == 3 -> when ((index + random.nextInt(2)) % 2) {
                    0 -> multiplicationPattern(
                        start = listOf(2, 3, 4, 5)[random.nextInt(4)],
                        factor = 2,
                        length = length
                    )
                    else -> additionPattern(
                        start = uniqueStart(random, usedStarts, 10, 60),
                        step = listOf(5, 10)[random.nextInt(2)],
                        length = length
                    )
                }
                grade == 4 -> when ((index + random.nextInt(2)) % 2) {
                    0 -> multiplicationPattern(
                        start = listOf(2, 3, 4, 5, 6)[random.nextInt(5)],
                        factor = listOf(2, 3)[random.nextInt(2)],
                        length = length
                    )
                    else -> additionPattern(
                        start = uniqueStart(random, usedStarts, 15, 80),
                        step = listOf(5, 10, 25)[random.nextInt(3)],
                        length = length
                    )
                }
                else -> when ((index + random.nextInt(3)) % 3) {
                    0 -> multiplicationPattern(
                        start = listOf(2, 3, 4, 5, 6)[random.nextInt(5)],
                        factor = listOf(2, 3, 4)[random.nextInt(3)],
                        length = length
                    )
                    1 -> fractionPattern(
                        numeratorStart = listOf(1, 2, 3)[random.nextInt(3)],
                        denominator = listOf(4, 5, 8, 10)[random.nextInt(4)],
                        length = length
                    )
                    else -> additionPattern(
                        start = uniqueStart(random, usedStarts, 100, 400),
                        step = listOf(25, 50, 100)[random.nextInt(3)],
                        length = length
                    )
                }
            }
        }

        return patterns
    }

    private fun additionPattern(start: Int, step: Int, length: Int): Pattern {
        val values = List(length) { (start + it * step).toString() }
        return Pattern(
            values = values,
            rule = LocalizedText(
                nl = "tel telkens $step erbij",
                en = "add $step each time",
                fr = "ajoute $step à chaque fois"
            )
        )
    }

    private fun multiplicationPattern(start: Int, factor: Int, length: Int): Pattern {
        val values = generateSequence(start) { it * factor }
            .take(length)
            .map { it.toString() }
            .toList()
        return Pattern(
            values = values,
            rule = LocalizedText(
                nl = "vermenigvuldig telkens met $factor",
                en = "multiply by $factor each time",
                fr = "multiplie par $factor à chaque fois"
            )
        )
    }

    private fun fractionPattern(numeratorStart: Int, denominator: Int, length: Int): Pattern {
        val values = List(length) { index -> "${numeratorStart + index}/$denominator" }
        return Pattern(
            values = values,
            rule = LocalizedText(
                nl = "de teller wordt telkens 1 groter",
                en = "the numerator grows by 1 each time",
                fr = "le numérateur augmente de 1 à chaque fois"
            )
        )
    }

    private fun generateLayouts(
        groupCount: Int,
        groupLengths: List<Int>,
        difficultyStars: Int,
        random: Random
    ): List<List<Pair<Float, Float>>> {
        val zones = zonesFor(groupCount, random).shuffled(random)
        return groupLengths.mapIndexed { index, length ->
            val zone = zones[index]
            val shape = pathShape(length, difficultyStars, random)
            val mirrorX = random.nextBoolean()
            val mirrorY = random.nextBoolean()
            val swapAxes = random.nextInt(100) < when (groupCount) {
                1 -> 35
                2 -> 45
                else -> 50
            }

            shape.map { point ->
                var x = point.first
                var y = point.second
                if (swapAxes) {
                    val oldX = x
                    x = y
                    y = oldX
                }
                if (mirrorX) x = 1f - x
                if (mirrorY) y = 1f - y
                clamp01(zone.x + x * zone.width) to clamp01(zone.y + y * zone.height)
            }
        }
    }

    private fun zonesFor(groupCount: Int, random: Random): List<Zone> {
        val options = when (groupCount) {
            1 -> listOf(
                listOf(Zone(0.06f, 0.08f, 0.88f, 0.84f)),
                listOf(Zone(0.12f, 0.06f, 0.76f, 0.88f))
            )
            2 -> listOf(
                listOf(Zone(0.06f, 0.08f, 0.88f, 0.36f), Zone(0.06f, 0.56f, 0.88f, 0.36f)),
                listOf(Zone(0.06f, 0.10f, 0.40f, 0.80f), Zone(0.54f, 0.10f, 0.40f, 0.80f)),
                listOf(Zone(0.06f, 0.08f, 0.54f, 0.42f), Zone(0.40f, 0.54f, 0.54f, 0.38f)),
                listOf(Zone(0.40f, 0.08f, 0.54f, 0.42f), Zone(0.06f, 0.54f, 0.54f, 0.38f))
            )
            else -> listOf(
                listOf(Zone(0.06f, 0.07f, 0.88f, 0.25f), Zone(0.06f, 0.38f, 0.88f, 0.24f), Zone(0.06f, 0.68f, 0.88f, 0.24f)),
                listOf(Zone(0.06f, 0.08f, 0.40f, 0.38f), Zone(0.54f, 0.08f, 0.40f, 0.38f), Zone(0.08f, 0.56f, 0.84f, 0.36f)),
                listOf(Zone(0.06f, 0.10f, 0.28f, 0.80f), Zone(0.36f, 0.10f, 0.28f, 0.80f), Zone(0.66f, 0.10f, 0.28f, 0.80f)),
                listOf(Zone(0.08f, 0.08f, 0.36f, 0.36f), Zone(0.56f, 0.22f, 0.36f, 0.36f), Zone(0.22f, 0.58f, 0.56f, 0.34f))
            )
        }
        return options[random.nextInt(options.size)]
    }

    private fun pathShape(
        length: Int,
        difficultyStars: Int,
        random: Random
    ): List<Pair<Float, Float>> {
        val easyThreePointShapes = listOf(
            listOf(0.16f to 0.38f, 0.50f to 0.56f, 0.84f to 0.34f),
            listOf(0.20f to 0.72f, 0.48f to 0.32f, 0.80f to 0.62f),
            listOf(0.22f to 0.24f, 0.52f to 0.34f, 0.78f to 0.72f)
        )
        val threePointShapes = easyThreePointShapes + listOf(
            listOf(0.18f to 0.48f, 0.50f to 0.18f, 0.82f to 0.78f),
            listOf(0.24f to 0.20f, 0.72f to 0.40f, 0.44f to 0.80f),
            listOf(0.30f to 0.86f, 0.50f to 0.42f, 0.70f to 0.12f),
            listOf(0.18f to 0.78f, 0.76f to 0.62f, 0.42f to 0.20f)
        )
        val fourPointShapes = listOf(
            listOf(0.10f to 0.25f, 0.38f to 0.72f, 0.62f to 0.28f, 0.90f to 0.68f),
            listOf(0.14f to 0.72f, 0.34f to 0.28f, 0.62f to 0.22f, 0.86f to 0.64f),
            listOf(0.22f to 0.18f, 0.72f to 0.34f, 0.38f to 0.62f, 0.84f to 0.84f),
            listOf(0.22f to 0.84f, 0.44f to 0.58f, 0.62f to 0.34f, 0.78f to 0.14f),
            listOf(0.26f to 0.22f, 0.48f to 0.78f, 0.70f to 0.42f, 0.44f to 0.14f),
            listOf(0.16f to 0.50f, 0.42f to 0.20f, 0.66f to 0.58f, 0.84f to 0.34f)
        )
        return when {
            length <= 3 && difficultyStars <= 1 -> easyThreePointShapes[random.nextInt(easyThreePointShapes.size)].take(length)
            length <= 3 -> threePointShapes[random.nextInt(threePointShapes.size)].take(length)
            else -> fourPointShapes[random.nextInt(fourPointShapes.size)].take(length)
        }
    }

    private fun generateDistractors(
        random: Random,
        existingStars: List<ConstellationStar>,
        count: Int
    ): List<ConstellationStar> {
        val result = mutableListOf<ConstellationStar>()
        var attempts = 0
        while (result.size < count && attempts < count * 100) {
            attempts += 1
            val x = 0.06f + random.nextFloat() * 0.88f
            val y = 0.08f + random.nextFloat() * 0.84f
            val tooClose = (existingStars + result).any { star ->
                val dx = star.x - x
                val dy = star.y - y
                dx * dx + dy * dy < 0.0072f
            }
            if (!tooClose) {
                result += ConstellationStar(
                    id = "distractor-${result.size}-$attempts",
                    name = "background star",
                    x = x,
                    y = y,
                    isTarget = false,
                    order = null,
                    label = null,
                    brightness = 0.32f + random.nextFloat() * 0.36f,
                    groupId = null,
                    groupSymbol = null,
                    groupRule = null
                )
            }
        }
        return result
    }

    private fun randomConstellationName(random: Random): LocalizedText {
        val names = listOf(
            LocalizedText(nl = "Sterrenvlecht", en = "Star weave", fr = "Tresse d'étoiles"),
            LocalizedText(nl = "Kosmische route", en = "Cosmic route", fr = "Route cosmique"),
            LocalizedText(nl = "Nevelpad", en = "Nebula trail", fr = "Chemin de nébuleuse"),
            LocalizedText(nl = "Meteorenspoor", en = "Meteor trail", fr = "Trace de météores"),
            LocalizedText(nl = "Ruimteladder", en = "Space ladder", fr = "Échelle spatiale")
        )
        return names[random.nextInt(names.size)]
    }

    private fun uniqueStart(random: Random, usedStarts: MutableSet<Int>, from: Int, to: Int): Int {
        repeat(30) {
            val value = random.nextInt(from, to + 1)
            if (usedStarts.add(value)) return value
        }
        return max(from, (usedStarts.maxOrNull() ?: from) + 7)
    }

    private fun randomJitter(random: Random, amount: Float): Float = (random.nextFloat() - 0.5f) * amount

    private fun clamp01(value: Float): Float = min(0.94f, max(0.06f, value))
}
