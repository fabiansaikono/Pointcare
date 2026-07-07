package be.saikono.poincare.ui.puzzle

import be.saikono.poincare.model.SkillLevel
import java.util.Locale
import kotlin.math.max
import kotlin.math.round
import kotlin.random.Random

const val GALAXYCON_SUBCATEGORY_ID = "galaxycon"
const val GALAXYCON_START_LIVES = 5
const val GALAXYCON_MAX_LIVES = 5
const val GALAXYCON_MULTIPLIER_DURATION_MS = 15_000L

data class GalaxyconWord(
    val id: String,
    val text: String,
    val hintNl: String,
    val hintEn: String,
    val hintFr: String,
    val difficulty: Int
) {
    fun hint(locale: String): String = when (normalizeGalaxyconLocale(locale)) {
        "en-US" -> hintEn
        "fr-BE" -> hintFr
        else -> hintNl
    }
}

data class GalaxyconLevelSpec(
    val id: String,
    val title: String,
    val planetName: String,
    val words: List<GalaxyconWord>,
    val difficultyStars: Int,
    val speedFractionPerSecond: Float,
    val spawnEveryMs: Long,
    val durationMs: Long,
    val maxExpectedCollisions: Int,
    val seed: Int
) {
    val totalRequiredLetters: Int = words.sumOf { it.text.length }
}

enum class GalaxyconObjectType {
    CORRECT_LETTER,
    WRONG_LETTER,
    STAR,
    MULTIPLIER,
    DEBRIS
}

data class GalaxyconFlyingObject(
    val id: Long,
    val type: GalaxyconObjectType,
    val lane: Int,
    val x: Float,
    val label: String,
    val multiplier: Int = 1
)

data class GalaxyconRunResult(
    val scorePercent: Float,
    val displayScore: String,
    val points: Int,
    val collisions: Int,
    val wordsCompleted: Int,
    val totalWords: Int,
    val correctLetters: Int,
    val totalLetters: Int,
    val starsCollected: Int,
    val multipliersCollected: Int,
    val planetReached: Boolean,
    val stickersEarned: List<String>,
    val elapsedMs: Long
)

object GalaxyconGenerator {
    fun createLevel(
        locale: String,
        level: SkillLevel,
        difficultyStars: Int
    ): GalaxyconLevelSpec {
        val stars = difficultyStars.coerceIn(1, 3)
        val normalizedLocale = normalizeGalaxyconLocale(locale)
        val gradeBias = when (level) {
            SkillLevel.NOT_APPLICABLE -> 1
            else -> level.ordinal + 1
        }.coerceIn(1, 6)
        val targetDifficulty = (stars + (gradeBias - 1) / 2).coerceIn(1, 3)
        val words = wordsFor(normalizedLocale)
            .filter { it.difficulty <= targetDifficulty }
            .sortedWith(compareBy<GalaxyconWord> { it.difficulty }.thenBy { it.text.length })
            .let { pool ->
                val needed = when (stars) {
                    1 -> 3
                    2 -> 4
                    else -> 5
                }
                val seed = 31 * normalizedLocale.hashCode() + 17 * level.name.hashCode() + stars
                pool.shuffled(Random(seed)).take(needed)
            }

        val planet = when (stars) {
            1 -> "Lumora"
            2 -> "Nebulix"
            else -> "Orbinox"
        }

        return GalaxyconLevelSpec(
            id = "galaxycon-${normalizedLocale.lowercase(Locale.US)}-${level.name.lowercase(Locale.US)}-$stars",
            title = "Galaxycon",
            planetName = planet,
            words = words,
            difficultyStars = stars,
            speedFractionPerSecond = when (stars) {
                1 -> 0.18f
                2 -> 0.22f
                else -> 0.26f
            },
            spawnEveryMs = when (stars) {
                1 -> 1_350L
                2 -> 1_150L
                else -> 980L
            },
            durationMs = when (stars) {
                1 -> 55_000L
                2 -> 65_000L
                else -> 75_000L
            },
            maxExpectedCollisions = when (stars) {
                1 -> 8
                2 -> 7
                else -> 6
            },
            seed = 73 * normalizedLocale.hashCode() + 19 * level.name.hashCode() + stars
        )
    }

