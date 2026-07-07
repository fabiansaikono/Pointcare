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
        val pool = templates.filter { template ->
            template.minGrade <= normalizedGrade && template.baseDifficulty <= normalizedStars + 1
        }.ifEmpty { templates.filter { it.minGrade <= normalizedGrade }.ifEmpty { templates } }

        val template = pool[random.nextInt(pool.size)]
        val jitter = when (normalizedStars) {
            1 -> 0.004f
            2 -> 0.010f
            else -> 0.016f
        }
        val scale = 0.90f + random.nextFloat() * 0.06f
        val dx = (1f - scale) / 2f + (random.nextFloat() - 0.5f) * 0.025f
        val dy = (1f - scale) / 2f + (random.nextFloat() - 0.5f) * 0.025f

        val labelsByStarId = generatedLabelsForTemplate(
            template = template,
            grade = normalizedGrade,
            difficultyStars = normalizedStars,
            random = random
        )
        val targetStars = template.stars.mapIndexed { index, star ->
            val x = clamp01(dx + star.x * scale + randomJitter(random, jitter))
            val y = clamp01(dy + star.y * scale + randomJitter(random, jitter))
            ConstellationStar(
                id = star.id,
                name = star.name,
                x = x,
                y = y,
                isTarget = true,
                order = index + 1,
                label = labelsByStarId[star.id] ?: (index + 1).toString(),
                brightness = star.brightness
            )
        }

        val distractorCount = when (normalizedStars) {
            1 -> max(0, template.stars.size / 4)
            2 -> max(2, template.stars.size / 2)
            else -> max(4, template.stars.size)
        }
        val distractors = generateDistractors(random, targetStars, distractorCount)
        val shuffledStars = (targetStars + distractors).shuffled(random)

        return ConstellationPuzzle(
            id = "generated-constellation-${template.id}-$normalizedGrade-$normalizedStars-$seed",
            templateId = template.id,
            constellationName = template.name,
            mission = LocalizedText(
                nl = "Verbind de sterren van ${template.name.nl}.",
                en = "Connect the stars of ${template.name.en ?: template.name.nl}.",
                fr = "Relie les étoiles de ${template.name.fr ?: template.name.nl}."
            ),
            fact = template.fact,
            difficultyStars = normalizedStars,
            grade = normalizedGrade,
            stars = shuffledStars,
            connections = template.connections.map { ConstellationConnection(it.first, it.second) },
            targetStarIds = template.stars.map { it.id }.toSet(),
            seed = seed
        )
    }

    private fun generateDistractors(
        random: Random,
        targetStars: List<ConstellationStar>,
        count: Int
    ): List<ConstellationStar> {
        val result = mutableListOf<ConstellationStar>()
        var attempts = 0
        while (result.size < count && attempts < count * 40) {
            attempts += 1
            val x = 0.07f + random.nextFloat() * 0.86f
            val y = 0.08f + random.nextFloat() * 0.84f
            val tooClose = (targetStars + result).any { star ->
                val dx = star.x - x
                val dy = star.y - y
                dx * dx + dy * dy < 0.0055f
            }
            if (!tooClose) {
                result += ConstellationStar(
                    id = "distractor-${result.size}-$attempts",
                    name = "",
                    x = x,
                    y = y,
                    isTarget = false,
                    order = null,
                    label = null,
                    brightness = 0.55f + random.nextFloat() * 0.35f
                )
            }
        }
        return result
    }

    private fun randomJitter(random: Random, amount: Float): Float =
        (random.nextFloat() - 0.5f) * amount

    private fun clamp01(value: Float): Float = min(0.94f, max(0.06f, value))

    private enum class LabelStyle { Counting, Odd, Multiples, Fractions, Expressions }

    private fun generatedLabelsForTemplate(
        template: Template,
        grade: Int,
        difficultyStars: Int,
        random: Random
    ): Map<String, String> {
        val baseLabels = generatedLabels(
            count = template.stars.size,
            grade = grade,
            difficultyStars = difficultyStars,
            random = random
        )
        val labels = template.stars.mapIndexed { index, star ->
            star.id to baseLabels.getOrElse(index) { (index + 1).toString() }
        }.toMap().toMutableMap()

        val neighboursByStarId = mutableMapOf<String, MutableSet<String>>()
        template.stars.forEach { star -> neighboursByStarId.getOrPut(star.id) { mutableSetOf() } }
        template.connections.forEach { (from, to) ->
            neighboursByStarId.getOrPut(from) { mutableSetOf() }.add(to)
            neighboursByStarId.getOrPut(to) { mutableSetOf() }.add(from)
        }

        val components = connectedComponents(template.stars.map { it.id }, neighboursByStarId)

        // Separate disconnected one-line components must be visually/logically clear.
        // Example: one edge can show 2 -> 4, another 52 -> 54 instead of reusing the same labels.
        components.forEachIndexed { componentIndex, component ->
            val componentConnections = template.connections.filter { (from, to) ->
                from in component && to in component
            }
            if (component.size == 2 && componentConnections.size == 1) {
                val orderedPair = componentConnections.first()
                val pairLabels = separateEdgeLabels(
                    componentIndex = componentIndex,
                    grade = grade,
                    difficultyStars = difficultyStars,
                    random = random
                )
                labels[orderedPair.first] = pairLabels.first
                labels[orderedPair.second] = pairLabels.second
            }
        }

        // Branches are different: when multiple lines leave one point, the destination labels
        // should be equivalent, so it is clear those stars belong together.
        neighboursByStarId
            .filterValues { neighbours -> neighbours.size > 1 }
            .keys
            .sorted()
            .forEachIndexed { branchIndex, branchStarId ->
                val neighbours = neighboursByStarId[branchStarId].orEmpty().sorted()
                val equivalentLabels = equivalentBranchLabels(
                    count = neighbours.size,
                    grade = grade,
                    difficultyStars = difficultyStars,
                    branchIndex = branchIndex,
                    random = random
                )
                neighbours.forEachIndexed { index, neighbourId ->
                    labels[neighbourId] = equivalentLabels[index % equivalentLabels.size]
                }
            }

        return labels
    }

    private fun connectedComponents(
        starIds: List<String>,
        neighboursByStarId: Map<String, Set<String>>
    ): List<Set<String>> {
        val visited = mutableSetOf<String>()
        val result = mutableListOf<Set<String>>()
        starIds.forEach { start ->
            if (start !in visited) {
                val component = mutableSetOf<String>()
                val stack = mutableListOf(start)
                while (stack.isNotEmpty()) {
                    val current = stack.removeAt(stack.lastIndex)
                    if (current in visited) continue
                    visited += current
                    component += current
                    neighboursByStarId[current].orEmpty().forEach { neighbour ->
                        if (neighbour !in visited) stack += neighbour
                    }
                }
                result += component
            }
        }
        return result
    }

    private fun separateEdgeLabels(
        componentIndex: Int,
        grade: Int,
        difficultyStars: Int,
        random: Random
    ): Pair<String, String> {
        val step = when {
            grade >= 4 || difficultyStars >= 3 -> listOf(2, 3, 5, 6)[(componentIndex + random.nextInt(4)) % 4]
            grade >= 2 -> listOf(2, 3, 4)[(componentIndex + random.nextInt(3)) % 3]
            else -> 2
        }
        val base = when {
            grade >= 4 || difficultyStars >= 3 -> 52 + componentIndex * 10
            grade >= 2 -> 12 + componentIndex * 10
            else -> 2 + componentIndex * 10
        }
        return base.toString() to (base + step).toString()
    }

    private fun equivalentBranchLabels(
        count: Int,
        grade: Int,
        difficultyStars: Int,
        branchIndex: Int,
        random: Random
    ): List<String> {
        val minimumCount = count.coerceAtLeast(1)
        return when {
            grade >= 4 && difficultyStars >= 3 -> {
                val target = listOf(12, 18, 24, 30, 36)[(branchIndex + random.nextInt(5)) % 5]
                expressionVariantsFor(target).take(minimumCount).ifEmpty { List(minimumCount) { target.toString() } }
            }
            grade >= 3 && difficultyStars >= 2 -> {
                val numerator = (branchIndex + 1) * 2
                val denominator = listOf(3, 4, 5)[(branchIndex + random.nextInt(3)) % 3]
                List(minimumCount) { index ->
                    val multiplier = index + 1
                    "${numerator * multiplier}/${denominator * multiplier}"
                }
            }
            grade >= 2 -> {
                val factor = listOf(2, 3, 4, 5, 7)[(branchIndex + random.nextInt(5)) % 5]
                val value = factor * (branchIndex + 2)
                List(minimumCount) { value.toString() }
            }
            else -> {
                val value = 1 + branchIndex * 2
                List(minimumCount) { value.toString() }
            }
        }
    }

    private fun expressionVariantsFor(target: Int): List<String> {
        val multiplicationPairs = (1..target)
            .filter { divisor -> target % divisor == 0 }
            .map { divisor -> divisor to target / divisor }
            .filter { (left, right) -> left <= 9 && right <= 12 }
            .map { (left, right) -> "${left}×${right}" }

        val additionPairs = listOf(
            "${target / 2}+${target - target / 2}",
            "${target + 3}-3",
            "${target - 2}+2"
        )

        return (multiplicationPairs + additionPairs).distinct()
    }

    private fun generatedLabels(
        count: Int,
        grade: Int,
        difficultyStars: Int,
        random: Random
    ): List<String> {
        val availableStyles = when {
            grade <= 1 && difficultyStars <= 1 -> listOf(LabelStyle.Counting, LabelStyle.Odd)
            grade <= 2 -> listOf(LabelStyle.Counting, LabelStyle.Odd, LabelStyle.Multiples)
            grade <= 3 -> listOf(LabelStyle.Odd, LabelStyle.Multiples, LabelStyle.Fractions)
            else -> listOf(LabelStyle.Multiples, LabelStyle.Fractions, LabelStyle.Expressions)
        }
        val style = availableStyles[random.nextInt(availableStyles.size)]
        return when (style) {
            LabelStyle.Counting -> List(count) { index -> (index + 1).toString() }
            LabelStyle.Odd -> List(count) { index -> (1 + index * 2).toString() }
            LabelStyle.Multiples -> {
                val factor = when {
                    grade >= 5 -> listOf(6, 7, 8, 9, 12)[random.nextInt(5)]
                    grade >= 3 -> listOf(3, 4, 5, 6, 7)[random.nextInt(5)]
                    else -> listOf(2, 3, 5, 10)[random.nextInt(4)]
                }
                List(count) { index -> ((index + 1) * factor).toString() }
            }
            LabelStyle.Fractions -> {
                val denominator = when {
                    grade >= 5 -> listOf(3, 4, 5, 6)[random.nextInt(4)]
                    else -> listOf(2, 3, 4)[random.nextInt(3)]
                }
                List(count) { index -> "${index + 1}/$denominator" }
            }
            LabelStyle.Expressions -> {
                val multipliers = listOf(3, 4, 5, 6, 7, 8, 9)
                List(count) { index ->
                    val left = index + 1
                    val right = multipliers[(index + random.nextInt(multipliers.size)) % multipliers.size]
                    "${left}×${right}"
                }
            }
        }
    }

    private data class Template(
        val id: String,
        val name: LocalizedText,
        val minGrade: Int,
        val baseDifficulty: Int,
        val stars: List<StarTemplate>,
        val connections: List<Pair<String, String>>,
        val fact: LocalizedText
    )

    private data class StarTemplate(
        val id: String,
        val name: String,
        val x: Float,
        val y: Float,
        val brightness: Float = 1f
    )

    private val templates: List<Template> = listOf(
        Template(
            id = "cassiopeia",
            name = LocalizedText(nl = "Cassiopeia", en = "Cassiopeia", fr = "Cassiopée"),
            minGrade = 1,
            baseDifficulty = 1,
            stars = listOf(
                StarTemplate("caph", "Caph", 0.10f, 0.56f, 0.95f),
                StarTemplate("shedar", "Schedar", 0.29f, 0.35f, 1.00f),
                StarTemplate("gamma-cas", "Gamma Cas", 0.47f, 0.58f, 0.90f),
                StarTemplate("ruchbah", "Ruchbah", 0.66f, 0.38f, 0.80f),
                StarTemplate("segin", "Segin", 0.86f, 0.56f, 0.75f)
            ),
            connections = listOf("caph" to "shedar", "shedar" to "gamma-cas", "gamma-cas" to "ruchbah", "ruchbah" to "segin"),
            fact = LocalizedText(
                nl = "Cassiopeia lijkt op een W aan de hemel.",
                en = "Cassiopeia often looks like a W in the sky.",
                fr = "Cassiopée ressemble souvent à un W dans le ciel."
            )
        ),
        Template(
            id = "pegasus-square",
            name = LocalizedText(nl = "Pegasus", en = "Pegasus", fr = "Pégase"),
            minGrade = 1,
            baseDifficulty = 1,
            stars = listOf(
                StarTemplate("markab", "Markab", 0.24f, 0.28f, 0.90f),
                StarTemplate("scheat", "Scheat", 0.24f, 0.70f, 0.88f),
                StarTemplate("alpheratz", "Alpheratz", 0.70f, 0.70f, 1.00f),
                StarTemplate("algenib", "Algenib", 0.70f, 0.28f, 0.92f)
            ),
            connections = listOf("markab" to "scheat", "scheat" to "alpheratz", "alpheratz" to "algenib", "algenib" to "markab"),
            fact = LocalizedText(
                nl = "Pegasus is het gevleugelde paard uit de Griekse mythologie.",
                en = "Pegasus is the winged horse from Greek mythology.",
                fr = "Pégase est le cheval ailé de la mythologie grecque."
            )
        ),
        Template(
            id = "crux",
            name = LocalizedText(nl = "Crux", en = "Crux", fr = "Croix du Sud"),
            minGrade = 1,
            baseDifficulty = 1,
            stars = listOf(
                StarTemplate("gacrux", "Gacrux", 0.50f, 0.17f, 0.95f),
                StarTemplate("acrux", "Acrux", 0.50f, 0.80f, 1.00f),
                StarTemplate("mimosa", "Mimosa", 0.31f, 0.48f, 0.95f),
                StarTemplate("delta-crucis", "Delta Crucis", 0.70f, 0.48f, 0.75f)
            ),
            connections = listOf("gacrux" to "acrux", "mimosa" to "delta-crucis"),
            fact = LocalizedText(
                nl = "Crux wordt ook het Zuiderkruis genoemd.",
                en = "Crux is also called the Southern Cross.",
                fr = "Crux est aussi appelée la Croix du Sud."
            )
        ),
        Template(
            id = "ursa-major-dipper",
            name = LocalizedText(nl = "Grote Beer", en = "Ursa Major", fr = "Grande Ourse"),
            minGrade = 2,
            baseDifficulty = 2,
            stars = listOf(
                StarTemplate("dubhe", "Dubhe", 0.18f, 0.20f, 1.00f),
                StarTemplate("merak", "Merak", 0.20f, 0.45f, 0.94f),
                StarTemplate("phecda", "Phecda", 0.38f, 0.54f, 0.88f),
                StarTemplate("megrez", "Megrez", 0.50f, 0.36f, 0.70f),
                StarTemplate("alioth", "Alioth", 0.64f, 0.32f, 0.97f),
                StarTemplate("mizar", "Mizar", 0.78f, 0.26f, 0.95f),
                StarTemplate("alkaid", "Alkaid", 0.91f, 0.18f, 0.94f)
            ),
            connections = listOf("dubhe" to "merak", "merak" to "phecda", "phecda" to "megrez", "megrez" to "alioth", "alioth" to "mizar", "mizar" to "alkaid"),
            fact = LocalizedText(
                nl = "Deze zeven sterren zijn de bekende steelpan in de Grote Beer.",
                en = "These seven stars are the well-known Big Dipper pattern inside Ursa Major.",
                fr = "Ces sept étoiles forment le célèbre chariot dans la Grande Ourse."
            )
        ),
        Template(
            id = "orion",
            name = LocalizedText(nl = "Orion", en = "Orion", fr = "Orion"),
            minGrade = 2,
            baseDifficulty = 2,
            stars = listOf(
                StarTemplate("betelgeuse", "Betelgeuse", 0.28f, 0.18f, 1.00f),
                StarTemplate("bellatrix", "Bellatrix", 0.58f, 0.21f, 0.92f),
                StarTemplate("mintaka", "Mintaka", 0.58f, 0.43f, 0.82f),
                StarTemplate("rigel", "Rigel", 0.64f, 0.78f, 1.00f),
                StarTemplate("saiph", "Saiph", 0.34f, 0.78f, 0.88f),
                StarTemplate("alnitak", "Alnitak", 0.34f, 0.44f, 0.86f),
                StarTemplate("alnilam", "Alnilam", 0.46f, 0.47f, 0.90f)
            ),
            connections = listOf(
                "betelgeuse" to "bellatrix", "bellatrix" to "mintaka", "mintaka" to "rigel", "rigel" to "saiph",
                "saiph" to "alnitak", "alnitak" to "betelgeuse", "alnitak" to "alnilam", "alnilam" to "mintaka"
            ),
            fact = LocalizedText(
                nl = "De drie sterren op een rij vormen de Gordel van Orion.",
                en = "The three stars in a row form Orion's Belt.",
                fr = "Les trois étoiles alignées forment la ceinture d'Orion."
            )
        ),
        Template(
            id = "cygnus",
            name = LocalizedText(nl = "Zwaan", en = "Cygnus", fr = "Cygne"),
            minGrade = 2,
            baseDifficulty = 2,
            stars = listOf(
                StarTemplate("deneb", "Deneb", 0.50f, 0.13f, 1.00f),
                StarTemplate("sadr", "Sadr", 0.50f, 0.40f, 0.94f),
                StarTemplate("albireo", "Albireo", 0.50f, 0.80f, 0.85f),
                StarTemplate("gienah", "Gienah", 0.27f, 0.46f, 0.82f),
                StarTemplate("delta-cygni", "Delta Cygni", 0.74f, 0.46f, 0.80f)
            ),
            connections = listOf("deneb" to "sadr", "sadr" to "albireo", "gienah" to "sadr", "sadr" to "delta-cygni"),
            fact = LocalizedText(
                nl = "Cygnus wordt ook het Noorderkruis genoemd.",
                en = "Cygnus is also called the Northern Cross.",
                fr = "Le Cygne est aussi appelé la Croix du Nord."
            )
        ),
        Template(
            id = "lyra",
            name = LocalizedText(nl = "Lier", en = "Lyra", fr = "Lyre"),
            minGrade = 2,
            baseDifficulty = 2,
            stars = listOf(
                StarTemplate("vega", "Vega", 0.50f, 0.15f, 1.00f),
                StarTemplate("epsilon-lyrae", "Epsilon Lyrae", 0.37f, 0.35f, 0.72f),
                StarTemplate("zeta-lyrae", "Zeta Lyrae", 0.62f, 0.35f, 0.76f),
                StarTemplate("beta-lyrae", "Sheliak", 0.66f, 0.68f, 0.82f),
                StarTemplate("gamma-lyrae", "Sulafat", 0.35f, 0.68f, 0.80f)
            ),
            connections = listOf("vega" to "epsilon-lyrae", "vega" to "zeta-lyrae", "epsilon-lyrae" to "gamma-lyrae", "gamma-lyrae" to "beta-lyrae", "beta-lyrae" to "zeta-lyrae"),
            fact = LocalizedText(
                nl = "Vega is de heldere ster bovenaan de Lier.",
                en = "Vega is the bright star at the top of Lyra.",
                fr = "Véga est l'étoile brillante en haut de la Lyre."
            )
        ),
        Template(
            id = "leo",
            name = LocalizedText(nl = "Leeuw", en = "Leo", fr = "Lion"),
            minGrade = 3,
            baseDifficulty = 3,
            stars = listOf(
                StarTemplate("regulus", "Regulus", 0.24f, 0.60f, 1.00f),
                StarTemplate("algieba", "Algieba", 0.35f, 0.43f, 0.95f),
                StarTemplate("adhafera", "Adhafera", 0.46f, 0.31f, 0.78f),
                StarTemplate("rasalas", "Rasalas", 0.58f, 0.27f, 0.74f),
                StarTemplate("chertan", "Chertan", 0.58f, 0.61f, 0.76f),
                StarTemplate("zosma", "Zosma", 0.69f, 0.50f, 0.85f),
                StarTemplate("denebola", "Denebola", 0.86f, 0.43f, 0.88f)
            ),
            connections = listOf("regulus" to "algieba", "algieba" to "adhafera", "adhafera" to "rasalas", "regulus" to "chertan", "chertan" to "zosma", "zosma" to "denebola"),
            fact = LocalizedText(
                nl = "Leo is een van de sterrenbeelden van de dierenriem.",
                en = "Leo is one of the zodiac constellations.",
                fr = "Le Lion est une des constellations du zodiaque."
            )
        ),
        Template(
            id = "aquila",
            name = LocalizedText(nl = "Arend", en = "Aquila", fr = "Aigle"),
            minGrade = 3,
            baseDifficulty = 3,
            stars = listOf(
                StarTemplate("altair", "Altair", 0.50f, 0.43f, 1.00f),
                StarTemplate("tarazed", "Tarazed", 0.43f, 0.29f, 0.82f),
                StarTemplate("alshain", "Alshain", 0.58f, 0.57f, 0.78f),
                StarTemplate("delta-aquilae", "Delta Aquilae", 0.35f, 0.64f, 0.70f),
                StarTemplate("zeta-aquilae", "Zeta Aquilae", 0.68f, 0.22f, 0.72f)
            ),
            connections = listOf("tarazed" to "altair", "altair" to "alshain", "altair" to "delta-aquilae", "altair" to "zeta-aquilae"),
            fact = LocalizedText(
                nl = "Altair is de helderste ster van Aquila.",
                en = "Altair is the brightest star in Aquila.",
                fr = "Altaïr est l'étoile la plus brillante de l'Aigle."
            )
        ),
        Template(
            id = "scorpius",
            name = LocalizedText(nl = "Schorpioen", en = "Scorpius", fr = "Scorpion"),
            minGrade = 4,
            baseDifficulty = 3,
            stars = listOf(
                StarTemplate("graffias", "Graffias", 0.21f, 0.18f, 0.78f),
                StarTemplate("dschubba", "Dschubba", 0.36f, 0.22f, 0.84f),
                StarTemplate("antares", "Antares", 0.31f, 0.38f, 1.00f),
                StarTemplate("jabbah", "Jabbah", 0.46f, 0.43f, 0.72f),
                StarTemplate("sargas", "Sargas", 0.55f, 0.57f, 0.83f),
                StarTemplate("lesath", "Lesath", 0.73f, 0.70f, 0.77f),
                StarTemplate("shaula", "Shaula", 0.82f, 0.78f, 0.92f)
            ),
            connections = listOf("graffias" to "dschubba", "dschubba" to "antares", "antares" to "jabbah", "jabbah" to "sargas", "sargas" to "lesath", "lesath" to "shaula"),
            fact = LocalizedText(
                nl = "Antares is een opvallend rode ster in Scorpius.",
                en = "Antares is a striking red-looking star in Scorpius.",
                fr = "Antarès est une étoile à l'aspect rouge dans le Scorpion."
            )
        )
    )
}