    fun createObject(
        level: GalaxyconLevelSpec,
        nextRequiredLetter: Char?,
        locale: String,
        spawnIndex: Long
    ): GalaxyconFlyingObject {
        val rng = Random(level.seed + spawnIndex.toInt() * 97)
        val lane = rng.nextInt(0, 3)
        val roll = rng.nextFloat()
        val type = when {
            nextRequiredLetter != null && roll < correctLetterChance(level.difficultyStars) -> GalaxyconObjectType.CORRECT_LETTER
            roll < wrongLetterLimit(level.difficultyStars) -> GalaxyconObjectType.WRONG_LETTER
            roll < 0.73f -> GalaxyconObjectType.DEBRIS
            roll < 0.91f -> GalaxyconObjectType.STAR
            else -> GalaxyconObjectType.MULTIPLIER
        }

        return when (type) {
            GalaxyconObjectType.CORRECT_LETTER -> GalaxyconFlyingObject(
                id = spawnIndex,
                type = type,
                lane = lane,
                x = 1.08f,
                label = nextRequiredLetter?.uppercaseChar()?.toString().orEmpty()
            )

            GalaxyconObjectType.WRONG_LETTER -> GalaxyconFlyingObject(
                id = spawnIndex,
                type = type,
                lane = lane,
                x = 1.08f,
                label = randomWrongLetter(rng, nextRequiredLetter, locale).toString()
            )

            GalaxyconObjectType.STAR -> GalaxyconFlyingObject(
                id = spawnIndex,
                type = type,
                lane = lane,
                x = 1.08f,
                label = "★"
            )

            GalaxyconObjectType.MULTIPLIER -> {
                val multiplier = listOf(2, 3, 4, 5)[rng.nextInt(4)]
                GalaxyconFlyingObject(
                    id = spawnIndex,
                    type = type,
                    lane = lane,
                    x = 1.08f,
                    label = "X$multiplier",
                    multiplier = multiplier
                )
            }

            GalaxyconObjectType.DEBRIS -> GalaxyconFlyingObject(
                id = spawnIndex,
                type = type,
                lane = lane,
                x = 1.08f,
                label = listOf("☄", "◆", "◼")[rng.nextInt(3)]
            )
        }
    }

    private fun correctLetterChance(stars: Int): Float = when (stars) {
        1 -> 0.44f
        2 -> 0.39f
        else -> 0.34f
    }

    private fun wrongLetterLimit(stars: Int): Float = when (stars) {
        1 -> 0.55f
        2 -> 0.58f
        else -> 0.62f
    }

    private fun randomWrongLetter(rng: Random, nextRequiredLetter: Char?, locale: String): Char {
        val alphabet = when (normalizeGalaxyconLocale(locale)) {
            "fr-BE" -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            else -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        }
        var candidate = alphabet[rng.nextInt(alphabet.length)]
        repeat(8) {
            if (nextRequiredLetter == null || !candidate.equals(nextRequiredLetter, ignoreCase = true)) return candidate
            candidate = alphabet[rng.nextInt(alphabet.length)]
        }
        return if (nextRequiredLetter?.uppercaseChar() == 'A') 'B' else 'A'
    }

    private fun wordsFor(locale: String): List<GalaxyconWord> = when (normalizeGalaxyconLocale(locale)) {
        "en-US" -> listOf(
            GalaxyconWord("galaxycon_en_sun", "sun", "De zon geeft licht.", "The sun gives light.", "Le soleil donne de la lumiere.", 1),
            GalaxyconWord("galaxycon_en_moon", "moon", "Je ziet hem vaak 's nachts.", "You often see it at night.", "On la voit souvent la nuit.", 1),
            GalaxyconWord("galaxycon_en_star", "star", "Een lichtpunt in de ruimte.", "A bright point in space.", "Un point lumineux dans l'espace.", 1),
            GalaxyconWord("galaxycon_en_rocket", "rocket", "Hij vliegt naar de ruimte.", "It flies into space.", "Elle vole vers l'espace.", 2),
            GalaxyconWord("galaxycon_en_planet", "planet", "Een wereld die rond een ster draait.", "A world that moves around a star.", "Un monde qui tourne autour d'une etoile.", 2),
            GalaxyconWord("galaxycon_en_orbit", "orbit", "Een baan rond een planeet.", "A path around a planet.", "Un chemin autour d'une planete.", 2),
            GalaxyconWord("galaxycon_en_galaxy", "galaxy", "Een groep sterren.", "A group of stars.", "Un groupe d'etoiles.", 3),
            GalaxyconWord("galaxycon_en_astronaut", "astronaut", "Iemand die naar de ruimte reist.", "A person who travels to space.", "Une personne qui voyage dans l'espace.", 3),
            GalaxyconWord("galaxycon_en_meteor", "meteor", "Een vallende ruimtesteen.", "A falling space rock.", "Une pierre de l'espace qui tombe.", 3)
        )

        "fr-BE" -> listOf(
            GalaxyconWord("galaxycon_fr_sol", "sol", "De grond onder je voeten.", "The ground under your feet.", "Le sol sous tes pieds.", 1),
            GalaxyconWord("galaxycon_fr_lune", "lune", "Je ziet haar vaak 's nachts.", "You often see it at night.", "On la voit souvent la nuit.", 1),
            GalaxyconWord("galaxycon_fr_etoile", "etoile", "Een lichtpunt in de ruimte.", "A bright point in space.", "Un point lumineux dans l'espace.", 2),
            GalaxyconWord("galaxycon_fr_fusee", "fusee", "Ze vliegt naar de ruimte.", "It flies into space.", "Elle vole vers l'espace.", 2),
            GalaxyconWord("galaxycon_fr_planete", "planete", "Een wereld die rond een ster draait.", "A world that moves around a star.", "Un monde qui tourne autour d'une etoile.", 2),
            GalaxyconWord("galaxycon_fr_orbite", "orbite", "Een baan rond een planeet.", "A path around a planet.", "Un chemin autour d'une planete.", 3),
            GalaxyconWord("galaxycon_fr_galaxie", "galaxie", "Een groep sterren.", "A group of stars.", "Un groupe d'etoiles.", 3),
            GalaxyconWord("galaxycon_fr_meteore", "meteore", "Een vallende ruimtesteen.", "A falling space rock.", "Une pierre de l'espace qui tombe.", 3)
        )

        else -> listOf(
            GalaxyconWord("galaxycon_nl_zon", "zon", "De zon geeft licht.", "The sun gives light.", "Le soleil donne de la lumiere.", 1),
            GalaxyconWord("galaxycon_nl_maan", "maan", "Je ziet hem vaak 's nachts.", "You often see it at night.", "On la voit souvent la nuit.", 1),
            GalaxyconWord("galaxycon_nl_ster", "ster", "Een lichtpunt in de ruimte.", "A bright point in space.", "Un point lumineux dans l'espace.", 1),
            GalaxyconWord("galaxycon_nl_raket", "raket", "Hij vliegt naar de ruimte.", "It flies into space.", "Elle vole vers l'espace.", 2),
            GalaxyconWord("galaxycon_nl_planeet", "planeet", "Een wereld die rond een ster draait.", "A world that moves around a star.", "Un monde qui tourne autour d'une etoile.", 2),
            GalaxyconWord("galaxycon_nl_baan", "baan", "Een route rond een planeet.", "A path around a planet.", "Un chemin autour d'une planete.", 2),
            GalaxyconWord("galaxycon_nl_melkweg", "melkweg", "Onze grote groep sterren.", "Our large group of stars.", "Notre grand groupe d'etoiles.", 3),
            GalaxyconWord("galaxycon_nl_astronaut", "astronaut", "Iemand die naar de ruimte reist.", "A person who travels to space.", "Une personne qui voyage dans l'espace.", 3),
            GalaxyconWord("galaxycon_nl_meteoor", "meteoor", "Een vallende ruimtesteen.", "A falling space rock.", "Une pierre de l'espace qui tombe.", 3)
        )
    }
}

fun calculateGalaxyconResult(
    level: GalaxyconLevelSpec,
    points: Int,
    collisions: Int,
    wordsCompleted: Int,
    correctLetters: Int,
    starsCollected: Int,
    multipliersCollected: Int,
    planetReached: Boolean,
    stickersEarned: List<String>,
    elapsedMs: Long
): GalaxyconRunResult {
    val wordScore = percent(wordsCompleted, level.words.size)
    val letterScore = percent(correctLetters, level.totalRequiredLetters)
    val collisionScore = (100f - percent(collisions, level.maxExpectedCollisions)).coerceIn(0f, 100f)
    val bonusTarget = max(1, level.words.size * 35)
    val bonusScore = percent(starsCollected * 5 + multipliersCollected * 20, bonusTarget)
    val planetScore = if (planetReached) 100f else 0f

    val finalScore = (
        wordScore * 0.40f +
            letterScore * 0.20f +
            collisionScore * 0.20f +
            bonusScore * 0.10f +
            planetScore * 0.10f
        ).coerceIn(0f, 100f)

    val rounded = round(finalScore * 10f) / 10f
    return GalaxyconRunResult(
        scorePercent = rounded,
        displayScore = String.format(Locale.US, "%.1f%%", rounded),
        points = points,
        collisions = collisions,
        wordsCompleted = wordsCompleted,
        totalWords = level.words.size,
        correctLetters = correctLetters,
        totalLetters = level.totalRequiredLetters,
        starsCollected = starsCollected,
        multipliersCollected = multipliersCollected,
        planetReached = planetReached,
        stickersEarned = stickersEarned.distinct(),
        elapsedMs = elapsedMs
    )
}

fun normalizeGalaxyconLocale(locale: String): String = when (locale) {
    "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> "fr-BE"
    "en", "en-US", "en_US", "en-GB", "en_GB" -> "en-US"
    else -> "nl-BE"
}

private fun percent(value: Int, maxValue: Int): Float {
    if (maxValue <= 0) return 0f
    return ((value.toFloat() / maxValue.toFloat()) * 100f).coerceIn(0f, 100f)
}
